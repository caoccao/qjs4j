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

import java.util.Arrays;
import java.util.Stack;

/**
 * Regular expression bytecode executor.
 * Implements ES2020 regex semantics using a stack-based interpreter.
 * Based on QuickJS libregexp.c execution engine.
 */
public final class RegExpEngine {
    private final RegExpBytecode bytecode;

    public RegExpEngine(RegExpBytecode bytecode) {
        this.bytecode = bytecode;
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

        ExecutionContext executionContext = new ExecutionContext(
                input,
                bytecode.instructions(),
                bytecode.captureCount(),
                bytecode.isIgnoreCase(),
                bytecode.isMultiline(),
                bytecode.isDotAll(),
                bytecode.isUnicode()
        );

        // Try matching at each position
        int end = bytecode.isSticky() ? startIndex + 1 : input.length() + 1;
        for (int pos = startIndex; pos < end; pos++) {
            executionContext.reset(pos);
            if (execute(executionContext)) {
                return executionContext.createResult(true);
            }
        }

        return null;
    }

    /**
     * Execute the bytecode starting from the given instruction pointer.
     */
    private boolean execute(ExecutionContext executionContext) {
        // Backtracking stack: stores (pc, pos, captures snapshot)
        Stack<BacktrackPoint> backtrackStack = new Stack<>();
        byte[] bc = executionContext.bytecode;
        int pc = 0;

        while (true) {
            if (pc >= bc.length) {
                // Ran off the end without matching - try backtracking
                if (!backtrackStack.isEmpty()) {
                    BacktrackPoint bp = backtrackStack.pop();
                    pc = bp.pc;
                    executionContext.restoreState(bp);
                    continue;
                }
                return false;
            }

            int opcode = bc[pc] & 0xFF;
            RegExpOpcode op = RegExpOpcode.fromCode(opcode);

            switch (op) {
                case CHAR -> {
                    int ch = readU16(bc, pc + 1);
                    if (!executionContext.matchChar(ch)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 3;
                }

                case CHAR_I -> {
                    int ch = readU16(bc, pc + 1);
                    if (!executionContext.matchCharIgnoreCase(ch)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 3;
                }

                case CHAR32 -> {
                    int ch = readU32(bc, pc + 1);
                    if (!executionContext.matchChar(ch)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 5;
                }

                case CHAR32_I -> {
                    int ch = readU32(bc, pc + 1);
                    if (!executionContext.matchCharIgnoreCase(ch)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 5;
                }

                case DOT -> {
                    if (!executionContext.matchDot()) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case ANY -> {
                    if (!executionContext.matchAny()) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case LINE_START, LINE_START_M -> {
                    if (!executionContext.matchLineStart(op == RegExpOpcode.LINE_START_M)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case LINE_END, LINE_END_M -> {
                    if (!executionContext.matchLineEnd(op == RegExpOpcode.LINE_END_M)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case MATCH -> {
                    // Successful match
                    return true;
                }

                case LOOKAHEAD -> {
                    // Positive lookahead: execute sub-pattern without consuming input
                    int len = readU32(bc, pc + 1);
                    byte[] assertionBytecode = createAssertionBytecode(bc, pc + 5, len);
                    ExecutionContext assertionContext = executeLookaheadAssertion(executionContext, assertionBytecode);
                    if (assertionContext == null) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    executionContext.copyCapturesFrom(assertionContext);
                    pc += 5 + len;
                }

                case NEGATIVE_LOOKAHEAD -> {
                    // Negative lookahead: execute sub-pattern, succeed if it fails
                    int len = readU32(bc, pc + 1);
                    byte[] assertionBytecode = createAssertionBytecode(bc, pc + 5, len);
                    ExecutionContext assertionContext = executeLookaheadAssertion(executionContext, assertionBytecode);
                    if (assertionContext != null) {
                        // Negative lookahead fails if pattern matches
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 5 + len;
                }

                case LOOKBEHIND -> {
                    // Positive lookbehind: match sub-pattern ending at current position
                    int len = readU32(bc, pc + 1);
                    byte[] assertionBytecode = createAssertionBytecode(bc, pc + 5, len);
                    ExecutionContext assertionContext = executeLookbehindAssertion(executionContext, assertionBytecode);
                    if (assertionContext == null) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    executionContext.copyCapturesFrom(assertionContext);
                    pc += 5 + len;
                }

                case NEGATIVE_LOOKBEHIND -> {
                    // Negative lookbehind: succeed only when sub-pattern does not end at current position
                    int len = readU32(bc, pc + 1);
                    byte[] assertionBytecode = createAssertionBytecode(bc, pc + 5, len);
                    ExecutionContext assertionContext = executeLookbehindAssertion(executionContext, assertionBytecode);
                    if (assertionContext != null) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 5 + len;
                }

                case LOOKAHEAD_MATCH, NEGATIVE_LOOKAHEAD_MATCH, LOOKBEHIND_MATCH, NEGATIVE_LOOKBEHIND_MATCH -> {
                    // End of assertion sub-pattern
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
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 3 + len;
                }

                case RANGE_I -> {
                    int len = readU16(bc, pc + 1);
                    if (!executionContext.matchRange(bc, pc + 3, len, true)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 3 + len;
                }

                case SPACE -> {
                    if (!executionContext.matchSpace()) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case NOT_SPACE -> {
                    if (!executionContext.matchNotSpace()) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case NOT_RANGE -> {
                    int len = readU16(bc, pc + 1);
                    if (!executionContext.matchNotRange(bc, pc + 3, len, false)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 3 + len;
                }

                case NOT_RANGE_I -> {
                    int len = readU16(bc, pc + 1);
                    if (!executionContext.matchNotRange(bc, pc + 3, len, true)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 3 + len;
                }

                case BACK_REFERENCE -> {
                    int groupNum = bc[pc + 1] & 0xFF;
                    if (!executionContext.matchBackReference(groupNum, false)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 2;
                }

                case BACK_REFERENCE_I -> {
                    int groupNum = bc[pc + 1] & 0xFF;
                    if (!executionContext.matchBackReference(groupNum, true)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
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
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case NOT_WORD_BOUNDARY, NOT_WORD_BOUNDARY_I -> {
                    if (!executionContext.matchNotWordBoundary(op == RegExpOpcode.NOT_WORD_BOUNDARY_I)) {
                        if (!backtrackStack.isEmpty()) {
                            BacktrackPoint bp = backtrackStack.pop();
                            pc = bp.pc;
                            executionContext.restoreState(bp);
                            continue;
                        }
                        return false;
                    }
                    pc += 1;
                }

                case SPLIT_GOTO_FIRST -> {
                    // Try goto path first, save next path for backtracking
                    int offset = readU32(bc, pc + 1);
                    int pcNext = pc + 5; // Alternative: continue to next instruction
                    int pcGoto = pc + 5 + offset; // First choice: jump

                    // Save the next path for backtracking
                    backtrackStack.push(executionContext.createBacktrackPoint(pcNext));

                    // Take the goto path
                    pc = pcGoto;
                }

                case SPLIT_NEXT_FIRST -> {
                    // Try next path first, save goto path for backtracking
                    int offset = readU32(bc, pc + 1);
                    int pcNext = pc + 5; // First choice: continue to next instruction
                    int pcGoto = pc + 5 + offset; // Alternative: jump

                    // Save the goto path for backtracking
                    backtrackStack.push(executionContext.createBacktrackPoint(pcGoto));

                    // Take the next path
                    pc = pcNext;
                }

                default -> {
                    // Unsupported opcode - try backtracking
                    if (!backtrackStack.isEmpty()) {
                        BacktrackPoint bp = backtrackStack.pop();
                        pc = bp.pc;
                        executionContext.restoreState(bp);
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
        int endPos = outerContext.pos;
        String prefixInput = outerContext.input.substring(0, outerContext.toCharIndex(endPos));
        for (int startPos = 0; startPos <= endPos; startPos++) {
            ExecutionContext assertionContext = executeStandalone(outerContext, prefixInput, assertionBytecode, startPos);
            if (assertionContext != null && assertionContext.pos == endPos) {
                return assertionContext;
            }
        }
        return null;
    }

    private ExecutionContext executeStandalone(
            ExecutionContext outerContext,
            String input,
            byte[] bytecode,
            int startPos) {
        ExecutionContext tempContext = new ExecutionContext(
                input,
                bytecode,
                outerContext.captureCount,
                outerContext.ignoreCase,
                outerContext.multiline,
                outerContext.dotAll,
                outerContext.unicode
        );
        tempContext.pos = startPos;
        System.arraycopy(outerContext.captureStarts, 0, tempContext.captureStarts, 0, outerContext.captureCount);
        System.arraycopy(outerContext.captureEnds, 0, tempContext.captureEnds, 0, outerContext.captureCount);
        return execute(tempContext) ? tempContext : null;
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
     * Represents a point in execution that can be backtracked to.
     *
     * @param pc            Program counter (instruction pointer)
     * @param pos           Position in input string
     * @param captureStarts Copy of capture group start positions
     * @param captureEnds   Copy of capture group end positions
     */
    private record BacktrackPoint(
            int pc,
            int pos,
            int[] captureStarts,
            int[] captureEnds
    ) {
    }

    /**
     * Execution context for a single match attempt.
     */
    private static class ExecutionContext {
        final byte[] bytecode;
        final int captureCount;
        final int[] codePoints;
        final boolean dotAll;
        final boolean ignoreCase;
        final String input;
        final boolean multiline;
        final boolean unicode;
        int[] captureEnds;
        int[] captureStarts;
        int pos;  // Current position in code points

        ExecutionContext(String input, byte[] bytecode, int captureCount,
                         boolean ignoreCase, boolean multiline, boolean dotAll, boolean unicode) {
            this.input = input;
            this.bytecode = bytecode;
            this.codePoints = input.codePoints().toArray();
            this.captureCount = captureCount;
            this.ignoreCase = ignoreCase;
            this.multiline = multiline;
            this.dotAll = dotAll;
            this.unicode = unicode;
            this.captureStarts = new int[captureCount];
            this.captureEnds = new int[captureCount];
            Arrays.fill(captureStarts, -1);
            Arrays.fill(captureEnds, -1);
        }

        void copyCapturesFrom(ExecutionContext other) {
            System.arraycopy(other.captureStarts, 0, captureStarts, 0, captureCount);
            System.arraycopy(other.captureEnds, 0, captureEnds, 0, captureCount);
        }

        BacktrackPoint createBacktrackPoint(int pc) {
            return new BacktrackPoint(pc, pos, captureStarts.clone(), captureEnds.clone());
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

                    // Convert code point indices to character indices
                    int charStart = 0;
                    for (int j = 0; j < start && j < codePoints.length; j++) {
                        charStart += Character.charCount(codePoints[j]);
                    }

                    int charEnd = charStart;
                    for (int j = start; j < end && j < codePoints.length; j++) {
                        charEnd += Character.charCount(codePoints[j]);
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
                    if (Character.toLowerCase(refCh) != Character.toLowerCase(currCh)) {
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

        boolean matchChar(int ch) {
            if (pos >= codePoints.length) {
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
            int current = codePoints[pos];
            if (current == ch ||
                    Character.toLowerCase(current) == Character.toLowerCase(ch) ||
                    Character.toUpperCase(current) == Character.toUpperCase(ch)) {
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

            // Check if character is NOT in any of the ranges
            for (int i = 0; i < numRanges; i++) {
                int start = readU32(bc, offset);
                int end = readU32(bc, offset + 4);
                offset += 8;

                if (ignoreCase) {
                    int chLower = Character.toLowerCase(ch);
                    int startLower = Character.toLowerCase(start);
                    int endLower = Character.toLowerCase(end);
                    if (chLower >= startLower && chLower <= endLower) {
                        // Character is in range, so inverted match fails
                        return false;
                    }
                } else {
                    if (ch >= start && ch <= end) {
                        // Character is in range, so inverted match fails
                        return false;
                    }
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

            // Check if character is in any of the ranges
            for (int i = 0; i < numRanges; i++) {
                int start = readU32(bc, offset);
                int end = readU32(bc, offset + 4);
                offset += 8;

                if (ignoreCase) {
                    int chLower = Character.toLowerCase(ch);
                    int startLower = Character.toLowerCase(start);
                    int endLower = Character.toLowerCase(end);
                    if (chLower >= startLower && chLower <= endLower) {
                        pos++;
                        return true;
                    }
                } else {
                    if (ch >= start && ch <= end) {
                        pos++;
                        return true;
                    }
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

        private int readU16(byte[] bc, int offset) {
            return (bc[offset] & 0xFF) | ((bc[offset + 1] & 0xFF) << 8);
        }

        private int readU32(byte[] bc, int offset) {
            return (bc[offset] & 0xFF) |
                    ((bc[offset + 1] & 0xFF) << 8) |
                    ((bc[offset + 2] & 0xFF) << 16) |
                    ((bc[offset + 3] & 0xFF) << 24);
        }

        void reset(int startPos) {
            this.pos = startPos;
            Arrays.fill(captureStarts, -1);
            Arrays.fill(captureEnds, -1);
            if (captureCount > 0) {
                captureStarts[0] = startPos;
            }
        }

        void restoreState(BacktrackPoint bp) {
            this.pos = bp.pos;
            this.captureStarts = bp.captureStarts.clone();
            this.captureEnds = bp.captureEnds.clone();
        }

        void saveEnd(int captureIndex) {
            if (captureIndex < captureCount) {
                captureEnds[captureIndex] = pos;
            }
        }

        void saveStart(int captureIndex) {
            if (captureIndex < captureCount) {
                captureStarts[captureIndex] = pos;
            }
        }

        int toCharIndex(int codePointIndex) {
            int bounded = Math.max(0, Math.min(codePointIndex, codePoints.length));
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
