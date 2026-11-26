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
import com.caoccao.qjs4j.unicode.UnicodeRangeBuilder;

import java.util.*;

import static com.caoccao.qjs4j.regexp.RegExpCompiler.*;

/** Parses character classes and emits the bytecode for their code-point ranges and string alternatives. */
final class RegExpCharacterClass {
    private static final int[] ASCII_WORD = {'0', '9', 'A', 'Z', '_', '_', 'a', 'z'};
    private static final int[] DIGITS = {'0', '9'};
    // Long s and Kelvin sign fold to ASCII word characters only in Unicode ignore-case mode.
    private static final int[] FOLDED_WORD = {'0', '9', 'A', 'Z', '_', '_', 'a', 'z', 0x017F, 0x017F, 0x212A, 0x212A};
    /**
     * JavaScript whitespace ranges for \s (differs from Unicode White_Space: includes U+FEFF which is not in the
     * Unicode White_Space property). Pairs of [start, end] inclusive.
     */
    private static final int[] JS_WHITESPACE_RANGES = {0x0009, 0x000D, // TAB, LF, VT, FF, CR
            0x0020, 0x0020, // SPACE
            0x00A0, 0x00A0, // NO-BREAK SPACE
            0x1680, 0x1680, // OGHAM SPACE MARK
            0x2000, 0x200A, // EN QUAD .. HAIR SPACE
            0x2028, 0x2029, // LINE SEPARATOR, PARAGRAPH SEPARATOR
            0x202F, 0x202F, // NARROW NO-BREAK SPACE
            0x205F, 0x205F, // MEDIUM MATHEMATICAL SPACE
            0x3000, 0x3000, // IDEOGRAPHIC SPACE
            0xFEFF, 0xFEFF, // ZERO WIDTH NO-BREAK SPACE (BOM)
    };
    private static final int[] NON_DIGITS = UnicodePropertyResolver.invertRanges(DIGITS);
    private static final int[] NON_FOLDED_WORD = UnicodePropertyResolver.invertRanges(FOLDED_WORD);
    private static final int[] NON_WHITESPACE = UnicodePropertyResolver.invertRanges(JS_WHITESPACE_RANGES);
    private static final int[] NON_WORD = UnicodePropertyResolver.invertRanges(ASCII_WORD);
    private RegExpCharacterClass() {
    }

    static int[] characterClassRanges(CompileContext context, int escape) {
        boolean folded = context.isUnicodeMode() && context.isIgnoreCase();
        return switch (escape) {
            case 'd' -> DIGITS;
            case 'D' -> NON_DIGITS;
            case 'w' -> folded ? FOLDED_WORD : ASCII_WORD;
            case 'W' -> folded ? NON_FOLDED_WORD : NON_WORD;
            case 's' -> JS_WHITESPACE_RANGES;
            case 'S' -> NON_WHITESPACE;
            default -> throw new IllegalArgumentException("Invalid character class escape");
        };
    }

    static void compileCharacterClass(CompileContext context, boolean isBackwardDirection) {
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
        if (context.isUnicodeSetsMode()) {
            emitExtendedClassSet(context, readUnicodeSet(context));
        } else {
            compileRangeClass(context);
        }
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
    }

    private static void compileRangeClass(CompileContext context) {
        context.pos++;
        boolean inverted = context.consume('^');
        UnicodeRangeBuilder ranges = new UnicodeRangeBuilder();
        while (!context.consume(']')) {
            int start = readRangeAtom(context, ranges);
            boolean hasRange = context.pos + 1 < context.codePoints.length && context.codePoints[context.pos] == '-'
                    && context.codePoints[context.pos + 1] != ']';
            if (start == -1) {
                if (hasRange && context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Invalid range in character class");
                }
                continue;
            }
            int end = start;
            if (hasRange) {
                context.pos++;
                end = readRangeAtom(context, ranges);
                if (end == -1) {
                    if (context.isUnicodeMode()) {
                        throw new RegExpSyntaxException("Invalid range in character class");
                    }
                    // Annex B treats a class escape endpoint as a union with a literal hyphen.
                    ranges.add((int) '-', (int) '-');
                    end = start;
                }
                if (start > end) {
                    throw new RegExpSyntaxException("Range out of order in character class");
                }
            }
            ranges.add(start, end);
        }
        int[] pairs = ranges.toArray();
        emitRanges(context, context.isIgnoreCase() ? pairs : UnicodeRangeBuilder.normalize(pairs), inverted);
    }

    /**
     * Compile a \p{} or \P{} Unicode property escape. Handles both regular properties and sequence properties (v flag).
     * Based on QuickJS parse_unicode_property + re_emit_string_list.
     */
    static void compileUnicodePropertyEscape(CompileContext context, boolean isInverted, boolean isBackwardDirection) {
        ExtendedClassSet property = readProperty(context, context.isUnicodeSetsMode() && !isInverted);
        if (!property.sequences().isEmpty()) {
            emitExtendedClassSet(context, property);
            return;
        }
        int[] propertyRanges = property.ranges();
        // Regular property resolved successfully
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
        boolean emitInvertedRanges = isInverted;
        if (isInverted && context.isUnicodeMode() && context.isIgnoreCase()) {
            propertyRanges = UnicodePropertyResolver.invertRanges(propertyRanges);
            emitInvertedRanges = false;
        }
        emitRanges(context, propertyRanges, emitInvertedRanges);
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
    }

    private static void emitExtendedClassSet(CompileContext context, ExtendedClassSet extendedClassSet) {
        emitStringList(context, extendedClassSet.ranges(), extendedClassSet.sequences());
    }

    /**
     * Emit everything a {@code RANGE}/{@code NOT_RANGE} opcode carries after its opcode byte: the byte length of the
     * rest of the instruction, the number of ranges, and the ranges themselves as inclusive 32-bit code point pairs.
     * <p>
     * Both header fields are 32 bits. They were 16 bits and neither was checked, so a class of 8,192 disjoint ranges
     * produced a 65,538-byte payload whose length field wrapped to 2: the matcher then resumed decoding opcodes in the
     * middle of range data, and a valid pattern silently matched the wrong thing rather than being rejected. The
     * arithmetic also lived at seven call sites, each with its own hand-written {@code 2 + n * 8}; it lives here now.
     *
     * @param context
     *            the compile context
     * @param ranges
     *            inclusive code point pairs, {@code [start0, end0, start1, end1, ...]}
     */
    static void emitRangePayload(CompileContext context, int[] ranges) {
        int numRanges = ranges.length / 2;
        // Four bytes for the count field, eight per range.
        long dataSize = 4L + (long) numRanges * 8L;
        context.buffer.appendU32(dataSize);
        context.buffer.appendU32(numRanges);
        for (int range : ranges) {
            context.buffer.appendU32(range);
        }
    }

    private static void emitRanges(CompileContext context, int[] ranges, boolean inverted) {
        context.buffer.appendU8(inverted
                ? (context.isIgnoreCase() ? RegExpOpcode.NOT_RANGE_I.getCode() : RegExpOpcode.NOT_RANGE.getCode())
                : (context.isIgnoreCase() ? RegExpOpcode.RANGE_I.getCode() : RegExpOpcode.RANGE.getCode()));

        emitRangePayload(context, ranges);
    }

    /**
     * Emit bytecode for a string list (union of single code point ranges and multi-codepoint sequences). Ported from
     * QuickJS re_emit_string_list in libregexp.c.
     * <p>
     * Emits alternatives: try longest sequences first, then shorter ones, then code point ranges. Uses
     * SPLIT_NEXT_FIRST/GOTO to create a chain of alternatives.
     */
    private static void emitStringList(CompileContext context, int[] ranges, List<int[]> sequences) {

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

    private static List<int[]> filterSequences(List<int[]> left, List<int[]> right, boolean keepMatches) {
        Set<String> rightKeys = new HashSet<>();
        for (int[] sequence : right) {
            rightKeys.add(sequenceKey(sequence));
        }
        List<int[]> result = new ArrayList<>();
        for (int[] sequence : left) {
            if (rightKeys.contains(sequenceKey(sequence)) == keepMatches) {
                result.add(sequence);
            }
        }
        return result;
    }

    private static ExtendedClassSet intersectExtendedClassSets(ExtendedClassSet left, ExtendedClassSet right) {
        int[] ranges = UnicodePropertyResolver.intersectRanges(left.ranges(), right.ranges());
        List<int[]> sequences = filterSequences(left.sequences(), right.sequences(), true);
        return normalizeExtendedClassSet(new ExtendedClassSet(ranges, sequences, null));
    }

    private static boolean isInvalidUnicodeSetsClassSinglePunctuator(int ch) {
        return ch == '(' || ch == ')' || ch == '[' || ch == '{' || ch == '}' || ch == '/' || ch == '-' || ch == '|';
    }

    private static boolean isInvalidUnicodeSetsDoublePunctuator(CompileContext context, int ch) {
        if (!isUnicodeSetsReservedDoublePunctuator(ch)) {
            return false;
        }
        int nextPos = context.pos + 1;
        return nextPos < context.codePoints.length && context.codePoints[nextPos] == ch;
    }

    private static boolean isUnicodePropertyChar(int ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '_';
    }

    private static boolean isUnicodeSetsClassEnd(CompileContext context) {
        return context.pos >= context.codePoints.length || context.codePoints[context.pos] == ']';
    }

    private static boolean isUnicodeSetsOperatorAt(CompileContext context, String operator) {
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

    private static boolean isUnicodeSetsReservedDoublePunctuator(int ch) {
        return ch == '&' || ch == '!' || ch == '#' || ch == '$' || ch == '%' || ch == '*' || ch == '+' || ch == ','
                || ch == '.' || ch == ':' || ch == ';' || ch == '<' || ch == '=' || ch == '>' || ch == '?' || ch == '@'
                || ch == '^' || ch == '`' || ch == '~';
    }

    private static ExtendedClassSet normalizeExtendedClassSet(ExtendedClassSet extendedClassSet) {
        int[] normalizedRanges = UnicodeRangeBuilder.normalize(extendedClassSet.ranges());
        List<int[]> normalizedSequences = new ArrayList<>();
        Set<String> sequenceKeys = new LinkedHashSet<>();
        for (int[] sequence : extendedClassSet.sequences()) {
            if (sequence.length == 0) {
                continue;
            }
            if (sequence.length == 1) {
                normalizedRanges = UnicodePropertyResolver.unionRanges(normalizedRanges,
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

    private static int parseClassEscape(CompileContext context, UnicodeRangeBuilder ranges) {
        int ch = context.codePoints[context.pos++];

        return switch (ch) {
            case 'b' -> '\b'; // backspace (U+0008) inside character class
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'f' -> '\f';
            case 'v' -> '\u000B';
            case 'd', 'D', 'w', 'W', 's', 'S' -> {
                ranges.addAll(characterClassRanges(context, ch));
                yield -1;
            }
            case 'p', 'P' -> {
                if (!context.isUnicodeMode()) {
                    yield ch;
                }
                int[] propertyRanges = readProperty(context, false).ranges();
                if (ch == 'P') {
                    propertyRanges = UnicodePropertyResolver.invertRanges(propertyRanges);
                }
                ranges.addAll(propertyRanges);
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
                    if (!context.isUnicodeMode() && ((next >= '0' && next <= '9') || next == '_')) {
                        context.pos++;
                        yield next % 32;
                    }
                }
                // AnnexB: Invalid \c in char class → class contains '\' and 'c'
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Invalid control escape");
                }
                ranges.add((int) '\\', (int) '\\');
                yield 'c';
            }
            case 'x' -> readFixedHexEscape(context, 'x', 2);
            case 'u' -> parseUnicodeEscape(context);
            case '0' -> {
                if (context.isUnicodeMode()) {
                    // In unicode mode, \0 is allowed only if NOT followed by a decimal digit
                    if (context.pos < context.codePoints.length) {
                        int nextCh = context.codePoints[context.pos];
                        if (nextCh >= '0' && nextCh <= '9') {
                            throw new RegExpSyntaxException("Invalid escape sequence");
                        }
                    }
                    yield 0; // NULL character
                } else {
                    yield parseLegacyOctalEscape(context, 0);
                }
            }
            case '1', '2', '3', '4', '5', '6', '7' -> {
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

    private static ExtendedClassSet parseUnicodeSetsElement(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Unterminated character class");
        }
        int currentChar = context.codePoints[context.pos];
        if (currentChar == '\\') {
            context.pos++;
            return parseUnicodeSetsEscapeElement(context);
        }
        // V8's wording for each.
        if (currentChar == ']' || currentChar == '[') {
            throw new RegExpSyntaxException("Invalid character in character class");
        }
        if (isInvalidUnicodeSetsClassSinglePunctuator(currentChar)) {
            throw new RegExpSyntaxException("Invalid character in character class");
        }
        if (isInvalidUnicodeSetsDoublePunctuator(context, currentChar)) {
            throw new RegExpSyntaxException("Invalid set operation in character class");
        }
        context.pos++;
        return singleCodePointExtendedClassSet(currentChar);
    }

    private static ExtendedClassSet parseUnicodeSetsEscapeElement(CompileContext context) {
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

        if ("dDwWsS".indexOf(escapedChar) >= 0) {
            context.pos++;
            return new ExtendedClassSet(characterClassRanges(context, escapedChar), List.of(), null);
        }
        UnicodeRangeBuilder ranges = new UnicodeRangeBuilder();
        int parsedValue = parseClassEscape(context, ranges);
        if (parsedValue == -1) {
            return normalizeExtendedClassSet(new ExtendedClassSet(ranges.toArray(), new ArrayList<>(), null));
        }
        return singleCodePointExtendedClassSet(parsedValue);
    }

    /**
     * ClassSetExpression in {@code v} mode: exactly one of a union, an intersection ({@code &&}) or a subtraction
     * ({@code --}), whose operands are nested classes, escapes or single characters but not ranges. The operators used
     * to be accepted in any mix, as in {@code [a&&b--c]} and {@code [ab&&c]}, and {@code [a&&&b]} was accepted.
     */
    private static ExtendedClassSet parseUnicodeSetsExpression(CompileContext context) {
        if (isUnicodeSetsClassEnd(context)) {
            return new ExtendedClassSet(new int[0], List.of(), null);
        }
        // A class cannot start with an operator; V8 reports a leading "--" as two stray '-' characters.
        if (isUnicodeSetsOperatorAt(context, "&&")) {
            throw new RegExpSyntaxException("Invalid set operation in character class");
        }
        if (isUnicodeSetsOperatorAt(context, "--")) {
            throw new RegExpSyntaxException("Invalid character in character class");
        }
        context.lastUnicodeSetsOperandWasRange = false;
        ExtendedClassSet result = parseUnicodeSetsOperand(context);
        String operator = isUnicodeSetsOperatorAt(context, "&&")
                ? "&&"
                : isUnicodeSetsOperatorAt(context, "--") ? "--" : null;
        if (operator != null) {
            if (context.lastUnicodeSetsOperandWasRange) {
                throw new RegExpSyntaxException("Invalid set operation in character class");
            }
            while (isUnicodeSetsOperatorAt(context, operator)) {
                context.pos += 2;
                // ClassIntersection :: ClassSetOperand && [lookahead ≠ &] ClassSetOperand
                if ("&&".equals(operator) && context.pos < context.codePoints.length
                        && context.codePoints[context.pos] == '&') {
                    throw new RegExpSyntaxException("Invalid character in character class");
                }
                context.lastUnicodeSetsOperandWasRange = false;
                ExtendedClassSet right = parseUnicodeSetsOperand(context);
                if (context.lastUnicodeSetsOperandWasRange) {
                    throw new RegExpSyntaxException("Invalid set operation in character class");
                }
                result = "&&".equals(operator)
                        ? intersectExtendedClassSets(result, right)
                        : subtractExtendedClassSets(result, right);
            }
            if (!isUnicodeSetsClassEnd(context)) {
                throw new RegExpSyntaxException("Invalid set operation in character class");
            }
            return normalizeExtendedClassSet(result);
        }
        while (!isUnicodeSetsClassEnd(context)) {
            if (isUnicodeSetsOperatorAt(context, "&&") || isUnicodeSetsOperatorAt(context, "--")) {
                throw new RegExpSyntaxException("Invalid set operation in character class");
            }
            result = unionExtendedClassSets(result, parseUnicodeSetsOperand(context));
        }
        return normalizeExtendedClassSet(result);
    }

    private static ExtendedClassSet parseUnicodeSetsOperand(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Unterminated character class");
        }
        if (context.codePoints[context.pos] == '[') {
            ExtendedClassSet nested = readUnicodeSet(context);
            context.lastUnicodeSetsOperandWasRange = false;
            return nested;
        }
        ExtendedClassSet firstElement = parseUnicodeSetsElement(context);
        if (firstElement.singleCodePoint() == null) {
            return firstElement;
        }
        if (context.pos + 1 == context.codePoints.length && context.codePoints[context.pos] == '-') {
            // A range cut off by the end of the pattern: the class is unterminated, as V8 reports it.
            throw new RegExpSyntaxException("Unterminated character class");
        }
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '-'
                && !isUnicodeSetsOperatorAt(context, "--") && context.pos + 1 < context.codePoints.length
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
            context.lastUnicodeSetsOperandWasRange = true;
            return new ExtendedClassSet(new int[]{rangeStart, rangeEnd}, List.of(), null);
        }
        return firstElement;
    }

    private static ExtendedClassSet parseUnicodeSetsPropertyEscapeSet(CompileContext context, boolean inverted) {
        ExtendedClassSet set = readProperty(context, !inverted);
        if (inverted) {
            return new ExtendedClassSet(UnicodePropertyResolver.invertRanges(set.ranges()), List.of(), null);
        }
        return set.sequences().isEmpty() ? set : normalizeExtendedClassSet(set);
    }

    private static ExtendedClassSet parseUnicodeSetsStringLiteralSet(CompileContext context) {
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
                    EscapedCodePoint unicodeEscapeParseResult = tryParseUnicodeEscapeAt(context.codePoints,
                            context.pos - 1, true);
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

            sequences.add(toIntArray(sequenceCodePoints));

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

    private static ExtendedClassSet readProperty(CompileContext context, boolean allowSequences) {
        if (!context.consume('{')) {
            throw new RegExpSyntaxException("expecting '{' after \\p");
        }
        String name = readPropertyIdentifier(context);
        String value = context.consume('=') ? readPropertyIdentifier(context) : null;
        if (!context.consume('}')) {
            throw new RegExpSyntaxException("expecting '}'");
        }
        int[] ranges = resolveUnicodePropertyRanges(context.properties, name, value);
        if (ranges != null) {
            return new ExtendedClassSet(ranges, List.of(), null);
        }
        if (allowSequences && value == null) {
            UnicodePropertyResolver.SequencePropertyResult result = UnicodePropertyResolver
                    .resolveSequenceProperty(name);
            if (result != null) {
                return new ExtendedClassSet(result.codePointRanges(), result.sequences(), null);
            }
        }
        throw new RegExpSyntaxException("unknown unicode property name");
    }

    private static String readPropertyIdentifier(CompileContext context) {
        int start = context.pos;
        while (context.pos < context.codePoints.length && isUnicodePropertyChar(context.codePoints[context.pos])) {
            context.pos++;
        }
        return new String(context.codePoints, start, context.pos - start);
    }

    private static int readRangeAtom(CompileContext context, UnicodeRangeBuilder ranges) {
        if (context.pos == context.codePoints.length) {
            throw new RegExpSyntaxException("Unterminated character class");
        }
        int ch = context.codePoints[context.pos++];
        if (ch != '\\') {
            return ch;
        }
        if (context.pos == context.codePoints.length) {
            throw new RegExpSyntaxException("Incomplete escape in character class");
        }
        return parseClassEscape(context, ranges);
    }

    private static ExtendedClassSet readUnicodeSet(CompileContext context) {
        context.pos++; // Skip '['

        boolean inverted = context.consume('^');

        ExtendedClassSet extendedClassSet = parseUnicodeSetsExpression(context);
        if (!context.consume(']')) {
            throw new RegExpSyntaxException("Unterminated character class");
        }

        if (inverted) {
            if (!extendedClassSet.sequences().isEmpty()) {
                throw new RegExpSyntaxException("Invalid UnicodeSets negated class");
            }
            extendedClassSet = new ExtendedClassSet(UnicodePropertyResolver.invertRanges(extendedClassSet.ranges()),
                    List.of(), null);
        }

        return extendedClassSet;
    }

    private static int[] resolveUnicodePropertyRanges(UnicodePropertyResolver unicodePropertyResolver,
            String propertyName, String propertyValue) {
        if (propertyValue != null) {
            if ("General_Category".equals(propertyName) || "gc".equals(propertyName)) {
                int[] ranges = unicodePropertyResolver.resolveGeneralCategory(propertyValue);
                if (ranges == null) {
                    throw new RegExpSyntaxException("unknown unicode general category");
                }
                return ranges;
            }
            if ("Script".equals(propertyName) || "sc".equals(propertyName)) {
                int[] ranges = unicodePropertyResolver.resolveScript(propertyValue, false);
                if (ranges == null) {
                    throw new RegExpSyntaxException("unknown unicode script");
                }
                return ranges;
            }
            if ("Script_Extensions".equals(propertyName) || "scx".equals(propertyName)) {
                int[] ranges = unicodePropertyResolver.resolveScript(propertyValue, true);
                if (ranges == null) {
                    throw new RegExpSyntaxException("unknown unicode script");
                }
                return ranges;
            }
            throw new RegExpSyntaxException("unknown unicode property name");
        }

        // Try General Category first (bare name like \p{Lu} or \p{Letter})
        int[] ranges = unicodePropertyResolver.resolveGeneralCategory(propertyName);
        if (ranges != null) {
            return ranges;
        }
        // Try binary property (like \p{Alphabetic} or \p{Alpha})
        ranges = unicodePropertyResolver.resolveBinaryProperty(propertyName);
        if (ranges != null) {
            return ranges;
        }
        return null;
    }

    private static String sequenceKey(int[] sequence) {
        StringBuilder keyBuilder = new StringBuilder(sequence.length * 8);
        for (int codePoint : sequence) {
            keyBuilder.append(codePoint).append(',');
        }
        return keyBuilder.toString();
    }

    private static ExtendedClassSet singleCodePointExtendedClassSet(int codePoint) {
        return new ExtendedClassSet(new int[]{codePoint, codePoint}, List.of(), codePoint);
    }

    private static ExtendedClassSet subtractExtendedClassSets(ExtendedClassSet left, ExtendedClassSet right) {
        int[] ranges = UnicodePropertyResolver.intersectRanges(left.ranges(),
                UnicodePropertyResolver.invertRanges(right.ranges()));
        List<int[]> sequences = filterSequences(left.sequences(), right.sequences(), false);
        return normalizeExtendedClassSet(new ExtendedClassSet(ranges, sequences, null));
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static ExtendedClassSet unionExtendedClassSets(ExtendedClassSet left, ExtendedClassSet right) {
        int[] ranges = UnicodePropertyResolver.unionRanges(left.ranges(), right.ranges());
        List<int[]> sequences = unionSequenceLists(left.sequences(), right.sequences());
        return normalizeExtendedClassSet(new ExtendedClassSet(ranges, sequences, null));
    }

    private static List<int[]> unionSequenceLists(List<int[]> left, List<int[]> right) {
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

    private record ExtendedClassSet(int[] ranges, List<int[]> sequences, Integer singleCodePoint) {
    }
}
