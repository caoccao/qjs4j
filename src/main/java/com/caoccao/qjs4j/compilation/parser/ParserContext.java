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

import com.caoccao.qjs4j.compilation.Lexer;
import com.caoccao.qjs4j.compilation.Token;
import com.caoccao.qjs4j.compilation.TokenType;
import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

/**
 * Shared mutable state for the parser.
 * Holds all fields, token utilities, validation methods, and context management
 * that are shared across the delegate parser classes.
 */
final class ParserContext {
    final boolean isEval;
    final Lexer lexer;
    final boolean moduleMode;
    int asyncFunctionNesting;
    Token currentToken;
    int functionNesting;
    boolean inDerivedConstructor;
    boolean inFunctionBody = true;
    boolean inOperatorAllowed = true;
    Token nextToken;
    boolean parsingClassWithSuper;
    int previousTokenLine;
    boolean strictMode;
    boolean superPropertyAllowed;

    ParserContext(Lexer lexer, boolean moduleMode, boolean isEval,
                  int functionNesting, int asyncFunctionNesting) {
        this.lexer = lexer;
        this.moduleMode = moduleMode;
        this.isEval = isEval;
        this.functionNesting = functionNesting;
        this.asyncFunctionNesting = asyncFunctionNesting;
        this.currentToken = lexer.nextToken();
        this.nextToken = lexer.nextToken();
    }

    // ---- Token utilities ----

    void advance() {
        previousTokenLine = currentToken.line();
        currentToken = nextToken;
        nextToken = lexer.nextToken();
    }

    void consumeSemicolon() {
        if (match(TokenType.SEMICOLON)) {
            advance();
            return;
        }
        if (hasNewlineBefore() || match(TokenType.RBRACE) || match(TokenType.EOF)) {
            return;
        }
        throw new JSSyntaxErrorException("Unexpected token '" + currentToken.value() + "'");
    }

    void enterFunctionContext(boolean asyncFunction) {
        functionNesting++;
        if (asyncFunction) {
            asyncFunctionNesting++;
        }
    }

    void exitFunctionContext(boolean asyncFunction) {
        if (asyncFunction) {
            asyncFunctionNesting--;
        }
        functionNesting--;
    }

    Token expect(TokenType type) {
        if (!match(type)) {
            throw new RuntimeException("Expected " + type + " but got " + currentToken.type() +
                    " at line " + currentToken.line() + ", column " + currentToken.column());
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

    boolean isASIToken() {
        return switch (currentToken.type()) {
            case NUMBER, STRING, IDENTIFIER,
                 INC, DEC, NULL, FALSE, TRUE,
                 IF, RETURN, VAR, THIS, DELETE, TYPEOF,
                 NEW, DO, WHILE, FOR, SWITCH, THROW,
                 TRY, FUNCTION, CLASS,
                 CONST, LET -> true;
            default -> false;
        };
    }

    // ---- Validation methods ----

    boolean isAssignmentOperator(TokenType type) {
        return type == TokenType.ASSIGN || type == TokenType.PLUS_ASSIGN ||
                type == TokenType.MINUS_ASSIGN || type == TokenType.MUL_ASSIGN ||
                type == TokenType.DIV_ASSIGN || type == TokenType.MOD_ASSIGN ||
                type == TokenType.EXP_ASSIGN || type == TokenType.LSHIFT_ASSIGN ||
                type == TokenType.RSHIFT_ASSIGN || type == TokenType.URSHIFT_ASSIGN ||
                type == TokenType.AND_ASSIGN || type == TokenType.OR_ASSIGN ||
                type == TokenType.XOR_ASSIGN || type == TokenType.LOGICAL_AND_ASSIGN ||
                type == TokenType.LOGICAL_OR_ASSIGN || type == TokenType.NULLISH_ASSIGN;
    }

    boolean isAwaitExpressionAllowed() {
        return asyncFunctionNesting > 0 || (moduleMode && functionNesting == 0);
    }

    boolean isAwaitIdentifierAllowed() {
        return !moduleMode && asyncFunctionNesting == 0;
    }

    boolean isAwaitUsingDeclarationStart() {
        return match(TokenType.AWAIT)
                && isUsingIdentifierToken(nextToken);
    }

    boolean isExpressionStartToken(TokenType tokenType) {
        return switch (tokenType) {
            case ASYNC, AWAIT, BIGINT, CLASS, FALSE, FUNCTION, IDENTIFIER,
                 LBRACE, LBRACKET, LPAREN, NEW, NULL, NUMBER, REGEX,
                 STRING, TEMPLATE, THIS, TRUE -> true;
            default -> false;
        };
    }

    boolean isIdentifierPartChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    boolean isIdentifierStartChar(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    boolean isPatternStartToken(TokenType tokenType) {
        return tokenType == TokenType.IDENTIFIER
                || tokenType == TokenType.LBRACE
                || tokenType == TokenType.LBRACKET
                || tokenType == TokenType.AWAIT;
    }

    boolean isUsingDeclarationStart() {
        return currentToken.type() == TokenType.IDENTIFIER
                && "using".equals(currentToken.value())
                && isPatternStartToken(nextToken.type());
    }

    boolean isUsingIdentifierToken(Token token) {
        return token.type() == TokenType.IDENTIFIER && "using".equals(token.value());
    }

    boolean isValidContinuationAfterAwaitIdentifier() {
        if (nextToken.line() > currentToken.line()) {
            return true;
        }
        if (!isExpressionStartToken(nextToken.type())) {
            return true;
        }
        return nextToken.type() == TokenType.LPAREN
                || nextToken.type() == TokenType.LBRACKET
                || nextToken.type() == TokenType.TEMPLATE;
    }

    boolean isValidForInOfTarget(Expression expr) {
        if (expr instanceof Identifier || expr instanceof MemberExpression) {
            return true;
        }
        return !strictMode && expr instanceof CallExpression;
    }

    boolean match(TokenType type) {
        return currentToken.type() == type;
    }

    // ---- Context management ----

    /**
     * Parse directives at the beginning of a program or function.
     * Returns true if "use strict" directive was found.
     */
    boolean parseDirectives() {
        boolean hasUseStrict = false;

        while (match(TokenType.STRING)) {
            String stringValue = currentToken.value();
            int stringLine = currentToken.line();

            Token next = peek();
            boolean hasSemi = false;

            if (next.type() == TokenType.SEMICOLON) {
                hasSemi = true;
            } else if (next.type() == TokenType.RBRACE || next.type() == TokenType.EOF) {
                hasSemi = true;
            } else {
                TokenType nextType = next.type();
                boolean isASI = switch (nextType) {
                    case NUMBER, STRING, IDENTIFIER,
                         INC, DEC, NULL, FALSE, TRUE,
                         IF, RETURN, VAR, THIS, DELETE, TYPEOF,
                         NEW, DO, WHILE, FOR, SWITCH, THROW,
                         TRY, FUNCTION, CLASS,
                         CONST, LET -> true;
                    default -> false;
                };
                if (isASI && next.line() > stringLine) {
                    hasSemi = true;
                }
            }

            if (!hasSemi) {
                break;
            }

            advance();
            if (match(TokenType.SEMICOLON)) {
                advance();
            }

            if ("use strict".equals(stringValue)) {
                hasUseStrict = true;
            }
        }

        return hasUseStrict;
    }

    Identifier parseIdentifier() {
        SourceLocation location = getLocation();
        if (match(TokenType.IDENTIFIER)) {
            String name = currentToken.value();
            advance();
            return new Identifier(name, location);
        }
        if (match(TokenType.ASYNC)) {
            String name = currentToken.value();
            advance();
            return new Identifier(name, location);
        }
        if (match(TokenType.AWAIT)) {
            if (isAwaitIdentifierAllowed()) {
                String name = currentToken.value();
                advance();
                return new Identifier(name, location);
            }
            throw new JSSyntaxErrorException("Unexpected 'await' keyword");
        }
        if (match(TokenType.FROM)) {
            String name = currentToken.value();
            advance();
            return new Identifier(name, location);
        }
        throw new RuntimeException("Expected identifier but got " + currentToken.type() +
                " at line " + currentToken.line() + ", column " + currentToken.column());
    }

    // ---- Shared parsing utilities ----

    Token peek() {
        return nextToken;
    }

    boolean peekPastParensIsArrow() {
        Token savedCurrent = currentToken;
        Token savedNext = nextToken;
        int savedPrevLine = previousTokenLine;
        Lexer.LexerState savedLexer = lexer.saveState();
        try {
            advance(); // consume '('
            int depth = 1;
            while (depth > 0 && !match(TokenType.EOF)) {
                if (match(TokenType.LPAREN)) depth++;
                else if (match(TokenType.RPAREN)) depth--;
                if (depth > 0) advance();
            }
            if (depth == 0) {
                advance(); // consume closing ')'
                return match(TokenType.ARROW);
            }
            return false;
        } finally {
            currentToken = savedCurrent;
            nextToken = savedNext;
            previousTokenLine = savedPrevLine;
            lexer.restoreState(savedLexer);
        }
    }
}
