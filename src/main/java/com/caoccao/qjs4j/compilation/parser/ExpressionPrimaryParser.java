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

    private CallExpression createCallExpression(Expression callee, List<Expression> arguments, boolean optional,
            SourceLocation location) {
        boolean partOfOptionalChain = optional
                || (callee.isPartOfOptionalChain() && !parserContext.isParenthesizedExpression(callee));
        return new CallExpression(callee, arguments, optional, partOfOptionalChain, location);
    }

    private MemberExpression createMemberExpression(Expression object, Expression property, boolean computed,
            boolean optional, SourceLocation location) {
        boolean partOfOptionalChain = optional
                || (object.isPartOfOptionalChain() && !parserContext.isParenthesizedExpression(object));
        return new MemberExpression(object, property, computed, optional, partOfOptionalChain, location);
    }

    private List<Expression> parseCallArguments() {
        parserContext.advance();
        List<Expression> args = new ArrayList<>();

        if (!parserContext.match(TokenType.RPAREN)) {
            do {
                if (parserContext.consumeIf(TokenType.COMMA)) {
                    if (parserContext.match(TokenType.RPAREN)) {
                        break;
                    }
                }
                if (parserContext.match(TokenType.ELLIPSIS)) {
                    SourceLocation spreadLocation = parserContext.consumeLocation();
                    Expression argument = expressions.parseAssignmentExpression();
                    args.add(new SpreadElement(argument, spreadLocation));
                } else {
                    args.add(expressions.parseAssignmentExpression());
                }
            } while (parserContext.match(TokenType.COMMA));
        }

        parserContext.expect(TokenType.RPAREN);
        return args;
    }

    Expression parseCallExpression() {
        Expression expr = expressions.parseMemberExpression();

        while (true) {
            if (parserContext.match(TokenType.TEMPLATE)) {
                // Parentheses end an optional chain, so `(a?.b)`x`` is an ordinary tagged template.
                if (expr.isPartOfOptionalChain() && !parserContext.isParenthesizedExpression(expr)) {
                    throw new JSSyntaxErrorException("Invalid tagged template on optional chain");
                }
                expr = parseTaggedTemplate(expr);
            } else if (parserContext.match(TokenType.LPAREN)) {
                if (expr instanceof Identifier identifier && JSKeyword.IMPORT.equals(identifier.getName())) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
                expr = parseCallSuffix(expr);
            } else if (parserContext.match(TokenType.OPTIONAL_CHAINING)) {
                expr = parseOptionalLink(expr);
            } else if (parserContext.match(TokenType.DOT)) {
                validateNoCoverInitializedName(expr);
                SourceLocation location = parserContext.consumeLocation();
                Expression property = parseMemberPropertyName();
                expr = createMemberExpression(expr, property, false, false, location);
            } else if (parserContext.match(TokenType.LBRACKET)) {
                validateNoCoverInitializedName(expr);
                expr = parseComputedMember(expr, false);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expression parseCallSuffix(Expression callee) {
        SourceLocation location = parserContext.getLocation();
        return createCallExpression(callee, parseCallArguments(), false, location);
    }

    private Expression parseComputedMember(Expression object, boolean assignmentOnly) {
        SourceLocation location = parserContext.consumeLocation();
        Expression property = assignmentOnly ? expressions.parseAssignmentExpression() : expressions.parseExpression();
        parserContext.expect(TokenType.RBRACKET);
        return createMemberExpression(object, property, true, false, location);
    }

    private SpreadElement parseCoverRestElement() {
        SourceLocation location = parserContext.consumeLocation();
        Expression argument = switch (parserContext.currentToken.type()) {
            case LBRACKET -> delegates.literals.parseArrayExpression();
            case LBRACE -> delegates.literals.parseObjectExpression();
            default -> parserContext.parseIdentifier();
        };
        return new SpreadElement(argument, location);
    }

    Expression parseMemberExpression() {
        if (parserContext.match(TokenType.NEW)) {
            SourceLocation location = parserContext.getLocation();
            parserContext.requireUnescapedToken("Unexpected token NEW");
            parserContext.advance();

            // Handle new.target meta-property
            if (parserContext.match(TokenType.DOT) && parserContext.nextToken.type() == TokenType.IDENTIFIER
                    && JSKeyword.TARGET.equals(parserContext.nextToken.value())) {
                if (parserContext.nextToken.escaped()) {
                    throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
                }
                if (parserContext.newTargetNesting == 0 && !parserContext.inClassFieldInitializer
                        && !parserContext.inClassStaticInit) {
                    if (parserContext.isEval) {
                        if (!parserContext.allowNewTargetInEval) {
                            throw new JSSyntaxErrorException("new.target expression is not allowed here");
                        }
                    } else {
                        throw new JSSyntaxErrorException("new.target expression is not allowed here");
                    }
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
                if (parserContext.consumeIf(TokenType.DOT)) {
                    SourceLocation memberLocation = parserContext.getLocation();
                    Expression property = parseMemberPropertyName();
                    callee = createMemberExpression(callee, property, false, false, memberLocation);
                } else if (parserContext.match(TokenType.LBRACKET)) {
                    // A computed member is located at its `[`, as in the call loop and as V8 reports it.
                    callee = parseComputedMember(callee, false);
                } else if (parserContext.match(TokenType.TEMPLATE)) {
                    // No optional chain can reach here: `new a?.b` is rejected, and a parenthesized
                    // chain such as `new (a?.b)`x`` is an ordinary tag.
                    callee = parseTaggedTemplate(callee);
                } else {
                    break;
                }
            }
            if (parserContext.match(TokenType.OPTIONAL_CHAINING)) {
                throw new JSSyntaxErrorException("Invalid optional chain from new expression");
            }

            List<Expression> args = parserContext.match(TokenType.LPAREN) ? parseCallArguments() : new ArrayList<>();
            return new NewExpression(callee, args, location);
        }

        Expression expr = expressions.parsePrimaryExpression();
        while (true) {
            if (parserContext.match(TokenType.DOT)) {
                validateNoCoverInitializedName(expr);
                parserContext.advance();
                SourceLocation memberLocation = parserContext.getLocation();
                Expression property = parseMemberPropertyName();
                expr = createMemberExpression(expr, property, false, false, memberLocation);
            } else if (parserContext.match(TokenType.LBRACKET)) {
                validateNoCoverInitializedName(expr);
                // A computed member is located at its `[`, as in the call loop and as V8 reports it.
                expr = parseComputedMember(expr, false);
            } else {
                break;
            }
        }
        return expr;
    }

    Expression parseMemberPropertyName() {
        TokenType type = parserContext.currentToken.type();
        if (type.isIdentifierName()) {
            return parserContext.consumeIdentifierName();
        }
        if (type == TokenType.PRIVATE_NAME) {
            return parserContext.consumePrivateName();
        }
        if (type == TokenType.EOF) {
            throw new JSSyntaxErrorException("Unexpected end of input");
        }
        throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
    }

    private Expression parseOptionalLink(Expression base) {
        SourceLocation location = parserContext.consumeLocation();
        if (parserContext.match(TokenType.LPAREN)) {
            return createCallExpression(base, parseCallArguments(), true, location);
        }
        if (parserContext.consumeIf(TokenType.LBRACKET)) {
            Expression property = expressions.parseExpression();
            parserContext.expect(TokenType.RBRACKET);
            return createMemberExpression(base, property, true, true, location);
        }
        return createMemberExpression(base, parseMemberPropertyName(), false, true, location);
    }

    Expression parsePostfixExpression() {
        Expression expr = expressions.parseCallExpression();

        if (!parserContext.hasNewlineBefore()
                && (parserContext.match(TokenType.INC) || parserContext.match(TokenType.DEC))) {
            if (expr instanceof Identifier identifier) {
                String identifierName = identifier.getName();
                if ("import.meta".equals(identifierName) || "new.target".equals(identifierName)
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
            if (expr.containsOptionalChain()) {
                throw new JSSyntaxErrorException("Invalid left-hand side expression in postfix operation");
            }
            UnaryOperator op = parserContext.match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = parserContext.consumeLocation();
            return new UnaryExpression(op, expr, false, location);
        }

        return expr;
    }

    Expression parsePostPrimaryExpression(Expression expr, SourceLocation location) {
        while (true) {
            if (parserContext.match(TokenType.TEMPLATE)) {
                if (expr.isPartOfOptionalChain()) {
                    throw new JSSyntaxErrorException("Invalid tagged template on optional chain");
                }
                expr = parseTaggedTemplate(expr);
            } else if (parserContext.match(TokenType.LPAREN)) {
                expr = parseCallSuffix(expr);
            } else if (parserContext.match(TokenType.OPTIONAL_CHAINING)) {
                expr = parseOptionalLink(expr);
            } else if (parserContext.match(TokenType.DOT)) {
                SourceLocation loc = parserContext.consumeLocation();
                Expression property = parseMemberPropertyName();
                expr = createMemberExpression(expr, property, false, false, loc);
            } else if (parserContext.match(TokenType.LBRACKET)) {
                expr = parseComputedMember(expr, true);
            } else {
                break;
            }
        }

        AssignmentOperator operator = ParserContext.assignmentOperator(parserContext.currentToken.type());
        if (operator != null) {
            // Validate that expr is a valid assignment target
            if (!(expr instanceof Identifier) && !(expr instanceof MemberExpression)
                    && !(expr instanceof CallExpression)) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            SourceLocation loc = parserContext.getLocation();
            if (parserContext.strictMode && expr instanceof Identifier identifier) {
                String identifierName = identifier.getName();
                if (JSKeyword.EVAL.equals(identifierName) || JSKeyword.ARGUMENTS.equals(identifierName)) {
                    throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                }
            }
            parserContext.advance();
            Expression right = expressions.parseAssignmentExpression();
            return new AssignmentExpression(expr, operator, right, loc);
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
                yield parseNumericLiteral(value, location, false);
            }
            case BIGINT -> parseNumericLiteral(parserContext.consumeValue(), location, true);
            case STRING -> new Literal(parserContext.consumeValue(), location);
            case REGEX -> {
                String value = parserContext.consumeValue();
                yield new Literal(new RegExpLiteralValue(value), location);
            }
            case TEMPLATE -> delegates.literals.parseTemplateLiteral(false);
            case TRUE -> {
                parserContext.requireUnescapedToken("Keyword must not contain escaped characters");
                parserContext.advance();
                yield new Literal(true, location);
            }
            case FALSE -> {
                parserContext.requireUnescapedToken("Keyword must not contain escaped characters");
                parserContext.advance();
                yield new Literal(false, location);
            }
            case NULL -> {
                parserContext.requireUnescapedToken("Keyword must not contain escaped characters");
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
                // A private name begins an expression only as the left operand of `in`, which the relational parser
                // takes before it gets here: `(#x in o)` is a brand check, `(#x) in o` and `#x + 1` are errors.
                throw new JSSyntaxErrorException("Unexpected identifier '" + name + "'");
            }
            case THIS -> {
                parserContext.advance();
                yield new Identifier(JSKeyword.THIS, location);
            }
            case LPAREN -> {
                parserContext.advance();

                if (parserContext.consumeIf(TokenType.RPAREN)) {
                    // `()` is only an arrow function's empty parameter list; as an expression it is an error. It
                    // used to evaluate to an empty array.
                    requireArrowAfterCoverParameters("Unexpected token ')'");
                    yield new ArrayExpression(new ArrayList<>(), location);
                }

                if (parserContext.match(TokenType.IDENTIFIER) || parserContext.match(TokenType.ELLIPSIS)) {
                    if (parserContext.match(TokenType.ELLIPSIS)) {
                        SpreadElement rest = parseCoverRestElement();
                        parserContext.expect(TokenType.RPAREN);
                        requireArrowAfterCoverParameters("Unexpected token '...'");
                        yield new ArrayExpression(List.of(rest), location);
                    }

                    {
                        boolean savedIn = parserContext.inOperatorAllowed;
                        parserContext.inOperatorAllowed = true;
                        Expression firstExpr = expressions.parseAssignmentExpression();

                        if (!parserContext.match(TokenType.COMMA)) {
                            // Single expression in parens
                            parserContext.inOperatorAllowed = savedIn;
                            parserContext.expect(TokenType.RPAREN);
                            // An expression that starts with an identifier is never itself parenthesized, so
                            // ((a)) => 1 is caught below, where the inner parentheses are parsed.
                            parserContext.markParenthesizedExpression(firstExpr);
                            yield firstExpr;
                        }

                        // Multiple expressions - may contain rest element for arrow params
                        List<Expression> elements = new ArrayList<>();
                        elements.add(firstExpr);
                        boolean trailingComma = false;

                        while (parserContext.consumeIf(TokenType.COMMA)) {

                            if (parserContext.match(TokenType.RPAREN)) {
                                // Trailing comma
                                trailingComma = true;
                                break;
                            }

                            if (parserContext.match(TokenType.ELLIPSIS)) {
                                // Rest/spread: (expr, ...rest) for arrow params
                                SpreadElement rest = parseCoverRestElement();
                                elements.add(rest);
                                break;
                            }

                            elements.add(expressions.parseAssignmentExpression());
                        }

                        parserContext.inOperatorAllowed = savedIn;
                        parserContext.expect(TokenType.RPAREN);

                        // A rest element or a trailing comma makes this an arrow parameter list and nothing else.
                        boolean hasSpread = elements.get(elements.size() - 1) instanceof SpreadElement;
                        if (hasSpread) {
                            requireArrowAfterCoverParameters("Unexpected token '...'");
                        } else if (trailingComma) {
                            requireArrowAfterCoverParameters("Unexpected token ')'");
                        }

                        if (elements.size() == 1) {
                            yield elements.get(0);
                        }

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
                    if (parserContext.isParenthesizedExpression(expr) && parserContext.match(TokenType.ARROW)) {
                        // ((a)) => 1: a parameter cannot be parenthesized.
                        throw new JSSyntaxErrorException("Invalid destructuring assignment target");
                    }
                    parserContext.markParenthesizedExpression(expr);
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
                // Decorator: @ DecoratorMemberExpression | @ DecoratorCallExpression | @
                // DecoratorParenthesizedExpression
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
                            if (parserContext.consumeIf(TokenType.PRIVATE_NAME)) {
                            } else {
                                expressions.parsePropertyName();
                            }
                        }
                        // Optional DecoratorCallExpression: DecoratorMemberExpression Arguments
                        if (parserContext.consumeIf(TokenType.LPAREN)) {
                            if (!parserContext.match(TokenType.RPAREN)) {
                                expressions.parseAssignmentExpression();
                                while (parserContext.consumeIf(TokenType.COMMA)) {
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
                parserContext.requireUnescapedToken("Unexpected token IMPORT");
                parserContext.advance(); // consume 'import'
                if (parserContext.match(TokenType.DOT)) {
                    if (parserContext.nextToken.type() == TokenType.IDENTIFIER
                            && "meta".equals(parserContext.nextToken.value()) && !parserContext.nextToken.escaped()) {
                        if (!parserContext.moduleMode) {
                            throw new JSSyntaxErrorException("Cannot use 'import.meta' outside a module");
                        }
                        // import.meta
                        parserContext.advance(); // consume '.'
                        parserContext.advance(); // consume 'meta'
                        yield new Identifier("import.meta", location);
                    } else if (parserContext.nextToken.type() == TokenType.IDENTIFIER
                            && "defer".equals(parserContext.nextToken.value()) && !parserContext.nextToken.escaped()) {
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
                } else if (parserContext.consumeIf(TokenType.LPAREN)) {
                    boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                    parserContext.inOperatorAllowed = true;
                    try {
                        Expression source = expressions.parseAssignmentExpression();
                        Expression options = null;
                        if (parserContext.consumeIf(TokenType.COMMA)) {
                            if (!parserContext.match(TokenType.RPAREN)) {
                                options = expressions.parseAssignmentExpression();
                                if (parserContext.consumeIf(TokenType.COMMA)) {
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
                if (parserContext.superPropertyAllowed
                        && (parserContext.match(TokenType.DOT) || parserContext.match(TokenType.LBRACKET))) {
                    yield new Identifier(JSKeyword.SUPER, location);
                }
                throw new JSSyntaxErrorException("'super' keyword unexpected here");
            }
            default ->
                throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
        };
    }

    Expression parsePropertyName() {
        TokenType type = parserContext.currentToken.type();
        if (type != TokenType.NULL && type.isIdentifierName()) {
            return parserContext.consumeIdentifierName();
        }
        SourceLocation location = parserContext.getLocation();
        return switch (type) {
            case PRIVATE_NAME -> parserContext.consumePrivateName();
            case STRING -> new Literal(parserContext.consumeValue(), location);
            case NUMBER -> parseNumericLiteral(parserContext.consumeValue(), location, false);
            case BIGINT -> parseNumericLiteral(parserContext.consumeValue(), location, true);
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
            default -> throw new JSSyntaxErrorException("Unexpected end of input");
        };
    }

    private Expression parseTaggedTemplate(Expression tag) {
        SourceLocation location = parserContext.getLocation();
        return new TaggedTemplateExpression(tag, delegates.literals.parseTemplateLiteral(true), location);
    }

    Expression parseUnaryExpression() {
        if (parserContext.match(TokenType.ASYNC) && parserContext.nextToken.type() == TokenType.FUNCTION
                && parserContext.nextToken.line() == parserContext.currentToken.line()) {
            parserContext.requireUnescapedToken("Keyword must not contain escaped characters");
            SourceLocation asyncLocation = parserContext.consumeLocation();
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
            SourceLocation location = parserContext.consumeLocation();
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
            SourceLocation location = parserContext.consumeLocation();
            Expression operand = parseUnaryExpression();
            if (operand instanceof Identifier identifier) {
                String identifierName = identifier.getName();
                if ("import.meta".equals(identifierName) || "new.target".equals(identifierName)
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
            if (operand.containsOptionalChain()) {
                throw new JSSyntaxErrorException("Invalid left-hand side expression in prefix operation");
            }
            return new UnaryExpression(op, operand, true, location);
        }

        UnaryOperator op = UnaryOperator.prefixOperator(parserContext.currentToken.type());
        if (op != null) {
            SourceLocation location = parserContext.consumeLocation();
            Expression operand = parseUnaryExpression();
            if (op == UnaryOperator.DELETE) {
                // The early error is stated over IdentifierReference. `this` and `new.target` are
                // parsed as Identifier nodes but are a PrimaryExpression and a MetaProperty, so
                // `delete this` and `delete new.target` are legal in strict mode and evaluate to
                // true.
                if (parserContext.strictMode && ParserContext.isIdentifierReference(operand)) {
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

    /**
     * The parenthesized form just read can only be an arrow function's parameter list, so {@code =>} must follow on the
     * same line.
     *
     * @param message
     *            V8's message for the token that makes it a parameter list
     */
    private void requireArrowAfterCoverParameters(String message) {
        if (!parserContext.match(TokenType.ARROW)) {
            throw new JSSyntaxErrorException(message);
        }
        if (parserContext.hasNewlineBefore()) {
            throw new JSSyntaxErrorException("Unexpected token '=>'");
        }
    }

    private static boolean isPrivateDeleteTarget(Expression expression) {
        if (expression instanceof MemberExpression memberExpression) {
            return memberExpression.getProperty() instanceof PrivateIdentifier;
        }
        return false;
    }

    private static Object parseDecimalOrLegacyOctal(String normalizedValue) {
        // Check for legacy octal: starts with 0, followed by digits, all digits 0-7
        if (normalizedValue.length() > 1 && normalizedValue.charAt(0) == '0' && normalizedValue.charAt(1) >= '0'
                && normalizedValue.charAt(1) <= '9' && normalizedValue.indexOf('.') == -1
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
                        ? (int) longVal
                        : (double) longVal;
            }
        }
        double doubleVal = Double.parseDouble(normalizedValue);
        if (doubleVal == Math.floor(doubleVal) && !Double.isInfinite(doubleVal) && doubleVal >= Integer.MIN_VALUE
                && doubleVal <= Integer.MAX_VALUE) {
            return (int) doubleVal;
        }
        return doubleVal;
    }

    private static Literal parseNumericLiteral(String token, SourceLocation location, boolean bigInt) {
        String digits = token.replace("_", "");
        int radix = 10;
        if (digits.length() > 1 && digits.charAt(0) == '0') {
            radix = switch (digits.charAt(1)) {
                case 'x', 'X' -> 16;
                case 'o', 'O' -> 8;
                case 'b', 'B' -> 2;
                default -> 10;
            };
        }
        if (radix != 10) {
            digits = digits.substring(2);
        }
        if (bigInt) {
            return new Literal(new BigInteger(digits, radix), location);
        }
        if (radix == 10) {
            return new Literal(parseDecimalOrLegacyOctal(digits), location);
        }
        return new Literal((double) Long.parseLong(digits, radix), location);
    }

    /**
     * Check if an ObjectExpression contains CoverInitializedName (shorthand property with default). Per spec 13.2.5.1:
     * it is a SyntaxError if an ObjectLiteral that is not a destructuring pattern contains a CoverInitializedName.
     */
    private static void validateNoCoverInitializedName(Expression expr) {
        if (expr instanceof ObjectExpression objectExpression) {
            for (ObjectExpressionProperty property : objectExpression.getProperties()) {
                if (property.isShorthand() && property.getValue() instanceof AssignmentExpression assignmentExpression
                        && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN) {
                    throw new JSSyntaxErrorException("Invalid shorthand property initializer");
                }
            }
        }
    }
}
