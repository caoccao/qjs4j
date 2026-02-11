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
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Number.prototype methods.
 */
public class NumberPrototypeTest extends BaseJavetTest {
    private void assertInvalidNumericLiteral(String source) {
        assertThatThrownBy(() -> resetContext().eval(source))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet(
                // Verify that loose equality passes between primitive and primitive
                "123 == 123",
                "123 == 321",
                "123 == Number(123)",
                "123 == Number(321)",
                // Verify that strict equality passes between primitive and primitive
                "123 === 123",
                "123 === 321",
                "123 === Number(123)",
                "123 === Number(321)",
                // Verify that loose equality passes between primitive and primitive
                "Number(123) == Number(123)",
                "Number(123) == Number(321)",
                "Number(123) == 123",
                "Number(123) == 321",
                // Verify that loose equality passes between primitive and object
                "123 == new Number(123)",
                "123 == new Number(321)",
                "Number(123) == new Number(123)",
                "Number(123) == new Number(321)",
                // Verify that loose equality fails between object and object
                "new Number(123) == new Number(123)",
                // Verify that strict equality fails between primitive and object
                "123 === new Number(123)",
                // Verify that strict equality fails between object and object
                "new Number(123) === new Number(123)");
    }

    @Test
    public void testIsFinite() {
        // Normal case: finite number
        JSValue result = NumberPrototype.isFinite(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: NaN
        result = NumberPrototype.isFinite(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: positive Infinity
        result = NumberPrototype.isFinite(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: negative Infinity
        result = NumberPrototype.isFinite(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NEGATIVE_INFINITY)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: non-number value
        result = NumberPrototype.isFinite(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: no arguments
        result = NumberPrototype.isFinite(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testIsInteger() {
        // Normal case: integer
        JSValue result = NumberPrototype.isInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: negative integer
        result = NumberPrototype.isInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-17)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: zero
        result = NumberPrototype.isInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(0)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: float
        result = NumberPrototype.isInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42.5)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: NaN
        result = NumberPrototype.isInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: Infinity
        result = NumberPrototype.isInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: non-number value
        result = NumberPrototype.isInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: no arguments
        result = NumberPrototype.isInteger(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testIsNaN() {
        // Normal case: NaN number
        JSValue result = NumberPrototype.isNaN(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: finite number
        result = NumberPrototype.isNaN(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: Infinity
        result = NumberPrototype.isNaN(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: non-number value
        result = NumberPrototype.isNaN(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not a number")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: no arguments
        result = NumberPrototype.isNaN(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testIsSafeInteger() {
        // Normal case: safe integer
        JSValue result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: maximum safe integer
        result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(NumberPrototype.MAX_SAFE_INTEGER)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: minimum safe integer
        result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-NumberPrototype.MAX_SAFE_INTEGER)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: unsafe integer (too large)
        result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(NumberPrototype.MAX_SAFE_INTEGER + 1)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: float
        result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42.5)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: NaN
        result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: Infinity
        result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: non-number value
        result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: no arguments
        result = NumberPrototype.isSafeInteger(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testNumberPropertyDescriptorsWithJavet() {
        assertBooleanWithJavet(
                "Number.parseInt === parseInt",
                "Number.parseFloat === parseFloat",
                "Object.getOwnPropertyDescriptor(Number, 'parseInt').writable === true",
                "Object.getOwnPropertyDescriptor(Number, 'parseInt').enumerable === false",
                "Object.getOwnPropertyDescriptor(Number, 'parseInt').configurable === true",
                "Object.getOwnPropertyDescriptor(Number.prototype, 'toString').writable === true",
                "Object.getOwnPropertyDescriptor(Number.prototype, 'toString').enumerable === false",
                "Object.getOwnPropertyDescriptor(Number.prototype, 'toString').configurable === true",
                "Object.getOwnPropertyDescriptor(Number, 'prototype').writable === false",
                "Object.getOwnPropertyDescriptor(Number, 'prototype').enumerable === false",
                "Object.getOwnPropertyDescriptor(Number, 'prototype').configurable === false");
    }

    @Test
    public void testNumericSeparatorsInvalidLiterals() {
        assertInvalidNumericLiteral("0_1");
        assertInvalidNumericLiteral("1__0");
        assertInvalidNumericLiteral("1_");
        assertInvalidNumericLiteral("0x_FF");
        assertInvalidNumericLiteral("0xFF_");
        assertInvalidNumericLiteral("0b_10");
        assertInvalidNumericLiteral("0b10_");
        assertInvalidNumericLiteral("0o_77");
        assertInvalidNumericLiteral("0o77_");
        assertInvalidNumericLiteral("1_.0");
        assertInvalidNumericLiteral("1._0");
        assertInvalidNumericLiteral("1e_1");
        assertInvalidNumericLiteral("1e+_1");
        assertInvalidNumericLiteral("1e1_");
        assertInvalidNumericLiteral("1_n");
        assertInvalidNumericLiteral("0b_1n");
        assertInvalidNumericLiteral("0b1_n");
    }

    @Test
    public void testNumericSeparatorsValidLiterals() {
        assertBooleanWithJavet("""
                1_000_000 === 1000000
                    && 0b1010_1010 === 170
                    && 0o7_5_5 === 493
                    && 0xAB_CD === 43981
                    && 12.34_56 === 12.3456
                    && 1_2.3_4 === 12.34
                    && 1.2e3_4 === 1.2e34
                    && 1_2e3_4 === 1.2e35
                    && 1e+1_0 === 1e10
                    && 1e-1_0 === 1e-10
                    && 123_456n === 123456n
                    && 1_0n === 10n
                    && 0b1010_1010n === 170n
                    && 0o7_7n === 63n
                    && 0xA_Bn === 171n""");
    }

    @Test
    public void testParseFloat() {
        // Normal case: valid float string
        JSValue result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42.5")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.5);

        // Normal case: integer string
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Normal case: negative number
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("-123.45")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-123.45);

        // Normal case: scientific notation
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("1.23e4")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(12300.0);

        // Normal case: leading/trailing whitespace
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("  42.5  ")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.5);

        // Normal case: string starting with number
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42abc")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Special case: Infinity
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("Infinity")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(Double.POSITIVE_INFINITY);

        // Special case: NaN string
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("NaN")});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: invalid string
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: empty string
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("")});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: no arguments
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: non-string argument (should coerce)
        result = NumberPrototype.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42.5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.5);
    }

    @Test
    public void testParseInt() {
        // Normal case: valid integer string
        JSValue result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Normal case: float string (truncates)
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42.9")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Normal case: negative number
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("-123")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-123.0);

        // Normal case: hexadecimal
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("0xFF"), new JSNumber(16)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(255.0);

        // Normal case: auto-detect hex
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("0xFF")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(255.0);

        // Normal case: binary
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("1010"), new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0);

        // Normal case: leading/trailing whitespace
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("  42  ")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Normal case: string starting with number
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42abc")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Edge case: invalid string
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: empty string
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("")});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: no arguments
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: invalid radix (too low)
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42"), new JSNumber(1)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: invalid radix (too high)
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42"), new JSNumber(37)});
        assertThat(Double.isNaN(result.asNumber().map(JSNumber::value).orElseThrow())).isTrue();

        // Edge case: non-string argument (should coerce)
        result = NumberPrototype.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42.9)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);
    }

    @Test
    public void testParseIntWithJavet() {
        assertBooleanWithJavet(
                "parseInt('-0xF') === -15",
                "Number.parseInt('-0xF') === -15",
                "parseInt('+0x10') === 16",
                "Number.parseInt('+0x10') === 16",
                "parseInt('0o10') === 0",
                "Number.parseInt('0o10') === 0",
                "parseInt('0b10') === 0",
                "Number.parseInt('0b10') === 0");
    }

    @Test
    public void testToExponential() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision (no argument - uses minimal precision)
        JSValue result = NumberPrototype.toExponential(context, num, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("1.23456e+2");
        result = NumberPrototype.toExponential(context, new JSNumber(123123123123123.456D), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("1.2312312312312345e+14");
        result = NumberPrototype.toExponential(context, new JSNumber(123123123123123123.456D), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("1.2312312312312312e+17");

        // Normal case: specific precision
        result = NumberPrototype.toExponential(context, num, new JSValue[]{new JSNumber(2)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("1.23e+2");

        // Normal case: zero
        result = NumberPrototype.toExponential(context, new JSNumber(0), new JSValue[]{new JSNumber(1)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("0.0e+0");

        // Normal case: negative number
        result = NumberPrototype.toExponential(context, new JSNumber(-42.7), new JSValue[]{new JSNumber(3)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("-4.270e+1");

        // Special case: NaN
        result = NumberPrototype.toExponential(context, new JSNumber(Double.NaN), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("NaN");

        // Special case: Infinity
        result = NumberPrototype.toExponential(context, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("Infinity");

        // Special case: negative Infinity
        result = NumberPrototype.toExponential(context, new JSNumber(Double.NEGATIVE_INFINITY), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("-Infinity");

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toExponential(context, num, new JSValue[]{new JSNumber(-1)}));
        assertPendingException(context);

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toExponential(context, num, new JSValue[]{new JSNumber(101)}));
        assertPendingException(context);
    }

    @Test
    public void testToExponentialWithJavet() {
        List<Double> testNumbers = List.of(
                0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D, -123456789.123456789D,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        testNumbers.forEach(number -> {
            IntStream.range(0, 101).forEach(fractionDigits -> assertWithJavet(
                    () -> v8Runtime.getExecutor("Number(" + number + ").toExponential(" + fractionDigits + ")").executeString(),
                    () -> NumberPrototype.toExponential(context, new JSNumber(number), new JSValue[]{new JSNumber(fractionDigits)}).asString().map(JSString::value).orElseThrow()));
            assertWithJavet(
                    () -> v8Runtime.getExecutor("Number(" + number + ").toExponential()").executeString(),
                    () -> NumberPrototype.toExponential(context, new JSNumber(number), new JSValue[]{}).asString().map(JSString::value).orElseThrow());
        });
    }

    @Test
    public void testToFixed() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision (0)
        JSValue result = NumberPrototype.toFixed(context, num, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("123");
        result = NumberPrototype.toFixed(context, num, new JSValue[]{new JSNumber(2)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("123.46");
        result = NumberPrototype.toFixed(context, num, new JSValue[]{new JSNumber(3)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("123.456");
        result = NumberPrototype.toFixed(context, num, new JSValue[]{new JSNumber(10)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("123.4560000000");

        // Normal case: rounding - 1.005 is actually ~1.0049999... in binary
        // so it rounds down to 1.00, not up to 1.01 (this matches JavaScript behavior)
        result = NumberPrototype.toFixed(context, new JSNumber(1.005), new JSValue[]{new JSNumber(2)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("1.00");

        // Normal case: zero
        result = NumberPrototype.toFixed(context, new JSNumber(0), new JSValue[]{new JSNumber(2)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("0.00");

        // Normal case: negative number
        result = NumberPrototype.toFixed(context, new JSNumber(-42.7), new JSValue[]{new JSNumber(1)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("-42.7");

        // Special case: NaN
        result = NumberPrototype.toFixed(context, new JSNumber(Double.NaN), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("NaN");

        // Special case: Infinity
        result = NumberPrototype.toFixed(context, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("Infinity");

        // Special case: large number
        result = NumberPrototype.toFixed(context, new JSNumber(1e22), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).satisfies(s -> assertThat(s.contains("e")).isTrue());

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toFixed(context, num, new JSValue[]{new JSNumber(-1)}));
        assertPendingException(context);

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toFixed(context, num, new JSValue[]{new JSNumber(101)}));
        assertPendingException(context);
    }

    @Test
    public void testToFixedWithJavet() {
        List<Double> testNumbers = List.of(
                0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D, -123456789.123456789D,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        testNumbers.forEach(number -> {
            IntStream.range(0, 101).forEach(fractionDigits -> assertWithJavet(
                    () -> v8Runtime.getExecutor("Number(" + number + ").toFixed(" + fractionDigits + ")").executeString(),
                    () -> NumberPrototype.toFixed(context, new JSNumber(number), new JSValue[]{new JSNumber(fractionDigits)}).asString().map(JSString::value).orElseThrow()));
            assertWithJavet(
                    () -> v8Runtime.getExecutor("Number(" + number + ").toFixed()").executeString(),
                    () -> NumberPrototype.toFixed(context, new JSNumber(number), new JSValue[]{}).asString().map(JSString::value).orElseThrow());
        });
    }

    @Test
    public void testToLocaleString() {
        JSNumber num = new JSNumber(1234.56);

        // Normal case: basic functionality
        JSValue result = NumberPrototype.toLocaleString(context, num, new JSValue[]{});
        // Simplified implementation just uses toString, so should match
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("1234.56");

        // Special case: NaN
        result = NumberPrototype.toLocaleString(context, new JSNumber(Double.NaN), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("NaN");

        // Special case: Infinity
        result = NumberPrototype.toLocaleString(context, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("Infinity");
    }

    @Test
    public void testToPrecision() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision
        JSValue result = NumberPrototype.toPrecision(context, num, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("123.456");

        // Normal case: specific precision
        result = NumberPrototype.toPrecision(context, num, new JSValue[]{new JSNumber(4)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("123.5");

        // Normal case: exponential notation for small numbers
        result = NumberPrototype.toPrecision(context, new JSNumber(0.000123), new JSValue[]{new JSNumber(3)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("0.000123");

        // Normal case: exponential notation for large numbers
        result = NumberPrototype.toPrecision(context, new JSNumber(123456789), new JSValue[]{new JSNumber(4)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("1.235e+8");

        // Normal case: zero
        result = NumberPrototype.toPrecision(context, new JSNumber(0), new JSValue[]{new JSNumber(3)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("0.00");

        // Special case: NaN
        result = NumberPrototype.toPrecision(context, new JSNumber(Double.NaN), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("NaN");

        // Special case: Infinity
        result = NumberPrototype.toPrecision(context, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("Infinity");

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toPrecision(context, num, new JSValue[]{new JSNumber(0)}));
        assertPendingException(context);

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toPrecision(context, num, new JSValue[]{new JSNumber(101)}));
        assertPendingException(context);
    }

    @Test
    public void testToPrecisionWithJavet() {
        List<Double> testNumbers = List.of(
                0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D, -123456789.123456789D,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        testNumbers.forEach(number -> {
            IntStream.range(1, 101).forEach(precision -> assertWithJavet(
                    () -> v8Runtime.getExecutor("Number(" + number + ").toPrecision(" + precision + ")").executeString(),
                    () -> NumberPrototype.toPrecision(context, new JSNumber(number), new JSValue[]{new JSNumber(precision)}).asString().map(JSString::value).orElseThrow()));
            assertWithJavet(
                    () -> v8Runtime.getExecutor("Number(" + number + ").toPrecision()").executeString(),
                    () -> NumberPrototype.toPrecision(context, new JSNumber(number), new JSValue[]{}).asString().map(JSString::value).orElseThrow());
        });
    }

    @Test
    public void testToString() {
        JSNumber num = new JSNumber(42);

        // Normal case: default radix (10)
        JSValue result = NumberPrototype.toString(context, num, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("42");

        // Normal case: decimal number
        result = NumberPrototype.toString(context, new JSNumber(123.456), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("123.456");

        // Normal case: different radix
        result = NumberPrototype.toString(context, num, new JSValue[]{new JSNumber(16)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("2a");

        // Normal case: binary
        result = NumberPrototype.toString(context, num, new JSValue[]{new JSNumber(2)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("101010");

        // Normal case: negative number
        result = NumberPrototype.toString(context, new JSNumber(-42), new JSValue[]{new JSNumber(16)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("-2a");

        // Special case: NaN
        result = NumberPrototype.toString(context, new JSNumber(Double.NaN), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("NaN");

        // Special case: Infinity
        result = NumberPrototype.toString(context, new JSNumber(Double.POSITIVE_INFINITY), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("Infinity");

        // Special case: zero
        result = NumberPrototype.toString(context, new JSNumber(0), new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("0");

        // Edge case: radix too low - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toString(context, num, new JSValue[]{new JSNumber(1)}));
        assertPendingException(context);

        // Edge case: radix too high - skip error check due to JSObject implementation
        assertRangeError(NumberPrototype.toString(context, num, new JSValue[]{new JSNumber(37)}));
        assertPendingException(context);

        // Edge case: non-integer with non-10 radix (now supported for floating-point conversion)
        result = NumberPrototype.toString(context, new JSNumber(42.5), new JSValue[]{new JSNumber(16)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("2a.8");
    }

    @Test
    public void testToStringWithJavet() {
        List<Double> testNumbers = List.of(
                0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D, -123456789.123456789D,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        testNumbers.forEach(number -> IntStream.range(2, 37).forEach(radix -> assertWithJavet(
                () -> v8Runtime.getExecutor("Number(" + number + ").toString(" + radix + ")").executeString(),
                () -> NumberPrototype.toString(context, new JSNumber(number), new JSValue[]{new JSNumber(radix)}).asString().map(JSString::value).orElseThrow())));
    }

    @Test
    public void testValueOf() {
        JSNumber num = new JSNumber(42.5);

        // Normal case: number object
        JSValue result = NumberPrototype.valueOf(context, num, new JSValue[]{});
        assertThat(result).isEqualTo(num);
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.5);

        // Edge case: called on non-number
        assertTypeError(NumberPrototype.valueOf(context, new JSString("42"), new JSValue[]{}));
        assertPendingException(context);
    }
}
