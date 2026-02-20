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

package com.caoccao.qjs4j.compilation;

import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexerTest {
    @Test
    void testIdentifierUnicodeEscapes() {
        Token identifierToken = new Lexer("\\u0061").nextToken();
        assertThat(identifierToken.type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(identifierToken.value()).isEqualTo("a");

        Token keywordToken = new Lexer("ret\\u0075rn").nextToken();
        assertThat(keywordToken.type()).isEqualTo(TokenType.RETURN);
        assertThat(keywordToken.value()).isEqualTo("return");
    }

    @Test
    void testInvalidIdentifierUnicodeEscapeThrows() {
        assertThatThrownBy(() -> new Lexer("\\u00G0").nextToken())
                .isInstanceOf(JSSyntaxErrorException.class);
        assertThatThrownBy(() -> new Lexer("\\u{110000}").nextToken())
                .isInstanceOf(JSSyntaxErrorException.class);
    }

    @Test
    void testLeadingDotNumberLiteral() {
        Lexer lexer = new Lexer(".5e1");
        Token token = lexer.nextToken();
        assertThat(token.type()).isEqualTo(TokenType.NUMBER);
        assertThat(token.value()).isEqualTo(".5e1");
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.EOF);
    }

    @Test
    void testPrivateIdentifierUnicodeEscape() {
        Token token = new Lexer("#\\u0061bc").nextToken();
        assertThat(token.type()).isEqualTo(TokenType.PRIVATE_NAME);
        assertThat(token.value()).isEqualTo("#abc");
    }

    @Test
    void testQuestionDotBeforeDigitIsNotOptionalChaining() {
        Lexer lexer = new Lexer("a?.3:0");
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.QUESTION);
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.NUMBER);
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.COLON);
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.NUMBER);
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.EOF);
    }

    @Test
    void testStringEscapes() {
        Token escapedToken = new Lexer("'a\\n\\x41\\u0042\\u{43}'").nextToken();
        assertThat(escapedToken.type()).isEqualTo(TokenType.STRING);
        assertThat(escapedToken.value()).isEqualTo("a\nABC");

        Token lineContinuationToken = new Lexer("'a\\\nb'").nextToken();
        assertThat(lineContinuationToken.type()).isEqualTo(TokenType.STRING);
        assertThat(lineContinuationToken.value()).isEqualTo("ab");

        Token octalToken = new Lexer("'\\123'").nextToken();
        assertThat(octalToken.type()).isEqualTo(TokenType.STRING);
        assertThat(octalToken.value()).isEqualTo("S");

        Token zeroEightToken = new Lexer("'\\08'").nextToken();
        assertThat(zeroEightToken.type()).isEqualTo(TokenType.STRING);
        assertThat(zeroEightToken.value()).isEqualTo("\0" + "8");
    }

    @Test
    void testStringInvalidEscapesThrow() {
        assertThatThrownBy(() -> new Lexer("'\\xG1'").nextToken())
                .isInstanceOf(JSSyntaxErrorException.class);
        assertThatThrownBy(() -> new Lexer("'\\u0G00'").nextToken())
                .isInstanceOf(JSSyntaxErrorException.class);
        assertThatThrownBy(() -> new Lexer("'\\u{110000}'").nextToken())
                .isInstanceOf(JSSyntaxErrorException.class);
    }

    @Test
    void testUnterminatedCommentThrows() {
        assertThatThrownBy(() -> new Lexer("/*").nextToken())
                .isInstanceOf(JSSyntaxErrorException.class);
    }

    @Test
    void testUnterminatedRegexThrows() {
        assertThatThrownBy(() -> new Lexer("/abc").nextToken())
                .isInstanceOf(JSSyntaxErrorException.class);
    }

    @Test
    void testUnterminatedTemplateThrows() {
        assertThatThrownBy(() -> new Lexer("`abc").nextToken())
                .isInstanceOf(JSSyntaxErrorException.class);
    }
}
