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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.*;

/**
 * Emits bytecode instructions.
 * Handles encoding of opcodes, operands, and manages constant/atom pools.
 */
public final class BytecodeEmitter {
    private static final int INITIAL_CODE_CAPACITY = 256;
    private final Map<String, Integer> atomIndexCache;
    private final List<String> atomPool;
    private final Map<JSValue, Integer> constantIndexCache;
    private final List<JSValue> constantPool;
    /**
     * Growable code buffer, written and patched in place.
     * <p>
     * This used to be a {@link java.io.ByteArrayOutputStream}, which offers no way to overwrite an
     * already-written byte: every {@link #patchJump(int, int)} and {@link #markCatchAsFinally(int)}
     * had to copy the whole buffer out, edit it, reset the stream and copy it back. Compiling a
     * function with <em>J</em> jumps over <em>N</em> bytes of code cost O(J&middot;N) in raw array
     * copying, and every {@code if}, loop, {@code &&}, {@code ||}, {@code ?:}, {@code try} and
     * {@code switch} emits at least one patch.
     */
    private byte[] code;
    private int codeSize;

    public BytecodeEmitter() {
        this.code = new byte[INITIAL_CODE_CAPACITY];
        this.codeSize = 0;
        this.constantPool = new ArrayList<>();
        this.constantIndexCache = new HashMap<>();
        this.atomPool = new ArrayList<>();
        this.atomIndexCache = new HashMap<>();
    }

    /**
     * Build the final Bytecode object.
     */
    public Bytecode build(int localCount) {
        return build(localCount, null);
    }

    /**
     * Build the final Bytecode object with local variable name information.
     */
    public Bytecode build(int localCount, String[] localVarNames) {
        byte[] instructions = Arrays.copyOf(code, codeSize);
        JSValue[] constants = constantPool.toArray(JSValue.NO_ARGS);
        String[] atoms = atomPool.toArray(new String[0]);

        return new Bytecode(instructions, constants, atoms, localCount, localVarNames);
    }

    /**
     * Get the current bytecode offset.
     */
    public int currentOffset() {
        return codeSize;
    }

    /**
     * Emit an atom (interned string) reference.
     * Returns the atom index.
     */
    public int emitAtom(String str) {
        // Cached the same way emitConstant is. A linear atomPool.indexOf(str) per emitted atom
        // cost O(A^2) for A distinct atoms in a function.
        Integer cached = atomIndexCache.get(str);
        int index;
        if (cached != null) {
            index = cached;
        } else {
            index = atomPool.size();
            atomPool.add(str);
            atomIndexCache.put(str, index);
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
            write(opcode);
        } else {
            // Extended opcode encoding:
            // - prefix 0x00 (INVALID opcode slot)
            // - second byte stores opcode - 256
            // The VM decoder maps this pair back to the original Opcode enum entry.
            write(0);
            write(opcode - 0x100);
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
        if (op.getSize() == 5) {
            emitU32(value);
        } else {
            emitU16(value);
        }
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
        write(value >> 8);
        write(value);
    }

    /**
     * Emit an unsigned 32-bit value (big-endian).
     */
    public void emitU32(int value) {
        write(value >> 24);
        write(value >> 16);
        write(value >> 8);
        write(value);
    }

    /**
     * Emit an unsigned 8-bit value.
     */
    public void emitU8(int value) {
        write(value);
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
        return Arrays.copyOf(code, codeSize);
    }

    /**
     * Get code size in bytes.
     */
    public int getCodeSize() {
        return codeSize;
    }

    /**
     * Get the constant pool.
     */
    public List<JSValue> getConstantPool() {
        return constantPool;
    }

    /**
     * Mark a previously patched CATCH jump offset as a finally handler
     * by setting bit 31 of the I32 offset. The VM uses this flag to
     * distinguish try-catch from try-finally handlers during generator
     * return unwinding.
     */
    public void markCatchAsFinally(int offset) {
        code[offset] |= (byte) 0x80;
    }

    /**
     * Patch a previously emitted jump instruction with the target offset.
     */
    public void patchJump(int offset, int target) {
        int jumpDistance = target - (offset + 4);

        code[offset] = (byte) (jumpDistance >> 24);
        code[offset + 1] = (byte) (jumpDistance >> 16);
        code[offset + 2] = (byte) (jumpDistance >> 8);
        code[offset + 3] = (byte) jumpDistance;
    }

    /**
     * Append one byte, growing the buffer when needed.
     *
     * @param value the value whose low 8 bits are appended
     */
    private void write(int value) {
        if (codeSize == code.length) {
            code = Arrays.copyOf(code, code.length * 2);
        }
        code[codeSize++] = (byte) value;
    }
}
