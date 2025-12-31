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
import com.caoccao.qjs4j.builtins.SharedArrayBufferConstructor;
import com.caoccao.qjs4j.builtins.SymbolConstructor;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;

/**
 * The JavaScript virtual machine bytecode interpreter.
 * Executes compiled bytecode using a stack-based architecture.
 */
public final class VirtualMachine {
    private final JSContext context;
    private final CallStack valueStack;
    private StackFrame currentFrame;
    private JSValue pendingException;

    public VirtualMachine(JSContext context) {
        this.valueStack = new CallStack();
        this.context = context;
        this.currentFrame = null;
        this.pendingException = null;
    }

    /**
     * Clear the pending exception in the VM.
     * This is needed when an async function catches an exception.
     */
    public void clearPendingException() {
        this.pendingException = null;
    }

    // ==================== Arithmetic Operation Handlers ====================

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
                    boolean foundHandler = false;
                    while (valueStack.getStackTop() > 0) {
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
                        throw new VMException("Unhandled exception: " + exception);
                    }

                    // Continue execution at catch handler
                    continue;
                }

                int opcode = bytecode.readOpcode(pc);
                Opcode op = Opcode.fromInt(opcode);

                switch (op) {
                    // ==================== Constants and Literals ====================
                    case INVALID -> throw new VMException("Invalid opcode at PC " + pc);
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
                    case SWAP -> {
                        JSValue v1 = valueStack.pop();
                        JSValue v2 = valueStack.pop();
                        valueStack.push(v1);
                        valueStack.push(v2);
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
                        currentFrame.getLocals()[putLocalIndex] = valueStack.pop();
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
                            valueStack.push(targetObj.get(PropertyKey.fromString(fieldName), context));
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
                            valueStack.push(targetObj.get(key, context));
                        } else {
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
                        JSArray array = new JSArray();
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
                        throw new VMException("Exception thrown: " + exception);
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

                    // ==================== Async Operations ====================
                    case AWAIT -> {
                        handleAwait();
                        pc += op.getSize();
                    }

                    // ==================== Other Operations ====================
                    default -> throw new VMException("Unimplemented opcode: " + op + " at PC " + pc);
                }
            }
        } catch (VMException e) {
            // Restore stack and strict mode on exception
            valueStack.setStackTop(savedStackTop);
            currentFrame = previousFrame;
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
            if (savedStrictMode) {
                context.enterStrictMode();
            } else {
                context.exitStrictMode();
            }
            throw new VMException("VM error: " + e.getMessage(), e);
        }
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

        // Push the promise onto the stack
        // Note: For a full implementation with proper async/await support,
        // we would need to suspend the current execution context here
        // and resume it when the promise settles. For now, this simple
        // implementation just returns the promise value.
        valueStack.push(promise);
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
            return;
        }

        // Special handling for Symbol constructor (must be called without new)
        if (callee instanceof JSObject calleeObj) {
            if (calleeObj.getConstructorType() == ConstructorType.SYMBOL) {
                // Call Symbol() function
                JSValue result = SymbolConstructor.call(context, receiver, args);
                valueStack.push(result);
                return;
            }

            // Special handling for BigInt constructor (must be called without new)
            if (calleeObj.getConstructorType() == ConstructorType.BIG_INT) {
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
        } else {
            throw new VMException("Cannot call non-function value");
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
        if (constructor instanceof JSProxy proxy) {
            // Following QuickJS JS_CallConstructorInternal:
            // Check if target is a constructor BEFORE checking for construct trap
            JSValue target = proxy.getTarget();
            if (!(target instanceof JSFunction)) {
                throw new JSException(context.throwTypeError("proxy is not a constructor"));
            }

            JSValue result = proxyConstruct(proxy, args);
            valueStack.push(result);
            return;
        }

        // Check for ES6 class constructor
        if (constructor instanceof JSClass classConstructor) {
            // Use the class's construct() method
            JSObject instance = classConstructor.construct(context, args);
            valueStack.push(instance);
            return;
        }

        // Check for Boolean constructor (must come before generic JSFunction check)
        if (constructor instanceof JSObject ctorObj) {
            if (ctorObj.getConstructorType() == ConstructorType.BOOLEAN) {
                // Get the value to convert to boolean
                JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

                // Convert to boolean using ToBoolean
                JSBoolean boolValue = JSTypeConversions.toBoolean(value);

                // Create Boolean object wrapper
                JSBooleanObject boolObj = new JSBooleanObject(boolValue);

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    boolObj.setPrototype(prototype);
                }

                valueStack.push(boolObj);
                return;
            }

            // Check for Number constructor (must come before generic JSFunction check)
            if (ctorObj.getConstructorType() == ConstructorType.NUMBER) {
                // ES2020: If no argument is passed, use +0
                JSNumber numValue;
                if (args.length == 0) {
                    numValue = new JSNumber(0.0);
                } else {
                    // Convert to number using ToNumber
                    numValue = JSTypeConversions.toNumber(context, args[0]);
                }

                // Create Number object wrapper
                JSNumberObject numObj = new JSNumberObject(numValue);

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    numObj.setPrototype(prototype);
                }

                valueStack.push(numObj);
                return;
            }

            // Check for String constructor (must come before generic JSFunction check)
            if (ctorObj.getConstructorType() == ConstructorType.STRING) {
                // ES2020: If no argument is passed, use empty string
                JSString strValue;
                if (args.length == 0) {
                    strValue = new JSString("");
                } else {
                    // Convert to string using ToString
                    strValue = JSTypeConversions.toString(context, args[0]);
                }

                // Create String object wrapper
                JSStringObject strObj = new JSStringObject(strValue);

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    strObj.setPrototype(prototype);
                }

                valueStack.push(strObj);
                return;
            }

            // Check for BigInt constructor
            // Note: BigInt cannot be called with 'new' operator per ES2020 spec
            if (ctorObj.getConstructorType() == ConstructorType.BIG_INT) {
                // ES2020: BigInt cannot be used as a constructor
                valueStack.push(context.throwTypeError("BigInt is not a constructor"));
                return;
            }

            // Check for Symbol constructor
            // Note: Symbol cannot be called with 'new' operator per ES2020 spec
            if (ctorObj.getConstructorType() == ConstructorType.SYMBOL) {
                // ES2020: Symbol cannot be used as a constructor
                valueStack.push(context.throwTypeError("Symbol is not a constructor"));
                return;
            }
        }

        if (constructor instanceof JSFunction func) {
            // Following QuickJS js_create_from_ctor:
            // Create new object with prototype from constructor.prototype
            JSObject newObj = new JSObject();

            // Get the prototype property from the constructor
            JSValue prototypeValue = func.get("prototype");
            if (prototypeValue instanceof JSObject prototypeObj) {
                newObj.setPrototype(prototypeObj);
            }

            // Call constructor with new object as this
            JSValue result;
            if (constructor instanceof JSNativeFunction nativeFunc) {
                result = nativeFunc.call(context, newObj, args);
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
                    throw new VMException(errorMsg);
                }
            } else if (constructor instanceof JSBytecodeFunction bytecodeFunc) {
                result = execute(bytecodeFunc, newObj, args);
            } else {
                result = JSUndefined.INSTANCE;
            }

            // Following QuickJS JS_CallConstructorInternal:
            // If constructor returns an object, use that; otherwise use newObj
            if (result instanceof JSObject) {
                valueStack.push(result);
            } else {
                valueStack.push(newObj);
            }
        } else if (constructor instanceof JSObject ctorObj) {
            // Check for ES6 class constructor marker
            JSValue isClassCtor = ctorObj.get("[[ClassConstructor]]");
            if (isClassCtor instanceof JSBoolean && ((JSBoolean) isClassCtor).value()) {
                context.throwTypeError("Class constructor cannot be invoked without 'new'");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            // Check for Date constructor
            if (ctorObj.getConstructorType() == ConstructorType.DATE) {
                // Create Date object
                long timeValue;
                if (args.length == 0) {
                    // No arguments: current time
                    timeValue = System.currentTimeMillis();
                } else if (args.length == 1) {
                    // Single argument: timestamp or parseable string
                    JSValue arg = args[0];
                    if (arg instanceof JSNumber num) {
                        timeValue = (long) num.value();
                    } else {
                        // Try to parse as string
                        JSString str = JSTypeConversions.toString(context, arg);
                        // Simplified: just use current time for now
                        timeValue = System.currentTimeMillis();
                    }
                } else {
                    // Multiple arguments: year, month, date, etc.
                    // Simplified: just use current time for now
                    timeValue = System.currentTimeMillis();
                }

                JSDate dateObj = new JSDate(timeValue);

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    dateObj.setPrototype(prototype);
                }

                valueStack.push(dateObj);
                return;
            }

            // Check for Symbol constructor (throws error when used with new)
            if (ctorObj.getConstructorType() == ConstructorType.SYMBOL) {
                context.throwTypeError("Symbol is not a constructor");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }

            // Check for BigInt constructor (throws error when used with new)
            if (ctorObj.getConstructorType() == ConstructorType.BIG_INT) {
                context.throwTypeError("BigInt is not a constructor");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }

            // Check for RegExp constructor
            if (ctorObj.getConstructorType() == ConstructorType.REGEXP) {
                // Create RegExp object
                String pattern = "";
                String flags = "";

                if (args.length > 0) {
                    // First argument is the pattern
                    JSValue patternArg = args[0];
                    if (patternArg instanceof JSRegExp existingRegExp) {
                        // Copy from existing RegExp
                        pattern = existingRegExp.getPattern();
                        flags = args.length > 1 && !(args[1] instanceof JSUndefined)
                                ? JSTypeConversions.toString(context, args[1]).value()
                                : existingRegExp.getFlags();
                    } else if (!(patternArg instanceof JSUndefined)) {
                        pattern = JSTypeConversions.toString(context, patternArg).value();
                        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
                            flags = JSTypeConversions.toString(context, args[1]).value();
                        }
                    }
                }

                try {
                    JSRegExp regexpObj = new JSRegExp(pattern, flags);

                    // Set prototype
                    JSValue prototypeValue = ctorObj.get("prototype");
                    if (prototypeValue instanceof JSObject prototype) {
                        regexpObj.setPrototype(prototype);
                    }

                    valueStack.push(regexpObj);
                } catch (Exception e) {
                    // Invalid regex - throw SyntaxError
                    context.throwSyntaxError("Invalid regular expression: " + e.getMessage());
                    valueStack.push(JSUndefined.INSTANCE);
                }
                return;
            }

            // Check for Map constructor
            if (ctorObj.getConstructorType() == ConstructorType.MAP) {
                // Create Map object
                JSMap mapObj = new JSMap();

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    mapObj.setPrototype(prototype);
                }

                // If an iterable is provided, populate the map
                if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
                    JSValue iterableArg = args[0];

                    // Handle array directly for efficiency
                    if (iterableArg instanceof JSArray arr) {
                        for (long i = 0; i < arr.getLength(); i++) {
                            JSValue entry = arr.get((int) i);
                            if (!(entry instanceof JSObject entryObj)) {
                                context.throwTypeError("Iterator value must be an object");
                                valueStack.push(JSUndefined.INSTANCE);
                                return;
                            }

                            // Get key and value from entry [key, value]
                            JSValue key = entryObj.get(0);
                            JSValue value = entryObj.get(1);
                            mapObj.mapSet(key, value);
                        }
                    } else if (iterableArg instanceof JSObject) {
                        // Try to get iterator
                        JSValue iterator = JSIteratorHelper.getIterator(iterableArg, context);
                        if (iterator instanceof JSIterator iter) {
                            // Iterate and populate
                            while (true) {
                                JSObject nextResult = iter.next();
                                if (nextResult == null) {
                                    break;
                                }

                                JSValue done = nextResult.get("done");
                                if (done == JSBoolean.TRUE) {
                                    break;
                                }

                                JSValue entry = nextResult.get("value");
                                if (!(entry instanceof JSObject entryObj)) {
                                    context.throwTypeError("Iterator value must be an object");
                                    valueStack.push(JSUndefined.INSTANCE);
                                    return;
                                }

                                // Get key and value from entry [key, value]
                                JSValue key = entryObj.get(0);
                                JSValue value = entryObj.get(1);
                                mapObj.mapSet(key, value);
                            }
                        }
                    }
                }

                valueStack.push(mapObj);
                return;
            }

            // Check for Set constructor
            if (ctorObj.getConstructorType() == ConstructorType.SET) {
                // Create Set object
                JSSet setObj = new JSSet();

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    setObj.setPrototype(prototype);
                }

                // If an iterable is provided, populate the set
                if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
                    JSValue iterableArg = args[0];

                    // Handle array directly for efficiency
                    if (iterableArg instanceof JSArray arr) {
                        for (long i = 0; i < arr.getLength(); i++) {
                            JSValue value = arr.get((int) i);
                            setObj.setAdd(value);
                        }
                    } else if (iterableArg instanceof JSObject) {
                        // Try to get iterator
                        JSValue iterator = JSIteratorHelper.getIterator(iterableArg, context);
                        if (iterator instanceof JSIterator iter) {
                            // Iterate and populate
                            while (true) {
                                JSObject nextResult = iter.next();
                                if (nextResult == null) {
                                    break;
                                }

                                JSValue done = nextResult.get("done");
                                if (done == JSBoolean.TRUE) {
                                    break;
                                }

                                JSValue value = nextResult.get("value");
                                setObj.setAdd(value);
                            }
                        }
                    }
                }

                valueStack.push(setObj);
                return;
            }

            // Check for WeakMap constructor
            if (ctorObj.getConstructorType() == ConstructorType.WEAK_MAP) {
                // Create WeakMap object
                JSWeakMap weakMapObj = new JSWeakMap();

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    weakMapObj.setPrototype(prototype);
                }

                // If an iterable is provided, populate the weakmap
                if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
                    JSValue iterableArg = args[0];

                    // Handle array directly for efficiency
                    if (iterableArg instanceof JSArray arr) {
                        for (long i = 0; i < arr.getLength(); i++) {
                            JSValue entry = arr.get((int) i);
                            if (!(entry instanceof JSObject entryObj)) {
                                context.throwTypeError("Iterator value must be an object");
                                valueStack.push(JSUndefined.INSTANCE);
                                return;
                            }

                            // Get key and value from entry [key, value]
                            JSValue key = entryObj.get(0);
                            JSValue value = entryObj.get(1);

                            // WeakMap requires object keys
                            if (!(key instanceof JSObject)) {
                                context.throwTypeError("WeakMap key must be an object");
                                valueStack.push(JSUndefined.INSTANCE);
                                return;
                            }

                            weakMapObj.weakMapSet((JSObject) key, value);
                        }
                    } else if (iterableArg instanceof JSObject) {
                        // Try to get iterator
                        JSValue iterator = JSIteratorHelper.getIterator(iterableArg, context);
                        if (iterator instanceof JSIterator iter) {
                            // Iterate and populate
                            while (true) {
                                JSObject nextResult = iter.next();
                                if (nextResult == null) {
                                    break;
                                }

                                JSValue done = nextResult.get("done");
                                if (done == JSBoolean.TRUE) {
                                    break;
                                }

                                JSValue entry = nextResult.get("value");
                                if (!(entry instanceof JSObject entryObj)) {
                                    context.throwTypeError("Iterator value must be an object");
                                    valueStack.push(JSUndefined.INSTANCE);
                                    return;
                                }

                                // Get key and value from entry [key, value]
                                JSValue key = entryObj.get(0);
                                JSValue value = entryObj.get(1);

                                // WeakMap requires object keys
                                if (!(key instanceof JSObject)) {
                                    context.throwTypeError("WeakMap key must be an object");
                                    valueStack.push(JSUndefined.INSTANCE);
                                    return;
                                }

                                weakMapObj.weakMapSet((JSObject) key, value);
                            }
                        }
                    }
                }

                valueStack.push(weakMapObj);
                return;
            }

            // Check for WeakSet constructor
            if (ctorObj.getConstructorType() == ConstructorType.WEAK_SET) {
                // Create WeakSet object
                JSWeakSet weakSetObj = new JSWeakSet();

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    weakSetObj.setPrototype(prototype);
                }

                // If an iterable is provided, populate the weakset
                if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
                    JSValue iterableArg = args[0];

                    // Handle array directly for efficiency
                    if (iterableArg instanceof JSArray arr) {
                        for (long i = 0; i < arr.getLength(); i++) {
                            JSValue value = arr.get((int) i);

                            // WeakSet requires object values
                            if (!(value instanceof JSObject)) {
                                context.throwTypeError("WeakSet value must be an object");
                                valueStack.push(JSUndefined.INSTANCE);
                                return;
                            }

                            weakSetObj.weakSetAdd((JSObject) value);
                        }
                    } else if (iterableArg instanceof JSObject) {
                        // Try to get iterator
                        JSValue iterator = JSIteratorHelper.getIterator(iterableArg, context);
                        if (iterator instanceof JSIterator iter) {
                            // Iterate and populate
                            while (true) {
                                JSObject nextResult = iter.next();
                                if (nextResult == null) {
                                    break;
                                }

                                JSValue done = nextResult.get("done");
                                if (done == JSBoolean.TRUE) {
                                    break;
                                }

                                JSValue value = nextResult.get("value");

                                // WeakSet requires object values
                                if (!(value instanceof JSObject)) {
                                    context.throwTypeError("WeakSet value must be an object");
                                    valueStack.push(JSUndefined.INSTANCE);
                                    return;
                                }

                                weakSetObj.weakSetAdd((JSObject) value);
                            }
                        }
                    }
                }

                valueStack.push(weakSetObj);
                return;
            }

            // Check for WeakRef constructor
            if (ctorObj.getConstructorType() == ConstructorType.WEAK_REF) {
                // WeakRef requires exactly 1 argument: target
                if (args.length == 0) {
                    context.throwTypeError("WeakRef constructor requires a target object");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                JSValue result = com.caoccao.qjs4j.builtins.WeakRefConstructor.createWeakRef(context, args[0]);
                if (result instanceof JSWeakRef weakRef) {
                    // Set prototype
                    JSValue prototypeValue = ctorObj.get("prototype");
                    if (prototypeValue instanceof JSObject prototype) {
                        weakRef.setPrototype(prototype);
                    }
                    valueStack.push(weakRef);
                } else {
                    // Error was thrown
                    valueStack.push(result);
                }
                return;
            }

            // Check for FinalizationRegistry constructor
            if (ctorObj.getConstructorType() == ConstructorType.FINALIZATION_REGISTRY) {
                // FinalizationRegistry requires exactly 1 argument: cleanupCallback
                if (args.length == 0) {
                    context.throwTypeError("FinalizationRegistry constructor requires a cleanup callback");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                JSValue result = com.caoccao.qjs4j.builtins.FinalizationRegistryConstructor.createFinalizationRegistry(context, args[0]);
                if (result instanceof JSFinalizationRegistry registry) {
                    // Set prototype
                    JSValue prototypeValue = ctorObj.get("prototype");
                    if (prototypeValue instanceof JSObject prototype) {
                        registry.setPrototype(prototype);
                    }
                    valueStack.push(registry);
                } else {
                    // Error was thrown
                    valueStack.push(result);
                }
                return;
            }

            // Check for Proxy constructor
            if (ctorObj.getConstructorType() == ConstructorType.PROXY) {
                // Proxy requires exactly 2 arguments: target and handler
                if (args.length < 2) {
                    context.throwTypeError("Proxy constructor requires target and handler");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                // Target must be an object (since JSFunction extends JSObject, this covers both)
                JSValue target = args[0];
                if (!(target instanceof JSObject)) {
                    context.throwTypeError("Proxy target must be an object");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                if (!(args[1] instanceof JSObject handler)) {
                    context.throwTypeError("Proxy handler must be an object");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                // Create Proxy object
                JSProxy proxyObj = new JSProxy(target, handler, context);
                valueStack.push(proxyObj);
                return;
            }

            // Check for Promise constructor
            if (ctorObj.getConstructorType() == ConstructorType.PROMISE) {
                // Promise requires an executor function
                if (args.length == 0 || !(args[0] instanceof JSFunction executor)) {
                    context.throwTypeError("Promise constructor requires an executor function");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                // Create Promise object
                JSPromise promiseObj = new JSPromise();

                // Set prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    promiseObj.setPrototype(prototype);
                }

                // Create resolve and reject functions
                JSNativeFunction resolveFunc = new JSNativeFunction("resolve", 1,
                        (childContext, thisArg, funcArgs) -> {
                            JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                            promiseObj.fulfill(value);
                            return JSUndefined.INSTANCE;
                        });

                JSNativeFunction rejectFunc = new JSNativeFunction("reject", 1,
                        (childContext, thisArg, funcArgs) -> {
                            JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                            promiseObj.reject(reason);
                            return JSUndefined.INSTANCE;
                        });

                // Call the executor with resolve and reject
                try {
                    JSValue[] executorArgs = new JSValue[]{resolveFunc, rejectFunc};
                    executor.call(context, JSUndefined.INSTANCE, executorArgs);
                } catch (Exception e) {
                    // If executor throws, reject the promise
                    promiseObj.reject(new JSString("Error in Promise executor: " + e.getMessage()));
                }

                valueStack.push(promiseObj);
                return;
            }

            // Check for SharedArrayBuffer constructor
            if (ctorObj.getConstructorType() == ConstructorType.SHARED_ARRAY_BUFFER) {
                // Get length argument
                JSValue lengthArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

                // Create SharedArrayBuffer
                JSValue result = SharedArrayBufferConstructor.createSharedArrayBuffer(context, lengthArg);

                if (result instanceof JSSharedArrayBuffer sharedArrayBuffer) {
                    // Set prototype
                    JSValue prototypeValue = ctorObj.get("prototype");
                    if (prototypeValue instanceof JSObject prototype) {
                        sharedArrayBuffer.setPrototype(prototype);
                    }
                }

                valueStack.push(result);
                return;
            }

            // Handle Error constructors
            JSValue errorNameValue = ctorObj.get("[[ErrorName]]");
            if (errorNameValue instanceof JSString errorName) {
                // Create Error object
                JSObject errorObj = new JSObject();

                // Get prototype
                JSValue prototypeValue = ctorObj.get("prototype");
                if (prototypeValue instanceof JSObject prototype) {
                    errorObj.setPrototype(prototype);
                }

                // Set name
                errorObj.set("name", errorName);

                // Set message from first argument
                if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
                    JSString message = JSTypeConversions.toString(context, args[0]);
                    errorObj.set("message", message);
                } else {
                    errorObj.set("message", new JSString(""));
                }

                valueStack.push(errorObj);
            } else {
                throw new VMException("Cannot construct non-function value");
            }
        } else {
            throw new VMException("Cannot construct non-function value");
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
            result = jsObj.delete(key);
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

    // ==================== Bitwise Operation Handlers ====================

    private void handleExp() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = Math.pow(JSTypeConversions.toNumber(context, left).value(), JSTypeConversions.toNumber(context, right).value());
        valueStack.push(new JSNumber(result));
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

    private void handleInstanceof() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        // Simplified instanceof check
        boolean result = right instanceof JSFunction && left instanceof JSObject;
        // Simplified
        valueStack.push(JSBoolean.valueOf(result));
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

    // ==================== Comparison Operation Handlers ====================

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

    // ==================== Logical Operation Handlers ====================

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

    private void handleShl() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int leftInt = JSTypeConversions.toInt32(context, left);
        int rightInt = JSTypeConversions.toInt32(context, right);
        valueStack.push(new JSNumber(leftInt << (rightInt & 0x1F)));
    }

    // ==================== Type Operation Handlers ====================

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

    // ==================== Async Operation Handlers ====================

    private void handleStrictNeq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = !JSTypeConversions.strictEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    // ==================== Function Call Handlers ====================

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
        JSArray argArray = new JSArray();
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
        JSArray argArray = new JSArray();
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
     * VM exception for runtime errors.
     */
    public static class VMException extends RuntimeException {
        public VMException(String message) {
            super(message);
        }

        public VMException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
