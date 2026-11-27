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
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class OpcodeHandler {

    private OpcodeHandler() {
    }

    private static JSValue bigIntArithmetic(VirtualMachine vm, Opcode op, BigInteger left, BigInteger right) {
        switch (op) {
            case MUL :
                return new JSBigInt(left.multiply(right));
            case SUB :
                return new JSBigInt(left.subtract(right));
            case DIV :
            case MOD :
                if (right.signum() == 0) {
                    vm.pendingException = vm.context.throwRangeError("Division by zero");
                    return JSUndefined.INSTANCE;
                }
                return new JSBigInt(op == Opcode.DIV ? left.divide(right) : left.remainder(right));
            case POW :
                if (right.signum() < 0) {
                    vm.pendingException = vm.context.throwRangeError("Exponent must be positive");
                    return JSUndefined.INSTANCE;
                }
                if (right.bitLength() > 31) {
                    vm.pendingException = vm.context.throwRangeError("BigInt exponent is too large");
                    return JSUndefined.INSTANCE;
                }
                return new JSBigInt(left.pow(right.intValue()));
            default :
                throw new IllegalArgumentException("Unexpected arithmetic opcode " + op);
        }
    }

    private static void bitwise(Opcode op, ExecutionContext executionContext) {
        JSValue left = executionContext.peek(1);
        JSValue right = executionContext.peek(0);
        if (left instanceof JSNumber a && right instanceof JSNumber b) {
            int result = bitwiseInt(op, JSTypeConversions.toInt32(a.value()), JSTypeConversions.toInt32(b.value()));
            executionContext.set(1, JSNumber.of(result));
            executionContext.sp--;
        } else {
            VirtualMachine vm = executionContext.virtualMachine;
            CallStack stack = vm.valueStack;
            stack.stackTop = executionContext.sp;
            stack.pop();
            stack.pop();
            VirtualMachine.NumericPair pair;
            try {
                pair = vm.numericPair(left, right);
            } catch (JSVirtualMachineException e) {
                captureVmExceptionAsPending(executionContext, e);
                pair = null;
            }
            if (pair == null || vm.pendingException != null) {
                stack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                BigInteger a = ((JSBigInt) pair.left()).value();
                BigInteger b = ((JSBigInt) pair.right()).value();
                stack.push(new JSBigInt(switch (op) {
                    case AND -> a.and(b);
                    case OR -> a.or(b);
                    case XOR -> a.xor(b);
                    default -> throw new IllegalArgumentException(op.name());
                }));
            } else {
                stack.push(JSNumber.of(bitwiseInt(op, JSTypeConversions.toInt32(vm.context, pair.left()),
                        JSTypeConversions.toInt32(vm.context, pair.right()))));
            }
            executionContext.sp = stack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    private static int bitwiseInt(Opcode op, int left, int right) {
        return switch (op) {
            case AND -> left & right;
            case OR -> left | right;
            case XOR -> left ^ right;
            default -> throw new IllegalArgumentException(op.name());
        };
    }

    private static void branchIf(Opcode op, ExecutionContext ctx, boolean expected, boolean shortOffset) {
        int pc = ctx.pc;
        int next = pc + op.getSize();
        if (ctx.virtualMachine.isBranchTruthy(ctx.pop()) == expected) {
            next += shortOffset ? ctx.instructions[pc + 1] : ctx.bytecode.readU32(pc + 1);
        }
        ctx.pc = next;
    }

    private static void captureJavaScriptException(ExecutionContext ctx, JSException exception) {
        JSValue error = exception.getErrorValue();
        if (error == null) {
            String message = exception.getMessage();
            error = ctx.virtualMachine.context.throwError("Error", message == null ? "Unhandled exception" : message);
        }
        ctx.setErrorAsPending(error);
    }

    private static void captureVmExceptionAsPending(ExecutionContext executionContext, JSVirtualMachineException e) {
        JSValue errorValue = e.getJsValue() != null ? e.getJsValue() : e.getJsError();
        if (errorValue != null) {
            executionContext.virtualMachine.pendingException = errorValue;
        } else {
            executionContext.setErrorAsPending(executionContext.virtualMachine.context
                    .throwError(e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    private static void compareRelational(Opcode op, ExecutionContext ctx, boolean reverse, boolean inclusive) {
        JSStackValue[] stack = ctx.virtualMachine.valueStack.stack;
        int sp = ctx.sp;
        if (stack[sp - 2] instanceof JSNumber left && stack[sp - 1] instanceof JSNumber right) {
            double x = reverse ? right.value() : left.value();
            double y = reverse ? left.value() : right.value();
            stack[sp - 2] = JSBoolean.valueOf(inclusive ? x >= y : x < y);
            ctx.sp = sp - 1;
        } else {
            compareRelationalOperands(ctx, reverse, inclusive);
        }
        ctx.pc += op.getSize();
    }

    private static void compareRelationalOperands(ExecutionContext executionContext, boolean reverse, boolean negate) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        JSValue right = vm.valueStack.pop();
        JSValue left = vm.valueStack.pop();
        try {
            // Reversing the comparison still coerces the original left operand first.
            JSTypeConversions.RelationalComparisonResult result = reverse
                    ? JSTypeConversions.lessThanResult(vm.context, right, left, false)
                    : JSTypeConversions.lessThanResult(vm.context, left, right);
            vm.capturePendingException();
            JSTypeConversions.RelationalComparisonResult expected = negate
                    ? JSTypeConversions.RelationalComparisonResult.FALSE
                    : JSTypeConversions.RelationalComparisonResult.TRUE;
            vm.valueStack
                    .push(vm.pendingException == null ? JSBoolean.valueOf(result == expected) : JSUndefined.INSTANCE);
        } catch (JSVirtualMachineException e) {
            vm.captureVMException(e);
            vm.valueStack.push(JSUndefined.INSTANCE);
        }
        executionContext.sp = vm.valueStack.stackTop;
    }

    private static JSNumber divideOrRemainder(Opcode op, JSNumber dividend, JSNumber divisor) {
        double x = dividend.value();
        double y = divisor.value();
        if (op == Opcode.DIV) {
            return JSNumber.of(x / y);
        }
        // Positive integer operands give the same remainder under integer and floating-point division.
        // All zero and negative dividends use floating point to preserve the sign of zero.
        int a = (int) x;
        int b = (int) y;
        if (a > 0 && b > 0 && x == a && y == b) {
            return JSNumber.of(a % b);
        }
        return JSNumber.of(x % y);
    }

    static void handleAbstractEquality(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        if (stack[sp - 2] instanceof JSNumber x && stack[sp - 1] instanceof JSNumber y
                && !executionContext.virtualMachine.context.hasPendingException()) {
            boolean equal = x.value() == y.value();
            stack[sp - 2] = JSBoolean.valueOf(op == Opcode.EQ ? equal : !equal);
            executionContext.sp = sp - 1;
            executionContext.pc += op.getSize();
            return;
        }

        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        if (executionContext.capturePendingException()) {
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            return;
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(op == Opcode.EQ ? result : !result));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleAdd(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSValue rightValue = executionContext.peek(0);
        JSValue leftValue = executionContext.peek(1);
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            executionContext.set(1, JSNumber.of(leftNumber.value() + rightNumber.value()));
            executionContext.sp--;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            try {
                executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.addValues(left, right));
                if (executionContext.capturePendingException()) {
                    executionContext.virtualMachine.valueStack.pop();
                    executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                }
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.captureVMException(e);
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } catch (JSException e) {
                captureJavaScriptException(executionContext, e);
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } catch (JSErrorException e) {
                executionContext.setErrorAsPending(executionContext.virtualMachine.context.throwError(e));
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    static void handleAddLoc(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue rightValue = executionContext.pop();
        JSValue leftValue = executionContext.locals[localIndex];
        try {
            executionContext.locals[localIndex] = executionContext.virtualMachine.addValues(leftValue, rightValue);
            executionContext.capturePendingException();
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.captureVMException(e);
        } catch (JSException e) {
            captureJavaScriptException(executionContext, e);
        } catch (JSErrorException e) {
            executionContext.setErrorAsPending(executionContext.virtualMachine.context.throwError(e));
        }
        executionContext.pc = pc + op.getSize();
    }

    static void handleAnd(Opcode op, ExecutionContext executionContext) {
        bitwise(op, executionContext);
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
        executionContext.finishOpcode(pc + op.getSize(), sp);
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

    static void handleDec(Opcode op, ExecutionContext executionContext) {
        incrementTop(op, executionContext, -1);
    }

    static void handleDecLoc(Opcode op, ExecutionContext executionContext) {
        incrementLocal(op, executionContext, -1);
    }

    static void handleDivisionOrRemainder(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        if (stack[sp - 2] instanceof JSNumber dividend && stack[sp - 1] instanceof JSNumber divisor
                && executionContext.virtualMachine.pendingException == null
                && !executionContext.virtualMachine.context.hasPendingException()) {
            stack[sp - 2] = divideOrRemainder(op, dividend, divisor);
            executionContext.sp = sp - 1;
            executionContext.pc += op.getSize();
            return;
        }

        handleNumericOperands(op, executionContext);
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

    static void handleExp(Opcode op, ExecutionContext executionContext) {
        handleNumericOperands(op, executionContext);
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

    static void handleGt(Opcode op, ExecutionContext executionContext) {
        compareRelational(op, executionContext, true, false);
    }

    static void handleGte(Opcode op, ExecutionContext executionContext) {
        compareRelational(op, executionContext, false, true);
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
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        if (!(right instanceof JSObject jsObj)) {
            executionContext.raiseTypeError("invalid 'in' operand");
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }
        PropertyKey key = PropertyKey.fromValue(executionContext.virtualMachine.context, left);
        if (executionContext.capturePendingException()) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else {
            boolean result = jsObj.has(key);
            if (executionContext.capturePendingException()) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else {
                executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleInc(Opcode op, ExecutionContext executionContext) {
        incrementTop(op, executionContext, 1);
    }

    static void handleIncLoc(Opcode op, ExecutionContext executionContext) {
        incrementLocal(op, executionContext, 1);
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
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();

        // Per ECMAScript spec, right must be an object
        if (!(right instanceof JSObject constructor)) {
            executionContext.raiseTypeError("Right-hand side of instanceof is not an object");
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }

        JSValue hasInstanceMethod = constructor.get(PropertyKey.SYMBOL_HAS_INSTANCE);
        if (executionContext.capturePendingException()) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return;
        }
        if (!(hasInstanceMethod instanceof JSUndefined) && !(hasInstanceMethod instanceof JSNull)) {
            if (!(hasInstanceMethod instanceof JSFunction hasInstanceFunction)) {
                executionContext.raiseTypeError("@@hasInstance is not callable");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            JSValue result = hasInstanceFunction.call(executionContext.virtualMachine.context, right,
                    executionContext.virtualMachine.singleArg(left));
            if (executionContext.capturePendingException()) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            executionContext.virtualMachine.valueStack
                    .push(JSTypeConversions.toBoolean(result) == JSBoolean.TRUE ? JSBoolean.TRUE : JSBoolean.FALSE);
        } else {
            boolean callable = right instanceof JSFunction;
            if (right instanceof JSProxy proxy && JSTypeChecking.isFunction(proxy.getTarget())) {
                callable = true;
            }
            if (!callable) {
                executionContext.raiseTypeError("Right-hand side of instanceof is not callable");
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            try {
                executionContext.virtualMachine.valueStack
                        .push(executionContext.virtualMachine.ordinaryHasInstance(right, left)
                                ? JSBoolean.TRUE
                                : JSBoolean.FALSE);
            } catch (JSVirtualMachineException e) {
                executionContext.virtualMachine.captureVMException(e);
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
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
        executionContext.pc += op.getSize();
    }

    static void handleIsUndefined(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value.isUndefined());
        executionContext.pc += op.getSize();
    }

    static void handleIsUndefinedOrNull(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(value instanceof JSNull || value instanceof JSUndefined);
        executionContext.pc += op.getSize();
    }

    static void handleLogicalNot(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.toBoolean(operand) == JSBoolean.FALSE;
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(result));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleLt(Opcode op, ExecutionContext executionContext) {
        compareRelational(op, executionContext, false, false);
    }

    static void handleLte(Opcode op, ExecutionContext executionContext) {
        compareRelational(op, executionContext, true, true);
    }

    static void handleMul(Opcode op, ExecutionContext executionContext) {
        JSValue rightValue = executionContext.peek(0);
        JSValue leftValue = executionContext.peek(1);
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            executionContext.set(1, JSNumber.of(leftNumber.value() * rightNumber.value()));
            executionContext.sp--;
            executionContext.pc += op.getSize();
            return;
        }
        handleNumericOperands(op, executionContext);
    }

    static void handleNeg(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue numeric = executionContext.virtualMachine.toNumericValue(operand);
        if (numeric instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().negate()));
        } else {
            executionContext.virtualMachine.valueStack.push(JSNumber.of(-((JSNumber) numeric).value()));
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
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
        executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
    }

    static void handleNop(Opcode op, ExecutionContext executionContext) {
        executionContext.pc += op.getSize();
    }

    static void handleNot(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue numeric;
        try {
            numeric = executionContext.virtualMachine.toNumericValue(operand);
        } catch (JSVirtualMachineException e) {
            captureVmExceptionAsPending(executionContext, e);
            numeric = null;
        }
        if (numeric == null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else if (executionContext.virtualMachine.pendingException != null) {
            executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
        } else if (numeric instanceof JSBigInt bigInt) {
            executionContext.virtualMachine.valueStack.push(new JSBigInt(bigInt.value().not()));
        } else {
            int result = ~JSTypeConversions.toInt32(executionContext.virtualMachine.context, numeric);
            executionContext.virtualMachine.valueStack.push(JSNumber.of(result));
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleNull(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNull.INSTANCE);
        executionContext.pc += 1;
    }

    private static void handleNumericOperands(Opcode op, ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        JSValue right = vm.valueStack.pop();
        JSValue left = vm.valueStack.pop();
        VirtualMachine.NumericPair pair = vm.numericPair(left, right);
        JSValue result = JSUndefined.INSTANCE;
        if (pair != null) {
            if (pair.bigInt()) {
                result = bigIntArithmetic(vm, op, ((JSBigInt) pair.left()).value(), ((JSBigInt) pair.right()).value());
            } else {
                double x = ((JSNumber) pair.left()).value();
                double y = ((JSNumber) pair.right()).value();
                result = JSNumber.of(switch (op) {
                    case MUL -> x * y;
                    case SUB -> x - y;
                    case DIV -> x / y;
                    case MOD -> x % y;
                    case POW -> Math.pow(x, y);
                    default -> throw new IllegalArgumentException("Unexpected arithmetic opcode " + op);
                });
            }
        }
        vm.valueStack.push(result);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleObjectNew(Opcode op, ExecutionContext executionContext) {
        executionContext.push(executionContext.virtualMachine.context.createJSObject());
        executionContext.pc += op.getSize();
    }

    static void handleOr(Opcode op, ExecutionContext executionContext) {
        bitwise(op, executionContext);
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

    static void handlePlus(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSNumber result = JSTypeConversions.toNumber(executionContext.virtualMachine.context, operand);
        executionContext.virtualMachine.capturePendingException();
        executionContext.virtualMachine.valueStack.push(result);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handlePostDec(Opcode op, ExecutionContext executionContext) {
        postIncrement(op, executionContext, -1);
    }

    static void handlePostInc(Opcode op, ExecutionContext executionContext) {
        postIncrement(op, executionContext, 1);
    }

    static void handlePush0(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(0));
        executionContext.pc += 1;
    }

    static void handlePush1(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(1));
        executionContext.pc += 1;
    }

    static void handlePush2(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(2));
        executionContext.pc += 1;
    }

    static void handlePush3(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(3));
        executionContext.pc += 1;
    }

    static void handlePush4(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(4));
        executionContext.pc += 1;
    }

    static void handlePush5(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(5));
        executionContext.pc += 1;
    }

    static void handlePush6(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(6));
        executionContext.pc += 1;
    }

    static void handlePush7(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(7));
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
        executionContext.pc += op.getSize();
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

    static void handlePushMinus1(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSNumber.of(-1));
        executionContext.pc += 1;
    }

    static void handlePushThis(Opcode op, ExecutionContext executionContext) {
        VarRef derivedThisRef = executionContext.frame.getDerivedThisRef();
        if (derivedThisRef != null) {
            JSValue thisValue = derivedThisRef.get();
            if (executionContext.virtualMachine.isUninitialized(thisValue)) {
                executionContext.raiseReferenceError(
                        "Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
                executionContext.push(JSUndefined.INSTANCE);
                executionContext.pc += op.getSize();
                return;
            }
            executionContext.push(thisValue);
        } else {
            executionContext.push(executionContext.frame.getThisArg());
        }
        executionContext.pc += op.getSize();
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

    static void handleSar(Opcode op, ExecutionContext executionContext) {
        shiftSigned(op, executionContext, false);
    }

    static void handleShl(Opcode op, ExecutionContext executionContext) {
        shiftSigned(op, executionContext, true);
    }

    static void handleShr(Opcode op, ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        JSStackValue[] stack = vm.valueStack.stack;
        int sp = executionContext.sp;
        if (stack[sp - 2] instanceof JSNumber left && stack[sp - 1] instanceof JSNumber right) {
            stack[sp - 2] = unsignedShift(left, right);
            executionContext.sp = sp - 1;
            executionContext.pc += op.getSize();
            return;
        }

        vm.valueStack.stackTop = sp;
        JSValue right = vm.valueStack.pop();
        JSValue left = vm.valueStack.pop();
        JSValue leftNumeric = unsignedShiftOperand(vm, left);
        JSValue rightNumeric = leftNumeric != null && vm.pendingException == null
                ? unsignedShiftOperand(vm, right)
                : null;
        JSValue result = JSUndefined.INSTANCE;
        if (vm.pendingException == null) {
            if (leftNumeric instanceof JSBigInt || rightNumeric instanceof JSBigInt) {
                executionContext.raiseTypeError("BigInts do not support >>>");
            } else if (leftNumeric instanceof JSNumber x && rightNumeric instanceof JSNumber y) {
                result = unsignedShift(x, y);
            }
        }
        vm.valueStack.push(result);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleSpecialObject(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int objectType = executionContext.bytecode.readU8(pc + 1);
        JSValue specialObject = executionContext.virtualMachine.createSpecialObject(objectType, executionContext.frame);
        executionContext.push(specialObject);
        executionContext.pc = pc + op.getSize();
    }

    static void handleStrictEquality(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(op == Opcode.STRICT_EQ ? result : !result));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleSub(Opcode op, ExecutionContext executionContext) {
        JSValue rightValue = executionContext.peek(0);
        JSValue leftValue = executionContext.peek(1);
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            executionContext.set(1, JSNumber.of(leftNumber.value() - rightNumber.value()));
            executionContext.sp--;
            executionContext.pc += op.getSize();
            return;
        }
        handleNumericOperands(op, executionContext);
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
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        String type = JSTypeChecking.typeof(operand);
        executionContext.virtualMachine.valueStack.push(new JSString(type));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleTypeofIsFunction(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(JSKeyword.FUNCTION.equals(JSTypeChecking.typeof(value)));
        executionContext.pc += op.getSize();
    }

    static void handleTypeofIsUndefined(Opcode op, ExecutionContext executionContext) {
        int sp = executionContext.sp;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        JSValue value = (JSValue) stack[sp - 1];
        stack[sp - 1] = JSBoolean.valueOf(JSKeyword.UNDEFINED.equals(JSTypeChecking.typeof(value)));
        executionContext.pc += op.getSize();
    }

    static void handleUndefined(Opcode op, ExecutionContext executionContext) {
        executionContext.push(JSUndefined.INSTANCE);
        executionContext.pc += 1;
    }

    static void handleXor(Opcode op, ExecutionContext executionContext) {
        bitwise(op, executionContext);
    }

    private static void incrementLocal(Opcode op, ExecutionContext executionContext, int delta) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue localValue = executionContext.locals[localIndex];
        executionContext.locals[localIndex] = executionContext.virtualMachine.incrementValue(localValue, delta);
        executionContext.pc = pc + op.getSize();
    }

    private static void incrementTop(Opcode op, ExecutionContext executionContext, int delta) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine.incrementValue(operand, delta));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
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

    private static void postIncrement(Opcode op, ExecutionContext executionContext, int delta) {
        if (executionContext.peek(0) instanceof JSNumber number) {
            executionContext.push(JSNumber.of(number.value() + delta));
            executionContext.pc += op.getSize();
            return;
        }
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue operand = executionContext.virtualMachine.valueStack.pop();
        JSValue oldValue = executionContext.virtualMachine.toNumericValue(operand);
        executionContext.virtualMachine.valueStack.push(oldValue);
        executionContext.virtualMachine.valueStack
                .push(executionContext.virtualMachine.incrementNumericValue(oldValue, delta));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
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
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    private static void rotateLeft(ExecutionContext execution, int depth) {
        JSStackValue[] stack = execution.virtualMachine.valueStack.stack;
        int first = execution.sp - depth;
        JSStackValue value = stack[first];
        System.arraycopy(stack, first + 1, stack, first, depth - 1);
        stack[execution.sp - 1] = value;
        execution.pc++;
    }

    /** Returns true when delegation has left a value on the stack for the opcode to finish. */

    private static void shiftSigned(Opcode op, ExecutionContext executionContext, boolean leftShift) {
        int sp = executionContext.sp;
        JSValue rightValue = executionContext.peek(0);
        JSValue leftValue = executionContext.peek(1);
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            int leftInt = JSTypeConversions.toInt32(leftNumber.value());
            int rightInt = JSTypeConversions.toInt32(rightNumber.value());
            executionContext.set(1, JSNumber.of(leftShift ? leftInt << rightInt : leftInt >> rightInt));
            executionContext.sp--;
        } else {
            executionContext.virtualMachine.valueStack.stackTop = sp;
            JSValue right = executionContext.virtualMachine.valueStack.pop();
            JSValue left = executionContext.virtualMachine.valueStack.pop();
            VirtualMachine.NumericPair pair = executionContext.virtualMachine.numericPair(left, right);
            if (pair == null) {
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            } else if (pair.bigInt()) {
                executionContext.virtualMachine.valueStack.push(executionContext.virtualMachine
                        .shiftBigInt((JSBigInt) pair.left(), (JSBigInt) pair.right(), leftShift));
            } else {
                int leftInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.left());
                int rightInt = JSTypeConversions.toInt32(executionContext.virtualMachine.context, pair.right());
                executionContext.virtualMachine.valueStack
                        .push(JSNumber.of(leftShift ? leftInt << rightInt : leftInt >> rightInt));
            }
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
        }
        executionContext.pc += op.getSize();
    }

    private static JSNumber unsignedShift(JSNumber left, JSNumber right) {
        return JSNumber.of(Integer
                .toUnsignedLong(JSTypeConversions.toInt32(left.value()) >>> JSTypeConversions.toInt32(right.value())));
    }

    private static JSValue unsignedShiftOperand(VirtualMachine vm, JSValue value) {
        JSValue primitive;
        try {
            primitive = JSTypeConversions.toPrimitive(vm.context, value, JSTypeConversions.PreferredType.NUMBER);
        } catch (JSVirtualMachineException e) {
            vm.captureVMException(e);
            return null;
        } catch (JSException e) {
            vm.pendingException = e.getErrorValue() != null
                    ? e.getErrorValue()
                    : vm.context.throwError(e.getMessage() != null ? e.getMessage() : "toPrimitive");
            return null;
        }
        if (primitive == null || vm.pendingException != null || primitive instanceof JSBigInt) {
            return primitive;
        }
        JSValue number = JSTypeConversions.toNumber(vm.context, primitive);
        vm.capturePendingException();
        return number;
    }

}
