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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Unit tests for Math object methods.
 */
public class MathObjectTest extends BaseJavetTest {

    @Test
    public void testAbs() {
        // Normal case: positive number
        JSValue result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5.5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.5);

        // Normal case: negative number
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-3.7)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.7);

        // Normal case: zero
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Normal case: NaN
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Normal case: Infinity
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NEGATIVE_INFINITY)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(Double.POSITIVE_INFINITY);

        // Edge case: no arguments
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: string coercion
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("-42")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);
    }

    @Test
    public void testAdd() {
        Stream.of(
                "1 + 1",
                "var a = 1; var b = 2; a + b").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                        () -> context.eval(code).toJavaObject()));
        Stream.of(
                "2**32 + 2**32").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeLong().doubleValue(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testCbrt() {
        // Normal case: perfect cube
        JSValue result = MathObject.cbrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(8)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // Normal case: negative perfect cube
        result = MathObject.cbrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-8)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-2.0);

        // Normal case: non-perfect cube
        result = MathObject.cbrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(Math.cbrt(3));

        // Edge case: no arguments
        result = MathObject.cbrt(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testCeil() {
        // Normal case: positive number
        JSValue result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);

        // Normal case: negative number
        result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-4.0);

        // Normal case: integer
        result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3.0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Normal case: NaN
        result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: no arguments
        result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
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
        Stream.of(
                "1 / 1").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                        () -> context.eval(code).toJavaObject()));
        Stream.of(
                "var a = 1; var b = 2; a / b",
                "-2 / -3",
                "1 / 0",
                "Infinity / -Infinity").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeDouble(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testExponentialAndLogarithmicFunctions() {
        // Test exp, log, log10, log2
        JSValue result = MathObject.exp(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(1.0, offset(1e-10));

        result = MathObject.log(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        result = MathObject.log10(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(10)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(1.0, offset(1e-10));

        result = MathObject.log2(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(1.0, offset(1e-10));

        // Test expm1, log1p
        result = MathObject.expm1(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        result = MathObject.log1p(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        // Edge case: no arguments
        result = MathObject.exp(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testFloor() {
        // Normal case: positive number
        JSValue result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.9)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Normal case: negative number
        result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-5.0);

        // Normal case: integer
        result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(7.0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(7.0);

        // Normal case: NaN
        result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: no arguments
        result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testHyperbolicFunctions() {
        // Test sinh, cosh, tanh
        JSValue result = MathObject.sinh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        result = MathObject.cosh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(1.0, offset(1e-10));

        result = MathObject.tanh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        // Edge case: no arguments
        result = MathObject.sinh(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testInverseHyperbolicFunctions() {
        // Test asinh, acosh, atanh
        JSValue result = MathObject.asinh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        result = MathObject.acosh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        result = MathObject.atanh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        // Edge case: no arguments
        result = MathObject.asinh(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testInverseTrigonometricFunctions() {
        // Test asin, acos, atan
        JSValue result = MathObject.asin(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        result = MathObject.acos(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        result = MathObject.atan(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        // Test atan2
        result = MathObject.atan2(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0), new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        // Edge case: no arguments
        result = MathObject.asin(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testMax() {
        // Normal case: multiple numbers
        JSValue result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(5), new JSNumber(3)
        });
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);

        // Normal case: single number
        result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Normal case: with NaN
        result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(Double.NaN), new JSNumber(3)
        });
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Normal case: negative numbers
        result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-5), new JSNumber(-1), new JSNumber(-10)
        });
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Edge case: no arguments
        result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    public void testMin() {
        // Normal case: multiple numbers
        JSValue result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(5), new JSNumber(3)
        });
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Normal case: single number
        result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Normal case: with NaN
        result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(Double.NaN), new JSNumber(3)
        });
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Normal case: negative numbers
        result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-5), new JSNumber(-1), new JSNumber(-10)
        });
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-10.0);

        // Edge case: no arguments
        result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    public void testMultiply() {
        Stream.of(
                "1 * 1",
                "var a = 1; var b = 2; a * b",
                "-2 * -3").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testPow() {
        // Normal case: positive base, positive exponent
        JSValue result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2), new JSNumber(3)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(8.0);

        // Normal case: base 0, positive exponent
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Normal case: negative base, integer exponent
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-2), new JSNumber(3)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-8.0);

        // Normal case: fractional exponent
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4), new JSNumber(0.5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // Edge case: no base
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: no arguments
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testRandom() {
        // Normal case: random number
        JSValue result = MathObject.random(context, JSUndefined.INSTANCE, new JSValue[]{});
        double randomValue = result.asNumber().map(JSNumber::value).orElseThrow();
        assertThat(randomValue >= 0.0 && randomValue < 1.0).isTrue();

        // Edge case: with arguments (should ignore them)
        result = MathObject.random(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        randomValue = result.asNumber().map(JSNumber::value).orElseThrow();
        assertThat(randomValue >= 0.0 && randomValue < 1.0).isTrue();
    }

    @Test
    public void testRound() {
        // Normal case: round up
        JSValue result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);

        // Normal case: round down
        result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.4)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Normal case: negative number
        result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-4.0);

        // Normal case: NaN
        result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: no arguments
        result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testSign() {
        // Normal case: positive number
        JSValue result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Normal case: negative number
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-3)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Normal case: zero
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Normal case: negative zero
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-0.0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-0.0);

        // Normal case: NaN
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: no arguments
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testSpecializedFunctions() {
        // Test clz32
        JSValue result = MathObject.clz32(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(31.0);

        // Test fround
        result = MathObject.fround(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1.5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(1.5, offset(1e-6));

        // Test hypot
        result = MathObject.hypot(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3), new JSNumber(4)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(5.0, offset(1e-10));

        // Test imul
        result = MathObject.imul(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2), new JSNumber(3)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(6.0);

        // Edge case: no arguments
        result = MathObject.clz32(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(32.0);
    }

    @Test
    public void testSqrt() {
        // Normal case: perfect square
        JSValue result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(9)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Normal case: non-perfect square
        result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(Math.sqrt(2));

        // Normal case: zero
        result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Normal case: negative number
        result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-1)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: no arguments
        result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testSubtract() {
        Stream.of(
                "1 - 1",
                "var a = 1; var b = 2; a - b",
                "2**32 - 2**32").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testTrigonometricFunctions() {
        // Test sin, cos, tan with common values
        JSValue result = MathObject.sin(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        result = MathObject.cos(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(1.0, offset(1e-10));

        result = MathObject.tan(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(0.0, offset(1e-10));

        // Test with PI/2
        result = MathObject.sin(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Math.PI / 2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isCloseTo(1.0, offset(1e-10));

        // Edge case: no arguments
        result = MathObject.sin(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }

    @Test
    public void testTrunc() {
        // Normal case: positive number
        JSValue result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.9)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Normal case: negative number
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.9)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-4.0);

        // Normal case: integer
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(7.0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(7.0);

        // Normal case: NaN
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Normal case: Infinity
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(Double.POSITIVE_INFINITY);

        // Edge case: no arguments
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();
    }
}