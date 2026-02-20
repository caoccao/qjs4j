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
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate parser for literal expressions: arrays, objects, and template literals.
 * Also contains template scanning utility methods for finding expression boundaries
 * and processing escape sequences within template strings.
 */
record LiteralParser(ParserContext ctx, ParserDelegates delegates) {

    private int findTemplateExpressionEnd(String templateStr, int expressionStart) {
        int braceDepth = 1;
        int pos = expressionStart;
        boolean regexAllowed = true;

        while (pos < templateStr.length()) {
            char c = templateStr.charAt(pos);

            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            if (c == '\'' || c == '"') {
                pos = skipQuotedString(templateStr, pos, c);
                regexAllowed = false;
                continue;
            }

            if (c == '`') {
                pos = skipNestedTemplateLiteral(templateStr, pos);
                regexAllowed = false;
                continue;
            }

            if (c == '/') {
                if (pos + 1 < templateStr.length()) {
                    char next = templateStr.charAt(pos + 1);
                    if (next == '/') {
                        pos = skipLineComment(templateStr, pos + 2);
                        regexAllowed = true;
                        continue;
                    }
                    if (next == '*') {
                        pos = skipBlockComment(templateStr, pos + 2);
                        regexAllowed = true;
                        continue;
                    }
                }

                if (regexAllowed) {
                    pos = skipRegexLiteral(templateStr, pos);
                    regexAllowed = false;
                    continue;
                }

                pos++;
                if (pos < templateStr.length() && templateStr.charAt(pos) == '=') {
                    pos++;
                }
                regexAllowed = true;
                continue;
            }

            if (c == '{') {
                braceDepth++;
                pos++;
                regexAllowed = true;
                continue;
            }

            if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return pos;
                }
                pos++;
                regexAllowed = false;
                continue;
            }

            if (c == '(' || c == '[' || c == ',' || c == ';' || c == ':') {
                pos++;
                regexAllowed = true;
                continue;
            }

            if (c == ')' || c == ']') {
                pos++;
                regexAllowed = false;
                continue;
            }

            if (c == '.') {
                if (pos + 2 < templateStr.length()
                        && templateStr.charAt(pos + 1) == '.'
                        && templateStr.charAt(pos + 2) == '.') {
                    pos += 3;
                    regexAllowed = true;
                } else if (pos + 1 < templateStr.length() && Character.isDigit(templateStr.charAt(pos + 1))) {
                    pos = skipNumberLiteral(templateStr, pos);
                    regexAllowed = false;
                } else {
                    pos++;
                    regexAllowed = false;
                }
                continue;
            }

            if (ctx.isIdentifierStartChar(c)) {
                int start = pos++;
                while (pos < templateStr.length() && ctx.isIdentifierPartChar(templateStr.charAt(pos))) {
                    pos++;
                }
                String identifier = templateStr.substring(start, pos);
                regexAllowed = switch (identifier) {
                    case "return", "throw", "case", "delete", "void", "typeof",
                         "instanceof", "in", "of", "new", "do", "else", "yield", "await" -> true;
                    default -> false;
                };
                continue;
            }

            if (Character.isDigit(c)) {
                pos = skipNumberLiteral(templateStr, pos);
                regexAllowed = false;
                continue;
            }

            if ("+-*%&|^!~<>=?".indexOf(c) >= 0) {
                pos++;
                if (pos < templateStr.length()) {
                    char next = templateStr.charAt(pos);
                    if (next == '=' || (next == c && "&|+-<>?".indexOf(c) >= 0)) {
                        pos++;
                    } else if (c == '=' && next == '>') {
                        pos++;
                    }
                }
                if (c == '>' && pos < templateStr.length() && templateStr.charAt(pos) == '>') {
                    pos++;
                    if (pos < templateStr.length() && templateStr.charAt(pos) == '>') {
                        pos++;
                    }
                    if (pos < templateStr.length() && templateStr.charAt(pos) == '=') {
                        pos++;
                    }
                }
                regexAllowed = true;
                continue;
            }

            pos++;
            regexAllowed = false;
        }

        throw new JSSyntaxErrorException("Unterminated template expression");
    }

    private String normalizeTemplateLineTerminators(String str) {
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

    Expression parseArrayExpression() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.LBRACKET);

        List<Expression> elements = new ArrayList<>();

        if (!ctx.match(TokenType.RBRACKET)) {
            do {
                if (ctx.match(TokenType.COMMA)) {
                    ctx.advance();
                    elements.add(null); // hole in array
                } else if (ctx.match(TokenType.ELLIPSIS)) {
                    // Spread element: ...expr
                    SourceLocation spreadLocation = ctx.getLocation();
                    ctx.advance(); // consume ELLIPSIS
                    Expression argument = delegates.expressions.parseAssignmentExpression();
                    elements.add(new SpreadElement(argument, spreadLocation));
                    if (ctx.match(TokenType.COMMA)) {
                        ctx.advance();
                    }
                } else {
                    elements.add(delegates.expressions.parseAssignmentExpression());
                    if (ctx.match(TokenType.COMMA)) {
                        ctx.advance();
                    }
                }
            } while (!ctx.match(TokenType.RBRACKET) && !ctx.match(TokenType.EOF));
        }

        ctx.expect(TokenType.RBRACKET);
        return new ArrayExpression(elements, location);
    }

    Expression parseObjectExpression() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.LBRACE);

        List<ObjectExpression.Property> properties = new ArrayList<>();

        while (!ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
            boolean isAsync = false;
            boolean isGenerator = false;

            // Check for 'async' modifier (only if NOT followed by colon or comma)
            if (ctx.match(TokenType.ASYNC)) {
                // Peek ahead to determine if this is a modifier or property name
                if (ctx.nextToken.type() == TokenType.COLON || ctx.nextToken.type() == TokenType.COMMA) {
                    // { async: value } or { async, ... } - async is property name
                    // Don't advance, let parsePropertyName handle it
                } else {
                    // async is likely a modifier for method
                    isAsync = true;
                    ctx.advance();
                }
            }

            // Check for generator *
            if (ctx.match(TokenType.MUL)) {
                isGenerator = true;
                ctx.advance();
            }

            // Check for getter/setter: get name() {} or set name(v) {}
            // Similar to parseClassElement logic
            if (!isAsync && !isGenerator && ctx.match(TokenType.IDENTIFIER)) {
                String name = ctx.currentToken.value();
                if (("get".equals(name) || "set".equals(name)) &&
                        ctx.nextToken.type() != TokenType.COLON &&
                        ctx.nextToken.type() != TokenType.COMMA &&
                        ctx.nextToken.type() != TokenType.LPAREN &&
                        ctx.nextToken.type() != TokenType.RBRACE) {
                    String kind = name;
                    ctx.advance(); // consume 'get' or 'set'

                    // Parse property name after get/set (may be computed)
                    boolean computed = ctx.match(TokenType.LBRACKET);
                    Expression key = delegates.expressions.parsePropertyName();

                    FunctionExpression value = delegates.functions.parseMethod(kind);
                    properties.add(new ObjectExpression.Property(key, value, kind, computed, false));

                    if (ctx.match(TokenType.COMMA)) {
                        ctx.advance();
                    } else {
                        break;
                    }
                    continue;
                }
            }

            // Parse property name (can be identifier, string, number, or computed [expr])
            boolean computed = ctx.match(TokenType.LBRACKET);
            Expression key = delegates.expressions.parsePropertyName();

            // Determine if this is a method or regular property
            if (ctx.match(TokenType.LPAREN)) {
                // Method shorthand: name() {} or async name() {} or *name() {} or async *name() {}
                SourceLocation funcLocation = ctx.getLocation();
                ctx.enterFunctionContext(isAsync);
                Expression value;
                try {
                    ctx.expect(TokenType.LPAREN);
                    FunctionParams funcParams = delegates.functions.parseFunctionParameters();
                    BlockStatement body = delegates.statements.parseBlockStatement();
                    value = new FunctionExpression(null, funcParams.params(), funcParams.defaults(), funcParams.restParameter(), body, isAsync, isGenerator, funcLocation);
                } finally {
                    ctx.exitFunctionContext(isAsync);
                }
                properties.add(new ObjectExpression.Property(key, value, "init", computed, false));
            } else if (ctx.match(TokenType.COLON)) {
                // Regular property: key: value
                ctx.advance();
                Expression value = delegates.expressions.parseAssignmentExpression();
                properties.add(new ObjectExpression.Property(key, value, "init", computed, false));
            } else if (!computed && key instanceof Identifier keyId
                    && (ctx.match(TokenType.COMMA) || ctx.match(TokenType.RBRACE) || ctx.match(TokenType.ASSIGN))) {
                // Shorthand property: {x} or CoverInitializedName: {x = defaultExpr}
                Expression value;
                if (ctx.match(TokenType.ASSIGN)) {
                    ctx.advance();
                    Expression defaultValue = delegates.expressions.parseAssignmentExpression();
                    value = new AssignmentExpression(keyId,
                            AssignmentExpression.AssignmentOperator.ASSIGN,
                            defaultValue, keyId.getLocation());
                } else {
                    value = keyId;
                }
                properties.add(new ObjectExpression.Property(key, value, "init", false, true));
            } else {
                // Fallback: expect colon
                ctx.expect(TokenType.COLON);
                Expression value = delegates.expressions.parseAssignmentExpression();
                properties.add(new ObjectExpression.Property(key, value, "init", computed, false));
            }

            if (ctx.match(TokenType.COMMA)) {
                ctx.advance();
            } else {
                break;
            }
        }

        ctx.expect(TokenType.RBRACE);
        return new ObjectExpression(properties, location);
    }

    Expression parseTemplateExpression(String expressionSource) {
        if (expressionSource.isBlank()) {
            throw new JSSyntaxErrorException("Empty template expression");
        }

        Lexer expressionLexer = new Lexer(expressionSource);
        expressionLexer.setStrictMode(ctx.strictMode);
        Parser expressionParser = new Parser(
                expressionLexer,
                ctx.moduleMode,
                ctx.isEval,
                ctx.functionNesting,
                ctx.asyncFunctionNesting);
        Expression expression = expressionParser.parseExpression();
        if (expressionParser.currentToken().type() != TokenType.EOF) {
            throw new JSSyntaxErrorException("Invalid template expression");
        }
        return expression;
    }

    TemplateLiteral parseTemplateLiteral(boolean tagged) {
        SourceLocation location = ctx.getLocation();
        String templateStr = ctx.currentToken.value();
        ctx.advance();

        List<String> quasis = new ArrayList<>();
        List<String> rawQuasis = new ArrayList<>();
        List<Expression> expressions = new ArrayList<>();

        int quasiStart = 0;
        int pos = 0;
        while (pos < templateStr.length()) {
            char c = templateStr.charAt(pos);
            if (c == '\\' && pos + 1 < templateStr.length()) {
                // Skip escaped character so \${ does not trigger interpolation.
                pos += 2;
                continue;
            }
            if (c == '$' && pos + 1 < templateStr.length() && templateStr.charAt(pos + 1) == '{') {
                String rawQuasi = normalizeTemplateLineTerminators(templateStr.substring(quasiStart, pos));
                rawQuasis.add(rawQuasi);
                quasis.add(processTemplateEscapeSequences(rawQuasi, tagged));

                int exprStart = pos + 2;
                int exprEnd = findTemplateExpressionEnd(templateStr, exprStart);
                String expressionSource = templateStr.substring(exprStart, exprEnd);
                expressions.add(parseTemplateExpression(expressionSource));

                pos = exprEnd + 1; // skip closing }
                quasiStart = pos;
            } else {
                pos++;
            }
        }

        String rawQuasi = normalizeTemplateLineTerminators(templateStr.substring(quasiStart));
        rawQuasis.add(rawQuasi);
        quasis.add(processTemplateEscapeSequences(rawQuasi, tagged));
        return new TemplateLiteral(quasis, rawQuasis, expressions, location);
    }

    private String processTemplateEscapeSequences(String str, boolean tagged) {
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
                    if (i + 3 >= str.length()
                            || Character.digit(str.charAt(i + 2), 16) < 0
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
                        int codePoint;
                        try {
                            codePoint = Integer.parseInt(str.substring(i + 3, end), 16);
                        } catch (NumberFormatException e) {
                            if (tagged) {
                                return null;
                            }
                            throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
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

    private int skipBlockComment(String source, int pos) {
        while (pos + 1 < source.length()) {
            if (source.charAt(pos) == '*' && source.charAt(pos + 1) == '/') {
                return pos + 2;
            }
            pos++;
        }
        throw new JSSyntaxErrorException("Unterminated block comment in template expression");
    }

    private int skipLineComment(String source, int pos) {
        while (pos < source.length() && source.charAt(pos) != '\n' && source.charAt(pos) != '\r') {
            pos++;
        }
        return pos;
    }

    private int skipNestedTemplateLiteral(String source, int backtickPos) {
        int pos = backtickPos + 1;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\\') {
                pos = Math.min(pos + 2, source.length());
                continue;
            }
            if (c == '`') {
                return pos + 1;
            }
            if (c == '$' && pos + 1 < source.length() && source.charAt(pos + 1) == '{') {
                int exprEnd = findTemplateExpressionEnd(source, pos + 2);
                pos = exprEnd + 1;
                continue;
            }
            pos++;
        }
        throw new JSSyntaxErrorException("Unterminated nested template literal");
    }

    private int skipNumberLiteral(String source, int pos) {
        int start = pos;
        if (source.charAt(pos) == '0' && pos + 1 < source.length()) {
            char prefix = source.charAt(pos + 1);
            if (prefix == 'x' || prefix == 'X' || prefix == 'b' || prefix == 'B' || prefix == 'o' || prefix == 'O') {
                pos += 2;
                while (pos < source.length() && ctx.isIdentifierPartChar(source.charAt(pos))) {
                    pos++;
                }
                return pos;
            }
        }

        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            pos++;
        }
        if (pos < source.length() && source.charAt(pos) == '.') {
            pos++;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
        }
        if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
            int expPos = pos + 1;
            if (expPos < source.length() && (source.charAt(expPos) == '+' || source.charAt(expPos) == '-')) {
                expPos++;
            }
            while (expPos < source.length() && Character.isDigit(source.charAt(expPos))) {
                expPos++;
            }
            pos = expPos;
        }
        if (pos < source.length() && source.charAt(pos) == 'n') {
            pos++;
        }
        return pos == start ? start + 1 : pos;
    }

    private int skipQuotedString(String source, int quotePos, char quote) {
        int pos = quotePos + 1;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\\') {
                pos = Math.min(pos + 2, source.length());
                continue;
            }
            if (c == quote) {
                return pos + 1;
            }
            if (c == '\n' || c == '\r') {
                throw new JSSyntaxErrorException("Unterminated string in template expression");
            }
            pos++;
        }
        throw new JSSyntaxErrorException("Unterminated string in template expression");
    }

    private int skipRegexLiteral(String source, int slashPos) {
        int pos = slashPos + 1;
        boolean inCharacterClass = false;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\\') {
                pos = Math.min(pos + 2, source.length());
                continue;
            }
            if (c == '[') {
                inCharacterClass = true;
                pos++;
                continue;
            }
            if (c == ']' && inCharacterClass) {
                inCharacterClass = false;
                pos++;
                continue;
            }
            if (c == '/' && !inCharacterClass) {
                pos++;
                while (pos < source.length() && ctx.isIdentifierPartChar(source.charAt(pos))) {
                    pos++;
                }
                return pos;
            }
            if (c == '\n' || c == '\r') {
                throw new JSSyntaxErrorException("Unterminated regex literal in template expression");
            }
            pos++;
        }
        throw new JSSyntaxErrorException("Unterminated regex literal in template expression");
    }
}
