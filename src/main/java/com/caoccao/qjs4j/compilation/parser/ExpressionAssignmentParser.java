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
import com.caoccao.qjs4j.compilation.lexer.LexerState;
import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.List;

final class ExpressionAssignmentParser {
    private final ParserContext ctx;
    private final ParserDelegates delegates;
    private final ExpressionParser expressions;

    ExpressionAssignmentParser(ParserContext ctx, ParserDelegates delegates, ExpressionParser expressions) {
        this.ctx = ctx;
        this.delegates = delegates;
        this.expressions = expressions;
    }

    Expression parseAssignmentExpression() {
        SourceLocation location = ctx.getLocation();

        if (ctx.match(TokenType.ASYNC)) {
            SourceLocation asyncLocation = location;
            Token savedCurrent = ctx.currentToken;
            Token savedNext = ctx.nextToken;
            int savedPrevLine = ctx.previousTokenLine;
            LexerState savedLexer = ctx.lexer.saveState();

            ctx.advance();

            if (!ctx.hasNewlineBefore()) {
                if (ctx.match(TokenType.FUNCTION)) {
                    Expression asyncFunc = delegates.functions.parseFunctionExpression(true, asyncLocation);
                    return expressions.parsePostPrimaryExpression(asyncFunc, location);
                }

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

                if (ctx.match(TokenType.LPAREN) && ctx.peekPastParensIsArrow()) {
                    ctx.enterFunctionContext(true);
                    try {
                        ctx.advance();
                        FunctionParams funcParams = delegates.functions.parseFunctionParameters();
                        ctx.advance();

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
                        return new ArrowFunctionExpression(
                                funcParams.params(),
                                funcParams.defaults(),
                                funcParams.restParameter(),
                                body,
                                true,
                                fullLocation);
                    } finally {
                        ctx.exitFunctionContext(true);
                    }
                }
            }

            ctx.currentToken = savedCurrent;
            ctx.nextToken = savedNext;
            ctx.previousTokenLine = savedPrevLine;
            ctx.lexer.restoreState(savedLexer);
        }

        Expression left = expressions.parseConditionalExpression();

        if (ctx.match(TokenType.ARROW)) {
            List<Identifier> params = new ArrayList<>();
            List<Expression> defaults = new ArrayList<>();
            RestParameter restParameter = null;

            if (left instanceof Identifier) {
                params.add((Identifier) left);
                defaults.add(null);
            } else if (left instanceof AssignmentExpression assignExpr
                    && assignExpr.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                    && assignExpr.left() instanceof Identifier paramId) {
                params.add(paramId);
                defaults.add(assignExpr.right());
            } else if (left instanceof SequenceExpression seqExpr) {
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
                if (!arrayExpr.elements().isEmpty()) {
                    for (int i = 0; i < arrayExpr.elements().size(); i++) {
                        Expression expr = arrayExpr.elements().get(i);

                        if (expr instanceof SpreadElement spreadElem) {
                            if (spreadElem.argument() instanceof Identifier restId) {
                                restParameter = new RestParameter(restId, spreadElem.getLocation());
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
                throw new RuntimeException("Unsupported arrow function parameters at line " +
                        ctx.currentToken.line() + ", column " + ctx.currentToken.column());
            }

            ctx.advance();

            ASTNode body;
            ctx.enterFunctionContext(false);
            try {
                if (ctx.match(TokenType.LBRACE)) {
                    body = delegates.statements.parseBlockStatement();
                } else {
                    body = parseAssignmentExpression();
                }
            } finally {
                ctx.exitFunctionContext(false);
            }

            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    ctx.currentToken.offset()
            );

            return new ArrowFunctionExpression(params, defaults, restParameter, body, false, fullLocation);
        }

        if (ctx.isAssignmentOperator(ctx.currentToken.type())) {
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

    Expression parseExpression() {
        SourceLocation location = ctx.getLocation();
        List<Expression> expressionsList = new ArrayList<>();

        expressionsList.add(parseAssignmentExpression());

        while (ctx.match(TokenType.COMMA)) {
            ctx.advance();
            expressionsList.add(parseAssignmentExpression());
        }

        if (expressionsList.size() == 1) {
            return expressionsList.get(0);
        }

        return new SequenceExpression(expressionsList, location);
    }
}
