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

import com.caoccao.qjs4j.compilation.ast.ArrayExpression;
import com.caoccao.qjs4j.compilation.ast.Expression;
import com.caoccao.qjs4j.compilation.ast.SpreadElement;
import com.caoccao.qjs4j.vm.Opcode;

final class ArrayExpressionDestructuringAssignmentCompiler {
    private final CompilerContext compilerContext;

    ArrayExpressionDestructuringAssignmentCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileArrayDestructuringAssignment(ArrayExpression arrayExpr) {
        // Stack: [iterable]
        // Use iterator protocol (FOR_OF_START/FOR_OF_NEXT/ITERATOR_CLOSE) per ES spec.
        // Following QuickJS: pre-evaluate LHS references before calling next(),
        // and the VM auto-closes iterators on exception via the JSCatchOffset(0) marker.

        var elements = arrayExpr.getElements();
        boolean hasRest = false;
        int restIndex = -1;
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof SpreadElement) {
                hasRest = true;
                restIndex = i;
                break;
            }
        }

        // Start iteration: iterable -> iter next catch_offset
        compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);

        if (hasRest) {
            // Process elements before rest
            for (int i = 0; i < restIndex; i++) {
                Expression element = elements.get(i);
                // Pre-evaluate LHS, then call FOR_OF_NEXT with the appropriate depth
                int depth = compilerContext.expressionCompiler.preEvaluateAssignmentTarget(element);
                compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, depth);
                // Stack: iter next catch_offset [pre-eval...] value done
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                // Stack: iter next catch_offset [pre-eval...] value
                if (element != null) {
                    compilerContext.patternCompiler.emitAssignmentFromPreEvaluated(element, depth);
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                }
            }

            SpreadElement spreadElem = (SpreadElement) elements.get(restIndex);
            Expression restTarget = spreadElem.getArgument();
            int restTargetDepth = compilerContext.expressionCompiler.preEvaluateAssignmentTarget(restTarget);

            // Collect remaining elements into array for rest
            compilerContext.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);
            compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
            compilerContext.emitter.emitI32(0);

            int labelRestNext = compilerContext.emitter.currentOffset();
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 2 + restTargetDepth);
            int jumpRestDone = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
            compilerContext.emitter.emitOpcode(Opcode.INC);
            compilerContext.emitter.emitOpcode(Opcode.GOTO);
            int backJumpPos = compilerContext.emitter.currentOffset();
            compilerContext.emitter.emitU32(labelRestNext - (backJumpPos + 4));

            compilerContext.emitter.patchJump(jumpRestDone, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);

            // Iterator is fully exhausted after rest collection. Remove iterator state
            // before assigning the rest target so abrupt completions in nested patterns
            // do not attempt an extra IteratorClose.
            compilerContext.patternCompiler.emitDropIteratorStatePreservingTopValues(restTargetDepth + 1);

            // Assign collected array to rest target.
            compilerContext.patternCompiler.emitAssignmentFromPreEvaluated(restTarget, restTargetDepth);
        } else {
            // No rest element - use iterator with done tracking and IteratorClose
            int iteratorDoneLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                    "$arrayAssignIterDone" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, iteratorDoneLocalIndex);

            for (Expression element : elements) {
                // Pre-evaluate LHS, then call FOR_OF_NEXT with the appropriate depth
                int depth = compilerContext.expressionCompiler.preEvaluateAssignmentTarget(element);
                compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, depth);
                // Stack: iter next catch_offset [pre-eval...] value done
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, iteratorDoneLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                // Stack: iter next catch_offset [pre-eval...] value
                if (element != null) {
                    compilerContext.patternCompiler.emitAssignmentFromPreEvaluated(element, depth);
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                }
            }

            // Check if iterator was exhausted
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, iteratorDoneLocalIndex);
            int skipIteratorCloseJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            // Not exhausted - call IteratorClose
            compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            int iteratorCloseDoneJump = compilerContext.emitter.emitJump(Opcode.GOTO);
            // Exhausted - just drop iter state
            compilerContext.emitter.patchJump(skipIteratorCloseJump, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.patchJump(iteratorCloseDoneJump, compilerContext.emitter.currentOffset());
        }
    }
}
