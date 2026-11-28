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
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.List;

/** Interprets the array/object cover grammar in arrow parameters and destructuring assignments. */
final class AssignmentPatterns {

    private AssignmentPatterns() {
    }

    private static void checkParameterRest(int index, int size, Expression argument) {
        if (index != size - 1) {
            throw new JSSyntaxErrorException("Rest element must be last element");
        }
        if (argument instanceof AssignmentExpression) {
            throw new JSSyntaxErrorException("Rest element cannot have a default initializer");
        }
    }

    private static ArrayPattern convertArrowArrayExpressionToPattern(ParserContext parserContext,
            ArrayExpression arrayExpression) {
        List<Pattern> elements = new ArrayList<>();
        for (int elementIndex = 0; elementIndex < arrayExpression.getElements().size(); elementIndex++) {
            Expression elementExpression = arrayExpression.getElements().get(elementIndex);
            if (elementExpression == null) {
                elements.add(null);
            } else if (elementExpression instanceof SpreadElement spreadElement) {
                checkParameterRest(elementIndex, arrayExpression.getElements().size(), spreadElement.getArgument());
                Pattern restArgumentPattern = convertArrowExpressionToPattern(parserContext,
                        spreadElement.getArgument());
                elements.add(new RestElement(restArgumentPattern, spreadElement.getLocation()));
            } else {
                elements.add(convertArrowExpressionToPattern(parserContext, elementExpression));
            }
        }
        return new ArrayPattern(elements, arrayExpression.getLocation());
    }

    static Pattern convertArrowExpressionToPattern(ParserContext parserContext, Expression expression) {
        if (expression instanceof Identifier identifier) {
            parserContext.validateBindingIdentifier(identifier.getName());
            return identifier;
        }
        if (expression instanceof ObjectExpression objectExpression) {
            return convertArrowObjectExpressionToPattern(parserContext, objectExpression);
        }
        if (expression instanceof ArrayExpression arrayExpression) {
            return convertArrowArrayExpressionToPattern(parserContext, arrayExpression);
        }
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN) {
            Pattern leftPattern = convertArrowExpressionToPattern(parserContext, assignmentExpression.getLeft());
            return new AssignmentPattern(leftPattern, assignmentExpression.getRight(),
                    assignmentExpression.getLocation());
        }
        throw invalidArrowParameter(parserContext);
    }

    private static ObjectPattern convertArrowObjectExpressionToPattern(ParserContext parserContext,
            ObjectExpression objectExpression) {
        List<ObjectPatternProperty> properties = new ArrayList<>();
        RestElement restElement = null;
        List<ObjectExpressionProperty> objProperties = objectExpression.getProperties();
        for (int i = 0; i < objProperties.size(); i++) {
            ObjectExpressionProperty property = objProperties.get(i);
            if ("spread".equals(property.getKind())) {
                checkParameterRest(i, objProperties.size(), property.getValue());
                Pattern restArgumentPattern = convertArrowExpressionToPattern(parserContext, property.getValue());
                restElement = new RestElement(restArgumentPattern, property.getValue().getLocation());
                continue;
            }
            if (!"init".equals(property.getKind())) {
                throw invalidArrowParameter(parserContext);
            }
            Expression propertyKey = property.getKey();
            boolean validNonComputedKey = propertyKey instanceof Identifier
                    || (propertyKey instanceof Literal literal && (literal.getValue() instanceof String
                            || literal.getValue() instanceof Integer || literal.getValue() instanceof Long
                            || literal.getValue() instanceof Double || literal.getValue() instanceof Float));
            if (!property.isComputed() && !validNonComputedKey) {
                throw invalidArrowParameter(parserContext);
            }
            Pattern valuePattern = convertArrowExpressionToPattern(parserContext, property.getValue());
            properties.add(new ObjectPatternProperty(propertyKey, valuePattern, property.isComputed(),
                    property.isShorthand()));
        }
        return new ObjectPattern(properties, restElement, objectExpression.getLocation());
    }

    private static Expression extractAssignmentTarget(Expression expression) {
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN) {
            return assignmentExpression.getLeft();
        }
        return expression;
    }

    private static boolean hasTrailingCommaAfterRestElement(ParserContext parserContext,
            ArrayExpression arrayExpression, int assignmentOperatorOffset) {
        String source = parserContext.tokens.source();
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

    private static JSSyntaxErrorException invalidArrowParameter(ParserContext parserContext) {
        return new JSSyntaxErrorException("Invalid arrow function parameter at line "
                + parserContext.tokens.current().line() + ", column " + parserContext.tokens.current().column());
    }

    private static boolean isArrowDestructuringParameterExpression(Expression expression) {
        if (expression instanceof ObjectExpression || expression instanceof ArrayExpression) {
            return true;
        }
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN) {
            return assignmentExpression.getLeft() instanceof ObjectExpression
                    || assignmentExpression.getLeft() instanceof ArrayExpression;
        }
        return false;
    }

    static void parseArrowParameterExpression(ParserContext parserContext, Expression expression, List<Pattern> params,
            List<Expression> defaults) {
        if (expression instanceof Identifier identifier) {
            params.add(identifier);
            defaults.add(null);
            return;
        }
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN
                && assignmentExpression.getLeft() instanceof Identifier parameterIdentifier) {
            params.add(parameterIdentifier);
            defaults.add(assignmentExpression.getRight());
            return;
        }
        if (isArrowDestructuringParameterExpression(expression)) {
            parseDestructuringArrowParameter(parserContext, expression, params, defaults);
            return;
        }
        throw invalidArrowParameter(parserContext);
    }

    static void parseDestructuringArrowParameter(ParserContext parserContext, Expression expression,
            List<Pattern> params, List<Expression> defaults) {
        Pattern parameterPattern;
        Expression defaultExpression = null;
        if (expression instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN) {
            parameterPattern = convertArrowExpressionToPattern(parserContext, assignmentExpression.getLeft());
            defaultExpression = assignmentExpression.getRight();
        } else {
            parameterPattern = convertArrowExpressionToPattern(parserContext, expression);
        }
        params.add(parameterPattern);
        defaults.add(defaultExpression);
    }

    private static void validateArrayAssignmentPatternTarget(ParserContext parserContext,
            ArrayExpression arrayExpression, int assignmentOperatorOffset) {
        List<Expression> elements = arrayExpression.getElements();
        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            Expression elementExpression = elements.get(elementIndex);
            if (elementExpression == null) {
                continue;
            }
            if (elementExpression instanceof SpreadElement spreadElement) {
                validateRestTarget(parserContext, spreadElement.getArgument(), elementIndex, elements.size(),
                        assignmentOperatorOffset);
                continue;
            }
            validateElement(parserContext, elementExpression, assignmentOperatorOffset);
        }
        if (!elements.isEmpty() && elements.get(elements.size() - 1) instanceof SpreadElement
                && hasTrailingCommaAfterRestElement(parserContext, arrayExpression, assignmentOperatorOffset)) {
            throw new JSSyntaxErrorException("Rest element must be last element");
        }
    }

    static void validateAssignmentPatternTarget(ParserContext parserContext, Expression expression,
            int assignmentOperatorOffset) {
        if (parserContext.isParenthesizedExpression(expression)) {
            if (expression instanceof ArrayExpression || expression instanceof ObjectExpression) {
                throw new JSSyntaxErrorException("Invalid destructuring assignment target");
            }
        }
        if (expression instanceof Identifier identifier) {
            if ("import.meta".equals(identifier.getName()) || "new.target".equals(identifier.getName())
                    || JSKeyword.THIS.equals(identifier.getName())) {
                throw new JSSyntaxErrorException("Invalid destructuring assignment target");
            }
            // Per spec: IdentifierReference cannot be a ReservedWord.
            // This catches shorthand properties like { break } or { def\u0061ult } in destructuring.
            parserContext.validateBindingIdentifier(identifier.getName());
            if (parserContext.strictMode && (JSKeyword.EVAL.equals(identifier.getName())
                    || JSKeyword.ARGUMENTS.equals(identifier.getName()))) {
                throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
            }
            return;
        }
        if (expression instanceof MemberExpression memberExpression) {
            if (memberExpression.isOptional()) {
                throw new JSSyntaxErrorException("Invalid destructuring assignment target");
            }
            return;
        }
        if (expression instanceof ArrayExpression arrayExpression) {
            validateArrayAssignmentPatternTarget(parserContext, arrayExpression, assignmentOperatorOffset);
            return;
        }
        if (expression instanceof ObjectExpression objectExpression) {
            validateObjectAssignmentPatternTarget(parserContext, objectExpression, assignmentOperatorOffset);
            return;
        }
        throw new JSSyntaxErrorException("Invalid destructuring assignment target");
    }

    private static void validateElement(ParserContext parserContext, Expression value, int offset) {
        Expression target = extractAssignmentTarget(value);
        if (target != value && parserContext.isParenthesizedExpression(value)) {
            throw new JSSyntaxErrorException("Invalid destructuring assignment target");
        }
        validateAssignmentPatternTarget(parserContext, target, offset);
    }

    static void validateForInOfAssignmentTarget(ParserContext parserContext, Expression expression,
            int assignmentBoundaryOffset) {
        if (expression instanceof CallExpression) {
            if (parserContext.strictMode) {
                throw new JSSyntaxErrorException("Invalid left-hand side in for-loop");
            }
            return;
        }
        validateAssignmentPatternTarget(parserContext, expression, assignmentBoundaryOffset);
    }

    private static void validateObjectAssignmentPatternTarget(ParserContext parserContext,
            ObjectExpression objectExpression, int assignmentOperatorOffset) {
        List<ObjectExpressionProperty> properties = objectExpression.getProperties();
        for (int propertyIndex = 0; propertyIndex < properties.size(); propertyIndex++) {
            ObjectExpressionProperty property = properties.get(propertyIndex);
            if ("spread".equals(property.getKind())) {
                validateRestTarget(parserContext, property.getValue(), propertyIndex, properties.size(),
                        assignmentOperatorOffset);
                continue;
            }
            if (!"init".equals(property.getKind())) {
                throw new JSSyntaxErrorException("Invalid destructuring assignment target");
            }
            Expression propertyValue = property.getValue();
            validateElement(parserContext, propertyValue, assignmentOperatorOffset);
        }
    }

    private static void validateRestTarget(ParserContext parserContext, Expression value, int index, int size,
            int offset) {
        if (index != size - 1) {
            throw new JSSyntaxErrorException("Rest element must be last element");
        }
        if (extractAssignmentTarget(value) != value) {
            throw new JSSyntaxErrorException("Rest element cannot have a default initializer");
        }
        validateAssignmentPatternTarget(parserContext, value, offset);
    }
}
