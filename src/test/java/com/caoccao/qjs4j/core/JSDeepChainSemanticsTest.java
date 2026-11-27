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
 * Neither a prototype chain's length nor a {@code Proxy} chain's depth may change what a program means.
 * <p>
 * Both walks used a recursion limit of 1,000 and then gave a wrong answer past it: a property read through 1,001
 * {@code Object.create} links raised a {@code RangeError} where the value was plainly there, and a function behind
 * 1,002 proxies reported {@code typeof "object"} and refused to be constructed. The boundary values are pinned on both
 * sides, because a cutoff that moves is still a cutoff.
 */
public class JSDeepChainSemanticsTest extends BaseJavetTest {

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainAbsentPropertyReadsUndefined() {
        assertStringWithJavet("""
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                String(chainOf(999, { x: 1 }).notThere);""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                String(chainOf(1000, { x: 1 }).notThere);""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                String(chainOf(1001, { x: 1 }).notThere);""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                String(chainOf(20000, { x: 1 }).notThere);""");
    }

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainInOperator() {
        assertBooleanWithJavet("""
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                'x' in chainOf(999, { x: 1 });""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                'y' in chainOf(999, { x: 1 });""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                'x' in chainOf(1000, { x: 1 });""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                'y' in chainOf(1000, { x: 1 });""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                'x' in chainOf(1001, { x: 1 });""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                'y' in chainOf(1001, { x: 1 });""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                'x' in chainOf(20000, { x: 1 });""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                'y' in chainOf(20000, { x: 1 });""");
    }

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainPropertyRead() {
        // The review's reproducer is depth 1001; 999 and 1000 pin the other side of the old cutoff.
        assertIntegerWithJavet("""
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                chainOf(999, { x: 1 }).x;""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                chainOf(1000, { x: 1 }).x;""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                chainOf(1001, { x: 1 }).x;""", """
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                chainOf(20000, { x: 1 }).x;""");
    }

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainRunsInheritedAccessors() {
        assertStringWithJavet("""
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                const base = { get probe() { return this === leaf ? 'receiver' : 'wrong' } };
                const leaf = chainOf(5000, base);
                leaf.probe;""");
        assertStringWithJavet("""
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                let seen = 'none';
                const base = { set probe(value) { seen = value } };
                const leaf = chainOf(5000, base);
                leaf.probe = 'assigned';
                seen;""");
    }

    @Test
    @Timeout(120)
    public void testDeepPrototypeChainStillEnumerates() {
        assertStringWithJavet("""
                function chainOf(depth, base) {
                  let object = base;
                  for (let i = 0; i < depth; i++) { object = Object.create(object); }
                  return object;
                }
                const leaf = chainOf(5000, { inherited: 1 });
                const keys = [];
                for (const key in leaf) { keys.push(key); }
                keys.join(',');""");
    }

    @Test
    @Timeout(120)
    public void testDeepProxyChainIsArray() {
        assertBooleanWithJavet("""
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(999, []));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(999, {}));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(1000, []));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(1000, {}));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(1001, []));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(1001, {}));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(1002, []));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(1002, {}));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(20000, []));""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                Array.isArray(proxiesOf(20000, {}));""");
    }

    @Test
    @Timeout(120)
    public void testDeepProxyChainIsCallable() {
        // Depth 200, not 1,002: actually *invoking* through a proxy chain nests one activation per
        // proxy, so it is bounded by the interpreter's call-stack budget — a documented, now
        // configurable limit (JSRuntimeOptions.setMaxStackSize), not the classification cutoff this
        // class is about. Classification at 20,000 is covered by testDeepProxyChainTypeof.
        assertIntegerWithJavet("""
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                proxiesOf(200, function (a, b) { return a + b })(2, 3);""");
    }

    @Test
    @Timeout(120)
    public void testDeepProxyChainIsConstructable() {
        // 1,001 and 1,002 because those are the classification cutoff this class exists to pin —
        // a function behind that many proxies used to refuse to be constructed. Not 20,000: like
        // calling through a chain, constructing through one nests an activation per proxy, so past
        // some depth it is each engine's call-stack budget being measured rather than the
        // classification. At 20,000 that budget is V8's, and how much of it is left depends on how
        // busy the suite is — the assertion held only while V8 happened not to run out first.
        // Classification at 20,000 is covered by testDeepProxyChainTypeof, which does not recurse.
        assertIntegerWithJavet("""
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                function Point(x) { this.x = x }
                Reflect.construct(proxiesOf(1001, Point), [7]).x;""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                function Point(x) { this.x = x }
                Reflect.construct(proxiesOf(1002, Point), [7]).x;""");
    }

    @Test
    @Timeout(120)
    public void testDeepProxyChainTypeof() {
        // The review's reproducer: typeof flipped from "function" to "object" at 1,001 wrappers.
        assertStringWithJavet("""
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(999, function () {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(999, {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(1000, function () {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(1000, {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(1001, function () {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(1001, {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(1002, function () {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(1002, {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(20000, function () {});""", """
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                typeof proxiesOf(20000, {});""");
    }

    @Test
    @Timeout(120)
    public void testDeepProxyOverBoundFunctionIsConstructable() {
        assertIntegerWithJavet("""
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                function Point(x) { this.x = x }
                let bound = Point;
                for (let i = 0; i < 100; i++) { bound = bound.bind(null); }
                Reflect.construct(proxiesOf(200, bound), [7]).x;""");
    }

    @Test
    @Timeout(120)
    public void testRevokedProxyInADeepChainStillThrows() {
        assertStringWithJavet("""
                function proxiesOf(depth, base) {
                  let value = base;
                  for (let i = 0; i < depth; i++) { value = new Proxy(value, {}); }
                  return value;
                }
                const revocable = Proxy.revocable([], {});
                const deep = proxiesOf(1500, revocable.proxy);
                revocable.revoke();
                try { Array.isArray(deep); 'no error'; } catch (e) { e.name; }""");
    }
}
