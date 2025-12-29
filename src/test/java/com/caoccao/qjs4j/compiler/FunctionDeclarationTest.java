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
 * Tests for function declarations to debug async/await issues.
 */
public class FunctionDeclarationTest {
    private JSContext context;

    @BeforeEach
    void setUp() {
        JSRuntime runtime = new JSRuntime();
        context = runtime.createContext();
    }

    @Test
    void testAsyncFunctionDeclarationCall() {
        String code = """
                async function test() {
                    return 42;
                }
                test();
                """;

        JSValue result = context.eval(code);
        System.out.println("Result type: " + result.getClass().getSimpleName());
        System.out.println("Result: " + result);
        System.out.println("Result toString: " + result);

        if (result instanceof JSBytecodeFunction func) {
            System.out.println("Function name: " + func.getName());
            System.out.println("Function isAsync: " + func.isAsync());
        }

        assertInstanceOf(JSPromise.class, result, "Async function should return a promise");
    }

    @Test
    void testAsyncFunctionDeclarationReference() {
        String code = """
                async function test() {
                    return 42;
                }
                test;
                """;

        JSValue result = context.eval(code);
        System.out.println("Result type: " + result.getClass().getSimpleName());
        System.out.println("Result: " + result);

        assertInstanceOf(JSBytecodeFunction.class, result, "Should return the function");
        JSBytecodeFunction func = (JSBytecodeFunction) result;
        assertEquals("test", func.getName());
        assertTrue(func.isAsync(), "Function should be marked as async");
    }

    @Test
    void testFunctionDeclarationCall() {
        String code = """
                function test() {
                    return 42;
                }
                test();
                """;

        JSValue result = context.eval(code);
        System.out.println("Result type: " + result.getClass().getSimpleName());
        System.out.println("Result: " + result);

        assertInstanceOf(JSNumber.class, result, "Should return 42");
        assertEquals(42.0, ((JSNumber) result).value());
    }

    @Test
    void testSimpleFunctionDeclaration() {
        String code = """
                function test() {
                    return 42;
                }
                test;
                """;

        try {
            JSValue result = context.eval(code);
            System.out.println("Result type: " + result.getClass().getSimpleName());
            System.out.println("Result: " + result);

            assertInstanceOf(JSBytecodeFunction.class, result, "Should return the function");
            JSBytecodeFunction func = (JSBytecodeFunction) result;
            assertEquals("test", func.getName());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception thrown: " + e.getMessage());
        }
    }
}
