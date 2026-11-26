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

import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.Iterator;

/**
 * Represents an async iterator in JavaScript. Based on ES2018 async iteration protocol.
 * <p>
 * Async iterators return promises that resolve to iterator results: - next() returns {@code Promise<{value, done}>} -
 * Used by for-await-of loops - Identified by Symbol.asyncIterator
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
     * @param iteratorFunction
     *            The function that produces values
     * @param context
     *            The execution context
     */
    public JSAsyncIterator(AsyncIteratorFunction iteratorFunction, JSContext context) {
        super(context);
        this.iteratorFunction = iteratorFunction;
        this.context = context;

        // Add next() method
        this.set(PropertyKey.NEXT,
                new JSNativeFunction(context, "next", 0, (childContext, thisArg, args) -> iteratorFunction.next()));

        // Make this iterable via Symbol.asyncIterator
        JSSymbol asyncIteratorSymbol = JSSymbol.getWellKnownSymbol("asyncIterator");
        if (asyncIteratorSymbol != null) {
            this.set(PropertyKey.fromSymbol(asyncIteratorSymbol), new JSNativeFunction(context,
                    "[Symbol.asyncIterator]", 0, (childContext, thisArg, args) -> thisArg));
        }
    }

    /**
     * Close this async iterator by calling return() on the underlying iterator object. Per ES spec AsyncIteratorClose.
     *
     * @return A promise that resolves when the iterator is closed, or null if no return method
     */
    public JSPromise close() {
        JSObject iter = underlyingIterator;
        if (iter == null) {
            return null;
        }
        JSValue returnMethod = iter.get(PropertyKey.RETURN);
        if (context.hasPendingException()) {
            JSPromise rejected = context.createJSPromise();
            rejected.reject(context.takeExecutionException());
            return rejected;
        }
        if (!(returnMethod instanceof JSFunction returnFunc)) {
            return null;
        }
        JSValue result = returnFunc.call(context, iter, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            JSPromise rejected = context.createJSPromise();
            rejected.reject(context.takeExecutionException());
            return rejected;
        }
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

    private static JSPromise asyncFromSyncNext(JSContext context, JSObject iterator, JSValue next) {
        JSValue result;
        try {
            result = JSCall.call(context, next, iterator, JSValue.NO_ARGS);
        } catch (JSException e) {
            if (context.hasPendingException()) {
                context.clearAllPendingExceptions();
            }
            return createRejectedPromise(context, e.getErrorValue());
        } catch (Exception e) {
            JSValue reason = context.hasPendingException()
                    ? context.takeExecutionException()
                    : new JSString(e.getMessage() != null ? e.getMessage() : e.toString());
            return createRejectedPromise(context, reason);
        }
        if (context.hasPendingException()) {
            return createRejectedPromise(context, context.takeExecutionException());
        }
        if (!(result instanceof JSObject object)) {
            JSValue error = context.throwTypeError("iterator result must be an object");
            context.clearPendingException();
            return createRejectedPromise(context, error);
        }
        JSValue value = object.get(PropertyKey.VALUE);
        if (context.hasPendingException()) {
            return createRejectedPromise(context, context.takeExecutionException());
        }
        JSValue done = object.get(PropertyKey.DONE);
        if (context.hasPendingException()) {
            return createRejectedPromise(context, context.takeExecutionException());
        }
        return createAsyncFromSyncResultPromise(context, value, JSTypeConversions.toBoolean(done).value(), iterator);
    }

    /**
     * %AsyncFromSyncIteratorPrototype%.return ( [ value ] ) ES2024 27.1.6.2.2, as AsyncIteratorClose calls it: with no
     * value. Every abrupt step rejects the returned promise rather than throwing.
     */
    private static JSPromise asyncFromSyncReturn(JSContext context, JSObject syncIterator) {
        JSValue result;
        try {
            JSValue returnMethod = syncIterator.get(PropertyKey.RETURN);
            if (context.hasPendingException()) {
                return createRejectedPromise(context, context.takeExecutionException());
            }
            if (returnMethod.isNullOrUndefined()) {
                // Step 7: no return method — resolve with { value: undefined, done: true }.
                JSObject iteratorResult = JSIteratorHelper.createResult(context, JSUndefined.INSTANCE, true);
                JSPromise promise = context.createJSPromise();
                promise.resolve(context, iteratorResult);
                return promise;
            }
            if (!(returnMethod instanceof JSFunction returnFunction)) {
                JSValue error = context.throwTypeError("iterator return is not a function");
                context.clearPendingException();
                return createRejectedPromise(context, error);
            }
            result = returnFunction.call(context, syncIterator, JSValue.NO_ARGS);
        } catch (JSException e) {
            context.clearPendingException();
            return createRejectedPromise(context, e.getErrorValue());
        } catch (JSVirtualMachineException e) {
            JSValue error = context.hasPendingException() ? context.takeExecutionException() : e.getJsValue();
            return createRejectedPromise(context, error != null ? error : context.throwError(e.getMessage()));
        }
        if (context.hasPendingException()) {
            return createRejectedPromise(context, context.takeExecutionException());
        }
        if (!(result instanceof JSObject resultObject)) {
            JSValue error = context
                    .throwTypeError("Iterator result " + describeIteratorResult(result) + " is not an object");
            context.clearPendingException();
            return createRejectedPromise(context, error);
        }
        JSValue done = resultObject.get(PropertyKey.DONE);
        if (context.hasPendingException()) {
            return createRejectedPromise(context, context.takeExecutionException());
        }
        JSValue resultValue = resultObject.get(PropertyKey.VALUE);
        if (context.hasPendingException()) {
            return createRejectedPromise(context, context.takeExecutionException());
        }
        // AsyncFromSyncIteratorContinuation: awaits the value, then resolves { value, done }.
        return createAsyncFromSyncResultPromise(context, resultValue,
                JSTypeConversions.toBoolean(done) == JSBoolean.TRUE);
    }

    private static void closeSyncIteratorAfterRejection(JSContext context, JSObject iterator) {
        if (iterator == null) {
            return;
        }
        try {
            JSValue method = iterator.get(PropertyKey.RETURN);
            if (!context.hasPendingException() && !method.isNullOrUndefined()) {
                JSCall.call(context, method, iterator, JSValue.NO_ARGS);
            }
        } catch (JSException | JSVirtualMachineException ignored) {
            // IteratorClose with a throw completion preserves the original rejection.
        } finally {
            context.clearAllPendingExceptions();
        }
    }

    /**
     * The iterator a {@code for await} loop over a sync iterable keeps, for closing: an object whose {@code return} is
     * %AsyncFromSyncIteratorPrototype%.return, so a {@code break} or {@code return} awaits a promise exactly as it
     * would for the wrapper the specification creates (CreateAsyncFromSyncIterator). The loop's {@code next} is
     * supplied separately.
     *
     * @param context
     *            the context
     * @param syncIterator
     *            the sync iterator
     * @return the object to close the loop through
     */
    public static JSObject createAsyncFromSyncIteratorForClose(JSContext context, JSObject syncIterator) {
        JSObject wrapper = context.createJSObject();
        wrapper.set(PropertyKey.RETURN, new JSNativeFunction(context, "return", 1,
                (childContext, thisArg, args) -> asyncFromSyncReturn(childContext, syncIterator)));
        return wrapper;
    }

    /** The cached next method used by a for-await loop over a synchronous iterator. */
    public static JSNativeFunction createAsyncFromSyncNextMethod(JSContext context, JSObject iterator, JSValue next) {
        return new JSNativeFunction(context, "next", 0,
                (child, receiver, args) -> asyncFromSyncNext(child, iterator, next));
    }

    /**
     * Create an async-from-sync iterator result promise. Per ES spec AsyncFromSyncIteratorContinuation: resolves the
     * value via PromiseResolve before placing it in the iterator result. This means if value is a promise/thenable, it
     * gets awaited.
     *
     * @param context
     *            The execution context
     * @param value
     *            The iterator value (may be a promise/thenable)
     * @param done
     *            Whether iteration is complete
     * @return A promise that resolves to the iterator result (with resolved value)
     */
    public static JSPromise createAsyncFromSyncResultPromise(JSContext context, JSValue value, boolean done) {
        return createAsyncFromSyncResultPromise(context, value, done, null);
    }

    /** The next() continuation closes a non-completed sync iterator if awaiting its value rejects. */
    public static JSPromise createAsyncFromSyncResultPromise(JSContext context, JSValue value, boolean done,
            JSObject syncIterator) {
        JSPromise valueWrapperPromise;
        if (value instanceof JSPromise promiseValue) {
            // PromiseResolve(%Promise%, value) performs observable constructor access.
            promiseValue.get(PropertyKey.CONSTRUCTOR);
            if (context.hasPendingException()) {
                JSValue reason = context.takeExecutionException();
                if (!done) {
                    closeSyncIteratorAfterRejection(context, syncIterator);
                }
                return createRejectedPromise(context, reason);
            }
            valueWrapperPromise = promiseValue;
        } else {
            valueWrapperPromise = context.createJSPromise();
            valueWrapperPromise.resolve(context, value);
            if (context.hasPendingException()) {
                JSValue reason = context.takeExecutionException();
                if (!done) {
                    closeSyncIteratorAfterRejection(context, syncIterator);
                }
                return createRejectedPromise(context, reason);
            }
        }

        JSPromise resultPromise = context.createJSPromise();
        valueWrapperPromise.observe(context,
                new JSNativeFunction(context, "onResolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                    JSValue resolved = JSValue.argumentOrUndefined(callbackArgs, 0);
                    JSObject result = JSIteratorHelper.createResult(context, resolved, done);
                    resultPromise.fulfill(result);
                    return JSUndefined.INSTANCE;
                }), new JSNativeFunction(context, "onReject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                    JSValue reason = JSValue.argumentOrUndefined(callbackArgs, 0);
                    if (!done) {
                        closeSyncIteratorAfterRejection(context, syncIterator);
                    }
                    resultPromise.reject(reason);
                    return JSUndefined.INSTANCE;
                }));
        return resultPromise;
    }

    /**
     * Create an IteratorResult object that resolves in a promise.
     *
     * @param context
     *            The execution context
     * @param value
     *            The iterator value
     * @param done
     *            Whether iteration is complete
     * @return A promise that resolves to the iterator result
     */
    public static JSPromise createIteratorResultPromise(JSContext context, JSValue value, boolean done) {
        JSPromise promise = context.createJSPromise();
        JSObject result = JSIteratorHelper.createResult(context, value, done);
        promise.fulfill(result);
        return promise;
    }

    static JSPromise createRejectedPromise(JSContext context, JSValue reason) {
        JSPromise promise = context.createJSPromise();
        promise.reject(reason);
        return promise;
    }

    /**
     * A primitive iterator result as V8 names it in "Iterator result X is not an object", without running any script.
     *
     * @param value
     *            the primitive
     * @return its description
     */
    public static String describeIteratorResult(JSValue value) {
        if (value instanceof JSSymbol symbol) {
            return symbol.toString();
        }
        return JSTypeConversions.toString(null, value).value();
    }

    /**
     * Execute a for-await-of loop. Iterates asynchronously, waiting for each promise to resolve.
     *
     * @param context
     *            The execution context
     * @param iterable
     *            The iterable to loop over
     * @param callback
     *            The callback to execute for each value
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
    public static JSPromise forAwaitOfIterator(JSContext context, JSAsyncIterator iterator,
            AsyncIterationCallback callback) {
        JSPromise completionPromise = context.createJSPromise();
        iterateNext(iterator, callback, context, completionPromise);
        return completionPromise;
    }

    /**
     * Create an async iterator from an array. Returns values one at a time asynchronously.
     *
     * @param array
     *            The array to iterate
     * @param context
     *            The execution context
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
     * Create an async iterator from a Java Iterable. Useful for wrapping Java collections.
     *
     * @param iterable
     *            The Java iterable
     * @param context
     *            The execution context
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
     * Create an async iterator from a regular iterator. Per ES spec CreateAsyncFromSyncIterator: values from the sync
     * iterator are resolved via PromiseResolve before being placed in the iterator result. This means promise values
     * are awaited/unwrapped.
     *
     * @param iterator
     *            The synchronous iterator
     * @param context
     *            The execution context
     * @return An async iterator
     */
    public static JSAsyncIterator fromIterator(JSIterator iterator, JSContext context) {
        JSAsyncIterator asyncIter = new JSAsyncIterator(() -> {
            // Per ES spec: IfAbruptRejectPromise - catch sync iterator next() exceptions
            JSObject result;
            try {
                result = iterator.next();
            } catch (Exception e) {
                return createRejectedPromise(context, context.takeRejectionReason(e));
            }
            if (context.hasPendingException()) {
                return createRejectedPromise(context, context.takeExecutionException());
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
     * Create an async iterator that produces values from a promise. When the promise resolves, yields the value once
     * and then completes.
     *
     * @param context
     *            The execution context
     * @param promise
     *            The promise to await
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
                promise.addReactions(new JSPromise.ReactionRecord(
                        new JSNativeFunction(context, "onFulfilled", 1, (childContext, thisArg, args) -> {
                            JSValue value = JSValue.argumentOrUndefined(args, 0);
                            JSObject result = JSIteratorHelper.createResult(childContext, value, false);
                            resultPromise.fulfill(result);
                            return JSUndefined.INSTANCE;
                        }), resultPromise, context), new JSPromise.ReactionRecord(
                                new JSNativeFunction(context, "onRejected", 1, (childContext, thisArg, args) -> {
                                    // If promise rejects, propagate the rejection
                                    JSValue reason = JSValue.argumentOrUndefined(args, 0);
                                    resultPromise.reject(reason);
                                    return JSUndefined.INSTANCE;
                                }), resultPromise, context));
                return resultPromise;
            }
        }, context);
    }

    /**
     * Get an async iterator from a value. Checks for Symbol.asyncIterator, falls back to Symbol.iterator.
     *
     * @param iterable
     *            The value to get an iterator from
     * @param context
     *            The execution context
     * @return An async iterator, or null if not iterable
     */
    public static JSAsyncIterator getAsyncIterator(JSValue iterable, JSContext context) {
        if (!(iterable instanceof JSObject obj)) {
            return null;
        }

        // First, try Symbol.asyncIterator
        JSValue asyncIteratorMethod = obj.get(PropertyKey.SYMBOL_ASYNC_ITERATOR);

        if (asyncIteratorMethod instanceof JSFunction asyncIterFunc) {
            JSValue result = asyncIterFunc.call(context, iterable, JSValue.NO_ARGS);
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
            JSValue result = iterFunc.call(context, iterable, JSValue.NO_ARGS);
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
     * @param value
     *            The value to check
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
     * Internal helper to iterate to the next value. Uses continuation-passing style to avoid deep recursion.
     */
    private static void iterateNext(JSAsyncIterator iterator, AsyncIterationCallback callback, JSContext context,
            JSPromise completionPromise) {
        // Get next promise
        JSPromise nextPromise = iterator.next();

        // When next promise resolves, process the result
        nextPromise.observe(context,
                new JSNativeFunction(context, "onNextFulfilled", 1, (childContext, thisArg, args) -> {
                    JSValue result = JSValue.argumentOrUndefined(args, 0);

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
                    processingPromise.observe(childContext,
                            new JSNativeFunction(context, "onProcessed", 1, (innerContext, innerThisArg, innerArgs) -> {
                                // Continue iteration
                                iterateNext(iterator, callback, childContext, completionPromise);
                                return JSUndefined.INSTANCE;
                            }), new JSNativeFunction(context, "onProcessError", 1,
                                    (innerContext, innerThisArg, innerArgs) -> {
                                        // If processing fails, reject completion promise
                                        JSValue error = innerArgs.length > 0 ? innerArgs[0] : JSUndefined.INSTANCE;
                                        completionPromise.reject(error);
                                        return JSUndefined.INSTANCE;
                                    }));

                    return JSUndefined.INSTANCE;
                }), new JSNativeFunction(context, "onNextRejected", 1, (childContext, thisArg, args) -> {
                    // If next() fails, reject completion promise
                    JSValue error = JSValue.argumentOrUndefined(args, 0);
                    JSPromise closePromise = iterator.close();
                    if (closePromise == null) {
                        completionPromise.reject(error);
                        return JSUndefined.INSTANCE;
                    }
                    closePromise.observe(childContext, new JSNativeFunction(context, "onCloseResolve", 1,
                            (innerContext, innerThisArg, innerArgs) -> {
                                completionPromise.reject(error);
                                return JSUndefined.INSTANCE;
                            }), new JSNativeFunction(context, "onCloseReject", 1,
                                    (innerContext, innerThisArg, innerArgs) -> {
                                        // Preserve the original next() rejection reason.
                                        completionPromise.reject(error);
                                        return JSUndefined.INSTANCE;
                                    }));
                    return JSUndefined.INSTANCE;
                }));
    }

    static JSPromise rejectPendingException(JSContext context) {
        return createRejectedPromise(context, context.takeExecutionException());
    }

    static JSPromise rejectTypeError(JSContext context, String message) {
        JSValue error = context.throwTypeError(message);
        context.clearAllPendingExceptions();
        return createRejectedPromise(context, error);
    }

    /**
     * Convert an async iterable to an array. Waits for all values to be produced asynchronously.
     *
     * @param context
     *            The execution context
     * @param iterable
     *            The async iterable
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
        }).addReactions(new JSPromise.ReactionRecord(
                new JSNativeFunction(context, "onComplete", 1, (childContext, thisArg, args) -> {
                    resultPromise.fulfill(array);
                    return JSUndefined.INSTANCE;
                }), resultPromise, context),
                new JSPromise.ReactionRecord(resultPromise.rejectHandler(context, "onError"), resultPromise, context));

        return resultPromise;
    }

    /**
     * Wrap any JSObject that has a next() method as an async iterator. The next() method is expected to return a
     * Promise that resolves to {value, done}.
     */
    public static JSAsyncIterator wrapAsAsyncIterator(JSObject iterObj, JSContext context) {
        JSValue nextMethod = iterObj.get(PropertyKey.NEXT);
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            return null;
        }
        JSAsyncIterator asyncIter = new JSAsyncIterator(() -> {
            JSValue result = nextFunc.call(context, iterObj, JSValue.NO_ARGS);
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
                result = nextFunc.call(context, iterObj, JSValue.NO_ARGS);
            } catch (Exception e) {
                return createRejectedPromise(context, context.takeRejectionReason(e));
            }
            if (context.hasPendingException()) {
                return createRejectedPromise(context, context.takeExecutionException());
            }
            if (result instanceof JSObject resultObj) {
                JSValue value = resultObj.get(PropertyKey.VALUE);
                if (context.hasPendingException()) {
                    return createRejectedPromise(context, context.takeExecutionException());
                }
                JSValue doneValue = resultObj.get(PropertyKey.DONE);
                if (context.hasPendingException()) {
                    return createRejectedPromise(context, context.takeExecutionException());
                }
                boolean done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;
                if (context.hasPendingException()) {
                    return createRejectedPromise(context, context.takeExecutionException());
                }
                // Per ES spec CreateAsyncFromSyncIterator: resolve promise values
                return createAsyncFromSyncResultPromise(context, value, done);
            }
            return createRejectedPromise(context, new JSString("Iterator result is not an object"));
        }, context);
        asyncIter.setUnderlyingIterator(iterObj);
        return asyncIter;
    }

    /**
     * Functional interface for async iterator next() implementation.
     */
    @FunctionalInterface
    public interface AsyncIterationCallback {
        /**
         * Process an iteration value. Called asynchronously for each value.
         *
         * @param value
         *            The current value
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
