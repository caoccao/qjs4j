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
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StringPrototype methods.
 */
public class StringPrototypeTest extends BaseJavetTest {
    protected JSString str;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        str = new JSString("hello world");
    }

    @Test
    public void testCharAt() {
        // Normal case
        JSValue result = StringPrototype.charAt(context, str, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("h"));

        result = StringPrototype.charAt(context, str, new JSValue[]{new JSNumber(4)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("o"));

        // Default index 0
        result = StringPrototype.charAt(context, str, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("h"));

        // Out of bounds
        result = StringPrototype.charAt(context, str, new JSValue[]{new JSNumber(-1)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        result = StringPrototype.charAt(context, str, new JSValue[]{new JSNumber(20)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Non-integer index
        result = StringPrototype.charAt(context, str, new JSValue[]{new JSString("1")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("e"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.charAt(context, empty, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Non-string thisArg
        result = StringPrototype.charAt(context, new JSNumber(42), new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("4"));
    }

    @Test
    public void testCharCodeAt() {
        // Normal case
        JSValue result = StringPrototype.charCodeAt(context, str, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(104.0));

        result = StringPrototype.charCodeAt(context, str, new JSValue[]{new JSNumber(4)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(111.0));

        // Default index 0
        result = StringPrototype.charCodeAt(context, str, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(104.0));

        // Out of bounds
        result = StringPrototype.charCodeAt(context, str, new JSValue[]{new JSNumber(-1)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isNaN());

        result = StringPrototype.charCodeAt(context, str, new JSValue[]{new JSNumber(20)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isNaN());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.charCodeAt(context, empty, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isNaN());
    }

    @Test
    public void testCodePointAt() {
        // Normal case
        JSValue result = StringPrototype.codePointAt(context, str, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(104.0));

        // Surrogate pair (if applicable)
        JSString surrogate = new JSString("😀");
        result = StringPrototype.codePointAt(context, surrogate, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(128512.0));

        // Out of bounds
        result = StringPrototype.codePointAt(context, str, new JSValue[]{new JSNumber(-1)});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        result = StringPrototype.codePointAt(context, str, new JSValue[]{new JSNumber(20)});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testConcat() {
        // Normal case
        JSValue result = StringPrototype.concat(context, str, new JSValue[]{new JSString(" test")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world test"));

        // Multiple args
        result = StringPrototype.concat(context, str, new JSValue[]{new JSString(" "), new JSString("test")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world test"));

        // No args
        result = StringPrototype.concat(context, str, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Non-string args
        result = StringPrototype.concat(context, str, new JSValue[]{new JSNumber(42)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world42"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.concat(context, empty, new JSValue[]{str});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));
    }

    @Test
    public void testEndsWith() {
        // Normal case
        JSValue result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("world")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("hello")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // With position
        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("lo"), new JSNumber(5)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Position beyond length
        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("world"), new JSNumber(50)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Position 0
        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("world"), new JSNumber(0)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Empty search string
        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.endsWith(context, empty, new JSValue[]{new JSString("")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testEquals() {
        Stream.of(
                // Verify that loose equality passes between primitive and primitive
                "'hello' == 'hello'",
                "'hello' == 'world'",
                "'hello' == String('hello')",
                "'hello' == String('world')",
                // Verify that strict equality passes between primitive and primitive
                "'hello' === 'hello'",
                "'hello' === 'world'",
                "'hello' === String('hello')",
                "'hello' === String('world')",
                // Verify that loose equality passes between primitive and primitive
                "String('hello') == String('hello')",
                "String('hello') == String('world')",
                "String('hello') == 'hello'",
                "String('hello') == 'world'",
                // Verify that loose equality passes between primitive and object
                "'hello' == new String('hello')",
                "'hello' == new String('world')",
                "String('hello') == new String('hello')",
                "String('hello') == new String('world')",
                // Verify that loose equality fails between object and object
                "new String('hello') == new String('hello')",
                // Verify that strict equality fails between primitive and object
                "'hello' === new String('hello')",
                // Verify that strict equality fails between object and object
                "new String('hello') === new String('hello')").forEach(code -> {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeBoolean(),
                    () -> context.eval(code).toJavaObject());
        });
    }

    @Test
    public void testGetLength() {
        // ASCII string
        assertThat(str.value().length()).isEqualTo(11);

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        assertThat(chinese.value().length()).isEqualTo(4);

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        assertThat(emoji.value().length()).isEqualTo(6); // Each emoji is 2 code units

        // Mixed
        JSString mixed = new JSString("Hello 你好 😀");
        assertThat(mixed.value().length()).isEqualTo(11); // H e l l o   你 好   😀 (2 for 😀)

        // Empty string
        JSString empty = new JSString("");
        assertThat(empty.value().length()).isEqualTo(0);
    }

    @Test
    public void testIncludes() {
        // Normal case
        JSValue result = StringPrototype.includes(context, str, new JSValue[]{new JSString("world")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        result = StringPrototype.includes(context, str, new JSValue[]{new JSString("test")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // With position
        result = StringPrototype.includes(context, str, new JSValue[]{new JSString("world"), new JSNumber(6)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        result = StringPrototype.includes(context, str, new JSValue[]{new JSString("world"), new JSNumber(7)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Empty search string
        result = StringPrototype.includes(context, str, new JSValue[]{new JSString("")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.includes(context, empty, new JSValue[]{new JSString("test")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testIndexOf() {
        // Normal case
        JSValue result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("world")});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(6.0));

        result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("test")});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(-1.0));

        // With position
        result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("o"), new JSNumber(5)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(7.0));

        // Position beyond length
        result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("world"), new JSNumber(50)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(-1.0));

        // Empty search string
        result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("")});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(0.0));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.indexOf(context, empty, new JSValue[]{new JSString("test")});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(-1.0));
    }

    @Test
    public void testLastIndexOf() {
        // Normal case
        JSValue result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("o")});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(7.0));

        result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("test")});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(-1.0));

        // With position
        result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("o"), new JSNumber(5)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(4.0));

        // Position 0
        result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("h"), new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(0.0));

        // Empty search string
        result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("")});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(11.0));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.lastIndexOf(context, empty, new JSValue[]{new JSString("test")});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(-1.0));
    }

    @Test
    public void testPadEnd() {
        // Normal case
        JSValue result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(15), new JSString("*")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world****"));

        // Shorter target length
        result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(5)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Default filler
        result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(15)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world    "));

        // Empty filler
        result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(15), new JSString("")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Long filler
        result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(15), new JSString("abc")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello worldabca"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.padEnd(context, empty, new JSValue[]{new JSNumber(5), new JSString("*")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("*****"));
    }

    @Test
    public void testPadStart() {
        // Normal case
        JSValue result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(15), new JSString("*")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("****hello world"));

        // Shorter target length
        result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(5)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Default filler
        result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(15)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("    hello world"));

        // Empty filler
        result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(15), new JSString("")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Long filler
        result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(15), new JSString("abc")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("abcahello world"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.padStart(context, empty, new JSValue[]{new JSNumber(5), new JSString("*")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("*****"));
    }

    @Test
    public void testRepeat() {
        // Normal case
        JSValue result = StringPrototype.repeat(context, str, new JSValue[]{new JSNumber(2)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello worldhello world"));

        // Zero count
        result = StringPrototype.repeat(context, str, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // One count
        result = StringPrototype.repeat(context, str, new JSValue[]{new JSNumber(1)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Negative count
        StringPrototype.repeat(context, str, new JSValue[]{new JSNumber(-1)});
        assertPendingException(context);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.repeat(context, empty, new JSValue[]{new JSNumber(3)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));
    }

    @Test
    public void testReplace() {
        // Normal case
        JSValue result = StringPrototype.replace(context, str, new JSValue[]{new JSString("world"), new JSString("universe")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello universe"));

        // No match
        result = StringPrototype.replace(context, str, new JSValue[]{new JSString("test"), new JSString("universe")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Replace with function (simplified, assuming string replacement)
        result = StringPrototype.replace(context, str, new JSValue[]{new JSString("o"), new JSString("x")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hellx world"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.replace(context, empty, new JSValue[]{new JSString(""), new JSString("test")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("test"));
    }

    @Test
    public void testReplaceAll() {
        // Normal case
        JSValue result = StringPrototype.replaceAll(context, str, new JSValue[]{new JSString("o"), new JSString("x")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hellx wxrld"));

        // No match
        result = StringPrototype.replaceAll(context, str, new JSValue[]{new JSString("test"), new JSString("universe")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.replaceAll(context, empty, new JSValue[]{new JSString(""), new JSString("test")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));
    }

    @Test
    public void testSlice() {
        // Normal case
        JSValue result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(6)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("world"));

        result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));

        // Negative indices
        result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(-5)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("world"));

        result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(0), new JSNumber(-6)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));

        // Out of bounds
        result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(50)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.slice(context, empty, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));
    }

    @Test
    public void testSplit() {
        // Normal case
        JSValue result = StringPrototype.split(context, str, new JSValue[]{new JSString(" ")});
        JSArray arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(2);
        assertThat(arr.get(0)).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));
        assertThat(arr.get(1)).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("world"));

        // No separator
        result = StringPrototype.split(context, str, new JSValue[]{});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(1);
        assertThat(arr.get(0)).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Empty separator
        result = StringPrototype.split(context, str, new JSValue[]{new JSString("")});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(11); // "h","e","l","l","o"," ","w","o","r","l","d"

        // Limit
        result = StringPrototype.split(context, str, new JSValue[]{new JSString(" "), new JSNumber(1)});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(1);
        assertThat(arr.get(0)).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.split(context, empty, new JSValue[]{new JSString(" ")});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(1);
        assertThat(arr.get(0)).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));
    }

    @Test
    public void testStartsWith() {
        // Normal case
        JSValue result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("hello")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("world")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // With position
        result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("world"), new JSNumber(6)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Position beyond length
        result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("hello"), new JSNumber(50)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Empty search string
        result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.startsWith(context, empty, new JSValue[]{new JSString("")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testSubstr() {
        // Normal case
        JSValue result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(6), new JSNumber(5)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("world"));

        // No length
        result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(6)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("world"));

        // Negative start
        result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(-5), new JSNumber(5)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("world"));

        // Negative length
        result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(0), new JSNumber(-1)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Out of bounds
        result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(50)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.substr(context, empty, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        result = StringPrototype.substr(context, chinese, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("你好"));

        result = StringPrototype.substr(context, chinese, new JSValue[]{new JSNumber(1), new JSNumber(2)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("好世"));

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        result = StringPrototype.substr(context, emoji, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("😀"));

        result = StringPrototype.substr(context, emoji, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("🌟"));
    }

    @Test
    public void testSubstring() {
        // Normal case
        JSValue result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(6)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("world"));

        result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));

        // Swapped indices
        result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(5), new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));

        // Negative indices
        result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(-5)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Out of bounds
        result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(50)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.substring(context, empty, new JSValue[]{new JSNumber(0)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        result = StringPrototype.substring(context, chinese, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("你好"));

        result = StringPrototype.substring(context, chinese, new JSValue[]{new JSNumber(1), new JSNumber(3)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("好世"));

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        result = StringPrototype.substring(context, emoji, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("😀"));

        result = StringPrototype.substring(context, emoji, new JSValue[]{new JSNumber(2), new JSNumber(4)});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("🌟"));
    }

    @Test
    public void testToLowerCase() {
        JSString upper = new JSString("HELLO WORLD");
        JSValue result = StringPrototype.toLowerCase(context, upper, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Already lower
        result = StringPrototype.toLowerCase(context, str, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Mixed
        JSString mixed = new JSString("HeLLo WoRLd");
        result = StringPrototype.toLowerCase(context, mixed, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toLowerCase(context, empty, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));
    }

    @Test
    public void testToString() {
        // Normal case
        JSValue result = StringPrototype.toString_(context, str, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toString_(context, empty, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Non-string thisArg
        try {
            StringPrototype.toString_(context, new JSNumber(42), new JSValue[]{});
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("TypeError: String.prototype.toString requires that 'this' be a String");
        }
        try {
            context.eval("String.prototype.toString.call(123)");
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("TypeError: String.prototype.toString requires that 'this' be a String");
        }
    }

    @Test
    public void testToUpperCase() {
        JSValue result = StringPrototype.toUpperCase(context, str, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("HELLO WORLD"));

        // Already upper
        JSString upper = new JSString("HELLO WORLD");
        result = StringPrototype.toUpperCase(context, upper, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("HELLO WORLD"));

        // Mixed
        JSString mixed = new JSString("HeLLo WoRLd");
        result = StringPrototype.toUpperCase(context, mixed, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("HELLO WORLD"));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toUpperCase(context, empty, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));
    }

    @Test
    public void testTrim() {
        JSString spaced = new JSString("  hello world  ");
        JSValue result = StringPrototype.trim(context, spaced, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // No spaces
        result = StringPrototype.trim(context, str, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Only spaces
        JSString spaces = new JSString("   ");
        result = StringPrototype.trim(context, spaces, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.trim(context, empty, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));
    }
}