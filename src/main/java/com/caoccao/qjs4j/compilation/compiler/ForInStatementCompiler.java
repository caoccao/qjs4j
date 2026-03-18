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

final class ForInStatementCompiler {
    private final CompilerContext compilerContext;

    ForInStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(ForInStatement forInStmt) {
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

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
            for (String boundName : declarationPattern.getBoundNames()) {
                Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(boundName);
                if (localIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                    compilerContext.tdzLocals.add(boundName);
                }
            }
            compilerContext.expressionCompiler.compile(forInStmt.getRight());
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
                compilerContext.expressionCompiler.compile(initializer);
                if (declarationPattern instanceof Identifier identifier
                        && initializer.isAnonymousFunction()) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, identifier.getName());
                }
                compilerContext.inferredClassName = null;
                compilerContext.patternCompiler.compileForOfValueAssignment(declarationPattern, true);
            }
        }

        if (!hasHeadTdzScope) {
            compilerContext.expressionCompiler.compile(forInStmt.getRight());
            compilerContext.emitter.emitOpcode(Opcode.FOR_IN_START);
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(
                loopStart,
                compilerContext.scopeManager.getScopeDepth() - 1,
                compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        compilerContext.emitter.emitOpcode(Opcode.FOR_IN_NEXT);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        if (isExpressionBased) {
            compileForInExpressionTargetAssignment((Expression) forInStmt.getLeft());
        } else if (declarationPattern != null) {
            compilerContext.patternCompiler.compileForOfValueAssignment(declarationPattern, isVar);
        }

        compilerContext.statementCompiler.compile(forInStmt.getBody());

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

    private void compileForInExpressionTargetAssignment(Expression leftExpression) {
        if (leftExpression instanceof CallExpression) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.expressionCompiler.compile(leftExpression);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            compilerContext.emitter.emitU8(5);
            return;
        }
        compilerContext.patternCompiler.compileAssignmentTarget(leftExpression);
    }

}
