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

/**
 * Represents a for-of statement: for (variable of iterable) { ... }
 * or async for-of: for await (variable of iterable) { ... }
 * <p>
 * Based on ES2015 for-of loops and ES2018 async iteration.
 */
public record ForOfStatement(
        VariableDeclaration left,    // Variable declaration (let x, const x, var x)
        Expression right,             // Iterable expression
        Statement body,               // Loop body
        boolean isAsync,              // true for 'for await', false for 'for'
        SourceLocation location
) implements Statement {
    @Override
    public SourceLocation getLocation() {
        return location;
    }
}
