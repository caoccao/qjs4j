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

import com.caoccao.qjs4j.utils.AsciiHex;

import static com.caoccao.qjs4j.regexp.RegExpCompiler.*;

/** Escape decoding shared by atoms, capture names and character classes. */
final class RegExpEscapes {

    private RegExpEscapes() {
    }

    private static int combineTrailingLowSurrogateEscapeIfPresent(CompileContext context, int codePoint) {
        if (!context.isUnicodeMode()) {
            return codePoint;
        }
        if (!Character.isHighSurrogate((char) codePoint)) {
            return codePoint;
        }
        // Per QuickJS: only combine with 4-digit \\uHHHH trailing escapes,
        // NOT braced \\u{XXXX} escapes.
        EscapedCodePoint unicodeEscapeParseResult = tryParseUnicodeEscapeAt(context.codePoints, context.pos, false);
        if (unicodeEscapeParseResult == null) {
            return codePoint;
        }
        int trailingCodePoint = unicodeEscapeParseResult.codePoint();
        if (!Character.isLowSurrogate((char) trailingCodePoint)) {
            return codePoint;
        }
        context.pos = unicodeEscapeParseResult.nextPos();
        return Character.toCodePoint((char) codePoint, (char) trailingCodePoint);
    }

    private static int fixedHexValue(int[] codePoints, int start, int digits) {
        if (start > codePoints.length - digits) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < digits; i++) {
            int digit = AsciiHex.value(codePoints[start + i]);
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    /**
     * Returns true if the character is a RegExp syntax character per ES2024 11.8.5. These are: ^ $ \ . * + ? ( ) [ ] {
     * } | /
     */
    static boolean isSyntaxCharacter(int ch) {
        return ch == '^' || ch == '$' || ch == '\\' || ch == '.' || ch == '*' || ch == '+' || ch == '?' || ch == '('
                || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' || ch == '|' || ch == '/';
    }

    private static int parseBracedUnicodeEscape(CompileContext context) {
        int firstDigit = ++context.pos;
        int value = 0;
        while (context.hasMore() && context.peek() != '}') {
            int digit = AsciiHex.value(context.peek());
            if (digit < 0) {
                throw new RegExpSyntaxException("Invalid unicode escape");
            }
            context.pos++;
            value = (value << 4) | digit;
            if (value > MAX_UNICODE_CODE_POINT) {
                throw new RegExpSyntaxException("Unicode escape out of range");
            }
        }
        if (context.pos == firstDigit) {
            throw new RegExpSyntaxException("Empty unicode escape");
        }
        if (context.pos == context.codePoints.length) {
            throw new RegExpSyntaxException("Invalid unicode escape");
        }
        context.pos++;
        // Only four-digit escapes combine with a trailing escaped surrogate.
        return value;
    }

    static int parseLegacyOctalEscape(CompileContext context, int firstDigit) {
        int value = firstDigit;
        if (context.hasMore()) {
            int next = context.peek();
            if (next >= '0' && next <= '7') {
                value = (value << 3) | (next - '0');
                context.pos++;
                if (value < 32 && context.hasMore()) {
                    int third = context.peek();
                    if (third >= '0' && third <= '7') {
                        value = (value << 3) | (third - '0');
                        context.pos++;
                    }
                }
            }
        }
        return value;
    }

    static int parseUnicodeEscape(CompileContext context) {
        if (context.isUnicodeMode() && context.at('{')) {
            return parseBracedUnicodeEscape(context);
        }
        int value = readFixedHexEscape(context, 'u', 4);
        return combineTrailingLowSurrogateEscapeIfPresent(context, value);
    }

    static int readCharacterEscape(CompileContext context, int escape) {
        return switch (escape) {
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'v' -> 0x0B;
            case 'x' -> readFixedHexEscape(context, 'x', 2);
            case 'u' -> parseUnicodeEscape(context);
            case '0' -> {
                if (!context.isUnicodeMode()) {
                    yield parseLegacyOctalEscape(context, 0);
                }
                if (context.hasMore() && context.peek() >= '0' && context.peek() <= '9') {
                    throw new RegExpSyntaxException("Invalid escape sequence");
                }
                yield 0;
            }
            default -> throw new AssertionError(escape);
        };
    }

    static int readControlLetter(CompileContext context, boolean inClass) {
        if (context.hasMore()) {
            int next = context.peek();
            boolean annexB = inClass && !context.isUnicodeMode() && ((next >= '0' && next <= '9') || next == '_');
            if ((next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z') || annexB) {
                context.pos++;
                return next % 32;
            }
        }
        if (context.isUnicodeMode()) {
            throw new RegExpSyntaxException("Invalid control escape");
        }
        return -1;
    }

    /** Leave the cursor on the first following character for an Annex B identity escape. */
    static int readFixedHexEscape(CompileContext context, int escape, int digits) {
        int value = fixedHexValue(context.codePoints, context.pos, digits);
        if (value >= 0) {
            context.pos += digits;
            return value;
        }
        if (context.isUnicodeMode()) {
            throw new RegExpSyntaxException(escape == 'x' ? "Invalid hex escape" : "Invalid unicode escape");
        }
        return escape;
    }

    static EscapedCodePoint tryParseUnicodeEscapeAt(int[] codePoints, int startPos, boolean allowBraced) {
        if (startPos + 1 >= codePoints.length || codePoints[startPos] != '\\' || codePoints[startPos + 1] != 'u') {
            return null;
        }
        int currentPos = startPos + 2;
        if (currentPos < codePoints.length && codePoints[currentPos] == '{') {
            if (!allowBraced) {
                return null;
            }
            currentPos++;
            int value = 0;
            int digitCount = 0;
            while (currentPos < codePoints.length && codePoints[currentPos] != '}') {
                int hexValue = AsciiHex.value(codePoints[currentPos]);
                if (hexValue < 0) {
                    return null;
                }
                value = (value << 4) | hexValue;
                digitCount++;
                if (digitCount > 6 || value > MAX_UNICODE_CODE_POINT) {
                    return null;
                }
                currentPos++;
            }
            if (digitCount == 0 || currentPos >= codePoints.length || codePoints[currentPos] != '}') {
                return null;
            }
            return new EscapedCodePoint(value, currentPos + 1);
        }
        int value = fixedHexValue(codePoints, currentPos, 4);
        if (value < 0) {
            return null;
        }
        return new EscapedCodePoint(value, currentPos + 4);
    }

    record EscapedCodePoint(int codePoint, int nextPos) {
    }
}
