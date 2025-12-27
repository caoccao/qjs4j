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
 * Unit tests for Object constructor static methods.
 */
public class ObjectConstructorTest extends BaseTest {
    @Test
    public void testFromEntries() {
        // Test using eval to create proper arrays with prototypes
        ctx.eval("var entries = [['a', 1], ['b', 2]]");
        JSValue entries = ctx.getGlobalObject().get("entries");

        // For now, just test that fromEntries doesn't crash
        JSValue result = ObjectConstructor.fromEntries(ctx, JSUndefined.INSTANCE, new JSValue[]{entries});
        JSObject obj = result.asObject().orElse(null);
        assertNotNull(obj);
        assertEquals(1.0, obj.get("a").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals(2.0, obj.get("b").asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: empty array
        ctx.eval("var emptyEntries = []");
        JSValue emptyEntries = ctx.getGlobalObject().get("emptyEntries");
        result = ObjectConstructor.fromEntries(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyEntries});
        obj = result.asObject().orElse(null);
        assertNotNull(obj);
        assertEquals(0, obj.getOwnPropertyKeys().size());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.fromEntries(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: non-iterable argument
        assertTypeError(ObjectConstructor.fromEntries(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)}));
        assertPendingException(ctx);

        // Test with string keys and various value types - use manual creation
        JSArray mixedEntries = new JSArray();
        JSArray mixedEntry1 = new JSArray();
        mixedEntry1.push(new JSString("key1"));
        mixedEntry1.push(new JSString("value1"));
        mixedEntries.push(mixedEntry1);

        JSArray mixedEntry2 = new JSArray();
        mixedEntry2.push(new JSString("key2"));
        mixedEntry2.push(JSBoolean.TRUE);
        mixedEntries.push(mixedEntry2);

        // Set prototype for the arrays
        JSObject arrayProto = ctx.getGlobalObject().get("Array").asObject().orElse(null).get("prototype").asObject().orElse(null);
        mixedEntries.setPrototype(arrayProto);
        mixedEntry1.setPrototype(arrayProto);
        mixedEntry2.setPrototype(arrayProto);

        result = ObjectConstructor.fromEntries(ctx, JSUndefined.INSTANCE, new JSValue[]{mixedEntries});
        obj = result.asObject().orElse(null);
        assertNotNull(obj);
        assertEquals("value1", obj.get("key1").asString().map(JSString::value).orElse(""));
        assertEquals(JSBoolean.TRUE, obj.get("key2"));
    }

    @Test
    public void testObjectAssign() {
        JSObject target = new JSObject();
        target.set("a", new JSNumber(1));

        JSObject source1 = new JSObject();
        source1.set("b", new JSNumber(2));

        JSObject source2 = new JSObject();
        source2.set("c", new JSNumber(3));
        source2.set("a", new JSNumber(10)); // Override target.a

        // Normal case
        JSValue result = ObjectConstructor.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{target, source1, source2});
        assertSame(target, result);
        assertEquals(10.0, target.get("a").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals(2.0, target.get("b").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals(3.0, target.get("c").asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: null/undefined sources (should be ignored)
        JSObject target2 = new JSObject();
        target2.set("x", new JSNumber(1));
        result = ObjectConstructor.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{target2, JSNull.INSTANCE, JSUndefined.INSTANCE});
        assertSame(target2, result);
        assertEquals(1.0, target2.get("x").asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: non-object target
        assertTypeError(ObjectConstructor.assign(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE}));
        assertPendingException(ctx);

        result = ctx.eval("var target = {a: 1}; Object.assign(target, {b: 2}, {c: 3}); JSON.stringify(target)");
        assertNotNull(result);
        assertEquals("{\"a\":1,\"b\":2,\"c\":3}", result.toJavaObject());
    }

    @Test
    public void testObjectCreate() {
        JSObject proto = new JSObject();
        proto.set("x", new JSNumber(100));

        // Normal case: create object with prototype
        JSValue result = ObjectConstructor.create(ctx, JSUndefined.INSTANCE, new JSValue[]{proto});
        JSObject obj = result.asObject().orElse(null);
        assertNotNull(obj);
        assertSame(proto, obj.getPrototype());

        // Edge case: create with null prototype
        result = ObjectConstructor.create(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        obj = result.asObject().orElse(null);
        assertNotNull(obj);
        assertNull(obj.getPrototype());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.create(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: invalid prototype
        assertTypeError(ObjectConstructor.create(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not valid")}));
        assertPendingException(ctx);

        ctx.eval("var proto = {x: 10}");
        result = ctx.eval("var newObj = Object.create(proto); newObj.x");
        assertNotNull(result);
        assertEquals(10.0, (Double) result.toJavaObject());
    }

    @Test
    public void testObjectEntries() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case
        JSValue result = ObjectConstructor.entries(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray entries = result.asArray().orElse(null);
        assertNotNull(entries);
        assertEquals(2, entries.getLength());

        // Check first entry
        JSValue firstEntry = entries.get(0);
        JSArray firstPair = firstEntry.asArray().orElse(null);
        assertNotNull(firstPair);
        assertEquals(2, firstPair.getLength());
        assertTrue(firstPair.get(0).asString().isPresent());
        assertTrue(firstPair.get(1).asNumber().isPresent());

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.entries(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        entries = result.asArray().orElse(null);
        assertNotNull(entries);
        assertEquals(0, entries.getLength());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.entries(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);

        ctx.eval("var obj = {a: 1, b: 2, c: 3}");
        result = ctx.eval("JSON.stringify(Object.entries(obj))");
        assertNotNull(result);
        assertEquals("[[\"a\",1],[\"b\",2],[\"c\",3]]", result.toJavaObject());
    }

    @Test
    public void testObjectFreeze() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case
        JSValue result = ObjectConstructor.freeze(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertSame(obj, result);
        assertTrue(obj.isFrozen());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.freeze(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testObjectGetPrototypeOf() {
        JSObject proto = new JSObject();
        JSObject obj = new JSObject();
        obj.setPrototype(proto);

        // Normal case
        JSValue result = ObjectConstructor.getPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertSame(proto, result);

        // Edge case: null prototype
        JSObject objWithNullProto = new JSObject();
        objWithNullProto.setPrototype(null);
        result = ObjectConstructor.getPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{objWithNullProto});
        assertTrue(result.isNull());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.getPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.getPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")}));
        assertPendingException(ctx);

        ctx.eval("var proto = {x: 10}; var newObj = Object.create(proto)");
        result = ctx.eval("Object.getPrototypeOf(newObj) === proto");
        assertNotNull(result);
        assertTrue((boolean) result.toJavaObject());
    }

    @Test
    public void testObjectHasOwnProperty() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case: property exists
        JSValue result = ObjectConstructor.hasOwnProperty(ctx, obj, new JSValue[]{new JSString("a")});
        assertTrue(result.isBooleanTrue());

        // Property doesn't exist
        result = ObjectConstructor.hasOwnProperty(ctx, obj, new JSValue[]{new JSString("z")});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        result = ObjectConstructor.hasOwnProperty(ctx, obj, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-object
        assertTypeError(ObjectConstructor.hasOwnProperty(ctx, new JSString("not object"), new JSValue[]{new JSString("a")}));
        assertPendingException(ctx);
    }

    @Test
    public void testObjectIsFrozen() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: not frozen
        JSValue result = ObjectConstructor.isFrozen(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanFalse());

        // After freezing
        obj.freeze();
        result = ObjectConstructor.isFrozen(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanTrue());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.isFrozen(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testObjectIsSealed() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: not sealed
        JSValue result = ObjectConstructor.isSealed(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanFalse());

        // After sealing
        obj.seal();
        result = ObjectConstructor.isSealed(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanTrue());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.isSealed(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testObjectKeys() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));
        obj.set("c", new JSNumber(3));

        // Normal case
        JSValue result = ObjectConstructor.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray keys = result.asArray().orElse(null);
        assertNotNull(keys);
        assertEquals(3, keys.getLength());

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        keys = result.asArray().orElse(null);
        assertNotNull(keys);
        assertEquals(0, keys.getLength());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.keys(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE}));
        assertPendingException(ctx);

        result = ctx.eval("var obj = {a: 1, b: 2, c: 3}; Object.keys(obj)");
        assertNotNull(result);
        assertEquals("[\"a\", \"b\", \"c\"]", result.toString());
    }

    @Test
    public void testObjectValues() {
        ctx.eval("var obj = {a: 1, b: 2, c: 3}");
        JSValue result = ctx.eval("JSON.stringify(Object.values(obj))");
        assertNotNull(result);
        assertEquals("[1,2,3]", result.toJavaObject());
    }

    @Test
    public void testSeal() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case
        JSValue result = ObjectConstructor.seal(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertSame(obj, result);
        assertTrue(obj.isSealed());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.seal(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testSetPrototypeOf() {
        JSObject obj = new JSObject();
        JSObject newProto = new JSObject();
        newProto.set("y", new JSNumber(200));

        // Normal case
        JSValue result = ObjectConstructor.setPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{obj, newProto});
        assertSame(obj, result);
        assertSame(newProto, obj.getPrototype());

        // Edge case: set to null
        result = ObjectConstructor.setPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{obj, JSNull.INSTANCE});
        assertSame(obj, result);
        assertNull(obj.getPrototype());

        // Edge case: missing arguments
        assertTypeError(ObjectConstructor.setPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{obj}));
        assertPendingException(ctx);

        // Edge case: non-object target
        assertTypeError(ObjectConstructor.setPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object"), newProto}));
        assertPendingException(ctx);

        // Edge case: invalid prototype
        assertTypeError(ObjectConstructor.setPrototypeOf(ctx, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("invalid")}));
        assertPendingException(ctx);
    }

    @Test
    public void testValues() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));
        obj.set("c", new JSNumber(3));

        // Normal case
        JSValue result = ObjectConstructor.values(ctx, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray values = result.asArray().orElse(null);
        assertNotNull(values);
        assertEquals(3, values.getLength());

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.values(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        values = result.asArray().orElse(null);
        assertNotNull(values);
        assertEquals(0, values.getLength());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.values(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }
}
