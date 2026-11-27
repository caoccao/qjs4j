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
    AND_ASSIGN,
    ARROW,
    AS(true, true),
    ASSIGN,
    ASYNC(true, true),
    AT,
    AWAIT(true, true),

    BIGINT,
    BIT_AND,
    BIT_NOT,
    BIT_OR,
    BIT_XOR,
    BREAK(true),

    CASE(true),
    CATCH(true),
    CLASS(true),
    COLON,
    COMMA,
    CONST(true),
    CONTINUE(true),

    DEC,
    DEFAULT(true),
    DELETE(true),
    DIV,
    DIV_ASSIGN,
    DO(true),
    DOT,

    ELLIPSIS,
    ELSE(true),
    EOF,
    EQ,
    EXP,
    EXP_ASSIGN,
    EXPORT(true),
    EXTENDS(true),

    FALSE(true),
    FINALLY(true),
    FOR(true),
    FROM(true, true),
    FUNCTION(true),

    GE,
    GT,

    HASH,

    IDENTIFIER(true, true),
    IF(true),
    IMPORT(true),
    IN(true),
    INC,
    INSTANCEOF(true),

    LBRACE,
    LBRACKET,
    LE,
    LET(true, true),
    LOGICAL_AND,
    LOGICAL_AND_ASSIGN,
    LOGICAL_OR,
    LOGICAL_OR_ASSIGN,
    LPAREN,
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
    NEW(true),
    NOT,
    NULL(true),
    NULLISH_ASSIGN,
    NULLISH_COALESCING,
    NUMBER,

    OF(true, true),
    OPTIONAL_CHAINING,
    OR_ASSIGN,

    PLUS,
    PLUS_ASSIGN,
    /** #identifier for private class fields */
    PRIVATE_NAME,

    QUESTION,

    RBRACE,
    RBRACKET,
    REGEX,
    RETURN(true),
    RPAREN,
    RSHIFT,
    RSHIFT_ASSIGN,

    SEMICOLON,
    STRICT_EQ,
    STRICT_NE,
    STRING,
    SUPER(true),
    SWITCH(true),

    TEMPLATE,
    THIS(true),
    THROW(true),
    TRUE(true),
    TRY(true),
    TYPEOF(true),

    URSHIFT,
    URSHIFT_ASSIGN,

    VAR(true),
    VOID(true),

    WHILE(true),

    XOR_ASSIGN,

    YIELD(true, true);

    private final boolean identifierCandidate;
    private final boolean identifierName;

    TokenType() {
        this(false);
    }

    TokenType(boolean identifierName) {
        this(identifierName, false);
    }

    TokenType(boolean identifierName, boolean identifierCandidate) {
        this.identifierCandidate = identifierCandidate;
        this.identifierName = identifierName;
    }

    /** A token that can start a label or an async arrow parameter, subject to contextual restrictions. */
    public boolean isIdentifierCandidate() {
        return identifierCandidate;
    }

    public boolean isIdentifierName() {
        return identifierName;
    }
}
