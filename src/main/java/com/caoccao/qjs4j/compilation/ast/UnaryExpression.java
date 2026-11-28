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

import com.caoccao.qjs4j.compilation.lexer.TokenType;

/**
 * Represents a unary expression.
 */
public final class UnaryExpression extends Expression {
    private final Expression operand;
    private final UnaryOperator operator;
    private final boolean prefix;

    public UnaryExpression(UnaryOperator operator, Expression operand, boolean prefix, SourceLocation location) {
        super(location);
        this.operator = operator;
        this.operand = operand;
        this.prefix = prefix;
    }

    @Override
    boolean computeDirectEvalVarArguments() {
        return SyntaxQuery.DIRECT_EVAL_VAR_ARGUMENTS.in(operand);
    }

    @Override
    boolean containsSuspension(SyntaxQuery kind) {
        return kind.in(operand);
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
        BIT_NOT(TokenType.BIT_NOT),
        DEC(TokenType.DEC),
        DELETE(TokenType.DELETE),
        INC(TokenType.INC),
        MINUS(TokenType.MINUS),
        NOT(TokenType.NOT),
        PLUS(TokenType.PLUS),
        TYPEOF(TokenType.TYPEOF),
        VOID(TokenType.VOID);

        private static final UnaryOperator[] PREFIX = new UnaryOperator[TokenType.values().length];
        static {
            for (UnaryOperator operator : values()) {
                if (operator != INC && operator != DEC) {
                    PREFIX[operator.token.ordinal()] = operator;
                }
            }
        }
        private final String displayText;

        private final TokenType token;

        UnaryOperator(TokenType token) {
            this.token = token;
            displayText = token.isIdentifierName() ? token.fixedText() + " " : token.fixedText();
        }

        public String displayText() {
            return displayText;
        }

        public static UnaryOperator prefixOperator(TokenType token) {
            return PREFIX[token.ordinal()];
        }
    }
}
