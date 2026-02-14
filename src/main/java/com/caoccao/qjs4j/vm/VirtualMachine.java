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
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.*;

/**
 * The JavaScript virtual machine bytecode interpreter.
 * Executes compiled bytecode using a stack-based architecture.
 */
public final class VirtualMachine {
    private static final JSObject UNINITIALIZED_MARKER = new JSObject();
    private final JSContext context;
    private final Set<JSObject> initializedConstantObjects;
    private final StringBuilder propertyAccessChain;  // Track last property access for better error messages
    private final CallStack valueStack;
    private StackFrame currentFrame;
    private int generatorResumeIndex;
    private List<JSGeneratorState.ResumeRecord> generatorResumeRecords;
    private JSValue pendingException;
    private boolean propertyAccessLock;  // When true, don't update lastPropertyAccess (during argument evaluation)
    private YieldResult yieldResult;  // Set when generator yields
    private int yieldSkipCount;  // How many yields to skip (for resuming generators)

    public VirtualMachine(JSContext context) {
        this.valueStack = new CallStack();
        this.context = context;
        this.initializedConstantObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        this.currentFrame = null;
        this.generatorResumeRecords = List.of();
        this.generatorResumeIndex = 0;
        this.pendingException = null;
        this.propertyAccessChain = new StringBuilder();
        this.propertyAccessLock = false;
        this.yieldResult = null;
        this.yieldSkipCount = 0;
    }

    /**
     * Clear the pending exception in the VM.
     * This is needed when an async function catches an exception.
     */
    public void clearPendingException() {
        this.pendingException = null;
    }

    private JSValue constructFunction(JSFunction function, JSValue[] args) {
        if (function instanceof JSBoundFunction boundFunction) {
            JSFunction targetFunction = boundFunction.getTarget();
            if (!JSTypeChecking.isConstructor(targetFunction)) {
                context.throwTypeError(boundFunction.getName() + " is not a constructor");
                return JSUndefined.INSTANCE;
            }
            return constructFunction(targetFunction, boundFunction.prependBoundArgs(args));
        }
        if (function instanceof JSClass jsClass) {
            return jsClass.construct(context, args);
        }

        JSConstructorType constructorType = function.getConstructorType();
        if (constructorType == null) {
            JSObject thisObject = new JSObject();
            context.transferPrototype(thisObject, function);

            JSValue result;
            if (function instanceof JSNativeFunction nativeFunc) {
                result = nativeFunc.call(context, thisObject, args);
                if (context.hasPendingException()) {
                    JSValue exception = context.getPendingException();
                    String errorMessage = "Unhandled exception in constructor";
                    if (exception instanceof JSObject errorObj) {
                        JSValue messageValue = errorObj.get("message");
                        if (messageValue instanceof JSString messageString) {
                            errorMessage = messageString.value();
                        }
                    }
                    throw new JSVirtualMachineException(errorMessage);
                }
            } else if (function instanceof JSBytecodeFunction bytecodeFunction) {
                result = execute(bytecodeFunction, thisObject, args);
            } else {
                result = JSUndefined.INSTANCE;
            }

            if (result instanceof JSObject) {
                return result;
            }
            return thisObject;
        }

        JSObject result = null;
        switch (constructorType) {
            case AGGREGATE_ERROR,
                 ARRAY,
                 ARRAY_BUFFER,
                 BIG_INT_OBJECT,
                 BOOLEAN_OBJECT,
                 DATA_VIEW,
                 DATE,
                 ERROR,
                 EVAL_ERROR,
                 FINALIZATION_REGISTRY,
                 MAP,
                 NUMBER_OBJECT,
                 PROMISE,
                 PROXY,
                 RANGE_ERROR,
                 REFERENCE_ERROR,
                 REGEXP,
                 SET,
                 SHARED_ARRAY_BUFFER,
                 STRING_OBJECT,
                 SUPPRESSED_ERROR,
                 SYMBOL_OBJECT,
                 SYNTAX_ERROR,
                 TYPED_ARRAY_BIGINT64,
                 TYPED_ARRAY_BIGUINT64,
                 TYPED_ARRAY_FLOAT16,
                 TYPED_ARRAY_FLOAT32,
                 TYPED_ARRAY_FLOAT64,
                 TYPED_ARRAY_INT16,
                 TYPED_ARRAY_INT32,
                 TYPED_ARRAY_INT8,
                 TYPED_ARRAY_UINT16,
                 TYPED_ARRAY_UINT32,
                 TYPED_ARRAY_UINT8,
                 TYPED_ARRAY_UINT8_CLAMPED,
                 TYPE_ERROR,
                 URI_ERROR,
                 WEAK_MAP,
                 WEAK_REF,
                 WEAK_SET -> result = constructorType.create(context, args);
        }
        if (result != null && !result.isError() && !result.isProxy()) {
            context.transferPrototype(result, function);
        }
        return result != null ? result : JSUndefined.INSTANCE;
    }

    private void copyDataProperties(JSValue targetValue, JSValue sourceValue, JSValue excludeListValue) {
        if (!(targetValue instanceof JSObject targetObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("copy target must be an object"));
        }
        if (sourceValue == null || sourceValue.isUndefined() || sourceValue.isNull()) {
            return;
        }

        JSObject sourceObject = toObject(sourceValue);
        if (sourceObject == null) {
            return;
        }

        Set<PropertyKey> excludedKeys = new HashSet<>();
        if (excludeListValue instanceof JSArray excludeArray) {
            for (int i = 0; i < excludeArray.getLength(); i++) {
                excludedKeys.add(PropertyKey.fromValue(context, excludeArray.get(i)));
            }
        } else if (excludeListValue instanceof JSObject excludeObject) {
            for (PropertyKey key : excludeObject.ownPropertyKeys()) {
                JSValue excludedValue = excludeObject.get(key, context);
                excludedKeys.add(PropertyKey.fromValue(context, excludedValue));
            }
        } else if (excludeListValue != null && !excludeListValue.isUndefined() && !excludeListValue.isNull()) {
            excludedKeys.add(PropertyKey.fromValue(context, excludeListValue));
        }

        for (PropertyKey key : sourceObject.ownPropertyKeys()) {
            PropertyDescriptor descriptor = sourceObject.getOwnPropertyDescriptor(key);
            if (descriptor == null || !descriptor.isEnumerable() || excludedKeys.contains(key)) {
                continue;
            }
            JSValue propertyValue = sourceObject.get(key, context);
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                context.clearPendingException();
                return;
            }
            targetObject.set(key, propertyValue, context);
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                context.clearPendingException();
                return;
            }
        }
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
                PropertyDescriptor.accessorDescriptor(getter, setter, true, true));
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

                // Create non-mapped arguments object (strict mode or modern)
                JSValue[] args = targetFrame.getArguments();
                boolean isStrict = targetFunc instanceof JSBytecodeFunction func && func.isStrict();
                // Pass the target function as callee for non-strict mode
                return new JSArguments(context, args, isStrict, isStrict ? null : targetFunc);

            case 1: // SPECIAL_OBJECT_MAPPED_ARGUMENTS
                // Legacy mapped arguments (shares with function parameters)
                // For now, treat same as normal arguments
                // TODO: Implement parameter mapping for non-strict mode
                JSValue[] mappedArgs = currentFrame.getArguments();
                JSFunction mappedFunc = currentFrame.getFunction();
                return new JSArguments(context, mappedArgs, false, mappedFunc);

            case 2: // SPECIAL_OBJECT_THIS_FUNC
                // Return the currently executing function
                return currentFrame.getFunction();

            case 3: // SPECIAL_OBJECT_NEW_TARGET
                // Return new.target (for class constructors)
                // For now return undefined - this needs new.target tracking
                return JSUndefined.INSTANCE;

            case 4: // SPECIAL_OBJECT_HOME_OBJECT
                // Return the home object for super property access
                // For now return undefined - needs super tracking
                return JSUndefined.INSTANCE;

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
        // Save the current value stack position
        // This ensures that nested function calls don't corrupt the caller's stack
        int savedStackTop = valueStack.getStackTop();

        // Save and set strict mode based on function
        // Following QuickJS: each function has its own strict mode flag
        boolean savedStrictMode = context.isStrictMode();
        if (function.isStrict()) {
            context.enterStrictMode();
        } else {
            context.exitStrictMode();
        }

        // Create new stack frame
        StackFrame frame = new StackFrame(function, thisArg, args, currentFrame);
        StackFrame previousFrame = currentFrame;
        currentFrame = frame;

        try {
            Bytecode bytecode = function.getBytecode();
            int pc = 0;

            // Main execution loop
            while (true) {
                if (pendingException != null) {
                    // QuickJS-style exception handling: unwind stack looking for catch offset
                    JSValue exception = pendingException;
                    pendingException = null;

                    // Unwind the stack looking for a CatchOffset marker
                    // Only unwind within the current function's stack frame (QuickJS: while (sp > stack_buf))
                    boolean foundHandler = false;
                    while (valueStack.getStackTop() > savedStackTop) {
                        JSStackValue val = valueStack.popStackValue();
                        if (val instanceof JSCatchOffset catchOffset) {
                            // Found catch handler - push exception and jump to it
                            valueStack.push(exception);
                            pc = catchOffset.offset();
                            foundHandler = true;
                            context.clearPendingException();
                            break;
                        }
                    }

                    if (!foundHandler) {
                        // No handler found - propagate exception
                        currentFrame = previousFrame;
                        if (exception instanceof JSError jsError) {
                            throw new JSVirtualMachineException(jsError);
                        }
                        // Safely convert exception to string without calling JavaScript methods
                        // to avoid issues when already in exception state
                        String exceptionMessage = safeExceptionToString(context, exception);
                        throw new JSVirtualMachineException("Unhandled exception: " + exceptionMessage);
                    }

                    // Continue execution at catch handler
                    continue;
                }

                int opcode = bytecode.readOpcode(pc);
                Opcode op = Opcode.fromInt(opcode);
                if (op == Opcode.INVALID && pc + 1 < bytecode.getLength()) {
                    // Extended opcode encoding: 0x00 prefix + low-byte payload for enum codes >= 256.
                    int extendedOpcode = 0x100 + bytecode.readU8(pc + 1);
                    Opcode extendedOp = Opcode.fromInt(extendedOpcode);
                    if (extendedOp != Opcode.INVALID) {
                        op = extendedOp;
                        // Rebase PC to the second opcode byte so existing `pc + 1` operand reads
                        // and `pc += op.getSize()` increments continue to work unchanged.
                        pc += 1;
                    }
                }

                switch (op) {
                    // ==================== Constants and Literals ====================
                    case INVALID -> throw new JSVirtualMachineException("Invalid opcode at PC " + pc);
                    case PUSH_I32 -> {
                        valueStack.push(new JSNumber(bytecode.readI32(pc + 1)));
                        pc += op.getSize();
                    }
                    case PUSH_BIGINT_I32 -> {
                        valueStack.push(new JSBigInt(bytecode.readI32(pc + 1)));
                        pc += op.getSize();
                    }
                    case PUSH_MINUS1, PUSH_0, PUSH_1, PUSH_2, PUSH_3, PUSH_4, PUSH_5, PUSH_6, PUSH_7 -> {
                        int value = switch (op) {
                            case PUSH_MINUS1 -> -1;
                            case PUSH_0 -> 0;
                            case PUSH_1 -> 1;
                            case PUSH_2 -> 2;
                            case PUSH_3 -> 3;
                            case PUSH_4 -> 4;
                            case PUSH_5 -> 5;
                            case PUSH_6 -> 6;
                            case PUSH_7 -> 7;
                            default -> throw new IllegalStateException("Unexpected short push opcode: " + op);
                        };
                        valueStack.push(new JSNumber(value));
                        pc += op.getSize();
                    }
                    case PUSH_I8 -> {
                        int value = (byte) bytecode.readU8(pc + 1);
                        valueStack.push(new JSNumber(value));
                        pc += op.getSize();
                    }
                    case PUSH_I16 -> {
                        int value = (short) bytecode.readU16(pc + 1);
                        valueStack.push(new JSNumber(value));
                        pc += op.getSize();
                    }
                    case PUSH_CONST -> {
                        int constIndex = bytecode.readU32(pc + 1);
                        JSValue constValue = bytecode.getConstants()[constIndex];

                        // Initialize prototype chain for functions
                        if (constValue instanceof JSFunction func) {
                            func.initializePrototypeChain(context);
                        } else if (constValue instanceof JSObject jsObject) {
                            ensureConstantObjectPrototype(jsObject);
                        }

                        valueStack.push(constValue);
                        pc += op.getSize();
                    }
                    case PUSH_CONST8 -> {
                        int constIndex = bytecode.readU8(pc + 1);
                        JSValue constValue = bytecode.getConstants()[constIndex];
                        if (constValue instanceof JSFunction func) {
                            func.initializePrototypeChain(context);
                        } else if (constValue instanceof JSObject jsObject) {
                            ensureConstantObjectPrototype(jsObject);
                        }
                        valueStack.push(constValue);
                        pc += op.getSize();
                    }
                    case PUSH_ATOM_VALUE -> {
                        // QuickJS OP_push_atom_value: read atom, convert to string value, push
                        int atomIndex = bytecode.readU32(pc + 1);
                        String atomStr = bytecode.getAtoms()[atomIndex];
                        valueStack.push(new JSString(atomStr));
                        pc += op.getSize();
                    }
                    case PRIVATE_SYMBOL -> {
                        // Create a unique private symbol for a private class field
                        // Reads: atom (field name)
                        // Result: pushes new symbol onto stack
                        int fieldNameAtom = bytecode.readU32(pc + 1);
                        String fieldName = bytecode.getAtoms()[fieldNameAtom];

                        // Create a unique symbol with the field name as description
                        // Each private field gets its own unique symbol
                        JSSymbol privateSymbol = new JSSymbol(fieldName);

                        valueStack.push(privateSymbol);
                        pc += op.getSize();
                    }
                    case FCLOSURE -> {
                        // Load function from constant pool and create closure
                        int funcIndex = bytecode.readU32(pc + 1);
                        JSValue funcValue = bytecode.getConstants()[funcIndex];
                        if (funcValue instanceof JSBytecodeFunction templateFunction) {
                            int closureCount = templateFunction.getClosureVars().length;
                            JSValue[] capturedClosureVars = new JSValue[closureCount];
                            for (int i = closureCount - 1; i >= 0; i--) {
                                capturedClosureVars[i] = valueStack.pop();
                            }
                            JSBytecodeFunction closureFunction = templateFunction.copyWithClosureVars(capturedClosureVars);
                            closureFunction.initializePrototypeChain(context);
                            valueStack.push(closureFunction);
                        } else {
                            // Initialize the function's prototype chain to inherit from Function.prototype
                            if (funcValue instanceof JSFunction func) {
                                func.initializePrototypeChain(context);
                            }
                            valueStack.push(funcValue);
                        }
                        pc += op.getSize();
                    }
                    case FCLOSURE8 -> {
                        int funcIndex = bytecode.readU8(pc + 1);
                        JSValue funcValue = bytecode.getConstants()[funcIndex];
                        if (funcValue instanceof JSBytecodeFunction templateFunction) {
                            int closureCount = templateFunction.getClosureVars().length;
                            JSValue[] capturedClosureVars = new JSValue[closureCount];
                            for (int i = closureCount - 1; i >= 0; i--) {
                                capturedClosureVars[i] = valueStack.pop();
                            }
                            JSBytecodeFunction closureFunction = templateFunction.copyWithClosureVars(capturedClosureVars);
                            closureFunction.initializePrototypeChain(context);
                            valueStack.push(closureFunction);
                        } else {
                            if (funcValue instanceof JSFunction func) {
                                func.initializePrototypeChain(context);
                            }
                            valueStack.push(funcValue);
                        }
                        pc += op.getSize();
                    }
                    case PUSH_EMPTY_STRING -> {
                        valueStack.push(new JSString(""));
                        pc += op.getSize();
                    }
                    case UNDEFINED -> {
                        valueStack.push(JSUndefined.INSTANCE);
                        pc += op.getSize();
                    }
                    case NULL -> {
                        valueStack.push(JSNull.INSTANCE);
                        pc += op.getSize();
                    }
                    case PUSH_THIS -> {
                        valueStack.push(currentFrame.getThisArg());
                        pc += op.getSize();
                    }
                    case PUSH_FALSE -> {
                        valueStack.push(JSBoolean.FALSE);
                        pc += op.getSize();
                    }
                    case PUSH_TRUE -> {
                        valueStack.push(JSBoolean.TRUE);
                        pc += op.getSize();
                    }
                    case SPECIAL_OBJECT -> {
                        // SPECIAL_OBJECT creates special runtime objects
                        // Opcode format: SPECIAL_OBJECT type (1 byte for opcode + 1 byte for type)
                        int objectType = bytecode.readU8(pc + 1);
                        JSValue specialObj = createSpecialObject(objectType, currentFrame);
                        valueStack.push(specialObj);
                        pc += op.getSize();
                    }
                    case REST -> {
                        // REST creates an array from remaining arguments
                        // Used at the start of functions with rest parameters (...args)
                        // Opcode format: REST first (1 byte for opcode + 2 bytes for u16)
                        // Reads the index of the first rest parameter
                        int first = bytecode.readU16(pc + 1);
                        JSValue[] funcArgs = currentFrame.getArguments();
                        int argc = funcArgs.length;

                        // Determine how many arguments to include in the rest array
                        // If first >= argc, create empty array
                        int restStart = Math.min(first, argc);
                        int restCount = argc - restStart;

                        // Create array from remaining arguments
                        JSValue[] restArgs = new JSValue[restCount];
                        System.arraycopy(funcArgs, restStart, restArgs, 0, restCount);

                        JSArray restArray = context.createJSArray(restArgs);
                        valueStack.push(restArray);
                        pc += op.getSize();
                    }

                    // ==================== Stack Manipulation ====================
                    case DROP -> {
                        valueStack.pop();
                        pc += op.getSize();
                    }
                    case NIP -> {
                        JSValue top = valueStack.pop();
                        valueStack.pop();
                        valueStack.push(top);
                        pc += op.getSize();
                    }
                    case DUP -> {
                        valueStack.push(valueStack.peek(0));
                        pc += op.getSize();
                    }
                    case DUP1 -> {
                        // a b -> a a b (duplicate the value at offset 1)
                        valueStack.push(valueStack.peek(1));
                        pc += op.getSize();
                    }
                    case DUP2 -> {
                        valueStack.push(valueStack.peek(1));
                        valueStack.push(valueStack.peek(1));
                        pc += op.getSize();
                    }
                    case INSERT2 -> {
                        // INSERT2: [a, b] -> [b, a, b]
                        // Duplicate top and insert below second element
                        JSValue top = valueStack.peek(0);
                        JSValue second = valueStack.peek(1);
                        valueStack.pop();  // Remove original top
                        valueStack.pop();  // Remove second
                        valueStack.push(top);
                        valueStack.push(second);
                        valueStack.push(top);
                        pc += op.getSize();
                    }
                    case INSERT3 -> {
                        // INSERT3: [a, b, c] -> [c, a, b, c]
                        // Duplicate top and insert below third element
                        JSValue top = valueStack.peek(0);
                        JSValue second = valueStack.peek(1);
                        JSValue third = valueStack.peek(2);
                        valueStack.pop();  // Remove original top
                        valueStack.pop();  // Remove second
                        valueStack.pop();  // Remove third
                        valueStack.push(top);
                        valueStack.push(third);
                        valueStack.push(second);
                        valueStack.push(top);
                        pc += op.getSize();
                    }
                    case INSERT4 -> {
                        // INSERT4: [a, b, c, d] -> [d, a, b, c, d]
                        // Duplicate top and insert below fourth element
                        JSValue top = valueStack.peek(0);
                        JSValue second = valueStack.peek(1);
                        JSValue third = valueStack.peek(2);
                        JSValue fourth = valueStack.peek(3);
                        valueStack.pop();  // Remove original top
                        valueStack.pop();  // Remove second
                        valueStack.pop();  // Remove third
                        valueStack.pop();  // Remove fourth
                        valueStack.push(top);
                        valueStack.push(fourth);
                        valueStack.push(third);
                        valueStack.push(second);
                        valueStack.push(top);
                        pc += op.getSize();
                    }
                    case SWAP -> {
                        JSValue v1 = valueStack.pop();
                        JSValue v2 = valueStack.pop();
                        valueStack.push(v1);
                        valueStack.push(v2);
                        // After SWAP in a method call, we're evaluating arguments
                        // Lock property access tracking to preserve the callee's property chain
                        propertyAccessLock = true;
                        pc += op.getSize();
                    }
                    case ROT3L -> {
                        JSValue a = valueStack.pop();
                        JSValue b = valueStack.pop();
                        JSValue c = valueStack.pop();
                        valueStack.push(b);
                        valueStack.push(a);
                        valueStack.push(c);
                        pc += op.getSize();
                    }
                    case ROT3R -> {
                        JSValue a = valueStack.pop();
                        JSValue b = valueStack.pop();
                        JSValue c = valueStack.pop();
                        valueStack.push(a);
                        valueStack.push(c);
                        valueStack.push(b);
                        pc += op.getSize();
                    }
                    case SWAP2 -> {
                        // SWAP2: [a, b, c, d] -> [c, d, a, b]
                        // Exchanges bottom 2 with top 2
                        JSValue d = valueStack.pop();
                        JSValue c = valueStack.pop();
                        JSValue b = valueStack.pop();
                        JSValue a = valueStack.pop();
                        valueStack.push(c);
                        valueStack.push(d);
                        valueStack.push(a);
                        valueStack.push(b);
                        pc += op.getSize();
                    }

                    // ==================== Arithmetic Operations ====================
                    case ADD -> {
                        handleAdd();
                        pc += op.getSize();
                    }
                    case SUB -> {
                        handleSub();
                        pc += op.getSize();
                    }
                    case MUL -> {
                        handleMul();
                        pc += op.getSize();
                    }
                    case DIV -> {
                        handleDiv();
                        pc += op.getSize();
                    }
                    case MOD -> {
                        handleMod();
                        pc += op.getSize();
                    }
                    case EXP, POW -> {
                        handleExp();
                        pc += op.getSize();
                    }
                    case PLUS -> {
                        handlePlus();
                        pc += op.getSize();
                    }
                    case NEG -> {
                        handleNeg();
                        pc += op.getSize();
                    }
                    case INC -> {
                        handleInc();
                        pc += op.getSize();
                    }
                    case DEC -> {
                        handleDec();
                        pc += op.getSize();
                    }
                    case INC_LOC -> {
                        int localIndex = bytecode.readU8(pc + 1);
                        JSValue localValue = getLocalValue(localIndex);
                        double result = JSTypeConversions.toNumber(context, localValue).value() + 1;
                        setLocalValue(localIndex, new JSNumber(result));
                        pc += op.getSize();
                    }
                    case DEC_LOC -> {
                        int localIndex = bytecode.readU8(pc + 1);
                        JSValue localValue = getLocalValue(localIndex);
                        double result = JSTypeConversions.toNumber(context, localValue).value() - 1;
                        setLocalValue(localIndex, new JSNumber(result));
                        pc += op.getSize();
                    }
                    case ADD_LOC -> {
                        int localIndex = bytecode.readU8(pc + 1);
                        JSValue right = valueStack.pop();
                        JSValue left = getLocalValue(localIndex);
                        JSValue result;
                        if (left instanceof JSString || right instanceof JSString) {
                            String leftStr = JSTypeConversions.toString(context, left).value();
                            String rightStr = JSTypeConversions.toString(context, right).value();
                            result = new JSString(leftStr + rightStr);
                        } else {
                            double leftNum = JSTypeConversions.toNumber(context, left).value();
                            double rightNum = JSTypeConversions.toNumber(context, right).value();
                            result = new JSNumber(leftNum + rightNum);
                        }
                        setLocalValue(localIndex, result);
                        pc += op.getSize();
                    }
                    case POST_INC -> {
                        handlePostInc();
                        pc += op.getSize();
                    }
                    case POST_DEC -> {
                        handlePostDec();
                        pc += op.getSize();
                    }
                    case PERM3 -> {
                        // PERM3: [a, b, c] -> [b, a, c] (QuickJS: obj a b -> a obj b)
                        JSValue c = valueStack.pop();
                        JSValue b = valueStack.pop();
                        JSValue a = valueStack.pop();
                        valueStack.push(b);
                        valueStack.push(a);
                        valueStack.push(c);
                        pc += op.getSize();
                    }
                    case PERM4 -> {
                        // PERM4: [a, b, c, d] -> [c, a, b, d] (QuickJS: obj prop a b -> a obj prop b)
                        JSValue d = valueStack.pop();
                        JSValue c = valueStack.pop();
                        JSValue b = valueStack.pop();
                        JSValue a = valueStack.pop();
                        valueStack.push(c);
                        valueStack.push(a);
                        valueStack.push(b);
                        valueStack.push(d);
                        pc += op.getSize();
                    }
                    case PERM5 -> {
                        // PERM5: [a, b, c, d, e] -> [d, a, b, c, e] (QuickJS: this obj prop a b -> a this obj prop b)
                        JSValue e = valueStack.pop();
                        JSValue d = valueStack.pop();
                        JSValue c = valueStack.pop();
                        JSValue b = valueStack.pop();
                        JSValue a = valueStack.pop();
                        valueStack.push(d);
                        valueStack.push(a);
                        valueStack.push(b);
                        valueStack.push(c);
                        valueStack.push(e);
                        pc += op.getSize();
                    }

                    // ==================== Bitwise Operations ====================
                    case SHL -> {
                        handleShl();
                        pc += op.getSize();
                    }
                    case SAR -> {
                        handleSar();
                        pc += op.getSize();
                    }
                    case SHR -> {
                        handleShr();
                        pc += op.getSize();
                    }
                    case AND -> {
                        handleAnd();
                        pc += op.getSize();
                    }
                    case OR -> {
                        handleOr();
                        pc += op.getSize();
                    }
                    case XOR -> {
                        handleXor();
                        pc += op.getSize();
                    }
                    case NOT -> {
                        handleNot();
                        pc += op.getSize();
                    }

                    // ==================== Comparison Operations ====================
                    case EQ -> {
                        handleEq();
                        pc += op.getSize();
                    }
                    case NEQ -> {
                        handleNeq();
                        pc += op.getSize();
                    }
                    case STRICT_EQ -> {
                        handleStrictEq();
                        pc += op.getSize();
                    }
                    case STRICT_NEQ -> {
                        handleStrictNeq();
                        pc += op.getSize();
                    }
                    case LT -> {
                        handleLt();
                        pc += op.getSize();
                    }
                    case LTE -> {
                        handleLte();
                        pc += op.getSize();
                    }
                    case GT -> {
                        handleGt();
                        pc += op.getSize();
                    }
                    case GTE -> {
                        handleGte();
                        pc += op.getSize();
                    }
                    case INSTANCEOF -> {
                        handleInstanceof();
                        pc += op.getSize();
                    }
                    case IN -> {
                        handleIn();
                        pc += op.getSize();
                    }
                    case PRIVATE_IN -> {
                        handlePrivateIn();
                        pc += op.getSize();
                    }

                    // ==================== Logical Operations ====================
                    case LOGICAL_NOT, LNOT -> {
                        handleLogicalNot();
                        pc += op.getSize();
                    }
                    case LOGICAL_AND -> {
                        handleLogicalAnd();
                        pc += op.getSize();
                    }
                    case LOGICAL_OR -> {
                        handleLogicalOr();
                        pc += op.getSize();
                    }
                    case NULLISH_COALESCE -> {
                        handleNullishCoalesce();
                        pc += op.getSize();
                    }

                    // ==================== Variable Access ====================
                    case GET_VAR_UNDEF -> {
                        int getVarRefIndex = bytecode.readU16(pc + 1);
                        valueStack.push(currentFrame.getVarRef(getVarRefIndex));
                        pc += op.getSize();
                    }
                    case GET_REF_VALUE -> {
                        JSValue propertyValue = valueStack.peek(0);
                        JSValue objectValue = valueStack.peek(1);
                        PropertyKey key = PropertyKey.fromValue(context, propertyValue);

                        if (objectValue.isUndefined()) {
                            throw referenceErrorNotDefined(key);
                        }

                        JSObject targetObject = toObject(objectValue);
                        if (targetObject == null) {
                            throw new JSVirtualMachineException(context.throwTypeError("value has no property"));
                        }

                        JSValue value;
                        if (!targetObject.has(key)) {
                            if (context.isStrictMode()) {
                                throw referenceErrorNotDefined(key);
                            }
                            value = JSUndefined.INSTANCE;
                        } else {
                            value = targetObject.get(key, context);
                        }
                        valueStack.push(value);
                        pc += op.getSize();
                    }
                    case GET_VAR -> {
                        int getVarAtom = bytecode.readU32(pc + 1);
                        String getVarName = bytecode.getAtoms()[getVarAtom];
                        PropertyKey key = PropertyKey.fromString(getVarName);
                        JSObject globalObject = context.getGlobalObject();
                        if (!globalObject.has(key)) {
                            throw referenceErrorNotDefined(key);
                        }
                        JSValue varValue = globalObject.get(key);
                        // Start tracking property access from variable name (unless locked)
                        if (!propertyAccessLock) {
                            resetPropertyAccessTracking();
                            propertyAccessChain.append(getVarName);
                        }
                        valueStack.push(varValue);
                        pc += op.getSize();
                    }
                    case PUT_VAR_INIT -> {
                        int putVarRefIndex = bytecode.readU16(pc + 1);
                        JSValue putValue = valueStack.pop();
                        currentFrame.setVarRef(putVarRefIndex, putValue);
                        pc += op.getSize();
                    }
                    case PUT_REF_VALUE -> {
                        JSValue setValue = valueStack.pop();
                        JSValue propertyValue = valueStack.pop();
                        JSValue objectValue = valueStack.pop();
                        PropertyKey key = PropertyKey.fromValue(context, propertyValue);

                        if (objectValue.isUndefined()) {
                            if (context.isStrictMode()) {
                                throw referenceErrorNotDefined(key);
                            }
                            objectValue = context.getGlobalObject();
                        }

                        JSObject targetObject = toObject(objectValue);
                        if (targetObject == null) {
                            throw new JSVirtualMachineException(context.throwTypeError("value has no property"));
                        }

                        if (!targetObject.has(key) && context.isStrictMode()) {
                            throw referenceErrorNotDefined(key);
                        }

                        targetObject.set(key, setValue, context);
                        if (context.hasPendingException()) {
                            pendingException = context.getPendingException();
                            context.clearPendingException();
                        }
                        pc += op.getSize();
                    }
                    case PUT_VAR -> {
                        int putVarAtom = bytecode.readU32(pc + 1);
                        String putVarName = bytecode.getAtoms()[putVarAtom];
                        JSValue putValue = valueStack.pop();
                        context.getGlobalObject().set(PropertyKey.fromString(putVarName), putValue);
                        pc += op.getSize();
                    }
                    case SET_VAR -> {
                        int setVarAtom = bytecode.readU32(pc + 1);
                        String setVarName = bytecode.getAtoms()[setVarAtom];
                        JSValue setValue = valueStack.peek(0);
                        context.getGlobalObject().set(PropertyKey.fromString(setVarName), setValue);
                        pc += op.getSize();
                    }
                    case DELETE_VAR -> {
                        int deleteVarAtom = bytecode.readU32(pc + 1);
                        String deleteVarName = bytecode.getAtoms()[deleteVarAtom];
                        boolean deleted = context.getGlobalObject().delete(PropertyKey.fromString(deleteVarName), context);
                        valueStack.push(JSBoolean.valueOf(deleted));
                        pc += op.getSize();
                    }
                    case GET_LOCAL, GET_LOC -> {
                        int getLocalIndex = bytecode.readU16(pc + 1);
                        valueStack.push(getLocalValue(getLocalIndex));
                        pc += op.getSize();
                    }
                    case GET_LOC8 -> {
                        int getLocalIndex = bytecode.readU8(pc + 1);
                        valueStack.push(getLocalValue(getLocalIndex));
                        pc += op.getSize();
                    }
                    case GET_LOC0, GET_LOC1, GET_LOC2, GET_LOC3 -> {
                        int getLocalIndex = switch (op) {
                            case GET_LOC0 -> 0;
                            case GET_LOC1 -> 1;
                            case GET_LOC2 -> 2;
                            case GET_LOC3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short get local opcode: " + op);
                        };
                        valueStack.push(getLocalValue(getLocalIndex));
                        pc += op.getSize();
                    }
                    case PUT_LOCAL, PUT_LOC -> {
                        int putLocalIndex = bytecode.readU16(pc + 1);
                        JSValue value = valueStack.pop();
                        setLocalValue(putLocalIndex, value);
                        pc += op.getSize();
                    }
                    case PUT_LOC8 -> {
                        int putLocalIndex = bytecode.readU8(pc + 1);
                        JSValue value = valueStack.pop();
                        setLocalValue(putLocalIndex, value);
                        pc += op.getSize();
                    }
                    case PUT_LOC0, PUT_LOC1, PUT_LOC2, PUT_LOC3 -> {
                        int putLocalIndex = switch (op) {
                            case PUT_LOC0 -> 0;
                            case PUT_LOC1 -> 1;
                            case PUT_LOC2 -> 2;
                            case PUT_LOC3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short put local opcode: " + op);
                        };
                        JSValue value = valueStack.pop();
                        setLocalValue(putLocalIndex, value);
                        pc += op.getSize();
                    }
                    case SET_LOCAL, SET_LOC -> {
                        int setLocalIndex = bytecode.readU16(pc + 1);
                        setLocalValue(setLocalIndex, valueStack.peek(0));
                        pc += op.getSize();
                    }
                    case SET_LOC8 -> {
                        int setLocalIndex = bytecode.readU8(pc + 1);
                        setLocalValue(setLocalIndex, valueStack.peek(0));
                        pc += op.getSize();
                    }
                    case SET_LOC0, SET_LOC1, SET_LOC2, SET_LOC3 -> {
                        int setLocalIndex = switch (op) {
                            case SET_LOC0 -> 0;
                            case SET_LOC1 -> 1;
                            case SET_LOC2 -> 2;
                            case SET_LOC3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short set local opcode: " + op);
                        };
                        setLocalValue(setLocalIndex, valueStack.peek(0));
                        pc += op.getSize();
                    }
                    case GET_ARG -> {
                        int argIndex = bytecode.readU16(pc + 1);
                        valueStack.push(getArgumentValue(argIndex));
                        pc += op.getSize();
                    }
                    case GET_ARG0, GET_ARG1, GET_ARG2, GET_ARG3 -> {
                        int argIndex = switch (op) {
                            case GET_ARG0 -> 0;
                            case GET_ARG1 -> 1;
                            case GET_ARG2 -> 2;
                            case GET_ARG3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short get arg opcode: " + op);
                        };
                        valueStack.push(getArgumentValue(argIndex));
                        pc += op.getSize();
                    }
                    case PUT_ARG -> {
                        int argIndex = bytecode.readU16(pc + 1);
                        JSValue value = valueStack.pop();
                        setArgumentValue(argIndex, value);
                        pc += op.getSize();
                    }
                    case PUT_ARG0, PUT_ARG1, PUT_ARG2, PUT_ARG3 -> {
                        int argIndex = switch (op) {
                            case PUT_ARG0 -> 0;
                            case PUT_ARG1 -> 1;
                            case PUT_ARG2 -> 2;
                            case PUT_ARG3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short put arg opcode: " + op);
                        };
                        JSValue value = valueStack.pop();
                        setArgumentValue(argIndex, value);
                        pc += op.getSize();
                    }
                    case SET_ARG -> {
                        int argIndex = bytecode.readU16(pc + 1);
                        setArgumentValue(argIndex, valueStack.peek(0));
                        pc += op.getSize();
                    }
                    case SET_ARG0, SET_ARG1, SET_ARG2, SET_ARG3 -> {
                        int argIndex = switch (op) {
                            case SET_ARG0 -> 0;
                            case SET_ARG1 -> 1;
                            case SET_ARG2 -> 2;
                            case SET_ARG3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short set arg opcode: " + op);
                        };
                        setArgumentValue(argIndex, valueStack.peek(0));
                        pc += op.getSize();
                    }
                    case GET_VAR_REF -> {
                        int getVarRefIndex = bytecode.readU16(pc + 1);
                        valueStack.push(currentFrame.getVarRef(getVarRefIndex));
                        pc += op.getSize();
                    }
                    case GET_VAR_REF0, GET_VAR_REF1, GET_VAR_REF2, GET_VAR_REF3 -> {
                        int getVarRefIndex = switch (op) {
                            case GET_VAR_REF0 -> 0;
                            case GET_VAR_REF1 -> 1;
                            case GET_VAR_REF2 -> 2;
                            case GET_VAR_REF3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short get var ref opcode: " + op);
                        };
                        valueStack.push(currentFrame.getVarRef(getVarRefIndex));
                        pc += op.getSize();
                    }
                    case PUT_VAR_REF -> {
                        int putVarRefIndex = bytecode.readU16(pc + 1);
                        JSValue value = valueStack.pop();
                        currentFrame.setVarRef(putVarRefIndex, value);
                        pc += op.getSize();
                    }
                    case PUT_VAR_REF0, PUT_VAR_REF1, PUT_VAR_REF2, PUT_VAR_REF3 -> {
                        int putVarRefIndex = switch (op) {
                            case PUT_VAR_REF0 -> 0;
                            case PUT_VAR_REF1 -> 1;
                            case PUT_VAR_REF2 -> 2;
                            case PUT_VAR_REF3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short put var ref opcode: " + op);
                        };
                        JSValue value = valueStack.pop();
                        currentFrame.setVarRef(putVarRefIndex, value);
                        pc += op.getSize();
                    }
                    case SET_VAR_REF -> {
                        int setVarRefIndex = bytecode.readU16(pc + 1);
                        currentFrame.setVarRef(setVarRefIndex, valueStack.peek(0));
                        pc += op.getSize();
                    }
                    case SET_VAR_REF0, SET_VAR_REF1, SET_VAR_REF2, SET_VAR_REF3 -> {
                        int setVarRefIndex = switch (op) {
                            case SET_VAR_REF0 -> 0;
                            case SET_VAR_REF1 -> 1;
                            case SET_VAR_REF2 -> 2;
                            case SET_VAR_REF3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short set var ref opcode: " + op);
                        };
                        currentFrame.setVarRef(setVarRefIndex, valueStack.peek(0));
                        pc += op.getSize();
                    }
                    case CLOSE_LOC -> {
                        int closeLocalIndex = bytecode.readU16(pc + 1);
                        currentFrame.closeLocal(closeLocalIndex);
                        pc += op.getSize();
                    }
                    case SET_LOC_UNINITIALIZED -> {
                        int index = bytecode.readU16(pc + 1);
                        currentFrame.getLocals()[index] = UNINITIALIZED_MARKER;
                        pc += op.getSize();
                    }
                    case GET_LOC_CHECK, GET_LOC_CHECKTHIS -> {
                        int index = bytecode.readU16(pc + 1);
                        JSValue localValue = currentFrame.getLocals()[index];
                        if (isUninitialized(localValue)) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        valueStack.push(localValue);
                        pc += op.getSize();
                    }
                    case PUT_LOC_CHECK -> {
                        int index = bytecode.readU16(pc + 1);
                        if (isUninitialized(currentFrame.getLocals()[index])) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        currentFrame.getLocals()[index] = valueStack.pop();
                        pc += op.getSize();
                    }
                    case SET_LOC_CHECK -> {
                        int index = bytecode.readU16(pc + 1);
                        if (isUninitialized(currentFrame.getLocals()[index])) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        currentFrame.getLocals()[index] = valueStack.peek(0);
                        pc += op.getSize();
                    }
                    case PUT_LOC_CHECK_INIT -> {
                        int index = bytecode.readU16(pc + 1);
                        if (!isUninitialized(currentFrame.getLocals()[index])) {
                            throw new JSVirtualMachineException(context.throwReferenceError("'this' can be initialized only once"));
                        }
                        currentFrame.getLocals()[index] = valueStack.pop();
                        pc += op.getSize();
                    }
                    case GET_VAR_REF_CHECK -> {
                        int index = bytecode.readU16(pc + 1);
                        JSValue value = currentFrame.getVarRef(index);
                        if (isUninitialized(value)) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        valueStack.push(value);
                        pc += op.getSize();
                    }
                    case PUT_VAR_REF_CHECK -> {
                        int index = bytecode.readU16(pc + 1);
                        if (isUninitialized(currentFrame.getVarRef(index))) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        currentFrame.setVarRef(index, valueStack.pop());
                        pc += op.getSize();
                    }
                    case PUT_VAR_REF_CHECK_INIT -> {
                        int index = bytecode.readU16(pc + 1);
                        if (!isUninitialized(currentFrame.getVarRef(index))) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is already initialized"));
                        }
                        currentFrame.setVarRef(index, valueStack.pop());
                        pc += op.getSize();
                    }
                    case MAKE_LOC_REF, MAKE_ARG_REF, MAKE_VAR_REF_REF -> {
                        int atomIndex = bytecode.readU32(pc + 1);
                        int refIndex = bytecode.readU16(pc + 5);
                        String atomName = bytecode.getAtoms()[atomIndex];

                        JSObject referenceObject = createReferenceObject(op, refIndex, atomName);
                        valueStack.push(referenceObject);
                        valueStack.push(new JSString(atomName));
                        pc += op.getSize();
                    }
                    case MAKE_VAR_REF -> {
                        int atomIndex = bytecode.readU32(pc + 1);
                        String atomName = bytecode.getAtoms()[atomIndex];
                        valueStack.push(context.getGlobalObject());
                        valueStack.push(new JSString(atomName));
                        pc += op.getSize();
                    }

                    // ==================== Property Access ====================
                    case GET_FIELD -> {
                        int getFieldAtom = bytecode.readU32(pc + 1);
                        String fieldName = bytecode.getAtoms()[getFieldAtom];
                        JSValue obj = valueStack.pop();

                        // Auto-box primitives to access their prototype methods
                        JSObject targetObj = toObject(obj);
                        if (targetObj != null) {
                            JSValue result = targetObj.get(PropertyKey.fromString(fieldName), context);
                            // Check if getter threw an exception
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                                valueStack.push(JSUndefined.INSTANCE);
                            } else {
                                // Track property access for better error messages (unless locked)
                                if (!propertyAccessLock) {
                                    if (!propertyAccessChain.isEmpty()) {
                                        propertyAccessChain.append('.');
                                    }
                                    propertyAccessChain.append(fieldName);
                                }
                                valueStack.push(result);
                            }
                        } else {
                            resetPropertyAccessTracking();
                            valueStack.push(JSUndefined.INSTANCE);
                        }
                        pc += op.getSize();
                    }
                    case GET_LENGTH -> {
                        JSValue objectValue = valueStack.pop();
                        JSObject targetObject = toObject(objectValue);
                        if (targetObject != null) {
                            JSValue result = targetObject.get(PropertyKey.fromString("length"), context);
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                                valueStack.push(JSUndefined.INSTANCE);
                            } else {
                                valueStack.push(result);
                            }
                        } else {
                            valueStack.push(JSUndefined.INSTANCE);
                        }
                        pc += op.getSize();
                    }
                    case PUT_FIELD -> {
                        int putFieldAtom = bytecode.readU32(pc + 1);
                        String putFieldName = bytecode.getAtoms()[putFieldAtom];
                        JSValue putFieldObj = valueStack.pop();
                        // The value should be on top of the stack.
                        JSValue putFieldValue = valueStack.peek(0);
                        if (putFieldObj instanceof JSObject jsObj) {
                            jsObj.set(PropertyKey.fromString(putFieldName), putFieldValue, context);
                            // Check if setter threw an exception
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                            }
                        }
                        pc += op.getSize();
                    }
                    case GET_ARRAY_EL -> {
                        JSValue index = valueStack.pop();
                        JSValue arrayObj = valueStack.pop();

                        // Auto-box primitives to access their prototype methods
                        JSObject targetObj = toObject(arrayObj);
                        if (targetObj != null) {
                            PropertyKey key = PropertyKey.fromValue(context, index);
                            JSValue result = targetObj.get(key, context);
                            // Check if getter threw an exception
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                                valueStack.push(JSUndefined.INSTANCE);
                            } else {
                                // Track property access for better error messages (unless locked)
                                if (!propertyAccessLock) {
                                    if (index instanceof JSString jsString) {
                                        String propertyName = jsString.value();
                                        if (!propertyAccessChain.isEmpty()) {
                                            propertyAccessChain.append('.');
                                        }
                                        propertyAccessChain.append(propertyName);
                                    } else if (index instanceof JSNumber jsNumber) {
                                        String propertyName = JSTypeConversions.toString(context, jsNumber).value();
                                        if (!propertyAccessChain.isEmpty()) {
                                            propertyAccessChain.append('.');
                                        }
                                        propertyAccessChain.append(propertyName);
                                    } else if (index instanceof JSSymbol jsSymbol) {
                                        propertyAccessChain.append("[Symbol.").append(jsSymbol.getDescription()).append("]");
                                    }
                                }
                                valueStack.push(result);
                            }
                        } else {
                            resetPropertyAccessTracking();
                            valueStack.push(JSUndefined.INSTANCE);
                        }
                        pc += op.getSize();
                    }
                    case GET_ARRAY_EL2 -> {
                        JSValue index = valueStack.pop();
                        JSValue arrayObj = valueStack.peek(0);

                        JSObject targetObj = toObject(arrayObj);
                        if (targetObj != null) {
                            PropertyKey key = PropertyKey.fromValue(context, index);
                            JSValue result = targetObj.get(key, context);
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                                valueStack.push(JSUndefined.INSTANCE);
                            } else {
                                valueStack.push(result);
                            }
                        } else {
                            valueStack.push(JSUndefined.INSTANCE);
                        }
                        pc += op.getSize();
                    }
                    case GET_ARRAY_EL3 -> {
                        JSValue index = valueStack.peek(0);
                        JSValue arrayObj = valueStack.peek(1);

                        if (!(index instanceof JSNumber || index instanceof JSString || index instanceof JSSymbol)) {
                            if (arrayObj.isUndefined() || arrayObj.isNull()) {
                                throw new JSVirtualMachineException(context.throwTypeError("value has no property"));
                            }
                            JSValue convertedIndex = toPropertyKeyValue(index);
                            valueStack.set(0, convertedIndex);
                            index = convertedIndex;
                        }

                        JSObject targetObj = toObject(arrayObj);
                        if (targetObj == null) {
                            throw new JSVirtualMachineException(context.throwTypeError("value has no property"));
                        }

                        PropertyKey key = PropertyKey.fromValue(context, index);
                        JSValue result = targetObj.get(key, context);
                        if (context.hasPendingException()) {
                            pendingException = context.getPendingException();
                            context.clearPendingException();
                            valueStack.push(JSUndefined.INSTANCE);
                        } else {
                            valueStack.push(result);
                        }
                        pc += op.getSize();
                    }
                    case PUT_ARRAY_EL -> {
                        // Stack layout: [value, object, property] (property on top)
                        JSValue putElIndex = valueStack.pop();   // Pop property
                        JSValue putElObj = valueStack.pop();     // Pop object
                        JSValue putElValue = valueStack.pop();   // Pop value
                        if (putElObj instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(context, putElIndex);
                            jsObj.set(key, putElValue, context);
                            // Check if setter threw an exception
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                            }
                        }
                        // Assignment expressions return the assigned value
                        valueStack.push(putElValue);
                        pc += op.getSize();
                    }
                    case TO_PROPKEY -> {
                        JSValue rawKey = valueStack.pop();
                        valueStack.push(toPropertyKeyValue(rawKey));
                        pc += op.getSize();
                    }
                    case TO_PROPKEY2 -> {
                        JSValue rawKey = valueStack.pop();
                        JSValue baseObject = valueStack.pop();
                        valueStack.push(baseObject);
                        valueStack.push(toPropertyKeyValue(rawKey));
                        pc += op.getSize();
                    }

                    // ==================== Control Flow ====================
                    case IF_FALSE -> {
                        JSValue condition = valueStack.pop();
                        boolean isFalsy = JSTypeConversions.toBoolean(condition) == JSBoolean.FALSE;
                        if (isFalsy) {
                            int offset = bytecode.readI32(pc + 1);
                            pc += op.getSize() + offset;
                        } else {
                            pc += op.getSize();
                        }
                    }
                    case IF_TRUE -> {
                        JSValue trueCondition = valueStack.pop();
                        boolean isTruthy = JSTypeConversions.toBoolean(trueCondition) == JSBoolean.TRUE;
                        if (isTruthy) {
                            int offset = bytecode.readI32(pc + 1);
                            pc += op.getSize() + offset;
                        } else {
                            pc += op.getSize();
                        }
                    }
                    case IF_TRUE8 -> {
                        JSValue condition = valueStack.pop();
                        boolean isTruthy = JSTypeConversions.toBoolean(condition) == JSBoolean.TRUE;
                        int offset = (byte) bytecode.readU8(pc + 1);
                        if (isTruthy) {
                            pc += op.getSize() + offset;
                        } else {
                            pc += op.getSize();
                        }
                    }
                    case IF_FALSE8 -> {
                        JSValue condition = valueStack.pop();
                        boolean isFalsy = JSTypeConversions.toBoolean(condition) == JSBoolean.FALSE;
                        int offset = (byte) bytecode.readU8(pc + 1);
                        if (isFalsy) {
                            pc += op.getSize() + offset;
                        } else {
                            pc += op.getSize();
                        }
                    }
                    case GOTO -> {
                        int gotoOffset = bytecode.readI32(pc + 1);
                        pc += op.getSize() + gotoOffset;
                    }
                    case GOTO8 -> {
                        int gotoOffset = (byte) bytecode.readU8(pc + 1);
                        pc += op.getSize() + gotoOffset;
                    }
                    case GOTO16 -> {
                        int gotoOffset = (short) bytecode.readU16(pc + 1);
                        pc += op.getSize() + gotoOffset;
                    }
                    case RETURN -> {
                        JSValue returnValue = valueStack.pop();
                        // Restore stack and strict mode before returning
                        valueStack.setStackTop(savedStackTop);
                        currentFrame = previousFrame;
                        if (savedStrictMode) {
                            context.enterStrictMode();
                        } else {
                            context.exitStrictMode();
                        }
                        return returnValue;
                    }
                    case RETURN_UNDEF -> {
                        // Restore stack and strict mode before returning
                        valueStack.setStackTop(savedStackTop);
                        currentFrame = previousFrame;
                        if (savedStrictMode) {
                            context.enterStrictMode();
                        } else {
                            context.exitStrictMode();
                        }
                        return JSUndefined.INSTANCE;
                    }
                    case RETURN_ASYNC -> {
                        // Return from async function - pops value from stack
                        // The wrapping in a promise is handled by JSBytecodeFunction.call()
                        JSValue returnValue = valueStack.pop();
                        // Restore stack and strict mode before returning
                        valueStack.setStackTop(savedStackTop);
                        currentFrame = previousFrame;
                        if (savedStrictMode) {
                            context.enterStrictMode();
                        } else {
                            context.exitStrictMode();
                        }
                        return returnValue;
                    }

                    // ==================== Function Calls ====================
                    case INIT_CTOR -> {
                        handleInitCtor();
                        pc += op.getSize();
                    }
                    case CALL -> {
                        int argCount = bytecode.readU16(pc + 1);
                        handleCall(argCount);
                        pc += op.getSize();
                    }
                    case CALL0, CALL1, CALL2, CALL3 -> {
                        int argCount = switch (op) {
                            case CALL0 -> 0;
                            case CALL1 -> 1;
                            case CALL2 -> 2;
                            case CALL3 -> 3;
                            default -> throw new IllegalStateException("Unexpected short call opcode: " + op);
                        };
                        handleCall(argCount);
                        pc += op.getSize();
                    }
                    case CALL_CONSTRUCTOR -> {
                        int ctorArgCount = bytecode.readU16(pc + 1);
                        handleCallConstructor(ctorArgCount);
                        pc += op.getSize();
                    }

                    // ==================== Object/Array Creation ====================
                    case OBJECT, OBJECT_NEW -> {
                        valueStack.push(context.createJSObject());
                        pc += op.getSize();
                    }
                    case ARRAY_NEW -> {
                        JSArray array = context.createJSArray();
                        valueStack.push(array);
                        pc += op.getSize();
                    }
                    case ARRAY_FROM -> {
                        // Create array from N elements on stack
                        // Stack: elem0 elem1 ... elemN-1 -> array
                        int count = bytecode.readU16(pc + 1);
                        JSArray array = context.createJSArray();

                        // Pop elements in reverse order and add to array
                        JSValue[] elements = new JSValue[count];
                        for (int i = count - 1; i >= 0; i--) {
                            elements[i] = valueStack.pop();
                        }
                        for (JSValue element : elements) {
                            array.push(element);
                        }

                        valueStack.push(array);
                        pc += op.getSize();
                    }
                    case APPLY -> {
                        // Apply function with arguments from array
                        // Stack: thisArg function argsArray -> result
                        // Parameter: isConstructorCall (0=regular, 1=constructor)
                        int isConstructorCall = bytecode.readU16(pc + 1);

                        JSValue argsArrayValue = valueStack.pop();
                        JSValue functionValue = valueStack.pop();
                        JSValue thisArgValue = valueStack.pop();

                        JSValue result;
                        if (isConstructorCall != 0) {
                            // QuickJS OP_apply constructor mode routes to CallConstructor2
                            // with thisArg as newTarget.
                            JSValue newTarget = thisArgValue;
                            if (newTarget.isUndefined() || newTarget.isNull()) {
                                newTarget = functionValue;
                            }
                            result = JSReflectObject.construct(
                                    context,
                                    JSUndefined.INSTANCE,
                                    new JSValue[]{functionValue, argsArrayValue, newTarget});
                        } else {
                            JSValue[] applyArgs = buildApplyArguments(argsArrayValue, true);
                            if (applyArgs == null) {
                                pendingException = context.getPendingException();
                                valueStack.push(JSUndefined.INSTANCE);
                                pc += op.getSize();
                                break;
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
                            valueStack.push(JSUndefined.INSTANCE);
                        } else {
                            valueStack.push(result);
                        }
                        pc += op.getSize();
                    }
                    case PUSH_ARRAY -> {
                        JSValue element = valueStack.pop();
                        JSValue array = valueStack.peek(0);
                        if (array instanceof JSArray jsArray) {
                            jsArray.push(element);
                        }
                        pc += op.getSize();
                    }
                    case APPEND -> {
                        // Append enumerated object elements to array
                        // Stack: array pos enumobj -> array pos
                        // Based on QuickJS OP_append (quickjs.c js_append_enumerate)
                        JSValue enumobj = valueStack.pop();
                        JSValue posValue = valueStack.pop();
                        JSValue arrayValue = valueStack.pop();

                        if (!(arrayValue instanceof JSArray array)) {
                            throw new JSVirtualMachineException("APPEND: first argument must be an array");
                        }

                        if (!(posValue instanceof JSNumber posNum)) {
                            throw new JSVirtualMachineException("APPEND: second argument must be a number");
                        }

                        int pos = (int) posNum.value();

                        // Get iterator from enumobj
                        try {
                            JSValue iterator = JSIteratorHelper.getIterator(context, enumobj);

                            if (iterator == null) {
                                // Not iterable, throw TypeError
                                context.throwError("TypeError", "Value is not iterable");
                                throw new JSVirtualMachineException("APPEND: value is not iterable");
                            }

                            // Iterate and append all elements
                            while (true) {
                                JSObject resultObj = JSIteratorHelper.iteratorNext(iterator, context);
                                if (resultObj == null) {
                                    break;
                                }

                                // Check if done
                                JSValue doneValue = resultObj.get("done");
                                if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                                    break;
                                }

                                // Get value and append to array at position
                                JSValue value = resultObj.get("value");
                                // Set array element (this will update length automatically)
                                array.set(pos++, value, context);
                            }

                            // Push array and updated position back onto stack
                            valueStack.push(array);
                            valueStack.push(new JSNumber(pos));

                        } catch (Exception e) {
                            throw new JSVirtualMachineException("APPEND: error iterating: " + e.getMessage(), e);
                        }

                        pc += op.getSize();
                    }
                    case DEFINE_ARRAY_EL -> {
                        // Define array element
                        // Stack: array idx val -> array idx
                        // Based on QuickJS OP_define_array_el
                        JSValue value = valueStack.pop();
                        JSValue idxValue = valueStack.peek(0);  // Keep idx on stack
                        JSValue arrayValue = valueStack.peek(1); // Keep array on stack

                        if (!(arrayValue instanceof JSArray array)) {
                            throw new JSVirtualMachineException("DEFINE_ARRAY_EL: first argument must be an array");
                        }

                        if (!(idxValue instanceof JSNumber idxNum)) {
                            throw new JSVirtualMachineException("DEFINE_ARRAY_EL: second argument must be a number");
                        }

                        int idx = (int) idxNum.value();

                        // Set array element (this will update length automatically)
                        array.set(idx, value, context);

                        pc += op.getSize();
                    }
                    case DEFINE_PROP -> {
                        JSValue propValue = valueStack.pop();
                        JSValue propKey = valueStack.pop();
                        JSValue propObj = valueStack.peek(0);
                        if (propObj instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(context, propKey);
                            jsObj.set(key, propValue);
                        }
                        pc += op.getSize();
                    }
                    case SET_NAME -> {
                        int nameAtom = bytecode.readU32(pc + 1);
                        String name = bytecode.getAtoms()[nameAtom];
                        setObjectName(valueStack.peek(0), new JSString(name));
                        pc += op.getSize();
                    }
                    case SET_NAME_COMPUTED -> {
                        JSValue nameValue = valueStack.peek(1);
                        setObjectName(valueStack.peek(0), getComputedNameString(nameValue));
                        pc += op.getSize();
                    }
                    case SET_PROTO -> {
                        JSValue protoValue = valueStack.pop();
                        JSValue objectValue = valueStack.peek(0);
                        if (objectValue instanceof JSObject object) {
                            if (protoValue instanceof JSObject prototypeObject) {
                                object.setPrototype(prototypeObject);
                            } else if (protoValue.isNull()) {
                                object.setPrototype(null);
                            }
                        }
                        pc += op.getSize();
                    }
                    case SET_HOME_OBJECT -> {
                        JSValue homeObjectValue = valueStack.peek(1);
                        JSValue methodValue = valueStack.peek(0);
                        if (methodValue instanceof JSObject methodObject && homeObjectValue instanceof JSObject homeObject) {
                            methodObject.set(PropertyKey.fromString("[[HomeObject]]"), homeObject);
                        }
                        pc += op.getSize();
                    }
                    case COPY_DATA_PROPERTIES -> {
                        int mask = bytecode.readU8(pc + 1);
                        JSValue targetValue = valueStack.peek(mask & 3);
                        JSValue sourceValue = valueStack.peek((mask >> 2) & 7);
                        JSValue excludeListValue = valueStack.peek((mask >> 5) & 7);
                        copyDataProperties(targetValue, sourceValue, excludeListValue);
                        pc += op.getSize();
                    }
                    case DEFINE_CLASS -> {
                        // Stack: superClass constructor
                        // Reads: atom (class name)
                        // Result: proto constructor (pushes prototype object)
                        int classNameAtom = bytecode.readU32(pc + 1);
                        String className = bytecode.getAtoms()[classNameAtom];
                        JSValue constructor = valueStack.pop();
                        JSValue superClass = valueStack.pop();

                        if (!(constructor instanceof JSFunction constructorFunc)) {
                            throw new JSVirtualMachineException("DEFINE_CLASS: constructor must be a function");
                        }

                        // Create the class prototype object
                        JSObject prototype = context.createJSObject();

                        // Set up prototype chain
                        if (superClass != JSUndefined.INSTANCE && superClass != JSNull.INSTANCE) {
                            if (superClass instanceof JSFunction superFunc) {
                                // prototype.__proto__ = superFunc.prototype
                                context.transferPrototype(prototype, superFunc);
                                // constructor.__proto__ = superFunc (the parent constructor itself)
                                constructorFunc.setPrototype(superFunc);
                            }
                        }
                        // Set constructor.prototype = prototype
                        if (constructorFunc instanceof JSObject) {
                            constructorFunc.set(PropertyKey.fromString("prototype"), prototype);
                        }

                        // Set prototype.constructor = constructor
                        prototype.set(PropertyKey.fromString("constructor"), constructor);
                        setObjectName(constructor, new JSString(className));

                        // Push prototype and constructor onto stack
                        valueStack.push(prototype);
                        valueStack.push(constructor);
                        pc += op.getSize();
                    }
                    case DEFINE_CLASS_COMPUTED -> {
                        int classNameAtom = bytecode.readU32(pc + 1);
                        int classFlags = bytecode.readU8(pc + 5);
                        String className = bytecode.getAtoms()[classNameAtom];
                        JSValue constructor = valueStack.pop();
                        JSValue superClass = valueStack.pop();
                        JSValue computedClassNameValue = valueStack.peek(0);

                        if (!(constructor instanceof JSFunction constructorFunc)) {
                            throw new JSVirtualMachineException("DEFINE_CLASS_COMPUTED: constructor must be a function");
                        }

                        JSObject prototype = context.createJSObject();
                        boolean hasHeritage = (classFlags & 1) != 0;

                        if (hasHeritage && superClass != JSUndefined.INSTANCE && superClass != JSNull.INSTANCE) {
                            if (superClass instanceof JSFunction superFunc) {
                                // prototype.__proto__ = superFunc.prototype
                                context.transferPrototype(prototype, superFunc);
                                // constructor.__proto__ = superFunc (the parent constructor itself)
                                constructorFunc.setPrototype(superFunc);
                            } else {
                                throw new JSVirtualMachineException(context.throwTypeError("parent class must be constructor"));
                            }
                        }

                        constructorFunc.set(PropertyKey.fromString("prototype"), prototype);
                        prototype.set(PropertyKey.fromString("constructor"), constructor);
                        JSString computedClassName = getComputedNameString(computedClassNameValue);
                        if (computedClassName.value().isEmpty()) {
                            computedClassName = new JSString(className);
                        }
                        setObjectName(constructor, computedClassName);

                        valueStack.push(prototype);
                        valueStack.push(constructor);
                        pc += op.getSize();
                    }
                    case DEFINE_METHOD -> {
                        // Stack: obj method
                        // Reads: atom (method name)
                        // Result: obj (pops both, adds method to obj, pushes obj back)
                        int methodNameAtom = bytecode.readU32(pc + 1);
                        String methodName = bytecode.getAtoms()[methodNameAtom];
                        JSValue method = valueStack.pop();  // Pop method
                        JSValue obj = valueStack.pop();     // Pop obj

                        if (obj instanceof JSObject jsObj) {
                            jsObj.set(PropertyKey.fromString(methodName), method);
                        }

                        valueStack.push(obj);  // Push obj back
                        pc += op.getSize();
                    }
                    case DEFINE_METHOD_COMPUTED -> {
                        int methodFlags = bytecode.readU8(pc + 1);
                        boolean enumerable = (methodFlags & 4) != 0;
                        int methodKind = methodFlags & 3;

                        JSValue methodValue = valueStack.pop();
                        JSValue propertyValue = valueStack.pop();
                        JSValue objectValue = valueStack.peek(0);

                        if (objectValue instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(context, propertyValue);
                            JSString computedName = getComputedNameString(propertyValue);
                            if (methodValue instanceof JSObject methodObject) {
                                methodObject.set(PropertyKey.fromString("[[HomeObject]]"), jsObj);
                                String namePrefix = switch (methodKind) {
                                    case 1 -> "get ";
                                    case 2 -> "set ";
                                    default -> "";
                                };
                                setObjectName(methodObject, new JSString(namePrefix + computedName.value()));
                            }

                            switch (methodKind) {
                                case 0 -> jsObj.defineProperty(
                                        key,
                                        PropertyDescriptor.dataDescriptor(methodValue, true, enumerable, true));
                                case 1 -> {
                                    JSFunction getter = methodValue instanceof JSFunction jsFunction ? jsFunction : null;
                                    JSFunction setter = null;
                                    PropertyDescriptor descriptor = jsObj.getOwnPropertyDescriptor(key);
                                    if (descriptor != null && descriptor.hasSetter()) {
                                        setter = descriptor.getSetter();
                                    }
                                    jsObj.defineProperty(
                                            key,
                                            PropertyDescriptor.accessorDescriptor(getter, setter, enumerable, true));
                                }
                                case 2 -> {
                                    JSFunction setter = methodValue instanceof JSFunction jsFunction ? jsFunction : null;
                                    JSFunction getter = null;
                                    PropertyDescriptor descriptor = jsObj.getOwnPropertyDescriptor(key);
                                    if (descriptor != null && descriptor.hasGetter()) {
                                        getter = descriptor.getGetter();
                                    }
                                    jsObj.defineProperty(
                                            key,
                                            PropertyDescriptor.accessorDescriptor(getter, setter, enumerable, true));
                                }
                                default ->
                                        throw new JSVirtualMachineException("DEFINE_METHOD_COMPUTED: unsupported method flags " + methodFlags);
                            }
                        }

                        pc += op.getSize();
                    }
                    case DEFINE_FIELD -> {
                        // Stack: obj value
                        // Reads: atom (field name)
                        // Result: obj (pops both, adds field to obj, pushes obj back)
                        int fieldNameAtom = bytecode.readU32(pc + 1);
                        String fieldName = bytecode.getAtoms()[fieldNameAtom];
                        JSValue value = valueStack.pop();   // Pop value
                        JSValue obj = valueStack.pop();     // Pop obj

                        if (obj instanceof JSObject jsObj) {
                            jsObj.set(PropertyKey.fromString(fieldName), value);
                        }

                        valueStack.push(obj);  // Push obj back
                        pc += op.getSize();
                    }
                    case DEFINE_PRIVATE_FIELD -> {
                        // Stack: obj privateSymbol value
                        // Result: obj (pops privateSymbol and value, adds private field to obj, pushes obj back)
                        JSValue value = valueStack.pop();           // Pop value
                        JSValue privateSymbol = valueStack.pop();   // Pop private symbol
                        JSValue obj = valueStack.pop();             // Pop obj

                        if (obj instanceof JSObject jsObj && privateSymbol instanceof JSSymbol symbol) {
                            // Set the private field using the symbol as the key
                            jsObj.set(PropertyKey.fromSymbol(symbol), value);
                        }

                        valueStack.push(obj);  // Push obj back
                        pc += op.getSize();
                    }
                    case GET_PRIVATE_FIELD -> {
                        // Stack: obj privateSymbol
                        // Result: value (pops both, gets value from obj using privateSymbol)
                        JSValue privateSymbol = valueStack.pop();  // Pop private symbol
                        JSValue obj = valueStack.pop();            // Pop obj

                        JSValue value = JSUndefined.INSTANCE;
                        if (obj instanceof JSObject jsObj && privateSymbol instanceof JSSymbol symbol) {
                            value = jsObj.get(PropertyKey.fromSymbol(symbol));
                        }

                        valueStack.push(value);
                        pc += op.getSize();
                    }
                    case PUT_PRIVATE_FIELD -> {
                        // Stack: obj value privateSymbol
                        // Result: value (pops obj and privateSymbol, leaves value as assignment result)
                        JSValue privateSymbol = valueStack.pop();  // Pop private symbol
                        JSValue value = valueStack.pop();          // Pop value
                        JSValue obj = valueStack.pop();            // Pop obj

                        if (obj instanceof JSObject jsObj && privateSymbol instanceof JSSymbol symbol) {
                            jsObj.set(PropertyKey.fromSymbol(symbol), value);
                        }

                        // Push value back to stack (assignment expressions return the assigned value)
                        valueStack.push(value);

                        pc += op.getSize();
                    }

                    // ==================== Exception Handling ====================
                    case THROW -> {
                        JSValue exception = valueStack.pop();
                        pendingException = exception;
                        context.setPendingException(exception);
                        // Don't throw immediately - let the exception handling loop unwind the stack
                        // This matches QuickJS behavior: goto exception;
                        // Don't advance PC - let the exception handler deal with it
                    }
                    case CATCH -> {
                        // QuickJS: pushes catch offset marker onto stack
                        // This marker is used during exception unwinding to find the catch handler
                        int catchOffset = bytecode.readI32(pc + 1);
                        int catchHandlerPC = pc + op.getSize() + catchOffset;
                        valueStack.pushStackValue(new JSCatchOffset(catchHandlerPC));
                        pc += op.getSize();
                    }
                    case NIP_CATCH -> {
                        JSValue returnValue = valueStack.pop();
                        boolean foundCatchMarker = false;
                        while (valueStack.getStackTop() > savedStackTop) {
                            JSStackValue stackValue = valueStack.popStackValue();
                            if (stackValue instanceof JSCatchOffset) {
                                foundCatchMarker = true;
                                break;
                            }
                        }
                        if (!foundCatchMarker) {
                            throw new JSVirtualMachineException(context.throwError("nip_catch"));
                        }
                        valueStack.push(returnValue);
                        pc += op.getSize();
                    }

                    // ==================== Type Operations ====================
                    case TO_STRING -> {
                        JSValue value = valueStack.peek(0);
                        if (!(value instanceof JSString)) {
                            valueStack.set(0, JSTypeConversions.toString(context, value));
                        }
                        pc += op.getSize();
                    }
                    case TYPEOF -> {
                        handleTypeof();
                        pc += op.getSize();
                    }
                    case DELETE -> {
                        handleDelete();
                        pc += op.getSize();
                    }
                    case IS_UNDEFINED_OR_NULL -> {
                        handleIsUndefinedOrNull();
                        pc += op.getSize();
                    }
                    case IS_UNDEFINED -> {
                        JSValue value = valueStack.peek(0);
                        valueStack.set(0, JSBoolean.valueOf(value.isUndefined()));
                        pc += op.getSize();
                    }
                    case IS_NULL -> {
                        JSValue value = valueStack.peek(0);
                        valueStack.set(0, JSBoolean.valueOf(value.isNull()));
                        pc += op.getSize();
                    }
                    case TYPEOF_IS_UNDEFINED -> {
                        JSValue value = valueStack.peek(0);
                        valueStack.set(0, JSBoolean.valueOf("undefined".equals(JSTypeChecking.typeof(value))));
                        pc += op.getSize();
                    }
                    case TYPEOF_IS_FUNCTION -> {
                        JSValue value = valueStack.peek(0);
                        valueStack.set(0, JSBoolean.valueOf("function".equals(JSTypeChecking.typeof(value))));
                        pc += op.getSize();
                    }

                    // ==================== Async Operations ====================
                    case ITERATOR_CHECK_OBJECT -> {
                        JSValue iteratorResult = valueStack.peek(0);
                        if (!(iteratorResult instanceof JSObject)) {
                            throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
                        }
                        pc += op.getSize();
                    }
                    case ITERATOR_GET_VALUE_DONE -> {
                        JSValue iteratorResult = valueStack.peek(0);
                        if (!(iteratorResult instanceof JSObject iteratorResultObject)) {
                            throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
                        }

                        JSValue doneValue = iteratorResultObject.get(PropertyKey.fromString("done"));
                        JSValue value = iteratorResultObject.get(PropertyKey.fromString("value"));
                        if (value == null) {
                            value = JSUndefined.INSTANCE;
                        }
                        boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();

                        valueStack.set(0, value);
                        valueStack.set(1, new JSNumber(0));
                        valueStack.push(JSBoolean.valueOf(done));
                        pc += op.getSize();
                    }
                    case ITERATOR_CLOSE -> {
                        valueStack.pop(); // catch_offset
                        valueStack.pop(); // next method
                        JSValue iteratorValue = valueStack.pop();
                        if (iteratorValue instanceof JSObject iteratorObject && !iteratorValue.isUndefined()) {
                            JSValue returnMethodValue = iteratorObject.get(PropertyKey.fromString("return"));
                            if (returnMethodValue instanceof JSFunction returnMethod) {
                                returnMethod.call(context, iteratorObject, new JSValue[0]);
                                if (context.hasPendingException()) {
                                    pendingException = context.getPendingException();
                                    context.clearPendingException();
                                }
                            } else if (!(returnMethodValue.isUndefined() || returnMethodValue.isNull())) {
                                throw new JSVirtualMachineException(context.throwTypeError("iterator return is not a function"));
                            }
                        }
                        pc += op.getSize();
                    }
                    case ITERATOR_NEXT -> {
                        JSValue argumentValue = valueStack.peek(0);
                        JSValue catchOffset = valueStack.peek(1);
                        JSValue nextMethodValue = valueStack.peek(2);
                        JSValue iteratorValue = valueStack.peek(3);
                        if (!(nextMethodValue instanceof JSFunction nextMethod)) {
                            throw new JSVirtualMachineException(context.throwTypeError("iterator next is not a function"));
                        }
                        JSValue nextResult = nextMethod.call(context, iteratorValue, new JSValue[]{argumentValue});
                        valueStack.set(0, nextResult);
                        valueStack.set(1, catchOffset);
                        pc += op.getSize();
                    }
                    case ITERATOR_CALL -> {
                        int flags = bytecode.readU8(pc + 1);
                        JSValue argumentValue = valueStack.peek(0);
                        JSValue iteratorValue = valueStack.peek(3);
                        if (!(iteratorValue instanceof JSObject iteratorObject)) {
                            throw new JSVirtualMachineException(context.throwTypeError("iterator call target must be an object"));
                        }

                        String methodName = (flags & 1) != 0 ? "throw" : "return";
                        JSValue methodValue = iteratorObject.get(PropertyKey.fromString(methodName));
                        boolean noMethod = methodValue.isUndefined() || methodValue.isNull();
                        if (!noMethod) {
                            if (!(methodValue instanceof JSFunction method)) {
                                throw new JSVirtualMachineException(context.throwTypeError("iterator " + methodName + " is not a function"));
                            }
                            JSValue callResult = (flags & 2) != 0
                                    ? method.call(context, iteratorObject, new JSValue[0])
                                    : method.call(context, iteratorObject, new JSValue[]{argumentValue});
                            valueStack.set(0, callResult);
                        }
                        valueStack.push(JSBoolean.valueOf(noMethod));
                        pc += op.getSize();
                    }
                    case AWAIT -> {
                        handleAwait();
                        pc += op.getSize();
                    }
                    case FOR_AWAIT_OF_START -> {
                        handleForAwaitOfStart();
                        pc += op.getSize();
                    }
                    case FOR_AWAIT_OF_NEXT -> {
                        handleForAwaitOfNext();
                        pc += op.getSize();
                    }
                    case FOR_OF_START -> {
                        handleForOfStart();
                        pc += op.getSize();
                    }
                    case FOR_OF_NEXT -> {
                        int depth = bytecode.readU8(pc + 1);  // Read the depth parameter
                        handleForOfNext(depth);
                        pc += op.getSize();
                    }
                    case FOR_IN_START -> {
                        handleForInStart();
                        pc += op.getSize();
                    }
                    case FOR_IN_NEXT -> {
                        handleForInNext();
                        pc += op.getSize();
                    }
                    case FOR_IN_END -> {
                        handleForInEnd();
                        pc += op.getSize();
                    }

                    // ==================== Generator Operations ====================
                    case INITIAL_YIELD -> {
                        handleInitialYield();
                        pc += op.getSize();
                        // Initial yield doesn't suspend - generator creation continues
                    }
                    case YIELD -> {
                        handleYield();
                        pc += op.getSize();
                        // Check if we should suspend (generator yielded)
                        if (yieldResult != null) {
                            // Return the yielded value - execution will resume here on next()
                            JSValue returnValue = valueStack.pop();
                            valueStack.setStackTop(savedStackTop);
                            currentFrame = previousFrame;
                            if (savedStrictMode) {
                                context.enterStrictMode();
                            } else {
                                context.exitStrictMode();
                            }
                            return returnValue;
                        }
                    }
                    case YIELD_STAR -> {
                        handleYieldStar();
                        pc += op.getSize();
                        // Check if we should suspend
                        if (yieldResult != null) {
                            JSValue returnValue = valueStack.pop();
                            valueStack.setStackTop(savedStackTop);
                            currentFrame = previousFrame;
                            if (savedStrictMode) {
                                context.enterStrictMode();
                            } else {
                                context.exitStrictMode();
                            }
                            return returnValue;
                        }
                    }
                    case ASYNC_YIELD_STAR -> {
                        handleAsyncYieldStar();
                        pc += op.getSize();
                    }
                    case NOP -> pc += op.getSize();

                    // ==================== Other Operations ====================
                    default -> throw new JSVirtualMachineException("Unimplemented opcode: " + op + " at PC " + pc);
                }
            }
        } catch (JSVirtualMachineException e) {
            // Restore stack and strict mode on exception
            valueStack.setStackTop(savedStackTop);
            currentFrame = previousFrame;
            resetPropertyAccessTracking();
            if (savedStrictMode) {
                context.enterStrictMode();
            } else {
                context.exitStrictMode();
            }
            throw e;
        } catch (Exception e) {
            // Restore stack and strict mode on exception
            valueStack.setStackTop(savedStackTop);
            currentFrame = previousFrame;
            resetPropertyAccessTracking();
            if (savedStrictMode) {
                context.enterStrictMode();
            } else {
                context.exitStrictMode();
            }
            throw new JSVirtualMachineException("VM error: " + e.getMessage(), e);
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

        // Set yield skip count - we'll skip this many yields to resume from the right place
        // This is a workaround since we're not saving/restoring PC
        yieldSkipCount = state.getYieldCount();
        generatorResumeRecords = state.getResumeRecords();
        generatorResumeIndex = 0;

        // Execute (or resume) the generator
        JSValue result = execute(function, thisArg, args);

        // Check if generator yielded
        if (yieldResult != null) {
            // Generator yielded - increment count and update state
            state.incrementYieldCount();
            state.setState(JSGeneratorState.State.SUSPENDED_YIELD);
            return yieldResult.value();
        } else {
            // Generator completed (returned)
            state.setCompleted(true);
            return result;
        }
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

        // String concatenation or numeric addition
        if (left instanceof JSString || right instanceof JSString) {
            String leftStr = JSTypeConversions.toString(context, left).value();
            String rightStr = JSTypeConversions.toString(context, right).value();
            valueStack.push(new JSString(leftStr + rightStr));
        } else {
            double leftNum = JSTypeConversions.toNumber(context, left).value();
            double rightNum = JSTypeConversions.toNumber(context, right).value();
            valueStack.push(new JSNumber(leftNum + rightNum));
        }
    }

    private void handleAnd() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int result = JSTypeConversions.toInt32(context, left) & JSTypeConversions.toInt32(context, right);
        valueStack.push(new JSNumber(result));
    }

    private void handleAsyncYieldStar() {
        // Keep the same suspension model as sync yield* in the current generator runtime.
        // Full async delegation semantics can be layered on top of this baseline.
        handleYieldStar();
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
            promise = new JSPromise();
            promise.fulfill(value);
        }

        // For proper async/await support, we need to wait for the promise to settle
        // and push the resolved value (not the promise itself)

        // If the promise is pending, we need to process microtasks until it settles
        if (promise.getState() == JSPromise.PromiseState.PENDING) {
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
            JSPromiseRejectCallback callback = context.getPromiseRejectCallback();
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

    private void handleCall(int argCount) {
        // Stack layout (bottom to top): method, receiver, arg1, arg2, ...
        // Pop arguments from stack
        JSValue[] args = new JSValue[argCount];
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
            if (function instanceof JSNativeFunction nativeFunc) {
                // Check if this function requires 'new'
                if (nativeFunc.requiresNew()) {
                    String constructorName = nativeFunc.getName() != null ? nativeFunc.getName() : "constructor";
                    resetPropertyAccessTracking();
                    String errorMessage = switch (constructorName) {
                        case JSPromise.NAME -> "Promise constructor cannot be invoked without 'new'";
                        default -> "Constructor " + constructorName + " requires 'new'";
                    };
                    throw new JSVirtualMachineException(context.throwTypeError(errorMessage));
                }
                // Call native function with receiver as thisArg
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
                    if (e.getJsError() != null) {
                        pendingException = e.getJsError();
                    } else if (context.hasPendingException()) {
                        pendingException = context.getPendingException();
                    } else {
                        pendingException = context.throwError("Error",
                                e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                    }
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
                    if (e.getJsError() != null) {
                        pendingException = e.getJsError();
                    } else if (context.hasPendingException()) {
                        pendingException = context.getPendingException();
                    } else {
                        pendingException = context.throwError("Error",
                                e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                    }
                    valueStack.push(JSUndefined.INSTANCE);
                }
            } else {
                valueStack.push(JSUndefined.INSTANCE);
            }
            // Clear property access tracking after successful call
            resetPropertyAccessTracking();
        } else {
            // Not a function - throw TypeError
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
            throw new JSVirtualMachineException(context.throwTypeError(message));
        }
    }

    private void handleCallConstructor(int argCount) {
        // Pop arguments
        JSValue[] args = new JSValue[argCount];
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
            JSValue result = constructFunction(jsFunction, args);
            if (context.hasPendingException()) {
                pendingException = context.getPendingException();
                valueStack.push(JSUndefined.INSTANCE);
            } else {
                valueStack.push(result);
            }
        } else {
            throw new JSVirtualMachineException("Cannot construct non-function value");
        }
    }

    private JSValue[] buildApplyArguments(JSValue argsArrayValue, boolean allowNullOrUndefined) {
        if (allowNullOrUndefined && (argsArrayValue.isUndefined() || argsArrayValue.isNull())) {
            return new JSValue[0];
        }
        if (!(argsArrayValue instanceof JSObject arrayLike)) {
            context.throwTypeError("CreateListFromArrayLike called on non-object");
            return null;
        }

        JSValue lengthValue = arrayLike.get(PropertyKey.fromString("length"), context);
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
            JSValue argValue = arrayLike.get(PropertyKey.fromString(String.valueOf(i)), context);
            if (context.hasPendingException()) {
                return null;
            }
            if (argValue instanceof JSUndefined) {
                argValue = arrayLike.get(PropertyKey.fromIndex(i), context);
                if (context.hasPendingException()) {
                    return null;
                }
            }
            args[i] = argValue;
        }
        return args;
    }

    private void handleDec() {
        JSValue operand = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, operand).value() - 1;
        valueStack.push(new JSNumber(result));
    }

    private void handleDelete() {
        JSValue property = valueStack.pop();
        JSValue object = valueStack.pop();
        boolean result = false;
        if (object instanceof JSObject jsObj) {
            PropertyKey key = PropertyKey.fromValue(context, property);
            result = jsObj.delete(key, context);
        }
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleDiv() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, left).value() / JSTypeConversions.toNumber(context, right).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleEq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.abstractEquals(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleExp() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = Math.pow(JSTypeConversions.toNumber(context, left).value(), JSTypeConversions.toNumber(context, right).value());
        valueStack.push(new JSNumber(result));
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

        JSValue result = nextFunc.call(context, iterator, new JSValue[0]);

        // Restore catch_offset and push the result
        valueStack.push(catchOffset);  // Restore catch_offset
        valueStack.push(result);        // Push the result (promise that resolves to {value, done})
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
                throw new JSVirtualMachineException("Object is not async iterable");
            }
        }

        // First, try Symbol.asyncIterator
        JSValue asyncIteratorMethod = iterableObj.get(PropertyKey.fromSymbol(JSSymbol.ASYNC_ITERATOR));
        JSValue iteratorMethod = null;
        boolean isAsync = true;

        if (asyncIteratorMethod instanceof JSFunction) {
            iteratorMethod = asyncIteratorMethod;
        } else {
            // Fall back to Symbol.iterator (sync iterator that will be auto-wrapped)
            iteratorMethod = iterableObj.get(PropertyKey.fromSymbol(JSSymbol.ITERATOR));
            isAsync = false;

            if (!(iteratorMethod instanceof JSFunction)) {
                throw new JSVirtualMachineException("Object is not async iterable");
            }
        }

        // Call the iterator method to get an iterator
        JSValue iterator = ((JSFunction) iteratorMethod).call(context, iterable, new JSValue[0]);

        if (!(iterator instanceof JSObject iteratorObj)) {
            throw new JSVirtualMachineException("Iterator method must return an object");
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.fromString("next"));

        if (!(nextMethod instanceof JSFunction)) {
            throw new JSVirtualMachineException("Iterator must have a next method");
        }

        // Push iterator, next method, and catch offset (0) onto the stack
        valueStack.push(iterator);         // Iterator object
        valueStack.push(nextMethod);       // next() method
        valueStack.push(new JSNumber(0));  // Catch offset (placeholder)
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

    private void handleForInStart() {
        // Pop the object from the stack
        JSValue obj = valueStack.pop();

        // Create a for-in enumerator
        JSForInEnumerator enumerator = new JSForInEnumerator(obj);

        // Push the enumerator onto the stack (wrapped in a special internal object)
        // We'll use JSInternalValue to hold it
        valueStack.pushStackValue(new JSInternalValue(enumerator));
    }

    private void handleForOfNext(int depth) {
        // Stack layout: ... iter next catch_offset [depth values] (bottom to top)
        // The depth parameter tells us how many values are between catch_offset and top
        // Following QuickJS: offset = -3 - depth
        // iter is at sp[offset] = sp[-3-depth], next is at sp[offset+1] = sp[-2-depth]

        // Pop depth values temporarily
        List<JSValue> tempValues = new ArrayList<>(depth);
        for (int i = 0; i < depth; i++) {
            tempValues.add(valueStack.pop());
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

        JSValue result = nextFunc.call(context, iterator, new JSValue[0]);

        // For sync iterators, extract value and done from the result object
        // QuickJS FOR_OF_NEXT pushes: iter, next, catch_offset, value, done
        // So we need to extract {value, done} from result

        if (!(result instanceof JSObject resultObj)) {
            throw new JSVirtualMachineException("Iterator result must be an object");
        }

        // Get the value property
        JSValue value = resultObj.get("value");
        if (value == null) {
            value = JSUndefined.INSTANCE;
        }

        // Get the done property
        JSValue doneValue = resultObj.get("done");
        boolean done = false;
        if (doneValue instanceof JSBoolean boolVal) {
            done = boolVal.isBooleanTrue();
        }

        // Push catch_offset back, then restore temp values, then push value and done
        valueStack.push(catchOffset);
        for (int i = tempValues.size() - 1; i >= 0; i--) {
            valueStack.push(tempValues.get(i));
        }
        valueStack.push(value);
        valueStack.push(done ? JSBoolean.TRUE : JSBoolean.FALSE);
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
        JSValue iteratorMethod = iterableObj.get(PropertyKey.fromSymbol(JSSymbol.ITERATOR));

        if (!(iteratorMethod instanceof JSFunction iteratorFunc)) {
            throw new JSVirtualMachineException("Object is not iterable");
        }

        // Call the Symbol.iterator method to get an iterator
        // Use the original iterable value for the 'this' binding, not the boxed version
        JSValue iterator = iteratorFunc.call(context, iterable, new JSValue[0]);

        if (!(iterator instanceof JSObject iteratorObj)) {
            throw new JSVirtualMachineException("Iterator method must return an object");
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(PropertyKey.fromString("next"));

        if (!(nextMethod instanceof JSFunction)) {
            String actualType = nextMethod == null ? "null" : nextMethod.getClass().getSimpleName();
            throw new JSVirtualMachineException(
                    "Iterator must have a next method (got " + actualType + ", iterator=" + iteratorObj.getClass().getSimpleName() + ")"
            );
        }

        // Push iterator, next method, and catch offset (0) onto the stack
        valueStack.push(iterator);         // Iterator object
        valueStack.push(nextMethod);       // next() method
        valueStack.push(new JSNumber(0));  // Catch offset (placeholder)
    }

    private void handleGt() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.lessThan(context, right, left);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleGte() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.lessThan(context, right, left) ||
                JSTypeConversions.abstractEquals(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
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

    private void handleInc() {
        JSValue operand = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, operand).value() + 1;
        valueStack.push(new JSNumber(result));
    }

    private void handleInitCtor() {
        JSValue thisArg = currentFrame.getThisArg();
        if (!(thisArg instanceof JSObject)) {
            throw new JSVirtualMachineException(context.throwTypeError("class constructors must be invoked with 'new'"));
        }
        valueStack.push(thisArg);
    }

    private void handleInitialYield() {
        // Initial yield - generator is being created
        // In QuickJS, this is where generator creation stops and returns the generator object
        // For now, just continue - the generator state tracking will handle suspension
        // The yieldResult will be null, so executeGenerator won't mark it as yielding
    }

    private void handleInstanceof() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();

        // Per ECMAScript spec, right must be an object
        if (!(right instanceof JSObject constructor)) {
            throw new JSVirtualMachineException("Right-hand side of instanceof is not an object");
        }

        JSValue hasInstanceMethod = constructor.get(PropertyKey.fromSymbol(JSSymbol.HAS_INSTANCE), context);
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

    private void handleIsUndefinedOrNull() {
        JSValue value = valueStack.pop();
        boolean result = value instanceof JSNull || value instanceof JSUndefined;
        valueStack.push(result ? JSBoolean.TRUE : JSBoolean.FALSE);
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

    private void handleLogicalNot() {
        JSValue operand = valueStack.pop();
        boolean result = JSTypeConversions.toBoolean(operand) == JSBoolean.FALSE;
        valueStack.push(JSBoolean.valueOf(result));
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

    private void handleLt() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.lessThan(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleLte() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.lessThan(context, left, right) ||
                JSTypeConversions.abstractEquals(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleMod() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, left).value() % JSTypeConversions.toNumber(context, right).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleMul() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, left).value() * JSTypeConversions.toNumber(context, right).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleNeg() {
        JSValue operand = valueStack.pop();
        double result = -JSTypeConversions.toNumber(context, operand).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleNeq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = !JSTypeConversions.abstractEquals(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleNot() {
        JSValue operand = valueStack.pop();
        int result = ~JSTypeConversions.toInt32(context, operand);
        valueStack.push(new JSNumber(result));
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

    private void handleOr() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int result = JSTypeConversions.toInt32(context, left) | JSTypeConversions.toInt32(context, right);
        valueStack.push(new JSNumber(result));
    }

    private void handlePlus() {
        JSValue operand = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, operand).value();
        valueStack.push(new JSNumber(result));
    }

    private void handlePostDec() {
        // POST_DEC: [value] -> [old_value, new_value]
        // Takes value on top, pushes old value then new value
        JSValue operand = valueStack.pop();
        double oldValue = JSTypeConversions.toNumber(context, operand).value();
        double newValue = oldValue - 1;
        valueStack.push(new JSNumber(oldValue));
        valueStack.push(new JSNumber(newValue));
    }

    private void handlePostInc() {
        // POST_INC: [value] -> [old_value, new_value]
        // Takes value on top, pushes old value then new value
        JSValue operand = valueStack.pop();
        double oldValue = JSTypeConversions.toNumber(context, operand).value();
        double newValue = oldValue + 1;
        valueStack.push(new JSNumber(oldValue));
        valueStack.push(new JSNumber(newValue));
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

    private void handleSar() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int leftInt = JSTypeConversions.toInt32(context, left);
        int rightInt = JSTypeConversions.toInt32(context, right);
        valueStack.push(new JSNumber(leftInt >> (rightInt & 0x1F)));
    }

    private void handleShl() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int leftInt = JSTypeConversions.toInt32(context, left);
        int rightInt = JSTypeConversions.toInt32(context, right);
        valueStack.push(new JSNumber(leftInt << (rightInt & 0x1F)));
    }

    private void handleShr() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int leftInt = JSTypeConversions.toInt32(context, left);
        int rightInt = JSTypeConversions.toInt32(context, right);
        valueStack.push(new JSNumber((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
    }

    private void handleStrictEq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleStrictNeq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = !JSTypeConversions.strictEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleSub() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, left).value() - JSTypeConversions.toNumber(context, right).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleTypeof() {
        JSValue operand = valueStack.pop();
        String type = JSTypeChecking.typeof(operand);
        valueStack.push(new JSString(type));
    }

    private void handleXor() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int result = JSTypeConversions.toInt32(context, left) ^ JSTypeConversions.toInt32(context, right);
        valueStack.push(new JSNumber(result));
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

        // Pop the yielded value from stack
        JSValue value = valueStack.pop();

        // Create yield result to signal suspension
        yieldResult = new YieldResult(YieldResult.Type.YIELD, value);

        // Push the value back so it can be returned
        valueStack.push(value);
    }

    private void handleYieldStar() {
        // TODO: Implement yield* (delegating yield)
        JSValue value = valueStack.pop();
        yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, value);
        valueStack.push(value);
    }

    private boolean isUninitialized(JSValue value) {
        return value == UNINITIALIZED_MARKER;
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

        JSValue prototypeValue = constructorObject.get(PropertyKey.fromString("prototype"), context);
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
    private JSValue proxyConstruct(JSProxy proxy, JSValue[] args, JSValue newTarget) {
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
            if (target instanceof JSProxy targetProxy) {
                return proxyConstruct(targetProxy, args, newTarget);
            }

            JSObject instance = new JSObject();
            if (target instanceof JSObject targetObj) {
                context.transferPrototype(instance, targetObj);
            }

            JSValue result;
            if (target instanceof JSNativeFunction nativeFunc) {
                result = nativeFunc.call(context, instance, args);
            } else if (target instanceof JSBytecodeFunction bytecodeFunc) {
                result = execute(bytecodeFunc, instance, args);
            } else if (target instanceof JSFunction targetFunc) {
                result = targetFunc.call(context, instance, args);
            } else {
                return instance;
            }

            if (result instanceof JSObject) {
                return result;
            } else {
                return instance;
            }
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

    private void resetPropertyAccessTracking() {
        this.propertyAccessChain.setLength(0);
        this.propertyAccessLock = false;
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
            JSValue messageValue = exceptionObj.get(PropertyKey.fromString("message"), null);
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
            JSValue nameValue = exceptionObj.get(PropertyKey.fromString("name"), null);
            if (nameValue instanceof JSString nameStr) {
                return nameStr.value();
            }
        } catch (Exception e) {
            // Ignore errors when getting name
        }

        // Fall back to Java toString
        return exceptionObj.toString();
    }

    private void setArgumentValue(int index, JSValue value) {
        JSValue[] arguments = currentFrame.getArguments();
        if (index >= 0 && index < arguments.length) {
            arguments[index] = value;
        }
        // Keep local mirror in sync for argument slots copied into locals.
        setLocalValue(index, value);
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
        object.definePropertyConfigurable("name", nameValue);
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

        // Auto-box primitives
        if (value instanceof JSString str) {
            // Get String.prototype from global object
            JSObject global = context.getGlobalObject();
            JSValue stringCtor = global.get("String");
            if (stringCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get("prototype");
                if (prototype instanceof JSObject protoObj) {
                    // Create a temporary wrapper object with String.prototype
                    JSObject wrapper = new JSObject();
                    wrapper.setPrototype(protoObj);
                    // Store the primitive value
                    wrapper.setPrimitiveValue(str);
                    // Add length property as own property (shadows prototype's length)
                    // This is a data property with the actual string length
                    wrapper.definePropertyReadonlyNonConfigurable("length", new JSNumber(str.value().length()));
                    return wrapper;
                }
            }
        }

        if (value instanceof JSNumber num) {
            // Get Number.prototype from global object
            JSObject global = context.getGlobalObject();
            JSValue numberCtor = global.get("Number");
            if (numberCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get("prototype");
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
            JSObject global = context.getGlobalObject();
            JSValue booleanCtor = global.get("Boolean");
            if (booleanCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get("prototype");
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
            JSObject global = context.getGlobalObject();
            JSValue bigIntCtor = global.get("BigInt");
            if (bigIntCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get("prototype");
                if (prototype instanceof JSObject protoObj) {
                    JSBigIntObject wrapper = new JSBigIntObject(bigInt);
                    wrapper.setPrototype(protoObj);
                    return wrapper;
                }
            }
        }

        if (value instanceof JSSymbol sym) {
            // Get Symbol.prototype from global object
            JSObject global = context.getGlobalObject();
            JSValue symbolCtor = global.get("Symbol");
            if (symbolCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get("prototype");
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
            return new JSNumber(key.asIndex());
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
}
