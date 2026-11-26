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
 * Base sealed class for all AST nodes.
 */
public abstract sealed class ASTNode
        permits Pattern, Statement, ModuleItem, Program, RestParameter, VariableDeclarator, TryStatement.CatchClause,
        SwitchStatement.SwitchCase, ClassElement, ObjectProperty, ImportSpecifier, ExportSpecifier {
    protected Boolean awaitInside;
    private final SourceLocation location;
    protected Boolean yieldInside;

    protected ASTNode(SourceLocation location) {
        this.location = location;
        this.awaitInside = null;
        this.yieldInside = null;
    }

    public final boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = containsSuspension(Suspension.AWAIT);
        }
        return awaitInside;
    }

    /** Leaves and function boundaries do not suspend the enclosing evaluation. */
    boolean containsSuspension(Suspension kind) {
        return false;
    }

    public final boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = containsSuspension(Suspension.YIELD);
        }
        return yieldInside;
    }

    public final SourceLocation getLocation() {
        return location;
    }

    enum Suspension {
        /** An await expression or a for-await-of loop. */
        AWAIT,
        /** A yield expression. */
        YIELD;

        boolean in(ASTNode node) {
            return node != null && (this == AWAIT ? node.containsAwait() : node.containsYield());
        }

        boolean in(List<? extends ASTNode> nodes) {
            if (nodes != null) {
                for (ASTNode node : nodes) {
                    if (in(node)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
