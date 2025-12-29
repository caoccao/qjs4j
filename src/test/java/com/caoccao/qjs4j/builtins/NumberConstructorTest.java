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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Number constructor.
 */
public class NumberConstructorTest extends BaseTest {

    @Test
    public void testNewNumberCreatesJSNumberObject() {
        // Test new Number(42) creates JSNumberObject
        JSValue result1 = ctx.eval("new Number(42);");
        assertInstanceOf(JSNumberObject.class, result1, "new Number(42) should return JSNumberObject");
        assertTrue(result1.isNumberObject(), "new Number(42) should be a number object");

        JSNumberObject numObj1 = (JSNumberObject) result1;
        assertEquals(42.0, numObj1.getValue().value());

        // Test new Number(3.14) creates JSNumberObject
        JSValue result2 = ctx.eval("new Number(3.14);");
        assertInstanceOf(JSNumberObject.class, result2, "new Number(3.14) should return JSNumberObject");
        assertTrue(result2.isNumberObject(), "new Number(3.14) should be a number object");

        JSNumberObject numObj2 = (JSNumberObject) result2;
        assertEquals(3.14, numObj2.getValue().value(), 0.001);
    }

    @Test
    public void testNumberConstructorWithDifferentValues() {
        // Test with integer
        JSValue result1 = ctx.eval("new Number(100);");
        assertInstanceOf(JSNumberObject.class, result1);
        assertEquals(100.0, ((JSNumberObject) result1).getValue().value());

        // Test with negative number
        JSValue result2 = ctx.eval("new Number(-42);");
        assertInstanceOf(JSNumberObject.class, result2);
        assertEquals(-42.0, ((JSNumberObject) result2).getValue().value());

        // Test with zero
        JSValue result3 = ctx.eval("new Number(0);");
        assertInstanceOf(JSNumberObject.class, result3);
        assertEquals(0.0, ((JSNumberObject) result3).getValue().value());

        // Test with string to number conversion
        JSValue result4 = ctx.eval("new Number('123');");
        assertInstanceOf(JSNumberObject.class, result4);
        assertEquals(123.0, ((JSNumberObject) result4).getValue().value());

        // Test with boolean to number conversion
        JSValue result5 = ctx.eval("new Number(true);");
        assertInstanceOf(JSNumberObject.class, result5);
        assertEquals(1.0, ((JSNumberObject) result5).getValue().value());

        JSValue result6 = ctx.eval("new Number(false);");
        assertInstanceOf(JSNumberObject.class, result6);
        assertEquals(0.0, ((JSNumberObject) result6).getValue().value());

        // Test with undefined (should be NaN)
        JSValue result7 = ctx.eval("new Number(undefined);");
        assertInstanceOf(JSNumberObject.class, result7);
        assertTrue(Double.isNaN(((JSNumberObject) result7).getValue().value()));

        // Test with null (should be 0)
        JSValue result8 = ctx.eval("new Number(null);");
        assertInstanceOf(JSNumberObject.class, result8);
        assertEquals(0.0, ((JSNumberObject) result8).getValue().value());
    }

    @Test
    public void testNumberConstructorWithNoArguments() {
        // Test new Number() without arguments (should be 0)
        JSValue result = ctx.eval("new Number();");
        assertInstanceOf(JSNumberObject.class, result);
        assertEquals(0.0, ((JSNumberObject) result).getValue().value());
    }

    @Test
    public void testNumberObjectSpecialValues() {
        // Test NaN
        JSValue resultNaN = ctx.eval("new Number(NaN);");
        assertInstanceOf(JSNumberObject.class, resultNaN);
        assertTrue(Double.isNaN(((JSNumberObject) resultNaN).getValue().value()));

        // Test Infinity
        JSValue resultInf = ctx.eval("new Number(Infinity);");
        assertInstanceOf(JSNumberObject.class, resultInf);
        assertEquals(Double.POSITIVE_INFINITY, ((JSNumberObject) resultInf).getValue().value());

        // Test -Infinity
        JSValue resultNegInf = ctx.eval("new Number(-Infinity);");
        assertInstanceOf(JSNumberObject.class, resultNegInf);
        assertEquals(Double.NEGATIVE_INFINITY, ((JSNumberObject) resultNegInf).getValue().value());
    }

    @Test
    public void testNumberObjectToExponential() {
        JSValue result = ctx.eval("(new Number(12345)).toExponential(2);");
        String resultStr = result.asString().map(JSString::value).orElseThrow();
        assertTrue(resultStr.startsWith("1.23e+4") || resultStr.startsWith("1.23E+4"));
    }

    @Test
    public void testNumberObjectToFixed() {
        JSValue result = ctx.eval("(new Number(3.14159)).toFixed(2);");
        assertEquals("3.14", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testNumberObjectToPrecision() {
        JSValue result = ctx.eval("(new Number(123.456)).toPrecision(4);");
        assertEquals("123.5", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testNumberObjectToString() {
        JSValue result1 = ctx.eval("(new Number(42)).toString();");
        assertEquals("42", result1.asString().map(JSString::value).orElseThrow());

        JSValue result2 = ctx.eval("(new Number(3.14)).toString();");
        assertEquals("3.14", result2.asString().map(JSString::value).orElseThrow());

        JSValue result3 = ctx.eval("(new Number(-5)).toString();");
        assertEquals("-5", result3.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testNumberObjectTypeof() {
        JSValue result = ctx.eval("typeof new Number(42);");
        assertEquals("object", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testNumberObjectValueOf() {
        JSValue result = ctx.eval("(new Number(42)).valueOf();");
        assertInstanceOf(JSNumber.class, result, "valueOf should return primitive");
        assertFalse(result instanceof JSNumberObject, "valueOf should NOT return JSNumberObject");
        assertEquals(42.0, ((JSNumber) result).value());
    }

    @Test
    public void testNumberWithoutNewReturnsPrimitive() {
        // Test Number(42) without new returns primitive
        JSValue result1 = ctx.eval("Number(42);");
        assertInstanceOf(JSNumber.class, result1, "Number(42) should return JSNumber primitive");
        assertFalse(result1 instanceof JSNumberObject, "Number(42) should NOT be JSNumberObject");
        assertEquals(42.0, ((JSNumber) result1).value());

        // Test Number(3.14) without new returns primitive
        JSValue result2 = ctx.eval("Number(3.14);");
        assertInstanceOf(JSNumber.class, result2, "Number(3.14) should return JSNumber primitive");
        assertFalse(result2 instanceof JSNumberObject, "Number(3.14) should NOT be JSNumberObject");
        assertEquals(3.14, ((JSNumber) result2).value(), 0.001);
    }
}
