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

import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;
import com.caoccao.qjs4j.utils.DynamicBuffer;

import java.util.*;

/**
 * Compiles regex patterns to bytecode.
 * Based on QuickJS libregexp.c compiler.
 * <p>
 * This is a simplified initial implementation that handles basic patterns.
 * Full ES2020 regex syntax support will be added incrementally.
 */
public final class RegExpCompiler {

    private static final long MAX_QUANTIFIER_BOUND = Integer.MAX_VALUE;
    private static final int MAX_UNICODE_CODE_POINT = 0x10FFFF;
    private static final int MAX_UNROLLED_QUANTIFIER_REPETITIONS = 4096;
    private int captureCount;
    private List<String> groupNames;
    private Map<String, Integer> namedCaptureIndices;
    private int totalCaptureCount;

    private long appendDecimalDigitWithClamp(long currentValue, int digit) {
        if (currentValue >= MAX_QUANTIFIER_BOUND) {
            return MAX_QUANTIFIER_BOUND;
        }
        long nextValue = currentValue * 10L + digit;
        return Math.min(nextValue, MAX_QUANTIFIER_BOUND);
    }

    private void appendRanges(List<Integer> ranges, int[] propertyRanges) {
        for (int propertyRange : propertyRanges) {
            ranges.add(propertyRange);
        }
    }

    private int combineTrailingLowSurrogateEscapeIfPresent(CompileContext context, int codePoint) {
        if (!context.isUnicodeMode()) {
            return codePoint;
        }
        if (!Character.isHighSurrogate((char) codePoint)) {
            return codePoint;
        }
        UnicodeEscapeParseResult unicodeEscapeParseResult = tryParseUnicodeEscapeAt(context, context.pos);
        if (unicodeEscapeParseResult == null) {
            return codePoint;
        }
        int trailingCodePoint = unicodeEscapeParseResult.codePoint();
        if (!Character.isLowSurrogate((char) trailingCodePoint)) {
            return codePoint;
        }
        context.pos = unicodeEscapeParseResult.nextPos();
        return Character.toCodePoint((char) codePoint, (char) trailingCodePoint);
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
            if (context.pos != context.codePoints.length) {
                throw new RegExpSyntaxException("Unexpected character in pattern");
            }
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

    private void compileAlternative(CompileContext context, boolean isBackwardDirection) {
        // An alternative is a sequence of terms (atoms with optional quantifiers)
        int alternativeStart = context.buffer.size();
        while (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];

            // Check for end of alternative
            if (ch == '|' || ch == ')') {
                return;
            }

            // Parse a term (atom + optional quantifier)
            int termStart = context.buffer.size();
            compileTerm(context, isBackwardDirection);
            if (isBackwardDirection) {
                reverseAlternativeTerm(context, alternativeStart, termStart);
            }
        }
    }

    private void compileAtom(CompileContext context, boolean isBackwardDirection) {
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
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.buffer.appendU8(context.isDotAll() ?
                        RegExpOpcode.ANY.getCode() :
                        RegExpOpcode.DOT.getCode());
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.pos++;
            }

            case '\\' -> {
                context.pos++;
                compileEscape(context, isBackwardDirection);
            }

            case '[' -> {
                compileCharacterClass(context, isBackwardDirection);
            }

            case '(' -> {
                compileGroup(context, isBackwardDirection);
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
                compileLiteralChar(context, ch, isBackwardDirection);
                context.pos++;
            }

            case '}' -> {
                // In unicode mode, lone '}' outside a quantifier is a syntax error
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Lone quantifier bracket at position " + context.pos);
                }
                compileLiteralChar(context, ch, isBackwardDirection);
                context.pos++;
            }

            case ']' -> {
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Unexpected ']'");
                }
                compileLiteralChar(context, ch, isBackwardDirection);
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
                compileLiteralChar(context, ch, isBackwardDirection);
                context.pos++;
            }
        }
    }

    private void compileCharacterClass(CompileContext context, boolean isBackwardDirection) {
        // Character classes [abc], [^abc], [a-z]
        if (context.isUnicodeSetsMode()) {
            compileUnicodeSetsCharacterClass(context, isBackwardDirection);
            return;
        }
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
        context.pos++; // Skip '['

        boolean inverted = false;
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '^') {
            inverted = true;
            context.pos++;
        }

        List<Integer> ranges = new ArrayList<>();
        boolean foundClosingBracket = false;

        while (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];

            if (ch == ']') {
                context.pos++;
                foundClosingBracket = true;
                break;
            }

            if (ch == '\\') {
                context.pos++;
                if (context.pos >= context.codePoints.length) {
                    throw new RegExpSyntaxException("Incomplete escape in character class");
                }
                ch = parseClassEscape(context, ranges);
                if (ch == -1) {
                    // It was a character class escape like \d, \w, \s, \p{...}
                    // The ranges were added by parseClassEscape
                    // In Unicode mode, character class escapes can't be used as range endpoints
                    if (context.isUnicodeMode() &&
                            context.pos < context.codePoints.length &&
                            context.codePoints[context.pos] == '-' &&
                            context.pos + 1 < context.codePoints.length &&
                            context.codePoints[context.pos + 1] != ']') {
                        throw new RegExpSyntaxException("Invalid range in character class");
                    }
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

        if (!foundClosingBracket) {
            throw new RegExpSyntaxException("Unclosed character class");
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
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
    }

    private void compileDisjunction(CompileContext context, boolean isBackwardDirection) {
        // A disjunction is a sequence of alternatives separated by |
        int start = context.buffer.size();

        // Compile first alternative
        compileAlternative(context, isBackwardDirection);

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
            compileAlternative(context, isBackwardDirection);

            // Patch the GOTO offset
            int gotoOffset = context.buffer.size() - (gotoPos + 5);
            context.buffer.setU32(gotoPos + 1, gotoOffset);
        }
    }

    private void compileEscape(CompileContext context, boolean isBackwardDirection) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Incomplete escape sequence");
        }

        int ch = context.codePoints[context.pos++];
        switch (ch) {
            case 'n' -> compileLiteralChar(context, '\n', isBackwardDirection);
            case 'r' -> compileLiteralChar(context, '\r', isBackwardDirection);
            case 't' -> compileLiteralChar(context, '\t', isBackwardDirection);
            case 'f' -> compileLiteralChar(context, '\f', isBackwardDirection);
            case 'v' -> compileLiteralChar(context, '\u000B', isBackwardDirection);
            case 'd' -> {
                // \d - match digits [0-9]
                // Emit a simple character range check for now
                // This is a simplified implementation - proper implementation would use RANGE opcode
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.buffer.appendU8(RegExpOpcode.RANGE.getCode());
                context.buffer.appendU16(10); // length of range data = 1 range * 2 codepoints * 4 bytes + 2 bytes count
                context.buffer.appendU16(1);  // 1 range
                context.buffer.appendU32('0');
                context.buffer.appendU32('9');
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
            }
            case 'D' -> {
                // \D - match non-digits (not [0-9])
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.buffer.appendU8(RegExpOpcode.NOT_RANGE.getCode());
                context.buffer.appendU16(10); // length of range data = 1 range * 2 codepoints * 4 bytes + 2 bytes count
                context.buffer.appendU16(1);  // 1 range
                context.buffer.appendU32('0');
                context.buffer.appendU32('9');
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
            }
            case 'w' -> {
                // \w - match word characters [a-zA-Z0-9_]
                // Emit RANGE opcode with ranges for word characters
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.buffer.appendU8(RegExpOpcode.RANGE.getCode());
                boolean includeUnicodeIgnoreCaseWordExtras = context.isUnicodeMode() && context.isIgnoreCase();
                int rangeCount = includeUnicodeIgnoreCaseWordExtras ? 6 : 4;
                context.buffer.appendU16(2 + (rangeCount * 8));
                context.buffer.appendU16(rangeCount);
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
                if (includeUnicodeIgnoreCaseWordExtras) {
                    // ES Unicode ignoreCase adds canonicalized matches for \w:
                    // U+017F LATIN SMALL LETTER LONG S and U+212A KELVIN SIGN.
                    context.buffer.appendU32(0x017F);
                    context.buffer.appendU32(0x017F);
                    context.buffer.appendU32(0x212A);
                    context.buffer.appendU32(0x212A);
                }
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
            }
            case 'W' -> {
                // \W - match non-word characters (not [a-zA-Z0-9_])
                // Emit NOT_RANGE opcode with same ranges as \w
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.buffer.appendU8(RegExpOpcode.NOT_RANGE.getCode());
                boolean includeUnicodeIgnoreCaseWordExtras = context.isUnicodeMode() && context.isIgnoreCase();
                if (includeUnicodeIgnoreCaseWordExtras) {
                    context.buffer.appendU16(2 + (6 * 8));
                    context.buffer.appendU16(6);
                    context.buffer.appendU32('0');
                    context.buffer.appendU32('9');
                    context.buffer.appendU32('A');
                    context.buffer.appendU32('Z');
                    context.buffer.appendU32('_');
                    context.buffer.appendU32('_');
                    context.buffer.appendU32('a');
                    context.buffer.appendU32('z');
                    context.buffer.appendU32(0x017F);
                    context.buffer.appendU32(0x017F);
                    context.buffer.appendU32(0x212A);
                    context.buffer.appendU32(0x212A);
                } else {
                    context.buffer.appendU16(2 + (4 * 8));
                    context.buffer.appendU16(4);
                    context.buffer.appendU32('0');
                    context.buffer.appendU32('9');
                    context.buffer.appendU32('A');
                    context.buffer.appendU32('Z');
                    context.buffer.appendU32('_');
                    context.buffer.appendU32('_');
                    context.buffer.appendU32('a');
                    context.buffer.appendU32('z');
                }
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
            }
            case 's' -> {
                // \s - match whitespace
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.buffer.appendU8(RegExpOpcode.SPACE.getCode());
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
            }
            case 'S' -> {
                // \S - match non-whitespace
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.buffer.appendU8(RegExpOpcode.NOT_SPACE.getCode());
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
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
                    compileLiteralChar(context, 'k', isBackwardDirection);
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

                context.buffer.appendU8(context.isIgnoreCase()
                        ? (isBackwardDirection ? RegExpOpcode.BACKWARD_BACK_REFERENCE_I.getCode() : RegExpOpcode.BACK_REFERENCE_I.getCode())
                        : (isBackwardDirection ? RegExpOpcode.BACKWARD_BACK_REFERENCE.getCode() : RegExpOpcode.BACK_REFERENCE.getCode()));
                context.buffer.appendU8(groupNum);
            }
            case 'p', 'P' -> {
                if (!context.isUnicodeMode()) {
                    compileLiteralChar(context, ch, isBackwardDirection);
                    break;
                }
                compileUnicodePropertyEscape(context, ch == 'P', isBackwardDirection);
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
                    compileLiteralChar(context, 0, isBackwardDirection);
                } else {
                    // Non-Unicode mode: legacy octal escape (\0, \00, \000, etc.)
                    int value = parseLegacyOctalEscape(context, 0);
                    compileLiteralChar(context, value, isBackwardDirection);
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
                    context.buffer.appendU8(context.isIgnoreCase()
                            ? (isBackwardDirection ? RegExpOpcode.BACKWARD_BACK_REFERENCE_I.getCode() : RegExpOpcode.BACK_REFERENCE_I.getCode())
                            : (isBackwardDirection ? RegExpOpcode.BACKWARD_BACK_REFERENCE.getCode() : RegExpOpcode.BACK_REFERENCE.getCode()));
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
                        compileLiteralChar(context, octalValue, isBackwardDirection);
                    } else {
                        // \8, \9 → identity escape (literal digit)
                        compileLiteralChar(context, ch, isBackwardDirection);
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
                        compileLiteralChar(context, next % 32, isBackwardDirection);
                        break;
                    }
                }
                // AnnexB: In non-unicode mode, \c without valid control letter
                // returns literal '\' and backs up to re-parse 'c' as next term
                // (matching QuickJS get_class_atom behavior)
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Invalid control escape");
                }
                context.pos--; // back up to 'c' so it's re-parsed as next atom
                compileLiteralChar(context, '\\', isBackwardDirection);
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
                    compileLiteralChar(context, value, isBackwardDirection);
                } else if (!context.isUnicodeMode()) {
                    // AnnexB: In non-unicode mode, \x without valid hex is identity escape
                    compileLiteralChar(context, 'x', isBackwardDirection);
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
                        boolean closedBrace = false;
                        while (context.pos < context.codePoints.length) {
                            int hexCh = context.codePoints[context.pos];
                            if (hexCh == '}') {
                                context.pos++;
                                closedBrace = true;
                                break;
                            }
                            int hexVal = hexValue(hexCh);
                            if (hexVal == -1) {
                                throw new RegExpSyntaxException("Invalid unicode escape");
                            }
                            value = (value << 4) | hexVal;
                            digitCount++;
                            context.pos++;
                            if (value > 0x10FFFF) {
                                throw new RegExpSyntaxException("Unicode escape out of range");
                            }
                        }
                        if (digitCount == 0) {
                            throw new RegExpSyntaxException("Empty unicode escape");
                        }
                        if (!closedBrace) {
                            throw new RegExpSyntaxException("Invalid unicode escape");
                        }
                        int combinedCodePoint = combineTrailingLowSurrogateEscapeIfPresent(context, value);
                        compileLiteralChar(context, combinedCodePoint, isBackwardDirection);
                    } else {
                        // Non-unicode mode: backslash-u is identity escape, '{' handled by caller
                        compileLiteralChar(context, 'u', isBackwardDirection);
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
                        int combinedCodePoint = combineTrailingLowSurrogateEscapeIfPresent(context, value);
                        compileLiteralChar(context, combinedCodePoint, isBackwardDirection);
                    } else if (!context.isUnicodeMode()) {
                        // AnnexB: In non-unicode mode, backslash-u without valid hex is identity escape
                        compileLiteralChar(context, 'u', isBackwardDirection);
                    } else {
                        throw new RegExpSyntaxException("Invalid unicode escape");
                    }
                }
            }
            default -> {
                // Identity escape
                if (context.isUnicodeMode()) {
                    // In unicode mode, only syntax characters are valid identity escapes
                    // QuickJS: ^$\.*+?()[]{}|/
                    if (!isSyntaxCharacter(ch)) {
                        throw new RegExpSyntaxException("invalid escape sequence in regular expression");
                    }
                }
                // Non-unicode mode: any character is a valid identity escape (Annex B)
                compileLiteralChar(context, ch, isBackwardDirection);
            }
        }
    }

    private void compileGroup(CompileContext context, boolean isBackwardDirection) {
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
            InlineModifierGroupParseResult inlineModifierGroupParseResult = tryParseInlineModifierGroup(context);
            if (inlineModifierGroupParseResult != null) {
                int savedFlags = context.currentFlags;
                context.currentFlags = inlineModifierGroupParseResult.scopedFlags();
                try {
                    compileDisjunction(context, isBackwardDirection);
                } finally {
                    context.currentFlags = savedFlags;
                }
                if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ')') {
                    throw new RegExpSyntaxException("Unclosed group");
                }
                context.pos++;
                context.lastAtomCanRepeat = true;
                return;
            }
            int groupType = context.codePoints[context.pos++];
            if (groupType == ':') {
                // Non-capturing group (?:...)
                isCapturing = false;
            } else if (groupType == '=') {
                // Positive lookahead (?=...)
                compileLookahead(context, false);
                context.lastAtomCanRepeat = !context.isUnicodeMode();
                return;
            } else if (groupType == '!') {
                // Negative lookahead (?!...)
                compileLookahead(context, true);
                context.lastAtomCanRepeat = !context.isUnicodeMode();
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
            context.buffer.appendU8(isBackwardDirection
                    ? RegExpOpcode.SAVE_END.getCode()
                    : RegExpOpcode.SAVE_START.getCode());
            context.buffer.appendU8(groupIndex);
        }

        // Compile group contents
        compileDisjunction(context, isBackwardDirection);

        if (isCapturing) {
            // Save end
            context.buffer.appendU8(isBackwardDirection
                    ? RegExpOpcode.SAVE_START.getCode()
                    : RegExpOpcode.SAVE_END.getCode());
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

    private void compileLiteralChar(CompileContext context, int ch, boolean isBackwardDirection) {
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
        compileLiteralChar(context, ch);
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
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

        // Compile lookahead contents (forward direction)
        compileDisjunction(context, false);

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

        compileDisjunction(context, true);

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

        compileDisjunction(context, false);

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

        // Following QuickJS: reset captures inside the quantifier body at each iteration.
        // This is needed when the atom contains optional captures (via SPLIT/GOTO) that
        // might not be set on every iteration. Without resetting, captures from a previous
        // iteration leak into the current one.
        boolean hasCapturesInAtom = captureCountBeforeAtom != captureCount;
        boolean needCaptureReset = hasCapturesInAtom && needCaptureInit(atomCode);
        boolean needInitialCaptureResetBeforeQuantifier = false;
        if (needCaptureReset) {
            // Prepend SAVE_RESET to the atom code so captures are cleared at each iteration
            byte[] resetPrefix = new byte[3];
            resetPrefix[0] = (byte) RegExpOpcode.SAVE_RESET.getCode();
            resetPrefix[1] = (byte) captureCountBeforeAtom;
            resetPrefix[2] = (byte) (captureCount - 1);
            byte[] newAtomCode = new byte[resetPrefix.length + atomCode.length];
            System.arraycopy(resetPrefix, 0, newAtomCode, 0, resetPrefix.length);
            System.arraycopy(atomCode, 0, newAtomCode, resetPrefix.length, atomCode.length);
            atomCode = newAtomCode;
            atomSize = atomCode.length;
        }
        if (hasCapturesInAtom && min == 0) {
            // When quant_min == 0 and all captures are always initialized in the atom,
            // we still need a one-time reset before the SPLIT for the case where the
            // atom is not executed at all (following QuickJS). This is also needed when
            // per-iteration resets are injected, because the zero-iteration branch skips
            // the atom bytecode entirely.
            needInitialCaptureResetBeforeQuantifier = true;
        }

        if (needInitialCaptureResetBeforeQuantifier) {
            context.buffer.appendU8(RegExpOpcode.SAVE_RESET.getCode());
            context.buffer.appendU8(captureCountBeforeAtom);
            context.buffer.appendU8(captureCount - 1);
        }

        if (min > MAX_UNROLLED_QUANTIFIER_REPETITIONS) {
            min = MAX_UNROLLED_QUANTIFIER_REPETITIONS;
            if (max < min) {
                max = min;
            }
        }
        if (max != Integer.MAX_VALUE && max > MAX_UNROLLED_QUANTIFIER_REPETITIONS) {
            max = MAX_UNROLLED_QUANTIFIER_REPETITIONS;
            if (max < min) {
                max = min;
            }
        }

        // Allocate a unique register for this quantifier's advance check to avoid
        // conflicts with nested quantifiers that also use SET_CHAR_POS/CHECK_ADVANCE.
        int advReg = addZeroAdvanceCheck ? context.nextAdvanceCheckRegister++ : 0;

        // Implement quantifier using SPLIT opcodes
        if (min == 0 && max == 1) {
            // ? quantifier: SPLIT then atom
            // Greedy: try atom first, then skip
            // Non-greedy: try skip first, then atom
            if (addZeroAdvanceCheck) {
                context.buffer.appendU8(RegExpOpcode.SET_CHAR_POS.getCode());
                context.buffer.appendU8(advReg);
            }
            context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() :
                    RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
            context.buffer.appendU32(atomSize + (addZeroAdvanceCheck ? 2 : 0));
            context.buffer.append(atomCode);
            if (addZeroAdvanceCheck) {
                context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                context.buffer.appendU8(advReg);
            }
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
                context.buffer.appendU8(advReg);
            }
            context.buffer.append(atomCode);
            if (addZeroAdvanceCheck) {
                context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                context.buffer.appendU8(advReg);
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
                context.buffer.appendU8(advReg);
                context.buffer.append(atomCode);
                context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                context.buffer.appendU8(advReg);
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
                        context.buffer.appendU8(advReg);
                    }
                    context.buffer.append(atomCode);
                    if (addZeroAdvanceCheck) {
                        context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                        context.buffer.appendU8(advReg);
                    }
                    context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
                    int offset = loopStart - (context.buffer.size() + 4);
                    context.buffer.appendU32(offset);
                } else {
                    int remaining = max - min;
                    for (int i = 0; i < remaining; i++) {
                        if (addZeroAdvanceCheck) {
                            context.buffer.appendU8(RegExpOpcode.SET_CHAR_POS.getCode());
                            context.buffer.appendU8(advReg);
                        }
                        context.buffer.appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() :
                                RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
                        context.buffer.appendU32(atomSize + (addZeroAdvanceCheck ? 2 : 0));
                        context.buffer.append(atomCode);
                        if (addZeroAdvanceCheck) {
                            context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                            context.buffer.appendU8(advReg);
                        }
                    }
                }
            }
        }
    }

    private void compileTerm(CompileContext context, boolean isBackwardDirection) {
        int atomStart = context.buffer.size();
        int captureCountBeforeAtom = captureCount;

        // Parse the atom
        compileAtom(context, isBackwardDirection);

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

    /**
     * Compile a \p{} or \P{} Unicode property escape.
     * Handles both regular properties and sequence properties (v flag).
     * Based on QuickJS parse_unicode_property + re_emit_string_list.
     */
    private void compileUnicodePropertyEscape(CompileContext context, boolean isInverted, boolean isBackwardDirection) {
        // Parse the property name
        int savedPos = context.pos;
        int[] propertyRanges = null;
        try {
            propertyRanges = parseUnicodePropertyEscape(context);
        } catch (RegExpSyntaxException e) {
            // If we're in unicode sets mode and not inverted, try sequence properties
            if (context.isUnicodeSetsMode() && !isInverted) {
                context.pos = savedPos;
                // Re-parse just the property name without resolving
                if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '{') {
                    throw e;
                }
                context.pos++;
                int nameStart = context.pos;
                while (context.pos < context.codePoints.length && isUnicodePropertyChar(context.codePoints[context.pos])) {
                    context.pos++;
                }
                String propertyName = new String(context.codePoints, nameStart, context.pos - nameStart);
                if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '}') {
                    throw e;
                }
                context.pos++;

                UnicodePropertyResolver.SequencePropertyResult seqResult =
                        UnicodePropertyResolver.resolveSequenceProperty(propertyName);
                if (seqResult == null) {
                    throw e;
                }

                emitStringList(context, seqResult);
                return;
            }
            throw e;
        }

        // Regular property resolved successfully
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
        boolean emitInvertedRanges = isInverted;
        if (isInverted && context.isUnicodeMode() && context.isIgnoreCase()) {
            propertyRanges = invertRanges(propertyRanges);
            emitInvertedRanges = false;
        }
        emitRanges(context, propertyRanges, emitInvertedRanges);
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
    }

    private void compileUnicodeSetsCharacterClass(CompileContext context, boolean isBackwardDirection) {
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
        context.pos++; // Skip '['

        boolean inverted = false;
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '^') {
            inverted = true;
            context.pos++;
        }

        ExtendedClassSet extendedClassSet = parseUnicodeSetsExpression(context);
        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ']') {
            throw new RegExpSyntaxException("Unclosed character class");
        }
        context.pos++; // Skip ']'

        if (inverted) {
            if (!extendedClassSet.sequences().isEmpty()) {
                throw new RegExpSyntaxException("Invalid UnicodeSets negated class");
            }
            extendedClassSet = new ExtendedClassSet(invertRanges(extendedClassSet.ranges()), List.of(), null);
        }

        emitExtendedClassSet(context, extendedClassSet);
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
    }

    private void emitExtendedClassSet(CompileContext context, ExtendedClassSet extendedClassSet) {
        if (extendedClassSet.sequences().isEmpty()) {
            emitRanges(context, extendedClassSet.ranges(), false);
            return;
        }
        UnicodePropertyResolver.SequencePropertyResult sequencePropertyResult =
                new UnicodePropertyResolver.SequencePropertyResult(
                        extendedClassSet.ranges(),
                        extendedClassSet.sequences());
        emitStringList(context, sequencePropertyResult);
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

    /**
     * Emit bytecode for a string list (union of single code point ranges and multi-codepoint sequences).
     * Ported from QuickJS re_emit_string_list in libregexp.c.
     * <p>
     * Emits alternatives: try longest sequences first, then shorter ones, then code point ranges.
     * Uses SPLIT_NEXT_FIRST/GOTO to create a chain of alternatives.
     */
    private void emitStringList(CompileContext context, UnicodePropertyResolver.SequencePropertyResult stringList) {
        List<int[]> sequences = stringList.sequences();
        int[] ranges = stringList.codePointRanges();

        if (sequences.isEmpty()) {
            // Simple case: only code point ranges
            emitRanges(context, ranges, false);
            return;
        }

        // Sort sequences by length descending (longest first)
        List<int[]> sortedSequences = new ArrayList<>(sequences);
        sortedSequences.sort((a, b) -> Integer.compare(b.length, a.length));

        // Check for empty string and filter it out
        boolean hasEmptyString = false;
        List<int[]> nonEmptySequences = new ArrayList<>();
        for (int[] seq : sortedSequences) {
            if (seq.length == 0) {
                hasEmptyString = true;
            } else {
                nonEmptySequences.add(seq);
            }
        }

        boolean hasRanges = ranges.length > 0;
        List<Integer> gotoPositions = new ArrayList<>();

        // Emit each sequence as an alternative
        for (int i = 0; i < nonEmptySequences.size(); i++) {
            int[] seq = nonEmptySequences.get(i);
            boolean isLast = !hasEmptyString && !hasRanges && i == nonEmptySequences.size() - 1;

            int splitPos = -1;
            if (!isLast) {
                splitPos = context.buffer.size();
                context.buffer.appendU8(RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
                context.buffer.appendU32(0); // placeholder
            }

            // Emit each character in the sequence
            for (int codePoint : seq) {
                compileLiteralChar(context, codePoint);
            }

            if (!isLast) {
                gotoPositions.add(context.buffer.size());
                context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
                context.buffer.appendU32(0); // placeholder

                // Patch SPLIT offset: jump to after this GOTO
                int splitOffset = context.buffer.size() - (splitPos + 5);
                context.buffer.setU32(splitPos + 1, splitOffset);
            }
        }

        // Emit char ranges if present
        if (hasRanges) {
            boolean isLast = !hasEmptyString;
            int splitPos = -1;
            if (!isLast) {
                splitPos = context.buffer.size();
                context.buffer.appendU8(RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
                context.buffer.appendU32(0); // placeholder
            }

            emitRanges(context, ranges, false);

            if (!isLast) {
                // Patch SPLIT offset
                int splitOffset = context.buffer.size() - (splitPos + 5);
                context.buffer.setU32(splitPos + 1, splitOffset);
            }
        }

        // Patch all GOTO targets to point to current position (end of all alternatives)
        for (int gotoPos : gotoPositions) {
            int gotoOffset = context.buffer.size() - (gotoPos + 5);
            context.buffer.setU32(gotoPos + 1, gotoOffset);
        }
    }

    private void ensureGroupNameSize(int size) {
        while (groupNames.size() < size) {
            groupNames.add(null);
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

    private ExtendedClassSet intersectExtendedClassSets(ExtendedClassSet left, ExtendedClassSet right) {
        int[] ranges = UnicodePropertyResolver.intersectRanges(left.ranges(), right.ranges());
        List<int[]> sequences = intersectSequenceLists(left.sequences(), right.sequences());
        return normalizeExtendedClassSet(new ExtendedClassSet(ranges, sequences, null));
    }

    private List<int[]> intersectSequenceLists(List<int[]> left, List<int[]> right) {
        Set<String> rightKeys = new HashSet<>();
        for (int[] sequence : right) {
            rightKeys.add(sequenceKey(sequence));
        }
        List<int[]> result = new ArrayList<>();
        for (int[] sequence : left) {
            if (rightKeys.contains(sequenceKey(sequence))) {
                result.add(sequence);
            }
        }
        return result;
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

    private boolean isInlineModifierFlag(int ch) {
        return ch == 'i' || ch == 'm' || ch == 's';
    }

    private boolean isInvalidUnicodeSetsClassSinglePunctuator(int ch) {
        return ch == '('
                || ch == ')'
                || ch == '['
                || ch == '{'
                || ch == '}'
                || ch == '/'
                || ch == '-'
                || ch == '|';
    }

    private boolean isInvalidUnicodeSetsDoublePunctuator(CompileContext context, int ch) {
        if (!isUnicodeSetsReservedDoublePunctuator(ch)) {
            return false;
        }
        int nextPos = context.pos + 1;
        return nextPos < context.codePoints.length && context.codePoints[nextPos] == ch;
    }

    private boolean isJsIdentifierPart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || codePoint == 0x200C || codePoint == 0x200D ||
                Character.isUnicodeIdentifierPart(codePoint);
    }

    private boolean isJsIdentifierStart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint);
    }

    /**
     * Returns true if the character is a RegExp syntax character per ES2024 11.8.5.
     * These are: ^ $ \ . * + ? ( ) [ ] { } | /
     */
    private boolean isSyntaxCharacter(int ch) {
        return ch == '^' || ch == '$' || ch == '\\' || ch == '.' ||
                ch == '*' || ch == '+' || ch == '?' ||
                ch == '(' || ch == ')' ||
                ch == '[' || ch == ']' ||
                ch == '{' || ch == '}' ||
                ch == '|' || ch == '/';
    }

    private boolean isUnicodePropertyChar(int ch) {
        return (ch >= '0' && ch <= '9') ||
                (ch >= 'A' && ch <= 'Z') ||
                (ch >= 'a' && ch <= 'z') ||
                ch == '_';
    }

    private boolean isUnicodeSetsOperatorAt(CompileContext context, String operator) {
        if (context.pos + operator.length() > context.codePoints.length) {
            return false;
        }
        for (int i = 0; i < operator.length(); i++) {
            if (context.codePoints[context.pos + i] != operator.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isUnicodeSetsReservedDoublePunctuator(int ch) {
        return ch == '&'
                || ch == '!'
                || ch == '#'
                || ch == '$'
                || ch == '%'
                || ch == '*'
                || ch == '+'
                || ch == ','
                || ch == '.'
                || ch == ':'
                || ch == ';'
                || ch == '<'
                || ch == '='
                || ch == '>'
                || ch == '?'
                || ch == '@'
                || ch == '^'
                || ch == '`'
                || ch == '~';
    }

    /**
     * Following QuickJS re_need_check_adv_and_capture_init: determines if captures
     * inside a quantifier body need explicit resetting at each iteration.
     * Returns true if the atom contains complex opcodes (SPLIT, GOTO, back references)
     * that might cause some captures to not be initialized on every iteration.
     */
    private boolean needCaptureInit(byte[] atomCode) {
        int pos = 0;
        while (pos < atomCode.length) {
            int opcode = atomCode[pos] & 0xFF;
            RegExpOpcode op = RegExpOpcode.fromCode(opcode);
            switch (op) {
                case CHAR, CHAR_I, CHAR32, CHAR32_I, DOT, ANY, SPACE, NOT_SPACE, PREV:
                    break;
                case RANGE, RANGE_I, RANGE32, RANGE32_I, NOT_RANGE, NOT_RANGE_I: {
                    int rangeLen = ((atomCode[pos + 1] & 0xFF) | ((atomCode[pos + 2] & 0xFF) << 8));
                    pos += 3 + rangeLen;
                    continue;
                }
                case LINE_START, LINE_START_M, LINE_END, LINE_END_M,
                     WORD_BOUNDARY, WORD_BOUNDARY_I, NOT_WORD_BOUNDARY, NOT_WORD_BOUNDARY_I,
                     SAVE_START, SAVE_END, SAVE_RESET, SET_CHAR_POS, SET_I32:
                    break;
                case BACK_REFERENCE, BACK_REFERENCE_I, BACKWARD_BACK_REFERENCE, BACKWARD_BACK_REFERENCE_I:
                    return true;
                default:
                    // Complex opcode (SPLIT, GOTO, etc.) - captures may not be initialized
                    return true;
            }
            pos += op.getLength();
        }
        return false;
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
                case PREV:
                    // PREV moves the position in backward-compiled lookbehind atoms.
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

    private ExtendedClassSet normalizeExtendedClassSet(ExtendedClassSet extendedClassSet) {
        int[] normalizedRanges = normalizeRangePairs(extendedClassSet.ranges());
        List<int[]> normalizedSequences = new ArrayList<>();
        Set<String> sequenceKeys = new LinkedHashSet<>();
        for (int[] sequence : extendedClassSet.sequences()) {
            if (sequence.length == 0) {
                continue;
            }
            if (sequence.length == 1) {
                normalizedRanges = UnicodePropertyResolver.unionRanges(
                        normalizedRanges,
                        new int[]{sequence[0], sequence[0]});
                continue;
            }
            String key = sequenceKey(sequence);
            if (sequenceKeys.add(key)) {
                normalizedSequences.add(sequence);
            }
        }
        return new ExtendedClassSet(normalizedRanges, normalizedSequences, extendedClassSet.singleCodePoint());
    }

    private int[] normalizeRangePairs(int[] ranges) {
        if (ranges.length == 0) {
            return ranges;
        }
        int[] normalized = new int[0];
        for (int i = 0; i < ranges.length; i += 2) {
            normalized = UnicodePropertyResolver.unionRanges(normalized, new int[]{ranges[i], ranges[i + 1]});
        }
        return normalized;
    }

    private int parseClassEscape(CompileContext context, List<Integer> ranges) {
        int ch = context.codePoints[context.pos++];

        return switch (ch) {
            case 'b' -> '\b'; // backspace (U+0008) inside character class
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
                if (context.isUnicodeMode() && context.isIgnoreCase()) {
                    ranges.add(0x017F);
                    ranges.add(0x017F);
                    ranges.add(0x212A);
                    ranges.add(0x212A);
                }
                yield -1;
            }
            case 'W' -> {
                // \W - non-word characters (not [a-zA-Z0-9_])
                if (context.isUnicodeMode() && context.isIgnoreCase()) {
                    ranges.add(0);
                    ranges.add((int) '0' - 1);
                    ranges.add((int) '9' + 1);
                    ranges.add((int) 'A' - 1);
                    ranges.add((int) 'Z' + 1);
                    ranges.add((int) '_' - 1);
                    ranges.add((int) '_' + 1);
                    ranges.add((int) 'a' - 1);
                    ranges.add((int) 'z' + 1);
                    ranges.add(0x017E);
                    ranges.add(0x0180);
                    ranges.add(0x2129);
                    ranges.add(0x212B);
                    ranges.add(MAX_UNICODE_CODE_POINT);
                } else {
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
                }
                yield -1;
            }
            case 's' -> {
                // \s - whitespace characters
                appendRanges(ranges, UnicodePropertyResolver.resolveBinaryProperty("White_Space"));
                yield -1;
            }
            case 'S' -> {
                // \S - non-whitespace characters
                appendRanges(ranges, invertRanges(UnicodePropertyResolver.resolveBinaryProperty("White_Space")));
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
                    // Annex B.1.4: In non-unicode mode, \c inside character class also
                    // accepts DecimalDigit (0-9) and _ as ClassControlLetter
                    if (!context.isUnicodeMode() &&
                            ((next >= '0' && next <= '9') || next == '_')) {
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
                    boolean closedBrace = false;
                    while (context.pos < context.codePoints.length) {
                        int hexCh = context.codePoints[context.pos];
                        if (hexCh == '}') {
                            context.pos++;
                            closedBrace = true;
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
                    if (!closedBrace) {
                        throw new RegExpSyntaxException("Invalid unicode escape");
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
                    throw new RegExpSyntaxException("Invalid escape sequence");
                }
                yield parseLegacyOctalEscape(context, ch - '0');
            }
            default -> {
                if (context.isUnicodeMode() && !isSyntaxCharacter(ch) && ch != '/' && ch != '-') {
                    throw new RegExpSyntaxException("invalid escape sequence in regular expression");
                }
                yield ch;
            }
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
                if (Character.isHighSurrogate((char) codePoint) && pos < codePoints.length && codePoints[pos] == '\\') {
                    EscapedCodePoint trailingEscapedCodePoint = parseUnicodeEscapeInGroupName(codePoints, pos);
                    if (trailingEscapedCodePoint != null && Character.isLowSurrogate((char) trailingEscapedCodePoint.codePoint())) {
                        codePoint = Character.toCodePoint(
                                (char) codePoint,
                                (char) trailingEscapedCodePoint.codePoint());
                        pos = trailingEscapedCodePoint.nextPos();
                    }
                }
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
        long minLong = 0;
        int min;
        int max;

        // Parse first number
        while (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];
            if (ch >= '0' && ch <= '9') {
                minLong = appendDecimalDigitWithClamp(minLong, ch - '0');
                context.pos++;
            } else {
                break;
            }
        }
        min = (int) Math.min(minLong, MAX_QUANTIFIER_BOUND);

        // Check for comma
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == ',') {
            context.pos++;

            // Check if there's a max value
            if (context.pos < context.codePoints.length &&
                    context.codePoints[context.pos] >= '0' &&
                    context.codePoints[context.pos] <= '9') {
                long maxLong = 0;
                while (context.pos < context.codePoints.length) {
                    int ch = context.codePoints[context.pos];
                    if (ch >= '0' && ch <= '9') {
                        maxLong = appendDecimalDigitWithClamp(maxLong, ch - '0');
                        context.pos++;
                    } else {
                        break;
                    }
                }
                max = (int) Math.min(maxLong, MAX_QUANTIFIER_BOUND);
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

    private ExtendedClassSet parseUnicodeSetsElement(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Unexpected end of character class");
        }
        int currentChar = context.codePoints[context.pos];
        if (currentChar == '\\') {
            context.pos++;
            return parseUnicodeSetsEscapeElement(context);
        }
        if (currentChar == ']' || currentChar == '[') {
            throw new RegExpSyntaxException("Invalid character in UnicodeSets character class");
        }
        if (isInvalidUnicodeSetsClassSinglePunctuator(currentChar)) {
            throw new RegExpSyntaxException("Invalid character in UnicodeSets character class");
        }
        if (isInvalidUnicodeSetsDoublePunctuator(context, currentChar)) {
            throw new RegExpSyntaxException("Invalid character in UnicodeSets character class");
        }
        context.pos++;
        return singleCodePointExtendedClassSet(currentChar);
    }

    private ExtendedClassSet parseUnicodeSetsEscapeElement(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Incomplete escape in character class");
        }
        int escapedChar = context.codePoints[context.pos];
        if (escapedChar == 'q') {
            return parseUnicodeSetsStringLiteralSet(context);
        }
        if (escapedChar == 'p' || escapedChar == 'P') {
            context.pos++;
            return parseUnicodeSetsPropertyEscapeSet(context, escapedChar == 'P');
        }

        List<Integer> ranges = new ArrayList<>();
        int parsedValue = parseClassEscape(context, ranges);
        if (parsedValue == -1) {
            return normalizeExtendedClassSet(new ExtendedClassSet(toIntArray(ranges), new ArrayList<>(), null));
        }
        return singleCodePointExtendedClassSet(parsedValue);
    }

    private ExtendedClassSet parseUnicodeSetsExpression(CompileContext context) {
        ExtendedClassSet result = parseUnicodeSetsIntersectionDifference(context);
        while (true) {
            if (context.pos >= context.codePoints.length || context.codePoints[context.pos] == ']') {
                break;
            }
            if (isUnicodeSetsOperatorAt(context, "&&") || isUnicodeSetsOperatorAt(context, "--")) {
                break;
            }
            ExtendedClassSet nextOperand = parseUnicodeSetsIntersectionDifference(context);
            result = unionExtendedClassSets(result, nextOperand);
        }
        return normalizeExtendedClassSet(result);
    }

    private ExtendedClassSet parseUnicodeSetsIntersectionDifference(CompileContext context) {
        ExtendedClassSet result = parseUnicodeSetsOperand(context);
        while (true) {
            if (isUnicodeSetsOperatorAt(context, "&&")) {
                context.pos += 2;
                ExtendedClassSet right = parseUnicodeSetsOperand(context);
                result = intersectExtendedClassSets(result, right);
                continue;
            }
            if (isUnicodeSetsOperatorAt(context, "--")) {
                context.pos += 2;
                ExtendedClassSet right = parseUnicodeSetsOperand(context);
                result = subtractExtendedClassSets(result, right);
                continue;
            }
            break;
        }
        return result;
    }

    private ExtendedClassSet parseUnicodeSetsOperand(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Unexpected end of character class");
        }
        if (context.codePoints[context.pos] == '[') {
            context.pos++;
            boolean inverted = false;
            if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '^') {
                inverted = true;
                context.pos++;
            }
            ExtendedClassSet nested = parseUnicodeSetsExpression(context);
            if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ']') {
                throw new RegExpSyntaxException("Unclosed character class");
            }
            context.pos++;
            if (inverted) {
                if (!nested.sequences().isEmpty()) {
                    throw new RegExpSyntaxException("Invalid UnicodeSets negated class");
                }
                nested = new ExtendedClassSet(invertRanges(nested.ranges()), List.of(), null);
            }
            return nested;
        }
        ExtendedClassSet firstElement = parseUnicodeSetsElement(context);
        if (firstElement.singleCodePoint() == null) {
            return firstElement;
        }
        if (context.pos < context.codePoints.length
                && context.codePoints[context.pos] == '-'
                && !isUnicodeSetsOperatorAt(context, "--")
                && context.pos + 1 < context.codePoints.length
                && context.codePoints[context.pos + 1] != ']') {
            int rangeStart = firstElement.singleCodePoint();
            context.pos++;
            ExtendedClassSet rangeEndElement = parseUnicodeSetsElement(context);
            if (rangeEndElement.singleCodePoint() == null) {
                throw new RegExpSyntaxException("Invalid range in character class");
            }
            int rangeEnd = rangeEndElement.singleCodePoint();
            if (rangeStart > rangeEnd) {
                throw new RegExpSyntaxException("Range out of order in character class");
            }
            return new ExtendedClassSet(new int[]{rangeStart, rangeEnd}, List.of(), null);
        }
        return firstElement;
    }

    private ExtendedClassSet parseUnicodeSetsPropertyEscapeSet(CompileContext context, boolean inverted) {
        int savedPos = context.pos;
        try {
            int[] propertyRanges = parseUnicodePropertyEscape(context);
            if (inverted) {
                if (context.isUnicodeMode() && context.isIgnoreCase()) {
                    propertyRanges = invertRanges(propertyRanges);
                    return new ExtendedClassSet(propertyRanges, List.of(), null);
                }
                propertyRanges = invertRanges(propertyRanges);
            }
            return new ExtendedClassSet(propertyRanges, List.of(), null);
        } catch (RegExpSyntaxException e) {
            if (!context.isUnicodeSetsMode() || inverted) {
                throw e;
            }
            context.pos = savedPos;
            UnicodePropertyResolver.SequencePropertyResult sequencePropertyResult =
                    parseUnicodeSetsSequenceProperty(context);
            return normalizeExtendedClassSet(new ExtendedClassSet(
                    sequencePropertyResult.codePointRanges(),
                    new ArrayList<>(sequencePropertyResult.sequences()),
                    null));
        }
    }

    private UnicodePropertyResolver.SequencePropertyResult parseUnicodeSetsSequenceProperty(CompileContext context) {
        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '{') {
            throw new RegExpSyntaxException("expecting '{' after \\p");
        }
        context.pos++;
        int nameStart = context.pos;
        while (context.pos < context.codePoints.length && isUnicodePropertyChar(context.codePoints[context.pos])) {
            context.pos++;
        }
        String propertyName = new String(context.codePoints, nameStart, context.pos - nameStart);
        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '}') {
            throw new RegExpSyntaxException("expecting '}'");
        }
        context.pos++;
        UnicodePropertyResolver.SequencePropertyResult sequencePropertyResult =
                UnicodePropertyResolver.resolveSequenceProperty(propertyName);
        if (sequencePropertyResult == null) {
            throw new RegExpSyntaxException("unknown unicode property name");
        }
        return sequencePropertyResult;
    }

    private ExtendedClassSet parseUnicodeSetsStringLiteralSet(CompileContext context) {
        context.pos++; // Skip 'q'
        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '{') {
            throw new RegExpSyntaxException("Invalid UnicodeSets string literal");
        }
        context.pos++;

        List<int[]> sequences = new ArrayList<>();
        while (true) {
            List<Integer> sequenceCodePoints = new ArrayList<>();
            while (context.pos < context.codePoints.length) {
                int currentChar = context.codePoints[context.pos];
                if (currentChar == '|' || currentChar == '}') {
                    break;
                }
                if (currentChar == '\\') {
                    context.pos++;
                    if (context.pos >= context.codePoints.length) {
                        throw new RegExpSyntaxException("Invalid UnicodeSets string literal");
                    }
                    UnicodeEscapeParseResult unicodeEscapeParseResult = tryParseUnicodeEscapeAt(context, context.pos - 1);
                    if (unicodeEscapeParseResult != null) {
                        sequenceCodePoints.add(unicodeEscapeParseResult.codePoint());
                        context.pos = unicodeEscapeParseResult.nextPos();
                    } else {
                        int escapedChar = context.codePoints[context.pos++];
                        sequenceCodePoints.add(escapedChar);
                    }
                } else {
                    context.pos++;
                    sequenceCodePoints.add(currentChar);
                }
            }

            int[] sequence = new int[sequenceCodePoints.size()];
            for (int i = 0; i < sequenceCodePoints.size(); i++) {
                sequence[i] = sequenceCodePoints.get(i);
            }
            sequences.add(sequence);

            if (context.pos >= context.codePoints.length) {
                throw new RegExpSyntaxException("Invalid UnicodeSets string literal");
            }
            if (context.codePoints[context.pos] == '}') {
                context.pos++;
                break;
            }
            context.pos++; // Skip '|'
        }

        ExtendedClassSet result = new ExtendedClassSet(new int[0], sequences, null);
        return normalizeExtendedClassSet(result);
    }

    private int[] resolveUnicodePropertyRanges(String propertyName, String propertyValue) {
        if (propertyValue != null) {
            if ("General_Category".equals(propertyName) || "gc".equals(propertyName)) {
                int[] ranges = UnicodePropertyResolver.resolveGeneralCategory(propertyValue);
                if (ranges == null) {
                    throw new RegExpSyntaxException("unknown unicode general category");
                }
                return ranges;
            }
            if ("Script".equals(propertyName) || "sc".equals(propertyName)) {
                int[] ranges = UnicodePropertyResolver.resolveScript(propertyValue, false);
                if (ranges == null) {
                    throw new RegExpSyntaxException("unknown unicode script");
                }
                return ranges;
            }
            if ("Script_Extensions".equals(propertyName) || "scx".equals(propertyName)) {
                int[] ranges = UnicodePropertyResolver.resolveScript(propertyValue, true);
                if (ranges == null) {
                    throw new RegExpSyntaxException("unknown unicode script");
                }
                return ranges;
            }
            throw new RegExpSyntaxException("unknown unicode property name");
        }

        // Try General Category first (bare name like \p{Lu} or \p{Letter})
        int[] ranges = UnicodePropertyResolver.resolveGeneralCategory(propertyName);
        if (ranges != null) {
            return ranges;
        }
        // Try binary property (like \p{Alphabetic} or \p{Alpha})
        ranges = UnicodePropertyResolver.resolveBinaryProperty(propertyName);
        if (ranges != null) {
            return ranges;
        }
        throw new RegExpSyntaxException("unknown unicode property name");
    }

    private void reverseAlternativeTerm(CompileContext context, int alternativeStart, int termStart) {
        if (termStart <= alternativeStart || termStart >= context.buffer.size()) {
            return;
        }
        int end = context.buffer.size();
        byte[] previousTerms = context.buffer.getRange(alternativeStart, termStart - alternativeStart);
        byte[] currentTerm = context.buffer.getRange(termStart, end - termStart);
        context.buffer.truncate(alternativeStart);
        context.buffer.append(currentTerm);
        context.buffer.append(previousTerms);
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
                        throw new RegExpSyntaxException("Duplicate capture group name");
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

    private String sequenceKey(int[] sequence) {
        StringBuilder keyBuilder = new StringBuilder(sequence.length * 8);
        for (int codePoint : sequence) {
            keyBuilder.append(codePoint).append(',');
        }
        return keyBuilder.toString();
    }

    private ExtendedClassSet singleCodePointExtendedClassSet(int codePoint) {
        return new ExtendedClassSet(new int[]{codePoint, codePoint}, List.of(), codePoint);
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

    private ExtendedClassSet subtractExtendedClassSets(ExtendedClassSet left, ExtendedClassSet right) {
        int[] ranges = UnicodePropertyResolver.intersectRanges(left.ranges(), invertRanges(right.ranges()));
        List<int[]> sequences = subtractSequenceLists(left.sequences(), right.sequences());
        return normalizeExtendedClassSet(new ExtendedClassSet(ranges, sequences, null));
    }

    private List<int[]> subtractSequenceLists(List<int[]> left, List<int[]> right) {
        Set<String> rightKeys = new HashSet<>();
        for (int[] sequence : right) {
            rightKeys.add(sequenceKey(sequence));
        }
        List<int[]> result = new ArrayList<>();
        for (int[] sequence : left) {
            if (!rightKeys.contains(sequenceKey(sequence))) {
                result.add(sequence);
            }
        }
        return result;
    }

    private int toInlineModifierFlagBit(int ch) {
        return switch (ch) {
            case 'i' -> RegExpBytecode.FLAG_IGNORECASE;
            case 'm' -> RegExpBytecode.FLAG_MULTILINE;
            case 's' -> RegExpBytecode.FLAG_DOTALL;
            default -> 0;
        };
    }

    private int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private InlineModifierGroupParseResult tryParseInlineModifierGroup(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            return null;
        }
        int startChar = context.codePoints[context.pos];
        if (!(isInlineModifierFlag(startChar) || startChar == '-')) {
            return null;
        }

        int addFlags = 0;
        int removeFlags = 0;
        boolean parsingRemoveFlags = false;
        boolean sawDash = false;
        boolean sawModifierFlag = false;

        while (context.pos < context.codePoints.length) {
            int currentChar = context.codePoints[context.pos];
            if (currentChar == ':') {
                if (!sawModifierFlag) {
                    throw new RegExpSyntaxException("Invalid inline modifiers");
                }
                context.pos++;
                int scopedFlags = (context.currentFlags | addFlags) & ~removeFlags;
                return new InlineModifierGroupParseResult(scopedFlags);
            }
            if (currentChar == '-') {
                if (sawDash) {
                    throw new RegExpSyntaxException("Invalid inline modifiers");
                }
                sawDash = true;
                parsingRemoveFlags = true;
                context.pos++;
                continue;
            }
            if (!isInlineModifierFlag(currentChar)) {
                throw new RegExpSyntaxException("Invalid inline modifiers");
            }
            int flagBit = toInlineModifierFlagBit(currentChar);
            if (!parsingRemoveFlags) {
                if ((addFlags & flagBit) != 0) {
                    throw new RegExpSyntaxException("Invalid inline modifiers");
                }
                addFlags |= flagBit;
            } else {
                if ((removeFlags & flagBit) != 0 || (addFlags & flagBit) != 0) {
                    throw new RegExpSyntaxException("Invalid inline modifiers");
                }
                removeFlags |= flagBit;
            }
            sawModifierFlag = true;
            context.pos++;
        }

        throw new RegExpSyntaxException("Incomplete group syntax");
    }

    private UnicodeEscapeParseResult tryParseUnicodeEscapeAt(CompileContext context, int startPos) {
        if (startPos + 1 >= context.codePoints.length
                || context.codePoints[startPos] != '\\'
                || context.codePoints[startPos + 1] != 'u') {
            return null;
        }
        int currentPos = startPos + 2;
        if (currentPos < context.codePoints.length && context.codePoints[currentPos] == '{') {
            currentPos++;
            int value = 0;
            int digitCount = 0;
            while (currentPos < context.codePoints.length && context.codePoints[currentPos] != '}') {
                int hexValue = hexValue(context.codePoints[currentPos]);
                if (hexValue < 0) {
                    return null;
                }
                value = (value << 4) | hexValue;
                digitCount++;
                if (digitCount > 6 || value > MAX_UNICODE_CODE_POINT) {
                    return null;
                }
                currentPos++;
            }
            if (digitCount == 0
                    || currentPos >= context.codePoints.length
                    || context.codePoints[currentPos] != '}') {
                return null;
            }
            return new UnicodeEscapeParseResult(value, currentPos + 1);
        }
        if (currentPos + 3 >= context.codePoints.length) {
            return null;
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int hexValue = hexValue(context.codePoints[currentPos + i]);
            if (hexValue < 0) {
                return null;
            }
            value = (value << 4) | hexValue;
        }
        return new UnicodeEscapeParseResult(value, currentPos + 4);
    }

    private ExtendedClassSet unionExtendedClassSets(ExtendedClassSet left, ExtendedClassSet right) {
        int[] ranges = UnicodePropertyResolver.unionRanges(left.ranges(), right.ranges());
        List<int[]> sequences = unionSequenceLists(left.sequences(), right.sequences());
        return normalizeExtendedClassSet(new ExtendedClassSet(ranges, sequences, null));
    }

    private List<int[]> unionSequenceLists(List<int[]> left, List<int[]> right) {
        List<int[]> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (int[] sequence : left) {
            if (keys.add(sequenceKey(sequence))) {
                result.add(sequence);
            }
        }
        for (int[] sequence : right) {
            if (keys.add(sequenceKey(sequence))) {
                result.add(sequence);
            }
        }
        return result;
    }

    private static class CompileContext {
        final DynamicBuffer buffer;
        final int[] codePoints;
        final int flags;
        final String pattern;
        int currentFlags;
        boolean lastAtomCanRepeat;
        int nextAdvanceCheckRegister;
        int pos;

        CompileContext(String pattern, int flags, DynamicBuffer buffer) {
            this.pattern = pattern;
            this.codePoints = pattern.codePoints().toArray();
            this.flags = flags;
            this.currentFlags = flags;
            this.buffer = buffer;
            this.lastAtomCanRepeat = false;
            this.nextAdvanceCheckRegister = 0;
            this.pos = 0;
        }

        boolean isDotAll() {
            return (currentFlags & RegExpBytecode.FLAG_DOTALL) != 0;
        }

        boolean isIgnoreCase() {
            return (currentFlags & RegExpBytecode.FLAG_IGNORECASE) != 0;
        }

        boolean isMultiline() {
            return (currentFlags & RegExpBytecode.FLAG_MULTILINE) != 0;
        }

        boolean isUnicode() {
            return (currentFlags & (RegExpBytecode.FLAG_UNICODE | RegExpBytecode.FLAG_UNICODE_SETS)) != 0;
        }

        boolean isUnicodeMode() {
            return (currentFlags & (RegExpBytecode.FLAG_UNICODE | RegExpBytecode.FLAG_UNICODE_SETS)) != 0;
        }

        boolean isUnicodeSetsMode() {
            return (currentFlags & RegExpBytecode.FLAG_UNICODE_SETS) != 0;
        }
    }

    private record EscapedCodePoint(int codePoint, int nextPos) {
    }

    private record ExtendedClassSet(int[] ranges, List<int[]> sequences, Integer singleCodePoint) {
    }

    private record GroupNameParseResult(String name, int nextPos) {
    }

    private record InlineModifierGroupParseResult(int scopedFlags) {
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

    private record UnicodeEscapeParseResult(int codePoint, int nextPos) {
    }
}
