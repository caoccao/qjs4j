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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.util.DtoaConverter;

/**
 * Implementation of JavaScript Number.prototype methods.
 * Based on ES2020 Number.prototype specification.
 */
public final class NumberPrototype {
    public static final long MAX_SAFE_INTEGER = 9007199254740991L; // 2^53 - 1

    /**
     * Number.isFinite(value)
     * ES2020 20.1.2.2
     */
    public static JSValue isFinite(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        JSValue value = args[0];
        return JSBoolean.valueOf(value instanceof JSNumber n && Double.isFinite(n.value()));
    }

    /**
     * Number.isInteger(value)
     * ES2020 20.1.2.3
     */
    public static JSValue isInteger(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        JSValue value = args[0];
        if (!(value instanceof JSNumber n)) {
            return JSBoolean.FALSE;
        }
        double d = n.value();
        return JSBoolean.valueOf(Double.isFinite(d) && d == Math.floor(d));
    }

    /**
     * Number.isNaN(value)
     * ES2020 20.1.2.4
     */
    public static JSValue isNaN(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        JSValue value = args[0];
        return JSBoolean.valueOf(value instanceof JSNumber n && Double.isNaN(n.value()));
    }

    /**
     * Number.isSafeInteger(value)
     * ES2020 20.1.2.5
     */
    public static JSValue isSafeInteger(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        JSValue value = args[0];
        if (!(value instanceof JSNumber n)) {
            return JSBoolean.FALSE;
        }
        double d = n.value();
        return JSBoolean.valueOf(
                Double.isFinite(d) &&
                        d == Math.floor(d) &&
                        Math.abs(d) <= MAX_SAFE_INTEGER
        );
    }

    /**
     * Number.parseFloat(string)
     * ES2020 20.1.2.12
     */
    public static JSValue parseFloat(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        String str = JSTypeConversions.toString(args[0]).value().trim();

        // Find the longest prefix that could be a valid number
        if (str.isEmpty()) {
            return new JSNumber(Double.NaN);
        }

        // Skip leading whitespace (already done with trim)
        int start = 0;

        // Check for optional sign
        if (str.charAt(start) == '+' || str.charAt(start) == '-') {
            start++;
        }

        // Check for Infinity
        if (str.regionMatches(true, start, "Infinity", 0, 8)) {
            return new JSNumber(str.charAt(0) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        }

        // Check for NaN
        if (str.regionMatches(true, start, "NaN", 0, 3)) {
            return new JSNumber(Double.NaN);
        }

        // Parse the number part
        int end = start;
        boolean hasDot = false;
        boolean hasExp = false;

        // Integer part
        while (end < str.length() && Character.isDigit(str.charAt(end))) {
            end++;
        }

        // Decimal part
        if (end < str.length() && str.charAt(end) == '.') {
            hasDot = true;
            end++;
            while (end < str.length() && Character.isDigit(str.charAt(end))) {
                end++;
            }
        }

        // Exponent part
        if (end < str.length() && (str.charAt(end) == 'e' || str.charAt(end) == 'E')) {
            hasExp = true;
            end++;
            if (end < str.length() && (str.charAt(end) == '+' || str.charAt(end) == '-')) {
                end++;
            }
            while (end < str.length() && Character.isDigit(str.charAt(end))) {
                end++;
            }
        }

        if (end == start && !hasDot) {
            // No valid number found
            return new JSNumber(Double.NaN);
        }

        String numberStr = str.substring(0, end);
        try {
            return new JSNumber(Double.parseDouble(numberStr));
        } catch (NumberFormatException e) {
            return new JSNumber(Double.NaN);
        }
    }

    /**
     * Number.parseInt(string, radix)
     * ES2020 20.1.2.13
     */
    public static JSValue parseInt(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }

        String str = JSTypeConversions.toString(args[0]).value().trim();
        long radix = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : 0;

        // Auto-detect radix if 0
        if (radix == 0) {
            if (str.startsWith("0x") || str.startsWith("0X")) {
                radix = 16;
                str = str.substring(2);
            } else if (str.startsWith("0o") || str.startsWith("0O")) {
                radix = 8;
                str = str.substring(2);
            } else if (str.startsWith("0b") || str.startsWith("0B")) {
                radix = 2;
                str = str.substring(2);
            } else {
                radix = 10;
            }
        } else {
            // If radix is 16, allow 0x/0X prefix
            if (radix == 16 && (str.startsWith("0x") || str.startsWith("0X"))) {
                str = str.substring(2);
            }
        }

        // Validate radix
        if (radix < 2 || radix > 36) {
            return new JSNumber(Double.NaN);
        }

        // Skip leading whitespace (already done with trim)
        int start = 0;

        // Check for optional sign
        boolean negative = false;
        if (start < str.length() && str.charAt(start) == '+') {
            start++;
        } else if (start < str.length() && str.charAt(start) == '-') {
            negative = true;
            start++;
        }

        // Parse digits
        long result = 0;
        boolean hasDigits = false;

        for (int i = start; i < str.length(); i++) {
            char c = str.charAt(i);
            int digit;

            if (Character.isDigit(c)) {
                digit = c - '0';
            } else if (Character.isLetter(c)) {
                digit = Character.toLowerCase(c) - 'a' + 10;
            } else {
                break; // Stop at first invalid character
            }

            if (digit < 0 || digit >= radix) {
                break; // Invalid digit for this radix
            }

            hasDigits = true;

            // Check for overflow
            if (result > (Long.MAX_VALUE - digit) / radix) {
                return new JSNumber(negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            }

            result = result * radix + digit;
        }

        if (!hasDigits) {
            return new JSNumber(Double.NaN);
        }

        return new JSNumber(negative ? -result : result);
    }

    /**
     * Number.prototype.toExponential(fractionDigits)
     * ES2020 20.1.3.2
     */
    public static JSValue toExponential(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSNumber num = JSTypeConversions.toNumber(thisArg);
        double value = num.value();
        // Get fractionDigits
        if (args.length == 0 || args[0].isUndefined()) {
            // No argument: use automatic precision (minimal digits to uniquely represent the value)
            return new JSString(DtoaConverter.convertExponentialWithoutFractionDigits(value));
        }
        int fractionDigits = (int) JSTypeConversions.toInteger(args[0]);
        // RangeError if out of bounds [0, 100]
        if (fractionDigits < 0 || fractionDigits > DtoaConverter.MAX_DIGITS) {
            return ctx.throwError("RangeError", "toExponential() fractionDigits must be between 0 and 100");
        }
        return new JSString(DtoaConverter.convertExponentialWithFractionDigits(value, fractionDigits));
    }

    /**
     * Number.prototype.toFixed(fractionDigits)
     * ES2020 20.1.3.3
     */
    public static JSValue toFixed(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSNumber num = JSTypeConversions.toNumber(thisArg);
        double value = num.value();
        int fractionDigits = 0;
        // Get fractionDigits
        if (args.length > 0 && !args[0].isUndefined()) {
            fractionDigits = (int) JSTypeConversions.toInteger(args[0]);
        }
        // RangeError if out of bounds [0, 100]
        if (fractionDigits < 0 || fractionDigits > DtoaConverter.MAX_DIGITS) {
            return ctx.throwError("RangeError", "toFixed() fractionDigits must be between 0 and 100");
        }
        return new JSString(DtoaConverter.convertFixed(value, fractionDigits));
    }

    /**
     * Number.prototype.toLocaleString()
     * ES2020 20.1.3.4 (simplified)
     */
    public static JSValue toLocaleString(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSNumber num = JSTypeConversions.toNumber(thisArg);
        // Simplified: just use default toString
        return new JSString(DtoaConverter.convert(num.value()));
    }

    /**
     * Number.prototype.toPrecision(precision)
     * ES2020 20.1.3.5
     */
    public static JSValue toPrecision(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSNumber num = JSTypeConversions.toNumber(thisArg);
        double value = num.value();

        // If precision is undefined, use toString
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            return new JSString(DtoaConverter.convert(value));
        }

        int precision = (int) JSTypeConversions.toInteger(args[0]);

        // RangeError if out of bounds [1, 100]
        if (precision < 1 || precision > DtoaConverter.MAX_PRECISION) {
            return ctx.throwError("RangeError", "toPrecision() precision must be between 1 and 100");
        }

        return new JSString(DtoaConverter.convertWithPrecision(value, precision));
    }

    /**
     * Number.prototype.toString(radix)
     * ES2020 20.1.3.6
     */
    public static JSValue toString(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSNumber num = JSTypeConversions.toNumber(thisArg);
        double value = num.value();

        // Handle special cases
        if (Double.isNaN(value)) {
            return new JSString("NaN");
        }
        if (Double.isInfinite(value)) {
            return new JSString(value > 0 ? "Infinity" : "-Infinity");
        }

        // Get radix (default 10)
        int radix = args.length > 0 ? (int) JSTypeConversions.toInteger(args[0]) : 10;

        // RangeError if radix out of bounds [2, 36]
        if (radix < 2 || radix > 36) {
            return ctx.throwError("RangeError", "toString() radix must be between 2 and 36");
        }

        // For radix 10, use default conversion
        if (radix == 10) {
            return new JSString(DtoaConverter.convert(value));
        }

        // For other radixes, convert using the radix conversion method
        return new JSString(DtoaConverter.convertToRadix(value, radix));
    }

    /**
     * Number.prototype.valueOf()
     * ES2020 20.1.3.7
     */
    public static JSValue valueOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSNumber num) {
            return num;
        }
        return ctx.throwError("TypeError", "Number.prototype.valueOf called on non-number");
    }
}
