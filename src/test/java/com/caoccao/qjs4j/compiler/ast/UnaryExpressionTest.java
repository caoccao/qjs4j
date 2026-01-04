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

package com.caoccao.qjs4j.compiler.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class UnaryExpressionTest extends BaseJavetTest {

    @Test
    public void testArrayElementPostfixIncrement() {
        assertIntegerWithJavet(
                """
                        let arr = [1, 2, 3];
                        let old = arr[1]++;
                        old;""",
                """
                        let arr = [1, 2, 3];
                        arr[1]++;
                        arr[1];""");
    }

    @Test
    public void testArrayElementPrefixIncrement() {
        assertIntegerWithJavet(
                """
                        let arr = [1, 2, 3];
                        let result = ++arr[1];
                        result;""",
                """
                        let arr = [1, 2, 3];
                        ++arr[1];
                        arr[1];""");
    }

    @Test
    public void testObjectPropertyPostfixIncrement() {
        assertIntegerWithJavet(
                """
                        let obj = { x: 5 };
                        let old = obj.x++;
                        old;""",
                """
                        let obj = { x: 5 };
                        obj.x++;
                        obj.x;""");
    }

    @Test
    public void testObjectPropertyPrefixIncrement() {
        assertIntegerWithJavet(
                """
                        let obj = { x: 5 };
                        let result = ++obj.x;
                        result;""",
                """
                        let obj = { x: 5 };
                        ++obj.x;
                        obj.x;""");
    }

    @Test
    public void testPostfixDecrement() {
        assertIntegerWithJavet(
                """
                        let arr = [10, 20, 30];
                        let old = arr[0]--;
                        old;""",
                """
                        let arr = [10, 20, 30];
                        arr[0]--;
                        arr[0];""");
    }

    @Test
    public void testPrefixDecrement() {
        assertIntegerWithJavet(
                """
                        let obj = { y: 10 };
                        let result = --obj.y;
                        result;""",
                """
                        let obj = { y: 10 };
                        --obj.y;
                        obj.y;""");
    }
}
