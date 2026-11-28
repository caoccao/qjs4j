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

package com.caoccao.qjs4j.builtins.number;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSNativeCallback;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Unit tests for Math object methods.
 */
public class MathObjectTest extends BaseJavetTest {

    private void assertMath(JSNativeCallback operation, double expected, double... inputs) {
        JSValue value = callFunction(operation, numberArgs(inputs));
        if (Double.isNaN(expected)) {
            assertThat(value.asNumber().orElseThrow().value()).isNaN();
        } else {
            assertNumberValue(value, expected);
        }
    }

    private void assertMathClose(JSNativeCallback operation, double expected, double... inputs) {
        assertThat(callFunction(operation, numberArgs(inputs)).asNumber().map(JSNumber::value).orElseThrow())
                .isCloseTo(expected, offset(1e-10));
    }

    @Test
    public void testAbs() {
        // Normal case: positive number
        assertMath(MathObject::abs, 5.5, 5.5);

        // Normal case: negative number
        assertMath(MathObject::abs, 3.7, -3.7);

        // Normal case: zero
        assertMath(MathObject::abs, 0.0, 0);

        // Normal case: NaN
        assertMath(MathObject::abs, Double.NaN, Double.NaN);

        // Normal case: Infinity
        assertMath(MathObject::abs, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);

        // Edge case: no arguments
        assertMath(MathObject::abs, Double.NaN);

        // Edge case: string coercion
        assertNumberValue(callFunction(MathObject::abs, "-42"), 42.0);
    }

    @Test
    public void testAdd() {
        assertIntegerWithJavet("1 + 1", "var a = 1; var b = 2; a + b");
        assertDoubleWithJavet("2**32 + 2**32");
    }

    @Test
    public void testCbrt() {
        // Normal case: perfect cube
        assertMath(MathObject::cbrt, 2.0, 8);

        // Normal case: negative perfect cube
        assertMath(MathObject::cbrt, -2.0, -8);

        // Normal case: non-perfect cube
        assertMath(MathObject::cbrt, Math.cbrt(3), 3);

        // Edge case: no arguments
        assertMath(MathObject::cbrt, Double.NaN);
    }

    @Test
    public void testCeil() {
        // Normal case: positive number
        assertMath(MathObject::ceil, 5.0, 4.2);

        // Normal case: negative number
        assertMath(MathObject::ceil, -4.0, -4.2);

        // Normal case: integer
        assertMath(MathObject::ceil, 3.0, 3.0);

        // Normal case: NaN
        assertMath(MathObject::ceil, Double.NaN, Double.NaN);

        // Edge case: no arguments
        assertMath(MathObject::ceil, Double.NaN);
    }

    @Test
    public void testConstants() {
        // Test that constants are accessible (though they're not methods, they're static fields)
        // These would typically be tested through property access on the Math object,
        // but we can verify they exist and have correct values
        assertThat(MathObject.E).isCloseTo(Math.E, offset(1e-15));
        assertThat(MathObject.PI).isCloseTo(Math.PI, offset(1e-15));
        assertThat(MathObject.SQRT2).isCloseTo(Math.sqrt(2), offset(1e-15));
        assertThat(MathObject.SQRT1_2).isCloseTo(Math.sqrt(0.5), offset(1e-15));
    }

    @Test
    public void testDivide() {
        assertIntegerWithJavet("1 / 1");
        assertDoubleWithJavet("var a = 1; var b = 2; a / b", "-2 / -3", "1 / 0", "Infinity / -Infinity");
    }

    @Test
    public void testExponentialAndLogarithmicFunctions() {
        // Test exp, log, log10, log2
        assertMathClose(MathObject::exp, 1.0, 0);

        assertMathClose(MathObject::log, 0.0, 1);

        assertMathClose(MathObject::log10, 1.0, 10);

        assertMathClose(MathObject::log2, 1.0, 2);

        // Test expm1, log1p
        assertMathClose(MathObject::expm1, 0.0, 0);

        assertMathClose(MathObject::log1p, 0.0, 0);

        // Edge case: no arguments
        assertMath(MathObject::exp, Double.NaN);
    }

    @Test
    public void testF16Round() {
        assertEvalJavaValue("typeof Math.f16round", "function");
        assertEvalJavaValue("Math.f16round.length", 1.0);
        assertEvalJavaValue("Math.f16round(1.337)", 1.3369140625);
        assertEvalJavaValue("Object.is(Math.f16round(-0), -0)", true);
        assertEvalJavaValue("Math.f16round(Infinity)", Double.POSITIVE_INFINITY);
    }

    @Test
    public void testFloor() {
        // Normal case: positive number
        assertMath(MathObject::floor, 4.0, 4.9);

        // Normal case: negative number
        assertMath(MathObject::floor, -5.0, -4.1);

        // Normal case: integer
        assertMath(MathObject::floor, 7.0, 7.0);

        // Normal case: NaN
        assertMath(MathObject::floor, Double.NaN, Double.NaN);

        // Edge case: no arguments
        assertMath(MathObject::floor, Double.NaN);
    }

    @Test
    public void testHyperbolicFunctions() {
        // Test sinh, cosh, tanh
        assertMathClose(MathObject::sinh, 0.0, 0);

        assertMathClose(MathObject::cosh, 1.0, 0);

        assertMathClose(MathObject::tanh, 0.0, 0);

        // Edge case: no arguments
        assertMath(MathObject::sinh, Double.NaN);
    }

    @Test
    public void testInverseHyperbolicFunctions() {
        // Test asinh, acosh, atanh
        assertMathClose(MathObject::asinh, 0.0, 0);

        assertMathClose(MathObject::acosh, 0.0, 1);

        assertMathClose(MathObject::atanh, 0.0, 0);

        // Edge case: no arguments
        assertMath(MathObject::asinh, Double.NaN);
    }

    @Test
    public void testInverseTrigonometricFunctions() {
        // Test asin, acos, atan
        assertMathClose(MathObject::asin, 0.0, 0);

        assertMathClose(MathObject::acos, 0.0, 1);

        assertMathClose(MathObject::atan, 0.0, 0);

        // Test atan2
        assertMathClose(MathObject::atan2, 0.0, 0, 1);

        // Edge case: no arguments
        assertMath(MathObject::asin, Double.NaN);
    }

    @Test
    public void testMathObjectRegistration() {
        assertBooleanWithJavet("""
                (() => {
                  const piDesc = Object.getOwnPropertyDescriptor(Math, 'PI');
                  const absDesc = Object.getOwnPropertyDescriptor(Math, 'abs');
                  const tagDesc = Object.getOwnPropertyDescriptor(Math, Symbol.toStringTag);
                  return Object.keys(Math).length === 0
                    && piDesc.writable === false
                    && piDesc.enumerable === false
                    && piDesc.configurable === false
                    && typeof piDesc.value === 'number'
                    && absDesc.writable === true
                    && absDesc.enumerable === false
                    && absDesc.configurable === true
                    && typeof absDesc.value === 'function'
                    && tagDesc.value === 'Math'
                    && tagDesc.writable === false
                    && tagDesc.enumerable === false
                    && tagDesc.configurable === true;
                })()
                """);
    }

    @Test
    public void testMax() {
        // Normal case: multiple numbers
        assertMath(MathObject::max, 5.0, 1, 5, 3);

        // Normal case: single number
        assertMath(MathObject::max, 42.0, 42);

        // Normal case: with NaN
        assertMath(MathObject::max, Double.NaN, 1, Double.NaN, 3);

        // Normal case: negative numbers
        assertMath(MathObject::max, -1.0, -5, -1, -10);

        // Edge case: no arguments
        assertMath(MathObject::max, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void testMin() {
        // Normal case: multiple numbers
        assertMath(MathObject::min, 1.0, 1, 5, 3);

        // Normal case: single number
        assertMath(MathObject::min, 42.0, 42);

        // Normal case: with NaN
        assertMath(MathObject::min, Double.NaN, 1, Double.NaN, 3);

        // Normal case: negative numbers
        assertMath(MathObject::min, -10.0, -5, -1, -10);

        // Edge case: no arguments
        assertMath(MathObject::min, Double.POSITIVE_INFINITY);
    }

    @Test
    public void testMultiply() {
        assertIntegerWithJavet("1 * 1", "var a = 1; var b = 2; a * b", "-2 * -3");
    }

    @Test
    public void testPow() {
        // Normal case: positive base, positive exponent
        assertMath(MathObject::pow, 8.0, 2, 3);

        // Normal case: base 0, positive exponent
        assertMath(MathObject::pow, 0.0, 0, 5);

        // Normal case: negative base, integer exponent
        assertMath(MathObject::pow, -8.0, -2, 3);

        // Normal case: fractional exponent
        assertMath(MathObject::pow, 2.0, 4, 0.5);

        // Edge case: no base
        assertMath(MathObject::pow, Double.NaN, 2);

        // Edge case: no arguments
        assertMath(MathObject::pow, Double.NaN);
    }

    @Test
    public void testRandom() {
        // Normal case: random number
        var randomMethod = builtin(MathObject::random);
        JSValue result = randomMethod.call();
        double randomValue = result.asNumber().map(JSNumber::value).orElseThrow();
        assertThat(randomValue >= 0.0 && randomValue < 1.0).isTrue();

        // Edge case: with arguments (should ignore them)
        randomValue = randomMethod.call(42).asNumber().map(JSNumber::value).orElseThrow();
        assertThat(randomValue >= 0.0 && randomValue < 1.0).isTrue();
    }

    @Test
    public void testRound() {
        // Normal case: round up
        assertMath(MathObject::round, 5.0, 4.5);

        // Normal case: round down
        assertMath(MathObject::round, 4.0, 4.4);

        // Normal case: negative number
        assertMath(MathObject::round, -4.0, -4.5);

        // Normal case: NaN
        assertMath(MathObject::round, Double.NaN, Double.NaN);

        // Edge case: no arguments
        assertMath(MathObject::round, Double.NaN);
    }

    @Test
    public void testRoundAndFroundEdgeCases() {
        assertBooleanWithJavet("Object.is(Math.round(-0.5), -0)", "Object.is(Math.round(-0.1), -0)",
                "Math.round(-1.5) === -1", "Math.round(0.5) === 1", "Object.is(Math.fround(-0), -0)",
                "Math.fround(1.337) === 1.3370000123977661", "Math.hypot(NaN, Infinity) === Infinity");
    }

    @Test
    public void testSign() {
        // Normal case: positive number
        assertMath(MathObject::sign, 1.0, 5);

        // Normal case: negative number
        assertMath(MathObject::sign, -1.0, -3);

        // Normal case: zero
        assertMath(MathObject::sign, 0.0, 0);

        // Normal case: negative zero
        assertMath(MathObject::sign, -0.0, -0.0);

        // Normal case: NaN
        assertMath(MathObject::sign, Double.NaN, Double.NaN);

        // Edge case: no arguments
        assertMath(MathObject::sign, Double.NaN);
    }

    @Test
    public void testSpecializedFunctions() {
        // Test clz32
        assertMath(MathObject::clz32, 31.0, 1);

        // Test fround
        assertThat(callFunction(MathObject::fround, 1.5).asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(1.5,
                offset(1e-6));

        // Test hypot
        assertMathClose(MathObject::hypot, 5.0, 3, 4);

        // Test imul
        assertMath(MathObject::imul, 6.0, 2, 3);

        // Edge case: no arguments
        assertMath(MathObject::clz32, 32.0);
    }

    @Test
    public void testSqrt() {
        // Normal case: perfect square
        assertMath(MathObject::sqrt, 3.0, 9);

        // Normal case: non-perfect square
        assertMath(MathObject::sqrt, Math.sqrt(2), 2);

        // Normal case: zero
        assertMath(MathObject::sqrt, 0.0, 0);

        // Normal case: negative number
        assertMath(MathObject::sqrt, Double.NaN, -1);

        // Edge case: no arguments
        assertMath(MathObject::sqrt, Double.NaN);
    }

    @Test
    public void testSubtract() {
        assertIntegerWithJavet("1 - 1", "var a = 1; var b = 2; a - b", "2**32 - 2**32");
    }

    @Test
    public void testSumPrecise() {
        assertEvalJavaValue("typeof Math.sumPrecise", "function");
        assertEvalJavaValue("Math.sumPrecise.length", 1.0);
        assertEvalJavaValue("Math.sumPrecise([1e100, 1, -1e100])", 1.0);
        assertEvalJavaValue("Object.is(Math.sumPrecise([]), -0)", true);
        assertEvalJavaValue("Object.is(Math.sumPrecise([0]), +0)", true);
        assertEvalJavaValue("Object.is(Math.sumPrecise([-0]), -0)", true);
        assertEvalJavaValue("Number.isNaN(Math.sumPrecise([Infinity, -Infinity]))", true);
        assertEvalJavaValue("""
                (() => {
                  const iterable = {
                    [Symbol.iterator]() {
                      const iterator = {
                        i: 0,
                        closed: 0,
                        next: function () {
                          if (this.i === 0) {
                            this.i++;
                            return { value: 1, done: false };
                          }
                          if (this.i === 1) {
                            this.i++;
                            return { value: 'x', done: false };
                          }
                          return { value: undefined, done: true };
                        },
                        \u0072eturn: function () {
                          this.closed++;
                          return { done: true };
                        }
                      };
                      this.iteratorRef = iterator;
                      return iterator;
                    }
                  };
                  try {
                    Math.sumPrecise(iterable);
                    return false;
                  } catch (e) {
                    const it = iterable.iteratorRef;
                    return e instanceof TypeError
                      && it
                      && typeof it.return === 'function'
                      && it.closed === 1;
                  }
                })()
                """, true);
    }

    @Test
    public void testTrigonometricFunctions() {
        // Test sin, cos, tan with common values
        assertMathClose(MathObject::sin, 0.0, 0);

        assertMathClose(MathObject::cos, 1.0, 0);

        assertMathClose(MathObject::tan, 0.0, 0);

        // Test with PI/2
        assertThat(callFunction(MathObject::sin, Math.PI / 2).asNumber().map(JSNumber::value).orElseThrow())
                .isCloseTo(1.0, offset(1e-10));

        // Edge case: no arguments
        assertMath(MathObject::sin, Double.NaN);
    }

    @Test
    public void testTrunc() {
        // Normal case: positive number
        assertMath(MathObject::trunc, 4.0, 4.9);

        // Normal case: negative number
        assertMath(MathObject::trunc, -4.0, -4.9);

        // Normal case: integer
        assertMath(MathObject::trunc, 7.0, 7.0);

        // Normal case: NaN
        assertMath(MathObject::trunc, Double.NaN, Double.NaN);

        // Normal case: Infinity
        assertMath(MathObject::trunc, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

        // Edge case: no arguments
        assertMath(MathObject::trunc, Double.NaN);
    }
}
