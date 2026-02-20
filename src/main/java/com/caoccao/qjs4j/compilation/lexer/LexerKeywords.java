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

import java.util.HashMap;
import java.util.Map;

final class LexerKeywords {
    static final Map<String, TokenType> KEYWORDS = createKeywords();

    private LexerKeywords() {
    }

    private static Map<String, TokenType> createKeywords() {
        Map<String, TokenType> keywords = new HashMap<>();
        keywords.put("as", TokenType.AS);
        keywords.put("async", TokenType.ASYNC);
        keywords.put("await", TokenType.AWAIT);
        keywords.put("break", TokenType.BREAK);
        keywords.put("case", TokenType.CASE);
        keywords.put("catch", TokenType.CATCH);
        keywords.put("class", TokenType.CLASS);
        keywords.put("const", TokenType.CONST);
        keywords.put("continue", TokenType.CONTINUE);
        keywords.put("default", TokenType.DEFAULT);
        keywords.put("delete", TokenType.DELETE);
        keywords.put("do", TokenType.DO);
        keywords.put("else", TokenType.ELSE);
        keywords.put("export", TokenType.EXPORT);
        keywords.put("extends", TokenType.EXTENDS);
        keywords.put("false", TokenType.FALSE);
        keywords.put("finally", TokenType.FINALLY);
        keywords.put("for", TokenType.FOR);
        keywords.put("from", TokenType.FROM);
        keywords.put("function", TokenType.FUNCTION);
        keywords.put("if", TokenType.IF);
        keywords.put("import", TokenType.IMPORT);
        keywords.put("in", TokenType.IN);
        keywords.put("instanceof", TokenType.INSTANCEOF);
        keywords.put("let", TokenType.LET);
        keywords.put("new", TokenType.NEW);
        keywords.put("null", TokenType.NULL);
        keywords.put("of", TokenType.OF);
        keywords.put("return", TokenType.RETURN);
        keywords.put("super", TokenType.SUPER);
        keywords.put("switch", TokenType.SWITCH);
        keywords.put("this", TokenType.THIS);
        keywords.put("throw", TokenType.THROW);
        keywords.put("true", TokenType.TRUE);
        keywords.put("try", TokenType.TRY);
        keywords.put("typeof", TokenType.TYPEOF);
        keywords.put("var", TokenType.VAR);
        keywords.put("void", TokenType.VOID);
        keywords.put("while", TokenType.WHILE);
        keywords.put("yield", TokenType.YIELD);
        return keywords;
    }
}
