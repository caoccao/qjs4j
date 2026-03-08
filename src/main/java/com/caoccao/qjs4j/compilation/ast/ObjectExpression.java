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
 * Represents an object literal expression.
 */
public final class ObjectExpression extends Expression {
    private final List<Property> properties;

    public ObjectExpression(List<Property> properties, SourceLocation location) {
        super(location);
        this.properties = properties;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside != null) {
            return awaitInside;
        }
        boolean hasAwait = false;
        if (properties != null) {
            for (Property property : properties) {
                if (property != null
                        && ((property.key() != null && property.key().containsAwait())
                        || (property.value() != null && property.value().containsAwait()))) {
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
        if (properties != null) {
            for (Property property : properties) {
                if (property != null
                        && ((property.key() != null && property.key().containsYield())
                        || (property.value() != null && property.value().containsYield()))) {
                    hasYield = true;
                    break;
                }
            }
        }
        yieldInside = hasYield;
        return yieldInside;
    }

    public List<Property> properties() {
        return properties;
    }

    public record Property(
            Expression key,
            Expression value,
            String kind,
            boolean computed,
            boolean shorthand,
            boolean method
    ) {
    }
}
