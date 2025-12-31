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

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RegExpPrototype methods and Javet-based RegExp tests.
 */
public class RegExpPrototypeTest extends BaseJavetTest {

    @Test
    public void testExec() {
        // Create a simple regex
        JSRegExp regexp = new JSRegExp("hello", "");

        // Normal case: match found
        JSValue result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString("hello world")});
        JSArray matchArray = result.asArray().orElseThrow();
        assertThat(matchArray.getLength()).isEqualTo(1); // just the full match, no captures
        assertThat(matchArray.get(0)).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello"));
        assertThat(matchArray.get("index")).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(0.0));
        assertThat(matchArray.get("input")).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("hello world"));

        // Edge case: called on non-RegExp
        result = RegExpPrototype.exec(context, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetFlags() {
        // Normal case: no flags
        JSRegExp regexp = new JSRegExp("test", "");
        JSValue result = RegExpPrototype.getFlags(context, regexp, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo(""));

        // Normal case: multiple flags
        JSRegExp withFlags = new JSRegExp("hello", "gim");
        result = RegExpPrototype.getFlags(context, withFlags, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("gim"));

        // Normal case: single flag
        JSRegExp singleFlag = new JSRegExp("world", "i");
        result = RegExpPrototype.getFlags(context, singleFlag, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("i"));

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getFlags(context, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetGlobal() {
        // Normal case: global flag set
        JSRegExp global = new JSRegExp("test", "g");
        JSValue result = RegExpPrototype.getGlobal(context, global, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: global flag not set
        JSRegExp nonGlobal = new JSRegExp("test", "i");
        result = RegExpPrototype.getGlobal(context, nonGlobal, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp("test", "");
        result = RegExpPrototype.getGlobal(context, noFlags, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getGlobal(context, new JSString("not a regexp"), new JSValue[]{});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testGetIgnoreCase() {
        // Normal case: ignoreCase flag set
        JSRegExp ignoreCase = new JSRegExp("test", "i");
        JSValue result = RegExpPrototype.getIgnoreCase(context, ignoreCase, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: ignoreCase flag not set
        JSRegExp caseSensitive = new JSRegExp("test", "g");
        result = RegExpPrototype.getIgnoreCase(context, caseSensitive, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp("test", "");
        result = RegExpPrototype.getIgnoreCase(context, noFlags, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getIgnoreCase(context, new JSString("not a regexp"), new JSValue[]{});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testGetMultiline() {
        // Normal case: multiline flag set
        JSRegExp multiline = new JSRegExp("test", "m");
        JSValue result = RegExpPrototype.getMultiline(context, multiline, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: multiline flag not set
        JSRegExp singleLine = new JSRegExp("test", "g");
        result = RegExpPrototype.getMultiline(context, singleLine, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: no flags
        JSRegExp noFlags = new JSRegExp("test", "");
        result = RegExpPrototype.getMultiline(context, noFlags, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getMultiline(context, new JSString("not a regexp"), new JSValue[]{});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testGetSource() {
        // Normal case: simple pattern
        JSRegExp regexp = new JSRegExp("test", "");
        JSValue result = RegExpPrototype.getSource(context, regexp, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("test"));

        // Normal case: pattern with special characters
        JSRegExp special = new JSRegExp("test", "i");
        result = RegExpPrototype.getSource(context, special, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("test"));

        // Edge case: called on non-RegExp
        result = RegExpPrototype.getSource(context, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testJavetAlternation() {
        Stream.of(
                "/cat|dog/.test('I have a cat')",
                "/cat|dog/.test('I have a dog')",
                "/cat|dog/.test('I have a bird')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetBackReferences() {
        Stream.of(
                // Simple back reference
                "/(\\w+) \\1/.test('hello hello')",
                "/(\\w+) \\1/.test('hello world')",
                "/(\\d+)-\\1/.exec('123-123')[0] === '123-123'",
                "/(\\d+)-\\1/.test('123-456')",
                // Multiple back references
                "/(\\w)(\\w)\\2\\1/.test('abba')",
                "/(\\w)(\\w)\\2\\1/.test('abcd')",
                // Nested groups
                "/((\\w)\\w)\\1/.test('xyxy')",
                "/((\\w)\\w)\\1/.test('xyzz')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));

        // Back reference with case insensitive
        Stream.of(
                "/(\\w+) \\1/i.test('Hello HELLO')",
                "/(abc)\\1/i.test('ABCABC')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetBasicPatterns() {
        Stream.of(
                "/hello/.test('hello world')",
                "/hello/.test('goodbye')",
                "/^hello/.test('hello world')",
                "/world$/.test('hello world')",
                "/^test$/.test('test')",
                "/^test$/.test('testing')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetBigIntPattern() {
        Stream.of(
                "/^\\d+n$/.test('123n')",
                "/^\\d+n$/.test('0n')",
                "/^\\d+n$/.test('123')",
                "/^\\d+n$/.test('n')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetBoundaries() {
        Stream.of(
                "/\\btest\\b/.test('test')",
                "/\\btest\\b/.test('testing')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetCaptureGroups() {
        Stream.of(
                "'hello world'.match(/(\\w+) (\\w+)/)[0]",
                "'hello world'.match(/(\\w+) (\\w+)/)[1]",
                "'hello world'.match(/(\\w+) (\\w+)/)[2]").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetCharacterClasses() {
        Stream.of(
                "/\\d/.test('abc123')",
                "/\\d/.test('abc')",
                "/^\\d+$/.test('12345')",
                "/\\w/.test('test_123')",
                "/\\W/.test('test_123')",
                "/\\W/.test('!!')",
                "/^\\w+$/.test('test_123')",
                "/\\s/.test('hello world')",
                "/\\s/.test('helloworld')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetComplexPatterns() {
        Stream.of(
                // Combination of features
                "/(\\d{3})-(\\d{3})-(\\d{4})/.exec('123-456-7890')[0]",
                "/(\\d{3})-(\\d{3})-(\\d{4})/.exec('123-456-7890')[1]",
                "/(\\d{3})-(\\d{3})-(\\d{4})/.exec('123-456-7890')[2]",
                "/(\\d{3})-(\\d{3})-(\\d{4})/.exec('123-456-7890')[3]").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject()));
        Stream.of(
                // Email-like pattern
                "/\\w+@\\w+\\.\\w+/.test('test@example.com')",
                "/\\w+@\\w+\\.\\w+/.test('not-an-email')",
                // URL-like pattern with lookahead
                "/https?(?=:\\/\\/)/.test('https://')",
                "/https?(?=:\\/\\/)/.test('http://')",
                "/https?(?=:\\/\\/)/.test('ftp://')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetCustomCharacterClasses() {
        Stream.of(
                "/[abc]/.test('apple')",
                "/[abc]/.test('dog')",
                "/[a-z]/.test('hello')",
                "/[0-9]/.test('123')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetExecMethod() {
        // Test exec returning strings
        Stream.of(
                "/test/.exec('this is a test')[0]",
                "/(\\d+)n/.exec('123n')[0]",
                "/(\\d+)n/.exec('123n')[1]").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject()));

        // Test exec returning index (number)
        assertWithJavet(
                () -> v8Runtime.getExecutor("/test/.exec('this is a test').index").executeInteger().doubleValue(),
                () -> context.eval("/test/.exec('this is a test').index").toJavaObject());
    }

    @Test
    public void testJavetFlags() {
        Stream.of(
                "/test/i.test('TEST')",
                "/test/.test('TEST')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetHexEscapes() {
        Stream.of(
                "/\\x41/.test('A')",
                "/\\x41/.test('B')",
                "/\\x30/.test('0')",
                "/\\x20/.test(' ')",
                "/\\x48\\x65\\x6C\\x6C\\x6F/.test('Hello')",
                "/\\x48\\x65\\x6C\\x6C\\x6F/.test('World')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetLookaheadAssertions() {
        Stream.of(
                // Positive lookahead
                "/\\d+(?=px)/.exec('100px')[0]").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject()));
        Stream.of(
                // Positive lookahead
                "/test(?=ing)/.test('testing')",
                "/test(?=ing)/.test('tested')",
                "/foo(?=bar)/.test('foobar')",
                "/foo(?=bar)/.test('foobaz')",
                // Negative lookahead
                "/test(?!ing)/.test('tested')",
                "/test(?!ing)/.test('testing')",
                "/\\d+(?!px)/.test('100em')",
                "/foo(?!bar)/.test('foobaz')",
                "/foo(?!bar)/.test('foobar')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));

        // Lookahead doesn't consume characters
        String code = "/test(?=ing)/.exec('testing').index";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testJavetMixedFeatures() {
        // Test lookahead in isolation first
        Stream.of(
                // Non-capturing group + lookahead
                "/(?:test)(?=ing)/.test('testing')",
                // Back reference + character class
                "/(\\w+)@\\1\\.com/.test('hello@hello.com')",
                "/(\\w+)@\\1\\.com/.test('hello@world.com')",
                // Multiple lookaheads
                "/test(?=.*ing)(?=.*st)/.test('testing')",
                // Hex escape + quantifier
                "/\\x48+/.test('HHH')",
                // Complex email validation
                "/\\w+(?:\\.[\\w-]+)*@\\w+(?:\\.[\\w-]+)+/.test('user.name@example.co.uk')",
                "/\\w+(?:\\.[\\w-]+)*@\\w+(?:\\.[\\w-]+)+/.test('invalid@')").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeBoolean(),
                        () -> context.eval(code).toJavaObject()));
        Stream.of(
                // Repeated back reference
                "/(\\w)\\1+/.exec('aaa')[0]",
                "/(\\w)\\1+/.exec('hello')[0]").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetNonCapturingGroups() {
        Stream.of(
                "/(?:abc)/.test('abc')",
                "/(?:abc)/.test('xyz')",
                "/(?:test)+/.test('testtesttest')",
                "/(?:a|b|c)/.test('b')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));

        // Non-capturing groups don't create capture groups
        Stream.of(
                "/(?:hello)+/.exec('hellohello')[0]",
                "/(?:a)(b)/.exec('ab')[1]").forEach(code ->
                assertWithJavet(
                        () -> v8Runtime.getExecutor(code).executeString(),
                        () -> context.eval(code).toJavaObject()));

        String code = "/(?:a)(b)/.exec('ab').length";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testJavetNonDigitClass() {
        Stream.of(
                "/\\D/.test('abc')",
                "/\\D/.test('123')",
                "/^\\D+$/.test('abc')",
                "/^\\D+$/.test('123')",
                "/\\d+\\D+\\d+/.test('123abc456')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
        Stream.of(
                "/\\D+/.exec('abc123')[0]").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetQuantifiers() {
        Stream.of(
                "/a+/.test('aaa')",
                "/\\d+/.test('123')",
                "/a*/.test('aaa')",
                "/a*b/.test('b')",
                "/colou?r/.test('color')",
                "/colou?r/.test('colour')",
                "/a{2,4}/.test('aaa')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetSpecialCharacters() {
        Stream.of(
                "/a.b/.test('aab')",
                "/a.b/.test('ab')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetTestMethod() {
        Stream.of(
                "/test/.test('this is a test')",
                "/^\\d+$/.test('12345')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testJavetUnicodeEscapes() {
        Stream.of(
                // Basic Unicode escapes
                "/\\u0041/.test('A')",
                "/\\u0041/.test('B')",
                "/\\u4E2D/.test('中')",
                "/\\u4E2D/.test('国')",
                "/\\u0048\\u0065\\u006C\\u006C\\u006F/.test('Hello')").forEach(code -> assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject()));
    }

    @Test
    public void testTest() {
        // Create a simple regex
        JSRegExp regexp = new JSRegExp("test", "");

        // Normal case: match found
        JSValue result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("this is a test")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: no match
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("no match here")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: empty string
        result = RegExpPrototype.test(context, regexp, new JSValue[]{new JSString("")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: case insensitive
        JSRegExp caseInsensitive = new JSRegExp("TEST", "i");
        result = RegExpPrototype.test(context, caseInsensitive, new JSValue[]{new JSString("test")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: global regex with lastIndex
        JSRegExp global = new JSRegExp("test", "g");
        result = RegExpPrototype.test(context, global, new JSValue[]{new JSString("test test test")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);
        assertThat(global.getLastIndex()).isEqualTo(4); // After first match

        result = RegExpPrototype.test(context, global, new JSValue[]{new JSString("test test test")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);
        assertThat(global.getLastIndex()).isEqualTo(9); // After second match

        // Normal case: no arguments (empty string)
        result = RegExpPrototype.test(context, regexp, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-RegExp
        result = RegExpPrototype.test(context, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testToStringMethod() {
        // Normal case: simple regex
        JSRegExp regexp = new JSRegExp("test", "");
        JSValue result = RegExpPrototype.toStringMethod(context, regexp, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("/test/"));

        // Normal case: regex with flags
        JSRegExp withFlags = new JSRegExp("hello", "gi");
        result = RegExpPrototype.toStringMethod(context, withFlags, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("/hello/gi"));

        // Normal case: pattern with special characters
        JSRegExp special = new JSRegExp("test", "m");
        result = RegExpPrototype.toStringMethod(context, special, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("/test/m"));

        // Edge case: called on non-RegExp
        result = RegExpPrototype.toStringMethod(context, new JSString("not a regexp"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }
}
