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

final class ArrayExpressionCompiler extends AstNodeCompiler<ArrayExpression> {
    ArrayExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ArrayExpression arrayExpr) {
        compilerContext.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);

        // Check if we have any spread elements or holes
        boolean hasSpread = arrayExpr.getElements().stream()
                .anyMatch(e -> e instanceof SpreadElement);
        boolean hasHoles = arrayExpr.getElements().stream()
                .anyMatch(e -> e == null);

        if (!hasSpread && !hasHoles) {
            // Simple case: no spread elements, no holes
            compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, 0);
            for (Expression element : arrayExpr.getElements()) {
                compilerContext.expressionCompiler.compile(element);
                compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                compilerContext.emitter.emitOpcode(Opcode.INC);
            }
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else {
            // Complex case: has spread elements or holes
            // Following QuickJS: emit position tracking
            // Stack starts with: array
            int idx = 0;
            boolean needsIndex = false;
            boolean needsLength = false;

            for (Expression element : arrayExpr.getElements()) {
                if (element instanceof SpreadElement spreadElement) {
                    // Emit index if not already on stack
                    if (!needsIndex) {
                        compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                    }
                    // Compile the iterable expression
                    compilerContext.expressionCompiler.compile(spreadElement.getArgument());
                    // Emit APPEND to spread elements into the array
                    // Stack: array pos iterable -> array pos
                    compilerContext.emitter.emitOpcode(Opcode.APPEND);
                    // After APPEND, index is updated on stack
                    needsLength = false;
                } else if (element != null) {
                    if (needsIndex) {
                        // We have index on stack, use DEFINE_ARRAY_EL
                        compilerContext.expressionCompiler.compile(element);
                        compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        compilerContext.emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    } else {
                        // No index on stack yet
                        // Start using index-based assignment since we have holes or spread
                        compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                        compilerContext.expressionCompiler.compile(element);
                        compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        compilerContext.emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    }
                } else {
                    // Hole in array
                    if (needsIndex) {
                        // We have position on stack, just increment it
                        compilerContext.emitter.emitOpcode(Opcode.INC);
                    } else {
                        idx++;
                    }
                    needsLength = true;
                }
            }

            // If we have a trailing hole, set the array length explicitly
            // This handles cases like [1, 2, ,] where we need length=3 but only 2 elements
            if (needsLength) {
                if (needsIndex) {
                    // Stack: array idx
                    // QuickJS pattern: dup1 (duplicate array), put_field "length"
                    // dup1: array idx -> array array idx
                    compilerContext.emitter.emitOpcode(Opcode.DUP1);  // array array idx
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx (PUT_FIELD leaves value)
                    compilerContext.emitter.emitOpcode(Opcode.DROP);  // array
                } else {
                    // Stack: array (idx is compile-time constant)
                    // QuickJS pattern: dup, push idx, swap, put_field "length", drop
                    compilerContext.emitter.emitOpcode(Opcode.DUP);  // array array
                    compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);  // array array idx
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);  // array idx array
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx
                    compilerContext.emitter.emitOpcode(Opcode.DROP);  // array
                }
            } else if (needsIndex) {
                // No trailing hole, just drop the index
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }
    }
}
