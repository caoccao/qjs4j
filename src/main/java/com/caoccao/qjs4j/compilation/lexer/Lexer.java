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
import com.caoccao.qjs4j.unicode.UnicodeData;

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
    private static final Map<String, TokenType> KEYWORDS = LexerKeywords.KEYWORDS;

    final String source;
    private final LexerTemplateScanner templateScanner;
    int column;
    int line;
    int position;
    private TokenType lastTokenType;
    private Token lookahead;
    private boolean strictMode;

    public Lexer(String source) {
        this.source = source;
        this.templateScanner = new LexerTemplateScanner(this);
        this.position = 0;
        this.line = 1;
        this.column = 1;
        this.lookahead = null;
        this.lastTokenType = null;
        this.strictMode = false;
    }

    private static boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029';
    }

    char advance() {
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

    private Token finalizeNumberOrBigIntToken(int startPos, int startLine, int startColumn) {
        if (!isAtEnd() && peek() == 'n') {
            advance(); // consume 'n'
            if (!isAtEnd() && (isIdentifierStart(peek()) || Character.isDigit(peek()))) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            String value = source.substring(startPos, position - 1); // exclude 'n' from value
            return new Token(TokenType.BIGINT, value, startLine, startColumn, startPos);
        }
        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    boolean isAtEnd() {
        return position >= source.length();
    }

    private boolean isDigitForRadix(char c, int radix) {
        return Character.digit(c, radix) >= 0;
    }

    boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' ||
                UnicodeData.isIdentifierPart(c);
    }

    private boolean isIdentifierPartCodePoint(int codePoint) {
        if (codePoint <= Character.MAX_VALUE) {
            return isIdentifierPart((char) codePoint);
        }
        return Character.isUnicodeIdentifierPart(codePoint);
    }

    // Core scanning logic

    boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$' ||
                UnicodeData.isIdentifierStart(c);
    }

    private boolean isIdentifierStartCodePoint(int codePoint) {
        if (codePoint <= Character.MAX_VALUE) {
            return isIdentifierStart((char) codePoint);
        }
        return Character.isUnicodeIdentifierStart(codePoint);
    }

    private Token makeToken(TokenType type, String value) {
        return new Token(type, value, line, column, position);
    }

    private boolean match(char expected) {
        if (isAtEnd()) {
            return false;
        }
        if (source.charAt(position) != expected) {
            return false;
        }
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

    private char parseLegacyOctalEscape(char firstDigit) {
        int value = firstDigit - '0';
        int maxDigits = firstDigit <= '3' ? 3 : 2;
        int digits = 1;
        while (digits < maxDigits && !isAtEnd() && peek() >= '0' && peek() <= '7') {
            value = (value << 3) + (advance() - '0');
            digits++;
        }
        return (char) value;
    }

    private int parseUnicodeEscapeSequence() {
        if (isAtEnd() || peek() != 'u') {
            return -1;
        }
        advance(); // consume 'u'
        return parseUnicodeEscapeSequenceAfterU();
    }

    private int parseUnicodeEscapeSequenceAfterU() {
        if (!isAtEnd() && peek() == '{') {
            advance(); // consume '{'
            int codePoint = 0;
            int digitCount = 0;
            while (!isAtEnd() && peek() != '}') {
                int hex = Character.digit(peek(), 16);
                if (hex < 0) {
                    return -1;
                }
                if (codePoint > 0x10FFFF / 16) {
                    return -1;
                }
                codePoint = (codePoint << 4) | hex;
                digitCount++;
                advance();
            }
            if (isAtEnd() || peek() != '}' || digitCount == 0 || codePoint > 0x10FFFF) {
                return -1;
            }
            advance(); // consume '}'
            return codePoint;
        }

        if (position + 3 >= source.length()) {
            return -1;
        }
        int codeUnit = 0;
        for (int i = 0; i < 4; i++) {
            int hex = Character.digit(source.charAt(position + i), 16);
            if (hex < 0) {
                return -1;
            }
            codeUnit = (codeUnit << 4) | hex;
        }
        position += 4;
        column += 4;
        return codeUnit;
    }

    char peek() {
        if (isAtEnd()) {
            return '\0';
        }
        return source.charAt(position);
    }

    // Character utilities

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

    public void restoreState(LexerState state) {
        this.position = state.position();
        this.line = state.line();
        this.column = state.column();
        this.lastTokenType = state.lastTokenType();
        this.lookahead = state.lookahead();
        this.strictMode = state.strictMode();
    }

    public LexerState saveState() {
        return new LexerState(position, line, column, lastTokenType, lookahead, strictMode);
    }

    private Token scanBinaryNumber(int startPos, int startLine, int startColumn) {
        scanDigitsWithNumericSeparators(2, false, false);
        validateNoRadixLiteralContinuation(2);
        return finalizeNumberOrBigIntToken(startPos, startLine, startColumn);
    }

    private void scanDigitsWithNumericSeparators(int radix, boolean firstDigitAlreadyConsumed, boolean firstDigitIsZero) {
        if (!firstDigitAlreadyConsumed) {
            if (isAtEnd() || !isDigitForRadix(peek(), radix)) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            advance();
        }

        int digitCount = 1;
        boolean lastWasSeparator = false;

        while (!isAtEnd()) {
            char c = peek();
            if (isDigitForRadix(c, radix)) {
                advance();
                digitCount++;
                lastWasSeparator = false;
            } else if (c == '_') {
                if (lastWasSeparator ||
                        (radix == 10 && firstDigitIsZero && digitCount == 1) ||
                        position + 1 >= source.length() ||
                        !isDigitForRadix(source.charAt(position + 1), radix)) {
                    throw new JSSyntaxErrorException("Invalid or unexpected token");
                }
                advance();
                lastWasSeparator = true;
            } else {
                break;
            }
        }

        if (lastWasSeparator) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
    }

    private Token scanHexNumber(int startPos, int startLine, int startColumn) {
        scanDigitsWithNumericSeparators(16, false, false);
        validateNoRadixLiteralContinuation(16);
        return finalizeNumberOrBigIntToken(startPos, startLine, startColumn);
    }

    private Token scanIdentifier(int startPos, int startLine, int startColumn, int firstCodePoint) {
        StringBuilder valueBuilder = new StringBuilder();
        valueBuilder.appendCodePoint(firstCodePoint);

        while (!isAtEnd()) {
            if (peek() == '\\') {
                int currentPos = position;
                int currentColumn = column;
                advance(); // consume '\'
                int codePoint = parseUnicodeEscapeSequence();
                if (codePoint < 0) {
                    position = currentPos;
                    column = currentColumn;
                    break;
                }
                if (!isIdentifierPartCodePoint(codePoint)) {
                    throw new JSSyntaxErrorException("Invalid or unexpected token");
                }
                valueBuilder.appendCodePoint(codePoint);
                continue;
            }

            if (!isIdentifierPart(peek())) {
                break;
            }
            valueBuilder.append(advance());
        }

        String value = valueBuilder.toString();

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
        scanDigitsWithNumericSeparators(10, true, source.charAt(startPos) == '0');

        // Check for decimal point. Match QuickJS behavior: for decimal literals that already
        // have an integer part, '.' is part of the numeric token even without following digits.
        if (!isAtEnd() && peek() == '.') {
            hasDecimalPoint = true;
            advance(); // consume '.'
            if (!isAtEnd() && peek() == '_') {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            if (!isAtEnd() && Character.isDigit(peek())) {
                scanDigitsWithNumericSeparators(10, false, false);
            }
        }

        // Check for exponent
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            hasExponent = true;
            advance(); // consume 'e'
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                advance(); // consume sign
            }
            if (isAtEnd() || !Character.isDigit(peek())) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            scanDigitsWithNumericSeparators(10, false, false);
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
            if (!isAtEnd() && (isIdentifierStart(peek()) || Character.isDigit(peek()))) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            String value = source.substring(startPos, position - 1); // exclude 'n' from value
            return new Token(TokenType.BIGINT, value, startLine, startColumn, startPos);
        }

        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    private Token scanNumberStartingWithDot(int startPos, int startLine, int startColumn) {
        // At least one decimal digit is required after '.'
        scanDigitsWithNumericSeparators(10, false, false);

        // Optional exponent part
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            advance(); // consume e/E
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                advance();
            }
            if (isAtEnd() || !Character.isDigit(peek())) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            scanDigitsWithNumericSeparators(10, false, false);
        }

        if (!isAtEnd() && (peek() == 'n' || isIdentifierStart(peek()))) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }

        String value = source.substring(startPos, position);
        return new Token(TokenType.NUMBER, value, startLine, startColumn, startPos);
    }

    private Token scanOctalNumber(int startPos, int startLine, int startColumn) {
        scanDigitsWithNumericSeparators(8, false, false);
        validateNoRadixLiteralContinuation(8);
        return finalizeNumberOrBigIntToken(startPos, startLine, startColumn);
    }

    private Token scanOperatorOrPunctuation(char c, int startPos, int startLine, int startColumn) {
        // Special case: check for regex literal when encountering '/'
        if (c == '/' && expectRegex()) {
            // Back up one position and scan as regex
            position = startPos;
            column = startColumn;
            return scanRegex(startPos, startLine, startColumn);
        }
        if (c == '.' && !isAtEnd() && Character.isDigit(peek())) {
            return scanNumberStartingWithDot(startPos, startLine, startColumn);
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
                } else if (!isAtEnd() && peek() == '.'
                        && (position + 1 >= source.length() || !Character.isDigit(source.charAt(position + 1)))) {
                    advance(); // consume '.'
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
                if (match('+')) {
                    yield TokenType.INC;
                } else if (match('=')) {
                    yield TokenType.PLUS_ASSIGN;
                } else {
                    yield TokenType.PLUS;
                }
            }
            case '-' -> {
                if (match('-')) {
                    yield TokenType.DEC;
                } else if (match('=')) {
                    yield TokenType.MINUS_ASSIGN;
                } else {
                    yield TokenType.MINUS;
                }
            }
            case '*' -> {
                if (match('*')) {
                    if (match('=')) {
                        yield TokenType.EXP_ASSIGN;
                    } else {
                        yield TokenType.EXP;
                    }
                } else if (match('=')) {
                    yield TokenType.MUL_ASSIGN;
                } else {
                    yield TokenType.MUL;
                }
            }
            case '/' -> {
                if (match('=')) {
                    yield TokenType.DIV_ASSIGN;
                } else {
                    yield TokenType.DIV;
                }
            }
            case '%' -> {
                if (match('=')) {
                    yield TokenType.MOD_ASSIGN;
                } else {
                    yield TokenType.MOD;
                }
            }
            case '&' -> {
                if (match('&')) {
                    if (match('=')) {
                        yield TokenType.LOGICAL_AND_ASSIGN;
                    } else {
                        yield TokenType.LOGICAL_AND;
                    }
                } else if (match('=')) {
                    yield TokenType.AND_ASSIGN;
                } else {
                    yield TokenType.BIT_AND;
                }
            }
            case '|' -> {
                if (match('|')) {
                    if (match('=')) {
                        yield TokenType.LOGICAL_OR_ASSIGN;
                    } else {
                        yield TokenType.LOGICAL_OR;
                    }
                } else if (match('=')) {
                    yield TokenType.OR_ASSIGN;
                } else {
                    yield TokenType.BIT_OR;
                }
            }
            case '^' -> {
                if (match('=')) {
                    yield TokenType.XOR_ASSIGN;
                } else {
                    yield TokenType.BIT_XOR;
                }
            }
            case '=' -> {
                if (match('=')) {
                    if (match('=')) {
                        yield TokenType.STRICT_EQ;
                    } else {
                        yield TokenType.EQ;
                    }
                } else if (match('>')) {
                    yield TokenType.ARROW;
                } else {
                    yield TokenType.ASSIGN;
                }
            }
            case '!' -> {
                if (match('=')) {
                    if (match('=')) {
                        yield TokenType.STRICT_NE;
                    } else {
                        yield TokenType.NE;
                    }
                } else {
                    yield TokenType.NOT;
                }
            }
            case '<' -> {
                if (match('<')) {
                    if (match('=')) {
                        yield TokenType.LSHIFT_ASSIGN;
                    } else {
                        yield TokenType.LSHIFT;
                    }
                } else if (match('=')) {
                    yield TokenType.LE;
                } else {
                    yield TokenType.LT;
                }
            }
            case '>' -> {
                if (match('>')) {
                    if (match('>')) {
                        if (match('=')) {
                            yield TokenType.URSHIFT_ASSIGN;
                        } else {
                            yield TokenType.URSHIFT;
                        }
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
            default -> throw new JSSyntaxErrorException("Invalid or unexpected token");
        };

        String value = source.substring(startPos, position);
        return new Token(type, value, startLine, startColumn, startPos);
    }

    private Token scanRegex(int startPos, int startLine, int startColumn) {
        advance(); // consume opening /

        StringBuilder pattern = new StringBuilder();
        boolean inCharClass = false;
        boolean terminated = false;

        while (!isAtEnd()) {
            char c = peek();

            // End of regex
            if (c == '/' && !inCharClass) {
                advance(); // consume closing /
                terminated = true;
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
                if (isAtEnd()) {
                    throw new JSSyntaxErrorException("Invalid or unexpected token");
                }
                pattern.append(advance()); // append escaped character
                continue;
            }

            // Newlines terminate regex
            if (c == '\n' || c == '\r') {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }

            pattern.append(advance());
        }

        if (!terminated) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
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
            if (peek() == '\n' || peek() == '\r') {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }

            if (peek() == '\\') {
                advance(); // consume backslash
                if (isAtEnd()) {
                    throw new JSSyntaxErrorException("Invalid or unexpected token");
                }
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
                    case '0' -> {
                        if (!isAtEnd() && peek() >= '0' && peek() <= '7') {
                            if (strictMode) {
                                throw new JSSyntaxErrorException("Octal escape sequences are not allowed in strict mode");
                            }
                            value.append(parseLegacyOctalEscape(escaped));
                        } else {
                            value.append('\0');
                        }
                    }
                    case 'x' -> {
                        if (position + 1 >= source.length()) {
                            throw new JSSyntaxErrorException("Invalid or unexpected token");
                        }
                        int hi = Character.digit(source.charAt(position), 16);
                        int lo = Character.digit(source.charAt(position + 1), 16);
                        if (hi < 0 || lo < 0) {
                            throw new JSSyntaxErrorException("Invalid or unexpected token");
                        }
                        value.append((char) ((hi << 4) + lo));
                        position += 2;
                        column += 2;
                    }
                    case 'u' -> {
                        int codePoint = parseUnicodeEscapeSequenceAfterU();
                        if (codePoint < 0) {
                            throw new JSSyntaxErrorException("Invalid or unexpected token");
                        }
                        value.appendCodePoint(codePoint);
                    }
                    case '\n' -> {
                        line++;
                        column = 1;
                    }
                    case '\r' -> {
                        if (!isAtEnd() && peek() == '\n') {
                            advance();
                        }
                        line++;
                        column = 1;
                    }
                    default -> {
                        if (escaped >= '1' && escaped <= '7') {
                            if (strictMode) {
                                throw new JSSyntaxErrorException("Octal escape sequences are not allowed in strict mode");
                            }
                            value.append(parseLegacyOctalEscape(escaped));
                        } else if (strictMode && (escaped == '8' || escaped == '9')) {
                            throw new JSSyntaxErrorException("\\8 and \\9 are not allowed in strict mode");
                        } else {
                            value.append(escaped);
                        }
                    }
                }
            } else {
                value.append(advance());
            }
        }

        if (isAtEnd()) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        advance(); // consume closing quote

        return new Token(TokenType.STRING, value.toString(), startLine, startColumn, startPos);
    }

    private Token scanTemplate(int startPos, int startLine, int startColumn) {
        return templateScanner.scanTemplate(startPos, startLine, startColumn);
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
            Token token = scanIdentifier(startPos, startLine, startColumn, c);
            lastTokenType = token.type();
            return token;
        }

        // Identifier with Unicode escape start (\\uXXXX / \\u{...})
        if (c == '\\' && !isAtEnd() && peek() == 'u') {
            int codePoint = parseUnicodeEscapeSequence();
            if (codePoint < 0 || !isIdentifierStartCodePoint(codePoint)) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            Token token = scanIdentifier(startPos, startLine, startColumn, codePoint);
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
            if (!isAtEnd() && (isIdentifierStart(peek()) || peek() == '\\')) {
                StringBuilder name = new StringBuilder();

                if (peek() == '\\') {
                    advance(); // consume '\'
                    int codePoint = parseUnicodeEscapeSequence();
                    if (codePoint < 0 || !isIdentifierStartCodePoint(codePoint)) {
                        throw new JSSyntaxErrorException("Invalid or unexpected token");
                    }
                    name.appendCodePoint(codePoint);
                } else {
                    char first = advance();
                    if (!isIdentifierStart(first)) {
                        throw new JSSyntaxErrorException("Invalid or unexpected token");
                    }
                    name.append(first);
                }

                while (!isAtEnd()) {
                    if (peek() == '\\') {
                        int currentPos = position;
                        int currentColumn = column;
                        advance(); // consume '\'
                        int codePoint = parseUnicodeEscapeSequence();
                        if (codePoint < 0) {
                            position = currentPos;
                            column = currentColumn;
                            break;
                        }
                        if (!isIdentifierPartCodePoint(codePoint)) {
                            throw new JSSyntaxErrorException("Invalid or unexpected token");
                        }
                        name.appendCodePoint(codePoint);
                    } else if (isIdentifierPart(peek())) {
                        name.append(advance());
                    } else {
                        break;
                    }
                }

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

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    private void skipWhitespaceAndComments() {
        boolean seenLineTerminator = false;
        while (!isAtEnd()) {
            char c = peek();

            // Line terminators (must be checked BEFORE isWhiteSpace)
            if (isLineTerminator(c)) {
                if (c == '\r' && position + 1 < source.length() && source.charAt(position + 1) == '\n') {
                    advance(); // consume \r
                }
                advance(); // consume line terminator
                line++;
                column = 1;
                seenLineTerminator = true;
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
                    while (!isAtEnd() && !isLineTerminator(peek())) {
                        advance();
                    }
                    continue;
                }

                // Multi-line comment
                if (next == '*') {
                    advance(); // consume /
                    advance(); // consume *
                    boolean closed = false;
                    while (!isAtEnd()) {
                        if (peek() == '*' && position + 1 < source.length() && source.charAt(position + 1) == '/') {
                            advance(); // consume *
                            advance(); // consume /
                            closed = true;
                            break;
                        }
                        if (isLineTerminator(peek())) {
                            line++;
                            column = 0;
                            seenLineTerminator = true;
                        }
                        advance();
                    }
                    if (!closed) {
                        throw new JSSyntaxErrorException("Invalid or unexpected token");
                    }
                    continue;
                }
            }

            // Annex B: HTML-like comment <!-- (treated as single-line comment)
            if (c == '<' && position + 3 < source.length()
                    && source.charAt(position + 1) == '!'
                    && source.charAt(position + 2) == '-'
                    && source.charAt(position + 3) == '-') {
                advance(); // <
                advance(); // !
                advance(); // -
                advance(); // -
                while (!isAtEnd() && !isLineTerminator(peek())) {
                    advance();
                }
                continue;
            }

            // Annex B: HTML-like close comment --> (treated as single-line comment
            // only when preceded by a line terminator per ES spec)
            if (seenLineTerminator
                    && c == '-' && position + 2 < source.length()
                    && source.charAt(position + 1) == '-'
                    && source.charAt(position + 2) == '>') {
                advance(); // -
                advance(); // -
                advance(); // >
                while (!isAtEnd() && !isLineTerminator(peek())) {
                    advance();
                }
                continue;
            }

            break;
        }
    }

    private void validateNoRadixLiteralContinuation(int radix) {
        if (isAtEnd()) {
            return;
        }

        char next = peek();
        if (next == '.' || next == 'e' || next == 'E' || next == '_' || Character.isDigit(next)) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        if (isIdentifierStart(next) && next != 'n') {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
    }

}
