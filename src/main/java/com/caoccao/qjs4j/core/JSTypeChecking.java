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

import com.caoccao.qjs4j.builtins.NumberPrototype;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

/**
 * Type checking utilities for JavaScript values.
 * Based on QuickJS quickjs.c type checking macros.
 * <p>
 * Provides fast type checking predicates for all JavaScript value types.
 */
public final class JSTypeChecking {

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
        if (isFunction(value)) {
            return "function";
        }
        if (value instanceof JSObject) {
            return "object";
        }
        return "unknown";
    }

    /**
     * ES2024 7.2.2 IsArray(argument).
     * Unwraps Proxy chains to check the target, following QuickJS JS_IsArray.
     * Throws TypeError on revoked proxies via context.
     *
     * @return 1 if array, 0 if not, -1 if exception (revoked proxy)
     */
    public static int isArray(JSContext context, JSValue value) {
        JSValue hare = value;
        JSValue tortoise = value;
        boolean advanceTortoise = false;
        while (hare instanceof JSProxy proxy) {
            if (proxy.isRevoked()) {
                context.throwTypeError("Cannot perform 'isArray' on a proxy that has been revoked");
                return -1;
            }
            hare = proxy.getTarget();
            if (advanceTortoise && tortoise instanceof JSProxy tortoiseProxy) {
                tortoise = tortoiseProxy.getTarget();
            }
            advanceTortoise = !advanceTortoise;
            if (hare == tortoise) {
                // Only reachable through the raw embedder API; the Proxy constructor cannot make
                // a cycle. Reported rather than spun on.
                context.throwTypeError("Cyclic proxy chain");
                return -1;
            }
        }
        return (hare instanceof JSObject obj && obj.isArrayObject()) ? 1 : 0;
    }

    // Primitive type checks

    /**
     * Check if value is an array. Simple version without context
     * that cannot throw on revoked proxies.
     */
    public static boolean isArray(JSValue value) {
        if (value instanceof JSObject obj && obj.isArrayObject()) {
            return true;
        }
        return unwrapTargets(value, false) instanceof JSObject target && target.isArrayObject();
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
        return isFunction(value);
    }

    /**
     * Check if value is a constructor (can be used with new).
     */
    public static boolean isConstructor(JSValue value) {
        // [[Construct]] follows both a Proxy's target and a bound function's target, and either
        // can nest arbitrarily, so the unwrap is a loop rather than two mutual recursions.
        JSValue target = unwrapTargets(value, true);
        if (target instanceof JSBytecodeFunction bytecodeFunction) {
            return bytecodeFunction.isConstructor();
        }
        if (target instanceof JSNativeFunction nativeFunction) {
            return nativeFunction.isConstructor();
        }
        // Other function types default to constructor-capable.
        if (target instanceof JSClass) {
            return true;
        }
        return target instanceof JSFunction;
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
        if (value instanceof JSFunction) {
            return true;
        }
        return unwrapTargets(value, false) instanceof JSFunction;
    }

    /**
     * Check if value is an integer.
     */
    public static boolean isInteger(JSValue value) {
        if (!(value instanceof JSNumber n)) {
            return false;
        }
        double doubleValue = n.value();
        return Double.isFinite(doubleValue) && doubleValue == Math.floor(doubleValue);
    }

    /**
     * Check if value is NaN.
     */
    public static boolean isNaN(JSValue value) {
        return value instanceof JSNumber n && Double.isNaN(n.value());
    }

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

    // Composite checks

    /**
     * Check if value is a number.
     */
    public static boolean isNumber(JSValue value) {
        return value instanceof JSNumber;
    }

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

    // Number-specific checks

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
                Math.abs(d) <= NumberPrototype.MAX_SAFE_INTEGER;
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
     * One step of {@link #unwrapTargets(JSValue, boolean)}.
     *
     * @param value                the current value
     * @param unwrapBoundFunctions true to also follow {@code [[BoundTargetFunction]]}
     * @return the next value, or {@code null} when this one is the end of the chain
     */
    private static JSValue nextTarget(JSValue value, boolean unwrapBoundFunctions) {
        if (value instanceof JSProxy proxy) {
            return proxy.getTarget();
        }
        if (unwrapBoundFunctions && value instanceof JSBoundFunction boundFunction) {
            return boundFunction.getTarget();
        }
        return null;
    }

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
            throw new JSTypeErrorException(message != null ? message : "Value cannot be null or undefined");
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
            throw new JSTypeErrorException(msg);
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
            if (isFunction(value)) {
                return "function";
            }
            return "object";
        }
        return "undefined";
    }

    /**
     * Follow a chain of {@code Proxy} targets — and optionally bound-function targets — to the
     * value at the end of it.
     * <p>
     * Iterative, and with no depth cutoff. Classification used to recurse with a limit of 1,000
     * and then answer {@code false}, so wrapping a function in 1,002 proxies changed its
     * {@code typeof} from {@code "function"} to {@code "object"} and made it unconstructable:
     * a valid object's ECMAScript type depended on how many times it had been wrapped. A target
     * is fixed when its {@code Proxy} is created, so the walk cannot see a chain change under it.
     * <p>
     * Termination does not rely on the chain being acyclic. The {@code Proxy} constructor cannot
     * build a cycle — the target must already exist — but the raw embedder API is not bound by
     * that, so Floyd's algorithm runs alongside the walk and reports a cycle as "not found"
     * instead of spinning.
     *
     * @param value                the value to unwrap
     * @param unwrapBoundFunctions true to also follow {@code [[BoundTargetFunction]]}
     * @return the value at the end of the chain, or {@code null} when the chain is cyclic
     */
    private static JSValue unwrapTargets(JSValue value, boolean unwrapBoundFunctions) {
        JSValue hare = value;
        JSValue tortoise = value;
        boolean advanceTortoise = false;
        while (true) {
            JSValue next = nextTarget(hare, unwrapBoundFunctions);
            if (next == null) {
                return hare;
            }
            hare = next;
            if (advanceTortoise) {
                tortoise = nextTarget(tortoise, unwrapBoundFunctions);
            }
            advanceTortoise = !advanceTortoise;
            if (hare == tortoise) {
                return null;
            }
        }
    }
}
