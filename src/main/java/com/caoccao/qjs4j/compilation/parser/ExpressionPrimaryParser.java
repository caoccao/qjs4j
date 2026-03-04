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
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.regexp.RegExpLiteralValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class ExpressionPrimaryParser {
    private final ParserDelegates delegates;
    private final ExpressionParser expressions;
    private final ParserContext parserContext;

    ExpressionPrimaryParser(ParserContext parserContext, ParserDelegates delegates, ExpressionParser expressions) {
        this.parserContext = parserContext;
        this.delegates = delegates;
        this.expressions = expressions;
    }

    Expression parseCallExpression() {
        Expression expr = expressions.parseMemberExpression();

        while (true) {
            if (parserContext.match(TokenType.TEMPLATE)) {
                SourceLocation location = parserContext.getLocation();
                TemplateLiteral template = delegates.literals.parseTemplateLiteral(true);
                expr = new TaggedTemplateExpression(expr, template, location);
            } else if (parserContext.match(TokenType.LPAREN)) {
                SourceLocation location = parserContext.getLocation();
                parserContext.advance();
                List<Expression> args = new ArrayList<>();

                if (!parserContext.match(TokenType.RPAREN)) {
                    do {
                        if (parserContext.match(TokenType.COMMA)) {
                            parserContext.advance();
                            if (parserContext.match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (parserContext.match(TokenType.ELLIPSIS)) {
                            SourceLocation spreadLocation = parserContext.getLocation();
                            parserContext.advance();
                            Expression argument = expressions.parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLocation));
                        } else {
                            args.add(expressions.parseAssignmentExpression());
                        }
                    } while (parserContext.match(TokenType.COMMA));
                }

                parserContext.expect(TokenType.RPAREN);
                expr = new CallExpression(expr, args, false, location);
            } else if (parserContext.match(TokenType.OPTIONAL_CHAINING)) {
                SourceLocation location = parserContext.getLocation();
                parserContext.advance();
                if (parserContext.match(TokenType.LPAREN)) {
                    // Optional call: expr?.(args)
                    parserContext.advance();
                    List<Expression> args = new ArrayList<>();
                    if (!parserContext.match(TokenType.RPAREN)) {
                        do {
                            if (parserContext.match(TokenType.COMMA)) {
                                parserContext.advance();
                                if (parserContext.match(TokenType.RPAREN)) {
                                    break;
                                }
                            }
                            if (parserContext.match(TokenType.ELLIPSIS)) {
                                SourceLocation spreadLocation = parserContext.getLocation();
                                parserContext.advance();
                                Expression argument = expressions.parseAssignmentExpression();
                                args.add(new SpreadElement(argument, spreadLocation));
                            } else {
                                args.add(expressions.parseAssignmentExpression());
                            }
                        } while (parserContext.match(TokenType.COMMA));
                    }
                    parserContext.expect(TokenType.RPAREN);
                    expr = new CallExpression(expr, args, true, location);
                } else if (parserContext.match(TokenType.LBRACKET)) {
                    // Optional computed member: expr?.[prop]
                    parserContext.advance();
                    Expression property = expressions.parseAssignmentExpression();
                    parserContext.expect(TokenType.RBRACKET);
                    expr = new MemberExpression(expr, property, true, true, location);
                } else {
                    // Optional member: expr?.prop
                    Expression property = expressions.parsePropertyName();
                    expr = new MemberExpression(expr, property, false, true, location);
                }
            } else if (parserContext.match(TokenType.DOT)) {
                SourceLocation location = parserContext.getLocation();
                parserContext.advance();
                Expression property = expressions.parsePropertyName();
                expr = new MemberExpression(expr, property, false, false, location);
            } else if (parserContext.match(TokenType.LBRACKET)) {
                SourceLocation location = parserContext.getLocation();
                parserContext.advance();
                Expression property = expressions.parseAssignmentExpression();
                parserContext.expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, false, location);
            } else {
                break;
            }
        }

        return expr;
    }

    Expression parseMemberExpression() {
        if (parserContext.match(TokenType.NEW)) {
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();

            // Handle new.target meta-property
            if (parserContext.match(TokenType.DOT) && parserContext.nextToken.type() == TokenType.IDENTIFIER
                    && JSKeyword.TARGET.equals(parserContext.nextToken.value())) {
                if ((parserContext.isEval && !parserContext.allowNewTargetInEval)
                        || (!parserContext.isEval && parserContext.functionNesting == 0)) {
                    throw new JSSyntaxErrorException("'new.target' keyword unexpected here");
                }
                parserContext.advance(); // consume '.'
                parserContext.advance(); // consume 'target'
                return new Identifier("new.target", location);
            }

            Expression callee = parseMemberExpression();

            while (true) {
                if (parserContext.match(TokenType.DOT)) {
                    parserContext.advance();
                    SourceLocation memberLocation = parserContext.getLocation();
                    Expression property = expressions.parsePropertyName();
                    callee = new MemberExpression(callee, property, false, false, memberLocation);
                } else if (parserContext.match(TokenType.LBRACKET)) {
                    parserContext.advance();
                    SourceLocation memberLocation = parserContext.getLocation();
                    Expression property = expressions.parseExpression();
                    parserContext.expect(TokenType.RBRACKET);
                    callee = new MemberExpression(callee, property, true, false, memberLocation);
                } else {
                    break;
                }
            }

            List<Expression> args = new ArrayList<>();
            if (parserContext.match(TokenType.LPAREN)) {
                parserContext.advance();
                if (!parserContext.match(TokenType.RPAREN)) {
                    do {
                        if (parserContext.match(TokenType.COMMA)) {
                            parserContext.advance();
                            if (parserContext.match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (parserContext.match(TokenType.ELLIPSIS)) {
                            SourceLocation spreadLocation = parserContext.getLocation();
                            parserContext.advance();
                            Expression argument = expressions.parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLocation));
                        } else {
                            args.add(expressions.parseAssignmentExpression());
                        }
                    } while (parserContext.match(TokenType.COMMA));
                }
                parserContext.expect(TokenType.RPAREN);
            }

            return new NewExpression(callee, args, location);
        }

        Expression expr = expressions.parsePrimaryExpression();
        while (true) {
            if (parserContext.match(TokenType.DOT)) {
                parserContext.advance();
                SourceLocation memberLocation = parserContext.getLocation();
                Expression property = expressions.parsePropertyName();
                expr = new MemberExpression(expr, property, false, false, memberLocation);
            } else if (parserContext.match(TokenType.LBRACKET)) {
                parserContext.advance();
                SourceLocation memberLocation = parserContext.getLocation();
                Expression property = expressions.parseExpression();
                parserContext.expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, false, memberLocation);
            } else {
                break;
            }
        }
        return expr;
    }

    Expression parsePostPrimaryExpression(Expression expr, SourceLocation location) {
        while (true) {
            if (parserContext.match(TokenType.TEMPLATE)) {
                SourceLocation loc = parserContext.getLocation();
                TemplateLiteral template = delegates.literals.parseTemplateLiteral(true);
                expr = new TaggedTemplateExpression(expr, template, loc);
            } else if (parserContext.match(TokenType.LPAREN)) {
                SourceLocation loc = parserContext.getLocation();
                parserContext.advance();
                List<Expression> args = new ArrayList<>();
                if (!parserContext.match(TokenType.RPAREN)) {
                    do {
                        if (parserContext.match(TokenType.COMMA)) {
                            parserContext.advance();
                            if (parserContext.match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (parserContext.match(TokenType.ELLIPSIS)) {
                            SourceLocation spreadLoc = parserContext.getLocation();
                            parserContext.advance();
                            Expression argument = expressions.parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLoc));
                        } else {
                            args.add(expressions.parseAssignmentExpression());
                        }
                    } while (parserContext.match(TokenType.COMMA));
                }
                parserContext.expect(TokenType.RPAREN);
                expr = new CallExpression(expr, args, false, loc);
            } else if (parserContext.match(TokenType.DOT)) {
                SourceLocation loc = parserContext.getLocation();
                parserContext.advance();
                Expression property = expressions.parsePropertyName();
                expr = new MemberExpression(expr, property, false, false, loc);
            } else if (parserContext.match(TokenType.LBRACKET)) {
                SourceLocation loc = parserContext.getLocation();
                parserContext.advance();
                Expression property = expressions.parseAssignmentExpression();
                parserContext.expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, false, loc);
            } else {
                break;
            }
        }

        if (parserContext.isAssignmentOperator(parserContext.currentToken.type())) {
            // Validate that expr is a valid assignment target
            if (!(expr instanceof Identifier)
                    && !(expr instanceof MemberExpression)
                    && !(expr instanceof CallExpression)) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            TokenType op = parserContext.currentToken.type();
            SourceLocation loc = parserContext.getLocation();
            if (parserContext.strictMode && expr instanceof Identifier identifier) {
                String identifierName = identifier.name();
                if (JSKeyword.EVAL.equals(identifierName) || JSKeyword.ARGUMENTS.equals(identifierName)) {
                    throw new JSSyntaxErrorException(
                            "Unexpected eval or arguments in strict mode");
                }
            }
            parserContext.advance();
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

        if (!parserContext.hasNewlineBefore() && (parserContext.match(TokenType.INC) || parserContext.match(TokenType.DEC))) {
            // In strict mode, CallExpression as update operand is an early SyntaxError (spec 13.4.1)
            if (parserContext.strictMode && expr instanceof CallExpression) {
                throw new JSSyntaxErrorException("Invalid left-hand side expression in postfix operation");
            }
            UnaryOperator op = parserContext.match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            return new UnaryExpression(op, expr, false, location);
        }

        return expr;
    }

    Expression parsePrimaryExpression() {
        SourceLocation location = parserContext.getLocation();

        return switch (parserContext.currentToken.type()) {
            case NUMBER -> {
                String value = parserContext.currentToken.value();
                parserContext.advance();
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
                String value = parserContext.currentToken.value();
                parserContext.advance();
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
                String value = parserContext.currentToken.value();
                parserContext.advance();
                yield new Literal(value, location);
            }
            case REGEX -> {
                String value = parserContext.currentToken.value();
                parserContext.advance();
                yield new Literal(new RegExpLiteralValue(value), location);
            }
            case TEMPLATE -> delegates.literals.parseTemplateLiteral(false);
            case TRUE -> {
                parserContext.advance();
                yield new Literal(true, location);
            }
            case FALSE -> {
                parserContext.advance();
                yield new Literal(false, location);
            }
            case NULL -> {
                parserContext.advance();
                yield new Literal(null, location);
            }
            case IDENTIFIER -> parserContext.parseIdentifier();
            case ASYNC -> parserContext.parseIdentifier();
            case AWAIT -> parserContext.parseIdentifier();
            case YIELD -> parserContext.parseIdentifier();
            case FROM -> parserContext.parseIdentifier();
            case OF -> parserContext.parseIdentifier();
            case PRIVATE_NAME -> {
                String name = parserContext.currentToken.value();
                String fieldName = name.substring(1);
                parserContext.advance();
                yield new PrivateIdentifier(fieldName, location);
            }
            case THIS -> {
                parserContext.advance();
                yield new Identifier(JSKeyword.THIS, location);
            }
            case LPAREN -> {
                parserContext.advance();

                if (parserContext.match(TokenType.RPAREN)) {
                    parserContext.advance();
                    yield new ArrayExpression(new ArrayList<>(), location);
                }

                if (parserContext.match(TokenType.IDENTIFIER) || parserContext.match(TokenType.ELLIPSIS)) {
                    if (parserContext.match(TokenType.ELLIPSIS)) {
                        SourceLocation restLocation = parserContext.getLocation();
                        parserContext.advance();
                        Expression restArg;
                        if (parserContext.match(TokenType.LBRACKET)) {
                            restArg = delegates.literals.parseArrayExpression();
                        } else if (parserContext.match(TokenType.LBRACE)) {
                            restArg = delegates.literals.parseObjectExpression();
                        } else {
                            restArg = parserContext.parseIdentifier();
                        }
                        parserContext.expect(TokenType.RPAREN);
                        yield new ArrayExpression(List.of(new SpreadElement(restArg, restLocation)), location);
                    }

                    if (parserContext.nextToken.type() != TokenType.COMMA &&
                            parserContext.nextToken.type() != TokenType.RPAREN) {
                        boolean savedIn = parserContext.inOperatorAllowed;
                        parserContext.inOperatorAllowed = true;
                        Expression firstExpr = expressions.parseAssignmentExpression();

                        if (!parserContext.match(TokenType.COMMA)) {
                            // Single expression in parens
                            parserContext.inOperatorAllowed = savedIn;
                            parserContext.expect(TokenType.RPAREN);
                            yield firstExpr;
                        }

                        // Multiple expressions - may contain rest element for arrow params
                        List<Expression> elements = new ArrayList<>();
                        elements.add(firstExpr);

                        while (parserContext.match(TokenType.COMMA)) {
                            parserContext.advance();

                            if (parserContext.match(TokenType.RPAREN)) {
                                // Trailing comma
                                break;
                            }

                            if (parserContext.match(TokenType.ELLIPSIS)) {
                                // Rest/spread: (expr, ...rest) for arrow params
                                SourceLocation restLoc = parserContext.getLocation();
                                parserContext.advance();
                                Expression restArg;
                                if (parserContext.match(TokenType.LBRACKET)) {
                                    restArg = delegates.literals.parseArrayExpression();
                                } else if (parserContext.match(TokenType.LBRACE)) {
                                    restArg = delegates.literals.parseObjectExpression();
                                } else {
                                    restArg = parserContext.parseIdentifier();
                                }
                                elements.add(new SpreadElement(restArg, restLoc));
                                break;
                            }

                            elements.add(expressions.parseAssignmentExpression());
                        }

                        parserContext.inOperatorAllowed = savedIn;
                        parserContext.expect(TokenType.RPAREN);

                        if (elements.size() == 1) {
                            yield elements.get(0);
                        }

                        // If there's a SpreadElement, this is arrow params → use ArrayExpression
                        boolean hasSpread = elements.get(elements.size() - 1) instanceof SpreadElement;
                        if (hasSpread) {
                            yield new ArrayExpression(elements, location);
                        }

                        // Otherwise, regular sequence expression
                        yield new SequenceExpression(elements, location);
                    }

                    List<Expression> potentialParams = new ArrayList<>();
                    Identifier firstParam = parserContext.parseIdentifier();
                    if (parserContext.match(TokenType.ASSIGN)) {
                        SourceLocation assignLoc = parserContext.getLocation();
                        parserContext.advance();
                        Expression defaultExpr = expressions.parseAssignmentExpression();
                        potentialParams.add(new AssignmentExpression(firstParam,
                                AssignmentExpression.AssignmentOperator.ASSIGN, defaultExpr, assignLoc));
                    } else {
                        potentialParams.add(firstParam);
                    }

                    while (parserContext.match(TokenType.COMMA)) {
                        parserContext.advance();

                        if (parserContext.match(TokenType.ELLIPSIS)) {
                            SourceLocation restLocation = parserContext.getLocation();
                            parserContext.advance();
                            Expression restArg;
                            if (parserContext.match(TokenType.LBRACKET)) {
                                restArg = delegates.literals.parseArrayExpression();
                            } else if (parserContext.match(TokenType.LBRACE)) {
                                restArg = delegates.literals.parseObjectExpression();
                            } else {
                                restArg = parserContext.parseIdentifier();
                            }
                            potentialParams.add(new SpreadElement(restArg, restLocation));
                            break;
                        }

                        Expression paramExpr;
                        if (parserContext.match(TokenType.IDENTIFIER)) {
                            paramExpr = parserContext.parseIdentifier();
                        } else if (parserContext.match(TokenType.LBRACE)) {
                            paramExpr = delegates.literals.parseObjectExpression();
                        } else if (parserContext.match(TokenType.LBRACKET)) {
                            paramExpr = delegates.literals.parseArrayExpression();
                        } else if (parserContext.match(TokenType.RPAREN)) {
                            // Trailing comma in arrow parameters: (a, b,) => {}
                            break;
                        } else {
                            throw new JSSyntaxErrorException("Unexpected token in arrow function parameters at line " +
                                    parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
                        }
                        if (parserContext.match(TokenType.ASSIGN)) {
                            SourceLocation assignLoc = parserContext.getLocation();
                            parserContext.advance();
                            Expression defaultExpr = expressions.parseAssignmentExpression();
                            potentialParams.add(new AssignmentExpression(paramExpr,
                                    AssignmentExpression.AssignmentOperator.ASSIGN, defaultExpr, assignLoc));
                        } else {
                            potentialParams.add(paramExpr);
                        }
                    }

                    parserContext.expect(TokenType.RPAREN);

                    if (potentialParams.size() == 1 && !(potentialParams.get(0) instanceof SpreadElement)) {
                        yield potentialParams.get(0);
                    } else if (parserContext.match(TokenType.ARROW)
                            || potentialParams.stream().anyMatch(e -> e instanceof SpreadElement)) {
                        // Arrow function params or contains spread element
                        yield new ArrayExpression(potentialParams, location);
                    } else {
                        // Not arrow params - treat as comma expression (grouping)
                        yield new SequenceExpression(potentialParams, location);
                    }
                } else {
                    boolean savedIn = parserContext.inOperatorAllowed;
                    parserContext.inOperatorAllowed = true;
                    Expression expr = expressions.parseExpression();
                    parserContext.inOperatorAllowed = savedIn;
                    parserContext.expect(TokenType.RPAREN);
                    yield expr;
                }
            }
            case LBRACKET -> delegates.literals.parseArrayExpression();
            case LBRACE -> delegates.literals.parseObjectExpression();
            case FUNCTION -> delegates.functions.parseFunctionExpression();
            case CLASS -> delegates.functions.parseClassExpression();
            case SUPER -> {
                parserContext.advance();
                if (parserContext.match(TokenType.LPAREN)) {
                    if (parserContext.inDerivedConstructor) {
                        yield new Identifier(JSKeyword.SUPER, location);
                    }
                    throw new JSSyntaxErrorException("'super' keyword unexpected here");
                }
                if (parserContext.superPropertyAllowed && (parserContext.match(TokenType.DOT) || parserContext.match(TokenType.LBRACKET))) {
                    yield new Identifier(JSKeyword.SUPER, location);
                }
                throw new JSSyntaxErrorException("'super' keyword unexpected here");
            }
            default -> throw new JSSyntaxErrorException(
                    "Unexpected token " + parserContext.currentToken.type() + " at line "
                            + parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
        };
    }

    Expression parsePropertyName() {
        SourceLocation location = parserContext.getLocation();
        return switch (parserContext.currentToken.type()) {
            case IDENTIFIER -> {
                String name = parserContext.currentToken.value();
                parserContext.advance();
                yield new Identifier(name, location);
            }
            case PRIVATE_NAME -> {
                String name = parserContext.currentToken.value();
                String fieldName = name.substring(1);
                parserContext.advance();
                yield new PrivateIdentifier(fieldName, location);
            }
            case STRING -> {
                String value = parserContext.currentToken.value();
                parserContext.advance();
                yield new Literal(value, location);
            }
            case NUMBER -> {
                String value = parserContext.currentToken.value();
                parserContext.advance();
                yield new Literal(value, location);
            }
            case LBRACKET -> {
                parserContext.advance();
                Expression expr = expressions.parseAssignmentExpression();
                parserContext.expect(TokenType.RBRACKET);
                yield expr;
            }
            case AS, ASYNC, AWAIT, BREAK, CASE, CATCH, CLASS, CONST, CONTINUE,
                 DEFAULT, DELETE, DO, ELSE, EXPORT, EXTENDS, FALSE, FINALLY,
                 FOR, FROM, FUNCTION, IF, IMPORT, IN, INSTANCEOF, LET, NEW,
                 NULL, OF, RETURN, SUPER, SWITCH, THIS, THROW, TRUE, TRY,
                 TYPEOF, VAR, VOID, WHILE, YIELD -> {
                String name = parserContext.currentToken.value();
                parserContext.advance();
                yield new Identifier(name, location);
            }
            default -> throw new JSSyntaxErrorException("Unexpected end of input");
        };
    }

    Expression parseUnaryExpression() {
        if (parserContext.match(TokenType.AWAIT)) {
            if (!parserContext.inFunctionBody && parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("'await' expression not allowed in formal parameters of an async function");
            }
            if (!parserContext.isAwaitExpressionAllowed()) {
                if (parserContext.isAwaitIdentifierAllowed() && parserContext.isValidContinuationAfterAwaitIdentifier()) {
                    return parsePostfixExpression();
                }
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression argument = parseUnaryExpression();
            return new AwaitExpression(argument, location);
        }

        if (parserContext.match(TokenType.YIELD)) {
            // In non-strict, non-generator contexts, yield is a valid identifier
            if (parserContext.isYieldIdentifierAllowed()) {
                return parsePostfixExpression();
            }
            // In strict mode outside a generator, yield is a reserved word
            if (parserContext.generatorFunctionNesting == 0) {
                throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
            }
            // YieldExpression is only valid at AssignmentExpression level (spec 14.4),
            // not at UnaryExpression level. E.g. "void yield" must be SyntaxError.
            throw new JSSyntaxErrorException("Unexpected token 'yield'");
        }

        if (parserContext.match(TokenType.INC) || parserContext.match(TokenType.DEC)) {
            UnaryOperator op = parserContext.match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression operand = parseUnaryExpression();
            // In strict mode, CallExpression as update operand is an early SyntaxError (spec 13.4.1)
            if (parserContext.strictMode && operand instanceof CallExpression) {
                throw new JSSyntaxErrorException("Invalid left-hand side expression in prefix operation");
            }
            return new UnaryExpression(op, operand, true, location);
        }

        if (parserContext.match(TokenType.PLUS) || parserContext.match(TokenType.MINUS) || parserContext.match(TokenType.NOT) ||
                parserContext.match(TokenType.BIT_NOT) || parserContext.match(TokenType.TYPEOF) ||
                parserContext.match(TokenType.VOID) || parserContext.match(TokenType.DELETE)) {
            UnaryOperator op = switch (parserContext.currentToken.type()) {
                case PLUS -> UnaryOperator.PLUS;
                case MINUS -> UnaryOperator.MINUS;
                case NOT -> UnaryOperator.NOT;
                case BIT_NOT -> UnaryOperator.BIT_NOT;
                case TYPEOF -> UnaryOperator.TYPEOF;
                case VOID -> UnaryOperator.VOID;
                case DELETE -> UnaryOperator.DELETE;
                default -> null;
            };
            SourceLocation location = parserContext.getLocation();
            parserContext.advance();
            Expression operand = parseUnaryExpression();
            return new UnaryExpression(op, operand, true, location);
        }

        return parsePostfixExpression();
    }
}
