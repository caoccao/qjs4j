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
import com.caoccao.qjs4j.core.error.JSStackFrame;
import com.caoccao.qjs4j.core.regexp.JSRegExp;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.IdentityHashMap;
import java.util.Set;

public final class OpcodeHandler {

    private OpcodeHandler() {
    }

    private static void branchIf(Opcode op, ExecutionContext ctx, boolean expected, boolean shortOffset) {
        int pc = ctx.pc;
        int next = pc + op.getSize();
        if (ctx.virtualMachine.isBranchTruthy(ctx.pop()) == expected) {
            next += shortOffset ? ctx.instructions[pc + 1] : ctx.bytecode.readU32(pc + 1);
        }
        ctx.pc = next;
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
        executionContext.finishOpcode(op, sp);
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

    static void handleFclosure(Opcode op, ExecutionContext executionContext) {
        pushClosure(op, executionContext, executionContext.bytecode.readU32(executionContext.pc + 1), true);
    }

    static void handleFclosure8(Opcode op, ExecutionContext executionContext) {
        pushClosure(op, executionContext, executionContext.bytecode.readU8(executionContext.pc + 1), false);
    }

    static void handleGosub(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int offset = executionContext.bytecode.readU32(pc + 1);
        // Push return address (instruction after this GOSUB) onto the stack
        int returnAddress = pc + op.getSize();
        executionContext.pushStackValue(new JSInternalValue(returnAddress));
        // Jump to the finally block
        executionContext.pc = pc + op.getSize() + offset;
    }

    static void handleGoto(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int offset = executionContext.bytecode.readU32(pc + 1);
        executionContext.pc = pc + op.getSize() + offset;
    }

    static void handleGoto16(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int offset = (short) (executionContext.bytecode.readU16(pc + 1));
        executionContext.pc = pc + op.getSize() + offset;
    }

    static void handleGoto8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.pc = pc + op.getSize() + executionContext.instructions[pc + 1];
    }

    static void handleIfFalse(Opcode op, ExecutionContext executionContext) {
        branchIf(op, executionContext, false, false);
    }

    static void handleIfFalse8(Opcode op, ExecutionContext executionContext) {
        branchIf(op, executionContext, false, true);
    }

    static void handleIfTrue(Opcode op, ExecutionContext executionContext) {
        branchIf(op, executionContext, true, false);
    }

    static void handleIfTrue8(Opcode op, ExecutionContext executionContext) {
        branchIf(op, executionContext, true, true);
    }

    static void handleIn(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue right = operandStack.pop();
        JSValue left = operandStack.pop();
        if (!(right instanceof JSObject jsObj)) {
            executionContext.raiseTypeError("invalid 'in' operand");
            operandStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(op);
            return;
        }
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, left);
        if (executionContext.capturePendingException()) {
            operandStack.push(JSUndefined.INSTANCE);
        } else {
            boolean result = jsObj.has(key);
            if (executionContext.capturePendingException()) {
                operandStack.push(JSUndefined.INSTANCE);
            } else {
                operandStack.push(JSBoolean.valueOf(result));
            }
        }
        executionContext.finishStackOpcode(op);
    }

    static void handleInsert2(Opcode op, ExecutionContext executionContext) {
        insertTop(executionContext, 2);
    }

    static void handleInsert3(Opcode op, ExecutionContext executionContext) {
        insertTop(executionContext, 3);
    }

    static void handleInsert4(Opcode op, ExecutionContext executionContext) {
        insertTop(executionContext, 4);
    }

    static void handleInstanceof(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue right = operandStack.pop();
        JSValue left = operandStack.pop();

        // Per ECMAScript spec, right must be an object
        if (!(right instanceof JSObject constructor)) {
            executionContext.raiseTypeError("Right-hand side of instanceof is not an object");
            operandStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(op);
            return;
        }

        JSValue hasInstanceMethod = constructor.get(PropertyKey.SYMBOL_HAS_INSTANCE);
        if (executionContext.capturePendingException()) {
            operandStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(op);
            return;
        }
        if (!(hasInstanceMethod instanceof JSUndefined) && !(hasInstanceMethod instanceof JSNull)) {
            if (!(hasInstanceMethod instanceof JSFunction hasInstanceFunction)) {
                executionContext.raiseTypeError("@@hasInstance is not callable");
                operandStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(op);
                return;
            }
            JSValue result = hasInstanceFunction.call(executionContext.virtualMachine.context, right,
                    executionContext.virtualMachine.singleArg(left));
            if (executionContext.capturePendingException()) {
                operandStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(op);
                return;
            }
            operandStack.push(JSTypeConversions.toBoolean(result) == JSBoolean.TRUE ? JSBoolean.TRUE : JSBoolean.FALSE);
        } else {
            boolean callable = right instanceof JSFunction;
            if (right instanceof JSProxy proxy && JSTypeChecking.isFunction(proxy.getTarget())) {
                callable = true;
            }
            if (!callable) {
                executionContext.raiseTypeError("Right-hand side of instanceof is not callable");
                operandStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(op);
                return;
            }
            try {
                operandStack.push(executionContext.virtualMachine.ordinaryHasInstance(right, left)
                        ? JSBoolean.TRUE
                        : JSBoolean.FALSE);
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.captureVMException(e);
                operandStack.push(JSUndefined.INSTANCE);
            }
        }
        executionContext.finishStackOpcode(op);
    }

    static void handleInvalid(Opcode op, ExecutionContext executionContext) {
        // A byte that decodes to no instruction means miscompiled or corrupted bytecode. That is an
        // engine defect, so it must not be routed to the script's catch handlers as if it were a
        // guest error. Report the surrounding bytes: 0x00 doubles as the extended-opcode prefix, so
        // a trailing 0x00 and a truncated two-byte instruction look identical without them.
        int pc = executionContext.pc;
        byte[] instructions = executionContext.instructions;
        StringBuilder message = new StringBuilder("Internal engine error: invalid opcode at pc=").append(pc)
                .append(" (bytes:");
        for (int offset = pc; offset < Math.min(instructions.length, pc + 4); offset++) {
            message.append(String.format(" %02x", instructions[offset] & 0xFF));
        }
        message.append(", length=").append(instructions.length).append(')');
        throw new JSTerminationException(message.toString());
    }

    static void handleIsNull(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isNull());
        executionContext.advance(op);
    }

    static void handleIsUndefined(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isUndefined());
        executionContext.advance(op);
    }

    static void handleIsUndefinedOrNull(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value instanceof JSNull || value instanceof JSUndefined);
        executionContext.advance(op);
    }

    static void handleLogicalNot(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue operand = operandStack.pop();
        boolean result = JSTypeConversions.toBoolean(operand) == JSBoolean.FALSE;
        operandStack.push(JSBoolean.valueOf(result));
        executionContext.finishStackOpcode(op);
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
            executionContext.setErrorAsPending(executionContext.virtualMachine.context.throwError("nip_catch"));
            return;
        }
        stack[sp++] = returnValue;
        executionContext.finishOpcode(op, sp);
    }

    static void handleNop(Opcode op, ExecutionContext executionContext) {
        executionContext.advance(op);
    }

    static void handleNull(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNull.INSTANCE);
        executionContext.pc += 1;
    }

    static void handleObjectNew(Opcode op, ExecutionContext executionContext) {
        executionContext.push(executionContext.virtualMachine.context.createJSObject());
        executionContext.advance(op);
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

    static void handlePushAtomValue(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int atomIndex = executionContext.bytecode.readU32(pc + 1);
        executionContext.push(new JSString(executionContext.bytecode.getAtoms()[atomIndex]));
        executionContext.pc = pc + op.getSize();
    }

    static void handlePushBigintI32(Opcode op, ExecutionContext executionContext) {
        executionContext.push(new JSBigInt(executionContext.bytecode.readI32(executionContext.pc + 1)));
        executionContext.advance(op);
    }

    static void handlePushConst(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int constIndex = op == Opcode.PUSH_CONST8
                ? executionContext.bytecode.readU8(pc + 1)
                : executionContext.bytecode.readU32(pc + 1);
        JSValue constantValue = executionContext.bytecode.getConstants()[constIndex];
        if (constantValue instanceof JSSymbol symbol) {
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = internalGetActivePrivateSymbolRemap(executionContext,
                    executionContext.sp);
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
        int pc = executionContext.pc;
        executionContext.push(JSNumber.of((short) (executionContext.bytecode.readU16(pc + 1))));
        executionContext.pc = pc + 3;
    }

    static void handlePushI32(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int intValue = executionContext.bytecode.readU32(pc + 1);
        executionContext.push(JSNumber.of(intValue));
        executionContext.pc = pc + 5;
    }

    static void handlePushI8(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        executionContext.push(JSNumber.of(executionContext.instructions[pc + 1]));
        executionContext.pc = pc + 2;
    }

    static void handlePushThis(Opcode op, ExecutionContext executionContext) {
        VarRef derivedThisRef = executionContext.frame.getDerivedThisRef();
        if (derivedThisRef != null) {
            JSValue thisValue = derivedThisRef.get();
            if (executionContext.virtualMachine.isUninitialized(thisValue)) {
                executionContext.raiseReferenceError(
                        "Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
                executionContext.push(JSUndefined.INSTANCE);
                executionContext.advance(op);
                return;
            }
            executionContext.push(thisValue);
        } else {
            executionContext.push(executionContext.frame.getThisArg());
        }
        executionContext.advance(op);
    }

    static void handlePushTrue(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSBoolean.TRUE);
        executionContext.pc += 1;
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
            JSRegExp newRegExp = executionContext.virtualMachine.context.createJSRegExp(templateRegExp.getPattern(),
                    templateRegExp.getFlags());
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
        if (stackValue instanceof JSInternalValue internalValue
                && internalValue.value() instanceof Integer returnAddress) {
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
        rotateLeft(executionContext, 3);
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
        rotateLeft(executionContext, 4);
    }

    // x a b c d -> a b c d x
    static void handleRot5l(Opcode op, ExecutionContext executionContext) {
        rotateLeft(executionContext, 5);
    }

    static void handleSpecialObject(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int objectType = executionContext.bytecode.readU8(pc + 1);
        JSValue specialObject = executionContext.virtualMachine.createSpecialObject(objectType, executionContext.frame);
        executionContext.push(specialObject);
        executionContext.pc = pc + op.getSize();
    }

    static void handleSwap(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
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
            // V8's wording for an assignment to a const, a class name or a function's own name; QuickJS says
            // "'x' is read-only".
            case 0 -> executionContext.virtualMachine.context.throwTypeError("Assignment to constant variable.");
            case 1 ->
                executionContext.virtualMachine.context.throwError("SyntaxError: redeclaration of '" + throwName + "'");
            case 2 -> executionContext.virtualMachine.context.throwReferenceError(throwName + " is not initialized");
            case 3 -> executionContext.virtualMachine.context.throwReferenceError("unsupported reference to 'super'");
            case 4 -> executionContext.virtualMachine.context.throwTypeError("iterator does not have a throw method");
            case 5 -> executionContext.virtualMachine.context.throwReferenceError(throwName);
            default -> throw new JSVirtualMachineException("invalid throw_error type: " + throwType);
        }
        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                .getPendingException();
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
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .getPendingException();
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
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleTypeof(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue operand = operandStack.pop();
        String type = JSTypeChecking.typeof(operand);
        operandStack.push(new JSString(type));
        executionContext.finishStackOpcode(op);
    }

    static void handleTypeofIsFunction(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(JSKeyword.FUNCTION.equals(JSTypeChecking.typeof(value)));
        executionContext.advance(op);
    }

    static void handleTypeofIsUndefined(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(JSKeyword.UNDEFINED.equals(JSTypeChecking.typeof(value)));
        executionContext.advance(op);
    }

    static void handleUndefined(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSUndefined.INSTANCE);
        executionContext.pc += 1;
    }

    /** Duplicate the top value below the last depth operands. */
    private static void insertTop(ExecutionContext execution, int depth) {
        JSStackValue[] stack = execution.virtualMachine.valueStack.stack;
        int first = execution.sp - depth;
        JSStackValue value = stack[execution.sp - 1];
        System.arraycopy(stack, first, stack, first + 1, depth);
        stack[first] = value;
        execution.sp++;
        execution.pc++;
    }

    /**
     * Create VarRef array from capture source info during FCLOSURE. For LOCAL sources, creates/reuses a VarRef pointing
     * to the parent's local slot. For VAR_REF sources, shares the parent's existing VarRef.
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

    private static IdentityHashMap<JSSymbol, JSSymbol> internalGetActivePrivateSymbolRemap(
            ExecutionContext executionContext, int sp) {
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

    /**
     * Attach lexical state and realm metadata shared by both closure opcodes. Stack: unchanged; the caller pushes the
     * initialized closure.
     */
    private static void internalInitializeClosure(ExecutionContext executionContext, JSBytecodeFunction closureTemplate,
            JSBytecodeFunction closureFunction, IdentityHashMap<JSSymbol, JSSymbol> symbolRemap) {
        if (symbolRemap != null && !symbolRemap.isEmpty()) {
            closureFunction.setClassPrivateSymbolRemap(symbolRemap);
        }
        StackFrame evalDynamicScopeFrame = internalResolveEvalDynamicScopeFrame(executionContext);
        if (evalDynamicScopeFrame == null && internalHasDirectEvalCall(closureTemplate)
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
            closureFunction.setModuleEnvironment(enclosingFunction.getModuleEnvironment());
            // A function defined by eval code is located in that eval in a stack trace, and it belongs to the
            // eval's lexical environment.
            closureFunction.setEvalOrigin(enclosingFunction.getEvalOrigin());
            closureFunction.setEvalEnvironment(enclosingFunction.getEvalEnvironment());
        }
        if (importMetaFilename == null || importMetaFilename.isEmpty()) {
            JSStackFrame currentStackFrame = executionContext.virtualMachine.context.getCurrentStackFrame();
            if (currentStackFrame != null) {
                importMetaFilename = currentStackFrame.filename();
            }
        }
        closureFunction.setImportMetaFilename(importMetaFilename);
        // Arrow functions capture this, arguments, new.target, active function, and home object from the enclosing
        // scope
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
                closureFunction.setCapturedArguments(executionContext.virtualMachine
                        .createArgumentsObject(executionContext.frame, enclosingFunc, mapped));
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
    }

    private static StackFrame internalResolveEvalDynamicScopeFrame(ExecutionContext executionContext) {
        if (executionContext.frame == null
                || !(executionContext.frame.getFunction() instanceof JSBytecodeFunction currentBytecodeFunction)) {
            return null;
        }
        JSContext context = executionContext.virtualMachine.context;
        if (context.hasEvalOverlayFrames()) {
            // The creating frame owns eval-local let and const bindings. Its caller chain
            // supplies the outer scopes when the closure itself performs a direct eval.
            return executionContext.frame;
        }
        if (currentBytecodeFunction.isEvalDynamicScopeLookupEnabled()) {
            if (executionContext.frame.getCaller() != null) {
                return executionContext.frame;
            }
            return currentBytecodeFunction.getEvalDynamicScopeFrame();
        }
        return null;
    }

    private static IdentityHashMap<JSSymbol, JSSymbol> internalResolvePrivateSymbolRemap(
            ExecutionContext executionContext, JSBytecodeFunction templateFunction, int pc, Opcode op, int sp) {
        IdentityHashMap<JSSymbol, JSSymbol> activeRemap = internalGetActivePrivateSymbolRemap(executionContext, sp);
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

    private static boolean internalStartsClassDefinition(ExecutionContext executionContext, int pc, Opcode op) {
        int nextPc = pc + op.getSize();
        if (nextPc >= executionContext.instructions.length) {
            return false;
        }
        Opcode nextOpcode = executionContext.decodedOpcodes[nextPc];
        return nextOpcode == Opcode.DEFINE_CLASS || nextOpcode == Opcode.DEFINE_CLASS_COMPUTED;
    }

    private static void pushClosure(Opcode op, ExecutionContext executionContext, int functionIndex, boolean bindSelf) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue functionValue = executionContext.bytecode.getConstants()[functionIndex];
        if (functionValue instanceof JSBytecodeFunction templateFunction) {
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = internalResolvePrivateSymbolRemap(executionContext,
                    templateFunction, pc, op, sp);
            JSBytecodeFunction closureTemplate = templateFunction;
            if (symbolRemap != null && !symbolRemap.isEmpty()) {
                closureTemplate = templateFunction.copyTemplateWithRemappedPrivateSymbols(symbolRemap);
            }
            int[] captureInfos = closureTemplate.getCaptureSourceInfos();
            JSBytecodeFunction closureFunction;
            if (captureInfos != null) {
                VarRef[] capturedVarRefs = internalCreateVarRefsFromCaptures(captureInfos, executionContext.frame);
                closureFunction = closureTemplate.copyWithVarRefs(capturedVarRefs);
                int selfIndex = bindSelf ? closureTemplate.getSelfCaptureIndex() : -1;
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
                int selfIndex = bindSelf ? closureTemplate.getSelfCaptureIndex() : -1;
                if (selfIndex >= 0 && selfIndex < capturedClosureVars.length) {
                    capturedClosureVars[selfIndex] = closureFunction;
                }
            }
            internalInitializeClosure(executionContext, closureTemplate, closureFunction, symbolRemap);
            stack[sp++] = closureFunction;
        } else {
            if (functionValue instanceof JSFunction function) {
                function.initializePrototypeChain(executionContext.virtualMachine.context);
            }
            stack[sp++] = functionValue;
        }
        executionContext.finishOpcode(op, sp);
    }

    static void pushSmallInteger(Opcode op, ExecutionContext ctx) {
        ctx.push(JSNumber.of(op.getCode() - Opcode.PUSH_0.getCode()));
        ctx.pc++;
    }

    private static void rotateLeft(ExecutionContext execution, int depth) {
        JSStackValue[] stack = execution.virtualMachine.valueStack.stack;
        int first = execution.sp - depth;
        JSStackValue value = stack[first];
        System.arraycopy(stack, first + 1, stack, first, depth - 1);
        stack[execution.sp - 1] = value;
        execution.pc++;
    }
}
