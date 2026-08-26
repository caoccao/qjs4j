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
 * ES2024 15.2.5: a named function expression's own name is bound in an environment that
 * <em>wraps</em> the function's variable environment. A declaration of the same name inside the
 * body therefore creates a different binding that shadows it, and that binding starts out
 * undefined rather than holding the function.
 * <p>
 * The compiler used to give both bindings one local slot, which the frame pre-loaded with the
 * function object, so the shadowing declaration observed the function instead. Suppressing the
 * self-name slot entirely fixed that half and broke the other: default-parameter initializers are
 * evaluated in the parameter environment, which sits <em>inside</em> the function-name environment
 * and outside the body's variable environment, so they must still see the function.
 */
public class JSNamedFunctionExpressionBindingTest extends BaseJavetTest {
    @Test
    void testAssigningToTheSelfNameThroughDirectEvalIsRefused() {
        // The binding is immutable, so a sloppy assignment is ignored and a strict one throws. The
        // shadowing body binding must not receive the value either.
        assertStringWithJavet("""
                var f = function n(a = (eval('n = 1'), eval('n'))) {
                  var n;
                  return String(a === f);
                };
                f();""");
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
        assertStringWithJavet("""
                var f = function n(a = function q(b = eval('q')) { var q; return typeof b; }) {
                  var n;
                  return a();
                };
                f();""");
    }

    @Test
    void testDirectEvalInADefaultParameterOfANestedNamedFunctionStillSeesTheOuterName() {
        assertStringWithJavet("""
                var f = function n(a = function q(b = eval('n')) { var q; return b; }) {
                  var n;
                  return String(a() === f);
                };
                f();""");
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
        assertStringWithJavet("""
                var f = function n(a = eval('n')) {
                  var n;
                  return String(a === f);
                };
                f();""");
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
        assertStringWithJavet("""
                var f = function n(a = () => () => eval('n')) {
                  var n;
                  return String(a()() === f);
                };
                f();""");
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
        assertStringWithJavet("""
                var f = function n(a = 1) {
                  var n;
                  return String(eval('typeof n'));
                };
                f();""");
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
        assertStringWithJavet("""
                var f = function n(a = (function () {
                  try { n = 1; } catch (e) { return e.constructor.name; }
                  return typeof n;
                })()) {
                  var n;
                  return [a, typeof n].join(',');
                };
                f();""");
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
        assertStringWithJavet("""
                var f = function n(a = n) {
                  var n;
                  return [a === f, typeof n].join(',');
                };
                f();""");
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
}
