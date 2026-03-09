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
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate parser for destructuring patterns (array patterns, object patterns).
 */
record PatternParser(ParserContext parserContext, ParserDelegates delegates) {

    ArrayPattern parseArrayPattern() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACKET);
        List<Pattern> elements = new ArrayList<>();
        while (!parserContext.match(TokenType.RBRACKET) && !parserContext.match(TokenType.EOF)) {
            if (!elements.isEmpty()) {
                parserContext.expect(TokenType.COMMA);
                if (parserContext.match(TokenType.RBRACKET)) {
                    break;
                }
            }
            if (parserContext.match(TokenType.COMMA)) {
                elements.add(null);
            } else if (parserContext.match(TokenType.ELLIPSIS)) {
                SourceLocation restLocation = parserContext.getLocation();
                parserContext.advance();
                Pattern argument = parsePattern();
                elements.add(new RestElement(argument, restLocation));
                if (parserContext.match(TokenType.COMMA)) {
                    throw new RuntimeException("Rest element must be last in array pattern at line " +
                            parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
                }
                break;
            } else {
                Pattern element = parsePattern();
                if (parserContext.match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = parserContext.getLocation();
                    parserContext.advance();
                    Expression defaultValue = delegates.expressions.parseAssignmentExpression();
                    element = new AssignmentPattern(element, defaultValue, assignLoc);
                }
                elements.add(element);
            }
        }
        parserContext.expect(TokenType.RBRACKET);
        return new ArrayPattern(elements, location);
    }

    ObjectPattern parseObjectPattern() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACE);
        List<ObjectPatternProperty> properties = new ArrayList<>();
        RestElement restElement = null;
        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            if (!properties.isEmpty() || restElement != null) {
                parserContext.expect(TokenType.COMMA);
                if (parserContext.match(TokenType.RBRACE)) {
                    break;
                }
            }
            if (parserContext.match(TokenType.ELLIPSIS)) {
                SourceLocation restLoc = parserContext.getLocation();
                parserContext.advance();
                Pattern restArgument = parsePattern();
                restElement = new RestElement(restArgument, restLoc);
                if (!parserContext.match(TokenType.RBRACE)) {
                    throw new JSSyntaxErrorException("Rest element must be last element");
                }
                break;
            }
            boolean computed = parserContext.match(TokenType.LBRACKET);
            Expression key = delegates.expressions.parsePropertyName();
            Pattern value;
            boolean shorthand = false;
            if (parserContext.match(TokenType.COLON)) {
                parserContext.advance();
                value = parsePattern();
                if (parserContext.match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = parserContext.getLocation();
                    parserContext.advance();
                    Expression defaultValue = delegates.expressions.parseAssignmentExpression();
                    value = new AssignmentPattern(value, defaultValue, assignLoc);
                }
            } else if (!computed && key instanceof Identifier keyIdentifier) {
                value = keyIdentifier;
                shorthand = true;
                if (parserContext.match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = parserContext.getLocation();
                    parserContext.advance();
                    Expression defaultValue = delegates.expressions.parseAssignmentExpression();
                    value = new AssignmentPattern(value, defaultValue, assignLoc);
                }
            } else {
                throw new JSSyntaxErrorException("Expected ':' in object binding pattern at line "
                        + parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
            }
            properties.add(new ObjectPatternProperty(key, value, computed, shorthand));
        }
        parserContext.expect(TokenType.RBRACE);
        return new ObjectPattern(properties, restElement, location);
    }

    Pattern parsePattern() {
        if (parserContext.match(TokenType.LBRACE)) {
            return parseObjectPattern();
        } else if (parserContext.match(TokenType.LBRACKET)) {
            return parseArrayPattern();
        } else {
            return parserContext.parseIdentifier();
        }
    }
}
