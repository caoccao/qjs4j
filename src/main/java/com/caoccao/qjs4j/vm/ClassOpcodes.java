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
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

/** Class heritage, method definition and derived-constructor initialization. */
final class ClassOpcodes {
    private ClassOpcodes() {
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
        if (hasHeritage && !setHeritage(executionContext, constructorFunction, prototypeObject, superClassValue)) {
            stack[sp++] = JSUndefined.INSTANCE;
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
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
                executionContext.raiseTypeError("Cannot redefine property: " + methodKey.toPropertyString());
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

            JSProxy privateProxy = isProxyPrivateTarget ? (JSProxy) object : null;
            PropertyDescriptor descriptor;
            if (methodKind == 0) {
                if (isPrivateSymbolKey && (privateProxy == null
                        ? object.hasOwnProperty(key)
                        : privateProxy.hasOwnPrivatePropertyDirect(key))) {
                    executionContext.raiseTypeError("Cannot initialize the same private elements twice on an object");
                    return;
                }
                PropertyDescriptor.DataState state;
                if (nonWritable) {
                    state = enumerable
                            ? PropertyDescriptor.DataState.EnumerableConfigurable
                            : PropertyDescriptor.DataState.Configurable;
                } else {
                    state = enumerable
                            ? PropertyDescriptor.DataState.All
                            : PropertyDescriptor.DataState.ConfigurableWritable;
                }
                descriptor = PropertyDescriptor.dataDescriptor(methodValue, state);
            } else if (methodKind == 1 || methodKind == 2) {
                boolean isGetter = methodKind == 1;
                PropertyDescriptor previous = privateProxy == null
                        ? object.getOwnPropertyDescriptor(key)
                        : privateProxy.getOwnPrivatePropertyDescriptorDirect(key);
                if (isPrivateSymbolKey && previous != null && (previous.isDataDescriptor()
                        || (isGetter ? previous.getGetter() : previous.getSetter()) != null)) {
                    executionContext.raiseTypeError("Cannot initialize the same private elements twice on an object");
                    return;
                }
                JSFunction method = methodValue instanceof JSFunction function ? function : null;
                JSFunction getter = isGetter ? method : previous != null ? previous.getGetter() : null;
                JSFunction setter = !isGetter ? method : previous != null ? previous.getSetter() : null;
                descriptor = PropertyDescriptor.accessorDescriptor(getter, setter,
                        enumerable
                                ? PropertyDescriptor.AccessorState.All
                                : PropertyDescriptor.AccessorState.Configurable);
            } else {
                throw new JSVirtualMachineException("DEFINE_METHOD_COMPUTED: unsupported method flags " + methodFlags);
            }
            if (privateProxy != null) {
                privateProxy.definePrivatePropertyDirect(key, descriptor);
            } else if (!object.defineProperty(key, descriptor)) {
                String keyName = key != null ? key.toPropertyString() : "property";
                executionContext.raiseTypeError("Cannot redefine property: " + keyName);
                return;
            }
        }

        executionContext.finishOpcode(pc + op.getSize(), sp);
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
            executionContext.raiseTypeError("class constructors must be invoked with 'new'");
            return;
        }

        // Per ES spec BindThisValue: if thisBindingStatus is "initialized", throw ReferenceError.
        // Arrow functions share the this-binding with the enclosing derived constructor.
        // Check if this was already initialized by a previous super() call.
        JSFunction currentFunction = executionContext.virtualMachine.currentFrame.getFunction();
        if (hasInitializedThis(executionContext.frame, currentFunction)) {
            executionContext.raiseReferenceError("Super constructor may only be called once");
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }

        // Explicit super(...): APPLY constructor mode left the initialized this value on stack.
        if (executionContext.virtualMachine.valueStack.getStackTop() > executionContext.virtualMachine.currentFrame
                .getStackBase()) {
            JSValue thisValue = executionContext.virtualMachine.valueStack.pop();
            if (!(thisValue instanceof JSObject jsObject)) {
                executionContext.raiseTypeError("super() returned non-object");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            initializeDerivedThis(executionContext.virtualMachine.currentFrame, jsObject);
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
                        initializeDerivedThis(callerFrame, jsObject);
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
                executionContext.raiseTypeError("parent class must be constructor");
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
                executionContext.raiseTypeError("parent class must be constructor");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            if (executionContext.capturePendingException()) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (!(superResult instanceof JSObject superObject)) {
                executionContext.raiseTypeError("super() returned non-object");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                initializeDerivedThis(executionContext.virtualMachine.currentFrame, superObject);
                executionContext.virtualMachine.valueStack.push(superObject);
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
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

    private static boolean hasInitializedThis(StackFrame frame, JSFunction function) {
        if (frame.getThisArg() instanceof JSObject) {
            return true;
        }
        if (!(function instanceof JSBytecodeFunction arrow) || !arrow.isArrow()) {
            return false;
        }
        VarRef sharedThis = frame.getDerivedThisRef();
        return arrow.getCapturedThisArg() instanceof JSObject
                || sharedThis != null && sharedThis.get() instanceof JSObject;
    }

    private static void initializeDerivedThis(StackFrame frame, JSObject value) {
        frame.setThisArg(value);
        VarRef reference = frame.getDerivedThisRef();
        if (reference != null) {
            reference.set(value);
        }
    }

    private static boolean setHeritage(ExecutionContext ctx, JSFunction constructor, JSObject prototype,
            JSValue parent) {
        if (parent instanceof JSNull) {
            prototype.setPrototype(null);
            return true;
        }
        VirtualMachine vm = ctx.virtualMachine;
        if (!(parent instanceof JSObject parentObject) || !JSTypeChecking.isConstructor(parent)) {
            vm.context.throwTypeError("parent class must be constructor");
            vm.pendingException = vm.context.getPendingException();
            return false;
        }
        JSValue parentPrototype = parentObject.get(PropertyKey.PROTOTYPE);
        if (ctx.capturePendingException()) {
            return false;
        }
        if (parentPrototype instanceof JSObject object) {
            prototype.setPrototype(object);
        } else if (parentPrototype instanceof JSNull) {
            prototype.setPrototype(null);
        } else {
            vm.context.throwTypeError("parent class prototype is not an object or null");
            vm.pendingException = vm.context.getPendingException();
            return false;
        }
        constructor.setPrototype(parentObject);
        return true;
    }
}
