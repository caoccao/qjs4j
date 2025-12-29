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
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSStringObject;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for String constructor.
 */
public class StringConstructorTest extends BaseTest {

    @Test
    public void testNewStringCreatesJSStringObject() {
        // Test new String("hello") creates JSStringObject
        JSValue result1 = context.eval("new String('hello');");
        assertInstanceOf(JSStringObject.class, result1, "new String('hello') should return JSStringObject");
        assertTrue(result1.isStringObject(), "new String('hello') should be a string object");

        JSStringObject strObj1 = (JSStringObject) result1;
        assertEquals("hello", strObj1.getValue().value());

        // Test new String("world") creates JSStringObject
        JSValue result2 = context.eval("new String('world');");
        assertInstanceOf(JSStringObject.class, result2, "new String('world') should return JSStringObject");
        assertTrue(result2.isStringObject(), "new String('world') should be a string object");

        JSStringObject strObj2 = (JSStringObject) result2;
        assertEquals("world", strObj2.getValue().value());
    }

    @Test
    public void testStringConstructorWithDifferentValues() {
        // Test with simple string
        JSValue result1 = context.eval("new String('test');");
        assertInstanceOf(JSStringObject.class, result1);
        assertEquals("test", ((JSStringObject) result1).getValue().value());

        // Test with empty string
        JSValue result2 = context.eval("new String('');");
        assertInstanceOf(JSStringObject.class, result2);
        assertEquals("", ((JSStringObject) result2).getValue().value());

        // Test with number to string conversion
        JSValue result3 = context.eval("new String(123);");
        assertInstanceOf(JSStringObject.class, result3);
        assertEquals("123", ((JSStringObject) result3).getValue().value());

        // Test with boolean to string conversion
        JSValue result4 = context.eval("new String(true);");
        assertInstanceOf(JSStringObject.class, result4);
        assertEquals("true", ((JSStringObject) result4).getValue().value());

        JSValue result5 = context.eval("new String(false);");
        assertInstanceOf(JSStringObject.class, result5);
        assertEquals("false", ((JSStringObject) result5).getValue().value());

        // Test with undefined (should be "undefined")
        JSValue result6 = context.eval("new String(undefined);");
        assertInstanceOf(JSStringObject.class, result6);
        assertEquals("undefined", ((JSStringObject) result6).getValue().value());

        // Test with null (should be "null")
        JSValue result7 = context.eval("new String(null);");
        assertInstanceOf(JSStringObject.class, result7);
        assertEquals("null", ((JSStringObject) result7).getValue().value());
    }

    @Test
    public void testStringConstructorWithNoArguments() {
        // Test new String() without arguments (should be empty string)
        JSValue result = context.eval("new String();");
        assertInstanceOf(JSStringObject.class, result);
        assertEquals("", ((JSStringObject) result).getValue().value());
    }

    @Test
    public void testStringObjectCharAt() {
        JSValue result = context.eval("(new String('hello')).charAt(1);");
        assertEquals("e", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStringObjectConcat() {
        JSValue result = context.eval("(new String('hello')).concat(' ', 'world');");
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStringObjectIndexOf() {
        JSValue result = context.eval("(new String('hello')).indexOf('l');");
        assertEquals(2.0, ((JSNumber) result).value());
    }

    @Test
    public void testStringObjectLength() {
        // Test that String object has length property
        JSValue result = context.eval("(new String('hello')).length;");
        assertEquals(5.0, ((JSNumber) result).value());

        JSValue result2 = context.eval("(new String('')).length;");
        assertEquals(0.0, ((JSNumber) result2).value());

        JSValue result3 = context.eval("(new String('test123')).length;");
        assertEquals(7.0, ((JSNumber) result3).value());
    }

    @Test
    public void testStringObjectReplace() {
        JSValue result = context.eval("(new String('hello world')).replace('world', 'there');");
        assertEquals("hello there", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStringObjectSlice() {
        JSValue result = context.eval("(new String('hello world')).slice(0, 5);");
        assertEquals("hello", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStringObjectToLowerCase() {
        JSValue result = context.eval("(new String('HELLO')).toLowerCase();");
        assertEquals("hello", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStringObjectToString() {
        JSValue result1 = context.eval("(new String('hello')).toString();");
        assertEquals("hello", result1.asString().map(JSString::value).orElseThrow());

        JSValue result2 = context.eval("(new String('world')).toString();");
        assertEquals("world", result2.asString().map(JSString::value).orElseThrow());

        JSValue result3 = context.eval("(new String('')).toString();");
        assertEquals("", result3.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStringObjectToUpperCase() {
        JSValue result = context.eval("(new String('hello')).toUpperCase();");
        assertEquals("HELLO", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStringObjectTypeof() {
        JSValue result = context.eval("typeof new String('hello');");
        assertEquals("object", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStringObjectValueOf() {
        JSValue result = context.eval("(new String('hello')).valueOf();");
        assertInstanceOf(JSString.class, result, "valueOf should return primitive");
        assertFalse(result instanceof JSStringObject, "valueOf should NOT return JSStringObject");
        assertEquals("hello", ((JSString) result).value());
    }

    @Test
    public void testStringWithoutNewReturnsPrimitive() {
        // Test String("hello") without new returns primitive
        JSValue result1 = context.eval("String('hello');");
        assertInstanceOf(JSString.class, result1, "String('hello') should return JSString primitive");
        assertFalse(result1 instanceof JSStringObject, "String('hello') should NOT be JSStringObject");
        assertEquals("hello", ((JSString) result1).value());

        // Test String("world") without new returns primitive
        JSValue result2 = context.eval("String('world');");
        assertInstanceOf(JSString.class, result2, "String('world') should return JSString primitive");
        assertFalse(result2 instanceof JSStringObject, "String('world') should NOT be JSStringObject");
        assertEquals("world", ((JSString) result2).value());
    }
}
