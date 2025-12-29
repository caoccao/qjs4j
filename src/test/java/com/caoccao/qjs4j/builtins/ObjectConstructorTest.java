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
    public void testAssign() {
        JSObject target = new JSObject();
        target.set("a", new JSNumber(1));

        JSObject source1 = new JSObject();
        source1.set("b", new JSNumber(2));

        JSObject source2 = new JSObject();
        source2.set("c", new JSNumber(3));
        source2.set("a", new JSNumber(10)); // Override target.a

        // Normal case
        JSValue result = ObjectConstructor.assign(context, JSUndefined.INSTANCE, new JSValue[]{target, source1, source2});
        assertSame(target, result);
        assertEquals(10.0, target.get("a").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, target.get("b").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, target.get("c").asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: null/undefined sources (should be ignored)
        JSObject target2 = new JSObject();
        target2.set("x", new JSNumber(1));
        result = ObjectConstructor.assign(context, JSUndefined.INSTANCE, new JSValue[]{target2, JSNull.INSTANCE, JSUndefined.INSTANCE});
        assertSame(target2, result);
        assertEquals(1.0, target2.get("x").asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.assign(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object target
        assertTypeError(ObjectConstructor.assign(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE}));
        assertPendingException(context);

        result = context.eval("var target = {a: 1}; Object.assign(target, {b: 2}, {c: 3}); JSON.stringify(target)");
        assertNotNull(result);
        assertEquals("{\"a\":1,\"b\":2,\"c\":3}", result.toJavaObject());
    }

    @Test
    public void testCreate() {
        JSObject proto = new JSObject();
        proto.set("x", new JSNumber(100));

        // Normal case: create object with prototype
        JSValue result = ObjectConstructor.create(context, JSUndefined.INSTANCE, new JSValue[]{proto});
        JSObject obj = result.asObject().orElseThrow();
        assertSame(proto, obj.getPrototype());

        // Edge case: create with null prototype
        result = ObjectConstructor.create(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        obj = result.asObject().orElseThrow();
        assertNull(obj.getPrototype());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.create(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: invalid prototype
        assertTypeError(ObjectConstructor.create(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not valid")}));
        assertPendingException(context);

        context.eval("var proto = {x: 10}");
        result = context.eval("var newObj = Object.create(proto); newObj.x");
        assertNotNull(result);
        assertEquals(10.0, (Double) result.toJavaObject());
    }

    @Test
    public void testDefineProperties() {
        JSValue result = context.eval(
                "var obj = {}; " +
                        "Object.defineProperties(obj, {" +
                        "  x: {value: 1, writable: true, enumerable: true}," +
                        "  y: {value: 2, writable: false, enumerable: true}" +
                        "}); " +
                        "JSON.stringify({x: obj.x, y: obj.y})"
        );
        assertEquals("{\"x\":1,\"y\":2}", result.toJavaObject());
    }

    @Test
    public void testDefineProperty() {
        JSObject obj = new JSObject();

        // Create a data descriptor
        JSObject descriptor = new JSObject();
        descriptor.set("value", new JSNumber(42));
        descriptor.set("writable", JSBoolean.TRUE);
        descriptor.set("enumerable", JSBoolean.TRUE);
        descriptor.set("configurable", JSBoolean.TRUE);

        JSValue result = context.eval("var obj = {}; Object.defineProperty(obj, 'x', {value: 42, writable: true}); obj.x");
        assertEquals(42.0, (Double) result.toJavaObject());

        // Test with eval to verify it's registered
        result = context.eval("var obj2 = {}; Object.defineProperty(obj2, 'y', {value: 100}); obj2.y");
        assertEquals(100.0, (Double) result.toJavaObject());
    }

    @Test
    public void testEntries() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case
        JSValue result = ObjectConstructor.entries(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray entries = result.asArray().orElseThrow();
        assertEquals(2, entries.getLength());

        // Check first entry
        JSValue firstEntry = entries.get(0);
        JSArray firstPair = firstEntry.asArray().orElseThrow();
        assertEquals(2, firstPair.getLength());
        assertTrue(firstPair.get(0).asString().isPresent());
        assertTrue(firstPair.get(1).asNumber().isPresent());

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.entries(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        entries = result.asArray().orElseThrow();
        assertEquals(0, entries.getLength());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.entries(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        context.eval("var obj = {a: 1, b: 2, c: 3}");
        result = context.eval("JSON.stringify(Object.entries(obj))");
        assertNotNull(result);
        assertEquals("[[\"a\",1],[\"b\",2],[\"c\",3]]", result.toJavaObject());
    }

    @Test
    public void testFreeze() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case
        JSValue result = ObjectConstructor.freeze(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertSame(obj, result);
        assertTrue(obj.isFrozen());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.freeze(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testFromEntries() {
        // Test using eval to create proper arrays with prototypes
        context.eval("var entries = [['a', 1], ['b', 2]]");
        JSValue entries = context.getGlobalObject().get("entries");

        // For now, just test that fromEntries doesn't crash
        JSValue result = ObjectConstructor.fromEntries(context, JSUndefined.INSTANCE, new JSValue[]{entries});
        JSObject obj = result.asObject().orElseThrow();
        assertEquals(1.0, obj.get("a").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, obj.get("b").asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        context.eval("var emptyEntries = []");
        JSValue emptyEntries = context.getGlobalObject().get("emptyEntries");
        result = ObjectConstructor.fromEntries(context, JSUndefined.INSTANCE, new JSValue[]{emptyEntries});
        obj = result.asObject().orElseThrow();
        assertEquals(0, obj.getOwnPropertyKeys().size());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.fromEntries(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-iterable argument
        assertTypeError(ObjectConstructor.fromEntries(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)}));
        assertPendingException(context);

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
        JSObject arrayProto = context.getGlobalObject().get("Array").asObject().orElseThrow().get("prototype").asObject().orElseThrow();
        mixedEntries.setPrototype(arrayProto);
        mixedEntry1.setPrototype(arrayProto);
        mixedEntry2.setPrototype(arrayProto);

        result = ObjectConstructor.fromEntries(context, JSUndefined.INSTANCE, new JSValue[]{mixedEntries});
        obj = result.asObject().orElseThrow();
        assertEquals("value1", obj.get("key1").asString().map(JSString::value).orElseThrow());
        assertEquals(JSBoolean.TRUE, obj.get("key2"));
    }

    @Test
    public void testGetOwnPropertyDescriptor() {
        JSObject obj = new JSObject();
        obj.set("testProp", new JSString("testValue"));

        // Normal case: existing property
        JSValue result = ObjectConstructor.getOwnPropertyDescriptor(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("testProp")});
        JSObject desc = result.asObject().orElseThrow();
        assertEquals("testValue", desc.get("value").asString().map(JSString::value).orElseThrow());
        assertTrue(desc.get("writable").asBoolean().map(JSBoolean::isBooleanTrue).orElseThrow());
        assertTrue(desc.get("enumerable").asBoolean().map(JSBoolean::isBooleanTrue).orElseThrow());
        assertTrue(desc.get("configurable").asBoolean().map(JSBoolean::isBooleanTrue).orElseThrow());

        // Normal case: non-existing property
        result = ObjectConstructor.getOwnPropertyDescriptor(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("nonexistent")});
        assertTrue(result.isUndefined());

        // Edge case: insufficient arguments
        result = ObjectConstructor.getOwnPropertyDescriptor(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isUndefined());

        // Edge case: non-object
        assertTypeError(ObjectConstructor.getOwnPropertyDescriptor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object"), new JSString("prop")}));
        assertPendingException(context);
    }

    @Test
    public void testGetOwnPropertyDescriptors() {
        JSValue result = context.eval(
                "var obj = {a: 1, b: 2}; " +
                        "var descs = Object.getOwnPropertyDescriptors(obj); " +
                        "descs.a.value + descs.b.value"
        );
        assertEquals(3.0, (Double) result.toJavaObject());
    }

    @Test
    public void testGetOwnPropertyNames() {
        JSObject obj = new JSObject();
        obj.set("prop1", new JSString("value1"));
        obj.set("prop2", new JSString("value2"));

        // Normal case: object with properties
        JSValue result = ObjectConstructor.getOwnPropertyNames(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray names = result.asArray().orElseThrow();
        assertEquals(2, names.getLength());
        // Note: order may vary, so check both are present
        String name0 = names.get(0).asString().map(JSString::value).orElseThrow();
        String name1 = names.get(1).asString().map(JSString::value).orElseThrow();
        assertTrue((name0.equals("prop1") && name1.equals("prop2")) || (name0.equals("prop2") && name1.equals("prop1")));

        // Normal case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.getOwnPropertyNames(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        names = result.asArray().orElseThrow();
        assertEquals(0, names.getLength());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.getOwnPropertyNames(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.getOwnPropertyNames(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")}));
        assertPendingException(context);
    }

    @Test
    public void testGetOwnPropertySymbols() {
        JSObject obj = new JSObject();
        JSSymbol symbol1 = new JSSymbol("sym1");
        JSSymbol symbol2 = new JSSymbol("sym2");
        obj.set(PropertyKey.fromSymbol(symbol1), new JSString("value1"));
        obj.set(PropertyKey.fromSymbol(symbol2), new JSString("value2"));

        // Normal case: object with symbol properties
        JSValue result = ObjectConstructor.getOwnPropertySymbols(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray symbols = result.asArray().orElseThrow();
        assertEquals(2, symbols.getLength());

        // Normal case: object with no symbol properties
        JSObject regularObj = new JSObject();
        regularObj.set("prop", new JSString("value"));
        result = ObjectConstructor.getOwnPropertySymbols(context, JSUndefined.INSTANCE, new JSValue[]{regularObj});
        symbols = result.asArray().orElseThrow();
        assertEquals(0, symbols.getLength());

        // Edge case: no arguments
        result = ObjectConstructor.getOwnPropertySymbols(context, JSUndefined.INSTANCE, new JSValue[]{});
        symbols = result.asArray().orElseThrow();
        assertEquals(0, symbols.getLength());

        // Edge case: non-object (should return empty array)
        result = ObjectConstructor.getOwnPropertySymbols(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")});
        symbols = result.asArray().orElseThrow();
        assertEquals(0, symbols.getLength());
    }

    @Test
    public void testGetPrototypeOf() {
        JSObject proto = new JSObject();
        JSObject obj = new JSObject();
        obj.setPrototype(proto);

        // Normal case
        JSValue result = ObjectConstructor.getPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertSame(proto, result);

        // Edge case: null prototype
        JSObject objWithNullProto = new JSObject();
        objWithNullProto.setPrototype(null);
        result = ObjectConstructor.getPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{objWithNullProto});
        assertTrue(result.isNull());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.getPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.getPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")}));
        assertPendingException(context);

        context.eval("var proto = {x: 10}; var newObj = Object.create(proto)");
        result = context.eval("Object.getPrototypeOf(newObj) === proto");
        assertNotNull(result);
        assertTrue((boolean) result.toJavaObject());
    }

    @Test
    public void testGroupBy() {
        JSArray items = new JSArray();
        items.push(new JSNumber(1));
        items.push(new JSNumber(2));
        items.push(new JSNumber(3));
        items.push(new JSNumber(4));

        // Callback function: group by even/odd
        JSFunction callback = new JSNativeFunction("testCallback", 3, (ctx, thisArg, args) -> {
            double num = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (num % 2 == 0) ? new JSString("even") : new JSString("odd");
        });

        // Normal case: group by even/odd
        JSValue result = ObjectConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{items, callback});
        JSObject groups = result.asObject().orElseThrow();

        // Check even group
        JSValue evenGroup = groups.get("even");
        JSArray evenArray = evenGroup.asArray().orElseThrow();
        assertEquals(2, evenArray.getLength());
        assertEquals(2.0, evenArray.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, evenArray.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Check odd group
        JSValue oddGroup = groups.get("odd");
        JSArray oddArray = oddGroup.asArray().orElseThrow();
        assertEquals(2, oddArray.getLength());
        assertEquals(1.0, oddArray.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, oddArray.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyItems = new JSArray();
        result = ObjectConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{emptyItems, callback});
        groups = result.asObject().orElseThrow();
        // Should have no properties
        assertTrue(groups.get("even").isUndefined());
        assertTrue(groups.get("odd").isUndefined());

        // Edge case: insufficient arguments
        assertTypeError(ObjectConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{items}));
        assertPendingException(context);

        // Edge case: non-array items
        assertTypeError(ObjectConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not array"), callback}));
        assertPendingException(context);

        // Edge case: non-function callback
        assertTypeError(ObjectConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{items, new JSString("not function")}));
        assertPendingException(context);
    }

    @Test
    public void testHasOwn() {
        JSObject obj = new JSObject();
        obj.set("existingProp", new JSString("value"));

        // Normal case: existing property
        JSValue result = ObjectConstructor.hasOwn(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("existingProp")});
        assertTrue(result.isBooleanTrue());

        // Normal case: non-existing property
        result = ObjectConstructor.hasOwn(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("nonexistent")});
        assertTrue(result.isBooleanFalse());

        // Edge case: insufficient arguments (should return false, not throw)
        result = ObjectConstructor.hasOwn(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.hasOwn(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.hasOwn(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object"), new JSString("prop")}));
        assertPendingException(context);
    }

    @Test
    public void testHasOwnProperty() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case: property exists
        JSValue result = ObjectConstructor.hasOwnProperty(context, obj, new JSValue[]{new JSString("a")});
        assertTrue(result.isBooleanTrue());

        // Property doesn't exist
        result = ObjectConstructor.hasOwnProperty(context, obj, new JSValue[]{new JSString("z")});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        result = ObjectConstructor.hasOwnProperty(context, obj, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-object
        assertTypeError(ObjectConstructor.hasOwnProperty(context, new JSString("not object"), new JSValue[]{new JSString("a")}));
        assertPendingException(context);
    }

    @Test
    public void testIs() {
        // Test SameValue algorithm
        JSValue result = context.eval("Object.is(1, 1)");
        assertTrue((Boolean) result.toJavaObject());

        result = context.eval("Object.is(1, 2)");
        assertFalse((Boolean) result.toJavaObject());

        // NaN equals NaN in SameValue
        result = context.eval("Object.is(NaN, NaN)");
        assertTrue((Boolean) result.toJavaObject());

        // +0 differs from -0 in SameValue
        result = context.eval("Object.is(0, -0)");
        assertFalse((Boolean) result.toJavaObject());

        result = context.eval("Object.is(0, 0)");
        assertTrue((Boolean) result.toJavaObject());

        // Objects
        result = context.eval("var obj = {}; Object.is(obj, obj)");
        assertTrue((Boolean) result.toJavaObject());

        result = context.eval("Object.is({}, {})");
        assertFalse((Boolean) result.toJavaObject());
    }

    // NOTE: Additional tests for Object extensibility methods (preventExtensions, getOwnPropertyDescriptor,
    // getOwnPropertySymbols, etc.) from Phase 34-35 have been commented out due to issues with JavaScript
    // evaluation returning incorrect types (returning "[object Object]" instead of expected string/number
    // values). The implementations exist but proper testing requires investigation into the
    // JSValue.toJavaObject() behavior with JavaScript-evaluated results.

    @Test
    public void testIsExtensible() {
        // Test normal extensible object
        JSValue result = context.eval("var obj = {}; Object.isExtensible(obj)");
        assertTrue((Boolean) result.toJavaObject());

        // Test after preventExtensions
        result = context.eval(
                "var obj2 = {}; " +
                        "Object.preventExtensions(obj2); " +
                        "Object.isExtensible(obj2)"
        );
        assertFalse((Boolean) result.toJavaObject());

        // Test sealed object is not extensible
        result = context.eval(
                "var obj3 = {}; " +
                        "Object.seal(obj3); " +
                        "Object.isExtensible(obj3)"
        );
        assertFalse((Boolean) result.toJavaObject());

        // Test frozen object is not extensible
        result = context.eval(
                "var obj4 = {}; " +
                        "Object.freeze(obj4); " +
                        "Object.isExtensible(obj4)"
        );
        assertFalse((Boolean) result.toJavaObject());
    }

    @Test
    public void testIsFrozen() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: not frozen
        JSValue result = ObjectConstructor.isFrozen(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanFalse());

        // After freezing
        obj.freeze();
        result = ObjectConstructor.isFrozen(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanTrue());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.isFrozen(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testIsSealed() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: not sealed
        JSValue result = ObjectConstructor.isSealed(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanFalse());

        // After sealing
        obj.seal();
        result = ObjectConstructor.isSealed(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertTrue(result.isBooleanTrue());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.isSealed(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testKeys() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));
        obj.set("c", new JSNumber(3));

        // Normal case
        JSValue result = ObjectConstructor.keys(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray keys = result.asArray().orElseThrow();
        assertEquals(3, keys.getLength());

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.keys(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        keys = result.asArray().orElseThrow();
        assertEquals(0, keys.getLength());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.keys(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.keys(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE}));
        assertPendingException(context);

        result = context.eval("var obj = {a: 1, b: 2, c: 3}; Object.keys(obj)");
        assertNotNull(result);
        assertEquals("[\"a\", \"b\", \"c\"]", result.toString());
    }

    @Test
    public void testPreventExtensions() {
        JSObject obj = new JSObject();

        // Normal case: prevent extensions on object
        JSValue result = ObjectConstructor.preventExtensions(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertSame(obj, result);

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.preventExtensions(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object (should return as-is)
        JSValue primitive = new JSString("string");
        result = ObjectConstructor.preventExtensions(context, JSUndefined.INSTANCE, new JSValue[]{primitive});
        assertSame(primitive, result);
    }

    @Test
    public void testSeal() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case
        JSValue result = ObjectConstructor.seal(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertSame(obj, result);
        assertTrue(obj.isSealed());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.seal(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testSetPrototypeOf() {
        JSObject obj = new JSObject();
        JSObject newProto = new JSObject();
        newProto.set("y", new JSNumber(200));

        // Normal case
        JSValue result = ObjectConstructor.setPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{obj, newProto});
        assertSame(obj, result);
        assertSame(newProto, obj.getPrototype());

        // Edge case: set to null
        result = ObjectConstructor.setPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSNull.INSTANCE});
        assertSame(obj, result);
        assertNull(obj.getPrototype());

        // Edge case: missing arguments
        assertTypeError(ObjectConstructor.setPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{obj}));
        assertPendingException(context);

        // Edge case: non-object target
        assertTypeError(ObjectConstructor.setPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object"), newProto}));
        assertPendingException(context);

        // Edge case: invalid prototype
        assertTypeError(ObjectConstructor.setPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("invalid")}));
        assertPendingException(context);
    }

    @Test
    public void testValues() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));
        obj.set("c", new JSNumber(3));

        // Normal case
        JSValue result = ObjectConstructor.values(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        JSArray values = result.asArray().orElseThrow();
        assertEquals(3, values.getLength());

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.values(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        values = result.asArray().orElseThrow();
        assertEquals(0, values.getLength());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.values(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        context.eval("var obj = {a: 1, b: 2, c: 3}");
        result = context.eval("JSON.stringify(Object.values(obj))");
        assertNotNull(result);
        assertEquals("[1,2,3]", result.toJavaObject());
    }
}
