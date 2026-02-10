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
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SetConstructorTest extends BaseJavetTest {

    @Test
    void testSetConstructorIterableEdgeCases() {
        assertBooleanWithJavet("""
                var set = new Set(new Set([0, 1, 2]));
                set.size === 3 && set.has(0) && set.has(1) && set.has(2)""");

        assertBooleanWithJavet("""
                var set = new Set('aba');
                set.size === 2 && set.has('a') && set.has('b')""");

        assertThatThrownBy(() -> context.eval("new Set({})"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Set(1)"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");

    }

    @Test
    void testTypeof() {
        // Set should be a function
        assertStringWithJavet("typeof Set");

        // Set.length should be 0
        assertIntegerWithJavet("Set.length");

        // Set.name should be "Set"
        assertStringWithJavet("Set.name");

        assertStringWithJavet("new Set().toString()");

        assertErrorWithJavet("Set()");
    }
}
