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
        // For untagged template literals, concatenate strings and expressions
        // Example: `Hello ${name}!` becomes "Hello " + name + "!"

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

        // Add each expression and subsequent quasi using string concatenation (ADD)
        for (int i = 0; i < expressions.size(); i++) {
            // Template substitutions use ToString coercion (not + operator default hint).
            // Call String(expr) to ensure ToString semantics.
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, "String");
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.expressionCompiler.compile(expressions.get(i));
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 1);

            // Concatenate using ADD after explicit ToString on the substitution.
            compilerContext.emitter.emitOpcode(Opcode.ADD);

            // Add the next quasi if it exists
            if (i + 1 < quasis.size()) {
                String quasi = quasis.get(i + 1);
                if (quasi == null) {
                    throw new JSCompilerException("Invalid escape sequence in untagged template literal");
                }
                if (!quasi.isEmpty()) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
                    compilerContext.emitter.emitOpcode(Opcode.ADD);
                }
            }
        }
    }
}
