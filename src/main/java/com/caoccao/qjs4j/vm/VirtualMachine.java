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

/**
 * The JavaScript virtual machine bytecode interpreter.
 * Executes compiled bytecode using a stack-based architecture.
 */
public final class VirtualMachine {
    private final JSContext context;
    private final StringBuilder propertyAccessChain;  // Track last property access for better error messages
    private final CallStack valueStack;
    private StackFrame currentFrame;
    private JSValue pendingException;
    private boolean propertyAccessLock;  // When true, don't update lastPropertyAccess (during argument evaluation)
    private YieldResult yieldResult;  // Set when generator yields
    private int yieldSkipCount;  // How many yields to skip (for resuming generators)

    public VirtualMachine(JSContext context) {
        this.valueStack = new CallStack();
        this.context = context;
        this.currentFrame = null;
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
                        throw new JSVirtualMachineException("Unhandled exception: " + JSTypeConversions.toString(context, exception).value());
                    }

                    // Continue execution at catch handler
                    continue;
                }

                int opcode = bytecode.readOpcode(pc);
                Opcode op = Opcode.fromInt(opcode);

                switch (op) {
                    // ==================== Constants and Literals ====================
                    case INVALID -> throw new JSVirtualMachineException("Invalid opcode at PC " + pc);
                    case PUSH_I32 -> {
                        valueStack.push(new JSNumber(bytecode.readI32(pc + 1)));
                        pc += op.getSize();
                    }
                    case PUSH_CONST -> {
                        int constIndex = bytecode.readU32(pc + 1);
                        JSValue constValue = bytecode.getConstants()[constIndex];

                        // Set prototype for RegExp objects created from literals
                        if (constValue instanceof JSRegExp regexp) {
                            JSValue regexpCtor = context.getGlobalObject().get("RegExp");
                            if (regexpCtor instanceof JSObject ctorObj) {
                                JSValue prototypeValue = ctorObj.get("prototype");
                                if (prototypeValue instanceof JSObject prototype) {
                                    regexp.setPrototype(prototype);
                                }
                            }
                        }

                        valueStack.push(constValue);
                        pc += op.getSize();
                    }
                    case FCLOSURE -> {
                        // Load function from constant pool and create closure
                        int funcIndex = bytecode.readU32(pc + 1);
                        JSValue funcValue = bytecode.getConstants()[funcIndex];
                        // Initialize the function's prototype chain to inherit from Function.prototype
                        if (funcValue instanceof JSFunction func) {
                            func.initializePrototypeChain(context);
                        }
                        // The function is already a JSBytecodeFunction, just push it
                        valueStack.push(funcValue);
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
                    case EXP -> {
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

                    // ==================== Logical Operations ====================
                    case LOGICAL_NOT -> {
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
                    case GET_VAR -> {
                        int getVarAtom = bytecode.readU32(pc + 1);
                        String getVarName = bytecode.getAtoms()[getVarAtom];
                        JSValue varValue = context.getGlobalObject().get(PropertyKey.fromString(getVarName));
                        // Start tracking property access from variable name (unless locked)
                        if (!propertyAccessLock) {
                            resetPropertyAccessTracking();
                            propertyAccessChain.append(getVarName);
                        }
                        valueStack.push(varValue);
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
                    case GET_LOCAL -> {
                        int getLocalIndex = bytecode.readU16(pc + 1);
                        JSValue localValue = currentFrame.getLocals()[getLocalIndex];
                        valueStack.push(localValue);
                        pc += op.getSize();
                    }
                    case PUT_LOCAL -> {
                        int putLocalIndex = bytecode.readU16(pc + 1);
                        JSValue value = valueStack.pop();
                        currentFrame.getLocals()[putLocalIndex] = value;
                        pc += op.getSize();
                    }
                    case SET_LOCAL -> {
                        int setLocalIndex = bytecode.readU16(pc + 1);
                        currentFrame.getLocals()[setLocalIndex] = valueStack.peek(0);
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
                    case GOTO -> {
                        int gotoOffset = bytecode.readI32(pc + 1);
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
                    case CALL -> {
                        int argCount = bytecode.readU16(pc + 1);
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
                        valueStack.push(new JSObject());
                        pc += op.getSize();
                    }
                    case ARRAY_NEW -> {
                        JSArray array = context.createJSArray();
                        // Set prototype to Array.prototype
                        JSObject arrayPrototype = (JSObject) context.getGlobalObject().get("Array");
                        if (arrayPrototype instanceof JSObject) {
                            JSValue protoValue = arrayPrototype.get("prototype");
                            if (protoValue instanceof JSObject proto) {
                                array.setPrototype(proto);
                            }
                        }
                        valueStack.push(array);
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

                    // ==================== Type Operations ====================
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

                    // ==================== Async Operations ====================
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
                        int catchOffsetParam = bytecode.readU8(pc + 1);  // Read the U8 parameter
                        handleForOfNext();
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
    public JSValue executeGenerator(GeneratorState state, JSContext context) {
        JSBytecodeFunction function = state.getFunction();
        JSValue thisArg = state.getThisArg();
        JSValue[] args = state.getArgs();

        // Clear any previous yield result
        yieldResult = null;

        // Set yield skip count - we'll skip this many yields to resume from the right place
        // This is a workaround since we're not saving/restoring PC
        yieldSkipCount = state.getYieldCount();

        // Execute (or resume) the generator
        JSValue result = execute(function, thisArg, args);

        // Check if generator yielded
        if (yieldResult != null) {
            // Generator yielded - increment count and update state
            state.incrementYieldCount();
            state.setState(GeneratorState.State.SUSPENDED_YIELD);
            return yieldResult.value();
        } else {
            // Generator completed (returned)
            state.setCompleted(true);
            return result;
        }
    }

    // ==================== Arithmetic Operation Handlers ====================

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
        // TODO: Implement async yield* for async generators
        throw new JSVirtualMachineException("Async yield* expression not yet implemented");
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
            // Check if there's a promise rejection callback
            JSPromiseRejectCallback callback = context.getPromiseRejectCallback();
            if (callback != null) {
                // Callback handles the rejection, set pending exception so catch clause can handle it
                JSValue result = promise.getResult();
                callback.callback(PromiseRejectEvent.PromiseRejectWithNoHandler, promise, result);
                pendingException = result;
                context.setPendingException(result);
            } else {
                // No callback set, follow current design: throw VM exception
                throw new JSVirtualMachineException("Unhandled promise rejection: " + promise.getResult());
            }
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
                // Call through the function's call method to handle async wrapping
                JSValue result = bytecodeFunc.call(context, receiver, args);
                valueStack.push(result);
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
            if (target instanceof JSFunction) {
                valueStack.push(proxyConstruct(jsProxy, args));
            } else {
                context.throwTypeError("proxy is not a constructor");
                valueStack.push(JSUndefined.INSTANCE);
            }
        } else if (constructor instanceof JSClass jsClass) {
            // Check for ES6 class constructor
            // Use the class's construct() method
            JSObject instance = jsClass.construct(context, args);
            valueStack.push(instance);
        } else if (constructor instanceof JSFunction jsFunction) {
            JSConstructorType constructorType = jsFunction.getConstructorType();
            if (constructorType == null) {
                JSObject thisObject = new JSObject();
                // Get the prototype property from the constructor
                JSValue prototypeValue = jsFunction.get("prototype");
                if (prototypeValue instanceof JSObject prototypeObj) {
                    thisObject.setPrototype(prototypeObj);
                }
                // Call constructor with new object as this
                JSValue result;
                if (constructor instanceof JSNativeFunction nativeFunc) {
                    result = nativeFunc.call(context, thisObject, args);
                    // Check for pending exception after native constructor call
                    if (context.hasPendingException()) {
                        // Throw immediately to propagate the exception
                        JSValue exception = context.getPendingException();
                        // Get error message safely
                        String errorMsg = "Unhandled exception in constructor";
                        if (exception instanceof JSObject errorObj) {
                            JSValue msgValue = errorObj.get("message");
                            if (msgValue instanceof JSString msgStr) {
                                errorMsg = msgStr.value();
                            }
                        }
                        throw new JSVirtualMachineException(errorMsg);
                    }
                } else if (constructor instanceof JSBytecodeFunction bytecodeFunc) {
                    result = execute(bytecodeFunc, thisObject, args);
                } else {
                    result = JSUndefined.INSTANCE;
                }
                // Following QuickJS JS_CallConstructorInternal:
                // If constructor returns an object, use that; otherwise use newObj
                if (result instanceof JSObject) {
                    valueStack.push(result);
                } else {
                    valueStack.push(thisObject);
                }
            } else {
                JSObject result = null;
                switch (jsFunction.getConstructorType()) {
                    case BOOLEAN_OBJECT, NUMBER_OBJECT, STRING_OBJECT, BIG_INT_OBJECT, SYMBOL_OBJECT,
                         TYPED_ARRAY_INT8, TYPED_ARRAY_INT16, TYPED_ARRAY_UINT8_CLAMPED, TYPED_ARRAY_UINT8,
                         TYPED_ARRAY_UINT16, TYPED_ARRAY_INT32, TYPED_ARRAY_UINT32, TYPED_ARRAY_FLOAT16,
                         TYPED_ARRAY_FLOAT32, TYPED_ARRAY_FLOAT64, TYPED_ARRAY_BIGINT64, TYPED_ARRAY_BIGUINT64 ->
                            result = constructorType.create(context, args);
                }
                if (result != null) {
                    if (!result.isError()) {
                        result.transferPrototypeFrom(jsFunction);
                    }
                    valueStack.push(result);
                }
            }
        } else if (constructor instanceof JSObject jsObject) {
            JSConstructorType constructorType = jsObject.getConstructorType();
            JSObject resultObject = null;
            switch (constructorType) {
                case DATE, SYMBOL_OBJECT, BIG_INT_OBJECT, PROXY, PROMISE, SHARED_ARRAY_BUFFER,
                     REGEXP, MAP, SET, FINALIZATION_REGISTRY, WEAK_MAP, WEAK_SET, WEAK_REF,
                     ERROR, TYPE_ERROR, RANGE_ERROR, REFERENCE_ERROR, SYNTAX_ERROR,
                     URI_ERROR, EVAL_ERROR, AGGREGATE_ERROR, SUPPRESSED_ERROR ->
                        resultObject = constructorType.create(context, args);
            }
            if (resultObject != null) {
                valueStack.push(resultObject);
            }
        } else {
            throw new JSVirtualMachineException("Cannot construct non-function value");
        }
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

    private void handleForOfNext() {
        // Stack layout before: iter, next, catch_offset (bottom to top)
        // Stack layout after: iter, next, catch_offset, value, done (bottom to top)

        // Pop catch offset temporarily
        JSValue catchOffset = valueStack.pop();

        // Peek next method and iterator (don't pop - they stay for next iteration)
        JSValue nextMethod = valueStack.peek(0);  // next method
        JSValue iterator = valueStack.peek(1);    // iterator object

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

        // Push catch_offset, value, and done onto the stack
        valueStack.push(catchOffset);  // Restore catch_offset
        valueStack.push(value);
        valueStack.push(done ? JSBoolean.TRUE : JSBoolean.FALSE);
    }

    // ==================== Bitwise Operation Handlers ====================

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
        boolean result = false;
        if (right instanceof JSObject jsObj) {
            PropertyKey key = PropertyKey.fromValue(context, left);
            result = jsObj.has(key);
        }
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleInc() {
        JSValue operand = valueStack.pop();
        double result = JSTypeConversions.toNumber(context, operand).value() + 1;
        valueStack.push(new JSNumber(result));
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

        // Get the prototype property from the constructor (right operand)
        JSValue prototypeValue = constructor.get("prototype");

        // If the constructor doesn't have a valid prototype property, return false
        // (This can happen with some built-in functions that aren't constructors)
        if (!(prototypeValue instanceof JSObject constructorPrototype)) {
            valueStack.push(JSBoolean.FALSE);
            return;
        }

        // If left is not an object, instanceof is false
        if (!(left instanceof JSObject obj)) {
            valueStack.push(JSBoolean.FALSE);
            return;
        }

        // Walk the prototype chain of left to see if it matches constructor.prototype
        JSObject currentPrototype = obj.getPrototype();

        while (currentPrototype != null) {
            if (currentPrototype == constructorPrototype) {
                valueStack.push(JSBoolean.TRUE);
                return;
            }
            currentPrototype = currentPrototype.getPrototype();
        }

        valueStack.push(JSBoolean.FALSE);
    }

    // ==================== Comparison Operation Handlers ====================

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

    // ==================== Logical Operation Handlers ====================

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

    private void handleSar() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int leftInt = JSTypeConversions.toInt32(context, left);
        int rightInt = JSTypeConversions.toInt32(context, right);
        valueStack.push(new JSNumber(leftInt >> (rightInt & 0x1F)));
    }

    // ==================== Type Operation Handlers ====================

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

    // ==================== Async Operation Handlers ====================

    private void handleStrictEq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    // ==================== Function Call Handlers ====================

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

    private void handleIsUndefinedOrNull() {
        JSValue value = valueStack.pop();
        boolean result = value instanceof JSNull || value instanceof JSUndefined;
        valueStack.push(result ? JSBoolean.TRUE : JSBoolean.FALSE);
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
            // Don't yield - just continue execution
            // The value is already on the stack from the yield expression
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

    // ==================== Generator Operation Handlers ====================

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
        // Following QuickJS js_proxy_call:
        // Check if target is callable BEFORE checking for apply trap
        JSValue target = proxy.getTarget();
        if (!(target instanceof JSFunction)) {
            throw new JSException(context.throwTypeError("proxy is not a function"));
        }

        // Get the apply trap from the handler
        JSValue applyTrap = proxy.getHandler().get("apply");

        // If no apply trap, forward to target
        if (applyTrap == JSUndefined.INSTANCE || applyTrap == null) {
            // Forward call to target
            if (target instanceof JSNativeFunction nativeFunc) {
                return nativeFunc.call(context, thisArg, args);
            } else if (target instanceof JSBytecodeFunction bytecodeFunc) {
                return execute(bytecodeFunc, thisArg, args);
            } else {
                return JSUndefined.INSTANCE;
            }
        }

        // Check that apply trap is a function
        if (!(applyTrap instanceof JSFunction applyFunc)) {
            throw new JSException(context.throwTypeError("apply trap is not a function"));
        }

        // Create arguments array
        JSArray argArray = context.createJSArray(0, args.length);
        for (JSValue arg : args) {
            argArray.push(arg);
        }

        // Call the apply trap: apply(target, thisArg, argArray)
        JSValue[] trapArgs = new JSValue[]{
                proxy.getTarget(),
                thisArg,
                argArray
        };

        if (applyFunc instanceof JSNativeFunction nativeFunc) {
            return nativeFunc.call(context, proxy.getHandler(), trapArgs);
        } else if (applyFunc instanceof JSBytecodeFunction bytecodeFunc) {
            return execute(bytecodeFunc, proxy.getHandler(), trapArgs);
        } else {
            return JSUndefined.INSTANCE;
        }
    }

    /**
     * Invoke proxy construct trap when calling a proxy with 'new'.
     * Based on QuickJS js_proxy_call_constructor (quickjs.c:50304).
     *
     * @param proxy The proxy being constructed
     * @param args  The arguments
     * @return The constructed object
     */
    private JSValue proxyConstruct(JSProxy proxy, JSValue[] args) {
        // Following QuickJS: target is already validated as a constructor
        // by the caller (handleCallConstructor)
        JSValue target = proxy.getTarget();

        // Get the construct trap from the handler
        JSValue constructTrap = proxy.getHandler().get("construct");

        // If no construct trap, forward to target
        if (constructTrap == JSUndefined.INSTANCE || constructTrap == null) {
            // Forward to target constructor
            // Create a new instance
            JSValue prototypeValue = null;
            if (target instanceof JSObject targetObj) {
                prototypeValue = targetObj.get("prototype");
            }
            JSObject instance = new JSObject();
            if (prototypeValue instanceof JSObject prototype) {
                instance.setPrototype(prototype);
            }

            // Call the function with the new instance as 'this'
            // Target is already validated as JSFunction by caller
            JSValue result;
            if (target instanceof JSNativeFunction nativeFunc) {
                result = nativeFunc.call(context, instance, args);
            } else if (target instanceof JSBytecodeFunction bytecodeFunc) {
                result = execute(bytecodeFunc, instance, args);
            } else {
                // Should never reach here since target is validated as JSFunction
                return instance;
            }

            // If function returned an object, use that; otherwise use instance
            if (result instanceof JSObject) {
                return result;
            } else {
                return instance;
            }
        }

        // Check that construct trap is a function
        if (!(constructTrap instanceof JSFunction constructFunc)) {
            throw new JSException(context.throwTypeError("construct trap is not a function"));
        }

        // Create arguments array
        JSArray argArray = context.createJSArray(0, args.length);
        for (JSValue arg : args) {
            argArray.push(arg);
        }

        // Call the construct trap: construct(target, argArray, newTarget)
        // newTarget is the proxy itself
        JSValue[] trapArgs = new JSValue[]{
                target,
                argArray,
                proxy  // newTarget
        };

        JSValue result;
        if (constructFunc instanceof JSNativeFunction nativeFunc) {
            result = nativeFunc.call(context, proxy.getHandler(), trapArgs);
        } else if (constructFunc instanceof JSBytecodeFunction bytecodeFunc) {
            result = execute(bytecodeFunc, proxy.getHandler(), trapArgs);
        } else {
            return JSUndefined.INSTANCE;
        }

        // Validate that construct trap returned an object
        if (!(result instanceof JSObject)) {
            throw new JSException(context.throwTypeError(
                    "'construct' on proxy: trap returned non-object ('" +
                            JSTypeConversions.toString(context, result) +
                            "')"));
        }

        return result;
    }

    private void resetPropertyAccessTracking() {
        this.propertyAccessChain.setLength(0);
        this.propertyAccessLock = false;
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
                    wrapper.defineProperty(PropertyKey.fromString("length"),
                            PropertyDescriptor.dataDescriptor(new JSNumber(str.value().length()), false, false, false));
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
}
