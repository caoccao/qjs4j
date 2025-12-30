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

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WeakMap.prototype methods.
 */
public class WeakMapPrototypeTest extends BaseJavetTest {

    @Test
    public void testDelete() {
        // Normal case: delete existing key
        String code1 = """
                var weakMap = new WeakMap();
                var key1 = {};
                var key2 = {};
                weakMap.set(key1, 'value1');
                weakMap.set(key2, 'value2');
                weakMap.delete(key1);""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code1).executeBoolean(),
                () -> context.eval(code1).toJavaObject());

        // Normal case: delete non-existing key
        String code2 = """
                var weakMap = new WeakMap();
                var key = {};
                weakMap.delete(key);""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code2).executeBoolean(),
                () -> context.eval(code2).toJavaObject());

        // Normal case: no arguments
        String code3 = """
                var weakMap = new WeakMap();
                weakMap.delete();""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code3).executeBoolean(),
                () -> context.eval(code3).toJavaObject());

        // Edge case: non-object key
        String code4 = """
                var weakMap = new WeakMap();
                weakMap.delete('string');""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code4).executeBoolean(),
                () -> context.eval(code4).toJavaObject());

        // Edge case: called on non-WeakMap
        assertErrorWithJavet("WeakMap.prototype.delete.call('not weakmap', {});");
    }

    @Test
    public void testGet() {
        // Normal case: get existing key
        String code1 = """
                var weakMap = new WeakMap();
                var key1 = {};
                weakMap.set(key1, 'value1');
                weakMap.get(key1);""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code1).executeString(),
                () -> context.eval(code1).toJavaObject());

        // Normal case: get non-existing key
        String code2 = """
                var weakMap = new WeakMap();
                var key = {};
                weakMap.get(key);""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code2).executeObject(),
                () -> context.eval(code2).toJavaObject());

        // Normal case: no arguments
        String code3 = """
                var weakMap = new WeakMap();
                weakMap.get();""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code3).executeObject(),
                () -> context.eval(code3).toJavaObject());

        // Edge case: non-object key
        String code4 = """
                var weakMap = new WeakMap();
                weakMap.get('string');""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code4).executeObject(),
                () -> context.eval(code4).toJavaObject());

        // Edge case: called on non-WeakMap
        assertErrorWithJavet("WeakMap.prototype.get.call('not weakmap', {});");
    }

    @Test
    public void testHas() {
        Stream.of(
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
                weakMap.has('string');"""
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeBoolean(),
                        () -> context.eval(code).toJavaObject()));

        // Edge case: called on non-WeakMap
        assertErrorWithJavet("WeakMap.prototype.has.call('not weakmap', {});");
    }

    @Test
    public void testSet() {
        // Normal case: set new key-value
        String code1 = """
                var weakMap = new WeakMap();
                var key1 = {};
                weakMap.set(key1, 'value1').constructor === WeakMap""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code1).executeBoolean(),
                () -> context.eval(code1).toJavaObject());

        // Verify set returns the WeakMap
        String code2 = """
                var weakMap = new WeakMap();
                var key = {};
                weakMap.set(key, 'value') === weakMap;""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code2).executeBoolean(),
                () -> context.eval(code2).toJavaObject());

        // Normal case: set with undefined value
        String code3 = """
                var weakMap = new WeakMap();
                var key = {};
                weakMap.set(key);
                weakMap.get(key);""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code3).executeObject(),
                () -> context.eval(code3).toJavaObject());

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
}
