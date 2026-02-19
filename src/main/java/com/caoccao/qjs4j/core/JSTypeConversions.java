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

import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

import java.math.BigDecimal;
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
        if (x.isBigInt() && y.isString()) {
            try {
                return strictEquals(x, stringToBigInt(((JSString) y).value()));
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (x.isString() && y.isBigInt()) {
            try {
                return strictEquals(stringToBigInt(((JSString) x).value()), y);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (x instanceof JSNumber xNumber && y instanceof JSBigInt yBigInt) {
            return numberEqualsBigInt(xNumber.value(), yBigInt.value());
        }
        if (x instanceof JSBigInt xBigInt && y instanceof JSNumber yNumber) {
            return numberEqualsBigInt(yNumber.value(), xBigInt.value());
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

    private static int compareBigIntAndNumber(BigInteger bigInt, double number) {
        if (Double.isNaN(number)) {
            return 1;
        }
        if (number == Double.POSITIVE_INFINITY) {
            return -1;
        }
        if (number == Double.NEGATIVE_INFINITY) {
            return 1;
        }
        return new BigDecimal(bigInt).compareTo(BigDecimal.valueOf(number));
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

    private static boolean isValidBigIntDigits(String digits, int radix) {
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            boolean valid = switch (radix) {
                case 2 -> c == '0' || c == '1';
                case 8 -> c >= '0' && c <= '7';
                case 10 -> c >= '0' && c <= '9';
                case 16 -> (c >= '0' && c <= '9') ||
                        (c >= 'a' && c <= 'f') ||
                        (c >= 'A' && c <= 'F');
                default -> false;
            };
            if (!valid) {
                return false;
            }
        }
        return true;
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

        if (px instanceof JSBigInt xBigInt) {
            if (py instanceof JSBigInt yBigInt) {
                return xBigInt.value().compareTo(yBigInt.value()) < 0;
            }
            double ny = py instanceof JSNumber n ? n.value() : toNumber(context, py).value();
            if (Double.isNaN(ny)) {
                return false;
            }
            return compareBigIntAndNumber(xBigInt.value(), ny) < 0;
        }
        if (py instanceof JSBigInt yBigInt) {
            double nx = px instanceof JSNumber n ? n.value() : toNumber(context, px).value();
            if (Double.isNaN(nx)) {
                return false;
            }
            return compareBigIntAndNumber(yBigInt.value(), nx) > 0;
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

    private static boolean numberEqualsBigInt(double number, BigInteger bigInt) {
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return false;
        }
        if (Math.rint(number) != number) {
            return false;
        }
        return compareBigIntAndNumber(bigInt, number) == 0;
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
     * Convert string to BigInt following QuickJS JS_StringToBigInt semantics.
     * Empty/whitespace-only strings convert to 0n.
     * Decimal strings allow an optional leading sign.
     * Binary/octal/hex prefixes do not allow a leading sign.
     *
     * @throws NumberFormatException if the string is not a valid BigInt literal
     */
    public static JSBigInt stringToBigInt(String value) {
        String text = value.strip();
        if (text.isEmpty()) {
            return new JSBigInt(BigInteger.ZERO);
        }

        int sign = 1;
        int start = 0;
        boolean hasSign = false;
        char first = text.charAt(0);
        if (first == '+' || first == '-') {
            hasSign = true;
            sign = first == '-' ? -1 : 1;
            start = 1;
            if (start >= text.length()) {
                throw new NumberFormatException("Missing digits");
            }
        }

        int radix = 10;
        int digitsStart = start;
        if (text.length() - start >= 2 && text.charAt(start) == '0') {
            char prefix = text.charAt(start + 1);
            if (prefix == 'x' || prefix == 'X' || prefix == 'o' || prefix == 'O' || prefix == 'b' || prefix == 'B') {
                if (hasSign) {
                    throw new NumberFormatException("Signed non-decimal BigInt is not allowed");
                }
                digitsStart = start + 2;
                radix = switch (prefix) {
                    case 'x', 'X' -> 16;
                    case 'o', 'O' -> 8;
                    default -> 2;
                };
            }
        }

        if (digitsStart >= text.length()) {
            throw new NumberFormatException("Missing digits");
        }
        String digits = text.substring(digitsStart);
        if (!isValidBigIntDigits(digits, radix)) {
            throw new NumberFormatException("Invalid digits");
        }

        BigInteger bigInteger = new BigInteger(digits, radix);
        if (sign < 0) {
            bigInteger = bigInteger.negate();
        }
        return new JSBigInt(bigInteger);
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
     * ToBigInt(value)
     * ES2020 7.1.13
     * Converts a value to a BigInt.
     */
    public static JSBigInt toBigInt(JSContext context, JSValue value) {
        if (value instanceof JSBigInt bigInt) {
            return bigInt;
        }
        if (value instanceof JSBigIntObject bigIntObject) {
            return bigIntObject.getValue();
        }
        if (value instanceof JSBoolean booleanValue) {
            return new JSBigInt(booleanValue.value() ? 1L : 0L);
        }
        if (value instanceof JSString stringValue) {
            try {
                return stringToBigInt(stringValue.value());
            } catch (NumberFormatException e) {
                throw new JSSyntaxErrorException(
                        "Cannot convert " + stringValue.value() + " to a BigInt");
            }
        }
        if (value instanceof JSObject objectValue) {
            JSValue primitive = toPrimitive(context, objectValue, PreferredType.NUMBER);
            return toBigInt(context, primitive);
        }
        throw new JSTypeErrorException(
                "Cannot convert " + value + " to a BigInt");
    }

    /**
     * ToBigInt64(value)
     * Converts a value to BigInt, then truncates to int64 (mod 2^64).
     * Following QuickJS JS_ToBigInt64Free.
     */
    public static long toBigInt64(JSContext context, JSValue value) {
        JSBigInt bigInt = toBigInt(context, value);
        return bigInt.value().longValue(); // longValue() gives the low 64 bits
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
        // Following QuickJS JS_ToIndex: saturate to int64 range then check bounds
        long v;
        if (Double.isNaN(integerIndex)) {
            v = 0;
        } else if (integerIndex < Long.MIN_VALUE) {
            v = Long.MIN_VALUE;
        } else if (integerIndex >= 0x1p63) {
            v = Long.MAX_VALUE;
        } else {
            v = (long) integerIndex;
        }
        if (v < 0 || v > 0x1FFFFFFFFFFFFFL) { // MAX_SAFE_INTEGER = 2^53 - 1
            throw new JSRangeErrorException("invalid array index");
        }

        return v;
    }

    /**
     * ToInt32(argument)
     * ES2020 7.1.6
     */
    public static int toInt32(JSContext context, JSValue value) {
        JSNumber number = toNumber(context, value);
        return toInt32(number.value());
    }

    /**
     * ToInt32 for a primitive double value.
     * Mirrors QuickJS JS_ToInt32Free() bit-level conversion semantics.
     */
    public static int toInt32(double d) {
        long bits = Double.doubleToRawLongBits(d);
        int e = (int) ((bits >>> 52) & 0x7ff);

        if (e <= (1023 + 30)) {
            return (int) d;
        } else if (e <= (1023 + 30 + 53)) {
            long v = (bits & ((1L << 52) - 1)) | (1L << 52);
            v = v << ((e - 1023) - 52 + 32);
            int result = (int) (v >>> 32);
            if ((bits >>> 63) != 0) {
                result = -result;
            }
            return result;
        }
        // Includes NaN and infinities.
        return 0;
    }

    /**
     * ToInt8(argument)
     * ES2020 7.1.9
     */
    public static byte toInt8(JSContext context, JSValue value) {
        return (byte) toInt32(context, value);
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
        if (value instanceof JSBigInt) {
            // Per ES spec, ToNumber(BigInt) throws TypeError
            if (context != null) {
                context.throwTypeError("Cannot convert a BigInt value to a number");
            }
            return JSNumber.of(Double.NaN);
        }
        if (value instanceof JSSymbol) {
            if (context != null) {
                context.throwTypeError("cannot convert symbol to number");
            }
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
            } else if (primitiveValue instanceof JSBigInt) {
                // Per ES spec, ToNumber(BigInt) throws TypeError
                if (context != null) {
                    context.throwTypeError("Cannot convert a BigInt value to a number");
                }
                return JSNumber.of(Double.NaN);
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
     * ToObject(argument)
     * ES2024 7.1.18
     * Converts a value to an Object by auto-boxing primitives.
     * Returns null for null/undefined.
     */
    public static JSObject toObject(JSContext context, JSValue value) {
        if (value instanceof JSObject jsObj) {
            return jsObj;
        }
        if (value instanceof JSNull || value instanceof JSUndefined) {
            return null;
        }
        JSObject global = context.getGlobalObject();
        if (value instanceof JSString str) {
            JSValue stringCtor = global.get("String");
            if (stringCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSObject wrapper = new JSObject();
                    wrapper.setPrototype(protoObj);
                    wrapper.setPrimitiveValue(str);
                    wrapper.definePropertyReadonlyNonConfigurable("length", JSNumber.of(str.value().length()));
                    return wrapper;
                }
            }
        }
        if (value instanceof JSNumber num) {
            JSValue numberCtor = global.get("Number");
            if (numberCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSObject wrapper = new JSObject();
                    wrapper.setPrototype(protoObj);
                    wrapper.setPrimitiveValue(num);
                    return wrapper;
                }
            }
        }
        if (value instanceof JSBoolean bool) {
            JSValue booleanCtor = global.get("Boolean");
            if (booleanCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSObject wrapper = new JSObject();
                    wrapper.setPrototype(protoObj);
                    wrapper.setPrimitiveValue(bool);
                    return wrapper;
                }
            }
        }
        if (value instanceof JSBigInt bigInt) {
            JSValue bigIntCtor = global.get("BigInt");
            if (bigIntCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSBigIntObject wrapper = new JSBigIntObject(bigInt);
                    wrapper.setPrototype(protoObj);
                    return wrapper;
                }
            }
        }
        if (value instanceof JSSymbol sym) {
            JSValue symbolCtor = global.get("Symbol");
            if (symbolCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSSymbolObject wrapper = new JSSymbolObject(sym);
                    wrapper.setPrototype(protoObj);
                    return wrapper;
                }
            }
        }
        return null;
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

            JSValue toPrimitiveMethod = obj.get(PropertyKey.SYMBOL_TO_PRIMITIVE, context);
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
        return toInt32(context, value) & 0xFFFF;
    }

    /**
     * ToUint32(argument)
     * ES2020 7.1.7
     */
    public static long toUint32(JSContext context, JSValue value) {
        return Integer.toUnsignedLong(toInt32(context, value));
    }

    /**
     * ToUint8(argument)
     * ES2020 7.1.10
     */
    public static short toUint8(JSContext context, JSValue value) {
        return (short) (toInt32(context, value) & 0xFF);
    }

    /**
     * ToUint8Clamp for a primitive double value.
     * Mirrors QuickJS JS_ToUint8ClampFree() semantics (ties-to-even).
     */
    public static int toUint8Clamp(double d) {
        if (Double.isNaN(d)) {
            return 0;
        }
        if (d <= 0) {
            return 0;
        }
        if (d >= 255) {
            return 255;
        }
        return (int) Math.rint(d);
    }

    /**
     * ToUint8Clamp(value)
     * ES operation used by Uint8ClampedArray element writes.
     */
    public static int toUint8Clamp(JSContext context, JSValue value) {
        return toUint8Clamp(toNumber(context, value).value());
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
