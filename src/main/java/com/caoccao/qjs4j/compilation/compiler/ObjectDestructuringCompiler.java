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
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.vm.Opcode;
import java.math.BigInteger;

/** Key access and object-rest bytecode shared by binding and assignment patterns. */
final class ObjectDestructuringCompiler {
    private ObjectDestructuringCompiler() {
    }

    /** Stack before copy: [source] or [excludedKeys, source]. Leaves a fresh result on top. */
    static void copyRest(BytecodeEmitter emitter, boolean hasExclusions) {
        emitter.emitOpcode(Opcode.OBJECT);
        if (hasExclusions) {
            emitter.emitIntOperand(Opcode.COPY_DATA_PROPERTIES, 68);
        } else {
            emitter.emitOpcode(Opcode.DUP);
            emitter.emitOpcode(Opcode.ROT3L);
            emitter.emitOpcode(Opcode.NULL);
            emitter.emitIntOperand(Opcode.COPY_DATA_PROPERTIES, 7);
            emitter.emitDrops(2);
        }
    }

    static void finishRest(BytecodeEmitter emitter, boolean hasExclusions) {
        emitter.emitOpcode(Opcode.DROP);
        if (hasExclusions) {
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    /** Emit a key operand if needed, or return the atom for a direct field access. */
    static String prepareRead(CompilerContext context, ObjectProperty<?> property, int computedKeyLocal) {
        Expression key = property.getKey();
        if (computedKeyLocal >= 0) {
            context.emitter.emitGetLocal(computedKeyLocal);
        } else if (!property.isComputed() && key instanceof Literal literal
                && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
            long index = ((Number) literal.getValue()).longValue();
            if (index == (int) index) {
                context.emitter.emitIntOperand(Opcode.PUSH_I32, (int) index);
            } else {
                context.emitter.emitPushConstant(JSNumber.of(index));
            }
        } else {
            String name = staticKey(property);
            if (name != null) {
                return name;
            }
            ExpressionCompiler.compilePropertyKey(context, key);
        }
        return null;
    }

    static void read(BytecodeEmitter emitter, String atom) {
        if (atom == null) {
            emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else {
            emitter.emitOpcodeAtom(Opcode.GET_FIELD, atom);
        }
    }

    static String staticKey(ObjectProperty<?> property) {
        if (!property.isComputed()) {
            if (property.getKey() instanceof Identifier identifier) {
                return identifier.getName();
            }
            if (property.getKey() instanceof Literal literal) {
                Object value = literal.getValue();
                if (value instanceof String || value instanceof Integer || value instanceof Long
                        || value instanceof BigInteger) {
                    return value.toString();
                }
            }
        }
        return null;
    }
}
