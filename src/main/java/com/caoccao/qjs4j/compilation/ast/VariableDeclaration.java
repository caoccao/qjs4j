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
 * Represents a variable declaration statement.
 */
public final class VariableDeclaration extends Statement {
    private final List<VariableDeclarator> declarations;
    private final VariableKind kind;

    public VariableDeclaration(List<VariableDeclarator> declarations, VariableKind kind, SourceLocation location) {
        super(location);
        this.declarations = declarations;
        this.kind = kind;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (declarations != null) {
                for (VariableDeclarator declarator : declarations) {
                    if (declarator != null && declarator.containsAwait()) {
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
            if (declarations != null) {
                for (VariableDeclarator declarator : declarations) {
                    if (declarator != null && declarator.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    @Override
    public List<VariableDeclarator> getVarDeclarators() {
        if (varDeclarators == null) {
            varDeclarators = kind == VariableKind.VAR && declarations != null
                    ? declarations
                    : List.of();
        }
        return varDeclarators;
    }

    public List<VariableDeclarator> getDeclarations() {
        return declarations;
    }

    public VariableKind getKind() {
        return kind;
    }

}
