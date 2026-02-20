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

/**
 * Test cases for the 'super' keyword in invalid contexts.
 * Super property access and super calls are only valid inside methods
 * and derived class constructors respectively.
 */
public class SuperExpressionTest extends BaseJavetTest {
    @Test
    public void testSuperCallInGlobalScope() {
        assertErrorWithJavet("super()");
    }

    @Test
    public void testSuperCallInRegularFunction() {
        assertErrorWithJavet("(function() { super(); })()");
    }

    @Test
    public void testSuperComputedPropertyInGlobalScope() {
        assertErrorWithJavet("super['x']");
    }

    @Test
    public void testSuperComputedPropertyInRegularFunction() {
        assertErrorWithJavet("(function() { return super['x']; })()");
    }

    @Test
    public void testSuperPropertyAccessInGlobalScope() {
        assertErrorWithJavet("super.x");
    }

    @Test
    public void testSuperPropertyInArrowFunction() {
        assertErrorWithJavet("var f = () => super.x; f()");
    }

    @Test
    public void testSuperPropertyInDerivedMethod() {
        assertIntegerWithJavet("""
                class A {
                    m() { return 2; }
                    n() { return 40; }
                }
                class B extends A {
                    m() { return super.n() + super.m(); }
                }
                new B().m();
                """);
    }

    @Test
    public void testSuperPropertyInRegularFunction() {
        assertErrorWithJavet("(function() { return super.x; })()");
    }

    @Test
    public void testSuperPropertyInStaticMethod() {
        assertIntegerWithJavet("""
                class A {
                    static m() { return 5; }
                }
                class B extends A {
                    static m() { return super.m() + 1; }
                }
                B.m();
                """);
    }

    @Test
    public void testSuperPropertyWriteInDerivedMethod() {
        assertIntegerWithJavet("""
                class A {}
                A.prototype.x = 1;
                class B extends A {
                    m() { super.x = 42; return this.x; }
                }
                new B().m();
                """);
    }
}
