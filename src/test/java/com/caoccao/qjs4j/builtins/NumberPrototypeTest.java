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

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Number.prototype methods.
 */
public class NumberPrototypeTest extends BaseTest {

    @Test
    public void testConstants() {
        // Test MAX_SAFE_INTEGER constant
        assertEquals(9007199254740991L, NumberPrototype.MAX_SAFE_INTEGER);

        // Verify it's actually the maximum safe integer (2^53 - 1)
        assertEquals(Math.pow(2, 53) - 1, NumberPrototype.MAX_SAFE_INTEGER, 0.0);
    }

    @Test
    public void testEquals() {
        // Verify that loose equality passes between primitive and primitive
        assertTrue(ctx.eval("123 == 123").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123 == 321").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(ctx.eval("123 == Number(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123 == Number(321)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality passes between primitive and primitive
        assertTrue(ctx.eval("123 === 123").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123 === 321").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(ctx.eval("123 === Number(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123 === Number(321)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality passes between primitive and primitive
        assertTrue(ctx.eval("Number(123) == Number(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("Number(123) == Number(321)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality passes between primitive and object
        assertTrue(ctx.eval("123 == new Number(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123 == new Number(321)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(ctx.eval("Number(123) == new Number(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("Number(123) == new Number(321)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality fails between object and object
        assertFalse(ctx.eval("new Number(123) == new Number(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality fails between primitive and object
        assertFalse(ctx.eval("123 === new Number(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality fails between object and object
        assertFalse(ctx.eval("new Number(123) === new Number(123)").asBoolean().map(JSBoolean::value).orElseThrow());
    }

    @Test
    public void testIsFinite() {
        // Normal case: finite number
        JSValue result = NumberPrototype.isFinite(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertTrue(result.isBooleanTrue());

        // Normal case: NaN
        result = NumberPrototype.isFinite(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(result.isBooleanFalse());

        // Normal case: positive Infinity
        result = NumberPrototype.isFinite(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertTrue(result.isBooleanFalse());

        // Normal case: negative Infinity
        result = NumberPrototype.isFinite(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NEGATIVE_INFINITY)});
        assertTrue(result.isBooleanFalse());

        // Normal case: non-number value
        result = NumberPrototype.isFinite(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        result = NumberPrototype.isFinite(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(result.isBooleanFalse());
    }

    @Test
    public void testIsInteger() {
        // Normal case: integer
        JSValue result = NumberPrototype.isInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertTrue(result.isBooleanTrue());

        // Normal case: negative integer
        result = NumberPrototype.isInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-17)});
        assertTrue(result.isBooleanTrue());

        // Normal case: zero
        result = NumberPrototype.isInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertTrue(result.isBooleanTrue());

        // Normal case: float
        result = NumberPrototype.isInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42.5)});
        assertTrue(result.isBooleanFalse());

        // Normal case: NaN
        result = NumberPrototype.isInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(result.isBooleanFalse());

        // Normal case: Infinity
        result = NumberPrototype.isInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertTrue(result.isBooleanFalse());

        // Normal case: non-number value
        result = NumberPrototype.isInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        result = NumberPrototype.isInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(result.isBooleanFalse());
    }

    @Test
    public void testIsNaN() {
        // Normal case: NaN number
        JSValue result = NumberPrototype.isNaN(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(result.isBooleanTrue());

        // Normal case: finite number
        result = NumberPrototype.isNaN(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertTrue(result.isBooleanFalse());

        // Normal case: Infinity
        result = NumberPrototype.isNaN(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertTrue(result.isBooleanFalse());

        // Normal case: non-number value
        result = NumberPrototype.isNaN(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not a number")});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        result = NumberPrototype.isNaN(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(result.isBooleanFalse());
    }

    @Test
    public void testIsSafeInteger() {
        // Normal case: safe integer
        JSValue result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertTrue(result.isBooleanTrue());

        // Normal case: maximum safe integer
        result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(NumberPrototype.MAX_SAFE_INTEGER)});
        assertTrue(result.isBooleanTrue());

        // Normal case: minimum safe integer
        result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-NumberPrototype.MAX_SAFE_INTEGER)});
        assertTrue(result.isBooleanTrue());

        // Normal case: unsafe integer (too large)
        result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(NumberPrototype.MAX_SAFE_INTEGER + 1)});
        assertTrue(result.isBooleanFalse());

        // Normal case: float
        result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42.5)});
        assertTrue(result.isBooleanFalse());

        // Normal case: NaN
        result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertTrue(result.isBooleanFalse());

        // Normal case: Infinity
        result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertTrue(result.isBooleanFalse());

        // Normal case: non-number value
        result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        result = NumberPrototype.isSafeInteger(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(result.isBooleanFalse());
    }

    @Test
    public void testParseFloat() {
        // Normal case: valid float string
        JSValue result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42.5")});
        assertEquals(42.5, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: integer string
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("-123.45")});
        assertEquals(-123.45, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: scientific notation
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("1.23e4")});
        assertEquals(12300.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: leading/trailing whitespace
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("  42.5  ")});
        assertEquals(42.5, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: string starting with number
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42abc")});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Special case: Infinity
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("Infinity")});
        assertEquals(Double.POSITIVE_INFINITY, result.asNumber().map(JSNumber::value).orElseThrow());

        // Special case: NaN string
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("NaN")});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: invalid string
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: empty string
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("")});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: no arguments
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: non-string argument (should coerce)
        result = NumberPrototype.parseFloat(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42.5)});
        assertEquals(42.5, result.asNumber().map(JSNumber::value).orElseThrow());
    }

    @Test
    public void testParseInt() {
        // Normal case: valid integer string
        JSValue result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: float string (truncates)
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42.9")});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: negative number
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("-123")});
        assertEquals(-123.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: hexadecimal
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("0xFF"), new JSNumber(16)});
        assertEquals(255.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: auto-detect hex
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("0xFF")});
        assertEquals(255.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: binary
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("1010"), new JSNumber(2)});
        assertEquals(10.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: leading/trailing whitespace
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("  42  ")});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: string starting with number
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42abc")});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: invalid string
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: empty string
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("")});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: no arguments
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: invalid radix (too low)
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42"), new JSNumber(1)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: invalid radix (too high)
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("42"), new JSNumber(37)});
        assertTrue(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow()));

        // Edge case: non-string argument (should coerce)
        result = NumberPrototype.parseInt(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42.9)});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());
    }

    @Test
    public void testToExponential() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision (no argument - uses minimal precision)
        JSValue result = NumberPrototype.toExponential(ctx, num, new JSValue[]{});
        assertEquals("1.23456e+2", result.asString().map(JSString::value).orElseThrow());
        result = NumberPrototype.toExponential(ctx, new JSNumber(123123123123123.456D), new JSValue[]{});
        assertEquals("1.2312312312312345e+14", result.asString().map(JSString::value).orElseThrow());
        result = NumberPrototype.toExponential(ctx, new JSNumber(123123123123123123.456D), new JSValue[]{});
        assertEquals("1.2312312312312312e+17", result.asString().map(JSString::value).orElseThrow());

        // Normal case: specific precision
        result = NumberPrototype.toExponential(ctx, num, new JSValue[]{new JSNumber(2)});
        assertEquals("1.23e+2", result.asString().map(JSString::value).orElseThrow());

        // Normal case: zero
        result = NumberPrototype.toExponential(ctx, new JSNumber(0), new JSValue[]{new JSNumber(1)});
        assertEquals("0.0e+0", result.asString().map(JSString::value).orElseThrow());

        // Normal case: negative number
        result = NumberPrototype.toExponential(ctx, new JSNumber(-42.7), new JSValue[]{new JSNumber(3)});
        assertEquals("-4.270e+1", result.asString().map(JSString::value).orElseThrow());

        // Special case: NaN
        result = NumberPrototype.toExponential(ctx, new JSNumber(Double.NaN), new JSValue[]{});
        assertEquals("NaN", result.asString().map(JSString::value).orElseThrow());

        // Special case: Infinity
        result = NumberPrototype.toExponential(ctx, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertEquals("Infinity", result.asString().map(JSString::value).orElseThrow());

        // Special case: negative Infinity
        result = NumberPrototype.toExponential(ctx, new JSNumber(Double.NEGATIVE_INFINITY), new JSValue[]{});
        assertEquals("-Infinity", result.asString().map(JSString::value).orElseThrow());

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toExponential(ctx, num, new JSValue[]{new JSNumber(-1)}));
        assertPendingException(ctx);

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toExponential(ctx, num, new JSValue[]{new JSNumber(101)}));
        assertPendingException(ctx);
    }

    @Test
    public void testToExponentialWithJavet() throws JavetException {
        List<Double> testNumbers = List.of(
                0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D, -123456789.123456789D,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        try (V8Runtime v8Runtime = V8Host.getV8Instance().createV8Runtime()) {
            testNumbers.forEach(number -> {
                IntStream.range(0, 101).forEach(fractionDigits -> {
                    String expectedValue = null;
                    try {
                        expectedValue = v8Runtime.getExecutor("Number(" + number + ").toExponential(" + fractionDigits + ")").executeString();
                    } catch (JavetException e) {
                        fail(e);
                    }
                    assertEquals(
                            expectedValue,
                            NumberPrototype.toExponential(ctx, new JSNumber(number), new JSValue[]{new JSNumber(fractionDigits)}).asString().map(JSString::value).orElseThrow(),
                            "Number: " + number + ", fractionDigits: " + fractionDigits);
                });
                String expectedValue = null;
                try {
                    expectedValue = v8Runtime.getExecutor("Number(" + number + ").toExponential()").executeString();
                } catch (JavetException e) {
                    fail(e);
                }
                assertEquals(
                        expectedValue,
                        NumberPrototype.toExponential(ctx, new JSNumber(number), new JSValue[]{}).asString().map(JSString::value).orElseThrow(),
                        "Number: " + number);
            });
        }
    }

    @Test
    public void testToFixed() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision (0)
        JSValue result = NumberPrototype.toFixed(ctx, num, new JSValue[]{});
        assertEquals("123", result.asString().map(JSString::value).orElseThrow());
        result = NumberPrototype.toFixed(ctx, num, new JSValue[]{new JSNumber(2)});
        assertEquals("123.46", result.asString().map(JSString::value).orElseThrow());
        result = NumberPrototype.toFixed(ctx, num, new JSValue[]{new JSNumber(3)});
        assertEquals("123.456", result.asString().map(JSString::value).orElseThrow());
        result = NumberPrototype.toFixed(ctx, num, new JSValue[]{new JSNumber(10)});
        assertEquals("123.4560000000", result.asString().map(JSString::value).orElseThrow());

        // Normal case: rounding - 1.005 is actually ~1.0049999... in binary
        // so it rounds down to 1.00, not up to 1.01 (this matches JavaScript behavior)
        result = NumberPrototype.toFixed(ctx, new JSNumber(1.005), new JSValue[]{new JSNumber(2)});
        assertEquals("1.00", result.asString().map(JSString::value).orElseThrow());

        // Normal case: zero
        result = NumberPrototype.toFixed(ctx, new JSNumber(0), new JSValue[]{new JSNumber(2)});
        assertEquals("0.00", result.asString().map(JSString::value).orElseThrow());

        // Normal case: negative number
        result = NumberPrototype.toFixed(ctx, new JSNumber(-42.7), new JSValue[]{new JSNumber(1)});
        assertEquals("-42.7", result.asString().map(JSString::value).orElseThrow());

        // Special case: NaN
        result = NumberPrototype.toFixed(ctx, new JSNumber(Double.NaN), new JSValue[]{});
        assertEquals("NaN", result.asString().map(JSString::value).orElseThrow());

        // Special case: Infinity
        result = NumberPrototype.toFixed(ctx, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertEquals("Infinity", result.asString().map(JSString::value).orElseThrow());

        // Special case: large number
        result = NumberPrototype.toFixed(ctx, new JSNumber(1e22), new JSValue[]{});
        assertTrue(result.asString().map(JSString::value).orElseThrow().contains("e"));

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toFixed(ctx, num, new JSValue[]{new JSNumber(-1)}));
        assertPendingException(ctx);

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toFixed(ctx, num, new JSValue[]{new JSNumber(101)}));
        assertPendingException(ctx);

        // Edge case: non-number thisArg (should coerce)
        result = NumberPrototype.toFixed(ctx, new JSString("42.5"), new JSValue[]{new JSNumber(1)});
        assertEquals("42.5", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testToFixedWithJavet() throws JavetException {
        List<Double> testNumbers = List.of(
                0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D, -123456789.123456789D,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        try (V8Runtime v8Runtime = V8Host.getV8Instance().createV8Runtime()) {
            testNumbers.forEach(number -> {
                IntStream.range(0, 101).forEach(fractionDigits -> {
                    String expectedValue = null;
                    try {
                        expectedValue = v8Runtime.getExecutor("Number(" + number + ").toFixed(" + fractionDigits + ")").executeString();
                    } catch (JavetException e) {
                        fail(e);
                    }
                    assertEquals(
                            expectedValue,
                            NumberPrototype.toFixed(ctx, new JSNumber(number), new JSValue[]{new JSNumber(fractionDigits)}).asString().map(JSString::value).orElseThrow(),
                            "Number: " + number + ", fractionDigits: " + fractionDigits);
                });
                String expectedValue = null;
                try {
                    expectedValue = v8Runtime.getExecutor("Number(" + number + ").toFixed()").executeString();
                } catch (JavetException e) {
                    fail(e);
                }
                assertEquals(
                        expectedValue,
                        NumberPrototype.toFixed(ctx, new JSNumber(number), new JSValue[]{}).asString().map(JSString::value).orElseThrow(),
                        "Number: " + number);
            });
        }
    }

    @Test
    public void testToLocaleString() {
        JSNumber num = new JSNumber(1234.56);

        // Normal case: basic functionality
        JSValue result = NumberPrototype.toLocaleString(ctx, num, new JSValue[]{});
        // Simplified implementation just uses toString, so should match
        assertEquals("1234.56", result.asString().map(JSString::value).orElseThrow());

        // Special case: NaN
        result = NumberPrototype.toLocaleString(ctx, new JSNumber(Double.NaN), new JSValue[]{});
        assertEquals("NaN", result.asString().map(JSString::value).orElseThrow());

        // Special case: Infinity
        result = NumberPrototype.toLocaleString(ctx, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertEquals("Infinity", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testToPrecision() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision
        JSValue result = NumberPrototype.toPrecision(ctx, num, new JSValue[]{});
        assertEquals("123.456", result.asString().map(JSString::value).orElseThrow());

        // Normal case: specific precision
        result = NumberPrototype.toPrecision(ctx, num, new JSValue[]{new JSNumber(4)});
        assertEquals("123.5", result.asString().map(JSString::value).orElseThrow());

        // Normal case: exponential notation for small numbers
        result = NumberPrototype.toPrecision(ctx, new JSNumber(0.000123), new JSValue[]{new JSNumber(3)});
        assertEquals("0.000123", result.asString().map(JSString::value).orElseThrow());

        // Normal case: exponential notation for large numbers
        result = NumberPrototype.toPrecision(ctx, new JSNumber(123456789), new JSValue[]{new JSNumber(4)});
        assertEquals("1.235e+8", result.asString().map(JSString::value).orElseThrow());

        // Normal case: zero
        result = NumberPrototype.toPrecision(ctx, new JSNumber(0), new JSValue[]{new JSNumber(3)});
        assertEquals("0.00", result.asString().map(JSString::value).orElseThrow());

        // Special case: NaN
        result = NumberPrototype.toPrecision(ctx, new JSNumber(Double.NaN), new JSValue[]{});
        assertEquals("NaN", result.asString().map(JSString::value).orElseThrow());

        // Special case: Infinity
        result = NumberPrototype.toPrecision(ctx, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertEquals("Infinity", result.asString().map(JSString::value).orElseThrow());

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toPrecision(ctx, num, new JSValue[]{new JSNumber(0)}));
        assertPendingException(ctx);

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toPrecision(ctx, num, new JSValue[]{new JSNumber(101)}));
        assertPendingException(ctx);
    }

    @Test
    public void testToPrecisionWithJavet() throws JavetException {
        List<Double> testNumbers = List.of(
                0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D, -123456789.123456789D,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        try (V8Runtime v8Runtime = V8Host.getV8Instance().createV8Runtime()) {
            testNumbers.forEach(number -> {
                IntStream.range(1, 101).forEach(precision -> {
                    String expectedValue = null;
                    try {
                        expectedValue = v8Runtime.getExecutor("Number(" + number + ").toPrecision(" + precision + ")").executeString();
                    } catch (JavetException e) {
                        fail(e);
                    }
                    assertEquals(
                            expectedValue,
                            NumberPrototype.toPrecision(ctx, new JSNumber(number), new JSValue[]{new JSNumber(precision)}).asString().map(JSString::value).orElseThrow(),
                            "Number: " + number + ", fractionDigits: " + precision);
                });
                String expectedValue = null;
                try {
                    expectedValue = v8Runtime.getExecutor("Number(" + number + ").toPrecision()").executeString();
                } catch (JavetException e) {
                    fail(e);
                }
                assertEquals(
                        expectedValue,
                        NumberPrototype.toPrecision(ctx, new JSNumber(number), new JSValue[]{}).asString().map(JSString::value).orElseThrow(),
                        "Number: " + number);
            });
        }
    }

    @Test
    public void testToString() {
        JSNumber num = new JSNumber(42);

        // Normal case: default radix (10)
        JSValue result = NumberPrototype.toString(ctx, num, new JSValue[]{});
        assertEquals("42", result.asString().map(JSString::value).orElseThrow());

        // Normal case: decimal number
        result = NumberPrototype.toString(ctx, new JSNumber(123.456), new JSValue[]{});
        assertEquals("123.456", result.asString().map(JSString::value).orElseThrow());

        // Normal case: different radix
        result = NumberPrototype.toString(ctx, num, new JSValue[]{new JSNumber(16)});
        assertEquals("2a", result.asString().map(JSString::value).orElseThrow());

        // Normal case: binary
        result = NumberPrototype.toString(ctx, num, new JSValue[]{new JSNumber(2)});
        assertEquals("101010", result.asString().map(JSString::value).orElseThrow());

        // Normal case: negative number
        result = NumberPrototype.toString(ctx, new JSNumber(-42), new JSValue[]{new JSNumber(16)});
        assertEquals("-2a", result.asString().map(JSString::value).orElseThrow());

        // Special case: NaN
        result = NumberPrototype.toString(ctx, new JSNumber(Double.NaN), new JSValue[]{});
        assertEquals("NaN", result.asString().map(JSString::value).orElseThrow());

        // Special case: Infinity
        result = NumberPrototype.toString(ctx, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertEquals("Infinity", result.asString().map(JSString::value).orElseThrow());

        // Special case: zero
        result = NumberPrototype.toString(ctx, new JSNumber(0), new JSValue[]{});
        assertEquals("0", result.asString().map(JSString::value).orElseThrow());

        // Edge case: radix too low - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toString(ctx, num, new JSValue[]{new JSNumber(1)}));
        assertPendingException(ctx);

        // Edge case: radix too high - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toString(ctx, num, new JSValue[]{new JSNumber(37)}));
        assertPendingException(ctx);

        // Edge case: non-integer with non-10 radix (now supported for floating-point conversion)
        result = NumberPrototype.toString(ctx, new JSNumber(42.5), new JSValue[]{new JSNumber(16)});
        assertEquals("2a.8", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testToStringWithJavet() throws JavetException {
        List<Double> testNumbers = List.of(
                0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D, -123456789.123456789D,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        try (V8Runtime v8Runtime = V8Host.getV8Instance().createV8Runtime()) {
            testNumbers.forEach(number -> IntStream.range(2, 37).forEach(radix -> {
                String expectedValue = null;
                try {
                    expectedValue = v8Runtime.getExecutor("Number(" + number + ").toString(" + radix + ")").executeString();
                } catch (JavetException e) {
                    fail(e);
                }
                assertEquals(
                        expectedValue,
                        NumberPrototype.toString(ctx, new JSNumber(number), new JSValue[]{new JSNumber(radix)}).asString().map(JSString::value).orElseThrow(),
                        "Number: " + number + ", radix: " + radix);
            }));
        }
    }

    @Test
    public void testValueOf() {
        JSNumber num = new JSNumber(42.5);

        // Normal case: number object
        JSValue result = NumberPrototype.valueOf(ctx, num, new JSValue[]{});
        assertEquals(num, result);
        assertEquals(42.5, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: called on non-number
        assertTypeError(NumberPrototype.valueOf(ctx, new JSString("42"), new JSValue[]{}));
        assertPendingException(ctx);
    }
}