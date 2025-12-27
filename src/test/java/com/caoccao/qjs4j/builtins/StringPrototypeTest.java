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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StringPrototype methods.
 */
public class StringPrototypeTest extends BaseTest {
    @Test
    public void testCharAt() {
        // Normal case
        JSValue result = StringPrototype.charAt(ctx, str, new JSValue[]{new JSNumber(0)});
        assertEquals("h", result.asString().map(JSString::value).orElse(""));

        result = StringPrototype.charAt(ctx, str, new JSValue[]{new JSNumber(4)});
        assertEquals("o", result.asString().map(JSString::value).orElse(""));

        // Default index 0
        result = StringPrototype.charAt(ctx, str, new JSValue[]{});
        assertEquals("h", result.asString().map(JSString::value).orElse(""));

        // Out of bounds
        result = StringPrototype.charAt(ctx, str, new JSValue[]{new JSNumber(-1)});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        result = StringPrototype.charAt(ctx, str, new JSValue[]{new JSNumber(20)});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Non-integer index
        result = StringPrototype.charAt(ctx, str, new JSValue[]{new JSString("1")});
        assertEquals("e", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.charAt(ctx, empty, new JSValue[]{new JSNumber(0)});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Non-string thisArg
        result = StringPrototype.charAt(ctx, new JSNumber(42), new JSValue[]{new JSNumber(0)});
        assertEquals("4", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testCharCodeAt() {
        // Normal case
        JSValue result = StringPrototype.charCodeAt(ctx, str, new JSValue[]{new JSNumber(0)});
        assertEquals(104.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        result = StringPrototype.charCodeAt(ctx, str, new JSValue[]{new JSNumber(4)});
        assertEquals(111.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Default index 0
        result = StringPrototype.charCodeAt(ctx, str, new JSValue[]{});
        assertEquals(104.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Out of bounds
        result = StringPrototype.charCodeAt(ctx, str, new JSValue[]{new JSNumber(-1)});
        assertTrue(result.asNumber().isPresent() && Double.isNaN(result.asNumber().get().value()));

        result = StringPrototype.charCodeAt(ctx, str, new JSValue[]{new JSNumber(20)});
        assertTrue(result.asNumber().isPresent() && Double.isNaN(result.asNumber().get().value()));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.charCodeAt(ctx, empty, new JSValue[]{new JSNumber(0)});
        assertTrue(result.asNumber().isPresent() && Double.isNaN(result.asNumber().get().value()));
    }

    @Test
    public void testCodePointAt() {
        // Normal case
        JSValue result = StringPrototype.codePointAt(ctx, str, new JSValue[]{new JSNumber(0)});
        assertEquals(104.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Surrogate pair (if applicable)
        JSString surrogate = new JSString("😀");
        result = StringPrototype.codePointAt(ctx, surrogate, new JSValue[]{new JSNumber(0)});
        assertEquals(128512.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Out of bounds
        result = StringPrototype.codePointAt(ctx, str, new JSValue[]{new JSNumber(-1)});
        assertTrue(result.isUndefined());

        result = StringPrototype.codePointAt(ctx, str, new JSValue[]{new JSNumber(20)});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testConcat() {
        // Normal case
        JSValue result = StringPrototype.concat(ctx, str, new JSValue[]{new JSString(" test")});
        assertEquals("hello world test", result.asString().map(JSString::value).orElse(""));

        // Multiple args
        result = StringPrototype.concat(ctx, str, new JSValue[]{new JSString(" "), new JSString("test")});
        assertEquals("hello world test", result.asString().map(JSString::value).orElse(""));

        // No args
        result = StringPrototype.concat(ctx, str, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Non-string args
        result = StringPrototype.concat(ctx, str, new JSValue[]{new JSNumber(42)});
        assertEquals("hello world42", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.concat(ctx, empty, new JSValue[]{str});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testEndsWith() {
        // Normal case
        JSValue result = StringPrototype.endsWith(ctx, str, new JSValue[]{new JSString("world")});
        assertEquals(JSBoolean.TRUE, result);

        result = StringPrototype.endsWith(ctx, str, new JSValue[]{new JSString("hello")});
        assertTrue(result.isBooleanFalse());

        // With position
        result = StringPrototype.endsWith(ctx, str, new JSValue[]{new JSString("lo"), new JSNumber(5)});
        assertEquals(JSBoolean.TRUE, result);

        // Position beyond length
        result = StringPrototype.endsWith(ctx, str, new JSValue[]{new JSString("world"), new JSNumber(50)});
        assertEquals(JSBoolean.TRUE, result);

        // Position 0
        result = StringPrototype.endsWith(ctx, str, new JSValue[]{new JSString("world"), new JSNumber(0)});
        assertTrue(result.isBooleanFalse());

        // Empty search string
        result = StringPrototype.endsWith(ctx, str, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.endsWith(ctx, empty, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);
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
        JSValue result = StringPrototype.includes(ctx, str, new JSValue[]{new JSString("world")});
        assertEquals(JSBoolean.TRUE, result);

        result = StringPrototype.includes(ctx, str, new JSValue[]{new JSString("test")});
        assertTrue(result.isBooleanFalse());

        // With position
        result = StringPrototype.includes(ctx, str, new JSValue[]{new JSString("world"), new JSNumber(6)});
        assertEquals(JSBoolean.TRUE, result);

        result = StringPrototype.includes(ctx, str, new JSValue[]{new JSString("world"), new JSNumber(7)});
        assertEquals(JSBoolean.FALSE, result);

        // Empty search string
        result = StringPrototype.includes(ctx, str, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.includes(ctx, empty, new JSValue[]{new JSString("test")});
        assertEquals(JSBoolean.FALSE, result);
    }

    @Test
    public void testIndexOf() {
        // Normal case
        JSValue result = StringPrototype.indexOf(ctx, str, new JSValue[]{new JSString("world")});
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        result = StringPrototype.indexOf(ctx, str, new JSValue[]{new JSString("test")});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // With position
        result = StringPrototype.indexOf(ctx, str, new JSValue[]{new JSString("o"), new JSNumber(5)});
        assertEquals(7.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Position beyond length
        result = StringPrototype.indexOf(ctx, str, new JSValue[]{new JSString("world"), new JSNumber(50)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Empty search string
        result = StringPrototype.indexOf(ctx, str, new JSValue[]{new JSString("")});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.indexOf(ctx, empty, new JSValue[]{new JSString("test")});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0.0));
    }

    @Test
    public void testLastIndexOf() {
        // Normal case
        JSValue result = StringPrototype.lastIndexOf(ctx, str, new JSValue[]{new JSString("o")});
        assertEquals(7.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        result = StringPrototype.lastIndexOf(ctx, str, new JSValue[]{new JSString("test")});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // With position
        result = StringPrototype.lastIndexOf(ctx, str, new JSValue[]{new JSString("o"), new JSNumber(5)});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Position 0
        result = StringPrototype.lastIndexOf(ctx, str, new JSValue[]{new JSString("h"), new JSNumber(0)});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Empty search string
        result = StringPrototype.lastIndexOf(ctx, str, new JSValue[]{new JSString("")});
        assertEquals(11.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.lastIndexOf(ctx, empty, new JSValue[]{new JSString("test")});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0.0));
    }

    @Test
    public void testPadEnd() {
        // Normal case
        JSValue result = StringPrototype.padEnd(ctx, str, new JSValue[]{new JSNumber(15), new JSString("*")});
        assertEquals("hello world****", result.asString().map(JSString::value).orElse(""));

        // Shorter target length
        result = StringPrototype.padEnd(ctx, str, new JSValue[]{new JSNumber(5)});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Default filler
        result = StringPrototype.padEnd(ctx, str, new JSValue[]{new JSNumber(15)});
        assertEquals("hello world    ", result.asString().map(JSString::value).orElse(""));

        // Empty filler
        result = StringPrototype.padEnd(ctx, str, new JSValue[]{new JSNumber(15), new JSString("")});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Long filler
        result = StringPrototype.padEnd(ctx, str, new JSValue[]{new JSNumber(15), new JSString("abc")});
        assertEquals("hello worldabca", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.padEnd(ctx, empty, new JSValue[]{new JSNumber(5), new JSString("*")});
        assertEquals("*****", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testPadStart() {
        // Normal case
        JSValue result = StringPrototype.padStart(ctx, str, new JSValue[]{new JSNumber(15), new JSString("*")});
        assertEquals("****hello world", result.asString().map(JSString::value).orElse(""));

        // Shorter target length
        result = StringPrototype.padStart(ctx, str, new JSValue[]{new JSNumber(5)});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Default filler
        result = StringPrototype.padStart(ctx, str, new JSValue[]{new JSNumber(15)});
        assertEquals("    hello world", result.asString().map(JSString::value).orElse(""));

        // Empty filler
        result = StringPrototype.padStart(ctx, str, new JSValue[]{new JSNumber(15), new JSString("")});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Long filler
        result = StringPrototype.padStart(ctx, str, new JSValue[]{new JSNumber(15), new JSString("abc")});
        assertEquals("abcahello world", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.padStart(ctx, empty, new JSValue[]{new JSNumber(5), new JSString("*")});
        assertEquals("*****", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testRepeat() {
        // Normal case
        JSValue result = StringPrototype.repeat(ctx, str, new JSValue[]{new JSNumber(2)});
        assertEquals("hello worldhello world", ((JSString) result).value());

        // Zero count
        result = StringPrototype.repeat(ctx, str, new JSValue[]{new JSNumber(0)});
        assertEquals("", ((JSString) result).value());

        // One count
        result = StringPrototype.repeat(ctx, str, new JSValue[]{new JSNumber(1)});
        assertEquals("hello world", ((JSString) result).value());

        // Negative count
        StringPrototype.repeat(ctx, str, new JSValue[]{new JSNumber(-1)});
        assertPendingException(ctx);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.repeat(ctx, empty, new JSValue[]{new JSNumber(3)});
        assertEquals("", ((JSString) result).value());
    }

    @Test
    public void testReplace() {
        // Normal case
        JSValue result = StringPrototype.replace(ctx, str, new JSValue[]{new JSString("world"), new JSString("universe")});
        assertEquals("hello universe", ((JSString) result).value());

        // No match
        result = StringPrototype.replace(ctx, str, new JSValue[]{new JSString("test"), new JSString("universe")});
        assertEquals("hello world", ((JSString) result).value());

        // Replace with function (simplified, assuming string replacement)
        result = StringPrototype.replace(ctx, str, new JSValue[]{new JSString("o"), new JSString("x")});
        assertEquals("hellx world", ((JSString) result).value());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.replace(ctx, empty, new JSValue[]{new JSString(""), new JSString("test")});
        assertEquals("test", ((JSString) result).value());
    }

    @Test
    public void testReplaceAll() {
        // Normal case
        JSValue result = StringPrototype.replaceAll(ctx, str, new JSValue[]{new JSString("o"), new JSString("x")});
        assertEquals("hellx wxrld", ((JSString) result).value());

        // No match
        result = StringPrototype.replaceAll(ctx, str, new JSValue[]{new JSString("test"), new JSString("universe")});
        assertEquals("hello world", ((JSString) result).value());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.replaceAll(ctx, empty, new JSValue[]{new JSString(""), new JSString("test")});
        assertEquals("", ((JSString) result).value());
    }

    @Test
    public void testSlice() {
        // Normal case
        JSValue result = StringPrototype.slice(ctx, str, new JSValue[]{new JSNumber(6)});
        assertEquals("world", ((JSString) result).value());

        result = StringPrototype.slice(ctx, str, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertEquals("hello", ((JSString) result).value());

        // Negative indices
        result = StringPrototype.slice(ctx, str, new JSValue[]{new JSNumber(-5)});
        assertEquals("world", ((JSString) result).value());

        result = StringPrototype.slice(ctx, str, new JSValue[]{new JSNumber(0), new JSNumber(-6)});
        assertEquals("hello", ((JSString) result).value());

        // Out of bounds
        result = StringPrototype.slice(ctx, str, new JSValue[]{new JSNumber(50)});
        assertEquals("", ((JSString) result).value());

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.slice(ctx, empty, new JSValue[]{new JSNumber(0)});
        assertEquals("", ((JSString) result).value());
    }

    @Test
    public void testSplit() {
        // Normal case
        JSValue result = StringPrototype.split(ctx, str, new JSValue[]{new JSString(" ")});
        JSArray arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(2, arr.getLength());
        assertEquals("hello", arr.get(0).asString().map(JSString::value).orElse(""));
        assertEquals("world", arr.get(1).asString().map(JSString::value).orElse(""));

        // No separator
        result = StringPrototype.split(ctx, str, new JSValue[]{});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(1, arr.getLength());
        assertEquals("hello world", arr.get(0).asString().map(JSString::value).orElse(""));

        // Empty separator
        result = StringPrototype.split(ctx, str, new JSValue[]{new JSString("")});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(11, arr.getLength()); // "h","e","l","l","o"," ","w","o","r","l","d"

        // Limit
        result = StringPrototype.split(ctx, str, new JSValue[]{new JSString(" "), new JSNumber(1)});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(1, arr.getLength());
        assertEquals("hello", arr.get(0).asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.split(ctx, empty, new JSValue[]{new JSString(" ")});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(1, arr.getLength());
        assertEquals("", arr.get(0).asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testStartsWith() {
        // Normal case
        JSValue result = StringPrototype.startsWith(ctx, str, new JSValue[]{new JSString("hello")});
        assertEquals(JSBoolean.TRUE, result);

        result = StringPrototype.startsWith(ctx, str, new JSValue[]{new JSString("world")});
        assertEquals(JSBoolean.FALSE, result);

        // With position
        result = StringPrototype.startsWith(ctx, str, new JSValue[]{new JSString("world"), new JSNumber(6)});
        assertEquals(JSBoolean.TRUE, result);

        // Position beyond length
        result = StringPrototype.startsWith(ctx, str, new JSValue[]{new JSString("hello"), new JSNumber(50)});
        assertEquals(JSBoolean.FALSE, result);

        // Empty search string
        result = StringPrototype.startsWith(ctx, str, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.startsWith(ctx, empty, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.TRUE, result);
    }

    @Test
    public void testSubstr() {
        // Normal case
        JSValue result = StringPrototype.substr(ctx, str, new JSValue[]{new JSNumber(6), new JSNumber(5)});
        assertEquals("world", result.asString().map(JSString::value).orElse(""));

        // No length
        result = StringPrototype.substr(ctx, str, new JSValue[]{new JSNumber(6)});
        assertEquals("world", result.asString().map(JSString::value).orElse(""));

        // Negative start
        result = StringPrototype.substr(ctx, str, new JSValue[]{new JSNumber(-5), new JSNumber(5)});
        assertEquals("world", result.asString().map(JSString::value).orElse(""));

        // Negative length
        result = StringPrototype.substr(ctx, str, new JSValue[]{new JSNumber(0), new JSNumber(-1)});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Out of bounds
        result = StringPrototype.substr(ctx, str, new JSValue[]{new JSNumber(50)});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.substr(ctx, empty, new JSValue[]{new JSNumber(0)});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        result = StringPrototype.substr(ctx, chinese, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertEquals("你好", result.asString().map(JSString::value).orElse(""));

        result = StringPrototype.substr(ctx, chinese, new JSValue[]{new JSNumber(1), new JSNumber(2)});
        assertEquals("好世", result.asString().map(JSString::value).orElse(""));

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        result = StringPrototype.substr(ctx, emoji, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertEquals("😀", result.asString().map(JSString::value).orElse(""));

        result = StringPrototype.substr(ctx, emoji, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals("🌟", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testSubstring() {
        // Normal case
        JSValue result = StringPrototype.substring(ctx, str, new JSValue[]{new JSNumber(6)});
        assertEquals("world", result.asString().map(JSString::value).orElse(""));

        result = StringPrototype.substring(ctx, str, new JSValue[]{new JSNumber(0), new JSNumber(5)});
        assertEquals("hello", result.asString().map(JSString::value).orElse(""));

        // Swapped indices
        result = StringPrototype.substring(ctx, str, new JSValue[]{new JSNumber(5), new JSNumber(0)});
        assertEquals("hello", result.asString().map(JSString::value).orElse(""));

        // Negative indices
        result = StringPrototype.substring(ctx, str, new JSValue[]{new JSNumber(-5)});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Out of bounds
        result = StringPrototype.substring(ctx, str, new JSValue[]{new JSNumber(50)});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.substring(ctx, empty, new JSValue[]{new JSNumber(0)});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        result = StringPrototype.substring(ctx, chinese, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertEquals("你好", result.asString().map(JSString::value).orElse(""));

        result = StringPrototype.substring(ctx, chinese, new JSValue[]{new JSNumber(1), new JSNumber(3)});
        assertEquals("好世", result.asString().map(JSString::value).orElse(""));

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        result = StringPrototype.substring(ctx, emoji, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertEquals("😀", result.asString().map(JSString::value).orElse(""));

        result = StringPrototype.substring(ctx, emoji, new JSValue[]{new JSNumber(2), new JSNumber(4)});
        assertEquals("🌟", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testToLowerCase() {
        JSString upper = new JSString("HELLO WORLD");
        JSValue result = StringPrototype.toLowerCase(ctx, upper, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Already lower
        result = StringPrototype.toLowerCase(ctx, str, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Mixed
        JSString mixed = new JSString("HeLLo WoRLd");
        result = StringPrototype.toLowerCase(ctx, mixed, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toLowerCase(ctx, empty, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testToUpperCase() {
        JSValue result = StringPrototype.toUpperCase(ctx, str, new JSValue[]{});
        assertEquals("HELLO WORLD", result.asString().map(JSString::value).orElse(""));

        // Already upper
        JSString upper = new JSString("HELLO WORLD");
        result = StringPrototype.toUpperCase(ctx, upper, new JSValue[]{});
        assertEquals("HELLO WORLD", result.asString().map(JSString::value).orElse(""));

        // Mixed
        JSString mixed = new JSString("HeLLo WoRLd");
        result = StringPrototype.toUpperCase(ctx, mixed, new JSValue[]{});
        assertEquals("HELLO WORLD", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toUpperCase(ctx, empty, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElse(""));
    }

    @Test
    public void testTrim() {
        JSString spaced = new JSString("  hello world  ");
        JSValue result = StringPrototype.trim(ctx, spaced, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // No spaces
        result = StringPrototype.trim(ctx, str, new JSValue[]{});
        assertEquals("hello world", result.asString().map(JSString::value).orElse(""));

        // Only spaces
        JSString spaces = new JSString("   ");
        result = StringPrototype.trim(ctx, spaces, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.trim(ctx, empty, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElse(""));
    }
}