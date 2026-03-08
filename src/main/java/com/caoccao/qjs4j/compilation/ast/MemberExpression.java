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
 * Represents a member access expression.
 */
public final class MemberExpression extends Expression {
    private final boolean computed;
    private final Expression object;
    private final boolean optional;
    private final Expression property;

    public MemberExpression(
            Expression object,
            Expression property,
            boolean computed,
            boolean optional,
            SourceLocation location) {
        super(location);
        this.object = object;
        this.property = property;
        this.computed = computed;
        this.optional = optional;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside != null) {
            return awaitInside;
        }
        boolean objectContainsAwait = object != null && object.containsAwait();
        boolean propertyContainsAwait = property != null && property.containsAwait();
        awaitInside = objectContainsAwait || propertyContainsAwait;
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside != null) {
            return yieldInside;
        }
        boolean objectContainsYield = object != null && object.containsYield();
        boolean propertyContainsYield = property != null && property.containsYield();
        yieldInside = objectContainsYield || propertyContainsYield;
        return yieldInside;
    }

    public boolean computed() {
        return computed;
    }

    public Expression object() {
        return object;
    }

    public boolean optional() {
        return optional;
    }

    public Expression property() {
        return property;
    }
}
