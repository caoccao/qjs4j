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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.math.BigInteger;
import java.util.*;

/**
 * The JavaScript virtual machine bytecode interpreter.
 * Executes compiled bytecode using a stack-based architecture.
 */
public final class VirtualMachine {
    static final BigInteger BIGINT_NEGATIVE_ONE = BigInteger.valueOf(-1);
    static final BigInteger BIGINT_ONE = BigInteger.ONE;
    static final BigInteger BIGINT_ZERO = BigInteger.ZERO;
    static final JSValue[] EMPTY_ARGS = new JSValue[0];
    static final int INTERRUPT_CHECK_INTERVAL = 0xFFFF; // Check every ~65K opcodes
    static final JSObject UNINITIALIZED_MARKER = new JSObject();
    final JSContext context;
    final Set<JSObject> initializedConstantObjects;
    final StringBuilder propertyAccessChain;  // Track last property access for better error messages
    final boolean trackPropertyAccess;
    final CallStack valueStack;
    JSGeneratorState activeGeneratorState;
    boolean awaitSuspensionEnabled;
    JSPromise awaitSuspensionPromise;
    StackFrame currentFrame;
    long executionDeadline;  // 0 = no deadline
    long executionDeadlineNanos; // 0 = no deadline
    JSValue[] forOfTempValues;
    boolean generatorForceReturn;  // When true, exception handler skips catch offsets, enters only finally
    int generatorResumeIndex;
    List<JSGeneratorState.ResumeRecord> generatorResumeRecords;
    JSValue generatorReturnValue;  // The return value during generator force return
    int interruptCounter;
    JSValue lastConstructorThisArg;  // Saved from frame before return for derived constructor check
    JSValue pendingException;
    boolean propertyAccessLock;  // When true, don't update lastPropertyAccess (during argument evaluation)
    YieldResult yieldResult;  // Set when generator yields
    int yieldSkipCount;  // How many yields to skip (for resuming generators)

    public VirtualMachine(JSContext context) {
        this.valueStack = new CallStack();
        this.context = context;
        this.activeGeneratorState = null;
        this.initializedConstantObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        this.currentFrame = null;
        this.generatorResumeRecords = List.of();
        this.generatorResumeIndex = 0;
        this.pendingException = null;
        this.propertyAccessChain = new StringBuilder();
        this.trackPropertyAccess = !"false".equalsIgnoreCase(System.getProperty("qjs4j.vm.trackPropertyAccess", "true"));
        this.propertyAccessLock = false;
        this.awaitSuspensionEnabled = false;
        this.awaitSuspensionPromise = null;
        this.yieldResult = null;
        this.yieldSkipCount = 0;
        this.executionDeadline = 0;
        this.executionDeadlineNanos = 0;
        this.interruptCounter = 0;
        this.forOfTempValues = EMPTY_ARGS;
    }

    static PendingExceptionAction handlePendingExceptionForExecute(ExecutionContext executionContext) {
        if (executionContext.virtualMachine.pendingException == null) {
            return PendingExceptionAction.NONE;
        }

        JSValue exception = executionContext.virtualMachine.pendingException;
        executionContext.virtualMachine.pendingException = null;

        boolean foundHandler = false;
        while (executionContext.virtualMachine.valueStack.stackTop > executionContext.frameStackBase) {
            JSStackValue stackValue = executionContext.stack[--executionContext.virtualMachine.valueStack.stackTop];
            if (stackValue instanceof JSCatchOffset catchOffset) {
                if (executionContext.virtualMachine.generatorForceReturn && !catchOffset.isFinally()) {
                    continue;
                }
                executionContext.stack[executionContext.virtualMachine.valueStack.stackTop++] = exception;
                executionContext.pc = catchOffset.offset();
                foundHandler = true;
                executionContext.virtualMachine.context.clearPendingException();
                break;
            }
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;

        if (foundHandler) {
            return PendingExceptionAction.CONTINUE;
        }

        if (executionContext.virtualMachine.generatorForceReturn) {
            executionContext.virtualMachine.generatorForceReturn = false;
            executionContext.virtualMachine.restoreExecuteCallerState(
                    executionContext.restoreStackTop,
                    executionContext.previousFrame,
                    executionContext.savedStrictMode);
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.returnValue = executionContext.virtualMachine.generatorReturnValue;
            return PendingExceptionAction.RETURN;
        }

        executionContext.virtualMachine.restoreExecuteCallerState(
                executionContext.restoreStackTop,
                executionContext.previousFrame,
                executionContext.savedStrictMode);
        if (exception instanceof JSError jsError) {
            throw new JSVirtualMachineException(jsError);
        }
        String exceptionMessage = executionContext.virtualMachine.safeExceptionToString(executionContext.virtualMachine.context, exception);
        throw new JSVirtualMachineException("Unhandled exception: " + exceptionMessage, exception);
    }

    JSValue addValues(JSValue left, JSValue right) {
        JSValue leftPrimitive = JSTypeConversions.toPrimitive(context, left, JSTypeConversions.PreferredType.DEFAULT);
        JSValue rightPrimitive = JSTypeConversions.toPrimitive(context, right, JSTypeConversions.PreferredType.DEFAULT);
        capturePendingException();

        if (leftPrimitive instanceof JSString || rightPrimitive instanceof JSString) {
            JSValue result = new JSString(
                    JSTypeConversions.toString(context, leftPrimitive).value()
                            + JSTypeConversions.toString(context, rightPrimitive).value());
            capturePendingException();
            return result;
        }

        JSValue leftNumeric = toNumericValue(leftPrimitive);
        JSValue rightNumeric = toNumericValue(rightPrimitive);
        if (leftNumeric instanceof JSBigInt leftBigInt && rightNumeric instanceof JSBigInt rightBigInt) {
            return new JSBigInt(leftBigInt.value().add(rightBigInt.value()));
        }
        if (leftNumeric instanceof JSBigInt || rightNumeric instanceof JSBigInt) {
            return throwMixedBigIntTypeError();
        }
        return JSNumber.of(((JSNumber) leftNumeric).value() + ((JSNumber) rightNumeric).value());
    }

    void appendPropertyAccessForArrayIndex(JSValue indexValue) {
        if (indexValue instanceof JSString stringValue) {
            if (!propertyAccessChain.isEmpty()) {
                propertyAccessChain.append('.');
            }
            propertyAccessChain.append(stringValue.value());
        } else if (indexValue instanceof JSNumber numberValue) {
            String propertyName = JSTypeConversions.toString(context, numberValue).value();
            if (!propertyAccessChain.isEmpty()) {
                propertyAccessChain.append('.');
            }
            propertyAccessChain.append(propertyName);
        } else if (indexValue instanceof JSSymbol symbolValue) {
            propertyAccessChain.append("[Symbol.").append(symbolValue.getDescription()).append("]");
        }
    }

    JSValue[] buildApplyArguments(JSValue argsArrayValue, boolean allowNullOrUndefined) {
        if (allowNullOrUndefined && argsArrayValue.isNullOrUndefined()) {
            return EMPTY_ARGS;
        }
        if (!(argsArrayValue instanceof JSObject arrayLike)) {
            context.throwTypeError("CreateListFromArrayLike called on non-object");
            return null;
        }

        JSValue lengthValue = arrayLike.get(context, PropertyKey.LENGTH);
        if (context.hasPendingException()) {
            return null;
        }

        long length = JSTypeConversions.toLength(context, lengthValue);
        if (context.hasPendingException()) {
            return null;
        }
        if (length > Integer.MAX_VALUE) {
            context.throwRangeError("too many arguments in function call");
            return null;
        }

        JSValue[] args = new JSValue[(int) length];
        for (int i = 0; i < args.length; i++) {
            JSValue argValue = arrayLike.get(context, PropertyKey.fromString(Integer.toString(i)));
            if (context.hasPendingException()) {
                return null;
            }
            args[i] = argValue;
        }
        return args;
    }

    void capturePendingException() {
        if (pendingException == null && context.hasPendingException()) {
            pendingException = context.getPendingException();
        }
    }

    void capturePendingExceptionFromVmOrContext(JSVirtualMachineException e) {
        if (e.getJsValue() != null) {
            pendingException = e.getJsValue();
        } else if (e.getJsError() != null) {
            pendingException = e.getJsError();
        } else if (context.hasPendingException()) {
            pendingException = context.getPendingException();
        } else {
            pendingException = context.throwError("Error",
                    e.getMessage() != null ? e.getMessage() : "Unhandled exception");
        }
        context.clearPendingException();
    }

    /**
     * Convert a JSVirtualMachineException to a pendingException so the VM's
     * JS exception handling mechanism (catch handlers on the value stack) can process it.
     */
    void captureVMException(JSVirtualMachineException e) {
        if (e.getJsValue() != null) {
            pendingException = e.getJsValue();
        } else if (e.getJsError() != null) {
            pendingException = e.getJsError();
        } else if (context.hasPendingException()) {
            pendingException = context.getPendingException();
        } else {
            pendingException = context.throwError("Error",
                    e.getMessage() != null ? e.getMessage() : "Unhandled exception");
        }
        context.clearPendingException();
    }

    void checkExecutionInterruptForExecute() {
        if (executionDeadline != 0 && --interruptCounter <= 0) {
            interruptCounter = INTERRUPT_CHECK_INTERVAL;
            if (System.nanoTime() >= executionDeadlineNanos) {
                throw new JSVirtualMachineException("execution timeout");
            }
        }
    }

    void clearActiveGeneratorSuspendedExecutionState() {
        if (activeGeneratorState != null) {
            activeGeneratorState.clearSuspendedExecutionState();
        }
    }

    /**
     * Clear the pending exception in the VM.
     * This is needed when an async function catches an exception.
     */
    public void clearPendingException() {
        this.pendingException = null;
    }

    JSValue constructFunction(JSFunction function, JSValue[] args, JSValue newTarget) {
        if (function instanceof JSBoundFunction boundFunction) {
            JSFunction targetFunction = boundFunction.getTarget();
            if (!JSTypeChecking.isConstructor(targetFunction)) {
                context.throwTypeError(boundFunction.getName() + " is not a constructor");
                return JSUndefined.INSTANCE;
            }
            JSValue adjustedNewTarget = newTarget == function ? targetFunction : newTarget;
            return constructFunction(targetFunction, boundFunction.prependBoundArgs(args), adjustedNewTarget);
        }
        if (function instanceof JSClass jsClass) {
            return jsClass.construct(context, args);
        }

        JSConstructorType constructorType = function.getConstructorType();
        if (constructorType == null) {
            JSObject thisObject = new JSObject();
            String intrinsicDefaultPrototypeName = context.getIntrinsicDefaultPrototypeName(function);
            if (newTarget instanceof JSObject newTargetObject) {
                JSObject resolvedPrototype = context.getPrototypeFromConstructor(
                        newTargetObject,
                        intrinsicDefaultPrototypeName);
                if (context.hasPendingException()) {
                    JSValue exception = context.getPendingException();
                    throw new JSVirtualMachineException(exception.toString(), exception);
                }
                if (resolvedPrototype != null) {
                    thisObject.setPrototype(resolvedPrototype);
                }
            } else {
                JSObject resolvedPrototype = context.getPrototypeFromConstructor(function, intrinsicDefaultPrototypeName);
                if (context.hasPendingException()) {
                    JSValue exception = context.getPendingException();
                    throw new JSVirtualMachineException(exception.toString(), exception);
                }
                if (resolvedPrototype != null) {
                    thisObject.setPrototype(resolvedPrototype);
                }
            }

            // Check if this is a derived constructor
            boolean isDerived = function instanceof JSBytecodeFunction bcFunc
                    && bcFunc.isDerivedConstructor();

            // For derived constructors, use JSUndefined as initial this
            // (this must be initialized by super() call)
            JSValue constructThis = isDerived ? JSUndefined.INSTANCE : thisObject;

            JSValue result;
            JSContext constructorContext = function.getRealmContext() != null ? function.getRealmContext() : context;
            JSValue savedNewTarget = context.getConstructorNewTarget();
            JSValue savedConstructorContextNewTarget = null;
            JSValue savedNativeConstructorContextNewTarget = null;
            context.setConstructorNewTarget(newTarget);
            if (constructorContext != context) {
                savedConstructorContextNewTarget = constructorContext.getConstructorNewTarget();
                constructorContext.setConstructorNewTarget(newTarget);
            }
            try {
                if (function instanceof JSNativeFunction nativeFunc) {
                    savedNativeConstructorContextNewTarget = constructorContext.getNativeConstructorNewTarget();
                    constructorContext.setNativeConstructorNewTarget(newTarget);
                    result = nativeFunc.call(context, constructThis, args);
                    if (context.hasPendingException()) {
                        JSValue exception = context.getPendingException();
                        String errorMessage = "Unhandled exception in constructor";
                        if (exception instanceof JSObject errorObj) {
                            JSValue messageValue = errorObj.get(PropertyKey.MESSAGE);
                            if (messageValue instanceof JSString messageString) {
                                errorMessage = messageString.value();
                            }
                        }
                        throw new JSVirtualMachineException(errorMessage);
                    }
                } else if (function instanceof JSBytecodeFunction bytecodeFunction) {
                    result = constructorContext.getVirtualMachine().execute(bytecodeFunction, constructThis, args, newTarget);
                    if (constructorContext != context && constructorContext.hasPendingException()) {
                        context.setPendingException(constructorContext.getPendingException());
                        constructorContext.clearPendingException();
                    }
                } else {
                    result = function.call(constructorContext, constructThis, args);
                    if (constructorContext != context && constructorContext.hasPendingException()) {
                        context.setPendingException(constructorContext.getPendingException());
                        constructorContext.clearPendingException();
                    }
                }
            } finally {
                context.setConstructorNewTarget(savedNewTarget);
                if (function instanceof JSNativeFunction) {
                    constructorContext.setNativeConstructorNewTarget(savedNativeConstructorContextNewTarget);
                }
                if (constructorContext != context) {
                    constructorContext.setConstructorNewTarget(savedConstructorContextNewTarget);
                }
            }

            // ES spec step 13: validate constructor return value
            if (result instanceof JSObject) {
                return result;
            }
            if (isDerived) {
                // ES spec step 13c: If result is not undefined, throw TypeError
                if (!(result instanceof JSUndefined)) {
                    throw new JSVirtualMachineException(
                            context.throwTypeError("Derived constructors may only return object or undefined"));
                }
                // ES spec step 15: Return GetThisBinding()
                // If super() was never called, this is still uninitialized (JSUndefined)
                // In that case throw ReferenceError
                // Use lastConstructorThisArg saved by RETURN/RETURN_UNDEF before frame was popped
                JSValue finalThis = constructorContext == context
                        ? lastConstructorThisArg
                        : constructorContext.getVirtualMachine().lastConstructorThisArg;
                if (finalThis == null || finalThis instanceof JSUndefined) {
                    throw new JSVirtualMachineException(
                            context.throwReferenceError("Must call super constructor in derived class before accessing 'this' or returning from derived constructor"));
                }
                if (finalThis instanceof JSObject finalThisObj) {
                    return finalThisObj;
                }
                throw new JSVirtualMachineException(
                        context.throwReferenceError("Must call super constructor in derived class before accessing 'this' or returning from derived constructor"));
            }
            return thisObject;
        }

        JSValue result;
        try {
            result = constructorType.create(context, args);
        } catch (JSErrorException e) {
            throw new JSVirtualMachineException(
                    context.throwError(e.getErrorType().name(), e.getMessage()));
        }
        if (context.hasPendingException()) {
            throw new JSVirtualMachineException(context.getPendingException().toString(),
                    context.getPendingException());
        }

        // Per ES spec and QuickJS (js_create_from_ctor), resolve the prototype
        // from newTarget AFTER argument processing so that argument errors
        // (e.g. ToIndex(Symbol) → TypeError) are thrown before accessing
        // newTarget.prototype.
        if (result instanceof JSObject jsObject && !jsObject.isProxy()) {
            JSObject resolvedPrototype = null;
            if (newTarget instanceof JSObject newTargetObject) {
                String intrinsicDefaultPrototypeName = context.getIntrinsicDefaultPrototypeName(function);
                resolvedPrototype = context.getPrototypeFromConstructor(
                        newTargetObject,
                        intrinsicDefaultPrototypeName);
                if (context.hasPendingException()) {
                    throw new JSVirtualMachineException(context.getPendingException().toString(),
                            context.getPendingException());
                }
            }
            if (resolvedPrototype != null) {
                jsObject.setPrototype(resolvedPrototype);
            } else {
                context.transferPrototype(jsObject, function);
            }
            if (jsObject instanceof JSDataView dataView && !dataView.validateConstructorState(context)) {
                throw new JSVirtualMachineException(context.getPendingException().toString(),
                        context.getPendingException());
            }
        }
        return result;
    }

    public JSPromise consumeAwaitSuspensionPromise() {
        JSPromise promise = awaitSuspensionPromise;
        awaitSuspensionPromise = null;
        return promise;
    }

    void copyDataProperties(JSValue targetValue, JSValue sourceValue, JSValue excludeListValue) {
        if (!(targetValue instanceof JSObject targetObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("copy target must be an object"));
        }
        if (sourceValue == null || sourceValue.isNullOrUndefined()) {
            return;
        }

        JSObject sourceObject = toObject(sourceValue);
        if (sourceObject == null) {
            return;
        }

        Set<PropertyKey> excludedKeys = null;
        if (excludeListValue instanceof JSArray excludeArray) {
            excludedKeys = new HashSet<>();
            for (int i = 0; i < excludeArray.getLength(); i++) {
                excludedKeys.add(PropertyKey.fromValue(context, excludeArray.get(i)));
            }
        } else if (excludeListValue instanceof JSObject excludeObject) {
            excludedKeys = new HashSet<>();
            for (PropertyKey key : excludeObject.ownPropertyKeys()) {
                JSValue excludedValue = excludeObject.get(context, key);
                excludedKeys.add(PropertyKey.fromValue(context, excludedValue));
            }
        } else if (excludeListValue != null && !excludeListValue.isNullOrUndefined()) {
            excludedKeys = new HashSet<>();
            excludedKeys.add(PropertyKey.fromValue(context, excludeListValue));
        }

        for (PropertyKey key : sourceObject.ownPropertyKeys()) {
            PropertyDescriptor descriptor = sourceObject.getOwnPropertyDescriptor(key);
            if (descriptor == null || !descriptor.isEnumerable() || (excludedKeys != null && excludedKeys.contains(key))) {
                continue;
            }
            JSValue propertyValue = sourceObject.get(context, key);
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                context.clearPendingException();
                return;
            }
            targetObject.set(context, key, propertyValue);
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                context.clearPendingException();
                return;
            }
        }
    }

    JSValue createArgumentsObject(StackFrame frame, JSFunction function, boolean mapped) {
        JSArguments cachedArgumentsObject = frame.getArgumentsObject(mapped);
        if (cachedArgumentsObject != null) {
            return cachedArgumentsObject;
        }

        JSValue[] args = frame.getArguments();
        JSArguments argumentsObject;
        if (mapped && function != null) {
            int formalArgCount = function.getLength();
            int mappedCount = Math.min(args.length, formalArgCount);
            VarRef[] mappedVarRefs = mappedCount > 0 ? new VarRef[args.length] : null;
            for (int i = 0; i < mappedCount; i++) {
                mappedVarRefs[i] = frame.getOrCreateLocalVarRef(i);
            }
            argumentsObject = new JSArguments(context, args, false, function, mappedVarRefs);
        } else {
            boolean isStrict = function instanceof JSBytecodeFunction bytecodeFunction && bytecodeFunction.isStrict();
            argumentsObject = new JSArguments(context, args, isStrict, isStrict ? null : function);
        }
        context.transferPrototype(argumentsObject, JSObject.NAME);
        frame.setArgumentsObject(mapped, argumentsObject);
        return argumentsObject;
    }

    ExecutionContext createExecutionContext(
            JSBytecodeFunction function,
            StackFrame frame,
            StackFrame previousFrame,
            int frameStackBase,
            int restoreStackTop,
            boolean savedStrictMode,
            JSGeneratorState generatorStateForExecution,
            boolean resumeGeneratorExecution) {
        ExecutionContext executionContext = new ExecutionContext(
                this,
                function.getBytecode(),
                frame,
                previousFrame,
                frameStackBase,
                restoreStackTop,
                savedStrictMode);
        if (resumeGeneratorExecution) {
            valueStack.stackTop = frameStackBase;
            JSStackValue[] suspendedStackValues = generatorStateForExecution.getSuspendedStackValues();
            if (suspendedStackValues != null && suspendedStackValues.length > 0) {
                System.arraycopy(suspendedStackValues, 0, executionContext.stack, frameStackBase, suspendedStackValues.length);
            }
            executionContext.sp = frameStackBase + (suspendedStackValues == null ? 0 : suspendedStackValues.length);
            JSGeneratorState.ResumeRecord pendingResumeRecord = generatorStateForExecution.consumePendingResumeRecord();
            if (pendingResumeRecord != null) {
                if (pendingResumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
                    pendingException = pendingResumeRecord.value();
                    context.setPendingException(pendingResumeRecord.value());
                } else if (pendingResumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
                    generatorForceReturn = true;
                    generatorReturnValue = pendingResumeRecord.value();
                    pendingException = pendingResumeRecord.value();
                    context.setPendingException(pendingResumeRecord.value());
                } else {
                    executionContext.stack[executionContext.sp++] = pendingResumeRecord.value();
                }
            }
            executionContext.pc = generatorStateForExecution.getSuspendedProgramCounter();
        } else {
            executionContext.sp = valueStack.stackTop;
            executionContext.pc = 0;
        }
        return executionContext;
    }

    JSObject createReferenceObject(Opcode makeRefOpcode, int refIndex, String atomName) {
        StackFrame capturedFrame = currentFrame;
        JSObject referenceObject = new JSObject();
        PropertyKey key = PropertyKey.fromString(atomName);

        JSNativeFunction getter = new JSNativeFunction(
                "get " + atomName,
                0,
                (ctx, thisArg, args) -> readReferenceValue(capturedFrame, makeRefOpcode, refIndex),
                false);
        JSNativeFunction setter = new JSNativeFunction(
                "set " + atomName,
                1,
                (ctx, thisArg, args) -> {
                    JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                    writeReferenceValue(capturedFrame, makeRefOpcode, refIndex, value);
                    return JSUndefined.INSTANCE;
                },
                false);

        referenceObject.defineProperty(
                key,
                PropertyDescriptor.accessorDescriptor(getter, setter, PropertyDescriptor.AccessorState.All));
        return referenceObject;
    }

    /**
     * Create special runtime objects based on object type.
     * Based on QuickJS OP_SPECIAL_OBJECT opcode (quickjs.c).
     *
     * @param objectType   Type identifier (0=arguments, 1=mapped_arguments, 2=this_func, etc.)
     * @param currentFrame Current stack frame for context
     * @return The created special object
     */
    JSValue createSpecialObject(int objectType, StackFrame currentFrame) {
        switch (objectType) {
            case 0: // SPECIAL_OBJECT_ARGUMENTS
                // For arrow functions, walk up the call stack to find parent non-arrow function's arguments
                // Following QuickJS: arrow functions inherit arguments from enclosing scope
                StackFrame targetFrame = currentFrame;
                JSFunction targetFunc = targetFrame.getFunction();

                // Walk up call stack while we're in arrow functions
                while (targetFunc instanceof JSBytecodeFunction bytecodeFunc && bytecodeFunc.isArrow()) {
                    targetFrame = targetFrame.getCaller();
                    if (targetFrame == null) {
                        // No parent frame, arguments is undefined (shouldn't happen in valid code)
                        return JSUndefined.INSTANCE;
                    }
                    targetFunc = targetFrame.getFunction();
                }
                return createArgumentsObject(targetFrame, targetFunc, shouldUseMappedArguments(targetFunc));

            case 1: // SPECIAL_OBJECT_MAPPED_ARGUMENTS
                // Legacy mapped arguments (shares with function parameters)
                JSFunction mappedFunc = currentFrame.getFunction();
                boolean canMap = mappedFunc != null && !(
                        mappedFunc instanceof JSBytecodeFunction bytecodeFunction
                                && bytecodeFunction.isStrict());
                return createArgumentsObject(currentFrame, mappedFunc, canMap);

            case 2: // SPECIAL_OBJECT_THIS_FUNC
                // Return the currently executing function
                return currentFrame.getFunction();

            case 3: // SPECIAL_OBJECT_NEW_TARGET
                // Return new.target.
                // Propagated from constructor invocation paths (new / Reflect.construct).
                return currentFrame.getNewTarget();

            case 4: // SPECIAL_OBJECT_HOME_OBJECT
                // Return the home object for super property access
                JSObject homeObject = currentFrame.getFunction().getHomeObject();
                return homeObject != null ? homeObject : JSUndefined.INSTANCE;

            case 5: // SPECIAL_OBJECT_VAR_OBJECT
                // Return the variable object (for with statement)
                // For now return undefined
                return JSUndefined.INSTANCE;

            case 6: // SPECIAL_OBJECT_IMPORT_META
                // Return import.meta object for ES6 modules
                // For now return undefined - needs module support
                return JSUndefined.INSTANCE;

            default:
                throw new JSVirtualMachineException("Unknown special object type: " + objectType);
        }
    }

    Opcode decodeOpcodeForExecute(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        Opcode opcode = executionContext.decodedOpcodes[pc];
        int rebase = executionContext.opcodeRebaseOffsets[pc];
        if (opcode == null) {
            int opcodeValue = executionContext.instructions[pc] & 0xFF;
            opcode = Opcode.fromInt(opcodeValue);
            if (opcode == Opcode.INVALID && pc + 1 < executionContext.instructions.length) {
                int extendedOpcodeValue = 0x100 + (executionContext.instructions[pc + 1] & 0xFF);
                Opcode extendedOpcode = Opcode.fromInt(extendedOpcodeValue);
                if (extendedOpcode != Opcode.INVALID) {
                    opcode = extendedOpcode;
                    rebase = 1;
                }
            }
        }
        if (rebase != 0) {
            executionContext.pc = pc + rebase;
        }
        return opcode;
    }

    void ensureConstantObjectPrototype(JSObject object) {
        if (!initializedConstantObjects.add(object)) {
            return;
        }

        if (object instanceof JSArray) {
            context.transferPrototype(object, JSArray.NAME);
        } else if (object instanceof JSRegExp) {
            context.transferPrototype(object, JSRegExp.NAME);
        }

        // Template objects may contain nested constant objects (e.g. template.raw array).
        for (PropertyKey key : object.getOwnPropertyKeys()) {
            PropertyDescriptor descriptor = object.getOwnPropertyDescriptor(key);
            if (descriptor == null || !descriptor.hasValue()) {
                continue;
            }
            JSValue value = descriptor.getValue();
            if (value instanceof JSObject nestedObject) {
                ensureConstantObjectPrototype(nestedObject);
            }
        }
    }

    /**
     * Execute a bytecode function.
     */
    public JSValue execute(JSBytecodeFunction function, JSValue thisArg, JSValue[] args) {
        return execute(function, thisArg, args, JSUndefined.INSTANCE);
    }

    public JSValue execute(JSBytecodeFunction function, JSValue thisArg, JSValue[] args, JSValue newTarget) {
        JSGeneratorState generatorStateForExecution = activeGeneratorState;
        boolean resumeGeneratorExecution =
                generatorStateForExecution != null
                        && generatorStateForExecution.getFunction() == function
                        && generatorStateForExecution.hasSuspendedExecutionState()
                        && generatorStateForExecution.hasPendingResumeRecord();
        // Save the current caller stack position so function exit can restore it.
        int callerStackTop = valueStack.getStackTop();
        // Always use callerStackTop as the frame's operand stack base.
        // For resumed generators, the suspended stack values are relative and
        // will be correctly placed at the current caller position.  Using the
        // original suspended stackBase would write into the caller's stack
        // region when the generator is resumed at a different call depth.
        int frameStackBase = callerStackTop;
        int restoreStackTop = callerStackTop;

        // Save and set strict mode based on function
        // Following QuickJS: each function has its own strict mode flag
        boolean savedStrictMode = context.isStrictMode();
        if (function.isStrict()) {
            context.enterStrictMode();
        } else {
            context.exitStrictMode();
        }

        // Create or restore stack frame
        StackFrame frame = resumeGeneratorExecution
                ? generatorStateForExecution.getSuspendedFrame()
                : new StackFrame(function, thisArg, args, currentFrame, newTarget, callerStackTop);
        StackFrame previousFrame = currentFrame;
        currentFrame = frame;

        try {
            ExecutionContext executionContext = createExecutionContext(
                    function,
                    frame,
                    previousFrame,
                    frameStackBase,
                    restoreStackTop,
                    savedStrictMode,
                    generatorStateForExecution,
                    resumeGeneratorExecution);
            int sp = executionContext.sp;
            int pc = executionContext.pc;

            // Main execution loop
            while (true) {
                // Sync local sp to valueStack at top of each iteration.
                // This ensures cold opcodes (which use valueStack directly) see the correct stackTop.
                valueStack.stackTop = sp;

                executionContext.sp = sp;
                executionContext.pc = pc;
                PendingExceptionAction pendingExceptionAction = handlePendingExceptionForExecute(executionContext);
                if (pendingExceptionAction == PendingExceptionAction.RETURN) {
                    return executionContext.returnValue;
                }
                if (pendingExceptionAction == PendingExceptionAction.CONTINUE) {
                    sp = executionContext.sp;
                    pc = executionContext.pc;
                    continue;
                }

                sp = executionContext.sp;
                pc = executionContext.pc;
                checkExecutionInterruptForExecute();
                executionContext.pc = pc;
                executionContext.opcodeRequestedReturn = false;

                Opcode op = decodeOpcodeForExecute(executionContext);
                op.getHandler().call(op, executionContext);
                if (executionContext.opcodeRequestedReturn) {
                    return executionContext.returnValue;
                }
                sp = executionContext.sp;
                pc = executionContext.pc;
            }
        } catch (JSVirtualMachineException e) {
            // Restore stack and strict mode on exception
            restoreExecuteFailureState(restoreStackTop, previousFrame, savedStrictMode);
            throw e;
        } catch (JSException e) {
            // Preserve thrown JS values so callers can keep the original error type and realm.
            restoreExecuteFailureState(restoreStackTop, previousFrame, savedStrictMode);
            JSValue errorValue = e.getErrorValue();
            if (errorValue instanceof JSError jsError) {
                throw new JSVirtualMachineException(jsError);
            }
            throw new JSVirtualMachineException(e.getMessage(), errorValue);
        } catch (Exception e) {
            // Restore stack and strict mode on exception
            restoreExecuteFailureState(restoreStackTop, previousFrame, savedStrictMode);
            throw new JSVirtualMachineException("VM error: " + e.getMessage(), e);
        }
    }

    public JSValue executeAsyncFunction(JSGeneratorState state, JSContext context) {
        yieldResult = null;
        awaitSuspensionPromise = null;
        JSGeneratorState previousActiveGeneratorState = activeGeneratorState;
        boolean previousAwaitSuspensionEnabled = awaitSuspensionEnabled;
        activeGeneratorState = state;
        awaitSuspensionEnabled = true;
        try {
            return execute(state.getFunction(), state.getThisArg(), state.getArgs());
        } finally {
            activeGeneratorState = previousActiveGeneratorState;
            awaitSuspensionEnabled = previousAwaitSuspensionEnabled;
        }
    }

    /**
     * Execute a generator function with state management.
     * Resumes from saved state if generator was previously yielded.
     */
    public JSValue executeGenerator(JSGeneratorState state, JSContext context) {
        JSBytecodeFunction function = state.getFunction();
        JSValue thisArg = state.getThisArg();
        JSValue[] args = state.getArgs();

        // Clear any previous yield result
        yieldResult = null;
        state.setAwaitSuspended(false);

        boolean useSuspendedExecutionState =
                state.hasSuspendedExecutionState()
                        && state.hasPendingResumeRecord();
        if (useSuspendedExecutionState) {
            yieldSkipCount = 0;
            generatorResumeRecords = List.of();
            generatorResumeIndex = 0;
        } else {
            // Set yield skip count - we'll skip this many yields to resume from the right place
            // This is a workaround since we're not saving/restoring PC
            yieldSkipCount = state.getYieldCount();
            generatorResumeRecords = state.getResumeRecords();
            generatorResumeIndex = 0;
        }

        JSGeneratorState previousActiveGeneratorState = activeGeneratorState;
        activeGeneratorState = state;
        boolean previousAwaitSuspensionEnabled = awaitSuspensionEnabled;
        boolean previousGeneratorForceReturn = generatorForceReturn;
        JSValue previousGeneratorReturnValue = generatorReturnValue;
        // Don't reset generatorForceReturn here - it may be set by the pending resume record
        // handling in execute() for suspended state mode
        if (function.isAsync()) {
            awaitSuspensionEnabled = true;
        }
        JSValue result;
        try {
            // Execute (or resume) the generator
            result = execute(function, thisArg, args);
        } finally {
            awaitSuspensionEnabled = previousAwaitSuspensionEnabled;
            activeGeneratorState = previousActiveGeneratorState;
            generatorForceReturn = previousGeneratorForceReturn;
            generatorReturnValue = previousGeneratorReturnValue;
        }

        // Check if generator yielded
        if (yieldResult != null) {
            // Generator yielded - increment count and update state
            state.incrementYieldCount();
            state.setState(JSGeneratorState.State.SUSPENDED_YIELD);
            // Save yield result per-generator so each generator tracks its own yield* state
            state.setLastYieldResult(yieldResult);
            return yieldResult.value();
        } else if (awaitSuspensionPromise != null) {
            state.setAwaitSuspended(true);
            state.setState(JSGeneratorState.State.SUSPENDED_YIELD);
            return JSUndefined.INSTANCE;
        } else {
            // Generator completed (returned)
            state.setLastYieldResult(null);
            state.setCompleted(true);
            return result;
        }
    }

    void finalizeExecuteReturn(ExecutionContext executionContext) {
        restoreExecuteCallerState(
                executionContext.restoreStackTop,
                executionContext.previousFrame,
                executionContext.savedStrictMode);
    }

    JSValue getArgumentValue(int index) {
        JSValue[] arguments = currentFrame.getArguments();
        if (index >= 0 && index < arguments.length) {
            JSValue value = arguments[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
    }

    JSString getComputedNameString(JSValue keyValue) {
        if (keyValue instanceof JSSymbol symbol) {
            String description = symbol.getDescription();
            return new JSString(description == null || description.isEmpty() ? "[]" : "[" + description + "]");
        }
        PropertyKey key = PropertyKey.fromValue(context, keyValue);
        if (key.isSymbol()) {
            JSSymbol symbol = key.asSymbol();
            String description = symbol != null ? symbol.getDescription() : null;
            return new JSString(description == null || description.isEmpty() ? "[]" : "[" + description + "]");
        }
        return new JSString(key.toPropertyString());
    }

    public StackFrame getCurrentFrame() {
        return currentFrame;
    }

    /**
     * Get the last yield result from generator execution.
     * Used to check if the yield was a yield* (delegation).
     */
    public YieldResult getLastYieldResult() {
        return yieldResult;
    }

    JSValue getLocalValue(int index) {
        JSValue[] locals = currentFrame.getLocals();
        if (index >= 0 && index < locals.length) {
            JSValue value = locals[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
    }

    JSValue incrementValue(JSValue value, int delta) {
        JSValue numeric = toNumericValue(value);
        if (numeric instanceof JSBigInt bigInt) {
            return new JSBigInt(bigInt.value().add(delta >= 0 ? BIGINT_ONE : BIGINT_NEGATIVE_ONE));
        }
        return JSNumber.of(((JSNumber) numeric).value() + delta);
    }

    void initializeConstantValueIfNeeded(JSValue constantValue) {
        if (constantValue instanceof JSFunction functionValue) {
            functionValue.initializePrototypeChain(context);
        } else if (constantValue instanceof JSObject objectValue) {
            ensureConstantObjectPrototype(objectValue);
        }
    }

    boolean isBranchTruthy(JSValue conditionValue) {
        if (conditionValue instanceof JSBoolean booleanValue) {
            return booleanValue.value();
        }
        if (conditionValue instanceof JSNumber numberValue) {
            double d = numberValue.value();
            return d != 0.0 && !Double.isNaN(d);
        }
        return JSTypeConversions.toBoolean(conditionValue) == JSBoolean.TRUE;
    }

    boolean isUninitialized(JSValue value) {
        return value == UNINITIALIZED_MARKER;
    }

    NumericPair numericPair(JSValue left, JSValue right) {
        JSValue leftNumeric = toNumericValue(left);
        JSValue rightNumeric = toNumericValue(right);
        boolean leftIsBigInt = leftNumeric instanceof JSBigInt;
        boolean rightIsBigInt = rightNumeric instanceof JSBigInt;
        if (leftIsBigInt != rightIsBigInt) {
            throwMixedBigIntTypeError();
            return null;
        }
        return new NumericPair(leftNumeric, rightNumeric, leftIsBigInt);
    }

    boolean ordinaryHasInstance(JSValue constructorValue, JSValue objectValue) {
        if (constructorValue instanceof JSBoundFunction boundFunction) {
            return ordinaryHasInstance(boundFunction.getTarget(), objectValue);
        }
        if (!(objectValue instanceof JSObject object)) {
            return false;
        }
        if (!(constructorValue instanceof JSObject constructorObject)) {
            return false;
        }

        JSValue prototypeValue = constructorObject.get(context, PropertyKey.PROTOTYPE);
        if (context.hasPendingException()) {
            JSValue pendingException = context.getPendingException();
            if (pendingException instanceof JSError jsError) {
                throw new JSVirtualMachineException(jsError);
            }
            throw new JSVirtualMachineException("instanceof check failed");
        }
        if (!(prototypeValue instanceof JSObject constructorPrototype)) {
            throw new JSVirtualMachineException(context.throwTypeError("Function has non-object prototype in instanceof check"));
        }

        JSObject currentPrototype = object.getPrototype();
        while (currentPrototype != null) {
            if (currentPrototype == constructorPrototype) {
                return true;
            }
            currentPrototype = currentPrototype.getPrototype();
        }
        return false;
    }

    /**
     * Invoke proxy apply trap when calling a proxy as a function.
     * Based on QuickJS js_proxy_call (quickjs.c:50338).
     *
     * @param proxy   The proxy being called
     * @param thisArg The 'this' value for the call
     * @param args    The arguments
     * @return The result of the call
     */
    JSValue proxyApply(JSProxy proxy, JSValue thisArg, JSValue[] args) {
        if (proxy.isRevoked()) {
            throw new JSException(context.throwTypeError("Cannot perform 'apply' on a proxy that has been revoked"));
        }

        JSValue target = proxy.getTarget();
        if (!JSTypeChecking.isFunction(target)) {
            throw new JSException(context.throwTypeError("proxy is not a function"));
        }

        JSValue applyTrap = proxy.getHandler().get("apply");
        if (applyTrap instanceof JSNull) {
            applyTrap = JSUndefined.INSTANCE;
        }

        if (applyTrap == JSUndefined.INSTANCE || applyTrap == null) {
            if (target instanceof JSProxy targetProxy) {
                return proxyApply(targetProxy, thisArg, args);
            }
            if (target instanceof JSNativeFunction nativeFunc) {
                return nativeFunc.call(context, thisArg, args);
            }
            if (target instanceof JSBytecodeFunction bytecodeFunc) {
                return execute(bytecodeFunc, thisArg, args);
            }
            if (target instanceof JSFunction targetFunc) {
                return targetFunc.call(context, thisArg, args);
            }
            return JSUndefined.INSTANCE;
        }

        if (!(applyTrap instanceof JSFunction applyFunc)) {
            throw new JSException(context.throwTypeError("apply trap is not a function"));
        }

        JSArray argArray = context.createJSArray(0, args.length);
        for (JSValue arg : args) {
            argArray.push(arg);
        }

        JSValue[] trapArgs = new JSValue[]{
                proxy.getTarget(),
                thisArg,
                argArray
        };
        return applyFunc.call(context, proxy.getHandler(), trapArgs);
    }

    /**
     * Invoke proxy construct trap when calling a proxy with 'new'.
     * Based on QuickJS js_proxy_call_constructor (quickjs.c:50304).
     *
     * @param proxy The proxy being constructed
     * @param args  The arguments
     * @return The constructed object
     */
    JSValue proxyConstruct(JSProxy proxy, JSValue[] args, JSValue newTarget) {
        if (proxy.isRevoked()) {
            throw new JSException(context.throwTypeError("Cannot perform 'construct' on a proxy that has been revoked"));
        }

        JSValue target = proxy.getTarget();
        if (!JSTypeChecking.isConstructor(target)) {
            throw new JSException(context.throwTypeError("proxy is not a constructor"));
        }

        JSValue constructTrap = proxy.getHandler().get("construct");
        if (constructTrap instanceof JSNull) {
            constructTrap = JSUndefined.INSTANCE;
        }

        if (constructTrap == JSUndefined.INSTANCE || constructTrap == null) {
            // ES2024 10.5.13 step 7: If trap is undefined, return ? Construct(target, args, newTarget)
            if (target instanceof JSProxy targetProxy) {
                return proxyConstruct(targetProxy, args, newTarget);
            }
            if (target instanceof JSFunction targetFunc) {
                return constructFunction(targetFunc, args, newTarget);
            }
            throw new JSException(context.throwTypeError("proxy target is not a constructor"));
        }

        if (!(constructTrap instanceof JSFunction constructFunc)) {
            throw new JSException(context.throwTypeError("construct trap is not a function"));
        }

        JSArray argArray = context.createJSArray(0, args.length);
        for (JSValue arg : args) {
            argArray.push(arg);
        }

        JSValue[] trapArgs = new JSValue[]{
                target,
                argArray,
                newTarget
        };

        JSValue result = constructFunc.call(context, proxy.getHandler(), trapArgs);

        if (!(result instanceof JSObject)) {
            throw new JSException(context.throwTypeError(
                    "'construct' on proxy: trap returned non-object ('" +
                            JSTypeConversions.toString(context, result) +
                            "')"));
        }

        return result;
    }

    JSValue readReferenceValue(StackFrame frame, Opcode makeRefOpcode, int refIndex) {
        return switch (makeRefOpcode) {
            case MAKE_LOC_REF -> (refIndex >= 0 && refIndex < frame.getLocals().length)
                    ? frame.getLocals()[refIndex]
                    : JSUndefined.INSTANCE;
            case MAKE_ARG_REF -> (refIndex >= 0 && refIndex < frame.getArguments().length)
                    ? frame.getArguments()[refIndex]
                    : JSUndefined.INSTANCE;
            case MAKE_VAR_REF_REF -> frame.getVarRef(refIndex);
            default -> JSUndefined.INSTANCE;
        };
    }

    JSVirtualMachineException referenceErrorNotDefined(PropertyKey key) {
        String name = key != null ? key.toPropertyString() : "variable";
        return new JSVirtualMachineException(context.throwReferenceError(name + " is not defined"));
    }

    void requestOpcodeReturnFromExecute(ExecutionContext executionContext, JSValue returnValue) {
        restoreExecuteCallerState(
                executionContext.restoreStackTop,
                executionContext.previousFrame,
                executionContext.savedStrictMode);
        executionContext.returnValue = returnValue;
        executionContext.opcodeRequestedReturn = true;
    }

    void resetPropertyAccessTracking() {
        if (trackPropertyAccess) {
            this.propertyAccessChain.setLength(0);
        }
        this.propertyAccessLock = false;
    }

    void restoreExecuteCallerState(int restoreStackTop, StackFrame previousFrame, boolean savedStrictMode) {
        valueStack.stackTop = restoreStackTop;
        currentFrame = previousFrame;
        if (savedStrictMode) {
            context.enterStrictMode();
        } else {
            context.exitStrictMode();
        }
    }

    void restoreExecuteFailureState(int restoreStackTop, StackFrame previousFrame, boolean savedStrictMode) {
        valueStack.setStackTop(restoreStackTop);
        currentFrame = previousFrame;
        resetPropertyAccessTracking();
        if (savedStrictMode) {
            context.enterStrictMode();
        } else {
            context.exitStrictMode();
        }
    }

    /**
     * Safely convert an exception object to a string without calling JavaScript methods.
     * This is used when already in an exception state to avoid cascading failures.
     */
    String safeExceptionToString(JSContext context, JSValue exception) {
        if (exception == null) {
            return "null";
        }

        // For primitive values, use direct conversion
        if (!(exception instanceof JSObject exceptionObj)) {
            return exception.toString();
        }

        // Try to get the message property directly (without calling getters or toString)
        // This avoids calling JavaScript code which might fail in exception state
        try {
            JSValue messageValue = exceptionObj.get(null, PropertyKey.MESSAGE);
            if (messageValue instanceof JSString msgStr) {
                return msgStr.value();
            } else if (messageValue != null && !(messageValue instanceof JSUndefined)) {
                return messageValue.toString();
            }
        } catch (Exception e) {
            // Ignore errors when getting message
        }

        // Try to get the name property
        try {
            JSValue nameValue = exceptionObj.get(null, PropertyKey.NAME);
            if (nameValue instanceof JSString nameStr) {
                return nameStr.value();
            }
        } catch (Exception e) {
            // Ignore errors when getting name
        }

        // Fall back to Java toString
        return exceptionObj.toString();
    }

    void saveActiveGeneratorSuspendedExecutionState(
            StackFrame frame,
            int programCounter,
            JSStackValue[] stack,
            int stackTop,
            int stackBase) {
        if (activeGeneratorState == null) {
            return;
        }
        int stackLength = Math.max(0, stackTop - stackBase);
        JSStackValue[] suspendedStackValues = new JSStackValue[stackLength];
        if (stackLength > 0) {
            System.arraycopy(stack, stackBase, suspendedStackValues, 0, stackLength);
        }
        activeGeneratorState.saveSuspendedExecutionState(frame, programCounter, suspendedStackValues);
    }

    void setArgumentValue(int index, JSValue value) {
        JSValue[] arguments = currentFrame.getArguments();
        if (index >= 0 && index < arguments.length) {
            arguments[index] = value;
        }
        // Keep local mirror in sync for argument slots copied into locals.
        setLocalValue(index, value);
    }

    /**
     * Set an execution deadline for the VM.
     * After this time, the VM will throw an interrupt exception.
     * Set to 0 to clear the deadline.
     */
    public void setExecutionDeadline(long deadlineMs) {
        this.executionDeadline = deadlineMs;
        if (deadlineMs == 0) {
            this.executionDeadlineNanos = 0;
        } else {
            long nowMs = System.currentTimeMillis();
            long remainingMs = Math.max(0, deadlineMs - nowMs);
            this.executionDeadlineNanos = System.nanoTime() + remainingMs * 1_000_000L;
        }
        this.interruptCounter = INTERRUPT_CHECK_INTERVAL;
    }

    void setLocalValue(int index, JSValue value) {
        JSValue[] locals = currentFrame.getLocals();
        if (index >= 0 && index < locals.length) {
            locals[index] = value;
        }
    }

    void setObjectName(JSValue objectValue, JSString nameValue) {
        if (!(objectValue instanceof JSObject object)) {
            return;
        }
        object.defineProperty(PropertyKey.fromString("name"), nameValue, PropertyDescriptor.DataState.Configurable);
    }

    JSValue shiftBigInt(JSBigInt value, JSBigInt shiftCount, boolean leftShiftOperator) {
        BigInteger count = shiftCount.value();
        boolean countPositive = count.signum() >= 0;
        boolean performLeftShift = leftShiftOperator == countPositive;
        BigInteger magnitude = count.abs();
        if (magnitude.bitLength() > 31) {
            if (performLeftShift) {
                pendingException = context.throwRangeError("BigInt shift count is too large");
                return JSUndefined.INSTANCE;
            }
            return new JSBigInt(value.value().signum() < 0 ? BIGINT_NEGATIVE_ONE : BIGINT_ZERO);
        }
        int shift = magnitude.intValue();
        BigInteger result = performLeftShift
                ? value.value().shiftLeft(shift)
                : value.value().shiftRight(shift);
        return new JSBigInt(result);
    }

    boolean shouldUseMappedArguments(JSFunction function) {
        return function instanceof JSBytecodeFunction bytecodeFunction
                && !bytecodeFunction.isStrict()
                && !bytecodeFunction.isArrow()
                && !bytecodeFunction.hasParameterExpressions();
    }

    JSValue throwMixedBigIntTypeError() {
        pendingException = context.throwTypeError("Cannot mix BigInt and other types");
        return JSUndefined.INSTANCE;
    }

    void throwVariableUninitializedReferenceError() {
        throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
    }

    JSValue toNumericValue(JSValue value) {
        JSValue primitive = JSTypeConversions.toPrimitive(context, value, JSTypeConversions.PreferredType.NUMBER);
        capturePendingException();
        if (primitive instanceof JSBigInt || primitive instanceof JSNumber) {
            return primitive;
        }
        JSValue result = JSTypeConversions.toNumber(context, primitive);
        capturePendingException();
        return result;
    }

    /**
     * Convert a value to an object (auto-boxing for primitives).
     * Returns null for null and undefined.
     * Since JSFunction now extends JSObject, functions are already objects.
     */
    JSObject toObject(JSValue value) {
        // JSFunction extends JSObject, so this handles both objects and functions
        if (value instanceof JSObject jsObj) {
            return jsObj;
        }

        if (value instanceof JSNull || value instanceof JSUndefined) {
            return null;
        }

        JSObject global = context.getGlobalObject();

        // Auto-box primitives
        if (value instanceof JSString str) {
            // Get String.prototype from global object
            JSValue stringCtor = global.get(JSString.NAME);
            if (stringCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    // Create a temporary wrapper object with String.prototype
                    JSObject wrapper = new JSObject();
                    wrapper.setPrototype(protoObj);
                    // Store the primitive value
                    wrapper.setPrimitiveValue(str);
                    // Add length property as own property (shadows prototype's length)
                    // This is a data property with the actual string length
                    wrapper.defineProperty(PropertyKey.fromString("length"), JSNumber.of(str.value().length()), PropertyDescriptor.DataState.None);
                    return wrapper;
                }
            }
        }

        if (value instanceof JSNumber num) {
            // Get Number.prototype from global object
            JSValue numberCtor = global.get(JSNumberObject.NAME);
            if (numberCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSObject wrapper = new JSObject();
                    wrapper.setPrototype(protoObj);
                    wrapper.setPrimitiveValue(num);
                    return wrapper;
                }
            }
        }

        if (value instanceof JSBoolean bool) {
            // Get Boolean.prototype from global object
            JSValue booleanCtor = global.get(JSBoolean.NAME);
            if (booleanCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSObject wrapper = new JSObject();
                    wrapper.setPrototype(protoObj);
                    wrapper.setPrimitiveValue(bool);
                    return wrapper;
                }
            }
        }

        if (value instanceof JSBigInt bigInt) {
            // Get BigInt.prototype from global object
            JSValue bigIntCtor = global.get(JSBigInt.NAME);
            if (bigIntCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSBigIntObject wrapper = new JSBigIntObject(bigInt);
                    wrapper.setPrototype(protoObj);
                    return wrapper;
                }
            }
        }

        if (value instanceof JSSymbol sym) {
            // Get Symbol.prototype from global object
            JSValue symbolCtor = global.get(JSSymbol.NAME);
            if (symbolCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    // Create a Symbol object wrapper (not a generic JSObject)
                    JSSymbolObject wrapper = new JSSymbolObject(sym);
                    wrapper.setPrototype(protoObj);
                    return wrapper;
                }
            }
        }

        return null;
    }

    /**
     * Convert a runtime value to an ECMAScript property key value (string or symbol).
     */
    JSValue toPropertyKeyValue(JSValue rawKey) {
        PropertyKey key = PropertyKey.fromValue(context, rawKey);
        if (key.isSymbol()) {
            return key.asSymbol();
        }
        if (key.isIndex()) {
            return JSNumber.of(key.asIndex());
        }
        String keyString = key.asString();
        return new JSString(keyString != null ? keyString : key.toPropertyString());
    }

    void writeReferenceValue(StackFrame frame, Opcode makeRefOpcode, int refIndex, JSValue value) {
        switch (makeRefOpcode) {
            case MAKE_LOC_REF -> {
                if (refIndex >= 0 && refIndex < frame.getLocals().length) {
                    frame.getLocals()[refIndex] = value;
                }
            }
            case MAKE_ARG_REF -> {
                if (refIndex >= 0 && refIndex < frame.getArguments().length) {
                    frame.getArguments()[refIndex] = value;
                }
            }
            case MAKE_VAR_REF_REF -> frame.setVarRef(refIndex, value);
            default -> {
            }
        }
    }

    enum PendingExceptionAction {
        NONE,
        CONTINUE,
        RETURN
    }

    record NumericPair(JSValue left, JSValue right, boolean bigInt) {
    }

}
