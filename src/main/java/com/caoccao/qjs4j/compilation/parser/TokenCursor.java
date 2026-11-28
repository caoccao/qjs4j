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
import com.caoccao.qjs4j.compilation.lexer.LexerState;
import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

/** Two-token lookahead and lexer checkpoints shared by all grammar productions in one parse. */
final class TokenCursor {
    private Token currentToken;
    private final Lexer lexer;
    private Token nextToken;
    private int previousTokenEndOffset;
    private int previousTokenLine;

    TokenCursor(Lexer lexer, boolean moduleMode) {
        this.lexer = lexer;
        lexer.setModuleMode(moduleMode);
        this.currentToken = lexer.nextToken();
        this.nextToken = lexer.nextToken();
    }

    void advance() {
        previousTokenLine = currentToken.line();
        previousTokenEndOffset = currentToken.offset() + currentToken.value().length();
        currentToken = nextToken;
        nextToken = lexer.nextToken();
    }

    Identifier consumeIdentifierName() {
        SourceLocation location = getLocation();
        String name = currentToken.value();
        advance();
        return new Identifier(name, location);
    }

    boolean consumeIf(TokenType type) {
        if (!match(type)) {
            return false;
        }
        advance();
        return true;
    }

    SourceLocation consumeLocation() {
        SourceLocation location = getLocation();
        advance();
        return location;
    }

    String consumeValue() {
        String value = currentToken.value();
        advance();
        return value;
    }

    Token current() {
        return currentToken;
    }

    Token expect(TokenType type) {
        if (!match(type)) {
            if (currentToken.type() == TokenType.EOF) {
                // Source that simply stops early is not a token mismatch, and naming the token type
                // the parser wanted describes the parser rather than the program. `export {} from`
                // reported "Expected STRING but got EOF at line 1, column 15" where every engine
                // says the input ended.
                throw new JSSyntaxErrorException("Unexpected end of input");
            }
            throw new JSSyntaxErrorException("Expected " + type + " but got " + currentToken.type() + " at line "
                    + currentToken.line() + ", column " + currentToken.column());
        }
        Token token = currentToken;
        advance();
        return token;
    }

    SourceLocation getLocation() {
        return new SourceLocation(currentToken.line(), currentToken.column(), currentToken.offset());
    }

    boolean hasNewlineBefore() {
        return currentToken.line() > previousTokenLine;
    }

    boolean identifier(String name) {
        return match(TokenType.IDENTIFIER) && name.equals(currentToken.value());
    }

    boolean isAwaitUsingDeclarationStart() {
        boolean awaitToken = match(TokenType.AWAIT) || (identifier(JSKeyword.AWAIT));
        if (!awaitToken || !isUsingIdentifierToken(nextToken)) {
            return false;
        }
        // [no LineTerminator here] between 'await' and 'using'
        if (nextToken.line() != currentToken.line()) {
            return false;
        }
        // [no LineTerminator here] between 'using' and BindingIdentifier
        // Also verify the next token is a valid BindingIdentifier start.
        Token afterUsing = lexer.peekToken();
        return isBindingIdentifierStartToken(afterUsing) && afterUsing.line() == nextToken.line();
    }

    private boolean isBindingIdentifierStartToken(Token token) {
        return switch (token.type()) {
            case IDENTIFIER, ASYNC, AWAIT, YIELD, FROM, OF, AS, LET -> true;
            default -> false;
        };
    }

    boolean isRawUseStrictDirective(Token directiveToken) {
        if (directiveToken.type() != TokenType.STRING || !JSKeyword.USE_STRICT.equals(directiveToken.value())) {
            return false;
        }

        int offset = directiveToken.offset();
        String source = lexer.getSource();
        if (offset < 0 || offset >= source.length()) {
            return false;
        }

        char quote = source.charAt(offset);
        if (quote != '\'' && quote != '"') {
            return false;
        }

        int end = offset + 1 + JSKeyword.USE_STRICT.length();
        return source.startsWith(JSKeyword.USE_STRICT, offset + 1) && end < source.length()
                && source.charAt(end) == quote;
    }

    boolean isUsingDeclarationStart() {
        return isUsingIdentifierToken(currentToken) && isBindingIdentifierStartToken(nextToken) && nextOnSameLine();
    }

    boolean isUsingIdentifierToken(Token token) {
        return token.type() == TokenType.IDENTIFIER && JSKeyword.USING.equals(token.value());
    }

    Token lookAheadAfterNext() {
        return lexer.peekToken();
    }

    boolean match(TokenType type) {
        return currentToken.type() == type;
    }

    Token next() {
        return nextToken;
    }

    boolean nextIs(TokenType type) {
        return nextToken.type() == type;
    }

    boolean nextOnSameLine() {
        return nextToken.line() == currentToken.line();
    }

    boolean nextOnSameLine(TokenType type) {
        return nextIs(type) && nextOnSameLine();
    }

    int previousEnd() {
        return previousTokenEndOffset;
    }

    void requireUnescapedToken(String message) {
        if (currentToken.escaped()) {
            throw new JSSyntaxErrorException(message);
        }
    }

    Token rescanAsRegex() {
        currentToken = lexer.rescanAsRegex(currentToken);
        nextToken = lexer.nextToken();
        return currentToken;
    }

    void restoreTokenPosition(TokenPosition position) {
        currentToken = position.current();
        nextToken = position.next();
        previousTokenLine = position.previousLine();
        previousTokenEndOffset = position.previousEnd();
        lexer.restoreState(position.lexerState());
    }

    TokenPosition saveTokenPosition() {
        return new TokenPosition(currentToken, nextToken, previousTokenLine, previousTokenEndOffset, lexer.saveState());
    }

    void setStrictMode(boolean strict) {
        lexer.setStrictMode(strict);
    }

    String source() {
        return lexer.getSource();
    }

    JSSyntaxErrorException unexpectedToken() {
        return new JSSyntaxErrorException("Unexpected token '" + currentToken.value() + "'");
    }

    static boolean isASIToken(TokenType type) {
        return switch (type) {
            case NUMBER, STRING, IDENTIFIER, INC, DEC, NULL, FALSE, TRUE, IF, RETURN, VAR, THIS, DELETE, TYPEOF, NEW,
                    DO, WHILE, FOR, SWITCH, THROW, TRY, FUNCTION, CLASS, CONST, LET ->
                true;
            default -> false;
        };
    }
    record TokenPosition(Token current, Token next, int previousLine, int previousEnd, LexerState lexerState) {
    }
}
