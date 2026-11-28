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
    protected List<VariableDeclarator> computeVarDeclarators() {
        return body != null ? body.getVarDeclarators() : List.of();
    }

    @Override
    boolean containsSuspension(SyntaxQuery kind) {
        return kind.in(body);
    }

    public Statement getBody() {
        return body;
    }

    public Identifier getLabel() {
        return label;
    }

}
