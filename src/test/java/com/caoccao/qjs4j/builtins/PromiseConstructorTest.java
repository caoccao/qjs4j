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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Promise constructor static methods with iterable support.
 */
public class PromiseConstructorTest extends BaseJavetTest {

    @Test
    public void testArrayHasSymbolIterator() {
        // First, verify that arrays have Symbol.iterator
        JSValue result = context.eval("const arr = [1,2,3]; typeof arr[Symbol.iterator]");
        assertThat(result).isInstanceOf(JSString.class);
        assertThat(((JSString) result).value()).isEqualTo("function");
    }

    @Test
    public void testAsyncForAwaitOfLoop() {
        assertIntegerWithJavet("""
                async function test() {
                  let sum = 0;
                  const asyncIterable = {
                    async *[Symbol.asyncIterator]() {
                      yield 1;
                      yield 2;
                      yield 3;
                    }
                  };
                  for await (const item of asyncIterable) {
                    sum += item;
                  }
                  return sum;
                }
                test()"""
        );
    }

    @Test
    public void testAsyncForAwaitOfLoopWithBreak() {
        assertIntegerWithJavet("""
                async function test() {
                  let sum = 0;
                  const asyncIterable = {
                    async *[Symbol.asyncIterator]() {
                      yield 1;
                      yield 2;
                      yield 3;
                      yield 4;
                      yield 5;
                    }
                  };
                  for await (const item of asyncIterable) {
                    if (item > 3) break;
                    sum += item;
                  }
                  return sum;
                }
                test()""");
    }

    @Test
    public void testAsyncForAwaitOfLoopWithSyncIterable() {
        assertIntegerWithJavet("""
                async function test() {
                  let sum = 0;
                  const arr = [1, 2, 3];
                  for await (const item of arr) {
                    sum += item;
                  }
                  return sum;
                }
                test()""");
    }

    @Test
    public void testPromiseAllWithArray() {
        assertStringWithJavet("""
                var p1 = Promise.resolve(1);
                var p2 = Promise.resolve(2);
                var p3 = Promise.resolve(3);
                Promise.all([p1, p2, p3]).then(arr => JSON.stringify(arr))""");
    }

    @Test
    public void testPromiseAllWithNativeIterables() {
        assertStringWithJavet("""
                var s = new Set([Promise.resolve(1), Promise.resolve(2), Promise.resolve(3)]);
                Promise.all(s).then(arr => JSON.stringify(arr))""");
    }

    @Test
    void testPromiseConstructorBehavior() {
        assertBooleanWithJavet("""
                let value = null;
                const p = new Promise((resolve) => {
                    value = 'resolved';
                    resolve(42);
                });
                value === 'resolved'
                """);
    }

    @Test
    void testPromiseStaticMethods() {
        // Promise static methods should still exist and work
        assertStringWithJavet(
                "typeof Promise.resolve",
                "typeof Promise.reject",
                "typeof Promise.all",
                "typeof Promise.race",
                "typeof Promise.allSettled",
                "typeof Promise.any");

        // Test Promise.resolve
        assertStringWithJavet("""
                let result = 'pending';
                Promise.resolve('test').then(x => { result = x; });
                result""");
    }

    @Test
    void testPromiseTypeof() {
        // Promise should be a function
        assertStringWithJavet("typeof Promise");

        // Promise.length should be 1 (executor function parameter)
        assertIntegerWithJavet("Promise.length");

        // Promise.name should be "Promise"
        assertStringWithJavet("Promise.name");

        // new Promise() should work
        assertBooleanWithJavet("new Promise((resolve) => resolve()) instanceof Promise");

        // Promise() without new should throw TypeError (requires new)
        assertErrorWithJavet("Promise(() => {})");
    }
}
