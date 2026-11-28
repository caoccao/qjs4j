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

import java.util.List;

final class ArrayExpressionCompiler {
    private ArrayExpressionCompiler() {
    }

    static void compile(CompilerContext context, ArrayExpression expression) {
        emitArray(context, expression.getElements());
    }

    /** Array literals and spread argument lists use the same index-tracking instructions. */
    static void emitArray(CompilerContext context, List<Expression> elements) {
        BytecodeEmitter emitter = context.emitter;
        emitter.emitIntOperand(Opcode.ARRAY_FROM, 0);
        int leadingHoles = 0;
        for (Expression element : elements) {
            if (element != null) {
                break;
            }
            leadingHoles++;
        }
        if (leadingHoles > 0 && leadingHoles == elements.size()) {
            emitter.emitOpcode(Opcode.DUP);
            emitter.emitIntOperand(Opcode.PUSH_I32, leadingHoles);
            emitter.emitOpcode(Opcode.SWAP);
            emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");
        } else {
            emitter.emitIntOperand(Opcode.PUSH_I32, leadingHoles);
            for (var iterator = elements.listIterator(leadingHoles); iterator.hasNext();) {
                Expression element = iterator.next();
                if (element == null) {
                    emitter.emitOpcode(Opcode.INC);
                } else if (element instanceof SpreadElement spread) {
                    context.compileExpression(spread.getArgument());
                    emitter.emitSourcePosition(spread.getArgument().getLocation());
                    emitter.emitOpcode(Opcode.APPEND);
                } else {
                    context.compileExpression(element);
                    emitter.emitArrayElement();
                }
            }
            if (!elements.isEmpty() && elements.get(elements.size() - 1) == null) {
                emitter.emitOpcode(Opcode.DUP1);
                emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");
            }
        }
        emitter.emitOpcode(Opcode.DROP);
    }
}
