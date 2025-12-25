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
        JSValue result = RegExpPrototype.exec(ctx, regexp, new JSValue[]{new JSString("hello world")});
        assertInstanceOf(JSArray.class, result);
        JSArray arr = (JSArray) result;
        assertEquals(2, arr.getLength()); // At least the matched string
        assertEquals("hello", ((JSString) arr.get(0)).getValue());

        // Check index property
        JSValue indexValue = arr.get("index");
        assertInstanceOf(JSNumber.class, indexValue);
        assertEquals(0.0, ((JSNumber) indexValue).value());

        // Check input property
        JSValue inputValue = arr.get("input");
        assertInstanceOf(JSString.class, inputValue);
        assertEquals("hello world", ((JSString) inputValue).getValue());

        // Normal case: no match
        result = RegExpPrototype.exec(ctx, regexp, new JSValue[]{new JSString("goodbye")});
        assertInstanceOf(JSNull.class, result);

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.exec(ctx, new JSObject(), new JSValue[]{new JSString("test")}));
        assertPendingException(ctx);
    }

    @Test
    public void testExecWithGlobalFlag() {
        JSRegExp regexp = new JSRegExp("o", "g");

        // First exec
        JSValue result = RegExpPrototype.exec(ctx, regexp, new JSValue[]{new JSString("foo bar")});
        assertInstanceOf(JSArray.class, result);
        JSArray arr = (JSArray) result;
        assertEquals("o", ((JSString) arr.get(0)).getValue());
        assertTrue(regexp.getLastIndex() > 0);

        // Second exec (should get next match)
        result = RegExpPrototype.exec(ctx, regexp, new JSValue[]{new JSString("foo bar")});
        assertInstanceOf(JSArray.class, result);
        arr = (JSArray) result;
        assertEquals("o", ((JSString) arr.get(0)).getValue());

        // Third exec (no more matches, should return null and reset lastIndex)
        result = RegExpPrototype.exec(ctx, regexp, new JSValue[]{new JSString("foo bar")});
        assertInstanceOf(JSNull.class, result);
        assertEquals(0, regexp.getLastIndex());
    }

    @Test
    public void testGetFlags() {
        JSRegExp regexp = new JSRegExp("test", "gimsuy");

        JSValue result = RegExpPrototype.getFlags(ctx, regexp, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        assertEquals("gimsuy", ((JSString) result).getValue());

        // Edge case: no flags
        JSRegExp regexp2 = new JSRegExp("test", "");
        result = RegExpPrototype.getFlags(ctx, regexp2, new JSValue[]{});
        assertEquals("", ((JSString) result).getValue());

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.getFlags(ctx, new JSArray(), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetGlobal() {
        JSRegExp regexp1 = new JSRegExp("test", "g");
        JSValue result = RegExpPrototype.getGlobal(ctx, regexp1, new JSValue[]{});
        assertEquals(JSBoolean.TRUE, result);

        JSRegExp regexp2 = new JSRegExp("test", "i");
        result = RegExpPrototype.getGlobal(ctx, regexp2, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getGlobal(ctx, new JSString("not regexp"), new JSValue[]{});
        assertInstanceOf(JSUndefined.class, result);
    }

    @Test
    public void testGetIgnoreCase() {
        JSRegExp regexp1 = new JSRegExp("test", "i");
        JSValue result = RegExpPrototype.getIgnoreCase(ctx, regexp1, new JSValue[]{});
        assertEquals(JSBoolean.TRUE, result);

        JSRegExp regexp2 = new JSRegExp("test", "g");
        result = RegExpPrototype.getIgnoreCase(ctx, regexp2, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getIgnoreCase(ctx, JSNull.INSTANCE, new JSValue[]{});
        assertInstanceOf(JSUndefined.class, result);
    }

    @Test
    public void testGetMultiline() {
        JSRegExp regexp1 = new JSRegExp("test", "m");
        JSValue result = RegExpPrototype.getMultiline(ctx, regexp1, new JSValue[]{});
        assertEquals(JSBoolean.TRUE, result);

        JSRegExp regexp2 = new JSRegExp("test", "");
        result = RegExpPrototype.getMultiline(ctx, regexp2, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getMultiline(ctx, JSBoolean.TRUE, new JSValue[]{});
        assertInstanceOf(JSUndefined.class, result);
    }

    @Test
    public void testGetSource() {
        JSRegExp regexp = new JSRegExp("hello", "");

        JSValue result = RegExpPrototype.getSource(ctx, regexp, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        assertEquals("hello", ((JSString) result).getValue());

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.getSource(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testLastIndexTracking() {
        JSRegExp regexp = new JSRegExp("test", "g");

        // Initial lastIndex should be 0
        assertEquals(0, regexp.getLastIndex());

        // After successful test, lastIndex should update
        RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("test test")});
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
        assertInstanceOf(JSString.class, source);
        assertEquals("test", ((JSString) source).getValue());

        JSValue flags = regexp.get("flags");
        assertInstanceOf(JSString.class, flags);
        assertEquals("gim", ((JSString) flags).getValue());

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
        JSValue result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("hello world")});
        assertEquals(JSBoolean.TRUE, result);

        // Normal case: no match
        result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("goodbye world")});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: empty string
        result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("")});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: no arguments (should test against "")
        result = RegExpPrototype.test(ctx, regexp, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.test(ctx, new JSString("not regexp"), new JSValue[]{new JSString("test")}));
        assertPendingException(ctx);
    }

    @Test
    public void testTestWithGlobalFlag() {
        JSRegExp regexp = new JSRegExp("o", "g");

        // First test should match and update lastIndex
        JSValue result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("foo bar")});
        assertEquals(JSBoolean.TRUE, result);
        assertTrue(regexp.getLastIndex() > 0);

        // Second test should continue from lastIndex
        result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("foo bar")});
        assertEquals(JSBoolean.TRUE, result);

        // Third test should fail and reset lastIndex
        result = RegExpPrototype.test(ctx, regexp, new JSValue[]{new JSString("foo bar")});
        assertEquals(JSBoolean.FALSE, result);
        assertEquals(0, regexp.getLastIndex());
    }

    @Test
    public void testToString() {
        JSRegExp regexp = new JSRegExp("test", "gi");

        JSValue result = RegExpPrototype.toStringMethod(ctx, regexp, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        assertEquals("/test/gi", ((JSString) result).getValue());

        // Edge case: empty pattern
        JSRegExp regexp2 = new JSRegExp("", "");
        result = RegExpPrototype.toStringMethod(ctx, regexp2, new JSValue[]{});
        assertEquals("//", ((JSString) result).getValue());

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.toStringMethod(ctx, new JSString("not regexp"), new JSValue[]{}));
        assertPendingException(ctx);
    }
}
