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

/** Array.fromAsync construction and its promise continuations. */
final class ArrayFromAsync {
    private final JSContext context;
    private long index;
    private final JSValue mapper;
    private final JSValue mapThis;
    private final JSPromise promise;
    private JSObject target;

    private ArrayFromAsync(JSContext context, JSValue mapper, JSValue mapThis) {
        this.context = context;
        this.mapper = mapper;
        this.mapThis = mapThis;
        promise = context.createJSPromise();
    }

    private void appendIterableValue(JSAsyncIterator iterator, JSValue value, JSPromise completion) {
        if (target instanceof JSArray array) {
            array.push(value);
        } else if (!target.defineProperty(PropertyKey.fromIndexString(index), value,
                PropertyDescriptor.DataState.All)) {
            iterator.close();
            context.processMicrotasks();
            JSValue error = context.throwTypeError("Cannot define property " + index + " on result object");
            context.clearAllPendingExceptions();
            completion.reject(error);
            return;
        }
        index++;
        completion.fulfill(JSUndefined.INSTANCE);
    }

    private void arrayLikeStep(JSObject source, int index, int length) {
        if (index >= length) {
            finishFromAsync(context, promise, target, length);
            return;
        }
        try {
            JSValue value = source.get(PropertyKey.fromIndexString(index));
            if (!rejectPending()) {
                await(value, "onValueResolve", "onValueReject", resolved -> {
                    if (mapper instanceof JSFunction function) {
                        JSValue mapped = ArrayFrom.map(context, function, mapThis, resolved, index);
                        if (!rejectPending()) {
                            await(mapped, "onMapResolve", "onMapReject",
                                    result -> storeAndContinue(source, index, length, result));
                        }
                    } else {
                        storeAndContinue(source, index, length, resolved);
                    }
                });
            }
        } catch (Exception e) {
            promise.reject(context.takeRejectionReason(e));
        }
    }

    private void await(JSValue value, String onResolve, String onReject, java.util.function.Consumer<JSValue> next) {
        resolveThenable(context, value).observe(context,
                new JSNativeFunction(context, onResolve, 1, (ctx, receiver, args) -> {
                    try {
                        next.accept(JSValue.argumentOrUndefined(args, 0));
                    } catch (Exception e) {
                        promise.reject(context.takeRejectionReason(e));
                    }
                    return JSUndefined.INSTANCE;
                }), promise.rejectHandler(context, onReject));
    }

    private void fromIterator(JSAsyncIterator asyncIterator, JSValue constructor) {
        target = ArrayFrom.construct(context, constructor, JSValue.NO_ARGS, 0, "Array.fromAsync");
        if (rejectPending()) {
            return;
        }

        JSPromise iterationPromise = JSAsyncIterator.forAwaitOfIterator(context, asyncIterator, (value) -> {
            // Per ES2024 23.1.2.1: async iterable path
            // - Without mapFn: mappedValue = nextValue (no Await)
            // - With mapFn: mappedValue = Await(mapFn(value, k))
            if (mapper instanceof JSFunction mappingFunc) {
                // IfAbruptCloseAsyncIterator: sync mapFn throw
                JSValue mappedValue;
                try {
                    mappedValue = ArrayFrom.map(context, mappingFunc, mapThis, value, index);
                } catch (Exception e) {
                    return closeIteratorAndReject(context, asyncIterator, context.takeRejectionReason(e));
                }
                if (context.hasPendingException()) {
                    return closeIteratorAndReject(context, asyncIterator, context.takeExecutionException());
                }
                JSPromise awaitPromise = resolveThenable(context, mappedValue);
                JSPromise processingPromise = context.createJSPromise();
                awaitPromise.observe(context, new JSNativeFunction(context, "onResolve", 1,
                        (callbackContext, callbackThisArg, callbackArgs) -> {
                            JSValue resolved = JSValue.argumentOrUndefined(callbackArgs, 0);
                            appendIterableValue(asyncIterator, resolved, processingPromise);
                            return JSUndefined.INSTANCE;
                        }), // IfAbruptCloseAsyncIterator: async mapFn rejection
                        new JSNativeFunction(context, "onReject", 1,
                                (callbackContext, callbackThisArg, callbackArgs) -> {
                                    asyncIterator.close();
                                    context.processMicrotasks();
                                    processingPromise.reject(JSValue.argumentOrUndefined(callbackArgs, 0));
                                    return JSUndefined.INSTANCE;
                                }));
                return processingPromise;
            } else {
                JSPromise processed = context.createJSPromise();
                appendIterableValue(asyncIterator, value, processed);
                return processed;
            }
        });

        iterationPromise.observe(context,
                new JSNativeFunction(context, "onComplete", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                    finishFromAsync(context, promise, target, index);
                    return JSUndefined.INSTANCE;
                }), promise.rejectHandler(context, "onError"));

    }
    private boolean rejectPending() {
        if (!context.hasPendingException()) {
            return false;
        }
        promise.reject(context.takeExecutionException());
        return true;
    }

    private void start(JSValue constructor, JSValue arrayLike) {
        if (!(mapper instanceof JSUndefined) && !(mapper instanceof JSFunction)) {
            rejectWithError(promise, context, "TypeError",
                    "Array.fromAsync: when provided, the second argument must be a function");
            return;
        }
        try {
            if (arrayLike instanceof JSObject object) {
                JSFunction method = ArrayFrom.iteratorMethod(context, object, PropertyKey.SYMBOL_ASYNC_ITERATOR, object,
                        "Symbol.asyncIterator is not a function");
                boolean async = method != null;
                if (!async && !context.hasPendingException()) {
                    method = ArrayFrom.iteratorMethod(context, object, PropertyKey.SYMBOL_ITERATOR, object,
                            "Symbol.iterator is not a function");
                }
                if (rejectPending()) {
                    return;
                }
                if (method != null) {
                    JSValue iterator = method.call(context, arrayLike, JSValue.NO_ARGS);
                    if (rejectPending()) {
                        return;
                    }
                    JSAsyncIterator wrapped = wrapIterator(context, iterator, async);
                    if (wrapped == null) {
                        if (!rejectPending()) {
                            rejectWithError(promise, context, "TypeError",
                                    async
                                            ? "Result of Symbol.asyncIterator is not an async iterator"
                                            : "Result of Symbol.iterator is not an iterator");
                        }
                        return;
                    }
                    fromIterator(wrapped, constructor);
                    return;
                }
            }
            JSObject source = ArrayPrototype.toObjectChecked(context, arrayLike);
            if (rejectPending()) {
                return;
            }
            long length = ArrayPrototype.lengthOfArrayLike(context, source);
            if (rejectPending()) {
                return;
            }
            if (length > 0xFFFF_FFFFL) {
                rejectWithError(promise, context, "RangeError", "Invalid array length");
                return;
            }
            target = ArrayFrom.construct(context, constructor, new JSValue[]{JSNumber.of(length)}, length,
                    "Array.fromAsync");
            if (!rejectPending()) {
                arrayLikeStep(source, 0, (int) length);
            }
        } catch (Exception e) {
            if (!rejectPending()) {
                rejectWithError(promise, context, "Error", e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
    }

    private void storeAndContinue(JSObject source, int index, int length, JSValue value) {
        if (ArrayFrom.define(context, target, index, value)) {
            arrayLikeStep(source, index + 1, length);
        } else {
            rejectPending();
        }
    }

    private static JSPromise closeIteratorAndReject(JSContext context, JSAsyncIterator asyncIterator, JSValue reason) {
        asyncIterator.close();
        JSPromise rejected = context.createJSPromise();
        rejected.reject(reason);
        return rejected;
    }

    private static void finishFromAsync(JSContext context, JSPromise resultPromise, JSObject target, long length) {
        if (!(target instanceof JSArray)) {
            // Per spec: Perform ? Set(A, "length", k, true)
            // The true Throw flag means always throw TypeError on failure
            try {
                boolean success = target.setWithResult(PropertyKey.LENGTH, JSNumber.of(length));
                if (context.hasPendingException()) {
                    resultPromise.reject(context.takeExecutionException());
                    return;
                }
                if (!success) {
                    JSValue error = context.throwTypeError("Cannot assign to read only property 'length' of object");
                    context.clearPendingException();
                    resultPromise.reject(error);
                    return;
                }
            } catch (Exception e) {
                resultPromise.reject(context.takeRejectionReason(e));
                return;
            }
        }
        resultPromise.fulfill(target);
    }

    static JSValue from(JSContext context, JSValue constructor, JSValue[] args) {
        ArrayFromAsync operation = new ArrayFromAsync(context, JSValue.argumentOrUndefined(args, 1),
                JSValue.argumentOrUndefined(args, 2));
        operation.start(constructor, JSValue.argumentOrUndefined(args, 0));
        return operation.promise;
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
     * Resolve a value that may be a Promise or thenable into a Promise. If already a Promise, return it. If a thenable
     * (has .then method), wrap it. Otherwise, return an immediately fulfilled Promise.
     */
    private static JSPromise resolveThenable(JSContext context, JSValue value) {
        if (value instanceof JSPromise promise) {
            return promise;
        }
        if (value instanceof JSObject obj) {
            JSValue thenMethod = obj.get(PropertyKey.THEN);
            if (thenMethod instanceof JSFunction thenFunc) {
                JSPromise promise = context.createJSPromise();
                thenFunc.call(context, value, new JSValue[]{promise.fulfillHandler(context, "resolve"),
                        promise.rejectHandler(context, "reject")});
                return promise;
            }
        }
        JSPromise promise = context.createJSPromise();
        promise.fulfill(value);
        return promise;
    }

    private static JSAsyncIterator wrapIterator(JSContext context, JSValue value, boolean async) {
        if (!(value instanceof JSObject object)) {
            return null;
        }
        if (async) {
            return object instanceof JSAsyncIterator iterator
                    ? iterator
                    : JSAsyncIterator.wrapAsAsyncIterator(object, context);
        }
        return object instanceof JSIterator iterator
                ? JSAsyncIterator.fromIterator(iterator, context)
                : JSAsyncIterator.wrapSyncAsAsyncIterator(object, context);
    }
}
