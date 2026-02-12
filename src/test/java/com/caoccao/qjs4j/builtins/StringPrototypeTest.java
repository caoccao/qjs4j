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
    public void testAnchor() {
        assertStringWithJavet(
                "'test'.anchor('myAnchor')",
                "'hello'.anchor('link1')",
                "''.anchor('x')",
                "'text'.anchor('')");

        // Test quote escaping
        JSValue result = StringPrototype.anchor(context, new JSString("test"), new JSValue[]{new JSString("my\"anchor")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("<a name=\"my&quot;anchor\">test</a>"));
    }

    @Test
    public void testBig() {
        assertStringWithJavet(
                "'test'.big()",
                "'hello'.big()",
                "''.big()");
    }

    @Test
    public void testBlink() {
        assertStringWithJavet(
                "'test'.blink()",
                "'hello'.blink()",
                "''.blink()");
    }

    @Test
    public void testBold() {
        assertStringWithJavet(
                "'test'.bold()",
                "'hello world'.bold()",
                "''.bold()");
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
    public void testConstructor() {
        assertStringWithJavet(
                "'123'.constructor.name",
                "'123'.substring(1).constructor.name",
                "'123'.constructor.name.constructor.name");
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
        assertBooleanWithJavet(
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
                "new String('hello') === new String('hello')");
    }

    @Test
    public void testFixed() {
        assertStringWithJavet(
                "'test'.fixed()",
                "'hello'.fixed()",
                "''.fixed()");
    }

    @Test
    public void testFontcolor() {
        assertStringWithJavet(
                "'test'.fontcolor('red')",
                "'hello'.fontcolor('#FF0000')",
                "''.fontcolor('blue')",
                "'text'.fontcolor('')");

        // Test quote escaping
        JSValue result = StringPrototype.fontcolor(context, new JSString("test"), new JSValue[]{new JSString("\"red\"")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("<font color=\"&quot;red&quot;\">test</font>"));
    }

    @Test
    public void testFontsize() {
        assertStringWithJavet(
                "'test'.fontsize('5')",
                "'hello'.fontsize('3')",
                "''.fontsize('7')",
                "'text'.fontsize('1')");
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

        JSValue nonString = new JSNumber(123);
        assertThat(StringPrototype.getLength(context, nonString, new JSValue[]{}).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        assertIntegerWithJavet("'hello world'.length",
                "'你好世界'.length",
                "'😀🌟🚀'.length",
                "'Hello 你好 😀'.length",
                "''.length");

        assertErrorWithJavet("String.prototype.length.call({})",
                "String.prototype.length.call(123)",
                "String.prototype.length.call(true)",
                "String.prototype.length.call('abc')",
                "String['prototype'].length.call('abc')",
                "String.prototype.length.call(null)",
                "String.prototype.length.call(undefined)");
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
    public void testIsWellFormed() {
        assertBooleanWithJavet(
                "'hello'.isWellFormed()",
                "''.isWellFormed()",
                "'test string'.isWellFormed()",
                "'abc123'.isWellFormed()",
                // Test with valid surrogate pairs (emoji)
                "'\\uD83D\\uDE00'.isWellFormed()", // 😀 emoji
                "'hello\\uD83D\\uDE00world'.isWellFormed()");

        // Test unpaired surrogates
        JSValue result = StringPrototype.isWellFormed(context, new JSString("hello\uD800world"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSBoolean.class, jsBool -> assertThat(jsBool.value()).isFalse());

        result = StringPrototype.isWellFormed(context, new JSString("\uDFFFtest"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSBoolean.class, jsBool -> assertThat(jsBool.value()).isFalse());
    }

    @Test
    public void testItalics() {
        assertStringWithJavet(
                "'test'.italics()",
                "'hello world'.italics()",
                "''.italics()");
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
    public void testLink() {
        assertStringWithJavet(
                "'test'.link('http://example.com')",
                "'click here'.link('https://google.com')",
                "''.link('url')",
                "'text'.link('')");

        // Test quote escaping
        JSValue result = StringPrototype.link(context, new JSString("test"), new JSValue[]{new JSString("http://example.com?q=\"hello\"")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("<a href=\"http://example.com?q=&quot;hello&quot;\">test</a>"));
    }

    @Test
    public void testLocaleCompare() {
        assertIntegerWithJavet(
                // Test basic string comparison
                "'a'.localeCompare('b')",
                "'b'.localeCompare('a')",
                "'hello'.localeCompare('hello')",
                "'apple'.localeCompare('banana')",
                "'zebra'.localeCompare('apple')",
                // Test with different lengths
                "'abc'.localeCompare('ab')",
                "'ab'.localeCompare('abc')",
                // Test with empty strings
                "''.localeCompare('')",
                "'a'.localeCompare('')",
                "''.localeCompare('a')",
                // Test case sensitivity
                "'A'.localeCompare('a')",
                "'a'.localeCompare('A')",
                // Test with numbers in strings
                "'1'.localeCompare('2')",
                "'10'.localeCompare('2')",
                // Test with special characters
                "'hello world'.localeCompare('hello world')",
                "'test!'.localeCompare('test?')");
    }

    @Test
    public void testMatch() {
        assertStringWithJavet(
                // Match with string
                "JSON.stringify('hello world'.match('world'))",
                "JSON.stringify('hello world'.match('test'))",
                "JSON.stringify('hello world'.match('o'))",
                // Match with non-global regex
                "JSON.stringify('hello world'.match(/o/))",
                "JSON.stringify('hello world'.match(/world/))",
                "JSON.stringify('hello world'.match(/(\\w+) (\\w+)/))",
                // Match with global regex
                "JSON.stringify('hello world'.match(/o/g))",
                "JSON.stringify('hello world'.match(/l/g))",
                "JSON.stringify('hello world'.match(/xyz/g))",
                // No match cases
                "JSON.stringify('hello world'.match('xyz'))",
                "JSON.stringify('hello world'.match(/xyz/))",
                // Edge cases
                "JSON.stringify(''.match('test'))",
                "JSON.stringify('hello world'.match(''))");
    }

    @Test
    public void testMatchAll() {
        assertStringWithJavet(
                // MatchAll with global regex - convert to array for comparison
                "JSON.stringify(Array.from('hello world'.matchAll(/o/g)).map(m => m[0]))",
                "JSON.stringify(Array.from('hello world'.matchAll(/l/g)).map(m => m[0]))",
                "JSON.stringify(Array.from('hello world'.matchAll(/(\\w)(\\w)/g)).map(m => [m[0], m[1], m[2]]))",
                // MatchAll with string (auto-converted to global regex)
                "JSON.stringify(Array.from('hello world'.matchAll('o')).map(m => m[0]))",
                "JSON.stringify(Array.from('hello world'.matchAll('l')).map(m => m[0]))",
                // No matches
                "JSON.stringify(Array.from('hello world'.matchAll(/xyz/g)).map(m => m[0]))",
                // Test non-global regex throws error
                "try { 'hello world'.matchAll(/o/); 'no error'; } catch(e) { e.message; }");
    }

    @Test
    public void testNormalize() {
        assertStringWithJavet(
                // Test default (NFC)
                "'hello'.normalize()",
                "'test'.normalize()",
                "''.normalize()",
                // Test NFC - basic ASCII should remain unchanged
                "'abc'.normalize('NFC')",
                // Test NFD - basic ASCII should remain unchanged
                "'abc'.normalize('NFD')",
                // Test NFKC
                "'abc'.normalize('NFKC')",
                // Test NFKD
                "'abc'.normalize('NFKD')");

        // Test that Angstrom sign normalizes correctly
        JSValue result = StringPrototype.normalize(context, new JSString("\u212B"), new JSValue[]{new JSString("NFC")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> {
            // Both \u212B (Angstrom) and \u00C5 (A with ring) normalize to \u00C5 in NFC
            assertThat(jsStr.value()).isEqualTo("\u00C5");
        });

        // Test NFD produces decomposed form
        result = StringPrototype.normalize(context, new JSString("\u00C5"), new JSValue[]{new JSString("NFD")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> {
            // \u00C5 decomposes to A (\u0041) + combining ring above (\u030A)
            assertThat(jsStr.value()).isEqualTo("\u0041\u030A");
        });

        // Test undefined form defaults to NFC
        JSValue result1 = StringPrototype.normalize(context, new JSString("\u212B"), new JSValue[]{JSUndefined.INSTANCE});
        JSValue result2 = StringPrototype.normalize(context, new JSString("\u212B"), new JSValue[]{new JSString("NFC")});
        assertThat(result1).isInstanceOfSatisfying(JSString.class, jsStr1 ->
                assertThat(result2).isInstanceOfSatisfying(JSString.class, jsStr2 ->
                        assertThat(jsStr1.value()).isEqualTo(jsStr2.value())));

        // Test invalid form
        assertErrorWithJavet(
                "'test'.normalize('INVALID')",
                "'test'.normalize('nfc')",
                "'test'.normalize('ABC')");
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
    public void testPrototype() {
        assertObjectWithJavet(
                "Object.getOwnPropertyNames(String.prototype).sort()");
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
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("test"));
    }

    @Test
    public void testReplaceAllWithRegExp() {
        assertStringWithJavet(
                // ReplaceAll with string
                "'hello world'.replaceAll('o', 'x')",
                "'hello world'.replaceAll('l', 'L')",
                "'hello world'.replaceAll('test', 'xyz')",
                // ReplaceAll with global regex
                "'hello world'.replaceAll(/o/g, 'x')",
                "'hello world'.replaceAll(/l/g, 'L')",
                "'hello world'.replaceAll(/[aeiou]/g, 'X')",
                // ReplaceAll with capture groups
                "'hello world, hello universe'.replaceAll(/(\\w+)/g, '[$1]')",
                "'abc123def456'.replaceAll(/(\\d+)/g, '($1)')",
                // Edge cases
                "''.replaceAll('test', 'xyz')",
                "''.replaceAll('', 'xyz')",
                "'hello world'.replaceAll('', 'X')",
                // Test non-global regex throws error
                "try { 'hello world'.replaceAll(/o/, 'x'); 'no error'; } catch(e) { e.message; }");
    }

    @Test
    public void testReplaceWithRegExp() {
        assertStringWithJavet(
                // Replace with string
                "'hello world'.replace('world', 'universe')",
                "'hello world'.replace('o', 'x')",
                "'hello world'.replace('test', 'xyz')",
                // Replace with regex
                "'hello world'.replace(/world/, 'universe')",
                "'hello world'.replace(/o/, 'x')",
                "'hello world'.replace(/O/i, 'x')",
                // Replace with capture groups
                "'hello world'.replace(/(\\w+) (\\w+)/, '$2 $1')",
                "'hello world'.replace(/(\\w+)/, '[$1]')",
                "'hello world'.replace(/(o)/g, '($1)')",
                // Replace with $& (full match)
                "'hello world'.replace(/world/, '[$&]')",
                // Edge cases
                "''.replace('test', 'xyz')",
                "'hello world'.replace('', 'X')");
    }

    @Test
    public void testSearch() {
        assertIntegerWithJavet(
                // Search with string
                "'hello world'.search('world')",
                "'hello world'.search('o')",
                "'hello world'.search('test')",
                "'hello world'.search('xyz')",
                // Search with regex
                "'hello world'.search(/world/)",
                "'hello world'.search(/w\\w+/)",
                "'hello world'.search(/[aeiou]/)",
                "'hello world'.search(/xyz/)",
                // Search with case-insensitive regex
                "'hello world'.search(/WORLD/i)",
                "'hello world'.search(/W/i)",
                // Edge cases
                "''.search('test')",
                "'hello world'.search('')");
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
    public void testSmall() {
        assertStringWithJavet(
                "'test'.small()",
                "'hello'.small()",
                "''.small()");
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
    public void testSplitWithRegExp() {
        assertStringWithJavet(
                // Split with string
                "JSON.stringify('hello world'.split(' '))",
                "JSON.stringify('a,b,c'.split(','))",
                "JSON.stringify('hello world'.split('o'))",
                // Split with regex
                "JSON.stringify('hello world'.split(/\\s+/))",
                "JSON.stringify('a1b2c3'.split(/\\d/))",
                "JSON.stringify('hello  world'.split(/\\s+/))",
                // Split with capture groups
                "JSON.stringify('a1b2c3'.split(/(\\d)/))",
                // Split with limit
                "JSON.stringify('hello world'.split(' ', 1))",
                "JSON.stringify('a,b,c,d'.split(',', 2))",
                "JSON.stringify('hello world'.split(/\\s+/, 1))",
                // Edge cases
                "JSON.stringify('hello world'.split(''))",
                "JSON.stringify(''.split(' '))",
                "JSON.stringify('hello world'.split())");
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
    public void testStartsEndsIncludesIsRegExpSemantics() {
        assertBooleanWithJavet(
                "(() => { const o = { [Symbol.match]: false, toString() { return 'a'; } }; return 'ab'.includes(o); })()",
                "(() => { const o = { [Symbol.match]: false, toString() { return 'a'; } }; return 'ab'.startsWith(o); })()",
                "(() => { const o = { [Symbol.match]: false, toString() { return 'b'; } }; return 'ab'.endsWith(o); })()",
                "(() => { const re = /a/; re[Symbol.match] = false; return 'ab'.includes(re) === false; })()",
                "(() => { const o = { [Symbol.match]: true, toString() { return 'a'; } }; try { 'ab'.includes(o); return false; } catch (e) { return e instanceof TypeError; } })()",
                "(() => { const o = { [Symbol.match]: true, toString() { return 'a'; } }; try { 'ab'.startsWith(o); return false; } catch (e) { return e instanceof TypeError; } })()",
                "(() => { const o = { [Symbol.match]: true, toString() { return 'b'; } }; try { 'ab'.endsWith(o); return false; } catch (e) { return e instanceof TypeError; } })()");
    }

    @Test
    public void testStrike() {
        assertStringWithJavet(
                "'test'.strike()",
                "'hello world'.strike()",
                "''.strike()");
    }

    @Test
    public void testSub() {
        assertStringWithJavet(
                "'test'.sub()",
                "'H2O'.sub()",
                "''.sub()");
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
    public void testSubstrAbruptConversions() {
        int[] lengthCallCount = new int[]{0};
        JSObject lenValueOf = context.createJSObject();
        lenValueOf.set("valueOf", new JSNativeFunction("valueOf", 0, (ctx, thisArg, args) -> {
            lengthCallCount[0]++;
            return new JSNumber(1);
        }));

        StringPrototype.substr(context, new JSString(""), new JSValue[]{new JSSymbol("x"), lenValueOf});
        assertThat(lengthCallCount[0]).isEqualTo(0);
        assertPendingException(context);

        lengthCallCount[0] = 0;
        JSObject startValueOf = context.createJSObject();
        startValueOf.set("valueOf", new JSNativeFunction("valueOf", 0, (ctx, thisArg, args) -> ctx.throwError("x")));

        StringPrototype.substr(context, new JSString(""), new JSValue[]{startValueOf, lenValueOf});
        assertThat(lengthCallCount[0]).isEqualTo(0);
        assertPendingException(context);

        StringPrototype.substr(context, new JSString(""), new JSValue[]{new JSNumber(0), new JSSymbol("x")});
        assertPendingException(context);
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
    public void testSup() {
        assertStringWithJavet(
                "'test'.sup()",
                "'x2'.sup()",
                "''.sup()");
    }

    @Test
    public void testToLocaleLowerCase() {
        assertStringWithJavet(
                "'HELLO'.toLocaleLowerCase()",
                "'WORLD'.toLocaleLowerCase()",
                "'TeSt'.toLocaleLowerCase()",
                "''.toLocaleLowerCase()",
                "'abc'.toLocaleLowerCase()",
                "'ABC123'.toLocaleLowerCase()");

        // Test with locale parameter (currently ignored, but should work)
        JSValue result = StringPrototype.toLocaleLowerCase(context, new JSString("HELLO"), new JSValue[]{new JSString("en-US")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("hello"));
    }

    @Test
    public void testToLocaleUpperCase() {
        assertStringWithJavet(
                "'hello'.toLocaleUpperCase()",
                "'world'.toLocaleUpperCase()",
                "'TeSt'.toLocaleUpperCase()",
                "''.toLocaleUpperCase()",
                "'ABC'.toLocaleUpperCase()",
                "'abc123'.toLocaleUpperCase()");

        // Test with locale parameter (currently ignored, but should work)
        JSValue result = StringPrototype.toLocaleUpperCase(context, new JSString("hello"), new JSValue[]{new JSString("en-US")});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("HELLO"));
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
        assertErrorWithJavet(
                "String.prototype.toString.call(123)");
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
    public void testToWellFormed() {
        assertStringWithJavet(
                "'hello'.toWellFormed()",
                "''.toWellFormed()",
                "'test string'.toWellFormed()",
                // Test with valid surrogate pairs
                "'\\uD83D\\uDE00'.toWellFormed()", // 😀 emoji
                "'hello\\uD83D\\uDE00world'.toWellFormed()");

        // Test unpaired surrogates get replaced with U+FFFD
        JSValue result = StringPrototype.toWellFormed(context, new JSString("hello\uD800world"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("hello\uFFFDworld"));

        result = StringPrototype.toWellFormed(context, new JSString("\uDFFFtest"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr ->
                assertThat(jsStr.value()).isEqualTo("\uFFFDtest"));

        // Already well-formed should return same string
        JSString wellFormed = new JSString("hello");
        result = StringPrototype.toWellFormed(context, wellFormed, new JSValue[]{});
        assertThat(result).isSameAs(wellFormed);
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

    @Test
    public void testTrimAliases() {
        // Test that trimLeft and trimRight work together
        assertStringWithJavet(
                "'  hello  '.trimLeft().trimRight()",
                "'  hello  '.trimRight().trimLeft()",
                "'\\t\\nhello\\t\\n'.trimLeft().trimRight()"
        );

        // Test combined trimming equals trim()
        assertBooleanWithJavet(
                "'  test  '.trimLeft().trimRight() === '  test  '.trim()",
                "'\\t\\ntest\\t\\n'.trimLeft().trimRight() === '\\t\\ntest\\t\\n'.trim()"
        );
    }

    @Test
    public void testTrimLeft() {
        // Test trimLeft as alias for trimStart
        assertStringWithJavet(
                "'  hello'.trimLeft()",
                "'\\t\\nhello'.trimLeft()",
                "'hello  '.trimLeft()",
                "'  hello  '.trimLeft()",
                "'hello'.trimLeft()",
                "'   '.trimLeft()",
                "''.trimLeft()");

        // Verify it's identical to trimStart
        assertBooleanWithJavet(
                "'  test  '.trimLeft() === '  test  '.trimStart()");
    }

    @Test
    public void testTrimRight() {
        // Test trimRight as alias for trimEnd
        assertStringWithJavet(
                "'hello  '.trimRight()",
                "'hello\\t\\n'.trimRight()",
                "'  hello'.trimRight()",
                "'  hello  '.trimRight()",
                "'hello'.trimRight()",
                "'   '.trimRight()",
                "''.trimRight()");

        // Verify it's identical to trimEnd
        assertBooleanWithJavet(
                "'  test  '.trimRight() === '  test  '.trimEnd()");
    }

    @Test
    public void testMethodDescriptorsAndAliases() {
        assertBooleanWithJavet(
                "!Object.getOwnPropertyDescriptor(String.prototype, 'blink').enumerable",
                "!Object.getOwnPropertyDescriptor(String.prototype, 'substr').enumerable",
                "!Object.getOwnPropertyDescriptor(String.prototype, 'trimLeft').enumerable",
                "!Object.getOwnPropertyDescriptor(String.prototype, 'trimRight').enumerable",
                "String.prototype.trimLeft === String.prototype.trimStart",
                "String.prototype.trimRight === String.prototype.trimEnd");

        assertStringWithJavet(
                "String.prototype.trimLeft.name",
                "String.prototype.trimRight.name");
    }

    @Test
    public void testOptionalUndefinedArguments() {
        assertStringWithJavet(
                "'a'.substr(0, undefined)",
                "'abc'.slice(1, undefined)",
                "'abc'.substring(1, undefined)",
                "'abc'.padStart(5, undefined)",
                "'abc'.padEnd(5, undefined)",
                "JSON.stringify('abc'.match())",
                "JSON.stringify(Array.from('abc'.matchAll()).map(v => v[0]))");

        assertIntegerWithJavet(
                "'abc'.search()",
                "'abc'.lastIndexOf('', undefined)");

        assertBooleanWithJavet(
                "'abc'.endsWith('abc', undefined)");
    }

    @Test
    public void testSymbolMethodDispatch() {
        assertStringWithJavet(
                "'abc'.split({ [Symbol.split](s, l) { return 'split:' + s + ':' + l; } }, 2)",
                "'abc'.match({ [Symbol.match](s) { return 'match:' + s; } })",
                "'abc'.replace({ [Symbol.replace](s, r) { return 'replace:' + s + ':' + r; } }, 'x')",
                "'abc'.replaceAll({ [Symbol.replace](s, r) { return 'replaceAll:' + s + ':' + r; } }, 'x')",
                "'abc'.matchAll({ [Symbol.matchAll](s) { return 'matchAll:' + s; } })");

        assertIntegerWithJavet(
                "'abc'.search({ [Symbol.search](s) { return 7; } })");
    }

    @Test
    public void testHtmlMethodArgumentChecks() {
        StringPrototype.blink(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertPendingException(context);

        StringPrototype.anchor(context, new JSString("x"), new JSValue[]{});
        assertPendingException(context);

        StringPrototype.fontcolor(context, new JSString("x"), new JSValue[]{});
        assertPendingException(context);

        StringPrototype.fontsize(context, new JSString("x"), new JSValue[]{});
        assertPendingException(context);

        StringPrototype.link(context, new JSString("x"), new JSValue[]{});
        assertPendingException(context);
    }
}
