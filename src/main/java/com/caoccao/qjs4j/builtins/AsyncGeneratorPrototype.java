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
 * Prototype methods for async generator objects.
 * Based on ES2018 AsyncGenerator.prototype specification.
 */
public final class AsyncGeneratorPrototype {

    /**
     * Create an async generator that yields values with a delay.
     * Useful for simulating async operations.
     * <p>
     * Note: This is a simplified version. A full implementation would use
     * actual timers or scheduled microtasks.
     *
     * @param ctx    The execution context
     * @param values Values to yield
     * @return An async generator that yields values "asynchronously"
     */
    public static JSAsyncGenerator createDelayedGenerator(JSContext ctx, JSValue[] values) {
        return new JSAsyncGenerator((inputValue, isThrow) -> {
            final int[] index = {0};

            if (isThrow) {
                JSPromise promise = new JSPromise();
                promise.reject(inputValue);
                return promise;
            }

            int currentIndex = index[0]++;

            if (currentIndex >= values.length) {
                JSPromise promise = new JSPromise();
                JSObject result = new JSObject();
                result.set("value", JSUndefined.INSTANCE);
                result.set("done", JSBoolean.TRUE);
                promise.fulfill(result);
                return promise;
            }

            // Create a promise that resolves with the value
            JSPromise promise = new JSPromise();

            // In a real implementation, this would use setTimeout or similar
            // For now, we enqueue as a microtask to simulate async behavior
            ctx.enqueueMicrotask(() -> {
                JSObject result = new JSObject();
                result.set("value", values[currentIndex]);
                result.set("done", JSBoolean.FALSE);
                promise.fulfill(result);
            });

            return promise;
        }, ctx);
    }

    /**
     * Create an async generator that yields values from a promise array.
     * Each promise is awaited before yielding its value.
     *
     * @param ctx      The execution context
     * @param promises Array of promises to yield
     * @return An async generator
     */
    public static JSAsyncGenerator createFromPromises(JSContext ctx, JSPromise[] promises) {
        return new JSAsyncGenerator((inputValue, isThrow) -> {
            // Use a holder to track the current index
            final int[] index = {0};

            if (isThrow) {
                JSPromise promise = new JSPromise();
                promise.reject(inputValue);
                return promise;
            }

            int currentIndex = index[0]++;

            if (currentIndex >= promises.length) {
                // All promises processed
                JSPromise promise = new JSPromise();
                JSObject result = new JSObject();
                result.set("value", JSUndefined.INSTANCE);
                result.set("done", JSBoolean.TRUE);
                promise.fulfill(result);
                return promise;
            }

            // Wait for the current promise and yield its value
            JSPromise currentPromise = promises[currentIndex];
            JSPromise resultPromise = new JSPromise();

            currentPromise.addReactions(
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
                                JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                resultPromise.reject(error);
                                return JSUndefined.INSTANCE;
                            }),
                            resultPromise,
                            ctx
                    )
            );

            return resultPromise;
        }, ctx);
    }

    /**
     * Create a simple async generator that yields a sequence of promises.
     * <p>
     * Example usage:
     * <pre>
     * JSAsyncGenerator gen = AsyncGeneratorPrototype.createFromValues(
     *     ctx,
     *     new JSValue[]{new JSNumber(1), new JSNumber(2), new JSNumber(3)}
     * );
     * </pre>
     *
     * @param ctx    The execution context
     * @param values Values to yield (each wrapped in a promise)
     * @return An async generator that yields the values
     */
    public static JSAsyncGenerator createFromValues(JSContext ctx, JSValue[] values) {
        // Use a holder to track the current index across calls
        final int[] indexHolder = {0};

        return JSAsyncGenerator.create((inputValue) -> {
            int currentIndex = indexHolder[0]++;

            if (currentIndex >= values.length) {
                // All values yielded
                JSPromise promise = new JSPromise();
                JSObject result = new JSObject();
                result.set("value", JSUndefined.INSTANCE);
                result.set("done", JSBoolean.TRUE);
                promise.fulfill(result);
                return promise;
            }

            // Yield next value
            JSPromise promise = new JSPromise();
            JSObject result = new JSObject();
            result.set("value", values[currentIndex]);
            result.set("done", JSBoolean.FALSE);
            promise.fulfill(result);
            return promise;
        }, ctx);
    }

    /**
     * AsyncGenerator.prototype.next(value)
     * Get the next value from the async generator.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-asyncgenerator-prototype-next">ECMAScript AsyncGenerator.prototype.next</a>
     */
    public static JSValue next(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSAsyncGenerator generator)) {
            return ctx.throwError("TypeError", "AsyncGenerator.prototype.next called on non-AsyncGenerator");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return generator.next(value);
    }

    /**
     * AsyncGenerator.prototype.return(value)
     * Return a value and close the async generator.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-asyncgenerator-prototype-return">ECMAScript AsyncGenerator.prototype.return</a>
     */
    public static JSValue return_(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSAsyncGenerator generator)) {
            return ctx.throwError("TypeError", "AsyncGenerator.prototype.return called on non-AsyncGenerator");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return generator.return_(value);
    }

    /**
     * AsyncGenerator.prototype.throw(exception)
     * Throw an exception into the async generator.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-asyncgenerator-prototype-throw">ECMAScript AsyncGenerator.prototype.throw</a>
     */
    public static JSValue throw_(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSAsyncGenerator generator)) {
            return ctx.throwError("TypeError", "AsyncGenerator.prototype.throw called on non-AsyncGenerator");
        }

        JSValue exception = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return generator.throw_(exception);
    }
}
