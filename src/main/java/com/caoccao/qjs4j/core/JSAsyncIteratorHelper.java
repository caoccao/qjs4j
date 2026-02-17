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
 * Helper utilities for async iteration (for-await-of loops).
 * Based on ES2018 async iteration protocol.
 * <p>
 * Provides utilities for:
 * - Getting async iterators from iterables
 * - Executing for-await-of loops
 * - Converting sync iterators to async
 */
public final class JSAsyncIteratorHelper {

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
            error.set("name", new JSString("TypeError"));
            error.set("message", new JSString("Object is not async iterable"));
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
                if (wrapped != null) return wrapped;
            }
        }

        // Fall back to Symbol.iterator (sync iterator)
        JSValue iteratorMethod = obj.get(PropertyKey.SYMBOL_ITERATOR);

        if (iteratorMethod instanceof JSFunction iterFunc) {
            JSValue result = iterFunc.call(context, iterable, new JSValue[0]);
            if (result instanceof JSIterator syncIter) {
                return JSAsyncIterator.fromIterator(syncIter, context);
            }
            // Accept any JSObject with next() as a sync iterator, wrap as async
            if (result instanceof JSObject iterObj) {
                JSAsyncIterator wrapped = wrapSyncAsAsyncIterator(iterObj, context);
                if (wrapped != null) return wrapped;
            }
        }

        return null;
    }

    /**
     * Wrap any JSObject that has a next() method as an async iterator.
     * The next() method is expected to return a Promise that resolves to {value, done}.
     */
    public static JSAsyncIterator wrapAsAsyncIterator(JSObject iterObj, JSContext context) {
        JSValue nextMethod = iterObj.get("next");
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            return null;
        }
        return new JSAsyncIterator(() -> {
            JSValue result = nextFunc.call(context, iterObj, new JSValue[0]);
            if (result instanceof JSPromise promise) {
                return promise;
            }
            // If next() doesn't return a promise, wrap the result
            JSPromise promise = context.createJSPromise();
            promise.fulfill(result);
            return promise;
        }, context);
    }

    /**
     * Wrap a sync iterator JSObject (has next() returning {value, done}) as an async iterator.
     */
    public static JSAsyncIterator wrapSyncAsAsyncIterator(JSObject iterObj, JSContext context) {
        JSValue nextMethod = iterObj.get("next");
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            return null;
        }
        return new JSAsyncIterator(() -> {
            JSValue result = nextFunc.call(context, iterObj, new JSValue[0]);
            if (result instanceof JSObject resultObj) {
                JSValue value = resultObj.get("value");
                JSValue doneValue = resultObj.get("done");
                boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();
                // Per ES spec CreateAsyncFromSyncIterator: resolve promise values
                return JSAsyncIterator.createAsyncFromSyncResultPromise(context, value, done);
            }
            JSPromise promise = context.createJSPromise();
            promise.reject(new JSString("Iterator result is not an object"));
            return promise;
        }, context);
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
                            JSValue value = resultObj.get("value");
                            JSPromise processingPromise = callback.iterate(value);

                            // When processing completes, continue to next iteration
                            processingPromise.addReactions(
                                    new JSPromise.ReactionRecord(
                                            new JSNativeFunction("onProcessed", 1, (innerContext, thisArg2, args2) -> {
                                                // Continue iteration
                                                iterateNext(iterator, callback, childContext, completionPromise);
                                                return JSUndefined.INSTANCE;
                                            }),
                                            null,
                                            childContext
                                    ),
                                    new JSPromise.ReactionRecord(
                                            new JSNativeFunction("onProcessError", 1, (innerContext, thisArg2, args2) -> {
                                                // If processing fails, reject completion promise
                                                JSValue error = args2.length > 0 ? args2[0] : JSUndefined.INSTANCE;
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
     * Callback interface for for-await-of iteration.
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
}
