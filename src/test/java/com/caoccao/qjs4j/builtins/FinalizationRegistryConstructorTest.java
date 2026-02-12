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
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FinalizationRegistry constructor.
 */
public class FinalizationRegistryConstructorTest extends BaseJavetTest {

    @Test
    public void testCallWithoutNew() {
        assertErrorWithJavet("FinalizationRegistry(function(){})");
    }

    @Test
    public void testCallWithoutNewAndNoArgs() {
        assertErrorWithJavet("FinalizationRegistry()");
    }

    @Test
    public void testConstructorLength() {
        assertIntegerWithJavet("FinalizationRegistry.length");
    }

    @Test
    public void testConstructorName() {
        assertStringWithJavet("FinalizationRegistry.name");
    }

    @Test
    public void testConstructorPrototypeDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(FinalizationRegistry, 'prototype').writable === false",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry, 'prototype').enumerable === false",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry, 'prototype').configurable === false");
    }

    @Test
    public void testGlobalDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(globalThis, 'FinalizationRegistry').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'FinalizationRegistry').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'FinalizationRegistry').configurable === true");
    }

    @Test
    public void testNewWithNoArgs() {
        assertThatThrownBy(() -> context.eval("new FinalizationRegistry()"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
    }

    @Test
    public void testNewWithNonFunction() {
        for (String code : new String[]{
                "new FinalizationRegistry(42)",
                "new FinalizationRegistry('string')",
                "new FinalizationRegistry(true)",
                "new FinalizationRegistry(null)",
                "new FinalizationRegistry(undefined)",
                "new FinalizationRegistry({})",
                "new FinalizationRegistry([])"}) {
            assertThatThrownBy(() -> context.eval(code))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("TypeError");
        }
    }

    @Test
    public void testNewWithValidFunction() {
        assertBooleanWithJavet(
                "new FinalizationRegistry(function(){}) instanceof FinalizationRegistry");
    }

    @Test
    public void testPrototypeChain() {
        assertBooleanWithJavet(
                "Object.getPrototypeOf(FinalizationRegistry.prototype) === Object.prototype");
    }

    @Test
    public void testPrototypeConstructor() {
        assertBooleanWithJavet(
                "FinalizationRegistry.prototype.constructor === FinalizationRegistry");
    }

    @Test
    public void testPrototypeConstructorDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'constructor').writable === true",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'constructor').enumerable === false",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'constructor').configurable === true");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet("typeof FinalizationRegistry");
    }
}
