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

package com.caoccao.qjs4j.compilation.lexer;

import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

final class LexerTemplateScanner {
    private final Lexer lexer;

    LexerTemplateScanner(Lexer lexer) {
        this.lexer = lexer;
    }

    Token scanTemplate(int startPos, int startLine, int startColumn) {
        StringBuilder value = new StringBuilder();
        boolean terminated = false;

        while (!lexer.isAtEnd()) {
            char c = lexer.peek();
            if (c == '`') {
                lexer.advance();
                terminated = true;
                break;
            }
            if (c == '\\') {
                value.append(lexer.advance());
                if (!lexer.isAtEnd()) {
                    value.append(lexer.advance());
                }
                continue;
            }
            if (c == '$' && lexer.position + 1 < lexer.source.length() && lexer.source.charAt(lexer.position + 1) == '{') {
                value.append(lexer.advance());
                value.append(lexer.advance());
                scanTemplateExpression(value);
                continue;
            }
            value.append(lexer.advance());
        }

        if (!terminated) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }

        return new Token(TokenType.TEMPLATE, value.toString(), startLine, startColumn, startPos);
    }

    private void scanTemplateBlockComment(StringBuilder value) {
        value.append(lexer.advance());
        value.append(lexer.advance());
        while (!lexer.isAtEnd()) {
            char c = lexer.advance();
            value.append(c);
            if (c == '*' && !lexer.isAtEnd() && lexer.peek() == '/') {
                value.append(lexer.advance());
                return;
            }
        }
    }

    private void scanTemplateExpression(StringBuilder value) {
        int braceDepth = 1;
        boolean regexAllowed = true;
        while (!lexer.isAtEnd() && braceDepth > 0) {
            char c = lexer.peek();
            if (Character.isWhitespace(c)) {
                value.append(lexer.advance());
                continue;
            }
            if (c == '\'' || c == '"') {
                scanTemplateQuotedString(value, c);
                regexAllowed = false;
                continue;
            }
            if (c == '`') {
                scanTemplateNestedTemplate(value);
                regexAllowed = false;
                continue;
            }
            if (c == '/' && lexer.position + 1 < lexer.source.length()) {
                char next = lexer.source.charAt(lexer.position + 1);
                if (next == '/') {
                    scanTemplateLineComment(value);
                    regexAllowed = true;
                    continue;
                }
                if (next == '*') {
                    scanTemplateBlockComment(value);
                    regexAllowed = true;
                    continue;
                }
                if (regexAllowed) {
                    scanTemplateRegex(value);
                    regexAllowed = false;
                    continue;
                }
                value.append(lexer.advance());
                if (!lexer.isAtEnd() && lexer.peek() == '=') {
                    value.append(lexer.advance());
                }
                regexAllowed = true;
                continue;
            }

            if (c == '{') {
                value.append(lexer.advance());
                braceDepth++;
                regexAllowed = true;
                continue;
            }
            if (c == '}') {
                value.append(lexer.advance());
                braceDepth--;
                regexAllowed = false;
                continue;
            }
            if (c == '(' || c == '[' || c == ',' || c == ';' || c == ':') {
                value.append(lexer.advance());
                regexAllowed = true;
                continue;
            }
            if (c == ')' || c == ']') {
                value.append(lexer.advance());
                regexAllowed = false;
                continue;
            }

            if (c == '.') {
                if (lexer.position + 2 < lexer.source.length()
                        && lexer.source.charAt(lexer.position + 1) == '.'
                        && lexer.source.charAt(lexer.position + 2) == '.') {
                    value.append(lexer.advance());
                    value.append(lexer.advance());
                    value.append(lexer.advance());
                    regexAllowed = true;
                } else if (lexer.position + 1 < lexer.source.length() && Character.isDigit(lexer.source.charAt(lexer.position + 1))) {
                    scanTemplateNumber(value);
                    regexAllowed = false;
                } else {
                    value.append(lexer.advance());
                    regexAllowed = false;
                }
                continue;
            }

            if (lexer.isIdentifierStart(c)) {
                String identifier = scanTemplateIdentifier(value);
                regexAllowed = switch (identifier) {
                    case "return", "throw", "case", "delete", "void", "typeof",
                         "instanceof", "in", "of", "new", "do", "else", "yield", "await" -> true;
                    default -> false;
                };
                continue;
            }

            if (Character.isDigit(c)) {
                scanTemplateNumber(value);
                regexAllowed = false;
                continue;
            }

            if ("+-*%&|^!~<>=?".indexOf(c) >= 0) {
                value.append(lexer.advance());
                if (!lexer.isAtEnd()) {
                    char next = lexer.peek();
                    if (next == '=' || (next == c && "&|+-<>?".indexOf(c) >= 0)) {
                        value.append(lexer.advance());
                    } else if (c == '=' && next == '>') {
                        value.append(lexer.advance());
                    }
                }
                if (c == '>' && !lexer.isAtEnd() && lexer.peek() == '>') {
                    value.append(lexer.advance());
                    if (!lexer.isAtEnd() && lexer.peek() == '>') {
                        value.append(lexer.advance());
                    }
                    if (!lexer.isAtEnd() && lexer.peek() == '=') {
                        value.append(lexer.advance());
                    }
                }
                regexAllowed = true;
                continue;
            }

            value.append(lexer.advance());
            regexAllowed = false;
        }
        if (braceDepth != 0) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
    }

    private String scanTemplateIdentifier(StringBuilder value) {
        int start = lexer.position;
        value.append(lexer.advance());
        while (!lexer.isAtEnd() && lexer.isIdentifierPart(lexer.peek())) {
            value.append(lexer.advance());
        }
        return lexer.source.substring(start, lexer.position);
    }

    private void scanTemplateLineComment(StringBuilder value) {
        value.append(lexer.advance());
        value.append(lexer.advance());
        while (!lexer.isAtEnd()) {
            char c = lexer.advance();
            value.append(c);
            if (c == '\n' || c == '\r') {
                return;
            }
        }
    }

    private void scanTemplateNestedTemplate(StringBuilder value) {
        value.append(lexer.advance());
        while (!lexer.isAtEnd()) {
            char c = lexer.peek();
            if (c == '`') {
                value.append(lexer.advance());
                return;
            }
            if (c == '\\') {
                value.append(lexer.advance());
                if (!lexer.isAtEnd()) {
                    value.append(lexer.advance());
                }
                continue;
            }
            if (c == '$' && lexer.position + 1 < lexer.source.length() && lexer.source.charAt(lexer.position + 1) == '{') {
                value.append(lexer.advance());
                value.append(lexer.advance());
                scanTemplateExpression(value);
                continue;
            }
            value.append(lexer.advance());
        }
    }

    private void scanTemplateNumber(StringBuilder value) {
        if (lexer.peek() == '0' && lexer.position + 1 < lexer.source.length()) {
            char prefix = lexer.source.charAt(lexer.position + 1);
            if (prefix == 'x' || prefix == 'X' || prefix == 'b' || prefix == 'B' || prefix == 'o' || prefix == 'O') {
                value.append(lexer.advance());
                value.append(lexer.advance());
                while (!lexer.isAtEnd() && lexer.isIdentifierPart(lexer.peek())) {
                    value.append(lexer.advance());
                }
                return;
            }
        }

        while (!lexer.isAtEnd() && Character.isDigit(lexer.peek())) {
            value.append(lexer.advance());
        }
        if (!lexer.isAtEnd() && lexer.peek() == '.') {
            value.append(lexer.advance());
            while (!lexer.isAtEnd() && Character.isDigit(lexer.peek())) {
                value.append(lexer.advance());
            }
        }
        if (!lexer.isAtEnd() && (lexer.peek() == 'e' || lexer.peek() == 'E')) {
            value.append(lexer.advance());
            if (!lexer.isAtEnd() && (lexer.peek() == '+' || lexer.peek() == '-')) {
                value.append(lexer.advance());
            }
            while (!lexer.isAtEnd() && Character.isDigit(lexer.peek())) {
                value.append(lexer.advance());
            }
        }
        if (!lexer.isAtEnd() && lexer.peek() == 'n') {
            value.append(lexer.advance());
        }
    }

    private void scanTemplateQuotedString(StringBuilder value, char quote) {
        value.append(lexer.advance());
        while (!lexer.isAtEnd()) {
            char c = lexer.peek();
            if (c == '\\') {
                value.append(lexer.advance());
                if (!lexer.isAtEnd()) {
                    value.append(lexer.advance());
                }
                continue;
            }
            value.append(lexer.advance());
            if (c == quote) {
                return;
            }
        }
    }

    private void scanTemplateRegex(StringBuilder value) {
        value.append(lexer.advance());
        boolean inClass = false;
        while (!lexer.isAtEnd()) {
            char c = lexer.advance();
            value.append(c);
            if (c == '\\') {
                if (!lexer.isAtEnd()) {
                    value.append(lexer.advance());
                }
                continue;
            }
            if (c == '[') {
                inClass = true;
                continue;
            }
            if (c == ']' && inClass) {
                inClass = false;
                continue;
            }
            if (c == '/' && !inClass) {
                break;
            }
            if (c == '\n' || c == '\r') {
                return;
            }
        }
        while (!lexer.isAtEnd() && lexer.isIdentifierPart(lexer.peek())) {
            value.append(lexer.advance());
        }
    }
}
