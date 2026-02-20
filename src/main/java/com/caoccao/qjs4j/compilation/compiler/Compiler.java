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

import com.caoccao.qjs4j.compilation.Lexer;
import com.caoccao.qjs4j.compilation.ast.Program;
import com.caoccao.qjs4j.compilation.parser.Parser;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.vm.Bytecode;

/**
 * Main compiler interface that integrates the entire compilation pipeline.
 * <p>
 * Pipeline: JavaScript Source → Lexer → Tokens → Parser → AST → BytecodeCompiler → Bytecode
 */
public final class Compiler {
    private final String fileName;
    private final String source;
    private boolean isEval; // true if compiling eval code

    public Compiler(String source, String fileName) {
        if (source == null) {
            throw new JSCompilerException("Source code cannot be null");
        }
        this.source = source;
        this.fileName = fileName;
        this.isEval = false;
    }

    /**
     * Compile JavaScript source code into executable bytecode.
     *
     * @param isModule true to compile as ES6 module (always strict), false for script
     * @return A CompileResult containing the bytecode function and parsed AST
     * @throws JSCompilerException if compilation fails
     */
    public CompileResult compile(boolean isModule) {
        try {
            Program ast = parse(isModule);
            BytecodeCompiler compiler = new BytecodeCompiler();
            compiler.setSourceCode(source);
            Bytecode bytecode = compiler.compile(ast);
            String name = fileName != null ? fileName : (isModule ? "<module>" : "<script>");
            boolean strict = isModule || ast.strict();
            JSBytecodeFunction func = new JSBytecodeFunction(bytecode, name, 0, strict, null);
            return new CompileResult(func, ast);
        } catch (JSCompilerException | JSErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new JSCompilerException("Unexpected compilation error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JavaScript source code into an AST (without bytecode compilation).
     * Useful for static analysis, code transformation, etc.
     *
     * @param isModule the is module
     * @return The parsed AST Program node
     * @throws JSCompilerException if parsing fails
     */
    public Program parse(boolean isModule) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, isModule, isEval);
        return parser.parse();
    }

    /**
     * Set whether this is compiling eval code.
     * Per QuickJS, return statements at top level of eval code throw SyntaxError.
     *
     * @param isEval true if compiling eval code
     * @return this compiler for chaining
     */
    public Compiler setEval(boolean isEval) {
        this.isEval = isEval;
        return this;
    }

    public record CompileResult(JSBytecodeFunction function, Program ast) {
    }
}
