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
            String atom = ObjectDestructuringCompiler.prepareRead(compilerContext, prop, -1);
            maybePreResolveBindingIdentifierReference(getBindingIdentifierForPreResolve(prop.getValue()));
            ObjectDestructuringCompiler.read(compilerContext.emitter, atom);
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
                String name = ObjectDestructuringCompiler.staticKey(prop);
                if (name != null) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, name);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                } else {
                    // Computed property key: re-evaluate expression to get the key name
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null
                    compilerContext.expressionCompiler.compile(prop.getKey());
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
            boolean hasExclusions = !objPattern.getProperties().isEmpty();
            ObjectDestructuringCompiler.copyRest(compilerContext.emitter, hasExclusions);
            compilerContext.patternCompiler.compile(objPattern.getRestElement().getArgument());
            ObjectDestructuringCompiler.finishRest(compilerContext.emitter, hasExclusions);
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

        int propertyLocalIndex = compilerContext.scopeManager.currentScope()
                .declareLocal("#preResolvedRefProp" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, propertyLocalIndex);

        int objectLocalIndex = compilerContext.scopeManager.currentScope()
                .declareLocal("#preResolvedRefObj" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, objectLocalIndex);

        compilerContext.preResolvedBindingReferences.computeIfAbsent(bindingName, ignored -> new ArrayDeque<>())
                .addLast(new CompilerContext.PreResolvedReference(objectLocalIndex, propertyLocalIndex));
    }
}
