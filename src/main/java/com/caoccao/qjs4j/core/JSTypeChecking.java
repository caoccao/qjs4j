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

/**
 * Type checking utilities for JavaScript values.
 * Based on QuickJS quickjs.c type checking macros.
 * <p>
 * Provides fast type checking predicates for all JavaScript value types.
 */
public final class JSTypeChecking {

    // Primitive type checks

    /**
     * Get the JavaScript type name of a value.
     * Matches the behavior of JavaScript's typeof operator.
     */
    public static String getTypeName(JSValue value) {
        if (value instanceof JSUndefined) {
            return "undefined";
        }
        if (value instanceof JSNull) {
            // Note: typeof null === "object" in JavaScript (historical bug)
            return "object";
        }
        if (value instanceof JSBoolean) {
            return "boolean";
        }
        if (value instanceof JSNumber) {
            return "number";
        }
        if (value instanceof JSString) {
            return "string";
        }
        if (value instanceof JSSymbol) {
            return "symbol";
        }
        if (value instanceof JSBigInt) {
            return "bigint";
        }
        if (value instanceof JSFunction) {
            return "function";
        }
        if (value instanceof JSObject) {
            return "object";
        }
        return "unknown";
    }

    /**
     * Check if value is an array.
     */
    public static boolean isArray(JSValue value) {
        return value instanceof JSArray;
    }

    /**
     * Check if value is a BigInt.
     */
    public static boolean isBigInt(JSValue value) {
        return value instanceof JSBigInt;
    }

    /**
     * Check if value is a boolean.
     */
    public static boolean isBoolean(JSValue value) {
        return value instanceof JSBoolean;
    }

    /**
     * Check if value is callable (can be invoked as a function).
     */
    public static boolean isCallable(JSValue value) {
        return value instanceof JSFunction;
    }

    /**
     * Check if value is a constructor (can be used with new).
     */
    public static boolean isConstructor(JSValue value) {
        // Check if function has [[Construct]] internal method
        if (value instanceof JSBytecodeFunction bytecodeFunc) {
            return bytecodeFunc.isConstructor();
        } else if (value instanceof JSNativeFunction nativeFunc) {
            return nativeFunc.isConstructor();
        } else // Other function types (bound functions) default to true
            if (value instanceof JSClass) {
                return true;
            } else return value instanceof JSFunction;
    }

    /**
     * Check if value is falsy (converts to false in boolean context).
     */
    public static boolean isFalsy(JSValue value) {
        return !isTruthy(value);
    }

    /**
     * Check if value is finite (not NaN, not Infinity).
     */
    public static boolean isFinite(JSValue value) {
        return value instanceof JSNumber n && Double.isFinite(n.value());
    }

    /**
     * Check if value is a function.
     */
    public static boolean isFunction(JSValue value) {
        return value instanceof JSFunction;
    }

    /**
     * Check if value is an integer.
     */
    public static boolean isInteger(JSValue value) {
        if (!(value instanceof JSNumber n)) {
            return false;
        }
        double d = n.value();
        return Double.isFinite(d) && d == Math.floor(d);
    }

    /**
     * Check if value is NaN.
     */
    public static boolean isNaN(JSValue value) {
        return value instanceof JSNumber n && Double.isNaN(n.value());
    }

    // Composite checks

    /**
     * Check if value is null.
     */
    public static boolean isNull(JSValue value) {
        return value instanceof JSNull;
    }

    /**
     * Check if value is null or undefined (nullish).
     */
    public static boolean isNullish(JSValue value) {
        return value instanceof JSNull || value instanceof JSUndefined;
    }

    /**
     * Check if value is a number.
     */
    public static boolean isNumber(JSValue value) {
        return value instanceof JSNumber;
    }

    // Number-specific checks

    /**
     * Check if value is an object (including functions, arrays).
     */
    public static boolean isObject(JSValue value) {
        return value instanceof JSObject;
    }

    /**
     * Check if value is a primitive (not an object).
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
     * Check if value is a safe integer (-(2^53 - 1) to 2^53 - 1).
     */
    public static boolean isSafeInteger(JSValue value) {
        if (!(value instanceof JSNumber n)) {
            return false;
        }
        double d = n.value();
        return Double.isFinite(d) &&
                d == Math.floor(d) &&
                Math.abs(d) <= 0x1FFFFFFFFFFFFFL; // 2^53 - 1
    }

    /**
     * Check if two values have the same type.
     */
    public static boolean isSameType(JSValue a, JSValue b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.type() == b.type();
    }

    // Boolean value checks

    /**
     * Check if value is a string.
     */
    public static boolean isString(JSValue value) {
        return value instanceof JSString;
    }

    /**
     * Check if value is a symbol.
     */
    public static boolean isSymbol(JSValue value) {
        return value instanceof JSSymbol;
    }

    // Type equality checks

    /**
     * Check if value is truthy (converts to true in boolean context).
     */
    public static boolean isTruthy(JSValue value) {
        return JSTypeConversions.toBoolean(value).value();
    }

    /**
     * Check if value is undefined.
     */
    public static boolean isUndefined(JSValue value) {
        return value instanceof JSUndefined;
    }

    // Validation helpers

    /**
     * Require that value is a function.
     */
    public static JSFunction requireFunction(JSValue value) {
        return requireType(value, JSFunction.class, "Expected function");
    }

    /**
     * Require that value is not null or undefined.
     * Throws if value is nullish.
     */
    public static JSValue requireNotNullish(JSValue value, String message) {
        if (isNullish(value)) {
            throw new IllegalArgumentException(message != null ? message : "Value cannot be null or undefined");
        }
        return value;
    }

    /**
     * Require that value is a number.
     */
    public static JSNumber requireNumber(JSValue value) {
        return requireType(value, JSNumber.class, "Expected number");
    }

    /**
     * Require that value is an object.
     */
    public static JSObject requireObject(JSValue value) {
        return requireType(value, JSObject.class, "Expected object");
    }

    /**
     * Require that value is a string.
     */
    public static JSString requireString(JSValue value) {
        return requireType(value, JSString.class, "Expected string");
    }

    /**
     * Require that value is of a specific type.
     * Throws if value is not of the expected type.
     */
    public static <T extends JSValue> T requireType(JSValue value, Class<T> expectedType, String message) {
        if (!expectedType.isInstance(value)) {
            String msg = message != null ? message :
                    "Expected " + expectedType.getSimpleName() + " but got " + getTypeName(value);
            throw new IllegalArgumentException(msg);
        }
        return expectedType.cast(value);
    }

    /**
     * Get the typeof string for a value.
     * ES2020 13.5.3
     */
    public static String typeof(JSValue value) {
        if (value instanceof JSUndefined) {
            return "undefined";
        }
        if (value instanceof JSNull) {
            return "object"; // typeof null === "object" (historical quirk)
        }
        if (value instanceof JSBoolean) {
            return "boolean";
        }
        if (value instanceof JSNumber) {
            return "number";
        }
        if (value instanceof JSString) {
            return "string";
        }
        if (value instanceof JSSymbol) {
            return "symbol";
        }
        if (value instanceof JSBigInt) {
            return "bigint";
        }
        if (value instanceof JSObject obj) {
            if (obj.isHTMLDDA()) {
                return "undefined";
            }
            if (value instanceof JSFunction) {
                return "function";
            }
            return "object";
        }
        return "undefined";
    }
}
