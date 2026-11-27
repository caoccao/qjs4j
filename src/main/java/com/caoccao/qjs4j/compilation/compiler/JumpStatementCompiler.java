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

import com.caoccao.qjs4j.compilation.ast.ContinueStatement;
import com.caoccao.qjs4j.compilation.ast.JumpStatement;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

final class JumpStatementCompiler extends AstNodeCompiler<JumpStatement> {
    JumpStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(JumpStatement statement) {
        if (compilerContext.finallySubroutineDepth > 0) {
            emitter.emitDrops(2);
        }
        boolean continuing = statement instanceof ContinueStatement;
        String label = statement.getLabel() == null ? null : statement.getLabel().getName();
        LoopContext target = findTarget(statement, label, continuing);
        if (label != null) {
            compilerContext.emitHelpers.emitIteratorCloseForLoopsUntil(target);
        }
        int scopeDepth = continuing ? target.continueTargetScopeDepth : target.breakTargetScopeDepth;
        compilerContext.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(scopeDepth);
        compilerContext.emitHelpers.emitActiveFinallyGosubs();
        int jump = emitter.emitJump(Opcode.GOTO);
        if (continuing) {
            target.continuePositions.add(jump);
        } else {
            target.breakPositions.add(jump);
        }
    }

    private LoopContext findTarget(JumpStatement statement, String label, boolean continuing) {
        boolean labelExists = false;
        for (LoopContext loop : compilerContext.loopManager) {
            if (label != null && !label.equals(loop.label)) {
                continue;
            }
            labelExists = true;
            if (continuing ? !loop.isRegularStmt && !loop.isSwitchStatement : label != null || !loop.isRegularStmt) {
                return loop;
            }
        }
        if (label == null) {
            throw new JSCompilerException(continuing
                    ? "Illegal continue statement: no surrounding iteration statement"
                    : "Illegal break statement", statement);
        }
        if (labelExists) {
            throw new JSCompilerException(
                    "Illegal continue statement: '" + label + "' does not denote an iteration statement", statement);
        }
        throw new JSCompilerException("Undefined label '" + label + "'", statement);
    }
}
