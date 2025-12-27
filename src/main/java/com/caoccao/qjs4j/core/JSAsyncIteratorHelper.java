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

    /**
     * Get an async iterator from a value.
     * Checks for Symbol.asyncIterator, falls back to Symbol.iterator.
     *
     * @param iterable The value to get an iterator from
     * @param ctx      The execution context
     * @return An async iterator, or null if not iterable
     */
    public static JSAsyncIterator getAsyncIterator(JSValue iterable, JSContext ctx) {
        if (!(iterable instanceof JSObject obj)) {
            return null;
        }

        // First, try Symbol.asyncIterator
        PropertyKey asyncIteratorKey = PropertyKey.fromSymbol(JSSymbol.ASYNC_ITERATOR);
        JSValue asyncIteratorMethod = obj.get(asyncIteratorKey);

        if (asyncIteratorMethod instanceof JSFunction asyncIterFunc) {
            // Call the async iterator method
            JSValue result = asyncIterFunc.call(ctx, iterable, new JSValue[0]);
            if (result instanceof JSAsyncIterator asyncIter) {
                return asyncIter;
            }
        }

        // Fall back to Symbol.iterator (sync iterator)
        PropertyKey iteratorKey = PropertyKey.fromSymbol(JSSymbol.ITERATOR);
        JSValue iteratorMethod = obj.get(iteratorKey);

        if (iteratorMethod instanceof JSFunction iterFunc) {
            // Call the iterator method
            JSValue result = iterFunc.call(ctx, iterable, new JSValue[0]);
            if (result instanceof JSIterator syncIter) {
                // Convert sync iterator to async
                return JSAsyncIterator.fromIterator(syncIter, ctx);
            }
        }

        return null;
    }

    /**
     * Execute a for-await-of loop.
     * Iterates asynchronously, waiting for each promise to resolve.
     *
     * @param iterable The iterable to loop over
     * @param callback The callback to execute for each value
     * @param ctx      The execution context
     * @return A promise that resolves when iteration is complete
     */
    public static JSPromise forAwaitOf(JSValue iterable, AsyncIterationCallback callback, JSContext ctx) {
        JSPromise completionPromise = new JSPromise();

        // Get async iterator
        JSAsyncIterator iterator = getAsyncIterator(iterable, ctx);
        if (iterator == null) {
            JSObject error = new JSObject();
            error.set("name", new JSString("TypeError"));
            error.set("message", new JSString("Object is not async iterable"));
            completionPromise.reject(error);
            return completionPromise;
        }

        // Start iteration
        iterateNext(iterator, callback, ctx, completionPromise);

        return completionPromise;
    }

    /**
     * Internal helper to iterate to the next value.
     * Uses continuation-passing style to avoid deep recursion.
     */
    private static void iterateNext(JSAsyncIterator iterator, AsyncIterationCallback callback,
                                    JSContext ctx, JSPromise completionPromise) {
        // Get next promise
        JSPromise nextPromise = iterator.next();

        // When next promise resolves, process the result
        nextPromise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onNextFulfilled", 1, (context, thisArg, args) -> {
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
                                            new JSNativeFunction("onProcessed", 1, (ctx2, thisArg2, args2) -> {
                                                // Continue iteration
                                                iterateNext(iterator, callback, context, completionPromise);
                                                return JSUndefined.INSTANCE;
                                            }),
                                            null,
                                            context
                                    ),
                                    new JSPromise.ReactionRecord(
                                            new JSNativeFunction("onProcessError", 1, (ctx2, thisArg2, args2) -> {
                                                // If processing fails, reject completion promise
                                                JSValue error = args2.length > 0 ? args2[0] : JSUndefined.INSTANCE;
                                                completionPromise.reject(error);
                                                return JSUndefined.INSTANCE;
                                            }),
                                            null,
                                            context
                                    )
                            );

                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        ctx
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onNextRejected", 1, (context, thisArg, args) -> {
                            // If next() fails, reject completion promise
                            JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            completionPromise.reject(error);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        ctx
                )
        );
    }

    /**
     * Convert an async iterable to an array.
     * Waits for all values to be produced asynchronously.
     *
     * @param iterable The async iterable
     * @param ctx      The execution context
     * @return A promise that resolves to an array of all values
     */
    public static JSPromise toArray(JSValue iterable, JSContext ctx) {
        JSPromise resultPromise = new JSPromise();
        JSArray array = new JSArray();

        // Use for-await-of to collect all values
        forAwaitOf(iterable, (value) -> {
            array.push(value);
            // Return immediately resolved promise
            JSPromise resolved = new JSPromise();
            resolved.fulfill(JSUndefined.INSTANCE);
            return resolved;
        }, ctx).addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onComplete", 1, (context, thisArg, args) -> {
                            resultPromise.fulfill(array);
                            return JSUndefined.INSTANCE;
                        }),
                        resultPromise,
                        ctx
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onError", 1, (context, thisArg, args) -> {
                            JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            resultPromise.reject(error);
                            return JSUndefined.INSTANCE;
                        }),
                        resultPromise,
                        ctx
                )
        );

        return resultPromise;
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
        PropertyKey asyncIteratorKey = PropertyKey.fromSymbol(JSSymbol.ASYNC_ITERATOR);
        JSValue asyncIteratorMethod = obj.get(asyncIteratorKey);
        if (asyncIteratorMethod instanceof JSFunction) {
            return true;
        }

        // Check for Symbol.iterator (can be converted to async)
        PropertyKey iteratorKey = PropertyKey.fromSymbol(JSSymbol.ITERATOR);
        JSValue iteratorMethod = obj.get(iteratorKey);
        return iteratorMethod instanceof JSFunction;
    }
}
