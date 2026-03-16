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
import com.caoccao.qjs4j.compilation.lexer.Token;
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

    private static boolean isOptionalChainExpression(Expression expression) {
        if (expression instanceof MemberExpression memberExpression) {
            return memberExpression.isOptional() || isOptionalChainExpression(memberExpression.getObject());
        }
        if (expression instanceof CallExpression callExpression) {
            return callExpression.isOptional() || isOptionalChainExpression(callExpression.getCallee());
        }
        return false;
    }

    private static boolean isPrivateDeleteTarget(Expression expression) {
        if (expression instanceof MemberExpression memberExpression) {
            return memberExpression.getProperty() instanceof PrivateIdentifier;
        }
        return false;
    }

    private static Object parseDecimalOrLegacyOctal(String normalizedValue) {
        // Check for legacy octal: starts with 0, followed by digits, all digits 0-7
        if (normalizedValue.length() > 1 && normalizedValue.charAt(0) == '0'
                && normalizedValue.charAt(1) >= '0' && normalizedValue.charAt(1) <= '9'
                && normalizedValue.indexOf('.') == -1
                && normalizedValue.indexOf('e') == -1 && normalizedValue.indexOf('E') == -1) {
            boolean allOctalDigits = true;
            for (int i = 1; i < normalizedValue.length(); i++) {
                if (normalizedValue.charAt(i) > '7') {
                    allOctalDigits = false;
                    break;
                }
            }
            if (allOctalDigits) {
                long longVal = Long.parseLong(normalizedValue, 8);
                return (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                        ? (int) longVal : (double) longVal;
            }
        }
        double doubleVal = Double.parseDouble(normalizedValue);
        if (doubleVal == Math.floor(doubleVal) && !Double.isInfinite(doubleVal)
                && doubleVal >= Integer.MIN_VALUE && doubleVal <= Integer.MAX_VALUE) {
            return (int) doubleVal;
        }
        return doubleVal;
    }

    Expression parseCallExpression() {
        Expression expr = expressions.parseMemberExpression();

        while (true) {
            if (parserContext.match(TokenType.TEMPLATE)) {
                if (isOptionalChainExpression(expr)) {
                    throw new JSSyntaxErrorException("Invalid tagged template on optional chain");
                }
                SourceLocation location = parserContext.getLocation();
                TemplateLiteral template = delegates.literals.parseTemplateLiteral(true);
                expr = new TaggedTemplateExpression(expr, template, location);
            } else if (parserContext.match(TokenType.LPAREN)) {
                if (expr instanceof Identifier identifier && JSKeyword.IMPORT.equals(identifier.getName())) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
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
                    Expression property = expressions.parseExpression();
                    parserContext.expect(TokenType.RBRACKET);
                    expr = new MemberExpression(expr, property, true, true, location);
                } else {
                    // Optional member: expr?.prop
                    Expression property = parseMemberPropertyName();
                    expr = new MemberExpression(expr, property, false, true, location);
                }
            } else if (parserContext.match(TokenType.DOT)) {
                SourceLocation location = parserContext.getLocation();
                parserContext.advance();
                Expression property = parseMemberPropertyName();
                expr = new MemberExpression(expr, property, false, false, location);
            } else if (parserContext.match(TokenType.LBRACKET)) {
                SourceLocation location = parserContext.getLocation();
                parserContext.advance();
                Expression property = expressions.parseExpression();
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
            if (parserContext.currentToken.escaped()) {
                throw new JSSyntaxErrorException("Unexpected token NEW");
            }
            parserContext.advance();

            // Handle new.target meta-property
            if (parserContext.match(TokenType.DOT) && parserContext.nextToken.type() == TokenType.IDENTIFIER
                    && JSKeyword.TARGET.equals(parserContext.nextToken.value())) {
                if (parserContext.nextToken.escaped()) {
                    throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
                }
                if ((parserContext.isEval && !parserContext.allowNewTargetInEval)
                        || (!parserContext.isEval
                        && parserContext.newTargetNesting == 0
                        && !parserContext.inClassFieldInitializer
                        && !parserContext.inClassStaticInit)) {
                    throw new JSSyntaxErrorException("'new.target' keyword unexpected here");
                }
                parserContext.advance(); // consume '.'
                parserContext.advance(); // consume 'target'
                return new Identifier("new.target", location);
            }

            boolean startsWithImport = parserContext.match(TokenType.IMPORT);
            if (startsWithImport && parserContext.nextToken.type() == TokenType.LPAREN) {
                throw new JSSyntaxErrorException("Unexpected import call in constructor position");
            }

            Expression callee = parseMemberExpression();
            if (startsWithImport && callee instanceof ImportExpression) {
                throw new JSSyntaxErrorException("Unexpected import call in constructor position");
            }

            while (true) {
                if (parserContext.match(TokenType.DOT)) {
                    parserContext.advance();
                    SourceLocation memberLocation = parserContext.getLocation();
                    Expression property = parseMemberPropertyName();
                    callee = new MemberExpression(callee, property, false, false, memberLocation);
                } else if (parserContext.match(TokenType.LBRACKET)) {
                    parserContext.advance();
                    SourceLocation memberLocation = parserContext.getLocation();
                    Expression property = expressions.parseExpression();
                    parserContext.expect(TokenType.RBRACKET);
                    callee = new MemberExpression(callee, property, true, false, memberLocation);
                } else if (parserContext.match(TokenType.TEMPLATE)) {
                    if (isOptionalChainExpression(callee)) {
                        throw new JSSyntaxErrorException("Invalid tagged template on optional chain");
                    }
                    SourceLocation templateLocation = parserContext.getLocation();
                    TemplateLiteral template = delegates.literals.parseTemplateLiteral(true);
                    callee = new TaggedTemplateExpression(callee, template, templateLocation);
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
                Expression property = parseMemberPropertyName();
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

    Expression parseMemberPropertyName() {
        SourceLocation location = parserContext.getLocation();
        return switch (parserContext.currentToken.type()) {
            case IDENTIFIER -> {
                String name = parserContext.currentToken.value();
                parserContext.advance();
                yield new Identifier(name, location);
            }
            case PRIVATE_NAME -> {
                String privateName = parserContext.currentToken.value().substring(1);
                if (!parserContext.isPrivateNameAccessible(privateName)) {
                    throw new JSSyntaxErrorException("undefined private field '#" + privateName + "'");
                }
                String name = parserContext.currentToken.value();
                parserContext.advance();
                yield new PrivateIdentifier(name.substring(1), location);
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
            case EOF -> throw new JSSyntaxErrorException("Unexpected end of input");
            default ->
                    throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
        };
    }

    Expression parsePostPrimaryExpression(Expression expr, SourceLocation location) {
        while (true) {
            if (parserContext.match(TokenType.TEMPLATE)) {
                if (isOptionalChainExpression(expr)) {
                    throw new JSSyntaxErrorException("Invalid tagged template on optional chain");
                }
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
            } else if (parserContext.match(TokenType.OPTIONAL_CHAINING)) {
                SourceLocation loc = parserContext.getLocation();
                parserContext.advance();
                if (parserContext.match(TokenType.LPAREN)) {
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
                    expr = new CallExpression(expr, args, true, loc);
                } else if (parserContext.match(TokenType.LBRACKET)) {
                    parserContext.advance();
                    Expression property = expressions.parseExpression();
                    parserContext.expect(TokenType.RBRACKET);
                    expr = new MemberExpression(expr, property, true, true, loc);
                } else {
                    Expression property = parseMemberPropertyName();
                    expr = new MemberExpression(expr, property, false, true, loc);
                }
            } else if (parserContext.match(TokenType.DOT)) {
                SourceLocation loc = parserContext.getLocation();
                parserContext.advance();
                Expression property = parseMemberPropertyName();
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
                String identifierName = identifier.getName();
                if (JSKeyword.EVAL.equals(identifierName) || JSKeyword.ARGUMENTS.equals(identifierName)) {
                    throw new JSSyntaxErrorException(
                            "Unexpected eval or arguments in strict mode");
                }
            }
            parserContext.advance();
            Expression right = expressions.parseAssignmentExpression();
            AssignmentOperator operator = switch (op) {
                case ASSIGN -> AssignmentOperator.ASSIGN;
                case AND_ASSIGN -> AssignmentOperator.AND_ASSIGN;
                case DIV_ASSIGN -> AssignmentOperator.DIV_ASSIGN;
                case EXP_ASSIGN -> AssignmentOperator.EXP_ASSIGN;
                case LOGICAL_AND_ASSIGN -> AssignmentOperator.LOGICAL_AND_ASSIGN;
                case LOGICAL_OR_ASSIGN -> AssignmentOperator.LOGICAL_OR_ASSIGN;
                case LSHIFT_ASSIGN -> AssignmentOperator.LSHIFT_ASSIGN;
                case MINUS_ASSIGN -> AssignmentOperator.MINUS_ASSIGN;
                case MOD_ASSIGN -> AssignmentOperator.MOD_ASSIGN;
                case MUL_ASSIGN -> AssignmentOperator.MUL_ASSIGN;
                case NULLISH_ASSIGN -> AssignmentOperator.NULLISH_ASSIGN;
                case OR_ASSIGN -> AssignmentOperator.OR_ASSIGN;
                case PLUS_ASSIGN -> AssignmentOperator.PLUS_ASSIGN;
                case RSHIFT_ASSIGN -> AssignmentOperator.RSHIFT_ASSIGN;
                case URSHIFT_ASSIGN -> AssignmentOperator.URSHIFT_ASSIGN;
                case XOR_ASSIGN -> AssignmentOperator.XOR_ASSIGN;
                default -> AssignmentOperator.ASSIGN;
            };
            return new AssignmentExpression(expr, operator, right, loc);
        }

        return expr;
    }

    Expression parsePostfixExpression() {
        Expression expr = expressions.parseCallExpression();

        if (!parserContext.hasNewlineBefore() && (parserContext.match(TokenType.INC) || parserContext.match(TokenType.DEC))) {
            if (expr instanceof Identifier identifier) {
                String identifierName = identifier.getName();
                if ("import.meta".equals(identifierName)
                        || "new.target".equals(identifierName)
                        || JSKeyword.THIS.equals(identifierName)) {
                    throw new JSSyntaxErrorException("Invalid left-hand side expression in postfix operation");
                }
                if (parserContext.strictMode
                        && (JSKeyword.EVAL.equals(identifierName) || JSKeyword.ARGUMENTS.equals(identifierName))) {
                    throw new JSSyntaxErrorException("Invalid left-hand side expression in postfix operation");
                }
            }
            // In strict mode, CallExpression as update operand is an early SyntaxError (spec 13.4.1)
            if (parserContext.strictMode && expr instanceof CallExpression) {
                throw new JSSyntaxErrorException("Invalid left-hand side expression in postfix operation");
            }
            if (isOptionalChainExpression(expr)) {
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
                // In strict mode, legacy octal (010, 07) and non-octal decimal (08, 09)
                // literals are forbidden. These start with '0' followed by a digit.
                if (parserContext.strictMode && value.length() > 1 && value.charAt(0) == '0') {
                    char second = value.charAt(1);
                    if (second >= '0' && second <= '9') {
                        throw new JSSyntaxErrorException("Octal literals are not allowed in strict mode.");
                    }
                }
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
                    numValue = parseDecimalOrLegacyOctal(normalizedValue);
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
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
                }
                parserContext.advance();
                yield new Literal(true, location);
            }
            case FALSE -> {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
                }
                parserContext.advance();
                yield new Literal(false, location);
            }
            case NULL -> {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
                }
                parserContext.advance();
                yield new Literal(null, location);
            }
            case IDENTIFIER, AS, ASYNC, AWAIT, YIELD, FROM, OF, LET -> parserContext.parseIdentifier();
            case PRIVATE_NAME -> {
                String name = parserContext.currentToken.value();
                String fieldName = name.substring(1);
                if (!parserContext.isPrivateNameAccessible(fieldName)) {
                    throw new JSSyntaxErrorException("undefined private field '#" + fieldName + "'");
                }
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

                    {
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
                } else {
                    boolean savedIn = parserContext.inOperatorAllowed;
                    parserContext.inOperatorAllowed = true;
                    Expression expr = expressions.parseExpression();
                    parserContext.inOperatorAllowed = savedIn;
                    parserContext.expect(TokenType.RPAREN);
                    yield expr;
                }
            }
            case DIV, DIV_ASSIGN -> {
                // When '/' or '/=' appears in expression position (e.g. after a block's '}'),
                // the lexer may have incorrectly tokenized it as division. Re-scan as regex.
                // This mirrors QuickJS's js_parse_unary() TOK_DIV_ASSIGN / '/' handling.
                Token regexToken = parserContext.lexer.rescanAsRegex(parserContext.currentToken);
                parserContext.currentToken = regexToken;
                parserContext.nextToken = parserContext.lexer.nextToken();
                String value = regexToken.value();
                parserContext.advance();
                yield new Literal(new RegExpLiteralValue(value), location);
            }
            case LBRACKET -> delegates.literals.parseArrayExpression();
            case LBRACE -> delegates.literals.parseObjectExpression();
            case FUNCTION -> delegates.functions.parseFunctionExpression();
            case CLASS -> delegates.functions.parseClassExpression();
            case AT -> {
                // Parse decorator list before class expression
                // Decorator: @ DecoratorMemberExpression | @ DecoratorCallExpression | @ DecoratorParenthesizedExpression
                while (parserContext.match(TokenType.AT)) {
                    parserContext.advance(); // consume @
                    if (parserContext.match(TokenType.LPAREN)) {
                        // DecoratorParenthesizedExpression: @(expression)
                        parserContext.advance(); // consume (
                        boolean savedIn = parserContext.inOperatorAllowed;
                        parserContext.inOperatorAllowed = true;
                        expressions.parseExpression();
                        parserContext.inOperatorAllowed = savedIn;
                        parserContext.expect(TokenType.RPAREN);
                    } else {
                        // DecoratorMemberExpression: identifier (.propertyName)*
                        parserContext.parseIdentifier();
                        while (parserContext.match(TokenType.DOT)) {
                            parserContext.advance(); // consume .
                            if (parserContext.match(TokenType.PRIVATE_NAME)) {
                                parserContext.advance();
                            } else {
                                expressions.parsePropertyName();
                            }
                        }
                        // Optional DecoratorCallExpression: DecoratorMemberExpression Arguments
                        if (parserContext.match(TokenType.LPAREN)) {
                            parserContext.advance(); // consume (
                            if (!parserContext.match(TokenType.RPAREN)) {
                                expressions.parseAssignmentExpression();
                                while (parserContext.match(TokenType.COMMA)) {
                                    parserContext.advance();
                                    expressions.parseAssignmentExpression();
                                }
                            }
                            parserContext.expect(TokenType.RPAREN);
                        }
                    }
                }
                // After decorators, must be a class expression
                if (!parserContext.match(TokenType.CLASS)) {
                    throw new JSSyntaxErrorException("Decorators are not valid here");
                }
                yield delegates.functions.parseClassExpression();
            }
            case IMPORT -> {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Unexpected token IMPORT");
                }
                parserContext.advance(); // consume 'import'
                if (parserContext.match(TokenType.DOT)) {
                    if (parserContext.nextToken.type() == TokenType.IDENTIFIER
                            && "meta".equals(parserContext.nextToken.value())
                            && !parserContext.nextToken.escaped()) {
                        if (!parserContext.moduleMode) {
                            throw new JSSyntaxErrorException("Cannot use 'import.meta' outside a module");
                        }
                        // import.meta
                        parserContext.advance(); // consume '.'
                        parserContext.advance(); // consume 'meta'
                        yield new Identifier("import.meta", location);
                    } else if (parserContext.nextToken.type() == TokenType.IDENTIFIER
                            && "defer".equals(parserContext.nextToken.value())
                            && !parserContext.nextToken.escaped()) {
                        // import.defer(specifier)
                        parserContext.advance(); // consume '.'
                        parserContext.advance(); // consume 'defer'
                        parserContext.expect(TokenType.LPAREN);
                        Expression source = expressions.parseAssignmentExpression();
                        parserContext.expect(TokenType.RPAREN);
                        yield new ImportExpression(source, null, true, location);
                    } else {
                        throw new JSSyntaxErrorException("The only valid meta property for import is 'import.meta'");
                    }
                } else if (parserContext.match(TokenType.LPAREN)) {
                    parserContext.advance(); // consume '('
                    boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                    parserContext.inOperatorAllowed = true;
                    try {
                        Expression source = expressions.parseAssignmentExpression();
                        Expression options = null;
                        if (parserContext.match(TokenType.COMMA)) {
                            parserContext.advance(); // consume ','
                            if (!parserContext.match(TokenType.RPAREN)) {
                                options = expressions.parseAssignmentExpression();
                                if (parserContext.match(TokenType.COMMA)) {
                                    parserContext.advance(); // consume trailing comma
                                }
                            }
                        }
                        parserContext.expect(TokenType.RPAREN);
                        yield new ImportExpression(source, options, location);
                    } finally {
                        parserContext.inOperatorAllowed = savedInOperatorAllowed;
                    }
                } else {
                    throw new JSSyntaxErrorException("Unexpected token " + parserContext.currentToken.type());
                }
            }
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
                    "Unexpected token '" + parserContext.currentToken.value() + "'");
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
                if (!parserContext.isPrivateNameAccessible(fieldName)) {
                    throw new JSSyntaxErrorException("undefined private field '#" + fieldName + "'");
                }
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
                // Parse numeric property names to canonical form (e.g., 0b10 -> 2)
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
                    numValue = parseDecimalOrLegacyOctal(normalizedValue);
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
            case LBRACKET -> {
                parserContext.advance();
                // Allow 'in' operator inside computed property names
                boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                parserContext.inOperatorAllowed = true;
                Expression expr = expressions.parseAssignmentExpression();
                parserContext.inOperatorAllowed = savedInOperatorAllowed;
                parserContext.expect(TokenType.RBRACKET);
                yield expr;
            }
            case NULL -> {
                parserContext.advance();
                yield new Literal(null, location);
            }
            case AS, ASYNC, AWAIT, BREAK, CASE, CATCH, CLASS, CONST, CONTINUE,
                 DEFAULT, DELETE, DO, ELSE, EXPORT, EXTENDS, FALSE, FINALLY,
                 FOR, FROM, FUNCTION, IF, IMPORT, IN, INSTANCEOF, LET, NEW,
                 OF, RETURN, SUPER, SWITCH, THIS, THROW, TRUE, TRY,
                 TYPEOF, VAR, VOID, WHILE, YIELD -> {
                String name = parserContext.currentToken.value();
                parserContext.advance();
                yield new Identifier(name, location);
            }
            default -> throw new JSSyntaxErrorException("Unexpected end of input");
        };
    }

    Expression parseUnaryExpression() {
        if (parserContext.match(TokenType.ASYNC)
                && parserContext.nextToken.type() == TokenType.FUNCTION
                && parserContext.nextToken.line() == parserContext.currentToken.line()) {
            if (parserContext.currentToken.escaped()) {
                throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
            }
            SourceLocation asyncLocation = parserContext.getLocation();
            parserContext.advance();
            Expression asyncFunctionExpression = delegates.functions.parseFunctionExpression(true, asyncLocation);
            return expressions.parsePostPrimaryExpression(asyncFunctionExpression, asyncLocation);
        }

        if (parserContext.match(TokenType.AWAIT)) {
            if (parserContext.currentToken.escaped() && parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
            }
            if (!parserContext.inFunctionBody && parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Illegal await-expression in formal parameters of async function");
            }
            if (!parserContext.isAwaitExpressionAllowed()) {
                if (parserContext.isAwaitIdentifierAllowed()) {
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
            if (operand instanceof Identifier identifier) {
                String identifierName = identifier.getName();
                if ("import.meta".equals(identifierName)
                        || "new.target".equals(identifierName)
                        || JSKeyword.THIS.equals(identifierName)) {
                    throw new JSSyntaxErrorException("Invalid left-hand side expression in prefix operation");
                }
                if (parserContext.strictMode
                        && (JSKeyword.EVAL.equals(identifierName) || JSKeyword.ARGUMENTS.equals(identifierName))) {
                    throw new JSSyntaxErrorException("Invalid left-hand side expression in prefix operation");
                }
            }
            // In strict mode, CallExpression as update operand is an early SyntaxError (spec 13.4.1)
            if (parserContext.strictMode && operand instanceof CallExpression) {
                throw new JSSyntaxErrorException("Invalid left-hand side expression in prefix operation");
            }
            if (isOptionalChainExpression(operand)) {
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
            if (op == UnaryOperator.DELETE) {
                if (parserContext.strictMode && operand instanceof Identifier) {
                    throw new JSSyntaxErrorException("Delete of an unqualified identifier in strict mode.");
                }
                if (isPrivateDeleteTarget(operand)) {
                    throw new JSSyntaxErrorException("Private fields can not be deleted");
                }
            }
            return new UnaryExpression(op, operand, true, location);
        }

        return parsePostfixExpression();
    }
}
