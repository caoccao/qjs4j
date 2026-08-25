/*
 * Copyright (c) 2025-2026. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.core.JSRuntimeOptions;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.unicode.CharacterProperties;

import java.util.Arrays;

/**
 * Regular expression bytecode executor.
 * Implements ES2020 regex semantics using a stack-based interpreter.
 * Based on QuickJS libregexp.c execution engine.
 */
public final class RegExpEngine {
    /**
     * Hard cap on the backtracking stack, in bytes — V8's
     * {@code RegExpStack::kMaximumStackSize}, {@code 64 * MB} in {@code src/regexp/regexp-stack.h}.
     * <p>
     * A cap in <em>entries</em> is not a memory bound and does not correspond to anything V8 has:
     * V8 bounds the bytes and lets the entry count fall out of the frame size. Counting entries
     * instead made the budget mean different things for different patterns — at 2<sup>20</sup>
     * entries a one-group pattern reserved 21 ints each and a 100,000-group pattern 200,019 — and
     * it silently set the real ceiling far below V8's for ordinary patterns.
     *
     * @see #MAX_BACKTRACK_INTS
     */
    private static final int MAX_BACKTRACK_BYTES = 64 * 1024 * 1024;
    /**
     * {@link #MAX_BACKTRACK_BYTES} expressed in {@code int} slots, which is what the two stacks are
     * measured in. The entry stack and the saved-state store share this one budget.
     */
    private static final int MAX_BACKTRACK_INTS = MAX_BACKTRACK_BYTES / Integer.BYTES;
    private final long backtrackLimit;
    private final RegExpBytecode bytecode;

    public RegExpEngine(RegExpBytecode bytecode) {
        this(bytecode, JSRuntimeOptions.DEFAULT_REGEXP_BACKTRACK_LIMIT);
    }

    /**
     * Create an engine with an explicit backtracking budget.
     *
     * @param bytecode       the compiled pattern
     * @param backtrackLimit the maximum number of backtracking steps for one match attempt;
     *                       0 or negative means unbounded
     */
    public RegExpEngine(RegExpBytecode bytecode, long backtrackLimit) {
        this.bytecode = bytecode;
        this.backtrackLimit = Math.max(0L, backtrackLimit);
    }

    private byte[] createAssertionBytecode(byte[] bytecode, int startPc, int len) {
        if (len <= 0) {
            return new byte[]{(byte) RegExpOpcode.MATCH.getCode()};
        }
        byte[] assertionBytecode = new byte[len];
        System.arraycopy(bytecode, startPc, assertionBytecode, 0, len);
        assertionBytecode[len - 1] = (byte) RegExpOpcode.MATCH.getCode();
        return assertionBytecode;
    }

    /**
     * Execute the regex against the input string starting at the given index.
     *
     * @param input      The string to match against
     * @param startIndex The index to start matching from
     * @return The match result, or null if no match
     */
    public MatchResult exec(String input, int startIndex) {
        if (input == null || startIndex < 0 || startIndex > input.length()) {
            return null;
        }

        boolean isUnicode = bytecode.isUnicode() || bytecode.hasUnicodeSets();

        ExecutionContext executionContext = new ExecutionContext(
                input,
                bytecode.instructions(),
                bytecode.captureCount(),
                bytecode.groupNames(),
                bytecode.isIgnoreCase(),
                bytecode.isMultiline(),
                bytecode.isDotAll(),
                isUnicode,
                bytecode.registerCount(),
                backtrackLimit
        );

        // Try matching at each position
        if (isUnicode) {
            // In unicode mode, the engine works with code point indices internally.
            // Convert startIndex from UTF-16 units to code point index.
            int codePointStart = input.codePointCount(0, startIndex);
            int codePointEnd = bytecode.isSticky()
                    ? codePointStart + 1
                    : executionContext.codePoints.length + 1;
            for (int pos = codePointStart; pos < codePointEnd; pos++) {
                executionContext.reset(pos);
                if (execute(executionContext)) {
                    return executionContext.createResult(true);
                }
            }
        } else {
            int end = bytecode.isSticky() ? startIndex + 1 : input.length() + 1;
            for (int pos = startIndex; pos < end; pos++) {
                executionContext.reset(pos);
                if (execute(executionContext)) {
                    return executionContext.createResult(true);
                }
            }
        }

        return null;
    }

    /**
     * Execute the bytecode starting from the given instruction pointer.
     */
    private boolean execute(ExecutionContext executionContext) {
        byte[] bc = executionContext.bytecode;
        int pc = 0;
        executionContext.resetBacktrack();

        while (true) {
            if (pc >= bc.length) {
                if (executionContext.hasBacktrack()) {
                    pc = executionContext.popBacktrack();
                    continue;
                }
                return false;
            }

            RegExpOpcode op = RegExpOpcode.fromCode(bc[pc] & 0xFF);

            switch (op) {
                case CHAR -> {
                    int ch = readU16(bc, pc + 1);
                    if (!executionContext.matchChar(ch)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 3;
                }

                case CHAR_I -> {
                    int ch = readU16(bc, pc + 1);
                    if (!executionContext.matchCharIgnoreCase(ch)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 3;
                }

                case CHAR32 -> {
                    int ch = readU32(bc, pc + 1);
                    if (!executionContext.matchChar(ch)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 5;
                }

                case CHAR32_I -> {
                    int ch = readU32(bc, pc + 1);
                    if (!executionContext.matchCharIgnoreCase(ch)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 5;
                }

                case DOT -> {
                    if (!executionContext.matchDot()) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case ANY -> {
                    if (!executionContext.matchAny()) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case LINE_START, LINE_START_M -> {
                    if (!executionContext.matchLineStart(op == RegExpOpcode.LINE_START_M)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case LINE_END, LINE_END_M -> {
                    if (!executionContext.matchLineEnd(op == RegExpOpcode.LINE_END_M)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case MATCH -> {
                    return true;
                }

                case LOOKAHEAD -> {
                    int len = readU32(bc, pc + 1);
                    byte[] assertionBytecode = createAssertionBytecode(bc, pc + 5, len);
                    ExecutionContext assertionContext = executeLookaheadAssertion(executionContext, assertionBytecode);
                    if (assertionContext == null) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    executionContext.copyCapturesFrom(assertionContext);
                    pc += 5 + len;
                }

                case NEGATIVE_LOOKAHEAD -> {
                    int len = readU32(bc, pc + 1);
                    byte[] assertionBytecode = createAssertionBytecode(bc, pc + 5, len);
                    ExecutionContext assertionContext = executeLookaheadAssertion(executionContext, assertionBytecode);
                    if (assertionContext != null) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 5 + len;
                }

                case LOOKBEHIND -> {
                    int len = readU32(bc, pc + 1);
                    byte[] assertionBytecode = createAssertionBytecode(bc, pc + 5, len);
                    ExecutionContext assertionContext = executeLookbehindAssertion(executionContext, assertionBytecode);
                    if (assertionContext == null) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    executionContext.copyCapturesFrom(assertionContext);
                    pc += 5 + len;
                }

                case NEGATIVE_LOOKBEHIND -> {
                    int len = readU32(bc, pc + 1);
                    byte[] assertionBytecode = createAssertionBytecode(bc, pc + 5, len);
                    ExecutionContext assertionContext = executeLookbehindAssertion(executionContext, assertionBytecode);
                    if (assertionContext != null) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 5 + len;
                }

                case LOOKAHEAD_MATCH, NEGATIVE_LOOKAHEAD_MATCH, LOOKBEHIND_MATCH,
                     NEGATIVE_LOOKBEHIND_MATCH -> {
                    return true;
                }

                case SAVE_START -> {
                    int captureIndex = bc[pc + 1] & 0xFF;
                    executionContext.saveStart(captureIndex);
                    pc += 2;
                }

                case SAVE_END -> {
                    int captureIndex = bc[pc + 1] & 0xFF;
                    executionContext.saveEnd(captureIndex);
                    pc += 2;
                }

                case RANGE -> {
                    int len = readU16(bc, pc + 1);
                    if (!executionContext.matchRange(bc, pc + 3, len, false)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 3 + len;
                }

                case RANGE_I -> {
                    int len = readU16(bc, pc + 1);
                    if (!executionContext.matchRange(bc, pc + 3, len, true)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 3 + len;
                }

                case SPACE -> {
                    if (!executionContext.matchSpace()) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case NOT_SPACE -> {
                    if (!executionContext.matchNotSpace()) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case NOT_RANGE -> {
                    int len = readU16(bc, pc + 1);
                    if (!executionContext.matchNotRange(bc, pc + 3, len, false)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 3 + len;
                }

                case NOT_RANGE_I -> {
                    int len = readU16(bc, pc + 1);
                    if (!executionContext.matchNotRange(bc, pc + 3, len, true)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 3 + len;
                }

                case BACK_REFERENCE -> {
                    int groupNum = bc[pc + 1] & 0xFF;
                    if (!executionContext.matchBackReference(groupNum, false)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 2;
                }

                case BACK_REFERENCE_I -> {
                    int groupNum = bc[pc + 1] & 0xFF;
                    if (!executionContext.matchBackReference(groupNum, true)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 2;
                }

                case BACKWARD_BACK_REFERENCE -> {
                    int groupNum = bc[pc + 1] & 0xFF;
                    if (!executionContext.matchBackwardBackReference(groupNum, false)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 2;
                }

                case BACKWARD_BACK_REFERENCE_I -> {
                    int groupNum = bc[pc + 1] & 0xFF;
                    if (!executionContext.matchBackwardBackReference(groupNum, true)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 2;
                }

                case GOTO -> {
                    int offset = readU32(bc, pc + 1);
                    pc += 5 + offset;
                }

                case WORD_BOUNDARY, WORD_BOUNDARY_I -> {
                    if (!executionContext.matchWordBoundary(op == RegExpOpcode.WORD_BOUNDARY_I)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case NOT_WORD_BOUNDARY, NOT_WORD_BOUNDARY_I -> {
                    if (!executionContext.matchNotWordBoundary(op == RegExpOpcode.NOT_WORD_BOUNDARY_I)) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case SPLIT_GOTO_FIRST -> {
                    int offset = readU32(bc, pc + 1);
                    executionContext.pushBacktrack(pc + 5);
                    pc = pc + 5 + offset;
                }

                case SPLIT_NEXT_FIRST -> {
                    int offset = readU32(bc, pc + 1);
                    executionContext.pushBacktrack(pc + 5 + offset);
                    pc += 5;
                }

                case SET_CHAR_POS -> {
                    int regIdx = bc[pc + 1] & 0xFF;
                    if (regIdx < executionContext.registers.length) {
                        executionContext.stateDirty = true;
                        executionContext.registers[regIdx] = executionContext.pos;
                    }
                    pc += 2;
                }

                case CHECK_ADVANCE -> {
                    int regIdx = bc[pc + 1] & 0xFF;
                    if (regIdx < executionContext.registers.length
                            && executionContext.registers[regIdx] == executionContext.pos) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 2;
                }

                case PREV -> {
                    if (!executionContext.movePrevious()) {
                        if (executionContext.hasBacktrack()) {
                            pc = executionContext.popBacktrack();
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case SAVE_RESET -> {
                    int startCapture = bc[pc + 1] & 0xFF;
                    int endCapture = bc[pc + 2] & 0xFF;
                    executionContext.stateDirty = true;
                    for (int i = startCapture; i <= endCapture && i < executionContext.captureCount; i++) {
                        executionContext.captureStarts[i] = -1;
                        executionContext.captureEnds[i] = -1;
                    }
                    pc += 3;
                }

                default -> {
                    if (executionContext.hasBacktrack()) {
                        pc = executionContext.popBacktrack();
                        continue;
                    }
                    return false;
                }
            }
        }
    }

    private ExecutionContext executeLookaheadAssertion(ExecutionContext outerContext, byte[] assertionBytecode) {
        return executeStandalone(outerContext, outerContext.input, assertionBytecode, outerContext.pos);
    }

    private ExecutionContext executeLookbehindAssertion(ExecutionContext outerContext, byte[] assertionBytecode) {
        return executeStandalone(outerContext, outerContext.input, assertionBytecode, outerContext.pos);
    }

    private ExecutionContext executeStandalone(
            ExecutionContext outerContext,
            String input,
            byte[] bytecode,
            int startPos) {
        // A lookaround runs on its own context. It inherits the outer context's *remaining* budget
        // and charges what it spends back, so a lookaround inside a loop cannot reset the budget
        // on every iteration and reintroduce unbounded backtracking.
        ExecutionContext tempContext = new ExecutionContext(
                input,
                bytecode,
                outerContext.captureCount,
                outerContext.groupNames,
                outerContext.ignoreCase,
                outerContext.multiline,
                outerContext.dotAll,
                outerContext.unicode,
                // Lookaround bodies are compiled in the same CompileContext as the pattern, so they
                // draw on the same register counter and the outer count bounds them.
                outerContext.registerCount,
                outerContext.remainingBacktrackBudget()
        );
        tempContext.pos = startPos;
        System.arraycopy(outerContext.captureStarts, 0, tempContext.captureStarts, 0, outerContext.captureCount);
        System.arraycopy(outerContext.captureEnds, 0, tempContext.captureEnds, 0, outerContext.captureCount);
        try {
            return execute(tempContext) ? tempContext : null;
        } finally {
            outerContext.backtrackSteps += tempContext.backtrackSteps;
        }
    }

    /**
     * Read a 16-bit unsigned value from bytecode (little-endian).
     */
    private int readU16(byte[] bc, int offset) {
        return (bc[offset] & 0xFF) | ((bc[offset + 1] & 0xFF) << 8);
    }

    /**
     * Read a 32-bit unsigned value from bytecode (little-endian).
     */
    private int readU32(byte[] bc, int offset) {
        return (bc[offset] & 0xFF) |
                ((bc[offset + 1] & 0xFF) << 8) |
                ((bc[offset + 2] & 0xFF) << 16) |
                ((bc[offset + 3] & 0xFF) << 24);
    }

    /**
     * Test if the regex matches the input.
     */
    public boolean test(String input) {
        return exec(input, 0) != null;
    }

    /**
     * Execution context for a single match attempt.
     */
    private static class ExecutionContext {
        static final int MAX_REGISTERS = RegExpBytecode.ExecutionLimits.MAX_REGISTERS;
        /**
         * Ints per entry on the backtrack stack: {@code pc}, {@code pos}, {@code stateOffset}.
         * <p>
         * The captures and registers used to live inline in every entry, so one backtrack point
         * cost {@code 3 + 2 * captureCount + 16} ints — 84 bytes for a group-less pattern, against
         * the 4 bytes of a V8 slot. Test262's {@code property-escapes} tests match a greedy
         * {@code +} against every code point in Unicode, ~1.11 million backtrack points, which at
         * 84 bytes each is ~93 MiB: above V8's whole 64 MiB budget, for a pattern V8 matches
         * comfortably. Saved state now lives in {@link #stateData} and is written only when it
         * actually changes, so an entry is 12 bytes and the budget buys a comparable amount of
         * backtracking to V8's.
         * <p>
         * The state itself was also always sized for 16 registers. Almost no pattern allocates any
         * — only the zero-advance check does — so the compiler now reports the real count and a
         * state is {@code 2 * captureCount + registerCount} ints. Together the two changes are what
         * let {@code /^([\s\S])+$/u}, which dirties state on every iteration and so cannot share
         * a saved copy, match the same subject V8 matches.
         */
        private static final int BACKTRACK_ENTRY_SIZE = 3;
        private static final int INITIAL_BACKTRACK_ENTRIES = 64;
        private static final int INITIAL_SAVED_STATES = 4;
        final byte[] bytecode;
        final int captureCount;
        final int[] codePoints;
        final boolean dotAll;
        final String[] groupNames;
        final boolean ignoreCase;
        final String input;
        final boolean multiline;
        /**
         * Registers this pattern allocated, from the compiler. Sized to what the pattern uses
         * rather than to {@link #MAX_REGISTERS}: the saved state is copied on every backtrack point
         * where state changed, and 16 registers is 64 bytes per point that almost no pattern needs.
         */
        final int registerCount;
        final int[] registers;  // Registers for loop counters and position tracking (QuickJS capture[2*captureCount+...])
        final boolean unicode;
        /**
         * Budget of backtracking steps for this match attempt; 0 means unbounded.
         */
        private final long backtrackLimit;
        /**
         * Ints per saved state: {@code captureCount * 2 + registerCount}.
         */
        private final int stateSize;
        int backtrackTop;
        int[] captureEnds;
        int[] captureStarts;
        int pos;  // Current position in code points
        // The backtrack stack proper: BACKTRACK_ENTRY_SIZE ints per entry, [pc, pos, stateOffset].
        private int[] backtrackData;
        /**
         * Backtracking steps consumed so far.
         */
        private long backtrackSteps;
        private int lastStateOffset;
        // Saved captures and registers, appended only when they have actually changed since the
        // last save. Consecutive entries with unmodified state share one copy by pointing at the
        // same offset, which is what keeps a greedy loop's backtrack points at 12 bytes each.
        private int[] stateData;
        private boolean stateDirty;  // true if captures/registers modified since last state save
        private int stateTop;

        ExecutionContext(
                String input,
                byte[] bytecode,
                int captureCount,
                String[] groupNames,
                boolean ignoreCase,
                boolean multiline,
                boolean dotAll,
                boolean unicode,
                int registerCount,
                long backtrackLimit) {
            this.backtrackLimit = backtrackLimit;
            this.registerCount = Math.max(0, Math.min(registerCount, MAX_REGISTERS));
            this.input = input;
            this.bytecode = bytecode;
            this.codePoints = unicode ? input.codePoints().toArray() : input.chars().toArray();
            this.captureCount = captureCount;
            this.groupNames = groupNames;
            this.ignoreCase = ignoreCase;
            this.multiline = multiline;
            this.dotAll = dotAll;
            this.unicode = unicode;
            this.captureStarts = new int[captureCount];
            this.captureEnds = new int[captureCount];
            this.registers = new int[this.registerCount];
            Arrays.fill(captureStarts, -1);
            Arrays.fill(captureEnds, -1);
            this.stateSize = captureCount + captureCount + this.registerCount;
            // Both allocations are small and fixed. The old code sized the first allocation at 64
            // full-width entries, which for a capture-heavy pattern was tens of MiB claimed before
            // the match began — memory no later cap could take back.
            this.backtrackData = new int[BACKTRACK_ENTRY_SIZE * INITIAL_BACKTRACK_ENTRIES];
            this.stateData = new int[stateSize * INITIAL_SAVED_STATES];
            this.stateDirty = true;
        }

        /**
         * ES spec Canonicalize for case-insensitive matching.
         * Non-Unicode mode: toUpperCase, but if ch >= 128 and result < 128, return ch unchanged.
         * Unicode mode: simple case fold.
         */
        private int canonicalize(int ch) {
            if (unicode) {
                return CharacterProperties.caseFold(ch);
            }
            int upper = Character.toUpperCase(ch);
            // ES2024 22.2.2.8.2: if ch >= 128 and upper < 128, return ch unchanged
            if (ch >= 128 && upper < 128) {
                return ch;
            }
            return upper;
        }

        private boolean codePointEqualsIgnoreCaseUnicode(int leftCodePoint, int rightCodePoint) {
            if (leftCodePoint == rightCodePoint) {
                return true;
            }
            String leftString = new String(Character.toChars(leftCodePoint));
            String rightString = new String(Character.toChars(rightCodePoint));
            return leftString.equalsIgnoreCase(rightString);
        }

        void copyCapturesFrom(ExecutionContext other) {
            stateDirty = true;
            System.arraycopy(other.captureStarts, 0, captureStarts, 0, captureCount);
            System.arraycopy(other.captureEnds, 0, captureEnds, 0, captureCount);
        }

        MatchResult createResult(boolean matched) {
            if (!matched) {
                return new MatchResult(false, -1, -1, null, null);
            }

            // Save the end position of the overall match (capture group 0)
            if (captureCount > 0) {
                captureEnds[0] = pos;
            }

            int startIndex = captureCount > 0 ? captureStarts[0] : -1;
            int endIndex = captureCount > 0 ? captureEnds[0] : -1;

            String[] captures = new String[captureCount];
            int[][] indices = new int[captureCount][2];

            for (int i = 0; i < captureCount; i++) {
                if (captureStarts[i] >= 0 && captureEnds[i] >= 0) {
                    int start = captureStarts[i];
                    int end = captureEnds[i];

                    int charStart;
                    int charEnd;
                    if (unicode) {
                        // Convert code point indices to UTF-16 indices in /u mode.
                        charStart = 0;
                        for (int j = 0; j < start && j < codePoints.length; j++) {
                            charStart += Character.charCount(codePoints[j]);
                        }

                        charEnd = charStart;
                        for (int j = start; j < end && j < codePoints.length; j++) {
                            charEnd += Character.charCount(codePoints[j]);
                        }
                    } else {
                        // In non-/u mode, positions are already UTF-16 code unit indices.
                        charStart = start;
                        charEnd = end;
                    }

                    captures[i] = input.substring(charStart, charEnd);
                    indices[i][0] = charStart;
                    indices[i][1] = charEnd;
                } else {
                    captures[i] = null;
                    indices[i][0] = -1;
                    indices[i][1] = -1;
                }
            }

            return new MatchResult(true, startIndex, endIndex, captures, indices);
        }

        /**
         * Double an int stack, refusing to let the two stacks together exceed
         * {@link #MAX_BACKTRACK_INTS}.
         * <p>
         * The two share one budget because they are two halves of the same structure; bounding them
         * separately would let the pair reach twice V8's ceiling.
         *
         * @param array       the stack to grow
         * @param required    the length it must reach
         * @param otherLength the length of the other stack, which shares the budget
         * @return the grown array
         * @throws JSRangeErrorException when the budget cannot accommodate {@code required}
         */
        private int[] growWithinBudget(int[] array, int required, int otherLength) {
            // Computed in long: required and otherLength are both ints, but their sum is not
            // guaranteed to be, and a wrapped sum would silently pass the check.
            long budget = MAX_BACKTRACK_INTS - (long) otherLength;
            if ((long) required > budget) {
                throw new JSRangeErrorException(
                        "regular expression execution exceeded the backtracking stack limit");
            }
            long grown = Math.max((long) array.length * 2, required);
            return Arrays.copyOf(array, (int) Math.min(budget, grown));
        }

        boolean hasBacktrack() {
            return backtrackTop > 0;
        }

        /**
         * Test a code point against sorted, disjoint inclusive ranges encoded in bytecode.
         */
        private boolean isInSortedRanges(byte[] bc, int offset, int numRanges, int ch) {
            int low = 0;
            int high = numRanges - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int rangeOffset = offset + middle * 8;
                int start = readU32(bc, rangeOffset);
                if (ch < start) {
                    high = middle - 1;
                    continue;
                }
                int end = readU32(bc, rangeOffset + 4);
                if (ch > end) {
                    low = middle + 1;
                    continue;
                }
                return true;
            }
            return false;
        }

        private boolean isWordChar(int ch, boolean ignoreCase) {
            // Word characters: [a-zA-Z0-9_]
            if (ch < 256) {
                return (ch >= 'a' && ch <= 'z') ||
                        (ch >= 'A' && ch <= 'Z') ||
                        (ch >= '0' && ch <= '9') ||
                        ch == '_';
            }
            // For Unicode mode with ignore case, handle special characters
            // 0x017f: Latin Small Letter Long S
            // 0x212a: Kelvin Sign
            return ignoreCase && (ch == 0x017f || ch == 0x212a);
        }

        boolean matchAny() {
            if (pos >= codePoints.length) {
                return false;
            }
            pos++;
            return true;
        }

        boolean matchBackReference(int groupNum, boolean ignoreCase) {
            groupNum = resolveNamedBackReferenceGroup(groupNum);
            // Check if the capture group has been captured
            if (groupNum >= captureCount || captureStarts[groupNum] == -1 || captureEnds[groupNum] == -1) {
                // Group not captured yet - match empty string (succeeds)
                return true;
            }

            int refStart = captureStarts[groupNum];
            int refEnd = captureEnds[groupNum];

            // Check if we have enough characters left to match
            int refLen = refEnd - refStart;
            if (pos + refLen > codePoints.length) {
                return false;
            }

            // Match the captured text
            for (int i = 0; i < refLen; i++) {
                int refCh = codePoints[refStart + i];
                int currCh = codePoints[pos + i];

                if (ignoreCase) {
                    if (canonicalize(refCh) != canonicalize(currCh)) {
                        return false;
                    }
                } else {
                    if (refCh != currCh) {
                        return false;
                    }
                }
            }

            // Advance position by the matched length
            pos += refLen;
            return true;
        }

        boolean matchBackwardBackReference(int groupNum, boolean ignoreCase) {
            groupNum = resolveNamedBackReferenceGroup(groupNum);
            if (groupNum >= captureCount || captureStarts[groupNum] == -1 || captureEnds[groupNum] == -1) {
                return true;
            }

            int referenceStart = captureStarts[groupNum];
            int referenceEnd = captureEnds[groupNum];
            int referenceLength = referenceEnd - referenceStart;
            if (referenceLength <= 0) {
                return true;
            }
            if (pos < referenceLength) {
                return false;
            }

            for (int referenceIndex = referenceEnd - 1; referenceIndex >= referenceStart; referenceIndex--) {
                int referenceChar = codePoints[referenceIndex];
                int currentChar = codePoints[pos - 1];
                if (ignoreCase) {
                    if (canonicalize(referenceChar) != canonicalize(currentChar)) {
                        return false;
                    }
                } else if (referenceChar != currentChar) {
                    return false;
                }
                pos--;
            }
            return true;
        }

        boolean matchChar(int ch) {
            if (pos >= codePoints.length) {
                return false;
            }
            if (!unicode && ch > 0xFFFF) {
                if (pos + 1 >= codePoints.length) {
                    return false;
                }
                char[] surrogatePair = Character.toChars(ch);
                if (codePoints[pos] == surrogatePair[0] && codePoints[pos + 1] == surrogatePair[1]) {
                    pos += 2;
                    return true;
                }
                return false;
            }
            if (codePoints[pos] == ch) {
                pos++;
                return true;
            }
            return false;
        }

        boolean matchCharIgnoreCase(int ch) {
            if (pos >= codePoints.length) {
                return false;
            }
            if (!unicode && ch > 0xFFFF) {
                if (pos + 1 >= codePoints.length) {
                    return false;
                }
                char[] surrogatePair = Character.toChars(ch);
                if (codePoints[pos] == surrogatePair[0] && codePoints[pos + 1] == surrogatePair[1]) {
                    pos += 2;
                    return true;
                }
                return false;
            }
            int current = codePoints[pos];
            if (current == ch || canonicalize(current) == canonicalize(ch)) {
                pos++;
                return true;
            }
            return false;
        }

        boolean matchDot() {
            if (pos >= codePoints.length) {
                return false;
            }
            int ch = codePoints[pos];
            // Dot matches everything except line terminators
            if (ch == '\n' || ch == '\r' || ch == 0x2028 || ch == 0x2029) {
                return false;
            }
            pos++;
            return true;
        }

        boolean matchLineEnd(boolean multilineMode) {
            if (pos >= codePoints.length) {
                return true;
            }
            if (multilineMode) {
                int ch = codePoints[pos];
                return ch == '\n' || ch == '\r' || ch == 0x2028 || ch == 0x2029;
            }
            return false;
        }

        boolean matchLineStart(boolean multilineMode) {
            if (pos == 0) {
                return true;
            }
            if (multilineMode && pos < codePoints.length) {
                int prevCh = codePoints[pos - 1];
                return prevCh == '\n' || prevCh == '\r' || prevCh == 0x2028 || prevCh == 0x2029;
            }
            return false;
        }

        boolean matchNotRange(byte[] bc, int offset, int len, boolean ignoreCase) {
            if (pos >= codePoints.length) {
                return false;
            }
            int ch = codePoints[pos];

            // Read number of ranges
            int numRanges = readU16(bc, offset);
            offset += 2;

            if (!ignoreCase) {
                if (isInSortedRanges(bc, offset, numRanges, ch)) {
                    return false;
                }
                pos++;
                return true;
            }

            // Check if character is NOT in any of the ranges
            int canonCh = canonicalize(ch);
            for (int i = 0; i < numRanges; i++) {
                int start = readU32(bc, offset);
                int end = readU32(bc, offset + 4);
                offset += 8;

                int canonStart = canonicalize(start);
                int canonEnd = canonicalize(end);
                if ((canonCh >= canonStart && canonCh <= canonEnd)
                        || (unicode && start == end && codePointEqualsIgnoreCaseUnicode(ch, start))) {
                    // Character is in range, so inverted match fails
                    return false;
                }
            }
            // Character is not in any range, so inverted match succeeds
            pos++;
            return true;
        }

        boolean matchNotSpace() {
            if (pos >= codePoints.length) {
                return false;
            }
            int ch = codePoints[pos];
            // JavaScript whitespace: space, tab, line terminators, Unicode Zs category
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f' ||
                    ch == 0x0B || ch == 0x00A0 || ch == 0xFEFF || ch == 0x2028 || ch == 0x2029 ||
                    Character.getType(ch) == Character.SPACE_SEPARATOR) {
                return false;
            }
            pos++;
            return true;
        }

        boolean matchNotWordBoundary(boolean ignoreCase) {
            return !matchWordBoundary(ignoreCase);
        }

        boolean matchRange(byte[] bc, int offset, int len, boolean ignoreCase) {
            if (pos >= codePoints.length) {
                return false;
            }
            int ch = codePoints[pos];

            // Read number of ranges
            int numRanges = readU16(bc, offset);
            offset += 2;

            if (!ignoreCase) {
                if (isInSortedRanges(bc, offset, numRanges, ch)) {
                    pos++;
                    return true;
                }
                return false;
            }

            // Check if character is in any of the ranges
            int canonCh = canonicalize(ch);
            for (int i = 0; i < numRanges; i++) {
                int start = readU32(bc, offset);
                int end = readU32(bc, offset + 4);
                offset += 8;

                int canonStart = canonicalize(start);
                int canonEnd = canonicalize(end);
                if ((canonCh >= canonStart && canonCh <= canonEnd)
                        || (unicode && start == end && codePointEqualsIgnoreCaseUnicode(ch, start))) {
                    pos++;
                    return true;
                }
            }
            return false;
        }

        boolean matchSpace() {
            if (pos >= codePoints.length) {
                return false;
            }
            int ch = codePoints[pos];
            // JavaScript whitespace: space, tab, line terminators, Unicode Zs category
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f' ||
                    ch == 0x0B || ch == 0x00A0 || ch == 0xFEFF || ch == 0x2028 || ch == 0x2029 ||
                    Character.getType(ch) == Character.SPACE_SEPARATOR) {
                pos++;
                return true;
            }
            return false;
        }

        boolean matchWordBoundary(boolean ignoreCase) {
            // Word boundary: transition between word and non-word character
            // Check character before current position
            boolean prevIsWord;
            if (pos == 0) {
                prevIsWord = false;
            } else {
                int prevCh = codePoints[pos - 1];
                prevIsWord = isWordChar(prevCh, ignoreCase);
            }

            // Check character at current position
            boolean currIsWord;
            if (pos >= codePoints.length) {
                currIsWord = false;
            } else {
                int currCh = codePoints[pos];
                currIsWord = isWordChar(currCh, ignoreCase);
            }

            // Boundary exists if one is word char and the other is not
            return prevIsWord != currIsWord;
        }

        boolean movePrevious() {
            if (pos <= 0) {
                return false;
            }
            pos--;
            return true;
        }

        int popBacktrack() {
            // The matcher is a backtracking engine, so a pattern like /(a+)+$/ takes time
            // exponential in the input length. Counting pops bounds that: the stack depth stays
            // small during exponential blow-up, so a depth limit alone would not catch it.
            if (backtrackLimit > 0 && ++backtrackSteps > backtrackLimit) {
                throw new JSRangeErrorException(
                        "regular expression execution exceeded the backtracking limit");
            }
            backtrackTop -= BACKTRACK_ENTRY_SIZE;
            int base = backtrackTop;
            pos = backtrackData[base + 1];
            int stateOffset = backtrackData[base + 2];
            System.arraycopy(stateData, stateOffset, captureStarts, 0, captureCount);
            System.arraycopy(stateData, stateOffset + captureCount, captureEnds, 0, captureCount);
            System.arraycopy(stateData, stateOffset + captureCount + captureCount, registers, 0, registerCount);
            // The popped entry no longer owns a slot in the saved-state store. Retain only the
            // states referenced by entries that remain on the stack. Offsets are non-decreasing —
            // an entry either appends a state or reuses the previous entry's — so the final entry
            // also references the final live state. Keeping the popped entry's state here leaked
            // one state on every pop/push cycle and made a shallow alternation accumulate saved
            // states for the whole match.
            if (backtrackTop == 0) {
                stateTop = 0;
                lastStateOffset = 0;
            } else {
                lastStateOffset = backtrackData[backtrackTop - BACKTRACK_ENTRY_SIZE + 2];
                stateTop = lastStateOffset + stateSize;
            }
            stateDirty = true;
            return backtrackData[base];
        }

        void pushBacktrack(int pc) {
            if (stateDirty || stateTop == 0) {
                if (stateTop + stateSize > stateData.length) {
                    stateData = growWithinBudget(stateData, stateTop + stateSize, backtrackData.length);
                }
                lastStateOffset = stateTop;
                System.arraycopy(captureStarts, 0, stateData, stateTop, captureCount);
                System.arraycopy(captureEnds, 0, stateData, stateTop + captureCount, captureCount);
                System.arraycopy(registers, 0, stateData, stateTop + captureCount + captureCount, registerCount);
                stateTop += stateSize;
                stateDirty = false;
            }
            if (backtrackTop + BACKTRACK_ENTRY_SIZE > backtrackData.length) {
                backtrackData = growWithinBudget(
                        backtrackData, backtrackTop + BACKTRACK_ENTRY_SIZE, stateData.length);
            }
            int base = backtrackTop;
            backtrackData[base] = pc;
            backtrackData[base + 1] = pos;
            backtrackData[base + 2] = lastStateOffset;
            backtrackTop += BACKTRACK_ENTRY_SIZE;
        }

        private int readU16(byte[] bc, int offset) {
            return (bc[offset] & 0xFF) | ((bc[offset + 1] & 0xFF) << 8);
        }

        private int readU32(byte[] bc, int offset) {
            return (bc[offset] & 0xFF) |
                    ((bc[offset + 1] & 0xFF) << 8) |
                    ((bc[offset + 2] & 0xFF) << 16) |
                    ((bc[offset + 3] & 0xFF) << 24);
        }

        /**
         * Budget available to a nested match (a lookaround).
         *
         * @return the unspent part of this context's budget, or 0 when unbounded
         * @throws JSRangeErrorException when this context has already exhausted its budget
         */
        long remainingBacktrackBudget() {
            if (backtrackLimit <= 0) {
                return 0;
            }
            long remaining = backtrackLimit - backtrackSteps;
            if (remaining <= 0) {
                throw new JSRangeErrorException(
                        "regular expression execution exceeded the backtracking limit");
            }
            return remaining;
        }

        void reset(int startPos) {
            this.pos = startPos;
            Arrays.fill(captureStarts, -1);
            Arrays.fill(captureEnds, -1);
            if (captureCount > 0) {
                captureStarts[0] = startPos;
            }
            stateDirty = true;
        }

        /**
         * Discard both stacks before a match attempt.
         * <p>
         * The saved-state store has to be reset alongside the entry stack: leaving {@code stateTop}
         * where the previous attempt left it would make the next attempt append states above stale
         * data and count them against the budget.
         */
        void resetBacktrack() {
            backtrackTop = 0;
            stateTop = 0;
            lastStateOffset = 0;
            stateDirty = true;
        }

        private int resolveNamedBackReferenceGroup(int groupNum) {
            if (groupNames == null || groupNum <= 0 || groupNum >= captureCount || groupNum >= groupNames.length) {
                return groupNum;
            }
            String groupName = groupNames[groupNum];
            if (groupName == null) {
                return groupNum;
            }
            int resolvedGroupNum = groupNum;
            for (int captureIndex = groupNum + 1; captureIndex < captureCount && captureIndex < groupNames.length; captureIndex++) {
                if (!groupName.equals(groupNames[captureIndex])) {
                    continue;
                }
                if (captureStarts[captureIndex] >= 0 && captureEnds[captureIndex] >= 0) {
                    resolvedGroupNum = captureIndex;
                }
            }
            if (captureStarts[resolvedGroupNum] >= 0 && captureEnds[resolvedGroupNum] >= 0) {
                return resolvedGroupNum;
            }
            for (int captureIndex = groupNum - 1; captureIndex > 0 && captureIndex < groupNames.length; captureIndex--) {
                if (!groupName.equals(groupNames[captureIndex])) {
                    continue;
                }
                if (captureStarts[captureIndex] >= 0 && captureEnds[captureIndex] >= 0) {
                    return captureIndex;
                }
            }
            return groupNum;
        }

        void saveEnd(int captureIndex) {
            if (captureIndex < captureCount) {
                stateDirty = true;
                captureEnds[captureIndex] = pos;
            }
        }

        void saveStart(int captureIndex) {
            if (captureIndex < captureCount) {
                stateDirty = true;
                captureStarts[captureIndex] = pos;
            }
        }

        int toCharIndex(int codePointIndex) {
            int bounded = Math.max(0, Math.min(codePointIndex, codePoints.length));
            if (!unicode) {
                return bounded;
            }
            int charIndex = 0;
            for (int i = 0; i < bounded; i++) {
                charIndex += Character.charCount(codePoints[i]);
            }
            return charIndex;
        }
    }

    /**
     * Result of a regex match operation.
     *
     * @param matched    Whether the pattern matched
     * @param startIndex Starting position of the match (in code points)
     * @param endIndex   Ending position of the match (in code points)
     * @param captures   Array of captured groups (including group 0 - the full match)
     * @param indices    Array of [start, end] indices for each capture group
     */
    public record MatchResult(
            boolean matched,
            int startIndex,
            int endIndex,
            String[] captures,
            int[][] indices
    ) {
        /**
         * Get a specific capture group.
         */
        public String getCapture(int index) {
            return matched && captures != null && index >= 0 && index < captures.length
                    ? captures[index]
                    : null;
        }

        /**
         * Get the full matched string.
         */
        public String getMatch() {
            return matched && captures != null && captures.length > 0 ? captures[0] : null;
        }
    }
}
