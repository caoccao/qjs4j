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
 * Unit tests for RegExp constructor and RegExp.prototype methods.
 */
public class RegExpConstructorTest extends BaseTest {

    @Test
    public void testExec() {
        JSRegExp regexp = new JSRegExp("hello", "");

        // Normal case: match
        JSValue result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("hello world")});
        JSArray arr = result.asArray().orElseThrow();
        assertEquals(2, arr.getLength()); // At least the matched string
        assertEquals("hello", arr.get(0).asString().map(JSString::value).orElseThrow());

        // Check index property
        JSValue indexValue = arr.get("index");
        assertEquals(0.0, indexValue.asNumber().map(JSNumber::value).orElseThrow());

        // Check input property
        JSValue inputValue = arr.get("input");
        assertEquals("hello world", inputValue.asString().map(JSString::value).orElseThrow());

        // Normal case: no match
        result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("goodbye")});
        assertTrue(result.isNull());

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.exec(context, new JSObject(), new JSValue[]{new JSString("test")}));
        assertPendingException(context);
    }

    @Test
    public void testExecWithGlobalFlag() {
        JSRegExp regexp = new JSRegExp("o", "g");

        // First exec
        JSValue result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("foo bar")});
        JSArray arr = result.asArray().orElseThrow();
        assertEquals("o", arr.get(0).asString().map(JSString::value).orElseThrow());
        assertTrue(regexp.getLastIndex() > 0);

        // Second exec (should get next match)
        result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("foo bar")});
        arr = result.asArray().orElseThrow();
        assertEquals("o", arr.get(0).asString().map(JSString::value).orElseThrow());

        // Third exec (no more matches, should return null and reset lastIndex)
        result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertTrue(result.isNull());
        assertEquals(0, regexp.getLastIndex());
    }

    @Test
    public void testGetFlags() {
        JSRegExp regexp = new JSRegExp("test", "gimsuy");

        JSValue result = RegExpPrototype.getFlags(context, regexp, new JSValue[]{});
        assertEquals("gimsuy", result.asString().map(JSString::value).orElseThrow());

        // Edge case: no flags
        JSRegExp regexp2 = new JSRegExp("test", "");
        result = RegExpPrototype.getFlags(context, regexp2, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.getFlags(context, new JSArray(), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testGetGlobal() {
        JSRegExp regexp1 = new JSRegExp("test", "g");
        JSValue result = RegExpPrototype.getGlobal(context, regexp1, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        JSRegExp regexp2 = new JSRegExp("test", "i");
        result = RegExpPrototype.getGlobal(context, regexp2, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getGlobal(context, new JSString("not regexp"), new JSValue[]{});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testGetIgnoreCase() {
        JSRegExp regexp1 = new JSRegExp("test", "i");
        JSValue result = RegExpPrototype.getIgnoreCase(context, regexp1, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        JSRegExp regexp2 = new JSRegExp("test", "g");
        result = RegExpPrototype.getIgnoreCase(context, regexp2, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getIgnoreCase(context, JSNull.INSTANCE, new JSValue[]{});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testGetMultiline() {
        JSRegExp regexp1 = new JSRegExp("test", "m");
        JSValue result = RegExpPrototype.getMultiline(context, regexp1, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        JSRegExp regexp2 = new JSRegExp("test", "");
        result = RegExpPrototype.getMultiline(context, regexp2, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getMultiline(context, JSBoolean.TRUE, new JSValue[]{});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testGetSource() {
        JSRegExp regexp = new JSRegExp("hello", "");

        JSValue result = RegExpPrototype.getSource(context, regexp, new JSValue[]{});
        assertEquals("hello", result.asString().map(JSString::value).orElseThrow());

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.getSource(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testLastIndexTracking() {
        JSRegExp regexp = new JSRegExp("test", "g");

        // Initial lastIndex should be 0
        assertEquals(0, regexp.getLastIndex());

        // After successful test, lastIndex should update
        RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("test test")});
        assertTrue(regexp.getLastIndex() > 0);

        // Can manually set lastIndex
        regexp.setLastIndex(10);
        assertEquals(10, regexp.getLastIndex());
    }

    @Test
    public void testRegExpConstruction() {
        // Normal case: simple pattern
        JSRegExp regexp = new JSRegExp("abc", "");
        assertEquals("abc", regexp.getPattern());
        assertEquals("", regexp.getFlags());
        assertFalse(regexp.isGlobal());

        // Normal case: with flags
        JSRegExp regexp2 = new JSRegExp("test", "gi");
        assertEquals("test", regexp2.getPattern());
        assertEquals("gi", regexp2.getFlags());
        assertTrue(regexp2.isGlobal());

        // Edge case: empty pattern
        JSRegExp regexp3 = new JSRegExp("", "");
        assertEquals("", regexp3.getPattern());

        // Edge case: null pattern/flags
        JSRegExp regexp4 = new JSRegExp(null, null);
        assertEquals("", regexp4.getPattern());
        assertEquals("", regexp4.getFlags());
    }

    @Test
    public void testRegExpProperties() {
        JSRegExp regexp = new JSRegExp("test", "gim");

        // Check properties are set
        JSValue source = regexp.get("source");
        assertEquals("test", source.asString().map(JSString::value).orElseThrow());

        JSValue flags = regexp.get("flags");
        assertEquals("gim", flags.asString().map(JSString::value).orElseThrow());

        JSValue global = regexp.get("global");
        assertEquals(JSBoolean.TRUE, global);

        JSValue ignoreCase = regexp.get("ignoreCase");
        assertEquals(JSBoolean.TRUE, ignoreCase);

        JSValue multiline = regexp.get("multiline");
        assertEquals(JSBoolean.TRUE, multiline);

        JSValue dotAll = regexp.get("dotAll");
        assertEquals(JSBoolean.FALSE, dotAll);

        JSValue unicode = regexp.get("unicode");
        assertEquals(JSBoolean.FALSE, unicode);

        JSValue sticky = regexp.get("sticky");
        assertEquals(JSBoolean.FALSE, sticky);
    }

    @Test
    public void testTest() {
        JSRegExp regexp = new JSRegExp("hello", "");

        // Normal case: match
        JSValue result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("hello world")});
        assertTrue(result.isBooleanTrue());

        // Normal case: no match
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("goodbye world")});
        assertTrue(result.isBooleanFalse());

        // Edge case: empty string
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("")});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments (should test against "")
        result = RegExpPrototype.test(context, regexp, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.test(context, new JSString("not regexp"), new JSValue[]{new JSString("test")}));
        assertPendingException(context);
    }

    @Test
    public void testTestWithGlobalFlag() {
        JSRegExp regexp = new JSRegExp("o", "g");

        // First test should match and update lastIndex
        JSValue result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertTrue(result.isBooleanTrue());
        assertTrue(regexp.getLastIndex() > 0);

        // Second test should continue from lastIndex
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertTrue(result.isBooleanTrue());

        // Third test should fail and reset lastIndex
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertTrue(result.isBooleanFalse());
        assertEquals(0, regexp.getLastIndex());
    }

    @Test
    public void testToString() {
        JSRegExp regexp = new JSRegExp("test", "gi");

        JSValue result = RegExpPrototype.toStringMethod(context, regexp, new JSValue[]{});
        assertEquals("/test/gi", result.asString().map(JSString::value).orElseThrow());

        // Edge case: empty pattern
        JSRegExp regexp2 = new JSRegExp("", "");
        result = RegExpPrototype.toStringMethod(context, regexp2, new JSValue[]{});
        assertEquals("//", result.asString().map(JSString::value).orElseThrow());

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.toStringMethod(context, new JSString("not regexp"), new JSValue[]{}));
        assertPendingException(context);
    }
}
