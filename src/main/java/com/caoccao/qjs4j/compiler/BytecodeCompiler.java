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

import com.caoccao.qjs4j.compiler.ast.ASTNode;
import com.caoccao.qjs4j.vm.Bytecode;

/**
 * Compiles AST into bytecode.
 */
public final class BytecodeCompiler {
    private final BytecodeEmitter emitter;

    public BytecodeCompiler() {
        this.emitter = new BytecodeEmitter();
    }

    public Bytecode compile(ASTNode ast) {
        return null;
    }
}
