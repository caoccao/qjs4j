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
import com.caoccao.qjs4j.core.JSNumberObject;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Unit tests for Number constructor.
 */
public class NumberConstructorTest extends BaseTest {

    @Test
    public void testNewNumberCreatesJSNumberObject() {
        // Test new Number(42) creates JSNumberObject
        JSValue result1 = context.eval("new Number(42);");
        assertThat(result1).isInstanceOf(JSNumberObject.class);
        assertThat(result1.isNumberObject()).isTrue();

        assertThat(result1).isInstanceOfSatisfying(JSNumberObject.class,
                numObj1 -> assertThat(numObj1.getValue().value()).isEqualTo(42.0));

        // Test new Number(3.14) creates JSNumberObject
        JSValue result2 = context.eval("new Number(3.14);");
        assertThat(result2).isInstanceOf(JSNumberObject.class);
        assertThat(result2.isNumberObject()).isTrue();

        assertThat(result2).isInstanceOfSatisfying(JSNumberObject.class,
                numObj2 -> assertThat(numObj2.getValue().value()).isCloseTo(3.14, offset(0.001)));
    }

    @Test
    public void testNumberConstructorWithDifferentValues() {
        // Test with integer
        JSValue result1 = context.eval("new Number(100);");
        assertThat(result1).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(100.0));

        // Test with negative number
        JSValue result2 = context.eval("new Number(-42);");
        assertThat(result2).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(-42.0));

        // Test with zero
        JSValue result3 = context.eval("new Number(0);");
        assertThat(result3).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(0.0));

        // Test with string to number conversion
        JSValue result4 = context.eval("new Number('123');");
        assertThat(result4).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(123.0));

        // Test with boolean to number conversion
        JSValue result5 = context.eval("new Number(true);");
        assertThat(result5).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(1.0));

        JSValue result6 = context.eval("new Number(false);");
        assertThat(result6).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(0.0));

        // Test with undefined (should be NaN)
        JSValue result7 = context.eval("new Number(undefined);");
        assertThat(result7).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(Double.isNaN(numObj.getValue().value())).isTrue());

        // Test with null (should be 0)
        JSValue result8 = context.eval("new Number(null);");
        assertThat(result8).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(0.0));
    }

    @Test
    public void testNumberConstructorWithNoArguments() {
        // Test new Number() without arguments (should be 0)
        JSValue result = context.eval("new Number();");
        assertThat(result).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(0.0));
    }

    @Test
    public void testNumberObjectSpecialValues() {
        // Test NaN
        JSValue resultNaN = context.eval("new Number(NaN);");
        assertThat(resultNaN).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(Double.isNaN(numObj.getValue().value())).isTrue());

        // Test Infinity
        JSValue resultInf = context.eval("new Number(Infinity);");
        assertThat(resultInf).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(Double.POSITIVE_INFINITY));

        // Test -Infinity
        JSValue resultNegInf = context.eval("new Number(-Infinity);");
        assertThat(resultNegInf).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void testNumberObjectToExponential() {
        JSValue result = context.eval("(new Number(12345)).toExponential(2);");
        assertThat(result.asString().map(JSString::value).orElseThrow()).satisfies(
                resultStr -> assertThat(resultStr.startsWith("1.23e+4") || resultStr.startsWith("1.23E+4")).isTrue());
    }

    @Test
    public void testNumberObjectToFixed() {
        JSValue result = context.eval("(new Number(3.14159)).toFixed(2);");
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("3.14");
    }

    @Test
    public void testNumberObjectToPrecision() {
        JSValue result = context.eval("(new Number(123.456)).toPrecision(4);");
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("123.5");
    }

    @Test
    public void testNumberObjectToString() {
        JSValue result1 = context.eval("(new Number(42)).toString();");
        assertThat(result1.asString().map(JSString::value).orElseThrow()).isEqualTo("42");

        JSValue result2 = context.eval("(new Number(3.14)).toString();");
        assertThat(result2.asString().map(JSString::value).orElseThrow()).isEqualTo("3.14");

        JSValue result3 = context.eval("(new Number(-5)).toString();");
        assertThat(result3.asString().map(JSString::value).orElseThrow()).isEqualTo("-5");
    }

    @Test
    public void testNumberObjectTypeof() {
        JSValue result = context.eval("typeof new Number(42);");
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("object");
    }

    @Test
    public void testNumberObjectValueOf() {
        JSValue result = context.eval("(new Number(42)).valueOf();");
        assertThat(result).isInstanceOf(JSNumber.class);
        assertThat(result).isNotInstanceOf(JSNumberObject.class);
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(42.0));
    }

    @Test
    public void testNumberWithoutNewReturnsPrimitive() {
        // Test Number(42) without new returns primitive
        JSValue result1 = context.eval("Number(42);");
        assertThat(result1).isInstanceOf(JSNumber.class);
        assertThat(result1).isNotInstanceOf(JSNumberObject.class);
        assertThat(result1).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(42.0));

        // Test Number(3.14) without new returns primitive
        JSValue result2 = context.eval("Number(3.14);");
        assertThat(result2).isInstanceOf(JSNumber.class);
        assertThat(result2).isNotInstanceOf(JSNumberObject.class);
        assertThat(result2).isInstanceOfSatisfying(JSNumber.class,
                num -> assertThat(num.value()).isCloseTo(3.14, offset(0.001)));
    }
}
