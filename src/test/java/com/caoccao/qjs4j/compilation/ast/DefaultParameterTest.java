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

public class DefaultParameterTest extends BaseJavetTest {

    @Test
    public void testArrowBothParamsDefault() {
        assertIntegerWithJavet("var f = (x = 10, y = 20) => x + y; f();");
    }

    @Test
    public void testArrowDefaultExpression() {
        assertIntegerWithJavet("var f = (x = 2 * 3 + 1) => x; f();");
    }

    @Test
    public void testArrowDefaultLazyEvaluation() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var counter = () => { count++; return count; };
                        var f = (x = counter()) => x;
                        f(99);
                        count;""");
    }

    @Test
    public void testArrowDefaultNotTriggeredByNull() {
        assertBooleanWithJavet("var f = (x = 42) => x === null; f(null);");
    }

    @Test
    public void testArrowDefaultWithBlockBody() {
        assertIntegerWithJavet("var f = (x = 42) => { return x; }; f();");
    }

    @Test
    public void testArrowDefaultWithFunctionCall() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var counter = () => { count++; return count; };
                        var f = (x = counter()) => x;
                        f();""");
    }

    @Test
    public void testArrowFunctionLength() {
        assertIntegerWithJavet("var f = (x, y = 10) => x + y; f.length;");
    }

    @Test
    public void testArrowMultipleParamsDefaultNotUsed() {
        assertIntegerWithJavet("var f = (x, y = 100) => x + y; f(1, 200);");
    }

    @Test
    public void testArrowMultipleParamsWithDefault() {
        assertIntegerWithJavet("var f = (x, y = 100) => x + y; f(1);");
    }

    @Test
    public void testArrowSingleParamDefaultNotUsed() {
        assertIntegerWithJavet("var f = (x = 42) => x; f(10);");
    }

    @Test
    public void testArrowSingleParamDefaultUsed() {
        assertIntegerWithJavet("var f = (x = 42) => x; f();");
    }

    @Test
    public void testArrowSingleParamDefaultWithUndefined() {
        assertIntegerWithJavet("var f = (x = 42) => x; f(undefined);");
    }

    @Test
    public void testDefaultAfterRestThrows() {
        assertErrorWithJavet("function f(...rest, x = 42) { return x; }");
    }

    @Test
    public void testDefaultParamArgumentsLengthWithValue() {
        assertIntegerWithJavet(
                """
                        function f(x = 42) { return arguments.length; }
                        f(10);""");
    }

    @Test
    public void testDefaultParamDoesNotAffectArgumentsLength() {
        assertIntegerWithJavet(
                """
                        function f(x = 42) { return arguments.length; }
                        f();""");
    }

    @Test
    public void testDefaultWithTrailingComma() {
        assertIntegerWithJavet("function f(x = 42,) { return x; } f();");
    }

    @Test
    public void testDefaultWithTrailingCommaNotUsed() {
        assertIntegerWithJavet("function f(x = 42,) { return x; } f(10);");
    }

    @Test
    public void testFunctionDeclarationDefaultBoolean() {
        assertBooleanWithJavet("function f(x = true) { return x; } f();");
    }

    @Test
    public void testFunctionDeclarationDefaultCalledEachTime() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        function counter() { count++; return count; }
                        function f(x = counter()) { return x; }
                        f(); f(); f();
                        count;""");
    }

    @Test
    public void testFunctionDeclarationDefaultExpression() {
        assertIntegerWithJavet("function f(x = 2 + 3) { return x; } f();");
    }

    @Test
    public void testFunctionDeclarationDefaultFunctionCall() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        function counter() { count++; return count; }
                        function f(x = counter()) { return x; }
                        f();""");
    }

    @Test
    public void testFunctionDeclarationDefaultLazyEvaluation() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        function counter() { count++; return count; }
                        function f(x = counter()) { return x; }
                        f(99);
                        count;""");
    }

    @Test
    public void testFunctionDeclarationDefaultNotTriggeredByEmptyString() {
        assertStringWithJavet("function f(x = 'default') { return x; } f('');");
    }

    @Test
    public void testFunctionDeclarationDefaultNotTriggeredByFalse() {
        assertBooleanWithJavet("function f(x = true) { return x; } f(false);");
    }

    @Test
    public void testFunctionDeclarationDefaultNotTriggeredByNaN() {
        assertBooleanWithJavet("function f(x = 42) { return typeof x === 'number' && isNaN(x); } f(NaN);");
    }

    @Test
    public void testFunctionDeclarationDefaultNotTriggeredByNull() {
        assertBooleanWithJavet("function f(x = 42) { return x === null; } f(null);");
    }

    @Test
    public void testFunctionDeclarationDefaultNotTriggeredByZero() {
        assertIntegerWithJavet("function f(x = 42) { return x; } f(0);");
    }

    @Test
    public void testFunctionDeclarationDefaultNotUsed() {
        assertIntegerWithJavet("function f(x = 42) { return x; } f(10);");
    }

    @Test
    public void testFunctionDeclarationDefaultReferencesEarlierParam() {
        assertIntegerWithJavet("function f(x, y = x * 2) { return y; } f(5);");
    }

    @Test
    public void testFunctionDeclarationDefaultString() {
        assertStringWithJavet("function f(x = 'hello') { return x; } f();");
    }

    @Test
    public void testFunctionDeclarationDefaultUsed() {
        assertIntegerWithJavet("function f(x = 42) { return x; } f();");
    }

    @Test
    public void testFunctionDeclarationDefaultUsedInBody() {
        assertIntegerWithJavet(
                """
                        function f(x = 10) {
                            var y = x + 5;
                            return y;
                        }
                        f();""");
    }

    @Test
    public void testFunctionDeclarationDefaultWithArrayLiteral() {
        assertIntegerWithJavet("function f(x = [1, 2, 3]) { return x[1]; } f();");
    }

    @Test
    public void testFunctionDeclarationDefaultWithClosureCapture() {
        assertIntegerWithJavet(
                """
                        var val = 42;
                        function f(x = val) { return x; }
                        f();""");
    }

    @Test
    public void testFunctionDeclarationDefaultWithCommaOperator() {
        assertIntegerWithJavet("function f(x = (1, 2, 3)) { return x; } f();");
    }

    @Test
    public void testFunctionDeclarationDefaultWithObjectLiteral() {
        assertIntegerWithJavet("function f(x = {a: 42}) { return x.a; } f();");
    }

    @Test
    public void testFunctionDeclarationDefaultWithTernary() {
        assertIntegerWithJavet("function f(x = true ? 42 : 0) { return x; } f();");
    }

    @Test
    public void testFunctionDeclarationDefaultWithUndefined() {
        assertIntegerWithJavet("function f(x = 42) { return x; } f(undefined);");
    }

    @Test
    public void testFunctionDeclarationMixedDefaultAndNonDefault() {
        assertIntegerWithJavet("function f(a, b = 10, c = 20) { return a + b + c; } f(1);");
    }

    @Test
    public void testFunctionDeclarationMixedDefaultPartial() {
        assertIntegerWithJavet("function f(a, b = 10, c = 20) { return a + b + c; } f(1, 2);");
    }

    @Test
    public void testFunctionDeclarationMultipleDefaults() {
        assertIntegerWithJavet("function f(x = 10, y = 20) { return x + y; } f();");
    }

    @Test
    public void testFunctionDeclarationMultipleDefaultsNoneUsed() {
        assertIntegerWithJavet("function f(x = 10, y = 20) { return x + y; } f(5, 15);");
    }

    @Test
    public void testFunctionDeclarationMultipleDefaultsPartial() {
        assertIntegerWithJavet("function f(x = 10, y = 20) { return x + y; } f(5);");
    }

    @Test
    public void testFunctionDeclarationSecondParamDefault() {
        assertIntegerWithJavet("function f(x, y = 100) { return y; } f(1);");
    }

    @Test
    public void testFunctionDeclarationSecondParamDefaultNotUsed() {
        assertIntegerWithJavet("function f(x, y = 100) { return y; } f(1, 200);");
    }

    @Test
    public void testFunctionExpressionDefaultNotUsed() {
        assertIntegerWithJavet("var f = function(x = 42) { return x; }; f(10);");
    }

    @Test
    public void testFunctionExpressionDefaultUsed() {
        assertIntegerWithJavet("var f = function(x = 42) { return x; }; f();");
    }

    @Test
    public void testFunctionExpressionLength() {
        assertIntegerWithJavet("var f = function(x = 1, y = 2, z) { return z; }; f.length;");
    }

    @Test
    public void testFunctionExpressionMultipleDefaults() {
        assertIntegerWithJavet("var f = function(x = 10, y = 20) { return x + y; }; f();");
    }

    @Test
    public void testFunctionExpressionNamedWithDefault() {
        assertIntegerWithJavet("var f = function foo(x = 42) { return x; }; f();");
    }

    @Test
    public void testFunctionLengthAllDefaults() {
        assertIntegerWithJavet("function f(x = 1, y = 2) {} f.length;");
    }

    @Test
    public void testFunctionLengthMixedDefaultsMiddle() {
        assertIntegerWithJavet("function f(a, b = 10, c) {} f.length;");
    }

    @Test
    public void testFunctionLengthNoDefaults() {
        assertIntegerWithJavet("function f(x, y) {} f.length;");
    }

    @Test
    public void testFunctionLengthWithOneDefault() {
        assertIntegerWithJavet("function f(x = 42) {} f.length;");
    }

    @Test
    public void testFunctionLengthWithSecondDefault() {
        assertIntegerWithJavet("function f(x, y = 10) {} f.length;");
    }

    @Test
    public void testMethodDefaultParam() {
        assertIntegerWithJavet(
                """
                        var obj = {
                            add(x, y = 10) { return x + y; }
                        };
                        obj.add(5);""");
    }

    @Test
    public void testMethodDefaultParamNotUsed() {
        assertIntegerWithJavet(
                """
                        var obj = {
                            add(x, y = 10) { return x + y; }
                        };
                        obj.add(5, 20);""");
    }

    @Test
    public void testMultipleCallsCounterDefault() {
        assertStringWithJavet(
                """
                        var count = 0;
                        function counter() { return ++count; }
                        function f(x = counter()) { return x; }
                        '' + f() + ',' + f() + ',' + f(99);""");
    }

    @Test
    public void testMultipleCallsSameDefault() {
        assertStringWithJavet(
                """
                        function f(x = 0) { return x; }
                        '' + f() + ',' + f(1) + ',' + f() + ',' + f(2);""");
    }
}
