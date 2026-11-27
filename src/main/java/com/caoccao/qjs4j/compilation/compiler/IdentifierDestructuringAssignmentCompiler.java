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
import com.caoccao.qjs4j.core.JSArguments;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles Identifier destructuring assignment targets into bytecode.
 */
final class IdentifierDestructuringAssignmentCompiler extends AstNodeCompiler<Identifier> {

    IdentifierDestructuringAssignmentCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(Identifier id) {
        // Stack: [value]
        // Assign value to identifier and pop value from stack
        String name = id.getName();
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
        if (localIndex == null && JSArguments.NAME.equals(name)) {
            localIndex = ensureImplicitArgumentsLocalBinding();
        }
        if (localIndex != null) {
            if (compilerContext.scopeManager.isLocalBindingConst(name)) {
                compilerContext.emitHelpers.emitConstAssignmentErrorForLocal(name, localIndex);
                return;
            }
            if (compilerContext.tdzLocals.contains(name)) {
                emitter.emitIntOperand(Opcode.PUT_LOC_CHECK, localIndex);
            } else {
                emitter.emitPutLocal(localIndex);
            }
        } else {
            Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
            if (capturedIndex != null) {
                if (compilerContext.captureResolver.isCapturedBindingImmutable(name)) {
                    compilerContext.emitHelpers.emitConstAssignmentErrorForCaptured(name, capturedIndex);
                    return;
                }
                emitter.emitIntOperand(Opcode.PUT_VAR_REF_CHECK, capturedIndex);
            } else {
                emitter.emitOpcodeAtom(Opcode.MAKE_VAR_REF, name);
                emitter.emitStoreValueBelowReference();
            }
        }
    }

    private Integer ensureImplicitArgumentsLocalBinding() {
        if (!compilerContext.hasEnclosingArgumentsBinding) {
            return null;
        }
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(JSArguments.NAME);
        if (localIndex != null) {
            return localIndex;
        }
        CompilerScope currentScope = compilerContext.scopeManager.currentScope();
        if (currentScope == null) {
            return null;
        }
        return currentScope.declareLocal(JSArguments.NAME);
    }
}
