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

import static com.caoccao.qjs4j.regexp.RegExpEscapes.*;

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
            context.emit(RegExpOpcode.PREV);
        }
        if (context.isUnicodeSetsMode()) {
            emitClassSet(context, RegExpUnicodeSet.read(context));
        } else {
            compileRangeClass(context);
        }
        if (isBackwardDirection) {
            context.emit(RegExpOpcode.PREV);
        }
    }

    private static void compileRangeClass(CompileContext context) {
        context.pos++;
        boolean inverted = context.consume('^');
        UnicodeRangeBuilder ranges = new UnicodeRangeBuilder();
        while (!context.consume(']')) {
            int start = readRangeAtom(context, ranges);
            boolean hasRange = context.pos + 1 < context.codePoints.length && context.peek() == '-'
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
        RegExpUnicodeSet property = readProperty(context, context.isUnicodeSetsMode() && !isInverted);
        if (!property.sequences().isEmpty()) {
            emitClassSet(context, property);
            return;
        }
        int[] propertyRanges = property.ranges();
        // Regular property resolved successfully
        if (isBackwardDirection) {
            context.emit(RegExpOpcode.PREV);
        }
        boolean emitInvertedRanges = isInverted;
        if (isInverted && context.isUnicodeMode() && context.isIgnoreCase()) {
            propertyRanges = UnicodePropertyResolver.invertRanges(propertyRanges);
            emitInvertedRanges = false;
        }
        emitRanges(context, propertyRanges, emitInvertedRanges);
        if (isBackwardDirection) {
            context.emit(RegExpOpcode.PREV);
        }
    }

    private static void emitClassSet(CompileContext context, RegExpUnicodeSet extendedClassSet) {
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
        context.emit(inverted
                ? (context.isIgnoreCase() ? RegExpOpcode.NOT_RANGE_I : RegExpOpcode.NOT_RANGE)
                : (context.isIgnoreCase() ? RegExpOpcode.RANGE_I : RegExpOpcode.RANGE));

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
            emitRanges(context, ranges, false);
            return;
        }
        List<int[]> sorted = new ArrayList<>(sequences);
        sorted.sort((a, b) -> Integer.compare(b.length, a.length));
        boolean empty = sorted.get(sorted.size() - 1).length == 0;
        List<Integer> exits = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            int[] sequence = sorted.get(i);
            if (sequence.length == 0) {
                break;
            }
            boolean last = i + 1 == sorted.size() && ranges.length == 0;
            int next = last ? -1 : context.emitForwardJump(RegExpOpcode.SPLIT_NEXT_FIRST);
            for (int codePoint : sequence) {
                compileLiteralChar(context, codePoint);
            }
            if (!last) {
                exits.add(context.emitForwardJump(RegExpOpcode.GOTO));
                context.patchJumpToHere(next);
            }
        }
        if (ranges.length > 0) {
            int next = empty ? context.emitForwardJump(RegExpOpcode.SPLIT_NEXT_FIRST) : -1;
            emitRanges(context, ranges, false);
            if (empty) {
                context.patchJumpToHere(next);
            }
        }
        for (int exit : exits) {
            context.patchJumpToHere(exit);
        }
    }

    private static boolean isUnicodePropertyChar(int ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '_';
    }

    static int parseClassEscape(CompileContext context, UnicodeRangeBuilder ranges) {
        int ch = context.read();

        return switch (ch) {
            case 'b' -> '\b'; // backspace (U+0008) inside character class
            case 'n', 'r', 't', 'f', 'v', '0', 'x', 'u' -> readCharacterEscape(context, ch);
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
                int code = readControlLetter(context, true);
                if (code < 0) {
                    ranges.add((int) '\\', (int) '\\');
                    yield 'c';
                }
                yield code;
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

    static RegExpUnicodeSet readProperty(CompileContext context, boolean allowSequences) {
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
            return new RegExpUnicodeSet(ranges, List.of(), null);
        }
        if (allowSequences && value == null) {
            UnicodePropertyResolver.SequencePropertyResult result = UnicodePropertyResolver
                    .resolveSequenceProperty(name);
            if (result != null) {
                return new RegExpUnicodeSet(result.codePointRanges(), result.sequences(), null);
            }
        }
        throw new RegExpSyntaxException("unknown unicode property name");
    }

    private static String readPropertyIdentifier(CompileContext context) {
        int start = context.pos;
        while (context.hasMore() && isUnicodePropertyChar(context.peek())) {
            context.pos++;
        }
        return new String(context.codePoints, start, context.pos - start);
    }

    private static int readRangeAtom(CompileContext context, UnicodeRangeBuilder ranges) {
        if (context.pos == context.codePoints.length) {
            throw new RegExpSyntaxException("Unterminated character class");
        }
        int ch = context.read();
        if (ch != '\\') {
            return ch;
        }
        if (context.pos == context.codePoints.length) {
            throw new RegExpSyntaxException("Incomplete escape in character class");
        }
        return parseClassEscape(context, ranges);
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

    static CharacterClass whitespace(boolean inverted) {
        // CharacterClass exposes its ranges through the Java API.
        return new CharacterClass(inverted, JS_WHITESPACE_RANGES.clone());
    }
}
