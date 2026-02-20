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
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;

    CompilerAnalysis(CompilerContext ctx, CompilerDelegates delegates) {
        this.ctx = ctx;
        this.delegates = delegates;
    }

    void collectLexicalBindings(List<Statement> body, Set<String> lexicals) {
        for (Statement s : body) {
            if (s instanceof VariableDeclaration vd && vd.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : vd.declarations()) {
                    collectPatternBindingNames(d.id(), lexicals);
                }
            }
        }
    }

    void collectPatternBindingNames(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier id) {
            names.add(id.name());
        } else if (pattern instanceof ArrayPattern arrPattern) {
            for (Pattern element : arrPattern.elements()) {
                if (element != null) {
                    collectPatternBindingNames(element, names);
                }
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            for (ObjectPattern.Property prop : objPattern.properties()) {
                collectPatternBindingNames(prop.value(), names);
            }
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            collectPatternBindingNames(assignPattern.left(), names);
        } else if (pattern instanceof RestElement restElement) {
            collectPatternBindingNames(restElement.argument(), names);
        }
    }

    /**
     * Recursively collect all var-declared names from a statement tree.
     * var declarations are function/global-scoped, so they must be hoisted
     * out of any block nesting (for, try, if, switch, etc.).
     * Does NOT recurse into function declarations/expressions (they have their own scope).
     */
    void collectVarNamesFromStatement(Statement stmt, Set<String> varNames) {
        if (stmt instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
            for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                collectPatternBindingNames(d.id(), varNames);
            }
        } else if (stmt instanceof ForStatement forStmt) {
            if (forStmt.init() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), varNames);
                }
            }
            if (forStmt.body() != null) {
                collectVarNamesFromStatement(forStmt.body(), varNames);
            }
        } else if (stmt instanceof ForInStatement forInStmt) {
            if (forInStmt.left() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), varNames);
                }
            }
            if (forInStmt.body() != null) {
                collectVarNamesFromStatement(forInStmt.body(), varNames);
            }
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            if (forOfStmt.left() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), varNames);
                }
            }
            if (forOfStmt.body() != null) {
                collectVarNamesFromStatement(forOfStmt.body(), varNames);
            }
        } else if (stmt instanceof BlockStatement block) {
            for (Statement s : block.body()) {
                collectVarNamesFromStatement(s, varNames);
            }
        } else if (stmt instanceof IfStatement ifStmt) {
            collectVarNamesFromStatement(ifStmt.consequent(), varNames);
            if (ifStmt.alternate() != null) {
                collectVarNamesFromStatement(ifStmt.alternate(), varNames);
            }
        } else if (stmt instanceof WhileStatement whileStmt) {
            collectVarNamesFromStatement(whileStmt.body(), varNames);
        } else if (stmt instanceof TryStatement tryStmt) {
            for (Statement s : tryStmt.block().body()) {
                collectVarNamesFromStatement(s, varNames);
            }
            if (tryStmt.handler() != null) {
                for (Statement s : tryStmt.handler().body().body()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
            if (tryStmt.finalizer() != null) {
                for (Statement s : tryStmt.finalizer().body()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                for (Statement s : sc.consequent()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
        } else if (stmt instanceof LabeledStatement labeledStmt) {
            collectVarNamesFromStatement(labeledStmt.body(), varNames);
        }
    }

    /**
     * Annex B.3.3.1: Hoist eligible function declarations from blocks/if-statements
     * to the function scope as var bindings (initialized to undefined).
     *
     * @param body           the function body statements
     * @param parameterNames the set of parameter names (BoundNames of argumentsList),
     *                       including "arguments" when the function has an implicit arguments binding
     */
    void hoistFunctionBodyAnnexBDeclarations(List<Statement> body, Set<String> parameterNames) {
        if (ctx.strictMode) {
            return; // Annex B does not apply in strict mode
        }

        // Collect top-level lexical bindings (let/const) from the function body.
        Set<String> topLevelLexicals = new HashSet<>();
        collectLexicalBindings(body, topLevelLexicals);

        // Scan for Annex B candidates in the function body
        Set<String> candidates = new HashSet<>();
        for (Statement stmt : body) {
            scanAnnexBStatement(stmt, topLevelLexicals, candidates);
        }

        if (candidates.isEmpty()) {
            return;
        }

        // Collect names that are already declared (explicit var, top-level functions)
        // to avoid creating duplicate bindings.
        Set<String> alreadyDeclared = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof FunctionDeclaration fd && fd.id() != null) {
                alreadyDeclared.add(fd.id().name());
            } else {
                collectVarNamesFromStatement(stmt, alreadyDeclared);
            }
        }

        for (String name : candidates) {
            // Per B.3.3.1 step ii: skip if F is an element of BoundNames of argumentsList
            // (including the implicit "arguments" binding).
            // QuickJS: !((func_idx = find_var(fd, func_name)) >= 0 && (func_idx & ARGUMENT_VAR_OFFSET))
            //       && !(func_name == JS_ATOM_arguments && fd->has_arguments_binding)
            if (parameterNames.contains(name)) {
                continue;
            }
            ctx.annexBFunctionNames.add(name);
            Integer existingLocal = ctx.findLocalInScopes(name);
            if (existingLocal != null) {
                // Already exists (e.g., explicit var declaration)
                ctx.annexBFunctionScopeLocals.put(name, existingLocal);
            } else if (!alreadyDeclared.contains(name)) {
                // Create new local in function scope, initialized to undefined
                int localIndex = ctx.currentScope().declareLocal(name);
                ctx.annexBFunctionScopeLocals.put(name, localIndex);
                ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        }
    }

    /**
     * Pre-declare all variable names as locals in the current scope.
     * This ensures bindings are visible during Phase 1 (function declaration hoisting),
     * so nested function declarations can properly capture outer variables via VarRef.
     * <p>
     * Handles:
     * - var declarations: function-scoped, recurse into blocks (they hoist)
     * - let/const declarations: block-scoped, only top-level of function body
     * (they don't hoist into nested blocks but ARE in scope at function level)
     */
    void hoistVarDeclarationsAsLocals(List<Statement> body) {
        Set<String> varNames = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof FunctionDeclaration) {
                continue;
            }
            // Collect var names (recurses into blocks since var is function-scoped)
            collectVarNamesFromStatement(stmt, varNames);
            // Collect top-level let/const names (don't recurse — they're block-scoped)
            if (stmt instanceof VariableDeclaration vd && vd.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : vd.declarations()) {
                    collectPatternBindingNames(d.id(), varNames);
                }
            }
        }
        for (String varName : varNames) {
            if (ctx.currentScope().getLocal(varName) == null) {
                ctx.currentScope().declareLocal(varName);
            }
        }
    }

    void registerGlobalProgramBindings(List<Statement> body) {
        for (Statement stmt : body) {
            if (stmt instanceof VariableDeclaration varDecl) {
                for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
                    collectPatternBindingNames(declarator.id(), ctx.nonDeletableGlobalBindings);
                }
            } else if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.id() != null) {
                    ctx.nonDeletableGlobalBindings.add(funcDecl.id().name());
                }
            } else if (stmt instanceof ClassDeclaration classDecl) {
                if (classDecl.id() != null) {
                    ctx.nonDeletableGlobalBindings.add(classDecl.id().name());
                }
            }
        }
    }

    void scanAnnexBBlock(List<Statement> body, Set<String> parentLexicals, Set<String> result) {
        Set<String> blockLexicals = new HashSet<>(parentLexicals);
        collectLexicalBindings(body, blockLexicals);
        // Per B.3.3.1: block-scoped function declarations create lexical bindings.
        // Include them so nested blocks see them as lexical conflicts (replacing a
        // nested function with var F would clash with the enclosing lexical F).
        // Use a separate set for nested checks so same-level declarations are not blocked.
        Set<String> blockLexicalsWithFuncs = new HashSet<>(blockLexicals);
        for (Statement s : body) {
            if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                blockLexicalsWithFuncs.add(fd.id().name());
            }
        }
        for (Statement s : body) {
            if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                if (!blockLexicals.contains(fd.id().name())) {
                    result.add(fd.id().name());
                }
            }
            scanAnnexBStatement(s, blockLexicalsWithFuncs, result);
        }
    }

    void scanAnnexBFunctions(List<Statement> programBody, Set<String> declaredFuncVarNames) {
        if (ctx.strictMode) {
            return; // Annex B does not apply in strict mode
        }
        // Collect top-level lexical bindings (let/const) from the program body.
        // Per B.3.3.3 step ii, if replacing the function declaration with "var F"
        // would produce an early error (conflict with let/const), the extension is skipped.
        Set<String> topLevelLexicals = new HashSet<>();
        collectLexicalBindings(programBody, topLevelLexicals);

        Set<String> candidates = new HashSet<>();
        for (Statement stmt : programBody) {
            // Only recurse into compound statements; top-level FunctionDeclarations
            // are regular hoisting, not Annex B.
            scanAnnexBStatement(stmt, topLevelLexicals, candidates);
        }
        for (String name : candidates) {
            ctx.annexBFunctionNames.add(name);
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
            scanAnnexBBlock(block.body(), lexicalBindings, result);
        } else if (stmt instanceof IfStatement ifStmt) {
            if (ifStmt.consequent() instanceof FunctionDeclaration fd && fd.id() != null) {
                if (!lexicalBindings.contains(fd.id().name())) {
                    result.add(fd.id().name());
                }
            } else {
                scanAnnexBStatement(ifStmt.consequent(), lexicalBindings, result);
            }
            if (ifStmt.alternate() != null) {
                if (ifStmt.alternate() instanceof FunctionDeclaration fd && fd.id() != null) {
                    if (!lexicalBindings.contains(fd.id().name())) {
                        result.add(fd.id().name());
                    }
                } else {
                    scanAnnexBStatement(ifStmt.alternate(), lexicalBindings, result);
                }
            }
        } else if (stmt instanceof TryStatement tryStmt) {
            scanAnnexBBlock(tryStmt.block().body(), lexicalBindings, result);
            if (tryStmt.handler() != null) {
                // Per B.3.5, simple catch parameter (catch(e)) does NOT block Annex B var hoisting.
                // But destructuring catch parameter (catch({ f })) creates let-like bindings
                // that DO block hoisting (following QuickJS: destructuring uses TOK_LET).
                Set<String> catchLexicals = new HashSet<>(lexicalBindings);
                Pattern catchParam = tryStmt.handler().param();
                if (catchParam != null && !(catchParam instanceof Identifier)) {
                    // Destructuring pattern: collect binding names as lexical blockers
                    collectPatternBindingNames(catchParam, catchLexicals);
                }
                scanAnnexBBlock(tryStmt.handler().body().body(), catchLexicals, result);
            }
            if (tryStmt.finalizer() != null) {
                scanAnnexBBlock(tryStmt.finalizer().body(), lexicalBindings, result);
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            // Collect lexical bindings across all cases (switch shares one scope)
            Set<String> switchLexicals = new HashSet<>(lexicalBindings);
            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                collectLexicalBindings(sc.consequent(), switchLexicals);
            }
            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                for (Statement s : sc.consequent()) {
                    if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                        if (!switchLexicals.contains(fd.id().name())) {
                            result.add(fd.id().name());
                        }
                    }
                    scanAnnexBStatement(s, switchLexicals, result);
                }
            }
        } else if (stmt instanceof ForStatement forStmt) {
            if (forStmt.body() != null) {
                // Collect lexical bindings from the for-loop's init clause (e.g. "let f" in "for (let f; ; )")
                // Per B.3.3.3 step ii, if replacing the function declaration with "var F" would produce
                // an early error (conflict with let/const), the Annex B extension is skipped.
                Set<String> forLexicals = new HashSet<>(lexicalBindings);
                if (forStmt.init() instanceof VariableDeclaration vd && vd.kind() != VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator d : vd.declarations()) {
                        collectPatternBindingNames(d.id(), forLexicals);
                    }
                }
                scanAnnexBStatement(forStmt.body(), forLexicals, result);
            }
        } else if (stmt instanceof WhileStatement whileStmt) {
            scanAnnexBStatement(whileStmt.body(), lexicalBindings, result);
        } else if (stmt instanceof ForInStatement forInStmt) {
            Set<String> forInLexicals = new HashSet<>(lexicalBindings);
            if (forInStmt.left() instanceof VariableDeclaration varDecl && varDecl.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), forInLexicals);
                }
            }
            scanAnnexBStatement(forInStmt.body(), forInLexicals, result);
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            Set<String> forOfLexicals = new HashSet<>(lexicalBindings);
            if (forOfStmt.left() instanceof VariableDeclaration varDecl && varDecl.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), forOfLexicals);
                }
            }
            scanAnnexBStatement(forOfStmt.body(), forOfLexicals, result);
        }
    }
}
