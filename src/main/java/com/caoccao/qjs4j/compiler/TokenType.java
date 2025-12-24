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

/**
 * Enumeration of all JavaScript token types.
 */
public enum TokenType {
    EOF,
    IDENTIFIER,
    NUMBER,
    STRING,
    TEMPLATE,

    // Keywords
    FUNCTION,
    VAR,
    LET,
    CONST,
    IF,
    ELSE,
    FOR,
    WHILE,
    DO,
    RETURN,
    BREAK,
    CONTINUE,
    SWITCH,
    CASE,
    DEFAULT,
    TRY,
    CATCH,
    FINALLY,
    THROW,
    CLASS,
    EXTENDS,
    SUPER,
    NEW,
    THIS,
    IMPORT,
    EXPORT,
    FROM,
    AS,
    ASYNC,
    AWAIT,
    YIELD,
    TYPEOF,
    INSTANCEOF,
    VOID,
    DELETE,
    IN,
    OF,

    // Operators
    PLUS,
    MINUS,
    MUL,
    DIV,
    MOD,
    EXP,
    EQ,
    NE,
    STRICT_EQ,
    STRICT_NE,
    LT,
    LE,
    GT,
    GE,
    ASSIGN,
    PLUS_ASSIGN,
    MINUS_ASSIGN,
    MUL_ASSIGN,
    DIV_ASSIGN,
    MOD_ASSIGN,
    EXP_ASSIGN,
    AND_ASSIGN,
    OR_ASSIGN,
    XOR_ASSIGN,
    LSHIFT_ASSIGN,
    RSHIFT_ASSIGN,
    URSHIFT_ASSIGN,
    LOGICAL_AND,
    LOGICAL_OR,
    NULLISH_COALESCING,
    BIT_AND,
    BIT_OR,
    BIT_XOR,
    BIT_NOT,
    LSHIFT,
    RSHIFT,
    URSHIFT,
    INC,
    DEC,
    NOT,

    // Punctuation
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    LBRACKET,
    RBRACKET,
    SEMICOLON,
    COMMA,
    DOT,
    QUESTION,
    COLON,
    ARROW,
    ELLIPSIS,
    OPTIONAL_CHAINING
}
