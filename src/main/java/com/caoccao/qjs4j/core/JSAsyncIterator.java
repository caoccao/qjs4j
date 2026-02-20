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

import java.util.Iterator;

/**
 * Represents an async iterator in JavaScript.
 * Based on ES2018 async iteration protocol.
 * <p>
 * Async iterators return promises that resolve to iterator results:
 * - next() returns Promise<{value, done}>
 * - Used by for-await-of loops
 * - Identified by Symbol.asyncIterator
 */
public class JSAsyncIterator extends JSObject {
    public static final String NAME = "AsyncIterator";
    private final JSContext context;
    private final AsyncIteratorFunction iteratorFunction;
    /**
     * The underlying iterator object, used for calling return() to close.
     */
    private JSObject underlyingIterator;

    /**
     * Create a new async iterator.
     *
     * @param iteratorFunction The function that produces values
     * @param context          The execution context
     */
    public JSAsyncIterator(AsyncIteratorFunction iteratorFunction, JSContext context) {
        super();
        this.iteratorFunction = iteratorFunction;
        this.context = context;

        // Add next() method
        this.set(PropertyKey.NEXT, new JSNativeFunction(
                "next",
                0,
                (childContext, thisArg, args) -> iteratorFunction.next()));

        // Make this iterable via Symbol.asyncIterator
        JSSymbol asyncIteratorSymbol = JSSymbol.getWellKnownSymbol("asyncIterator");
        if (asyncIteratorSymbol != null) {
            this.set(PropertyKey.fromSymbol(asyncIteratorSymbol), new JSNativeFunction(
                    "[Symbol.asyncIterator]",
                    0,
                    (childContext, thisArg, args) -> thisArg));
        }
    }

    static JSValue consumePendingException(JSContext context) {
        JSValue reason = context.getPendingException();
        context.clearAllPendingExceptions();
        return reason;
    }

    static JSValue consumePendingExceptionOrCreateStringError(JSContext context, Exception e) {
        if (context.hasPendingException()) {
            return consumePendingException(context);
        }
        String message = e.getMessage();
        return new JSString(message != null ? message : e.toString());
    }

    /**
     * Create an async-from-sync iterator result promise.
     * Per ES spec AsyncFromSyncIteratorContinuation: resolves the value
     * via PromiseResolve before placing it in the iterator result.
     * This means if value is a promise/thenable, it gets awaited.
     *
     * @param context The execution context
     * @param value   The iterator value (may be a promise/thenable)
     * @param done    Whether iteration is complete
     * @return A promise that resolves to the iterator result (with resolved value)
     */
    public static JSPromise createAsyncFromSyncResultPromise(JSContext context, JSValue value, boolean done) {
        // Check if value is a promise/thenable that needs resolution
        if (value instanceof JSPromise promiseValue) {
            JSPromise resultPromise = context.createJSPromise();
            promiseValue.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onResolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                                JSValue resolved = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                                JSObject result = context.createJSObject();
                                result.set(PropertyKey.VALUE, resolved);
                                result.set(PropertyKey.DONE, JSBoolean.valueOf(done));
                                resultPromise.fulfill(result);
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context
                    ),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                                resultPromise.reject(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context
                    )
            );
            return resultPromise;
        }
        if (value instanceof JSObject obj) {
            JSValue thenMethod = obj.get(PropertyKey.THEN);
            if (thenMethod instanceof JSFunction thenFunc) {
                JSPromise resultPromise = context.createJSPromise();
                thenFunc.call(context, value, new JSValue[]{
                        new JSNativeFunction("resolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            JSValue resolved = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                            JSObject result = context.createJSObject();
                            result.set(PropertyKey.VALUE, resolved);
                            result.set(PropertyKey.DONE, JSBoolean.valueOf(done));
                            resultPromise.fulfill(result);
                            return JSUndefined.INSTANCE;
                        }),
                        new JSNativeFunction("reject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            resultPromise.reject(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        })
                });
                return resultPromise;
            }
        }
        // Non-thenable: use directly
        return createIteratorResultPromise(context, value, done);
    }

    /**
     * Create an IteratorResult object that resolves in a promise.
     *
     * @param context The execution context
     * @param value   The iterator value
     * @param done    Whether iteration is complete
     * @return A promise that resolves to the iterator result
     */
    public static JSPromise createIteratorResultPromise(JSContext context, JSValue value, boolean done) {
        JSPromise promise = context.createJSPromise();
        JSObject result = context.createJSObject();
        result.set(PropertyKey.VALUE, value);
        result.set(PropertyKey.DONE, JSBoolean.valueOf(done));
        promise.fulfill(result);
        return promise;
    }

    static JSPromise createRejectedPromise(JSContext context, JSValue reason) {
        JSPromise promise = context.createJSPromise();
        promise.reject(reason);
        return promise;
    }

    /**
     * Execute a for-await-of loop.
     * Iterates asynchronously, waiting for each promise to resolve.
     *
     * @param context  The execution context
     * @param iterable The iterable to loop over
     * @param callback The callback to execute for each value
     * @return A promise that resolves when iteration is complete
     */
    public static JSPromise forAwaitOf(JSContext context, JSValue iterable, AsyncIterationCallback callback) {
        JSPromise completionPromise = context.createJSPromise();

        // Get async iterator
        JSAsyncIterator iterator = getAsyncIterator(iterable, context);
        if (iterator == null) {
            JSObject error = context.createJSObject();
            error.set(PropertyKey.NAME, new JSString("TypeError"));
            error.set(PropertyKey.MESSAGE, new JSString("Object is not async iterable"));
            completionPromise.reject(error);
            return completionPromise;
        }

        // Start iteration
        iterateNext(iterator, callback, context, completionPromise);

        return completionPromise;
    }

    /**
     * Execute a for-await-of loop using an already-obtained async iterator.
     */
    public static JSPromise forAwaitOfIterator(JSContext context, JSAsyncIterator iterator, AsyncIterationCallback callback) {
        JSPromise completionPromise = context.createJSPromise();
        iterateNext(iterator, callback, context, completionPromise);
        return completionPromise;
    }

    /**
     * Create an async iterator from an array.
     * Returns values one at a time asynchronously.
     *
     * @param array   The array to iterate
     * @param context The execution context
     * @return An async iterator
     */
    public static JSAsyncIterator fromArray(JSArray array, JSContext context) {
        return new JSAsyncIterator(new AsyncIteratorFunction() {
            private int index = 0;

            @Override
            public JSPromise next() {
                if (index >= array.getLength()) {
                    return createIteratorResultPromise(context, JSUndefined.INSTANCE, true);
                }
                JSValue value = array.get(index++);
                return createIteratorResultPromise(context, value, false);
            }
        }, context);
    }

    /**
     * Create an async iterator from a Java Iterable.
     * Useful for wrapping Java collections.
     *
     * @param iterable The Java iterable
     * @param context  The execution context
     * @return An async iterator
     */
    public static JSAsyncIterator fromIterable(Iterable<JSValue> iterable, JSContext context) {
        Iterator<JSValue> javaIterator = iterable.iterator();
        return new JSAsyncIterator(() -> {
            if (!javaIterator.hasNext()) {
                return createIteratorResultPromise(context, JSUndefined.INSTANCE, true);
            }
            JSValue value = javaIterator.next();
            return createIteratorResultPromise(context, value, false);
        }, context);
    }

    /**
     * Create an async iterator from a regular iterator.
     * Per ES spec CreateAsyncFromSyncIterator: values from the sync iterator
     * are resolved via PromiseResolve before being placed in the iterator result.
     * This means promise values are awaited/unwrapped.
     *
     * @param iterator The synchronous iterator
     * @param context  The execution context
     * @return An async iterator
     */
    public static JSAsyncIterator fromIterator(JSIterator iterator, JSContext context) {
        JSAsyncIterator asyncIter = new JSAsyncIterator(() -> {
            // Per ES spec: IfAbruptRejectPromise - catch sync iterator next() exceptions
            JSObject result;
            try {
                result = iterator.next();
            } catch (Exception e) {
                return createRejectedPromise(context, consumePendingExceptionOrCreateStringError(context, e));
            }
            if (context.hasPendingException()) {
                return createRejectedPromise(context, consumePendingException(context));
            }
            JSValue value = result.get(PropertyKey.VALUE);
            JSValue doneValue = result.get("done");
            boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();
            return createAsyncFromSyncResultPromise(context, value, done);
        }, context);
        asyncIter.setUnderlyingIterator(iterator);
        return asyncIter;
    }

    /**
     * Create an async iterator that produces values from a promise.
     * When the promise resolves, yields the value once and then completes.
     *
     * @param context The execution context
     * @param promise The promise to await
     * @return An async iterator
     */
    public static JSAsyncIterator fromPromise(JSContext context, JSPromise promise) {
        return new JSAsyncIterator(new AsyncIteratorFunction() {
            private boolean consumed = false;

            @Override
            public JSPromise next() {
                if (consumed) {
                    return createIteratorResultPromise(context, JSUndefined.INSTANCE, true);
                }
                consumed = true;

                // Return a promise that waits for the input promise
                JSPromise resultPromise = context.createJSPromise();
                promise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfilled", 1, (childContext, thisArg, args) -> {
                                    JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                    JSObject result = childContext.createJSObject();
                                    result.set(PropertyKey.VALUE, value);
                                    result.set(PropertyKey.DONE, JSBoolean.FALSE);
                                    resultPromise.fulfill(result);
                                    return JSUndefined.INSTANCE;
                                }),
                                resultPromise,
                                context
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onRejected", 1, (childContext, thisArg, args) -> {
                                    // If promise rejects, propagate the rejection
                                    JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                    resultPromise.reject(reason);
                                    return JSUndefined.INSTANCE;
                                }),
                                resultPromise,
                                context
                        )
                );
                return resultPromise;
            }
        }, context);
    }

    /**
     * Get an async iterator from a value.
     * Checks for Symbol.asyncIterator, falls back to Symbol.iterator.
     *
     * @param iterable The value to get an iterator from
     * @param context  The execution context
     * @return An async iterator, or null if not iterable
     */
    public static JSAsyncIterator getAsyncIterator(JSValue iterable, JSContext context) {
        if (!(iterable instanceof JSObject obj)) {
            return null;
        }

        // First, try Symbol.asyncIterator
        JSValue asyncIteratorMethod = obj.get(PropertyKey.SYMBOL_ASYNC_ITERATOR);

        if (asyncIteratorMethod instanceof JSFunction asyncIterFunc) {
            JSValue result = asyncIterFunc.call(context, iterable, new JSValue[0]);
            if (result instanceof JSAsyncIterator asyncIter) {
                return asyncIter;
            }
            // Accept any JSObject with next() as an async iterator (e.g., JSAsyncGenerator)
            if (result instanceof JSObject iterObj) {
                JSAsyncIterator wrapped = wrapAsAsyncIterator(iterObj, context);
                if (wrapped != null) {
                    return wrapped;
                }
            }
        }

        // Fall back to Symbol.iterator (sync iterator)
        JSValue iteratorMethod = obj.get(PropertyKey.SYMBOL_ITERATOR);

        if (iteratorMethod instanceof JSFunction iterFunc) {
            JSValue result = iterFunc.call(context, iterable, new JSValue[0]);
            if (result instanceof JSIterator syncIter) {
                return fromIterator(syncIter, context);
            }
            // Accept any JSObject with next() as a sync iterator, wrap as async
            if (result instanceof JSObject iterObj) {
                return wrapSyncAsAsyncIterator(iterObj, context);
            }
        }

        return null;
    }

    /**
     * Check if a value is async iterable.
     *
     * @param value The value to check
     * @return True if the value has Symbol.asyncIterator or Symbol.iterator
     */
    public static boolean isAsyncIterable(JSValue value) {
        if (!(value instanceof JSObject obj)) {
            return false;
        }

        // Check for Symbol.asyncIterator
        PropertyKey asyncIteratorKey = PropertyKey.SYMBOL_ASYNC_ITERATOR;
        JSValue asyncIteratorMethod = obj.get(asyncIteratorKey);
        if (asyncIteratorMethod instanceof JSFunction) {
            return true;
        }

        // Check for Symbol.iterator (can be converted to async)
        PropertyKey iteratorKey = PropertyKey.SYMBOL_ITERATOR;
        JSValue iteratorMethod = obj.get(iteratorKey);
        return iteratorMethod instanceof JSFunction;
    }

    /**
     * Internal helper to iterate to the next value.
     * Uses continuation-passing style to avoid deep recursion.
     */
    private static void iterateNext(
            JSAsyncIterator iterator,
            AsyncIterationCallback callback,
            JSContext context,
            JSPromise completionPromise) {
        // Get next promise
        JSPromise nextPromise = iterator.next();

        // When next promise resolves, process the result
        nextPromise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onNextFulfilled", 1, (childContext, thisArg, args) -> {
                            JSValue result = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

                            if (!(result instanceof JSObject resultObj)) {
                                completionPromise.reject(new JSString("Iterator result is not an object"));
                                return JSUndefined.INSTANCE;
                            }

                            // Check if done
                            JSValue doneValue = resultObj.get("done");
                            boolean done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;

                            if (done) {
                                // Iteration complete
                                completionPromise.fulfill(JSUndefined.INSTANCE);
                                return JSUndefined.INSTANCE;
                            }

                            // Get value and process it
                            JSValue value = resultObj.get(PropertyKey.VALUE);
                            JSPromise processingPromise = callback.iterate(value);

                            // When processing completes, continue to next iteration
                            processingPromise.addReactions(
                                    new JSPromise.ReactionRecord(
                                            new JSNativeFunction("onProcessed", 1, (innerContext, innerThisArg, innerArgs) -> {
                                                // Continue iteration
                                                iterateNext(iterator, callback, childContext, completionPromise);
                                                return JSUndefined.INSTANCE;
                                            }),
                                            null,
                                            childContext
                                    ),
                                    new JSPromise.ReactionRecord(
                                            new JSNativeFunction("onProcessError", 1, (innerContext, innerThisArg, innerArgs) -> {
                                                // If processing fails, reject completion promise
                                                JSValue error = innerArgs.length > 0 ? innerArgs[0] : JSUndefined.INSTANCE;
                                                completionPromise.reject(error);
                                                return JSUndefined.INSTANCE;
                                            }),
                                            null,
                                            childContext
                                    )
                            );

                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onNextRejected", 1, (childContext, thisArg, args) -> {
                            // If next() fails, reject completion promise
                            JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            completionPromise.reject(error);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                )
        );
    }

    /**
     * Convert an async iterable to an array.
     * Waits for all values to be produced asynchronously.
     *
     * @param context  The execution context
     * @param iterable The async iterable
     * @return A promise that resolves to an array of all values
     */
    public static JSPromise toArray(JSContext context, JSValue iterable) {
        JSPromise resultPromise = context.createJSPromise();
        JSArray array = context.createJSArray();

        // Use for-await-of to collect all values
        forAwaitOf(context, iterable, (value) -> {
            array.push(value);
            // Return immediately resolved promise
            JSPromise resolved = context.createJSPromise();
            resolved.fulfill(JSUndefined.INSTANCE);
            return resolved;
        }).addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onComplete", 1, (childContext, thisArg, args) -> {
                            resultPromise.fulfill(array);
                            return JSUndefined.INSTANCE;
                        }),
                        resultPromise,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onError", 1, (childContext, thisArg, args) -> {
                            JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            resultPromise.reject(error);
                            return JSUndefined.INSTANCE;
                        }),
                        resultPromise,
                        context
                )
        );

        return resultPromise;
    }

    /**
     * Wrap any JSObject that has a next() method as an async iterator.
     * The next() method is expected to return a Promise that resolves to {value, done}.
     */
    public static JSAsyncIterator wrapAsAsyncIterator(JSObject iterObj, JSContext context) {
        JSValue nextMethod = iterObj.get(PropertyKey.NEXT);
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            return null;
        }
        JSAsyncIterator asyncIter = new JSAsyncIterator(() -> {
            JSValue result = nextFunc.call(context, iterObj, new JSValue[0]);
            if (result instanceof JSPromise promise) {
                return promise;
            }
            // If next() doesn't return a promise, wrap the result
            JSPromise promise = context.createJSPromise();
            promise.fulfill(result);
            return promise;
        }, context);
        asyncIter.setUnderlyingIterator(iterObj);
        return asyncIter;
    }

    /**
     * Wrap a sync iterator JSObject (has next() returning {value, done}) as an async iterator.
     */
    public static JSAsyncIterator wrapSyncAsAsyncIterator(JSObject iterObj, JSContext context) {
        JSValue nextMethod = iterObj.get(PropertyKey.NEXT);
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            return null;
        }
        JSAsyncIterator asyncIter = new JSAsyncIterator(() -> {
            // Per ES spec: IfAbruptRejectPromise - catch sync iterator next() exceptions
            JSValue result;
            try {
                result = nextFunc.call(context, iterObj, new JSValue[0]);
            } catch (Exception e) {
                return createRejectedPromise(
                        context,
                        consumePendingExceptionOrCreateStringError(context, e));
            }
            if (context.hasPendingException()) {
                return createRejectedPromise(context, consumePendingException(context));
            }
            if (result instanceof JSObject resultObj) {
                JSValue value = resultObj.get(PropertyKey.VALUE);
                JSValue doneValue = resultObj.get("done");
                boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();
                // Per ES spec CreateAsyncFromSyncIterator: resolve promise values
                return createAsyncFromSyncResultPromise(context, value, done);
            }
            return createRejectedPromise(context, new JSString("Iterator result is not an object"));
        }, context);
        asyncIter.setUnderlyingIterator(iterObj);
        return asyncIter;
    }

    /**
     * Close this async iterator by calling return() on the underlying iterator object.
     * Per ES spec AsyncIteratorClose.
     *
     * @return A promise that resolves when the iterator is closed, or null if no return method
     */
    public JSPromise close() {
        JSObject iter = underlyingIterator;
        if (iter == null) {
            return null;
        }
        JSValue returnMethod = iter.get(PropertyKey.RETURN);
        if (!(returnMethod instanceof JSFunction returnFunc)) {
            return null;
        }
        JSValue result = returnFunc.call(context, iter, new JSValue[0]);
        if (result instanceof JSPromise promise) {
            return promise;
        }
        JSPromise promise = context.createJSPromise();
        promise.fulfill(result != null ? result : JSUndefined.INSTANCE);
        return promise;
    }

    /**
     * Call the async iterator's next() method.
     *
     * @return A promise that resolves to {value, done}
     */
    public JSPromise next() {
        return iteratorFunction.next();
    }

    /**
     * Set the underlying iterator object so that close() can call its return() method.
     */
    public void setUnderlyingIterator(JSObject underlyingIterator) {
        this.underlyingIterator = underlyingIterator;
    }

    @Override
    public String toString() {
        return "[object AsyncIterator]";
    }

    /**
     * Functional interface for async iterator next() implementation.
     */
    @FunctionalInterface
    public interface AsyncIterationCallback {
        /**
         * Process an iteration value.
         * Called asynchronously for each value.
         *
         * @param value The current value
         * @return A promise that resolves when processing is complete
         */
        JSPromise iterate(JSValue value);
    }

    @FunctionalInterface
    public interface AsyncIteratorFunction {
        /**
         * Get the next value asynchronously.
         *
         * @return A promise that resolves to an IteratorResult
         */
        JSPromise next();
    }
}
