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

import com.caoccao.qjs4j.compiler.ast.Program;
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

    /**
     * Compile JavaScript source code into executable bytecode.
     *
     * @param source   The JavaScript source code to compile
     * @param filename Optional filename for error reporting (can be null)
     * @return A JSBytecodeFunction containing the compiled bytecode
     * @throws JSCompilerException if compilation fails
     */
    public static JSBytecodeFunction compile(String source, String filename) {
        if (source == null) {
            throw new JSCompilerException("Source code cannot be null");
        }

        try {
            // Stage 1 & 2: Lexical and Syntax Analysis (Source → Tokens → AST)
            Lexer lexer = new Lexer(source);
            Parser parser = new Parser(lexer, false);
            Program ast = parser.parse();

            // Stage 3: Code Generation (AST → Bytecode)
            BytecodeCompiler compiler = new BytecodeCompiler();
            compiler.setSourceCode(source);  // Store source for extracting function source
            Bytecode bytecode = compiler.compile(ast);

            // Create and return bytecode function
            String name = filename != null ? filename : "<script>";
            return new JSBytecodeFunction(bytecode, name, 0, ast.strict(), null);

        } catch (BytecodeCompiler.CompilerException e) {
            throw new JSCompilerException("Bytecode compiler error: " + e.getMessage(), e);
        } catch (JSErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new JSCompilerException("Unexpected compilation error: " + e.getMessage(), e);
        }
    }

    /**
     * Compile JavaScript source code into executable bytecode (with default filename).
     *
     * @param source The JavaScript source code to compile
     * @return A JSBytecodeFunction containing the compiled bytecode
     * @throws JSCompilerException if compilation fails
     */
    public static JSBytecodeFunction compile(String source) {
        return compile(source, null);
    }

    /**
     * Compile ES6 module source code into executable bytecode.
     * The resulting function will be executed in module scope.
     *
     * @param source   The module source code to compile
     * @param filename Optional filename for error reporting (can be null)
     * @return A JSBytecodeFunction containing the compiled module bytecode
     * @throws JSCompilerException if compilation fails
     */
    public static JSBytecodeFunction compileModule(String source, String filename) {
        if (source == null) {
            throw new JSCompilerException("Source code cannot be null");
        }

        try {
            // Stage 1 & 2: Lexical and Syntax Analysis (Source → Tokens → AST)
            Lexer lexer = new Lexer(source);
            Parser parser = new Parser(lexer, true);
            Program ast = parser.parse();

            // Stage 3: Code Generation (AST → Bytecode)
            BytecodeCompiler compiler = new BytecodeCompiler();
            compiler.setSourceCode(source);  // Store source for extracting function source
            Bytecode bytecode = compiler.compile(ast);

            // Create and return bytecode function
            // Note: Module code is always strict mode
            String name = filename != null ? filename : "<module>";
            return new JSBytecodeFunction(bytecode, name, 0, true, null);

        } catch (BytecodeCompiler.CompilerException e) {
            throw new JSCompilerException("Module compiler error: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new JSCompilerException("Module compilation error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new JSCompilerException("Unexpected module compilation error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JavaScript source code into an AST (without bytecode compilation).
     * Useful for static analysis, code transformation, etc.
     *
     * @param source   The JavaScript source code to parse
     * @param filename Optional filename for error reporting (can be null)
     * @return The parsed AST Program node
     * @throws JSCompilerException if parsing fails
     */
    public static Program parse(String source, String filename) {
        if (source == null) {
            throw new JSCompilerException("Source code cannot be null");
        }

        try {
            Lexer lexer = new Lexer(source);
            Parser parser = new Parser(lexer, false);
            return parser.parse();

        } catch (RuntimeException e) {
            throw new JSCompilerException("Parsing error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new JSCompilerException("Unexpected parsing error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JavaScript source code into an AST (with default filename).
     *
     * @param source The JavaScript source code to parse
     * @return The parsed AST Program node
     * @throws JSCompilerException if parsing fails
     */
    public static Program parse(String source) {
        return parse(source, null);
    }

    // Note: Tokenization is internal to the Lexer/Parser pipeline
    // and not exposed as a public API in this implementation.

}
