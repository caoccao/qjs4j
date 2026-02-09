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

import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.unicode.UnicodeData;

import java.util.HashMap;
import java.util.Map;

/**
 * Lexical analyzer for JavaScript source code.
 * Converts source text into a stream of tokens.
 * <p>
 * Implements ECMAScript lexical grammar including:
 * - Number literals (decimal, hex, binary, octal, scientific)
 * - String literals with escape sequences
 * - Identifiers and keywords
 * - Operators and punctuation
 * - Comments (single-line, multi-line)
 * - Template literals (basic support)
 */
public final class Lexer {
    // Keyword mapping
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("as", TokenType.AS);
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
        KEYWORDS.put("false", TokenType.FALSE);
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
        KEYWORDS.put("null", TokenType.NULL);
        KEYWORDS.put("of", TokenType.OF);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("super", TokenType.SUPER);
        KEYWORDS.put("switch", TokenType.SWITCH);
        KEYWORDS.put("this", TokenType.THIS);
        KEYWORDS.put("throw", TokenType.THROW);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("try", TokenType.TRY);
        KEYWORDS.put("typeof", TokenType.TYPEOF);
        KEYWORDS.put("var", TokenType.VAR);
        KEYWORDS.put("void", TokenType.VOID);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("yield", TokenType.YIELD);
    }

    private final String source;
    private int column;
    private TokenType lastTokenType;
    private int line;
    private Token lookahead;
    private int position;

    public Lexer(String source) {
        this.source = source;
        this.position = 0;
        this.line = 1;
        this.column = 1;
        this.lookahead = null;
        this.lastTokenType = null;
    }

    private char advance() {
        char c = source.charAt(position);
        position++;
        column++;
        return c;
    }

    /**
     * Determine if the current context expects a regex literal.
     * Regex can appear after operators, keywords, or at the start of an expression.
     */
    private boolean expectRegex() {
        if (lastTokenType == null) {
            return true; // Start of input
        }

        return switch (lastTokenType) {
            // After operators
            case ASSIGN, EQ, NE, STRICT_EQ, STRICT_NE, LT, LE, GT, GE,
                 PLUS, MINUS, MUL, DIV, MOD, EXP,
                 BIT_AND, BIT_OR, BIT_XOR, BIT_NOT,
                 LOGICAL_AND, LOGICAL_OR, NOT,
                 LSHIFT, RSHIFT, URSHIFT,
                 PLUS_ASSIGN, MINUS_ASSIGN, MUL_ASSIGN, DIV_ASSIGN, MOD_ASSIGN,
                 EXP_ASSIGN, AND_ASSIGN, OR_ASSIGN, XOR_ASSIGN,
                 LSHIFT_ASSIGN, RSHIFT_ASSIGN, URSHIFT_ASSIGN,
                 NULLISH_COALESCING,
                 // After punctuation
                 LPAREN, LBRACKET, LBRACE, COMMA, SEMICOLON, COLON, QUESTION,
                 ARROW,
                 // After keywords that start expressions
                 RETURN, THROW, TYPEOF, VOID, DELETE, NEW,
                 IF, WHILE, FOR, CASE -> true;
            default -> false;
        };
    }

    private boolean isAtEnd() {
        return position >= source.length();
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    // Core scanning logic

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' ||
                UnicodeData.isIdentifierPart(c);
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$' ||
                UnicodeData.isIdentifierStart(c);
    }

    private Token makeToken(TokenType type, String value) {
        return new Token(type, value, line, column, position);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(position) != expected) return false;
        advance();
        return true;
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

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(position);
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
        lastTokenType = null;
    }

    private Token scanBinaryNumber(int startPos, int startLine, int startColumn) {
        int digitStart = position;
        while (!isAtEnd() && (peek() == '0' || peek() == '1')) {
            advance();
        }
        // Validate at least one binary digit was scanned
        if (position == digitStart) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        // Validate no invalid digits, decimal point, or exponent follow
        if (!isAtEnd() && (Character.isDigit(peek()) || peek() == '.' || peek() == 'e' || peek() == 'E')) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        // Check for BigInt literal suffix 'n'
        if (!isAtEnd() && peek() == 'n') {
            advance(); // consume 'n'
            String value = source.substring(startPos, position - 1); // exclude 'n' from value
            return new Token(TokenType.BIGINT, value, startLine, startColumn, startPos);
        }
        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    // Character utilities

    private Token scanHexNumber(int startPos, int startLine, int startColumn) {
        int digitStart = position;
        while (!isAtEnd() && isHexDigit(peek())) {
            advance();
        }
        // Validate at least one hex digit was scanned
        if (position == digitStart) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        // Validate no invalid characters or decimal point follow (e.g., 'g' in '0xFFg' or '.' in '0xFF.')
        if (!isAtEnd() && ((Character.isLetterOrDigit(peek()) && peek() != 'n') || peek() == '.')) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        // Check for BigInt literal suffix 'n'
        if (!isAtEnd() && peek() == 'n') {
            advance(); // consume 'n'
            String value = source.substring(startPos, position - 1); // exclude 'n' from value
            return new Token(TokenType.BIGINT, value, startLine, startColumn, startPos);
        }
        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
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
        boolean hasDecimalPoint = false;
        boolean hasExponent = false;

        while (!isAtEnd() && Character.isDigit(peek())) {
            advance();
        }

        // Check for decimal point
        if (!isAtEnd() && peek() == '.' && position + 1 < source.length() &&
                Character.isDigit(source.charAt(position + 1))) {
            hasDecimalPoint = true;
            advance(); // consume '.'
            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }
        }

        // Check for exponent
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            hasExponent = true;
            advance(); // consume 'e'
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                advance(); // consume sign
            }
            int exponentStart = position;
            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }
            // Validate at least one digit in exponent
            if (position == exponentStart) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
        }

        // Check for invalid identifier characters after number (e.g., '1abc')
        if (!isAtEnd() && isIdentifierStart(peek()) && peek() != 'n') {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }

        // Check for BigInt literal suffix 'n'
        if (!isAtEnd() && peek() == 'n') {
            // BigInt literals cannot have decimal points or exponents
            if (hasDecimalPoint || hasExponent) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            advance(); // consume 'n'
            String value = source.substring(startPos, position - 1); // exclude 'n' from value
            return new Token(TokenType.BIGINT, value, startLine, startColumn, startPos);
        }

        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    private Token scanOctalNumber(int startPos, int startLine, int startColumn) {
        int digitStart = position;
        while (!isAtEnd() && peek() >= '0' && peek() <= '7') {
            advance();
        }
        // Validate at least one octal digit was scanned
        if (position == digitStart) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        // Validate no invalid digits, decimal point, or exponent follow (e.g., '8' or '9' in '0o78', or '.' in '0o7.' or 'e' in '0o7e1')
        if (!isAtEnd() && (Character.isDigit(peek()) || peek() == '.' || peek() == 'e' || peek() == 'E')) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        // Check for BigInt literal suffix 'n'
        if (!isAtEnd() && peek() == 'n') {
            advance(); // consume 'n'
            String value = source.substring(startPos, position - 1); // exclude 'n' from value
            return new Token(TokenType.BIGINT, value, startLine, startColumn, startPos);
        }
        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    private Token scanOperatorOrPunctuation(char c, int startPos, int startLine, int startColumn) {
        // Special case: check for regex literal when encountering '/'
        if (c == '/' && expectRegex()) {
            // Back up one position and scan as regex
            position = startPos;
            column = startColumn;
            return scanRegex(startPos, startLine, startColumn);
        }

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
                    if (match('=')) {
                        yield TokenType.NULLISH_ASSIGN;
                    } else {
                        yield TokenType.NULLISH_COALESCING;
                    }
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
                    if (match('=')) yield TokenType.LOGICAL_AND_ASSIGN;
                    else yield TokenType.LOGICAL_AND;
                } else if (match('=')) {
                    yield TokenType.AND_ASSIGN;
                } else {
                    yield TokenType.BIT_AND;
                }
            }
            case '|' -> {
                if (match('|')) {
                    if (match('=')) yield TokenType.LOGICAL_OR_ASSIGN;
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

    private Token scanRegex(int startPos, int startLine, int startColumn) {
        advance(); // consume opening /

        StringBuilder pattern = new StringBuilder();
        boolean inCharClass = false;

        while (!isAtEnd()) {
            char c = peek();

            // End of regex
            if (c == '/' && !inCharClass) {
                advance(); // consume closing /
                break;
            }

            // Track character classes [...]
            if (c == '[' && !inCharClass) {
                inCharClass = true;
            } else if (c == ']' && inCharClass) {
                inCharClass = false;
            }

            // Handle escape sequences
            if (c == '\\') {
                pattern.append(advance()); // append backslash
                if (!isAtEnd()) {
                    pattern.append(advance()); // append escaped character
                }
                continue;
            }

            // Newlines terminate regex
            if (c == '\n' || c == '\r') {
                break;
            }

            pattern.append(advance());
        }

        // Scan flags (g, i, m, s, u, y)
        StringBuilder flags = new StringBuilder();
        while (!isAtEnd() && isIdentifierPart(peek())) {
            flags.append(advance());
        }

        // Combine pattern and flags in the value
        String value = "/" + pattern + "/" + flags;
        return new Token(TokenType.REGEX, value, startLine, startColumn, startPos);
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

    private void scanTemplateBlockComment(StringBuilder value) {
        value.append(advance()); // /
        value.append(advance()); // *
        while (!isAtEnd()) {
            char c = advance();
            value.append(c);
            if (c == '*' && !isAtEnd() && peek() == '/') {
                value.append(advance());
                return;
            }
        }
    }

    private void scanTemplateExpression(StringBuilder value) {
        int braceDepth = 1;
        boolean regexAllowed = true;
        while (!isAtEnd() && braceDepth > 0) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                value.append(advance());
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
            if (c == '/' && position + 1 < source.length()) {
                char next = source.charAt(position + 1);
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
                value.append(advance());
                if (!isAtEnd() && peek() == '=') {
                    value.append(advance());
                }
                regexAllowed = true;
                continue;
            }

            if (c == '{') {
                value.append(advance());
                braceDepth++;
                regexAllowed = true;
                continue;
            }
            if (c == '}') {
                value.append(advance());
                braceDepth--;
                regexAllowed = false;
                continue;
            }
            if (c == '(' || c == '[' || c == ',' || c == ';' || c == ':') {
                value.append(advance());
                regexAllowed = true;
                continue;
            }
            if (c == ')' || c == ']') {
                value.append(advance());
                regexAllowed = false;
                continue;
            }

            if (c == '.') {
                if (position + 2 < source.length()
                        && source.charAt(position + 1) == '.'
                        && source.charAt(position + 2) == '.') {
                    value.append(advance());
                    value.append(advance());
                    value.append(advance());
                    regexAllowed = true;
                } else if (position + 1 < source.length() && Character.isDigit(source.charAt(position + 1))) {
                    scanTemplateNumber(value);
                    regexAllowed = false;
                } else {
                    value.append(advance());
                    regexAllowed = false;
                }
                continue;
            }

            if (isIdentifierStart(c)) {
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
                value.append(advance());
                if (!isAtEnd()) {
                    char next = peek();
                    if (next == '=' || (next == c && "&|+-<>?".indexOf(c) >= 0)) {
                        value.append(advance());
                    } else if (c == '=' && next == '>') {
                        value.append(advance());
                    }
                }
                if (c == '>' && !isAtEnd() && peek() == '>') {
                    value.append(advance());
                    if (!isAtEnd() && peek() == '>') {
                        value.append(advance());
                    }
                    if (!isAtEnd() && peek() == '=') {
                        value.append(advance());
                    }
                }
                regexAllowed = true;
                continue;
            }

            value.append(advance());
            regexAllowed = false;
        }
    }

    private void scanTemplateLineComment(StringBuilder value) {
        value.append(advance()); // /
        value.append(advance()); // /
        while (!isAtEnd()) {
            char c = advance();
            value.append(c);
            if (c == '\n' || c == '\r') {
                return;
            }
        }
    }

    private String scanTemplateIdentifier(StringBuilder value) {
        int start = position;
        value.append(advance());
        while (!isAtEnd() && isIdentifierPart(peek())) {
            value.append(advance());
        }
        return source.substring(start, position);
    }

    private void scanTemplateNestedTemplate(StringBuilder value) {
        value.append(advance()); // opening `
        while (!isAtEnd()) {
            char c = peek();
            if (c == '`') {
                value.append(advance());
                return;
            }
            if (c == '\\') {
                value.append(advance());
                if (!isAtEnd()) {
                    value.append(advance());
                }
                continue;
            }
            if (c == '$' && position + 1 < source.length() && source.charAt(position + 1) == '{') {
                value.append(advance());
                value.append(advance());
                scanTemplateExpression(value);
                continue;
            }
            value.append(advance());
        }
    }

    private void scanTemplateQuotedString(StringBuilder value, char quote) {
        value.append(advance()); // opening quote
        while (!isAtEnd()) {
            char c = peek();
            if (c == '\\') {
                value.append(advance());
                if (!isAtEnd()) {
                    value.append(advance());
                }
                continue;
            }
            value.append(advance());
            if (c == quote) {
                return;
            }
        }
    }

    private void scanTemplateRegex(StringBuilder value) {
        value.append(advance()); // opening '/'
        boolean inClass = false;
        while (!isAtEnd()) {
            char c = advance();
            value.append(c);
            if (c == '\\') {
                if (!isAtEnd()) {
                    value.append(advance());
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
        while (!isAtEnd() && isIdentifierPart(peek())) {
            value.append(advance());
        }
    }

    private void scanTemplateNumber(StringBuilder value) {
        if (peek() == '0' && position + 1 < source.length()) {
            char prefix = source.charAt(position + 1);
            if (prefix == 'x' || prefix == 'X' || prefix == 'b' || prefix == 'B' || prefix == 'o' || prefix == 'O') {
                value.append(advance());
                value.append(advance());
                while (!isAtEnd() && isIdentifierPart(peek())) {
                    value.append(advance());
                }
                return;
            }
        }

        while (!isAtEnd() && Character.isDigit(peek())) {
            value.append(advance());
        }
        if (!isAtEnd() && peek() == '.') {
            value.append(advance());
            while (!isAtEnd() && Character.isDigit(peek())) {
                value.append(advance());
            }
        }
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            value.append(advance());
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                value.append(advance());
            }
            while (!isAtEnd() && Character.isDigit(peek())) {
                value.append(advance());
            }
        }
        if (!isAtEnd() && peek() == 'n') {
            value.append(advance());
        }
    }

    private Token scanTemplate(int startPos, int startLine, int startColumn) {
        // Store the complete template literal including ${...} expressions.
        StringBuilder value = new StringBuilder();

        while (!isAtEnd()) {
            char c = peek();
            if (c == '`') {
                advance(); // consume closing backtick
                break;
            }
            if (c == '\\') {
                value.append(advance());
                if (!isAtEnd()) {
                    value.append(advance());
                }
                continue;
            }
            if (c == '$' && position + 1 < source.length() && source.charAt(position + 1) == '{') {
                value.append(advance()); // $
                value.append(advance()); // {
                scanTemplateExpression(value);
                continue;
            }
            value.append(advance());
        }

        return new Token(TokenType.TEMPLATE, value.toString(), startLine, startColumn, startPos);
    }

    private Token scanToken() {
        skipWhitespaceAndComments();

        if (isAtEnd()) {
            Token token = makeToken(TokenType.EOF, "");
            lastTokenType = TokenType.EOF;
            return token;
        }

        int startPos = position;
        int startLine = line;
        int startColumn = column;

        char c = advance();

        // Identifiers and keywords
        if (isIdentifierStart(c)) {
            Token token = scanIdentifier(startPos, startLine, startColumn);
            lastTokenType = token.type();
            return token;
        }

        // Numbers
        if (Character.isDigit(c)) {
            Token token = scanNumber(startPos, startLine, startColumn);
            lastTokenType = token.type();
            return token;
        }

        // Strings
        if (c == '"' || c == '\'') {
            Token token = scanString(c, startPos, startLine, startColumn);
            lastTokenType = token.type();
            return token;
        }

        // Template literals
        if (c == '`') {
            Token token = scanTemplate(startPos, startLine, startColumn);
            lastTokenType = token.type();
            return token;
        }

        // Private identifiers (#name)
        if (c == '#') {
            // Check if next character is an identifier start
            if (!isAtEnd() && isIdentifierStart(peek())) {
                // Scan the identifier part (without the #)
                int nameStart = position;
                while (!isAtEnd() && isIdentifierPart(peek())) {
                    advance();
                }
                String name = source.substring(nameStart, position);
                // The value includes the # prefix
                String value = "#" + name;
                Token token = new Token(TokenType.PRIVATE_NAME, value, startLine, startColumn, startPos);
                lastTokenType = token.type();
                return token;
            }
            // Otherwise, it's just a # token (for potential future use)
            Token token = makeToken(TokenType.HASH, "#");
            lastTokenType = token.type();
            return token;
        }

        // Operators and punctuation
        Token token = scanOperatorOrPunctuation(c, startPos, startLine, startColumn);
        lastTokenType = token.type();
        return token;
    }

    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char c = peek();

            // Line terminators (must be checked BEFORE isWhiteSpace)
            if (c == '\n' || c == '\r') {
                if (c == '\r' && position + 1 < source.length() && source.charAt(position + 1) == '\n') {
                    advance(); // consume \r
                }
                advance(); // consume \n
                line++;
                column = 1;
                continue;
            }

            // Whitespace
            if (UnicodeData.isWhiteSpace(c)) {
                advance();
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
}
