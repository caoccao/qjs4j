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
 * Represents an expression statement.
 */
public final class ExpressionStatement extends Statement {
    private final Expression expression;

    public ExpressionStatement(Expression expression, SourceLocation location) {
        super(location);
        this.expression = expression;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = (expression != null && expression.containsAwait());
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = (expression != null && expression.containsYield());
        }
        return yieldInside;
    }

    public Expression expression() {
        return expression;
    }

}
