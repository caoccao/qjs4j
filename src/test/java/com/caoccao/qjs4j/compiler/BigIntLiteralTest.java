package com.caoccao.qjs4j.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BigIntLiteralTest {
    @Test
    public void testLexerBigInt() {
        Lexer lexer = new Lexer("123n");
        Token token = lexer.nextToken();
        assertEquals(TokenType.BIGINT, token.type());
        assertEquals("123", token.value());
    }

    @Test
    public void testLexerBinaryBigInt() {
        Lexer lexer = new Lexer("0b1111n");
        Token token = lexer.nextToken();
        assertEquals(TokenType.BIGINT, token.type());
        assertEquals("0b1111", token.value());
    }

    @Test
    public void testLexerHexBigInt() {
        Lexer lexer = new Lexer("0xFFn");
        Token token = lexer.nextToken();
        assertEquals(TokenType.BIGINT, token.type());
        assertEquals("0xFF", token.value());
    }

    @Test
    public void testLexerOctalBigInt() {
        Lexer lexer = new Lexer("0o77n");
        Token token = lexer.nextToken();
        assertEquals(TokenType.BIGINT, token.type());
        assertEquals("0o77", token.value());
    }
}
