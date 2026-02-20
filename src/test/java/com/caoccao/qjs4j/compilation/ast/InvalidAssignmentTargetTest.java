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

package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Test cases for invalid left-hand side assignment target validation.
 * Reserved words and literals cannot appear on the left side of an assignment.
 */
public class InvalidAssignmentTargetTest extends BaseJavetTest {
    @Test
    public void testArrayDestructuringIsValidTarget() {
        assertIntegerWithJavet(
                """
                        var x;
                        ([x] = [42]);
                        x""");
    }

    @Test
    public void testAssignToBinaryExpression() {
        assertErrorWithJavet("(a + b) = 1");
    }

    @Test
    public void testAssignToComputedMemberExpression() {
        assertIntegerWithJavet("var obj = {}; obj['x'] = 42; obj['x']");
    }

    @Test
    public void testAssignToDoubleQuotedString() {
        assertErrorWithJavet("\"hello\" = \"world\"");
    }

    @Test
    public void testAssignToFalse() {
        assertErrorWithJavet("false = 0");
    }

    @Test
    public void testAssignToFloatNumber() {
        assertErrorWithJavet("1.5 = 2");
    }

    @Test
    public void testAssignToFunctionCall() {
        assertErrorWithJavet("foo() = 1");
    }

    @Test
    public void testAssignToIdentifier() {
        assertIntegerWithJavet("var x; x = 42; x");
    }

    @Test
    public void testAssignToMemberExpression() {
        assertIntegerWithJavet("var obj = {}; obj.x = 42; obj.x");
    }

    @Test
    public void testAssignToNull() {
        assertErrorWithJavet("null = 0");
    }

    @Test
    public void testAssignToNumber() {
        assertErrorWithJavet("1 = 2");
    }

    @Test
    public void testAssignToParenthesizedLiteral() {
        assertErrorWithJavet("(1) = 2");
    }

    @Test
    public void testAssignToString() {
        assertErrorWithJavet("'hello' = 'world'");
    }

    @Test
    public void testAssignToTrue() {
        assertErrorWithJavet("true = 1");
    }

    @Test
    public void testCompoundAssignToFalse() {
        assertErrorWithJavet("false += 1");
    }

    @Test
    public void testCompoundAssignToIdentifier() {
        assertIntegerWithJavet("var x = 10; x += 5; x");
    }

    @Test
    public void testCompoundAssignToNumber() {
        assertErrorWithJavet("1 += 2");
    }

    @Test
    public void testCompoundAssignToString() {
        assertErrorWithJavet("'a' += 'b'");
    }

    @Test
    public void testCompoundAssignToTrue() {
        assertErrorWithJavet("true -= 1");
    }

    @Test
    public void testObjectDestructuringIsValidTarget() {
        assertIntegerWithJavet(
                """
                        var x;
                        ({x} = {x: 42});
                        x""");
    }
}
