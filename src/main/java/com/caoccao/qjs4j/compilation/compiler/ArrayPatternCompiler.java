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

import com.caoccao.qjs4j.compilation.ast.ArrayPattern;
import com.caoccao.qjs4j.compilation.ast.Pattern;
import com.caoccao.qjs4j.compilation.ast.RestElement;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles ArrayPattern destructuring into bytecode.
 */
final class ArrayPatternCompiler {

    private ArrayPatternCompiler() {
    }

    static void compile(CompilerContext compilerContext, ArrayPattern arrPattern) {
        var elements = arrPattern.getElements();
        int restIndex = 0;
        while (restIndex < elements.size() && !(elements.get(restIndex) instanceof RestElement)) {
            restIndex++;
        }
        boolean hasRest = restIndex < elements.size();
        int doneLocal = EmitHelpers.startArrayDestructuring(compilerContext, "#arrayPatternIteratorDone", hasRest);
        for (int i = 0; i < restIndex; i++) {
            Pattern element = elements.get(i);
            EmitHelpers.emitNextDestructuringValue(compilerContext, 0, doneLocal);
            if (element == null) {
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                compilerContext.compilePattern(element);
            }
        }
        if (!hasRest) {
            EmitHelpers.emitIteratorCloseUnlessDone(compilerContext, doneLocal);
            return;
        }
        EmitHelpers.emitIteratorRestArray(compilerContext, 0);
        RestElement rest = (RestElement) elements.get(restIndex);
        compilerContext.compilePattern(rest.getArgument());
        EmitHelpers.emitDropIteratorState(compilerContext);
    }
}
