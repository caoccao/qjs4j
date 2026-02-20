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

public class SequenceExpressionTest extends BaseJavetTest {

    @Test
    public void testBasicSequenceExpression() {
        assertIntegerWithJavet(
                """
                        let x;
                        x = (1, 2, 3);
                        x;""");
    }

    @Test
    public void testCommaInArray() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        let arr = [(x = 1, x), (x = 2, x), (x = 3, x)];
                        arr[2];""");
    }

    @Test
    public void testCommaInForLoop() {
        assertIntegerWithJavet(
                """
                        let sum = 0;
                        for (let i = 0, j = 10; i < 3; i++, j--) {
                            sum = sum + i + j;
                        }
                        sum;""");
    }

    @Test
    public void testCommaInIfCondition() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        let result = (x = 10, x > 5) ? x : 0;
                        result;""");
    }

    @Test
    public void testCommaInObject() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        let obj = { a: (x = 5, x), b: (x = 10, x) };
                        obj.b;""");
    }

    @Test
    public void testCommaInReturnStatement() {
        assertIntegerWithJavet(
                """
                        function test() {
                            let x = 1;
                            return (x++, x++, x);
                        }
                        test();""");
    }

    @Test
    public void testCommaInWhileLoop() {
        assertIntegerWithJavet(
                """
                        let x = 0, count = 0;
                        while ((x++, count++, x < 5)) {
                            // empty
                        }
                        count;""");
    }

    @Test
    public void testCommaWithArithmetic() {
        assertIntegerWithJavet(
                """
                        let a = 0, b = 0;
                        ((a = 3, b = 4, a + b) * 2);""");
    }

    @Test
    public void testCommaWithFunctionCalls() {
        assertIntegerWithJavet(
                """
                        let counter = 0;
                        function inc() { return ++counter; }
                        (inc(), inc(), inc(), counter);""");
    }

    @Test
    public void testCommaWithLogicalOps() {
        assertBooleanWithJavet(
                """
                        let a = 0, b = 0;
                        ((a = 10, a > 5) && (b = 20, b > 15));""");
    }

    @Test
    public void testCommaWithSideEffects() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        let result = (x++, x++, x++, x);
                        result;""");
    }

    @Test
    public void testCommaWithStrings() {
        assertStringWithJavet(
                """
                        let s = "";
                        (s = "a", s = s + "b", s = s + "c", s);""");
    }

    @Test
    public void testCommaWithTernary() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        (x = 10, x > 5) ? (x++, x) : (x--, x);""");
    }

    @Test
    public void testCommaWithVariables() {
        assertIntegerWithJavet(
                """
                        let a = 0, b = 0;
                        (a = 5, b = 10, a + b);""");
    }

    @Test
    public void testNestedComma() {
        assertIntegerWithJavet(
                """
                        let a = 0;
                        ((a = 5, a), (a++, a));""");
    }

    @Test
    public void testNestedSequenceExpressions() {
        assertIntegerWithJavet(
                """
                        let a = 0, b = 0, c = 0;
                        ((a = 1, b = 2), (c = a + b, c * 2));""");
    }

    @Test
    public void testSequenceExpressionEvaluation() {
        assertIntegerWithJavet(
                """
                        let a = 0, b = 0;
                        (a = 1, b = 2, a + b);""");
    }

    @Test
    public void testSequenceExpressionInArrayInitializer() {
        assertIntegerWithJavet(
                """
                        let x = 0, y = 0;
                        let arr = [(x = 1, x), (y = 2, y), (x + y)];
                        arr[2];""");
    }

    @Test
    public void testSequenceExpressionInAssignment() {
        assertIntegerWithJavet(
                """
                        let a, b, c;
                        let result = (a = 10, b = 20, c = a + b);
                        result;""");
    }

    @Test
    public void testSequenceExpressionInCondition() {
        assertBooleanWithJavet(
                """
                        let x = 0;
                        let result = (x = 5, x > 3) ? true : false;
                        result;""");
    }

    @Test
    public void testSequenceExpressionInForLoop() {
        assertIntegerWithJavet(
                """
                        let sum = 0;
                        for (let i = 0, j = 10; i < 5; i++, j--) {
                            sum += i + j;
                        }
                        sum;""");
    }

    @Test
    public void testSequenceExpressionInObjectInitializer() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        let obj = { a: (x = 10, x), b: (x++, x) };
                        obj.b;""");
    }

    @Test
    public void testSequenceExpressionInReturn() {
        assertIntegerWithJavet(
                """
                        function test() {
                            let x = 5;
                            return (x++, x++, x);
                        }
                        test();""");
    }

    @Test
    public void testSequenceExpressionInSwitchCase() {
        assertIntegerWithJavet(
                """
                        let x = 0, y = 0;
                        switch ((x = 2, y = 3, x + y)) {
                            case 5: x = 100; break;
                            default: x = 0;
                        }
                        x;""");
    }

    @Test
    public void testSequenceExpressionInThrowStatement() {
        assertBooleanWithJavet(
                """
                        let x = 0;
                        try {
                            throw (x = 5, new Error("test"));
                        } catch (e) {
                            x === 5;
                        }""");
    }

    @Test
    public void testSequenceExpressionInWhileLoop() {
        assertIntegerWithJavet(
                """
                        let x = 0, y = 10;
                        while ((x++, y--, x < 5)) {
                            // empty
                        }
                        x;""");
    }

    @Test
    public void testSequenceExpressionMultipleStatements() {
        assertIntegerWithJavet(
                """
                        let a = 0;
                        (a = 1, a++);
                        (a++, a++);
                        a;""");
    }

    @Test
    public void testSequenceExpressionPrecedence() {
        assertIntegerWithJavet(
                """
                        let a = 1, b = 2, c = 3;
                        let result = (a = 10, b = 20) + c;
                        result;""");
    }

    @Test
    public void testSequenceExpressionSingleExpression() {
        assertIntegerWithJavet(
                """
                        (42);""");
    }

    @Test
    public void testSequenceExpressionWithArithmeticOperators() {
        assertIntegerWithJavet(
                """
                        let a = 0, b = 0;
                        (a = 5, b = 3, a + b) * 2;""");
    }

    @Test
    public void testSequenceExpressionWithArrayAccess() {
        assertIntegerWithJavet(
                """
                        let arr = [1, 2, 3];
                        let i = 0;
                        (i++, arr[i]++, arr[i]);""");
    }

    @Test
    public void testSequenceExpressionWithBitwiseOperators() {
        assertIntegerWithJavet(
                """
                        let a = 0, b = 0;
                        (a = 5, b = 3, a | b);""");
    }

    @Test
    public void testSequenceExpressionWithBooleans() {
        assertBooleanWithJavet(
                """
                        let a = false, b = false;
                        (a = true, b = false, a && !b);""");
    }

    @Test
    public void testSequenceExpressionWithDeleteOperator() {
        assertBooleanWithJavet(
                """
                        let obj = { x: 10 };
                        (obj.y = 20, delete obj.y);""");
    }

    @Test
    public void testSequenceExpressionWithDestructuring() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        let arr = (x = 10, [5, 15]);
                        arr[0];""");
    }

    @Test
    public void testSequenceExpressionWithExponentiationOperator() {
        assertIntegerWithJavet(
                """
                        let a = 0, b = 0;
                        Math.floor((a = 2, b = 3, a ** b));""");
    }

    @Test
    public void testSequenceExpressionWithFunctionCalls() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        function inc() { return ++x; }
                        (inc(), inc(), inc());""");
    }

    @Test
    public void testSequenceExpressionWithLogicalOperators() {
        assertBooleanWithJavet(
                """
                        let a = 0, b = 0;
                        ((a = 5, a > 3) && (b = 10, b > 5));""");
    }

    @Test
    public void testSequenceExpressionWithNewExpression() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        class Counter {
                            constructor(val) { this.value = val; }
                        }
                        (x = 5, new Counter(x)).value;""");
    }

    @Test
    public void testSequenceExpressionWithNull() {
        assertObjectWithJavet(
                """
                        let x = 10;
                        (x++, null);""");
    }

    @Test
    public void testSequenceExpressionWithObjectAccess() {
        assertIntegerWithJavet(
                """
                        let obj = { x: 10 };
                        (obj.x++, obj.x++, obj.x);""");
    }

    @Test
    public void testSequenceExpressionWithShiftOperators() {
        assertIntegerWithJavet(
                """
                        let a = 0, b = 0;
                        (a = 8, b = 2, a >> b);""");
    }

    @Test
    public void testSequenceExpressionWithSideEffects() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        (x++, x++, x);""");
    }

    @Test
    public void testSequenceExpressionWithSpreadOperator() {
        assertIntegerWithJavet(
                """
                        let arr1 = [1, 2];
                        let x = 0;
                        let result = [(x = arr1[0], x), ...arr1];
                        result.length;""");
    }

    @Test
    public void testSequenceExpressionWithStrings() {
        assertStringWithJavet(
                """
                        let s = "";
                        (s = "hello", s = s + " ", s + "world");""");
    }

    @Test
    public void testSequenceExpressionWithTemplateStrings() {
        assertStringWithJavet(
                """
                        let x = 0, y = 0;
                        `result: ${(x = 10, y = 20, x + y)}`;""");
    }

    @Test
    public void testSequenceExpressionWithTernary() {
        assertIntegerWithJavet(
                """
                        let x = 0;
                        (x = 5, x > 3) ? (x++, x) : (x--, x);""");
    }

    @Test
    public void testSequenceExpressionWithThisKeyword() {
        assertIntegerWithJavet(
                """
                        let obj = {
                            x: 5,
                            test: function() {
                                let y = 0;
                                return (y = 10, this.x + y);
                            }
                        };
                        obj.test();""");
    }

    @Test
    public void testSequenceExpressionWithTypeofOperator() {
        assertStringWithJavet(
                """
                        let x = 0;
                        (x = 42, typeof x);""");
    }

    @Test
    public void testSequenceExpressionWithUndefined() {
        assertUndefinedWithJavet(
                """
                        let x;
                        (x = 5, undefined);""");
    }

    @Test
    public void testSequenceExpressionWithVoidOperator() {
        assertUndefinedWithJavet(
                """
                        let x = 0;
                        (x = 5, void x);""");
    }

    @Test
    public void testSimpleComma() {
        assertIntegerWithJavet("(1, 2, 3);");
    }
}
