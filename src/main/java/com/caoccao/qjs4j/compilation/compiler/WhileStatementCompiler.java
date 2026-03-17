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

import com.caoccao.qjs4j.compilation.ast.WhileStatement;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles while-statement AST nodes into bytecode.
 */
final class WhileStatementCompiler {
    private final CompilerContext compilerContext;

    WhileStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileWhileStatement(WhileStatement whileStmt) {
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart, compilerContext.scopeManager.getScopeDepth(), compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        compilerContext.expressionCompiler.compileExpression(whileStmt.getTest());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        compilerContext.statementCompiler.compileStatement(whileStmt.getBody());

        compilerContext.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToEnd, loopEnd);

        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJump(breakPos, loopEnd);
        }
        for (int continuePos : loop.continuePositions) {
            compilerContext.emitter.patchJump(continuePos, loopStart);
        }

        compilerContext.loopManager.popLoop();
    }
}
