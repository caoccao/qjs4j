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

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compiles statement AST nodes into bytecode. Extracted from BytecodeCompiler to separate statement compilation
 * concerns.
 */
final class StatementCompiler {
    private StatementCompiler() {
    }

    static void compile(CompilerContext compilerContext, Statement stmt) {
        if (stmt == null) {
            // An absent statement, such as the hoisted-away body of an Annex B `else function f() {}`.
            return;
        }
        // The fallback position of everything the statement emits, for stack traces.
        compilerContext.emitter.emitSourcePosition(stmt.getLocation());
        if (stmt instanceof ExpressionStatement exprStmt) {
            compileExpressionStatement(compilerContext, exprStmt.getExpression());
        } else if (stmt instanceof BlockStatement block) {
            BlockStatementCompiler.compile(compilerContext, block);
        } else if (stmt instanceof IfStatement ifStmt) {
            IfStatementCompiler.compile(compilerContext, ifStmt);
        } else if (stmt instanceof WhileLoop loop) {
            WhileStatementCompiler.compile(compilerContext, loop);
        } else if (stmt instanceof ForStatement forStmt) {
            ForStatementCompiler.compile(compilerContext, forStmt);
        } else if (stmt instanceof ForEachStatement forEach) {
            ForEachStatementCompiler.compile(compilerContext, forEach);
        } else if (stmt instanceof ReturnStatement retStmt) {
            ReturnStatementCompiler.compile(compilerContext, retStmt);
        } else if (stmt instanceof JumpStatement jump) {
            JumpStatementCompiler.compile(compilerContext, jump);
        } else if (stmt instanceof ThrowStatement throwStmt) {
            ThrowStatementCompiler.compile(compilerContext, throwStmt);
        } else if (stmt instanceof TryStatement tryStmt) {
            TryStatementCompiler.compile(compilerContext, tryStmt);
        } else if (stmt instanceof SwitchStatement switchStmt) {
            SwitchStatementCompiler.compile(compilerContext, switchStmt);
        } else if (stmt instanceof WithStatement withStmt) {
            WithStatementCompiler.compile(compilerContext, withStmt);
        } else if (stmt instanceof DebuggerStatement) {
            // No-op in runtime unless a debugger is attached.
        } else if (stmt instanceof VariableDeclaration varDecl) {
            VariableDeclarationCompiler.compile(compilerContext, varDecl);
        } else if (stmt instanceof FunctionDeclaration funcDecl) {
            FunctionDeclarationCompiler.compile(compilerContext, funcDecl);
        } else if (stmt instanceof ClassDeclaration classDecl) {
            ClassDeclarationCompiler.compile(compilerContext, classDecl);
        } else if (stmt instanceof LabeledStatement labeledStmt) {
            LabeledStatementCompiler.compile(compilerContext, labeledStmt);
        }
    }

    static void compileBlockStatements(CompilerContext compilerContext, List<Statement> statements) {
        boolean savedIsLastInProgram = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        for (Statement stmt : statements) {
            FunctionDeclaration hoistedFunction = stmt.unwrapLabeledFunctionDeclaration();
            if (hoistedFunction != null) {
                // Per Annex B.3.3: emit the var store at the source position
                IdentifierCompiler.emitDeferredAnnexBVarStore(compilerContext, hoistedFunction);
                continue;
            }
            compilerContext.compileStatement(stmt);
        }
        compilerContext.isLastInProgram = savedIsLastInProgram;
    }

    private static void compileExpressionStatement(CompilerContext compilerContext, Expression expr) {
        BytecodeEmitter emitter = compilerContext.emitter;
        compilerContext.compileExpression(expr);
        if (compilerContext.evalReturnLocalIndex >= 0) {
            emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
        } else if (!compilerContext.isLastInProgram) {
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    static void compileNestedStatement(CompilerContext compilerContext, Statement statement) {
        boolean saved = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        try {
            compile(compilerContext, statement);
        } finally {
            compilerContext.isLastInProgram = saved;
        }
    }

    static void emitEvalReturnUndefinedIfNeeded(CompilerContext compilerContext) {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitUndefinedLocal(compilerContext.evalReturnLocalIndex);
        }
    }

    static void hoistBlockFunctions(CompilerContext compilerContext, List<Statement> statements) {
        for (Statement stmt : statements) {
            FunctionDeclaration funcDecl = stmt.unwrapLabeledFunctionDeclaration();
            if (funcDecl != null) {
                FunctionDeclarationCompiler.compile(compilerContext, funcDecl);
            }
        }
    }

    private static void initializeLexicalBinding(CompilerContext compilerContext, String name, boolean immutable) {
        var scope = compilerContext.scopeManager.currentScope();
        Integer index = scope.getLocal(name);
        if (index == null) {
            index = scope.declareLocal(name);
        }
        if (immutable) {
            scope.markConstLocal(name);
        }
        compilerContext.emitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, index);
        compilerContext.tdzLocals.add(name);
    }

    static void predeclareLexicalBindings(CompilerContext compilerContext, List<Statement> statements,
            boolean includeFunctions) {
        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration declaration && declaration.getKind() != VariableKind.VAR) {
                boolean immutable = declaration.getKind().isImmutable();
                for (VariableDeclarator declarator : declaration.getDeclarations()) {
                    if (declarator.getId() instanceof Identifier identifier) {
                        initializeLexicalBinding(compilerContext, identifier.getName(), immutable);
                        continue;
                    }
                    Set<String> names = new HashSet<>();
                    CompilerAnalysis.collectPatternBindingNames(declarator.getId(), names);
                    for (String name : names) {
                        initializeLexicalBinding(compilerContext, name, immutable);
                    }
                }
            } else if (statement instanceof ClassDeclaration declaration && declaration.getId() != null) {
                initializeLexicalBinding(compilerContext, declaration.getId().getName(), false);
            } else if (includeFunctions) {
                FunctionDeclaration function = statement.unwrapLabeledFunctionDeclaration();
                if (function != null && function.getId() != null) {
                    initializeLexicalBinding(compilerContext, function.getId().getName(), false);
                }
            }
        }
    }
}
