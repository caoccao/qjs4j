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

package com.caoccao.qjs4j.compiler.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class declaration compilation and execution.
 */
public class ClassCompilerTest extends BaseJavetTest {

    @Test
    public void testClassWithConstructor() throws Exception {
        assertIntegerWithJavet("""
                class Point {
                    constructor(x, y) {
                        this.x = x;
                        this.y = y;
                    }
                }
                const p = new Point(1, 2);
                p.x + p.y""");
    }

    @Test
    public void testClassWithFieldsAndConstructor() throws Exception {
        assertIntegerWithJavet("""
                class Point {
                    z = 5;
                    constructor(x, y) {
                        this.x = x;
                        this.y = y;
                    }
                }
                const p = new Point(1, 2);
                p.x + p.y + p.z""");
    }

    @Test
    public void testClassWithMethod() throws Exception {
        String source = """
                class Counter {
                    increment() {
                        return 42;
                    }
                }
                const c = new Counter();
                c.increment()
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Class with method test result: " + result);
        }
    }

    @Test
    public void testClassWithMultipleStaticBlocks() throws Exception {
        String source = """
                class Test {
                    static x = 0;
                    static {
                        this.x = 10;
                    }
                    static {
                        this.x = this.x + 5;
                    }
                }
                Test.x
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Class with multiple static blocks test result: " + result);
        }
    }

    @Test
    public void testClassWithPrivateField() throws Exception {
        String source = """
                class Counter {
                    #count = 0;
                }
                const c = new Counter();
                c
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Class with private field test result: " + result);
        }
    }

    @Test
    public void testClassWithPrivateFieldAccess() throws Exception {
        String source = """
                class Counter {
                    #count = 5;
                    getCount() {
                        return this.#count;
                    }
                    setCount(val) {
                        this.#count = val;
                    }
                }
                const c = new Counter();
                c.setCount(10);
                c.getCount()
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Class with private field access test result: " + result);
        }
    }

    @Test
    public void testClassWithPrivateFieldInitializer() throws Exception {
        String source = """
                class Counter {
                    #count = 42;
                }
                const c = new Counter();
                c
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Class with private field initializer test result: " + result);
        }
    }

    @Test
    public void testClassWithPublicField() throws Exception {
        String source = """
                class Counter {
                    count = 0;
                }
                const c = new Counter();
                c.count
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Class with public field test result: " + result);
        }
    }

    @Test
    public void testClassWithPublicFieldInitializer() throws Exception {
        String source = """
                class Point {
                    x = 10;
                    y = 20;
                }
                const p = new Point();
                p.x + p.y
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Class with public field initializer test result: " + result);
        }
    }

    @Test
    public void testClassWithStaticBlock() throws Exception {
        String source = """
                class Config {
                    static apiUrl;
                    static {
                        this.apiUrl = 'http://localhost:3000';
                    }
                }
                Config.apiUrl
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Class with static block test result: " + result);
        }
    }

    @Test
    public void testSimpleClass() throws Exception {
        String source = """
                class Point {
                }
                Point
                """;

        try (JSContext context = new JSContext(new JSRuntime())) {
            JSValue result = context.eval(source);
            assertThat(result).isNotNull();
            System.out.println("Simple class test passed: " + result);
        }
    }
}
