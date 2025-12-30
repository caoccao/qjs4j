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

import java.util.stream.Stream;

/**
 * Unit tests for SymbolPrototype methods.
 */
public class SymbolPrototypeTest extends BaseJavetTest {

    @Test
    public void testGetDescription() {
        Stream.of(
                // Normal case: symbol with description
                "Symbol('testDescription').description;",
                // Normal case: symbol without description
                "Symbol().description;",
                // Test with well-known symbol
                "Symbol.iterator.description;"
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeObject(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testToPrimitive() {
        // Test Symbol[@@toPrimitive] - returns the symbol itself
        Stream.of(
                "var sym1 = Symbol('test'); sym1[Symbol.toPrimitive]() === sym1",
                "var sym2 = Symbol.iterator; sym2[Symbol.toPrimitive]() === sym2"
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeBoolean(),
                        () -> context.eval(code).toJavaObject()));

        // Edge case: called on non-symbol should throw TypeError
        assertErrorWithJavet("Symbol.prototype[Symbol.toPrimitive].call('not a symbol');");
    }

    @Test
    public void testToString() {
        Stream.of(
                // Normal case: symbol with description
                "Symbol('testDescription').toString();",
                // Normal case: symbol without description
                "Symbol().toString();",
                // Normal case: via call for well-known symbol
                "Symbol.prototype.toString.call(Symbol.iterator);",
                // Symbol with empty string description
                "Symbol('').toString();",
                // Well-known symbols
                "Symbol.asyncIterator.toString();",
                "Symbol.hasInstance.toString();"
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeString(),
                        () -> context.eval(code).toJavaObject()));

        // Edge case: called on non-symbol should throw TypeError
        assertErrorWithJavet("Symbol.prototype.toString.call('not a symbol');");

        // Test that concatenating symbol with string throws TypeError
        assertErrorWithJavet("const a = '' + Symbol.iterator;", "Cannot convert a Symbol value to a string");
    }

    @Test
    public void testToStringTag() {
        // Symbol.prototype[@@toStringTag] should return "Symbol"
        Stream.of(
                "Symbol.prototype[Symbol.toStringTag];",
                "Object.prototype.toString.call(Symbol('test'));",
                "Object.prototype.toString.call(Symbol.iterator);"
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeString(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testValueOf() {
        Stream.of(
                // Normal case: symbol valueOf returns itself
                "var sym = Symbol('test'); sym.valueOf() === sym;",
                "var sym = Symbol.iterator; sym.valueOf() === sym;",
                // Test with Object(Symbol())
                "var symObj = Object(Symbol('test')); typeof symObj.valueOf();",
                "var symObj = Object(Symbol('test')); symObj.valueOf() !== symObj;"
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeObject(),
                        () -> context.eval(code).toJavaObject()));

        // Edge case: called on non-symbol should throw TypeError
        assertErrorWithJavet("Symbol.prototype.valueOf.call('not a symbol');");
    }
}