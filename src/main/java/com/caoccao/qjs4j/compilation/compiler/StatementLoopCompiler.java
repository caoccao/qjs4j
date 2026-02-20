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
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;
    private final StatementCompiler owner;

    StatementLoopCompiler(StatementCompiler owner, CompilerContext ctx, CompilerDelegates delegates) {
        this.owner = owner;
        this.ctx = ctx;
        this.delegates = delegates;
    }

    void compileBreakStatement(BreakStatement breakStmt) {
        if (breakStmt.label() != null) {
            String labelName = breakStmt.label().name();
            LoopContext target = null;
            for (LoopContext loopCtx : ctx.loopStack) {
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
            int jumpPos = ctx.emitter.emitJump(Opcode.GOTO);
            target.breakPositions.add(jumpPos);
        } else {
            if (ctx.loopStack.isEmpty()) {
                throw new JSCompilerException("Break statement outside of loop");
            }
            LoopContext loopContext = null;
            for (LoopContext loopCtx : ctx.loopStack) {
                if (!loopCtx.isRegularStmt) {
                    loopContext = loopCtx;
                    break;
                }
            }
            if (loopContext == null) {
                throw new JSCompilerException("Break statement outside of loop");
            }
            delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(loopContext.breakTargetScopeDepth);
            int jumpPos = ctx.emitter.emitJump(Opcode.GOTO);
            loopContext.breakPositions.add(jumpPos);
        }
    }

    void compileContinueStatement(ContinueStatement contStmt) {
        if (contStmt.label() != null) {
            String labelName = contStmt.label().name();
            LoopContext target = null;
            for (LoopContext loopCtx : ctx.loopStack) {
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
            int jumpPos = ctx.emitter.emitJump(Opcode.GOTO);
            target.continuePositions.add(jumpPos);
        } else {
            LoopContext loopContext = null;
            for (LoopContext loopCtx : ctx.loopStack) {
                if (!loopCtx.isRegularStmt) {
                    loopContext = loopCtx;
                    break;
                }
            }
            if (loopContext == null) {
                throw new JSCompilerException("Continue statement outside of loop");
            }
            delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(loopContext.continueTargetScopeDepth);
            int jumpPos = ctx.emitter.emitJump(Opcode.GOTO);
            loopContext.continuePositions.add(jumpPos);
        }
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
                ctx.currentScope().declareLocal(varName);
            }
        }

        ctx.enterScope();

        Integer varIndex = null;
        if (!isExpressionBased) {
            if (varDecl.kind() != VariableKind.VAR) {
                ctx.currentScope().declareLocal(varName);
            }
            varIndex = ctx.findLocalInScopes(varName);
        }

        if (!isExpressionBased && varDecl != null && varDecl.declarations().get(0).init() != null) {
            delegates.expressions.compileExpression(varDecl.declarations().get(0).init());
            if (varIndex != null) {
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, varIndex);
            } else {
                ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            }
        }

        delegates.expressions.compileExpression(forInStmt.right());
        ctx.emitter.emitOpcode(Opcode.FOR_IN_START);

        int loopStart = ctx.emitter.currentOffset();
        LoopContext loop = ctx.createLoopContext(loopStart, ctx.scopeDepth - 1, ctx.scopeDepth);
        ctx.loopStack.push(loop);

        ctx.emitter.emitOpcode(Opcode.FOR_IN_NEXT);
        ctx.emitter.emitOpcode(Opcode.DUP);
        ctx.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToEnd = ctx.emitter.emitJump(Opcode.IF_TRUE);

        if (isExpressionBased) {
            Expression leftExpr = (Expression) forInStmt.left();
            if (leftExpr instanceof Identifier id) {
                Integer localIdx = ctx.findLocalInScopes(id.name());
                if (localIdx != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIdx);
                } else {
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.name());
                }
            } else if (leftExpr instanceof MemberExpression memberExpr) {
                delegates.expressions.compileExpression(memberExpr.object());
                if (memberExpr.computed()) {
                    delegates.expressions.compileExpression(memberExpr.property());
                    throw new JSCompilerException("Computed member expression in for-in not yet supported");
                } else {
                    String propName = ((Identifier) memberExpr.property()).name();
                    ctx.emitter.emitOpcode(Opcode.SWAP);
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propName);
                    ctx.emitter.emitOpcode(Opcode.DROP);
                }
            } else if (leftExpr instanceof CallExpression) {
                ctx.emitter.emitOpcode(Opcode.DROP);
                delegates.expressions.compileExpression(leftExpr);
                ctx.emitter.emitOpcode(Opcode.DROP);
                ctx.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
                ctx.emitter.emitU8(5);
            } else {
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
        } else if (varIndex != null) {
            ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, varIndex);
        } else {
            ctx.emitter.emitOpcode(Opcode.DROP);
        }

        owner.compileStatement(forInStmt.body());

        if (!isExpressionBased && varDecl != null && varDecl.kind() != VariableKind.VAR && varIndex != null) {
            ctx.emitter.emitOpcodeU16(Opcode.CLOSE_LOC, varIndex);
        }

        ctx.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = ctx.emitter.currentOffset();
        ctx.emitter.emitU32(loopStart - (backJumpPos + 4));

        ctx.emitter.patchJump(jumpToEnd, ctx.emitter.currentOffset());
        ctx.emitter.emitOpcode(Opcode.DROP);

        for (int breakPos : loop.breakPositions) {
            ctx.emitter.patchJump(breakPos, ctx.emitter.currentOffset());
        }
        for (int continuePos : loop.continuePositions) {
            ctx.emitter.patchJump(continuePos, loopStart);
        }

        ctx.emitter.emitOpcode(Opcode.FOR_IN_END);

        ctx.loopStack.pop();
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        ctx.exitScope();
    }

    void compileForOfStatement(ForOfStatement forOfStmt) {
        if (!(forOfStmt.left() instanceof VariableDeclaration varDecl)) {
            if (forOfStmt.left() instanceof CallExpression callExpr) {
                delegates.patterns.compileForOfWithCallExpressionTarget(forOfStmt, callExpr);
                return;
            }
            throw new JSCompilerException("Expression-based for-of not yet supported");
        }
        if (varDecl.declarations().size() != 1) {
            throw new JSCompilerException("for-of loop must have exactly one variable");
        }
        Pattern pattern = varDecl.declarations().get(0).id();
        boolean isVar = varDecl.kind() == VariableKind.VAR;

        if (isVar && !ctx.inGlobalScope) {
            delegates.patterns.declarePatternVariables(pattern);
        }

        ctx.enterScope();
        delegates.expressions.compileExpression(forOfStmt.right());

        if (forOfStmt.isAsync()) {
            ctx.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
        } else {
            ctx.emitter.emitOpcode(Opcode.FOR_OF_START);
        }

        if (!isVar || ctx.inGlobalScope) {
            delegates.patterns.declarePatternVariables(pattern);
        }

        boolean savedInGlobalScope = ctx.inGlobalScope;
        ctx.inGlobalScope = false;

        int loopStart = ctx.emitter.currentOffset();
        LoopContext loop = ctx.createLoopContext(loopStart, ctx.scopeDepth - 1, ctx.scopeDepth);
        loop.hasIterator = true;
        ctx.loopStack.push(loop);

        int jumpToEnd;

        if (forOfStmt.isAsync()) {
            ctx.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            ctx.emitter.emitOpcode(Opcode.AWAIT);
            ctx.emitter.emitOpcode(Opcode.DUP);
            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "done");
            jumpToEnd = ctx.emitter.emitJump(Opcode.IF_TRUE);
            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
            delegates.patterns.compileForOfValueAssignment(pattern, isVar);
        } else {
            ctx.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
            jumpToEnd = ctx.emitter.emitJump(Opcode.IF_TRUE);
            delegates.patterns.compileForOfValueAssignment(pattern, isVar);
        }

        owner.compileStatement(forOfStmt.body());

        if (!isVar) {
            delegates.emitHelpers.emitCloseLocForPattern(pattern);
        }

        ctx.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = ctx.emitter.currentOffset();
        ctx.emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = ctx.emitter.currentOffset();
        ctx.emitter.patchJump(jumpToEnd, loopEnd);

        if (forOfStmt.isAsync()) {
            ctx.emitter.emitOpcode(Opcode.DROP);
        } else {
            ctx.emitter.emitOpcode(Opcode.DROP);
        }

        int breakTarget = ctx.emitter.currentOffset();
        ctx.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);

        for (int breakPos : loop.breakPositions) {
            ctx.emitter.patchJump(breakPos, breakTarget);
        }
        for (int continuePos : loop.continuePositions) {
            ctx.emitter.patchJump(continuePos, loopStart);
        }

        ctx.loopStack.pop();
        ctx.inGlobalScope = savedInGlobalScope;

        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        ctx.exitScope();
    }

    void compileForStatement(ForStatement forStmt) {
        boolean initCompiled = false;
        if (forStmt.init() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
            owner.compileVariableDeclaration(varDecl);
            initCompiled = true;
        }

        ctx.enterScope();

        if (!initCompiled && forStmt.init() != null) {
            if (forStmt.init() instanceof VariableDeclaration varDecl) {
                boolean savedInGlobalScope = ctx.inGlobalScope;
                if (ctx.inGlobalScope && varDecl.kind() != VariableKind.VAR) {
                    ctx.inGlobalScope = false;
                }
                owner.compileVariableDeclaration(varDecl);
                ctx.inGlobalScope = savedInGlobalScope;
            } else if (forStmt.init() instanceof Expression expr) {
                delegates.expressions.compileExpression(expr);
                ctx.emitter.emitOpcode(Opcode.DROP);
            } else if (forStmt.init() instanceof ExpressionStatement exprStmt) {
                delegates.expressions.compileExpression(exprStmt.expression());
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
        }

        int loopStart = ctx.emitter.currentOffset();
        LoopContext loop = ctx.createLoopContext(loopStart, ctx.scopeDepth - 1, ctx.scopeDepth);
        ctx.loopStack.push(loop);

        int jumpToEnd = -1;
        if (forStmt.test() != null) {
            delegates.expressions.compileExpression(forStmt.test());
            jumpToEnd = ctx.emitter.emitJump(Opcode.IF_FALSE);
        }

        owner.compileStatement(forStmt.body());

        if (forStmt.init() instanceof VariableDeclaration varDeclForClose
                && varDeclForClose.kind() != VariableKind.VAR) {
            delegates.emitHelpers.emitCloseLocForPattern(varDeclForClose);
        }

        int updateStart = ctx.emitter.currentOffset();

        if (forStmt.update() != null) {
            delegates.expressions.compileExpression(forStmt.update());
            ctx.emitter.emitOpcode(Opcode.DROP);
        }

        ctx.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = ctx.emitter.currentOffset();
        ctx.emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = ctx.emitter.currentOffset();

        if (jumpToEnd != -1) {
            ctx.emitter.patchJump(jumpToEnd, loopEnd);
        }

        for (int breakPos : loop.breakPositions) {
            ctx.emitter.patchJump(breakPos, loopEnd);
        }
        for (int continuePos : loop.continuePositions) {
            ctx.emitter.patchJump(continuePos, updateStart);
        }

        ctx.loopStack.pop();
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        ctx.exitScope();
    }

    void compileWhileStatement(WhileStatement whileStmt) {
        int loopStart = ctx.emitter.currentOffset();
        LoopContext loop = ctx.createLoopContext(loopStart, ctx.scopeDepth, ctx.scopeDepth);
        ctx.loopStack.push(loop);

        delegates.expressions.compileExpression(whileStmt.test());
        int jumpToEnd = ctx.emitter.emitJump(Opcode.IF_FALSE);

        owner.compileStatement(whileStmt.body());

        ctx.emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = ctx.emitter.currentOffset();
        ctx.emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = ctx.emitter.currentOffset();
        ctx.emitter.patchJump(jumpToEnd, loopEnd);

        for (int breakPos : loop.breakPositions) {
            ctx.emitter.patchJump(breakPos, loopEnd);
        }
        for (int continuePos : loop.continuePositions) {
            ctx.emitter.patchJump(continuePos, loopStart);
        }

        ctx.loopStack.pop();
    }
}
