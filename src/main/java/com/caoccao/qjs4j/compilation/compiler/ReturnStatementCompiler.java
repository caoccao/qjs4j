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

import com.caoccao.qjs4j.compilation.ast.CallExpression;
import com.caoccao.qjs4j.compilation.ast.ReturnStatement;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.Iterator;
import java.util.List;

/**
 * Compiles return-statement AST nodes into bytecode.
 */
final class ReturnStatementCompiler {
    private final CompilerContext compilerContext;

    ReturnStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileReturnStatement(ReturnStatement retStmt) {
        // Tail call optimization: when the return argument has a call in tail position
        // in strict mode, with no active finally blocks, iterators, or async context,
        // emit TAIL_CALL instead of CALL + RETURN (ES2015 14.6.1 HasCallInTailPosition).
        // Exclude spread calls (which use APPLY, not CALL) and super calls from TCO.
        if (retStmt.getArgument() instanceof CallExpression
                && retStmt.getArgument().hasTailCallInTailPosition()
                && compilerContext.strictMode
                && !compilerContext.isInAsyncFunction
                && compilerContext.activeFinallyGosubPatches.isEmpty()
                && !compilerContext.loopManager.hasActiveIteratorLoops()) {
            // Direct call in tail position: TAIL_CALL handles the return entirely
            compilerContext.emitTailCalls = true;
            compilerContext.expressionCompiler.compileExpression(retStmt.getArgument());
            compilerContext.emitTailCalls = false;
            return;
        }

        // For non-direct-call expressions with tail calls in sub-positions
        // (e.g., return a ?? f(), return 0, f(), return a ? f() : g()),
        // set emitTailCalls and fall through to emit RETURN for non-tail paths.
        boolean enableTco = retStmt.getArgument() != null
                && retStmt.getArgument().hasTailCallInTailPosition()
                && compilerContext.strictMode
                && !compilerContext.isInAsyncFunction
                && compilerContext.activeFinallyGosubPatches.isEmpty()
                && !compilerContext.loopManager.hasActiveIteratorLoops();

        if (retStmt.getArgument() != null) {
            if (enableTco) {
                compilerContext.emitTailCalls = true;
            }
            compilerContext.expressionCompiler.compileExpression(retStmt.getArgument());
            compilerContext.emitTailCalls = false;
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        int returnValueIndex = compilerContext.scopeManager.currentScope().declareLocal("$return_value_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);

        compilerContext.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(0);

        if (compilerContext.loopManager.hasActiveIteratorLoops()) {
            compilerContext.emitHelpers.emitAbruptCompletionIteratorClose();
        }

        // Execute active finally blocks via GOSUB before returning.
        // Walk from innermost to outermost finally context.
        Iterator<List<Integer>> gosubPatchIterator = compilerContext.activeFinallyGosubPatches.iterator();
        Iterator<Integer> nipCatchCountIterator = compilerContext.activeFinallyNipCatchCounts.iterator();
        while (gosubPatchIterator.hasNext() && nipCatchCountIterator.hasNext()) {
            List<Integer> gosubPatches = gosubPatchIterator.next();
            int nipCatchCount = nipCatchCountIterator.next();
            // Push dummy value (NIP_CATCH expects a value on top of the CatchOffset)
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            // NIP_CATCH removes the CatchOffset for this try-finally, keeps dummy on top
            for (int i = 0; i < nipCatchCount; i++) {
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            }
            // GOSUB pushes return address and jumps to finally block (patched later)
            int gosubPos = compilerContext.emitter.emitJump(Opcode.GOSUB);
            gosubPatches.add(gosubPos);
            // After RET returns here, drop the dummy value
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        if (compilerContext.finallySubroutineDepth > 0) {
            // Return from a finally subroutine must discard the caller completion value
            // and GOSUB return address after nested finally chains are processed.
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
        // ES2024 ReturnStatement step 3: If GetGeneratorKind() is async, set exprValue to Await(exprValue)
        // This only applies to explicit return expressions in async generators, not implicit returns
        if (compilerContext.isInAsyncFunction && compilerContext.isInGeneratorFunction && retStmt.getArgument() != null) {
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
        }
        // Emit RETURN_ASYNC for async functions, RETURN for sync functions
        compilerContext.emitter.emitOpcode(compilerContext.isInAsyncFunction ? Opcode.RETURN_ASYNC : Opcode.RETURN);
    }
}
