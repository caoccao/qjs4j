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
import com.caoccao.qjs4j.core.JSAsyncDisposableStack;
import com.caoccao.qjs4j.core.JSDisposableStack;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

final class ForOfStatementCompiler extends AstNodeCompiler<ForOfStatement> {
    ForOfStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ForOfStatement forOfStmt) {
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        boolean isExpressionBased = !(forOfStmt.getLeft() instanceof VariableDeclaration);
        VariableDeclaration varDecl = null;
        Pattern pattern = null;
        boolean isVar = false;

        if (isExpressionBased) {
            if (forOfStmt.getLeft() instanceof CallExpression callExpr) {
                compileForOfWithCallExpressionTarget(forOfStmt, callExpr);
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
            compilerContext.expressionCompiler.compile(forOfStmt.getRight());
            if (forOfStmt.isAsync()) {
                compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
            }
            compilerContext.scopeManager.exitScope();
        }

        compilerContext.scopeManager.enterScope();

        boolean isUsingForOf = varDecl != null
                && (varDecl.getKind() == VariableKind.USING || varDecl.getKind() == VariableKind.AWAIT_USING);
        boolean isAwaitUsingForOf = varDecl != null && varDecl.getKind() == VariableKind.AWAIT_USING;

        if (!isExpressionBased && !isVar) {
            compilerContext.patternCompiler.declarePatternVariables(pattern);
            if (varDecl != null && (varDecl.getKind() == VariableKind.CONST
                    || varDecl.getKind() == VariableKind.USING
                    || varDecl.getKind() == VariableKind.AWAIT_USING)) {
                compilerContext.patternCompiler.markPatternConstBindings(pattern);
            }
        }
        if (!hasHeadTdzScope) {
            compilerContext.expressionCompiler.compile(forOfStmt.getRight());
            if (forOfStmt.isAsync()) {
                compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
            }
        }

        // Declare a local for the per-iteration DisposableStack if needed
        int forOfUsingStackLocal = -1;
        if (isUsingForOf) {
            boolean useAsyncStack = isAwaitUsingForOf || compilerContext.isInAsyncFunction;
            forOfUsingStackLocal = compilerContext.scopeManager.currentScope().declareLocal(
                    "$forof_using_stack_" + compilerContext.emitter.currentOffset());
            compilerContext.scopeManager.currentScope().setUsingStackLocal(forOfUsingStackLocal, useAsyncStack);
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(
                loopStart,
                compilerContext.scopeManager.getScopeDepth() - 1,
                compilerContext.scopeManager.getScopeDepth());
        loop.hasIterator = true;
        compilerContext.loopManager.pushLoop(loop);

        int jumpToEnd;

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "done");
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
            compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();
        } else {
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();
        }

        if (isUsingForOf) {
            // Create a NEW DisposableStack for each iteration
            boolean useAsyncStack = isAwaitUsingForOf || compilerContext.isInAsyncFunction;
            String constructorName = useAsyncStack ? JSAsyncDisposableStack.NAME : JSDisposableStack.NAME;
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, constructorName);
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, 0);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, forOfUsingStackLocal);

            // Call stack.use(value) - value is on the stack
            compilerContext.emitHelpers.emitMethodCallWithSingleArgOnLocalObject(forOfUsingStackLocal, "use");
        }

        if (isExpressionBased) {
            compileForOfExpressionTargetAssignment((Expression) forOfStmt.getLeft());
        } else {
            compilerContext.patternCompiler.compileForOfValueAssignment(pattern, isVar);
        }

        boolean savedIsLastInProgram = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        compilerContext.statementCompiler.compile(forOfStmt.getBody());
        compilerContext.isLastInProgram = savedIsLastInProgram;

        if (!isExpressionBased && !isVar) {
            compilerContext.emitHelpers.emitCloseLocForPattern(pattern);
        }

        // Per-iteration disposal for using/await-using
        if (isUsingForOf) {
            compilerContext.emitHelpers.emitScopeUsingDisposal(compilerContext.scopeManager.currentScope());
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

        if (!isUsingForOf) {
            compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        }
        compilerContext.scopeManager.exitScope();
    }

    private void compileForOfExpressionTargetAssignment(Expression leftExpression) {
        if (leftExpression instanceof CallExpression) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.expressionCompiler.compile(leftExpression);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            compilerContext.emitter.emitU8(5);
            return;
        }
        compilerContext.expressionDestructuringAssignmentCompiler.compile(leftExpression);
    }

    private void compileForOfWithCallExpressionTarget(ForOfStatement forOfStmt, CallExpression callExpr) {
        compilerContext.scopeManager.enterScope();

        // Compile the iterable expression
        compilerContext.expressionCompiler.compile(forOfStmt.getRight());

        // FOR_OF_START to get iterator
        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
        }

        // Stack: iter, next, catch_offset
        int loopStart = compilerContext.emitter.currentOffset();

        // FOR_OF_NEXT to get next value
        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "done");
        } else {
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
        }

        // Stack: iter, next, catch_offset, value, done
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        // Stack: iter, next, catch_offset, value (or result for async)

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
        }

        // Drop the value - we can't assign it
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        // Evaluate the call expression (f() is called at runtime)
        compilerContext.expressionCompiler.compile(callExpr);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        // Throw ReferenceError
        compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
        compilerContext.emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE

        // End label (when done=true)
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        // Drop remaining value on stack
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        // Clean up iterator: drop catch_offset, next, iter
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }
}
