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

package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.compilation.Lexer;
import com.caoccao.qjs4j.compilation.Parser;
import com.caoccao.qjs4j.compilation.Token;
import com.caoccao.qjs4j.compilation.TokenType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class LiteralTest extends BaseJavetTest {
    @Test
    public void testBinaryNumbers() {
        assertIntegerWithJavet(
                "0b0",
                "0b1",
                "0b10",
                "0b1111",
                "0B0",
                "0B1010");
    }

    @Test
    public void testDecimalNumbers() {
        assertIntegerWithJavet(
                "1.0");
        assertDoubleWithJavet(
                "0.5",
                "123.456",
                "3.14159");
    }

    @Test
    public void testExponentNumbers() {
        assertIntegerWithJavet(
                "1e3",
                "1.5e3",
                "1E3",
                "1e+3",
                "0.1e10");
        assertDoubleWithJavet(
                "1e-3",
                "2.5E-2");
    }

    @Test
    public void testHexNumbers() {
        assertIntegerWithJavet(
                "0x0",
                "0x1",
                "0xF",
                "0xFF",
                "0x123",
                "0xABC",
                "0xabc",
                "0X0",
                "0XFF");
    }

    @Test
    public void testIntegerNumbers() {
        assertIntegerWithJavet(
                "0",
                "1",
                "42",
                "123",
                "999");
    }

    @Test
    public void testInvalidBinaryNumbers() {
        assertErrorWithJavet(
                "0b",
                "0bn",
                "0b2",
                "0bg");
    }

    @Test
    public void testInvalidDecimalNumbers() {
        assertErrorWithJavet(
                "1..",
                "1e",
                "1E",
                "1e+",
                "1e-");
    }

    @Test
    public void testInvalidHexNumbers() {
        assertErrorWithJavet(
                "0x",
                "0xn",
                "0xg",
                "0xGG");
    }

    @Test
    public void testInvalidOctalNumbers() {
        assertErrorWithJavet(
                "0o",
                "0on",
                "0o8",
                "0o9",
                "0og");
    }

    @Test
    public void testLexerBigInt() {
        Lexer lexer = new Lexer("123n");
        Token token = lexer.nextToken();
        assertThat(token.type()).isEqualTo(TokenType.BIGINT);
        assertThat(token.value()).isEqualTo("123");

        assertLongWithJavet(
                "123n",
                "123.456n",
                "1abcn",
                "1en",
                "1e3n");
    }

    @Test
    public void testLexerBinaryBigInt() {
        Lexer lexer = new Lexer("0b1111n");
        Token token = lexer.nextToken();
        assertThat(token.type()).isEqualTo(TokenType.BIGINT);
        assertThat(token.value()).isEqualTo("0b1111");

        assertLongWithJavet(
                "0b1010n",
                "0b123n",
                "0b1.0n",
                "0babcn",
                "0b1e1n");
    }

    @Test
    public void testLexerHexBigInt() {
        Lexer lexer = new Lexer("0xFFn");
        Token token = lexer.nextToken();
        assertThat(token.type()).isEqualTo(TokenType.BIGINT);
        assertThat(token.value()).isEqualTo("0xFF");

        assertLongWithJavet(
                "0x1234n",
                "0x123n",
                "0x1.0n",
                "0xabcn",
                "0xABCn",
                "0xxyxn",
                "0x1e1n");
    }

    @Test
    public void testLexerOctalBigInt() {
        Lexer lexer = new Lexer("0o77n");
        Token token = lexer.nextToken();
        assertThat(token.type()).isEqualTo(TokenType.BIGINT);
        assertThat(token.value()).isEqualTo("0o77");

        assertLongWithJavet(
                "0o1234n",
                "0o123n",
                "0o1.0n",
                "0oabcn",
                "0oABCn",
                "0oxyxn",
                "0o1e1n");
    }

    @Test
    public void testNegativeNumbers() {
        assertIntegerWithJavet(
                "-1",
                "-1e3");
        assertDoubleWithJavet(
                "-0.5",
                "-123.456",
                "-1.5e-2");
    }

    @Test
    public void testNumbersWithIdentifiers() {
        assertErrorWithJavet(
                "123abc",
                "456xyz");
    }

    @Test
    public void testOctalNumbers() {
        assertIntegerWithJavet(
                "0o0",
                "0o1",
                "0o7",
                "0o77",
                "0o123",
                "0o567",
                "0O0",
                "0O77");
    }

    @Test
    public void testParseBigIntLiteral() {
        Lexer lexer = new Lexer("123n");
        Parser parser = new Parser(lexer);
        Program program = parser.parse();

        assertThat(program.body()).hasSize(1);
        assertThat(program.body().get(0)).isInstanceOfSatisfying(ExpressionStatement.class, exprStmt -> {
            assertThat(exprStmt.expression()).isInstanceOfSatisfying(Literal.class, literal -> {
                assertThat(literal.value()).isInstanceOf(BigInteger.class);
                assertThat(literal.value()).isEqualTo(BigInteger.valueOf(123));
            });
        });
    }

    @Test
    public void testParseHexBigIntLiteral() {
        Lexer lexer = new Lexer("0xFFn");
        Parser parser = new Parser(lexer);
        Program program = parser.parse();

        assertThat(program.body()).hasSize(1);
        assertThat(program.body().get(0)).isInstanceOfSatisfying(ExpressionStatement.class, exprStmt -> {
            assertThat(exprStmt.expression()).isInstanceOfSatisfying(Literal.class, literal ->
                    assertThat(literal.value()).isEqualTo(BigInteger.valueOf(255)));
        });
    }

    @Test
    public void testValidBigIntLiterals() {
        assertLongWithJavet(
                "0n",
                "0b1111111111111111111111111111111n");
        assertBigIntegerWithJavet(
                "999999999999999999999999999999n",
                "0o777777777777777777777777777n",
                "0xFFFFFFFFFFFFFFFFFFFFFFFFFFn");
    }

    @Test
    public void testZeroVariants() {
        assertIntegerWithJavet(
                "0",
                "0x0",
                "0b0",
                "0o0",
                "0.0",
                "0e0");
    }
}
