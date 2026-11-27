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
import java.util.List;
import java.util.Set;

/**
 * Delegate compiler for static analysis and hoisting operations. Handles variable collection, pattern binding name
 * extraction, Annex B function hoisting, and global program binding registration.
 */
final class CompilerAnalysis {
    private final CompilerContext compilerContext;

    CompilerAnalysis(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    private Set<String> annexBCandidates(List<Statement> body) {
        // Single pass: collect top-level lexical bindings and scan for Annex B candidates.
        // Per B.3.3.3 step ii, if replacing the function declaration with "var F"
        // would produce an early error (conflict with let/const), the extension is skipped.
        Set<String> topLevelLexicals = new HashSet<>();
        Set<String> candidates = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof VariableDeclaration vd && vd.getKind() != VariableKind.VAR) {
                for (VariableDeclarator d : vd.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), topLevelLexicals);
                }
            }
            AnnexBFunctions.HOISTING.scan(stmt, topLevelLexicals, candidates);
        }
        return candidates;
    }

    /**
     * Whether a function body declares a binding with the given name.
     * <p>
     * Used to decide whether a named function expression's own name binding is shadowed. Per ES2024 15.2.5 the name
     * lives in a separate environment that wraps the function's variable environment, so a {@code var}, {@code let},
     * {@code const}, {@code class} or {@code function} declaration of the same name in the body creates a
     * <em>different</em> binding that hides it.
     *
     * @param body
     *            the function body statements
     * @param name
     *            the name to look for
     * @return true when the body declares that name
     */
    boolean bodyDeclaresBinding(List<Statement> body, String name) {
        Set<String> declaredNames = new HashSet<>();
        for (Statement statement : body) {
            if (statement instanceof FunctionDeclaration functionDeclaration) {
                if (functionDeclaration.getId() != null) {
                    declaredNames.add(functionDeclaration.getId().getName());
                }
                continue;
            }
            collectVarNamesFromStatement(statement, declaredNames);
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() != VariableKind.VAR) {
                for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternBindingNames(declarator.getId(), declaredNames);
                }
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                declaredNames.add(classDeclaration.getId().getName());
            }
        }
        return declaredNames.contains(name);
    }

    /**
     * Pre-declare all variable and function declaration names as locals in the current scope in a single pass over the
     * function body.
     * <p>
     * This ensures bindings are visible during Phase 1 (function declaration hoisting), so nested function declarations
     * can properly capture outer variables via VarRef, and sibling function declarations are visible to closure capture
     * resolution.
     * <p>
     * Handles: - Function declarations: top-level names declared as locals - var declarations: function-scoped, recurse
     * into blocks (they hoist) - let/const declarations: block-scoped, only top-level of function body (they don't
     * hoist into nested blocks but ARE in scope at function level)
     */

    void collectPatternBindingNames(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier identifier) {
            names.add(identifier.getName());
        } else if (pattern != null) {
            names.addAll(pattern.getBoundNames());
        }
    }

    /**
     * Recursively collect all var-declared names from a statement tree. var declarations are function/global-scoped, so
     * they must be hoisted out of any block nesting (for, try, if, switch, etc.). Does NOT recurse into function
     * declarations/expressions (they have their own scope).
     */
    void collectVarNamesFromStatement(Statement stmt, Set<String> varNames) {
        if (stmt == null) {
            return;
        }
        for (VariableDeclarator declarator : stmt.getVarDeclarators()) {
            collectPatternBindingNames(declarator.getId(), varNames);
        }
    }

    /**
     * Pre-declare top-level function declaration names as locals in the current function scope before compiling any
     * hoisted function declarations.
     * <p>
     * This ensures sibling function declarations are visible to closure capture resolution while compiling earlier
     * hoisted functions (e.g. function A capturing function B declared later in the same function body).
     */

    void hoistAllDeclarationsAsLocals(List<Statement> body) {
        Set<String> varNames = new HashSet<>();
        Set<String> lexicalNames = new HashSet<>();
        Set<String> constLexicalNames = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof FunctionDeclaration functionDeclaration) {
                if (functionDeclaration.getId() != null) {
                    String functionName = functionDeclaration.getId().getName();
                    if (compilerContext.scopeManager.currentScope().getLocal(functionName) == null) {
                        compilerContext.scopeManager.currentScope().declareLocal(functionName);
                    }
                }
                continue;
            }
            // Collect var names (recurses into blocks since var is function-scoped)
            collectVarNamesFromStatement(stmt, varNames);
            // Collect top-level let/const names (don't recurse — they're block-scoped)
            if (stmt instanceof VariableDeclaration vd && vd.getKind() != VariableKind.VAR) {
                Set<String> declarationNames = new HashSet<>();
                for (VariableDeclarator d : vd.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), declarationNames);
                }
                lexicalNames.addAll(declarationNames);
                if (vd.getKind().isImmutable()) {
                    constLexicalNames.addAll(declarationNames);
                }
            } else if (stmt instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                lexicalNames.add(classDeclaration.getId().getName());
            }
        }
        for (String varName : varNames) {
            if (compilerContext.scopeManager.currentScope().getLocal(varName) == null) {
                compilerContext.scopeManager.currentScope().declareLocal(varName);
            }
        }
        for (String lexicalName : lexicalNames) {
            Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(lexicalName);
            if (localIndex == null) {
                localIndex = compilerContext.scopeManager.currentScope().declareLocal(lexicalName);
            }
            if (constLexicalNames.contains(lexicalName)) {
                compilerContext.scopeManager.currentScope().markConstLocal(lexicalName);
            }
            compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
            compilerContext.tdzLocals.add(lexicalName);
        }
    }

    /**
     * Annex B.3.3.1: Hoist eligible function declarations from blocks/if-statements to the function scope as var
     * bindings (initialized to undefined).
     *
     * @param body
     *            the function body statements
     * @param parameterNames
     *            the set of parameter names (BoundNames of argumentsList), including "arguments" when the function has
     *            an implicit arguments binding
     */
    void hoistFunctionBodyAnnexBDeclarations(List<Statement> body, Set<String> parameterNames) {
        if (compilerContext.strictMode) {
            return; // Annex B does not apply in strict mode
        }

        Set<String> candidates = annexBCandidates(body);
        if (candidates.isEmpty()) {
            return;
        }
        Set<String> alreadyDeclared = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof FunctionDeclaration fd && fd.getId() != null) {
                alreadyDeclared.add(fd.getId().getName());
            } else {
                collectVarNamesFromStatement(stmt, alreadyDeclared);
            }
        }

        for (String name : candidates) {
            // Per B.3.3.1 step ii: skip if F is an element of BoundNames of argumentsList
            // (including the implicit "arguments" binding).
            // QuickJS: !((func_idx = find_var(fd, func_name)) >= 0 && (func_idx & ARGUMENT_VAR_OFFSET))
            // && !(func_name == JS_ATOM_arguments && fd->has_arguments_binding)
            if (parameterNames.contains(name)) {
                continue;
            }
            compilerContext.annexBFunctionNames.add(name);
            Integer existingLocal = compilerContext.scopeManager.findLocalInScopes(name);
            if (existingLocal != null) {
                // Already exists (e.g., explicit var declaration)
                compilerContext.annexBFunctionScopeLocals.put(name, existingLocal);
            } else if (!alreadyDeclared.contains(name)) {
                // Create new local in function scope, initialized to undefined
                int localIndex = compilerContext.scopeManager.currentScope().declareLocal(name);
                compilerContext.annexBFunctionScopeLocals.put(name, localIndex);
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                compilerContext.emitter.emitPutLocal(localIndex);
            }
        }
    }

    void scanAnnexBFunctions(List<Statement> programBody, Set<String> declaredFuncVarNames) {
        if (compilerContext.strictMode) {
            return; // Annex B does not apply in strict mode
        }
        for (String name : annexBCandidates(programBody)) {
            compilerContext.annexBFunctionNames.add(name);
            if (!declaredFuncVarNames.contains(name)) {
                // Create initial var binding only if the property doesn't already exist
                // on the global object (ES2024 CreateGlobalVarBinding semantics).
                compilerContext.emitHelpers.emitConditionalVarInit(name);
            }
        }
    }
}
