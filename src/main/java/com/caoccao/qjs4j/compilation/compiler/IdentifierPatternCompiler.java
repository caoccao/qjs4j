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

import com.caoccao.qjs4j.compilation.ast.Identifier;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.Deque;

/**
 * Compiles Identifier pattern assignments into bytecode.
 * Handles var/let/const declarations, global scope, TDZ, with-objects,
 * and pre-resolved binding references.
 */
final class IdentifierPatternCompiler extends AstNodeCompiler<Identifier> {

    IdentifierPatternCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(Identifier id) {
        // Stack: [value]
        // Assign value to identifier binding
        String varName = id.getName();
        if (emitAssignmentUsingPreResolvedBindingReference(varName)) {
            // Assignment consumed by a cached ResolveBinding reference.
        } else if (compilerContext.useExistingBindingInParentScopes
                && compilerContext.withObjectManager.hasActiveWithObject()) {
            compilerContext.assignmentExpressionCompiler.emitIdentifierReference(varName);
            compilerContext.emitter.emitOpcode(Opcode.ROT3L);
            compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
        } else if (compilerContext.inGlobalScope && compilerContext.tdzLocals.contains(varName)) {
            // TDZ local: let/const was pre-declared as a local for TDZ enforcement
            Integer tdzLocal = compilerContext.scopeManager.findLocalInScopes(varName);
            if (tdzLocal != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, tdzLocal);
            } else {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            }
        } else if (compilerContext.inGlobalScope) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
        } else if (compilerContext.varInGlobalProgram) {
            // var declaration in global program inside a block (for, try, if, etc.).
            // var is global-scoped, so use PUT_VAR — UNLESS the name is already
            // a local (e.g., catch parameter per ES B.3.5), in which case use PUT_LOCAL.
            Integer existingLocal = compilerContext.scopeManager.findLocalInScopes(varName);
            if (existingLocal != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, existingLocal);
            } else {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            }
        } else {
            Integer localIndex;
            if (compilerContext.useExistingBindingInParentScopes && compilerContext.varDeclarationScopeOverride != null) {
                CompilerScope varDeclarationScope = compilerContext.varDeclarationScopeOverride;
                localIndex = varDeclarationScope.getLocal(varName);
                if (localIndex == null) {
                    localIndex = varDeclarationScope.declareLocal(varName);
                }
            } else if (compilerContext.useExistingBindingInParentScopes) {
                localIndex = compilerContext.scopeManager.findLocalInScopes(varName);
                if (localIndex == null) {
                    localIndex = compilerContext.scopeManager.currentScope().declareLocal(varName);
                }
            } else {
                // let/const declarations are lexical. They should resolve only against
                // the current scope so block bindings shadow outer bindings.
                localIndex = compilerContext.scopeManager.currentScope().getLocal(varName);
                if (localIndex == null) {
                    localIndex = compilerContext.scopeManager.currentScope().declareLocal(varName);
                }
            }
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
        }
    }

    private boolean emitAssignmentUsingPreResolvedBindingReference(String variableName) {
        Deque<CompilerContext.PreResolvedReference> references = compilerContext.preResolvedBindingReferences.get(variableName);
        if (references == null || references.isEmpty()) {
            return false;
        }

        CompilerContext.PreResolvedReference reference = references.removeFirst();
        if (references.isEmpty()) {
            compilerContext.preResolvedBindingReferences.remove(variableName);
        }

        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, reference.objectLocalIndex());
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, reference.propertyLocalIndex());
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
        return true;
    }
}
