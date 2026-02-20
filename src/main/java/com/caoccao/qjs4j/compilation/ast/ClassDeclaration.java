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
public record ClassDeclaration(
        Identifier id,
        Expression superClass,
        List<ClassElement> body,
        SourceLocation location
) implements Declaration {
    @Override
    public SourceLocation getLocation() {
        return location;
    }

    /**
     * Base interface for class elements (methods, fields, static blocks)
     */
    public sealed interface ClassElement permits MethodDefinition, PropertyDefinition, StaticBlock {
    }

    /**
     * Represents a method in a class
     */
    public record MethodDefinition(
            Expression key,
            FunctionExpression value,
            String kind,
            boolean computed,
            boolean isStatic,
            boolean isPrivate
    ) implements ClassElement {
    }

    /**
     * Represents a field in a class (public or private)
     */
    public record PropertyDefinition(
            Expression key,
            Expression value,  // initializer expression, can be null
            boolean computed,
            boolean isStatic,
            boolean isPrivate
    ) implements ClassElement {
    }

    /**
     * Represents a static initialization block
     */
    public record StaticBlock(
            List<Statement> body
    ) implements ClassElement {
    }
}
