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
import java.util.ArrayDeque;

/**
 * Compiles ObjectPattern destructuring into bytecode.
 */
final class ObjectPatternCompiler extends AstNodeCompiler<ObjectPattern> {

    ObjectPatternCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ObjectPattern objPattern) {
        // Object destructuring: { proxy, revoke } = value
        // Stack: [object]
        boolean hasRest = objPattern.getRestElement() != null;

        // RequireObjectCoercible(value) even for empty patterns.
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TO_OBJECT);
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        if (hasRest && !objPattern.getProperties().isEmpty()) {
            // Create exclude list object and put it under source on stack
            // Stack: [source] -> [excludeList, source]
            compilerContext.emitter.emitOpcode(Opcode.OBJECT);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }

        for (ObjectPatternProperty prop : objPattern.getProperties()) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            Expression propertyKey = prop.getKey();
            if (!prop.isComputed() && propertyKey instanceof Identifier identifier) {
                Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                maybePreResolveBindingIdentifierReference(bindingIdentifier);
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, identifier.getName());
            } else if (!prop.isComputed() && propertyKey instanceof Literal literal && literal.getValue() instanceof String propertyName) {
                Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                maybePreResolveBindingIdentifierReference(bindingIdentifier);
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propertyName);
            } else if (!prop.isComputed() && propertyKey instanceof Literal literal
                    && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
                long propertyIndex = ((Number) literal.getValue()).longValue();
                if (propertyIndex >= Integer.MIN_VALUE && propertyIndex <= Integer.MAX_VALUE) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                    compilerContext.emitter.emitI32((int) propertyIndex);
                } else {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(propertyIndex));
                }
                Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                maybePreResolveBindingIdentifierReference(bindingIdentifier);
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (!prop.isComputed() && propertyKey instanceof Literal literal
                    && literal.getValue() instanceof BigInteger bigIntegerValue) {
                Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                maybePreResolveBindingIdentifierReference(bindingIdentifier);
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, bigIntegerValue.toString());
            } else {
                compilerContext.expressionCompiler.compile(propertyKey);
                compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                maybePreResolveBindingIdentifierReference(bindingIdentifier);
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            }
            // Assign to the pattern (could be nested)
            compilerContext.patternCompiler.compile(prop.getValue());

            if (hasRest) {
                // Add the property key to the exclude list
                // Stack: [excludeList, source]
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                // Stack: [source, excludeList]
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                // Stack: [source, excludeList, excludeList]
                compilerContext.emitter.emitOpcode(Opcode.NULL);
                // Stack: [source, excludeList, excludeList, null]
                if (!prop.isComputed() && propertyKey instanceof Identifier identifier) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, identifier.getName());
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop duplicate excludeList
                } else if (!prop.isComputed() && propertyKey instanceof Literal literal && literal.getValue() instanceof String propertyName) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, propertyName);
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop duplicate excludeList
                } else if (!prop.isComputed() && propertyKey instanceof Literal literal
                        && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, String.valueOf(((Number) literal.getValue()).longValue()));
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop duplicate excludeList
                } else if (!prop.isComputed() && propertyKey instanceof Literal literal
                        && literal.getValue() instanceof BigInteger bigIntegerValue) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, bigIntegerValue.toString());
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop duplicate excludeList
                } else {
                    // Computed property key: re-evaluate expression to get the key name
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null
                    compilerContext.expressionCompiler.compile(propertyKey);
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null value
                }
                // DEFINE_FIELD leaves excludeList on stack, so normalize both paths to:
                // Stack: [source, excludeList]
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                // Stack: [excludeList, source]
            }
        }

        if (hasRest) {
            // Compile rest element: {...rest} = source
            if (objPattern.getProperties().isEmpty()) {
                // No properties to exclude, just copy all
                // Stack: [source]
                compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                // Stack: [source, target]
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                // Stack: [source, target, target]
                compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                // Stack: [target, target, source]
                compilerContext.emitter.emitOpcode(Opcode.NULL);
                // Stack: [target, target, source, null(excludeList)]
                compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 7);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null
                compilerContext.emitter.emitOpcode(Opcode.DROP); // drop source
                // Stack: [target, target]
            } else {
                // Stack: [excludeList, source]
                compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                // Stack: [excludeList, source, target]
                compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 68);
                // Stack: [excludeList, source, target]
            }
            // Assign target (TOS) to the rest pattern
            compilerContext.patternCompiler.compile(objPattern.getRestElement().getArgument());
            if (objPattern.getProperties().isEmpty()) {
                // Drop extra target preserved for COPY_DATA_PROPERTIES.
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                // Stack: [excludeList, source]
                compilerContext.emitter.emitOpcode(Opcode.DROP); // drop source
                compilerContext.emitter.emitOpcode(Opcode.DROP); // drop excludeList
            }
        } else {
            // Drop the original object
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }

    private Identifier getBindingIdentifierForPreResolve(Pattern pattern) {
        if (pattern instanceof Identifier identifier) {
            return identifier;
        }
        if (pattern instanceof AssignmentPattern assignmentPattern
                && assignmentPattern.getLeft() instanceof Identifier identifier) {
            return identifier;
        }
        return null;
    }

    private void maybePreResolveBindingIdentifierReference(Identifier bindingIdentifier) {
        if (!compilerContext.useExistingBindingInParentScopes || bindingIdentifier == null) {
            return;
        }

        if (!compilerContext.withObjectManager.hasActiveWithObject()) {
            compilerContext.identifierCompiler.compile(bindingIdentifier);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            return;
        }

        String bindingName = bindingIdentifier.getName();
        compilerContext.assignmentExpressionCompiler.emitIdentifierReference(bindingName);

        int propertyLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                "$preResolvedRefProp" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, propertyLocalIndex);

        int objectLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                "$preResolvedRefObj" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, objectLocalIndex);

        compilerContext.preResolvedBindingReferences
                .computeIfAbsent(bindingName, ignored -> new ArrayDeque<>())
                .addLast(new CompilerContext.PreResolvedReference(objectLocalIndex, propertyLocalIndex));
    }
}
