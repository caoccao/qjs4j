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
    private ArrayExpressionDestructuringAssignmentCompiler() {
    }

    static void compile(CompilerContext compilerContext, ArrayExpression arrayExpr) {
        var elements = arrayExpr.getElements();
        int restIndex = 0;
        while (restIndex < elements.size() && !(elements.get(restIndex) instanceof SpreadElement)) {
            restIndex++;
        }
        boolean hasRest = restIndex < elements.size();
        int doneLocal = IteratorCompiler.startArrayDestructuring(compilerContext, "#arrayAssignIterDone", hasRest);
        for (int i = 0; i < restIndex; i++) {
            Expression element = elements.get(i);
            // Evaluate the assignment reference before advancing the iterator.
            int depth = ExpressionCompiler.preEvaluateAssignmentTarget(compilerContext, element);
            IteratorCompiler.emitNextDestructuringValue(compilerContext, depth, doneLocal);
            if (element == null) {
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                ExpressionDestructuringAssignmentCompiler.compileFromPreEvaluated(compilerContext, element, depth);
            }
        }
        if (!hasRest) {
            IteratorCompiler.emitIteratorCloseUnlessDone(compilerContext, doneLocal);
            return;
        }
        Expression restTarget = ((SpreadElement) elements.get(restIndex)).getArgument();
        int depth = ExpressionCompiler.preEvaluateAssignmentTarget(compilerContext, restTarget);
        IteratorCompiler.emitIteratorRestArray(compilerContext, depth);
        // A nested assignment can throw. Remove the exhausted iterator before assigning.
        emitDropIteratorStatePreservingTopValues(compilerContext, depth + 1);
        ExpressionDestructuringAssignmentCompiler.compileFromPreEvaluated(compilerContext, restTarget, depth);
    }

    private static void emitDropIteratorStatePreservingTopValues(CompilerContext compilerContext,
            int preservedValueCount) {
        BytecodeEmitter emitter = compilerContext.emitter;
        int[] preservedLocalIndexes = new int[preservedValueCount];
        for (int valueIndex = preservedValueCount - 1; valueIndex >= 0; valueIndex--) {
            int localIndex = compilerContext.scopeManager.currentScope()
                    .declareLocal("#iter_preserve_" + valueIndex + "_" + emitter.currentOffset());
            emitter.emitPutLocal(localIndex);
            preservedLocalIndexes[valueIndex] = localIndex;
        }

        compilerContext.emitter.emitDrops(3);

        for (int valueIndex = 0; valueIndex < preservedValueCount; valueIndex++) {
            emitter.emitGetLocal(preservedLocalIndexes[valueIndex]);
        }
    }
}
