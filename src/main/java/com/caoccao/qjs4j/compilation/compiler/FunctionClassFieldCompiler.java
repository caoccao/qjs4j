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
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class FunctionClassFieldCompiler {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;

    FunctionClassFieldCompiler(CompilerContext compilerContext, CompilerDelegates delegates) {
        this.compilerContext = compilerContext;
        this.delegates = delegates;
    }

    private static boolean isAnonymousFunctionDefinition(Expression expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof ArrowFunctionExpression) {
            return true;
        }
        if (expression instanceof FunctionExpression functionExpression) {
            return functionExpression.getId() == null;
        }
        if (expression instanceof ClassExpression classExpression) {
            return classExpression.getId() == null;
        }
        return false;
    }

    void compileComputedFieldNameCache(
            PropertyDefinition field,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSSymbol> privateSymbols) {
        JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
        if (computedFieldSymbol == null) {
            throw new JSCompilerException("Computed field key symbol not found");
        }

        Map<String, JSSymbol> savedPrivateSymbols = compilerContext.privateSymbols;
        compilerContext.privateSymbols = privateSymbols;

        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
        try {
            delegates.expressions.compileExpression(field.getKey());
            compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
            compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } finally {
            compilerContext.privateSymbols = savedPrivateSymbols;
        }
    }

    void compileFieldInitialization(List<PropertyDefinition> fields,
                                    Map<String, JSSymbol> privateSymbols,
                                    IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
                                    IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols) {
        for (PropertyDefinition field : fields) {
            boolean isPrivate = field.isPrivate();

            compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);

            // Track class field initializer context for ContainsArguments check in eval
            boolean savedInClassFieldInitializer = compilerContext.inClassFieldInitializer;
            compilerContext.inClassFieldInitializer = true;

            if (isPrivate) {
                if (!(field.getKey() instanceof PrivateIdentifier privateId)) {
                    throw new JSCompilerException("Invalid private field key");
                }
                String fieldName = privateId.getName();

                if (field.getValue() != null) {
                    delegates.expressions.compileExpression(field.getValue());
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                JSSymbol symbol = privateSymbols.get(fieldName);
                if (symbol != null) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    if (isAnonymousFunctionDefinition(field.getValue())) {
                        compilerContext.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    continue;
                }
            } else if (field.isAutoAccessor()) {
                JSSymbol backingSymbol = autoAccessorBackingSymbols.get(field);
                if (backingSymbol == null) {
                    throw new JSCompilerException("Auto-accessor backing symbol not found");
                }

                if (field.getValue() != null) {
                    delegates.expressions.compileExpression(field.getValue());
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, backingSymbol);
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                if (isAnonymousFunctionDefinition(field.getValue())) {
                    compilerContext.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
                }
                compilerContext.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
            } else {
                if (field.isComputed()) {
                    JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
                    if (computedFieldSymbol == null) {
                        throw new JSCompilerException("Computed field key not found");
                    }
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(2);
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else {
                    delegates.emitHelpers.emitNonComputedPublicFieldKey(field.getKey());
                }

                if (field.getValue() != null) {
                    delegates.expressions.compileExpression(field.getValue());
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
            }

            compilerContext.inClassFieldInitializer = savedInClassFieldInitializer;
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }
}
