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

import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSValue;

/**
 * The JavaScript virtual machine bytecode interpreter.
 */
public final class VirtualMachine {
    private final CallStack stack;
    private StackFrame currentFrame;

    public VirtualMachine() {
        this.stack = new CallStack();
        this.currentFrame = null;
    }

    public JSValue execute(JSBytecodeFunction function, JSValue thisArg, JSValue[] args) {
        return null;
    }

    private int readOpcode(Bytecode bytecode, int pc) {
        return bytecode.readOpcode(pc);
    }

    private void handlePushI32() {
    }

    private void handlePushConst() {
    }

    private void handleAdd() {
    }

    private JSValue handleReturn() {
        return null;
    }
}
