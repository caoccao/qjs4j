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

package com.caoccao.qjs4j.compilation.ast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for AST analysis.
 */
public final class AstUtils {

    private AstUtils() {
    }

    private static void collectBlockLexicals(List<Statement> stmts, Set<String> lexicals) {
        for (Statement s : stmts) {
            if (s instanceof VariableDeclaration varDecl && varDecl.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternNames(d.id(), lexicals);
                }
            }
        }
    }

    /**
     * Collect top-level const declaration names from a parsed program.
     *
     * @param program    The parsed program AST
     * @param constDecls Output: const names declared by this program
     */
    public static void collectGlobalConstDeclarations(Program program, Set<String> constDecls) {
        for (Statement stmt : program.body()) {
            if (stmt instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() == VariableKind.CONST) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    collectPatternNames(declarator.id(), constDecls);
                }
            }
        }
    }

    /**
     * Collect global declarations from a parsed program following ES2024 GlobalDeclarationInstantiation.
     * Collects var and lex (let/const) names declared at the top level.
     *
     * @param program  The parsed program AST
     * @param varDecls Output: var/function names declared by this program
     * @param lexDecls Output: let/const names declared by this program
     */
    public static void collectGlobalDeclarations(
            Program program,
            Set<String> varDecls,
            Set<String> lexDecls) {
        collectGlobalDeclarations(program, varDecls, lexDecls, null, null);
    }

    /**
     * Collect global declarations from a parsed program following ES2024 GlobalDeclarationInstantiation.
     * Collects var and lex (let/const) names declared at the top level, and optionally const names.
     *
     * @param program    The parsed program AST
     * @param varDecls   Output: var/function names declared by this program
     * @param lexDecls   Output: let/const names declared by this program
     * @param constDecls Optional output: const names declared by this program
     */
    public static void collectGlobalDeclarations(
            Program program,
            Set<String> varDecls,
            Set<String> lexDecls,
            Set<String> constDecls) {
        collectGlobalDeclarations(program, varDecls, lexDecls, constDecls, null);
    }

    /**
     * Collect global declarations from a parsed program following ES2024 GlobalDeclarationInstantiation.
     * Collects var and lex (let/const) names declared at the top level, and optionally const/function names.
     *
     * @param program       The parsed program AST
     * @param varDecls      Output: var/function names declared by this program
     * @param lexDecls      Output: let/const names declared by this program
     * @param constDecls    Optional output: const names declared by this program
     * @param functionDecls Optional output: top-level function declaration names in reverse source order
     */
    public static void collectGlobalDeclarations(
            Program program,
            Set<String> varDecls,
            Set<String> lexDecls,
            Set<String> constDecls,
            Set<String> functionDecls) {
        List<Statement> body = program.body();
        for (int statementIndex = body.size() - 1; statementIndex >= 0; statementIndex--) {
            Statement stmt = body.get(statementIndex);
            if (stmt instanceof VariableDeclaration varDecl) {
                if (varDecl.kind() != VariableKind.VAR) {
                    boolean isConstDeclaration = varDecl.kind() == VariableKind.CONST;
                    // let or const
                    for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                        collectPatternNames(d.id(), lexDecls, isConstDeclaration ? constDecls : null);
                    }
                }
            } else if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.id() != null) {
                    varDecls.add(funcDecl.id().name());
                    if (functionDecls != null) {
                        functionDecls.add(funcDecl.id().name());
                    }
                }
            } else if (stmt instanceof ClassDeclaration classDecl) {
                if (classDecl.id() != null) {
                    lexDecls.add(classDecl.id().name());
                }
            }
        }
        for (Statement statement : body) {
            collectVarScopedNames(statement, varDecls);
        }

        // Also collect Annex B function hoisting candidates (functions in blocks/if/switch)
        // since they create var bindings at the global level
        if (!program.strict()) {
            Set<String> topLevelLexicals = new HashSet<>(lexDecls);
            Set<String> annexBCandidates = new HashSet<>();
            for (Statement stmt : body) {
                scanAnnexBForCollisionCheck(stmt, topLevelLexicals, annexBCandidates);
            }
            varDecls.addAll(annexBCandidates);
        }
    }

    static void collectPatternNames(Pattern pattern, Set<String> names) {
        collectPatternNames(pattern, names, null);
    }

    private static void collectPatternNames(Pattern pattern, Set<String> primary, Set<String> secondary) {
        if (pattern instanceof Identifier id) {
            primary.add(id.name());
            if (secondary != null) {
                secondary.add(id.name());
            }
        } else if (pattern instanceof ArrayPattern arr) {
            for (Pattern element : arr.elements()) {
                if (element != null) {
                    collectPatternNames(element, primary, secondary);
                }
            }
        } else if (pattern instanceof ObjectPattern obj) {
            for (ObjectPatternProperty prop : obj.properties()) {
                collectPatternNames(prop.value(), primary, secondary);
            }
        } else if (pattern instanceof RestElement rest) {
            collectPatternNames(rest.argument(), primary, secondary);
        }
    }

    /**
     * Collect var-scoped declarations from a statement subtree.
     * Recurses through statement forms that do not create function scope and
     * skips descending into function/class bodies.
     */
    private static void collectVarScopedNames(Statement statement, Set<String> varDecls) {
        if (statement instanceof VariableDeclaration variableDeclaration
                && variableDeclaration.kind() == VariableKind.VAR) {
            for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                collectPatternNames(declarator.id(), varDecls);
            }
            return;
        }
        if (statement instanceof ForStatement forStatement) {
            if (forStatement.init() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    collectPatternNames(declarator.id(), varDecls);
                }
            }
            if (forStatement.body() != null) {
                collectVarScopedNames(forStatement.body(), varDecls);
            }
            return;
        }
        if (statement instanceof ForInStatement forInStatement) {
            if (forInStatement.left() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    collectPatternNames(declarator.id(), varDecls);
                }
            }
            if (forInStatement.body() != null) {
                collectVarScopedNames(forInStatement.body(), varDecls);
            }
            return;
        }
        if (statement instanceof ForOfStatement forOfStatement) {
            if (forOfStatement.left() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    collectPatternNames(declarator.id(), varDecls);
                }
            }
            if (forOfStatement.body() != null) {
                collectVarScopedNames(forOfStatement.body(), varDecls);
            }
            return;
        }
        if (statement instanceof BlockStatement blockStatement) {
            for (Statement nestedStatement : blockStatement.body()) {
                collectVarScopedNames(nestedStatement, varDecls);
            }
            return;
        }
        if (statement instanceof IfStatement ifStatement) {
            collectVarScopedNames(ifStatement.consequent(), varDecls);
            if (ifStatement.alternate() != null) {
                collectVarScopedNames(ifStatement.alternate(), varDecls);
            }
            return;
        }
        if (statement instanceof WhileStatement whileStatement) {
            collectVarScopedNames(whileStatement.body(), varDecls);
            return;
        }
        if (statement instanceof DoWhileStatement doWhileStatement) {
            collectVarScopedNames(doWhileStatement.body(), varDecls);
            return;
        }
        if (statement instanceof TryStatement tryStatement) {
            for (Statement nestedStatement : tryStatement.block().body()) {
                collectVarScopedNames(nestedStatement, varDecls);
            }
            if (tryStatement.handler() != null) {
                for (Statement nestedStatement : tryStatement.handler().body().body()) {
                    collectVarScopedNames(nestedStatement, varDecls);
                }
            }
            if (tryStatement.finalizer() != null) {
                for (Statement nestedStatement : tryStatement.finalizer().body()) {
                    collectVarScopedNames(nestedStatement, varDecls);
                }
            }
            return;
        }
        if (statement instanceof SwitchStatement switchStatement) {
            for (SwitchStatement.SwitchCase switchCase : switchStatement.cases()) {
                for (Statement nestedStatement : switchCase.consequent()) {
                    collectVarScopedNames(nestedStatement, varDecls);
                }
            }
            return;
        }
        if (statement instanceof LabeledStatement labeledStatement) {
            collectVarScopedNames(labeledStatement.body(), varDecls);
        }
    }

    /**
     * Scan for Annex B function declaration candidates in compound statements.
     * These are function declarations inside blocks, if-statements, switch cases, etc.
     * that would create var bindings via Annex B.3.3 hoisting.
     * <p>
     * Per B.3.3.2: hoisting is skipped if replacing the FunctionDeclaration with a
     * VariableStatement would produce early errors — i.e. if there's a let/const
     * with the same name in an enclosing block scope.
     */
    private static void scanAnnexBForCollisionCheck(
            Statement stmt, Set<String> lexicalBindings, Set<String> result) {
        if (stmt instanceof BlockStatement block) {
            // Collect block-level lexical declarations
            Set<String> blockLexicals = new HashSet<>(lexicalBindings);
            collectBlockLexicals(block.body(), blockLexicals);

            for (Statement s : block.body()) {
                if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                    if (!blockLexicals.contains(fd.id().name())) {
                        result.add(fd.id().name());
                    }
                }
                scanAnnexBForCollisionCheck(s, blockLexicals, result);
            }
        } else if (stmt instanceof IfStatement ifStmt) {
            if (ifStmt.consequent() instanceof FunctionDeclaration fd && fd.id() != null) {
                if (!lexicalBindings.contains(fd.id().name())) {
                    result.add(fd.id().name());
                }
            } else {
                scanAnnexBForCollisionCheck(ifStmt.consequent(), lexicalBindings, result);
            }
            if (ifStmt.alternate() != null) {
                if (ifStmt.alternate() instanceof FunctionDeclaration fd && fd.id() != null) {
                    if (!lexicalBindings.contains(fd.id().name())) {
                        result.add(fd.id().name());
                    }
                } else {
                    scanAnnexBForCollisionCheck(ifStmt.alternate(), lexicalBindings, result);
                }
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            // Switch cases share a single scope for lexical declarations
            Set<String> switchLexicals = new HashSet<>(lexicalBindings);
            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                collectBlockLexicals(sc.consequent(), switchLexicals);
            }

            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                for (Statement s : sc.consequent()) {
                    if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                        if (!switchLexicals.contains(fd.id().name())) {
                            result.add(fd.id().name());
                        }
                    }
                    scanAnnexBForCollisionCheck(s, switchLexicals, result);
                }
            }
        }
    }
}
