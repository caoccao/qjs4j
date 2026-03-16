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
 * Represents a labeled statement: label: statement
 */
public final class LabeledStatement extends Statement {
    private final Statement body;
    private final Identifier label;

    public LabeledStatement(Identifier label, Statement body, SourceLocation location) {
        super(location);
        this.label = label;
        this.body = body;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = body != null && body.containsAwait();
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = body != null && body.containsYield();
        }
        return yieldInside;
    }

    @Override
    public List<VariableDeclarator> getVarDeclarators() {
        if (varDeclarators == null) {
            varDeclarators = body != null ? body.getVarDeclarators() : List.of();
        }
        return varDeclarators;
    }

    public Statement getBody() {
        return body;
    }

    public Identifier getLabel() {
        return label;
    }

}
