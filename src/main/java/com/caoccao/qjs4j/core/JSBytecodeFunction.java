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
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VarRef;
import com.caoccao.qjs4j.vm.YieldResult;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Represents a JavaScript function compiled to bytecode. Based on QuickJS JSFunctionBytecode structure.
 * <p>
 * Bytecode functions are created by: - Function declarations - Function expressions - Arrow functions - Method
 * definitions
 * <p>
 * They contain: - Compiled bytecode for execution - Closure variables (captured from outer scopes) - Prototype object
 * (for constructors) - Function metadata (name, length)
 */
public final class JSBytecodeFunction extends JSFunction {
    public static final String NAME = JSFunction.NAME;
    /**
     * The local slot a named function expression sets on entering its body, or -1.
     * <p>
     * See {@link #getParameterScopeFunctionName()} for what it is for.
     */
    private int bodyScopeEnteredLocalIndex = -1;
    private final Bytecode bytecode;
    private JSFunction capturedActiveFunction;
    private JSValue capturedArguments;
    private VarRef capturedDerivedThisRef;
    private JSValue capturedNewTarget;
    private JSValue capturedThisArg;
    private String[] capturedVarNames;
    private int[] captureSourceInfos;
    private boolean classConstructor;
    private IdentityHashMap<JSSymbol, JSSymbol> classPrivateSymbolRemap;
    private Set<JSSymbol> classPrivateSymbols;
    private final JSValue[] closureVars;
    private boolean derivedConstructor;
    private boolean displaysAsArgumentsObjectInToString;
    private StackFrame evalDynamicScopeFrame;
    private boolean evalDynamicScopeLookupEnabled;
    private boolean evalSuperCallAllowed;
    private boolean hasArgumentsParameterBinding;
    private boolean hasParameterExpressions;
    /**
     * The named-function-expression bindings this function captured from enclosing parameter environments. Never null.
     * <p>
     * See {@link #getInheritedParameterScopeFunctionNames()}.
     */
    private Set<String> inheritedParameterScopeFunctionNames = Set.of();
    private final boolean isArrow;
    private final boolean isAsync;
    private final boolean isConstructor;
    private final boolean isGenerator;
    private final int length;
    private final String name;
    private boolean newTargetAllowed;
    /**
     * The name of this named function expression when its body redeclares it, else null.
     * <p>
     * See {@link #getActiveParameterScopeFunctionName(StackFrame)}.
     */
    private String parameterScopeFunctionName;
    /**
     * Whether this is the function a script, module or eval body was compiled into, rather than a function the source
     * declared. A stack trace prints such a frame by its position alone, as V8 does.
     */
    private boolean program;
    private final JSObject prototype;
    private final int selfCaptureIndex;
    private int selfLocalIndex;
    private String sourceCode;
    private final boolean strict;
    private VarRef[] varRefs;

    /**
     * Create a bytecode function with full configuration.
     */
    public JSBytecodeFunction(JSContext context, Bytecode bytecode, String name, int length, JSValue[] closureVars,
            JSObject prototype, boolean isConstructor, boolean isAsync, boolean isGenerator, boolean isArrow,
            boolean strict, String sourceCode) {
        this(context, bytecode, name, length, closureVars, prototype, isConstructor, isAsync, isGenerator, isArrow,
                strict, sourceCode, -1);
    }

    /**
     * Create a bytecode function with full configuration and self-capture index.
     */
    public JSBytecodeFunction(JSContext context, Bytecode bytecode, String name, int length, JSValue[] closureVars,
            JSObject prototype, boolean isConstructor, boolean isAsync, boolean isGenerator, boolean isArrow,
            boolean strict, String sourceCode, int selfCaptureIndex) {
        super(context); // Initialize as JSObject
        this.bytecode = bytecode;
        this.name = name != null ? name : "";
        this.length = length;
        this.closureVars = closureVars != null ? closureVars : JSValue.NO_ARGS;
        this.prototype = prototype;
        this.isConstructor = isConstructor;
        this.isAsync = isAsync;
        this.isGenerator = isGenerator;
        this.isArrow = isArrow;
        this.strict = strict;
        this.sourceCode = sourceCode;
        this.selfCaptureIndex = selfCaptureIndex;
        selfLocalIndex = -1;

        // Set up function properties on the object
        // Per ES spec, length comes before name in property order
        // Both are {writable: false, enumerable: false, configurable: true}
        this.defineProperty(PropertyKey.LENGTH,
                PropertyDescriptor.dataDescriptor(JSNumber.of(this.length), PropertyDescriptor.DataState.Configurable));
        this.defineProperty(PropertyKey.NAME,
                PropertyDescriptor.dataDescriptor(new JSString(this.name), PropertyDescriptor.DataState.Configurable));

        // Every function (except arrow functions) has a prototype property
        if (prototype != null) {
            // Class constructor: prototype is {writable: false, enumerable: false, configurable: false}
            this.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        } else if (isConstructor) {
            // User-defined function constructor: prototype is {writable: true, enumerable: false, configurable: false}
            JSObject funcPrototype = new JSObject(context);
            funcPrototype.defineProperty(PropertyKey.CONSTRUCTOR,
                    PropertyDescriptor.dataDescriptor(this, PropertyDescriptor.DataState.ConfigurableWritable));
            this.defineProperty(PropertyKey.PROTOTYPE,
                    PropertyDescriptor.dataDescriptor(funcPrototype, PropertyDescriptor.DataState.Writable));
        }
    }

    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return callInternal(context, thisArg, args, args.length);
    }

    /**
     * Call with an explicit argument count. When {@code args} comes from a reusable buffer, {@code argCount} may be
     * less than {@code args.length}. For generators/async the buffer is copied to a right-sized array before being
     * stored in the long-lived {@link JSGeneratorState}.
     */
    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args, int argCount) {
        // For generators and async functions that persist args in JSGeneratorState,
        // ensure we own a right-sized copy so the shared buffer can be reused.
        if ((isGenerator || isAsync) && args.length != argCount) {
            JSValue[] owned = new JSValue[argCount];
            System.arraycopy(args, 0, owned, 0, argCount);
            return call(context, thisArg, owned);
        }
        return callInternal(context, thisArg, args, argCount);
    }

    private JSValue callInternal(JSContext context, JSValue thisArg, JSValue[] args, int argCount) {
        // Per ES spec, each function has a [[Realm]] internal slot. When called cross-realm,
        // the function should execute in its own realm, not the caller's realm.
        JSContext executionContext = getHomeContext() != null
                ? getHomeContext()
                : (context != null ? context : getContext());

        if (classConstructor) {
            return executionContext
                    .throwTypeError("Class constructor " + getName() + " cannot be invoked without 'new'");
        }

        // Arrow functions use the lexically captured this, ignoring the provided thisArg
        if (isArrow && capturedThisArg != null) {
            thisArg = capturedThisArg;
        }

        // OrdinaryCallBindThis: in non-strict mode, coerce this value
        if (!strict && !(thisArg instanceof JSObject)) {
            if (thisArg instanceof JSUndefined || thisArg instanceof JSNull) {
                thisArg = executionContext.getGlobalObject();
            } else {
                // Auto-box primitives to wrapper objects (String, Number, Boolean, etc.)
                thisArg = JSTypeConversions.toObject(executionContext, thisArg);
            }
        }

        if (isGenerator) {
            return startGenerator(executionContext, thisArg, args);
        }

        // If this is an async function, wrap execution in a promise
        if (isAsync) {
            JSPromise promise = context.createJSPromise();
            JSGeneratorState asyncFunctionState = new JSGeneratorState(this, thisArg, args);
            resumeAsyncFunctionExecution(context, asyncFunctionState, promise);
            return promise;
        }

        // For non-async functions, execute normally and let exceptions propagate
        // Arrow functions pass their captured new.target to the frame
        if (isArrow && capturedNewTarget != null) {
            return executionContext.getVirtualMachine().execute(this, thisArg, args, argCount, capturedNewTarget);
        }
        return executionContext.getVirtualMachine().execute(this, thisArg, args, argCount, JSUndefined.INSTANCE);
    }

    private void copyClosureStateTo(JSBytecodeFunction target) {
        target.hasParameterExpressions = hasParameterExpressions;
        target.hasArgumentsParameterBinding = hasArgumentsParameterBinding;
        target.selfLocalIndex = selfLocalIndex;
        target.parameterScopeFunctionName = parameterScopeFunctionName;
        target.bodyScopeEnteredLocalIndex = bodyScopeEnteredLocalIndex;
        target.inheritedParameterScopeFunctionNames = inheritedParameterScopeFunctionNames;
        target.capturedActiveFunction = capturedActiveFunction;
        target.capturedDerivedThisRef = capturedDerivedThisRef;
        target.capturedThisArg = capturedThisArg;
        target.capturedArguments = capturedArguments;
        target.capturedNewTarget = capturedNewTarget;
        target.capturedVarNames = capturedVarNames;
        target.classPrivateSymbols = classPrivateSymbols;
        target.classPrivateSymbolRemap = classPrivateSymbolRemap;
        target.displaysAsArgumentsObjectInToString = displaysAsArgumentsObjectInToString;
        target.evalDynamicScopeFrame = evalDynamicScopeFrame;
        target.evalDynamicScopeLookupEnabled = evalDynamicScopeLookupEnabled;
    }

    public JSBytecodeFunction copyTemplateWithRemappedPrivateSymbols(IdentityHashMap<JSSymbol, JSSymbol> symbolRemap) {
        if (symbolRemap == null || symbolRemap.isEmpty()) {
            return this;
        }
        return internalCopyTemplateWithRemappedPrivateSymbols(symbolRemap, new IdentityHashMap<>());
    }

    public JSBytecodeFunction copyWithClosureVars(JSValue[] capturedClosureVars) {
        JSBytecodeFunction copiedFunction = new JSBytecodeFunction(context, bytecode, name, length, capturedClosureVars,
                prototype, isConstructor, isAsync, isGenerator, isArrow, strict, sourceCode, selfCaptureIndex);
        copyClosureStateTo(copiedFunction);
        return copiedFunction;
    }

    /**
     * Create a copy of this function with the given VarRef array for closure variables. Used by FCLOSURE when creating
     * closures with reference-based capture.
     */
    public JSBytecodeFunction copyWithVarRefs(VarRef[] capturedVarRefs) {
        JSBytecodeFunction copiedFunction = new JSBytecodeFunction(context, bytecode, name, length, JSValue.NO_ARGS,
                prototype, isConstructor, isAsync, isGenerator, isArrow, strict, sourceCode, selfCaptureIndex);
        copiedFunction.varRefs = capturedVarRefs;
        copyClosureStateTo(copiedFunction);
        return copiedFunction;
    }

    private JSAsyncGenerator createAsyncGenerator(JSContext executionContext, JSGeneratorState generatorState,
            JSGenerator generatorDriver) {
        final JSObject[] delegatedYieldStarIteratorHolder = new JSObject[]{null};
        final JSFunction[] delegatedNextMethodHolder = new JSFunction[]{null};
        final JSAsyncGenerator.AsyncGeneratorFunction[] asyncGeneratorRequestExecutorHolder = new JSAsyncGenerator.AsyncGeneratorFunction[1];
        final JSAsyncGenerator[] asyncGeneratorHolder = new JSAsyncGenerator[1];

        asyncGeneratorRequestExecutorHolder[0] = (inputValue, requestKind) -> {
            JSPromise promise = executionContext.createJSPromise();
            // Internal: the async generator settles the caller's request promise from this one,
            // so its rejection is never unhandled on its own account.
            promise.markHandled();

            // Check if generator is completed
            if (generatorState.isCompleted()) {
                JSObject result = JSIteratorHelper.createResult(executionContext, JSUndefined.INSTANCE, true);
                promise.fulfill(result);
                return promise;
            }

            try {
                JSObject delegateIterator = delegatedYieldStarIteratorHolder[0];
                if (requestKind == JSAsyncGenerator.AsyncGeneratorRequestKind.NEXT && delegateIterator != null) {
                    JSPromise delegatedNextPromise = createAsyncDelegatedNextPromise(executionContext, delegateIterator,
                            inputValue, delegatedNextMethodHolder[0]);
                    return handleDelegatedIteratorResult(executionContext, delegatedNextPromise, generatorState,
                            delegatedYieldStarIteratorHolder, delegateIterator, asyncGeneratorRequestExecutorHolder,
                            false);
                }
                if (requestKind == JSAsyncGenerator.AsyncGeneratorRequestKind.RETURN && delegateIterator != null) {
                    // Per ES2024 AsyncGeneratorUnwrapYieldResumption: Await the return value
                    // before delegating to the iterator's return method.
                    JSPromise unwrapAwaitPromise = executionContext.createJSPromise();
                    unwrapAwaitPromise.resolve(executionContext, inputValue);
                    if (executionContext.hasPendingException()) {
                        JSValue unwrapError = executionContext.getPendingException();
                        executionContext.clearAllPendingExceptions();
                        promise.reject(unwrapError);
                        return promise;
                    }
                    JSPromise unwrapOuterPromise = executionContext.createJSPromise();
                    unwrapAwaitPromise.observe(executionContext, new JSNativeFunction(executionContext,
                            "onUnwrapReturnResolve", 1, (unwrapCtx, unwrapThis, unwrapArgs) -> {
                                JSValue awaitedReturnValue = unwrapArgs.length > 0
                                        ? unwrapArgs[0]
                                        : JSUndefined.INSTANCE;
                                JSPromise delegatedReturnPromise = createAsyncFromSyncDelegatedReturnPromise(
                                        executionContext, delegateIterator, awaitedReturnValue);
                                JSPromise handledResult = handleDelegatedIteratorResult(executionContext,
                                        delegatedReturnPromise, generatorState, delegatedYieldStarIteratorHolder,
                                        delegateIterator, asyncGeneratorRequestExecutorHolder, true);
                                handledResult.observe(executionContext, new JSNativeFunction(executionContext,
                                        "onDelegateReturnResolve", 1, (c, t, a) -> {
                                            unwrapOuterPromise.fulfill(a.length > 0 ? a[0] : JSUndefined.INSTANCE);
                                            return JSUndefined.INSTANCE;
                                        }), new JSNativeFunction(executionContext, "onDelegateReturnReject", 1,
                                                (c, t, a) -> {
                                                    unwrapOuterPromise
                                                            .reject(a.length > 0 ? a[0] : JSUndefined.INSTANCE);
                                                    return JSUndefined.INSTANCE;
                                                }));
                                return JSUndefined.INSTANCE;
                            }), new JSNativeFunction(executionContext, "onUnwrapReturnReject", 1,
                                    (unwrapCtx, unwrapThis, unwrapArgs) -> {
                                        JSValue unwrapError = unwrapArgs.length > 0
                                                ? unwrapArgs[0]
                                                : JSUndefined.INSTANCE;
                                        unwrapOuterPromise.reject(unwrapError);
                                        return JSUndefined.INSTANCE;
                                    }));
                    return unwrapOuterPromise;
                }
                if (requestKind == JSAsyncGenerator.AsyncGeneratorRequestKind.THROW && delegateIterator != null) {
                    JSPromise delegatedThrowPromise = createAsyncFromSyncDelegatedThrowPromise(executionContext,
                            delegateIterator, inputValue);
                    return handleDelegatedIteratorResult(executionContext, delegatedThrowPromise, generatorState,
                            delegatedYieldStarIteratorHolder, delegateIterator, asyncGeneratorRequestExecutorHolder,
                            false);
                }

                JSAsyncGenerator.AsyncGeneratorRequestKind resumeKind = requestKind;
                JSValue resumeValue = inputValue;
                if (requestKind == JSAsyncGenerator.AsyncGeneratorRequestKind.RETURN
                        && generatorDriver.getState() == JSGenerator.State.SUSPENDED_YIELD
                        && delegatedYieldStarIteratorHolder[0] == null
                        && inputValue instanceof JSPromise promiseValue) {
                    // A return completion at a yield awaits its value first; PromiseResolve reads
                    // value.constructor, and an exception there is thrown into the generator at the yield.
                    promiseValue.get(PropertyKey.CONSTRUCTOR);
                    if (executionContext.hasPendingException()) {
                        resumeValue = executionContext.getPendingException();
                        executionContext.clearAllPendingExceptions();
                        resumeKind = JSAsyncGenerator.AsyncGeneratorRequestKind.THROW;
                    }
                }

                // A return completion goes through the generator like any other resumption: the VM runs
                // its finally blocks, which may themselves yield, await or throw. It used to be thrown in
                // as a private signal object whenever the source text contained "finally", which a catch
                // block could catch, and which a finally block that awaited never finished delivering.
                JSObject iteratorResult = switch (resumeKind) {
                    case NEXT -> generatorDriver.next(resumeValue);
                    case RETURN -> generatorDriver.returnMethod(resumeValue);
                    case THROW -> generatorDriver.throwMethod(resumeValue);
                };
                if (generatorState.isAwaitSuspended()) {
                    JSPromise awaitedPromise = executionContext.getVirtualMachine().consumeAwaitSuspensionPromise();
                    if (awaitedPromise == null) {
                        promise.reject(new JSString("Async generator await suspension promise is missing"));
                        return promise;
                    }
                    awaitedPromise.addReactions(
                            awaitReaction(executionContext, "onAsyncGenAwaitResolve", JSGeneratorState.ResumeKind.NEXT,
                                    generatorState, asyncGeneratorRequestExecutorHolder[0], requestKind,
                                    asyncGeneratorHolder[0], promise),
                            awaitReaction(executionContext, "onAsyncGenAwaitReject", JSGeneratorState.ResumeKind.THROW,
                                    generatorState, asyncGeneratorRequestExecutorHolder[0], requestKind,
                                    asyncGeneratorHolder[0], promise));
                    return promise;
                }
                if (executionContext.hasPendingException()) {
                    JSValue exception = executionContext.getPendingException();
                    executionContext.clearAllPendingExceptions();
                    promise.reject(exception);
                    return promise;
                }

                // Check if this was a yield or completion
                if (generatorState.isCompleted()) {
                    delegatedYieldStarIteratorHolder[0] = null;
                    // Generator completed - return final value with done: true
                    // Per ES spec, also await the return value
                    JSValue completedValue = iteratorResult.get(PropertyKey.VALUE);
                    fulfillAsyncYield(executionContext, promise, completedValue, true);
                } else {
                    // Generator yielded - check if yield* (already has {value, done})
                    YieldResult lastYield = generatorState.getLastYieldResult();
                    if (lastYield != null && lastYield.isYieldStar()) {
                        delegatedYieldStarIteratorHolder[0] = lastYield.delegateIterator();
                        delegatedNextMethodHolder[0] = lastYield.cachedNextMethod() instanceof JSFunction cachedNext
                                ? cachedNext
                                : null;
                        // Use intermediate promise + handleDelegatedIteratorResult so
                        // errors from .value getters route back through the generator
                        JSPromise yieldStarPromise = executionContext.createJSPromise();
                        fulfillAsyncYieldStarResult(executionContext, yieldStarPromise, iteratorResult,
                                lastYield.delegateIterator(), lastYield.nativeAsyncIterator());
                        // Fast path: if the yield* result is already resolved and not done,
                        // fulfill synchronously to avoid extra microtask ticks from
                        // handleDelegatedIteratorResult's promise chain.
                        if (yieldStarPromise.getState() == JSPromise.PromiseState.FULFILLED) {
                            JSValue yieldStarResult = yieldStarPromise.getResult();
                            if (yieldStarResult instanceof JSObject yieldStarObj) {
                                JSValue doneVal = yieldStarObj.get(PropertyKey.DONE);
                                boolean isDone = doneVal instanceof JSBoolean && ((JSBoolean) doneVal).value();
                                if (!isDone) {
                                    promise.fulfill(yieldStarResult);
                                    return promise;
                                }
                            }
                        }
                        JSPromise delegatedResult = handleDelegatedIteratorResult(executionContext, yieldStarPromise,
                                generatorState, delegatedYieldStarIteratorHolder, lastYield.delegateIterator(),
                                asyncGeneratorRequestExecutorHolder, false);
                        delegatedResult.observe(executionContext,
                                new JSNativeFunction(executionContext, "onYieldStarResolve", 1, (c, t, a) -> {
                                    promise.fulfill(a.length > 0 ? a[0] : JSUndefined.INSTANCE);
                                    return JSUndefined.INSTANCE;
                                }), new JSNativeFunction(executionContext, "onYieldStarReject", 1, (c, t, a) -> {
                                    promise.reject(a.length > 0 ? a[0] : JSUndefined.INSTANCE);
                                    return JSUndefined.INSTANCE;
                                }));
                    } else {
                        delegatedYieldStarIteratorHolder[0] = null;
                        delegatedNextMethodHolder[0] = null;
                        // Per ES spec AsyncGeneratorYield step 8: Await the yielded value
                        JSValue yieldedValue = iteratorResult.get(PropertyKey.VALUE);
                        fulfillAsyncYield(executionContext, promise, yieldedValue, false);
                    }
                }
            } catch (Exception e) {
                // Preserve the actual JS error object from pending exception or VM exception payload.
                if (executionContext.hasPendingException()) {
                    JSValue exception = executionContext.getPendingException();
                    executionContext.clearAllPendingExceptions();
                    promise.reject(exception);
                } else if (e instanceof JSException jsException) {
                    promise.reject(jsException.getErrorValue());
                } else if (e instanceof JSVirtualMachineException virtualMachineException) {
                    if (virtualMachineException.getJsValue() != null) {
                        promise.reject(virtualMachineException.getJsValue());
                    } else if (virtualMachineException.getJsError() != null) {
                        promise.reject(virtualMachineException.getJsError());
                    } else {
                        String errorMessage = virtualMachineException.getMessage() != null
                                ? virtualMachineException.getMessage()
                                : virtualMachineException.toString();
                        JSValue error = executionContext.throwError(errorMessage);
                        executionContext.clearPendingException();
                        promise.reject(error);
                    }
                } else {
                    String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                    JSValue error = executionContext.throwError(errorMessage);
                    executionContext.clearPendingException();
                    promise.reject(error);
                }
            }

            return promise;
        };

        JSAsyncGenerator asyncGenerator = new JSAsyncGenerator(asyncGeneratorRequestExecutorHolder[0],
                executionContext);
        asyncGeneratorHolder[0] = asyncGenerator;
        return asyncGenerator;
    }

    public boolean displaysAsArgumentsObjectInToString() {
        return displaysAsArgumentsObjectInToString;
    }

    /**
     * This function's own named-function-expression binding, when the given frame is still initializing parameters and
     * so resolves the name through the parameter environment.
     * <p>
     * Kept apart from {@link #getInheritedParameterScopeFunctionNames()} because the two answer different questions.
     * This one names a binding of <em>this</em> frame, so it holds whatever the frame's own slots say; an inherited one
     * names a binding of an enclosing frame, which any binding this function declares under the same spelling shadows.
     *
     * @param frame
     *            the frame to answer for
     * @return the name, or null when the frame is not in that phase
     */
    public String getActiveParameterScopeFunctionName(StackFrame frame) {
        return isOwnParameterScopeActive(frame) ? parameterScopeFunctionName : null;
    }

    /**
     * The local slot this function sets to {@code true} the moment it enters its body, or -1 when it has no such slot.
     *
     * @return the slot index, or -1
     * @see #getParameterScopeFunctionName()
     */
    public int getBodyScopeEnteredLocalIndex() {
        return bodyScopeEnteredLocalIndex;
    }

    /**
     * Get the bytecode for this function.
     */
    public Bytecode getBytecode() {
        return bytecode;
    }

    /**
     * Get the captured active function for arrow functions. Used by SPECIAL_OBJECT 2 (THIS_FUNC) to resolve the
     * enclosing constructor for super() calls inside arrow functions.
     */
    public JSFunction getCapturedActiveFunction() {
        return capturedActiveFunction;
    }

    /**
     * Get the captured arguments object for arrow functions. Arrow functions lexically inherit arguments from the
     * enclosing non-arrow function.
     */
    public JSValue getCapturedArguments() {
        return capturedArguments;
    }

    public VarRef getCapturedDerivedThisRef() {
        return capturedDerivedThisRef;
    }

    /**
     * Get the captured new.target value for arrow functions. Arrow functions lexically inherit new.target from the
     * enclosing function.
     */
    public JSValue getCapturedNewTarget() {
        return capturedNewTarget;
    }

    /**
     * Get the captured this value for arrow functions.
     */
    public JSValue getCapturedThisArg() {
        return capturedThisArg;
    }

    public String getCapturedVarName(int captureSlot) {
        if (capturedVarNames == null || captureSlot < 0 || captureSlot >= capturedVarNames.length) {
            return null;
        }
        return capturedVarNames[captureSlot];
    }

    public String[] getCapturedVarNames() {
        return capturedVarNames;
    }

    /**
     * Get the capture source info array for template functions. Each entry encodes the source of a closure capture: -
     * {@code value >= 0}: LOCAL capture at that local slot index - {@code value < 0}: VAR_REF capture at
     * {@code -(value + 1)} Returns null for non-template (instantiated) functions.
     */
    public int[] getCaptureSourceInfos() {
        return captureSourceInfos;
    }

    public IdentityHashMap<JSSymbol, JSSymbol> getClassPrivateSymbolRemap() {
        return classPrivateSymbolRemap;
    }

    public Set<JSSymbol> getClassPrivateSymbols() {
        return classPrivateSymbols;
    }

    /**
     * Get the closure variables (captured from outer scopes).
     */
    public JSValue[] getClosureVars() {
        return closureVars;
    }

    /**
     * Get the constructor's prototype property (for constructor calls / new). Note: This is different from
     * getPrototype() which returns the internal [[Prototype]].
     */
    public JSObject getConstructorPrototype() {
        return prototype;
    }

    public StackFrame getEvalDynamicScopeFrame() {
        return evalDynamicScopeFrame;
    }

    /**
     * The named-function-expression bindings this function captured from enclosing parameter environments.
     * <p>
     * A closure written inside two nested initializers is inside both environments, so one name was never enough — the
     * inner one displaced the outer, and the outer binding silently vanished from everything compiled there.
     * <p>
     * These are names, not binding identities, and a name is not a binding: a function nested in an enclosing
     * initializer may declare its own parameter, {@code var}, lexical, class or function of the same spelling, and that
     * nearer binding is a different one — mutable, except for {@code const}. Callers must therefore treat an entry here
     * as authoritative only for a name the frame does not bind itself.
     *
     * @return the inherited bindings; empty when there are none
     */
    public Set<String> getInheritedParameterScopeFunctionNames() {
        return inheritedParameterScopeFunctionNames;
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
     * The name of this named function expression, when the body declares that name again.
     * <p>
     * ES2024 15.2.5 binds the {@code BindingIdentifier} of a named function expression in a function environment that
     * <em>wraps</em> the parameter and variable environments. A default initializer therefore resolves the name to the
     * function itself, while a {@code var}, lexical, class or function declaration of the same name in the body is a
     * different binding that starts as {@code undefined}. Both live in one flattened frame here, in two slots, and
     * compiled code refers to whichever is correct by index.
     * <p>
     * Direct {@code eval} cannot: it resolves by name, at run time, and the answer depends on which of the two
     * environments the frame is currently executing in. {@link #getBodyScopeEnteredLocalIndex()} is what tells the two
     * phases apart — it is false for the whole of parameter initialization and true from the first instruction of the
     * body — so a direct {@code eval} in a default initializer can be given the function, and one in the body the
     * body's own binding.
     *
     * @return the shadowed function-expression name, or null when this function has none
     */
    public String getParameterScopeFunctionName() {
        return parameterScopeFunctionName;
    }

    /**
     * Get the self-capture index for closure self-reference patching. Returns -1 if this function does not capture its
     * own name. Following QuickJS var_refs pattern where a function's closure variable pointing to itself is patched
     * after creation.
     */
    public int getSelfCaptureIndex() {
        return selfCaptureIndex;
    }

    public int getSelfLocalIndex() {
        return selfLocalIndex;
    }

    /**
     * Get the source code for this function.
     */
    public String getSourceCode() {
        return sourceCode;
    }

    /**
     * Get the VarRef array for closure variables (reference-based capture). Returns null if this function uses
     * value-based closureVars instead.
     */
    public VarRef[] getVarRefs() {
        return varRefs;
    }

    public boolean hasArgumentsParameterBinding() {
        return hasArgumentsParameterBinding;
    }

    /**
     * Check if this function has parameter expressions (default values, rest, or destructuring). Following QuickJS
     * has_parameter_expressions flag. Used by eval() to detect when var declarations would conflict with implicit
     * arguments binding.
     */
    public boolean hasParameterExpressions() {
        return hasParameterExpressions;
    }

    private Bytecode internalCopyBytecodeWithRemappedPrivateSymbols(IdentityHashMap<JSSymbol, JSSymbol> symbolRemap,
            IdentityHashMap<JSBytecodeFunction, JSBytecodeFunction> functionMemo) {
        JSValue[] originalConstants = bytecode.getConstants();
        if (originalConstants.length == 0) {
            return bytecode;
        }
        JSValue[] copiedConstants = new JSValue[originalConstants.length];
        boolean changed = false;
        for (int i = 0; i < originalConstants.length; i++) {
            JSValue originalConstant = originalConstants[i];
            JSValue copiedConstant = originalConstant;
            if (originalConstant instanceof JSSymbol originalSymbol) {
                JSSymbol remappedSymbol = symbolRemap.get(originalSymbol);
                if (remappedSymbol != null) {
                    copiedConstant = remappedSymbol;
                }
            } else if (originalConstant instanceof JSBytecodeFunction bytecodeFunction) {
                copiedConstant = bytecodeFunction.internalCopyTemplateWithRemappedPrivateSymbols(symbolRemap,
                        functionMemo);
            }
            copiedConstants[i] = copiedConstant;
            if (copiedConstant != originalConstant) {
                changed = true;
            }
        }
        if (!changed) {
            return bytecode;
        }
        Bytecode copiedBytecode = new Bytecode(bytecode.getInstructions(), copiedConstants, bytecode.getAtoms(),
                bytecode.getLocalCount(), bytecode.getLocalVarNames());
        copiedBytecode.setSourcePositions(bytecode.getSourcePositions());
        copiedBytecode.setCallDescriptions(bytecode.getCallDescriptions());
        return copiedBytecode;
    }

    private JSBytecodeFunction internalCopyTemplateWithRemappedPrivateSymbols(
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap,
            IdentityHashMap<JSBytecodeFunction, JSBytecodeFunction> functionMemo) {
        JSBytecodeFunction memoizedFunction = functionMemo.get(this);
        if (memoizedFunction != null) {
            return memoizedFunction;
        }
        Bytecode remappedBytecode = internalCopyBytecodeWithRemappedPrivateSymbols(symbolRemap, functionMemo);
        JSBytecodeFunction copiedFunction = new JSBytecodeFunction(context, remappedBytecode, name, length, closureVars,
                prototype, isConstructor, isAsync, isGenerator, isArrow, strict, sourceCode, selfCaptureIndex);
        functionMemo.put(this, copiedFunction);
        copiedFunction.captureSourceInfos = captureSourceInfos;
        copyClosureStateTo(copiedFunction);
        copiedFunction.classConstructor = classConstructor;
        copiedFunction.derivedConstructor = derivedConstructor;
        copiedFunction.varRefs = varRefs;
        return copiedFunction;
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
     * Check if this is a class constructor. Class constructors throw TypeError when called without 'new'.
     */
    public boolean isClassConstructor() {
        return classConstructor;
    }

    /**
     * Check if this function can be used as a constructor.
     */
    public boolean isConstructor() {
        return isConstructor;
    }

    /**
     * Check if this is a derived class constructor (extends a parent class).
     */
    public boolean isDerivedConstructor() {
        return derivedConstructor;
    }

    public boolean isEvalDynamicScopeLookupEnabled() {
        return evalDynamicScopeLookupEnabled;
    }

    public boolean isEvalSuperCallAllowed() {
        return evalSuperCallAllowed;
    }

    /**
     * Check if this is a generator function.
     */
    public boolean isGenerator() {
        return isGenerator;
    }

    public boolean isNewTargetAllowed() {
        return newTargetAllowed;
    }

    /**
     * Whether this frame is still initializing the parameters of a named function expression whose body redeclares its
     * own name.
     *
     * @param frame
     *            the frame to answer for
     * @return true while the parameter environment, not the body, is what the name resolves through
     */
    public boolean isOwnParameterScopeActive(StackFrame frame) {
        if (parameterScopeFunctionName == null || bodyScopeEnteredLocalIndex < 0) {
            return false;
        }
        JSValue[] locals = frame == null ? null : frame.getLocals();
        return locals != null && bodyScopeEnteredLocalIndex < locals.length
                && locals[bodyScopeEnteredLocalIndex] != JSBoolean.TRUE;
    }

    public boolean isProgram() {
        return program;
    }

    /**
     * Check if this function is in strict mode. Following QuickJS {@code js_mode & JS_MODE_STRICT}.
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Set the captured active function for arrow functions. Called during FCLOSURE to capture the enclosing non-arrow
     * function reference for super() resolution.
     */
    public void setCapturedActiveFunction(JSFunction capturedActiveFunction) {
        this.capturedActiveFunction = capturedActiveFunction;
    }

    /**
     * Set the captured arguments object for arrow functions. Called during FCLOSURE to capture the enclosing non-arrow
     * function's arguments.
     */
    public void setCapturedArguments(JSValue capturedArguments) {
        this.capturedArguments = capturedArguments;
    }

    public void setCapturedDerivedThisRef(VarRef ref) {
        this.capturedDerivedThisRef = ref;
    }

    /**
     * Set the captured new.target value for arrow functions. Called during FCLOSURE to capture the enclosing function's
     * new.target.
     */
    public void setCapturedNewTarget(JSValue capturedNewTarget) {
        this.capturedNewTarget = capturedNewTarget;
    }

    /**
     * Set the captured this value for arrow functions. Called during FCLOSURE to capture the enclosing scope's this.
     */
    public void setCapturedThisArg(JSValue thisArg) {
        this.capturedThisArg = thisArg;
    }

    public void setCapturedVarNames(String[] capturedVarNames) {
        this.capturedVarNames = capturedVarNames;
    }

    /**
     * Set the capture source info array for this template function. Called by the compiler to record where each closure
     * variable comes from.
     */
    public void setCaptureSourceInfos(int[] captureSourceInfos) {
        this.captureSourceInfos = captureSourceInfos;
    }

    /**
     * Mark this function as a class constructor. Called during DEFINE_CLASS opcode execution.
     */
    public void setClassConstructor(boolean classConstructor) {
        this.classConstructor = classConstructor;
    }

    public void setClassPrivateSymbolRemap(IdentityHashMap<JSSymbol, JSSymbol> classPrivateSymbolRemap) {
        this.classPrivateSymbolRemap = classPrivateSymbolRemap;
    }

    public void setClassPrivateSymbols(Collection<JSSymbol> classPrivateSymbols) {
        if (classPrivateSymbols == null || classPrivateSymbols.isEmpty()) {
            this.classPrivateSymbols = Set.of();
            return;
        }
        Set<JSSymbol> symbolSet = Collections.newSetFromMap(new IdentityHashMap<>());
        symbolSet.addAll(classPrivateSymbols);
        this.classPrivateSymbols = symbolSet;
    }

    /**
     * Mark this function as a derived class constructor. Called during DEFINE_CLASS opcode execution when a superclass
     * is present.
     */
    public void setDerivedConstructor(boolean derivedConstructor) {
        this.derivedConstructor = derivedConstructor;
    }

    public void setDisplaysAsArgumentsObjectInToString(boolean displaysAsArgumentsObjectInToString) {
        this.displaysAsArgumentsObjectInToString = displaysAsArgumentsObjectInToString;
    }

    public void setEvalDynamicScopeFrame(StackFrame evalDynamicScopeFrame) {
        this.evalDynamicScopeFrame = evalDynamicScopeFrame;
    }

    public void setEvalDynamicScopeLookupEnabled(boolean evalDynamicScopeLookupEnabled) {
        this.evalDynamicScopeLookupEnabled = evalDynamicScopeLookupEnabled;
    }

    public void setEvalSuperCallAllowed(boolean evalSuperCallAllowed) {
        this.evalSuperCallAllowed = evalSuperCallAllowed;
    }

    public void setHasArgumentsParameterBinding(boolean hasArgumentsParameterBinding) {
        this.hasArgumentsParameterBinding = hasArgumentsParameterBinding;
    }

    /**
     * Set whether this function has parameter expressions.
     */
    public void setHasParameterExpressions(boolean hasParameterExpressions) {
        this.hasParameterExpressions = hasParameterExpressions;
    }

    /**
     * Record the named-function-expression bindings this function captured from the parameter environments it was
     * created inside.
     *
     * @param inheritedParameterScopeFunctionNames
     *            the names
     * @see #getInheritedParameterScopeFunctionNames()
     */
    public void setInheritedParameterScopeFunctionNames(Set<String> inheritedParameterScopeFunctionNames) {
        this.inheritedParameterScopeFunctionNames = inheritedParameterScopeFunctionNames == null
                ? Set.of()
                : inheritedParameterScopeFunctionNames;
    }

    public void setNewTargetAllowed(boolean newTargetAllowed) {
        this.newTargetAllowed = newTargetAllowed;
    }

    /**
     * Record the two-environment shape of a named function expression whose body redeclares its name.
     *
     * @param parameterScopeFunctionName
     *            the function-expression name
     * @param bodyScopeEnteredLocalIndex
     *            the slot set to true on entering the body
     * @see #getActiveParameterScopeFunctionName(StackFrame)
     */
    public void setOwnParameterScopeFunctionName(String parameterScopeFunctionName, int bodyScopeEnteredLocalIndex) {
        this.parameterScopeFunctionName = parameterScopeFunctionName;
        this.bodyScopeEnteredLocalIndex = bodyScopeEnteredLocalIndex;
    }

    public void setProgram(boolean program) {
        this.program = program;
    }

    public void setSelfLocalIndex(int selfLocalIndex) {
        this.selfLocalIndex = selfLocalIndex;
    }

    /**
     * Set the source code for this function. Used to override the default toString() representation.
     */
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    private JSObject startGenerator(JSContext executionContext, JSValue thisArg, JSValue[] args) {
        // Both kinds execute through INITIAL_YIELD so parameter errors surface at the call site.
        JSGeneratorState state = new JSGeneratorState(this, thisArg, args);
        executionContext.getVirtualMachine().executeGenerator(state, executionContext);
        JSGenerator driver = new JSGenerator(executionContext, state);
        JSObject generator = isAsync ? createAsyncGenerator(executionContext, state, driver) : driver;
        JSValue prototype = get(PropertyKey.PROTOTYPE);
        if (prototype instanceof JSObject object) {
            generator.setPrototype(object);
        } else if (isAsync && executionContext.getAsyncGeneratorPrototype() != null) {
            generator.setPrototype(executionContext.getAsyncGeneratorPrototype());
        } else {
            JSObject functionPrototype = isAsync
                    ? executionContext.getAsyncGeneratorFunctionPrototype()
                    : executionContext.getGeneratorFunctionPrototype();
            if (functionPrototype != null
                    && functionPrototype.get(PropertyKey.PROTOTYPE) instanceof JSObject fallback) {
                generator.setPrototype(fallback);
            }
        }
        return generator;
    }

    @Override
    public Object toJavaObject() {
        return toString();
    }

    @Override
    public String toString() {
        // If source code is available, return it (QuickJS: b->debug.source)
        if (sourceCode != null) {
            return sourceCode;
        }

        // QuickJS format: "[prefix]name() {\n [native code]\n}"
        String prefix;
        if (isAsync && isGenerator) {
            prefix = "async function *";
        } else if (isAsync) {
            prefix = "async function ";
        } else if (isGenerator) {
            prefix = "function *";
        } else {
            prefix = "function ";
        }
        String displayName = name != null ? name : "";
        return prefix + displayName + "() {\n    [native code]\n}";
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
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
                JSValue error = JSAsyncIterator.consumePendingExceptionOrCreateStringError(context, e);
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

    private static JSPromise.ReactionRecord awaitReaction(JSContext context, String name,
            JSGeneratorState.ResumeKind resumeKind, JSGeneratorState state,
            JSAsyncGenerator.AsyncGeneratorFunction executor, JSAsyncGenerator.AsyncGeneratorRequestKind requestKind,
            JSAsyncGenerator generator, JSPromise requestPromise) {
        JSNativeFunction callback = new JSNativeFunction(context, name, 1, (childContext, thisArg, args) -> {
            state.setPendingResumeRecord(resumeKind, JSValue.argumentOrUndefined(args, 0));
            JSPromise resumed = executor.executeNext(JSUndefined.INSTANCE, requestKind);
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

    private static JSPromise createAsyncDelegatedNextPromise(JSContext context, JSObject delegateIterator,
            JSValue inputValue, JSFunction cachedNextMethod) {
        // Per ES spec, the next method is cached during GetIterator and reused
        JSFunction nextFunction;
        if (cachedNextMethod != null) {
            nextFunction = cachedNextMethod;
        } else {
            JSValue nextMethodValue = delegateIterator.get(PropertyKey.NEXT);
            if (context.hasPendingException()) {
                return JSAsyncIterator.rejectPendingException(context);
            }
            if (!(nextMethodValue instanceof JSFunction nextFunc)) {
                return JSAsyncIterator.rejectTypeError(context, "iterator next is not a function");
            }
            nextFunction = nextFunc;
        }
        JSValue nextResult;
        try {
            nextResult = nextFunction.call(context, delegateIterator, new JSValue[]{inputValue});
        } catch (Exception e) {
            JSValue error = JSAsyncIterator.consumePendingExceptionOrCreateStringError(context, e);
            return JSAsyncIterator.createRejectedPromise(context, error);
        }
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        if (!(nextResult instanceof JSObject nextResultObject)) {
            return JSAsyncIterator.rejectTypeError(context, "iterator must return an object");
        }
        // Per ES2024: "If generatorKind is async, then set innerResult to ? Await(innerResult)"
        // Handle thenable/promise results before reading done/value
        return awaitAndProcessIteratorResult(context, nextResultObject, delegateIterator);
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
            JSValue error = JSAsyncIterator.consumePendingExceptionOrCreateStringError(context, e);
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
                JSValue error = JSAsyncIterator.consumePendingExceptionOrCreateStringError(context, e);
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
     * Handle the result of a delegated iterator operation (next/throw/return). When done=false: resolve outerPromise
     * with the result, keep the delegate. When done=true: set yieldStarCompletionValue, resume the generator, chain
     * result.
     */
    private static JSPromise handleDelegatedIteratorResult(JSContext context, JSPromise delegatedPromise,
            JSGeneratorState generatorState, JSObject[] delegatedYieldStarIteratorHolder, JSObject delegateIterator,
            JSAsyncGenerator.AsyncGeneratorFunction[] asyncGeneratorRequestExecutorHolder, boolean isReturnCompletion) {
        JSPromise outerPromise = context.createJSPromise();
        delegatedPromise.observe(context, new JSNativeFunction(context, "onDelegatedResolve", 1,
                (childContext, callbackThisArg, callbackArgs) -> {
                    JSValue resultValue = JSValue.argumentOrUndefined(callbackArgs, 0);
                    if (!(resultValue instanceof JSObject resultObject)) {
                        delegatedYieldStarIteratorHolder[0] = null;
                        generatorState.setCompleted(true);
                        outerPromise.reject(childContext.throwTypeError("Iterator result is not an object"));
                        childContext.clearAllPendingExceptions();
                        return JSUndefined.INSTANCE;
                    }
                    JSValue doneValue = resultObject.get(PropertyKey.DONE);
                    if (childContext.hasPendingException()) {
                        delegatedYieldStarIteratorHolder[0] = null;
                        generatorState.setCompleted(true);
                        JSValue error = childContext.getPendingException();
                        childContext.clearAllPendingExceptions();
                        outerPromise.reject(error);
                        return JSUndefined.INSTANCE;
                    }
                    boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
                    if (done) {
                        // Delegation completed — resume the generator past yield*
                        delegatedYieldStarIteratorHolder[0] = null;
                        JSValue value = resultObject.get(PropertyKey.VALUE);
                        if (childContext.hasPendingException()) {
                            generatorState.setCompleted(true);
                            JSValue error = childContext.getPendingException();
                            childContext.clearAllPendingExceptions();
                            outerPromise.reject(error);
                            return JSUndefined.INSTANCE;
                        }
                        // Set pending resume record so the generator resumes
                        // from saved execution state (after yield*) with the completion value.
                        // RETURN: generator force-returns with value (enters finally blocks only)
                        // NEXT: completion value becomes the yield* expression result
                        generatorState.setPendingResumeRecord(isReturnCompletion
                                ? JSGeneratorState.ResumeKind.RETURN
                                : JSGeneratorState.ResumeKind.NEXT, value);
                        resumeDelegation(context, childContext, asyncGeneratorRequestExecutorHolder[0], outerPromise);
                    } else {
                        // Not done — yield the value to the caller
                        delegatedYieldStarIteratorHolder[0] = delegateIterator;
                        outerPromise.fulfill(resultValue);
                    }
                    return JSUndefined.INSTANCE;
                }),
                new JSNativeFunction(context, "onDelegatedReject", 1, (childContext, callbackThisArg, callbackArgs) -> {
                    delegatedYieldStarIteratorHolder[0] = null;
                    JSValue error = JSValue.argumentOrUndefined(callbackArgs, 0);
                    // Per ES2024: errors from Await(innerResult) and IteratorValue
                    // should propagate inside the generator (? notation in spec).
                    // Route the error back through the generator via THROW resume
                    // so the generator's try/catch can intercept it.
                    generatorState.setPendingResumeRecord(JSGeneratorState.ResumeKind.THROW, error);
                    resumeDelegation(context, childContext, asyncGeneratorRequestExecutorHolder[0], outerPromise);
                    return JSUndefined.INSTANCE;
                }));
        return outerPromise;
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
        JSValue error = context.getPendingException();
        context.clearAllPendingExceptions();
        promise.reject(error);
        return true;
    }

    private static void resumeAsyncFunctionExecution(JSContext context, JSGeneratorState asyncFunctionState,
            JSPromise outerPromise) {
        try {
            JSValue result = context.getVirtualMachine().executeAsyncFunction(asyncFunctionState, context);
            JSPromise awaitedPromise = context.getVirtualMachine().consumeAwaitSuspensionPromise();
            if (awaitedPromise != null) {
                awaitedPromise.observe(context, new JSNativeFunction(context, "onAwaitResolve", 1,
                        (childContext, callbackThisArg, callbackArgs) -> {
                            JSValue resolvedValue = JSValue.argumentOrUndefined(callbackArgs, 0);
                            asyncFunctionState.setPendingResumeRecord(JSGeneratorState.ResumeKind.NEXT, resolvedValue);
                            resumeAsyncFunctionExecution(context, asyncFunctionState, outerPromise);
                            return JSUndefined.INSTANCE;
                        }), new JSNativeFunction(context, "onAwaitReject", 1,
                                (childContext, callbackThisArg, callbackArgs) -> {
                                    JSValue rejectionValue = JSValue.argumentOrUndefined(callbackArgs, 0);
                                    asyncFunctionState.setPendingResumeRecord(JSGeneratorState.ResumeKind.THROW,
                                            rejectionValue);
                                    resumeAsyncFunctionExecution(context, asyncFunctionState, outerPromise);
                                    return JSUndefined.INSTANCE;
                                }));
                return;
            }
            asyncFunctionState.clearSuspendedExecutionState();
            if (context.hasPendingException()) {
                JSValue exception = context.getPendingException();
                context.clearAllPendingExceptions();
                outerPromise.reject(exception);
                return;
            }
            outerPromise.resolve(context, result);
        } catch (JSVirtualMachineException e) {
            if (context.hasPendingException()) {
                JSValue exception = context.getPendingException();
                context.clearAllPendingExceptions();
                outerPromise.reject(exception);
            } else if (e.getJsValue() != null) {
                outerPromise.reject(e.getJsValue());
            } else if (e.getJsError() != null) {
                outerPromise.reject(e.getJsError());
            } else {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                JSObject errorObj = context.createJSObject();
                errorObj.set(PropertyKey.MESSAGE, new JSString(errorMessage));
                outerPromise.reject(errorObj);
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
            JSObject errorObj = context.createJSObject();
            errorObj.set(PropertyKey.MESSAGE, new JSString(errorMessage));
            outerPromise.reject(errorObj);
        }
    }

    private static void resumeDelegation(JSContext context, JSContext reactionContext,
            JSAsyncGenerator.AsyncGeneratorFunction executor, JSPromise outer) {
        JSPromise resumed = executor.executeNext(JSUndefined.INSTANCE, JSAsyncGenerator.AsyncGeneratorRequestKind.NEXT);
        resumed.observe(reactionContext, outer.fulfillHandler(context, "onResumeResolve"),
                outer.rejectHandler(context, "onResumeReject"));
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
