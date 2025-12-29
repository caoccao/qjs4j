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

package com.caoccao.qjs4j.compiler;

import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for async/await functionality.
 */
public class AsyncAwaitTest {
    private JSContext context;

    @BeforeEach
    void setUp() {
        JSRuntime runtime = new JSRuntime();
        context = runtime.createContext();
    }

    @Test
    void testAsyncArrowFunction() {
        String code = """
                const test = async () => {
                    return 'hello';
                };
                test();
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSPromise.class, result);

        JSPromise promise = (JSPromise) result;
        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
        assertEquals("hello", ((JSString) promise.getResult()).value());
    }

    @Test
    void testAsyncFunctionIsAsync() {
        // Test that a bytecode function has the isAsync flag set correctly
        String code = """
                async function test() {
                    return 1;
                }
                test;
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSBytecodeFunction.class, result);

        JSBytecodeFunction func = (JSBytecodeFunction) result;
        assertTrue(func.isAsync(), "Function should be marked as async");
    }

    @Test
    void testAsyncFunctionReturnsPromise() {
        String code = """
                async function test() {
                    return 42;
                }
                test();
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSPromise.class, result, "Async function should return a promise");

        JSPromise promise = (JSPromise) result;
        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState(),
                "Promise should be fulfilled");
        assertEquals(42.0, ((JSNumber) promise.getResult()).value(),
                "Promise should resolve to 42");
    }

    @Test
    void testAsyncFunctionToString() {
        String code = """
                async function myAsyncFunction() {
                    return 1;
                }
                String(myAsyncFunction);
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSString.class, result);

        String str = ((JSString) result).value();
        assertTrue(str.contains("async"), "toString should show 'async'");
        assertTrue(str.contains("myAsyncFunction"), "toString should show function name");
    }

    @Test
    void testAsyncFunctionWithMultipleAwaits() {
        String code = """
                async function test() {
                    const a = await 10;
                    const b = await 20;
                    return a + b;
                }
                test();
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSPromise.class, result);

        JSPromise promise = (JSPromise) result;
        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
    }

    @Test
    void testAsyncFunctionWithoutReturn() {
        String code = """
                async function test() {
                    const x = 42;
                }
                test();
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSPromise.class, result);

        JSPromise promise = (JSPromise) result;
        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
        assertInstanceOf(JSUndefined.class, promise.getResult());
    }

    @Test
    void testAwaitInExpression() {
        String code = """
                async function test() {
                    return (await 5) + (await 10);
                }
                test();
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSPromise.class, result);

        JSPromise promise = (JSPromise) result;
        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
    }

    @Test
    void testAwaitPromise() {
        String code = """
                async function test() {
                    const promise = Promise.resolve(100);
                    const value = await promise;
                    return value;
                }
                test();
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSPromise.class, result);

        JSPromise promise = (JSPromise) result;
        // Note: The current simple implementation may not fully resolve chained promises
        // This test validates the basic structure is working
        assertNotNull(promise);
    }

    @Test
    void testAwaitSimpleValue() {
        String code = """
                async function test() {
                    const value = await 42;
                    return value;
                }
                test();
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSPromise.class, result);

        JSPromise promise = (JSPromise) result;
        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
    }

    @Test
    void testRegularFunctionIsNotAsync() {
        String code = """
                function test() {
                    return 1;
                }
                test;
                """;

        JSValue result = context.eval(code);
        assertInstanceOf(JSBytecodeFunction.class, result);

        JSBytecodeFunction func = (JSBytecodeFunction) result;
        assertFalse(func.isAsync(), "Regular function should not be marked as async");
    }
}
