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

import java.util.ArrayList;
import java.util.List;

/** A for loop shares its head declaration and body with the scope and hoisting analyses. */
public abstract sealed class ForLoop extends Statement permits ForStatement, ForEachStatement {
    protected final Statement body;
    protected final ASTNode head;

    protected ForLoop(ASTNode head, Statement body, SourceLocation location) {
        super(location);
        this.head = head;
        this.body = body;
    }

    @Override
    protected List<VariableDeclarator> computeVarDeclarators() {
        List<VariableDeclarator> collectedVarDeclarators = new ArrayList<>();
        if (head instanceof VariableDeclaration variableDeclaration
                && variableDeclaration.getKind() == VariableKind.VAR) {
            collectedVarDeclarators.addAll(variableDeclaration.getDeclarations());
        }
        appendVarDeclarators(collectedVarDeclarators, body);
        return collectedVarDeclarators;
    }

    public Statement getBody() {
        return body;
    }

    public ASTNode getHead() {
        return head;
    }
}
