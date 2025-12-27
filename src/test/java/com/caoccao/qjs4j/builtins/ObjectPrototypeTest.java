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
    public void testCreate() {
        // Normal case: create object with null prototype
        JSValue result = ObjectPrototype.create(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        JSObject obj = result.asObject().orElse(null);
        assertNotNull(obj);
        assertNull(obj.getPrototype());

        // Normal case: create object with object prototype
        JSObject proto = new JSObject();
        proto.set("testProp", new JSString("testValue"));
        result = ObjectPrototype.create(ctx, JSUndefined.INSTANCE, new JSValue[]{proto});
        JSObject obj2 = result.asObject().orElse(null);
        assertNotNull(obj2);
        assertEquals(proto, obj2.getPrototype());
        // Should inherit property
        assertEquals("testValue", obj2.get("testProp").asString().map(JSString::value).orElse(""));

        // Edge case: invalid prototype
        result = ObjectPrototype.create(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: no arguments
        result = ObjectPrototype.create(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
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

        JSValue result = ObjectPrototype.defineProperty(ctx, JSUndefined.INSTANCE, new JSValue[]{
                obj, new JSString("testProp"), descriptor
        });
        assertEquals(obj, result);
        assertEquals("test", obj.get("testProp").asString().map(JSString::value).orElse(""));

        // Edge case: not enough arguments
        result = ObjectPrototype.defineProperty(ctx, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("prop")});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: first argument not object
        result = ObjectPrototype.defineProperty(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("not object"), new JSString("prop"), descriptor
        });
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testKeys() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));
        obj.set("c", new JSNumber(3));

        // Normal case: object with properties
        JSValue result = ObjectPrototype.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray keys = result.asArray().orElse(null);
        assertNotNull(keys);
        assertTrue(keys.getLength() >= 3); // May include prototype properties

        // Normal case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectPrototype.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        JSArray emptyKeys = result.asArray().orElse(null);
        assertNotNull(emptyKeys);
        assertEquals(0, emptyKeys.getLength());

        // Edge case: null
        result = ObjectPrototype.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: undefined
        result = ObjectPrototype.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: primitive
        result = ObjectPrototype.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("string")});
        JSArray primitiveKeys = result.asArray().orElse(null);
        assertNotNull(primitiveKeys);
        assertEquals(0, primitiveKeys.getLength());
    }

    @Test
    public void testValues() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSString("hello"));
        obj.set("c", JSBoolean.TRUE);

        // Normal case: object with properties
        JSValue result = ObjectPrototype.values(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray values = result.asArray().orElse(null);
        assertNotNull(values);
        assertTrue(values.getLength() >= 3);

        // Normal case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectPrototype.values(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        JSArray emptyValues = result.asArray().orElse(null);
        assertNotNull(emptyValues);
        assertEquals(0, emptyValues.getLength());

        // Edge case: null
        result = ObjectPrototype.values(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: primitive
        result = ObjectPrototype.values(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        JSArray primitiveValues = result.asArray().orElse(null);
        assertNotNull(primitiveValues);
        assertEquals(0, primitiveValues.getLength());
    }

    @Test
    public void testEntries() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSString("hello"));

        // Normal case: object with properties
        JSValue result = ObjectPrototype.entries(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray entries = result.asArray().orElse(null);
        assertNotNull(entries);
        assertTrue(entries.getLength() >= 2);

        // Normal case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectPrototype.entries(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        JSArray emptyEntries = result.asArray().orElse(null);
        assertNotNull(emptyEntries);
        assertEquals(0, emptyEntries.getLength());

        // Edge case: null
        result = ObjectPrototype.entries(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: primitive
        result = ObjectPrototype.entries(ctx, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.FALSE});
        JSArray primitiveEntries = result.asArray().orElse(null);
        assertNotNull(primitiveEntries);
        assertEquals(0, primitiveEntries.getLength());
    }

    @Test
    public void testAssign() {
        JSObject target = new JSObject();
        target.set("a", new JSNumber(1));

        JSObject source = new JSObject();
        source.set("b", new JSNumber(2));
        source.set("c", new JSString("hello"));

        // Normal case: assign sources to target
        JSValue result = ObjectPrototype.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{target, source});
        assertEquals(target, result);
        assertEquals(1.0, target.get("a").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals(2.0, target.get("b").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals("hello", target.get("c").asString().map(JSString::value).orElse(""));

        // Normal case: multiple sources
        JSObject source2 = new JSObject();
        source2.set("d", JSBoolean.TRUE);
        result = ObjectPrototype.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{target, source2});
        assertEquals(target, result);
        assertEquals(JSBoolean.TRUE, target.get("d"));

        // Normal case: skip null/undefined sources
        result = ObjectPrototype.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{target, JSNull.INSTANCE, source});
        assertEquals(target, result);

        // Edge case: no arguments
        result = ObjectPrototype.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: null target
        result = ObjectPrototype.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testFreeze() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: freeze object
        JSValue result = ObjectPrototype.freeze(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertEquals(obj, result);

        // Normal case: freeze primitive (returns primitive)
        result = ObjectPrototype.freeze(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("string")});
        assertEquals("string", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testToString() {
        // Normal case: undefined
        JSValue result = ObjectPrototype.toString(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals("[object Undefined]", result.asString().map(JSString::value).orElse(""));

        // Normal case: null
        result = ObjectPrototype.toString(ctx, JSNull.INSTANCE, new JSValue[]{});
        assertEquals("[object Null]", result.asString().map(JSString::value).orElse(""));

        // Normal case: object
        JSObject obj = new JSObject();
        result = ObjectPrototype.toString(ctx, obj, new JSValue[]{});
        assertEquals("[object Object]", result.asString().map(JSString::value).orElse(""));

        // Normal case: array
        JSArray arr = new JSArray();
        result = ObjectPrototype.toString(ctx, arr, new JSValue[]{});
        assertEquals("[object Array]", result.asString().map(JSString::value).orElse(""));

        // Normal case: function
        JSFunction func = new JSNativeFunction("test", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        result = ObjectPrototype.toString(ctx, func, new JSValue[]{});
        assertEquals("[object Function]", result.asString().map(JSString::value).orElse(""));

        // Normal case: string
        result = ObjectPrototype.toString(ctx, new JSString("test"), new JSValue[]{});
        assertEquals("[object String]", result.asString().map(JSString::value).orElse(""));

        // Normal case: number
        result = ObjectPrototype.toString(ctx, new JSNumber(42), new JSValue[]{});
        assertEquals("[object Number]", result.asString().map(JSString::value).orElse(""));

        // Normal case: boolean
        result = ObjectPrototype.toString(ctx, JSBoolean.TRUE, new JSValue[]{});
        assertEquals("[object Boolean]", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testValueOf() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: object
        JSValue result = ObjectPrototype.valueOf(ctx, obj, new JSValue[]{});
        assertEquals(obj, result);

        // Normal case: primitive
        JSString str = new JSString("hello");
        result = ObjectPrototype.valueOf(ctx, str, new JSValue[]{});
        assertEquals(str, result);

        // Normal case: null
        result = ObjectPrototype.valueOf(ctx, JSNull.INSTANCE, new JSValue[]{});
        assertEquals(JSNull.INSTANCE, result);
    }

    @Test
    public void testHasOwnProperty() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case: has property
        JSValue result = ObjectPrototype.hasOwnProperty(ctx, obj, new JSValue[]{new JSString("a")});
        assertEquals(JSBoolean.TRUE, result);

        // Normal case: doesn't have property
        result = ObjectPrototype.hasOwnProperty(ctx, obj, new JSValue[]{new JSString("c")});
        assertEquals(JSBoolean.FALSE, result);

        // Normal case: numeric property
        result = ObjectPrototype.hasOwnProperty(ctx, obj, new JSValue[]{new JSNumber(1)});
        assertEquals(JSBoolean.FALSE, result); // "1" != "a"

        // Edge case: no arguments
        result = ObjectPrototype.hasOwnProperty(ctx, obj, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-object
        result = ObjectPrototype.hasOwnProperty(ctx, new JSString("string"), new JSValue[]{new JSString("length")});
        assertEquals(JSBoolean.FALSE, result);
    }
}
