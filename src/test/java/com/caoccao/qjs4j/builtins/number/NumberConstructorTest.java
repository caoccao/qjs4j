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
        assertThat(context.eval("new Number(100);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(100.0));

        // Test with negative number
        assertThat(context.eval("new Number(-42);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(-42.0));

        // Test with zero
        assertThat(context.eval("new Number(0);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(0.0));

        // Test with string to number conversion
        assertThat(context.eval("new Number('123');")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(123.0));

        // Test with boolean to number conversion
        assertThat(context.eval("new Number(true);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(1.0));

        assertThat(context.eval("new Number(false);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(0.0));

        // Test with undefined (should be NaN)
        assertThat(context.eval("new Number(undefined);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(Double.isNaN(numObj.getValue().value())).isTrue());

        // Test with null (should be 0)
        assertThat(context.eval("new Number(null);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(0.0));
    }

    @Test
    public void testNumberConstructorWithNoArguments() {
        // Test new Number() without arguments (should be 0)
        assertThat(context.eval("new Number();")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(0.0));
    }

    @Test
    public void testNumberObjectSpecialValues() {
        // Test NaN
        assertThat(context.eval("new Number(NaN);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(Double.isNaN(numObj.getValue().value())).isTrue());

        // Test Infinity
        assertThat(context.eval("new Number(Infinity);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(Double.POSITIVE_INFINITY));

        // Test -Infinity
        assertThat(context.eval("new Number(-Infinity);")).isInstanceOfSatisfying(JSNumberObject.class,
                numObj -> assertThat(numObj.getValue().value()).isEqualTo(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void testNumberObjectToExponential() {
        assertThat(context.eval("(new Number(12345)).toExponential(2);").asString().map(JSString::value).orElseThrow())
                .satisfies(resultStr -> assertThat(resultStr.startsWith("1.23e+4") || resultStr.startsWith("1.23E+4"))
                        .isTrue());
    }

    @Test
    public void testNumberObjectToFixed() {
        assertEvalString("(new Number(3.14159)).toFixed(2);", "3.14");
    }

    @Test
    public void testNumberObjectToPrecision() {
        assertEvalString("(new Number(123.456)).toPrecision(4);", "123.5");
    }

    @Test
    public void testNumberObjectToString() {
        assertEvalString("(new Number(42)).toString();", "42");

        assertEvalString("(new Number(3.14)).toString();", "3.14");

        assertEvalString("(new Number(-5)).toString();", "-5");
    }

    @Test
    public void testNumberObjectTypeof() {
        assertEvalString("typeof new Number(42);", "object");
    }

    @Test
    public void testNumberObjectValueOf() {
        JSValue result = context.eval("(new Number(42)).valueOf();");
        assertThat(result).isInstanceOf(JSNumber.class);
        assertThat(result).isNotInstanceOf(JSNumberObject.class);
        assertNumberValue(result, 42.0);
    }

    @Test
    public void testNumberWithoutNewReturnsPrimitive() {
        // Test Number(42) without new returns primitive
        JSValue result1 = context.eval("Number(42);");
        assertThat(result1).isInstanceOf(JSNumber.class);
        assertThat(result1).isNotInstanceOf(JSNumberObject.class);
        assertNumberValue(result1, 42.0);

        // Test Number(3.14) without new returns primitive
        JSValue result2 = context.eval("Number(3.14);");
        assertThat(result2).isInstanceOf(JSNumber.class);
        assertThat(result2).isNotInstanceOf(JSNumberObject.class);
        assertThat(result2).isInstanceOfSatisfying(JSNumber.class,
                num -> assertThat(num.value()).isCloseTo(3.14, offset(0.001)));
    }
}
