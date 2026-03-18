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

import com.caoccao.qjs4j.compilation.ast.FunctionExpression;
import com.caoccao.qjs4j.compilation.ast.Identifier;
import com.caoccao.qjs4j.compilation.ast.ObjectExpression;
import com.caoccao.qjs4j.compilation.ast.ObjectExpressionProperty;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

final class ObjectExpressionCompiler {
    private final CompilerContext compilerContext;

    ObjectExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(ObjectExpression objExpr) {
        int protoDataPropertyCount = 0;
        for (ObjectExpressionProperty property : objExpr.getProperties()) {
            if (property.isProtoDataProperty()) {
                protoDataPropertyCount++;
                if (protoDataPropertyCount > 1) {
                    throw new JSCompilerException("Duplicate __proto__ fields are not allowed in object literals");
                }
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.OBJECT);

        for (ObjectExpressionProperty prop : objExpr.getProperties()) {
            String kind = prop.getKind();

            if ("spread".equals(kind)) {
                // Object spread: {...expr}
                // Stack: obj -> obj expr null -> obj (via COPY_DATA_PROPERTIES)
                compilerContext.expressionCompiler.compile(prop.getValue());
                compilerContext.emitter.emitOpcode(Opcode.NULL);
                // mask=6: target@sp[-3](offset 2), source@sp[-2](offset 1), exclude@sp[-1](offset 0)
                compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 6);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                continue;
            }

            if (JSKeyword.GET.equals(kind) || JSKeyword.SET.equals(kind)) {
                // Getter/setter property: use DEFINE_METHOD_COMPUTED
                // Stack: obj -> obj key method -> obj
                // Push key
                if (prop.isComputed()) {
                    compilerContext.expressionCompiler.compile(prop.getKey());
                    compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                } else if (prop.getKey() instanceof Identifier id) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.getName()));
                } else {
                    compilerContext.expressionCompiler.compile(prop.getKey());
                    compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                }

                // Compile the getter/setter function (not constructable per ES spec)
                compilerContext.functionExpressionCompiler.compile((FunctionExpression) prop.getValue(), true);

                // DEFINE_METHOD_COMPUTED with flags: kind (1=get, 2=set) | enumerable (4)
                int methodKind = JSKeyword.GET.equals(kind) ? 1 : 2;
                int flags = methodKind | 4; // enumerable = true for object literal properties
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, flags);
            } else {
                // Regular property: key: value
                // ES2015 B.3.1: __proto__ in object literal sets prototype
                if (!prop.isComputed() && !prop.isShorthand()
                        && prop.getKey() instanceof Identifier id
                        && "__proto__".equals(id.getName())) {
                    // Stack: obj -> obj proto -> obj
                    compilerContext.expressionCompiler.compile(prop.getValue());
                    compilerContext.emitter.emitOpcode(Opcode.SET_PROTO);
                } else {
                    // Push value
                    if (prop.isMethod() && prop.getValue() instanceof FunctionExpression methodFunc) {
                        // Push key for DEFINE_METHOD_COMPUTED
                        if (prop.getKey() instanceof Identifier id && !prop.isComputed()) {
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.getName()));
                        } else {
                            compilerContext.expressionCompiler.compile(prop.getKey());
                            compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                        }
                        // Concise methods are not constructors per ES spec
                        compilerContext.functionExpressionCompiler.compile(methodFunc, true);
                        // Object literal methods are enumerable.
                        compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
                    } else if (prop.getKey() instanceof Identifier id && !prop.isComputed()) {
                        // Non-computed identifier key: use DEFINE_FIELD with atom
                        compilerContext.expressionCompiler.compile(prop.getValue());
                        if (prop.getValue().isAnonymousFunction()) {
                            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, id.getName());
                        }
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, id.getName());
                    } else {
                        // Computed or non-identifier key: define own data property directly.
                        // Stack: [obj] -> [obj, key, value] -> [obj]
                        compilerContext.expressionCompiler.compile(prop.getKey());
                        compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                        compilerContext.expressionCompiler.compile(prop.getValue());
                        if (prop.getValue().isAnonymousFunction()) {
                            compilerContext.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
                        }
                        compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
                    }
                }
            }
        }
    }
}
