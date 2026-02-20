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

import com.caoccao.qjs4j.compilation.ast.BinaryExpression;
import com.caoccao.qjs4j.compilation.ast.BinaryExpression.BinaryOperator;
import com.caoccao.qjs4j.compilation.ast.ConditionalExpression;
import com.caoccao.qjs4j.compilation.ast.Expression;
import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.compilation.lexer.TokenType;

final class ExpressionBinaryParser {
    private final ParserContext ctx;
    private final ExpressionParser expressions;

    ExpressionBinaryParser(ParserContext ctx, ExpressionParser expressions) {
        this.ctx = ctx;
        this.expressions = expressions;
    }

    Expression parseAdditiveExpression() {
        Expression left = expressions.parseMultiplicativeExpression();

        while (ctx.match(TokenType.PLUS) || ctx.match(TokenType.MINUS)) {
            BinaryOperator op = ctx.match(TokenType.PLUS) ? BinaryOperator.ADD : BinaryOperator.SUB;
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseMultiplicativeExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseBitwiseAndExpression() {
        Expression left = expressions.parseEqualityExpression();

        while (ctx.match(TokenType.BIT_AND)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseEqualityExpression();
            left = new BinaryExpression(BinaryOperator.BIT_AND, left, right, location);
        }

        return left;
    }

    Expression parseBitwiseOrExpression() {
        Expression left = expressions.parseBitwiseXorExpression();

        while (ctx.match(TokenType.BIT_OR)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseBitwiseXorExpression();
            left = new BinaryExpression(BinaryOperator.BIT_OR, left, right, location);
        }

        return left;
    }

    Expression parseBitwiseXorExpression() {
        Expression left = expressions.parseBitwiseAndExpression();

        while (ctx.match(TokenType.BIT_XOR)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseBitwiseAndExpression();
            left = new BinaryExpression(BinaryOperator.BIT_XOR, left, right, location);
        }

        return left;
    }

    Expression parseConditionalExpression() {
        Expression test = expressions.parseLogicalOrExpression();

        if (ctx.match(TokenType.QUESTION)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression consequent = expressions.parseAssignmentExpression();
            ctx.expect(TokenType.COLON);
            Expression alternate = expressions.parseAssignmentExpression();

            return new ConditionalExpression(test, consequent, alternate, location);
        }

        return test;
    }

    Expression parseEqualityExpression() {
        Expression left = expressions.parseRelationalExpression();

        while (ctx.match(TokenType.EQ) || ctx.match(TokenType.NE) ||
                ctx.match(TokenType.STRICT_EQ) || ctx.match(TokenType.STRICT_NE)) {
            BinaryOperator op = switch (ctx.currentToken.type()) {
                case EQ -> BinaryOperator.EQ;
                case NE -> BinaryOperator.NE;
                case STRICT_EQ -> BinaryOperator.STRICT_EQ;
                case STRICT_NE -> BinaryOperator.STRICT_NE;
                default -> null;
            };
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseRelationalExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseExponentiationExpression() {
        Expression left = expressions.parseUnaryExpression();

        if (ctx.match(TokenType.EXP)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseExponentiationExpression();
            return new BinaryExpression(BinaryOperator.EXP, left, right, location);
        }

        return left;
    }

    Expression parseLogicalAndExpression() {
        Expression left = expressions.parseBitwiseOrExpression();

        while (ctx.match(TokenType.LOGICAL_AND)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseBitwiseOrExpression();
            left = new BinaryExpression(BinaryOperator.LOGICAL_AND, left, right, location);
        }

        return left;
    }

    Expression parseLogicalOrExpression() {
        Expression left = expressions.parseLogicalAndExpression();

        while (ctx.match(TokenType.LOGICAL_OR) || ctx.match(TokenType.NULLISH_COALESCING)) {
            BinaryOperator op = ctx.match(TokenType.LOGICAL_OR)
                    ? BinaryOperator.LOGICAL_OR
                    : BinaryOperator.NULLISH_COALESCING;
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseLogicalAndExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseMultiplicativeExpression() {
        Expression left = expressions.parseExponentiationExpression();

        while (ctx.match(TokenType.MUL) || ctx.match(TokenType.DIV) || ctx.match(TokenType.MOD)) {
            BinaryOperator op = switch (ctx.currentToken.type()) {
                case DIV -> BinaryOperator.DIV;
                case MOD -> BinaryOperator.MOD;
                case MUL -> BinaryOperator.MUL;
                default -> null;
            };
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseExponentiationExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseRelationalExpression() {
        Expression left = expressions.parseShiftExpression();

        while (ctx.match(TokenType.LT) || ctx.match(TokenType.LE) ||
                ctx.match(TokenType.GT) || ctx.match(TokenType.GE) ||
                (ctx.inOperatorAllowed && ctx.match(TokenType.IN)) || ctx.match(TokenType.INSTANCEOF)) {
            BinaryOperator op = switch (ctx.currentToken.type()) {
                case LT -> BinaryOperator.LT;
                case LE -> BinaryOperator.LE;
                case GT -> BinaryOperator.GT;
                case GE -> BinaryOperator.GE;
                case IN -> BinaryOperator.IN;
                case INSTANCEOF -> BinaryOperator.INSTANCEOF;
                default -> null;
            };
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseShiftExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseShiftExpression() {
        Expression left = expressions.parseAdditiveExpression();

        while (ctx.match(TokenType.LSHIFT) || ctx.match(TokenType.RSHIFT) || ctx.match(TokenType.URSHIFT)) {
            BinaryOperator op = switch (ctx.currentToken.type()) {
                case LSHIFT -> BinaryOperator.LSHIFT;
                case RSHIFT -> BinaryOperator.RSHIFT;
                case URSHIFT -> BinaryOperator.URSHIFT;
                default -> null;
            };
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseAdditiveExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }
}
