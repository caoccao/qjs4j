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
import org.junit.jupiter.api.Timeout;

/**
 * Neither a prototype chain's length nor a {@code Proxy} chain's depth may change what a program
 * means.
 * <p>
 * Both walks used a recursion limit of 1,000 and then gave a wrong answer past it: a property read
 * through 1,001 {@code Object.create} links raised a {@code RangeError} where the value was plainly
 * there, and a function behind 1,002 proxies reported {@code typeof "object"} and refused to be
 * constructed. The boundary values are pinned on both sides, because a cutoff that moves is still
 * a cutoff.
 */
public class JSDeepChainSemanticsTest extends BaseJavetTest {
    private static final String BUILD_PROTOTYPE_CHAIN = """
            function chainOf(depth, base) {
              let object = base;
              for (let i = 0; i < depth; i++) { object = Object.create(object); }
              return object;
            }
            """;
    private static final String BUILD_PROXY_CHAIN = """
            function proxiesOf(depth, base) {
              let value = base;
              for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
              return value;
            }
            """;

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainAbsentPropertyReadsUndefined() {
        for (int depth : new int[]{999, 1000, 1001, 20000}) {
            assertStringWithJavet(BUILD_PROTOTYPE_CHAIN
                    + "String(chainOf(" + depth + ", { x: 1 }).notThere);");
        }
    }

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainInOperator() {
        for (int depth : new int[]{999, 1000, 1001, 20000}) {
            assertBooleanWithJavet(BUILD_PROTOTYPE_CHAIN + "'x' in chainOf(" + depth + ", { x: 1 });");
            assertBooleanWithJavet(BUILD_PROTOTYPE_CHAIN + "'y' in chainOf(" + depth + ", { x: 1 });");
        }
    }

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainPropertyRead() {
        // The review's reproducer is depth 1001; 999 and 1000 pin the other side of the old cutoff.
        for (int depth : new int[]{999, 1000, 1001, 20000}) {
            assertIntegerWithJavet(BUILD_PROTOTYPE_CHAIN + "chainOf(" + depth + ", { x: 1 }).x;");
        }
    }

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainRunsInheritedAccessors() {
        assertStringWithJavet(BUILD_PROTOTYPE_CHAIN + """
                const base = { get probe() { return this === leaf ? 'receiver' : 'wrong' } };
                const leaf = chainOf(5000, base);
                leaf.probe;""");
        assertStringWithJavet(BUILD_PROTOTYPE_CHAIN + """
                let seen = 'none';
                const base = { set probe(value) { seen = value } };
                const leaf = chainOf(5000, base);
                leaf.probe = 'assigned';
                seen;""");
    }

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainStillEnumerates() {
        assertStringWithJavet(BUILD_PROTOTYPE_CHAIN + """
                const leaf = chainOf(5000, { inherited: 1 });
                const keys = [];
                for (const key in leaf) { keys.push(key); }
                keys.join(',');""");
    }

    @Test
    @Timeout(120)
    public void testDeepProxyChainIsArray() {
        for (int depth : new int[]{999, 1000, 1001, 1002, 20000}) {
            assertBooleanWithJavet(BUILD_PROXY_CHAIN + "Array.isArray(proxiesOf(" + depth + ", []));");
            assertBooleanWithJavet(BUILD_PROXY_CHAIN + "Array.isArray(proxiesOf(" + depth + ", {}));");
        }
    }

    @Test
    @Timeout(120)
    public void testDeepProxyChainIsCallable() {
        // Depth 200, not 1,002: actually *invoking* through a proxy chain nests one activation per
        // proxy, so it is bounded by the interpreter's call-stack budget — a documented, now
        // configurable limit (JSRuntimeOptions.setMaxStackSize), not the classification cutoff this
        // class is about. Classification at 20,000 is covered by testDeepProxyChainTypeof.
        assertIntegerWithJavet(BUILD_PROXY_CHAIN
                + "proxiesOf(200, function (a, b) { return a + b })(2, 3);");
    }

    @Test
    @Timeout(120)
    public void testDeepProxyChainIsConstructable() {
        for (int depth : new int[]{1001, 1002, 20000}) {
            assertIntegerWithJavet(BUILD_PROXY_CHAIN + """
                    function Point(x) { this.x = x }
                    Reflect.construct(proxiesOf(DEPTH, Point), [7]).x;""".replace("DEPTH", String.valueOf(depth)));
        }
    }

    @Test
    @Timeout(120)
    public void testDeepProxyChainTypeof() {
        // The review's reproducer: typeof flipped from "function" to "object" at 1,001 wrappers.
        for (int depth : new int[]{999, 1000, 1001, 1002, 20000}) {
            assertStringWithJavet(BUILD_PROXY_CHAIN + "typeof proxiesOf(" + depth + ", function () {});");
            assertStringWithJavet(BUILD_PROXY_CHAIN + "typeof proxiesOf(" + depth + ", {});");
        }
    }

    @Test
    @Timeout(120)
    public void testDeepProxyOverBoundFunctionIsConstructable() {
        assertIntegerWithJavet(BUILD_PROXY_CHAIN + """
                function Point(x) { this.x = x }
                let bound = Point;
                for (let i = 0; i < 100; i++) { bound = bound.bind(null); }
                Reflect.construct(proxiesOf(200, bound), [7]).x;""");
    }

    @Test
    @Timeout(120)
    public void testRevokedProxyInADeepChainStillThrows() {
        assertStringWithJavet(BUILD_PROXY_CHAIN + """
                const revocable = Proxy.revocable([], {});
                const deep = proxiesOf(1500, revocable.proxy);
                revocable.revoke();
                try { Array.isArray(deep); 'no error'; } catch (e) { e.name; }""");
    }
}
