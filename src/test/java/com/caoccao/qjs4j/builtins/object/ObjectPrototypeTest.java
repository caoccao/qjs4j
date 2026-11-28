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
        var assignMethod = builtin(ObjectPrototype::assign);
        assertThat(assignMethod.call(target, source)).isEqualTo(target);
        assertNumberValue(target.get("a"), 1.0);
        assertNumberValue(target.get("b"), 2.0);
        assertStringValue(target.get("c"), "hello");

        // Normal case: multiple sources
        JSObject source2 = new JSObject(context);
        source2.set("d", JSBoolean.TRUE);
        assertThat(assignMethod.call(target, source2)).isEqualTo(target);
        assertThat(target.get("d")).isEqualTo(JSBoolean.TRUE);

        // Normal case: skip null/undefined sources
        assertThat(assignMethod.call(target, JSNull.INSTANCE, source)).isEqualTo(target);

        // Edge case: no arguments
        assertPendingTypeError(assignMethod.call());

        // Edge case: null target
        assertPendingTypeError(assignMethod.call(JSNull.INSTANCE));
    }

    @Test
    public void testConstructor() {
        assertStringWithJavet("Object.constructor.name");
    }

    @Test
    public void testCreate() {
        // Normal case: create object with null prototype
        var createMethod = builtin(ObjectPrototype::create);
        assertThat(createMethod.call(JSNull.INSTANCE)).isInstanceOfSatisfying(JSObject.class,
                obj -> assertThat(obj.getPrototype()).isNull());

        // Normal case: create object with object prototype
        JSObject proto = new JSObject(context);
        proto.set("testProp", new JSString("testValue"));
        assertThat(createMethod.call(proto)).isInstanceOfSatisfying(JSObject.class, obj2 -> {
            assertThat(obj2.getPrototype()).isEqualTo(proto);
            // Should inherit property
            assertStringValue(obj2.get("testProp"), "testValue");
        });

        // Edge case: invalid prototype
        assertPendingTypeError(createMethod.call("not object"));

        // Edge case: no arguments
        assertPendingTypeError(createMethod.call());
    }

    @Test
    public void testDefineGetter() {
        JSObject obj = new JSObject(context);
        JSNativeFunction getter = new JSNativeFunction(context, "testGetter", 0,
                (ctx, thisArg, args) -> new JSString("getter value"));

        // Normal case: define getter
        var __defineGetter__ = builtin(ObjectPrototype::__defineGetter__, obj);
        assertThat(__defineGetter__.call("prop", getter)).isEqualTo(JSUndefined.INSTANCE);

        // Verify getter was defined
        PropertyDescriptor desc = obj.getOwnPropertyDescriptor(PropertyKey.fromString("prop"));
        assertThat(desc).isNotNull();
        assertThat(desc.getGetter()).isEqualTo(getter);

        // Edge case: non-function getter
        assertPendingTypeError(__defineGetter__.call("badProp", "not a function"));

        // Edge case: null thisArg
        assertPendingTypeError(callBuiltin(ObjectPrototype::__defineGetter__, JSNull.INSTANCE, "prop", getter));

        // Edge case: insufficient arguments
        assertPendingTypeError(__defineGetter__.call("prop"));
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

        var defineProperty = builtin(ObjectPrototype::defineProperty);
        assertThat(defineProperty.call(obj, "testProp", descriptor)).isEqualTo(obj);
        assertStringValue(obj.get("testProp"), "test");

        // Edge case: not enough arguments
        assertPendingTypeError(defineProperty.call(obj, "prop"));

        // Edge case: first argument not object
        assertPendingTypeError(defineProperty.call("not object", "prop", descriptor));
    }

    @Test
    public void testDefineSetter() {
        JSObject obj = new JSObject(context);
        JSNativeFunction setter = new JSNativeFunction(context, "testSetter", 1,
                (ctx, thisArg, args) -> JSUndefined.INSTANCE);

        // Normal case: define setter
        var __defineSetter__ = builtin(ObjectPrototype::__defineSetter__, obj);
        assertThat(__defineSetter__.call("prop", setter)).isEqualTo(JSUndefined.INSTANCE);

        // Verify setter was defined
        PropertyDescriptor desc = obj.getOwnPropertyDescriptor(PropertyKey.fromString("prop"));
        assertThat(desc).isNotNull();
        assertThat(desc.getSetter()).isEqualTo(setter);

        // Edge case: non-function setter
        assertPendingTypeError(__defineSetter__.call("badProp", 42));

        // Edge case: null thisArg
        assertPendingTypeError(callBuiltin(ObjectPrototype::__defineSetter__, JSNull.INSTANCE, "prop", setter));

        // Edge case: insufficient arguments
        assertPendingTypeError(__defineSetter__.call("prop"));
    }

    @Test
    public void testEntries() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSString("hello"));

        // Normal case: object with properties
        var entriesMethod = builtin(ObjectPrototype::entries);
        assertThat(entriesMethod.call(obj)).isInstanceOfSatisfying(JSArray.class,
                entries -> assertThat(entries.getLength()).isGreaterThanOrEqualTo(2));

        // Normal case: empty object
        JSObject emptyObj = new JSObject(context);
        assertThat(entriesMethod.call(emptyObj)).isInstanceOfSatisfying(JSArray.class,
                emptyEntries -> assertThat(emptyEntries.getLength()).isEqualTo(0));

        // Edge case: null
        assertPendingTypeError(entriesMethod.call(JSNull.INSTANCE));

        // Edge case: primitive
        assertThat(entriesMethod.call(JSBoolean.FALSE)).isInstanceOfSatisfying(JSArray.class,
                primitiveEntries -> assertThat(primitiveEntries.getLength()).isEqualTo(0));
    }

    @Test
    public void testHasOwnProperty() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));

        // Normal case: has property
        var hasOwnPropertyMethod = nativeMethod(ObjectPrototype::hasOwnProperty);
        assertThat(hasOwnPropertyMethod.call(obj, "a")).isEqualTo(JSBoolean.TRUE);

        // Normal case: doesn't have property
        assertThat(hasOwnPropertyMethod.call(obj, "c")).isEqualTo(JSBoolean.FALSE);

        // Normal case: numeric property
        assertThat(hasOwnPropertyMethod.call(obj, 1)).isEqualTo(JSBoolean.FALSE); // "1" != "a"

        // Edge case: no arguments
        assertThat(hasOwnPropertyMethod.call(obj)).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-object primitive (should ToObject per spec)
        assertThat(hasOwnPropertyMethod.call("string", "length")).isEqualTo(JSBoolean.TRUE); // String wrapper has
                                                                                             // "length" own property

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
        var isPrototypeOfMethod = nativeMethod(ObjectPrototype::isPrototypeOf);
        assertThat(isPrototypeOfMethod.call(obj, proto)).isEqualTo(JSBoolean.FALSE);

        // Normal case: no relationship
        JSObject unrelated = new JSObject(context);
        isPrototypeOf.expectValue(JSBoolean.FALSE, unrelated);

        // Edge case: primitive argument
        isPrototypeOf.expectValue(JSBoolean.FALSE, "test");

        // Edge case: null thisArg
        assertPendingTypeError(isPrototypeOfMethod.call(JSNull.INSTANCE, obj));
    }

    @Test
    public void testKeys() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSNumber(2));
        obj.set("c", new JSNumber(3));

        // Normal case: object with properties
        var keysMethod = builtin(ObjectPrototype::keys);
        assertThat(keysMethod.call(obj)).isInstanceOfSatisfying(JSArray.class,
                keys -> assertThat(keys.getLength()).isGreaterThanOrEqualTo(3)); // May include prototype properties

        // Normal case: empty object
        JSObject emptyObj = new JSObject(context);
        assertThat(keysMethod.call(emptyObj)).isInstanceOfSatisfying(JSArray.class,
                emptyKeys -> assertThat(emptyKeys.getLength()).isEqualTo(0));

        // Edge case: null
        assertPendingTypeError(keysMethod.call(JSNull.INSTANCE));

        // Edge case: undefined
        assertPendingTypeError(keysMethod.call(JSUndefined.INSTANCE));

        // Edge case: primitive
        assertThat(keysMethod.call("string")).isInstanceOfSatisfying(JSArray.class,
                primitiveKeys -> assertThat(primitiveKeys.getLength()).isEqualTo(0));
    }

    @Test
    public void testLookupGetter() {
        JSObject proto = new JSObject(context);
        JSNativeFunction protoGetter = new JSNativeFunction(context, "protoGetter", 0,
                (ctx, thisArg, args) -> new JSString("proto value"));
        var __defineGetter__ = nativeMethod(ObjectPrototype::__defineGetter__);
        __defineGetter__.call(proto, "protoProp", protoGetter);

        JSObject obj = new JSObject(context, proto);
        JSNativeFunction objGetter = new JSNativeFunction(context, "objGetter", 0,
                (ctx, thisArg, args) -> new JSString("obj value"));
        __defineGetter__.call(obj, "objProp", objGetter);

        // Normal case: lookup own getter
        var __lookupGetter__ = builtin(ObjectPrototype::__lookupGetter__, obj);
        assertThat(__lookupGetter__.call("objProp")).isEqualTo(objGetter);

        // Normal case: lookup getter from prototype
        assertThat(__lookupGetter__.call("protoProp")).isEqualTo(protoGetter);

        // Normal case: property without getter
        obj.set("dataProp", new JSString("data"));
        assertThat(__lookupGetter__.call("dataProp")).isEqualTo(JSUndefined.INSTANCE);

        // Normal case: non-existent property
        assertThat(__lookupGetter__.call("nonExistent")).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: no arguments
        assertThat(__lookupGetter__.call()).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: null thisArg
        assertPendingTypeError(callBuiltin(ObjectPrototype::__lookupGetter__, JSNull.INSTANCE, "prop"));
    }

    @Test
    public void testLookupSetter() {
        JSObject proto = new JSObject(context);
        JSNativeFunction protoSetter = new JSNativeFunction(context, "protoSetter", 1,
                (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        var __defineSetter__ = nativeMethod(ObjectPrototype::__defineSetter__);
        __defineSetter__.call(proto, "protoProp", protoSetter);

        JSObject obj = new JSObject(context, proto);
        JSNativeFunction objSetter = new JSNativeFunction(context, "objSetter", 1,
                (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        __defineSetter__.call(obj, "objProp", objSetter);

        // Normal case: lookup own setter
        var __lookupSetter__ = builtin(ObjectPrototype::__lookupSetter__, obj);
        assertThat(__lookupSetter__.call("objProp")).isEqualTo(objSetter);

        // Normal case: lookup setter from prototype
        assertThat(__lookupSetter__.call("protoProp")).isEqualTo(protoSetter);

        // Normal case: property without setter
        obj.set("dataProp", new JSString("data"));
        assertThat(__lookupSetter__.call("dataProp")).isEqualTo(JSUndefined.INSTANCE);

        // Normal case: non-existent property
        assertThat(__lookupSetter__.call("nonExistent")).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: no arguments
        assertThat(__lookupSetter__.call()).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: null thisArg
        assertPendingTypeError(callBuiltin(ObjectPrototype::__lookupSetter__, JSNull.INSTANCE, "prop"));
    }

    @Test
    public void testPropertyIsEnumerable() {
        JSObject obj = new JSObject(context);
        obj.set("enumProp", new JSString("value"));

        // Normal case: enumerable property
        var propertyIsEnumerable = builtin(ObjectPrototype::propertyIsEnumerable, obj);
        assertThat(propertyIsEnumerable.call("enumProp")).isEqualTo(JSBoolean.TRUE);

        // Normal case: non-existent property
        assertThat(propertyIsEnumerable.call("nonExistent")).isEqualTo(JSBoolean.FALSE);

        // Edge case: null thisArg
        assertPendingTypeError(callBuiltin(ObjectPrototype::propertyIsEnumerable, JSNull.INSTANCE, "test"));
    }

    @Test
    public void testProtoAccessor() {
        JSObject proto = new JSObject(context);
        proto.set("protoProperty", new JSString("from proto"));
        JSObject obj = new JSObject(context, proto);

        // Normal case: get __proto__
        var __proto__Getter = nativeMethod(ObjectPrototype::__proto__Getter);
        assertThat(__proto__Getter.call(obj)).isEqualTo(proto);

        // Normal case: set __proto__ to another object
        JSObject newProto = new JSObject(context);
        newProto.set("newProperty", new JSString("from new proto"));
        var __proto__Setter = builtin(ObjectPrototype::__proto__Setter, obj);
        assertThat(__proto__Setter.call(newProto)).isEqualTo(JSUndefined.INSTANCE);
        assertThat(obj.getPrototype()).isEqualTo(newProto);

        // Normal case: set __proto__ to null
        assertThat(__proto__Setter.call(JSNull.INSTANCE)).isEqualTo(JSUndefined.INSTANCE);
        assertThat(obj.getPrototype()).isNull();

        // Edge case: get __proto__ on non-object primitive (should ToObject per spec)
        assertThat(__proto__Getter.call("test")).isInstanceOf(JSObject.class); // Returns String.prototype

        // Edge case: set __proto__ on non-object
        var __proto__SetterMethod = nativeMethod(ObjectPrototype::__proto__Setter);
        assertThat(__proto__SetterMethod.call("test", proto)).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: set __proto__ to primitive (should be ignored)
        JSObject testObj = new JSObject(context, proto);
        assertThat(__proto__SetterMethod.call(testObj, "not an object")).isEqualTo(JSUndefined.INSTANCE);
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
        var toLocaleString = nativeMethod(ObjectPrototype::toLocaleString);
        assertStringValue(toLocaleString.call(obj), "[object Object]");

        // Edge case: null thisArg
        assertPendingTypeError(toLocaleString.call(JSNull.INSTANCE));
    }

    @Test
    public void testToString() {
        // Normal case: undefined
        var toString = nativeMethod(ObjectPrototype::toString);
        assertStringValue(toString.call(JSUndefined.INSTANCE), "[object Undefined]");

        // Normal case: null
        assertStringValue(toString.call(JSNull.INSTANCE), "[object Null]");

        // Normal case: object
        JSObject obj = new JSObject(context);
        assertStringValue(toString.call(obj), "[object Object]");

        // Normal case: array
        JSArray arr = new JSArray(context);
        assertStringValue(toString.call(arr), "[object Array]");

        // Normal case: function
        JSFunction func = new JSNativeFunction(context, "test", 0,
                (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        assertStringValue(toString.call(func), "[object Function]");

        // Normal case: string
        assertStringValue(toString.call("test"), "[object String]");

        // Normal case: number
        assertStringValue(toString.call(42), "[object Number]");

        // Normal case: boolean
        assertStringValue(toString.call(JSBoolean.TRUE), "[object Boolean]");
    }

    @Test
    public void testValueOf() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));

        // Normal case: object
        var valueOf = nativeMethod(ObjectPrototype::valueOf);
        assertThat(valueOf.call(obj)).isEqualTo(obj);

        // Normal case: primitive (should ToObject - return a wrapper)
        JSString str = new JSString("hello");
        assertThat(valueOf.call(str)).isInstanceOf(JSObject.class);

        // Normal case: null (should throw TypeError per spec)
        assertPendingTypeError(valueOf.call(JSNull.INSTANCE));
    }

    @Test
    public void testValues() {
        JSObject obj = new JSObject(context);
        obj.set("a", new JSNumber(1));
        obj.set("b", new JSString("hello"));
        obj.set("c", JSBoolean.TRUE);

        // Normal case: object with properties
        var valuesMethod = builtin(ObjectPrototype::values);
        assertThat(valuesMethod.call(obj)).isInstanceOfSatisfying(JSArray.class,
                values -> assertThat(values.getLength()).isGreaterThanOrEqualTo(3));

        // Normal case: empty object
        JSObject emptyObj = new JSObject(context);
        assertThat(valuesMethod.call(emptyObj)).isInstanceOfSatisfying(JSArray.class,
                emptyValues -> assertThat(emptyValues.getLength()).isEqualTo(0));

        // Edge case: null
        assertPendingTypeError(valuesMethod.call(JSNull.INSTANCE));

        // Edge case: primitive
        assertThat(valuesMethod.call(42)).isInstanceOfSatisfying(JSArray.class,
                primitiveValues -> assertThat(primitiveValues.getLength()).isEqualTo(0));
    }
}
