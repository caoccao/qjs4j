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

public final class OpcodeHandler {
    private OpcodeHandler() {
    }

    static void handleAdd(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() + rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleAdd(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleAddLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue rightValue = (JSValue) executionContext.stack[--executionContext.sp];
        JSValue leftValue = executionContext.locals[localIndex];
        if (leftValue == null) {
            leftValue = JSUndefined.INSTANCE;
        }
        executionContext.locals[localIndex] = executionContext.virtualMachine.addValues(leftValue, rightValue);
        executionContext.pc = pc + op.getSize();
    }

    static void handleAnd(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(((int) leftNumber.value()) & ((int) rightNumber.value()));
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleAnd(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleAppend(Opcode op, ExecutionContext executionContext) {
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
            JSValue iterator = JSIteratorHelper.getIterator(executionContext.virtualMachine.context, enumerableObject);
            if (iterator == null) {
                executionContext.virtualMachine.context.throwError("TypeError", "Value is not iterable");
                throw new JSVirtualMachineException("APPEND: value is not iterable");
            }

            while (true) {
                JSObject resultObject = JSIteratorHelper.iteratorNext(iterator, executionContext.virtualMachine.context);
                if (resultObject == null) {
                    break;
                }
                JSValue doneValue = resultObject.get("done");
                if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                    break;
                }
                JSValue value = resultObject.get(PropertyKey.VALUE);
                array.set(executionContext.virtualMachine.context, position++, value);
            }

            stack[sp++] = array;
            stack[sp++] = JSNumber.of(position);
        } catch (Exception e) {
            throw new JSVirtualMachineException("APPEND: error iterating: " + e.getMessage(), e);
        }

        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleApply(Opcode op, ExecutionContext executionContext) {
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
                throw new JSVirtualMachineException("APPLY: not a function");
            }
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

    static void handleArrayFrom(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int count = executionContext.bytecode.readU16(pc + 1);
        JSArray array = executionContext.virtualMachine.context.createJSArray();
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
        executionContext.pc = pc + op.getSize();
    }

    static void handleArrayNew(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = executionContext.virtualMachine.context.createJSArray();
        executionContext.pc += op.getSize();
    }

    static void handleAsyncYieldStar(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleAsyncYieldStar(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.yieldResult != null) {
            JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
            executionContext.virtualMachine.clearActiveGeneratorSuspendedExecutionState();
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, returnValue);
        }
    }

    static void handleAwait(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleAwait(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.awaitSuspensionPromise != null) {
            executionContext.virtualMachine.saveActiveGeneratorSuspendedExecutionState(
                    executionContext.frame,
                    executionContext.pc,
                    executionContext.stack,
                    executionContext.sp,
                    executionContext.frameStackBase);
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, JSUndefined.INSTANCE);
        }
    }

    static void handleCall(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        byte[] instructions = executionContext.instructions;
        int argumentCount = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleCall(executionContext, argumentCount);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleCall0(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleCall(executionContext, 0);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleCall1(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleCall(executionContext, 1);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleCall2(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleCall(executionContext, 2);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleCall3(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleCall(executionContext, 3);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleCallConstructor(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        byte[] instructions = executionContext.instructions;
        int argumentCount = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleCallConstructor(executionContext, argumentCount);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc = pc + op.getSize();
    }

    static void handleCatch(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int rawCatchOffset = executionContext.bytecode.readI32(pc + 1);
        boolean isFinally = (rawCatchOffset & 0x80000000) != 0;
        int catchOffset = rawCatchOffset & 0x7FFFFFFF;
        int catchHandlerProgramCounter = pc + op.getSize() + catchOffset;
        executionContext.stack[executionContext.sp++] = new JSCatchOffset(catchHandlerProgramCounter, isFinally);
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
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue targetValue = (JSValue) stack[sp - 1 - (mask & 3)];
        JSValue sourceValue = (JSValue) stack[sp - 1 - ((mask >> 2) & 7)];
        JSValue excludeListValue = (JSValue) stack[sp - 1 - ((mask >> 5) & 7)];
        executionContext.virtualMachine.copyDataProperties(targetValue, sourceValue, excludeListValue);
        executionContext.pc = pc + op.getSize();
    }

    static void handleDec(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleDec(executionContext);
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
        array.set(executionContext.virtualMachine.context, index, value);

        executionContext.sp = sp;
        executionContext.pc += op.getSize();
    }

    static void handleDefineClass(Opcode op, ExecutionContext executionContext) {
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

        JSObject prototypeObject = executionContext.virtualMachine.context.createJSObject();
        if (superClassValue instanceof JSNull) {
            prototypeObject.setPrototype(null);
        } else if (superClassValue != JSUndefined.INSTANCE) {
            if (!JSTypeChecking.isConstructor(superClassValue)) {
                executionContext.virtualMachine.context.throwTypeError("parent class must be constructor");
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            if (superClassValue instanceof JSFunction superFunction) {
                executionContext.virtualMachine.context.transferPrototype(prototypeObject, superFunction);
                constructorFunction.setPrototype(superFunction);
            }
        }

        JSObject constructorObject = constructorFunction;
        constructorObject.defineProperty(
                PropertyKey.fromString("prototype"),
                prototypeObject,
                PropertyDescriptor.DataState.None);

        prototypeObject.set(PropertyKey.CONSTRUCTOR, constructorValue);
        executionContext.virtualMachine.setObjectName(constructorValue, new JSString(className));

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

        JSObject prototypeObject = executionContext.virtualMachine.context.createJSObject();
        boolean hasHeritage = (classFlags & 1) != 0;
        if (hasHeritage && superClassValue != JSUndefined.INSTANCE && superClassValue != JSNull.INSTANCE) {
            if (superClassValue instanceof JSFunction superFunction) {
                executionContext.virtualMachine.context.transferPrototype(prototypeObject, superFunction);
                constructorFunction.setPrototype(superFunction);
            } else {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("parent class must be constructor"));
            }
        }

        constructorFunction.defineProperty(
                PropertyKey.fromString("prototype"),
                prototypeObject,
                PropertyDescriptor.DataState.None);
        prototypeObject.set(PropertyKey.CONSTRUCTOR, constructorValue);
        JSString computedClassName = executionContext.virtualMachine.getComputedNameString(computedClassNameValue);
        if (computedClassName.value().isEmpty()) {
            computedClassName = new JSString(className);
        }
        executionContext.virtualMachine.setObjectName(constructorValue, computedClassName);

        stack[sp++] = prototypeObject;
        stack[sp++] = constructorValue;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleDefineField(Opcode op, ExecutionContext executionContext) {
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
        executionContext.pc = pc + op.getSize();
    }

    static void handleDefineMethod(Opcode op, ExecutionContext executionContext) {
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
        executionContext.pc = pc + op.getSize();
    }

    static void handleDefineMethodComputed(Opcode op, ExecutionContext executionContext) {
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
            PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, propertyValue);
            JSString computedName = executionContext.virtualMachine.getComputedNameString(propertyValue);
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
                executionContext.virtualMachine.setObjectName(methodFunction, new JSString(namePrefix + computedName.value()));
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
        executionContext.pc = pc + op.getSize();
    }

    static void handleDefinePrivateField(Opcode op, ExecutionContext executionContext) {
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
        executionContext.pc = pc + op.getSize();
    }

    static void handleDefineProp(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue propertyValue = (JSValue) stack[--sp];
        JSValue propertyKeyValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];
        if (objectValue instanceof JSObject jsObject) {
            PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, propertyKeyValue);
            jsObject.defineProperty(executionContext.virtualMachine.context, key, propertyValue, PropertyDescriptor.DataState.All);
        }
        executionContext.sp = sp;
        executionContext.pc += op.getSize();
    }

    static void handleDelete(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleDelete(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleDeleteVar(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        boolean deleted = executionContext.virtualMachine.context.getGlobalObject().delete(executionContext.virtualMachine.context, PropertyKey.fromString(variableName));
        executionContext.stack[executionContext.sp++] = JSBoolean.valueOf(deleted);
        executionContext.pc = pc + op.getSize();
    }

    static void handleDiv(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleDiv(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleDrop(Opcode op, ExecutionContext executionContext) {
        executionContext.sp--;
        executionContext.pc += 1;
    }

    static void handleDup(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 1];
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    static void handleDup1(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 2];
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    static void handleDup2(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        stack[sp] = stack[sp - 2];
        stack[sp + 1] = stack[sp - 1];
        executionContext.sp = sp + 2;
        executionContext.pc += 1;
    }

    static void handleEq(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleEq(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleExp(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleExp(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleFclosure(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int functionIndex = executionContext.bytecode.readU32(pc + 1);
        JSValue functionValue = executionContext.bytecode.getConstants()[functionIndex];
        if (functionValue instanceof JSBytecodeFunction templateFunction) {
            int[] captureInfos = templateFunction.getCaptureSourceInfos();
            JSBytecodeFunction closureFunction;
            if (captureInfos != null) {
                VarRef[] capturedVarRefs = internalCreateVarRefsFromCaptures(captureInfos, executionContext.frame);
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
            // Arrow functions capture this from the enclosing scope
            if (closureFunction.isArrow()) {
                closureFunction.setCapturedThisArg(executionContext.frame.getThisArg());
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
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        int functionIndex = executionContext.bytecode.readU8(pc + 1);
        JSValue functionValue = executionContext.bytecode.getConstants()[functionIndex];
        if (functionValue instanceof JSBytecodeFunction templateFunction) {
            int[] captureInfos = templateFunction.getCaptureSourceInfos();
            JSBytecodeFunction closureFunction;
            if (captureInfos != null) {
                VarRef[] capturedVarRefs = internalCreateVarRefsFromCaptures(captureInfos, executionContext.frame);
                closureFunction = templateFunction.copyWithVarRefs(capturedVarRefs);
            } else {
                int closureCount = templateFunction.getClosureVars().length;
                JSValue[] capturedClosureVars = new JSValue[closureCount];
                for (int i = closureCount - 1; i >= 0; i--) {
                    capturedClosureVars[i] = (JSValue) stack[--sp];
                }
                closureFunction = templateFunction.copyWithClosureVars(capturedClosureVars);
            }
            // Arrow functions capture this from the enclosing scope
            if (closureFunction.isArrow()) {
                closureFunction.setCapturedThisArg(executionContext.frame.getThisArg());
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
        internalHandleForAwaitOfNext(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleForAwaitOfStart(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleForAwaitOfStart(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleForInEnd(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleForInEnd(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleForInNext(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleForInNext(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleForInStart(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleForInStart(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleForOfNext(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int depth = executionContext.bytecode.readU8(pc + 1);
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleForOfNext(executionContext, depth);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc = pc + op.getSize();
    }

    static void handleForOfStart(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleForOfStart(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleGetArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.stack[executionContext.sp++] = executionContext.virtualMachine.getArgumentValue(argumentIndex);
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
        executionContext.stack[executionContext.sp++] = executionContext.virtualMachine.getArgumentValue(argumentIndex);
        executionContext.pc += op.getSize();
    }

    static void handleGetArrayEl(Opcode op, ExecutionContext executionContext) {
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
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        executionContext.virtualMachine.valueStack.stackTop = sp - 1;
        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            try {
                PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                JSValue result = targetObject.get(executionContext.virtualMachine.context, key);
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
            executionContext.virtualMachine.resetPropertyAccessTracking();
            stack[sp - 1] = JSUndefined.INSTANCE;
        }
        executionContext.virtualMachine.valueStack.stackTop = sp;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetArrayEl2(Opcode op, ExecutionContext executionContext) {
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
                executionContext.pc = pc + op.getSize();
                return;
            }
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(arrayObjectValue);
        if (targetObject != null) {
            try {
                PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                JSValue result = targetObject.get(executionContext.virtualMachine.context, key);
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
            stack[sp++] = JSUndefined.INSTANCE;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetArrayEl3(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[sp - 1];
        JSValue arrayObjectValue = (JSValue) stack[sp - 2];

        if (!(indexValue instanceof JSNumber || indexValue instanceof JSString || indexValue instanceof JSSymbol)) {
            if (arrayObjectValue.isNullOrUndefined()) {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("value has no property"));
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
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("value has no property"));
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
        JSValue result = targetObject.get(executionContext.virtualMachine.context, key);
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
        JSValue objectValue = (JSValue) executionContext.stack[--executionContext.sp];

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(executionContext.virtualMachine.context, PropertyKey.fromString(fieldName));
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
            } else {
                if (executionContext.virtualMachine.trackPropertyAccess && !executionContext.virtualMachine.propertyAccessLock) {
                    if (!executionContext.virtualMachine.propertyAccessChain.isEmpty()) {
                        executionContext.virtualMachine.propertyAccessChain.append('.');
                    }
                    executionContext.virtualMachine.propertyAccessChain.append(fieldName);
                }
                executionContext.stack[executionContext.sp++] = result;
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("cannot read property '" + fieldName + "' of " + typeName);
            executionContext.virtualMachine.resetPropertyAccessTracking();
            executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetLength(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSValue objectValue = (JSValue) executionContext.stack[--executionContext.sp];
        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject != null) {
            JSValue result = targetObject.get(executionContext.virtualMachine.context, PropertyKey.LENGTH);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
            } else {
                executionContext.stack[executionContext.sp++] = result;
            }
        } else {
            String typeName = objectValue instanceof JSNull ? "null" : "undefined";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("cannot read property 'length' of " + typeName);
            executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetLoc(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int localIndex = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        JSValue localValue = executionContext.locals[localIndex];
        executionContext.stack[executionContext.sp++] = localValue != null ? localValue : JSUndefined.INSTANCE;
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc0(Opcode op, ExecutionContext executionContext) {
        JSValue localValue = executionContext.locals[0];
        executionContext.stack[executionContext.sp++] = localValue != null ? localValue : JSUndefined.INSTANCE;
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc1(Opcode op, ExecutionContext executionContext) {
        JSValue localValue = executionContext.locals[1];
        executionContext.stack[executionContext.sp++] = localValue != null ? localValue : JSUndefined.INSTANCE;
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc2(Opcode op, ExecutionContext executionContext) {
        JSValue localValue = executionContext.locals[2];
        executionContext.stack[executionContext.sp++] = localValue != null ? localValue : JSUndefined.INSTANCE;
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc3(Opcode op, ExecutionContext executionContext) {
        JSValue localValue = executionContext.locals[3];
        executionContext.stack[executionContext.sp++] = localValue != null ? localValue : JSUndefined.INSTANCE;
        executionContext.pc += op.getSize();
    }

    static void handleGetLoc8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue localValue = executionContext.locals[localIndex];
        executionContext.stack[executionContext.sp++] = localValue != null ? localValue : JSUndefined.INSTANCE;
        executionContext.pc += op.getSize();
    }

    static void handleGetLocCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue localValue = executionContext.frame.getLocals()[localIndex];
        if (executionContext.virtualMachine.isUninitialized(localValue)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        executionContext.stack[executionContext.sp++] = localValue;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetPrivateField(Opcode op, ExecutionContext executionContext) {
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
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetRefValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue propertyValue = (JSValue) stack[sp - 1];
        JSValue objectValue = (JSValue) stack[sp - 2];
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, propertyValue);

        if (objectValue.isUndefined()) {
            String name = key != null ? key.toPropertyString() : "variable";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwReferenceError(name + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("value has no property");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        JSValue value;
        if (!targetObject.has(key)) {
            if (executionContext.virtualMachine.context.isStrictMode()) {
                String name = key != null ? key.toPropertyString() : "variable";
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwReferenceError(name + " is not defined");
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                executionContext.pc = pc + op.getSize();
                return;
            }
            value = JSUndefined.INSTANCE;
        } else {
            value = targetObject.get(executionContext.virtualMachine.context, key);
        }
        stack[sp++] = value;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetSuper(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
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
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue keyValue = (JSValue) stack[--sp];
        JSValue superObjectValue = (JSValue) stack[--sp];
        JSValue receiverValue = (JSValue) stack[--sp];

        if (!(superObjectValue instanceof JSObject superObject)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("super object expected"));
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, keyValue);
        JSValue result;
        if (receiverValue instanceof JSObject receiverObject) {
            result = superObject.getWithReceiver(key, executionContext.virtualMachine.context, receiverObject);
        } else {
            JSObject boxedReceiver = executionContext.virtualMachine.toObject(receiverValue);
            result = boxedReceiver != null
                    ? superObject.getWithReceiver(key, executionContext.virtualMachine.context, boxedReceiver)
                    : superObject.get(executionContext.virtualMachine.context, key);
        }

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
        JSStackValue[] stack = executionContext.stack;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        PropertyKey key = PropertyKey.fromString(variableName);
        JSObject globalObject = executionContext.virtualMachine.context.getGlobalObject();
        if (!globalObject.has(key)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwReferenceError(variableName + " is not defined");
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            JSValue variableValue = globalObject.get(key);
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
        executionContext.stack[executionContext.sp++] = executionContext.frame.getVarRef(varRefIndex);
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetVarRefCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = executionContext.frame.getVarRef(varRefIndex);
        if (executionContext.virtualMachine.isUninitialized(value)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        executionContext.stack[executionContext.sp++] = value;
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
        executionContext.stack[executionContext.sp++] = executionContext.frame.getVarRef(varRefIndex);
        executionContext.pc += op.getSize();
    }

    static void handleGetVarUndef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.stack[executionContext.sp++] = executionContext.frame.getVarRef(varRefIndex);
        executionContext.pc = pc + op.getSize();
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
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() > rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleGt(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleGte(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() >= rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleGte(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleIfFalse(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        JSValue conditionValue = (JSValue) executionContext.stack[--executionContext.sp];
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
        JSValue conditionValue = (JSValue) executionContext.stack[--executionContext.sp];
        if (executionContext.virtualMachine.isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + op.getSize();
        } else {
            executionContext.pc = pc + op.getSize() + executionContext.instructions[pc + 1];
        }
    }

    static void handleIfTrue(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        JSValue conditionValue = (JSValue) executionContext.stack[--executionContext.sp];
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
        JSValue conditionValue = (JSValue) executionContext.stack[--executionContext.sp];
        if (executionContext.virtualMachine.isBranchTruthy(conditionValue)) {
            executionContext.pc = pc + op.getSize() + executionContext.instructions[pc + 1];
        } else {
            executionContext.pc = pc + op.getSize();
        }
    }

    static void handleIn(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleIn(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleInc(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleInc(executionContext);
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
        internalHandleInitCtor(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleInitialYield(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleInitialYield(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.yieldResult != null) {
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, JSUndefined.INSTANCE);
        }
    }

    static void handleInsert2(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue topValue = stack[sp - 1];
        stack[sp] = topValue;
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = topValue;
        executionContext.sp = sp + 1;
        executionContext.pc += 1;
    }

    static void handleInsert3(Opcode op, ExecutionContext executionContext) {
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

    static void handleInsert4(Opcode op, ExecutionContext executionContext) {
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

    static void handleInstanceof(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleInstanceof(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleInvalid(Opcode op, ExecutionContext executionContext) {
        throw new JSVirtualMachineException("Invalid opcode at PC " + executionContext.pc);
    }

    static void handleIsNull(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isNull());
        executionContext.pc += op.getSize();
    }

    static void handleIsUndefined(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isUndefined());
        executionContext.pc += op.getSize();
    }

    static void handleIsUndefinedOrNull(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value instanceof JSNull || value instanceof JSUndefined);
        executionContext.pc += op.getSize();
    }

    static void handleIteratorCall(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int flags = executionContext.bytecode.readU8(pc + 1);
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!(iteratorValue instanceof JSObject iteratorObject)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator call target must be an object"));
        }

        String methodName = (flags & 1) != 0 ? "throw" : "return";
        JSValue methodValue = (flags & 1) != 0
                ? iteratorObject.get(executionContext.virtualMachine.context, PropertyKey.THROW)
                : iteratorObject.get(executionContext.virtualMachine.context, PropertyKey.RETURN);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            throw new JSVirtualMachineException(
                    executionContext.virtualMachine.context.getPendingException().toString(),
                    executionContext.virtualMachine.context.getPendingException());
        }
        boolean noMethod = methodValue.isNullOrUndefined();
        if (!noMethod) {
            if (!(methodValue instanceof JSFunction method)) {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator " + methodName + " is not a function"));
            }
            JSValue callResult = (flags & 2) != 0
                    ? method.call(executionContext.virtualMachine.context, iteratorObject, VirtualMachine.EMPTY_ARGS)
                    : method.call(executionContext.virtualMachine.context, iteratorObject, new JSValue[]{argumentValue});
            stack[sp - 1] = callResult;
        }
        stack[sp++] = JSBoolean.valueOf(noMethod);
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleIteratorCheckObject(Opcode op, ExecutionContext executionContext) {
        JSValue iteratorResult = (JSValue) executionContext.stack[executionContext.sp - 1];
        if (!(iteratorResult instanceof JSObject)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator must return an object"));
        }
        executionContext.pc += op.getSize();
    }

    static void handleIteratorClose(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue originalPendingException = executionContext.virtualMachine.pendingException;
        sp--;
        sp--;
        JSValue iteratorValue = (JSValue) stack[--sp];
        if (iteratorValue instanceof JSObject iteratorObject && !iteratorValue.isUndefined()) {
            JSValue returnMethodValue = iteratorObject.get(executionContext.virtualMachine.context, PropertyKey.RETURN);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                if (originalPendingException == null) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                }
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.sp = sp;
                executionContext.pc += op.getSize();
                return;
            }
            if (returnMethodValue instanceof JSFunction returnMethod) {
                JSValue closeResult = returnMethod.call(executionContext.virtualMachine.context, iteratorObject, VirtualMachine.EMPTY_ARGS);
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
        JSStackValue[] stack = executionContext.stack;
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
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue catchOffsetValue = (JSValue) stack[sp - 2];
        JSValue nextMethodValue = (JSValue) stack[sp - 3];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!(nextMethodValue instanceof JSFunction nextMethod)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator next is not a function"));
        }
        JSValue nextResult = nextMethod.call(executionContext.virtualMachine.context, iteratorValue, new JSValue[]{argumentValue});
        stack[sp - 1] = nextResult;
        stack[sp - 2] = catchOffsetValue;
        executionContext.pc += op.getSize();
    }

    static void handleLogicalAnd(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleLogicalAnd(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleLogicalNot(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleLogicalNot(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleLogicalOr(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleLogicalOr(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleLt(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() < rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleLt(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleLte(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSBoolean.valueOf(leftNumber.value() <= rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleLte(executionContext);
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
        executionContext.stack[executionContext.sp++] = referenceObject;
        executionContext.stack[executionContext.sp++] = new JSString(atomName);
        executionContext.pc = pc + op.getSize();
    }

    static void handleMakeVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String atomName = executionContext.bytecode.getAtoms()[atomIndex];
        executionContext.stack[executionContext.sp++] = executionContext.virtualMachine.context.getGlobalObject();
        executionContext.stack[executionContext.sp++] = new JSString(atomName);
        executionContext.pc = pc + op.getSize();
    }

    static void handleMod(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleMod(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleMul(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() * rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleMul(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleNeg(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleNeg(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleNeq(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleNeq(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleNip(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        stack[sp - 2] = stack[sp - 1];
        executionContext.sp = sp - 1;
        executionContext.pc += 1;
    }

    static void handleNipCatch(Opcode op, ExecutionContext executionContext) {
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
        internalHandleNot(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleNull(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNull.INSTANCE;
        executionContext.pc += 1;
    }

    static void handleNullishCoalesce(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleNullishCoalesce(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleObjectNew(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = executionContext.virtualMachine.context.createJSObject();
        executionContext.pc += op.getSize();
    }

    static void handleOr(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleOr(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handlePerm3(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 3];
        stack[sp - 3] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
        executionContext.pc += 1;
    }

    static void handlePerm4(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 4];
        stack[sp - 4] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = firstValue;
        executionContext.pc += 1;
    }

    static void handlePerm5(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
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
        internalHandlePlus(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handlePostDec(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandlePostDec(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handlePostInc(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandlePostInc(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handlePrivateIn(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandlePrivateIn(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handlePrivateSymbol(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int fieldNameAtomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtoms()[fieldNameAtomIndex];
        executionContext.stack[executionContext.sp++] = new JSSymbol(fieldName);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePush0(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(0);
        executionContext.pc += 1;
    }

    static void handlePush1(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(1);
        executionContext.pc += 1;
    }

    static void handlePush2(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(2);
        executionContext.pc += 1;
    }

    static void handlePush3(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(3);
        executionContext.pc += 1;
    }

    static void handlePush4(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(4);
        executionContext.pc += 1;
    }

    static void handlePush5(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(5);
        executionContext.pc += 1;
    }

    static void handlePush6(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(6);
        executionContext.pc += 1;
    }

    static void handlePush7(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(7);
        executionContext.pc += 1;
    }

    static void handlePushArray(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue element = (JSValue) stack[--sp];
        JSValue arrayValue = (JSValue) stack[sp - 1];
        if (arrayValue instanceof JSArray jsArray) {
            jsArray.push(element);
        }
        executionContext.sp = sp;
        executionContext.pc += op.getSize();
    }

    static void handlePushAtomValue(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int atomIndex = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        executionContext.stack[executionContext.sp++] = new JSString(executionContext.bytecode.getAtoms()[atomIndex]);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePushBigintI32(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = new JSBigInt(executionContext.bytecode.readI32(executionContext.pc + 1));
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
        executionContext.virtualMachine.initializeConstantValueIfNeeded(constantValue);
        executionContext.stack[executionContext.sp++] = constantValue;
        executionContext.pc = pc + op.getSize();
    }

    static void handlePushConst8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int constIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue constantValue = executionContext.bytecode.getConstants()[constIndex];
        executionContext.virtualMachine.initializeConstantValueIfNeeded(constantValue);
        executionContext.stack[executionContext.sp++] = constantValue;
        executionContext.pc = pc + op.getSize();
    }

    static void handlePushEmptyString(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = new JSString("");
        executionContext.pc += 1;
    }

    static void handlePushFalse(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSBoolean.FALSE;
        executionContext.pc += 1;
    }

    static void handlePushI16(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        executionContext.stack[executionContext.sp++] =
                JSNumber.of((short) (((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF)));
        executionContext.pc = pc + 3;
    }

    static void handlePushI32(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int intValue = ((instructions[pc + 1] & 0xFF) << 24)
                | ((instructions[pc + 2] & 0xFF) << 16)
                | ((instructions[pc + 3] & 0xFF) << 8)
                | (instructions[pc + 4] & 0xFF);
        executionContext.stack[executionContext.sp++] = JSNumber.of(intValue);
        executionContext.pc = pc + 5;
    }

    static void handlePushI8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.stack[executionContext.sp++] = JSNumber.of(executionContext.instructions[pc + 1]);
        executionContext.pc = pc + 2;
    }

    static void handlePushMinus1(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSNumber.of(-1);
        executionContext.pc += 1;
    }

    static void handlePushThis(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = executionContext.frame.getThisArg();
        executionContext.pc += 1;
    }

    static void handlePushTrue(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSBoolean.TRUE;
        executionContext.pc += 1;
    }

    static void handlePutArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue argumentValue = (JSValue) executionContext.stack[--executionContext.sp];
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
        JSValue argumentValue = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.virtualMachine.setArgumentValue(argumentIndex, argumentValue);
        executionContext.pc += op.getSize();
    }

    static void handlePutArrayEl(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue assignedValue = (JSValue) stack[--sp];
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject jsObject) {
            try {
                PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                jsObject.set(executionContext.virtualMachine.context, key, assignedValue);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    executionContext.virtualMachine.context.clearPendingException();
                }
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
            // In strict mode, setting a property on a primitive throws TypeError
            if (executionContext.virtualMachine.context.isStrictMode()) {
                PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                executionContext.virtualMachine.context.throwTypeError(
                        "Cannot create property '" + key + "' on " + JSTypeChecking.typeof(objectValue) + " '" + objectValue + "'");
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
            } else {
                JSObject boxedObject = executionContext.virtualMachine.toObject(objectValue);
                if (boxedObject != null) {
                    try {
                        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, indexValue);
                        boxedObject.set(executionContext.virtualMachine.context, key, assignedValue);
                        if (executionContext.virtualMachine.context.hasPendingException()) {
                            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                            executionContext.virtualMachine.context.clearPendingException();
                        }
                    } catch (JSVirtualMachineException e) {
                        executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                    }
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
        JSValue objectValue = (JSValue) executionContext.stack[--executionContext.sp];
        JSValue fieldValue = (JSValue) executionContext.stack[executionContext.sp - 1];
        PropertyKey propertyKey = PropertyKey.fromString(fieldName);

        if (objectValue instanceof JSObject jsObject) {
            try {
                jsObject.set(executionContext.virtualMachine.context, propertyKey, fieldValue);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    executionContext.virtualMachine.context.clearPendingException();
                }
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            }
        } else if (objectValue instanceof JSNull || objectValue instanceof JSUndefined) {
            executionContext.virtualMachine.context.throwTypeError("cannot set property '" + fieldName + "' of "
                    + (objectValue instanceof JSNull ? "null" : "undefined"));
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
        } else {
            // In strict mode, setting a property on a primitive throws TypeError
            if (executionContext.virtualMachine.context.isStrictMode()) {
                executionContext.virtualMachine.context.throwTypeError(
                        "Cannot create property '" + fieldName + "' on " + JSTypeChecking.typeof(objectValue) + " '" + objectValue + "'");
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.context.clearPendingException();
            } else {
                JSObject boxedObject = executionContext.virtualMachine.toObject(objectValue);
                if (boxedObject != null) {
                    try {
                        boxedObject.set(executionContext.virtualMachine.context, propertyKey, fieldValue);
                        if (executionContext.virtualMachine.context.hasPendingException()) {
                            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                            executionContext.virtualMachine.context.clearPendingException();
                        }
                    } catch (JSVirtualMachineException e) {
                        executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
                    }
                }
            }
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutLoc(Opcode op, ExecutionContext executionContext) {
        byte[] instructions = executionContext.instructions;
        int pc = executionContext.pc;
        int localIndex = ((instructions[pc + 1] & 0xFF) << 8) | (instructions[pc + 2] & 0xFF);
        executionContext.locals[localIndex] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc0(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[0] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc1(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[1] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc2(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[2] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc3(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[3] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc += op.getSize();
    }

    static void handlePutLoc8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        executionContext.locals[localIndex] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc += op.getSize();
    }

    static void handlePutLocCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        localValues[localIndex] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutLocCheckInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (!executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwReferenceError("'this' can be initialized only once"));
        }
        localValues[localIndex] = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutPrivateField(Opcode op, ExecutionContext executionContext) {
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
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutRefValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue setValue = (JSValue) stack[--sp];
        JSValue propertyValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, propertyValue);

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

        if (!targetObject.has(key) && executionContext.virtualMachine.context.isStrictMode()) {
            String name = key != null ? key.toPropertyString() : "variable";
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwReferenceError(name + " is not defined");
            executionContext.sp = sp;
            executionContext.pc = pc + op.getSize();
            return;
        }

        targetObject.set(executionContext.virtualMachine.context, key, setValue);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutSuperValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue keyValue = (JSValue) stack[--sp];
        JSValue superObjectValue = (JSValue) stack[--sp];
        JSValue receiverValue = (JSValue) stack[--sp];
        JSValue assignedValue = (JSValue) stack[--sp];

        if (!(superObjectValue instanceof JSObject superObject)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("super object expected"));
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, keyValue);
        if (receiverValue instanceof JSObject receiverObject) {
            superObject.set(executionContext.virtualMachine.context, key, assignedValue, receiverObject);
        } else {
            JSObject boxedReceiver = executionContext.virtualMachine.toObject(receiverValue);
            if (boxedReceiver != null) {
                superObject.set(executionContext.virtualMachine.context, key, assignedValue, boxedReceiver);
            } else {
                superObject.set(executionContext.virtualMachine.context, key, assignedValue);
            }
        }

        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.context.clearPendingException();
        }
        stack[sp++] = assignedValue;
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVar(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue value = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.virtualMachine.context.getGlobalObject().set(PropertyKey.fromString(variableName), value);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.frame.setVarRef(varRefIndex, value);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue value = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.frame.setVarRef(varRefIndex, value);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRefCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        if (executionContext.virtualMachine.isUninitialized(executionContext.frame.getVarRef(varRefIndex))) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        executionContext.frame.setVarRef(varRefIndex, (JSValue) executionContext.stack[--executionContext.sp]);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRefCheckInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        if (!executionContext.virtualMachine.isUninitialized(executionContext.frame.getVarRef(varRefIndex))) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwReferenceError("variable is already initialized"));
        }
        executionContext.frame.setVarRef(varRefIndex, (JSValue) executionContext.stack[--executionContext.sp]);
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
        JSValue value = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.frame.setVarRef(varRefIndex, value);
        executionContext.pc += op.getSize();
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
        executionContext.stack[executionContext.sp++] = restArray;
        executionContext.pc = pc + op.getSize();
    }

    static void handleReturn(Opcode op, ExecutionContext executionContext) {
        JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
        executionContext.virtualMachine.lastConstructorThisArg = executionContext.frame.getThisArg();
        executionContext.virtualMachine.finalizeExecuteReturn(executionContext);
        executionContext.returnValue = returnValue;
        executionContext.opcodeRequestedReturn = true;
    }

    static void handleReturnAsync(Opcode op, ExecutionContext executionContext) {
        JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
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
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue firstValue = stack[sp - 3];
        stack[sp - 3] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 1];
        stack[sp - 1] = firstValue;
        executionContext.pc += 1;
    }

    static void handleRot3r(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue thirdValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = stack[sp - 3];
        stack[sp - 3] = thirdValue;
        executionContext.pc += 1;
    }

    static void handleSar(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(((int) leftNumber.value()) >> (((int) rightNumber.value()) & 0x1F));
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleSar(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleSetArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.virtualMachine.setArgumentValue(argumentIndex, (JSValue) executionContext.stack[executionContext.sp - 1]);
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
        executionContext.virtualMachine.setArgumentValue(argumentIndex, (JSValue) executionContext.stack[executionContext.sp - 1]);
        executionContext.pc += op.getSize();
    }

    static void handleSetHomeObject(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
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
        executionContext.locals[localIndex] = (JSValue) executionContext.stack[executionContext.sp - 1];
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc0(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[0] = (JSValue) executionContext.stack[executionContext.sp - 1];
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc1(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[1] = (JSValue) executionContext.stack[executionContext.sp - 1];
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc2(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[2] = (JSValue) executionContext.stack[executionContext.sp - 1];
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc3(Opcode op, ExecutionContext executionContext) {
        executionContext.locals[3] = (JSValue) executionContext.stack[executionContext.sp - 1];
        executionContext.pc += op.getSize();
    }

    static void handleSetLoc8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        executionContext.locals[localIndex] = (JSValue) executionContext.stack[executionContext.sp - 1];
        executionContext.pc += op.getSize();
    }

    static void handleSetLocCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.bytecode.readU16(pc + 1);
        JSValue[] localValues = executionContext.frame.getLocals();
        if (executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        localValues[localIndex] = (JSValue) executionContext.stack[executionContext.sp - 1];
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
        executionContext.virtualMachine.setObjectName((JSValue) executionContext.stack[executionContext.sp - 1], new JSString(name));
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetNameComputed(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue nameValue = (JSValue) stack[sp - 2];
        executionContext.virtualMachine.setObjectName((JSValue) stack[sp - 1], executionContext.virtualMachine.getComputedNameString(nameValue));
        executionContext.pc += op.getSize();
    }

    static void handleSetProto(Opcode op, ExecutionContext executionContext) {
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
        executionContext.pc += op.getSize();
    }

    static void handleSetVar(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String variableName = executionContext.bytecode.getAtoms()[atomIndex];
        JSValue value = (JSValue) executionContext.stack[executionContext.sp - 1];
        PropertyKey variableKey = PropertyKey.fromString(variableName);
        JSObject globalObject = executionContext.virtualMachine.context.getGlobalObject();
        if (executionContext.virtualMachine.context.isStrictMode() && !globalObject.has(variableKey)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwReferenceError(variableName + " is not defined"));
        }
        globalObject.set(variableKey, value);
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.bytecode.readU16(pc + 1);
        executionContext.frame.setVarRef(varRefIndex, (JSValue) executionContext.stack[executionContext.sp - 1]);
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
        executionContext.frame.setVarRef(varRefIndex, (JSValue) executionContext.stack[executionContext.sp - 1]);
        executionContext.pc += op.getSize();
    }

    static void handleShl(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(((int) leftNumber.value()) << (((int) rightNumber.value()) & 0x1F));
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleShl(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleShr(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleShr(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleSpecialObject(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int objectType = executionContext.bytecode.readU8(pc + 1);
        JSValue specialObject = executionContext.virtualMachine.createSpecialObject(objectType, executionContext.frame);
        executionContext.stack[executionContext.sp++] = specialObject;
        executionContext.pc = pc + op.getSize();
    }

    static void handleStrictEq(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleStrictEq(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleStrictNeq(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleStrictNeq(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleSub(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rightValue = (JSValue) stack[sp - 1];
        JSValue leftValue = (JSValue) stack[sp - 2];
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            stack[sp - 2] = JSNumber.of(leftNumber.value() - rightNumber.value());
            executionContext.sp = sp - 1;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            internalHandleSub(executionContext);
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleSwap(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
        executionContext.virtualMachine.propertyAccessLock = true;
        executionContext.pc += 1;
    }

    static void handleSwap2(Opcode op, ExecutionContext executionContext) {
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

    static void handleThrow(Opcode op, ExecutionContext executionContext) {
        JSValue exceptionValue = (JSValue) executionContext.stack[--executionContext.sp];
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

    static void handleToPropKey(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
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

    static void handleToPropKey2(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.stack;
        int sp = executionContext.sp;
        JSValue rawKey = (JSValue) stack[--sp];
        JSValue baseObject = (JSValue) stack[--sp];
        stack[sp++] = baseObject;
        try {
            stack[sp++] = executionContext.virtualMachine.toPropertyKeyValue(rawKey);
        } catch (JSVirtualMachineException e) {
            sp--;
            executionContext.virtualMachine.captureVMException(e);
            executionContext.sp = sp;
            executionContext.pc = pc;
            return;
        }
        executionContext.sp = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleToString(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        if (!(value instanceof JSString)) {
            stack[sp - 1] = JSTypeConversions.toString(executionContext.virtualMachine.context, value);
        }
        executionContext.pc += op.getSize();
    }

    static void handleTypeof(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleTypeof(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleTypeofIsFunction(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf("function".equals(JSTypeChecking.typeof(value)));
        executionContext.pc += op.getSize();
    }

    static void handleTypeofIsUndefined(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf("undefined".equals(JSTypeChecking.typeof(value)));
        executionContext.pc += op.getSize();
    }

    static void handleUndefined(Opcode op, ExecutionContext executionContext) {
        executionContext.stack[executionContext.sp++] = JSUndefined.INSTANCE;
        executionContext.pc += 1;
    }

    static void handleXor(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleXor(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
    }

    static void handleYield(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleYield(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.yieldResult != null) {
            JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
            executionContext.virtualMachine.saveActiveGeneratorSuspendedExecutionState(
                    executionContext.frame,
                    executionContext.pc,
                    executionContext.stack,
                    executionContext.sp,
                    executionContext.frameStackBase);
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, returnValue);
        }
    }

    static void handleYieldStar(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        internalHandleYieldStar(executionContext);
        executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        executionContext.pc += op.getSize();
        if (executionContext.virtualMachine.yieldResult != null) {
            JSValue returnValue = (JSValue) executionContext.stack[--executionContext.sp];
            executionContext.virtualMachine.clearActiveGeneratorSuspendedExecutionState();
            executionContext.virtualMachine.requestOpcodeReturnFromExecute(executionContext, returnValue);
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

    private static void internalHandleAdd(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.addValues(left, right));
    }

    private static void internalHandleAnd(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        // Fast path for number AND (avoids toInt32 overhead)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSNumber.of((int) leftNum.value() & (int) rightNum.value()));
            return;
        }
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().and(rightBigInt.value())));
            return;
        }
        int result = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left()) & JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
        executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
    }

    private static void internalHandleAsyncYieldStar(ExecutionContext executionContext) {
        // Keep the same suspension model as sync yield* in the current generator runtime.
        // Full async delegation semantics can be layered on top of this baseline.
        internalHandleYieldStar(executionContext);
    }

    private static void internalHandleAwait(ExecutionContext executionContext) {
        JSValue value = executionContext.virtualMachine.valueStack.pop();

        // If the value is already a promise, use it directly
        // Otherwise, wrap it in a resolved promise
        JSPromise promise;
        if (value instanceof JSPromise) {
            promise = (JSPromise) value;
        } else {
            // Create a new promise and immediately fulfill it
            promise = executionContext.virtualMachine.context.createJSPromise();
            promise.fulfill(value);
        }

        // For proper async/await support, we need to wait for the promise to settle
        // and push the resolved value (not the promise itself)

        // If the promise is pending, we need to process microtasks until it settles
        if (promise.getState() == JSPromise.PromiseState.PENDING) {
            if (executionContext.virtualMachine.awaitSuspensionEnabled && executionContext.virtualMachine.activeGeneratorState != null) {
                executionContext.virtualMachine.awaitSuspensionPromise = promise;
                return;
            }
            // Process microtasks until the promise settles
            executionContext.virtualMachine.context.processMicrotasks();
        }

        // Now the promise should be settled, push the resolved value
        if (promise.getState() == JSPromise.PromiseState.FULFILLED) {
            executionContext.virtualMachine.valueStack.push(promise.getResult());
        } else if (promise.getState() == JSPromise.PromiseState.REJECTED) {
            // Rejected await operand throws into the current async control flow.
            // Let VM catch handling propagate it to surrounding try/catch.
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

    private static void internalHandleCall(ExecutionContext executionContext, int argCount) {
        // Stack layout (bottom to top): method, receiver, arg1, arg2, ...
        // Pop arguments from stack
        JSValue[] args = argCount == 0 ? VirtualMachine.EMPTY_ARGS : new JSValue[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
            args[i] = executionContext.virtualMachine.valueStack.pop();
        }

        // Pop receiver (thisArg)
        JSValue receiver = executionContext.virtualMachine.valueStack.pop();

        // Pop callee (method)
        JSValue callee = executionContext.virtualMachine.valueStack.pop();

        // SWAP locks property tracking while evaluating method-call arguments.
        // Unlock before invoking the callee so nested calls can build their own chains.
        executionContext.virtualMachine.propertyAccessLock = false;

        // Handle proxy apply trap (QuickJS: js_proxy_call)
        if (callee instanceof JSProxy proxy) {
            JSValue result = executionContext.virtualMachine.proxyApply(proxy, receiver, args);
            executionContext.virtualMachine.valueStack.push(result);
            executionContext.virtualMachine.resetPropertyAccessTracking();
            return;
        }

        // Special handling for Symbol constructor (must be called without new)
        if (callee instanceof JSObject calleeObj) {
            if (calleeObj.getConstructorType() == JSConstructorType.SYMBOL_OBJECT) {
                // Call Symbol() function
                JSValue result = SymbolConstructor.call(executionContext.virtualMachine.context, receiver, args);
                executionContext.virtualMachine.valueStack.push(result);
                return;
            }

            // Special handling for BigInt constructor (must be called without new)
            if (calleeObj.getConstructorType() == JSConstructorType.BIG_INT_OBJECT) {
                // Call BigInt() function
                JSValue result = BigIntConstructor.call(executionContext.virtualMachine.context, receiver, args);
                executionContext.virtualMachine.valueStack.push(result);
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
                JSContext errorContext = function.getRealmContext() != null
                        ? function.getRealmContext()
                        : executionContext.virtualMachine.context;
                executionContext.virtualMachine.resetPropertyAccessTracking();
                executionContext.virtualMachine.pendingException = errorContext.throwTypeError("Class constructor " + function.getName()
                        + " cannot be invoked without 'new'");
                errorContext.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                return;
            }

            if (function instanceof JSNativeFunction nativeFunc) {
                // Check if this function requires 'new'
                if (nativeFunc.requiresNew()) {
                    JSContext errorContext = nativeFunc.getRealmContext() != null
                            ? nativeFunc.getRealmContext()
                            : executionContext.virtualMachine.context;
                    String constructorName = nativeFunc.getName() != null ? nativeFunc.getName() : "constructor";
                    executionContext.virtualMachine.resetPropertyAccessTracking();
                    String errorMessage = switch (constructorName) {
                        case JSPromise.NAME -> "Promise constructor cannot be invoked without 'new'";
                        default -> "Constructor " + constructorName + " requires 'new'";
                    };
                    executionContext.virtualMachine.pendingException = errorContext.throwTypeError(errorMessage);
                    errorContext.clearPendingException();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    return;
                }
                // Call native function with receiver as thisArg
                try {
                    JSValue result = nativeFunc.call(executionContext.virtualMachine.context, receiver, args);
                    // Check for pending exception after native function call
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        // Set pending exception in VM and push placeholder
                        // The main loop will handle the exception on next iteration.
                        executionContext.virtualMachine.pendingException =
                                executionContext.virtualMachine.context.getPendingException();
                        executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        executionContext.virtualMachine.valueStack.push(result);
                    }
                } catch (JSException e) {
                    // Native function threw a JSException (e.g. from eval/evalScript)
                    // Convert to pending exception so VM try-catch handles it.
                    executionContext.virtualMachine.pendingException = e.getErrorValue();
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                } catch (JSVirtualMachineException e) {
                    // Native function internally called a bytecode function that threw
                    // (e.g. ToPrimitive calling user's toString/valueOf)
                    if (e.getJsValue() != null) {
                        executionContext.virtualMachine.pendingException = e.getJsValue();
                    } else if (e.getJsError() != null) {
                        executionContext.virtualMachine.pendingException = e.getJsError();
                    } else if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    } else {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError("Error",
                                e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                    }
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                } catch (JSErrorException e) {
                    // Native function threw a typed JS error (e.g. JSRangeErrorException)
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError(e.getErrorType().name(), e.getMessage());
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            } else if (function instanceof JSBytecodeFunction bytecodeFunc) {
                try {
                    // Call through the function's call method to handle async wrapping
                    JSValue result = bytecodeFunc.call(executionContext.virtualMachine.context, receiver, args);
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                        executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        executionContext.virtualMachine.valueStack.push(result);
                    }
                } catch (JSVirtualMachineException e) {
                    if (e.getJsValue() != null) {
                        executionContext.virtualMachine.pendingException = e.getJsValue();
                    } else if (e.getJsError() != null) {
                        executionContext.virtualMachine.pendingException = e.getJsError();
                    } else if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    } else {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError("Error",
                                e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                    }
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            } else if (function instanceof JSBoundFunction boundFunc) {
                // Call bound function - the receiver is ignored for bound functions
                try {
                    JSValue result = boundFunc.call(executionContext.virtualMachine.context, receiver, args);
                    // Check for pending exception after bound function call
                    if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                        executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                    } else {
                        executionContext.virtualMachine.valueStack.push(result);
                    }
                } catch (JSVirtualMachineException e) {
                    if (e.getJsValue() != null) {
                        executionContext.virtualMachine.pendingException = e.getJsValue();
                    } else if (e.getJsError() != null) {
                        executionContext.virtualMachine.pendingException = e.getJsError();
                    } else if (executionContext.virtualMachine.context.hasPendingException()) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                    } else {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError("Error",
                                e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                    }
                    executionContext.virtualMachine.context.clearPendingException();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            } else {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
            // Clear property access tracking after successful call
            executionContext.virtualMachine.resetPropertyAccessTracking();
        } else {
            // Not a function - set pending TypeError so JS catch handlers can process it
            // Generate a descriptive error message similar to V8/QuickJS
            String message;
            if (!executionContext.virtualMachine.propertyAccessChain.isEmpty()) {
                // Use the tracked property access for better error messages
                message = executionContext.virtualMachine.propertyAccessChain + " is not a function";
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
            executionContext.virtualMachine.resetPropertyAccessTracking();
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError(message);
            executionContext.virtualMachine.context.clearPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        }
    }

    private static void internalHandleCallConstructor(ExecutionContext executionContext, int argCount) {
        // Pop arguments
        JSValue[] args = argCount == 0 ? VirtualMachine.EMPTY_ARGS : new JSValue[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
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
                return;
            }
            JSValue result;
            try {
                result = executionContext.virtualMachine.constructFunction(jsFunction, args, jsFunction);
            } catch (JSVirtualMachineException e) {
                // Constructor internally called a bytecode function that threw
                // (e.g., getter in iterable item). Convert to executionContext.virtualMachine.pendingException so
                // the main loop can route it to JavaScript catch handlers.
                if (e.getJsValue() != null) {
                    executionContext.virtualMachine.pendingException = e.getJsValue();
                } else if (e.getJsError() != null) {
                    executionContext.virtualMachine.pendingException = e.getJsError();
                } else if (executionContext.virtualMachine.context.hasPendingException()) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                } else {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError("Error",
                            e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                }
                executionContext.virtualMachine.context.clearPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                executionContext.virtualMachine.valueStack.push(result);
            }
        } else {
            executionContext.virtualMachine.context.throwTypeError("not a constructor");
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        }
    }

    private static void internalHandleDec(ExecutionContext executionContext) {
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.incrementValue(operand, -1));
    }

    private static void internalHandleDelete(ExecutionContext executionContext) {
        JSValue property = executionContext.virtualMachine.valueStack.pop();
        JSValue object = executionContext.virtualMachine.valueStack.pop();
        boolean result = false;
        if (object instanceof JSObject jsObj) {
            PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, property);
            result = jsObj.delete(executionContext.virtualMachine.context, key);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            }
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleDiv(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            if (rightBigInt.value().equals(VirtualMachine.BIGINT_ZERO)) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwRangeError("Division by zero");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().divide(rightBigInt.value())));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        executionContext.virtualMachine.valueStack.push(JSNumber.of(leftNumber.value() / rightNumber.value()));
    }

    private static void internalHandleEq(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleExp(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            BigInteger exponent = rightBigInt.value();
            if (exponent.signum() < 0) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwRangeError("Exponent must be positive");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            final int exponentInt;
            try {
                exponentInt = exponent.intValueExact();
            } catch (ArithmeticException e) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwRangeError("BigInt exponent is too large");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().pow(exponentInt)));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        executionContext.virtualMachine.valueStack.push(JSNumber.of(Math.pow(leftNumber.value(), rightNumber.value())));
    }

    private static void internalHandleForAwaitOfNext(ExecutionContext executionContext) {
        // Stack layout before: iter, next, catch_offset (bottom to top)
        // Stack layout after: iter, next, catch_offset, result (bottom to top)

        // Pop catch offset temporarily
        JSValue catchOffset = executionContext.virtualMachine.valueStack.pop();

        // Peek next method and iterator (don't pop - they stay for next iteration)
        JSValue nextMethod = executionContext.virtualMachine.valueStack.peek(0);  // next method
        JSValue iterator = executionContext.virtualMachine.valueStack.peek(1);    // iterator object

        // Call iterator.next()
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            throw new JSVirtualMachineException("Next method must be a function");
        }

        JSValue result = nextFunc.call(executionContext.virtualMachine.context, iterator, VirtualMachine.EMPTY_ARGS);

        // Restore catch_offset and push the result
        executionContext.virtualMachine.valueStack.push(catchOffset);  // Restore catch_offset
        executionContext.virtualMachine.valueStack.push(result);        // Push the result (promise that resolves to {value, done})
    }

    private static void internalHandleForAwaitOfStart(ExecutionContext executionContext) {
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
            // Non-callable, non-nullish value: TypeError.
            executionContext.virtualMachine.pendingException =
                    executionContext.virtualMachine.context.throwTypeError("object is not async iterable");
            return;
        } else {
            // Fall back to Symbol.iterator (sync iterator that will be auto-wrapped)
            iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);

            if (!(iteratorMethod instanceof JSFunction)) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("object is not async iterable");
                return;
            }
            wrapSyncIteratorAsAsync = true;
        }

        // Call the iterator method to get an iterator
        JSValue iterator = ((JSFunction) iteratorMethod).call(executionContext.virtualMachine.context, iterable, VirtualMachine.EMPTY_ARGS);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            return;
        }

        if (!(iterator instanceof JSObject iteratorObj)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("iterator must return an object");
            return;
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);

        if (!(nextMethod instanceof JSFunction nextFunction)) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("iterator must have a next method");
            return;
        }

        JSValue nextMethodForStack = nextFunction;
        if (wrapSyncIteratorAsAsync) {
            final JSObject syncIteratorObject = iteratorObj;
            final JSFunction syncNextFunction = nextFunction;
            nextMethodForStack = new JSNativeFunction("next", 0, (childContext, thisArg, args) -> {
                JSValue syncResult;
                try {
                    syncResult = syncNextFunction.call(childContext, syncIteratorObject, VirtualMachine.EMPTY_ARGS);
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
                                        JSValue closeResult = returnFunction.call(callbackContext, syncIteratorObject, VirtualMachine.EMPTY_ARGS);
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
        executionContext.virtualMachine.valueStack.push(iterator);         // Iterator object
        executionContext.virtualMachine.valueStack.push(nextMethodForStack);       // next() method
        executionContext.virtualMachine.valueStack.push(JSNumber.of(0));  // Catch offset (placeholder)
    }

    private static void internalHandleForInEnd(ExecutionContext executionContext) {
        // Clean up the enumerator from the stack
        JSStackValue stackValue = executionContext.virtualMachine.valueStack.popStackValue();
        if (!(stackValue instanceof JSInternalValue internal) ||
                !(internal.value() instanceof JSForInEnumerator)) {
            throw new JSVirtualMachineException("Invalid for-in enumerator in FOR_IN_END");
        }
        // Just pop it, no need to do anything else
    }

    private static void internalHandleForInNext(ExecutionContext executionContext) {
        // The enumerator should be on top of the stack (peek at it, don't pop)
        JSStackValue stackValue = executionContext.virtualMachine.valueStack.popStackValue();

        if (!(stackValue instanceof JSInternalValue internal) ||
                !(internal.value() instanceof JSForInEnumerator enumerator)) {
            throw new JSVirtualMachineException("Invalid for-in enumerator");
        }

        // Get the next key from the enumerator
        JSValue nextKey = enumerator.next();

        // Push enumerator back first (so it stays at same position)
        executionContext.virtualMachine.valueStack.pushStackValue(new JSInternalValue(enumerator));

        // Then push the key onto the stack
        executionContext.virtualMachine.valueStack.push(nextKey);
    }

    private static void internalHandleForInStart(ExecutionContext executionContext) {
        // Pop the object from the stack
        JSValue obj = executionContext.virtualMachine.valueStack.pop();

        // Create a for-in enumerator
        JSForInEnumerator enumerator = new JSForInEnumerator(obj);

        // Push the enumerator onto the stack (wrapped in a special internal object)
        // We'll use JSInternalValue to hold it
        executionContext.virtualMachine.valueStack.pushStackValue(new JSInternalValue(enumerator));
    }

    private static void internalHandleForOfNext(ExecutionContext executionContext, int depth) {
        // Stack layout: ... iter next catch_offset [depth values] (bottom to top)
        // The depth parameter tells us how many values are between catch_offset and top
        // Following QuickJS: offset = -3 - depth
        // iter is at sp[offset] = sp[-3-depth], next is at sp[offset+1] = sp[-2-depth]

        // Pop depth values temporarily
        if (executionContext.virtualMachine.forOfTempValues.length < depth) {
            executionContext.virtualMachine.forOfTempValues = new JSValue[Math.max(depth, executionContext.virtualMachine.forOfTempValues.length * 2)];
        }
        for (int i = 0; i < depth; i++) {
            executionContext.virtualMachine.forOfTempValues[i] = executionContext.virtualMachine.valueStack.pop();
        }
        // Now top of stack is catch_offset
        JSValue catchOffset = executionContext.virtualMachine.valueStack.pop();

        // Now peek next method and iterator (don't pop - they stay for next iteration)
        // Stack is now: ... iter next (top)
        JSValue nextMethod = executionContext.virtualMachine.valueStack.peek(0);  // next method (top)
        JSValue iterator = executionContext.virtualMachine.valueStack.peek(1);    // iterator object (below next)

        // Call iterator.next()
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            String actualType = nextMethod == null ? "null" : nextMethod.getClass().getSimpleName();
            String iterType = iterator == null ? "null" : iterator.getClass().getSimpleName();
            throw new JSVirtualMachineException(
                    "Next method must be a function in FOR_OF_NEXT (nextMethod=" + actualType + ", iterator=" + iterType + ")"
            );
        }

        JSValue result = nextFunc.call(executionContext.virtualMachine.context, iterator, VirtualMachine.EMPTY_ARGS);

        // Check for pending exception (e.g., TypedArray detachment during iteration)
        if (executionContext.virtualMachine.context.hasPendingException()) {
            // Restore stack before throwing
            executionContext.virtualMachine.valueStack.push(catchOffset);
            for (int i = depth - 1; i >= 0; i--) {
                executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.forOfTempValues[i]);
                executionContext.virtualMachine.forOfTempValues[i] = null;
            }
            JSValue pendingEx = executionContext.virtualMachine.context.getPendingException();
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
        executionContext.virtualMachine.valueStack.push(catchOffset);
        for (int i = depth - 1; i >= 0; i--) {
            executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.forOfTempValues[i]);
            executionContext.virtualMachine.forOfTempValues[i] = null;
        }
        executionContext.virtualMachine.valueStack.push(value);
        executionContext.virtualMachine.valueStack.push(done ? JSBoolean.TRUE : JSBoolean.FALSE);
    }

    private static void internalHandleForOfStart(ExecutionContext executionContext) {
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
        JSValue iterator = iteratorFunc.call(executionContext.virtualMachine.context, iterable, VirtualMachine.EMPTY_ARGS);

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
        executionContext.virtualMachine.valueStack.push(iterator);         // Iterator object
        executionContext.virtualMachine.valueStack.push(nextMethod);       // next() method
        executionContext.virtualMachine.valueStack.push(JSNumber.of(0));  // Catch offset (placeholder)
    }

    private static void internalHandleGt(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        // Fast path for number comparison
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(leftNum.value() > rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(executionContext.virtualMachine.context, right, left);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleGte(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        // Fast path for number comparison
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(leftNum.value() >= rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(executionContext.virtualMachine.context, right, left) ||
                JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleIn(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (!(right instanceof JSObject jsObj)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("invalid 'in' operand"));
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, left);
        boolean result = jsObj.has(key);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleInc(ExecutionContext executionContext) {
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.incrementValue(operand, 1));
    }

    private static void internalHandleInitCtor(ExecutionContext executionContext) {
        JSValue frameNewTarget = executionContext.virtualMachine.currentFrame.getNewTarget();
        if (frameNewTarget.isNullOrUndefined()) {
            // Keep direct VM opcode tests stable when executionContext.virtualMachine.execute() is used without constructor plumbing.
            if (executionContext.virtualMachine.currentFrame.getCaller() == null) {
                JSValue fallbackThis = executionContext.virtualMachine.currentFrame.getThisArg();
                if (fallbackThis instanceof JSObject) {
                    executionContext.virtualMachine.valueStack.push(fallbackThis);
                    return;
                }
            }
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("class constructors must be invoked with 'new'"));
        }

        // Explicit super(...): APPLY constructor mode left the initialized this value on stack.
        if (executionContext.virtualMachine.valueStack.getStackTop() > executionContext.virtualMachine.currentFrame.getStackBase()) {
            JSValue thisValue = executionContext.virtualMachine.valueStack.pop();
            if (!(thisValue instanceof JSObject jsObject)) {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("super() returned non-object"));
            }
            executionContext.virtualMachine.currentFrame.setThisArg(jsObject);
            executionContext.virtualMachine.valueStack.push(jsObject);
            return;
        }

        // Default derived constructor path:
        // constructor(...args) { super(...args); }
        JSFunction currentFunction = executionContext.virtualMachine.currentFrame.getFunction();
        JSObject superConstructorObject = currentFunction.getPrototype();
        if (!(superConstructorObject instanceof JSFunction superConstructor)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("parent class must be constructor"));
        }

        JSValue superResult = executionContext.virtualMachine.constructFunction(superConstructor, executionContext.virtualMachine.currentFrame.getArguments(), frameNewTarget);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.getPendingException();
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (!(superResult instanceof JSObject superObject)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("super() returned non-object"));
        }
        executionContext.virtualMachine.currentFrame.setThisArg(superObject);
        executionContext.virtualMachine.valueStack.push(superObject);
    }

    private static void internalHandleInitialYield(ExecutionContext executionContext) {
        // Initial yield - generator is being created
        // In QuickJS, this is where generator creation stops and returns the generator object.
        // Per ES spec, parameter defaults are evaluated before INITIAL_YIELD,
        // so errors during parameter initialization (e.g., SyntaxError from eval)
        // are thrown during the generator function call, not during .next().
        if (executionContext.virtualMachine.yieldSkipCount > 0) {
            executionContext.virtualMachine.yieldSkipCount--;
            return;
        }
        // Signal suspension at INITIAL_YIELD.
        executionContext.virtualMachine.yieldResult =
                new YieldResult(YieldResult.Type.INITIAL_YIELD, JSUndefined.INSTANCE);
    }

    private static void internalHandleInstanceof(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();

        // Per ECMAScript spec, right must be an object
        if (!(right instanceof JSObject constructor)) {
            throw new JSVirtualMachineException("Right-hand side of instanceof is not an object");
        }

        JSValue hasInstanceMethod = constructor.get(executionContext.virtualMachine.context, PropertyKey.SYMBOL_HAS_INSTANCE);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            JSValue pendingException = executionContext.virtualMachine.context.getPendingException();
            if (pendingException instanceof JSError jsError) {
                throw new JSVirtualMachineException(jsError);
            }
            throw new JSVirtualMachineException("instanceof check failed");
        }
        if (!(hasInstanceMethod instanceof JSUndefined) && !(hasInstanceMethod instanceof JSNull)) {
            if (!(hasInstanceMethod instanceof JSFunction hasInstanceFunction)) {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("@@hasInstance is not callable"));
            }
            JSValue result = hasInstanceFunction.call(executionContext.virtualMachine.context, right, new JSValue[]{left});
            if (executionContext.virtualMachine.context.hasPendingException()) {
                JSValue pendingException = executionContext.virtualMachine.context.getPendingException();
                if (pendingException instanceof JSError jsError) {
                    throw new JSVirtualMachineException(jsError);
                }
                throw new JSVirtualMachineException("instanceof check failed");
            }
            executionContext.virtualMachine.valueStack.push(JSTypeConversions.toBoolean(result) == JSBoolean.TRUE ? JSBoolean.TRUE : JSBoolean.FALSE);
            return;
        }

        boolean callable = right instanceof JSFunction;
        if (right instanceof JSProxy proxy && JSTypeChecking.isFunction(proxy.getTarget())) {
            callable = true;
        }
        if (!callable) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("Right-hand side of instanceof is not callable"));
        }

        executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.ordinaryHasInstance(right, left) ? JSBoolean.TRUE : JSBoolean.FALSE);
    }

    private static void internalHandleLogicalAnd(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        // Short-circuit: return left if falsy, otherwise right
        if (JSTypeConversions.toBoolean(left) == JSBoolean.FALSE) {
            executionContext.virtualMachine.valueStack.push(left);
        } else {
            executionContext.virtualMachine.valueStack.push(right);
        }
    }

    private static void internalHandleLogicalNot(ExecutionContext executionContext) {
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.toBoolean(operand) == JSBoolean.FALSE;
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleLogicalOr(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        // Short-circuit: return left if truthy, otherwise right
        if (JSTypeConversions.toBoolean(left) == JSBoolean.TRUE) {
            executionContext.virtualMachine.valueStack.push(left);
        } else {
            executionContext.virtualMachine.valueStack.push(right);
        }
    }

    private static void internalHandleLt(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        // Fast path for number comparison (avoids toPrimitive/toNumber overhead)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(leftNum.value() < rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(executionContext.virtualMachine.context, left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleLte(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        // Fast path for number comparison (avoids double type conversion)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(leftNum.value() <= rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(executionContext.virtualMachine.context, left, right) ||
                JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleMod(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            if (rightBigInt.value().equals(VirtualMachine.BIGINT_ZERO)) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwRangeError("Division by zero");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().remainder(rightBigInt.value())));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        executionContext.virtualMachine.valueStack.push(JSNumber.of(leftNumber.value() % rightNumber.value()));
    }

    private static void internalHandleMul(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().multiply(rightBigInt.value())));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        executionContext.virtualMachine.valueStack.push(JSNumber.of(leftNumber.value() * rightNumber.value()));
    }

    private static void internalHandleNeg(ExecutionContext executionContext) {
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue numeric = executionContext.virtualMachine.toNumericValue(operand);
        if (numeric instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().negate()));
            return;
        }
        executionContext.virtualMachine.valueStack.push(JSNumber.of(-((JSNumber) numeric).value()));
    }

    private static void internalHandleNeq(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = !JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleNot(ExecutionContext executionContext) {
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue numeric = executionContext.virtualMachine.toNumericValue(operand);
        if (numeric instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().not()));
            return;
        }
        int result = ~JSTypeConversions.toInt32(executionContext.virtualMachine.context, numeric);
        executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
    }

    private static void internalHandleNullishCoalesce(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        // Return right if left is null or undefined
        if (left instanceof JSNull || left instanceof JSUndefined) {
            executionContext.virtualMachine.valueStack.push(right);
        } else {
            executionContext.virtualMachine.valueStack.push(left);
        }
    }

    private static void internalHandleOr(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSNumber.of((int) leftNum.value() | (int) rightNum.value()));
            return;
        }
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().or(rightBigInt.value())));
            return;
        }
        int result = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left()) | JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
        executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
    }

    private static void internalHandlePlus(ExecutionContext executionContext) {
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSNumber result = JSTypeConversions.toNumber(executionContext.virtualMachine.context, operand);
        executionContext.virtualMachine.capturePendingException();
        executionContext.virtualMachine.valueStack.push(result);
    }

    private static void internalHandlePostDec(ExecutionContext executionContext) {
        // POST_DEC: [value] -> [old_value, new_value]
        // Takes value on top, pushes old value then new value
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue oldValue = executionContext.virtualMachine.toNumericValue(operand);
        executionContext.virtualMachine.valueStack.push(oldValue);
        if (oldValue instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().subtract(VirtualMachine.BIGINT_ONE)));
            return;
        }
        executionContext.virtualMachine.valueStack.push(JSNumber.of(((JSNumber) oldValue).value() - 1));
    }

    private static void internalHandlePostInc(ExecutionContext executionContext) {
        // POST_INC: [value] -> [old_value, new_value]
        // Takes value on top, pushes old value then new value
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue oldValue = executionContext.virtualMachine.toNumericValue(operand);
        executionContext.virtualMachine.valueStack.push(oldValue);
        if (oldValue instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().add(VirtualMachine.BIGINT_ONE)));
            return;
        }
        executionContext.virtualMachine.valueStack.push(JSNumber.of(((JSNumber) oldValue).value() + 1));
    }

    private static void internalHandlePrivateIn(ExecutionContext executionContext) {
        JSValue privateField = executionContext.virtualMachine.valueStack.pop();
        JSValue object = executionContext.virtualMachine.valueStack.pop();

        if (!(object instanceof JSObject jsObj)) {
            throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("invalid 'in' operand"));
        }

        boolean result = false;
        if (privateField instanceof JSSymbol symbol) {
            result = jsObj.has(PropertyKey.fromSymbol(symbol));
        }

        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleSar(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSNumber.of((int) leftNum.value() >> ((int) rightNum.value() & 0x1F)));
            return;
        }
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.shiftBigInt((JSBigInt) pair.left(), (JSBigInt) pair.right(), false));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left());
        int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
        executionContext.virtualMachine.valueStack.push(JSNumber.of(leftInt >> (rightInt & 0x1F)));
    }

    private static void internalHandleShl(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSNumber.of((int) leftNum.value() << ((int) rightNum.value() & 0x1F)));
            return;
        }
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.shiftBigInt((JSBigInt) pair.left(), (JSBigInt) pair.right(), true));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left());
        int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
        executionContext.virtualMachine.valueStack.push(JSNumber.of(leftInt << (rightInt & 0x1F)));
    }

    private static void internalHandleShr(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        JSValue leftPrimitive;
        JSValue rightPrimitive;
        try {
            leftPrimitive = JSTypeConversions.toPrimitive(executionContext.virtualMachine.context, left, JSTypeConversions.PreferredType.NUMBER);
            rightPrimitive = JSTypeConversions.toPrimitive(executionContext.virtualMachine.context, right, JSTypeConversions.PreferredType.NUMBER);
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.capturePendingExceptionFromVmOrContext(e);
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        } catch (JSException e) {
            if (e.getErrorValue() != null) {
                executionContext.virtualMachine.pendingException = e.getErrorValue();
            } else {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwError("Error",
                        e.getMessage() != null ? e.getMessage() : "toPrimitive");
            }
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        executionContext.virtualMachine.capturePendingException();
        if (executionContext.virtualMachine.pendingException != null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (leftPrimitive instanceof JSBigInt || rightPrimitive instanceof JSBigInt) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context.throwTypeError("BigInts do not support >>>");
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        JSValue leftNumeric = JSTypeConversions.toNumber(executionContext.virtualMachine.context, leftPrimitive);
        JSValue rightNumeric = JSTypeConversions.toNumber(executionContext.virtualMachine.context, rightPrimitive);
        if (leftNumeric instanceof JSNumber leftNum && rightNumeric instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSNumber.of(((int) leftNum.value() >>> ((int) rightNum.value() & 0x1F)) & 0xFFFFFFFFL));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, leftNumeric);
        int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, rightNumeric);
        executionContext.virtualMachine.valueStack.push(JSNumber.of((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
    }

    private static void internalHandleStrictEq(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleStrictNeq(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = !JSTypeConversions.strictEquals(left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
    }

    private static void internalHandleSub(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().subtract(rightBigInt.value())));
            return;
        }
        JSNumber leftNumber = (JSNumber) pair.left();
        JSNumber rightNumber = (JSNumber) pair.right();
        executionContext.virtualMachine.valueStack.push(JSNumber.of(leftNumber.value() - rightNumber.value()));
    }

    private static void internalHandleTypeof(ExecutionContext executionContext) {
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        String type = JSTypeChecking.typeof(operand);
        executionContext.virtualMachine.valueStack.push(new JSString(type));
    }

    private static void internalHandleXor(ExecutionContext executionContext) {
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            executionContext.virtualMachine.valueStack.push(JSNumber.of((int) leftNum.value() ^ (int) rightNum.value()));
            return;
        }
        VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
        if (pair == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            return;
        }
        if (pair.bigInt()) {
            JSBigInt leftBigInt = (JSBigInt) pair.left();
            JSBigInt rightBigInt = (JSBigInt) pair.right();
            executionContext.virtualMachine.valueStack.push(new JSBigInt(leftBigInt.value().xor(rightBigInt.value())));
            return;
        }
        int result = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left()) ^ JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
        executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
    }

    private static void internalHandleYield(ExecutionContext executionContext) {
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
            return;
        }

        // At the target yield - check for RETURN/THROW resume records (replay mode)
        JSGeneratorState.ResumeRecord resumeRecord = executionContext.virtualMachine.generatorResumeIndex < executionContext.virtualMachine.generatorResumeRecords.size()
                ? executionContext.virtualMachine.generatorResumeRecords.get(executionContext.virtualMachine.generatorResumeIndex)
                : null;
        if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
            executionContext.virtualMachine.generatorResumeIndex++;
            executionContext.virtualMachine.valueStack.pop(); // Pop the yielded value
            // Trigger force return through finally handlers generatorForceReturn = true;
            executionContext.virtualMachine.generatorReturnValue = resumeRecord.value();
            executionContext.virtualMachine.pendingException = resumeRecord.value();
            executionContext.virtualMachine.context.setPendingException(resumeRecord.value());
            executionContext.virtualMachine.valueStack.push(resumeRecord.value());
            return;
        }

        // Pop the yielded value from stack
        JSValue value = executionContext.virtualMachine.valueStack.pop();

        // Create yield result to signal suspension.
        executionContext.virtualMachine.yieldResult = new YieldResult(YieldResult.Type.YIELD, value);

        // Push the value back so it can be returned
        executionContext.virtualMachine.valueStack.push(value);
    }

    private static void internalHandleYieldStar(ExecutionContext executionContext) {
        // yield* delegates to another iterator
        // Pop the iterable from the stack
        JSValue iterable = executionContext.virtualMachine.valueStack.pop();

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
        JSValue iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);
        if (!(iteratorMethod instanceof JSFunction iteratorFunc)) {
            throw new JSVirtualMachineException("Object is not iterable");
        }

        // Call Symbol.iterator to get the iterator
        JSValue iterator = iteratorFunc.call(executionContext.virtualMachine.context, iterable, VirtualMachine.EMPTY_ARGS);
        if (!(iterator instanceof JSObject iteratorObj)) {
            throw new JSVirtualMachineException("Iterator method must return an object");
        }

        // Check for RETURN/THROW resume (yield* delegation protocol per ES2024 27.5.3.3)
        // Following QuickJS js_generator_next: when resumed with GEN_MAGIC_RETURN or GEN_MAGIC_THROW,
        // the magic and value are passed to the yield* bytecode which calls the appropriate method.
        JSGeneratorState.ResumeRecord resumeRecord =
                executionContext.virtualMachine.generatorResumeIndex < executionContext.virtualMachine.generatorResumeRecords.size()
                        ? executionContext.virtualMachine.generatorResumeRecords.get(executionContext.virtualMachine.generatorResumeIndex)
                        : null;

        if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
            executionContext.virtualMachine.generatorResumeIndex++; // consume the record
            JSValue returnValue = resumeRecord.value();

            // Get "return" method from iterator
            JSValue returnMethodValue = iteratorObj.get(executionContext.virtualMachine.context, PropertyKey.RETURN);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                throw new JSVirtualMachineException(
                        executionContext.virtualMachine.context.getPendingException().toString(),
                        executionContext.virtualMachine.context.getPendingException());
            }
            boolean noReturnMethod = returnMethodValue.isNullOrUndefined();

            if (noReturnMethod) {
                // No return method - complete with the return value (don't yield)
                executionContext.virtualMachine.valueStack.push(returnValue);
                return;
            }

            if (!(returnMethodValue instanceof JSFunction returnFunc)) {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator return is not a function"));
            }

            // Call iterator.return(value)
            JSValue result = returnFunc.call(executionContext.virtualMachine.context, iteratorObj, new JSValue[]{returnValue});
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
            JSValue doneValue = ((JSObject) result).get(executionContext.virtualMachine.context, PropertyKey.DONE);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                throw new JSVirtualMachineException(
                        executionContext.virtualMachine.context.getPendingException().toString(),
                        executionContext.virtualMachine.context.getPendingException());
            }
            if (JSTypeConversions.toBoolean(doneValue).value()) {
                // Done - push value and complete (don't yield)
                JSValue value = ((JSObject) result).get(executionContext.virtualMachine.context, PropertyKey.VALUE);
                if (executionContext.virtualMachine.context.hasPendingException()) {
                    throw new JSVirtualMachineException(
                            executionContext.virtualMachine.context.getPendingException().toString(),
                            executionContext.virtualMachine.context.getPendingException());
                }
                executionContext.virtualMachine.valueStack.push(value);
                return;
            } else {
                // Not done - yield the result and continue delegation.
                executionContext.virtualMachine.yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result, iteratorObj);
                executionContext.virtualMachine.valueStack.push(result);
                return;
            }
        }

        if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
            executionContext.virtualMachine.generatorResumeIndex++; // consume the record
            JSValue throwValue = resumeRecord.value();

            // Get "throw" method from iterator
            JSValue throwMethodValue = iteratorObj.get(PropertyKey.THROW);
            boolean noThrowMethod = throwMethodValue.isNullOrUndefined();

            if (noThrowMethod) {
                // No throw method - close iterator and throw TypeError
                JSValue closeMethod = iteratorObj.get(PropertyKey.RETURN);
                if (closeMethod instanceof JSFunction closeFunc) {
                    closeFunc.call(executionContext.virtualMachine.context, iteratorObj, VirtualMachine.EMPTY_ARGS);
                }
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError(
                        "iterator does not have a throw method"));
            }

            if (!(throwMethodValue instanceof JSFunction throwFunc)) {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator throw is not a function"));
            }

            // Call iterator.throw(value)
            JSValue result = throwFunc.call(executionContext.virtualMachine.context, iteratorObj, new JSValue[]{throwValue});

            // Check result is an object
            if (!(result instanceof JSObject)) {
                throw new JSVirtualMachineException(executionContext.virtualMachine.context.throwTypeError("iterator must return an object"));
            }

            // Check done flag
            JSValue doneValue = ((JSObject) result).get(PropertyKey.DONE);
            if (JSTypeConversions.toBoolean(doneValue).value()) {
                // Done - push value and complete the yield* expression
                JSValue value = ((JSObject) result).get(PropertyKey.VALUE);
                executionContext.virtualMachine.valueStack.push(value);
                return;
            } else {
                // Not done - yield the result.
                executionContext.virtualMachine.yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result, iteratorObj);
                executionContext.virtualMachine.valueStack.push(result);
                return;
            }
        }

        // Default: NEXT protocol - call iterator.next()
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            throw new JSVirtualMachineException("Iterator must have a next method");
        }

        // Skip past previously-yielded values during generator replay.
        // Each yield* value counts as one yield, so we consume executionContext.virtualMachine.yieldSkipCount
        // values from the inner iterator to reach the correct position.
        while (executionContext.virtualMachine.yieldSkipCount > 0) {
            JSValue skipResult = nextFunc.call(executionContext.virtualMachine.context, iterator, VirtualMachine.EMPTY_ARGS);
            if (!(skipResult instanceof JSObject)) {
                throw new JSVirtualMachineException("Iterator result must be an object");
            }
            JSValue skipDone = ((JSObject) skipResult).get(PropertyKey.DONE);
            if (JSTypeConversions.toBoolean(skipDone).value()) {
                // Inner iterator exhausted during skip — yield* expression is done
                JSValue value = ((JSObject) skipResult).get(PropertyKey.VALUE);
                executionContext.virtualMachine.valueStack.push(value);
                return;
            }
            executionContext.virtualMachine.yieldSkipCount--;
        }

        JSValue result = nextFunc.call(executionContext.virtualMachine.context, iterator, VirtualMachine.EMPTY_ARGS);

        // The result should be an object (the iterator result)
        if (!(result instanceof JSObject)) {
            throw new JSVirtualMachineException("Iterator result must be an object");
        }

        // Check if the inner iterator is done
        JSValue doneValue = ((JSObject) result).get(PropertyKey.DONE);
        if (JSTypeConversions.toBoolean(doneValue).value()) {
            // Inner iterator done - yield* expression value is the final value
            JSValue value = ((JSObject) result).get(PropertyKey.VALUE);
            executionContext.virtualMachine.valueStack.push(value);
            // Don't set executionContext.virtualMachine.yieldResult - the yield* expression completes
            return;
        }

        // Set yield result to the raw iterator result object
        // This is what QuickJS does with *pdone = 2.
        executionContext.virtualMachine.yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result, iteratorObj);

        // Push the result back on the stack
        executionContext.virtualMachine.valueStack.push(result);
    }
}
