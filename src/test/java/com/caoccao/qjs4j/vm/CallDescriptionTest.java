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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * "X is not a function" and "X is not a constructor" name the callee as V8 does.
 * <p>
 * The name used to be rebuilt at run time from the property reads that preceded the call. Every read extended that
 * chain, so a loop reading {@code arr.length} through a local grew it without bound — 3e7 iterations exhausted a 1 GB
 * heap — and it named the wrong expression whenever a captured variable, a computed key or a call came in between:
 * {@code a.b.c} for {@code o.a.b.c()}, {@code [Symbol.Symbol.iterator]} for {@code o[Symbol.iterator]()}. The compiler
 * now records each call's callee name, so property reads do no bookkeeping at all.
 */
public class CallDescriptionTest extends BaseJavetTest {

    @Test
    public void testCallees() {
        assertStringWithJavet(
                """
                        const attempt = action => {
                          try {
                            action();
                            return 'no error';
                          } catch (e) {
                            return e.message;
                          }
                        };
                        const o = { a: { b: {} }, list: [{}], 'a b': 1, 3: 0, s: 'str', n: null, f() { return o; } };
                        const sym = Symbol('custom');
                        const k = 'a';
                        let u;
                        const n = 5;
                        [
                          attempt(() => o.a.b.c()), attempt(() => o['a b']()), attempt(() => o[3]()), attempt(() => o[k].b()),
                          attempt(() => o[sym]()), attempt(() => o[Symbol.iterator]()), attempt(() => o.f().a.b()),
                          attempt(() => o.f(1, 2)()), attempt(() => (o.a)()), attempt(() => o?.a.b()), attempt(() => o.a?.b()),
                          attempt(() => o.a?.b?.()), attempt(() => o?.a?.[k]()), attempt(() => u()), attempt(() => n()),
                          attempt(() => o.s()), attempt(() => o.n()), attempt(() => (() => 1)()()), attempt(() => o.list[0]()),
                          attempt(() => o.a['b']()), attempt(() => `x`()), attempt(() => 'y'()), attempt(() => (5)()),
                          attempt(() => o.a[`b`]()), attempt(() => o.a.b.c(...[1])), attempt(() => this.x()),
                          attempt(() => globalThis.nope()), attempt(() => o[0.5]()), attempt(() => o[-1]()), attempt(() => o[true]()),
                          attempt(() => o[null]()), attempt(() => o['0']()),
                        ].join('\\n')""");
    }

    @Test
    public void testCalleesInFunctionsAndEval() {
        // Captured, local and eval-scoped bindings are named like any other.
        assertStringWithJavet("""
                function f() {
                  const o = { a: { b: 1 } };
                  let n = 0;
                  for (let i = 0; i < 3; i++) n += o.a.b;
                  try { o.a.c() } catch (e) { return e.message }
                }
                f()""", """
                function g(o) { o.x; o.y; try { o.z() } catch (e) { return e.message } }
                g({})""", """
                function k() { const s = 'x'; try { s.foo() } catch (e) { return e.message } }
                k()""", """
                class C {
                  constructor() { this.helper = {} }
                  run() { try { this.helper.go() } catch (e) { return e.message } }
                  #hidden = 1;
                  runPrivate() { try { this.#hidden() } catch (e) { return e.message } }
                }
                new C().run() + ' | ' + new C().runPrivate()""", """
                function h() { const x = { b: {} }; const y = x; try { y.b.zz() } catch (e) { return e.message } }
                h()""", """
                eval(`
                  const o = { a: {} };
                  const call = () => { try { o.a.b() } catch (e) { return e.message } };
                  call();
                `)""");
    }

    @Test
    public void testConstructors() {
        assertStringWithJavet("""
                const attempt = action => {
                  try {
                    action();
                    return 'no error';
                  } catch (e) {
                    return e.message;
                  }
                };
                const o = { a: { b: {} }, list: [{}], 'a b': 1, 3: 0, s: 'str', n: null, f() { return o; } };
                const sym = Symbol('custom');
                const k = 'a';
                let u;
                const n = 5;
                const plain = { m() {}, arrow: () => 1 };
                const arrow = () => 1;
                [
                  attempt(() => new o.a()), attempt(() => new n()), attempt(() => new plain.m()),
                  attempt(() => new plain.arrow()), attempt(() => new arrow()), attempt(() => new Math.max()),
                  attempt(() => new (class {}.x)()), attempt(() => new 'abc'()), attempt(() => new o.a(...[1])),
                  attempt(() => new u()),
                ].join('\\n')""");
    }

    @Test
    public void testUnaryKeysAndTaggedTemplates() {
        assertStringWithJavet(
                """
                        const attempt = action => {
                          try {
                            action();
                            return 'no error';
                          } catch (e) {
                            return e.message;
                          }
                        };
                        const o = { a: { b: {} }, list: [{}], 'a b': 1, 3: 0, s: 'str', n: null, f() { return o; } };
                        const sym = Symbol('custom');
                        const k = 'a';
                        let u;
                        const n = 5;
                              const x = 1;
                              const tag = {};
                              [
                                attempt(() => o[-x]()), attempt(() => o[+1]()), attempt(() => o[-'1']()), attempt(() => o[typeof x]()),
                                attempt(() => o[void 0]()), attempt(() => o[~x]()), attempt(() => o[!x]()), attempt(() => o[+x]()),
                                attempt(() => tag`x`), attempt(() => ({})`x`), attempt(() => o.a`x`), attempt(() => o[++o.c]()),
                        attempt(() => o[o.c--]()), attempt(() => o[delete o.q]()), attempt(() => ({ a: 1 }).x()),
                        attempt(() => o[`a${k}b`]()), attempt(() => o[`a${k}b${k}`]()),
                              ].join('\\n')""");
    }
}
