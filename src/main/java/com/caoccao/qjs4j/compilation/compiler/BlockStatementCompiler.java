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

final class BlockStatementCompiler {
    private final CompilerContext compilerContext;

    BlockStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(BlockStatement block) {
        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;
        // Phase 1: pre-declare lexical bindings for TDZ before compiling any hoisted functions.
        for (Statement stmt : block.getBody()) {
            if (stmt instanceof VariableDeclaration vd && vd.getKind() != VariableKind.VAR) {
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
        for (Statement stmt : block.getBody()) {
            if (stmt instanceof FunctionDeclaration funcDecl) {
                compilerContext.functionDeclarationCompiler.compileFunctionDeclaration(funcDecl);
            }
        }

        // Phase 3: compile non-function statements in source order.
        for (Statement stmt : block.getBody()) {
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted
            }
            compilerContext.statementCompiler.compileStatement(stmt);
        }
        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
    }
}
