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

package com.caoccao.qjs4j.builtins.regexp;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.regexp.JSRegExp;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RegExpPrototype methods and Javet-based RegExp tests.
 */
public class RegExpPrototypeTest extends BaseJavetTest {

    @Test
    public void testEmptyMatchStopsWhenLastIndexCoercionThrows() {
        assertStringWithJavet("""
                (() => {
                    const events = [];
                    let coercionThrows = false;
                    const receiver = {
                        flags: 'gu',
                        get lastIndex() {
                            return coercionThrows ? { valueOf() { events.push('coerce'); throw 42; } } : 0;
                        },
                        set lastIndex(value) { events.push('set:' + value); },
                        exec() { coercionThrows = true; return {0: ''}; }
                    };
                    receiver.constructor = {[Symbol.species]: function () { return receiver; }};
                    try {
                        const result = RegExp.prototype[Symbol.match].call(receiver, 'a', '');
                        if ('match' === 'matchAll') result.next();
                    } catch (error) { events.push('caught:' + error); }
                    return events.join(',');
                })()""", """
                (() => {
                    const events = [];
                    let coercionThrows = false;
                    const receiver = {
                        flags: 'gu',
                        get lastIndex() {
                            return coercionThrows ? { valueOf() { events.push('coerce'); throw 42; } } : 0;
                        },
                        set lastIndex(value) { events.push('set:' + value); },
                        exec() { coercionThrows = true; return {0: ''}; }
                    };
                    receiver.constructor = {[Symbol.species]: function () { return receiver; }};
                    try {
                        const result = RegExp.prototype[Symbol.replace].call(receiver, 'a', '');
                        if ('replace' === 'matchAll') result.next();
                    } catch (error) { events.push('caught:' + error); }
                    return events.join(',');
                })()""", """
                (() => {
                    const events = [];
                    let coercionThrows = false;
                    const receiver = {
                        flags: 'gu',
                        get lastIndex() {
                            return coercionThrows ? { valueOf() { events.push('coerce'); throw 42; } } : 0;
                        },
                        set lastIndex(value) { events.push('set:' + value); },
                        exec() { coercionThrows = true; return {0: ''}; }
                    };
                    receiver.constructor = {[Symbol.species]: function () { return receiver; }};
                    try {
                        const result = RegExp.prototype[Symbol.matchAll].call(receiver, 'a', '');
                        if ('matchAll' === 'matchAll') result.next();
                    } catch (error) { events.push('caught:' + error); }
                    return events.join(',');
                })()""");
    }

    @Test
    public void testEmptyUnicodeMatchAdvancesLargeLastIndex() {
        assertStringWithJavet("""
                (() => {
                    let calls = 0;
                    const receiver = {
                        flags: 'gu',
                        lastIndex: 0,
                        exec() {
                            if (calls++) return null;
                            this.lastIndex = 2 ** 32 + 1;
                            return {0: '', length: 1, index: 0};
                        }
                    };
                    receiver.constructor = {[Symbol.species]: function () { return receiver; }};
                    const result = RegExp.prototype[Symbol.match].call(receiver, 'a', '');
                    if ('match' === 'matchAll') result.next();
                    return String(receiver.lastIndex);
                })()""", """
                (() => {
                    let calls = 0;
                    const receiver = {
                        flags: 'gu',
                        lastIndex: 0,
                        exec() {
                            if (calls++) return null;
                            this.lastIndex = 2 ** 32 + 1;
                            return {0: '', length: 1, index: 0};
                        }
                    };
                    receiver.constructor = {[Symbol.species]: function () { return receiver; }};
                    const result = RegExp.prototype[Symbol.replace].call(receiver, 'a', '');
                    if ('replace' === 'matchAll') result.next();
                    return String(receiver.lastIndex);
                })()""", """
                (() => {
                    let calls = 0;
                    const receiver = {
                        flags: 'gu',
                        lastIndex: 0,
                        exec() {
                            if (calls++) return null;
                            this.lastIndex = 2 ** 32 + 1;
                            return {0: '', length: 1, index: 0};
                        }
                    };
                    receiver.constructor = {[Symbol.species]: function () { return receiver; }};
                    const result = RegExp.prototype[Symbol.matchAll].call(receiver, 'a', '');
                    if ('matchAll' === 'matchAll') result.next();
                    return String(receiver.lastIndex);
                })()""");
    }

    @Test
    public void testExec() {
        // Create a simple regex
        JSRegExp regexp = new JSRegExp(context, "hello", "");

        // Normal case: match found
        JSValue result = callBuiltin(RegExpPrototype::exec, regexp, "hello world");
        JSArray matchArray = result.asArray().orElseThrow();
        assertThat(matchArray.getLength()).isEqualTo(1); // just the full match, no captures
        assertStringValue(matchArray.get(0), "hello");
        assertNumberValue(matchArray.get("index"), 0.0);
        assertStringValue(matchArray.get("input"), "hello world");

        // Edge case: called on non-RegExp
        result = callBuiltin(RegExpPrototype::exec, new JSString("not a regexp"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testFlagsAndPropertiesForIndicesAndUnicodeSets() {
        assertBooleanWithJavet("/a/d.hasIndices", "!/a/.hasIndices", "/a/v.unicodeSets", "!/a/u.unicodeSets");
        assertStringWithJavet("new RegExp('a', 'ig').flags", "Object.prototype.toString.call(/a/)");
    }

    @Test
    public void testGetFlags() {
        // Normal case: no flags
        JSRegExp regexp = new JSRegExp(context, "test", "");
        JSValue result = callBuiltin(RegExpPrototype::getFlags, regexp);
        assertStringValue(result, "");

        // Normal case: multiple flags
        JSRegExp withFlags = new JSRegExp(context, "hello", "gim");
        result = callBuiltin(RegExpPrototype::getFlags, withFlags);
        assertStringValue(result, "gim");

        // Normal case: single flag
        JSRegExp singleFlag = new JSRegExp(context, "world", "i");
        result = callBuiltin(RegExpPrototype::getFlags, singleFlag);
        assertStringValue(result, "i");

        // Edge case: called on non-RegExp
        result = callBuiltin(RegExpPrototype::getFlags, new JSString("not a regexp"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetGlobal() {
        // Normal case: global flag set
        JSRegExp global = new JSRegExp(context, "test", "g");
        JSValue result = callBuiltin(RegExpPrototype::getGlobal, global);
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: global flag not set
        JSRegExp nonGlobal = new JSRegExp(context, "test", "i");
        result = callBuiltin(RegExpPrototype::getGlobal, nonGlobal);
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp(context, "test", "");
        result = callBuiltin(RegExpPrototype::getGlobal, noFlags);
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-RegExp
        result = callBuiltin(RegExpPrototype::getGlobal, new JSString("not a regexp"));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testGetIgnoreCase() {
        // Normal case: ignoreCase flag set
        JSRegExp ignoreCase = new JSRegExp(context, "test", "i");
        JSValue result = callBuiltin(RegExpPrototype::getIgnoreCase, ignoreCase);
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: ignoreCase flag not set
        JSRegExp caseSensitive = new JSRegExp(context, "test", "g");
        result = callBuiltin(RegExpPrototype::getIgnoreCase, caseSensitive);
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp(context, "test", "");
        result = callBuiltin(RegExpPrototype::getIgnoreCase, noFlags);
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-RegExp
        result = callBuiltin(RegExpPrototype::getIgnoreCase, new JSString("not a regexp"));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testGetMultiline() {
        // Normal case: multiline flag set
        JSRegExp multiline = new JSRegExp(context, "test", "m");
        JSValue result = callBuiltin(RegExpPrototype::getMultiline, multiline);
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: multiline flag not set
        JSRegExp singleLine = new JSRegExp(context, "test", "g");
        result = callBuiltin(RegExpPrototype::getMultiline, singleLine);
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp(context, "test", "");
        result = callBuiltin(RegExpPrototype::getMultiline, noFlags);
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-RegExp
        result = callBuiltin(RegExpPrototype::getMultiline, new JSString("not a regexp"));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testGetSource() {
        // Normal case: simple pattern
        JSRegExp regexp = new JSRegExp(context, "test", "");
        JSValue result = callBuiltin(RegExpPrototype::getSource, regexp);
        assertStringValue(result, "test");

        // Normal case: pattern with special characters
        JSRegExp special = new JSRegExp(context, "test", "i");
        result = callBuiltin(RegExpPrototype::getSource, special);
        assertStringValue(result, "test");

        // Edge case: called on non-RegExp
        result = callBuiltin(RegExpPrototype::getSource, new JSString("not a regexp"));
        assertPendingTypeError(context, result);

        assertStringWithJavet("new RegExp('').source");
    }

    @Test
    public void testIndicesResultWithNamedGroups() {
        assertBooleanWithJavet("Array.isArray(/(?<x>a)(b)?/d.exec('ab').indices)",
                "(() => { const m = /(?<x>a)(b)?/d.exec('ab'); return m.indices[0][0] === 0 && m.indices[0][1] === 2; })()",
                "(() => { const m = /(?<x>a)(b)?/d.exec('ab'); return m.indices[1][0] === 0 && m.indices[1][1] === 1; })()",
                "(() => { const m = /(?<x>a)(b)?/d.exec('ab'); return m.indices[2][0] === 1 && m.indices[2][1] === 2; })()",
                "(() => { const m = /(?<x>a)(b)?/d.exec('ab'); return m.indices.groups.x[0] === 0 && m.indices.groups.x[1] === 1; })()",
                "/a/.exec('a').indices === undefined");
    }

    @Test
    public void testInvalidRegExpFlags() {
        assertThatThrownBy(() -> resetContext().eval("new RegExp('a', 'gg')")).isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
        assertThatThrownBy(() -> resetContext().eval("new RegExp('a', 'uv')")).isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }

    @Test
    public void testJavetAlternation() {
        assertBooleanWithJavet("/cat|dog/.test('I have a cat')", "/cat|dog/.test('I have a dog')",
                "/cat|dog/.test('I have a bird')");
    }

    @Test
    public void testJavetBackReferences() {
        assertBooleanWithJavet(
                // Simple back reference
                "/(\\w+) \\1/.test('hello hello')", "/(\\w+) \\1/.test('hello world')",
                "/(\\d+)-\\1/.exec('123-123')[0] === '123-123'", "/(\\d+)-\\1/.test('123-456')",
                // Multiple back references
                "/(\\w)(\\w)\\2\\1/.test('abba')", "/(\\w)(\\w)\\2\\1/.test('abcd')",
                // Nested groups
                "/((\\w)\\w)\\1/.test('xyxy')", "/((\\w)\\w)\\1/.test('xyzz')",
                // Back reference with case insensitive
                "/(\\w+) \\1/i.test('Hello HELLO')", "/(abc)\\1/i.test('ABCABC')");
    }

    @Test
    public void testJavetBasicPatterns() {
        assertBooleanWithJavet("/hello/.test('hello world')", "/hello/.test('goodbye')", "/^hello/.test('hello world')",
                "/world$/.test('hello world')", "/^test$/.test('test')", "/^test$/.test('testing')");
    }

    @Test
    public void testJavetBigIntPattern() {
        assertBooleanWithJavet("/^\\d+n$/.test('123n')", "/^\\d+n$/.test('0n')", "/^\\d+n$/.test('123')",
                "/^\\d+n$/.test('n')");
    }

    @Test
    public void testJavetBoundaries() {
        assertBooleanWithJavet("/\\btest\\b/.test('test')", "/\\btest\\b/.test('testing')");
    }

    @Test
    public void testJavetCaptureGroups() {
        assertStringWithJavet("'hello world'.match(/(\\w+) (\\w+)/)[0]", "'hello world'.match(/(\\w+) (\\w+)/)[1]",
                "'hello world'.match(/(\\w+) (\\w+)/)[2]");
    }

    @Test
    public void testJavetCharacterClasses() {
        assertBooleanWithJavet("/\\d/.test('abc123')", "/\\d/.test('abc')", "/^\\d+$/.test('12345')",
                "/\\w/.test('test_123')", "/\\W/.test('test_123')", "/\\W/.test('!!')", "/^\\w+$/.test('test_123')",
                "/\\s/.test('hello world')", "/\\s/.test('helloworld')");
    }

    @Test
    public void testJavetComplexPatterns() {
        assertStringWithJavet(
                // Combination of features
                "/(\\d{3})-(\\d{3})-(\\d{4})/.exec('123-456-7890')[0]",
                "/(\\d{3})-(\\d{3})-(\\d{4})/.exec('123-456-7890')[1]",
                "/(\\d{3})-(\\d{3})-(\\d{4})/.exec('123-456-7890')[2]",
                "/(\\d{3})-(\\d{3})-(\\d{4})/.exec('123-456-7890')[3]");
        assertBooleanWithJavet(
                // Email-like pattern
                "/\\w+@\\w+\\.\\w+/.test('test@example.com')", "/\\w+@\\w+\\.\\w+/.test('not-an-email')",
                // URL-like pattern with lookahead
                "/https?(?=:\\/\\/)/.test('https://')", "/https?(?=:\\/\\/)/.test('http://')",
                "/https?(?=:\\/\\/)/.test('ftp://')");
    }

    @Test
    public void testJavetCustomCharacterClasses() {
        assertBooleanWithJavet("/[abc]/.test('apple')", "/[abc]/.test('dog')", "/[a-z]/.test('hello')",
                "/[0-9]/.test('123')");
    }

    @Test
    public void testJavetExecMethod() {
        // Test exec returning strings
        assertStringWithJavet("/test/.exec('this is a test')[0]", "/(\\d+)n/.exec('123n')[0]",
                "/(\\d+)n/.exec('123n')[1]");

        // Test exec returning index (number)
        assertIntegerWithJavet("/test/.exec('this is a test').index");
    }

    @Test
    public void testJavetFlags() {
        assertBooleanWithJavet("/test/i.test('TEST')", "/test/.test('TEST')");
    }

    @Test
    public void testJavetHexEscapes() {
        assertBooleanWithJavet("/\\x41/.test('A')", "/\\x41/.test('B')", "/\\x30/.test('0')", "/\\x20/.test(' ')",
                "/\\x48\\x65\\x6C\\x6C\\x6F/.test('Hello')", "/\\x48\\x65\\x6C\\x6C\\x6F/.test('World')");
    }

    @Test
    public void testJavetLookaheadAssertions() {
        assertStringWithJavet(
                // Positive lookahead
                "/\\d+(?=px)/.exec('100px')[0]");
        assertBooleanWithJavet(
                // Positive lookahead
                "/test(?=ing)/.test('testing')", "/test(?=ing)/.test('tested')", "/foo(?=bar)/.test('foobar')",
                "/foo(?=bar)/.test('foobaz')",
                // Negative lookahead
                "/test(?!ing)/.test('tested')", "/test(?!ing)/.test('testing')", "/\\d+(?!px)/.test('100em')",
                "/foo(?!bar)/.test('foobaz')", "/foo(?!bar)/.test('foobar')");

        // Lookahead doesn't consume characters
        assertIntegerWithJavet("/test(?=ing)/.exec('testing').index");
    }

    @Test
    public void testJavetMixedFeatures() {
        // Test lookahead in isolation first
        assertBooleanWithJavet(
                // Non-capturing group + lookahead
                "/(?:test)(?=ing)/.test('testing')",
                // Back reference + character class
                "/(\\w+)@\\1\\.com/.test('hello@hello.com')", "/(\\w+)@\\1\\.com/.test('hello@world.com')",
                // Multiple lookaheads
                "/test(?=.*ing)(?=.*st)/.test('testing')",
                // Hex escape + quantifier
                "/\\x48+/.test('HHH')",
                // Complex email validation
                "/\\w+(?:\\.[\\w-]+)*@\\w+(?:\\.[\\w-]+)+/.test('user.name@example.co.uk')",
                "/\\w+(?:\\.[\\w-]+)*@\\w+(?:\\.[\\w-]+)+/.test('invalid@')");
        assertStringWithJavet(
                // Repeated back reference
                "/(\\w)\\1+/.exec('aaa')[0]", "/(\\w)\\1+/.exec('hello')[0]");
    }

    @Test
    public void testJavetNonCapturingGroups() {
        assertBooleanWithJavet("/(?:abc)/.test('abc')", "/(?:abc)/.test('xyz')", "/(?:test)+/.test('testtesttest')",
                "/(?:a|b|c)/.test('b')");

        // Non-capturing groups don't create capture groups
        assertStringWithJavet("/(?:hello)+/.exec('hellohello')[0]", "/(?:a)(b)/.exec('ab')[1]");

        assertIntegerWithJavet("/(?:a)(b)/.exec('ab').length");
    }

    @Test
    public void testJavetNonDigitClass() {
        assertBooleanWithJavet("/\\D/.test('abc')", "/\\D/.test('123')", "/^\\D+$/.test('abc')", "/^\\D+$/.test('123')",
                "/\\d+\\D+\\d+/.test('123abc456')");
        assertStringWithJavet("/\\D+/.exec('abc123')[0]");
    }

    @Test
    public void testJavetQuantifiers() {
        assertBooleanWithJavet("/a+/.test('aaa')", "/\\d+/.test('123')", "/a*/.test('aaa')", "/a*b/.test('b')",
                "/colou?r/.test('color')", "/colou?r/.test('colour')", "/a{2,4}/.test('aaa')");
    }

    @Test
    public void testJavetSpecialCharacters() {
        assertBooleanWithJavet("/a.b/.test('aab')", "/a.b/.test('ab')");
    }

    @Test
    public void testJavetTestMethod() {
        assertBooleanWithJavet("/test/.test('this is a test')", "/^\\d+$/.test('12345')");
    }

    @Test
    public void testJavetUnicodeEscapes() {
        assertBooleanWithJavet(
                // Basic Unicode escapes
                "/\\u0041/.test('A')", "/\\u0041/.test('B')", "/\\u4E2D/.test('中')", "/\\u4E2D/.test('国')",
                "/\\u0048\\u0065\\u006C\\u006C\\u006F/.test('Hello')");
    }

    @Test
    public void testNonGlobalMatchReturnsExecValidationError() {
        JSObject receiver = context.createJSObject();
        receiver.set(PropertyKey.fromString("flags"), new JSString(""));
        receiver.set(PropertyKey.EXEC, new JSNativeFunction(context, "exec", 1, (ctx, self, args) -> JSBoolean.FALSE));
        JSValue result = callBuiltin(RegExpPrototype::symbolMatch, receiver, "x");
        assertThat(result).isSameAs(context.getPendingException());
        assertPendingTypeError(context, result, "RegExp exec method returned something other than an Object or null");
    }

    @Test
    public void testRegExpCharacterClassEscapesInClasses() {
        assertBooleanWithJavet("/^[\\D]+$/.test('abc')", "!/^[\\D]+$/.test('123')", "/^[\\W]+$/.test('!@#')",
                "!/^[\\W]+$/.test('abc_123')", "/^[\\S]+$/.test('abc')", "!/^[\\S]+$/.test(' \\t\\n')");
    }

    @Test
    public void testTest() {
        // Create a simple regex
        JSRegExp regexp = new JSRegExp(context, "test", "");

        // Normal case: match found
        BuiltinAssertions test = builtin(RegExpPrototype::test, regexp);
        test.expectValue(JSBoolean.TRUE, "this is a test");

        // Normal case: no match
        test.expectValue(JSBoolean.FALSE, "no match here");

        // Normal case: empty string
        test.expectValue(JSBoolean.FALSE, "");

        // Normal case: case insensitive
        JSRegExp caseInsensitive = new JSRegExp(context, "TEST", "i");
        JSValue result = callBuiltin(RegExpPrototype::test, caseInsensitive, "test");
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: global regex with lastIndex
        JSRegExp global = new JSRegExp(context, "test", "g");
        var testMethod = builtin(RegExpPrototype::test, global);
        result = testMethod.call("test test test");
        assertThat(result).isEqualTo(JSBoolean.TRUE);
        assertThat(global.getLastIndex()).isEqualTo(4); // After first match

        result = testMethod.call("test test test");
        assertThat(result).isEqualTo(JSBoolean.TRUE);
        assertThat(global.getLastIndex()).isEqualTo(9); // After second match

        // Normal case: no arguments (empty string)
        test.expectValue(JSBoolean.FALSE);

        // Edge case: called on non-RegExp
        result = callBuiltin(RegExpPrototype::test, new JSString("not a regexp"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testToStringMethod() {
        // Normal case: simple regex
        JSRegExp regexp = context.createJSRegExp("test", "");
        JSValue result = callBuiltin(RegExpPrototype::toStringMethod, regexp);
        assertStringValue(result, "/test/");

        // Normal case: regex with flags
        JSRegExp withFlags = context.createJSRegExp("hello", "gi");
        result = callBuiltin(RegExpPrototype::toStringMethod, withFlags);
        assertStringValue(result, "/hello/gi");

        // Normal case: pattern with special characters
        JSRegExp special = context.createJSRegExp("test", "m");
        result = callBuiltin(RegExpPrototype::toStringMethod, special);
        assertStringValue(result, "/test/m");

        // Edge case: called on non-RegExp
        result = callBuiltin(RegExpPrototype::toStringMethod, new JSString("not a regexp"));
        assertPendingTypeError(context, result);
    }
}
