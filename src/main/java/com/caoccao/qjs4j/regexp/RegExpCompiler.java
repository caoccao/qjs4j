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

import com.caoccao.qjs4j.util.DynamicBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles regex patterns to bytecode.
 * Based on QuickJS libregexp.c compiler.
 * <p>
 * This is a simplified initial implementation that handles basic patterns.
 * Full ES2020 regex syntax support will be added incrementally.
 */
public final class RegExpCompiler {

    private int captureCount;
    private List<String> groupNames;

    /**
     * Compile a regex pattern to bytecode.
     *
     * @param pattern The regex pattern string
     * @param flags   The regex flags (g, i, m, s, u, y, d, v)
     * @return Compiled bytecode
     * @throws RegExpSyntaxException if the pattern is invalid
     */
    public RegExpBytecode compile(String pattern, String flags) {
        if (pattern == null) {
            throw new RegExpSyntaxException("Pattern cannot be null");
        }

        int flagBits = RegExpBytecode.parseFlags(flags);
        captureCount = 1; // Capture group 0 (the entire match)
        groupNames = new ArrayList<>();

        DynamicBuffer buffer = new DynamicBuffer(256);
        CompileContext context = new CompileContext(pattern, flagBits, buffer);

        try {
            compilePattern(context);
            // End with MATCH opcode
            buffer.appendU8(RegExpOpcode.MATCH.getCode());

            return new RegExpBytecode(
                    buffer.toByteArray(),
                    flagBits,
                    captureCount,
                    groupNames.isEmpty() ? null : groupNames.toArray(new String[0])
            );
        } catch (Exception e) {
            throw new RegExpSyntaxException("Failed to compile pattern: " + e.getMessage(), e);
        }
    }

    private void compileAlternative(CompileContext context) {
        // An alternative is a sequence of terms (atoms with optional quantifiers)
        while (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];

            // Check for end of alternative
            if (ch == '|' || ch == ')') {
                return;
            }

            // Parse a term (atom + optional quantifier)
            compileTerm(context);
        }
    }

    private void compileAtom(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            return;
        }

        int ch = context.codePoints[context.pos];

        switch (ch) {
            case '^' -> {
                context.buffer.appendU8(context.isMultiline() ?
                        RegExpOpcode.LINE_START_M.getCode() :
                        RegExpOpcode.LINE_START.getCode());
                context.pos++;
            }

            case '$' -> {
                context.buffer.appendU8(context.isMultiline() ?
                        RegExpOpcode.LINE_END_M.getCode() :
                        RegExpOpcode.LINE_END.getCode());
                context.pos++;
            }

            case '.' -> {
                context.buffer.appendU8(context.isDotAll() ?
                        RegExpOpcode.ANY.getCode() :
                        RegExpOpcode.DOT.getCode());
                context.pos++;
            }

            case '\\' -> {
                context.pos++;
                compileEscape(context);
            }

            case '[' -> {
                compileCharacterClass(context);
            }

            case '(' -> {
                compileGroup(context);
            }

            case '*', '+', '?', '{' -> {
                throw new RegExpSyntaxException("Nothing to repeat at position " + context.pos);
            }

            case ')' -> {
                // Don't consume - let parent handle it
            }

            case '|' -> {
                // Don't consume - let parent handle it
            }

            default -> {
                // Literal character
                compileLiteralChar(context, ch);
                context.pos++;
            }
        }
    }

    private void compileCharacterClass(CompileContext context) {
        // Character classes [abc], [^abc], [a-z]
        context.pos++; // Skip '['

        boolean inverted = false;
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '^') {
            inverted = true;
            context.pos++;
        }

        List<Integer> ranges = new ArrayList<>();

        while (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];

            if (ch == ']') {
                context.pos++;
                break;
            }

            if (ch == '\\') {
                context.pos++;
                if (context.pos >= context.codePoints.length) {
                    throw new RegExpSyntaxException("Incomplete escape in character class");
                }
                ch = parseClassEscape(context, ranges);
                if (ch == -1) {
                    // It was a character class escape like \d, \w, \s
                    // The ranges were added by parseClassEscape
                    continue;
                }
            } else {
                context.pos++;
            }

            // Check for range
            int start = ch;
            int end = ch;

            if (context.pos < context.codePoints.length &&
                    context.codePoints[context.pos] == '-' &&
                    context.pos + 1 < context.codePoints.length &&
                    context.codePoints[context.pos + 1] != ']') {

                context.pos++; // Skip '-'

                int rangeCh = context.codePoints[context.pos];
                if (rangeCh == '\\') {
                    context.pos++;
                    if (context.pos >= context.codePoints.length) {
                        throw new RegExpSyntaxException("Incomplete escape in character class range");
                    }
                    end = parseClassEscape(context, ranges);
                    if (end == -1) {
                        throw new RegExpSyntaxException("Invalid range in character class");
                    }
                } else {
                    end = rangeCh;
                    context.pos++;
                }

                if (start > end) {
                    throw new RegExpSyntaxException("Range out of order in character class");
                }
            }

            ranges.add(start);
            ranges.add(end);
        }

        // Emit RANGE/NOT_RANGE or RANGE_I/NOT_RANGE_I opcode based on inverted flag
        if (inverted) {
            context.buffer.appendU8(context.isIgnoreCase() ?
                    RegExpOpcode.NOT_RANGE_I.getCode() :
                    RegExpOpcode.NOT_RANGE.getCode());
        } else {
            context.buffer.appendU8(context.isIgnoreCase() ?
                    RegExpOpcode.RANGE_I.getCode() :
                    RegExpOpcode.RANGE.getCode());
        }

        // Calculate size: 2 bytes for count + (numRanges * 8 bytes per range)
        int numRanges = ranges.size() / 2;
        int dataSize = 2 + (numRanges * 8);

        context.buffer.appendU16(dataSize);
        context.buffer.appendU16(numRanges);

        for (int i = 0; i < ranges.size(); i += 2) {
            context.buffer.appendU32(ranges.get(i));   // start
            context.buffer.appendU32(ranges.get(i + 1)); // end
        }
    }

    private void compileDisjunction(CompileContext context) {
        // A disjunction is a sequence of alternatives separated by |
        int start = context.buffer.size();

        // Compile first alternative
        compileAlternative(context);

        // Handle additional alternatives
        while (context.pos < context.codePoints.length && context.codePoints[context.pos] == '|') {
            context.pos++; // Skip '|'

            int len = context.buffer.size() - start;

            // Insert a SPLIT at the start to choose between alternatives
            context.buffer.insert(start, 5);
            context.buffer.setU8(start, RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
            context.buffer.setU32(start + 1, len + 5);

            // Emit GOTO to skip the next alternative
            int gotoPos = context.buffer.size();
            context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
            context.buffer.appendU32(0); // Placeholder for jump offset

            // Compile next alternative
            compileAlternative(context);

            // Patch the GOTO offset
            int gotoOffset = context.buffer.size() - (gotoPos + 5);
            context.buffer.setU32(gotoPos + 1, gotoOffset);
        }
    }

    private void compileEscape(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Incomplete escape sequence");
        }

        int ch = context.codePoints[context.pos++];
        switch (ch) {
            case 'n' -> compileLiteralChar(context, '\n');
            case 'r' -> compileLiteralChar(context, '\r');
            case 't' -> compileLiteralChar(context, '\t');
            case 'f' -> compileLiteralChar(context, '\f');
            case 'v' -> compileLiteralChar(context, '\u000B');
            case 'd' -> {
                // \d - match digits [0-9]
                // Emit a simple character range check for now
                // This is a simplified implementation - proper implementation would use RANGE opcode
                context.buffer.appendU8(RegExpOpcode.RANGE.getCode());
                context.buffer.appendU16(10); // length of range data = 1 range * 2 codepoints * 4 bytes + 2 bytes count
                context.buffer.appendU16(1);  // 1 range
                context.buffer.appendU32('0');
                context.buffer.appendU32('9');
            }
            case 'D' -> {
                // \D - match non-digits (not [0-9])
                context.buffer.appendU8(RegExpOpcode.NOT_RANGE.getCode());
                context.buffer.appendU16(10); // length of range data = 1 range * 2 codepoints * 4 bytes + 2 bytes count
                context.buffer.appendU16(1);  // 1 range
                context.buffer.appendU32('0');
                context.buffer.appendU32('9');
            }
            case 'w' -> {
                // \w - match word characters [a-zA-Z0-9_]
                // Emit RANGE opcode with ranges for word characters
                context.buffer.appendU8(RegExpOpcode.RANGE.getCode());
                // Size: 2 bytes for count + (4 ranges * 2 values * 4 bytes each)
                context.buffer.appendU16(2 + (4 * 8));
                context.buffer.appendU16(4); // 4 ranges
                // Range 1: '0'-'9'
                context.buffer.appendU32('0');
                context.buffer.appendU32('9');
                // Range 2: 'A'-'Z'
                context.buffer.appendU32('A');
                context.buffer.appendU32('Z');
                // Range 3: '_'-'_'
                context.buffer.appendU32('_');
                context.buffer.appendU32('_');
                // Range 4: 'a'-'z'
                context.buffer.appendU32('a');
                context.buffer.appendU32('z');
            }
            case 'W' -> {
                // \W - match non-word characters (not [a-zA-Z0-9_])
                // Emit NOT_RANGE opcode with same ranges as \w
                context.buffer.appendU8(RegExpOpcode.NOT_RANGE.getCode());
                // Size: 2 bytes for count + (4 ranges * 2 values * 4 bytes each)
                context.buffer.appendU16(2 + (4 * 8));
                context.buffer.appendU16(4); // 4 ranges
                // Range 1: '0'-'9'
                context.buffer.appendU32('0');
                context.buffer.appendU32('9');
                // Range 2: 'A'-'Z'
                context.buffer.appendU32('A');
                context.buffer.appendU32('Z');
                // Range 3: '_'-'_'
                context.buffer.appendU32('_');
                context.buffer.appendU32('_');
                // Range 4: 'a'-'z'
                context.buffer.appendU32('a');
                context.buffer.appendU32('z');
            }
            case 's' -> {
                // \s - match whitespace
                context.buffer.appendU8(RegExpOpcode.SPACE.getCode());
            }
            case 'S' -> {
                // \S - match non-whitespace
                context.buffer.appendU8(RegExpOpcode.NOT_SPACE.getCode());
            }
            case 'b' -> {
                // \b - match word boundary
                context.buffer.appendU8(context.isIgnoreCase() && context.isUnicode() ?
                        RegExpOpcode.WORD_BOUNDARY_I.getCode() :
                        RegExpOpcode.WORD_BOUNDARY.getCode());
            }
            case 'B' -> {
                // \B - match non-word boundary
                context.buffer.appendU8(context.isIgnoreCase() && context.isUnicode() ?
                        RegExpOpcode.NOT_WORD_BOUNDARY_I.getCode() :
                        RegExpOpcode.NOT_WORD_BOUNDARY.getCode());
            }
            case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                // Back reference \1, \2, etc.
                int groupNum = ch - '0';
                // Check for multi-digit group numbers
                while (context.pos < context.codePoints.length) {
                    int nextCh = context.codePoints[context.pos];
                    if (nextCh >= '0' && nextCh <= '9') {
                        groupNum = groupNum * 10 + (nextCh - '0');
                        context.pos++;
                    } else {
                        break;
                    }
                }

                if (groupNum >= captureCount) {
                    // Invalid back reference - treat as literal
                    compileLiteralChar(context, ch);
                } else {
                    context.buffer.appendU8(context.isIgnoreCase() ?
                            RegExpOpcode.BACK_REFERENCE_I.getCode() :
                            RegExpOpcode.BACK_REFERENCE.getCode());
                    context.buffer.appendU8(groupNum);
                }
            }
            case 'x' -> {
                // Hex escape \xHH
                if (context.pos + 1 >= context.codePoints.length) {
                    throw new RegExpSyntaxException("Incomplete hex escape");
                }
                int hex1 = hexValue(context.codePoints[context.pos]);
                int hex2 = hexValue(context.codePoints[context.pos + 1]);
                if (hex1 == -1 || hex2 == -1) {
                    throw new RegExpSyntaxException("Invalid hex escape");
                }
                context.pos += 2;
                int value = (hex1 << 4) | hex2;
                compileLiteralChar(context, value);
            }
            case 'u' -> {
                // Unicode escape \\uHHHH or \\u{H...}
                if (context.pos >= context.codePoints.length) {
                    throw new RegExpSyntaxException("Incomplete unicode escape");
                }
                if (context.codePoints[context.pos] == '{') {
                    // \\u{H...} format
                    context.pos++; // Skip '{'
                    int value = 0;
                    int digitCount = 0;
                    while (context.pos < context.codePoints.length) {
                        int hexCh = context.codePoints[context.pos];
                        if (hexCh == '}') {
                            context.pos++;
                            break;
                        }
                        int hexVal = hexValue(hexCh);
                        if (hexVal == -1) {
                            throw new RegExpSyntaxException("Invalid unicode escape");
                        }
                        value = (value << 4) | hexVal;
                        digitCount++;
                        context.pos++;
                        if (digitCount > 6) {
                            throw new RegExpSyntaxException("Unicode escape too long");
                        }
                    }
                    if (digitCount == 0) {
                        throw new RegExpSyntaxException("Empty unicode escape");
                    }
                    compileLiteralChar(context, value);
                } else {
                    // \\uHHHH format
                    if (context.pos + 3 >= context.codePoints.length) {
                        throw new RegExpSyntaxException("Incomplete unicode escape");
                    }
                    int value = 0;
                    for (int i = 0; i < 4; i++) {
                        int hexVal = hexValue(context.codePoints[context.pos + i]);
                        if (hexVal == -1) {
                            throw new RegExpSyntaxException("Invalid unicode escape");
                        }
                        value = (value << 4) | hexVal;
                    }
                    context.pos += 4;
                    compileLiteralChar(context, value);
                }
            }
            default -> {
                // Literal escaped character
                compileLiteralChar(context, ch);
            }
        }
    }

    private void compileGroup(CompileContext context) {
        // Capture groups (...) or non-capturing groups (?:...)
        context.pos++; // Skip '('

        // Check for special group syntax
        boolean isCapturing = true;
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '?') {
            context.pos++;
            if (context.pos >= context.codePoints.length) {
                throw new RegExpSyntaxException("Incomplete group syntax");
            }
            int groupType = context.codePoints[context.pos++];
            if (groupType == ':') {
                // Non-capturing group (?:...)
                isCapturing = false;
            } else if (groupType == '=') {
                // Positive lookahead (?=...)
                compileLookahead(context, false);
                return;
            } else if (groupType == '!') {
                // Negative lookahead (?!...)
                compileLookahead(context, true);
                return;
            } else {
                throw new RegExpSyntaxException("Unknown group type: (?" + (char) groupType);
            }
        }

        int groupIndex = -1;
        if (isCapturing) {
            groupIndex = captureCount++;
            // Save start
            context.buffer.appendU8(RegExpOpcode.SAVE_START.getCode());
            context.buffer.appendU8(groupIndex);
        }

        // Compile group contents
        compileDisjunction(context);

        if (isCapturing) {
            // Save end
            context.buffer.appendU8(RegExpOpcode.SAVE_END.getCode());
            context.buffer.appendU8(groupIndex);
        }

        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ')') {
            throw new RegExpSyntaxException("Unclosed group");
        }
        context.pos++; // Skip ')'
    }

    private void compileLiteralChar(CompileContext context, int ch) {
        if (ch <= 0xFFFF) {
            context.buffer.appendU8(context.isIgnoreCase() ?
                    RegExpOpcode.CHAR_I.getCode() :
                    RegExpOpcode.CHAR.getCode());
            context.buffer.appendU16(ch);
        } else {
            context.buffer.appendU8(context.isIgnoreCase() ?
                    RegExpOpcode.CHAR32_I.getCode() :
                    RegExpOpcode.CHAR32.getCode());
            context.buffer.appendU32(ch);
        }
    }

    private void compileLookahead(CompileContext context, boolean isNegative) {
        // (?=...) positive lookahead or (?!...) negative lookahead
        int lookaheadStart = context.buffer.size();

        // Emit LOOKAHEAD or NEGATIVE_LOOKAHEAD opcode
        context.buffer.appendU8(isNegative ?
                RegExpOpcode.NEGATIVE_LOOKAHEAD.getCode() :
                RegExpOpcode.LOOKAHEAD.getCode());
        context.buffer.appendU32(0); // Placeholder for length

        // Compile lookahead contents
        compileDisjunction(context);

        // Emit LOOKAHEAD_MATCH or NEGATIVE_LOOKAHEAD_MATCH
        context.buffer.appendU8(isNegative ?
                RegExpOpcode.NEGATIVE_LOOKAHEAD_MATCH.getCode() :
                RegExpOpcode.LOOKAHEAD_MATCH.getCode());

        // Patch the length
        int lookaheadLen = context.buffer.size() - (lookaheadStart + 5);
        context.buffer.setU32(lookaheadStart + 1, lookaheadLen);

        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ')') {
            throw new RegExpSyntaxException("Unclosed lookahead");
        }
        context.pos++; // Skip ')'
    }

    private void compilePattern(CompileContext context) {
        // Save start of capture group 0
        context.buffer.appendU8(RegExpOpcode.SAVE_START.getCode());
        context.buffer.appendU8(0); // Capture group 0

        compileDisjunction(context);

        // Save end of capture group 0
        context.buffer.appendU8(RegExpOpcode.SAVE_END.getCode());
        context.buffer.appendU8(0); // Capture group 0
    }

    private void compileQuantifier(CompileContext context, int atomStart, int captureCountBeforeAtom) {
        int ch = context.codePoints[context.pos];
        int min, max;
        boolean greedy = true;

        // Parse quantifier
        switch (ch) {
            case '*' -> {
                min = 0;
                max = Integer.MAX_VALUE;
                context.pos++;
            }
            case '+' -> {
                min = 1;
                max = Integer.MAX_VALUE;
                context.pos++;
            }
            case '?' -> {
                min = 0;
                max = 1;
                context.pos++;
            }
            case '{' -> {
                // Parse {n} or {n,} or {n,m}
                context.pos++;
                int[] result = parseQuantifierBounds(context);
                min = result[0];
                max = result[1];
                if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '}') {
                    throw new RegExpSyntaxException("Unclosed quantifier at position " + context.pos);
                }
                context.pos++;
            }
            default -> {
                return; // Not a quantifier
            }
        }

        // Check for non-greedy modifier '?'
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '?') {
            greedy = false;
            context.pos++;
        }

        // Get the atom bytecode
        int atomEnd = context.buffer.size();
        int atomSize = atomEnd - atomStart;
        byte[] atomCode = context.buffer.getRange(atomStart, atomSize);

        // Remove the atom from the buffer
        context.buffer.truncate(atomStart);

        // Implement quantifier using SPLIT opcodes
        if (min == 0 && max == 1) {
            // ? quantifier: SPLIT then atom
            // Greedy: try atom first, then skip
            // Non-greedy: try skip first, then atom
            context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_GOTO_FIRST.getCode() :
                    RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
            context.buffer.appendU32(atomSize);
            context.buffer.append(atomCode);
        } else if (min == 0 && max == Integer.MAX_VALUE) {
            // * quantifier: SPLIT (skip or continue), atom, GOTO back
            // Greedy: try atom first (SPLIT_NEXT_FIRST), then allow skip
            // Structure:
            //   loopStart: SPLIT_NEXT_FIRST +atomSize+5 (to end)
            //              atom
            //              GOTO -atomSize-5-5 (back to loopStart)
            //   end:
            int loopStart = context.buffer.size();
            context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() :
                    RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
            context.buffer.appendU32(atomSize + 5); // Skip atom and GOTO
            context.buffer.append(atomCode);
            context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
            int offset = loopStart - (context.buffer.size() + 4);
            context.buffer.appendU32(offset);
        } else if (min == 1 && max == Integer.MAX_VALUE) {
            // + quantifier: atom, then SPLIT back or continue
            // Structure:
            //   loopStart: atom
            //              SPLIT (greedy: try loop first, non-greedy: try continue first)
            context.buffer.append(atomCode);
            int loopStart = context.buffer.size() - atomSize;
            context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_GOTO_FIRST.getCode() :
                    RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
            int offset = loopStart - (context.buffer.size() + 4);
            context.buffer.appendU32(offset);
        } else {
            // {n,m} quantifier: unroll min times, then optional max-min times
            for (int i = 0; i < min; i++) {
                context.buffer.append(atomCode);
            }
            if (max != min) {
                int remaining = max == Integer.MAX_VALUE ? 100 : (max - min); // Cap infinite to reasonable number
                for (int i = 0; i < remaining; i++) {
                    context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_GOTO_FIRST.getCode() :
                            RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
                    context.buffer.appendU32(atomSize);
                    context.buffer.append(atomCode);
                }
            }
        }
    }

    private void compileTerm(CompileContext context) {
        int atomStart = context.buffer.size();
        int captureCountBeforeAtom = captureCount;

        // Parse the atom
        compileAtom(context);

        // Check for quantifier
        if (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];

            if (ch == '*' || ch == '+' || ch == '?' || ch == '{') {
                compileQuantifier(context, atomStart, captureCountBeforeAtom);
            }
        }
    }

    private int hexValue(int ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        } else if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        return -1;
    }

    private int parseClassEscape(CompileContext context, List<Integer> ranges) {
        int ch = context.codePoints[context.pos++];

        return switch (ch) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'f' -> '\f';
            case 'v' -> '\u000B';
            case 'd' -> {
                // \d - digits [0-9]
                ranges.add((int) '0');
                ranges.add((int) '9');
                yield -1;
            }
            case 'D' -> {
                // \D - non-digits (we can't represent this in a character class easily)
                // For now, throw an error as it's complex to handle
                throw new RegExpSyntaxException("\\D not supported in character class");
            }
            case 'w' -> {
                // \w - word characters [a-zA-Z0-9_]
                ranges.add((int) '0');
                ranges.add((int) '9');
                ranges.add((int) 'A');
                ranges.add((int) 'Z');
                ranges.add((int) '_');
                ranges.add((int) '_');
                ranges.add((int) 'a');
                ranges.add((int) 'z');
                yield -1;
            }
            case 'W' -> {
                // \W - non-word characters (complex to represent)
                throw new RegExpSyntaxException("\\W not supported in character class");
            }
            case 's' -> {
                // \s - whitespace characters
                ranges.add((int) ' ');
                ranges.add((int) ' ');
                ranges.add((int) '\t');
                ranges.add((int) '\t');
                ranges.add((int) '\n');
                ranges.add((int) '\n');
                ranges.add((int) '\r');
                ranges.add((int) '\r');
                ranges.add((int) '\f');
                ranges.add((int) '\f');
                ranges.add(0x000B); // \v
                ranges.add(0x000B);
                yield -1;
            }
            case 'S' -> {
                // \S - non-whitespace (complex to represent)
                throw new RegExpSyntaxException("\\S not supported in character class");
            }
            default -> ch; // Literal escaped character
        };
    }

    private int[] parseQuantifierBounds(CompileContext context) {
        int min = 0, max;

        // Parse first number
        while (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];
            if (ch >= '0' && ch <= '9') {
                min = min * 10 + (ch - '0');
                context.pos++;
            } else {
                break;
            }
        }

        // Check for comma
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == ',') {
            context.pos++;

            // Check if there's a max value
            if (context.pos < context.codePoints.length &&
                    context.codePoints[context.pos] >= '0' &&
                    context.codePoints[context.pos] <= '9') {
                max = 0;
                while (context.pos < context.codePoints.length) {
                    int ch = context.codePoints[context.pos];
                    if (ch >= '0' && ch <= '9') {
                        max = max * 10 + (ch - '0');
                        context.pos++;
                    } else {
                        break;
                    }
                }
            } else {
                max = Integer.MAX_VALUE; // {n,} means n or more
            }
        } else {
            max = min; // {n} means exactly n
        }

        return new int[]{min, max};
    }

    private static class CompileContext {
        final DynamicBuffer buffer;
        final int[] codePoints;
        final int flags;
        final String pattern;
        int pos;

        CompileContext(String pattern, int flags, DynamicBuffer buffer) {
            this.pattern = pattern;
            this.codePoints = pattern.codePoints().toArray();
            this.flags = flags;
            this.buffer = buffer;
            this.pos = 0;
        }

        boolean isDotAll() {
            return (flags & RegExpBytecode.FLAG_DOTALL) != 0;
        }

        boolean isIgnoreCase() {
            return (flags & RegExpBytecode.FLAG_IGNORECASE) != 0;
        }

        boolean isMultiline() {
            return (flags & RegExpBytecode.FLAG_MULTILINE) != 0;
        }

        boolean isUnicode() {
            return (flags & RegExpBytecode.FLAG_UNICODE) != 0;
        }
    }

    /**
     * Exception thrown when regex pattern has syntax errors.
     */
    public static class RegExpSyntaxException extends RuntimeException {
        public RegExpSyntaxException(String message) {
            super(message);
        }

        public RegExpSyntaxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
