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
 * Represents a sequence expression (comma operator).
 * Evaluates each expression in order and returns the value of the last one.
 * Example: (a = 1, b = 2, a + b)
 */
public final class SequenceExpression extends Expression {
    private final List<Expression> expressions;

    public SequenceExpression(List<Expression> expressions, SourceLocation location) {
        super(location);
        this.expressions = expressions;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (expressions != null) {
                for (Expression expression : expressions) {
                    if (expression != null && expression.containsAwait()) {
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
            if (expressions != null) {
                for (Expression expression : expressions) {
                    if (expression != null && expression.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    public List<Expression> getExpressions() {
        return expressions;
    }
}
