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
        JSValue result = MathObject.abs(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5.5)});
        assertEquals(5.5, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative number
        result = MathObject.abs(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-3.7)});
        assertEquals(3.7, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: zero
        result = MathObject.abs(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: NaN
        result = MathObject.abs(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Normal case: Infinity
        result = MathObject.abs(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NEGATIVE_INFINITY)});
        assertEquals(Double.POSITIVE_INFINITY, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: no arguments
        result = MathObject.abs(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Edge case: string coercion
        result = MathObject.abs(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("-42")});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElse(0.0));
    }

    @Test
    public void testCeil() {
        // Normal case: positive number
        JSValue result = MathObject.ceil(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.2)});
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative number
        result = MathObject.ceil(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.2)});
        assertEquals(-4.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: integer
        result = MathObject.ceil(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3.0)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: NaN
        result = MathObject.ceil(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Edge case: no arguments
        result = MathObject.ceil(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testFloor() {
        // Normal case: positive number
        JSValue result = MathObject.floor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.9)});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative number
        result = MathObject.floor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.1)});
        assertEquals(-5.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: integer
        result = MathObject.floor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(7.0)});
        assertEquals(7.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: NaN
        result = MathObject.floor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Edge case: no arguments
        result = MathObject.floor(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testRound() {
        // Normal case: round up
        JSValue result = MathObject.round(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.5)});
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: round down
        result = MathObject.round(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.4)});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative number
        result = MathObject.round(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.5)});
        assertEquals(-4.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: NaN
        result = MathObject.round(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Edge case: no arguments
        result = MathObject.round(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testTrunc() {
        // Normal case: positive number
        JSValue result = MathObject.trunc(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4.9)});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative number
        result = MathObject.trunc(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-4.9)});
        assertEquals(-4.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: integer
        result = MathObject.trunc(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(7.0)});
        assertEquals(7.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: NaN
        result = MathObject.trunc(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Normal case: Infinity
        result = MathObject.trunc(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertEquals(Double.POSITIVE_INFINITY, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: no arguments
        result = MathObject.trunc(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testSqrt() {
        // Normal case: perfect square
        JSValue result = MathObject.sqrt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(9)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: non-perfect square
        result = MathObject.sqrt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertEquals(Math.sqrt(2), result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: zero
        result = MathObject.sqrt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative number
        result = MathObject.sqrt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-1)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Edge case: no arguments
        result = MathObject.sqrt(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testCbrt() {
        // Normal case: perfect cube
        JSValue result = MathObject.cbrt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(8)});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative perfect cube
        result = MathObject.cbrt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-8)});
        assertEquals(-2.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: non-perfect cube
        result = MathObject.cbrt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3)});
        assertEquals(Math.cbrt(3), result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: no arguments
        result = MathObject.cbrt(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testMax() {
        // Normal case: multiple numbers
        JSValue result = MathObject.max(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(5), new JSNumber(3)
        });
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: single number
        result = MathObject.max(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: with NaN
        result = MathObject.max(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(Double.NaN), new JSNumber(3)
        });
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Normal case: negative numbers
        result = MathObject.max(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-5), new JSNumber(-1), new JSNumber(-10)
        });
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: no arguments
        result = MathObject.max(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals(Double.NEGATIVE_INFINITY, result.asNumber().map(JSNumber::value).orElse(0.0));
    }

    @Test
    public void testMin() {
        // Normal case: multiple numbers
        JSValue result = MathObject.min(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(5), new JSNumber(3)
        });
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: single number
        result = MathObject.min(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: with NaN
        result = MathObject.min(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1), new JSNumber(Double.NaN), new JSNumber(3)
        });
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Normal case: negative numbers
        result = MathObject.min(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-5), new JSNumber(-1), new JSNumber(-10)
        });
        assertEquals(-10.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: no arguments
        result = MathObject.min(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals(Double.POSITIVE_INFINITY, result.asNumber().map(JSNumber::value).orElse(0.0));
    }

    @Test
    public void testPow() {
        // Normal case: positive base, positive exponent
        JSValue result = MathObject.pow(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2), new JSNumber(3)});
        assertEquals(8.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: base 0, positive exponent
        result = MathObject.pow(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative base, integer exponent
        result = MathObject.pow(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-2), new JSNumber(3)});
        assertEquals(-8.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: fractional exponent
        result = MathObject.pow(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(4), new JSNumber(0.5)});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: no base
        result = MathObject.pow(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Edge case: no arguments
        result = MathObject.pow(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testRandom() {
        // Normal case: random number
        JSValue result = MathObject.random(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        double randomValue = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(randomValue >= 0.0 && randomValue < 1.0);

        // Edge case: with arguments (should ignore them)
        result = MathObject.random(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        randomValue = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(randomValue >= 0.0 && randomValue < 1.0);
    }

    @Test
    public void testSign() {
        // Normal case: positive number
        JSValue result = MathObject.sign(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative number
        result = MathObject.sign(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-3)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: zero
        result = MathObject.sign(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: negative zero
        result = MathObject.sign(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-0.0)});
        assertEquals(-0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: NaN
        result = MathObject.sign(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));

        // Edge case: no arguments
        result = MathObject.sign(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testTrigonometricFunctions() {
        // Test sin, cos, tan with common values
        JSValue result = MathObject.sin(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.cos(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.tan(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Test with PI/2
        result = MathObject.sin(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Math.PI / 2)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Edge case: no arguments
        result = MathObject.sin(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testInverseTrigonometricFunctions() {
        // Test asin, acos, atan
        JSValue result = MathObject.asin(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.acos(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.atan(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Test atan2
        result = MathObject.atan2(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0), new JSNumber(1)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Edge case: no arguments
        result = MathObject.asin(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testHyperbolicFunctions() {
        // Test sinh, cosh, tanh
        JSValue result = MathObject.sinh(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.cosh(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.tanh(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Edge case: no arguments
        result = MathObject.sinh(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testInverseHyperbolicFunctions() {
        // Test asinh, acosh, atanh
        JSValue result = MathObject.asinh(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.acosh(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.atanh(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Edge case: no arguments
        result = MathObject.asinh(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testExponentialAndLogarithmicFunctions() {
        // Test exp, log, log10, log2
        JSValue result = MathObject.exp(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.log(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.log10(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(10)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.log2(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Test expm1, log1p
        result = MathObject.expm1(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        result = MathObject.log1p(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Edge case: no arguments
        result = MathObject.exp(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElse(0.0)));
    }

    @Test
    public void testSpecializedFunctions() {
        // Test clz32
        JSValue result = MathObject.clz32(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1)});
        assertEquals(31.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Test fround
        result = MathObject.fround(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1.5)});
        assertEquals(1.5f, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-6);

        // Test hypot
        result = MathObject.hypot(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3), new JSNumber(4)});
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElse(0.0), 1e-10);

        // Test imul
        result = MathObject.imul(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2), new JSNumber(3)});
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: no arguments
        result = MathObject.clz32(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals(32.0, result.asNumber().map(JSNumber::value).orElse(0.0));
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
}