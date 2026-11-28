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
 * Represents a class declaration.
 */
public final class ClassDeclaration extends Declaration {
    private final ClassExpression expression;

    public ClassDeclaration(Identifier id, Expression superClass, List<ClassElement> body, SourceLocation location) {
        super(location);
        expression = new ClassExpression(id, superClass, body, location);
    }

    public ClassExpression asExpression() {
        return expression;
    }

    @Override
    boolean containsSuspension(SyntaxQuery kind) {
        return kind.in(expression);
    }

    public List<ClassElement> getBody() {
        return expression.getBody();
    }

    public Identifier getId() {
        return expression.getId();
    }

    public Expression getSuperClass() {
        return expression.getSuperClass();
    }
}
