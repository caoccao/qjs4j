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
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Advanced tests for async/await functionality, focusing on proper asynchronous behavior,
 * promise chaining, and execution suspension/resumption.
 */
public class AsyncTest extends BaseJavetTest {
    @Test
    void testAsyncArrowFunction() {
        assertStringWithJavet("""
                const test = async () => {
                    return 'hello';
                };
                test();""");
    }

    @Test
    void testAsyncAwaitWithPromiseAll() {
        // Test using Promise.all with async/await
        assertStringWithJavet("""
                async function test() {
                    const results = await Promise.all([
                        Promise.resolve(1),
                        Promise.resolve(2),
                        Promise.resolve(3)
                    ]);
                    return results;
                }
                JSON.stringify(test());""");
    }

    @Test
    void testAsyncFunctionErrorHandling() {
        assertUndefinedWithJavet("""
                async function test() {
                    throw 'test error';
                }
                const promise = test();
                if (promise.state === 'rejected') {
                    throw promise.result;
                }""");
    }

    @Test
    void testAsyncFunctionIsAsync() {
        // Test that a bytecode function has the isAsync flag set correctly
        assertBooleanWithJavet("""
                async function test() {
                    return 1;
                }
                test.constructor.name === 'AsyncFunction';""");
    }

    @Test
    void testAsyncFunctionReturnsPromise() {
        assertIntegerWithJavet("""
                async function test() {
                    return 42;
                }
                test();""");
    }

    @Test
    void testAsyncFunctionToString() {
        assertStringWithJavet("""
                async function myAsyncFunction() {
                    return 1; // Test
                }
                String(myAsyncFunction);""");
    }

    @Test
    void testAsyncFunctionWithMultipleAwaits() {
        assertIntegerWithJavet("""
                async function test() {
                    const a = await 10;
                    const b = await 20;
                    return a + b;
                }
                test();""");
    }

    @Test
    void testAsyncFunctionWithPromiseResolution() {
        // Test that async functions properly work with promises that resolve asynchronously
        assertObjectWithJavet("""
                let resolved = false;
                async function test() {
                    const promise = new Promise(function(resolve) {
                        resolved = true;
                        resolve(42);
                    });
                    const value = await promise;
                    return value;
                }
                const result = test();
                [resolved, result];""");
    }

    @Test
    void testAsyncFunctionWithoutReturn() {
        assertBooleanWithJavet("""
                async function test() {
                    const x = 42;
                }
                test() === undefined;""");
    }

    @Test
    void testAsyncGeneratorBasic() {
        // Test basic async generator syntax (if implemented)
        assertStringWithJavet("""
                async function* generator() {
                    yield 1;
                    yield 2;
                    yield 3;
                }
                const gen = generator();
                typeof gen;""");
    }

    @Test
    void testAwaitInExpression() {
        assertIntegerWithJavet("""
                async function test() {
                    return (await 5) + (await 10);
                }
                test();""");
    }

    @Test
    void testAwaitPromise() {
        assertIntegerWithJavet("""
                async function test() {
                    const promise = Promise.resolve(100);
                    const value = await promise;
                    return value;
                }
                test();""");
    }

    @Test
    void testAwaitPromiseChain() {
        assertIntegerWithJavet("""
                async function test() {
                    const value = await Promise.resolve(10).then(function(x) { return x * 2; });
                    return value;
                }
                test();""");
    }

    @Test
    void testAwaitRejectedPromise() {
        AtomicBoolean called = new AtomicBoolean(false);
        context.setPromiseRejectCallback((event, promise, result) -> {
            called.set(true);
        });
        assertStringWithJavet("""
                async function test() {
                    try {
                        await Promise.reject(new Error('rejected'));
                        return 'should not reach here';
                    } catch (e) {
                        return 'caught: ' + e.message;
                    }
                }
                test();""");
        context.setPromiseRejectCallback(null);
        assertThat(called.get()).isTrue();
    }

    @Test
    void testAwaitSimpleValue() {
        assertIntegerWithJavet("""
                async function test() {
                    const value = await 42;
                    return value;
                }
                test();""");
    }

    @Test
    void testConcurrentAsyncFunctions() {
        assertObjectWithJavet("""
                let results = [];
                async function task1() {
                    await 1;
                    results.push('task1');
                    return 'done1';
                }
                async function task2() {
                    await 2;
                    results.push('task2');
                    return 'done2';
                }
                const p1 = task1();
                const p2 = task2();
                Promise.all([p1, p2, results]);""");
    }

    @Test
    void testForAwaitOfLoop() {
        assertStringWithJavet("""
                async function test() {
                    const values = [1, 2, 3];
                    const results = [];
                    for await (const value of values) {
                        results.push(value);
                    }
                    return JSON.stringify(results);
                }
                test();""");
    }

    @Test
    void testMultipleSequentialAwaits() {
        assertStringWithJavet("""
                let order = [];
                async function test() {
                    order.push(1);
                    await 'first';
                    order.push(2);
                    await 'second';
                    order.push(3);
                    return JSON.stringify(order);
                }
                test();""");
    }

    @Test
    void testNestedAsyncFunctions() {
        assertIntegerWithJavet("""
                async function inner() {
                    return 42;
                }
                async function outer() {
                    const value = await inner();
                    return value * 2;
                }
                outer();""");
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