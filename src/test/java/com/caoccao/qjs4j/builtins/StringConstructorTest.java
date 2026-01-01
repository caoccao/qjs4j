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
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSStringObject;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

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

        Stream.of("typeof new String('a')", "typeof 'a'", "typeof String('a')").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeString(),
                        () -> context.eval(code).toJavaObject()));
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
        JSValue result1 = context.eval("String.fromCharCode(65);");
        assertThat(result1).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("A"));

        JSValue result2 = context.eval("String.fromCharCode(72, 101, 108, 108, 111);");
        assertThat(result2).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("Hello"));

        // Test with no arguments
        JSValue result3 = context.eval("String.fromCharCode();");
        assertThat(result3).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Test with Unicode characters (BMP) - using decimal values
        JSValue result4 = context.eval("String.fromCharCode(20013, 25991);");
        assertThat(result4).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("中文"));

        // Test with values > 65535 (should be taken modulo 65536)
        JSValue result5 = context.eval("String.fromCharCode(65536 + 65);");
        assertThat(result5).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("A"));

        // Test with negative numbers (converted to uint16)
        JSValue result6 = context.eval("String.fromCharCode(-1);");
        assertThat(result6).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(String.valueOf((char) 65535)));

        // Test with floating point numbers (truncated to integer)
        JSValue result7 = context.eval("String.fromCharCode(65.9);");
        assertThat(result7).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("A"));

        assertWithJavet(
                () -> v8Runtime.getExecutor("String.fromCharCode(72, 101, 108, 108, 111)").executeString(),
                () -> context.eval("String.fromCharCode(72, 101, 108, 108, 111)").toJavaObject());
    }

    @Test
    public void testStringFromCodePoint() {
        // Test basic ASCII characters
        JSValue result1 = context.eval("String.fromCodePoint(65);");
        assertThat(result1).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("A"));

        JSValue result2 = context.eval("String.fromCodePoint(72, 101, 108, 108, 111);");
        assertThat(result2).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("Hello"));

        // Test with no arguments
        JSValue result3 = context.eval("String.fromCodePoint();");
        assertThat(result3).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Test with Unicode characters (BMP) - using decimal values
        JSValue result4 = context.eval("String.fromCodePoint(20013, 25991);");
        assertThat(result4).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("中文"));

        // Test with supplementary characters (outside BMP) - using decimal values
        JSValue result5 = context.eval("String.fromCodePoint(128512);");
        assertThat(result5).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("😀"));

        JSValue result6 = context.eval("String.fromCodePoint(128077);");
        assertThat(result6).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("👍"));

        // Test error cases - invalid code points
        JSValue error1 = context.eval("try { String.fromCodePoint(-1); 'no error'; } catch(e) { e.name; }");
        assertThat(error1).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("RangeError"));

        JSValue error2 = context.eval("try { String.fromCodePoint(1114112); 'no error'; } catch(e) { e.name; }");
        assertThat(error2).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("RangeError"));

        JSValue error3 = context.eval("try { String.fromCodePoint(3.14); 'no error'; } catch(e) { e.name; }");
        assertThat(error3).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("RangeError"));

        assertWithJavet(
                () -> v8Runtime.getExecutor("String.fromCodePoint(72, 101, 108, 108, 111)").executeString(),
                () -> context.eval("String.fromCodePoint(72, 101, 108, 108, 111)").toJavaObject());
        assertWithJavet(
                () -> v8Runtime.getExecutor("String.fromCodePoint(128512)").executeString(),
                () -> context.eval("String.fromCodePoint(128512)").toJavaObject());
    }

    @Test
    public void testStringObjectCharAt() {
        JSValue result = context.eval("(new String('hello')).charAt(1);");
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("e"));
    }

    @Test
    public void testStringObjectConcat() {
        JSValue result = context.eval("(new String('hello')).concat(' ', 'world');");
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));
    }

    @Test
    public void testStringObjectIndexOf() {
        JSValue result = context.eval("(new String('hello')).indexOf('l');");
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(2.0));
    }

    @Test
    public void testStringObjectLength() {
        // Test that String object has length property
        JSValue result = context.eval("(new String('hello')).length;");
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(5.0));

        JSValue result2 = context.eval("(new String('')).length;");
        assertThat(result2).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(0.0));

        JSValue result3 = context.eval("(new String('test123')).length;");
        assertThat(result3).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(7.0));
    }

    @Test
    public void testStringObjectReplace() {
        JSValue result = context.eval("(new String('hello world')).replace('world', 'there');");
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello there"));
    }

    @Test
    public void testStringObjectSlice() {
        JSValue result = context.eval("(new String('hello world')).slice(0, 5);");
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));
    }

    @Test
    public void testStringObjectToLowerCase() {
        JSValue result = context.eval("(new String('HELLO')).toLowerCase();");
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));
    }

    @Test
    public void testStringObjectToString() {
        JSValue result1 = context.eval("(new String('hello')).toString();");
        assertThat(result1).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));

        JSValue result2 = context.eval("(new String('world')).toString();");
        assertThat(result2).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("world"));

        JSValue result3 = context.eval("(new String('')).toString();");
        assertThat(result3).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));
    }

    @Test
    public void testStringObjectToUpperCase() {
        JSValue result = context.eval("(new String('hello')).toUpperCase();");
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("HELLO"));
    }

    @Test
    public void testStringObjectTypeof() {
        JSValue result = context.eval("typeof new String('hello');");
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("object"));
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
        JSValue result1 = context.eval("String.raw({ raw: ['Hello', ' ', 'World'] }, 'beautiful');");
        assertThat(result1).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("Hellobeautiful World"));

        // Test with template object containing escape sequences
        // In JavaScript string literals, '\\n' becomes a newline character, not the literal \n
        JSValue result2 = context.eval("String.raw({ raw: ['Hello\\nWorld'] });");
        assertThat(result2).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("Hello\nWorld"));

        JSValue result3 = context.eval("String.raw({ raw: ['Line 1\\n', '\\tTabbed'] }, 'Line 2');");
        assertThat(result3).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("Line 1\nLine 2\tTabbed"));

        // Test with substitutions
        JSValue result4 = context.eval("String.raw({ raw: ['Hello ', '!'] }, 'World');");
        assertThat(result4).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("Hello World!"));

        JSValue result5 = context.eval("String.raw({ raw: ['', '+', '=', ''] }, 1, 2, 3);");
        assertThat(result5).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("1+2=3"));

        // Test with empty raw array
        JSValue result6 = context.eval("String.raw({ raw: [] });");
        assertThat(result6).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Test with single element
        JSValue result7 = context.eval("String.raw({ raw: ['Hello'] });");
        assertThat(result7).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("Hello"));

        // Test error cases
        JSValue error1 = context.eval("try { String.raw(); 'no error'; } catch(e) { e.name; }");
        assertThat(error1).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("TypeError"));

        JSValue error2 = context.eval("try { String.raw(null); 'no error'; } catch(e) { e.name; }");
        assertThat(error2).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("TypeError"));

        JSValue error3 = context.eval("try { String.raw({}); 'no error'; } catch(e) { e.name; }");
        assertThat(error3).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("TypeError"));

        // Test with unicode escape sequences in raw strings
        JSValue result8 = context.eval("String.raw({ raw: ['\\u4E2D\\u6587'] });");
        assertThat(result8).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("中文"));

        assertWithJavet(
                () -> v8Runtime.getExecutor("String.raw({ raw: ['Hello', ' ', 'World'] }, 'beautiful')").executeString(),
                () -> context.eval("String.raw({ raw: ['Hello', ' ', 'World'] }, 'beautiful')").toJavaObject());
    }

    @Test
    public void testStringTrim() {
        String code = "String.prototype.trim.call`  abc  `";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
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
        JSValue result1 = context.eval("String.raw`Hello\nWorld`");
        assertThat(result1).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("Hello\nWorld"));

        // Test String.raw with expressions
        JSValue result2 = context.eval("String.raw`Hello ${'beautiful'} World`");
        assertThat(result2).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("Hello beautiful World"));

        // Test String.raw with multiple expressions
        JSValue result3 = context.eval("String.raw`${1}+${2}=${3}`");
        assertThat(result3).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("1+2=3"));

        Stream.of("String.raw`Hello\nWorld`", "String.raw`Value: ${42}`")
                .forEach(code ->
                        assertWithJavet(
                                () -> v8Runtime.getExecutor(code).executeString(),
                                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testTemplateLiterals() {
        // Test basic template literal without expressions
        JSValue result1 = context.eval("`hello world`");
        assertThat(result1).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("hello world"));

        // Test template literal with single expression
        JSValue result2 = context.eval("const name = 'Alice'; `Hello ${name}!`");
        assertThat(result2).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("Hello Alice!"));

        // Test template literal with multiple expressions
        JSValue result3 = context.eval("`${1} + ${2} = ${1 + 2}`");
        assertThat(result3).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("1 + 2 = 3"));

        // Test template literal with expressions and text
        JSValue result4 = context.eval("const x = 10; const y = 20; `x=${x}, y=${y}, sum=${x+y}`");
        assertThat(result4).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("x=10, y=20, sum=30"));

        // Test empty template literal
        JSValue result5 = context.eval("``");
        assertThat(result5).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo(""));

        // Test template literal with newlines
        JSValue result6 = context.eval("`line1\\nline2`");
        assertThat(result6).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("line1\nline2"));

        assertWithJavet(
                () -> v8Runtime.getExecutor("`Hello ${'World'}!`").executeString(),
                () -> context.eval("`Hello ${'World'}!`").toJavaObject());
    }
}
