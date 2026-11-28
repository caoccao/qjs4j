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

import com.caoccao.qjs4j.compilation.ast.FunctionDeclaration;
import com.caoccao.qjs4j.core.JSArguments;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Handles compilation of function declarations.
 */
final class FunctionDeclarationCompiler {
    private FunctionDeclarationCompiler() {
    }

    static void compile(CompilerContext compilerContext, FunctionDeclaration funcDecl) {
        // Pre-declare function name as a local in the current scope (if non-global and not
        // already declared). This must happen BEFORE creating the child compiler so the
        // function body can capture the name via closure for self-reference.
        // Per QuickJS: function declarations inside blocks/switch cases create a lexical
        // binding in the current scope, even if a parent scope has the same name (e.g.,
        // a parameter). This prevents the function object from overwriting the parent binding.
        String functionName = funcDecl.getId().getName();
        if (!compilerContext.inGlobalScope) {
            if (compilerContext.scopeManager.currentScope().getLocal(functionName) == null) {
                compilerContext.scopeManager.currentScope().declareLocal(functionName);
            }
        }

        JSBytecodeFunction function = OrdinaryFunctionCompiler.compile(compilerContext, funcDecl, false);

        // Store the function in a variable with its name
        // Per B.3.3.1: the Annex B runtime hook only fires if no enclosing block
        // scope has a lexical binding for the same name (otherwise replacing this
        // function with var F would produce an Early Error).
        boolean isAnnexB = compilerContext.annexBFunctionNames.contains(functionName) && !funcDecl.isAsync()
                && !funcDecl.isGenerator() && !compilerContext.scopeManager.hasEnclosingBlockScopeLocal(functionName);
        if (isAnnexB && JSArguments.NAME.equals(functionName)) {
            function.setDisplaysAsArgumentsObjectInToString(true);
        }
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(functionName);
        if (localIndex == null && compilerContext.inGlobalScope) {
            if (!compilerContext.evalMode) {
                compilerContext.nonDeletableGlobalBindings.add(functionName);
            }
            compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            return;
        }
        if (localIndex == null) {
            localIndex = compilerContext.scopeManager.currentScope().declareLocal(functionName);
        }
        if (isAnnexB && !compilerContext.suppressAnnexBVarStore) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitPutLocal(localIndex);
            EmitHelpers.emitAnnexBVarStore(compilerContext, functionName);
        } else {
            compilerContext.emitter.emitPutLocal(localIndex);
        }
    }
}
