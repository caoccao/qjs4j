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
 * Tagged template expression.
 * Represents tagged template literals like String.raw`hello`.
 */
public final class TaggedTemplateExpression extends Expression {
    private final TemplateLiteral quasi;
    private final Expression tag;

    public TaggedTemplateExpression(
            Expression tag,
            TemplateLiteral quasi,
            SourceLocation location) {
        super(location);
        this.tag = tag;
        this.quasi = quasi;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = tag != null && tag.containsAwait();
            if (!awaitInside && quasi != null && quasi.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = tag != null && tag.containsYield();
            if (!yieldInside && quasi != null && quasi.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public TemplateLiteral getQuasi() {
        return quasi;
    }

    public Expression getTag() {
        return tag;
    }

    @Override
    public boolean hasTailCallInTailPosition() {
        return true;
    }
}
