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

/**
 * Unit tests for Reflect object methods.
 */
public class JSReflectObjectTest extends BaseJavetTest {

    @Test
    public void testReflectApply() {
        assertIntegerWithJavet("Reflect.apply(Math.max, undefined, [1, 9, 3])",
                "Reflect.apply(function(a, b) { return this.k + a + b; }, { k: 1 }, { 0: 2, 1: 3, length: 2 })",
                inArrowFunction("""
                        const target = function(a, b) { return a + b; };
                        const proxy = new Proxy(target, {
                          apply(t, thisArg, args) {
                            return Reflect.apply(t, thisArg, args) * 2;
                          }
                        });
                        return Reflect.apply(proxy, null, [2, 4]);
                        """));
    }

    @Test
    public void testReflectApplyErrors() {
        assertBooleanWithJavet(inArrowFunction("""
                try {
                  Reflect.apply(1, null, []);
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """), inArrowFunction("""
                try {
                  Reflect.apply(() => 1, null, undefined);
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """), inArrowFunction("""
                try {
                  Reflect.apply(() => 1, null, 1);
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """));
    }

    @Test
    public void testReflectConstruct() {
        assertIntegerWithJavet("Reflect.construct(function A(x) { this.x = x; }, [3]).x", inArrowFunction("""
                function A(a, b) { this.v = a + b; }
                const B = A.bind(null, 4);
                return Reflect.construct(B, [5]).v;
                """));

        assertStringWithJavet(inArrowFunction("""
                function A(x) { this.x = x; }
                function B() {}
                B.prototype = { p: 1 };
                const o = Reflect.construct(A, { 0: 5, length: 1 }, B);
                return JSON.stringify([o.x, Object.getPrototypeOf(o) === B.prototype, o.p]);
                """), inArrowFunction("""
                const target = function(x) { this.x = x; };
                const proxy = new Proxy(target, {
                  construct(t, args, newTarget) {
                    return { x: args[0], hasNewTarget: typeof newTarget === "function" };
                  }
                });
                const result = Reflect.construct(proxy, [7]);
                return JSON.stringify([result.x, result.hasNewTarget]);
                """));
    }

    @Test
    public void testReflectConstructErrors() {
        assertBooleanWithJavet(inArrowFunction("""
                try {
                  Reflect.construct(() => 1, []);
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """), inArrowFunction("""
                try {
                  Reflect.construct(function A() {}, undefined);
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """), inArrowFunction("""
                try {
                  Reflect.construct(function A() {}, [], () => 1);
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """));
    }

    @Test
    public void testReflectDefinePropertyAndGetOwnPropertyDescriptor() {
        assertStringWithJavet(inArrowFunction("""
                const o = {};
                const ok = Reflect.defineProperty(o, "x", {
                  value: 1, writable: false, enumerable: true, configurable: false
                });
                const d = Reflect.getOwnPropertyDescriptor(o, "x");
                return JSON.stringify([ok, o.x, d.writable, d.enumerable, d.configurable]);
                """), inArrowFunction("""
                const o = {};
                Object.defineProperty(o, "x", {
                  get: function() { return 1; },
                  configurable: true
                });
                const d = Reflect.getOwnPropertyDescriptor(o, "x");
                return JSON.stringify([typeof d.get, typeof d.set, d.enumerable, d.configurable]);
                """));

        assertBooleanWithJavet("Reflect.getOwnPropertyDescriptor({}, 'x') === undefined", inArrowFunction("""
                const p = new Proxy({}, {
                  defineProperty() { return false; }
                });
                return Reflect.defineProperty(p, "x", { value: 1 }) === false;
                """), inArrowFunction("""
                try {
                  Reflect.defineProperty({}, "x", 1);
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """), inArrowFunction("""
                try {
                  Reflect.getOwnPropertyDescriptor(1, "x");
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """));
    }

    @Test
    public void testReflectPreventExtensionsAndSetPrototypeOf() {
        assertBooleanWithJavet(inArrowFunction("""
                const obj = {};
                return Reflect.preventExtensions(obj) && !Reflect.isExtensible(obj);
                """), inArrowFunction("""
                const p = new Proxy({}, {
                  preventExtensions() { return false; }
                });
                try {
                  return Reflect.preventExtensions(p) === false;
                } catch (e) {
                  return false;
                }
                """), inArrowFunction("""
                const p = new Proxy({}, {
                  setPrototypeOf() { return false; }
                });
                try {
                  return Reflect.setPrototypeOf(p, {}) === false;
                } catch (e) {
                  return false;
                }
                """), inArrowFunction("""
                const proto = { p: 1 };
                const obj = {};
                return Reflect.setPrototypeOf(obj, proto)
                  && Object.getPrototypeOf(obj) === proto;
                """), inArrowFunction("""
                try {
                  Reflect.setPrototypeOf({}, 1);
                  return false;
                } catch (e) {
                  return e instanceof TypeError;
                }
                """));
    }

    @Test
    public void testReflectPropertyOperations() {
        assertStringWithJavet(inArrowFunction("""
                const obj = { x: 1, y: 2 };
                Reflect.deleteProperty(obj, "x");
                return JSON.stringify(obj);
                """), "var obj = {x: 1}; JSON.stringify([Reflect.has(obj, 'x'), Reflect.has(obj, 'y')])",
                inArrowFunction("""
                        const obj = { x: 1 };
                        Reflect.set(obj, "y", 2);
                        return JSON.stringify([Reflect.get(obj, "x"), Reflect.get(obj, "y")]);
                        """));
    }

    @Test
    public void testReflectRegistration() {
        assertBooleanWithJavet("Object.keys(Reflect).length === 0");

        assertStringWithJavet(inArrowFunction("""
                const d = Object.getOwnPropertyDescriptor(Reflect, "apply");
                const t = Object.getOwnPropertyDescriptor(Reflect, Symbol.toStringTag);
                return JSON.stringify([
                  typeof d.value, d.writable, d.enumerable, d.configurable,
                  Reflect[Symbol.toStringTag], t.writable, t.enumerable, t.configurable
                ]);
                """), """
                (() => JSON.stringify(Object.getOwnPropertyNames(Reflect).sort()))()
                """);
    }
}
