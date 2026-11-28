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
import com.caoccao.qjs4j.compilation.lexer.Lexer;
import com.caoccao.qjs4j.compilation.lexer.LexerTemplateScanner;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate parser for literal expressions: arrays, objects, and template literals. Also contains template scanning
 * utility methods for finding expression boundaries and processing escape sequences within template strings.
 */
final class LiteralParser extends GrammarProduction {
    LiteralParser(ParserContext parserContext) {
        super(parserContext);
    }

    Expression parseArrayExpression() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.LBRACKET);

        List<Expression> elements = new ArrayList<>();

        if (!tokens.match(TokenType.RBRACKET)) {
            do {
                if (tokens.consumeIf(TokenType.COMMA)) {
                    elements.add(null); // hole in array
                } else if (tokens.match(TokenType.ELLIPSIS)) {
                    // Spread element: ...expr
                    SourceLocation spreadLocation = tokens.consumeLocation(); // consume ELLIPSIS
                    Expression argument = parserContext.expressions.parseAssignmentExpression();
                    elements.add(new SpreadElement(argument, spreadLocation));
                    if (tokens.consumeIf(TokenType.COMMA)) {
                    } else if (!tokens.match(TokenType.RBRACKET)) {
                        // After a spread element, expect comma or closing bracket
                        tokens.expect(TokenType.RBRACKET);
                    }
                } else {
                    elements.add(parserContext.expressions.parseAssignmentExpression());
                    if (tokens.consumeIf(TokenType.COMMA)) {
                    } else if (!tokens.match(TokenType.RBRACKET)) {
                        // After an element, expect comma or closing bracket
                        // This catches cases like [a b] which should be a SyntaxError
                        tokens.expect(TokenType.RBRACKET);
                    }
                }
            } while (!tokens.match(TokenType.RBRACKET) && !tokens.match(TokenType.EOF));
        }

        tokens.expect(TokenType.RBRACKET);
        return new ArrayExpression(elements, location);
    }

    Expression parseObjectExpression() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.LBRACE);

        List<ObjectExpressionProperty> properties = new ArrayList<>();

        while (!tokens.match(TokenType.RBRACE) && !tokens.match(TokenType.EOF)) {
            // Spread property: {...expr}
            if (tokens.match(TokenType.ELLIPSIS)) {
                SourceLocation spreadLocation = tokens.consumeLocation(); // consume ELLIPSIS
                Expression argument = parserContext.expressions.parseAssignmentExpression();
                properties.add(new ObjectExpressionProperty(null, argument, "spread", false, false, false));
                if (tokens.consumeIf(TokenType.COMMA)) {
                } else {
                    break;
                }
                continue;
            }

            boolean isAsync = false;
            boolean isGenerator = false;

            // Capture the method start location before any modifiers (async, *)
            SourceLocation methodStartLocation = tokens.getLocation();

            // Check for 'async' modifier (only if NOT followed by colon, comma, etc.)
            if (tokens.match(TokenType.ASYNC)) {
                // Peek ahead to determine if this is a modifier or property name
                TokenType nextType = tokens.next().type();
                if (nextType == TokenType.COLON || nextType == TokenType.COMMA || nextType == TokenType.RBRACE
                        || nextType == TokenType.LPAREN || nextType == TokenType.ASSIGN) {
                    // { async: value }, { async, ... }, { async }, { async() {} }, { async = x }
                    // async is a property name, not a modifier
                } else if (tokens.nextOnSameLine()) {
                    tokens.requireUnescapedToken("Unexpected token IDENTIFIER");
                    // async is likely a modifier for method
                    isAsync = true;
                    tokens.advance();
                }
            }

            // Check for generator *
            if (tokens.match(TokenType.MUL)) {
                isGenerator = true;
                tokens.advance();
            }

            // Check for getter/setter: get name() {} or set name(v) {}
            // Similar to parseClassElement logic
            if (!isAsync && !isGenerator && tokens.match(TokenType.IDENTIFIER)) {
                String name = tokens.current().value();
                if ((JSKeyword.GET.equals(name) || JSKeyword.SET.equals(name)) && !tokens.nextIs(TokenType.COLON)
                        && !tokens.nextIs(TokenType.COMMA) && !tokens.nextIs(TokenType.LPAREN)
                        && !tokens.nextIs(TokenType.RBRACE)) {
                    if (tokens.current().escaped()) {
                        throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
                    }
                    String kind = name;
                    tokens.advance(); // consume 'get' or 'set'

                    // Parse property name after get/set (may be computed)
                    boolean computed = tokens.match(TokenType.LBRACKET);
                    Expression key = parserContext.primary.parsePropertyName();

                    boolean savedSuperPropertyAllowed = parserContext.superPropertyAllowed;
                    parserContext.superPropertyAllowed = true;
                    FunctionExpression value;
                    try {
                        value = parserContext.functions.parseMethod(kind, methodStartLocation, false, false);
                    } finally {
                        parserContext.superPropertyAllowed = savedSuperPropertyAllowed;
                    }
                    properties.add(new ObjectExpressionProperty(key, value, kind, computed, false, false));

                    if (tokens.consumeIf(TokenType.COMMA)) {
                    } else {
                        break;
                    }
                    continue;
                }
            }

            // If no async/generator prefix, update methodStartLocation to the property name position
            if (!isAsync && !isGenerator) {
                methodStartLocation = tokens.getLocation();
            }

            // Parse property name (can be identifier, string, number, or computed [expr])
            boolean computed = tokens.match(TokenType.LBRACKET);
            Expression key = parserContext.primary.parsePropertyName();

            // Determine if this is a method or regular property
            if ((isAsync || isGenerator) && !tokens.match(TokenType.LPAREN)) {
                throw new JSSyntaxErrorException("Unexpected token " + tokens.current().type());
            }
            if (tokens.match(TokenType.LPAREN)) {
                // Method shorthand: name() {} or async name() {} or *name() {} or async *name() {}
                boolean savedSuperPropertyAllowed = parserContext.superPropertyAllowed;
                parserContext.superPropertyAllowed = true;
                FunctionExpression value;
                try {
                    value = parserContext.functions.parseMethod("method", methodStartLocation, isAsync, isGenerator);
                } finally {
                    parserContext.superPropertyAllowed = savedSuperPropertyAllowed;
                }
                properties.add(new ObjectExpressionProperty(key, value, "init", computed, false, true));
            } else if (tokens.match(TokenType.COLON)) {
                // Regular property: key: value
                tokens.advance();
                Expression value = parserContext.expressions.parseAssignmentExpression();
                properties.add(new ObjectExpressionProperty(key, value, "init", computed, false, false));
            } else if (!computed && key instanceof Identifier keyId && (tokens.match(TokenType.COMMA)
                    || tokens.match(TokenType.RBRACE) || tokens.match(TokenType.ASSIGN))) {
                // Shorthand property: {x} or CoverInitializedName: {x = defaultExpr}
                // Following QuickJS: the shorthand value is an IdentifierReference,
                // so yield/await must be valid identifiers in the current context.
                // Always-reserved words (e.g. this, break, if) can never be IdentifierReferences.
                if (parserContext.isAlwaysReservedIdentifier(keyId.getName())) {
                    throw new JSSyntaxErrorException("Unexpected reserved word");
                }
                if (parserContext.strictMode && parserContext.isStrictReservedIdentifier(keyId.getName())) {
                    throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
                }
                if (JSKeyword.YIELD.equals(keyId.getName()) && !parserContext.isYieldIdentifierAllowed()) {
                    throw new JSSyntaxErrorException("Unexpected reserved word");
                }
                if (JSKeyword.AWAIT.equals(keyId.getName())
                        && (parserContext.isAwaitExpressionAllowed() || parserContext.inClassStaticInit)) {
                    throw new JSSyntaxErrorException("Unexpected reserved word");
                }
                // Shorthand value is an IdentifierReference to the variable.
                // If referencing 'arguments' or 'eval', the enclosing function needs
                // the implicit arguments binding, same as parseIdentifierReference().
                if (JSKeyword.ARGUMENTS.equals(keyId.getName()) || JSKeyword.EVAL.equals(keyId.getName())) {
                    parserContext.needsArguments = true;
                }
                Expression value;
                if (tokens.consumeIf(TokenType.ASSIGN)) {
                    Expression defaultValue = parserContext.expressions.parseAssignmentExpression();
                    value = new AssignmentExpression(keyId, AssignmentOperator.ASSIGN, defaultValue,
                            keyId.getLocation());
                } else {
                    value = keyId;
                }
                properties.add(new ObjectExpressionProperty(key, value, "init", false, true, false));
            } else {
                // Fallback: expect colon
                tokens.expect(TokenType.COLON);
                Expression value = parserContext.expressions.parseAssignmentExpression();
                properties.add(new ObjectExpressionProperty(key, value, "init", computed, false, false));
            }

            if (tokens.consumeIf(TokenType.COMMA)) {
            } else {
                break;
            }
        }

        tokens.expect(TokenType.RBRACE);
        return new ObjectExpression(properties, location);
    }

    Expression parseTemplateExpression(String expressionSource) {
        if (expressionSource.isBlank()) {
            throw new JSSyntaxErrorException("Empty template expression");
        }

        Lexer expressionLexer = new Lexer(expressionSource);
        expressionLexer.setStrictMode(parserContext.strictMode);
        Parser expressionParser = new Parser(expressionLexer, parserContext.moduleMode, parserContext.isEval,
                parserContext.inheritedStrictMode, parserContext.functionNesting, parserContext.asyncFunctionNesting,
                parserContext.generatorFunctionNesting, parserContext.newTargetNesting,
                parserContext.superPropertyAllowed, parserContext.allowNewTargetInEval,
                parserContext.inDerivedConstructor, parserContext.evalPrivateNames);
        Expression expression = expressionParser.parseExpression();
        if (expressionParser.currentToken().type() != TokenType.EOF) {
            throw new JSSyntaxErrorException("Invalid template expression");
        }
        return expression;
    }

    TemplateLiteral parseTemplateLiteral(boolean tagged) {
        SourceLocation location = tokens.getLocation();
        String templateStr = tokens.consumeValue();

        List<String> quasis = new ArrayList<>();
        List<String> rawQuasis = new ArrayList<>();
        List<Expression> expressions = new ArrayList<>();

        int start = 0;
        while (true) {
            int delimiter = LexerTemplateScanner.nextDelimiter(templateStr, start);
            String rawQuasi = normalizeTemplateLineTerminators(templateStr.substring(start, delimiter));
            rawQuasis.add(rawQuasi);
            quasis.add(processTemplateEscapeSequences(rawQuasi, tagged));
            if (delimiter == templateStr.length()) {
                break;
            }
            int expressionStart = delimiter + 2;
            int end = LexerTemplateScanner.expressionEnd(templateStr, expressionStart);
            expressions.add(parseTemplateExpression(templateStr.substring(expressionStart, end)));
            start = end + 1;
        }
        return new TemplateLiteral(quasis, rawQuasis, expressions, location);
    }

    private static String normalizeTemplateLineTerminators(String str) {
        StringBuilder normalized = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\r') {
                if (i + 1 < str.length() && str.charAt(i + 1) == '\n') {
                    i++;
                }
                normalized.append('\n');
            } else {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private static String processTemplateEscapeSequences(String str, boolean tagged) {
        StringBuilder result = new StringBuilder(str.length());
        int i = 0;
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c != '\\') {
                result.append(c);
                i++;
                continue;
            }

            if (i + 1 >= str.length()) {
                if (tagged) {
                    return null;
                }
                throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
            }

            char next = str.charAt(i + 1);
            switch (next) {
                case '\n' -> i += 2; // Line continuation
                case '\r' -> {
                    i += 2;
                    if (i < str.length() && str.charAt(i) == '\n') {
                        i++;
                    }
                }
                case '\u2028', '\u2029' -> i += 2; // Line continuation
                case '\\', '\'', '"', '`', '$' -> {
                    result.append(next);
                    i += 2;
                }
                case 'b' -> {
                    result.append('\b');
                    i += 2;
                }
                case 'f' -> {
                    result.append('\f');
                    i += 2;
                }
                case 'n' -> {
                    result.append('\n');
                    i += 2;
                }
                case 'r' -> {
                    result.append('\r');
                    i += 2;
                }
                case 't' -> {
                    result.append('\t');
                    i += 2;
                }
                case 'v' -> {
                    result.append('\u000B');
                    i += 2;
                }
                case '0' -> {
                    if (i + 2 < str.length() && Character.isDigit(str.charAt(i + 2))) {
                        if (tagged) {
                            return null;
                        }
                        throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                    }
                    result.append('\0');
                    i += 2;
                }
                case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (tagged) {
                        return null;
                    }
                    throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                }
                case 'x' -> {
                    if (i + 3 >= str.length() || Character.digit(str.charAt(i + 2), 16) < 0
                            || Character.digit(str.charAt(i + 3), 16) < 0) {
                        if (tagged) {
                            return null;
                        }
                        throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                    }
                    int value = Integer.parseInt(str.substring(i + 2, i + 4), 16);
                    result.append((char) value);
                    i += 4;
                }
                case 'u' -> {
                    if (i + 2 < str.length() && str.charAt(i + 2) == '{') {
                        int end = i + 3;
                        while (end < str.length() && str.charAt(end) != '}') {
                            if (Character.digit(str.charAt(end), 16) < 0) {
                                if (tagged) {
                                    return null;
                                }
                                throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                            }
                            end++;
                        }
                        if (end >= str.length() || end == i + 3) {
                            if (tagged) {
                                return null;
                            }
                            throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                        }
                        int codePoint = 0;
                        for (int j = i + 3; j < end; j++) {
                            int digit = Character.digit(str.charAt(j), 16);
                            if (digit < 0) {
                                if (tagged) {
                                    return null;
                                }
                                throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                            }
                            if (codePoint > (0x10FFFF - digit) / 16) {
                                if (tagged) {
                                    return null;
                                }
                                throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                            }
                            codePoint = (codePoint << 4) | digit;
                        }
                        if (codePoint < 0 || codePoint > 0x10FFFF) {
                            if (tagged) {
                                return null;
                            }
                            throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                        }
                        result.appendCodePoint(codePoint);
                        i = end + 1;
                    } else {
                        if (i + 5 >= str.length()) {
                            if (tagged) {
                                return null;
                            }
                            throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                        }
                        int codeUnit = 0;
                        for (int j = i + 2; j < i + 6; j++) {
                            int digit = Character.digit(str.charAt(j), 16);
                            if (digit < 0) {
                                if (tagged) {
                                    return null;
                                }
                                throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                            }
                            codeUnit = (codeUnit << 4) | digit;
                        }
                        result.append((char) codeUnit);
                        i += 6;
                    }
                }
                default -> {
                    // NonEscapeCharacter: keep the escaped character and drop '\'.
                    result.append(next);
                    i += 2;
                }
            }
        }
        return result.toString();
    }

}
