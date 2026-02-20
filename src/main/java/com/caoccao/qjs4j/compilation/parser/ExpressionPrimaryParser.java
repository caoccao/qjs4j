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
import com.caoccao.qjs4j.compilation.ast.UnaryExpression.UnaryOperator;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.regexp.RegExpLiteralValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class ExpressionPrimaryParser {
    private final ParserContext ctx;
    private final ParserDelegates delegates;
    private final ExpressionParser expressions;

    ExpressionPrimaryParser(ParserContext ctx, ParserDelegates delegates, ExpressionParser expressions) {
        this.ctx = ctx;
        this.delegates = delegates;
        this.expressions = expressions;
    }

    Expression parseCallExpression() {
        Expression expr = expressions.parseMemberExpression();

        while (true) {
            if (ctx.match(TokenType.TEMPLATE)) {
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
                            if (ctx.match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (ctx.match(TokenType.ELLIPSIS)) {
                            SourceLocation spreadLocation = ctx.getLocation();
                            ctx.advance();
                            Expression argument = expressions.parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLocation));
                        } else {
                            args.add(expressions.parseAssignmentExpression());
                        }
                    } while (ctx.match(TokenType.COMMA));
                }

                ctx.expect(TokenType.RPAREN);
                expr = new CallExpression(expr, args, location);
            } else if (ctx.match(TokenType.DOT)) {
                SourceLocation location = ctx.getLocation();
                ctx.advance();
                Expression property = expressions.parsePropertyName();
                expr = new MemberExpression(expr, property, false, location);
            } else if (ctx.match(TokenType.LBRACKET)) {
                SourceLocation location = ctx.getLocation();
                ctx.advance();
                Expression property = expressions.parseAssignmentExpression();
                ctx.expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, location);
            } else {
                break;
            }
        }

        return expr;
    }

    Expression parseMemberExpression() {
        if (ctx.match(TokenType.NEW)) {
            SourceLocation location = ctx.getLocation();
            ctx.advance();
            Expression callee = parseMemberExpression();

            while (true) {
                if (ctx.match(TokenType.DOT)) {
                    ctx.advance();
                    SourceLocation memberLocation = ctx.getLocation();
                    Expression property = expressions.parsePropertyName();
                    callee = new MemberExpression(callee, property, false, memberLocation);
                } else if (ctx.match(TokenType.LBRACKET)) {
                    ctx.advance();
                    SourceLocation memberLocation = ctx.getLocation();
                    Expression property = expressions.parseExpression();
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
                            if (ctx.match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (ctx.match(TokenType.ELLIPSIS)) {
                            SourceLocation spreadLocation = ctx.getLocation();
                            ctx.advance();
                            Expression argument = expressions.parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLocation));
                        } else {
                            args.add(expressions.parseAssignmentExpression());
                        }
                    } while (ctx.match(TokenType.COMMA));
                }
                ctx.expect(TokenType.RPAREN);
            }

            return new NewExpression(callee, args, location);
        }

        return expressions.parsePrimaryExpression();
    }

    Expression parsePostPrimaryExpression(Expression expr, SourceLocation location) {
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
                            if (ctx.match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (ctx.match(TokenType.ELLIPSIS)) {
                            SourceLocation spreadLoc = ctx.getLocation();
                            ctx.advance();
                            Expression argument = expressions.parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLoc));
                        } else {
                            args.add(expressions.parseAssignmentExpression());
                        }
                    } while (ctx.match(TokenType.COMMA));
                }
                ctx.expect(TokenType.RPAREN);
                expr = new CallExpression(expr, args, loc);
            } else if (ctx.match(TokenType.DOT)) {
                SourceLocation loc = ctx.getLocation();
                ctx.advance();
                Expression property = expressions.parsePropertyName();
                expr = new MemberExpression(expr, property, false, loc);
            } else if (ctx.match(TokenType.LBRACKET)) {
                SourceLocation loc = ctx.getLocation();
                ctx.advance();
                Expression property = expressions.parseAssignmentExpression();
                ctx.expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, loc);
            } else {
                break;
            }
        }

        if (ctx.isAssignmentOperator(ctx.currentToken.type())) {
            TokenType op = ctx.currentToken.type();
            SourceLocation loc = ctx.getLocation();
            ctx.advance();
            Expression right = expressions.parseAssignmentExpression();
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
        Expression expr = expressions.parseCallExpression();

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
                    long longVal = Long.parseLong(normalizedValue.substring(2), 16);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else if (normalizedValue.startsWith("0b") || normalizedValue.startsWith("0B")) {
                    long longVal = Long.parseLong(normalizedValue.substring(2), 2);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else if (normalizedValue.startsWith("0o") || normalizedValue.startsWith("0O")) {
                    long longVal = Long.parseLong(normalizedValue.substring(2), 8);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else {
                    double doubleVal = Double.parseDouble(normalizedValue);
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
            case TEMPLATE -> delegates.literals.parseTemplateLiteral(false);
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
                ctx.advance();

                if (ctx.match(TokenType.RPAREN)) {
                    ctx.advance();
                    yield new ArrayExpression(new ArrayList<>(), location);
                }

                if (ctx.match(TokenType.IDENTIFIER) || ctx.match(TokenType.ELLIPSIS)) {
                    if (ctx.match(TokenType.ELLIPSIS)) {
                        SourceLocation restLocation = ctx.getLocation();
                        ctx.advance();
                        Identifier restArg = ctx.parseIdentifier();
                        RestParameter restParam = new RestParameter(restArg, restLocation);
                        ctx.expect(TokenType.RPAREN);
                        yield new ArrayExpression(List.of(new SpreadElement(restArg, restParam.getLocation())), location);
                    }

                    if (ctx.nextToken.type() != TokenType.COMMA &&
                            ctx.nextToken.type() != TokenType.RPAREN &&
                            ctx.nextToken.type() != TokenType.ARROW) {
                        boolean savedIn = ctx.inOperatorAllowed;
                        ctx.inOperatorAllowed = true;
                        Expression expr = expressions.parseExpression();
                        ctx.inOperatorAllowed = savedIn;
                        ctx.expect(TokenType.RPAREN);
                        yield expr;
                    }

                    List<Expression> potentialParams = new ArrayList<>();
                    Identifier firstParam = ctx.parseIdentifier();
                    if (ctx.match(TokenType.ASSIGN)) {
                        SourceLocation assignLoc = ctx.getLocation();
                        ctx.advance();
                        Expression defaultExpr = expressions.parseAssignmentExpression();
                        potentialParams.add(new AssignmentExpression(firstParam,
                                AssignmentExpression.AssignmentOperator.ASSIGN, defaultExpr, assignLoc));
                    } else {
                        potentialParams.add(firstParam);
                    }

                    while (ctx.match(TokenType.COMMA)) {
                        ctx.advance();

                        if (ctx.match(TokenType.ELLIPSIS)) {
                            SourceLocation restLocation = ctx.getLocation();
                            ctx.advance();
                            Identifier restArg = ctx.parseIdentifier();
                            potentialParams.add(new SpreadElement(restArg, restLocation));
                            break;
                        }

                        if (!ctx.match(TokenType.IDENTIFIER)) {
                            throw new RuntimeException("Complex arrow function parameters not yet supported at line " +
                                    ctx.currentToken.line() + ", column " + ctx.currentToken.column());
                        }
                        Identifier param = ctx.parseIdentifier();
                        if (ctx.match(TokenType.ASSIGN)) {
                            SourceLocation assignLoc = ctx.getLocation();
                            ctx.advance();
                            Expression defaultExpr = expressions.parseAssignmentExpression();
                            potentialParams.add(new AssignmentExpression(param,
                                    AssignmentExpression.AssignmentOperator.ASSIGN, defaultExpr, assignLoc));
                        } else {
                            potentialParams.add(param);
                        }
                    }

                    ctx.expect(TokenType.RPAREN);

                    if (potentialParams.size() == 1 && !(potentialParams.get(0) instanceof SpreadElement)) {
                        yield potentialParams.get(0);
                    } else {
                        yield new ArrayExpression(potentialParams, location);
                    }
                } else {
                    boolean savedIn = ctx.inOperatorAllowed;
                    ctx.inOperatorAllowed = true;
                    Expression expr = expressions.parseExpression();
                    ctx.inOperatorAllowed = savedIn;
                    ctx.expect(TokenType.RPAREN);
                    yield expr;
                }
            }
            case LBRACKET -> delegates.literals.parseArrayExpression();
            case LBRACE -> delegates.literals.parseObjectExpression();
            case FUNCTION -> delegates.functions.parseFunctionExpression();
            case CLASS -> delegates.functions.parseClassExpression();
            case SUPER -> {
                ctx.advance();
                if (ctx.match(TokenType.LPAREN)) {
                    if (ctx.inDerivedConstructor) {
                        yield new Identifier("super", location);
                    }
                    throw new JSSyntaxErrorException("'super' keyword unexpected here");
                }
                if (ctx.superPropertyAllowed && (ctx.match(TokenType.DOT) || ctx.match(TokenType.LBRACKET))) {
                    yield new Identifier("super", location);
                }
                throw new JSSyntaxErrorException("'super' keyword unexpected here");
            }
            default -> {
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
                String name = ctx.currentToken.value();
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
                yield new Literal(value, location);
            }
            case LBRACKET -> {
                ctx.advance();
                Expression expr = expressions.parseAssignmentExpression();
                ctx.expect(TokenType.RBRACKET);
                yield expr;
            }
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

    Expression parseUnaryExpression() {
        if (ctx.match(TokenType.AWAIT)) {
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

        if (ctx.match(TokenType.YIELD)) {
            if (!ctx.inFunctionBody) {
                throw new JSSyntaxErrorException("'yield' expression not allowed in formal parameters of a generator function");
            }
            SourceLocation location = ctx.getLocation();
            ctx.advance();

            boolean delegate = false;
            if (ctx.match(TokenType.MUL)) {
                delegate = true;
                ctx.advance();
            }

            Expression argument = null;
            if (!ctx.match(TokenType.SEMICOLON) && !ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
                argument = expressions.parseAssignmentExpression();
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
