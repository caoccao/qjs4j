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
    BIGINT,
    EOF,
    IDENTIFIER,
    NUMBER,
    REGEX,
    STRING,
    TEMPLATE,

    // Keywords
    AS,
    ASYNC,
    AWAIT,
    BREAK,
    CASE,
    CATCH,
    CLASS,
    CONST,
    CONTINUE,
    DEFAULT,
    DELETE,
    DO,
    ELSE,
    EXPORT,
    EXTENDS,
    FALSE,
    FINALLY,
    FOR,
    FROM,
    FUNCTION,
    IF,
    IMPORT,
    IN,
    INSTANCEOF,
    LET,
    NEW,
    NULL,
    OF,
    RETURN,
    SUPER,
    SWITCH,
    THIS,
    THROW,
    TRUE,
    TRY,
    TYPEOF,
    VAR,
    VOID,
    WHILE,
    YIELD,

    // Operators
    AND_ASSIGN,
    ASSIGN,
    BIT_AND,
    BIT_NOT,
    BIT_OR,
    BIT_XOR,
    DEC,
    DIV,
    DIV_ASSIGN,
    EQ,
    EXP,
    EXP_ASSIGN,
    GE,
    GT,
    INC,
    LE,
    LOGICAL_AND,
    LOGICAL_AND_ASSIGN,
    LOGICAL_OR,
    LOGICAL_OR_ASSIGN,
    LSHIFT,
    LSHIFT_ASSIGN,
    LT,
    MINUS,
    MINUS_ASSIGN,
    MOD,
    MOD_ASSIGN,
    MUL,
    MUL_ASSIGN,
    NE,
    NOT,
    NULLISH_ASSIGN,
    NULLISH_COALESCING,
    OR_ASSIGN,
    PLUS,
    PLUS_ASSIGN,
    RSHIFT,
    RSHIFT_ASSIGN,
    STRICT_EQ,
    STRICT_NE,
    URSHIFT,
    URSHIFT_ASSIGN,
    XOR_ASSIGN,

    // Punctuation
    ARROW,
    COLON,
    COMMA,
    DOT,
    ELLIPSIS,
    HASH,
    LBRACE,
    LBRACKET,
    LPAREN,
    OPTIONAL_CHAINING,
    QUESTION,
    RBRACE,
    RBRACKET,
    RPAREN,
    SEMICOLON,

    // Special
    PRIVATE_NAME,  // #identifier for private class fields
}
