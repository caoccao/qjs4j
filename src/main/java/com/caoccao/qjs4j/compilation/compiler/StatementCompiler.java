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
final class StatementCompiler extends AstNodeCompiler<Statement> {
    StatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(Statement stmt) {
        if (stmt == null) {
            // An absent statement, such as the hoisted-away body of an Annex B `else function f() {}`.
            return;
        }
        // The fallback position of everything the statement emits, for stack traces.
        compilerContext.emitter.emitSourcePosition(stmt.getLocation());
        if (stmt instanceof ExpressionStatement exprStmt) {
            compileExpressionStatement(exprStmt.getExpression());
        } else if (stmt instanceof BlockStatement block) {
            compilerContext.blockStatementCompiler.compile(block);
        } else if (stmt instanceof IfStatement ifStmt) {
            compilerContext.ifStatementCompiler.compile(ifStmt);
        } else if (stmt instanceof WhileLoop loop) {
            compilerContext.whileStatementCompiler.compile(loop);
        } else if (stmt instanceof ForStatement forStmt) {
            compilerContext.forStatementCompiler.compile(forStmt);
        } else if (stmt instanceof ForInStatement forInStmt) {
            compilerContext.forInStatementCompiler.compile(forInStmt);
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            compilerContext.forOfStatementCompiler.compile(forOfStmt);
        } else if (stmt instanceof ReturnStatement retStmt) {
            compilerContext.returnStatementCompiler.compile(retStmt);
        } else if (stmt instanceof BreakStatement breakStmt) {
            compilerContext.breakStatementCompiler.compile(breakStmt);
        } else if (stmt instanceof ContinueStatement contStmt) {
            compilerContext.continueStatementCompiler.compile(contStmt);
        } else if (stmt instanceof ThrowStatement throwStmt) {
            compilerContext.throwStatementCompiler.compile(throwStmt);
        } else if (stmt instanceof TryStatement tryStmt) {
            compilerContext.tryStatementCompiler.compile(tryStmt);
        } else if (stmt instanceof SwitchStatement switchStmt) {
            compilerContext.switchStatementCompiler.compile(switchStmt);
        } else if (stmt instanceof WithStatement withStmt) {
            compilerContext.withStatementCompiler.compile(withStmt);
        } else if (stmt instanceof DebuggerStatement) {
            // No-op in runtime unless a debugger is attached.
        } else if (stmt instanceof VariableDeclaration varDecl) {
            compilerContext.variableDeclarationCompiler.compile(varDecl);
        } else if (stmt instanceof FunctionDeclaration funcDecl) {
            compilerContext.functionDeclarationCompiler.compile(funcDecl);
        } else if (stmt instanceof ClassDeclaration classDecl) {
            compilerContext.classDeclarationCompiler.compile(classDecl);
        } else if (stmt instanceof LabeledStatement labeledStmt) {
            compilerContext.labeledStatementCompiler.compile(labeledStmt);
        }
    }

    private void compileExpressionStatement(Expression expr) {
        compilerContext.expressionCompiler.compile(expr);
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        } else if (!compilerContext.isLastInProgram) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }

    void compileNestedStatement(Statement statement) {
        boolean saved = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        try {
            compile(statement);
        } finally {
            compilerContext.isLastInProgram = saved;
        }
    }

    public void emitEvalReturnUndefinedIfNeeded() {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }
    }
    private void initializeLexicalBinding(String name, boolean immutable) {
        var scope = compilerContext.scopeManager.currentScope();
        Integer index = scope.getLocal(name);
        if (index == null) {
            index = scope.declareLocal(name);
        }
        if (immutable) {
            scope.markConstLocal(name);
        }
        compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, index);
        compilerContext.tdzLocals.add(name);
    }

    void predeclareLexicalBindings(List<Statement> statements, boolean includeFunctions) {
        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration declaration && declaration.getKind() != VariableKind.VAR) {
                boolean immutable = declaration.getKind() == VariableKind.CONST
                        || declaration.getKind() == VariableKind.USING
                        || declaration.getKind() == VariableKind.AWAIT_USING;
                for (VariableDeclarator declarator : declaration.getDeclarations()) {
                    if (declarator.getId() instanceof Identifier identifier) {
                        initializeLexicalBinding(identifier.getName(), immutable);
                        continue;
                    }
                    Set<String> names = new HashSet<>();
                    compilerContext.compilerAnalysis.collectPatternBindingNames(declarator.getId(), names);
                    for (String name : names) {
                        initializeLexicalBinding(name, immutable);
                    }
                }
            } else if (statement instanceof ClassDeclaration declaration && declaration.getId() != null) {
                initializeLexicalBinding(declaration.getId().getName(), false);
            } else if (includeFunctions) {
                FunctionDeclaration function = statement.unwrapLabeledFunctionDeclaration();
                if (function != null && function.getId() != null) {
                    initializeLexicalBinding(function.getId().getName(), false);
                }
            }
        }
    }
}
