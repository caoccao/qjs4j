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
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, local);
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
            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, id.getName());
        }
        compilerContext.inferredClassName = null;
        compilerContext.patternCompiler.compileForOfValueAssignment(pattern, true);
    }

    private void compileForInLoop(ForEachStatement forInStmt, Pattern pattern, boolean lexical) {
        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart,
                compilerContext.scopeManager.getScopeDepth() - 1, compilerContext.scopeManager.getScopeDepth());
        compilerContext.loopManager.pushLoop(loop);

        compilerContext.emitter.emitOpcode(Opcode.FOR_IN_NEXT);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        compilerContext.statementCompiler.emitEvalReturnUndefinedIfNeeded();

        compileBody(forInStmt, pattern, lexical);

        compilerContext.emitter.emitGoto(loopStart);

        compilerContext.emitter.patchJumpToHere(jumpToEnd);
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJumpToHere(breakPos);
        }
        compilerContext.emitter.patchJumps(loop.continuePositions, loopStart);

        compilerContext.emitter.emitOpcode(Opcode.DROP);

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
            forOfUsingStackLocal = compilerContext.scopeManager.currentScope()
                    .declareLocal("#forof_using_stack_" + compilerContext.emitter.currentOffset());
            compilerContext.scopeManager.currentScope().setUsingStackLocal(forOfUsingStackLocal, useAsyncStack);
        }

        // A for-await loop keeps its iterator in a local too, for the awaited return() of a break or a return.
        // Stack: iter next marker -> iter next marker iter next marker -> iter next marker iter -> iter next marker
        int asyncIteratorLocal = -1;
        if (forOfStmt.isAsync()) {
            asyncIteratorLocal = compilerContext.scopeManager.currentScope()
                    .declareLocal("#for_await_iterator_" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DUP3);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, asyncIteratorLocal);
        }

        int loopStart = compilerContext.emitter.currentOffset();
        LoopContext loop = compilerContext.loopManager.createLoopContext(loopStart,
                compilerContext.scopeManager.getScopeDepth() - 1, compilerContext.scopeManager.getScopeDepth());
        loop.hasIterator = true;
        loop.asyncIteratorLocal = asyncIteratorLocal;
        compilerContext.loopManager.pushLoop(loop);

        int jumpToEnd;

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            // An error from awaiting next(), or reading its result, does not close the iterator.
            // Catch it above the iterator marker, discard that record, and rethrow. Body errors
            // still reach the marker and perform AsyncIteratorClose.
            int nextError = compilerContext.emitter.emitJump(Opcode.CATCH);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "done");
            int doneJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            int bodyJump = compilerContext.emitter.emitJump(Opcode.GOTO);

            compilerContext.emitter.patchJumpToHere(nextError);
            int errorLocal = compilerContext.scopeManager.currentScope()
                    .declareLocal("#for_await_next_error_" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, errorLocal);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, errorLocal);
            compilerContext.emitter.emitOpcode(Opcode.THROW);

            compilerContext.emitter.patchJumpToHere(doneJump);
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);
            compilerContext.emitter.patchJumpToHere(bodyJump);
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

        compileBody(forOfStmt, pattern, lexical);

        // Per-iteration disposal for using/await-using
        if (isUsingForOf) {
            compilerContext.emitHelpers.emitScopeUsingDisposal(compilerContext.scopeManager.currentScope());
        }

        compilerContext.emitter.emitGoto(loopStart);

        int loopEnd = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToEnd, loopEnd);

        compilerContext.emitter.emitOpcode(Opcode.DROP);

        // A for-await loop that ran to the end has nothing to close; one left by a break awaits its iterator's
        // return(). Stack: iter next marker -> (empty)
        int jumpOverBreakTarget = -1;
        if (asyncIteratorLocal >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            jumpOverBreakTarget = compilerContext.emitter.emitJump(Opcode.GOTO);
        }
        int breakTarget = compilerContext.emitter.currentOffset();
        compilerContext.emitHelpers.emitLoopIteratorClose(loop);
        if (jumpOverBreakTarget >= 0) {
            compilerContext.emitter.patchJumpToHere(jumpOverBreakTarget);
        }

        compilerContext.emitter.patchJumps(loop.breakPositions, breakTarget);
        compilerContext.emitter.patchJumps(loop.continuePositions, loopStart);

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
        compilerContext.emitter.patchJumpToHere(jumpToEnd);
        // Drop remaining value on stack
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        // Clean up iterator: drop catch_offset, next, iter
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }

    private void startIteration(ForEachStatement statement) {
        compilerContext.expressionCompiler.compile(statement.getRight());
        if (statement instanceof ForInStatement) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_IN_START);
        } else {
            compilerContext.emitter.emitSourcePosition(statement.getRight().getLocation());
            compilerContext.emitter.emitOpcode(statement.isAsync() ? Opcode.FOR_AWAIT_OF_START : Opcode.FOR_OF_START);
        }
    }
}
