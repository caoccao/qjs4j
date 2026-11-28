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

/** A key/value property shared by object literals and binding patterns. */
public abstract sealed class ObjectProperty<V extends Pattern> extends ASTNode
        permits ObjectPatternProperty, ObjectExpressionProperty {
    private final boolean computed;
    private final Expression key;
    private final boolean shorthand;
    private final V value;

    ObjectProperty(Expression key, V value, boolean computed, boolean shorthand) {
        super(SourceLocation.firstOf(key, value));
        this.key = key;
        this.value = value;
        this.computed = computed;
        this.shorthand = shorthand;
    }

    @Override
    boolean containsSuspension(SyntaxQuery kind) {
        return kind.in(key) || kind.in(value);
    }

    public Expression getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public boolean isComputed() {
        return computed;
    }

    public boolean isShorthand() {
        return shorthand;
    }
}
