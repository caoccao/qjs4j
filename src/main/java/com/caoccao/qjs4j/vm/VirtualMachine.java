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

import com.caoccao.qjs4j.builtins.BigIntConstructor;
import com.caoccao.qjs4j.builtins.SymbolConstructor;
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
    private static final BigInteger BIGINT_NEGATIVE_ONE = BigInteger.valueOf(-1);
    private static final BigInteger BIGINT_ONE = BigInteger.ONE;
    private static final BigInteger BIGINT_ZERO = BigInteger.ZERO;
    private static final JSValue[] EMPTY_ARGS = new JSValue[0];
    private static final int INTERRUPT_CHECK_INTERVAL = 0xFFFF; // Check every ~65K opcodes
    private static final JSObject UNINITIALIZED_MARKER = new JSObject();
    private final JSContext context;
    private final Set<JSObject> initializedConstantObjects;
    private final StringBuilder propertyAccessChain;  // Track last property access for better error messages
    private final boolean trackPropertyAccess;
    private final CallStack valueStack;
    private JSGeneratorState activeGeneratorState;
    private boolean awaitSuspensionEnabled;
    private JSPromise awaitSuspensionPromise;
    private StackFrame currentFrame;
    private long executionDeadline;  // 0 = no deadline
    private long executionDeadlineNanos; // 0 = no deadline
    private JSValue[] forOfTempValues;
    private boolean generatorForceReturn;  // When true, exception handler skips catch offsets, enters only finally
    private int generatorResumeIndex;
    private List<JSGeneratorState.ResumeRecord> generatorResumeRecords;
    private JSValue generatorReturnValue;  // The return value during generator force return
    private int interruptCounter;
    private JSValue lastConstructorThisArg;  // Saved from frame before return for derived constructor check
    private JSValue pendingException;
    private boolean propertyAccessLock;  // When true, don't update lastPropertyAccess (during argument evaluation)
    private YieldResult yieldResult;  // Set when generator yields
    private int yieldSkipCount;  // How many yields to skip (for resuming generators)

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

    /**
     * Create VarRef array from capture source info during FCLOSURE.
     * For LOCAL sources, creates/reuses a VarRef pointing to the parent's local slot.
     * For VAR_REF sources, shares the parent's existing VarRef.
     */
    private static VarRef[] createVarRefsFromCaptures(int[] captureInfos, StackFrame parentFrame) {
        VarRef[] varRefs = new VarRef[captureInfos.length];
        for (int i = 0; i < captureInfos.length; i++) {
            int info = captureInfos[i];
            if (info >= 0) {
                // LOCAL capture: create/reuse VarRef pointing to parent's local slot
                varRefs[i] = parentFrame.getOrCreateLocalVarRef(info);
            } else {
                // VAR_REF capture: share parent's existing VarRef
                int varRefIndex = -(info + 1);
                VarRef parentRef = parentFrame.getVarRefCell(varRefIndex);
                if (parentRef != null) {
                    varRefs[i] = parentRef;
                } else {
                    // Fallback: create standalone VarRef with current value
                    varRefs[i] = new VarRef(parentFrame.getVarRef(varRefIndex));
                }
            }
        }
        return varRefs;
    }

    private JSValue addValues(JSValue left, JSValue right) {
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

    private void appendPropertyAccessForArrayIndex(JSValue indexValue) {
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

    private JSValue[] buildApplyArguments(JSValue argsArrayValue, boolean allowNullOrUndefined) {
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

    private void capturePendingException() {
        if (pendingException == null && context.hasPendingException()) {
            pendingException = context.getPendingException();
        }
    }

    private void capturePendingExceptionFromVmOrContext(JSVirtualMachineException e) {
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
    private void captureVMException(JSVirtualMachineException e) {
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

    private void checkExecutionInterruptForExecute() {
        if (executionDeadline != 0 && --interruptCounter <= 0) {
            interruptCounter = INTERRUPT_CHECK_INTERVAL;
            if (System.nanoTime() >= executionDeadlineNanos) {
                throw new JSVirtualMachineException("execution timeout");
            }
        }
    }

    private void clearActiveGeneratorSuspendedExecutionState() {
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

    private JSValue constructFunction(JSFunction function, JSValue[] args, JSValue newTarget) {
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
            if (newTarget instanceof JSObject newTargetObject) {
                if (!context.transferPrototypeFromConstructor(thisObject, newTargetObject)) {
                    if (context.hasPendingException()) {
                        JSValue exception = context.getPendingException();
                        throw new JSVirtualMachineException(exception.toString(), exception);
                    }
                    // ES spec GetPrototypeFromConstructor step 4a: GetFunctionRealm(constructor)
                    // If newTarget is a revoked Proxy, throw TypeError
                    if (newTargetObject instanceof JSProxy proxy && proxy.isRevoked()) {
                        JSContext proxyContext = proxy.getProxyContext();
                        throw new JSVirtualMachineException(
                                proxyContext.throwTypeError("Cannot perform 'construct' on a proxy that has been revoked"));
                    }
                    context.transferPrototypeFromConstructor(thisObject, function);
                    if (context.hasPendingException()) {
                        JSValue exception = context.getPendingException();
                        throw new JSVirtualMachineException(exception.toString(), exception);
                    }
                }
            } else {
                context.transferPrototypeFromConstructor(thisObject, function);
                if (context.hasPendingException()) {
                    JSValue exception = context.getPendingException();
                    throw new JSVirtualMachineException(exception.toString(), exception);
                }
            }

            // Check if this is a derived constructor
            boolean isDerived = function instanceof JSBytecodeFunction bcFunc
                    && bcFunc.isDerivedConstructor();

            // For derived constructors, use JSUndefined as initial this
            // (this must be initialized by super() call)
            JSValue constructThis = isDerived ? JSUndefined.INSTANCE : thisObject;

            JSValue result;
            if (function instanceof JSNativeFunction nativeFunc) {
                JSValue savedNewTarget = context.getConstructorNewTarget();
                context.setConstructorNewTarget(newTarget);
                try {
                    result = nativeFunc.call(context, constructThis, args);
                } finally {
                    context.setConstructorNewTarget(savedNewTarget);
                }
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
                result = execute(bytecodeFunction, constructThis, args, newTarget);
            } else {
                result = JSUndefined.INSTANCE;
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
                JSValue finalThis = lastConstructorThisArg;
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
                JSValue proto = newTargetObject.get(context, PropertyKey.PROTOTYPE);
                if (context.hasPendingException()) {
                    throw new JSVirtualMachineException(context.getPendingException().toString(),
                            context.getPendingException());
                }
                if (proto instanceof JSObject protoObj) {
                    resolvedPrototype = protoObj;
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

    private void copyDataProperties(JSValue targetValue, JSValue sourceValue, JSValue excludeListValue) {
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

    private JSValue createArgumentsObject(StackFrame frame, JSFunction function, boolean mapped) {
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

    private ExecutionContext createExecutionContext(
            JSBytecodeFunction function,
            StackFrame frame,
            StackFrame previousFrame,
            int frameStackBase,
            int restoreStackTop,
            boolean savedStrictMode,
            JSGeneratorState generatorStateForExecution,
            boolean resumeGeneratorExecution) {
        ExecutionContext executionContext = new ExecutionContext(
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

    private JSObject createReferenceObject(Opcode makeRefOpcode, int refIndex, String atomName) {
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
    private JSValue createSpecialObject(int objectType, StackFrame currentFrame) {
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

    private Opcode decodeOpcodeForExecute(ExecutionContext executionContext) {
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

    private void dispatchOpcodeForExecute(ExecutionContext executionContext) {
        Opcode op = decodeOpcodeForExecute(executionContext);
        switch (op) {
            // ==================== Constants and Literals ====================
            case INVALID -> {
                handleInvalid(executionContext);
            }
            case PUSH_I32 -> {
                handlePushI32(executionContext);
            }
            case PUSH_BIGINT_I32 -> {
                handlePushBigintI32(executionContext);
            }
            case PUSH_MINUS1 -> {
                handlePushMinus1(executionContext);
            }
            case PUSH_0 -> {
                handlePush0(executionContext);
            }
            case PUSH_1 -> {
                handlePush1(executionContext);
            }
            case PUSH_2 -> {
                handlePush2(executionContext);
            }
            case PUSH_3 -> {
                handlePush3(executionContext);
            }
            case PUSH_4 -> {
                handlePush4(executionContext);
            }
            case PUSH_5 -> {
                handlePush5(executionContext);
            }
            case PUSH_6 -> {
                handlePush6(executionContext);
            }
            case PUSH_7 -> {
                handlePush7(executionContext);
            }
            case PUSH_I8 -> {
                handlePushI8(executionContext);
            }
            case PUSH_I16 -> {
                handlePushI16(executionContext);
            }
            case PUSH_CONST -> {
                handlePushConst(executionContext);
            }
            case PUSH_CONST8 -> {
                handlePushConst8(executionContext);
            }
            case PUSH_ATOM_VALUE -> {
                handlePushAtomValue(executionContext);
            }
            case PRIVATE_SYMBOL -> {
                handlePrivateSymbol(executionContext);
            }
            case FCLOSURE -> {
                handleFclosure(executionContext);
            }
            case FCLOSURE8 -> {
                handleFclosure8(executionContext);
            }
            case PUSH_EMPTY_STRING -> {
                handlePushEmptyString(executionContext);
            }
            case UNDEFINED -> {
                handleUndefined(executionContext);
            }
            case NULL -> {
                handleNull(executionContext);
            }
            case PUSH_THIS -> {
                handlePushThis(executionContext);
            }
            case PUSH_FALSE -> {
                handlePushFalse(executionContext);
            }
            case PUSH_TRUE -> {
                handlePushTrue(executionContext);
            }
            case SPECIAL_OBJECT -> {
                handleSpecialObject(executionContext);
            }
            case REST -> {
                handleRest(executionContext);
            }

            // ==================== Stack Manipulation ====================
            case DROP -> {
                handleDrop(executionContext);
            }
            case NIP -> {
                handleNip(executionContext);
            }
            case DUP -> {
                handleDup(executionContext);
            }
            case DUP1 -> {
                handleDup1(executionContext);
            }
            case DUP2 -> {
                handleDup2(executionContext);
            }
            case INSERT2 -> {
                handleInsert2(executionContext);
            }
            case INSERT3 -> {
                handleInsert3(executionContext);
            }
            case INSERT4 -> {
                handleInsert4(executionContext);
            }
            case SWAP -> {
                handleSwap(executionContext);
            }
            case ROT3L -> {
                handleRot3l(executionContext);
            }
            case ROT3R -> {
                handleRot3r(executionContext);
            }
            case SWAP2 -> {
                handleSwap2(executionContext);
            }

            // ==================== Arithmetic Operations ====================
            case ADD -> {
                handleAddOpcode(executionContext);
            }
            case SUB -> {
                handleSubOpcode(executionContext);
            }
            case MUL -> {
                handleMulOpcode(executionContext);
            }
            case DIV -> {
                handleDivOpcode(executionContext);
            }
            case MOD -> {
                handleModOpcode(executionContext);
            }
            case EXP, POW -> {
                handleExpOpcode(executionContext);
            }
            case PLUS -> {
                handlePlusOpcode(executionContext);
            }
            case NEG -> {
                handleNegOpcode(executionContext);
            }
            case INC -> {
                handleIncOpcode(executionContext);
            }
            case DEC -> {
                handleDecOpcode(executionContext);
            }
            case INC_LOC -> {
                handleIncLoc(executionContext);
            }
            case DEC_LOC -> {
                handleDecLoc(executionContext);
            }
            case ADD_LOC -> {
                handleAddLoc(executionContext);
            }
            case POST_INC -> {
                handlePostIncOpcode(executionContext);
            }
            case POST_DEC -> {
                handlePostDecOpcode(executionContext);
            }
            case PERM3 -> {
                handlePerm3(executionContext);
            }
            case PERM4 -> {
                handlePerm4(executionContext);
            }
            case PERM5 -> {
                handlePerm5(executionContext);
            }

            // ==================== Bitwise Operations ====================
            case SHL -> {
                handleShlOpcode(executionContext);
            }
            case SAR -> {
                handleSarOpcode(executionContext);
            }
            case SHR -> {
                handleShrOpcode(executionContext);
            }
            case AND -> {
                handleAndOpcode(executionContext);
            }
            case OR -> {
                handleOrOpcode(executionContext);
            }
            case XOR -> {
                handleXorOpcode(executionContext);
            }
            case NOT -> {
                handleNotOpcode(executionContext);
            }

            // ==================== Comparison Operations ====================
            case EQ -> {
                handleEqOpcode(executionContext);
            }
            case NEQ -> {
                handleNeqOpcode(executionContext);
            }
            case STRICT_EQ -> {
                handleStrictEqOpcode(executionContext);
            }
            case STRICT_NEQ -> {
                handleStrictNeqOpcode(executionContext);
            }
            case LT -> {
                handleLtOpcode(executionContext);
            }
            case LTE -> {
                handleLteOpcode(executionContext);
            }
            case GT -> {
                handleGtOpcode(executionContext);
            }
            case GTE -> {
                handleGteOpcode(executionContext);
            }
            case INSTANCEOF -> {
                handleInstanceofOpcode(executionContext);
            }
            case IN -> {
                handleInOpcode(executionContext);
            }
            case PRIVATE_IN -> {
                handlePrivateInOpcode(executionContext);
            }

            // ==================== Logical Operations ====================
            case LOGICAL_NOT, LNOT -> {
                handleLogicalNotOpcode(executionContext);
            }
            case LOGICAL_AND -> {
                handleLogicalAndOpcode(executionContext);
            }
            case LOGICAL_OR -> {
                handleLogicalOrOpcode(executionContext);
            }
            case NULLISH_COALESCE -> {
                handleNullishCoalesceOpcode(executionContext);
            }

            // ==================== Variable Access ====================
            case GET_VAR_UNDEF -> {
                handleGetVarUndef(executionContext);
            }
            case GET_REF_VALUE -> {
                handleGetRefValue(executionContext);
            }
            case GET_VAR -> {
                handleGetVar(executionContext);
            }
            case PUT_VAR_INIT -> {
                handlePutVarInit(executionContext);
            }
            case PUT_REF_VALUE -> {
                handlePutRefValue(executionContext);
            }
            case PUT_VAR -> {
                handlePutVar(executionContext);
            }
            case SET_VAR -> {
                handleSetVar(executionContext);
            }
            case DELETE_VAR -> {
                handleDeleteVar(executionContext);
            }
            case GET_LOCAL, GET_LOC -> {
                handleGetLoc(executionContext);
            }
            case GET_LOC8 -> {
                handleGetLoc8(executionContext);
            }
            case GET_LOC0 -> {
                handleGetLoc0(executionContext);
            }
            case GET_LOC1 -> {
                handleGetLoc1(executionContext);
            }
            case GET_LOC2 -> {
                handleGetLoc2(executionContext);
            }
            case GET_LOC3 -> {
                handleGetLoc3(executionContext);
            }
            case PUT_LOCAL, PUT_LOC -> {
                handlePutLoc(executionContext);
            }
            case PUT_LOC8 -> {
                handlePutLoc8(executionContext);
            }
            case PUT_LOC0 -> {
                handlePutLoc0(executionContext);
            }
            case PUT_LOC1 -> {
                handlePutLoc1(executionContext);
            }
            case PUT_LOC2 -> {
                handlePutLoc2(executionContext);
            }
            case PUT_LOC3 -> {
                handlePutLoc3(executionContext);
            }
            case SET_LOCAL, SET_LOC -> {
                handleSetLoc(executionContext);
            }
            case SET_LOC8 -> {
                handleSetLoc8(executionContext);
            }
            case SET_LOC0 -> {
                handleSetLoc0(executionContext);
            }
            case SET_LOC1 -> {
                handleSetLoc1(executionContext);
            }
            case SET_LOC2 -> {
                handleSetLoc2(executionContext);
            }
            case SET_LOC3 -> {
                handleSetLoc3(executionContext);
            }
            case GET_ARG -> {
                handleGetArg(executionContext);
            }
            case GET_ARG0, GET_ARG1, GET_ARG2, GET_ARG3 -> {
                handleGetArgShort(executionContext, op);
            }
            case PUT_ARG -> {
                handlePutArg(executionContext);
            }
            case PUT_ARG0, PUT_ARG1, PUT_ARG2, PUT_ARG3 -> {
                handlePutArgShort(executionContext, op);
            }
            case SET_ARG -> {
                handleSetArg(executionContext);
            }
            case SET_ARG0, SET_ARG1, SET_ARG2, SET_ARG3 -> {
                handleSetArgShort(executionContext, op);
            }
            case GET_VAR_REF -> {
                handleGetVarRef(executionContext);
            }
            case GET_VAR_REF0, GET_VAR_REF1, GET_VAR_REF2, GET_VAR_REF3 -> {
                handleGetVarRefShort(executionContext, op);
            }
            case PUT_VAR_REF -> {
                handlePutVarRef(executionContext);
            }
            case PUT_VAR_REF0, PUT_VAR_REF1, PUT_VAR_REF2, PUT_VAR_REF3 -> {
                handlePutVarRefShort(executionContext, op);
            }
            case SET_VAR_REF -> {
                handleSetVarRef(executionContext);
            }
            case SET_VAR_REF0, SET_VAR_REF1, SET_VAR_REF2, SET_VAR_REF3 -> {
                handleSetVarRefShort(executionContext, op);
            }
            case CLOSE_LOC -> {
                handleCloseLoc(executionContext);
            }
            case SET_LOC_UNINITIALIZED -> {
                handleSetLocUninitialized(executionContext);
            }
            case GET_LOC_CHECK, GET_LOC_CHECKTHIS -> {
                handleGetLocCheck(executionContext);
            }
            case PUT_LOC_CHECK -> {
                handlePutLocCheck(executionContext);
            }
            case SET_LOC_CHECK -> {
                handleSetLocCheck(executionContext);
            }
            case PUT_LOC_CHECK_INIT -> {
                handlePutLocCheckInit(executionContext);
            }
            case GET_VAR_REF_CHECK -> {
                handleGetVarRefCheck(executionContext);
            }
            case PUT_VAR_REF_CHECK -> {
                handlePutVarRefCheck(executionContext);
            }
            case PUT_VAR_REF_CHECK_INIT -> {
                handlePutVarRefCheckInit(executionContext);
            }
            case MAKE_LOC_REF, MAKE_ARG_REF, MAKE_VAR_REF_REF -> {
                handleMakeScopedRef(executionContext, op);
            }
            case MAKE_VAR_REF -> {
                handleMakeVarRef(executionContext);
            }

            // ==================== Property Access ====================
            case GET_FIELD -> {
                handleGetField(executionContext);
            }
            case GET_LENGTH -> {
                handleGetLength(executionContext);
            }
            case PUT_FIELD -> {
                handlePutField(executionContext);
            }
            case GET_ARRAY_EL -> {
                handleGetArrayEl(executionContext);
            }
            case GET_ARRAY_EL2 -> {
                handleGetArrayEl2(executionContext);
            }
            case GET_ARRAY_EL3 -> {
                handleGetArrayEl3(executionContext);
            }
            case PUT_ARRAY_EL -> {
                handlePutArrayEl(executionContext);
            }
            case GET_SUPER_VALUE -> {
                handleGetSuperValue(executionContext);
            }
            case PUT_SUPER_VALUE -> {
                handlePutSuperValue(executionContext);
            }
            case TO_PROPKEY -> {
                handleToPropKey(executionContext);
            }
            case TO_PROPKEY2 -> {
                handleToPropKey2(executionContext);
            }

            // ==================== Control Flow ====================
            case IF_FALSE -> {
                handleIfFalse(executionContext);
            }
            case IF_TRUE -> {
                handleIfTrue(executionContext);
            }
            case IF_TRUE8 -> {
                handleIfTrue8(executionContext);
            }
            case IF_FALSE8 -> {
                handleIfFalse8(executionContext);
            }
            case GOTO -> {
                handleGoto(executionContext);
            }
            case GOTO8 -> {
                handleGoto8(executionContext);
            }
            case GOTO16 -> {
                handleGoto16(executionContext);
            }
            case RETURN -> {
                handleReturnOpcode(executionContext);
            }
            case RETURN_UNDEF -> {
                handleReturnUndefOpcode(executionContext);
            }
            case RETURN_ASYNC -> {
                handleReturnAsyncOpcode(executionContext);
            }

            // ==================== Function Calls ====================
            case INIT_CTOR -> {
                handleInitCtorOpcode(executionContext);
            }
            case CALL -> {
                handleCallOpcode(executionContext);
            }
            case CALL0 -> {
                handleCall0(executionContext);
            }
            case CALL1 -> {
                handleCall1(executionContext);
            }
            case CALL2 -> {
                handleCall2(executionContext);
            }
            case CALL3 -> {
                handleCall3(executionContext);
            }
            case CALL_CONSTRUCTOR -> {
                handleCallConstructorOpcode(executionContext);
            }

            // ==================== Object/Array Creation ====================
            case OBJECT, OBJECT_NEW -> {
                handleObjectNew(executionContext);
            }
            case ARRAY_NEW -> {
                handleArrayNew(executionContext);
            }
            case ARRAY_FROM -> {
                handleArrayFrom(executionContext);
            }
            case APPLY -> {
                handleApply(executionContext);
            }
            case PUSH_ARRAY -> {
                handlePushArray(executionContext);
            }
            case APPEND -> {
                handleAppend(executionContext);
            }
            case DEFINE_ARRAY_EL -> {
                handleDefineArrayEl(executionContext);
            }
            case DEFINE_PROP -> {
                handleDefineProp(executionContext);
            }
            case SET_NAME -> {
                handleSetName(executionContext);
            }
            case SET_NAME_COMPUTED -> {
                handleSetNameComputed(executionContext);
            }
            case SET_PROTO -> {
                handleSetProto(executionContext);
            }
            case SET_HOME_OBJECT -> {
                handleSetHomeObject(executionContext);
            }
            case COPY_DATA_PROPERTIES -> {
                handleCopyDataProperties(executionContext);
            }
            case DEFINE_CLASS -> {
                handleDefineClass(executionContext);
            }
            case DEFINE_CLASS_COMPUTED -> {
                handleDefineClassComputed(executionContext);
            }
            case DEFINE_METHOD -> {
                handleDefineMethod(executionContext);
            }
            case DEFINE_METHOD_COMPUTED -> {
                handleDefineMethodComputed(executionContext);
            }
            case DEFINE_FIELD -> {
                handleDefineField(executionContext);
            }
            case DEFINE_PRIVATE_FIELD -> {
                handleDefinePrivateField(executionContext);
            }
            case GET_PRIVATE_FIELD -> {
                handleGetPrivateField(executionContext);
            }
            case PUT_PRIVATE_FIELD -> {
                handlePutPrivateField(executionContext);
            }

            // ==================== Exception Handling ====================
            case THROW -> {
                handleThrowOpcode(executionContext);
            }
            case THROW_ERROR -> {
                handleThrowErrorOpcode(executionContext);
            }
            case CATCH -> {
                handleCatchOpcode(executionContext);
            }
            case NIP_CATCH -> {
                handleNipCatchOpcode(executionContext);
            }

            // ==================== Type Operations ====================
            case TO_STRING -> {
                handleToStringOpcode(executionContext);
            }
            case TYPEOF -> {
                handleTypeofOpcode(executionContext);
            }
            case DELETE -> {
                handleDeleteOpcode(executionContext);
            }
            case IS_UNDEFINED_OR_NULL -> {
                handleIsUndefinedOrNullOpcode(executionContext);
            }
            case IS_UNDEFINED -> {
                handleIsUndefinedOpcode(executionContext);
            }
            case IS_NULL -> {
                handleIsNullOpcode(executionContext);
            }
            case TYPEOF_IS_UNDEFINED -> {
                handleTypeofIsUndefinedOpcode(executionContext);
            }
            case TYPEOF_IS_FUNCTION -> {
                handleTypeofIsFunctionOpcode(executionContext);
            }

            // ==================== Async Operations ====================
            case ITERATOR_CHECK_OBJECT -> {
                handleIteratorCheckObjectOpcode(executionContext);
            }
            case ITERATOR_GET_VALUE_DONE -> {
                handleIteratorGetValueDoneOpcode(executionContext);
            }
            case ITERATOR_CLOSE -> {
                handleIteratorCloseOpcode(executionContext);
            }
            case ITERATOR_NEXT -> {
                handleIteratorNextOpcode(executionContext);
            }
            case ITERATOR_CALL -> {
                handleIteratorCallOpcode(executionContext);
            }
            case AWAIT -> {
                handleAwaitOpcode(executionContext);
            }
            case FOR_AWAIT_OF_START -> {
                handleForAwaitOfStartOpcode(executionContext);
            }
            case FOR_AWAIT_OF_NEXT -> {
                handleForAwaitOfNextOpcode(executionContext);
            }
            case FOR_OF_START -> {
                handleForOfStartOpcode(executionContext);
            }
            case FOR_OF_NEXT -> {
                handleForOfNextOpcode(executionContext);
            }
            case FOR_IN_START -> {
                handleForInStartOpcode(executionContext);
            }
            case FOR_IN_NEXT -> {
                handleForInNextOpcode(executionContext);
            }
            case FOR_IN_END -> {
                handleForInEndOpcode(executionContext);
            }

            // ==================== Generator Operations ====================
            case INITIAL_YIELD -> {
                handleInitialYieldOpcode(executionContext);
            }
            case YIELD -> {
                handleYieldOpcode(executionContext);
            }
            case YIELD_STAR -> {
                handleYieldStarOpcode(executionContext);
            }
            case ASYNC_YIELD_STAR -> {
                handleAsyncYieldStarOpcode(executionContext);
            }
            case NOP -> {
                handleNop(executionContext);
            }

            case GET_SUPER -> {
                handleGetSuper(executionContext);
            }

            // ==================== Other Operations ====================
            default -> throw new JSVirtualMachineException("Unimplemented opcode: " + op);
        }
    }

    private void ensureConstantObjectPrototype(JSObject object) {
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

                dispatchOpcodeForExecute(executionContext);
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

    private void finalizeExecuteReturn(ExecutionContext executionContext) {
        restoreExecuteCallerState(
                executionContext.restoreStackTop,
                executionContext.previousFrame,
                executionContext.savedStrictMode);
    }

    private JSValue getArgumentValue(int index) {
        JSValue[] arguments = currentFrame.getArguments();
        if (index >= 0 && index < arguments.length) {
            JSValue value = arguments[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
    }

    private JSString getComputedNameString(JSValue keyValue) {
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

    private JSValue getLocalValue(int index) {
        JSValue[] locals = currentFrame.getLocals();
        if (index >= 0 && index < locals.length) {
            JSValue value = locals[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
    }

    private void handleAdd() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        valueStack.push(addValues(left, right));
    }

    private void handleAddLoc(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue rightValue = (JSValue) executionContext.stack[--executionContext.sp];
        JSValue leftValue = executionContext.locals[localIndex];
        if (leftValue == null) {
            leftValue = JSUndefined.INSTANCE;
        }
        executionContext.locals[localIndex] = addValues(leftValue, rightValue);
        executionContext.pc = pc + Opcode.ADD_LOC.getSize();
    }

    private void handleAddOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() + rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleAdd();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.ADD.getSize();
    }

    private void handleAnd() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Fast path for number AND (avoids toInt32 overhead)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() & (int) rightNum.value()));
            return;
        }
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            valueStack.push(new JSBigInt(leftBigInt.value().and(rightBigInt.value())));
            return;
        }
        int result = JSTypeConversions.toInt32(context, pair.left()) & JSTypeConversions.toInt32(context, pair.right());
        valueStack.push(JSNumber.of(result));
    }

    private void handleAndOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(((int) leftNumber.value()) & ((int) rightNumber.value()));
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleAnd();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.AND.getSize();
    }

    private void handleAppend(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue enumerableObject = (JSValue) stack[--sp];
        JSValue positionValue = (JSValue) stack[--sp];
        JSValue arrayValue = (JSValue) stack[--sp];

        if (!(arrayValue instanceof JSArray array)) {
            throw new JSVirtualMachineException("APPEND: first argument must be an array");
        }

        if (!(positionValue instanceof JSNumber positionNumber)) {
            throw new JSVirtualMachineException("APPEND: second argument must be a number");
        }

        int position = (int) positionNumber.value();

        try {
            JSValue iterator = JSIteratorHelper.getIterator(context, enumerableObject);
            if (iterator == null) {
                context.throwError("TypeError", "Value is not iterable");
                throw new JSVirtualMachineException("APPEND: value is not iterable");
            }

            while (true) {
                JSObject resultObject = JSIteratorHelper.iteratorNext(iterator, context);
                if (resultObject == null) {
                    break;
                }
                JSValue doneValue = resultObject.get("done");
                if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                    break;
                }
                JSValue value = resultObject.get(PropertyKey.VALUE);
                array.set(context, position++, value);
            }

            stack[sp++] = array;
            stack[sp++] = JSNumber.of(position);
        } catch (Exception e) {
            throw new JSVirtualMachineException("APPEND: error iterating: " + e.getMessage(), e);
        }

        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.APPEND.getSize();
    }

    private void handleApply(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int isConstructorCall = executionContext.bytecode.readU16(pc + 1);
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue argsArrayValue = (JSValue) stack[--sp];
        JSValue functionValue = (JSValue) stack[--sp];
        JSValue thisArgValue = (JSValue) stack[--sp];

        JSValue result;
        if (isConstructorCall != 0) {
            JSValue constructorNewTarget = thisArgValue;
            if (constructorNewTarget.isNullOrUndefined()) {
                constructorNewTarget = functionValue;
            }
            result = JSReflectObject.construct(
                    context,
                    JSUndefined.INSTANCE,
                    new JSValue[]{functionValue, argsArrayValue, constructorNewTarget});
        } else {
            JSValue[] applyArgs = buildApplyArguments(argsArrayValue, true);
            if (applyArgs == null) {
                pendingException = context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + Opcode.APPLY.getSize();
                return;
            }
            if (functionValue instanceof JSProxy proxyFunction) {
                result = proxyApply(proxyFunction, thisArgValue, applyArgs);
            } else if (functionValue instanceof JSFunction applyFunction) {
                result = applyFunction.call(context, thisArgValue, applyArgs);
            } else {
                throw new JSVirtualMachineException("APPLY: not a function");
            }
        }

        if (context.hasPendingException()) {
            pendingException = context.getPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.APPLY.getSize();
    }

    private void handleArrayFrom(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int count = executionContext.bytecode.readU16(pc + 1);
        JSArray array = context.createJSArray();
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;

        JSValue[] elements = new JSValue[count];
        for (int i = count - 1; i >= 0; i--) {
            elements[i] = (JSValue) stack[--sp];
        }
        for (JSValue element : elements) {
            array.push(element);
        }

        stack[sp++] = array;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.ARRAY_FROM.getSize();
    }

    private void handleArrayNew(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = context.createJSArray();
        executionContext.pc += Opcode.ARRAY_NEW.getSize();
    }

    private void handleAsyncYieldStar() {
        // Keep the same suspension model as sync yield* in the current generator runtime.
        // Full async delegation semantics can be layered on top of this baseline.
        handleYieldStar();
    }

    private void handleAsyncYieldStarOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleAsyncYieldStar();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.ASYNC_YIELD_STAR.getSize();
        if (yieldResult != null) {
            JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
            clearActiveGeneratorSuspendedExecutionState();
            requestOpcodeReturnFromExecute(executionContext, returnValue);
        }
    }

    private void handleAwait() {
        JSValue value = valueStack.pop();

        // If the value is already a promise, use it directly
        // Otherwise, wrap it in a resolved promise
        JSPromise promise;
        if (value instanceof JSPromise) {
            promise = (JSPromise) value;
        } else {
            // Create a new promise and immediately fulfill it
            promise = context.createJSPromise();
            promise.fulfill(value);
        }

        // For proper async/await support, we need to wait for the promise to settle
        // and push the resolved value (not the promise itself)

        // If the promise is pending, we need to process microtasks until it settles
        if (promise.getState() == JSPromise.PromiseState.PENDING) {
            if (awaitSuspensionEnabled && activeGeneratorState != null) {
                awaitSuspensionPromise = promise;
                return;
            }
            // Process microtasks until the promise settles
            context.processMicrotasks();
        }

        // Now the promise should be settled, push the resolved value
        if (promise.getState() == JSPromise.PromiseState.FULFILLED) {
            valueStack.push(promise.getResult());
        } else if (promise.getState() == JSPromise.PromiseState.REJECTED) {
            // Rejected await operand throws into the current async control flow.
            // Let VM catch handling propagate it to surrounding try/catch.
            JSValue result = promise.getResult();
            IJSPromiseRejectCallback callback = context.getPromiseRejectCallback();
            if (callback != null) {
                callback.callback(PromiseRejectEvent.PromiseRejectWithNoHandler, promise, result);
            }
            pendingException = result;
            context.setPendingException(result);
        } else {
            // Promise is still pending - this shouldn't happen
            throw new JSVirtualMachineException("Promise did not settle after processing microtasks");
        }
    }

    private void handleAwaitOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleAwait();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.AWAIT.getSize();
        if (awaitSuspensionPromise != null) {
            saveActiveGeneratorSuspendedExecutionState(
                    executionContext.frame,
                    executionContext.pc,
                    executionContext.stack,
                    executionContext.sp,
                    executionContext.frameStackBase);
            requestOpcodeReturnFromExecute(executionContext, JSUndefined.INSTANCE);
        }
    }

    private void handleCall(int argCount) {
        // Stack layout (bottom to top): method, receiver, arg1, arg2, ...
        // Pop arguments from stack
        JSValue[] args = argCount == 0 ? EMPTY_ARGS : new JSValue[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
            args[i] = valueStack.pop();
        }

        // Pop receiver (thisArg)
        JSValue receiver = valueStack.pop();

        // Pop callee (method)
        JSValue callee = valueStack.pop();

        // SWAP locks property tracking while evaluating method-call arguments.
        // Unlock before invoking the callee so nested calls can build their own chains.
        propertyAccessLock = false;

        // Handle proxy apply trap (QuickJS: js_proxy_call)
        if (callee instanceof JSProxy proxy) {
            JSValue result = proxyApply(proxy, receiver, args);
            valueStack.push(result);
            resetPropertyAccessTracking();
            return;
        }

        // Special handling for Symbol constructor (must be called without new)
        if (callee instanceof JSObject calleeObj) {
            if (calleeObj.getConstructorType() == JSConstructorType.SYMBOL_OBJECT) {
                // Call Symbol() function
                JSValue result = SymbolConstructor.call(context, receiver, args);
                valueStack.push(result);
                return;
            }

            // Special handling for BigInt constructor (must be called without new)
            if (calleeObj.getConstructorType() == JSConstructorType.BIG_INT_OBJECT) {
                // Call BigInt() function
                JSValue result = BigIntConstructor.call(context, receiver, args);
                valueStack.push(result);
                return;
            }
        }

        if (callee instanceof JSFunction function) {
            // Per ES spec: If F's [[FunctionKind]] is "classConstructor", throw TypeError
            boolean isClassCtor = function instanceof JSClass;
            if (!isClassCtor && function instanceof JSBytecodeFunction bytecodeFunc) {
                isClassCtor = bytecodeFunc.isClassConstructor();
            }
            if (isClassCtor) {
                resetPropertyAccessTracking();
                pendingException = context.throwTypeError("Class constructor " + function.getName()
                        + " cannot be invoked without 'new'");
                context.clearPendingException();
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }

            if (function instanceof JSNativeFunction nativeFunc) {
                // Check if this function requires 'new'
                if (nativeFunc.requiresNew()) {
                    String constructorName = nativeFunc.getName() != null ? nativeFunc.getName() : "constructor";
                    resetPropertyAccessTracking();
                    String errorMessage = switch (constructorName) {
                        case JSPromise.NAME -> "Promise constructor cannot be invoked without 'new'";
                        default -> "Constructor " + constructorName + " requires 'new'";
                    };
                    pendingException = context.throwTypeError(errorMessage);
                    context.clearPendingException();
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }
                // Call native function with receiver as thisArg
                try {
                    JSValue result = nativeFunc.call(context, receiver, args);
                    // Check for pending exception after native function call
                    if (context.hasPendingException()) {
                        // Set pending exception in VM and push placeholder
                        // The main loop will handle the exception on next iteration
                        pendingException = context.getPendingException();
                        valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        valueStack.push(result);
                    }
                } catch (JSException e) {
                    // Native function threw a JSException (e.g. from eval/evalScript)
                    // Convert to pending exception so VM try-catch handles it
                    pendingException = e.getErrorValue();
                    context.clearPendingException();
                    valueStack.push(JSUndefined.INSTANCE);
                } catch (JSVirtualMachineException e) {
                    // Native function internally called a bytecode function that threw
                    // (e.g. ToPrimitive calling user's toString/valueOf)
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
                    valueStack.push(JSUndefined.INSTANCE);
                } catch (JSErrorException e) {
                    // Native function threw a typed JS error (e.g. JSRangeErrorException)
                    pendingException = context.throwError(e.getErrorType().name(), e.getMessage());
                    context.clearPendingException();
                    valueStack.push(JSUndefined.INSTANCE);
                }
            } else if (function instanceof JSBytecodeFunction bytecodeFunc) {
                try {
                    // Call through the function's call method to handle async wrapping
                    JSValue result = bytecodeFunc.call(context, receiver, args);
                    if (context.hasPendingException()) {
                        pendingException = context.getPendingException();
                        valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        valueStack.push(result);
                    }
                } catch (JSVirtualMachineException e) {
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
                    valueStack.push(JSUndefined.INSTANCE);
                }
            } else if (function instanceof JSBoundFunction boundFunc) {
                // Call bound function - the receiver is ignored for bound functions
                try {
                    JSValue result = boundFunc.call(context, receiver, args);
                    // Check for pending exception after bound function call
                    if (context.hasPendingException()) {
                        pendingException = context.getPendingException();
                        valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        valueStack.push(result);
                    }
                } catch (JSVirtualMachineException e) {
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
                    valueStack.push(JSUndefined.INSTANCE);
                }
            } else {
                valueStack.push(JSUndefined.INSTANCE);
            }
            // Clear property access tracking after successful call
            resetPropertyAccessTracking();
        } else {
            // Not a function - set pending TypeError so JS catch handlers can process it
            // Generate a descriptive error message similar to V8/QuickJS
            String message;
            if (!propertyAccessChain.isEmpty()) {
                // Use the tracked property access for better error messages
                message = propertyAccessChain + " is not a function";
            } else if (callee instanceof JSUndefined) {
                message = "undefined is not a function";
            } else if (callee instanceof JSNull) {
                message = "null is not a function";
            } else if (callee instanceof JSNumber) {
                message = callee.toJavaObject() + " is not a function";
            } else if (callee instanceof JSString str) {
                message = "'" + str.value() + "' is not a function";
            } else {
                message = JSTypeChecking.typeof(callee) + " is not a function";
            }
            resetPropertyAccessTracking();
            pendingException = context.throwTypeError(message);
            context.clearPendingException();
            valueStack.push(JSUndefined.INSTANCE);
        }
    }

    private void handleCall0(ExecutionContext executionContext) {
        handleCallFixedArityOpcode(executionContext, 0, Opcode.CALL0.getSize());
    }

    private void handleCall1(ExecutionContext executionContext) {
        handleCallFixedArityOpcode(executionContext, 1, Opcode.CALL1.getSize());
    }

    private void handleCall2(ExecutionContext executionContext) {
        handleCallFixedArityOpcode(executionContext, 2, Opcode.CALL2.getSize());
    }

    private void handleCall3(ExecutionContext executionContext) {
        handleCallFixedArityOpcode(executionContext, 3, Opcode.CALL3.getSize());
    }

    private void handleCallConstructor(int argCount) {
        // Pop arguments
        JSValue[] args = argCount == 0 ? EMPTY_ARGS : new JSValue[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
            args[i] = valueStack.pop();
        }
        // Pop constructor
        JSValue constructor = valueStack.pop();
        // Handle proxy construct trap (QuickJS: js_proxy_call_constructor)
        if (constructor instanceof JSProxy jsProxy) {
            // Following QuickJS JS_CallConstructorInternal:
            // Check if target is a constructor BEFORE checking for construct trap
            JSValue target = jsProxy.getTarget();
            if (JSTypeChecking.isConstructor(target)) {
                valueStack.push(proxyConstruct(jsProxy, args, jsProxy));
            } else {
                context.throwTypeError("proxy is not a constructor");
                pendingException = context.getPendingException();
                valueStack.push(JSUndefined.INSTANCE);
            }
        } else if (constructor instanceof JSFunction jsFunction) {
            // Check if the function is constructable
            if (!JSTypeChecking.isConstructor(jsFunction)) {
                context.throwTypeError(jsFunction.getName() + " is not a constructor");
                pendingException = context.getPendingException();
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            JSValue result;
            try {
                result = constructFunction(jsFunction, args, jsFunction);
            } catch (JSVirtualMachineException e) {
                // Constructor internally called a bytecode function that threw
                // (e.g., getter in iterable item). Convert to pendingException so
                // the main loop can route it to JavaScript catch handlers.
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
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                valueStack.push(JSUndefined.INSTANCE);
            } else {
                valueStack.push(result);
            }
        } else {
            context.throwTypeError("not a constructor");
            pendingException = context.getPendingException();
            valueStack.push(JSUndefined.INSTANCE);
        }
    }

    private void handleCallConstructorOpcode(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        byte[] instructions = executionContext.instructions;
        int argumentCount = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        valueStack.stackTop = executionContext.sp;
        handleCallConstructor(argumentCount);
        executionContext.sp = valueStack.stackTop;
        executionContext.pc = pc + Opcode.CALL_CONSTRUCTOR.getSize();
    }

    private void handleCallFixedArityOpcode(ExecutionContext executionContext, int argumentCount, int opcodeSize) {
        valueStack.stackTop = executionContext.sp;
        handleCall(argumentCount);
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += opcodeSize;
    }

    private void handleCallOpcode(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        byte[] instructions = executionContext.instructions;
        int argumentCount = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        handleCallFixedArityOpcode(executionContext, argumentCount, Opcode.CALL.getSize());
    }

    private void handleCatchOpcode(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int rawCatchOffset = executionContext.bytecode.readI32(pc + 1);
        boolean isFinally = (rawCatchOffset & 0x80000000) != 0;
        int catchOffset = rawCatchOffset & 0x7FFFFFFF;
        int catchHandlerProgramCounter = pc + Opcode.CATCH.getSize() + catchOffset;
        executionContext.stack[executionContext.sp++] = new JSCatchOffset(catchHandlerProgramCounter, isFinally);
        executionContext.pc = pc + Opcode.CATCH.getSize();
    }

    private void handleCloseLoc(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.frame.closeLocal(localIndex);
        executionContext.pc = pc + Opcode.CLOSE_LOC.getSize();
    }

    private void handleCopyDataProperties(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int mask = executionContext.bytecode.readU8(pc + 1);
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue targetValue = (JSValue) stack[sp - 1 - (mask & 3)];
        JSValue sourceValue = (JSValue) stack[sp - 1 - ((mask >> 2) & 7)];
        JSValue excludeListValue = (JSValue) stack[sp - 1 - ((mask >> 5) & 7)];
        copyDataProperties(targetValue, sourceValue, excludeListValue);
        executionContext.pc = pc + Opcode.COPY_DATA_PROPERTIES.getSize();
    }

    private void handleDec() {
        JSValue operand = valueStack.pop();
        valueStack.push(incrementValue(operand, -1));
    }

    private void handleDecLoc(ExecutionContext executionContext) {
        handleIncDecLoc(executionContext, -1);
    }

    private void handleDecOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleDec();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.DEC.getSize();
    }

    private void handleDefineArrayEl(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue value = (JSValue) stack[--sp];
        JSValue indexValue = (JSValue) stack[sp - 1];
        JSValue arrayValue = (JSValue) stack[sp - 2];

        if (!(arrayValue instanceof JSArray array)) {
            throw new JSVirtualMachineException("DEFINE_ARRAY_EL: first argument must be an array");
        }
        if (!(indexValue instanceof JSNumber indexNumber)) {
            throw new JSVirtualMachineException("DEFINE_ARRAY_EL: second argument must be a number");
        }

        int index = (int) indexNumber.value();
        array.set(context, index, value);

        executionContext.sp = sp;
        executionContext.pc += Opcode.DEFINE_ARRAY_EL.getSize();
    }

    private void handleDefineClass(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int classNameAtom = executionContext.bytecode.readU32(pc + 1);
        String className = executionContext.bytecode.getAtoms()[classNameAtom];
        JSValue constructorValue = (JSValue) stack[--sp];
        JSValue superClassValue = (JSValue) stack[--sp];

        if (!(constructorValue instanceof JSFunction constructorFunction)) {
            throw new JSVirtualMachineException("DEFINE_CLASS: constructor must be a function");
        }

        JSObject prototypeObject = context.createJSObject();
        if (superClassValue instanceof JSNull) {
            prototypeObject.setPrototype(null);
        } else if (superClassValue != JSUndefined.INSTANCE) {
            if (!JSTypeChecking.isConstructor(superClassValue)) {
                context.throwTypeError("parent class must be constructor");
                pendingException = context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + Opcode.DEFINE_CLASS.getSize();
                return;
            }
            if (superClassValue instanceof JSFunction superFunction) {
                context.transferPrototype(prototypeObject, superFunction);
                constructorFunction.setPrototype(superFunction);
            }
        }

        JSObject constructorObject = constructorFunction;
        constructorObject.defineProperty(
                PropertyKey.fromString("prototype"),
                prototypeObject,
                PropertyDescriptor.DataState.None);

        prototypeObject.set(PropertyKey.CONSTRUCTOR, constructorValue);
        setObjectName(constructorValue, new JSString(className));

        if (constructorFunction instanceof JSBytecodeFunction bytecodeConstructor) {
            bytecodeConstructor.setClassConstructor(true);
            if (superClassValue != JSUndefined.INSTANCE) {
                bytecodeConstructor.setDerivedConstructor(true);
            }
        }

        stack[sp++] = prototypeObject;
        stack[sp++] = constructorValue;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.DEFINE_CLASS.getSize();
    }

    private void handleDefineClassComputed(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int classNameAtom = executionContext.bytecode.readU32(pc + 1);
        int classFlags = executionContext.bytecode.readU8(pc + 5);
        String className = executionContext.bytecode.getAtoms()[classNameAtom];
        JSValue constructorValue = (JSValue) stack[--sp];
        JSValue superClassValue = (JSValue) stack[--sp];
        JSValue computedClassNameValue = (JSValue) stack[sp - 1];

        if (!(constructorValue instanceof JSFunction constructorFunction)) {
            throw new JSVirtualMachineException("DEFINE_CLASS_COMPUTED: constructor must be a function");
        }

        JSObject prototypeObject = context.createJSObject();
        boolean hasHeritage = (classFlags & 1) != 0;
        if (hasHeritage && superClassValue != JSUndefined.INSTANCE && superClassValue != JSNull.INSTANCE) {
            if (superClassValue instanceof JSFunction superFunction) {
                context.transferPrototype(prototypeObject, superFunction);
                constructorFunction.setPrototype(superFunction);
            } else {
                throw new JSVirtualMachineException(context.throwTypeError("parent class must be constructor"));
            }
        }

        constructorFunction.defineProperty(
                PropertyKey.fromString("prototype"),
                prototypeObject,
                PropertyDescriptor.DataState.None);
        prototypeObject.set(PropertyKey.CONSTRUCTOR, constructorValue);
        JSString computedClassName = getComputedNameString(computedClassNameValue);
        if (computedClassName.value().isEmpty()) {
            computedClassName = new JSString(className);
        }
        setObjectName(constructorValue, computedClassName);

        stack[sp++] = prototypeObject;
        stack[sp++] = constructorValue;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.DEFINE_CLASS_COMPUTED.getSize();
    }

    private void handleDefineField(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int fieldNameAtom = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[fieldNameAtom];
        JSValue value = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject object) {
            object.set(PropertyKey.fromString(fieldName), value);
        }

        stack[sp++] = objectValue;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.DEFINE_FIELD.getSize();
    }

    private void handleDefineMethod(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int methodNameAtom = executionContext.bytecode.readU32(pc + 1);
        String methodName = executionContext.bytecode.getAtoms()[methodNameAtom];
        JSValue methodValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject object) {
            if (methodValue instanceof JSFunction methodFunction) {
                methodFunction.setHomeObject(object);
            }
            object.set(PropertyKey.fromString(methodName), methodValue);
        }

        stack[sp++] = objectValue;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.DEFINE_METHOD.getSize();
    }

    private void handleDefineMethodComputed(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int methodFlags = executionContext.bytecode.readU8(pc + 1);
        boolean enumerable = (methodFlags & 4) != 0;
        int methodKind = methodFlags & 3;

        JSValue methodValue = (JSValue) stack[--sp];
        JSValue propertyValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];

        if (objectValue instanceof JSObject object) {
            PropertyKey key = PropertyKey.fromValue(context, propertyValue);
            JSString computedName = getComputedNameString(propertyValue);
            if (methodValue instanceof JSFunction methodFunction) {
                methodFunction.setHomeObject(object);
                String namePrefix;
                if (methodKind == 1) {
                    namePrefix = "get ";
                } else if (methodKind == 2) {
                    namePrefix = "set ";
                } else {
                    namePrefix = "";
                }
                setObjectName(methodFunction, new JSString(namePrefix + computedName.value()));
                if (methodKind == 1 || methodKind == 2) {
                    methodFunction.delete(PropertyKey.PROTOTYPE);
                }
            }

            if (methodKind == 0) {
                object.defineProperty(
                        key,
                        PropertyDescriptor.dataDescriptor(
                                methodValue,
                                enumerable
                                        ? PropertyDescriptor.DataState.All
                                        : PropertyDescriptor.DataState.ConfigurableWritable));
            } else if (methodKind == 1) {
                JSFunction getter = methodValue instanceof JSFunction function ? function : null;
                JSFunction setter = null;
                PropertyDescriptor descriptor = object.getOwnPropertyDescriptor(key);
                if (descriptor != null && descriptor.hasSetter()) {
                    setter = descriptor.getSetter();
                }
                object.defineProperty(
                        key,
                        PropertyDescriptor.accessorDescriptor(
                                getter,
                                setter,
                                enumerable
                                        ? PropertyDescriptor.AccessorState.All
                                        : PropertyDescriptor.AccessorState.Configurable));
            } else if (methodKind == 2) {
                JSFunction setter = methodValue instanceof JSFunction function ? function : null;
                JSFunction getter = null;
                PropertyDescriptor descriptor = object.getOwnPropertyDescriptor(key);
                if (descriptor != null && descriptor.hasGetter()) {
                    getter = descriptor.getGetter();
                }
                object.defineProperty(
                        key,
                        PropertyDescriptor.accessorDescriptor(
                                getter,
                                setter,
                                enumerable
                                        ? PropertyDescriptor.AccessorState.All
                                        : PropertyDescriptor.AccessorState.Configurable));
            } else {
                throw new JSVirtualMachineException("DEFINE_METHOD_COMPUTED: unsupported method flags " + methodFlags);
            }
        }

        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.DEFINE_METHOD_COMPUTED.getSize();
    }

    private void handleDefinePrivateField(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue value = (JSValue) stack[--sp];
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject object && privateSymbolValue instanceof JSSymbol symbol) {
            object.set(PropertyKey.fromSymbol(symbol), value);
        }

        stack[sp++] = objectValue;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.DEFINE_PRIVATE_FIELD.getSize();
    }

    private void handleDefineProp(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue propertyValue = (JSValue) stack[--sp];
        JSValue propertyKeyValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];
        if (objectValue instanceof JSObject jsObject) {
            PropertyKey key = PropertyKey.fromValue(context, propertyKeyValue);
            jsObject.defineProperty(context, key, propertyValue, PropertyDescriptor.DataState.All);
        }
        executionContext.sp = sp;
        executionContext.pc += Opcode.DEFINE_PROP.getSize();
    }

    private void handleDelete() {
        JSValue property = valueStack.pop();
        JSValue object = valueStack.pop();
        boolean result = false;
        if (object instanceof JSObject jsObj) {
            PropertyKey key = PropertyKey.fromValue(context, property);
            result = jsObj.delete(context, key);
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
            }
        }
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleDeleteOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleDelete();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.DELETE.getSize();
    }

    private void handleDeleteVar(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        boolean deleted = context.getGlobalObject().delete(context, PropertyKey.fromString(variableName));
        executionContext.stack[executionContext.sp++] = JSBoolean.valueOf(deleted);
        executionContext.pc = pc + Opcode.DELETE_VAR.getSize();
    }

    private void handleDiv() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            if (rightBigInt.value().equals(BIGINT_ZERO)) {
                pendingException = context.throwRangeError("Division by zero");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            valueStack.push(new JSBigInt(leftBigInt.value().divide(rightBigInt.value())));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        valueStack.push(JSNumber.of(leftNumber.value() / rightNumber.value()));
    }

    private void handleDivOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleDiv();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.DIV.getSize();
    }

    private void handleDrop(ExecutionContext executionContext) {
        executionContext.sp--;
        executionContext.pc += 1;
    }

    private void handleDup(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 1];
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    private void handleDup1(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 2];
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    private void handleDup2(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 2];
        stack[sp + 1] = stack[sp - 1];
        executionContext.sp = sp + 2;
        executionContext.pc += 1;
    }

    private void handleEq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.abstractEquals(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleEqOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleEq();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.EQ.getSize();
    }

    private void handleExp() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            BigInteger exponent = rightBigInt.value();
            if (exponent.signum() < 0) {
                pendingException = context.throwRangeError("Exponent must be positive");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            final int exponentInt;
            try {
                exponentInt = exponent.intValueExact();
            } catch (ArithmeticException e) {
                pendingException = context.throwRangeError("BigInt exponent is too large");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            valueStack.push(new JSBigInt(leftBigInt.value().pow(exponentInt)));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        valueStack.push(JSNumber.of(Math.pow(leftNumber.value(), rightNumber.value())));
    }

    private void handleExpOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleExp();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.EXP.getSize();
    }

    private void handleFclosure(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int functionIndex = executionContext.bytecode.readU32(pc + 1);
        JSValue functionValue = executionContext.bytecode.getConstants()[functionIndex];
        if (functionValue instanceof JSBytecodeFunction templateFunction) {
            int[] captureInfos = templateFunction.getCaptureSourceInfos();
            JSBytecodeFunction closureFunction;
            if (captureInfos != null) {
                VarRef[] capturedVarRefs = createVarRefsFromCaptures(captureInfos, executionContext.frame);
                closureFunction = templateFunction.copyWithVarRefs(capturedVarRefs);
                int selfIndex = templateFunction.getSelfCaptureIndex();
                if (selfIndex >= 0 && selfIndex < capturedVarRefs.length) {
                    capturedVarRefs[selfIndex].set(closureFunction);
                }
            } else {
                int closureCount = templateFunction.getClosureVars().length;
                JSValue[] capturedClosureVars = new JSValue[closureCount];
                for (int i = closureCount - 1; i >= 0; i--) {
                    capturedClosureVars[i] = (JSValue) stack[--sp];
                }
                closureFunction = templateFunction.copyWithClosureVars(capturedClosureVars);
                int selfIndex = templateFunction.getSelfCaptureIndex();
                if (selfIndex >= 0 && selfIndex < capturedClosureVars.length) {
                    capturedClosureVars[selfIndex] = closureFunction;
                }
            }
            closureFunction.initializePrototypeChain(context);
            stack[sp++] = closureFunction;
        } else {
            if (functionValue instanceof JSFunction function) {
                function.initializePrototypeChain(context);
            }
            stack[sp++] = functionValue;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.FCLOSURE.getSize();
    }

    private void handleFclosure8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int functionIndex = executionContext.bytecode.readU8(pc + 1);
        JSValue functionValue = executionContext.bytecode.getConstants()[functionIndex];
        if (functionValue instanceof JSBytecodeFunction templateFunction) {
            int[] captureInfos = templateFunction.getCaptureSourceInfos();
            JSBytecodeFunction closureFunction;
            if (captureInfos != null) {
                VarRef[] capturedVarRefs = createVarRefsFromCaptures(captureInfos, executionContext.frame);
                closureFunction = templateFunction.copyWithVarRefs(capturedVarRefs);
            } else {
                int closureCount = templateFunction.getClosureVars().length;
                JSValue[] capturedClosureVars = new JSValue[closureCount];
                for (int i = closureCount - 1; i >= 0; i--) {
                    capturedClosureVars[i] = (JSValue) stack[--sp];
                }
                closureFunction = templateFunction.copyWithClosureVars(capturedClosureVars);
            }
            closureFunction.initializePrototypeChain(context);
            stack[sp++] = closureFunction;
        } else {
            if (functionValue instanceof JSFunction function) {
                function.initializePrototypeChain(context);
            }
            stack[sp++] = functionValue;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.FCLOSURE8.getSize();
    }

    private void handleForAwaitOfNext() {
        // Stack layout before: iter, next, catch_offset (bottom to top)
        // Stack layout after: iter, next, catch_offset, result (bottom to top)

        // Pop catch offset temporarily
        JSValue catchOffset = valueStack.pop();

        // Peek next method and iterator (don't pop - they stay for next iteration)
        JSValue nextMethod = valueStack.peek(0);  // next method
        JSValue iterator = valueStack.peek(1);    // iterator object

        // Call iterator.next()
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            throw new JSVirtualMachineException("Next method must be a function");
        }

        JSValue result = nextFunc.call(context, iterator, EMPTY_ARGS);

        // Restore catch_offset and push the result
        valueStack.push(catchOffset);  // Restore catch_offset
        valueStack.push(result);        // Push the result (promise that resolves to {value, done})
    }

    private void handleForAwaitOfNextOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleForAwaitOfNext();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.FOR_AWAIT_OF_NEXT.getSize();
    }

    private void handleForAwaitOfStart() {
        // Pop the iterable from the stack
        JSValue iterable = valueStack.pop();

        // Auto-box primitives (strings, numbers, etc.) to access their Symbol.asyncIterator or Symbol.iterator
        JSObject iterableObj;
        if (iterable instanceof JSObject obj) {
            iterableObj = obj;
        } else {
            // Try to auto-box the primitive
            iterableObj = toObject(iterable);
            if (iterableObj == null) {
                pendingException = context.throwTypeError("object is not async iterable");
                return;
            }
        }

        // First, try Symbol.asyncIterator
        JSValue asyncIteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ASYNC_ITERATOR);
        JSValue iteratorMethod = null;
        boolean wrapSyncIteratorAsAsync = false;

        if (asyncIteratorMethod instanceof JSFunction) {
            iteratorMethod = asyncIteratorMethod;
        } else if (asyncIteratorMethod != null && !asyncIteratorMethod.isNullOrUndefined()) {
            // Non-callable, non-nullish value: TypeError
            pendingException = context.throwTypeError("object is not async iterable");
            return;
        } else {
            // Fall back to Symbol.iterator (sync iterator that will be auto-wrapped)
            iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);

            if (!(iteratorMethod instanceof JSFunction)) {
                pendingException = context.throwTypeError("object is not async iterable");
                return;
            }
            wrapSyncIteratorAsAsync = true;
        }

        // Call the iterator method to get an iterator
        JSValue iterator = ((JSFunction) iteratorMethod).call(context, iterable, EMPTY_ARGS);
        if (context.hasPendingException()) {
            pendingException = context.getPendingException();
            return;
        }

        if (!(iterator instanceof JSObject iteratorObj)) {
            pendingException = context.throwTypeError("iterator must return an object");
            return;
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);

        if (!(nextMethod instanceof JSFunction nextFunction)) {
            pendingException = context.throwTypeError("iterator must have a next method");
            return;
        }

        JSValue nextMethodForStack = nextFunction;
        if (wrapSyncIteratorAsAsync) {
            final JSObject syncIteratorObject = iteratorObj;
            final JSFunction syncNextFunction = nextFunction;
            nextMethodForStack = new JSNativeFunction("next", 0, (childContext, thisArg, args) -> {
                JSValue syncResult;
                try {
                    syncResult = syncNextFunction.call(childContext, syncIteratorObject, EMPTY_ARGS);
                } catch (JSException e) {
                    JSPromise rejectedPromise = childContext.createJSPromise();
                    if (childContext.hasPendingException()) {
                        childContext.clearAllPendingExceptions();
                    }
                    rejectedPromise.reject(e.getErrorValue());
                    return rejectedPromise;
                } catch (Exception e) {
                    JSPromise rejectedPromise = childContext.createJSPromise();
                    JSValue reason;
                    if (childContext.hasPendingException()) {
                        reason = childContext.getPendingException();
                        childContext.clearAllPendingExceptions();
                    } else {
                        String message = e.getMessage();
                        reason = new JSString(message != null ? message : e.toString());
                    }
                    rejectedPromise.reject(reason);
                    return rejectedPromise;
                }
                if (childContext.hasPendingException()) {
                    JSValue reason = childContext.getPendingException();
                    childContext.clearAllPendingExceptions();
                    JSPromise rejectedPromise = childContext.createJSPromise();
                    rejectedPromise.reject(reason);
                    return rejectedPromise;
                }
                if (!(syncResult instanceof JSObject syncResultObject)) {
                    JSPromise rejectedPromise = childContext.createJSPromise();
                    rejectedPromise.reject(childContext.throwTypeError("iterator result must be an object"));
                    childContext.clearPendingException();
                    return rejectedPromise;
                }

                JSValue value = syncResultObject.get(childContext, PropertyKey.VALUE);
                if (childContext.hasPendingException()) {
                    JSValue reason = childContext.getPendingException();
                    childContext.clearAllPendingExceptions();
                    JSPromise rejectedPromise = childContext.createJSPromise();
                    rejectedPromise.reject(reason);
                    return rejectedPromise;
                }
                JSValue doneValue = syncResultObject.get(childContext, PropertyKey.DONE);
                if (childContext.hasPendingException()) {
                    JSValue reason = childContext.getPendingException();
                    childContext.clearAllPendingExceptions();
                    JSPromise rejectedPromise = childContext.createJSPromise();
                    rejectedPromise.reject(reason);
                    return rejectedPromise;
                }
                boolean done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;
                if (childContext.hasPendingException()) {
                    JSValue reason = childContext.getPendingException();
                    childContext.clearAllPendingExceptions();
                    JSPromise rejectedPromise = childContext.createJSPromise();
                    rejectedPromise.reject(reason);
                    return rejectedPromise;
                }
                JSPromise asyncFromSyncResultPromise = JSAsyncIterator.createAsyncFromSyncResultPromise(childContext, value, done);
                if (done) {
                    return asyncFromSyncResultPromise;
                }
                JSPromise closeOnRejectionPromise = childContext.createJSPromise();
                asyncFromSyncResultPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfilled", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                                    JSValue iteratorResult = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                                    closeOnRejectionPromise.fulfill(iteratorResult);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                childContext
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onRejected", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                                    JSValue reason = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                                    JSValue returnMethodValue = syncIteratorObject.get(callbackContext, PropertyKey.RETURN);
                                    if (callbackContext.hasPendingException()) {
                                        callbackContext.clearAllPendingExceptions();
                                    } else if (returnMethodValue instanceof JSFunction returnFunction) {
                                        JSValue closeResult = returnFunction.call(callbackContext, syncIteratorObject, EMPTY_ARGS);
                                        if (callbackContext.hasPendingException()) {
                                            callbackContext.clearAllPendingExceptions();
                                        } else if (!(closeResult instanceof JSObject)) {
                                            // Preserve the original rejection reason during close-on-rejection.
                                        }
                                    } else if (returnMethodValue.isNullOrUndefined()) {
                                        // No return method.
                                    } else {
                                        // Non-callable return method is ignored here to preserve original rejection.
                                    }
                                    closeOnRejectionPromise.reject(reason);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                childContext
                        )
                );
                return closeOnRejectionPromise;
            });
        }

        // Push iterator, next method, and catch offset (0) onto the stack
        valueStack.push(iterator);         // Iterator object
        valueStack.push(nextMethodForStack);       // next() method
        valueStack.push(JSNumber.of(0));  // Catch offset (placeholder)
    }

    private void handleForAwaitOfStartOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleForAwaitOfStart();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.FOR_AWAIT_OF_START.getSize();
    }

    private void handleForInEnd() {
        // Clean up the enumerator from the stack
        JSStackValue stackValue = valueStack.popStackValue();
        if (!(stackValue instanceof JSInternalValue internal) ||
                !(internal.value() instanceof JSForInEnumerator)) {
            throw new JSVirtualMachineException("Invalid for-in enumerator in FOR_IN_END");
        }
        // Just pop it, no need to do anything else
    }

    private void handleForInEndOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleForInEnd();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.FOR_IN_END.getSize();
    }

    private void handleForInNext() {
        // The enumerator should be on top of the stack (peek at it, don't pop)
        JSStackValue stackValue = valueStack.popStackValue();

        if (!(stackValue instanceof JSInternalValue internal) ||
                !(internal.value() instanceof JSForInEnumerator enumerator)) {
            throw new JSVirtualMachineException("Invalid for-in enumerator");
        }

        // Get the next key from the enumerator
        JSValue nextKey = enumerator.next();

        // Push enumerator back first (so it stays at same position)
        valueStack.pushStackValue(new JSInternalValue(enumerator));

        // Then push the key onto the stack
        valueStack.push(nextKey);
    }

    private void handleForInNextOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleForInNext();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.FOR_IN_NEXT.getSize();
    }

    private void handleForInStart() {
        // Pop the object from the stack
        JSValue obj = valueStack.pop();

        // Create a for-in enumerator
        JSForInEnumerator enumerator = new JSForInEnumerator(obj);

        // Push the enumerator onto the stack (wrapped in a special internal object)
        // We'll use JSInternalValue to hold it
        valueStack.pushStackValue(new JSInternalValue(enumerator));
    }

    private void handleForInStartOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleForInStart();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.FOR_IN_START.getSize();
    }

    private void handleForOfNext(int depth) {
        // Stack layout: ... iter next catch_offset [depth values] (bottom to top)
        // The depth parameter tells us how many values are between catch_offset and top
        // Following QuickJS: offset = -3 - depth
        // iter is at sp[offset] = sp[-3-depth], next is at sp[offset+1] = sp[-2-depth]

        // Pop depth values temporarily
        if (forOfTempValues.length < depth) {
            forOfTempValues = new JSValue[Math.max(depth, forOfTempValues.length * 2)];
        }
        for (int i = 0; i < depth; i++) {
            forOfTempValues[i] = valueStack.pop();
        }
        // Now top of stack is catch_offset
        JSValue catchOffset = valueStack.pop();

        // Now peek next method and iterator (don't pop - they stay for next iteration)
        // Stack is now: ... iter next (top)
        JSValue nextMethod = valueStack.peek(0);  // next method (top)
        JSValue iterator = valueStack.peek(1);    // iterator object (below next)

        // Call iterator.next()
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            String actualType = nextMethod == null ? "null" : nextMethod.getClass().getSimpleName();
            String iterType = iterator == null ? "null" : iterator.getClass().getSimpleName();
            throw new JSVirtualMachineException(
                    "Next method must be a function in FOR_OF_NEXT (nextMethod=" + actualType + ", iterator=" + iterType + ")"
            );
        }

        JSValue result = nextFunc.call(context, iterator, EMPTY_ARGS);

        // Check for pending exception (e.g., TypedArray detachment during iteration)
        if (context.hasPendingException()) {
            // Restore stack before throwing
            valueStack.push(catchOffset);
            for (int i = depth - 1; i >= 0; i--) {
                valueStack.push(forOfTempValues[i]);
                forOfTempValues[i] = null;
            }
            JSValue pendingEx = context.getPendingException();
            if (pendingEx instanceof JSError jsError) {
                throw new JSVirtualMachineException(jsError);
            }
            throw new JSVirtualMachineException("Iterator next threw", pendingEx);
        }

        // For sync iterators, extract value and done from the result object
        // QuickJS FOR_OF_NEXT pushes: iter, next, catch_offset, value, done
        // So we need to extract {value, done} from result

        if (!(result instanceof JSObject resultObj)) {
            throw new JSVirtualMachineException("Iterator result must be an object");
        }

        // Get the value property
        JSValue value = resultObj.get(PropertyKey.VALUE);
        if (value == null) {
            value = JSUndefined.INSTANCE;
        }

        // Get the done property
        JSValue doneValue = resultObj.get(PropertyKey.DONE);
        boolean done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;

        // Push catch_offset back, then restore temp values, then push value and done
        valueStack.push(catchOffset);
        for (int i = depth - 1; i >= 0; i--) {
            valueStack.push(forOfTempValues[i]);
            forOfTempValues[i] = null;
        }
        valueStack.push(value);
        valueStack.push(done ? JSBoolean.TRUE : JSBoolean.FALSE);
    }

    private void handleForOfNextOpcode(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int depth = executionContext.bytecode.readU8(pc + 1);
        valueStack.stackTop = executionContext.sp;
        handleForOfNext(depth);
        executionContext.sp = valueStack.stackTop;
        executionContext.pc = pc + Opcode.FOR_OF_NEXT.getSize();
    }

    private void handleForOfStart() {
        // Pop the iterable from the stack
        JSValue iterable = valueStack.pop();

        // Auto-box primitives (strings, numbers, etc.) to access their Symbol.iterator
        JSObject iterableObj;
        if (iterable instanceof JSObject obj) {
            iterableObj = obj;
        } else {
            // Try to auto-box the primitive
            iterableObj = toObject(iterable);
            if (iterableObj == null) {
                throw new JSVirtualMachineException("Object is not iterable");
            }
        }

        // Get Symbol.iterator method
        JSValue iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);

        if (!(iteratorMethod instanceof JSFunction iteratorFunc)) {
            throw new JSVirtualMachineException("Object is not iterable");
        }

        // Call the Symbol.iterator method to get an iterator
        // Use the original iterable value for the 'this' binding, not the boxed version
        JSValue iterator = iteratorFunc.call(context, iterable, EMPTY_ARGS);

        if (!(iterator instanceof JSObject iteratorObj)) {
            throw new JSVirtualMachineException("Iterator method must return an object");
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);

        if (!(nextMethod instanceof JSFunction)) {
            String actualType = nextMethod == null ? "null" : nextMethod.getClass().getSimpleName();
            throw new JSVirtualMachineException(
                    "Iterator must have a next method (got " + actualType + ", iterator=" + iteratorObj.getClass().getSimpleName() + ")"
            );
        }

        // Push iterator, next method, and catch offset (0) onto the stack
        valueStack.push(iterator);         // Iterator object
        valueStack.push(nextMethod);       // next() method
        valueStack.push(JSNumber.of(0));  // Catch offset (placeholder)
    }

    private void handleForOfStartOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleForOfStart();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.FOR_OF_START.getSize();
    }

    private void handleGetArg(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.stack[executionContext.sp++] = getArgumentValue(argumentIndex);
        executionContext.pc = pc + Opcode.GET_ARG.getSize();
    }

    private void handleGetArgShort(ExecutionContext executionContext, Opcode opcode) {
        int argumentIndex = switch (opcode) {
            case GET_ARG0 -> 0;
            case GET_ARG1 -> 1;
            case GET_ARG2 -> 2;
            case GET_ARG3 -> 3;
            default -> throw new IllegalStateException("Unexpected short get arg opcode: " + opcode);
        };
        executionContext.stack[executionContext.sp++] = getArgumentValue(argumentIndex);
        executionContext.pc += opcode.getSize();
    }

    private void handleGetArrayEl(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];

        if (objectValue instanceof JSString stringValue && indexValue instanceof JSNumber numberValue) {
            double doubleValue = numberValue.value();
            int index = (int) doubleValue;
            if (index == doubleValue && index >= 0 && index < stringValue.value().length()) {
                stack[sp - 1] = new JSString(String.valueOf(stringValue.value().charAt(index)));
                executionContext.sp = sp;
                executionContext.pc = pc + Opcode.GET_ARRAY_EL.getSize();
                return;
            }
        }

        valueStack.stackTop = sp - 1;
        JSObject targetObject = toObject(objectValue);
        if (targetObject != null) {
            try {
                PropertyKey key = PropertyKey.fromValue(context, indexValue);
                JSValue result = targetObject.get(context, key);
                if (context.hasPendingException()) {
                    pendingException = context.getPendingException();
                    context.clearPendingException();
                    stack[sp - 1] = JSUndefined.INSTANCE;
                } else {
                    if (trackPropertyAccess && !propertyAccessLock) {
                        appendPropertyAccessForArrayIndex(indexValue);
                    }
                    stack[sp - 1] = result;
                }
            } catch (JSVirtualMachineException e) {
                captureVMException(e);
                stack[sp - 1] = JSUndefined.INSTANCE;
            }
        } else {
            resetPropertyAccessTracking();
            stack[sp - 1] = JSUndefined.INSTANCE;
        }
        valueStack.stackTop = sp;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.GET_ARRAY_EL.getSize();
    }

    private void handleGetArrayEl2(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue arrayObjectValue = (JSValue) stack[sp - 1];

        if (arrayObjectValue instanceof JSString stringValue && indexValue instanceof JSNumber numberValue) {
            double doubleValue = numberValue.value();
            int index = (int) doubleValue;
            if (index == doubleValue && index >= 0 && index < stringValue.value().length()) {
                stack[sp++] = new JSString(String.valueOf(stringValue.value().charAt(index)));
                executionContext.sp = sp;
                executionContext.pc = pc + Opcode.GET_ARRAY_EL2.getSize();
                return;
            }
        }

        JSObject targetObject = toObject(arrayObjectValue);
        if (targetObject != null) {
            try {
                PropertyKey key = PropertyKey.fromValue(context, indexValue);
                JSValue result = targetObject.get(context, key);
                if (context.hasPendingException()) {
                    pendingException = context.getPendingException();
                    context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = result;
                }
            } catch (JSVirtualMachineException e) {
                captureVMException(e);
                stack[sp++] = JSUndefined.INSTANCE;
            }
        } else {
            stack[sp++] = JSUndefined.INSTANCE;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.GET_ARRAY_EL2.getSize();
    }

    private void handleGetArrayEl3(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[sp - 1];
        JSValue arrayObjectValue = (JSValue) stack[sp - 2];

        if (!(indexValue instanceof JSNumber || indexValue instanceof JSString || indexValue instanceof JSSymbol)) {
            if (arrayObjectValue.isNullOrUndefined()) {
                throw new JSVirtualMachineException(context.throwTypeError("value has no property"));
            }
            try {
                JSValue convertedIndex = toPropertyKeyValue(indexValue);
                stack[sp - 1] = convertedIndex;
                indexValue = convertedIndex;
            } catch (JSVirtualMachineException e) {
                captureVMException(e);
                executionContext.sp = sp;
                executionContext.pc = pc;
                return;
            }
        }

        JSObject targetObject = toObject(arrayObjectValue);
        if (targetObject == null) {
            throw new JSVirtualMachineException(context.throwTypeError("value has no property"));
        }

        PropertyKey key = PropertyKey.fromValue(context, indexValue);
        JSValue result = targetObject.get(context, key);
        if (context.hasPendingException()) {
            pendingException = context.getPendingException();
            context.clearPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.GET_ARRAY_EL3.getSize();
    }

    private void handleGetField(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue objectValue = (JSValue) executionContext.stack[--executionContext.sp];

        JSObject targetObject = toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(context, PropertyKey.fromString(fieldName));
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                context.clearPendingException();
                executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
            } else {
                if (trackPropertyAccess && !propertyAccessLock) {
                    if (!propertyAccessChain.isEmpty()) {
                        propertyAccessChain.append('.');
                    }
                    propertyAccessChain.append(fieldName);
                }
                executionContext.stack[executionContext.sp++] = result;
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            pendingException = context.throwTypeError("cannot read property '" + fieldName + "' of " + typeName);
            resetPropertyAccessTracking();
            executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
        }
        executionContext.pc = pc + Opcode.GET_FIELD.getSize();
    }

    private void handleGetLength(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue objectValue = (JSValue) executionContext.stack[--executionContext.sp];
        JSObject targetObject = toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(context, PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                context.clearPendingException();
                executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
            } else {
                executionContext.stack[executionContext.sp++] = result;
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            pendingException = context.throwTypeError("cannot read property 'length' of " + typeName);
            executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
        }
        executionContext.pc = pc + Opcode.GET_LENGTH.getSize();
    }

    private void handleGetLoc(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int localIndex = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        handleGetLocAtIndex(executionContext, localIndex, Opcode.GET_LOC.getSize());
    }

    private void handleGetLoc0(ExecutionContext executionContext) {
        handleGetLocAtIndex(executionContext, 0, Opcode.GET_LOC0.getSize());
    }

    private void handleGetLoc1(ExecutionContext executionContext) {
        handleGetLocAtIndex(executionContext, 1, Opcode.GET_LOC1.getSize());
    }

    private void handleGetLoc2(ExecutionContext executionContext) {
        handleGetLocAtIndex(executionContext, 2, Opcode.GET_LOC2.getSize());
    }

    private void handleGetLoc3(ExecutionContext executionContext) {
        handleGetLocAtIndex(executionContext, 3, Opcode.GET_LOC3.getSize());
    }

    private void handleGetLoc8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        handleGetLocAtIndex(executionContext, localIndex, Opcode.GET_LOC8.getSize());
    }

    private void handleGetLocAtIndex(ExecutionContext executionContext, int localIndex, int opcodeSize) {
        JSValue localValue = executionContext.locals[localIndex];
        executionContext.stack[executionContext.sp++] = localValue != null ? localValue : JSUndefined.INSTANCE;
        executionContext.pc += opcodeSize;
    }

    private void handleGetLocCheck(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue localValue = executionContext.frame.getLocals()[localIndex];
        if (isUninitialized(localValue)) {
            throwVariableUninitializedReferenceError();
        }
        executionContext.stack[executionContext.sp++] = localValue;
        executionContext.pc = pc + Opcode.GET_LOC_CHECK.getSize();
    }

    private void handleGetPrivateField(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        JSValue value = JSUndefined.INSTANCE;
        if (objectValue instanceof JSObject object && privateSymbolValue instanceof JSSymbol symbol) {
            value = object.get(PropertyKey.fromSymbol(symbol));
        }

        stack[sp++] = value;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.GET_PRIVATE_FIELD.getSize();
    }

    private void handleGetRefValue(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue propertyValue = (JSValue) stack[sp - 1];
        JSValue objectValue = (JSValue) stack[sp - 2];
        PropertyKey key = PropertyKey.fromValue(context, propertyValue);

        if (objectValue.isUndefined()) {
            String name = key != null ? key.toPropertyString() : "variable";
            pendingException = context.throwReferenceError(name + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + Opcode.GET_REF_VALUE.getSize();
            return;
        }

        JSObject targetObject = toObject(objectValue);
        if (targetObject == null) {
            pendingException = context.throwTypeError("value has no property");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + Opcode.GET_REF_VALUE.getSize();
            return;
        }

        JSValue value;
        if (!targetObject.has(key)) {
            if (context.isStrictMode()) {
                String name = key != null ? key.toPropertyString() : "variable";
                pendingException = context.throwReferenceError(name + " is not defined");
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + Opcode.GET_REF_VALUE.getSize();
                return;
            }
            value = JSUndefined.INSTANCE;
        } else {
            value = targetObject.get(context, key);
        }
        stack[sp++] = value;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.GET_REF_VALUE.getSize();
    }

    private void handleGetSuper(ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue objectValue = (JSValue) stack[sp - 1];
        JSObject object = toObject(objectValue);
        if (object == null) {
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                context.clearPendingException();
            }
            stack[sp - 1] = JSUndefined.INSTANCE;
        } else {
            JSObject prototypeObject = object.getPrototype();
            stack[sp - 1] = prototypeObject != null ? prototypeObject : JSNull.INSTANCE;
        }
        executionContext.pc += Opcode.GET_SUPER.getSize();
    }

    private void handleGetSuperValue(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue keyValue = (JSValue) stack[--sp];
        JSValue superObjectValue = (JSValue) stack[--sp];
        JSValue receiverValue = (JSValue) stack[--sp];

        if (!(superObjectValue instanceof JSObject superObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("super object expected"));
        }

        PropertyKey key = PropertyKey.fromValue(context, keyValue);
        JSValue result;
        if (receiverValue instanceof JSObject receiverObject) {
            result = superObject.getWithReceiver(key, context, receiverObject);
        } else {
            JSObject boxedReceiver = toObject(receiverValue);
            result = boxedReceiver != null
                    ? superObject.getWithReceiver(key, context, boxedReceiver)
                    : superObject.get(context, key);
        }

        if (context.hasPendingException()) {
            pendingException = context.getPendingException();
            context.clearPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.GET_SUPER_VALUE.getSize();
    }

    private void handleGetVar(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        PropertyKey key = PropertyKey.fromString(variableName);
        JSObject globalObject = context.getGlobalObject();
        if (!globalObject.has(key)) {
            pendingException = context.throwReferenceError(variableName + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            JSValue variableValue = globalObject.get(key);
            if (trackPropertyAccess && !propertyAccessLock) {
                resetPropertyAccessTracking();
                propertyAccessChain.append(variableName);
            }
            stack[sp++] = variableValue;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.GET_VAR.getSize();
    }

    private void handleGetVarRef(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.stack[executionContext.sp++] = executionContext.frame.getVarRef(varRefIndex);
        executionContext.pc = pc + Opcode.GET_VAR_REF.getSize();
    }

    private void handleGetVarRefCheck(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = executionContext.frame.getVarRef(varRefIndex);
        if (isUninitialized(value)) {
            throwVariableUninitializedReferenceError();
        }
        executionContext.stack[executionContext.sp++] = value;
        executionContext.pc = pc + Opcode.GET_VAR_REF_CHECK.getSize();
    }

    private void handleGetVarRefShort(ExecutionContext executionContext, Opcode opcode) {
        int varRefIndex = switch (opcode) {
            case GET_VAR_REF0 -> 0;
            case GET_VAR_REF1 -> 1;
            case GET_VAR_REF2 -> 2;
            case GET_VAR_REF3 -> 3;
            default -> throw new IllegalStateException("Unexpected short get var ref opcode: " + opcode);
        };
        executionContext.stack[executionContext.sp++] = executionContext.frame.getVarRef(varRefIndex);
        executionContext.pc += opcode.getSize();
    }

    private void handleGetVarUndef(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.stack[executionContext.sp++] = executionContext.frame.getVarRef(varRefIndex);
        executionContext.pc = pc + Opcode.GET_VAR_UNDEF.getSize();
    }

    private void handleGoto(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int offset = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        executionContext.pc = pc + Opcode.GOTO.getSize() + offset;
    }

    private void handleGoto16(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int offset = (short) (((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF));
        executionContext.pc = pc + Opcode.GOTO16.getSize() + offset;
    }

    private void handleGoto8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.pc = pc + Opcode.GOTO8.getSize() + executionContext.instructions[pc + 1];
    }

    private void handleGt() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Fast path for number comparison
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSBoolean.valueOf(leftNum.value() > rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(context, right, left);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleGtOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() > rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleGt();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.GT.getSize();
    }

    private void handleGte() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Fast path for number comparison
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSBoolean.valueOf(leftNum.value() >= rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(context, right, left) ||
                JSTypeConversions.abstractEquals(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleGteOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() >= rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleGte();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.GTE.getSize();
    }

    private void handleIfFalse(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        JSValue conditionValue = (JSValue) executionContext.stack[--executionContext.sp];
        int offset = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        if (isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + Opcode.IF_FALSE.getSize();
        } else {
            executionContext.pc = pc + Opcode.IF_FALSE.getSize() + offset;
        }
    }

    private void handleIfFalse8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue conditionValue = (JSValue) executionContext.stack[--executionContext.sp];
        if (isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + Opcode.IF_FALSE8.getSize();
        } else {
            executionContext.pc = pc + Opcode.IF_FALSE8.getSize() + executionContext.instructions[pc + 1];
        }
    }

    private void handleIfTrue(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        JSValue conditionValue = (JSValue) executionContext.stack[--executionContext.sp];
        int offset = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        if (isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + Opcode.IF_TRUE.getSize() + offset;
        } else {
            executionContext.pc = pc + Opcode.IF_TRUE.getSize();
        }
    }

    private void handleIfTrue8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue conditionValue = (JSValue) executionContext.stack[--executionContext.sp];
        if (isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + Opcode.IF_TRUE8.getSize() + executionContext.instructions[pc + 1];
        } else {
            executionContext.pc = pc + Opcode.IF_TRUE8.getSize();
        }
    }

    private void handleIn() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (!(right instanceof JSObject jsObj)) {
            throw new JSVirtualMachineException(context.throwTypeError("invalid 'in' operand"));
        }

        PropertyKey key = PropertyKey.fromValue(context, left);
        boolean result = jsObj.has(key);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleInOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleIn();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.IN.getSize();
    }

    private void handleInc() {
        JSValue operand = valueStack.pop();
        valueStack.push(incrementValue(operand, 1));
    }

    private void handleIncDecLoc(ExecutionContext executionContext, int delta) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue localValue = executionContext.locals[localIndex];
        if (localValue == null) {
            localValue = JSUndefined.INSTANCE;
        }
        executionContext.locals[localIndex] = incrementValue(localValue, delta);
        executionContext.pc = pc + Opcode.INC_LOC.getSize();
    }

    private void handleIncLoc(ExecutionContext executionContext) {
        handleIncDecLoc(executionContext, 1);
    }

    private void handleIncOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleInc();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.INC.getSize();
    }

    private void handleInitCtor() {
        JSValue frameNewTarget = currentFrame.getNewTarget();
        if (frameNewTarget.isNullOrUndefined()) {
            // Keep direct VM opcode tests stable when execute() is used without constructor plumbing.
            if (currentFrame.getCaller() == null) {
                JSValue fallbackThis = currentFrame.getThisArg();
                if (fallbackThis instanceof JSObject) {
                    valueStack.push(fallbackThis);
                    return;
                }
            }
            throw new JSVirtualMachineException(context.throwTypeError("class constructors must be invoked with 'new'"));
        }

        // Explicit super(...): APPLY constructor mode left the initialized this value on stack.
        if (valueStack.getStackTop() > currentFrame.getStackBase()) {
            JSValue thisValue = valueStack.pop();
            if (!(thisValue instanceof JSObject jsObject)) {
                throw new JSVirtualMachineException(context.throwTypeError("super() returned non-object"));
            }
            currentFrame.setThisArg(jsObject);
            valueStack.push(jsObject);
            return;
        }

        // Default derived constructor path:
        // constructor(...args) { super(...args); }
        JSFunction currentFunction = currentFrame.getFunction();
        JSObject superConstructorObject = currentFunction.getPrototype();
        if (!(superConstructorObject instanceof JSFunction superConstructor)) {
            throw new JSVirtualMachineException(context.throwTypeError("parent class must be constructor"));
        }

        JSValue superResult = constructFunction(superConstructor, currentFrame.getArguments(), frameNewTarget);
        if (context.hasPendingException()) {
            pendingException = context.getPendingException();
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (!(superResult instanceof JSObject superObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("super() returned non-object"));
        }
        currentFrame.setThisArg(superObject);
        valueStack.push(superObject);
    }

    private void handleInitCtorOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleInitCtor();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.INIT_CTOR.getSize();
    }

    private void handleInitialYield() {
        // Initial yield - generator is being created
        // In QuickJS, this is where generator creation stops and returns the generator object.
        // Per ES spec, parameter defaults are evaluated before INITIAL_YIELD,
        // so errors during parameter initialization (e.g., SyntaxError from eval)
        // are thrown during the generator function call, not during .next().
        if (yieldSkipCount > 0) {
            yieldSkipCount--;
            return;
        }
        // Signal suspension at INITIAL_YIELD
        yieldResult = new YieldResult(YieldResult.Type.INITIAL_YIELD, JSUndefined.INSTANCE);
    }

    private void handleInitialYieldOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleInitialYield();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.INITIAL_YIELD.getSize();
        if (yieldResult != null) {
            requestOpcodeReturnFromExecute(executionContext, JSUndefined.INSTANCE);
        }
    }

    private void handleInsert2(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue topValue = stack[sp - 1];
        stack[sp] = topValue;
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = topValue;
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    private void handleInsert3(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue topValue = stack[sp - 1];
        stack[sp] = topValue;
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = topValue;
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    private void handleInsert4(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue topValue = stack[sp - 1];
        stack[sp] = topValue;
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = stack[sp - 4];
        stack[sp - 4] = topValue;
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    private void handleInstanceof() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();

        // Per ECMAScript spec, right must be an object
        if (!(right instanceof JSObject constructor)) {
            throw new JSVirtualMachineException("Right-hand side of instanceof is not an object");
        }

        JSValue hasInstanceMethod = constructor.get(context, PropertyKey.SYMBOL_HAS_INSTANCE);
        if (context.hasPendingException()) {
            JSValue pendingException = context.getPendingException();
            if (pendingException instanceof JSError jsError) {
                throw new JSVirtualMachineException(jsError);
            }
            throw new JSVirtualMachineException("instanceof check failed");
        }
        if (!(hasInstanceMethod instanceof JSUndefined) && !(hasInstanceMethod instanceof JSNull)) {
            if (!(hasInstanceMethod instanceof JSFunction hasInstanceFunction)) {
                throw new JSVirtualMachineException(context.throwTypeError("@@hasInstance is not callable"));
            }
            JSValue result = hasInstanceFunction.call(context, right, new JSValue[]{left});
            if (context.hasPendingException()) {
                JSValue pendingException = context.getPendingException();
                if (pendingException instanceof JSError jsError) {
                    throw new JSVirtualMachineException(jsError);
                }
                throw new JSVirtualMachineException("instanceof check failed");
            }
            valueStack.push(JSTypeConversions.toBoolean(result) == JSBoolean.TRUE ? JSBoolean.TRUE : JSBoolean.FALSE);
            return;
        }

        boolean callable = right instanceof JSFunction;
        if (right instanceof JSProxy proxy && JSTypeChecking.isFunction(proxy.getTarget())) {
            callable = true;
        }
        if (!callable) {
            throw new JSVirtualMachineException(context.throwTypeError("Right-hand side of instanceof is not callable"));
        }

        valueStack.push(ordinaryHasInstance(right, left) ? JSBoolean.TRUE : JSBoolean.FALSE);
    }

    private void handleInstanceofOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleInstanceof();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.INSTANCEOF.getSize();
    }

    private void handleInvalid(ExecutionContext executionContext) {
        throw new JSVirtualMachineException("Invalid opcode at PC " + executionContext.pc);
    }

    private void handleIsNullOpcode(ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isNull());
        executionContext.pc += Opcode.IS_NULL.getSize();
    }

    private void handleIsUndefinedOpcode(ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isUndefined());
        executionContext.pc += Opcode.IS_UNDEFINED.getSize();
    }

    private void handleIsUndefinedOrNull() {
        JSValue value = valueStack.pop();
        boolean result = value instanceof JSNull || value instanceof JSUndefined;
        valueStack.push(result ? JSBoolean.TRUE : JSBoolean.FALSE);
    }

    private void handleIsUndefinedOrNullOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleIsUndefinedOrNull();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.IS_UNDEFINED_OR_NULL.getSize();
    }

    private void handleIteratorCallOpcode(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int flags = executionContext.bytecode.readU8(pc + 1);
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!(iteratorValue instanceof JSObject iteratorObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("iterator call target must be an object"));
        }

        String methodName = (flags & 1) != 0 ? "throw" : "return";
        JSValue methodValue = (flags & 1) != 0
                ? iteratorObject.get(context, PropertyKey.THROW)
                : iteratorObject.get(context, PropertyKey.RETURN);
        if (context.hasPendingException()) {
            throw new JSVirtualMachineException(
                    context.getPendingException().toString(),
                    context.getPendingException());
        }
        boolean noMethod = methodValue.isNullOrUndefined();
        if (!noMethod) {
            if (!(methodValue instanceof JSFunction method)) {
                throw new JSVirtualMachineException(context.throwTypeError("iterator " + methodName + " is not a function"));
            }
            JSValue callResult = (flags & 2) != 0
                    ? method.call(context, iteratorObject, EMPTY_ARGS)
                    : method.call(context, iteratorObject, new JSValue[]{argumentValue});
            stack[sp - 1] = callResult;
        }
        stack[sp++] = JSBoolean.valueOf(noMethod);
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.ITERATOR_CALL.getSize();
    }

    private void handleIteratorCheckObjectOpcode(ExecutionContext executionContext) {
        JSValue iteratorResult = (JSValue) executionContext.stack[executionContext.sp - 1];
        if (!(iteratorResult instanceof JSObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
        }
        executionContext.pc += Opcode.ITERATOR_CHECK_OBJECT.getSize();
    }

    private void handleIteratorCloseOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue originalPendingException = pendingException;
        sp--;
        sp--;
        JSValue iteratorValue = (JSValue) stack[--sp];
        if (iteratorValue instanceof JSObject iteratorObject && !iteratorValue.isUndefined()) {
            JSValue returnMethodValue = iteratorObject.get(context, PropertyKey.RETURN);
            if (context.hasPendingException()) {
                if (originalPendingException == null) {
                    pendingException = context.getPendingException();
                }
                context.clearPendingException();
                executionContext.sp = sp;
                executionContext.pc += Opcode.ITERATOR_CLOSE.getSize();
                return;
            }
            if (returnMethodValue instanceof JSFunction returnMethod) {
                JSValue closeResult = returnMethod.call(context, iteratorObject, EMPTY_ARGS);
                if (context.hasPendingException()) {
                    if (originalPendingException == null) {
                        pendingException = context.getPendingException();
                    }
                    context.clearPendingException();
                } else if (!(closeResult instanceof JSObject)) {
                    if (originalPendingException == null) {
                        pendingException = context.throwTypeError("iterator result is not an object");
                    }
                }
            } else if (returnMethodValue.isNullOrUndefined()) {
                // No return method.
            } else if (returnMethodValue instanceof JSObject returnObject && returnObject.isHTMLDDA()) {
                // IsHTMLDDA callable edge case; preserve previous no-op behavior.
            } else {
                if (originalPendingException == null) {
                    pendingException = context.throwTypeError("iterator return is not a function");
                }
            }
        }
        executionContext.sp = sp;
        executionContext.pc += Opcode.ITERATOR_CLOSE.getSize();
    }

    private void handleIteratorGetValueDoneOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue iteratorResult = (JSValue) stack[sp - 1];
        if (!(iteratorResult instanceof JSObject iteratorResultObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
        }

        JSValue doneValue = iteratorResultObject.get(PropertyKey.DONE);
        JSValue value = iteratorResultObject.get(PropertyKey.VALUE);
        if (value == null) {
            value = JSUndefined.INSTANCE;
        }
        boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();

        stack[sp - 1] = value;
        stack[sp - 2] = JSNumber.of(0);
        stack[sp++] = JSBoolean.valueOf(done);
        executionContext.sp = sp;
        executionContext.pc += Opcode.ITERATOR_GET_VALUE_DONE.getSize();
    }

    private void handleIteratorNextOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue catchOffsetValue = (JSValue) stack[sp - 2];
        JSValue nextMethodValue = (JSValue) stack[sp - 3];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!(nextMethodValue instanceof JSFunction nextMethod)) {
            throw new JSVirtualMachineException(context.throwTypeError("iterator next is not a function"));
        }
        JSValue nextResult = nextMethod.call(context, iteratorValue, new JSValue[]{argumentValue});
        stack[sp - 1] = nextResult;
        stack[sp - 2] = catchOffsetValue;
        executionContext.pc += Opcode.ITERATOR_NEXT.getSize();
    }

    private void handleLogicalAnd() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Short-circuit: return left if falsy, otherwise right
        if (JSTypeConversions.toBoolean(left) == JSBoolean.FALSE) {
            valueStack.push(left);
        } else {
            valueStack.push(right);
        }
    }

    private void handleLogicalAndOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleLogicalAnd();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.LOGICAL_AND.getSize();
    }

    private void handleLogicalNot() {
        JSValue operand = valueStack.pop();
        boolean result = JSTypeConversions.toBoolean(operand) == JSBoolean.FALSE;
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleLogicalNotOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleLogicalNot();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.LOGICAL_NOT.getSize();
    }

    private void handleLogicalOr() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Short-circuit: return left if truthy, otherwise right
        if (JSTypeConversions.toBoolean(left) == JSBoolean.TRUE) {
            valueStack.push(left);
        } else {
            valueStack.push(right);
        }
    }

    private void handleLogicalOrOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleLogicalOr();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.LOGICAL_OR.getSize();
    }

    private void handleLt() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Fast path for number comparison (avoids toPrimitive/toNumber overhead)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSBoolean.valueOf(leftNum.value() < rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleLtOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() < rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleLt();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.LT.getSize();
    }

    private void handleLte() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Fast path for number comparison (avoids double type conversion)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSBoolean.valueOf(leftNum.value() <= rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(context, left, right) ||
                JSTypeConversions.abstractEquals(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleLteOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() <= rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleLte();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.LTE.getSize();
    }

    private void handleMakeScopedRef(ExecutionContext executionContext, Opcode opcode) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        int refIndex = executionContext.bytecode.readU16(pc + 5);
        String atomName = executionContext.bytecode.getAtoms()[atomIndex];
        JSObject referenceObject = createReferenceObject(opcode, refIndex, atomName);
        executionContext.stack[executionContext.sp++] = referenceObject;
        executionContext.stack[executionContext.sp++] = new JSString(atomName);
        executionContext.pc = pc + opcode.getSize();
    }

    private void handleMakeVarRef(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String atomName = executionContext.bytecode.getAtoms()[atomIndex];
        executionContext.stack[executionContext.sp++] = context.getGlobalObject();
        executionContext.stack[executionContext.sp++] = new JSString(atomName);
        executionContext.pc = pc + Opcode.MAKE_VAR_REF.getSize();
    }

    private void handleMod() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            if (rightBigInt.value().equals(BIGINT_ZERO)) {
                pendingException = context.throwRangeError("Division by zero");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            valueStack.push(new JSBigInt(leftBigInt.value().remainder(rightBigInt.value())));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        valueStack.push(JSNumber.of(leftNumber.value() % rightNumber.value()));
    }

    private void handleModOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleMod();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.MOD.getSize();
    }

    private void handleMul() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            valueStack.push(new JSBigInt(leftBigInt.value().multiply(rightBigInt.value())));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        valueStack.push(JSNumber.of(leftNumber.value() * rightNumber.value()));
    }

    private void handleMulOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() * rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleMul();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.MUL.getSize();
    }

    private void handleNeg() {
        JSValue operand = valueStack.pop();
        JSValue numeric = toNumericValue(operand);
        if (numeric instanceof JSBigInt bigInt) {
            valueStack.push(new JSBigInt(bigInt.value().negate()));
            return;
        }
        valueStack.push(JSNumber.of(-((JSNumber) numeric).value()));
    }

    private void handleNegOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleNeg();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.NEG.getSize();
    }

    private void handleNeq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = !JSTypeConversions.abstractEquals(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleNeqOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleNeq();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.NEQ.getSize();
    }

    private void handleNip(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        stack[sp - 2] = stack[sp - 1];
        executionContext.sp = sp - 1;
        executionContext.pc += 1;
    }

    private void handleNipCatchOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue returnValue = (JSValue) stack[--sp];
        boolean foundCatchMarker = false;
        while (sp > executionContext.frameStackBase) {
            JSStackValue stackValue = stack[--sp];
            if (stackValue instanceof JSCatchOffset) {
                foundCatchMarker = true;
                break;
            }
        }
        if (!foundCatchMarker) {
            throw new JSVirtualMachineException(context.throwError("nip_catch"));
        }
        stack[sp++] = returnValue;
        executionContext.sp = sp;
        executionContext.pc += Opcode.NIP_CATCH.getSize();
    }

    private void handleNop(ExecutionContext executionContext) {
        executionContext.pc += Opcode.NOP.getSize();
    }

    private void handleNot() {
        JSValue operand = valueStack.pop();
        JSValue numeric = toNumericValue(operand);
        if (numeric instanceof JSBigInt bigInt) {
            valueStack.push(new JSBigInt(bigInt.value().not()));
            return;
        }
        int result = ~JSTypeConversions.toInt32(context, numeric);
        valueStack.push(JSNumber.of(result));
    }

    private void handleNotOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleNot();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.NOT.getSize();
    }

    private void handleNull(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNull.INSTANCE;
        executionContext.pc += 1;
    }

    private void handleNullishCoalesce() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Return right if left is null or undefined
        if (left instanceof JSNull || left instanceof JSUndefined) {
            valueStack.push(right);
        } else {
            valueStack.push(left);
        }
    }

    private void handleNullishCoalesceOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleNullishCoalesce();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.NULLISH_COALESCE.getSize();
    }

    private void handleObjectNew(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = context.createJSObject();
        executionContext.pc += Opcode.OBJECT.getSize();
    }

    private void handleOr() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() | (int) rightNum.value()));
            return;
        }
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            valueStack.push(new JSBigInt(leftBigInt.value().or(rightBigInt.value())));
            return;
        }
        int result = JSTypeConversions.toInt32(context, pair.left()) | JSTypeConversions.toInt32(context, pair.right());
        valueStack.push(JSNumber.of(result));
    }

    private void handleOrOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleOr();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.OR.getSize();
    }

    private PendingExceptionAction handlePendingExceptionForExecute(ExecutionContext executionContext) {
        if (pendingException == null) {
            return PendingExceptionAction.NONE;
        }

        JSValue exception = pendingException;
        pendingException = null;

        boolean foundHandler = false;
        while (valueStack.stackTop > executionContext.frameStackBase) {
            JSStackValue stackValue = executionContext.stack[--valueStack.stackTop];
            if (stackValue instanceof JSCatchOffset catchOffset) {
                if (generatorForceReturn && !catchOffset.isFinally()) {
                    continue;
                }
                executionContext.stack[valueStack.stackTop++] = exception;
                executionContext.pc = catchOffset.offset();
                foundHandler = true;
                context.clearPendingException();
                break;
            }
        }
        executionContext.sp = valueStack.stackTop;

        if (foundHandler) {
            return PendingExceptionAction.CONTINUE;
        }

        if (generatorForceReturn) {
            generatorForceReturn = false;
            restoreExecuteCallerState(
                    executionContext.restoreStackTop,
                    executionContext.previousFrame,
                    executionContext.savedStrictMode);
            context.clearPendingException();
            executionContext.returnValue = generatorReturnValue;
            return PendingExceptionAction.RETURN;
        }

        restoreExecuteCallerState(
                executionContext.restoreStackTop,
                executionContext.previousFrame,
                executionContext.savedStrictMode);
        if (exception instanceof JSError jsError) {
            throw new JSVirtualMachineException(jsError);
        }
        String exceptionMessage = safeExceptionToString(context, exception);
        throw new JSVirtualMachineException("Unhandled exception: " + exceptionMessage, exception);
    }

    private void handlePerm3(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 3];
        stack[sp - 3] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
        executionContext.pc += 1;
    }

    private void handlePerm4(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 4];
        stack[sp - 4] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = firstValue;
        executionContext.pc += 1;
    }

    private void handlePerm5(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 5];
        stack[sp - 5] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = stack[sp - 4];
        stack[sp - 4] = firstValue;
        executionContext.pc += 1;
    }

    private void handlePlus() {
        JSValue operand = valueStack.pop();
        JSNumber result = JSTypeConversions.toNumber(context, operand);
        capturePendingException();
        valueStack.push(result);
    }

    private void handlePlusOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handlePlus();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.PLUS.getSize();
    }

    private void handlePostDec() {
        // POST_DEC: [value] -> [old_value, new_value]
        // Takes value on top, pushes old value then new value
        JSValue operand = valueStack.pop();
        JSValue oldValue = toNumericValue(operand);
        valueStack.push(oldValue);
        if (oldValue instanceof JSBigInt bigInt) {
            valueStack.push(new JSBigInt(bigInt.value().subtract(BIGINT_ONE)));
            return;
        }
        valueStack.push(JSNumber.of(((JSNumber) oldValue).value() - 1));
    }

    private void handlePostDecOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handlePostDec();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.POST_DEC.getSize();
    }

    private void handlePostInc() {
        // POST_INC: [value] -> [old_value, new_value]
        // Takes value on top, pushes old value then new value
        JSValue operand = valueStack.pop();
        JSValue oldValue = toNumericValue(operand);
        valueStack.push(oldValue);
        if (oldValue instanceof JSBigInt bigInt) {
            valueStack.push(new JSBigInt(bigInt.value().add(BIGINT_ONE)));
            return;
        }
        valueStack.push(JSNumber.of(((JSNumber) oldValue).value() + 1));
    }

    private void handlePostIncOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handlePostInc();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.POST_INC.getSize();
    }

    private void handlePrivateIn() {
        JSValue privateField = valueStack.pop();
        JSValue object = valueStack.pop();

        if (!(object instanceof JSObject jsObj)) {
            throw new JSVirtualMachineException(context.throwTypeError("invalid 'in' operand"));
        }

        boolean result = false;
        if (privateField instanceof JSSymbol symbol) {
            result = jsObj.has(PropertyKey.fromSymbol(symbol));
        }

        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handlePrivateInOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handlePrivateIn();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.PRIVATE_IN.getSize();
    }

    private void handlePrivateSymbol(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int fieldNameAtomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[fieldNameAtomIndex];
        executionContext.stack[executionContext.sp++] = new JSSymbol(fieldName);
        executionContext.pc = pc + Opcode.PRIVATE_SYMBOL.getSize();
    }

    private void handlePush0(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(0);
        executionContext.pc += 1;
    }

    private void handlePush1(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(1);
        executionContext.pc += 1;
    }

    private void handlePush2(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(2);
        executionContext.pc += 1;
    }

    private void handlePush3(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(3);
        executionContext.pc += 1;
    }

    private void handlePush4(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(4);
        executionContext.pc += 1;
    }

    private void handlePush5(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(5);
        executionContext.pc += 1;
    }

    private void handlePush6(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(6);
        executionContext.pc += 1;
    }

    private void handlePush7(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(7);
        executionContext.pc += 1;
    }

    private void handlePushArray(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue element = (JSValue) stack[--sp];
        JSValue arrayValue = (JSValue) stack[sp - 1];
        if (arrayValue instanceof JSArray jsArray) {
            jsArray.push(element);
        }
        executionContext.sp = sp;
        executionContext.pc += Opcode.PUSH_ARRAY.getSize();
    }

    private void handlePushAtomValue(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int atomIndex = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        executionContext.stack[executionContext.sp++] = new JSString(executionContext.bytecode.getAtoms()[atomIndex]);
        executionContext.pc = pc + Opcode.PUSH_ATOM_VALUE.getSize();
    }

    private void handlePushBigintI32(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = new JSBigInt(executionContext.bytecode.readI32(executionContext.pc + 1));
        executionContext.pc += Opcode.PUSH_BIGINT_I32.getSize();
    }

    private void handlePushConst(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int constIndex = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        JSValue constantValue = executionContext.bytecode.getConstants()[constIndex];
        initializeConstantValueIfNeeded(constantValue);
        executionContext.stack[executionContext.sp++] = constantValue;
        executionContext.pc = pc + Opcode.PUSH_CONST.getSize();
    }

    private void handlePushConst8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int constIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue constantValue = executionContext.bytecode.getConstants()[constIndex];
        initializeConstantValueIfNeeded(constantValue);
        executionContext.stack[executionContext.sp++] = constantValue;
        executionContext.pc = pc + Opcode.PUSH_CONST8.getSize();
    }

    private void handlePushEmptyString(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = new JSString("");
        executionContext.pc += 1;
    }

    private void handlePushFalse(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSBoolean.FALSE;
        executionContext.pc += 1;
    }

    private void handlePushI16(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        executionContext.stack[executionContext.sp++] =
                JSNumber.of((short) (((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF)));
        executionContext.pc = pc + 3;
    }

    private void handlePushI32(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int intValue = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        executionContext.stack[executionContext.sp++] = JSNumber.of(intValue);
        executionContext.pc = pc + 5;
    }

    private void handlePushI8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.stack[executionContext.sp++] = JSNumber.of(executionContext.instructions[pc + 1]);
        executionContext.pc = pc + 2;
    }

    private void handlePushMinus1(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(-1);
        executionContext.pc += 1;
    }

    private void handlePushThis(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = executionContext.frame.getThisArg();
        executionContext.pc += 1;
    }

    private void handlePushTrue(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSBoolean.TRUE;
        executionContext.pc += 1;
    }

    private void handlePutArg(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue argumentValue = (JSValue) executionContext.stack[--executionContext.sp];
        setArgumentValue(argumentIndex, argumentValue);
        executionContext.pc = pc + Opcode.PUT_ARG.getSize();
    }

    private void handlePutArgShort(ExecutionContext executionContext, Opcode opcode) {
        int argumentIndex = switch (opcode) {
            case PUT_ARG0 -> 0;
            case PUT_ARG1 -> 1;
            case PUT_ARG2 -> 2;
            case PUT_ARG3 -> 3;
            default -> throw new IllegalStateException("Unexpected short put arg opcode: " + opcode);
        };
        JSValue argumentValue = (JSValue) executionContext.stack[--executionContext.sp];
        setArgumentValue(argumentIndex, argumentValue);
        executionContext.pc += opcode.getSize();
    }

    private void handlePutArrayEl(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue assignedValue = (JSValue) stack[--sp];
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject jsObject) {
            try {
                PropertyKey key = PropertyKey.fromValue(context, indexValue);
                jsObject.set(context, key, assignedValue);
                if (context.hasPendingException()) {
                    pendingException = context.getPendingException();
                    context.clearPendingException();
                }
            } catch (JSVirtualMachineException e) {
                capturePendingExceptionFromVmOrContext(e);
            }
        } else if (objectValue instanceof JSNull || objectValue instanceof JSUndefined) {
            PropertyKey key = PropertyKey.fromValue(context, indexValue);
            context.throwTypeError("cannot set property '" + key + "' of "
                    + (objectValue instanceof JSNull ? "null" : "undefined"));
            pendingException = context.getPendingException();
            context.clearPendingException();
        } else {
            JSObject boxedObject = toObject(objectValue);
            if (boxedObject != null) {
                try {
                    PropertyKey key = PropertyKey.fromValue(context, indexValue);
                    boxedObject.set(context, key, assignedValue);
                    if (context.hasPendingException()) {
                        pendingException = context.getPendingException();
                        context.clearPendingException();
                    }
                } catch (JSVirtualMachineException e) {
                    capturePendingExceptionFromVmOrContext(e);
                }
            }
        }

        stack[sp++] = assignedValue;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.PUT_ARRAY_EL.getSize();
    }

    private void handlePutField(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue objectValue = (JSValue) executionContext.stack[--executionContext.sp];
        JSValue fieldValue = (JSValue) executionContext.stack[executionContext.sp - 1];
        PropertyKey propertyKey = PropertyKey.fromString(fieldName);

        if (objectValue instanceof JSObject jsObject) {
            try {
                jsObject.set(context, propertyKey, fieldValue);
                if (context.hasPendingException()) {
                    pendingException = context.getPendingException();
                    context.clearPendingException();
                }
            } catch (JSVirtualMachineException e) {
                capturePendingExceptionFromVmOrContext(e);
            }
        } else if (objectValue instanceof JSNull || objectValue instanceof JSUndefined) {
            context.throwTypeError("cannot set property '" + fieldName + "' of "
                    + (objectValue instanceof JSNull ? "null" : "undefined"));
            pendingException = context.getPendingException();
            context.clearPendingException();
        } else {
            JSObject boxedObject = toObject(objectValue);
            if (boxedObject != null) {
                try {
                    boxedObject.set(context, propertyKey, fieldValue);
                    if (context.hasPendingException()) {
                        pendingException = context.getPendingException();
                        context.clearPendingException();
                    }
                } catch (JSVirtualMachineException e) {
                    capturePendingExceptionFromVmOrContext(e);
                }
            }
        }
        executionContext.pc = pc + Opcode.PUT_FIELD.getSize();
    }

    private void handlePutLoc(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int localIndex = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        handlePutLocAtIndex(executionContext, localIndex, Opcode.PUT_LOC.getSize());
    }

    private void handlePutLoc0(ExecutionContext executionContext) {
        handlePutLocAtIndex(executionContext, 0, Opcode.PUT_LOC0.getSize());
    }

    private void handlePutLoc1(ExecutionContext executionContext) {
        handlePutLocAtIndex(executionContext, 1, Opcode.PUT_LOC1.getSize());
    }

    private void handlePutLoc2(ExecutionContext executionContext) {
        handlePutLocAtIndex(executionContext, 2, Opcode.PUT_LOC2.getSize());
    }

    private void handlePutLoc3(ExecutionContext executionContext) {
        handlePutLocAtIndex(executionContext, 3, Opcode.PUT_LOC3.getSize());
    }

    private void handlePutLoc8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        handlePutLocAtIndex(executionContext, localIndex, Opcode.PUT_LOC8.getSize());
    }

    private void handlePutLocAtIndex(ExecutionContext executionContext, int localIndex, int opcodeSize) {
        executionContext.locals[localIndex] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc += opcodeSize;
    }

    private void handlePutLocCheck(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (isUninitialized(localValues[localIndex])) {
            throwVariableUninitializedReferenceError();
        }
        localValues[localIndex] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc = pc + Opcode.PUT_LOC_CHECK.getSize();
    }

    private void handlePutLocCheckInit(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (!isUninitialized(localValues[localIndex])) {
            throw new JSVirtualMachineException(context.throwReferenceError("'this' can be initialized only once"));
        }
        localValues[localIndex] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc = pc + Opcode.PUT_LOC_CHECK_INIT.getSize();
    }

    private void handlePutPrivateField(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue value = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject object && privateSymbolValue instanceof JSSymbol symbol) {
            object.set(PropertyKey.fromSymbol(symbol), value);
        }

        stack[sp++] = value;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.PUT_PRIVATE_FIELD.getSize();
    }

    private void handlePutRefValue(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue setValue = (JSValue) stack[--sp];
        JSValue propertyValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];
        PropertyKey key = PropertyKey.fromValue(context, propertyValue);

        if (objectValue.isUndefined()) {
            if (context.isStrictMode()) {
                String name = key != null ? key.toPropertyString() : "variable";
                pendingException = context.throwReferenceError(name + " is not defined");
                executionContext.sp = sp;
                executionContext.pc = pc + Opcode.PUT_REF_VALUE.getSize();
                return;
            }
            objectValue = context.getGlobalObject();
        }

        JSObject targetObject = toObject(objectValue);
        if (targetObject == null) {
            pendingException = context.throwTypeError("value has no property");
            executionContext.sp = sp;
            executionContext.pc = pc + Opcode.PUT_REF_VALUE.getSize();
            return;
        }

        if (!targetObject.has(key) && context.isStrictMode()) {
            String name = key != null ? key.toPropertyString() : "variable";
            pendingException = context.throwReferenceError(name + " is not defined");
            executionContext.sp = sp;
            executionContext.pc = pc + Opcode.PUT_REF_VALUE.getSize();
            return;
        }

        targetObject.set(context, key, setValue);
        if (context.hasPendingException()) {
            pendingException = context.getPendingException();
            context.clearPendingException();
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.PUT_REF_VALUE.getSize();
    }

    private void handlePutSuperValue(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue keyValue = (JSValue) stack[--sp];
        JSValue superObjectValue = (JSValue) stack[--sp];
        JSValue receiverValue = (JSValue) stack[--sp];
        JSValue assignedValue = (JSValue) stack[--sp];

        if (!(superObjectValue instanceof JSObject superObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("super object expected"));
        }

        PropertyKey key = PropertyKey.fromValue(context, keyValue);
        if (receiverValue instanceof JSObject receiverObject) {
            superObject.set(context, key, assignedValue, receiverObject);
        } else {
            JSObject boxedReceiver = toObject(receiverValue);
            if (boxedReceiver != null) {
                superObject.set(context, key, assignedValue, boxedReceiver);
            } else {
                superObject.set(context, key, assignedValue);
            }
        }

        if (context.hasPendingException()) {
            pendingException = context.getPendingException();
            context.clearPendingException();
        }
        stack[sp++] = assignedValue;
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.PUT_SUPER_VALUE.getSize();
    }

    private void handlePutVar(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue value = (JSValue) executionContext.stack[--executionContext.sp];
        context.getGlobalObject().set(PropertyKey.fromString(variableName), value);
        executionContext.pc = pc + Opcode.PUT_VAR.getSize();
    }

    private void handlePutVarInit(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.frame.setVarRef(varRefIndex, value);
        executionContext.pc = pc + Opcode.PUT_VAR_INIT.getSize();
    }

    private void handlePutVarRef(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.frame.setVarRef(varRefIndex, value);
        executionContext.pc = pc + Opcode.PUT_VAR_REF.getSize();
    }

    private void handlePutVarRefCheck(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        if (isUninitialized(executionContext.frame.getVarRef(varRefIndex))) {
            throwVariableUninitializedReferenceError();
        }
        executionContext.frame.setVarRef(varRefIndex, (JSValue) executionContext.stack[--executionContext.sp]);
        executionContext.pc = pc + Opcode.PUT_VAR_REF_CHECK.getSize();
    }

    private void handlePutVarRefCheckInit(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        if (!isUninitialized(executionContext.frame.getVarRef(varRefIndex))) {
            throw new JSVirtualMachineException(context.throwReferenceError("variable is already initialized"));
        }
        executionContext.frame.setVarRef(varRefIndex, (JSValue) executionContext.stack[--executionContext.sp]);
        executionContext.pc = pc + Opcode.PUT_VAR_REF_CHECK_INIT.getSize();
    }

    private void handlePutVarRefShort(ExecutionContext executionContext, Opcode opcode) {
        int varRefIndex = switch (opcode) {
            case PUT_VAR_REF0 -> 0;
            case PUT_VAR_REF1 -> 1;
            case PUT_VAR_REF2 -> 2;
            case PUT_VAR_REF3 -> 3;
            default -> throw new IllegalStateException("Unexpected short put var ref opcode: " + opcode);
        };
        JSValue value = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.frame.setVarRef(varRefIndex, value);
        executionContext.pc += opcode.getSize();
    }

    private void handleRest(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int firstRestArgumentIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] functionArguments = executionContext.frame.getArguments();
        int argumentCount = functionArguments.length;
        int restStartIndex = Math.min(firstRestArgumentIndex, argumentCount);
        int restCount = argumentCount - restStartIndex;
        JSValue[] restArguments = new JSValue[restCount];
        System.arraycopy(functionArguments, restStartIndex, restArguments, 0, restCount);
        JSArray restArray = context.createJSArray(restArguments);
        executionContext.stack[executionContext.sp++] = restArray;
        executionContext.pc = pc + Opcode.REST.getSize();
    }

    private void handleReturnAsyncOpcode(ExecutionContext executionContext) {
        JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
        finalizeExecuteReturn(executionContext);
        executionContext.returnValue = returnValue;
        executionContext.opcodeRequestedReturn = true;
    }

    private void handleReturnOpcode(ExecutionContext executionContext) {
        JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
        lastConstructorThisArg = executionContext.frame.getThisArg();
        finalizeExecuteReturn(executionContext);
        executionContext.returnValue = returnValue;
        executionContext.opcodeRequestedReturn = true;
    }

    private void handleReturnUndefOpcode(ExecutionContext executionContext) {
        lastConstructorThisArg = executionContext.frame.getThisArg();
        finalizeExecuteReturn(executionContext);
        executionContext.returnValue = JSUndefined.INSTANCE;
        executionContext.opcodeRequestedReturn = true;
    }

    private void handleRot3l(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 3];
        stack[sp - 3] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 1];
        stack[sp - 1] = firstValue;
        executionContext.pc += 1;
    }

    private void handleRot3r(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue thirdValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = thirdValue;
        executionContext.pc += 1;
    }

    private void handleSar() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() >> ((int) rightNum.value() & 0x1F)));
            return;
        }
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            valueStack.push(shiftBigInt((JSBigInt) pair.left(), (JSBigInt) pair.right(), false));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(context, pair.left());
        int rightInt = JSTypeConversions.toInt32(context, pair.right());
        valueStack.push(JSNumber.of(leftInt >> (rightInt & 0x1F)));
    }

    private void handleSarOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(((int) leftNumber.value()) >> (((int) rightNumber.value()) & 0x1F));
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleSar();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.SAR.getSize();
    }

    private void handleSetArg(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        setArgumentValue(argumentIndex, (JSValue) executionContext.stack[executionContext.sp - 1]);
        executionContext.pc = pc + Opcode.SET_ARG.getSize();
    }

    private void handleSetArgShort(ExecutionContext executionContext, Opcode opcode) {
        int argumentIndex = switch (opcode) {
            case SET_ARG0 -> 0;
            case SET_ARG1 -> 1;
            case SET_ARG2 -> 2;
            case SET_ARG3 -> 3;
            default -> throw new IllegalStateException("Unexpected short set arg opcode: " + opcode);
        };
        setArgumentValue(argumentIndex, (JSValue) executionContext.stack[executionContext.sp - 1]);
        executionContext.pc += opcode.getSize();
    }

    private void handleSetHomeObject(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue homeObjectValue = (JSValue) stack[sp - 2];
        JSValue methodValue = (JSValue) stack[sp - 1];
        if (methodValue instanceof JSFunction methodFunction && homeObjectValue instanceof JSObject homeObject) {
            methodFunction.setHomeObject(homeObject);
        }
        executionContext.pc += Opcode.SET_HOME_OBJECT.getSize();
    }

    private void handleSetLoc(ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int localIndex = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        handleSetLocAtIndex(executionContext, localIndex, Opcode.SET_LOC.getSize());
    }

    private void handleSetLoc0(ExecutionContext executionContext) {
        handleSetLocAtIndex(executionContext, 0, Opcode.SET_LOC0.getSize());
    }

    private void handleSetLoc1(ExecutionContext executionContext) {
        handleSetLocAtIndex(executionContext, 1, Opcode.SET_LOC1.getSize());
    }

    private void handleSetLoc2(ExecutionContext executionContext) {
        handleSetLocAtIndex(executionContext, 2, Opcode.SET_LOC2.getSize());
    }

    private void handleSetLoc3(ExecutionContext executionContext) {
        handleSetLocAtIndex(executionContext, 3, Opcode.SET_LOC3.getSize());
    }

    private void handleSetLoc8(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        handleSetLocAtIndex(executionContext, localIndex, Opcode.SET_LOC8.getSize());
    }

    private void handleSetLocAtIndex(ExecutionContext executionContext, int localIndex, int opcodeSize) {
        executionContext.locals[localIndex] = (JSValue) executionContext.stack[executionContext.sp - 1];
        executionContext.pc += opcodeSize;
    }

    private void handleSetLocCheck(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (isUninitialized(localValues[localIndex])) {
            throwVariableUninitializedReferenceError();
        }
        localValues[localIndex] = (JSValue) executionContext.stack[executionContext.sp - 1];
        executionContext.pc = pc + Opcode.SET_LOC_CHECK.getSize();
    }

    private void handleSetLocUninitialized(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.frame.getLocals()[localIndex] = UNINITIALIZED_MARKER;
        executionContext.pc = pc + Opcode.SET_LOC_UNINITIALIZED.getSize();
    }

    private void handleSetName(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String name = executionContext.bytecode.getAtoms()[atomIndex];
        setObjectName((JSValue) executionContext.stack[executionContext.sp - 1], new JSString(name));
        executionContext.pc = pc + Opcode.SET_NAME.getSize();
    }

    private void handleSetNameComputed(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue nameValue = (JSValue) stack[sp - 2];
        setObjectName((JSValue) stack[sp - 1], getComputedNameString(nameValue));
        executionContext.pc += Opcode.SET_NAME_COMPUTED.getSize();
    }

    private void handleSetProto(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue prototypeValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];
        if (objectValue instanceof JSObject object) {
            if (prototypeValue instanceof JSObject prototypeObject) {
                object.setPrototype(prototypeObject);
            } else if (prototypeValue.isNull()) {
                object.setPrototype(null);
            }
        }
        executionContext.sp = sp;
        executionContext.pc += Opcode.SET_PROTO.getSize();
    }

    private void handleSetVar(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue value = (JSValue) executionContext.stack[executionContext.sp - 1];
        PropertyKey variableKey = PropertyKey.fromString(variableName);
        JSObject globalObject = context.getGlobalObject();
        if (context.isStrictMode() && !globalObject.has(variableKey)) {
            throw new JSVirtualMachineException(context.throwReferenceError(variableName + " is not defined"));
        }
        globalObject.set(variableKey, value);
        executionContext.pc = pc + Opcode.SET_VAR.getSize();
    }

    private void handleSetVarRef(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.frame.setVarRef(varRefIndex, (JSValue) executionContext.stack[executionContext.sp - 1]);
        executionContext.pc = pc + Opcode.SET_VAR_REF.getSize();
    }

    private void handleSetVarRefShort(ExecutionContext executionContext, Opcode opcode) {
        int varRefIndex = switch (opcode) {
            case SET_VAR_REF0 -> 0;
            case SET_VAR_REF1 -> 1;
            case SET_VAR_REF2 -> 2;
            case SET_VAR_REF3 -> 3;
            default -> throw new IllegalStateException("Unexpected short set var ref opcode: " + opcode);
        };
        executionContext.frame.setVarRef(varRefIndex, (JSValue) executionContext.stack[executionContext.sp - 1]);
        executionContext.pc += opcode.getSize();
    }

    private void handleShl() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() << ((int) rightNum.value() & 0x1F)));
            return;
        }
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            valueStack.push(shiftBigInt((JSBigInt) pair.left(), (JSBigInt) pair.right(), true));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(context, pair.left());
        int rightInt = JSTypeConversions.toInt32(context, pair.right());
        valueStack.push(JSNumber.of(leftInt << (rightInt & 0x1F)));
    }

    private void handleShlOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(((int) leftNumber.value()) << (((int) rightNumber.value()) & 0x1F));
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleShl();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.SHL.getSize();
    }

    private void handleShr() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        JSValue leftPrimitive = JSTypeConversions.toPrimitive(context, left, JSTypeConversions.PreferredType.NUMBER);
        JSValue rightPrimitive = JSTypeConversions.toPrimitive(context, right, JSTypeConversions.PreferredType.NUMBER);
        if (leftPrimitive instanceof JSBigInt || rightPrimitive instanceof JSBigInt) {
            pendingException = context.throwTypeError("BigInts do not support >>>");
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        JSValue leftNumeric = JSTypeConversions.toNumber(context, leftPrimitive);
        JSValue rightNumeric = JSTypeConversions.toNumber(context, rightPrimitive);
        if (leftNumeric instanceof JSNumber leftNum && rightNumeric instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of(((int) leftNum.value() >>> ((int) rightNum.value() & 0x1F)) & 0xFFFFFFFFL));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(context, leftNumeric);
        int rightInt = JSTypeConversions.toInt32(context, rightNumeric);
        valueStack.push(JSNumber.of((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
    }

    private void handleShrOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleShr();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.SHR.getSize();
    }

    private void handleSpecialObject(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int objectType = executionContext.bytecode.readU8(pc + 1);
        JSValue specialObject = createSpecialObject(objectType, executionContext.frame);
        executionContext.stack[executionContext.sp++] = specialObject;
        executionContext.pc = pc + Opcode.SPECIAL_OBJECT.getSize();
    }

    private void handleStrictEq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleStrictEqOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleStrictEq();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.STRICT_EQ.getSize();
    }

    private void handleStrictNeq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = !JSTypeConversions.strictEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleStrictNeqOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleStrictNeq();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.STRICT_NEQ.getSize();
    }

    private void handleSub() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            valueStack.push(new JSBigInt(leftBigInt.value().subtract(rightBigInt.value())));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        valueStack.push(JSNumber.of(leftNumber.value() - rightNumber.value()));
    }

    private void handleSubOpcode(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() - rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            valueStack.stackTop = sp;
            handleSub();
            executionContext.sp = valueStack.stackTop;
        }
        executionContext.pc += Opcode.SUB.getSize();
    }

    private void handleSwap(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
        propertyAccessLock = true;
        executionContext.pc += 1;
    }

    private void handleSwap2(ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue firstPairLeft = stack[sp - 4];
        JSStackValue firstPairRight = stack[sp - 3];
        stack[sp - 4] = stack[sp - 2];
        stack[sp - 3] = stack[sp - 1];
        stack[sp - 2] = firstPairLeft;
        stack[sp - 1] = firstPairRight;
        executionContext.pc += 1;
    }

    private void handleThrowErrorOpcode(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int throwAtom = executionContext.bytecode.readU32(pc + 1);
        int throwType = executionContext.bytecode.readU8(pc + 5);
        String throwName = executionContext.bytecode.getAtoms()[throwAtom];
        switch (throwType) {
            case 0 -> context.throwTypeError("'" + throwName + "' is read-only");
            case 1 -> context.throwError("SyntaxError: redeclaration of '" + throwName + "'");
            case 2 -> context.throwReferenceError(throwName + " is not initialized");
            case 3 -> context.throwReferenceError("unsupported reference to 'super'");
            case 4 -> context.throwTypeError("iterator does not have a throw method");
            case 5 -> context.throwReferenceError(throwName);
            default -> throw new JSVirtualMachineException("invalid throw_error type: " + throwType);
        }
        pendingException = context.getPendingException();
        // PC intentionally unchanged. Exception unwinding loop handles control transfer.
    }

    private void handleThrowOpcode(ExecutionContext executionContext) {
        JSValue exceptionValue = (JSValue) executionContext.stack[--executionContext.sp];
        pendingException = exceptionValue;
        context.setPendingException(exceptionValue);
        // PC intentionally unchanged. Exception unwinding loop handles control transfer.
    }

    private void handleToPropKey(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rawKey = (JSValue) stack[--sp];
        try {
            stack[sp++] = toPropertyKeyValue(rawKey);
        } catch (JSVirtualMachineException e) {
            captureVMException(e);
            executionContext.sp = sp;
            executionContext.pc = pc;
            return;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.TO_PROPKEY.getSize();
    }

    private void handleToPropKey2(ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rawKey = (JSValue) stack[--sp];
        JSValue baseObject = (JSValue) stack[--sp];
        stack[sp++] = baseObject;
        try {
            stack[sp++] = toPropertyKeyValue(rawKey);
        } catch (JSVirtualMachineException e) {
            sp--;
            captureVMException(e);
            executionContext.sp = sp;
            executionContext.pc = pc;
            return;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + Opcode.TO_PROPKEY2.getSize();
    }

    private void handleToStringOpcode(ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        if (!(value instanceof JSString)) {
            stack[sp - 1] = JSTypeConversions.toString(context, value);
        }
        executionContext.pc += Opcode.TO_STRING.getSize();
    }

    private void handleTypeof() {
        JSValue operand = valueStack.pop();
        String type = JSTypeChecking.typeof(operand);
        valueStack.push(new JSString(type));
    }

    private void handleTypeofIsFunctionOpcode(ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf("function".equals(JSTypeChecking.typeof(value)));
        executionContext.pc += Opcode.TYPEOF_IS_FUNCTION.getSize();
    }

    private void handleTypeofIsUndefinedOpcode(ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf("undefined".equals(JSTypeChecking.typeof(value)));
        executionContext.pc += Opcode.TYPEOF_IS_UNDEFINED.getSize();
    }

    private void handleTypeofOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleTypeof();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.TYPEOF.getSize();
    }

    private void handleUndefined(ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
        executionContext.pc += 1;
    }

    private void handleXor() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() ^ (int) rightNum.value()));
            return;
        }
        NumericPair pair = numericPair(left, right);
        if (pair == null) {
            valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            valueStack.push(new JSBigInt(leftBigInt.value().xor(rightBigInt.value())));
            return;
        }
        int result = JSTypeConversions.toInt32(context, pair.left()) ^ JSTypeConversions.toInt32(context, pair.right());
        valueStack.push(JSNumber.of(result));
    }

    private void handleXorOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleXor();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.XOR.getSize();
    }

    private void handleYield() {
        // Check if we should skip this yield (resuming from later point)
        if (yieldSkipCount > 0) {
            yieldSkipCount--;
            JSValue yieldedValue = valueStack.pop();
            JSGeneratorState.ResumeRecord resumeRecord = generatorResumeIndex < generatorResumeRecords.size()
                    ? generatorResumeRecords.get(generatorResumeIndex++)
                    : null;
            if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
                pendingException = resumeRecord.value();
                context.setPendingException(resumeRecord.value());
            } else if (resumeRecord != null) {
                valueStack.push(resumeRecord.value());
            } else {
                // If resume data is missing, default to undefined to preserve stack shape.
                valueStack.push(JSUndefined.INSTANCE);
            }
            // Don't yield - just continue execution from the resumed generator state.
            return;
        }

        // At the target yield - check for RETURN/THROW resume records (replay mode)
        JSGeneratorState.ResumeRecord resumeRecord = generatorResumeIndex < generatorResumeRecords.size()
                ? generatorResumeRecords.get(generatorResumeIndex)
                : null;
        if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
            generatorResumeIndex++;
            valueStack.pop(); // Pop the yielded value
            // Trigger force return through finally handlers
            generatorForceReturn = true;
            generatorReturnValue = resumeRecord.value();
            pendingException = resumeRecord.value();
            context.setPendingException(resumeRecord.value());
            valueStack.push(resumeRecord.value());
            return;
        }

        // Pop the yielded value from stack
        JSValue value = valueStack.pop();

        // Create yield result to signal suspension
        yieldResult = new YieldResult(YieldResult.Type.YIELD, value);

        // Push the value back so it can be returned
        valueStack.push(value);
    }

    private void handleYieldOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleYield();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.YIELD.getSize();
        if (yieldResult != null) {
            JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
            saveActiveGeneratorSuspendedExecutionState(
                    executionContext.frame,
                    executionContext.pc,
                    executionContext.stack,
                    executionContext.sp,
                    executionContext.frameStackBase);
            requestOpcodeReturnFromExecute(executionContext, returnValue);
        }
    }

    private void handleYieldStar() {
        // yield* delegates to another iterator
        // Pop the iterable from the stack
        JSValue iterable = valueStack.pop();

        // Get the iterator from the iterable
        JSObject iterableObj;
        if (iterable instanceof JSObject obj) {
            iterableObj = obj;
        } else {
            iterableObj = toObject(iterable);
            if (iterableObj == null) {
                throw new JSVirtualMachineException("Object is not iterable");
            }
        }

        // Get Symbol.iterator method
        JSValue iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);
        if (!(iteratorMethod instanceof JSFunction iteratorFunc)) {
            throw new JSVirtualMachineException("Object is not iterable");
        }

        // Call Symbol.iterator to get the iterator
        JSValue iterator = iteratorFunc.call(context, iterable, EMPTY_ARGS);
        if (!(iterator instanceof JSObject iteratorObj)) {
            throw new JSVirtualMachineException("Iterator method must return an object");
        }

        // Check for RETURN/THROW resume (yield* delegation protocol per ES2024 27.5.3.3)
        // Following QuickJS js_generator_next: when resumed with GEN_MAGIC_RETURN or GEN_MAGIC_THROW,
        // the magic and value are passed to the yield* bytecode which calls the appropriate method.
        JSGeneratorState.ResumeRecord resumeRecord =
                generatorResumeIndex < generatorResumeRecords.size()
                        ? generatorResumeRecords.get(generatorResumeIndex)
                        : null;

        if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
            generatorResumeIndex++; // consume the record
            JSValue returnValue = resumeRecord.value();

            // Get "return" method from iterator
            JSValue returnMethodValue = iteratorObj.get(context, PropertyKey.RETURN);
            if (context.hasPendingException()) {
                throw new JSVirtualMachineException(
                        context.getPendingException().toString(),
                        context.getPendingException());
            }
            boolean noReturnMethod = returnMethodValue.isNullOrUndefined();

            if (noReturnMethod) {
                // No return method - complete with the return value (don't yield)
                valueStack.push(returnValue);
                return;
            }

            if (!(returnMethodValue instanceof JSFunction returnFunc)) {
                throw new JSVirtualMachineException(context.throwTypeError("iterator return is not a function"));
            }

            // Call iterator.return(value)
            JSValue result = returnFunc.call(context, iteratorObj, new JSValue[]{returnValue});
            if (context.hasPendingException()) {
                throw new JSVirtualMachineException(
                        context.getPendingException().toString(),
                        context.getPendingException());
            }

            // Check result is an object (per spec, TypeError if not)
            if (!(result instanceof JSObject)) {
                throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
            }

            // Check done flag
            JSValue doneValue = ((JSObject) result).get(context, PropertyKey.DONE);
            if (context.hasPendingException()) {
                throw new JSVirtualMachineException(
                        context.getPendingException().toString(),
                        context.getPendingException());
            }
            if (JSTypeConversions.toBoolean(doneValue).value()) {
                // Done - push value and complete (don't yield)
                JSValue value = ((JSObject) result).get(context, PropertyKey.VALUE);
                if (context.hasPendingException()) {
                    throw new JSVirtualMachineException(
                            context.getPendingException().toString(),
                            context.getPendingException());
                }
                valueStack.push(value);
                return;
            } else {
                // Not done - yield the result and continue delegation
                yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result, iteratorObj);
                valueStack.push(result);
                return;
            }
        }

        if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
            generatorResumeIndex++; // consume the record
            JSValue throwValue = resumeRecord.value();

            // Get "throw" method from iterator
            JSValue throwMethodValue = iteratorObj.get(PropertyKey.THROW);
            boolean noThrowMethod = throwMethodValue.isNullOrUndefined();

            if (noThrowMethod) {
                // No throw method - close iterator and throw TypeError
                JSValue closeMethod = iteratorObj.get(PropertyKey.RETURN);
                if (closeMethod instanceof JSFunction closeFunc) {
                    closeFunc.call(context, iteratorObj, EMPTY_ARGS);
                }
                throw new JSVirtualMachineException(context.throwTypeError(
                        "iterator does not have a throw method"));
            }

            if (!(throwMethodValue instanceof JSFunction throwFunc)) {
                throw new JSVirtualMachineException(context.throwTypeError("iterator throw is not a function"));
            }

            // Call iterator.throw(value)
            JSValue result = throwFunc.call(context, iteratorObj, new JSValue[]{throwValue});

            // Check result is an object
            if (!(result instanceof JSObject)) {
                throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
            }

            // Check done flag
            JSValue doneValue = ((JSObject) result).get(PropertyKey.DONE);
            if (JSTypeConversions.toBoolean(doneValue).value()) {
                // Done - push value and complete the yield* expression
                JSValue value = ((JSObject) result).get(PropertyKey.VALUE);
                valueStack.push(value);
                return;
            } else {
                // Not done - yield the result
                yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result, iteratorObj);
                valueStack.push(result);
                return;
            }
        }

        // Default: NEXT protocol - call iterator.next()
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            throw new JSVirtualMachineException("Iterator must have a next method");
        }

        // Skip past previously-yielded values during generator replay.
        // Each yield* value counts as one yield, so we consume yieldSkipCount
        // values from the inner iterator to reach the correct position.
        while (yieldSkipCount > 0) {
            JSValue skipResult = nextFunc.call(context, iterator, EMPTY_ARGS);
            if (!(skipResult instanceof JSObject)) {
                throw new JSVirtualMachineException("Iterator result must be an object");
            }
            JSValue skipDone = ((JSObject) skipResult).get(PropertyKey.DONE);
            if (JSTypeConversions.toBoolean(skipDone).value()) {
                // Inner iterator exhausted during skip — yield* expression is done
                JSValue value = ((JSObject) skipResult).get(PropertyKey.VALUE);
                valueStack.push(value);
                return;
            }
            yieldSkipCount--;
        }

        JSValue result = nextFunc.call(context, iterator, EMPTY_ARGS);

        // The result should be an object (the iterator result)
        if (!(result instanceof JSObject)) {
            throw new JSVirtualMachineException("Iterator result must be an object");
        }

        // Check if the inner iterator is done
        JSValue doneValue = ((JSObject) result).get(PropertyKey.DONE);
        if (JSTypeConversions.toBoolean(doneValue).value()) {
            // Inner iterator done - yield* expression value is the final value
            JSValue value = ((JSObject) result).get(PropertyKey.VALUE);
            valueStack.push(value);
            // Don't set yieldResult - the yield* expression completes
            return;
        }

        // Set yield result to the raw iterator result object
        // This is what QuickJS does with *pdone = 2
        yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result, iteratorObj);

        // Push the result back on the stack
        valueStack.push(result);
    }

    private void handleYieldStarOpcode(ExecutionContext executionContext) {
        valueStack.stackTop = executionContext.sp;
        handleYieldStar();
        executionContext.sp = valueStack.stackTop;
        executionContext.pc += Opcode.YIELD_STAR.getSize();
        if (yieldResult != null) {
            JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
            clearActiveGeneratorSuspendedExecutionState();
            requestOpcodeReturnFromExecute(executionContext, returnValue);
        }
    }

    private JSValue incrementValue(JSValue value, int delta) {
        JSValue numeric = toNumericValue(value);
        if (numeric instanceof JSBigInt bigInt) {
            return new JSBigInt(bigInt.value().add(delta >= 0 ? BIGINT_ONE : BIGINT_NEGATIVE_ONE));
        }
        return JSNumber.of(((JSNumber) numeric).value() + delta);
    }

    private void initializeConstantValueIfNeeded(JSValue constantValue) {
        if (constantValue instanceof JSFunction functionValue) {
            functionValue.initializePrototypeChain(context);
        } else if (constantValue instanceof JSObject objectValue) {
            ensureConstantObjectPrototype(objectValue);
        }
    }

    private boolean isBranchTruthy(JSValue conditionValue) {
        if (conditionValue instanceof JSBoolean booleanValue) {
            return booleanValue.value();
        }
        if (conditionValue instanceof JSNumber numberValue) {
            double d = numberValue.value();
            return d != 0.0 && !Double.isNaN(d);
        }
        return JSTypeConversions.toBoolean(conditionValue) == JSBoolean.TRUE;
    }

    private boolean isUninitialized(JSValue value) {
        return value == UNINITIALIZED_MARKER;
    }

    private NumericPair numericPair(JSValue left, JSValue right) {
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

    private boolean ordinaryHasInstance(JSValue constructorValue, JSValue objectValue) {
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
    private JSValue proxyApply(JSProxy proxy, JSValue thisArg, JSValue[] args) {
        if (proxy.isRevoked()) {
            JSContext proxyContext = proxy.getProxyContext();
            throw new JSException(proxyContext.throwTypeError("Cannot perform 'apply' on a proxy that has been revoked"));
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
    private JSValue proxyConstruct(JSProxy proxy, JSValue[] args, JSValue newTarget) {
        if (proxy.isRevoked()) {
            JSContext proxyContext = proxy.getProxyContext();
            throw new JSException(proxyContext.throwTypeError("Cannot perform 'construct' on a proxy that has been revoked"));
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

    private JSValue readReferenceValue(StackFrame frame, Opcode makeRefOpcode, int refIndex) {
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

    private JSVirtualMachineException referenceErrorNotDefined(PropertyKey key) {
        String name = key != null ? key.toPropertyString() : "variable";
        return new JSVirtualMachineException(context.throwReferenceError(name + " is not defined"));
    }

    private void requestOpcodeReturnFromExecute(ExecutionContext executionContext, JSValue returnValue) {
        restoreExecuteCallerState(
                executionContext.restoreStackTop,
                executionContext.previousFrame,
                executionContext.savedStrictMode);
        executionContext.returnValue = returnValue;
        executionContext.opcodeRequestedReturn = true;
    }

    private void resetPropertyAccessTracking() {
        if (trackPropertyAccess) {
            this.propertyAccessChain.setLength(0);
        }
        this.propertyAccessLock = false;
    }

    private void restoreExecuteCallerState(int restoreStackTop, StackFrame previousFrame, boolean savedStrictMode) {
        valueStack.stackTop = restoreStackTop;
        currentFrame = previousFrame;
        if (savedStrictMode) {
            context.enterStrictMode();
        } else {
            context.exitStrictMode();
        }
    }

    private void restoreExecuteFailureState(int restoreStackTop, StackFrame previousFrame, boolean savedStrictMode) {
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
    private String safeExceptionToString(JSContext context, JSValue exception) {
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

    private void saveActiveGeneratorSuspendedExecutionState(
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

    private void setArgumentValue(int index, JSValue value) {
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

    private void setLocalValue(int index, JSValue value) {
        JSValue[] locals = currentFrame.getLocals();
        if (index >= 0 && index < locals.length) {
            locals[index] = value;
        }
    }

    private void setObjectName(JSValue objectValue, JSString nameValue) {
        if (!(objectValue instanceof JSObject object)) {
            return;
        }
        object.defineProperty(PropertyKey.fromString("name"), nameValue, PropertyDescriptor.DataState.Configurable);
    }

    private JSValue shiftBigInt(JSBigInt value, JSBigInt shiftCount, boolean leftShiftOperator) {
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

    private boolean shouldUseMappedArguments(JSFunction function) {
        return function instanceof JSBytecodeFunction bytecodeFunction
                && !bytecodeFunction.isStrict()
                && !bytecodeFunction.isArrow()
                && !bytecodeFunction.hasParameterExpressions();
    }

    private JSValue throwMixedBigIntTypeError() {
        pendingException = context.throwTypeError("Cannot mix BigInt and other types");
        return JSUndefined.INSTANCE;
    }

    private void throwVariableUninitializedReferenceError() {
        throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
    }

    private JSValue toNumericValue(JSValue value) {
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
    private JSObject toObject(JSValue value) {
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
    private JSValue toPropertyKeyValue(JSValue rawKey) {
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

    private void writeReferenceValue(StackFrame frame, Opcode makeRefOpcode, int refIndex, JSValue value) {
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

    private enum PendingExceptionAction {
        NONE,
        CONTINUE,
        RETURN
    }

    private record NumericPair(JSValue left, JSValue right, boolean bigInt) {
    }

    private final class ExecutionContext {
        private final Bytecode bytecode;
        private final Opcode[] decodedOpcodes;
        private final StackFrame frame;
        private final int frameStackBase;
        private final byte[] instructions;
        private final JSValue[] locals;
        private final byte[] opcodeRebaseOffsets;
        private final StackFrame previousFrame;
        private final int restoreStackTop;
        private final boolean savedStrictMode;
        private final JSStackValue[] stack;
        private boolean opcodeRequestedReturn;
        private int pc;
        private JSValue returnValue;
        private int sp;

        private ExecutionContext(
                Bytecode bytecode,
                StackFrame frame,
                StackFrame previousFrame,
                int frameStackBase,
                int restoreStackTop,
                boolean savedStrictMode) {
            this.bytecode = bytecode;
            this.instructions = bytecode.getInstructions();
            this.decodedOpcodes = bytecode.getDecodedOpcodes();
            this.opcodeRebaseOffsets = bytecode.getOpcodeRebaseOffsets();
            this.frame = frame;
            this.previousFrame = previousFrame;
            this.frameStackBase = frameStackBase;
            this.restoreStackTop = restoreStackTop;
            this.savedStrictMode = savedStrictMode;
            this.locals = frame.getLocals();
            this.stack = valueStack.stack;
            this.pc = 0;
            this.opcodeRequestedReturn = false;
            this.returnValue = null;
            this.sp = 0;
        }
    }
}
