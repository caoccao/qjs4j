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
 * Represents a method in a class.
 */
public final class MethodDefinition extends ClassElement {
    private final boolean computed;
    private final boolean isPrivate;
    private final boolean isStatic;
    private final Expression key;
    private final String kind;
    private final FunctionExpression value;

    public MethodDefinition(
            Expression key,
            FunctionExpression value,
            String kind,
            boolean computed,
            boolean isStatic,
            boolean isPrivate) {
        super(key != null ? key.getLocation() : (value != null ? value.getLocation() : new SourceLocation(0, 0, 0, 0)));
        this.key = key;
        this.value = value;
        this.kind = kind;
        this.computed = computed;
        this.isStatic = isStatic;
        this.isPrivate = isPrivate;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = key != null && key.containsAwait();
            if (!awaitInside && value != null && value.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = key != null && key.containsYield();
            if (!yieldInside && value != null && value.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public Expression getKey() {
        return key;
    }

    public String getKind() {
        return kind;
    }

    public FunctionExpression getValue() {
        return value;
    }

    public boolean isComputed() {
        return computed;
    }

    /**
     * Per ES2024, only non-static, non-computed "constructor" defines
     * a class constructor method.
     */
    public boolean isConstructor() {
        if (isStatic || computed) {
            return false;
        }
        if (key instanceof Identifier id) {
            return "constructor".equals(id.getName());
        }
        if (key instanceof Literal literal && literal.getValue() instanceof String name) {
            return "constructor".equals(name);
        }
        return false;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public boolean isStatic() {
        return isStatic;
    }
}
