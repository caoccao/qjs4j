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

import com.caoccao.qjs4j.core.JSKeyword;

import java.util.*;

/**
 * Utility methods for AST analysis.
 */
public final class AstUtils {

    private AstUtils() {
    }

    public static Set<String> buildParameterNames(List<Pattern> params, List<Statement> body) {
        Set<String> paramNames = new HashSet<>();
        for (Pattern param : params) {
            paramNames.addAll(extractBoundNames(param));
        }
        if (!paramNames.contains(JSKeyword.ARGUMENTS)) {
            boolean hasVarArguments = body != null
                    && body.stream().anyMatch(Statement::containsVarArguments);
            if (!hasVarArguments) {
                paramNames.add(JSKeyword.ARGUMENTS);
            }
        }
        return paramNames;
    }

    private static void collectBlockLexicals(List<Statement> stmts, Set<String> lexicals) {
        for (Statement s : stmts) {
            if (s instanceof VariableDeclaration varDecl && varDecl.getKind() != VariableKind.VAR) {
                for (VariableDeclarator variableDeclarator : varDecl.getDeclarations()) {
                    collectPatternNames(variableDeclarator.getId(), lexicals);
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
                    for (VariableDeclarator d : varDecl.getDeclarations()) {
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
            for (VariableDeclarator declarator : statement.getVarDeclarators()) {
                collectPatternNames(declarator.getId(), varDecls);
            }
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

    public static int computeDefinedArgCount(List<Pattern> params, List<Expression> defaults, boolean hasRest) {
        if (defaults == null) {
            return params.size();
        }
        int count = 0;
        for (int i = 0; i < params.size(); i++) {
            if (i < defaults.size() && defaults.get(i) != null) {
                break;
            }
            count++;
        }
        return count;
    }

    public static List<String> extractBoundNames(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            return List.of(id.getName());
        } else if (pattern instanceof ObjectPattern objPattern) {
            List<String> names = new ArrayList<>();
            for (ObjectPatternProperty prop : objPattern.getProperties()) {
                names.addAll(extractBoundNames(prop.getValue()));
            }
            if (objPattern.getRestElement() != null) {
                names.addAll(extractBoundNames(objPattern.getRestElement().getArgument()));
            }
            return names;
        } else if (pattern instanceof ArrayPattern arrPattern) {
            List<String> names = new ArrayList<>();
            for (Pattern element : arrPattern.getElements()) {
                if (element != null) {
                    names.addAll(extractBoundNames(element));
                }
            }
            return names;
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            return extractBoundNames(assignPattern.getLeft());
        } else if (pattern instanceof RestElement restElement) {
            return extractBoundNames(restElement.getArgument());
        }
        return List.of();
    }

    public static String[] extractLocalVarNames(Map<Integer, String> localNamesByIndex, int count) {
        if (count == 0) {
            return null;
        }
        String[] names = new String[count];
        for (var entry : localNamesByIndex.entrySet()) {
            int index = entry.getKey();
            String name = entry.getValue();
            if (index >= 0 && index < count) {
                names[index] = name;
            }
        }
        return names;
    }

    public static String[] extractLocalVarNames(Collection<Map<Integer, String>> localNamesByIndexList, int localCount) {
        if (localCount == 0) {
            return null;
        }
        String[] names = new String[localCount];
        for (Map<Integer, String> localNamesByIndex : localNamesByIndexList) {
            for (var entry : localNamesByIndex.entrySet()) {
                int index = entry.getKey();
                String name = entry.getValue();
                if (index >= 0 && index < localCount) {
                    names[index] = name;
                }
            }
        }
        return names;
    }

    public static boolean hasNonSimpleParameters(List<Pattern> params, List<Expression> defaults, RestParameter restParameter) {
        if (restParameter != null) {
            return true;
        }
        if (defaults != null) {
            for (Expression d : defaults) {
                if (d != null) {
                    return true;
                }
            }
        }
        if (params != null) {
            for (Pattern param : params) {
                if (!(param instanceof Identifier)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isSuperIdentifier(Expression expression) {
        return expression instanceof Identifier id && JSKeyword.SUPER.equals(id.getName());
    }

    public static boolean isSuperMemberExpression(MemberExpression memberExpression) {
        return isSuperIdentifier(memberExpression.getObject());
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
            Statement statement, Set<String> lexicalBindingNames, Set<String> annexBFunctionNames) {
        if (statement instanceof BlockStatement blockStatement) {
            // Collect block-level lexical declarations
            Set<String> blockLexicalNames = new HashSet<>(lexicalBindingNames);
            collectBlockLexicals(blockStatement.getBody(), blockLexicalNames);

            for (Statement nestedStatement : blockStatement.getBody()) {
                if (nestedStatement instanceof FunctionDeclaration functionDeclaration
                        && functionDeclaration.getId() != null) {
                    if (functionDeclaration.isAnnexBSimpleDeclaration()
                            && !blockLexicalNames.contains(functionDeclaration.getId().getName())) {
                        annexBFunctionNames.add(functionDeclaration.getId().getName());
                    }
                }
                scanAnnexBForCollisionCheck(nestedStatement, blockLexicalNames, annexBFunctionNames);
            }
        } else if (statement instanceof IfStatement ifStatement) {
            if (ifStatement.getConsequent() instanceof FunctionDeclaration functionDeclaration
                    && functionDeclaration.getId() != null) {
                if (functionDeclaration.isAnnexBSimpleDeclaration()
                        && !lexicalBindingNames.contains(functionDeclaration.getId().getName())) {
                    annexBFunctionNames.add(functionDeclaration.getId().getName());
                }
            } else {
                scanAnnexBForCollisionCheck(ifStatement.getConsequent(), lexicalBindingNames, annexBFunctionNames);
            }
            if (ifStatement.getAlternate() != null) {
                if (ifStatement.getAlternate() instanceof FunctionDeclaration functionDeclaration
                        && functionDeclaration.getId() != null) {
                    if (functionDeclaration.isAnnexBSimpleDeclaration()
                            && !lexicalBindingNames.contains(functionDeclaration.getId().getName())) {
                        annexBFunctionNames.add(functionDeclaration.getId().getName());
                    }
                } else {
                    scanAnnexBForCollisionCheck(ifStatement.getAlternate(), lexicalBindingNames, annexBFunctionNames);
                }
            }
        } else if (statement instanceof SwitchStatement switchStatement) {
            // Switch cases share a single scope for lexical declarations
            Set<String> switchLexicalNames = new HashSet<>(lexicalBindingNames);
            for (SwitchStatement.SwitchCase switchCase : switchStatement.getCases()) {
                collectBlockLexicals(switchCase.getConsequent(), switchLexicalNames);
            }

            for (SwitchStatement.SwitchCase switchCase : switchStatement.getCases()) {
                for (Statement nestedStatement : switchCase.getConsequent()) {
                    if (nestedStatement instanceof FunctionDeclaration functionDeclaration
                            && functionDeclaration.getId() != null) {
                        if (functionDeclaration.isAnnexBSimpleDeclaration()
                                && !switchLexicalNames.contains(functionDeclaration.getId().getName())) {
                            annexBFunctionNames.add(functionDeclaration.getId().getName());
                        }
                    }
                    scanAnnexBForCollisionCheck(nestedStatement, switchLexicalNames, annexBFunctionNames);
                }
            }
        }
    }

}
