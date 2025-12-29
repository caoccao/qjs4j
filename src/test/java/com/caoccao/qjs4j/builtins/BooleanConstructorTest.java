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
import com.caoccao.qjs4j.core.JSBoolean;
import com.caoccao.qjs4j.core.JSBooleanObject;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Boolean constructor.
 */
public class BooleanConstructorTest extends BaseTest {

    @Test
    public void testBooleanConstructorWithDifferentValues() {
        // Test with truthy values
        JSValue result1 = context.eval("new Boolean(1);");
        assertTrue(result1.isBooleanObject());
        assertTrue(result1.asBooleanObject().map(JSBooleanObject::getValue).map(JSBoolean::value).orElseThrow());

        JSValue result2 = context.eval("new Boolean('hello');");
        assertTrue(result2.isBooleanObject());
        assertTrue(result2.asBooleanObject().map(JSBooleanObject::getValue).map(JSBoolean::value).orElseThrow());

        // Test with falsy values
        JSValue result3 = context.eval("new Boolean(0);");
        assertTrue(result3.isBooleanObject());
        assertFalse(result3.asBooleanObject().map(JSBooleanObject::getValue).map(JSBoolean::value).orElseThrow());

        JSValue result4 = context.eval("new Boolean('');");
        assertTrue(result4.isBooleanObject());
        assertFalse(result4.asBooleanObject().map(JSBooleanObject::getValue).map(JSBoolean::value).orElseThrow());

        JSValue result5 = context.eval("new Boolean(null);");
        assertTrue(result5.isBooleanObject());
        assertFalse(result5.asBooleanObject().map(JSBooleanObject::getValue).map(JSBoolean::value).orElseThrow());

        JSValue result6 = context.eval("new Boolean(undefined);");
        assertTrue(result6.isBooleanObject());
        assertFalse(result6.asBooleanObject().map(JSBooleanObject::getValue).map(JSBoolean::value).orElseThrow());
    }

    @Test
    public void testBooleanObjectToString() {
        JSValue result1 = context.eval("(new Boolean(true)).toString();");
        assertEquals("true", result1.asString().map(JSString::value).orElseThrow());

        JSValue result2 = context.eval("(new Boolean(false)).toString();");
        assertEquals("false", result2.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testBooleanObjectTypeof() {
        JSValue result = context.eval("typeof new Boolean(true);");
        assertEquals("object", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testBooleanObjectValueOf() {
        JSValue result = context.eval("(new Boolean(true)).valueOf();");
        assertTrue(result.isBooleanTrue());
        assertFalse(result.isBooleanObject(), "valueOf should return primitive");
    }

    @Test
    public void testBooleanWithoutNewReturnsPrimitive() {
        // Test Boolean(true) without new returns primitive
        JSValue result1 = context.eval("Boolean(true);");
        assertTrue(result1.isBoolean(), "Boolean(true) should return JSBoolean primitive");
        assertFalse(result1.isBooleanObject(), "Boolean(true) should NOT be JSBooleanObject");
        assertTrue(result1.isBooleanTrue());

        // Test Boolean(false) without new returns primitive
        JSValue result2 = context.eval("Boolean(false);");
        assertTrue(result2.isBoolean(), "Boolean(false) should return JSBoolean primitive");
        assertFalse(result2.isBooleanObject(), "Boolean(false) should NOT be JSBooleanObject");
        assertTrue(result2.isBooleanFalse());
    }

    @Test
    public void testNewBooleanCreatesJSBooleanObject() {
        // Test new Boolean(true) creates JSBooleanObject
        JSValue result1 = context.eval("new Boolean(true);");
        assertTrue(result1.isBooleanObject(), "new Boolean(true) should return JSBooleanObject");
        assertTrue(result1.isBooleanObject(), "new Boolean(true) should be a boolean object");

        JSBooleanObject boolObj1 = (JSBooleanObject) result1;
        assertTrue(boolObj1.getValue().isBooleanTrue());

        // Test new Boolean(false) creates JSBooleanObject
        JSValue result2 = context.eval("new Boolean(false);");
        assertTrue(result2.isBooleanObject(), "new Boolean(false) should return JSBooleanObject");
        assertTrue(result2.isBooleanObject(), "new Boolean(false) should be a boolean object");

        JSBooleanObject boolObj2 = (JSBooleanObject) result2;
        assertTrue(boolObj2.getValue().isBooleanFalse());
    }
}
