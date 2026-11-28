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
import com.caoccao.qjs4j.compilation.ast.NewExpression;
import com.caoccao.qjs4j.compilation.ast.SpreadElement;
import com.caoccao.qjs4j.vm.Opcode;

final class NewExpressionCompiler {
    private NewExpressionCompiler() {
    }

    static void compile(CompilerContext compilerContext, NewExpression newExpr) {
        boolean hasSpread = newExpr.getArguments().stream().anyMatch(arg -> arg instanceof SpreadElement);

        compilerContext.compileExpression(newExpr.getCallee());

        if (hasSpread) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            ArrayExpressionCompiler.emitArray(compilerContext, newExpr.getArguments());
            // A frame stopped at a `new` reports the position of the `new` keyword, as V8 does.
            compilerContext.emitter.emitSourcePosition(newExpr.getLocation());
            compilerContext.emitter.emitCallDescription(CallPrinter.describeCallee(newExpr.getCallee()));
            compilerContext.emitter.emitIntOperand(Opcode.APPLY, 1);
            return;
        }

        for (Expression arg : newExpr.getArguments()) {
            compilerContext.compileExpression(arg);
        }
        compilerContext.emitter.emitSourcePosition(newExpr.getLocation());
        compilerContext.emitter.emitCallDescription(CallPrinter.describeCallee(newExpr.getCallee()));
        compilerContext.emitter.emitIntOperand(Opcode.CALL_CONSTRUCTOR, newExpr.getArguments().size());
    }
}
