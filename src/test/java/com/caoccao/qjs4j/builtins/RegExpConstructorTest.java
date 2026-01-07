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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RegExp constructor and RegExp.prototype methods.
 */
public class RegExpConstructorTest extends BaseJavetTest {

    @Test
    void testCaptureGroups() {
        // Single capture group
        assertStringWithJavet("/(\\d+)/.exec('abc123')[1]");

        // Multiple capture groups
        assertStringWithJavet("/(\\d+)-(\\d+)/.exec('2024-01-07')[1]");
        assertStringWithJavet("/(\\d+)-(\\d+)/.exec('2024-01-07')[2]");

        // Nested capture groups
        assertStringWithJavet("/((\\d+)-(\\d+))/.exec('2024-01-07')[1]");
        assertStringWithJavet("/((\\d+)-(\\d+))/.exec('2024-01-07')[2]");
        assertStringWithJavet("/((\\d+)-(\\d+))/.exec('2024-01-07')[3]");
    }

    @Test
    void testComplexPatterns() {
        // Email-like pattern
        assertBooleanWithJavet("/^[a-z]+@[a-z]+\\.[a-z]+$/.test('test@example.com')");

        // URL-like pattern
        assertBooleanWithJavet("/^https?:\\/\\//.test('https://example.com')");

        // Phone number pattern
        assertBooleanWithJavet("/^\\d{3}-\\d{3}-\\d{4}$/.test('123-456-7890')");

        // Date pattern
        assertBooleanWithJavet("/^\\d{4}-\\d{2}-\\d{2}$/.test('2024-01-07')");
    }

    @Test
    void testConstructorEdgeCases() {
        // Empty pattern - source should be empty string, toString shows (?:)
        assertStringWithJavet("new RegExp().toString()");
        assertStringWithJavet("new RegExp('').toString()");
        assertStringWithJavet("RegExp().toString()");

        // Empty flags
        assertStringWithJavet("new RegExp('test').flags");
        assertStringWithJavet("new RegExp('test', '').flags");
    }

    @Test
    void testConstructorWithNew() {
        // Basic construction with new
        assertStringWithJavet("new RegExp('test').source");
        assertStringWithJavet("new RegExp('test', 'gi').flags");
        assertBooleanWithJavet("new RegExp('test', 'g').global");
        assertBooleanWithJavet("new RegExp('test', 'i').ignoreCase");
        assertBooleanWithJavet("new RegExp('test', 'm').multiline");
        assertBooleanWithJavet("new RegExp('test', 's').dotAll");
        assertBooleanWithJavet("new RegExp('test', 'u').unicode");
        assertBooleanWithJavet("new RegExp('test', 'y').sticky");
    }

    @Test
    void testConstructorWithRegExpArgument() {
        // Copying existing RegExp
        assertStringWithJavet("new RegExp(/test/gi).source");
        assertStringWithJavet("new RegExp(/test/gi).flags");
        assertStringWithJavet("RegExp(/test/gi).source");

        // Overriding flags when copying
        assertStringWithJavet("new RegExp(/test/gi, 'm').flags");
        assertBooleanWithJavet("new RegExp(/test/gi, 'm').multiline");
        assertBooleanWithJavet("!new RegExp(/test/gi, 'm').global");
    }

    @Test
    void testConstructorWithoutNew() {
        // Calling RegExp as a function (without new)
        assertStringWithJavet("RegExp('test').source");
        assertStringWithJavet("RegExp('test', 'gi').flags");
        assertBooleanWithJavet("RegExp('test', 'g').global");

        // Both should create equivalent RegExp objects
        assertBooleanWithJavet("RegExp('test').source === new RegExp('test').source");
        assertStringWithJavet("RegExp('test', 'gi').toString()", "new RegExp('test', 'gi').toString()");
    }

    @Test
    void testDotAllFlagBehavior() {
        // . without dotAll doesn't match newline
        assertBooleanWithJavet("!/a.b/.test('a\\nb')");

        // . with dotAll matches newline
        assertBooleanWithJavet("/a.b/s.test('a\\nb')");
    }

    @Test
    void testEdgeCasePatterns() {
        // Empty alternation
        assertBooleanWithJavet("/a|/.test('')");
        assertBooleanWithJavet("/|b/.test('')");

        // Optional groups
        assertBooleanWithJavet("/(test)?/.test('')");
        assertBooleanWithJavet("/(test)?/.test('test')");

        // Anchors
        assertBooleanWithJavet("/^test$/.test('test')");
        assertBooleanWithJavet("!/^test$/.test('test2')");

        // Zero-width assertions
        assertBooleanWithJavet("/test(?=ing)/.test('testing')");
        assertStringWithJavet("/test(?=ing)/.exec('testing')[0]");
    }

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
    void testExecMethod() {
        // Basic exec
        assertStringWithJavet("/hello/.exec('hello world')[0]");
        assertIntegerWithJavet("/hello/.exec('hello world').index");
        assertStringWithJavet("/hello/.exec('hello world').input");

        // No match returns null
        assertBooleanWithJavet("/hello/.exec('goodbye') === null");

        // Capture groups
        assertStringWithJavet("/(\\d+)/.exec('abc123def')[0]");
        assertStringWithJavet("/(\\d+)/.exec('abc123def')[1]");
        assertIntegerWithJavet("/(\\d+)/.exec('abc123def').length");
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
    void testFlagsProperty() {
        // All flag combinations
        assertStringWithJavet("/test/g.flags");
        assertStringWithJavet("/test/gi.flags");
        assertStringWithJavet("/test/gim.flags");
        assertStringWithJavet("/test/gims.flags");
        assertStringWithJavet("/test/gimsu.flags");
        assertStringWithJavet("/test/gimsuy.flags");
        assertStringWithJavet("/test/.flags");
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
    void testGlobalFlagBehavior() {
        // Global flag affects exec
        assertStringWithJavet("var r = /o/g; r.exec('foo')[0]");
        assertIntegerWithJavet("var r = /o/g; r.exec('foo'); r.lastIndex");

        // Global flag affects test
        assertBooleanWithJavet("var r = /o/g; r.test('foo')");
        assertIntegerWithJavet("var r = /o/g; r.test('foo'); r.lastIndex");

        // lastIndex resets on no match
        assertIntegerWithJavet("var r = /x/g; r.lastIndex = 5; r.test('abc'); r.lastIndex");
    }

    @Test
    void testInstanceOf() {
        // RegExp instances
        assertBooleanWithJavet("new RegExp('test') instanceof RegExp");
        assertBooleanWithJavet("RegExp('test') instanceof RegExp");
        assertBooleanWithJavet("/test/ instanceof RegExp");

        // Not a RegExp
        assertBooleanWithJavet("!('test' instanceof RegExp)");
        assertBooleanWithJavet("!(123 instanceof RegExp)");
    }

    @Test
    void testLastIndexProperty() {
        // Initial lastIndex
        assertIntegerWithJavet("/test/.lastIndex");
        assertIntegerWithJavet("new RegExp('test').lastIndex");

        // Setting lastIndex
        assertIntegerWithJavet("var r = /test/; r.lastIndex = 5; r.lastIndex");

        // lastIndex with global flag
        assertIntegerWithJavet("var r = /o/g; r.exec('foo'); r.lastIndex");
        assertIntegerWithJavet("var r = /o/g; r.exec('foo'); r.exec('foo'); r.lastIndex");
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
    void testMultilineFlagBehavior() {
        // ^ and $ without multiline
        assertBooleanWithJavet("/^test/.test('line1\\ntest')");

        // ^ and $ with multiline
        assertBooleanWithJavet("/^test/m.test('line1\\ntest')");
        assertBooleanWithJavet("/test$/m.test('test\\nline2')");
    }

    @Test
    void testQuantifiers() {
        // Basic quantifiers
        assertBooleanWithJavet("/a+/.test('aaa')");
        assertBooleanWithJavet("/a*/.test('bbb')");
        assertBooleanWithJavet("/a?/.test('b')");

        // Range quantifiers
        assertBooleanWithJavet("/a{3}/.test('aaa')");
        assertBooleanWithJavet("!/a{3}/.test('aa')");
        assertBooleanWithJavet("/a{2,4}/.test('aaa')");
        assertBooleanWithJavet("/a{2,}/.test('aaaaa')");

        // Greedy vs lazy
        assertStringWithJavet("'<a><b>'.match(/<.*>/)[0]");
        assertStringWithJavet("'<a><b>'.match(/<.*?>/)[0]");
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
    void testRegExpLiteralVsConstructor() {
        // Literals and constructor should be equivalent
        assertStringWithJavet("/test/.source", "new RegExp('test').source");
        assertStringWithJavet("/test/gi.flags", "new RegExp('test', 'gi').flags");
        assertBooleanWithJavet("/test/.test('test')", "new RegExp('test').test('test')");
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
    void testSourceProperty() {
        // Basic source
        assertStringWithJavet("/test/.source");
        assertStringWithJavet("/hello world/.source");

        // Empty source
        assertStringWithJavet("/(?:)/.source");

        // Special characters in source
        assertStringWithJavet("/\\d+/.source");
        assertStringWithJavet("/[a-z]+/.source");
    }

    @Test
    void testSpecialPatterns() {
        // Word boundary
        assertBooleanWithJavet("/\\btest\\b/.test('test')");
        assertBooleanWithJavet("!/\\btest\\b/.test('testing')");

        // Character classes
        assertBooleanWithJavet("/[a-z]+/.test('abc')");
        assertBooleanWithJavet("/[0-9]+/.test('123')");
        assertBooleanWithJavet("/[^0-9]+/.test('abc')");
    }

    @Test
    void testStickyFlagBehavior() {
        // Sticky flag requires match at lastIndex
        assertBooleanWithJavet("var r = /test/y; r.test('test')");
        assertBooleanWithJavet("var r = /test/y; r.lastIndex = 5; !r.test('     test')");
        assertBooleanWithJavet("var r = /test/y; r.lastIndex = 5; r.test('     test')");
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
    void testTestMethod() {
        // Basic test
        assertBooleanWithJavet("/hello/.test('hello world')");
        assertBooleanWithJavet("!/hello/.test('goodbye')");

        // Case sensitivity
        assertBooleanWithJavet("/HELLO/.test('hello') === false");
        assertBooleanWithJavet("/HELLO/i.test('hello')");

        // Empty string
        assertBooleanWithJavet("/^$/.test('')");
        assertBooleanWithJavet("!/test/.test('')");

        // Special characters
        assertBooleanWithJavet("/\\./.test('a.b')");
        assertBooleanWithJavet("/\\d/.test('123')");
        assertBooleanWithJavet("/\\s/.test(' ')");
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

        // Edge case: empty pattern (should be /(?:)/ per ES spec)
        JSRegExp regexp2 = new JSRegExp("", "");
        result = RegExpPrototype.toStringMethod(context, regexp2, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("/(?:)/"));

        // Edge case: called on non-RegExp
        assertTypeError(RegExpPrototype.toStringMethod(context, new JSString("not regexp"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    void testToStringMethod() {
        // Basic toString
        assertStringWithJavet("/test/.toString()");
        assertStringWithJavet("/test/gi.toString()");

        // Empty pattern
        assertStringWithJavet("/(?:)/.toString()");
        assertStringWithJavet("new RegExp().toString()");

        // All flags
        assertStringWithJavet("/test/gimsuy.toString()");
    }

    @Test
    void testTypeof() {
        // RegExp should be a function
        assertStringWithJavet("typeof RegExp");

        // RegExp.length should be 0
        assertIntegerWithJavet("RegExp.length");

        // RegExp.name should be "RegExp"
        assertStringWithJavet("RegExp.name");

        assertStringWithJavet(
                "new RegExp().toString()",
                "RegExp().toString()");
    }

    @Test
    void testUnicodeFlagBehavior() {
        // Unicode flag basic test
        assertBooleanWithJavet("/test/u.test('test')");
        assertStringWithJavet("/test/u.flags");
    }
}
