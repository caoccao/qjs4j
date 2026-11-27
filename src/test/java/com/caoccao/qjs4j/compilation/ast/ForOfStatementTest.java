package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simplest possible for-of test.
 */
public class ForOfStatementTest extends BaseJavetTest {
    private static final String CLOSABLE_GENERATOR = """
            var closed = false;
            function* g() {
              try {
                yield 1;
                yield 2;
              } finally {
                closed = true;
              }
            }
            """;
    private static final String DESTRUCTURING_LOOP = """
            var result = '';
            for (var [k, v] of [[1, 'a'], [2, 'b']]) result += k + v;
            result""";
    private static final String GLOBAL_LOOP = """
            var result = '';
            for (var x of ['a', 'b', 'c']) result += x;
            result""";
    @Override
    protected JSRuntime createRuntime() {
        return new JSRuntime();
    }

    @Test
    void testArrayForOf() {
        // Compare with array
        assertStringWithJavet("""
                var arr = ['a'];
                var result = 'none';
                for (let x of arr) { result = x; }
                result""");
    }

    @Test
    void testArrayOfArraysWithDestructuring() {
        assertStringWithJavet(DESTRUCTURING_LOOP);
    }

    @Test
    void testForOfArrayDestructuring() {
        assertThat(context.eval(DESTRUCTURING_LOOP).toString()).isEqualTo("1a2b");
    }

    @Test
    void testForOfInFunction() {
        String code = """
                function test() {
                  var result = '';
                  for (var x of ['a', 'b', 'c']) result += x;
                  return result;
                }
                test()""";
        JSValue value = context.eval(code);
        String result = value.toString();
        assertThat(result).isEqualTo("abc");
    }

    @Test
    void testForOfInGlobalScope() {
        assertThat(context.eval(GLOBAL_LOOP).toString()).isEqualTo("abc");
    }

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
    void testForOfSimpleArray() {
        assertThat(context.eval(GLOBAL_LOOP).toString()).isEqualTo("abc");
    }

    @Test
    void testIteratorCloseOnBreak() {
        assertBooleanWithJavet(CLOSABLE_GENERATOR + """
                for (const x of g()) {
                  break;
                }
                closed""");
    }

    @Test
    void testIteratorCloseOnReturn() {
        assertBooleanWithJavet(CLOSABLE_GENERATOR + """
                (function () {
                  for (const x of g()) {
                    return;
                  }
                })();
                closed""");
    }

    @Test
    void testIteratorCloseOnThrow() {
        assertBooleanWithJavet(CLOSABLE_GENERATOR + """
                try {
                  for (const x of g()) {
                    throw new Error('x');
                  }
                } catch (e) {
                }
                closed""");
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
        assertStringWithJavet("""
                var s = new Set(['a']);
                var result = 'none';
                for (let x of s) { result = x; }
                result""");
    }

    @Test
    void testSimpleForOfTypeof() {
        // Check the type
        assertStringWithJavet("""
                var s = new Set(['a']);
                var result = 'none';
                for (let x of s) { result = typeof x; }
                result""");
    }
}
