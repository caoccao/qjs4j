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

import java.util.IdentityHashMap;
import java.util.Set;

/** Bytecode access to frame locals, arguments, captured variables, and named bindings. */
final class BindingOpcodes {
    private static final int MAX_CLOSURE_SCAN_FRAME_COUNT = 65_536;

    private BindingOpcodes() {
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
        JSContext context = executionContext.virtualMachine.context;
        if (variableName == null || !context.hasEvalOverlayFrames() || context.hasEvalOverlayBinding(variableName)
                || executionContext.frame == null
                || !(executionContext.frame.getFunction() instanceof JSBytecodeFunction function)
                || !function.isEvalDynamicScopeLookupEnabled()) {
            return null;
        }
        StackFrame caller = executionContext.frame.getCaller();
        if (caller == null || !(caller.getFunction() instanceof JSBytecodeFunction callerFunction)) {
            return null;
        }
        EvalScopedLocalBinding local = findFrameLocal(caller, callerFunction, variableName);
        if (local != null || !callerFunction.isEvalDynamicScopeLookupEnabled()) {
            return local;
        }
        StackFrame frame = callerFunction.getEvalDynamicScopeFrame();
        if (frame == null) {
            return null;
        }
        IdentityHashMap<StackFrame, Boolean> visited = new IdentityHashMap<>();
        visited.put(caller, Boolean.TRUE);
        while (frame != null && visited.put(frame, Boolean.TRUE) == null) {
            if (frame.getFunction() instanceof JSBytecodeFunction outer) {
                local = findFrameLocal(frame, outer, variableName);
                if (local != null && local.value() != VirtualMachine.UNINITIALIZED_MARKER) {
                    return local;
                }
            }
            frame = frame.getCaller();
        }
        return null;
    }

    private static EvalScopedLocalBinding findFrameLocal(StackFrame frame, JSBytecodeFunction function, String name) {
        String[] names = function.getBytecode().getLocalVarNames();
        JSValue[] locals = frame.getLocals();
        if (names != null && locals != null) {
            for (int i = 0; i < names.length && i < locals.length; i++) {
                if (name.equals(names[i])) {
                    return new EvalScopedLocalBinding(frame, i, locals[i]);
                }
            }
        }
        return null;
    }

    static void handleCloseLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.operandU16();
        executionContext.frame.closeLocal(localIndex);
        executionContext.pc = pc + op.getSize();
    }

    static void handleDeleteVar(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        String variableName = executionContext.bytecode.readAtom(pc + 1);
        boolean deleted;
        JSContext context = executionContext.virtualMachine.context;
        StackFrame dynamicBindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
        if (dynamicBindingFrame != null) {
            deleted = internalDeleteDynamicVarBinding(dynamicBindingFrame, variableName);
            if (deleted && context.hasEvalOverlayFrames() && context.hasEvalOverlayBinding(variableName)) {
                JSObject globalObject = context.getGlobalObject();
                PropertyKey variableKey = PropertyKey.fromString(variableName);
                globalObject.delete(variableKey);
                executionContext.capturePendingException();
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

    static void handleGetArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.operandU16();
        executionContext.push(executionContext.virtualMachine.getArgumentValue(argumentIndex));
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetArgShort(Opcode op, ExecutionContext executionContext) {
        int argumentIndex = op.getCode() - Opcode.GET_ARG0.getCode();
        executionContext.push(executionContext.virtualMachine.getArgumentValue(argumentIndex));
        executionContext.advance(op);
    }

    static void handleGetLoc(Opcode op, ExecutionContext executionContext) {
        pushLocal(op, executionContext, executionContext.operandU16());
    }

    static void handleGetLoc8(Opcode op, ExecutionContext executionContext) {
        pushLocal(op, executionContext, executionContext.instructions[executionContext.pc + 1] & 0xFF);
    }

    static void handleGetLocCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.operandU16();
        JSValue localValue = executionContext.frame.getLocals()[localIndex];
        if (executionContext.virtualMachine.isUninitialized(localValue)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError(
                    VirtualMachine.localVariableName(executionContext.frame, localIndex));
        }
        JSStackValue[] stack = executionContext.stack();
        int sp = executionContext.sp;
        stack[sp++] = localValue;
        executionContext.finishOpcode(op, sp);
    }

    static void handleGetLocShort(Opcode op, ExecutionContext executionContext) {
        int index = op.getCode() - Opcode.GET_LOC0.getCode();
        pushLocal(op, executionContext, index);
    }

    static void handleGetRefValue(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.stack();
        JSValue property = (JSValue) stack[sp - 1];
        JSValue receiver = (JSValue) stack[sp - 2];
        int nextPc = executionContext.pc + op.getSize();
        stack[sp] = readReference(executionContext, receiver, property);
        executionContext.finishOpcode(nextPc, sp + 1);
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
        int atomIndex = executionContext.operandI32();
        String[] atoms = executionContext.bytecode.getAtoms();
        JSValue value;
        if (atomIndex < 0 || atomIndex >= atoms.length) {
            value = readVarRefValue(executionContext, executionContext.bytecode.readU16(pc + 1));
        } else {
            value = readVariable(executionContext, atoms[atomIndex],
                    executionContext.bytecode.getCachedPropertyKey(atomIndex), allowMissing);
        }
        executionContext.push(value);
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.operandU16();
        executionContext.push(readVarRefValue(executionContext, varRefIndex));
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetVarRefCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.operandU16();
        JSValue value = readVarRefValue(executionContext, varRefIndex);
        if (executionContext.virtualMachine.isUninitialized(value)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        executionContext.push(value);
        executionContext.pc = pc + op.getSize();
    }

    static void handleGetVarRefShort(Opcode op, ExecutionContext executionContext) {
        int varRefIndex = op.getCode() - Opcode.GET_VAR_REF0.getCode();
        executionContext.push(readVarRefValue(executionContext, varRefIndex));
        executionContext.advance(op);
    }

    static void handleGetVarUndef(Opcode op, ExecutionContext executionContext) {
        handleGetVar(op, executionContext, true);
    }

    static void handleMakeScopedRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.operandI32();
        int refIndex = executionContext.bytecode.readU16(pc + 5);
        String atomName = executionContext.bytecode.getAtom(atomIndex);
        JSObject referenceObject = executionContext.virtualMachine.createReferenceObject(op, refIndex, atomName);
        executionContext.push(referenceObject);
        executionContext.push(new JSString(atomName));
        executionContext.pc = pc + op.getSize();
    }

    static void handleMakeVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        String atomName = executionContext.bytecode.readAtom(pc + 1);
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

    static void handlePutArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.operandU16();
        JSValue argumentValue = executionContext.pop();
        executionContext.virtualMachine.setArgumentValue(argumentIndex, argumentValue);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutArgShort(Opcode op, ExecutionContext executionContext) {
        int argumentIndex = op.getCode() - Opcode.PUT_ARG0.getCode();
        JSValue argumentValue = executionContext.pop();
        executionContext.virtualMachine.setArgumentValue(argumentIndex, argumentValue);
        executionContext.advance(op);
    }

    static void handlePutLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.operandU16();
        executionContext.locals[localIndex] = executionContext.pop();
        executionContext.advance(op);
    }

    static void handlePutLoc8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        executionContext.locals[localIndex] = executionContext.pop();
        executionContext.advance(op);
    }

    static void handlePutLocCheck(Opcode op, ExecutionContext executionContext) {
        writeCheckedLocal(op, executionContext, true);
    }

    static void handlePutLocCheckInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.operandU16();
        JSValue[] localValues = executionContext.frame.getLocals();
        if (!executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            executionContext.raiseReferenceError("'this' can be initialized only once");
            return;
        }
        localValues[localIndex] = executionContext.pop();
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutLocShort(Opcode op, ExecutionContext executionContext) {
        int index = op.getCode() - Opcode.PUT_LOC0.getCode();
        executionContext.locals[index] = executionContext.pop();
        executionContext.advance(op);
    }

    static void handlePutRefValue(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack();
        int sp = executionContext.sp;
        JSValue setValue = (JSValue) stack[--sp];
        JSValue propertyValue = (JSValue) stack[--sp];
        JSValue objectValue = (JSValue) stack[--sp];
        JSContext context = executionContext.virtualMachine.context;
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, propertyValue);
        String variableName = key != null && key.isString() ? key.asString() : null;

        if (variableName != null && objectValue.isUndefined()) {
            if (writeDynamicReference(executionContext, variableName, key, setValue)) {
                executionContext.finishOpcode(op, sp);
                return;
            }
        }

        if (objectValue.isUndefined()) {
            if (executionContext.virtualMachine.context.isStrictMode()) {
                undefinedReference(executionContext.virtualMachine, key);
                executionContext.finishOpcode(op, sp);
                return;
            }
            objectValue = executionContext.virtualMachine.context.getGlobalObject();
        }

        JSObject targetObject = executionContext.virtualMachine.toObject(objectValue);
        if (targetObject == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .throwTypeError("value has no property");
            executionContext.finishOpcode(op, sp);
            return;
        }

        if (targetObject == context.getGlobalObject() && variableName != null) {
            if (writeDynamicReference(executionContext, variableName, key, setValue)) {
                executionContext.finishOpcode(op, sp);
                return;
            }
            if (variableName != null && context.hasGlobalLexicalBinding(variableName)) {
                if (!context.isGlobalLexicalBindingInitialized(variableName)) {
                    executionContext.virtualMachine.pendingException = context
                            .throwReferenceError("Cannot access '" + variableName + "' before initialization");
                    executionContext.finishOpcode(op, sp);
                    return;
                }
                if (context.hasGlobalConstDeclaration(variableName)) {
                    executionContext.virtualMachine.pendingException = context
                            .throwTypeError("Assignment to constant variable.");
                    executionContext.finishOpcode(op, sp);
                    return;
                }
                context.writeGlobalLexicalBinding(variableName, setValue);
                executionContext.finishOpcode(op, sp);
                return;
            }
        }

        if (!targetObject.has(key) && executionContext.virtualMachine.context.isStrictMode()) {
            undefinedReference(executionContext.virtualMachine, key);
            executionContext.finishOpcode(op, sp);
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
        executionContext.finishOpcode(op, sp);
    }

    static void handlePutVar(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.operandI32();
        String[] atomPool = executionContext.bytecode.getAtoms();
        if (atomPool.length == 0 || atomIndex < 0 || atomIndex >= atomPool.length) {
            handlePutVarRef(op, executionContext);
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
                executionContext.capturePendingException();
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
            executionContext.capturePendingException();
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
            executionContext.capturePendingException();
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
        executionContext.capturePendingException();
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.operandU16();
        JSValue value = executionContext.pop();
        executionContext.frame.setVarRef(varRefIndex, value);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.operandU16();
        JSValue value = executionContext.pop();
        writeVarRefValue(executionContext, varRefIndex, value);
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRefCheck(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.operandU16();
        JSValue currentValue = readVarRefValue(executionContext, varRefIndex);
        if (executionContext.virtualMachine.isUninitialized(currentValue)) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError();
        }
        writeVarRefValue(executionContext, varRefIndex, executionContext.pop());
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRefCheckInit(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.operandU16();
        JSValue currentValue = readVarRefValue(executionContext, varRefIndex);
        if (!executionContext.virtualMachine.isUninitialized(currentValue)) {
            executionContext.raiseReferenceError("variable is already initialized");
            return;
        }
        writeVarRefValue(executionContext, varRefIndex, executionContext.pop());
        executionContext.pc = pc + op.getSize();
    }

    static void handlePutVarRefShort(Opcode op, ExecutionContext executionContext) {
        int varRefIndex = op.getCode() - Opcode.PUT_VAR_REF0.getCode();
        JSValue value = executionContext.pop();
        writeVarRefValue(executionContext, varRefIndex, value);
        executionContext.advance(op);
    }

    static void handleSetArg(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentIndex = executionContext.operandU16();
        executionContext.virtualMachine.setArgumentValue(argumentIndex, executionContext.peek(0));
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetArgShort(Opcode op, ExecutionContext executionContext) {
        int argumentIndex = op.getCode() - Opcode.SET_ARG0.getCode();
        executionContext.virtualMachine.setArgumentValue(argumentIndex, executionContext.peek(0));
        executionContext.advance(op);
    }

    static void handleSetLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.operandU16();
        executionContext.locals[localIndex] = executionContext.peek(0);
        executionContext.advance(op);
    }

    static void handleSetLoc8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        executionContext.locals[localIndex] = executionContext.peek(0);
        executionContext.advance(op);
    }

    static void handleSetLocCheck(Opcode op, ExecutionContext executionContext) {
        writeCheckedLocal(op, executionContext, false);
    }

    static void handleSetLocShort(Opcode op, ExecutionContext executionContext) {
        int index = op.getCode() - Opcode.SET_LOC0.getCode();
        executionContext.locals[index] = executionContext.peek(0);
        executionContext.advance(op);
    }

    static void handleSetLocUninitialized(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.operandU16();
        // Entering the scope that declares this binding again — a loop body's next iteration — starts a new
        // binding; closures from the previous entry keep the old one.
        executionContext.frame.detachLocalVarRef(localIndex);
        executionContext.frame.getLocals()[localIndex] = VirtualMachine.UNINITIALIZED_MARKER;
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetVarRef(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int varRefIndex = executionContext.operandU16();
        executionContext.frame.setVarRef(varRefIndex, executionContext.peek(0));
        executionContext.pc = pc + op.getSize();
    }

    static void handleSetVarRefShort(Opcode op, ExecutionContext executionContext) {
        int varRefIndex = op.getCode() - Opcode.SET_VAR_REF0.getCode();
        executionContext.frame.setVarRef(varRefIndex, executionContext.peek(0));
        executionContext.advance(op);
    }

    private static boolean internalDeleteDynamicVarBinding(StackFrame stackFrame, String variableName) {
        if (stackFrame.hasDynamicVarBindingAlias(variableName)) {
            return false;
        }
        return stackFrame.removeDynamicVarBinding(variableName);
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

    private static void pushLocal(Opcode op, ExecutionContext executionContext, int index) {
        JSValue value = executionContext.locals[index];
        executionContext.stack()[executionContext.sp++] = value;
        executionContext.advance(op);
    }

    private static JSValue readNamedBindingValue(ExecutionContext executionContext, String variableName,
            JSValue value) {
        return value == VirtualMachine.UNINITIALIZED_MARKER
                ? uninitializedReference(executionContext.virtualMachine, variableName)
                : value;
    }

    private static JSValue readReference(ExecutionContext executionContext, JSValue receiver, JSValue property) {
        VirtualMachine vm = executionContext.virtualMachine;
        JSContext context = vm.context;
        PropertyKey key = PropertyKey.fromValue(context, property);
        String name = key != null && key.isString() ? key.asString() : null;
        boolean unresolved = receiver.isUndefined();
        JSObject object = unresolved ? null : vm.toObject(receiver);
        if (!unresolved && object == null) {
            vm.pendingException = context.throwTypeError("value has no property");
            return JSUndefined.INSTANCE;
        }
        if (name != null && (unresolved || object == context.getGlobalObject())) {
            StackFrame frame = findDynamicVarBindingFrame(executionContext, name);
            if (frame != null) {
                JSValue value = frame.getDynamicVarBinding(name);
                return value != null ? value : JSUndefined.INSTANCE;
            }
            EvalScopedLocalBinding local = findEvalScopedLocalBinding(executionContext, name);
            if (local != null) {
                if (local.value() == VirtualMachine.UNINITIALIZED_MARKER) {
                    return uninitializedReference(vm, name);
                }
                return local.value() != null ? local.value() : JSUndefined.INSTANCE;
            }
            if (!unresolved && context.hasGlobalLexicalBinding(name)) {
                return context.isGlobalLexicalBindingInitialized(name)
                        ? context.readGlobalLexicalBinding(name)
                        : uninitializedReference(vm, name);
            }
        }
        if (unresolved || !object.has(key)) {
            undefinedReference(vm, key);
            return JSUndefined.INSTANCE;
        }
        return object.get(key);
    }

    private static JSValue readVariable(ExecutionContext executionContext, String variableName, PropertyKey propertyKey,
            boolean allowMissing) {
        StackFrame dynamicBindingFrame = findDynamicVarBindingFrame(executionContext, variableName);
        if (dynamicBindingFrame != null) {
            JSValue variableValue = dynamicBindingFrame.getDynamicVarBinding(variableName);
            return variableValue != null ? variableValue : JSUndefined.INSTANCE;
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
                return readNamedBindingValue(executionContext, variableName, closureVarRef.get());
            }
        }
        JSContext context = executionContext.virtualMachine.context;
        JSObject globalObject = context.getGlobalObject();
        JSEvalOverlayBinding overlayBinding = resolveVisibleEvalOverlayBinding(executionContext, variableName);
        if (overlayBinding != null) {
            JSValue overlayValue = overlayBinding.get();
            if (overlayValue != null) {
                return overlayValue;
            }
        }
        EvalScopedLocalBinding localBinding = findEvalScopedLocalBinding(executionContext, variableName);
        if (localBinding != null) {
            JSValue localValue = localBinding.value() != null ? localBinding.value() : JSUndefined.INSTANCE;
            return readNamedBindingValue(executionContext, variableName, localValue);
        }
        JSModuleEnvironment moduleEnvironment = executionContext.frame.getFunction().getModuleEnvironment();
        if (moduleEnvironment != null && moduleEnvironment.has(variableName)) {
            return moduleEnvironment.get(variableName);
        }
        if (context.hasGlobalLexicalBinding(variableName)) {
            return context.isGlobalLexicalBindingInitialized(variableName)
                    ? context.readGlobalLexicalBinding(variableName)
                    : uninitializedReference(executionContext.virtualMachine, variableName);
        }
        JSValue variableValue = globalObject.get(propertyKey);
        boolean hasProperty = !(variableValue instanceof JSUndefined) || globalObject.has(propertyKey);
        if (!hasProperty && !shouldScanClosureVarRefsForGetVar(executionContext)) {
            VarRef closureVarRef = findClosureVarRef(executionContext, variableName);
            if (closureVarRef != null) {
                return readNamedBindingValue(executionContext, variableName, closureVarRef.get());
            }
        }
        if (!hasProperty && !allowMissing) {
            executionContext.virtualMachine.pendingException = context
                    .throwReferenceError(variableName + " is not defined");
            return JSUndefined.INSTANCE;
        }
        return variableValue;
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

    private static void undefinedReference(VirtualMachine vm, PropertyKey key) {
        String name = key != null ? key.toPropertyString() : "variable";
        vm.pendingException = vm.context.throwReferenceError(name + " is not defined");
    }

    private static JSValue uninitializedReference(VirtualMachine vm, String name) {
        vm.pendingException = vm.context.throwReferenceError("Cannot access '" + name + "' before initialization");
        return JSUndefined.INSTANCE;
    }

    private static void writeCheckedLocal(Opcode op, ExecutionContext executionContext, boolean pop) {
        int pc = executionContext.pc;
        int localIndex = executionContext.operandU16();
        JSValue[] localValues = executionContext.frame.getLocals();
        if (executionContext.virtualMachine.isUninitialized(localValues[localIndex])) {
            executionContext.virtualMachine.throwVariableUninitializedReferenceError(
                    VirtualMachine.localVariableName(executionContext.frame, localIndex));
        }
        localValues[localIndex] = pop ? executionContext.pop() : executionContext.peek(0);
        executionContext.pc = pc + op.getSize();
    }

    private static boolean writeDynamicReference(ExecutionContext executionContext, String name, PropertyKey key,
            JSValue value) {
        StackFrame frame = findDynamicVarBindingFrame(executionContext, name);
        if (frame == null) {
            return false;
        }
        frame.setDynamicVarBinding(name, value);
        JSContext context = executionContext.virtualMachine.context;
        if (context.hasEvalOverlayFrames() && context.hasEvalOverlayBinding(name)) {
            context.getGlobalObject().set(key, value);
            executionContext.capturePendingException();
        }
        return true;
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
