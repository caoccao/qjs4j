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
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.List;

final class ExpressionAssignmentParser {

    private ExpressionAssignmentParser() {
    }

    private static ArrowFunctionExpression finishArrow(ParserContext parserContext, FunctionParams params, ASTNode body,
            boolean async, SourceLocation location) {
        ParameterEarlyErrors.validateArrow(parserContext, params, body);
        if (body instanceof BlockStatement block) {
            ParameterEarlyErrors.validateFormalsBodyDuplicate(params, block.getBody());
        }
        return new ArrowFunctionExpression(params, body, async,
                location.withEndOffset(parserContext.tokens.previousEnd()));
    }

    private static ArrowFunctionExpression parseArrowBody(ParserContext parserContext, FunctionParams params,
            boolean async, SourceLocation location) {
        parserContext.enterLexicalFunction(async);
        boolean enclosingStaticInit = parserContext.inClassStaticInit;
        parserContext.inClassStaticInit = false;
        ASTNode body;
        try {
            body = readArrowBody(parserContext);
        } finally {
            parserContext.inClassStaticInit = enclosingStaticInit;
            parserContext.exitLexicalFunction(async);
        }
        return finishArrow(parserContext, params, body, async, location);
    }

    static Expression parseAssignmentExpression(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        // Save whether the current token is a direct identifier for fn-name inference (spec 13.15.2 step 1.c)
        TokenType startTokenType = tokens.current().type();
        boolean lhsStartsWithIdentifier = startTokenType == TokenType.IDENTIFIER || startTokenType == TokenType.ASYNC
                || startTokenType == TokenType.AWAIT || startTokenType == TokenType.YIELD
                || startTokenType == TokenType.FROM || startTokenType == TokenType.OF;

        // ES2024 12.7.1: Escaped contextual keywords (e.g. \u0061sync) are IDENTIFIER tokens.
        // When escaped `async` precedes `function` on the same line, V8 gives
        // "Keyword must not contain escaped characters" rather than a generic parse error.
        if (tokens.match(TokenType.IDENTIFIER) && tokens.current().escaped()
                && JSKeyword.ASYNC.equals(tokens.current().value()) && tokens.nextOnSameLine(TokenType.FUNCTION)) {
            throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
        }
        if (tokens.match(TokenType.ASYNC)) {
            SourceLocation asyncLocation = location;
            TokenCursor.TokenPosition start = tokens.saveTokenPosition();

            tokens.advance();

            if (!tokens.hasNewlineBefore()) {
                if (tokens.match(TokenType.FUNCTION)) {
                    Expression asyncFunc = FunctionClassParser.parseFunctionExpression(parserContext, true,
                            asyncLocation);
                    return ExpressionPrimaryParser.parsePostPrimaryExpression(parserContext, asyncFunc, location);
                }

                if (tokens.current().type().isIdentifierCandidate() && tokens.nextIs(TokenType.ARROW)) {
                    Identifier param = parserContext.parseIdentifier();
                    parserContext.validateBindingIdentifier(param.getName(), true);
                    if (tokens.hasNewlineBefore()) {
                        // ArrowParameters [no LineTerminator here] =>
                        throw new JSSyntaxErrorException("Unexpected token '=>'");
                    }
                    tokens.expect(TokenType.ARROW);

                    return parseArrowBody(parserContext, new FunctionParams(List.of(param), null, null), true,
                            asyncLocation);
                }

                if (tokens.match(TokenType.LPAREN) && peekPastParensIsArrow(parserContext)) {
                    parserContext.enterLexicalFunction(true);
                    boolean savedInClassStaticInit = parserContext.inClassStaticInit;
                    parserContext.inClassStaticInit = false;
                    try {
                        tokens.advance();
                        FunctionParams funcParams = FunctionClassParser.parseFunctionParameters(parserContext);
                        tokens.advance();

                        return finishArrow(parserContext, funcParams, readArrowBody(parserContext), true, location);
                    } finally {
                        parserContext.inClassStaticInit = savedInClassStaticInit;
                        parserContext.exitLexicalFunction(true);
                    }
                }
            }

            tokens.restoreTokenPosition(start);
        }

        // YieldExpression is at AssignmentExpression level per ES2024 spec (14.4)
        // It must NOT be parsed at UnaryExpression level (e.g. "void yield" must be SyntaxError in generators)
        if (tokens.match(TokenType.YIELD) && !parserContext.isYieldIdentifierAllowed()) {
            if (parserContext.generatorFunctionNesting > 0) {
                if (!parserContext.inFunctionBody) {
                    throw new JSSyntaxErrorException(
                            "'yield' expression not allowed in formal parameters of a generator function");
                }
                SourceLocation yieldLocation = tokens.consumeLocation();

                boolean delegate = false;
                // Per ES2024 14.4: yield [no LineTerminator here] * AssignmentExpression
                if (!tokens.hasNewlineBefore() && tokens.match(TokenType.MUL)) {
                    delegate = true;
                    tokens.advance();
                }

                Expression argument = null;
                if (delegate) {
                    // yield* requires an AssignmentExpression (no line terminator restriction)
                    argument = parseAssignmentExpression(parserContext);
                } else if (!tokens.hasNewlineBefore() && !tokens.match(TokenType.SEMICOLON)
                        && !tokens.match(TokenType.RBRACE) && !tokens.match(TokenType.RBRACKET)
                        && !tokens.match(TokenType.RPAREN) && !tokens.match(TokenType.TEMPLATE)
                        && !tokens.match(TokenType.COLON) && !tokens.match(TokenType.COMMA)
                        && !tokens.match(TokenType.EOF)) {
                    argument = parseAssignmentExpression(parserContext);
                }

                return new YieldExpression(argument, delegate, yieldLocation);
            }
            // In strict mode outside a generator, yield is a reserved word — fall through
            // to parseConditionalExpression which will error when it hits yield
        }

        Expression left = ExpressionBinaryParser.parseConditionalExpression(parserContext);

        if (tokens.match(TokenType.ARROW) && !tokens.hasNewlineBefore()) {
            List<Pattern> params = new ArrayList<>();
            List<Expression> defaults = new ArrayList<>();
            RestParameter restParameter = null;

            if (left instanceof Identifier identifier) {
                parserContext.validateBindingIdentifier(identifier.getName());
                params.add(identifier);
                defaults.add(null);
            } else if (left instanceof AssignmentExpression assignExpr
                    && assignExpr.getOperator() == AssignmentOperator.ASSIGN
                    && assignExpr.getLeft() instanceof Identifier paramId) {
                params.add(paramId);
                defaults.add(assignExpr.getRight());
            } else if (left instanceof ObjectExpression || (left instanceof AssignmentExpression assignmentExpression
                    && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN
                    && (assignmentExpression.getLeft() instanceof ObjectExpression
                            || assignmentExpression.getLeft() instanceof ArrayExpression))) {
                AssignmentPatterns.parseDestructuringArrowParameter(parserContext, left, params, defaults);
            } else if (left instanceof SequenceExpression seqExpr) {
                for (Expression expr : seqExpr.getExpressions()) {
                    if (parserContext.isParenthesizedExpression(expr)) {
                        // ((a), b) => 1: a parameter cannot be parenthesized.
                        throw new JSSyntaxErrorException("Invalid destructuring assignment target");
                    }
                    AssignmentPatterns.parseArrowParameterExpression(parserContext, expr, params, defaults);
                }
            } else if (left instanceof ArrayExpression arrayExpr) {
                boolean isParenthesizedParameterList = arrayExpr.getLocation().offset() == location.offset();
                if (!isParenthesizedParameterList) {
                    AssignmentPatterns.parseDestructuringArrowParameter(parserContext, left, params, defaults);
                } else if (!arrayExpr.getElements().isEmpty()) {
                    for (int i = 0; i < arrayExpr.getElements().size(); i++) {
                        Expression expr = arrayExpr.getElements().get(i);

                        if (expr instanceof SpreadElement spreadElem) {
                            if (i != arrayExpr.getElements().size() - 1) {
                                throw new JSSyntaxErrorException("Rest parameter must be last formal parameter");
                            }
                            Pattern restArgPattern = AssignmentPatterns.convertArrowExpressionToPattern(parserContext,
                                    spreadElem.getArgument());
                            restParameter = new RestParameter(restArgPattern, spreadElem.getLocation());
                        } else {
                            AssignmentPatterns.parseArrowParameterExpression(parserContext, expr, params, defaults);
                        }
                    }
                }
            } else {
                // (a + b) => 1: a parenthesized expression that is not a parameter list, as V8 reports it.
                throw new JSSyntaxErrorException("Invalid destructuring assignment target");
            }

            tokens.advance();

            return parseArrowBody(parserContext, new FunctionParams(params, defaults, restParameter), false, location);
        }

        AssignmentOperator operator = AssignmentOperator.fromToken(tokens.current().type());
        if (operator != null) {
            if (!(left instanceof Identifier) && !(left instanceof MemberExpression)
                    && !(left instanceof ArrayExpression) && !(left instanceof ObjectExpression)
                    && !(left instanceof CallExpression)) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            // Parenthesized ObjectExpression/ArrayExpression are not valid destructuring targets (spec 13.15.1)
            if (parserContext.isParenthesizedExpression(left)
                    && (left instanceof ObjectExpression || left instanceof ArrayExpression)) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            if (left instanceof Identifier newTargetId && "new.target".equals(newTargetId.getName())) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            if (left instanceof Identifier importMetaId && "import.meta".equals(importMetaId.getName())) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            // PrimaryExpression: this — AssignmentTargetType is "invalid" (spec 13.2.1)
            if (left instanceof Identifier thisId && JSKeyword.THIS.equals(thisId.getName())) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            // In strict mode, CallExpression as assignment LHS is an early SyntaxError
            // (follows QuickJS; V8 uses web-compat runtime error instead)
            if (parserContext.strictMode && left instanceof CallExpression) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            // OptionalExpression is never a valid assignment target (spec 13.15.1)
            if (left.containsOptionalChain()) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            if (parserContext.strictMode && left instanceof Identifier identifier) {
                String identifierName = identifier.getName();
                if (JSKeyword.EVAL.equals(identifierName) || JSKeyword.ARGUMENTS.equals(identifierName)) {
                    throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                }
            }

            location = tokens.getLocation();
            int assignmentOperatorOffset = location.offset();
            tokens.advance();
            Expression right = parseAssignmentExpression(parserContext);

            if (operator == AssignmentOperator.ASSIGN
                    && (left instanceof ArrayExpression || left instanceof ObjectExpression)) {
                AssignmentPatterns.validateAssignmentPatternTarget(parserContext, left, assignmentOperatorOffset);
            }

            boolean isIdentifierRef = lhsStartsWithIdentifier && left instanceof Identifier;
            return new AssignmentExpression(left, operator, right, isIdentifierRef, location);
        }

        return left;
    }

    static Expression parseExpression(ParserContext parserContext) {
        SourceLocation location = parserContext.tokens.getLocation();
        List<Expression> expressionsList = new ArrayList<>();

        expressionsList.add(parseAssignmentExpression(parserContext));

        while (parserContext.tokens.consumeIf(TokenType.COMMA)) {
            expressionsList.add(parseAssignmentExpression(parserContext));
        }

        if (expressionsList.size() == 1) {
            return expressionsList.get(0);
        }

        return new SequenceExpression(expressionsList, location);
    }

    private static boolean peekPastParensIsArrow(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        TokenCursor.TokenPosition start = tokens.saveTokenPosition();
        try {
            tokens.advance();
            int depth = 1;
            while (depth > 0 && !tokens.match(TokenType.EOF)) {
                if (tokens.match(TokenType.LPAREN)) {
                    depth++;
                } else if (tokens.match(TokenType.RPAREN)) {
                    depth--;
                }
                if (depth > 0) {
                    tokens.advance();
                }
            }
            if (depth == 0) {
                tokens.advance();
                // ArrowParameters [no LineTerminator here] =>
                return tokens.match(TokenType.ARROW) && !tokens.hasNewlineBefore();
            }
            return false;
        } finally {
            tokens.restoreTokenPosition(start);
        }
    }

    private static ASTNode readArrowBody(ParserContext parserContext) {
        return parserContext.tokens.match(TokenType.LBRACE)
                ? StatementParser.parseBlockStatementAsBody(parserContext)
                : parseAssignmentExpression(parserContext);
    }

    static void validateForInOfAssignmentTarget(ParserContext parserContext, Expression expression,
            int assignmentBoundaryOffset) {
        AssignmentPatterns.validateForInOfAssignmentTarget(parserContext, expression, assignmentBoundaryOffset);
    }
}
