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
 * Represents a for-in statement: for (variable in object) { ... }
 * * <p>
 * * Based on ES5 for-in loops.
 * * left can be a VariableDeclaration (e.g., var x) or a Pattern/Expression (e.g., x, obj.prop).
 */
public final class ForInStatement extends Statement {
    private final Statement body;
    private final ASTNode left;
    private final Expression right;

    public ForInStatement(ASTNode left, Expression right, Statement body, SourceLocation location) {
        super(location);
        this.left = left;
        this.right = right;
        this.body = body;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = left != null && left.containsAwait();
            if (!awaitInside && right != null && right.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && body != null && body.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = left != null && left.containsYield();
            if (!yieldInside && right != null && right.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && body != null && body.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    @Override
    public List<VariableDeclarator> getVarDeclarators() {
        if (varDeclarators == null) {
            List<VariableDeclarator> collectedVarDeclarators = new ArrayList<>();
            if (left instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.VAR) {
                collectedVarDeclarators.addAll(variableDeclaration.getDeclarations());
            }
            if (body != null) {
                collectedVarDeclarators.addAll(body.getVarDeclarators());
            }
            varDeclarators = collectedVarDeclarators;
        }
        return varDeclarators;
    }

    public Statement getBody() {
        return body;
    }

    public ASTNode getLeft() {
        return left;
    }

    public Expression getRight() {
        return right;
    }

}
