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

/**
 * Represents an if statement.
 */
public final class IfStatement extends Statement {
    private final Statement alternate;
    private final Statement consequent;
    private final Expression test;

    public IfStatement(Expression test, Statement consequent, Statement alternate, SourceLocation location) {
        super(location);
        this.test = test;
        this.consequent = consequent;
        this.alternate = alternate;
    }

    @Override
    protected List<VariableDeclarator> computeVarDeclarators() {
        List<VariableDeclarator> collectedVarDeclarators = new ArrayList<>();
        appendVarDeclarators(collectedVarDeclarators, consequent);
        appendVarDeclarators(collectedVarDeclarators, alternate);
        return collectedVarDeclarators;
    }

    @Override
    boolean containsSuspension(SyntaxQuery kind) {
        return kind.in(test) || kind.in(consequent) || kind.in(alternate);
    }

    public Statement getAlternate() {
        return alternate;
    }

    public Statement getConsequent() {
        return consequent;
    }

    public Expression getTest() {
        return test;
    }

}
