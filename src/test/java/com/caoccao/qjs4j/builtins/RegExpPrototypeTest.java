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
 * Unit tests for RegExpPrototype methods.
 */
public class RegExpPrototypeTest extends BaseTest {

    @Test
    public void testExec() {
        // Create a simple regex
        JSRegExp regexp = new JSRegExp("hello", "");

        // Normal case: match found
        JSValue result = RegExpPrototype.exec(ctx, regexp, new JSValue[]{new JSString("hello world")});
        JSArray matchArray = result.asArray().orElse(null);
        assertNotNull(matchArray);
        assertEquals(2, matchArray.getLength()); // full match only
        assertEquals("hello", matchArray.get(0).asString().map(JSString::value).orElse(""));
        assertEquals(0.0, matchArray.get("index").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals("hello world", matchArray.get("input").asString().map(JSString::value).orElse(""));

        // Edge case: called on non-RegExp
        result = RegExpPrototype.exec(ctx, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetFlags() {
        // Normal case: no flags
        JSRegExp regexp = new JSRegExp("test", "");
        JSValue result = RegExpPrototype.getFlags(ctx, regexp, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Normal case: multiple flags
        JSRegExp withFlags = new JSRegExp("hello", "gim");
        result = RegExpPrototype.getFlags(ctx, withFlags, new JSValue[]{});
        assertEquals("gim", result.asString().map(JSString::value).orElse(""));

        // Normal case: single flag
        JSRegExp singleFlag = new JSRegExp("world", "i");
        result = RegExpPrototype.getFlags(ctx, singleFlag, new JSValue[]{});
        assertEquals("i", result.asString().map(JSString::value).orElse(""));

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getFlags(ctx, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetGlobal() {
        // Normal case: global flag set
        JSRegExp global = new JSRegExp("test", "g");
        JSValue result = RegExpPrototype.getGlobal(ctx, global, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        // Normal case: global flag not set
        JSRegExp nonGlobal = new JSRegExp("test", "i");
        result = RegExpPrototype.getGlobal(ctx, nonGlobal, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp("test", "");
        result = RegExpPrototype.getGlobal(ctx, noFlags, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getGlobal(ctx, new JSString("not a regexp"), new JSValue[]{});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testGetIgnoreCase() {
        // Normal case: ignoreCase flag set
        JSRegExp ignoreCase = new JSRegExp("test", "i");
        JSValue result = RegExpPrototype.getIgnoreCase(ctx, ignoreCase, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        // Normal case: ignoreCase flag not set
        JSRegExp caseSensitive = new JSRegExp("test", "g");
        result = RegExpPrototype.getIgnoreCase(ctx, caseSensitive, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp("test", "");
        result = RegExpPrototype.getIgnoreCase(ctx, noFlags, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getIgnoreCase(ctx, new JSString("not a regexp"), new JSValue[]{});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testGetMultiline() {
        // Normal case: multiline flag set
        JSRegExp multiline = new JSRegExp("test", "m");
        JSValue result = RegExpPrototype.getMultiline(ctx, multiline, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        // Normal case: multiline flag not set
        JSRegExp singleLine = new JSRegExp("test", "g");
        result = RegExpPrototype.getMultiline(ctx, singleLine, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp("test", "");
        result = RegExpPrototype.getMultiline(ctx, noFlags, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getMultiline(ctx, new JSString("not a regexp"), new JSValue[]{});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testGetSource() {
        // Normal case: simple pattern
        JSRegExp regexp = new JSRegExp("test", "");
        JSValue result = RegExpPrototype.getSource(ctx, regexp, new JSValue[]{});
        assertEquals("test", result.asString().map(JSString::value).orElse(""));

        // Normal case: pattern with special characters
        JSRegExp special = new JSRegExp("test", "i");
        result = RegExpPrototype.getSource(ctx, special, new JSValue[]{});
        assertEquals("test", result.asString().map(JSString::value).orElse(""));

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getSource(ctx, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testTest() {
        // Create a simple regex
        JSRegExp regexp = new JSRegExp("test", "");

        // Normal case: match found
        JSValue result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("this is a test")});
        assertTrue(result.isBooleanTrue());

        // Normal case: no match
        result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("no match here")});
        assertEquals(JSBoolean.FALSE, result);

        // Normal case: empty string
        result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.FALSE, result);

        // Normal case: case insensitive
        JSRegExp caseInsensitive = new JSRegExp("TEST", "i");
        result = RegExpPrototype.test(ctx, caseInsensitive, new JSValue[]{new JSString("test")});
        assertTrue(result.isBooleanTrue());

        // Normal case: global regex with lastIndex
        JSRegExp global = new JSRegExp("test", "g");
        result = RegExpPrototype.test(ctx, global, new JSValue[]{new JSString("test test test")});
        assertTrue(result.isBooleanTrue());
        assertEquals(4, global.getLastIndex()); // After first match

        result = RegExpPrototype.test(ctx, global, new JSValue[]{new JSString("test test test")});
        assertTrue(result.isBooleanTrue());
        assertEquals(9, global.getLastIndex()); // After second match

        // Normal case: no arguments (empty string)
        result = RegExpPrototype.test(ctx, regexp, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-RegExp
        result = RegExpPrototype.test(ctx, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testToStringMethod() {
        // Normal case: simple regex
        JSRegExp regexp = new JSRegExp("test", "");
        JSValue result = RegExpPrototype.toStringMethod(ctx, regexp, new JSValue[]{});
        assertEquals("/test/", result.asString().map(JSString::value).orElse(""));

        // Normal case: regex with flags
        JSRegExp withFlags = new JSRegExp("hello", "gi");
        result = RegExpPrototype.toStringMethod(ctx, withFlags, new JSValue[]{});
        assertEquals("/hello/gi", result.asString().map(JSString::value).orElse(""));

        // Normal case: pattern with special characters
        JSRegExp special = new JSRegExp("test", "m");
        result = RegExpPrototype.toStringMethod(ctx, special, new JSValue[]{});
        assertEquals("/test/m", result.asString().map(JSString::value).orElse(""));

        // Edge case: called on non-RegExp
        result = RegExpPrototype.toStringMethod(ctx, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }
}