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
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.HashSet;
import java.util.Set;

/**
 * Compiles variable declaration AST nodes into bytecode.
 */
final class VariableDeclarationCompiler {
    private VariableDeclarationCompiler() {
    }

    static void compile(CompilerContext compilerContext, VariableDeclaration varDecl) {
        boolean isUsingDeclaration = varDecl.getKind().isUsing();
        boolean isAwaitUsingDeclaration = varDecl.getKind() == VariableKind.AWAIT_USING;
        if ((varDecl.getKind() == VariableKind.CONST || varDecl.getKind() == VariableKind.LET || isUsingDeclaration)
                && !compilerContext.inGlobalScope && !compilerContext.varInGlobalProgram) {
            for (VariableDeclarator declarator : varDecl.getDeclarations()) {
                Set<String> bindingNames = new HashSet<>();
                CompilerAnalysis.collectPatternBindingNames(declarator.getId(), bindingNames);
                for (String bindingName : bindingNames) {
                    if (varDecl.getKind() == VariableKind.CONST || isUsingDeclaration) {
                        compilerContext.scopeManager.currentScope().declareConstLocal(bindingName);
                    } else {
                        compilerContext.scopeManager.currentScope().declareLocal(bindingName);
                    }
                }
            }
        }
        // Track whether this is a var declaration in global program scope
        // so compilePatternAssignment can use PUT_VAR for global-scoped vars.
        compilerContext.pushState();
        if (compilerContext.isGlobalProgram && varDecl.getKind() == VariableKind.VAR) {
            compilerContext.varInGlobalProgram = true;
        }
        for (VariableDeclarator declarator : varDecl.getDeclarations()) {
            if ((compilerContext.inGlobalScope || compilerContext.varInGlobalProgram) && !compilerContext.evalMode) {
                CompilerAnalysis.collectPatternBindingNames(declarator.getId(),
                        compilerContext.nonDeletableGlobalBindings);
            }
            if (isUsingDeclaration) {
                if (declarator.getInit() == null) {
                    throw new JSCompilerException(varDecl.getKind() + " declaration requires an initializer",
                            declarator);
                }

                compileInitializer(compilerContext, declarator);
                int usingStackLocalIndex = UsingScopeCompiler.ensureUsingStackLocal(compilerContext,
                        isAwaitUsingDeclaration, declarator);
                UsingScopeCompiler.emitRegisterResource(compilerContext, usingStackLocalIndex);
                compilerContext.compilePattern(declarator.getId());
                continue;
            }

            // var declarations without an initializer are handled during declaration
            // instantiation (binding remains unchanged if already initialized).
            if (varDecl.getKind() == VariableKind.VAR && declarator.getInit() == null) {
                if (declarator.getId() instanceof Identifier identifier
                        && compilerContext.scopeManager.isLocalBindingFunctionName(identifier.getName())) {
                    // Named function expressions create an immutable name binding in an outer
                    // function-name environment. A same-name `var` declaration in the body
                    // must initialize the function-scope binding to undefined.
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                    PatternCompiler.compileVarPatternAssignment(compilerContext, declarator.getId());
                    continue;
                }
                if (!(declarator.getId() instanceof Identifier)) {
                    throw new JSCompilerException("Missing initializer in destructuring declaration", declarator);
                }
                continue;
            }

            if (varDecl.getKind() == VariableKind.VAR && declarator.getId() instanceof Identifier identifier
                    && declarator.getInit() != null && compilerContext.withObjectManager.hasActiveWithObject()) {
                // Resolve binding before evaluating initializer (ES VariableDeclaration semantics).
                AssignmentExpressionCompiler.emitIdentifierReference(compilerContext, identifier.getName());
                int preResolvedPropertyLocalIndex = compilerContext.storeTemporary("#preResolvedVarProperty_");
                int preResolvedObjectLocalIndex = compilerContext.storeTemporary("#preResolvedVarObject_");

                ExpressionCompiler.compileNamed(compilerContext, declarator.getInit(), identifier.getName());
                compilerContext.emitter.emitGetLocal(preResolvedObjectLocalIndex);
                compilerContext.emitter.emitGetLocal(preResolvedPropertyLocalIndex);
                compilerContext.emitter.emitStoreValueBelowReference();
                continue;
            }

            // Compile initializer or push undefined
            if (declarator.getInit() != null) {
                compileInitializer(compilerContext, declarator);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            // Assign to pattern (handles Identifier, ObjectPattern, ArrayPattern)
            if (varDecl.getKind() == VariableKind.VAR) {
                PatternCompiler.compileVarPatternAssignment(compilerContext, declarator.getId());
            } else {
                compilerContext.compilePattern(declarator.getId());
            }
        }
        compilerContext.popState();
    }

    private static void compileInitializer(CompilerContext compilerContext, VariableDeclarator declarator) {
        // NamedEvaluation supplies the binding name while compiling an anonymous class.
        if (declarator.getId() instanceof Identifier targetId
                && declarator.getInit() instanceof ClassExpression classExpr && classExpr.getId() == null) {
            compilerContext.inferredClassName = targetId.getName();
        }
        compilerContext.compileExpression(declarator.getInit());
        if (declarator.getId() instanceof Identifier targetId && declarator.getInit().isAnonymousFunction()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, targetId.getName());
        }
        compilerContext.inferredClassName = null;
    }
}
