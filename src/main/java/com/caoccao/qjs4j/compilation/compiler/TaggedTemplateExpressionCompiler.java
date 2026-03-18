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
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.List;

final class TaggedTemplateExpressionCompiler extends AstNodeCompiler<TaggedTemplateExpression> {
    TaggedTemplateExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(TaggedTemplateExpression taggedTemplate) {
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
            compilerContext.expressionCompiler.compile(memberExpr.getObject());

            // Get the method while keeping the object on the stack
            if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.expressionCompiler.compile(memberExpr.getProperty());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
            }

            // SWAP converts obj/func to func/obj for internalHandleCall and locks property chain
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } else {
            // Regular function call: func`template`
            // Compile the tag function first (will be the callee)
            compilerContext.expressionCompiler.compile(taggedTemplate.getTag());

            // Add undefined as receiver/thisArg
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // QuickJS behavior: each call site uses a stable, frozen template object.
        // Build it once in the constant pool and pass it as the first argument.
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, createTaggedTemplateObject(template));

        // Add substitution expressions as additional arguments
        for (Expression expr : expressions) {
            compilerContext.expressionCompiler.compile(expr);
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

    private JSArray createTaggedTemplateObject(TemplateLiteral template) {
        List<String> cookedQuasis = template.getQuasis();
        List<String> rawQuasis = template.getRawQuasis();
        int segmentCount = rawQuasis.size();

        JSArray templateObject = new JSArray(compilerContext.context);
        JSArray rawArray = new JSArray(compilerContext.context);

        for (int i = 0; i < segmentCount; i++) {
            JSString rawValue = new JSString(rawQuasis.get(i));
            rawArray.set(i, rawValue);
            rawArray.defineProperty(
                    PropertyKey.fromIndex(i),
                    PropertyDescriptor.dataDescriptor(rawValue, PropertyDescriptor.DataState.Enumerable));

            String cookedQuasi = cookedQuasis.get(i);
            JSValue cookedValue = cookedQuasi == null ? JSUndefined.INSTANCE : new JSString(cookedQuasi);
            templateObject.set(i, cookedValue);
            templateObject.defineProperty(
                    PropertyKey.fromIndex(i),
                    PropertyDescriptor.dataDescriptor(cookedValue, PropertyDescriptor.DataState.Enumerable));
        }

        // QuickJS/spec attributes for template objects.
        rawArray.defineProperty(PropertyKey.fromString("length"), JSNumber.of(segmentCount), PropertyDescriptor.DataState.None);
        templateObject.defineProperty(PropertyKey.fromString("length"), JSNumber.of(segmentCount), PropertyDescriptor.DataState.None);
        templateObject.defineProperty(PropertyKey.fromString("raw"), rawArray, PropertyDescriptor.DataState.None);

        rawArray.freeze();
        templateObject.freeze();
        return templateObject;
    }
}
