package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSStackValue;
import com.caoccao.qjs4j.core.JSValue;

final class ExecutionContext {
    final Bytecode bytecode;
    final Opcode[] decodedOpcodes;
    final StackFrame frame;
    final int frameStackBase;
    final byte[] instructions;
    final JSValue[] locals;
    final byte[] opcodeRebaseOffsets;
    boolean opcodeRequestedReturn;
    int pc;
    final StackFrame previousFrame;
    final int restoreStackTop;
    JSValue returnValue;
    final boolean savedStrictMode;
    int sp;
    final VirtualMachine virtualMachine;

    ExecutionContext(VirtualMachine virtualMachine, Bytecode bytecode, StackFrame frame, StackFrame previousFrame,
            int frameStackBase, int restoreStackTop, boolean savedStrictMode) {
        this.virtualMachine = virtualMachine;
        this.bytecode = bytecode;
        this.instructions = bytecode.getInstructions();
        this.decodedOpcodes = bytecode.getDecodedOpcodes();
        this.opcodeRebaseOffsets = bytecode.getOpcodeRebaseOffsets();
        this.frame = frame;
        this.previousFrame = previousFrame;
        this.frameStackBase = frameStackBase;
        this.restoreStackTop = restoreStackTop;
        this.savedStrictMode = savedStrictMode;
        this.locals = frame.getLocals();
        this.pc = 0;
        frame.setExecutionContext(this);
        this.opcodeRequestedReturn = false;
        this.returnValue = null;
        this.sp = 0;
    }

    void advance(Opcode op) {
        pc += op.getSize();
    }

    CallStack beginStackOpcode() {
        CallStack stack = virtualMachine.valueStack;
        stack.stackTop = sp;
        return stack;
    }

    boolean capturePendingException() {
        JSContext context = virtualMachine.context;
        if (context.hasPendingException()) {
            virtualMachine.pendingException = context.takePendingException();
            return true;
        }
        return false;
    }

    /** Publish the next instruction and the operand stack depth after a handler completes. */
    void finishOpcode(int nextPc, int stackPointer) {
        sp = stackPointer;
        pc = nextPc;
    }

    void finishOpcode(Opcode op, int stackPointer) {
        finishOpcode(pc + op.getSize(), stackPointer);
    }

    /** A handler using CallStack.push/pop takes its final depth from that stack. */
    void finishStackOpcode(int nextPc) {
        finishOpcode(nextPc, virtualMachine.valueStack.stackTop);
    }

    void finishStackOpcode(Opcode op) {
        finishOpcode(op, virtualMachine.valueStack.stackTop);
    }

    /** A getter or call can grow the backing stack while it runs. Publish to its current array. */
    void finishWithResult(Opcode op, int resultSlot, JSValue value) {
        virtualMachine.valueStack.stack[resultSlot] = value;
        finishOpcode(op, resultSlot + 1);
    }

    JSValue peek(int offset) {
        return (JSValue) virtualMachine.valueStack.stack[sp - 1 - offset];
    }

    JSValue pop() {
        return (JSValue) virtualMachine.valueStack.stack[--sp];
    }

    JSStackValue popStackValue() {
        return virtualMachine.valueStack.stack[--sp];
    }

    void push(JSValue value) {
        CallStack valueStack = virtualMachine.valueStack;
        if (sp >= valueStack.stack.length) {
            valueStack.stackTop = sp;
            valueStack.push(value);
            sp = valueStack.stackTop;
        } else {
            valueStack.stack[sp++] = value;
        }
    }

    void pushStackValue(JSStackValue value) {
        CallStack valueStack = virtualMachine.valueStack;
        if (sp >= valueStack.stack.length) {
            valueStack.stackTop = sp;
            valueStack.pushStackValue(value);
            sp = valueStack.stackTop;
        } else {
            valueStack.stack[sp++] = value;
        }
    }

    void raiseReferenceError(String message) {
        setErrorAsPending(virtualMachine.context.throwReferenceError(message));
    }

    void raiseTypeError(String message) {
        setErrorAsPending(virtualMachine.context.throwTypeError(message));
    }

    void set(int offset, JSValue value) {
        virtualMachine.valueStack.stack[sp - 1 - offset] = value;
    }

    void setErrorAsPending(JSValue errorValue) {
        virtualMachine.pendingException = errorValue;
        virtualMachine.context.clearPendingException();
    }
}
