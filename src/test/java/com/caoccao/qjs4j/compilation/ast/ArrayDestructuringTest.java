package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Test cases for array destructuring assignment.
 * Destructuring allows unpacking values from arrays into distinct variables.
 */
public class ArrayDestructuringTest extends BaseJavetTest {

    @Test
    public void testArrayDestructuringWithRest() {
        assertIntegerWithJavet(
                """
                        const [a, ...rest] = [1, 2, 3];
                        rest.length""");
    }

    @Test
    public void testArrayDestructuringWithRestSum() {
        assertIntegerWithJavet(
                """
                        const [first, ...rest] = [1, 2, 3, 4];
                        first + rest.reduce((a, b) => a + b, 0)""");
    }

    @Test
    public void testArrayDestructuringWithSkip() {
        assertIntegerWithJavet(
                """
                        const [a, , c] = [1, 2, 3];
                        a + c""");
    }

    @Test
    public void testNestedArrayDestructuring() {
        assertIntegerWithJavet(
                """
                        const [a, [b, c]] = [1, [2, 3]];
                        a + b + c""");
    }

    @Test
    public void testSimpleArrayDestructuring() {
        assertIntegerWithJavet(
                """
                        const [a, b] = [1, 2];
                        a + b""");
    }
}
