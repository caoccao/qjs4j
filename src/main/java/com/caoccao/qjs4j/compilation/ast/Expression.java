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
 * Base sealed class for all expression nodes.
 */
public abstract sealed class Expression implements ASTNode permits
        Literal, Identifier, PrivateIdentifier, BinaryExpression, UnaryExpression,
        AssignmentExpression, ConditionalExpression, CallExpression,
        MemberExpression, NewExpression, FunctionExpression,
        ArrowFunctionExpression, ArrayExpression, ObjectExpression, AwaitExpression,
        YieldExpression, TemplateLiteral, TaggedTemplateExpression, ClassExpression,
        SpreadElement, SequenceExpression, ImportExpression {
    private final SourceLocation location;
    protected Boolean awaitInside;
    protected Boolean yieldInside;

    protected Expression(SourceLocation location) {
        this.location = location;
        awaitInside = null;
        yieldInside = null;
    }

    public abstract boolean containsAwait();

    public abstract boolean containsYield();

    @Override
    public final SourceLocation getLocation() {
        return location;
    }

    public final SourceLocation location() {
        return location;
    }
}
