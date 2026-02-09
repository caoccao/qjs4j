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
import org.junit.jupiter.api.Test;

/**
 * Test class declaration compilation and execution.
 */
public class ClassCompilerTest extends BaseJavetTest {

    @Test
    public void testClassToString() {
        assertStringWithJavet(
                """
                        class A {
                        }
                        typeof A""",
                """
                        class A {
                          toString() {
                            return 'A';
                          }
                        }
                        A.toString()""",
                """
                        class A {
                          toString() {
                            return 'A';
                          }
                        }
                        String(A)""");
    }

    @Test
    public void testClassWithConstructor() {
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
    public void testClassWithFieldsAndConstructor() {
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
    public void testClassWithMethod() {
        assertIntegerWithJavet("""
                class Counter {
                    increment() {
                        return 42;
                    }
                }
                const c = new Counter();
                c.increment()""");
    }

    @Test
    public void testClassWithMixedMethods() {
        assertIntegerWithJavet("""
                class Calculator {
                    constructor(value) {
                        this.value = value;
                    }
                
                    add(n) {
                        return this.value + n;
                    }
                
                    static multiply(a, b) {
                        return a * b;
                    }
                }
                
                const c = new Calculator(10);
                const instanceResult = c.add(5);
                const staticResult = Calculator.multiply(3, 4);
                instanceResult + staticResult""");
    }

    @Test
    public void testClassWithMultipleStaticBlocks() {
        assertIntegerWithJavet("""
                class Test {
                    static x = 0;
                    static {
                        this.x = 10;
                    }
                    static {
                        this.x = this.x + 5;
                    }
                }
                Test.x""");
    }

    @Test
    public void testClassWithPrivateField() {
        assertBooleanWithJavet("""
                class Counter {
                    #count = 1;
                }
                const c = new Counter();
                c.count === undefined""");
    }

    @Test
    public void testClassWithPrivateFieldAccess() {
        assertIntegerWithJavet("""
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
                c.getCount()""");
    }

    @Test
    public void testClassWithPublicField() {
        assertIntegerWithJavet("""
                class Counter {
                    count = 0;
                }
                const c = new Counter();
                c.count""");
    }

    @Test
    public void testClassWithPublicFieldInitializer() {
        assertIntegerWithJavet("""
                class Point {
                    x = 10;
                    y = 20;
                }
                const p = new Point();
                p.x + p.y""");
    }

    @Test
    public void testClassWithStaticBlock() {
        assertStringWithJavet("""
                class Config {
                    static apiUrl;
                    static {
                        this.apiUrl = 'http://localhost:3000';
                    }
                }
                Config.apiUrl""");
    }

    @Test
    public void testClassWithStaticMethod() {
        assertIntegerWithJavet("""
                class MathUtils {
                    static add(a, b) {
                        return a + b;
                    }
                }
                MathUtils.add(5, 3)""");
    }

    @Test
    public void testClassDeclarationStaticMethodCanAccessPrivateField() {
        assertIntegerWithJavet("""
                class Counter {
                    #count = 7;
                    static read(instance) {
                        return instance.#count;
                    }
                }
                Counter.read(new Counter())""");
    }

    @Test
    public void testClassExpressionStaticMethod() {
        assertIntegerWithJavet("""
                const MathUtils = class {
                    static add(a, b) {
                        return a + b;
                    }
                };
                MathUtils.add(5, 3)""");
    }

    @Test
    public void testClassExpressionStaticMethodCanAccessPrivateField() {
        assertIntegerWithJavet("""
                const Counter = class {
                    #count = 9;
                    static read(instance) {
                        return instance.#count;
                    }
                };
                Counter.read(new Counter())""");
    }

    @Test
    public void testClassExpressionStaticMethodThisBinding() {
        assertBooleanWithJavet("""
                const A = class {
                    static isSelf() {
                        return this === A;
                    }
                };
                A.isSelf()""");
    }

    @Test
    public void testClassExpressionAnonymousStaticMethodInvocation() {
        assertIntegerWithJavet("""
                (class {
                    static valuePlusTwo() {
                        return 42;
                    }
                }).valuePlusTwo()""");
    }

    @Test
    public void testClassExpressionStaticVsInstanceMethodNamespace() {
        assertBooleanWithJavet("""
                const Calculator = class {
                    method() {
                        return 1;
                    }
                    static method() {
                        return 2;
                    }
                };
                const c = new Calculator();
                c.method() === 1 && Calculator.method() === 2 && c.method !== Calculator.method""");
    }
}
