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

/**
 * Compiles statement AST nodes into bytecode.
 * Extracted from BytecodeCompiler to separate statement compilation concerns.
 */
final class StatementCompiler {
    private final CompilerContext compilerContext;

    StatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(Statement stmt) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            compileExpressionStatement(exprStmt.getExpression());
        } else if (stmt instanceof BlockStatement block) {
            compilerContext.blockStatementCompiler.compile(block);
        } else if (stmt instanceof IfStatement ifStmt) {
            compilerContext.ifStatementCompiler.compile(ifStmt);
        } else if (stmt instanceof WhileStatement whileStmt) {
            compilerContext.whileStatementCompiler.compile(whileStmt);
        } else if (stmt instanceof DoWhileStatement doWhileStmt) {
            compilerContext.doWhileStatementCompiler.compile(doWhileStmt);
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

    public void emitEvalReturnUndefinedIfNeeded() {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }
    }

}
