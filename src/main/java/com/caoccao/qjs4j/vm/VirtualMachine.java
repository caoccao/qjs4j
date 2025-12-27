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
     * Execute a bytecode function.
     */
    public JSValue execute(JSBytecodeFunction function, JSValue thisArg, JSValue[] args) {
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
                        if (val instanceof CatchOffset catchOffset) {
                            // Found catch handler - push exception and jump to it
                            valueStack.push(exception);
                            pc = catchOffset.getOffset();
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
                    case INVALID:
                        throw new VMException("Invalid opcode at PC " + pc);

                    case PUSH_I32:
                        valueStack.push(new JSNumber(bytecode.readI32(pc + 1)));
                        pc += op.getSize();
                        break;

                    case PUSH_CONST:
                        int constIndex = bytecode.readU32(pc + 1);
                        valueStack.push(bytecode.getConstants()[constIndex]);
                        pc += op.getSize();
                        break;

                    case UNDEFINED:
                        valueStack.push(JSUndefined.INSTANCE);
                        pc += op.getSize();
                        break;

                    case NULL:
                        valueStack.push(JSNull.INSTANCE);
                        pc += op.getSize();
                        break;

                    case PUSH_THIS:
                        valueStack.push(currentFrame.getThisArg());
                        pc += op.getSize();
                        break;

                    case PUSH_FALSE:
                        valueStack.push(JSBoolean.FALSE);
                        pc += op.getSize();
                        break;

                    case PUSH_TRUE:
                        valueStack.push(JSBoolean.TRUE);
                        pc += op.getSize();
                        break;

                    // ==================== Stack Manipulation ====================
                    case DROP:
                        valueStack.pop();
                        pc += op.getSize();
                        break;

                    case NIP:
                        JSValue top = valueStack.pop();
                        valueStack.pop();
                        valueStack.push(top);
                        pc += op.getSize();
                        break;

                    case DUP:
                        valueStack.push(valueStack.peek(0));
                        pc += op.getSize();
                        break;

                    case DUP2:
                        valueStack.push(valueStack.peek(1));
                        valueStack.push(valueStack.peek(1));
                        pc += op.getSize();
                        break;

                    case SWAP:
                        JSValue v1 = valueStack.pop();
                        JSValue v2 = valueStack.pop();
                        valueStack.push(v1);
                        valueStack.push(v2);
                        pc += op.getSize();
                        break;

                    case ROT3L:
                        JSValue a = valueStack.pop();
                        JSValue b = valueStack.pop();
                        JSValue c = valueStack.pop();
                        valueStack.push(b);
                        valueStack.push(a);
                        valueStack.push(c);
                        pc += op.getSize();
                        break;

                    // ==================== Arithmetic Operations ====================
                    case ADD:
                        handleAdd();
                        pc += op.getSize();
                        break;

                    case SUB:
                        handleSub();
                        pc += op.getSize();
                        break;

                    case MUL:
                        handleMul();
                        pc += op.getSize();
                        break;

                    case DIV:
                        handleDiv();
                        pc += op.getSize();
                        break;

                    case MOD:
                        handleMod();
                        pc += op.getSize();
                        break;

                    case EXP:
                        handleExp();
                        pc += op.getSize();
                        break;

                    case PLUS:
                        handlePlus();
                        pc += op.getSize();
                        break;

                    case NEG:
                        handleNeg();
                        pc += op.getSize();
                        break;

                    case INC:
                        handleInc();
                        pc += op.getSize();
                        break;

                    case DEC:
                        handleDec();
                        pc += op.getSize();
                        break;

                    // ==================== Bitwise Operations ====================
                    case SHL:
                        handleShl();
                        pc += op.getSize();
                        break;

                    case SAR:
                        handleSar();
                        pc += op.getSize();
                        break;

                    case SHR:
                        handleShr();
                        pc += op.getSize();
                        break;

                    case AND:
                        handleAnd();
                        pc += op.getSize();
                        break;

                    case OR:
                        handleOr();
                        pc += op.getSize();
                        break;

                    case XOR:
                        handleXor();
                        pc += op.getSize();
                        break;

                    case NOT:
                        handleNot();
                        pc += op.getSize();
                        break;

                    // ==================== Comparison Operations ====================
                    case EQ:
                        handleEq();
                        pc += op.getSize();
                        break;

                    case NEQ:
                        handleNeq();
                        pc += op.getSize();
                        break;

                    case STRICT_EQ:
                        handleStrictEq();
                        pc += op.getSize();
                        break;

                    case STRICT_NEQ:
                        handleStrictNeq();
                        pc += op.getSize();
                        break;

                    case LT:
                        handleLt();
                        pc += op.getSize();
                        break;

                    case LTE:
                        handleLte();
                        pc += op.getSize();
                        break;

                    case GT:
                        handleGt();
                        pc += op.getSize();
                        break;

                    case GTE:
                        handleGte();
                        pc += op.getSize();
                        break;

                    case INSTANCEOF:
                        handleInstanceof();
                        pc += op.getSize();
                        break;

                    case IN:
                        handleIn();
                        pc += op.getSize();
                        break;

                    // ==================== Logical Operations ====================
                    case LOGICAL_NOT:
                        handleLogicalNot();
                        pc += op.getSize();
                        break;

                    case LOGICAL_AND:
                        handleLogicalAnd();
                        pc += op.getSize();
                        break;

                    case LOGICAL_OR:
                        handleLogicalOr();
                        pc += op.getSize();
                        break;

                    case NULLISH_COALESCE:
                        handleNullishCoalesce();
                        pc += op.getSize();
                        break;

                    // ==================== Variable Access ====================
                    case GET_VAR:
                        int getVarAtom = bytecode.readU32(pc + 1);
                        String getVarName = bytecode.getAtoms()[getVarAtom];
                        JSValue varValue = context.getGlobalObject().get(PropertyKey.fromString(getVarName));
                        valueStack.push(varValue);
                        pc += op.getSize();
                        break;

                    case PUT_VAR:
                        int putVarAtom = bytecode.readU32(pc + 1);
                        String putVarName = bytecode.getAtoms()[putVarAtom];
                        JSValue putValue = valueStack.pop();
                        context.getGlobalObject().set(PropertyKey.fromString(putVarName), putValue);
                        pc += op.getSize();
                        break;

                    case SET_VAR:
                        int setVarAtom = bytecode.readU32(pc + 1);
                        String setVarName = bytecode.getAtoms()[setVarAtom];
                        JSValue setValue = valueStack.peek(0);
                        context.getGlobalObject().set(PropertyKey.fromString(setVarName), setValue);
                        pc += op.getSize();
                        break;

                    case GET_LOCAL:
                        int getLocalIndex = bytecode.readU16(pc + 1);
                        JSValue localValue = currentFrame.getLocals()[getLocalIndex];
                        valueStack.push(localValue);
                        pc += op.getSize();
                        break;

                    case PUT_LOCAL:
                        int putLocalIndex = bytecode.readU16(pc + 1);
                        currentFrame.getLocals()[putLocalIndex] = valueStack.pop();
                        pc += op.getSize();
                        break;

                    case SET_LOCAL:
                        int setLocalIndex = bytecode.readU16(pc + 1);
                        currentFrame.getLocals()[setLocalIndex] = valueStack.peek(0);
                        pc += op.getSize();
                        break;

                    // ==================== Property Access ====================
                    case GET_FIELD:
                        int getFieldAtom = bytecode.readU32(pc + 1);
                        String fieldName = bytecode.getAtoms()[getFieldAtom];
                        JSValue obj = valueStack.pop();

                        // Auto-box primitives to access their prototype methods
                        JSObject targetObj = toObject(obj);
                        if (targetObj != null) {
                            valueStack.push(targetObj.get(PropertyKey.fromString(fieldName)));
                        } else {
                            valueStack.push(JSUndefined.INSTANCE);
                        }
                        pc += op.getSize();
                        break;

                    case PUT_FIELD:
                        int putFieldAtom = bytecode.readU32(pc + 1);
                        String putFieldName = bytecode.getAtoms()[putFieldAtom];
                        JSValue putFieldObj = valueStack.pop();
                        // The value should be on top of the stack.
                        JSValue putFieldValue = valueStack.peek(0);
                        if (putFieldObj instanceof JSObject jsObj) {
                            jsObj.set(PropertyKey.fromString(putFieldName), putFieldValue);
                        }
                        pc += op.getSize();
                        break;

                    case GET_ARRAY_EL:
                        JSValue index = valueStack.pop();
                        JSValue arrayObj = valueStack.pop();
                        if (arrayObj instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(index);
                            valueStack.push(jsObj.get(key));
                        } else {
                            valueStack.push(JSUndefined.INSTANCE);
                        }
                        pc += op.getSize();
                        break;

                    case PUT_ARRAY_EL:
                        JSValue putElValue = valueStack.pop();
                        JSValue putElIndex = valueStack.pop();
                        JSValue putElObj = valueStack.pop();
                        if (putElObj instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(putElIndex);
                            jsObj.set(key, putElValue);
                        }
                        pc += op.getSize();
                        break;

                    // ==================== Control Flow ====================
                    case IF_FALSE:
                        JSValue condition = valueStack.pop();
                        boolean isFalsy = JSTypeConversions.toBoolean(condition) == JSBoolean.FALSE;
                        if (isFalsy) {
                            int offset = bytecode.readI32(pc + 1);
                            pc += op.getSize() + offset;
                        } else {
                            pc += op.getSize();
                        }
                        break;

                    case IF_TRUE:
                        JSValue trueCondition = valueStack.pop();
                        boolean isTruthy = JSTypeConversions.toBoolean(trueCondition) == JSBoolean.TRUE;
                        if (isTruthy) {
                            int offset = bytecode.readI32(pc + 1);
                            pc += op.getSize() + offset;
                        } else {
                            pc += op.getSize();
                        }
                        break;

                    case GOTO:
                        int gotoOffset = bytecode.readI32(pc + 1);
                        pc += op.getSize() + gotoOffset;
                        break;

                    case RETURN:
                        JSValue returnValue = valueStack.pop();
                        currentFrame = previousFrame;
                        return returnValue;

                    case RETURN_UNDEF:
                        currentFrame = previousFrame;
                        return JSUndefined.INSTANCE;

                    // ==================== Function Calls ====================
                    case CALL:
                        int argCount = bytecode.readU16(pc + 1);
                        handleCall(argCount);
                        pc += op.getSize();
                        break;

                    case CALL_CONSTRUCTOR:
                        int ctorArgCount = bytecode.readU16(pc + 1);
                        handleCallConstructor(ctorArgCount);
                        pc += op.getSize();
                        break;

                    // ==================== Object/Array Creation ====================
                    case OBJECT:
                    case OBJECT_NEW:
                        valueStack.push(new JSObject());
                        pc += op.getSize();
                        break;

                    case ARRAY_NEW:
                        valueStack.push(new JSArray());
                        pc += op.getSize();
                        break;

                    case PUSH_ARRAY:
                        JSValue element = valueStack.pop();
                        JSValue array = valueStack.peek(0);
                        if (array instanceof JSArray jsArray) {
                            jsArray.push(element);
                        }
                        pc += op.getSize();
                        break;

                    case DEFINE_PROP:
                        JSValue propValue = valueStack.pop();
                        JSValue propKey = valueStack.pop();
                        JSValue propObj = valueStack.peek(0);
                        if (propObj instanceof JSObject jsObj) {
                            PropertyKey key = PropertyKey.fromValue(propKey);
                            jsObj.set(key, propValue);
                        }
                        pc += op.getSize();
                        break;

                    // ==================== Exception Handling ====================
                    case THROW:
                        JSValue exception = valueStack.pop();
                        pendingException = exception;
                        context.setPendingException(exception);
                        throw new VMException("Exception thrown: " + exception);

                    case CATCH:
                        // QuickJS: pushes catch offset marker onto stack
                        // This marker is used during exception unwinding to find the catch handler
                        int catchOffset = bytecode.readI32(pc + 1);
                        int catchHandlerPC = pc + op.getSize() + catchOffset;
                        valueStack.pushStackValue(new CatchOffset(catchHandlerPC));
                        pc += op.getSize();
                        break;

                    // ==================== Type Operations ====================
                    case TYPEOF:
                        handleTypeof();
                        pc += op.getSize();
                        break;

                    case DELETE:
                        handleDelete();
                        pc += op.getSize();
                        break;

                    // ==================== Other Operations ====================
                    default:
                        throw new VMException("Unimplemented opcode: " + op + " at PC " + pc);
                }
            }
        } catch (VMException e) {
            currentFrame = previousFrame;
            throw e;
        } catch (Exception e) {
            currentFrame = previousFrame;
            throw new VMException("VM error: " + e.getMessage(), e);
        }
    }

    // ==================== Arithmetic Operation Handlers ====================

    private void handleAdd() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();

        // String concatenation or numeric addition
        if (left instanceof JSString || right instanceof JSString) {
            String leftStr = JSTypeConversions.toString(left).value();
            String rightStr = JSTypeConversions.toString(right).value();
            valueStack.push(new JSString(leftStr + rightStr));
        } else {
            double leftNum = JSTypeConversions.toNumber(left).value();
            double rightNum = JSTypeConversions.toNumber(right).value();
            valueStack.push(new JSNumber(leftNum + rightNum));
        }
    }

    private void handleAnd() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int result = JSTypeConversions.toInt32(left) & JSTypeConversions.toInt32(right);
        valueStack.push(new JSNumber(result));
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

        // Special handling for Symbol constructor (must be called without new)
        if (callee instanceof JSObject calleeObj) {
            JSValue isSymbolCtor = calleeObj.get("[[SymbolConstructor]]");
            if (isSymbolCtor instanceof JSBoolean && ((JSBoolean) isSymbolCtor).value()) {
                // Call Symbol() function
                JSValue result = SymbolConstructor.call(context, receiver, args);
                valueStack.push(result);
                return;
            }

            // Special handling for BigInt constructor (must be called without new)
            JSValue isBigIntCtor = calleeObj.get("[[BigIntConstructor]]");
            if (isBigIntCtor instanceof JSBoolean && ((JSBoolean) isBigIntCtor).value()) {
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
                valueStack.push(result);
            } else if (function instanceof JSBytecodeFunction bytecodeFunc) {
                // Recursive call with receiver as thisArg
                JSValue result = execute(bytecodeFunc, receiver, args);
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

        // Check for ES6 class constructor
        if (constructor instanceof JSClass classConstructor) {
            // Use the class's construct() method
            JSObject instance = classConstructor.construct(context, args);
            valueStack.push(instance);
            return;
        }

        if (constructor instanceof JSFunction) {
            // Create new object
            JSObject newObj = new JSObject();

            // Call constructor with new object as this
            if (constructor instanceof JSNativeFunction nativeFunc) {
                nativeFunc.call(context, newObj, args);
            } else if (constructor instanceof JSBytecodeFunction bytecodeFunc) {
                execute(bytecodeFunc, newObj, args);
            }

            valueStack.push(newObj);
        } else if (constructor instanceof JSObject ctorObj) {
            // Check for ES6 class constructor marker
            JSValue isClassCtor = ctorObj.get("[[ClassConstructor]]");
            if (isClassCtor instanceof JSBoolean && ((JSBoolean) isClassCtor).value()) {
                context.throwError("TypeError", "Class constructor cannot be invoked without 'new'");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }
            // Check for Date constructor
            JSValue isDateCtor = ctorObj.get("[[DateConstructor]]");
            if (isDateCtor instanceof JSBoolean && ((JSBoolean) isDateCtor).value()) {
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
                        JSString str = JSTypeConversions.toString(arg);
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
            JSValue isSymbolCtor = ctorObj.get("[[SymbolConstructor]]");
            if (isSymbolCtor instanceof JSBoolean && ((JSBoolean) isSymbolCtor).value()) {
                context.throwError("TypeError", "Symbol is not a constructor");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }

            // Check for BigInt constructor (throws error when used with new)
            JSValue isBigIntCtor = ctorObj.get("[[BigIntConstructor]]");
            if (isBigIntCtor instanceof JSBoolean && ((JSBoolean) isBigIntCtor).value()) {
                context.throwError("TypeError", "BigInt is not a constructor");
                valueStack.push(JSUndefined.INSTANCE);
                return;
            }

            // Check for RegExp constructor
            JSValue isRegExpCtor = ctorObj.get("[[RegExpConstructor]]");
            if (isRegExpCtor instanceof JSBoolean && ((JSBoolean) isRegExpCtor).value()) {
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
                                ? JSTypeConversions.toString(args[1]).value()
                                : existingRegExp.getFlags();
                    } else if (!(patternArg instanceof JSUndefined)) {
                        pattern = JSTypeConversions.toString(patternArg).value();
                        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
                            flags = JSTypeConversions.toString(args[1]).value();
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
                    context.throwError("SyntaxError", "Invalid regular expression: " + e.getMessage());
                    valueStack.push(JSUndefined.INSTANCE);
                }
                return;
            }

            // Check for Map constructor
            JSValue isMapCtor = ctorObj.get("[[MapConstructor]]");
            if (isMapCtor instanceof JSBoolean && ((JSBoolean) isMapCtor).value()) {
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
                                context.throwError("TypeError", "Iterator value must be an object");
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
                                    context.throwError("TypeError", "Iterator value must be an object");
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
            JSValue isSetCtor = ctorObj.get("[[SetConstructor]]");
            if (isSetCtor instanceof JSBoolean && ((JSBoolean) isSetCtor).value()) {
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
            JSValue isWeakMapCtor = ctorObj.get("[[WeakMapConstructor]]");
            if (isWeakMapCtor instanceof JSBoolean && ((JSBoolean) isWeakMapCtor).value()) {
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
                                context.throwError("TypeError", "Iterator value must be an object");
                                valueStack.push(JSUndefined.INSTANCE);
                                return;
                            }

                            // Get key and value from entry [key, value]
                            JSValue key = entryObj.get(0);
                            JSValue value = entryObj.get(1);

                            // WeakMap requires object keys
                            if (!(key instanceof JSObject)) {
                                context.throwError("TypeError", "WeakMap key must be an object");
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
                                    context.throwError("TypeError", "Iterator value must be an object");
                                    valueStack.push(JSUndefined.INSTANCE);
                                    return;
                                }

                                // Get key and value from entry [key, value]
                                JSValue key = entryObj.get(0);
                                JSValue value = entryObj.get(1);

                                // WeakMap requires object keys
                                if (!(key instanceof JSObject)) {
                                    context.throwError("TypeError", "WeakMap key must be an object");
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
            JSValue isWeakSetCtor = ctorObj.get("[[WeakSetConstructor]]");
            if (isWeakSetCtor instanceof JSBoolean && ((JSBoolean) isWeakSetCtor).value()) {
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
                                context.throwError("TypeError", "WeakSet value must be an object");
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
                                    context.throwError("TypeError", "WeakSet value must be an object");
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
            JSValue isWeakRefCtor = ctorObj.get("[[WeakRefConstructor]]");
            if (isWeakRefCtor instanceof JSBoolean && ((JSBoolean) isWeakRefCtor).value()) {
                // WeakRef requires exactly 1 argument: target
                if (args.length == 0) {
                    context.throwError("TypeError", "WeakRef constructor requires a target object");
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
            JSValue isFinalizationRegistryCtor = ctorObj.get("[[FinalizationRegistryConstructor]]");
            if (isFinalizationRegistryCtor instanceof JSBoolean && ((JSBoolean) isFinalizationRegistryCtor).value()) {
                // FinalizationRegistry requires exactly 1 argument: cleanupCallback
                if (args.length == 0) {
                    context.throwError("TypeError", "FinalizationRegistry constructor requires a cleanup callback");
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
            JSValue isProxyCtor = ctorObj.get("[[ProxyConstructor]]");
            if (isProxyCtor instanceof JSBoolean && ((JSBoolean) isProxyCtor).value()) {
                // Proxy requires exactly 2 arguments: target and handler
                if (args.length < 2) {
                    context.throwError("TypeError", "Proxy constructor requires target and handler");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                // Target must be an object or function (in JavaScript, functions are objects)
                JSValue target = args[0];
                if (!(target instanceof JSObject) && !(target instanceof JSFunction)) {
                    context.throwError("TypeError", "Proxy target must be an object");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                if (!(args[1] instanceof JSObject handler)) {
                    context.throwError("TypeError", "Proxy handler must be an object");
                    valueStack.push(JSUndefined.INSTANCE);
                    return;
                }

                // Create Proxy object
                JSProxy proxyObj = new JSProxy(target, handler, context);
                valueStack.push(proxyObj);
                return;
            }

            // Check for Promise constructor
            JSValue isPromiseCtor = ctorObj.get("[[PromiseConstructor]]");
            if (isPromiseCtor instanceof JSBoolean && ((JSBoolean) isPromiseCtor).value()) {
                // Promise requires an executor function
                if (args.length == 0 || !(args[0] instanceof JSFunction executor)) {
                    context.throwError("TypeError", "Promise constructor requires an executor function");
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
                        (ctx, thisArg, funcArgs) -> {
                            JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                            promiseObj.fulfill(value);
                            return JSUndefined.INSTANCE;
                        });

                JSNativeFunction rejectFunc = new JSNativeFunction("reject", 1,
                        (ctx, thisArg, funcArgs) -> {
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
            JSValue isSharedArrayBufferCtor = ctorObj.get("[[SharedArrayBufferConstructor]]");
            if (isSharedArrayBufferCtor instanceof JSBoolean && ((JSBoolean) isSharedArrayBufferCtor).value()) {
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
                    JSString message = JSTypeConversions.toString(args[0]);
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
        double result = JSTypeConversions.toNumber(operand).value() - 1;
        valueStack.push(new JSNumber(result));
    }

    private void handleDelete() {
        JSValue property = valueStack.pop();
        JSValue object = valueStack.pop();
        boolean result = false;
        if (object instanceof JSObject jsObj) {
            PropertyKey key = PropertyKey.fromValue(property);
            result = jsObj.delete(key);
        }
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleDiv() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(left).value() / JSTypeConversions.toNumber(right).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleEq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.abstractEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleExp() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = Math.pow(JSTypeConversions.toNumber(left).value(), JSTypeConversions.toNumber(right).value());
        valueStack.push(new JSNumber(result));
    }

    private void handleGt() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.lessThan(right, left);
        valueStack.push(JSBoolean.valueOf(result));
    }

    // ==================== Bitwise Operation Handlers ====================

    private void handleGte() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.lessThan(right, left) ||
                JSTypeConversions.abstractEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleIn() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = false;
        if (right instanceof JSObject jsObj) {
            PropertyKey key = PropertyKey.fromValue(left);
            result = jsObj.has(key);
        }
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleInc() {
        JSValue operand = valueStack.pop();
        double result = JSTypeConversions.toNumber(operand).value() + 1;
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

    // ==================== Comparison Operation Handlers ====================

    private void handleLt() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.lessThan(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleLte() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.lessThan(left, right) ||
                JSTypeConversions.abstractEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleMod() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(left).value() % JSTypeConversions.toNumber(right).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleMul() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(left).value() * JSTypeConversions.toNumber(right).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleNeg() {
        JSValue operand = valueStack.pop();
        double result = -JSTypeConversions.toNumber(operand).value();
        valueStack.push(new JSNumber(result));
    }

    private void handleNeq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = !JSTypeConversions.abstractEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleNot() {
        JSValue operand = valueStack.pop();
        int result = ~JSTypeConversions.toInt32(operand);
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
        int result = JSTypeConversions.toInt32(left) | JSTypeConversions.toInt32(right);
        valueStack.push(new JSNumber(result));
    }

    private void handlePlus() {
        JSValue operand = valueStack.pop();
        double result = JSTypeConversions.toNumber(operand).value();
        valueStack.push(new JSNumber(result));
    }

    // ==================== Logical Operation Handlers ====================

    private void handleSar() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int leftInt = JSTypeConversions.toInt32(left);
        int rightInt = JSTypeConversions.toInt32(right);
        valueStack.push(new JSNumber(leftInt >> (rightInt & 0x1F)));
    }

    private void handleShl() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int leftInt = JSTypeConversions.toInt32(left);
        int rightInt = JSTypeConversions.toInt32(right);
        valueStack.push(new JSNumber(leftInt << (rightInt & 0x1F)));
    }

    private void handleShr() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int leftInt = JSTypeConversions.toInt32(left);
        int rightInt = JSTypeConversions.toInt32(right);
        valueStack.push(new JSNumber((leftInt >>> (rightInt & 0x1F)) & 0xFFFFFFFFL));
    }

    private void handleStrictEq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    // ==================== Type Operation Handlers ====================

    private void handleStrictNeq() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        boolean result = !JSTypeConversions.strictEquals(left, right);
        valueStack.push(JSBoolean.valueOf(result));
    }

    private void handleSub() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        double result = JSTypeConversions.toNumber(left).value() - JSTypeConversions.toNumber(right).value();
        valueStack.push(new JSNumber(result));
    }

    // ==================== Function Call Handlers ====================

    private void handleTypeof() {
        JSValue operand = valueStack.pop();
        String type = JSTypeChecking.typeof(operand);
        valueStack.push(new JSString(type));
    }

    private void handleXor() {
        JSValue right = valueStack.pop();
        JSValue left = valueStack.pop();
        int result = JSTypeConversions.toInt32(left) ^ JSTypeConversions.toInt32(right);
        valueStack.push(new JSNumber(result));
    }

    /**
     * Convert a value to an object (auto-boxing for primitives).
     * Returns null for null and undefined.
     */
    private JSObject toObject(JSValue value) {
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
                    wrapper.set("[[PrimitiveValue]]", str);
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
                    wrapper.set("[[PrimitiveValue]]", num);
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
                    wrapper.set("[[PrimitiveValue]]", bool);
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
