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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RegExp constructor and RegExp.prototype methods.
 */
public class RegExpConstructorTest extends BaseTest {

    @Test
    public void testExec() {
        JSRegExp regexp = new JSRegExp("hello", "");

        // Normal case: match
        JSValue result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("hello world")});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, arr -> {
            assertThat(arr.getLength()).isEqualTo(1); // Just the matched string, no captures
            assertThat(arr.get(0)).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("hello"));

            // Check index property
            JSValue indexValue = arr.get("index");
            assertThat(indexValue).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(0.0));

            // Check input property
            JSValue inputValue = arr.get("input");
            assertThat(inputValue).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("hello world"));
        });

        // Normal case: no match
        result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("goodbye")});
        assertThat(result.isNull()).isTrue();

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.exec(context, new JSObject(), new JSValue[]{new JSString("test")}));
        assertPendingException(context);
    }

    @Test
    public void testExecWithGlobalFlag() {
        JSRegExp regexp = new JSRegExp("o", "g");

        // First exec
        JSValue result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, arr -> {
            assertThat(arr.get(0)).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("o"));
        });
        assertThat(regexp.getLastIndex() > 0).isTrue();

        // Second exec (should get next match)
        result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertThat(result).isInstanceOfSatisfying(JSArray.class, arr -> {
            assertThat(arr.get(0)).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("o"));
        });

        // Third exec (no more matches, should return null and reset lastIndex)
        result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertThat(result.isNull()).isTrue();
        assertThat(regexp.getLastIndex()).isEqualTo(0);
    }

    @Test
    public void testGetFlags() {
        JSRegExp regexp = new JSRegExp("test", "gimsuy");

        JSValue result = RegExpPrototype.getFlags(context, regexp, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("gimsuy"));

        // Edge case: no flags
        JSRegExp regexp2 = new JSRegExp("test", "");
        result = RegExpPrototype.getFlags(context, regexp2, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo(""));

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.getFlags(context, new JSArray(), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testGetGlobal() {
        JSRegExp regexp1 = new JSRegExp("test", "g");
        JSValue result = RegExpPrototype.getGlobal(context, regexp1, new JSValue[]{});
        assertThat(result.isBooleanTrue()).isTrue();

        JSRegExp regexp2 = new JSRegExp("test", "i");
        result = RegExpPrototype.getGlobal(context, regexp2, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getGlobal(context, new JSString("not regexp"), new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    public void testGetIgnoreCase() {
        JSRegExp regexp1 = new JSRegExp("test", "i");
        JSValue result = RegExpPrototype.getIgnoreCase(context, regexp1, new JSValue[]{});
        assertThat(result.isBooleanTrue()).isTrue();

        JSRegExp regexp2 = new JSRegExp("test", "g");
        result = RegExpPrototype.getIgnoreCase(context, regexp2, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getIgnoreCase(context, JSNull.INSTANCE, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    public void testGetMultiline() {
        JSRegExp regexp1 = new JSRegExp("test", "m");
        JSValue result = RegExpPrototype.getMultiline(context, regexp1, new JSValue[]{});
        assertThat(result.isBooleanTrue()).isTrue();

        JSRegExp regexp2 = new JSRegExp("test", "");
        result = RegExpPrototype.getMultiline(context, regexp2, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: called on non-RegExp (should return undefined)
        result = RegExpPrototype.getMultiline(context, JSBoolean.TRUE, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    public void testGetSource() {
        JSRegExp regexp = new JSRegExp("hello", "");

        JSValue result = RegExpPrototype.getSource(context, regexp, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("hello"));

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.getSource(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testLastIndexTracking() {
        JSRegExp regexp = new JSRegExp("test", "g");

        // Initial lastIndex should be 0
        assertThat(regexp.getLastIndex()).isEqualTo(0);

        // After successful test, lastIndex should update
        RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("test test")});
        assertThat(regexp.getLastIndex() > 0).isTrue();

        // Can manually set lastIndex
        regexp.setLastIndex(10);
        assertThat(regexp.getLastIndex()).isEqualTo(10);
    }

    @Test
    public void testRegExpConstruction() {
        // Normal case: simple pattern
        JSRegExp regexp = new JSRegExp("abc", "");
        assertThat(regexp.getPattern()).isEqualTo("abc");
        assertThat(regexp.getFlags()).isEqualTo("");
        assertThat(regexp.isGlobal()).isFalse();

        // Normal case: with flags
        JSRegExp regexp2 = new JSRegExp("test", "gi");
        assertThat(regexp2.getPattern()).isEqualTo("test");
        assertThat(regexp2.getFlags()).isEqualTo("gi");
        assertThat(regexp2.isGlobal()).isTrue();

        // Edge case: empty pattern
        JSRegExp regexp3 = new JSRegExp("", "");
        assertThat(regexp3.getPattern()).isEqualTo("");

        // Edge case: null pattern/flags
        JSRegExp regexp4 = new JSRegExp(null, null);
        assertThat(regexp4.getPattern()).isEqualTo("");
        assertThat(regexp4.getFlags()).isEqualTo("");
    }

    @Test
    public void testRegExpProperties() {
        JSRegExp regexp = new JSRegExp("test", "gim");

        // Check properties are set
        JSValue source = regexp.get("source");
        assertThat(source).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("test"));

        JSValue flags = regexp.get("flags");
        assertThat(flags).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("gim"));

        JSValue global = regexp.get("global");
        assertThat(global).isEqualTo(JSBoolean.TRUE);

        JSValue ignoreCase = regexp.get("ignoreCase");
        assertThat(ignoreCase).isEqualTo(JSBoolean.TRUE);

        JSValue multiline = regexp.get("multiline");
        assertThat(multiline).isEqualTo(JSBoolean.TRUE);

        JSValue dotAll = regexp.get("dotAll");
        assertThat(dotAll).isEqualTo(JSBoolean.FALSE);

        JSValue unicode = regexp.get("unicode");
        assertThat(unicode).isEqualTo(JSBoolean.FALSE);

        JSValue sticky = regexp.get("sticky");
        assertThat(sticky).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testTest() {
        JSRegExp regexp = new JSRegExp("hello", "");

        // Normal case: match
        JSValue result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("hello world")});
        assertThat(result.isBooleanTrue()).isTrue();

        // Normal case: no match
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("goodbye world")});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: empty string
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("")});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: no arguments (should test against "")
        result = RegExpPrototype.test(context, regexp, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.test(context, new JSString("not regexp"), new JSValue[]{new JSString("test")}));
        assertPendingException(context);
    }

    @Test
    public void testTestWithGlobalFlag() {
        JSRegExp regexp = new JSRegExp("o", "g");

        // First test should match and update lastIndex
        JSValue result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertThat(result.isBooleanTrue()).isTrue();
        assertThat(regexp.getLastIndex() > 0).isTrue();

        // Second test should continue from lastIndex
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertThat(result.isBooleanTrue()).isTrue();

        // Third test should fail and reset lastIndex
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("foo bar")});
        assertThat(result.isBooleanFalse()).isTrue();
        assertThat(regexp.getLastIndex()).isEqualTo(0);
    }

    @Test
    public void testToString() {
        JSRegExp regexp = new JSRegExp("test", "gi");

        JSValue result = RegExpPrototype.toStringMethod(context, regexp, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("/test/gi"));

        // Edge case: empty pattern
        JSRegExp regexp2 = new JSRegExp("", "");
        result = RegExpPrototype.toStringMethod(context, regexp2, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("//"));

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.toStringMethod(context, new JSString("not regexp"), new JSValue[]{}));
        assertPendingException(context);
    }
}
