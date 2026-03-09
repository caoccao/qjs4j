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
 * Base sealed class for all AST nodes.
 */
public abstract sealed class ASTNode permits
        Pattern, Statement, ModuleItem, Program, RestParameter,
        VariableDeclaration.VariableDeclarator, TryStatement.CatchClause,
        SwitchStatement.SwitchCase, ClassElement, ObjectPatternProperty,
        ObjectExpressionProperty, ImportSpecifier, ExportSpecifier {
    private final SourceLocation location;
    protected Boolean awaitInside;
    protected Boolean yieldInside;

    protected ASTNode(SourceLocation location) {
        this.location = location;
        this.awaitInside = null;
        this.yieldInside = null;
    }

    public abstract boolean containsAwait();

    public abstract boolean containsYield();

    public final SourceLocation getLocation() {
        return location;
    }

    public final SourceLocation location() {
        return location;
    }
}
