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
import org.junit.jupiter.api.Test;

/**
 * Unit tests for WeakMap.prototype methods.
 */
public class WeakMapPrototypeTest extends BaseJavetTest {

    @Test
    public void testDelete() {
        // Normal case: delete existing key
        assertBooleanWithJavet("""
                        var weakMap = new WeakMap();
                        var key1 = {};
                        var key2 = {};
                        weakMap.set(key1, 'value1');
                        weakMap.set(key2, 'value2');
                        weakMap.delete(key1);""",
                """
                        var weakMap = new WeakMap();
                        var key = {};
                        weakMap.delete(key);""",
                """
                        var weakMap = new WeakMap();
                        weakMap.delete();""",
                """
                        var weakMap = new WeakMap();
                        weakMap.delete('string');""");

        // Edge case: called on non-WeakMap
        assertErrorWithJavet("WeakMap.prototype.delete.call('not weakmap', {});");
    }

    @Test
    public void testGet() {
        // Normal case: get existing key
        assertStringWithJavet("""
                var weakMap = new WeakMap();
                var key1 = {};
                weakMap.set(key1, 'value1');
                weakMap.get(key1);""");

        // Normal case: get non-existing key
        assertBooleanWithJavet(
                "var weakMap = new WeakMap(); var key = {}; weakMap.get(key) === undefined;",
                "var weakMap = new WeakMap(); weakMap.get() === undefined;",
                "var weakMap = new WeakMap(); weakMap.get('string') === undefined;");

        // Edge case: called on non-WeakMap
        assertErrorWithJavet("WeakMap.prototype.get.call('not weakmap', {});");
    }

    @Test
    public void testHas() {
        assertBooleanWithJavet(
                // Normal case: has existing key
                """
                        var weakMap = new WeakMap();
                        var key1 = {};
                        weakMap.set(key1, 'value1');
                        weakMap.has(key1);""",
                // Normal case: has non-existing key
                """
                        var weakMap = new WeakMap();
                        var key = {};
                        weakMap.has(key);""",
                // Normal case: no arguments
                """
                        var weakMap = new WeakMap();
                        weakMap.has();""",
                // Edge case: non-object key
                """
                        var weakMap = new WeakMap();
                        weakMap.has('string');""");

        // Edge case: called on non-WeakMap
        assertErrorWithJavet("WeakMap.prototype.has.call('not weakmap', {});");
    }

    @Test
    public void testSet() {
        // Normal case: set new key-value
        assertBooleanWithJavet(
                """
                        var weakMap = new WeakMap();
                        var key1 = {};
                        weakMap.set(key1, 'value1').constructor === WeakMap""", """
                        var weakMap = new WeakMap();
                        var key = {};
                        weakMap.set(key, 'value') === weakMap;""", """
                        var weakMap = new WeakMap();
                        var key = {};
                        weakMap.set(key);
                        weakMap.get(key) === undefined;""");

        // Edge case: no arguments
        assertErrorWithJavet("""
                var weakMap = new WeakMap();
                weakMap.set();""");

        // Edge case: non-object key
        assertErrorWithJavet("""
                var weakMap = new WeakMap();
                weakMap.set('string', 'value');""");

        // Edge case: called on non-WeakMap
        assertErrorWithJavet("WeakMap.prototype.set.call('not weakmap', {}, 'value');");
    }

    @Test
    void testWeakMapBasicOperations() {
        assertBooleanWithJavet("""
                const wm = new WeakMap();
                const key = {};
                wm.set(key, 42);
                wm.get(key) === 42;
                """);
    }

    @Test
    void testWeakMapChaining() {
        assertBooleanWithJavet("""
                const wm = new WeakMap();
                const k1 = {}, k2 = {};
                const result = wm.set(k1, 1).set(k2, 2);
                result === wm && wm.get(k1) === 1 && wm.get(k2) === 2;
                """);
    }

    @Test
    void testWeakMapDelete() {
        assertBooleanWithJavet("""
                const wm = new WeakMap();
                const key = {};
                wm.set(key, 'value');
                const result = wm.delete(key);
                result && !wm.has(key);
                """);
    }

    @Test
    void testWeakMapHas() {
        assertBooleanWithJavet("""
                const wm = new WeakMap();
                const key = {};
                wm.set(key, 'value');
                wm.has(key);
                """);
    }
}
