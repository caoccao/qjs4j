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

/** Numeric prefix parsing for the global parseInt and parseFloat functions. */
final class GlobalNumberParser {
    private GlobalNumberParser() {
    }

    private static int asciiDigitValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 10;
        }
        return -1;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isInfinityPrefix(String str, int start) {
        return start + 8 <= str.length() && str.startsWith("Infinity", start);
    }

    static JSValue numberConversionError(JSContext context, JSValue value) {
        if (value.isSymbol() || value.isSymbolObject()) {
            return context.throwTypeError("cannot convert symbol to number");
        }
        if (value.isBigInt() || value.isBigIntObject()) {
            return context.throwTypeError("cannot convert bigint to number");
        }
        return null;
    }

    static JSValue parseFloat(String inputString) {
        int index = JSTypeConversions.skipLeadingWhitespace(inputString);
        if (index >= inputString.length()) {
            return JSNumber.of(Double.NaN);
        }

        int signIndex = index;
        if (inputString.charAt(index) == '+' || inputString.charAt(index) == '-') {
            index++;
        }
        if (isInfinityPrefix(inputString, index)) {
            boolean isNegative = inputString.charAt(signIndex) == '-';
            return JSNumber.of(isNegative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        }

        int i = skipDigits(inputString, index);
        boolean hasLeadingDigits = i != index;
        boolean hasFractionDigits = false;
        if (i < inputString.length() && inputString.charAt(i) == '.') {
            int fractionStart = i + 1;
            i = skipDigits(inputString, fractionStart);
            hasFractionDigits = i != fractionStart;
        }
        if (!hasLeadingDigits && !hasFractionDigits) {
            return JSNumber.of(Double.NaN);
        }

        if (i < inputString.length() && (inputString.charAt(i) == 'e' || inputString.charAt(i) == 'E')) {
            int exponentMarkerIndex = i;
            i++;
            if (i < inputString.length() && (inputString.charAt(i) == '+' || inputString.charAt(i) == '-')) {
                i++;
            }
            int exponentDigitsStart = i;
            i = skipDigits(inputString, i);
            if (i == exponentDigitsStart) {
                i = exponentMarkerIndex;
            }
        }

        String validNumber = inputString.substring(signIndex, i);
        return JSNumber.of(Double.parseDouble(validNumber));
    }

    static JSValue parseInt(JSContext context, JSValue[] args, String inputString) {
        int index = JSTypeConversions.skipLeadingWhitespace(inputString);
        if (index >= inputString.length()) {
            return JSNumber.of(Double.NaN);
        }

        int sign = 1;
        if (inputString.charAt(index) == '+') {
            index++;
        } else if (inputString.charAt(index) == '-') {
            sign = -1;
            index++;
        }

        int radix = 0;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            JSValue radixValue = args[1];
            if (radixValue instanceof JSNumber number) {
                radix = JSTypeConversions.toInt32(number.value());
            } else {
                JSValue error = numberConversionError(context, radixValue);
                if (error != null) {
                    return error;
                }
                radix = JSTypeConversions.toInt32(context, radixValue);
            }
        }

        if (radix != 0 && (radix < 2 || radix > 36)) {
            return JSNumber.of(Double.NaN);
        }

        boolean stripPrefix = radix == 0 || radix == 16;
        if (radix == 0) {
            radix = 10;
        }
        if (stripPrefix && index + 1 < inputString.length() && inputString.charAt(index) == '0'
                && (inputString.charAt(index + 1) == 'x' || inputString.charAt(index + 1) == 'X')) {
            index += 2;
            radix = 16;
        }

        double result = 0.0;
        boolean foundDigit = false;
        while (index < inputString.length()) {
            int digit = asciiDigitValue(inputString.charAt(index));
            if (digit < 0 || digit >= radix) {
                break;
            }
            result = result * radix + digit;
            foundDigit = true;
            index++;
        }

        if (!foundDigit) {
            return JSNumber.of(Double.NaN);
        }

        return JSNumber.of(sign * result);
    }

    private static int skipDigits(String text, int start) {
        int index = start;
        while (index < text.length() && isAsciiDigit(text.charAt(index))) {
            index++;
        }
        return index;
    }
}
