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
import com.caoccao.qjs4j.core.error.JSError;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

/** Bytecode operations on indexed, named, private, and super properties. */
final class PropertyOpcodes {
    private PropertyOpcodes() {
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

    private static void getIndexedValue(Opcode op, ExecutionContext executionContext, boolean preserveReceiver) {
        VirtualMachine vm = executionContext.virtualMachine;
        JSStackValue[] stack = vm.valueStack.stack;
        int sp = executionContext.sp;
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[sp - 1];

        int resultIndex = preserveReceiver ? sp : sp - 1;
        if (objectValue instanceof JSString string && indexValue instanceof JSNumber number) {
            int index = (int) number.value();
            if (index == number.value() && index >= 0 && index < string.value().length()) {
                stack[resultIndex] = new JSString(String.valueOf(string.value().charAt(index)));
                executionContext.finishOpcode(op, resultIndex + 1);
                return;
            }
        }

        if (!preserveReceiver) {
            vm.valueStack.stackTop = resultIndex;
        }
        JSObject object = vm.toObject(objectValue);
        JSValue result = JSUndefined.INSTANCE;
        if (object == null) {
            executionContext
                    .setErrorAsPending(createCannotReadPropertiesTypeError(vm.context, objectValue, indexValue));
        } else {
            try {
                PropertyKey key = PropertyKey.fromValue(vm.context, indexValue);
                // Mapped arguments override get(key). Primitive accessors receive the unboxed receiver.
                JSValue value = objectValue instanceof JSObject ? object.get(key) : object.get(key, objectValue);
                if (!executionContext.capturePendingException()) {
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
        executionContext.finishOpcode(op, resultIndex + 1);
    }

    private static JSValue getNamedProperty(ExecutionContext execution, JSValue value, PropertyKey key, String name,
            JSValue receiver) {
        VirtualMachine vm = execution.virtualMachine;
        JSObject target = vm.toObject(value);
        if (target == null) {
            vm.pendingException = vm.context.throwTypeError("Cannot read properties of "
                    + (value instanceof JSNull ? "null" : "undefined") + " (reading '" + name + "')");
            return JSUndefined.INSTANCE;
        }
        // GET_LENGTH uses the object's own get(key), including exotic overrides.
        JSValue result = receiver == null ? target.get(key) : target.get(key, receiver);
        return execution.capturePendingException() ? JSUndefined.INSTANCE : result;
    }

    /**
     * The {@code length} of a primitive string. It is an own, non-writable, non-configurable property of the String
     * wrapper, so nothing on the prototype chain can shadow it: read it without allocating the wrapper, and without
     * flattening a rope.
     */
    private static JSNumber getStringLength(JSString string) {
        return JSNumber.of(string.length());
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

        executionContext.finishOpcode(op, sp);
    }

    static void handleDefineField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        int fieldNameAtom = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtom(fieldNameAtom);
        JSValue value = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        if (objectValue instanceof JSObject object) {
            PropertyKey fieldKey = executionContext.bytecode.getCachedPropertyKey(fieldNameAtom);
            boolean defineSucceeded = object.defineProperty(fieldKey,
                    PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.All));
            if (!defineSucceeded) {
                executionContext.raiseTypeError("Cannot redefine property: " + fieldKey.toPropertyString());
                return;
            }
        }

        stack[sp++] = objectValue;
        executionContext.finishOpcode(op, sp);
    }

    static void handleDefinePrivateField(Opcode op, ExecutionContext executionContext) {
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
                executionContext.finishOpcode(op, sp);
                return;
            }
            boolean hasPrivateProperty = object instanceof JSProxy proxy
                    ? proxy.hasOwnPrivatePropertyDirect(privateKey)
                    : object.hasOwnProperty(privateKey);
            if (hasPrivateProperty) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .throwTypeError("Cannot initialize the same private elements twice on an object");
                stack[sp++] = objectValue;
                executionContext.finishOpcode(op, sp);
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
        executionContext.finishOpcode(op, sp);
    }

    static void handleDelete(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue property = operandStack.pop();
        JSValue object = operandStack.pop();
        JSObject targetObject = executionContext.virtualMachine.toObject(object);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("Cannot convert undefined or null to object");
            operandStack.push(JSBoolean.FALSE);
            executionContext.finishStackOpcode(op);
            return;
        }
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, property);
        boolean result = targetObject.delete(key);
        if (executionContext.virtualMachine.context.hasPendingException()) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .getPendingException();
        }
        operandStack.push(JSBoolean.valueOf(result));
        executionContext.finishStackOpcode(op);
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
        executionContext.finishWithResult(op, sp,
                executionContext.capturePendingException() ? JSUndefined.INSTANCE : result);
    }

    static void handleGetField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtom(atomIndex);
        // GET_FIELD2 retains the receiver below the property value.
        JSValue objectValue = op == Opcode.GET_FIELD2 ? executionContext.peek(0) : executionContext.pop();
        if (objectValue instanceof JSString string && "length".equals(fieldName)) {
            JSNumber stringLength = getStringLength(string);
            executionContext.push(stringLength);
            executionContext.pc = pc + op.getSize();
            return;
        }

        executionContext.push(getNamedProperty(executionContext, objectValue,
                executionContext.bytecode.getCachedPropertyKey(atomIndex), fieldName, objectValue));
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
        executionContext.push(getNamedProperty(executionContext, objectValue, PropertyKey.LENGTH, "length", null));
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetPrivateField(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];
        stack[sp++] = accessPrivateField(executionContext.virtualMachine, objectValue, privateSymbolValue,
                JSUndefined.INSTANCE, false);
        executionContext.finishOpcode(op, sp);
    }

    static void handleGetSuper(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue objectValue = (JSValue) stack[sp - 1];
        JSObject object = executionContext.virtualMachine.toObject(objectValue);
        if (object == null) {
            executionContext.capturePendingException();
            stack[sp - 1] = JSUndefined.INSTANCE;
        } else {
            JSObject prototypeObject = object.getPrototype();
            stack[sp - 1] = prototypeObject != null ? prototypeObject : JSNull.INSTANCE;
        }
        executionContext.advance(op);
    }

    static void handleGetSuperValue(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue keyValue = (JSValue) stack[--sp];
        JSValue superObjectValue = (JSValue) stack[--sp];
        JSValue receiverValue = (JSValue) stack[--sp];

        if (!(superObjectValue instanceof JSObject superObject)) {
            executionContext.raiseTypeError("super object expected");
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.finishOpcode(op, sp);
            return;
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, keyValue);
        // Pass receiver directly without boxing. Per ES spec, super property access
        // should not auto-box primitive receivers. The getter receives the original
        // primitive as 'this' (e.g., super.prop.call(3) should see this === 3).
        JSValue result = superObject.getWithReceiver(key, receiverValue);

        executionContext.finishWithResult(op, sp,
                executionContext.capturePendingException() ? JSUndefined.INSTANCE : result);
    }

    static void handlePrivateIn(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue privateField = operandStack.pop();
        JSValue object = operandStack.pop();
        if (!(object instanceof JSObject jsObj)) {
            executionContext.raiseTypeError("invalid 'in' operand");
            operandStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(op);
            return;
        }
        boolean result = false;
        if (privateField instanceof JSSymbol symbol) {
            result = jsObj.has(PropertyKey.fromSymbol(symbol));
        }
        operandStack.push(JSBoolean.valueOf(result));
        executionContext.finishStackOpcode(op);
    }

    static void handlePrivateSymbol(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        String fieldName = executionContext.bytecode.readAtom(pc + 1);
        executionContext.push(new JSSymbol(fieldName));
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutArrayEl(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue assignedValue = (JSValue) stack[--sp];
        JSValue indexValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];

        putProperty(executionContext, objectValue, null, null, indexValue, assignedValue);
        stack[sp++] = assignedValue;
        executionContext.finishOpcode(op, sp);
    }

    static void handlePutField(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        String fieldName = executionContext.bytecode.getAtom(atomIndex);
        JSValue objectValue = executionContext.pop();
        JSValue fieldValue = executionContext.peek(0);
        PropertyKey propertyKey = executionContext.bytecode.getCachedPropertyKey(atomIndex);

        putProperty(executionContext, objectValue, propertyKey, fieldName, null, fieldValue);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutPrivateField(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue privateSymbolValue = (JSValue) stack[--sp];
        JSValue value = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];
        stack[sp++] = accessPrivateField(executionContext.virtualMachine, objectValue, privateSymbolValue, value, true);
        executionContext.finishOpcode(op, sp);
    }

    // Stack: this obj key val -> val (QuickJS order: value at top)
    static void handlePutSuperValue(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue assignedValue = (JSValue) stack[--sp];
        JSValue keyValue = (JSValue) stack[--sp];
        JSValue superObjectValue = (JSValue) stack[--sp];
        JSValue receiverValue = (JSValue) stack[--sp];

        if (!(superObjectValue instanceof JSObject superObject)) {
            executionContext.raiseTypeError("super object expected");
            stack[sp++] = assignedValue;
            executionContext.finishOpcode(op, sp);
            return;
        }

        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, keyValue);
        VirtualMachine vm = executionContext.virtualMachine;
        JSObject receiver = receiverValue instanceof JSObject object ? object : vm.toObject(receiverValue);
        if (receiver == null) {
            setObjectProperty(vm, superObject, key, assignedValue);
        } else if (superObject instanceof JSProxy proxy) {
            proxy.proxySet(vm.context, key, assignedValue, receiver);
        } else {
            superObject.setWithReceiverAndException(key, assignedValue, receiver);
        }

        executionContext.virtualMachine.capturePendingExceptionFromContext(superObject.getContext());
        stack[sp++] = assignedValue;
        executionContext.finishOpcode(op, sp);
    }

    private static void putPrimitiveProperty(ExecutionContext execution, JSValue receiver, PropertyKey key,
            String fieldName, JSValue index, JSValue value) {
        VirtualMachine vm = execution.virtualMachine;
        if (receiver instanceof JSNull || receiver instanceof JSUndefined) {
            Object name = fieldName != null ? fieldName : PropertyKey.fromValue(vm.context, index);
            vm.context.throwTypeError(
                    "cannot set property '" + name + "' of " + (receiver instanceof JSNull ? "null" : "undefined"));
            vm.pendingException = vm.context.takePendingException();
            return;
        }
        JSObject target = vm.toObject(receiver);
        if (target == null) {
            return;
        }
        try {
            if (key == null) {
                key = PropertyKey.fromValue(vm.context, index);
            }
            // Inherited setters receive the original primitive.
            boolean success = target.setWithResult(key, value, receiver);
            if (!execution.capturePendingException() && !success && vm.context.isStrictMode()) {
                vm.context.throwTypeError("Cannot create property '" + (fieldName != null ? fieldName : key) + "' on "
                        + JSTypeChecking.typeof(receiver) + " '" + receiver + "'");
                vm.pendingException = vm.context.takePendingException();
            }
        } catch (JSVirtualMachineException e) {
            vm.captureVMException(e);
        }
    }

    /** The indexed form coerces its key after boxing the receiver. Named stores already have a cached key. */
    private static void putProperty(ExecutionContext execution, JSValue receiver, PropertyKey key, String fieldName,
            JSValue index, JSValue value) {
        VirtualMachine vm = execution.virtualMachine;
        if (receiver instanceof JSObject object) {
            try {
                if (key == null) {
                    key = PropertyKey.fromValue(vm.context, index);
                }
                setObjectProperty(vm, object, key, value);
                vm.capturePendingExceptionFromContext(object.getContext());
            } catch (JSVirtualMachineException e) {
                vm.captureVMException(e);
            }
            return;
        }
        putPrimitiveProperty(execution, receiver, key, fieldName, index, value);
    }

    private static void setObjectProperty(VirtualMachine vm, JSObject target, PropertyKey key, JSValue value) {
        if (target instanceof JSProxy proxy) {
            proxy.proxySet(vm.context, key, value);
        } else {
            target.set(key, value);
        }
    }
}
