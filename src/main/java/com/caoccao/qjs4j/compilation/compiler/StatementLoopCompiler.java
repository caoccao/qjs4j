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
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.Iterator;
import java.util.List;

final class StatementLoopCompiler {
    private final CompilerContext compilerContext;

    StatementLoopCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileBreakStatement(BreakStatement breakStmt) {
        if (compilerContext.finallySubroutineDepth > 0) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
        if (breakStmt.getLabel() != null) {
            String labelName = breakStmt.getLabel().getName();
            LoopContext target = null;
            for (LoopContext loopCtx : compilerContext.loopManager) {
                if (labelName.equals(loopCtx.label)) {
                    target = loopCtx;
                    break;
                }
            }
            if (target == null) {
                throw new JSCompilerException("Undefined label '" + labelName + "'");
            }
            compilerContext.emitHelpers.emitIteratorCloseForLoopsUntil(target);
            compilerContext.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(target.breakTargetScopeDepth);
            emitActiveFinallyGosubs();
            int jumpPos = compilerContext.emitter.emitJump(Opcode.GOTO);
            target.breakPositions.add(jumpPos);
        } else {
            if (compilerContext.loopManager.isEmpty()) {
                throw new JSCompilerException("Break statement outside of loop");
            }
            LoopContext loopContext = null;
            for (LoopContext loopCtx : compilerContext.loopManager) {
                if (!loopCtx.isRegularStmt) {
                    loopContext = loopCtx;
                    break;
                }
            }
            if (loopContext == null) {
                throw new JSCompilerException("Break statement outside of loop");
            }
            compilerContext.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(loopContext.breakTargetScopeDepth);
            emitActiveFinallyGosubs();
            int jumpPos = compilerContext.emitter.emitJump(Opcode.GOTO);
            loopContext.breakPositions.add(jumpPos);
        }
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

    void compileDoWhileStatement(DoWhileStatement doWhileStmt) {
        emitEvalReturnUndefinedIfNeeded();

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart, compilerContext.scopeManager.getScopeDepth(), compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        emitEvalReturnUndefinedIfNeeded();
        compilerContext.statementCompiler.compileStatement(doWhileStmt.getBody());

        int testStart = compilerContext.emitter.currentOffset();
        compilerContext.expressionCompiler.compileExpression(doWhileStmt.getTest());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToEnd, loopEnd);

        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJump(breakPos, loopEnd);
        }
        for (int continuePos : loop.continuePositions) {
            compilerContext.emitter.patchJump(continuePos, testStart);
        }

        compilerContext.loopManager.popLoop();
    }

    void compileForInStatement(ForInStatement forInStmt) {
        emitEvalReturnUndefinedIfNeeded();

        boolean isExpressionBased = forInStmt.getLeft() instanceof Expression;
        VariableDeclaration varDecl = null;
        Pattern declarationPattern = null;
        boolean isVar = false;

        if (!isExpressionBased) {
            varDecl = (VariableDeclaration) forInStmt.getLeft();
            if (varDecl.getDeclarations().size() != 1) {
                throw new JSCompilerException("for-in loop must have exactly one variable");
            }
            declarationPattern = varDecl.getDeclarations().get(0).getId();
            isVar = varDecl.getKind() == VariableKind.VAR;
            if (isVar && !compilerContext.inGlobalScope) {
                compilerContext.patternCompiler.declarePatternVariables(declarationPattern);
            }
        }

        boolean hasHeadTdzScope = !isExpressionBased && !isVar;
        if (hasHeadTdzScope && declarationPattern != null) {
            compilerContext.scopeManager.enterScope();
            compilerContext.patternCompiler.declarePatternVariables(declarationPattern);
            if (declarationPattern != null) {
                for (String boundName : declarationPattern.getBoundNames()) {
                    Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(boundName);
                    if (localIndex != null) {
                        compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                        compilerContext.tdzLocals.add(boundName);
                    }
                }
            }
            compilerContext.expressionCompiler.compileExpression(forInStmt.getRight());
            compilerContext.emitter.emitOpcode(Opcode.FOR_IN_START);
            compilerContext.scopeManager.exitScope();
        }

        compilerContext.scopeManager.enterScope();

        if (!isExpressionBased && !isVar && declarationPattern != null) {
            compilerContext.patternCompiler.declarePatternVariables(declarationPattern);
            if (varDecl != null && varDecl.getKind() == VariableKind.CONST) {
                compilerContext.patternCompiler.markPatternConstBindings(declarationPattern);
            }
        }

        if (!isExpressionBased) {
            Expression initializer = varDecl != null ? varDecl.getDeclarations().get(0).getInit() : null;
            if (initializer != null && declarationPattern != null) {
                if (declarationPattern instanceof Identifier identifier
                        && initializer instanceof ClassExpression classExpression
                        && classExpression.getId() == null) {
                    compilerContext.inferredClassName = identifier.getName();
                }
                compilerContext.expressionCompiler.compileExpression(initializer);
                if (declarationPattern instanceof Identifier identifier
                        && initializer.isAnonymousFunction()) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, identifier.getName());
                }
                compilerContext.inferredClassName = null;
                compilerContext.patternCompiler.compileForOfValueAssignment(declarationPattern, true);
            }
        }

        if (!hasHeadTdzScope) {
            compilerContext.expressionCompiler.compileExpression(forInStmt.getRight());
            compilerContext.emitter.emitOpcode(Opcode.FOR_IN_START);
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart, compilerContext.scopeManager.getScopeDepth() - 1, compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        compilerContext.emitter.emitOpcode(Opcode.FOR_IN_NEXT);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        emitEvalReturnUndefinedIfNeeded();

        if (isExpressionBased) {
            compileForOfExpressionTargetAssignment((Expression) forInStmt.getLeft());
        } else if (declarationPattern != null) {
            compilerContext.patternCompiler.compileForOfValueAssignment(declarationPattern, isVar);
        }

        compilerContext.statementCompiler.compileStatement(forInStmt.getBody());

        if (!isExpressionBased && !isVar && declarationPattern != null) {
            compilerContext.emitHelpers.emitCloseLocForPattern(declarationPattern);
        }

        compilerContext.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitU32(loopStart - (backJumpPos + 4));

        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJump(breakPos, compilerContext.emitter.currentOffset());
        }
        for (int continuePos : loop.continuePositions) {
            compilerContext.emitter.patchJump(continuePos, loopStart);
        }

        compilerContext.emitter.emitOpcode(Opcode.DROP);

        compilerContext.loopManager.popLoop();
        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }

    private void compileForOfExpressionTargetAssignment(Expression leftExpression) {
        if (leftExpression instanceof CallExpression) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.expressionCompiler.compileExpression(leftExpression);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            compilerContext.emitter.emitU8(5);
            return;
        }
        compilerContext.patternCompiler.compileAssignmentTarget(leftExpression);
    }

    void compileForOfStatement(ForOfStatement forOfStmt) {
        emitEvalReturnUndefinedIfNeeded();

        boolean isExpressionBased = !(forOfStmt.getLeft() instanceof VariableDeclaration);
        VariableDeclaration varDecl = null;
        Pattern pattern = null;
        boolean isVar = false;

        if (isExpressionBased) {
            if (forOfStmt.getLeft() instanceof CallExpression callExpr) {
                compilerContext.patternCompiler.compileForOfWithCallExpressionTarget(forOfStmt, callExpr);
                return;
            }
        } else {
            varDecl = (VariableDeclaration) forOfStmt.getLeft();
            if (varDecl.getDeclarations().size() != 1) {
                throw new JSCompilerException("for-of loop must have exactly one variable");
            }
            pattern = varDecl.getDeclarations().get(0).getId();
            isVar = varDecl.getKind() == VariableKind.VAR;

            if (isVar && !compilerContext.inGlobalScope) {
                compilerContext.patternCompiler.declarePatternVariables(pattern);
            }
        }

        boolean hasHeadTdzScope = !isExpressionBased && !isVar;
        if (hasHeadTdzScope && pattern != null) {
            compilerContext.scopeManager.enterScope();
            compilerContext.patternCompiler.declarePatternVariables(pattern);
            for (String boundName : pattern.getBoundNames()) {
                Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(boundName);
                if (localIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                    compilerContext.tdzLocals.add(boundName);
                }
            }
            compilerContext.expressionCompiler.compileExpression(forOfStmt.getRight());
            if (forOfStmt.isAsync()) {
                compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
            }
            compilerContext.scopeManager.exitScope();
        }

        compilerContext.scopeManager.enterScope();

        if (!isExpressionBased && !isVar) {
            compilerContext.patternCompiler.declarePatternVariables(pattern);
            if (varDecl != null && (varDecl.getKind() == VariableKind.CONST
                    || varDecl.getKind() == VariableKind.USING
                    || varDecl.getKind() == VariableKind.AWAIT_USING)) {
                compilerContext.patternCompiler.markPatternConstBindings(pattern);
            }
        }
        if (!hasHeadTdzScope) {
            compilerContext.expressionCompiler.compileExpression(forOfStmt.getRight());
            if (forOfStmt.isAsync()) {
                compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
            }
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart, compilerContext.scopeManager.getScopeDepth() - 1, compilerContext.scopeManager.getScopeDepth());
        loop.hasIterator = true;
        compilerContext.loopManager.pushLoop(loop);

        int jumpToEnd;

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "done");
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
            emitEvalReturnUndefinedIfNeeded();
            if (isExpressionBased) {
                compileForOfExpressionTargetAssignment((Expression) forOfStmt.getLeft());
            } else {
                compilerContext.patternCompiler.compileForOfValueAssignment(pattern, isVar);
            }
        } else {
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            emitEvalReturnUndefinedIfNeeded();
            if (isExpressionBased) {
                compileForOfExpressionTargetAssignment((Expression) forOfStmt.getLeft());
            } else {
                compilerContext.patternCompiler.compileForOfValueAssignment(pattern, isVar);
            }
        }

        compilerContext.statementCompiler.compileStatement(forOfStmt.getBody());

        if (!isExpressionBased && !isVar) {
            compilerContext.emitHelpers.emitCloseLocForPattern(pattern);
        }

        compilerContext.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToEnd, loopEnd);

        compilerContext.emitter.emitOpcode(Opcode.DROP);

        int breakTarget = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);

        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJump(breakPos, breakTarget);
        }
        for (int continuePos : loop.continuePositions) {
            compilerContext.emitter.patchJump(continuePos, loopStart);
        }

        compilerContext.loopManager.popLoop();

        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }

    void compileForStatement(ForStatement forStmt) {
        emitEvalReturnUndefinedIfNeeded();

        boolean initCompiled = false;
        if (forStmt.getInit() instanceof VariableDeclaration varDecl && varDecl.getKind() == VariableKind.VAR) {
            compilerContext.statementCompiler.compileVariableDeclaration(varDecl);
            initCompiled = true;
        }

        compilerContext.scopeManager.enterScope();

        if (!initCompiled && forStmt.getInit() != null) {
            if (forStmt.getInit() instanceof VariableDeclaration varDecl) {
                compilerContext.pushState();
                if (compilerContext.inGlobalScope && varDecl.getKind() != VariableKind.VAR) {
                    compilerContext.inGlobalScope = false;
                }
                compilerContext.statementCompiler.compileVariableDeclaration(varDecl);
                compilerContext.popState();
            } else if (forStmt.getInit() instanceof Expression expr) {
                compilerContext.expressionCompiler.compileExpression(expr);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else if (forStmt.getInit() instanceof ExpressionStatement exprStmt) {
                compilerContext.expressionCompiler.compileExpression(exprStmt.getExpression());
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
            compilerContext.expressionCompiler.compileExpression(forStmt.getTest());
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        }
        emitEvalReturnUndefinedIfNeeded();

        compilerContext.statementCompiler.compileStatement(forStmt.getBody());

        if (forStmt.getInit() instanceof VariableDeclaration varDeclForClose
                && varDeclForClose.getKind() != VariableKind.VAR) {
            compilerContext.emitHelpers.emitCloseLocForPattern(varDeclForClose);
        }

        int updateStart = compilerContext.emitter.currentOffset();

        if (forStmt.getUpdate() != null) {
            compilerContext.expressionCompiler.compileExpression(forStmt.getUpdate());
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

    void compileWhileStatement(WhileStatement whileStmt) {
        emitEvalReturnUndefinedIfNeeded();

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart, compilerContext.scopeManager.getScopeDepth(), compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        compilerContext.expressionCompiler.compileExpression(whileStmt.getTest());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        emitEvalReturnUndefinedIfNeeded();

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

    private void emitEvalReturnUndefinedIfNeeded() {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }
    }
}
