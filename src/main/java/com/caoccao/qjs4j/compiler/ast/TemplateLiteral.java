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

package com.caoccao.qjs4j.compiler.ast;

import java.util.List;

/**
 * Template literal expression.
 * Represents template strings like `hello ${world}`.
 *
 * @param quasis      List of template element strings (the static parts)
 * @param expressions List of expressions to be interpolated
 * @param location    Source location
 */
public record TemplateLiteral(
        List<String> quasis,
        List<Expression> expressions,
        SourceLocation location
) implements Expression {
    @Override
    public SourceLocation getLocation() {
        return location;
    }
}
