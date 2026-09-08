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

/**
 * Enumeration of all JavaScript token types.
 */
public enum TokenType {
    // Operators
    AND_ASSIGN, ARROW,
    // Keywords
    AS, ASSIGN, ASYNC,
    // Punctuation
    AT, AWAIT,

    BIGINT, BIT_AND, BIT_NOT, BIT_OR, BIT_XOR, BREAK, CASE, CATCH, CLASS, COLON, COMMA, CONST, CONTINUE, DEC, DEFAULT, DELETE, DIV, DIV_ASSIGN, DO, DOT, ELLIPSIS, ELSE, EOF, EQ, EXP, EXP_ASSIGN, EXPORT, EXTENDS, FALSE, FINALLY, FOR, FROM, FUNCTION, GE, GT, HASH, IDENTIFIER, IF, IMPORT, IN,

    INC, INSTANCEOF, LBRACE, LBRACKET, LE, LET, LOGICAL_AND, LOGICAL_AND_ASSIGN, LOGICAL_OR, LOGICAL_OR_ASSIGN, LPAREN, LSHIFT, LSHIFT_ASSIGN, LT, MINUS, MINUS_ASSIGN, MOD, MOD_ASSIGN, MUL, MUL_ASSIGN, NE, NEW, NOT, NULL, NULLISH_ASSIGN, NULLISH_COALESCING, NUMBER, OF, OPTIONAL_CHAINING, OR_ASSIGN, PLUS, PLUS_ASSIGN,
    // Special
    /** #identifier for private class fields */
    PRIVATE_NAME, QUESTION, RBRACE, RBRACKET, REGEX, RETURN, RPAREN, RSHIFT, RSHIFT_ASSIGN, SEMICOLON, STRICT_EQ,

    STRICT_NE, STRING, SUPER, SWITCH, TEMPLATE, THIS, THROW, TRUE, TRY, TYPEOF, URSHIFT, URSHIFT_ASSIGN, VAR, VOID, WHILE, XOR_ASSIGN,

    YIELD,
}
