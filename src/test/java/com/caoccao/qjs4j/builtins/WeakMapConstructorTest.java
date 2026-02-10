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

public class WeakMapConstructorTest extends BaseJavetTest {

    @Test
    void testTypeof() {
        assertStringWithJavet("typeof WeakMap");

        assertIntegerWithJavet("WeakMap.length");

        assertStringWithJavet("WeakMap.name");

        assertStringWithJavet("new WeakMap().toString()");

        assertErrorWithJavet("WeakMap()");
    }

    @Test
    void testWeakMapConstructorIterableEdgeCases() {
        assertBooleanWithJavet("""
                var k1 = {};
                var k2 = {};
                var source = new Map([[k1, 1], [k2, 2]]);
                var wm = new WeakMap(source);
                wm.get(k1) === 1 && wm.get(k2) === 2""");

        assertThatThrownBy(() -> context.eval("new WeakMap({})"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new WeakMap([[1, 'a']])"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
    }

}
