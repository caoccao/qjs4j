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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ExpressionAssignmentParser {
    private static final Set<String> RESERVED_WORDS = Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "enum", "export", "extends",
            "false", "finally", "for", "function", "if", "import", "in",
            "instanceof", "new", "null", "return", "super", "switch", "this",
            "throw", "true", "try", "typeof", "var", "void", "while", "with");

    private static final Set<String> STRICT_RESERVED_WORDS = Set.of(
            "implements", "interface", "let", "package", "private", "protected",
            "public", "static", "yield");

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
        for (int elementIndex = 0; elementIndex < arrayExpression.elements().size(); elementIndex++) {
            Expression elementExpression = arrayExpression.elements().get(elementIndex);
            if (elementExpression == null) {
                elements.add(null);
            } else if (elementExpression instanceof SpreadElement spreadElement) {
                if (elementIndex != arrayExpression.elements().size() - 1) {
                    throw new JSSyntaxErrorException("Rest element must be last element");
                }
                if (spreadElement.argument() instanceof AssignmentExpression) {
                    throw new JSSyntaxErrorException("Rest element cannot have a default initializer");
                }
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
            validateBindingIdentifier(identifier.name());
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
        throw new JSSyntaxErrorException("Invalid arrow function parameter at line " +
                parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
    }

    private ObjectPattern convertArrowObjectExpressionToPattern(ObjectExpression objectExpression) {
        List<ObjectPattern.Property> properties = new ArrayList<>();
        RestElement restElement = null;
        List<ObjectExpression.Property> objProperties = objectExpression.properties();
        for (int i = 0; i < objProperties.size(); i++) {
            ObjectExpression.Property property = objProperties.get(i);
            if ("spread".equals(property.kind())) {
                if (i != objProperties.size() - 1) {
                    throw new JSSyntaxErrorException("Rest element must be last element");
                }
                if (property.value() instanceof AssignmentExpression) {
                    throw new JSSyntaxErrorException("Rest element cannot have a default initializer");
                }
                Pattern restArgumentPattern = convertArrowExpressionToPattern(property.value());
                restElement = new RestElement(restArgumentPattern, property.value().getLocation());
                continue;
            }
            if (!"init".equals(property.kind())) {
                throw new JSSyntaxErrorException("Invalid arrow function parameter at line " +
                        parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
            }
            Expression propertyKey = property.key();
            boolean validNonComputedKey = propertyKey instanceof Identifier
                    || (propertyKey instanceof Literal literal
                    && (literal.value() instanceof String
                    || literal.value() instanceof Integer
                    || literal.value() instanceof Long
                    || literal.value() instanceof Double
                    || literal.value() instanceof Float));
            if (!property.computed() && !validNonComputedKey) {
                throw new JSSyntaxErrorException("Invalid arrow function parameter at line " +
                        parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
            }
            Pattern valuePattern = convertArrowExpressionToPattern(property.value());
            properties.add(new ObjectPattern.Property(propertyKey, valuePattern, property.computed(), property.shorthand()));
        }
        return new ObjectPattern(properties, restElement, objectExpression.getLocation());
    }

    private Expression extractAssignmentTarget(Expression expression) {
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
            return assignmentExpression.left();
        }
        return expression;
    }

    private List<String> extractBoundNames(Pattern pattern) {
        if (pattern instanceof Identifier identifier) {
            return List.of(identifier.name());
        }
        if (pattern instanceof ObjectPattern objectPattern) {
            List<String> names = new ArrayList<>();
            for (ObjectPattern.Property property : objectPattern.properties()) {
                names.addAll(extractBoundNames(property.value()));
            }
            if (objectPattern.restElement() != null) {
                names.addAll(extractBoundNames(objectPattern.restElement().argument()));
            }
            return names;
        }
        if (pattern instanceof ArrayPattern arrayPattern) {
            List<String> names = new ArrayList<>();
            for (Pattern element : arrayPattern.elements()) {
                if (element != null) {
                    names.addAll(extractBoundNames(element));
                }
            }
            return names;
        }
        if (pattern instanceof AssignmentPattern assignmentPattern) {
            return extractBoundNames(assignmentPattern.left());
        }
        if (pattern instanceof RestElement restElement) {
            return extractBoundNames(restElement.argument());
        }
        return List.of();
    }

    private boolean hasTrailingCommaAfterRestElement(ArrayExpression arrayExpression, int assignmentOperatorOffset) {
        String source = parserContext.lexer.getSource();
        int startOffset = arrayExpression.getLocation().offset();
        if (startOffset < 0 || startOffset >= source.length() || startOffset >= assignmentOperatorOffset) {
            return false;
        }

        int bracketDepth = 0;
        boolean inString = false;
        char stringDelimiter = '\0';
        boolean escaped = false;
        int closingBracketOffset = -1;

        for (int index = startOffset; index < source.length() && index < assignmentOperatorOffset; index++) {
            char currentChar = source.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (currentChar == '\\') {
                    escaped = true;
                    continue;
                }
                if (currentChar == stringDelimiter) {
                    inString = false;
                }
                continue;
            }
            if (currentChar == '\'' || currentChar == '"') {
                inString = true;
                stringDelimiter = currentChar;
                continue;
            }
            if (currentChar == '[') {
                bracketDepth++;
                continue;
            }
            if (currentChar == ']') {
                bracketDepth--;
                if (bracketDepth == 0) {
                    closingBracketOffset = index;
                    break;
                }
            }
        }

        if (closingBracketOffset <= startOffset) {
            return false;
        }
        int previousOffset = closingBracketOffset - 1;
        while (previousOffset >= startOffset && Character.isWhitespace(source.charAt(previousOffset))) {
            previousOffset--;
        }
        return previousOffset >= startOffset && source.charAt(previousOffset) == ',';
    }

    private boolean hasUseStrictDirective(ASTNode body) {
        if (!(body instanceof BlockStatement blockStatement)) {
            return false;
        }
        for (Statement statement : blockStatement.body()) {
            if (statement instanceof ExpressionStatement expressionStatement
                    && expressionStatement.expression() instanceof Literal literal
                    && literal.value() instanceof String literalString) {
                if ("use strict".equals(literalString)) {
                    return true;
                }
                continue;
            }
            break;
        }
        return false;
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

    private boolean isSimpleParameterList(List<Pattern> params, List<Expression> defaults, RestParameter restParameter) {
        if (restParameter != null) {
            return false;
        }
        for (Pattern pattern : params) {
            if (!(pattern instanceof Identifier)) {
                return false;
            }
        }
        for (Expression defaultExpression : defaults) {
            if (defaultExpression != null) {
                return false;
            }
        }
        return true;
    }

    private void parseArrowParameterExpression(
            Expression expression,
            List<Pattern> params,
            List<Expression> defaults) {
        if (expression instanceof Identifier identifier) {
            params.add(identifier);
            defaults.add(null);
            return;
        }
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                && assignmentExpression.left() instanceof Identifier parameterIdentifier) {
            params.add(parameterIdentifier);
            defaults.add(assignmentExpression.right());
            return;
        }
        if (isArrowDestructuringParameterExpression(expression)) {
            parseDestructuringArrowParameter(expression, params, defaults);
            return;
        }
        throw new JSSyntaxErrorException("Invalid arrow function parameter at line " +
                parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
    }

    Expression parseAssignmentExpression() {
        SourceLocation location = parserContext.getLocation();
        // Save whether the current token is a direct identifier for fn-name inference (spec 13.15.2 step 1.c)
        TokenType startTokenType = parserContext.currentToken.type();
        boolean lhsStartsWithIdentifier = startTokenType == TokenType.IDENTIFIER
                || startTokenType == TokenType.ASYNC || startTokenType == TokenType.AWAIT
                || startTokenType == TokenType.YIELD || startTokenType == TokenType.FROM
                || startTokenType == TokenType.OF;

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

        if (parserContext.match(TokenType.ARROW) && !parserContext.hasNewlineBefore()) {
            List<Pattern> params = new ArrayList<>();
            List<Expression> defaults = new ArrayList<>();
            RestParameter restParameter = null;

            if (left instanceof Identifier identifier) {
                validateBindingIdentifier(identifier.name());
                params.add(identifier);
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
                parseDestructuringArrowParameter(left, params, defaults);
            } else if (left instanceof SequenceExpression seqExpr) {
                for (Expression expr : seqExpr.expressions()) {
                    parseArrowParameterExpression(expr, params, defaults);
                }
            } else if (left instanceof ArrayExpression arrayExpr) {
                boolean isParenthesizedParameterList = arrayExpr.getLocation().offset() == location.offset();
                if (!isParenthesizedParameterList) {
                    parseDestructuringArrowParameter(left, params, defaults);
                } else if (!arrayExpr.elements().isEmpty()) {
                    for (int i = 0; i < arrayExpr.elements().size(); i++) {
                        Expression expr = arrayExpr.elements().get(i);

                        if (expr instanceof SpreadElement spreadElem) {
                            if (i != arrayExpr.elements().size() - 1) {
                                throw new JSSyntaxErrorException("Rest parameter must be last formal parameter");
                            }
                            Pattern restArgPattern = convertArrowExpressionToPattern(spreadElem.argument());
                            restParameter = new RestParameter(restArgPattern, spreadElem.getLocation());
                        } else {
                            parseArrowParameterExpression(expr, params, defaults);
                        }
                    }
                }
            } else {
                throw new JSSyntaxErrorException("Unsupported arrow function parameters at line " +
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
            validateArrowParameters(params, defaults, restParameter, body);

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
            if (left instanceof Identifier newTargetId && "new.target".equals(newTargetId.name())) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }
            if (parserContext.strictMode && left instanceof Identifier identifier) {
                String identifierName = identifier.name();
                if ("eval".equals(identifierName) || "arguments".equals(identifierName)) {
                    throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                }
            }

            TokenType op = parserContext.currentToken.type();
            location = parserContext.getLocation();
            int assignmentOperatorOffset = location.offset();
            parserContext.advance();
            Expression right = parseAssignmentExpression();

            if (op == TokenType.ASSIGN
                    && (left instanceof ArrayExpression || left instanceof ObjectExpression)) {
                validateAssignmentPatternTarget(left, assignmentOperatorOffset);
            }

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

            boolean isIdentifierRef = lhsStartsWithIdentifier && left instanceof Identifier;
            return new AssignmentExpression(left, operator, right, isIdentifierRef, location);
        }

        return left;
    }

    private void parseDestructuringArrowParameter(
            Expression expression,
            List<Pattern> params,
            List<Expression> defaults) {
        Pattern parameterPattern;
        Expression defaultExpression = null;
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
            parameterPattern = convertArrowExpressionToPattern(assignmentExpression.left());
            defaultExpression = assignmentExpression.right();
        } else {
            parameterPattern = convertArrowExpressionToPattern(expression);
        }
        params.add(parameterPattern);
        defaults.add(defaultExpression);
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

    private void validateArrayAssignmentPatternTarget(ArrayExpression arrayExpression, int assignmentOperatorOffset) {
        List<Expression> elements = arrayExpression.elements();
        boolean seenRestElement = false;
        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            Expression elementExpression = elements.get(elementIndex);
            if (elementExpression == null) {
                if (seenRestElement) {
                    throw new JSSyntaxErrorException("Rest element must be last element");
                }
                continue;
            }
            if (elementExpression instanceof SpreadElement spreadElement) {
                if (seenRestElement || elementIndex != elements.size() - 1) {
                    throw new JSSyntaxErrorException("Rest element must be last element");
                }
                if (spreadElement.argument() instanceof AssignmentExpression assignmentExpression
                        && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
                    throw new JSSyntaxErrorException("Rest element cannot have a default initializer");
                }
                validateAssignmentPatternTarget(spreadElement.argument(), assignmentOperatorOffset);
                seenRestElement = true;
                continue;
            }
            if (seenRestElement) {
                throw new JSSyntaxErrorException("Rest element must be last element");
            }
            validateAssignmentPatternTarget(extractAssignmentTarget(elementExpression), assignmentOperatorOffset);
        }
        if (seenRestElement
                && !elements.isEmpty()
                && elements.get(elements.size() - 1) instanceof SpreadElement
                && hasTrailingCommaAfterRestElement(arrayExpression, assignmentOperatorOffset)) {
            throw new JSSyntaxErrorException("Rest element must be last element");
        }
    }

    private void validateArrowParameters(List<Pattern> params, List<Expression> defaults, RestParameter restParameter, ASTNode body) {
        boolean strictParameters = parserContext.strictMode || hasUseStrictDirective(body);
        Set<String> seen = new HashSet<>();
        for (Pattern pattern : params) {
            for (String parameterName : extractBoundNames(pattern)) {
                if (!seen.add(parameterName)) {
                    throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
                }
                if (strictParameters && ("eval".equals(parameterName) || "arguments".equals(parameterName))) {
                    throw new JSSyntaxErrorException("invalid argument name in strict code");
                }
            }
        }
        if (restParameter != null) {
            for (String restName : extractBoundNames(restParameter.argument())) {
                if (!seen.add(restName)) {
                    throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
                }
                if (strictParameters && ("eval".equals(restName) || "arguments".equals(restName))) {
                    throw new JSSyntaxErrorException("invalid argument name in strict code");
                }
            }
        }
        if (hasUseStrictDirective(body) && !isSimpleParameterList(params, defaults, restParameter)) {
            throw new JSSyntaxErrorException("Illegal 'use strict' directive in function with non-simple parameter list");
        }
    }

    private void validateAssignmentPatternTarget(Expression expression, int assignmentOperatorOffset) {
        if (expression instanceof Identifier identifier) {
            // Per spec: IdentifierReference cannot be a ReservedWord.
            // This catches shorthand properties like { break } or { def\u0061ult } in destructuring.
            validateBindingIdentifier(identifier.name());
            if (parserContext.strictMode
                    && ("eval".equals(identifier.name()) || "arguments".equals(identifier.name()))) {
                throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
            }
            return;
        }
        if (expression instanceof MemberExpression memberExpression) {
            if (memberExpression.optional()) {
                throw new JSSyntaxErrorException("Invalid destructuring assignment target");
            }
            return;
        }
        if (expression instanceof ArrayExpression arrayExpression) {
            validateArrayAssignmentPatternTarget(arrayExpression, assignmentOperatorOffset);
            return;
        }
        if (expression instanceof ObjectExpression objectExpression) {
            validateObjectAssignmentPatternTarget(objectExpression, assignmentOperatorOffset);
            return;
        }
        throw new JSSyntaxErrorException("Invalid destructuring assignment target");
    }

    private void validateBindingIdentifier(String name) {
        if (RESERVED_WORDS.contains(name)) {
            throw new JSSyntaxErrorException("Unexpected reserved word");
        }
        if (parserContext.strictMode && STRICT_RESERVED_WORDS.contains(name)) {
            throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
        }
    }

    private void validateObjectAssignmentPatternTarget(ObjectExpression objectExpression, int assignmentOperatorOffset) {
        List<ObjectExpression.Property> properties = objectExpression.properties();
        boolean seenRestElement = false;
        for (int propertyIndex = 0; propertyIndex < properties.size(); propertyIndex++) {
            ObjectExpression.Property property = properties.get(propertyIndex);
            if ("spread".equals(property.kind())) {
                if (seenRestElement || propertyIndex != properties.size() - 1) {
                    throw new JSSyntaxErrorException("Rest element must be last element");
                }
                if (property.value() instanceof AssignmentExpression assignmentExpression
                        && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
                    throw new JSSyntaxErrorException("Rest element cannot have a default initializer");
                }
                validateAssignmentPatternTarget(property.value(), assignmentOperatorOffset);
                seenRestElement = true;
                continue;
            }
            if (!"init".equals(property.kind())) {
                throw new JSSyntaxErrorException("Invalid destructuring assignment target");
            }
            if (seenRestElement) {
                throw new JSSyntaxErrorException("Rest element must be last element");
            }
            validateAssignmentPatternTarget(extractAssignmentTarget(property.value()), assignmentOperatorOffset);
        }
    }
}
