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

import com.caoccao.qjs4j.core.JSKeyword;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of all JavaScript token types.
 */
public enum TokenType {
    AND_ASSIGN("&="),
    ARROW("=>"),
    AS(JSKeyword.AS, true),
    ASSIGN("="),
    ASYNC(JSKeyword.ASYNC, true),
    AT,
    AWAIT(JSKeyword.AWAIT, true),

    BIGINT,
    BIT_AND("&"),
    BIT_NOT("~"),
    BIT_OR("|"),
    BIT_XOR("^"),
    BREAK(JSKeyword.BREAK),

    CASE(JSKeyword.CASE),
    CATCH(JSKeyword.CATCH),
    CLASS(JSKeyword.CLASS),
    COLON(":"),
    COMMA(","),
    CONST(JSKeyword.CONST),
    CONTINUE(JSKeyword.CONTINUE),

    DEC("--"),
    DEFAULT(JSKeyword.DEFAULT),
    DELETE(JSKeyword.DELETE),
    DIV("/"),
    DIV_ASSIGN("/="),
    DO(JSKeyword.DO),
    DOT("."),

    ELLIPSIS("..."),
    ELSE(JSKeyword.ELSE),
    EOF,
    EQ("=="),
    EXP("**"),
    EXP_ASSIGN("**="),
    EXPORT(JSKeyword.EXPORT),
    EXTENDS(JSKeyword.EXTENDS),

    FALSE(JSKeyword.FALSE),
    FINALLY(JSKeyword.FINALLY),
    FOR(JSKeyword.FOR),
    FROM(JSKeyword.FROM, true),
    FUNCTION(JSKeyword.FUNCTION),

    GE(">="),
    GT(">"),

    HASH,

    IDENTIFIER(null, true),
    IF(JSKeyword.IF),
    IMPORT(JSKeyword.IMPORT),
    IN(JSKeyword.IN),
    INC("++"),
    INSTANCEOF(JSKeyword.INSTANCEOF),

    LBRACE("{"),
    LBRACKET("["),
    LE("<="),
    LET(JSKeyword.LET, true),
    LOGICAL_AND("&&"),
    LOGICAL_AND_ASSIGN("&&="),
    LOGICAL_OR("||"),
    LOGICAL_OR_ASSIGN("||="),
    LPAREN("("),
    LSHIFT("<<"),

    LSHIFT_ASSIGN("<<="),
    LT("<"),

    MINUS("-"),
    MINUS_ASSIGN("-="),
    MOD("%"),
    MOD_ASSIGN("%="),
    MUL("*"),
    MUL_ASSIGN("*="),

    NE("!="),
    NEW(JSKeyword.NEW),
    NOT("!"),
    NULL(JSKeyword.NULL),
    NULLISH_ASSIGN("??="),
    NULLISH_COALESCING("??"),
    NUMBER,

    OF(JSKeyword.OF, true),
    OPTIONAL_CHAINING("?."),
    OR_ASSIGN("|="),

    PLUS("+"),
    PLUS_ASSIGN("+="),
    /** #identifier for private class fields */
    PRIVATE_NAME,

    QUESTION("?"),

    RBRACE("}"),
    RBRACKET("]"),
    REGEX,
    RETURN(JSKeyword.RETURN),
    RPAREN(")"),
    RSHIFT(">>"),
    RSHIFT_ASSIGN(">>="),

    SEMICOLON(";"),
    STRICT_EQ("==="),
    STRICT_NE("!=="),
    STRING,
    SUPER(JSKeyword.SUPER),
    SWITCH(JSKeyword.SWITCH),

    TEMPLATE,
    THIS(JSKeyword.THIS),
    THROW(JSKeyword.THROW),
    TRUE(JSKeyword.TRUE),
    TRY(JSKeyword.TRY),
    TYPEOF(JSKeyword.TYPEOF),

    URSHIFT(">>>"),
    URSHIFT_ASSIGN(">>>="),

    VAR(JSKeyword.VAR),
    VOID(JSKeyword.VOID),

    WHILE(JSKeyword.WHILE),

    XOR_ASSIGN("^="),

    YIELD(JSKeyword.YIELD, true);

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    private static final TokenType[] SINGLE_PUNCTUATORS = new TokenType[128];

    static {
        Map<String, TokenType> punctuators = new HashMap<>();
        for (TokenType token : values()) {
            if (token.fixedText == null) {
                continue;
            }
            if (token.identifierName) {
                KEYWORDS.put(token.fixedText, token);
            } else {
                punctuators.put(token.fixedText, token);
            }
        }
        for (TokenType token : punctuators.values()) {
            String text = token.fixedText;
            if (text.length() == 1) {
                SINGLE_PUNCTUATORS[text.charAt(0)] = token;
            } else if (token != ELLIPSIS) {
                // The lexer handles dot sequences along with fractional numeric literals.
                TokenType prefix = punctuators.get(text.substring(0, text.length() - 1));
                if (prefix.continuations == null) {
                    prefix.continuations = new TokenType[128];
                }
                prefix.continuations[text.charAt(text.length() - 1)] = token;
            }
        }
    }

    private TokenType[] continuations;
    private final String fixedText;
    private final boolean identifierCandidate;
    private final boolean identifierName;

    TokenType() {
        this(null, false);
    }

    TokenType(String fixedText) {
        this(fixedText, false);
    }

    TokenType(String fixedText, boolean identifierCandidate) {
        this.identifierCandidate = identifierCandidate;
        this.fixedText = fixedText;
        this.identifierName = identifierCandidate || (fixedText != null && Character.isLetter(fixedText.charAt(0)));
    }

    /** The canonical spelling of a keyword or punctuator. */
    public String fixedText() {
        return fixedText;
    }

    /** A token that can start a label or an async arrow parameter, subject to contextual restrictions. */
    public boolean isIdentifierCandidate() {
        return identifierCandidate;
    }

    public boolean isIdentifierName() {
        return identifierName;
    }

    static TokenType keywordOrIdentifier(String value) {
        return KEYWORDS.getOrDefault(value, IDENTIFIER);
    }

    static TokenType punctuator(String source, int offset) {
        char first = source.charAt(offset);
        TokenType token = first < SINGLE_PUNCTUATORS.length ? SINGLE_PUNCTUATORS[first] : null;
        int next = offset + 1;
        while (token != null && token.continuations != null && next < source.length()) {
            char c = source.charAt(next);
            TokenType longer = c < token.continuations.length ? token.continuations[c] : null;
            if (longer == null) {
                break;
            }
            if (longer == OPTIONAL_CHAINING && next + 1 < source.length()
                    && Character.isDigit(source.charAt(next + 1))) {
                break;
            }
            token = longer;
            next++;
        }
        return token;
    }
}
