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
import org.junit.jupiter.api.Test;

/**
 * A failed {@code Object.defineProperty} or {@code Object.defineProperties} names the reason V8 names.
 * <p>
 * {@code Object.defineProperty} said "object is not extensible" for every failure — including a redefinition of a
 * non-configurable property on an extensible object — and {@code Object.defineProperties} said "Cannot redefine
 * property" for every failure, including an addition to a non-extensible object.
 */
public class DefinePropertyFailureMessageTest extends BaseJavetTest {
    @Test
    public void testDefineProperties() {
        assertErrorWithJavet("Object.defineProperties(Object.freeze({ a: 1 }), { a: { value: 2 } })",
                "Object.defineProperties(Object.preventExtensions({}), { a: { value: 2 } })",
                "Object.defineProperties(new Proxy({}, { defineProperty() { return false } }), { q: { value: 1 } })",
                "Object.defineProperties(String.prototype, { length: { value: 1 } })");
    }

    @Test
    public void testDefineProperty() {
        assertErrorWithJavet("Object.defineProperty(Object.freeze({ a: 1 }), 'a', { value: 2 })",
                "Object.defineProperty(Object.preventExtensions({}), 'b', { value: 1 })",
                "Object.defineProperty(String.prototype, 'length', { value: 3 })",
                "Object.defineProperty([], 'length', { get() {} })",
                "Object.defineProperty(new Proxy({}, { defineProperty() { return false } }), 'q', { value: 1 })",
                "Object.defineProperty(Object.preventExtensions([]), 0, { value: 1 })",
                "Object.defineProperty(Object.freeze([1]), 0, { value: 2 })",
                "Object.defineProperty(new Uint8Array(1), 5, { value: 2 })",
                "Object.defineProperty(new Uint8Array(1), 0, { get() {} })",
                "Object.defineProperty(Object.seal({ a: 1 }), 'a', { get() {} })",
                "var s = Symbol('k'); Object.defineProperty(Object.preventExtensions({}), s, { value: 1 })",
                "Object.defineProperty(Object.defineProperty({}, 'c', { value: 1 }), 'c', { value: 2 })",
                "Object.defineProperty(Object.preventExtensions(new Uint8Array(1)), 'foo', { value: 1 })",
                "Object.defineProperty(Object.freeze(new Uint8Array(0)), 'foo', { value: 1 })");
        assertBooleanWithJavet("Reflect.defineProperty(Object.freeze({ a: 1 }), 'a', { value: 2 })");
    }
}
