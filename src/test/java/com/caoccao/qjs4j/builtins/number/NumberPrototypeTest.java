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
        assertThatThrownBy(() -> resetContext().eval(source)).isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    private void assertNumberFormat(String method, JSNativeCallback callback, int firstDigit) {
        List<Double> testNumbers = List.of(0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D,
                -123456789.123456789D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        testNumbers.forEach(number -> {
            IntStream.range(firstDigit, 101)
                    .forEach(
                            fractionDigits -> assertWithJavet(
                                    () -> v8Runtime
                                            .getExecutor(
                                                    "Number(" + number + ")." + method + "(" + fractionDigits + ")")
                                            .executeString(),
                                    () -> callback.call(context, new JSNumber(number), numberArgs(fractionDigits))
                                            .asString().map(JSString::value).orElseThrow()));
            assertWithJavet(() -> v8Runtime.getExecutor("Number(" + number + ")." + method + "()").executeString(),
                    () -> callback.call(context, new JSNumber(number), JSValue.NO_ARGS).asString().map(JSString::value)
                            .orElseThrow());
        });
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet(
                // Verify that loose equality passes between primitive and primitive
                "123 == 123", "123 == 321", "123 == Number(123)", "123 == Number(321)",
                // Verify that strict equality passes between primitive and primitive
                "123 === 123", "123 === 321", "123 === Number(123)", "123 === Number(321)",
                // Verify that loose equality passes between primitive and primitive
                "Number(123) == Number(123)", "Number(123) == Number(321)", "Number(123) == 123", "Number(123) == 321",
                // Verify that loose equality passes between primitive and object
                "123 == new Number(123)", "123 == new Number(321)", "Number(123) == new Number(123)",
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
        BuiltinAssertions isFinite = builtin(NumberPrototype::isFinite, JSUndefined.INSTANCE);
        isFinite.expectValue(JSBoolean.TRUE, numberArgs(42));

        // Normal case: NaN
        isFinite.expectValue(JSBoolean.FALSE, numberArgs(Double.NaN));

        // Normal case: positive Infinity
        isFinite.expectValue(JSBoolean.FALSE, numberArgs(Double.POSITIVE_INFINITY));

        // Normal case: negative Infinity
        isFinite.expectValue(JSBoolean.FALSE, numberArgs(Double.NEGATIVE_INFINITY));

        // Normal case: non-number value
        isFinite.expectValue(JSBoolean.FALSE, stringArgs("42"));

        // Edge case: no arguments
        isFinite.expectValue(JSBoolean.FALSE);
    }

    @Test
    public void testIsInteger() {
        // Normal case: integer
        BuiltinAssertions isInteger = builtin(NumberPrototype::isInteger, JSUndefined.INSTANCE);
        isInteger.expectValue(JSBoolean.TRUE, numberArgs(42));

        // Normal case: negative integer
        isInteger.expectValue(JSBoolean.TRUE, numberArgs(-17));

        // Normal case: zero
        isInteger.expectValue(JSBoolean.TRUE, numberArgs(0));

        // Normal case: float
        isInteger.expectValue(JSBoolean.FALSE, numberArgs(42.5));

        // Normal case: NaN
        isInteger.expectValue(JSBoolean.FALSE, numberArgs(Double.NaN));

        // Normal case: Infinity
        isInteger.expectValue(JSBoolean.FALSE, numberArgs(Double.POSITIVE_INFINITY));

        // Normal case: non-number value
        isInteger.expectValue(JSBoolean.FALSE, stringArgs("42"));

        // Edge case: no arguments
        isInteger.expectValue(JSBoolean.FALSE);
    }

    @Test
    public void testIsNaN() {
        // Normal case: NaN number
        BuiltinAssertions isNaN = builtin(NumberPrototype::isNaN, JSUndefined.INSTANCE);
        isNaN.expectValue(JSBoolean.TRUE, numberArgs(Double.NaN));

        // Normal case: finite number
        isNaN.expectValue(JSBoolean.FALSE, numberArgs(42));

        // Normal case: Infinity
        isNaN.expectValue(JSBoolean.FALSE, numberArgs(Double.POSITIVE_INFINITY));

        // Normal case: non-number value
        isNaN.expectValue(JSBoolean.FALSE, stringArgs("not a number"));

        // Edge case: no arguments
        isNaN.expectValue(JSBoolean.FALSE);
    }

    @Test
    public void testIsSafeInteger() {
        // Normal case: safe integer
        BuiltinAssertions isSafeInteger = builtin(NumberPrototype::isSafeInteger, JSUndefined.INSTANCE);
        isSafeInteger.expectValue(JSBoolean.TRUE, numberArgs(42));

        // Normal case: maximum safe integer
        isSafeInteger.expectValue(JSBoolean.TRUE, numberArgs(NumberPrototype.MAX_SAFE_INTEGER));

        // Normal case: minimum safe integer
        isSafeInteger.expectValue(JSBoolean.TRUE, numberArgs(-NumberPrototype.MAX_SAFE_INTEGER));

        // Normal case: unsafe integer (too large)
        isSafeInteger.expectValue(JSBoolean.FALSE, new JSNumber(NumberPrototype.MAX_SAFE_INTEGER + 1));

        // Normal case: float
        isSafeInteger.expectValue(JSBoolean.FALSE, numberArgs(42.5));

        // Normal case: NaN
        isSafeInteger.expectValue(JSBoolean.FALSE, numberArgs(Double.NaN));

        // Normal case: Infinity
        isSafeInteger.expectValue(JSBoolean.FALSE, numberArgs(Double.POSITIVE_INFINITY));

        // Normal case: non-number value
        isSafeInteger.expectValue(JSBoolean.FALSE, stringArgs("42"));

        // Edge case: no arguments
        isSafeInteger.expectValue(JSBoolean.FALSE);
    }

    @Test
    public void testNumberPropertyDescriptorsWithJavet() {
        assertBooleanWithJavet("Number.parseInt === parseInt", "Number.parseFloat === parseFloat",
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
        BuiltinAssertions parseFloat = builtin(NumberPrototype::parseFloat, JSUndefined.INSTANCE);
        parseFloat.expectNumber(42.5, stringArgs("42.5"));

        // Normal case: integer string
        parseFloat.expectNumber(42.0, stringArgs("42"));

        // Normal case: negative number
        parseFloat.expectNumber(-123.45, stringArgs("-123.45"));

        // Normal case: scientific notation
        parseFloat.expectNumber(12300.0, stringArgs("1.23e4"));

        // Normal case: leading/trailing whitespace
        parseFloat.expectNumber(42.5, stringArgs("  42.5  "));

        // Normal case: string starting with number
        parseFloat.expectNumber(42.0, stringArgs("42abc"));

        // Special case: Infinity
        parseFloat.expectNumber(Double.POSITIVE_INFINITY, stringArgs("Infinity"));

        // Special case: NaN string
        parseFloat.expectNaN(stringArgs("NaN"));

        // Edge case: invalid string
        parseFloat.expectNaN(stringArgs("abc"));

        // Edge case: empty string
        parseFloat.expectNaN(stringArgs(""));

        // Edge case: no arguments
        parseFloat.expectNaN();

        // Edge case: non-string argument (should coerce)
        parseFloat.expectNumber(42.5, numberArgs(42.5));
    }

    @Test
    public void testParseInt() {
        // Normal case: valid integer string
        BuiltinAssertions parseInt = builtin(NumberPrototype::parseInt, JSUndefined.INSTANCE);
        parseInt.expectNumber(42.0, stringArgs("42"));

        // Normal case: float string (truncates)
        parseInt.expectNumber(42.0, stringArgs("42.9"));

        // Normal case: negative number
        parseInt.expectNumber(-123.0, stringArgs("-123"));

        // Normal case: hexadecimal
        parseInt.expectNumber(255.0, new JSString("0xFF"), new JSNumber(16));

        // Normal case: auto-detect hex
        parseInt.expectNumber(255.0, stringArgs("0xFF"));

        // Normal case: binary
        parseInt.expectNumber(10.0, new JSString("1010"), new JSNumber(2));

        // Normal case: leading/trailing whitespace
        parseInt.expectNumber(42.0, stringArgs("  42  "));

        // Normal case: string starting with number
        parseInt.expectNumber(42.0, stringArgs("42abc"));

        // Edge case: invalid string
        parseInt.expectNaN(stringArgs("abc"));

        // Edge case: empty string
        parseInt.expectNaN(stringArgs(""));

        // Edge case: no arguments
        parseInt.expectNaN();

        // Edge case: invalid radix (too low)
        parseInt.expectNaN(new JSString("42"), new JSNumber(1));

        // Edge case: invalid radix (too high)
        parseInt.expectNaN(new JSString("42"), new JSNumber(37));

        // Edge case: non-string argument (should coerce)
        parseInt.expectNumber(42.0, numberArgs(42.9));
    }

    @Test
    public void testParseIntWithJavet() {
        assertBooleanWithJavet("parseInt('-0xF') === -15", "Number.parseInt('-0xF') === -15",
                "parseInt('+0x10') === 16", "Number.parseInt('+0x10') === 16", "parseInt('0o10') === 0",
                "Number.parseInt('0o10') === 0", "parseInt('0b10') === 0", "Number.parseInt('0b10') === 0");
    }

    @Test
    public void testToExponential() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision (no argument - uses minimal precision)
        JSValue result = NumberPrototype.toExponential(context, num, JSValue.NO_ARGS);
        assertStringValue(result, "1.23456e+2");
        result = NumberPrototype.toExponential(context, new JSNumber(123123123123123.456D), JSValue.NO_ARGS);
        assertStringValue(result, "1.2312312312312345e+14");
        result = NumberPrototype.toExponential(context, new JSNumber(123123123123123123.456D), JSValue.NO_ARGS);
        assertStringValue(result, "1.2312312312312312e+17");

        // Normal case: specific precision
        result = NumberPrototype.toExponential(context, num, numberArgs(2));
        assertStringValue(result, "1.23e+2");

        // Normal case: zero
        result = NumberPrototype.toExponential(context, new JSNumber(0), numberArgs(1));
        assertStringValue(result, "0.0e+0");

        // Normal case: negative number
        result = NumberPrototype.toExponential(context, new JSNumber(-42.7), numberArgs(3));
        assertStringValue(result, "-4.270e+1");

        // Special case: NaN
        result = NumberPrototype.toExponential(context, new JSNumber(Double.NaN), JSValue.NO_ARGS);
        assertStringValue(result, "NaN");

        // Special case: Infinity
        result = NumberPrototype.toExponential(context, new JSNumber(Double.POSITIVE_INFINITY), JSValue.NO_ARGS);
        assertStringValue(result, "Infinity");

        // Special case: negative Infinity
        result = NumberPrototype.toExponential(context, new JSNumber(Double.NEGATIVE_INFINITY), JSValue.NO_ARGS);
        assertStringValue(result, "-Infinity");

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertPendingRangeError(context, NumberPrototype.toExponential(context, num, numberArgs(-1)));

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertPendingRangeError(context, NumberPrototype.toExponential(context, num, numberArgs(101)));
    }

    @Test
    public void testToExponentialShortestRepresentationWithJavet() {
        // Test toExponential() without arguments (shortest representation in exponential form)
        assertStringWithJavet("(0).toExponential()", "(-0).toExponential()", "(1).toExponential()",
                "(-1).toExponential()", "(123).toExponential()", "(123.456).toExponential()",
                "(-123.456).toExponential()", "(0.001).toExponential()", "(0.0001).toExponential()",
                "(-0.0001).toExponential()", "(1e20).toExponential()", "(1e-20).toExponential()",
                "(1000000000000000128).toExponential()", "(9007199254740992).toExponential()",
                "(1.7976931348623157e+308).toExponential()", "(5e-324).toExponential()",
                "(3.141592653589793).toExponential()", "(999999999999999).toExponential()",
                "(12345678.9).toExponential()");
    }

    @Test
    public void testToExponentialWithJavet() {
        assertNumberFormat("toExponential", NumberPrototype::toExponential, 0);
    }

    @Test
    public void testToFixed() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision (0)
        BuiltinAssertions toFixed = builtin(NumberPrototype::toFixed, num);
        toFixed.expectString("123");
        toFixed.expectString("123.46", numberArgs(2));
        toFixed.expectString("123.456", numberArgs(3));
        toFixed.expectString("123.4560000000", numberArgs(10));

        // Normal case: rounding - 1.005 is actually ~1.0049999... in binary
        // so it rounds down to 1.00, not up to 1.01 (this matches JavaScript behavior)
        JSValue result = NumberPrototype.toFixed(context, new JSNumber(1.005), numberArgs(2));
        assertStringValue(result, "1.00");

        // Normal case: zero
        result = NumberPrototype.toFixed(context, new JSNumber(0), numberArgs(2));
        assertStringValue(result, "0.00");

        // Normal case: negative number
        result = NumberPrototype.toFixed(context, new JSNumber(-42.7), numberArgs(1));
        assertStringValue(result, "-42.7");

        // Special case: NaN
        result = NumberPrototype.toFixed(context, new JSNumber(Double.NaN), JSValue.NO_ARGS);
        assertStringValue(result, "NaN");

        // Special case: Infinity
        result = NumberPrototype.toFixed(context, new JSNumber(Double.POSITIVE_INFINITY), JSValue.NO_ARGS);
        assertStringValue(result, "Infinity");

        // Special case: large number
        result = NumberPrototype.toFixed(context, new JSNumber(1e22), JSValue.NO_ARGS);
        assertThat(result.asString().map(JSString::value).orElseThrow())
                .satisfies(s -> assertThat(s.contains("e")).isTrue());

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertPendingRangeError(context, NumberPrototype.toFixed(context, num, numberArgs(-1)));

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertPendingRangeError(context, NumberPrototype.toFixed(context, num, numberArgs(101)));
    }

    @Test
    public void testToFixedExactnessWithJavet() {
        // Test that toFixed uses exact binary value, not shortest representation
        assertStringWithJavet("(1000000000000000128).toString()", "(1000000000000000128).toFixed(0)",
                "(0.1).toFixed(20)", "(0.2).toFixed(20)", "(0.1 + 0.2).toFixed(20)", "(1e20).toFixed(0)",
                "(-1e20).toFixed(0)", "(1.005).toFixed(2)", "(0.5).toFixed(0)", "(1.5).toFixed(0)", "(2.5).toFixed(0)");
    }

    @Test
    public void testToFixedWithJavet() {
        assertNumberFormat("toFixed", NumberPrototype::toFixed, 0);
    }

    @Test
    public void testToLocaleString() {
        JSNumber num = new JSNumber(1234.56);

        // Normal case: basic functionality
        JSValue result = NumberPrototype.toLocaleString(context, num, JSValue.NO_ARGS);
        // Without arguments the default Intl.NumberFormat applies, grouping included, as in V8
        assertStringValue(result, "1,234.56");

        // Special case: NaN
        result = NumberPrototype.toLocaleString(context, new JSNumber(Double.NaN), JSValue.NO_ARGS);
        assertStringValue(result, "NaN");

        // Special case: Infinity
        result = NumberPrototype.toLocaleString(context, new JSNumber(Double.POSITIVE_INFINITY), JSValue.NO_ARGS);
        assertStringValue(result, "∞");
    }

    @Test
    public void testToPrecision() {
        JSNumber num = new JSNumber(123.456);

        // Normal case: default precision
        JSValue result = NumberPrototype.toPrecision(context, num, JSValue.NO_ARGS);
        assertStringValue(result, "123.456");

        // Normal case: specific precision
        result = NumberPrototype.toPrecision(context, num, numberArgs(4));
        assertStringValue(result, "123.5");

        // Normal case: exponential notation for small numbers
        result = NumberPrototype.toPrecision(context, new JSNumber(0.000123), numberArgs(3));
        assertStringValue(result, "0.000123");

        // Normal case: exponential notation for large numbers
        result = NumberPrototype.toPrecision(context, new JSNumber(123456789), numberArgs(4));
        assertStringValue(result, "1.235e+8");

        // Normal case: zero
        result = NumberPrototype.toPrecision(context, new JSNumber(0), numberArgs(3));
        assertStringValue(result, "0.00");

        // Special case: NaN
        result = NumberPrototype.toPrecision(context, new JSNumber(Double.NaN), JSValue.NO_ARGS);
        assertStringValue(result, "NaN");

        // Special case: Infinity
        result = NumberPrototype.toPrecision(context, new JSNumber(Double.POSITIVE_INFINITY), JSValue.NO_ARGS);
        assertStringValue(result, "Infinity");

        // Edge case: precision too low - skip error check due to JSObject implementation
        assertPendingRangeError(context, NumberPrototype.toPrecision(context, num, numberArgs(0)));

        // Edge case: precision too high - skip error check due to JSObject implementation
        assertPendingRangeError(context, NumberPrototype.toPrecision(context, num, numberArgs(101)));
    }

    @Test
    public void testToPrecisionWithJavet() {
        assertNumberFormat("toPrecision", NumberPrototype::toPrecision, 1);
    }

    @Test
    public void testToString() {
        JSNumber num = new JSNumber(42);

        // Normal case: default radix (10)
        BuiltinAssertions toString = builtin(NumberPrototype::toString, num);
        toString.expectString("42");

        // Normal case: decimal number
        JSValue result = NumberPrototype.toString(context, new JSNumber(123.456), JSValue.NO_ARGS);
        assertStringValue(result, "123.456");

        // Normal case: different radix
        toString.expectString("2a", numberArgs(16));

        // Normal case: binary
        toString.expectString("101010", numberArgs(2));

        // Normal case: negative number
        result = NumberPrototype.toString(context, new JSNumber(-42), numberArgs(16));
        assertStringValue(result, "-2a");

        // Special case: NaN
        result = NumberPrototype.toString(context, new JSNumber(Double.NaN), JSValue.NO_ARGS);
        assertStringValue(result, "NaN");

        // Special case: Infinity
        result = NumberPrototype.toString(context, new JSNumber(Double.POSITIVE_INFINITY), JSValue.NO_ARGS);
        assertStringValue(result, "Infinity");

        // Special case: zero
        result = NumberPrototype.toString(context, new JSNumber(0), JSValue.NO_ARGS);
        assertStringValue(result, "0");

        // Edge case: radix too low - skip error check due to JSObject implementation
        assertPendingRangeError(context, NumberPrototype.toString(context, num, numberArgs(1)));

        // Edge case: radix too high - skip error check due to JSObject implementation
        assertPendingRangeError(context, NumberPrototype.toString(context, num, numberArgs(37)));

        // Edge case: non-integer with non-10 radix (now supported for floating-point conversion)
        result = NumberPrototype.toString(context, new JSNumber(42.5), numberArgs(16));
        assertStringValue(result, "2a.8");
    }

    @Test
    public void testToStringShortestRepresentationWithJavet() {
        // Test that Number.prototype.toString() produces the shortest decimal representation
        // matching V8/ES spec across all JDK versions (JDK 17+).
        // Covers: large integers near precision boundary, small decimals,
        // exponential notation boundaries, and special IEEE 754 values.
        assertStringWithJavet(
                // Large integers where exact binary value differs from shortest representation
                "(1000000000000000128).toString()", "(-1000000000000000128).toString()",
                "(9007199254740992).toString()", "(9007199254740994).toString()",
                // Integers at various magnitudes
                "(1e15).toString()", "(1e16).toString()", "(1e17).toString()", "(1e18).toString()", "(1e19).toString()",
                "(1e20).toString()", "(-1e20).toString()",
                // Boundary: decimal vs exponential notation (exponent 20 vs 21)
                "(1e21).toString()", "(1e22).toString()", "(-1e21).toString()", "(9.999999999999998e20).toString()",
                "(1.5e20).toString()",
                // Small numbers: boundary at 1e-7 (decimal) vs 1e-7 (exponential)
                "(0.000001).toString()", "(0.0000001).toString()", "(0.00000015).toString()", "(-0.000001).toString()",
                "(-0.0000001).toString()", "(1e-6).toString()", "(1e-7).toString()", "(1e-8).toString()",
                "(5e-324).toString()",
                // Regular decimals
                "(0.1).toString()", "(0.2).toString()", "(0.3).toString()", "(1.5).toString()", "(-1.5).toString()",
                "(3.141592653589793).toString()", "(0.1 + 0.2).toString()",
                // Integers that are exactly representable
                "(42).toString()", "(-42).toString()", "(100).toString()", "(999999999999999).toString()",
                "(9999999999999998).toString()",
                // Large values in exponential notation
                "(1.7976931348623157e+308).toString()", "(1.5e+308).toString()", "(1e+100).toString()",
                "(-1.7976931348623157e+308).toString()",
                // Small subnormals
                "(5e-324).toString()", "(2.2250738585072014e-308).toString()", "(2.2250738585072e-308).toString()",
                // Zero
                "(0).toString()", "(-0).toString()",
                // NaN and Infinity
                "NaN.toString()", "Infinity.toString()", "(-Infinity).toString()");
    }

    @Test
    public void testToStringWithJavet() {
        List<Double> testNumbers = List.of(0D, 1D, -1D, 123.456D, -123.456D, 123456789.123456789D,
                -123456789.123456789D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        testNumbers.forEach(number -> IntStream.range(2, 37)
                .forEach(radix -> assertWithJavet(
                        () -> v8Runtime.getExecutor("Number(" + number + ").toString(" + radix + ")").executeString(),
                        () -> NumberPrototype.toString(context, new JSNumber(number), numberArgs(radix)).asString()
                                .map(JSString::value).orElseThrow())));
    }

    @Test
    public void testValueOf() {
        JSNumber num = new JSNumber(42.5);

        // Normal case: number object
        JSValue result = NumberPrototype.valueOf(context, num, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(num);
        assertNumberValue(result, 42.5);

        // Edge case: called on non-number
        assertPendingTypeError(context, NumberPrototype.valueOf(context, new JSString("42"), JSValue.NO_ARGS));
    }
}
