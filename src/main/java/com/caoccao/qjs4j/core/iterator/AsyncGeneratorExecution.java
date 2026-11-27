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

package com.caoccao.qjs4j.core.iterator;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.error.JSError;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.vm.YieldResult;

import com.caoccao.qjs4j.core.iterator.JSAsyncGenerator.AsyncGeneratorRequestKind;

/** Drives one bytecode async generator, including suspended awaits and yield* delegation. */
public final class AsyncGeneratorExecution implements JSAsyncGenerator.AsyncGeneratorFunction {
    private final JSContext context;
    private JSObject delegate;
    private final JSGenerator driver;
    private final JSAsyncGenerator generator;
    private JSFunction nextMethod;
    private final JSGeneratorState state;

    private AsyncGeneratorExecution(JSContext context, JSGeneratorState state, JSGenerator driver) {
        this.context = context;
        this.state = state;
        this.driver = driver;
        generator = new JSAsyncGenerator(this, context);
    }

    private JSPromise.ReactionRecord awaitReaction(String name, JSGeneratorState.ResumeKind resumeKind,
            AsyncGeneratorRequestKind requestKind, JSPromise requestPromise) {
        JSNativeFunction callback = new JSNativeFunction(context, name, 1, (childContext, thisArg, args) -> {
            state.setPendingResumeRecord(resumeKind, JSValue.argumentOrUndefined(args, 0));
            JSPromise resumed = executeNext(JSUndefined.INSTANCE, requestKind);
            // Settle synchronously when possible to preserve the async generator's microtask order.
            resumed.markHandled();
            if (resumed.getState() == JSPromise.PromiseState.FULFILLED) {
                generator.completeCurrentRequest(resumed.getResult());
            } else if (resumed.getState() == JSPromise.PromiseState.REJECTED) {
                requestPromise.reject(resumed.getResult());
            } else {
                resumed.observe(context, new JSNativeFunction(context, "onResumeResolve", 1, (c, receiver, values) -> {
                    generator.completeCurrentRequest(JSValue.argumentOrUndefined(values, 0));
                    return JSUndefined.INSTANCE;
                }), requestPromise.rejectHandler(context, "onResumeReject"));
            }
            return JSUndefined.INSTANCE;
        });
        return new JSPromise.ReactionRecord(callback, null, context);
    }

    @Override
    public JSPromise executeNext(JSValue value, AsyncGeneratorRequestKind kind) {
        JSPromise request = context.createJSPromise();
        request.markHandled();
        if (state.isCompleted()) {
            request.fulfill(JSIteratorHelper.createResult(context, JSUndefined.INSTANCE, true));
            return request;
        }
        try {
            if (delegate != null) {
                return switch (kind) {
                    case NEXT -> handleDelegatedIteratorResult(
                            createAsyncDelegatedNextPromise(context, delegate, value, nextMethod), delegate, false);
                    case THROW -> handleDelegatedIteratorResult(
                            createAsyncFromSyncDelegatedThrowPromise(context, delegate, value), delegate, false);
                    case RETURN -> returnToDelegate(value, delegate, request);
                };
            }
            AsyncGeneratorRequestKind resumeKind = kind;
            if (kind == AsyncGeneratorRequestKind.RETURN && driver.getState() == JSGenerator.State.SUSPENDED_YIELD
                    && value instanceof JSPromise input) {
                // PromiseResolve observes the constructor before a return completion at a yield.
                input.get(PropertyKey.CONSTRUCTOR);
                if (context.hasPendingException()) {
                    value = context.getPendingException();
                    context.clearAllPendingExceptions();
                    resumeKind = AsyncGeneratorRequestKind.THROW;
                }
            }
            JSObject result = switch (resumeKind) {
                case NEXT -> driver.next(value);
                case RETURN -> driver.returnMethod(value);
                case THROW -> driver.throwMethod(value);
            };
            if (state.isAwaitSuspended()) {
                JSPromise awaited = context.getVirtualMachine().consumeAwaitSuspensionPromise();
                if (awaited == null) {
                    request.reject(new JSString("Async generator await suspension promise is missing"));
                } else {
                    awaited.addReactions(
                            awaitReaction("onAsyncGenAwaitResolve", JSGeneratorState.ResumeKind.NEXT, kind, request),
                            awaitReaction("onAsyncGenAwaitReject", JSGeneratorState.ResumeKind.THROW, kind, request));
                }
            } else if (!rejectPendingYield(context, request)) {
                boolean done = state.isCompleted();
                YieldResult lastYield = done ? null : state.getLastYieldResult();
                if (lastYield != null && lastYield.isYieldStar()) {
                    startDelegation(request, result, lastYield);
                } else {
                    delegate = null;
                    nextMethod = null;
                    fulfillAsyncYield(context, request, result.get(PropertyKey.VALUE), done);
                }
            }
        } catch (Exception exception) {
            request.reject(context.takeRejectionReason(exception));
        }
        return request;
    }

    /**
     * Handle the result of a delegated iterator operation (next/throw/return). When done=false: resolve outerPromise
     * with the result, keep the delegate. When done=true: set yieldStarCompletionValue, resume the generator, chain
     * result.
     */
    private JSPromise handleDelegatedIteratorResult(JSPromise delegatedPromise, JSObject delegateIterator,
            boolean isReturnCompletion) {
        JSPromise outerPromise = context.createJSPromise();
        delegatedPromise.observe(context, new JSNativeFunction(context, "onDelegatedResolve", 1,
                (childContext, callbackThisArg, callbackArgs) -> {
                    JSValue resultValue = JSValue.argumentOrUndefined(callbackArgs, 0);
                    if (!(resultValue instanceof JSObject resultObject)) {
                        delegate = null;
                        state.setCompleted(true);
                        outerPromise.reject(childContext.throwTypeError("Iterator result is not an object"));
                        childContext.clearAllPendingExceptions();
                        return JSUndefined.INSTANCE;
                    }
                    JSValue doneValue = resultObject.get(PropertyKey.DONE);
                    if (childContext.hasPendingException()) {
                        delegate = null;
                        state.setCompleted(true);
                        outerPromise.reject(childContext.takeExecutionException());
                        return JSUndefined.INSTANCE;
                    }
                    boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
                    if (done) {
                        // Delegation completed — resume the generator past yield*
                        delegate = null;
                        JSValue value = resultObject.get(PropertyKey.VALUE);
                        if (childContext.hasPendingException()) {
                            state.setCompleted(true);
                            outerPromise.reject(childContext.takeExecutionException());
                            return JSUndefined.INSTANCE;
                        }
                        // Set pending resume record so the generator resumes
                        // from saved execution state (after yield*) with the completion value.
                        // RETURN: generator force-returns with value (enters finally blocks only)
                        // NEXT: completion value becomes the yield* expression result
                        state.setPendingResumeRecord(isReturnCompletion
                                ? JSGeneratorState.ResumeKind.RETURN
                                : JSGeneratorState.ResumeKind.NEXT, value);
                        resumeDelegation(childContext, outerPromise);
                    } else {
                        // Not done — yield the value to the caller
                        delegate = delegateIterator;
                        outerPromise.fulfill(resultValue);
                    }
                    return JSUndefined.INSTANCE;
                }),
                new JSNativeFunction(context, "onDelegatedReject", 1, (childContext, callbackThisArg, callbackArgs) -> {
                    delegate = null;
                    JSValue error = JSValue.argumentOrUndefined(callbackArgs, 0);
                    // Per ES2024: errors from Await(innerResult) and IteratorValue
                    // should propagate inside the generator (? notation in spec).
                    // Route the error back through the generator via THROW resume
                    // so the generator's try/catch can intercept it.
                    state.setPendingResumeRecord(JSGeneratorState.ResumeKind.THROW, error);
                    resumeDelegation(childContext, outerPromise);
                    return JSUndefined.INSTANCE;
                }));
        return outerPromise;
    }

    private void resumeDelegation(JSContext reactionContext, JSPromise outer) {
        JSPromise resumed = executeNext(JSUndefined.INSTANCE, AsyncGeneratorRequestKind.NEXT);
        resumed.observe(reactionContext, outer.fulfillHandler(context, "onResumeResolve"),
                outer.rejectHandler(context, "onResumeReject"));
    }

    private JSPromise returnToDelegate(JSValue value, JSObject iterator, JSPromise request) {
        JSPromise awaited = context.createJSPromise();
        awaited.resolve(context, value);
        if (!rejectPendingYield(context, request)) {
            awaited.observe(context,
                    new JSNativeFunction(context, "onUnwrapReturnResolve", 1, (ctx, receiver, args) -> {
                        JSPromise result = createAsyncFromSyncDelegatedReturnPromise(context, iterator,
                                JSValue.argumentOrUndefined(args, 0));
                        handleDelegatedIteratorResult(result, iterator, true).observe(context,
                                request.fulfillHandler(context, "onDelegateReturnResolve"),
                                request.rejectHandler(context, "onDelegateReturnReject"));
                        return JSUndefined.INSTANCE;
                    }), request.rejectHandler(context, "onUnwrapReturnReject"));
        }
        return request;
    }

    private void startDelegation(JSPromise request, JSObject result, YieldResult lastYield) {
        delegate = lastYield.delegateIterator();
        nextMethod = lastYield.cachedNextMethod() instanceof JSFunction cached ? cached : null;
        JSPromise yielded = context.createJSPromise();
        fulfillAsyncYieldStarResult(context, yielded, result, delegate, lastYield.nativeAsyncIterator());
        // An immediately available value must reach the caller without an extra reaction tick.
        if (yielded.getState() == JSPromise.PromiseState.FULFILLED && yielded.getResult() instanceof JSObject object
                && !object.get(PropertyKey.DONE).isBooleanTrue()) {
            request.fulfill(object);
            return;
        }
        handleDelegatedIteratorResult(yielded, lastYield.delegateIterator(), false).observe(context,
                request.fulfillHandler(context, "onYieldStarResolve"),
                request.rejectHandler(context, "onYieldStarReject"));
    }

    private static JSPromise awaitAndProcessIteratorResult(JSContext context, JSObject rawResult,
            JSObject delegateIterator) {
        if (rawResult instanceof JSPromise promiseResult) {
            JSPromise resultPromise = context.createJSPromise();
            promiseResult.observe(context,
                    delegatedResultHandler(context, "onAwaitResult", delegateIterator, resultPromise),
                    resultPromise.rejectHandler(context, "onAwaitReject"));
            return resultPromise;
        }
        JSValue thenMethod = rawResult.get(PropertyKey.THEN);
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        if (thenMethod instanceof JSFunction thenFunc) {
            JSPromise resultPromise = context.createJSPromise();
            try {
                thenFunc.call(context, rawResult,
                        new JSValue[]{delegatedResultHandler(context, "", delegateIterator, resultPromise),
                                resultPromise.rejectHandler(context, "")});
            } catch (Exception e) {
                JSValue error = context.takeRejectionReason(e);
                return JSAsyncIterator.createRejectedPromise(context, error);
            }
            if (context.hasPendingException()) {
                return JSAsyncIterator.rejectPendingException(context);
            }
            return resultPromise;
        }

        // Non-thenable, non-promise: read done/value directly
        return readDoneValueAndCreatePromise(context, rawResult, delegateIterator);
    }

    private static void awaitYieldStar(JSContext context, JSPromise source, JSPromise promise,
            JSObject delegateIterator, boolean nativeAsyncIterator, String onResolve, String onReject) {
        source.observe(context, new JSNativeFunction(context, onResolve, 1, (childContext, thisArg, args) -> {
            JSValue resolved = JSValue.argumentOrUndefined(args, 0);
            if (!(resolved instanceof JSObject resolvedObj)) {
                JSError typeError = context.throwTypeError("Iterator result is not an object");
                context.clearPendingException();
                promise.reject(typeError);
                return JSUndefined.INSTANCE;
            }
            fulfillAsyncYieldStarResult(context, promise, resolvedObj, delegateIterator, nativeAsyncIterator);
            return JSUndefined.INSTANCE;
        }), promise.rejectHandler(context, onReject));
    }

    private static JSPromise callDelegatedMethod(JSContext context, JSObject iterator, JSValue method, JSValue argument,
            String name) {
        if (!(method instanceof JSFunction function)) {
            return JSAsyncIterator.rejectTypeError(context, "iterator " + name + " is not a function");
        }
        JSValue result = function.call(context, iterator, new JSValue[]{argument});
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        if (!(result instanceof JSObject object)) {
            return JSAsyncIterator.rejectTypeError(context, "iterator must return an object");
        }
        return awaitAndProcessIteratorResult(context, object, iterator);
    }

    public static JSAsyncGenerator create(JSContext context, JSGeneratorState state, JSGenerator driver) {
        return new AsyncGeneratorExecution(context, state, driver).generator;
    }

    private static JSPromise createAsyncDelegatedNextPromise(JSContext context, JSObject delegateIterator,
            JSValue inputValue, JSFunction cachedNextMethod) {
        JSValue method = cachedNextMethod != null ? cachedNextMethod : delegateIterator.get(PropertyKey.NEXT);
        if (cachedNextMethod == null && context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        try {
            return callDelegatedMethod(context, delegateIterator, method, inputValue, "next");
        } catch (Exception e) {
            JSValue error = context.takeRejectionReason(e);
            return JSAsyncIterator.createRejectedPromise(context, error);
        }
    }

    private static JSPromise createAsyncFromSyncDelegatedReturnPromise(JSContext context, JSObject delegateIterator,
            JSValue argumentValue) {
        try {
            JSValue returnMethodValue = delegateIterator.get(PropertyKey.RETURN);
            if (context.hasPendingException()) {
                return JSAsyncIterator.rejectPendingException(context);
            }
            if (returnMethodValue.isNullOrUndefined()) {
                // Per ES2024 yield* return: "If generatorKind is async, set received.[[Value]] to ?
                // Await(received.[[Value]])"
                JSPromise returnAwaitPromise = context.createJSPromise();
                returnAwaitPromise.resolve(context, argumentValue);
                if (context.hasPendingException()) {
                    return JSAsyncIterator.rejectPendingException(context);
                }
                JSPromise returnResultPromise = context.createJSPromise();
                returnAwaitPromise.observe(context,
                        new JSNativeFunction(context, "onReturnUndefinedAwaitResolve", 1, (childCtx, thisArg, args) -> {
                            JSValue awaitedValue = JSValue.argumentOrUndefined(args, 0);
                            JSObject result = JSIteratorHelper.createResult(context, awaitedValue, true);
                            returnResultPromise.fulfill(result);
                            return JSUndefined.INSTANCE;
                        }), returnResultPromise.rejectHandler(context, "onReturnUndefinedAwaitReject"));
                return returnResultPromise;
            }
            return callDelegatedMethod(context, delegateIterator, returnMethodValue, argumentValue, "return");
        } catch (Exception e) {
            JSValue error = context.takeRejectionReason(e);
            return JSAsyncIterator.createRejectedPromise(context, error);
        }
    }

    private static JSPromise createAsyncFromSyncDelegatedThrowPromise(JSContext context, JSObject delegateIterator,
            JSValue argumentValue) {
        JSValue throwMethodValue = delegateIterator.get(PropertyKey.THROW);
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        if (throwMethodValue.isNullOrUndefined()) {
            JSValue returnMethodValue = delegateIterator.get(PropertyKey.RETURN);
            if (context.hasPendingException()) {
                return JSAsyncIterator.rejectPendingException(context);
            }
            if (!returnMethodValue.isNullOrUndefined()) {
                if (!(returnMethodValue instanceof JSFunction returnFunction)) {
                    return JSAsyncIterator.rejectTypeError(context, "iterator return is not a function");
                }
                JSValue closeResult = returnFunction.call(context, delegateIterator, JSValue.NO_ARGS);
                if (context.hasPendingException()) {
                    return JSAsyncIterator.rejectPendingException(context);
                }
                if (!(closeResult instanceof JSObject)) {
                    return JSAsyncIterator.rejectTypeError(context, "iterator must return an object");
                }
            }
            return JSAsyncIterator.rejectTypeError(context, "iterator does not have a throw method");
        }
        return callDelegatedMethod(context, delegateIterator, throwMethodValue, argumentValue, "throw");
    }

    private static JSNativeFunction delegatedResultHandler(JSContext context, String name, JSObject delegateIterator,
            JSPromise resultPromise) {
        return new JSNativeFunction(context, name, 1, (childContext, thisArg, args) -> {
            JSValue value = JSValue.argumentOrUndefined(args, 0);
            if (!(value instanceof JSObject result)) {
                JSValue error = childContext.throwTypeError("Iterator result is not an object");
                childContext.clearPendingException();
                resultPromise.reject(error);
            } else {
                JSPromise inner = readDoneValueAndCreatePromise(childContext, result, delegateIterator);
                inner.observe(childContext, resultPromise.fulfillHandler(context, "onInner"),
                        resultPromise.rejectHandler(context, "onInnerErr"));
            }
            return JSUndefined.INSTANCE;
        });
    }

    /**
     * Per ES spec AsyncGeneratorYield/AsyncGeneratorResolve: Await the yielded/returned value before placing it in the
     * iterator result. If the value is a promise or thenable, resolve it first; otherwise use it directly.
     */
    private static void fulfillAsyncYield(JSContext context, JSPromise promise, JSValue value, boolean done) {
        // Per ES2024 spec: yield in async generators first awaits the yielded value
        // (YieldExpression step 4: "set value to ? Await(value)"), then yields the
        // awaited result which synchronously resolves the .next() promise via
        // AsyncGeneratorCompleteStep. To achieve correct microtask interleaving,
        // already-settled promises and primitives are resolved synchronously here
        // so that executeRequestWithGeneratorFunction can provide the single "await"
        // tick via its microtask scheduling.
        if (value instanceof JSPromise promiseValue) {
            // Per ES spec PromiseResolve: access .constructor for observable side effects
            promiseValue.get(PropertyKey.CONSTRUCTOR);
            if (context.hasPendingException()) {
                JSValue error = context.takePendingException();
                promise.reject(error);
                return;
            }
            if (promiseValue.getState() == JSPromise.PromiseState.FULFILLED) {
                // Already-fulfilled promise: unwrap and fulfill synchronously
                JSValue resolvedValue = promiseValue.getResult();
                JSObject result = JSIteratorHelper.createResult(context, resolvedValue, done);
                promise.fulfill(result);
                return;
            }
            if (promiseValue.getState() == JSPromise.PromiseState.REJECTED) {
                // Awaited directly rather than through a reaction, so record that it is handled.
                promiseValue.markHandled();
                promise.reject(promiseValue.getResult());
                return;
            }
            // Pending promise: add reaction to fulfill when resolved (1 tick)
            promiseValue.observe(context,
                    new JSNativeFunction(context, "onResolve", 1, (childContext, thisArg, args) -> {
                        JSValue resolvedValue = JSValue.argumentOrUndefined(args, 0);
                        JSObject result = JSIteratorHelper.createResult(context, resolvedValue, done);
                        promise.fulfill(result);
                        return JSUndefined.INSTANCE;
                    }), promise.rejectHandler(context, "onReject"));
            return;
        }
        // Check for thenable objects
        if (value instanceof JSObject obj) {
            JSValue thenMethod = obj.get(PropertyKey.THEN);
            if (context.hasPendingException()) {
                JSValue error = context.takePendingException();
                promise.reject(error);
                return;
            }
            if (thenMethod instanceof JSFunction) {
                // Thenable: use existing async-from-sync resolution path
                JSPromise resolvedResultPromise = JSAsyncIterator.createAsyncFromSyncResultPromise(context, value,
                        done);
                resolvedResultPromise.observe(context, promise.fulfillHandler(context, "onResolve"),
                        promise.rejectHandler(context, "onReject"));
                return;
            }
        }
        // Primitive or non-thenable object: fulfill synchronously
        JSObject result = JSIteratorHelper.createResult(context, value, done);
        promise.fulfill(result);
    }

    private static void fulfillAsyncYieldStarResult(JSContext context, JSPromise promise, JSObject iteratorResultObject,
            JSObject delegateIterator, boolean nativeAsyncIterator) {
        // Per ES2024 spec: "If generatorKind is async, then set innerResult to ? Await(innerResult)"
        // If the iterator result is a Promise, await it first before reading .done/.value
        if (iteratorResultObject instanceof JSPromise promiseResult) {
            awaitYieldStar(context, promiseResult, promise, delegateIterator, nativeAsyncIterator, "onAwaitInnerResult",
                    "onAwaitInnerReject");
            return;
        }

        // Check for callable `then` (thenable iterator result) — per Await spec PromiseResolve
        JSValue thenMethod = iteratorResultObject.get(PropertyKey.THEN);
        if (rejectPendingYield(context, promise)) {
            return;
        }
        if (thenMethod instanceof JSFunction thenFunc) {
            // Thenable: call then() and wait for resolution, per Await spec step 2
            JSPromise thenablePromise = context.createJSPromise();
            try {
                thenFunc.call(context, iteratorResultObject, new JSValue[]{thenablePromise.fulfillHandler(context, ""),
                        thenablePromise.rejectHandler(context, "")});
            } catch (Exception e) {
                JSValue error = context.takeRejectionReason(e);
                promise.reject(error);
                return;
            }
            if (rejectPendingYield(context, promise)) {
                return;
            }
            awaitYieldStar(context, thenablePromise, promise, delegateIterator, nativeAsyncIterator,
                    "onThenableResolve", "onThenableReject");
            return;
        }

        // Non-thenable, non-Promise: proceed with direct property access
        JSValue doneValue = iteratorResultObject.get(PropertyKey.DONE);
        if (rejectPendingYield(context, promise)) {
            return;
        }
        boolean done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;
        if (rejectPendingYield(context, promise)) {
            return;
        }
        JSValue value = iteratorResultObject.get(PropertyKey.VALUE);
        if (rejectPendingYield(context, promise)) {
            return;
        }
        if (nativeAsyncIterator) {
            // For native async iterators, pass value directly without unwrapping.
            // yield* should not await the value from IteratorValue.
            JSObject result = JSIteratorHelper.createResult(context, value, done);
            promise.fulfill(result);
        } else {
            // For sync-wrapped iterators, use createAsyncFromSyncResultPromise
            // to properly await/unwrap Promise values per AsyncFromSyncIterator spec.
            JSPromise resultPromise = unwrapDelegatedValue(context, delegateIterator, value, done);
            resultPromise.observe(context, promise.fulfillHandler(context, "onResolve"),
                    promise.rejectHandler(context, "onReject"));
        }
    }

    /**
     * Read done and value from an iterator result object (done before value per spec), and create a promise resolving
     * to {value, done}.
     */
    private static JSPromise readDoneValueAndCreatePromise(JSContext context, JSObject result,
            JSObject delegateIterator) {
        JSValue doneValue = result.get(PropertyKey.DONE);
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
        JSValue value = result.get(PropertyKey.VALUE);
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        return unwrapDelegatedValue(context, delegateIterator, value, done);
    }

    private static boolean rejectPendingYield(JSContext context, JSPromise promise) {
        if (!context.hasPendingException()) {
            return false;
        }
        promise.reject(context.takeExecutionException());
        return true;
    }

    private static JSPromise unwrapDelegatedValue(JSContext context, JSObject delegateIterator, JSValue value,
            boolean done) {
        JSPromise asyncFromSyncResultPromise = JSAsyncIterator.createAsyncFromSyncResultPromise(context, value, done);
        if (done || delegateIterator == null) {
            return asyncFromSyncResultPromise;
        }
        // Add close-on-rejection handling for non-done results
        JSPromise closeOnRejectionPromise = context.createJSPromise();
        asyncFromSyncResultPromise.observe(context, closeOnRejectionPromise.fulfillHandler(context, "onResolve"),
                new JSNativeFunction(context, "onReject", 1, (childCtx, thisArg, args) -> {
                    JSValue originalError = JSValue.argumentOrUndefined(args, 0);
                    JSValue returnMethodValue = delegateIterator.get(PropertyKey.RETURN);
                    if (childCtx.hasPendingException()) {
                        childCtx.clearAllPendingExceptions();
                    } else if (returnMethodValue instanceof JSFunction returnFunction) {
                        returnFunction.call(childCtx, delegateIterator, JSValue.NO_ARGS);
                        if (childCtx.hasPendingException()) {
                            childCtx.clearAllPendingExceptions();
                        }
                    }
                    closeOnRejectionPromise.reject(originalError);
                    return JSUndefined.INSTANCE;
                }));
        return closeOnRejectionPromise;
    }
}
