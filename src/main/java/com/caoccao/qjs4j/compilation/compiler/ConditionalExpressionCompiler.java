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

import com.caoccao.qjs4j.compilation.ast.ConditionalExpression;
import com.caoccao.qjs4j.vm.Opcode;

final class ConditionalExpressionCompiler {
    private final CompilerContext compilerContext;

    ConditionalExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(ConditionalExpression condExpr) {
        compilerContext.pushState();
        compilerContext.emitTailCalls = false;
        compilerContext.expressionCompiler.compile(condExpr.getTest());
        compilerContext.popState();
        int jumpToAlternate = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        compilerContext.expressionCompiler.compile(condExpr.getConsequent());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);
        compilerContext.emitter.patchJump(jumpToAlternate, compilerContext.emitter.currentOffset());
        compilerContext.expressionCompiler.compile(condExpr.getAlternate());
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
    }
}
