package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for global object functions.
 */
public class JSGlobalObjectTest extends BaseJavetTest {
    @Test
    public void testDecodeURIBasicAscii() {
        // Basic ASCII percent-decoding
        assertStringWithJavet(
                "decodeURIComponent('%41')",        // A
                "decodeURIComponent('%61')",        // a
                "decodeURIComponent('%30')",        // 0
                "decodeURIComponent('%20')",        // space
                "decodeURIComponent('%7E')",        // ~
                "decodeURIComponent('%21')",        // !
                "decodeURIComponent('%2A')",        // *
                "decodeURIComponent('%28')",        // (
                "decodeURIComponent('%29')",        // )
                "decodeURIComponent('%27')",        // '
                "decodeURIComponent('%2D')",        // -
                "decodeURIComponent('%5F')",        // _
                "decodeURIComponent('%2E')",        // .
                "decodeURIComponent('%7e')",        // ~ (lowercase hex)
                "decodeURIComponent('%2f')",        // / (lowercase hex)
                "decodeURIComponent('%25')");       // %
    }

    @Test
    public void testDecodeURIComponentReservedChars() {
        // decodeURIComponent decodes ALL percent-encoded chars including reserved ones
        assertStringWithJavet(
                "decodeURIComponent('%3B')",        // ;
                "decodeURIComponent('%2F')",        // /
                "decodeURIComponent('%3F')",        // ?
                "decodeURIComponent('%3A')",        // :
                "decodeURIComponent('%40')",        // @
                "decodeURIComponent('%26')",        // &
                "decodeURIComponent('%3D')",        // =
                "decodeURIComponent('%2B')",        // +
                "decodeURIComponent('%24')",        // $
                "decodeURIComponent('%2C')",        // ,
                "decodeURIComponent('%23')");       // #
    }

    @Test
    public void testDecodeURIDecodesUnreservedChars() {
        // decodeURI DOES decode unreserved chars (not in reserved set)
        assertStringWithJavet(
                "decodeURI('%20')",         // space - not reserved, decoded
                "decodeURI('%41')",         // A - not reserved, decoded
                "decodeURI('%61')",         // a - not reserved, decoded
                "decodeURI('%25')",         // % - not reserved, decoded
                "decodeURI('%5B')",         // [ - not reserved, decoded
                "decodeURI('%5D')",         // ] - not reserved, decoded
                "decodeURI('%7B')",         // { - not reserved, decoded
                "decodeURI('%7D')");        // } - not reserved, decoded
    }

    @Test
    public void testDecodeURIErrorCodepointTooLarge() {
        // Codepoint > U+10FFFF
        assertStringWithJavet(
                "(() => { try { decodeURIComponent('%F4%90%80%80'); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testDecodeURIErrorIncompletePercent() {
        // Incomplete percent sequences
        assertStringWithJavet(
                "(() => { try { decodeURIComponent('%'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURIComponent('%0'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURIComponent('abc%'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURIComponent('abc%2'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURI('%'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURI('%G'); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testDecodeURIErrorInvalidContinuationByte() {
        // Invalid continuation bytes
        assertStringWithJavet(
                // Lone continuation byte
                "(() => { try { decodeURIComponent('%80'); return 'no error'; } catch (e) { return e.name; } })()",
                // Start of 2-byte seq followed by non-continuation
                "(() => { try { decodeURIComponent('%C3%00'); return 'no error'; } catch (e) { return e.name; } })()",
                // 0xFE - invalid start byte
                "(() => { try { decodeURIComponent('%FE'); return 'no error'; } catch (e) { return e.name; } })()",
                // 0xFF - invalid start byte
                "(() => { try { decodeURIComponent('%FF'); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testDecodeURIErrorInvalidHex() {
        // Invalid hex characters after %
        assertStringWithJavet(
                "(() => { try { decodeURIComponent('%GG'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURIComponent('%ZZ'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURIComponent('%0G'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURIComponent('%G0'); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testDecodeURIErrorOverlongEncoding() {
        // Overlong UTF-8 encodings (must be rejected)
        assertStringWithJavet(
                // 2-byte overlong for U+002F (should be 1 byte)
                "(() => { try { decodeURIComponent('%C0%AF'); return 'no error'; } catch (e) { return e.name; } })()",
                // 2-byte overlong for U+0000
                "(() => { try { decodeURIComponent('%C0%80'); return 'no error'; } catch (e) { return e.name; } })()",
                // 3-byte overlong for U+002F
                "(() => { try { decodeURIComponent('%E0%80%AF'); return 'no error'; } catch (e) { return e.name; } })()",
                // 4-byte overlong for U+002F
                "(() => { try { decodeURIComponent('%F0%80%80%AF'); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testDecodeURIErrorSurrogateCodepoints() {
        // Surrogate codepoints (U+D800 to U+DFFF) must be rejected
        assertStringWithJavet(
                // U+D800 (high surrogate start)
                "(() => { try { decodeURIComponent('%ED%A0%80'); return 'no error'; } catch (e) { return e.name; } })()",
                // U+DBFF (high surrogate end)
                "(() => { try { decodeURIComponent('%ED%AF%BF'); return 'no error'; } catch (e) { return e.name; } })()",
                // U+DC00 (low surrogate start)
                "(() => { try { decodeURIComponent('%ED%B0%80'); return 'no error'; } catch (e) { return e.name; } })()",
                // U+DFFF (low surrogate end)
                "(() => { try { decodeURIComponent('%ED%BF%BF'); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testDecodeURIErrorTruncatedMultibyte() {
        // Truncated multi-byte UTF-8 sequences
        assertStringWithJavet(
                // 2-byte sequence missing continuation
                "(() => { try { decodeURIComponent('%C3'); return 'no error'; } catch (e) { return e.name; } })()",
                // 3-byte sequence missing last continuation
                "(() => { try { decodeURIComponent('%E4%B8'); return 'no error'; } catch (e) { return e.name; } })()",
                // 4-byte sequence missing continuations
                "(() => { try { decodeURIComponent('%F0%9F'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURIComponent('%F0%9F%98'); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testDecodeURIFunctionProperties() {
        // Function name and length properties
        assertStringWithJavet(
                "decodeURIComponent.name",
                "decodeURI.name");
        assertBooleanWithJavet(
                "decodeURIComponent.length === 1",
                "decodeURI.length === 1",
                "typeof decodeURIComponent === 'function'",
                "typeof decodeURI === 'function'");
    }

    @Test
    public void testDecodeURIMixedContent() {
        // Mixed encoded and plain text
        assertStringWithJavet(
                "decodeURIComponent('Hello%20World')",
                "decodeURIComponent('a%2Bb%20c')",
                "decodeURIComponent('%E4%BD%A0%E5%A5%BDworld')",
                "decodeURIComponent('test%3Dvalue%26key%3Dother')",
                "decodeURIComponent('100%25%20correct')",
                "decodeURIComponent('path%2Fto%2Ffile')",
                "decodeURI('https://example.com/a%20b?x=1&y=2#hash')",
                "decodeURI('%E4%BD%A0%E5%A5%BD')");
    }

    @Test
    public void testDecodeURIPassthrough() {
        // Strings without percent encoding pass through unchanged
        assertStringWithJavet(
                "decodeURIComponent('hello')",
                "decodeURIComponent('abc123')",
                "decodeURIComponent('')",
                "decodeURIComponent('no-encoding-here')",
                "decodeURIComponent('ABCxyz')",
                "decodeURI('hello')",
                "decodeURI('')");
    }

    @Test
    public void testDecodeURIPreservesReservedChars() {
        // decodeURI does NOT decode reserved chars and # - they stay percent-encoded
        assertStringWithJavet(
                "decodeURI('%3B')",         // ; - reserved, stays encoded
                "decodeURI('%2F')",         // / - reserved, stays encoded
                "decodeURI('%3F')",         // ? - reserved, stays encoded
                "decodeURI('%3A')",         // : - reserved, stays encoded
                "decodeURI('%40')",         // @ - reserved, stays encoded
                "decodeURI('%26')",         // & - reserved, stays encoded
                "decodeURI('%3D')",         // = - reserved, stays encoded
                "decodeURI('%2B')",         // + - reserved, stays encoded
                "decodeURI('%24')",         // $ - reserved, stays encoded
                "decodeURI('%2C')",         // , - reserved, stays encoded
                "decodeURI('%23')");        // # - reserved, stays encoded
    }

    @Test
    public void testDecodeURIRoundTrip() {
        // Round-trip: encode then decode should return original
        assertBooleanWithJavet(
                "decodeURIComponent(encodeURIComponent('Hello World')) === 'Hello World'",
                "decodeURIComponent(encodeURIComponent(';/?:@&=+$,#')) === ';/?:@&=+$,#'",
                "decodeURIComponent(encodeURIComponent('\\u4F60\\u597D')) === '\\u4F60\\u597D'",
                "decodeURIComponent(encodeURIComponent('\\uD83D\\uDE00')) === '\\uD83D\\uDE00'",
                "decodeURIComponent(encodeURIComponent('')) === ''",
                "decodeURI(encodeURI('https://example.com/path?q=hello world#frag')) === 'https://example.com/path?q=hello world#frag'");
    }

    @Test
    public void testDecodeURITypeCoercion() {
        // Non-string inputs are converted to string first
        assertStringWithJavet(
                "decodeURIComponent(42)",
                "decodeURIComponent(true)",
                "decodeURIComponent(false)",
                "decodeURIComponent(null)",
                "decodeURI(42)",
                "decodeURI(null)");
    }

    @Test
    public void testDecodeURIUndefinedInput() {
        // undefined input - should return "undefined"
        assertStringWithJavet(
                "decodeURIComponent()",
                "decodeURIComponent(undefined)",
                "decodeURI()",
                "decodeURI(undefined)");
    }

    @Test
    public void testDecodeURIUtf8FourByte() {
        // UTF-8 4-byte sequences (U+10000 to U+10FFFF)
        assertStringWithJavet(
                "decodeURIComponent('%F0%9F%98%80')",  // 😀 (U+1F600)
                "decodeURIComponent('%F0%9F%8E%89')",  // 🎉 (U+1F389)
                "decodeURIComponent('%F0%90%80%80')",  // 𐀀 (U+10000, lowest 4-byte)
                "decodeURIComponent('%F0%9F%92%A9')",  // 💩 (U+1F4A9)
                "decodeURIComponent('%F4%8F%BF%BF')"); // U+10FFFF (highest valid)
    }

    @Test
    public void testDecodeURIUtf8ThreeByte() {
        // UTF-8 3-byte sequences (U+0800 to U+FFFF, excluding surrogates)
        assertStringWithJavet(
                "decodeURIComponent('%E4%B8%AD')",     // 中 (U+4E2D)
                "decodeURIComponent('%E2%82%AC')",     // € (U+20AC)
                "decodeURIComponent('%E2%9C%93')",     // ✓ (U+2713)
                "decodeURIComponent('%E0%A0%80')",     // U+0800 (lowest 3-byte)
                "decodeURIComponent('%EF%BF%BD')",     // U+FFFD (replacement char)
                "decodeURIComponent('%EF%BF%BF')");    // U+FFFF (highest 3-byte)
    }

    @Test
    public void testDecodeURIUtf8TwoByte() {
        // UTF-8 2-byte sequences (U+0080 to U+07FF)
        assertStringWithJavet(
                "decodeURIComponent('%C2%A2')",        // ¢ (U+00A2)
                "decodeURIComponent('%C3%A9')",        // é (U+00E9)
                "decodeURIComponent('%C3%BC')",        // ü (U+00FC)
                "decodeURIComponent('%C3%B1')",        // ñ (U+00F1)
                "decodeURIComponent('%C2%A9')",        // © (U+00A9)
                "decodeURIComponent('%C2%AE')",        // ® (U+00AE)
                "decodeURIComponent('%C2%80')",        // U+0080 (lowest 2-byte)
                "decodeURIComponent('%DF%BF')");       // U+07FF (highest 2-byte)
    }

    @Test
    public void testEscape() {
        assertStringWithJavet(
                // Test basic ASCII characters that should not be escaped
                "escape('abc')",
                "escape('ABC')",
                "escape('123')",
                "escape('@*_+-./')",
                // Test characters that should be escaped with %XX format
                "escape('Hello World')",
                "escape('a b c')",
                "escape('!#$%&()=')",
                "escape('a=b&c=d')",
                "escape('test:value')",
                "escape('<script>')",
                // Test Unicode characters (should use %uXXXX format)
                "escape('你好')",
                "escape('世界')",
                "escape('Hello 世界')",
                "escape('abc中def')",
                // Test empty string
                "escape('')",
                // Test special cases
                "escape('100% correct')",
                "escape('email@domain.com')",
                "escape('path/to/file.txt')",
                "JSON.stringify(Object.getOwnPropertyDescriptor(escape, \"name\"))",
                "JSON.stringify(Object.getOwnPropertyDescriptor(escape, \"length\"))");
    }

    @Test
    public void testEscapeEdgeCases() {
        assertStringWithJavet(
                // Test with numbers and other types (should convert to string first)
                "escape('42')",
                "escape('3.14')",
                // Test consecutive spaces and special characters
                "escape('  ')",
                "escape('!!!')",
                "escape('###')");
    }

    @Test
    public void testEscapeUnescapeRoundTrip() {
        // Test that escape and unescape are inverses
        assertStringWithJavet(
                "unescape(escape('Hello World'))",
                "unescape(escape('test@example.com'))",
                "unescape(escape('a=b&c=d'))",
                "unescape(escape('你好世界'))",
                "unescape(escape('Hello 世界!'))",
                "unescape(escape('!@#$%^&*()'))",
                "unescape(escape(''))");
    }

    @Test
    public void testGlobalDescriptorsAndTagWithQuickJSSemantics() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(globalThis, 'parseInt').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'parseInt').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'parseInt').configurable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'Object').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'Infinity').writable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'Infinity').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'Infinity').configurable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'NaN').writable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'undefined').writable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'globalThis').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'globalThis').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'globalThis').configurable === true");
        assertThat(context.eval("globalThis[Symbol.toStringTag]").toJavaObject()).isEqualTo("global");
        assertThat(context.eval("Object.prototype.toString.call(globalThis)").toJavaObject()).isEqualTo("[object global]");
    }

    @Test
    public void testGlobalFunctionDescriptorsForUriAndNumericParsing() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(globalThis, 'decodeURI').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'decodeURI').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'decodeURI').configurable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'decodeURIComponent').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'encodeURI').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'encodeURIComponent').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'escape').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'unescape').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'isFinite').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'isNaN').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'parseFloat').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'parseInt').writable === true",
                "decodeURI.length === 1",
                "decodeURIComponent.length === 1",
                "encodeURI.length === 1",
                "encodeURIComponent.length === 1",
                "escape.length === 1",
                "unescape.length === 1",
                "isFinite.length === 1",
                "isNaN.length === 1",
                "parseFloat.length === 1",
                "parseInt.length === 2");
    }

    @Test
    public void testGlobalFunctionErrorsWithQuickJSSemantics() {
        String[] codeArray = {
                "new parseInt()",
                "new isNaN()",
                "new decodeURI()",
                "isNaN(Symbol())",
                "isFinite(Symbol())",
                "isNaN(1n)",
                "isFinite(1n)",
                "isNaN(Object(Symbol()))",
                "isFinite(Object(Symbol()))",
                "isNaN(Object(1n))",
                "isFinite(Object(1n))"
        };
        for (String code : codeArray) {
            assertThatThrownBy(() -> context.eval(code))
                    .as(code)
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("TypeError");
        }
    }

    @Test
    public void testGlobalFunctionsAreNotConstructors() {
        assertBooleanWithJavet("""
                (() => {
                  const isConstructor = (fn) => {
                    if (typeof fn !== 'function') {
                      return false;
                    }
                    try {
                      Reflect.construct(function () {}, [], fn);
                      return true;
                    } catch {
                      return false;
                    }
                  };
                
                  const functionPrototypeArguments = Object.getOwnPropertyDescriptor(Function.prototype, 'arguments');
                  const functionPrototypeCaller = Object.getOwnPropertyDescriptor(Function.prototype, 'caller');
                
                  const nonConstructors = [
                    parseInt,
                    parseFloat,
                    isNaN,
                    isFinite,
                    eval,
                    encodeURI,
                    decodeURI,
                    encodeURIComponent,
                    decodeURIComponent,
                    escape,
                    unescape,
                    Function.prototype,
                    Function.prototype.call,
                    Function.prototype.apply,
                    Function.prototype.bind,
                    Function.prototype.toString,
                    functionPrototypeArguments.get,
                    functionPrototypeArguments.set,
                    functionPrototypeCaller.get,
                    functionPrototypeCaller.set
                  ];
                
                  return nonConstructors.every((fn) => !isConstructor(fn));
                })();
                """);
    }

    @Test
    public void testIdentifierResolutionWithReferenceErrorAndTypeof() {
        assertThatThrownBy(() -> context.eval("missingIdentifier;"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("ReferenceError");
        assertStringWithJavet("typeof missingIdentifier");
        assertBooleanWithJavet("""
                (() => {
                  globalThis.__tmpMissing = undefined;
                  try {
                    return __tmpMissing === undefined;
                  } finally {
                    delete globalThis.__tmpMissing;
                  }
                })();
                """);
    }

    @Test
    public void testParseFloatEdgeCasesWithJavet() {
        assertBooleanWithJavet(
                "Object.is(parseFloat('1e'), 1)",
                "Object.is(parseFloat('1e+'), 1)",
                "Object.is(parseFloat('-0x1'), -0)",
                "Object.is(parseFloat('+.8xyz'), 0.8)",
                "Object.is(parseFloat('InfinityandMore'), Infinity)",
                "Object.is(parseFloat('-Infinity-and-more'), -Infinity)",
                "Number.isNaN(parseFloat('.'))",
                "Number.isNaN(parseFloat('+.'))",
                "Number.isNaN(parseFloat('e10'))");
    }

    @Test
    public void testParseIntEdgeCasesWithJavet() {
        assertBooleanWithJavet(
                "Object.is(parseInt('10', Infinity), 10)",
                "Object.is(parseInt('10', -Infinity), 10)",
                "Object.is(parseInt('10', 4294967298), 2)",
                "Object.is(parseInt('0x10', 17), 0)",
                "Object.is(parseInt('  -0xF'), -15)",
                "Object.is(parseInt('+0x10', 16), 16)",
                "Object.is(parseInt('123xyz', 10), 123)",
                "Number.isNaN(parseInt('１２３', 10))",
                "Number.isNaN(parseInt('', 10))",
                "Number.isNaN(parseInt('+', 10))");
        assertStringWithJavet(
                "(() => { try { parseInt('10', 1n); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { parseInt('10', Symbol()); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testUnescape() {
        assertStringWithJavet(
                // Test basic %XX sequences
                "unescape('%20')",
                "unescape('%21')",
                "unescape('%40')",
                // Test %uXXXX sequences for Unicode
                "unescape('%u4F60')",
                "unescape('%u597D')",
                "unescape('%u4F60%u597D')",
                // Test mixed sequences
                "unescape('Hello%20World')",
                "unescape('%21%23%24')",
                "unescape('test%3Avalue')",
                // Test strings without escape sequences
                "unescape('abc')",
                "unescape('123')",
                "unescape('')",
                // Test invalid sequences (should be treated as literals)
                "unescape('%')",
                "unescape('%Z')",
                "unescape('%uGGGG')",
                "JSON.stringify(Object.getOwnPropertyDescriptor(unescape, \"name\"))",
                "JSON.stringify(Object.getOwnPropertyDescriptor(unescape, \"length\"))");
    }

    @Test
    public void testUriEncodeDecodeEdgeCasesWithJavet() {
        assertStringWithJavet(
                "encodeURI('https://example.com/a b?x=1&y=#hash')",
                "encodeURI(';/?:@&=+$,#')",
                "encodeURIComponent(';/?:@&=+$,#')",
                "encodeURIComponent('a+b c')",
                "decodeURI('https://example.com/a%20b?x=%23#hash')",
                "decodeURIComponent('a%2Bb%20c%2F%3F%23')",
                "decodeURIComponent('%F0%9F%98%80')",
                "decodeURI('%E4%BD%A0%E5%A5%BD')");
        assertStringWithJavet(
                "(() => { try { decodeURI('%'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURI('%E0%A4%A'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURI('%C0%AF'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { decodeURIComponent('%ED%A0%80'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { encodeURI('\\uD800'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { encodeURI('\\uDC00'); return 'no error'; } catch (e) { return e.name; } })()",
                "(() => { try { encodeURI('\\uD800A'); return 'no error'; } catch (e) { return e.name; } })()");
    }

    @Test
    public void testVarBindingsRemainFunctionScopedAcrossLoopScopes() {
        assertBooleanWithJavet("""
                (() => {
                  function testFor() {
                    for (var a = 0; a < 1; a++) {
                    }
                    for (a = 1; a < 2; a++) {
                    }
                    return a === 2;
                  }
                  function testForIn() {
                    let last = "";
                    for (var k in { x: 1 }) {
                      last = k;
                    }
                    return k === last && k === "x";
                  }
                  function testForOf() {
                    for (var v of [42]) {
                    }
                    return v === 42;
                  }
                  function testGeneratorVarReuse() {
                    function* gen() {
                      for (var alpha = 0x41; alpha <= 0x41; alpha++) {
                        yield alpha;
                      }
                      for (alpha = 0x42; alpha <= 0x42; alpha++) {
                        yield alpha;
                      }
                    }
                    const values = Array.from(gen());
                    return values.length === 2 && values[0] === 0x41 && values[1] === 0x42;
                  }
                  return testFor() && testForIn() && testForOf() && testGeneratorVarReuse();
                })();
                """);
    }
}
