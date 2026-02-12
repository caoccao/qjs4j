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
import com.caoccao.qjs4j.utils.DtoaConverter;

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
    public static JSValue isFinite(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public static JSValue isInteger(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public static JSValue isNaN(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public static JSValue isSafeInteger(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public static JSValue parseFloat(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.getJSGlobalObject().parseFloat(context, thisArg, args);
    }

    /**
     * Number.parseInt(string, radix)
     * ES2020 20.1.2.13
     */
    public static JSValue parseInt(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.getJSGlobalObject().parseInt(context, thisArg, args);
    }

    /**
     * Number.prototype.toExponential(fractionDigits)
     * ES2020 20.1.3.2
     */
    public static JSValue toExponential(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asNumberWithDownCast()
                .map(jsNumber -> {
                    double value = jsNumber.value();
                    // Get fractionDigits
                    if (args.length == 0 || args[0].isUndefined()) {
                        // No argument: use automatic precision (minimal digits to uniquely represent the value)
                        return new JSString(DtoaConverter.convertExponentialWithoutFractionDigits(value));
                    }
                    int fractionDigits = (int) JSTypeConversions.toInteger(context, args[0]);
                    // RangeError if out of bounds [0, 100]
                    if (fractionDigits < 0 || fractionDigits > DtoaConverter.MAX_DIGITS) {
                        return context.throwRangeError("toExponential() argument must be between 0 and 100");
                    }
                    return new JSString(DtoaConverter.convertExponentialWithFractionDigits(value, fractionDigits));
                })
                .orElseGet(() -> context.throwTypeError("Number.prototype.toExponential requires that 'this' be a Number"));
    }

    /**
     * Number.prototype.toFixed(fractionDigits)
     * ES2020 20.1.3.3
     */
    public static JSValue toFixed(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asNumberWithDownCast()
                .map(jsNumber -> {
                    double value = jsNumber.value();
                    int fractionDigits = 0;
                    // Get fractionDigits
                    if (args.length > 0 && !args[0].isUndefined()) {
                        fractionDigits = (int) JSTypeConversions.toInteger(context, args[0]);
                    }
                    // RangeError if out of bounds [0, 100]
                    if (fractionDigits < 0 || fractionDigits > DtoaConverter.MAX_DIGITS) {
                        return context.throwRangeError("toFixed() digits argument must be between 0 and 100");
                    }
                    return new JSString(DtoaConverter.convertFixed(value, fractionDigits));
                })
                .orElseGet(() -> context.throwTypeError("Number.prototype.toPrecision requires that 'this' be a Number"));
    }

    /**
     * Number.prototype.toLocaleString()
     * ES2020 20.1.3.4 (simplified)
     */
    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asNumberWithDownCast()
                .map(jsNumber -> {
                    // Simplified: just use default toString
                    return (JSValue) new JSString(DtoaConverter.convert(jsNumber.value()));
                })
                .orElseGet(() -> context.throwTypeError("Number.prototype.toLocaleString requires that 'this' be a Number"));
    }

    /**
     * Number.prototype.toPrecision(precision)
     * ES2020 20.1.3.5
     */
    public static JSValue toPrecision(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asNumberWithDownCast()
                .map(jsNumber -> {
                    double value = jsNumber.value();
                    // If precision is undefined, use toString
                    if (args.length == 0 || args[0] instanceof JSUndefined) {
                        return new JSString(DtoaConverter.convert(value));
                    }
                    int precision = (int) JSTypeConversions.toInteger(context, args[0]);
                    // RangeError if out of bounds [1, 100]
                    if (precision < 1 || precision > DtoaConverter.MAX_PRECISION) {
                        return context.throwRangeError("toPrecision() precision must be between 1 and 100");
                    }
                    return new JSString(DtoaConverter.convertWithPrecision(value, precision));
                })
                .orElseGet(() -> context.throwTypeError("Number.prototype.toPrecision requires that 'this' be a Number"));
    }

    /**
     * Number.prototype.toString(radix)
     * ES2020 20.1.3.6
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asNumberWithDownCast()
                .map(jsNumber -> {
                    double value = jsNumber.value();
                    // Handle special cases
                    if (Double.isNaN(value)) {
                        return new JSString("NaN");
                    }
                    if (Double.isInfinite(value)) {
                        return new JSString(value > 0 ? "Infinity" : "-Infinity");
                    }
                    // Get radix (default 10)
                    int radix = args.length > 0 ? (int) JSTypeConversions.toInteger(context, args[0]) : 10;
                    // RangeError if radix out of bounds [2, 36]
                    if (radix < 2 || radix > 36) {
                        return context.throwRangeError("toString() radix must be between 2 and 36");
                    }
                    // For radix 10, use default conversion
                    if (radix == 10) {
                        return new JSString(DtoaConverter.convert(value));
                    }
                    // For other radixes, convert using the radix conversion method
                    return new JSString(DtoaConverter.convertToRadix(value, radix));
                })
                .orElseGet(() -> context.throwTypeError("Number.prototype.toString requires that 'this' be a Number"));
    }

    /**
     * Number.prototype.valueOf()
     * ES2020 20.1.3.7
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asNumberWithDownCast()
                .map(jsNumber -> (JSValue) jsNumber)
                .orElseGet(() -> context.throwTypeError("Number.prototype.valueOf requires that 'this' be a Number"));
    }
}
