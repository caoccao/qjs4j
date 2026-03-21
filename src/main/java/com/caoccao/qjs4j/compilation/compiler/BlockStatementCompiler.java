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

import java.util.HashSet;
import java.util.Set;

final class BlockStatementCompiler extends AstNodeCompiler<BlockStatement> {
    BlockStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(BlockStatement block) {
        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;

        boolean hasUsingDeclarations = false;

        // Phase 1: pre-declare lexical bindings for TDZ before compiling any hoisted functions.
        for (Statement stmt : block.getBody()) {
            if (stmt instanceof VariableDeclaration vd && vd.getKind() != VariableKind.VAR) {
                if (vd.getKind() == VariableKind.USING || vd.getKind() == VariableKind.AWAIT_USING) {
                    hasUsingDeclarations = true;
                }
                for (VariableDeclarator d : vd.getDeclarations()) {
                    Set<String> names = new HashSet<>();
                    compilerContext.compilerAnalysis.collectPatternBindingNames(d.getId(), names);
                    for (String name : names) {
                        Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(name);
                        if (localIndex == null) {
                            localIndex = compilerContext.scopeManager.currentScope().declareLocal(name);
                        }
                        if (vd.getKind() == VariableKind.CONST
                                || vd.getKind() == VariableKind.USING
                                || vd.getKind() == VariableKind.AWAIT_USING) {
                            compilerContext.scopeManager.currentScope().markConstLocal(name);
                        }
                        compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                        compilerContext.tdzLocals.add(name);
                    }
                }
            } else if (stmt instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                String className = classDeclaration.getId().getName();
                Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(className);
                if (localIndex == null) {
                    localIndex = compilerContext.scopeManager.currentScope().declareLocal(className);
                }
                compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                compilerContext.tdzLocals.add(className);
            }
        }

        // Phase 2: hoist function declarations after lexical bindings exist,
        // so nested function bodies resolve captures against block-scoped names.
        // Also handles labeled function declarations (e.g., "l: function f() {}")
        // per Annex B.3.2.
        // Suppress Annex B var store during hoisting — it's deferred to the source position.
        compilerContext.suppressAnnexBVarStore = true;
        for (Statement stmt : block.getBody()) {
            FunctionDeclaration funcDecl = stmt.unwrapLabeledFunctionDeclaration();
            if (funcDecl != null) {
                compilerContext.functionDeclarationCompiler.compile(funcDecl);
            }
        }
        compilerContext.suppressAnnexBVarStore = false;

        // Set up CATCH handler for exception-safe using disposal.
        // The CATCH is emitted BEFORE Phase 3 so the CatchOffset is below all
        // statement values. Each statement is net-zero stack effect, so the
        // CatchOffset stays at the bottom.
        int usingCatchJump = -1;
        if (hasUsingDeclarations) {
            usingCatchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
            compilerContext.scopeManager.currentScope().setUsingCatchJumpPosition(usingCatchJump);
        }

        // Phase 3: compile non-function statements in source order.
        // For function declarations, emit the Annex B var store at the source position
        // (the function itself was already hoisted in Phase 2).
        boolean savedIsLastInProgram = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        for (Statement stmt : block.getBody()) {
            FunctionDeclaration hoistedFunction = stmt.unwrapLabeledFunctionDeclaration();
            if (hoistedFunction != null) {
                // Per Annex B.3.3: emit the var store at the source position
                compilerContext.emitHelpers.emitDeferredAnnexBVarStore(hoistedFunction);
                continue;
            }
            compilerContext.statementCompiler.compile(stmt);
        }
        compilerContext.isLastInProgram = savedIsLastInProgram;

        if (hasUsingDeclarations) {
            // Normal path: remove CATCH handler, dispose, skip over exception handler
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
            int jumpOverCatch = compilerContext.emitter.emitJump(Opcode.GOTO);

            // Exception path: caught exception is on the stack
            compilerContext.emitter.patchJump(usingCatchJump, compilerContext.emitter.currentOffset());
            compilerContext.emitHelpers.emitScopeUsingDisposalWithException(compilerContext.scopeManager.currentScope());

            compilerContext.emitter.patchJump(jumpOverCatch, compilerContext.emitter.currentOffset());
        } else {
            compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        }
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
    }
}
