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

import com.caoccao.qjs4j.core.JSValue;

/**
 * Represents compiled bytecode for a function.
 */
public final class Bytecode {
    private final String[] atomPool;
    private final JSValue[] constantPool;
    private final byte[] instructions;
    private final int localCount;
    private final String[] localVarNames;
    private volatile Opcode[] decodedOpcodes;
    private volatile byte[] opcodeRebaseOffsets;

    public Bytecode(byte[] instructions, JSValue[] constantPool, String[] atomPool, int localCount) {
        this(instructions, constantPool, atomPool, localCount, null);
    }

    public Bytecode(byte[] instructions, JSValue[] constantPool, String[] atomPool, int localCount, String[] localVarNames) {
        this.instructions = instructions;
        this.constantPool = constantPool;
        this.atomPool = atomPool;
        this.localCount = localCount;
        this.localVarNames = localVarNames;
    }

    private void ensureDecodedOpcodes() {
        if (decodedOpcodes != null && opcodeRebaseOffsets != null) {
            return;
        }
        synchronized (this) {
            if (decodedOpcodes != null && opcodeRebaseOffsets != null) {
                return;
            }
            Opcode[] decoded = new Opcode[instructions.length];
            byte[] rebases = new byte[instructions.length];
            int pc = 0;
            while (pc < instructions.length) {
                int opcode = instructions[pc] & 0xFF;
                Opcode op = Opcode.fromInt(opcode);
                int rebase = 0;
                if (op == Opcode.INVALID && pc + 1 < instructions.length) {
                    int extendedOpcode = 0x100 + (instructions[pc + 1] & 0xFF);
                    Opcode extendedOp = Opcode.fromInt(extendedOpcode);
                    if (extendedOp != Opcode.INVALID) {
                        op = extendedOp;
                        rebase = 1;
                    }
                }
                decoded[pc] = op;
                rebases[pc] = (byte) rebase;
                pc += op.getSize() + rebase;
            }
            decodedOpcodes = decoded;
            opcodeRebaseOffsets = rebases;
        }
    }

    public String getAtom(int index) {
        return atomPool[index];
    }

    public String[] getAtoms() {
        return atomPool;
    }

    public JSValue getConstant(int index) {
        return constantPool[index];
    }

    public JSValue[] getConstants() {
        return constantPool;
    }

    public Opcode[] getDecodedOpcodes() {
        ensureDecodedOpcodes();
        return decodedOpcodes;
    }

    public byte[] getInstructions() {
        return instructions;
    }

    public int getLength() {
        return instructions.length;
    }

    public int getLocalCount() {
        return localCount;
    }

    public String[] getLocalVarNames() {
        return localVarNames;
    }

    public byte[] getOpcodeRebaseOffsets() {
        ensureDecodedOpcodes();
        return opcodeRebaseOffsets;
    }

    public int readI32(int offset) {
        return readU32(offset);
    }

    public int readOpcode(int offset) {
        return instructions[offset] & 0xFF;
    }

    public int readU16(int offset) {
        return ((instructions[offset] & 0xFF) << 8) | (instructions[offset + 1] & 0xFF);
    }

    public int readU32(int offset) {
        return ((instructions[offset] & 0xFF) << 24) |
                ((instructions[offset + 1] & 0xFF) << 16) |
                ((instructions[offset + 2] & 0xFF) << 8) |
                (instructions[offset + 3] & 0xFF);
    }

    public int readU8(int offset) {
        return instructions[offset] & 0xFF;
    }
}
