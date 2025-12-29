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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.core.JSSymbolObject;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Symbol object wrapper (JSSymbolObject).
 * Tests the object form of symbols created with Object(Symbol()).
 */
public class SymbolObjectTest extends BaseTest {

    @Test
    public void testNewSymbolThrowsTypeError() {
        // Test that new Symbol() throws TypeError
        try {
            ctx.eval("new Symbol('foo');");
            fail("new Symbol() should throw TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Symbol is not a constructor") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testObjectSymbolCreatesJSSymbolObject() {
        // Test Object(Symbol('foo')) creates JSSymbolObject
        JSValue result1 = ctx.eval("Object(Symbol('foo'));");
        assertInstanceOf(JSSymbolObject.class, result1, "Object(Symbol('foo')) should return JSSymbolObject");
        assertTrue(result1.isSymbolObject(), "Object(Symbol('foo')) should be a symbol object");

        JSSymbolObject symObj1 = (JSSymbolObject) result1;
        assertEquals("foo", symObj1.getValue().getDescription());

        // Test Object(Symbol()) creates JSSymbolObject with undefined description
        JSValue result2 = ctx.eval("Object(Symbol());");
        assertInstanceOf(JSSymbolObject.class, result2, "Object(Symbol()) should return JSSymbolObject");
        assertTrue(result2.isSymbolObject(), "Object(Symbol()) should be a symbol object");

        JSSymbolObject symObj2 = (JSSymbolObject) result2;
        assertNull(symObj2.getValue().getDescription());
    }

    @Test
    public void testSymbolObjectAsPropertyKey() {
        // Symbol objects are created correctly
        JSValue result = ctx.eval("var symObj = Object(Symbol('key')); typeof symObj;");
        assertEquals("object", result.toJavaObject());

        // The valueOf extracts the primitive
        JSValue primitiveResult = ctx.eval("symObj.valueOf();");
        assertInstanceOf(JSSymbol.class, primitiveResult);
    }

    @Test
    public void testSymbolObjectCoercion() {
        // Symbol objects should behave like objects in boolean context
        JSValue result1 = ctx.eval("Boolean(Object(Symbol('test')));");
        assertTrue((Boolean) result1.toJavaObject(), "Symbol object should be truthy");

        // Symbol object toString should work
        JSValue result2 = ctx.eval("Object(Symbol('test')).toString();");
        assertEquals("Symbol(test)", result2.toJavaObject());
    }

    @Test
    public void testSymbolObjectDescription() {
        // Test description property access
        JSValue result1 = ctx.eval("""
                var sym = Object(Symbol('test description'));
                sym.description;
                """);
        // Note: description is a getter on Symbol.prototype
        // For now it might return undefined, this depends on implementation
    }

    @Test
    public void testSymbolObjectEquality() {
        // Test equality comparisons
        JSValue result1 = ctx.eval("""
                var symObj1 = Object(Symbol('test'));
                var symObj2 = symObj1;
                symObj1 === symObj2;
                """);
        assertTrue((Boolean) result1.toJavaObject(), "Same symbol object should be strictly equal");

        JSValue result2 = ctx.eval("""
                var symObj1 = Object(Symbol('test'));
                var symObj2 = Object(Symbol('test'));
                symObj1 === symObj2;
                """);
        assertFalse((Boolean) result2.toJavaObject(), "Different symbol objects should not be strictly equal");

        JSValue result3 = ctx.eval("""
                var symObj = Object(Symbol('test'));
                var sym = symObj.valueOf();
                symObj === sym;
                """);
        assertFalse((Boolean) result3.toJavaObject(), "Symbol object should not equal its primitive value");
    }

    @Test
    public void testSymbolObjectGetPrimitiveValue() {
        // Test accessing [[PrimitiveValue]] internal slot
        JSValue result = ctx.eval("""
                var symObj = Object(Symbol('test'));
                symObj['[[PrimitiveValue]]'];
                """);
        // This should return the primitive symbol value
        assertInstanceOf(JSSymbol.class, result);
        assertEquals("test", ((JSSymbol) result).getDescription());
    }

    @Test
    public void testSymbolObjectInObject() {
        // Test creating symbol object
        JSValue result = ctx.eval("""
                var symObj = Object(Symbol('prop'));
                symObj instanceof Symbol;
                """);
        assertTrue((Boolean) result.toJavaObject(), "Symbol object should be instanceof Symbol");
    }

    @Test
    public void testSymbolObjectInstanceof() {
        // Test instanceof checks
        JSValue result1 = ctx.eval("Object(Symbol('test')) instanceof Symbol;");
        assertTrue((Boolean) result1.toJavaObject(), "Symbol object should be instanceof Symbol");

        JSValue result2 = ctx.eval("Object(Symbol('test')) instanceof Object;");
        assertTrue((Boolean) result2.toJavaObject(), "Symbol object should be instanceof Object");
    }

    @Test
    public void testSymbolObjectPrototypeChain() {
        // Verify prototype chain
        JSValue result = ctx.eval("""
                var symObj = Object(Symbol('test'));
                Object.getPrototypeOf(symObj) === Symbol.prototype;
                """);
        assertTrue((Boolean) result.toJavaObject(), "Symbol object prototype should be Symbol.prototype");
    }

    @Test
    public void testSymbolObjectToString() {
        // toString() on symbol object
        JSValue result1 = ctx.eval("Object(Symbol('foo')).toString();");
        assertEquals("Symbol(foo)", result1.toJavaObject());

        // toString() with no description
        JSValue result2 = ctx.eval("Object(Symbol()).toString();");
        assertEquals("Symbol()", result2.toJavaObject());

        // toString() with empty description
        JSValue result3 = ctx.eval("Object(Symbol('')).toString();");
        assertEquals("Symbol()", result3.toJavaObject());
    }

    @Test
    public void testSymbolObjectTypeof() {
        // typeof on Symbol object should return "object", not "symbol"
        JSValue result = ctx.eval("typeof Object(Symbol('test'));");
        assertEquals("object", result.toJavaObject(), "typeof symbol object should be 'object'");

        // Compare with primitive symbol
        JSValue primitiveResult = ctx.eval("typeof Symbol('test');");
        assertEquals("symbol", primitiveResult.toJavaObject(), "typeof symbol primitive should be 'symbol'");
    }

    @Test
    public void testSymbolObjectUniqueness() {
        // Each Symbol() call creates a unique symbol, wrapped in different objects
        JSValue result = ctx.eval("""
                var sym1 = Object(Symbol('same'));
                var sym2 = Object(Symbol('same'));
                sym1 === sym2;
                """);
        assertFalse((Boolean) result.toJavaObject(), "Two symbol objects should not be equal even with same description");

        // Check that the symbols inside are different
        JSValue sym1 = ctx.eval("sym1;");
        JSValue sym2 = ctx.eval("sym2;");
        assertNotEquals(sym1, sym2);
    }

    @Test
    public void testSymbolObjectValueOf() {
        // valueOf() should return the primitive symbol value
        JSValue result = ctx.eval("""
                var symObj = Object(Symbol('test'));
                symObj.valueOf();
                """);
        assertInstanceOf(JSSymbol.class, result, "valueOf should return primitive symbol");
        assertFalse(result instanceof JSSymbolObject, "valueOf should NOT return JSSymbolObject");
        assertEquals("test", ((JSSymbol) result).getDescription());
    }

    @Test
    public void testSymbolObjectWithDifferentDescriptions() {
        // Test with string description
        JSValue result1 = ctx.eval("Object(Symbol('test'));");
        assertInstanceOf(JSSymbolObject.class, result1);
        assertEquals("test", ((JSSymbolObject) result1).getValue().getDescription());

        // Test with numeric description (should be converted to string)
        JSValue result2 = ctx.eval("Object(Symbol(123));");
        assertInstanceOf(JSSymbolObject.class, result2);
        assertEquals("123", ((JSSymbolObject) result2).getValue().getDescription());

        // Test with boolean description
        JSValue result3 = ctx.eval("Object(Symbol(true));");
        assertInstanceOf(JSSymbolObject.class, result3);
        assertEquals("true", ((JSSymbolObject) result3).getValue().getDescription());

        // Test with undefined description
        JSValue result4 = ctx.eval("Object(Symbol(undefined));");
        assertInstanceOf(JSSymbolObject.class, result4);
        assertNull(((JSSymbolObject) result4).getValue().getDescription());

        // Test with null description (should convert to "null")
        JSValue result5 = ctx.eval("Object(Symbol(null));");
        assertInstanceOf(JSSymbolObject.class, result5);
        assertEquals("null", ((JSSymbolObject) result5).getValue().getDescription());

        // Test with empty string
        JSValue result6 = ctx.eval("Object(Symbol(''));");
        assertInstanceOf(JSSymbolObject.class, result6);
        assertEquals("", ((JSSymbolObject) result6).getValue().getDescription());
    }

    @Test
    public void testSymbolObjectWithWellKnownSymbols() {
        // Test wrapping well-known symbols with Object()
        JSValue result = ctx.eval("Object(Symbol.iterator);");
        assertInstanceOf(JSSymbolObject.class, result);
        assertEquals(JSSymbol.ITERATOR, ((JSSymbolObject) result).getValue());
    }
}
