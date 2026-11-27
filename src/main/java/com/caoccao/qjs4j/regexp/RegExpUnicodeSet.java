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

import java.util.*;
import com.caoccao.qjs4j.unicode.*;
import static com.caoccao.qjs4j.regexp.RegExpEscapes.*;
import static com.caoccao.qjs4j.regexp.RegExpCompiler.*;
import static com.caoccao.qjs4j.regexp.RegExpCharacterClass.*;

/** UnicodeSets operands, string disjunctions and set operations in v-mode character classes. */
record RegExpUnicodeSet(int[] ranges, List<int[]> sequences, Integer singleCodePoint) {
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

    private static RegExpUnicodeSet parseUnicodeSetsElement(CompileContext context) {
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
        return single(currentChar);
    }

    private static RegExpUnicodeSet parseUnicodeSetsEscapeElement(CompileContext context) {
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
            return new RegExpUnicodeSet(characterClassRanges(context, escapedChar), List.of(), null);
        }
        UnicodeRangeBuilder ranges = new UnicodeRangeBuilder();
        int parsedValue = parseClassEscape(context, ranges);
        if (parsedValue == -1) {
            return new RegExpUnicodeSet(UnicodeRangeBuilder.normalize(ranges.toArray()), List.of(), null);
        }
        return single(parsedValue);
    }

    /**
     * ClassSetExpression in {@code v} mode: exactly one of a union, an intersection ({@code &&}) or a subtraction
     * ({@code --}), whose operands are nested classes, escapes or single characters but not ranges. The operators used
     * to be accepted in any mix, as in {@code [a&&b--c]} and {@code [ab&&c]}, and {@code [a&&&b]} was accepted.
     */
    private static RegExpUnicodeSet parseUnicodeSetsExpression(CompileContext context) {
        if (isUnicodeSetsClassEnd(context)) {
            return new RegExpUnicodeSet(new int[0], List.of(), null);
        }
        // A class cannot start with an operator; V8 reports a leading "--" as two stray '-' characters.
        if (isUnicodeSetsOperatorAt(context, "&&")) {
            throw new RegExpSyntaxException("Invalid set operation in character class");
        }
        if (isUnicodeSetsOperatorAt(context, "--")) {
            throw new RegExpSyntaxException("Invalid character in character class");
        }
        context.lastUnicodeSetsOperandWasRange = false;
        RegExpUnicodeSet result = parseUnicodeSetsOperand(context);
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
                RegExpUnicodeSet right = parseUnicodeSetsOperand(context);
                if (context.lastUnicodeSetsOperandWasRange) {
                    throw new RegExpSyntaxException("Invalid set operation in character class");
                }
                result = "&&".equals(operator) ? result.filter(right, true) : result.filter(right, false);
            }
            if (!isUnicodeSetsClassEnd(context)) {
                throw new RegExpSyntaxException("Invalid set operation in character class");
            }
            return result.normalized();
        }
        if (isUnicodeSetsClassEnd(context)) {
            return result.normalized();
        }
        Union union = new Union();
        union.add(result);
        while (!isUnicodeSetsClassEnd(context)) {
            if (isUnicodeSetsOperatorAt(context, "&&") || isUnicodeSetsOperatorAt(context, "--")) {
                throw new RegExpSyntaxException("Invalid set operation in character class");
            }
            union.add(parseUnicodeSetsOperand(context));
        }
        return union.finish(null);
    }

    private static RegExpUnicodeSet parseUnicodeSetsOperand(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Unterminated character class");
        }
        if (context.codePoints[context.pos] == '[') {
            RegExpUnicodeSet nested = read(context);
            context.lastUnicodeSetsOperandWasRange = false;
            return nested;
        }
        RegExpUnicodeSet firstElement = parseUnicodeSetsElement(context);
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
            RegExpUnicodeSet rangeEndElement = parseUnicodeSetsElement(context);
            if (rangeEndElement.singleCodePoint() == null) {
                throw new RegExpSyntaxException("Invalid range in character class");
            }
            int rangeEnd = rangeEndElement.singleCodePoint();
            if (rangeStart > rangeEnd) {
                throw new RegExpSyntaxException("Range out of order in character class");
            }
            context.lastUnicodeSetsOperandWasRange = true;
            return new RegExpUnicodeSet(new int[]{rangeStart, rangeEnd}, List.of(), null);
        }
        return firstElement;
    }

    private static RegExpUnicodeSet parseUnicodeSetsPropertyEscapeSet(CompileContext context, boolean inverted) {
        RegExpUnicodeSet set = readProperty(context, !inverted);
        if (inverted) {
            return new RegExpUnicodeSet(UnicodePropertyResolver.invertRanges(set.ranges()), List.of(), null);
        }
        return set.sequences().isEmpty() ? set : set.normalized();
    }

    private static RegExpUnicodeSet parseUnicodeSetsStringLiteralSet(CompileContext context) {
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

        RegExpUnicodeSet result = new RegExpUnicodeSet(new int[0], sequences, null);
        return result.normalized();
    }

    static RegExpUnicodeSet read(CompileContext context) {
        context.pos++; // Skip '['

        boolean inverted = context.consume('^');

        RegExpUnicodeSet extendedClassSet = parseUnicodeSetsExpression(context);
        if (!context.consume(']')) {
            throw new RegExpSyntaxException("Unterminated character class");
        }

        if (inverted) {
            if (!extendedClassSet.sequences().isEmpty()) {
                throw new RegExpSyntaxException("Invalid UnicodeSets negated class");
            }
            extendedClassSet = new RegExpUnicodeSet(UnicodePropertyResolver.invertRanges(extendedClassSet.ranges()),
                    List.of(), null);
        }

        return extendedClassSet;
    }

    private static RegExpUnicodeSet single(int codePoint) {
        return new RegExpUnicodeSet(new int[]{codePoint, codePoint}, List.of(), codePoint);
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private RegExpUnicodeSet normalized() {
        if (sequences.isEmpty()) {
            return this; // Range-only operands are normalized at their source.
        }
        Union union = new Union();
        union.add(this);
        return union.finish(singleCodePoint);
    }

    private RegExpUnicodeSet filter(RegExpUnicodeSet right, boolean intersect) {
        int[] filtered = UnicodePropertyResolver.intersectRanges(ranges,
                intersect ? right.ranges : UnicodePropertyResolver.invertRanges(right.ranges));
        if (sequences.isEmpty() || right.sequences.isEmpty()) {
            return new RegExpUnicodeSet(filtered, intersect ? List.of() : sequences, null);
        }
        Set<Sequence> keys = new HashSet<>();
        for (int[] sequence : right.sequences) {
            keys.add(new Sequence(sequence));
        }
        List<int[]> kept = new ArrayList<>();
        for (int[] sequence : sequences) {
            if (keys.contains(new Sequence(sequence)) == intersect) {
                kept.add(sequence);
            }
        }
        return new RegExpUnicodeSet(filtered, kept, null);
    }

    private record Sequence(int[] points) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Sequence sequence && Arrays.equals(points, sequence.points);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(points);
        }
    }

    private static final class Union {
        private final UnicodeRangeBuilder ranges = new UnicodeRangeBuilder();
        private final Set<Sequence> seen = new HashSet<>();
        private final List<int[]> strings = new ArrayList<>();

        void add(RegExpUnicodeSet operand) {
            ranges.addAll(operand.ranges);
            for (int[] sequence : operand.sequences) {
                if (sequence.length == 1) {
                    ranges.add(sequence[0], sequence[0]);
                } else if (sequence.length > 1 && seen.add(new Sequence(sequence))) {
                    strings.add(sequence);
                }
            }
        }

        RegExpUnicodeSet finish(Integer single) {
            return new RegExpUnicodeSet(UnicodeRangeBuilder.normalize(ranges.toArray()), strings, single);
        }
    }
}
