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
 * Represents a property in an object expression.
 */
public final class ObjectExpressionProperty extends ASTNode {
    private final boolean computed;
    private final Expression key;
    private final String kind;
    private final boolean method;
    private final boolean shorthand;
    private final Expression value;

    public ObjectExpressionProperty(
            Expression key,
            Expression value,
            String kind,
            boolean computed,
            boolean shorthand,
            boolean method) {
        super(key != null ? key.getLocation() : (value != null ? value.getLocation() : new SourceLocation(0, 0, 0, 0)));
        this.key = key;
        this.value = value;
        this.kind = kind;
        this.computed = computed;
        this.shorthand = shorthand;
        this.method = method;
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

    public Expression getValue() {
        return value;
    }

    public boolean isComputed() {
        return computed;
    }

    public boolean isMethod() {
        return method;
    }

    public boolean isProtoDataProperty() {
        if (computed || shorthand || method || !"init".equals(kind)) {
            return false;
        }
        if (key instanceof Identifier identifier) {
            return "__proto__".equals(identifier.getName());
        }
        if (key instanceof Literal literal && literal.getValue() instanceof String stringValue) {
            return "__proto__".equals(stringValue);
        }
        return false;
    }

    public boolean isShorthand() {
        return shorthand;
    }
}
