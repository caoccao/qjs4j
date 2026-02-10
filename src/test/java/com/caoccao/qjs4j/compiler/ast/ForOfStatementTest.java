package com.caoccao.qjs4j.compiler.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simplest possible for-of test.
 */
public class ForOfStatementTest extends BaseJavetTest {
    @Test
    void testForOfLetClosurePerIterationBinding() {
        assertStringWithJavet("""
                var funcs = [];
                for (let x of [0, 1, 2]) {
                  funcs.push(() => x);
                }
                funcs.map(f => f()).join(',')""");
    }

    @Test
    void testIteratorCloseOnBreak() {
        assertBooleanWithJavet("""
                var closed = false;
                function* g() {
                  try {
                    yield 1;
                    yield 2;
                  } finally {
                    closed = true;
                  }
                }
                for (const x of g()) {
                  break;
                }
                closed""");
    }

    @Test
    void testIteratorCloseOnReturn() {
        assertBooleanWithJavet("""
                var closed = false;
                function* g() {
                  try {
                    yield 1;
                    yield 2;
                  } finally {
                    closed = true;
                  }
                }
                (function () {
                  for (const x of g()) {
                    return;
                  }
                })();
                closed""");
    }

    @Test
    void testIteratorCloseOnThrow() {
        assertBooleanWithJavet("""
                var closed = false;
                function* g() {
                  try {
                    yield 1;
                    yield 2;
                  } finally {
                    closed = true;
                  }
                }
                try {
                  for (const x of g()) {
                    throw new Error('x');
                  }
                } catch (e) {
                }
                closed""");
    }

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
    void testArrayOfArraysWithDestructuring() {
        // for-of with array destructuring over an array of arrays
        assertStringWithJavet("""
                var result = '';
                for (var [k, v] of [[1, 'a'], [2, 'b']]) result += k + v;
                result""");
    }

    @Test
    void testForOfArrayDestructuring() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    var result = '';
                    for (var [k, v] of [[1, 'a'], [2, 'b']]) result += k + v;
                    result""";
            JSValue value = context.eval(code);
            String result = value.toString();
            System.out.println("Result: " + result);
            assertThat(result).isEqualTo("1a2b");
        }
    }

    @Test
    void testForOfInFunction() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    function test() {
                      var result = '';
                      for (var x of ['a', 'b', 'c']) result += x;
                      return result;
                    }
                    test()""";
            JSValue value = context.eval(code);
            String result = value.toString();
            System.out.println("For-of in function: " + result);
            assertThat(result).isEqualTo("abc");
        }
    }

    @Test
    void testForOfInGlobalScope() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    var result = '';
                    for (var x of ['a', 'b', 'c']) result += x;
                    result""";
            JSValue value = context.eval(code);
            String result = value.toString();
            System.out.println("For-of in global: " + result);
            assertThat(result).isEqualTo("abc");
        }
    }

    @Test
    void testForOfSimpleArray() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    var result = '';
                    for (var x of ['a', 'b', 'c']) result += x;
                    result""";
            JSValue value = context.eval(code);
            String result = value.toString();
            System.out.println("Result: " + result);
            assertThat(result).isEqualTo("abc");
        }
    }

    @Test
    void testSimpleArrayIteration() {
        // Simple for-of with single variable (no destructuring)
        assertStringWithJavet("""
                var result = '';
                for (var v of ['a', 'b', 'c']) result += v;
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
