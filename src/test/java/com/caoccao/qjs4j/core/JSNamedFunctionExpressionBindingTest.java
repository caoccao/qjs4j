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
 * function object, so the shadowing declaration observed the function instead.
 */
public class JSNamedFunctionExpressionBindingTest extends BaseJavetTest {
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
