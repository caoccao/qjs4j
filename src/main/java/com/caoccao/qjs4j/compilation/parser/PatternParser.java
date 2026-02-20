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

import com.caoccao.qjs4j.compilation.TokenType;
import com.caoccao.qjs4j.compilation.ast.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate parser for destructuring patterns (array patterns, object patterns).
 */
record PatternParser(ParserContext ctx, ParserDelegates delegates) {

    ArrayPattern parseArrayPattern() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.LBRACKET);
        List<Pattern> elements = new ArrayList<>();
        while (!ctx.match(TokenType.RBRACKET) && !ctx.match(TokenType.EOF)) {
            if (!elements.isEmpty()) {
                ctx.expect(TokenType.COMMA);
                if (ctx.match(TokenType.RBRACKET)) break;
            }
            if (ctx.match(TokenType.COMMA)) {
                elements.add(null);
            } else if (ctx.match(TokenType.ELLIPSIS)) {
                SourceLocation restLocation = ctx.getLocation();
                ctx.advance();
                Pattern argument = parsePattern();
                elements.add(new RestElement(argument, restLocation));
                if (ctx.match(TokenType.COMMA)) {
                    throw new RuntimeException("Rest element must be last in array pattern at line " +
                            ctx.currentToken.line() + ", column " + ctx.currentToken.column());
                }
                break;
            } else {
                Pattern element = parsePattern();
                if (ctx.match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = ctx.getLocation();
                    ctx.advance();
                    Expression defaultValue = delegates.expressions.parseAssignmentExpression();
                    element = new AssignmentPattern(element, defaultValue, assignLoc);
                }
                elements.add(element);
            }
        }
        ctx.expect(TokenType.RBRACKET);
        return new ArrayPattern(elements, location);
    }

    ObjectPattern parseObjectPattern() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.LBRACE);
        List<ObjectPattern.Property> properties = new ArrayList<>();
        while (!ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
            if (!properties.isEmpty()) {
                ctx.expect(TokenType.COMMA);
                if (ctx.match(TokenType.RBRACE)) break;
            }
            Identifier key = ctx.parseIdentifier();
            Pattern value;
            boolean shorthand = false;
            if (ctx.match(TokenType.COLON)) {
                ctx.advance();
                value = parsePattern();
                if (ctx.match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = ctx.getLocation();
                    ctx.advance();
                    Expression defaultValue = delegates.expressions.parseAssignmentExpression();
                    value = new AssignmentPattern(value, defaultValue, assignLoc);
                }
            } else {
                value = key;
                shorthand = true;
                if (ctx.match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = ctx.getLocation();
                    ctx.advance();
                    Expression defaultValue = delegates.expressions.parseAssignmentExpression();
                    value = new AssignmentPattern(value, defaultValue, assignLoc);
                }
            }
            properties.add(new ObjectPattern.Property(key, value, shorthand));
        }
        ctx.expect(TokenType.RBRACE);
        return new ObjectPattern(properties, location);
    }

    Pattern parsePattern() {
        if (ctx.match(TokenType.LBRACE)) {
            return parseObjectPattern();
        } else if (ctx.match(TokenType.LBRACKET)) {
            return parseArrayPattern();
        } else {
            return ctx.parseIdentifier();
        }
    }
}
