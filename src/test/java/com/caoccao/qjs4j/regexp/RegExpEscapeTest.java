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

package com.caoccao.qjs4j.regexp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RegExpCompiler escape sequence handling (compileEscape, parseClassEscape). Covers identity escapes, control
 * escapes, hex/unicode escapes, octal escapes, backreferences, and Unicode mode vs non-Unicode mode (Annex B) behavior.
 */
public class RegExpEscapeTest extends RegExpTestBase {
    // --- Identity Escapes: Unicode Mode ---

    @Test
    public void testBackreferenceInvalidIdentityEscape89() {
        // In non-unicode mode, \8 and \9 with no groups are identity escapes
        assertMatches("\\8", "", "8");
        assertMatches("\\9", "", "9");
    }

    @Test
    public void testBackreferenceInvalidOctalFallback() {
        // In non-unicode mode, invalid backreferences with digits 1-7 become octal escapes
        // \1 with no groups = octal 1 = U+0001
        assertMatches("\\1", "", "\u0001");
        // \7 with no groups = octal 7 = U+0007
        assertMatches("\\7", "", "\u0007");
    }

    @Test
    public void testBackreferenceInvalidUnicodeModeFails() {
        // In unicode mode, invalid backreferences throw
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertInvalidPattern(compiler, "\\1", "u");
    }

    // --- Identity Escapes: Non-Unicode Mode (Annex B) ---

    @Test
    public void testBackreferenceValid() {
        assertMatches("(a)\\1", "", "aa");
        assertDoesNotMatch("(a)\\1", "", "ab");
        assertMatches("(a)(b)\\2", "", "abb");
    }

    @Test
    public void testBasicEscapeSequences() {
        assertMatches("\\n", "", "\n");
        assertMatches("\\r", "", "\r");
        assertMatches("\\t", "", "\t");
        assertMatches("\\f", "", "\f");
        assertMatches("\\v", "", "\u000B");
    }

    // --- Control Escape \c ---

    @Test
    public void testCharacterClassEscapes() {
        assertMatches("\\d", "", "5");
        assertDoesNotMatch("\\d", "", "a");
        assertMatches("\\D", "", "a");
        assertDoesNotMatch("\\D", "", "5");
        assertMatches("\\w", "", "a");
        assertMatches("\\w", "", "_");
        assertDoesNotMatch("\\w", "", "!");
        assertMatches("\\W", "", "!");
        assertDoesNotMatch("\\W", "", "a");
        assertMatches("\\s", "", " ");
        assertDoesNotMatch("\\s", "", "a");
        assertMatches("\\S", "", "a");
        assertDoesNotMatch("\\S", "", " ");
    }

    @Test
    public void testControlEscapeDigitsInCharacterClass() {
        // Annex B.1.4: \c inside character class accepts digits and underscore
        // \c0 = '0' % 32 = 48 % 32 = 16 = U+0010
        assertMatches("[\\c0]", "", "\u0010");
        // \c_ = '_' % 32 = 95 % 32 = 31 = U+001F
        assertMatches("[\\c_]", "", "\u001F");
        // \c9 = '9' % 32 = 57 % 32 = 25 = U+0019
        assertMatches("[\\c9]", "", "\u0019");
    }

    @Test
    public void testControlEscapeFallbackNonUnicodeMode() {
        // In non-unicode mode, \c without valid letter = literal '\' then 'c' re-parsed
        // \c? should match literal '\' followed by 'c' followed by optional nothing
        // Actually: \c (not followed by letter) → literal '\', then 'c' is re-parsed
        assertMatches("\\c!", "", "\\c!");
        // The pattern is: literal '\' (from \c fallback), then '!' as literal
        // Wait - after backing up, 'c' is re-parsed, then '!' is parsed separately
        // So the pattern matches: \, c, !
        assertMatches("\\c!", "", "\\c!");
    }

    @Test
    public void testControlEscapeInvalidInUnicodeMode() {
        // \c without valid letter should throw in unicode mode
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertInvalidPattern(compiler, "\\c0", "u");
        assertInvalidPattern(compiler, "\\c_", "u");
        assertInvalidPattern(compiler, "\\c!", "u");
    }

    @Test
    public void testControlEscapeValidLetters() {
        // \cA-\cZ and \ca-\cz are valid control escapes
        // \cA = 'A' % 32 = 1 (U+0001)
        assertMatches("\\cA", "", "\u0001");
        assertMatches("\\ca", "", "\u0001");
        // \cZ = 'Z' % 32 = 26 (U+001A)
        assertMatches("\\cZ", "", "\u001A");
        assertMatches("\\cz", "", "\u001A");
        // \cM = 'M' % 32 = 13 = \r
        assertMatches("\\cM", "", "\r");
        assertMatches("\\cm", "", "\r");
        // \cJ = 'J' % 32 = 10 = \n
        assertMatches("\\cJ", "", "\n");
        assertMatches("\\cj", "", "\n");
    }

    // --- Null / Legacy Octal Escape \0 ---

    @Test
    public void testControlEscapeValidLettersUnicodeMode() {
        // \cA-\cZ should work in unicode mode too
        assertMatches("\\cA", "u", "\u0001");
        assertMatches("\\cZ", "u", "\u001A");
    }

    @Test
    public void testControlEscapeViaJS() {
        assertBooleanWithJavet("""
                /\\cA/.test('\\x01') && /\\cZ/.test('\\x1A')""");
    }

    @Test
    public void testHexEscapeInvalidNonUnicodeMode() {
        // Invalid \x is identity escape in non-unicode mode (matches literal 'x')
        assertMatches("\\xGG", "", "xGG");
        assertMatches("\\x", "", "x");
    }

    // --- Hex Escape \x ---

    @Test
    public void testHexEscapeInvalidUnicodeModeFails() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertInvalidPattern(compiler, "\\xGG", "u");
        assertInvalidPattern(compiler, "\\x", "u");
    }

    @Test
    public void testHexEscapeValid() {
        assertMatches("\\x41", "", "A");
        assertMatches("\\x61", "", "a");
        assertMatches("\\x00", "", "\u0000");
        assertMatches("\\xFF", "", "\u00FF");
        assertMatches("\\xff", "", "\u00FF");
    }

    @Test
    public void testHexEscapeViaJS() {
        assertBooleanWithJavet("""
                /\\x41/.test('A') && /\\xFF/.test('\\xFF')""");
    }

    // --- Unicode Escape ---

    @Test
    public void testIdentityEscapeAnyCharNonUnicodeMode() {
        // In non-unicode mode, any character can be identity-escaped (Annex B)
        assertMatches("\\a", "", "a");
        assertMatches("\\e", "", "e");
        assertMatches("\\g", "", "g");
        assertMatches("\\i", "", "i");
        assertMatches("\\z", "", "z");
        assertMatches("\\-", "", "-");
        assertMatches("\\!", "", "!");
        assertMatches("\\@", "", "@");
        assertMatches("\\#", "", "#");
    }

    @Test
    public void testIdentityEscapeDashUnicodeModeFails() {
        // \- should throw in unicode mode outside character class
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertThatThrownBy(() -> compiler.compile("\\-", "u")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
    }

    @Test
    public void testIdentityEscapeNonSyntaxCharsUnicodeModeFails() {
        // Non-syntax characters should throw in unicode mode
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertThatThrownBy(() -> compiler.compile("\\a", "u")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
        assertThatThrownBy(() -> compiler.compile("\\e", "u")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
        assertThatThrownBy(() -> compiler.compile("\\g", "u")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
        assertThatThrownBy(() -> compiler.compile("\\i", "u")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
        assertThatThrownBy(() -> compiler.compile("\\z", "u")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
    }

    @Test
    public void testIdentityEscapeSyntaxCharsNonUnicodeMode() {
        // Syntax chars should work in non-unicode mode too
        assertMatches("\\^", "", "^");
        assertMatches("\\$", "", "$");
        assertMatches("\\.", "", ".");
        assertMatches("\\*", "", "*");
    }

    // --- Backreferences ---

    @Test
    public void testIdentityEscapeSyntaxCharsUnicodeMode() {
        // All syntax characters should be valid identity escapes in unicode mode
        // Syntax chars: ^ $ \ . * + ? ( ) [ ] { } | /
        assertMatches("\\^", "u", "^");
        assertMatches("\\$", "u", "$");
        assertMatches("\\\\", "u", "\\");
        assertMatches("\\.", "u", ".");
        assertMatches("\\*", "u", "*");
        assertMatches("\\+", "u", "+");
        assertMatches("\\?", "u", "?");
        assertMatches("\\|", "u", "|");
        assertMatches("\\/", "u", "/");
        // Brackets: need to be inside a pattern context where they're not special
        assertMatches("\\(\\)", "u", "()");
        assertMatches("\\[\\]", "u", "[]");
        assertMatches("\\{\\}", "u", "{}");
    }

    @Test
    public void testIdentityEscapeViaJSNonUnicodeMode() {
        // In non-unicode mode, \\a is a valid identity escape matching 'a'
        assertBooleanWithJavet("""
                /\\a/.test('a')""");
    }

    @Test
    public void testIdentityEscapeViaJSUnicodeMode() {
        // Test that unicode mode properly rejects invalid identity escapes
        // through the JS engine (RegExp constructor)
        assertBooleanWithJavet("""
                try { new RegExp('\\\\a', 'u'); false; } catch(e) { true; }""");
    }

    @Test
    public void testLegacyOctalEscapeNonUnicodeMode() {
        // \0 = null char
        assertMatches("\\0", "", "\u0000");
        // \00 = octal 00 = U+0000
        assertMatches("\\00", "", "\u0000");
        // \07 = octal 07 = U+0007
        assertMatches("\\07", "", "\u0007");
    }

    // --- Basic Escape Sequences ---

    @Test
    public void testLegacyOctalViaJS() {
        assertBooleanWithJavet("""
                /\\0/.test('\\x00') && /\\00/.test('\\x00')""");
    }

    // --- Character Class Escapes ---

    @Test
    public void testNullEscapeFollowedByDigitUnicodeModeFails() {
        // \0 followed by digit should error in unicode mode
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertInvalidPattern(compiler, "\\00", "u");
        assertInvalidPattern(compiler, "\\01", "u");
    }

    // --- End-to-end tests via JS engine ---

    @Test
    public void testNullEscapeUnicodeMode() {
        // \0 in unicode mode = null character (U+0000)
        assertMatches("\\0", "u", "\u0000");
        assertDoesNotMatch("\\0", "u", "0");
    }

    @Test
    public void testUnicodeEscapeBracedUnicodeMode() {
        assertMatches("\\u{41}", "u", "A");
        assertMatches("\\u{0041}", "u", "A");
        assertMatches("\\u{1F600}", "u", "\uD83D\uDE00"); // emoji
    }

    @Test
    public void testUnicodeEscapeInvalidNonUnicodeMode() {
        // Invalid backslash-u is identity escape in non-unicode mode
        assertMatches("\\uGGGG", "", "uGGGG");
    }

    @Test
    public void testUnicodeEscapeInvalidUnicodeModeFails() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertInvalidPattern(compiler, "\\uGGGG", "u");
    }

    @Test
    public void testUnicodeEscapeValid() {
        assertMatches("\\u0041", "", "A");
        assertMatches("\\u0061", "", "a");
        assertMatches("\\u03B1", "", "\u03B1"); // alpha
    }
}
