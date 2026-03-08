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
 * Template literal expression.
 * Represents template strings like `hello ${world}`.
 *
 * @param quasis      List of cooked template element strings (the static parts)
 *                    For tagged templates, entries can be null when an escape is invalid.
 * @param rawQuasis   List of raw template element strings
 * @param expressions List of expressions to be interpolated
 * @param location    Source location
 */
public final class TemplateLiteral extends Expression {
    private final List<Expression> expressions;
    private final List<String> quasis;
    private final List<String> rawQuasis;

    public TemplateLiteral(
            List<String> quasis,
            List<String> rawQuasis,
            List<Expression> expressions,
            SourceLocation location) {
        super(location);
        this.quasis = quasis;
        this.rawQuasis = rawQuasis;
        this.expressions = expressions;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside != null) {
            return awaitInside;
        }
        boolean hasAwait = false;
        if (expressions != null) {
            for (Expression expression : expressions) {
                if (expression != null && expression.containsAwait()) {
                    hasAwait = true;
                    break;
                }
            }
        }
        awaitInside = hasAwait;
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside != null) {
            return yieldInside;
        }
        boolean hasYield = false;
        if (expressions != null) {
            for (Expression expression : expressions) {
                if (expression != null && expression.containsYield()) {
                    hasYield = true;
                    break;
                }
            }
        }
        yieldInside = hasYield;
        return yieldInside;
    }

    public List<Expression> expressions() {
        return expressions;
    }

    public List<String> quasis() {
        return quasis;
    }

    public List<String> rawQuasis() {
        return rawQuasis;
    }
}
