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
 * Represents a conditional (ternary) expression.
 */
public final class ConditionalExpression extends Expression {
    private final Expression alternate;
    private final Expression consequent;
    private final Expression test;

    public ConditionalExpression(
            Expression test,
            Expression consequent,
            Expression alternate,
            SourceLocation location) {
        super(location);
        this.test = test;
        this.consequent = consequent;
        this.alternate = alternate;
    }

    public Expression alternate() {
        return alternate;
    }

    public Expression consequent() {
        return consequent;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (test != null && test.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && consequent != null && consequent.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && alternate != null && alternate.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (test != null && test.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && consequent != null && consequent.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && alternate != null && alternate.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public Expression test() {
        return test;
    }
}
