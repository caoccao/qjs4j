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
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Bytecode;

import java.util.Map;

/**
 * Compiles AST into bytecode.
 * <p>
 * This is the public facade that delegates to specialized compiler classes:
 * {@link ExpressionCompiler}, {@link StatementCompiler}, {@link FunctionClassCompiler},
 * {@link PatternCompiler}, {@link EmitHelpers}, and {@link CompilerAnalysis}.
 * All shared mutable state is held in {@link CompilerContext}.
 */
public final class BytecodeCompiler {
    private final CompilerContext compilerContext;
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
        this(inheritedStrictMode, null, null);
    }

    BytecodeCompiler(boolean inheritedStrictMode, CaptureResolver parentCaptureResolver) {
        this(inheritedStrictMode, parentCaptureResolver, null);
    }

    BytecodeCompiler(boolean inheritedStrictMode, CaptureResolver parentCaptureResolver, JSContext context) {
        this.compilerContext = new CompilerContext(inheritedStrictMode, parentCaptureResolver, context);
        this.delegates = new CompilerDelegates(compilerContext);
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

        int localCount;
        if (compilerContext.scopeManager.isEmpty()) {
            localCount = compilerContext.scopeManager.getMaxLocalCount();
        } else {
            localCount = compilerContext.scopeManager.currentScope().getLocalCount();
        }
        String[] localVarNames = compilerContext.scopeManager.getLocalVarNames();
        return compilerContext.emitter.build(localCount, localVarNames);
    }

    /**
     * Get the CompilerContext for this compiler.
     * Used by delegate classes to access nested compiler state.
     */
    CompilerContext context() {
        return compilerContext;
    }

    /**
     * Get the CompilerDelegates for this compiler.
     * Used by delegate classes to call methods on nested compiler delegates.
     */
    CompilerDelegates delegates() {
        return delegates;
    }

    public void setClassFieldEvalContext(boolean classFieldEvalContext) {
        compilerContext.classFieldEvalContext = classFieldEvalContext;
    }

    public void setContext(JSContext context) {
        compilerContext.context = context;
    }

    public void setEvalMode(boolean evalMode) {
        compilerContext.evalMode = evalMode;
    }

    public void setPredeclareProgramLexicalsAsLocals(boolean predeclareProgramLexicalsAsLocals) {
        compilerContext.predeclareProgramLexicalsAsLocals = predeclareProgramLexicalsAsLocals;
    }

    public void setPrivateSymbols(Map<String, JSSymbol> privateSymbols) {
        compilerContext.privateSymbols = privateSymbols != null ? privateSymbols : Map.of();
    }

    /**
     * Set the original source code (used for extracting function source in toString()).
     */
    public void setSourceCode(String sourceCode) {
        compilerContext.sourceCode = sourceCode;
    }
}
