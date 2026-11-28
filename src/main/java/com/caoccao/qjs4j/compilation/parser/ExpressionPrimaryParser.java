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

final class ExpressionPrimaryParser extends GrammarProduction {

    ExpressionPrimaryParser(ParserContext parserContext) {
        super(parserContext);
    }

    private boolean continuesOptionalChain(Expression base) {
        return base.isPartOfOptionalChain() && !parserContext.isParenthesizedExpression(base);
    }

    private CallExpression createCallExpression(Expression callee, List<Expression> arguments, boolean optional,
            SourceLocation location) {
        boolean partOfOptionalChain = optional || continuesOptionalChain(callee);
        return new CallExpression(callee, arguments, optional, partOfOptionalChain, location);
    }

    private MemberExpression createMemberExpression(Expression object, Expression property, boolean computed,
            boolean optional, SourceLocation location) {
        boolean partOfOptionalChain = optional || continuesOptionalChain(object);
        return new MemberExpression(object, property, computed, optional, partOfOptionalChain, location);
    }

    private List<Expression> parseCallArguments() {
        tokens.advance();
        List<Expression> args = new ArrayList<>();

        if (!tokens.match(TokenType.RPAREN)) {
            do {
                if (tokens.consumeIf(TokenType.COMMA)) {
                    if (tokens.match(TokenType.RPAREN)) {
                        break;
                    }
                }
                if (tokens.match(TokenType.ELLIPSIS)) {
                    SourceLocation spreadLocation = tokens.consumeLocation();
                    Expression argument = parserContext.expressions.parseAssignmentExpression();
                    args.add(new SpreadElement(argument, spreadLocation));
                } else {
                    args.add(parserContext.expressions.parseAssignmentExpression());
                }
            } while (tokens.match(TokenType.COMMA));
        }

        tokens.expect(TokenType.RPAREN);
        return args;
    }

    /** The cover grammar after an async function parses computed keys as assignment expressions. */
    private Expression parseCallChain(Expression expr, boolean afterAsyncFunction) {
        while (true) {
            switch (tokens.current().type()) {
                case TEMPLATE -> {
                    if (expr.isPartOfOptionalChain()
                            && (afterAsyncFunction || !parserContext.isParenthesizedExpression(expr))) {
                        throw new JSSyntaxErrorException("Invalid tagged template on optional chain");
                    }
                    expr = parseTaggedTemplate(expr);
                }
                case LPAREN -> {
                    if (!afterAsyncFunction && expr instanceof Identifier identifier
                            && JSKeyword.IMPORT.equals(identifier.getName())) {
                        throw new JSSyntaxErrorException("Unexpected token '('");
                    }
                    expr = parseCallSuffix(expr);
                }
                case OPTIONAL_CHAINING -> expr = parseOptionalLink(expr);
                case DOT, LBRACKET -> {
                    if (!afterAsyncFunction) {
                        validateNoCoverInitializedName(expr);
                    }
                    expr = tokens.match(TokenType.DOT)
                            ? parseNamedMember(expr, tokens.consumeLocation())
                            : parseComputedMember(expr, afterAsyncFunction);
                }
                default -> {
                    return expr;
                }
            }
        }
    }

    Expression parseCallExpression() {
        return parseCallChain(parseMemberExpression(), false);
    }

    private Expression parseCallSuffix(Expression callee) {
        SourceLocation location = tokens.getLocation();
        return createCallExpression(callee, parseCallArguments(), false, location);
    }

    private Expression parseComputedMember(Expression object, boolean assignmentOnly) {
        SourceLocation location = tokens.consumeLocation();
        Expression property = assignmentOnly
                ? parserContext.expressions.parseAssignmentExpression()
                : parserContext.expressions.parseExpression();
        tokens.expect(TokenType.RBRACKET);
        return createMemberExpression(object, property, true, false, location);
    }

    private SpreadElement parseCoverRestElement() {
        SourceLocation location = tokens.consumeLocation();
        Expression argument = switch (tokens.current().type()) {
            case LBRACKET -> parserContext.literals.parseArrayExpression();
            case LBRACE -> parserContext.literals.parseObjectExpression();
            default -> parserContext.parseIdentifier();
        };
        return new SpreadElement(argument, location);
    }

    Expression parseMemberExpression() {
        if (tokens.match(TokenType.NEW)) {
            SourceLocation location = tokens.getLocation();
            tokens.requireUnescapedToken("Unexpected token NEW");
            tokens.advance();

            // Handle new.target meta-property
            if (tokens.match(TokenType.DOT) && tokens.nextIs(TokenType.IDENTIFIER)
                    && JSKeyword.TARGET.equals(tokens.next().value())) {
                if (tokens.next().escaped()) {
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
                tokens.advance(); // consume '.'
                tokens.advance(); // consume 'target'
                return new Identifier("new.target", location);
            }

            boolean startsWithImport = tokens.match(TokenType.IMPORT);
            if (startsWithImport && tokens.nextIs(TokenType.LPAREN)) {
                throw new JSSyntaxErrorException("Unexpected import call in constructor position");
            }

            Expression callee = parseMemberExpression();
            if (startsWithImport && callee instanceof ImportExpression) {
                throw new JSSyntaxErrorException("Unexpected import call in constructor position");
            }

            while (true) {
                if (tokens.consumeIf(TokenType.DOT)) {
                    callee = parseNamedMember(callee, tokens.getLocation());
                } else if (tokens.match(TokenType.LBRACKET)) {
                    // A computed member is located at its `[`, as in the call loop and as V8 reports it.
                    callee = parseComputedMember(callee, false);
                } else if (tokens.match(TokenType.TEMPLATE)) {
                    // No optional chain can reach here: `new a?.b` is rejected, and a parenthesized
                    // chain such as `new (a?.b)`x`` is an ordinary tag.
                    callee = parseTaggedTemplate(callee);
                } else {
                    break;
                }
            }
            if (tokens.match(TokenType.OPTIONAL_CHAINING)) {
                throw new JSSyntaxErrorException("Invalid optional chain from new expression");
            }

            List<Expression> args = tokens.match(TokenType.LPAREN) ? parseCallArguments() : new ArrayList<>();
            return new NewExpression(callee, args, location);
        }

        Expression expr = parsePrimaryExpression();
        while (true) {
            if (tokens.match(TokenType.DOT)) {
                validateNoCoverInitializedName(expr);
                tokens.advance();
                expr = parseNamedMember(expr, tokens.getLocation());
            } else if (tokens.match(TokenType.LBRACKET)) {
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
        TokenType type = tokens.current().type();
        if (type.isIdentifierName()) {
            return tokens.consumeIdentifierName();
        }
        if (type == TokenType.PRIVATE_NAME) {
            return parserContext.consumePrivateName();
        }
        if (type == TokenType.EOF) {
            throw new JSSyntaxErrorException("Unexpected end of input");
        }
        throw tokens.unexpectedToken();
    }

    private Expression parseNamedMember(Expression base, SourceLocation location) {
        return createMemberExpression(base, parseMemberPropertyName(), false, false, location);
    }

    private Expression parseOptionalLink(Expression base) {
        SourceLocation location = tokens.consumeLocation();
        if (tokens.match(TokenType.LPAREN)) {
            return createCallExpression(base, parseCallArguments(), true, location);
        }
        if (tokens.consumeIf(TokenType.LBRACKET)) {
            Expression property = parserContext.expressions.parseExpression();
            tokens.expect(TokenType.RBRACKET);
            return createMemberExpression(base, property, true, true, location);
        }
        return createMemberExpression(base, parseMemberPropertyName(), false, true, location);
    }

    Expression parsePostfixExpression() {
        Expression expr = parseCallExpression();

        if (!tokens.hasNewlineBefore() && (tokens.match(TokenType.INC) || tokens.match(TokenType.DEC))) {
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
            UnaryOperator op = tokens.match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = tokens.consumeLocation();
            return new UnaryExpression(op, expr, false, location);
        }

        return expr;
    }

    Expression parsePostPrimaryExpression(Expression expr) {
        expr = parseCallChain(expr, true);

        AssignmentOperator operator = AssignmentOperator.fromToken(tokens.current().type());
        if (operator != null) {
            // Validate that expr is a valid assignment target
            if (!(expr instanceof Identifier) && !(expr instanceof MemberExpression)
                    && !(expr instanceof CallExpression)) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            SourceLocation loc = tokens.getLocation();
            if (parserContext.strictMode && expr instanceof Identifier identifier) {
                String identifierName = identifier.getName();
                if (JSKeyword.EVAL.equals(identifierName) || JSKeyword.ARGUMENTS.equals(identifierName)) {
                    throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                }
            }
            tokens.advance();
            Expression right = parserContext.expressions.parseAssignmentExpression();
            return new AssignmentExpression(expr, operator, right, loc);
        }

        return expr;
    }

    Expression parsePrimaryExpression() {
        SourceLocation location = tokens.getLocation();

        return switch (tokens.current().type()) {
            case NUMBER -> {
                String value = tokens.current().value();
                // In strict mode, legacy octal (010, 07) and non-octal decimal (08, 09)
                // literals are forbidden. These start with '0' followed by a digit.
                if (parserContext.strictMode && value.length() > 1 && value.charAt(0) == '0') {
                    char second = value.charAt(1);
                    if (second >= '0' && second <= '9') {
                        throw new JSSyntaxErrorException("Octal literals are not allowed in strict mode.");
                    }
                }
                tokens.advance();
                yield parseNumericLiteral(value, location, false);
            }
            case BIGINT -> parseNumericLiteral(tokens.consumeValue(), location, true);
            case STRING -> new Literal(tokens.consumeValue(), location);
            case REGEX -> {
                String value = tokens.consumeValue();
                yield new Literal(new RegExpLiteralValue(value), location);
            }
            case TEMPLATE -> parserContext.literals.parseTemplateLiteral(false);
            case TRUE -> {
                tokens.requireUnescapedToken("Keyword must not contain escaped characters");
                tokens.advance();
                yield new Literal(true, location);
            }
            case FALSE -> {
                tokens.requireUnescapedToken("Keyword must not contain escaped characters");
                tokens.advance();
                yield new Literal(false, location);
            }
            case NULL -> {
                tokens.requireUnescapedToken("Keyword must not contain escaped characters");
                tokens.advance();
                yield new Literal(null, location);
            }
            case IDENTIFIER, AS, ASYNC, AWAIT, YIELD, FROM, OF, LET -> parserContext.parseIdentifier();
            case PRIVATE_NAME -> {
                String name = tokens.current().value();
                String fieldName = name.substring(1);
                if (!parserContext.isPrivateNameAccessible(fieldName)) {
                    throw new JSSyntaxErrorException("undefined private field '#" + fieldName + "'");
                }
                // A private name begins an expression only as the left operand of `in`, which the relational parser
                // takes before it gets here: `(#x in o)` is a brand check, `(#x) in o` and `#x + 1` are errors.
                throw new JSSyntaxErrorException("Unexpected identifier '" + name + "'");
            }
            case THIS -> {
                tokens.advance();
                yield new Identifier(JSKeyword.THIS, location);
            }
            case LPAREN -> {
                tokens.advance();

                if (tokens.consumeIf(TokenType.RPAREN)) {
                    // `()` is only an arrow function's empty parameter list; as an expression it is an error. It
                    // used to evaluate to an empty array.
                    requireArrowAfterCoverParameters("Unexpected token ')'");
                    yield new ArrayExpression(new ArrayList<>(), location);
                }

                if (tokens.match(TokenType.IDENTIFIER) || tokens.match(TokenType.ELLIPSIS)) {
                    if (tokens.match(TokenType.ELLIPSIS)) {
                        SpreadElement rest = parseCoverRestElement();
                        tokens.expect(TokenType.RPAREN);
                        requireArrowAfterCoverParameters("Unexpected token '...'");
                        yield new ArrayExpression(List.of(rest), location);
                    }

                    {
                        boolean savedIn = parserContext.inOperatorAllowed;
                        parserContext.inOperatorAllowed = true;
                        Expression firstExpr = parserContext.expressions.parseAssignmentExpression();

                        if (!tokens.match(TokenType.COMMA)) {
                            // Single expression in parens
                            parserContext.inOperatorAllowed = savedIn;
                            tokens.expect(TokenType.RPAREN);
                            // An expression that starts with an identifier is never itself parenthesized, so
                            // ((a)) => 1 is caught below, where the inner parentheses are parsed.
                            parserContext.markParenthesizedExpression(firstExpr);
                            yield firstExpr;
                        }

                        // Multiple expressions - may contain rest element for arrow params
                        List<Expression> elements = new ArrayList<>();
                        elements.add(firstExpr);
                        boolean trailingComma = false;

                        while (tokens.consumeIf(TokenType.COMMA)) {

                            if (tokens.match(TokenType.RPAREN)) {
                                // Trailing comma
                                trailingComma = true;
                                break;
                            }

                            if (tokens.match(TokenType.ELLIPSIS)) {
                                // Rest/spread: (expr, ...rest) for arrow params
                                SpreadElement rest = parseCoverRestElement();
                                elements.add(rest);
                                break;
                            }

                            elements.add(parserContext.expressions.parseAssignmentExpression());
                        }

                        parserContext.inOperatorAllowed = savedIn;
                        tokens.expect(TokenType.RPAREN);

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
                    Expression expr = parserContext.expressions.parseExpression();
                    parserContext.inOperatorAllowed = savedIn;
                    tokens.expect(TokenType.RPAREN);
                    if (parserContext.isParenthesizedExpression(expr) && tokens.match(TokenType.ARROW)) {
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
                Token regexToken = tokens.rescanAsRegex();
                String value = regexToken.value();
                tokens.advance();
                yield new Literal(new RegExpLiteralValue(value), location);
            }
            case LBRACKET -> parserContext.literals.parseArrayExpression();
            case LBRACE -> parserContext.literals.parseObjectExpression();
            case FUNCTION -> parserContext.functions.parseFunctionExpression();
            case CLASS -> parserContext.functions.parseClassExpression();
            case AT -> {
                parserContext.functions.parseDecoratorList();
                // After decorators, must be a class expression
                if (!tokens.match(TokenType.CLASS)) {
                    throw new JSSyntaxErrorException("Decorators are not valid here");
                }
                yield parserContext.functions.parseClassExpression();
            }
            case IMPORT -> {
                tokens.requireUnescapedToken("Unexpected token IMPORT");
                tokens.advance(); // consume 'import'
                if (tokens.match(TokenType.DOT)) {
                    if (tokens.nextIs(TokenType.IDENTIFIER) && "meta".equals(tokens.next().value())
                            && !tokens.next().escaped()) {
                        if (!parserContext.moduleMode) {
                            throw new JSSyntaxErrorException("Cannot use 'import.meta' outside a module");
                        }
                        // import.meta
                        tokens.advance(); // consume '.'
                        tokens.advance(); // consume 'meta'
                        yield new Identifier("import.meta", location);
                    } else if (tokens.nextIs(TokenType.IDENTIFIER) && "defer".equals(tokens.next().value())
                            && !tokens.next().escaped()) {
                        // import.defer(specifier)
                        tokens.advance(); // consume '.'
                        tokens.advance(); // consume 'defer'
                        tokens.expect(TokenType.LPAREN);
                        Expression source = parserContext.expressions.parseAssignmentExpression();
                        tokens.expect(TokenType.RPAREN);
                        yield new ImportExpression(source, null, true, location);
                    } else {
                        throw new JSSyntaxErrorException("The only valid meta property for import is 'import.meta'");
                    }
                } else if (tokens.consumeIf(TokenType.LPAREN)) {
                    boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                    parserContext.inOperatorAllowed = true;
                    try {
                        Expression source = parserContext.expressions.parseAssignmentExpression();
                        Expression options = null;
                        if (tokens.consumeIf(TokenType.COMMA)) {
                            if (!tokens.match(TokenType.RPAREN)) {
                                options = parserContext.expressions.parseAssignmentExpression();
                                if (tokens.consumeIf(TokenType.COMMA)) {
                                }
                            }
                        }
                        tokens.expect(TokenType.RPAREN);
                        yield new ImportExpression(source, options, location);
                    } finally {
                        parserContext.inOperatorAllowed = savedInOperatorAllowed;
                    }
                } else {
                    throw new JSSyntaxErrorException("Unexpected token " + tokens.current().type());
                }
            }
            case SUPER -> {
                tokens.advance();
                if (tokens.match(TokenType.LPAREN)) {
                    if (parserContext.inDerivedConstructor) {
                        yield new Identifier(JSKeyword.SUPER, location);
                    }
                    throw new JSSyntaxErrorException("'super' keyword unexpected here");
                }
                if (parserContext.superPropertyAllowed
                        && (tokens.match(TokenType.DOT) || tokens.match(TokenType.LBRACKET))) {
                    yield new Identifier(JSKeyword.SUPER, location);
                }
                throw new JSSyntaxErrorException("'super' keyword unexpected here");
            }
            default -> throw tokens.unexpectedToken();
        };
    }

    Expression parsePropertyName() {
        TokenType type = tokens.current().type();
        if (type != TokenType.NULL && type.isIdentifierName()) {
            return tokens.consumeIdentifierName();
        }
        SourceLocation location = tokens.getLocation();
        return switch (type) {
            case PRIVATE_NAME -> parserContext.consumePrivateName();
            case STRING -> new Literal(tokens.consumeValue(), location);
            case NUMBER -> parseNumericLiteral(tokens.consumeValue(), location, false);
            case BIGINT -> parseNumericLiteral(tokens.consumeValue(), location, true);
            case LBRACKET -> {
                tokens.advance();
                // Allow 'in' operator inside computed property names
                boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                parserContext.inOperatorAllowed = true;
                Expression expr = parserContext.expressions.parseAssignmentExpression();
                parserContext.inOperatorAllowed = savedInOperatorAllowed;
                tokens.expect(TokenType.RBRACKET);
                yield expr;
            }
            case NULL -> {
                tokens.advance();
                yield new Literal(null, location);
            }
            default -> throw new JSSyntaxErrorException("Unexpected end of input");
        };
    }

    private Expression parseTaggedTemplate(Expression tag) {
        SourceLocation location = tokens.getLocation();
        return new TaggedTemplateExpression(tag, parserContext.literals.parseTemplateLiteral(true), location);
    }

    Expression parseUnaryExpression() {
        if (tokens.match(TokenType.ASYNC) && tokens.nextOnSameLine(TokenType.FUNCTION)) {
            tokens.requireUnescapedToken("Keyword must not contain escaped characters");
            SourceLocation asyncLocation = tokens.consumeLocation();
            Expression asyncFunctionExpression = parserContext.functions.parseFunctionExpression(true, asyncLocation);
            return parsePostPrimaryExpression(asyncFunctionExpression);
        }

        if (tokens.match(TokenType.AWAIT)) {
            if (tokens.current().escaped() && parserContext.isAwaitExpressionAllowed()) {
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
            SourceLocation location = tokens.consumeLocation();
            Expression argument = parseUnaryExpression();
            return new AwaitExpression(argument, location);
        }

        if (tokens.match(TokenType.YIELD)) {
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

        if (tokens.match(TokenType.INC) || tokens.match(TokenType.DEC)) {
            UnaryOperator op = tokens.match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = tokens.consumeLocation();
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

        UnaryOperator op = UnaryOperator.prefixOperator(tokens.current().type());
        if (op != null) {
            SourceLocation location = tokens.consumeLocation();
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
        if (!tokens.match(TokenType.ARROW)) {
            throw new JSSyntaxErrorException(message);
        }
        if (tokens.hasNewlineBefore()) {
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
