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
 * Represents a conditional (ternary) expression.
 */
public final class ConditionalExpression extends Expression {
    private final Expression alternate;
    private final Expression consequent;
    private final Expression test;

    public ConditionalExpression(Expression test, Expression consequent, Expression alternate,
            SourceLocation location) {
        super(location);
        this.test = test;
        this.consequent = consequent;
        this.alternate = alternate;
    }

    @Override
    boolean computeDirectEvalVarArguments() {
        return SyntaxQuery.DIRECT_EVAL_VAR_ARGUMENTS.in(test) || SyntaxQuery.DIRECT_EVAL_VAR_ARGUMENTS.in(consequent)
                || SyntaxQuery.DIRECT_EVAL_VAR_ARGUMENTS.in(alternate);
    }

    @Override
    boolean containsSuspension(SyntaxQuery kind) {
        return kind.in(test) || kind.in(consequent) || kind.in(alternate);
    }

    public Expression getAlternate() {
        return alternate;
    }

    public Expression getConsequent() {
        return consequent;
    }

    public Expression getTest() {
        return test;
    }

    @Override
    public boolean hasTailCallInTailPosition() {
        return getConsequent().hasTailCallInTailPosition() || getAlternate().hasTailCallInTailPosition();
    }
}
