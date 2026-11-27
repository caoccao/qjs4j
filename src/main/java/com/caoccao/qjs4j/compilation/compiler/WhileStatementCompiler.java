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

import com.caoccao.qjs4j.compilation.ast.WhileLoop;
import com.caoccao.qjs4j.compilation.ast.DoWhileStatement;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles while-statement AST nodes into bytecode.
 */
final class WhileStatementCompiler extends AstNodeCompiler<WhileLoop> {
    WhileStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(WhileLoop statement) {
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart,
                compilerContext.scopeManager.getScopeDepth(), compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        boolean testAfterBody = statement instanceof DoWhileStatement;
        if (testAfterBody) {
            compileBody(statement);
        }
        int testStart = compilerContext.emitter.currentOffset();
        compilerContext.expressionCompiler.compile(statement.getTest());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        if (!testAfterBody) {
            compileBody(statement);
        }

        compilerContext.emitter.emitGoto(loopStart);

        int loopEnd = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToEnd, loopEnd);

        compilerContext.emitter.patchJumps(loop.breakPositions, loopEnd);
        compilerContext.emitter.patchJumps(loop.continuePositions, testStart);

        compilerContext.loopManager.popLoop();
    }

    private void compileBody(WhileLoop statement) {
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();
        compilerContext.statementCompiler.compileNestedStatement(statement.getBody());
    }
}
