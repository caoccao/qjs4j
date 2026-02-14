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

import com.caoccao.qjs4j.compiler.ast.*;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.vm.Bytecode;

import java.util.HashSet;
import java.util.Set;

/**
 * Main compiler interface that integrates the entire compilation pipeline.
 * <p>
 * Pipeline: JavaScript Source → Lexer → Tokens → Parser → AST → BytecodeCompiler → Bytecode
 */
public final class Compiler {

    /**
     * Collect global declarations from a parsed program following ES2024 GlobalDeclarationInstantiation.
     * Collects var and lex (let/const) names declared at the top level.
     *
     * @param program  The parsed program AST
     * @param varDecls Output: var/function names declared by this program
     * @param lexDecls Output: let/const names declared by this program
     */
    public static void collectGlobalDeclarations(
            Program program,
            Set<String> varDecls,
            Set<String> lexDecls) {
        for (Statement stmt : program.body()) {
            if (stmt instanceof VariableDeclaration varDecl) {
                if (varDecl.kind() == VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                        collectPatternNames(d.id(), varDecls);
                    }
                } else {
                    // let or const
                    for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                        collectPatternNames(d.id(), lexDecls);
                    }
                }
            } else if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.id() != null) {
                    varDecls.add(funcDecl.id().name());
                }
            }
        }

        // Also collect Annex B function hoisting candidates (functions in blocks/if/switch)
        // since they create var bindings at the global level
        if (!program.strict()) {
            Set<String> topLevelLexicals = new HashSet<>(lexDecls);
            Set<String> annexBCandidates = new HashSet<>();
            for (Statement stmt : program.body()) {
                scanAnnexBForCollisionCheck(stmt, topLevelLexicals, annexBCandidates);
            }
            varDecls.addAll(annexBCandidates);
        }
    }

    private static void collectPatternNames(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier id) {
            names.add(id.name());
        } else if (pattern instanceof ArrayPattern arr) {
            for (Pattern element : arr.elements()) {
                if (element != null) {
                    collectPatternNames(element, names);
                }
            }
        } else if (pattern instanceof ObjectPattern obj) {
            for (ObjectPattern.Property prop : obj.properties()) {
                collectPatternNames(prop.value(), names);
            }
        } else if (pattern instanceof RestElement rest) {
            collectPatternNames(rest.argument(), names);
        }
    }

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
            Lexer lexer = new Lexer(source);
            Parser parser = new Parser(lexer, false);
            Program ast = parser.parse();

            BytecodeCompiler compiler = new BytecodeCompiler();
            compiler.setSourceCode(source);
            Bytecode bytecode = compiler.compile(ast);

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
     * Compile JavaScript source code, returning both the bytecode function and the parsed AST.
     * This overload allows the caller to perform GlobalDeclarationInstantiation checks
     * using the AST before execution.
     *
     * @param source   The JavaScript source code to compile
     * @param filename Optional filename for error reporting (can be null)
     * @return A CompileResult containing the bytecode function and parsed AST
     * @throws JSCompilerException if compilation fails
     */
    public static CompileResult compileWithAST(String source, String filename) {
        if (source == null) {
            throw new JSCompilerException("Source code cannot be null");
        }

        try {
            Lexer lexer = new Lexer(source);
            Parser parser = new Parser(lexer, false);
            Program ast = parser.parse();

            BytecodeCompiler compiler = new BytecodeCompiler();
            compiler.setSourceCode(source);
            Bytecode bytecode = compiler.compile(ast);

            String name = filename != null ? filename : "<script>";
            JSBytecodeFunction func = new JSBytecodeFunction(bytecode, name, 0, ast.strict(), null);
            return new CompileResult(func, ast);

        } catch (BytecodeCompiler.CompilerException e) {
            throw new JSCompilerException("Bytecode compiler error: " + e.getMessage(), e);
        } catch (JSErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new JSCompilerException("Unexpected compilation error: " + e.getMessage(), e);
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

    // Note: Tokenization is internal to the Lexer/Parser pipeline
    // and not exposed as a public API in this implementation.

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

    /**
     * Scan for Annex B function declaration candidates in compound statements.
     * These are function declarations inside blocks, if-statements, switch cases, etc.
     * that would create var bindings via Annex B.3.3 hoisting.
     */
    private static void scanAnnexBForCollisionCheck(
            Statement stmt, Set<String> lexicalBindings, Set<String> result) {
        if (stmt instanceof BlockStatement block) {
            for (Statement s : block.body()) {
                if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                    if (!lexicalBindings.contains(fd.id().name())) {
                        result.add(fd.id().name());
                    }
                }
                scanAnnexBForCollisionCheck(s, lexicalBindings, result);
            }
        } else if (stmt instanceof IfStatement ifStmt) {
            if (ifStmt.consequent() instanceof FunctionDeclaration fd && fd.id() != null) {
                if (!lexicalBindings.contains(fd.id().name())) {
                    result.add(fd.id().name());
                }
            } else {
                scanAnnexBForCollisionCheck(ifStmt.consequent(), lexicalBindings, result);
            }
            if (ifStmt.alternate() != null) {
                if (ifStmt.alternate() instanceof FunctionDeclaration fd && fd.id() != null) {
                    if (!lexicalBindings.contains(fd.id().name())) {
                        result.add(fd.id().name());
                    }
                } else {
                    scanAnnexBForCollisionCheck(ifStmt.alternate(), lexicalBindings, result);
                }
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                for (Statement s : sc.consequent()) {
                    if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                        if (!lexicalBindings.contains(fd.id().name())) {
                            result.add(fd.id().name());
                        }
                    }
                    scanAnnexBForCollisionCheck(s, lexicalBindings, result);
                }
            }
        }
    }

    public record CompileResult(JSBytecodeFunction function, Program ast) {
    }

}
