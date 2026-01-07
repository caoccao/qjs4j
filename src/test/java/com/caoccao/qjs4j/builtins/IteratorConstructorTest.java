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
 * Unit tests for Iterator constructor static methods.
 */
public class IteratorConstructorTest extends BaseJavetTest {
    @Test
    void testIteratorCannotBeConstructedDirectly() {
        assertErrorWithJavet(
                "new Iterator()",
                "Iterator()");
    }

    @Test
    void testIteratorName() throws Exception {
        assertStringWithJavet(
                "Iterator.name");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet(
                "typeof Iterator;",
                "typeof Iterator.from",
                "typeof Iterator.prototype",
                "typeof Iterator.prototype.map",
                "typeof Iterator.prototype.filter",
                "typeof Iterator.prototype.toArray");
        assertIntegerWithJavet(
                "Iterator.length;");
    }
}
