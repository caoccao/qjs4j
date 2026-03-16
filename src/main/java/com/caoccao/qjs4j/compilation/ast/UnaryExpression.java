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
 * Represents a unary expression.
 */
public final class UnaryExpression extends Expression {
    private final Expression operand;
    private final UnaryOperator operator;
    private final boolean prefix;
    private Boolean directEvalVarArgumentsInside;

    public UnaryExpression(
            UnaryOperator operator,
            Expression operand,
            boolean prefix,
            SourceLocation location) {
        super(location);
        this.operator = operator;
        this.operand = operand;
        this.prefix = prefix;
        directEvalVarArgumentsInside = null;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = operand != null && operand.containsAwait();
        }
        return awaitInside;
    }

    @Override
    public boolean containsDirectEvalVarArguments() {
        if (directEvalVarArgumentsInside == null) {
            directEvalVarArgumentsInside = operand != null && operand.containsDirectEvalVarArguments();
        }
        return directEvalVarArgumentsInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = operand != null && operand.containsYield();
        }
        return yieldInside;
    }

    public Expression getOperand() {
        return operand;
    }

    public UnaryOperator getOperator() {
        return operator;
    }

    public boolean isPrefix() {
        return prefix;
    }

    public enum UnaryOperator {
        PLUS, MINUS, NOT, BIT_NOT, TYPEOF, VOID, DELETE, INC, DEC
    }
}
