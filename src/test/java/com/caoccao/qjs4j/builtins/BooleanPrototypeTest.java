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
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Boolean.prototype methods.
 */
public class BooleanPrototypeTest extends BaseJavetTest {

    @Test
    public void testBooleanInArithmetic() {
        assertBooleanWithJavet(
                "true + true === 2",
                "true + false === 1",
                "false + false === 0",
                "true - false === 1",
                "true * 5 === 5");
    }

    @Test
    public void testBooleanPrimitiveAutoboxing() {
        assertStringWithJavet("true.toString()");
        assertBooleanWithJavet("true.valueOf()");
    }

    @Test
    public void testBooleanPrototypeChain() {
        assertBooleanWithJavet(
                "Object.getPrototypeOf(Boolean.prototype) === Object.prototype");
    }

    @Test
    public void testBooleanPrototypeConstructor() {
        assertBooleanWithJavet(
                "Boolean.prototype.constructor === Boolean");
    }

    @Test
    public void testBooleanPrototypeIsABooleanObject() {
        // Boolean.prototype has [[BooleanData]] = false per QuickJS
        assertStringWithJavet(
                "Boolean.prototype.valueOf.call(Boolean.prototype).toString()",
                "Boolean.prototype.toString.call(Boolean.prototype)");
    }

    @Test
    public void testBooleanPrototypeMethodsNotEnumerable() {
        // for...in should not enumerate prototype methods
        assertStringWithJavet(
                "var keys = []; for (var k in Boolean.prototype) keys.push(k); keys.join(',')");
    }

    @Test
    public void testBooleanThisBooleanValueSemantics() {
        assertErrorWithJavet(
                "Boolean.prototype.toString.call(Object.create(Boolean.prototype))",
                "Boolean.prototype.valueOf.call(Object.create(Boolean.prototype))");
    }

    @Test
    public void testBooleanToNumber() {
        assertBooleanWithJavet(
                "+true === 1",
                "+false === 0");
    }

    @Test
    public void testBooleanToString() {
        assertStringWithJavet(
                "'' + true",
                "'' + false",
                "String(true)",
                "String(false)");
    }

    @Test
    public void testConstructorDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'constructor').writable === true",
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'constructor').enumerable === false",
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'constructor').configurable === true");
    }

    @Test
    public void testLooseEquality() {
        assertBooleanWithJavet(
                // Primitive vs primitive
                "true == true",
                "true == false",
                "false == false",
                "true == Boolean(true)",
                "true == Boolean(false)",
                "Boolean(true) == Boolean(true)",
                "Boolean(true) == Boolean(false)",
                // Primitive vs object
                "true == new Boolean(true)",
                "true == new Boolean(false)",
                "false == new Boolean(false)",
                "Boolean(true) == new Boolean(true)",
                "Boolean(true) == new Boolean(false)",
                // Object vs object
                "new Boolean(true) == new Boolean(true)");
    }

    @Test
    public void testReferenceEquality() {
        assertBooleanWithJavet(
                "var b = new Boolean(true); b == b",
                "var b = new Boolean(true); b === b");
    }

    @Test
    public void testStrictEquality() {
        assertBooleanWithJavet(
                // Primitive vs primitive
                "true === true",
                "true === false",
                "false === false",
                "true === Boolean(true)",
                "true === Boolean(false)",
                // Primitive vs object
                "true === new Boolean(true)",
                // Object vs object
                "new Boolean(true) === new Boolean(true)");
    }

    @Test
    public void testToStringDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'toString').writable === true",
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'toString').enumerable === false",
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'toString').configurable === true");
    }

    @Test
    public void testToStringLength() {
        assertBooleanWithJavet("Boolean.prototype.toString.length === 0");
    }

    @Test
    public void testToStringName() {
        assertStringWithJavet("Boolean.prototype.toString.name");
    }

    @Test
    public void testToStringOnNonBoolean() {
        assertErrorWithJavet(
                "Boolean.prototype.toString.call(42)",
                "Boolean.prototype.toString.call('string')",
                "Boolean.prototype.toString.call({})");
    }

    @Test
    public void testToStringOnObject() {
        assertStringWithJavet(
                "(new Boolean(true)).toString()",
                "(new Boolean(false)).toString()");
    }

    @Test
    public void testToStringOnPrimitive() {
        assertStringWithJavet(
                "true.toString()",
                "false.toString()");
    }

    @Test
    public void testToStringTagForBooleanObjects() {
        assertStringWithJavet(
                "Object.prototype.toString.call(Boolean.prototype)",
                "Object.prototype.toString.call(new Boolean(true))",
                "Object.prototype.toString.call(new Boolean(false))");
    }

    @Test
    public void testToStringViaCall() {
        assertStringWithJavet(
                "Boolean.prototype.toString.call(true)",
                "Boolean.prototype.toString.call(false)",
                "Boolean.prototype.toString.call(new Boolean(true))",
                "Boolean.prototype.toString.call(new Boolean(false))");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet(
                "typeof true",
                "typeof false",
                "typeof new Boolean(true)");
    }

    @Test
    public void testValueOfDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'valueOf').writable === true",
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'valueOf').enumerable === false",
                "Object.getOwnPropertyDescriptor(Boolean.prototype, 'valueOf').configurable === true");
    }

    @Test
    public void testValueOfLength() {
        assertBooleanWithJavet("Boolean.prototype.valueOf.length === 0");
    }

    @Test
    public void testValueOfName() {
        assertStringWithJavet("Boolean.prototype.valueOf.name");
    }

    @Test
    public void testValueOfOnNonBoolean() {
        assertErrorWithJavet(
                "Boolean.prototype.valueOf.call(42)",
                "Boolean.prototype.valueOf.call('string')",
                "Boolean.prototype.valueOf.call({})");
    }

    @Test
    public void testValueOfOnObject() {
        assertBooleanWithJavet(
                "(new Boolean(true)).valueOf()",
                "(new Boolean(false)).valueOf()");
    }

    @Test
    public void testValueOfOnPrimitive() {
        assertBooleanWithJavet(
                "true.valueOf()",
                "false.valueOf()");
    }

    @Test
    public void testValueOfViaCall() {
        assertBooleanWithJavet(
                "Boolean.prototype.valueOf.call(true)",
                "Boolean.prototype.valueOf.call(false)",
                "Boolean.prototype.valueOf.call(new Boolean(true))",
                "Boolean.prototype.valueOf.call(new Boolean(false))");
    }
}
