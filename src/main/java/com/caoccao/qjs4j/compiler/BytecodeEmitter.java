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

package com.caoccao.qjs4j.compiler;

import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.utils.AtomTable;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits bytecode instructions.
 * Handles encoding of opcodes, operands, and manages constant/atom pools.
 */
public final class BytecodeEmitter {
    private final List<String> atomPool;
    private final AtomTable atoms;
    private final ByteArrayOutputStream code;
    private final Map<JSValue, Integer> constantIndexCache;
    private final List<JSValue> constantPool;

    public BytecodeEmitter() {
        this.code = new ByteArrayOutputStream();
        this.constantPool = new ArrayList<>();
        this.atoms = new AtomTable();
        this.constantIndexCache = new HashMap<>();
        this.atomPool = new ArrayList<>();
    }

    /**
     * Build the final Bytecode object.
     */
    public Bytecode build(int localCount) {
        byte[] instructions = code.toByteArray();
        JSValue[] constants = constantPool.toArray(new JSValue[0]);
        String[] atoms = atomPool.toArray(new String[0]);

        return new Bytecode(instructions, constants, atoms, localCount);
    }

    /**
     * Get the current bytecode offset.
     */
    public int currentOffset() {
        return code.size();
    }

    /**
     * Emit an atom (interned string) reference.
     * Returns the atom index.
     */
    public int emitAtom(String str) {
        int index = atomPool.indexOf(str);
        if (index == -1) {
            index = atomPool.size();
            atomPool.add(str);
        }
        emitU32(index);
        return index;
    }

    /**
     * Add a constant to the constant pool and emit its index.
     * Returns the constant index.
     */
    public int emitConstant(JSValue value) {
        Integer cached = constantIndexCache.get(value);
        if (cached != null) {
            emitU32(cached);
            return cached;
        }

        int index = constantPool.size();
        constantPool.add(value);
        constantIndexCache.put(value, index);
        emitU32(index);
        return index;
    }

    /**
     * Emit a signed 32-bit integer value.
     */
    public void emitI32(int value) {
        emitU32(value);
    }

    /**
     * Reserve space for a jump offset and return the position to patch later.
     */
    public int emitJump(Opcode jumpOp) {
        emitOpcode(jumpOp);
        int jumpPos = currentOffset();
        emitU32(0xFFFFFFFF); // Placeholder
        return jumpPos;
    }

    /**
     * Emit a single opcode.
     */
    public void emitOpcode(Opcode op) {
        int opcode = op.getCode();
        if (opcode <= 0xFF) {
            code.write(opcode);
        } else {
            // Extended opcode encoding:
            // - prefix 0x00 (INVALID opcode slot)
            // - second byte stores opcode - 256
            // The VM decoder maps this pair back to the original Opcode enum entry.
            code.write(0);
            code.write(opcode - 0x100);
        }
    }

    /**
     * Emit an opcode with an atom operand.
     */
    public void emitOpcodeAtom(Opcode op, String atom) {
        emitOpcode(op);
        emitAtom(atom);
    }

    /**
     * Emit an opcode with a constant operand.
     */
    public void emitOpcodeConstant(Opcode op, JSValue constant) {
        emitOpcode(op);
        emitConstant(constant);
    }

    /**
     * Emit an opcode with a u16 operand.
     */
    public void emitOpcodeU16(Opcode op, int value) {
        emitOpcode(op);
        emitU16(value);
    }

    /**
     * Emit an opcode with a u32 operand.
     */
    public void emitOpcodeU32(Opcode op, int value) {
        emitOpcode(op);
        emitU32(value);
    }

    /**
     * Emit an opcode with a u8 operand.
     */
    public void emitOpcodeU8(Opcode op, int value) {
        emitOpcode(op);
        emitU8(value);
    }

    /**
     * Emit an unsigned 16-bit value (big-endian).
     */
    public void emitU16(int value) {
        code.write((value >> 8) & 0xFF);
        code.write(value & 0xFF);
    }

    /**
     * Emit an unsigned 32-bit value (big-endian).
     */
    public void emitU32(int value) {
        code.write((value >> 24) & 0xFF);
        code.write((value >> 16) & 0xFF);
        code.write((value >> 8) & 0xFF);
        code.write(value & 0xFF);
    }

    /**
     * Emit an unsigned 8-bit value.
     */
    public void emitU8(int value) {
        code.write(value & 0xFF);
    }

    /**
     * Get the atom pool.
     */
    public List<String> getAtomPool() {
        return atomPool;
    }

    /**
     * Get the current bytecode as array.
     */
    public byte[] getCode() {
        return code.toByteArray();
    }

    /**
     * Get code size in bytes.
     */
    public int getCodeSize() {
        return code.size();
    }

    /**
     * Get the constant pool.
     */
    public List<JSValue> getConstantPool() {
        return constantPool;
    }

    /**
     * Patch a previously emitted jump instruction with the target offset.
     */
    public void patchJump(int offset, int target) {
        byte[] bytes = code.toByteArray();
        int jumpDistance = target - (offset + 4);

        bytes[offset] = (byte) ((jumpDistance >> 24) & 0xFF);
        bytes[offset + 1] = (byte) ((jumpDistance >> 16) & 0xFF);
        bytes[offset + 2] = (byte) ((jumpDistance >> 8) & 0xFF);
        bytes[offset + 3] = (byte) (jumpDistance & 0xFF);

        // Reset stream with patched bytes
        code.reset();
        code.write(bytes, 0, bytes.length);
    }
}
