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
 * Represents a property in an object pattern.
 */
public final class ObjectPatternProperty extends ASTNode {
    private final boolean computed;
    private final Expression key;
    private final boolean shorthand;
    private final Pattern value;

    public ObjectPatternProperty(
            Expression key,
            Pattern value,
            boolean computed,
            boolean shorthand) {
        super(key != null ? key.location() : (value != null ? value.location() : new SourceLocation(0, 0, 0, 0)));
        this.key = key;
        this.value = value;
        this.computed = computed;
        this.shorthand = shorthand;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (key != null && key.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && value != null && value.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (key != null && key.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && value != null && value.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public boolean computed() {
        return computed;
    }

    public Expression key() {
        return key;
    }

    public boolean shorthand() {
        return shorthand;
    }

    public Pattern value() {
        return value;
    }
}
