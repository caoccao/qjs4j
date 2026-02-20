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

import com.caoccao.qjs4j.compilation.ast.ClassDeclaration;
import com.caoccao.qjs4j.compilation.ast.PrivateIdentifier;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class FunctionClassFieldCompiler {
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;

    FunctionClassFieldCompiler(CompilerContext ctx, CompilerDelegates delegates) {
        this.ctx = ctx;
        this.delegates = delegates;
    }

    void compileComputedFieldNameCache(
            ClassDeclaration.PropertyDefinition field,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols) {
        JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
        if (computedFieldSymbol == null) {
            throw new JSCompilerException("Computed field key symbol not found");
        }

        ctx.emitter.emitOpcode(Opcode.SWAP);
        ctx.emitter.emitOpcode(Opcode.DUP);
        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
        delegates.expressions.compileExpression(field.key());
        ctx.emitter.emitOpcode(Opcode.TO_PROPKEY);
        ctx.emitter.emitOpcode(Opcode.DEFINE_PROP);
        ctx.emitter.emitOpcode(Opcode.DROP);
        ctx.emitter.emitOpcode(Opcode.SWAP);
    }

    void compileFieldInitialization(List<ClassDeclaration.PropertyDefinition> fields,
                                    Map<String, JSSymbol> privateSymbols,
                                    IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols) {
        for (ClassDeclaration.PropertyDefinition field : fields) {
            boolean isPrivate = field.isPrivate();

            ctx.emitter.emitOpcode(Opcode.PUSH_THIS);

            if (isPrivate) {
                if (!(field.key() instanceof PrivateIdentifier privateId)) {
                    throw new JSCompilerException("Invalid private field key");
                }
                String fieldName = privateId.name();

                if (field.value() != null) {
                    delegates.expressions.compileExpression(field.value());
                } else {
                    ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                JSSymbol symbol = privateSymbols.get(fieldName);
                if (symbol != null) {
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    ctx.emitter.emitOpcode(Opcode.SWAP);
                    ctx.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
                } else {
                    ctx.emitter.emitOpcode(Opcode.DROP);
                    ctx.emitter.emitOpcode(Opcode.DROP);
                    continue;
                }
            } else {
                if (field.computed()) {
                    JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
                    if (computedFieldSymbol == null) {
                        throw new JSCompilerException("Computed field key not found");
                    }
                    ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    ctx.emitter.emitU8(2);
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
                    ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else {
                    delegates.emitHelpers.emitNonComputedPublicFieldKey(field.key());
                }

                if (field.value() != null) {
                    delegates.expressions.compileExpression(field.value());
                } else {
                    ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                ctx.emitter.emitOpcode(Opcode.DEFINE_PROP);
            }

            ctx.emitter.emitOpcode(Opcode.DROP);
        }
    }
}
