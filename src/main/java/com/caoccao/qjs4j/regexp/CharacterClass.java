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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a character class in a regex.
 * Character classes match sets of characters (e.g., [a-z], \d, \w).
 * Based on QuickJS libregexp.c character range implementation.
 *
 * @param ranges pairs of [start, end] inclusive
 */
public record CharacterClass(boolean inverted, int[] ranges) {
    /**
     * Create a character class from ranges.
     *
     * @param inverted Whether the class is inverted (e.g., [^a-z])
     * @param ranges   Array of range pairs [start1, end1, start2, end2, ...]
     */
    public CharacterClass {
    }

    /**
     * Matches any character including line terminators (dotAll mode)
     */
    public static CharacterClass any() {
        return new CharacterClass(false, new int[]{0, 0x10FFFF});
    }

    /**
     * \d - Digit character class [0-9]
     */
    public static CharacterClass digit() {
        return new CharacterClass(false, new int[]{'0', '9'});
    }

    // Predefined character classes

    /**
     * . (dot) - Matches any character except line terminators
     */
    public static CharacterClass dot() {
        // Excludes: \n, \r, \u2028, \u2029
        return new CharacterClass(true, new int[]{
                0x000A, 0x000A,  // LF
                0x000D, 0x000D,  // CR
                0x2028, 0x2029   // Line separator, paragraph separator
        });
    }

    /**
     * Normalize and merge overlapping ranges.
     */
    private static int[] normalizeRanges(int[] ranges) {
        if (ranges.length == 0) {
            return ranges;
        }

        // Sort ranges by start position
        Integer[] pairs = new Integer[ranges.length / 2];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = i * 2;
        }
        Arrays.sort(pairs, (a, b) -> Integer.compare(ranges[a], ranges[b]));

        List<Integer> result = new ArrayList<>();
        int currentStart = ranges[pairs[0]];
        int currentEnd = ranges[pairs[0] + 1];

        for (int i = 1; i < pairs.length; i++) {
            int idx = pairs[i];
            int start = ranges[idx];
            int end = ranges[idx + 1];

            if (start <= currentEnd + 1) {
                // Merge overlapping or adjacent ranges
                currentEnd = Math.max(currentEnd, end);
            } else {
                // Add current range and start new one
                result.add(currentStart);
                result.add(currentEnd);
                currentStart = start;
                currentEnd = end;
            }
        }

        // Add final range
        result.add(currentStart);
        result.add(currentEnd);

        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * \D - Non-digit character class [^0-9]
     */
    public static CharacterClass notDigit() {
        return new CharacterClass(true, new int[]{'0', '9'});
    }

    /**
     * \S - Non-whitespace character class
     */
    public static CharacterClass notWhitespace() {
        return new CharacterClass(true, new int[]{
                0x0009, 0x000D,
                0x0020, 0x0020,
                0x00A0, 0x00A0,
                0x1680, 0x1680,
                0x2000, 0x200A,
                0x2028, 0x2029,
                0x202F, 0x202F,
                0x205F, 0x205F,
                0x3000, 0x3000,
                0xFEFF, 0xFEFF
        });
    }

    /**
     * \W - Non-word character class [^a-zA-Z0-9_]
     */
    public static CharacterClass notWord() {
        return new CharacterClass(true, new int[]{
                'a', 'z',
                'A', 'Z',
                '0', '9',
                '_', '_'
        });
    }

    /**
     * Create a character class from a range.
     */
    public static CharacterClass range(int start, int end) {
        if (start > end) {
            throw new IllegalArgumentException("Invalid range: " + start + "-" + end);
        }
        return new CharacterClass(false, new int[]{start, end});
    }

    /**
     * Create a character class from a single character.
     */
    public static CharacterClass single(int codePoint) {
        return new CharacterClass(false, new int[]{codePoint, codePoint});
    }

    /**
     * Merge multiple character classes.
     */
    public static CharacterClass union(CharacterClass... classes) {
        List<Integer> rangeList = new ArrayList<>();
        boolean anyInverted = false;

        for (CharacterClass cc : classes) {
            if (cc.inverted) {
                anyInverted = true;
                // Union with inverted classes is complex, not implemented yet
                throw new UnsupportedOperationException("Union with inverted classes not yet supported");
            }
            for (int r : cc.ranges) {
                rangeList.add(r);
            }
        }

        int[] merged = rangeList.stream().mapToInt(Integer::intValue).toArray();
        return new CharacterClass(false, normalizeRanges(merged));
    }

    /**
     * \s - Whitespace character class
     */
    public static CharacterClass whitespace() {
        // JavaScript whitespace: space, tab, CR, LF, FF, VT, and Unicode spaces
        return new CharacterClass(false, new int[]{
                0x0009, 0x000D,  // \t to \r
                0x0020, 0x0020,  // space
                0x00A0, 0x00A0,  // non-breaking space
                0x1680, 0x1680,  // Ogham space mark
                0x2000, 0x200A,  // various spaces
                0x2028, 0x2029,  // line separator, paragraph separator
                0x202F, 0x202F,  // narrow no-break space
                0x205F, 0x205F,  // medium mathematical space
                0x3000, 0x3000,  // ideographic space
                0xFEFF, 0xFEFF   // zero width no-break space
        });
    }

    /**
     * \w - Word character class [a-zA-Z0-9_]
     */
    public static CharacterClass word() {
        return new CharacterClass(false, new int[]{
                'a', 'z',
                'A', 'Z',
                '0', '9',
                '_', '_'
        });
    }

    /**
     * Check if a code point matches this character class.
     */
    public boolean matches(int codePoint) {
        boolean inRange = false;

        // Check if code point is in any of the ranges
        for (int i = 0; i < ranges.length; i += 2) {
            int start = ranges[i];
            int end = ranges[i + 1];
            if (codePoint >= start && codePoint <= end) {
                inRange = true;
                break;
            }
        }

        // Apply inversion if needed
        return inverted != inRange;
    }

    /**
     * Check if a code point matches this character class (case insensitive).
     */
    public boolean matchesIgnoreCase(int codePoint) {
        // Try the original code point
        if (matches(codePoint)) {
            return true;
        }

        // Try uppercase variant
        int upper = Character.toUpperCase(codePoint);
        if (upper != codePoint && matches(upper)) {
            return true;
        }

        // Try lowercase variant
        int lower = Character.toLowerCase(codePoint);
        return lower != codePoint && matches(lower);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(inverted ? "[^" : "[");
        for (int i = 0; i < ranges.length; i += 2) {
            if (i > 0) { sb.append(", "); }
            int start = ranges[i];
            int end = ranges[i + 1];
            if (start == end) {
                sb.append(String.format("U+%04X", start));
            } else {
                sb.append(String.format("U+%04X-U+%04X", start, end));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
