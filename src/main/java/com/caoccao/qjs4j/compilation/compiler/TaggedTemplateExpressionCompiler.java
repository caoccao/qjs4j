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

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.List;

final class TaggedTemplateExpressionCompiler {
    private final CompilerContext compilerContext;

    TaggedTemplateExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileTaggedTemplateExpression(TaggedTemplateExpression taggedTemplate) {
        boolean isTailCall = compilerContext.emitTailCalls;
        compilerContext.emitTailCalls = false;

        // Tagged template: tag`template`
        // The tag function receives:
        // 1. A template object (array-like) with cooked strings and a 'raw' property
        // 2. The values of the substitutions as additional arguments

        TemplateLiteral template = taggedTemplate.getQuasi();
        List<Expression> expressions = template.getExpressions();

        // Check if this is a method call (tag is a member expression)
        if (taggedTemplate.getTag() instanceof MemberExpression memberExpr) {
            // For method calls: obj.method`template`
            // We need to preserve obj as the 'this' value

            // Push object (receiver)
            compilerContext.expressionCompiler.compileExpression(memberExpr.getObject());

            // Get the method while keeping the object on the stack
            if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.expressionCompiler.compileExpression(memberExpr.getProperty());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
            }

            // SWAP converts obj/func to func/obj for internalHandleCall and locks property chain
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } else {
            // Regular function call: func`template`
            // Compile the tag function first (will be the callee)
            compilerContext.expressionCompiler.compileExpression(taggedTemplate.getTag());

            // Add undefined as receiver/thisArg
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // QuickJS behavior: each call site uses a stable, frozen template object.
        // Build it once in the constant pool and pass it as the first argument.
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, compilerContext.functionCompiler.createTaggedTemplateObject(template));

        // Add substitution expressions as additional arguments
        for (Expression expr : expressions) {
            compilerContext.expressionCompiler.compileExpression(expr);
        }

        // Call the tag function
        // argCount = 1 (template array) + number of expressions
        int argCount = 1 + expressions.size();
        boolean isMethodCall = taggedTemplate.getTag() instanceof MemberExpression;
        if (isMethodCall) {
            compilerContext.emitter.emitOpcodeU16(
                    isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD,
                    argCount);
        } else {
            compilerContext.emitter.emitOpcodeU16(
                    isTailCall ? Opcode.TAIL_CALL : Opcode.CALL,
                    argCount);
        }
    }
}
