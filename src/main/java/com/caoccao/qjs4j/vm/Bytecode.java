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
    private final byte[] instructions;
    private final JSValue[] constantPool;
    private final String[] atomPool;

    public Bytecode(byte[] instructions, JSValue[] constantPool, String[] atomPool) {
        this.instructions = instructions;
        this.constantPool = constantPool;
        this.atomPool = atomPool;
    }

    public int readOpcode(int offset) {
        return instructions[offset] & 0xFF;
    }

    public int readU8(int offset) {
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

    public JSValue getConstant(int index) {
        return constantPool[index];
    }

    public String getAtom(int index) {
        return atomPool[index];
    }

    public byte[] getInstructions() {
        return instructions;
    }

    public int getLength() {
        return instructions.length;
    }
}
