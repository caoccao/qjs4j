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

import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexerTest {
    /**
     * The token types a source lexes to, without the terminating EOF.
     *
     * @param source the source
     * @return the token types
     */
    private static List<TokenType> tokenTypes(String source) {
        Lexer lexer = new Lexer(source);
        List<TokenType> types = new ArrayList<>();
        while (true) {
            Token token = lexer.nextToken();
            if (token == null || token.type() == TokenType.EOF) {
                return types;
            }
            types.add(token.type());
        }
    }

    @Test
    void testDefaultAsAPropertyNameIsFollowedByDivision() {
        // `default` is the one keyword that is also a legal property name, and there the '/' is
        // division. Reading it as a regular expression would swallow the rest of the line.
        assertThat(tokenTypes("x.default / 2"))
                .containsExactly(TokenType.IDENTIFIER, TokenType.DOT, TokenType.DEFAULT,
                        TokenType.DIV, TokenType.NUMBER);
        assertThat(tokenTypes("x?.default / 2"))
                .containsExactly(TokenType.IDENTIFIER, TokenType.OPTIONAL_CHAINING, TokenType.DEFAULT,
                        TokenType.DIV, TokenType.NUMBER);
    }

    @Test
    void testExportDefaultIsFollowedByARegularExpression() {
        // `export default` takes an AssignmentExpression, so a '/' after it opens a regular
        // expression. It was read as division, and `export default /\(/;` did not lex at all
        // because the escape after it is not the start of an identifier.
        assertThat(tokenTypes("export default /a/;"))
                .containsExactly(TokenType.EXPORT, TokenType.DEFAULT, TokenType.REGEX,
                        TokenType.SEMICOLON);
        Lexer lexer = new Lexer("export default /\\(/;");
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.EXPORT);
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.DEFAULT);
        Token regexToken = lexer.nextToken();
        assertThat(regexToken.type()).isEqualTo(TokenType.REGEX);
        assertThat(regexToken.value()).isEqualTo("/\\(/");
    }

    @Test
    void testIdentifierUnicodeEscapes() {
        Token identifierToken = new Lexer("\\u0061").nextToken();
        assertThat(identifierToken.type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(identifierToken.value()).isEqualTo("a");

        // ES2024 12.7.1: An IdentifierName containing Unicode escape sequences
        // cannot be a keyword — it is always tokenized as IDENTIFIER.
        Token escapedKeywordToken = new Lexer("ret\\u0075rn").nextToken();
        assertThat(escapedKeywordToken.type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(escapedKeywordToken.value()).isEqualTo("return");
        assertThat(escapedKeywordToken.escaped()).isTrue();
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
    void testSwitchDefaultIsStillFollowedByARegularExpression() {
        // The colon separates the clause from what follows, so this went through a different branch
        // already and must keep working.
        assertThat(tokenTypes("switch (x) { default: /a/; }"))
                .containsExactly(TokenType.SWITCH, TokenType.LPAREN, TokenType.IDENTIFIER,
                        TokenType.RPAREN, TokenType.LBRACE, TokenType.DEFAULT, TokenType.COLON,
                        TokenType.REGEX, TokenType.SEMICOLON, TokenType.RBRACE);
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
