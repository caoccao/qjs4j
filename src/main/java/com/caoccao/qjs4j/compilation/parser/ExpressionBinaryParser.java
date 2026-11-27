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

package com.caoccao.qjs4j.compilation.parser;

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

final class ExpressionBinaryParser {
    private final ExpressionParser expressions;
    private final ParserContext parserContext;

    ExpressionBinaryParser(ParserContext parserContext, ExpressionParser expressions) {
        this.parserContext = parserContext;
        this.expressions = expressions;
    }

    private boolean isDisallowedExponentiationUnaryBaseToken(TokenType tokenType) {
        return switch (tokenType) {
            case BIT_NOT, DELETE, MINUS, NOT, PLUS, TYPEOF, VOID -> true;
            default -> false;
        };
    }

    private Expression parseBinaryExpression(int minimumPrecedence) {
        Expression left = minimumPrecedence <= Precedence.RELATIONAL.level && parserContext.inOperatorAllowed
                && parserContext.match(TokenType.PRIVATE_NAME) && parserContext.nextToken.type() == TokenType.IN
                        ? parserContext.consumePrivateName()
                        : parseExponentiationExpression();
        while (true) {
            Infix infix = Infix.BY_TOKEN[parserContext.currentToken.type().ordinal()];
            if (infix == null || infix.precedence.level < minimumPrecedence
                    || (infix.operator == BinaryOperator.IN && !parserContext.inOperatorAllowed)) {
                return left;
            }
            SourceLocation location = parserContext.consumeLocation();
            // Only strictly tighter operators belong to the right operand of a left-associative operator.
            Expression right = parseBinaryExpression(infix.precedence.level + 1);
            left = new BinaryExpression(infix.operator, left, right, location);
        }
    }

    Expression parseConditionalExpression() {
        Expression test = expressions.parseLogicalOrExpression();

        if (parserContext.match(TokenType.QUESTION)) {
            SourceLocation location = parserContext.consumeLocation();
            boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
            parserContext.inOperatorAllowed = true;
            Expression consequent = expressions.parseAssignmentExpression();
            parserContext.inOperatorAllowed = savedInOperatorAllowed;
            parserContext.expect(TokenType.COLON);
            Expression alternate = expressions.parseAssignmentExpression();

            return new ConditionalExpression(test, consequent, alternate, location);
        }

        return test;
    }

    Expression parseExponentiationExpression() {
        TokenType firstTokenType = parserContext.currentToken.type();
        Expression left = expressions.parseUnaryExpression();

        if (parserContext.match(TokenType.EXP)) {
            if (isDisallowedExponentiationUnaryBaseToken(firstTokenType)
                    || (left instanceof AwaitExpression && !parserContext.isParenthesizedExpression(left))) {
                throw new JSSyntaxErrorException("Unary operator used immediately before exponentiation expression");
            }
            SourceLocation location = parserContext.consumeLocation();
            Expression right = parseExponentiationExpression();
            return new BinaryExpression(BinaryOperator.EXP, left, right, location);
        }

        return left;
    }

    Expression parseLogicalAndExpression() {
        Expression left = parseBinaryExpression(Precedence.BIT_OR.level);

        boolean hasLogicalAnd = false;
        while (parserContext.match(TokenType.LOGICAL_AND)) {
            hasLogicalAnd = true;
            SourceLocation location = parserContext.consumeLocation();
            Expression right = parseBinaryExpression(Precedence.BIT_OR.level);
            left = new BinaryExpression(BinaryOperator.LOGICAL_AND, left, right, location);
        }

        if (hasLogicalAnd && parserContext.match(TokenType.NULLISH_COALESCING)) {
            throw new JSSyntaxErrorException("cannot mix '??' with '&&' or '||'");
        }

        return left;
    }

    Expression parseLogicalOrExpression() {
        Expression left = expressions.parseLogicalAndExpression();

        if (parserContext.match(TokenType.NULLISH_COALESCING)) {
            // Parse CoalesceExpression: left ?? right ?? ...
            // Cannot mix with || or && (ES2020 spec)
            do {
                SourceLocation location = parserContext.consumeLocation();
                Expression right = parseBinaryExpression(Precedence.BIT_OR.level);
                left = new BinaryExpression(BinaryOperator.NULLISH_COALESCING, left, right, location);
            } while (parserContext.match(TokenType.NULLISH_COALESCING));
            if (parserContext.match(TokenType.LOGICAL_OR) || parserContext.match(TokenType.LOGICAL_AND)) {
                throw new JSSyntaxErrorException("cannot mix '??' with '&&' or '||'");
            }
            return left;
        }

        boolean hasLogicalOr = false;
        while (parserContext.match(TokenType.LOGICAL_OR)) {
            hasLogicalOr = true;
            SourceLocation location = parserContext.consumeLocation();
            Expression right = expressions.parseLogicalAndExpression();
            left = new BinaryExpression(BinaryOperator.LOGICAL_OR, left, right, location);
        }

        if (hasLogicalOr && parserContext.match(TokenType.NULLISH_COALESCING)) {
            throw new JSSyntaxErrorException("cannot mix '??' with '&&' or '||'");
        }

        return left;
    }

    private record Infix(BinaryOperator operator, Precedence precedence) {
        private static final Infix[] BY_TOKEN = new Infix[TokenType.values().length];

        static {
            bind(TokenType.BIT_OR, BinaryOperator.BIT_OR, Precedence.BIT_OR);
            bind(TokenType.BIT_XOR, BinaryOperator.BIT_XOR, Precedence.BIT_XOR);
            bind(TokenType.BIT_AND, BinaryOperator.BIT_AND, Precedence.BIT_AND);
            bind(TokenType.EQ, BinaryOperator.EQ, Precedence.EQUALITY);
            bind(TokenType.NE, BinaryOperator.NE, Precedence.EQUALITY);
            bind(TokenType.STRICT_EQ, BinaryOperator.STRICT_EQ, Precedence.EQUALITY);
            bind(TokenType.STRICT_NE, BinaryOperator.STRICT_NE, Precedence.EQUALITY);
            bind(TokenType.GE, BinaryOperator.GE, Precedence.RELATIONAL);
            bind(TokenType.GT, BinaryOperator.GT, Precedence.RELATIONAL);
            bind(TokenType.IN, BinaryOperator.IN, Precedence.RELATIONAL);
            bind(TokenType.INSTANCEOF, BinaryOperator.INSTANCEOF, Precedence.RELATIONAL);
            bind(TokenType.LE, BinaryOperator.LE, Precedence.RELATIONAL);
            bind(TokenType.LT, BinaryOperator.LT, Precedence.RELATIONAL);
            bind(TokenType.LSHIFT, BinaryOperator.LSHIFT, Precedence.SHIFT);
            bind(TokenType.RSHIFT, BinaryOperator.RSHIFT, Precedence.SHIFT);
            bind(TokenType.URSHIFT, BinaryOperator.URSHIFT, Precedence.SHIFT);
            bind(TokenType.PLUS, BinaryOperator.ADD, Precedence.ADDITIVE);
            bind(TokenType.MINUS, BinaryOperator.SUB, Precedence.ADDITIVE);
            bind(TokenType.DIV, BinaryOperator.DIV, Precedence.MULTIPLICATIVE);
            bind(TokenType.MOD, BinaryOperator.MOD, Precedence.MULTIPLICATIVE);
            bind(TokenType.MUL, BinaryOperator.MUL, Precedence.MULTIPLICATIVE);
        }

        private static void bind(TokenType token, BinaryOperator operator, Precedence precedence) {
            BY_TOKEN[token.ordinal()] = new Infix(operator, precedence);
        }
    }

    private enum Precedence {
        ADDITIVE(7), BIT_AND(3), BIT_OR(1), BIT_XOR(2), EQUALITY(4), MULTIPLICATIVE(8), RELATIONAL(5), SHIFT(6);
        final int level;
        Precedence(int level) {
            this.level = level;
        }
    }
}
