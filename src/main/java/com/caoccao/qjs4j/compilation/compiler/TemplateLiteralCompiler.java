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
import com.caoccao.qjs4j.compilation.ast.TemplateLiteral;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.List;

final class TemplateLiteralCompiler extends AstNodeCompiler<TemplateLiteral> {
    TemplateLiteralCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(TemplateLiteral templateLiteral) {
        // Follow QuickJS strategy for untagged templates:
        // emit first cooked quasi, then call String.prototype.concat with
        // interleaved substitutions and following quasis.
        // This preserves template-substitution ToString behavior (including
        // Symbol TypeError) without introducing a new opcode.

        List<String> quasis = templateLiteral.getQuasis();
        List<Expression> expressions = templateLiteral.getExpressions();

        if (quasis.isEmpty()) {
            // Empty template literal
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(""));
            return;
        }

        // Start with the first quasi
        String firstQuasi = quasis.get(0);
        if (firstQuasi == null) {
            throw new JSCompilerException("Invalid escape sequence in untagged template literal");
        }
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(firstQuasi));

        if (expressions.isEmpty()) {
            return;
        }

        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "concat");
        compilerContext.emitter.emitOpcode(Opcode.SWAP);

        int argumentCount = 0;
        for (int index = 0; index < expressions.size(); index++) {
            compilerContext.expressionCompiler.compile(expressions.get(index));
            argumentCount++;

            if (index + 1 < quasis.size()) {
                String quasi = quasis.get(index + 1);
                if (quasi == null) {
                    throw new JSCompilerException("Invalid escape sequence in untagged template literal");
                }
                if (!quasi.isEmpty()) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
                    argumentCount++;
                }
            }
        }

        compilerContext.emitter.emitOpcodeU16(Opcode.CALL_METHOD, argumentCount);
    }
}
