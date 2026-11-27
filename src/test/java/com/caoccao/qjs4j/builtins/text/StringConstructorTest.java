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

package com.caoccao.qjs4j.builtins.text;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSStringObject;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for String constructor.
 */
public class StringConstructorTest extends BaseJavetTest {

    @Test
    public void testNewStringCreatesJSStringObject() {
        // Test new String("hello") creates JSStringObject
        JSValue result1 = context.eval("new String('hello');");
        assertThat(result1).isInstanceOf(JSStringObject.class);
        assertThat(result1.isStringObject()).isTrue();

        JSStringObject strObj1 = (JSStringObject) result1;
        assertThat(strObj1.getValue().value()).isEqualTo("hello");

        // Test new String("world") creates JSStringObject
        JSValue result2 = context.eval("new String('world');");
        assertThat(result2).isInstanceOf(JSStringObject.class);
        assertThat(result2.isStringObject()).isTrue();

        JSStringObject strObj2 = (JSStringObject) result2;
        assertThat(strObj2.getValue().value()).isEqualTo("world");

        assertStringWithJavet("typeof new String('a')", "typeof 'a'", "typeof String('a')");
    }

    @Test
    public void testStringConstructorWithDifferentValues() {
        // Test with simple string
        JSValue result1 = context.eval("new String('test');");
        assertThat(result1).isInstanceOf(JSStringObject.class);
        assertThat(((JSStringObject) result1).getValue().value()).isEqualTo("test");

        // Test with empty string
        JSValue result2 = context.eval("new String('');");
        assertThat(result2).isInstanceOf(JSStringObject.class);
        assertThat(((JSStringObject) result2).getValue().value()).isEqualTo("");

        // Test with number to string conversion
        JSValue result3 = context.eval("new String(123);");
        assertThat(result3).isInstanceOf(JSStringObject.class);
        assertThat(((JSStringObject) result3).getValue().value()).isEqualTo("123");

        // Test with boolean to string conversion
        JSValue result4 = context.eval("new String(true);");
        assertThat(result4).isInstanceOf(JSStringObject.class);
        assertThat(((JSStringObject) result4).getValue().value()).isEqualTo("true");

        JSValue result5 = context.eval("new String(false);");
        assertThat(result5).isInstanceOf(JSStringObject.class);
        assertThat(((JSStringObject) result5).getValue().value()).isEqualTo("false");

        // Test with undefined (should be "undefined")
        JSValue result6 = context.eval("new String(undefined);");
        assertThat(result6).isInstanceOf(JSStringObject.class);
        assertThat(((JSStringObject) result6).getValue().value()).isEqualTo("undefined");

        // Test with null (should be "null")
        JSValue result7 = context.eval("new String(null);");
        assertThat(result7).isInstanceOf(JSStringObject.class);
        assertThat(((JSStringObject) result7).getValue().value()).isEqualTo("null");
    }

    @Test
    public void testStringConstructorWithNoArguments() {
        // Test new String() without arguments (should be empty string)
        JSValue result = context.eval("new String();");
        assertThat(result).isInstanceOf(JSStringObject.class);
        assertThat(((JSStringObject) result).getValue().value()).isEqualTo("");
    }

    @Test
    public void testStringFromCharCode() {
        // Test basic ASCII characters
        assertEvalString("String.fromCharCode(65);", "A");

        assertEvalString("String.fromCharCode(72, 101, 108, 108, 111);", "Hello");

        // Test with no arguments
        assertEvalString("String.fromCharCode();", "");

        // Test with Unicode characters (BMP) - using decimal values
        assertEvalString("String.fromCharCode(20013, 25991);", "中文");

        // Test with values > 65535 (should be taken modulo 65536)
        assertEvalString("String.fromCharCode(65536 + 65);", "A");

        // Test with negative numbers (converted to uint16)
        assertStringValue(context.eval("String.fromCharCode(-1);"), String.valueOf((char) 65535));

        // Test with floating point numbers (truncated to integer)
        assertEvalString("String.fromCharCode(65.9);", "A");

        assertStringWithJavet("String.fromCharCode(72, 101, 108, 108, 111)");
    }

    @Test
    public void testStringFromCodePoint() {
        // Test basic ASCII characters
        assertEvalString("String.fromCodePoint(65);", "A");

        assertEvalString("String.fromCodePoint(72, 101, 108, 108, 111);", "Hello");

        // Test with no arguments
        assertEvalString("String.fromCodePoint();", "");

        // Test with Unicode characters (BMP) - using decimal values
        assertEvalString("String.fromCodePoint(20013, 25991);", "中文");

        // Test with supplementary characters (outside BMP) - using decimal values
        assertEvalString("String.fromCodePoint(128512);", "😀");

        assertEvalString("String.fromCodePoint(128077);", "👍");

        // Test error cases - invalid code points
        assertEvalString("try { String.fromCodePoint(-1); 'no error'; } catch(e) { e.name; }", "RangeError");

        assertEvalString("try { String.fromCodePoint(1114112); 'no error'; } catch(e) { e.name; }", "RangeError");

        assertEvalString("try { String.fromCodePoint(3.14); 'no error'; } catch(e) { e.name; }", "RangeError");

        assertStringWithJavet("String.fromCodePoint(72, 101, 108, 108, 111)", "String.fromCodePoint(128512)");
    }

    @Test
    public void testStringObjectCharAt() {
        assertEvalString("(new String('hello')).charAt(1);", "e");
    }

    @Test
    public void testStringObjectConcat() {
        assertEvalString("(new String('hello')).concat(' ', 'world');", "hello world");
    }

    @Test
    public void testStringObjectIndexOf() {
        assertEvalNumber("(new String('hello')).indexOf('l');", 2.0);
    }

    @Test
    public void testStringObjectLength() {
        // Test that String object has length property
        assertEvalNumber("(new String('hello')).length;", 5.0);

        assertEvalNumber("(new String('')).length;", 0.0);

        assertEvalNumber("(new String('test123')).length;", 7.0);
    }

    @Test
    public void testStringObjectReplace() {
        assertEvalString("(new String('hello world')).replace('world', 'there');", "hello there");
    }

    @Test
    public void testStringObjectSlice() {
        assertEvalString("(new String('hello world')).slice(0, 5);", "hello");
    }

    @Test
    public void testStringObjectToLowerCase() {
        assertEvalString("(new String('HELLO')).toLowerCase();", "hello");
    }

    @Test
    public void testStringObjectToString() {
        assertEvalString("(new String('hello')).toString();", "hello");

        assertEvalString("(new String('world')).toString();", "world");

        assertEvalString("(new String('')).toString();", "");
    }

    @Test
    public void testStringObjectToUpperCase() {
        assertEvalString("(new String('hello')).toUpperCase();", "HELLO");
    }

    @Test
    public void testStringObjectTypeof() {
        assertEvalString("typeof new String('hello');", "object");
    }

    @Test
    public void testStringObjectValueOf() {
        JSValue result = context.eval("(new String('hello')).valueOf();");
        assertThat(result).isInstanceOf(JSString.class);
        assertThat(result).isNotInstanceOf(JSStringObject.class);
        assertThat(((JSString) result).value()).isEqualTo("hello");
    }

    @Test
    public void testStringRaw() {
        // Test basic usage with template object
        assertEvalString("String.raw({ raw: ['Hello', ' ', 'World'] }, 'beautiful');", "Hellobeautiful World");

        // Test with template object containing escape sequences
        // In JavaScript string literals, '\\n' becomes a newline character, not the literal \n
        assertEvalString("String.raw({ raw: ['Hello\\nWorld'] });", "Hello\nWorld");

        assertEvalString("String.raw({ raw: ['Line 1\\n', '\\tTabbed'] }, 'Line 2');", "Line 1\nLine 2\tTabbed");

        // Test with substitutions
        assertEvalString("String.raw({ raw: ['Hello ', '!'] }, 'World');", "Hello World!");

        assertEvalString("String.raw({ raw: ['', '+', '=', ''] }, 1, 2, 3);", "1+2=3");

        // Test with empty raw array
        assertEvalString("String.raw({ raw: [] });", "");

        // Test with single element
        assertEvalString("String.raw({ raw: ['Hello'] });", "Hello");

        // Test error cases
        assertEvalString("try { String.raw(); 'no error'; } catch(e) { e.name; }", "TypeError");

        assertEvalString("try { String.raw(null); 'no error'; } catch(e) { e.name; }", "TypeError");

        assertEvalString("try { String.raw({}); 'no error'; } catch(e) { e.name; }", "TypeError");

        // Test with unicode escape sequences in raw strings
        assertEvalString("String.raw({ raw: ['\\u4E2D\\u6587'] });", "中文");

        assertStringWithJavet("String.raw({ raw: ['Hello', ' ', 'World'] }, 'beautiful')");
    }

    @Test
    public void testStringTrim() {
        assertStringWithJavet("String.prototype.trim.call`  abc  `");
    }

    @Test
    public void testStringWithoutNewReturnsPrimitive() {
        // Test String("hello") without new returns primitive
        JSValue result1 = context.eval("String('hello');");
        assertThat(result1).isInstanceOf(JSString.class);
        assertThat(result1).isNotInstanceOf(JSStringObject.class);
        assertThat(((JSString) result1).value()).isEqualTo("hello");

        // Test String("world") without new returns primitive
        JSValue result2 = context.eval("String('world');");
        assertThat(result2).isInstanceOf(JSString.class);
        assertThat(result2).isNotInstanceOf(JSStringObject.class);
        assertThat(((JSString) result2).value()).isEqualTo("world");
    }

    @Test
    public void testTaggedTemplateLiterals() {
        // Test String.raw with template literal
        assertEvalString("String.raw`Hello\nWorld`", "Hello\nWorld");

        // Test String.raw with expressions
        assertEvalString("String.raw`Hello ${'beautiful'} World`", "Hello beautiful World");

        // Test String.raw with multiple expressions
        assertEvalString("String.raw`${1}+${2}=${3}`", "1+2=3");

        assertStringWithJavet("String.raw`Hello\nWorld`", "String.raw`Value: ${42}`");
    }

    @Test
    public void testTemplateLiterals() {
        // Test basic template literal without expressions
        assertEvalString("`hello world`", "hello world");

        // Test template literal with single expression
        assertEvalString("const name = 'Alice'; `Hello ${name}!`", "Hello Alice!");

        // Test template literal with multiple expressions
        assertEvalString("`${1} + ${2} = ${1 + 2}`", "1 + 2 = 3");

        // Test template literal with expressions and text
        assertEvalString("const x = 10; const y = 20; `x=${x}, y=${y}, sum=${x+y}`", "x=10, y=20, sum=30");

        // Test empty template literal
        assertEvalString("``", "");

        // Test template literal with newlines
        assertEvalString("`line1\\nline2`", "line1\nline2");

        assertStringWithJavet("`Hello ${'World'}!`");
    }
}
