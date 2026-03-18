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

import com.caoccao.qjs4j.compilation.ast.Expression;
import com.caoccao.qjs4j.compilation.ast.SequenceExpression;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.List;

final class SequenceExpressionCompiler extends AstNodeCompiler<SequenceExpression> {
    SequenceExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(SequenceExpression seqExpr) {
        List<Expression> expressions = seqExpr.getExpressions();
        for (int i = 0; i < expressions.size(); i++) {
            if (i < expressions.size() - 1) {
                compilerContext.pushState();
                compilerContext.emitTailCalls = false;
                compilerContext.expressionCompiler.compile(expressions.get(i));
                compilerContext.popState();
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                compilerContext.expressionCompiler.compile(expressions.get(i));
            }
        }
    }
}
