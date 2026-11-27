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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JSObject#freeze()} and {@link JSObject#seal()} do what {@code Object.freeze} and {@code Object.seal} do.
 * <p>
 * They used to set flags and leave every descriptor writable and configurable, so an object frozen from Java refused
 * assignment yet accepted {@code Object.defineProperty}, and {@code Object.isFrozen} said it was not frozen. The Java
 * cases assert what V8 reports for an object frozen with {@code Object.freeze}; the script cases, which now share the
 * Java implementation, are compared against V8 directly.
 */
public class JSObjectIntegrityLevelTest extends BaseJavetTest {

    private JSObject createObject() {
        JSObject object = context.createJSObject();
        object.set(PropertyKey.fromString("x"), JSNumber.of(1));
        return object;
    }

    private String evalWith(JSObject object, String code) {
        context.getGlobalObject().set(PropertyKey.fromString("o"), object);
        return context.eval(code).toString();
    }

    @Test
    public void testFailuresMatchV8() {
        assertErrorWithJavet("Object.freeze(new Uint8Array(1))", "Object.seal(new Uint8Array(1))",
                "Object.freeze(new Proxy({}, { preventExtensions() { return false } }))",
                "Object.seal(new Proxy({}, { preventExtensions() { return false } }))",
                "Object.freeze(new Proxy({ a: 1 }, { defineProperty() { return false } }))",
                "Object.seal(new Proxy({ a: 1 }, { defineProperty() { return false } }))",
                "Object.freeze(new Proxy({ a: 1 }, { ownKeys() { throw new Error('keys') } }))",
                "Object.freeze(new Proxy({ a: 1 }, { getOwnPropertyDescriptor() { throw new Error('gopd') } }))",
                "Object.freeze(new Proxy({ a: 1 }, { preventExtensions() { throw new Error('pe') } }))",
                "Object.freeze(new Proxy({ a: 1 }, { defineProperty() { throw new Error('dp') } }))",
                "Object.freeze(new Uint8Array(new ArrayBuffer(1, { maxByteLength: 2 })))",
                "Object.seal(new Uint8Array(new ArrayBuffer(1, { maxByteLength: 2 })))",
                "Object.freeze(new Uint8Array(new ArrayBuffer(0, { maxByteLength: 2 }), 0, 0))",
                "Object.freeze(new Proxy({}, { preventExtensions() { return true } }))",
                "Object.seal(new Proxy({ a: 1 }, { defineProperty() { throw new Error('seal dp') } }))",
                "Object.seal(new Proxy({ a: 1 }, { ownKeys() { throw new Error('seal keys') } }))",
                """
                        Object.freeze(new Proxy({}, { ownKeys: () => ['ghost'], getOwnPropertyDescriptor: () => undefined }))""");
        assertStringWithJavet(
                "var t = new Uint8Array(1); try { Object.freeze(t) } catch (e) {} String(Object.isExtensible(t))",
                "String(Object.isFrozen(Object.freeze(new Uint8Array(new SharedArrayBuffer(0, { maxByteLength: 2 }), 0, 0))))",
                "String(Object.isFrozen(Object.freeze(new Uint8Array(0))))");
    }

    @Test
    public void testFreezeFromJava() {
        JSObject object = createObject();
        assertThat(object.freeze()).isTrue();
        assertThat(object.isFrozen()).isTrue();
        assertThat(object.isSealed()).isTrue();
        assertThat(object.isExtensible()).isFalse();
        assertThat(evalWith(object, "Object.isFrozen(o) + ':' + Object.isSealed(o) + ':' + Object.isExtensible(o)"))
                .isEqualTo("true:true:false");
        assertThat(evalWith(object, "JSON.stringify(Object.getOwnPropertyDescriptor(o, 'x'))"))
                .isEqualTo("{\"value\":1,\"writable\":false,\"enumerable\":true,\"configurable\":false}");
        assertThat(
                evalWith(object, "try { Object.defineProperty(o, 'x', { value: 2 }); 'no' } catch (e) { e.message }"))
                .isEqualTo("Cannot redefine property: x");
        assertThat(evalWith(object, "'use strict'; try { o.x = 5; 'no' } catch (e) { e.message }"))
                .isEqualTo("Cannot assign to read only property 'x' of object '#<Object>'");
        assertThat(evalWith(object, "delete o.x; o.y = 1; o.x + ':' + o.y")).isEqualTo("1:undefined");
    }

    @Test
    public void testFreezeFromJavaOnArraysAccessorsAndProxies() {
        JSArray array = context.createJSArray(JSNumber.of(1), JSNumber.of(2));
        assertThat(array.freeze()).isTrue();
        assertThat(evalWith(array, """
                [
                  Object.isFrozen(o),
                  Object.getOwnPropertyDescriptor(o, 0).writable,
                  Object.getOwnPropertyDescriptor(o, 'length').writable,
                  Reflect.set(o, 0, 9),
                ].join(':')""")).isEqualTo("true:false:false:false");

        JSObject withAccessor = (JSObject) context.eval("({ get a() { return 1 }, set a(v) {} })");
        assertThat(withAccessor.freeze()).isTrue();
        assertThat(evalWith(withAccessor, """
                var d = Object.getOwnPropertyDescriptor(o, 'a');
                Object.isFrozen(o) + ':' + ('writable' in d) + ':' + d.configurable""")).isEqualTo("true:false:false");

        JSObject proxy = (JSObject) context.eval("""
                var log = [];
                var handler = {
                  ownKeys(t) {
                    log.push('ownKeys');
                    return Reflect.ownKeys(t);
                  },
                  getOwnPropertyDescriptor(t, k) {
                    log.push('gopd ' + String(k));
                    return Reflect.getOwnPropertyDescriptor(t, k);
                  },
                  defineProperty(t, k, d) {
                    log.push('define ' + String(k) + ' ' + JSON.stringify(d));
                    return Reflect.defineProperty(t, k, d);
                  },
                  preventExtensions(t) {
                    log.push('preventExtensions');
                    return Reflect.preventExtensions(t);
                  },
                };
                new Proxy({ a: 1 }, handler)""");
        assertThat(proxy.freeze()).isTrue();
        assertThat(context.eval("log.join('|')").toString())
                .isEqualTo("preventExtensions|ownKeys|gopd a|define a {\"writable\":false,\"configurable\":false}");

        JSObject refusing = (JSObject) context.eval("new Proxy({}, { preventExtensions() { return false } })");
        assertThat(refusing.freeze()).isFalse();
        assertThat(context.getPendingException()).isNotNull();
        assertThat(context.getPendingException().toString())
                .isEqualTo("TypeError: 'preventExtensions' on proxy: trap returned falsish");
        context.clearPendingException();
    }

    @Test
    public void testSealFromJava() {
        JSObject object = createObject();
        assertThat(object.seal()).isTrue();
        assertThat(object.isSealed()).isTrue();
        assertThat(object.isFrozen()).isFalse();
        assertThat(evalWith(object, "Object.isSealed(o) + ':' + Object.isFrozen(o) + ':' + Object.isExtensible(o)"))
                .isEqualTo("true:false:false");
        assertThat(evalWith(object, "JSON.stringify(Object.getOwnPropertyDescriptor(o, 'x'))"))
                .isEqualTo("{\"value\":1,\"writable\":true,\"enumerable\":true,\"configurable\":false}");
        assertThat(evalWith(object, "o.x = 3; delete o.x; o.y = 1; o.x + ':' + o.y")).isEqualTo("3:undefined");
        assertThat(
                evalWith(object, "try { Object.defineProperty(o, 'x', { get() {} }); 'no' } catch (e) { e.message }"))
                .isEqualTo("Cannot redefine property: x");
    }

    @Test
    public void testTrapOrderMatchesV8() {
        // Sealing defines { configurable: false } without reading the descriptor first; freezing reads it.
        assertStringWithJavet("""
                var log = [];
                var handler = {
                  ownKeys(t) {
                    log.push('ownKeys');
                    return Reflect.ownKeys(t);
                  },
                  getOwnPropertyDescriptor(t, k) {
                    log.push('gopd ' + String(k));
                    return Reflect.getOwnPropertyDescriptor(t, k);
                  },
                  defineProperty(t, k, d) {
                    log.push('define ' + String(k) + ' ' + JSON.stringify(d));
                    return Reflect.defineProperty(t, k, d);
                  },
                  preventExtensions(t) {
                    log.push('preventExtensions');
                    return Reflect.preventExtensions(t);
                  },
                };
                Object.seal(new Proxy({ a: 1 }, handler)); log.join('|')""", """
                var log = [];
                var handler = {
                  ownKeys(t) {
                    log.push('ownKeys');
                    return Reflect.ownKeys(t);
                  },
                  getOwnPropertyDescriptor(t, k) {
                    log.push('gopd ' + String(k));
                    return Reflect.getOwnPropertyDescriptor(t, k);
                  },
                  defineProperty(t, k, d) {
                    log.push('define ' + String(k) + ' ' + JSON.stringify(d));
                    return Reflect.defineProperty(t, k, d);
                  },
                  preventExtensions(t) {
                    log.push('preventExtensions');
                    return Reflect.preventExtensions(t);
                  },
                };
                Object.freeze(new Proxy({ a: 1, get b() { return 1 } }, handler)); log.join('|')""", """
                var log = [];
                var handler = {
                  ownKeys(t) {
                    log.push('ownKeys');
                    return Reflect.ownKeys(t);
                  },
                  getOwnPropertyDescriptor(t, k) {
                    log.push('gopd ' + String(k));
                    return Reflect.getOwnPropertyDescriptor(t, k);
                  },
                  defineProperty(t, k, d) {
                    log.push('define ' + String(k) + ' ' + JSON.stringify(d));
                    return Reflect.defineProperty(t, k, d);
                  },
                  preventExtensions(t) {
                    log.push('preventExtensions');
                    return Reflect.preventExtensions(t);
                  },
                };
                Object.isFrozen(Object.freeze(new Proxy([1], handler))) + log.join('|')""");
    }
}
