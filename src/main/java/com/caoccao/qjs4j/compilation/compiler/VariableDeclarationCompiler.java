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
    private final CompilerContext compilerContext;

    VariableDeclarationCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(VariableDeclaration varDecl) {
        boolean isUsingDeclaration = varDecl.getKind() == VariableKind.USING || varDecl.getKind() == VariableKind.AWAIT_USING;
        boolean isAwaitUsingDeclaration = varDecl.getKind() == VariableKind.AWAIT_USING;
        if ((varDecl.getKind() == VariableKind.CONST || isUsingDeclaration)
                && !compilerContext.inGlobalScope
                && !compilerContext.varInGlobalProgram) {
            for (VariableDeclarator declarator : varDecl.getDeclarations()) {
                Set<String> constNames = new HashSet<>();
                compilerContext.compilerAnalysis.collectPatternBindingNames(declarator.getId(), constNames);
                for (String constName : constNames) {
                    compilerContext.scopeManager.currentScope().declareConstLocal(constName);
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
            if (compilerContext.inGlobalScope || compilerContext.varInGlobalProgram) {
                compilerContext.compilerAnalysis.collectPatternBindingNames(declarator.getId(), compilerContext.nonDeletableGlobalBindings);
            }
            if (isUsingDeclaration) {
                if (declarator.getInit() == null) {
                    throw new JSCompilerException(varDecl.getKind() + " declaration requires an initializer");
                }

                compilerContext.expressionCompiler.compile(declarator.getInit());
                int usingStackLocalIndex = compilerContext.emitHelpers.ensureUsingStackLocal(isAwaitUsingDeclaration);
                compilerContext.emitHelpers.emitMethodCallWithSingleArgOnLocalObject(usingStackLocalIndex, "use");
                compilerContext.patternCompiler.compilePatternAssignment(declarator.getId());
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
                    compilerContext.patternCompiler.compileVarPatternAssignment(declarator.getId());
                    continue;
                }
                if (!(declarator.getId() instanceof Identifier)) {
                    throw new JSCompilerException("Missing initializer in destructuring declaration");
                }
                continue;
            }

            if (varDecl.getKind() == VariableKind.VAR
                    && declarator.getId() instanceof Identifier identifier
                    && declarator.getInit() != null
                    && compilerContext.withObjectManager.hasActiveWithObject()) {
                // Resolve binding before evaluating initializer (ES VariableDeclaration semantics).
                compilerContext.assignmentExpressionCompiler.emitIdentifierReference(identifier.getName());
                int preResolvedPropertyLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                        "$preResolvedVarProperty_" + compilerContext.emitter.currentOffset());
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, preResolvedPropertyLocalIndex);
                int preResolvedObjectLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                        "$preResolvedVarObject_" + compilerContext.emitter.currentOffset());
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, preResolvedObjectLocalIndex);

                compilerContext.expressionCompiler.compile(declarator.getInit());
                if (declarator.getInit().isAnonymousFunction()) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, identifier.getName());
                }
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, preResolvedObjectLocalIndex);
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, preResolvedPropertyLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
                continue;
            }

            // Compile initializer or push undefined
            if (declarator.getInit() != null) {
                // Pass inferred name to anonymous class expressions for NamedEvaluation
                if (declarator.getId() instanceof Identifier targetId
                        && declarator.getInit() instanceof ClassExpression classExpr
                        && classExpr.getId() == null) {
                    compilerContext.inferredClassName = targetId.getName();
                }
                compilerContext.expressionCompiler.compile(declarator.getInit());
                if (declarator.getId() instanceof Identifier targetId
                        && declarator.getInit().isAnonymousFunction()) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, targetId.getName());
                }
                compilerContext.inferredClassName = null;
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            // Assign to pattern (handles Identifier, ObjectPattern, ArrayPattern)
            if (varDecl.getKind() == VariableKind.VAR) {
                compilerContext.patternCompiler.compileVarPatternAssignment(declarator.getId());
            } else {
                compilerContext.patternCompiler.compilePatternAssignment(declarator.getId());
            }
        }
        compilerContext.popState();
    }
}
