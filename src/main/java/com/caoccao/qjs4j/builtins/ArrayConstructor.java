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

    private static JSPromise closeIteratorAndReject(JSContext context, JSAsyncIterator asyncIterator, JSValue reason) {
        asyncIterator.close();
        JSPromise rejected = context.createJSPromise();
        rejected.reject(reason);
        return rejected;
    }

    private static JSValue consumePendingException(JSContext context) {
        JSValue pendingException = context.getPendingException();
        context.clearAllPendingExceptions();
        return pendingException;
    }

    private static JSValue consumePendingExceptionOrCreateStringError(JSContext context, Exception e) {
        if (context.hasPendingException()) {
            return consumePendingException(context);
        }
        String message = e.getMessage();
        return new JSString(message != null ? message : e.toString());
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
        JSValue arrayLike = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue mapFn = args.length > 1 ? args[1] : null;
        JSValue mapThisArg = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        // Per spec, Array.fromAsync NEVER throws synchronously — all errors reject the promise.
        // Must use createJSPromise() to get proper prototype chain (with .then method).
        JSPromise resultPromise = context.createJSPromise();

        // Step 2: mapfn callable check
        if (mapFn != null && !(mapFn instanceof JSUndefined) && !(mapFn instanceof JSFunction)) {
            rejectWithError(resultPromise, context, "TypeError", "Array.fromAsync: when provided, the second argument must be a function");
            return resultPromise;
        }

        try {
            // Step 4: GetMethod(asyncItems, @@asyncIterator)
            // Per ES spec GetMethod: get property, return undefined if null/undefined,
            // throw TypeError if not callable
            JSValue usingAsyncIterator = JSUndefined.INSTANCE;
            JSValue usingSyncIterator = JSUndefined.INSTANCE;

            if (arrayLike instanceof JSObject obj) {
                // Pass context so getter functions execute properly
                JSValue asyncIterMethod = obj.get(PropertyKey.SYMBOL_ASYNC_ITERATOR, context);
                if (context.hasPendingException()) {
                    JSValue pendingException = context.getPendingException();
                    context.clearAllPendingExceptions();
                    resultPromise.reject(pendingException);
                    return resultPromise;
                }
                if (asyncIterMethod != null && !(asyncIterMethod instanceof JSUndefined) && !(asyncIterMethod instanceof JSNull)) {
                    if (!(asyncIterMethod instanceof JSFunction)) {
                        rejectWithError(resultPromise, context, "TypeError", "Symbol.asyncIterator is not a function");
                        return resultPromise;
                    }
                    usingAsyncIterator = asyncIterMethod;
                }

                // Step 5: if usingAsyncIterator is undefined, get @@iterator
                if (usingAsyncIterator instanceof JSUndefined) {
                    JSValue syncIterMethod = obj.get(PropertyKey.SYMBOL_ITERATOR, context);
                    if (context.hasPendingException()) {
                        JSValue pendingException = context.getPendingException();
                        context.clearAllPendingExceptions();
                        resultPromise.reject(pendingException);
                        return resultPromise;
                    }
                    if (syncIterMethod != null && !(syncIterMethod instanceof JSUndefined) && !(syncIterMethod instanceof JSNull)) {
                        if (!(syncIterMethod instanceof JSFunction)) {
                            rejectWithError(resultPromise, context, "TypeError", "Symbol.iterator is not a function");
                            return resultPromise;
                        }
                        usingSyncIterator = syncIterMethod;
                    }
                }
            }

            // Async iterable path
            if (usingAsyncIterator instanceof JSFunction asyncIterFunc) {
                JSValue iterResult = asyncIterFunc.call(context, arrayLike, new JSValue[0]);
                JSAsyncIterator asyncIterator = null;
                if (iterResult instanceof JSAsyncIterator ai) {
                    asyncIterator = ai;
                } else if (iterResult instanceof JSObject iterObj) {
                    asyncIterator = JSAsyncIterator.wrapAsAsyncIterator(iterObj, context);
                }
                if (asyncIterator == null) {
                    rejectWithError(resultPromise, context, "TypeError", "Result of Symbol.asyncIterator is not an async iterator");
                    return resultPromise;
                }
                return fromAsyncIterablePath(context, resultPromise, asyncIterator, thisArg, mapFn, mapThisArg);
            }

            // Sync iterable path (wrap as async)
            if (usingSyncIterator instanceof JSFunction syncIterFunc) {
                JSValue iterResult = syncIterFunc.call(context, arrayLike, new JSValue[0]);
                JSAsyncIterator asyncIterator = null;
                if (iterResult instanceof JSIterator syncIter) {
                    asyncIterator = JSAsyncIterator.fromIterator(syncIter, context);
                } else if (iterResult instanceof JSObject iterObj) {
                    asyncIterator = JSAsyncIterator.wrapSyncAsAsyncIterator(iterObj, context);
                }
                if (asyncIterator == null) {
                    rejectWithError(resultPromise, context, "TypeError", "Result of Symbol.iterator is not an iterator");
                    return resultPromise;
                }
                return fromAsyncIterablePath(context, resultPromise, asyncIterator, thisArg, mapFn, mapThisArg);
            }

            // Array-like path: no iterators found
            if (arrayLike instanceof JSNull || arrayLike instanceof JSUndefined) {
                rejectWithError(resultPromise, context, "TypeError", "Cannot convert undefined or null to object");
                return resultPromise;
            }

            JSObject arrayLikeObj;
            if (arrayLike instanceof JSObject obj) {
                arrayLikeObj = obj;
            } else {
                arrayLikeObj = JSTypeConversions.toObject(context, arrayLike);
                if (arrayLikeObj == null) {
                    rejectWithError(resultPromise, context, "TypeError", "Cannot convert to object");
                    return resultPromise;
                }
            }

            // LengthOfArrayLike — getter may throw
            JSValue lenValue = arrayLikeObj.get(PropertyKey.LENGTH, context);
            if (context.hasPendingException()) {
                JSValue pendingException = context.getPendingException();
                context.clearAllPendingExceptions();
                resultPromise.reject(pendingException);
                return resultPromise;
            }
            long length = JSTypeConversions.toLength(context, lenValue);
            if (context.hasPendingException()) {
                JSValue pendingException = context.getPendingException();
                context.clearAllPendingExceptions();
                resultPromise.reject(pendingException);
                return resultPromise;
            }
            if (length > 0xFFFFFFFFL) {
                rejectWithError(resultPromise, context, "RangeError", "Invalid array length");
                return resultPromise;
            }

            // Per spec 3.k.iv: If IsConstructor(C), let A be Construct(C, « len »), else ArrayCreate(len)
            JSObject target;
            boolean isArray;
            if (JSTypeChecking.isConstructor(thisArg)) {
                JSValue constructed = JSReflectObject.constructSimple(context, thisArg, new JSValue[]{JSNumber.of(length)});
                if (context.hasPendingException()) {
                    JSValue pendingException = context.getPendingException();
                    context.clearAllPendingExceptions();
                    resultPromise.reject(pendingException);
                    return resultPromise;
                }
                target = (JSObject) constructed;
                isArray = target instanceof JSArray;
            } else {
                // Per spec 3.k.v: Let A be ArrayCreate(len)
                target = context.createJSArray(length);
                isArray = true;
            }

            // Array-like iteration: get each element, await it, then store
            fromAsyncArrayLikeStep(context, resultPromise, target, isArray, arrayLikeObj, 0, (int) length, mapFn, mapThisArg);
        } catch (Exception e) {
            // Any synchronous error in the async body → reject the promise
            if (context.hasPendingException()) {
                JSValue pendingException = context.getPendingException();
                context.clearAllPendingExceptions();
                resultPromise.reject(pendingException);
            } else {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                rejectWithError(resultPromise, context, "Error", msg);
            }
        }
        return resultPromise;
    }

    /**
     * Process one element of an array-like in fromAsync.
     * Gets element at index, awaits it (if promise/thenable), stores to result, recurses.
     */
    private static void fromAsyncArrayLikeStep(
            JSContext context, JSPromise resultPromise, JSObject target,
            boolean isArray, JSObject arrayLikeObj, int index, int length,
            JSValue mapFn, JSValue mapThisArg) {
        if (index >= length) {
            if (!isArray) {
                // Per spec: Perform ? Set(A, "length", k, true)
                // The true Throw flag means always throw TypeError on failure
                try {
                    boolean success = target.setWithResult(PropertyKey.LENGTH, JSNumber.of(length), context);
                    if (context.hasPendingException()) {
                        resultPromise.reject(consumePendingException(context));
                        return;
                    }
                    if (!success) {
                        JSValue error = context.throwTypeError(
                                "Cannot assign to read only property 'length' of object");
                        context.clearPendingException();
                        resultPromise.reject(error);
                        return;
                    }
                } catch (Exception e) {
                    resultPromise.reject(consumePendingExceptionOrCreateStringError(context, e));
                    return;
                }
            }
            resultPromise.fulfill(target);
            return;
        }

        // Step 1: Get element value - may throw (e.g., getter throws)
        JSValue value;
        try {
            value = arrayLikeObj.get(PropertyKey.fromIndex(index), context);
        } catch (Exception e) {
            resultPromise.reject(consumePendingExceptionOrCreateStringError(context, e));
            return;
        }
        if (context.hasPendingException()) {
            resultPromise.reject(consumePendingException(context));
            return;
        }

        // Await the value
        JSPromise awaitValue = resolveThenable(context, value);
        awaitValue.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onValueResolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            JSValue awaitedValue = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;

                            // Step 2: If mapping, call mapFn on awaited value, then await result
                            if (mapFn instanceof JSFunction mappingFunc) {
                                JSValue mapped;
                                try {
                                    mapped = mappingFunc.call(context, mapThisArg, new JSValue[]{awaitedValue, JSNumber.of(index)});
                                } catch (Exception e) {
                                    resultPromise.reject(consumePendingExceptionOrCreateStringError(context, e));
                                    return JSUndefined.INSTANCE;
                                }
                                if (context.hasPendingException()) {
                                    resultPromise.reject(consumePendingException(context));
                                    return JSUndefined.INSTANCE;
                                }
                                JSPromise awaitMapped = resolveThenable(context, mapped);
                                awaitMapped.addReactions(
                                        new JSPromise.ReactionRecord(
                                                new JSNativeFunction("onMapResolve", 1, (innerContext, innerThisArg, innerArgs) -> {
                                                    JSValue finalValue = innerArgs.length > 0 ? innerArgs[0] : JSUndefined.INSTANCE;
                                                    // Per spec: CreateDataPropertyOrThrow(A, Pk, mappedValue)
                                                    target.createDataProperty(PropertyKey.fromIndex(index), finalValue);
                                                    fromAsyncArrayLikeStep(context, resultPromise, target, isArray, arrayLikeObj, index + 1, length, mapFn, mapThisArg);
                                                    return JSUndefined.INSTANCE;
                                                }),
                                                null, context
                                        ),
                                        new JSPromise.ReactionRecord(
                                                new JSNativeFunction("onMapReject", 1, (innerContext, innerThisArg, innerArgs) -> {
                                                    resultPromise.reject(innerArgs.length > 0 ? innerArgs[0] : JSUndefined.INSTANCE);
                                                    return JSUndefined.INSTANCE;
                                                }),
                                                null, context
                                        )
                                );
                            } else {
                                // No mapFn: use awaited value directly
                                // Per spec: CreateDataPropertyOrThrow(A, Pk, mappedValue)
                                target.createDataProperty(PropertyKey.fromIndex(index), awaitedValue);
                                fromAsyncArrayLikeStep(context, resultPromise, target, isArray, arrayLikeObj, index + 1, length, mapFn, mapThisArg);
                            }
                            return JSUndefined.INSTANCE;
                        }),
                        null, context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onValueReject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            resultPromise.reject(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        }),
                        null, context
                )
        );
    }

    private static JSPromise fromAsyncIterablePath(
            JSContext context, JSPromise resultPromise,
            JSAsyncIterator asyncIterator, JSValue C,
            JSValue mapFn, JSValue mapThisArg) {
        // Per ES spec: If IsConstructor(C), let A be Construct(C), else ArrayCreate(0)
        JSObject A;
        if (JSTypeChecking.isConstructor(C)) {
            JSValue constructed = JSReflectObject.constructSimple(context, C, new JSValue[0]);
            if (context.hasPendingException()) {
                JSValue pendingException = context.getPendingException();
                context.clearAllPendingExceptions();
                resultPromise.reject(pendingException);
                return resultPromise;
            }
            A = (JSObject) constructed;
        } else {
            A = context.createJSArray();
        }

        final int[] index = {0};
        final JSObject target = A;
        final boolean isArray = A instanceof JSArray;

        JSPromise iterationPromise = JSAsyncIterator.forAwaitOfIterator(context, asyncIterator, (value) -> {
            // Per ES2024 23.1.2.1: async iterable path
            // - Without mapFn: mappedValue = nextValue (no Await)
            // - With mapFn: mappedValue = Await(mapFn(value, k))
            if (mapFn instanceof JSFunction mappingFunc) {
                // IfAbruptCloseAsyncIterator: sync mapFn throw
                JSValue mappedValue;
                try {
                    mappedValue = mappingFunc.call(context, mapThisArg, new JSValue[]{value, JSNumber.of(index[0])});
                } catch (Exception e) {
                    return closeIteratorAndReject(
                            context,
                            asyncIterator,
                            consumePendingExceptionOrCreateStringError(context, e));
                }
                if (context.hasPendingException()) {
                    return closeIteratorAndReject(context, asyncIterator, consumePendingException(context));
                }
                JSPromise awaitPromise = resolveThenable(context, mappedValue);
                JSPromise processingPromise = context.createJSPromise();
                awaitPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onResolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                                    JSValue resolved = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                                    // CreateDataPropertyOrThrow
                                    if (isArray) {
                                        ((JSArray) target).push(resolved);
                                    } else if (!target.createDataProperty(PropertyKey.fromIndex(index[0]), resolved)) {
                                        asyncIterator.close();
                                        context.processMicrotasks();
                                        JSValue error = context.throwTypeError("Cannot define property " + index[0] + " on result object");
                                        context.clearAllPendingExceptions();
                                        processingPromise.reject(error);
                                        return JSUndefined.INSTANCE;
                                    }
                                    index[0]++;
                                    processingPromise.fulfill(JSUndefined.INSTANCE);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        ),
                        new JSPromise.ReactionRecord(
                                // IfAbruptCloseAsyncIterator: async mapFn rejection
                                new JSNativeFunction("onReject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                                    asyncIterator.close();
                                    context.processMicrotasks();
                                    processingPromise.reject(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        )
                );
                return processingPromise;
            } else {
                // No mapFn: use value directly, no Await
                // CreateDataPropertyOrThrow
                if (isArray) {
                    ((JSArray) target).push(value);
                } else if (!target.createDataProperty(PropertyKey.fromIndex(index[0]), value)) {
                    asyncIterator.close();
                    context.processMicrotasks();
                    JSValue error = context.throwTypeError("Cannot define property " + index[0] + " on result object");
                    context.clearAllPendingExceptions();
                    JSPromise rejected = context.createJSPromise();
                    rejected.reject(error);
                    return rejected;
                }
                index[0]++;
                JSPromise resolved = context.createJSPromise();
                resolved.fulfill(JSUndefined.INSTANCE);
                return resolved;
            }
        });

        iterationPromise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onComplete", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            if (!isArray) {
                                // Per spec: Perform ? Set(A, "length", len, true)
                                try {
                                    boolean success = target.setWithResult(PropertyKey.LENGTH, JSNumber.of(index[0]), context);
                                    if (context.hasPendingException()) {
                                        resultPromise.reject(consumePendingException(context));
                                        return JSUndefined.INSTANCE;
                                    }
                                    if (!success) {
                                        JSValue error = context.throwTypeError(
                                                "Cannot assign to read only property 'length' of object");
                                        context.clearPendingException();
                                        resultPromise.reject(error);
                                        return JSUndefined.INSTANCE;
                                    }
                                } catch (Exception e) {
                                    resultPromise.reject(consumePendingExceptionOrCreateStringError(context, e));
                                    return JSUndefined.INSTANCE;
                                }
                            }
                            resultPromise.fulfill(target);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onError", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            resultPromise.reject(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                )
        );

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

        // Per ES spec 7.2.2 IsArray / QuickJS JS_IsArray:
        // Recursively unwrap Proxy exotic objects to find the target
        JSValue arg = args[0];
        while (arg instanceof JSProxy proxy) {
            if (proxy.isRevoked()) {
                return context.throwTypeError("Cannot perform 'isArray' on a proxy that has been revoked");
            }
            arg = proxy.getTarget();
        }
        return JSBoolean.valueOf(arg instanceof JSObject obj && obj.isArrayObject());
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

    private static void rejectWithError(JSPromise promise, JSContext context, String errorType, String message) {
        // Clear any pending exception that was set as a side-effect
        if (context.hasPendingException()) {
            context.clearAllPendingExceptions();
        }
        JSError error = switch (errorType) {
            case "TypeError" -> new JSTypeError(context, message);
            case "RangeError" -> new JSRangeError(context, message);
            default -> new JSError(context, message);
        };
        context.transferPrototype(error, errorType);
        promise.reject(error);
    }

    /**
     * Resolve a value that may be a Promise or thenable into a Promise.
     * If already a Promise, return it. If a thenable (has .then method), wrap it.
     * Otherwise, return an immediately fulfilled Promise.
     */
    private static JSPromise resolveThenable(JSContext context, JSValue value) {
        if (value instanceof JSPromise promise) {
            return promise;
        }
        if (value instanceof JSObject obj) {
            JSValue thenMethod = obj.get(PropertyKey.THEN);
            if (thenMethod instanceof JSFunction thenFunc) {
                JSPromise promise = context.createJSPromise();
                thenFunc.call(context, value, new JSValue[]{
                        new JSNativeFunction("resolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            promise.fulfill(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        }),
                        new JSNativeFunction("reject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            promise.reject(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        })
                });
                return promise;
            }
        }
        JSPromise promise = context.createJSPromise();
        promise.fulfill(value);
        return promise;
    }
}
