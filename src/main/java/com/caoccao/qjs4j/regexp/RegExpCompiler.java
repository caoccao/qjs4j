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

import com.caoccao.qjs4j.unicode.UnicodeData;
import com.caoccao.qjs4j.utils.DynamicBuffer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/**
 * Compiles regex patterns to bytecode.
 * Based on QuickJS libregexp.c compiler.
 * <p>
 * This is a simplified initial implementation that handles basic patterns.
 * Full ES2020 regex syntax support will be added incrementally.
 */
public final class RegExpCompiler {

    private static final Map<String, int[]> BINARY_PROPERTY_RANGES_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, int[]> GENERAL_CATEGORY_RANGES_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_UNICODE_CODE_POINT = 0x10FFFF;
    private static final Map<String, int[]> SCRIPT_RANGES_CACHE = new ConcurrentHashMap<>();
    private int captureCount;
    private List<String> groupNames;
    private Map<String, Integer> namedCaptureIndices;
    private int totalCaptureCount;

    private void appendRanges(List<Integer> ranges, int[] propertyRanges) {
        for (int propertyRange : propertyRanges) {
            ranges.add(propertyRange);
        }
    }

    private int[] buildRanges(IntPredicate predicate) {
        List<Integer> ranges = new ArrayList<>();
        int rangeStart = -1;
        for (int codePoint = 0; codePoint <= MAX_UNICODE_CODE_POINT; codePoint++) {
            if (predicate.test(codePoint)) {
                if (rangeStart < 0) {
                    rangeStart = codePoint;
                }
            } else if (rangeStart >= 0) {
                ranges.add(rangeStart);
                ranges.add(codePoint - 1);
                rangeStart = -1;
            }
        }
        if (rangeStart >= 0) {
            ranges.add(rangeStart);
            ranges.add(MAX_UNICODE_CODE_POINT);
        }
        return toIntArray(ranges);
    }

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
        groupNames.add(null); // Group 0 has no name
        namedCaptureIndices = scanNamedCaptureGroups(pattern);
        if (!namedCaptureIndices.isEmpty()) {
            flagBits |= RegExpBytecode.FLAG_NAMED_GROUPS;
        }

        DynamicBuffer buffer = new DynamicBuffer(256);
        CompileContext context = new CompileContext(pattern, flagBits, buffer);

        try {
            compilePattern(context);
            // End with MATCH opcode
            buffer.appendU8(RegExpOpcode.MATCH.getCode());

            String[] compiledGroupNames = null;
            if (!namedCaptureIndices.isEmpty()) {
                compiledGroupNames = new String[captureCount];
                for (int i = 0; i < captureCount && i < groupNames.size(); i++) {
                    compiledGroupNames[i] = groupNames.get(i);
                }
            }

            return new RegExpBytecode(
                    buffer.toByteArray(),
                    flagBits,
                    captureCount,
                    compiledGroupNames
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
        context.lastAtomCanRepeat = true;

        int ch = context.codePoints[context.pos];

        switch (ch) {
            case '^' -> {
                context.lastAtomCanRepeat = false;
                context.buffer.appendU8(context.isMultiline() ?
                        RegExpOpcode.LINE_START_M.getCode() :
                        RegExpOpcode.LINE_START.getCode());
                context.pos++;
            }

            case '$' -> {
                context.lastAtomCanRepeat = false;
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

            case '*', '+', '?' -> {
                throw new RegExpSyntaxException("Nothing to repeat at position " + context.pos);
            }

            case '{' -> {
                if (startsWithValidQuantifier(context)) {
                    throw new RegExpSyntaxException("Nothing to repeat at position " + context.pos);
                }
                // In unicode mode, lone '{' is a syntax error (not AnnexB literal)
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Lone quantifier bracket at position " + context.pos);
                }
                compileLiteralChar(context, ch);
                context.pos++;
            }

            case '}' -> {
                // In unicode mode, lone '}' outside a quantifier is a syntax error
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Lone quantifier bracket at position " + context.pos);
                }
                compileLiteralChar(context, ch);
                context.pos++;
            }

            case ')' -> {
                context.lastAtomCanRepeat = false;
                // Don't consume - let parent handle it
            }

            case '|' -> {
                context.lastAtomCanRepeat = false;
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
                        if (context.isUnicodeMode()) {
                            throw new RegExpSyntaxException("Invalid range in character class");
                        }
                        // Annex B (B.1.4.1.1): In non-unicode mode, when the right side of a
                        // range is a multi-character class (like \d, \w, \s), treat as union of
                        // left atom, '-', and the class. The class ranges were already added by
                        // parseClassEscape(). Add left atom and '-' as single characters.
                        ranges.add(start);
                        ranges.add(start);
                        ranges.add((int) '-');
                        ranges.add((int) '-');
                        continue;
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
            case 'k' -> {
                // AnnexB: In non-unicode mode with no named capture groups,
                // \k is an identity escape matching literal 'k'
                if (!context.isUnicodeMode() && namedCaptureIndices.isEmpty()) {
                    compileLiteralChar(context, 'k');
                    break;
                }
                if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '<') {
                    throw new RegExpSyntaxException("expecting group name");
                }
                context.pos++; // Skip '<'

                GroupNameParseResult groupNameParseResult = parseGroupName(context.codePoints, context.pos);
                if (groupNameParseResult == null) {
                    throw new RegExpSyntaxException("invalid group name");
                }
                context.pos = groupNameParseResult.nextPos();

                Integer groupNum = namedCaptureIndices.get(groupNameParseResult.name());
                if (groupNum == null) {
                    throw new RegExpSyntaxException("group name not defined");
                }

                context.buffer.appendU8(context.isIgnoreCase() ?
                        RegExpOpcode.BACK_REFERENCE_I.getCode() :
                        RegExpOpcode.BACK_REFERENCE.getCode());
                context.buffer.appendU8(groupNum);
            }
            case 'p', 'P' -> {
                if (!context.isUnicodeMode()) {
                    compileLiteralChar(context, ch);
                    break;
                }
                int[] propertyRanges = parseUnicodePropertyEscape(context);
                emitRanges(context, propertyRanges, ch == 'P');
            }
            case '0' -> {
                // \0 escape: null character or legacy octal escape
                if (context.isUnicodeMode()) {
                    // Unicode mode: \0 is only valid when not followed by a decimal digit
                    if (context.pos < context.codePoints.length) {
                        int next = context.codePoints[context.pos];
                        if (next >= '0' && next <= '9') {
                            throw new RegExpSyntaxException("Invalid escape sequence");
                        }
                    }
                    compileLiteralChar(context, 0);
                } else {
                    // Non-Unicode mode: legacy octal escape (\0, \00, \000, etc.)
                    int value = parseLegacyOctalEscape(context, 0);
                    compileLiteralChar(context, value);
                }
            }
            case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                // Back reference \1, \2, etc.
                // Use totalCaptureCount (pre-scanned) to support forward references
                int groupNum = ch - '0';
                int savedPos = context.pos;
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

                if (groupNum < totalCaptureCount) {
                    // Valid backreference (including forward references)
                    context.buffer.appendU8(context.isIgnoreCase() ?
                            RegExpOpcode.BACK_REFERENCE_I.getCode() :
                            RegExpOpcode.BACK_REFERENCE.getCode());
                    context.buffer.appendU8(groupNum);
                } else if (!context.isUnicodeMode()) {
                    // AnnexB: In non-unicode mode, invalid backreferences are
                    // treated as octal escapes (\1-\7) or identity escapes (\8-\9)
                    context.pos = savedPos; // Rewind consumed digits
                    int digit = ch - '0';
                    if (digit <= 7) {
                        // Octal escape: \1-\7 → U+0001-U+0007
                        // Also handle multi-digit octal: \12, \377, etc.
                        int octalValue = digit;
                        while (context.pos < context.codePoints.length) {
                            int nextCh = context.codePoints[context.pos];
                            if (nextCh >= '0' && nextCh <= '7') {
                                int newValue = octalValue * 8 + (nextCh - '0');
                                if (newValue > 0377) {
                                    break;
                                }
                                octalValue = newValue;
                                context.pos++;
                            } else {
                                break;
                            }
                        }
                        compileLiteralChar(context, octalValue);
                    } else {
                        // \8, \9 → identity escape (literal digit)
                        compileLiteralChar(context, ch);
                    }
                } else {
                    // Unicode mode: invalid backreference is a syntax error
                    throw new RegExpSyntaxException("Invalid backreference \\" + groupNum);
                }
            }
            case 'c' -> {
                // \c ControlLetter
                if (context.pos < context.codePoints.length) {
                    int next = context.codePoints[context.pos];
                    if ((next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z')) {
                        // Valid control escape: \cA-\cZ or \ca-\cz
                        context.pos++;
                        compileLiteralChar(context, next % 32);
                        break;
                    }
                }
                // AnnexB: In non-unicode mode, \c without valid control letter
                // is treated as literal backslash followed by literal 'c'
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Invalid control escape");
                }
                compileLiteralChar(context, '\\');
                compileLiteralChar(context, 'c');
            }
            case 'x' -> {
                // Hex escape \xHH
                boolean validHex = context.pos + 1 < context.codePoints.length
                        && hexValue(context.codePoints[context.pos]) != -1
                        && hexValue(context.codePoints[context.pos + 1]) != -1;
                if (validHex) {
                    int hex1 = hexValue(context.codePoints[context.pos]);
                    int hex2 = hexValue(context.codePoints[context.pos + 1]);
                    context.pos += 2;
                    int value = (hex1 << 4) | hex2;
                    compileLiteralChar(context, value);
                } else if (!context.isUnicodeMode()) {
                    // AnnexB: In non-unicode mode, \x without valid hex is identity escape
                    compileLiteralChar(context, 'x');
                } else {
                    throw new RegExpSyntaxException("Invalid hex escape");
                }
            }
            case 'u' -> {
                // Unicode escape \\uHHHH or \\u{H...}
                if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '{') {
                    if (context.isUnicodeMode()) {
                        // \\u{H...} format - only valid in unicode mode
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
                        // Non-unicode mode: backslash-u is identity escape, '{' handled by caller
                        compileLiteralChar(context, 'u');
                    }
                } else {
                    // Check for \\uHHHH format (4 valid hex digits)
                    boolean validUnicode = context.pos + 3 < context.codePoints.length
                            && hexValue(context.codePoints[context.pos]) != -1
                            && hexValue(context.codePoints[context.pos + 1]) != -1
                            && hexValue(context.codePoints[context.pos + 2]) != -1
                            && hexValue(context.codePoints[context.pos + 3]) != -1;
                    if (validUnicode) {
                        int value = 0;
                        for (int i = 0; i < 4; i++) {
                            value = (value << 4) | hexValue(context.codePoints[context.pos + i]);
                        }
                        context.pos += 4;
                        compileLiteralChar(context, value);
                    } else if (!context.isUnicodeMode()) {
                        // AnnexB: In non-unicode mode, backslash-u without valid hex is identity escape
                        compileLiteralChar(context, 'u');
                    } else {
                        throw new RegExpSyntaxException("Invalid unicode escape");
                    }
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
        String captureName = null;
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
                context.lastAtomCanRepeat = true;
                return;
            } else if (groupType == '!') {
                // Negative lookahead (?!...)
                compileLookahead(context, true);
                context.lastAtomCanRepeat = true;
                return;
            } else if (groupType == '<') {
                if (context.pos < context.codePoints.length &&
                        (context.codePoints[context.pos] == '=' || context.codePoints[context.pos] == '!')) {
                    boolean isNegative = context.codePoints[context.pos] == '!';
                    context.pos++;
                    compileLookbehind(context, isNegative);
                    context.lastAtomCanRepeat = false;
                    return;
                }

                GroupNameParseResult groupNameParseResult = parseGroupName(context.codePoints, context.pos);
                if (groupNameParseResult == null) {
                    throw new RegExpSyntaxException("invalid group name");
                }
                context.pos = groupNameParseResult.nextPos();
                captureName = groupNameParseResult.name();
            } else {
                throw new RegExpSyntaxException("Unknown group type: (?" + (char) groupType);
            }
        }

        int groupIndex = -1;
        if (isCapturing) {
            groupIndex = captureCount++;
            ensureGroupNameSize(groupIndex + 1);
            if (captureName != null) {
                groupNames.set(groupIndex, captureName);
            }
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
        context.lastAtomCanRepeat = true;
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

    private void compileLookbehind(CompileContext context, boolean isNegative) {
        // (?<=...) positive lookbehind or (?<!...) negative lookbehind
        int lookbehindStart = context.buffer.size();

        context.buffer.appendU8(isNegative ?
                RegExpOpcode.NEGATIVE_LOOKBEHIND.getCode() :
                RegExpOpcode.LOOKBEHIND.getCode());
        context.buffer.appendU32(0); // Placeholder for length

        compileDisjunction(context);

        context.buffer.appendU8(isNegative ?
                RegExpOpcode.NEGATIVE_LOOKBEHIND_MATCH.getCode() :
                RegExpOpcode.LOOKBEHIND_MATCH.getCode());

        int lookbehindLen = context.buffer.size() - (lookbehindStart + 5);
        context.buffer.setU32(lookbehindStart + 1, lookbehindLen);

        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ')') {
            throw new RegExpSyntaxException("Unclosed lookbehind");
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

        // Following QuickJS: check if the atom can match without advancing the position.
        // If so, we need to add SET_CHAR_POS/CHECK_ADVANCE to prevent infinite loops.
        boolean addZeroAdvanceCheck = needCheckAdvance(atomCode);

        // Implement quantifier using SPLIT opcodes
        if (min == 0 && max == 1) {
            // ? quantifier: SPLIT then atom
            // Greedy: try atom first, then skip
            // Non-greedy: try skip first, then atom
            context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() :
                    RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
            context.buffer.appendU32(atomSize);
            context.buffer.append(atomCode);
        } else if (min == 0 && max == Integer.MAX_VALUE) {
            // * quantifier: SPLIT (skip or continue), SET_CHAR_POS?, atom, CHECK_ADVANCE?, GOTO back
            // Following QuickJS: if atom can match empty, insert SET_CHAR_POS before atom and
            // CHECK_ADVANCE after atom to prevent infinite loops on zero-width patterns.
            int advCheckSize = addZeroAdvanceCheck ? 4 : 0; // SET_CHAR_POS(2) + CHECK_ADVANCE(2)
            int loopStart = context.buffer.size();
            context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() :
                    RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
            context.buffer.appendU32(atomSize + 5 + advCheckSize); // Skip atom + GOTO + advance checks
            if (addZeroAdvanceCheck) {
                context.buffer.appendU8(RegExpOpcode.SET_CHAR_POS.getCode());
                context.buffer.appendU8(0); // register 0
            }
            context.buffer.append(atomCode);
            if (addZeroAdvanceCheck) {
                context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                context.buffer.appendU8(0); // register 0
            }
            context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
            int offset = loopStart - (context.buffer.size() + 4);
            context.buffer.appendU32(offset);
        } else if (min == 1 && max == Integer.MAX_VALUE) {
            // + quantifier: atom, then SPLIT back or continue
            // With advance check: atom, SET_CHAR_POS, SPLIT, SET_CHAR_POS, atom, CHECK_ADVANCE, GOTO SPLIT
            if (addZeroAdvanceCheck) {
                // First iteration (no advance check needed)
                context.buffer.append(atomCode);
                // Loop iterations with advance check
                int loopStart = context.buffer.size();
                context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() :
                        RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
                // Offset to skip: SET_CHAR_POS(2) + atom + CHECK_ADVANCE(2) + GOTO(5)
                int skipSize = 2 + atomSize + 2 + 5;
                context.buffer.appendU32(skipSize);
                context.buffer.appendU8(RegExpOpcode.SET_CHAR_POS.getCode());
                context.buffer.appendU8(0);
                context.buffer.append(atomCode);
                context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                context.buffer.appendU8(0);
                context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
                int gotoOffset = loopStart - (context.buffer.size() + 4);
                context.buffer.appendU32(gotoOffset);
            } else {
                context.buffer.append(atomCode);
                int loopStart = context.buffer.size() - atomSize;
                context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_GOTO_FIRST.getCode() :
                        RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
                int offset = loopStart - (context.buffer.size() + 4);
                context.buffer.appendU32(offset);
            }
        } else {
            // {n,m} quantifier: unroll min times, then optional max-min times
            for (int i = 0; i < min; i++) {
                context.buffer.append(atomCode);
            }
            if (max != min) {
                if (max == Integer.MAX_VALUE) {
                    // {n,} - unbounded: use loop with advance check if needed
                    int advCheckSize = addZeroAdvanceCheck ? 4 : 0;
                    int loopStart = context.buffer.size();
                    context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() :
                            RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
                    context.buffer.appendU32(atomSize + 5 + advCheckSize);
                    if (addZeroAdvanceCheck) {
                        context.buffer.appendU8(RegExpOpcode.SET_CHAR_POS.getCode());
                        context.buffer.appendU8(0);
                    }
                    context.buffer.append(atomCode);
                    if (addZeroAdvanceCheck) {
                        context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                        context.buffer.appendU8(0);
                    }
                    context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
                    int offset = loopStart - (context.buffer.size() + 4);
                    context.buffer.appendU32(offset);
                } else {
                    int remaining = max - min;
                    for (int i = 0; i < remaining; i++) {
                        context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() :
                                RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
                        context.buffer.appendU32(atomSize);
                        context.buffer.append(atomCode);
                    }
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

            if (!context.lastAtomCanRepeat && (ch == '*' || ch == '+' || ch == '?' ||
                    (ch == '{' && startsWithValidQuantifier(context)))) {
                throw new RegExpSyntaxException("Nothing to repeat at position " + context.pos);
            }

            if (ch == '*' || ch == '+' || ch == '?' || (ch == '{' && startsWithValidQuantifier(context))) {
                compileQuantifier(context, atomStart, captureCountBeforeAtom);
            }
        }
    }

    private void emitRanges(CompileContext context, int[] ranges, boolean inverted) {
        context.buffer.appendU8(inverted
                ? (context.isIgnoreCase() ? RegExpOpcode.NOT_RANGE_I.getCode() : RegExpOpcode.NOT_RANGE.getCode())
                : (context.isIgnoreCase() ? RegExpOpcode.RANGE_I.getCode() : RegExpOpcode.RANGE.getCode()));

        int numRanges = ranges.length / 2;
        int dataSize = 2 + (numRanges * 8);
        context.buffer.appendU16(dataSize);
        context.buffer.appendU16(numRanges);

        for (int i = 0; i < ranges.length; i += 2) {
            context.buffer.appendU32(ranges[i]);
            context.buffer.appendU32(ranges[i + 1]);
        }
    }

    private void ensureGroupNameSize(int size) {
        while (groupNames.size() < size) {
            groupNames.add(null);
        }
    }

    private IntPredicate getBinaryPropertyPredicate(String propertyName) {
        return switch (propertyName) {
            case "Alphabetic" -> Character::isAlphabetic;
            case "Any" -> codePoint -> true;
            case "ASCII" -> codePoint -> codePoint <= 0x7F;
            case "Assigned" -> codePoint -> Character.getType(codePoint) != Character.UNASSIGNED;
            case "ID_Continue" -> UnicodeData::isIdentifierPart;
            case "ID_Start" -> UnicodeData::isIdentifierStart;
            case "Join_Control" -> codePoint -> codePoint == 0x200C || codePoint == 0x200D;
            case "Lowercase" -> Character::isLowerCase;
            case "Math" -> codePoint -> Character.getType(codePoint) == Character.MATH_SYMBOL;
            case "Uppercase" -> Character::isUpperCase;
            case "White_Space" -> this::isRegExpWhiteSpace;
            default -> null;
        };
    }

    private IntPredicate getGeneralCategoryPredicate(String categoryName) {
        return switch (categoryName) {
            case "C", "Other" -> codePoint -> {
                int type = Character.getType(codePoint);
                return type == Character.UNASSIGNED ||
                        type == Character.CONTROL ||
                        type == Character.FORMAT ||
                        type == Character.PRIVATE_USE ||
                        type == Character.SURROGATE;
            };
            case "Cc", "Control" -> codePoint -> Character.getType(codePoint) == Character.CONTROL;
            case "Cf", "Format" -> codePoint -> Character.getType(codePoint) == Character.FORMAT;
            case "Cn", "Unassigned" -> codePoint -> Character.getType(codePoint) == Character.UNASSIGNED;
            case "Co", "Private_Use" -> codePoint -> Character.getType(codePoint) == Character.PRIVATE_USE;
            case "Cs", "Surrogate" -> codePoint -> Character.getType(codePoint) == Character.SURROGATE;
            case "L", "Letter" -> codePoint -> {
                int type = Character.getType(codePoint);
                return type == Character.UPPERCASE_LETTER ||
                        type == Character.LOWERCASE_LETTER ||
                        type == Character.TITLECASE_LETTER ||
                        type == Character.MODIFIER_LETTER ||
                        type == Character.OTHER_LETTER;
            };
            case "LC", "Cased_Letter" -> codePoint -> {
                int type = Character.getType(codePoint);
                return type == Character.UPPERCASE_LETTER ||
                        type == Character.LOWERCASE_LETTER ||
                        type == Character.TITLECASE_LETTER;
            };
            case "Ll", "Lowercase_Letter" -> codePoint -> Character.getType(codePoint) == Character.LOWERCASE_LETTER;
            case "Lm", "Modifier_Letter" -> codePoint -> Character.getType(codePoint) == Character.MODIFIER_LETTER;
            case "Lo", "Other_Letter" -> codePoint -> Character.getType(codePoint) == Character.OTHER_LETTER;
            case "Lt", "Titlecase_Letter" -> codePoint -> Character.getType(codePoint) == Character.TITLECASE_LETTER;
            case "Lu", "Uppercase_Letter" -> codePoint -> Character.getType(codePoint) == Character.UPPERCASE_LETTER;
            case "M", "Mark" -> codePoint -> {
                int type = Character.getType(codePoint);
                return type == Character.NON_SPACING_MARK ||
                        type == Character.ENCLOSING_MARK ||
                        type == Character.COMBINING_SPACING_MARK;
            };
            case "Mc", "Combining_Spacing_Mark", "Spacing_Mark" ->
                    codePoint -> Character.getType(codePoint) == Character.COMBINING_SPACING_MARK;
            case "Me", "Enclosing_Mark" -> codePoint -> Character.getType(codePoint) == Character.ENCLOSING_MARK;
            case "Mn", "Nonspacing_Mark" -> codePoint -> Character.getType(codePoint) == Character.NON_SPACING_MARK;
            case "N", "Number" -> codePoint -> {
                int type = Character.getType(codePoint);
                return type == Character.DECIMAL_DIGIT_NUMBER ||
                        type == Character.LETTER_NUMBER ||
                        type == Character.OTHER_NUMBER;
            };
            case "Nd", "Decimal_Number" -> codePoint -> Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER;
            case "Nl", "Letter_Number" -> codePoint -> Character.getType(codePoint) == Character.LETTER_NUMBER;
            case "No", "Other_Number" -> codePoint -> Character.getType(codePoint) == Character.OTHER_NUMBER;
            case "P", "Punctuation" -> codePoint -> {
                int type = Character.getType(codePoint);
                return type == Character.CONNECTOR_PUNCTUATION ||
                        type == Character.DASH_PUNCTUATION ||
                        type == Character.START_PUNCTUATION ||
                        type == Character.END_PUNCTUATION ||
                        type == Character.INITIAL_QUOTE_PUNCTUATION ||
                        type == Character.FINAL_QUOTE_PUNCTUATION ||
                        type == Character.OTHER_PUNCTUATION;
            };
            case "Pc", "Connector_Punctuation" ->
                    codePoint -> Character.getType(codePoint) == Character.CONNECTOR_PUNCTUATION;
            case "Pd", "Dash_Punctuation" -> codePoint -> Character.getType(codePoint) == Character.DASH_PUNCTUATION;
            case "Pe", "Close_Punctuation", "End_Punctuation" ->
                    codePoint -> Character.getType(codePoint) == Character.END_PUNCTUATION;
            case "Pf", "Final_Punctuation", "Final_Quote_Punctuation" ->
                    codePoint -> Character.getType(codePoint) == Character.FINAL_QUOTE_PUNCTUATION;
            case "Pi", "Initial_Punctuation", "Initial_Quote_Punctuation" ->
                    codePoint -> Character.getType(codePoint) == Character.INITIAL_QUOTE_PUNCTUATION;
            case "Po", "Other_Punctuation" -> codePoint -> Character.getType(codePoint) == Character.OTHER_PUNCTUATION;
            case "Ps", "Open_Punctuation", "Start_Punctuation" ->
                    codePoint -> Character.getType(codePoint) == Character.START_PUNCTUATION;
            case "S", "Symbol" -> codePoint -> {
                int type = Character.getType(codePoint);
                return type == Character.MATH_SYMBOL ||
                        type == Character.CURRENCY_SYMBOL ||
                        type == Character.MODIFIER_SYMBOL ||
                        type == Character.OTHER_SYMBOL;
            };
            case "Sc", "Currency_Symbol" -> codePoint -> Character.getType(codePoint) == Character.CURRENCY_SYMBOL;
            case "Sk", "Modifier_Symbol" -> codePoint -> Character.getType(codePoint) == Character.MODIFIER_SYMBOL;
            case "Sm", "Math_Symbol" -> codePoint -> Character.getType(codePoint) == Character.MATH_SYMBOL;
            case "So", "Other_Symbol" -> codePoint -> Character.getType(codePoint) == Character.OTHER_SYMBOL;
            case "Z", "Separator" -> codePoint -> {
                int type = Character.getType(codePoint);
                return type == Character.SPACE_SEPARATOR ||
                        type == Character.LINE_SEPARATOR ||
                        type == Character.PARAGRAPH_SEPARATOR;
            };
            case "Zl", "Line_Separator" -> codePoint -> Character.getType(codePoint) == Character.LINE_SEPARATOR;
            case "Zp", "Paragraph_Separator" ->
                    codePoint -> Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR;
            case "Zs", "Space_Separator" -> codePoint -> Character.getType(codePoint) == Character.SPACE_SEPARATOR;
            default -> null;
        };
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

    private int[] invertRanges(int[] ranges) {
        if (ranges.length == 0) {
            return new int[]{0, MAX_UNICODE_CODE_POINT};
        }
        List<Integer> inverted = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < ranges.length; i += 2) {
            int rangeStart = ranges[i];
            int rangeEnd = ranges[i + 1];
            if (start < rangeStart) {
                inverted.add(start);
                inverted.add(rangeStart - 1);
            }
            if (rangeEnd == MAX_UNICODE_CODE_POINT) {
                start = MAX_UNICODE_CODE_POINT + 1;
                break;
            }
            start = rangeEnd + 1;
        }
        if (start <= MAX_UNICODE_CODE_POINT) {
            inverted.add(start);
            inverted.add(MAX_UNICODE_CODE_POINT);
        }
        return toIntArray(inverted);
    }

    private boolean isCanonicalScriptName(String scriptName) {
        if (scriptName == null || scriptName.isEmpty()) {
            return false;
        }
        boolean expectingUppercase = true;
        for (int i = 0; i < scriptName.length(); i++) {
            char ch = scriptName.charAt(i);
            if (ch == '_') {
                if (expectingUppercase) {
                    return false;
                }
                expectingUppercase = true;
                continue;
            }
            if (expectingUppercase) {
                if (!(ch >= 'A' && ch <= 'Z')) {
                    return false;
                }
                expectingUppercase = false;
            } else if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'))) {
                return false;
            }
        }
        return !expectingUppercase;
    }

    private boolean isJsIdentifierPart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || codePoint == 0x200C || codePoint == 0x200D ||
                Character.isUnicodeIdentifierPart(codePoint);
    }

    private boolean isJsIdentifierStart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint);
    }

    private boolean isRegExpWhiteSpace(int codePoint) {
        return codePoint == ' ' || codePoint == '\t' || codePoint == '\n' || codePoint == '\r' || codePoint == '\f' ||
                codePoint == 0x0B || codePoint == 0x00A0 || codePoint == 0xFEFF || codePoint == 0x2028 || codePoint == 0x2029 ||
                Character.getType(codePoint) == Character.SPACE_SEPARATOR;
    }

    private boolean isUnicodePropertyChar(int ch) {
        return (ch >= '0' && ch <= '9') ||
                (ch >= 'A' && ch <= 'Z') ||
                (ch >= 'a' && ch <= 'z') ||
                ch == '_';
    }

    /**
     * Determine whether a quantified atom needs a zero-advance check.
     * Returns true if the atom might match without advancing the position (e.g., lookahead,
     * anchors, word boundaries). Following QuickJS re_need_check_adv_and_capture_init.
     */
    private boolean needCheckAdvance(byte[] atomCode) {
        int pos = 0;
        boolean needCheck = true;
        while (pos < atomCode.length) {
            int opcode = atomCode[pos] & 0xFF;
            RegExpOpcode op = RegExpOpcode.fromCode(opcode);
            switch (op) {
                case CHAR, CHAR_I, CHAR32, CHAR32_I, DOT, ANY, SPACE, NOT_SPACE:
                    // These always advance the position
                    needCheck = false;
                    break;
                case RANGE, RANGE_I: {
                    // Variable length - read the range data length
                    int rangeLen = ((atomCode[pos + 1] & 0xFF) | ((atomCode[pos + 2] & 0xFF) << 8));
                    pos += 3 + rangeLen;
                    needCheck = false;
                    continue;
                }
                case RANGE32, RANGE32_I: {
                    int rangeLen = ((atomCode[pos + 1] & 0xFF) | ((atomCode[pos + 2] & 0xFF) << 8));
                    pos += 3 + rangeLen;
                    needCheck = false;
                    continue;
                }
                case NOT_RANGE, NOT_RANGE_I: {
                    int rangeLen = ((atomCode[pos + 1] & 0xFF) | ((atomCode[pos + 2] & 0xFF) << 8));
                    pos += 3 + rangeLen;
                    needCheck = false;
                    continue;
                }
                case LINE_START, LINE_START_M, LINE_END, LINE_END_M,
                     WORD_BOUNDARY, WORD_BOUNDARY_I, NOT_WORD_BOUNDARY, NOT_WORD_BOUNDARY_I,
                     SAVE_START, SAVE_END, SAVE_RESET, SET_CHAR_POS, SET_I32:
                    // These don't advance - no effect on the check
                    break;
                default:
                    // Unknown or complex opcode - assume might not advance
                    return true;
            }
            pos += op.getLength();
        }
        return needCheck;
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
                // \D - non-digits
                ranges.add(0);
                ranges.add((int) '0' - 1);
                ranges.add((int) '9' + 1);
                ranges.add(MAX_UNICODE_CODE_POINT);
                yield -1;
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
                // \W - non-word characters (not [a-zA-Z0-9_])
                ranges.add(0);
                ranges.add((int) '0' - 1);   // 0 .. '/'
                ranges.add((int) '9' + 1);
                ranges.add((int) 'A' - 1);   // ':' .. '@'
                ranges.add((int) 'Z' + 1);
                ranges.add((int) '_' - 1);   // '[' .. '^'
                ranges.add((int) '_' + 1);
                ranges.add((int) 'a' - 1);   // '`'
                ranges.add((int) 'z' + 1);
                ranges.add(MAX_UNICODE_CODE_POINT);
                yield -1;
            }
            case 's' -> {
                // \s - whitespace characters
                appendRanges(ranges, resolveBinaryPropertyRanges("White_Space"));
                yield -1;
            }
            case 'S' -> {
                // \S - non-whitespace characters
                appendRanges(ranges, invertRanges(resolveBinaryPropertyRanges("White_Space")));
                yield -1;
            }
            case 'p', 'P' -> {
                if (!context.isUnicodeMode()) {
                    yield ch;
                }
                int[] propertyRanges = parseUnicodePropertyEscape(context);
                if (ch == 'P') {
                    propertyRanges = invertRanges(propertyRanges);
                }
                appendRanges(ranges, propertyRanges);
                yield -1;
            }
            case 'c' -> {
                // \c ControlLetter in character class
                if (context.pos < context.codePoints.length) {
                    int next = context.codePoints[context.pos];
                    if ((next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z')) {
                        context.pos++;
                        yield next % 32;
                    }
                }
                // AnnexB: Invalid \c in char class → class contains '\' and 'c'
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Invalid control escape");
                }
                ranges.add((int) '\\');
                ranges.add((int) '\\');
                yield 'c';
            }
            case 'x' -> {
                // \xHH in character class
                boolean validHex = context.pos + 1 < context.codePoints.length
                        && hexValue(context.codePoints[context.pos]) != -1
                        && hexValue(context.codePoints[context.pos + 1]) != -1;
                if (validHex) {
                    int hex1 = hexValue(context.codePoints[context.pos]);
                    int hex2 = hexValue(context.codePoints[context.pos + 1]);
                    context.pos += 2;
                    yield (hex1 << 4) | hex2;
                } else if (!context.isUnicodeMode()) {
                    yield 'x';
                } else {
                    throw new RegExpSyntaxException("Invalid hex escape");
                }
            }
            case 'u' -> {
                // Unicode escape (backslash-u HHHH or braced) in character class
                if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '{' && context.isUnicodeMode()) {
                    context.pos++;
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
                    }
                    if (digitCount == 0) {
                        throw new RegExpSyntaxException("Empty unicode escape");
                    }
                    yield value;
                }
                boolean validUnicode = context.pos + 3 < context.codePoints.length
                        && hexValue(context.codePoints[context.pos]) != -1
                        && hexValue(context.codePoints[context.pos + 1]) != -1
                        && hexValue(context.codePoints[context.pos + 2]) != -1
                        && hexValue(context.codePoints[context.pos + 3]) != -1;
                if (validUnicode) {
                    int value = 0;
                    for (int i = 0; i < 4; i++) {
                        value = (value << 4) | hexValue(context.codePoints[context.pos + i]);
                    }
                    context.pos += 4;
                    yield value;
                } else if (!context.isUnicodeMode()) {
                    yield 'u';
                } else {
                    throw new RegExpSyntaxException("Invalid unicode escape");
                }
            }
            case '0', '1', '2', '3', '4', '5', '6', '7' -> {
                if (context.isUnicodeMode()) {
                    yield ch;
                }
                yield parseLegacyOctalEscape(context, ch - '0');
            }
            default -> ch; // Literal escaped character
        };
    }

    private GroupNameParseResult parseGroupName(int[] codePoints, int startPos) {
        StringBuilder name = new StringBuilder();
        int pos = startPos;

        while (pos < codePoints.length) {
            int codePoint = codePoints[pos];
            if (codePoint == '>') {
                if (name.isEmpty()) {
                    return null;
                }
                return new GroupNameParseResult(name.toString(), pos + 1);
            }

            if (codePoint == '\\') {
                EscapedCodePoint escapedCodePoint = parseUnicodeEscapeInGroupName(codePoints, pos);
                if (escapedCodePoint == null) {
                    return null;
                }
                codePoint = escapedCodePoint.codePoint();
                pos = escapedCodePoint.nextPos();
            } else {
                pos++;
            }

            if (name.isEmpty()) {
                if (!isJsIdentifierStart(codePoint)) {
                    return null;
                }
            } else if (!isJsIdentifierPart(codePoint)) {
                return null;
            }
            name.appendCodePoint(codePoint);
        }

        return null;
    }

    private int parseLegacyOctalEscape(CompileContext context, int firstDigit) {
        int value = firstDigit;
        if (context.pos < context.codePoints.length) {
            int next = context.codePoints[context.pos];
            if (next >= '0' && next <= '7') {
                value = (value << 3) | (next - '0');
                context.pos++;
                if (value < 32 && context.pos < context.codePoints.length) {
                    int third = context.codePoints[context.pos];
                    if (third >= '0' && third <= '7') {
                        value = (value << 3) | (third - '0');
                        context.pos++;
                    }
                }
            }
        }
        return value;
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

        if (max < min) {
            throw new RegExpSyntaxException("Invalid quantifier range {" + min + "," + max + "}");
        }

        return new int[]{min, max};
    }

    private EscapedCodePoint parseUnicodeEscapeInGroupName(int[] codePoints, int startPos) {
        if (startPos + 1 >= codePoints.length || codePoints[startPos] != '\\' || codePoints[startPos + 1] != 'u') {
            return null;
        }
        int pos = startPos + 2;
        if (pos < codePoints.length && codePoints[pos] == '{') {
            pos++;
            int value = 0;
            int digits = 0;
            while (pos < codePoints.length && codePoints[pos] != '}') {
                int hex = hexValue(codePoints[pos]);
                if (hex < 0) {
                    return null;
                }
                value = (value << 4) | hex;
                digits++;
                if (digits > 6) {
                    return null;
                }
                pos++;
            }
            if (digits == 0 || pos >= codePoints.length || codePoints[pos] != '}' || value > 0x10FFFF) {
                return null;
            }
            return new EscapedCodePoint(value, pos + 1);
        }

        if (pos + 3 >= codePoints.length) {
            return null;
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int hex = hexValue(codePoints[pos + i]);
            if (hex < 0) {
                return null;
            }
            value = (value << 4) | hex;
        }
        return new EscapedCodePoint(value, pos + 4);
    }

    private int[] parseUnicodePropertyEscape(CompileContext context) {
        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '{') {
            throw new RegExpSyntaxException("expecting '{' after \\p");
        }
        context.pos++; // Skip '{'

        int propertyNameStart = context.pos;
        while (context.pos < context.codePoints.length && isUnicodePropertyChar(context.codePoints[context.pos])) {
            context.pos++;
        }
        String propertyName = new String(context.codePoints, propertyNameStart, context.pos - propertyNameStart);
        String propertyValue = null;

        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '=') {
            context.pos++; // Skip '='
            int propertyValueStart = context.pos;
            while (context.pos < context.codePoints.length && isUnicodePropertyChar(context.codePoints[context.pos])) {
                context.pos++;
            }
            propertyValue = new String(context.codePoints, propertyValueStart, context.pos - propertyValueStart);
        }

        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '}') {
            throw new RegExpSyntaxException("expecting '}'");
        }
        context.pos++; // Skip '}'

        return resolveUnicodePropertyRanges(propertyName, propertyValue);
    }

    private int[] resolveBinaryPropertyRanges(String propertyName) {
        int[] cachedRanges = BINARY_PROPERTY_RANGES_CACHE.get(propertyName);
        if (cachedRanges != null) {
            return cachedRanges;
        }
        IntPredicate predicate = getBinaryPropertyPredicate(propertyName);
        if (predicate == null) {
            return null;
        }
        int[] ranges = buildRanges(predicate);
        BINARY_PROPERTY_RANGES_CACHE.put(propertyName, ranges);
        return ranges;
    }

    private int[] resolveGeneralCategoryRanges(String categoryName) {
        int[] cachedRanges = GENERAL_CATEGORY_RANGES_CACHE.get(categoryName);
        if (cachedRanges != null) {
            return cachedRanges;
        }
        IntPredicate predicate = getGeneralCategoryPredicate(categoryName);
        if (predicate == null) {
            return null;
        }
        int[] ranges = buildRanges(predicate);
        GENERAL_CATEGORY_RANGES_CACHE.put(categoryName, ranges);
        return ranges;
    }

    private int[] resolveScriptRanges(String scriptName) {
        int[] cachedRanges = SCRIPT_RANGES_CACHE.get(scriptName);
        if (cachedRanges != null) {
            return cachedRanges;
        }
        if (!isCanonicalScriptName(scriptName)) {
            return null;
        }

        Character.UnicodeScript script;
        try {
            script = Character.UnicodeScript.forName(scriptName);
        } catch (IllegalArgumentException e) {
            return null;
        }

        int[] ranges = buildRanges(codePoint -> Character.UnicodeScript.of(codePoint) == script);
        SCRIPT_RANGES_CACHE.put(scriptName, ranges);
        return ranges;
    }

    private int[] resolveUnicodePropertyRanges(String propertyName, String propertyValue) {
        if (propertyValue != null) {
            if ("General_Category".equals(propertyName) || "gc".equals(propertyName)) {
                int[] ranges = resolveGeneralCategoryRanges(propertyValue);
                if (ranges == null) {
                    throw new RegExpSyntaxException("unknown unicode general category");
                }
                return ranges;
            }
            if ("Script".equals(propertyName) || "sc".equals(propertyName) ||
                    "Script_Extensions".equals(propertyName) || "scx".equals(propertyName)) {
                int[] ranges = resolveScriptRanges(propertyValue);
                if (ranges == null) {
                    throw new RegExpSyntaxException("unknown unicode script");
                }
                return ranges;
            }
            throw new RegExpSyntaxException("unknown unicode property name");
        }

        int[] ranges = resolveGeneralCategoryRanges(propertyName);
        if (ranges != null) {
            return ranges;
        }
        ranges = resolveBinaryPropertyRanges(propertyName);
        if (ranges != null) {
            return ranges;
        }
        throw new RegExpSyntaxException("unknown unicode property name");
    }

    private Map<String, Integer> scanNamedCaptureGroups(String pattern) {
        int[] codePoints = pattern.codePoints().toArray();
        Map<String, Integer> captureIndices = new HashMap<>();
        boolean inCharacterClass = false;
        int captureIndex = 1;

        // Track disjunction nesting for ES2025 duplicate named groups.
        // Duplicate names are allowed in different alternatives of the same disjunction.
        // currentAltStack: names seen in the current alternative at each nesting level
        // allAltStack: names from all completed alternatives at each nesting level
        Deque<Set<String>> currentAltStack = new ArrayDeque<>();
        Deque<Set<String>> allAltStack = new ArrayDeque<>();
        currentAltStack.push(new HashSet<>());
        allAltStack.push(new HashSet<>());

        for (int i = 0; i < codePoints.length; i++) {
            int codePoint = codePoints[i];

            if (codePoint == '\\') {
                if (i + 1 < codePoints.length) {
                    i++;
                }
                continue;
            }
            if (inCharacterClass) {
                if (codePoint == ']') {
                    inCharacterClass = false;
                }
                continue;
            }
            if (codePoint == '[') {
                inCharacterClass = true;
                continue;
            }
            if (codePoint == '|') {
                allAltStack.peek().addAll(currentAltStack.peek());
                currentAltStack.peek().clear();
                continue;
            }
            if (codePoint == ')') {
                if (currentAltStack.size() > 1) {
                    Set<String> poppedCurrent = currentAltStack.pop();
                    Set<String> poppedAll = allAltStack.pop();
                    poppedAll.addAll(poppedCurrent);
                    currentAltStack.peek().addAll(poppedAll);
                }
                continue;
            }
            if (codePoint != '(') {
                continue;
            }

            if (i + 1 < codePoints.length && codePoints[i + 1] == '?') {
                if (i + 2 >= codePoints.length) {
                    currentAltStack.push(new HashSet<>());
                    allAltStack.push(new HashSet<>());
                    continue;
                }
                int groupType = codePoints[i + 2];
                if (groupType == ':'
                        || groupType == '='
                        || groupType == '!'
                        || groupType == '>') {
                    currentAltStack.push(new HashSet<>());
                    allAltStack.push(new HashSet<>());
                    continue;
                }
                if (groupType == '<') {
                    if (i + 3 < codePoints.length &&
                            (codePoints[i + 3] == '=' || codePoints[i + 3] == '!')) {
                        currentAltStack.push(new HashSet<>());
                        allAltStack.push(new HashSet<>());
                        continue;
                    }
                    GroupNameParseResult groupNameParseResult = parseGroupName(codePoints, i + 3);
                    if (groupNameParseResult == null) {
                        throw new RegExpSyntaxException("invalid group name");
                    }
                    String name = groupNameParseResult.name();
                    if (currentAltStack.peek().contains(name)) {
                        throw new RegExpSyntaxException("duplicate group name");
                    }
                    currentAltStack.peek().add(name);
                    if (!captureIndices.containsKey(name)) {
                        captureIndices.put(name, captureIndex);
                    }
                    captureIndex++;
                    i = groupNameParseResult.nextPos() - 1;
                    currentAltStack.push(new HashSet<>());
                    allAltStack.push(new HashSet<>());
                    continue;
                }
                currentAltStack.push(new HashSet<>());
                allAltStack.push(new HashSet<>());
                continue;
            }

            captureIndex++;
            currentAltStack.push(new HashSet<>());
            allAltStack.push(new HashSet<>());
        }
        totalCaptureCount = captureIndex;
        return captureIndices;
    }

    private boolean startsWithValidQuantifier(CompileContext context) {
        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '{') {
            return false;
        }
        int pos = context.pos + 1;
        if (pos >= context.codePoints.length || context.codePoints[pos] < '0' || context.codePoints[pos] > '9') {
            return false;
        }
        while (pos < context.codePoints.length && context.codePoints[pos] >= '0' && context.codePoints[pos] <= '9') {
            pos++;
        }
        if (pos < context.codePoints.length && context.codePoints[pos] == ',') {
            pos++;
            while (pos < context.codePoints.length && context.codePoints[pos] >= '0' && context.codePoints[pos] <= '9') {
                pos++;
            }
        }
        return pos < context.codePoints.length && context.codePoints[pos] == '}';
    }

    private int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static class CompileContext {
        final DynamicBuffer buffer;
        final int[] codePoints;
        final int flags;
        final String pattern;
        boolean lastAtomCanRepeat;
        int pos;

        CompileContext(String pattern, int flags, DynamicBuffer buffer) {
            this.pattern = pattern;
            this.codePoints = pattern.codePoints().toArray();
            this.flags = flags;
            this.buffer = buffer;
            this.lastAtomCanRepeat = false;
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

        boolean isUnicodeMode() {
            return (flags & (RegExpBytecode.FLAG_UNICODE | RegExpBytecode.FLAG_UNICODE_SETS)) != 0;
        }
    }

    private record EscapedCodePoint(int codePoint, int nextPos) {
    }

    private record GroupNameParseResult(String name, int nextPos) {
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
