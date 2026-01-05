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
 * Tests for the ECMAScript arguments object.
 * The arguments object is an array-like object accessible inside functions
 * that contains the values of the arguments passed to that function.
 */
public class JSArgumentsTest extends BaseJavetTest {

    @Test
    public void testArgumentsAccess() {
        assertIntegerWithJavet("""
                function test(a, b) {
                    return arguments[0] + arguments[1] + arguments[2];
                }
                test(10, 20, 30);""");
    }

    @Test
    public void testArgumentsArrayLike() {
        assertIntegerWithJavet("""
                function test() {
                    let sum = 0;
                    for (let i = 0; i < arguments.length; i++) {
                        sum += arguments[i];
                    }
                    return sum;
                }
                test(1, 2, 3, 4, 5);""");
    }

    @Test
    public void testArgumentsCalleeInNonStrictMode() {
        // In non-strict mode, arguments.callee references the function
        assertIntegerWithJavet("""
                function factorial(n) {
                    if (n <= 1) return 1;
                    // arguments.callee is a reference to factorial itself
                    return n * arguments.callee(n - 1);
                }
                factorial(5);""");
    }

    @Test
    public void testArgumentsCalleeInStrictMode() {
        // In strict mode, accessing arguments.callee throws TypeError
        assertBooleanWithJavet("""
                function test() {
                    'use strict';
                    try {
                        return arguments.callee;
                    } catch (e) {
                        return e instanceof TypeError;
                    }
                }
                test();""");
    }

    @Test
    public void testArgumentsCalleeIsFunction() {
        // Verify that callee is indeed the function
        assertBooleanWithJavet("""
                function test() {
                    return typeof arguments.callee === 'function';
                }
                test();""");
    }

    @Test
    public void testArgumentsCallerInStrictMode() {
        assertObjectWithJavet("""
                function test() {
                    'use strict';
                    try {
                        return typeof arguments.caller;
                    } catch (e) {
                        return e.message;
                    }
                }
                test();""");
    }

    @Test
    public void testArgumentsFewerThanParameters() {
        assertIntegerWithJavet("""
                function test(a, b, c, d) {
                    return arguments.length;
                }
                test(1, 2);""");
    }

    @Test
    public void testArgumentsInMethodContext() {
        assertIntegerWithJavet("""
                const obj = {
                    method: function(a, b, c) {
                        return arguments.length;
                    }
                };
                obj.method(1, 2, 3, 4, 5);""");
    }

    @Test
    public void testArgumentsInNestedFunction() {
        assertIntegerWithJavet("""
                function outer(a, b) {
                    function inner(x, y) {
                        return arguments.length;
                    }
                    return inner(1, 2, 3);
                }
                outer(10, 20);""");
    }

    @Test
    public void testArgumentsLength() {
        assertIntegerWithJavet("""
                function test(a, b) {
                    return arguments.length;
                }
                test(1, 2, 3, 4);""");
    }

    @Test
    public void testArgumentsModification() {
        assertIntegerWithJavet("""
                function test(a, b) {
                    arguments[0] = 100;
                    return arguments[0];
                }
                test(1, 2);
                """);
    }

    @Test
    public void testArgumentsMoreThanParameters() {
        assertIntegerWithJavet("""
                function test(a, b) {
                    return arguments.length;
                }
                test(1, 2, 3, 4, 5);
                """);
    }

    @Test
    public void testArgumentsNoParameters() {
        assertIntegerWithJavet("""
                function test() {
                    return arguments.length;
                }
                test(1, 2, 3);
                """);
    }

    @Test
    public void testArgumentsNotInArrowFunction() {
        // Arrow functions don't have their own arguments object
        // They inherit arguments from enclosing scope
        assertIntegerWithJavet("""
                function outer(a, b) {
                    const arrow = () => {
                        return arguments.length;
                    };
                    return arrow();
                }
                outer(1, 2, 3);""");
    }

    @Test
    public void testArgumentsStrictModeErrorMessage() {
        // Verify the error message contains appropriate information
        assertStringWithJavet("""
                function test() {
                    'use strict';
                    try {
                        return arguments.callee;
                    } catch (e) {
                        return e.message;
                    }
                }
                test();""");
    }

    @Test
    public void testArgumentsWithRestParameters() {
        assertIntegerWithJavet("""
                function test(a, ...rest) {
                    // arguments includes all arguments, not just rest
                    return arguments.length;
                }
                test(1, 2, 3, 4);""");
    }

    @Test
    public void testRestParameterAccess() {
        assertIntegerWithJavet("""
                function test(a, ...rest) {
                    return rest[0] + rest[1];
                }
                test(10, 20, 30);""");
    }

    @Test
    public void testRestParameterAndArguments() {
        assertBooleanWithJavet("""
                function test(a, ...rest) {
                    // arguments contains all args, rest contains only rest args
                    return arguments.length === 4 && rest.length === 3;
                }
                test(1, 2, 3, 4);""");
    }

    @Test
    public void testRestParameterBasic() {
        assertIntegerWithJavet("""
                function test(a, b, ...rest) {
                    return rest.length;
                }
                test(1, 2, 3, 4, 5);""");
    }

    @Test
    public void testRestParameterEmpty() {
        assertIntegerWithJavet("""
                function test(a, b, ...rest) {
                    return rest.length;
                }
                test(1, 2);""");
    }

    @Test
    public void testRestParameterIsArray() {
        assertBooleanWithJavet("""
                function test(...rest) {
                    return Array.isArray(rest);
                }
                test(1, 2, 3);""");
    }

    @Test
    public void testRestParameterModification() {
        assertIntegerWithJavet("""
                function test(...rest) {
                    rest[0] = 100;
                    return rest[0];
                }
                test(1, 2, 3);""");
    }

    @Test
    public void testRestParameterOnlyParameter() {
        assertIntegerWithJavet("""
                function test(...args) {
                    return args.length;
                }
                test(1, 2, 3, 4, 5);""");
    }

    @Test
    public void testRestParameterSpread() {
        assertIntegerWithJavet("""
                function sum(...numbers) {
                    let total = 0;
                    for (let num of numbers) {
                        total += num;
                    }
                    return total;
                }
                sum(1, 2, 3, 4, 5);""");
    }

    @Test
    public void testRestParameterWithMethods() {
        assertIntegerWithJavet("""
                function test(...numbers) {
                    return numbers.reduce((a, b) => a + b, 0);
                }
                test(1, 2, 3, 4, 5);""");
    }
}
