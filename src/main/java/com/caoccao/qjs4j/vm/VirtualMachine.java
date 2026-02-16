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
    private static final JSValue[] EMPTY_ARGS = new JSValue[0];
    private static final int INTERRUPT_CHECK_INTERVAL = 0xFFFF; // Check every ~65K opcodes
    private static final PropertyKey KEY_CONSTRUCTOR = PropertyKey.fromString("constructor");
    private static final PropertyKey KEY_DONE = PropertyKey.fromString("done");
    private static final PropertyKey KEY_HOME_OBJECT = PropertyKey.fromString("[[HomeObject]]");
    private static final PropertyKey KEY_LENGTH = PropertyKey.fromString("length");
    private static final PropertyKey KEY_MESSAGE = PropertyKey.fromString("message");
    private static final PropertyKey KEY_NAME = PropertyKey.fromString("name");
    private static final PropertyKey KEY_NEXT = PropertyKey.fromString("next");
    private static final PropertyKey KEY_PROTOTYPE = PropertyKey.fromString("prototype");
    private static final PropertyKey KEY_RETURN = PropertyKey.fromString("return");
    private static final PropertyKey KEY_THROW = PropertyKey.fromString("throw");
    private static final PropertyKey KEY_VALUE = PropertyKey.fromString("value");
    private static final JSObject UNINITIALIZED_MARKER = new JSObject();
    private final JSContext context;
    private final Set<JSObject> initializedConstantObjects;
    private final StringBuilder propertyAccessChain;  // Track last property access for better error messages
    private final boolean trackPropertyAccess;
    private final CallStack valueStack;
    private StackFrame currentFrame;
    private long executionDeadline;  // 0 = no deadline
    private long executionDeadlineNanos; // 0 = no deadline
    private JSValue[] forOfTempValues;
    private int generatorResumeIndex;
    private List<JSGeneratorState.ResumeRecord> generatorResumeRecords;
    private int interruptCounter;
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
        this.trackPropertyAccess = !"false".equalsIgnoreCase(System.getProperty("qjs4j.vm.trackPropertyAccess", "true"));
        this.propertyAccessLock = false;
        this.yieldResult = null;
        this.yieldSkipCount = 0;
        this.executionDeadline = 0;
        this.executionDeadlineNanos = 0;
        this.interruptCounter = 0;
        this.forOfTempValues = EMPTY_ARGS;
    }

    private JSValue[] buildApplyArguments(JSValue argsArrayValue, boolean allowNullOrUndefined) {
        if (allowNullOrUndefined && (argsArrayValue.isUndefined() || argsArrayValue.isNull())) {
            return EMPTY_ARGS;
        }
        if (!(argsArrayValue instanceof JSObject arrayLike)) {
            context.throwTypeError("CreateListFromArrayLike called on non-object");
            return null;
        }

        JSValue lengthValue = arrayLike.get(KEY_LENGTH, context);
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
            JSValue argValue = arrayLike.get(PropertyKey.fromIndex(i), context);
            if (context.hasPendingException()) {
                return null;
            }
            args[i] = argValue;
        }
        return args;
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

        Set<PropertyKey> excludedKeys = null;
        if (excludeListValue instanceof JSArray excludeArray) {
            excludedKeys = new HashSet<>();
            for (int i = 0; i < excludeArray.getLength(); i++) {
                excludedKeys.add(PropertyKey.fromValue(context, excludeArray.get(i)));
            }
        } else if (excludeListValue instanceof JSObject excludeObject) {
            excludedKeys = new HashSet<>();
            for (PropertyKey key : excludeObject.ownPropertyKeys()) {
                JSValue excludedValue = excludeObject.get(key, context);
                excludedKeys.add(PropertyKey.fromValue(context, excludedValue));
            }
        } else if (excludeListValue != null && !excludeListValue.isUndefined() && !excludeListValue.isNull()) {
            excludedKeys = new HashSet<>();
            excludedKeys.add(PropertyKey.fromValue(context, excludeListValue));
        }

        for (PropertyKey key : sourceObject.ownPropertyKeys()) {
            PropertyDescriptor descriptor = sourceObject.getOwnPropertyDescriptor(key);
            if (descriptor == null || !descriptor.isEnumerable() || (excludedKeys != null && excludedKeys.contains(key))) {
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
                JSArguments argsObj = new JSArguments(context, args, isStrict, isStrict ? null : targetFunc);
                context.transferPrototype(argsObj, JSObject.NAME);
                return argsObj;

            case 1: // SPECIAL_OBJECT_MAPPED_ARGUMENTS
                // Legacy mapped arguments (shares with function parameters)
                // For now, treat same as normal arguments
                // TODO: Implement parameter mapping for non-strict mode
                JSValue[] mappedArgs = currentFrame.getArguments();
                JSFunction mappedFunc = currentFrame.getFunction();
                JSArguments mappedArgsObj = new JSArguments(context, mappedArgs, false, mappedFunc);
                context.transferPrototype(mappedArgsObj, JSObject.NAME);
                return mappedArgsObj;

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
            byte[] ins = bytecode.getInstructions();
            Opcode[] decodedOpcodes = bytecode.getDecodedOpcodes();
            byte[] opcodeRebaseOffsets = bytecode.getOpcodeRebaseOffsets();
            JSValue[] locals = frame.getLocals();
            JSStackValue[] stack = valueStack.stack;
            int sp = valueStack.stackTop;
            int pc = 0;

            // Main execution loop
            while (true) {
                // Sync local sp to valueStack at top of each iteration.
                // This ensures cold opcodes (which use valueStack directly) see the correct stackTop.
                valueStack.stackTop = sp;

                if (pendingException != null) {
                    JSValue exception = pendingException;
                    pendingException = null;

                    boolean foundHandler = false;
                    while (valueStack.stackTop > savedStackTop) {
                        JSStackValue val = stack[--valueStack.stackTop];
                        if (val instanceof JSCatchOffset catchOffset) {
                            stack[valueStack.stackTop++] = exception;
                            pc = catchOffset.offset();
                            foundHandler = true;
                            context.clearPendingException();
                            break;
                        }
                    }
                    sp = valueStack.stackTop;

                    if (!foundHandler) {
                        currentFrame = previousFrame;
                        if (exception instanceof JSError jsError) {
                            throw new JSVirtualMachineException(jsError);
                        }
                        String exceptionMessage = safeExceptionToString(context, exception);
                        throw new JSVirtualMachineException("Unhandled exception: " + exceptionMessage, exception);
                    }

                    continue;
                }

                // Periodic interrupt check (every ~65K opcodes)
                if (executionDeadline != 0 && --interruptCounter <= 0) {
                    interruptCounter = INTERRUPT_CHECK_INTERVAL;
                    if (System.nanoTime() >= executionDeadlineNanos) {
                        throw new JSVirtualMachineException("execution timeout");
                    }
                }

                Opcode op = decodedOpcodes[pc];
                int rebase = opcodeRebaseOffsets[pc];
                if (op == null) {
                    int opcode = ins[pc] & 0xFF;
                    op = Opcode.fromInt(opcode);
                    if (op == Opcode.INVALID && pc + 1 < ins.length) {
                        int extendedOpcode = 0x100 + (ins[pc + 1] & 0xFF);
                        Opcode extendedOp = Opcode.fromInt(extendedOpcode);
                        if (extendedOp != Opcode.INVALID) {
                            op = extendedOp;
                            rebase = 1;
                        }
                    }
                }
                if (rebase != 0) {
                    pc += rebase;
                }

                switch (op) {
                    // ==================== Constants and Literals ====================
                    case INVALID -> throw new JSVirtualMachineException("Invalid opcode at PC " + pc);
                    case PUSH_I32 -> {
                        int i32val = ((ins[pc + 1] & 0xFF) << 24) | ((ins[pc + 2] & 0xFF) << 16) |
                                ((ins[pc + 3] & 0xFF) << 8) | (ins[pc + 4] & 0xFF);
                        stack[sp++] = JSNumber.of(i32val);
                        pc += 5;
                    }
                    case PUSH_BIGINT_I32 -> {
                        stack[sp++] = new JSBigInt(bytecode.readI32(pc + 1));
                        pc += op.getSize();
                    }
                    case PUSH_MINUS1 -> {
                        stack[sp++] = JSNumber.of(-1);
                        pc += 1;
                    }
                    case PUSH_0 -> {
                        stack[sp++] = JSNumber.of(0);
                        pc += 1;
                    }
                    case PUSH_1 -> {
                        stack[sp++] = JSNumber.of(1);
                        pc += 1;
                    }
                    case PUSH_2 -> {
                        stack[sp++] = JSNumber.of(2);
                        pc += 1;
                    }
                    case PUSH_3 -> {
                        stack[sp++] = JSNumber.of(3);
                        pc += 1;
                    }
                    case PUSH_4 -> {
                        stack[sp++] = JSNumber.of(4);
                        pc += 1;
                    }
                    case PUSH_5 -> {
                        stack[sp++] = JSNumber.of(5);
                        pc += 1;
                    }
                    case PUSH_6 -> {
                        stack[sp++] = JSNumber.of(6);
                        pc += 1;
                    }
                    case PUSH_7 -> {
                        stack[sp++] = JSNumber.of(7);
                        pc += 1;
                    }
                    case PUSH_I8 -> {
                        stack[sp++] = JSNumber.of(ins[pc + 1]);
                        pc += 2;
                    }
                    case PUSH_I16 -> {
                        stack[sp++] = JSNumber.of((short) (((ins[pc + 1] & 0xFF) << 8) | (ins[pc + 2] & 0xFF)));
                        pc += 3;
                    }
                    case PUSH_CONST -> {
                        int constIndex = ((ins[pc + 1] & 0xFF) << 24) | ((ins[pc + 2] & 0xFF) << 16) |
                                ((ins[pc + 3] & 0xFF) << 8) | (ins[pc + 4] & 0xFF);
                        JSValue constValue = bytecode.getConstants()[constIndex];

                        if (constValue instanceof JSFunction func) {
                            func.initializePrototypeChain(context);
                        } else if (constValue instanceof JSObject jsObject) {
                            ensureConstantObjectPrototype(jsObject);
                        }

                        stack[sp++] = constValue;
                        pc += 5;
                    }
                    case PUSH_CONST8 -> {
                        JSValue constValue8 = bytecode.getConstants()[ins[pc + 1] & 0xFF];
                        if (constValue8 instanceof JSFunction func) {
                            func.initializePrototypeChain(context);
                        } else if (constValue8 instanceof JSObject jsObject) {
                            ensureConstantObjectPrototype(jsObject);
                        }
                        stack[sp++] = constValue8;
                        pc += 2;
                    }
                    case PUSH_ATOM_VALUE -> {
                        int atomIndex = ((ins[pc + 1] & 0xFF) << 24) | ((ins[pc + 2] & 0xFF) << 16) |
                                ((ins[pc + 3] & 0xFF) << 8) | (ins[pc + 4] & 0xFF);
                        stack[sp++] = new JSString(bytecode.getAtoms()[atomIndex]);
                        pc += 5;
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

                        stack[sp++] = privateSymbol;
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
                                capturedClosureVars[i] = (JSValue) stack[--sp];
                            }
                            JSBytecodeFunction closureFunction = templateFunction.copyWithClosureVars(capturedClosureVars);
                            // Patch closure self-reference if needed.
                            // Following QuickJS var_refs pattern: when a function captures its own name
                            // from a block scope, the capture happens before the function is stored,
                            // resulting in undefined. We patch it here after creation.
                            int selfIdx = templateFunction.getSelfCaptureIndex();
                            if (selfIdx >= 0 && selfIdx < capturedClosureVars.length) {
                                capturedClosureVars[selfIdx] = closureFunction;
                            }
                            closureFunction.initializePrototypeChain(context);
                            stack[sp++] = closureFunction;
                        } else {
                            // Initialize the function's prototype chain to inherit from Function.prototype
                            if (funcValue instanceof JSFunction func) {
                                func.initializePrototypeChain(context);
                            }
                            stack[sp++] = funcValue;
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
                                capturedClosureVars[i] = (JSValue) stack[--sp];
                            }
                            JSBytecodeFunction closureFunction = templateFunction.copyWithClosureVars(capturedClosureVars);
                            closureFunction.initializePrototypeChain(context);
                            stack[sp++] = closureFunction;
                        } else {
                            if (funcValue instanceof JSFunction func) {
                                func.initializePrototypeChain(context);
                            }
                            stack[sp++] = funcValue;
                        }
                        pc += op.getSize();
                    }
                    case PUSH_EMPTY_STRING -> {
                        stack[sp++] = new JSString("");
                        pc += 1;
                    }
                    case UNDEFINED -> {
                        stack[sp++] = JSUndefined.INSTANCE;
                        pc += 1;
                    }
                    case NULL -> {
                        stack[sp++] = JSNull.INSTANCE;
                        pc += 1;
                    }
                    case PUSH_THIS -> {
                        stack[sp++] = currentFrame.getThisArg();
                        pc += 1;
                    }
                    case PUSH_FALSE -> {
                        stack[sp++] = JSBoolean.FALSE;
                        pc += 1;
                    }
                    case PUSH_TRUE -> {
                        stack[sp++] = JSBoolean.TRUE;
                        pc += 1;
                    }
                    case SPECIAL_OBJECT -> {
                        // SPECIAL_OBJECT creates special runtime objects
                        // Opcode format: SPECIAL_OBJECT type (1 byte for opcode + 1 byte for type)
                        int objectType = bytecode.readU8(pc + 1);
                        JSValue specialObj = createSpecialObject(objectType, currentFrame);
                        stack[sp++] = specialObj;
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
                        stack[sp++] = restArray;
                        pc += op.getSize();
                    }

                    // ==================== Stack Manipulation ====================
                    case DROP -> {
                        sp--;
                        pc += 1;
                    }
                    case NIP -> {
                        stack[sp - 2] = stack[sp - 1];
                        sp--;
                        pc += 1;
                    }
                    case DUP -> {
                        stack[sp] = stack[sp - 1];
                        sp++;
                        pc += 1;
                    }
                    case DUP1 -> {
                        stack[sp] = stack[sp - 2];
                        sp++;
                        pc += 1;
                    }
                    case DUP2 -> {
                        stack[sp] = stack[sp - 2];
                        stack[sp + 1] = stack[sp - 1];
                        sp += 2;
                        pc += 1;
                    }
                    case INSERT2 -> {
                        // [a, b] -> [b, a, b]
                        JSStackValue i2top = stack[sp - 1];
                        stack[sp] = i2top;
                        stack[sp - 1] = stack[sp - 2];
                        stack[sp - 2] = i2top;
                        sp++;
                        pc += 1;
                    }
                    case INSERT3 -> {
                        // [a, b, c] -> [c, a, b, c]
                        JSStackValue i3top = stack[sp - 1];
                        stack[sp] = i3top;
                        stack[sp - 1] = stack[sp - 2];
                        stack[sp - 2] = stack[sp - 3];
                        stack[sp - 3] = i3top;
                        sp++;
                        pc += 1;
                    }
                    case INSERT4 -> {
                        // [a, b, c, d] -> [d, a, b, c, d]
                        JSStackValue i4top = stack[sp - 1];
                        stack[sp] = i4top;
                        stack[sp - 1] = stack[sp - 2];
                        stack[sp - 2] = stack[sp - 3];
                        stack[sp - 3] = stack[sp - 4];
                        stack[sp - 4] = i4top;
                        sp++;
                        pc += 1;
                    }
                    case SWAP -> {
                        JSStackValue swapTmp = stack[sp - 1];
                        stack[sp - 1] = stack[sp - 2];
                        stack[sp - 2] = swapTmp;
                        propertyAccessLock = true;
                        pc += 1;
                    }
                    case ROT3L -> {
                        JSStackValue rot3a = stack[sp - 3];
                        stack[sp - 3] = stack[sp - 2];
                        stack[sp - 2] = stack[sp - 1];
                        stack[sp - 1] = rot3a;
                        pc += 1;
                    }
                    case ROT3R -> {
                        JSStackValue rot3c = stack[sp - 1];
                        stack[sp - 1] = stack[sp - 2];
                        stack[sp - 2] = stack[sp - 3];
                        stack[sp - 3] = rot3c;
                        pc += 1;
                    }
                    case SWAP2 -> {
                        JSStackValue s2a = stack[sp - 4], s2b = stack[sp - 3];
                        stack[sp - 4] = stack[sp - 2];
                        stack[sp - 3] = stack[sp - 1];
                        stack[sp - 2] = s2a;
                        stack[sp - 1] = s2b;
                        pc += 1;
                    }

                    // ==================== Arithmetic Operations ====================
                    case ADD -> {
                        // Inline number fast path to avoid sync overhead
                        JSValue addR = (JSValue) stack[sp - 1];
                        JSValue addL = (JSValue) stack[sp - 2];
                        if (addL instanceof JSNumber addLN && addR instanceof JSNumber addRN) {
                            stack[sp - 2] = JSNumber.of(addLN.value() + addRN.value());
                            sp--;
                        } else if (addL instanceof JSString || addR instanceof JSString) {
                            stack[sp - 2] = new JSString(
                                    JSTypeConversions.toString(context, addL).value() +
                                            JSTypeConversions.toString(context, addR).value());
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleAdd();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case SUB -> {
                        JSValue subR = (JSValue) stack[sp - 1];
                        JSValue subL = (JSValue) stack[sp - 2];
                        if (subL instanceof JSNumber subLN && subR instanceof JSNumber subRN) {
                            stack[sp - 2] = JSNumber.of(subLN.value() - subRN.value());
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleSub();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case MUL -> {
                        JSValue mulR = (JSValue) stack[sp - 1];
                        JSValue mulL = (JSValue) stack[sp - 2];
                        if (mulL instanceof JSNumber mulLN && mulR instanceof JSNumber mulRN) {
                            stack[sp - 2] = JSNumber.of(mulLN.value() * mulRN.value());
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleMul();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case DIV -> {
                        valueStack.stackTop = sp;
                        handleDiv();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case MOD -> {
                        valueStack.stackTop = sp;
                        handleMod();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case EXP, POW -> {
                        valueStack.stackTop = sp;
                        handleExp();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case PLUS -> {
                        valueStack.stackTop = sp;
                        handlePlus();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case NEG -> {
                        valueStack.stackTop = sp;
                        handleNeg();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case INC -> {
                        valueStack.stackTop = sp;
                        handleInc();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case DEC -> {
                        valueStack.stackTop = sp;
                        handleDec();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case INC_LOC -> {
                        int incLocIdx = ins[pc + 1] & 0xFF;
                        JSValue incLocVal = locals[incLocIdx];
                        if (incLocVal instanceof JSNumber incNum) {
                            locals[incLocIdx] = JSNumber.of(incNum.value() + 1);
                        } else {
                            locals[incLocIdx] = JSNumber.of(
                                    JSTypeConversions.toNumber(context, incLocVal != null ? incLocVal : JSUndefined.INSTANCE).value() + 1);
                        }
                        pc += 2;
                    }
                    case DEC_LOC -> {
                        int decLocIdx = ins[pc + 1] & 0xFF;
                        JSValue decLocVal = locals[decLocIdx];
                        if (decLocVal instanceof JSNumber decNum) {
                            locals[decLocIdx] = JSNumber.of(decNum.value() - 1);
                        } else {
                            locals[decLocIdx] = JSNumber.of(
                                    JSTypeConversions.toNumber(context, decLocVal != null ? decLocVal : JSUndefined.INSTANCE).value() - 1);
                        }
                        pc += 2;
                    }
                    case ADD_LOC -> {
                        int addLocIdx = ins[pc + 1] & 0xFF;
                        JSValue addRight = (JSValue) stack[--sp];
                        JSValue addLeft = locals[addLocIdx];
                        if (addLeft == null) addLeft = JSUndefined.INSTANCE;
                        if (addLeft instanceof JSNumber addLeftNum && addRight instanceof JSNumber addRightNum) {
                            locals[addLocIdx] = JSNumber.of(addLeftNum.value() + addRightNum.value());
                        } else if (addLeft instanceof JSString || addRight instanceof JSString) {
                            locals[addLocIdx] = new JSString(
                                    JSTypeConversions.toString(context, addLeft).value() +
                                            JSTypeConversions.toString(context, addRight).value());
                        } else {
                            locals[addLocIdx] = JSNumber.of(
                                    JSTypeConversions.toNumber(context, addLeft).value() +
                                            JSTypeConversions.toNumber(context, addRight).value());
                        }
                        pc += 2;
                    }
                    case POST_INC -> {
                        valueStack.stackTop = sp;
                        handlePostInc();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case POST_DEC -> {
                        valueStack.stackTop = sp;
                        handlePostDec();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case PERM3 -> {
                        // [a, b, c] -> [b, a, c]
                        JSStackValue p3tmp = stack[sp - 3];
                        stack[sp - 3] = stack[sp - 2];
                        stack[sp - 2] = p3tmp;
                        pc += 1;
                    }
                    case PERM4 -> {
                        // [a, b, c, d] -> [c, a, b, d]
                        JSStackValue p4a = stack[sp - 4];
                        stack[sp - 4] = stack[sp - 2];
                        stack[sp - 2] = stack[sp - 3];
                        stack[sp - 3] = p4a;
                        pc += 1;
                    }
                    case PERM5 -> {
                        // [a, b, c, d, e] -> [d, a, b, c, e]
                        JSStackValue p5a = stack[sp - 5];
                        stack[sp - 5] = stack[sp - 2];
                        stack[sp - 2] = stack[sp - 3];
                        stack[sp - 3] = stack[sp - 4];
                        stack[sp - 4] = p5a;
                        pc += 1;
                    }

                    // ==================== Bitwise Operations ====================
                    case SHL -> {
                        JSValue shlR = (JSValue) stack[sp - 1], shlL = (JSValue) stack[sp - 2];
                        if (shlL instanceof JSNumber shlLN && shlR instanceof JSNumber shlRN) {
                            stack[sp - 2] = JSNumber.of(((int) shlLN.value()) << (((int) shlRN.value()) & 0x1F));
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleShl();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case SAR -> {
                        JSValue sarR = (JSValue) stack[sp - 1], sarL = (JSValue) stack[sp - 2];
                        if (sarL instanceof JSNumber sarLN && sarR instanceof JSNumber sarRN) {
                            stack[sp - 2] = JSNumber.of(((int) sarLN.value()) >> (((int) sarRN.value()) & 0x1F));
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleSar();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case SHR -> {
                        valueStack.stackTop = sp;
                        handleShr();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case AND -> {
                        JSValue andR = (JSValue) stack[sp - 1], andL = (JSValue) stack[sp - 2];
                        if (andL instanceof JSNumber andLN && andR instanceof JSNumber andRN) {
                            stack[sp - 2] = JSNumber.of(((int) andLN.value()) & ((int) andRN.value()));
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleAnd();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case OR -> {
                        valueStack.stackTop = sp;
                        handleOr();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case XOR -> {
                        valueStack.stackTop = sp;
                        handleXor();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case NOT -> {
                        valueStack.stackTop = sp;
                        handleNot();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }

                    // ==================== Comparison Operations ====================
                    case EQ -> {
                        valueStack.stackTop = sp;
                        handleEq();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case NEQ -> {
                        valueStack.stackTop = sp;
                        handleNeq();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case STRICT_EQ -> {
                        stack[sp - 2] = JSBoolean.valueOf(JSTypeConversions.strictEquals(
                                (JSValue) stack[sp - 2], (JSValue) stack[sp - 1]));
                        sp--;
                        pc += 1;
                    }
                    case STRICT_NEQ -> {
                        stack[sp - 2] = JSBoolean.valueOf(!JSTypeConversions.strictEquals(
                                (JSValue) stack[sp - 2], (JSValue) stack[sp - 1]));
                        sp--;
                        pc += 1;
                    }
                    case LT -> {
                        JSValue ltR = (JSValue) stack[sp - 1], ltL = (JSValue) stack[sp - 2];
                        if (ltL instanceof JSNumber ltLN && ltR instanceof JSNumber ltRN) {
                            stack[sp - 2] = JSBoolean.valueOf(ltLN.value() < ltRN.value());
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleLt();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case LTE -> {
                        JSValue lteR = (JSValue) stack[sp - 1], lteL = (JSValue) stack[sp - 2];
                        if (lteL instanceof JSNumber lteLN && lteR instanceof JSNumber lteRN) {
                            stack[sp - 2] = JSBoolean.valueOf(lteLN.value() <= lteRN.value());
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleLte();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case GT -> {
                        JSValue gtR = (JSValue) stack[sp - 1], gtL = (JSValue) stack[sp - 2];
                        if (gtL instanceof JSNumber gtLN && gtR instanceof JSNumber gtRN) {
                            stack[sp - 2] = JSBoolean.valueOf(gtLN.value() > gtRN.value());
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleGt();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case GTE -> {
                        JSValue gteR = (JSValue) stack[sp - 1], gteL = (JSValue) stack[sp - 2];
                        if (gteL instanceof JSNumber gteLN && gteR instanceof JSNumber gteRN) {
                            stack[sp - 2] = JSBoolean.valueOf(gteLN.value() >= gteRN.value());
                            sp--;
                        } else {
                            valueStack.stackTop = sp;
                            handleGte();
                            sp = valueStack.stackTop;
                        }
                        pc += 1;
                    }
                    case INSTANCEOF -> {
                        valueStack.stackTop = sp;
                        handleInstanceof();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case IN -> {
                        valueStack.stackTop = sp;
                        handleIn();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case PRIVATE_IN -> {
                        valueStack.stackTop = sp;
                        handlePrivateIn();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }

                    // ==================== Logical Operations ====================
                    case LOGICAL_NOT, LNOT -> {
                        valueStack.stackTop = sp;
                        handleLogicalNot();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case LOGICAL_AND -> {
                        valueStack.stackTop = sp;
                        handleLogicalAnd();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case LOGICAL_OR -> {
                        valueStack.stackTop = sp;
                        handleLogicalOr();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case NULLISH_COALESCE -> {
                        valueStack.stackTop = sp;
                        handleNullishCoalesce();
                        sp = valueStack.stackTop;
                        pc += 1;
                    }

                    // ==================== Variable Access ====================
                    case GET_VAR_UNDEF -> {
                        int getVarRefIndex = bytecode.readU16(pc + 1);
                        stack[sp++] = currentFrame.getVarRef(getVarRefIndex);
                        pc += op.getSize();
                    }
                    case GET_REF_VALUE -> {
                        JSValue propertyValue = (JSValue) stack[sp - 1];
                        JSValue objectValue = (JSValue) stack[sp - 2];
                        PropertyKey key = PropertyKey.fromValue(context, propertyValue);

                        if (objectValue.isUndefined()) {
                            String name = key != null ? key.toPropertyString() : "variable";
                            pendingException = context.throwReferenceError(name + " is not defined");
                            stack[sp++] = JSUndefined.INSTANCE;
                            pc += op.getSize();
                            break;
                        }

                        JSObject targetObject = toObject(objectValue);
                        if (targetObject == null) {
                            pendingException = context.throwTypeError("value has no property");
                            stack[sp++] = JSUndefined.INSTANCE;
                            pc += op.getSize();
                            break;
                        }

                        JSValue value;
                        if (!targetObject.has(key)) {
                            if (context.isStrictMode()) {
                                String name = key != null ? key.toPropertyString() : "variable";
                                pendingException = context.throwReferenceError(name + " is not defined");
                                stack[sp++] = JSUndefined.INSTANCE;
                                pc += op.getSize();
                                break;
                            }
                            value = JSUndefined.INSTANCE;
                        } else {
                            value = targetObject.get(key, context);
                        }
                        stack[sp++] = value;
                        pc += op.getSize();
                    }
                    case GET_VAR -> {
                        int getVarAtom = bytecode.readU32(pc + 1);
                        String getVarName = bytecode.getAtoms()[getVarAtom];
                        PropertyKey key = PropertyKey.fromString(getVarName);
                        JSObject globalObject = context.getGlobalObject();
                        if (!globalObject.has(key)) {
                            // Set pendingException instead of throwing directly so the
                            // VM's exception handler can unwind to JavaScript try-catch.
                            pendingException = context.throwReferenceError(getVarName + " is not defined");
                            stack[sp++] = JSUndefined.INSTANCE;
                        } else {
                            JSValue varValue = globalObject.get(key);
                            // Start tracking property access from variable name (unless locked)
                            if (trackPropertyAccess && !propertyAccessLock) {
                                resetPropertyAccessTracking();
                                propertyAccessChain.append(getVarName);
                            }
                            stack[sp++] = varValue;
                        }
                        pc += op.getSize();
                    }
                    case PUT_VAR_INIT -> {
                        int putVarRefIndex = bytecode.readU16(pc + 1);
                        JSValue putValue = (JSValue) stack[--sp];
                        currentFrame.setVarRef(putVarRefIndex, putValue);
                        pc += op.getSize();
                    }
                    case PUT_REF_VALUE -> {
                        JSValue setValue = (JSValue) stack[--sp];
                        JSValue propertyValue = (JSValue) stack[--sp];
                        JSValue objectValue = (JSValue) stack[--sp];
                        PropertyKey key = PropertyKey.fromValue(context, propertyValue);

                        if (objectValue.isUndefined()) {
                            if (context.isStrictMode()) {
                                String name = key != null ? key.toPropertyString() : "variable";
                                pendingException = context.throwReferenceError(name + " is not defined");
                                pc += op.getSize();
                                break;
                            }
                            objectValue = context.getGlobalObject();
                        }

                        JSObject targetObject = toObject(objectValue);
                        if (targetObject == null) {
                            pendingException = context.throwTypeError("value has no property");
                            pc += op.getSize();
                            break;
                        }

                        if (!targetObject.has(key) && context.isStrictMode()) {
                            String name = key != null ? key.toPropertyString() : "variable";
                            pendingException = context.throwReferenceError(name + " is not defined");
                            pc += op.getSize();
                            break;
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
                        JSValue putValue = (JSValue) stack[--sp];
                        context.getGlobalObject().set(PropertyKey.fromString(putVarName), putValue);
                        pc += op.getSize();
                    }
                    case SET_VAR -> {
                        int setVarAtom = bytecode.readU32(pc + 1);
                        String setVarName = bytecode.getAtoms()[setVarAtom];
                        JSValue setValue = (JSValue) stack[sp - 1];
                        PropertyKey setVarKey = PropertyKey.fromString(setVarName);
                        JSObject setVarGlobal = context.getGlobalObject();
                        // Per ES spec, in strict mode assigning to an undeclared variable
                        // throws ReferenceError (QuickJS: JS_ThrowReferenceErrorNotDefined)
                        if (context.isStrictMode() && !setVarGlobal.has(setVarKey)) {
                            throw new JSVirtualMachineException(
                                    context.throwReferenceError(setVarName + " is not defined"));
                        }
                        setVarGlobal.set(setVarKey, setValue);
                        pc += op.getSize();
                    }
                    case DELETE_VAR -> {
                        int deleteVarAtom = bytecode.readU32(pc + 1);
                        String deleteVarName = bytecode.getAtoms()[deleteVarAtom];
                        boolean deleted = context.getGlobalObject().delete(PropertyKey.fromString(deleteVarName), context);
                        stack[sp++] = JSBoolean.valueOf(deleted);
                        pc += op.getSize();
                    }
                    case GET_LOCAL, GET_LOC -> {
                        int gli = ((ins[pc + 1] & 0xFF) << 8) | (ins[pc + 2] & 0xFF);
                        JSValue glv = locals[gli];
                        stack[sp++] = glv != null ? glv : JSUndefined.INSTANCE;
                        pc += 3;
                    }
                    case GET_LOC8 -> {
                        JSValue gl8v = locals[ins[pc + 1] & 0xFF];
                        stack[sp++] = gl8v != null ? gl8v : JSUndefined.INSTANCE;
                        pc += 2;
                    }
                    case GET_LOC0 -> {
                        JSValue gl0 = locals[0];
                        stack[sp++] = gl0 != null ? gl0 : JSUndefined.INSTANCE;
                        pc += 1;
                    }
                    case GET_LOC1 -> {
                        JSValue gl1 = locals[1];
                        stack[sp++] = gl1 != null ? gl1 : JSUndefined.INSTANCE;
                        pc += 1;
                    }
                    case GET_LOC2 -> {
                        JSValue gl2 = locals[2];
                        stack[sp++] = gl2 != null ? gl2 : JSUndefined.INSTANCE;
                        pc += 1;
                    }
                    case GET_LOC3 -> {
                        JSValue gl3 = locals[3];
                        stack[sp++] = gl3 != null ? gl3 : JSUndefined.INSTANCE;
                        pc += 1;
                    }
                    case PUT_LOCAL, PUT_LOC -> {
                        locals[((ins[pc + 1] & 0xFF) << 8) | (ins[pc + 2] & 0xFF)] = (JSValue) stack[--sp];
                        pc += 3;
                    }
                    case PUT_LOC8 -> {
                        locals[ins[pc + 1] & 0xFF] = (JSValue) stack[--sp];
                        pc += 2;
                    }
                    case PUT_LOC0 -> {
                        locals[0] = (JSValue) stack[--sp];
                        pc += 1;
                    }
                    case PUT_LOC1 -> {
                        locals[1] = (JSValue) stack[--sp];
                        pc += 1;
                    }
                    case PUT_LOC2 -> {
                        locals[2] = (JSValue) stack[--sp];
                        pc += 1;
                    }
                    case PUT_LOC3 -> {
                        locals[3] = (JSValue) stack[--sp];
                        pc += 1;
                    }
                    case SET_LOCAL, SET_LOC -> {
                        locals[((ins[pc + 1] & 0xFF) << 8) | (ins[pc + 2] & 0xFF)] = (JSValue) stack[sp - 1];
                        pc += 3;
                    }
                    case SET_LOC8 -> {
                        locals[ins[pc + 1] & 0xFF] = (JSValue) stack[sp - 1];
                        pc += 2;
                    }
                    case SET_LOC0 -> {
                        locals[0] = (JSValue) stack[sp - 1];
                        pc += 1;
                    }
                    case SET_LOC1 -> {
                        locals[1] = (JSValue) stack[sp - 1];
                        pc += 1;
                    }
                    case SET_LOC2 -> {
                        locals[2] = (JSValue) stack[sp - 1];
                        pc += 1;
                    }
                    case SET_LOC3 -> {
                        locals[3] = (JSValue) stack[sp - 1];
                        pc += 1;
                    }
                    case GET_ARG -> {
                        int argIndex = bytecode.readU16(pc + 1);
                        stack[sp++] = getArgumentValue(argIndex);
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
                        stack[sp++] = getArgumentValue(argIndex);
                        pc += op.getSize();
                    }
                    case PUT_ARG -> {
                        int argIndex = bytecode.readU16(pc + 1);
                        JSValue value = (JSValue) stack[--sp];
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
                        JSValue value = (JSValue) stack[--sp];
                        setArgumentValue(argIndex, value);
                        pc += op.getSize();
                    }
                    case SET_ARG -> {
                        int argIndex = bytecode.readU16(pc + 1);
                        setArgumentValue(argIndex, (JSValue) stack[sp - 1]);
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
                        setArgumentValue(argIndex, (JSValue) stack[sp - 1]);
                        pc += op.getSize();
                    }
                    case GET_VAR_REF -> {
                        int getVarRefIndex = bytecode.readU16(pc + 1);
                        stack[sp++] = currentFrame.getVarRef(getVarRefIndex);
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
                        stack[sp++] = currentFrame.getVarRef(getVarRefIndex);
                        pc += op.getSize();
                    }
                    case PUT_VAR_REF -> {
                        int putVarRefIndex = bytecode.readU16(pc + 1);
                        JSValue value = (JSValue) stack[--sp];
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
                        JSValue value = (JSValue) stack[--sp];
                        currentFrame.setVarRef(putVarRefIndex, value);
                        pc += op.getSize();
                    }
                    case SET_VAR_REF -> {
                        int setVarRefIndex = bytecode.readU16(pc + 1);
                        currentFrame.setVarRef(setVarRefIndex, (JSValue) stack[sp - 1]);
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
                        currentFrame.setVarRef(setVarRefIndex, (JSValue) stack[sp - 1]);
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
                        stack[sp++] = localValue;
                        pc += op.getSize();
                    }
                    case PUT_LOC_CHECK -> {
                        int index = bytecode.readU16(pc + 1);
                        if (isUninitialized(currentFrame.getLocals()[index])) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        currentFrame.getLocals()[index] = (JSValue) stack[--sp];
                        pc += op.getSize();
                    }
                    case SET_LOC_CHECK -> {
                        int index = bytecode.readU16(pc + 1);
                        if (isUninitialized(currentFrame.getLocals()[index])) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        currentFrame.getLocals()[index] = (JSValue) stack[sp - 1];
                        pc += op.getSize();
                    }
                    case PUT_LOC_CHECK_INIT -> {
                        int index = bytecode.readU16(pc + 1);
                        if (!isUninitialized(currentFrame.getLocals()[index])) {
                            throw new JSVirtualMachineException(context.throwReferenceError("'this' can be initialized only once"));
                        }
                        currentFrame.getLocals()[index] = (JSValue) stack[--sp];
                        pc += op.getSize();
                    }
                    case GET_VAR_REF_CHECK -> {
                        int index = bytecode.readU16(pc + 1);
                        JSValue value = currentFrame.getVarRef(index);
                        if (isUninitialized(value)) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        stack[sp++] = value;
                        pc += op.getSize();
                    }
                    case PUT_VAR_REF_CHECK -> {
                        int index = bytecode.readU16(pc + 1);
                        if (isUninitialized(currentFrame.getVarRef(index))) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is uninitialized"));
                        }
                        currentFrame.setVarRef(index, (JSValue) stack[--sp]);
                        pc += op.getSize();
                    }
                    case PUT_VAR_REF_CHECK_INIT -> {
                        int index = bytecode.readU16(pc + 1);
                        if (!isUninitialized(currentFrame.getVarRef(index))) {
                            throw new JSVirtualMachineException(context.throwReferenceError("variable is already initialized"));
                        }
                        currentFrame.setVarRef(index, (JSValue) stack[--sp]);
                        pc += op.getSize();
                    }
                    case MAKE_LOC_REF, MAKE_ARG_REF, MAKE_VAR_REF_REF -> {
                        int atomIndex = bytecode.readU32(pc + 1);
                        int refIndex = bytecode.readU16(pc + 5);
                        String atomName = bytecode.getAtoms()[atomIndex];

                        JSObject referenceObject = createReferenceObject(op, refIndex, atomName);
                        stack[sp++] = referenceObject;
                        stack[sp++] = new JSString(atomName);
                        pc += op.getSize();
                    }
                    case MAKE_VAR_REF -> {
                        int atomIndex = bytecode.readU32(pc + 1);
                        String atomName = bytecode.getAtoms()[atomIndex];
                        stack[sp++] = context.getGlobalObject();
                        stack[sp++] = new JSString(atomName);
                        pc += op.getSize();
                    }

                    // ==================== Property Access ====================
                    case GET_FIELD -> {
                        int getFieldAtom = bytecode.readU32(pc + 1);
                        String fieldName = bytecode.getAtoms()[getFieldAtom];
                        JSValue obj = (JSValue) stack[--sp];

                        // Auto-box primitives to access their prototype methods
                        JSObject targetObj = toObject(obj);
                        if (targetObj != null) {
                            JSValue result = targetObj.get(PropertyKey.fromString(fieldName), context);
                            // Check if getter threw an exception
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                                stack[sp++] = JSUndefined.INSTANCE;
                            } else {
                                // Track property access for better error messages (unless locked)
                                if (trackPropertyAccess && !propertyAccessLock) {
                                    if (!propertyAccessChain.isEmpty()) {
                                        propertyAccessChain.append('.');
                                    }
                                    propertyAccessChain.append(fieldName);
                                }
                                stack[sp++] = result;
                            }
                        } else {
                            resetPropertyAccessTracking();
                            stack[sp++] = JSUndefined.INSTANCE;
                        }
                        pc += op.getSize();
                    }
                    case GET_LENGTH -> {
                        JSValue objectValue = (JSValue) stack[--sp];
                        JSObject targetObject = toObject(objectValue);
                        if (targetObject != null) {
                            JSValue result = targetObject.get(KEY_LENGTH, context);
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                                stack[sp++] = JSUndefined.INSTANCE;
                            } else {
                                stack[sp++] = result;
                            }
                        } else {
                            stack[sp++] = JSUndefined.INSTANCE;
                        }
                        pc += op.getSize();
                    }
                    case PUT_FIELD -> {
                        int putFieldAtom = bytecode.readU32(pc + 1);
                        String putFieldName = bytecode.getAtoms()[putFieldAtom];
                        JSValue putFieldObj = (JSValue) stack[--sp];
                        // The value should be on top of the stack.
                        JSValue putFieldValue = (JSValue) stack[sp - 1];
                        if (putFieldObj instanceof JSObject jsObj) {
                            jsObj.set(PropertyKey.fromString(putFieldName), putFieldValue, context);
                            // Check if setter threw an exception
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                            }
                        } else if (putFieldObj instanceof JSNull || putFieldObj instanceof JSUndefined) {
                            context.throwTypeError("cannot set property '" + putFieldName + "' of " +
                                    (putFieldObj instanceof JSNull ? "null" : "undefined"));
                            pendingException = context.getPendingException();
                            context.clearPendingException();
                        } else {
                            // Primitive base: auto-box to object and set property.
                            // Per ES spec 6.2.3.2 (PutValue), setters on the prototype
                            // chain must be triggered even for primitive bases.
                            JSObject boxed = toObject(putFieldObj);
                            if (boxed != null) {
                                boxed.set(PropertyKey.fromString(putFieldName), putFieldValue, context);
                                if (context.hasPendingException()) {
                                    pendingException = context.getPendingException();
                                    context.clearPendingException();
                                }
                            }
                        }
                        pc += op.getSize();
                    }
                    case GET_ARRAY_EL -> {
                        JSValue gaelIdx = (JSValue) stack[--sp];
                        JSValue gaelObj = (JSValue) stack[sp - 1];

                        // Fast path: string character access (avoids boxing to JSStringObject)
                        if (gaelObj instanceof JSString str && gaelIdx instanceof JSNumber num) {
                            double d = num.value();
                            int idx = (int) d;
                            if (idx == d && idx >= 0 && idx < str.value().length()) {
                                stack[sp - 1] = new JSString(String.valueOf(str.value().charAt(idx)));
                                pc += 1;
                                break;
                            }
                        }

                        // Slow path: sync sp and use valueStack
                        valueStack.stackTop = sp - 1; // pop both, we'll push result
                        JSObject targetObj = toObject(gaelObj);
                        if (targetObj != null) {
                            PropertyKey key = PropertyKey.fromValue(context, gaelIdx);
                            JSValue result = targetObj.get(key, context);
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                                stack[sp - 1] = JSUndefined.INSTANCE;
                            } else {
                                if (trackPropertyAccess && !propertyAccessLock) {
                                    if (gaelIdx instanceof JSString jsString) {
                                        String propertyName = jsString.value();
                                        if (!propertyAccessChain.isEmpty()) {
                                            propertyAccessChain.append('.');
                                        }
                                        propertyAccessChain.append(propertyName);
                                    } else if (gaelIdx instanceof JSNumber jsNumber) {
                                        String propertyName = JSTypeConversions.toString(context, jsNumber).value();
                                        if (!propertyAccessChain.isEmpty()) {
                                            propertyAccessChain.append('.');
                                        }
                                        propertyAccessChain.append(propertyName);
                                    } else if (gaelIdx instanceof JSSymbol jsSymbol) {
                                        propertyAccessChain.append("[Symbol.").append(jsSymbol.getDescription()).append("]");
                                    }
                                }
                                stack[sp - 1] = result;
                            }
                        } else {
                            resetPropertyAccessTracking();
                            stack[sp - 1] = JSUndefined.INSTANCE;
                        }
                        valueStack.stackTop = sp;
                        pc += 1;
                    }
                    case GET_ARRAY_EL2 -> {
                        JSValue index = (JSValue) stack[--sp];
                        JSValue arrayObj = (JSValue) stack[sp - 1];

                        // Fast path: string character access
                        if (arrayObj instanceof JSString str && index instanceof JSNumber num) {
                            double d = num.value();
                            int idx = (int) d;
                            if (idx == d && idx >= 0 && idx < str.value().length()) {
                                stack[sp++] = new JSString(String.valueOf(str.value().charAt(idx)));
                                pc += 1;
                                break;
                            }
                        }

                        JSObject targetObj = toObject(arrayObj);
                        if (targetObj != null) {
                            PropertyKey key = PropertyKey.fromValue(context, index);
                            JSValue result = targetObj.get(key, context);
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                                stack[sp++] = JSUndefined.INSTANCE;
                            } else {
                                stack[sp++] = result;
                            }
                        } else {
                            stack[sp++] = JSUndefined.INSTANCE;
                        }
                        pc += 1;
                    }
                    case GET_ARRAY_EL3 -> {
                        JSValue index = (JSValue) stack[sp - 1];
                        JSValue arrayObj = (JSValue) stack[sp - 2];

                        if (!(index instanceof JSNumber || index instanceof JSString || index instanceof JSSymbol)) {
                            if (arrayObj.isUndefined() || arrayObj.isNull()) {
                                throw new JSVirtualMachineException(context.throwTypeError("value has no property"));
                            }
                            JSValue convertedIndex = toPropertyKeyValue(index);
                            stack[sp - 1] = convertedIndex;
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
                            stack[sp++] = JSUndefined.INSTANCE;
                        } else {
                            stack[sp++] = result;
                        }
                        pc += op.getSize();
                    }
                    case PUT_ARRAY_EL -> {
                        // Stack layout: [value, object, property] (property on top)
                        JSValue putElIndex = (JSValue) stack[--sp];   // Pop property
                        JSValue putElObj = (JSValue) stack[--sp];     // Pop object
                        JSValue putElValue = (JSValue) stack[--sp];   // Pop value
                        if (putElObj instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(context, putElIndex);
                            jsObj.set(key, putElValue, context);
                            // Check if setter threw an exception
                            if (context.hasPendingException()) {
                                pendingException = context.getPendingException();
                                context.clearPendingException();
                            }
                        } else if (putElObj instanceof JSNull || putElObj instanceof JSUndefined) {
                            PropertyKey key = PropertyKey.fromValue(context, putElIndex);
                            context.throwTypeError("cannot set property '" + key + "' of " +
                                    (putElObj instanceof JSNull ? "null" : "undefined"));
                            pendingException = context.getPendingException();
                            context.clearPendingException();
                        } else {
                            // Primitive base: auto-box and set (triggers setters on prototype chain)
                            JSObject boxed = toObject(putElObj);
                            if (boxed != null) {
                                PropertyKey key = PropertyKey.fromValue(context, putElIndex);
                                boxed.set(key, putElValue, context);
                                if (context.hasPendingException()) {
                                    pendingException = context.getPendingException();
                                    context.clearPendingException();
                                }
                            }
                        }
                        // Assignment expressions return the assigned value
                        stack[sp++] = putElValue;
                        pc += op.getSize();
                    }
                    case TO_PROPKEY -> {
                        JSValue rawKey = (JSValue) stack[--sp];
                        stack[sp++] = toPropertyKeyValue(rawKey);
                        pc += op.getSize();
                    }
                    case TO_PROPKEY2 -> {
                        JSValue rawKey = (JSValue) stack[--sp];
                        JSValue baseObject = (JSValue) stack[--sp];
                        stack[sp++] = baseObject;
                        stack[sp++] = toPropertyKeyValue(rawKey);
                        pc += op.getSize();
                    }

                    // ==================== Control Flow ====================
                    case IF_FALSE -> {
                        JSValue ifFalseCond = (JSValue) stack[--sp];
                        boolean ifFalseIsFalsy;
                        if (ifFalseCond instanceof JSBoolean bv) {
                            ifFalseIsFalsy = !bv.value();
                        } else if (ifFalseCond instanceof JSNumber nv) {
                            double d = nv.value();
                            ifFalseIsFalsy = d == 0.0 || Double.isNaN(d);
                        } else {
                            ifFalseIsFalsy = JSTypeConversions.toBoolean(ifFalseCond) == JSBoolean.FALSE;
                        }
                        if (ifFalseIsFalsy) {
                            pc += 5 + (((ins[pc + 1] & 0xFF) << 24) | ((ins[pc + 2] & 0xFF) << 16) |
                                    ((ins[pc + 3] & 0xFF) << 8) | (ins[pc + 4] & 0xFF));
                        } else {
                            pc += 5;
                        }
                    }
                    case IF_TRUE -> {
                        JSValue ifTrueCond = (JSValue) stack[--sp];
                        boolean ifTrueIsTruthy;
                        if (ifTrueCond instanceof JSBoolean bv) {
                            ifTrueIsTruthy = bv.value();
                        } else if (ifTrueCond instanceof JSNumber nv) {
                            double d = nv.value();
                            ifTrueIsTruthy = d != 0.0 && !Double.isNaN(d);
                        } else {
                            ifTrueIsTruthy = JSTypeConversions.toBoolean(ifTrueCond) == JSBoolean.TRUE;
                        }
                        if (ifTrueIsTruthy) {
                            pc += 5 + (((ins[pc + 1] & 0xFF) << 24) | ((ins[pc + 2] & 0xFF) << 16) |
                                    ((ins[pc + 3] & 0xFF) << 8) | (ins[pc + 4] & 0xFF));
                        } else {
                            pc += 5;
                        }
                    }
                    case IF_TRUE8 -> {
                        JSValue ifTrue8Cond = (JSValue) stack[--sp];
                        boolean ifTrue8IsTruthy;
                        if (ifTrue8Cond instanceof JSBoolean bv) {
                            ifTrue8IsTruthy = bv.value();
                        } else if (ifTrue8Cond instanceof JSNumber nv) {
                            double d = nv.value();
                            ifTrue8IsTruthy = d != 0.0 && !Double.isNaN(d);
                        } else {
                            ifTrue8IsTruthy = JSTypeConversions.toBoolean(ifTrue8Cond) == JSBoolean.TRUE;
                        }
                        if (ifTrue8IsTruthy) {
                            pc += 2 + ins[pc + 1];
                        } else {
                            pc += 2;
                        }
                    }
                    case IF_FALSE8 -> {
                        JSValue ifFalse8Cond = (JSValue) stack[--sp];
                        boolean ifFalse8IsFalsy;
                        if (ifFalse8Cond instanceof JSBoolean bv) {
                            ifFalse8IsFalsy = !bv.value();
                        } else if (ifFalse8Cond instanceof JSNumber nv) {
                            double d = nv.value();
                            ifFalse8IsFalsy = d == 0.0 || Double.isNaN(d);
                        } else {
                            ifFalse8IsFalsy = JSTypeConversions.toBoolean(ifFalse8Cond) == JSBoolean.FALSE;
                        }
                        if (ifFalse8IsFalsy) {
                            pc += 2 + ins[pc + 1];
                        } else {
                            pc += 2;
                        }
                    }
                    case GOTO -> {
                        int gotoOff = ((ins[pc + 1] & 0xFF) << 24) | ((ins[pc + 2] & 0xFF) << 16) |
                                ((ins[pc + 3] & 0xFF) << 8) | (ins[pc + 4] & 0xFF);
                        pc += 5 + gotoOff;
                    }
                    case GOTO8 -> {
                        pc += 2 + ins[pc + 1];
                    }
                    case GOTO16 -> {
                        int goto16Off = (short) (((ins[pc + 1] & 0xFF) << 8) | (ins[pc + 2] & 0xFF));
                        pc += 3 + goto16Off;
                    }
                    case RETURN -> {
                        JSValue returnValue = (JSValue) stack[--sp];
                        valueStack.stackTop = savedStackTop;
                        currentFrame = previousFrame;
                        if (savedStrictMode) {
                            context.enterStrictMode();
                        } else {
                            context.exitStrictMode();
                        }
                        return returnValue;
                    }
                    case RETURN_UNDEF -> {
                        valueStack.stackTop = savedStackTop;
                        currentFrame = previousFrame;
                        if (savedStrictMode) {
                            context.enterStrictMode();
                        } else {
                            context.exitStrictMode();
                        }
                        return JSUndefined.INSTANCE;
                    }
                    case RETURN_ASYNC -> {
                        JSValue returnValue = (JSValue) stack[--sp];
                        valueStack.stackTop = savedStackTop;
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
                        valueStack.stackTop = sp;
                        handleInitCtor();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case CALL -> {
                        int callArgCount = ((ins[pc + 1] & 0xFF) << 8) | (ins[pc + 2] & 0xFF);
                        valueStack.stackTop = sp;
                        handleCall(callArgCount);
                        sp = valueStack.stackTop;
                        pc += 3;
                    }
                    case CALL0 -> {
                        valueStack.stackTop = sp;
                        handleCall(0);
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case CALL1 -> {
                        valueStack.stackTop = sp;
                        handleCall(1);
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case CALL2 -> {
                        valueStack.stackTop = sp;
                        handleCall(2);
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case CALL3 -> {
                        valueStack.stackTop = sp;
                        handleCall(3);
                        sp = valueStack.stackTop;
                        pc += 1;
                    }
                    case CALL_CONSTRUCTOR -> {
                        int ctorArgCount = ((ins[pc + 1] & 0xFF) << 8) | (ins[pc + 2] & 0xFF);
                        valueStack.stackTop = sp;
                        handleCallConstructor(ctorArgCount);
                        sp = valueStack.stackTop;
                        pc += 3;
                    }

                    // ==================== Object/Array Creation ====================
                    case OBJECT, OBJECT_NEW -> {
                        stack[sp++] = context.createJSObject();
                        pc += 1;
                    }
                    case ARRAY_NEW -> {
                        JSArray array = context.createJSArray();
                        stack[sp++] = array;
                        pc += 1;
                    }
                    case ARRAY_FROM -> {
                        // Create array from N elements on stack
                        // Stack: elem0 elem1 ... elemN-1 -> array
                        int count = bytecode.readU16(pc + 1);
                        JSArray array = context.createJSArray();

                        // Pop elements in reverse order and add to array
                        JSValue[] elements = new JSValue[count];
                        for (int i = count - 1; i >= 0; i--) {
                            elements[i] = (JSValue) stack[--sp];
                        }
                        for (JSValue element : elements) {
                            array.push(element);
                        }

                        stack[sp++] = array;
                        pc += op.getSize();
                    }
                    case APPLY -> {
                        // Apply function with arguments from array
                        // Stack: thisArg function argsArray -> result
                        // Parameter: isConstructorCall (0=regular, 1=constructor)
                        int isConstructorCall = bytecode.readU16(pc + 1);

                        JSValue argsArrayValue = (JSValue) stack[--sp];
                        JSValue functionValue = (JSValue) stack[--sp];
                        JSValue thisArgValue = (JSValue) stack[--sp];

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
                                stack[sp++] = JSUndefined.INSTANCE;
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
                            stack[sp++] = JSUndefined.INSTANCE;
                        } else {
                            stack[sp++] = result;
                        }
                        pc += op.getSize();
                    }
                    case PUSH_ARRAY -> {
                        JSValue element = (JSValue) stack[--sp];
                        JSValue array = (JSValue) stack[sp - 1];
                        if (array instanceof JSArray jsArray) {
                            jsArray.push(element);
                        }
                        pc += op.getSize();
                    }
                    case APPEND -> {
                        // Append enumerated object elements to array
                        // Stack: array pos enumobj -> array pos
                        // Based on QuickJS OP_append (quickjs.c js_append_enumerate)
                        JSValue enumobj = (JSValue) stack[--sp];
                        JSValue posValue = (JSValue) stack[--sp];
                        JSValue arrayValue = (JSValue) stack[--sp];

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
                            stack[sp++] = array;
                            stack[sp++] = JSNumber.of(pos);

                        } catch (Exception e) {
                            throw new JSVirtualMachineException("APPEND: error iterating: " + e.getMessage(), e);
                        }

                        pc += op.getSize();
                    }
                    case DEFINE_ARRAY_EL -> {
                        // Define array element
                        // Stack: array idx val -> array idx
                        // Based on QuickJS OP_define_array_el
                        JSValue value = (JSValue) stack[--sp];
                        JSValue idxValue = (JSValue) stack[sp - 1];  // Keep idx on stack
                        JSValue arrayValue = (JSValue) stack[sp - 2]; // Keep array on stack

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
                        JSValue propValue = (JSValue) stack[--sp];
                        JSValue propKey = (JSValue) stack[--sp];
                        JSValue propObj = (JSValue) stack[sp - 1];
                        if (propObj instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(context, propKey);
                            jsObj.set(key, propValue);
                        }
                        pc += op.getSize();
                    }
                    case SET_NAME -> {
                        int nameAtom = bytecode.readU32(pc + 1);
                        String name = bytecode.getAtoms()[nameAtom];
                        setObjectName((JSValue) stack[sp - 1], new JSString(name));
                        pc += op.getSize();
                    }
                    case SET_NAME_COMPUTED -> {
                        JSValue nameValue = (JSValue) stack[sp - 2];
                        setObjectName((JSValue) stack[sp - 1], getComputedNameString(nameValue));
                        pc += op.getSize();
                    }
                    case SET_PROTO -> {
                        JSValue protoValue = (JSValue) stack[--sp];
                        JSValue objectValue = (JSValue) stack[sp - 1];
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
                        JSValue homeObjectValue = (JSValue) stack[sp - 2];
                        JSValue methodValue = (JSValue) stack[sp - 1];
                        if (methodValue instanceof JSObject methodObject && homeObjectValue instanceof JSObject homeObject) {
                            methodObject.set(KEY_HOME_OBJECT, homeObject);
                        }
                        pc += op.getSize();
                    }
                    case COPY_DATA_PROPERTIES -> {
                        int mask = bytecode.readU8(pc + 1);
                        JSValue targetValue = (JSValue) stack[sp - 1 - (mask & 3)];
                        JSValue sourceValue = (JSValue) stack[sp - 1 - ((mask >> 2) & 7)];
                        JSValue excludeListValue = (JSValue) stack[sp - 1 - ((mask >> 5) & 7)];
                        copyDataProperties(targetValue, sourceValue, excludeListValue);
                        pc += op.getSize();
                    }
                    case DEFINE_CLASS -> {
                        // Stack: superClass constructor
                        // Reads: atom (class name)
                        // Result: proto constructor (pushes prototype object)
                        int classNameAtom = bytecode.readU32(pc + 1);
                        String className = bytecode.getAtoms()[classNameAtom];
                        JSValue constructor = (JSValue) stack[--sp];
                        JSValue superClass = (JSValue) stack[--sp];

                        if (!(constructor instanceof JSFunction constructorFunc)) {
                            throw new JSVirtualMachineException("DEFINE_CLASS: constructor must be a function");
                        }

                        // Create the class prototype object
                        JSObject prototype = context.createJSObject();

                        // Set up prototype chain (ES spec ClassDefinitionEvaluation steps 5.e-5.g)
                        if (superClass instanceof JSNull) {
                            // Step 5.e: superclass is null — protoParent is null
                            prototype.setPrototype(null);
                        } else if (superClass != JSUndefined.INSTANCE) {
                            // Step 5.f: If IsConstructor(superclass) is false, throw TypeError
                            if (!JSTypeChecking.isConstructor(superClass)) {
                                context.throwTypeError("parent class must be constructor");
                                pendingException = context.getPendingException();
                                stack[sp++] = JSUndefined.INSTANCE;
                                stack[sp++] = JSUndefined.INSTANCE;
                                pc += op.getSize();
                                break;
                            }
                            // Step 5.g: superclass is a constructor
                            if (superClass instanceof JSFunction superFunc) {
                                // prototype.__proto__ = superFunc.prototype
                                context.transferPrototype(prototype, superFunc);
                                // constructor.__proto__ = superFunc (the parent constructor itself)
                                constructorFunc.setPrototype(superFunc);
                            }
                        }
                        // Set constructor.prototype = prototype
                        if (constructorFunc instanceof JSObject) {
                            constructorFunc.set(KEY_PROTOTYPE, prototype);
                        }

                        // Set prototype.constructor = constructor
                        prototype.set(KEY_CONSTRUCTOR, constructor);
                        setObjectName(constructor, new JSString(className));

                        // Push prototype and constructor onto stack
                        stack[sp++] = prototype;
                        stack[sp++] = constructor;
                        pc += op.getSize();
                    }
                    case DEFINE_CLASS_COMPUTED -> {
                        int classNameAtom = bytecode.readU32(pc + 1);
                        int classFlags = bytecode.readU8(pc + 5);
                        String className = bytecode.getAtoms()[classNameAtom];
                        JSValue constructor = (JSValue) stack[--sp];
                        JSValue superClass = (JSValue) stack[--sp];
                        JSValue computedClassNameValue = (JSValue) stack[sp - 1];

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

                        constructorFunc.set(KEY_PROTOTYPE, prototype);
                        prototype.set(KEY_CONSTRUCTOR, constructor);
                        JSString computedClassName = getComputedNameString(computedClassNameValue);
                        if (computedClassName.value().isEmpty()) {
                            computedClassName = new JSString(className);
                        }
                        setObjectName(constructor, computedClassName);

                        stack[sp++] = prototype;
                        stack[sp++] = constructor;
                        pc += op.getSize();
                    }
                    case DEFINE_METHOD -> {
                        // Stack: obj method
                        // Reads: atom (method name)
                        // Result: obj (pops both, adds method to obj, pushes obj back)
                        int methodNameAtom = bytecode.readU32(pc + 1);
                        String methodName = bytecode.getAtoms()[methodNameAtom];
                        JSValue method = (JSValue) stack[--sp];  // Pop method
                        JSValue obj = (JSValue) stack[--sp];     // Pop obj

                        if (obj instanceof JSObject jsObj) {
                            jsObj.set(PropertyKey.fromString(methodName), method);
                        }

                        stack[sp++] = obj;  // Push obj back
                        pc += op.getSize();
                    }
                    case DEFINE_METHOD_COMPUTED -> {
                        int methodFlags = bytecode.readU8(pc + 1);
                        boolean enumerable = (methodFlags & 4) != 0;
                        int methodKind = methodFlags & 3;

                        JSValue methodValue = (JSValue) stack[--sp];
                        JSValue propertyValue = (JSValue) stack[--sp];
                        JSValue objectValue = (JSValue) stack[sp - 1];

                        if (objectValue instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(context, propertyValue);
                            JSString computedName = getComputedNameString(propertyValue);
                            if (methodValue instanceof JSObject methodObject) {
                                methodObject.set(KEY_HOME_OBJECT, jsObj);
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
                        JSValue value = (JSValue) stack[--sp];   // Pop value
                        JSValue obj = (JSValue) stack[--sp];     // Pop obj

                        if (obj instanceof JSObject jsObj) {
                            jsObj.set(PropertyKey.fromString(fieldName), value);
                        }

                        stack[sp++] = obj;  // Push obj back
                        pc += op.getSize();
                    }
                    case DEFINE_PRIVATE_FIELD -> {
                        // Stack: obj privateSymbol value
                        // Result: obj (pops privateSymbol and value, adds private field to obj, pushes obj back)
                        JSValue value = (JSValue) stack[--sp];           // Pop value
                        JSValue privateSymbol = (JSValue) stack[--sp];   // Pop private symbol
                        JSValue obj = (JSValue) stack[--sp];             // Pop obj

                        if (obj instanceof JSObject jsObj && privateSymbol instanceof JSSymbol symbol) {
                            // Set the private field using the symbol as the key
                            jsObj.set(PropertyKey.fromSymbol(symbol), value);
                        }

                        stack[sp++] = obj;  // Push obj back
                        pc += op.getSize();
                    }
                    case GET_PRIVATE_FIELD -> {
                        // Stack: obj privateSymbol
                        // Result: value (pops both, gets value from obj using privateSymbol)
                        JSValue privateSymbol = (JSValue) stack[--sp];  // Pop private symbol
                        JSValue obj = (JSValue) stack[--sp];            // Pop obj

                        JSValue value = JSUndefined.INSTANCE;
                        if (obj instanceof JSObject jsObj && privateSymbol instanceof JSSymbol symbol) {
                            value = jsObj.get(PropertyKey.fromSymbol(symbol));
                        }

                        stack[sp++] = value;
                        pc += op.getSize();
                    }
                    case PUT_PRIVATE_FIELD -> {
                        // Stack: obj value privateSymbol
                        // Result: value (pops obj and privateSymbol, leaves value as assignment result)
                        JSValue privateSymbol = (JSValue) stack[--sp];  // Pop private symbol
                        JSValue value = (JSValue) stack[--sp];          // Pop value
                        JSValue obj = (JSValue) stack[--sp];            // Pop obj

                        if (obj instanceof JSObject jsObj && privateSymbol instanceof JSSymbol symbol) {
                            jsObj.set(PropertyKey.fromSymbol(symbol), value);
                        }

                        // Push value back to stack (assignment expressions return the assigned value)
                        stack[sp++] = value;

                        pc += op.getSize();
                    }

                    // ==================== Exception Handling ====================
                    case THROW -> {
                        JSValue exception = (JSValue) stack[--sp];
                        pendingException = exception;
                        context.setPendingException(exception);
                        // Don't throw immediately - let the exception handling loop unwind the stack
                        // This matches QuickJS behavior: goto exception;
                        // Don't advance PC - let the exception handler deal with it
                    }
                    case THROW_ERROR -> {
                        // QuickJS OP_throw_error: throws a typed error with an atom message
                        // Format: opcode(1) + atom(4) + type(1) = 6 bytes
                        int throwAtom = bytecode.readU32(pc + 1);
                        int throwType = bytecode.readU8(pc + 5);
                        String throwName = bytecode.getAtoms()[throwAtom];
                        switch (throwType) {
                            case 0 -> // JS_THROW_VAR_RO
                                    context.throwTypeError("'" + throwName + "' is read-only");
                            case 1 -> // JS_THROW_VAR_REDECL
                                    context.throwError("SyntaxError: redeclaration of '" + throwName + "'");
                            case 2 -> // JS_THROW_VAR_UNINITIALIZED
                                    context.throwReferenceError(throwName + " is not initialized");
                            case 3 -> // JS_THROW_ERROR_DELETE_SUPER
                                    context.throwReferenceError("unsupported reference to 'super'");
                            case 4 -> // JS_THROW_ERROR_ITERATOR_THROW
                                    context.throwTypeError("iterator does not have a throw method");
                            case 5 -> // JS_THROW_ERROR_INVALID_LVALUE (Annex B)
                                    context.throwReferenceError(throwName);
                            default -> throw new JSVirtualMachineException("invalid throw_error type: " + throwType);
                        }
                        pendingException = context.getPendingException();
                        // Don't advance PC - let the exception handler deal with it
                    }
                    case CATCH -> {
                        // QuickJS: pushes catch offset marker onto stack
                        // This marker is used during exception unwinding to find the catch handler
                        int catchOffset = bytecode.readI32(pc + 1);
                        int catchHandlerPC = pc + op.getSize() + catchOffset;
                        stack[sp++] = new JSCatchOffset(catchHandlerPC);
                        pc += op.getSize();
                    }
                    case NIP_CATCH -> {
                        JSValue returnValue = (JSValue) stack[--sp];
                        boolean foundCatchMarker = false;
                        while (sp > savedStackTop) {
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
                        pc += op.getSize();
                    }

                    // ==================== Type Operations ====================
                    case TO_STRING -> {
                        JSValue value = (JSValue) stack[sp - 1];
                        if (!(value instanceof JSString)) {
                            stack[sp - 1] = JSTypeConversions.toString(context, value);
                        }
                        pc += op.getSize();
                    }
                    case TYPEOF -> {
                        valueStack.stackTop = sp;
                        handleTypeof();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case DELETE -> {
                        valueStack.stackTop = sp;
                        handleDelete();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case IS_UNDEFINED_OR_NULL -> {
                        valueStack.stackTop = sp;
                        handleIsUndefinedOrNull();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case IS_UNDEFINED -> {
                        JSValue value = (JSValue) stack[sp - 1];
                        stack[sp - 1] = JSBoolean.valueOf(value.isUndefined());
                        pc += op.getSize();
                    }
                    case IS_NULL -> {
                        JSValue value = (JSValue) stack[sp - 1];
                        stack[sp - 1] = JSBoolean.valueOf(value.isNull());
                        pc += op.getSize();
                    }
                    case TYPEOF_IS_UNDEFINED -> {
                        JSValue value = (JSValue) stack[sp - 1];
                        stack[sp - 1] = JSBoolean.valueOf("undefined".equals(JSTypeChecking.typeof(value)));
                        pc += op.getSize();
                    }
                    case TYPEOF_IS_FUNCTION -> {
                        JSValue value = (JSValue) stack[sp - 1];
                        stack[sp - 1] = JSBoolean.valueOf("function".equals(JSTypeChecking.typeof(value)));
                        pc += op.getSize();
                    }

                    // ==================== Async Operations ====================
                    case ITERATOR_CHECK_OBJECT -> {
                        JSValue iteratorResult = (JSValue) stack[sp - 1];
                        if (!(iteratorResult instanceof JSObject)) {
                            throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
                        }
                        pc += op.getSize();
                    }
                    case ITERATOR_GET_VALUE_DONE -> {
                        JSValue iteratorResult = (JSValue) stack[sp - 1];
                        if (!(iteratorResult instanceof JSObject iteratorResultObject)) {
                            throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
                        }

                        JSValue doneValue = iteratorResultObject.get(KEY_DONE);
                        JSValue value = iteratorResultObject.get(KEY_VALUE);
                        if (value == null) {
                            value = JSUndefined.INSTANCE;
                        }
                        boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();

                        stack[sp - 1] = value;
                        stack[sp - 2] = JSNumber.of(0);
                        stack[sp++] = JSBoolean.valueOf(done);
                        pc += op.getSize();
                    }
                    case ITERATOR_CLOSE -> {
                        sp--; // catch_offset
                        sp--; // next method
                        JSValue iteratorValue = (JSValue) stack[--sp];
                        if (iteratorValue instanceof JSObject iteratorObject && !iteratorValue.isUndefined()) {
                            JSValue returnMethodValue = iteratorObject.get(KEY_RETURN);
                            if (returnMethodValue instanceof JSFunction returnMethod) {
                                JSValue closeResult = returnMethod.call(context, iteratorObject, EMPTY_ARGS);
                                if (context.hasPendingException()) {
                                    pendingException = context.getPendingException();
                                    context.clearPendingException();
                                } else if (!(closeResult instanceof JSObject)) {
                                    // Per ES2024 7.4.6 IteratorClose step 6:
                                    // If innerResult.[[Value]] is not an Object, throw TypeError
                                    pendingException = context.throwTypeError("iterator result is not an object");
                                }
                            } else if (returnMethodValue.isUndefined() || returnMethodValue.isNull()) {
                                // No return method - that's fine, skip
                            } else if (returnMethodValue instanceof JSObject returnObj && returnObj.isHTMLDDA()) {
                                // IsHTMLDDA: typeof is "undefined" but it IS callable
                                // This shouldn't happen since IsHTMLDDA is a JSFunction,
                                // but handle for completeness
                            } else {
                                pendingException = context.throwTypeError("iterator return is not a function");
                            }
                        }
                        pc += op.getSize();
                    }
                    case ITERATOR_NEXT -> {
                        JSValue argumentValue = (JSValue) stack[sp - 1];
                        JSValue catchOffset = (JSValue) stack[sp - 2];
                        JSValue nextMethodValue = (JSValue) stack[sp - 3];
                        JSValue iteratorValue = (JSValue) stack[sp - 4];
                        if (!(nextMethodValue instanceof JSFunction nextMethod)) {
                            throw new JSVirtualMachineException(context.throwTypeError("iterator next is not a function"));
                        }
                        JSValue nextResult = nextMethod.call(context, iteratorValue, new JSValue[]{argumentValue});
                        stack[sp - 1] = nextResult;
                        stack[sp - 2] = catchOffset;
                        pc += op.getSize();
                    }
                    case ITERATOR_CALL -> {
                        int flags = bytecode.readU8(pc + 1);
                        JSValue argumentValue = (JSValue) stack[sp - 1];
                        JSValue iteratorValue = (JSValue) stack[sp - 4];
                        if (!(iteratorValue instanceof JSObject iteratorObject)) {
                            throw new JSVirtualMachineException(context.throwTypeError("iterator call target must be an object"));
                        }

                        String methodName = (flags & 1) != 0 ? "throw" : "return";
                        JSValue methodValue = (flags & 1) != 0
                                ? iteratorObject.get(KEY_THROW)
                                : iteratorObject.get(KEY_RETURN);
                        boolean noMethod = methodValue.isUndefined() || methodValue.isNull();
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
                        pc += op.getSize();
                    }
                    case AWAIT -> {
                        valueStack.stackTop = sp;
                        handleAwait();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case FOR_AWAIT_OF_START -> {
                        valueStack.stackTop = sp;
                        handleForAwaitOfStart();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case FOR_AWAIT_OF_NEXT -> {
                        valueStack.stackTop = sp;
                        handleForAwaitOfNext();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case FOR_OF_START -> {
                        valueStack.stackTop = sp;
                        handleForOfStart();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case FOR_OF_NEXT -> {
                        int depth = bytecode.readU8(pc + 1);  // Read the depth parameter
                        valueStack.stackTop = sp;
                        handleForOfNext(depth);
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case FOR_IN_START -> {
                        valueStack.stackTop = sp;
                        handleForInStart();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case FOR_IN_NEXT -> {
                        valueStack.stackTop = sp;
                        handleForInNext();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }
                    case FOR_IN_END -> {
                        valueStack.stackTop = sp;
                        handleForInEnd();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                    }

                    // ==================== Generator Operations ====================
                    case INITIAL_YIELD -> {
                        valueStack.stackTop = sp;
                        handleInitialYield();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                        // Check if we should suspend (initial yield during generator creation)
                        if (yieldResult != null) {
                            // Return undefined - execution will resume from here on first .next()
                            sp = savedStackTop;
                            valueStack.stackTop = sp;
                            currentFrame = previousFrame;
                            if (savedStrictMode) {
                                context.enterStrictMode();
                            } else {
                                context.exitStrictMode();
                            }
                            return JSUndefined.INSTANCE;
                        }
                    }
                    case YIELD -> {
                        valueStack.stackTop = sp;
                        handleYield();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                        // Check if we should suspend (generator yielded)
                        if (yieldResult != null) {
                            // Return the yielded value - execution will resume here on next()
                            JSValue returnValue = (JSValue) stack[--sp];
                            sp = savedStackTop;
                            valueStack.stackTop = sp;
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
                        valueStack.stackTop = sp;
                        handleYieldStar();
                        sp = valueStack.stackTop;
                        pc += op.getSize();
                        // Check if we should suspend
                        if (yieldResult != null) {
                            JSValue returnValue = (JSValue) stack[--sp];
                            sp = savedStackTop;
                            valueStack.stackTop = sp;
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
                        valueStack.stackTop = sp;
                        handleAsyncYieldStar();
                        sp = valueStack.stackTop;
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

        // Fast path for number addition (avoids toNumber overhead)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of(leftNum.value() + rightNum.value()));
            return;
        }

        // String concatenation or numeric addition
        if (left instanceof JSString || right instanceof JSString) {
            String leftStr = JSTypeConversions.toString(context, left).value();
            String rightStr = JSTypeConversions.toString(context, right).value();
            valueStack.push(new JSString(leftStr + rightStr));
        } else {
            double leftNum = JSTypeConversions.toNumber(context, left).value();
            double rightNum = JSTypeConversions.toNumber(context, right).value();
            valueStack.push(JSNumber.of(leftNum + rightNum));
        }
    }

    private void handleAnd() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Fast path for number AND (avoids toInt32 overhead)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() & (int) rightNum.value()));
            return;
        }
        int result = JSTypeConversions.toInt32(context, left) & JSTypeConversions.toInt32(context, right);
        valueStack.push(JSNumber.of(result));
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
            JSValue result = constructFunction(jsFunction, args);
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

    private void handleDec() {
        JSValue operand = valueStack.pop();
        if (operand instanceof JSNumber num) {
            valueStack.push(JSNumber.of(num.value() - 1));
            return;
        }
        double result = JSTypeConversions.toNumber(context, operand).value() - 1;
        valueStack.push(JSNumber.of(result));
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
        valueStack.push(JSNumber.of(result));
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
        valueStack.push(JSNumber.of(result));
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
        JSValue asyncIteratorMethod = iterableObj.get(PropertyKey.fromSymbol(JSSymbol.ASYNC_ITERATOR));
        JSValue iteratorMethod = null;

        if (asyncIteratorMethod instanceof JSFunction) {
            iteratorMethod = asyncIteratorMethod;
        } else if (asyncIteratorMethod != null && !asyncIteratorMethod.isUndefined() && !asyncIteratorMethod.isNull()) {
            // Non-callable, non-nullish value: TypeError
            pendingException = context.throwTypeError("object is not async iterable");
            return;
        } else {
            // Fall back to Symbol.iterator (sync iterator that will be auto-wrapped)
            iteratorMethod = iterableObj.get(PropertyKey.fromSymbol(JSSymbol.ITERATOR));

            if (!(iteratorMethod instanceof JSFunction)) {
                pendingException = context.throwTypeError("object is not async iterable");
                return;
            }
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
        JSValue nextMethod = iteratorObj.get(KEY_NEXT);

        if (!(nextMethod instanceof JSFunction)) {
            pendingException = context.throwTypeError("iterator must have a next method");
            return;
        }

        // Push iterator, next method, and catch offset (0) onto the stack
        valueStack.push(iterator);         // Iterator object
        valueStack.push(nextMethod);       // next() method
        valueStack.push(JSNumber.of(0));  // Catch offset (placeholder)
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

        // For sync iterators, extract value and done from the result object
        // QuickJS FOR_OF_NEXT pushes: iter, next, catch_offset, value, done
        // So we need to extract {value, done} from result

        if (!(result instanceof JSObject resultObj)) {
            throw new JSVirtualMachineException("Iterator result must be an object");
        }

        // Get the value property
        JSValue value = resultObj.get(KEY_VALUE);
        if (value == null) {
            value = JSUndefined.INSTANCE;
        }

        // Get the done property
        JSValue doneValue = resultObj.get(KEY_DONE);
        boolean done = false;
        if (doneValue instanceof JSBoolean boolVal) {
            done = boolVal.isBooleanTrue();
        }

        // Push catch_offset back, then restore temp values, then push value and done
        valueStack.push(catchOffset);
        for (int i = depth - 1; i >= 0; i--) {
            valueStack.push(forOfTempValues[i]);
            forOfTempValues[i] = null;
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
        JSValue iterator = iteratorFunc.call(context, iterable, EMPTY_ARGS);

        if (!(iterator instanceof JSObject iteratorObj)) {
            throw new JSVirtualMachineException("Iterator method must return an object");
        }

        // Get the next() method from the iterator
        JSValue nextMethod = iteratorObj.get(KEY_NEXT);

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
        if (operand instanceof JSNumber num) {
            valueStack.push(JSNumber.of(num.value() + 1));
            return;
        }
        double result = JSTypeConversions.toNumber(context, operand).value() + 1;
        valueStack.push(JSNumber.of(result));
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
        // Fast path for number comparison (avoids toPrimitive/toNumber overhead)
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSBoolean.valueOf(leftNum.value() < rightNum.value()));
            return;
        }
        boolean result = JSTypeConversions.lessThan(context, left, right);
        valueStack.push(JSBoolean.valueOf(result));
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

    private void handleMod() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, left).value() % JSTypeConversions.toNumber(context, right).value();
        valueStack.push(JSNumber.of(result));
    }

    private void handleMul() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of(leftNum.value() * rightNum.value()));
            return;
        }
        double result = JSTypeConversions.toNumber(context, left).value() * JSTypeConversions.toNumber(context, right).value();
        valueStack.push(JSNumber.of(result));
    }

    private void handleNeg() {
        JSValue operand = valueStack.pop();
        double result = -JSTypeConversions.toNumber(context, operand).value();
        valueStack.push(JSNumber.of(result));
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
        valueStack.push(JSNumber.of(result));
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
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() | (int) rightNum.value()));
            return;
        }
        int result = JSTypeConversions.toInt32(context, left) | JSTypeConversions.toInt32(context, right);
        valueStack.push(JSNumber.of(result));
    }

    private void handlePlus() {
        JSValue operand = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, operand).value();
        valueStack.push(JSNumber.of(result));
    }

    private void handlePostDec() {
        // POST_DEC: [value] -> [old_value, new_value]
        // Takes value on top, pushes old value then new value
        JSValue operand = valueStack.pop();
        if (operand instanceof JSNumber num) {
            double oldValue = num.value();
            valueStack.push(num);
            valueStack.push(JSNumber.of(oldValue - 1));
            return;
        }
        double oldValue = JSTypeConversions.toNumber(context, operand).value();
        double newValue = oldValue - 1;
        valueStack.push(JSNumber.of(oldValue));
        valueStack.push(JSNumber.of(newValue));
    }

    private void handlePostInc() {
        // POST_INC: [value] -> [old_value, new_value]
        // Takes value on top, pushes old value then new value
        JSValue operand = valueStack.pop();
        if (operand instanceof JSNumber num) {
            double oldValue = num.value();
            valueStack.push(num);
            valueStack.push(JSNumber.of(oldValue + 1));
            return;
        }
        double oldValue = JSTypeConversions.toNumber(context, operand).value();
        double newValue = oldValue + 1;
        valueStack.push(JSNumber.of(oldValue));
        valueStack.push(JSNumber.of(newValue));
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
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() >> ((int) rightNum.value() & 0x1F)));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(context, left);
        int rightInt = JSTypeConversions.toInt32(context, right);
        valueStack.push(JSNumber.of(leftInt >> (rightInt & 0x1F)));
    }

    private void handleShl() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() << ((int) rightNum.value() & 0x1F)));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(context, left);
        int rightInt = JSTypeConversions.toInt32(context, right);
        valueStack.push(JSNumber.of(leftInt << (rightInt & 0x1F)));
    }

    private void handleShr() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of(((int) leftNum.value() >>> ((int) rightNum.value() & 0x1F)) & 0xFFFFFFFFL));
            return;
        }
        int leftInt = JSTypeConversions.toInt32(context, left);
        int rightInt = JSTypeConversions.toInt32(context, right);
        valueStack.push(JSNumber.of((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
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
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of(leftNum.value() - rightNum.value()));
            return;
        }
        double result = JSTypeConversions.toNumber(context, left).value() - JSTypeConversions.toNumber(context, right).value();
        valueStack.push(JSNumber.of(result));
    }

    private void handleTypeof() {
        JSValue operand = valueStack.pop();
        String type = JSTypeChecking.typeof(operand);
        valueStack.push(new JSString(type));
    }

    private void handleXor() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        if (left instanceof JSNumber leftNum && right instanceof JSNumber rightNum) {
            valueStack.push(JSNumber.of((int) leftNum.value() ^ (int) rightNum.value()));
            return;
        }
        int result = JSTypeConversions.toInt32(context, left) ^ JSTypeConversions.toInt32(context, right);
        valueStack.push(JSNumber.of(result));
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
        JSValue iteratorMethod = iterableObj.get(PropertyKey.fromSymbol(JSSymbol.ITERATOR));
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
            JSValue returnMethodValue = iteratorObj.get(KEY_RETURN);
            boolean noReturnMethod = returnMethodValue.isUndefined() || returnMethodValue.isNull();

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

            // Check result is an object (per spec, TypeError if not)
            if (!(result instanceof JSObject)) {
                throw new JSVirtualMachineException(context.throwTypeError("iterator must return an object"));
            }

            // Check done flag
            JSValue doneValue = ((JSObject) result).get(KEY_DONE);
            if (JSTypeConversions.toBoolean(doneValue).value()) {
                // Done - push value and complete (don't yield)
                JSValue value = ((JSObject) result).get(KEY_VALUE);
                valueStack.push(value);
                return;
            } else {
                // Not done - yield the result and continue delegation
                yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result);
                valueStack.push(result);
                return;
            }
        }

        if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
            generatorResumeIndex++; // consume the record
            JSValue throwValue = resumeRecord.value();

            // Get "throw" method from iterator
            JSValue throwMethodValue = iteratorObj.get(KEY_THROW);
            boolean noThrowMethod = throwMethodValue.isUndefined() || throwMethodValue.isNull();

            if (noThrowMethod) {
                // No throw method - close iterator and throw TypeError
                JSValue closeMethod = iteratorObj.get(KEY_RETURN);
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
            JSValue doneValue = ((JSObject) result).get(KEY_DONE);
            if (JSTypeConversions.toBoolean(doneValue).value()) {
                // Done - push value and complete the yield* expression
                JSValue value = ((JSObject) result).get(KEY_VALUE);
                valueStack.push(value);
                return;
            } else {
                // Not done - yield the result
                yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result);
                valueStack.push(result);
                return;
            }
        }

        // Default: NEXT protocol - call iterator.next()
        JSValue nextMethod = iteratorObj.get(KEY_NEXT);
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            throw new JSVirtualMachineException("Iterator must have a next method");
        }

        JSValue result = nextFunc.call(context, iterator, EMPTY_ARGS);

        // The result should be an object (the iterator result)
        if (!(result instanceof JSObject)) {
            throw new JSVirtualMachineException("Iterator result must be an object");
        }

        // Check if the inner iterator is done
        JSValue doneValue = ((JSObject) result).get(KEY_DONE);
        if (JSTypeConversions.toBoolean(doneValue).value()) {
            // Inner iterator done - yield* expression value is the final value
            JSValue value = ((JSObject) result).get(KEY_VALUE);
            valueStack.push(value);
            // Don't set yieldResult - the yield* expression completes
            return;
        }

        // Set yield result to the raw iterator result object
        // This is what QuickJS does with *pdone = 2
        yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result);

        // Push the result back on the stack
        valueStack.push(result);
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

        JSValue prototypeValue = constructorObject.get(KEY_PROTOTYPE, context);
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
        if (trackPropertyAccess) {
            this.propertyAccessChain.setLength(0);
        }
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
            JSValue messageValue = exceptionObj.get(KEY_MESSAGE, null);
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
            JSValue nameValue = exceptionObj.get(KEY_NAME, null);
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

        JSObject global = context.getGlobalObject();

        // Auto-box primitives
        if (value instanceof JSString str) {
            // Get String.prototype from global object
            JSValue stringCtor = global.get("String");
            if (stringCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(KEY_PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    // Create a temporary wrapper object with String.prototype
                    JSObject wrapper = new JSObject();
                    wrapper.setPrototype(protoObj);
                    // Store the primitive value
                    wrapper.setPrimitiveValue(str);
                    // Add length property as own property (shadows prototype's length)
                    // This is a data property with the actual string length
                    wrapper.definePropertyReadonlyNonConfigurable("length", JSNumber.of(str.value().length()));
                    return wrapper;
                }
            }
        }

        if (value instanceof JSNumber num) {
            // Get Number.prototype from global object
            JSValue numberCtor = global.get("Number");
            if (numberCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(KEY_PROTOTYPE);
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
            JSValue booleanCtor = global.get("Boolean");
            if (booleanCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(KEY_PROTOTYPE);
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
            JSValue bigIntCtor = global.get("BigInt");
            if (bigIntCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(KEY_PROTOTYPE);
                if (prototype instanceof JSObject protoObj) {
                    JSBigIntObject wrapper = new JSBigIntObject(bigInt);
                    wrapper.setPrototype(protoObj);
                    return wrapper;
                }
            }
        }

        if (value instanceof JSSymbol sym) {
            // Get Symbol.prototype from global object
            JSValue symbolCtor = global.get("Symbol");
            if (symbolCtor instanceof JSObject ctorObj) {
                JSValue prototype = ctorObj.get(KEY_PROTOTYPE);
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
}
