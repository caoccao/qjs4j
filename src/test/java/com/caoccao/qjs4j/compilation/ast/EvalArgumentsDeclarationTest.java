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
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test cases for EvalDeclarationInstantiation checks and generator initial execution.
 * <p>
 * Per ES spec, eval("var arguments") inside functions with non-simple parameters
 * (default values, rest, or destructuring) must throw SyntaxError since 'arguments'
 * is already bound in the parameter scope (following QuickJS add_arguments_arg behavior).
 * <p>
 * Note: V8 does not implement this spec requirement in non-strict mode, so these
 * eval("var arguments") tests use direct qjs4j assertions instead of assertErrorWithJavet.
 * <p>
 * Generator functions must evaluate parameter defaults during the function call
 * (up to INITIAL_YIELD), not deferred to .next(). These tests use assertWithJavet.
 */
public class EvalArgumentsDeclarationTest extends BaseJavetTest {

    // === eval("var arguments") SyntaxError in functions with parameter expressions ===
    // These follow the ES spec EvalDeclarationInstantiation check and QuickJS behavior.

    @Test
    public void testAsyncGeneratorDefaultParamEvaluatedDuringCall() {
        // Async generator: default parameter side-effects happen during function call
        assertIntegerWithJavet(
                """
                        var x = 0;
                        async function* g(p = (x = 42)) { yield p; }
                        g();
                        x""");
    }

    @Test
    public void testEvalVarArgumentsInArrowWithDefaultParam() {
        // Arrow functions don't have their own 'arguments' binding
        // so eval("var arguments") is allowed even with default params
        resetContext().eval("((p = 1) => { eval('var arguments'); })()");
        // Should not throw
    }

    @Test
    public void testEvalVarArgumentsInFunctionWithDefaultParam() {
        assertThatThrownBy(() -> resetContext().eval(
                "(function(p = 1) { eval('var arguments'); })()"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    @Test
    public void testEvalVarArgumentsInFunctionWithDefaultParamUsingOtherParam() {
        assertThatThrownBy(() -> resetContext().eval(
                "(function(a, b = a) { eval('var arguments'); })(1)"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    @Test
    public void testEvalVarArgumentsInFunctionWithDestructuringParam() {
        assertThatThrownBy(() -> resetContext().eval(
                "(function({x}) { eval('var arguments'); })({x: 1})"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    @Test
    public void testEvalVarArgumentsInFunctionWithMultipleDefaults() {
        assertThatThrownBy(() -> resetContext().eval(
                "(function(a = 1, b = 2) { eval('var arguments'); })()"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    @Test
    public void testEvalVarArgumentsInFunctionWithRestParam() {
        assertThatThrownBy(() -> resetContext().eval(
                "(function(...rest) { eval('var arguments'); })()"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    // === Positive cases: eval("var arguments") should NOT throw ===

    @Test
    public void testEvalVarArgumentsInFunctionWithSimpleParams() {
        // Function with only simple parameters - no parameter expressions
        // eval("var arguments") is allowed (no pre-existing arguments binding conflict)
        resetContext().eval("(function(a) { eval('var arguments'); })()");
        // Should not throw
    }

    @Test
    public void testEvalVarArgumentsInGeneratorWithDefaultParam() {
        assertThatThrownBy(() -> resetContext().eval(
                "(function*(p = 1) { eval('var arguments'); })().next()"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    // === Generator initial execution: parameter defaults evaluated during function call ===

    @Test
    public void testEvalVarArgumentsInNamedFunctionWithDefaultParam() {
        assertThatThrownBy(() -> resetContext().eval(
                "(function f(p = 1) { eval('var arguments'); })()"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    @Test
    public void testGeneratorDefaultParamEvaluatedDuringCall() {
        // Default parameter side-effects should happen during the function call, not during .next()
        assertIntegerWithJavet(
                """
                        var x = 0;
                        function* g(p = (x = 42)) { yield p; }
                        g();
                        x""");
    }

    @Test
    public void testGeneratorDefaultParamOverriddenByArgument() {
        // Explicit argument overrides default parameter
        assertIntegerWithJavet(
                """
                        function* g(x = 99) { yield x; }
                        g(7).next().value""");
    }

    @Test
    public void testGeneratorDefaultParamThrowsDuringCall() {
        // If default parameter evaluation throws, error propagates from the function call
        assertBooleanWithJavet(
                """
                        var threw = false;
                        try {
                            (function*(x = (() => { throw 1; })()) { yield x; })();
                        } catch(e) {
                            threw = true;
                        }
                        threw""");
    }

    @Test
    public void testGeneratorDefaultParamValueAvailableInBody() {
        // Default parameter value is available when yielded
        assertIntegerWithJavet(
                """
                        function* g(x = 99) { yield x; }
                        g().next().value""");
    }

    @Test
    public void testGeneratorMultipleDefaultParams() {
        // Multiple default parameters in generator
        assertIntegerWithJavet(
                """
                        function* g(a = 10, b = 20) { yield a + b; }
                        g().next().value""");
    }
}
