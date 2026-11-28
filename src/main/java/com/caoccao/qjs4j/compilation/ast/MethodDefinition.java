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
public final class MethodDefinition extends ClassMember<FunctionExpression> {
    private final String kind;

    public MethodDefinition(Expression key, FunctionExpression value, String kind, boolean computed, boolean isStatic,
            boolean isPrivate) {
        super(key, value, computed, isStatic, isPrivate);
        this.kind = kind;
    }

    public String getKind() {
        return kind;
    }

    public String getSimpleName() {
        if (getKey() instanceof Identifier id) {
            return id.getName();
        } else if (getKey() instanceof Literal literal) {
            return literal.getValue() != null ? literal.getValue().toString() : "null";
        } else if (getKey() instanceof PrivateIdentifier privateId) {
            return privateId.getName();
        } else {
            return "[computed]";
        }
    }

    @Override
    public FunctionExpression getValue() {
        return super.getValue();
    }

    /**
     * Per ES2024, only non-static, non-computed "constructor" defines a class constructor method.
     */
    public boolean isConstructor() {
        if (isStatic() || isComputed()) {
            return false;
        }
        if (getKey() instanceof Identifier id) {
            return "constructor".equals(id.getName());
        }
        if (getKey() instanceof Literal literal && literal.getValue() instanceof String name) {
            return "constructor".equals(name);
        }
        return false;
    }

}
