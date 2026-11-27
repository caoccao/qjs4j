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

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.unicode.UnicodeData;

import java.util.HashMap;
import java.util.Map;

/**
 * Lexical analyzer for JavaScript source code. Converts source text into a stream of tokens.
 * <p>
 * Implements ECMAScript lexical grammar including: - Number literals (decimal, hex, binary, octal, scientific) - String
 * literals with escape sequences - Identifiers and keywords - Operators and punctuation - Comments (single-line,
 * multi-line) - Template literals (basic support)
 */
public final class Lexer {
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put(JSKeyword.AS, TokenType.AS);
        KEYWORDS.put(JSKeyword.ASYNC, TokenType.ASYNC);
        KEYWORDS.put(JSKeyword.AWAIT, TokenType.AWAIT);
        KEYWORDS.put(JSKeyword.BREAK, TokenType.BREAK);
        KEYWORDS.put(JSKeyword.CASE, TokenType.CASE);
        KEYWORDS.put(JSKeyword.CATCH, TokenType.CATCH);
        KEYWORDS.put(JSKeyword.CLASS, TokenType.CLASS);
        KEYWORDS.put(JSKeyword.CONST, TokenType.CONST);
        KEYWORDS.put(JSKeyword.CONTINUE, TokenType.CONTINUE);
        KEYWORDS.put(JSKeyword.DEFAULT, TokenType.DEFAULT);
        KEYWORDS.put(JSKeyword.DELETE, TokenType.DELETE);
        KEYWORDS.put(JSKeyword.DO, TokenType.DO);
        KEYWORDS.put(JSKeyword.ELSE, TokenType.ELSE);
        KEYWORDS.put(JSKeyword.EXPORT, TokenType.EXPORT);
        KEYWORDS.put(JSKeyword.EXTENDS, TokenType.EXTENDS);
        KEYWORDS.put(JSKeyword.FALSE, TokenType.FALSE);
        KEYWORDS.put(JSKeyword.FINALLY, TokenType.FINALLY);
        KEYWORDS.put(JSKeyword.FOR, TokenType.FOR);
        KEYWORDS.put(JSKeyword.FROM, TokenType.FROM);
        KEYWORDS.put(JSKeyword.FUNCTION, TokenType.FUNCTION);
        KEYWORDS.put(JSKeyword.IF, TokenType.IF);
        KEYWORDS.put(JSKeyword.IMPORT, TokenType.IMPORT);
        KEYWORDS.put(JSKeyword.IN, TokenType.IN);
        KEYWORDS.put(JSKeyword.INSTANCEOF, TokenType.INSTANCEOF);
        KEYWORDS.put(JSKeyword.LET, TokenType.LET);
        KEYWORDS.put(JSKeyword.NEW, TokenType.NEW);
        KEYWORDS.put(JSKeyword.NULL, TokenType.NULL);
        KEYWORDS.put(JSKeyword.OF, TokenType.OF);
        KEYWORDS.put(JSKeyword.RETURN, TokenType.RETURN);
        KEYWORDS.put(JSKeyword.SUPER, TokenType.SUPER);
        KEYWORDS.put(JSKeyword.SWITCH, TokenType.SWITCH);
        KEYWORDS.put(JSKeyword.THIS, TokenType.THIS);
        KEYWORDS.put(JSKeyword.THROW, TokenType.THROW);
        KEYWORDS.put(JSKeyword.TRUE, TokenType.TRUE);
        KEYWORDS.put(JSKeyword.TRY, TokenType.TRY);
        KEYWORDS.put(JSKeyword.TYPEOF, TokenType.TYPEOF);
        KEYWORDS.put(JSKeyword.VAR, TokenType.VAR);
        KEYWORDS.put(JSKeyword.VOID, TokenType.VOID);
        KEYWORDS.put(JSKeyword.WHILE, TokenType.WHILE);
        KEYWORDS.put(JSKeyword.YIELD, TokenType.YIELD);
    }

    int column;
    /**
     * Whether the {@code default} keyword just produced was a property name — {@code x.default} — rather than the
     * {@code default} of {@code export default}. See {@link #expectRegex()}.
     */
    private boolean defaultFollowsPropertyAccess;
    private TokenType lastTokenType;
    int line;
    private Token lookahead;
    private boolean moduleMode;
    int position;
    final String source;
    private boolean strictMode;

    public Lexer(String source) {
        this.source = source;
        this.position = 0;
        this.line = 1;
        this.column = 1;
        this.lookahead = null;
        this.lastTokenType = null;
        this.defaultFollowsPropertyAccess = false;
        this.moduleMode = false;
        this.strictMode = false;
    }

    char advance() {
        char c = source.charAt(position);
        position++;
        column++;
        return c;
    }

    private boolean atIdentifierStart() {
        char c = peek();
        return isIdentifierStart(c)
                || (Character.isHighSurrogate(c) && UnicodeData.isIdentifierStart(source.codePointAt(position)));
    }

    /**
     * Determine if the current context expects a regex literal. Regex can appear after operators, keywords, or at the
     * start of an expression.
     */
    private boolean expectRegex() {
        if (lastTokenType == null) {
            return true; // Start of input
        }

        return switch (lastTokenType) {
            // After operators
            case ASSIGN, EQ, NE, STRICT_EQ, STRICT_NE, LT, LE, GT, GE, PLUS, MINUS, MUL, DIV, MOD, EXP, BIT_AND, BIT_OR,
                    BIT_XOR, BIT_NOT, LOGICAL_AND, LOGICAL_OR, NOT, LSHIFT, RSHIFT, URSHIFT, PLUS_ASSIGN, MINUS_ASSIGN,
                    MUL_ASSIGN, DIV_ASSIGN, MOD_ASSIGN, EXP_ASSIGN, AND_ASSIGN, OR_ASSIGN, XOR_ASSIGN, LSHIFT_ASSIGN,
                    RSHIFT_ASSIGN, URSHIFT_ASSIGN, NULLISH_COALESCING,
                    // After punctuation
                    LPAREN, LBRACKET, LBRACE, COMMA, SEMICOLON, COLON, QUESTION, ARROW,
                    // After keywords that start expressions
                    // Note: AWAIT is intentionally excluded — in non-async context await is
                    // an identifier and '/' is division. In async context, the parser's
                    // rescanAsRegex() path in parsePrimaryExpression handles the DIV→REGEX rescan.
                    // Note: YIELD is intentionally excluded for the same reason in non-generator
                    // sloppy code (`yield` may be an identifier, so '/' can be division).
                    // Generator contexts that need a regex after yield use parser rescan.
                    RETURN, THROW, TYPEOF, VOID, DELETE, NEW, IF, WHILE, FOR, CASE ->
                true;
            // `export default /re/` is a regular expression: `default` is followed by an
            // AssignmentExpression there, and it was read as division, so `export default /\(/;`
            // failed to lex at all. `case` was already here; `default` is its other half in a
            // switch, where a colon always separates it from what follows. The one other place the
            // keyword can appear before a '/' is as a property name — `x.default / 2` — which is
            // division, so that is asked about separately.
            case DEFAULT -> !defaultFollowsPropertyAccess;
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

    public String getSource() {
        return source;
    }

    boolean isAtEnd() {
        return position >= source.length();
    }

    private boolean isDigitForRadix(char c, int radix) {
        return Character.digit(c, radix) >= 0;
    }

    // Core scanning logic

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

    private TokenType matchEquals(TokenType plain, TokenType withEquals) {
        return match('=') ? withEquals : plain;
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
        return scanTokenWithLocation();
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

    /**
     * Peek at the next token without consuming it.
     */
    public Token peekToken() {
        if (lookahead == null) {
            lookahead = scanTokenWithLocation();
        }
        return lookahead;
    }

    /**
     * Re-scan a DIV or DIV_ASSIGN token as a regex literal. Called by the parser when it encounters '/' or '/=' in
     * expression position (e.g. after a block statement's closing brace) where the lexer incorrectly tokenized it as
     * division. Mirrors QuickJS's js_parse_regexp() re-scan.
     */
    public Token rescanAsRegex(Token divToken) {
        int startPos = divToken.offset();
        int startLine = divToken.line();
        int startColumn = divToken.column();
        // Back up to the '/' character — for DIV_ASSIGN the token is '/=' (2 chars)
        position = startPos;
        line = startLine;
        column = startColumn;
        Token regexToken = scanRegex(startPos, startLine, startColumn);
        lastTokenType = regexToken.type();
        return regexToken;
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
        defaultFollowsPropertyAccess = false;
    }

    // Character utilities

    public void restoreState(LexerState state) {
        this.position = state.position();
        this.line = state.line();
        this.column = state.column();
        this.lastTokenType = state.lastTokenType();
        this.defaultFollowsPropertyAccess = state.defaultFollowsPropertyAccess();
        this.lookahead = state.lookahead();
        this.strictMode = state.strictMode();
    }

    public LexerState saveState() {
        return new LexerState(position, line, column, lastTokenType, defaultFollowsPropertyAccess, lookahead,
                strictMode);
    }

    private void scanDigitsWithNumericSeparators(int radix, boolean firstDigitAlreadyConsumed,
            boolean firstDigitIsZero) {
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
                if (lastWasSeparator || (radix == 10 && firstDigitIsZero && digitCount == 1)
                        || position + 1 >= source.length() || !isDigitForRadix(source.charAt(position + 1), radix)) {
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

    private boolean scanExponent() {
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            advance(); // consume e/E
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                advance();
            }
            if (isAtEnd() || !Character.isDigit(peek())) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
            scanDigitsWithNumericSeparators(10, false, false);
            return true;
        }
        return false;
    }

    private Token scanIdentifier(int startPos, int startLine, int startColumn, boolean privateName) {
        StringBuilder decoded = null;
        boolean hasEscape = false;
        int segmentStart = startPos;
        int first = privateName ? startPos + 1 : startPos;
        while (!isAtEnd()) {
            char c = peek();
            if (c == '\\') {
                hasEscape = true;
                int escapeStart = position;
                int escapeColumn = column;
                advance();
                int codePoint = parseUnicodeEscapeSequence();
                if (codePoint < 0 && escapeStart != first) {
                    position = escapeStart;
                    column = escapeColumn;
                    break;
                }
                if (codePoint < 0 || !(escapeStart == first
                        ? UnicodeData.isIdentifierStart(codePoint)
                        : UnicodeData.isIdentifierPart(codePoint))) {
                    throw new JSSyntaxErrorException("Invalid or unexpected token");
                }
                if (decoded == null) {
                    decoded = new StringBuilder();
                }
                decoded.append(source, segmentStart, escapeStart).appendCodePoint(codePoint);
                segmentStart = position;
            } else if (isIdentifierPart(c)) {
                advance();
            } else {
                int codePoint = source.codePointAt(position);
                if (codePoint == c || !UnicodeData.isIdentifierPart(codePoint)) {
                    break;
                }
                position += 2;
                column += 2;
            }
        }
        String value = decoded == null
                ? source.substring(startPos, position)
                : decoded.append(source, segmentStart, position).toString();
        if (privateName) {
            return new Token(TokenType.PRIVATE_NAME, value, startLine, startColumn, startPos);
        }
        // Escaped reserved words stay identifiers until the parser checks their context.
        TokenType type = hasEscape ? TokenType.IDENTIFIER : KEYWORDS.getOrDefault(value, TokenType.IDENTIFIER);
        return new Token(type, value, startLine, startColumn, startPos, hasEscape);
    }

    private Token scanNumber(int startPos, int startLine, int startColumn) {
        // Handle special prefixes (0x, 0b, 0o)
        if (source.charAt(startPos) == '0' && position < source.length()) {
            int radix = switch (peek()) {
                case 'x', 'X' -> 16;
                case 'o', 'O' -> 8;
                case 'b', 'B' -> 2;
                default -> 0;
            };
            if (radix != 0) {
                advance();
                scanDigitsWithNumericSeparators(radix, false, false);
                validateNoRadixLiteralContinuation();
                return finalizeNumberOrBigIntToken(startPos, startLine, startColumn);
            }
        }

        // Scan decimal number
        boolean hasDecimalPoint = false;
        scanDigitsWithNumericSeparators(10, true, source.charAt(startPos) == '0');

        // Numeric separators are not allowed in legacy-octal-like or non-octal decimal
        // literals (e.g. 00_0, 01_0, 08_0, 09_0). These start with 0 followed by digits.
        if (source.charAt(startPos) == '0' && position > startPos + 1) {
            for (int i = startPos + 1; i < position; i++) {
                if (source.charAt(i) == '_') {
                    throw new JSSyntaxErrorException("Invalid or unexpected token");
                }
            }
        }

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

        boolean hasExponent = scanExponent();

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
            // Decimal BigInt with a leading zero followed by more digits is invalid.
            // This covers legacy-octal-like (00n, 01n, 07n) and non-octal decimal (08n, 0008n, 08_0n).
            // The only valid single-zero decimal BigInt is 0n.
            if (source.charAt(startPos) == '0' && position > startPos + 1) {
                throw new JSSyntaxErrorException("Invalid or unexpected token");
            }
        }
        return finalizeNumberOrBigIntToken(startPos, startLine, startColumn);
    }

    private Token scanNumberStartingWithDot(int startPos, int startLine, int startColumn) {
        // At least one decimal digit is required after '.'
        scanDigitsWithNumericSeparators(10, false, false);

        // Optional exponent part
        scanExponent();

        if (!isAtEnd() && (peek() == 'n' || isIdentifierStart(peek()))) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
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
                    yield matchEquals(TokenType.NULLISH_COALESCING, TokenType.NULLISH_ASSIGN);
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
            case '+' -> match('+') ? TokenType.INC : matchEquals(TokenType.PLUS, TokenType.PLUS_ASSIGN);
            case '-' -> match('-') ? TokenType.DEC : matchEquals(TokenType.MINUS, TokenType.MINUS_ASSIGN);
            case '*' -> match('*')
                    ? matchEquals(TokenType.EXP, TokenType.EXP_ASSIGN)
                    : matchEquals(TokenType.MUL, TokenType.MUL_ASSIGN);
            case '/' -> matchEquals(TokenType.DIV, TokenType.DIV_ASSIGN);
            case '%' -> matchEquals(TokenType.MOD, TokenType.MOD_ASSIGN);
            case '&' -> match('&')
                    ? matchEquals(TokenType.LOGICAL_AND, TokenType.LOGICAL_AND_ASSIGN)
                    : matchEquals(TokenType.BIT_AND, TokenType.AND_ASSIGN);
            case '|' -> match('|')
                    ? matchEquals(TokenType.LOGICAL_OR, TokenType.LOGICAL_OR_ASSIGN)
                    : matchEquals(TokenType.BIT_OR, TokenType.OR_ASSIGN);
            case '^' -> matchEquals(TokenType.BIT_XOR, TokenType.XOR_ASSIGN);
            case '=' -> {
                if (match('=')) {
                    yield matchEquals(TokenType.EQ, TokenType.STRICT_EQ);
                } else if (match('>')) {
                    yield TokenType.ARROW;
                } else {
                    yield TokenType.ASSIGN;
                }
            }
            case '!' -> {
                if (match('=')) {
                    yield matchEquals(TokenType.NE, TokenType.STRICT_NE);
                } else {
                    yield TokenType.NOT;
                }
            }
            case '<' -> match('<')
                    ? matchEquals(TokenType.LSHIFT, TokenType.LSHIFT_ASSIGN)
                    : matchEquals(TokenType.LT, TokenType.LE);
            case '>' -> {
                if (match('>')) {
                    yield match('>')
                            ? matchEquals(TokenType.URSHIFT, TokenType.URSHIFT_ASSIGN)
                            : matchEquals(TokenType.RSHIFT, TokenType.RSHIFT_ASSIGN);
                }
                yield matchEquals(TokenType.GT, TokenType.GE);
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
                // RegularExpressionBackslashSequence requires a RegularExpressionNonTerminator
                // after the backslash — line terminators are not allowed.
                if (isLineTerminator(peek())) {
                    break;
                }
                pattern.append(advance()); // append escaped character
                continue;
            }

            // Newlines and Unicode line terminators (LS \u2028, PS \u2029) terminate regex
            if (isLineTerminator(c)) {
                break;
            }

            pattern.append(advance());
        }

        if (!terminated) {
            throw new JSSyntaxErrorException("Invalid regular expression: missing /");
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
        boolean hasOctalEscape = false;

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
                        if (!isAtEnd() && peek() >= '0' && peek() <= '9') {
                            if (strictMode) {
                                throw new JSSyntaxErrorException(
                                        "Octal escape sequences are not allowed in strict mode");
                            }
                            hasOctalEscape = true;
                            if (peek() <= '7') {
                                value.append(parseLegacyOctalEscape(escaped));
                            } else {
                                // \08, \09 in non-strict: \0 = null char, 8/9 is next literal
                                value.append('\0');
                            }
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
                    case '\u2028', '\u2029' -> {
                        // Line continuation with Unicode line/paragraph separator
                        line++;
                        column = 1;
                    }
                    default -> {
                        if (escaped >= '1' && escaped <= '7') {
                            if (strictMode) {
                                throw new JSSyntaxErrorException(
                                        "Octal escape sequences are not allowed in strict mode");
                            }
                            hasOctalEscape = true;
                            value.append(parseLegacyOctalEscape(escaped));
                        } else if (escaped == '8' || escaped == '9') {
                            if (strictMode) {
                                throw new JSSyntaxErrorException("\\8 and \\9 are not allowed in strict mode");
                            }
                            hasOctalEscape = true;
                            value.append(escaped);
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

        return new Token(TokenType.STRING, value.toString(), startLine, startColumn, startPos, false, hasOctalEscape);
    }

    private Token scanTemplate(int startPos, int startLine, int startColumn) {
        int end = LexerTemplateScanner.templateEnd(source, position);
        String value = source.substring(position, end);
        column += end + 1 - position;
        position = end + 1;
        return new Token(TokenType.TEMPLATE, value, startLine, startColumn, startPos);
    }

    private Token scanToken() {
        skipWhitespaceAndComments();
        if (isAtEnd()) {
            lastTokenType = TokenType.EOF;
            return makeToken(TokenType.EOF, "");
        }
        int startPos = position;
        int startLine = line;
        int startColumn = column;
        char c = peek();
        Token token;
        if (atIdentifierStart() || c == '\\') {
            if (isIdentifierStart(c)) {
                defaultFollowsPropertyAccess = lastTokenType == TokenType.DOT
                        || lastTokenType == TokenType.OPTIONAL_CHAINING;
            }
            token = scanIdentifier(startPos, startLine, startColumn, false);
        } else {
            advance();
            if (Character.isDigit(c)) {
                token = scanNumber(startPos, startLine, startColumn);
            } else {
                token = switch (c) {
                    case '"', '\'' -> scanString(c, startPos, startLine, startColumn);
                    case '`' -> scanTemplate(startPos, startLine, startColumn);
                    case '@' -> new Token(TokenType.AT, "@", startLine, startColumn, startPos);
                    case '#' -> atIdentifierStart() || peek() == '\\'
                            ? scanIdentifier(startPos, startLine, startColumn, true)
                            : makeToken(TokenType.HASH, "#");
                    default -> scanOperatorOrPunctuation(c, startPos, startLine, startColumn);
                };
            }
        }
        lastTokenType = token.type();
        return token;
    }

    private Token scanTokenWithLocation() {
        try {
            return scanToken();
        } catch (JSSyntaxErrorException e) {
            throw e.withSourceLocation(new SourceLocation(line, column, position));
        }
    }

    public void setModuleMode(boolean moduleMode) {
        this.moduleMode = moduleMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    private void skipLineComment() {
        while (!isAtEnd() && !isLineTerminator(peek())) {
            advance();
        }
    }

    private void skipWhitespaceAndComments() {
        boolean seenLineTerminator = false;
        while (!isAtEnd()) {
            char c = peek();

            // Hashbang comment (#!...) is only valid at the very start of source text.
            if (position == 0 && c == '#' && position + 1 < source.length() && source.charAt(position + 1) == '!') {
                skipLineComment();
                continue;
            }

            // Annex B SingleLineHTMLCloseComment in first line before any token.
            if (lastTokenType == null && line == 1 && c == '-' && position + 2 < source.length()
                    && source.charAt(position + 1) == '-' && source.charAt(position + 2) == '>') {
                skipLineComment();
                continue;
            }

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
                    skipLineComment();
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
            // Not allowed in module code per ES spec B.1.1
            if (c == '<' && position + 3 < source.length() && source.charAt(position + 1) == '!'
                    && source.charAt(position + 2) == '-' && source.charAt(position + 3) == '-') {
                if (moduleMode) {
                    throw new JSSyntaxErrorException("HTML comments are not allowed in modules");
                }
                skipLineComment();
                continue;
            }

            // Annex B: HTML-like close comment --> (treated as single-line comment
            // only when preceded by a line terminator per ES spec)
            // Not allowed in module code per ES spec B.1.1
            if (seenLineTerminator && c == '-' && position + 2 < source.length() && source.charAt(position + 1) == '-'
                    && source.charAt(position + 2) == '>') {
                if (moduleMode) {
                    throw new JSSyntaxErrorException("HTML comments are not allowed in modules");
                }
                skipLineComment();
                continue;
            }

            break;
        }
    }

    private void validateNoRadixLiteralContinuation() {
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

    static boolean isIdentifierPart(char c) {
        if (c < 128) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$';
        }
        return UnicodeData.isIdentifierPart(c);
    }

    static boolean isIdentifierStart(char c) {
        if (c < 128) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
        }
        return UnicodeData.isIdentifierStart(c);
    }

    private static boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029';
    }
}
