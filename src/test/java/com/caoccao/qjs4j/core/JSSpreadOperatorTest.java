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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for the ECMAScript spread operator (...).
 * The spread operator allows an iterable (like an array) to be expanded
 * in places where zero or more arguments or elements are expected.
 */
public class JSSpreadOperatorTest extends BaseJavetTest {

    @Test
    public void testArrayConcatWithSpread() {
        assertStringWithJavet(
                """
                        const arr1 = [1, 2];
                        const arr2 = [3, 4];
                        JSON.stringify([...arr1, ...arr2])""");
    }

    @Test
    public void testArrayCopyWithSpread() {
        assertBooleanWithJavet(
                """
                        const arr1 = [1, 2, 3];
                        const arr2 = [...arr1];
                        arr1 !== arr2 && JSON.stringify(arr1) === JSON.stringify(arr2)""");
    }

    @Test
    public void testArrayInsertWithSpread() {
        assertStringWithJavet(
                """
                        const arr = [1, 2, 5, 6];
                        const insert = [3, 4];
                        JSON.stringify([...arr.slice(0, 2), ...insert, ...arr.slice(2)])""");
    }

    @Test
    public void testArrayLiteralSpreadBasic() {
        assertStringWithJavet(
                "JSON.stringify([...[1, 2, 3]])");
    }

    @Test
    public void testArrayLiteralSpreadEmpty() {
        assertStringWithJavet(
                "JSON.stringify([...[]])");
    }

    @Test
    public void testArrayLiteralSpreadMixed() {
        assertStringWithJavet(
                "JSON.stringify([1, ...[2, 3], 4])");
    }

    @Test
    public void testArrayLiteralSpreadMultiple() {
        assertStringWithJavet(
                "JSON.stringify([...[1, 2], ...[3, 4]])");
    }

    @Test
    public void testArrayLiteralSpreadNested() {
        assertStringWithJavet(
                "JSON.stringify([...[1, [2, 3]], 4])");
    }

    @Test
    public void testArrayLiteralSpreadString() {
        assertStringWithJavet(
                "JSON.stringify([...'abc'])",
                "JSON.stringify([1, ...'xy', 2])",
                "JSON.stringify([...''])",
                "JSON.stringify([...Number(123).toString()])",
                "JSON.stringify([...'🌟😀'])");
    }

    @Test
    public void testArrayLiteralSpreadWithHoles() {
        assertStringWithJavet(
                "JSON.stringify([0, ...[1, 2], , 3])",
                "JSON.stringify([0, ...[1, 2], , 3])",
                "JSON.stringify([, 1, 2])",
                "JSON.stringify([0, , 2])",
                "JSON.stringify([0, 1, ,])",
                "JSON.stringify([, , 2])",
                "JSON.stringify([, , ,])",
                "JSON.stringify([...[0], , ...[1, 2], , 3])",
                "JSON.stringify([0, , ...[1, 2]])",
                "JSON.stringify([...[0, 1], , 2])",
                "JSON.stringify([0,1,,,])",
                "JSON.stringify([0,...[1,2],,])",
                "JSON.stringify([0,,,...[1,2]])",
                "JSON.stringify([...[0],,...[1,2],,3])",
                "JSON.stringify([,0,,...[1],,2,...'ab',,3,,])",
                "JSON.stringify([0, ...\"ab\", , 2])");
    }

    @Test
    public void testArrayOfWithSpread() {
        assertStringWithJavet(
                "JSON.stringify(Array.of(...[1, 2, 3]))");
    }

    @Test
    public void testArrayPushWithSpread() {
        assertStringWithJavet(
                """
                        const arr = [1, 2];
                        arr.push(...[3, 4]);
                        JSON.stringify(arr)""");
    }

    @Test
    public void testComplexRestParameterUsage() {
        assertIntegerWithJavet(
                """
                        const fn = (a, b, ...rest) => a + b + rest.reduce((acc, v) => acc + v, 0);
                        fn(1, 2, 3, 4, 5)""");
    }

    @Test
    public void testFunctionCallSpreadBasic() {
        assertIntegerWithJavet(
                """
                        function sum(a, b, c) {
                            return a + b + c;
                        }
                        sum(...[1, 2, 3])""");
    }

    @Test
    public void testFunctionCallSpreadEmpty() {
        assertIntegerWithJavet(
                """
                        function test() {
                            return arguments.length;
                        }
                        test(...[])""");
    }

    @Test
    public void testFunctionCallSpreadMixed() {
        assertIntegerWithJavet(
                """
                        function sum(a, b, c, d, e) {
                            return a + b + c + d + e;
                        }
                        sum(1, ...[2, 3], 4, 5)""");
    }

    @Test
    public void testFunctionCallSpreadMultiple() {
        assertIntegerWithJavet(
                """
                        function sum(...args) {
                            return args.reduce((a, b) => a + b, 0);
                        }
                        sum(...[1, 2], ...[3, 4])""");
    }

    @Test
    public void testFunctionCallSpreadWithRestParameter() {
        assertIntegerWithJavet(
                """
                        function test(a, ...rest) {
                            return rest.length;
                        }
                        test(...[1, 2, 3, 4])""");
    }

    @Test
    public void testMathMaxWithSpread() {
        assertIntegerWithJavet(
                "Math.max(...[1, 5, 3, 2, 4])");
    }

    @Test
    public void testMathMinWithSpread() {
        assertIntegerWithJavet(
                "Math.min(...[5, 3, 1, 4, 2])");
    }

    @Test
    public void testMethodCallSpreadBasic() {
        assertIntegerWithJavet(
                """
                        const obj = {
                            sum: function(a, b, c) {
                                return a + b + c;
                            }
                        };
                        obj.sum(...[1, 2, 3])""");
    }

    @Test
    public void testMethodCallSpreadThis() {
        assertIntegerWithJavet(
                """
                        const obj = {
                            value: 10,
                            add: function(a, b) {
                                return this.value + a + b;
                            }
                        };
                        obj.add(...[5, 15])""");
    }

    @Test
    public void testMultipleSpreadInSameArray() {
        assertStringWithJavet(
                "JSON.stringify([...[1], ...[2], ...[3], ...[4, 5]])");
    }

    @Test
    public void testRestParameterAfterRegularInArrowFunction() {
        assertIntegerWithJavet(
                """
                        const fn = (first, ...rest) => first + rest.length;
                        fn(10, 1, 2, 3)""");
    }

    @Test
    public void testRestParameterAfterRegularParam() {
        assertIntegerWithJavet(
                """
                        const fn = (first, ...rest) => first + rest.length;
                        fn(10, 1, 2, 3)""");
    }

    @Test
    public void testRestParameterEmpty() {
        assertIntegerWithJavet(
                """
                        const fn = (...args) => args.length;
                        fn()""");
    }

    @Test
    public void testRestParameterEmptyInArrowFunction() {
        assertIntegerWithJavet(
                """
                        const fn = (...args) => args.length;
                        fn()""");
    }

    @Test
    public void testRestParameterOnlyInArrowFunction() {
        assertIntegerWithJavet(
                """
                        const sum = (...nums) => nums.reduce((a, b) => a + b, 0);
                        sum(1, 2, 3, 4, 5)""");
    }

    @Test
    public void testRestParameterWithArrayMethods() {
        assertStringWithJavet(
                """
                        const join = (...args) => args.join('-');
                        join('a', 'b', 'c', 'd')""");
    }

    @Test
    public void testRestParameterWithArrowFunction() {
        assertIntegerWithJavet(
                """
                        const sum = (...args) => args.reduce((a, b) => a + b, 0);
                        sum(1, 2, 3)""");
    }

    @Test
    public void testRestParameterWithDestructuring() {
        assertIntegerWithJavet(
                """
                        const fn = (a, ...rest) => {
                            const [b, c] = rest;
                            return a + b + c;
                        };
                        fn(1, 2, 3)""");
    }

    @Test
    public void testRestParameterWithSpreadInCall() {
        assertStringWithJavet(
                """
                        const fn = (...args) => JSON.stringify(args);
                        fn(...[1, 2], 3, ...[4, 5])""");
    }

    @Test
    public void testSimpleRestParameter() {
        assertIntegerWithJavet(
                """
                        function sum(...args) {
                            return args.reduce((a, b) => a + b, 0);
                        }
                        sum(1, 2, 3)""");
    }

    @Test
    public void testSpreadInFunctionCallWithRestParameter() {
        assertIntegerWithJavet(
                """
                        const sum = (...args) => args.reduce((a, b) => a + b, 0);
                        sum(...[1, 2, 3, 4, 5])""");
    }

    @Test
    public void testSpreadInNestedFunctionCalls() {
        assertIntegerWithJavet(
                """
                        function add(a, b) {
                            return a + b;
                        }
                        function multiply(x, y) {
                            return x * y;
                        }
                        multiply(...[add(...[2, 3]), add(...[4, 6])])""");
    }

    @Test
    public void testSpreadPreservesArrayHoles() {
        assertBooleanWithJavet(
                """
                        const arr = [1, , 3];
                        const spread = [...arr];
                        spread.length === 3""",
                """
                        const arr = [1, , 3];
                        const spread = [...arr];
                        spread[1] === undefined""");
    }

    @Test
    public void testSpreadWithArrayLikeObject() {
        assertStringWithJavet(
                """
                        const arrayLike = { 0: 'a', 1: 'b', 2: 'c', length: 3 };
                        JSON.stringify([...Array.from(arrayLike)])""");
    }

    @Test
    public void testSpreadWithArrowFunction() {
        assertIntegerWithJavet(
                """
                        const sum = (...args) => args.reduce((a, b) => a + b, 0);
                        sum(...[1, 2, 3, 4, 5])""");
    }

    @Test
    public void testSpreadWithComplexExpressions() {
        assertStringWithJavet(
                """
                        const a = [1, 2];
                        const b = [3, 4];
                        JSON.stringify([0, ...(a.length > 0 ? a : b), 5])""");
    }

    @Test
    public void testSpreadWithDestructuring() {
        assertIntegerWithJavet(
                "const [a, ...rest] = [...[1, 2, 3, 4]]; rest.length");
    }

    @Test
    public void testSpreadWithIterator() {
        assertStringWithJavet(
                """
                        function* gen() {
                            yield 1;
                            yield 2;
                            yield 3;
                        }
                        JSON.stringify([...gen()])""");
        assertBooleanWithJavet(
                """
                        function* gen() {
                            yield 1;
                        }
                        const g = gen();
                        typeof g[Symbol.iterator] === 'function'""",
                """
                        function* gen() {
                            yield 1;
                        }
                        const g = gen();
                        g[Symbol.iterator]() === g""");
    }

    @Test
    public void testStringFromCharCodeWithSpread() {
        assertStringWithJavet(
                "String.fromCharCode(...[72, 101, 108, 108, 111])");
    }
}
