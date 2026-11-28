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

import com.caoccao.qjs4j.vm.Opcode;

/** Emits iterator advancement and close sequences for loops and destructuring. */
final class IteratorCompiler {
    private IteratorCompiler() {
    }

    /**
     * AsyncIteratorClose for a {@code for await} loop left by a {@code break}, a {@code continue} to an outer loop or a
     * {@code return}: call the iterator's {@code return()}, await the result and require an object. The loop used to
     * call {@code return()} without awaiting it, as for a sync iterator, so the loop completed ticks before V8's.
     * <p>
     * Runs after {@code ITERATOR_CLOSE} has taken the loop's iterator off the stack, so a {@code return()} that throws
     * is not called a second time by exception unwinding.
     * <p>
     * Stack: unchanged
     */
    static void emitAsyncIteratorReturn(CompilerContext compilerContext, int iteratorLocal) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitGetLocal(iteratorLocal);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcode(Opcode.UNDEFINED);
        // Stack: iter undefined undefined undefined -> iter undefined undefined result noReturnMethod
        emitter.emitIntOperand(Opcode.ITERATOR_CALL, 2);
        int jumpWithoutReturnMethod = emitter.emitJump(Opcode.IF_TRUE);
        emitter.emitOpcode(Opcode.AWAIT);
        emitter.emitOpcode(Opcode.ITERATOR_CHECK_OBJECT);
        emitter.patchJumpToHere(jumpWithoutReturnMethod);
        emitter.emitDrops(4);
    }

    /**
     * Emit ITERATOR_CLOSE for any for-of loops between the current position and the target loop context. This is needed
     * when labeled break/continue crosses for-of loop boundaries, to properly close inner iterators whose cleanup code
     * would otherwise be skipped. Following QuickJS close_scopes pattern for iterator cleanup.
     */
    static void emitIteratorCloseForLoopsUntil(CompilerContext compilerContext, LoopContext target) {
        for (LoopContext loopCtx : compilerContext.loopManager) {
            if (loopCtx == target) {
                break;
            }
            if (loopCtx.hasIterator) {
                emitLoopIteratorClose(compilerContext, loopCtx);
            }
        }
    }

    static void emitIteratorCloseUnlessDone(CompilerContext compilerContext, int doneLocal) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitGetLocal(doneLocal);
        int exhausted = emitter.emitJump(Opcode.IF_TRUE);
        emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
        int end = emitter.emitJump(Opcode.GOTO);
        emitter.patchJumpToHere(exhausted);
        compilerContext.emitter.emitDrops(3);
        emitter.patchJumpToHere(end);
    }

    static void emitIteratorRestArray(CompilerContext compilerContext, int targetDepth) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitIntOperand(Opcode.ARRAY_FROM, 0);
        emitter.emitIntOperand(Opcode.PUSH_I32, 0);

        // Stack above the iterator: pre-evaluated target, result array, next index.
        int loopStart = emitter.currentOffset();
        emitter.emitIntOperand(Opcode.FOR_OF_NEXT, 2 + targetDepth);
        int doneJump = emitter.emitJump(Opcode.IF_TRUE);
        emitter.emitArrayElement();
        emitter.emitOpcode(Opcode.GOTO);
        emitter.emitU32(loopStart - (emitter.currentOffset() + 4));
        emitter.patchJumpToHere(doneJump);
        emitter.emitOpcode(Opcode.DROP); // final value
        emitter.emitOpcode(Opcode.DROP); // next index
    }

    /**
     * Close a loop's iterator: {@code ITERATOR_CLOSE}, followed for a {@code for await} loop by the awaited
     * {@code return()}.
     */
    static void emitLoopIteratorClose(CompilerContext compilerContext, LoopContext loopContext) {
        compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
        if (loopContext.asyncIteratorLocal >= 0) {
            emitAsyncIteratorReturn(compilerContext, loopContext.asyncIteratorLocal);
        }
    }

    static void emitNextDestructuringValue(CompilerContext compilerContext, int depth, int doneLocal) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitIntOperand(Opcode.FOR_OF_NEXT, depth);
        if (doneLocal >= 0) {
            emitter.emitOpcode(Opcode.DUP);
            emitter.emitPutLocal(doneLocal);
        }
        emitter.emitOpcode(Opcode.DROP);
    }

    static int startArrayDestructuring(CompilerContext compilerContext, String doneLocalPrefix, boolean hasRest) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitOpcode(Opcode.FOR_OF_START);
        if (hasRest) {
            return -1;
        }
        int doneLocal = compilerContext.scopeManager.currentScope()
                .declareLocal(doneLocalPrefix + emitter.currentOffset());
        emitter.emitOpcode(Opcode.PUSH_FALSE);
        emitter.emitPutLocal(doneLocal);
        return doneLocal;
    }
}
