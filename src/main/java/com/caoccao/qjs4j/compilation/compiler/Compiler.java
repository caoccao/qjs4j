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

import com.caoccao.qjs4j.compilation.ast.Program;
import com.caoccao.qjs4j.compilation.lexer.Lexer;
import com.caoccao.qjs4j.compilation.parser.Parser;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.vm.Bytecode;

import java.util.Map;
import java.util.Set;

/**
 * Main compiler interface that integrates the entire compilation pipeline.
 * <p>
 * Pipeline: JavaScript Source → Lexer → Tokens → Parser → AST → BytecodeCompiler → Bytecode
 */
public final class Compiler {
    private final String fileName;
    private final String source;
    private boolean classFieldEval;
    private JSContext context;
    private boolean evalAllowNewTarget;
    private boolean evalAllowSuperProperty;
    private Map<String, JSSymbol> evalPrivateSymbols;
    private boolean inheritedStrictMode;
    private boolean isEval; // true if compiling eval code
    private boolean predeclareProgramLexicalsAsLocals;

    public Compiler(String source, String fileName) {
        if (source == null) {
            throw new JSCompilerException("Source code cannot be null");
        }
        this.source = source;
        this.fileName = fileName;
        this.evalAllowNewTarget = false;
        this.evalAllowSuperProperty = false;
        this.inheritedStrictMode = false;
        this.isEval = false;
        this.evalPrivateSymbols = Map.of();
        this.predeclareProgramLexicalsAsLocals = false;
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
            compiler.setContext(context);
            compiler.setSourceCode(source);
            compiler.setPredeclareProgramLexicalsAsLocals(predeclareProgramLexicalsAsLocals);
            if (isEval) {
                compiler.setEvalMode(true);
            }
            if (!evalPrivateSymbols.isEmpty()) {
                compiler.setPrivateSymbols(evalPrivateSymbols);
            }
            if (classFieldEval) {
                compiler.setClassFieldEvalContext(true);
            }
            Bytecode bytecode = compiler.compile(ast);
            String name = fileName != null ? fileName : (isModule ? "<module>" : "<script>");
            boolean strict = isModule || ast.isStrict();
            JSBytecodeFunction func = new JSBytecodeFunction(
                    context,
                    bytecode,
                    name,
                    0,
                    JSValue.NO_ARGS,
                    null,
                    true,
                    false,
                    false,
                    false,
                    strict,
                    null);
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
        Parser parser = new Parser(
                lexer,
                isModule,
                isEval,
                inheritedStrictMode,
                evalAllowSuperProperty,
                evalAllowNewTarget,
                evalPrivateSymbols.isEmpty() ? Set.of() : evalPrivateSymbols.keySet());
        if (classFieldEval) {
            parser.setClassFieldEval(true);
        }
        return parser.parse();
    }

    public Compiler setClassFieldEval(boolean classFieldEval) {
        this.classFieldEval = classFieldEval;
        return this;
    }

    public Compiler setContext(JSContext context) {
        this.context = context;
        return this;
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

    public Compiler setEvalContextFlags(boolean allowSuperProperty, boolean allowNewTarget) {
        this.evalAllowSuperProperty = allowSuperProperty;
        this.evalAllowNewTarget = allowNewTarget;
        return this;
    }

    public Compiler setEvalPrivateSymbols(Map<String, JSSymbol> evalPrivateSymbols) {
        this.evalPrivateSymbols = evalPrivateSymbols != null ? evalPrivateSymbols : Map.of();
        return this;
    }

    public Compiler setInheritedStrictMode(boolean inheritedStrictMode) {
        this.inheritedStrictMode = inheritedStrictMode;
        return this;
    }

    public Compiler setPredeclareProgramLexicalsAsLocals(boolean predeclareProgramLexicalsAsLocals) {
        this.predeclareProgramLexicalsAsLocals = predeclareProgramLexicalsAsLocals;
        return this;
    }

    public record CompileResult(JSBytecodeFunction function, Program ast) {
    }
}
