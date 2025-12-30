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
}
