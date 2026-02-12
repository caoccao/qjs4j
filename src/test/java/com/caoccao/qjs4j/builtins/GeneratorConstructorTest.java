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
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Generator and GeneratorFunction constructor/prototype chain.
 * Based on QuickJS JS_AddIntrinsicGenerator registration.
 */
public class GeneratorConstructorTest extends BaseJavetTest {

    @Test
    public void testGeneratorFunctionHasPrototypeProperty() {
        assertBooleanWithJavet(
                "var gf = function*(){}; 'prototype' in gf");
    }

    @Test
    public void testGeneratorFunctionPrototypeInheritsFromFunctionPrototype() {
        assertBooleanWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(Object.getPrototypeOf(gf)) === Function.prototype");
    }

    @Test
    public void testGeneratorFunctionPrototypePropertyDescriptor() {
        assertBooleanWithJavet(
                "var gf = function*(){}; var d = Object.getOwnPropertyDescriptor(gf, 'prototype'); d.writable === true",
                "var gf = function*(){}; var d = Object.getOwnPropertyDescriptor(gf, 'prototype'); d.enumerable === false",
                "var gf = function*(){}; var d = Object.getOwnPropertyDescriptor(gf, 'prototype'); d.configurable === false");
    }

    @Test
    public void testGeneratorFunctionPrototypePrototypeIsGeneratorPrototype() {
        assertBooleanWithJavet(
                "var gf = function*(){}; var gfp = Object.getPrototypeOf(gf); 'prototype' in gfp");
    }

    @Test
    public void testGeneratorFunctionPrototypeToStringTag() {
        assertStringWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(gf)[Symbol.toStringTag]");
    }

    @Test
    public void testGeneratorFunctionPrototypeToStringTagDescriptor() {
        assertBooleanWithJavet(
                "var gf = function*(){}; var gfp = Object.getPrototypeOf(gf); var d = Object.getOwnPropertyDescriptor(gfp, Symbol.toStringTag); d.configurable === true",
                "var gf = function*(){}; var gfp = Object.getPrototypeOf(gf); var d = Object.getOwnPropertyDescriptor(gfp, Symbol.toStringTag); d.enumerable === false",
                "var gf = function*(){}; var gfp = Object.getPrototypeOf(gf); var d = Object.getOwnPropertyDescriptor(gfp, Symbol.toStringTag); d.writable === false");
    }

    @Test
    public void testGeneratorFunctionsShareSameGFP() {
        assertBooleanWithJavet(
                "var g1 = function*(){}; var g2 = function*(){}; Object.getPrototypeOf(g1) === Object.getPrototypeOf(g2)");
    }

    @Test
    public void testGeneratorObjectPrototypeChain() {
        // Generator object -> gf.prototype -> Generator.prototype -> Iterator.prototype -> Object.prototype
        assertBooleanWithJavet(
                "var gf = function*(){}; var g = gf(); Object.getPrototypeOf(g) === gf.prototype");
    }

    @Test
    public void testGeneratorObjectToString() {
        assertStringWithJavet(
                "function* gen() {} Object.prototype.toString.call(gen())");
    }

    @Test
    public void testGeneratorPrototypeConstructorDescriptor() {
        assertBooleanWithJavet(
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'constructor'); d.configurable === true",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'constructor'); d.enumerable === false");
    }

    @Test
    public void testGeneratorPrototypeConstructorIsGFP() {
        // Generator.prototype.constructor === GeneratorFunction.prototype
        assertBooleanWithJavet(
                "var gf = function*(){}; var gfp = Object.getPrototypeOf(gf); var gp = gfp.prototype; gp.constructor === gfp");
    }

    @Test
    public void testGeneratorPrototypeInheritsFromIteratorPrototype() {
        // Generator.prototype inherits from Iterator.prototype (which has Symbol.iterator)
        assertBooleanWithJavet(
                "var gf = function*(){}; var g = gf(); typeof g[Symbol.iterator] === 'function'",
                "var gf = function*(){}; var g = gf(); g[Symbol.iterator]() === g");
    }

    @Test
    public void testGeneratorPrototypeMethodDescriptorNext() {
        assertBooleanWithJavet(
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'next'); d.writable === true",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'next'); d.enumerable === false",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'next'); d.configurable === true");
    }

    @Test
    public void testGeneratorPrototypeMethodDescriptorReturn() {
        assertBooleanWithJavet(
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'return'); d.writable === true",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'return'); d.enumerable === false",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'return'); d.configurable === true");
    }

    @Test
    public void testGeneratorPrototypeMethodDescriptorThrow() {
        assertBooleanWithJavet(
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'throw'); d.writable === true",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'throw'); d.enumerable === false",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, 'throw'); d.configurable === true");
    }

    @Test
    public void testGeneratorPrototypeMethodLengthNext() {
        assertIntegerWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(gf).prototype.next.length");
    }

    @Test
    public void testGeneratorPrototypeMethodLengthReturn() {
        assertIntegerWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(gf).prototype.return.length");
    }

    @Test
    public void testGeneratorPrototypeMethodLengthThrow() {
        assertIntegerWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(gf).prototype.throw.length");
    }

    @Test
    public void testGeneratorPrototypeMethodNameNext() {
        assertStringWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(gf).prototype.next.name");
    }

    @Test
    public void testGeneratorPrototypeMethodNameReturn() {
        assertStringWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(gf).prototype.return.name");
    }

    @Test
    public void testGeneratorPrototypeMethodNameThrow() {
        assertStringWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(gf).prototype.throw.name");
    }

    @Test
    public void testGeneratorPrototypeToStringTag() {
        assertStringWithJavet(
                "var gf = function*(){}; Object.getPrototypeOf(gf).prototype[Symbol.toStringTag]");
    }

    @Test
    public void testGeneratorPrototypeToStringTagDescriptor() {
        assertBooleanWithJavet(
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, Symbol.toStringTag); d.configurable === true",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, Symbol.toStringTag); d.enumerable === false",
                "var gf = function*(){}; var gp = Object.getPrototypeOf(gf).prototype; var d = Object.getOwnPropertyDescriptor(gp, Symbol.toStringTag); d.writable === false");
    }

    @Test
    public void testTypeofGeneratorFunction() {
        assertStringWithJavet(
                "typeof function*(){}");
    }
}
