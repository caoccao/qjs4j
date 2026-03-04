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

final class LexerKeywords {
    static final Map<String, TokenType> KEYWORDS = createKeywords();

    private LexerKeywords() {
    }

    private static Map<String, TokenType> createKeywords() {
        Map<String, TokenType> keywords = new HashMap<>();
        keywords.put(JSKeyword.AS, TokenType.AS);
        keywords.put(JSKeyword.ASYNC, TokenType.ASYNC);
        keywords.put(JSKeyword.AWAIT, TokenType.AWAIT);
        keywords.put(JSKeyword.BREAK, TokenType.BREAK);
        keywords.put(JSKeyword.CASE, TokenType.CASE);
        keywords.put(JSKeyword.CATCH, TokenType.CATCH);
        keywords.put(JSKeyword.CLASS, TokenType.CLASS);
        keywords.put(JSKeyword.CONST, TokenType.CONST);
        keywords.put(JSKeyword.CONTINUE, TokenType.CONTINUE);
        keywords.put(JSKeyword.DEFAULT, TokenType.DEFAULT);
        keywords.put(JSKeyword.DELETE, TokenType.DELETE);
        keywords.put(JSKeyword.DO, TokenType.DO);
        keywords.put(JSKeyword.ELSE, TokenType.ELSE);
        keywords.put(JSKeyword.EXPORT, TokenType.EXPORT);
        keywords.put(JSKeyword.EXTENDS, TokenType.EXTENDS);
        keywords.put(JSKeyword.FALSE, TokenType.FALSE);
        keywords.put(JSKeyword.FINALLY, TokenType.FINALLY);
        keywords.put(JSKeyword.FOR, TokenType.FOR);
        keywords.put(JSKeyword.FROM, TokenType.FROM);
        keywords.put(JSKeyword.FUNCTION, TokenType.FUNCTION);
        keywords.put(JSKeyword.IF, TokenType.IF);
        keywords.put(JSKeyword.IMPORT, TokenType.IMPORT);
        keywords.put(JSKeyword.IN, TokenType.IN);
        keywords.put(JSKeyword.INSTANCEOF, TokenType.INSTANCEOF);
        keywords.put(JSKeyword.LET, TokenType.LET);
        keywords.put(JSKeyword.NEW, TokenType.NEW);
        keywords.put(JSKeyword.NULL, TokenType.NULL);
        keywords.put(JSKeyword.OF, TokenType.OF);
        keywords.put(JSKeyword.RETURN, TokenType.RETURN);
        keywords.put(JSKeyword.SUPER, TokenType.SUPER);
        keywords.put(JSKeyword.SWITCH, TokenType.SWITCH);
        keywords.put(JSKeyword.THIS, TokenType.THIS);
        keywords.put(JSKeyword.THROW, TokenType.THROW);
        keywords.put(JSKeyword.TRUE, TokenType.TRUE);
        keywords.put(JSKeyword.TRY, TokenType.TRY);
        keywords.put(JSKeyword.TYPEOF, TokenType.TYPEOF);
        keywords.put(JSKeyword.VAR, TokenType.VAR);
        keywords.put(JSKeyword.VOID, TokenType.VOID);
        keywords.put(JSKeyword.WHILE, TokenType.WHILE);
        keywords.put(JSKeyword.YIELD, TokenType.YIELD);
        return keywords;
    }
}
