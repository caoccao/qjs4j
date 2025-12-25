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

/**
 * Implementation of Array constructor static methods.
 * Based on ES2020 Array specification.
 */
public final class ArrayConstructor {

    /**
     * Array.isArray(arg)
     * ES2020 22.1.2.2
     * Determines whether the passed value is an Array.
     */
    public static JSValue isArray(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(args[0] instanceof JSArray);
    }

    /**
     * Array.of(...items)
     * ES2020 22.1.2.3
     * Creates a new Array instance with a variable number of arguments.
     */
    public static JSValue of(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSArray array = new JSArray();

        for (JSValue item : args) {
            array.push(item);
        }

        return array;
    }

    /**
     * Array.from(arrayLike, mapFn, thisArg)
     * ES2020 22.1.2.1
     * Creates a new Array instance from an array-like or iterable object.
     */
    public static JSValue from(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "undefined is not iterable");
        }

        JSValue arrayLike = args[0];
        JSValue mapFn = args.length > 1 ? args[1] : null;
        JSValue mapThisArg = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        // Check if mapFn is callable
        if (mapFn != null && !(mapFn instanceof JSUndefined) && !(mapFn instanceof JSFunction)) {
            return ctx.throwError("TypeError", "Array.from: when provided, the second argument must be a function");
        }

        JSArray result = new JSArray();

        // Handle JSArray input
        if (arrayLike instanceof JSArray sourceArray) {
            long length = sourceArray.getLength();
            for (long i = 0; i < length; i++) {
                JSValue value = sourceArray.get((int) i);

                // Apply mapping function if provided
                if (mapFn instanceof JSFunction mappingFunc) {
                    JSValue[] mapArgs = new JSValue[]{value, new JSNumber(i)};
                    value = mappingFunc.call(ctx, mapThisArg, mapArgs);
                }

                result.push(value);
            }
            return result;
        }

        // Handle object with length property
        if (arrayLike instanceof JSObject obj) {
            JSValue lengthValue = obj.get("length");
            if (lengthValue instanceof JSNumber num) {
                int length = (int) num.value();

                for (int i = 0; i < length; i++) {
                    JSValue value = obj.get(i);

                    // Apply mapping function if provided
                    if (mapFn instanceof JSFunction mappingFunc) {
                        JSValue[] mapArgs = new JSValue[]{value, new JSNumber(i)};
                        value = mappingFunc.call(ctx, mapThisArg, mapArgs);
                    }

                    result.push(value);
                }
                return result;
            }
        }

        // Handle string (iterable)
        if (arrayLike instanceof JSString str) {
            String value = str.getValue();
            for (int i = 0; i < value.length(); i++) {
                JSValue charValue = new JSString(String.valueOf(value.charAt(i)));

                // Apply mapping function if provided
                if (mapFn instanceof JSFunction mappingFunc) {
                    JSValue[] mapArgs = new JSValue[]{charValue, new JSNumber(i)};
                    charValue = mappingFunc.call(ctx, mapThisArg, mapArgs);
                }

                result.push(charValue);
            }
            return result;
        }

        return ctx.throwError("TypeError", "object is not iterable");
    }
}
