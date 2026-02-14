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
 * Tests for function declaration hoisting and Annex B.3.3 behavior.
 * <p>
 * Function declarations must be hoisted to the top of their scope
 * so they are available before their source position.
 */
public class FunctionHoistingTest extends BaseJavetTest {

    @Test
    public void testCallBeforeDeclaration() {
        // Function should be callable before its declaration in source order
        assertBooleanWithJavet(
                "typeof f === 'function' && f() === 42; function f() { return 42; }");
    }

    @Test
    public void testCallBeforeDeclarationReturnValue() {
        // The program result should be the return value of the hoisted function call
        assertIntegerWithJavet(
                "f(); function f() { return 42; } f()");
    }

    @Test
    public void testCompletionValueIsLastExpression() {
        // Function declarations don't contribute a completion value;
        // the last expression statement's value should be the result
        assertIntegerWithJavet(
                "42; function f() { return 1; }",
                "function f() { return 1; } 42");
    }

    @Test
    public void testHoistingWithExpressionStatementsBefore() {
        // Expressions before function declarations should be able to call the functions
        assertBooleanWithJavet(
                "var a = f(); var b = g(); a + b === 3; function f() { return 1; } function g() { return 2; }");
    }

    @Test
    public void testInsideIf() {
        assertIntegerWithJavet("""
                try {
                  throw null;
                } catch (f) {if (true) function f() { return 123; }}
                f()""");
    }

    @Test
    public void testInsideSwitchDefault() {
        assertStringWithJavet("""
                var after;
                (function() {
                  eval(
                    'switch (1) {' +
                    '  default:' +
                    '    function f() { return "function declaration"; }' +
                    '}\\
                    after = f;\\
                    \\
                    var f = 123;'
                  );
                }());
                after()""");
    }

    @Test
    public void testMultipleDeclarationsLastWins() {
        // When multiple function declarations share the same name, the last one wins
        assertBooleanWithJavet(
                "f() === 2; function f() { return 1; } function f() { return 2; }");
    }

    @Test
    public void testMultipleDeclarationsLastWinsCalledBeforeAll() {
        // Even when called before all declarations, the last declaration wins
        assertIntegerWithJavet(
                "f(); function f() { return 1; } function f() { return 2; } f()");
    }

    @Test
    public void testMutuallyRecursiveFunctions() {
        // Both functions should be available to each other due to hoisting
        assertBooleanWithJavet("""
                var r = isEven(4);
                function isEven(n) { return n === 0 ? true : isOdd(n - 1); }
                function isOdd(n) { return n === 0 ? false : isEven(n - 1); }
                r""");
    }

    @Test
    public void testTypeofBeforeDeclaration() {
        // typeof should return "function" for hoisted function declarations
        assertStringWithJavet(
                "typeof f; function f() {}");
    }
}
