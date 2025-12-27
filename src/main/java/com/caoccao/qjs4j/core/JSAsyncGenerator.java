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
 * Represents an async generator object in JavaScript.
 * Based on ES2018 async generator specification.
 * <p>
 * Async generators combine generator functions with async iteration:
 * - Defined with async function* syntax
 * - yield produces promises
 * - Can await within the generator body
 * - Implements async iterator protocol (Symbol.asyncIterator)
 * - Methods return promises: next(), return(), throw()
 */
public final class JSAsyncGenerator extends JSObject {
    /**
     * Async generator states based on ES2018.
     */
    public enum AsyncGeneratorState {
        SUSPENDED_START,    // Created but not started
        SUSPENDED_YIELD,    // Suspended at a yield point
        EXECUTING,          // Currently executing
        AWAITING_RETURN,    // Awaiting a return value
        COMPLETED           // Generator has completed
    }

    private AsyncGeneratorState state;
    private final JSContext context;
    private final AsyncGeneratorFunction generatorFunction;
    private JSValue returnValue;
    private JSValue thrownValue;

    /**
     * Functional interface for async generator implementation.
     */
    @FunctionalInterface
    public interface AsyncGeneratorFunction {
        /**
         * Execute the next step of the generator.
         *
         * @param inputValue Value passed to next()
         * @param isThrow    Whether this is a throw() call
         * @return A promise that resolves to {value, done}
         */
        JSPromise executeNext(JSValue inputValue, boolean isThrow);
    }

    /**
     * Create a new async generator.
     *
     * @param generatorFunction The generator implementation
     * @param ctx               The execution context
     */
    public JSAsyncGenerator(AsyncGeneratorFunction generatorFunction, JSContext ctx) {
        super();
        this.state = AsyncGeneratorState.SUSPENDED_START;
        this.context = ctx;
        this.generatorFunction = generatorFunction;
        this.returnValue = JSUndefined.INSTANCE;
        this.thrownValue = null;

        // Add next() method
        this.set("next", new JSNativeFunction("next", 1, (context, thisArg, args) -> {
            JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            return next(value);
        }));

        // Add return() method
        this.set("return", new JSNativeFunction("return", 1, (context, thisArg, args) -> {
            JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            return return_(value);
        }));

        // Add throw() method
        this.set("throw", new JSNativeFunction("throw", 1, (context, thisArg, args) -> {
            JSValue exception = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            return throw_(exception);
        }));

        // Make this an async iterable via Symbol.asyncIterator
        this.set(PropertyKey.fromSymbol(JSSymbol.ASYNC_ITERATOR),
                new JSNativeFunction("[Symbol.asyncIterator]", 0, (context, thisArg, args) -> thisArg));
    }

    /**
     * Get the next value from the async generator.
     * ES2018 AsyncGenerator.prototype.next()
     *
     * @param value Value to send into the generator
     * @return A promise that resolves to {value, done}
     */
    public JSPromise next(JSValue value) {
        if (state == AsyncGeneratorState.COMPLETED) {
            // Generator already completed
            return createIteratorResultPromise(returnValue, true);
        }

        if (state == AsyncGeneratorState.EXECUTING) {
            // Generator is already running - queue this request
            // In a full implementation, this would queue the request
            JSPromise promise = new JSPromise();
            JSObject error = new JSObject();
            error.set("name", new JSString("TypeError"));
            error.set("message", new JSString("Generator is already executing"));
            promise.reject(error);
            return promise;
        }

        state = AsyncGeneratorState.EXECUTING;

        try {
            // Execute the generator function
            JSPromise resultPromise = generatorFunction.executeNext(value, false);

            // Create a new promise for the final result
            JSPromise finalPromise = new JSPromise();

            // When the generator execution completes
            resultPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfilled", 1, (ctx, thisArg, args) -> {
                                JSValue result = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

                                if (result instanceof JSObject resultObj) {
                                    JSValue doneValue = resultObj.get("done");
                                    boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();

                                    if (done) {
                                        state = AsyncGeneratorState.COMPLETED;
                                        returnValue = resultObj.get("value");
                                    } else {
                                        state = AsyncGeneratorState.SUSPENDED_YIELD;
                                    }
                                }

                                finalPromise.fulfill(result);
                                return JSUndefined.INSTANCE;
                            }),
                            finalPromise,
                            context
                    ),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onRejected", 1, (ctx, thisArg, args) -> {
                                state = AsyncGeneratorState.COMPLETED;
                                JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                finalPromise.reject(error);
                                return JSUndefined.INSTANCE;
                            }),
                            finalPromise,
                            context
                    )
            );

            return finalPromise;
        } catch (Exception e) {
            state = AsyncGeneratorState.COMPLETED;
            JSPromise errorPromise = new JSPromise();
            errorPromise.reject(new JSString("Async generator error: " + e.getMessage()));
            return errorPromise;
        }
    }

    /**
     * Return a value from the async generator and close it.
     * ES2018 AsyncGenerator.prototype.return()
     *
     * @param value The return value
     * @return A promise that resolves to {value, done: true}
     */
    public JSPromise return_(JSValue value) {
        if (state == AsyncGeneratorState.COMPLETED) {
            // Already completed, return the value
            return createIteratorResultPromise(value, true);
        }

        state = AsyncGeneratorState.AWAITING_RETURN;
        returnValue = value;

        // In a full implementation, this would execute finally blocks
        state = AsyncGeneratorState.COMPLETED;
        return createIteratorResultPromise(value, true);
    }

    /**
     * Throw an exception into the async generator.
     * ES2018 AsyncGenerator.prototype.throw()
     *
     * @param exception The exception to throw
     * @return A promise that resolves to the next value or rejects
     */
    public JSPromise throw_(JSValue exception) {
        if (state == AsyncGeneratorState.COMPLETED) {
            // Generator is completed, reject immediately
            JSPromise promise = new JSPromise();
            promise.reject(exception);
            return promise;
        }

        state = AsyncGeneratorState.EXECUTING;
        thrownValue = exception;

        try {
            // Execute the generator with the thrown value
            JSPromise resultPromise = generatorFunction.executeNext(exception, true);

            JSPromise finalPromise = new JSPromise();

            resultPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfilled", 1, (ctx, thisArg, args) -> {
                                JSValue result = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

                                if (result instanceof JSObject resultObj) {
                                    JSValue doneValue = resultObj.get("done");
                                    boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();

                                    if (done) {
                                        state = AsyncGeneratorState.COMPLETED;
                                    } else {
                                        state = AsyncGeneratorState.SUSPENDED_YIELD;
                                    }
                                }

                                finalPromise.fulfill(result);
                                return JSUndefined.INSTANCE;
                            }),
                            finalPromise,
                            context
                    ),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onRejected", 1, (ctx, thisArg, args) -> {
                                state = AsyncGeneratorState.COMPLETED;
                                JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                finalPromise.reject(error);
                                return JSUndefined.INSTANCE;
                            }),
                            finalPromise,
                            context
                    )
            );

            return finalPromise;
        } catch (Exception e) {
            state = AsyncGeneratorState.COMPLETED;
            JSPromise errorPromise = new JSPromise();
            errorPromise.reject(exception);
            return errorPromise;
        }
    }

    /**
     * Get the current state of the async generator.
     */
    public AsyncGeneratorState getState() {
        return state;
    }

    /**
     * Create an iterator result promise.
     *
     * @param value The iterator value
     * @param done  Whether iteration is complete
     * @return A promise that resolves to {value, done}
     */
    private JSPromise createIteratorResultPromise(JSValue value, boolean done) {
        JSPromise promise = new JSPromise();
        JSObject result = new JSObject();
        result.set("value", value);
        result.set("done", JSBoolean.valueOf(done));
        promise.fulfill(result);
        return promise;
    }

    /**
     * Create a simple async generator from a function that yields promises.
     *
     * @param yielder Function that returns promise values in sequence
     * @param ctx     The execution context
     * @return An async generator
     */
    public static JSAsyncGenerator create(AsyncYieldFunction yielder, JSContext ctx) {
        return new JSAsyncGenerator((inputValue, isThrow) -> {
            if (isThrow) {
                // If throwing, reject the promise
                JSPromise promise = new JSPromise();
                promise.reject(inputValue);
                return promise;
            }
            return yielder.yieldNext(inputValue);
        }, ctx);
    }

    /**
     * Functional interface for simple async yield functions.
     */
    @FunctionalInterface
    public interface AsyncYieldFunction {
        /**
         * Yield the next value.
         *
         * @param inputValue Value passed to next()
         * @return A promise that resolves to {value, done}
         */
        JSPromise yieldNext(JSValue inputValue);
    }

    @Override
    public String toString() {
        return "[object AsyncGenerator]";
    }
}
