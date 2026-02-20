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
 * Unit tests for logical assignment operators (&&=, ||=, ??=).
 * Based on ES2021 logical assignment operators specification.
 */
public class LogicalAssignmentTest extends BaseJavetTest {

    @Test
    public void testLogicalAndAssignment_Identifier() {
        // Normal case: don't assign when falsy
        assertBooleanWithJavet(
                "var a = false; a &&= true; a");

        assertIntegerWithJavet(
                // Normal case: don't assign when 0
                "var a = 0; a &&= 10; a",
                // Normal case: don't assign when null (check that it stays null)
                "var a = null; a &&= 10; a === null ? 1 : 0",
                // Normal case: don't assign when undefined (check that it stays undefined)
                "var a = undefined; a &&= 10; a === undefined ? 1 : 0",
                // Normal case: assign when truthy
                "var a = 5; a &&= 10; a");

        // Normal case: assign when true
        assertStringWithJavet(
                "var a = true; a &&= 'yes'; a");
    }

    @Test
    public void testLogicalAndAssignment_MemberExpression() {
        assertIntegerWithJavet(
                // Normal case: object property with falsy value
                "var obj = {x: 0}; obj.x &&= 42; obj.x",
                // Normal case: object property with truthy value
                "var obj = {x: 10}; obj.x &&= 42; obj.x",
                // Normal case: computed property with truthy value
                "var obj = {y: 5}; var key = 'y'; obj[key] &&= 99; obj.y");

        // Normal case: computed property with falsy value
        assertBooleanWithJavet(
                "var obj = {y: false}; var key = 'y'; obj[key] &&= true; obj.y");
    }

    @Test
    public void testLogicalOrAssignment_Identifier() {
        assertIntegerWithJavet(
                // Normal case: assign when falsy
                "var a = 0; a ||= 10; a",
                // Normal case: assign when false
                "var a = false; a ||= 10; a",
                // Normal case: assign when null
                "var a = null; a ||= 10; a",
                // Normal case: assign when undefined
                "var a = undefined; a ||= 10; a",
                // Normal case: don't assign when truthy
                "var a = 5; a ||= 10; a");

        // Normal case: assign when empty string
        assertStringWithJavet(
                "var a = ''; a ||= 'default'; a");
    }

    @Test
    public void testLogicalOrAssignment_MemberExpression() {
        assertIntegerWithJavet(
                // Normal case: object property with falsy value
                "var obj = {x: 0}; obj.x ||= 42; obj.x",
                // Normal case: object property with truthy value
                "var obj = {x: 10}; obj.x ||= 42; obj.x",
                // Normal case: computed property with falsy value
                "var obj = {y: null}; var key = 'y'; obj[key] ||= 99; obj.y",
                // Normal case: computed property with truthy value
                "var obj = {y: 5}; var key = 'y'; obj[key] ||= 99; obj.y");
    }

    @Test
    public void testNullishCoalescingAssignment_Identifier() {
        assertIntegerWithJavet(
                // Normal case: assign when null
                "var a = null; a ??= 10; a",
                // Normal case: assign when undefined
                "var a = undefined; a ??= 10; a",
                // Normal case: don't assign when 0
                "var a = 0; a ??= 10; a",
                // Normal case: existing value
                "var a = 5; a ??= 10; a");

        // Normal case: don't assign when false
        assertBooleanWithJavet(
                "var a = false; a ??= true; a");

        // Normal case: don't assign when empty string
        assertStringWithJavet(
                "var a = ''; a ??= 'default'; a");
    }

    @Test
    public void testNullishCoalescingAssignment_MemberExpression() {
        assertIntegerWithJavet(
                // Normal case: object property with null
                "var obj = {x: null}; obj.x ??= 42; obj.x",
                // Normal case: object property with undefined
                "var obj = {x: undefined}; obj.x ??= 42; obj.x",
                // Normal case: object property with 0
                "var obj = {x: 0}; obj.x ??= 42; obj.x",
                // Normal case: object property with existing value
                "var obj = {x: 10}; obj.x ??= 42; obj.x",
                // Normal case: computed property with null
                "var obj = {y: null}; var key = 'y'; obj[key] ??= 99; obj.y",
                // Normal case: computed property with existing value
                "var obj = {y: 5}; var key = 'y'; obj[key] ??= 99; obj.y");
    }

    @Test
    public void testReturnValue() {
        assertIntegerWithJavet(
                // ??= should return the final value
                "var a = null; a ??= 42",
                "var a = 10; a ??= 42",
                // &&= should return the final value
                "var a = 5; a &&= 10",
                "var a = 0; a &&= 10",
                // ||= should return the final value
                "var a = 0; a ||= 10",
                "var a = 5; a ||= 10");
    }

    @Test
    public void testShortCircuitBehavior() {
        assertIntegerWithJavet(
                // ??= should not evaluate RHS if LHS is not nullish
                "var a = 5; var b = 0; a ??= (b = 10); b",
                // &&= should not evaluate RHS if LHS is falsy
                "var a = 0; var b = 0; a &&= (b = 10); b",
                // ||= should not evaluate RHS if LHS is truthy
                "var a = 5; var b = 0; a ||= (b = 10); b");
    }
}
