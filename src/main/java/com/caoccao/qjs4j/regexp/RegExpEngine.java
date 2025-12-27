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

        ExecutionContext ctx = new ExecutionContext(
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
            ctx.reset(pos);
            if (execute(ctx)) {
                return ctx.createResult(true);
            }
        }

        return null;
    }

    /**
     * Test if the regex matches the input.
     */
    public boolean test(String input) {
        return exec(input, 0) != null;
    }

    /**
     * Execute the bytecode starting from the given instruction pointer.
     */
    private boolean execute(ExecutionContext ctx) {
        byte[] bc = ctx.bytecode;
        int pc = 0;

        while (pc < bc.length) {
            int opcode = bc[pc] & 0xFF;
            RegExpOpcode op = RegExpOpcode.fromCode(opcode);

            switch (op) {
                case CHAR -> {
                    int ch = readU16(bc, pc + 1);
                    if (!ctx.matchChar(ch)) {
                        return false;
                    }
                    pc += 3;
                }

                case CHAR_I -> {
                    int ch = readU16(bc, pc + 1);
                    if (!ctx.matchCharIgnoreCase(ch)) {
                        return false;
                    }
                    pc += 3;
                }

                case CHAR32 -> {
                    int ch = readU32(bc, pc + 1);
                    if (!ctx.matchChar(ch)) {
                        return false;
                    }
                    pc += 5;
                }

                case CHAR32_I -> {
                    int ch = readU32(bc, pc + 1);
                    if (!ctx.matchCharIgnoreCase(ch)) {
                        return false;
                    }
                    pc += 5;
                }

                case DOT -> {
                    if (!ctx.matchDot()) {
                        return false;
                    }
                    pc += 1;
                }

                case ANY -> {
                    if (!ctx.matchAny()) {
                        return false;
                    }
                    pc += 1;
                }

                case LINE_START, LINE_START_M -> {
                    if (!ctx.matchLineStart(op == RegExpOpcode.LINE_START_M)) {
                        return false;
                    }
                    pc += 1;
                }

                case LINE_END, LINE_END_M -> {
                    if (!ctx.matchLineEnd(op == RegExpOpcode.LINE_END_M)) {
                        return false;
                    }
                    pc += 1;
                }

                case MATCH -> {
                    // Successful match
                    return true;
                }

                case SAVE_START -> {
                    int captureIndex = bc[pc + 1] & 0xFF;
                    ctx.saveStart(captureIndex);
                    pc += 2;
                }

                case SAVE_END -> {
                    int captureIndex = bc[pc + 1] & 0xFF;
                    ctx.saveEnd(captureIndex);
                    pc += 2;
                }

                default -> {
                    // Unsupported opcode - fail match
                    return false;
                }
            }
        }

        return false;
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
     * Execution context for a single match attempt.
     */
    private static class ExecutionContext {
        final String input;
        final byte[] bytecode;
        final int[] codePoints;
        final int captureCount;
        final boolean ignoreCase;
        final boolean multiline;
        final boolean dotAll;
        final boolean unicode;

        int pos;  // Current position in code points
        int[] captureStarts;
        int[] captureEnds;

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

        void reset(int startPos) {
            this.pos = startPos;
            Arrays.fill(captureStarts, -1);
            Arrays.fill(captureEnds, -1);
            if (captureCount > 0) {
                captureStarts[0] = startPos;
            }
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

        boolean matchAny() {
            if (pos >= codePoints.length) {
                return false;
            }
            pos++;
            return true;
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

        void saveStart(int captureIndex) {
            if (captureIndex < captureCount) {
                captureStarts[captureIndex] = pos;
            }
        }

        void saveEnd(int captureIndex) {
            if (captureIndex < captureCount) {
                captureEnds[captureIndex] = pos;
            }
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
         * Get the full matched string.
         */
        public String getMatch() {
            return matched && captures != null && captures.length > 0 ? captures[0] : null;
        }

        /**
         * Get a specific capture group.
         */
        public String getCapture(int index) {
            return matched && captures != null && index >= 0 && index < captures.length
                    ? captures[index]
                    : null;
        }
    }
}
