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
    private final AssignmentOperator operator;
    private final Expression right;
    private final boolean lhsIsIdentifierRef;

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
        if (awaitInside != null) {
            return awaitInside;
        }
        boolean leftContainsAwait = left != null && left.containsAwait();
        boolean rightContainsAwait = right != null && right.containsAwait();
        awaitInside = leftContainsAwait || rightContainsAwait;
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside != null) {
            return yieldInside;
        }
        boolean leftContainsYield = left != null && left.containsYield();
        boolean rightContainsYield = right != null && right.containsYield();
        yieldInside = leftContainsYield || rightContainsYield;
        return yieldInside;
    }

    public boolean lhsIsIdentifierRef() {
        return lhsIsIdentifierRef;
    }

    public Expression left() {
        return left;
    }

    public AssignmentOperator operator() {
        return operator;
    }

    public Expression right() {
        return right;
    }

    public enum AssignmentOperator {
        ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, MUL_ASSIGN, DIV_ASSIGN,
        MOD_ASSIGN, EXP_ASSIGN, LSHIFT_ASSIGN, RSHIFT_ASSIGN,
        URSHIFT_ASSIGN, AND_ASSIGN, OR_ASSIGN, XOR_ASSIGN,
        LOGICAL_AND_ASSIGN, LOGICAL_OR_ASSIGN, NULLISH_ASSIGN
    }
}
