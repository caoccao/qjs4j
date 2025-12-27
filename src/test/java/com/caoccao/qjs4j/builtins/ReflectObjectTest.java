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
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Reflect object methods, particularly extensibility.
 */
public class ReflectObjectTest extends BaseTest {

    @Test
    public void testReflectDeleteProperty() {
        JSValue result = ctx.eval(
                "var obj = {x: 1, y: 2}; " +
                        "Reflect.deleteProperty(obj, 'x'); " +
                        "JSON.stringify(obj)"
        );
        assertEquals("{\"y\":2}", result.toJavaObject());
    }

    @Test
    public void testReflectGet() {
        JSValue result = ctx.eval(
                "var obj = {x: 1}; " +
                        "Reflect.get(obj, 'x')"
        );
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testReflectHas() {
        JSValue result = ctx.eval(
                "var obj = {x: 1}; " +
                        "Reflect.has(obj, 'x')"
        );
        assertTrue((Boolean) result.toJavaObject());

        result = ctx.eval("Reflect.has(obj, 'y')");
        assertFalse((Boolean) result.toJavaObject());
    }

    @Test
    public void testReflectIsExtensible() {
        // Test normal extensible object
        JSValue result = ctx.eval("var obj = {}; Reflect.isExtensible(obj)");
        assertTrue((Boolean) result.toJavaObject());

        // Test after preventExtensions
        result = ctx.eval(
                "var obj2 = {}; " +
                        "Reflect.preventExtensions(obj2); " +
                        "Reflect.isExtensible(obj2)"
        );
        assertFalse((Boolean) result.toJavaObject());

        // Test frozen object is not extensible
        result = ctx.eval(
                "var obj3 = {}; " +
                        "Object.freeze(obj3); " +
                        "Reflect.isExtensible(obj3)"
        );
        assertFalse((Boolean) result.toJavaObject());

        // Test sealed object is not extensible
        result = ctx.eval(
                "var obj4 = {}; " +
                        "Object.seal(obj4); " +
                        "Reflect.isExtensible(obj4)"
        );
        assertFalse((Boolean) result.toJavaObject());
    }

    @Test
    public void testReflectPreventExtensions() {
        // Test that Reflect.preventExtensions prevents adding properties
        JSValue result = ctx.eval(
                "var obj = {a: 1}; " +
                        "Reflect.preventExtensions(obj); " +
                        "obj.b = 2; " +  // Should not add
                        "JSON.stringify(obj)"
        );
        assertEquals("{\"a\":1}", result.toJavaObject());

        // Test that it returns true
        result = ctx.eval(
                "var obj2 = {}; " +
                        "Reflect.preventExtensions(obj2)"
        );
        assertTrue((Boolean) result.toJavaObject());
    }

    @Test
    public void testReflectPreventExtensionsWithObjectPreventExtensions() {
        // Test that Reflect.preventExtensions and Object.preventExtensions are consistent
        JSValue result = ctx.eval(
                "var obj1 = {}; " +
                        "var obj2 = {}; " +
                        "Reflect.preventExtensions(obj1); " +
                        "Object.preventExtensions(obj2); " +
                        "Reflect.isExtensible(obj1) === Object.isExtensible(obj2)"
        );
        assertTrue((Boolean) result.toJavaObject());
    }

    @Test
    public void testReflectSet() {
        JSValue result = ctx.eval(
                "var obj = {}; " +
                        "Reflect.set(obj, 'x', 42); " +
                        "obj.x"
        );
        assertEquals(42.0, (Double) result.toJavaObject());
    }
}
