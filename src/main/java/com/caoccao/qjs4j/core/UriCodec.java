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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.utils.AsciiHex;

/** Encoding and decoding algorithms used by the URI and legacy escape global functions. */
final class UriCodec {
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    private static final boolean[] URI_RESERVED_TABLE = new boolean[128];
    private static final boolean[] URI_UNESCAPED_COMPONENT_TABLE = new boolean[128];
    private static final boolean[] URI_UNESCAPED_TABLE = new boolean[128];
    static {
        for (char c = 'a'; c <= 'z'; c++) {
            URI_UNESCAPED_COMPONENT_TABLE[c] = true;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            URI_UNESCAPED_COMPONENT_TABLE[c] = true;
        }
        for (char c = '0'; c <= '9'; c++) {
            URI_UNESCAPED_COMPONENT_TABLE[c] = true;
        }
        for (char c : "-_.!~*'()".toCharArray()) {
            URI_UNESCAPED_COMPONENT_TABLE[c] = true;
        }
        for (char c : ";/?:@&=+$,#".toCharArray()) {
            URI_RESERVED_TABLE[c] = true;
        }
        for (int i = 0; i < URI_UNESCAPED_TABLE.length; i++) {
            URI_UNESCAPED_TABLE[i] = URI_UNESCAPED_COMPONENT_TABLE[i] || URI_RESERVED_TABLE[i];
        }
    }

    private UriCodec() {
    }

    private static void appendPercentHex(StringBuilder result, int c) {
        result.append('%');
        result.append(HEX_CHARS[(c >> 4) & 0xF]);
        result.append(HEX_CHARS[c & 0xF]);
    }

    static JSValue decode(JSContext context, String encodedString, boolean isComponent) {
        if (encodedString.indexOf('%') < 0) {
            return new JSString(encodedString);
        }

        int length = encodedString.length();
        StringBuilder result = new StringBuilder(length);
        int k = 0;
        while (k < length) {
            int c = encodedString.charAt(k);
            if (c != '%') {
                result.append((char) c);
                k++;
                continue;
            }

            if (k + 2 >= length) {
                return context.throwURIError("expecting hex digit");
            }
            int decodedByte = hexByte(encodedString, k);
            if (decodedByte < 0) {
                return context.throwURIError("expecting hex digit");
            }
            int percentIndex = k;
            k += 3;

            if (decodedByte < 0x80) {
                if (!isComponent && isURIReserved(decodedByte)) {
                    result.append('%').append(encodedString.charAt(percentIndex + 1))
                            .append(encodedString.charAt(percentIndex + 2));
                } else {
                    result.append((char) decodedByte);
                }
                continue;
            }

            int continuationCount;
            int minCodePoint;
            int codePoint;
            if (decodedByte >= 0xC0 && decodedByte <= 0xDF) {
                continuationCount = 1;
                minCodePoint = 0x80;
                codePoint = decodedByte & 0x1F;
            } else if (decodedByte >= 0xE0 && decodedByte <= 0xEF) {
                continuationCount = 2;
                minCodePoint = 0x800;
                codePoint = decodedByte & 0x0F;
            } else if (decodedByte >= 0xF0 && decodedByte <= 0xF7) {
                continuationCount = 3;
                minCodePoint = 0x10000;
                codePoint = decodedByte & 0x07;
            } else {
                return context.throwURIError("malformed UTF-8");
            }

            for (int i = 0; i < continuationCount; i++) {
                if (k + 2 >= length || encodedString.charAt(k) != '%') {
                    return context.throwURIError("expecting %");
                }
                int continuationByte = hexByte(encodedString, k);
                if (continuationByte < 0) {
                    return context.throwURIError("expecting hex digit");
                }
                k += 3;
                if ((continuationByte & 0xC0) != 0x80) {
                    return context.throwURIError("malformed UTF-8");
                }
                codePoint = (codePoint << 6) | (continuationByte & 0x3F);
            }

            if (codePoint < minCodePoint || codePoint > Character.MAX_CODE_POINT
                    || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
                return context.throwURIError("malformed UTF-8");
            }
            result.appendCodePoint(codePoint);
        }
        return new JSString(result.toString());
    }

    static JSValue encode(JSContext context, String inputString, boolean isComponent) {
        int length = inputString.length();
        StringBuilder result = null;
        int k = 0;
        while (k < length) {
            char ch = inputString.charAt(k);
            int c = ch;
            if (isURIUnescaped(c, isComponent)) {
                if (result != null) {
                    result.append(ch);
                }
                k++;
                continue;
            }
            if (result == null) {
                result = new StringBuilder(length + 16);
                result.append(inputString, 0, k);
            }
            if (ch >= Character.MIN_LOW_SURROGATE && ch <= Character.MAX_LOW_SURROGATE) {
                return context.throwURIError("invalid character");
            }
            k++;
            if (ch >= Character.MIN_HIGH_SURROGATE && ch <= Character.MAX_HIGH_SURROGATE) {
                if (k >= length) {
                    return context.throwURIError("expecting surrogate pair");
                }
                char c1 = inputString.charAt(k++);
                if (c1 < Character.MIN_LOW_SURROGATE || c1 > Character.MAX_LOW_SURROGATE) {
                    return context.throwURIError("expecting surrogate pair");
                }
                c = Character.toCodePoint(ch, c1);
            }

            if (c < 0x80) {
                appendPercentHex(result, c);
            } else if (c < 0x800) {
                appendPercentHex(result, (c >> 6) | 0xC0);
                appendPercentHex(result, (c & 0x3F) | 0x80);
            } else if (c < 0x10000) {
                appendPercentHex(result, (c >> 12) | 0xE0);
                appendPercentHex(result, ((c >> 6) & 0x3F) | 0x80);
                appendPercentHex(result, (c & 0x3F) | 0x80);
            } else {
                appendPercentHex(result, (c >> 18) | 0xF0);
                appendPercentHex(result, ((c >> 12) & 0x3F) | 0x80);
                appendPercentHex(result, ((c >> 6) & 0x3F) | 0x80);
                appendPercentHex(result, (c & 0x3F) | 0x80);
            }
        }
        if (result == null) {
            return new JSString(inputString);
        }
        return new JSString(result.toString());
    }

    static JSString escape(String str) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            // Check if character should not be escaped
            if (isUnescaped(c)) {
                result.append(c);
            } else {
                // Escape the character
                if (c < 256) {
                    // Use %XX format for characters < 256
                    appendPercentHex(result, c);
                } else {
                    // Use %uXXXX format for characters >= 256
                    result.append('%').append('u');
                    result.append(HEX_CHARS[(c >> 12) & 0xF]);
                    result.append(HEX_CHARS[(c >> 8) & 0xF]);
                    result.append(HEX_CHARS[(c >> 4) & 0xF]);
                    result.append(HEX_CHARS[c & 0xF]);
                }
            }
        }

        return new JSString(result.toString());
    }

    private static int hexByte(String input, int percent) {
        int high = AsciiHex.value(input.charAt(percent + 1));
        int low = AsciiHex.value(input.charAt(percent + 2));
        return low < 0 ? -1 : (high << 4) | low;
    }

    private static boolean isUnescaped(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '@' || c == '*'
                || c == '_' || c == '+' || c == '-' || c == '.' || c == '/';
    }

    private static boolean isURIReserved(int c) {
        return c < URI_RESERVED_TABLE.length && URI_RESERVED_TABLE[c];
    }

    private static boolean isURIUnescaped(int c, boolean isComponent) {
        if (c >= URI_UNESCAPED_TABLE.length) {
            return false;
        }
        return isComponent ? URI_UNESCAPED_COMPONENT_TABLE[c] : URI_UNESCAPED_TABLE[c];
    }

    private static int parseFixedHex(String str, int start, int length) {
        int end = start + length;
        if (end > str.length()) {
            return -1;
        }
        int value = 0;
        for (int i = start; i < end; i++) {
            int digit = Character.digit(str.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    static JSString unescape(String str) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (c == '%') {
                // Try to parse %uXXXX format
                if (i + 6 <= str.length() && str.charAt(i + 1) == 'u') {
                    int codePoint = parseFixedHex(str, i + 2, 4);
                    if (codePoint >= 0) {
                        result.append((char) codePoint);
                        i += 5; // Skip the next 5 characters (uXXXX)
                        continue;
                    }
                }

                // Try to parse %XX format
                if (i + 3 <= str.length()) {
                    int codePoint = parseFixedHex(str, i + 1, 2);
                    if (codePoint >= 0) {
                        result.append((char) codePoint);
                        i += 2; // Skip the next 2 characters (XX)
                        continue;
                    }
                }
            }

            // Not an escape sequence, append as is
            result.append(c);
        }

        return new JSString(result.toString());
    }
}
