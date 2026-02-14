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

package com.caoccao.qjs4j.compiler;

import com.caoccao.qjs4j.compiler.ast.*;

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
        for (Statement stmt : program.body()) {
            if (stmt instanceof VariableDeclaration varDecl) {
                if (varDecl.kind() == VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                        collectPatternNames(d.id(), varDecls);
                    }
                } else {
                    // let or const
                    for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                        collectPatternNames(d.id(), lexDecls);
                    }
                }
            } else if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.id() != null) {
                    varDecls.add(funcDecl.id().name());
                }
            }
        }

        // Also collect Annex B function hoisting candidates (functions in blocks/if/switch)
        // since they create var bindings at the global level
        if (!program.strict()) {
            Set<String> topLevelLexicals = new HashSet<>(lexDecls);
            Set<String> annexBCandidates = new HashSet<>();
            for (Statement stmt : program.body()) {
                scanAnnexBForCollisionCheck(stmt, topLevelLexicals, annexBCandidates);
            }
            varDecls.addAll(annexBCandidates);
        }
    }

    static void collectPatternNames(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier id) {
            names.add(id.name());
        } else if (pattern instanceof ArrayPattern arr) {
            for (Pattern element : arr.elements()) {
                if (element != null) {
                    collectPatternNames(element, names);
                }
            }
        } else if (pattern instanceof ObjectPattern obj) {
            for (ObjectPattern.Property prop : obj.properties()) {
                collectPatternNames(prop.value(), names);
            }
        } else if (pattern instanceof RestElement rest) {
            collectPatternNames(rest.argument(), names);
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
