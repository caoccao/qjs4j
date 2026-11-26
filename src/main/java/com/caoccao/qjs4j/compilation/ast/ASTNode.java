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

    /** Leaves and function boundaries do not contain an enclosing await expression. */
    public boolean containsAwait() {
        return false;
    }

    final boolean containsAwaitIn(ASTNode child) {
        if (awaitInside == null) {
            awaitInside = childContainsAwait(child);
        }
        return awaitInside;
    }

    final boolean containsAwaitIn(ASTNode first, ASTNode second) {
        if (awaitInside == null) {
            awaitInside = childContainsAwait(first);
            if (!awaitInside && childContainsAwait(second)) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    final boolean containsAwaitIn(ASTNode first, ASTNode second, ASTNode third) {
        if (awaitInside == null) {
            awaitInside = childContainsAwait(first);
            if (!awaitInside && (childContainsAwait(second) || childContainsAwait(third))) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    final boolean containsAwaitIn(ASTNode first, List<? extends ASTNode> children) {
        if (awaitInside == null) {
            awaitInside = childContainsAwait(first);
            if (!awaitInside && anyContainsAwait(children)) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    final boolean containsAwaitIn(List<? extends ASTNode> children) {
        if (awaitInside == null) {
            awaitInside = false;
            if (anyContainsAwait(children)) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    /** Leaves and function boundaries do not contain an enclosing yield expression. */
    public boolean containsYield() {
        return false;
    }

    final boolean containsYieldIn(ASTNode child) {
        if (yieldInside == null) {
            yieldInside = childContainsYield(child);
        }
        return yieldInside;
    }

    final boolean containsYieldIn(ASTNode first, ASTNode second) {
        if (yieldInside == null) {
            yieldInside = childContainsYield(first);
            if (!yieldInside && childContainsYield(second)) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    final boolean containsYieldIn(ASTNode first, ASTNode second, ASTNode third) {
        if (yieldInside == null) {
            yieldInside = childContainsYield(first);
            if (!yieldInside && (childContainsYield(second) || childContainsYield(third))) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    final boolean containsYieldIn(ASTNode first, List<? extends ASTNode> children) {
        if (yieldInside == null) {
            yieldInside = childContainsYield(first);
            if (!yieldInside && anyContainsYield(children)) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    final boolean containsYieldIn(List<? extends ASTNode> children) {
        if (yieldInside == null) {
            yieldInside = false;
            if (anyContainsYield(children)) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public final SourceLocation getLocation() {
        return location;
    }

    static boolean anyContainsAwait(List<? extends ASTNode> nodes) {
        if (nodes != null) {
            for (ASTNode node : nodes) {
                if (node != null && node.containsAwait()) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean anyContainsYield(List<? extends ASTNode> nodes) {
        if (nodes != null) {
            for (ASTNode node : nodes) {
                if (node != null && node.containsYield()) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean childContainsAwait(ASTNode node) {
        return node != null && node.containsAwait();
    }

    static boolean childContainsYield(ASTNode node) {
        return node != null && node.containsYield();
    }
}
