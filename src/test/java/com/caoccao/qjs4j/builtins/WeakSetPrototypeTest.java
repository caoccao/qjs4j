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
 * Unit tests for WeakSet.prototype methods.
 */
public class WeakSetPrototypeTest extends BaseJavetTest {

    @Test
    public void testAdd() {
        // Normal case: add new value
        assertBooleanWithJavet("""
                        var weakSet = new WeakSet();
                        var value1 = {};
                        weakSet.add(value1).constructor === WeakSet""",
                """
                        var weakSet = new WeakSet();
                        var value = {};
                        weakSet.add(value) === weakSet;""",
                """
                        var weakSet = new WeakSet();
                        var value = {};
                        weakSet.add(value);
                        weakSet.add(value);
                        weakSet.has(value);""");

        // Edge case: no arguments
        assertErrorWithJavet("""
                var weakSet = new WeakSet();
                weakSet.add();""");

        // Edge case: non-object value
        assertErrorWithJavet("""
                var weakSet = new WeakSet();
                weakSet.add('string');""");

        // Edge case: called on non-WeakSet
        assertErrorWithJavet("WeakSet.prototype.add.call('not weakset', {});");
    }

    @Test
    public void testDelete() {
        assertBooleanWithJavet(
                // Normal case: delete existing value
                """
                        var weakSet = new WeakSet();
                        var value1 = {};
                        weakSet.add(value1);
                        weakSet.delete(value1);""",
                // Normal case: delete non-existing value
                """
                        var weakSet = new WeakSet();
                        var value = {};
                        weakSet.delete(value);""",
                // Normal case: no arguments
                """
                        var weakSet = new WeakSet();
                        weakSet.delete();""",
                // Edge case: non-object value
                """
                        var weakSet = new WeakSet();
                        weakSet.delete('string');""");

        // Edge case: called on non-WeakSet
        assertErrorWithJavet("WeakSet.prototype.delete.call('not weakset', {});");
    }

    @Test
    public void testHas() {
        assertBooleanWithJavet(
                // Normal case: has existing value
                """
                        var weakSet = new WeakSet();
                        var value1 = {};
                        weakSet.add(value1);
                        weakSet.has(value1);""",
                // Normal case: has non-existing value
                """
                        var weakSet = new WeakSet();
                        var value = {};
                        weakSet.has(value);""",
                // Normal case: no arguments
                """
                        var weakSet = new WeakSet();
                        weakSet.has();""",
                // Edge case: non-object value
                """
                        var weakSet = new WeakSet();
                        weakSet.has('string');""");

        // Edge case: called on non-WeakSet
        assertErrorWithJavet("WeakSet.prototype.has.call('not weakset', {});");
    }
}
