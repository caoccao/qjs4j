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
     * Convert code point using case folding.
     * Case folding is used for case-insensitive matching.
     * For most characters, it's the same as toLowerCase, but there are exceptions.
     */
    public static int caseFold(int codePoint) {
        // Unicode CaseFolding.txt "C" (Common) and special entries.
        // For most characters, case folding equals toLowerCase,
        // but these entries differ from Java's Character.toLowerCase().
        return switch (codePoint) {
            case 0x00B5 -> 0x03BC; // MICRO SIGN → GREEK SMALL LETTER MU
            case 0x0130 -> 0x0069; // LATIN CAPITAL LETTER I WITH DOT ABOVE → i
            case 0x017F -> 0x0073; // LATIN SMALL LETTER LONG S → s
            case 0x0345 -> 0x03B9; // COMBINING GREEK YPOGEGRAMMENI → GREEK SMALL LETTER IOTA
            case 0x0390, 0x1FD3 -> 0x0390; // GREEK SMALL LETTER IOTA WITH DIALYTIKA AND TONOS
            case 0x03A3 -> 0x03C3; // GREEK CAPITAL LETTER SIGMA → small sigma
            case 0x03B0, 0x1FE3 -> 0x03B0; // GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND TONOS
            case 0x03C2 -> 0x03C3; // GREEK SMALL LETTER FINAL SIGMA → small sigma
            case 0x03D0 -> 0x03B2; // GREEK BETA SYMBOL → small beta
            case 0x03D1 -> 0x03B8; // GREEK THETA SYMBOL → small theta
            case 0x03D5 -> 0x03C6; // GREEK PHI SYMBOL → small phi
            case 0x03D6 -> 0x03C0; // GREEK PI SYMBOL → small pi
            case 0x03F0 -> 0x03BA; // GREEK KAPPA SYMBOL → small kappa
            case 0x03F1 -> 0x03C1; // GREEK RHO SYMBOL → small rho
            case 0x03F5 -> 0x03B5; // GREEK LUNATE EPSILON SYMBOL → small epsilon
            case 0x1C80 -> 0x0432; // CYRILLIC SMALL LETTER ROUNDED VE → VE
            case 0x1C81 -> 0x0434; // CYRILLIC SMALL LETTER LONG-LEGGED DE → DE
            case 0x1C82 -> 0x043E; // CYRILLIC SMALL LETTER NARROW O → O
            case 0x1C83 -> 0x0441; // CYRILLIC SMALL LETTER WIDE ES → ES
            case 0x1C84, 0x1C85 -> 0x0442; // CYRILLIC SMALL LETTER TALL/THREE-LEGGED TE → TE
            case 0x1C86 -> 0x044A; // CYRILLIC SMALL LETTER TALL HARD SIGN → HARD SIGN
            case 0x1C87 -> 0x0463; // CYRILLIC SMALL LETTER TALL YAT → YAT
            case 0x1C88 -> 0xA64B; // CYRILLIC SMALL LETTER UNBLENDED UK → UK
            case 0x1C89 -> 0x1C8A; // CYRILLIC CAPITAL LETTER TJE → small (Unicode 15.0)
            case 0x1E9B -> 0x1E61; // LATIN SMALL LETTER LONG S WITH DOT ABOVE → S WITH DOT ABOVE
            case 0x1E9E -> 0x00DF; // LATIN CAPITAL LETTER SHARP S → sharp s
            case 0x1FBE -> 0x03B9; // GREEK PROSGEGRAMMENI → GREEK SMALL LETTER IOTA
            case 0xA7CB -> 0x0264; // LATIN CAPITAL LETTER RAMS HORN → small (Unicode 15.0)
            case 0xA7CC -> 0xA7CD; // LATIN CAPITAL LETTER S WITH DIAGONAL STROKE → small (Unicode 15.0)
            case 0xA7D0 -> 0xA7D1; // LATIN CAPITAL LETTER CLOSED INSULAR G → small (Unicode 15.0)
            case 0xA7D6 -> 0xA7D7; // LATIN CAPITAL LETTER MIDDLE SCOTS S → small (Unicode 15.0)
            case 0xA7D8 -> 0xA7D9; // LATIN CAPITAL LETTER SIGMOID S → small (Unicode 15.0)
            case 0xA7DA -> 0xA7DB; // LATIN CAPITAL LETTER LAMBDA → small (Unicode 15.0)
            case 0xA7DC -> 0x019B; // LATIN CAPITAL LETTER LAMBDA WITH STROKE → small (Unicode 16.0)
            case 0x212A -> 0x006B; // KELVIN SIGN → k
            case 0x212B -> 0x00E5; // ANGSTROM SIGN → a with ring
            case 0xFB05, 0xFB06 -> 0xFB06; // Latin ligatures long-s-t / st
            default -> {
                // Vithkuqi capital → small (Unicode 15.0, offset 0x27)
                if (codePoint >= 0x10570 && codePoint <= 0x10595
                        && codePoint != 0x1057B && codePoint != 0x1058B) {
                    yield codePoint + 0x27;
                }
                // Garay script uppercase → lowercase (Unicode 16.0)
                if (codePoint >= 0x10D50 && codePoint <= 0x10D65) {
                    yield codePoint + 0x20;
                }
                yield Character.toLowerCase(codePoint);
            }
        };
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
     * Check if character is cased (has uppercase or lowercase variants).
     */
    public static boolean isCased(int codePoint) {
        return Character.isUpperCase(codePoint) ||
                Character.isLowerCase(codePoint) ||
                Character.isTitleCase(codePoint);
    }

    /**
     * Get hex representation of codepoint.
     */
    public static String toHexString(int codePoint) {
        return String.format("U+%04X", codePoint);
    }

    /**
     * Convert code point to lowercase.
     * Returns the lowercase variant or the same codepoint if no mapping exists.
     */
    public static int toLowerCase(int codePoint) {
        return Character.toLowerCase(codePoint);
    }

    /**
     * Convert codepoint to string representation for display.
     */
    public static String toString(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    /**
     * Convert code point to uppercase.
     * Returns the uppercase variant or the same codepoint if no mapping exists.
     */
    public static int toUpperCase(int codePoint) {
        return Character.toUpperCase(codePoint);
    }
}
