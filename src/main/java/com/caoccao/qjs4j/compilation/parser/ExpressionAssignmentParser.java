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
    private final ParserDelegates delegates;
    private final ExpressionParser expressions;
    private final ParserContext parserContext;

    ExpressionAssignmentParser(ParserContext parserContext, ParserDelegates delegates, ExpressionParser expressions) {
        this.parserContext = parserContext;
        this.delegates = delegates;
        this.expressions = expressions;
    }

    private ArrayPattern convertArrowArrayExpressionToPattern(ArrayExpression arrayExpression) {
        List<Pattern> elements = new ArrayList<>();
        for (Expression elementExpression : arrayExpression.elements()) {
            if (elementExpression == null) {
                elements.add(null);
            } else if (elementExpression instanceof SpreadElement spreadElement) {
                Pattern restArgumentPattern = convertArrowExpressionToPattern(spreadElement.argument());
                elements.add(new RestElement(restArgumentPattern, spreadElement.getLocation()));
            } else {
                elements.add(convertArrowExpressionToPattern(elementExpression));
            }
        }
        return new ArrayPattern(elements, arrayExpression.getLocation());
    }

    private Pattern convertArrowExpressionToPattern(Expression expression) {
        if (expression instanceof Identifier identifier) {
            return identifier;
        }
        if (expression instanceof ObjectExpression objectExpression) {
            return convertArrowObjectExpressionToPattern(objectExpression);
        }
        if (expression instanceof ArrayExpression arrayExpression) {
            return convertArrowArrayExpressionToPattern(arrayExpression);
        }
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
            Pattern leftPattern = convertArrowExpressionToPattern(assignmentExpression.left());
            return new AssignmentPattern(leftPattern, assignmentExpression.right(), assignmentExpression.getLocation());
        }
        throw new RuntimeException("Invalid arrow function parameter at line " +
                parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
    }

    private ObjectPattern convertArrowObjectExpressionToPattern(ObjectExpression objectExpression) {
        List<ObjectPattern.Property> properties = new ArrayList<>();
        for (ObjectExpression.Property property : objectExpression.properties()) {
            if (!"init".equals(property.kind()) || property.computed()) {
                throw new RuntimeException("Invalid arrow function parameter at line " +
                        parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
            }
            if (!(property.key() instanceof Identifier)) {
                throw new RuntimeException("Invalid arrow function parameter at line " +
                        parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
            }
            Pattern valuePattern = convertArrowExpressionToPattern(property.value());
            properties.add(new ObjectPattern.Property(property.key(), valuePattern, property.shorthand()));
        }
        return new ObjectPattern(properties, objectExpression.getLocation());
    }

    private Identifier createSyntheticArrowParameterIdentifier(SourceLocation location, int syntheticParameterCount) {
        String name = "$qjs4j$arrowParam$" + syntheticParameterCount + "$" + location.offset();
        return new Identifier(name, location);
    }

    private VariableDeclaration createSyntheticArrowParameterPreludeStatement(
            Pattern pattern,
            Identifier sourceIdentifier,
            SourceLocation location) {
        VariableDeclaration.VariableDeclarator variableDeclarator =
                new VariableDeclaration.VariableDeclarator(pattern, sourceIdentifier);
        return new VariableDeclaration(List.of(variableDeclarator), VariableKind.LET, location);
    }

    private boolean isArrowDestructuringParameterExpression(Expression expression) {
        if (expression instanceof ObjectExpression || expression instanceof ArrayExpression) {
            return true;
        }
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
            return assignmentExpression.left() instanceof ObjectExpression
                    || assignmentExpression.left() instanceof ArrayExpression;
        }
        return false;
    }

    private int parseArrowParameterExpression(
            Expression expression,
            List<Pattern> params,
            List<Expression> defaults,
            List<Statement> parameterPreludeStatements,
            int syntheticParameterCount) {
        if (expression instanceof Identifier identifier) {
            params.add(identifier);
            defaults.add(null);
            return syntheticParameterCount;
        }
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                && assignmentExpression.left() instanceof Identifier parameterIdentifier) {
            params.add(parameterIdentifier);
            defaults.add(assignmentExpression.right());
            return syntheticParameterCount;
        }
        if (isArrowDestructuringParameterExpression(expression)) {
            return parseDestructuringArrowParameter(
                    expression,
                    params,
                    defaults,
                    parameterPreludeStatements,
                    syntheticParameterCount);
        }
        throw new RuntimeException("Invalid arrow function parameter at line " +
                parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
    }

    Expression parseAssignmentExpression() {
        SourceLocation location = parserContext.getLocation();

        if (parserContext.match(TokenType.ASYNC)) {
            SourceLocation asyncLocation = location;
            Token savedCurrent = parserContext.currentToken;
            Token savedNext = parserContext.nextToken;
            int savedPrevLine = parserContext.previousTokenLine;
            LexerState savedLexer = parserContext.lexer.saveState();

            parserContext.advance();

            if (!parserContext.hasNewlineBefore()) {
                if (parserContext.match(TokenType.FUNCTION)) {
                    Expression asyncFunc = delegates.functions.parseFunctionExpression(true, asyncLocation);
                    return expressions.parsePostPrimaryExpression(asyncFunc, location);
                }

                if (parserContext.match(TokenType.IDENTIFIER) && parserContext.nextToken.type() == TokenType.ARROW) {
                    Identifier param = parserContext.parseIdentifier();
                    parserContext.expect(TokenType.ARROW);

                    ASTNode body;
                    parserContext.enterFunctionContext(true);
                    try {
                        if (parserContext.match(TokenType.LBRACE)) {
                            body = delegates.statements.parseBlockStatement();
                        } else {
                            body = parseAssignmentExpression();
                        }
                    } finally {
                        parserContext.exitFunctionContext(true);
                    }

                    SourceLocation fullLocation = new SourceLocation(
                            asyncLocation.line(),
                            asyncLocation.column(),
                            asyncLocation.offset(),
                            parserContext.previousTokenEndOffset
                    );
                    return new ArrowFunctionExpression(List.of(param), null, null, body, true, fullLocation);
                }

                if (parserContext.match(TokenType.LPAREN) && parserContext.peekPastParensIsArrow()) {
                    parserContext.enterFunctionContext(true);
                    try {
                        parserContext.advance();
                        FunctionParams funcParams = delegates.functions.parseFunctionParameters();
                        parserContext.advance();

                        ASTNode body;
                        if (parserContext.match(TokenType.LBRACE)) {
                            body = delegates.statements.parseBlockStatement();
                        } else {
                            body = parseAssignmentExpression();
                        }

                        SourceLocation fullLocation = new SourceLocation(
                                location.line(),
                                location.column(),
                                location.offset(),
                                parserContext.previousTokenEndOffset
                        );
                        return new ArrowFunctionExpression(
                                funcParams.params(),
                                funcParams.defaults(),
                                funcParams.restParameter(),
                                body,
                                true,
                                fullLocation);
                    } finally {
                        parserContext.exitFunctionContext(true);
                    }
                }
            }

            parserContext.currentToken = savedCurrent;
            parserContext.nextToken = savedNext;
            parserContext.previousTokenLine = savedPrevLine;
            parserContext.lexer.restoreState(savedLexer);
        }

        Expression left = expressions.parseConditionalExpression();

        if (parserContext.match(TokenType.ARROW)) {
            List<Pattern> params = new ArrayList<>();
            List<Expression> defaults = new ArrayList<>();
            RestParameter restParameter = null;
            List<Statement> parameterPreludeStatements = new ArrayList<>();
            int syntheticParameterCount = 0;

            if (left instanceof Identifier) {
                params.add((Identifier) left);
                defaults.add(null);
            } else if (left instanceof AssignmentExpression assignExpr
                    && assignExpr.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                    && assignExpr.left() instanceof Identifier paramId) {
                params.add(paramId);
                defaults.add(assignExpr.right());
            } else if (left instanceof ObjectExpression
                    || (left instanceof AssignmentExpression assignmentExpression
                    && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                    && (assignmentExpression.left() instanceof ObjectExpression
                    || assignmentExpression.left() instanceof ArrayExpression))) {
                syntheticParameterCount = parseDestructuringArrowParameter(
                        left,
                        params,
                        defaults,
                        parameterPreludeStatements,
                        syntheticParameterCount);
            } else if (left instanceof SequenceExpression seqExpr) {
                for (Expression expr : seqExpr.expressions()) {
                    syntheticParameterCount = parseArrowParameterExpression(
                            expr,
                            params,
                            defaults,
                            parameterPreludeStatements,
                            syntheticParameterCount);
                }
            } else if (left instanceof ArrayExpression arrayExpr) {
                boolean isParenthesizedParameterList = arrayExpr.getLocation().offset() == location.offset();
                if (!isParenthesizedParameterList) {
                    syntheticParameterCount = parseDestructuringArrowParameter(
                            left,
                            params,
                            defaults,
                            parameterPreludeStatements,
                            syntheticParameterCount);
                } else if (!arrayExpr.elements().isEmpty()) {
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
                                        parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
                            }
                        } else {
                            syntheticParameterCount = parseArrowParameterExpression(
                                    expr,
                                    params,
                                    defaults,
                                    parameterPreludeStatements,
                                    syntheticParameterCount);
                        }
                    }
                }
            } else {
                throw new RuntimeException("Unsupported arrow function parameters at line " +
                        parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
            }

            parserContext.advance();

            ASTNode body;
            parserContext.enterFunctionContext(false);
            try {
                if (parserContext.match(TokenType.LBRACE)) {
                    body = delegates.statements.parseBlockStatement();
                } else {
                    body = parseAssignmentExpression();
                }
            } finally {
                parserContext.exitFunctionContext(false);
            }
            body = wrapArrowBodyWithParameterPrelude(body, parameterPreludeStatements);

            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    parserContext.previousTokenEndOffset
            );

            return new ArrowFunctionExpression(params, defaults, restParameter, body, false, fullLocation);
        }

        if (parserContext.isAssignmentOperator(parserContext.currentToken.type())) {
            if (!(left instanceof Identifier)
                    && !(left instanceof MemberExpression)
                    && !(left instanceof ArrayExpression)
                    && !(left instanceof ObjectExpression)
                    && !(left instanceof CallExpression)) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }

            TokenType op = parserContext.currentToken.type();
            location = parserContext.getLocation();
            parserContext.advance();
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

    private int parseDestructuringArrowParameter(
            Expression expression,
            List<Pattern> params,
            List<Expression> defaults,
            List<Statement> parameterPreludeStatements,
            int syntheticParameterCount) {
        Pattern parameterPattern;
        Expression defaultExpression = null;
        SourceLocation parameterLocation;
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
            parameterLocation = assignmentExpression.left().getLocation();
            parameterPattern = convertArrowExpressionToPattern(assignmentExpression.left());
            defaultExpression = assignmentExpression.right();
        } else {
            parameterLocation = expression.getLocation();
            parameterPattern = convertArrowExpressionToPattern(expression);
        }
        Identifier syntheticParameter = createSyntheticArrowParameterIdentifier(
                parameterLocation,
                syntheticParameterCount);
        params.add(syntheticParameter);
        defaults.add(defaultExpression);
        parameterPreludeStatements.add(createSyntheticArrowParameterPreludeStatement(
                parameterPattern,
                syntheticParameter,
                parameterLocation));
        return syntheticParameterCount + 1;
    }

    Expression parseExpression() {
        SourceLocation location = parserContext.getLocation();
        List<Expression> expressionsList = new ArrayList<>();

        expressionsList.add(parseAssignmentExpression());

        while (parserContext.match(TokenType.COMMA)) {
            parserContext.advance();
            expressionsList.add(parseAssignmentExpression());
        }

        if (expressionsList.size() == 1) {
            return expressionsList.get(0);
        }

        return new SequenceExpression(expressionsList, location);
    }

    private ASTNode wrapArrowBodyWithParameterPrelude(ASTNode body, List<Statement> parameterPreludeStatements) {
        if (parameterPreludeStatements.isEmpty()) {
            return body;
        }
        if (body instanceof BlockStatement blockStatement) {
            List<Statement> statements = new ArrayList<>(parameterPreludeStatements.size() + blockStatement.body().size());
            statements.addAll(parameterPreludeStatements);
            statements.addAll(blockStatement.body());
            return new BlockStatement(statements, blockStatement.getLocation());
        }
        if (body instanceof Expression expression) {
            List<Statement> statements = new ArrayList<>(parameterPreludeStatements.size() + 1);
            statements.addAll(parameterPreludeStatements);
            statements.add(new ReturnStatement(expression, expression.getLocation()));
            return new BlockStatement(statements, expression.getLocation());
        }
        return body;
    }
}
