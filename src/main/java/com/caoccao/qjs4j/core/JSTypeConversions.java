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

import com.caoccao.qjs4j.util.DtoaConverter;

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
     * ToPrimitive(input, preferredType)
     * ES2020 7.1.1
     */
    public static JSValue toPrimitive(JSValue input, PreferredType hint) {
        // If input is already primitive, return it
        if (isPrimitive(input)) {
            return input;
        }

        // For objects, call ToPrimitive with hint
        if (input instanceof JSObject obj) {
            // In a full implementation, this would call [[DefaultValue]]
            // For now, return a placeholder
            if (hint == PreferredType.STRING) {
                return toString(input);
            } else {
                return toNumber(input);
            }
        }

        return input;
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
        // Objects are truthy
        return JSBoolean.TRUE;
    }

    /**
     * ToNumber(argument)
     * ES2020 7.1.4
     */
    public static JSNumber toNumber(JSValue value) {
        if (value instanceof JSUndefined) {
            return new JSNumber(Double.NaN);
        }
        if (value instanceof JSNull) {
            return new JSNumber(0.0);
        }
        if (value instanceof JSBoolean b) {
            return new JSNumber(b.value() ? 1.0 : 0.0);
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
                return new JSNumber(b.value().doubleValue());
            } catch (Exception e) {
                return new JSNumber(Double.NaN);
            }
        }
        if (value instanceof JSSymbol) {
            // Symbols cannot be converted to numbers - would throw TypeError
            return new JSNumber(Double.NaN);
        }
        // For objects, call ToPrimitive with NUMBER hint
        JSValue primitive = toPrimitive(value, PreferredType.NUMBER);
        if (primitive == value) {
            return new JSNumber(Double.NaN);
        }
        return toNumber(primitive);
    }

    /**
     * ToString(argument)
     * ES2020 7.1.17
     */
    public static JSString toString(JSValue value) {
        if (value instanceof JSUndefined) {
            return new JSString("undefined");
        }
        if (value instanceof JSNull) {
            return new JSString("null");
        }
        if (value instanceof JSBoolean b) {
            return new JSString(b.value() ? "true" : "false");
        }
        if (value instanceof JSNumber n) {
            return new JSString(DtoaConverter.convert(n.value()));
        }
        if (value instanceof JSString s) {
            return s;
        }
        if (value instanceof JSBigInt b) {
            return new JSString(b.value().toString());
        }
        if (value instanceof JSSymbol s) {
            // Symbols cannot be converted to strings - would throw TypeError
            // For now, return the description
            return new JSString("Symbol(" + (s.getDescription() != null ? s.getDescription() : "") + ")");
        }
        // For objects, call ToPrimitive with STRING hint
        JSValue primitive = toPrimitive(value, PreferredType.STRING);
        if (primitive == value) {
            return new JSString("[object Object]");
        }
        return toString(primitive);
    }

    /**
     * ToInteger(argument)
     * ES2020 7.1.20 (ToIntegerOrInfinity)
     */
    public static double toInteger(JSValue value) {
        JSNumber number = toNumber(value);
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
     * ToInt32(argument)
     * ES2020 7.1.6
     */
    public static int toInt32(JSValue value) {
        JSNumber number = toNumber(value);
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
     * ToUint32(argument)
     * ES2020 7.1.7
     */
    public static long toUint32(JSValue value) {
        JSNumber number = toNumber(value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d) || Double.isInfinite(d) || d == 0.0) {
            return 0;
        }

        // ToUint32 uses modulo 2^32
        return (long) d % 0x100000000L;
    }

    /**
     * ToUint16(argument)
     * ES2020 7.1.8
     */
    public static int toUint16(JSValue value) {
        JSNumber number = toNumber(value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d) || Double.isInfinite(d) || d == 0.0) {
            return 0;
        }

        // ToUint16 uses modulo 2^16
        return (int) ((long) d % 0x10000L);
    }

    /**
     * ToInt8(argument)
     * ES2020 7.1.9
     */
    public static byte toInt8(JSValue value) {
        JSNumber number = toNumber(value);
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
     * ToUint8(argument)
     * ES2020 7.1.10
     */
    public static short toUint8(JSValue value) {
        JSNumber number = toNumber(value);
        double d = number.value();

        // Handle special cases
        if (Double.isNaN(d) || Double.isInfinite(d) || d == 0.0) {
            return 0;
        }

        // ToUint8 uses modulo 2^8
        return (short) ((long) d % 0x100L);
    }

    /**
     * ToLength(argument)
     * ES2020 7.1.22
     * Converts to integer suitable for array length (0 to 2^53-1)
     */
    public static long toLength(JSValue value) {
        double len = toInteger(value);

        if (len <= 0) {
            return 0;
        }

        // Maximum safe integer in JavaScript: 2^53 - 1
        return (long) Math.min(len, 0x1FFFFFFFFFFFFFL);
    }

    /**
     * ToIndex(argument)
     * ES2020 7.1.23
     * Used for typed array indices
     */
    public static long toIndex(JSValue value) {
        if (value instanceof JSUndefined) {
            return 0;
        }

        double integerIndex = toInteger(value);
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
     * Convert string to number following ES2020 rules.
     */
    private static JSNumber stringToNumber(String str) {
        // Trim whitespace
        str = str.strip();

        if (str.isEmpty()) {
            return new JSNumber(0.0);
        }

        // Handle special values
        if (str.equals("Infinity") || str.equals("+Infinity")) {
            return new JSNumber(Double.POSITIVE_INFINITY);
        }
        if (str.equals("-Infinity")) {
            return new JSNumber(Double.NEGATIVE_INFINITY);
        }

        // Try to parse as number
        try {
            // Handle hex numbers
            if (str.startsWith("0x") || str.startsWith("0X")) {
                return new JSNumber(Long.parseLong(str.substring(2), 16));
            }

            // Handle octal (legacy)
            if (str.startsWith("0") && str.length() > 1 && str.matches("0[0-7]+")) {
                return new JSNumber(Long.parseLong(str, 8));
            }

            // Parse as decimal
            return new JSNumber(Double.parseDouble(str));
        } catch (NumberFormatException e) {
            return new JSNumber(Double.NaN);
        }
    }

    /**
     * Abstract Equality Comparison (==).
     * ES2020 7.2.14
     */
    public static boolean abstractEquals(JSValue x, JSValue y) {
        // Same type comparison
        if (x.getClass() == y.getClass()) {
            return strictEquals(x, y);
        }

        // null == undefined
        if ((x instanceof JSNull && y instanceof JSUndefined) ||
                (x instanceof JSUndefined && y instanceof JSNull)) {
            return true;
        }

        // Number comparison
        if (x instanceof JSNumber && y instanceof JSString) {
            return abstractEquals(x, toNumber(y));
        }
        if (x instanceof JSString && y instanceof JSNumber) {
            return abstractEquals(toNumber(x), y);
        }

        // Boolean to number
        if (x instanceof JSBoolean) {
            return abstractEquals(toNumber(x), y);
        }
        if (y instanceof JSBoolean) {
            return abstractEquals(x, toNumber(y));
        }

        return false;
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

        // Objects (reference equality)
        return x == y;
    }

    /**
     * Less Than Comparison (x < y).
     * ES2020 7.2.13
     */
    public static boolean lessThan(JSValue x, JSValue y) {
        // Convert to primitives
        JSValue px = toPrimitive(x, PreferredType.NUMBER);
        JSValue py = toPrimitive(y, PreferredType.NUMBER);

        // If both are strings, compare lexicographically
        if (px instanceof JSString xStr && py instanceof JSString yStr) {
            return xStr.value().compareTo(yStr.value()) < 0;
        }

        // Otherwise, convert to numbers and compare
        double nx = toNumber(px).value();
        double ny = toNumber(py).value();

        // If either is NaN, return false
        if (Double.isNaN(nx) || Double.isNaN(ny)) {
            return false;
        }

        return nx < ny;
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
