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
public final class ObjectExpressionProperty extends ObjectProperty<Expression> {
    private final String kind;
    private final boolean method;

    public ObjectExpressionProperty(Expression key, Expression value, String kind, boolean computed, boolean shorthand,
            boolean method) {
        super(key, value, computed, shorthand);
        this.kind = kind;
        this.method = method;
    }

    public String getKind() {
        return kind;
    }

    @Override
    public Expression getValue() {
        return super.getValue();
    }

    public boolean isMethod() {
        return method;
    }

    public boolean isProtoDataProperty() {
        if (isComputed() || isShorthand() || method || !"init".equals(kind)) {
            return false;
        }
        Expression key = getKey();
        if (key instanceof Identifier identifier) {
            return "__proto__".equals(identifier.getName());
        }
        if (key instanceof Literal literal && literal.getValue() instanceof String stringValue) {
            return "__proto__".equals(stringValue);
        }
        return false;
    }

}
