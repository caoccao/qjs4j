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

import java.util.Locale;

/** ECMAScript string case conversion, including contextual mappings and characters newer than Java 17. */
public final class UnicodeCaseMapping {
    private UnicodeCaseMapping() {
    }

    static int codePointToLower16(int codePoint) {
        return switch (codePoint) {
            case 0xA7DC -> 0x019B; // LATIN CAPITAL LETTER LAMBDA WITH STROKE -> small (Unicode 16.0)
            case 0xA7CB -> 0x0264; // LATIN CAPITAL LETTER RAMS HORN -> small (Unicode 15.0)
            case 0x1C89 -> 0x1C8A; // CYRILLIC CAPITAL LETTER TJE -> small (Unicode 15.0)
            case 0xA7CC -> 0xA7CD; // LATIN CAPITAL LETTER S WITH DIAGONAL STROKE -> small (Unicode 15.0)
            case 0xA7D0 -> 0xA7D1; // LATIN CAPITAL LETTER CLOSED INSULAR G -> small (Unicode 15.0)
            case 0xA7D6 -> 0xA7D7; // LATIN CAPITAL LETTER MIDDLE SCOTS S -> small (Unicode 15.0)
            case 0xA7D8 -> 0xA7D9; // LATIN CAPITAL LETTER SIGMOID S -> small (Unicode 15.0)
            case 0xA7DA -> 0xA7DB; // LATIN CAPITAL LETTER LAMBDA -> small (Unicode 15.0)
            case 0x2C2F -> 0x2C5F; // GLAGOLITIC CAPITAL LETTER CAUDATE CHRIVI -> small (Unicode 14.0)
            case 0xA7C0 -> 0xA7C1; // LATIN CAPITAL LETTER OLD POLISH O -> small (Unicode 14.0)
            case 0xA7CE -> 0xA7CF; // LATIN CAPITAL LETTER PHARYNGEAL VOICED FRICATIVE -> small (Unicode 15.0)
            case 0xA7D2 -> 0xA7D3; // LATIN CAPITAL LETTER DOUBLE THORN -> small (Unicode 15.0)
            case 0xA7D4 -> 0xA7D5; // LATIN CAPITAL LETTER HALF H -> small (Unicode 15.0)
            default -> {
                // Vithkuqi capital -> small (Unicode 15.0, offset 0x27)
                if (codePoint >= 0x10570 && codePoint <= 0x10595 && codePoint != 0x1057B && codePoint != 0x1058B) {
                    yield codePoint + 0x27;
                }
                // Garay uppercase -> lowercase (Unicode 16.0, offset 0x20)
                if (codePoint >= 0x10D50 && codePoint <= 0x10D65) {
                    yield codePoint + 0x20;
                }
                yield codePoint;
            }
        };
    }

    private static int codePointToUpper16(int codePoint) {
        return switch (codePoint) {
            case 0x019B -> 0xA7DC; // LATIN SMALL LETTER LAMBDA WITH STROKE -> CAPITAL (Unicode 16.0)
            case 0x0264 -> 0xA7CB; // LATIN SMALL LETTER RAMS HORN -> CAPITAL (Unicode 15.0)
            case 0x1C8A -> 0x1C89; // CYRILLIC SMALL LETTER TJE -> CAPITAL (Unicode 15.0)
            case 0xA7CD -> 0xA7CC; // LATIN SMALL LETTER S WITH DIAGONAL STROKE -> CAPITAL (Unicode 15.0)
            case 0xA7D1 -> 0xA7D0; // LATIN SMALL LETTER CLOSED INSULAR G -> CAPITAL (Unicode 15.0)
            case 0xA7D7 -> 0xA7D6; // LATIN SMALL LETTER MIDDLE SCOTS S -> CAPITAL (Unicode 15.0)
            case 0xA7D9 -> 0xA7D8; // LATIN SMALL LETTER SIGMOID S -> CAPITAL (Unicode 15.0)
            case 0xA7DB -> 0xA7DA; // LATIN SMALL LETTER LAMBDA -> CAPITAL (Unicode 15.0)
            case 0x2C5F -> 0x2C2F; // GLAGOLITIC SMALL LETTER CAUDATE CHRIVI -> CAPITAL (Unicode 14.0)
            case 0xA7C1 -> 0xA7C0; // LATIN SMALL LETTER OLD POLISH O -> CAPITAL (Unicode 14.0)
            case 0xA7CF -> 0xA7CE; // LATIN SMALL LETTER PHARYNGEAL VOICED FRICATIVE -> CAPITAL (Unicode 15.0)
            case 0xA7D3 -> 0xA7D2; // LATIN SMALL LETTER DOUBLE THORN -> CAPITAL (Unicode 15.0)
            case 0xA7D5 -> 0xA7D4; // LATIN SMALL LETTER HALF H -> CAPITAL (Unicode 15.0)
            default -> {
                // Vithkuqi small -> capital (Unicode 15.0, offset 0x27)
                if (codePoint >= 0x10597 && codePoint <= 0x105BC && codePoint != 0x105A2 && codePoint != 0x105B2) {
                    yield codePoint - 0x27;
                }
                // Garay lowercase -> uppercase (Unicode 16.0, offset 0x20)
                if (codePoint >= 0x10D70 && codePoint <= 0x10D85) {
                    yield codePoint - 0x20;
                }
                yield codePoint;
            }
        };
    }

    /**
     * Check if a code point is "Cased" per Unicode property. Uses the full Unicode Cased property table.
     */
    private static boolean isCasedUnicode(UnicodePropertyResolver resolver, int codePoint) {
        int[] ranges = resolver.resolveBinaryProperty("Cased");
        if (ranges == null) {
            return Character.isUpperCase(codePoint) || Character.isLowerCase(codePoint)
                    || Character.isTitleCase(codePoint);
        }
        return UnicodeData.isCodePointInRanges(ranges, codePoint);
    }

    /**
     * Check if a code point is "Case_Ignorable" per Unicode property. Uses the full Unicode Case_Ignorable property
     * table.
     */
    private static boolean isCaseIgnorableUnicode(UnicodePropertyResolver resolver, int codePoint) {
        int type = Character.getType(codePoint);
        boolean fallbackCaseIgnorable = type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT || type == Character.MODIFIER_LETTER || type == Character.MODIFIER_SYMBOL;
        int[] ranges = resolver.resolveBinaryProperty("Case_Ignorable");
        if (ranges == null) {
            return fallbackCaseIgnorable;
        }
        return UnicodeData.isCodePointInRanges(ranges, codePoint) || fallbackCaseIgnorable;
    }

    /**
     * Check if a code point is a combining mark.
     */
    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    /**
     * Test if sigma at position sigmaPos is in a "final" context. Final sigma: preceded by a cased letter (skipping
     * case-ignorable) and NOT followed by a cased letter (skipping case-ignorable).
     */
    private static boolean isFinalSigma(UnicodePropertyResolver resolver, String s, int sigmaPos) {
        // Look backward: skip case-ignorable, check for cased
        int k = sigmaPos;
        int prevCodePoint = -1;
        while (k > 0) {
            int cp = Character.codePointBefore(s, k);
            k -= Character.charCount(cp);
            if (!isCaseIgnorableUnicode(resolver, cp)) {
                prevCodePoint = cp;
                break;
            }
        }
        if (prevCodePoint == -1 || !isCasedUnicode(resolver, prevCodePoint)) {
            return false;
        }

        // Look forward: skip case-ignorable, check for NOT cased
        k = sigmaPos + 1;
        while (k < s.length()) {
            int cp = s.codePointAt(k);
            k += Character.charCount(cp);
            if (!isCaseIgnorableUnicode(resolver, cp)) {
                return !isCasedUnicode(resolver, cp);
            }
        }
        return true; // End of string, no following cased letter
    }

    /**
     * Check if a code point has the Unicode Soft_Dotted property.
     */
    private static boolean isSoftDottedUnicode(UnicodePropertyResolver resolver, int codePoint) {
        int[] ranges = resolver.resolveBinaryProperty("Soft_Dotted");
        if (ranges == null) {
            return false;
        }
        return UnicodeData.isCodePointInRanges(ranges, codePoint);
    }

    /**
     * Custom toLowerCase that handles the Greek final sigma rule. When U+03A3 (SIGMA) is at a "final" position
     * (preceded by a cased letter with possible case-ignorable characters in between, and NOT followed by a cased
     * letter), it maps to U+03C2 (final sigma) instead of U+03C3.
     */
    public static String lower(UnicodePropertyResolver resolver, String s, Locale locale) {
        String language = locale.getLanguage();
        if ("lt".equals(language) || "tr".equals(language) || "az".equals(language)) {
            return s.toLowerCase(locale);
        }
        boolean needsCustom = false;
        for (int i = 0; i < s.length();) {
            int codePoint = s.codePointAt(i);
            if (codePoint == 0x03A3 || codePointToLower16(codePoint) != codePoint) {
                needsCustom = true;
                break;
            }
            i += Character.charCount(codePoint);
        }
        if (!needsCustom) {
            return s.toLowerCase(Locale.ROOT);
        }

        StringBuilder result = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int codePoint = s.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (codePoint == 0x03A3) {
                // Check final sigma condition
                if (isFinalSigma(resolver, s, i)) {
                    result.append('\u03C2'); // final sigma
                } else {
                    result.append('\u03C3'); // regular sigma
                }
            } else if (codePoint == 0x0130) {
                result.append("i\u0307");
            } else {
                int lower = Character.toLowerCase(codePoint);
                // Apply Unicode 16.0 additions
                int lower16 = codePointToLower16(lower);
                result.appendCodePoint(lower16);
            }
            i += charCount;
        }
        return result.toString();
    }

    /**
     * Lithuanian uppercasing removes U+0307 when it follows a Soft_Dotted code point with only combining marks in
     * between.
     */
    private static String removeLithuanianSoftDottedDots(UnicodePropertyResolver resolver, String input) {
        StringBuilder result = new StringBuilder(input.length());
        int index = 0;
        while (index < input.length()) {
            int codePoint = input.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (codePoint == 0x0307) {
                int lookBackIndex = result.length();
                int previousCodePoint = -1;
                while (lookBackIndex > 0) {
                    previousCodePoint = Character.codePointBefore(result, lookBackIndex);
                    lookBackIndex -= Character.charCount(previousCodePoint);
                    if (!isCombiningMark(previousCodePoint)) {
                        break;
                    }
                }
                if (previousCodePoint != -1 && isSoftDottedUnicode(resolver, previousCodePoint)) {
                    index += charCount;
                    continue;
                }
            }
            result.appendCodePoint(codePoint);
            index += charCount;
        }
        return result.toString();
    }

    /**
     * toUpperCase with Unicode 16.0 case mappings that Java 17 (Unicode 14.0) lacks.
     */
    public static String upper(UnicodePropertyResolver resolver, String s, Locale locale) {
        if ("lt".equals(locale.getLanguage())) {
            s = removeLithuanianSoftDottedDots(resolver, s);
        }
        String result = s.toUpperCase(locale);
        // Apply Unicode 16.0 additions
        boolean needsPatch = false;
        for (int i = 0; i < result.length();) {
            int codePoint = result.codePointAt(i);
            if (codePointToUpper16(codePoint) != codePoint) {
                needsPatch = true;
                break;
            }
            i += Character.charCount(codePoint);
        }
        if (!needsPatch) {
            return result;
        }
        StringBuilder sb = new StringBuilder(result.length());
        for (int i = 0; i < result.length();) {
            int codePoint = result.codePointAt(i);
            sb.appendCodePoint(codePointToUpper16(codePoint));
            i += Character.charCount(codePoint);
        }
        return sb.toString();
    }
}
