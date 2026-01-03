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
import org.junit.jupiter.api.Disabled;
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

    @Disabled
    @Test
    public void testAsyncForAwaitOfLoop() {
        // Test async for-await-of loop with async iterable
        JSValue result = context.eval(
                "async function test() { " +
                        "  let sum = 0; " +
                        "  const asyncIterable = { " +
                        "    async *[Symbol.asyncIterator]() { " +
                        "      yield 1; " +
                        "      yield 2; " +
                        "      yield 3; " +
                        "    } " +
                        "  }; " +
                        "  for await (const item of asyncIterable) { " +
                        "    sum += item; " +
                        "  } " +
                        "  return sum; " +
                        "} " +
                        "test()"
        );
        assertThat(result).isNotNull();
        // Result should be a promise that resolves to 6
    }

    @Disabled
    @Test
    public void testAsyncForAwaitOfLoopWithBreak() {
        // Test async for-await-of loop with break statement
        JSValue result = context.eval(
                "async function test() { " +
                        "  let sum = 0; " +
                        "  const asyncIterable = { " +
                        "    async *[Symbol.asyncIterator]() { " +
                        "      yield 1; " +
                        "      yield 2; " +
                        "      yield 3; " +
                        "      yield 4; " +
                        "      yield 5; " +
                        "    } " +
                        "  }; " +
                        "  for await (const item of asyncIterable) { " +
                        "    if (item > 3) break; " +
                        "    sum += item; " +
                        "  } " +
                        "  return sum; " +
                        "} " +
                        "test()"
        );
        assertThat(result).isNotNull();
        // Result should be a promise that resolves to 6 (1 + 2 + 3)
    }

    @Disabled
    @Test
    public void testAsyncForAwaitOfLoopWithSyncIterable() {
        // Test async for-await-of loop with sync iterable (should auto-wrap)
        assertIntegerWithJavet(true, """
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
        String code = """
                var p1 = Promise.resolve(1);
                var p2 = Promise.resolve(2);
                var p3 = Promise.resolve(3);
                Promise.all([p1, p2, p3]).then(arr => JSON.stringify(arr))""";
        assertStringWithJavet(true, code);
    }

    @Test
    public void testSyncForOfLoopNested() {
        assertIntegerWithJavet("""
                function test() {
                  let sum = 0;
                  const outer = [1, 2];
                  const inner = [10, 20];
                  for (const i of outer) {
                    for (const j of inner) {
                      sum += i * j;
                    }
                  }
                  return sum;
                }
                test()""");
    }

    @Test
    public void testSyncForOfLoopWithBreak() {
        assertIntegerWithJavet("""
                function test() {
                  let sum = 0;
                  const arr = [1, 2, 3, 4, 5];
                  for (const item of arr) {
                    if (item > 3) break;
                    sum += item;
                  }
                  return sum;
                }
                test()""");
    }

    @Test
    public void testSyncForOfLoopWithContinue() {
        assertIntegerWithJavet("""
                function test() {
                  let sum = 0;
                  const arr = [1, 2, 3, 4, 5];
                  for (const item of arr) {
                    if (item % 2 === 0) continue;
                    sum += item;
                  }
                  return sum;
                }
                test()""");
    }

    @Test
    public void testSyncForOfLoopWithEmptyArray() {
        assertIntegerWithJavet(
                """
                        function test() {
                          let count = 0;
                          const arr = [];
                          for (const item of arr) {
                            count++;
                          }
                          return count;
                        }
                        test()""");
    }

    @Test
    public void testSyncForOfLoopWithLetAndConst() {
        assertIntegerWithJavet(
                """
                        function test() {
                          let sum1 = 0;
                          let sum2 = 0;
                          const arr = [1, 2, 3];
                          for (let item of arr) {
                            sum1 += item;
                          }
                          for (const item of arr) {
                            sum2 += item;
                          }
                          return sum1 + sum2;
                        }
                        test()""");
    }

    @Disabled
    @Test
    public void testSyncForOfLoopWithString() {
        assertStringWithJavet("""
                function test() {
                  let result = '';
                  const str = 'abc';
                  for (const char of str) {
                    result += char + '-';
                  }
                  return result;
                }
                test()""");
    }
}
