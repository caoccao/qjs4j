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
 * Unit tests for Boolean constructor.
 */
public class BooleanConstructorTest extends BaseJavetTest {

    @Test
    public void testBooleanAsFunctionFalsyValues() {
        assertBooleanWithJavet(
                "Boolean(false)",
                "Boolean(0)",
                "Boolean(-0)",
                "Boolean('')",
                "Boolean(null)",
                "Boolean(undefined)",
                "Boolean(NaN)");
    }

    @Test
    public void testBooleanAsFunctionNoArgs() {
        assertBooleanWithJavet("Boolean()");
    }

    @Test
    public void testBooleanAsFunctionReturnsPrimitive() {
        assertStringWithJavet(
                "typeof Boolean(true)",
                "typeof Boolean(false)");
    }

    @Test
    public void testBooleanAsFunctionTruthyValues() {
        assertBooleanWithJavet(
                "Boolean(true)",
                "Boolean(1)",
                "Boolean(-1)",
                "Boolean('hello')",
                "Boolean(' ')",
                "Boolean([])",
                "Boolean({})",
                "Boolean(Infinity)",
                "Boolean(-Infinity)");
    }

    @Test
    public void testBooleanAsFunctionWithBooleanObject() {
        // Boolean(new Boolean(false)) should be true (object is truthy)
        assertBooleanWithJavet("Boolean(new Boolean(false))");
    }

    @Test
    public void testBooleanAsFunctionWithExtraArgs() {
        // Extra args are ignored
        assertBooleanWithJavet("Boolean(true, false)");
    }

    @Test
    public void testBooleanConstructorLength() {
        assertBooleanWithJavet("Boolean.length === 1");
    }

    @Test
    public void testBooleanConstructorName() {
        assertStringWithJavet("Boolean.name");
    }

    @Test
    public void testBooleanConstructorPrototypeDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(Boolean, 'prototype').writable === false",
                "Object.getOwnPropertyDescriptor(Boolean, 'prototype').enumerable === false",
                "Object.getOwnPropertyDescriptor(Boolean, 'prototype').configurable === false");
    }

    @Test
    public void testBooleanGlobalDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(globalThis, 'Boolean').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'Boolean').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'Boolean').configurable === true");
    }

    @Test
    public void testBooleanIsOnGlobal() {
        assertStringWithJavet("typeof Boolean");
    }

    @Test
    public void testInstanceofWithObject() {
        assertBooleanWithJavet(
                "new Boolean(true) instanceof Boolean",
                "new Boolean(false) instanceof Boolean");
    }

    @Test
    public void testInstanceofWithPrimitive() {
        assertBooleanWithJavet(
                "true instanceof Boolean",
                "false instanceof Boolean");
    }

    @Test
    public void testNewBooleanCreatesObject() {
        assertBooleanObjectWithJavet(
                "new Boolean(true)",
                "new Boolean(false)");
    }

    @Test
    public void testNewBooleanIsTruthy() {
        // Boolean objects are always truthy, even new Boolean(false)
        assertBooleanWithJavet(
                "!!(new Boolean(false))",
                "new Boolean(false) ? true : false");
    }

    @Test
    public void testNewBooleanNoArgs() {
        assertBooleanWithJavet("(new Boolean()).valueOf()");
    }

    @Test
    public void testNewBooleanObjectHasOwnProperty() {
        assertBooleanWithJavet(
                "var b = new Boolean(true); b.x = 1; b.hasOwnProperty('x')");
    }

    @Test
    public void testNewBooleanObjectInCondition() {
        assertBooleanWithJavet("new Boolean(false) ? true : false");
        assertStringWithJavet("new Boolean(false) ? 'truthy' : 'falsy'");
    }

    @Test
    public void testNewBooleanObjectTypeof() {
        assertStringWithJavet(
                "typeof new Boolean(true)",
                "typeof new Boolean(false)");
    }

    @Test
    public void testNewBooleanWithDifferentValues() {
        assertBooleanObjectWithJavet(
                // Truthy values
                "new Boolean(1)",
                "new Boolean('hello')",
                // Falsy values
                "new Boolean(0)",
                "new Boolean('')",
                "new Boolean(null)",
                "new Boolean(undefined)");
    }

    @Test
    public void testNewBooleanWithFalsyValues() {
        assertBooleanWithJavet(
                "(new Boolean(0)).valueOf()",
                "(new Boolean('')).valueOf()",
                "(new Boolean(null)).valueOf()",
                "(new Boolean(undefined)).valueOf()",
                "(new Boolean(NaN)).valueOf()");
    }

    @Test
    public void testNewBooleanWithTruthyValues() {
        assertBooleanWithJavet(
                "(new Boolean(1)).valueOf()",
                "(new Boolean('hello')).valueOf()",
                "(new Boolean([])).valueOf()",
                "(new Boolean({})).valueOf()");
    }
}
