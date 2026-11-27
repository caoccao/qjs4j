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

package com.caoccao.qjs4j.builtins.text;

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
        assertStringWithJavet("'test'.anchor('myAnchor')", "'hello'.anchor('link1')", "''.anchor('x')",
                "'text'.anchor('')");

        // Test quote escaping
        JSValue result = StringPrototype.anchor(context, new JSString("test"), stringArgs("my\"anchor"));
        assertThat(result).isInstanceOfSatisfying(JSString.class,
                jsStr -> assertThat(jsStr.value()).isEqualTo("<a name=\"my&quot;anchor\">test</a>"));
    }

    @Test
    public void testBig() {
        assertStringWithJavet("'test'.big()", "'hello'.big()", "''.big()");
    }

    @Test
    public void testBlink() {
        assertStringWithJavet("'test'.blink()", "'hello'.blink()", "''.blink()");
    }

    @Test
    public void testBold() {
        assertStringWithJavet("'test'.bold()", "'hello world'.bold()", "''.bold()");
    }

    @Test
    public void testCharAt() {
        // Normal case
        BuiltinAssertions charAt = builtin(StringPrototype::charAt, str);
        charAt.expectString("h", numberArgs(0));

        charAt.expectString("o", numberArgs(4));

        // Default index 0
        charAt.expectString("h");

        // Out of bounds
        charAt.expectString("", numberArgs(-1));

        charAt.expectString("", numberArgs(20));

        // Non-integer index
        charAt.expectString("e", stringArgs("1"));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.charAt(context, empty, numberArgs(0));
        assertStringValue(result, "");

        // Non-string thisArg
        result = StringPrototype.charAt(context, new JSNumber(42), numberArgs(0));
        assertStringValue(result, "4");
    }

    @Test
    public void testCharCodeAt() {
        // Normal case
        BuiltinAssertions charCodeAt = builtin(StringPrototype::charCodeAt, str);
        charCodeAt.expectNumber(104.0, numberArgs(0));

        charCodeAt.expectNumber(111.0, numberArgs(4));

        // Default index 0
        charCodeAt.expectNumber(104.0);

        // Out of bounds
        charCodeAt.expectNaN(numberArgs(-1));

        charCodeAt.expectNaN(numberArgs(20));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.charCodeAt(context, empty, numberArgs(0));
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isNaN());
    }

    @Test
    public void testCodePointAt() {
        // Normal case
        BuiltinAssertions codePointAt = builtin(StringPrototype::codePointAt, str);
        codePointAt.expectNumber(104.0, numberArgs(0));

        // Surrogate pair (if applicable)
        JSString surrogate = new JSString("😀");
        JSValue result = StringPrototype.codePointAt(context, surrogate, numberArgs(0));
        assertNumberValue(result, 128512.0);

        // Out of bounds
        codePointAt.expectValue(JSUndefined.INSTANCE, numberArgs(-1));

        codePointAt.expectValue(JSUndefined.INSTANCE, numberArgs(20));
    }

    @Test
    public void testConcat() {
        // Normal case
        BuiltinAssertions concat = builtin(StringPrototype::concat, str);
        concat.expectString("hello world test", stringArgs(" test"));

        // Multiple args
        concat.expectString("hello world test", stringArgs(" ", "test"));

        // No args
        concat.expectString("hello world");

        // Non-string args
        concat.expectString("hello world42", numberArgs(42));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.concat(context, empty, new JSValue[]{str});
        assertStringValue(result, "hello world");
    }

    @Test
    public void testConstructor() {
        assertStringWithJavet("'123'.constructor.name", "'123'.substring(1).constructor.name",
                "'123'.constructor.name.constructor.name");
    }

    @Test
    public void testEndsWith() {
        // Normal case
        BuiltinAssertions endsWith = builtin(StringPrototype::endsWith, str);
        endsWith.expectValue(JSBoolean.TRUE, stringArgs("world"));

        endsWith.expectValue(JSBoolean.FALSE, stringArgs("hello"));

        // With position
        endsWith.expectValue(JSBoolean.TRUE, new JSString("lo"), new JSNumber(5));

        // Position beyond length
        endsWith.expectValue(JSBoolean.TRUE, new JSString("world"), new JSNumber(50));

        // Position 0
        endsWith.expectValue(JSBoolean.FALSE, new JSString("world"), new JSNumber(0));

        // Empty search string
        endsWith.expectValue(JSBoolean.TRUE, stringArgs(""));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.endsWith(context, empty, stringArgs(""));
        assertThat(result).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet(
                // Verify that loose equality passes between primitive and primitive
                "'hello' == 'hello'", "'hello' == 'world'", "'hello' == String('hello')", "'hello' == String('world')",
                // Verify that strict equality passes between primitive and primitive
                "'hello' === 'hello'", "'hello' === 'world'", "'hello' === String('hello')",
                "'hello' === String('world')",
                // Verify that loose equality passes between primitive and primitive
                "String('hello') == String('hello')", "String('hello') == String('world')",
                "String('hello') == 'hello'", "String('hello') == 'world'",
                // Verify that loose equality passes between primitive and object
                "'hello' == new String('hello')", "'hello' == new String('world')",
                "String('hello') == new String('hello')", "String('hello') == new String('world')",
                // Verify that loose equality fails between object and object
                "new String('hello') == new String('hello')",
                // Verify that strict equality fails between primitive and object
                "'hello' === new String('hello')",
                // Verify that strict equality fails between object and object
                "new String('hello') === new String('hello')");
    }

    @Test
    public void testFixed() {
        assertStringWithJavet("'test'.fixed()", "'hello'.fixed()", "''.fixed()");
    }

    @Test
    public void testFontcolor() {
        assertStringWithJavet("'test'.fontcolor('red')", "'hello'.fontcolor('#FF0000')", "''.fontcolor('blue')",
                "'text'.fontcolor('')");

        // Test quote escaping
        JSValue result = StringPrototype.fontcolor(context, new JSString("test"), stringArgs("\"red\""));
        assertThat(result).isInstanceOfSatisfying(JSString.class,
                jsStr -> assertThat(jsStr.value()).isEqualTo("<font color=\"&quot;red&quot;\">test</font>"));
    }

    @Test
    public void testFontsize() {
        assertStringWithJavet("'test'.fontsize('5')", "'hello'.fontsize('3')", "''.fontsize('7')",
                "'text'.fontsize('1')");
    }

    @Test
    public void testFullLowercaseMappingAlongsideFinalSigma() {
        assertStringWithJavet("'İΟΣ'.toLowerCase()");
        assertStringWithJavet("'İΟΣ'.toLocaleLowerCase('en')");
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
        assertThat(mixed.value().length()).isEqualTo(11); // H e l l o 你 好 😀 (2 for 😀)

        // Empty string
        JSString empty = new JSString("");
        assertThat(empty.value().length()).isEqualTo(0);

        JSValue nonString = new JSNumber(123);
        assertThat(StringPrototype.getLength(context, nonString, JSValue.NO_ARGS).asNumber().map(JSNumber::value)
                .orElseThrow()).isEqualTo(0.0);

        assertIntegerWithJavet("'hello world'.length", "'你好世界'.length", "'😀🌟🚀'.length", "'Hello 你好 😀'.length",
                "''.length");

        assertErrorWithJavet("String.prototype.length.call({})", "String.prototype.length.call(123)",
                "String.prototype.length.call(true)", "String.prototype.length.call('abc')",
                "String['prototype'].length.call('abc')", "String.prototype.length.call(null)",
                "String.prototype.length.call(undefined)");
    }

    @Test
    public void testHtmlMethodArgumentChecks() {
        StringPrototype.blink(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);
        assertPendingException(context);

        StringPrototype.anchor(context, new JSString("x"), JSValue.NO_ARGS);
        assertPendingException(context);

        StringPrototype.fontcolor(context, new JSString("x"), JSValue.NO_ARGS);
        assertPendingException(context);

        StringPrototype.fontsize(context, new JSString("x"), JSValue.NO_ARGS);
        assertPendingException(context);

        StringPrototype.link(context, new JSString("x"), JSValue.NO_ARGS);
        assertPendingException(context);
    }

    @Test
    public void testIncludes() {
        // Normal case
        BuiltinAssertions includes = builtin(StringPrototype::includes, str);
        includes.expectValue(JSBoolean.TRUE, stringArgs("world"));

        includes.expectValue(JSBoolean.FALSE, stringArgs("test"));

        // With position
        includes.expectValue(JSBoolean.TRUE, new JSString("world"), new JSNumber(6));

        includes.expectValue(JSBoolean.FALSE, new JSString("world"), new JSNumber(7));

        // Empty search string
        includes.expectValue(JSBoolean.TRUE, stringArgs(""));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.includes(context, empty, stringArgs("test"));
        assertThat(result).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testIndexOf() {
        // Normal case
        BuiltinAssertions indexOf = builtin(StringPrototype::indexOf, str);
        indexOf.expectNumber(6.0, stringArgs("world"));

        indexOf.expectNumber(-1.0, stringArgs("test"));

        // With position
        indexOf.expectNumber(7.0, new JSString("o"), new JSNumber(5));

        // Position beyond length
        indexOf.expectNumber(-1.0, new JSString("world"), new JSNumber(50));

        // Empty search string
        indexOf.expectNumber(0.0, stringArgs(""));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.indexOf(context, empty, stringArgs("test"));
        assertNumberValue(result, -1.0);
    }

    @Test
    public void testIsWellFormed() {
        assertBooleanWithJavet("'hello'.isWellFormed()", "''.isWellFormed()", "'test string'.isWellFormed()",
                "'abc123'.isWellFormed()",
                // Test with valid surrogate pairs (emoji)
                "'\\uD83D\\uDE00'.isWellFormed()", // 😀 emoji
                "'hello\\uD83D\\uDE00world'.isWellFormed()");

        // Test unpaired surrogates
        JSValue result = StringPrototype.isWellFormed(context, new JSString("hello\uD800world"), JSValue.NO_ARGS);
        assertThat(result).isInstanceOfSatisfying(JSBoolean.class, jsBool -> assertThat(jsBool.value()).isFalse());

        result = StringPrototype.isWellFormed(context, new JSString("\uDFFFtest"), JSValue.NO_ARGS);
        assertThat(result).isInstanceOfSatisfying(JSBoolean.class, jsBool -> assertThat(jsBool.value()).isFalse());
    }

    @Test
    public void testItalics() {
        assertStringWithJavet("'test'.italics()", "'hello world'.italics()", "''.italics()");
    }

    @Test
    public void testLastIndexOf() {
        // Normal case
        BuiltinAssertions lastIndexOf = builtin(StringPrototype::lastIndexOf, str);
        lastIndexOf.expectNumber(7.0, stringArgs("o"));

        lastIndexOf.expectNumber(-1.0, stringArgs("test"));

        // With position
        lastIndexOf.expectNumber(4.0, new JSString("o"), new JSNumber(5));

        // Position 0
        lastIndexOf.expectNumber(0.0, new JSString("h"), new JSNumber(0));

        // Empty search string
        lastIndexOf.expectNumber(11.0, stringArgs(""));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.lastIndexOf(context, empty, stringArgs("test"));
        assertNumberValue(result, -1.0);
    }

    @Test
    public void testLink() {
        assertStringWithJavet("'test'.link('http://example.com')", "'click here'.link('https://google.com')",
                "''.link('url')", "'text'.link('')");

        // Test quote escaping
        JSValue result = StringPrototype.link(context, new JSString("test"),
                stringArgs("http://example.com?q=\"hello\""));
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value())
                .isEqualTo("<a href=\"http://example.com?q=&quot;hello&quot;\">test</a>"));
    }

    @Test
    public void testLithuanianSoftDottedUppercase() {
        assertStringWithJavet("""
                ['i\\u0307', 'j\\u0307', 'a\\u0307', 'i\\u0307b', 'I\\u0301'].map(
                    text => text.toLocaleUpperCase('lt')).join('|')
                """);
    }

    @Test
    public void testLocaleCompare() {
        assertIntegerWithJavet(
                // Test basic string comparison
                "'a'.localeCompare('b')", "'b'.localeCompare('a')", "'hello'.localeCompare('hello')",
                "'apple'.localeCompare('banana')", "'zebra'.localeCompare('apple')",
                // Test with different lengths
                "'abc'.localeCompare('ab')", "'ab'.localeCompare('abc')",
                // Test with empty strings
                "''.localeCompare('')", "'a'.localeCompare('')", "''.localeCompare('a')",
                // Test case sensitivity
                "'A'.localeCompare('a')", "'a'.localeCompare('A')",
                // Test with numbers in strings
                "'1'.localeCompare('2')", "'10'.localeCompare('2')",
                // Test with special characters
                "'hello world'.localeCompare('hello world')", "'test!'.localeCompare('test?')");
    }

    @Test
    public void testMatch() {
        assertStringWithJavet(
                // Match with string
                "JSON.stringify('hello world'.match('world'))", "JSON.stringify('hello world'.match('test'))",
                "JSON.stringify('hello world'.match('o'))",
                // Match with non-global regex
                "JSON.stringify('hello world'.match(/o/))", "JSON.stringify('hello world'.match(/world/))",
                "JSON.stringify('hello world'.match(/(\\w+) (\\w+)/))",
                // Match with global regex
                "JSON.stringify('hello world'.match(/o/g))", "JSON.stringify('hello world'.match(/l/g))",
                "JSON.stringify('hello world'.match(/xyz/g))",
                // No match cases
                "JSON.stringify('hello world'.match('xyz'))", "JSON.stringify('hello world'.match(/xyz/))",
                // Edge cases
                "JSON.stringify(''.match('test'))", "JSON.stringify('hello world'.match(''))");
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
    public void testMethodDescriptorsAndAliases() {
        assertBooleanWithJavet("!Object.getOwnPropertyDescriptor(String.prototype, 'blink').enumerable",
                "!Object.getOwnPropertyDescriptor(String.prototype, 'substr').enumerable",
                "!Object.getOwnPropertyDescriptor(String.prototype, 'trimLeft').enumerable",
                "!Object.getOwnPropertyDescriptor(String.prototype, 'trimRight').enumerable",
                "String.prototype.trimLeft === String.prototype.trimStart",
                "String.prototype.trimRight === String.prototype.trimEnd");

        assertStringWithJavet("String.prototype.trimLeft.name", "String.prototype.trimRight.name");
    }

    @Test
    public void testNormalize() {
        assertStringWithJavet(
                // Test default (NFC)
                "'hello'.normalize()", "'test'.normalize()", "''.normalize()",
                // Test NFC - basic ASCII should remain unchanged
                "'abc'.normalize('NFC')",
                // Test NFD - basic ASCII should remain unchanged
                "'abc'.normalize('NFD')",
                // Test NFKC
                "'abc'.normalize('NFKC')",
                // Test NFKD
                "'abc'.normalize('NFKD')");

        // Test that Angstrom sign normalizes correctly
        JSValue result = StringPrototype.normalize(context, new JSString("\u212B"), stringArgs("NFC"));
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> {
            // Both \u212B (Angstrom) and \u00C5 (A with ring) normalize to \u00C5 in NFC
            assertThat(jsStr.value()).isEqualTo("\u00C5");
        });

        // Test NFD produces decomposed form
        result = StringPrototype.normalize(context, new JSString("\u00C5"), stringArgs("NFD"));
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> {
            // \u00C5 decomposes to A (\u0041) + combining ring above (\u030A)
            assertThat(jsStr.value()).isEqualTo("\u0041\u030A");
        });

        // Test undefined form defaults to NFC
        JSValue result1 = StringPrototype.normalize(context, new JSString("\u212B"),
                new JSValue[]{JSUndefined.INSTANCE});
        JSValue result2 = StringPrototype.normalize(context, new JSString("\u212B"), stringArgs("NFC"));
        assertThat(result1).isInstanceOfSatisfying(JSString.class,
                jsStr1 -> assertThat(result2).isInstanceOfSatisfying(JSString.class,
                        jsStr2 -> assertThat(jsStr1.value()).isEqualTo(jsStr2.value())));

        // Test invalid form
        assertErrorWithJavet("'test'.normalize('INVALID')", "'test'.normalize('nfc')", "'test'.normalize('ABC')");
    }

    @Test
    public void testOptionalUndefinedArguments() {
        assertStringWithJavet("'a'.substr(0, undefined)", "'abc'.slice(1, undefined)", "'abc'.substring(1, undefined)",
                "'abc'.padStart(5, undefined)", "'abc'.padEnd(5, undefined)", "JSON.stringify('abc'.match())",
                "JSON.stringify(Array.from('abc'.matchAll()).map(v => v[0]))");

        assertIntegerWithJavet("'abc'.search()", "'abc'.lastIndexOf('', undefined)");

        assertBooleanWithJavet("'abc'.endsWith('abc', undefined)");
    }

    @Test
    public void testPadEnd() {
        // Normal case
        BuiltinAssertions padEnd = builtin(StringPrototype::padEnd, str);
        padEnd.expectString("hello world****", new JSNumber(15), new JSString("*"));

        // Shorter target length
        padEnd.expectString("hello world", numberArgs(5));

        // Default filler
        padEnd.expectString("hello world    ", numberArgs(15));

        // Empty filler
        padEnd.expectString("hello world", new JSNumber(15), new JSString(""));

        // Long filler
        padEnd.expectString("hello worldabca", new JSNumber(15), new JSString("abc"));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.padEnd(context, empty, new JSValue[]{new JSNumber(5), new JSString("*")});
        assertStringValue(result, "*****");
    }

    @Test
    public void testPadStart() {
        // Normal case
        BuiltinAssertions padStart = builtin(StringPrototype::padStart, str);
        padStart.expectString("****hello world", new JSNumber(15), new JSString("*"));

        // Shorter target length
        padStart.expectString("hello world", numberArgs(5));

        // Default filler
        padStart.expectString("    hello world", numberArgs(15));

        // Empty filler
        padStart.expectString("hello world", new JSNumber(15), new JSString(""));

        // Long filler
        padStart.expectString("abcahello world", new JSNumber(15), new JSString("abc"));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.padStart(context, empty, new JSValue[]{new JSNumber(5), new JSString("*")});
        assertStringValue(result, "*****");
    }

    @Test
    public void testPrototype() {
        assertObjectWithJavet("Object.getOwnPropertyNames(String.prototype).sort()");
    }

    @Test
    public void testRepeat() {
        // Normal case
        BuiltinAssertions repeat = builtin(StringPrototype::repeat, str);
        repeat.expectString("hello worldhello world", numberArgs(2));

        // Zero count
        repeat.expectString("", numberArgs(0));

        // One count
        repeat.expectString("hello world", numberArgs(1));

        // Negative count
        StringPrototype.repeat(context, str, numberArgs(-1));
        assertPendingException(context);

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.repeat(context, empty, numberArgs(3));
        assertStringValue(result, "");
    }

    @Test
    public void testReplace() {
        // Normal case
        BuiltinAssertions replace = builtin(StringPrototype::replace, str);
        replace.expectString("hello universe", stringArgs("world", "universe"));

        // No match
        replace.expectString("hello world", stringArgs("test", "universe"));

        // Replace with function (simplified, assuming string replacement)
        replace.expectString("hellx world", stringArgs("o", "x"));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.replace(context, empty, stringArgs("", "test"));
        assertStringValue(result, "test");
    }

    @Test
    public void testReplaceAll() {
        // Normal case
        JSValue result = StringPrototype.replaceAll(context, str, stringArgs("o", "x"));
        assertStringValue(result, "hellx wxrld");

        // No match
        result = StringPrototype.replaceAll(context, str, stringArgs("test", "universe"));
        assertStringValue(result, "hello world");

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.replaceAll(context, empty, stringArgs("", "test"));
        assertStringValue(result, "test");
    }

    @Test
    public void testReplaceAllWithRegExp() {
        assertStringWithJavet(
                // ReplaceAll with string
                "'hello world'.replaceAll('o', 'x')", "'hello world'.replaceAll('l', 'L')",
                "'hello world'.replaceAll('test', 'xyz')",
                // ReplaceAll with global regex
                "'hello world'.replaceAll(/o/g, 'x')", "'hello world'.replaceAll(/l/g, 'L')",
                "'hello world'.replaceAll(/[aeiou]/g, 'X')",
                // ReplaceAll with capture groups
                "'hello world, hello universe'.replaceAll(/(\\w+)/g, '[$1]')",
                "'abc123def456'.replaceAll(/(\\d+)/g, '($1)')",
                // Edge cases
                "''.replaceAll('test', 'xyz')", "''.replaceAll('', 'xyz')", "'hello world'.replaceAll('', 'X')",
                // Test non-global regex throws error
                "try { 'hello world'.replaceAll(/o/, 'x'); 'no error'; } catch(e) { e.message; }");
    }

    @Test
    public void testReplacementLiteralRuns() {
        assertStringWithJavet("""
                (() => {
                  const literal = 'xyz'.repeat(1000);
                  const templates = ['', literal, literal + '$', literal + '$1' + literal,
                    '$$' + literal + '$&' + literal + '$<word>' + literal + '$q'];
                  return JSON.stringify(templates.map(template => [
                    'xay'.replace('a', template),
                    'xayaz'.replaceAll('a', template),
                    'xayaz'.replace(/(?<word>a)/g, template)
                  ]));
                })()
                """);
    }

    @Test
    public void testReplacementNamedGroupAccessOrder() {
        assertStringWithJavet("""
                (() => {
                  const log = [];
                  const groups = {
                    get word() {
                      log.push('get');
                      return { toString() { log.push('string'); return 'A'; } };
                    },
                    get unused() { throw new Error('must stay lazy'); }
                  };
                  const re = /a/;
                  re.exec = () => Object.assign(['a'], { index: 1, groups });
                  const result = 'xay'.replace(re, '$<word>:$<missing>:$<word>:$<unterminated');
                  return result + '|' + log.join(',');
                })()
                """);
        assertStringWithJavet("""
                (() => {
                  const log = [];
                  const marker = {};
                  const groups = {
                    get first() {
                      log.push('get');
                      return { toString() { log.push('string'); throw marker; } };
                    },
                    get later() { log.push('later'); return 'bad'; }
                  };
                  const re = /a/;
                  re.exec = () => Object.assign(['a'], { index: 0, groups });
                  try { 'a'.replace(re, '$<first>$<later>'); }
                  catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing exception';
                })()
                """);
    }

    @Test
    public void testReplacementTokens() {
        assertStringWithJavet("\"xaby\".replace('ab', \"$$:$&:$`:$':$0:$01:$10:$99:$<word>:$<$&>:$\")",
                "\"xabyab\".replaceAll('ab', \"$$:$&:$`:$':$0:$01:$10:$99:$<word>:$<$&>:$\")",
                "\"xaby\".replace(/(?<word>a)(b)?/, \"$$:$&:$`:$':$0:$01:$10:$99:$<word>:$<missing>:$\")",
                "\"xay\".replace(/(?<word>a)(b)?/, \"$2:$02:$20:$<word>:$<missing>:$<unterminated\")",
                "\"abcdefghij\".replace(/(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)/, '$01:$10:$11:$99')");
    }

    @Test
    public void testReplaceWithRegExp() {
        assertStringWithJavet(
                // Replace with string
                "'hello world'.replace('world', 'universe')", "'hello world'.replace('o', 'x')",
                "'hello world'.replace('test', 'xyz')",
                // Replace with regex
                "'hello world'.replace(/world/, 'universe')", "'hello world'.replace(/o/, 'x')",
                "'hello world'.replace(/O/i, 'x')",
                // Replace with capture groups
                "'hello world'.replace(/(\\w+) (\\w+)/, '$2 $1')", "'hello world'.replace(/(\\w+)/, '[$1]')",
                "'hello world'.replace(/(o)/g, '($1)')",
                // Replace with $& (full match)
                "'hello world'.replace(/world/, '[$&]')",
                // Edge cases
                "''.replace('test', 'xyz')", "'hello world'.replace('', 'X')");
    }

    @Test
    public void testSearch() {
        assertIntegerWithJavet(
                // Search with string
                "'hello world'.search('world')", "'hello world'.search('o')", "'hello world'.search('test')",
                "'hello world'.search('xyz')",
                // Search with regex
                "'hello world'.search(/world/)", "'hello world'.search(/w\\w+/)", "'hello world'.search(/[aeiou]/)",
                "'hello world'.search(/xyz/)",
                // Search with case-insensitive regex
                "'hello world'.search(/WORLD/i)", "'hello world'.search(/W/i)",
                // Edge cases
                "''.search('test')", "'hello world'.search('')");
    }

    @Test
    public void testSlice() {
        // Normal case
        BuiltinAssertions slice = builtin(StringPrototype::slice, str);
        slice.expectString("world", numberArgs(6));

        slice.expectString("hello", numberArgs(0, 5));

        // Negative indices
        slice.expectString("world", numberArgs(-5));

        slice.expectString("hello", numberArgs(0, -6));

        // Out of bounds
        slice.expectString("", numberArgs(50));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.slice(context, empty, numberArgs(0));
        assertStringValue(result, "");
    }

    @Test
    public void testSmall() {
        assertStringWithJavet("'test'.small()", "'hello'.small()", "''.small()");
    }

    @Test
    public void testSplit() {
        // Normal case
        JSValue result = StringPrototype.split(context, str, stringArgs(" "));
        JSArray arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(2);
        assertStringValue(arr.get(0), "hello");
        assertStringValue(arr.get(1), "world");

        // No separator
        result = StringPrototype.split(context, str, JSValue.NO_ARGS);
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(1);
        assertStringValue(arr.get(0), "hello world");

        // Empty separator
        result = StringPrototype.split(context, str, stringArgs(""));
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(11); // "h","e","l","l","o"," ","w","o","r","l","d"

        // Limit
        result = StringPrototype.split(context, str, new JSValue[]{new JSString(" "), new JSNumber(1)});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(1);
        assertStringValue(arr.get(0), "hello");

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.split(context, empty, stringArgs(" "));
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(1);
        assertStringValue(arr.get(0), "");
    }

    @Test
    public void testSplitWithRegExp() {
        assertStringWithJavet(
                // Split with string
                "JSON.stringify('hello world'.split(' '))", "JSON.stringify('a,b,c'.split(','))",
                "JSON.stringify('hello world'.split('o'))",
                // Split with regex
                "JSON.stringify('hello world'.split(/\\s+/))", "JSON.stringify('a1b2c3'.split(/\\d/))",
                "JSON.stringify('hello  world'.split(/\\s+/))",
                // Split with capture groups
                "JSON.stringify('a1b2c3'.split(/(\\d)/))",
                // Split with limit
                "JSON.stringify('hello world'.split(' ', 1))", "JSON.stringify('a,b,c,d'.split(',', 2))",
                "JSON.stringify('hello world'.split(/\\s+/, 1))",
                // Edge cases
                "JSON.stringify('hello world'.split(''))", "JSON.stringify(''.split(' '))",
                "JSON.stringify('hello world'.split())");
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
    public void testStartsWith() {
        // Normal case
        BuiltinAssertions startsWith = builtin(StringPrototype::startsWith, str);
        startsWith.expectValue(JSBoolean.TRUE, stringArgs("hello"));

        startsWith.expectValue(JSBoolean.FALSE, stringArgs("world"));

        // With position
        startsWith.expectValue(JSBoolean.TRUE, new JSString("world"), new JSNumber(6));

        // Position beyond length
        startsWith.expectValue(JSBoolean.FALSE, new JSString("hello"), new JSNumber(50));

        // Empty search string
        startsWith.expectValue(JSBoolean.TRUE, stringArgs(""));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.startsWith(context, empty, stringArgs(""));
        assertThat(result).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testStrike() {
        assertStringWithJavet("'test'.strike()", "'hello world'.strike()", "''.strike()");
    }

    @Test
    public void testStringMatchProtocolsAcceptCallableProxy() {
        assertStringWithJavet("""
                (() => {
                    const receiver = {toString() { throw new Error('unexpected coercion'); }};
                    const pattern = {[Symbol.match]: new Proxy(function (input) {
                        return input === receiver && this === pattern ? 'matched' : 'wrong receiver';
                    }, {})};
                    return String.prototype.match.call(receiver, pattern);
                })()
                """, """
                (() => {
                    const receiver = {toString() { throw new Error('unexpected coercion'); }};
                    const pattern = {[Symbol.search]: new Proxy(function (input) {
                        return input === receiver && this === pattern ? 'matched' : 'wrong receiver';
                    }, {})};
                    return String.prototype.search.call(receiver, pattern);
                })()
                """, """
                (() => {
                    const receiver = {toString() { throw new Error('unexpected coercion'); }};
                    const pattern = {[Symbol.matchAll]: new Proxy(function (input) {
                        return input === receiver && this === pattern ? 'matched' : 'wrong receiver';
                    }, {})};
                    return String.prototype.matchAll.call(receiver, pattern);
                })()
                """);
    }

    @Test
    public void testSub() {
        assertStringWithJavet("'test'.sub()", "'H2O'.sub()", "''.sub()");
    }

    @Test
    public void testSubstr() {
        // Normal case
        BuiltinAssertions substr = builtin(StringPrototype::substr, str);
        substr.expectString("world", numberArgs(6, 5));

        // No length
        substr.expectString("world", numberArgs(6));

        // Negative start
        substr.expectString("world", numberArgs(-5, 5));

        // Negative length
        substr.expectString("", numberArgs(0, -1));

        // Out of bounds
        substr.expectString("", numberArgs(50));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.substr(context, empty, numberArgs(0));
        assertStringValue(result, "");

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        result = StringPrototype.substr(context, chinese, numberArgs(0, 2));
        assertStringValue(result, "你好");

        result = StringPrototype.substr(context, chinese, numberArgs(1, 2));
        assertStringValue(result, "好世");

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        result = StringPrototype.substr(context, emoji, numberArgs(0, 2));
        assertStringValue(result, "😀");

        result = StringPrototype.substr(context, emoji, numberArgs(2, 2));
        assertStringValue(result, "🌟");
    }

    @Test
    public void testSubstrAbruptConversions() {
        int[] lengthCallCount = new int[]{0};
        JSObject lenValueOf = context.createJSObject();
        lenValueOf.set("valueOf", new JSNativeFunction(context, "valueOf", 0, (ctx, thisArg, args) -> {
            lengthCallCount[0]++;
            return new JSNumber(1);
        }));

        StringPrototype.substr(context, new JSString(""), new JSValue[]{new JSSymbol("x"), lenValueOf});
        assertThat(lengthCallCount[0]).isEqualTo(0);
        assertPendingException(context);

        lengthCallCount[0] = 0;
        JSObject startValueOf = context.createJSObject();
        startValueOf.set("valueOf",
                new JSNativeFunction(context, "valueOf", 0, (ctx, thisArg, args) -> ctx.throwError("x")));

        StringPrototype.substr(context, new JSString(""), new JSValue[]{startValueOf, lenValueOf});
        assertThat(lengthCallCount[0]).isEqualTo(0);
        assertPendingException(context);

        StringPrototype.substr(context, new JSString(""), new JSValue[]{new JSNumber(0), new JSSymbol("x")});
        assertPendingException(context);
    }

    @Test
    public void testSubstring() {
        // Normal case
        BuiltinAssertions substring = builtin(StringPrototype::substring, str);
        substring.expectString("world", numberArgs(6));

        substring.expectString("hello", numberArgs(0, 5));

        // Swapped indices
        substring.expectString("hello", numberArgs(5, 0));

        // Negative indices
        substring.expectString("hello world", numberArgs(-5));

        // Out of bounds
        substring.expectString("", numberArgs(50));

        // Empty string
        JSString empty = new JSString("");
        JSValue result = StringPrototype.substring(context, empty, numberArgs(0));
        assertStringValue(result, "");

        // Chinese characters
        JSString chinese = new JSString("你好世界");
        result = StringPrototype.substring(context, chinese, numberArgs(0, 2));
        assertStringValue(result, "你好");

        result = StringPrototype.substring(context, chinese, numberArgs(1, 3));
        assertStringValue(result, "好世");

        // Emoji characters
        JSString emoji = new JSString("😀🌟🚀");
        result = StringPrototype.substring(context, emoji, numberArgs(0, 2));
        assertStringValue(result, "😀");

        result = StringPrototype.substring(context, emoji, numberArgs(2, 4));
        assertStringValue(result, "🌟");
    }

    @Test
    public void testSup() {
        assertStringWithJavet("'test'.sup()", "'x2'.sup()", "''.sup()");
    }

    @Test
    public void testSupplementalUnicodeCasePairs() {
        // Recent Latin, Cyrillic, Glagolitic, Vithkuqi and Garay letters on the Java 17 Unicode tables.
        assertStringWithJavet("""
                (() => {
                    const pairs = [[0xA7DC,0x019B], [0xA7CB,0x0264], [0x1C89,0x1C8A],
                        [0xA7CC,0xA7CD], [0xA7D0,0xA7D1], [0xA7D6,0xA7D7], [0xA7D8,0xA7D9],
                        [0xA7DA,0xA7DB], [0x2C2F,0x2C5F], [0xA7C0,0xA7C1], [0xA7CE,0xA7CF],
                        [0xA7D2,0xA7D3], [0xA7D4,0xA7D5], [0x10570,0x10597], [0x10D50,0x10D70]];
                    return pairs.map(([u,l]) => {
                        const upper = String.fromCodePoint(u), lower = String.fromCodePoint(l);
                        return [upper.toLowerCase().codePointAt(0), lower.toUpperCase().codePointAt(0),
                            new RegExp(upper, 'iu').test(lower)].join(':');
                    }).join(',');
                })()
                """);
    }

    @Test
    public void testSymbolMethodDispatch() {
        assertStringWithJavet("'abc'.split({ [Symbol.split](s, l) { return 'split:' + s + ':' + l; } }, 2)",
                "'abc'.match({ [Symbol.match](s) { return 'match:' + s; } })",
                "'abc'.replace({ [Symbol.replace](s, r) { return 'replace:' + s + ':' + r; } }, 'x')",
                "'abc'.replaceAll({ [Symbol.replace](s, r) { return 'replaceAll:' + s + ':' + r; } }, 'x')",
                "'abc'.matchAll({ [Symbol.matchAll](s) { return 'matchAll:' + s; } })");

        assertIntegerWithJavet("'abc'.search({ [Symbol.search](s) { return 7; } })");
    }

    @Test
    public void testToLocaleLowerCase() {
        assertStringWithJavet("'HELLO'.toLocaleLowerCase()", "'WORLD'.toLocaleLowerCase()",
                "'TeSt'.toLocaleLowerCase()", "''.toLocaleLowerCase()", "'abc'.toLocaleLowerCase()",
                "'ABC123'.toLocaleLowerCase()");

        // Test with locale parameter (currently ignored, but should work)
        JSValue result = StringPrototype.toLocaleLowerCase(context, new JSString("HELLO"), stringArgs("en-US"));
        assertStringValue(result, "hello");
    }

    @Test
    public void testToLocaleUpperCase() {
        assertStringWithJavet("'hello'.toLocaleUpperCase()", "'world'.toLocaleUpperCase()",
                "'TeSt'.toLocaleUpperCase()", "''.toLocaleUpperCase()", "'ABC'.toLocaleUpperCase()",
                "'abc123'.toLocaleUpperCase()");

        // Test with locale parameter (currently ignored, but should work)
        JSValue result = StringPrototype.toLocaleUpperCase(context, new JSString("hello"), stringArgs("en-US"));
        assertStringValue(result, "HELLO");
    }

    @Test
    public void testToLowerCase() {
        JSString upper = new JSString("HELLO WORLD");
        JSValue result = StringPrototype.toLowerCase(context, upper, JSValue.NO_ARGS);
        assertStringValue(result, "hello world");

        // Already lower
        result = StringPrototype.toLowerCase(context, str, JSValue.NO_ARGS);
        assertStringValue(result, "hello world");

        // Mixed
        JSString mixed = new JSString("HeLLo WoRLd");
        result = StringPrototype.toLowerCase(context, mixed, JSValue.NO_ARGS);
        assertStringValue(result, "hello world");

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toLowerCase(context, empty, JSValue.NO_ARGS);
        assertStringValue(result, "");
    }

    @Test
    public void testToString() {
        // Normal case
        JSValue result = StringPrototype.toString_(context, str, JSValue.NO_ARGS);
        assertStringValue(result, "hello world");

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toString_(context, empty, JSValue.NO_ARGS);
        assertStringValue(result, "");

        // Non-string thisArg
        try {
            StringPrototype.toString_(context, new JSNumber(42), JSValue.NO_ARGS);
        } catch (Exception e) {
            assertThat(e.getMessage())
                    .isEqualTo("TypeError: String.prototype.toString requires that 'this' be a String");
        }
        assertErrorWithJavet("String.prototype.toString.call(123)");
    }

    @Test
    public void testToUpperCase() {
        JSValue result = StringPrototype.toUpperCase(context, str, JSValue.NO_ARGS);
        assertStringValue(result, "HELLO WORLD");

        // Already upper
        JSString upper = new JSString("HELLO WORLD");
        result = StringPrototype.toUpperCase(context, upper, JSValue.NO_ARGS);
        assertStringValue(result, "HELLO WORLD");

        // Mixed
        JSString mixed = new JSString("HeLLo WoRLd");
        result = StringPrototype.toUpperCase(context, mixed, JSValue.NO_ARGS);
        assertStringValue(result, "HELLO WORLD");

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.toUpperCase(context, empty, JSValue.NO_ARGS);
        assertStringValue(result, "");
    }

    @Test
    public void testToWellFormed() {
        assertStringWithJavet("'hello'.toWellFormed()", "''.toWellFormed()", "'test string'.toWellFormed()",
                // Test with valid surrogate pairs
                "'\\uD83D\\uDE00'.toWellFormed()", // 😀 emoji
                "'hello\\uD83D\\uDE00world'.toWellFormed()");

        // Test unpaired surrogates get replaced with U+FFFD
        JSValue result = StringPrototype.toWellFormed(context, new JSString("hello\uD800world"), JSValue.NO_ARGS);
        assertStringValue(result, "hello\uFFFDworld");

        result = StringPrototype.toWellFormed(context, new JSString("\uDFFFtest"), JSValue.NO_ARGS);
        assertStringValue(result, "\uFFFDtest");

        // Already well-formed should return same string
        JSString wellFormed = new JSString("hello");
        result = StringPrototype.toWellFormed(context, wellFormed, JSValue.NO_ARGS);
        assertThat(result).isSameAs(wellFormed);
    }

    @Test
    public void testTrim() {
        JSString spaced = new JSString("  hello world  ");
        JSValue result = StringPrototype.trim(context, spaced, JSValue.NO_ARGS);
        assertStringValue(result, "hello world");

        // No spaces
        result = StringPrototype.trim(context, str, JSValue.NO_ARGS);
        assertStringValue(result, "hello world");

        // Only spaces
        JSString spaces = new JSString("   ");
        result = StringPrototype.trim(context, spaces, JSValue.NO_ARGS);
        assertStringValue(result, "");

        // Empty string
        JSString empty = new JSString("");
        result = StringPrototype.trim(context, empty, JSValue.NO_ARGS);
        assertStringValue(result, "");
    }

    @Test
    public void testTrimAliases() {
        // Test that trimLeft and trimRight work together
        assertStringWithJavet("'  hello  '.trimLeft().trimRight()", "'  hello  '.trimRight().trimLeft()",
                "'\\t\\nhello\\t\\n'.trimLeft().trimRight()");

        // Test combined trimming equals trim()
        assertBooleanWithJavet("'  test  '.trimLeft().trimRight() === '  test  '.trim()",
                "'\\t\\ntest\\t\\n'.trimLeft().trimRight() === '\\t\\ntest\\t\\n'.trim()");
    }

    @Test
    public void testTrimLeft() {
        // Test trimLeft as alias for trimStart
        assertStringWithJavet("'  hello'.trimLeft()", "'\\t\\nhello'.trimLeft()", "'hello  '.trimLeft()",
                "'  hello  '.trimLeft()", "'hello'.trimLeft()", "'   '.trimLeft()", "''.trimLeft()");

        // Verify it's identical to trimStart
        assertBooleanWithJavet("'  test  '.trimLeft() === '  test  '.trimStart()");
    }

    @Test
    public void testTrimRight() {
        // Test trimRight as alias for trimEnd
        assertStringWithJavet("'hello  '.trimRight()", "'hello\\t\\n'.trimRight()", "'  hello'.trimRight()",
                "'  hello  '.trimRight()", "'hello'.trimRight()", "'   '.trimRight()", "''.trimRight()");

        // Verify it's identical to trimEnd
        assertBooleanWithJavet("'  test  '.trimRight() === '  test  '.trimEnd()");
    }
}
