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
 * Unit tests for ObjectPrototype methods.
 */
public class ObjectPrototypeTest extends BaseTest {

    @Test
    public void testAssign() {
        JSObject target = new JSObject();
        target.set("a", new JSNumber(1));

        JSObject source = new JSObject();
        source.set("b", new JSNumber(2));
        source.set("c", new JSString("hello"));

        // Normal case: assign sources to target
        JSValue result = ObjectPrototype.assign(context, JSUndefined.INSTANCE, new JSValue[]{target, source});
        assertEquals(target, result);
        assertEquals(1.0, target.get("a").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, target.get("b").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals("hello", target.get("c").asString().map(JSString::value).orElseThrow());

        // Normal case: multiple sources
        JSObject source2 = new JSObject();
        source2.set("d", JSBoolean.TRUE);
        result = ObjectPrototype.assign(context, JSUndefined.INSTANCE, new JSValue[]{target, source2});
        assertEquals(target, result);
        assertEquals(JSBoolean.TRUE, target.get("d"));

        // Normal case: skip null/undefined sources
        result = ObjectPrototype.assign(context, JSUndefined.INSTANCE, new JSValue[]{target, JSNull.INSTANCE, source});
        assertEquals(target, result);

        // Edge case: no arguments
        result = ObjectPrototype.assign(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: null target
        result = ObjectPrototype.assign(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testCreate() {
        // Normal case: create object with null prototype
        JSValue result = ObjectPrototype.create(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        JSObject obj = result.asObject().orElseThrow();
        assertNull(obj.getPrototype());

        // Normal case: create object with object prototype
        JSObject proto = new JSObject();
        proto.set("testProp", new JSString("testValue"));
        result = ObjectPrototype.create(context, JSUndefined.INSTANCE, new JSValue[]{proto});
        JSObject obj2 = result.asObject().orElseThrow();
        assertEquals(proto, obj2.getPrototype());
        // Should inherit property
        assertEquals("testValue", obj2.get("testProp").asString().map(JSString::value).orElseThrow());

        // Edge case: invalid prototype
        result = ObjectPrototype.create(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: no arguments
        result = ObjectPrototype.create(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testDefineProperty() {
        JSObject obj = new JSObject();

        // Normal case: define data property
        JSObject descriptor = new JSObject();
        descriptor.set("value", new JSString("test"));
        descriptor.set("writable", JSBoolean.TRUE);
        descriptor.set("enumerable", JSBoolean.TRUE);
        descriptor.set("configurable", JSBoolean.TRUE);

        JSValue result = ObjectPrototype.defineProperty(context, JSUndefined.INSTANCE, new JSValue[]{
                obj, new JSString("testProp"), descriptor
        });
        assertEquals(obj, result);
        assertEquals("test", obj.get("testProp").asString().map(JSString::value).orElseThrow());

        // Edge case: not enough arguments
        result = ObjectPrototype.defineProperty(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("prop")});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: first argument not object
        result = ObjectPrototype.defineProperty(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("not object"), new JSString("prop"), descriptor
        });
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testEntries() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSString("hello"));

        // Normal case: object with properties
        JSValue result = ObjectPrototype.entries(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray entries = result.asArray().orElseThrow();
        assertTrue(entries.getLength() >= 2);

        // Normal case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectPrototype.entries(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        JSArray emptyEntries = result.asArray().orElseThrow();
        assertEquals(0, emptyEntries.getLength());

        // Edge case: null
        result = ObjectPrototype.entries(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: primitive
        result = ObjectPrototype.entries(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.FALSE});
        JSArray primitiveEntries = result.asArray().orElseThrow();
        assertEquals(0, primitiveEntries.getLength());
    }

    @Test
    public void testFreeze() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: freeze object
        JSValue result = ObjectPrototype.freeze(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertEquals(obj, result);

        // Normal case: freeze primitive (returns primitive)
        result = ObjectPrototype.freeze(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("string")});
        assertEquals("string", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testHasOwnProperty() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case: has property
        JSValue result = ObjectPrototype.hasOwnProperty(context, obj, new JSValue[]{new JSString("a")});
        assertEquals(JSBoolean.TRUE, result);

        // Normal case: doesn't have property
        result = ObjectPrototype.hasOwnProperty(context, obj, new JSValue[]{new JSString("c")});
        assertEquals(JSBoolean.FALSE, result);

        // Normal case: numeric property
        result = ObjectPrototype.hasOwnProperty(context, obj, new JSValue[]{new JSNumber(1)});
        assertEquals(JSBoolean.FALSE, result); // "1" != "a"

        // Edge case: no arguments
        result = ObjectPrototype.hasOwnProperty(context, obj, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-object
        result = ObjectPrototype.hasOwnProperty(context, new JSString("string"), new JSValue[]{new JSString("length")});
        assertEquals(JSBoolean.FALSE, result);
    }

    @Test
    public void testKeys() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));
        obj.set("c", new JSNumber(3));

        // Normal case: object with properties
        JSValue result = ObjectPrototype.keys(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray keys = result.asArray().orElseThrow();
        assertTrue(keys.getLength() >= 3); // May include prototype properties

        // Normal case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectPrototype.keys(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        JSArray emptyKeys = result.asArray().orElseThrow();
        assertEquals(0, emptyKeys.getLength());

        // Edge case: null
        result = ObjectPrototype.keys(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: undefined
        result = ObjectPrototype.keys(context, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: primitive
        result = ObjectPrototype.keys(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("string")});
        JSArray primitiveKeys = result.asArray().orElseThrow();
        assertEquals(0, primitiveKeys.getLength());
    }

    @Test
    public void testSet() {
        JSValue result = context.eval("""
                const obj = {};
                obj.a = 1;
                JSON.stringify(obj);""");
        assertEquals("{\"a\":1}", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testToString() {
        // Normal case: undefined
        JSValue result = ObjectPrototype.toString(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals("[object Undefined]", result.asString().map(JSString::value).orElseThrow());

        // Normal case: null
        result = ObjectPrototype.toString(context, JSNull.INSTANCE, new JSValue[]{});
        assertEquals("[object Null]", result.asString().map(JSString::value).orElseThrow());

        // Normal case: object
        JSObject obj = new JSObject();
        result = ObjectPrototype.toString(context, obj, new JSValue[]{});
        assertEquals("[object Object]", result.asString().map(JSString::value).orElseThrow());

        // Normal case: array
        JSArray arr = new JSArray();
        result = ObjectPrototype.toString(context, arr, new JSValue[]{});
        assertEquals("[object Array]", result.asString().map(JSString::value).orElseThrow());

        // Normal case: function
        JSFunction func = new JSNativeFunction("test", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        result = ObjectPrototype.toString(context, func, new JSValue[]{});
        assertEquals("[object Function]", result.asString().map(JSString::value).orElseThrow());

        // Normal case: string
        result = ObjectPrototype.toString(context, new JSString("test"), new JSValue[]{});
        assertEquals("[object String]", result.asString().map(JSString::value).orElseThrow());

        // Normal case: number
        result = ObjectPrototype.toString(context, new JSNumber(42), new JSValue[]{});
        assertEquals("[object Number]", result.asString().map(JSString::value).orElseThrow());

        // Normal case: boolean
        result = ObjectPrototype.toString(context, JSBoolean.TRUE, new JSValue[]{});
        assertEquals("[object Boolean]", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testValueOf() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: object
        JSValue result = ObjectPrototype.valueOf(context, obj, new JSValue[]{});
        assertEquals(obj, result);

        // Normal case: primitive
        JSString str = new JSString("hello");
        result = ObjectPrototype.valueOf(context, str, new JSValue[]{});
        assertEquals(str, result);

        // Normal case: null
        result = ObjectPrototype.valueOf(context, JSNull.INSTANCE, new JSValue[]{});
        assertEquals(JSNull.INSTANCE, result);
    }

    @Test
    public void testValues() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSString("hello"));
        obj.set("c", JSBoolean.TRUE);

        // Normal case: object with properties
        JSValue result = ObjectPrototype.values(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray values = result.asArray().orElseThrow();
        assertTrue(values.getLength() >= 3);

        // Normal case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectPrototype.values(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        JSArray emptyValues = result.asArray().orElseThrow();
        assertEquals(0, emptyValues.getLength());

        // Edge case: null
        result = ObjectPrototype.values(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: primitive
        result = ObjectPrototype.values(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        JSArray primitiveValues = result.asArray().orElseThrow();
        assertEquals(0, primitiveValues.getLength());
    }
}
