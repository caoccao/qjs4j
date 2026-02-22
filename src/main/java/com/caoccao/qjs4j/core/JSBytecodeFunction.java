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

import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.VarRef;
import com.caoccao.qjs4j.vm.YieldResult;

/**
 * Represents a JavaScript function compiled to bytecode.
 * Based on QuickJS JSFunctionBytecode structure.
 * <p>
 * Bytecode functions are created by:
 * - Function declarations
 * - Function expressions
 * - Arrow functions
 * - Method definitions
 * <p>
 * They contain:
 * - Compiled bytecode for execution
 * - Closure variables (captured from outer scopes)
 * - Prototype object (for constructors)
 * - Function metadata (name, length)
 */
public final class JSBytecodeFunction extends JSFunction {
    public static final String NAME = JSFunction.NAME;
    private final Bytecode bytecode;
    private final JSValue[] closureVars;
    private final boolean isArrow;
    private final boolean isAsync;
    private final boolean isConstructor;
    private final boolean isGenerator;
    private final int length;
    private final String name;
    private final JSObject prototype;
    private final int selfCaptureIndex;
    private final boolean strict;
    private int[] captureSourceInfos;
    private boolean hasParameterExpressions;
    private String sourceCode;
    private VarRef[] varRefs;

    /**
     * Create a bytecode function.
     *
     * @param bytecode The compiled bytecode
     * @param name     Function name (empty string for anonymous)
     * @param length   Number of formal parameters
     */
    public JSBytecodeFunction(Bytecode bytecode, String name, int length) {
        this(bytecode, name, length, new JSValue[0], null, true, false, false, false, false, null, -1);
    }

    /**
     * Create a bytecode function with strict mode.
     *
     * @param bytecode The compiled bytecode
     * @param name     Function name (empty string for anonymous)
     * @param length   Number of formal parameters
     * @param strict   Whether the function is in strict mode
     */
    public JSBytecodeFunction(Bytecode bytecode, String name, int length, boolean strict) {
        this(bytecode, name, length, new JSValue[0], null, true, false, false, false, strict, null, -1);
    }

    /**
     * Create a bytecode function with strict mode and source code.
     *
     * @param bytecode   The compiled bytecode
     * @param name       Function name (empty string for anonymous)
     * @param length     Number of formal parameters
     * @param strict     Whether the function is in strict mode
     * @param sourceCode The original source code of the function (for toString())
     */
    public JSBytecodeFunction(Bytecode bytecode, String name, int length, boolean strict, String sourceCode) {
        this(bytecode, name, length, new JSValue[0], null, true, false, false, false, strict, sourceCode, -1);
    }

    /**
     * Create a bytecode function with full configuration.
     */
    public JSBytecodeFunction(
            Bytecode bytecode,
            String name,
            int length,
            JSValue[] closureVars,
            JSObject prototype,
            boolean isConstructor,
            boolean isAsync,
            boolean isGenerator,
            boolean isArrow,
            boolean strict,
            String sourceCode) {
        this(bytecode, name, length, closureVars, prototype, isConstructor, isAsync, isGenerator, isArrow, strict, sourceCode, -1);
    }

    /**
     * Create a bytecode function with full configuration and self-capture index.
     */
    public JSBytecodeFunction(
            Bytecode bytecode,
            String name,
            int length,
            JSValue[] closureVars,
            JSObject prototype,
            boolean isConstructor,
            boolean isAsync,
            boolean isGenerator,
            boolean isArrow,
            boolean strict,
            String sourceCode,
            int selfCaptureIndex) {
        super(); // Initialize as JSObject
        this.bytecode = bytecode;
        this.name = name != null ? name : "";
        this.length = length;
        this.closureVars = closureVars != null ? closureVars : new JSValue[0];
        this.prototype = prototype;
        this.isConstructor = isConstructor;
        this.isAsync = isAsync;
        this.isGenerator = isGenerator;
        this.isArrow = isArrow;
        this.strict = strict;
        this.sourceCode = sourceCode;
        this.selfCaptureIndex = selfCaptureIndex;

        // Set up function properties on the object
        // Per ES spec, name and length are {writable: false, enumerable: false, configurable: true}
        this.defineProperty(PropertyKey.NAME,
                PropertyDescriptor.dataDescriptor(new JSString(this.name), false, false, true));
        this.defineProperty(PropertyKey.LENGTH,
                PropertyDescriptor.dataDescriptor(JSNumber.of(this.length), false, false, true));

        // Every function (except arrow functions) has a prototype property
        if (prototype != null) {
            // Class constructor: prototype is {writable: false, enumerable: false, configurable: false}
            this.definePropertyReadonlyNonConfigurable("prototype", prototype);
        } else if (isConstructor) {
            // User-defined function constructor: prototype is {writable: true, enumerable: false, configurable: false}
            JSObject funcPrototype = new JSObject();
            funcPrototype.set(PropertyKey.CONSTRUCTOR, this);
            this.defineProperty(PropertyKey.PROTOTYPE,
                    PropertyDescriptor.dataDescriptor(funcPrototype, true, false, false));
        }
    }

    /**
     * Per ES spec AsyncGeneratorYield/AsyncGeneratorResolve: Await the yielded/returned value
     * before placing it in the iterator result. If the value is a promise or thenable,
     * resolve it first; otherwise use it directly.
     */
    private static void fulfillAsyncYield(JSContext context, JSPromise promise, JSValue value, boolean done) {
        if (value instanceof JSPromise yieldedPromise) {
            // If already settled, use result directly
            if (yieldedPromise.getState() == JSPromise.PromiseState.FULFILLED) {
                JSObject iterResult = context.createJSObject();
                iterResult.set(PropertyKey.VALUE, yieldedPromise.getResult());
                iterResult.set(PropertyKey.DONE, JSBoolean.valueOf(done));
                promise.fulfill(iterResult);
                return;
            }
            if (yieldedPromise.getState() == JSPromise.PromiseState.REJECTED) {
                promise.reject(yieldedPromise.getResult());
                return;
            }
            // Pending: chain reactions
            yieldedPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onResolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                                JSValue resolved = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                                JSObject iterResult = context.createJSObject();
                                iterResult.set(PropertyKey.VALUE, resolved);
                                iterResult.set(PropertyKey.DONE, JSBoolean.valueOf(done));
                                promise.fulfill(iterResult);
                                return JSUndefined.INSTANCE;
                            }),
                            null, context
                    ),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                                promise.reject(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                                return JSUndefined.INSTANCE;
                            }),
                            null, context
                    )
            );
            return;
        }
        if (value instanceof JSObject obj) {
            JSValue thenMethod = obj.get(PropertyKey.THEN);
            if (thenMethod instanceof JSFunction thenFunc) {
                thenFunc.call(context, value, new JSValue[]{
                        new JSNativeFunction("resolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            JSValue resolved = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                            JSObject iterResult = context.createJSObject();
                            iterResult.set(PropertyKey.VALUE, resolved);
                            iterResult.set(PropertyKey.DONE, JSBoolean.valueOf(done));
                            promise.fulfill(iterResult);
                            return JSUndefined.INSTANCE;
                        }),
                        new JSNativeFunction("reject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                            promise.reject(callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        })
                });
                return;
            }
        }
        // Non-thenable: use directly
        JSObject iterResult = context.createJSObject();
        iterResult.set(PropertyKey.VALUE, value);
        iterResult.set(PropertyKey.DONE, JSBoolean.valueOf(done));
        promise.fulfill(iterResult);
    }

    private static void fulfillAsyncYieldStarResult(
            JSContext context,
            JSPromise promise,
            JSObject iteratorResultObject,
            JSObject delegateIterator) {
        JSValue doneValue = iteratorResultObject.get(context, PropertyKey.DONE);
        if (context.hasPendingException()) {
            JSValue error = context.getPendingException();
            context.clearAllPendingExceptions();
            promise.reject(error);
            return;
        }
        boolean done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;
        if (context.hasPendingException()) {
            JSValue error = context.getPendingException();
            context.clearAllPendingExceptions();
            promise.reject(error);
            return;
        }
        JSValue value = iteratorResultObject.get(context, PropertyKey.VALUE);
        if (context.hasPendingException()) {
            JSValue error = context.getPendingException();
            context.clearAllPendingExceptions();
            promise.reject(error);
            return;
        }
        JSPromise asyncFromSyncResultPromise = JSAsyncIterator.createAsyncFromSyncResultPromise(context, value, done);
        JSPromise resultPromise = asyncFromSyncResultPromise;
        if (!done && delegateIterator != null) {
            JSPromise closeOnRejectionPromise = context.createJSPromise();
            asyncFromSyncResultPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onResolve", 1, (childContext, thisArg, args) -> {
                                JSValue resolvedResult = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                closeOnRejectionPromise.fulfill(resolvedResult);
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context
                    ),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (childContext, thisArg, args) -> {
                                JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                JSValue returnMethodValue = delegateIterator.get(context, PropertyKey.RETURN);
                                if (context.hasPendingException()) {
                                    context.clearAllPendingExceptions();
                                } else if (returnMethodValue instanceof JSFunction returnFunction) {
                                    JSValue closeResult = returnFunction.call(context, delegateIterator, new JSValue[0]);
                                    if (context.hasPendingException()) {
                                        context.clearAllPendingExceptions();
                                    } else if (!(closeResult instanceof JSObject)) {
                                        // Preserve the original rejection reason during close-on-rejection.
                                    }
                                } else if (returnMethodValue.isNullOrUndefined()) {
                                    // No return method.
                                } else {
                                    // Non-callable return method is ignored here to preserve original rejection.
                                }
                                closeOnRejectionPromise.reject(error);
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context
                    )
            );
            resultPromise = closeOnRejectionPromise;
        }
        resultPromise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onResolve", 1, (childContext, thisArg, args) -> {
                            JSValue resolvedResult = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            promise.fulfill(resolvedResult);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onReject", 1, (childContext, thisArg, args) -> {
                            JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            promise.reject(error);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                )
        );
    }

    private static void attachDelegatedIteratorStateHandlers(
            JSContext context,
            JSPromise promise,
            JSGeneratorState generatorState,
            JSObject[] delegatedYieldStarIteratorHolder,
            JSObject delegateIterator) {
        promise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onDelegatedResolve", 1, (childContext, callbackThisArg, callbackArgs) -> {
                            JSValue resultValue = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                            if (resultValue instanceof JSObject resultObject) {
                                JSValue doneValue = resultObject.get(childContext, PropertyKey.DONE);
                                if (childContext.hasPendingException()) {
                                    delegatedYieldStarIteratorHolder[0] = null;
                                    generatorState.setCompleted(true);
                                    childContext.clearAllPendingExceptions();
                                    return JSUndefined.INSTANCE;
                                }
                                boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
                                if (childContext.hasPendingException()) {
                                    delegatedYieldStarIteratorHolder[0] = null;
                                    generatorState.setCompleted(true);
                                    childContext.clearAllPendingExceptions();
                                    return JSUndefined.INSTANCE;
                                }
                                if (done) {
                                    delegatedYieldStarIteratorHolder[0] = null;
                                    generatorState.setCompleted(true);
                                } else {
                                    delegatedYieldStarIteratorHolder[0] = delegateIterator;
                                }
                            } else {
                                delegatedYieldStarIteratorHolder[0] = null;
                                generatorState.setCompleted(true);
                            }
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onDelegatedReject", 1, (childContext, callbackThisArg, callbackArgs) -> {
                            delegatedYieldStarIteratorHolder[0] = null;
                            generatorState.setCompleted(true);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                )
        );
    }

    private static JSPromise createAsyncFromSyncDelegatedReturnPromise(
            JSContext context,
            JSObject delegateIterator,
            JSValue argumentValue) {
        JSValue returnMethodValue = delegateIterator.get(context, PropertyKey.RETURN);
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        if (returnMethodValue.isNullOrUndefined()) {
            return JSAsyncIterator.createIteratorResultPromise(context, argumentValue, true);
        }
        if (!(returnMethodValue instanceof JSFunction returnFunction)) {
            JSValue typeError = context.throwTypeError("iterator return is not a function");
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, typeError);
        }
        JSValue returnResult = returnFunction.call(context, delegateIterator, new JSValue[]{argumentValue});
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        if (!(returnResult instanceof JSObject returnResultObject)) {
            JSValue typeError = context.throwTypeError("iterator must return an object");
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, typeError);
        }
        JSValue doneValue = returnResultObject.get(context, PropertyKey.DONE);
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        JSValue value = returnResultObject.get(context, PropertyKey.VALUE);
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        return JSAsyncIterator.createAsyncFromSyncResultPromise(context, value, done);
    }

    private static JSPromise createAsyncFromSyncDelegatedThrowPromise(
            JSContext context,
            JSObject delegateIterator,
            JSValue argumentValue) {
        JSValue throwMethodValue = delegateIterator.get(context, PropertyKey.THROW);
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        if (throwMethodValue.isNullOrUndefined()) {
            JSValue returnMethodValue = delegateIterator.get(context, PropertyKey.RETURN);
            if (context.hasPendingException()) {
                JSValue exception = context.getPendingException();
                context.clearAllPendingExceptions();
                return JSAsyncIterator.createRejectedPromise(context, exception);
            }
            if (!returnMethodValue.isNullOrUndefined()) {
                if (!(returnMethodValue instanceof JSFunction returnFunction)) {
                    JSValue typeError = context.throwTypeError("iterator return is not a function");
                    context.clearAllPendingExceptions();
                    return JSAsyncIterator.createRejectedPromise(context, typeError);
                }
                JSValue closeResult = returnFunction.call(context, delegateIterator, new JSValue[0]);
                if (context.hasPendingException()) {
                    JSValue exception = context.getPendingException();
                    context.clearAllPendingExceptions();
                    return JSAsyncIterator.createRejectedPromise(context, exception);
                }
                if (!(closeResult instanceof JSObject)) {
                    JSValue typeError = context.throwTypeError("iterator must return an object");
                    context.clearAllPendingExceptions();
                    return JSAsyncIterator.createRejectedPromise(context, typeError);
                }
            }
            JSValue typeError = context.throwTypeError("iterator does not have a throw method");
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, typeError);
        }
        if (!(throwMethodValue instanceof JSFunction throwFunction)) {
            JSValue typeError = context.throwTypeError("iterator throw is not a function");
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, typeError);
        }
        JSValue throwResult = throwFunction.call(context, delegateIterator, new JSValue[]{argumentValue});
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        if (!(throwResult instanceof JSObject throwResultObject)) {
            JSValue typeError = context.throwTypeError("iterator must return an object");
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, typeError);
        }
        JSValue doneValue = throwResultObject.get(context, PropertyKey.DONE);
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        JSValue value = throwResultObject.get(context, PropertyKey.VALUE);
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            return JSAsyncIterator.createRejectedPromise(context, exception);
        }
        JSPromise asyncFromSyncResultPromise = JSAsyncIterator.createAsyncFromSyncResultPromise(context, value, done);
        if (done) {
            return asyncFromSyncResultPromise;
        }
        JSPromise closeOnRejectionPromise = context.createJSPromise();
        asyncFromSyncResultPromise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onResolve", 1, (childContext, callbackThisArg, callbackArgs) -> {
                            JSValue resolvedResult = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                            closeOnRejectionPromise.fulfill(resolvedResult);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("onReject", 1, (childContext, callbackThisArg, callbackArgs) -> {
                            JSValue originalError = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                            JSValue returnMethodValue = delegateIterator.get(childContext, PropertyKey.RETURN);
                            if (childContext.hasPendingException()) {
                                childContext.clearAllPendingExceptions();
                            } else if (returnMethodValue instanceof JSFunction returnFunction) {
                                JSValue closeResult = returnFunction.call(childContext, delegateIterator, new JSValue[0]);
                                if (childContext.hasPendingException()) {
                                    childContext.clearAllPendingExceptions();
                                } else if (!(closeResult instanceof JSObject)) {
                                    // Preserve the original rejection reason during close-on-rejection.
                                }
                            } else if (returnMethodValue.isNullOrUndefined()) {
                                // No return method to call.
                            } else {
                                // Non-callable return during close-on-rejection is ignored to preserve original reason.
                            }
                            closeOnRejectionPromise.reject(originalError);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                )
        );
        return closeOnRejectionPromise;
    }

    @Override
    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // Per ES spec, each function has a [[Realm]] internal slot. When called cross-realm,
        // the function should execute in its own realm, not the caller's realm.
        JSContext executionContext = getHomeContext() != null ? getHomeContext() : context;

        // OrdinaryCallBindThis: in non-strict mode, coerce this value
        if (!strict && !(thisArg instanceof JSObject)) {
            if (thisArg instanceof JSUndefined || thisArg instanceof JSNull) {
                thisArg = executionContext.getGlobalObject();
            } else {
                // Auto-box primitives to wrapper objects (String, Number, Boolean, etc.)
                thisArg = JSTypeConversions.toObject(executionContext, thisArg);
            }
        }

        // If this is an async generator function, create and return an async generator object
        // Following QuickJS pattern: create generator without executing the body
        if (isAsync && isGenerator) {
            // Create generator state to track execution
            JSGeneratorState generatorState = new JSGeneratorState(this, thisArg, args);

            // Per ES spec, execute up to INITIAL_YIELD to evaluate parameter defaults.
            // Errors during parameter initialization propagate from the function call.
            executionContext.getVirtualMachine().executeGenerator(generatorState, executionContext);
            JSGenerator generatorDriver = new JSGenerator(context, generatorState);
            final JSObject[] delegatedYieldStarIteratorHolder = new JSObject[]{null};

            return new JSAsyncGenerator((inputValue, requestKind) -> {
                JSPromise promise = context.createJSPromise();

                // Check if generator is completed
                if (generatorState.isCompleted()) {
                    JSObject result = context.createJSObject();
                    result.set(PropertyKey.VALUE, JSUndefined.INSTANCE);
                    result.set(PropertyKey.DONE, JSBoolean.TRUE);
                    promise.fulfill(result);
                    return promise;
                }

                try {
                    JSObject delegateIterator = delegatedYieldStarIteratorHolder[0];
                    if (requestKind == JSAsyncGenerator.AsyncGeneratorRequestKind.RETURN
                            && delegateIterator != null) {
                        JSPromise delegatedReturnPromise = createAsyncFromSyncDelegatedReturnPromise(
                                context,
                                delegateIterator,
                                inputValue);
                        attachDelegatedIteratorStateHandlers(
                                context,
                                delegatedReturnPromise,
                                generatorState,
                                delegatedYieldStarIteratorHolder,
                                delegateIterator);
                        return delegatedReturnPromise;
                    }
                    if (requestKind == JSAsyncGenerator.AsyncGeneratorRequestKind.THROW
                            && delegateIterator != null) {
                        JSPromise delegatedThrowPromise = createAsyncFromSyncDelegatedThrowPromise(
                                context,
                                delegateIterator,
                                inputValue);
                        attachDelegatedIteratorStateHandlers(
                                context,
                                delegatedThrowPromise,
                                generatorState,
                                delegatedYieldStarIteratorHolder,
                                delegateIterator);
                        return delegatedThrowPromise;
                    }

                    JSObject iteratorResult = switch (requestKind) {
                        case NEXT -> generatorDriver.next(inputValue);
                        case RETURN -> generatorDriver.returnMethod(inputValue);
                        case THROW -> generatorDriver.throwMethod(inputValue);
                    };
                    if (context.hasPendingException()) {
                        JSValue exception = context.getPendingException();
                        context.clearAllPendingExceptions();
                        promise.reject(exception);
                        return promise;
                    }

                    // Check if this was a yield or completion
                    if (generatorState.isCompleted()) {
                        delegatedYieldStarIteratorHolder[0] = null;
                        // Generator completed - return final value with done: true
                        // Per ES spec, also await the return value
                        JSValue completedValue = iteratorResult.get(PropertyKey.VALUE);
                        fulfillAsyncYield(context, promise, completedValue, true);
                    } else {
                        // Generator yielded - check if yield* (already has {value, done})
                        YieldResult lastYield = context.getVirtualMachine().getLastYieldResult();
                        if (lastYield != null && lastYield.isYieldStar()) {
                            delegatedYieldStarIteratorHolder[0] = lastYield.delegateIterator();
                            // ASYNC_YIELD_STAR currently returns a raw sync iterator result object.
                            // Apply async-from-sync iterator-result processing here.
                            fulfillAsyncYieldStarResult(context, promise, iteratorResult, lastYield.delegateIterator());
                        } else {
                            delegatedYieldStarIteratorHolder[0] = null;
                            // Per ES spec AsyncGeneratorYield step 8: Await the yielded value
                            JSValue yieldedValue = iteratorResult.get(PropertyKey.VALUE);
                            fulfillAsyncYield(context, promise, yieldedValue, false);
                        }
                    }
                } catch (Exception e) {
                    // Preserve the actual JS error object from pending exception
                    if (context.hasPendingException()) {
                        JSValue exception = context.getPendingException();
                        context.clearAllPendingExceptions();
                        promise.reject(exception);
                    } else {
                        String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                        JSObject errorObj = context.createJSObject();
                        errorObj.set(PropertyKey.MESSAGE, new JSString(errorMessage));
                        promise.reject(errorObj);
                    }
                }

                return promise;
            }, context);
        }

        // If this is a sync generator function, create and return a sync generator object
        // Following QuickJS js_generator_function_call pattern
        if (isGenerator && !isAsync) {
            // Create generator state to track execution
            JSGeneratorState generatorState = new JSGeneratorState(this, thisArg, args);

            // Per ES spec GeneratorStart, execute the generator function up to INITIAL_YIELD.
            // This evaluates parameter defaults and FunctionDeclarationInstantiation.
            // If an error occurs before INITIAL_YIELD (e.g., SyntaxError from eval),
            // it propagates up from the generator function call.
            // Following QuickJS js_generator_function_call which calls JS_CallInternal.
            executionContext.getVirtualMachine().executeGenerator(generatorState, executionContext);

            // Create generator object with proper prototype chain
            // In QuickJS, js_create_from_ctor gets prototype from the generator function's
            // "prototype" property and creates JS_CLASS_GENERATOR object
            JSGenerator generatorObj = new JSGenerator(context, generatorState);

            // Set prototype: use this function's prototype property (which inherits from Generator.prototype)
            JSValue funcPrototype = this.get(PropertyKey.PROTOTYPE);
            if (funcPrototype instanceof JSObject protoObj) {
                generatorObj.setPrototype(protoObj);
            } else {
                // Fallback: use Generator.prototype from context
                JSObject generatorFunctionPrototype = context.getGeneratorFunctionPrototype();
                if (generatorFunctionPrototype != null) {
                    JSValue genProto = generatorFunctionPrototype.get(PropertyKey.PROTOTYPE);
                    if (genProto instanceof JSObject genProtoObj) {
                        generatorObj.setPrototype(genProtoObj);
                    }
                }
            }

            return generatorObj;
        }

        // If this is an async function, wrap execution in a promise
        if (isAsync) {
            JSPromise promise = context.createJSPromise();
            try {
                // Execute bytecode in the VM
                JSValue result = context.getVirtualMachine().execute(this, thisArg, args);

                // If result is already a promise, use it directly
                if (result instanceof JSPromise) {
                    return result;
                }

                // Otherwise, resolve the promise (handles thenables per ES spec)
                promise.resolve(context, result);
            } catch (JSVirtualMachineException e) {
                // VM exception during async function execution
                // Check if there's a pending exception in the context
                if (context.hasPendingException()) {
                    JSValue exception = context.getPendingException();
                    context.clearAllPendingExceptions(); // Clear BOTH context and VM pending exceptions
                    promise.reject(exception);
                } else {
                    // Create error object from exception message
                    String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                    JSObject errorObj = context.createJSObject();
                    errorObj.set(PropertyKey.MESSAGE, new JSString(errorMessage));
                    promise.reject(errorObj);
                }
            } catch (Exception e) {
                // Any other exception in an async function should be caught
                // and wrapped in a rejected promise
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                JSObject errorObj = context.createJSObject();
                errorObj.set(PropertyKey.MESSAGE, new JSString(errorMessage));
                promise.reject(errorObj);
            }
            return promise;
        }

        // For non-async functions, execute normally and let exceptions propagate
        return executionContext.getVirtualMachine().execute(this, thisArg, args);
    }

    public JSBytecodeFunction copyWithClosureVars(JSValue[] capturedClosureVars) {
        JSBytecodeFunction copiedFunction = new JSBytecodeFunction(
                bytecode,
                name,
                length,
                capturedClosureVars,
                prototype,
                isConstructor,
                isAsync,
                isGenerator,
                isArrow,
                strict,
                sourceCode,
                selfCaptureIndex
        );
        copiedFunction.hasParameterExpressions = this.hasParameterExpressions;
        return copiedFunction;
    }

    /**
     * Create a copy of this function with the given VarRef array for closure variables.
     * Used by FCLOSURE when creating closures with reference-based capture.
     */
    public JSBytecodeFunction copyWithVarRefs(VarRef[] capturedVarRefs) {
        JSBytecodeFunction copiedFunction = new JSBytecodeFunction(
                bytecode,
                name,
                length,
                new JSValue[0],
                prototype,
                isConstructor,
                isAsync,
                isGenerator,
                isArrow,
                strict,
                sourceCode,
                selfCaptureIndex
        );
        copiedFunction.varRefs = capturedVarRefs;
        copiedFunction.hasParameterExpressions = this.hasParameterExpressions;
        return copiedFunction;
    }

    /**
     * Get the bytecode for this function.
     */
    public Bytecode getBytecode() {
        return bytecode;
    }

    /**
     * Get the capture source info array for template functions.
     * Each entry encodes the source of a closure capture:
     * - value >= 0: LOCAL capture at that local slot index
     * - value < 0: VAR_REF capture at -(value + 1)
     * Returns null for non-template (instantiated) functions.
     */
    public int[] getCaptureSourceInfos() {
        return captureSourceInfos;
    }

    /**
     * Get the closure variables (captured from outer scopes).
     */
    public JSValue[] getClosureVars() {
        return closureVars;
    }

    /**
     * Get the constructor's prototype property (for constructor calls / new).
     * Note: This is different from getPrototype() which returns the internal [[Prototype]].
     */
    public JSObject getConstructorPrototype() {
        return prototype;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the self-capture index for closure self-reference patching.
     * Returns -1 if this function does not capture its own name.
     * Following QuickJS var_refs pattern where a function's closure variable
     * pointing to itself is patched after creation.
     */
    public int getSelfCaptureIndex() {
        return selfCaptureIndex;
    }

    /**
     * Get the VarRef array for closure variables (reference-based capture).
     * Returns null if this function uses value-based closureVars instead.
     */
    public VarRef[] getVarRefs() {
        return varRefs;
    }

    /**
     * Check if this function has parameter expressions (default values, rest, or destructuring).
     * Following QuickJS has_parameter_expressions flag.
     * Used by eval() to detect when var declarations would conflict with implicit arguments binding.
     */
    public boolean hasParameterExpressions() {
        return hasParameterExpressions;
    }

    /**
     * Check if this is an arrow function.
     */
    public boolean isArrow() {
        return isArrow;
    }

    /**
     * Check if this is an async function.
     */
    public boolean isAsync() {
        return isAsync;
    }

    /**
     * Check if this function can be used as a constructor.
     */
    public boolean isConstructor() {
        return isConstructor;
    }

    /**
     * Check if this is a generator function.
     */
    public boolean isGenerator() {
        return isGenerator;
    }

    /**
     * Check if this function is in strict mode.
     * Following QuickJS js_mode & JS_MODE_STRICT.
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Set the capture source info array for this template function.
     * Called by the compiler to record where each closure variable comes from.
     */
    public void setCaptureSourceInfos(int[] captureSourceInfos) {
        this.captureSourceInfos = captureSourceInfos;
    }

    /**
     * Set whether this function has parameter expressions.
     */
    public void setHasParameterExpressions(boolean hasParameterExpressions) {
        this.hasParameterExpressions = hasParameterExpressions;
    }

    /**
     * Set the source code for this function.
     * Used to override the default toString() representation.
     */
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    @Override
    public Object toJavaObject() {
        return toString();
    }

    @Override
    public String toString() {
        // If source code is available, return it
        if (sourceCode != null) {
            return sourceCode;
        }

        // Otherwise, return a default representation
        StringBuilder sb = new StringBuilder();
        if (isAsync) {
            sb.append("async ");
        }
        sb.append("function");
        if (isGenerator) {
            sb.append("*");
        }
        sb.append(" ");
        if (!name.isEmpty()) {
            sb.append(name);
        }
        sb.append("() { [bytecode] }");
        return sb.toString();
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }
}
