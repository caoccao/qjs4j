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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.caoccao.qjs4j.unicode.UnicodePropertyTables.*;

/**
 * Resolves Unicode property names to code point ranges.
 * Ported from QuickJS libunicode.c.
 */
public final class UnicodePropertyResolver {

    private static final Map<String, int[]> GC_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, int[]> PROPERTY_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, int[]> SCRIPT_CACHE = new ConcurrentHashMap<>();
    private static final int SCRIPT_COMMON = 26;
    private static final int SCRIPT_INHERITED = 59;
    // Script index constants
    private static final int SCRIPT_UNKNOWN = 0;

    private UnicodePropertyResolver() {
    }

    /**
     * Build ranges of code points that change under case operations.
     * Ported from QuickJS unicode_case1().
     *
     * @param caseMask bitmask of CASE_U, CASE_L, CASE_F
     */
    private static int[] buildCaseChangeRanges(int caseMask) {
        if (caseMask == 0) {
            return new int[0];
        }

        // Run type masks for each case operation (from QuickJS)
        // CASE_U types: U, UF, UL, LSU, U2L_399_EXT2, UF_D20, UF_D1_EXT, U_EXT, UF_EXT2, UF_EXT3
        int upperMask = (1 << 0) | (1 << 2) | (1 << 4) | (1 << 5) | (1 << 6) |
                (1 << 7) | (1 << 8) | (1 << 9) | (1 << 11) | (1 << 13);
        // CASE_L types: L, LF, UL, LSU, U2L_399_EXT2, LF_EXT, LF_EXT2
        int lowerMask = (1 << 1) | (1 << 3) | (1 << 4) | (1 << 5) | (1 << 6) |
                (1 << 10) | (1 << 12);
        // CASE_F types: UF, LF, UL, LSU, U2L_399_EXT2, LF_EXT, LF_EXT2, UF_D20, UF_D1_EXT, UF_EXT2, UF_EXT3
        int foldMask = (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5) | (1 << 6) |
                (1 << 10) | (1 << 12) | (1 << 7) | (1 << 8) | (1 << 11) | (1 << 13);

        int mask = 0;
        if ((caseMask & CASE_U) != 0) {
            mask |= upperMask;
        }
        if ((caseMask & CASE_L) != 0) {
            mask |= lowerMask;
        }
        if ((caseMask & CASE_F) != 0) {
            mask |= foldMask;
        }

        List<Integer> ranges = new ArrayList<>();
        for (int idx = 0; idx < CASE_CONV_TABLE1.length; idx++) {
            int v = CASE_CONV_TABLE1[idx];
            int type = (v >> 4) & 0xF;
            int code = v >>> 15;
            int len = (v >> 8) & 0x7F;

            if (((mask >> type) & 1) != 0) {
                if (type == RUN_TYPE_UL) {
                    if ((caseMask & CASE_U) != 0 && (caseMask & (CASE_L | CASE_F)) != 0) {
                        // Both upper and lower/fold: add entire range
                        ranges.add(code);
                        ranges.add(code + len - 1);
                    } else {
                        // Only upper or only lower/fold: add alternating code points
                        int start = code + (((caseMask & CASE_U) != 0) ? 1 : 0);
                        for (int i = start; i < code + len; i += 2) {
                            ranges.add(i);
                            ranges.add(i);
                        }
                    }
                } else if (type == RUN_TYPE_LSU) {
                    if ((caseMask & CASE_U) != 0 && (caseMask & (CASE_L | CASE_F)) != 0) {
                        // Both: add entire range
                        ranges.add(code);
                        ranges.add(code + len - 1);
                    } else {
                        if ((caseMask & CASE_U) == 0) {
                            ranges.add(code);
                            ranges.add(code);
                        }
                        ranges.add(code + 1);
                        ranges.add(code + 1);
                        if ((caseMask & CASE_U) != 0) {
                            ranges.add(code + 2);
                            ranges.add(code + 2);
                        }
                    }
                } else {
                    // Default: add entire range
                    ranges.add(code);
                    ranges.add(code + len - 1);
                }
            }
        }

        return sortAndMergeRanges(toIntArray(ranges));
    }

    // --- Derived properties ---

    /**
     * Decode a binary property table into ranges [start, end, start, end, ...]
     * where ranges are inclusive on both ends.
     * Ported from QuickJS unicode_prop1().
     */
    private static int[] decodeBinaryProperty(int propIndex) {
        if (propIndex < 0 || propIndex >= PROP_TABLES.length) {
            return new int[0];
        }
        byte[] table = PROP_TABLES[propIndex];
        List<Integer> ranges = new ArrayList<>();
        int pos = 0;
        int codePoint = 0;
        boolean bit = false;

        while (pos < table.length) {
            int start = codePoint;
            int b = table[pos++] & 0xFF;
            if (b < 64) {
                // Two packed 3-bit lengths
                codePoint += (b >> 3) + 1;
                if (bit) {
                    ranges.add(start);
                    ranges.add(codePoint - 1);
                }
                bit = !bit;
                start = codePoint;
                codePoint += (b & 7) + 1;
            } else if (b >= 0x80) {
                codePoint += (b - 0x80) + 1;
            } else if (b < 0x60) {
                codePoint += (((b - 0x40) << 8) | (table[pos++] & 0xFF)) + 1;
            } else {
                codePoint += (((b - 0x60) << 16) | ((table[pos++] & 0xFF) << 8) | (table[pos++] & 0xFF)) + 1;
            }
            if (bit) {
                ranges.add(start);
                ranges.add(codePoint - 1);
            }
            bit = !bit;
        }

        return toIntArray(ranges);
    }

    /**
     * Decode the General Category table for a given GC bitmask.
     * Returns ranges [start, end, start, end, ...] (inclusive).
     * Ported from QuickJS unicode_general_category1().
     */
    private static int[] decodeGeneralCategory(long gcMask) {
        byte[] table = GC_TABLE;
        List<Integer> ranges = new ArrayList<>();
        int pos = 0;
        int codePoint = 0;

        while (pos < table.length) {
            int b = table[pos++] & 0xFF;
            int n = b >> 5;
            int v = b & 0x1F;
            if (n == 7) {
                int extra = table[pos++] & 0xFF;
                if (extra < 128) {
                    n = extra + 7;
                } else if (extra < 128 + 64) {
                    n = ((extra - 128) << 8) | (table[pos++] & 0xFF);
                    n += 7 + 128;
                } else {
                    n = ((extra - 128 - 64) << 16) | ((table[pos++] & 0xFF) << 8) | (table[pos++] & 0xFF);
                    n += 7 + 128 + (1 << 14);
                }
            }
            int start = codePoint;
            codePoint += n + 1;
            if (v == 31) {
                // Run of alternating Lu/Ll
                long b2 = gcMask & ((1L << GC_LU) | (1L << GC_LL));
                if (b2 != 0) {
                    if (b2 == ((1L << GC_LU) | (1L << GC_LL))) {
                        ranges.add(start);
                        ranges.add(codePoint - 1);
                    } else {
                        int s = start + (((gcMask & (1L << GC_LL)) != 0) ? 1 : 0);
                        for (; s < codePoint; s += 2) {
                            ranges.add(s);
                            ranges.add(s);
                        }
                    }
                }
            } else if (((gcMask >> v) & 1) != 0) {
                ranges.add(start);
                ranges.add(codePoint - 1);
            }
        }

        return mergeAdjacentRanges(toIntArray(ranges));
    }

    // --- Table decoders ---

    /**
     * Decode script table for a given script index.
     * Ported from QuickJS unicode_script().
     */
    private static int[] decodeScript(int scriptIndex, boolean extensions) {
        int[] baseRanges = decodeScriptBase(scriptIndex);

        if (scriptIndex == SCRIPT_UNKNOWN) {
            baseRanges = invertRanges(baseRanges);
        }

        if (!extensions) {
            return baseRanges;
        }

        boolean isCommon = (scriptIndex == SCRIPT_COMMON || scriptIndex == SCRIPT_INHERITED);
        int[] extRanges = decodeScriptExtensions(scriptIndex, isCommon);

        if (isCommon) {
            // For Common/Inherited: remove code points that have script extensions
            int[] invertedExt = invertRanges(extRanges);
            return intersectRanges(baseRanges, invertedExt);
        } else {
            // For other scripts: union base with extensions
            return unionRanges(baseRanges, extRanges);
        }
    }

    /**
     * Decode the main script table to find code points matching a script index.
     * Format: high bit = type (has script or not), lower 7 bits = variable-length count.
     * If type=1, next byte is the script ID.
     */
    private static int[] decodeScriptBase(int scriptIndex) {
        byte[] table = SCRIPT_TABLE;
        List<Integer> ranges = new ArrayList<>();
        int pos = 0;
        int codePoint = 0;

        while (pos < table.length) {
            int b = table[pos++] & 0xFF;
            int type = b >> 7;
            int n = b & 0x7F;
            if (n >= 96 && n < 112) {
                n = ((n - 96) << 8) | (table[pos++] & 0xFF);
                n += 96;
            } else if (n >= 112) {
                n = ((n - 112) << 16) | ((table[pos++] & 0xFF) << 8) | (table[pos++] & 0xFF);
                n += 96 + (1 << 12);
            }
            int nextCodePoint = codePoint + n + 1;
            if (type != 0) {
                int scriptId = table[pos++] & 0xFF;
                if (scriptId == scriptIndex || scriptIndex == SCRIPT_UNKNOWN) {
                    ranges.add(codePoint);
                    ranges.add(nextCodePoint - 1);
                }
            }
            codePoint = nextCodePoint;
        }

        return mergeAdjacentRanges(toIntArray(ranges));
    }

    /**
     * Decode the script extensions table.
     * Format: variable-length count, then v_len byte (number of extension IDs),
     * then v_len script ID bytes.
     * For Common/Inherited: collects code points that have ANY extensions.
     * For other scripts: collects code points where the script appears in extensions.
     */
    private static int[] decodeScriptExtensions(int scriptIndex, boolean isCommon) {
        byte[] table = SCRIPT_EXT_TABLE;
        List<Integer> ranges = new ArrayList<>();
        int pos = 0;
        int codePoint = 0;

        while (pos < table.length) {
            int b = table[pos++] & 0xFF;
            int n;
            if (b < 128) {
                n = b;
            } else if (b < 128 + 64) {
                n = ((b - 128) << 8) | (table[pos++] & 0xFF);
                n += 128;
            } else {
                n = ((b - 128 - 64) << 16) | ((table[pos++] & 0xFF) << 8) | (table[pos++] & 0xFF);
                n += 128 + (1 << 14);
            }
            int nextCodePoint = codePoint + n + 1;
            int extCount = table[pos++] & 0xFF;
            if (isCommon) {
                if (extCount != 0) {
                    ranges.add(codePoint);
                    ranges.add(nextCodePoint - 1);
                }
            } else {
                for (int i = 0; i < extCount; i++) {
                    if ((table[pos + i] & 0xFF) == scriptIndex) {
                        ranges.add(codePoint);
                        ranges.add(nextCodePoint - 1);
                        break;
                    }
                }
            }
            pos += extCount;
            codePoint = nextCodePoint;
        }

        return mergeAdjacentRanges(toIntArray(ranges));
    }

    /**
     * Find a name in a name table. Each entry in the table contains comma-separated aliases.
     * Returns the index of the matching entry, or -1 if not found.
     */
    public static int findName(String[] nameTable, String name) {
        for (int i = 0; i < nameTable.length; i++) {
            String entry = nameTable[i];
            int start = 0;
            while (start < entry.length()) {
                int comma = entry.indexOf(',', start);
                String alias;
                if (comma < 0) {
                    alias = entry.substring(start);
                    start = entry.length();
                } else {
                    alias = entry.substring(start, comma);
                    start = comma + 1;
                }
                if (alias.equals(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Get composite GC bitmask for multi-category groups.
     */
    private static long getCompositeGcMask(int gcIndex) {
        return switch (gcIndex) {
            case 30 -> // LC = Cased_Letter = Lu|Ll|Lt
                    (1L << GC_LU) | (1L << GC_LL) | (1L << GC_LT);
            case 31 -> // L = Letter = Lu|Ll|Lt|Lm|Lo
                    (1L << GC_LU) | (1L << GC_LL) | (1L << GC_LT) | (1L << GC_LM) | (1L << GC_LO);
            case 32 -> // M = Mark = Mn|Mc|Me
                    (1L << GC_MN) | (1L << GC_MC) | (1L << GC_ME);
            case 33 -> // N = Number = Nd|Nl|No
                    (1L << GC_ND) | (1L << GC_NL) | (1L << GC_NO);
            case 34 -> // S = Symbol = Sm|Sc|Sk|So
                    (1L << GC_SM) | (1L << GC_SC) | (1L << GC_SK) | (1L << GC_SO);
            case 35 -> // P = Punctuation = Pc|Pd|Ps|Pe|Pi|Pf|Po
                    (1L << GC_PC) | (1L << GC_PD) | (1L << GC_PS) | (1L << GC_PE) |
                            (1L << GC_PI) | (1L << GC_PF) | (1L << GC_PO);
            case 36 -> // Z = Separator = Zs|Zl|Zp
                    (1L << GC_ZS) | (1L << GC_ZL) | (1L << GC_ZP);
            case 37 -> // C = Other = Cc|Cf|Cs|Co|Cn
                    (1L << GC_CC) | (1L << GC_CF) | (1L << GC_CS) | (1L << GC_CO) | (1L << GC_CN);
            default -> 0L;
        };
    }

    /**
     * Intersection of two sorted range arrays.
     */
    public static int[] intersectRanges(int[] a, int[] b) {
        if (a == null || a.length == 0 || b == null || b.length == 0) {
            return new int[0];
        }
        List<Integer> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            int aStart = a[i];
            int aEnd = a[i + 1];
            int bStart = b[j];
            int bEnd = b[j + 1];

            int overlapStart = Math.max(aStart, bStart);
            int overlapEnd = Math.min(aEnd, bEnd);
            if (overlapStart <= overlapEnd) {
                result.add(overlapStart);
                result.add(overlapEnd);
            }

            if (aEnd < bEnd) {
                i += 2;
            } else {
                j += 2;
            }
        }
        return toIntArray(result);
    }

    /**
     * Invert ranges (complement with respect to [0, 0x10FFFF]).
     */
    public static int[] invertRanges(int[] ranges) {
        if (ranges == null || ranges.length == 0) {
            return new int[]{0, 0x10FFFF};
        }
        List<Integer> result = new ArrayList<>();
        int prev = 0;
        for (int i = 0; i < ranges.length; i += 2) {
            int start = ranges[i];
            int end = ranges[i + 1];
            if (prev < start) {
                result.add(prev);
                result.add(start - 1);
            }
            prev = end + 1;
        }
        if (prev <= 0x10FFFF) {
            result.add(prev);
            result.add(0x10FFFF);
        }
        return toIntArray(result);
    }

    /**
     * Merge adjacent ranges [a, b], [b+1, c] -> [a, c].
     */
    private static int[] mergeAdjacentRanges(int[] ranges) {
        if (ranges.length <= 2) {
            return ranges;
        }
        List<Integer> result = new ArrayList<>();
        int currentStart = ranges[0];
        int currentEnd = ranges[1];
        for (int i = 2; i < ranges.length; i += 2) {
            if (ranges[i] <= currentEnd + 1) {
                currentEnd = Math.max(currentEnd, ranges[i + 1]);
            } else {
                result.add(currentStart);
                result.add(currentEnd);
                currentStart = ranges[i];
                currentEnd = ranges[i + 1];
            }
        }
        result.add(currentStart);
        result.add(currentEnd);
        return toIntArray(result);
    }

    // --- Case change detection ---

    /**
     * Resolve a binary property name (or alias) to code point ranges.
     * Returns null if the name is not recognized.
     */
    public static int[] resolveBinaryProperty(String name) {
        int[] cached = PROPERTY_CACHE.get(name);
        if (cached != null) {
            return cached;
        }

        int propIndex = findName(PROP_NAME_TABLE, name);
        if (propIndex < 0) {
            return null;
        }
        propIndex += PROP_ASCII_HEX_DIGIT;

        int[] ranges = resolvePropertyByIndex(propIndex);
        if (ranges != null) {
            PROPERTY_CACHE.put(name, ranges);
        }
        return ranges;
    }

    private static int[] resolveDerivedProperty(int propIndex) {
        // These match the QuickJS enum values after the table entries
        // We need to compute them using composition
        // The enum values for these in QuickJS are:
        // ASCII=57, Alphabetic=58, Any=59, Assigned=60, Cased=61,
        // Changes_When_Casefolded=62, Changes_When_Casemapped=63,
        // Changes_When_Lowercased=64, Changes_When_NFKC_Casefolded=65,
        // Changes_When_Titlecased=66, Changes_When_Uppercased=67,
        // Grapheme_Base=68, Grapheme_Extend=69, ID_Continue=70,
        // ID_Compat_Math_Start=71, ID_Compat_Math_Continue=72, InCB=73,
        // Lowercase=74, Math=75, Uppercase=76, XID_Continue=77, XID_Start=78

        // However, the PROP_NAME_TABLE maps from name to these by offset from ASCII_Hex_Digit.
        // So name index 36 = "ASCII" -> propIndex = 36 + 21 = 57
        // Let me use a switch on the name directly instead.
        // Actually, the propIndex values here already account for the offset.
        // Let me check: PROP_NAME_TABLE has entries starting at index 0.
        // findName returns the position in PROP_NAME_TABLE.
        // We add PROP_ASCII_HEX_DIGIT (21).
        // So "ASCII" is at PROP_NAME_TABLE[36] -> propIndex = 36 + 21 = 57.
        // That matches the enum. Good.

        // ASCII (propIndex = 57)
        if (propIndex == 57) {
            return new int[]{0x00, 0x7F};
        }
        // Any (propIndex = 59)
        if (propIndex == 59) {
            return new int[]{0x00, 0x10FFFF};
        }
        // Assigned (propIndex = 60) = NOT Cn
        if (propIndex == 60) {
            int[] cn = decodeGeneralCategory(1L << GC_CN);
            return invertRanges(cn);
        }
        // Math (propIndex = 75) = Sm | Other_Math
        if (propIndex == 75) {
            int[] sm = decodeGeneralCategory(1L << GC_SM);
            int[] otherMath = decodeBinaryProperty(PROP_OTHER_MATH);
            return unionRanges(sm, otherMath);
        }
        // Lowercase (propIndex = 74) = Ll | Other_Lowercase
        if (propIndex == 74) {
            int[] ll = decodeGeneralCategory(1L << GC_LL);
            int[] otherLowercase = decodeBinaryProperty(PROP_OTHER_LOWERCASE);
            return unionRanges(ll, otherLowercase);
        }
        // Uppercase (propIndex = 76) = Lu | Other_Uppercase
        if (propIndex == 76) {
            int[] lu = decodeGeneralCategory(1L << GC_LU);
            int[] otherUppercase = decodeBinaryProperty(PROP_OTHER_UPPERCASE);
            return unionRanges(lu, otherUppercase);
        }
        // Cased (propIndex = 61) = Lu|Ll|Lt | Other_Uppercase | Other_Lowercase
        if (propIndex == 61) {
            int[] base = decodeGeneralCategory((1L << GC_LU) | (1L << GC_LL) | (1L << GC_LT));
            int[] otherUpper = decodeBinaryProperty(PROP_OTHER_UPPERCASE);
            int[] otherLower = decodeBinaryProperty(PROP_OTHER_LOWERCASE);
            return unionRanges(unionRanges(base, otherUpper), otherLower);
        }
        // Alphabetic (propIndex = 58) = Lu|Ll|Lt|Lm|Lo|Nl | Other_Uppercase | Other_Lowercase | Other_Alphabetic
        if (propIndex == 58) {
            int[] base = decodeGeneralCategory(
                    (1L << GC_LU) | (1L << GC_LL) | (1L << GC_LT) |
                            (1L << GC_LM) | (1L << GC_LO) | (1L << GC_NL));
            int[] otherUpper = decodeBinaryProperty(PROP_OTHER_UPPERCASE);
            int[] otherLower = decodeBinaryProperty(PROP_OTHER_LOWERCASE);
            int[] otherAlpha = decodeBinaryProperty(PROP_OTHER_ALPHABETIC);
            return unionRanges(unionRanges(unionRanges(base, otherUpper), otherLower), otherAlpha);
        }
        // Grapheme_Base (propIndex = 68) = NOT (Cc|Cf|Cs|Co|Cn|Zl|Zp|Me|Mn | Other_Grapheme_Extend)
        if (propIndex == 68) {
            long mask = (1L << GC_CC) | (1L << GC_CF) | (1L << GC_CS) | (1L << GC_CO) |
                    (1L << GC_CN) | (1L << GC_ZL) | (1L << GC_ZP) | (1L << GC_ME) | (1L << GC_MN);
            int[] excluded = decodeGeneralCategory(mask);
            int[] otherGraphemeExtend = decodeBinaryProperty(PROP_OTHER_GRAPHEME_EXTEND);
            int[] combined = unionRanges(excluded, otherGraphemeExtend);
            return invertRanges(combined);
        }
        // Grapheme_Extend (propIndex = 69) = Me|Mn | Other_Grapheme_Extend
        if (propIndex == 69) {
            int[] meMn = decodeGeneralCategory((1L << GC_ME) | (1L << GC_MN));
            int[] otherGraphemeExtend = decodeBinaryProperty(PROP_OTHER_GRAPHEME_EXTEND);
            return unionRanges(meMn, otherGraphemeExtend);
        }
        // ID_Continue (propIndex = 70) = ID_Start XOR ID_Continue1
        if (propIndex == 70) {
            int[] idStart = decodeBinaryProperty(PROP_ID_START);
            int[] idContinue1 = decodeBinaryProperty(PROP_ID_CONTINUE1);
            return xorRanges(idStart, idContinue1);
        }
        // XID_Start (propIndex = 78)
        if (propIndex == 78) {
            long gcMask = (1L << GC_LU) | (1L << GC_LL) | (1L << GC_LT) |
                    (1L << GC_LM) | (1L << GC_LO) | (1L << GC_NL);
            int[] base = decodeGeneralCategory(gcMask);
            int[] otherIdStart = decodeBinaryProperty(PROP_OTHER_ID_START);
            int[] patSyntax = decodeBinaryProperty(PROP_PATTERN_SYNTAX);
            int[] patWS = decodeBinaryProperty(PROP_PATTERN_WHITE_SPACE);
            int[] xidStart1 = decodeBinaryProperty(PROP_XID_START1);
            int[] result = unionRanges(base, otherIdStart);
            int[] excluded = invertRanges(unionRanges(unionRanges(patSyntax, patWS), xidStart1));
            return intersectRanges(result, excluded);
        }
        // XID_Continue (propIndex = 77)
        if (propIndex == 77) {
            long gcMask = (1L << GC_LU) | (1L << GC_LL) | (1L << GC_LT) |
                    (1L << GC_LM) | (1L << GC_LO) | (1L << GC_NL) |
                    (1L << GC_MN) | (1L << GC_MC) | (1L << GC_ND) | (1L << GC_PC);
            int[] base = decodeGeneralCategory(gcMask);
            int[] otherIdStart = decodeBinaryProperty(PROP_OTHER_ID_START);
            int[] otherIdContinue = decodeBinaryProperty(PROP_OTHER_ID_CONTINUE);
            int[] patSyntax = decodeBinaryProperty(PROP_PATTERN_SYNTAX);
            int[] patWS = decodeBinaryProperty(PROP_PATTERN_WHITE_SPACE);
            int[] xidContinue1 = decodeBinaryProperty(PROP_XID_CONTINUE1);
            int[] result = unionRanges(unionRanges(base, otherIdStart), otherIdContinue);
            int[] excluded = invertRanges(unionRanges(unionRanges(patSyntax, patWS), xidContinue1));
            return intersectRanges(result, excluded);
        }
        // Changes_When_Uppercased (propIndex = 67) - use case conversion table
        // Changes_When_Lowercased (propIndex = 64)
        // Changes_When_Casemapped (propIndex = 63)
        // Changes_When_Titlecased (propIndex = 66) = Changes_When_Uppercased XOR Changes_When_Titlecased1
        // Changes_When_Casefolded (propIndex = 62) = case_fold XOR Changes_When_Casefolded1
        // Changes_When_NFKC_Casefolded (propIndex = 65) = case_fold XOR Changes_When_NFKC_Casefolded1
        if (propIndex == 67) {
            return buildCaseChangeRanges(CASE_U);
        }
        if (propIndex == 64) {
            return buildCaseChangeRanges(CASE_L);
        }
        if (propIndex == 63) {
            return buildCaseChangeRanges(CASE_U | CASE_L | CASE_F);
        }
        if (propIndex == 66) {
            int[] upper = buildCaseChangeRanges(CASE_U);
            int[] titlecased1 = decodeBinaryProperty(PROP_CHANGES_WHEN_TITLECASED1);
            return xorRanges(upper, titlecased1);
        }
        if (propIndex == 62) {
            int[] fold = buildCaseChangeRanges(CASE_F);
            int[] casefolded1 = decodeBinaryProperty(PROP_CHANGES_WHEN_CASEFOLDED1);
            return xorRanges(fold, casefolded1);
        }
        if (propIndex == 65) {
            int[] fold = buildCaseChangeRanges(CASE_F);
            int[] nfkcCasefolded1 = decodeBinaryProperty(PROP_CHANGES_WHEN_NFKC_CASEFOLDED1);
            return xorRanges(fold, nfkcCasefolded1);
        }

        return null;
    }

    // --- Range set operations ---

    /**
     * Resolve a General Category name (or alias) to code point ranges.
     * Returns null if the name is not recognized.
     */
    public static int[] resolveGeneralCategory(String name) {
        int[] cached = GC_CACHE.get(name);
        if (cached != null) {
            return cached;
        }

        int gcIndex = findName(GC_NAME_TABLE, name);
        if (gcIndex < 0) {
            return null;
        }

        long gcMask;
        if (gcIndex >= 30) {
            // Composite categories: LC, L, M, N, S, P, Z, C
            gcMask = getCompositeGcMask(gcIndex);
        } else {
            gcMask = 1L << gcIndex;
        }

        int[] ranges = decodeGeneralCategory(gcMask);
        GC_CACHE.put(name, ranges);
        return ranges;
    }

    private static int[] resolvePropertyByIndex(int propIndex) {
        switch (propIndex) {
            case PROP_ASCII_HEX_DIGIT:
            case PROP_BIDI_CONTROL:
            case PROP_DASH:
            case PROP_DEPRECATED:
            case PROP_DIACRITIC:
            case PROP_EXTENDER:
            case PROP_HEX_DIGIT:
            case PROP_IDS_UNARY_OPERATOR:
            case PROP_IDS_BINARY_OPERATOR:
            case PROP_IDS_TRINARY_OPERATOR:
            case PROP_IDEOGRAPHIC:
            case PROP_JOIN_CONTROL:
            case PROP_LOGICAL_ORDER_EXCEPTION:
            case PROP_MODIFIER_COMBINING_MARK:
            case PROP_NONCHARACTER_CODE_POINT:
            case PROP_PATTERN_SYNTAX:
            case PROP_PATTERN_WHITE_SPACE:
            case PROP_QUOTATION_MARK:
            case PROP_RADICAL:
            case PROP_REGIONAL_INDICATOR:
            case PROP_SENTENCE_TERMINAL:
            case PROP_SOFT_DOTTED:
            case PROP_TERMINAL_PUNCTUATION:
            case PROP_UNIFIED_IDEOGRAPH:
            case PROP_VARIATION_SELECTOR:
            case PROP_WHITE_SPACE:
            case PROP_BIDI_MIRRORED:
            case PROP_EMOJI:
            case PROP_EMOJI_COMPONENT:
            case PROP_EMOJI_MODIFIER:
            case PROP_EMOJI_MODIFIER_BASE:
            case PROP_EMOJI_PRESENTATION:
            case PROP_EXTENDED_PICTOGRAPHIC:
            case PROP_DEFAULT_IGNORABLE_CODE_POINT:
            case PROP_HYPHEN:
            case PROP_OTHER_MATH:
            case PROP_OTHER_ALPHABETIC:
            case PROP_OTHER_LOWERCASE:
            case PROP_OTHER_UPPERCASE:
            case PROP_OTHER_GRAPHEME_EXTEND:
            case PROP_OTHER_DEFAULT_IGNORABLE_CODE_POINT:
            case PROP_OTHER_ID_START:
            case PROP_OTHER_ID_CONTINUE:
            case PROP_PREPENDED_CONCATENATION_MARK:
            case PROP_ID_CONTINUE1:
            case PROP_XID_START1:
            case PROP_XID_CONTINUE1:
            case PROP_CHANGES_WHEN_TITLECASED1:
            case PROP_CHANGES_WHEN_CASEFOLDED1:
            case PROP_CHANGES_WHEN_NFKC_CASEFOLDED1:
            case PROP_ID_START:
            case PROP_CASE_IGNORABLE:
                // Direct table lookup
                return decodeBinaryProperty(propIndex);
            default:
                // Handled below as special/derived properties
                break;
        }

        // Special cases that match QuickJS enum values beyond the table
        // The property name table has indices starting at ASCII_Hex_Digit (21)
        // but the following derived properties have higher enum values
        return resolveDerivedProperty(propIndex);
    }

    /**
     * Resolve a script name (or alias) to code point ranges.
     * Returns null if the name is not recognized.
     */
    public static int[] resolveScript(String name, boolean extensions) {
        String cacheKey = (extensions ? "scx:" : "sc:") + name;
        int[] cached = SCRIPT_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int scriptIndex = findName(SCRIPT_NAME_TABLE, name);
        if (scriptIndex < 0) {
            return null;
        }

        int[] ranges = decodeScript(scriptIndex, extensions);
        if (ranges != null) {
            SCRIPT_CACHE.put(cacheKey, ranges);
        }
        return ranges;
    }

    /**
     * Result of resolving a Unicode "property of strings" (sequence property).
     * Used for RegExp with the {@code v} flag.
     *
     * @param codePointRanges single code point ranges as inclusive start/end pairs
     * @param sequences       multi-codepoint sequences (each int[] is one sequence)
     */
    public record SequencePropertyResult(int[] codePointRanges, List<int[]> sequences) {
    }

    /**
     * Resolve a Unicode "property of strings" (sequence property) by name.
     * These are used in RegExp with the {@code v} flag for matching multi-codepoint sequences.
     * <p>
     * Known properties: Basic_Emoji, Emoji_Keycap_Sequence, RGI_Emoji_Modifier_Sequence,
     * RGI_Emoji_Flag_Sequence, RGI_Emoji_Tag_Sequence, RGI_Emoji_ZWJ_Sequence, RGI_Emoji.
     * <p>
     * Ported from QuickJS {@code unicode_sequence_prop1()} in libunicode.c.
     *
     * @param name the sequence property name
     * @return the result, or null if the name is not a known sequence property
     */
    public static SequencePropertyResult resolveSequenceProperty(String name) {
        return switch (name) {
            case "Basic_Emoji" -> resolveBasicEmoji();
            case "Emoji_Keycap_Sequence" -> resolveEmojiKeycapSequence();
            case "RGI_Emoji_Modifier_Sequence" -> resolveRgiEmojiModifierSequence();
            case "RGI_Emoji_Flag_Sequence" -> resolveRgiEmojiFlagSequence();
            case "RGI_Emoji_Tag_Sequence" -> resolveRgiEmojiTagSequence();
            case "RGI_Emoji_ZWJ_Sequence" -> resolveRgiEmojiZwjSequence();
            case "RGI_Emoji" -> resolveRgiEmoji();
            default -> null;
        };
    }

    private static SequencePropertyResult resolveBasicEmoji() {
        // Basic_Emoji1: single code points (length-1 sequences -> codePointRanges)
        int[] ranges1 = decodeBinaryProperty(PROP_BASIC_EMOJI1);

        // Basic_Emoji2: each code point c becomes sequence [c, 0xFE0F]
        int[] ranges2 = decodeBinaryProperty(PROP_BASIC_EMOJI2);
        List<int[]> sequences = new ArrayList<>();
        for (int i = 0; i < ranges2.length; i += 2) {
            for (int c = ranges2[i]; c <= ranges2[i + 1]; c++) {
                sequences.add(new int[]{c, 0xFE0F});
            }
        }

        return new SequencePropertyResult(ranges1, sequences);
    }

    private static SequencePropertyResult resolveEmojiKeycapSequence() {
        // Each code point c becomes sequence [c, 0xFE0F, 0x20E3]
        int[] ranges = decodeBinaryProperty(PROP_EMOJI_KEYCAP_SEQUENCE);
        List<int[]> sequences = new ArrayList<>();
        for (int i = 0; i < ranges.length; i += 2) {
            for (int c = ranges[i]; c <= ranges[i + 1]; c++) {
                sequences.add(new int[]{c, 0xFE0F, 0x20E3});
            }
        }
        return new SequencePropertyResult(new int[0], sequences);
    }

    private static SequencePropertyResult resolveRgiEmojiModifierSequence() {
        // Each code point c paired with each of 5 skin tone modifiers
        int[] ranges = decodeBinaryProperty(PROP_EMOJI_MODIFIER_BASE);
        List<int[]> sequences = new ArrayList<>();
        for (int i = 0; i < ranges.length; i += 2) {
            for (int c = ranges[i]; c <= ranges[i + 1]; c++) {
                for (int j = 0; j < 5; j++) {
                    sequences.add(new int[]{c, 0x1F3FB + j});
                }
            }
        }
        return new SequencePropertyResult(new int[0], sequences);
    }

    private static SequencePropertyResult resolveRgiEmojiFlagSequence() {
        // Each code point c encodes a pair: c0=c/26, c1=c%26 -> [0x1F1E6+c0, 0x1F1E6+c1]
        int[] ranges = decodeBinaryProperty(PROP_RGI_EMOJI_FLAG_SEQUENCE);
        List<int[]> sequences = new ArrayList<>();
        for (int i = 0; i < ranges.length; i += 2) {
            for (int c = ranges[i]; c <= ranges[i + 1]; c++) {
                int c0 = c / 26;
                int c1 = c % 26;
                sequences.add(new int[]{0x1F1E6 + c0, 0x1F1E6 + c1});
            }
        }
        return new SequencePropertyResult(new int[0], sequences);
    }

    private static SequencePropertyResult resolveRgiEmojiTagSequence() {
        // Each null-terminated string in the byte array becomes:
        // [0x1F3F4, byte1+0xE0000, byte2+0xE0000, ..., 0xE007F]
        byte[] data = UnicodePropertyTablesData.RGI_EMOJI_TAG_SEQUENCE;
        List<int[]> sequences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == 0) {
                if (i > start) {
                    int[] seq = new int[1 + (i - start) + 1]; // 0x1F3F4 + tag bytes + 0xE007F
                    seq[0] = 0x1F3F4;
                    for (int j = start; j < i; j++) {
                        seq[1 + (j - start)] = (data[j] & 0xFF) + 0xE0000;
                    }
                    seq[seq.length - 1] = 0xE007F;
                    sequences.add(seq);
                }
                start = i + 1;
            }
        }
        return new SequencePropertyResult(new int[0], sequences);
    }

    /**
     * Decode RGI Emoji ZWJ Sequences from the compact byte encoding.
     * Ported directly from QuickJS {@code unicode_sequence_prop1} case
     * {@code UNICODE_SEQUENCE_PROP_RGI_Emoji_ZWJ_Sequence} in libunicode.c.
     * <p>
     * Uses a flat seq[] array with placeholder slots, exactly matching QuickJS.
     */
    private static SequencePropertyResult resolveRgiEmojiZwjSequence() {
        byte[] tab = UnicodePropertyTablesData.RGI_EMOJI_ZWJ_SEQUENCE;
        List<int[]> sequences = new ArrayList<>();
        int i = 0;

        while (i < tab.length) {
            int len = tab[i++] & 0xFF;
            int[] seq = new int[len * 4]; // max possible size: code + modifier + FE0F + ZWJ per entry
            int k = 0;
            int mod = 0;
            int modCount = 0;
            int[] modPos = new int[2];
            int hcPos = -1;

            for (int j = 0; j < len; j++) {
                int codeLo = tab[i++] & 0xFF;
                int codeHi = tab[i++] & 0xFF;
                int codeVal = codeLo | (codeHi << 8);

                boolean presFlag = (codeVal >> 15) != 0;
                int mod1 = (codeVal >> 13) & 3;
                int code = codeVal & 0x1FFF;

                int c;
                if (code < 0x1000) {
                    c = code + 0x2000;
                } else {
                    c = 0x1F000 + (code - 0x1000);
                }

                if (c == 0x1F9B0) {
                    hcPos = k;
                }
                seq[k++] = c;
                if (mod1 != 0) {
                    mod = mod1;
                    modPos[modCount++] = k;
                    seq[k++] = 0; // placeholder, filled later
                }
                if (presFlag) {
                    seq[k++] = 0xFE0F;
                }
                if (j < len - 1) {
                    seq[k++] = 0x200D;
                }
            }

            // Generate all variants
            int numMod;
            switch (mod) {
                case 1 -> numMod = 5;
                case 2 -> numMod = 25;
                case 3 -> numMod = 20;
                default -> numMod = 1;
            }
            int numHc = (hcPos >= 0) ? 4 : 1;

            for (int hcIdx = 0; hcIdx < numHc; hcIdx++) {
                for (int modIdx = 0; modIdx < numMod; modIdx++) {
                    if (hcPos >= 0) {
                        seq[hcPos] = 0x1F9B0 + hcIdx;
                    }
                    switch (mod) {
                        case 1 -> seq[modPos[0]] = 0x1F3FB + modIdx;
                        case 2, 3 -> {
                            int i0 = modIdx / 5;
                            int i1 = modIdx % 5;
                            if (mod == 3 && i0 >= i1) {
                                i0++;
                            }
                            seq[modPos[0]] = 0x1F3FB + i0;
                            seq[modPos[1]] = 0x1F3FB + i1;
                        }
                    }
                    int[] result = new int[k];
                    System.arraycopy(seq, 0, result, 0, k);
                    sequences.add(result);
                }
            }
        }

        return new SequencePropertyResult(new int[0], sequences);
    }

    private static SequencePropertyResult resolveRgiEmoji() {
        // Union of all sequence properties
        SequencePropertyResult basic = resolveBasicEmoji();
        SequencePropertyResult keycap = resolveEmojiKeycapSequence();
        SequencePropertyResult modifier = resolveRgiEmojiModifierSequence();
        SequencePropertyResult flag = resolveRgiEmojiFlagSequence();
        SequencePropertyResult tag = resolveRgiEmojiTagSequence();
        SequencePropertyResult zwj = resolveRgiEmojiZwjSequence();

        // Merge code point ranges
        int[] mergedRanges = basic.codePointRanges();
        mergedRanges = unionRanges(mergedRanges, keycap.codePointRanges());
        mergedRanges = unionRanges(mergedRanges, modifier.codePointRanges());
        mergedRanges = unionRanges(mergedRanges, flag.codePointRanges());
        mergedRanges = unionRanges(mergedRanges, tag.codePointRanges());
        mergedRanges = unionRanges(mergedRanges, zwj.codePointRanges());

        // Merge sequences
        List<int[]> allSequences = new ArrayList<>();
        allSequences.addAll(basic.sequences());
        allSequences.addAll(keycap.sequences());
        allSequences.addAll(modifier.sequences());
        allSequences.addAll(flag.sequences());
        allSequences.addAll(tag.sequences());
        allSequences.addAll(zwj.sequences());

        return new SequencePropertyResult(mergedRanges, allSequences);
    }

    /**
     * Sort ranges by start value and merge overlapping/adjacent ranges.
     */
    private static int[] sortAndMergeRanges(int[] ranges) {
        if (ranges.length <= 2) {
            return ranges;
        }
        // Sort pairs by start value
        int pairCount = ranges.length / 2;
        int[][] pairs = new int[pairCount][2];
        for (int i = 0; i < pairCount; i++) {
            pairs[i][0] = ranges[i * 2];
            pairs[i][1] = ranges[i * 2 + 1];
        }
        java.util.Arrays.sort(pairs, (a, b) -> Integer.compare(a[0], b[0]));

        List<Integer> result = new ArrayList<>();
        int currentStart = pairs[0][0];
        int currentEnd = pairs[0][1];
        for (int i = 1; i < pairCount; i++) {
            if (pairs[i][0] <= currentEnd + 1) {
                currentEnd = Math.max(currentEnd, pairs[i][1]);
            } else {
                result.add(currentStart);
                result.add(currentEnd);
                currentStart = pairs[i][0];
                currentEnd = pairs[i][1];
            }
        }
        result.add(currentStart);
        result.add(currentEnd);
        return toIntArray(result);
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * Union of two sorted range arrays.
     */
    public static int[] unionRanges(int[] a, int[] b) {
        if (a == null || a.length == 0) {
            return b;
        }
        if (b == null || b.length == 0) {
            return a;
        }
        List<Integer> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        int currentStart = -1;
        int currentEnd = -1;

        while (i < a.length || j < b.length) {
            int aStart = i < a.length ? a[i] : Integer.MAX_VALUE;
            int bStart = j < b.length ? b[j] : Integer.MAX_VALUE;

            int nextStart;
            int nextEnd;
            if (aStart <= bStart) {
                nextStart = a[i];
                nextEnd = a[i + 1];
                i += 2;
            } else {
                nextStart = b[j];
                nextEnd = b[j + 1];
                j += 2;
            }

            if (currentStart < 0) {
                currentStart = nextStart;
                currentEnd = nextEnd;
            } else if (nextStart <= currentEnd + 1) {
                currentEnd = Math.max(currentEnd, nextEnd);
            } else {
                result.add(currentStart);
                result.add(currentEnd);
                currentStart = nextStart;
                currentEnd = nextEnd;
            }
        }
        if (currentStart >= 0) {
            result.add(currentStart);
            result.add(currentEnd);
        }
        return toIntArray(result);
    }

    /**
     * Symmetric difference (XOR) of two sorted range arrays.
     */
    public static int[] xorRanges(int[] a, int[] b) {
        int[] aOrB = unionRanges(a, b);
        int[] aAndB = intersectRanges(a, b);
        if (aAndB.length == 0) {
            return aOrB;
        }
        int[] notAAndB = invertRanges(aAndB);
        return intersectRanges(aOrB, notAAndB);
    }
}
