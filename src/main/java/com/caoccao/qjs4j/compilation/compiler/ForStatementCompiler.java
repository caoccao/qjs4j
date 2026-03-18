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

final class ForStatementCompiler extends AstNodeCompiler<ForStatement> {
    ForStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ForStatement forStmt) {
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        boolean initCompiled = false;
        if (forStmt.getInit() instanceof VariableDeclaration varDecl && varDecl.getKind() == VariableKind.VAR) {
            compilerContext.variableDeclarationCompiler.compile(varDecl);
            initCompiled = true;
        }

        compilerContext.scopeManager.enterScope();

        if (!initCompiled && forStmt.getInit() != null) {
            if (forStmt.getInit() instanceof VariableDeclaration varDecl) {
                compilerContext.pushState();
                if (compilerContext.inGlobalScope && varDecl.getKind() != VariableKind.VAR) {
                    compilerContext.inGlobalScope = false;
                }
                compilerContext.variableDeclarationCompiler.compile(varDecl);
                compilerContext.popState();
            } else if (forStmt.getInit() instanceof Expression expr) {
                compilerContext.expressionCompiler.compile(expr);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else if (forStmt.getInit() instanceof ExpressionStatement exprStmt) {
                compilerContext.expressionCompiler.compile(exprStmt.getExpression());
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }

        if (forStmt.getInit() instanceof VariableDeclaration lexicalForInit
                && lexicalForInit.getKind() != VariableKind.VAR) {
            compilerContext.emitHelpers.emitCloseLocForPattern(lexicalForInit);
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart, compilerContext.scopeManager.getScopeDepth() - 1, compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        int jumpToEnd = -1;
        if (forStmt.getTest() != null) {
            compilerContext.expressionCompiler.compile(forStmt.getTest());
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        }
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        compilerContext.statementCompiler.compile(forStmt.getBody());

        if (forStmt.getInit() instanceof VariableDeclaration varDeclForClose
                && varDeclForClose.getKind() != VariableKind.VAR) {
            compilerContext.emitHelpers.emitCloseLocForPattern(varDeclForClose);
        }

        int updateStart = compilerContext.emitter.currentOffset();

        if (forStmt.getUpdate() != null) {
            compilerContext.expressionCompiler.compile(forStmt.getUpdate());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        compilerContext.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = compilerContext.emitter.currentOffset();

        if (jumpToEnd != -1) {
            compilerContext.emitter.patchJump(jumpToEnd, loopEnd);
        }

        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJump(breakPos, loopEnd);
        }
        for (int continuePos : loop.continuePositions) {
            compilerContext.emitter.patchJump(continuePos, updateStart);
        }

        compilerContext.loopManager.popLoop();
        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }
}
