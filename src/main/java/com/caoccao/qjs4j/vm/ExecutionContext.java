package com.caoccao.qjs4j.vm;

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
    final StackFrame previousFrame;
    final int restoreStackTop;
    final boolean savedStrictMode;
    final VirtualMachine virtualMachine;
    boolean opcodeRequestedReturn;
    int pc;
    JSValue returnValue;
    int sp;

    ExecutionContext(
            VirtualMachine virtualMachine,
            Bytecode bytecode,
            StackFrame frame,
            StackFrame previousFrame,
            int frameStackBase,
            int restoreStackTop,
            boolean savedStrictMode) {
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
        this.opcodeRequestedReturn = false;
        this.returnValue = null;
        this.sp = 0;
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

    void set(int offset, JSValue value) {
        virtualMachine.valueStack.stack[sp - 1 - offset] = value;
    }
}
