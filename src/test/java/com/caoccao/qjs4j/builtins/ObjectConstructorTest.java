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
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Object constructor static methods.
 */
public class ObjectConstructorTest extends BaseJavetTest {
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
        assertThat(result).isSameAs(target);
        assertThat(target.get("a").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0);
        assertThat(target.get("b").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(target.get("c").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: null/undefined sources (should be ignored)
        JSObject target2 = new JSObject();
        target2.set("x", new JSNumber(1));
        result = ObjectConstructor.assign(context, JSUndefined.INSTANCE, new JSValue[]{target2, JSNull.INSTANCE, JSUndefined.INSTANCE});
        assertThat(result).isSameAs(target2);
        assertThat(target2.get("x").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.assign(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object target
        assertTypeError(ObjectConstructor.assign(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE}));
        assertPendingException(context);

        assertStringWithJavet("var target = {a: 1}; Object.assign(target, {b: 2}, {c: 3}); JSON.stringify(target)");
    }

    @Test
    public void testBuiltInFunctions() {
        assertStringWithJavet(
                """
                        JSON.stringify(Object.getOwnPropertyNames(globalThis)
                            .sort()
                            .filter(name => !['AsyncDisposableStack', 'DisposableStack', 'WebAssembly'].includes(name))
                            .filter(name => typeof globalThis[name] === 'function')
                            .map(name => [name, globalThis[name].length]));""");
    }

    @Test
    public void testBuiltInObjects() {
        assertStringWithJavet(
                """
                        JSON.stringify(Object.getOwnPropertyNames(globalThis)
                            .sort()
                            .filter(name => !['AsyncDisposableStack', 'DisposableStack', 'WebAssembly'].includes(name)));""");
    }

    @Test
    public void testCreate() {
        JSObject proto = new JSObject();
        proto.set("x", new JSNumber(100));

        // Normal case: create object with prototype
        JSValue result = ObjectConstructor.create(context, JSUndefined.INSTANCE, new JSValue[]{proto});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, obj -> assertThat(obj.getPrototype()).isSameAs(proto));

        // Edge case: create with null prototype
        result = ObjectConstructor.create(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, obj -> assertThat(obj.getPrototype()).isNull());

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.create(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: invalid prototype
        assertTypeError(ObjectConstructor.create(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not valid")}));
        assertPendingException(context);

        context.eval("var proto = {x: 10}");
        result = context.eval("var newObj = Object.create(proto); newObj.x");
        assertThat(result).isNotNull();
        assertThat(result.toJavaObject()).isEqualTo(10.0);

        assertBooleanWithJavet(
                "Object.create(null).prototype === null",
                "Object.getPrototypeOf(Object.create(null)) === null",
                "Object.create({}).prototype === {}.prototype",
                "Object.create({a:1}).prototype === {b:2}.prototype");

        assertErrorWithJavet(
                "Object.create()",
                "Object.create(123)",
                "Object.create(undefined)");
    }

    @Test
    public void testDefineProperties() {
        assertStringWithJavet("""
                var obj = {};
                Object.defineProperties(obj, {
                  x: {value: 1, writable: true, enumerable: true},
                  y: {value: 2, writable: false, enumerable: true}
                });
                JSON.stringify({x: obj.x, y: obj.y})""");
    }

    @Test
    public void testDefineProperty() {
        assertIntegerWithJavet(
                "var obj = {}; Object.defineProperty(obj, 'x', {value: 42, writable: true}); obj.x",
                "var obj2 = {}; Object.defineProperty(obj2, 'y', {value: 100}); obj2.y");
    }

    @Test
    public void testEntries() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case
        JSValue result = ObjectConstructor.entries(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, entries -> {
            assertThat(entries.getLength()).isEqualTo(2);
            // Check first entry
            JSValue firstEntry = entries.get(0);
            assertThat(firstEntry).isInstanceOfSatisfying(JSArray.class, firstPair -> {
                assertThat(firstPair.getLength()).isEqualTo(2);
                assertThat(firstPair.get(0).asString().isPresent()).isTrue();
                assertThat(firstPair.get(1).asNumber().isPresent()).isTrue();
            });
        });

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.entries(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, entries -> assertThat(entries.getLength()).isEqualTo(0));

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.entries(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        assertStringWithJavet("""
                var obj = {a: 1, b: 2, c: 3};
                JSON.stringify(Object.entries(obj))""");
    }

    @Test
    public void testFreeze() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case
        JSValue result = ObjectConstructor.freeze(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result).isSameAs(obj);
        assertThat(obj.isFrozen()).isTrue();

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.freeze(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Normal case: freeze primitive (returns primitive)
        result = ObjectPrototype.freeze(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("string")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("string"));

        // Edge case: freeze number primitive
        result = ObjectPrototype.freeze(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(42.0));

        // Edge case: freeze boolean primitive
        result = ObjectPrototype.freeze(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.TRUE});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Edge case: freeze null (returns null)
        result = ObjectPrototype.freeze(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertThat(result).isEqualTo(JSNull.INSTANCE);

        // Edge case: freeze undefined (returns undefined)
        result = ObjectPrototype.freeze(context, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        assertBooleanWithJavet(
                // Test Object.isFrozen returns true after freeze
                "var obj = {a: 1}; Object.freeze(obj); Object.isFrozen(obj)",
                // Test Object.isFrozen returns false before freeze
                "var obj = {a: 1}; !Object.isFrozen(obj)",
                // Test freeze on empty object
                "var obj = {}; Object.freeze(obj); Object.isFrozen(obj)",
                // Test freeze returns same object
                "var obj = {a: 1}; Object.freeze(obj) === obj",
                // Test freeze on nested object (shallow freeze)
                "var obj = {a: {b: 1}}; Object.freeze(obj); obj.a.b = 999; obj.a.b === 999",
                // Test freeze on object with getter/setter
                """
                        var obj = {_a: 1}; Object.defineProperty(obj, 'a', {get: function() {return this._a;}});
                        Object.freeze(obj);
                        Object.isFrozen(obj)""",
                // Test isExtensible returns false after freeze
                "var obj = {a: 1}; Object.freeze(obj); !Object.isExtensible(obj)",
                // Test isSealed returns true after freeze
                "var obj = {a: 1}; Object.freeze(obj); Object.isSealed(obj)",
                // Test freeze on already frozen object
                """
                        var obj = {a: 1}; Object.freeze(obj);
                        Object.freeze(obj);
                        Object.isFrozen(obj)""",
                // Test freeze prevents adding properties
                """
                        var obj = {a: 1}; Object.freeze(obj);
                        obj.b = 2;
                        obj.b === undefined""",
                // Test freeze prevents deleting properties
                """
                        var obj = {a: 1}; Object.freeze(obj);
                        delete obj.a;
                        obj.a === 1""",
                // Test freeze prevents modifying properties
                """
                        var obj = {a: 1}; Object.freeze(obj);
                        obj.a = 999;
                        obj.a === 1""",
                // Test freeze on array
                """
                        var arr = [1, 2, 3]; Object.freeze(arr);
                        arr.push(4);
                        arr.length === 3""",
                // Test freeze on array prevents modification
                """
                        var arr = [1, 2, 3]; Object.freeze(arr);
                        arr[0] = 999;
                        arr[0] === 1""",
                // Test freeze prevents adding to nested object but parent is frozen
                """
                        var obj = {a: {b: 1}}; Object.freeze(obj);
                        obj.c = 2;
                        obj.c === undefined""",
                // Test freeze on object with symbols
                """
                        var sym = Symbol('test'); var obj = {}; obj[sym] = 1;
                        Object.freeze(obj);
                        obj[sym] = 999;
                        obj[sym] === 1""",
                // Test freeze on object with non-enumerable properties
                """
                        var obj = {}; Object.defineProperty(obj, 'a', {value: 1, enumerable: false});
                        Object.freeze(obj);
                        obj.a = 999;
                        obj.a === 1""",
                // Test freeze on function
                """
                        var func = function() {}; Object.freeze(func);
                        func.newProp = 1;
                        func.newProp === undefined""",
                // Test writable is false after freeze
                """
                        var obj = {a: 1};
                        Object.freeze(obj);
                        var desc = Object.getOwnPropertyDescriptor(obj, 'a');
                        desc.writable === false""",
                // Test configurable is false after freeze
                """
                        var obj = {a: 1};
                        Object.freeze(obj);
                        var desc = Object.getOwnPropertyDescriptor(obj, 'a');
                        desc.configurable === false""",
                // Test enumerable is unchanged after freeze
                """
                        var obj = {a: 1};
                        Object.freeze(obj);
                        var desc = Object.getOwnPropertyDescriptor(obj, 'a');
                        desc.enumerable === true""",
                // Test accessor property configurable is false after freeze
                """
                        var obj = {};
                        Object.defineProperty(obj, 'x', {
                            get: function() { return 1; },
                            configurable: true,
                            enumerable: true
                        });
                        Object.freeze(obj);
                        var desc = Object.getOwnPropertyDescriptor(obj, 'x');
                        desc.configurable === false""",
                // Test accessor property does not have writable after freeze
                """
                        var obj = {};
                        Object.defineProperty(obj, 'x', {
                            get: function() { return 1; },
                            configurable: true
                        });
                        Object.freeze(obj);
                        var desc = Object.getOwnPropertyDescriptor(obj, 'x');
                        desc.writable === undefined""",
                // Test accessor property getter is preserved after freeze
                """
                        var obj = {};
                        Object.defineProperty(obj, 'x', {
                            get: function() { return 42; },
                            configurable: true
                        });
                        Object.freeze(obj);
                        obj.x === 42""",
                // Test non-enumerable property remains non-enumerable after freeze
                """
                        var obj = {};
                        Object.defineProperty(obj, 'a', {value: 1, enumerable: false, writable: true, configurable: true});
                        Object.freeze(obj);
                        var desc = Object.getOwnPropertyDescriptor(obj, 'a');
                        desc.enumerable === false && desc.writable === false && desc.configurable === false""",
                // Test multiple properties all become non-writable and non-configurable
                """
                        var obj = {a: 1, b: 2, c: 3};
                        Object.freeze(obj);
                        var descA = Object.getOwnPropertyDescriptor(obj, 'a');
                        var descB = Object.getOwnPropertyDescriptor(obj, 'b');
                        var descC = Object.getOwnPropertyDescriptor(obj, 'c');
                        descA.writable === false && descB.writable === false && descC.writable === false &&
                        descA.configurable === false && descB.configurable === false && descC.configurable === false""");
    }

    @Test
    public void testFromEntries() {
        // Test using eval to create proper arrays with prototypes
        context.eval("var entries = [['a', 1], ['b', 2]]");
        JSValue entries = context.getGlobalObject().get("entries");

        // For now, just test that fromEntries doesn't crash
        JSValue result = ObjectConstructor.fromEntries(context, JSUndefined.INSTANCE, new JSValue[]{entries});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, obj -> {
            assertThat(obj.get("a").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
            assertThat(obj.get("b").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        });

        // Edge case: empty array
        context.eval("var emptyEntries = []");
        JSValue emptyEntries = context.getGlobalObject().get("emptyEntries");
        result = ObjectConstructor.fromEntries(context, JSUndefined.INSTANCE, new JSValue[]{emptyEntries});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, obj -> assertThat(obj.getOwnPropertyKeys().size()).isEqualTo(0));

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
        JSValue arrayConstructor = context.getGlobalObject().get("Array");
        assertThat(arrayConstructor).isInstanceOfSatisfying(JSObject.class, arrayObj -> {
            JSValue arrayProtoValue = arrayObj.get("prototype");
            assertThat(arrayProtoValue).isInstanceOfSatisfying(JSObject.class, arrayProto -> {
                mixedEntries.setPrototype(arrayProto);
                mixedEntry1.setPrototype(arrayProto);
                mixedEntry2.setPrototype(arrayProto);
            });
        });

        result = ObjectConstructor.fromEntries(context, JSUndefined.INSTANCE, new JSValue[]{mixedEntries});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, obj -> {
            assertThat(obj.get("key1").asString().map(JSString::value).orElseThrow()).isEqualTo("value1");
            assertThat(obj.get("key2")).isEqualTo(JSBoolean.TRUE);
        });
    }

    @Test
    public void testGetOwnPropertyDescriptor() {
        JSObject obj = new JSObject();
        obj.set("testProp", new JSString("testValue"));

        // Normal case: existing property
        JSValue result = ObjectConstructor.getOwnPropertyDescriptor(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("testProp")});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, desc -> {
            assertThat(desc.get("value").asString().map(JSString::value).orElseThrow()).isEqualTo("testValue");
            assertThat(desc.get("writable").asBoolean().map(JSBoolean::isBooleanTrue).orElseThrow()).isTrue();
            assertThat(desc.get("enumerable").asBoolean().map(JSBoolean::isBooleanTrue).orElseThrow()).isTrue();
            assertThat(desc.get("configurable").asBoolean().map(JSBoolean::isBooleanTrue).orElseThrow()).isTrue();
        });

        // Normal case: non-existing property
        result = ObjectConstructor.getOwnPropertyDescriptor(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("nonexistent")});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: insufficient arguments
        result = ObjectConstructor.getOwnPropertyDescriptor(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: non-object
        assertTypeError(ObjectConstructor.getOwnPropertyDescriptor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object"), new JSString("prop")}));
        assertPendingException(context);
    }

    @Test
    public void testGetOwnPropertyDescriptors() {
        assertIntegerWithJavet("""
                var obj = {a: 1, b: 2};
                var descs = Object.getOwnPropertyDescriptors(obj);
                descs.a.value + descs.b.value""");
    }

    @Test
    public void testGetOwnPropertyNames() {
        JSObject obj = new JSObject();
        obj.set("prop1", new JSString("value1"));
        obj.set("prop2", new JSString("value2"));

        // Normal case: object with properties
        JSValue result = ObjectConstructor.getOwnPropertyNames(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, names -> {
            assertThat(names.getLength()).isEqualTo(2);
            // Note: order may vary, so check both are present
            String name0 = names.get(0).asString().map(JSString::value).orElseThrow();
            String name1 = names.get(1).asString().map(JSString::value).orElseThrow();
            assertThat((name0.equals("prop1") && name1.equals("prop2")) || (name0.equals("prop2") && name1.equals("prop1"))).isTrue();
        });

        // Normal case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.getOwnPropertyNames(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, names -> assertThat(names.getLength()).isEqualTo(0));

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.getOwnPropertyNames(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.getOwnPropertyNames(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")}));
        assertPendingException(context);

        assertObjectWithJavet(
                "var obj = {first: 'John', last: 'Doe'}; Object.getOwnPropertyNames(obj)",
                "Object.getOwnPropertyNames([])",
                "Object.getOwnPropertyNames(['a','b'])",
                "Object.getOwnPropertyNames([null, undefined, 1])",
                "var a = [1,2]; a['x'] = 'x'; Object.getOwnPropertyNames(a)");

        assertErrorWithJavet(
                "Object.getOwnPropertyNames(undefined)",
                "Object.getOwnPropertyNames()");

        assertStringWithJavet(
                "JSON.stringify(Object.getOwnPropertyNames(Object).sort())");
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
        assertThat(result).isInstanceOfSatisfying(JSArray.class, symbols -> assertThat(symbols.getLength()).isEqualTo(2));

        // Normal case: object with no symbol properties
        JSObject regularObj = new JSObject();
        regularObj.set("prop", new JSString("value"));
        result = ObjectConstructor.getOwnPropertySymbols(context, JSUndefined.INSTANCE, new JSValue[]{regularObj});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, symbols -> assertThat(symbols.getLength()).isEqualTo(0));

        // Edge case: no arguments
        result = ObjectConstructor.getOwnPropertySymbols(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, symbols -> assertThat(symbols.getLength()).isEqualTo(0));

        // Edge case: non-object (should return empty array)
        result = ObjectConstructor.getOwnPropertySymbols(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, symbols -> assertThat(symbols.getLength()).isEqualTo(0));
    }

    @Test
    public void testGetPrototypeOf() {
        JSObject proto = new JSObject();
        JSObject obj = new JSObject();
        obj.setPrototype(proto);

        // Normal case
        JSValue result = ObjectConstructor.getPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result).isSameAs(proto);

        // Edge case: null prototype
        JSObject objWithNullProto = new JSObject();
        objWithNullProto.setPrototype(null);
        result = ObjectConstructor.getPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{objWithNullProto});
        assertThat(result.isNull()).isTrue();

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.getPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.getPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not object")}));
        assertPendingException(context);

        assertBooleanWithJavet(
                """
                        var proto = {x: 10}; var newObj = Object.create(proto);
                        Object.getPrototypeOf(newObj) === proto""",
                """
                        class A {};
                        var a = new A();
                        Object.getPrototypeOf(a) === A.prototype""",
                """
                        class A {};
                        var a = new A();
                        typeof Object.getPrototypeOf(a) === 'Function'""");
    }

    @Test
    public void testGroupBy() {
        JSArray items = new JSArray();
        items.push(new JSNumber(1));
        items.push(new JSNumber(2));
        items.push(new JSNumber(3));
        items.push(new JSNumber(4));

        // Callback function: group by even/odd
        JSFunction callback = new JSNativeFunction("testCallback", 3, (childContext, thisArg, args) -> {
            double num = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (num % 2 == 0) ? new JSString("even") : new JSString("odd");
        });

        // Normal case: group by even/odd
        JSValue result = ObjectConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{items, callback});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, groups -> {
            // Check even group
            JSValue evenGroup = groups.get("even");
            assertThat(evenGroup).isInstanceOfSatisfying(JSArray.class, evenArray -> {
                assertThat(evenArray.getLength()).isEqualTo(2);
                assertThat(evenArray.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
                assertThat(evenArray.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
            });

            // Check odd group
            JSValue oddGroup = groups.get("odd");
            assertThat(oddGroup).isInstanceOfSatisfying(JSArray.class, oddArray -> {
                assertThat(oddArray.getLength()).isEqualTo(2);
                assertThat(oddArray.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
                assertThat(oddArray.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
            });
        });

        // Edge case: empty array
        JSArray emptyItems = new JSArray();
        result = ObjectConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{emptyItems, callback});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, groups -> {
            // Should have no properties
            assertThat(groups.get("even").isUndefined()).isTrue();
            assertThat(groups.get("odd").isUndefined()).isTrue();
        });

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
        assertThat(result.isBooleanTrue()).isTrue();

        // Normal case: non-existing property
        result = ObjectConstructor.hasOwn(context, JSUndefined.INSTANCE, new JSValue[]{obj, new JSString("nonexistent")});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: insufficient arguments (should return false, not throw)
        result = ObjectConstructor.hasOwn(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result.isBooleanFalse()).isTrue();

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
        assertThat(result.isBooleanTrue()).isTrue();

        // Property doesn't exist
        result = ObjectConstructor.hasOwnProperty(context, obj, new JSValue[]{new JSString("z")});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: no arguments
        result = ObjectConstructor.hasOwnProperty(context, obj, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: called on non-object
        assertTypeError(ObjectConstructor.hasOwnProperty(context, new JSString("not object"), new JSValue[]{new JSString("a")}));
        assertPendingException(context);
    }

    @Test
    public void testIs() {
        assertBooleanWithJavet(
                "Object.is(42, 42)",
                "Object.is('hello', 'hello')",
                "Object.is(true, true)",
                "Object.is(null, null)",
                "Object.is(undefined, undefined)",
                "Object.is(1, 1)",
                "Object.is(1, 2)",
                "Object.is(NaN, NaN)",
                "Object.is(0, -0)",
                "Object.is(0, 0)",
                "var obj = {}; Object.is(obj, obj)",
                "Object.is({}, {})");
    }

    @Test
    public void testIsExtensible() {
        assertBooleanWithJavet(
                "var obj = {}; Object.isExtensible(obj)",
                "var obj2 = {}; Object.preventExtensions(obj2); Object.isExtensible(obj2)",
                "var obj3 = {}; Object.seal(obj3); Object.isExtensible(obj3)",
                "var obj4 = {}; Object.freeze(obj4); Object.isExtensible(obj4)");
    }

    @Test
    public void testIsFrozen() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case: not frozen
        JSValue result = ObjectConstructor.isFrozen(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result.isBooleanFalse()).isTrue();

        // After freezing
        obj.freeze();
        result = ObjectConstructor.isFrozen(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result.isBooleanTrue()).isTrue();

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
        assertThat(result.isBooleanFalse()).isTrue();

        // After sealing
        obj.seal();
        result = ObjectConstructor.isSealed(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result.isBooleanTrue()).isTrue();

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
        assertThat(result).isInstanceOfSatisfying(JSArray.class, keys -> assertThat(keys.getLength()).isEqualTo(3));

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.keys(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, keys -> assertThat(keys.getLength()).isEqualTo(0));

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.keys(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object
        assertTypeError(ObjectConstructor.keys(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE}));
        assertPendingException(context);

        assertStringWithJavet("var obj = {a: 1, b: 2, c: 3}; JSON.stringify(Object.keys(obj))");
    }

    @Test
    public void testPreventExtensions() {
        JSObject obj = new JSObject();

        // Normal case: prevent extensions on object
        JSValue result = ObjectConstructor.preventExtensions(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result).isSameAs(obj);

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.preventExtensions(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object (should return as-is)
        JSValue primitive = new JSString("string");
        result = ObjectConstructor.preventExtensions(context, JSUndefined.INSTANCE, new JSValue[]{primitive});
        assertThat(result).isSameAs(primitive);
    }

    @Test
    public void testSeal() {
        JSObject obj = new JSObject();
        obj.set("a", new JSNumber(1));

        // Normal case
        JSValue result = ObjectConstructor.seal(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        assertThat(result).isSameAs(obj);
        assertThat(obj.isSealed()).isTrue();

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.seal(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Test seal behavior with Javet comparison
        assertBooleanWithJavet(
                // Test seal prevents adding properties
                """
                        var obj = {a: 1}; Object.seal(obj);
                        obj.b = 2;
                        obj.b === undefined""",
                // Test seal prevents deleting properties
                """
                        var obj = {a: 1}; Object.seal(obj);
                        delete obj.a;
                        obj.a === 1""",
                // Test seal allows modifying properties (difference from freeze)
                """
                        var obj = {a: 1}; Object.seal(obj);
                        obj.a = 999;
                        obj.a === 999""",
                // Test configurable is false after seal
                """
                        var obj = {a: 1}; Object.seal(obj);
                        var desc = Object.getOwnPropertyDescriptor(obj, 'a');
                        desc.configurable === false""",
                // Test writable is still true after seal (difference from freeze)
                """
                        var obj = {a: 1}; Object.seal(obj);
                        var desc = Object.getOwnPropertyDescriptor(obj, 'a');
                        desc.writable === true""",
                // Test isSealed returns true after seal
                """
                        var obj = {a: 1}; Object.seal(obj);
                        Object.isSealed(obj)""",
                // Test isExtensible returns false after seal
                """
                        var obj = {a: 1}; Object.seal(obj);
                        !Object.isExtensible(obj)""",
                // Test isFrozen returns false after seal (seal is not freeze)
                """
                        var obj = {a: 1}; Object.seal(obj);
                        !Object.isFrozen(obj)""");
    }

    @Test
    public void testSetPrototypeOf() {
        JSObject obj = new JSObject();
        JSObject newProto = new JSObject();
        newProto.set("y", new JSNumber(200));

        // Normal case
        JSValue result = ObjectConstructor.setPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{obj, newProto});
        assertThat(result).isSameAs(obj);
        assertThat(obj.getPrototype()).isSameAs(newProto);

        // Edge case: set to null
        result = ObjectConstructor.setPrototypeOf(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSNull.INSTANCE});
        assertThat(result).isSameAs(obj);
        assertThat(obj.getPrototype()).isNull();

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
        assertThat(result).isInstanceOfSatisfying(JSArray.class, values -> assertThat(values.getLength()).isEqualTo(3));

        // Edge case: empty object
        JSObject emptyObj = new JSObject();
        result = ObjectConstructor.values(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, values -> assertThat(values.getLength()).isEqualTo(0));

        // Edge case: no arguments
        assertTypeError(ObjectConstructor.values(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        assertStringWithJavet("var obj = {a: 1, b: 2, c: 3}; JSON.stringify(Object.values(obj))");
    }
}
