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
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Unit tests for Boolean.prototype methods.
 */
public class BooleanPrototypeTest extends BaseTest {
    @Test
    public void testEquals() {
        // Verify that loose equality passes between primitive and primitive
        assertTrue(context.eval("true == true").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("true == false").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(context.eval("true == Boolean(true)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("true == Boolean(false)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality passes between primitive and primitive
        assertTrue(context.eval("true === true").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("true === false").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(context.eval("true === Boolean(true)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("true === Boolean(false)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality passes between primitive and primitive
        assertTrue(context.eval("Boolean(true) == Boolean(true)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("Boolean(true) == Boolean(false)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality passes between primitive and object
        assertTrue(context.eval("true == new Boolean(true)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("true == new Boolean(false)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(context.eval("Boolean(true) == new Boolean(true)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("Boolean(true) == new Boolean(false)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality fails between object and object
        assertFalse(context.eval("new Boolean(true) == new Boolean(true)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality fails between primitive and object
        assertFalse(context.eval("true === new Boolean(true)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality fails between object and object
        assertFalse(context.eval("new Boolean(true) === new Boolean(true)").asBoolean().map(JSBoolean::value).orElseThrow());
    }

    @Test
    public void testToString() {
        // Normal case: true
        JSValue result = BooleanPrototype.toString(context, JSBoolean.TRUE, new JSValue[]{});
        assertEquals("true", result.asString().map(JSString::value).orElseThrow());

        // Normal case: false
        result = BooleanPrototype.toString(context, JSBoolean.FALSE, new JSValue[]{});
        assertEquals("false", result.asString().map(JSString::value).orElseThrow());

        // Normal case: Boolean object wrapper
        JSObject boolObj = new JSObject();
        boolObj.set("[[PrimitiveValue]]", JSBoolean.TRUE);
        result = BooleanPrototype.toString(context, boolObj, new JSValue[]{});
        assertEquals("true", result.asString().map(JSString::value).orElseThrow());

        JSObject boolObj2 = new JSObject();
        boolObj2.set("[[PrimitiveValue]]", JSBoolean.FALSE);
        result = BooleanPrototype.toString(context, boolObj2, new JSValue[]{});
        assertEquals("false", result.asString().map(JSString::value).orElseThrow());

        // Edge case: called on non-boolean
        assertTypeError(BooleanPrototype.toString(context, new JSString("not boolean"), new JSValue[]{}));
        assertPendingException(context);

        // Edge case: Boolean object without PrimitiveValue
        JSObject invalidObj = new JSObject();
        assertTypeError(BooleanPrototype.toString(context, invalidObj, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testTypeof() {
        JSValue trueType = context.eval("typeof true;");
        assertEquals("boolean", trueType.asString().map(JSString::value).orElseThrow());

        JSValue falseType = context.eval("typeof false;");
        assertEquals("boolean", falseType.asString().map(JSString::value).orElseThrow());

        JSValue boolObjType = context.eval("typeof new Boolean(true);");
        assertEquals("object", boolObjType.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testValueOf() {
        // Normal case: true
        JSValue result = BooleanPrototype.valueOf(context, JSBoolean.TRUE, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        // Normal case: false
        result = BooleanPrototype.valueOf(context, JSBoolean.FALSE, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Normal case: Boolean object wrapper
        JSObject boolObj = new JSObject();
        boolObj.set("[[PrimitiveValue]]", JSBoolean.TRUE);
        result = BooleanPrototype.valueOf(context, boolObj, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        JSObject boolObj2 = new JSObject();
        boolObj2.set("[[PrimitiveValue]]", JSBoolean.FALSE);
        result = BooleanPrototype.valueOf(context, boolObj2, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-boolean
        assertTypeError(BooleanPrototype.valueOf(context, new JSNumber(123), new JSValue[]{}));
        assertPendingException(context);

        // Edge case: Boolean object without PrimitiveValue
        JSObject invalidObj = new JSObject();
        assertTypeError(BooleanPrototype.valueOf(context, invalidObj, new JSValue[]{}));
        assertPendingException(context);
    }
}
