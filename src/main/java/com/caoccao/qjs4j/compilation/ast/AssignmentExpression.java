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

/**
 * Represents an assignment expression.
 *
 * @param lhsIsIdentifierRef true when the left-hand side is a direct IdentifierReference (not parenthesized).
 *                           Used for function name inference per spec 13.15.2 step 1.c.
 */
public final class AssignmentExpression extends Expression {
    private final Expression left;
    private final boolean lhsIsIdentifierRef;
    private final AssignmentOperator operator;
    private final Expression right;

    public AssignmentExpression(Expression left, AssignmentOperator operator, Expression right, SourceLocation location) {
        this(left, operator, right, false, location);
    }

    public AssignmentExpression(
            Expression left,
            AssignmentOperator operator,
            Expression right,
            boolean lhsIsIdentifierRef,
            SourceLocation location) {
        super(location);
        this.left = left;
        this.operator = operator;
        this.right = right;
        this.lhsIsIdentifierRef = lhsIsIdentifierRef;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = left != null && left.containsAwait();
            if (!awaitInside && right != null && right.containsAwait()) {
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
        }
        return yieldInside;
    }

    public Expression getLeft() {
        return left;
    }

    public AssignmentOperator getOperator() {
        return operator;
    }

    public Expression getRight() {
        return right;
    }

    public boolean isLhsIdentifierRef() {
        return lhsIsIdentifierRef;
    }

}
