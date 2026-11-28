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

import java.util.ArrayList;
import java.util.List;

final class SwitchStatementCompiler {
    private SwitchStatementCompiler() {
    }

    static void compile(CompilerContext compilerContext, SwitchStatement switchStmt) {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            // Default switch completion value is undefined.
            compilerContext.emitter.emitUndefinedLocal(compilerContext.evalReturnLocalIndex);
        }

        // Compile discriminant
        compilerContext.compileExpression(switchStmt.getDiscriminant());

        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;

        // Switch creates a lexical environment before evaluating case selectors.
        for (SwitchStatement.SwitchCase switchCase : switchStmt.getCases()) {
            StatementCompiler.predeclareLexicalBindings(compilerContext, switchCase.getConsequent(), true);
        }

        // BlockDeclarationInstantiation for switch case block: initialize function declarations.
        // Also handles labeled function declarations (e.g., "l: function f() {}").
        // Suppress Annex B var store during hoisting — it's deferred to the source position.
        compilerContext.suppressAnnexBVarStore = true;
        for (SwitchStatement.SwitchCase switchCase : switchStmt.getCases()) {
            StatementCompiler.hoistBlockFunctions(compilerContext, switchCase.getConsequent());
        }
        compilerContext.suppressAnnexBVarStore = false;

        List<Integer> caseJumps = new ArrayList<>();
        List<Integer> caseBodyStarts = new ArrayList<>();

        // Emit comparisons for each case.
        // Following QuickJS pattern: when a case matches, drop the discriminant
        // before jumping to the case body. This ensures the stack depth is
        // consistent regardless of which case (or no case) matches.
        for (SwitchStatement.SwitchCase switchCase : switchStmt.getCases()) {
            if (switchCase.getTest() != null) {
                // Duplicate discriminant for comparison
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.compileExpression(switchCase.getTest());
                compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);

                // If no match, skip to next test
                int jumpToNextTest = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                // Match: drop discriminant and jump to case body
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                int jumpToBody = compilerContext.emitter.emitJump(Opcode.GOTO);
                caseJumps.add(jumpToBody);
                // Patch IF_FALSE to continue with next test
                compilerContext.emitter.patchJumpToHere(jumpToNextTest);
            }
        }

        // No case matched: drop discriminant
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        // Jump to default or end
        int jumpToDefault = compilerContext.emitter.emitJump(Opcode.GOTO);

        LoopContext loop = compilerContext.loopManager.createLoopContext(compilerContext.emitter.currentOffset(),
                compilerContext.scopeManager.getScopeDepth(), compilerContext.scopeManager.getScopeDepth());
        loop.isSwitchStatement = true;
        compilerContext.loopManager.pushLoop(loop);

        int defaultBodyStart = -1;
        for (int i = 0; i < switchStmt.getCases().size(); i++) {
            SwitchStatement.SwitchCase switchCase = switchStmt.getCases().get(i);

            if (switchCase.getTest() != null) {
                int bodyStart = compilerContext.emitter.currentOffset();
                caseBodyStarts.add(bodyStart);
            } else {
                defaultBodyStart = compilerContext.emitter.currentOffset();
            }

            StatementCompiler.compileBlockStatements(compilerContext, switchCase.getConsequent());
        }

        int switchEnd = compilerContext.emitter.currentOffset();

        // Patch case jumps
        for (int i = 0; i < caseJumps.size(); i++) {
            compilerContext.emitter.patchJump(caseJumps.get(i), caseBodyStarts.get(i));
        }

        // Patch default jump
        if (defaultBodyStart >= 0) {
            compilerContext.emitter.patchJump(jumpToDefault, defaultBodyStart);
        } else {
            compilerContext.emitter.patchJump(jumpToDefault, switchEnd);
        }

        // Patch break statements
        compilerContext.emitter.patchJumps(loop.breakPositions, switchEnd);

        compilerContext.loopManager.popLoop();

        EmitHelpers.emitCurrentScopeUsingDisposal(compilerContext);
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
    }
}
