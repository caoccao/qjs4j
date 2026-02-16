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

import java.math.BigInteger;

/**
 * JavaScript type conversion operations as defined in ECMAScript specification.
 * Based on QuickJS quickjs.c type conversion implementation.
 * <p>
 * Implements all abstract operations from ES2020:
 * - ToPrimitive
 * - ToBoolean
 * - ToNumber
 * - ToString
 * - ToObject
 * - ToInteger
 * - ToInt32, ToUint32
 * - ToLength
 */
public final class JSTypeConversions {

    /**
     * Abstract Equality Comparison (==).
     * ES2020 7.2.14
     */
    public static boolean abstractEquals(JSContext context, JSValue x, JSValue y) {
        // Same type comparison
        if (x.getClass() == y.getClass()) {
            return strictEquals(x, y);
        }

        // null == undefined
        if ((x.isNull() && y.isUndefined()) || (x.isUndefined() && y.isNull())) {
            return true;
        }

        // Number comparison
        if (x.isNumber() && y.isString()) {
            return abstractEquals(context, x, toNumber(context, y));
        }
        if (x.isString() && y.isNumber()) {
            return abstractEquals(context, toNumber(context, x), y);
        }

        // Boolean to number
        if (x.isBoolean()) {
            return abstractEquals(context, toNumber(context, x), y);
        }
        if (y.isBoolean()) {
            return abstractEquals(context, x, toNumber(context, y));
        }

        // Object to primitive conversion (ES2020 7.2.14)
        // If one is object and other is primitive, convert object to primitive
        if (!isPrimitive(x) && isPrimitive(y)) {
            return abstractEquals(context, toPrimitive(context, x, PreferredType.DEFAULT), y);
        }
        if (isPrimitive(x) && !isPrimitive(y)) {
            return abstractEquals(context, x, toPrimitive(context, y, PreferredType.DEFAULT));
        }

        // Annex B: IsHTMLDDA object is equivalent to null/undefined for == and !=.
        if (x instanceof JSObject xObj && xObj.isHTMLDDA() && y.isNullOrUndefined()) {
            return true;
        }
        return y instanceof JSObject yObj && yObj.isHTMLDDA() && x.isNullOrUndefined();
    }

    /**
     * Check if a value is primitive.
     */
    public static boolean isPrimitive(JSValue value) {
        return value instanceof JSUndefined ||
                value instanceof JSNull ||
                value instanceof JSBoolean ||
                value instanceof JSNumber ||
                value instanceof JSString ||
                value instanceof JSSymbol ||
                value instanceof JSBigInt;
    }

    /**
     * Less Than Comparison (x < y).
     * ES2020 7.2.13
     */
    public static boolean lessThan(JSContext context, JSValue x, JSValue y) {
        // Convert to primitives
        JSValue px = toPrimitive(context, x, PreferredType.NUMBER);
        JSValue py = toPrimitive(context, y, PreferredType.NUMBER);

        // If both are strings, compare lexicographically
        if (px instanceof JSString xStr && py instanceof JSString yStr) {
            return xStr.value().compareTo(yStr.value()) < 0;
        }

        // Otherwise, convert to numbers and compare
        double nx = toNumber(context, px).value();
        double ny = toNumber(context, py).value();

        // If either is NaN, return false
        if (Double.isNaN(nx) || Double.isNaN(ny)) {
            return false;
        }

        return nx < ny;
    }

    /**
     * Strict Equality Comparison (===).
     * ES2020 7.2.15
     */
    public static boolean strictEquals(JSValue x, JSValue y) {
        // Different types
        if (x.getClass() != y.getClass()) {
            return false;
        }

        // Undefined and null
        if (x instanceof JSUndefined || x instanceof JSNull) {
            return true;
        }

        // Numbers
        if (x instanceof JSNumber xNum && y instanceof JSNumber yNum) {
            double xVal = xNum.value();
            double yVal = yNum.value();

            // NaN is not equal to anything, including itself
            if (Double.isNaN(xVal) || Double.isNaN(yVal)) {
                return false;
            }

            return xVal == yVal;
        }

        // Strings
        if (x instanceof JSString xStr && y instanceof JSString yStr) {
            return xStr.value().equals(yStr.value());
        }

        // Booleans
        if (x instanceof JSBoolean xBool && y instanceof JSBoolean yBool) {
            return xBool == yBool;
        }

        // BigInts
        if (x instanceof JSBigInt xBigInt && y instanceof JSBigInt yBigInt) {
            return xBigInt.value().equals(yBigInt.value());
        }

        // Objects (reference equality)
        return x == y;
    }

    /**
     * Convert string to number following ES2020 rules.
     */
    private static JSNumber stringToNumber(String str) {
        // Trim whitespace
        str = str.strip();

        if (str.isEmpty()) {
            return JSNumber.of(0.0);
        }

        // Handle special values
        if (str.equals("Infinity") || str.equals("+Infinity")) {
            return JSNumber.of(Double.POSITIVE_INFINITY);
        }
        if (str.equals("-Infinity")) {
            return JSNumber.of(Double.NEGATIVE_INFINITY);
        }

        // Try to parse as number
        try {
            // Handle hex numbers
            if (str.startsWith("0x") || str.startsWith("0X")) {
                return JSNumber.of(Long.parseLong(str.substring(2), 16));
            }

            // Handle octal (legacy)
            if (str.startsWith("0") && str.length() > 1 && str.matches("0[0-7]+")) {
                return JSNumber.of(Long.parseLong(str, 8));
            }

            // Parse as decimal
            return JSNumber.of(Double.parseDouble(str));
        } catch (NumberFormatException e) {
            return JSNumber.of(Double.NaN);
        }
    }

    /**
     * ToBoolean(argument)
     * ES2020 7.1.2
     */
    public static JSBoolean toBoolean(JSValue value) {
        if (value instanceof JSUndefined) {
            return JSBoolean.FALSE;
        }
        if (value instanceof JSNull) {
            return JSBoolean.FALSE;
        }
        if (value instanceof JSBoolean b) {
            return b;
        }
        if (value instanceof JSNumber n) {
            return n.value() == 0.0 || Double.isNaN(n.value()) ? JSBoolean.FALSE : JSBoolean.TRUE;
        }
        if (value instanceof JSString s) {
            return s.value().isEmpty() ? JSBoolean.FALSE : JSBoolean.TRUE;
        }
        if (value instanceof JSBigInt b) {
            return b.value().equals(BigInteger.ZERO) ? JSBoolean.FALSE : JSBoolean.TRUE;
        }
        if (value instanceof JSSymbol) {
            return JSBoolean.TRUE; // Symbols are always truthy
        }
        if (value instanceof JSObject obj && obj.isHTMLDDA()) {
            return JSBoolean.FALSE;
        }
        // Objects are truthy
        return JSBoolean.TRUE;
    }

    /**
     * ToIndex(argument)
     * ES2020 7.1.23
     * Used for typed array indices
     */
    public static long toIndex(JSContext context, JSValue value) {
        if (value instanceof JSUndefined) {
            return 0;
        }

        double integerIndex = toInteger(context, value);
        if (integerIndex < 0) {
            throw new IllegalArgumentException("Index must be non-negative");
        }

        long index = (long) integerIndex;
        if (index != integerIndex) {
            throw new IllegalArgumentException("Index must be an integer");
        }

        return index;
    }

    /**
     * ToInt32(argument)
     * ES2020 7.1.6
     */
    public static int toInt32(JSContext context, JSValue value) {
        JSNumber number = toNumber(context, value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d) || Double.isInfinite(d) || d == 0.0) {
            return 0;
        }

        // ToInt32 uses modulo 2^32
        long int32bit = (long) d % 0x100000000L;
        if (int32bit >= 0x80000000L) {
            return (int) (int32bit - 0x100000000L);
        }
        return (int) int32bit;
    }

    /**
     * ToInt8(argument)
     * ES2020 7.1.9
     */
    public static byte toInt8(JSContext context, JSValue value) {
        JSNumber number = toNumber(context, value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d) || Double.isInfinite(d) || d == 0.0) {
            return 0;
        }

        // ToInt8 uses modulo 2^8
        int int8bit = (int) ((long) d % 0x100L);
        if (int8bit >= 0x80) {
            return (byte) (int8bit - 0x100);
        }
        return (byte) int8bit;
    }

    /**
     * ToInteger(argument)
     * ES2020 7.1.20 (ToIntegerOrInfinity)
     */
    public static double toInteger(JSContext context, JSValue value) {
        JSNumber number = toNumber(context, value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d)) {
            return 0.0;
        }
        if (d == 0.0 || Double.isInfinite(d)) {
            return d;
        }

        // Truncate towards zero
        return d > 0 ? Math.floor(d) : Math.ceil(d);
    }

    /**
     * ToLength(argument)
     * ES2020 7.1.22
     * Converts to integer suitable for array length (0 to 2^53-1)
     */
    public static long toLength(JSContext context, JSValue value) {
        double len = toInteger(context, value);

        if (len <= 0) {
            return 0;
        }

        // Maximum safe integer in JavaScript: 2^53 - 1
        return (long) Math.min(len, 0x1FFFFFFFFFFFFFL);
    }

    /**
     * ToNumber(argument)
     * ES2020 7.1.4
     */
    public static JSNumber toNumber(JSContext context, JSValue value) {
        if (value instanceof JSUndefined) {
            return JSNumber.of(Double.NaN);
        }
        if (value instanceof JSNull) {
            return JSNumber.of(0.0);
        }
        if (value instanceof JSBoolean b) {
            return JSNumber.of(b.value() ? 1.0 : 0.0);
        }
        if (value instanceof JSNumber n) {
            return n;
        }
        if (value instanceof JSString s) {
            return stringToNumber(s.value());
        }
        if (value instanceof JSBigInt b) {
            // BigInt cannot be converted to number without loss of precision
            // In full implementation, this would throw a TypeError
            try {
                return JSNumber.of(b.value().doubleValue());
            } catch (Exception e) {
                return JSNumber.of(Double.NaN);
            }
        }
        if (value instanceof JSSymbol) {
            context.throwTypeError("cannot convert symbol to number");
            return JSNumber.of(Double.NaN);
        }

        // Handle wrapper objects (String, Number, Boolean, BigInt objects)
        if (value instanceof JSObject obj) {
            JSValue primitiveValue = obj.getPrimitiveValue();
            if (primitiveValue instanceof JSNumber num) {
                return num;
            } else if (primitiveValue instanceof JSString str) {
                return stringToNumber(str.value());
            } else if (primitiveValue instanceof JSBoolean bool) {
                return JSNumber.of(bool.value() ? 1.0 : 0.0);
            } else if (primitiveValue instanceof JSBigInt bigInt) {
                // BigInt cannot be converted to number without loss of precision
                try {
                    return JSNumber.of(bigInt.value().doubleValue());
                } catch (Exception e) {
                    return JSNumber.of(Double.NaN);
                }
            }
        }

        // For objects, call ToPrimitive with NUMBER hint
        JSValue primitive = toPrimitive(context, value, PreferredType.NUMBER);
        if (primitive == value) {
            return JSNumber.of(Double.NaN);
        }
        return toNumber(context, primitive);
    }

    /**
     * ToPrimitive(input, preferredType)
     * ES2020 7.1.1
     */
    public static JSValue toPrimitive(JSContext context, JSValue input, PreferredType hint) {
        // If input is already primitive, return it
        if (isPrimitive(input)) {
            return input;
        }

        // For objects, call ToPrimitive with hint
        if (input instanceof JSObject obj) {
            // IsHTMLDDA objects emulate undefined - return undefined for ToPrimitive
            if (obj.isHTMLDDA()) {
                return JSUndefined.INSTANCE;
            }

            // Check for [[PrimitiveValue]] internal slot (wrapper objects)
            JSValue primitiveValue = obj.getPrimitiveValue();
            if (primitiveValue != null && !(primitiveValue instanceof JSUndefined)) {
                return primitiveValue;
            }

            JSValue toPrimitiveMethod = obj.get(PropertyKey.fromSymbol(JSSymbol.TO_PRIMITIVE), context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // QuickJS compatibility: treat null like undefined for @@toPrimitive presence check.
            if (!(toPrimitiveMethod instanceof JSUndefined) && !(toPrimitiveMethod instanceof JSNull)) {
                if (!(toPrimitiveMethod instanceof JSFunction toPrimitiveFunction)) {
                    context.throwTypeError("toPrimitive");
                    return JSUndefined.INSTANCE;
                }

                String hintString = switch (hint) {
                    case STRING -> "string";
                    case NUMBER -> "number";
                    case DEFAULT -> "default";
                };
                JSValue result = toPrimitiveFunction.call(context, obj, new JSValue[]{new JSString(hintString)});
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (isPrimitive(result)) {
                    return result;
                }
                context.throwTypeError("toPrimitive");
                return JSUndefined.INSTANCE;
            }

            // OrdinaryToPrimitive: DEFAULT behaves like NUMBER.
            String[] methodNames = hint == PreferredType.STRING
                    ? new String[]{"toString", "valueOf"}
                    : new String[]{"valueOf", "toString"};

            for (String methodName : methodNames) {
                JSValue method = obj.get(PropertyKey.fromString(methodName), context);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (method instanceof JSFunction func) {
                    JSValue result = func.call(context, obj, new JSValue[0]);
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    if (isPrimitive(result)) {
                        return result;
                    }
                }
            }
            context.throwTypeError("toPrimitive");
            return JSUndefined.INSTANCE;
        }

        return input;
    }

    /**
     * ToString(argument)
     * ES2020 7.1.17
     */
    public static JSString toString(JSContext context, JSValue value) {
        if (value == null) {
            return new JSString("null");
        } else if (value.isNullOrUndefined()
                || value.isBigInt()
                || value.isBigIntObject()
                || value.isBoolean()
                || value.isBooleanObject()
                || value.isNumber()
                || value.isNumberObject()
                || value.isTypedArray()) {
            return new JSString(value.toString());
        } else if (value instanceof JSString s) {
            return s;
        } else if (value instanceof JSStringObject s) {
            return s.getValue();
        } else if (value instanceof JSSymbol s) {
            return new JSString(s.toString(context));
        }

        // Handle wrapper objects (String, Number, Boolean, BigInt objects)
        if (value instanceof JSObject obj) {
            JSValue primitiveValue = obj.getPrimitiveValue();
            if (primitiveValue instanceof JSString str) {
                return str;
            } else if (primitiveValue instanceof JSNumber num) {
                return new JSString(num.toString());
            } else if (primitiveValue instanceof JSBoolean bool) {
                return new JSString(bool.toString());
            } else if (primitiveValue instanceof JSBigInt bigInt) {
                return new JSString(bigInt.toString());
            }
        }

        // For objects, call ToPrimitive with STRING hint
        JSValue primitive = toPrimitive(context, value, PreferredType.STRING);
        if (primitive == value) {
            return new JSString("[object Object]");
        }
        return toString(context, primitive);
    }

    /**
     * ToUint16(argument)
     * ES2020 7.1.8
     */
    public static int toUint16(JSContext context, JSValue value) {
        JSNumber number = toNumber(context, value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d) || Double.isInfinite(d) || d == 0.0) {
            return 0;
        }

        // ToUint16 uses modulo 2^16
        return (int) ((long) d % 0x10000L);
    }

    /**
     * ToUint32(argument)
     * ES2020 7.1.7
     */
    public static long toUint32(JSContext context, JSValue value) {
        JSNumber number = toNumber(context, value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d) || Double.isInfinite(d) || d == 0.0) {
            return 0;
        }

        // ToUint32 uses modulo 2^32
        return (long) d % 0x100000000L;
    }

    /**
     * ToUint8(argument)
     * ES2020 7.1.10
     */
    public static short toUint8(JSContext context, JSValue value) {
        JSNumber number = toNumber(context, value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d) || Double.isInfinite(d) || d == 0.0) {
            return 0;
        }

        // ToUint8 uses modulo 2^8
        return (short) ((long) d % 0x100L);
    }

    /**
     * Preferred type for ToPrimitive operation.
     */
    public enum PreferredType {
        NUMBER,
        STRING,
        DEFAULT
    }
}
