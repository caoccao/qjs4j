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

package com.caoccao.qjs4j.unicode;

/**
 * Unicode character properties and case conversion.
 * Based on QuickJS libunicode.c implementation.
 */
public final class CharacterProperties {

    /**
     * Convert code point to uppercase.
     * Returns the uppercase variant or the same codepoint if no mapping exists.
     */
    public static int toUpperCase(int codePoint) {
        return Character.toUpperCase(codePoint);
    }

    /**
     * Convert code point to lowercase.
     * Returns the lowercase variant or the same codepoint if no mapping exists.
     */
    public static int toLowerCase(int codePoint) {
        return Character.toLowerCase(codePoint);
    }

    /**
     * Convert code point using case folding.
     * Case folding is used for case-insensitive matching.
     * For most characters, it's the same as toLowerCase, but there are exceptions.
     */
    public static int caseFold(int codePoint) {
        // Case folding special cases
        // For most characters, case folding equals toLowerCase
        // But there are some special mappings for full Unicode case folding

        // Common case folding: use lowercase
        int lower = Character.toLowerCase(codePoint);

        // Special cases for case folding
        switch (codePoint) {
            // Turkish I with dot
            case 0x0130: // İ (LATIN CAPITAL LETTER I WITH DOT ABOVE)
                return 0x0069; // i (LATIN SMALL LETTER I)

            // Greek sigma at end of word
            case 0x03A3: // Σ (GREEK CAPITAL LETTER SIGMA)
                return 0x03C3; // σ (GREEK SMALL LETTER SIGMA)

            // Other special cases can be added as needed
            default:
                return lower;
        }
    }

    /**
     * Get the Unicode general category of a code point.
     * Returns a two-letter string like "Lu", "Ll", "Nd", etc.
     */
    public static String getCategory(int codePoint) {
        int type = Character.getType(codePoint);

        return switch (type) {
            case Character.UPPERCASE_LETTER -> "Lu";
            case Character.LOWERCASE_LETTER -> "Ll";
            case Character.TITLECASE_LETTER -> "Lt";
            case Character.MODIFIER_LETTER -> "Lm";
            case Character.OTHER_LETTER -> "Lo";
            case Character.NON_SPACING_MARK -> "Mn";
            case Character.ENCLOSING_MARK -> "Me";
            case Character.COMBINING_SPACING_MARK -> "Mc";
            case Character.DECIMAL_DIGIT_NUMBER -> "Nd";
            case Character.LETTER_NUMBER -> "Nl";
            case Character.OTHER_NUMBER -> "No";
            case Character.SPACE_SEPARATOR -> "Zs";
            case Character.LINE_SEPARATOR -> "Zl";
            case Character.PARAGRAPH_SEPARATOR -> "Zp";
            case Character.CONTROL -> "Cc";
            case Character.FORMAT -> "Cf";
            case Character.PRIVATE_USE -> "Co";
            case Character.SURROGATE -> "Cs";
            case Character.DASH_PUNCTUATION -> "Pd";
            case Character.START_PUNCTUATION -> "Ps";
            case Character.END_PUNCTUATION -> "Pe";
            case Character.CONNECTOR_PUNCTUATION -> "Pc";
            case Character.OTHER_PUNCTUATION -> "Po";
            case Character.MATH_SYMBOL -> "Sm";
            case Character.CURRENCY_SYMBOL -> "Sc";
            case Character.MODIFIER_SYMBOL -> "Sk";
            case Character.OTHER_SYMBOL -> "So";
            case Character.INITIAL_QUOTE_PUNCTUATION -> "Pi";
            case Character.FINAL_QUOTE_PUNCTUATION -> "Pf";
            default -> "Cn"; // Unassigned
        };
    }

    /**
     * Check if character is cased (has uppercase or lowercase variants).
     */
    public static boolean isCased(int codePoint) {
        return Character.isUpperCase(codePoint) ||
                Character.isLowerCase(codePoint) ||
                Character.isTitleCase(codePoint);
    }

    /**
     * Check if character is case ignorable.
     * Case ignorable characters don't affect case-insensitive matching.
     */
    public static boolean isCaseIgnorable(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK ||
                type == Character.ENCLOSING_MARK ||
                type == Character.FORMAT ||
                codePoint == 0x0027 || // APOSTROPHE
                codePoint == 0x002E;   // FULL STOP
    }

    /**
     * Convert codepoint to string representation for display.
     */
    public static String toString(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    /**
     * Get hex representation of codepoint.
     */
    public static String toHexString(int codePoint) {
        return String.format("U+%04X", codePoint);
    }
}
