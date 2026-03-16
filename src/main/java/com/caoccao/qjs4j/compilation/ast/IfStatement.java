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
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = test != null && test.containsAwait();
            if (!awaitInside && consequent != null && consequent.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && alternate != null && alternate.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = test != null && test.containsYield();
            if (!yieldInside && consequent != null && consequent.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && alternate != null && alternate.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
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

    @Override
    public List<VariableDeclarator> getVarDeclarators() {
        if (varDeclarators == null) {
            List<VariableDeclarator> collectedVarDeclarators = new ArrayList<>();
            if (consequent != null) {
                collectedVarDeclarators.addAll(consequent.getVarDeclarators());
            }
            if (alternate != null) {
                collectedVarDeclarators.addAll(alternate.getVarDeclarators());
            }
            varDeclarators = collectedVarDeclarators;
        }
        return varDeclarators;
    }

}
