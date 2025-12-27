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
            String value = str.value();
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

    /**
     * Array.fromAsync(asyncIterable, mapFn, thisArg)
     * ES2022 23.1.2.2
     * Creates a new Array instance from an async iterable, iterable, or array-like object.
     * Returns a Promise that resolves to the created array.
     */
    public static JSValue fromAsync(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            JSPromise promise = new JSPromise();
            promise.reject(ctx.throwError("TypeError", "undefined is not iterable"));
            return promise;
        }

        JSValue arrayLike = args[0];
        JSValue mapFn = args.length > 1 ? args[1] : null;
        JSValue mapThisArg = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        // Check if mapFn is callable
        if (mapFn != null && !(mapFn instanceof JSUndefined) && !(mapFn instanceof JSFunction)) {
            JSPromise promise = new JSPromise();
            promise.reject(ctx.throwError("TypeError", "Array.fromAsync: when provided, the second argument must be a function"));
            return promise;
        }

        JSPromise resultPromise = new JSPromise();

        // Try to get async iterator first
        JSAsyncIterator asyncIterator = JSAsyncIteratorHelper.getAsyncIterator(arrayLike, ctx);
        if (asyncIterator != null) {
            // Use async iteration
            JSPromise arrayPromise = JSAsyncIteratorHelper.toArray(arrayLike, ctx);

            arrayPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfill", 1, (context, thisValue, funcArgs) -> {
                                JSValue arrayValue = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                if (!(arrayValue instanceof JSArray collectedArray)) {
                                    resultPromise.reject(ctx.throwError("TypeError", "Failed to collect array from async iterable"));
                                    return JSUndefined.INSTANCE;
                                }

                                // Apply mapping function if provided
                                if (mapFn instanceof JSFunction mappingFunc) {
                                    JSArray mappedArray = new JSArray();
                                    for (int i = 0; i < collectedArray.getLength(); i++) {
                                        JSValue value = collectedArray.get(i);
                                        JSValue[] mapArgs = new JSValue[]{value, new JSNumber(i)};
                                        JSValue mappedValue = mappingFunc.call(ctx, mapThisArg, mapArgs);
                                        mappedArray.push(mappedValue);
                                    }
                                    resultPromise.fulfill(mappedArray);
                                } else {
                                    resultPromise.fulfill(collectedArray);
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            resultPromise,
                            ctx
                    ),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (context, thisValue, funcArgs) -> {
                                JSValue error = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                resultPromise.reject(error);
                                return JSUndefined.INSTANCE;
                            }),
                            resultPromise,
                            ctx
                    )
            );

            return resultPromise;
        }

        // Handle JSArray input (sync fallback)
        if (arrayLike instanceof JSArray sourceArray) {
            JSArray result = new JSArray();
            for (int i = 0; i < sourceArray.getLength(); i++) {
                JSValue value = sourceArray.get(i);
                if (mapFn instanceof JSFunction mappingFunc) {
                    JSValue[] mapArgs = new JSValue[]{value, new JSNumber(i)};
                    value = mappingFunc.call(ctx, mapThisArg, mapArgs);
                }
                result.push(value);
            }
            resultPromise.fulfill(result);
            return resultPromise;
        }

        // Handle object with length property (sync fallback)
        if (arrayLike instanceof JSObject obj) {
            JSValue lengthValue = obj.get("length");
            if (lengthValue instanceof JSNumber num) {
                int length = (int) num.value();
                JSArray result = new JSArray();
                for (int i = 0; i < length; i++) {
                    JSValue value = obj.get(i);
                    if (mapFn instanceof JSFunction mappingFunc) {
                        JSValue[] mapArgs = new JSValue[]{value, new JSNumber(i)};
                        value = mappingFunc.call(ctx, mapThisArg, mapArgs);
                    }
                    result.push(value);
                }
                resultPromise.fulfill(result);
                return resultPromise;
            }
        }

        // Handle string (sync fallback)
        if (arrayLike instanceof JSString str) {
            String value = str.value();
            JSArray result = new JSArray();
            for (int i = 0; i < value.length(); i++) {
                JSValue charValue = new JSString(String.valueOf(value.charAt(i)));
                if (mapFn instanceof JSFunction mappingFunc) {
                    JSValue[] mapArgs = new JSValue[]{charValue, new JSNumber(i)};
                    charValue = mappingFunc.call(ctx, mapThisArg, mapArgs);
                }
                result.push(charValue);
            }
            resultPromise.fulfill(result);
            return resultPromise;
        }

        // Not iterable
        resultPromise.reject(ctx.throwError("TypeError", "object is not iterable"));
        return resultPromise;
    }

    /**
     * get Array[@@species]
     * ES2015 22.1.2.4
     * Returns the Array constructor.
     */
    public static JSValue getSpecies(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

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
}
