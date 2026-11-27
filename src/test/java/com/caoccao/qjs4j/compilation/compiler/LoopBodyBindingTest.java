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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * A block-scoped binding declared inside a loop body is a new binding on every iteration.
 * <p>
 * Only a loop head's bindings were renewed between iterations, so every {@code let}, {@code const}, class or catch
 * parameter in a loop body was one binding for the whole loop, and closures created on different iterations shared it:
 * {@code for (let i = 0; i < 3; i++) { const k = i; fs.push(() => k) }} returned 2, 2, 2.
 */
public class LoopBodyBindingTest extends BaseJavetTest {
    @Test
    public void testBindingsInLoopBodies() {
        assertStringWithJavet(collect("for (let i = 0; i < 3; i++) fs.push(() => i);"),
                collect("for (let i = 0; i < 3; i++) { const k = i; fs.push(() => k); }"),
                collect("for (let i = 0; i < 3; i++) { let k = i; fs.push(() => k); }"),
                collect("let i = 0; while (i < 3) { const k = i++; fs.push(() => k); }"),
                collect("for (var i = 0; i < 3; i++) { { const k = i; fs.push(() => k); } }"),
                collect("for (const v of [0, 1, 2]) { const k = v; fs.push(() => k); }"),
                collect("for (const key in { a: 1, b: 2 }) { const k = key; fs.push(() => k); }"),
                collect("let i = 0; do { const k = i; fs.push(() => k); } while (++i < 3);"),
                collect("for (let i = 0; i < 3; i++) { const k = i; fs.push(function () { return k; }); }"),
                collect("for (let i = 0; i < 3; i++) { const k = i; function f() { return k } fs.push(f); }"),
                collect("for (let i = 0; i < 3; i++) { switch (i) { default: let k = i; fs.push(() => k); } }"),
                collect("""
                        for (let i = 0; i < 3; i++) {
                          class C { static v = i; static get() { return C.v } }
                          fs.push(() => C.get());
                        }"""),
                collect("for (let i = 0; i < 4; i++) { const k = i; fs.push(() => k); if (i % 2) continue; }"),
                collect("""
                        for (let j = 0; j < 2; j++) {
                          for (let i = 0; i < 3; i++) { const k = j * 10 + i; fs.push(() => k); break; }
                        }"""), collect("for (let i = 0; i < 3; i++) { let k = i; fs.push(() => k); k += 100; }"),
                collect("""
                        for (let i = 0; i < 3; i++) {
                          const { a, b: [c] } = { a: i, b: [i * 2] };
                          fs.push(() => a + ':' + c);
                        }"""), inArrowFunction("""
                        function* g() { for (let i = 0; i < 3; i++) { const k = i; yield () => k; } }
                        return [...g()].map(f => f()).join();
                        """));
    }

    @Test
    public void testCatchParameters() {
        assertStringWithJavet(collect("for (let i = 0; i < 3; i++) { try { throw i } catch (e) { fs.push(() => e) } }"),
                collect("""
                        for (let i = 0; i < 3; i++) {
                          try { throw { v: i } } catch ({ v }) { fs.push(() => v) }
                        }"""), collect("""
                        for (let i = 0; i < 3; i++) {
                          try { throw { v: i } } catch ({ v }) { fs.push(() => v) } finally { fs.push(() => 'f') }
                        }"""), inArrowFunction("""
                        try {
                          try { throw {} } catch ({ a = typeof b, b }) { return a }
                        } catch (e) { return e.name + ': ' + e.message }
                        """));
    }

    private static String collect(String loop) {
        return "(() => { const fs = []; " + loop + " return fs.map(f => f()).join(); })()";
    }
}
