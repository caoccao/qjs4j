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

final class ArrayExpressionDestructuringAssignmentCompiler extends AstNodeCompiler<ArrayExpression> {
    ArrayExpressionDestructuringAssignmentCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ArrayExpression arrayExpr) {
        var elements = arrayExpr.getElements();
        int restIndex = 0;
        while (restIndex < elements.size() && !(elements.get(restIndex) instanceof SpreadElement)) {
            restIndex++;
        }
        boolean hasRest = restIndex < elements.size();
        int doneLocal = compilerContext.emitHelpers.startArrayDestructuring("#arrayAssignIterDone", hasRest);
        for (int i = 0; i < restIndex; i++) {
            Expression element = elements.get(i);
            // Evaluate the assignment reference before advancing the iterator.
            int depth = compilerContext.expressionCompiler.preEvaluateAssignmentTarget(element);
            compilerContext.emitHelpers.emitNextDestructuringValue(depth, doneLocal);
            if (element == null) {
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                compilerContext.expressionDestructuringAssignmentCompiler.compileFromPreEvaluated(element, depth);
            }
        }
        if (!hasRest) {
            compilerContext.emitHelpers.emitIteratorCloseUnlessDone(doneLocal);
            return;
        }
        Expression restTarget = ((SpreadElement) elements.get(restIndex)).getArgument();
        int depth = compilerContext.expressionCompiler.preEvaluateAssignmentTarget(restTarget);
        compilerContext.emitHelpers.emitIteratorRestArray(depth);
        // A nested assignment can throw. Remove the exhausted iterator before assigning.
        emitDropIteratorStatePreservingTopValues(depth + 1);
        compilerContext.expressionDestructuringAssignmentCompiler.compileFromPreEvaluated(restTarget, depth);
    }

    private void emitDropIteratorStatePreservingTopValues(int preservedValueCount) {
        int[] preservedLocalIndexes = new int[preservedValueCount];
        for (int valueIndex = preservedValueCount - 1; valueIndex >= 0; valueIndex--) {
            int localIndex = compilerContext.scopeManager.currentScope()
                    .declareLocal("#iter_preserve_" + valueIndex + "_" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitPutLocal(localIndex);
            preservedLocalIndexes[valueIndex] = localIndex;
        }

        compilerContext.emitHelpers.emitDropIteratorState();

        for (int valueIndex = 0; valueIndex < preservedValueCount; valueIndex++) {
            compilerContext.emitter.emitGetLocal(preservedLocalIndexes[valueIndex]);
        }
    }
}
