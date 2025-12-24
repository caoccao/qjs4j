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
import com.caoccao.qjs4j.util.AtomTable;
import com.caoccao.qjs4j.vm.Opcode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Emits bytecode instructions.
 */
public final class BytecodeEmitter {
    private final ByteArrayOutputStream code;
    private final List<JSValue> constantPool;
    private final AtomTable atoms;

    public BytecodeEmitter() {
        this.code = new ByteArrayOutputStream();
        this.constantPool = new ArrayList<>();
        this.atoms = new AtomTable();
    }

    public void emitOpcode(Opcode op) {
    }

    public void emitU8(int value) {
    }

    public void emitU16(int value) {
    }

    public void emitU32(int value) {
    }

    public void emitAtom(String str) {
    }

    public void emitConstant(JSValue value) {
    }

    public int currentOffset() {
        return code.size();
    }

    public void patchJump(int offset, int target) {
    }
}
