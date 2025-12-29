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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Math object methods.
 */
public class MathObjectTest extends BaseTest {

    @Test
    public void testAbs() {
        // Normal case: positive number
        JSValue result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5.5)});
        assertEquals(5.5, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-3.7)});
        assertEquals(3.7, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: zero
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: NaN
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Normal case: Infinity
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NEGATIVE_INFINITY)});
        assertEquals(Double.POSITIVE_INFINITY, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no arguments
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: string coercion
        result = MathObject.abs(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("-42")});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());
    }

    @Test
    public void testCbrt() {
        // Normal case: perfect cube
        JSValue result = MathObject.cbrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(8)});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative perfect cube
        result = MathObject.cbrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-8)});
        assertEquals(-2.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: non-perfect cube
        result = MathObject.cbrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3)});
        assertEquals(Math.cbrt(3), result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no arguments
        result = MathObject.cbrt(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testCeil() {
        // Normal case: positive number
        JSValue result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.2)});
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.2)});
        assertEquals(-4.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: integer
        result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3.0)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: NaN
        result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: no arguments
        result = MathObject.ceil(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testConstants() {
        // Test that constants are accessible (though they're not methods, they're static fields)
        // These would typically be tested through property access on the Math object,
        // but we can verify they exist and have correct values
        assertEquals(Math.E, MathObject.E, 1e-15);
        assertEquals(Math.PI, MathObject.PI, 1e-15);
        assertEquals(Math.sqrt(2), MathObject.SQRT2, 1e-15);
        assertEquals(Math.sqrt(0.5), MathObject.SQRT1_2, 1e-15);
    }

    @Test
    public void testExponentialAndLogarithmicFunctions() {
        // Test exp, log, log10, log2
        JSValue result = MathObject.exp(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.log(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.log10(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(10)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.log2(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Test expm1, log1p
        result = MathObject.expm1(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.log1p(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Edge case: no arguments
        result = MathObject.exp(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testFloor() {
        // Normal case: positive number
        JSValue result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.9)});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.1)});
        assertEquals(-5.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: integer
        result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(7.0)});
        assertEquals(7.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: NaN
        result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: no arguments
        result = MathObject.floor(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testHyperbolicFunctions() {
        // Test sinh, cosh, tanh
        JSValue result = MathObject.sinh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.cosh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.tanh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Edge case: no arguments
        result = MathObject.sinh(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testInverseHyperbolicFunctions() {
        // Test asinh, acosh, atanh
        JSValue result = MathObject.asinh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.acosh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.atanh(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Edge case: no arguments
        result = MathObject.asinh(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testInverseTrigonometricFunctions() {
        // Test asin, acos, atan
        JSValue result = MathObject.asin(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.acos(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.atan(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Test atan2
        result = MathObject.atan2(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0), new JSNumber(1)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Edge case: no arguments
        result = MathObject.asin(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testMax() {
        // Normal case: multiple numbers
        JSValue result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(5), new JSNumber(3)
        });
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: single number
        result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: with NaN
        result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(Double.NaN), new JSNumber(3)
        });
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Normal case: negative numbers
        result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-5), new JSNumber(-1), new JSNumber(-10)
        });
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no arguments
        result = MathObject.max(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals(Double.NEGATIVE_INFINITY, result.asNumber().map(JSNumber::value).orElseThrow());
    }

    @Test
    public void testMin() {
        // Normal case: multiple numbers
        JSValue result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(5), new JSNumber(3)
        });
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: single number
        result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: with NaN
        result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(Double.NaN), new JSNumber(3)
        });
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Normal case: negative numbers
        result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-5), new JSNumber(-1), new JSNumber(-10)
        });
        assertEquals(-10.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no arguments
        result = MathObject.min(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals(Double.POSITIVE_INFINITY, result.asNumber().map(JSNumber::value).orElseThrow());
    }

    @Test
    public void testPow() {
        // Normal case: positive base, positive exponent
        JSValue result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2), new JSNumber(3)});
        assertEquals(8.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: base 0, positive exponent
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative base, integer exponent
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-2), new JSNumber(3)});
        assertEquals(-8.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: fractional exponent
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4), new JSNumber(0.5)});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no base
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: no arguments
        result = MathObject.pow(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testRandom() {
        // Normal case: random number
        JSValue result = MathObject.random(context, JSUndefined.INSTANCE, new JSValue[]{});
        double randomValue = result.asNumber().map(JSNumber::value).orElseThrow();
        assertTrue(randomValue >= 0.0 && randomValue < 1.0);

        // Edge case: with arguments (should ignore them)
        result = MathObject.random(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        randomValue = result.asNumber().map(JSNumber::value).orElseThrow();
        assertTrue(randomValue >= 0.0 && randomValue < 1.0);
    }

    @Test
    public void testRound() {
        // Normal case: round up
        JSValue result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.5)});
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: round down
        result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.4)});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.5)});
        assertEquals(-4.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: NaN
        result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: no arguments
        result = MathObject.round(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testSign() {
        // Normal case: positive number
        JSValue result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-3)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: zero
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative zero
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-0.0)});
        assertEquals(-0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: NaN
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: no arguments
        result = MathObject.sign(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testSpecializedFunctions() {
        // Test clz32
        JSValue result = MathObject.clz32(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertEquals(31.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Test fround
        result = MathObject.fround(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1.5)});
        assertEquals(1.5f, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-6);

        // Test hypot
        result = MathObject.hypot(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3), new JSNumber(4)});
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Test imul
        result = MathObject.imul(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2), new JSNumber(3)});
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no arguments
        result = MathObject.clz32(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals(32.0, result.asNumber().map(JSNumber::value).orElseThrow());
    }

    @Test
    public void testSqrt() {
        // Normal case: perfect square
        JSValue result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(9)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: non-perfect square
        result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertEquals(Math.sqrt(2), result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: zero
        result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-1)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: no arguments
        result = MathObject.sqrt(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testTrigonometricFunctions() {
        // Test sin, cos, tan with common values
        JSValue result = MathObject.sin(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.cos(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        result = MathObject.tan(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Test with PI/2
        result = MathObject.sin(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Math.PI / 2)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow(), 1e-10);

        // Edge case: no arguments
        result = MathObject.sin(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }

    @Test
    public void testTrunc() {
        // Normal case: positive number
        JSValue result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.9)});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.9)});
        assertEquals(-4.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: integer
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(7.0)});
        assertEquals(7.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: NaN
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Normal case: Infinity
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertEquals(Double.POSITIVE_INFINITY, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no arguments
        result = MathObject.trunc(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));
    }
}