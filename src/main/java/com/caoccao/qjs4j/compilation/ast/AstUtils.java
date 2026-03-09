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
            if (s instanceof VariableDeclaration varDecl && varDecl.getKind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.getDeclarations()) {
                    collectPatternNames(d.getId(), lexicals);
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
        for (Statement stmt : program.getBody()) {
            if (stmt instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.CONST) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternNames(declarator.getId(), constDecls);
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
        List<Statement> body = program.getBody();
        for (int statementIndex = body.size() - 1; statementIndex >= 0; statementIndex--) {
            Statement stmt = body.get(statementIndex);
            if (stmt instanceof VariableDeclaration varDecl) {
                if (varDecl.getKind() != VariableKind.VAR) {
                    boolean isConstDeclaration = varDecl.getKind() == VariableKind.CONST;
                    // let or const
                    for (VariableDeclaration.VariableDeclarator d : varDecl.getDeclarations()) {
                        collectPatternNames(d.getId(), lexDecls, isConstDeclaration ? constDecls : null);
                    }
                }
            } else if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.getId() != null) {
                    varDecls.add(funcDecl.getId().getName());
                    if (functionDecls != null) {
                        functionDecls.add(funcDecl.getId().getName());
                    }
                }
            } else if (stmt instanceof ClassDeclaration classDecl) {
                if (classDecl.getId() != null) {
                    lexDecls.add(classDecl.getId().getName());
                }
            }
        }
        for (Statement statement : body) {
            collectVarScopedNames(statement, varDecls);
        }

        // Also collect Annex B function hoisting candidates (functions in blocks/if/switch)
        // since they create var bindings at the global level
        if (!program.isStrict()) {
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
            primary.add(id.getName());
            if (secondary != null) {
                secondary.add(id.getName());
            }
        } else if (pattern instanceof ArrayPattern arr) {
            for (Pattern element : arr.getElements()) {
                if (element != null) {
                    collectPatternNames(element, primary, secondary);
                }
            }
        } else if (pattern instanceof ObjectPattern obj) {
            for (ObjectPatternProperty prop : obj.getProperties()) {
                collectPatternNames(prop.getValue(), primary, secondary);
            }
        } else if (pattern instanceof RestElement rest) {
            collectPatternNames(rest.getArgument(), primary, secondary);
        }
    }

    /**
     * Collect var-scoped declarations from a statement subtree.
     * Recurses through statement forms that do not create function scope and
     * skips descending into function/class bodies.
     */
    private static void collectVarScopedNames(Statement statement, Set<String> varDecls) {
        if (statement instanceof VariableDeclaration variableDeclaration
                && variableDeclaration.getKind() == VariableKind.VAR) {
            for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                collectPatternNames(declarator.getId(), varDecls);
            }
            return;
        }
        if (statement instanceof ForStatement forStatement) {
            if (forStatement.getInit() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternNames(declarator.getId(), varDecls);
                }
            }
            if (forStatement.getBody() != null) {
                collectVarScopedNames(forStatement.getBody(), varDecls);
            }
            return;
        }
        if (statement instanceof ForInStatement forInStatement) {
            if (forInStatement.getLeft() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternNames(declarator.getId(), varDecls);
                }
            }
            if (forInStatement.getBody() != null) {
                collectVarScopedNames(forInStatement.getBody(), varDecls);
            }
            return;
        }
        if (statement instanceof ForOfStatement forOfStatement) {
            if (forOfStatement.getLeft() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternNames(declarator.getId(), varDecls);
                }
            }
            if (forOfStatement.getBody() != null) {
                collectVarScopedNames(forOfStatement.getBody(), varDecls);
            }
            return;
        }
        if (statement instanceof BlockStatement blockStatement) {
            for (Statement nestedStatement : blockStatement.getBody()) {
                collectVarScopedNames(nestedStatement, varDecls);
            }
            return;
        }
        if (statement instanceof IfStatement ifStatement) {
            collectVarScopedNames(ifStatement.getConsequent(), varDecls);
            if (ifStatement.getAlternate() != null) {
                collectVarScopedNames(ifStatement.getAlternate(), varDecls);
            }
            return;
        }
        if (statement instanceof WhileStatement whileStatement) {
            collectVarScopedNames(whileStatement.getBody(), varDecls);
            return;
        }
        if (statement instanceof DoWhileStatement doWhileStatement) {
            collectVarScopedNames(doWhileStatement.getBody(), varDecls);
            return;
        }
        if (statement instanceof TryStatement tryStatement) {
            for (Statement nestedStatement : tryStatement.getBlock().getBody()) {
                collectVarScopedNames(nestedStatement, varDecls);
            }
            if (tryStatement.getHandler() != null) {
                for (Statement nestedStatement : tryStatement.getHandler().getBody().getBody()) {
                    collectVarScopedNames(nestedStatement, varDecls);
                }
            }
            if (tryStatement.getFinalizer() != null) {
                for (Statement nestedStatement : tryStatement.getFinalizer().getBody()) {
                    collectVarScopedNames(nestedStatement, varDecls);
                }
            }
            return;
        }
        if (statement instanceof SwitchStatement switchStatement) {
            for (SwitchStatement.SwitchCase switchCase : switchStatement.getCases()) {
                for (Statement nestedStatement : switchCase.getConsequent()) {
                    collectVarScopedNames(nestedStatement, varDecls);
                }
            }
            return;
        }
        if (statement instanceof LabeledStatement labeledStatement) {
            collectVarScopedNames(labeledStatement.getBody(), varDecls);
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
            collectBlockLexicals(block.getBody(), blockLexicals);

            for (Statement s : block.getBody()) {
                if (s instanceof FunctionDeclaration fd && fd.getId() != null) {
                    if (!blockLexicals.contains(fd.getId().getName())) {
                        result.add(fd.getId().getName());
                    }
                }
                scanAnnexBForCollisionCheck(s, blockLexicals, result);
            }
        } else if (stmt instanceof IfStatement ifStmt) {
            if (ifStmt.getConsequent() instanceof FunctionDeclaration fd && fd.getId() != null) {
                if (!lexicalBindings.contains(fd.getId().getName())) {
                    result.add(fd.getId().getName());
                }
            } else {
                scanAnnexBForCollisionCheck(ifStmt.getConsequent(), lexicalBindings, result);
            }
            if (ifStmt.getAlternate() != null) {
                if (ifStmt.getAlternate() instanceof FunctionDeclaration fd && fd.getId() != null) {
                    if (!lexicalBindings.contains(fd.getId().getName())) {
                        result.add(fd.getId().getName());
                    }
                } else {
                    scanAnnexBForCollisionCheck(ifStmt.getAlternate(), lexicalBindings, result);
                }
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            // Switch cases share a single scope for lexical declarations
            Set<String> switchLexicals = new HashSet<>(lexicalBindings);
            for (SwitchStatement.SwitchCase sc : switchStmt.getCases()) {
                collectBlockLexicals(sc.getConsequent(), switchLexicals);
            }

            for (SwitchStatement.SwitchCase sc : switchStmt.getCases()) {
                for (Statement s : sc.getConsequent()) {
                    if (s instanceof FunctionDeclaration fd && fd.getId() != null) {
                        if (!switchLexicals.contains(fd.getId().getName())) {
                            result.add(fd.getId().getName());
                        }
                    }
                    scanAnnexBForCollisionCheck(s, switchLexicals, result);
                }
            }
        }
    }
}
