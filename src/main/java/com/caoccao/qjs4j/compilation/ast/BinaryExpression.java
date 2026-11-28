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
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Represents a binary expression.
 */
public final class BinaryExpression extends Expression {
    private final Expression left;
    private final BinaryOperator operator;
    private final Expression right;

    public BinaryExpression(BinaryOperator operator, Expression left, Expression right, SourceLocation location) {
        super(location);
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    boolean computeDirectEvalVarArguments() {
        return containsAlongLeftSpine(SyntaxQuery.DIRECT_EVAL_VAR_ARGUMENTS);
    }

    /**
     * Evaluates a cached, short-circuiting "contains" test over a left-associative chain without recursing down its
     * left spine. Chains such as {@code a + b + c + ...} nest on the left, so a recursive walk needs one Java frame per
     * operand and overflows the stack for generated code with thousands of operands.
     */
    private boolean containsAlongLeftSpine(SyntaxQuery query) {
        ArrayList<BinaryExpression> spine = new ArrayList<>();
        Expression current = this;
        while (current instanceof BinaryExpression binaryExpression) {
            spine.add(binaryExpression);
            current = binaryExpression.left;
        }
        boolean result = query.in(current);
        for (int i = spine.size() - 1; i >= 0; i--) {
            BinaryExpression binaryExpression = spine.get(i);
            if (!result && query.in(binaryExpression.right)) {
                result = true;
            }
            query.cache(binaryExpression, result);
        }
        return result;
    }

    @Override
    boolean containsSuspension(SyntaxQuery kind) {
        return containsAlongLeftSpine(kind);
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
        if (!operator.isEagerOperator()) {
            return right.hasTailCallInTailPosition();
        }
        return false;
    }

}
