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

    Expression parseAdditiveExpression() {
        Expression left = expressions.parseMultiplicativeExpression();

        while (parserContext.match(TokenType.PLUS) || parserContext.match(TokenType.MINUS)) {
            BinaryOperator op = parserContext.match(TokenType.PLUS) ? BinaryOperator.ADD : BinaryOperator.SUB;
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseMultiplicativeExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseBitwiseAndExpression() {
        Expression left = expressions.parseEqualityExpression();

        while (parserContext.match(TokenType.BIT_AND)) {
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseEqualityExpression();
            left = new BinaryExpression(BinaryOperator.BIT_AND, left, right, location);
        }

        return left;
    }

    Expression parseBitwiseOrExpression() {
        Expression left = expressions.parseBitwiseXorExpression();

        while (parserContext.match(TokenType.BIT_OR)) {
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseBitwiseXorExpression();
            left = new BinaryExpression(BinaryOperator.BIT_OR, left, right, location);
        }

        return left;
    }

    Expression parseBitwiseXorExpression() {
        Expression left = expressions.parseBitwiseAndExpression();

        while (parserContext.match(TokenType.BIT_XOR)) {
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseBitwiseAndExpression();
            left = new BinaryExpression(BinaryOperator.BIT_XOR, left, right, location);
        }

        return left;
    }

    Expression parseConditionalExpression() {
        Expression test = expressions.parseLogicalOrExpression();

        if (parserContext.match(TokenType.QUESTION)) {
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
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

    Expression parseEqualityExpression() {
        Expression left = expressions.parseRelationalExpression();

        while (parserContext.match(TokenType.EQ) || parserContext.match(TokenType.NE) ||
                parserContext.match(TokenType.STRICT_EQ) || parserContext.match(TokenType.STRICT_NE)) {
            BinaryOperator op = switch (parserContext.currentToken.type()) {
                case EQ -> BinaryOperator.EQ;
                case NE -> BinaryOperator.NE;
                case STRICT_EQ -> BinaryOperator.STRICT_EQ;
                case STRICT_NE -> BinaryOperator.STRICT_NE;
                default -> null;
            };
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseRelationalExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseExponentiationExpression() {
        TokenType firstTokenType = parserContext.currentToken.type();
        Expression left = expressions.parseUnaryExpression();

        if (parserContext.match(TokenType.EXP)) {
            if (isDisallowedExponentiationUnaryBaseToken(firstTokenType)
                    || (left instanceof AwaitExpression
                    && !parserContext.isParenthesizedExpression(left))) {
                throw new JSSyntaxErrorException("Unary operator used immediately before exponentiation expression");
            }
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = parseExponentiationExpression();
            return new BinaryExpression(BinaryOperator.EXP, left, right, location);
        }

        return left;
    }

    Expression parseLogicalAndExpression() {
        Expression left = expressions.parseBitwiseOrExpression();

        boolean hasLogicalAnd = false;
        while (parserContext.match(TokenType.LOGICAL_AND)) {
            hasLogicalAnd = true;
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseBitwiseOrExpression();
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
                SourceLocation location = parserContext.getLocation();
                parserContext.advance();
                Expression right = expressions.parseBitwiseOrExpression();
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
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseLogicalAndExpression();
            left = new BinaryExpression(BinaryOperator.LOGICAL_OR, left, right, location);
        }

        if (hasLogicalOr && parserContext.match(TokenType.NULLISH_COALESCING)) {
            throw new JSSyntaxErrorException("cannot mix '??' with '&&' or '||'");
        }

        return left;
    }

    Expression parseMultiplicativeExpression() {
        Expression left = expressions.parseExponentiationExpression();

        while (parserContext.match(TokenType.MUL) || parserContext.match(TokenType.DIV) || parserContext.match(TokenType.MOD)) {
            BinaryOperator op = switch (parserContext.currentToken.type()) {
                case DIV -> BinaryOperator.DIV;
                case MOD -> BinaryOperator.MOD;
                case MUL -> BinaryOperator.MUL;
                default -> null;
            };
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseExponentiationExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseRelationalExpression() {
        Expression left = expressions.parseShiftExpression();

        while (parserContext.match(TokenType.LT) || parserContext.match(TokenType.LE) ||
                parserContext.match(TokenType.GT) || parserContext.match(TokenType.GE) ||
                (parserContext.inOperatorAllowed && parserContext.match(TokenType.IN)) || parserContext.match(TokenType.INSTANCEOF)) {
            BinaryOperator op = switch (parserContext.currentToken.type()) {
                case LT -> BinaryOperator.LT;
                case LE -> BinaryOperator.LE;
                case GT -> BinaryOperator.GT;
                case GE -> BinaryOperator.GE;
                case IN -> BinaryOperator.IN;
                case INSTANCEOF -> BinaryOperator.INSTANCEOF;
                default -> null;
            };
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseShiftExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseShiftExpression() {
        Expression left = expressions.parseAdditiveExpression();

        while (parserContext.match(TokenType.LSHIFT) || parserContext.match(TokenType.RSHIFT) || parserContext.match(TokenType.URSHIFT)) {
            BinaryOperator op = switch (parserContext.currentToken.type()) {
                case LSHIFT -> BinaryOperator.LSHIFT;
                case RSHIFT -> BinaryOperator.RSHIFT;
                case URSHIFT -> BinaryOperator.URSHIFT;
                default -> null;
            };
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression right = expressions.parseAdditiveExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }
}
