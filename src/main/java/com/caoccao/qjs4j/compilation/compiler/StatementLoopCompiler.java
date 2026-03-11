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
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

final class StatementLoopCompiler {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;
    private final StatementCompiler owner;

    StatementLoopCompiler(StatementCompiler owner, CompilerContext compilerContext, CompilerDelegates delegates) {
        this.owner = owner;
        this.compilerContext = compilerContext;
        this.delegates = delegates;
    }

    void compileBreakStatement(BreakStatement breakStmt) {
        if (breakStmt.getLabel() != null) {
            String labelName = breakStmt.getLabel().getName();
            LoopContext target = null;
            for (LoopContext loopCtx : compilerContext.loopStack) {
                if (labelName.equals(loopCtx.label)) {
                    target = loopCtx;
                    break;
                }
            }
            if (target == null) {
                throw new JSCompilerException("Undefined label '" + labelName + "'");
            }
            delegates.emitHelpers.emitIteratorCloseForLoopsUntil(target);
            delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(target.breakTargetScopeDepth);
            int jumpPos = compilerContext.emitter.emitJump(Opcode.GOTO);
            target.breakPositions.add(jumpPos);
        } else {
            if (compilerContext.loopStack.isEmpty()) {
                throw new JSCompilerException("Break statement outside of loop");
            }
            LoopContext loopContext = null;
            for (LoopContext loopCtx : compilerContext.loopStack) {
                if (!loopCtx.isRegularStmt) {
                    loopContext = loopCtx;
                    break;
                }
            }
            if (loopContext == null) {
                throw new JSCompilerException("Break statement outside of loop");
            }
            delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(loopContext.breakTargetScopeDepth);
            int jumpPos = compilerContext.emitter.emitJump(Opcode.GOTO);
            loopContext.breakPositions.add(jumpPos);
        }
    }

    void compileContinueStatement(ContinueStatement contStmt) {
        if (contStmt.getLabel() != null) {
            String labelName = contStmt.getLabel().getName();
            LoopContext target = null;
            for (LoopContext loopCtx : compilerContext.loopStack) {
                if (labelName.equals(loopCtx.label) && !loopCtx.isRegularStmt) {
                    target = loopCtx;
                    break;
                }
            }
            if (target == null) {
                throw new JSCompilerException("Undefined label '" + labelName + "'");
            }
            delegates.emitHelpers.emitIteratorCloseForLoopsUntil(target);
            delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(target.continueTargetScopeDepth);
            int jumpPos = compilerContext.emitter.emitJump(Opcode.GOTO);
            target.continuePositions.add(jumpPos);
        } else {
            LoopContext loopContext = null;
            for (LoopContext loopCtx : compilerContext.loopStack) {
                if (!loopCtx.isRegularStmt) {
                    loopContext = loopCtx;
                    break;
                }
            }
            if (loopContext == null) {
                throw new JSCompilerException("Continue statement outside of loop");
            }
            delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(loopContext.continueTargetScopeDepth);
            int jumpPos = compilerContext.emitter.emitJump(Opcode.GOTO);
            loopContext.continuePositions.add(jumpPos);
        }
    }

    void compileDoWhileStatement(DoWhileStatement doWhileStmt) {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        owner.compileStatement(doWhileStmt.getBody());

        int testStart = compilerContext.emitter.currentOffset();
        delegates.expressions.compileExpression(doWhileStmt.getTest());
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

        compilerContext.loopStack.pop();
    }

    void compileForInStatement(ForInStatement forInStmt) {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }

        boolean isExpressionBased = forInStmt.getLeft() instanceof Expression;
        String varName = null;
        VariableDeclaration varDecl = null;

        if (!isExpressionBased) {
            varDecl = (VariableDeclaration) forInStmt.getLeft();
            if (varDecl.getDeclarations().size() != 1) {
                throw new JSCompilerException("for-in loop must have exactly one variable");
            }
            Pattern pattern = varDecl.getDeclarations().get(0).getId();
            if (!(pattern instanceof Identifier id)) {
                throw new JSCompilerException("for-in loop variable must be an identifier");
            }
            varName = id.getName();

            if (varDecl.getKind() == VariableKind.VAR) {
                compilerContext.currentScope().declareLocal(varName);
            }
        }

        compilerContext.enterScope();

        Integer varIndex = null;
        if (!isExpressionBased) {
            if (varDecl.getKind() != VariableKind.VAR) {
                compilerContext.currentScope().declareLocal(varName);
                if (varDecl.getKind() == VariableKind.CONST) {
                    compilerContext.currentScope().markConstLocal(varName);
                }
            }
            varIndex = compilerContext.findLocalInScopes(varName);
        }

        if (!isExpressionBased && varDecl != null && varDecl.getDeclarations().get(0).getInit() != null) {
            delegates.expressions.compileExpression(varDecl.getDeclarations().get(0).getInit());
            if (varIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, varIndex);
            } else {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            }
        }

        delegates.expressions.compileExpression(forInStmt.getRight());
        compilerContext.emitter.emitOpcode(Opcode.FOR_IN_START);

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth - 1, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        compilerContext.emitter.emitOpcode(Opcode.FOR_IN_NEXT);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        if (isExpressionBased) {
            Expression leftExpr = (Expression) forInStmt.getLeft();
            if (leftExpr instanceof Identifier id) {
                Integer localIdx = compilerContext.findLocalInScopes(id.getName());
                if (localIdx != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIdx);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.getName());
                }
            } else if (leftExpr instanceof MemberExpression memberExpr) {
                delegates.expressions.compileExpression(memberExpr.getObject());
                if (memberExpr.isComputed()) {
                    delegates.expressions.compileExpression(memberExpr.getProperty());
                    throw new JSCompilerException("Computed member expression in for-in not yet supported");
                } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateIdentifier) {
                    JSSymbol privateSymbol = compilerContext.privateSymbols.get(privateIdentifier.getName());
                    if (privateSymbol == null) {
                        throw new JSCompilerException("undefined private field '#" + privateIdentifier.getName() + "'");
                    }
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, privateSymbol);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                } else if (memberExpr.getProperty() instanceof Identifier propertyIdentifier) {
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propertyIdentifier.getName());
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                } else {
                    throw new JSCompilerException("Invalid for-in assignment target");
                }
            } else if (leftExpr instanceof CallExpression) {
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                delegates.expressions.compileExpression(leftExpr);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
                compilerContext.emitter.emitU8(5);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        } else if (varIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, varIndex);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        owner.compileStatement(forInStmt.getBody());

        if (!isExpressionBased && varDecl != null && varDecl.getKind() != VariableKind.VAR && varIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.CLOSE_LOC, varIndex);
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

        compilerContext.loopStack.pop();
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.exitScope();
    }

    private void compileForOfExpressionTargetAssignment(Expression leftExpression) {
        if (leftExpression instanceof CallExpression) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            delegates.expressions.compileExpression(leftExpression);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            compilerContext.emitter.emitU8(5);
            return;
        }
        delegates.patterns.compileAssignmentTarget(leftExpression);
    }

    void compileForOfStatement(ForOfStatement forOfStmt) {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }

        boolean isExpressionBased = !(forOfStmt.getLeft() instanceof VariableDeclaration);
        VariableDeclaration varDecl = null;
        Pattern pattern = null;
        boolean isVar = false;

        if (isExpressionBased) {
            if (forOfStmt.getLeft() instanceof CallExpression callExpr) {
                delegates.patterns.compileForOfWithCallExpressionTarget(forOfStmt, callExpr);
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
                delegates.patterns.declarePatternVariables(pattern);
            }
        }

        compilerContext.enterScope();
        delegates.expressions.compileExpression(forOfStmt.getRight());

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
        }

        if (!isExpressionBased && (!isVar || compilerContext.inGlobalScope)) {
            delegates.patterns.declarePatternVariables(pattern);
            if (varDecl != null && (varDecl.getKind() == VariableKind.CONST
                    || varDecl.getKind() == VariableKind.USING
                    || varDecl.getKind() == VariableKind.AWAIT_USING)) {
                delegates.patterns.markPatternConstBindings(pattern);
            }
        }

        boolean savedInGlobalScope = compilerContext.inGlobalScope;
        compilerContext.inGlobalScope = false;

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth - 1, compilerContext.scopeDepth);
        loop.hasIterator = true;
        compilerContext.loopStack.push(loop);

        int jumpToEnd;

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "done");
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
            if (isExpressionBased) {
                compileForOfExpressionTargetAssignment((Expression) forOfStmt.getLeft());
            } else {
                delegates.patterns.compileForOfValueAssignment(pattern, isVar);
            }
        } else {
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            if (isExpressionBased) {
                compileForOfExpressionTargetAssignment((Expression) forOfStmt.getLeft());
            } else {
                delegates.patterns.compileForOfValueAssignment(pattern, isVar);
            }
        }

        owner.compileStatement(forOfStmt.getBody());

        if (!isExpressionBased && !isVar) {
            delegates.emitHelpers.emitCloseLocForPattern(pattern);
        }

        compilerContext.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToEnd, loopEnd);

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        int breakTarget = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);

        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJump(breakPos, breakTarget);
        }
        for (int continuePos : loop.continuePositions) {
            compilerContext.emitter.patchJump(continuePos, loopStart);
        }

        compilerContext.loopStack.pop();
        compilerContext.inGlobalScope = savedInGlobalScope;

        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.exitScope();
    }

    void compileForStatement(ForStatement forStmt) {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }

        boolean initCompiled = false;
        if (forStmt.getInit() instanceof VariableDeclaration varDecl && varDecl.getKind() == VariableKind.VAR) {
            owner.compileVariableDeclaration(varDecl);
            initCompiled = true;
        }

        compilerContext.enterScope();

        if (!initCompiled && forStmt.getInit() != null) {
            if (forStmt.getInit() instanceof VariableDeclaration varDecl) {
                boolean savedInGlobalScope = compilerContext.inGlobalScope;
                if (compilerContext.inGlobalScope && varDecl.getKind() != VariableKind.VAR) {
                    compilerContext.inGlobalScope = false;
                }
                owner.compileVariableDeclaration(varDecl);
                compilerContext.inGlobalScope = savedInGlobalScope;
            } else if (forStmt.getInit() instanceof Expression expr) {
                delegates.expressions.compileExpression(expr);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else if (forStmt.getInit() instanceof ExpressionStatement exprStmt) {
                delegates.expressions.compileExpression(exprStmt.getExpression());
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth - 1, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        int jumpToEnd = -1;
        if (forStmt.getTest() != null) {
            delegates.expressions.compileExpression(forStmt.getTest());
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        }

        owner.compileStatement(forStmt.getBody());

        if (forStmt.getInit() instanceof VariableDeclaration varDeclForClose
                && varDeclForClose.getKind() != VariableKind.VAR) {
            delegates.emitHelpers.emitCloseLocForPattern(varDeclForClose);
        }

        int updateStart = compilerContext.emitter.currentOffset();

        if (forStmt.getUpdate() != null) {
            delegates.expressions.compileExpression(forStmt.getUpdate());
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

        compilerContext.loopStack.pop();
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.exitScope();
    }

    void compileWhileStatement(WhileStatement whileStmt) {
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        delegates.expressions.compileExpression(whileStmt.getTest());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        owner.compileStatement(whileStmt.getBody());

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

        compilerContext.loopStack.pop();
    }
}
