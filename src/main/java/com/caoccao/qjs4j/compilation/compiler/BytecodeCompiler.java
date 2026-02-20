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

import com.caoccao.qjs4j.compilation.ast.ASTNode;
import com.caoccao.qjs4j.compilation.ast.Program;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Bytecode;

/**
 * Compiles AST into bytecode.
 * <p>
 * This is the public facade that delegates to specialized compiler classes:
 * {@link ExpressionCompiler}, {@link StatementCompiler}, {@link FunctionClassCompiler},
 * {@link PatternCompiler}, {@link EmitHelpers}, and {@link CompilerAnalysis}.
 * All shared mutable state is held in {@link CompilerContext}.
 */
public final class BytecodeCompiler {
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;

    public BytecodeCompiler() {
        this(false, null);
    }

    /**
     * Create a BytecodeCompiler with inherited strict mode.
     * Following QuickJS pattern where nested functions inherit parent's strict mode.
     *
     * @param inheritedStrictMode Strict mode inherited from parent function
     */
    public BytecodeCompiler(boolean inheritedStrictMode) {
        this(inheritedStrictMode, null);
    }

    BytecodeCompiler(boolean inheritedStrictMode, CaptureResolver parentCaptureResolver) {
        this.ctx = new CompilerContext(inheritedStrictMode, parentCaptureResolver);
        this.delegates = new CompilerDelegates(ctx);
    }

    /**
     * Compile an AST into bytecode.
     *
     * @param ast the AST to compile (must be a Program node)
     * @return the compiled bytecode
     */
    public Bytecode compile(ASTNode ast) {
        if (ast instanceof Program program) {
            delegates.statements.compileProgram(program);
        } else {
            throw new JSCompilerException("Expected Program node");
        }
        return ctx.emitter.build(ctx.maxLocalCount);
    }

    /**
     * Get the CompilerContext for this compiler.
     * Used by delegate classes to access nested compiler state.
     */
    CompilerContext context() {
        return ctx;
    }

    /**
     * Get the CompilerDelegates for this compiler.
     * Used by delegate classes to call methods on nested compiler delegates.
     */
    CompilerDelegates delegates() {
        return delegates;
    }

    /**
     * Set the original source code (used for extracting function source in toString()).
     */
    public void setSourceCode(String sourceCode) {
        ctx.sourceCode = sourceCode;
    }
}
