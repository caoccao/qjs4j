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

    private ExpressionBinaryParser() {
    }

    private static boolean isDisallowedExponentiationUnaryBaseToken(TokenType tokenType) {
        return UnaryExpression.UnaryOperator.prefixOperator(tokenType) != null;
    }

    private static Expression parseBinaryExpression(ParserContext parserContext, int minimumPrecedence) {
        TokenCursor tokens = parserContext.tokens;
        Expression left = minimumPrecedence <= BinaryOperator.Precedence.RELATIONAL.level
                && parserContext.inOperatorAllowed && tokens.match(TokenType.PRIVATE_NAME)
                && tokens.nextIs(TokenType.IN)
                        ? parserContext.consumePrivateName()
                        : parseExponentiationExpression(parserContext);
        while (true) {
            BinaryOperator infix = BinaryOperator.infixOperator(tokens.current().type());
            if (infix == null || infix.precedence().level < minimumPrecedence
                    || (infix == BinaryOperator.IN && !parserContext.inOperatorAllowed)) {
                return left;
            }
            SourceLocation location = tokens.consumeLocation();
            // Only strictly tighter operators belong to the right operand of a left-associative operator.
            Expression right = parseBinaryExpression(parserContext, infix.precedence().level + 1);
            left = new BinaryExpression(infix, left, right, location);
        }
    }

    static Expression parseConditionalExpression(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        Expression test = ExpressionBinaryParser.parseLogicalOrExpression(parserContext);

        if (tokens.match(TokenType.QUESTION)) {
            SourceLocation location = tokens.consumeLocation();
            boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
            parserContext.inOperatorAllowed = true;
            Expression consequent = ExpressionAssignmentParser.parseAssignmentExpression(parserContext);
            parserContext.inOperatorAllowed = savedInOperatorAllowed;
            tokens.expect(TokenType.COLON);
            Expression alternate = ExpressionAssignmentParser.parseAssignmentExpression(parserContext);

            return new ConditionalExpression(test, consequent, alternate, location);
        }

        return test;
    }

    static Expression parseExponentiationExpression(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        TokenType firstTokenType = tokens.current().type();
        Expression left = ExpressionPrimaryParser.parseUnaryExpression(parserContext);

        if (tokens.match(TokenType.EXP)) {
            if (isDisallowedExponentiationUnaryBaseToken(firstTokenType)
                    || (left instanceof AwaitExpression && !parserContext.isParenthesizedExpression(left))) {
                throw new JSSyntaxErrorException("Unary operator used immediately before exponentiation expression");
            }
            SourceLocation location = tokens.consumeLocation();
            Expression right = parseExponentiationExpression(parserContext);
            return new BinaryExpression(BinaryOperator.EXP, left, right, location);
        }

        return left;
    }

    static Expression parseLogicalAndExpression(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        Expression left = parseBinaryExpression(parserContext, BinaryOperator.Precedence.BIT_OR.level);

        boolean hasLogicalAnd = false;
        while (tokens.match(TokenType.LOGICAL_AND)) {
            hasLogicalAnd = true;
            SourceLocation location = tokens.consumeLocation();
            Expression right = parseBinaryExpression(parserContext, BinaryOperator.Precedence.BIT_OR.level);
            left = new BinaryExpression(BinaryOperator.LOGICAL_AND, left, right, location);
        }

        if (hasLogicalAnd && tokens.match(TokenType.NULLISH_COALESCING)) {
            throw new JSSyntaxErrorException("cannot mix '??' with '&&' or '||'");
        }

        return left;
    }

    static Expression parseLogicalOrExpression(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        Expression left = ExpressionBinaryParser.parseLogicalAndExpression(parserContext);

        if (tokens.match(TokenType.NULLISH_COALESCING)) {
            // Parse CoalesceExpression: left ?? right ?? ...
            // Cannot mix with || or && (ES2020 spec)
            do {
                SourceLocation location = tokens.consumeLocation();
                Expression right = parseBinaryExpression(parserContext, BinaryOperator.Precedence.BIT_OR.level);
                left = new BinaryExpression(BinaryOperator.NULLISH_COALESCING, left, right, location);
            } while (tokens.match(TokenType.NULLISH_COALESCING));
            if (tokens.match(TokenType.LOGICAL_OR) || tokens.match(TokenType.LOGICAL_AND)) {
                throw new JSSyntaxErrorException("cannot mix '??' with '&&' or '||'");
            }
            return left;
        }

        boolean hasLogicalOr = false;
        while (tokens.match(TokenType.LOGICAL_OR)) {
            hasLogicalOr = true;
            SourceLocation location = tokens.consumeLocation();
            Expression right = ExpressionBinaryParser.parseLogicalAndExpression(parserContext);
            left = new BinaryExpression(BinaryOperator.LOGICAL_OR, left, right, location);
        }

        if (hasLogicalOr && tokens.match(TokenType.NULLISH_COALESCING)) {
            throw new JSSyntaxErrorException("cannot mix '??' with '&&' or '||'");
        }

        return left;
    }

}
