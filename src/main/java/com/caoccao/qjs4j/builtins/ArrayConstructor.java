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
     * Array constructor call/new.
     * Delegates to JSArray.create().
     * <p>
     * Based on ES2020 22.1.1.1
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSArray.create(context, args);
    }

    /**
     * Array.from(items, mapFn, thisArg)
     * ES2024 23.1.2.1
     * Creates a new Array instance from an array-like or iterable object.
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Let C be the this value.
        JSValue C = thisArg;

        // Step 2: Let items
        JSValue items = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Step 3-4: mapfn handling
        JSFunction mapFn = null;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            if (!(args[1] instanceof JSFunction)) {
                return context.throwTypeError("Array.from: when provided, the second argument must be a function");
            }
            mapFn = (JSFunction) args[1];
        }
        JSValue mapThisArg = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        // Step 5: Let usingIterator be ? GetMethod(items, @@iterator).
        boolean hasIterator = false;
        if (items instanceof JSObject itemsObj) {
            JSValue iterMethod = itemsObj.get(PropertyKey.SYMBOL_ITERATOR, context);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (iterMethod != null && !(iterMethod instanceof JSUndefined) && !(iterMethod instanceof JSNull)) {
                hasIterator = true;
            }
        } else if (items instanceof JSString) {
            // Strings are iterable (have @@iterator)
            hasIterator = true;
        }

        // Step 6: If usingIterator is not undefined, then
        if (hasIterator) {
            // Step 6.a: If IsConstructor(C), let A be Construct(C, « »), else let A be ArrayCreate(0).
            JSObject A;
            if (JSTypeChecking.isConstructor(C)) {
                JSValue constructed = JSReflectObject.constructSimple(context, C, new JSValue[0]);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                A = (JSObject) constructed;
            } else {
                A = context.createJSArray();
            }

            // Step 6.d: iterate
            final int[] k = {0};
            final JSFunction mapping = mapFn;
            final JSObject target = A;
            final boolean[] error = {false};
            JSIteratorHelper.forOf(context, items, (value) -> {
                if (context.hasPendingException()) {
                    error[0] = true;
                    return false;
                }
                JSValue mappedValue = value;
                if (mapping != null) {
                    mappedValue = mapping.call(context, mapThisArg, new JSValue[]{value, JSNumber.of(k[0])});
                    if (context.hasPendingException()) {
                        error[0] = true;
                        return false;
                    }
                }
                // Step 6.g.ix: CreateDataPropertyOrThrow(A, Pk, mappedValue)
                if (!target.createDataProperty(PropertyKey.fromIndex(k[0]), mappedValue)) {
                    context.throwTypeError("Cannot define property " + k[0] + " on result object");
                    return false;
                }
                k[0]++;
                return true;
            });
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            // Step 6.e: Set A.length
            A.set(PropertyKey.LENGTH, JSNumber.of(k[0]), context);
            return A;
        }

        // Step 7-8: Array-like path
        // Let arrayLike be ! ToObject(items).
        JSObject arrayLike;
        if (items instanceof JSObject obj) {
            arrayLike = obj;
        } else if (items instanceof JSUndefined || items instanceof JSNull) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        } else {
            // Non-iterable primitives (numbers, booleans, bigints, symbols).
            // When boxed via ToObject, these have no "length" property,
            // so LengthOfArrayLike returns 0, resulting in an empty array.
            long len = 0;
            JSObject A;
            if (JSTypeChecking.isConstructor(C)) {
                JSValue constructed = JSReflectObject.constructSimple(context, C, new JSValue[]{JSNumber.of(len)});
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                A = (JSObject) constructed;
            } else {
                A = context.createJSArray();
            }
            A.set(PropertyKey.LENGTH, JSNumber.of(len), context);
            return A;
        }

        // Step 9: Let len be ? LengthOfArrayLike(arrayLike).
        JSValue lenValue = arrayLike.get(PropertyKey.LENGTH, context);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long len = JSTypeConversions.toLength(context, lenValue);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 10-11: If IsConstructor(C), let A be Construct(C, «len»), else ArrayCreate(len).
        JSObject A;
        if (JSTypeChecking.isConstructor(C)) {
            JSValue constructed = JSReflectObject.constructSimple(context, C, new JSValue[]{JSNumber.of(len)});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            A = (JSObject) constructed;
        } else {
            A = context.createJSArray();
        }

        // Step 12-15: Loop
        for (long k = 0; k < len; k++) {
            JSValue kValue = arrayLike.get(PropertyKey.fromIndex((int) k), context);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            JSValue mappedValue = kValue;
            if (mapFn != null) {
                mappedValue = mapFn.call(context, mapThisArg, new JSValue[]{kValue, JSNumber.of(k)});
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
            // Step 15.viii: CreateDataPropertyOrThrow(A, Pk, mappedValue)
            if (!A.createDataProperty(PropertyKey.fromIndex((int) k), mappedValue)) {
                return context.throwTypeError("Cannot define property " + k + " on result object");
            }
        }

        // Step 16: Set A.length
        A.set(PropertyKey.LENGTH, JSNumber.of(len), context);
        return A;
    }

    /**
     * Array.fromAsync(asyncIterable, mapFn, thisArg)
     * ES2022 23.1.2.2
     * Creates a new Array instance from an async iterable, iterable, or array-like object.
     * Returns a Promise that resolves to the created array.
     */
    public static JSValue fromAsync(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            JSPromise promise = new JSPromise();
            promise.reject(context.throwTypeError("undefined is not iterable"));
            return promise;
        }

        JSValue arrayLike = args[0];
        JSValue mapFn = args.length > 1 ? args[1] : null;
        JSValue mapThisArg = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        // Check if mapFn is callable
        if (mapFn != null && !(mapFn instanceof JSUndefined) && !(mapFn instanceof JSFunction)) {
            JSPromise promise = new JSPromise();
            promise.reject(context.throwTypeError("Array.fromAsync: when provided, the second argument must be a function"));
            return promise;
        }

        JSPromise resultPromise = new JSPromise();

        // Try to get async iterator first
        JSAsyncIterator asyncIterator = JSAsyncIteratorHelper.getAsyncIterator(arrayLike, context);
        if (asyncIterator != null) {
            // Use async iteration
            JSPromise arrayPromise = JSAsyncIteratorHelper.toArray(context, arrayLike);

            arrayPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue arrayValue = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                if (!(arrayValue instanceof JSArray collectedArray)) {
                                    resultPromise.reject(context.throwTypeError("Failed to collect array from async iterable"));
                                    return JSUndefined.INSTANCE;
                                }

                                // Apply mapping function if provided
                                if (mapFn instanceof JSFunction mappingFunc) {
                                    JSArray mappedArray = context.createJSArray();
                                    for (int i = 0; i < collectedArray.getLength(); i++) {
                                        JSValue value = collectedArray.get(i);
                                        JSValue[] mapArgs = new JSValue[]{value, JSNumber.of(i)};
                                        JSValue mappedValue = mappingFunc.call(context, mapThisArg, mapArgs);
                                        mappedArray.push(mappedValue);
                                    }
                                    resultPromise.fulfill(mappedArray);
                                } else {
                                    resultPromise.fulfill(collectedArray);
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            resultPromise,
                            context
                    ),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue error = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                resultPromise.reject(error);
                                return JSUndefined.INSTANCE;
                            }),
                            resultPromise,
                            context
                    )
            );

            return resultPromise;
        }

        // Handle JSArray input (sync fallback)
        if (arrayLike instanceof JSArray sourceArray) {
            JSArray result = context.createJSArray();
            for (int i = 0; i < sourceArray.getLength(); i++) {
                JSValue value = sourceArray.get(i);
                if (mapFn instanceof JSFunction mappingFunc) {
                    JSValue[] mapArgs = new JSValue[]{value, JSNumber.of(i)};
                    value = mappingFunc.call(context, mapThisArg, mapArgs);
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
                JSArray result = context.createJSArray();
                for (int i = 0; i < length; i++) {
                    JSValue value = obj.get(i);
                    if (mapFn instanceof JSFunction mappingFunc) {
                        JSValue[] mapArgs = new JSValue[]{value, JSNumber.of(i)};
                        value = mappingFunc.call(context, mapThisArg, mapArgs);
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
            JSArray result = context.createJSArray();
            for (int i = 0; i < value.length(); i++) {
                JSValue charValue = new JSString(String.valueOf(value.charAt(i)));
                if (mapFn instanceof JSFunction mappingFunc) {
                    JSValue[] mapArgs = new JSValue[]{charValue, JSNumber.of(i)};
                    charValue = mappingFunc.call(context, mapThisArg, mapArgs);
                }
                result.push(charValue);
            }
            resultPromise.fulfill(result);
            return resultPromise;
        }

        // Try to use Symbol.iterator for general iterables (sync fallback)
        if (JSIteratorHelper.isIterable(arrayLike)) {
            JSArray result = context.createJSArray();
            final int[] index = {0};
            JSIteratorHelper.forOf(context, arrayLike, (value) -> {
                JSValue itemValue = value;
                if (mapFn instanceof JSFunction mappingFunc) {
                    JSValue[] mapArgs = new JSValue[]{value, JSNumber.of(index[0])};
                    itemValue = mappingFunc.call(context, mapThisArg, mapArgs);
                }
                result.push(itemValue);
                index[0]++;
                return true;
            });
            resultPromise.fulfill(result);
            return resultPromise;
        }

        // Not iterable
        resultPromise.reject(context.throwTypeError("object is not iterable"));
        return resultPromise;
    }

    /**
     * get Array[@@species]
     * ES2015 22.1.2.4
     * Returns the Array constructor.
     */
    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    /**
     * Array.isArray(arg)
     * ES2020 22.1.2.2
     * Determines whether the passed value is an Array.
     */
    public static JSValue isArray(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public static JSValue of(JSContext context, JSValue thisArg, JSValue[] args) {
        JSArray array = context.createJSArray();

        for (JSValue item : args) {
            array.push(item);
        }

        return array;
    }
}
