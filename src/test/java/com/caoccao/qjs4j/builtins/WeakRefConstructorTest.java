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
 * Unit tests for WeakRef constructor.
 */
public class WeakRefConstructorTest extends BaseJavetTest {

    @Test
    public void testConstruct() {
        // Normal case: create WeakRef with new
        assertBooleanWithJavet("""
                var obj = {};
                var ref = new WeakRef(obj);
                ref.deref() === obj;""");
    }

    @Test
    public void testCreateWeakRef() {
        // Verify WeakRef can be created
        assertStringWithJavet("""
                var obj = {};
                var ref = new WeakRef(obj);
                typeof ref;""");

        // Test deref returns the target
        assertIntegerWithJavet("""
                var obj = { x: 42 };
                var ref = new WeakRef(obj);
                ref.deref().x;""");

        assertErrorWithJavet(
                // Edge case: target is null
                "new WeakRef(null);",
                // Edge case: target is not an object
                "new WeakRef('string');",
                "new WeakRef(42);",
                "new WeakRef(true);",
                "new WeakRef(undefined);");
    }
}
