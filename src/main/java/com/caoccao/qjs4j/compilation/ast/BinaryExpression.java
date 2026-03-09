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
 * Represents a binary expression.
 */
public final class BinaryExpression extends Expression {
    private final Expression left;
    private final BinaryOperator operator;
    private final Expression right;

    public BinaryExpression(
            BinaryOperator operator,
            Expression left,
            Expression right,
            SourceLocation location) {
        super(location);
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (left != null && left.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && right != null && right.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (left != null && left.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && right != null && right.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public Expression left() {
        return left;
    }

    public BinaryOperator operator() {
        return operator;
    }

    public Expression right() {
        return right;
    }

}
