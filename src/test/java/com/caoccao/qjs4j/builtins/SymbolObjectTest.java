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
 * Unit tests for Symbol object wrapper (JSSymbolObject).
 * Tests the object form of symbols created with Object(Symbol()).
 */
public class SymbolObjectTest extends BaseJavetTest {

    @Test
    public void testNewSymbolThrowsTypeError() {
        // Test that new Symbol() throws TypeError
        assertErrorWithJavet("new Symbol('foo');");
    }

    @Test
    public void testSymbolObjectCoercion() {
        // Symbol objects should behave like objects in boolean context
        String code1 = "Boolean(Object(Symbol('test')));";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code1).executeBoolean(),
                () -> context.eval(code1).toJavaObject());

        // Symbol object toString should work
        String code2 = "Object(Symbol('test')).toString();";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code2).executeString(),
                () -> context.eval(code2).toJavaObject());
    }

    @Test
    public void testSymbolObjectDescription() {
        // Test description property access
        String code = """
                var sym = Object(Symbol('test description'));
                sym.description""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testSymbolObjectEquality() {
        // Test equality comparisons
        Stream.of(
                "var symObj1 = Object(Symbol('test')); var symObj2 = symObj1; symObj1 === symObj2;",
                "var symObj1 = Object(Symbol('test')); var symObj2 = Object(Symbol('test')); symObj1 === symObj2;",
                "var symObj = Object(Symbol('test')); var sym = symObj.valueOf(); symObj === sym;"
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeBoolean(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testSymbolObjectInObject() {
        // Test creating symbol object
        String code = """
                var symObj = Object(Symbol('prop'));
                symObj instanceof Symbol""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testSymbolObjectInstanceof() {
        Stream.of(
                "Object(Symbol('test')) instanceof Symbol;",
                "Object(Symbol('test')) instanceof Object;").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeBoolean(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testSymbolObjectPrototypeChain() {
        // Verify prototype chain
        String code = """
                var symObj = Object(Symbol('test'));
                Object.getPrototypeOf(symObj) === Symbol.prototype""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testSymbolObjectToString() {
        Stream.of(
                "Object(Symbol('foo')).toString();",
                "Object(Symbol()).toString();",
                "Object(Symbol('')).toString();").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeString(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testSymbolObjectTypeof() {
        Stream.of(
                // typeof on Symbol object should return "object", not "symbol"
                "typeof Object(Symbol('test'));",
                "typeof Object(Symbol('foo'));",
                "typeof Object(Symbol());",
                // Compare with primitive symbol
                "typeof Symbol('test');",
                "var symObj = Object(Symbol('key')); typeof symObj;",
                "var sym = Symbol('key'); typeof sym;"
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeString(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testSymbolObjectUniqueness() {
        // Each Symbol() call creates a unique symbol, wrapped in different objects
        String code = """
                var sym1 = Object(Symbol('same'));
                var sym2 = Object(Symbol('same'));
                sym1 === sym2;""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testSymbolObjectValueOf() {
        // valueOf() should return the primitive symbol value, verify the type
        String code = """
                var symObj = Object(Symbol('test'));
                typeof symObj.valueOf();""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testSymbolObjectWithDifferentDescriptions() {
        Stream.of(
                // Test with string description
                "Object(Symbol('test')).description;",
                // Test with numeric description (should be converted to string)
                "Object(Symbol(123)).description;",
                // Test with boolean description
                "Object(Symbol(true)).description;",
                // Test with undefined description
                "Object(Symbol(undefined)).description;",
                // Test with null description (should convert to \"null\")
                "Object(Symbol(null)).description;",
                // Test with empty string
                "Object(Symbol('')).description;"
        ).forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeObject(),
                        () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testSymbolObjectWithWellKnownSymbols() {
        // Test wrapping well-known symbols with Object() - verify the type
        String code = "typeof Object(Symbol.iterator);";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }
}
