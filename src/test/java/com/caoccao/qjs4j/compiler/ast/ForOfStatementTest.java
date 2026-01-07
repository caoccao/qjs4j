package com.caoccao.qjs4j.compiler.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Simplest possible for-of test.
 */
public class ForOfStatementTest extends BaseJavetTest {
    @Test
    void testArrayForOf() {
        // Compare with array
        assertStringWithJavet(
                """
                        var arr = ['a'];
                        var result = 'none';
                        for (let x of arr) { result = x; }
                        result""");
    }

    @Test
    void testSimpleForOf() {
        // Absolute simplest for-of test
        assertStringWithJavet(
                """
                        var s = new Set(['a']);
                        var result = 'none';
                        for (let x of s) { result = x; }
                        result""");
    }

    @Test
    void testSimpleForOfTypeof() {
        // Check the type
        assertStringWithJavet(
                """
                        var s = new Set(['a']);
                        var result = 'none';
                        for (let x of s) { result = typeof x; }
                        result""");
    }
}
