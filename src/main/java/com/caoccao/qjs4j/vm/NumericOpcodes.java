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
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.math.BigInteger;

/** Arithmetic, bitwise and comparison instructions on Number and BigInt operands. */
final class NumericOpcodes {
    private NumericOpcodes() {
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

    static void bitwise(Opcode op, ExecutionContext executionContext) {
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
        executionContext.advance(op);
    }

    private static int bitwiseInt(Opcode op, int left, int right) {
        return switch (op) {
            case AND -> left & right;
            case OR -> left | right;
            case XOR -> left ^ right;
            default -> throw new IllegalArgumentException(op.name());
        };
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
        ctx.advance(op);
    }

    private static void compareRelationalOperands(ExecutionContext executionContext, boolean reverse, boolean negate) {
        VirtualMachine vm = executionContext.virtualMachine;
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue right = operandStack.pop();
        JSValue left = operandStack.pop();
        try {
            // Reversing the comparison still coerces the original left operand first.
            JSTypeConversions.RelationalComparisonResult result = reverse
                    ? JSTypeConversions.lessThanResult(vm.context, right, left, false)
                    : JSTypeConversions.lessThanResult(vm.context, left, right);
            vm.capturePendingException();
            JSTypeConversions.RelationalComparisonResult expected = negate
                    ? JSTypeConversions.RelationalComparisonResult.FALSE
                    : JSTypeConversions.RelationalComparisonResult.TRUE;
            operandStack
                    .push(vm.pendingException == null ? JSBoolean.valueOf(result == expected) : JSUndefined.INSTANCE);
        } catch (JSVirtualMachineException e) {
            vm.captureVMException(e);
            operandStack.push(JSUndefined.INSTANCE);
        }
        executionContext.sp = operandStack.stackTop;
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
            executionContext.advance(op);
            return;
        }

        executionContext.beginStackOpcode();
        JSValue right = executionContext.virtualMachine.valueStack.pop();
        JSValue left = executionContext.virtualMachine.valueStack.pop();
        boolean result = JSTypeConversions.abstractEquals(executionContext.virtualMachine.context, left, right);
        if (executionContext.capturePendingException()) {
            executionContext.sp = executionContext.virtualMachine.valueStack.stackTop;
            return;
        }
        executionContext.virtualMachine.valueStack.push(JSBoolean.valueOf(op == Opcode.EQ ? result : !result));
        executionContext.finishStackOpcode(op);
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
        executionContext.advance(op);
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
            executionContext.advance(op);
            return;
        }

        handleNumericOperands(op, executionContext);
    }

    static void handleGt(Opcode op, ExecutionContext executionContext) {
        compareRelational(op, executionContext, true, false);
    }

    static void handleGte(Opcode op, ExecutionContext executionContext) {
        compareRelational(op, executionContext, false, true);
    }

    static void handleInc(Opcode op, ExecutionContext executionContext) {
        incrementTop(op, executionContext, 1);
    }

    static void handleIncLoc(Opcode op, ExecutionContext executionContext) {
        incrementLocal(op, executionContext, 1);
    }

    static void handleLt(Opcode op, ExecutionContext executionContext) {
        compareRelational(op, executionContext, false, false);
    }

    static void handleLte(Opcode op, ExecutionContext executionContext) {
        compareRelational(op, executionContext, true, true);
    }

    static void handleNeg(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue operand = operandStack.pop();
        JSValue numeric = executionContext.virtualMachine.toNumericValue(operand);
        if (numeric instanceof JSBigInt bigInt) {
            operandStack.push(new JSBigInt(bigInt.value().negate()));
        } else {
            operandStack.push(JSNumber.of(-((JSNumber) numeric).value()));
        }
        executionContext.finishStackOpcode(op);
    }

    static void handleNot(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue operand = operandStack.pop();
        JSValue numeric;
        try {
            numeric = executionContext.virtualMachine.toNumericValue(operand);
        } catch (JSVirtualMachineException e) {
            captureVmExceptionAsPending(executionContext, e);
            numeric = null;
        }
        if (numeric == null) {
            operandStack.push(JSUndefined.INSTANCE);
        } else if (executionContext.virtualMachine.pendingException != null) {
            operandStack.push(JSUndefined.INSTANCE);
        } else if (numeric instanceof JSBigInt bigInt) {
            operandStack.push(new JSBigInt(bigInt.value().not()));
        } else {
            int result = ~JSTypeConversions.toInt32(executionContext.virtualMachine.context, numeric);
            operandStack.push(JSNumber.of(result));
        }
        executionContext.finishStackOpcode(op);
    }

    static void handleNumericOperands(Opcode op, ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        executionContext.beginStackOpcode();
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
        executionContext.finishStackOpcode(op);
    }

    static void handlePlus(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue operand = operandStack.pop();
        JSNumber result = JSTypeConversions.toNumber(executionContext.virtualMachine.context, operand);
        executionContext.virtualMachine.capturePendingException();
        operandStack.push(result);
        executionContext.finishStackOpcode(op);
    }

    static void handlePostDec(Opcode op, ExecutionContext executionContext) {
        postIncrement(op, executionContext, -1);
    }

    static void handlePostInc(Opcode op, ExecutionContext executionContext) {
        postIncrement(op, executionContext, 1);
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
            executionContext.advance(op);
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
        executionContext.finishStackOpcode(op);
    }

    static void handleStrictEquality(Opcode op, ExecutionContext executionContext) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue right = operandStack.pop();
        JSValue left = operandStack.pop();
        boolean result = JSTypeConversions.strictEquals(left, right);
        operandStack.push(JSBoolean.valueOf(op == Opcode.STRICT_EQ ? result : !result));
        executionContext.finishStackOpcode(op);
    }

    private static void incrementLocal(Opcode op, ExecutionContext executionContext, int delta) {
        int pc = executionContext.pc;
        int localIndex = executionContext.instructions[pc + 1] & 0xFF;
        JSValue localValue = executionContext.locals[localIndex];
        executionContext.locals[localIndex] = executionContext.virtualMachine.incrementValue(localValue, delta);
        executionContext.pc = pc + op.getSize();
    }

    private static void incrementTop(Opcode op, ExecutionContext executionContext, int delta) {
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue operand = operandStack.pop();
        operandStack.push(executionContext.virtualMachine.incrementValue(operand, delta));
        executionContext.finishStackOpcode(op);
    }

    static void multiplyOrSubtract(Opcode op, ExecutionContext executionContext) {
        JSValue rightValue = executionContext.peek(0);
        JSValue leftValue = executionContext.peek(1);
        if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
            executionContext.set(1,
                    JSNumber.of(op == Opcode.MUL
                            ? leftNumber.value() * rightNumber.value()
                            : leftNumber.value() - rightNumber.value()));
            executionContext.sp--;
            executionContext.advance(op);
            return;
        }
        handleNumericOperands(op, executionContext);
    }

    private static void postIncrement(Opcode op, ExecutionContext executionContext, int delta) {
        if (executionContext.peek(0) instanceof JSNumber number) {
            executionContext.push(JSNumber.of(number.value() + delta));
            executionContext.advance(op);
            return;
        }
        CallStack operandStack = executionContext.beginStackOpcode();
        JSValue operand = operandStack.pop();
        JSValue oldValue = executionContext.virtualMachine.toNumericValue(operand);
        operandStack.push(oldValue);
        operandStack.push(executionContext.virtualMachine.incrementNumericValue(oldValue, delta));
        executionContext.finishStackOpcode(op);
    }

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
        executionContext.advance(op);
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
