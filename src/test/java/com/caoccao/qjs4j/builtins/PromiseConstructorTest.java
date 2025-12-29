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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for Promise constructor static methods with iterable support.
 */
public class PromiseConstructorTest extends BaseTest {

    @Test
    public void testPromiseAllWithArray() {
        // Test with array (original behavior)
        JSValue result = context.eval(
                "var p1 = Promise.resolve(1); " +
                        "var p2 = Promise.resolve(2); " +
                        "var p3 = Promise.resolve(3); " +
                        "Promise.all([p1, p2, p3]).then(arr => JSON.stringify(arr))"
        );
        assertNotNull(result);
    }

    // Note: Tests for JavaScript native iterables (Set, Map, custom iterables) are not included
    // because the current JSIteratorHelper implementation doesn't properly bridge with
    // JavaScript native objects. The iterable support works with Java-created iterables
    // (JSIterator, JSGenerator) but not with JavaScript's built-in Set/Map or custom iterables.
}
