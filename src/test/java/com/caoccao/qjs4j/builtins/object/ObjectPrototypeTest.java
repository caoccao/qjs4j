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

package com.caoccao.qjs4j.builtins.object;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ObjectPrototype methods.
 */
public class ObjectPrototypeTest extends BaseJavetTest {

    @Test
    public void testAssign() {
        JSObject target = new JSObject(context);
        target.set("a", new JSNumber(1));

        JSObject source = new JSObject(context);
        source.set("b", new JSNumber(2));
        source.set("c", new JSString("hello"));

        // Normal case: assign sources to target
        var assignMethod = builtin(ObjectPrototype::assign, JSUndefined.INSTANCE);
        JSValue result = assignMethod.call(target, source);
        assertThat(result).isEqualTo(target);
        assertNumberValue(target.get("a"), 1.0);
        assertNumberValue(target.get("b"), 2.0);
        assertStringValue(target.get("c"), "hello");

        // Normal case: multiple sources
        JSObject source2 = new JSObject(context);
        source2.set("d", JSBoolean.TRUE);
        result = assignMethod.call(target, source2);
        assertThat(result).isEqualTo(target);
        assertThat(target.get("d")).isEqualTo(JSBoolean.TRUE);

        // Normal case: skip null/undefined sources
        result = assignMethod.call(target, JSNull.INSTANCE, source);
        assertThat(result).isEqualTo(target);

        // Edge case: no arguments
        result = assignMethod.call();
        assertPendingTypeError(context, result);

        // Edge case: null target
        result = assignMethod.call(JSNull.INSTANCE);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testConstructor() {
        assertStringWithJavet("Object.constructor.name");
    }

    @Test
    public void testCreate() {
        // Normal case: create object with null prototype
        var createMethod = builtin(ObjectPrototype::create, JSUndefined.INSTANCE);
        JSValue result = createMethod.call(JSNull.INSTANCE);
        assertThat(result).isInstanceOfSatisfying(JSObject.class, obj -> assertThat(obj.getPrototype()).isNull());

        // Normal case: create object with object prototype
        JSObject proto = new JSObject(context);
        proto.set("testProp", new JSString("testValue"));
        result = createMethod.call(proto);
        assertThat(result).isInstanceOfSatisfying(JSObject.class, obj2 -> {
            assertThat(obj2.getPrototype()).isEqualTo(proto);
            // Should inherit property
            assertStringValue(obj2.get("testProp"), "testValue");
        });

        // Edge case: invalid prototype
        result = createMethod.call(stringArgs("not object"));
        assertPendingTypeError(context, result);

        // Edge case: no arguments
        result = createMethod.call();
        assertPendingTypeError(context, result);
    }

    @Test
    public void testDefineGetter() {
        JSObject obj = new JSObject(context);
        JSNativeFunction getter = new JSNativeFunction(context, "testGetter", 0,
                (ctx, thisArg, args) -> new JSString("getter value"));

        // Normal case: define getter
        var __defineGetter__ = builtin(ObjectPrototype::__defineGetter__, obj);
        JSValue result = __defineGetter__.call(new JSString("prop"), getter);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Verify getter was defined
        PropertyDescriptor desc = obj.getOwnPropertyDescriptor(PropertyKey.fromString("prop"));
        assertThat(desc).isNotNull();
        assertThat(desc.getGetter()).isEqualTo(getter);

        // Edge case: non-function getter
        result = __defineGetter__.call(stringArgs("badProp", "not a function"));
        assertPendingTypeError(context, result);

        // Edge case: null thisArg
        result = ObjectPrototype.__defineGetter__(context, JSNull.INSTANCE,
                new JSValue[]{new JSString("prop"), getter});
        assertPendingTypeError(context, result);

        // Edge case: insufficient arguments
        result = __defineGetter__.call(stringArgs("prop"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testDefineProperty() {
        JSObject obj = new JSObject(context);

        // Normal case: define data property
        JSObject descriptor = new JSObject(context);
        descriptor.set("value", new JSString("test"));
        descriptor.set("writable", JSBoolean.TRUE);
        descriptor.set("enumerable", JSBoolean.TRUE);
        descriptor.set("configurable", JSBoolean.TRUE);

        var defineProperty = builtin(ObjectPrototype::defineProperty, JSUndefined.INSTANCE);
        JSValue result = defineProperty.call(obj, new JSString("testProp"), descriptor);
        assertThat(result).isEqualTo(obj);
        assertStringValue(obj.get("testProp"), "test");

        // Edge case: not enough arguments
        result = defineProperty.call(obj, new JSString("prop"));
        assertPendingTypeError(context, result);

        // Edge case: first argument not object
        result = defineProperty.call(new JSString("not object"), new JSString("prop"), descriptor);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testDefineSetter() {
        JSObject obj = new JSObject(context);
        JSNativeFunction setter = new JSNativeFunction(context, "testSetter", 1,
                (ctx, thisArg, args) -> JSUndefined.INSTANCE);

        // Normal case: define setter
        var __defineSetter__ = builtin(ObjectPrototype::__defineSetter__, obj);
        JSValue result = __defineSetter__.call(new JSString("prop"), setter);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Verify setter was defined
        PropertyDescriptor desc = obj.getOwnPropertyDescriptor(PropertyKey.fromString("prop"));
        assertThat(desc).isNotNull();
        assertThat(desc.getSetter()).isEqualTo(setter);

        // Edge case: non-function setter
        result = __defineSetter__.call(new JSString("badProp"), new JSNumber(42));
        assertPendingTypeError(context, result);

        // Edge case: null thisArg
        result = ObjectPrototype.__defineSetter__(context, JSNull.INSTANCE,
                new JSValue[]{new JSString("prop"), setter});
        assertPendingTypeError(context, result);

        // Edge case: insufficient arguments
        result = __defineSetter__.call(stringArgs("prop"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testEntries() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSString("hello"));

        // Normal case: object with properties
        var entriesMethod = builtin(ObjectPrototype::entries, JSUndefined.INSTANCE);
        JSValue result = entriesMethod.call(obj);
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                entries -> assertThat(entries.getLength()).isGreaterThanOrEqualTo(2));

        // Normal case: empty object
        JSObject emptyObj = new JSObject(context);
        result = entriesMethod.call(emptyObj);
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                emptyEntries -> assertThat(emptyEntries.getLength()).isEqualTo(0));

        // Edge case: null
        result = entriesMethod.call(JSNull.INSTANCE);
        assertPendingTypeError(context, result);

        // Edge case: primitive
        result = entriesMethod.call(JSBoolean.FALSE);
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                primitiveEntries -> assertThat(primitiveEntries.getLength()).isEqualTo(0));
    }

    @Test
    public void testHasOwnProperty() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case: has property
        JSValue result = ObjectPrototype.hasOwnProperty(context, obj, stringArgs("a"));
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: doesn't have property
        result = ObjectPrototype.hasOwnProperty(context, obj, stringArgs("c"));
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: numeric property
        result = ObjectPrototype.hasOwnProperty(context, obj, numberArgs(1));
        assertThat(result).isEqualTo(JSBoolean.FALSE); // "1" != "a"

        // Edge case: no arguments
        result = ObjectPrototype.hasOwnProperty(context, obj, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-object primitive (should ToObject per spec)
        result = ObjectPrototype.hasOwnProperty(context, new JSString("string"), stringArgs("length"));
        assertThat(result).isEqualTo(JSBoolean.TRUE); // String wrapper has "length" own property

        assertBooleanWithJavet("var obj = {foo: 'bar'}; obj.hasOwnProperty('foo')",
                "var obj = {a: 1}; Object.getPrototypeOf(obj) === Object.prototype;", """
                        var outer = {
                            inner: {
                                value: 42
                            }
                        };
                        outer.hasOwnProperty('inner') && outer.inner.hasOwnProperty('value');
                        """, """
                        var obj = {};
                        Object.getPrototypeOf(obj) === Object.prototype;
                        """);
        assertStringWithJavet("""
                var obj = {x: 10};
                var results = {
                    hasOwnProperty: obj.hasOwnProperty('x'),
                    toString: typeof obj.toString === 'function',
                    valueOf: typeof obj.valueOf === 'function',
                    isPrototypeOf: typeof obj.isPrototypeOf === 'function'
                };
                JSON.stringify(results);
                """);
    }

    @Test
    public void testIsPrototypeOf() {
        JSObject proto = new JSObject(context);
        JSObject obj = new JSObject(context, proto);
        JSObject child = new JSObject(context, obj);

        // Normal case: direct prototype
        BuiltinAssertions isPrototypeOf = builtin(ObjectPrototype::isPrototypeOf, proto);
        isPrototypeOf.expectValue(JSBoolean.TRUE, obj);

        // Normal case: prototype chain
        isPrototypeOf.expectValue(JSBoolean.TRUE, child);

        // Normal case: same level (not in chain)
        JSValue result = ObjectPrototype.isPrototypeOf(context, obj, new JSValue[]{proto});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: no relationship
        JSObject unrelated = new JSObject(context);
        isPrototypeOf.expectValue(JSBoolean.FALSE, unrelated);

        // Edge case: primitive argument
        isPrototypeOf.expectValue(JSBoolean.FALSE, stringArgs("test"));

        // Edge case: null thisArg
        result = ObjectPrototype.isPrototypeOf(context, JSNull.INSTANCE, new JSValue[]{obj});
        assertPendingTypeError(context, result);
    }

    @Test
    public void testKeys() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));
        obj.set("c", new JSNumber(3));

        // Normal case: object with properties
        var keysMethod = builtin(ObjectPrototype::keys, JSUndefined.INSTANCE);
        JSValue result = keysMethod.call(obj);
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                keys -> assertThat(keys.getLength()).isGreaterThanOrEqualTo(3)); // May include prototype properties

        // Normal case: empty object
        JSObject emptyObj = new JSObject(context);
        result = keysMethod.call(emptyObj);
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                emptyKeys -> assertThat(emptyKeys.getLength()).isEqualTo(0));

        // Edge case: null
        result = keysMethod.call(JSNull.INSTANCE);
        assertPendingTypeError(context, result);

        // Edge case: undefined
        result = keysMethod.call(JSUndefined.INSTANCE);
        assertPendingTypeError(context, result);

        // Edge case: primitive
        result = keysMethod.call(stringArgs("string"));
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                primitiveKeys -> assertThat(primitiveKeys.getLength()).isEqualTo(0));
    }

    @Test
    public void testLookupGetter() {
        JSObject proto = new JSObject(context);
        JSNativeFunction protoGetter = new JSNativeFunction(context, "protoGetter", 0,
                (ctx, thisArg, args) -> new JSString("proto value"));
        ObjectPrototype.__defineGetter__(context, proto, new JSValue[]{new JSString("protoProp"), protoGetter});

        JSObject obj = new JSObject(context, proto);
        JSNativeFunction objGetter = new JSNativeFunction(context, "objGetter", 0,
                (ctx, thisArg, args) -> new JSString("obj value"));
        ObjectPrototype.__defineGetter__(context, obj, new JSValue[]{new JSString("objProp"), objGetter});

        // Normal case: lookup own getter
        var __lookupGetter__ = builtin(ObjectPrototype::__lookupGetter__, obj);
        JSValue result = __lookupGetter__.call(stringArgs("objProp"));
        assertThat(result).isEqualTo(objGetter);

        // Normal case: lookup getter from prototype
        result = __lookupGetter__.call(stringArgs("protoProp"));
        assertThat(result).isEqualTo(protoGetter);

        // Normal case: property without getter
        obj.set("dataProp", new JSString("data"));
        result = __lookupGetter__.call(stringArgs("dataProp"));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Normal case: non-existent property
        result = __lookupGetter__.call(stringArgs("nonExistent"));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: no arguments
        result = __lookupGetter__.call();
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: null thisArg
        result = ObjectPrototype.__lookupGetter__(context, JSNull.INSTANCE, stringArgs("prop"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testLookupSetter() {
        JSObject proto = new JSObject(context);
        JSNativeFunction protoSetter = new JSNativeFunction(context, "protoSetter", 1,
                (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        ObjectPrototype.__defineSetter__(context, proto, new JSValue[]{new JSString("protoProp"), protoSetter});

        JSObject obj = new JSObject(context, proto);
        JSNativeFunction objSetter = new JSNativeFunction(context, "objSetter", 1,
                (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        ObjectPrototype.__defineSetter__(context, obj, new JSValue[]{new JSString("objProp"), objSetter});

        // Normal case: lookup own setter
        var __lookupSetter__ = builtin(ObjectPrototype::__lookupSetter__, obj);
        JSValue result = __lookupSetter__.call(stringArgs("objProp"));
        assertThat(result).isEqualTo(objSetter);

        // Normal case: lookup setter from prototype
        result = __lookupSetter__.call(stringArgs("protoProp"));
        assertThat(result).isEqualTo(protoSetter);

        // Normal case: property without setter
        obj.set("dataProp", new JSString("data"));
        result = __lookupSetter__.call(stringArgs("dataProp"));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Normal case: non-existent property
        result = __lookupSetter__.call(stringArgs("nonExistent"));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: no arguments
        result = __lookupSetter__.call();
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: null thisArg
        result = ObjectPrototype.__lookupSetter__(context, JSNull.INSTANCE, stringArgs("prop"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testPropertyIsEnumerable() {
        JSObject obj = new JSObject(context);
        obj.set("enumProp", new JSString("value"));

        // Normal case: enumerable property
        var propertyIsEnumerable = builtin(ObjectPrototype::propertyIsEnumerable, obj);
        JSValue result = propertyIsEnumerable.call(stringArgs("enumProp"));
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: non-existent property
        result = propertyIsEnumerable.call(stringArgs("nonExistent"));
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: null thisArg
        result = ObjectPrototype.propertyIsEnumerable(context, JSNull.INSTANCE, stringArgs("test"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testProtoAccessor() {
        JSObject proto = new JSObject(context);
        proto.set("protoProperty", new JSString("from proto"));
        JSObject obj = new JSObject(context, proto);

        // Normal case: get __proto__
        JSValue result = ObjectPrototype.__proto__Getter(context, obj, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(proto);

        // Normal case: set __proto__ to another object
        JSObject newProto = new JSObject(context);
        newProto.set("newProperty", new JSString("from new proto"));
        var __proto__Setter = builtin(ObjectPrototype::__proto__Setter, obj);
        result = __proto__Setter.call(newProto);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        assertThat(obj.getPrototype()).isEqualTo(newProto);

        // Normal case: set __proto__ to null
        result = __proto__Setter.call(JSNull.INSTANCE);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        assertThat(obj.getPrototype()).isNull();

        // Edge case: get __proto__ on non-object primitive (should ToObject per spec)
        result = ObjectPrototype.__proto__Getter(context, new JSString("test"), JSValue.NO_ARGS);
        assertThat(result).isInstanceOf(JSObject.class); // Returns String.prototype

        // Edge case: set __proto__ on non-object
        result = ObjectPrototype.__proto__Setter(context, new JSString("test"), new JSValue[]{proto});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: set __proto__ to primitive (should be ignored)
        JSObject testObj = new JSObject(context, proto);
        result = ObjectPrototype.__proto__Setter(context, testObj, stringArgs("not an object"));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        assertThat(testObj.getPrototype()).isEqualTo(proto); // Should remain unchanged
    }

    @Test
    public void testPrototype() {
        assertObjectWithJavet("Object.getOwnPropertyNames(Object.prototype).sort()");
    }

    @Test
    public void testRegistrationDescriptors() {
        assertStringWithJavet(
                "(() => { const d = Object.getOwnPropertyDescriptor(Object.prototype, 'hasOwnProperty'); return [d.enumerable, d.writable, d.configurable, typeof d.value].join(','); })()",
                "(() => { const d = Object.getOwnPropertyDescriptor(Object.prototype, '__proto__'); return [d.enumerable, d.configurable, typeof d.get, typeof d.set].join(','); })()",
                "(() => { const d = Object.getOwnPropertyDescriptor(Object, 'keys'); return [d.enumerable, d.writable, d.configurable, typeof d.value].join(','); })()",
                "(() => { const d = Object.getOwnPropertyDescriptor(Object, 'prototype'); return [d.writable, d.enumerable, d.configurable].join(','); })()",
                "(() => { const d = Object.getOwnPropertyDescriptor(Object.prototype, 'constructor'); return [d.writable, d.enumerable, d.configurable].join(','); })()");
    }

    @Test
    public void testSet() {
        assertStringWithJavet("""
                const obj = {};
                obj.a = 1;
                JSON.stringify(obj);""");
    }

    @Test
    public void testToLocaleString() {
        JSObject obj = context.createJSObject();

        // Normal case: object without custom toString
        JSValue result = ObjectPrototype.toLocaleString(context, obj, JSValue.NO_ARGS);
        assertStringValue(result, "[object Object]");

        // Edge case: null thisArg
        result = ObjectPrototype.toLocaleString(context, JSNull.INSTANCE, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testToString() {
        // Normal case: undefined
        JSValue result = ObjectPrototype.toString(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);
        assertStringValue(result, "[object Undefined]");

        // Normal case: null
        result = ObjectPrototype.toString(context, JSNull.INSTANCE, JSValue.NO_ARGS);
        assertStringValue(result, "[object Null]");

        // Normal case: object
        JSObject obj = new JSObject(context);
        result = ObjectPrototype.toString(context, obj, JSValue.NO_ARGS);
        assertStringValue(result, "[object Object]");

        // Normal case: array
        JSArray arr = new JSArray(context);
        result = ObjectPrototype.toString(context, arr, JSValue.NO_ARGS);
        assertStringValue(result, "[object Array]");

        // Normal case: function
        JSFunction func = new JSNativeFunction(context, "test", 0,
                (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        result = ObjectPrototype.toString(context, func, JSValue.NO_ARGS);
        assertStringValue(result, "[object Function]");

        // Normal case: string
        result = ObjectPrototype.toString(context, new JSString("test"), JSValue.NO_ARGS);
        assertStringValue(result, "[object String]");

        // Normal case: number
        result = ObjectPrototype.toString(context, new JSNumber(42), JSValue.NO_ARGS);
        assertStringValue(result, "[object Number]");

        // Normal case: boolean
        result = ObjectPrototype.toString(context, JSBoolean.TRUE, JSValue.NO_ARGS);
        assertStringValue(result, "[object Boolean]");
    }

    @Test
    public void testValueOf() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));

        // Normal case: object
        JSValue result = ObjectPrototype.valueOf(context, obj, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(obj);

        // Normal case: primitive (should ToObject - return a wrapper)
        JSString str = new JSString("hello");
        result = ObjectPrototype.valueOf(context, str, JSValue.NO_ARGS);
        assertThat(result).isInstanceOf(JSObject.class);

        // Normal case: null (should throw TypeError per spec)
        assertPendingTypeError(context, ObjectPrototype.valueOf(context, JSNull.INSTANCE, JSValue.NO_ARGS));
    }

    @Test
    public void testValues() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSString("hello"));
        obj.set("c", JSBoolean.TRUE);

        // Normal case: object with properties
        var valuesMethod = builtin(ObjectPrototype::values, JSUndefined.INSTANCE);
        JSValue result = valuesMethod.call(obj);
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                values -> assertThat(values.getLength()).isGreaterThanOrEqualTo(3));

        // Normal case: empty object
        JSObject emptyObj = new JSObject(context);
        result = valuesMethod.call(emptyObj);
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                emptyValues -> assertThat(emptyValues.getLength()).isEqualTo(0));

        // Edge case: null
        result = valuesMethod.call(JSNull.INSTANCE);
        assertPendingTypeError(context, result);

        // Edge case: primitive
        result = valuesMethod.call(numberArgs(42));
        assertThat(result).isInstanceOfSatisfying(JSArray.class,
                primitiveValues -> assertThat(primitiveValues.getLength()).isEqualTo(0));
    }
}
