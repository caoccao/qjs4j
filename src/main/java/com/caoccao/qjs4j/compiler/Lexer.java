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

package com.caoccao.qjs4j.compiler;

import com.caoccao.qjs4j.unicode.UnicodeData;

import java.util.HashMap;
import java.util.Map;

/**
 * Lexical analyzer for JavaScript source code.
 * Converts source text into a stream of tokens.
 *
 * Implements ECMAScript lexical grammar including:
 * - Number literals (decimal, hex, binary, octal, scientific)
 * - String literals with escape sequences
 * - Identifiers and keywords
 * - Operators and punctuation
 * - Comments (single-line, multi-line)
 * - Template literals (basic support)
 */
public final class Lexer {
    private final String source;
    private int position;
    private int line;
    private int column;
    private Token lookahead;

    // Keyword mapping
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("async", TokenType.ASYNC);
        KEYWORDS.put("await", TokenType.AWAIT);
        KEYWORDS.put("break", TokenType.BREAK);
        KEYWORDS.put("case", TokenType.CASE);
        KEYWORDS.put("catch", TokenType.CATCH);
        KEYWORDS.put("class", TokenType.CLASS);
        KEYWORDS.put("const", TokenType.CONST);
        KEYWORDS.put("continue", TokenType.CONTINUE);
        KEYWORDS.put("default", TokenType.DEFAULT);
        KEYWORDS.put("delete", TokenType.DELETE);
        KEYWORDS.put("do", TokenType.DO);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("export", TokenType.EXPORT);
        KEYWORDS.put("extends", TokenType.EXTENDS);
        KEYWORDS.put("finally", TokenType.FINALLY);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("from", TokenType.FROM);
        KEYWORDS.put("function", TokenType.FUNCTION);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("import", TokenType.IMPORT);
        KEYWORDS.put("in", TokenType.IN);
        KEYWORDS.put("instanceof", TokenType.INSTANCEOF);
        KEYWORDS.put("let", TokenType.LET);
        KEYWORDS.put("new", TokenType.NEW);
        KEYWORDS.put("of", TokenType.OF);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("super", TokenType.SUPER);
        KEYWORDS.put("switch", TokenType.SWITCH);
        KEYWORDS.put("this", TokenType.THIS);
        KEYWORDS.put("throw", TokenType.THROW);
        KEYWORDS.put("try", TokenType.TRY);
        KEYWORDS.put("typeof", TokenType.TYPEOF);
        KEYWORDS.put("var", TokenType.VAR);
        KEYWORDS.put("void", TokenType.VOID);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("yield", TokenType.YIELD);
        KEYWORDS.put("as", TokenType.AS);
    }

    public Lexer(String source) {
        this.source = source;
        this.position = 0;
        this.line = 1;
        this.column = 1;
        this.lookahead = null;
    }

    /**
     * Get the next token from the source.
     */
    public Token nextToken() {
        if (lookahead != null) {
            Token token = lookahead;
            lookahead = null;
            return token;
        }
        return scanToken();
    }

    /**
     * Peek at the next token without consuming it.
     */
    public Token peekToken() {
        if (lookahead == null) {
            lookahead = scanToken();
        }
        return lookahead;
    }

    /**
     * Reset the lexer to the beginning of the source.
     */
    public void reset() {
        position = 0;
        line = 1;
        column = 1;
        lookahead = null;
    }

    // Core scanning logic

    private Token scanToken() {
        skipWhitespaceAndComments();

        if (isAtEnd()) {
            return makeToken(TokenType.EOF, "");
        }

        int startPos = position;
        int startLine = line;
        int startColumn = column;

        char c = advance();

        // Identifiers and keywords
        if (isIdentifierStart(c)) {
            return scanIdentifier(startPos, startLine, startColumn);
        }

        // Numbers
        if (Character.isDigit(c)) {
            return scanNumber(startPos, startLine, startColumn);
        }

        // Strings
        if (c == '"' || c == '\'') {
            return scanString(c, startPos, startLine, startColumn);
        }

        // Template literals
        if (c == '`') {
            return scanTemplate(startPos, startLine, startColumn);
        }

        // Operators and punctuation
        return scanOperatorOrPunctuation(c, startPos, startLine, startColumn);
    }

    private Token scanIdentifier(int startPos, int startLine, int startColumn) {
        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance();
        }

        String value = source.substring(startPos, position);

        // Check if it's a keyword
        TokenType type = KEYWORDS.getOrDefault(value, TokenType.IDENTIFIER);

        return new Token(type, value, startLine, startColumn, startPos);
    }

    private Token scanNumber(int startPos, int startLine, int startColumn) {
        // Handle special prefixes (0x, 0b, 0o)
        if (source.charAt(startPos) == '0' && position < source.length()) {
            char next = peek();
            if (next == 'x' || next == 'X') {
                advance(); // consume 'x'
                return scanHexNumber(startPos, startLine, startColumn);
            } else if (next == 'b' || next == 'B') {
                advance(); // consume 'b'
                return scanBinaryNumber(startPos, startLine, startColumn);
            } else if (next == 'o' || next == 'O') {
                advance(); // consume 'o'
                return scanOctalNumber(startPos, startLine, startColumn);
            }
        }

        // Scan decimal number
        while (!isAtEnd() && Character.isDigit(peek())) {
            advance();
        }

        // Check for decimal point
        if (!isAtEnd() && peek() == '.' && position + 1 < source.length() &&
            Character.isDigit(source.charAt(position + 1))) {
            advance(); // consume '.'
            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }
        }

        // Check for exponent
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            advance(); // consume 'e'
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                advance(); // consume sign
            }
            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }
        }

        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    private Token scanHexNumber(int startPos, int startLine, int startColumn) {
        while (!isAtEnd() && isHexDigit(peek())) {
            advance();
        }
        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    private Token scanBinaryNumber(int startPos, int startLine, int startColumn) {
        while (!isAtEnd() && (peek() == '0' || peek() == '1')) {
            advance();
        }
        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    private Token scanOctalNumber(int startPos, int startLine, int startColumn) {
        while (!isAtEnd() && peek() >= '0' && peek() <= '7') {
            advance();
        }
        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    private Token scanString(char quote, int startPos, int startLine, int startColumn) {
        StringBuilder value = new StringBuilder();

        while (!isAtEnd() && peek() != quote) {
            if (peek() == '\n') {
                // Unterminated string
                break;
            }

            if (peek() == '\\') {
                advance(); // consume backslash
                if (!isAtEnd()) {
                    char escaped = advance();
                    switch (escaped) {
                        case 'n' -> value.append('\n');
                        case 't' -> value.append('\t');
                        case 'r' -> value.append('\r');
                        case '\\' -> value.append('\\');
                        case '\'' -> value.append('\'');
                        case '"' -> value.append('"');
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'v' -> value.append('\u000B');
                        case '0' -> value.append('\0');
                        case 'x' -> {
                            // Hex escape \xHH
                            if (position + 1 < source.length()) {
                                String hex = source.substring(position, position + 2);
                                try {
                                    value.append((char) Integer.parseInt(hex, 16));
                                    position += 2;
                                    column += 2;
                                } catch (NumberFormatException e) {
                                    value.append('x');
                                }
                            }
                        }
                        case 'u' -> {
                            // Unicode escape: u+HHHH or u+{...}
                            if (!isAtEnd() && peek() == '{') {
                                advance(); // consume '{'
                                StringBuilder codePoint = new StringBuilder();
                                while (!isAtEnd() && peek() != '}') {
                                    codePoint.append(advance());
                                }
                                if (!isAtEnd()) advance(); // consume '}'
                                try {
                                    int cp = Integer.parseInt(codePoint.toString(), 16);
                                    value.appendCodePoint(cp);
                                } catch (NumberFormatException e) {
                                    value.append("u{").append(codePoint).append("}");
                                }
                            } else if (position + 3 < source.length()) {
                                String hex = source.substring(position, position + 4);
                                try {
                                    value.append((char) Integer.parseInt(hex, 16));
                                    position += 4;
                                    column += 4;
                                } catch (NumberFormatException e) {
                                    value.append('u');
                                }
                            }
                        }
                        default -> value.append(escaped);
                    }
                }
            } else {
                value.append(advance());
            }
        }

        if (!isAtEnd() && peek() == quote) {
            advance(); // consume closing quote
        }

        return new Token(TokenType.STRING, value.toString(), startLine, startColumn, startPos);
    }

    private Token scanTemplate(int startPos, int startLine, int startColumn) {
        StringBuilder value = new StringBuilder();

        while (!isAtEnd() && peek() != '`') {
            if (peek() == '\\') {
                advance();
                if (!isAtEnd()) {
                    value.append(advance());
                }
            } else if (peek() == '$' && position + 1 < source.length() && source.charAt(position + 1) == '{') {
                // Template interpolation - for now, just include it
                value.append(advance());
            } else {
                value.append(advance());
            }
        }

        if (!isAtEnd()) {
            advance(); // consume closing backtick
        }

        return new Token(TokenType.TEMPLATE, value.toString(), startLine, startColumn, startPos);
    }

    private Token scanOperatorOrPunctuation(char c, int startPos, int startLine, int startColumn) {
        TokenType type = switch (c) {
            case '(' -> TokenType.LPAREN;
            case ')' -> TokenType.RPAREN;
            case '{' -> TokenType.LBRACE;
            case '}' -> TokenType.RBRACE;
            case '[' -> TokenType.LBRACKET;
            case ']' -> TokenType.RBRACKET;
            case ';' -> TokenType.SEMICOLON;
            case ',' -> TokenType.COMMA;
            case '~' -> TokenType.BIT_NOT;
            case '?' -> {
                if (match('?')) {
                    yield TokenType.NULLISH_COALESCING;
                } else if (match('.')) {
                    yield TokenType.OPTIONAL_CHAINING;
                }
                yield TokenType.QUESTION;
            }
            case ':' -> TokenType.COLON;
            case '.' -> {
                if (match('.') && match('.')) {
                    yield TokenType.ELLIPSIS;
                }
                yield TokenType.DOT;
            }
            case '+' -> {
                if (match('+')) yield TokenType.INC;
                else if (match('=')) yield TokenType.PLUS_ASSIGN;
                else yield TokenType.PLUS;
            }
            case '-' -> {
                if (match('-')) yield TokenType.DEC;
                else if (match('=')) yield TokenType.MINUS_ASSIGN;
                else yield TokenType.MINUS;
            }
            case '*' -> {
                if (match('*')) {
                    if (match('=')) yield TokenType.EXP_ASSIGN;
                    else yield TokenType.EXP;
                } else if (match('=')) {
                    yield TokenType.MUL_ASSIGN;
                } else {
                    yield TokenType.MUL;
                }
            }
            case '/' -> {
                if (match('=')) yield TokenType.DIV_ASSIGN;
                else yield TokenType.DIV;
            }
            case '%' -> {
                if (match('=')) yield TokenType.MOD_ASSIGN;
                else yield TokenType.MOD;
            }
            case '&' -> {
                if (match('&')) {
                    if (match('=')) yield TokenType.AND_ASSIGN;
                    else yield TokenType.LOGICAL_AND;
                } else if (match('=')) {
                    yield TokenType.AND_ASSIGN;
                } else {
                    yield TokenType.BIT_AND;
                }
            }
            case '|' -> {
                if (match('|')) {
                    if (match('=')) yield TokenType.OR_ASSIGN;
                    else yield TokenType.LOGICAL_OR;
                } else if (match('=')) {
                    yield TokenType.OR_ASSIGN;
                } else {
                    yield TokenType.BIT_OR;
                }
            }
            case '^' -> {
                if (match('=')) yield TokenType.XOR_ASSIGN;
                else yield TokenType.BIT_XOR;
            }
            case '=' -> {
                if (match('=')) {
                    if (match('=')) yield TokenType.STRICT_EQ;
                    else yield TokenType.EQ;
                } else if (match('>')) {
                    yield TokenType.ARROW;
                } else {
                    yield TokenType.ASSIGN;
                }
            }
            case '!' -> {
                if (match('=')) {
                    if (match('=')) yield TokenType.STRICT_NE;
                    else yield TokenType.NE;
                } else {
                    yield TokenType.NOT;
                }
            }
            case '<' -> {
                if (match('<')) {
                    if (match('=')) yield TokenType.LSHIFT_ASSIGN;
                    else yield TokenType.LSHIFT;
                } else if (match('=')) {
                    yield TokenType.LE;
                } else {
                    yield TokenType.LT;
                }
            }
            case '>' -> {
                if (match('>')) {
                    if (match('>')) {
                        if (match('=')) yield TokenType.URSHIFT_ASSIGN;
                        else yield TokenType.URSHIFT;
                    } else if (match('=')) {
                        yield TokenType.RSHIFT_ASSIGN;
                    } else {
                        yield TokenType.RSHIFT;
                    }
                } else if (match('=')) {
                    yield TokenType.GE;
                } else {
                    yield TokenType.GT;
                }
            }
            default -> TokenType.EOF;
        };

        String value = source.substring(startPos, position);
        return new Token(type, value, startLine, startColumn, startPos);
    }

    // Character utilities

    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char c = peek();

            // Whitespace
            if (UnicodeData.isWhiteSpace(c)) {
                advance();
                continue;
            }

            // Line terminators
            if (c == '\n' || c == '\r') {
                if (c == '\r' && position + 1 < source.length() && source.charAt(position + 1) == '\n') {
                    advance(); // consume \r
                }
                advance(); // consume \n
                line++;
                column = 1;
                continue;
            }

            // Comments
            if (c == '/' && position + 1 < source.length()) {
                char next = source.charAt(position + 1);

                // Single-line comment
                if (next == '/') {
                    advance(); // consume first /
                    advance(); // consume second /
                    while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
                        advance();
                    }
                    continue;
                }

                // Multi-line comment
                if (next == '*') {
                    advance(); // consume /
                    advance(); // consume *
                    while (!isAtEnd()) {
                        if (peek() == '*' && position + 1 < source.length() && source.charAt(position + 1) == '/') {
                            advance(); // consume *
                            advance(); // consume /
                            break;
                        }
                        if (peek() == '\n') {
                            line++;
                            column = 0;
                        }
                        advance();
                    }
                    continue;
                }
            }

            break;
        }
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$' ||
               UnicodeData.isIdentifierStart(c);
    }

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' ||
               UnicodeData.isIdentifierPart(c);
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(position);
    }

    private char advance() {
        char c = source.charAt(position);
        position++;
        column++;
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(position) != expected) return false;
        advance();
        return true;
    }

    private boolean isAtEnd() {
        return position >= source.length();
    }

    private Token makeToken(TokenType type, String value) {
        return new Token(type, value, line, column, position);
    }
}
