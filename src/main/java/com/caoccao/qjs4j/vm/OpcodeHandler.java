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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class OpcodeHandler {
    private OpcodeHandler() {
    }

    /**
     * Convert a JSVirtualMachineException (thrown from a nested execute() call)
     * into a VM pendingException so the outer execution loop can route it to
     * the appropriate JS try-catch handler.
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

    private static void captureVmExceptionAsPending(ExecutionContext executionContext, JSVirtualMachineException e) {
        JSValue errorValue = e.getJsValue() != null ? e.getJsValue() : e.getJsError();
        if (errorValue != null) {
            executionContext.virtualMachine.pendingException = errorValue;
        } else {
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwError(
                            e.getMessage() != null ? e.getMessage() : "Unknown error");
            executionContext.virtualMachine.context.clearPendingException();
        }
    }

    private static void checkPendingException(JSContext context) {
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            throw new JSVirtualMachineException(exception.toString(), exception);
        }
    }

    private static JSValue clearAndGetPendingException(JSContext context) {
        JSValue exceptionValue = context.getPendingException();
        context.clearPendingException();
        return exceptionValue;
    }

    private static Map<String, String> collectImportAttributes(JSContext context, JSValue optionsValue) {
        if (optionsValue instanceof JSUndefined) {
            return null;
        }
        if (!(optionsValue instanceof JSObject optionsObject)) {
            JSValue errorValue = context.throwTypeError("options must be an object");
            JSValue pendingError = clearAndGetPendingException(context);
            throw new JSException(pendingError != null ? pendingError : errorValue);
        }

        JSValue withValue = optionsObject.get(PropertyKey.fromString("with"));
        if (context.hasPendingException()) {
            throw new JSException(clearAndGetPendingException(context));
        }
        if (withValue instanceof JSUndefined) {
            return null;
        }
        if (!(withValue instanceof JSObject withObject)) {
            JSValue errorValue = context.throwTypeError("options.with must be an object");
            JSValue pendingError = clearAndGetPendingException(context);
            throw new JSException(pendingError != null ? pendingError : errorValue);
        }

        Map<String, String> attributes = new HashMap<>();
        PropertyKey[] enumerableKeys = withObject.enumerableKeys();
        if (context.hasPendingException()) {
            throw new JSException(clearAndGetPendingException(context));
        }
        for (PropertyKey key : enumerableKeys) {
            if (!key.isString()) {
                continue;
            }
            JSValue attributeValue = withObject.get(key);
            if (context.hasPendingException()) {
                throw new JSException(clearAndGetPendingException(context));
            }
            if (!(attributeValue instanceof JSString attributeString)) {
                JSValue errorValue = context.throwTypeError("Import assertion value must be a string");
                JSValue pendingError = clearAndGetPendingException(context);
                throw new JSException(pendingError != null ? pendingError : errorValue);
            }
            attributes.put(key.asString(), attributeString.value());
        }
        return attributes;
    }

    private static JSError createCannotReadPropertiesTypeError(JSContext context, JSValue value) {
        String objectType = value instanceof JSNull ? "null" : "undefined";
        JSError jsError = context.throwTypeError("Cannot read properties of " + objectType);
        jsError.setVmMessage("value has no property");
        return jsError;
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
        if (evalDynamicScopeFrame != null
                && evalDynamicScopeFrame != currentFrame
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

    private static EvalScopedLocalBinding findEvalScopedLocalBinding(
            ExecutionContext executionContext,
            String variableName) {
        if (variableName == null) {
            return null;
        }
        JSContext context = executionContext.virtualMachine.context;
        if (!context.hasEvalOverlayFrames()) {
            return null;
        }
        boolean hasOverlayBinding = context.hasEvalOverlayBinding(variableName);
        boolean allowEvalScopedLookup = hasOverlayBinding;
        if (!allowEvalScopedLookup) {
            JSFunction currentFunction = executionContext.frame != null ? executionContext.frame.getFunction() : null;
            if (currentFunction instanceof JSBytecodeFunction currentBytecodeFunction) {
                allowEvalScopedLookup = currentBytecodeFunction.isEvalDynamicScopeLookupEnabled();
            }
        }
        if (!allowEvalScopedLookup) {
            return null;
        }
        StackFrame immediateCallerFrame = executionContext.frame.getCaller();
        IdentityHashMap<StackFrame, Boolean> visitedFrames = new IdentityHashMap<>();
        if (immediateCallerFrame != null
                && visitedFrames.put(immediateCallerFrame, Boolean.TRUE) == null
                && immediateCallerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction) {
            String[] localVarNames = bytecodeFunction.getBytecode().getLocalVarNames();
            JSValue[] localValues = immediateCallerFrame.getLocals();
            if (localVarNames != null && localValues != null) {
                for (int localIndex = 0; localIndex < localVarNames.length && localIndex < localValues.length; localIndex++) {
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
                        for (int localIndex = 0; localIndex < localVarNames.length && localIndex < localValues.length; localIndex++) {
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
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    executionContext.virtualMachine.context.clearPendingException();
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
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError(
                            "Error",
                            e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                }
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } catch (JSErrorException e) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwError(e);
                executionContext.virtualMachine.context.clearPendingException();
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
        if (leftValue == null) {
            leftValue = JSUndefined.INSTANCE;
        }
        try {
            executionContext.locals[localIndex] = executionContext.virtualMachine.addValues(leftValue, rightValue);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
            }
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.captureVMException(e);
        } catch (JSException e) {
            if (e.getErrorValue() != null) {
                executionContext.virtualMachine.pendingException = e.getErrorValue();
            } else {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError(
                        "Error",
                        e.getMessage() != null ? e.getMessage() : "Unhandled exception");
            }
            executionContext.virtualMachine.context.clearPendingException();
        } catch (JSErrorException e) {
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwError(e);
            executionContext.virtualMachine.context.clearPendingException();
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleAnd(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            int leftInt = JSTypeConversions.toInt32(leftNumber.value());
            int rightInt = JSTypeConversions.toInt32(rightNumber.value());
            stack[sp - 2] = JSNumber.of(leftInt & rightInt);
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            VirtualMachine.NumericPair pair;
            try {
                pair = executionContext.virtualMachine.numericPair(left, right);
            } catch (JSVirtualMachineException e) {
                captureVmExceptionAsPending(executionContext, e);
                pair = null;
            }
            if (pair != null && executionContext.virtualMachine.pendingException != null) {
                pair = null;
            }
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                JSBigInt leftBigInt = (JSBigInt) pair.left();
                JSBigInt rightBigInt = (JSBigInt) pair.right();
                executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().and(rightBigInt.value())));
            } else {
                int result = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left()) & JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
                executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
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

        int position = (int) positionNumber.value();

        try {
            JSContext context = executionContext.virtualMachine.context;
            JSValue iterator = JSIteratorHelper.getIterator(context, enumerableObject);
            if (context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = context.getPendingException();
                context.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            if (iterator == null) {
                executionContext.virtualMachine.pendingException = context.throwTypeError("Value is not iterable");
                context.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }

            while (true) {
                JSObject resultObject = JSIteratorHelper.iteratorNext(iterator, context);
                if (context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = context.getPendingException();
                    context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                if (resultObject == null) {
                    break;
                }
                JSValue doneValue = resultObject.get(PropertyKey.DONE);
                if (context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = context.getPendingException();
                    context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                    break;
                }
                JSValue value = resultObject.get(PropertyKey.VALUE);
                if (context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = context.getPendingException();
                    context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                array.set(position++, value);
                if (context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = context.getPendingException();
                    context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
            }
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.captureVMException(e);
            stack[sp++] = JSUndefined.INSTANCE;
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        stack[sp++] = array;
        stack[sp++] = JSNumber.of(position);
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
                JSValue constructorNewTarget = thisArgValue;
                if (constructorNewTarget.isNullOrUndefined()) {
                    constructorNewTarget = functionValue;
                }
                result = JSReflectObject.construct(
                        executionContext.virtualMachine.context,
                        JSUndefined.INSTANCE,
                        new JSValue[]{functionValue, argsArrayValue, constructorNewTarget});
            } else {
                JSValue[] applyArgs = executionContext.virtualMachine.buildApplyArguments(argsArrayValue, true);
                if (applyArgs == null) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                if (functionValue instanceof JSProxy proxyFunction) {
                    result = executionContext.virtualMachine.proxyApply(proxyFunction, thisArgValue, applyArgs);
                } else if (functionValue instanceof JSFunction applyFunction) {
                    result = applyFunction.call(executionContext.virtualMachine.context, thisArgValue, applyArgs);
                } else {
                    executionContext.virtualMachine.pendingException =
                            executionContext.virtualMachine.context.throwTypeError("Value is not a function");
                    executionContext.virtualMachine.context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
            }
        } catch (JSVirtualMachineException exception) {
            executionContext.virtualMachine.captureVMException(exception);
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleApplyEval(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue argsArrayValue = (JSValue) stack[--sp];
        JSValue callee = (JSValue) stack[--sp];

        JSValue[] applyArgs = executionContext.virtualMachine.buildApplyArguments(argsArrayValue, true);
        if (applyArgs == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
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
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleAsyncYieldStar(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSContext context = executionContext.virtualMachine.context;

        // yield* in async generators uses GetIterator(obj, async) per ES2024 7.4.3
        JSValue asyncYieldStarIterable = executionContext.virtualMachine.valueStack.pop();

        // Convert to object if needed
        JSObject asyncYieldStarIterableObj;
        if (asyncYieldStarIterable instanceof JSObject obj) {
            asyncYieldStarIterableObj = obj;
        } else {
            asyncYieldStarIterableObj = executionContext.virtualMachine.toObject(asyncYieldStarIterable);
            if (asyncYieldStarIterableObj == null) {
                throw new JSVirtualMachineException(
                        context.throwTypeError(asyncYieldStarIterable + " is not iterable"));
            }
        }

        // GetIterator(obj, async):
        // Step 1: Try Symbol.asyncIterator (using context-aware get to trigger getters)
        JSValue asyncIteratorMethod = asyncYieldStarIterableObj.get(PropertyKey.SYMBOL_ASYNC_ITERATOR);
        checkPendingException(context);

        JSObject asyncYieldStarIteratorObj;
        boolean isNativeAsyncIterator;

        if (!asyncIteratorMethod.isNullOrUndefined()) {
            // Symbol.asyncIterator exists -- must be callable (GetMethod step 3)
            if (!JSTypeChecking.isCallable(asyncIteratorMethod)) {
                throw new JSVirtualMachineException(
                        context.throwTypeError("is not a function"));
            }
            JSValue asyncYieldStarIterator = callCallableValue(context, asyncIteratorMethod, asyncYieldStarIterable, JSValue.NO_ARGS);
            checkPendingException(context);
            if (!(asyncYieldStarIterator instanceof JSObject)) {
                throw new JSVirtualMachineException(
                        context.throwTypeError("Result of the Symbol.asyncIterator method is not an object"));
            }
            asyncYieldStarIteratorObj = (JSObject) asyncYieldStarIterator;
            isNativeAsyncIterator = true;
        } else {
            // Step 2: Fall back to Symbol.iterator
            JSValue iteratorMethod = asyncYieldStarIterableObj.get(PropertyKey.SYMBOL_ITERATOR);
            checkPendingException(context);
            if (iteratorMethod.isNullOrUndefined()) {
                throw new JSVirtualMachineException(
                        context.throwTypeError("is not a function"));
            }
            if (!JSTypeChecking.isCallable(iteratorMethod)) {
                throw new JSVirtualMachineException(
                        context.throwTypeError("is not a function"));
            }
            JSValue asyncYieldStarIterator = callCallableValue(context, iteratorMethod, asyncYieldStarIterable, JSValue.NO_ARGS);
            checkPendingException(context);
            if (!(asyncYieldStarIterator instanceof JSObject)) {
                throw new JSVirtualMachineException(
                        context.throwTypeError("Result of the Symbol.iterator method is not an object"));
            }
            asyncYieldStarIteratorObj = (JSObject) asyncYieldStarIterator;
            isNativeAsyncIterator = false;
        }

        // Check for RETURN/THROW resume records (yield* delegation protocol per ES2024 27.5.3.3)
        JSGeneratorState.ResumeRecord asyncYieldStarResumeRecord =
                executionContext.virtualMachine.generatorResumeIndex < executionContext.virtualMachine.generatorResumeRecords.size()
                        ? executionContext.virtualMachine.generatorResumeRecords.get(executionContext.virtualMachine.generatorResumeIndex)
                        : null;

        if (asyncYieldStarResumeRecord != null && asyncYieldStarResumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
            executionContext.virtualMachine.generatorResumeIndex++;
            JSValue returnValue = asyncYieldStarResumeRecord.value();

            JSValue returnMethodValue = asyncYieldStarIteratorObj.get(PropertyKey.RETURN);
            checkPendingException(context);
            boolean noReturnMethod = returnMethodValue.isNullOrUndefined();

            if (noReturnMethod) {
                executionContext.virtualMachine.valueStack.push(returnValue);
            } else {
                if (!JSTypeChecking.isCallable(returnMethodValue)) {
                    throw new JSVirtualMachineException(
                            context.throwTypeError("iterator return is not a function"));
                }

                JSValue result = callCallableValue(context, returnMethodValue, asyncYieldStarIteratorObj, new JSValue[]{returnValue});
                checkPendingException(context);
                if (!(result instanceof JSObject returnResultObj)) {
                    throw new JSVirtualMachineException(
                            context.throwTypeError("iterator must return an object"));
                }

                JSValue doneValue = returnResultObj.get(PropertyKey.DONE);
                checkPendingException(context);
                if (JSTypeConversions.toBoolean(doneValue).value()) {
                    JSValue value = returnResultObj.get(PropertyKey.VALUE);
                    checkPendingException(context);
                    executionContext.virtualMachine.valueStack.push(value);
                } else {
                    executionContext.virtualMachine.yieldResult =
                            new YieldResult(YieldResult.Type.YIELD_STAR, result, asyncYieldStarIteratorObj,
                                    null, isNativeAsyncIterator);
                    executionContext.virtualMachine.valueStack.push(result);
                }
            }
        } else if (asyncYieldStarResumeRecord != null && asyncYieldStarResumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
            executionContext.virtualMachine.generatorResumeIndex++;
            JSValue throwValue = asyncYieldStarResumeRecord.value();

            JSValue throwMethodValue = asyncYieldStarIteratorObj.get(PropertyKey.THROW);
            checkPendingException(context);
            boolean noThrowMethod = throwMethodValue.isNullOrUndefined();

            if (noThrowMethod) {
                JSValue closeMethod = asyncYieldStarIteratorObj.get(PropertyKey.RETURN);
                if (JSTypeChecking.isCallable(closeMethod)) {
                    callCallableValue(context, closeMethod, asyncYieldStarIteratorObj, JSValue.NO_ARGS);
                }
                throw new JSVirtualMachineException(
                        context.throwTypeError("iterator does not have a throw method"));
            }

            if (!JSTypeChecking.isCallable(throwMethodValue)) {
                throw new JSVirtualMachineException(
                        context.throwTypeError("iterator throw is not a function"));
            }

            JSValue result = callCallableValue(context, throwMethodValue, asyncYieldStarIteratorObj, new JSValue[]{throwValue});
            checkPendingException(context);
            if (!(result instanceof JSObject)) {
                throw new JSVirtualMachineException(
                        context.throwTypeError("iterator must return an object"));
            }

            JSValue doneValue = ((JSObject) result).get(PropertyKey.DONE);
            checkPendingException(context);
            if (JSTypeConversions.toBoolean(doneValue).value()) {
                JSValue value = ((JSObject) result).get(PropertyKey.VALUE);
                checkPendingException(context);
                executionContext.virtualMachine.valueStack.push(value);
            } else {
                executionContext.virtualMachine.yieldResult =
                        new YieldResult(YieldResult.Type.YIELD_STAR, result, asyncYieldStarIteratorObj,
                                null, isNativeAsyncIterator);
                executionContext.virtualMachine.valueStack.push(result);
            }
        } else {
            // Default: NEXT protocol -- call iterator.next()
            JSValue nextMethod = asyncYieldStarIteratorObj.get(PropertyKey.NEXT);
            checkPendingException(context);
            if (!JSTypeChecking.isCallable(nextMethod)) {
                throw new JSVirtualMachineException(
                        context.throwTypeError("is not a function"));
            }

            // Per ES2024 spec: Invoke(iterator, "next", << received.[[Value]] >>)
            JSValue[] nextArgs = new JSValue[]{JSUndefined.INSTANCE};

            // Skip past previously-yielded values during generator replay
            boolean asyncInnerExhausted = false;
            while (executionContext.virtualMachine.yieldSkipCount > 0) {
                JSValue skipResult = callCallableValue(context, nextMethod, asyncYieldStarIteratorObj, nextArgs);
                checkPendingException(context);
                if (!(skipResult instanceof JSObject)) {
                    throw new JSVirtualMachineException(
                            context.throwTypeError("Iterator result must be an object"));
                }
                JSValue skipDone = ((JSObject) skipResult).get(PropertyKey.DONE);
                checkPendingException(context);
                if (JSTypeConversions.toBoolean(skipDone).value()) {
                    JSValue value = ((JSObject) skipResult).get(PropertyKey.VALUE);
                    checkPendingException(context);
                    executionContext.virtualMachine.valueStack.push(value);
                    asyncInnerExhausted = true;
                    break;
                }
                executionContext.virtualMachine.yieldSkipCount--;
            }

            if (!asyncInnerExhausted) {
                JSValue result = callCallableValue(context, nextMethod, asyncYieldStarIteratorObj, nextArgs);
                checkPendingException(context);

                if (!(result instanceof JSObject resultObj)) {
                    throw new JSVirtualMachineException(
                            context.throwTypeError("Iterator result must be an object"));
                }

                executionContext.virtualMachine.yieldResult =
                        new YieldResult(YieldResult.Type.YIELD_STAR, result,
                                asyncYieldStarIteratorObj, nextMethod, isNativeAsyncIterator);
                executionContext.virtualMachine.valueStack.push(result);
            }
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.yieldResult != null) {
            JSValue returnValue = executionContext.pop();
            // Save execution state so the generator can resume from after ASYNC_YIELD_STAR
            // when the delegate iterator completes (done=true), avoiding side-effect replay
            executionContext.virtualMachine.saveActiveGeneratorSuspendedExecutionState(
                    executionContext.frame,
                    executionContext.pc,
                    executionContext.virtualMachine.valueStack.stack,
                    executionContext.sp,
                    executionContext.frameStackBase);
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, returnValue);
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
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
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
        if (executionContext.virtualMachine.awaitSuspensionEnabled && executionContext.virtualMachine.activeGeneratorState != null) {
            executionContext.virtualMachine.awaitSuspensionPromise = promise;
        } else {
            // Fallback for non-suspension mode: always process microtasks to ensure
            // pending reactions (e.g., Promise.resolve().then(cb)) run before code after await.
            // ES2024 25.5.5.3: await always takes at least 1 microtask tick.
            executionContext.virtualMachine.context.processMicrotasks();

            // Now the promise should be settled, push the resolved value
            if (promise.getState() == JSPromise.PromiseState.FULFILLED) {
                executionContext.virtualMachine.valueStack.push(promise.getResult());
            } else if (promise.getState() == JSPromise.PromiseState.REJECTED) {
                JSValue result = promise.getResult();
                IJSPromiseRejectCallback callback = executionContext.virtualMachine.context.getPromiseRejectCallback();
                if (callback != null) {
                    callback.callback(PromiseRejectEvent.PromiseRejectWithNoHandler, promise, result);
                }
                executionContext.virtualMachine.pendingException = result;
                executionContext.virtualMachine.context.setPendingException(result);
            } else {
                // Promise is still pending - this shouldn't happen
                throw new JSVirtualMachineException("Promise did not settle after processing microtasks");
            }
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.awaitSuspensionPromise != null) {
            executionContext.virtualMachine.saveActiveGeneratorSuspendedExecutionState(
                    executionContext.frame,
                    executionContext.pc,
                    executionContext.virtualMachine.valueStack.stack,
                    executionContext.sp,
                    executionContext.frameStackBase);
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, JSUndefined.INSTANCE);
        }
    }

    static void handleCall(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        byte[] instructions = executionContext.instructions;
        int argumentCount = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
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
        byte[] instructions = executionContext.instructions;
        int argumentCount = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Pop arguments
        JSValue[] args = argumentCount == 0 ? JSValue.NO_ARGS : new JSValue[argumentCount];
        for (int i = argumentCount - 1; i >= 0; i--) {
            args[i] = executionContext.virtualMachine.valueStack.pop();
        }
        // Pop constructor
        JSValue constructor = executionContext.virtualMachine.valueStack.pop();
        // Handle proxy construct trap (QuickJS: js_proxy_call_constructor)
        if (constructor instanceof JSProxy jsProxy) {
            // Following QuickJS JS_CallConstructorInternal:
            // Check if target is a constructor BEFORE checking for construct trap
            JSValue target = jsProxy.getTarget();
            if (JSTypeChecking.isConstructor(target)) {
                executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.proxyConstruct(jsProxy, args, jsProxy));
            } else {
                executionContext.virtualMachine.context.throwTypeError("proxy is not a constructor");
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
        } else if (constructor instanceof JSFunction jsFunction) {
            // Check if the function is constructable
            if (!JSTypeChecking.isConstructor(jsFunction)) {
                executionContext.virtualMachine.context.throwTypeError(jsFunction.getName() + " is not a constructor");
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                JSValue result;
                try {
                    result = executionContext.virtualMachine.constructFunction(jsFunction, args, jsFunction);
                } catch (JSVirtualMachineException e) {
                    if (e.getJsValue() != null) {
                        executionContext.virtualMachine.pendingException = e.getJsValue();
                    } else if (e.getJsError() != null) {
                        executionContext.virtualMachine.pendingException = e.getJsError();
                    } else if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    } else {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError(
                                e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                    }
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    result = null;
                }
                if (result != null) {
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                        executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        executionContext.virtualMachine.valueStack.push(result);
                    }
                }
            }
        } else {
            executionContext.virtualMachine.context.throwTypeError("not a constructor");
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
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
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.incrementValue(operand, -1));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleDecLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue localValue = executionContext.locals[localIndex];
        if (localValue == null) {
            localValue = JSUndefined.INSTANCE;
        }
        executionContext.locals[localIndex] = executionContext.virtualMachine.incrementValue(localValue, -1);
        executionContext.pc = pc + op.getSize();
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

        executionContext.sp = sp;
        executionContext.pc += op.getSize();
    }

    static void handleDefineClass(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        int classNameAtom = executionContext.bytecode.readU32(pc + 1);
        String className = executionContext.bytecode.getAtoms()[classNameAtom];
        JSValue constructorValue = (JSValue) stack[--sp];
        JSValue superClassValue = (JSValue) stack[--sp];

        if (!(constructorValue instanceof JSFunction constructorFunction)) {
            throw new JSVirtualMachineException("DEFINE_CLASS: constructor must be a function");
        }

        JSObject prototypeObject = executionContext.virtualMachine.context.createJSObject();
        if (superClassValue instanceof JSNull) {
            prototypeObject.setPrototype(null);
        } else if (superClassValue != JSUndefined.INSTANCE) {
            if (!(superClassValue instanceof JSObject superClassObject)
                    || !JSTypeChecking.isConstructor(superClassValue)) {
                executionContext.virtualMachine.context.throwTypeError("parent class must be constructor");
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            JSValue superPrototypeValue = superClassObject.get(PropertyKey.PROTOTYPE);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            if (superPrototypeValue instanceof JSObject superPrototypeObject) {
                prototypeObject.setPrototype(superPrototypeObject);
            } else if (superPrototypeValue instanceof JSNull) {
                prototypeObject.setPrototype(null);
            } else {
                executionContext.virtualMachine.context.throwTypeError(
                        "parent class prototype is not an object or null");
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            constructorFunction.setPrototype(superClassObject);
        }

        JSObject constructorObject = constructorFunction;
        constructorObject.defineProperty(
                PropertyKey.fromString("prototype"),
                prototypeObject,
                PropertyDescriptor.DataState.None);

        prototypeObject.defineProperty(
                PropertyKey.CONSTRUCTOR,
                constructorValue,
                PropertyDescriptor.DataState.ConfigurableWritable);
        executionContext.virtualMachine.setObjectName(constructorValue, new JSString(className));

        // Set home object on constructor for super property access in constructors
        constructorFunction.setHomeObject(prototypeObject);

        if (constructorFunction instanceof JSBytecodeFunction bytecodeConstructor) {
            bytecodeConstructor.setClassConstructor(true);
            if (superClassValue != JSUndefined.INSTANCE) {
                bytecodeConstructor.setDerivedConstructor(true);
            }
        }

        stack[sp++] = prototypeObject;
        stack[sp++] = constructorValue;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleDefineClassComputed(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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

        JSObject prototypeObject = executionContext.virtualMachine.context.createJSObject();
        boolean hasHeritage = (classFlags & 1) != 0;
        if (hasHeritage) {
            if (superClassValue instanceof JSNull) {
                prototypeObject.setPrototype(null);
            } else {
                if (!(superClassValue instanceof JSObject superClassObject)
                        || !JSTypeChecking.isConstructor(superClassValue)) {
                    executionContext.virtualMachine.context.throwTypeError("parent class must be constructor");
                    executionContext.virtualMachine.pendingException =
                            executionContext.virtualMachine.context.getPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                JSValue superPrototypeValue = superClassObject.get(PropertyKey.PROTOTYPE);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException =
                            executionContext.virtualMachine.context.getPendingException();
                    executionContext.virtualMachine.context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                if (superPrototypeValue instanceof JSObject superPrototypeObject) {
                    prototypeObject.setPrototype(superPrototypeObject);
                } else if (superPrototypeValue instanceof JSNull) {
                    prototypeObject.setPrototype(null);
                } else {
                    executionContext.virtualMachine.context.throwTypeError(
                            "parent class prototype is not an object or null");
                    executionContext.virtualMachine.pendingException =
                            executionContext.virtualMachine.context.getPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                constructorFunction.setPrototype(superClassObject);
            }
        }

        constructorFunction.defineProperty(
                PropertyKey.fromString("prototype"),
                prototypeObject,
                PropertyDescriptor.DataState.None);
        prototypeObject.defineProperty(
                PropertyKey.CONSTRUCTOR,
                constructorValue,
                PropertyDescriptor.DataState.ConfigurableWritable);
        JSString computedClassName = executionContext.virtualMachine.getComputedNameString(computedClassNameValue);
        if (computedClassName.value().isEmpty()) {
            computedClassName = new JSString(className);
        }
        executionContext.virtualMachine.setObjectName(constructorValue, computedClassName);

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
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
            boolean defineSucceeded = object.defineProperty(
                    fieldKey,
                    PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.All));
            if (!defineSucceeded) {
                throw new JSVirtualMachineException(
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot redefine property: " + fieldKey.toPropertyString()));
            }
        }

        stack[sp++] = objectValue;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
            boolean defineSucceeded = object.defineProperty(
                    methodKey,
                    PropertyDescriptor.dataDescriptor(
                            methodValue,
                            PropertyDescriptor.DataState.ConfigurableWritable));
            if (!defineSucceeded) {
                throw new JSVirtualMachineException(
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot redefine property: " + methodKey.toPropertyString()));
            }
        }

        stack[sp++] = objectValue;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
            boolean isPrivateSymbolKey = key != null
                    && key.isSymbol()
                    && key.asSymbol().getDescription() != null
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
                executionContext.virtualMachine.setObjectName(methodFunction, new JSString(namePrefix + computedName.value()));
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
                if (isPrivateSymbolKey && isProxyPrivateTarget
                        && ((JSProxy) object).hasOwnPrivatePropertyDirect(key)) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.throwTypeError(
                                    "Cannot initialize the same private elements twice on an object"));
                }
                if (isPrivateSymbolKey && !isProxyPrivateTarget && object.hasOwnProperty(key)) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.throwTypeError(
                                    "Cannot initialize the same private elements twice on an object"));
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
                    ((JSProxy) object).definePrivatePropertyDirect(
                            key, PropertyDescriptor.dataDescriptor(methodValue, dataState));
                    defineSucceeded = true;
                } else {
                    defineSucceeded = object.defineProperty(
                            key,
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
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.throwTypeError(
                                        "Cannot initialize the same private elements twice on an object"));
                    }
                }
                if (descriptor != null && descriptor.hasSetter()) {
                    setter = descriptor.getSetter();
                }
                if (isProxyPrivateTarget) {
                    ((JSProxy) object).definePrivatePropertyDirect(
                            key,
                            PropertyDescriptor.accessorDescriptor(
                                    getter,
                                    setter,
                                    enumerable
                                            ? PropertyDescriptor.AccessorState.All
                                            : PropertyDescriptor.AccessorState.Configurable));
                    defineSucceeded = true;
                } else {
                    defineSucceeded = object.defineProperty(
                            key,
                            PropertyDescriptor.accessorDescriptor(
                                    getter,
                                    setter,
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
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.throwTypeError(
                                        "Cannot initialize the same private elements twice on an object"));
                    }
                }
                if (descriptor != null && descriptor.hasGetter()) {
                    getter = descriptor.getGetter();
                }
                if (isProxyPrivateTarget) {
                    ((JSProxy) object).definePrivatePropertyDirect(
                            key,
                            PropertyDescriptor.accessorDescriptor(
                                    getter,
                                    setter,
                                    enumerable
                                            ? PropertyDescriptor.AccessorState.All
                                            : PropertyDescriptor.AccessorState.Configurable));
                    defineSucceeded = true;
                } else {
                    defineSucceeded = object.defineProperty(
                            key,
                            PropertyDescriptor.accessorDescriptor(
                                    getter,
                                    setter,
                                    enumerable
                                            ? PropertyDescriptor.AccessorState.All
                                            : PropertyDescriptor.AccessorState.Configurable));
                }
            } else {
                throw new JSVirtualMachineException("DEFINE_METHOD_COMPUTED: unsupported method flags " + methodFlags);
            }

            if (!defineSucceeded) {
                String keyName = key != null ? key.toPropertyString() : "property";
                throw new JSVirtualMachineException(
                        executionContext.virtualMachine.context.throwTypeError("Cannot redefine property: " + keyName));
            }
        }

        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot define private field on a non-extensible object");
                stack[sp++] = objectValue;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            boolean hasPrivateProperty = object instanceof JSProxy proxy
                    ? proxy.hasOwnPrivatePropertyDirect(privateKey)
                    : object.hasOwnProperty(privateKey);
            if (hasPrivateProperty) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot initialize the same private elements twice on an object");
                stack[sp++] = objectValue;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            if (object instanceof JSProxy proxy) {
                proxy.definePrivatePropertyDirect(
                        privateKey,
                        PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.ConfigurableWritable));
            } else {
                object.defineProperty(
                        privateKey,
                        PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.ConfigurableWritable));
            }
        } else if (!(objectValue instanceof JSObject)) {
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError(
                            "Cannot define private field on non-object");
        }

        stack[sp++] = objectValue;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleDelete(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue property = executionContext.virtualMachine.valueStack.pop();
        JSValue object = executionContext.virtualMachine.valueStack.pop();
        JSObject targetObject = executionContext.virtualMachine.toObject(object);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError(
                            "Cannot convert undefined or null to object");
            executionContext.virtualMachine.valueStack.push(JSBoolean.FALSE);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, property);
        boolean result = targetObject.delete(key);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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
            if (deleted
                    && context.hasEvalOverlayFrames()
                    && context.hasEvalOverlayBinding(variableName)) {
                JSObject globalObject = context.getGlobalObject();
                PropertyKey variableKey = PropertyKey.fromString(variableName);
                globalObject.delete(variableKey);
                if (context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = context.getPendingException();
                    context.clearPendingException();
                }
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

    static void handleDiv(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            if (rightBigInt.value().equals(VirtualMachine.BIGINT_ZERO)) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwRangeError("Division by zero");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().divide(rightBigInt.value())));
            }
        } else {
            JSNumber leftNumber = (JSNumber) pair.left();
            JSNumber rightNumber = (JSNumber) pair.right();
            executionContext.virtualMachine.valueStack.push(JSNumber.of(leftNumber.value() / rightNumber.value()));
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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

    static void handleEq(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            return;
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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

            // Pop arguments
            JSValue[] args = argumentCount == 0 ? JSValue.NO_ARGS : new JSValue[argumentCount];
            for (int i = argumentCount - 1; i >= 0; i--) {
                args[i] = (JSValue) stack[--sp];
            }
            // Eval syntax: no receiver pop (directEvalSyntax)
            JSValue callee = (JSValue) stack[--sp];
            executionContext.virtualMachine.propertyAccessLock = false;
            executionContext.virtualMachine.valueStack.stackTop = sp;

            // Check if callee is a non-constructor, non-async/generator bytecode function eligible for TCO
            boolean canTrampoline = false;
            if (callee instanceof JSBytecodeFunction bytecodeFunc && !(callee instanceof JSClass)) {
                canTrampoline = !bytecodeFunc.isClassConstructor()
                        && !bytecodeFunc.isAsync()
                        && !bytecodeFunc.isGenerator();
            }

            if (canTrampoline) {
                executionContext.virtualMachine.resetPropertyAccessTracking();
                executionContext.virtualMachine.tailCallPending =
                        new VirtualMachine.TailCallRequest((JSBytecodeFunction) callee, JSUndefined.INSTANCE, args);
                executionContext.virtualMachine.lastConstructorThisArg = executionContext.frame.getThisArg();
                executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
                executionContext.opcodeRequestedReturn = true;
                return;
            }

            // Not eligible for trampoline: push values back and call normally, then return
            stack[sp++] = callee;
            for (JSValue arg : args) {
                stack[sp++] = arg;
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
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            BigInteger exponent = rightBigInt.value();
            if (exponent.signum() < 0) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwRangeError("Exponent must be positive");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                final int exponentInt;
                try {
                    exponentInt = exponent.intValueExact();
                } catch (ArithmeticException e) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwRangeError("BigInt exponent is too large");
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                    executionContext.pc += op.getSize();
                    return;
                }
                executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().pow(exponentInt)));
            }
        } else {
            JSNumber leftNumber = (JSNumber) pair.left();
            JSNumber rightNumber = (JSNumber) pair.right();
            executionContext.virtualMachine.valueStack.push(JSNumber.of(Math.pow(leftNumber.value(), rightNumber.value())));
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleFclosure(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        int functionIndex = executionContext.bytecode.readU32(pc + 1);
        JSValue functionValue = executionContext.bytecode.getConstants()[functionIndex];
        if (functionValue instanceof JSBytecodeFunction templateFunction) {
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = internalResolvePrivateSymbolRemap(
                    executionContext,
                    templateFunction,
                    pc,
                    op,
                    sp
            );
            JSBytecodeFunction closureTemplate = templateFunction;
            if (symbolRemap != null && !symbolRemap.isEmpty()) {
                closureTemplate = templateFunction.copyTemplateWithRemappedPrivateSymbols(symbolRemap);
            }
            int[] captureInfos = closureTemplate.getCaptureSourceInfos();
            JSBytecodeFunction closureFunction;
            if (captureInfos != null) {
                VarRef[] capturedVarRefs = internalCreateVarRefsFromCaptures(captureInfos, executionContext.frame);
                closureFunction = closureTemplate.copyWithVarRefs(capturedVarRefs);
                int selfIndex = closureTemplate.getSelfCaptureIndex();
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
                int selfIndex = closureTemplate.getSelfCaptureIndex();
                if (selfIndex >= 0 && selfIndex < capturedClosureVars.length) {
                    capturedClosureVars[selfIndex] = closureFunction;
                }
            }
            if (symbolRemap != null && !symbolRemap.isEmpty()) {
                closureFunction.setClassPrivateSymbolRemap(symbolRemap);
            }
            StackFrame evalDynamicScopeFrame = internalResolveEvalDynamicScopeFrame(executionContext);
            if (evalDynamicScopeFrame == null
                    && internalHasDirectEvalCall(closureTemplate)
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
            }
            if (importMetaFilename == null || importMetaFilename.isEmpty()) {
                JSStackFrame currentStackFrame = executionContext.virtualMachine.context.getCurrentStackFrame();
                if (currentStackFrame != null) {
                    importMetaFilename = currentStackFrame.filename();
                }
            }
            closureFunction.setImportMetaFilename(importMetaFilename);
            // Arrow functions capture this, arguments, new.target, active function, and home object from the enclosing scope
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
                    closureFunction.setCapturedArguments(
                            executionContext.virtualMachine.createArgumentsObject(
                                    executionContext.frame, enclosingFunc, mapped));
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
            stack[sp++] = closureFunction;
        } else {
            if (functionValue instanceof JSFunction function) {
                function.initializePrototypeChain(executionContext.virtualMachine.context);
            }
            stack[sp++] = functionValue;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleFclosure8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        int functionIndex = executionContext.bytecode.readU8(pc + 1);
        JSValue functionValue = executionContext.bytecode.getConstants()[functionIndex];
        if (functionValue instanceof JSBytecodeFunction templateFunction) {
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = internalResolvePrivateSymbolRemap(
                    executionContext,
                    templateFunction,
                    pc,
                    op,
                    sp
            );
            JSBytecodeFunction closureTemplate = templateFunction;
            if (symbolRemap != null && !symbolRemap.isEmpty()) {
                closureTemplate = templateFunction.copyTemplateWithRemappedPrivateSymbols(symbolRemap);
            }
            int[] captureInfos = closureTemplate.getCaptureSourceInfos();
            JSBytecodeFunction closureFunction;
            if (captureInfos != null) {
                VarRef[] capturedVarRefs = internalCreateVarRefsFromCaptures(captureInfos, executionContext.frame);
                closureFunction = closureTemplate.copyWithVarRefs(capturedVarRefs);
            } else {
                int closureCount = closureTemplate.getClosureVars().length;
                JSValue[] capturedClosureVars = new JSValue[closureCount];
                for (int i = closureCount - 1; i >= 0; i--) {
                    capturedClosureVars[i] = (JSValue) stack[--sp];
                }
                closureFunction = closureTemplate.copyWithClosureVars(capturedClosureVars);
            }
            if (symbolRemap != null && !symbolRemap.isEmpty()) {
                closureFunction.setClassPrivateSymbolRemap(symbolRemap);
            }
            StackFrame evalDynamicScopeFrame = internalResolveEvalDynamicScopeFrame(executionContext);
            if (evalDynamicScopeFrame == null
                    && internalHasDirectEvalCall(closureTemplate)
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
            }
            if (importMetaFilename == null || importMetaFilename.isEmpty()) {
                JSStackFrame currentStackFrame = executionContext.virtualMachine.context.getCurrentStackFrame();
                if (currentStackFrame != null) {
                    importMetaFilename = currentStackFrame.filename();
                }
            }
            closureFunction.setImportMetaFilename(importMetaFilename);
            // Arrow functions capture this, arguments, new.target, active function, and home object from the enclosing scope
            if (closureFunction.isArrow()) {
                VarRef derivedThisRef = executionContext.frame.getDerivedThisRef();
                if (derivedThisRef != null) {
                    closureFunction.setCapturedDerivedThisRef(derivedThisRef);
                }
                closureFunction.setCapturedThisArg(executionContext.frame.getThisArg());
                JSFunction enclosingFunc = executionContext.frame.getFunction();
                if (enclosingFunc instanceof JSBytecodeFunction enclosingBf
                        && (enclosingBf.isArrow() || enclosingBf.isEvalSuperCallAllowed())) {
                    closureFunction.setCapturedArguments(enclosingBf.getCapturedArguments());
                    closureFunction.setCapturedNewTarget(enclosingBf.getCapturedNewTarget());
                    closureFunction.setCapturedActiveFunction(enclosingBf.getCapturedActiveFunction());
                    if (enclosingBf.getHomeObject() != null) {
                        closureFunction.setHomeObject(enclosingBf.getHomeObject());
                    }
                } else if (enclosingFunc != null) {
                    boolean mapped = executionContext.virtualMachine.shouldUseMappedArguments(enclosingFunc);
                    closureFunction.setCapturedArguments(
                            executionContext.virtualMachine.createArgumentsObject(
                                    executionContext.frame, enclosingFunc, mapped));
                    closureFunction.setCapturedNewTarget(executionContext.frame.getNewTarget());
                    closureFunction.setCapturedActiveFunction(enclosingFunc);
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
            stack[sp++] = closureFunction;
        } else {
            if (functionValue instanceof JSFunction function) {
                function.initializePrototypeChain(executionContext.virtualMachine.context);
            }
            stack[sp++] = functionValue;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("object is not async iterable");
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
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
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError("object is not async iterable");
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        } else {
            // Fall back to Symbol.iterator (sync iterator that will be auto-wrapped)
            iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);

            if (!JSTypeChecking.isCallable(iteratorMethod)) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("object is not async iterable");
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
                return;
            }
            wrapSyncIteratorAsAsync = true;
        }

        // Call the iterator method to get an iterator
        JSValue iterator;
        try {
            iterator = callCallableValue(executionContext.virtualMachine.context, iteratorMethod, iterable, JSValue.NO_ARGS);
        } catch (JSException e) {
            executionContext.virtualMachine.pendingException = e.getErrorValue();
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }

        if (!(iterator instanceof JSObject iteratorObj)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("iterator must return an object");
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);

        if (!JSTypeChecking.isCallable(nextMethod)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("iterator must have a next method");
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }

        JSValue nextMethodForStack = nextMethod;
        if (wrapSyncIteratorAsAsync) {
            final JSObject syncIteratorObject = iteratorObj;
            final JSValue syncNextCallable = nextMethod;
            nextMethodForStack = new JSNativeFunction(executionContext.virtualMachine.context, "next", 0, (childContext, thisArg, args) -> {
                JSValue syncResult;
                try {
                    syncResult = callCallableValue(childContext, syncNextCallable, syncIteratorObject, JSValue.NO_ARGS);
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
                return JSAsyncIterator.createAsyncFromSyncResultPromise(childContext, syncValue, syncDone);
            });
        }

        executionContext.virtualMachine.clearForOfIteratorExhausted(iteratorObj);
        // Push iterator, next method, and catch offset (0) onto the stack
        executionContext.virtualMachine.valueStack.push(iterator);         // Iterator object
        executionContext.virtualMachine.valueStack.push(nextMethodForStack);       // next() method
        executionContext.virtualMachine.valueStack.pushStackValue(JSCatchOffset.ITERATOR_CLOSE_MARKER);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleForInNext(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSStackValue stackValue = executionContext.virtualMachine.valueStack.popStackValue();
        if (!(stackValue instanceof JSInternalValue internal) ||
                !(internal.value() instanceof JSForInEnumerator enumerator)) {
            throw new JSVirtualMachineException("Invalid for-in enumerator");
        }
        JSValue nextKey = enumerator.next();
        executionContext.virtualMachine.valueStack.pushStackValue(new JSInternalValue(enumerator));
        executionContext.virtualMachine.valueStack.push(nextKey);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleForInStart(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue obj = executionContext.virtualMachine.valueStack.pop();
        JSForInEnumerator enumerator = new JSForInEnumerator(obj);
        executionContext.virtualMachine.valueStack.pushStackValue(new JSInternalValue(enumerator));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleForOfNext(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int depth = executionContext.bytecode.readU8(pc + 1);
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Stack layout: ... iter next catch_offset [depth values] (bottom to top)
        // The depth parameter tells us how many values are between catch_offset and top
        // Following QuickJS: offset = -3 - depth

        // Pop depth values temporarily
        if (executionContext.virtualMachine.forOfTempValues.length < depth) {
            executionContext.virtualMachine.forOfTempValues = new JSValue[Math.max(depth, executionContext.virtualMachine.forOfTempValues.length * 2)];
        }
        for (int i = 0; i < depth; i++) {
            executionContext.virtualMachine.forOfTempValues[i] = executionContext.virtualMachine.valueStack.pop();
        }
        int markerIndex = -1;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        for (int index = executionContext.virtualMachine.valueStack.stackTop - 1;
             index >= executionContext.frameStackBase;
             index--) {
            if (stack[index] instanceof JSCatchOffset catchOffset && catchOffset.isIteratorCloseMarker()) {
                markerIndex = index;
                break;
            }
        }
        if (markerIndex < 2) {
            throw new JSVirtualMachineException(
                    executionContext.virtualMachine.context.throwError("Invalid iterator state in FOR_OF_NEXT"));
        }

        int preservedMarkerCount = executionContext.virtualMachine.valueStack.stackTop - markerIndex - 1;
        JSStackValue[] preservedMarkers = preservedMarkerCount > 0 ? new JSStackValue[preservedMarkerCount] : null;
        for (int markerOffset = 0; markerOffset < preservedMarkerCount; markerOffset++) {
            preservedMarkers[markerOffset] = executionContext.virtualMachine.valueStack.popStackValue();
        }

        JSStackValue catchOffset = executionContext.virtualMachine.valueStack.popStackValue();

        int stackTop = executionContext.virtualMachine.valueStack.stackTop;
        if (stackTop < 2) {
            throw new JSVirtualMachineException(
                    executionContext.virtualMachine.context.throwError("Invalid iterator stack depth in FOR_OF_NEXT"));
        }
        JSStackValue nextMethodStackValue = executionContext.virtualMachine.valueStack.stack[stackTop - 1];
        JSStackValue iteratorStackValue = executionContext.virtualMachine.valueStack.stack[stackTop - 2];
        JSValue nextMethod = nextMethodStackValue instanceof JSValue jsValue ? jsValue : null;
        JSValue iterator = iteratorStackValue instanceof JSValue jsValue ? jsValue : null;

        boolean done = false;
        JSValue value = JSUndefined.INSTANCE;
        boolean iteratorAlreadyDone = iterator instanceof JSObject iteratorObject
                && executionContext.virtualMachine.isForOfIteratorExhausted(iteratorObject);

        if (!iteratorAlreadyDone) {
            // Call iterator.next()
            if (!JSTypeChecking.isCallable(nextMethod)) {
                String actualType = nextMethodStackValue == null ? "null" : nextMethodStackValue.getClass().getSimpleName();
                String iterType = iteratorStackValue == null ? "null" : iteratorStackValue.getClass().getSimpleName();
                restoreForOfStateWithoutIteratorCloseMarker(
                        executionContext,
                        preservedMarkers,
                        preservedMarkerCount,
                        depth);
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError(
                        "Next method must be a function in FOR_OF_NEXT (nextMethod="
                                + actualType + ", iterator=" + iterType + ")");
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc = pc + op.getSize();
                return;
            }

            JSValue result;
            try {
                result = callCallableValue(executionContext.virtualMachine.context, nextMethod, iterator, JSValue.NO_ARGS);
            } catch (JSException e) {
                restoreForOfStateWithoutIteratorCloseMarker(
                        executionContext,
                        preservedMarkers,
                        preservedMarkerCount,
                        depth);
                executionContext.virtualMachine.pendingException = e.getErrorValue();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc = pc + op.getSize();
                return;
            } catch (JSVirtualMachineException e) {
                restoreForOfStateWithoutIteratorCloseMarker(
                        executionContext,
                        preservedMarkers,
                        preservedMarkerCount,
                        depth);
                executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc = pc + op.getSize();
                return;
            }

            // Check for pending exception (e.g., TypedArray detachment during iteration)
            if (executionContext.virtualMachine.context.hasPendingException()) {
                restoreForOfStateWithoutIteratorCloseMarker(
                        executionContext,
                        preservedMarkers,
                        preservedMarkerCount,
                        depth);
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc = pc + op.getSize();
                return;
            }

            if (!(result instanceof JSObject resultObj)) {
                restoreForOfStateWithoutIteratorCloseMarker(
                        executionContext,
                        preservedMarkers,
                        preservedMarkerCount,
                        depth);
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError("Iterator result must be an object");
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc = pc + op.getSize();
                return;
            }

            // Get the done property
            JSValue doneValue = resultObj.get(PropertyKey.DONE);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                restoreForOfStateWithoutIteratorCloseMarker(
                        executionContext,
                        preservedMarkers,
                        preservedMarkerCount,
                        depth);
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc = pc + op.getSize();
                return;
            }
            done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;
            if (done) {
                if (iterator instanceof JSObject iteratorObject) {
                    executionContext.virtualMachine.markForOfIteratorExhausted(iteratorObject);
                }
            } else {
                if (iterator instanceof JSObject iteratorObject) {
                    executionContext.virtualMachine.clearForOfIteratorExhausted(iteratorObject);
                }
                value = resultObj.get(PropertyKey.VALUE);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    restoreForOfStateWithoutIteratorCloseMarker(
                            executionContext,
                            preservedMarkers,
                            preservedMarkerCount,
                            depth);
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                    executionContext.pc = pc + op.getSize();
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
        executionContext.virtualMachine.valueStack.pushStackValue(catchOffset);
        for (int markerOffset = preservedMarkerCount - 1; markerOffset >= 0; markerOffset--) {
            executionContext.virtualMachine.valueStack.pushStackValue(preservedMarkers[markerOffset]);
        }
        for (int i = depth - 1; i >= 0; i--) {
            executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.forOfTempValues[i]);
            executionContext.virtualMachine.forOfTempValues[i] = null;
        }
        executionContext.virtualMachine.valueStack.push(value);
        executionContext.virtualMachine.valueStack.push(done ? JSBoolean.TRUE : JSBoolean.FALSE);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc = pc + op.getSize();
    }

    static void handleForOfStart(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Pop the iterable from the stack
        JSValue iterable = executionContext.virtualMachine.valueStack.pop();

        // Auto-box primitives (strings, numbers, etc.) to access their Symbol.iterator
        JSObject iterableObj;
        if (iterable instanceof JSObject obj) {
            iterableObj = obj;
        } else {
            // Try to auto-box the primitive
            iterableObj = executionContext.virtualMachine.toObject(iterable);
            if (iterableObj == null) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError("Object is not iterable");
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        // Get Symbol.iterator method
        JSValue iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            JSValue pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.pendingException = pendingException;
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc = pc + op.getSize();
            return;
        }

        if (!JSTypeChecking.isCallable(iteratorMethod)) {
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError("Object is not iterable");
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc = pc + op.getSize();
            return;
        }

        // Call the Symbol.iterator method to get an iterator
        // Use the original iterable value for the 'this' binding, not the boxed version
        JSValue iterator;
        try {
            iterator = callCallableValue(executionContext.virtualMachine.context, iteratorMethod, iterable, JSValue.NO_ARGS);
        } catch (JSException e) {
            executionContext.virtualMachine.pendingException = e.getErrorValue();
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc = pc + op.getSize();
            return;
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc = pc + op.getSize();
            return;
        }
        if (executionContext.virtualMachine.context.hasPendingException()) {
            JSValue pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.pendingException = pendingException;
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc = pc + op.getSize();
            return;
        }

        if (!(iterator instanceof JSObject iteratorObj)) {
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError("Iterator method must return an object");
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc = pc + op.getSize();
            return;
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            JSValue pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.pendingException = pendingException;
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc = pc + op.getSize();
            return;
        }

        // Per spec (GetIterator step 5): store the next value as-is.
        if (nextMethod == null) {
            nextMethod = JSUndefined.INSTANCE;
        }

        executionContext.virtualMachine.clearForOfIteratorExhausted(iteratorObj);
        // Push iterator, next method, and catch offset onto the stack.
        executionContext.virtualMachine.valueStack.push(iterator);         // Iterator object
        executionContext.virtualMachine.valueStack.push(nextMethod);       // next() method
        executionContext.virtualMachine.valueStack.pushStackValue(JSCatchOffset.ITERATOR_CLOSE_MARKER);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc = pc + op.getSize();
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
            default -> throw new IllegalStateException("Unexpected short get arg opcode: " + op);
        };
        executionContext.push(executionContext.virtualMachine.getArgumentValue(argumentIndex));
        executionContext.pc += op.getSize();
    }

    static void handleGetArrayEl(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];

        if (objectValue instanceof JSString stringValue && indexValue instanceof JSNumber numberValue) {
            double doubleValue = numberValue.value();
            int index = (int) doubleValue;
            if (index == doubleValue && index >= 0 && index < stringValue.value().length()) {
                stack[sp - 1] = new JSString(String.valueOf(stringValue.value().charAt(index)));
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        executionContext.virtualMachine.valueStack.stackTop = sp - 1;
        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            try {
                PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                // Pass the original primitive as receiver so strict getters see
                // the unboxed value. For objects, use get(key) so subclass
                // overrides (e.g. JSArguments mapped args) are dispatched.
                JSValue result;
                if (objectValue instanceof JSObject) {
                    result = targetObject.get(key);
                } else {
                    result = targetObject.get(key, objectValue);
                }
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    executionContext.virtualMachine.context.clearPendingException();
                    stack[sp - 1] = JSUndefined.INSTANCE;
                } else {
                    if (executionContext.virtualMachine.trackPropertyAccess && !executionContext.virtualMachine.propertyAccessLock) {
                        executionContext.virtualMachine.appendPropertyAccessForArrayIndex(indexValue);
                    }
                    stack[sp - 1] = result;
                }
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.captureVMException(e);
                stack[sp - 1] = JSUndefined.INSTANCE;
            }
        } else {
            executionContext.virtualMachine.pendingException =
                    createCannotReadPropertiesTypeError(executionContext.virtualMachine.context, objectValue);
            executionContext.virtualMachine.context.clearPendingException();
            stack[sp - 1] = JSUndefined.INSTANCE;
        }
        executionContext.virtualMachine.valueStack.stackTop = sp;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetArrayEl2(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue arrayObjectValue = (JSValue) stack[sp - 1];

        if (arrayObjectValue instanceof JSString stringValue && indexValue instanceof JSNumber numberValue) {
            double doubleValue = numberValue.value();
            int index = (int) doubleValue;
            if (index == doubleValue && index >= 0 && index < stringValue.value().length()) {
                stack[sp++] = new JSString(String.valueOf(stringValue.value().charAt(index)));
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(arrayObjectValue);
        if (targetObject != null) {
            try {
                PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                // Pass the original primitive as receiver so strict getters see
                // the unboxed value. For objects, use get(key) so subclass
                // overrides (e.g. JSArguments mapped args) are dispatched.
                JSValue result;
                if (arrayObjectValue instanceof JSObject) {
                    result = targetObject.get(key);
                } else {
                    result = targetObject.get(key, arrayObjectValue);
                }
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    executionContext.virtualMachine.context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = result;
                }
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.captureVMException(e);
                stack[sp++] = JSUndefined.INSTANCE;
            }
        } else {
            executionContext.virtualMachine.pendingException =
                    createCannotReadPropertiesTypeError(executionContext.virtualMachine.context, arrayObjectValue);
            executionContext.virtualMachine.context.clearPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetArrayEl3(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[sp - 1];
        JSValue arrayObjectValue = (JSValue) stack[sp - 2];

        if (!(indexValue instanceof JSNumber || indexValue instanceof JSString || indexValue instanceof JSSymbol)) {
            if (arrayObjectValue.isNullOrUndefined()) {
                executionContext.virtualMachine.pendingException =
                        createCannotReadPropertiesTypeError(executionContext.virtualMachine.context, arrayObjectValue);
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
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
            executionContext.virtualMachine.pendingException =
                    createCannotReadPropertiesTypeError(executionContext.virtualMachine.context, arrayObjectValue);
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
        JSValue result = targetObject.get(key, arrayObjectValue);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue objectValue = executionContext.pop();

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(executionContext.bytecode.getCachedPropertyKey(atomIndex),
                    objectValue);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.push(JSUndefined.INSTANCE);
            } else {
                if (executionContext.virtualMachine.trackPropertyAccess && !executionContext.virtualMachine.propertyAccessLock) {
                    if (!executionContext.virtualMachine.propertyAccessChain.isEmpty()) {
                        executionContext.virtualMachine.propertyAccessChain.append('.');
                    }
                    executionContext.virtualMachine.propertyAccessChain.append(fieldName);
                }
                executionContext.push(result);
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("cannot read property '" + fieldName + "' of " + typeName);
            executionContext.virtualMachine.resetPropertyAccessTracking();
            executionContext.push(JSUndefined.INSTANCE);
        }
        executionContext.pc = pc + op.getSize();
    }

    // obj -> obj val (get named property, keep object on stack)
    static void handleGetField2(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue objectValue = executionContext.peek(0);

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(executionContext.bytecode.getCachedPropertyKey(atomIndex),
                    objectValue);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.push(JSUndefined.INSTANCE);
            } else {
                if (executionContext.virtualMachine.trackPropertyAccess && !executionContext.virtualMachine.propertyAccessLock) {
                    if (!executionContext.virtualMachine.propertyAccessChain.isEmpty()) {
                        executionContext.virtualMachine.propertyAccessChain.append('.');
                    }
                    executionContext.virtualMachine.propertyAccessChain.append(fieldName);
                }
                executionContext.push(result);
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("cannot read property '" + fieldName + "' of " + typeName);
            executionContext.virtualMachine.resetPropertyAccessTracking();
            executionContext.push(JSUndefined.INSTANCE);
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetLength(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue objectValue = executionContext.pop();
        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(PropertyKey.LENGTH);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.push(JSUndefined.INSTANCE);
            } else {
                executionContext.push(result);
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("cannot read property 'length' of " + typeName);
            executionContext.push(JSUndefined.INSTANCE);
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetLoc(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int localIndex = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        JSValue localValue = executionContext.locals[localIndex];
        executionContext.push(localValue != null ? localValue : JSUndefined.INSTANCE);
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc0(Opcode op, ExecutionContext executionContext) {
        JSValue localValue = executionContext.locals[0];
        executionContext.push(localValue != null ? localValue : JSUndefined.INSTANCE);
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc1(Opcode op, ExecutionContext executionContext) {
        JSValue localValue = executionContext.locals[1];
        executionContext.push(localValue != null ? localValue : JSUndefined.INSTANCE);
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc2(Opcode op, ExecutionContext executionContext) {
        JSValue localValue = executionContext.locals[2];
        executionContext.push(localValue != null ? localValue : JSUndefined.INSTANCE);
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc3(Opcode op, ExecutionContext executionContext) {
        JSValue localValue = executionContext.locals[3];
        executionContext.push(localValue != null ? localValue : JSUndefined.INSTANCE);
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue localValue = executionContext.locals[localIndex];
        executionContext.push(localValue != null ? localValue : JSUndefined.INSTANCE);
        executionContext.pc += op.getSize();
    }

    static void handleGetLocCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue localValue = executionContext.frame.getLocals()[localIndex];
        if (executionContext.virtualMachine.isUninitialized(localValue)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        executionContext.push(localValue);
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetPrivateField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        JSValue value = JSUndefined.INSTANCE;
        if (privateSymbolValue instanceof JSSymbol symbol) {
            JSObject object;
            if (objectValue instanceof JSObject objectValueAsObject) {
                object = objectValueAsObject;
            } else {
                object = executionContext.virtualMachine.toObject(objectValue);
            }
            if (object == null) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot read private member from a non-object");
                stack[sp++] = value;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            PropertyKey key = PropertyKey.fromSymbol(symbol);
            PropertyDescriptor descriptor = object instanceof JSProxy proxy
                    ? proxy.getOwnPrivatePropertyDescriptorDirect(key)
                    : object.getOwnPropertyDescriptor(key);
            if (descriptor == null) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot read private member " + symbol.getDescription()
                                        + " from an object whose class did not declare it");
                stack[sp++] = value;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            if (descriptor.isAccessorDescriptor()) {
                JSFunction getterFunction = descriptor.getGetter();
                if (getterFunction == null) {
                    executionContext.virtualMachine.pendingException =
                            executionContext.virtualMachine.context.throwTypeError(
                                    "Cannot read private member " + symbol.getDescription()
                                            + " from an object whose class did not declare it");
                    stack[sp++] = value;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                value = getterFunction.call(
                        executionContext.virtualMachine.context,
                        object,
                        JSValue.NO_ARGS);
            } else {
                value = descriptor.getValue();
            }
        }

        stack[sp++] = value;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            EvalScopedLocalBinding localBinding = findEvalScopedLocalBinding(executionContext, variableName);
            if (localBinding != null) {
                if (localBinding.value() == VirtualMachine.UNINITIALIZED_MARKER) {
                    executionContext.virtualMachine.pendingException =
                            context.throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = localBinding.value() != null ? localBinding.value() : JSUndefined.INSTANCE;
                }
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        if (objectValue.isUndefined()) {
            String name = key != null ? key.toPropertyString() : "variable";
            executionContext.virtualMachine.pendingException = context.throwReferenceError(name + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = context.throwTypeError("value has no property");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        if (targetObject == context.getGlobalObject() && variableName != null) {
            StackFrame bindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
            if (bindingFrame != null) {
                JSValue variableValue = bindingFrame.getDynamicVarBinding(variableName);
                stack[sp++] = variableValue != null ? variableValue : JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            EvalScopedLocalBinding localBinding = findEvalScopedLocalBinding(executionContext, variableName);
            if (localBinding != null) {
                if (localBinding.value() == VirtualMachine.UNINITIALIZED_MARKER) {
                    executionContext.virtualMachine.pendingException =
                            context.throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = localBinding.value() != null ? localBinding.value() : JSUndefined.INSTANCE;
                }
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            if (variableName != null && context.hasGlobalLexicalBinding(variableName)) {
                if (!context.isGlobalLexicalBindingInitialized(variableName)) {
                    executionContext.virtualMachine.pendingException =
                            context.throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = context.readGlobalLexicalBinding(variableName);
                }
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        JSValue value;
        if (!targetObject.has(key)) {
            String name = key != null ? key.toPropertyString() : "variable";
            executionContext.virtualMachine.pendingException = context.throwReferenceError(name + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        } else {
            value = targetObject.get(key);
        }
        stack[sp++] = value;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetSuper(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue objectValue = (JSValue) stack[sp - 1];
        JSObject object = executionContext.virtualMachine.toObject(objectValue);
        if (object == null) {
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
            }
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
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError("super object expected");
            executionContext.virtualMachine.context.clearPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, keyValue);
        // Pass receiver directly without boxing. Per ES spec, super property access
        // should not auto-box primitive receivers. The getter receives the original
        // primitive as 'this' (e.g., super.prop.call(3) should see this === 3).
        JSValue result = superObject.getWithReceiver(key, receiverValue);

        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = result;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetVar(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String[] atomPool = executionContext.bytecode.getAtoms();
        if (atomPool.length == 0 || atomIndex < 0 || atomIndex >= atomPool.length) {
            int varRefIndex = executionContext.bytecode.readU16(pc + 1);
            stack[sp++] = readVarRefValue(executionContext, varRefIndex);
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }
        String variableName = atomPool[atomIndex];
        PropertyKey propertyKey = executionContext.bytecode.getCachedPropertyKey(atomIndex);
        StackFrame dynamicBindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
        if (dynamicBindingFrame != null) {
            JSValue variableValue = dynamicBindingFrame.getDynamicVarBinding(variableName);
            stack[sp++] = variableValue != null ? variableValue : JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }
        // Check closure VarRefs in current frame and caller frames.
        // VarRef-based closure variables are checked so that eval() inside class
        // member functions can resolve the class inner name binding.
        StackFrame checkFrame = executionContext.frame;
        IdentityHashMap<StackFrame, Boolean> visitedFrames = new IdentityHashMap<>();
        while (checkFrame != null && visitedFrames.put(checkFrame, Boolean.TRUE) == null) {
            JSFunction checkFunction = checkFrame.getFunction();
            if (checkFunction instanceof JSBytecodeFunction checkBytecodeFunction) {
                VarRef[] closureVarRefs = checkBytecodeFunction.getVarRefs();
                String[] closureVarNames = checkBytecodeFunction.getCapturedVarNames();
                if (closureVarRefs != null && closureVarNames != null) {
                    for (int i = 0; i < closureVarNames.length && i < closureVarRefs.length; i++) {
                        if (variableName.equals(closureVarNames[i]) && closureVarRefs[i] != null) {
                            JSValue closureValue = closureVarRefs[i].get();
                            if (closureValue == VirtualMachine.UNINITIALIZED_MARKER) {
                                executionContext.virtualMachine.pendingException =
                                        executionContext.virtualMachine.context.throwReferenceError(
                                                "Cannot access '" + variableName + "' before initialization");
                                stack[sp++] = JSUndefined.INSTANCE;
                            } else {
                                stack[sp++] = closureValue;
                            }
                            executionContext.sp = sp;
                            executionContext.pc = pc + op.getSize();
                            return;
                        }
                    }
                }
            }
            checkFrame = checkFrame.getCaller();
        }
        JSContext context = executionContext.virtualMachine.context;
        JSObject globalObject = context.getGlobalObject();
        boolean hasProperty = false;
        JSValue variableValue = JSUndefined.INSTANCE;
        if (context.hasEvalOverlayBinding(variableName)) {
            variableValue = globalObject.get(propertyKey);
            hasProperty = !(variableValue instanceof JSUndefined) || globalObject.has(propertyKey);
            if (hasProperty) {
                if (executionContext.virtualMachine.trackPropertyAccess && !executionContext.virtualMachine.propertyAccessLock) {
                    executionContext.virtualMachine.resetPropertyAccessTracking();
                    executionContext.virtualMachine.propertyAccessChain.append(variableName);
                }
                stack[sp++] = variableValue;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }
        EvalScopedLocalBinding localBinding = findEvalScopedLocalBinding(executionContext, variableName);
        if (localBinding != null) {
            if (localBinding.value() == VirtualMachine.UNINITIALIZED_MARKER) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwReferenceError(
                                "Cannot access '" + variableName + "' before initialization");
                stack[sp++] = JSUndefined.INSTANCE;
            } else {
                stack[sp++] = localBinding.value() != null ? localBinding.value() : JSUndefined.INSTANCE;
            }
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }
        if (context.hasGlobalLexicalBinding(variableName)) {
            if (!context.isGlobalLexicalBindingInitialized(variableName)) {
                executionContext.virtualMachine.pendingException =
                        context.throwReferenceError("Cannot access '" + variableName + "' before initialization");
                stack[sp++] = JSUndefined.INSTANCE;
            } else {
                stack[sp++] = context.readGlobalLexicalBinding(variableName);
            }
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }
        variableValue = globalObject.get(propertyKey);
        if (!(variableValue instanceof JSUndefined)) {
            hasProperty = true;
        } else {
            hasProperty = globalObject.has(propertyKey);
        }
        if (!hasProperty) {
            executionContext.virtualMachine.pendingException = context.throwReferenceError(variableName + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            if (executionContext.virtualMachine.trackPropertyAccess && !executionContext.virtualMachine.propertyAccessLock) {
                executionContext.virtualMachine.resetPropertyAccessTracking();
                executionContext.virtualMachine.propertyAccessChain.append(variableName);
            }
            stack[sp++] = variableValue;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
            default -> throw new IllegalStateException("Unexpected short get var ref opcode: " + op);
        };
        executionContext.push(readVarRefValue(executionContext, varRefIndex));
        executionContext.pc += op.getSize();
    }

    static void handleGetVarUndef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String[] atomPool = executionContext.bytecode.getAtoms();
        if (atomPool.length == 0 || atomIndex < 0 || atomIndex >= atomPool.length) {
            int varRefIndex = executionContext.bytecode.readU16(pc + 1);
            stack[sp++] = readVarRefValue(executionContext, varRefIndex);
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }
        String variableName = atomPool[atomIndex];
        PropertyKey propertyKey = executionContext.bytecode.getCachedPropertyKey(atomIndex);
        StackFrame dynamicBindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
        if (dynamicBindingFrame != null) {
            JSValue variableValue = dynamicBindingFrame.getDynamicVarBinding(variableName);
            stack[sp++] = variableValue != null ? variableValue : JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        // Check closure VarRefs in current frame and caller frames.
        StackFrame checkFrame = executionContext.frame;
        IdentityHashMap<StackFrame, Boolean> visitedFrames = new IdentityHashMap<>();
        while (checkFrame != null && visitedFrames.put(checkFrame, Boolean.TRUE) == null) {
            JSFunction checkFunction = checkFrame.getFunction();
            if (checkFunction instanceof JSBytecodeFunction checkBytecodeFunction) {
                VarRef[] closureVarRefs = checkBytecodeFunction.getVarRefs();
                String[] closureVarNames = checkBytecodeFunction.getCapturedVarNames();
                if (closureVarRefs != null && closureVarNames != null) {
                    for (int i = 0; i < closureVarNames.length && i < closureVarRefs.length; i++) {
                        if (variableName.equals(closureVarNames[i]) && closureVarRefs[i] != null) {
                            JSValue closureValue = closureVarRefs[i].get();
                            if (closureValue == VirtualMachine.UNINITIALIZED_MARKER) {
                                executionContext.virtualMachine.pendingException =
                                        executionContext.virtualMachine.context.throwReferenceError(
                                                "Cannot access '" + variableName + "' before initialization");
                                stack[sp++] = JSUndefined.INSTANCE;
                            } else {
                                stack[sp++] = closureValue;
                            }
                            executionContext.sp = sp;
                            executionContext.pc = pc + op.getSize();
                            return;
                        }
                    }
                }
            }
            checkFrame = checkFrame.getCaller();
        }
        JSContext context = executionContext.virtualMachine.context;
        JSObject globalObject = context.getGlobalObject();
        if (context.hasEvalOverlayBinding(variableName)) {
            JSValue value = globalObject.get(propertyKey);
            boolean hasProperty = !(value instanceof JSUndefined) || globalObject.has(propertyKey);
            if (hasProperty) {
                stack[sp++] = value;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }
        EvalScopedLocalBinding localBinding = findEvalScopedLocalBinding(executionContext, variableName);
        if (localBinding != null) {
            if (localBinding.value() == VirtualMachine.UNINITIALIZED_MARKER) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwReferenceError(
                                "Cannot access '" + variableName + "' before initialization");
                stack[sp++] = JSUndefined.INSTANCE;
            } else {
                stack[sp++] = localBinding.value() != null ? localBinding.value() : JSUndefined.INSTANCE;
            }
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }
        if (context.hasGlobalLexicalBinding(variableName)) {
            if (!context.isGlobalLexicalBindingInitialized(variableName)) {
                executionContext.virtualMachine.pendingException =
                        context.throwReferenceError("Cannot access '" + variableName + "' before initialization");
                stack[sp++] = JSUndefined.INSTANCE;
            } else {
                stack[sp++] = context.readGlobalLexicalBinding(variableName);
            }
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        stack[sp++] = globalObject.get(propertyKey);
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGosub(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int offset = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        // Push return address (instruction after this GOSUB) onto the stack
        int returnAddress = pc + op.getSize();
        executionContext.pushStackValue(new JSInternalValue(returnAddress));
        // Jump to the finally block
        executionContext.pc = pc + op.getSize() + offset;
    }

    static void handleGoto(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int offset = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        executionContext.pc = pc + op.getSize() + offset;
    }

    static void handleGoto16(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int offset = (short) (((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF));
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
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
                executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(leftNum.value() > rightNum.value()));
            } else {
                try {
                    JSTypeConversions.RelationalComparisonResult comparisonResult =
                            JSTypeConversions.lessThanResult(executionContext.virtualMachine.context, right, left, false);
                    executionContext.virtualMachine.capturePendingException();
                    if (executionContext.virtualMachine.pendingException != null) {
                        executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(
                                comparisonResult == JSTypeConversions.RelationalComparisonResult.TRUE));
                    }
                } catch (JSVirtualMachineException e) {
                    executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
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
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
                executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(leftNum.value() >= rightNum.value()));
            } else {
                try {
                    JSTypeConversions.RelationalComparisonResult comparisonResult =
                            JSTypeConversions.lessThanResult(executionContext.virtualMachine.context, left, right);
                    executionContext.virtualMachine.capturePendingException();
                    if (executionContext.virtualMachine.pendingException != null) {
                        executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(
                                comparisonResult == JSTypeConversions.RelationalComparisonResult.FALSE));
                    }
                } catch (JSVirtualMachineException e) {
                    executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleIfFalse(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        JSValue conditionValue = executionContext.pop();
        int offset = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
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
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        JSValue conditionValue = executionContext.pop();
        int offset = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
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
                throw new JSException(clearAndGetPendingException(context));
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
                errorValue = context.throwError(exception.getMessage() != null
                        ? exception.getMessage()
                        : "Module load error");
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
            JSValue errorValue = context.throwError(
                    "Error",
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
                    moduleNamespace = context.loadDynamicImportModuleDeferred(
                            specifierString,
                            referrerFilename,
                            importAttributes,
                            promise,
                            resolveState);
                } else {
                    moduleNamespace = context.loadDynamicImportModule(
                            specifierString,
                            referrerFilename,
                            importAttributes,
                            promise,
                            resolveState);
                }
                if (moduleNamespace != null && !resolveState.alreadyResolved) {
                    resolveState.alreadyResolved = true;
                    promise.resolve(context, moduleNamespace);
                }
            } catch (Exception e) {
                if (context.hasPendingException()) {
                    JSValue error = context.getPendingException();
                    context.clearPendingException();
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
                    JSValue errorValue = vme.getJsValue() != null ? vme.getJsValue()
                            : vme.getJsError() != null ? vme.getJsError()
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
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("invalid 'in' operand");
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, left);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else {
            boolean result = jsObj.has(key);
            executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleInc(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.incrementValue(operand, 1));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleIncLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue localValue = executionContext.locals[localIndex];
        if (localValue == null) {
            localValue = JSUndefined.INSTANCE;
        }
        executionContext.locals[localIndex] = executionContext.virtualMachine.incrementValue(localValue, 1);
        executionContext.pc = pc + op.getSize();
    }

    static void handleInitCtor(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue frameNewTarget = executionContext.virtualMachine.currentFrame.getNewTarget();
        if (frameNewTarget.isNullOrUndefined()) {
            // Keep direct VM opcode tests stable when executionContext.virtualMachine.execute() is used without constructor plumbing.
            if (executionContext.virtualMachine.currentFrame.getCaller() == null) {
                JSValue fallbackThis = executionContext.virtualMachine.currentFrame.getThisArg();
                if (fallbackThis instanceof JSObject) {
                    executionContext.virtualMachine.valueStack.push(fallbackThis);
                    executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                    executionContext.pc += op.getSize();
                    return;
                }
            }
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("class constructors must be invoked with 'new'"));
        }

        // Per ES spec BindThisValue: if thisBindingStatus is "initialized", throw ReferenceError.
        // Arrow functions share the this-binding with the enclosing derived constructor.
        // Check if this was already initialized by a previous super() call.
        JSFunction currentFunction = executionContext.virtualMachine.currentFrame.getFunction();
        JSValue existingThisValue = executionContext.virtualMachine.currentFrame.getThisArg();
        if (existingThisValue instanceof JSObject) {
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwReferenceError(
                            "Super constructor may only be called once");
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }
        if (currentFunction instanceof JSBytecodeFunction arrowBf && arrowBf.isArrow()) {
            JSValue capturedThis = arrowBf.getCapturedThisArg();
            if (capturedThis instanceof JSObject) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwReferenceError(
                                "Super constructor may only be called once");
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
                return;
            }
            // Also check the shared derivedThisRef VarRef which tracks the real initialization
            // state across calls. When an arrow escapes the constructor and calls super() again,
            // capturedThisArg may still appear uninitialized but the VarRef has been updated.
            VarRef arrowDerivedThisRef = executionContext.virtualMachine.currentFrame.getDerivedThisRef();
            if (arrowDerivedThisRef != null) {
                JSValue derivedThisValue = arrowDerivedThisRef.get();
                if (derivedThisValue instanceof JSObject) {
                    executionContext.virtualMachine.pendingException =
                            executionContext.virtualMachine.context.throwReferenceError(
                                    "Super constructor may only be called once");
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                    executionContext.pc += op.getSize();
                    return;
                }
            }
        }

        // Explicit super(...): APPLY constructor mode left the initialized this value on stack.
        if (executionContext.virtualMachine.valueStack.getStackTop() > executionContext.virtualMachine.currentFrame.getStackBase()) {
            JSValue thisValue = executionContext.virtualMachine.valueStack.pop();
            if (!(thisValue instanceof JSObject jsObject)) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError("super() returned non-object");
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
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
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError("parent class must be constructor");
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
                return;
            }

            JSValue superResult;
            if (superConstructorObject instanceof JSProxy superProxy) {
                superResult = superProxy.construct(
                        executionContext.virtualMachine.context,
                        executionContext.virtualMachine.currentFrame.getArguments(),
                        frameNewTarget);
            } else if (superConstructorObject instanceof JSFunction superConstructor) {
                superResult = executionContext.virtualMachine.constructFunction(
                        superConstructor,
                        executionContext.virtualMachine.currentFrame.getArguments(),
                        frameNewTarget);
            } else {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError("parent class must be constructor");
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
                return;
            }
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (!(superResult instanceof JSObject superObject)) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError("super() returned non-object");
                executionContext.virtualMachine.context.clearPendingException();
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
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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
            executionContext.virtualMachine.yieldResult =
                    new YieldResult(YieldResult.Type.INITIAL_YIELD, JSUndefined.INSTANCE);
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.yieldResult != null) {
            // Save suspended execution state so the generator resumes after INITIAL_YIELD
            // instead of re-executing from the start (which would re-run parameter
            // destructuring, causing side effects like double iterator close).
            executionContext.virtualMachine.saveActiveGeneratorSuspendedExecutionState(
                    executionContext.frame,
                    executionContext.pc,
                    executionContext.virtualMachine.valueStack.stack,
                    executionContext.sp,
                    executionContext.frameStackBase);
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, JSUndefined.INSTANCE);
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
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError("Right-hand side of instanceof is not an object");
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }

        JSValue hasInstanceMethod = constructor.get(PropertyKey.SYMBOL_HAS_INSTANCE);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }
        if (!(hasInstanceMethod instanceof JSUndefined) && !(hasInstanceMethod instanceof JSNull)) {
            if (!(hasInstanceMethod instanceof JSFunction hasInstanceFunction)) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError("@@hasInstance is not callable");
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
                return;
            }
            JSValue result = hasInstanceFunction.call(executionContext.virtualMachine.context, right, new JSValue[]{left});
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
                return;
            }
            executionContext.virtualMachine.valueStack.push(JSTypeConversions.toBoolean(result) == JSBoolean.TRUE ? JSBoolean.TRUE : JSBoolean.FALSE);
        } else {
            boolean callable = right instanceof JSFunction;
            if (right instanceof JSProxy proxy && JSTypeChecking.isFunction(proxy.getTarget())) {
                callable = true;
            }
            if (!callable) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError("Right-hand side of instanceof is not callable");
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                executionContext.pc += op.getSize();
                return;
            }
            try {
                executionContext.virtualMachine.valueStack.push(
                        executionContext.virtualMachine.ordinaryHasInstance(right, left) ? JSBoolean.TRUE : JSBoolean.FALSE);
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleInvalid(Opcode op, ExecutionContext executionContext) {
        throw new JSVirtualMachineException("Invalid opcode at PC " + executionContext.pc);
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
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator call target must be an object"));
        }

        String methodName = (flags & 1) != 0 ? "throw" : "return";
        JSValue methodValue = (flags & 1) != 0
                ? iteratorObject.get(PropertyKey.THROW)
                : iteratorObject.get(PropertyKey.RETURN);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            throw new JSVirtualMachineException(
                    executionContext.virtualMachine.context.getPendingException().toString(),
                    executionContext.virtualMachine.context.getPendingException());
        }
        boolean noMethod = methodValue.isNullOrUndefined();
        if (!noMethod) {
            if (!JSTypeChecking.isCallable(methodValue)) {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator " + methodName + " is not a function"));
            }
            JSValue callResult = (flags & 2) != 0
                    ? callCallableValue(executionContext.virtualMachine.context, methodValue, iteratorObject, JSValue.NO_ARGS)
                    : callCallableValue(executionContext.virtualMachine.context, methodValue, iteratorObject, new JSValue[]{argumentValue});
            stack[sp - 1] = callResult;
        }
        stack[sp++] = JSBoolean.valueOf(noMethod);
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleIteratorCheckObject(Opcode op, ExecutionContext executionContext) {
        JSValue iteratorResult = executionContext.peek(0);
        if (!(iteratorResult instanceof JSObject)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator must return an object"));
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
        int tailCount = sp - (markerIndex + 1);
        if (tailCount > 0) {
            System.arraycopy(stack, markerIndex + 1, stack, removeStartIndex, tailCount);
        }
        sp -= 3;
        for (int index = sp; index < sp + 3 && index < stack.length; index++) {
            stack[index] = null;
        }

        if (iteratorValue instanceof JSObject iteratorObject && !iteratorValue.isUndefined()) {
            executionContext.virtualMachine.clearForOfIteratorExhausted(iteratorObject);
            JSValue returnMethodValue = iteratorObject.get(PropertyKey.RETURN);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                if (originalPendingException == null) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                }
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.sp = sp;
                executionContext.pc += op.getSize();
                return;
            }
            if (JSTypeChecking.isCallable(returnMethodValue)) {
                JSValue closeResult = callCallableValue(executionContext.virtualMachine.context, returnMethodValue, iteratorObject, JSValue.NO_ARGS);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    if (originalPendingException == null) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    }
                    executionContext.virtualMachine.context.clearPendingException();
                } else if (!(closeResult instanceof JSObject)) {
                    if (originalPendingException == null) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("iterator result is not an object");
                    }
                }
            } else if (returnMethodValue.isNullOrUndefined()) {
                // No return method.
            } else if (returnMethodValue instanceof JSObject returnObject && returnObject.isHTMLDDA()) {
                // IsHTMLDDA callable edge case; preserve previous no-op behavior.
            } else {
                if (originalPendingException == null) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("iterator return is not a function");
                }
            }
        }
        executionContext.sp = sp;
        executionContext.pc += op.getSize();
    }

    static void handleIteratorGetValueDone(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue iteratorResult = (JSValue) stack[sp - 1];
        if (!(iteratorResult instanceof JSObject iteratorResultObject)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator must return an object"));
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
        executionContext.pc += op.getSize();
    }

    static void handleIteratorNext(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue catchOffsetValue = (JSValue) stack[sp - 2];
        JSValue nextMethodValue = (JSValue) stack[sp - 3];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!JSTypeChecking.isCallable(nextMethodValue)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator next is not a function"));
        }
        JSValue nextResult = callCallableValue(executionContext.virtualMachine.context, nextMethodValue, iteratorValue, new JSValue[]{argumentValue});
        stack[sp - 1] = nextResult;
        stack[sp - 2] = catchOffsetValue;
        executionContext.pc += op.getSize();
    }

    static void handleLogicalNot(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.toBoolean(operand) == JSBoolean.FALSE;
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
                executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(leftNum.value() < rightNum.value()));
            } else {
                try {
                    JSTypeConversions.RelationalComparisonResult comparisonResult =
                            JSTypeConversions.lessThanResult(executionContext.virtualMachine.context, left, right);
                    executionContext.virtualMachine.capturePendingException();
                    if (executionContext.virtualMachine.pendingException != null) {
                        executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(
                                comparisonResult == JSTypeConversions.RelationalComparisonResult.TRUE));
                    }
                } catch (JSVirtualMachineException e) {
                    executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
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
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
                executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(leftNum.value() <= rightNum.value()));
            } else {
                try {
                    JSTypeConversions.RelationalComparisonResult comparisonResult =
                            JSTypeConversions.lessThanResult(executionContext.virtualMachine.context, right, left, false);
                    executionContext.virtualMachine.capturePendingException();
                    if (executionContext.virtualMachine.pendingException != null) {
                        executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(
                                comparisonResult == JSTypeConversions.RelationalComparisonResult.FALSE));
                    }
                } catch (JSVirtualMachineException e) {
                    executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
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
        if (findDynamicVarBindingFrame(executionContext, atomName) != null
                || context.hasGlobalLexicalBinding(atomName)
                || context.getGlobalObject().has(key)) {
            baseObject = context.getGlobalObject();
        } else {
            baseObject = JSUndefined.INSTANCE;
        }
        executionContext.push(baseObject);
        executionContext.push(new JSString(atomName));
        executionContext.pc = pc + op.getSize();
    }

    static void handleMod(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            if (rightBigInt.value().equals(VirtualMachine.BIGINT_ZERO)) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwRangeError("Division by zero");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().remainder(rightBigInt.value())));
            }
        } else {
            JSNumber leftNumber = (JSNumber) pair.left();
            JSNumber rightNumber = (JSNumber) pair.right();
            executionContext.virtualMachine.valueStack.push(JSNumber.of(leftNumber.value() % rightNumber.value()));
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleMul(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() * rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                JSBigInt leftBigInt = (JSBigInt) pair.left();
                JSBigInt rightBigInt = (JSBigInt) pair.right();
                executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().multiply(rightBigInt.value())));
            } else {
                JSNumber leftNumber = (JSNumber) pair.left();
                JSNumber rightNumber = (JSNumber) pair.right();
                executionContext.virtualMachine.valueStack.push(JSNumber.of(leftNumber.value() * rightNumber.value()));
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
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
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleNeq(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = !JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            return;
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwError("nip_catch"));
        }
        stack[sp++] = returnValue;
        executionContext.sp = sp;
        executionContext.pc += op.getSize();
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
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleNull(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNull.INSTANCE);
        executionContext.pc += 1;
    }

    static void handleObjectNew(Opcode op, ExecutionContext executionContext) {
        executionContext.push(executionContext.virtualMachine.context.createJSObject());
        executionContext.pc += op.getSize();
    }

    static void handleOr(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSNumber.of(JSTypeConversions.toInt32(leftNum.value()) | JSTypeConversions.toInt32(rightNum.value())));
        } else {
            VirtualMachine.NumericPair pair;
            try {
                pair = executionContext.virtualMachine.numericPair(left, right);
            } catch (JSVirtualMachineException e) {
                captureVmExceptionAsPending(executionContext, e);
                pair = null;
            }
            if (pair != null && executionContext.virtualMachine.pendingException != null) {
                pair = null;
            }
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                JSBigInt leftBigInt = (JSBigInt) pair.left();
                JSBigInt rightBigInt = (JSBigInt) pair.right();
                executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().or(rightBigInt.value())));
            } else {
                int result = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left()) | JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
                executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
            }
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handlePostDec(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue oldValue = executionContext.virtualMachine.toNumericValue(operand);
        executionContext.virtualMachine.valueStack.push(oldValue);
        if (oldValue instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().subtract(VirtualMachine.BIGINT_ONE)));
        } else {
            executionContext.virtualMachine.valueStack.push(JSNumber.of(((JSNumber) oldValue).value() - 1));
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handlePostInc(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue oldValue = executionContext.virtualMachine.toNumericValue(operand);
        executionContext.virtualMachine.valueStack.push(oldValue);
        if (oldValue instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().add(VirtualMachine.BIGINT_ONE)));
        } else {
            executionContext.virtualMachine.valueStack.push(JSNumber.of(((JSNumber) oldValue).value() + 1));
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handlePrivateIn(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue privateField = executionContext.virtualMachine.valueStack.pop();
        JSValue object = executionContext.virtualMachine.valueStack.pop();
        if (!(object instanceof JSObject jsObj)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("invalid 'in' operand");
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
            return;
        }
        boolean result = false;
        if (privateField instanceof JSSymbol symbol) {
            result = jsObj.has(PropertyKey.fromSymbol(symbol));
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int atomIndex = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        executionContext.push(new JSString(executionContext.bytecode.getAtoms()[atomIndex]));
        executionContext.pc = pc + op.getSize();
    }

    static void handlePushBigintI32(Opcode op, ExecutionContext executionContext) {
        executionContext.push(new JSBigInt(executionContext.bytecode.readI32(executionContext.pc + 1)));
        executionContext.pc += op.getSize();
    }

    static void handlePushConst(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int constIndex = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        JSValue constantValue = executionContext.bytecode.getConstants()[constIndex];
        if (constantValue instanceof JSSymbol symbol) {
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = internalGetActivePrivateSymbolRemap(
                    executionContext,
                    executionContext.sp
            );
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

    static void handlePushConst8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int constIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue constantValue = executionContext.bytecode.getConstants()[constIndex];
        if (constantValue instanceof JSSymbol symbol) {
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = internalGetActivePrivateSymbolRemap(
                    executionContext,
                    executionContext.sp
            );
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
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        executionContext.push(
                JSNumber.of((short) (((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF))));
        executionContext.pc = pc + 3;
    }

    static void handlePushI32(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int intValue = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
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
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwReferenceError(
                                "Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
                executionContext.virtualMachine.context.clearPendingException();
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
            default -> throw new IllegalStateException("Unexpected short put arg opcode: " + op);
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
            executionContext.virtualMachine.context.throwTypeError("cannot set property '" + key + "' of "
                    + (objectValue instanceof JSNull ? "null" : "undefined"));
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
        } else {
            // For primitives, box to find setters in the prototype chain,
            // passing the original primitive as receiver. If no setter is
            // found, strict mode throws TypeError (QuickJS JS_PROP_THROW_STRICT).
            JSObject boxedObject = executionContext.virtualMachine.toObject(objectValue);
            if (boxedObject != null) {
                try {
                    PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                    boolean setSucceeded = boxedObject.setWithResult(key, assignedValue, objectValue);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                        executionContext.virtualMachine.context.clearPendingException();
                    } else if (!setSucceeded && executionContext.virtualMachine.context.isStrictMode()) {
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot create property '" + key + "' on " + JSTypeChecking.typeof(objectValue) + " '" + objectValue + "'");
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                        executionContext.virtualMachine.context.clearPendingException();
                    }
                } catch (JSVirtualMachineException e) {
                    executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                }
            }
        }

        stack[sp++] = assignedValue;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
        } else {
            // For primitives, box to find setters in the prototype chain,
            // passing the original primitive as receiver. If no setter is
            // found, strict mode throws TypeError (QuickJS JS_PROP_THROW_STRICT).
            JSObject boxedObject = executionContext.virtualMachine.toObject(objectValue);
            if (boxedObject != null) {
                try {
                    boolean setSucceeded = boxedObject.setWithResult(propertyKey, fieldValue, objectValue);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                        executionContext.virtualMachine.context.clearPendingException();
                    } else if (!setSucceeded && executionContext.virtualMachine.context.isStrictMode()) {
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot create property '" + fieldName + "' on " + JSTypeChecking.typeof(objectValue) + " '" + objectValue + "'");
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                        executionContext.virtualMachine.context.clearPendingException();
                    }
                } catch (JSVirtualMachineException e) {
                    executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                }
            }
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutLoc(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int localIndex = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
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
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        localValues[localIndex] = executionContext.pop();
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutLocCheckInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (!executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwReferenceError("'this' can be initialized only once"));
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

        if (privateSymbolValue instanceof JSSymbol symbol) {
            JSObject object;
            if (objectValue instanceof JSObject objectValueAsObject) {
                object = objectValueAsObject;
            } else {
                object = executionContext.virtualMachine.toObject(objectValue);
            }
            if (object == null) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot write private member to a non-object");
                stack[sp++] = value;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            PropertyKey key = PropertyKey.fromSymbol(symbol);
            PropertyDescriptor descriptor = object instanceof JSProxy proxy
                    ? proxy.getOwnPrivatePropertyDescriptorDirect(key)
                    : object.getOwnPropertyDescriptor(key);
            if (descriptor == null) {
                executionContext.virtualMachine.pendingException =
                        executionContext.virtualMachine.context.throwTypeError(
                                "Cannot write private member " + symbol.getDescription()
                                        + " to an object whose class did not declare it");
                stack[sp++] = value;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            if (descriptor.isAccessorDescriptor()) {
                JSFunction setterFunction = descriptor.getSetter();
                if (setterFunction == null) {
                    executionContext.virtualMachine.pendingException =
                            executionContext.virtualMachine.context.throwTypeError(
                                    "Cannot write private member " + symbol.getDescription()
                                            + " to an object whose class did not declare it");
                    stack[sp++] = value;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                setterFunction.call(
                        executionContext.virtualMachine.context,
                        object,
                        new JSValue[]{value});
            } else {
                if (!descriptor.isWritable()) {
                    executionContext.virtualMachine.pendingException =
                            executionContext.virtualMachine.context.throwTypeError(
                                    "Cannot write private member " + symbol.getDescription()
                                            + " to an object whose class did not declare it");
                    stack[sp++] = value;
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                if (object instanceof JSProxy proxy) {
                    proxy.setPrivatePropertyDirect(key, value);
                } else {
                    object.setPrivatePropertyDirect(key, value);
                }
            }
        }

        stack[sp++] = value;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
                if (context.hasEvalOverlayFrames()
                        && context.hasEvalOverlayBinding(variableName)) {
                    JSObject globalObject = context.getGlobalObject();
                    globalObject.set(key, setValue);
                    if (context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = context.getPendingException();
                        context.clearPendingException();
                    }
                }
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        if (objectValue.isUndefined()) {
            if (executionContext.virtualMachine.context.isStrictMode()) {
                String name = key != null ? key.toPropertyString() : "variable";
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwReferenceError(name + " is not defined");
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            objectValue = executionContext.virtualMachine.context.getGlobalObject();
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("value has no property");
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        if (targetObject == context.getGlobalObject() && variableName != null) {
            StackFrame bindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
            if (bindingFrame != null) {
                bindingFrame.setDynamicVarBinding(variableName, setValue);
                if (context.hasEvalOverlayFrames()
                        && context.hasEvalOverlayBinding(variableName)) {
                    targetObject.set(key, setValue);
                    if (context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = context.getPendingException();
                        context.clearPendingException();
                    }
                }
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            if (variableName != null && context.hasGlobalLexicalBinding(variableName)) {
                if (!context.isGlobalLexicalBindingInitialized(variableName)) {
                    executionContext.virtualMachine.pendingException =
                            context.throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                if (context.hasGlobalConstDeclaration(variableName)) {
                    executionContext.virtualMachine.pendingException = context.throwTypeError("Assignment to constant variable.");
                    executionContext.sp = sp;
                    executionContext.pc = pc + op.getSize();
                    return;
                }
                context.writeGlobalLexicalBinding(variableName, setValue);
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        if (!targetObject.has(key) && executionContext.virtualMachine.context.isStrictMode()) {
            String name = key != null ? key.toPropertyString() : "variable";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwReferenceError(name + " is not defined");
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
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
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError("super object expected");
            executionContext.virtualMachine.context.clearPendingException();
            stack[sp++] = assignedValue;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
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
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
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
            if (context.hasEvalOverlayFrames()
                    && context.hasEvalOverlayBinding(variableName)) {
                JSObject globalObject = context.getGlobalObject();
                PropertyKey variableKey = PropertyKey.fromString(variableName);
                globalObject.set(variableKey, value);
                if (context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = context.getPendingException();
                    context.clearPendingException();
                }
            }
            executionContext.pc = pc + op.getSize();
            return;
        }
        PropertyKey variableKey = PropertyKey.fromString(variableName);
        JSObject globalObject = context.getGlobalObject();
        if (context.hasEvalOverlayBinding(variableName) && globalObject.has(variableKey)) {
            // Set flag so import overlay setters can detect bare variable assignment
            // and throw TypeError (ES2024: import bindings are immutable).
            context.setInBareVariableAssignment(true);
            try {
                globalObject.set(variableKey, value);
            } finally {
                context.setInBareVariableAssignment(false);
            }
            if (context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = context.getPendingException();
                context.clearPendingException();
            }
            executionContext.pc = pc + op.getSize();
            return;
        }
        if (context.hasGlobalLexicalBinding(variableName)) {
            if (context.hasGlobalConstDeclaration(variableName)
                    && context.isGlobalLexicalBindingInitialized(variableName)) {
                executionContext.virtualMachine.pendingException =
                        context.throwTypeError("Assignment to constant variable.");
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
            if (context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = context.getPendingException();
                context.clearPendingException();
            }
            executionContext.pc = pc + op.getSize();
            return;
        }
        if (context.isStrictMode() && !globalObject.has(variableKey)) {
            executionContext.virtualMachine.pendingException =
                    context.throwReferenceError(variableName + " is not defined");
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
        if (context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = context.getPendingException();
            context.clearPendingException();
        }
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
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwReferenceError("variable is already initialized"));
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
            default -> throw new IllegalStateException("Unexpected short put var ref opcode: " + op);
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
            JSRegExp newRegExp = new JSRegExp(executionContext.virtualMachine.context, templateRegExp.getPattern(), templateRegExp.getFlags());
            executionContext.virtualMachine.initializeConstantValueIfNeeded(newRegExp);
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
        if (stackValue instanceof JSInternalValue internalValue && internalValue.value() instanceof Integer returnAddress) {
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
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            int leftInt = JSTypeConversions.toInt32(leftNumber.value());
            int rightInt = JSTypeConversions.toInt32(rightNumber.value());
            stack[sp - 2] = JSNumber.of(leftInt >> (rightInt & 0x1F));
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.shiftBigInt((JSBigInt) pair.left(), (JSBigInt) pair.right(), false));
            } else {
                int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left());
                int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
                executionContext.virtualMachine.valueStack.push(JSNumber.of(leftInt >> (rightInt & 0x1F)));
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
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
            default -> throw new IllegalStateException("Unexpected short set arg opcode: " + op);
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
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int localIndex = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
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
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        localValues[localIndex] = executionContext.peek(0);
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetLocUninitialized(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
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
        executionContext.virtualMachine.setObjectName((JSValue) stack[sp - 1], executionContext.virtualMachine.getComputedNameString(nameValue));
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
        executionContext.sp = sp;
        executionContext.pc += op.getSize();
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
            default -> throw new IllegalStateException("Unexpected short set var ref opcode: " + op);
        };
        executionContext.frame.setVarRef(varRefIndex, executionContext.peek(0));
        executionContext.pc += op.getSize();
    }

    static void handleShl(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            int leftInt = JSTypeConversions.toInt32(leftNumber.value());
            int rightInt = JSTypeConversions.toInt32(rightNumber.value());
            stack[sp - 2] = JSNumber.of(leftInt << (rightInt & 0x1F));
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.shiftBigInt((JSBigInt) pair.left(), (JSBigInt) pair.right(), true));
            } else {
                int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left());
                int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
                executionContext.virtualMachine.valueStack.push(JSNumber.of(leftInt << (rightInt & 0x1F)));
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
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
            leftPrimitive = JSTypeConversions.toPrimitive(executionContext.virtualMachine.context, left, JSTypeConversions.PreferredType.NUMBER);
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
        } catch (JSException e) {
            if (e.getErrorValue() != null) {
                executionContext.virtualMachine.pendingException = e.getErrorValue();
            } else {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError(
                        e.getMessage() != null ? e.getMessage() : "toPrimitive");
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
                rightPrimitive = JSTypeConversions.toPrimitive(executionContext.virtualMachine.context, right, JSTypeConversions.PreferredType.NUMBER);
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            } catch (JSException e) {
                if (e.getErrorValue() != null) {
                    executionContext.virtualMachine.pendingException = e.getErrorValue();
                } else {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError(
                            "Error",
                            e.getMessage() != null ? e.getMessage() : "toPrimitive");
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
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError("BigInts do not support >>>");
            executionContext.virtualMachine.context.clearPendingException();
        }
        if (leftNumeric instanceof JSNumber leftNum
                && rightNumeric instanceof JSNumber rightNum
                && executionContext.virtualMachine.pendingException == null) {
            int leftInt = JSTypeConversions.toInt32(leftNum.value());
            int rightInt = JSTypeConversions.toInt32(rightNum.value());
            executionContext.virtualMachine.valueStack.push(
                    JSNumber.of((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
        } else if (leftNumeric != null
                && rightNumeric != null
                && executionContext.virtualMachine.pendingException == null) {
            int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, leftNumeric);
            int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, rightNumeric);
            executionContext.virtualMachine.valueStack.push(
                    JSNumber.of((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
        } else {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleSpecialObject(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int objectType = executionContext.bytecode.readU8(pc + 1);
        JSValue specialObject = executionContext.virtualMachine.createSpecialObject(objectType, executionContext.frame);
        executionContext.push(specialObject);
        executionContext.pc = pc + op.getSize();
    }

    static void handleStrictEq(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleStrictNeq(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = !JSTypeConversions.strictEquals(left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleSub(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() - rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                JSBigInt leftBigInt = (JSBigInt) pair.left();
                JSBigInt rightBigInt = (JSBigInt) pair.right();
                executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().subtract(rightBigInt.value())));
            } else {
                JSNumber leftNumber = (JSNumber) pair.left();
                JSNumber rightNumber = (JSNumber) pair.right();
                executionContext.virtualMachine.valueStack.push(JSNumber.of(leftNumber.value() - rightNumber.value()));
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleSwap(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
        executionContext.virtualMachine.propertyAccessLock = true;
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
        byte[] instructions = executionContext.instructions;
        int argumentCount = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;

        // Peek at the callee to check if we can use the trampoline
        int calleeIndex = executionContext.sp - argumentCount - 2;
        JSValue callee = executionContext.peek(executionContext.sp - 1 - calleeIndex);

        // Check if callee is a non-constructor, non-async/generator bytecode function eligible for TCO.
        // Async and generator functions require special wrapping in call() that the trampoline bypasses.
        boolean canTrampoline = false;
        if (callee instanceof JSBytecodeFunction bytecodeFunc && !(callee instanceof JSClass)) {
            canTrampoline = !bytecodeFunc.isClassConstructor()
                    && !bytecodeFunc.isAsync()
                    && !bytecodeFunc.isGenerator();
        }

        if (canTrampoline) {
            // Pop arguments, receiver, callee for trampoline
            JSValue[] args = argumentCount == 0 ? JSValue.NO_ARGS : new JSValue[argumentCount];
            for (int i = argumentCount - 1; i >= 0; i--) {
                args[i] = executionContext.virtualMachine.valueStack.pop();
            }
            JSValue receiver = executionContext.virtualMachine.valueStack.pop();
            callee = executionContext.virtualMachine.valueStack.pop();
            executionContext.virtualMachine.propertyAccessLock = false;
            executionContext.virtualMachine.resetPropertyAccessTracking();

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
            executionContext.virtualMachine.tailCallPending =
                    new VirtualMachine.TailCallRequest(tailCallee, receiver, args);
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
            case 0 -> executionContext.virtualMachine.context.throwTypeError("'" + throwName + "' is read-only");
            case 1 ->
                    executionContext.virtualMachine.context.throwError("SyntaxError: redeclaration of '" + throwName + "'");
            case 2 -> executionContext.virtualMachine.context.throwReferenceError(throwName + " is not initialized");
            case 3 -> executionContext.virtualMachine.context.throwReferenceError("unsupported reference to 'super'");
            case 4 -> executionContext.virtualMachine.context.throwTypeError("iterator does not have a throw method");
            case 5 -> executionContext.virtualMachine.context.throwReferenceError(throwName);
            default -> throw new JSVirtualMachineException("invalid throw_error type: " + throwType);
        }
        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
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
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
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
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleTypeof(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        String type = JSTypeChecking.typeof(operand);
        executionContext.virtualMachine.valueStack.push(new JSString(type));
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
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
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSNumber.of(JSTypeConversions.toInt32(leftNum.value()) ^ JSTypeConversions.toInt32(rightNum.value())));
        } else {
            VirtualMachine.NumericPair pair;
            try {
                pair = executionContext.virtualMachine.numericPair(left, right);
            } catch (JSVirtualMachineException e) {
                captureVmExceptionAsPending(executionContext, e);
                pair = null;
            }
            if (pair != null && executionContext.virtualMachine.pendingException != null) {
                pair = null;
            }
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                JSBigInt leftBigInt = (JSBigInt) pair.left();
                JSBigInt rightBigInt = (JSBigInt) pair.right();
                executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().xor(rightBigInt.value())));
            } else {
                int result = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left()) ^ JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
                executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
            }
        }
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleYield(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Check if we should skip this yield (resuming from later point)
        if (executionContext.virtualMachine.yieldSkipCount > 0) {
            executionContext.virtualMachine.yieldSkipCount--;
            JSValue yieldedValue = executionContext.virtualMachine.valueStack.pop();
            JSGeneratorState.ResumeRecord resumeRecord = executionContext.virtualMachine.generatorResumeIndex < executionContext.virtualMachine.generatorResumeRecords.size()
                    ? executionContext.virtualMachine.generatorResumeRecords.get(executionContext.virtualMachine.generatorResumeIndex++)
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
            JSGeneratorState.ResumeRecord resumeRecord = executionContext.virtualMachine.generatorResumeIndex < executionContext.virtualMachine.generatorResumeRecords.size()
                    ? executionContext.virtualMachine.generatorResumeRecords.get(executionContext.virtualMachine.generatorResumeIndex)
                    : null;
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
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.yieldResult != null) {
            JSValue returnValue = executionContext.pop();
            executionContext.virtualMachine.saveActiveGeneratorSuspendedExecutionState(
                    executionContext.frame,
                    executionContext.pc,
                    executionContext.virtualMachine.valueStack.stack,
                    executionContext.sp,
                    executionContext.frameStackBase);
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, returnValue);
        }
    }

    static void handleYieldStar(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        try {

            // Check for RETURN/THROW resume (yield* delegation protocol per ES2024 27.5.3.3)
            JSGeneratorState.ResumeRecord resumeRecord =
                    executionContext.virtualMachine.generatorResumeIndex < executionContext.virtualMachine.generatorResumeRecords.size()
                            ? executionContext.virtualMachine.generatorResumeRecords.get(executionContext.virtualMachine.generatorResumeIndex)
                            : null;
            YieldResult lastYieldResult = executionContext.virtualMachine.activeGeneratorState != null
                    ? executionContext.virtualMachine.activeGeneratorState.getLastYieldResult()
                    : null;
            boolean reuseDelegateIterator = resumeRecord != null
                    && lastYieldResult != null
                    && lastYieldResult.isYieldStar()
                    && lastYieldResult.delegateIterator() != null
                    && lastYieldResult.delegationProgramCounter() == executionContext.pc;

            JSValue iterable = JSUndefined.INSTANCE;
            if (!reuseDelegateIterator) {
                // Fresh delegation entry: iterable is on top of stack.
                iterable = executionContext.virtualMachine.valueStack.pop();
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
                    iterableObj = executionContext.virtualMachine.toObject(iterable);
                    if (iterableObj == null) {
                        throw new JSVirtualMachineException("Object is not iterable");
                    }
                }

                // Get Symbol.iterator method
                JSValue iteratorMethod =
                        iterableObj.get(PropertyKey.SYMBOL_ITERATOR);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.getPendingException().toString(),
                            executionContext.virtualMachine.context.getPendingException());
                }
                if (!JSTypeChecking.isCallable(iteratorMethod)) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.throwTypeError("Object is not iterable"));
                }

                // Call Symbol.iterator to get the iterator
                JSValue iterator = callCallableValue(executionContext.virtualMachine.context, iteratorMethod, iterable, JSValue.NO_ARGS);
                if (!(iterator instanceof JSObject iteratorObject)) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.throwTypeError("Iterator method must return an object"));
                }
                iteratorObj = iteratorObject;
            }

            if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
                executionContext.virtualMachine.generatorResumeIndex++; // consume the record
                JSValue returnValue = resumeRecord.value();

                // Get "return" method from iterator
                JSValue returnMethodValue = iteratorObj.get(PropertyKey.RETURN);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.getPendingException().toString(),
                            executionContext.virtualMachine.context.getPendingException());
                }
                boolean noReturnMethod = returnMethodValue.isNullOrUndefined();

                if (noReturnMethod) {
                    internalStartGeneratorReturnCompletion(executionContext, returnValue);
                    executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                    executionContext.pc += op.getSize();
                    return;
                } else {
                    if (!JSTypeChecking.isCallable(returnMethodValue)) {
                        throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator return is not a function"));
                    }

                    // Call iterator.return(value)
                    JSValue result = callCallableValue(executionContext.virtualMachine.context, returnMethodValue, iteratorObj, new JSValue[]{returnValue});
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.getPendingException().toString(),
                                executionContext.virtualMachine.context.getPendingException());
                    }

                    // Check result is an object (per spec, TypeError if not)
                    if (!(result instanceof JSObject)) {
                        throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator must return an object"));
                    }

                    // Check done flag
                    JSValue doneValue = ((JSObject) result).get(PropertyKey.DONE);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.getPendingException().toString(),
                                executionContext.virtualMachine.context.getPendingException());
                    }
                    if (JSTypeConversions.toBoolean(doneValue).value()) {
                        JSValue value = ((JSObject) result).get(PropertyKey.VALUE);
                        if (executionContext.virtualMachine.context.hasPendingException()) {
                            throw new JSVirtualMachineException(
                                    executionContext.virtualMachine.context.getPendingException().toString(),
                                    executionContext.virtualMachine.context.getPendingException());
                        }
                        internalStartGeneratorReturnCompletion(executionContext, value);
                        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                        executionContext.pc += op.getSize();
                        return;
                    } else {
                        // Not done - yield the result and continue delegation.
                        executionContext.virtualMachine.yieldResult = new YieldResult(
                                YieldResult.Type.YIELD_STAR,
                                result,
                                iteratorObj,
                                null,
                                executionContext.pc);
                        executionContext.virtualMachine.valueStack.push(result);
                    }
                }
            } else if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
                executionContext.virtualMachine.generatorResumeIndex++; // consume the record
                JSValue throwValue = resumeRecord.value();

                // Get "throw" method from iterator
                JSValue throwMethodValue =
                        iteratorObj.get(PropertyKey.THROW);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.getPendingException().toString(),
                            executionContext.virtualMachine.context.getPendingException());
                }
                boolean noThrowMethod = throwMethodValue.isNullOrUndefined();

                if (noThrowMethod) {
                    // No throw method - close iterator and throw TypeError
                    JSValue closeMethod = iteratorObj.get(PropertyKey.RETURN);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.getPendingException().toString(),
                                executionContext.virtualMachine.context.getPendingException());
                    }
                    if (JSTypeChecking.isCallable(closeMethod)) {
                        callCallableValue(executionContext.virtualMachine.context, closeMethod, iteratorObj, JSValue.NO_ARGS);
                        if (executionContext.virtualMachine.context.hasPendingException()) {
                            throw new JSVirtualMachineException(
                                    executionContext.virtualMachine.context.getPendingException().toString(),
                                    executionContext.virtualMachine.context.getPendingException());
                        }
                    }
                    throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError(
                            "iterator does not have a throw method"));
                }

                if (!JSTypeChecking.isCallable(throwMethodValue)) {
                    throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator throw is not a function"));
                }

                // Call iterator.throw(value)
                JSValue result = callCallableValue(executionContext.virtualMachine.context, throwMethodValue, iteratorObj, new JSValue[]{throwValue});
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.getPendingException().toString(),
                            executionContext.virtualMachine.context.getPendingException());
                }

                // Check result is an object
                if (!(result instanceof JSObject)) {
                    throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator must return an object"));
                }

                // Check done flag
                JSValue doneValue = ((JSObject) result).get(PropertyKey.DONE);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.getPendingException().toString(),
                            executionContext.virtualMachine.context.getPendingException());
                }
                if (JSTypeConversions.toBoolean(doneValue).value()) {
                    // Done - push value and complete the yield* expression
                    JSValue value = ((JSObject) result).get(PropertyKey.VALUE);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.getPendingException().toString(),
                                executionContext.virtualMachine.context.getPendingException());
                    }
                    executionContext.virtualMachine.valueStack.push(value);
                } else {
                    // Not done - yield the result.
                    executionContext.virtualMachine.yieldResult = new YieldResult(
                            YieldResult.Type.YIELD_STAR,
                            result,
                            iteratorObj,
                            null,
                            executionContext.pc);
                    executionContext.virtualMachine.valueStack.push(result);
                }
            } else {
                // Default: NEXT protocol - call iterator.next()
                JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.getPendingException().toString(),
                            executionContext.virtualMachine.context.getPendingException());
                }
                if (!JSTypeChecking.isCallable(nextMethod)) {
                    throw new JSVirtualMachineException("Iterator must have a next method");
                }
                if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.NEXT) {
                    executionContext.virtualMachine.generatorResumeIndex++;
                }
                JSValue nextArgument = resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.NEXT
                        ? resumeRecord.value()
                        : JSUndefined.INSTANCE;
                JSValue[] undefinedNextArgs = new JSValue[]{JSUndefined.INSTANCE};
                JSValue[] nextArgs = new JSValue[]{nextArgument};

                // Skip past previously-yielded values during generator replay.
                int remainingYieldSkips = executionContext.virtualMachine.yieldSkipCount;
                boolean innerExhausted = false;
                while (!reuseDelegateIterator && remainingYieldSkips > 0) {
                    JSValue skipResult = callCallableValue(executionContext.virtualMachine.context, nextMethod, iteratorObj, undefinedNextArgs);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.getPendingException().toString(),
                                executionContext.virtualMachine.context.getPendingException());
                    }
                    if (!(skipResult instanceof JSObject)) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.throwTypeError("Iterator result must be an object"));
                    }
                    JSValue skipDone = ((JSObject) skipResult).get(PropertyKey.DONE);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.getPendingException().toString(),
                                executionContext.virtualMachine.context.getPendingException());
                    }
                    if (JSTypeConversions.toBoolean(skipDone).value()) {
                        // Inner iterator exhausted during skip
                        JSValue value = ((JSObject) skipResult).get(PropertyKey.VALUE);
                        if (executionContext.virtualMachine.context.hasPendingException()) {
                            throw new JSVirtualMachineException(
                                    executionContext.virtualMachine.context.getPendingException().toString(),
                                    executionContext.virtualMachine.context.getPendingException());
                        }
                        executionContext.virtualMachine.valueStack.push(value);
                        innerExhausted = true;
                        break;
                    }
                    remainingYieldSkips--;
                }
                executionContext.virtualMachine.yieldSkipCount = remainingYieldSkips;

                if (!innerExhausted) {
                    JSValue result = callCallableValue(executionContext.virtualMachine.context, nextMethod, iteratorObj, nextArgs);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.getPendingException().toString(),
                                executionContext.virtualMachine.context.getPendingException());
                    }

                    // The result should be an object (the iterator result)
                    if (!(result instanceof JSObject)) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.throwTypeError("Iterator result must be an object"));
                    }

                    // Check if the inner iterator is done
                    JSValue doneValue = ((JSObject) result).get(PropertyKey.DONE);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        throw new JSVirtualMachineException(
                                executionContext.virtualMachine.context.getPendingException().toString(),
                                executionContext.virtualMachine.context.getPendingException());
                    }
                    if (JSTypeConversions.toBoolean(doneValue).value()) {
                        if (reuseDelegateIterator && executionContext.virtualMachine.yieldSkipCount > 0) {
                            executionContext.virtualMachine.yieldSkipCount--;
                        }
                        // Inner iterator done - yield* expression value is the final value
                        JSValue value = ((JSObject) result).get(PropertyKey.VALUE);
                        if (executionContext.virtualMachine.context.hasPendingException()) {
                            throw new JSVirtualMachineException(
                                    executionContext.virtualMachine.context.getPendingException().toString(),
                                    executionContext.virtualMachine.context.getPendingException());
                        }
                        executionContext.virtualMachine.valueStack.push(value);
                        // Don't set yieldResult - the yield* expression completes
                    } else {
                        // Set yield result to the raw iterator result object
                        executionContext.virtualMachine.yieldResult = new YieldResult(
                                YieldResult.Type.YIELD_STAR,
                                result,
                                iteratorObj,
                                null,
                                executionContext.pc);
                        executionContext.virtualMachine.valueStack.push(result);
                    }
                }
            }
            if (executionContext.virtualMachine.yieldResult != null) {
                executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
                JSValue returnValue = executionContext.pop();
                // Resume at the same YIELD_STAR opcode until delegation completes.
                executionContext.virtualMachine.saveActiveGeneratorSuspendedExecutionState(
                        executionContext.frame,
                        executionContext.pc,
                        executionContext.virtualMachine.valueStack.stack,
                        executionContext.sp,
                        executionContext.frameStackBase);
                executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, returnValue);
                return;
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
        } catch (JSVirtualMachineException exception) {
            executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(exception);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            executionContext.pc += op.getSize();
        }
    }

    /**
     * Create VarRef array from capture source info during FCLOSURE.
     * For LOCAL sources, creates/reuses a VarRef pointing to the parent's local slot.
     * For VAR_REF sources, shares the parent's existing VarRef.
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
            ExecutionContext executionContext,
            int sp) {
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
        // Pop arguments from stack
        JSValue[] args = argCount == 0 ? JSValue.NO_ARGS : new JSValue[argCount];
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
        virtualMachine.propertyAccessLock = false;
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
                JSContext errorContext = nativeFunc.getRealmContext() != null
                        ? nativeFunc.getRealmContext()
                        : context;
                String constructorName = nativeFunc.getName() != null ? nativeFunc.getName() : "constructor";
                virtualMachine.resetPropertyAccessTracking();
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
                    result = JSGlobalObject.GlobalFunction.eval(
                            evalRealmContext,
                            context,
                            args,
                            true);
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
            virtualMachine.resetPropertyAccessTracking();
        } else if (callee instanceof JSProxy proxy) {
            JSValue result = virtualMachine.proxyApply(proxy, receiver, args);
            stack[sp++] = result;
            virtualMachine.resetPropertyAccessTracking();
        } else if (callee instanceof JSFunction function) {
            if (function.getHomeObject() == null
                    && receiver instanceof JSObject receiverObject
                    && function instanceof JSBytecodeFunction bytecodeFunction) {
                String functionName = bytecodeFunction.getName();
                if ("<static initializer>".equals(functionName)
                        || "<static field initializer>".equals(functionName)) {
                    bytecodeFunction.setHomeObject(receiverObject);
                }
            }

            boolean isClassCtor = function instanceof JSClass;
            if (!isClassCtor && function instanceof JSBytecodeFunction bytecodeFunc) {
                isClassCtor = bytecodeFunc.isClassConstructor();
            }
            if (isClassCtor) {
                JSContext errorContext = function.getRealmContext() != null
                        ? function.getRealmContext()
                        : context;
                virtualMachine.resetPropertyAccessTracking();
                virtualMachine.pendingException = errorContext.throwTypeError("Class constructor " + function.getName()
                        + " cannot be invoked without 'new'");
                errorContext.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
            }

            if (function instanceof JSBytecodeFunction bytecodeFunc) {
                try {
                    // Call through the function's call method to handle async wrapping
                    JSValue result = bytecodeFunc.call(context, receiver, args);
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
            virtualMachine.resetPropertyAccessTracking();
        } else {
            // Not a function - set pending TypeError so JS catch handlers can process it
            // Generate a descriptive error message similar to V8/QuickJS
            String message;
            if (!virtualMachine.propertyAccessChain.isEmpty()) {
                // Use the tracked property access for better error messages
                message = virtualMachine.propertyAccessChain + " is not a function";
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
            virtualMachine.resetPropertyAccessTracking();
            virtualMachine.pendingException = context.throwTypeError(message);
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

    private static StackFrame internalResolveEvalDynamicScopeFrame(ExecutionContext executionContext) {
        if (executionContext.frame == null
                || !(executionContext.frame.getFunction() instanceof JSBytecodeFunction currentBytecodeFunction)) {
            return null;
        }
        JSContext context = executionContext.virtualMachine.context;
        if (context.hasEvalOverlayFrames()) {
            StackFrame callerFrame = executionContext.frame != null ? executionContext.frame.getCaller() : null;
            if (callerFrame != null) {
                return callerFrame;
            }
        }
        if (currentBytecodeFunction.isEvalDynamicScopeLookupEnabled()) {
            return currentBytecodeFunction.getEvalDynamicScopeFrame();
        }
        return null;
    }

    private static StackFrame internalResolveEvalDynamicScopeFrameForCurrentFunction(ExecutionContext executionContext) {
        if (executionContext.frame == null
                || !(executionContext.frame.getFunction() instanceof JSBytecodeFunction currentBytecodeFunction)
                || !currentBytecodeFunction.isEvalDynamicScopeLookupEnabled()) {
            return null;
        }
        return currentBytecodeFunction.getEvalDynamicScopeFrame();
    }

    private static IdentityHashMap<JSSymbol, JSSymbol> internalResolvePrivateSymbolRemap(
            ExecutionContext executionContext,
            JSBytecodeFunction templateFunction,
            int pc,
            Opcode op,
            int sp) {
        IdentityHashMap<JSSymbol, JSSymbol> activeRemap =
                internalGetActivePrivateSymbolRemap(executionContext, sp);
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
     * Create VarRef array from capture source info during FCLOSURE.
     * For LOCAL sources, creates/reuses a VarRef pointing to the parent's local slot.
     * For VAR_REF sources, shares the parent's existing VarRef.
     */
    private static void internalStartGeneratorReturnCompletion(
            ExecutionContext executionContext,
            JSValue returnValue) {
        executionContext.virtualMachine.generatorReturnValue = returnValue;
        executionContext.virtualMachine.generatorForceReturn = true;
        executionContext.virtualMachine.pendingException = returnValue;
        executionContext.virtualMachine.context.setPendingException(returnValue);
    }

    private static boolean internalStartsClassDefinition(ExecutionContext executionContext, int pc, Opcode op) {
        int nextPc = pc + op.getSize();
        if (nextPc >= executionContext.instructions.length) {
            return false;
        }
        Opcode nextOpcode = executionContext.decodedOpcodes[nextPc];
        return nextOpcode == Opcode.DEFINE_CLASS || nextOpcode == Opcode.DEFINE_CLASS_COMPUTED;
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

    private static void restoreForOfStateWithoutIteratorCloseMarker(
            ExecutionContext executionContext,
            JSStackValue[] preservedMarkers,
            int preservedMarkerCount,
            int depth) {
        if (preservedMarkers != null) {
            for (int markerOffset = preservedMarkerCount - 1; markerOffset >= 0; markerOffset--) {
                executionContext.virtualMachine.valueStack.pushStackValue(preservedMarkers[markerOffset]);
            }
        }
        for (int valueOffset = 0; valueOffset < depth; valueOffset++) {
            executionContext.virtualMachine.forOfTempValues[valueOffset] = null;
        }
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

    private record EvalScopedLocalBinding(StackFrame frame, int localIndex, JSValue value) {
    }
}
