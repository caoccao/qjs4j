package com.caoccao.qjs4j.compiler;

import com.caoccao.qjs4j.compiler.ast.ExpressionStatement;
import com.caoccao.qjs4j.compiler.ast.Literal;
import com.caoccao.qjs4j.compiler.ast.Program;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BigIntParserTest {
    @Test
    public void testParseBigIntLiteral() {
        Lexer lexer = new Lexer("123n");
        Parser parser = new Parser(lexer);
        Program program = parser.parse();

        assertEquals(1, program.body().size());
        assertInstanceOf(ExpressionStatement.class, program.body().get(0));

        var exprStmt = (com.caoccao.qjs4j.compiler.ast.ExpressionStatement) program.body().get(0);
        assertInstanceOf(Literal.class, exprStmt.expression());

        Literal literal = (Literal) exprStmt.expression();
        assertInstanceOf(BigInteger.class, literal.value());
        assertEquals(BigInteger.valueOf(123), literal.value());
    }

    @Test
    public void testParseHexBigIntLiteral() {
        Lexer lexer = new Lexer("0xFFn");
        Parser parser = new Parser(lexer);
        Program program = parser.parse();

        assertEquals(1, program.body().size());
        var exprStmt = (com.caoccao.qjs4j.compiler.ast.ExpressionStatement) program.body().get(0);
        Literal literal = (Literal) exprStmt.expression();
        assertEquals(BigInteger.valueOf(255), literal.value());
    }
}
