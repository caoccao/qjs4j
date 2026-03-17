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
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.Iterator;
import java.util.List;

final class ContinueStatementCompiler {
    private final CompilerContext compilerContext;

    ContinueStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileContinueStatement(ContinueStatement contStmt) {
        if (compilerContext.finallySubroutineDepth > 0) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
        if (contStmt.getLabel() != null) {
            String labelName = contStmt.getLabel().getName();
            LoopContext target = null;
            for (LoopContext loopCtx : compilerContext.loopManager) {
                if (labelName.equals(loopCtx.label) && !loopCtx.isRegularStmt && !loopCtx.isSwitchStatement) {
                    target = loopCtx;
                    break;
                }
            }
            if (target == null) {
                throw new JSCompilerException("Undefined label '" + labelName + "'");
            }
            compilerContext.emitHelpers.emitIteratorCloseForLoopsUntil(target);
            compilerContext.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(target.continueTargetScopeDepth);
            emitActiveFinallyGosubs();
            int jumpPos = compilerContext.emitter.emitJump(Opcode.GOTO);
            target.continuePositions.add(jumpPos);
        } else {
            LoopContext loopContext = null;
            for (LoopContext loopCtx : compilerContext.loopManager) {
                if (!loopCtx.isRegularStmt && !loopCtx.isSwitchStatement) {
                    loopContext = loopCtx;
                    break;
                }
            }
            if (loopContext == null) {
                throw new JSCompilerException("Continue statement outside of loop");
            }
            compilerContext.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(loopContext.continueTargetScopeDepth);
            emitActiveFinallyGosubs();
            int jumpPos = compilerContext.emitter.emitJump(Opcode.GOTO);
            loopContext.continuePositions.add(jumpPos);
        }
    }

    private void emitActiveFinallyGosubs() {
        Iterator<List<Integer>> gosubPatchIterator = compilerContext.activeFinallyGosubPatches.iterator();
        Iterator<Integer> nipCatchCountIterator = compilerContext.activeFinallyNipCatchCounts.iterator();
        while (gosubPatchIterator.hasNext() && nipCatchCountIterator.hasNext()) {
            List<Integer> gosubPatches = gosubPatchIterator.next();
            int nipCatchCount = nipCatchCountIterator.next();
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            for (int i = 0; i < nipCatchCount; i++) {
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            }
            int gosubPosition = compilerContext.emitter.emitJump(Opcode.GOSUB);
            gosubPatches.add(gosubPosition);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }
}
