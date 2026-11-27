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
    private GlobalDeclarations globalDeclarations;
    private final boolean isModule;
    private final boolean strict;

    public Program(List<Statement> body, boolean isModule, boolean strict, SourceLocation location) {
        super(location);
        this.body = body;
        this.isModule = isModule;
        this.strict = strict;
        this.globalDeclarations = null;
    }

    @Override
    boolean containsSuspension(Suspension kind) {
        return kind.in(body);
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
                for (Statement statement : statements) {
                    if (statement != null) {
                        AnnexBFunctions.GLOBAL_COLLISIONS.scan(statement, lexDecls, varDecls);
                    }
                }
            }

            globalDeclarations = new GlobalDeclarations(Collections.unmodifiableSet(varDecls),
                    Collections.unmodifiableSet(lexDecls), Collections.unmodifiableSet(constDecls),
                    Collections.unmodifiableSet(functionDecls));
        }
        return globalDeclarations;
    }

    public boolean isModule() {
        return isModule;
    }

    public boolean isStrict() {
        return strict;
    }

    public record GlobalDeclarations(Set<String> varDeclarations, Set<String> lexicalDeclarations,
            Set<String> constDeclarations, Set<String> functionDeclarations) {
    }

}
