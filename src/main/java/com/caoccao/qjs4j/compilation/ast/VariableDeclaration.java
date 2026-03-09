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
 * Represents a variable declaration statement.
 */
public final class VariableDeclaration extends Statement {
    private final List<VariableDeclarator> declarations;
    private final VariableKind kind;

    public VariableDeclaration(List<VariableDeclarator> declarations, VariableKind kind, SourceLocation location) {
        super(location);
        this.declarations = declarations;
        this.kind = kind;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (declarations != null) {
                for (VariableDeclarator declarator : declarations) {
                    if (declarator != null && declarator.containsAwait()) {
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
            if (declarations != null) {
                for (VariableDeclarator declarator : declarations) {
                    if (declarator != null && declarator.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    public List<VariableDeclarator> declarations() {
        return declarations;
    }

    public VariableKind kind() {
        return kind;
    }

    public static final class VariableDeclarator extends ASTNode {
        private final Pattern id;
        private final Expression init;

        public VariableDeclarator(Pattern id, Expression init) {
            super(id != null ? id.location() : (init != null ? init.location() : new SourceLocation(0, 0, 0, 0)));
            this.id = id;
            this.init = init;
        }

        @Override
        public boolean containsAwait() {
            if (awaitInside == null) {
                awaitInside = false;
                if (id != null && id.containsAwait()) {
                    awaitInside = true;
                }
                if (!awaitInside && init != null && init.containsAwait()) {
                    awaitInside = true;
                }
            }
            return awaitInside;
        }

        @Override
        public boolean containsYield() {
            if (yieldInside == null) {
                yieldInside = false;
                if (id != null && id.containsYield()) {
                    yieldInside = true;
                }
                if (!yieldInside && init != null && init.containsYield()) {
                    yieldInside = true;
                }
            }
            return yieldInside;
        }

        public Pattern id() {
            return id;
        }

        public Expression init() {
            return init;
        }
    }

}
