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
    private final JSContext context;
    private final AsyncIteratorFunction iteratorFunction;
    /** The underlying iterator object, used for calling return() to close. */
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

    /**
     * Set the underlying iterator object so that close() can call its return() method.
     */
    public void setUnderlyingIterator(JSObject underlyingIterator) {
        this.underlyingIterator = underlyingIterator;
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
        if (result instanceof JSPromise p) {
            return p;
        }
        JSPromise p = context.createJSPromise();
        p.fulfill(result != null ? result : JSUndefined.INSTANCE);
        return p;
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
                            new JSNativeFunction("onResolve", 1, (ctx, thisArg, args) -> {
                                JSValue resolved = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
                            new JSNativeFunction("onReject", 1, (ctx, thisArg, args) -> {
                                resultPromise.reject(args.length > 0 ? args[0] : JSUndefined.INSTANCE);
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context
                    )
            );
            return resultPromise;
        }
        if (value instanceof JSObject obj) {
            JSValue thenMethod = obj.get("then");
            if (thenMethod instanceof JSFunction thenFunc) {
                JSPromise resultPromise = context.createJSPromise();
                thenFunc.call(context, value, new JSValue[]{
                        new JSNativeFunction("resolve", 1, (ctx, thisArg, args) -> {
                            JSValue resolved = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            JSObject result = context.createJSObject();
                            result.set(PropertyKey.VALUE, resolved);
                            result.set(PropertyKey.DONE, JSBoolean.valueOf(done));
                            resultPromise.fulfill(result);
                            return JSUndefined.INSTANCE;
                        }),
                        new JSNativeFunction("reject", 1, (ctx, thisArg, args) -> {
                            resultPromise.reject(args.length > 0 ? args[0] : JSUndefined.INSTANCE);
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
        return new JSAsyncIterator(() -> {
            JSObject result = iterator.next();
            JSValue value = result.get("value");
            JSValue doneValue = result.get("done");
            boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();
            return createAsyncFromSyncResultPromise(context, value, done);
        }, context);
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
     * Call the async iterator's next() method.
     *
     * @return A promise that resolves to {value, done}
     */
    public JSPromise next() {
        return iteratorFunction.next();
    }

    @Override
    public String toString() {
        return "[object AsyncIterator]";
    }

    /**
     * Functional interface for async iterator next() implementation.
     */
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
