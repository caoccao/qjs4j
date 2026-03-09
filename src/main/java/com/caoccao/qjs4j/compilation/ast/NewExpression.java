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
 * Represents a new expression.
 */
public final class NewExpression extends Expression {
    private final List<Expression> arguments;
    private final Expression callee;

    public NewExpression(
            Expression callee,
            List<Expression> arguments,
            SourceLocation location) {
        super(location);
        this.callee = callee;
        this.arguments = arguments;
    }

    public List<Expression> arguments() {
        return arguments;
    }

    public Expression callee() {
        return callee;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            boolean hasAwait = callee != null && callee.containsAwait();
            if (!hasAwait && arguments != null) {
                for (Expression argument : arguments) {
                    if (argument != null && argument.containsAwait()) {
                        hasAwait = true;
                        break;
                    }
                }
            }
            awaitInside = hasAwait;
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            boolean hasYield = callee != null && callee.containsYield();
            if (!hasYield && arguments != null) {
                for (Expression argument : arguments) {
                    if (argument != null && argument.containsYield()) {
                        hasYield = true;
                        break;
                    }
                }
            }
            yieldInside = hasYield;
        }
        return yieldInside;
    }
}
