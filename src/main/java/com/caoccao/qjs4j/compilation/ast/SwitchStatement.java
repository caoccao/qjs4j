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

    public List<SwitchCase> cases() {
        return cases;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (discriminant != null && discriminant.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && cases != null) {
                for (SwitchCase switchCase : cases) {
                    if (switchCase != null && switchCase.containsAwait()) {
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
            if (discriminant != null && discriminant.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && cases != null) {
                for (SwitchCase switchCase : cases) {
                    if (switchCase != null && switchCase.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    public Expression discriminant() {
        return discriminant;
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
        public boolean containsAwait() {
            if (awaitInside == null) {
                awaitInside = false;
                if (test != null && test.containsAwait()) {
                    awaitInside = true;
                }
                if (!awaitInside && consequent != null) {
                    for (Statement statement : consequent) {
                        if (statement != null && statement.containsAwait()) {
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
                if (test != null && test.containsYield()) {
                    yieldInside = true;
                }
                if (!yieldInside && consequent != null) {
                    for (Statement statement : consequent) {
                        if (statement != null && statement.containsYield()) {
                            yieldInside = true;
                            break;
                        }
                    }
                }
            }
            return yieldInside;
        }

        public List<Statement> consequent() {
            return consequent;
        }

        public Expression test() {
            return test;
        }

        private static SourceLocation resolveLocation(Expression test, List<Statement> consequent) {
            if (test != null) {
                return test.location();
            }
            if (consequent != null) {
                for (Statement statement : consequent) {
                    if (statement != null) {
                        return statement.location();
                    }
                }
            }
            return new SourceLocation(0, 0, 0, 0);
        }
    }

}
