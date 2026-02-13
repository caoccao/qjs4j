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
 * Unit tests for SymbolPrototype methods.
 */
public class SymbolPrototypeTest extends BaseJavetTest {
    @Test
    public void testGetDescription() {
        assertStringWithJavet(
                "Symbol('testDescription').description;",
                "String(Symbol().description);",
                "Symbol.iterator.description;");
    }

    @Test
    public void testToPrimitive() {
        // Test Symbol[@@toPrimitive] - returns the symbol itself
        assertBooleanWithJavet(
                "var sym1 = Symbol('test'); sym1[Symbol.toPrimitive]() === sym1",
                "var sym2 = Symbol.iterator; sym2[Symbol.toPrimitive]() === sym2");

        // Edge case: called on non-symbol should throw TypeError
        assertErrorWithJavet("Symbol.prototype[Symbol.toPrimitive].call('not a symbol');");
    }

    @Test
    public void testToString() {
        assertStringWithJavet(
                // Normal case: symbol with description
                "Symbol('testDescription').toString();",
                // Normal case: symbol without description
                "Symbol().toString();",
                // Normal case: via call for well-known symbol
                "Symbol.prototype.toString.call(Symbol.iterator);",
                // Symbol with empty string description
                "Symbol('').toString();",
                "var a = Symbol(''); escape(a);",
                // Well-known symbols
                "Symbol.asyncIterator.toString();",
                "Symbol.hasInstance.toString();");

        // Edge case: called on non-symbol should throw TypeError
        assertErrorWithJavet(
                "Symbol.prototype.toString.call('not a symbol');",
                "const a = '' + Symbol.iterator;");
    }

    @Test
    public void testToStringTag() {
        // Symbol.prototype[@@toStringTag] should return "Symbol"
        assertStringWithJavet(
                "Symbol.prototype[Symbol.toStringTag];",
                "Object.prototype.toString.call(Symbol('test'));",
                "Object.prototype.toString.call(Symbol.iterator);");

        assertBooleanWithJavet(
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(Symbol.prototype, Symbol.toStringTag);
                          return d.value === "Symbol"
                            && d.writable === false
                            && d.enumerable === false
                            && d.configurable === true;
                        })()
                        """);
    }

    @Test
    public void testValueOf() {
        assertBooleanWithJavet(
                "var sym = Symbol('test'); sym.valueOf() === sym;",
                "var sym = Symbol('test'); sym.valueOf() === sym;",
                "var sym = Symbol.iterator; sym.valueOf() === sym;",
                "var symObj = Object(Symbol('test')); typeof symObj.valueOf() === 'symbol';",
                "var symObj = Object(Symbol('test')); symObj.valueOf() !== symObj");

        // Edge case: called on non-symbol should throw TypeError
        assertErrorWithJavet("Symbol.prototype.valueOf.call('not a symbol');");
    }

    @Test
    public void testWellKnownPrototypeProperties() {
        assertBooleanWithJavet(
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(Symbol.prototype, Symbol.toPrimitive);
                          return typeof d.value === "function"
                            && d.enumerable === false;
                        })()
                        """,
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(Symbol.prototype, "toString");
                          return typeof d.value === "function"
                            && d.writable === true
                            && d.enumerable === false
                            && d.configurable === true;
                        })()
                        """,
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(Symbol.prototype, "constructor");
                          return d.value === Symbol
                            && d.writable === true
                            && d.enumerable === false
                            && d.configurable === true;
                        })()
                        """);
    }
}
