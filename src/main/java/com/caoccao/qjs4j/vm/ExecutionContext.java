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
    final JSStackValue[] stack;
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
        this.stack = virtualMachine.valueStack.stack;
        this.pc = 0;
        this.opcodeRequestedReturn = false;
        this.returnValue = null;
        this.sp = 0;
    }
}
