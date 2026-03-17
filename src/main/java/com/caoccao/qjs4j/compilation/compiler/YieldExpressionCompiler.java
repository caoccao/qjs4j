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

import com.caoccao.qjs4j.compilation.ast.YieldExpression;
import com.caoccao.qjs4j.vm.Opcode;

final class YieldExpressionCompiler {
    private final CompilerContext compilerContext;

    YieldExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileYieldExpression(YieldExpression yieldExpr) {
        if (yieldExpr.getArgument() != null) {
            compilerContext.expressionCompiler.compileExpression(yieldExpr.getArgument());
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        if (yieldExpr.isDelegate()) {
            compilerContext.emitter.emitOpcode(compilerContext.isInAsyncFunction ? Opcode.ASYNC_YIELD_STAR : Opcode.YIELD_STAR);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.YIELD);
        }
    }
}
