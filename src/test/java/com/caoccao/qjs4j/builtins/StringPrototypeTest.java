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
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StringPrototype methods.
 */
public class StringPrototypeTest extends BaseTest {
    protected JSString str;

    @BeforeEach
    public void setUp() {
        super.setUp();
        str = new JSString("hello world");
    }

    @Test
    public void testCharAt() {
        // Normal case
        JSValue result = StringPrototype.charAt(context, str, new JSValue[]{new JSNumber(0)});
        assertEquals("h", result.asString().map(JSString::value).orElseThrow());

        result = StringPrototype.charAt(context, str, new JSValue[]{new JSNumber(4)});
        assertEquals("o", result.asString().map(JSString::value).orElseThrow());

        // Default index 0
        result = StringPrototype.charAt(context, str, new JSValue[]{});
        assertEquals("h", result.asString().map(JSString::value).orElseThrow());

        // Out of bounds
        result = StringPrototype.charAt(context, str, new JSValue[]{new JSNumber(-1)});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        result = StringPrototype.charAt(context, str, new JSValue[]{new JSNumber(20)});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Non-integer index
        result = StringPrototype.charAt(context, str, new JSValue[]{new JSString("1")});
        assertEquals("e", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.charAt(context, empty, new JSValue[]{new JSNumber(0)});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Non-string thisArg
        result = StringPrototype.charAt(context, new JSNumber(42), new JSValue[]{new JSNumber(0)});
        assertEquals("4", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testCharCodeAt() {
        // Normal case
        JSValue result = StringPrototype.charCodeAt(context, str, new JSValue[]{new JSNumber(0)});
        assertEquals(104.0, result.asNumber().map(JSNumber::value).orElseThrow());

        result = StringPrototype.charCodeAt(context, str, new JSValue[]{new JSNumber(4)});
        assertEquals(111.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Default index 0
        result = StringPrototype.charCodeAt(context, str, new JSValue[]{});
        assertEquals(104.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Out of bounds
        result = StringPrototype.charCodeAt(context, str, new JSValue[]{new JSNumber(-1)});
        assertTrue(result.asNumber().isPresent() && Double.isNaN(result.asNumber().get().value()));

        result = StringPrototype.charCodeAt(context, str, new JSValue[]{new JSNumber(20)});
        assertTrue(result.asNumber().isPresent() && Double.isNaN(result.asNumber().get().value()));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.charCodeAt(context, empty, new JSValue[]{new JSNumber(0)});
        assertTrue(result.asNumber().isPresent() && Double.isNaN(result.asNumber().get().value()));
    }

    @Test
    public void testCodePointAt() {
        // Normal case
        JSValue result = StringPrototype.codePointAt(context, str, new JSValue[]{new JSNumber(0)});
        assertEquals(104.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Surrogate pair (if applicable)
        JSString surrogate = new JSString("😀");
        result = StringPrototype.codePointAt(context, surrogate, new JSValue[]{new JSNumber(0)});
        assertEquals(128512.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Out of bounds
        result = StringPrototype.codePointAt(context, str, new JSValue[]{new JSNumber(-1)});
        assertTrue(result.isUndefined());

        result = StringPrototype.codePointAt(context, str, new JSValue[]{new JSNumber(20)});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testConcat() {
        // Normal case
        JSValue result = StringPrototype.concat(context, str, new JSValue[]{new JSString(" test")});
        assertEquals("hello world test", result.asString().map(JSString::value).orElseThrow());

        // Multiple args
        result = StringPrototype.concat(context, str, new JSValue[]{new JSString(" "), new JSString("test")});
        assertEquals("hello world test", result.asString().map(JSString::value).orElseThrow());

        // No args
        result = StringPrototype.concat(context, str, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Non-string args
        result = StringPrototype.concat(context, str, new JSValue[]{new JSNumber(42)});
        assertEquals("hello world42", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.concat(context, empty, new JSValue[]{str});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testEndsWith() {
        // Normal case
        JSValue result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("world")});
        assertEquals(JSBoolean.TRUE, result);

        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("hello")});
        assertTrue(result.isBooleanFalse());

        // With position
        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("lo"), new JSNumber(5)});
        assertEquals(JSBoolean.TRUE, result);

        // Position beyond length
        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("world"), new JSNumber(50)});
        assertEquals(JSBoolean.TRUE, result);

        // Position 0
        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("world"), new JSNumber(0)});
        assertTrue(result.isBooleanFalse());

        // Empty search string
        result = StringPrototype.endsWith(context, str, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.endsWith(context, empty, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);
    }

    @Test
    public void testEquals() {
        // Verify that loose equality passes between primitive and primitive
        assertTrue(context.eval("'hello' == 'hello'").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("'hello' == 'world'").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(context.eval("'hello' == String('hello')").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("'hello' == String('world')").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality passes between primitive and primitive
        assertTrue(context.eval("'hello' === 'hello'").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("'hello' === 'world'").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(context.eval("'hello' === String('hello')").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("'hello' === String('world')").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality passes between primitive and primitive
        assertTrue(context.eval("String('hello') == String('hello')").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("String('hello') == String('world')").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality passes between primitive and object
        assertTrue(context.eval("'hello' == new String('hello')").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("'hello' == new String('world')").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(context.eval("String('hello') == new String('hello')").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(context.eval("String('hello') == new String('world')").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality fails between object and object
        assertFalse(context.eval("new String('hello') == new String('hello')").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality fails between primitive and object
        assertFalse(context.eval("'hello' === new String('hello')").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality fails between object and object
        assertFalse(context.eval("new String('hello') === new String('hello')").asBoolean().map(JSBoolean::value).orElseThrow());
    }

    @Test
    public void testGetLength() {
        // ASCII string
        assertEquals(11, str.value().length());

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        assertEquals(4, chinese.value().length());

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        assertEquals(6, emoji.value().length()); // Each emoji is 2 code units

        // Mixed
        JSString mixed = new JSString("Hello 你好 😀");
        assertEquals(11, mixed.value().length()); // H e l l o   你 好   😀 (2 for 😀)

        // Empty string
        JSString empty = new JSString("");
        assertEquals(0, empty.value().length());
    }

    @Test
    public void testIncludes() {
        // Normal case
        JSValue result = StringPrototype.includes(context, str, new JSValue[]{new JSString("world")});
        assertEquals(JSBoolean.TRUE, result);

        result = StringPrototype.includes(context, str, new JSValue[]{new JSString("test")});
        assertTrue(result.isBooleanFalse());

        // With position
        result = StringPrototype.includes(context, str, new JSValue[]{new JSString("world"), new JSNumber(6)});
        assertEquals(JSBoolean.TRUE, result);

        result = StringPrototype.includes(context, str, new JSValue[]{new JSString("world"), new JSNumber(7)});
        assertEquals(JSBoolean.FALSE, result);

        // Empty search string
        result = StringPrototype.includes(context, str, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.includes(context, empty, new JSValue[]{new JSString("test")});
        assertEquals(JSBoolean.FALSE, result);
    }

    @Test
    public void testIndexOf() {
        // Normal case
        JSValue result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("world")});
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElseThrow());

        result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("test")});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // With position
        result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("o"), new JSNumber(5)});
        assertEquals(7.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Position beyond length
        result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("world"), new JSNumber(50)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Empty search string
        result = StringPrototype.indexOf(context, str, new JSValue[]{new JSString("")});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.indexOf(context, empty, new JSValue[]{new JSString("test")});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());
    }

    @Test
    public void testLastIndexOf() {
        // Normal case
        JSValue result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("o")});
        assertEquals(7.0, result.asNumber().map(JSNumber::value).orElseThrow());

        result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("test")});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // With position
        result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("o"), new JSNumber(5)});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Position 0
        result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("h"), new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Empty search string
        result = StringPrototype.lastIndexOf(context, str, new JSValue[]{new JSString("")});
        assertEquals(11.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.lastIndexOf(context, empty, new JSValue[]{new JSString("test")});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());
    }

    @Test
    public void testPadEnd() {
        // Normal case
        JSValue result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(15), new JSString("*")});
        assertEquals("hello world****", result.asString().map(JSString::value).orElseThrow());

        // Shorter target length
        result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(5)});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Default filler
        result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(15)});
        assertEquals("hello world    ", result.asString().map(JSString::value).orElseThrow());

        // Empty filler
        result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(15), new JSString("")});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Long filler
        result = StringPrototype.padEnd(context, str, new JSValue[]{new JSNumber(15), new JSString("abc")});
        assertEquals("hello worldabca", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.padEnd(context, empty, new JSValue[]{new JSNumber(5), new JSString("*")});
        assertEquals("*****", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testPadStart() {
        // Normal case
        JSValue result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(15), new JSString("*")});
        assertEquals("****hello world", result.asString().map(JSString::value).orElseThrow());

        // Shorter target length
        result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(5)});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Default filler
        result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(15)});
        assertEquals("    hello world", result.asString().map(JSString::value).orElseThrow());

        // Empty filler
        result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(15), new JSString("")});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Long filler
        result = StringPrototype.padStart(context, str, new JSValue[]{new JSNumber(15), new JSString("abc")});
        assertEquals("abcahello world", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.padStart(context, empty, new JSValue[]{new JSNumber(5), new JSString("*")});
        assertEquals("*****", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testRepeat() {
        // Normal case
        JSValue result = StringPrototype.repeat(context, str, new JSValue[]{new JSNumber(2)});
        assertEquals("hello worldhello world", ((JSString) result).value());

        // Zero count
        result = StringPrototype.repeat(context, str, new JSValue[]{new JSNumber(0)});
        assertEquals("", ((JSString) result).value());

        // One count
        result = StringPrototype.repeat(context, str, new JSValue[]{new JSNumber(1)});
        assertEquals("hello world", ((JSString) result).value());

        // Negative count
        StringPrototype.repeat(context, str, new JSValue[]{new JSNumber(-1)});
        assertPendingException(context);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.repeat(context, empty, new JSValue[]{new JSNumber(3)});
        assertEquals("", ((JSString) result).value());
    }

    @Test
    public void testReplace() {
        // Normal case
        JSValue result = StringPrototype.replace(context, str, new JSValue[]{new JSString("world"), new JSString("universe")});
        assertEquals("hello universe", ((JSString) result).value());

        // No match
        result = StringPrototype.replace(context, str, new JSValue[]{new JSString("test"), new JSString("universe")});
        assertEquals("hello world", ((JSString) result).value());

        // Replace with function (simplified, assuming string replacement)
        result = StringPrototype.replace(context, str, new JSValue[]{new JSString("o"), new JSString("x")});
        assertEquals("hellx world", ((JSString) result).value());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.replace(context, empty, new JSValue[]{new JSString(""), new JSString("test")});
        assertEquals("test", ((JSString) result).value());
    }

    @Test
    public void testReplaceAll() {
        // Normal case
        JSValue result = StringPrototype.replaceAll(context, str, new JSValue[]{new JSString("o"), new JSString("x")});
        assertEquals("hellx wxrld", ((JSString) result).value());

        // No match
        result = StringPrototype.replaceAll(context, str, new JSValue[]{new JSString("test"), new JSString("universe")});
        assertEquals("hello world", ((JSString) result).value());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.replaceAll(context, empty, new JSValue[]{new JSString(""), new JSString("test")});
        assertEquals("", ((JSString) result).value());
    }

    @Test
    public void testSlice() {
        // Normal case
        JSValue result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(6)});
        assertEquals("world", ((JSString) result).value());

        result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertEquals("hello", ((JSString) result).value());

        // Negative indices
        result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(-5)});
        assertEquals("world", ((JSString) result).value());

        result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(0), new JSNumber(-6)});
        assertEquals("hello", ((JSString) result).value());

        // Out of bounds
        result = StringPrototype.slice(context, str, new JSValue[]{new JSNumber(50)});
        assertEquals("", ((JSString) result).value());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.slice(context, empty, new JSValue[]{new JSNumber(0)});
        assertEquals("", ((JSString) result).value());
    }

    @Test
    public void testSplit() {
        // Normal case
        JSValue result = StringPrototype.split(context, str, new JSValue[]{new JSString(" ")});
        JSArray arr = result.asArray().orElseThrow();
        assertEquals(2, arr.getLength());
        assertEquals("hello", arr.get(0).asString().map(JSString::value).orElseThrow());
        assertEquals("world", arr.get(1).asString().map(JSString::value).orElseThrow());

        // No separator
        result = StringPrototype.split(context, str, new JSValue[]{});
        arr = result.asArray().orElseThrow();
        assertEquals(1, arr.getLength());
        assertEquals("hello world", arr.get(0).asString().map(JSString::value).orElseThrow());

        // Empty separator
        result = StringPrototype.split(context, str, new JSValue[]{new JSString("")});
        arr = result.asArray().orElseThrow();
        assertEquals(11, arr.getLength()); // "h","e","l","l","o"," ","w","o","r","l","d"

        // Limit
        result = StringPrototype.split(context, str, new JSValue[]{new JSString(" "), new JSNumber(1)});
        arr = result.asArray().orElseThrow();
        assertEquals(1, arr.getLength());
        assertEquals("hello", arr.get(0).asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.split(context, empty, new JSValue[]{new JSString(" ")});
        arr = result.asArray().orElseThrow();
        assertEquals(1, arr.getLength());
        assertEquals("", arr.get(0).asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testStartsWith() {
        // Normal case
        JSValue result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("hello")});
        assertEquals(JSBoolean.TRUE, result);

        result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("world")});
        assertEquals(JSBoolean.FALSE, result);

        // With position
        result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("world"), new JSNumber(6)});
        assertEquals(JSBoolean.TRUE, result);

        // Position beyond length
        result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("hello"), new JSNumber(50)});
        assertEquals(JSBoolean.FALSE, result);

        // Empty search string
        result = StringPrototype.startsWith(context, str, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.startsWith(context, empty, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);
    }

    @Test
    public void testSubstr() {
        // Normal case
        JSValue result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(6), new JSNumber(5)});
        assertEquals("world", result.asString().map(JSString::value).orElseThrow());

        // No length
        result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(6)});
        assertEquals("world", result.asString().map(JSString::value).orElseThrow());

        // Negative start
        result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(-5), new JSNumber(5)});
        assertEquals("world", result.asString().map(JSString::value).orElseThrow());

        // Negative length
        result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(0), new JSNumber(-1)});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Out of bounds
        result = StringPrototype.substr(context, str, new JSValue[]{new JSNumber(50)});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.substr(context, empty, new JSValue[]{new JSNumber(0)});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        result = StringPrototype.substr(context, chinese, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertEquals("你好", result.asString().map(JSString::value).orElseThrow());

        result = StringPrototype.substr(context, chinese, new JSValue[]{new JSNumber(1), new JSNumber(2)});
        assertEquals("好世", result.asString().map(JSString::value).orElseThrow());

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        result = StringPrototype.substr(context, emoji, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertEquals("😀", result.asString().map(JSString::value).orElseThrow());

        result = StringPrototype.substr(context, emoji, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals("🌟", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testSubstring() {
        // Normal case
        JSValue result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(6)});
        assertEquals("world", result.asString().map(JSString::value).orElseThrow());

        result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertEquals("hello", result.asString().map(JSString::value).orElseThrow());

        // Swapped indices
        result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(5), new JSNumber(0)});
        assertEquals("hello", result.asString().map(JSString::value).orElseThrow());

        // Negative indices
        result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(-5)});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Out of bounds
        result = StringPrototype.substring(context, str, new JSValue[]{new JSNumber(50)});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.substring(context, empty, new JSValue[]{new JSNumber(0)});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        result = StringPrototype.substring(context, chinese, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertEquals("你好", result.asString().map(JSString::value).orElseThrow());

        result = StringPrototype.substring(context, chinese, new JSValue[]{new JSNumber(1), new JSNumber(3)});
        assertEquals("好世", result.asString().map(JSString::value).orElseThrow());

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        result = StringPrototype.substring(context, emoji, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertEquals("😀", result.asString().map(JSString::value).orElseThrow());

        result = StringPrototype.substring(context, emoji, new JSValue[]{new JSNumber(2), new JSNumber(4)});
        assertEquals("🌟", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testToLowerCase() {
        JSString upper = new JSString("HELLO WORLD");
        JSValue result = StringPrototype.toLowerCase(context, upper, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Already lower
        result = StringPrototype.toLowerCase(context, str, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Mixed
        JSString mixed = new JSString("HeLLo WoRLd");
        result = StringPrototype.toLowerCase(context, mixed, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toLowerCase(context, empty, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testToString() {
        // Normal case
        JSValue result = StringPrototype.toString_(context, str, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toString_(context, empty, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Non-string thisArg
        try {
            StringPrototype.toString_(context, new JSNumber(42), new JSValue[]{});
        } catch (Exception e) {
            assertEquals("TypeError: String.prototype.toString requires that 'this' be a String", e.getMessage());
        }
        try {
            context.eval("String.prototype.toString.call(123)");
        } catch (Exception e) {
            assertEquals("TypeError: String.prototype.toString requires that 'this' be a String", e.getMessage());
        }
    }

    @Test
    public void testToUpperCase() {
        JSValue result = StringPrototype.toUpperCase(context, str, new JSValue[]{});
        assertEquals("HELLO WORLD", result.asString().map(JSString::value).orElseThrow());

        // Already upper
        JSString upper = new JSString("HELLO WORLD");
        result = StringPrototype.toUpperCase(context, upper, new JSValue[]{});
        assertEquals("HELLO WORLD", result.asString().map(JSString::value).orElseThrow());

        // Mixed
        JSString mixed = new JSString("HeLLo WoRLd");
        result = StringPrototype.toUpperCase(context, mixed, new JSValue[]{});
        assertEquals("HELLO WORLD", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toUpperCase(context, empty, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());
    }

    @Test
    public void testTrim() {
        JSString spaced = new JSString("  hello world  ");
        JSValue result = StringPrototype.trim(context, spaced, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // No spaces
        result = StringPrototype.trim(context, str, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElseThrow());

        // Only spaces
        JSString spaces = new JSString("   ");
        result = StringPrototype.trim(context, spaces, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.trim(context, empty, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());
    }
}