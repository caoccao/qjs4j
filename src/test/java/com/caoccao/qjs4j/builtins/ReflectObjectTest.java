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
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Reflect object methods, particularly extensibility.
 */
public class ReflectObjectTest extends BaseJavetTest {

    @Test
    public void testReflectDeleteProperty() {
        assertStringWithJavet("var obj = {x: 1, y: 2}; Reflect.deleteProperty(obj, 'x'); JSON.stringify(obj)");
    }

    @Test
    public void testReflectGet() {
        assertIntegerWithJavet("var obj = {x: 1}; Reflect.get(obj, 'x')");
    }

    @Test
    public void testReflectHas() {
        assertStringWithJavet("var obj = {x: 1}; JSON.stringify([Reflect.has(obj, 'x'), Reflect.has(obj, 'y')])");
    }

    @Test
    public void testReflectIsExtensible() {
        assertBooleanWithJavet(
                "var obj = {}; Reflect.isExtensible(obj)",
                "var obj2 = {}; Reflect.preventExtensions(obj2); Reflect.isExtensible(obj2)",
                "var obj3 = {}; Object.freeze(obj3); Reflect.isExtensible(obj3)",
                "var obj4 = {}; Object.seal(obj4); Reflect.isExtensible(obj4)");
    }

    @Test
    public void testReflectPreventExtensions() {
        // Test that Reflect.preventExtensions prevents adding properties
        JSValue result = context.eval("""
                var obj = {a: 1};
                Reflect.preventExtensions(obj);
                obj.b = 2;
                JSON.stringify(obj)"""
        );
        assertThat(result.toJavaObject()).isEqualTo("{\"a\":1}");

        // Test that it returns true
        result = context.eval("var obj2 = {}; Reflect.preventExtensions(obj2)");
        assertThat((Boolean) result.toJavaObject()).isTrue();
    }

    @Test
    public void testReflectPreventExtensionsWithObjectPreventExtensions() {
        // Test that Reflect.preventExtensions and Object.preventExtensions are consistent
        assertBooleanWithJavet("""
                var obj1 = {};
                var obj2 = {};
                Reflect.preventExtensions(obj1);
                Object.preventExtensions(obj2);
                Reflect.isExtensible(obj1) === Object.isExtensible(obj2)""");
    }

    @Test
    public void testReflectSet() {
        assertIntegerWithJavet("var obj = {}; Reflect.set(obj, 'x', 42); obj.x");
    }
}
