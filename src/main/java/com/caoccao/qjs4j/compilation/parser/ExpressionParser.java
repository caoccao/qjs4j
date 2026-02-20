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

import com.caoccao.qjs4j.compilation.ast.Expression;
import com.caoccao.qjs4j.compilation.ast.SourceLocation;

/**
 * Expression parsing coordinator.
 * Delegates assignment parsing, precedence parsing, and primary/member parsing
 * to focused parser components.
 */
final class ExpressionParser {
    private final ExpressionAssignmentParser assignmentParser;
    private final ExpressionBinaryParser binaryParser;
    private final ExpressionPrimaryParser primaryParser;

    ExpressionParser(ParserContext ctx, ParserDelegates delegates) {
        this.primaryParser = new ExpressionPrimaryParser(ctx, delegates, this);
        this.binaryParser = new ExpressionBinaryParser(ctx, this);
        this.assignmentParser = new ExpressionAssignmentParser(ctx, delegates, this);
    }

    Expression parseAdditiveExpression() {
        return binaryParser.parseAdditiveExpression();
    }

    Expression parseAssignmentExpression() {
        return assignmentParser.parseAssignmentExpression();
    }

    Expression parseBitwiseAndExpression() {
        return binaryParser.parseBitwiseAndExpression();
    }

    Expression parseBitwiseOrExpression() {
        return binaryParser.parseBitwiseOrExpression();
    }

    Expression parseBitwiseXorExpression() {
        return binaryParser.parseBitwiseXorExpression();
    }

    Expression parseCallExpression() {
        return primaryParser.parseCallExpression();
    }

    Expression parseConditionalExpression() {
        return binaryParser.parseConditionalExpression();
    }

    Expression parseEqualityExpression() {
        return binaryParser.parseEqualityExpression();
    }

    Expression parseExponentiationExpression() {
        return binaryParser.parseExponentiationExpression();
    }

    Expression parseExpression() {
        return assignmentParser.parseExpression();
    }

    Expression parseLogicalAndExpression() {
        return binaryParser.parseLogicalAndExpression();
    }

    Expression parseLogicalOrExpression() {
        return binaryParser.parseLogicalOrExpression();
    }

    Expression parseMemberExpression() {
        return primaryParser.parseMemberExpression();
    }

    Expression parseMultiplicativeExpression() {
        return binaryParser.parseMultiplicativeExpression();
    }

    Expression parsePostPrimaryExpression(Expression expr, SourceLocation location) {
        return primaryParser.parsePostPrimaryExpression(expr, location);
    }

    Expression parsePostfixExpression() {
        return primaryParser.parsePostfixExpression();
    }

    Expression parsePrimaryExpression() {
        return primaryParser.parsePrimaryExpression();
    }

    Expression parsePropertyName() {
        return primaryParser.parsePropertyName();
    }

    Expression parseRelationalExpression() {
        return binaryParser.parseRelationalExpression();
    }

    Expression parseShiftExpression() {
        return binaryParser.parseShiftExpression();
    }

    Expression parseUnaryExpression() {
        return primaryParser.parseUnaryExpression();
    }
}
