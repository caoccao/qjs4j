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

/**
 * Test cases for destructuring assignment expressions and shorthand object properties.
 * Covers array destructuring assignment, object destructuring assignment,
 * default values, nested patterns, and member expression targets.
 */
public class DestructuringAssignmentTest extends BaseJavetTest {

    @Test
    public void testArrayDestructuringAssignmentExtraElements() {
        assertIntegerWithJavet(
                """
                        var x, y;
                        ([x, y] = [10, 20, 30]);
                        x + y""");
    }

    @Test
    public void testArrayDestructuringAssignmentMissingElements() {
        assertBooleanWithJavet(
                """
                        var x, y;
                        ([x, y] = [10]);
                        y === undefined""");
    }

    @Test
    public void testArrayDestructuringAssignmentSingleElement() {
        assertIntegerWithJavet(
                """
                        var x;
                        ([x] = [42]);
                        x""");
    }

    @Test
    public void testArrayDestructuringDefaultEmptyStringDoesNotTrigger() {
        assertStringWithJavet(
                """
                        var x;
                        ([x = 'default'] = ['']);
                        x""");
    }

    @Test
    public void testArrayDestructuringDefaultExpressionEvaluated() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var counter = function() { count++; return count; };
                        var x;
                        ([x = counter()] = []);
                        x""");
    }

    @Test
    public void testArrayDestructuringDefaultFalseDoesNotTrigger() {
        assertBooleanWithJavet(
                """
                        var x;
                        ([x = 42] = [false]);
                        x === false""");
    }

    @Test
    public void testArrayDestructuringDefaultLazyEvaluation() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var counter = function() { count++; return count; };
                        var x;
                        ([x = counter()] = [10]);
                        count""");
    }

    @Test
    public void testArrayDestructuringDefaultNotUsed() {
        assertIntegerWithJavet(
                """
                        var x;
                        ([x = 42] = [10]);
                        x""");
    }

    @Test
    public void testArrayDestructuringDefaultNullDoesNotTrigger() {
        assertBooleanWithJavet(
                """
                        var x;
                        ([x = 42] = [null]);
                        x === null""");
    }

    @Test
    public void testArrayDestructuringDefaultUndefinedTriggersDefault() {
        assertIntegerWithJavet(
                """
                        var x;
                        ([x = 42] = [undefined]);
                        x""");
    }

    @Test
    public void testArrayDestructuringDefaultUsed() {
        assertIntegerWithJavet(
                """
                        var x;
                        ([x = 42] = []);
                        x""");
    }

    @Test
    public void testArrayDestructuringDefaultZeroDoesNotTrigger() {
        assertIntegerWithJavet(
                """
                        var x;
                        ([x = 42] = [0]);
                        x""");
    }

    @Test
    public void testArrayDestructuringMixedDefaults() {
        assertIntegerWithJavet(
                """
                        var x, y;
                        ([x = 10, y = 20] = [5]);
                        x + y""");
    }

    @Test
    public void testArrayDestructuringMultipleDefaults() {
        assertIntegerWithJavet(
                """
                        var x, y;
                        ([x = 10, y = 20] = []);
                        x + y""");
    }

    @Test
    public void testArrayDestructuringReturnsRightSide() {
        assertIntegerWithJavet(
                """
                        var x;
                        var result = ([x] = [42]);
                        result[0]""");
    }

    @Test
    public void testArrayDestructuringToComputedMember() {
        assertIntegerWithJavet(
                """
                        var obj = {};
                        ([obj['x']] = [42]);
                        obj.x""");
    }

    @Test
    public void testArrayDestructuringToMemberDefaultNotUsed() {
        assertIntegerWithJavet(
                """
                        var obj = {};
                        ([obj.x = 99] = [42]);
                        obj.x""");
    }

    @Test
    public void testArrayDestructuringToMemberExpression() {
        assertIntegerWithJavet(
                """
                        var obj = {};
                        ([obj.x] = [42]);
                        obj.x""");
    }

    @Test
    public void testArrayDestructuringToMemberWithDefault() {
        assertIntegerWithJavet(
                """
                        var obj = {};
                        ([obj.x = 99] = []);
                        obj.x""");
    }

    @Test
    public void testNestedArrayDestructuringAssignment() {
        assertIntegerWithJavet(
                """
                        var a, b, c;
                        ([a, [b, c]] = [1, [2, 3]]);
                        a + b + c""");
    }

    @Test
    public void testObjectDestructuringAssignmentExtraProperties() {
        assertIntegerWithJavet(
                """
                        var x;
                        ({x} = {x: 42, y: 99});
                        x""");
    }

    @Test
    public void testObjectDestructuringAssignmentMissingProperty() {
        assertBooleanWithJavet(
                """
                        var x;
                        ({x} = {});
                        x === undefined""");
    }

    @Test
    public void testObjectDestructuringAssignmentMultiple() {
        assertIntegerWithJavet(
                """
                        var x, y;
                        ({x, y} = {x: 10, y: 20});
                        x + y""");
    }

    @Test
    public void testObjectDestructuringDefaultExpressionEvaluated() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var counter = function() { count++; return count; };
                        var x;
                        ({x = counter()} = {});
                        x""");
    }

    @Test
    public void testObjectDestructuringDefaultFalseDoesNotTrigger() {
        assertBooleanWithJavet(
                """
                        var x;
                        ({x = 42} = {x: false});
                        x === false""");
    }

    @Test
    public void testObjectDestructuringDefaultLazyEvaluation() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var counter = function() { count++; return count; };
                        var x;
                        ({x = counter()} = {x: 10});
                        count""");
    }

    @Test
    public void testObjectDestructuringDefaultNotUsed() {
        assertIntegerWithJavet(
                """
                        var x;
                        ({x = 42} = {x: 10});
                        x""");
    }

    @Test
    public void testObjectDestructuringDefaultNullDoesNotTrigger() {
        assertBooleanWithJavet(
                """
                        var x;
                        ({x = 42} = {x: null});
                        x === null""");
    }

    @Test
    public void testObjectDestructuringDefaultUndefinedTriggersDefault() {
        assertIntegerWithJavet(
                """
                        var x;
                        ({x = 42} = {x: undefined});
                        x""");
    }

    @Test
    public void testObjectDestructuringDefaultUsed() {
        assertIntegerWithJavet(
                """
                        var x;
                        ({x = 42} = {});
                        x""");
    }

    @Test
    public void testObjectDestructuringDefaultZeroDoesNotTrigger() {
        assertIntegerWithJavet(
                """
                        var x;
                        ({x = 42} = {x: 0});
                        x""");
    }

    @Test
    public void testObjectDestructuringMixedDefaults() {
        assertIntegerWithJavet(
                """
                        var x, y;
                        ({x = 10, y = 20} = {x: 5});
                        x + y""");
    }

    @Test
    public void testObjectDestructuringMultipleDefaults() {
        assertIntegerWithJavet(
                """
                        var x, y;
                        ({x = 10, y = 20} = {});
                        x + y""");
    }

    @Test
    public void testObjectDestructuringRenamedProperty() {
        assertIntegerWithJavet(
                """
                        var a;
                        ({x: a} = {x: 42});
                        a""");
    }

    @Test
    public void testObjectDestructuringRenamedWithDefault() {
        assertIntegerWithJavet(
                """
                        var a;
                        ({x: a = 42} = {});
                        a""");
    }

    @Test
    public void testObjectDestructuringReturnsRightSide() {
        assertIntegerWithJavet(
                """
                        var x;
                        var result = ({x} = {x: 42});
                        result.x""");
    }

    @Test
    public void testObjectDestructuringToMemberExpression() {
        assertIntegerWithJavet(
                """
                        var obj = {};
                        ({x: obj.y} = {x: 42});
                        obj.y""");
    }

    @Test
    public void testShorthandObjectProperty() {
        assertIntegerWithJavet(
                """
                        var x = 42;
                        var obj = {x};
                        obj.x""");
    }

    @Test
    public void testShorthandObjectPropertyMixed() {
        assertIntegerWithJavet(
                """
                        var x = 10;
                        var obj = {x, y: 20};
                        obj.x + obj.y""");
    }

    @Test
    public void testShorthandObjectPropertyMultiple() {
        assertIntegerWithJavet(
                """
                        var x = 10, y = 20;
                        var obj = {x, y};
                        obj.x + obj.y""");
    }

    @Test
    public void testSimpleArrayDestructuringAssignment() {
        assertIntegerWithJavet(
                """
                        var x, y;
                        ([x, y] = [1, 2]);
                        x + y""");
    }

    @Test
    public void testSimpleObjectDestructuringAssignment() {
        assertIntegerWithJavet(
                """
                        var x;
                        ({x} = {x: 42});
                        x""");
    }
}
