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
                    JSValue ex = context.getPendingException();
                    context.clearAllPendingExceptions();
                    resultPromise.reject(ex);
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
                        JSValue ex = context.getPendingException();
                        context.clearAllPendingExceptions();
                        resultPromise.reject(ex);
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
                    asyncIterator = JSAsyncIteratorHelper.wrapAsAsyncIterator(iterObj, context);
                }
                if (asyncIterator == null) {
                    rejectWithError(resultPromise, context, "TypeError", "Result of Symbol.asyncIterator is not an async iterator");
                    return resultPromise;
                }
                return fromAsyncIterablePath(context, resultPromise, asyncIterator, arrayLike, mapFn, mapThisArg);
            }

            // Sync iterable path (wrap as async)
            if (usingSyncIterator instanceof JSFunction syncIterFunc) {
                JSValue iterResult = syncIterFunc.call(context, arrayLike, new JSValue[0]);
                JSAsyncIterator asyncIterator = null;
                if (iterResult instanceof JSIterator syncIter) {
                    asyncIterator = JSAsyncIterator.fromIterator(syncIter, context);
                } else if (iterResult instanceof JSObject iterObj) {
                    asyncIterator = JSAsyncIteratorHelper.wrapSyncAsAsyncIterator(iterObj, context);
                }
                if (asyncIterator == null) {
                    rejectWithError(resultPromise, context, "TypeError", "Result of Symbol.iterator is not an iterator");
                    return resultPromise;
                }
                return fromAsyncIterablePath(context, resultPromise, asyncIterator, arrayLike, mapFn, mapThisArg);
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
                JSValue ex = context.getPendingException();
                context.clearAllPendingExceptions();
                resultPromise.reject(ex);
                return resultPromise;
            }
            long length = JSTypeConversions.toLength(context, lenValue);
            if (context.hasPendingException()) {
                JSValue ex = context.getPendingException();
                context.clearAllPendingExceptions();
                resultPromise.reject(ex);
                return resultPromise;
            }
            if (length > 0xFFFFFFFFL) {
                rejectWithError(resultPromise, context, "RangeError", "Invalid array length");
                return resultPromise;
            }

            // Array-like iteration: get each element, await it, then push
            JSArray result = context.createJSArray();
            fromAsyncArrayLikeStep(context, resultPromise, result, arrayLikeObj, 0, (int) length, mapFn, mapThisArg);
        } catch (Exception e) {
            // Any synchronous error in the async body → reject the promise
            if (context.hasPendingException()) {
                JSValue ex = context.getPendingException();
                context.clearAllPendingExceptions();
                resultPromise.reject(ex);
            } else {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                rejectWithError(resultPromise, context, "Error", msg);
            }
        }
        return resultPromise;
    }

    private static JSPromise fromAsyncIterablePath(
            JSContext context, JSPromise resultPromise,
            JSAsyncIterator asyncIterator, JSValue arrayLike,
            JSValue mapFn, JSValue mapThisArg) {
        JSArray result = context.createJSArray();
        final int[] index = {0};

        JSPromise iterationPromise = JSAsyncIteratorHelper.forAwaitOfIterator(context, asyncIterator, (value) -> {
            // Per ES2024 23.1.2.1: async iterable path
            // - Without mapFn: mappedValue = nextValue (no Await)
            // - With mapFn: mappedValue = Await(mapFn(value, k))
            if (mapFn instanceof JSFunction mappingFunc) {
                JSValue mappedValue = mappingFunc.call(context, mapThisArg, new JSValue[]{value, JSNumber.of(index[0])});
                JSPromise awaitPromise = resolveThenable(context, mappedValue);
                JSPromise processingPromise = new JSPromise();
                awaitPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onResolve", 1, (ctx2, t2, a2) -> {
                                    JSValue resolved = a2.length > 0 ? a2[0] : JSUndefined.INSTANCE;
                                    result.push(resolved);
                                    index[0]++;
                                    processingPromise.fulfill(JSUndefined.INSTANCE);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (ctx2, t2, a2) -> {
                                    processingPromise.reject(a2.length > 0 ? a2[0] : JSUndefined.INSTANCE);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        )
                );
                return processingPromise;
            } else {
                // No mapFn: use value directly, no Await
                result.push(value);
                index[0]++;
                JSPromise resolved = new JSPromise();
                resolved.fulfill(JSUndefined.INSTANCE);
                return resolved;
            }
        });

        iterationPromise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onComplete", 1, (ctx2, t2, a2) -> {
                            resultPromise.fulfill(result);
                            return JSUndefined.INSTANCE;
                        }),
                        resultPromise,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onError", 1, (ctx2, t2, a2) -> {
                            resultPromise.reject(a2.length > 0 ? a2[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        }),
                        resultPromise,
                        context
                )
        );

        return resultPromise;
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
     * Process one element of an array-like in fromAsync.
     * Gets element at index, awaits it (if promise/thenable), pushes to result, recurses.
     */
    private static void fromAsyncArrayLikeStep(
            JSContext context, JSPromise resultPromise, JSArray result,
            JSObject arrayLikeObj, int index, int length,
            JSValue mapFn, JSValue mapThisArg) {
        if (index >= length) {
            resultPromise.fulfill(result);
            return;
        }

        JSValue value = arrayLikeObj.get(PropertyKey.fromIndex(index), context);
        if (mapFn instanceof JSFunction mappingFunc) {
            value = mappingFunc.call(context, mapThisArg, new JSValue[]{value, JSNumber.of(index)});
        }

        // Await the value (resolve promises/thenables)
        JSPromise awaitPromise = resolveThenable(context, value);
        // Pass null as reaction promise to prevent triggerReaction from resolving resultPromise
        // with the handler's return value. We control resultPromise fulfillment explicitly.
        awaitPromise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onResolve", 1, (ctx2, t2, a2) -> {
                            JSValue resolved = a2.length > 0 ? a2[0] : JSUndefined.INSTANCE;
                            result.push(resolved);
                            // Continue to next element
                            fromAsyncArrayLikeStep(context, resultPromise, result, arrayLikeObj, index + 1, length, mapFn, mapThisArg);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onReject", 1, (ctx2, t2, a2) -> {
                            resultPromise.reject(a2.length > 0 ? a2[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                )
        );
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
     * Resolve a value that may be a Promise or thenable into a Promise.
     * If already a Promise, return it. If a thenable (has .then method), wrap it.
     * Otherwise, return an immediately fulfilled Promise.
     */
    private static JSPromise resolveThenable(JSContext context, JSValue value) {
        if (value instanceof JSPromise p) {
            return p;
        }
        if (value instanceof JSObject obj) {
            JSValue thenMethod = obj.get("then");
            if (thenMethod instanceof JSFunction thenFunc) {
                JSPromise promise = new JSPromise();
                thenFunc.call(context, value, new JSValue[]{
                        new JSNativeFunction("resolve", 1, (ctx, thisArg, args) -> {
                            promise.fulfill(args.length > 0 ? args[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        }),
                        new JSNativeFunction("reject", 1, (ctx, thisArg, args) -> {
                            promise.reject(args.length > 0 ? args[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        })
                });
                return promise;
            }
        }
        JSPromise promise = new JSPromise();
        promise.fulfill(value);
        return promise;
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
