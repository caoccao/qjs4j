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
 * Delegate compiler for static analysis and hoisting operations.
 * Handles variable collection, pattern binding name extraction, Annex B function hoisting,
 * and global program binding registration.
 */
final class CompilerAnalysis {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;

    CompilerAnalysis(CompilerContext compilerContext, CompilerDelegates delegates) {
        this.compilerContext = compilerContext;
        this.delegates = delegates;
    }

    void collectLexicalBindings(List<Statement> body, Set<String> lexicals) {
        for (Statement statement : body) {
            if (statement instanceof VariableDeclaration variableDeclaration && variableDeclaration.getKind() != VariableKind.VAR) {
                for (VariableDeclarator variableDeclarator : variableDeclaration.getDeclarations()) {
                    collectPatternBindingNames(variableDeclarator.getId(), lexicals);
                }
            }
        }
    }

    void collectPatternBindingNames(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier id) {
            names.add(id.getName());
        } else if (pattern instanceof ArrayPattern arrPattern) {
            for (Pattern element : arrPattern.getElements()) {
                if (element != null) {
                    collectPatternBindingNames(element, names);
                }
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            for (ObjectPatternProperty prop : objPattern.getProperties()) {
                collectPatternBindingNames(prop.getValue(), names);
            }
            if (objPattern.getRestElement() != null) {
                collectPatternBindingNames(objPattern.getRestElement().getArgument(), names);
            }
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            collectPatternBindingNames(assignPattern.getLeft(), names);
        } else if (pattern instanceof RestElement restElement) {
            collectPatternBindingNames(restElement.getArgument(), names);
        }
    }

    /**
     * Recursively collect all var-declared names from a statement tree.
     * var declarations are function/global-scoped, so they must be hoisted
     * out of any block nesting (for, try, if, switch, etc.).
     * Does NOT recurse into function declarations/expressions (they have their own scope).
     */
    void collectVarNamesFromStatement(Statement stmt, Set<String> varNames) {
        if (stmt instanceof VariableDeclaration varDecl && varDecl.getKind() == VariableKind.VAR) {
            for (VariableDeclarator d : varDecl.getDeclarations()) {
                collectPatternBindingNames(d.getId(), varNames);
            }
        } else if (stmt instanceof ForStatement forStmt) {
            if (forStmt.getInit() instanceof VariableDeclaration varDecl && varDecl.getKind() == VariableKind.VAR) {
                for (VariableDeclarator d : varDecl.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), varNames);
                }
            }
            if (forStmt.getBody() != null) {
                collectVarNamesFromStatement(forStmt.getBody(), varNames);
            }
        } else if (stmt instanceof ForInStatement forInStmt) {
            if (forInStmt.getLeft() instanceof VariableDeclaration varDecl && varDecl.getKind() == VariableKind.VAR) {
                for (VariableDeclarator d : varDecl.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), varNames);
                }
            }
            if (forInStmt.getBody() != null) {
                collectVarNamesFromStatement(forInStmt.getBody(), varNames);
            }
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            if (forOfStmt.getLeft() instanceof VariableDeclaration varDecl && varDecl.getKind() == VariableKind.VAR) {
                for (VariableDeclarator d : varDecl.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), varNames);
                }
            }
            if (forOfStmt.getBody() != null) {
                collectVarNamesFromStatement(forOfStmt.getBody(), varNames);
            }
        } else if (stmt instanceof BlockStatement block) {
            for (Statement s : block.getBody()) {
                collectVarNamesFromStatement(s, varNames);
            }
        } else if (stmt instanceof IfStatement ifStmt) {
            collectVarNamesFromStatement(ifStmt.getConsequent(), varNames);
            if (ifStmt.getAlternate() != null) {
                collectVarNamesFromStatement(ifStmt.getAlternate(), varNames);
            }
        } else if (stmt instanceof WhileStatement whileStmt) {
            collectVarNamesFromStatement(whileStmt.getBody(), varNames);
        } else if (stmt instanceof DoWhileStatement doWhileStmt) {
            collectVarNamesFromStatement(doWhileStmt.getBody(), varNames);
        } else if (stmt instanceof TryStatement tryStmt) {
            for (Statement s : tryStmt.getBlock().getBody()) {
                collectVarNamesFromStatement(s, varNames);
            }
            if (tryStmt.getHandler() != null) {
                for (Statement s : tryStmt.getHandler().getBody().getBody()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
            if (tryStmt.getFinalizer() != null) {
                for (Statement s : tryStmt.getFinalizer().getBody()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            for (SwitchStatement.SwitchCase sc : switchStmt.getCases()) {
                for (Statement s : sc.getConsequent()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
        } else if (stmt instanceof WithStatement withStmt) {
            if (withStmt.getBody() != null) {
                collectVarNamesFromStatement(withStmt.getBody(), varNames);
            }
        } else if (stmt instanceof LabeledStatement labeledStmt) {
            collectVarNamesFromStatement(labeledStmt.getBody(), varNames);
        }
    }

    /**
     * Pre-declare all variable and function declaration names as locals in the current scope
     * in a single pass over the function body.
     * <p>
     * This ensures bindings are visible during Phase 1 (function declaration hoisting),
     * so nested function declarations can properly capture outer variables via VarRef,
     * and sibling function declarations are visible to closure capture resolution.
     * <p>
     * Handles:
     * - Function declarations: top-level names declared as locals
     * - var declarations: function-scoped, recurse into blocks (they hoist)
     * - let/const declarations: block-scoped, only top-level of function body
     * (they don't hoist into nested blocks but ARE in scope at function level)
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
                if (vd.getKind() == VariableKind.CONST
                        || vd.getKind() == VariableKind.USING
                        || vd.getKind() == VariableKind.AWAIT_USING) {
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
     * Pre-declare top-level function declaration names as locals in the current function scope
     * before compiling any hoisted function declarations.
     * <p>
     * This ensures sibling function declarations are visible to closure capture resolution while
     * compiling earlier hoisted functions (e.g. function A capturing function B declared later in
     * the same function body).
     */

    /**
     * Annex B.3.3.1: Hoist eligible function declarations from blocks/if-statements
     * to the function scope as var bindings (initialized to undefined).
     *
     * @param body           the function body statements
     * @param parameterNames the set of parameter names (BoundNames of argumentsList),
     *                       including "arguments" when the function has an implicit arguments binding
     */
    void hoistFunctionBodyAnnexBDeclarations(List<Statement> body, Set<String> parameterNames) {
        if (compilerContext.strictMode) {
            return; // Annex B does not apply in strict mode
        }

        // Single pass: collect lexical bindings, Annex B candidates, and already-declared names
        Set<String> topLevelLexicals = new HashSet<>();
        Set<String> candidates = new HashSet<>();
        Set<String> alreadyDeclared = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof VariableDeclaration vd && vd.getKind() != VariableKind.VAR) {
                for (VariableDeclarator d : vd.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), topLevelLexicals);
                }
            }
            if (stmt instanceof FunctionDeclaration fd && fd.getId() != null) {
                alreadyDeclared.add(fd.getId().getName());
            } else {
                collectVarNamesFromStatement(stmt, alreadyDeclared);
            }
            scanAnnexBStatement(stmt, topLevelLexicals, candidates);
        }

        if (candidates.isEmpty()) {
            return;
        }

        for (String name : candidates) {
            // Per B.3.3.1 step ii: skip if F is an element of BoundNames of argumentsList
            // (including the implicit "arguments" binding).
            // QuickJS: !((func_idx = find_var(fd, func_name)) >= 0 && (func_idx & ARGUMENT_VAR_OFFSET))
            //       && !(func_name == JS_ATOM_arguments && fd->has_arguments_binding)
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
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            }
        }
    }

    void scanAnnexBBlock(List<Statement> body, Set<String> parentLexicals, Set<String> result) {
        // Single pass: collect lexical bindings and function names, then scan for Annex B candidates
        Set<String> blockLexicals = new HashSet<>(parentLexicals);
        Set<String> blockFuncNames = new HashSet<>();
        for (Statement s : body) {
            if (s instanceof VariableDeclaration vd && vd.getKind() != VariableKind.VAR) {
                for (VariableDeclarator d : vd.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), blockLexicals);
                }
            } else if (s instanceof FunctionDeclaration fd && fd.getId() != null) {
                blockFuncNames.add(fd.getId().getName());
            }
        }
        // Per B.3.3.1: block-scoped function declarations create lexical bindings.
        // Include them so nested blocks see them as lexical conflicts.
        Set<String> blockLexicalsWithFuncs = new HashSet<>(blockLexicals);
        blockLexicalsWithFuncs.addAll(blockFuncNames);
        for (Statement s : body) {
            if (s instanceof FunctionDeclaration fd && fd.getId() != null) {
                if (fd.isAnnexBSimpleDeclaration() && !blockLexicals.contains(fd.getId().getName())) {
                    result.add(fd.getId().getName());
                }
            }
            scanAnnexBStatement(s, blockLexicalsWithFuncs, result);
        }
    }

    void scanAnnexBFunctions(List<Statement> programBody, Set<String> declaredFuncVarNames) {
        if (compilerContext.strictMode) {
            return; // Annex B does not apply in strict mode
        }
        // Single pass: collect top-level lexical bindings and scan for Annex B candidates.
        // Per B.3.3.3 step ii, if replacing the function declaration with "var F"
        // would produce an early error (conflict with let/const), the extension is skipped.
        Set<String> topLevelLexicals = new HashSet<>();
        Set<String> candidates = new HashSet<>();
        for (Statement stmt : programBody) {
            if (stmt instanceof VariableDeclaration vd && vd.getKind() != VariableKind.VAR) {
                for (VariableDeclarator d : vd.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), topLevelLexicals);
                }
            }
            scanAnnexBStatement(stmt, topLevelLexicals, candidates);
        }
        for (String name : candidates) {
            compilerContext.annexBFunctionNames.add(name);
            if (!declaredFuncVarNames.contains(name)) {
                // Create initial var binding only if the property doesn't already exist
                // on the global object (ES2024 CreateGlobalVarBinding semantics).
                delegates.emitHelpers.emitConditionalVarInit(name);
            }
        }
    }

    /**
     * Recursively scan a statement for Annex B eligible function declarations.
     * A function declaration is Annex B eligible if it appears inside a block, if-statement,
     * catch clause, or switch case (not at the top level of the program).
     * The early error check prevents hoisting when a let/const with the same name
     * exists in the same block scope.
     */
    void scanAnnexBStatement(Statement stmt, Set<String> lexicalBindings, Set<String> result) {
        if (stmt instanceof BlockStatement block) {
            scanAnnexBBlock(block.getBody(), lexicalBindings, result);
        } else if (stmt instanceof IfStatement ifStmt) {
            if (ifStmt.getConsequent() instanceof FunctionDeclaration fd && fd.getId() != null) {
                if (fd.isAnnexBSimpleDeclaration() && !lexicalBindings.contains(fd.getId().getName())) {
                    result.add(fd.getId().getName());
                }
            } else {
                scanAnnexBStatement(ifStmt.getConsequent(), lexicalBindings, result);
            }
            if (ifStmt.getAlternate() != null) {
                if (ifStmt.getAlternate() instanceof FunctionDeclaration fd && fd.getId() != null) {
                    if (fd.isAnnexBSimpleDeclaration() && !lexicalBindings.contains(fd.getId().getName())) {
                        result.add(fd.getId().getName());
                    }
                } else {
                    scanAnnexBStatement(ifStmt.getAlternate(), lexicalBindings, result);
                }
            }
        } else if (stmt instanceof TryStatement tryStmt) {
            scanAnnexBBlock(tryStmt.getBlock().getBody(), lexicalBindings, result);
            if (tryStmt.getHandler() != null) {
                // Per B.3.5, simple catch parameter (catch(e)) does NOT block Annex B var hoisting.
                // But destructuring catch parameter (catch({ f })) creates let-like bindings
                // that DO block hoisting (following QuickJS: destructuring uses TOK_LET).
                Set<String> catchLexicals = new HashSet<>(lexicalBindings);
                Pattern catchParam = tryStmt.getHandler().getParam();
                if (catchParam != null && !(catchParam instanceof Identifier)) {
                    // Destructuring pattern: collect binding names as lexical blockers
                    collectPatternBindingNames(catchParam, catchLexicals);
                }
                scanAnnexBBlock(tryStmt.getHandler().getBody().getBody(), catchLexicals, result);
            }
            if (tryStmt.getFinalizer() != null) {
                scanAnnexBBlock(tryStmt.getFinalizer().getBody(), lexicalBindings, result);
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            // Collect lexical bindings across all cases (switch shares one scope)
            Set<String> switchLexicals = new HashSet<>(lexicalBindings);
            for (SwitchStatement.SwitchCase sc : switchStmt.getCases()) {
                collectLexicalBindings(sc.getConsequent(), switchLexicals);
            }
            for (SwitchStatement.SwitchCase sc : switchStmt.getCases()) {
                for (Statement s : sc.getConsequent()) {
                    if (s instanceof FunctionDeclaration fd && fd.getId() != null) {
                        if (fd.isAnnexBSimpleDeclaration() && !switchLexicals.contains(fd.getId().getName())) {
                            result.add(fd.getId().getName());
                        }
                    }
                    scanAnnexBStatement(s, switchLexicals, result);
                }
            }
        } else if (stmt instanceof ForStatement forStmt) {
            if (forStmt.getBody() != null) {
                // Collect lexical bindings from the for-loop's init clause (e.g. "let f" in "for (let f; ; )")
                // Per B.3.3.3 step ii, if replacing the function declaration with "var F" would produce
                // an early error (conflict with let/const), the Annex B extension is skipped.
                Set<String> forLexicals = new HashSet<>(lexicalBindings);
                if (forStmt.getInit() instanceof VariableDeclaration vd && vd.getKind() != VariableKind.VAR) {
                    for (VariableDeclarator d : vd.getDeclarations()) {
                        collectPatternBindingNames(d.getId(), forLexicals);
                    }
                }
                scanAnnexBStatement(forStmt.getBody(), forLexicals, result);
            }
        } else if (stmt instanceof WhileStatement whileStmt) {
            scanAnnexBStatement(whileStmt.getBody(), lexicalBindings, result);
        } else if (stmt instanceof DoWhileStatement doWhileStmt) {
            scanAnnexBStatement(doWhileStmt.getBody(), lexicalBindings, result);
        } else if (stmt instanceof ForInStatement forInStmt) {
            Set<String> forInLexicals = new HashSet<>(lexicalBindings);
            if (forInStmt.getLeft() instanceof VariableDeclaration varDecl && varDecl.getKind() != VariableKind.VAR) {
                for (VariableDeclarator d : varDecl.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), forInLexicals);
                }
            }
            scanAnnexBStatement(forInStmt.getBody(), forInLexicals, result);
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            Set<String> forOfLexicals = new HashSet<>(lexicalBindings);
            if (forOfStmt.getLeft() instanceof VariableDeclaration varDecl && varDecl.getKind() != VariableKind.VAR) {
                for (VariableDeclarator d : varDecl.getDeclarations()) {
                    collectPatternBindingNames(d.getId(), forOfLexicals);
                }
            }
            scanAnnexBStatement(forOfStmt.getBody(), forOfLexicals, result);
        }
    }
}
