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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a switch statement.
 */
public final class SwitchStatement extends Statement {
    private final List<SwitchCase> cases;
    private final Expression discriminant;

    public SwitchStatement(Expression discriminant, List<SwitchCase> cases, SourceLocation location) {
        super(location);
        this.discriminant = discriminant;
        this.cases = cases;
    }

    @Override
    boolean containsSuspension(Suspension kind) {
        return kind.in(discriminant) || kind.in(cases);
    }

    public List<SwitchCase> getCases() {
        return cases;
    }

    public Expression getDiscriminant() {
        return discriminant;
    }

    @Override
    public List<VariableDeclarator> getVarDeclarators() {
        if (varDeclarators == null) {
            List<VariableDeclarator> collectedVarDeclarators = new ArrayList<>();
            if (cases != null) {
                for (SwitchCase switchCase : cases) {
                    if (switchCase != null && switchCase.getConsequent() != null) {
                        for (Statement statement : switchCase.getConsequent()) {
                            if (statement != null) {
                                collectedVarDeclarators.addAll(statement.getVarDeclarators());
                            }
                        }
                    }
                }
            }
            varDeclarators = collectedVarDeclarators;
        }
        return varDeclarators;
    }

    public static final class SwitchCase extends ASTNode {
        private final List<Statement> consequent;
        private final Expression test;

        public SwitchCase(Expression test, List<Statement> consequent) {
            super(resolveLocation(test, consequent));
            this.test = test;
            this.consequent = consequent;
        }

        @Override
        boolean containsSuspension(Suspension kind) {
            return kind.in(test) || kind.in(consequent);
        }

        public List<Statement> getConsequent() {
            return consequent;
        }

        public Expression getTest() {
            return test;
        }

        private static SourceLocation resolveLocation(Expression test, List<Statement> consequent) {
            if (test != null) {
                return test.getLocation();
            }
            if (consequent != null) {
                for (Statement statement : consequent) {
                    if (statement != null) {
                        return statement.getLocation();
                    }
                }
            }
            return SourceLocation.UNKNOWN;
        }
    }

}
