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
import com.caoccao.qjs4j.core.disposable.JSAsyncDisposableStack;
import com.caoccao.qjs4j.core.disposable.JSDisposableStack;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

/** Compiles the common binding scope and the distinct enumeration and iterator loops of for-in and for-of. */
final class ForEachStatementCompiler extends AstNodeCompiler<ForEachStatement> {
    ForEachStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ForEachStatement statement) {
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();
        if (statement instanceof ForOfStatement of && of.getLeft() instanceof CallExpression call) {
            compileForOfWithCallExpressionTarget(of, call);
            return;
        }

        VariableDeclaration declaration = statement.getLeft() instanceof VariableDeclaration variable ? variable : null;
        Pattern pattern = null;
        if (declaration != null) {
            if (declaration.getDeclarations().size() != 1) {
                String loop = statement instanceof ForInStatement ? "for-in" : "for-of";
                throw new JSCompilerException(loop + " loop must have exactly one variable", declaration);
            }
            pattern = declaration.getDeclarations().get(0).getId();
            if (declaration.getKind() == VariableKind.VAR && !compilerContext.inGlobalScope) {
                compilerContext.patternCompiler.declarePatternVariables(pattern);
            }
        }
        boolean lexical = declaration != null && declaration.getKind() != VariableKind.VAR;
        if (lexical) {
            // The RHS sees the loop names in their temporal dead zone.
            compilerContext.scopeManager.enterScope();
            compilerContext.patternCompiler.declarePatternVariables(pattern);
            for (String name : pattern.getBoundNames()) {
                Integer local = compilerContext.scopeManager.currentScope().getLocal(name);
                if (local != null) {
                    emitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, local);
                    compilerContext.tdzLocals.add(name);
                }
            }
            startIteration(statement);
            compilerContext.scopeManager.exitScope();
        }
        compilerContext.scopeManager.enterScope();
        if (lexical) {
            compilerContext.patternCompiler.declarePatternVariables(pattern);
            boolean immutable = statement instanceof ForInStatement
                    ? declaration.getKind() == VariableKind.CONST
                    : declaration.getKind().isImmutable();
            if (immutable) {
                compilerContext.patternCompiler.markPatternConstBindings(pattern);
            }
        }
        if (statement instanceof ForInStatement && declaration != null) {
            compileForInInitializer(pattern, declaration.getDeclarations().get(0).getInit());
        }
        if (!lexical) {
            startIteration(statement);
        }
        if (statement instanceof ForOfStatement of) {
            compileForOfLoop(of, declaration, pattern, lexical);
        } else {
            compileForInLoop(statement, pattern, lexical);
        }
    }

    private void compileBody(ForEachStatement statement, Pattern pattern, boolean lexical) {
        if (statement.getLeft() instanceof Expression expression) {
            compilerContext.expressionDestructuringAssignmentCompiler.compileIterationTarget(expression);
        } else if (pattern != null) {
            compilerContext.patternCompiler.compileForOfValueAssignment(pattern, !lexical);
        }
        compilerContext.statementCompiler.compileNestedStatement(statement.getBody());
        if (lexical) {
            compilerContext.emitHelpers.emitCloseLocForPattern(pattern);
        }
    }

    private void compileForInInitializer(Pattern pattern, Expression initializer) {
        if (initializer == null || pattern == null) {
            return;
        }
        if (pattern instanceof Identifier id && initializer instanceof ClassExpression cls && cls.getId() == null) {
            compilerContext.inferredClassName = id.getName();
        }
        compilerContext.expressionCompiler.compile(initializer);
        if (pattern instanceof Identifier id && initializer.isAnonymousFunction()) {
            emitter.emitOpcodeAtom(Opcode.SET_NAME, id.getName());
        }
        compilerContext.inferredClassName = null;
        compilerContext.patternCompiler.compileForOfValueAssignment(pattern, true);
    }

    private void compileForInLoop(ForEachStatement forInStmt, Pattern pattern, boolean lexical) {
        int loopStart = emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart,
                compilerContext.scopeManager.getScopeDepth() - 1, compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        emitter.emitOpcode(Opcode.FOR_IN_NEXT);
        emitter.emitOpcode(Opcode.DUP);
        emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToEnd = emitter.emitJump(Opcode.IF_TRUE);
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        compileBody(forInStmt, pattern, lexical);

        emitter.emitGoto(loopStart);

        emitter.patchJumpToHere(jumpToEnd);
        emitter.emitOpcode(Opcode.DROP);

        for (int breakPos : loop.breakPositions) {
            emitter.patchJumpToHere(breakPos);
        }
        emitter.patchJumps(loop.continuePositions, loopStart);

        emitter.emitOpcode(Opcode.DROP);

        compilerContext.loopManager.popLoop();
        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }

    private void compileForOfLoop(ForOfStatement forOfStmt, VariableDeclaration varDecl, Pattern pattern,
            boolean lexical) {
        boolean isUsingForOf = varDecl != null && varDecl.getKind().isUsing();
        boolean isAwaitUsingForOf = varDecl != null && varDecl.getKind() == VariableKind.AWAIT_USING;
        // Declare a local for the per-iteration DisposableStack if needed
        int forOfUsingStackLocal = -1;
        if (isUsingForOf) {
            boolean useAsyncStack = isAwaitUsingForOf || compilerContext.isInAsyncFunction;
            forOfUsingStackLocal = compilerContext.declareTemporary("#forof_using_stack_");
            compilerContext.scopeManager.currentScope().setUsingStackLocal(forOfUsingStackLocal, useAsyncStack);
        }

        // A for-await loop keeps its iterator in a local too, for the awaited return() of a break or a return.
        // Stack: iter next marker -> iter next marker iter next marker -> iter next marker iter -> iter next marker
        int asyncIteratorLocal = -1;
        if (forOfStmt.isAsync()) {
            asyncIteratorLocal = compilerContext.declareTemporary("#for_await_iterator_");
            emitter.emitOpcode(Opcode.DUP3);
            emitter.emitDrops(2);
            emitter.emitPutLocal(asyncIteratorLocal);
        }

        int loopStart = emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart,
                compilerContext.scopeManager.getScopeDepth() - 1, compilerContext.scopeManager.getScopeDepth());
        loop.hasIterator = true;
        loop.asyncIteratorLocal = asyncIteratorLocal;
        compilerContext.loopManager.pushLoop(loop);

        int jumpToEnd;

        if (forOfStmt.isAsync()) {
            emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            // An error from awaiting next(), or reading its result, does not close the iterator.
            // Catch it above the iterator marker, discard that record, and rethrow. Body errors
            // still reach the marker and perform AsyncIteratorClose.
            int nextError = emitter.emitJump(Opcode.CATCH);
            emitter.emitOpcode(Opcode.SWAP);
            emitter.emitOpcode(Opcode.AWAIT);
            emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "done");
            int doneJump = emitter.emitJump(Opcode.IF_TRUE);
            emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
            emitter.emitOpcode(Opcode.NIP_CATCH);
            int bodyJump = emitter.emitJump(Opcode.GOTO);

            emitter.patchJumpToHere(nextError);
            int errorLocal = compilerContext.storeTemporary("#for_await_next_error_");
            emitter.emitDrops(3);
            emitter.emitGetLocal(errorLocal);
            emitter.emitOpcode(Opcode.THROW);

            emitter.patchJumpToHere(doneJump);
            emitter.emitOpcode(Opcode.NIP_CATCH);
            jumpToEnd = emitter.emitJump(Opcode.GOTO);
            emitter.patchJumpToHere(bodyJump);
            compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();
        } else {
            emitter.emitIntOperand(Opcode.FOR_OF_NEXT, 0);
            jumpToEnd = emitter.emitJump(Opcode.IF_TRUE);
            compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();
        }

        if (isUsingForOf) {
            // Create a NEW DisposableStack for each iteration
            boolean useAsyncStack = isAwaitUsingForOf || compilerContext.isInAsyncFunction;
            String constructorName = useAsyncStack ? JSAsyncDisposableStack.NAME : JSDisposableStack.NAME;
            emitter.emitOpcodeAtom(Opcode.GET_VAR, constructorName);
            emitter.emitIntOperand(Opcode.CALL_CONSTRUCTOR, 0);
            emitter.emitPutLocal(forOfUsingStackLocal);

            // Call stack.use(value) - value is on the stack
            compilerContext.emitHelpers.emitMethodCallWithSingleArgOnLocalObject(forOfUsingStackLocal, "use");
        }

        compileBody(forOfStmt, pattern, lexical);

        // Per-iteration disposal for using/await-using
        if (isUsingForOf) {
            compilerContext.emitHelpers.emitScopeUsingDisposal(compilerContext.scopeManager.currentScope());
        }

        emitter.emitGoto(loopStart);

        int loopEnd = emitter.currentOffset();
        emitter.patchJump(jumpToEnd, loopEnd);

        emitter.emitOpcode(Opcode.DROP);

        // A for-await loop that ran to the end has nothing to close; one left by a break awaits its iterator's
        // return(). Stack: iter next marker -> (empty)
        int jumpOverBreakTarget = -1;
        if (asyncIteratorLocal >= 0) {
            emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            jumpOverBreakTarget = emitter.emitJump(Opcode.GOTO);
        }
        int breakTarget = emitter.currentOffset();
        compilerContext.emitHelpers.emitLoopIteratorClose(loop);
        if (jumpOverBreakTarget >= 0) {
            emitter.patchJumpToHere(jumpOverBreakTarget);
        }

        emitter.patchJumps(loop.breakPositions, breakTarget);
        emitter.patchJumps(loop.continuePositions, loopStart);

        compilerContext.loopManager.popLoop();

        if (!isUsingForOf) {
            compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        }
        compilerContext.scopeManager.exitScope();
    }

    private void compileForOfWithCallExpressionTarget(ForOfStatement forOfStmt, CallExpression callExpr) {
        compilerContext.scopeManager.enterScope();

        // Compile the iterable expression
        compilerContext.expressionCompiler.compile(forOfStmt.getRight());

        // FOR_OF_START to get iterator
        if (forOfStmt.isAsync()) {
            emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
        } else {
            emitter.emitOpcode(Opcode.FOR_OF_START);
        }

        // Stack: iter, next, catch_offset
        int loopStart = emitter.currentOffset();

        // FOR_OF_NEXT to get next value
        if (forOfStmt.isAsync()) {
            emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            emitter.emitOpcode(Opcode.AWAIT);
            emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "done");
        } else {
            emitter.emitIntOperand(Opcode.FOR_OF_NEXT, 0);
        }

        // Stack: iter, next, catch_offset, value, done
        int jumpToEnd = emitter.emitJump(Opcode.IF_TRUE);
        // Stack: iter, next, catch_offset, value (or result for async)

        if (forOfStmt.isAsync()) {
            emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
        }

        // Drop the value - we can't assign it
        emitter.emitOpcode(Opcode.DROP);
        // Evaluate the call expression (f() is called at runtime)
        compilerContext.expressionCompiler.compile(callExpr);
        emitter.emitOpcode(Opcode.DROP);
        // Throw ReferenceError
        emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
        emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE

        // End label (when done=true)
        emitter.patchJumpToHere(jumpToEnd);
        // Drop remaining value on stack
        emitter.emitOpcode(Opcode.DROP);

        // Clean up iterator: drop catch_offset, next, iter
        emitter.emitDrops(3);

        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }

    private void startIteration(ForEachStatement statement) {
        compilerContext.expressionCompiler.compile(statement.getRight());
        if (statement instanceof ForInStatement) {
            emitter.emitOpcode(Opcode.FOR_IN_START);
        } else {
            emitter.emitSourcePosition(statement.getRight().getLocation());
            emitter.emitOpcode(statement.isAsync() ? Opcode.FOR_AWAIT_OF_START : Opcode.FOR_OF_START);
        }
    }
}
