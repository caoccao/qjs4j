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

final class ForStatementCompiler {
    private ForStatementCompiler() {
    }

    static void compile(CompilerContext compilerContext, ForStatement forStmt) {
        StatementCompiler.emitEvalReturnUndefinedIfNeeded(compilerContext);

        boolean initCompiled = false;
        if (forStmt.getInit() instanceof VariableDeclaration varDecl && varDecl.getKind() == VariableKind.VAR) {
            VariableDeclarationCompiler.compile(compilerContext, varDecl);
            initCompiled = true;
        }

        compilerContext.scopeManager.enterScope();

        boolean hasUsingInit = !initCompiled && forStmt.getInit() instanceof VariableDeclaration usingCheck
                && usingCheck.getKind().isUsing();

        int forUsingCatchJump = EmitHelpers.beginUsingScope(compilerContext, hasUsingInit);

        if (!initCompiled && forStmt.getInit() != null) {
            if (forStmt.getInit() instanceof VariableDeclaration varDecl) {
                compilerContext.pushState();
                if (compilerContext.inGlobalScope && varDecl.getKind() != VariableKind.VAR) {
                    compilerContext.inGlobalScope = false;
                }
                VariableDeclarationCompiler.compile(compilerContext, varDecl);
                compilerContext.popState();
            } else if (forStmt.getInit() instanceof Expression expr) {
                compilerContext.compileExpression(expr);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else if (forStmt.getInit() instanceof ExpressionStatement exprStmt) {
                compilerContext.emitter.emitSourcePosition(exprStmt.getLocation());
                compilerContext.compileExpression(exprStmt.getExpression());
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }

        if (forStmt.getInit() instanceof VariableDeclaration lexicalForInit
                && lexicalForInit.getKind() != VariableKind.VAR) {
            EmitHelpers.emitCloseLocForPattern(compilerContext, lexicalForInit);
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart,
                compilerContext.scopeManager.getScopeDepth() - 1, compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        int jumpToEnd = -1;
        if (forStmt.getTest() != null) {
            compilerContext.compileExpression(forStmt.getTest());
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        }
        StatementCompiler.emitEvalReturnUndefinedIfNeeded(compilerContext);

        StatementCompiler.compileNestedStatement(compilerContext, forStmt.getBody());

        if (forStmt.getInit() instanceof VariableDeclaration varDeclForClose
                && varDeclForClose.getKind() != VariableKind.VAR) {
            EmitHelpers.emitCloseLocForPattern(compilerContext, varDeclForClose);
        }

        int updateStart = compilerContext.emitter.currentOffset();

        if (forStmt.getUpdate() != null) {
            compilerContext.compileExpression(forStmt.getUpdate());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        compilerContext.emitter.emitGoto(loopStart);

        int loopEnd = compilerContext.emitter.currentOffset();

        if (jumpToEnd != -1) {
            compilerContext.emitter.patchJump(jumpToEnd, loopEnd);
        }

        compilerContext.emitter.patchJumps(loop.continuePositions, updateStart);

        compilerContext.loopManager.popLoop();

        EmitHelpers.finishUsingScope(compilerContext, forUsingCatchJump, false);

        // Patch break positions AFTER disposal code, since break already handles
        // its own disposal via emitUsingDisposalsForScopeDepthGreaterThan
        int exitPoint = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJumps(loop.breakPositions, exitPoint);

        compilerContext.scopeManager.exitScope();
    }
}
