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
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles ObjectExpression destructuring assignment into bytecode.
 */
final class ObjectExpressionDestructuringAssignmentCompiler extends AstNodeCompiler<ObjectExpression> {

    ObjectExpressionDestructuringAssignmentCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ObjectExpression objExpr) {
        // Stack: [source]
        // Separate regular properties from spread (rest) property.
        // In ObjectExpression, spread is represented as kind="spread".
        List<ObjectExpressionProperty> regularProperties = new ArrayList<>();
        Expression restTarget = null;
        for (ObjectExpressionProperty prop : objExpr.getProperties()) {
            if ("spread".equals(prop.getKind())) {
                restTarget = prop.getValue();
            } else {
                regularProperties.add(prop);
            }
        }

        // Per spec: RequireObjectCoercible(value) for all ObjectAssignmentPattern forms.
        // When there are regular properties, the first GET_FIELD throws for null/undefined.
        // For empty patterns or rest-only patterns, we need an explicit check.
        if (regularProperties.isEmpty()) {
            if (restTarget == null) {
                // {} = val → just check and return
                compilerContext.emitter.emitOpcode(Opcode.TO_OBJECT);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                return;
            }
            // {...rest} = val → explicit coercibility check
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.TO_OBJECT);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        int sourceLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                "$objectAssignSource" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, sourceLocalIndex);

        // If there's a rest element with regular properties, create an exclude list
        int excludeListLocalIndex = -1;
        if (restTarget != null && !regularProperties.isEmpty()) {
            compilerContext.emitter.emitOpcode(Opcode.OBJECT);
            excludeListLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                    "$excludeList" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, excludeListLocalIndex);
        }

        for (ObjectExpressionProperty property : regularProperties) {
            int propertyKeyLocalIndex = -1;
            if (property.isComputed()) {
                compilerContext.expressionCompiler.compile(property.getKey());
                compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                propertyKeyLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                        "$objectAssignKey" + compilerContext.emitter.currentOffset());
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, propertyKeyLocalIndex);
            }

            int targetDepth = compilerContext.expressionCompiler.preEvaluateAssignmentTarget(property.getValue());

            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, sourceLocalIndex);
            if (property.isComputed()) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, propertyKeyLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (property.getKey() instanceof Identifier identifier) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, identifier.getName());
            } else if (property.getKey() instanceof Literal literal && literal.getValue() instanceof String propertyName) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propertyName);
            } else if (property.getKey() instanceof Literal literal
                    && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
                long propertyIndex = ((Number) literal.getValue()).longValue();
                if (propertyIndex >= Integer.MIN_VALUE && propertyIndex <= Integer.MAX_VALUE) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                    compilerContext.emitter.emitI32((int) propertyIndex);
                } else {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(propertyIndex));
                }
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (property.getKey() instanceof Literal literal
                    && literal.getValue() instanceof BigInteger bigIntegerValue) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, bigIntegerValue.toString());
            } else {
                compilerContext.expressionCompiler.compile(property.getKey());
                compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            }

            compilerContext.expressionDestructuringAssignmentCompiler.compileFromPreEvaluated(property.getValue(), targetDepth);

            // If rest, add property key to exclude list
            if (restTarget != null) {
                if (property.isComputed()) {
                    // Computed property: use PUT_ARRAY_EL which works with objects.
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, excludeListLocalIndex);
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, propertyKeyLocalIndex);
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null value
                } else {
                    // Non-computed: use DEFINE_FIELD with atom name.
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, excludeListLocalIndex);
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    if (property.getKey() instanceof Identifier identifier) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, identifier.getName());
                    } else if (property.getKey() instanceof Literal literal && literal.getValue() instanceof String propertyName) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, propertyName);
                    } else if (property.getKey() instanceof Literal literal
                            && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD,
                                String.valueOf(((Number) literal.getValue()).longValue()));
                    } else if (property.getKey() instanceof Literal literal
                            && literal.getValue() instanceof BigInteger bigIntegerValue) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, bigIntegerValue.toString());
                    }
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop excludeList
                }
            }
        }

        if (restTarget != null) {
            if (regularProperties.isEmpty()) {
                // No exclude list — copy all properties from source to a new object.
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, sourceLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                compilerContext.emitter.emitOpcode(Opcode.NULL);
                compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 7);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // null
                compilerContext.emitter.emitOpcode(Opcode.DROP); // source
                compilerContext.expressionDestructuringAssignmentCompiler.compile(restTarget);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // drop extra target
            } else {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, excludeListLocalIndex);
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, sourceLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 68);
                compilerContext.expressionDestructuringAssignmentCompiler.compile(restTarget);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // source
                compilerContext.emitter.emitOpcode(Opcode.DROP); // excludeList
            }
        }
    }
}
