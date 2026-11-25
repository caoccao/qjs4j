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
 * ES2024 15.2.5: a named function expression's own name is bound in an environment that <em>wraps</em> the function's
 * variable environment. A declaration of the same name inside the body therefore creates a different binding that
 * shadows it, and that binding starts out undefined rather than holding the function.
 * <p>
 * The compiler used to give both bindings one local slot, which the frame pre-loaded with the function object, so the
 * shadowing declaration observed the function instead. Suppressing the self-name slot entirely fixed that half and
 * broke the other: default-parameter initializers are evaluated in the parameter environment, which sits
 * <em>inside</em> the function-name environment and outside the body's variable environment, so they must still see the
 * function.
 */
public class JSNamedFunctionExpressionBindingTest extends BaseJavetTest {
    @Test
    void testABodyDeclarationStillShadowsInsideANestedFunction() {
        // The chain must not reach past a nearer binding: once the inner body is running, its own
        // declaration is what the name means.
        assertStringWithJavet(shadowedNameScript("function q(b = 1) { var q = 5; return eval('q'); }", "String(a())"));
    }

    @Test
    void testANestedArrowsOwnLetIsAssignableThroughDirectEval() {
        assertStringWithJavet(shadowedNameScript("() => { let n = 1; eval('n = 2'); return n; }", "String(a())"));
    }

    @Test
    void testANestedClosureCanDeclareAFunctionInsideDirectEval() {
        assertStringWithJavet(shadowedNameScript("""
                function () {
                  let n = 1;
                  eval('function h() { return n; }');
                  return String(typeof h);
                }""", "a()"));
    }

    @Test
    void testANestedClosureCanDeclareAVarInsideDirectEval() {
        assertStringWithJavet(
                shadowedNameScript("function () { let n = 1; eval('var m = n + 1;'); return typeof m; }", "a()"));
    }

    @Test
    void testANestedClosureCanRedeclareItsOwnVarInsideDirectEval() {
        assertStringWithJavet(
                shadowedNameScript("function () { var n = 1; eval('var n = 3;'); return String(n); }", "a()"));
    }

    @Test
    void testANestedClosuresOwnClassIsAssignableThroughDirectEval() {
        assertStringWithJavet(shadowedNameScript("function () { class n {} eval('n = 2'); return typeof n; }", "a()"));
    }

    @Test
    void testANestedClosuresOwnConstIsWhatDirectEvalReads() {
        assertStringWithJavet(shadowedNameScript("function () { const n = 1; return String(eval('n')); }", "a()"));
    }

    @Test
    void testANestedClosuresOwnFunctionDeclarationIsAssignableThroughDirectEval() {
        assertStringWithJavet(
                shadowedNameScript("function () { function n() {} eval('n = 2'); return typeof n; }", "a()"));
    }

    @Test
    void testANestedClosuresOwnLetIsAssignableThroughDirectEval() {
        // The review's reproduction. A name is not a binding identity: the closure's own `let n` is
        // a different, mutable binding from the enclosing function expression's immutable name, and
        // classifying it by spelling discarded the assignment and left n at 1.
        assertStringWithJavet("""
                var f = function n(
                  a = function () {
                    let n = 1;
                    eval('n = 2');
                    return n;
                  }
                ) {
                  var n;
                  return String(a());
                };
                f();""");
    }

    @Test
    void testANestedClosuresOwnParameterIsAssignableThroughDirectEval() {
        assertStringWithJavet(shadowedNameScript("function (n) { eval('n = 2'); return n; }", "String(a(1))"));
    }

    @Test
    void testANestedClosuresOwnVarIsAssignableThroughDirectEval() {
        assertStringWithJavet(shadowedNameScript("function () { var n = 1; eval('n = 2'); return n; }", "String(a())"));
    }

    @Test
    void testANestedGeneratorInitializerSeesTheOuterBinding() {
        assertStringWithJavet("""
                var f = function* n(a = function q(b = eval('n')) { var q; return b; }) {
                  var n;
                  yield String(a() === f);
                };
                f().next().value;""");
    }

    @Test
    void testANestedInitializerWithNoShadowStillSeesBoth() {
        assertStringWithJavet("""
                var f = function n(a = function q(b = () => [eval('n'), eval('q')]) { return b; }) {
                  var n;
                  return a;
                };
                var a = f();
                var v = a()();
                String([v[0] === f, v[1] === a]);""");
    }

    @Test
    void testANestedNamedFunctionExpressionReusingTheOuterNameShadowsIt() {
        // The nearest binding wins, and the outer one of the same name is simply not visible.
        assertStringWithJavet(
                shadowedNameScript("function n(b = eval('n')) { var n; return b; }", "String(a() === a)"));
    }

    @Test
    void testANestedNamedFunctionExpressionReusingTheOuterNameWithoutABodyShadow() {
        assertStringWithJavet(shadowedNameScript("function n(b = eval('n')) { return b; }", "String(a() === a)"));
    }

    @Test
    void testAnInnerInitializerCanReadTheOuterBindingDirectly() {
        assertStringWithJavet(
                shadowedNameScript("function q(b = eval('n')) { var q; return b; }", "String(a() === f)"));
    }

    @Test
    void testAnInnerInitializerSeesItsOwnBindingByTypeof() {
        assertStringWithJavet(shadowedNameScript("function q(b = eval('typeof q')) { var q; return b; }", "a()"));
    }

    @Test
    void testAPlainFunctionClosureInsideNestedInitializersSeesBothBindings() {
        assertStringWithJavet("""
                var f = function n(a = function q(b = function () { return [eval('n'), eval('q')]; }) {
                  var q;
                  return b;
                }) {
                  var n;
                  return a;
                };
                var a = f();
                var v = a()();
                String([v[0] === f, v[1] === a]);""");
    }

    @Test
    void testAssigningToAnInnerNestedBindingThroughEvalIsRefused() {
        assertStringWithJavet(
                shadowedNameScript("function q(b = (eval('q = 1'), eval('typeof q'))) { var q; return b; }", "a()"));
    }

    @Test
    void testAssigningToAnOuterNestedBindingThroughEvalIsRefused() {
        assertStringWithJavet(shadowedNameScript("function q(b = (eval('n = 1'), eval('n'))) { var q; return b; }",
                "String(a() === f)"));
    }

    @Test
    void testAssigningToTheSelfNameThroughDirectEvalIsRefused() {
        // The binding is immutable, so a sloppy assignment is ignored and a strict one throws. The
        // shadowing body binding must not receive the value either.
        assertStringWithJavet(shadowedNameScript("(eval('n = 1'), eval('n'))", "String(a === f)"));
    }

    @Test
    void testClassDeclarationShadowsSelfName() {
        assertStringWithJavet("""
                var probe;
                var f = function n() {
                  class n {}
                  probe = function () { return typeof n; };
                };
                f();
                probe();""");
    }

    @Test
    void testClassDeclarationShadowsSelfNameButNotDefaultParameters() {
        assertStringWithJavet("""
                var f = function n(a = n, b = typeof n) {
                  class n {}
                  return [a === f, b, typeof n].join(',');
                };
                f();""");
    }

    @Test
    void testDefaultParameterClosureCapturesTheSelfName() {
        // The closure is created in the parameter environment, so it keeps the function-name
        // binding even though the body rebinds the name in a slot of its own.
        assertStringWithJavet("""
                var f = function n(a = function () { return n; }) {
                  var n = 'body';
                  return [a() === f, n].join(',');
                };
                f();""");
    }

    @Test
    void testDefaultParameterSeesSelfNameWhenNothingShadowsIt() {
        assertStringWithJavet("""
                var f = function n(a = n) { return a === f; };
                String(f());""");
    }

    @Test
    void testDirectEvalInADefaultParameterOfANestedNamedFunctionSeesItsOwnName() {
        assertStringWithJavet(shadowedNameScript("function q(b = eval('q')) { var q; return typeof b; }", "a()"));
    }

    @Test
    void testDirectEvalInADefaultParameterOfANestedNamedFunctionStillSeesTheOuterName() {
        assertStringWithJavet(
                shadowedNameScript("function q(b = eval('n')) { var q; return b; }", "String(a() === f)"));
    }

    @Test
    void testDirectEvalInADefaultParameterSeesTheSelfNameWhenAClassShadowsIt() {
        assertStringWithJavet("""
                var f = function n(a = eval('n')) {
                  class n {}
                  return String(a === f);
                };
                f();""");
    }

    @Test
    void testDirectEvalInADefaultParameterSeesTheSelfNameWhenAConstShadowsIt() {
        assertStringWithJavet("""
                var f = function n(a = eval('n')) {
                  const n = 1;
                  return String(a === f);
                };
                f();""");
    }

    @Test
    void testDirectEvalInADefaultParameterSeesTheSelfNameWhenAFunctionShadowsIt() {
        assertStringWithJavet("""
                var f = function n(a = eval('n')) {
                  function n() {}
                  return String(a === f);
                };
                f();""");
    }

    @Test
    void testDirectEvalInADefaultParameterSeesTheSelfNameWhenALetShadowsIt() {
        assertStringWithJavet("""
                var f = function n(a = eval('n')) {
                  let n;
                  return String(a === f);
                };
                f();""");
    }

    @Test
    void testDirectEvalInADefaultParameterSeesTheSelfNameWhenAVarShadowsIt() {
        // Compiled references resolve to the right slot by index. Direct eval resolves by name at
        // run time, and the name mapped to the body's slot from the moment the body declared it —
        // so eval("n") in a default initializer saw undefined instead of the function.
        assertStringWithJavet(shadowedNameScript("eval('n')", "String(a === f)"));
    }

    @Test
    void testDirectEvalInADefaultParameterSeesTheSelfNameWhenNothingShadowsIt() {
        assertStringWithJavet("""
                var f = function n(a = eval('n')) {
                  return String(a === f);
                };
                f();""");
    }

    @Test
    void testDirectEvalInADestructuringDefaultSeesTheSelfName() {
        assertStringWithJavet("""
                var f = function n({x = eval('n')} = {}) {
                  var n;
                  return String(x === f);
                };
                f();""");
    }

    @Test
    void testDirectEvalInADoublyNestedClosureFromADefaultParameterKeepsTheSelfName() {
        assertStringWithJavet(shadowedNameScript("() => () => eval('n')", "String(a()() === f)"));
    }

    @Test
    void testDirectEvalInAFunctionCreatedByADefaultParameterKeepsTheSelfName() {
        assertStringWithJavet("""
                var f = function n(a = function () { return eval('n'); }) {
                  var n = 5;
                  return String(a() === f);
                };
                f();""");
    }

    @Test
    void testDirectEvalInAGeneratorDefaultParameterSeesTheSelfName() {
        assertStringWithJavet("""
                var f = function* n(a = eval('n')) {
                  var n;
                  yield String(a === f);
                };
                f().next().value;""");
    }

    @Test
    void testDirectEvalInAnArrowCreatedByADefaultParameterKeepsTheSelfName() {
        // The arrow's environment is the parameter environment, so calling it from the body — where
        // the name means something else — does not change what its eval resolves.
        assertStringWithJavet("""
                var f = function n(a = () => eval('n')) {
                  let n;
                  return String(a() === f);
                };
                f();""");
    }

    @Test
    void testDirectEvalInTheBodySeesAnUninitializedShadowingVar() {
        assertStringWithJavet(shadowedNameScript("1", "String(eval('typeof n'))"));
    }

    @Test
    void testDirectEvalInTheBodySeesTheBodyDeclaration() {
        // The other half of the same rule: once the body is running, the name is the body's.
        assertStringWithJavet("""
                var f = function n(a = 1) {
                  var n = 7;
                  return String(eval('n'));
                };
                f();""");
    }

    @Test
    void testFunctionDeclarationShadowsSelfNameButNotDefaultParameters() {
        assertStringWithJavet("""
                var f = function n(a = n) {
                  function n() { return 'inner'; }
                  return [a === f, n()].join(',');
                };
                f();""");
    }

    @Test
    void testInnerFunctionDeclarationShadowsSelfName() {
        assertStringWithJavet("""
                var probe;
                var f = function n() {
                  function n() { return 'inner'; }
                  probe = function () { return n(); };
                };
                f();
                probe();""");
    }

    @Test
    void testLetDeclarationShadowsSelfName() {
        assertStringWithJavet("""
                var probe;
                var f = function n() {
                  let n = 'shadow';
                  probe = function () { return n; };
                };
                f();
                probe();""");
    }

    @Test
    void testLetDeclarationShadowsSelfNameButNotDefaultParameters() {
        assertStringWithJavet("""
                var f = function n(a = n) {
                  let n = 'body';
                  return [a === f, n].join(',');
                };
                f();""");
    }

    @Test
    void testParameterOfTheSameNameShadowsTheSelfNameBinding() {
        // A parameter binding is in the parameter environment itself, so it wins over the
        // function-name environment for every initializer that follows it.
        assertStringWithJavet("""
                var f = function n(n, a = n) { return [n, a].join(','); };
                f('parameter');""");
    }

    @Test
    void testSelfNameIsVisibleWhenNotShadowed() {
        assertStringWithJavet("""
                var probe;
                var f = function n() {
                  probe = function () { return typeof n; };
                };
                f();
                probe();""");
    }

    @Test
    void testSelfNameStaysImmutableInsideDefaultParameters() {
        assertStringWithJavet(shadowedNameScript("""
                (function () {
                  try { n = 1; } catch (e) { return e.constructor.name; }
                  return typeof n;
                })()""", "[a, typeof n].join(',')"));
    }

    @Test
    void testSelfNameStaysImmutableWhenNotShadowed() {
        // Assigning to the name binding is silently ignored in sloppy mode and a TypeError in
        // strict mode; either way the binding still holds the function.
        assertStringWithJavet("""
                var f = function n() {
                  try { n = 1; } catch (e) { return 'TypeError'; }
                  return typeof n;
                };
                f();""");
    }

    @Test
    void testShadowingBodyBindingIsMutableUnlikeTheSelfName() {
        // The self-name binding is immutable; the binding a body declaration creates is an ordinary
        // one, so the two must not share the const/function-name marking either.
        assertStringWithJavet("""
                var f = function n(a = n) {
                  var n = 'first';
                  n = 'assigned';
                  return [a === f, n].join(',');
                };
                f();""");
    }

    @Test
    void testTheInnermostOfTwoNestedInitializersShadowsWithItsOwnBinding() {
        // Two nested parameter environments, and a closure inside the inner one that binds the
        // outer environment's name itself. Its own binding must win, while the inner environment's
        // name — which it does not bind — is still the immutable function.
        assertStringWithJavet("""
                var f = function n(a = function m(b = function () {
                  let n = 5;
                  eval('n = 6');
                  return [n, typeof m];
                }) {
                  var m;
                  return b;
                }) {
                  var n;
                  return a;
                };
                String(f()()());""");
    }

    @Test
    void testThreeNestedNamedFunctionExpressionsKeepAllThreeBindings() {
        assertStringWithJavet("""
                var f = function n(a = function q(b = function r(c = () => [eval('n'), eval('q'), eval('r')]) {
                  var r;
                  return c;
                }) {
                  var q;
                  return b;
                }) {
                  var n;
                  return a;
                };
                var a = f();
                var b = a();
                var c = b();
                var v = c();
                String([v[0] === f, v[1] === a, v[2] === b]);""");
    }

    @Test
    void testTwoNestedNamedFunctionExpressionsKeepBothBindings() {
        // The review's reproduction. One slot could only remember the innermost name, so the outer
        // function-expression binding disappeared from everything compiled inside the inner
        // initializer — a closure there saw `undefined`, or a global of the same name.
        assertStringWithJavet("""
                var f = function n(a = function q(b = () => [eval('n'), eval('q')]) {
                  var q;
                  return b;
                }) {
                  var n;
                  return [a(), a];
                };
                var r = f();
                var v = r[0]();
                String([v[0] === f, v[1] === r[1]]);""");
    }

    @Test
    void testUninitializedVarShadowsSelfName() {
        // The reproducer from test262 language/expressions/call/scope-var-open.js. `var n;` with no
        // initializer creates a fresh binding, so the probe must see undefined, not the function.
        assertStringWithJavet("""
                var probe;
                var f = function n() {
                  var n;
                  probe = function () { return typeof n; };
                };
                f();
                probe();""");
    }

    @Test
    void testVarDeclarationShadowsSelfNameButNotDefaultParameters() {
        // The reproducer from the review: valid in every conforming engine, a ReferenceError here.
        assertStringWithJavet(shadowedNameScript("n", "[a === f, typeof n].join(',')"));
    }

    @Test
    void testVarWithInitializerShadowsSelfName() {
        assertStringWithJavet("""
                var probe;
                var f = function n() {
                  var n = 'shadow';
                  probe = function () { return n; };
                };
                f();
                probe();""");
    }

    private static String shadowedNameScript(String initializer, String resultExpression) {
        return """
                var f = function n(a = %s) {
                  var n;
                  return %s;
                };
                f();""".formatted(initializer, resultExpression);
    }
}
