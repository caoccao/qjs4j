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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SwitchStatementCompiler extends AstNodeCompiler<SwitchStatement> {
    SwitchStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(SwitchStatement switchStmt) {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            // Default switch completion value is undefined.
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }

        // Compile discriminant
        compilerContext.expressionCompiler.compile(switchStmt.getDiscriminant());

        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;

        // Switch creates a lexical environment before evaluating case selectors.
        for (SwitchStatement.SwitchCase switchCase : switchStmt.getCases()) {
            for (Statement statement : switchCase.getConsequent()) {
                if (statement instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.getKind() != VariableKind.VAR) {
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        Set<String> declarationNames = new HashSet<>();
                        compilerContext.compilerAnalysis.collectPatternBindingNames(declarator.getId(), declarationNames);
                        for (String declarationName : declarationNames) {
                            Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(declarationName);
                            if (localIndex == null) {
                                localIndex = compilerContext.scopeManager.currentScope().declareLocal(declarationName);
                            }
                            if (variableDeclaration.getKind() == VariableKind.CONST
                                    || variableDeclaration.getKind() == VariableKind.USING
                                    || variableDeclaration.getKind() == VariableKind.AWAIT_USING) {
                                compilerContext.scopeManager.currentScope().markConstLocal(declarationName);
                            }
                            compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                            compilerContext.tdzLocals.add(declarationName);
                        }
                    }
                } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                    String className = classDeclaration.getId().getName();
                    Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(className);
                    if (localIndex == null) {
                        localIndex = compilerContext.scopeManager.currentScope().declareLocal(className);
                    }
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                    compilerContext.tdzLocals.add(className);
                } else {
                    FunctionDeclaration functionDeclaration = statement.unwrapLabeledFunctionDeclaration();
                    if (functionDeclaration != null && functionDeclaration.getId() != null) {
                        String functionName = functionDeclaration.getId().getName();
                        Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(functionName);
                        if (localIndex == null) {
                            localIndex = compilerContext.scopeManager.currentScope().declareLocal(functionName);
                        }
                        compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                        compilerContext.tdzLocals.add(functionName);
                    }
                }
            }
        }

        // BlockDeclarationInstantiation for switch case block: initialize function declarations.
        // Also handles labeled function declarations (e.g., "l: function f() {}").
        // Suppress Annex B var store during hoisting — it's deferred to the source position.
        compilerContext.suppressAnnexBVarStore = true;
        for (SwitchStatement.SwitchCase switchCase : switchStmt.getCases()) {
            for (Statement statement : switchCase.getConsequent()) {
                FunctionDeclaration functionDeclaration = statement.unwrapLabeledFunctionDeclaration();
                if (functionDeclaration != null) {
                    compilerContext.functionDeclarationCompiler.compile(functionDeclaration);
                }
            }
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
                compilerContext.expressionCompiler.compile(switchCase.getTest());
                compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);

                // If no match, skip to next test
                int jumpToNextTest = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                // Match: drop discriminant and jump to case body
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                int jumpToBody = compilerContext.emitter.emitJump(Opcode.GOTO);
                caseJumps.add(jumpToBody);
                // Patch IF_FALSE to continue with next test
                compilerContext.emitter.patchJump(jumpToNextTest, compilerContext.emitter.currentOffset());
            }
        }

        // No case matched: drop discriminant
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        // Jump to default or end
        int jumpToDefault = compilerContext.emitter.emitJump(Opcode.GOTO);

        LoopContext loop = compilerContext.loopManager.createLoopContext(compilerContext.emitter.currentOffset(), compilerContext.scopeManager.getScopeDepth(), compilerContext.scopeManager.getScopeDepth());
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

            boolean savedIsLastInProgram = compilerContext.isLastInProgram;
            compilerContext.isLastInProgram = false;
            for (Statement stmt : switchCase.getConsequent()) {
                FunctionDeclaration hoistedFunction = stmt.unwrapLabeledFunctionDeclaration();
                if (hoistedFunction != null) {
                    // Per Annex B.3.3: emit the var store at the source position
                    compilerContext.emitHelpers.emitDeferredAnnexBVarStore(hoistedFunction);
                    continue;
                }
                compilerContext.statementCompiler.compile(stmt);
            }
            compilerContext.isLastInProgram = savedIsLastInProgram;
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
        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJump(breakPos, switchEnd);
        }

        compilerContext.loopManager.popLoop();

        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
    }
}
