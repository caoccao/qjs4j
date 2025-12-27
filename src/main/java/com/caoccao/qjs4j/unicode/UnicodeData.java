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
 * Unicode character property tables and lookups.
 * Based on QuickJS libunicode.c implementation.
 */
public final class UnicodeData {

    // Character type bits (from QuickJS)
    public static final int UNICODE_C_SPACE = (1 << 0);
    public static final int UNICODE_C_DIGIT = (1 << 1);
    public static final int UNICODE_C_UPPER = (1 << 2);
    public static final int UNICODE_C_LOWER = (1 << 3);
    public static final int UNICODE_C_UNDER = (1 << 4);
    public static final int UNICODE_C_DOLLAR = (1 << 5);
    public static final int UNICODE_C_XDIGIT = (1 << 6);

    // ASCII character type table (0-255)
    private static final byte[] ASCII_CTYPE_BITS = new byte[256];

    static {
        // Initialize ASCII character types
        for (int i = 0; i < 256; i++) {
            byte bits = 0;

            // Space characters
            if (i == ' ' || i == '\t' || i == '\n' || i == '\r' || i == '\f' || i == '\u000B') {
                bits |= UNICODE_C_SPACE;
            }

            // Digits
            if (i >= '0' && i <= '9') {
                bits |= UNICODE_C_DIGIT | UNICODE_C_XDIGIT;
            }

            // Uppercase
            if (i >= 'A' && i <= 'Z') {
                bits |= UNICODE_C_UPPER;
            }

            // Lowercase
            if (i >= 'a' && i <= 'z') {
                bits |= UNICODE_C_LOWER;
            }

            // Hex digits
            if ((i >= 'A' && i <= 'F') || (i >= 'a' && i <= 'f')) {
                bits |= UNICODE_C_XDIGIT;
            }

            // Underscore
            if (i == '_') {
                bits |= UNICODE_C_UNDER;
            }

            // Dollar sign
            if (i == '$') {
                bits |= UNICODE_C_DOLLAR;
            }

            ASCII_CTYPE_BITS[i] = bits;
        }
    }

    /**
     * Check if character is a whitespace character.
     * Follows ECMAScript specification.
     */
    public static boolean isWhiteSpace(int codePoint) {
        if (codePoint < 256) {
            return (ASCII_CTYPE_BITS[codePoint] & UNICODE_C_SPACE) != 0;
        }

        // Non-ASCII whitespace characters
        switch (codePoint) {
            case 0x00A0: // NO-BREAK SPACE
            case 0x1680: // OGHAM SPACE MARK
            case 0x2000: // EN QUAD
            case 0x2001: // EM QUAD
            case 0x2002: // EN SPACE
            case 0x2003: // EM SPACE
            case 0x2004: // THREE-PER-EM SPACE
            case 0x2005: // FOUR-PER-EM SPACE
            case 0x2006: // SIX-PER-EM SPACE
            case 0x2007: // FIGURE SPACE
            case 0x2008: // PUNCTUATION SPACE
            case 0x2009: // THIN SPACE
            case 0x200A: // HAIR SPACE
            case 0x202F: // NARROW NO-BREAK SPACE
            case 0x205F: // MEDIUM MATHEMATICAL SPACE
            case 0x3000: // IDEOGRAPHIC SPACE
            case 0xFEFF: // ZERO WIDTH NO-BREAK SPACE
                return true;
            default:
                return Character.isWhitespace(codePoint);
        }
    }

    /**
     * Check if character can start an identifier.
     * Follows ECMAScript specification (ID_Start property).
     */
    public static boolean isIdentifierStart(int codePoint) {
        if (codePoint < 128) {
            // ASCII fast path
            return (ASCII_CTYPE_BITS[codePoint] & (UNICODE_C_UPPER | UNICODE_C_LOWER |
                    UNICODE_C_UNDER | UNICODE_C_DOLLAR)) != 0;
        }

        // Use Java's Character class for Unicode support
        return Character.isUnicodeIdentifierStart(codePoint);
    }

    /**
     * Check if character can continue an identifier.
     * Follows ECMAScript specification (ID_Continue property).
     */
    public static boolean isIdentifierPart(int codePoint) {
        if (codePoint < 128) {
            // ASCII fast path
            return (ASCII_CTYPE_BITS[codePoint] & (UNICODE_C_UPPER | UNICODE_C_LOWER |
                    UNICODE_C_UNDER | UNICODE_C_DOLLAR | UNICODE_C_DIGIT)) != 0;
        }

        // ZWNJ (U+200C) and ZWJ (U+200D) are allowed in identifiers
        if (codePoint == 0x200C || codePoint == 0x200D) {
            return true;
        }

        // Use Java's Character class for Unicode support
        return Character.isUnicodeIdentifierPart(codePoint);
    }

    /**
     * Check if character is a line terminator.
     * ECMAScript recognizes: LF, CR, LS, PS
     */
    public static boolean isLineTerminator(int codePoint) {
        return codePoint == 0x000A ||  // LINE FEED (LF)
                codePoint == 0x000D ||  // CARRIAGE RETURN (CR)
                codePoint == 0x2028 ||  // LINE SEPARATOR (LS)
                codePoint == 0x2029;    // PARAGRAPH SEPARATOR (PS)
    }

    /**
     * Check if character is a decimal digit.
     */
    public static boolean isDigit(int codePoint) {
        if (codePoint < 256) {
            return (ASCII_CTYPE_BITS[codePoint] & UNICODE_C_DIGIT) != 0;
        }
        return Character.isDigit(codePoint);
    }

    /**
     * Check if character is a hex digit.
     */
    public static boolean isHexDigit(int codePoint) {
        if (codePoint < 256) {
            return (ASCII_CTYPE_BITS[codePoint] & UNICODE_C_XDIGIT) != 0;
        }
        return false;
    }

    /**
     * Check if character is a word character (for regex \w).
     */
    public static boolean isWordChar(int codePoint) {
        if (codePoint < 256) {
            return (ASCII_CTYPE_BITS[codePoint] & (UNICODE_C_UPPER | UNICODE_C_LOWER |
                    UNICODE_C_UNDER | UNICODE_C_DIGIT)) != 0;
        }
        return false;
    }

    /**
     * Check if codepoint is a surrogate.
     */
    public static boolean isSurrogate(int codePoint) {
        return (codePoint >> 11) == (0xD800 >> 11); // 0xD800-0xDFFF
    }

    /**
     * Check if codepoint is a high surrogate.
     */
    public static boolean isHighSurrogate(int codePoint) {
        return (codePoint >> 10) == (0xD800 >> 10); // 0xD800-0xDBFF
    }

    /**
     * Check if codepoint is a low surrogate.
     */
    public static boolean isLowSurrogate(int codePoint) {
        return (codePoint >> 10) == (0xDC00 >> 10); // 0xDC00-0xDFFF
    }

    /**
     * Combine high and low surrogates into a codepoint.
     */
    public static int fromSurrogates(int high, int low) {
        return 0x10000 + 0x400 * (high - 0xD800) + (low - 0xDC00);
    }
}
