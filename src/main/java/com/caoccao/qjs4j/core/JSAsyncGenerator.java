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

import java.util.ArrayDeque;
import java.util.Queue;

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
    public static final String NAME = "AsyncGenerator";
    private final JSContext context;
    private final AsyncGeneratorFunction generatorFunction;
    private final Queue<AsyncGeneratorRequest> requestQueue;
    private boolean drainScheduled;
    private JSValue returnValue;
    private AsyncGeneratorState state;
    private JSValue thrownValue;

    /**
     * Create a new async generator.
     *
     * @param generatorFunction The generator implementation
     * @param context           The execution context
     */
    public JSAsyncGenerator(AsyncGeneratorFunction generatorFunction, JSContext context) {
        super();
        this.state = AsyncGeneratorState.SUSPENDED_START;
        this.context = context;
        this.generatorFunction = generatorFunction;
        this.requestQueue = new ArrayDeque<>();
        this.drainScheduled = false;
        this.returnValue = JSUndefined.INSTANCE;
        this.thrownValue = null;

        // Add next() method
        this.set(PropertyKey.NEXT, new JSNativeFunction("next", 1, (childContext, thisArg, args) -> {
            JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            return next(value);
        }));

        // Add return() method
        this.set(PropertyKey.RETURN, new JSNativeFunction("return", 1, (childContext, thisArg, args) -> {
            JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            return return_(value);
        }));

        // Add throw() method
        this.set(PropertyKey.THROW, new JSNativeFunction("throw", 1, (childContext, thisArg, args) -> {
            JSValue exception = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            return throw_(exception);
        }));

        // Make this an async iterable via Symbol.asyncIterator
        this.set(PropertyKey.SYMBOL_ASYNC_ITERATOR,
                new JSNativeFunction("[Symbol.asyncIterator]", 0, (childContext, thisArg, args) -> thisArg));
    }

    /**
     * Create a simple async generator from a function that yields promises.
     *
     * @param yielder Function that returns promise values in sequence
     * @param context The execution context
     * @return An async generator
     */
    public static JSAsyncGenerator create(AsyncYieldFunction yielder, JSContext context) {
        return new JSAsyncGenerator((inputValue, requestKind) -> {
            if (requestKind == AsyncGeneratorRequestKind.THROW) {
                // If throwing, reject the promise
                JSPromise promise = context.createJSPromise();
                promise.reject(inputValue);
                return promise;
            }
            if (requestKind == AsyncGeneratorRequestKind.RETURN) {
                JSPromise promise = context.createJSPromise();
                JSObject result = context.createJSObject();
                result.set(PropertyKey.VALUE, inputValue);
                result.set(PropertyKey.DONE, JSBoolean.TRUE);
                promise.fulfill(result);
                return promise;
            }
            return yielder.yieldNext(inputValue);
        }, context);
    }

    private JSPromise createIteratorResultFromResult(JSValue result) {
        JSPromise promise = context.createJSPromise();
        promise.fulfill(result);
        return promise;
    }

    /**
     * Create an iterator result promise.
     *
     * @param value The iterator value
     * @param done  Whether iteration is complete
     * @return A promise that resolves to {value, done}
     */
    private JSPromise createIteratorResultPromise(JSValue value, boolean done) {
        JSPromise promise = context.createJSPromise();
        JSObject result = context.createJSObject();
        result.set(PropertyKey.VALUE, value);
        result.set(PropertyKey.DONE, JSBoolean.valueOf(done));
        promise.fulfill(result);
        return promise;
    }

    /**
     * Get the current state of the async generator.
     */
    public AsyncGeneratorState getState() {
        return state;
    }

    /**
     * Get the next value from the async generator.
     * ES2018 AsyncGenerator.prototype.next()
     *
     * @param value Value to send into the generator
     * @return A promise that resolves to {value, done}
     */
    public JSPromise next(JSValue value) {
        return enqueueRequest(AsyncGeneratorRequestKind.NEXT, value);
    }

    private void processResult(JSValue result) {
        if (result instanceof JSObject resultObj) {
            JSValue doneValue = resultObj.get("done");
            boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();
            if (done) {
                state = AsyncGeneratorState.COMPLETED;
                returnValue = resultObj.get(PropertyKey.VALUE);
            } else {
                state = AsyncGeneratorState.SUSPENDED_YIELD;
            }
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
        return enqueueRequest(AsyncGeneratorRequestKind.RETURN, value);
    }

    /**
     * Throw an exception into the async generator.
     * ES2018 AsyncGenerator.prototype.throw()
     *
     * @param exception The exception to throw
     * @return A promise that resolves to the next value or rejects
     */
    public JSPromise throw_(JSValue exception) {
        return enqueueRequest(AsyncGeneratorRequestKind.THROW, exception);
    }

    private void drainRequestQueue() {
        if (state == AsyncGeneratorState.EXECUTING) {
            return;
        }
        AsyncGeneratorRequest request = requestQueue.poll();
        if (request == null) {
            return;
        }
        try {
            executeRequest(request);
        } catch (RuntimeException e) {
            state = AsyncGeneratorState.COMPLETED;
            if (context.hasPendingException()) {
                JSValue error = context.getPendingException();
                context.clearAllPendingExceptions();
                request.promise().reject(error);
            } else {
                request.promise().reject(new JSString(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
            scheduleDrainRequestQueue();
        }
    }

    private JSPromise enqueueRequest(AsyncGeneratorRequestKind kind, JSValue value) {
        JSPromise promise = context.createJSPromise();
        requestQueue.offer(new AsyncGeneratorRequest(kind, value, promise));
        if (!drainScheduled) {
            drainRequestQueue();
        }
        if (context.hasPendingException()) {
            context.clearAllPendingExceptions();
        }
        return promise;
    }

    private void executeRequestWithGeneratorFunction(AsyncGeneratorRequest request) {
        boolean isThrow = request.kind() == AsyncGeneratorRequestKind.THROW;
        if (state == AsyncGeneratorState.COMPLETED) {
            if (isThrow) {
                request.promise().reject(request.value());
            } else {
                JSValue completedValue = request.kind() == AsyncGeneratorRequestKind.NEXT
                        ? JSUndefined.INSTANCE
                        : request.value();
                request.promise().fulfill(createIteratorResultObject(completedValue, true));
            }
            scheduleDrainRequestQueue();
            return;
        }

        state = AsyncGeneratorState.EXECUTING;
        if (request.kind() == AsyncGeneratorRequestKind.THROW) {
            thrownValue = request.value();
        }

        try {
            JSPromise resultPromise = generatorFunction.executeNext(request.value(), request.kind());
            if (resultPromise.getState() == JSPromise.PromiseState.FULFILLED) {
                JSValue result = resultPromise.getResult();
                processResult(result);
                request.promise().fulfill(result);
                scheduleDrainRequestQueue();
                return;
            }
            if (resultPromise.getState() == JSPromise.PromiseState.REJECTED) {
                state = AsyncGeneratorState.COMPLETED;
                request.promise().reject(resultPromise.getResult());
                scheduleDrainRequestQueue();
                return;
            }
            resultPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfilled", 1, (childContext, thisArg, args) -> {
                                JSValue result = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                processResult(result);
                                request.promise().fulfill(result);
                                scheduleDrainRequestQueue();
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context
                    ),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onRejected", 1, (childContext, thisArg, args) -> {
                                state = AsyncGeneratorState.COMPLETED;
                                JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                request.promise().reject(error);
                                scheduleDrainRequestQueue();
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context
                    )
            );
        } catch (Exception e) {
            state = AsyncGeneratorState.COMPLETED;
            if (context.hasPendingException()) {
                JSValue error = context.getPendingException();
                context.clearAllPendingExceptions();
                request.promise().reject(error);
            } else if (isThrow) {
                request.promise().reject(request.value());
            } else {
                String message = e.getMessage();
                request.promise().reject(new JSString("Async generator error: " + (message != null ? message : e.toString())));
            }
            scheduleDrainRequestQueue();
        }
    }

    private void executeRequest(AsyncGeneratorRequest request) {
        switch (request.kind()) {
            case NEXT, THROW, RETURN -> executeRequestWithGeneratorFunction(request);
        }
    }

    private JSObject createIteratorResultObject(JSValue value, boolean done) {
        JSObject result = context.createJSObject();
        result.set(PropertyKey.VALUE, value);
        result.set(PropertyKey.DONE, JSBoolean.valueOf(done));
        return result;
    }

    private void scheduleDrainRequestQueue() {
        if (drainScheduled) {
            return;
        }
        drainScheduled = true;
        context.enqueueMicrotask(() -> {
            drainScheduled = false;
            drainRequestQueue();
        });
    }

    @Override
    public String toString() {
        return "[object AsyncGenerator]";
    }

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

    /**
     * Functional interface for async generator implementation.
     */
    @FunctionalInterface
    public interface AsyncGeneratorFunction {
        /**
         * Execute the next step of the generator.
         *
         * @param inputValue   Value passed to next()/return()/throw()
         * @param requestKind  The async generator request kind
         * @return A promise that resolves to {value, done}
         */
        JSPromise executeNext(JSValue inputValue, AsyncGeneratorRequestKind requestKind);
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
    private record AsyncGeneratorRequest(AsyncGeneratorRequestKind kind, JSValue value, JSPromise promise) {
    }

    public enum AsyncGeneratorRequestKind {
        NEXT,
        RETURN,
        THROW
    }
}
