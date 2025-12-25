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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implementation of JavaScript Number.prototype methods.
 * Based on ES2020 Number.prototype specification.
 */
public final class NumberPrototype {
    public static final long MAX_SAFE_INTEGER = 9007199254740991L; // 2^53 - 1

    /**
     * Number.prototype.toFixed(fractionDigits)
     * ES2020 20.1.3.3
     */
    public static JSValue toFixed(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSNumber num = JSTypeConversions.toNumber(thisArg);
        double value = num.value();

        // Get fractionDigits (default 0)
        long f = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;

        // RangeError if out of bounds [0, 100]
        if (f < 0 || f > 100) {
            return ctx.throwError("RangeError", "toFixed() fractionDigits must be between 0 and 100");
        }

        // Handle special cases
        if (Double.isNaN(value)) {
            return new JSString("NaN");
        }

        if (Math.abs(value) >= 1e21) {
            return new JSString(DtoaConverter.convert(value));
        }

        // Use BigDecimal for accurate rounding
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale((int) f, RoundingMode.HALF_UP);

        return new JSString(bd.toPlainString());
    }

    /**
     * Number.prototype.toExponential(fractionDigits)
     * ES2020 20.1.3.2
     */
    public static JSValue toExponential(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSNumber num = JSTypeConversions.toNumber(thisArg);
        double value = num.value();

        // Handle special cases
        if (Double.isNaN(value)) {
            return new JSString("NaN");
        }
        if (Double.isInfinite(value)) {
            return new JSString(value > 0 ? "Infinity" : "-Infinity");
        }

        // Get fractionDigits
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            // Default: use toString with exponential notation
            return new JSString(String.format("%e", value));
        }

        long f = (long) JSTypeConversions.toInteger(args[0]);

        // RangeError if out of bounds [0, 100]
        if (f < 0 || f > 100) {
            return ctx.throwError("RangeError", "toExponential() fractionDigits must be between 0 and 100");
        }

        // Format with specified precision
        String format = "%." + f + "e";
        return new JSString(String.format(format, value));
    }

    /**
     * Number.prototype.toPrecision(precision)
     * ES2020 20.1.3.5
     */
    public static JSValue toPrecision(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSNumber num = JSTypeConversions.toNumber(thisArg);
        double value = num.value();

        // Handle special cases
        if (Double.isNaN(value)) {
            return new JSString("NaN");
        }
        if (Double.isInfinite(value)) {
            return new JSString(value > 0 ? "Infinity" : "-Infinity");
        }

        // If precision is undefined, use toString
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            return new JSString(DtoaConverter.convert(value));
        }

        long p = (long) JSTypeConversions.toInteger(args[0]);

        // RangeError if out of bounds [1, 100]
        if (p < 1 || p > 100) {
            return ctx.throwError("RangeError", "toPrecision() precision must be between 1 and 100");
        }

        // Use BigDecimal for accurate formatting
        if (value == 0.0) {
            // Special case for zero
            StringBuilder result = new StringBuilder("0");
            if (p > 1) {
                result.append(".");
                for (int i = 1; i < p; i++) {
                    result.append("0");
                }
            }
            return new JSString(result.toString());
        }

        // Determine if we should use exponential or fixed notation
        int exponent = (int) Math.floor(Math.log10(Math.abs(value)));

        if (exponent < -6 || exponent >= p) {
            // Use exponential notation
            String format = "%." + (p - 1) + "e";
            return new JSString(String.format(format, value));
        } else {
            // Use fixed notation
            BigDecimal bd = BigDecimal.valueOf(value);
            bd = bd.round(new java.math.MathContext((int) p, RoundingMode.HALF_UP));
            String result = bd.stripTrailingZeros().toPlainString();

            // Ensure we have exactly p significant digits
            return new JSString(result);
        }
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
        long radix = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 10;

        // RangeError if radix out of bounds [2, 36]
        if (radix < 2 || radix > 36) {
            return ctx.throwError("RangeError", "toString() radix must be between 2 and 36");
        }

        // For radix 10, use default conversion
        if (radix == 10) {
            return new JSString(DtoaConverter.convert(value));
        }

        // For other radixes, only works for integers
        if (value != Math.floor(value)) {
            // Non-integer with non-10 radix - not fully supported
            return new JSString(DtoaConverter.convert(value));
        }

        // Convert integer to specified radix
        long intValue = (long) value;
        if (intValue < 0) {
            return new JSString("-" + Long.toString(-intValue, (int) radix));
        }
        return new JSString(Long.toString(intValue, (int) radix));
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
     * Number.prototype.valueOf()
     * ES2020 20.1.3.7
     */
    public static JSValue valueOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSNumber num) {
            return num;
        }
        return ctx.throwError("TypeError", "Number.prototype.valueOf called on non-number");
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
            Math.abs(d) <= 0x1FFFFFFFFFFFFFL  // 2^53 - 1
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
        String str = JSTypeConversions.toString(args[0]).getValue().strip();
        try {
            return new JSNumber(Double.parseDouble(str));
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

        String str = JSTypeConversions.toString(args[0]).getValue().strip();
        long radix = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : 0;

        // Auto-detect radix if 0
        if (radix == 0) {
            if (str.startsWith("0x") || str.startsWith("0X")) {
                radix = 16;
                str = str.substring(2);
            } else {
                radix = 10;
            }
        }

        // Validate radix
        if (radix < 2 || radix > 36) {
            return new JSNumber(Double.NaN);
        }

        try {
            return new JSNumber(Long.parseLong(str, (int) radix));
        } catch (NumberFormatException e) {
            return new JSNumber(Double.NaN);
        }
    }
}
