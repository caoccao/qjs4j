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

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles ObjectExpression destructuring assignment into bytecode.
 */
final class ObjectExpressionDestructuringAssignmentCompiler {

    private ObjectExpressionDestructuringAssignmentCompiler() {
    }

    static void compile(CompilerContext compilerContext, ObjectExpression objExpr) {
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
            compilerContext.emitter.emitRequireObjectCoercible();
        }

        int sourceLocalIndex = compilerContext.storeTemporary("#objectAssignSource");

        // If there's a rest element with regular properties, create an exclude list
        int excludeListLocalIndex = -1;
        if (restTarget != null && !regularProperties.isEmpty()) {
            compilerContext.emitter.emitOpcode(Opcode.OBJECT);
            excludeListLocalIndex = compilerContext.declareTemporary("#excludeList");
            compilerContext.emitter.emitPutLocal(excludeListLocalIndex);
        }

        for (ObjectExpressionProperty property : regularProperties) {
            int propertyKeyLocalIndex = -1;
            if (property.isComputed()) {
                compilerContext.expressionCompiler.compilePropertyKey(property.getKey());
                propertyKeyLocalIndex = compilerContext.declareTemporary("#objectAssignKey");
                compilerContext.emitter.emitPutLocal(propertyKeyLocalIndex);
            }

            int targetDepth = compilerContext.expressionCompiler.preEvaluateAssignmentTarget(property.getValue());

            compilerContext.emitter.emitGetLocal(sourceLocalIndex);
            String atom = ObjectDestructuringCompiler.prepareRead(compilerContext, property, propertyKeyLocalIndex);
            ObjectDestructuringCompiler.read(compilerContext.emitter, atom);

            compilerContext.expressionDestructuringAssignmentCompiler.compileFromPreEvaluated(property.getValue(),
                    targetDepth);

            // If rest, add property key to exclude list
            if (restTarget != null) {
                if (property.isComputed()) {
                    // Computed property: use PUT_ARRAY_EL which works with objects.
                    compilerContext.emitter.emitGetLocal(excludeListLocalIndex);
                    compilerContext.emitter.emitGetLocal(propertyKeyLocalIndex);
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null value
                } else {
                    // Non-computed: use DEFINE_FIELD with atom name.
                    compilerContext.emitter.emitGetLocal(excludeListLocalIndex);
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    String name = ObjectDestructuringCompiler.staticKey(property);
                    if (name != null) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, name);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop excludeList
                }
            }
        }

        if (restTarget != null) {
            boolean hasExclusions = excludeListLocalIndex >= 0;
            if (hasExclusions) {
                compilerContext.emitter.emitGetLocal(excludeListLocalIndex);
            }
            compilerContext.emitter.emitGetLocal(sourceLocalIndex);
            ObjectDestructuringCompiler.copyRest(compilerContext.emitter, hasExclusions);
            compilerContext.expressionDestructuringAssignmentCompiler.compile(restTarget);
            ObjectDestructuringCompiler.finishRest(compilerContext.emitter, hasExclusions);
        }
    }
}
