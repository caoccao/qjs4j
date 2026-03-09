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
 * Represents a class expression (class used as an expression, not a declaration).
 * Example: const MyClass = class { ... } or new (class extends Base {})()
 */
public final class ClassExpression extends Expression {
    private final List<ClassElement> body;
    private final Identifier id;
    private final Expression superClass;

    public ClassExpression(
            Identifier id,
            Expression superClass,
            List<ClassElement> body,
            SourceLocation location) {
        super(location);
        this.id = id;
        this.superClass = superClass;
        this.body = body;
    }

    public List<ClassElement> body() {
        return body;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (superClass != null && superClass.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && body != null) {
                for (ClassElement classElement : body) {
                    if (classElement != null && classElement.containsAwait()) {
                        awaitInside = true;
                        break;
                    }
                }
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (superClass != null && superClass.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && body != null) {
                for (ClassElement classElement : body) {
                    if (classElement != null && classElement.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    public Identifier id() {
        return id;
    }

    public Expression superClass() {
        return superClass;
    }
}
