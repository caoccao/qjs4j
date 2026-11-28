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
    private byte cachedQueries;
    private final SourceLocation location;

    protected ASTNode(SourceLocation location) {
        this.location = location;
    }

    public final boolean containsAwait() {
        return SyntaxQuery.AWAIT.in(this);
    }

    /** Leaves and function boundaries do not suspend the enclosing evaluation. */
    boolean containsSuspension(SyntaxQuery kind) {
        return false;
    }

    public final boolean containsYield() {
        return SyntaxQuery.YIELD.in(this);
    }

    public final SourceLocation getLocation() {
        return location;
    }

    enum SyntaxQuery {
        /** An await expression or a for-await-of loop. */
        AWAIT(1),
        /** Direct eval that can introduce a var arguments binding. */
        DIRECT_EVAL_VAR_ARGUMENTS(16),
        /** A yield expression. */
        YIELD(4);

        private final int found;
        // Each query has a known bit and a result bit. A single byte holds all three cached answers.
        private final int known;

        SyntaxQuery(int known) {
            this.known = known;
            found = known << 1;
        }

        void cache(ASTNode node, boolean result) {
            node.cachedQueries = (byte) ((node.cachedQueries & ~found) | known | (result ? found : 0));
        }

        Boolean cachedResult(ASTNode node) {
            int bits = node.cachedQueries;
            return (bits & known) == 0 ? null : Boolean.valueOf((bits & found) != 0);
        }

        boolean in(ASTNode node) {
            if (node == null) {
                return false;
            }
            Boolean cached = cachedResult(node);
            if (cached != null) {
                return cached;
            }
            boolean result = this == DIRECT_EVAL_VAR_ARGUMENTS
                    ? node instanceof Expression expression && expression.computeDirectEvalVarArguments()
                    : node.containsSuspension(this);
            cache(node, result);
            return result;
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
