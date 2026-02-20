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

import com.caoccao.qjs4j.compilation.Lexer;
import com.caoccao.qjs4j.compilation.Token;
import com.caoccao.qjs4j.compilation.TokenType;
import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.compilation.ast.BinaryExpression.BinaryOperator;
import com.caoccao.qjs4j.compilation.ast.UnaryExpression.UnaryOperator;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.regexp.RegExpLiteralValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Expression parsing delegate.
 * Handles all expression parsing: primary, unary, binary, assignment,
 * conditional, call, member access, and operator precedence.
 */
record ExpressionParser(ParserContext ctx, ParserDelegates delegates) {

    Expression parseAdditiveExpression() {
        Expression left = parseMultiplicativeExpression();

        while (ctx.match(TokenType.PLUS) || ctx.match(TokenType.MINUS)) {
            BinaryOperator op = ctx.match(TokenType.PLUS) ? BinaryOperator.ADD : BinaryOperator.SUB;
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseMultiplicativeExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseAssignmentExpression() {
        SourceLocation location = ctx.getLocation();

        // Check for async arrow function: async () => {} or async (params) => {}
        // Following QuickJS's approach: save state before consuming 'async', try all
        // async patterns, and restore state if none match so the normal expression
        // parser handles 'async' as a regular identifier.
        if (ctx.match(TokenType.ASYNC)) {
            SourceLocation asyncLocation = location;
            Token savedCurrent = ctx.currentToken;
            Token savedNext = ctx.nextToken;
            int savedPrevLine = ctx.previousTokenLine;
            Lexer.LexerState savedLexer = ctx.lexer.saveState();

            ctx.advance(); // consume 'async'

            // Line terminator after 'async' means it's an identifier, not async keyword
            if (!ctx.hasNewlineBefore()) {
                // Async function expression: async function (...) {}
                // Continue with member/call chain so e.g. `async function*() {}.prototype` works.
                if (ctx.match(TokenType.FUNCTION)) {
                    Expression asyncFunc = delegates.functions.parseFunctionExpression(true, asyncLocation);
                    return parsePostPrimaryExpression(asyncFunc, location);
                }

                // Async arrow function with single identifier parameter: async x => x
                if (ctx.match(TokenType.IDENTIFIER) && ctx.nextToken.type() == TokenType.ARROW) {
                    Identifier param = ctx.parseIdentifier();
                    ctx.expect(TokenType.ARROW);

                    ASTNode body;
                    ctx.enterFunctionContext(true);
                    try {
                        if (ctx.match(TokenType.LBRACE)) {
                            body = delegates.statements.parseBlockStatement();
                        } else {
                            body = parseAssignmentExpression();
                        }
                    } finally {
                        ctx.exitFunctionContext(true);
                    }

                    SourceLocation fullLocation = new SourceLocation(
                            asyncLocation.line(),
                            asyncLocation.column(),
                            asyncLocation.offset(),
                            ctx.currentToken.offset()
                    );
                    return new ArrowFunctionExpression(List.of(param), null, null, body, true, fullLocation);
                }

                // Async arrow function with parenthesized parameters: async (...) => {}
                // Use peekPastParensIsArrow() to check without consuming tokens
                if (ctx.match(TokenType.LPAREN) && ctx.peekPastParensIsArrow()) {
                    ctx.enterFunctionContext(true);
                    try {
                        ctx.advance(); // consume '('
                        FunctionParams funcParams = delegates.functions.parseFunctionParameters();
                        ctx.advance(); // consume '=>' (confirmed by peekPastParensIsArrow)

                        ASTNode body;
                        if (ctx.match(TokenType.LBRACE)) {
                            body = delegates.statements.parseBlockStatement();
                        } else {
                            body = parseAssignmentExpression();
                        }

                        SourceLocation fullLocation = new SourceLocation(
                                location.line(),
                                location.column(),
                                location.offset(),
                                ctx.currentToken.offset()
                        );
                        return new ArrowFunctionExpression(funcParams.params(), funcParams.defaults(), funcParams.restParameter(), body, true, fullLocation);
                    } finally {
                        ctx.exitFunctionContext(true);
                    }
                }
            }

            // Not an async function/arrow. Restore state so 'async' is treated
            // as a regular identifier by the normal expression parser.
            ctx.currentToken = savedCurrent;
            ctx.nextToken = savedNext;
            ctx.previousTokenLine = savedPrevLine;
            ctx.lexer.restoreState(savedLexer);
        }

        Expression left = parseConditionalExpression();

        // After parsing conditional expression, check if it's actually an arrow function
        // Pattern: identifier => expr  OR  (params) => expr
        if (ctx.match(TokenType.ARROW)) {
            // Convert the parsed expression to arrow function parameters
            List<Identifier> params = new ArrayList<>();
            List<Expression> defaults = new ArrayList<>();
            RestParameter restParameter = null;

            if (left instanceof Identifier) {
                // Single parameter without parentheses: x => x + 1
                params.add((Identifier) left);
                defaults.add(null);
            } else if (left instanceof AssignmentExpression assignExpr
                    && assignExpr.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                    && assignExpr.left() instanceof Identifier paramId) {
                // Single parameter with default: (x = defaultExpr) => body
                params.add(paramId);
                defaults.add(assignExpr.right());
            } else if (left instanceof SequenceExpression seqExpr) {
                // Multiple parameters possibly with defaults: (x = a, y = b) => body
                for (Expression expr : seqExpr.expressions()) {
                    if (expr instanceof Identifier id) {
                        params.add(id);
                        defaults.add(null);
                    } else if (expr instanceof AssignmentExpression ae
                            && ae.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                            && ae.left() instanceof Identifier aeParamId) {
                        params.add(aeParamId);
                        defaults.add(ae.right());
                    } else {
                        throw new RuntimeException("Invalid arrow function parameter at line " +
                                ctx.currentToken.line() + ", column " + ctx.currentToken.column());
                    }
                }
            } else if (left instanceof ArrayExpression arrayExpr) {
                // ArrayExpression is used as a marker for:
                // 1. Empty parameter list: () => expr
                // 2. Multiple parameters: (x, y, z) => expr
                // 3. Parameters with rest: (x, ...rest) => expr
                if (arrayExpr.elements().isEmpty()) {
                    // Empty parameter list
                    // params stays empty
                } else {
                    // Extract parameters and check for rest parameter
                    for (int i = 0; i < arrayExpr.elements().size(); i++) {
                        Expression expr = arrayExpr.elements().get(i);

                        if (expr instanceof SpreadElement spreadElem) {
                            // Rest parameter
                            if (spreadElem.argument() instanceof Identifier restId) {
                                restParameter = new RestParameter(restId, spreadElem.getLocation());

                                // Rest parameter must be last
                                if (i != arrayExpr.elements().size() - 1) {
                                    throw new JSSyntaxErrorException("Rest parameter must be last formal parameter");
                                }
                            } else {
                                throw new RuntimeException("Invalid rest parameter at line " +
                                        ctx.currentToken.line() + ", column " + ctx.currentToken.column());
                            }
                        } else if (expr instanceof Identifier id) {
                            params.add(id);
                            defaults.add(null);
                        } else if (expr instanceof AssignmentExpression ae
                                && ae.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                                && ae.left() instanceof Identifier aeParamId) {
                            params.add(aeParamId);
                            defaults.add(ae.right());
                        } else {
                            throw new RuntimeException("Invalid arrow function parameter at line " +
                                    ctx.currentToken.line() + ", column " + ctx.currentToken.column());
                        }
                    }
                }
            } else {
                // Could be other complex cases that we don't support yet
                throw new RuntimeException("Unsupported arrow function parameters at line " +
                        ctx.currentToken.line() + ", column " + ctx.currentToken.column());
            }

            ctx.advance(); // consume '=>'

            // Parse body
            ASTNode body;
            ctx.enterFunctionContext(false);
            try {
                if (ctx.match(TokenType.LBRACE)) {
                    body = delegates.statements.parseBlockStatement();
                } else {
                    // Expression body
                    body = parseAssignmentExpression();
                }
            } finally {
                ctx.exitFunctionContext(false);
            }

            // Update location to include end offset
            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    ctx.currentToken.offset()
            );

            return new ArrowFunctionExpression(params, defaults, restParameter, body, false, fullLocation);
        }

        if (ctx.isAssignmentOperator(ctx.currentToken.type())) {
            // Validate that left is a valid assignment target
            // CallExpression is a valid LeftHandSideExpression syntactically;
            // the error is a runtime ReferenceError, not a parse-time SyntaxError.
            if (!(left instanceof Identifier)
                    && !(left instanceof MemberExpression)
                    && !(left instanceof ArrayExpression)
                    && !(left instanceof ObjectExpression)
                    && !(left instanceof CallExpression)) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }

            TokenType op = ctx.currentToken.type();
            location = ctx.getLocation();
            ctx.advance();
            Expression right = parseAssignmentExpression();

            AssignmentExpression.AssignmentOperator operator = switch (op) {
                case ASSIGN -> AssignmentExpression.AssignmentOperator.ASSIGN;
                case AND_ASSIGN -> AssignmentExpression.AssignmentOperator.AND_ASSIGN;
                case DIV_ASSIGN -> AssignmentExpression.AssignmentOperator.DIV_ASSIGN;
                case EXP_ASSIGN -> AssignmentExpression.AssignmentOperator.EXP_ASSIGN;
                case LOGICAL_AND_ASSIGN -> AssignmentExpression.AssignmentOperator.LOGICAL_AND_ASSIGN;
                case LOGICAL_OR_ASSIGN -> AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN;
                case LSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.LSHIFT_ASSIGN;
                case MINUS_ASSIGN -> AssignmentExpression.AssignmentOperator.MINUS_ASSIGN;
                case MOD_ASSIGN -> AssignmentExpression.AssignmentOperator.MOD_ASSIGN;
                case MUL_ASSIGN -> AssignmentExpression.AssignmentOperator.MUL_ASSIGN;
                case NULLISH_ASSIGN -> AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN;
                case OR_ASSIGN -> AssignmentExpression.AssignmentOperator.OR_ASSIGN;
                case PLUS_ASSIGN -> AssignmentExpression.AssignmentOperator.PLUS_ASSIGN;
                case RSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.RSHIFT_ASSIGN;
                case URSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.URSHIFT_ASSIGN;
                case XOR_ASSIGN -> AssignmentExpression.AssignmentOperator.XOR_ASSIGN;
                default -> AssignmentExpression.AssignmentOperator.ASSIGN;
            };

            return new AssignmentExpression(left, operator, right, location);
        }

        return left;
    }

    Expression parseBitwiseAndExpression() {
        Expression left = parseEqualityExpression();

        while (ctx.match(TokenType.BIT_AND)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseEqualityExpression();
            left = new BinaryExpression(BinaryOperator.BIT_AND, left, right, location);
        }

        return left;
    }

    Expression parseBitwiseOrExpression() {
        Expression left = parseBitwiseXorExpression();

        while (ctx.match(TokenType.BIT_OR)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseBitwiseXorExpression();
            left = new BinaryExpression(BinaryOperator.BIT_OR, left, right, location);
        }

        return left;
    }

    Expression parseBitwiseXorExpression() {
        Expression left = parseBitwiseAndExpression();

        while (ctx.match(TokenType.BIT_XOR)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseBitwiseAndExpression();
            left = new BinaryExpression(BinaryOperator.BIT_XOR, left, right, location);
        }

        return left;
    }

    Expression parseCallExpression() {
        Expression expr = parseMemberExpression();

        while (true) {
            if (ctx.match(TokenType.TEMPLATE)) {
                // Tagged template: expr`template`
                SourceLocation location = ctx.getLocation();
                TemplateLiteral template = delegates.literals.parseTemplateLiteral(true);
                expr = new TaggedTemplateExpression(expr, template, location);
            } else if (ctx.match(TokenType.LPAREN)) {
                SourceLocation location = ctx.getLocation();
                ctx.advance();
                List<Expression> args = new ArrayList<>();

                if (!ctx.match(TokenType.RPAREN)) {
                    do {
                        if (ctx.match(TokenType.COMMA)) {
                            ctx.advance();
                            // Handle trailing comma
                            if (ctx.match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (ctx.match(TokenType.ELLIPSIS)) {
                            // Spread argument: ...expr
                            SourceLocation spreadLocation = ctx.getLocation();
                            ctx.advance(); // consume ELLIPSIS
                            Expression argument = parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLocation));
                        } else {
                            args.add(parseAssignmentExpression());
                        }
                    } while (ctx.match(TokenType.COMMA));
                }

                ctx.expect(TokenType.RPAREN);
                expr = new CallExpression(expr, args, location);
            } else if (ctx.match(TokenType.DOT)) {
                SourceLocation location = ctx.getLocation();
                ctx.advance();
                Expression property = parsePropertyName();
                expr = new MemberExpression(expr, property, false, location);
            } else if (ctx.match(TokenType.LBRACKET)) {
                SourceLocation location = ctx.getLocation();
                ctx.advance();
                Expression property = parseAssignmentExpression();
                ctx.expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, location);
            } else {
                break;
            }
        }

        return expr;
    }

    Expression parseConditionalExpression() {
        Expression test = parseLogicalOrExpression();

        if (ctx.match(TokenType.QUESTION)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression consequent = parseAssignmentExpression();
            ctx.expect(TokenType.COLON);
            Expression alternate = parseAssignmentExpression();

            return new ConditionalExpression(test, consequent, alternate, location);
        }

        return test;
    }

    Expression parseEqualityExpression() {
        Expression left = parseRelationalExpression();

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
            Expression right = parseRelationalExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseExponentiationExpression() {
        Expression left = parseUnaryExpression();

        if (ctx.match(TokenType.EXP)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseExponentiationExpression(); // right-associative
            return new BinaryExpression(BinaryOperator.EXP, left, right, location);
        }

        return left;
    }

    Expression parseExpression() {
        SourceLocation location = ctx.getLocation();
        List<Expression> expressions = new ArrayList<>();

        expressions.add(parseAssignmentExpression());

        // Check for comma operator
        while (ctx.match(TokenType.COMMA)) {
            ctx.advance(); // consume comma
            expressions.add(parseAssignmentExpression());
        }

        // If only one expression, return it directly (no sequence)
        if (expressions.size() == 1) {
            return expressions.get(0);
        }

        // Multiple expressions - return a SequenceExpression
        return new SequenceExpression(expressions, location);
    }

    Expression parseLogicalAndExpression() {
        Expression left = parseBitwiseOrExpression();

        while (ctx.match(TokenType.LOGICAL_AND)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseBitwiseOrExpression();
            left = new BinaryExpression(BinaryOperator.LOGICAL_AND, left, right, location);
        }

        return left;
    }

    Expression parseLogicalOrExpression() {
        Expression left = parseLogicalAndExpression();

        while (ctx.match(TokenType.LOGICAL_OR) || ctx.match(TokenType.NULLISH_COALESCING)) {
            BinaryOperator op = ctx.match(TokenType.LOGICAL_OR) ?
                    BinaryOperator.LOGICAL_OR : BinaryOperator.NULLISH_COALESCING;
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseLogicalAndExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseMemberExpression() {
        if (ctx.match(TokenType.NEW)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression callee = parseMemberExpression();

            // Parse member access after the callee so
            // `new Intl.DateTimeFormat()` binds as `new (Intl.DateTimeFormat)()`
            // instead of `(new Intl).DateTimeFormat()`.
            while (true) {
                if (ctx.match(TokenType.DOT)) {
                    ctx.advance();
                    SourceLocation memberLocation = ctx.getLocation();
                    Expression property = parsePropertyName();
                    callee = new MemberExpression(callee, property, false, memberLocation);
                } else if (ctx.match(TokenType.LBRACKET)) {
                    ctx.advance();
                    SourceLocation memberLocation = ctx.getLocation();
                    Expression property = parseExpression();
                    ctx.expect(TokenType.RBRACKET);
                    callee = new MemberExpression(callee, property, true, memberLocation);
                } else {
                    break;
                }
            }

            List<Expression> args = new ArrayList<>();
            if (ctx.match(TokenType.LPAREN)) {
                ctx.advance();
                if (!ctx.match(TokenType.RPAREN)) {
                    do {
                        if (ctx.match(TokenType.COMMA)) {
                            ctx.advance();
                            // Handle trailing comma
                            if (ctx.match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (ctx.match(TokenType.ELLIPSIS)) {
                            // Spread argument: ...expr
                            SourceLocation spreadLocation = ctx.getLocation();
                            ctx.advance(); // consume ELLIPSIS
                            Expression argument = parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLocation));
                        } else {
                            args.add(parseAssignmentExpression());
                        }
                    } while (ctx.match(TokenType.COMMA));
                }
                ctx.expect(TokenType.RPAREN);
            }

            return new NewExpression(callee, args, location);
        }

        return parsePrimaryExpression();
    }

    Expression parseMultiplicativeExpression() {
        Expression left = parseExponentiationExpression();

        while (ctx.match(TokenType.MUL) || ctx.match(TokenType.DIV) || ctx.match(TokenType.MOD)) {
            BinaryOperator op = switch (ctx.currentToken.type()) {
                case DIV -> BinaryOperator.DIV;
                case MOD -> BinaryOperator.MOD;
                case MUL -> BinaryOperator.MUL;
                default -> null;
            };
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseExponentiationExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    /**
     * Continue parsing an expression that was already parsed as a primary expression.
     * Handles member access (.prop, [expr]), call expressions, postfix operators,
     * and assignment operators. Used when async function expressions are parsed
     * directly in parseAssignmentExpression, bypassing the normal chain.
     */
    Expression parsePostPrimaryExpression(Expression expr, SourceLocation location) {
        // Member/call chain (same as parseCallExpression's while loop)
        while (true) {
            if (ctx.match(TokenType.TEMPLATE)) {
                SourceLocation loc = ctx.getLocation();
                TemplateLiteral template = delegates.literals.parseTemplateLiteral(true);
                expr = new TaggedTemplateExpression(expr, template, loc);
            } else if (ctx.match(TokenType.LPAREN)) {
                SourceLocation loc = ctx.getLocation();
                ctx.advance();
                List<Expression> args = new ArrayList<>();
                if (!ctx.match(TokenType.RPAREN)) {
                    do {
                        if (ctx.match(TokenType.COMMA)) {
                            ctx.advance();
                            if (ctx.match(TokenType.RPAREN)) break;
                        }
                        if (ctx.match(TokenType.ELLIPSIS)) {
                            SourceLocation spreadLoc = ctx.getLocation();
                            ctx.advance();
                            Expression argument = parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLoc));
                        } else {
                            args.add(parseAssignmentExpression());
                        }
                    } while (ctx.match(TokenType.COMMA));
                }
                ctx.expect(TokenType.RPAREN);
                expr = new CallExpression(expr, args, loc);
            } else if (ctx.match(TokenType.DOT)) {
                SourceLocation loc = ctx.getLocation();
                ctx.advance();
                Expression property = parsePropertyName();
                expr = new MemberExpression(expr, property, false, loc);
            } else if (ctx.match(TokenType.LBRACKET)) {
                SourceLocation loc = ctx.getLocation();
                ctx.advance();
                Expression property = parseAssignmentExpression();
                ctx.expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, loc);
            } else {
                break;
            }
        }

        // Assignment check
        if (ctx.isAssignmentOperator(ctx.currentToken.type())) {
            TokenType op = ctx.currentToken.type();
            SourceLocation loc = ctx.getLocation();
            ctx.advance();
            Expression right = parseAssignmentExpression();
            AssignmentExpression.AssignmentOperator operator = switch (op) {
                case ASSIGN -> AssignmentExpression.AssignmentOperator.ASSIGN;
                case AND_ASSIGN -> AssignmentExpression.AssignmentOperator.AND_ASSIGN;
                case DIV_ASSIGN -> AssignmentExpression.AssignmentOperator.DIV_ASSIGN;
                case EXP_ASSIGN -> AssignmentExpression.AssignmentOperator.EXP_ASSIGN;
                case LOGICAL_AND_ASSIGN -> AssignmentExpression.AssignmentOperator.LOGICAL_AND_ASSIGN;
                case LOGICAL_OR_ASSIGN -> AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN;
                case LSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.LSHIFT_ASSIGN;
                case MINUS_ASSIGN -> AssignmentExpression.AssignmentOperator.MINUS_ASSIGN;
                case MOD_ASSIGN -> AssignmentExpression.AssignmentOperator.MOD_ASSIGN;
                case MUL_ASSIGN -> AssignmentExpression.AssignmentOperator.MUL_ASSIGN;
                case NULLISH_ASSIGN -> AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN;
                case OR_ASSIGN -> AssignmentExpression.AssignmentOperator.OR_ASSIGN;
                case PLUS_ASSIGN -> AssignmentExpression.AssignmentOperator.PLUS_ASSIGN;
                case RSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.RSHIFT_ASSIGN;
                case URSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.URSHIFT_ASSIGN;
                case XOR_ASSIGN -> AssignmentExpression.AssignmentOperator.XOR_ASSIGN;
                default -> AssignmentExpression.AssignmentOperator.ASSIGN;
            };
            return new AssignmentExpression(expr, operator, right, loc);
        }

        return expr;
    }

    Expression parsePostfixExpression() {
        Expression expr = parseCallExpression();

        // Per ES spec 12.9.1: if a line terminator occurs between the operand
        // and the ++/-- operator, ASI inserts a semicolon before the operator.
        // Following QuickJS !s->got_lf check in postfix operator parsing.
        if (!ctx.hasNewlineBefore() && (ctx.match(TokenType.INC) || ctx.match(TokenType.DEC))) {
            UnaryOperator op = ctx.match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            return new UnaryExpression(op, expr, false, location);
        }

        return expr;
    }

    Expression parsePrimaryExpression() {
        SourceLocation location = ctx.getLocation();

        return switch (ctx.currentToken.type()) {
            case NUMBER -> {
                String value = ctx.currentToken.value();
                ctx.advance();
                String normalizedValue = value.replace("_", "");
                Object numValue;
                if (normalizedValue.startsWith("0x") || normalizedValue.startsWith("0X")) {
                    // Parse hex number - store as long or int
                    long longVal = Long.parseLong(normalizedValue.substring(2), 16);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else if (normalizedValue.startsWith("0b") || normalizedValue.startsWith("0B")) {
                    // Parse binary number - store as long or int
                    long longVal = Long.parseLong(normalizedValue.substring(2), 2);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else if (normalizedValue.startsWith("0o") || normalizedValue.startsWith("0O")) {
                    // Parse octal number - store as long or int
                    long longVal = Long.parseLong(normalizedValue.substring(2), 8);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else {
                    // Parse decimal number - use double
                    double doubleVal = Double.parseDouble(normalizedValue);
                    // Check if it's a whole number that fits in an int
                    if (doubleVal == Math.floor(doubleVal) && !Double.isInfinite(doubleVal) &&
                            doubleVal >= Integer.MIN_VALUE && doubleVal <= Integer.MAX_VALUE) {
                        numValue = (int) doubleVal;
                    } else {
                        numValue = doubleVal;
                    }
                }
                yield new Literal(numValue, location);
            }
            case BIGINT -> {
                String value = ctx.currentToken.value();
                ctx.advance();
                String normalizedValue = value.replace("_", "");
                // Parse BigInt literal - handle different radixes
                BigInteger bigIntValue;
                if (normalizedValue.startsWith("0x") || normalizedValue.startsWith("0X")) {
                    bigIntValue = new BigInteger(normalizedValue.substring(2), 16);
                } else if (normalizedValue.startsWith("0b") || normalizedValue.startsWith("0B")) {
                    bigIntValue = new BigInteger(normalizedValue.substring(2), 2);
                } else if (normalizedValue.startsWith("0o") || normalizedValue.startsWith("0O")) {
                    bigIntValue = new BigInteger(normalizedValue.substring(2), 8);
                } else {
                    bigIntValue = new BigInteger(normalizedValue);
                }
                yield new Literal(bigIntValue, location);
            }
            case STRING -> {
                String value = ctx.currentToken.value();
                ctx.advance();
                yield new Literal(value, location);
            }
            case REGEX -> {
                String value = ctx.currentToken.value();
                ctx.advance();
                yield new Literal(new RegExpLiteralValue(value), location);
            }
            case TEMPLATE -> {
                yield delegates.literals.parseTemplateLiteral(false);
            }
            case TRUE -> {
                ctx.advance();
                yield new Literal(true, location);
            }
            case FALSE -> {
                ctx.advance();
                yield new Literal(false, location);
            }
            case NULL -> {
                ctx.advance();
                yield new Literal(null, location);
            }
            case IDENTIFIER -> ctx.parseIdentifier();
            case ASYNC -> ctx.parseIdentifier();
            case AWAIT -> ctx.parseIdentifier();
            case PRIVATE_NAME -> {
                String name = ctx.currentToken.value();
                String fieldName = name.substring(1);
                ctx.advance();
                yield new PrivateIdentifier(fieldName, location);
            }
            case THIS -> {
                ctx.advance();
                yield new Identifier("this", location);
            }
            case LPAREN -> {
                // This could be either:
                // 1. A grouped expression: (expr)
                // 2. An arrow function parameter list: (params) => body
                // We need to distinguish between them

                // Try to detect arrow function by looking ahead
                // Patterns: () => or (id) => or (id, id, ...) =>

                ctx.advance(); // consume (

                // Check for empty parameter list: () which could be arrow function
                if (ctx.match(TokenType.RPAREN)) {
                    // Could be () => ...
                    // Return an empty ArrayExpression as a marker for empty parameter list
                    ctx.advance();
                    yield new ArrayExpression(new ArrayList<>(), location);
                }

                // Try to parse as potential arrow function parameters
                // This is a simplified heuristic: if we see identifier(s) and commas, followed by ), =>
                // then treat it as arrow function params
                // Otherwise parse as expression

                // Check if next token is identifier - could be arrow function param
                if (ctx.match(TokenType.IDENTIFIER) || ctx.match(TokenType.ELLIPSIS)) {
                    // Check for rest parameter (...args)
                    if (ctx.match(TokenType.ELLIPSIS)) {
                        // This must be an arrow function with rest parameter: (...args) => expr
                        // We cannot parse this as a regular expression, so parse as arrow function params
                        SourceLocation restLocation = ctx.getLocation();
                        ctx.advance(); // consume '...'
                        Identifier restArg = ctx.parseIdentifier();
                        RestParameter restParam = new RestParameter(restArg, restLocation);

                        ctx.expect(TokenType.RPAREN);

                        // Mark this as arrow function parameters with rest
                        // We'll use a special marker - an ArrayExpression with a SpreadElement
                        yield new ArrayExpression(
                                List.of(new SpreadElement(restArg, restLocation)),
                                location
                        );
                    }

                    // Peek ahead to distinguish between:
                    // (id) => expr (arrow function with single param)
                    // (id, id2) => expr (arrow function with multiple params)
                    // (id = value) (grouped assignment expression)
                    // (id + value) (grouped binary expression)

                    // We need to look at what comes after the identifier
                    // to decide if this is arrow function params or a grouped expression

                    // Check if the token after identifier is an assignment operator
                    // If so, this is a grouped expression like (b = 10), not arrow params
                    if (ctx.nextToken.type() != TokenType.COMMA &&
                            ctx.nextToken.type() != TokenType.RPAREN &&
                            ctx.nextToken.type() != TokenType.ARROW) {
                        // This looks like a grouped expression, not arrow function params
                        // Parse the whole thing as an expression
                        // ES spec: Expression[+In] inside parentheses
                        boolean savedIn = ctx.inOperatorAllowed;
                        ctx.inOperatorAllowed = true;
                        Expression expr = parseExpression();
                        ctx.inOperatorAllowed = savedIn;
                        ctx.expect(TokenType.RPAREN);
                        yield expr;
                    }

                    // Could be (id) or (id, id, ...) or (id, ...rest) or (id = default, ...)
                    // Parse as parameter list tentatively
                    List<Expression> potentialParams = new ArrayList<>();
                    Identifier firstParam = ctx.parseIdentifier();
                    // Check for default value on first param
                    if (ctx.match(TokenType.ASSIGN)) {
                        SourceLocation assignLoc = ctx.getLocation();
                        ctx.advance(); // consume '='
                        Expression defaultExpr = parseAssignmentExpression();
                        potentialParams.add(new AssignmentExpression(firstParam,
                                AssignmentExpression.AssignmentOperator.ASSIGN, defaultExpr, assignLoc));
                    } else {
                        potentialParams.add(firstParam);
                    }

                    // Check for more parameters or rest parameter
                    while (ctx.match(TokenType.COMMA)) {
                        ctx.advance(); // consume comma

                        // Check for rest parameter at end
                        if (ctx.match(TokenType.ELLIPSIS)) {
                            SourceLocation restLocation = ctx.getLocation();
                            ctx.advance(); // consume '...'
                            Identifier restArg = ctx.parseIdentifier();
                            RestParameter restParam = new RestParameter(restArg, restLocation);

                            // Add SpreadElement as marker for rest parameter
                            potentialParams.add(new SpreadElement(restArg, restLocation));

                            // Rest must be last, so break
                            break;
                        }

                        if (!ctx.match(TokenType.IDENTIFIER)) {
                            // Not a simple parameter list, might be complex expression
                            throw new RuntimeException("Complex arrow function parameters not yet supported at line " +
                                    ctx.currentToken.line() + ", column " + ctx.currentToken.column());
                        }
                        Identifier param = ctx.parseIdentifier();
                        // Check for default value
                        if (ctx.match(TokenType.ASSIGN)) {
                            SourceLocation assignLoc = ctx.getLocation();
                            ctx.advance(); // consume '='
                            Expression defaultExpr = parseAssignmentExpression();
                            potentialParams.add(new AssignmentExpression(param,
                                    AssignmentExpression.AssignmentOperator.ASSIGN, defaultExpr, assignLoc));
                        } else {
                            potentialParams.add(param);
                        }
                    }

                    ctx.expect(TokenType.RPAREN);

                    // Now check if followed by =>
                    // If yes, this is an arrow function parameter list
                    // If no, this was a grouped identifier (or sequence)
                    // For now, we'll create a custom marker for this case

                    // Return first param if single (and not a spread), or create a marker for multiple
                    if (potentialParams.size() == 1 && !(potentialParams.get(0) instanceof SpreadElement)) {
                        yield potentialParams.get(0);
                    } else {
                        // Multiple parameters or rest parameter - create an ArrayExpression as marker
                        yield new ArrayExpression(potentialParams, location);
                    }
                } else {
                    // Not starting with identifier, parse as expression
                    // ES spec: Expression[+In] inside parentheses
                    boolean savedIn = ctx.inOperatorAllowed;
                    ctx.inOperatorAllowed = true;
                    Expression expr = parseExpression();
                    ctx.inOperatorAllowed = savedIn;
                    ctx.expect(TokenType.RPAREN);
                    yield expr;
                }
            }
            case LBRACKET -> delegates.literals.parseArrayExpression();
            case LBRACE -> delegates.literals.parseObjectExpression();
            case FUNCTION -> delegates.functions.parseFunctionExpression();
            case CLASS -> delegates.functions.parseClassExpression(); // Class expressions
            case SUPER -> {
                ctx.advance(); // consume 'super'
                // super() is only valid in derived class constructors.
                if (ctx.match(TokenType.LPAREN)) {
                    if (ctx.inDerivedConstructor) {
                        yield new Identifier("super", location);
                    }
                    throw new JSSyntaxErrorException("'super' keyword unexpected here");
                }
                // super.property / super[expr] is valid in method bodies.
                if (ctx.superPropertyAllowed && (ctx.match(TokenType.DOT) || ctx.match(TokenType.LBRACKET))) {
                    yield new Identifier("super", location);
                }
                throw new JSSyntaxErrorException("'super' keyword unexpected here");
            }
            default -> {
                // Error case - return a literal undefined
                ctx.advance();
                yield new Literal(null, location);
            }
        };
    }

    Expression parsePropertyName() {
        SourceLocation location = ctx.getLocation();
        return switch (ctx.currentToken.type()) {
            case IDENTIFIER -> {
                String name = ctx.currentToken.value();
                ctx.advance();
                yield new Identifier(name, location);
            }
            case PRIVATE_NAME -> {
                // Private field access: obj.#field
                String name = ctx.currentToken.value();
                // Remove '#' prefix for the PrivateIdentifier name
                String fieldName = name.substring(1);
                ctx.advance();
                yield new PrivateIdentifier(fieldName, location);
            }
            case STRING -> {
                String value = ctx.currentToken.value();
                ctx.advance();
                yield new Literal(value, location);
            }
            case NUMBER -> {
                String value = ctx.currentToken.value();
                ctx.advance();
                // Numeric keys are converted to strings
                yield new Literal(value, location);
            }
            case LBRACKET -> {
                // Computed property name: [expression]
                ctx.advance();
                Expression expr = parseAssignmentExpression();
                ctx.expect(TokenType.RBRACKET);
                yield expr;
            }
            // Allow keywords as property names (e.g., obj.delete, obj.class, obj.return)
            case AS, ASYNC, AWAIT, BREAK, CASE, CATCH, CLASS, CONST, CONTINUE,
                 DEFAULT, DELETE, DO, ELSE, EXPORT, EXTENDS, FALSE, FINALLY,
                 FOR, FROM, FUNCTION, IF, IMPORT, IN, INSTANCEOF, LET, NEW,
                 NULL, OF, RETURN, SUPER, SWITCH, THIS, THROW, TRUE, TRY,
                 TYPEOF, VAR, VOID, WHILE, YIELD -> {
                String name = ctx.currentToken.value();
                ctx.advance();
                yield new Identifier(name, location);
            }
            default -> throw new JSSyntaxErrorException("Unexpected end of input");
        };
    }

    Expression parseRelationalExpression() {
        Expression left = parseShiftExpression();

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
            Expression right = parseShiftExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseShiftExpression() {
        Expression left = parseAdditiveExpression();

        while (ctx.match(TokenType.LSHIFT) || ctx.match(TokenType.RSHIFT) || ctx.match(TokenType.URSHIFT)) {
            BinaryOperator op = switch (ctx.currentToken.type()) {
                case LSHIFT -> BinaryOperator.LSHIFT;
                case RSHIFT -> BinaryOperator.RSHIFT;
                case URSHIFT -> BinaryOperator.URSHIFT;
                default -> null;
            };
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression right = parseAdditiveExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    Expression parseUnaryExpression() {
        // Handle await expressions
        if (ctx.match(TokenType.AWAIT)) {
            // Per QuickJS: in_function_body == FALSE prevents await during parameter parsing
            // in async functions. Treat as identifier when not in function body.
            if (!ctx.inFunctionBody && ctx.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("'await' expression not allowed in formal parameters of an async function");
            }
            if (!ctx.isAwaitExpressionAllowed()) {
                if (ctx.isAwaitIdentifierAllowed() && ctx.isValidContinuationAfterAwaitIdentifier()) {
                    return parsePostfixExpression();
                }
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression argument = parseUnaryExpression();
            return new AwaitExpression(argument, location);
        }

        // Handle yield expressions
        if (ctx.match(TokenType.YIELD)) {
            // Per QuickJS: in_function_body == FALSE prevents yield during parameter parsing
            // in generator functions. It's a SyntaxError to use yield in parameters.
            if (!ctx.inFunctionBody) {
                throw new JSSyntaxErrorException("'yield' expression not allowed in formal parameters of a generator function");
            }
            SourceLocation location = ctx.getLocation();
            ctx.advance();

            // Check for yield* (delegating yield)
            boolean delegate = false;
            if (ctx.match(TokenType.MUL)) {
                delegate = true;
                ctx.advance();
            }

            // Yield can have no argument: just "yield" by itself
            // or can have an argument: "yield expr" or "yield* expr"
            Expression argument = null;
            if (!ctx.match(TokenType.SEMICOLON) && !ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
                argument = parseAssignmentExpression();
            }

            return new YieldExpression(argument, delegate, location);
        }

        if (ctx.match(TokenType.INC) || ctx.match(TokenType.DEC)) {
            UnaryOperator op = ctx.match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression operand = parseUnaryExpression();
            return new UnaryExpression(op, operand, true, location);
        }

        if (ctx.match(TokenType.PLUS) || ctx.match(TokenType.MINUS) || ctx.match(TokenType.NOT) ||
                ctx.match(TokenType.BIT_NOT) || ctx.match(TokenType.TYPEOF) ||
                ctx.match(TokenType.VOID) || ctx.match(TokenType.DELETE)) {
            UnaryOperator op = switch (ctx.currentToken.type()) {
                case PLUS -> UnaryOperator.PLUS;
                case MINUS -> UnaryOperator.MINUS;
                case NOT -> UnaryOperator.NOT;
                case BIT_NOT -> UnaryOperator.BIT_NOT;
                case TYPEOF -> UnaryOperator.TYPEOF;
                case VOID -> UnaryOperator.VOID;
                case DELETE -> UnaryOperator.DELETE;
                default -> null;
            };
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression operand = parseUnaryExpression();
            return new UnaryExpression(op, operand, true, location);
        }

        return parsePostfixExpression();
    }
}
