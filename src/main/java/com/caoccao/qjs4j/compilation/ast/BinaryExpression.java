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
    private Boolean directEvalVarArgumentsInside;

    public BinaryExpression(
            BinaryOperator operator,
            Expression left,
            Expression right,
            SourceLocation location) {
        super(location);
        this.operator = operator;
        this.left = left;
        this.right = right;
        directEvalVarArgumentsInside = null;
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
    public boolean containsDirectEvalVarArguments() {
        if (directEvalVarArgumentsInside == null) {
            directEvalVarArgumentsInside = left != null && left.containsDirectEvalVarArguments();
            if (!directEvalVarArgumentsInside && right != null && right.containsDirectEvalVarArguments()) {
                directEvalVarArgumentsInside = true;
            }
        }
        return directEvalVarArgumentsInside;
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

    public BinaryOperator getOperator() {
        return operator;
    }

    public Expression getRight() {
        return right;
    }

    @Override
    public boolean hasTailCallInTailPosition() {
        if (operator == BinaryOperator.NULLISH_COALESCING
                || operator == BinaryOperator.LOGICAL_AND
                || operator == BinaryOperator.LOGICAL_OR) {
            return right.hasTailCallInTailPosition();
        }
        return false;
    }

}
