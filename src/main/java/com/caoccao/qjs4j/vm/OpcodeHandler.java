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
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class OpcodeHandler {
    private static final int MAX_CLOSURE_SCAN_FRAME_COUNT = 65_536;

    private OpcodeHandler() {
    }

    private static JSValue accessPrivateField(VirtualMachine vm, JSValue receiver, JSValue privateName, JSValue value,
            boolean write) {
        if (!(privateName instanceof JSSymbol symbol)) {
            return value;
        }
        JSObject object = receiver instanceof JSObject obj ? obj : vm.toObject(receiver);
        if (object == null) {
            vm.pendingException = vm.context.throwTypeError(write
                    ? "Cannot write private member to a non-object"
                    : "Cannot read private member from a non-object");
            return value;
        }
        PropertyKey key = PropertyKey.fromSymbol(symbol);
        PropertyDescriptor descriptor = object instanceof JSProxy proxy
                ? proxy.getOwnPrivatePropertyDescriptorDirect(key)
                : object.getOwnPropertyDescriptor(key);
        if (descriptor != null) {
            if (descriptor.isAccessorDescriptor()) {
                JSFunction accessor = write ? descriptor.getSetter() : descriptor.getGetter();
                if (accessor != null) {
                    if (!write) {
                        return accessor.call(vm.context, object, JSValue.NO_ARGS);
                    }
                    accessor.call(vm.context, object, vm.singleArg(value));
                    return value;
                }
            } else if (!write) {
                return descriptor.getValue();
            } else if (descriptor.isWritable()) {
                object.setPrivatePropertyDirect(key, value);
                return value;
            }
        }
        vm.pendingException = vm.context.throwTypeError(
                (write ? "Cannot write private member " : "Cannot read private member ") + symbol.getDescription()
                        + (write ? " to" : " from") + " an object whose class did not declare it");
        return value;
    }

    private static JSNumber appendIterable(ExecutionContext executionContext, JSArray array, int position,
            JSValue enumerableObject) {
        try {
            JSContext context = executionContext.virtualMachine.context;
            JSValue iterator = JSIteratorHelper.getIterator(context, enumerableObject);
            if (capturePendingException(executionContext)) {
                return null;
            }
            if (iterator == null) {
                executionContext.virtualMachine.pendingException = context.throwTypeError("Value is not iterable");
                context.clearPendingException();
                return null;
            }

            while (true) {
                JSObject resultObject = JSIteratorHelper.iteratorNext(iterator, context);
                if (capturePendingException(executionContext)) {
                    return null;
                }
                if (resultObject == null) {
                    break;
                }
                JSValue doneValue = resultObject.get(PropertyKey.DONE);
                if (capturePendingException(executionContext)) {
                    return null;
                }
                if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                    break;
                }
                JSValue value = resultObject.get(PropertyKey.VALUE);
                if (capturePendingException(executionContext)) {
                    return null;
                }
                array.set(position++, value);
                if (capturePendingException(executionContext)) {
                    return null;
                }
            }
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.captureVMException(e);
            return null;
        }
        return JSNumber.of(position);
    }

    private static JSValue bigIntArithmetic(VirtualMachine vm, Opcode op, BigInteger left, BigInteger right) {
        switch (op) {
            case MUL :
                return new JSBigInt(left.multiply(right));
            case SUB :
                return new JSBigInt(left.subtract(right));
            case DIV :
            case MOD :
                if (right.signum() == 0) {
                    vm.pendingException = vm.context.throwRangeError("Division by zero");
                    return JSUndefined.INSTANCE;
                }
                return new JSBigInt(op == Opcode.DIV ? left.divide(right) : left.remainder(right));
            case POW :
                if (right.signum() < 0) {
                    vm.pendingException = vm.context.throwRangeError("Exponent must be positive");
                    return JSUndefined.INSTANCE;
                }
                if (right.bitLength() > 31) {
                    vm.pendingException = vm.context.throwRangeError("BigInt exponent is too large");
                    return JSUndefined.INSTANCE;
                }
                return new JSBigInt(left.pow(right.intValue()));
            default :
                throw new IllegalArgumentException("Unexpected arithmetic opcode " + op);
        }
    }

    private static void bitwise(Opcode op, ExecutionContext executionContext) {
        JSValue left = executionContext.peek(1);
        JSValue right = executionContext.peek(0);
        if (left instanceof JSNumber a && right instanceof JSNumber b) {
            int result = bitwiseInt(op, JSTypeConversions.toInt32(a.value()), JSTypeConversions.toInt32(b.value()));
            executionContext.set(1, JSNumber.of(result));
            executionContext.sp--;
        } else {
            VirtualMachine vm = executionContext.virtualMachine;
            CallStack stack = vm.valueStack;
            stack.stackTop = executionContext.sp;
            stack.pop();
            stack.pop();
            VirtualMachine.NumericPair pair;
            try {
                pair = vm.numericPair(left, right);
            } catch (JSVirtualMachineException e) {
                captureVmExceptionAsPending(executionContext, e);
                pair = null;
            }
            if (pair == null || vm.pendingException != null) {
                stack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                BigInteger a = ((JSBigInt) pair.left()).value();
                BigInteger b = ((JSBigInt) pair.right()).value();
                stack.push(new JSBigInt(switch (op) {
                    case AND -> a.and(b);
                    case OR -> a.or(b);
                    case XOR -> a.xor(b);
                    default -> throw new IllegalArgumentException(op.name());
                }));
            } else {
                stack.push(JSNumber.of(bitwiseInt(op, JSTypeConversions.toInt32(vm.context, pair.left()),
                        JSTypeConversions.toInt32(vm.context, pair.right()))));
            }
            executionContext.sp = stack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    private static int bitwiseInt(Opcode op, int left, int right) {
        return switch (op) {
            case AND -> left & right;
            case OR -> left | right;
            case XOR -> left ^ right;
            default -> throw new IllegalArgumentException(op.name());
        };
    }

    /**
     * Convert a JSVirtualMachineException (thrown from a nested execute() call) into a VM pendingException so the outer
     * execution loop can route it to the appropriate JS try-catch handler.
     */
    /**
     * Call a callable value that may be a JSFunction or a callable JSProxy.
     */
    private static JSValue callCallableValue(JSContext context, JSValue callable, JSValue thisArg, JSValue[] args) {
        if (callable instanceof JSProxy proxy) {
            return proxy.apply(context, thisArg, args);
        }
        if (callable instanceof JSFunction function) {
            return function.call(context, thisArg, args);
        }
        throw new JSVirtualMachineException(context.throwTypeError("not a function"));
    }

    private static JSObject callDelegateMethod(ExecutionContext executionContext, JSObject iterator, JSValue method,
            JSValue argument, String nonCallableMessage) {
        if (!JSTypeChecking.isCallable(method)) {
            raiseTypeError(executionContext, nonCallableMessage);
            return null;
        }
        VirtualMachine vm = executionContext.virtualMachine;
        JSValue result = callCallableValue(vm.context, method, iterator, vm.singleArg(argument));
        if (capturePendingException(executionContext)) {
            return null;
        }
        if (result instanceof JSObject object) {
            return object;
        }
        raiseTypeError(executionContext, "iterator must return an object");
        return null;
    }

    private static JSObject callDelegateNext(ExecutionContext executionContext, JSObject iterator, JSValue method,
            JSValue[] args) {
        JSValue result = callCallableValue(executionContext.virtualMachine.context, method, iterator, args);
        if (capturePendingException(executionContext)) {
            return null;
        }
        if (result instanceof JSObject object) {
            return object;
        }
        raiseTypeError(executionContext, "Iterator result must be an object");
        return null;
    }

    private static boolean captureForOfStepException(ExecutionContext executionContext, JSStackValue[] preservedMarkers,
            int preservedMarkerCount, int depth, int nextPc) {
        VirtualMachine vm = executionContext.virtualMachine;
        if (!vm.context.hasPendingException()) {
            return false;
        }
        restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount, depth);
        vm.pendingException = vm.context.takePendingException();
        executionContext.finishStackOpcode(nextPc);
        return true;
    }

    private static boolean capturePendingException(ExecutionContext executionContext) {
        JSContext context = executionContext.virtualMachine.context;
        if (context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = context.takePendingException();
            return true;
        }
        return false;
    }

    private static void captureVmExceptionAsPending(ExecutionContext executionContext, JSVirtualMachineException e) {
        JSValue errorValue = e.getJsValue() != null ? e.getJsValue() : e.getJsError();
        if (errorValue != null) {
            executionContext.virtualMachine.pendingException = errorValue;
        } else {
            setErrorAsPending(executionContext, executionContext.virtualMachine.context
                    .throwError(e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    private static Map<String, String> collectImportAttributes(JSContext context, JSValue optionsValue) {
        if (optionsValue instanceof JSUndefined) {
            return null;
        }
        if (!(optionsValue instanceof JSObject optionsObject)) {
            JSValue errorValue = context.throwTypeError("options must be an object");
            JSValue pendingError = context.takePendingException();
            throw new JSException(pendingError != null ? pendingError : errorValue);
        }

        JSValue withValue = optionsObject.get(PropertyKey.fromString("with"));
        if (context.hasPendingException()) {
            throw new JSException(context.takePendingException());
        }
        if (withValue instanceof JSUndefined) {
            return null;
        }
        if (!(withValue instanceof JSObject withObject)) {
            JSValue errorValue = context.throwTypeError("options.with must be an object");
            JSValue pendingError = context.takePendingException();
            throw new JSException(pendingError != null ? pendingError : errorValue);
        }

        Map<String, String> attributes = new HashMap<>();
        PropertyKey[] enumerableKeys = withObject.enumerableKeys();
        if (context.hasPendingException()) {
            throw new JSException(context.takePendingException());
        }
        for (PropertyKey key : enumerableKeys) {
            if (!key.isString()) {
                continue;
            }
            JSValue attributeValue = withObject.get(key);
            if (context.hasPendingException()) {
                throw new JSException(context.takePendingException());
            }
            if (!(attributeValue instanceof JSString attributeString)) {
                JSValue errorValue = context.throwTypeError("Import assertion value must be a string");
                JSValue pendingError = context.takePendingException();
                throw new JSException(pendingError != null ? pendingError : errorValue);
            }
            attributes.put(key.asString(), attributeString.value());
        }
        return attributes;
    }

    private static void compareRelationalOperands(ExecutionContext executionContext, boolean reverse, boolean negate) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        JSValue right = vm.valueStack.pop();
        JSValue left = vm.valueStack.pop();
        try {
            // Reversing the comparison still coerces the original left operand first.
            JSTypeConversions.RelationalComparisonResult result = reverse
                    ? JSTypeConversions.lessThanResult(vm.context, right, left, false)
                    : JSTypeConversions.lessThanResult(vm.context, left, right);
            vm.capturePendingException();
            JSTypeConversions.RelationalComparisonResult expected = negate
                    ? JSTypeConversions.RelationalComparisonResult.FALSE
                    : JSTypeConversions.RelationalComparisonResult.TRUE;
            vm.valueStack
                    .push(vm.pendingException == null ? JSBoolean.valueOf(result == expected) : JSUndefined.INSTANCE);
        } catch (JSVirtualMachineException e) {
            vm.capturePendingExceptionFromVmOrContext(e);
            vm.valueStack.push(JSUndefined.INSTANCE);
        }
        executionContext.sp = vm.valueStack.stackTop;
    }

    private static JSError createCannotReadPropertiesTypeError(JSContext context, JSValue value) {
        return createCannotReadPropertiesTypeError(context, value, null);
    }

    /**
     * Build the TypeError for reading a property of {@code null} or {@code undefined}.
     * <p>
     * The property being read is named when it is known, matching V8's
     * {@code "Cannot read properties of null (reading 'foo')"}. The computed-index path used to omit it even though the
     * key was in hand, so {@code null.foo} and {@code null['foo']} reported the same failure differently.
     *
     * @param context
     *            the context to raise the error in
     * @param value
     *            the null or undefined base value
     * @param keyValue
     *            the property key being read, or {@code null} when it is unavailable
     * @return the error object
     */
    private static JSError createCannotReadPropertiesTypeError(JSContext context, JSValue value, JSValue keyValue) {
        String objectType = value instanceof JSNull ? "null" : "undefined";
        String keyDescription = describePropertyKeyForError(keyValue);
        String message = keyDescription == null
                ? "Cannot read properties of " + objectType
                : "Cannot read properties of " + objectType + " (reading '" + keyDescription + "')";
        JSError jsError = context.throwTypeError(message);
        jsError.setVmMessage("value has no property");
        return jsError;
    }

    /**
     * How the TypeError a call or {@code new} with an unusable callee raises names it: as the compiler named the callee
     * ({@code o.a.b is not a function}), or, for a literal callee, by its value.
     */
    private static String describeCallee(ExecutionContext executionContext, JSValue callee) {
        String description = executionContext.bytecode.getCallDescription(executionContext.pc);
        if (description != null) {
            return description;
        }
        // Described without running script: a literal's text, or "object" for a value the compiler did not name.
        if (callee instanceof JSString str) {
            return "\"" + str.value() + "\"";
        }
        if (callee instanceof JSObject) {
            return "object";
        }
        return JSAsyncIterator.describeIteratorResult(callee);
    }

    /**
     * Render a property key for an error message, without running any script.
     *
     * @param keyValue
     *            the key
     * @return the key's text, or {@code null} when it cannot be rendered without coercion
     */
    private static String describePropertyKeyForError(JSValue keyValue) {
        if (keyValue instanceof JSString stringKey) {
            return stringKey.value();
        }
        if (keyValue instanceof JSNumber numberKey) {
            return JSTypeConversions.toString(null, numberKey).value();
        }
        if (keyValue instanceof JSSymbol symbolKey) {
            return symbolKey.toJavaObject();
        }
        return null;
    }

    private static JSNumber divideOrRemainder(Opcode op, JSNumber dividend, JSNumber divisor) {
        return JSNumber.of(op == Opcode.DIV ? dividend.value() / divisor.value() : dividend.value() % divisor.value());
    }

    private static VarRef findClosureVarRef(ExecutionContext executionContext, String variableName) {
        StackFrame checkFrame = executionContext.frame;
        int scannedFrameCount = 0;
        while (checkFrame != null && scannedFrameCount < MAX_CLOSURE_SCAN_FRAME_COUNT) {
            JSFunction checkFunction = checkFrame.getFunction();
            if (checkFunction instanceof JSBytecodeFunction checkBytecodeFunction) {
                VarRef[] closureVarRefs = checkBytecodeFunction.getVarRefs();
                String[] closureVarNames = checkBytecodeFunction.getCapturedVarNames();
                if (closureVarRefs != null && closureVarNames != null) {
                    for (int i = 0; i < closureVarNames.length && i < closureVarRefs.length; i++) {
                        if (variableName.equals(closureVarNames[i]) && closureVarRefs[i] != null) {
                            return closureVarRefs[i];
                        }
                    }
                }
            }
            checkFrame = checkFrame.getCaller();
            scannedFrameCount++;
        }
        return null;
    }

    private static StackFrame findDynamicVarBindingFrame(ExecutionContext executionContext, String variableName) {
        if (variableName == null || executionContext.frame == null) {
            return null;
        }
        StackFrame currentFrame = executionContext.frame;
        if (currentFrame.hasDynamicVarBinding(variableName)) {
            return currentFrame;
        }
        StackFrame evalDynamicScopeFrame = internalResolveEvalDynamicScopeFrameForCurrentFunction(executionContext);
        if (evalDynamicScopeFrame != null && evalDynamicScopeFrame != currentFrame
                && evalDynamicScopeFrame.hasDynamicVarBinding(variableName)) {
            return evalDynamicScopeFrame;
        }
        JSContext context = executionContext.virtualMachine.context;
        if (!context.hasEvalOverlayFrames() && evalDynamicScopeFrame == null) {
            return null;
        }
        IdentityHashMap<StackFrame, Boolean> visitedFrames = new IdentityHashMap<>();
        StackFrame callerFrame = currentFrame.getCaller();
        while (callerFrame != null && visitedFrames.put(callerFrame, Boolean.TRUE) == null) {
            if (callerFrame.hasDynamicVarBinding(variableName)) {
                return callerFrame;
            }
            callerFrame = callerFrame.getCaller();
        }
        return null;
    }

    private static EvalScopedLocalBinding findEvalScopedLocalBinding(ExecutionContext executionContext,
            String variableName) {
        if (variableName == null) {
            return null;
        }
        JSContext context = executionContext.virtualMachine.context;
        if (!context.hasEvalOverlayFrames()) {
            return null;
        }
        if (context.hasEvalOverlayBinding(variableName)) {
            return null;
        }
        boolean allowEvalScopedLookup = false;
        JSFunction currentFunction = executionContext.frame != null ? executionContext.frame.getFunction() : null;
        if (currentFunction instanceof JSBytecodeFunction currentBytecodeFunction) {
            allowEvalScopedLookup = currentBytecodeFunction.isEvalDynamicScopeLookupEnabled();
        }
        if (!allowEvalScopedLookup) {
            return null;
        }
        StackFrame immediateCallerFrame = executionContext.frame.getCaller();
        IdentityHashMap<StackFrame, Boolean> visitedFrames = new IdentityHashMap<>();
        if (immediateCallerFrame != null && visitedFrames.put(immediateCallerFrame, Boolean.TRUE) == null
                && immediateCallerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction) {
            String[] localVarNames = bytecodeFunction.getBytecode().getLocalVarNames();
            JSValue[] localValues = immediateCallerFrame.getLocals();
            if (localVarNames != null && localValues != null) {
                for (int localIndex = 0; localIndex < localVarNames.length
                        && localIndex < localValues.length; localIndex++) {
                    String localVarName = localVarNames[localIndex];
                    if (!variableName.equals(localVarName)) {
                        continue;
                    }
                    JSValue localValue = localValues[localIndex];
                    return new EvalScopedLocalBinding(immediateCallerFrame, localIndex, localValue);
                }
            }
        }
        if (immediateCallerFrame != null
                && immediateCallerFrame.getFunction() instanceof JSBytecodeFunction immediateCallerFunction
                && immediateCallerFunction.isEvalDynamicScopeLookupEnabled()
                && immediateCallerFunction.getEvalDynamicScopeFrame() != null) {
            StackFrame evalScopeFrame = immediateCallerFunction.getEvalDynamicScopeFrame();
            while (evalScopeFrame != null && visitedFrames.put(evalScopeFrame, Boolean.TRUE) == null) {
                if (evalScopeFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction) {
                    String[] localVarNames = bytecodeFunction.getBytecode().getLocalVarNames();
                    JSValue[] localValues = evalScopeFrame.getLocals();
                    if (localVarNames != null && localValues != null) {
                        for (int localIndex = 0; localIndex < localVarNames.length
                                && localIndex < localValues.length; localIndex++) {
                            String localVarName = localVarNames[localIndex];
                            if (!variableName.equals(localVarName)) {
                                continue;
                            }
                            JSValue localValue = localValues[localIndex];
                            if (localValue == VirtualMachine.UNINITIALIZED_MARKER
                                    && evalScopeFrame != immediateCallerFrame) {
                                break;
                            }
                            return new EvalScopedLocalBinding(evalScopeFrame, localIndex, localValue);
                        }
                    }
                }
                evalScopeFrame = evalScopeFrame.getCaller();
            }
        }
        return null;
    }

    private static void getIndexedValue(Opcode op, ExecutionContext executionContext, boolean preserveReceiver) {
        VirtualMachine vm = executionContext.virtualMachine;
        int pc = executionContext.pc;
        JSStackValue[] stack = vm.valueStack.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];

        int resultIndex = preserveReceiver ? sp : sp - 1;
        if (objectValue instanceof JSString string && indexValue instanceof JSNumber number) {
            int index = (int) number.value();
            if (index == number.value() && index >= 0 && index < string.value().length()) {
                stack[resultIndex] = new JSString(String.valueOf(string.value().charAt(index)));
                executionContext.finishOpcode(pc + op.getSize(), resultIndex + 1);
                return;
            }
        }

        if (!preserveReceiver) {
            vm.valueStack.stackTop = resultIndex;
        }
        JSObject object = vm.toObject(objectValue);
        JSValue result = JSUndefined.INSTANCE;
        if (object == null) {
            setErrorAsPending(executionContext,
                    createCannotReadPropertiesTypeError(vm.context, objectValue, indexValue));
        } else {
            try {
                PropertyKey key = PropertyKey.fromValue(vm.context, indexValue);
                // Mapped arguments override get(key). Primitive accessors receive the unboxed receiver.
                JSValue value = objectValue instanceof JSObject ? object.get(key) : object.get(key, objectValue);
                if (!capturePendingException(executionContext)) {
                    result = value;
                }
            } catch (JSVirtualMachineException e) {
                vm.captureVMException(e);
            }
        }
        stack[resultIndex] = result;
        if (!preserveReceiver) {
            vm.valueStack.stackTop = resultIndex + 1;
        }
        executionContext.finishOpcode(pc + op.getSize(), resultIndex + 1);
    }

    /**
     * The {@code length} of a primitive string. It is an own, non-writable, non-configurable property of the String
     * wrapper, so nothing on the prototype chain can shadow it: read it without allocating the wrapper, and without
     * flattening a rope.
     */
    private static JSNumber getStringLength(JSString string) {
        return JSNumber.of(string.length());
    }

    static void handleAbstractEquality(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        if (stack[sp - 2] instanceof JSNumber x && stack[sp - 1] instanceof JSNumber y
                && !executionContext.virtualMachine.context.hasPendingException()) {
            boolean equal = x.value() == y.value();
            stack[sp - 2] = JSBoolean.valueOf(op == Opcode.EQ ? equal : !equal);
            executionContext.sp = sp - 1;
            executionContext.pc += op.getSize();
            return;
        }

        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        if (capturePendingException(executionContext)) {
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            return;
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(op == Opcode.EQ ? result : !result));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleAdd(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() + rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            try {
                executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.addValues(left, right));
                if (capturePendingException(executionContext)) {
                    executionContext.virtualMachine.valueStack.pop();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.captureVMException(e);
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } catch (JSException e) {
                if (e.getErrorValue() != null) {
                    executionContext.virtualMachine.pendingException = e.getErrorValue();
                } else {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                            .throwError("Error", e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                }
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } catch (JSErrorException e) {
                setErrorAsPending(executionContext, executionContext.virtualMachine.context.throwError(e));
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleAddLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue rightValue = executionContext.pop();
        JSValue leftValue = executionContext.locals[localIndex];
        try {
            executionContext.locals[localIndex] = executionContext.virtualMachine.addValues(leftValue, rightValue);
            capturePendingException(executionContext);
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.captureVMException(e);
        } catch (JSException e) {
            if (e.getErrorValue() != null) {
                executionContext.virtualMachine.pendingException = e.getErrorValue();
            } else {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .throwError("Error", e.getMessage() != null ? e.getMessage() : "Unhandled exception");
            }
            executionContext.virtualMachine.context.clearPendingException();
        } catch (JSErrorException e) {
            setErrorAsPending(executionContext, executionContext.virtualMachine.context.throwError(e));
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleAnd(Opcode op, ExecutionContext executionContext) {
        bitwise(op, executionContext);
    }

    static void handleAppend(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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

        JSNumber nextPosition = appendIterable(executionContext, array, (int) positionNumber.value(), enumerableObject);
        stack[sp++] = nextPosition == null ? JSUndefined.INSTANCE : array;
        stack[sp++] = nextPosition == null ? JSUndefined.INSTANCE : nextPosition;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleApply(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int isConstructorCall = executionContext.bytecode.readU16(pc + 1);
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue argsArrayValue = (JSValue) stack[--sp];
        JSValue functionValue = (JSValue) stack[--sp];
        JSValue thisArgValue = (JSValue) stack[--sp];

        JSValue result;
        try {
            if (isConstructorCall != 0) {
                // A `new` with spread arguments names its callee; a super(...) call leaves the check to construct.
                String description = executionContext.bytecode.getCallDescription(pc);
                if (description != null && !JSTypeChecking.isConstructor(functionValue)) {
                    raiseTypeError(executionContext, description + " is not a constructor");
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
                JSValue constructorNewTarget = thisArgValue;
                if (constructorNewTarget.isNullOrUndefined()) {
                    constructorNewTarget = functionValue;
                }
                result = JSReflectObject.construct(executionContext.virtualMachine.context, JSUndefined.INSTANCE,
                        new JSValue[]{functionValue, argsArrayValue, constructorNewTarget});
            } else {
                JSValue[] applyArgs = executionContext.virtualMachine.buildApplyArguments(argsArrayValue, true);
                if (applyArgs == null) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                            .getPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
                if (functionValue instanceof JSProxy proxyFunction) {
                    result = executionContext.virtualMachine.proxyApply(proxyFunction, thisArgValue, applyArgs);
                } else if (functionValue instanceof JSFunction applyFunction) {
                    result = applyFunction.call(executionContext.virtualMachine.context, thisArgValue, applyArgs);
                } else {
                    raiseTypeError(executionContext,
                            describeCallee(executionContext, functionValue) + " is not a function");
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
            }
        } catch (JSVirtualMachineException exception) {
            executionContext.virtualMachine.captureVMException(exception);
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .getPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleApplyEval(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue argsArrayValue = (JSValue) stack[--sp];
        JSValue callee = (JSValue) stack[--sp];

        JSValue[] applyArgs = executionContext.virtualMachine.buildApplyArguments(argsArrayValue, true);
        if (applyArgs == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .getPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = callee;
            for (JSValue applyArg : applyArgs) {
                stack[sp++] = applyArg;
            }
            executionContext.sp = sp;
            internalHandleCall(executionContext, applyArgs.length, true);
            sp = executionContext.sp;
        }
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleArrayFrom(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int count = executionContext.bytecode.readU16(pc + 1);
        JSArray array = executionContext.virtualMachine.context.createJSArray();
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;

        JSValue[] elements = new JSValue[count];
        for (int i = count - 1; i >= 0; i--) {
            elements[i] = (JSValue) stack[--sp];
        }
        for (JSValue element : elements) {
            array.push(element);
        }

        stack[sp++] = array;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleAsyncYieldStar(Opcode op, ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        JSContext context = vm.context;

        // yield* in async generators uses GetIterator(obj, async) per ES2024 7.4.3
        JSValue asyncYieldStarIterable = vm.valueStack.pop();

        JSObject iterable = vm.toObject(asyncYieldStarIterable);
        if (iterable == null) {
            raiseTypeError(executionContext, asyncYieldStarIterable + " is not iterable");
            return;
        }
        JSValue method = iterable.get(PropertyKey.SYMBOL_ASYNC_ITERATOR);
        if (capturePendingException(executionContext)) {
            return;
        }
        boolean isNativeAsyncIterator = !method.isNullOrUndefined();
        if (!isNativeAsyncIterator) {
            method = iterable.get(PropertyKey.SYMBOL_ITERATOR);
            if (capturePendingException(executionContext)) {
                return;
            }
        }
        if (!JSTypeChecking.isCallable(method)) {
            raiseTypeError(executionContext, "is not a function");
            return;
        }
        JSValue iterator = callCallableValue(context, method, asyncYieldStarIterable, JSValue.NO_ARGS);
        if (capturePendingException(executionContext)) {
            return;
        }
        if (!(iterator instanceof JSObject iteratorObj)) {
            raiseTypeError(executionContext,
                    isNativeAsyncIterator
                            ? "Result of the Symbol.asyncIterator method is not an object"
                            : "Result of the Symbol.iterator method is not an object");
            return;
        }

        // Check for RETURN/THROW resume records (yield* delegation protocol per ES2024 27.5.3.3)
        JSGeneratorState.ResumeRecord resumeRecord = vm.currentGeneratorResumeRecord();

        if (resumeRecord != null && (resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN
                || resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW)) {
            if (!resumeAbruptDelegate(op, executionContext, iteratorObj, resumeRecord, isNativeAsyncIterator)) {
                return;
            }
        } else {
            // Default: NEXT protocol -- call iterator.next()
            JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
            if (capturePendingException(executionContext)) {
                return;
            }
            if (!JSTypeChecking.isCallable(nextMethod)) {
                setErrorAsPending(executionContext, context.throwTypeError("is not a function"));
                return;
            }

            // Per ES2024 spec: Invoke(iterator, "next", << received.[[Value]] >>)
            JSValue[] nextArgs = new JSValue[]{JSUndefined.INSTANCE};

            // Skip past previously-yielded values during generator replay
            boolean asyncInnerExhausted = false;
            while (vm.yieldSkipCount > 0) {
                JSObject skipResult = callDelegateNext(executionContext, iteratorObj, nextMethod, nextArgs);
                if (skipResult == null) {
                    return;
                }
                JSValue skipDone = skipResult.get(PropertyKey.DONE);
                if (capturePendingException(executionContext)) {
                    return;
                }
                if (JSTypeConversions.toBoolean(skipDone).value()) {
                    JSValue value = skipResult.get(PropertyKey.VALUE);
                    if (capturePendingException(executionContext)) {
                        return;
                    }
                    vm.valueStack.push(value);
                    asyncInnerExhausted = true;
                    break;
                }
                vm.yieldSkipCount--;
            }

            if (!asyncInnerExhausted) {
                JSObject result = callDelegateNext(executionContext, iteratorObj, nextMethod, nextArgs);
                if (result == null) {
                    return;
                }

                yieldDelegateResult(executionContext, iteratorObj, result, nextMethod, -1, isNativeAsyncIterator);
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        if (vm.yieldResult != null) {
            JSValue returnValue = executionContext.pop();
            // Save execution state so the generator can resume from after ASYNC_YIELD_STAR
            // when the delegate iterator completes (done=true), avoiding side-effect replay
            suspendGenerator(executionContext, returnValue);
        }
    }

    static void handleAwait(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue value = executionContext.virtualMachine.valueStack.pop();

        // If the value is already a promise, preserve PromiseResolve observability by
        // accessing `constructor` first (may throw), then use the promise directly.
        // Otherwise, wrap it via Promise.resolve() which handles thenables.
        JSPromise promise;
        if (value instanceof JSPromise promiseValue) {
            promiseValue.get(PropertyKey.CONSTRUCTOR);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .getPendingException();
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            promise = promiseValue;
        } else {
            promise = executionContext.virtualMachine.context.createJSPromise();
            promise.resolve(executionContext.virtualMachine.context, value);
        }

        // Per ES2024 spec (25.5.5.3 Await), await always takes exactly 1 microtask tick.
        // When running in suspension mode (inside an async function), always suspend and let
        // the reaction callbacks in resumeAsyncFunctionExecution handle resumption via microtask.
        if (executionContext.virtualMachine.awaitSuspensionEnabled
                && executionContext.virtualMachine.activeGeneratorState != null) {
            executionContext.virtualMachine.awaitSuspensionPromise = promise;
        } else {
            // Fallback for non-suspension mode: always process microtasks to ensure
            // pending reactions (e.g., Promise.resolve().then(cb)) run before code after await.
            // ES2024 25.5.5.3: await always takes at least 1 microtask tick.
            executionContext.virtualMachine.context.processMicrotasks();
            // Awaiting handles the promise's rejection, exactly as attaching a reaction would.
            promise.markHandled();

            // Now the promise should be settled, push the resolved value
            if (promise.getState() == JSPromise.PromiseState.FULFILLED) {
                executionContext.virtualMachine.valueStack.push(promise.getResult());
            } else if (promise.getState() == JSPromise.PromiseState.REJECTED) {
                JSValue result = promise.getResult();
                executionContext.virtualMachine.pendingException = result;
                executionContext.virtualMachine.context.setPendingException(result);
            } else {
                // Promise is still pending - this shouldn't happen
                throw new JSVirtualMachineException("Promise did not settle after processing microtasks");
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        if (executionContext.virtualMachine.awaitSuspensionPromise != null) {
            suspendGenerator(executionContext, JSUndefined.INSTANCE);
        }
    }

    static void handleCall(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentCount = executionContext.bytecode.readU16(pc + 1);
        internalHandleCall(executionContext, argumentCount, false);
        executionContext.pc += op.getSize();
    }

    static void handleCall0(Opcode op, ExecutionContext executionContext) {
        internalHandleCall(executionContext, 0, false);
        executionContext.pc += op.getSize();
    }

    static void handleCall1(Opcode op, ExecutionContext executionContext) {
        internalHandleCall(executionContext, 1, false);
        executionContext.pc += op.getSize();
    }

    static void handleCall2(Opcode op, ExecutionContext executionContext) {
        internalHandleCall(executionContext, 2, false);
        executionContext.pc += op.getSize();
    }

    static void handleCall3(Opcode op, ExecutionContext executionContext) {
        internalHandleCall(executionContext, 3, false);
        executionContext.pc += op.getSize();
    }

    static void handleCallConstructor(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentCount = executionContext.bytecode.readU16(pc + 1);
        VirtualMachine virtualMachine = executionContext.virtualMachine;
        JSContext context = virtualMachine.context;
        JSStackValue[] stack = virtualMachine.valueStack.stack;
        int sp = executionContext.sp;

        // Pop arguments (constructors pass args to native/bytecode functions that rely on args.length)
        JSValue[] args = argumentCount == 0 ? JSValue.NO_ARGS : new JSValue[argumentCount];
        for (int i = argumentCount - 1; i >= 0; i--) {
            args[i] = (JSValue) stack[--sp];
        }
        // Pop constructor
        JSValue constructor = (JSValue) stack[--sp];
        virtualMachine.valueStack.stackTop = sp;

        // Handle proxy construct trap (QuickJS: js_proxy_call_constructor)
        if (constructor instanceof JSProxy jsProxy) {
            // Following QuickJS JS_CallConstructorInternal:
            // Check if target is a constructor BEFORE checking for construct trap
            JSValue target = jsProxy.getTarget();
            if (JSTypeChecking.isConstructor(target)) {
                stack[sp++] = virtualMachine.proxyConstruct(jsProxy, args, jsProxy);
            } else {
                context.throwTypeError("proxy is not a constructor");
                virtualMachine.pendingException = context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
            }
        } else if (constructor instanceof JSFunction jsFunction) {
            // Check if the function is constructable
            if (!JSTypeChecking.isConstructor(jsFunction)) {
                String description = executionContext.bytecode.getCallDescription(executionContext.pc);
                context.throwTypeError(
                        (description != null ? description : jsFunction.getName()) + " is not a constructor");
                virtualMachine.pendingException = context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
            } else {
                JSValue result;
                try {
                    result = virtualMachine.constructFunction(jsFunction, args, jsFunction);
                } catch (JSVirtualMachineException e) {
                    virtualMachine.captureVMException(e);
                    result = null;
                }
                if (result == null || context.hasPendingException()) {
                    if (virtualMachine.pendingException == null) {
                        virtualMachine.pendingException = context.takePendingException();
                    }
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = result;
                }
            }
        } else {
            context.throwTypeError(describeCallee(executionContext, constructor) + " is not a constructor");
            virtualMachine.pendingException = context.getPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        }
        executionContext.sp = sp;
        virtualMachine.valueStack.stackTop = sp;
        executionContext.pc = pc + op.getSize();
    }

    // Method call: compiler emits DUP → GET_FIELD → SWAP → args → CALL_METHOD.
    // SWAP puts the stack in func/receiver order (same as CALL) and locks property
    // access tracking during argument evaluation.
    static void handleCallMethod(Opcode op, ExecutionContext executionContext) {
        handleCall(op, executionContext);
    }

    static void handleCatch(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int rawCatchOffset = executionContext.bytecode.readI32(pc + 1);
        boolean isFinally = (rawCatchOffset & 0x80000000) != 0;
        int catchOffset = rawCatchOffset & 0x7FFFFFFF;
        int catchHandlerProgramCounter = pc + op.getSize() + catchOffset;
        executionContext.pushStackValue(new JSCatchOffset(catchHandlerProgramCounter, isFinally));
        executionContext.pc = pc + op.getSize();
    }

    static void handleCloseLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.frame.closeLocal(localIndex);
        executionContext.pc = pc + op.getSize();
    }

    static void handleCopyDataProperties(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int mask = executionContext.bytecode.readU8(pc + 1);
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue targetValue = (JSValue) stack[sp - 1 - (mask & 3)];
        JSValue sourceValue = (JSValue) stack[sp - 1 - ((mask >> 2) & 7)];
        JSValue excludeListValue = (JSValue) stack[sp - 1 - ((mask >> 5) & 7)];
        executionContext.virtualMachine.copyDataProperties(targetValue, sourceValue, excludeListValue);
        executionContext.pc = pc + op.getSize();
    }

    static void handleDec(Opcode op, ExecutionContext executionContext) {
        incrementTop(op, executionContext, -1);
    }

    static void handleDecLoc(Opcode op, ExecutionContext executionContext) {
        incrementLocal(op, executionContext, -1);
    }

    static void handleDefineArrayEl(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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
        array.set(index, value);

        executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
    }

    static void handleDefineClass(Opcode op, ExecutionContext executionContext) {
        handleDefineClass(op, executionContext, false);
    }

    /**
     * Stack: [computedName,] superClass, constructor -> [computedName,] prototype, constructor. Computed definitions
     * keep the name on the stack and carry an explicit heritage flag.
     */
    private static void handleDefineClass(Opcode op, ExecutionContext executionContext, boolean computed) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        int classNameAtom = executionContext.bytecode.readU32(pc + 1);
        int classFlags = computed ? executionContext.bytecode.readU8(pc + 5) : 0;
        String className = executionContext.bytecode.getAtoms()[classNameAtom];
        JSValue constructorValue = (JSValue) stack[--sp];
        JSValue superClassValue = (JSValue) stack[--sp];
        JSValue computedClassNameValue = computed ? (JSValue) stack[sp - 1] : JSUndefined.INSTANCE;

        if (!(constructorValue instanceof JSFunction constructorFunction)) {
            throw new JSVirtualMachineException(
                    (computed ? "DEFINE_CLASS_COMPUTED" : "DEFINE_CLASS") + ": constructor must be a function");
        }

        JSObject prototypeObject = executionContext.virtualMachine.context.createJSObject();
        boolean hasHeritage = computed ? (classFlags & 1) != 0 : superClassValue != JSUndefined.INSTANCE;
        if (hasHeritage) {
            if (superClassValue instanceof JSNull) {
                prototypeObject.setPrototype(null);
            } else {
                if (!(superClassValue instanceof JSObject superClassObject)
                        || !JSTypeChecking.isConstructor(superClassValue)) {
                    executionContext.virtualMachine.context.throwTypeError("parent class must be constructor");
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                            .getPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
                JSValue superPrototypeValue = superClassObject.get(PropertyKey.PROTOTYPE);
                if (capturePendingException(executionContext)) {
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
                if (superPrototypeValue instanceof JSObject superPrototypeObject) {
                    prototypeObject.setPrototype(superPrototypeObject);
                } else if (superPrototypeValue instanceof JSNull) {
                    prototypeObject.setPrototype(null);
                } else {
                    executionContext.virtualMachine.context
                            .throwTypeError("parent class prototype is not an object or null");
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                            .getPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
                constructorFunction.setPrototype(superClassObject);
            }
        }

        constructorFunction.defineProperty(PropertyKey.PROTOTYPE, prototypeObject, PropertyDescriptor.DataState.None);
        prototypeObject.defineProperty(PropertyKey.CONSTRUCTOR, constructorValue,
                PropertyDescriptor.DataState.ConfigurableWritable);
        JSString constructorName = computed
                ? executionContext.virtualMachine.getComputedNameString(computedClassNameValue)
                : new JSString(className);
        if (computed && constructorName.value().isEmpty()) {
            constructorName = new JSString(className);
        }
        executionContext.virtualMachine.setObjectName(constructorValue, constructorName);

        // Set home object on constructor for super property access in constructors
        constructorFunction.setHomeObject(prototypeObject);

        if (constructorFunction instanceof JSBytecodeFunction bytecodeConstructor) {
            bytecodeConstructor.setClassConstructor(true);
            if (hasHeritage) {
                bytecodeConstructor.setDerivedConstructor(true);
            }
        }

        stack[sp++] = prototypeObject;
        stack[sp++] = constructorValue;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleDefineClassComputed(Opcode op, ExecutionContext executionContext) {
        handleDefineClass(op, executionContext, true);
    }

    static void handleDefineField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        int fieldNameAtom = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[fieldNameAtom];
        JSValue value = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject object) {
            PropertyKey fieldKey = executionContext.bytecode.getCachedPropertyKey(fieldNameAtom);
            boolean defineSucceeded = object.defineProperty(fieldKey,
                    PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.All));
            if (!defineSucceeded) {
                raiseTypeError(executionContext, "Cannot redefine property: " + fieldKey.toPropertyString());
                return;
            }
        }

        stack[sp++] = objectValue;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleDefineMethod(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        int methodNameAtom = executionContext.bytecode.readU32(pc + 1);
        String methodName = executionContext.bytecode.getAtoms()[methodNameAtom];
        JSValue methodValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject object) {
            if (methodValue instanceof JSFunction methodFunction) {
                methodFunction.setHomeObject(object);
            }
            PropertyKey methodKey = PropertyKey.fromString(methodName);
            boolean defineSucceeded = object.defineProperty(methodKey,
                    PropertyDescriptor.dataDescriptor(methodValue, PropertyDescriptor.DataState.ConfigurableWritable));
            if (!defineSucceeded) {
                raiseTypeError(executionContext, "Cannot redefine property: " + methodKey.toPropertyString());
                return;
            }
        }

        stack[sp++] = objectValue;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleDefineMethodComputed(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        int methodFlags = executionContext.bytecode.readU8(pc + 1);
        boolean enumerable = (methodFlags & 4) != 0;
        boolean nonWritable = (methodFlags & 8) != 0;
        int methodKind = methodFlags & 3;

        JSValue methodValue = (JSValue) stack[--sp];
        JSValue propertyValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];

        if (objectValue instanceof JSObject object) {
            JSValue propertyKeyValue = executionContext.virtualMachine.toPropertyKeyValue(propertyValue);
            PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, propertyKeyValue);
            JSString computedName = executionContext.virtualMachine.getComputedNameString(propertyKeyValue);
            boolean isPrivateSymbolKey = key != null && key.isSymbol() && key.asSymbol().getDescription() != null
                    && key.asSymbol().getDescription().startsWith("#");
            boolean isProxyPrivateTarget = isPrivateSymbolKey && object instanceof JSProxy;
            if (methodValue instanceof JSFunction methodFunction) {
                JSObject homeObject = object;
                if (isPrivateSymbolKey && !(object instanceof JSFunction)) {
                    JSObject prototypeObject = object.getPrototype();
                    if (prototypeObject != null) {
                        homeObject = prototypeObject;
                    }
                }
                methodFunction.setHomeObject(homeObject);
                String namePrefix;
                if (methodKind == 1) {
                    namePrefix = "get ";
                } else if (methodKind == 2) {
                    namePrefix = "set ";
                } else {
                    namePrefix = "";
                }
                executionContext.virtualMachine.setObjectName(methodFunction,
                        new JSString(namePrefix + computedName.value()));
                JSContext context = executionContext.virtualMachine.context;
                boolean wasStrictMode = context.isStrictMode();
                try {
                    if (wasStrictMode) {
                        context.exitStrictMode();
                    }
                    methodFunction.delete(PropertyKey.PROTOTYPE);
                } finally {
                    if (wasStrictMode) {
                        context.enterStrictMode();
                    }
                }
            }

            boolean defineSucceeded;
            if (methodKind == 0) {
                if (isPrivateSymbolKey && isProxyPrivateTarget && ((JSProxy) object).hasOwnPrivatePropertyDirect(key)) {
                    raiseTypeError(executionContext, "Cannot initialize the same private elements twice on an object");
                    return;
                }
                if (isPrivateSymbolKey && !isProxyPrivateTarget && object.hasOwnProperty(key)) {
                    raiseTypeError(executionContext, "Cannot initialize the same private elements twice on an object");
                    return;
                }
                PropertyDescriptor.DataState dataState;
                if (nonWritable) {
                    dataState = enumerable
                            ? PropertyDescriptor.DataState.EnumerableConfigurable
                            : PropertyDescriptor.DataState.Configurable;
                } else {
                    dataState = enumerable
                            ? PropertyDescriptor.DataState.All
                            : PropertyDescriptor.DataState.ConfigurableWritable;
                }
                if (isProxyPrivateTarget) {
                    ((JSProxy) object).definePrivatePropertyDirect(key,
                            PropertyDescriptor.dataDescriptor(methodValue, dataState));
                    defineSucceeded = true;
                } else {
                    defineSucceeded = object.defineProperty(key,
                            PropertyDescriptor.dataDescriptor(methodValue, dataState));
                }
            } else if (methodKind == 1) {
                JSFunction getter = methodValue instanceof JSFunction function ? function : null;
                JSFunction setter = null;
                PropertyDescriptor descriptor = isProxyPrivateTarget
                        ? ((JSProxy) object).getOwnPrivatePropertyDescriptorDirect(key)
                        : object.getOwnPropertyDescriptor(key);
                if (isPrivateSymbolKey && descriptor != null) {
                    if (descriptor.isDataDescriptor() || descriptor.getGetter() != null) {
                        raiseTypeError(executionContext,
                                "Cannot initialize the same private elements twice on an object");
                        return;
                    }
                }
                if (descriptor != null && descriptor.hasSetter()) {
                    setter = descriptor.getSetter();
                }
                if (isProxyPrivateTarget) {
                    ((JSProxy) object).definePrivatePropertyDirect(key,
                            PropertyDescriptor.accessorDescriptor(getter, setter,
                                    enumerable
                                            ? PropertyDescriptor.AccessorState.All
                                            : PropertyDescriptor.AccessorState.Configurable));
                    defineSucceeded = true;
                } else {
                    defineSucceeded = object.defineProperty(key,
                            PropertyDescriptor.accessorDescriptor(getter, setter,
                                    enumerable
                                            ? PropertyDescriptor.AccessorState.All
                                            : PropertyDescriptor.AccessorState.Configurable));
                }
            } else if (methodKind == 2) {
                JSFunction setter = methodValue instanceof JSFunction function ? function : null;
                JSFunction getter = null;
                PropertyDescriptor descriptor = isProxyPrivateTarget
                        ? ((JSProxy) object).getOwnPrivatePropertyDescriptorDirect(key)
                        : object.getOwnPropertyDescriptor(key);
                if (isPrivateSymbolKey && descriptor != null) {
                    if (descriptor.isDataDescriptor() || descriptor.getSetter() != null) {
                        raiseTypeError(executionContext,
                                "Cannot initialize the same private elements twice on an object");
                        return;
                    }
                }
                if (descriptor != null && descriptor.hasGetter()) {
                    getter = descriptor.getGetter();
                }
                if (isProxyPrivateTarget) {
                    ((JSProxy) object).definePrivatePropertyDirect(key,
                            PropertyDescriptor.accessorDescriptor(getter, setter,
                                    enumerable
                                            ? PropertyDescriptor.AccessorState.All
                                            : PropertyDescriptor.AccessorState.Configurable));
                    defineSucceeded = true;
                } else {
                    defineSucceeded = object.defineProperty(key,
                            PropertyDescriptor.accessorDescriptor(getter, setter,
                                    enumerable
                                            ? PropertyDescriptor.AccessorState.All
                                            : PropertyDescriptor.AccessorState.Configurable));
                }
            } else {
                throw new JSVirtualMachineException("DEFINE_METHOD_COMPUTED: unsupported method flags " + methodFlags);
            }

            if (!defineSucceeded) {
                String keyName = key != null ? key.toPropertyString() : "property";
                raiseTypeError(executionContext, "Cannot redefine property: " + keyName);
                return;
            }
        }

        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleDefinePrivateField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue value = (JSValue) stack[--sp];
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject object && privateSymbolValue instanceof JSSymbol symbol) {
            PropertyKey privateKey = PropertyKey.fromSymbol(symbol);
            if (!object.isExtensible()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .throwTypeError("Cannot define private field on a non-extensible object");
                stack[sp++] = objectValue;
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
            boolean hasPrivateProperty = object instanceof JSProxy proxy
                    ? proxy.hasOwnPrivatePropertyDirect(privateKey)
                    : object.hasOwnProperty(privateKey);
            if (hasPrivateProperty) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .throwTypeError("Cannot initialize the same private elements twice on an object");
                stack[sp++] = objectValue;
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
            if (object instanceof JSProxy proxy) {
                proxy.definePrivatePropertyDirect(privateKey,
                        PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.ConfigurableWritable));
            } else {
                object.defineProperty(privateKey,
                        PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.ConfigurableWritable));
            }
        } else if (!(objectValue instanceof JSObject)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("Cannot define private field on non-object");
        }

        stack[sp++] = objectValue;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleDelete(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue property = executionContext.virtualMachine.valueStack.pop();
        JSValue object = executionContext.virtualMachine.valueStack.pop();
        JSObject targetObject = executionContext.virtualMachine.toObject(object);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("Cannot convert undefined or null to object");
            executionContext.virtualMachine.valueStack.push(JSBoolean.FALSE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, property);
        boolean result = targetObject.delete(key);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .getPendingException();
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleDeleteVar(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        boolean deleted;
        JSContext context = executionContext.virtualMachine.context;
        StackFrame dynamicBindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
        if (dynamicBindingFrame != null) {
            deleted = internalDeleteDynamicVarBinding(dynamicBindingFrame, variableName);
            if (deleted && context.hasEvalOverlayFrames() && context.hasEvalOverlayBinding(variableName)) {
                JSObject globalObject = context.getGlobalObject();
                PropertyKey variableKey = PropertyKey.fromString(variableName);
                globalObject.delete(variableKey);
                capturePendingException(executionContext);
            }
        } else if (context.hasGlobalLexicalBinding(variableName)) {
            // Global lexical bindings are never deletable.
            deleted = false;
        } else {
            deleted = context.getGlobalObject().delete(PropertyKey.fromString(variableName));
        }
        executionContext.push(JSBoolean.valueOf(deleted));
        executionContext.pc = pc + op.getSize();
    }

    static void handleDivisionOrRemainder(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        if (stack[sp - 2] instanceof JSNumber dividend && stack[sp - 1] instanceof JSNumber divisor
                && executionContext.virtualMachine.pendingException == null
                && !executionContext.virtualMachine.context.hasPendingException()) {
            stack[sp - 2] = divideOrRemainder(op, dividend, divisor);
            executionContext.sp = sp - 1;
            executionContext.pc += op.getSize();
            return;
        }

        handleNumericOperands(op, executionContext);
    }

    static void handleDrop(Opcode op, ExecutionContext executionContext) {
        executionContext.sp--;
        executionContext.pc += 1;
    }

    static void handleDup(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 1];
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    static void handleDup1(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 2];
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    static void handleDup2(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 2];
        stack[sp + 1] = stack[sp - 1];
        executionContext.sp = sp + 2;
        executionContext.pc += 1;
    }

    // a b c -> a b c a b c
    static void handleDup3(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 3];
        stack[sp + 1] = stack[sp - 2];
        stack[sp + 2] = stack[sp - 1];
        executionContext.sp = sp + 3;
        executionContext.pc += 1;
    }

    static void handleEval(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentCount = executionContext.bytecode.readU16(pc + 1);
        int evalFlags = executionContext.bytecode.readU16(pc + 3);
        boolean inClassFieldInitializer = (evalFlags & 2) != 0;
        int tailFlag = evalFlags & 1;

        // Schedule class field eval flag before the eval function is called
        if (inClassFieldInitializer) {
            executionContext.virtualMachine.context.scheduleClassFieldEvalCall();
        }

        if (tailFlag != 0) {
            // Tail position eval call: handle TCO when callee is not the real eval
            JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
            int sp = executionContext.sp;

            // Pop arguments into reusable buffer
            JSValue[] args = executionContext.virtualMachine.borrowArgsBuffer(argumentCount);
            for (int i = argumentCount - 1; i >= 0; i--) {
                args[i] = (JSValue) stack[--sp];
            }
            // Eval syntax: no receiver pop (directEvalSyntax)
            JSValue callee = (JSValue) stack[--sp];
            executionContext.virtualMachine.valueStack.stackTop = sp;

            // Check if callee is a non-constructor, non-async/generator bytecode function eligible for TCO
            boolean canTrampoline = false;
            if (callee instanceof JSBytecodeFunction bytecodeFunc && !(callee instanceof JSClass)) {
                canTrampoline = !bytecodeFunc.isClassConstructor() && !bytecodeFunc.isAsync()
                        && !bytecodeFunc.isGenerator();
            }

            if (canTrampoline) {
                executionContext.virtualMachine.tailCallPending = new VirtualMachine.TailCallRequest(
                        (JSBytecodeFunction) callee, JSUndefined.INSTANCE, args, argumentCount);
                executionContext.virtualMachine.lastConstructorThisArg = executionContext.frame.getThisArg();
                executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
                executionContext.opcodeRequestedReturn = true;
                return;
            }

            // Not eligible for trampoline: push values back and call normally, then return
            stack[sp++] = callee;
            for (int i = 0; i < argumentCount; i++) {
                stack[sp++] = args[i];
            }
            executionContext.sp = sp;
            internalHandleCall(executionContext, argumentCount, true);
            if (executionContext.virtualMachine.pendingException != null) {
                // Let the main execution loop route the pending exception through
                // catch/finally handlers instead of forcing an early return.
                executionContext.pc = pc + op.getSize();
                return;
            }
            sp = executionContext.sp;
            JSValue result = (JSValue) stack[--sp];
            executionContext.sp = sp;
            executionContext.virtualMachine.lastConstructorThisArg = executionContext.frame.getThisArg();
            executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
            executionContext.returnValue = result;
            executionContext.opcodeRequestedReturn = true;
            return;
        }

        internalHandleCall(executionContext, argumentCount, true);
        executionContext.pc = pc + op.getSize();
    }

    static void handleExp(Opcode op, ExecutionContext executionContext) {
        handleNumericOperands(op, executionContext);
    }

    static void handleFclosure(Opcode op, ExecutionContext executionContext) {
        pushClosure(op, executionContext, executionContext.bytecode.readU32(executionContext.pc + 1), true);
    }

    static void handleFclosure8(Opcode op, ExecutionContext executionContext) {
        pushClosure(op, executionContext, executionContext.bytecode.readU8(executionContext.pc + 1), false);
    }

    static void handleForAwaitOfNext(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSStackValue catchOffset = executionContext.virtualMachine.valueStack.popStackValue();
        JSValue nextMethod = executionContext.virtualMachine.valueStack.peek(0);
        JSValue iterator = executionContext.virtualMachine.valueStack.peek(1);
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            throw new JSVirtualMachineException("Next method must be a function");
        }
        JSValue result = nextFunc.call(executionContext.virtualMachine.context, iterator, JSValue.NO_ARGS);
        executionContext.virtualMachine.valueStack.pushStackValue(catchOffset);
        executionContext.virtualMachine.valueStack.push(result);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleForAwaitOfStart(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Pop the iterable from the stack
        JSValue iterable = executionContext.virtualMachine.valueStack.pop();

        // Auto-box primitives (strings, numbers, etc.) to access their Symbol.asyncIterator or Symbol.iterator
        JSObject iterableObj;
        if (iterable instanceof JSObject obj) {
            iterableObj = obj;
        } else {
            // Try to auto-box the primitive
            iterableObj = executionContext.virtualMachine.toObject(iterable);
            if (iterableObj == null) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .throwTypeError("object is not async iterable");
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
        }

        // First, try Symbol.asyncIterator
        JSValue asyncIteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ASYNC_ITERATOR);
        JSValue iteratorMethod = null;
        boolean wrapSyncIteratorAsAsync = false;

        if (JSTypeChecking.isCallable(asyncIteratorMethod)) {
            iteratorMethod = asyncIteratorMethod;
        } else if (asyncIteratorMethod != null && !asyncIteratorMethod.isNullOrUndefined()) {
            // Non-callable, non-nullish value: TypeError.
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("object is not async iterable");
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        } else {
            // Fall back to Symbol.iterator (sync iterator that will be auto-wrapped)
            iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);

            if (!JSTypeChecking.isCallable(iteratorMethod)) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .throwTypeError("object is not async iterable");
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            wrapSyncIteratorAsAsync = true;
        }

        // Call the iterator method to get an iterator
        JSValue iterator;
        try {
            iterator = callCallableValue(executionContext.virtualMachine.context, iteratorMethod, iterable,
                    JSValue.NO_ARGS);
        } catch (JSException e) {
            executionContext.virtualMachine.pendingException = e.getErrorValue();
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .getPendingException();
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }

        if (!(iterator instanceof JSObject iteratorObj)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("iterator must return an object");
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);

        if (!JSTypeChecking.isCallable(nextMethod)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("iterator must have a next method");
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }

        JSValue nextMethodForStack = nextMethod;
        if (wrapSyncIteratorAsAsync) {
            final JSObject syncIteratorObject = iteratorObj;
            final JSValue syncNextCallable = nextMethod;
            nextMethodForStack = new JSNativeFunction(executionContext.virtualMachine.context, "next", 0,
                    (childContext, thisArg, args) -> {
                        JSValue syncResult;
                        try {
                            syncResult = callCallableValue(childContext, syncNextCallable, syncIteratorObject,
                                    JSValue.NO_ARGS);
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

                        JSValue syncValue = syncResultObject.get(PropertyKey.VALUE);
                        if (childContext.hasPendingException()) {
                            JSValue reason = childContext.getPendingException();
                            childContext.clearAllPendingExceptions();
                            JSPromise rejectedPromise = childContext.createJSPromise();
                            rejectedPromise.reject(reason);
                            return rejectedPromise;
                        }
                        JSValue doneValue = syncResultObject.get(PropertyKey.DONE);
                        if (childContext.hasPendingException()) {
                            JSValue reason = childContext.getPendingException();
                            childContext.clearAllPendingExceptions();
                            JSPromise rejectedPromise = childContext.createJSPromise();
                            rejectedPromise.reject(reason);
                            return rejectedPromise;
                        }
                        boolean syncDone = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;
                        if (childContext.hasPendingException()) {
                            JSValue reason = childContext.getPendingException();
                            childContext.clearAllPendingExceptions();
                            JSPromise rejectedPromise = childContext.createJSPromise();
                            rejectedPromise.reject(reason);
                            return rejectedPromise;
                        }
                        return JSAsyncIterator.createAsyncFromSyncResultPromise(childContext, syncValue, syncDone,
                                syncIteratorObject);
                    });
        }

        executionContext.virtualMachine.clearForOfIteratorExhausted(iteratorObj);
        // A sync iterator is closed through its async-from-sync wrapper's return(), which always exists and answers
        // with a promise (CreateAsyncFromSyncIterator); the loop awaits it on a break or a return.
        JSValue iteratorForStack = wrapSyncIteratorAsAsync
                ? JSAsyncIterator.createAsyncFromSyncIteratorForClose(executionContext.virtualMachine.context,
                        iteratorObj)
                : iterator;
        // Push iterator, next method, and the for-await marker onto the stack
        executionContext.virtualMachine.valueStack.push(iteratorForStack); // Iterator object
        executionContext.virtualMachine.valueStack.push(nextMethodForStack); // next() method
        executionContext.virtualMachine.valueStack.pushStackValue(JSCatchOffset.ASYNC_ITERATOR_CLOSE_MARKER);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleForInNext(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSStackValue stackValue = executionContext.virtualMachine.valueStack.popStackValue();
        if (!(stackValue instanceof JSInternalValue internal)
                || !(internal.value() instanceof JSForInEnumerator enumerator)) {
            throw new JSVirtualMachineException("Invalid for-in enumerator");
        }
        JSValue nextKey = enumerator.next();
        executionContext.virtualMachine.valueStack.pushStackValue(new JSInternalValue(enumerator));
        executionContext.virtualMachine.valueStack.push(nextKey);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleForInStart(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue sourceValue = executionContext.virtualMachine.valueStack.pop();
        JSValue enumerableValue = sourceValue;
        if (!sourceValue.isNullOrUndefined() && !(sourceValue instanceof JSObject)) {
            JSObject boxedObject = executionContext.virtualMachine.toObject(sourceValue);
            if (boxedObject != null) {
                enumerableValue = boxedObject;
            } else {
                enumerableValue = JSUndefined.INSTANCE;
            }
        }
        JSForInEnumerator enumerator = new JSForInEnumerator(enumerableValue);
        executionContext.virtualMachine.valueStack.pushStackValue(new JSInternalValue(enumerator));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleForOfNext(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int nextPc = pc + op.getSize();
        VirtualMachine vm = executionContext.virtualMachine;
        int depth = executionContext.bytecode.readU8(pc + 1);
        vm.valueStack.stackTop = executionContext.sp;
        // Stack layout: ... iter next catch_offset [depth values] (bottom to top)
        // The depth parameter tells us how many values are between catch_offset and top
        // Following QuickJS: offset = -3 - depth

        // Pop depth values temporarily
        if (vm.forOfTempValues.length < depth) {
            vm.forOfTempValues = new JSValue[Math.max(depth, vm.forOfTempValues.length * 2)];
        }
        for (int i = 0; i < depth; i++) {
            vm.forOfTempValues[i] = vm.valueStack.pop();
        }
        int markerIndex = -1;
        JSStackValue[] stack = vm.valueStack.stack;
        for (int index = vm.valueStack.stackTop - 1; index >= executionContext.frameStackBase; index--) {
            if (stack[index] instanceof JSCatchOffset catchOffset && catchOffset.isIteratorCloseMarker()) {
                markerIndex = index;
                break;
            }
        }
        if (markerIndex < 2) {
            setErrorAsPending(executionContext, vm.context.throwError("Invalid iterator state in FOR_OF_NEXT"));
            return;
        }

        int preservedMarkerCount = vm.valueStack.stackTop - markerIndex - 1;
        JSStackValue[] preservedMarkers = preservedMarkerCount > 0 ? new JSStackValue[preservedMarkerCount] : null;
        for (int markerOffset = 0; markerOffset < preservedMarkerCount; markerOffset++) {
            preservedMarkers[markerOffset] = vm.valueStack.popStackValue();
        }

        JSStackValue catchOffset = vm.valueStack.popStackValue();

        int stackTop = vm.valueStack.stackTop;
        if (stackTop < 2) {
            setErrorAsPending(executionContext, vm.context.throwError("Invalid iterator stack depth in FOR_OF_NEXT"));
            return;
        }
        JSStackValue nextMethodStackValue = vm.valueStack.stack[stackTop - 1];
        JSStackValue iteratorStackValue = vm.valueStack.stack[stackTop - 2];
        JSValue nextMethod = nextMethodStackValue instanceof JSValue jsValue ? jsValue : null;
        JSValue iterator = iteratorStackValue instanceof JSValue jsValue ? jsValue : null;

        boolean done = false;
        JSValue value = JSUndefined.INSTANCE;
        boolean iteratorAlreadyDone = iterator instanceof JSObject iteratorObject
                && vm.isForOfIteratorExhausted(iteratorObject);

        if (!iteratorAlreadyDone) {
            // Call iterator.next()
            if (!JSTypeChecking.isCallable(nextMethod)) {
                String actualType = nextMethodStackValue == null
                        ? "null"
                        : nextMethodStackValue.getClass().getSimpleName();
                String iterType = iteratorStackValue == null ? "null" : iteratorStackValue.getClass().getSimpleName();
                restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount,
                        depth);
                vm.pendingException = vm.context
                        .throwTypeError("Next method must be a function in FOR_OF_NEXT (nextMethod=" + actualType
                                + ", iterator=" + iterType + ")");
                executionContext.finishStackOpcode(nextPc);
                return;
            }

            JSValue result;
            try {
                result = callCallableValue(vm.context, nextMethod, iterator, JSValue.NO_ARGS);
            } catch (JSException e) {
                restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount,
                        depth);
                vm.pendingException = e.getErrorValue();
                vm.context.clearPendingException();
                executionContext.finishStackOpcode(nextPc);
                return;
            } catch (JSVirtualMachineException e) {
                restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount,
                        depth);
                vm.capturePendingExceptionFromVmOrContext(e);
                executionContext.finishStackOpcode(nextPc);
                return;
            }

            // Check for pending exception (e.g., TypedArray detachment during iteration)
            if (captureForOfStepException(executionContext, preservedMarkers, preservedMarkerCount, depth, nextPc)) {
                return;
            }

            if (!(result instanceof JSObject resultObj)) {
                restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount,
                        depth);
                vm.pendingException = vm.context.throwTypeError("Iterator result must be an object");
                executionContext.finishStackOpcode(nextPc);
                return;
            }

            // Get the done property
            JSValue doneValue = resultObj.get(PropertyKey.DONE);
            if (captureForOfStepException(executionContext, preservedMarkers, preservedMarkerCount, depth, nextPc)) {
                return;
            }
            done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;
            if (done) {
                if (iterator instanceof JSObject iteratorObject) {
                    vm.markForOfIteratorExhausted(iteratorObject);
                }
            } else {
                if (iterator instanceof JSObject iteratorObject) {
                    vm.clearForOfIteratorExhausted(iteratorObject);
                }
                value = resultObj.get(PropertyKey.VALUE);
                if (captureForOfStepException(executionContext, preservedMarkers, preservedMarkerCount, depth,
                        nextPc)) {
                    return;
                }
                if (value == null) {
                    value = JSUndefined.INSTANCE;
                }
            }
        } else {
            done = true;
        }

        // Push catch_offset back, then restore temp values, then push value and done
        vm.valueStack.pushStackValue(catchOffset);
        for (int markerOffset = preservedMarkerCount - 1; markerOffset >= 0; markerOffset--) {
            vm.valueStack.pushStackValue(preservedMarkers[markerOffset]);
        }
        for (int i = depth - 1; i >= 0; i--) {
            vm.valueStack.push(vm.forOfTempValues[i]);
            vm.forOfTempValues[i] = null;
        }
        vm.valueStack.push(value);
        vm.valueStack.push(done ? JSBoolean.TRUE : JSBoolean.FALSE);
        executionContext.finishStackOpcode(nextPc);
    }

    static void handleForOfStart(Opcode op, ExecutionContext executionContext) {
        int nextPc = executionContext.pc + op.getSize();
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        startForOf(executionContext, vm.valueStack.pop());
        executionContext.finishStackOpcode(nextPc);
    }

    static void handleGetArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.push(executionContext.virtualMachine.getArgumentValue(argumentIndex));
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetArgShort(Opcode op, ExecutionContext executionContext) {
        int argumentIndex = switch (op) {
            case GET_ARG0 -> 0;
            case GET_ARG1 -> 1;
            case GET_ARG2 -> 2;
            case GET_ARG3 -> 3;
            default ->
                throw new JSVirtualMachineException("Internal engine error: unexpected short get arg opcode " + op);
        };
        executionContext.push(executionContext.virtualMachine.getArgumentValue(argumentIndex));
        executionContext.pc += op.getSize();
    }

    static void handleGetArrayEl(Opcode op, ExecutionContext executionContext) {
        getIndexedValue(op, executionContext, false);
    }

    static void handleGetArrayEl2(Opcode op, ExecutionContext executionContext) {
        getIndexedValue(op, executionContext, true);
    }

    static void handleGetArrayEl3(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[sp - 1];
        JSValue arrayObjectValue = (JSValue) stack[sp - 2];

        if (!(indexValue instanceof JSNumber || indexValue instanceof JSString || indexValue instanceof JSSymbol)) {
            if (arrayObjectValue.isNullOrUndefined()) {
                executionContext.virtualMachine.pendingException = createCannotReadPropertiesTypeError(
                        executionContext.virtualMachine.context, arrayObjectValue, indexValue);
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
            try {
                JSValue convertedIndex = executionContext.virtualMachine.toPropertyKeyValue(indexValue);
                stack[sp - 1] = convertedIndex;
                indexValue = convertedIndex;
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.captureVMException(e);
                executionContext.sp = sp;
                executionContext.pc = pc;
                return;
            }
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(arrayObjectValue);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = createCannotReadPropertiesTypeError(
                    executionContext.virtualMachine.context, arrayObjectValue, indexValue);
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
        JSValue result = targetObject.get(key, arrayObjectValue);
        if (capturePendingException(executionContext)) {
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleGetField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[atomIndex];
        // GET_FIELD2 retains the receiver below the property value.
        JSValue objectValue = op == Opcode.GET_FIELD2 ? executionContext.peek(0) : executionContext.pop();
        if (objectValue instanceof JSString string && "length".equals(fieldName)) {
            JSNumber stringLength = getStringLength(string);
            executionContext.push(stringLength);
            executionContext.pc = pc + op.getSize();
            return;
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(executionContext.bytecode.getCachedPropertyKey(atomIndex), objectValue);
            if (capturePendingException(executionContext)) {
                executionContext.push(JSUndefined.INSTANCE);
            } else {
                executionContext.push(result);
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("Cannot read properties of " + typeName + " (reading '" + fieldName + "')");
            executionContext.push(JSUndefined.INSTANCE);
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetLength(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue objectValue = executionContext.pop();
        if (objectValue instanceof JSString string) {
            executionContext.push(getStringLength(string));
            executionContext.pc = pc + op.getSize();
            return;
        }
        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(PropertyKey.LENGTH);
            if (capturePendingException(executionContext)) {
                executionContext.push(JSUndefined.INSTANCE);
            } else {
                executionContext.push(result);
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("Cannot read properties of " + typeName + " (reading 'length')");
            executionContext.push(JSUndefined.INSTANCE);
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetLoc(Opcode op, ExecutionContext executionContext) {
        pushLocal(op, executionContext, executionContext.bytecode.readU16(executionContext.pc + 1));
    }

    static void handleGetLoc0(Opcode op, ExecutionContext executionContext) {
        pushLocal(op, executionContext, 0);
    }

    static void handleGetLoc1(Opcode op, ExecutionContext executionContext) {
        pushLocal(op, executionContext, 1);
    }

    static void handleGetLoc2(Opcode op, ExecutionContext executionContext) {
        pushLocal(op, executionContext, 2);
    }

    static void handleGetLoc3(Opcode op, ExecutionContext executionContext) {
        pushLocal(op, executionContext, 3);
    }

    static void handleGetLoc8(Opcode op, ExecutionContext executionContext) {
        pushLocal(op, executionContext, executionContext.instructions[executionContext.pc + 1] & 0xFF);
    }

    static void handleGetLocCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue localValue = executionContext.frame.getLocals()[localIndex];
        if (executionContext.virtualMachine.isUninitialized(localValue)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError(
                    VirtualMachine.localVariableName(executionContext.frame, localIndex));
        }
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        stack[sp++] = localValue;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleGetPrivateField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];
        stack[sp++] = accessPrivateField(executionContext.virtualMachine, objectValue, privateSymbolValue,
                JSUndefined.INSTANCE, false);
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleGetRefValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue propertyValue = (JSValue) stack[sp - 1];
        JSValue objectValue = (JSValue) stack[sp - 2];
        JSContext context = executionContext.virtualMachine.context;
        PropertyKey key = PropertyKey.fromValue(context, propertyValue);
        String variableName = key != null && key.isString() ? key.asString() : null;

        if (variableName != null && objectValue.isUndefined()) {
            StackFrame bindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
            if (bindingFrame != null) {
                JSValue variableValue = bindingFrame.getDynamicVarBinding(variableName);
                stack[sp++] = variableValue != null ? variableValue : JSUndefined.INSTANCE;
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
            EvalScopedLocalBinding localBinding = findEvalScopedLocalBinding(executionContext, variableName);
            if (localBinding != null) {
                if (localBinding.value() == VirtualMachine.UNINITIALIZED_MARKER) {
                    executionContext.virtualMachine.pendingException = context
                            .throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = localBinding.value() != null ? localBinding.value() : JSUndefined.INSTANCE;
                }
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
        }

        if (objectValue.isUndefined()) {
            String name = key != null ? key.toPropertyString() : "variable";
            executionContext.virtualMachine.pendingException = context.throwReferenceError(name + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = context.throwTypeError("value has no property");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        if (targetObject == context.getGlobalObject() && variableName != null) {
            StackFrame bindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
            if (bindingFrame != null) {
                JSValue variableValue = bindingFrame.getDynamicVarBinding(variableName);
                stack[sp++] = variableValue != null ? variableValue : JSUndefined.INSTANCE;
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
            EvalScopedLocalBinding localBinding = findEvalScopedLocalBinding(executionContext, variableName);
            if (localBinding != null) {
                if (localBinding.value() == VirtualMachine.UNINITIALIZED_MARKER) {
                    executionContext.virtualMachine.pendingException = context
                            .throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = localBinding.value() != null ? localBinding.value() : JSUndefined.INSTANCE;
                }
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
            if (variableName != null && context.hasGlobalLexicalBinding(variableName)) {
                if (!context.isGlobalLexicalBindingInitialized(variableName)) {
                    executionContext.virtualMachine.pendingException = context
                            .throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = context.readGlobalLexicalBinding(variableName);
                }
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
        }

        JSValue value;
        if (!targetObject.has(key)) {
            String name = key != null ? key.toPropertyString() : "variable";
            executionContext.virtualMachine.pendingException = context.throwReferenceError(name + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        } else {
            value = targetObject.get(key);
        }
        stack[sp++] = value;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleGetSuper(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue objectValue = (JSValue) stack[sp - 1];
        JSObject object = executionContext.virtualMachine.toObject(objectValue);
        if (object == null) {
            capturePendingException(executionContext);
            stack[sp - 1] = JSUndefined.INSTANCE;
        } else {
            JSObject prototypeObject = object.getPrototype();
            stack[sp - 1] = prototypeObject != null ? prototypeObject : JSNull.INSTANCE;
        }
        executionContext.pc += op.getSize();
    }

    static void handleGetSuperValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue keyValue = (JSValue) stack[--sp];
        JSValue superObjectValue = (JSValue) stack[--sp];
        JSValue receiverValue = (JSValue) stack[--sp];

        if (!(superObjectValue instanceof JSObject superObject)) {
            raiseTypeError(executionContext, "super object expected");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, keyValue);
        // Pass receiver directly without boxing. Per ES spec, super property access
        // should not auto-box primitive receivers. The getter receives the original
        // primitive as 'this' (e.g., super.prop.call(3) should see this === 3).
        JSValue result = superObject.getWithReceiver(key, receiverValue);

        if (capturePendingException(executionContext)) {
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleGetVar(Opcode op, ExecutionContext executionContext) {
        handleGetVar(op, executionContext, false);
    }

    /**
     * Stack: ... -> ..., value. Both opcodes resolve the same binding kinds; GET_VAR_UNDEF permits an absent name for
     * typeof, but still rejects an uninitialized lexical binding.
     */
    private static void handleGetVar(Opcode op, ExecutionContext executionContext, boolean allowMissing) {
        int pc = executionContext.pc;
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String[] atomPool = executionContext.bytecode.getAtoms();
        if (atomPool.length == 0 || atomIndex < 0 || atomIndex >= atomPool.length) {
            int varRefIndex = executionContext.bytecode.readU16(pc + 1);
            stack[sp++] = readVarRefValue(executionContext, varRefIndex);
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }
        String variableName = atomPool[atomIndex];
        PropertyKey propertyKey = executionContext.bytecode.getCachedPropertyKey(atomIndex);
        StackFrame dynamicBindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
        if (dynamicBindingFrame != null) {
            JSValue variableValue = dynamicBindingFrame.getDynamicVarBinding(variableName);
            stack[sp++] = variableValue != null ? variableValue : JSUndefined.INSTANCE;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }
        // For ordinary reads, a name the eval overlay resolved is not up for reinterpretation: the overlay was
        // built from the caller's locals, captures and parameter environments in the order the
        // grammar gives them, and this scan knows only names. Letting the scan win meant a captured
        // outer binding beat a nearer one of the same name — a function expression named `n` nested
        // in the default initializer of another named `n` saw the outer function, not itself.
        // GET_VAR_UNDEF retains its closure-first lookup when that scan is enabled.
        if (shouldScanClosureVarRefsForGetVar(executionContext)
                && (allowMissing || !executionContext.virtualMachine.context.hasEvalOverlayBinding(variableName))) {
            // Check closure VarRefs in current frame and caller frames.
            // VarRef-based closure variables are checked so that eval() inside class
            // member functions can resolve the class inner name binding.
            VarRef closureVarRef = findClosureVarRef(executionContext, variableName);
            if (closureVarRef != null) {
                stack[sp++] = readNamedBindingValue(executionContext, variableName, closureVarRef.get());
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
        }
        JSContext context = executionContext.virtualMachine.context;
        JSObject globalObject = context.getGlobalObject();
        boolean hasProperty = false;
        JSValue variableValue = JSUndefined.INSTANCE;
        JSEvalOverlayBinding overlayBinding = resolveVisibleEvalOverlayBinding(executionContext, variableName);
        if (overlayBinding != null) {
            JSValue overlayValue = overlayBinding.get();
            if (overlayValue != null) {
                stack[sp++] = overlayValue;
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
        }
        EvalScopedLocalBinding localBinding = findEvalScopedLocalBinding(executionContext, variableName);
        if (localBinding != null) {
            JSValue localValue = localBinding.value() != null ? localBinding.value() : JSUndefined.INSTANCE;
            stack[sp++] = readNamedBindingValue(executionContext, variableName, localValue);
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }
        JSModuleEnvironment moduleEnvironment = executionContext.frame.getFunction().getModuleEnvironment();
        if (moduleEnvironment != null && moduleEnvironment.has(variableName)) {
            stack[sp++] = moduleEnvironment.get(variableName);
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }
        if (context.hasGlobalLexicalBinding(variableName)) {
            if (!context.isGlobalLexicalBindingInitialized(variableName)) {
                executionContext.virtualMachine.pendingException = context
                        .throwReferenceError("Cannot access '" + variableName + "' before initialization");
                stack[sp++] = JSUndefined.INSTANCE;
            } else {
                JSValue lexicalValue = context.readGlobalLexicalBinding(variableName);
                stack[sp++] = lexicalValue;
            }
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }
        variableValue = globalObject.get(propertyKey);
        if (!(variableValue instanceof JSUndefined)) {
            hasProperty = true;
        } else {
            hasProperty = globalObject.has(propertyKey);
        }
        if (!hasProperty && !shouldScanClosureVarRefsForGetVar(executionContext)) {
            VarRef closureVarRef = findClosureVarRef(executionContext, variableName);
            if (closureVarRef != null) {
                stack[sp++] = readNamedBindingValue(executionContext, variableName, closureVarRef.get());
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
        }
        if (!hasProperty && !allowMissing) {
            executionContext.virtualMachine.pendingException = context
                    .throwReferenceError(variableName + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = variableValue;
        }
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleGetVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.push(readVarRefValue(executionContext, varRefIndex));
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetVarRefCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = readVarRefValue(executionContext, varRefIndex);
        if (executionContext.virtualMachine.isUninitialized(value)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        executionContext.push(value);
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetVarRefShort(Opcode op, ExecutionContext executionContext) {
        int varRefIndex = switch (op) {
            case GET_VAR_REF0 -> 0;
            case GET_VAR_REF1 -> 1;
            case GET_VAR_REF2 -> 2;
            case GET_VAR_REF3 -> 3;
            default ->
                throw new JSVirtualMachineException("Internal engine error: unexpected short get var ref opcode " + op);
        };
        executionContext.push(readVarRefValue(executionContext, varRefIndex));
        executionContext.pc += op.getSize();
    }

    static void handleGetVarUndef(Opcode op, ExecutionContext executionContext) {
        handleGetVar(op, executionContext, true);
    }

    static void handleGosub(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int offset = executionContext.bytecode.readU32(pc + 1);
        // Push return address (instruction after this GOSUB) onto the stack
        int returnAddress = pc + op.getSize();
        executionContext.pushStackValue(new JSInternalValue(returnAddress));
        // Jump to the finally block
        executionContext.pc = pc + op.getSize() + offset;
    }

    static void handleGoto(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int offset = executionContext.bytecode.readU32(pc + 1);
        executionContext.pc = pc + op.getSize() + offset;
    }

    static void handleGoto16(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int offset = (short) (executionContext.bytecode.readU16(pc + 1));
        executionContext.pc = pc + op.getSize() + offset;
    }

    static void handleGoto8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.pc = pc + op.getSize() + executionContext.instructions[pc + 1];
    }

    static void handleGt(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() > rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            compareRelationalOperands(executionContext, true, false);
        }
        executionContext.pc += op.getSize();
    }

    static void handleGte(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() >= rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            compareRelationalOperands(executionContext, false, true);
        }
        executionContext.pc += op.getSize();
    }

    static void handleIfFalse(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue conditionValue = executionContext.pop();
        int offset = executionContext.bytecode.readU32(pc + 1);
        if (executionContext.virtualMachine.isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + op.getSize();
        } else {
            executionContext.pc = pc + op.getSize() + offset;
        }
    }

    static void handleIfFalse8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue conditionValue = executionContext.pop();
        if (executionContext.virtualMachine.isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + op.getSize();
        } else {
            executionContext.pc = pc + op.getSize() + executionContext.instructions[pc + 1];
        }
    }

    static void handleIfTrue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue conditionValue = executionContext.pop();
        int offset = executionContext.bytecode.readU32(pc + 1);
        if (executionContext.virtualMachine.isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + op.getSize() + offset;
        } else {
            executionContext.pc = pc + op.getSize();
        }
    }

    static void handleIfTrue8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue conditionValue = executionContext.pop();
        if (executionContext.virtualMachine.isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + op.getSize() + executionContext.instructions[pc + 1];
        } else {
            executionContext.pc = pc + op.getSize();
        }
    }

    // specifier options -> promise (dynamic module import)
    static void handleImport(Opcode op, ExecutionContext executionContext) {
        JSContext context = executionContext.virtualMachine.context;
        JSValue options = executionContext.pop();
        boolean deferPhase = options == JSImportDeferMarker.VALUE;
        if (deferPhase) {
            options = JSUndefined.INSTANCE;
        }
        JSValue specifier = executionContext.pop();
        JSStackFrame currentStackFrame = context.getCurrentStackFrame();
        String referrerFilename = currentStackFrame != null ? currentStackFrame.filename() : null;

        JSPromise promise = context.createJSPromise();
        final JSPromise.ResolveState resolveState = new JSPromise.ResolveState();
        final String specifierString;
        final Map<String, String> importAttributes;

        try {
            specifierString = JSTypeConversions.toString(context, specifier).toString();
            if (context.hasPendingException()) {
                throw new JSException(context.takePendingException());
            }
            importAttributes = collectImportAttributes(context, options);
        } catch (JSException exception) {
            context.clearPendingException();
            JSValue errorValue = exception.getErrorValue();
            if (errorValue == null) {
                errorValue = context.throwError(exception.getMessage());
                context.clearPendingException();
            }
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                promise.reject(errorValue);
            }
            executionContext.push(promise);
            executionContext.pc += op.getSize();
            return;
        } catch (JSVirtualMachineException exception) {
            context.clearPendingException();
            JSValue errorValue = exception.getJsValue();
            if (errorValue == null) {
                errorValue = exception.getJsError();
            }
            if (errorValue == null) {
                errorValue = context
                        .throwError(exception.getMessage() != null ? exception.getMessage() : "Module load error");
                context.clearPendingException();
            }
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                promise.reject(errorValue);
            }
            executionContext.push(promise);
            executionContext.pc += op.getSize();
            return;
        } catch (Exception exception) {
            context.clearPendingException();
            JSValue errorValue = context.throwError("Error",
                    exception.getMessage() != null ? exception.getMessage() : "Module load error");
            context.clearPendingException();
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                promise.reject(errorValue);
            }
            executionContext.push(promise);
            executionContext.pc += op.getSize();
            return;
        }

        context.enqueueMicrotask(() -> {
            try {
                JSObject moduleNamespace;
                if (deferPhase) {
                    moduleNamespace = context.loadDynamicImportModuleDeferred(specifierString, referrerFilename,
                            importAttributes, promise, resolveState);
                } else {
                    moduleNamespace = context.loadDynamicImportModule(specifierString, referrerFilename,
                            importAttributes, promise, resolveState);
                }
                if (moduleNamespace != null && !resolveState.alreadyResolved) {
                    resolveState.alreadyResolved = true;
                    promise.resolve(context, moduleNamespace);
                }
            } catch (Exception e) {
                if (context.hasPendingException()) {
                    JSValue error = context.takePendingException();
                    if (!resolveState.alreadyResolved) {
                        resolveState.alreadyResolved = true;
                        promise.reject(error);
                    }
                } else if (e instanceof JSException jsException) {
                    JSValue errorValue = jsException.getErrorValue();
                    if (!resolveState.alreadyResolved) {
                        resolveState.alreadyResolved = true;
                        promise.reject(errorValue);
                    }
                } else if (e instanceof JSVirtualMachineException vme) {
                    // Extract the original JS error value from the VM exception
                    JSValue errorValue = vme.getJsValue() != null
                            ? vme.getJsValue()
                            : vme.getJsError() != null
                                    ? vme.getJsError()
                                    : new JSString(vme.getMessage() != null ? vme.getMessage() : "Unknown error");
                    if (!resolveState.alreadyResolved) {
                        resolveState.alreadyResolved = true;
                        promise.reject(errorValue);
                    }
                } else {
                    if (!resolveState.alreadyResolved) {
                        resolveState.alreadyResolved = true;
                        promise.reject(context.throwError("Error loading module: " + e.getMessage()));
                        context.clearPendingException();
                    }
                }
            }
        });

        executionContext.push(promise);
        executionContext.pc += op.getSize();
    }

    static void handleIn(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (!(right instanceof JSObject jsObj)) {
            raiseTypeError(executionContext, "invalid 'in' operand");
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, left);
        if (capturePendingException(executionContext)) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else {
            boolean result = jsObj.has(key);
            if (capturePendingException(executionContext)) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleInc(Opcode op, ExecutionContext executionContext) {
        incrementTop(op, executionContext, 1);
    }

    static void handleIncLoc(Opcode op, ExecutionContext executionContext) {
        incrementLocal(op, executionContext, 1);
    }

    static void handleInitCtor(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue frameNewTarget = executionContext.virtualMachine.currentFrame.getNewTarget();
        if (frameNewTarget.isNullOrUndefined()) {
            // Keep direct VM opcode tests stable when executionContext.virtualMachine.execute() is used without
            // constructor plumbing.
            if (executionContext.virtualMachine.currentFrame.getCaller() == null) {
                JSValue fallbackThis = executionContext.virtualMachine.currentFrame.getThisArg();
                if (fallbackThis instanceof JSObject) {
                    executionContext.virtualMachine.valueStack.push(fallbackThis);
                    executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                    return;
                }
            }
            raiseTypeError(executionContext, "class constructors must be invoked with 'new'");
            return;
        }

        // Per ES spec BindThisValue: if thisBindingStatus is "initialized", throw ReferenceError.
        // Arrow functions share the this-binding with the enclosing derived constructor.
        // Check if this was already initialized by a previous super() call.
        JSFunction currentFunction = executionContext.virtualMachine.currentFrame.getFunction();
        JSValue existingThisValue = executionContext.virtualMachine.currentFrame.getThisArg();
        if (existingThisValue instanceof JSObject) {
            raiseReferenceError(executionContext, "Super constructor may only be called once");
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }
        if (currentFunction instanceof JSBytecodeFunction arrowBf && arrowBf.isArrow()) {
            JSValue capturedThis = arrowBf.getCapturedThisArg();
            if (capturedThis instanceof JSObject) {
                raiseReferenceError(executionContext, "Super constructor may only be called once");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            // Also check the shared derivedThisRef VarRef which tracks the real initialization
            // state across calls. When an arrow escapes the constructor and calls super() again,
            // capturedThisArg may still appear uninitialized but the VarRef has been updated.
            VarRef arrowDerivedThisRef = executionContext.virtualMachine.currentFrame.getDerivedThisRef();
            if (arrowDerivedThisRef != null) {
                JSValue derivedThisValue = arrowDerivedThisRef.get();
                if (derivedThisValue instanceof JSObject) {
                    raiseReferenceError(executionContext, "Super constructor may only be called once");
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                    return;
                }
            }
        }

        // Explicit super(...): APPLY constructor mode left the initialized this value on stack.
        if (executionContext.virtualMachine.valueStack.getStackTop() > executionContext.virtualMachine.currentFrame
                .getStackBase()) {
            JSValue thisValue = executionContext.virtualMachine.valueStack.pop();
            if (!(thisValue instanceof JSObject jsObject)) {
                raiseTypeError(executionContext, "super() returned non-object");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            executionContext.virtualMachine.currentFrame.setThisArg(jsObject);
            // Update shared derivedThisRef so arrow functions see the initialized this
            VarRef derivedThisRef = executionContext.virtualMachine.currentFrame.getDerivedThisRef();
            if (derivedThisRef != null) {
                derivedThisRef.set(jsObject);
            }
            // For arrow functions and eval inside derived constructors, update the captured this
            // and propagate the initialized this back to the enclosing constructor frame.
            if (currentFunction instanceof JSBytecodeFunction currentBf
                    && (currentBf.isArrow() || currentBf.isEvalSuperCallAllowed())) {
                if (currentBf.isArrow()) {
                    currentBf.setCapturedThisArg(jsObject);
                }
                StackFrame callerFrame = executionContext.virtualMachine.currentFrame.getCaller();
                while (callerFrame != null) {
                    JSFunction callerFunc = callerFrame.getFunction();
                    if (callerFunc instanceof JSBytecodeFunction callerBf && callerBf.isDerivedConstructor()) {
                        callerFrame.setThisArg(jsObject);
                        VarRef callerThisRef = callerFrame.getDerivedThisRef();
                        if (callerThisRef != null) {
                            callerThisRef.set(jsObject);
                        }
                        break;
                    }
                    callerFrame = callerFrame.getCaller();
                }
            }
            executionContext.virtualMachine.valueStack.push(jsObject);
        } else {
            // Default derived constructor path:
            // constructor(...args) { super(...args); }
            JSObject superConstructorObject = currentFunction.getPrototype();
            if (!JSTypeChecking.isConstructor(superConstructorObject)) {
                raiseTypeError(executionContext, "parent class must be constructor");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }

            JSValue superResult;
            if (superConstructorObject instanceof JSProxy superProxy) {
                superResult = superProxy.construct(executionContext.virtualMachine.context,
                        executionContext.frame.getArguments(), frameNewTarget);
            } else if (superConstructorObject instanceof JSFunction superConstructor) {
                superResult = executionContext.virtualMachine.constructFunction(superConstructor,
                        executionContext.frame.getArguments(), frameNewTarget);
            } else {
                raiseTypeError(executionContext, "parent class must be constructor");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            if (capturePendingException(executionContext)) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (!(superResult instanceof JSObject superObject)) {
                raiseTypeError(executionContext, "super() returned non-object");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                executionContext.virtualMachine.currentFrame.setThisArg(superObject);
                VarRef defaultDerivedThisRef = executionContext.virtualMachine.currentFrame.getDerivedThisRef();
                if (defaultDerivedThisRef != null) {
                    defaultDerivedThisRef.set(superObject);
                }
                executionContext.virtualMachine.valueStack.push(superObject);
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleInitialYield(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Initial yield - generator is being created
        // Per ES spec, parameter defaults are evaluated before INITIAL_YIELD,
        // so errors during parameter initialization are thrown during the generator function call.
        if (executionContext.virtualMachine.yieldSkipCount > 0) {
            executionContext.virtualMachine.yieldSkipCount--;
        } else {
            // Signal suspension at INITIAL_YIELD.
            executionContext.virtualMachine.yieldResult = new YieldResult(YieldResult.Type.INITIAL_YIELD,
                    JSUndefined.INSTANCE);
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        if (executionContext.virtualMachine.yieldResult != null) {
            // Save suspended execution state so the generator resumes after INITIAL_YIELD
            // instead of re-executing from the start (which would re-run parameter
            // destructuring, causing side effects like double iterator close).
            suspendGenerator(executionContext, JSUndefined.INSTANCE);
        }
    }

    static void handleInsert2(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue topValue = stack[sp - 1];
        stack[sp] = topValue;
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = topValue;
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    static void handleInsert3(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue topValue = stack[sp - 1];
        stack[sp] = topValue;
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = topValue;
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    static void handleInsert4(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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

    static void handleInstanceof(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();

        // Per ECMAScript spec, right must be an object
        if (!(right instanceof JSObject constructor)) {
            raiseTypeError(executionContext, "Right-hand side of instanceof is not an object");
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }

        JSValue hasInstanceMethod = constructor.get(PropertyKey.SYMBOL_HAS_INSTANCE);
        if (capturePendingException(executionContext)) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }
        if (!(hasInstanceMethod instanceof JSUndefined) && !(hasInstanceMethod instanceof JSNull)) {
            if (!(hasInstanceMethod instanceof JSFunction hasInstanceFunction)) {
                raiseTypeError(executionContext, "@@hasInstance is not callable");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            JSValue result = hasInstanceFunction.call(executionContext.virtualMachine.context, right,
                    executionContext.virtualMachine.singleArg(left));
            if (capturePendingException(executionContext)) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            executionContext.virtualMachine.valueStack
                    .push(JSTypeConversions.toBoolean(result) == JSBoolean.TRUE ? JSBoolean.TRUE : JSBoolean.FALSE);
        } else {
            boolean callable = right instanceof JSFunction;
            if (right instanceof JSProxy proxy && JSTypeChecking.isFunction(proxy.getTarget())) {
                callable = true;
            }
            if (!callable) {
                raiseTypeError(executionContext, "Right-hand side of instanceof is not callable");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            try {
                executionContext.virtualMachine.valueStack
                        .push(executionContext.virtualMachine.ordinaryHasInstance(right, left)
                                ? JSBoolean.TRUE
                                : JSBoolean.FALSE);
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleInvalid(Opcode op, ExecutionContext executionContext) {
        // A byte that decodes to no instruction means miscompiled or corrupted bytecode. That is an
        // engine defect, so it must not be routed to the script's catch handlers as if it were a
        // guest error. Report the surrounding bytes: 0x00 doubles as the extended-opcode prefix, so
        // a trailing 0x00 and a truncated two-byte instruction look identical without them.
        int pc = executionContext.pc;
        byte[] instructions = executionContext.instructions;
        StringBuilder message = new StringBuilder("Internal engine error: invalid opcode at pc=").append(pc)
                .append(" (bytes:");
        for (int offset = pc; offset < Math.min(instructions.length, pc + 4); offset++) {
            message.append(String.format(" %02x", instructions[offset] & 0xFF));
        }
        message.append(", length=").append(instructions.length).append(')');
        throw new JSTerminationException(message.toString());
    }

    static void handleIsNull(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isNull());
        executionContext.pc += op.getSize();
    }

    static void handleIsUndefined(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isUndefined());
        executionContext.pc += op.getSize();
    }

    static void handleIsUndefinedOrNull(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value instanceof JSNull || value instanceof JSUndefined);
        executionContext.pc += op.getSize();
    }

    static void handleIteratorCall(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int flags = executionContext.bytecode.readU8(pc + 1);
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!(iteratorValue instanceof JSObject iteratorObject)) {
            raiseTypeError(executionContext, "iterator call target must be an object");
            return;
        }

        String methodName = (flags & 1) != 0 ? "throw" : "return";
        JSValue methodValue = (flags & 1) != 0
                ? iteratorObject.get(PropertyKey.THROW)
                : iteratorObject.get(PropertyKey.RETURN);
        if (capturePendingException(executionContext)) {
            return;
        }
        boolean noMethod = methodValue.isNullOrUndefined();
        if (!noMethod) {
            if (!JSTypeChecking.isCallable(methodValue)) {
                raiseTypeError(executionContext, "iterator " + methodName + " is not a function");
                return;
            }
            JSValue callResult = (flags & 2) != 0
                    ? callCallableValue(executionContext.virtualMachine.context, methodValue, iteratorObject,
                            JSValue.NO_ARGS)
                    : callCallableValue(executionContext.virtualMachine.context, methodValue, iteratorObject,
                            executionContext.virtualMachine.singleArg(argumentValue));
            stack[sp - 1] = callResult;
        }
        stack[sp++] = JSBoolean.valueOf(noMethod);
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleIteratorCheckObject(Opcode op, ExecutionContext executionContext) {
        JSValue iteratorResult = executionContext.peek(0);
        if (!(iteratorResult instanceof JSObject)) {
            // V8's wording, which names the value.
            raiseTypeError(executionContext,
                    "Iterator result " + JSAsyncIterator.describeIteratorResult(iteratorResult) + " is not an object");
            return;
        }
        executionContext.pc += op.getSize();
    }

    static void handleIteratorClose(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue originalPendingException = executionContext.virtualMachine.pendingException;
        int markerIndex = -1;
        for (int index = sp - 1; index >= executionContext.frameStackBase; index--) {
            if (stack[index] instanceof JSCatchOffset catchOffset && catchOffset.isIteratorCloseMarker()) {
                markerIndex = index;
                break;
            }
        }
        if (markerIndex < 0 && sp > executionContext.frameStackBase) {
            JSStackValue markerCandidate = stack[sp - 1];
            if (markerCandidate instanceof JSNumber markerNumber && markerNumber.value() == 0) {
                markerIndex = sp - 1;
            }
        }
        if (markerIndex < 2) {
            executionContext.pc += op.getSize();
            return;
        }

        int removeStartIndex = markerIndex - 2;
        JSValue iteratorValue = stack[removeStartIndex] instanceof JSValue jsValue ? jsValue : JSUndefined.INSTANCE;
        // A for-await loop's iterator is only removed here; the compiled code calls return() and awaits it.
        boolean asyncIterator = stack[markerIndex] instanceof JSCatchOffset marker
                && marker.isAsyncIteratorCloseMarker();
        int tailCount = sp - (markerIndex + 1);
        if (tailCount > 0) {
            System.arraycopy(stack, markerIndex + 1, stack, removeStartIndex, tailCount);
        }
        sp -= 3;
        for (int index = sp; index < sp + 3 && index < stack.length; index++) {
            stack[index] = null;
        }

        if (!asyncIterator && iteratorValue instanceof JSObject iteratorObject) {
            boolean iteratorAlreadyExhausted = executionContext.virtualMachine.isForOfIteratorExhausted(iteratorObject);
            executionContext.virtualMachine.clearForOfIteratorExhausted(iteratorObject);
            if (!iteratorAlreadyExhausted) {
                JSValue returnMethodValue = iteratorObject.get(PropertyKey.RETURN);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    if (originalPendingException == null) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                                .getPendingException();
                    }
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
                    return;
                }
                if (JSTypeChecking.isCallable(returnMethodValue)) {
                    JSValue closeResult = callCallableValue(executionContext.virtualMachine.context, returnMethodValue,
                            iteratorObject, JSValue.NO_ARGS);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        if (originalPendingException == null) {
                            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                                    .getPendingException();
                        }
                        executionContext.virtualMachine.context.clearPendingException();
                    } else if (!(closeResult instanceof JSObject)) {
                        if (originalPendingException == null) {
                            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                                    .throwTypeError("iterator result is not an object");
                        }
                    }
                } else if (returnMethodValue.isNullOrUndefined()) {
                    // No return method.
                } else if (returnMethodValue instanceof JSObject returnObject && returnObject.isHTMLDDA()) {
                    // IsHTMLDDA callable edge case; preserve previous no-op behavior.
                } else {
                    if (originalPendingException == null) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                                .throwTypeError("iterator return is not a function");
                    }
                }
            }
        }
        executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
    }

    static void handleIteratorGetValueDone(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue iteratorResult = (JSValue) stack[sp - 1];
        if (!(iteratorResult instanceof JSObject iteratorResultObject)) {
            raiseTypeError(executionContext, "iterator must return an object");
            return;
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
        executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
    }

    static void handleIteratorNext(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue catchOffsetValue = (JSValue) stack[sp - 2];
        JSValue nextMethodValue = (JSValue) stack[sp - 3];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!JSTypeChecking.isCallable(nextMethodValue)) {
            raiseTypeError(executionContext, "iterator next is not a function");
            return;
        }
        JSValue nextResult = callCallableValue(executionContext.virtualMachine.context, nextMethodValue, iteratorValue,
                executionContext.virtualMachine.singleArg(argumentValue));
        stack[sp - 1] = nextResult;
        stack[sp - 2] = catchOffsetValue;
        executionContext.pc += op.getSize();
    }

    static void handleLogicalNot(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.toBoolean(operand) == JSBoolean.FALSE;
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleLt(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() < rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            compareRelationalOperands(executionContext, false, false);
        }
        executionContext.pc += op.getSize();
    }

    static void handleLte(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() <= rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            compareRelationalOperands(executionContext, true, true);
        }
        executionContext.pc += op.getSize();
    }

    static void handleMakeScopedRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        int refIndex = executionContext.bytecode.readU16(pc + 5);
        String atomName = executionContext.bytecode.getAtoms()[atomIndex];
        JSObject referenceObject = executionContext.virtualMachine.createReferenceObject(op, refIndex, atomName);
        executionContext.push(referenceObject);
        executionContext.push(new JSString(atomName));
        executionContext.pc = pc + op.getSize();
    }

    static void handleMakeVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String atomName = executionContext.bytecode.getAtoms()[atomIndex];
        JSContext context = executionContext.virtualMachine.context;
        PropertyKey key = PropertyKey.fromString(atomName);
        JSValue baseObject;
        JSModuleEnvironment moduleEnvironment = executionContext.frame.getFunction().getModuleEnvironment();
        JSEvalOverlayBinding overlayBinding = resolveVisibleEvalOverlayBinding(executionContext, atomName);
        if (findDynamicVarBindingFrame(executionContext, atomName) != null) {
            baseObject = context.getGlobalObject();
        } else if (overlayBinding != null && (overlayBinding.isDisplaced() || context.getGlobalObject().has(key))) {
            // Inside the eval, a caller binding shadows an import of the same name, as it does for reads.
            baseObject = overlayBinding.referenceBase();
        } else if (moduleEnvironment != null && moduleEnvironment.has(atomName)) {
            baseObject = moduleEnvironment.imports();
        } else if (context.hasGlobalLexicalBinding(atomName) || context.getGlobalObject().has(key)) {
            baseObject = context.getGlobalObject();
        } else {
            baseObject = JSUndefined.INSTANCE;
        }
        executionContext.push(baseObject);
        executionContext.push(new JSString(atomName));
        executionContext.pc = pc + op.getSize();
    }

    static void handleMul(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() * rightNumber.value());
            executionContext.sp = sp - 1;
            executionContext.pc += op.getSize();
            return;
        }
        handleNumericOperands(op, executionContext);
    }

    static void handleNeg(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue numeric = executionContext.virtualMachine.toNumericValue(operand);
        if (numeric instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().negate()));
        } else {
            executionContext.virtualMachine.valueStack.push(JSNumber.of(-((JSNumber) numeric).value()));
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleNip(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        stack[sp - 2] = stack[sp - 1];
        executionContext.sp = sp - 1;
        executionContext.pc += 1;
    }

    static void handleNipCatch(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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
            setErrorAsPending(executionContext, executionContext.virtualMachine.context.throwError("nip_catch"));
            return;
        }
        stack[sp++] = returnValue;
        executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
    }

    static void handleNop(Opcode op, ExecutionContext executionContext) {
        executionContext.pc += op.getSize();
    }

    static void handleNot(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue numeric;
        try {
            numeric = executionContext.virtualMachine.toNumericValue(operand);
        } catch (JSVirtualMachineException e) {
            captureVmExceptionAsPending(executionContext, e);
            numeric = null;
        }
        if (numeric == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else if (executionContext.virtualMachine.pendingException != null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else if (numeric instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().not()));
        } else {
            int result = ~JSTypeConversions.toInt32(executionContext.virtualMachine.context, numeric);
            executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleNull(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNull.INSTANCE);
        executionContext.pc += 1;
    }

    private static void handleNumericOperands(Opcode op, ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        JSValue right = vm.valueStack.pop();
        JSValue left = vm.valueStack.pop();
        VirtualMachine.NumericPair pair = vm.numericPair(left, right);
        JSValue result = JSUndefined.INSTANCE;
        if (pair != null) {
            if (pair.bigInt()) {
                result = bigIntArithmetic(vm, op, ((JSBigInt) pair.left()).value(), ((JSBigInt) pair.right()).value());
            } else {
                double x = ((JSNumber) pair.left()).value();
                double y = ((JSNumber) pair.right()).value();
                result = JSNumber.of(switch (op) {
                    case MUL -> x * y;
                    case SUB -> x - y;
                    case DIV -> x / y;
                    case MOD -> x % y;
                    case POW -> Math.pow(x, y);
                    default -> throw new IllegalArgumentException("Unexpected arithmetic opcode " + op);
                });
            }
        }
        vm.valueStack.push(result);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleObjectNew(Opcode op, ExecutionContext executionContext) {
        executionContext.push(executionContext.virtualMachine.context.createJSObject());
        executionContext.pc += op.getSize();
    }

    static void handleOr(Opcode op, ExecutionContext executionContext) {
        bitwise(op, executionContext);
    }

    static void handlePerm3(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 3];
        stack[sp - 3] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
        executionContext.pc += 1;
    }

    static void handlePerm4(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 4];
        stack[sp - 4] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = firstValue;
        executionContext.pc += 1;
    }

    static void handlePerm5(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 5];
        stack[sp - 5] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = stack[sp - 4];
        stack[sp - 4] = firstValue;
        executionContext.pc += 1;
    }

    static void handlePlus(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSNumber result = JSTypeConversions.toNumber(executionContext.virtualMachine.context, operand);
        executionContext.virtualMachine.capturePendingException();
        executionContext.virtualMachine.valueStack.push(result);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handlePostDec(Opcode op, ExecutionContext executionContext) {
        postIncrement(op, executionContext, -1);
    }

    static void handlePostInc(Opcode op, ExecutionContext executionContext) {
        postIncrement(op, executionContext, 1);
    }

    static void handlePrivateIn(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue privateField = executionContext.virtualMachine.valueStack.pop();
        JSValue object = executionContext.virtualMachine.valueStack.pop();
        if (!(object instanceof JSObject jsObj)) {
            raiseTypeError(executionContext, "invalid 'in' operand");
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }
        boolean result = false;
        if (privateField instanceof JSSymbol symbol) {
            result = jsObj.has(PropertyKey.fromSymbol(symbol));
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handlePrivateSymbol(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int fieldNameAtomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[fieldNameAtomIndex];
        executionContext.push(new JSSymbol(fieldName));
        executionContext.pc = pc + op.getSize();
    }

    static void handlePush0(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(0));
        executionContext.pc += 1;
    }

    static void handlePush1(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(1));
        executionContext.pc += 1;
    }

    static void handlePush2(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(2));
        executionContext.pc += 1;
    }

    static void handlePush3(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(3));
        executionContext.pc += 1;
    }

    static void handlePush4(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(4));
        executionContext.pc += 1;
    }

    static void handlePush5(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(5));
        executionContext.pc += 1;
    }

    static void handlePush6(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(6));
        executionContext.pc += 1;
    }

    static void handlePush7(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(7));
        executionContext.pc += 1;
    }

    static void handlePushAtomValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        executionContext.push(new JSString(executionContext.bytecode.getAtoms()[atomIndex]));
        executionContext.pc = pc + op.getSize();
    }

    static void handlePushBigintI32(Opcode op, ExecutionContext executionContext) {
        executionContext.push(new JSBigInt(executionContext.bytecode.readI32(executionContext.pc + 1)));
        executionContext.pc += op.getSize();
    }

    static void handlePushConst(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int constIndex = op == Opcode.PUSH_CONST8
                ? executionContext.bytecode.readU8(pc + 1)
                : executionContext.bytecode.readU32(pc + 1);
        JSValue constantValue = executionContext.bytecode.getConstants()[constIndex];
        if (constantValue instanceof JSSymbol symbol) {
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = internalGetActivePrivateSymbolRemap(executionContext,
                    executionContext.sp);
            if (symbolRemap != null) {
                JSSymbol remappedSymbol = symbolRemap.get(symbol);
                if (remappedSymbol != null) {
                    constantValue = remappedSymbol;
                }
            }
        }
        executionContext.virtualMachine.initializeConstantValueIfNeeded(constantValue);
        executionContext.push(constantValue);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePushEmptyString(Opcode op, ExecutionContext executionContext) {
        executionContext.push(new JSString(""));
        executionContext.pc += 1;
    }

    static void handlePushFalse(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSBoolean.FALSE);
        executionContext.pc += 1;
    }

    static void handlePushI16(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.push(JSNumber.of((short) (executionContext.bytecode.readU16(pc + 1))));
        executionContext.pc = pc + 3;
    }

    static void handlePushI32(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int intValue = executionContext.bytecode.readU32(pc + 1);
        executionContext.push(JSNumber.of(intValue));
        executionContext.pc = pc + 5;
    }

    static void handlePushI8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.push(JSNumber.of(executionContext.instructions[pc + 1]));
        executionContext.pc = pc + 2;
    }

    static void handlePushMinus1(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(-1));
        executionContext.pc += 1;
    }

    static void handlePushThis(Opcode op, ExecutionContext executionContext) {
        VarRef derivedThisRef = executionContext.frame.getDerivedThisRef();
        if (derivedThisRef != null) {
            JSValue thisValue = derivedThisRef.get();
            if (executionContext.virtualMachine.isUninitialized(thisValue)) {
                raiseReferenceError(executionContext,
                        "Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
                executionContext.push(JSUndefined.INSTANCE);
                executionContext.pc += op.getSize();
                return;
            }
            executionContext.push(thisValue);
        } else {
            executionContext.push(executionContext.frame.getThisArg());
        }
        executionContext.pc += op.getSize();
    }

    static void handlePushTrue(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSBoolean.TRUE);
        executionContext.pc += 1;
    }

    static void handlePutArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue argumentValue = executionContext.pop();
        executionContext.virtualMachine.setArgumentValue(argumentIndex, argumentValue);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutArgShort(Opcode op, ExecutionContext executionContext) {
        int argumentIndex = switch (op) {
            case PUT_ARG0 -> 0;
            case PUT_ARG1 -> 1;
            case PUT_ARG2 -> 2;
            case PUT_ARG3 -> 3;
            default ->
                throw new JSVirtualMachineException("Internal engine error: unexpected short put arg opcode " + op);
        };
        JSValue argumentValue = executionContext.pop();
        executionContext.virtualMachine.setArgumentValue(argumentIndex, argumentValue);
        executionContext.pc += op.getSize();
    }

    static void handlePutArrayEl(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue assignedValue = (JSValue) stack[--sp];
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject jsObject) {
            try {
                PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                if (jsObject instanceof JSProxy proxy) {
                    proxy.proxySet(executionContext.virtualMachine.context, key, assignedValue);
                } else {
                    jsObject.set(key, assignedValue);
                }
                executionContext.virtualMachine.capturePendingExceptionFromContext(jsObject.getContext());
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            }
        } else if (objectValue instanceof JSNull || objectValue instanceof JSUndefined) {
            PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
            executionContext.virtualMachine.context.throwTypeError(
                    "cannot set property '" + key + "' of " + (objectValue instanceof JSNull ? "null" : "undefined"));
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .takePendingException();
        } else {
            // For primitives, box to find setters in the prototype chain,
            // passing the original primitive as receiver. If no setter is
            // found, strict mode throws TypeError (QuickJS JS_PROP_THROW_STRICT).
            JSObject boxedObject = executionContext.virtualMachine.toObject(objectValue);
            if (boxedObject != null) {
                try {
                    PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                    boolean setSucceeded = boxedObject.setWithResult(key, assignedValue, objectValue);
                    if (!capturePendingException(executionContext) && !setSucceeded
                            && executionContext.virtualMachine.context.isStrictMode()) {
                        executionContext.virtualMachine.context.throwTypeError("Cannot create property '" + key
                                + "' on " + JSTypeChecking.typeof(objectValue) + " '" + objectValue + "'");
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                                .takePendingException();
                    }
                } catch (JSVirtualMachineException e) {
                    executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                }
            }
        }

        stack[sp++] = assignedValue;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handlePutField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue objectValue = executionContext.pop();
        JSValue fieldValue = executionContext.peek(0);
        PropertyKey propertyKey = executionContext.bytecode.getCachedPropertyKey(atomIndex);

        if (objectValue instanceof JSObject jsObject) {
            try {
                if (jsObject instanceof JSProxy proxy) {
                    proxy.proxySet(executionContext.virtualMachine.context, propertyKey, fieldValue);
                } else {
                    jsObject.set(propertyKey, fieldValue);
                }
                executionContext.virtualMachine.capturePendingExceptionFromContext(jsObject.getContext());
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            }
        } else if (objectValue instanceof JSNull || objectValue instanceof JSUndefined) {
            executionContext.virtualMachine.context.throwTypeError("cannot set property '" + fieldName + "' of "
                    + (objectValue instanceof JSNull ? "null" : "undefined"));
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .takePendingException();
        } else {
            // For primitives, box to find setters in the prototype chain,
            // passing the original primitive as receiver. If no setter is
            // found, strict mode throws TypeError (QuickJS JS_PROP_THROW_STRICT).
            JSObject boxedObject = executionContext.virtualMachine.toObject(objectValue);
            if (boxedObject != null) {
                try {
                    boolean setSucceeded = boxedObject.setWithResult(propertyKey, fieldValue, objectValue);
                    if (!capturePendingException(executionContext) && !setSucceeded
                            && executionContext.virtualMachine.context.isStrictMode()) {
                        executionContext.virtualMachine.context.throwTypeError("Cannot create property '" + fieldName
                                + "' on " + JSTypeChecking.typeof(objectValue) + " '" + objectValue + "'");
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                                .takePendingException();
                    }
                } catch (JSVirtualMachineException e) {
                    executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                }
            }
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.locals[localIndex] = executionContext.pop();
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc0(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[0] = executionContext.pop();
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc1(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[1] = executionContext.pop();
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc2(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[2] = executionContext.pop();
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc3(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[3] = executionContext.pop();
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        executionContext.locals[localIndex] = executionContext.pop();
        executionContext.pc += op.getSize();
    }

    static void handlePutLocCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError(
                    VirtualMachine.localVariableName(executionContext.frame, localIndex));
        }
        localValues[localIndex] = executionContext.pop();
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutLocCheckInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (!executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            raiseReferenceError(executionContext, "'this' can be initialized only once");
            return;
        }
        localValues[localIndex] = executionContext.pop();
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutPrivateField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue value = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];
        stack[sp++] = accessPrivateField(executionContext.virtualMachine, objectValue, privateSymbolValue, value, true);
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handlePutRefValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue setValue = (JSValue) stack[--sp];
        JSValue propertyValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];
        JSContext context = executionContext.virtualMachine.context;
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, propertyValue);
        String variableName = key != null && key.isString() ? key.asString() : null;

        if (variableName != null && objectValue.isUndefined()) {
            StackFrame bindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
            if (bindingFrame != null) {
                bindingFrame.setDynamicVarBinding(variableName, setValue);
                if (context.hasEvalOverlayFrames() && context.hasEvalOverlayBinding(variableName)) {
                    JSObject globalObject = context.getGlobalObject();
                    globalObject.set(key, setValue);
                    capturePendingException(executionContext);
                }
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
        }

        if (objectValue.isUndefined()) {
            if (executionContext.virtualMachine.context.isStrictMode()) {
                String name = key != null ? key.toPropertyString() : "variable";
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .throwReferenceError(name + " is not defined");
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
            objectValue = executionContext.virtualMachine.context.getGlobalObject();
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("value has no property");
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        if (targetObject == context.getGlobalObject() && variableName != null) {
            StackFrame bindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
            if (bindingFrame != null) {
                bindingFrame.setDynamicVarBinding(variableName, setValue);
                if (context.hasEvalOverlayFrames() && context.hasEvalOverlayBinding(variableName)) {
                    targetObject.set(key, setValue);
                    capturePendingException(executionContext);
                }
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
            if (variableName != null && context.hasGlobalLexicalBinding(variableName)) {
                if (!context.isGlobalLexicalBindingInitialized(variableName)) {
                    executionContext.virtualMachine.pendingException = context
                            .throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
                if (context.hasGlobalConstDeclaration(variableName)) {
                    executionContext.virtualMachine.pendingException = context
                            .throwTypeError("Assignment to constant variable.");
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
                context.writeGlobalLexicalBinding(variableName, setValue);
                executionContext.finishOpcode(pc + op.getSize(), sp);
                return;
            }
        }

        if (!targetObject.has(key) && executionContext.virtualMachine.context.isStrictMode()) {
            String name = key != null ? key.toPropertyString() : "variable";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwReferenceError(name + " is not defined");
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        // Set flag so import overlay setters can detect bare variable assignment
        // and throw TypeError (ES2024: import bindings are immutable).
        if (targetObject == context.getGlobalObject()) {
            context.setInBareVariableAssignment(true);
        }
        try {
            if (targetObject instanceof JSProxy proxy) {
                proxy.proxySet(executionContext.virtualMachine.context, key, setValue);
            } else {
                targetObject.set(key, setValue);
            }
        } finally {
            context.setInBareVariableAssignment(false);
        }
        executionContext.virtualMachine.capturePendingExceptionFromContext(targetObject.getContext());
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    // Stack: this obj key val -> val (QuickJS order: value at top)
    static void handlePutSuperValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue assignedValue = (JSValue) stack[--sp];
        JSValue keyValue = (JSValue) stack[--sp];
        JSValue superObjectValue = (JSValue) stack[--sp];
        JSValue receiverValue = (JSValue) stack[--sp];

        if (!(superObjectValue instanceof JSObject superObject)) {
            raiseTypeError(executionContext, "super object expected");
            stack[sp++] = assignedValue;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, keyValue);
        if (receiverValue instanceof JSObject receiverObject) {
            if (superObject instanceof JSProxy proxy) {
                proxy.proxySet(executionContext.virtualMachine.context, key, assignedValue, receiverObject);
            } else {
                superObject.setWithReceiverAndException(key, assignedValue, receiverObject);
            }
        } else {
            JSObject boxedReceiver = executionContext.virtualMachine.toObject(receiverValue);
            if (boxedReceiver != null) {
                if (superObject instanceof JSProxy proxy) {
                    proxy.proxySet(executionContext.virtualMachine.context, key, assignedValue, boxedReceiver);
                } else {
                    superObject.setWithReceiverAndException(key, assignedValue, boxedReceiver);
                }
            } else {
                if (superObject instanceof JSProxy proxy) {
                    proxy.proxySet(executionContext.virtualMachine.context, key, assignedValue);
                } else {
                    superObject.set(key, assignedValue);
                }
            }
        }

        executionContext.virtualMachine.capturePendingExceptionFromContext(superObject.getContext());
        stack[sp++] = assignedValue;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handlePutVar(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String[] atomPool = executionContext.bytecode.getAtoms();
        if (atomPool.length == 0 || atomIndex < 0 || atomIndex >= atomPool.length) {
            int varRefIndex = executionContext.bytecode.readU16(pc + 1);
            JSValue value = executionContext.pop();
            writeVarRefValue(executionContext, varRefIndex, value);
            executionContext.pc = pc + op.getSize();
            return;
        }
        String variableName = atomPool[atomIndex];
        JSValue value = executionContext.pop();
        JSContext context = executionContext.virtualMachine.context;
        StackFrame dynamicBindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
        if (dynamicBindingFrame != null) {
            dynamicBindingFrame.setDynamicVarBinding(variableName, value);
            if (context.hasEvalOverlayFrames() && context.hasEvalOverlayBinding(variableName)) {
                JSObject globalObject = context.getGlobalObject();
                PropertyKey variableKey = PropertyKey.fromString(variableName);
                globalObject.set(variableKey, value);
                capturePendingException(executionContext);
            }
            executionContext.pc = pc + op.getSize();
            return;
        }
        PropertyKey variableKey = PropertyKey.fromString(variableName);
        JSObject globalObject = context.getGlobalObject();
        JSEvalOverlayBinding overlayBinding = resolveVisibleEvalOverlayBinding(executionContext, variableName);
        if (overlayBinding != null && (overlayBinding.isDisplaced() || globalObject.has(variableKey))) {
            // Inside the eval, the caller's binding comes before the module's imports, in the order reads resolve
            // them: a local that shadows an import takes the assignment instead of the import rejecting it.
            overlayBinding.set(value);
            capturePendingException(executionContext);
            executionContext.pc = pc + op.getSize();
            return;
        }
        JSModuleEnvironment moduleEnvironment = executionContext.frame.getFunction().getModuleEnvironment();
        if (moduleEnvironment != null && moduleEnvironment.has(variableName)) {
            executionContext.virtualMachine.pendingException = context
                    .throwTypeError("Assignment to constant variable.");
            executionContext.pc = pc + op.getSize();
            return;
        }
        if (context.hasGlobalLexicalBinding(variableName)) {
            if (context.hasGlobalConstDeclaration(variableName)
                    && context.isGlobalLexicalBindingInitialized(variableName)) {
                executionContext.virtualMachine.pendingException = context
                        .throwTypeError("Assignment to constant variable.");
                executionContext.pc = pc + op.getSize();
                return;
            }
            context.writeGlobalLexicalBinding(variableName, value);
            executionContext.pc = pc + op.getSize();
            return;
        }
        if (context.consumeGlobalFunctionBindingInitialization(variableName)) {
            PropertyDescriptor functionDescriptor = new PropertyDescriptor();
            functionDescriptor.setValue(value);
            PropertyDescriptor existingDescriptor = globalObject.getOwnPropertyDescriptor(variableKey);
            if (existingDescriptor == null || existingDescriptor.isConfigurable()) {
                functionDescriptor.setWritable(true);
                functionDescriptor.setEnumerable(true);
                functionDescriptor.setConfigurable(context.isActiveGlobalFunctionBindingConfigurable());
            }
            globalObject.defineProperty(variableKey, functionDescriptor);
            capturePendingException(executionContext);
            executionContext.pc = pc + op.getSize();
            return;
        }
        if (context.isStrictMode() && !globalObject.has(variableKey)) {
            executionContext.virtualMachine.pendingException = context
                    .throwReferenceError(variableName + " is not defined");
            executionContext.pc = pc + op.getSize();
            return;
        }
        // Set flag so import overlay setters can detect bare variable assignment
        context.setInBareVariableAssignment(true);
        try {
            globalObject.set(variableKey, value);
        } finally {
            context.setInBareVariableAssignment(false);
        }
        capturePendingException(executionContext);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = executionContext.pop();
        executionContext.frame.setVarRef(varRefIndex, value);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = executionContext.pop();
        writeVarRefValue(executionContext, varRefIndex, value);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRefCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue currentValue = readVarRefValue(executionContext, varRefIndex);
        if (executionContext.virtualMachine.isUninitialized(currentValue)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        writeVarRefValue(executionContext, varRefIndex, executionContext.pop());
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRefCheckInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue currentValue = readVarRefValue(executionContext, varRefIndex);
        if (!executionContext.virtualMachine.isUninitialized(currentValue)) {
            raiseReferenceError(executionContext, "variable is already initialized");
            return;
        }
        writeVarRefValue(executionContext, varRefIndex, executionContext.pop());
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRefShort(Opcode op, ExecutionContext executionContext) {
        int varRefIndex = switch (op) {
            case PUT_VAR_REF0 -> 0;
            case PUT_VAR_REF1 -> 1;
            case PUT_VAR_REF2 -> 2;
            case PUT_VAR_REF3 -> 3;
            default ->
                throw new JSVirtualMachineException("Internal engine error: unexpected short put var ref opcode " + op);
        };
        JSValue value = executionContext.pop();
        writeVarRefValue(executionContext, varRefIndex, value);
        executionContext.pc += op.getSize();
    }

    static void handleRegexp(Opcode op, ExecutionContext executionContext) {
        // Read constant index from instruction argument
        int pc = executionContext.pc;
        int constIndex = ((executionContext.instructions[pc + 1] & 0xFF) << 24)
                | ((executionContext.instructions[pc + 2] & 0xFF) << 16)
                | ((executionContext.instructions[pc + 3] & 0xFF) << 8)
                | (executionContext.instructions[pc + 4] & 0xFF);
        JSValue template = executionContext.bytecode.getConstants()[constIndex];
        if (template instanceof JSRegExp templateRegExp) {
            // Create a new JSRegExp from the template's pattern and flags
            JSRegExp newRegExp = executionContext.virtualMachine.context.createJSRegExp(templateRegExp.getPattern(),
                    templateRegExp.getFlags());
            executionContext.push(newRegExp);
        } else {
            throw new JSVirtualMachineException("REGEXP constant is not a JSRegExp");
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleRest(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int firstRestArgumentIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] functionArguments = executionContext.frame.getArguments();
        int argumentCount = functionArguments.length;
        int restStartIndex = Math.min(firstRestArgumentIndex, argumentCount);
        int restCount = argumentCount - restStartIndex;
        JSValue[] restArguments = new JSValue[restCount];
        System.arraycopy(functionArguments, restStartIndex, restArguments, 0, restCount);
        JSArray restArray = executionContext.virtualMachine.context.createJSArray(restArguments);
        executionContext.push(restArray);
        executionContext.pc = pc + op.getSize();
    }

    static void handleRet(Opcode op, ExecutionContext executionContext) {
        // Pop the GOSUB return address from the stack and jump to it
        JSStackValue stackValue = executionContext.popStackValue();
        if (stackValue instanceof JSInternalValue internalValue
                && internalValue.value() instanceof Integer returnAddress) {
            executionContext.pc = returnAddress;
        } else {
            throw new JSVirtualMachineException("Invalid ret value");
        }
    }

    static void handleReturn(Opcode op, ExecutionContext executionContext) {
        JSValue returnValue = executionContext.pop();
        executionContext.virtualMachine.lastConstructorThisArg = executionContext.frame.getThisArg();
        executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
        executionContext.returnValue = returnValue;
        executionContext.opcodeRequestedReturn = true;
    }

    static void handleReturnAsync(Opcode op, ExecutionContext executionContext) {
        JSValue returnValue = executionContext.pop();
        executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
        executionContext.returnValue = returnValue;
        executionContext.opcodeRequestedReturn = true;
    }

    static void handleReturnUndef(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.lastConstructorThisArg = executionContext.frame.getThisArg();
        executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
        executionContext.returnValue = JSUndefined.INSTANCE;
        executionContext.opcodeRequestedReturn = true;
    }

    static void handleRot3l(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 3];
        stack[sp - 3] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 1];
        stack[sp - 1] = firstValue;
        executionContext.pc += 1;
    }

    static void handleRot3r(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue thirdValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = thirdValue;
        executionContext.pc += 1;
    }

    // x a b c -> a b c x
    static void handleRot4l(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 4];
        stack[sp - 4] = stack[sp - 3];
        stack[sp - 3] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 1];
        stack[sp - 1] = firstValue;
        executionContext.pc += 1;
    }

    // x a b c d -> a b c d x
    static void handleRot5l(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 5];
        stack[sp - 5] = stack[sp - 4];
        stack[sp - 4] = stack[sp - 3];
        stack[sp - 3] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 1];
        stack[sp - 1] = firstValue;
        executionContext.pc += 1;
    }

    static void handleSar(Opcode op, ExecutionContext executionContext) {
        shiftSigned(op, executionContext, false);
    }

    static void handleSetArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.virtualMachine.setArgumentValue(argumentIndex, executionContext.peek(0));
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetArgShort(Opcode op, ExecutionContext executionContext) {
        int argumentIndex = switch (op) {
            case SET_ARG0 -> 0;
            case SET_ARG1 -> 1;
            case SET_ARG2 -> 2;
            case SET_ARG3 -> 3;
            default ->
                throw new JSVirtualMachineException("Internal engine error: unexpected short set arg opcode " + op);
        };
        executionContext.virtualMachine.setArgumentValue(argumentIndex, executionContext.peek(0));
        executionContext.pc += op.getSize();
    }

    static void handleSetHomeObject(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue homeObjectValue = (JSValue) stack[sp - 2];
        JSValue methodValue = (JSValue) stack[sp - 1];
        if (methodValue instanceof JSFunction methodFunction && homeObjectValue instanceof JSObject homeObject) {
            methodFunction.setHomeObject(homeObject);
        }
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.locals[localIndex] = executionContext.peek(0);
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc0(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[0] = executionContext.peek(0);
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc1(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[1] = executionContext.peek(0);
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc2(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[2] = executionContext.peek(0);
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc3(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[3] = executionContext.peek(0);
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        executionContext.locals[localIndex] = executionContext.peek(0);
        executionContext.pc += op.getSize();
    }

    static void handleSetLocCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError(
                    VirtualMachine.localVariableName(executionContext.frame, localIndex));
        }
        localValues[localIndex] = executionContext.peek(0);
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetLocUninitialized(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        // Entering the scope that declares this binding again — a loop body's next iteration — starts a new
        // binding; closures from the previous entry keep the old one.
        executionContext.frame.detachLocalVarRef(localIndex);
        executionContext.frame.getLocals()[localIndex] = VirtualMachine.UNINITIALIZED_MARKER;
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetName(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String name = executionContext.bytecode.getAtoms()[atomIndex];
        executionContext.virtualMachine.setObjectName(executionContext.peek(0), new JSString(name));
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetNameComputed(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue nameValue = (JSValue) stack[sp - 2];
        executionContext.virtualMachine.setObjectName((JSValue) stack[sp - 1],
                executionContext.virtualMachine.getComputedNameString(nameValue));
        executionContext.pc += op.getSize();
    }

    static void handleSetProto(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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
        executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
    }

    static void handleSetVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.frame.setVarRef(varRefIndex, executionContext.peek(0));
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetVarRefShort(Opcode op, ExecutionContext executionContext) {
        int varRefIndex = switch (op) {
            case SET_VAR_REF0 -> 0;
            case SET_VAR_REF1 -> 1;
            case SET_VAR_REF2 -> 2;
            case SET_VAR_REF3 -> 3;
            default ->
                throw new JSVirtualMachineException("Internal engine error: unexpected short set var ref opcode " + op);
        };
        executionContext.frame.setVarRef(varRefIndex, executionContext.peek(0));
        executionContext.pc += op.getSize();
    }

    static void handleShl(Opcode op, ExecutionContext executionContext) {
        shiftSigned(op, executionContext, true);
    }

    static void handleShr(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        JSValue leftPrimitive = null;
        JSValue leftNumeric = null;
        boolean leftIsBigInt = false;
        JSValue rightPrimitive = null;
        JSValue rightNumeric = null;
        try {
            leftPrimitive = JSTypeConversions.toPrimitive(executionContext.virtualMachine.context, left,
                    JSTypeConversions.PreferredType.NUMBER);
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
        } catch (JSException e) {
            if (e.getErrorValue() != null) {
                executionContext.virtualMachine.pendingException = e.getErrorValue();
            } else {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .throwError(e.getMessage() != null ? e.getMessage() : "toPrimitive");
            }
        }
        if (leftPrimitive != null && executionContext.virtualMachine.pendingException == null) {
            if (leftPrimitive instanceof JSBigInt) {
                leftIsBigInt = true;
                leftNumeric = leftPrimitive;
            } else {
                leftNumeric = JSTypeConversions.toNumber(executionContext.virtualMachine.context, leftPrimitive);
                executionContext.virtualMachine.capturePendingException();
            }
        }
        if (leftNumeric != null && executionContext.virtualMachine.pendingException == null) {
            try {
                rightPrimitive = JSTypeConversions.toPrimitive(executionContext.virtualMachine.context, right,
                        JSTypeConversions.PreferredType.NUMBER);
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            } catch (JSException e) {
                if (e.getErrorValue() != null) {
                    executionContext.virtualMachine.pendingException = e.getErrorValue();
                } else {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                            .throwError("Error", e.getMessage() != null ? e.getMessage() : "toPrimitive");
                }
            }
        }
        if (rightPrimitive != null && executionContext.virtualMachine.pendingException == null) {
            if (rightPrimitive instanceof JSBigInt) {
                rightNumeric = rightPrimitive;
            } else {
                rightNumeric = JSTypeConversions.toNumber(executionContext.virtualMachine.context, rightPrimitive);
                executionContext.virtualMachine.capturePendingException();
            }
        }
        if (executionContext.virtualMachine.pendingException == null
                && (leftIsBigInt || rightNumeric instanceof JSBigInt)) {
            raiseTypeError(executionContext, "BigInts do not support >>>");
        }
        if (leftNumeric instanceof JSNumber leftNum && rightNumeric instanceof JSNumber rightNum
                && executionContext.virtualMachine.pendingException == null) {
            int leftInt = JSTypeConversions.toInt32(leftNum.value());
            int rightInt = JSTypeConversions.toInt32(rightNum.value());
            executionContext.virtualMachine.valueStack.push(JSNumber.of((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
        } else if (leftNumeric != null && rightNumeric != null
                && executionContext.virtualMachine.pendingException == null) {
            int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, leftNumeric);
            int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, rightNumeric);
            executionContext.virtualMachine.valueStack.push(JSNumber.of((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
        } else {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleSpecialObject(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int objectType = executionContext.bytecode.readU8(pc + 1);
        JSValue specialObject = executionContext.virtualMachine.createSpecialObject(objectType, executionContext.frame);
        executionContext.push(specialObject);
        executionContext.pc = pc + op.getSize();
    }

    static void handleStrictEquality(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(op == Opcode.STRICT_EQ ? result : !result));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleSub(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() - rightNumber.value());
            executionContext.sp = sp - 1;
            executionContext.pc += op.getSize();
            return;
        }
        handleNumericOperands(op, executionContext);
    }

    static void handleSwap(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
        executionContext.pc += 1;
    }

    static void handleSwap2(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue firstPairLeft = stack[sp - 4];
        JSStackValue firstPairRight = stack[sp - 3];
        stack[sp - 4] = stack[sp - 2];
        stack[sp - 3] = stack[sp - 1];
        stack[sp - 2] = firstPairLeft;
        stack[sp - 1] = firstPairRight;
        executionContext.pc += 1;
    }

    static void handleTailCall(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentCount = executionContext.bytecode.readU16(pc + 1);
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;

        // Peek at the callee to check if we can use the trampoline
        int calleeIndex = executionContext.sp - argumentCount - 2;
        JSValue callee = executionContext.peek(executionContext.sp - 1 - calleeIndex);

        // Check if callee is a non-constructor, non-async/generator bytecode function eligible for TCO.
        // Async and generator functions require special wrapping in call() that the trampoline bypasses.
        boolean canTrampoline = false;
        if (callee instanceof JSBytecodeFunction bytecodeFunc && !(callee instanceof JSClass)) {
            canTrampoline = !bytecodeFunc.isClassConstructor() && !bytecodeFunc.isAsync()
                    && !bytecodeFunc.isGenerator();
        }

        if (canTrampoline) {
            // Pop arguments into reusable buffer — StackFrame copies on next execute()
            JSValue[] args = executionContext.virtualMachine.borrowArgsBuffer(argumentCount);
            for (int i = argumentCount - 1; i >= 0; i--) {
                args[i] = executionContext.virtualMachine.valueStack.pop();
            }
            JSValue receiver = executionContext.virtualMachine.valueStack.pop();
            callee = executionContext.virtualMachine.valueStack.pop();

            // Apply OrdinaryCallBindThis for the tail-called function, matching JSBytecodeFunction.call()
            JSBytecodeFunction tailCallee = (JSBytecodeFunction) callee;
            if (tailCallee.isArrow() && tailCallee.getCapturedThisArg() != null) {
                receiver = tailCallee.getCapturedThisArg();
            }
            if (!tailCallee.isStrict() && !(receiver instanceof JSObject)) {
                if (receiver instanceof JSUndefined || receiver instanceof JSNull) {
                    receiver = executionContext.virtualMachine.context.getGlobalObject();
                } else {
                    receiver = JSTypeConversions.toObject(executionContext.virtualMachine.context, receiver);
                }
            }

            // Store the tail call request for the trampoline loop in execute()
            executionContext.virtualMachine.tailCallPending = new VirtualMachine.TailCallRequest(tailCallee, receiver,
                    args, argumentCount);
            // Clean up the current frame (same as RETURN)
            executionContext.virtualMachine.lastConstructorThisArg = executionContext.frame.getThisArg();
            executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
            executionContext.opcodeRequestedReturn = true;
            return;
        }

        // Fallback: call normally and return the result (like CALL + RETURN)
        internalHandleCall(executionContext, argumentCount, false);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        JSValue result = executionContext.pop();
        executionContext.virtualMachine.lastConstructorThisArg = executionContext.frame.getThisArg();
        executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
        executionContext.returnValue = result;
        executionContext.opcodeRequestedReturn = true;
    }

    // Tail-call variant of CALL_METHOD.
    static void handleTailCallMethod(Opcode op, ExecutionContext executionContext) {
        handleTailCall(op, executionContext);
    }

    static void handleThrow(Opcode op, ExecutionContext executionContext) {
        JSValue exceptionValue = executionContext.pop();
        executionContext.virtualMachine.pendingException = exceptionValue;
        executionContext.virtualMachine.context.setPendingException(exceptionValue);
        // PC intentionally unchanged. Exception unwinding loop handles control transfer.
    }

    static void handleThrowError(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int throwAtom = executionContext.bytecode.readU32(pc + 1);
        int throwType = executionContext.bytecode.readU8(pc + 5);
        String throwName = executionContext.bytecode.getAtoms()[throwAtom];
        switch (throwType) {
            // V8's wording for an assignment to a const, a class name or a function's own name; QuickJS says
            // "'x' is read-only".
            case 0 -> executionContext.virtualMachine.context.throwTypeError("Assignment to constant variable.");
            case 1 ->
                executionContext.virtualMachine.context.throwError("SyntaxError: redeclaration of '" + throwName + "'");
            case 2 -> executionContext.virtualMachine.context.throwReferenceError(throwName + " is not initialized");
            case 3 -> executionContext.virtualMachine.context.throwReferenceError("unsupported reference to 'super'");
            case 4 -> executionContext.virtualMachine.context.throwTypeError("iterator does not have a throw method");
            case 5 -> executionContext.virtualMachine.context.throwReferenceError(throwName);
            default -> throw new JSVirtualMachineException("invalid throw_error type: " + throwType);
        }
        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                .getPendingException();
        // PC intentionally unchanged. Exception unwinding loop handles control transfer.
    }

    static void handleToObject(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        JSObject object = executionContext.virtualMachine.toObject(value);
        if (object == null) {
            executionContext.virtualMachine.context.throwTypeError("Cannot convert undefined or null to object");
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .getPendingException();
            executionContext.sp = sp;
            executionContext.pc = pc;
            return;
        }
        stack[sp - 1] = object;
        executionContext.pc = pc + op.getSize();
    }

    static void handleToPropKey(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rawKey = (JSValue) stack[--sp];
        try {
            stack[sp++] = executionContext.virtualMachine.toPropertyKeyValue(rawKey);
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.captureVMException(e);
            executionContext.sp = sp;
            executionContext.pc = pc;
            return;
        }
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleTypeof(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        String type = JSTypeChecking.typeof(operand);
        executionContext.virtualMachine.valueStack.push(new JSString(type));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleTypeofIsFunction(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(JSKeyword.FUNCTION.equals(JSTypeChecking.typeof(value)));
        executionContext.pc += op.getSize();
    }

    static void handleTypeofIsUndefined(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(JSKeyword.UNDEFINED.equals(JSTypeChecking.typeof(value)));
        executionContext.pc += op.getSize();
    }

    static void handleUndefined(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSUndefined.INSTANCE);
        executionContext.pc += 1;
    }

    static void handleXor(Opcode op, ExecutionContext executionContext) {
        bitwise(op, executionContext);
    }

    static void handleYield(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Check if we should skip this yield (resuming from later point)
        if (executionContext.virtualMachine.yieldSkipCount > 0) {
            executionContext.virtualMachine.yieldSkipCount--;
            JSValue yieldedValue = executionContext.virtualMachine.valueStack.pop();
            JSGeneratorState.ResumeRecord resumeRecord = executionContext.virtualMachine.generatorResumeIndex < executionContext.virtualMachine.generatorResumeRecords
                    .size()
                            ? executionContext.virtualMachine.generatorResumeRecords
                                    .get(executionContext.virtualMachine.generatorResumeIndex++)
                            : null;
            if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
                executionContext.virtualMachine.pendingException = resumeRecord.value();
                executionContext.virtualMachine.context.setPendingException(resumeRecord.value());
            } else if (resumeRecord != null) {
                executionContext.virtualMachine.valueStack.push(resumeRecord.value());
            } else {
                // If resume data is missing, default to undefined to preserve stack shape.
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
            // Don't yield - just continue execution from the resumed generator state.
        } else {
            // At the target yield - check for RETURN/THROW resume records (replay mode)
            JSGeneratorState.ResumeRecord resumeRecord = executionContext.virtualMachine.currentGeneratorResumeRecord();
            if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
                executionContext.virtualMachine.generatorResumeIndex++;
                executionContext.virtualMachine.valueStack.pop(); // Pop the yielded value
                // Trigger force return through finally handlers
                executionContext.virtualMachine.generatorReturnValue = resumeRecord.value();
                executionContext.virtualMachine.pendingException = resumeRecord.value();
                executionContext.virtualMachine.context.setPendingException(resumeRecord.value());
                executionContext.virtualMachine.valueStack.push(resumeRecord.value());
            } else {
                // Pop the yielded value from stack
                JSValue value = executionContext.virtualMachine.valueStack.pop();

                // Create yield result to signal suspension.
                executionContext.virtualMachine.yieldResult = new YieldResult(YieldResult.Type.YIELD, value);

                // Push the value back so it can be returned
                executionContext.virtualMachine.valueStack.push(value);
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        if (executionContext.virtualMachine.yieldResult != null) {
            JSValue returnValue = executionContext.pop();
            suspendGenerator(executionContext, returnValue);
        }
    }

    static void handleYieldStar(Opcode op, ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        try {

            // Check for RETURN/THROW resume (yield* delegation protocol per ES2024 27.5.3.3)
            JSGeneratorState.ResumeRecord resumeRecord = vm.currentGeneratorResumeRecord();
            YieldResult lastYieldResult = vm.activeGeneratorState != null
                    ? vm.activeGeneratorState.getLastYieldResult()
                    : null;
            boolean reuseDelegateIterator = resumeRecord != null && lastYieldResult != null
                    && lastYieldResult.isYieldStar() && lastYieldResult.delegateIterator() != null
                    && lastYieldResult.delegationProgramCounter() == executionContext.pc;

            JSValue iterable = JSUndefined.INSTANCE;
            if (!reuseDelegateIterator) {
                // Fresh delegation entry: iterable is on top of stack.
                iterable = vm.valueStack.pop();
            }

            JSObject iteratorObj;
            if (reuseDelegateIterator) {
                iteratorObj = lastYieldResult.delegateIterator();
            } else {
                // Get the iterator from the iterable
                JSObject iterableObj;
                if (iterable instanceof JSObject obj) {
                    iterableObj = obj;
                } else {
                    iterableObj = vm.toObject(iterable);
                    if (iterableObj == null) {
                        raiseTypeError(executionContext, "Object is not iterable");
                        return;
                    }
                }

                // Get Symbol.iterator method
                JSValue iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);
                if (capturePendingException(executionContext)) {
                    return;
                }
                if (!JSTypeChecking.isCallable(iteratorMethod)) {
                    raiseTypeError(executionContext, "Object is not iterable");
                    return;
                }

                // Call Symbol.iterator to get the iterator
                JSValue iterator = callCallableValue(vm.context, iteratorMethod, iterable, JSValue.NO_ARGS);
                if (!(iterator instanceof JSObject iteratorObject)) {
                    raiseTypeError(executionContext, "Iterator method must return an object");
                    return;
                }
                iteratorObj = iteratorObject;
            }

            if (resumeRecord != null && (resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN
                    || resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW)) {
                if (!resumeAbruptDelegate(op, executionContext, iteratorObj, resumeRecord, false)) {
                    return;
                }
            } else {
                // Default: NEXT protocol - call iterator.next()
                JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
                if (capturePendingException(executionContext)) {
                    return;
                }
                if (!JSTypeChecking.isCallable(nextMethod)) {
                    raiseTypeError(executionContext, "Iterator must have a next method");
                    return;
                }
                if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.NEXT) {
                    vm.generatorResumeIndex++;
                }
                JSValue nextArgument = resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.NEXT
                        ? resumeRecord.value()
                        : JSUndefined.INSTANCE;
                JSValue[] undefinedNextArgs = new JSValue[]{JSUndefined.INSTANCE};
                JSValue[] nextArgs = new JSValue[]{nextArgument};

                // Skip past previously-yielded values during generator replay.
                int remainingYieldSkips = vm.yieldSkipCount;
                boolean innerExhausted = false;
                while (!reuseDelegateIterator && remainingYieldSkips > 0) {
                    JSObject skipResult = callDelegateNext(executionContext, iteratorObj, nextMethod,
                            undefinedNextArgs);
                    if (skipResult == null) {
                        return;
                    }
                    JSValue skipDone = skipResult.get(PropertyKey.DONE);
                    if (capturePendingException(executionContext)) {
                        return;
                    }
                    if (JSTypeConversions.toBoolean(skipDone).value()) {
                        // Inner iterator exhausted during skip
                        JSValue value = skipResult.get(PropertyKey.VALUE);
                        if (capturePendingException(executionContext)) {
                            return;
                        }
                        vm.valueStack.push(value);
                        innerExhausted = true;
                        break;
                    }
                    remainingYieldSkips--;
                }
                vm.yieldSkipCount = remainingYieldSkips;

                if (!innerExhausted) {
                    JSObject result = callDelegateNext(executionContext, iteratorObj, nextMethod, nextArgs);
                    if (result == null) {
                        return;
                    }

                    // Check if the inner iterator is done
                    JSValue doneValue = result.get(PropertyKey.DONE);
                    if (capturePendingException(executionContext)) {
                        return;
                    }
                    if (JSTypeConversions.toBoolean(doneValue).value()) {
                        if (reuseDelegateIterator && vm.yieldSkipCount > 0) {
                            vm.yieldSkipCount--;
                        }
                        // Inner iterator done - yield* expression value is the final value
                        JSValue value = result.get(PropertyKey.VALUE);
                        if (capturePendingException(executionContext)) {
                            return;
                        }
                        vm.valueStack.push(value);
                        // Don't set yieldResult - the yield* expression completes
                    } else {
                        // Set yield result to the raw iterator result object
                        yieldDelegateResult(executionContext, iteratorObj, result, null, executionContext.pc, false);
                    }
                }
            }
            if (vm.yieldResult != null) {
                executionContext.sp = vm.valueStack.stackTop;
                JSValue returnValue = executionContext.pop();
                // Resume at the same YIELD_STAR opcode until delegation completes.
                suspendGenerator(executionContext, returnValue);
                return;
            }
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        } catch (JSVirtualMachineException exception) {
            vm.capturePendingExceptionFromVmOrContext(exception);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        }
    }

    private static void incrementLocal(Opcode op, ExecutionContext executionContext, int delta) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue localValue = executionContext.locals[localIndex];
        executionContext.locals[localIndex] = executionContext.virtualMachine.incrementValue(localValue, delta);
        executionContext.pc = pc + op.getSize();
    }

    private static void incrementTop(Opcode op, ExecutionContext executionContext, int delta) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.incrementValue(operand, delta));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    /**
     * Create VarRef array from capture source info during FCLOSURE. For LOCAL sources, creates/reuses a VarRef pointing
     * to the parent's local slot. For VAR_REF sources, shares the parent's existing VarRef.
     */
    private static VarRef[] internalCreateVarRefsFromCaptures(int[] captureInfos, StackFrame parentFrame) {
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

    private static boolean internalDeleteDynamicVarBinding(StackFrame stackFrame, String variableName) {
        if (stackFrame.hasDynamicVarBindingAlias(variableName)) {
            return false;
        }
        return stackFrame.removeDynamicVarBinding(variableName);
    }

    private static IdentityHashMap<JSSymbol, JSSymbol> internalGetActivePrivateSymbolRemap(
            ExecutionContext executionContext, int sp) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] instanceof JSBytecodeFunction stackFunction) {
                IdentityHashMap<JSSymbol, JSSymbol> stackRemap = stackFunction.getClassPrivateSymbolRemap();
                if (stackRemap != null && !stackRemap.isEmpty()) {
                    return stackRemap;
                }
            }
        }
        if (executionContext.frame.getFunction() instanceof JSBytecodeFunction frameFunction) {
            IdentityHashMap<JSSymbol, JSSymbol> frameRemap = frameFunction.getClassPrivateSymbolRemap();
            if (frameRemap != null && !frameRemap.isEmpty()) {
                return frameRemap;
            }
        }
        return null;
    }

    private static void internalHandleCall(ExecutionContext executionContext, int argCount, boolean directEvalSyntax) {
        VirtualMachine virtualMachine = executionContext.virtualMachine;
        JSContext context = virtualMachine.context;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;

        // Stack layout (bottom to top): method, receiver, arg1, arg2, ...
        // Peek at callee to decide buffer strategy (callee is below receiver and args)
        int calleeIndex = directEvalSyntax ? (sp - argCount - 1) : (sp - argCount - 2);
        JSValue calleePreview = (JSValue) stack[calleeIndex];
        boolean useBorrowedBuffer = calleePreview instanceof JSBytecodeFunction;

        // Pop arguments from stack — reuse buffer for bytecode functions
        JSValue[] args;
        if (useBorrowedBuffer) {
            args = virtualMachine.borrowArgsBuffer(argCount);
        } else {
            args = argCount == 0 ? JSValue.NO_ARGS : new JSValue[argCount];
        }
        for (int i = argCount - 1; i >= 0; i--) {
            args[i] = (JSValue) stack[--sp];
        }

        JSValue receiver = JSUndefined.INSTANCE;
        if (!directEvalSyntax) {
            receiver = (JSValue) stack[--sp];
        }

        // Pop callee (method)
        JSValue callee = (JSValue) stack[--sp];

        // SWAP locks property tracking while evaluating method-call arguments.
        // Unlock before invoking the callee so nested calls can build their own chains.
        virtualMachine.valueStack.stackTop = sp;

        // Fast path for native functions (most common case for built-in method calls).
        // Checked first to avoid Proxy/constructor-type/class-constructor checks on every call.
        if (callee instanceof JSNativeFunction nativeFunc) {
            JSConstructorType ctorType = nativeFunc.getConstructorType();
            if (ctorType == JSConstructorType.SYMBOL_OBJECT) {
                JSValue result = SymbolConstructor.call(context, receiver, args);
                stack[sp++] = result;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
            }
            if (ctorType == JSConstructorType.BIG_INT_OBJECT) {
                JSValue result = BigIntConstructor.call(context, receiver, args);
                stack[sp++] = result;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
            }
            if (nativeFunc.requiresNew()) {
                JSContext errorContext = nativeFunc.getRealmContext() != null ? nativeFunc.getRealmContext() : context;
                String constructorName = nativeFunc.getName() != null ? nativeFunc.getName() : "constructor";
                String errorMessage = switch (constructorName) {
                    case JSPromise.NAME -> "Promise constructor cannot be invoked without 'new'";
                    default -> "Constructor " + constructorName + " requires 'new'";
                };
                virtualMachine.pendingException = errorContext.throwTypeError(errorMessage);
                errorContext.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
            }
            try {
                JSValue result;
                if (directEvalSyntax && JSKeyword.EVAL.equals(nativeFunc.getName())) {
                    JSContext evalRealmContext = nativeFunc.getRealmContext() != null
                            ? nativeFunc.getRealmContext()
                            : context;
                    result = JSGlobalObject.GlobalFunction.eval(evalRealmContext, context, args, true);
                } else {
                    result = nativeFunc.call(context, receiver, args);
                }
                if (context.hasPendingException()) {
                    virtualMachine.pendingException = context.getPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = result;
                }
            } catch (JSException e) {
                virtualMachine.pendingException = e.getErrorValue();
                context.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
            } catch (JSVirtualMachineException e) {
                virtualMachine.capturePendingExceptionFromVmOrContext(e);
                stack[sp++] = JSUndefined.INSTANCE;
            } catch (JSErrorException e) {
                virtualMachine.pendingException = context.throwError(e);
                context.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
            }
        } else if (callee instanceof JSProxy proxy) {
            JSValue result = virtualMachine.proxyApply(proxy, receiver, args);
            stack[sp++] = result;
        } else if (callee instanceof JSFunction function) {
            if (function.getHomeObject() == null && receiver instanceof JSObject receiverObject
                    && function instanceof JSBytecodeFunction bytecodeFunction) {
                String functionName = bytecodeFunction.getName();
                if ("<static initializer>".equals(functionName) || "<static field initializer>".equals(functionName)) {
                    bytecodeFunction.setHomeObject(receiverObject);
                }
            }

            boolean isClassCtor = function instanceof JSClass;
            if (!isClassCtor && function instanceof JSBytecodeFunction bytecodeFunc) {
                isClassCtor = bytecodeFunc.isClassConstructor();
            }
            if (isClassCtor) {
                JSContext errorContext = function.getRealmContext() != null ? function.getRealmContext() : context;
                virtualMachine.pendingException = errorContext
                        .throwTypeError("Class constructor " + function.getName() + " cannot be invoked without 'new'");
                errorContext.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
            }

            if (function instanceof JSBytecodeFunction bytecodeFunc) {
                try {
                    // Call through the function's call method to handle async wrapping.
                    // Pass argCount so the borrowed buffer (which may be oversized) is
                    // handled correctly by StackFrame and generator/async paths.
                    JSValue result = bytecodeFunc.call(context, receiver, args, argCount);
                    if (context.hasPendingException()) {
                        virtualMachine.pendingException = context.getPendingException();
                        stack[sp++] = JSUndefined.INSTANCE;
                    } else {
                        stack[sp++] = result;
                    }
                } catch (JSException e) {
                    virtualMachine.pendingException = e.getErrorValue();
                    context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                } catch (JSVirtualMachineException e) {
                    virtualMachine.capturePendingExceptionFromVmOrContext(e);
                    stack[sp++] = JSUndefined.INSTANCE;
                }
            } else if (function instanceof JSBoundFunction boundFunc) {
                // Call bound function - the receiver is ignored for bound functions
                try {
                    JSValue result = boundFunc.call(context, receiver, args);
                    // Check for pending exception after bound function call
                    if (context.hasPendingException()) {
                        virtualMachine.pendingException = context.getPendingException();
                        stack[sp++] = JSUndefined.INSTANCE;
                    } else {
                        stack[sp++] = result;
                    }
                } catch (JSVirtualMachineException e) {
                    virtualMachine.capturePendingExceptionFromVmOrContext(e);
                    stack[sp++] = JSUndefined.INSTANCE;
                }
            } else {
                stack[sp++] = JSUndefined.INSTANCE;
            }
            // Clear property access tracking after successful call
        } else {
            // Not a function - set pending TypeError so JS catch handlers can process it
            virtualMachine.pendingException = context
                    .throwTypeError(describeCallee(executionContext, callee) + " is not a function");
            context.clearPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        }

        executionContext.sp = sp;
        virtualMachine.valueStack.stackTop = sp;
    }

    private static boolean internalHasDirectEvalCall(JSBytecodeFunction bytecodeFunction) {
        if (bytecodeFunction == null || bytecodeFunction.getBytecode() == null) {
            return false;
        }
        Opcode[] decodedOpcodes = bytecodeFunction.getBytecode().getDecodedOpcodes();
        if (decodedOpcodes == null) {
            return false;
        }
        for (Opcode decodedOpcode : decodedOpcodes) {
            if (decodedOpcode == Opcode.EVAL || decodedOpcode == Opcode.APPLY_EVAL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attach lexical state and realm metadata shared by both closure opcodes. Stack: unchanged; the caller pushes the
     * initialized closure.
     */
    private static void internalInitializeClosure(ExecutionContext executionContext, JSBytecodeFunction closureTemplate,
            JSBytecodeFunction closureFunction, IdentityHashMap<JSSymbol, JSSymbol> symbolRemap) {
        if (symbolRemap != null && !symbolRemap.isEmpty()) {
            closureFunction.setClassPrivateSymbolRemap(symbolRemap);
        }
        StackFrame evalDynamicScopeFrame = internalResolveEvalDynamicScopeFrame(executionContext);
        if (evalDynamicScopeFrame == null && internalHasDirectEvalCall(closureTemplate)
                && executionContext.frame != null) {
            evalDynamicScopeFrame = executionContext.frame;
        }
        if (evalDynamicScopeFrame != null) {
            closureFunction.setEvalDynamicScopeLookupEnabled(true);
            closureFunction.setEvalDynamicScopeFrame(evalDynamicScopeFrame);
        }
        String importMetaFilename = null;
        JSFunction enclosingFunction = executionContext.frame.getFunction();
        if (enclosingFunction != null) {
            importMetaFilename = enclosingFunction.getImportMetaFilename();
            closureFunction.setModuleEnvironment(enclosingFunction.getModuleEnvironment());
            // A function defined by eval code is located in that eval in a stack trace, and it belongs to the
            // eval's lexical environment.
            closureFunction.setEvalOrigin(enclosingFunction.getEvalOrigin());
            closureFunction.setEvalEnvironment(enclosingFunction.getEvalEnvironment());
        }
        if (importMetaFilename == null || importMetaFilename.isEmpty()) {
            JSStackFrame currentStackFrame = executionContext.virtualMachine.context.getCurrentStackFrame();
            if (currentStackFrame != null) {
                importMetaFilename = currentStackFrame.filename();
            }
        }
        closureFunction.setImportMetaFilename(importMetaFilename);
        // Arrow functions capture this, arguments, new.target, active function, and home object from the enclosing
        // scope
        if (closureFunction.isArrow()) {
            VarRef derivedThisRef = executionContext.frame.getDerivedThisRef();
            if (derivedThisRef != null) {
                closureFunction.setCapturedDerivedThisRef(derivedThisRef);
            }
            closureFunction.setCapturedThisArg(executionContext.frame.getThisArg());
            // Capture new.target and active function lexically from enclosing function
            JSFunction enclosingFunc = executionContext.frame.getFunction();
            if (enclosingFunc instanceof JSBytecodeFunction enclosingBf
                    && (enclosingBf.isArrow() || enclosingBf.isEvalSuperCallAllowed())) {
                // Nested arrow or arrow inside eval: propagate captured values from parent
                closureFunction.setCapturedArguments(enclosingBf.getCapturedArguments());
                closureFunction.setCapturedNewTarget(enclosingBf.getCapturedNewTarget());
                closureFunction.setCapturedActiveFunction(enclosingBf.getCapturedActiveFunction());
                // Propagate home object for super access
                if (enclosingBf.getHomeObject() != null) {
                    closureFunction.setHomeObject(enclosingBf.getHomeObject());
                }
            } else if (enclosingFunc != null) {
                // Direct arrow inside a regular function: capture from current frame
                boolean mapped = executionContext.virtualMachine.shouldUseMappedArguments(enclosingFunc);
                closureFunction.setCapturedArguments(executionContext.virtualMachine
                        .createArgumentsObject(executionContext.frame, enclosingFunc, mapped));
                closureFunction.setCapturedNewTarget(executionContext.frame.getNewTarget());
                closureFunction.setCapturedActiveFunction(enclosingFunc);
                // Capture home object for super property access
                if (enclosingFunc.getHomeObject() != null) {
                    closureFunction.setHomeObject(enclosingFunc.getHomeObject());
                }
            } else {
                closureFunction.setCapturedNewTarget(executionContext.frame.getNewTarget());
            }
        }
        // Set newTargetAllowed: regular functions always allow new.target,
        // arrows inherit from enclosing function (for eval() to check).
        if (closureFunction.isArrow()) {
            if (enclosingFunction instanceof JSBytecodeFunction enclosingBf) {
                closureFunction.setNewTargetAllowed(enclosingBf.isNewTargetAllowed());
            }
        } else {
            closureFunction.setNewTargetAllowed(true);
        }
        closureFunction.initializePrototypeChain(executionContext.virtualMachine.context);
    }

    private static StackFrame internalResolveEvalDynamicScopeFrame(ExecutionContext executionContext) {
        if (executionContext.frame == null
                || !(executionContext.frame.getFunction() instanceof JSBytecodeFunction currentBytecodeFunction)) {
            return null;
        }
        JSContext context = executionContext.virtualMachine.context;
        if (context.hasEvalOverlayFrames()) {
            // The creating frame owns eval-local let and const bindings. Its caller chain
            // supplies the outer scopes when the closure itself performs a direct eval.
            return executionContext.frame;
        }
        if (currentBytecodeFunction.isEvalDynamicScopeLookupEnabled()) {
            if (executionContext.frame.getCaller() != null) {
                return executionContext.frame;
            }
            return currentBytecodeFunction.getEvalDynamicScopeFrame();
        }
        return null;
    }

    private static StackFrame internalResolveEvalDynamicScopeFrameForCurrentFunction(
            ExecutionContext executionContext) {
        if (executionContext.frame == null
                || !(executionContext.frame.getFunction() instanceof JSBytecodeFunction currentBytecodeFunction)
                || !currentBytecodeFunction.isEvalDynamicScopeLookupEnabled()) {
            return null;
        }
        return currentBytecodeFunction.getEvalDynamicScopeFrame();
    }

    private static IdentityHashMap<JSSymbol, JSSymbol> internalResolvePrivateSymbolRemap(
            ExecutionContext executionContext, JSBytecodeFunction templateFunction, int pc, Opcode op, int sp) {
        IdentityHashMap<JSSymbol, JSSymbol> activeRemap = internalGetActivePrivateSymbolRemap(executionContext, sp);
        if (internalStartsClassDefinition(executionContext, pc, op)) {
            Set<JSSymbol> classPrivateSymbols = templateFunction.getClassPrivateSymbols();
            if (classPrivateSymbols != null && !classPrivateSymbols.isEmpty()) {
                IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = new IdentityHashMap<>();
                if (activeRemap != null && !activeRemap.isEmpty()) {
                    symbolRemap.putAll(activeRemap);
                }
                for (JSSymbol privateSymbol : classPrivateSymbols) {
                    symbolRemap.put(privateSymbol, new JSSymbol(privateSymbol.getDescription()));
                }
                return symbolRemap;
            }
        }
        return activeRemap;
    }

    /**
     * Create VarRef array from capture source info during FCLOSURE. For LOCAL sources, creates/reuses a VarRef pointing
     * to the parent's local slot. For VAR_REF sources, shares the parent's existing VarRef.
     */
    private static void internalStartGeneratorReturnCompletion(ExecutionContext executionContext, JSValue returnValue) {
        executionContext.virtualMachine.generatorReturnValue = returnValue;
        executionContext.virtualMachine.pendingException = VirtualMachine.GENERATOR_RETURN_SIGNAL;
        executionContext.virtualMachine.context.setPendingException(VirtualMachine.GENERATOR_RETURN_SIGNAL);
    }

    private static boolean internalStartsClassDefinition(ExecutionContext executionContext, int pc, Opcode op) {
        int nextPc = pc + op.getSize();
        if (nextPc >= executionContext.instructions.length) {
            return false;
        }
        Opcode nextOpcode = executionContext.decodedOpcodes[nextPc];
        return nextOpcode == Opcode.DEFINE_CLASS || nextOpcode == Opcode.DEFINE_CLASS_COMPUTED;
    }

    private static void postIncrement(Opcode op, ExecutionContext executionContext, int delta) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue oldValue = executionContext.virtualMachine.toNumericValue(operand);
        executionContext.virtualMachine.valueStack.push(oldValue);
        executionContext.virtualMachine.valueStack
                .push(executionContext.virtualMachine.incrementNumericValue(oldValue, delta));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    private static void pushClosure(Opcode op, ExecutionContext executionContext, int functionIndex, boolean bindSelf) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue functionValue = executionContext.bytecode.getConstants()[functionIndex];
        if (functionValue instanceof JSBytecodeFunction templateFunction) {
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = internalResolvePrivateSymbolRemap(executionContext,
                    templateFunction, pc, op, sp);
            JSBytecodeFunction closureTemplate = templateFunction;
            if (symbolRemap != null && !symbolRemap.isEmpty()) {
                closureTemplate = templateFunction.copyTemplateWithRemappedPrivateSymbols(symbolRemap);
            }
            int[] captureInfos = closureTemplate.getCaptureSourceInfos();
            JSBytecodeFunction closureFunction;
            if (captureInfos != null) {
                VarRef[] capturedVarRefs = internalCreateVarRefsFromCaptures(captureInfos, executionContext.frame);
                closureFunction = closureTemplate.copyWithVarRefs(capturedVarRefs);
                int selfIndex = bindSelf ? closureTemplate.getSelfCaptureIndex() : -1;
                if (selfIndex >= 0 && selfIndex < capturedVarRefs.length) {
                    capturedVarRefs[selfIndex].set(closureFunction);
                }
            } else {
                int closureCount = closureTemplate.getClosureVars().length;
                JSValue[] capturedClosureVars = new JSValue[closureCount];
                for (int i = closureCount - 1; i >= 0; i--) {
                    capturedClosureVars[i] = (JSValue) stack[--sp];
                }
                closureFunction = closureTemplate.copyWithClosureVars(capturedClosureVars);
                int selfIndex = bindSelf ? closureTemplate.getSelfCaptureIndex() : -1;
                if (selfIndex >= 0 && selfIndex < capturedClosureVars.length) {
                    capturedClosureVars[selfIndex] = closureFunction;
                }
            }
            internalInitializeClosure(executionContext, closureTemplate, closureFunction, symbolRemap);
            stack[sp++] = closureFunction;
        } else {
            if (functionValue instanceof JSFunction function) {
                function.initializePrototypeChain(executionContext.virtualMachine.context);
            }
            stack[sp++] = functionValue;
        }
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    private static void pushLocal(Opcode op, ExecutionContext executionContext, int index) {
        JSValue value = executionContext.locals[index];
        executionContext.virtualMachine.valueStack.stack[executionContext.sp++] = value;
        executionContext.pc += op.getSize();
    }

    private static void raiseReferenceError(ExecutionContext executionContext, String message) {
        setErrorAsPending(executionContext, executionContext.virtualMachine.context.throwReferenceError(message));
    }

    private static void raiseTypeError(ExecutionContext executionContext, String message) {
        setErrorAsPending(executionContext, executionContext.virtualMachine.context.throwTypeError(message));
    }

    private static JSValue readNamedBindingValue(ExecutionContext executionContext, String variableName,
            JSValue value) {
        if (value == VirtualMachine.UNINITIALIZED_MARKER) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwReferenceError("Cannot access '" + variableName + "' before initialization");
            return JSUndefined.INSTANCE;
        }
        return value;
    }

    private static JSValue readVarRefValue(ExecutionContext executionContext, int varRefIndex) {
        String capturedVarName = null;
        if (executionContext.frame.getFunction() instanceof JSBytecodeFunction bytecodeFunction) {
            capturedVarName = bytecodeFunction.getCapturedVarName(varRefIndex);
        }
        if (capturedVarName != null && executionContext.frame.hasDynamicVarBinding(capturedVarName)) {
            JSValue dynamicValue = executionContext.frame.getDynamicVarBinding(capturedVarName);
            return dynamicValue != null ? dynamicValue : JSUndefined.INSTANCE;
        }
        return executionContext.frame.getVarRef(varRefIndex);
    }

    /**
     * The binding an active direct eval overlays for {@code name} as the running function sees it, or null. The
     * function sees an overlay when it is that eval's code, was created inside it, or runs an eval nested in it. A
     * function the eval merely calls, or one an earlier eval created, resolves its own free names, so a module's import
     * keeps its meaning there even while an unrelated overlay shadows the same name on the global object; and a
     * function created in an outer eval reads the outer caller's binding even while an inner eval's overlay holds the
     * global property for a local of its own.
     */
    private static JSEvalOverlayBinding resolveVisibleEvalOverlayBinding(ExecutionContext executionContext,
            String name) {
        JSFunction function = executionContext.frame != null ? executionContext.frame.getFunction() : null;
        JSEvalEnvironment environment = function != null ? function.getEvalEnvironment() : null;
        return environment == null
                ? null
                : executionContext.virtualMachine.context.resolveEvalOverlayBinding(name, environment);
    }

    private static void restoreForOfStateWithoutIteratorCloseMarker(ExecutionContext executionContext,
            JSStackValue[] preservedMarkers, int preservedMarkerCount, int depth) {
        if (preservedMarkers != null) {
            for (int markerOffset = preservedMarkerCount - 1; markerOffset >= 0; markerOffset--) {
                executionContext.virtualMachine.valueStack.pushStackValue(preservedMarkers[markerOffset]);
            }
        }
        for (int valueOffset = 0; valueOffset < depth; valueOffset++) {
            executionContext.virtualMachine.forOfTempValues[valueOffset] = null;
        }
    }

    /** Returns true when delegation has left a value on the stack for the opcode to finish. */
    private static boolean resumeAbruptDelegate(Opcode op, ExecutionContext executionContext, JSObject iterator,
            JSGeneratorState.ResumeRecord record, boolean nativeAsync) {
        VirtualMachine vm = executionContext.virtualMachine;
        boolean synchronous = op == Opcode.YIELD_STAR;
        boolean returning = record.kind() == JSGeneratorState.ResumeKind.RETURN;
        vm.generatorResumeIndex++;
        JSValue method = iterator.get(returning ? PropertyKey.RETURN : PropertyKey.THROW);
        if (capturePendingException(executionContext)) {
            return false;
        }
        JSValue value = record.value();
        if (!method.isNullOrUndefined()) {
            JSObject result = callDelegateMethod(executionContext, iterator, method, value,
                    returning ? "iterator return is not a function" : "iterator throw is not a function");
            if (result == null) {
                return false;
            }
            JSValue done = result.get(PropertyKey.DONE);
            if (capturePendingException(executionContext)) {
                return false;
            }
            if (!JSTypeConversions.toBoolean(done).value()) {
                yieldDelegateResult(executionContext, iterator, result, null, synchronous ? executionContext.pc : -1,
                        nativeAsync);
                return true;
            }
            value = result.get(PropertyKey.VALUE);
            if (capturePendingException(executionContext)) {
                return false;
            }
        } else if (!returning) {
            JSValue close = iterator.get(PropertyKey.RETURN);
            if (synchronous && capturePendingException(executionContext)) {
                return false;
            }
            if (JSTypeChecking.isCallable(close)) {
                callCallableValue(vm.context, close, iterator, JSValue.NO_ARGS);
                if (synchronous && capturePendingException(executionContext)) {
                    return false;
                }
            }
            raiseTypeError(executionContext, "iterator does not have a throw method");
            return false;
        }
        if (returning && synchronous) {
            internalStartGeneratorReturnCompletion(executionContext, value);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return false;
        }
        vm.valueStack.push(value);
        return true;
    }

    private static void setErrorAsPending(ExecutionContext executionContext, JSValue errorValue) {
        executionContext.virtualMachine.pendingException = errorValue;
        executionContext.virtualMachine.context.clearPendingException();
    }

    private static void shiftSigned(Opcode op, ExecutionContext executionContext, boolean leftShift) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            int leftInt = JSTypeConversions.toInt32(leftNumber.value());
            int rightInt = JSTypeConversions.toInt32(rightNumber.value());
            stack[sp - 2] = JSNumber.of(leftShift ? leftInt << rightInt : leftInt >> rightInt);
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine
                        .shiftBigInt((JSBigInt) pair.left(), (JSBigInt) pair.right(), leftShift));
            } else {
                int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left());
                int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
                executionContext.virtualMachine.valueStack
                        .push(JSNumber.of(leftShift ? leftInt << rightInt : leftInt >> rightInt));
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    private static boolean shouldScanClosureVarRefsForGetVar(ExecutionContext executionContext) {
        JSContext context = executionContext.virtualMachine.context;
        if (context.hasEvalOverlayFrames()) {
            return true;
        } else {
            JSFunction currentFunction = executionContext.frame != null ? executionContext.frame.getFunction() : null;
            if (currentFunction instanceof JSBytecodeFunction currentBytecodeFunction) {
                return currentBytecodeFunction.isEvalDynamicScopeLookupEnabled();
            } else {
                return false;
            }
        }
    }

    private static void startForOf(ExecutionContext executionContext, JSValue iterable) {
        VirtualMachine vm = executionContext.virtualMachine;
        // Auto-box primitives (strings, numbers, etc.) to access their Symbol.iterator
        JSObject iterableObj = vm.toObject(iterable);
        if (iterableObj == null) {
            vm.pendingException = vm.context.throwTypeError("Object is not iterable");
            return;
        }
        // Get Symbol.iterator method
        JSValue iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);
        if (capturePendingException(executionContext)) {
            return;
        }

        if (!JSTypeChecking.isCallable(iteratorMethod)) {
            vm.pendingException = vm.context.throwTypeError("Object is not iterable");
            return;
        }

        // Call the Symbol.iterator method to get an iterator
        // Use the original iterable value for the 'this' binding, not the boxed version
        JSValue iterator;
        try {
            iterator = callCallableValue(vm.context, iteratorMethod, iterable, JSValue.NO_ARGS);
        } catch (JSException e) {
            vm.pendingException = e.getErrorValue();
            vm.context.clearPendingException();
            return;
        } catch (JSVirtualMachineException e) {
            vm.capturePendingExceptionFromVmOrContext(e);
            return;
        }
        if (capturePendingException(executionContext)) {
            return;
        }

        if (!(iterator instanceof JSObject iteratorObj)) {
            vm.pendingException = vm.context.throwTypeError("Iterator method must return an object");
            return;
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
        if (capturePendingException(executionContext)) {
            return;
        }

        // Per spec (GetIterator step 5): store the next value as-is.
        if (nextMethod == null) {
            nextMethod = JSUndefined.INSTANCE;
        }

        vm.clearForOfIteratorExhausted(iteratorObj);
        // Push iterator, next method, and catch offset onto the stack.
        vm.valueStack.push(iterator); // Iterator object
        vm.valueStack.push(nextMethod); // next() method
        vm.valueStack.pushStackValue(JSCatchOffset.ITERATOR_CLOSE_MARKER);
    }

    private static void suspendGenerator(ExecutionContext executionContext, JSValue value) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.saveActiveGeneratorSuspendedExecutionState(executionContext.frame, executionContext.pc, vm.valueStack.stack,
                executionContext.sp, executionContext.frameStackBase);
        vm.requestOpcodeReturnFromExecute(executionContext, value);
    }

    private static void writeVarRefValue(ExecutionContext executionContext, int varRefIndex, JSValue value) {
        String capturedVarName = null;
        if (executionContext.frame.getFunction() instanceof JSBytecodeFunction bytecodeFunction) {
            capturedVarName = bytecodeFunction.getCapturedVarName(varRefIndex);
        }
        if (capturedVarName != null && executionContext.frame.hasDynamicVarBinding(capturedVarName)) {
            executionContext.frame.setDynamicVarBinding(capturedVarName, value);
            return;
        }
        executionContext.frame.setVarRef(varRefIndex, value);
    }

    private static void yieldDelegateResult(ExecutionContext executionContext, JSObject iterator, JSObject result,
            JSValue nextMethod, int resumePc, boolean nativeAsync) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result, iterator, nextMethod, resumePc,
                nativeAsync);
        vm.valueStack.push(result);
    }

    private record EvalScopedLocalBinding(StackFrame frame, int localIndex, JSValue value) {
    }
}
