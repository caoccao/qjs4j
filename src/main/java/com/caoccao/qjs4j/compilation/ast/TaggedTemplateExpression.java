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
 *
 * @param tag      The tag function expression
 * @param quasi    The template literal
 * @param location Source location
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
        if (awaitInside != null) {
            return awaitInside;
        }
        boolean tagContainsAwait = tag != null && tag.containsAwait();
        boolean quasiContainsAwait = quasi != null && quasi.containsAwait();
        awaitInside = tagContainsAwait || quasiContainsAwait;
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside != null) {
            return yieldInside;
        }
        boolean tagContainsYield = tag != null && tag.containsYield();
        boolean quasiContainsYield = quasi != null && quasi.containsYield();
        yieldInside = tagContainsYield || quasiContainsYield;
        return yieldInside;
    }

    public TemplateLiteral quasi() {
        return quasi;
    }

    public Expression tag() {
        return tag;
    }
}
