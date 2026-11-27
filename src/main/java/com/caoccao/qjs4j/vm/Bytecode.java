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
import com.caoccao.qjs4j.core.PropertyKey;

import java.util.Map;

/**
 * Represents compiled bytecode for a function.
 */
public final class Bytecode {
    private static final int[] EMPTY_SOURCE_POSITIONS = new int[0];
    private final String[] atomPool;
    /**
     * How each call instruction names its callee, by program counter; null when the function makes no named call.
     */
    private Map<Integer, String> callDescriptions;
    private final JSValue[] constantPool;
    private Opcode[] decodedOpcodes;
    private final byte[] instructions;
    private final int localCount;
    private final String[] localVarNames;
    private byte[] opcodeRebaseOffsets;
    private PropertyKey[] propertyKeyCache;
    private int[] sourcePositions;

    public Bytecode(byte[] instructions, JSValue[] constantPool, String[] atomPool, int localCount) {
        this(instructions, constantPool, atomPool, localCount, null);
    }

    public Bytecode(byte[] instructions, JSValue[] constantPool, String[] atomPool, int localCount,
            String[] localVarNames) {
        this.instructions = instructions;
        this.constantPool = constantPool;
        this.atomPool = atomPool;
        this.localCount = localCount;
        this.localVarNames = localVarNames;
        this.sourcePositions = EMPTY_SOURCE_POSITIONS;
    }

    private void ensureDecodedOpcodes() {
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

    public String getAtom(int index) {
        return atomPool[index];
    }

    public String[] getAtoms() {
        return atomPool;
    }

    /**
     * Get a cached PropertyKey for the given atom index. Avoids allocating a new PropertyKey on every
     * GET_FIELD/PUT_FIELD opcode.
     */
    public PropertyKey getCachedPropertyKey(int atomIndex) {
        if (propertyKeyCache == null) {
            propertyKeyCache = new PropertyKey[atomPool.length];
        }
        PropertyKey key = propertyKeyCache[atomIndex];
        if (key == null) {
            key = PropertyKey.fromString(atomPool[atomIndex]);
            propertyKeyCache[atomIndex] = key;
        }
        return key;
    }

    /**
     * How the call instruction at a program counter names its callee, as V8 does in {@code x is not a function}.
     *
     * @param pc
     *            the program counter of a call instruction
     * @return the name, or null when the callee is to be described by its value
     */
    public String getCallDescription(int pc) {
        return callDescriptions == null ? null : callDescriptions.get(pc);
    }

    /**
     * The table behind {@link #getCallDescription(int)}, shared by copies of this bytecode.
     *
     * @return the table, or null
     */
    public Map<Integer, String> getCallDescriptions() {
        return callDescriptions;
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

    /**
     * The source position of the instruction at a program counter: QuickJS's {@code find_line_num}.
     *
     * @param pc
     *            the program counter
     * @return {line, column}, both 1-based, or null when the function recorded no position at or before it
     */
    public int[] getSourcePosition(int pc) {
        int low = 0;
        int high = sourcePositions.length / 3 - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (sourcePositions[middle * 3] <= pc) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        if (found < 0) {
            return null;
        }
        return new int[]{sourcePositions[found * 3 + 1], sourcePositions[found * 3 + 2]};
    }

    /**
     * The (pc, line, column) triples behind {@link #getSourcePosition(int)}.
     *
     * @return the table, in ascending pc order
     */
    public int[] getSourcePositions() {
        return sourcePositions;
    }

    /** Decode a four-byte atom-pool index and resolve the name at that operand. */
    String readAtom(int offset) {
        return getAtom(readU32(offset));
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
        return ((instructions[offset] & 0xFF) << 24) | ((instructions[offset + 1] & 0xFF) << 16)
                | ((instructions[offset + 2] & 0xFF) << 8) | (instructions[offset + 3] & 0xFF);
    }

    public int readU8(int offset) {
        return instructions[offset] & 0xFF;
    }

    public void setCallDescriptions(Map<Integer, String> callDescriptions) {
        this.callDescriptions = callDescriptions;
    }

    public void setSourcePositions(int[] sourcePositions) {
        this.sourcePositions = sourcePositions;
    }
}
