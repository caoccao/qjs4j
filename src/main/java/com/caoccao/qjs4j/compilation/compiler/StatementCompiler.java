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

    void compileStatement(Statement stmt) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            compilerContext.expressionCompiler.compileExpressionStatement(exprStmt.getExpression());
        } else if (stmt instanceof BlockStatement block) {
            compilerContext.blockStatementCompiler.compile(block);
        } else if (stmt instanceof IfStatement ifStmt) {
            compilerContext.ifStatementCompiler.compileIfStatement(ifStmt);
        } else if (stmt instanceof WhileStatement whileStmt) {
            compilerContext.whileStatementCompiler.compileWhileStatement(whileStmt);
        } else if (stmt instanceof DoWhileStatement doWhileStmt) {
            compilerContext.doWhileStatementCompiler.compileDoWhileStatement(doWhileStmt);
        } else if (stmt instanceof ForStatement forStmt) {
            compilerContext.forStatementCompiler.compileForStatement(forStmt);
        } else if (stmt instanceof ForInStatement forInStmt) {
            compilerContext.forInStatementCompiler.compileForInStatement(forInStmt);
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            compilerContext.forOfStatementCompiler.compileForOfStatement(forOfStmt);
        } else if (stmt instanceof ReturnStatement retStmt) {
            compilerContext.returnStatementCompiler.compileReturnStatement(retStmt);
        } else if (stmt instanceof BreakStatement breakStmt) {
            compilerContext.breakStatementCompiler.compile(breakStmt);
        } else if (stmt instanceof ContinueStatement contStmt) {
            compilerContext.continueStatementCompiler.compileContinueStatement(contStmt);
        } else if (stmt instanceof ThrowStatement throwStmt) {
            compilerContext.throwStatementCompiler.compileThrowStatement(throwStmt);
        } else if (stmt instanceof TryStatement tryStmt) {
            compilerContext.tryStatementCompiler.compileTryStatement(tryStmt);
        } else if (stmt instanceof SwitchStatement switchStmt) {
            compilerContext.switchStatementCompiler.compileSwitchStatement(switchStmt);
        } else if (stmt instanceof WithStatement withStmt) {
            compilerContext.withStatementCompiler.compileWithStatement(withStmt);
        } else if (stmt instanceof DebuggerStatement) {
            // No-op in runtime unless a debugger is attached.
        } else if (stmt instanceof VariableDeclaration varDecl) {
            compilerContext.variableDeclarationCompiler.compileVariableDeclaration(varDecl);
        } else if (stmt instanceof FunctionDeclaration funcDecl) {
            compilerContext.functionDeclarationCompiler.compileFunctionDeclaration(funcDecl);
        } else if (stmt instanceof ClassDeclaration classDecl) {
            compilerContext.classDeclarationCompiler.compileClassDeclaration(classDecl);
        } else if (stmt instanceof LabeledStatement labeledStmt) {
            compilerContext.labeledStatementCompiler.compileLabeledStatement(labeledStmt);
        }
    }

    public void emitEvalReturnUndefinedIfNeeded() {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }
    }

}
