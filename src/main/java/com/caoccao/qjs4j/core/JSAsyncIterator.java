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
    private final AsyncIteratorFunction iteratorFunction;
    private final JSContext context;

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

    /**
     * Create a new async iterator.
     *
     * @param iteratorFunction The function that produces values
     * @param ctx              The execution context
     */
    public JSAsyncIterator(AsyncIteratorFunction iteratorFunction, JSContext ctx) {
        super();
        this.iteratorFunction = iteratorFunction;
        this.context = ctx;

        // Add next() method
        this.set("next", new JSNativeFunction("next", 0, (context, thisArg, args) -> {
            return iteratorFunction.next();
        }));

        // Make this iterable via Symbol.asyncIterator
        JSSymbol asyncIteratorSymbol = JSSymbol.getWellKnownSymbol("asyncIterator");
        if (asyncIteratorSymbol != null) {
            this.set(PropertyKey.fromSymbol(asyncIteratorSymbol), new JSNativeFunction("[Symbol.asyncIterator]", 0,
                    (context, thisArg, args) -> thisArg));
        }
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
     * Create an IteratorResult object that resolves in a promise.
     *
     * @param value The iterator value
     * @param done  Whether iteration is complete
     * @return A promise that resolves to the iterator result
     */
    public static JSPromise createIteratorResultPromise(JSValue value, boolean done) {
        JSPromise promise = new JSPromise();
        JSObject result = new JSObject();
        result.set("value", value);
        result.set("done", JSBoolean.valueOf(done));
        promise.fulfill(result);
        return promise;
    }

    /**
     * Create an async iterator from a regular iterator.
     * Each iteration returns a promise that immediately resolves.
     *
     * @param iterator The synchronous iterator
     * @param ctx      The execution context
     * @return An async iterator
     */
    public static JSAsyncIterator fromIterator(JSIterator iterator, JSContext ctx) {
        return new JSAsyncIterator(() -> {
            JSObject result = iterator.next();
            JSValue value = result.get("value");
            JSValue doneValue = result.get("done");
            boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();
            return createIteratorResultPromise(value, done);
        }, ctx);
    }

    /**
     * Create an async iterator from an array.
     * Returns values one at a time asynchronously.
     *
     * @param array The array to iterate
     * @param ctx   The execution context
     * @return An async iterator
     */
    public static JSAsyncIterator fromArray(JSArray array, JSContext ctx) {
        return new JSAsyncIterator(new AsyncIteratorFunction() {
            private int index = 0;

            @Override
            public JSPromise next() {
                if (index >= array.getLength()) {
                    return createIteratorResultPromise(JSUndefined.INSTANCE, true);
                }
                JSValue value = array.get(index++);
                return createIteratorResultPromise(value, false);
            }
        }, ctx);
    }

    /**
     * Create an async iterator from a Java Iterable.
     * Useful for wrapping Java collections.
     *
     * @param iterable The Java iterable
     * @param ctx      The execution context
     * @return An async iterator
     */
    public static JSAsyncIterator fromIterable(Iterable<JSValue> iterable, JSContext ctx) {
        Iterator<JSValue> javaIterator = iterable.iterator();
        return new JSAsyncIterator(() -> {
            if (!javaIterator.hasNext()) {
                return createIteratorResultPromise(JSUndefined.INSTANCE, true);
            }
            JSValue value = javaIterator.next();
            return createIteratorResultPromise(value, false);
        }, ctx);
    }

    /**
     * Create an async iterator that produces values from a promise.
     * When the promise resolves, yields the value once and then completes.
     *
     * @param promise The promise to await
     * @param ctx     The execution context
     * @return An async iterator
     */
    public static JSAsyncIterator fromPromise(JSPromise promise, JSContext ctx) {
        return new JSAsyncIterator(new AsyncIteratorFunction() {
            private boolean consumed = false;

            @Override
            public JSPromise next() {
                if (consumed) {
                    return createIteratorResultPromise(JSUndefined.INSTANCE, true);
                }
                consumed = true;

                // Return a promise that waits for the input promise
                JSPromise resultPromise = new JSPromise();
                promise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfilled", 1, (context, thisArg, args) -> {
                                    JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                    JSObject result = new JSObject();
                                    result.set("value", value);
                                    result.set("done", JSBoolean.FALSE);
                                    resultPromise.fulfill(result);
                                    return JSUndefined.INSTANCE;
                                }),
                                resultPromise,
                                ctx
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onRejected", 1, (context, thisArg, args) -> {
                                    // If promise rejects, propagate the rejection
                                    JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                    resultPromise.reject(reason);
                                    return JSUndefined.INSTANCE;
                                }),
                                resultPromise,
                                ctx
                        )
                );
                return resultPromise;
            }
        }, ctx);
    }

    @Override
    public String toString() {
        return "[object AsyncIterator]";
    }
}
