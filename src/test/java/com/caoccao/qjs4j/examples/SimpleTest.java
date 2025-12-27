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

package com.caoccao.qjs4j.examples;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple test to demonstrate JavaScript execution.
 */
public class SimpleTest {

    public static void main(String[] args) {
        System.out.println("=== QuickJS for Java (qjs4j) - Simple Test ===\n");

        // Create runtime and context
        JSRuntime runtime = new JSRuntime();
        JSContext ctx = runtime.createContext();

        try {
            // Test 1: Simple arithmetic
            System.out.println("Test 1: Simple arithmetic");
            JSValue result1 = ctx.eval("2 + 3");
            assertEquals(5.0, (Double) result1.toJavaObject());
            System.out.println("2 + 3 = " + result1);
            System.out.println();

            // Test 2: Variables
            System.out.println("Test 2: Variables");
            JSValue result2 = ctx.eval("var x = 10; var y = 20; x + y");
            assertEquals(30.0, (Double) result2.toJavaObject());
            System.out.println("x + y = " + result2);
            System.out.println();

            // Test 3: Math object
            System.out.println("Test 3: Math object");
            JSValue result3 = ctx.eval("Math.PI");
            assertEquals(Math.PI, (Double) result3.toJavaObject());
            System.out.println("Math.PI = " + result3);
            JSValue result4 = ctx.eval("Math.sqrt(16)");
            assertEquals(4.0, (Double) result4.toJavaObject());
            System.out.println("Math.sqrt(16) = " + result4);
            System.out.println();

            // Test 4: String methods
            System.out.println("Test 4: String methods");
            JSValue result5 = ctx.eval("'hello world'.toUpperCase()");
            assertEquals("HELLO WORLD", result5.toJavaObject());
            System.out.println("'hello world'.toUpperCase() = " + result5);
            System.out.println();

            // Test 5: Global functions
            System.out.println("Test 5: Global functions");
            JSValue result6 = ctx.eval("parseInt('42')");
            assertEquals(42.0, (Double) result6.toJavaObject());
            System.out.println("parseInt('42') = " + result6);
            JSValue result7 = ctx.eval("parseFloat('3.14159')");
            assertEquals(3.14159, (Double) result7.toJavaObject());
            System.out.println("parseFloat('3.14159') = " + result7);
            System.out.println();

            // Test 6: JSON
            System.out.println("Test 6: JSON");
            JSValue result8 = ctx.eval("JSON.stringify({name: 'test', value: 42})");
            assertEquals("{\"name\":\"test\",\"value\":42}", result8.toJavaObject());
            System.out.println("JSON.stringify(...) = " + result8);
            System.out.println();

            // Test 7: Error handling
            System.out.println("Test 7: Error handling");
            boolean exceptionThrown = false;
            try {
                ctx.eval("throw new Error('Test error')");
                System.out.println("ERROR: Exception should have been thrown!");
            } catch (Exception e) {
                exceptionThrown = true;
                System.out.println("Successfully caught error: " + e.getMessage());
            }
            assertTrue(exceptionThrown);
            System.out.println();

            System.out.println("=== All tests completed successfully! ===");

        } finally {
            ctx.close();
            runtime.gc();
        }
    }
}
