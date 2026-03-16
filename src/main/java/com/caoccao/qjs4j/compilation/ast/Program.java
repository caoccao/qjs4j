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

import java.util.*;

/**
 * Represents a complete program (script or module).
 */
public final class Program extends ASTNode {
    private final List<Statement> body;
    private final boolean isModule;
    private final boolean strict;
    private GlobalDeclarations globalDeclarations;

    public Program(List<Statement> body, boolean isModule, boolean strict, SourceLocation location) {
        super(location);
        this.body = body;
        this.isModule = isModule;
        this.strict = strict;
        this.globalDeclarations = null;
    }

    private static void collectBlockLexicals(List<Statement> statements, Set<String> lexicalNames) {
        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() != VariableKind.VAR) {
                for (VariableDeclarator variableDeclarator : variableDeclaration.getDeclarations()) {
                    lexicalNames.addAll(variableDeclarator.getId().getBoundNames());
                }
            }
        }
    }

    private static void scanAnnexBForCollisionCheck(
            Statement statement, Set<String> lexicalBindingNames, Set<String> annexBFunctionNames) {
        if (statement instanceof BlockStatement blockStatement) {
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

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (body != null) {
                for (Statement item : body) {
                    if (item != null && item.containsAwait()) {
                        awaitInside = true;
                        break;
                    }
                }
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (body != null) {
                for (Statement item : body) {
                    if (item != null && item.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    public List<Statement> getBody() {
        return body;
    }

    public GlobalDeclarations getGlobalDeclarations() {
        if (globalDeclarations == null) {
            Set<String> varDecls = new HashSet<>();
            Set<String> lexDecls = new HashSet<>();
            Set<String> constDecls = new HashSet<>();
            Set<String> functionDecls = new LinkedHashSet<>();
            List<Statement> statements = body == null ? List.of() : body;

            for (int statementIndex = statements.size() - 1; statementIndex >= 0; statementIndex--) {
                Statement statement = statements.get(statementIndex);
                if (statement instanceof VariableDeclaration variableDeclaration) {
                    if (variableDeclaration.getKind() != VariableKind.VAR) {
                        boolean isConstDeclaration = variableDeclaration.getKind() == VariableKind.CONST;
                        for (VariableDeclarator variableDeclarator : variableDeclaration.getDeclarations()) {
                            List<String> boundNames = variableDeclarator.getId().getBoundNames();
                            lexDecls.addAll(boundNames);
                            if (isConstDeclaration) {
                                constDecls.addAll(boundNames);
                            }
                        }
                    }
                } else if (statement instanceof FunctionDeclaration functionDeclaration) {
                    if (functionDeclaration.getId() != null) {
                        varDecls.add(functionDeclaration.getId().getName());
                        functionDecls.add(functionDeclaration.getId().getName());
                    }
                } else if (statement instanceof ClassDeclaration classDeclaration) {
                    if (classDeclaration.getId() != null) {
                        lexDecls.add(classDeclaration.getId().getName());
                    }
                }
            }

            for (Statement statement : statements) {
                if (statement == null) {
                    continue;
                }
                for (VariableDeclarator variableDeclarator : statement.getVarDeclarators()) {
                    varDecls.addAll(variableDeclarator.getId().getBoundNames());
                }
            }

            if (!strict) {
                Set<String> topLevelLexicals = new HashSet<>(lexDecls);
                Set<String> annexBCandidates = new HashSet<>();
                for (Statement statement : statements) {
                    if (statement != null) {
                        scanAnnexBForCollisionCheck(statement, topLevelLexicals, annexBCandidates);
                    }
                }
                varDecls.addAll(annexBCandidates);
            }

            globalDeclarations = new GlobalDeclarations(
                    Collections.unmodifiableSet(varDecls),
                    Collections.unmodifiableSet(lexDecls),
                    Collections.unmodifiableSet(constDecls),
                    Collections.unmodifiableSet(functionDecls)
            );
        }
        return globalDeclarations;
    }

    public boolean isModule() {
        return isModule;
    }

    public boolean isStrict() {
        return strict;
    }

    public record GlobalDeclarations(
            Set<String> varDeclarations,
            Set<String> lexicalDeclarations,
            Set<String> constDeclarations,
            Set<String> functionDeclarations) {
    }

}
