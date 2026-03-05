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
        if (breakStmt.label() != null) {
            String labelName = breakStmt.label().name();
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
        if (contStmt.label() != null) {
            String labelName = contStmt.label().name();
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
        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        owner.compileStatement(doWhileStmt.body());

        int testStart = compilerContext.emitter.currentOffset();
        delegates.expressions.compileExpression(doWhileStmt.test());
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
        boolean isExpressionBased = forInStmt.left() instanceof Expression;
        String varName = null;
        VariableDeclaration varDecl = null;

        if (!isExpressionBased) {
            varDecl = (VariableDeclaration) forInStmt.left();
            if (varDecl.declarations().size() != 1) {
                throw new JSCompilerException("for-in loop must have exactly one variable");
            }
            Pattern pattern = varDecl.declarations().get(0).id();
            if (!(pattern instanceof Identifier id)) {
                throw new JSCompilerException("for-in loop variable must be an identifier");
            }
            varName = id.name();

            if (varDecl.kind() == VariableKind.VAR) {
                compilerContext.currentScope().declareLocal(varName);
            }
        }

        compilerContext.enterScope();

        Integer varIndex = null;
        if (!isExpressionBased) {
            if (varDecl.kind() != VariableKind.VAR) {
                compilerContext.currentScope().declareLocal(varName);
            }
            varIndex = compilerContext.findLocalInScopes(varName);
        }

        if (!isExpressionBased && varDecl != null && varDecl.declarations().get(0).init() != null) {
            delegates.expressions.compileExpression(varDecl.declarations().get(0).init());
            if (varIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, varIndex);
            } else {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            }
        }

        delegates.expressions.compileExpression(forInStmt.right());
        compilerContext.emitter.emitOpcode(Opcode.FOR_IN_START);

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth - 1, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        compilerContext.emitter.emitOpcode(Opcode.FOR_IN_NEXT);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        if (isExpressionBased) {
            Expression leftExpr = (Expression) forInStmt.left();
            if (leftExpr instanceof Identifier id) {
                Integer localIdx = compilerContext.findLocalInScopes(id.name());
                if (localIdx != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIdx);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.name());
                }
            } else if (leftExpr instanceof MemberExpression memberExpr) {
                delegates.expressions.compileExpression(memberExpr.object());
                if (memberExpr.computed()) {
                    delegates.expressions.compileExpression(memberExpr.property());
                    throw new JSCompilerException("Computed member expression in for-in not yet supported");
                } else {
                    String propName = ((Identifier) memberExpr.property()).name();
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propName);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
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

        owner.compileStatement(forInStmt.body());

        if (!isExpressionBased && varDecl != null && varDecl.kind() != VariableKind.VAR && varIndex != null) {
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
        if (leftExpression instanceof Identifier id) {
            Integer localIndex = compilerContext.findLocalInScopes(id.name());
            if (localIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            } else {
                Integer capturedIndex = compilerContext.resolveCapturedBindingIndex(id.name());
                if (capturedIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_VAR_REF, capturedIndex);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.name());
                }
            }
            return;
        }

        if (leftExpression instanceof MemberExpression memberExpression) {
            if (memberExpression.computed()) {
                throw new JSCompilerException("Computed member expression in for-of not yet supported");
            }
            if (!(memberExpression.property() instanceof Identifier propertyIdentifier)) {
                throw new JSCompilerException("Invalid for-of assignment target");
            }
            delegates.expressions.compileExpression(memberExpression.object());
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propertyIdentifier.name());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            return;
        }

        if (leftExpression instanceof CallExpression) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            delegates.expressions.compileExpression(leftExpression);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            compilerContext.emitter.emitU8(5);
            return;
        }

        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }

    void compileForOfStatement(ForOfStatement forOfStmt) {
        boolean isExpressionBased = !(forOfStmt.left() instanceof VariableDeclaration);
        VariableDeclaration varDecl = null;
        Pattern pattern = null;
        boolean isVar = false;

        if (isExpressionBased) {
            if (forOfStmt.left() instanceof CallExpression callExpr) {
                delegates.patterns.compileForOfWithCallExpressionTarget(forOfStmt, callExpr);
                return;
            }
        } else {
            varDecl = (VariableDeclaration) forOfStmt.left();
            if (varDecl.declarations().size() != 1) {
                throw new JSCompilerException("for-of loop must have exactly one variable");
            }
            pattern = varDecl.declarations().get(0).id();
            isVar = varDecl.kind() == VariableKind.VAR;

            if (isVar && !compilerContext.inGlobalScope) {
                delegates.patterns.declarePatternVariables(pattern);
            }
        }

        compilerContext.enterScope();
        delegates.expressions.compileExpression(forOfStmt.right());

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
        }

        if (!isExpressionBased && (!isVar || compilerContext.inGlobalScope)) {
            delegates.patterns.declarePatternVariables(pattern);
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
                compileForOfExpressionTargetAssignment((Expression) forOfStmt.left());
            } else {
                delegates.patterns.compileForOfValueAssignment(pattern, isVar);
            }
        } else {
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            if (isExpressionBased) {
                compileForOfExpressionTargetAssignment((Expression) forOfStmt.left());
            } else {
                delegates.patterns.compileForOfValueAssignment(pattern, isVar);
            }
        }

        owner.compileStatement(forOfStmt.body());

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
        boolean initCompiled = false;
        if (forStmt.init() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
            owner.compileVariableDeclaration(varDecl);
            initCompiled = true;
        }

        compilerContext.enterScope();

        if (!initCompiled && forStmt.init() != null) {
            if (forStmt.init() instanceof VariableDeclaration varDecl) {
                boolean savedInGlobalScope = compilerContext.inGlobalScope;
                if (compilerContext.inGlobalScope && varDecl.kind() != VariableKind.VAR) {
                    compilerContext.inGlobalScope = false;
                }
                owner.compileVariableDeclaration(varDecl);
                compilerContext.inGlobalScope = savedInGlobalScope;
            } else if (forStmt.init() instanceof Expression expr) {
                delegates.expressions.compileExpression(expr);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else if (forStmt.init() instanceof ExpressionStatement exprStmt) {
                delegates.expressions.compileExpression(exprStmt.expression());
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth - 1, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        int jumpToEnd = -1;
        if (forStmt.test() != null) {
            delegates.expressions.compileExpression(forStmt.test());
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        }

        owner.compileStatement(forStmt.body());

        if (forStmt.init() instanceof VariableDeclaration varDeclForClose
                && varDeclForClose.kind() != VariableKind.VAR) {
            delegates.emitHelpers.emitCloseLocForPattern(varDeclForClose);
        }

        int updateStart = compilerContext.emitter.currentOffset();

        if (forStmt.update() != null) {
            delegates.expressions.compileExpression(forStmt.update());
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
        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.createLoopContext(loopStart, compilerContext.scopeDepth, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        delegates.expressions.compileExpression(whileStmt.test());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        owner.compileStatement(whileStmt.body());

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
