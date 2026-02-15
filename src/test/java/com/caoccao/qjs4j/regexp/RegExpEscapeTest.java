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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RegExpCompiler escape sequence handling (compileEscape, parseClassEscape).
 * Covers identity escapes, control escapes, hex/unicode escapes, octal escapes,
 * backreferences, and Unicode mode vs non-Unicode mode (Annex B) behavior.
 */
public class RegExpEscapeTest extends BaseJavetTest {
    private boolean matches(String pattern, String flags, String input) {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile(pattern, flags);
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec(input, 0);
        return result != null && result.matched();
    }

    // --- Identity Escapes: Unicode Mode ---

    @Test
    public void testBackreferenceInvalidIdentityEscape89() {
        // In non-unicode mode, \8 and \9 with no groups are identity escapes
        assertThat(matches("\\8", "", "8")).isTrue();
        assertThat(matches("\\9", "", "9")).isTrue();
    }

    @Test
    public void testBackreferenceInvalidOctalFallback() {
        // In non-unicode mode, invalid backreferences with digits 1-7 become octal escapes
        // \1 with no groups = octal 1 = U+0001
        assertThat(matches("\\1", "", "\u0001")).isTrue();
        // \7 with no groups = octal 7 = U+0007
        assertThat(matches("\\7", "", "\u0007")).isTrue();
    }

    @Test
    public void testBackreferenceInvalidUnicodeModeFails() {
        // In unicode mode, invalid backreferences throw
        RegExpCompiler compiler = new RegExpCompiler();
        assertThatThrownBy(() -> compiler.compile("\\1", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
    }

    // --- Identity Escapes: Non-Unicode Mode (Annex B) ---

    @Test
    public void testBackreferenceValid() {
        assertThat(matches("(a)\\1", "", "aa")).isTrue();
        assertThat(matches("(a)\\1", "", "ab")).isFalse();
        assertThat(matches("(a)(b)\\2", "", "abb")).isTrue();
    }

    @Test
    public void testBasicEscapeSequences() {
        assertThat(matches("\\n", "", "\n")).isTrue();
        assertThat(matches("\\r", "", "\r")).isTrue();
        assertThat(matches("\\t", "", "\t")).isTrue();
        assertThat(matches("\\f", "", "\f")).isTrue();
        assertThat(matches("\\v", "", "\u000B")).isTrue();
    }

    // --- Control Escape \c ---

    @Test
    public void testCharacterClassEscapes() {
        assertThat(matches("\\d", "", "5")).isTrue();
        assertThat(matches("\\d", "", "a")).isFalse();
        assertThat(matches("\\D", "", "a")).isTrue();
        assertThat(matches("\\D", "", "5")).isFalse();
        assertThat(matches("\\w", "", "a")).isTrue();
        assertThat(matches("\\w", "", "_")).isTrue();
        assertThat(matches("\\w", "", "!")).isFalse();
        assertThat(matches("\\W", "", "!")).isTrue();
        assertThat(matches("\\W", "", "a")).isFalse();
        assertThat(matches("\\s", "", " ")).isTrue();
        assertThat(matches("\\s", "", "a")).isFalse();
        assertThat(matches("\\S", "", "a")).isTrue();
        assertThat(matches("\\S", "", " ")).isFalse();
    }

    @Test
    public void testControlEscapeDigitsInCharacterClass() {
        // Annex B.1.4: \c inside character class accepts digits and underscore
        // \c0 = '0' % 32 = 48 % 32 = 16 = U+0010
        assertThat(matches("[\\c0]", "", "\u0010")).isTrue();
        // \c_ = '_' % 32 = 95 % 32 = 31 = U+001F
        assertThat(matches("[\\c_]", "", "\u001F")).isTrue();
        // \c9 = '9' % 32 = 57 % 32 = 25 = U+0019
        assertThat(matches("[\\c9]", "", "\u0019")).isTrue();
    }

    @Test
    public void testControlEscapeFallbackNonUnicodeMode() {
        // In non-unicode mode, \c without valid letter = literal '\' then 'c' re-parsed
        // \c? should match literal '\' followed by 'c' followed by optional nothing
        // Actually: \c (not followed by letter) → literal '\', then 'c' is re-parsed
        assertThat(matches("\\c!", "", "\\c!")).isTrue();
        // The pattern is: literal '\' (from \c fallback), then '!' as literal
        // Wait - after backing up, 'c' is re-parsed, then '!' is parsed separately
        // So the pattern matches: \, c, !
        assertThat(matches("\\c!", "", "\\c!")).isTrue();
    }

    @Test
    public void testControlEscapeInvalidInUnicodeMode() {
        // \c without valid letter should throw in unicode mode
        RegExpCompiler compiler = new RegExpCompiler();
        assertThatThrownBy(() -> compiler.compile("\\c0", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
        assertThatThrownBy(() -> compiler.compile("\\c_", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
        assertThatThrownBy(() -> compiler.compile("\\c!", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
    }

    @Test
    public void testControlEscapeValidLetters() {
        // \cA-\cZ and \ca-\cz are valid control escapes
        // \cA = 'A' % 32 = 1 (U+0001)
        assertThat(matches("\\cA", "", "\u0001")).isTrue();
        assertThat(matches("\\ca", "", "\u0001")).isTrue();
        // \cZ = 'Z' % 32 = 26 (U+001A)
        assertThat(matches("\\cZ", "", "\u001A")).isTrue();
        assertThat(matches("\\cz", "", "\u001A")).isTrue();
        // \cM = 'M' % 32 = 13 = \r
        assertThat(matches("\\cM", "", "\r")).isTrue();
        assertThat(matches("\\cm", "", "\r")).isTrue();
        // \cJ = 'J' % 32 = 10 = \n
        assertThat(matches("\\cJ", "", "\n")).isTrue();
        assertThat(matches("\\cj", "", "\n")).isTrue();
    }

    // --- Null / Legacy Octal Escape \0 ---

    @Test
    public void testControlEscapeValidLettersUnicodeMode() {
        // \cA-\cZ should work in unicode mode too
        assertThat(matches("\\cA", "u", "\u0001")).isTrue();
        assertThat(matches("\\cZ", "u", "\u001A")).isTrue();
    }

    @Test
    public void testControlEscapeViaJS() {
        assertBooleanWithJavet("""
                /\\cA/.test('\\x01') && /\\cZ/.test('\\x1A')""");
    }

    @Test
    public void testHexEscapeInvalidNonUnicodeMode() {
        // Invalid \x is identity escape in non-unicode mode (matches literal 'x')
        assertThat(matches("\\xGG", "", "xGG")).isTrue();
        assertThat(matches("\\x", "", "x")).isTrue();
    }

    // --- Hex Escape \x ---

    @Test
    public void testHexEscapeInvalidUnicodeModeFails() {
        RegExpCompiler compiler = new RegExpCompiler();
        assertThatThrownBy(() -> compiler.compile("\\xGG", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
        assertThatThrownBy(() -> compiler.compile("\\x", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
    }

    @Test
    public void testHexEscapeValid() {
        assertThat(matches("\\x41", "", "A")).isTrue();
        assertThat(matches("\\x61", "", "a")).isTrue();
        assertThat(matches("\\x00", "", "\u0000")).isTrue();
        assertThat(matches("\\xFF", "", "\u00FF")).isTrue();
        assertThat(matches("\\xff", "", "\u00FF")).isTrue();
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
        assertThat(matches("\\a", "", "a")).isTrue();
        assertThat(matches("\\e", "", "e")).isTrue();
        assertThat(matches("\\g", "", "g")).isTrue();
        assertThat(matches("\\i", "", "i")).isTrue();
        assertThat(matches("\\z", "", "z")).isTrue();
        assertThat(matches("\\-", "", "-")).isTrue();
        assertThat(matches("\\!", "", "!")).isTrue();
        assertThat(matches("\\@", "", "@")).isTrue();
        assertThat(matches("\\#", "", "#")).isTrue();
    }

    @Test
    public void testIdentityEscapeDashUnicodeModeFails() {
        // \- should throw in unicode mode outside character class
        RegExpCompiler compiler = new RegExpCompiler();
        assertThatThrownBy(() -> compiler.compile("\\-", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
    }

    @Test
    public void testIdentityEscapeNonSyntaxCharsUnicodeModeFails() {
        // Non-syntax characters should throw in unicode mode
        RegExpCompiler compiler = new RegExpCompiler();
        assertThatThrownBy(() -> compiler.compile("\\a", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
        assertThatThrownBy(() -> compiler.compile("\\e", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
        assertThatThrownBy(() -> compiler.compile("\\g", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
        assertThatThrownBy(() -> compiler.compile("\\i", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
        assertThatThrownBy(() -> compiler.compile("\\z", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("invalid escape sequence");
    }

    @Test
    public void testIdentityEscapeSyntaxCharsNonUnicodeMode() {
        // Syntax chars should work in non-unicode mode too
        assertThat(matches("\\^", "", "^")).isTrue();
        assertThat(matches("\\$", "", "$")).isTrue();
        assertThat(matches("\\.", "", ".")).isTrue();
        assertThat(matches("\\*", "", "*")).isTrue();
    }

    // --- Backreferences ---

    @Test
    public void testIdentityEscapeSyntaxCharsUnicodeMode() {
        // All syntax characters should be valid identity escapes in unicode mode
        // Syntax chars: ^ $ \ . * + ? ( ) [ ] { } | /
        assertThat(matches("\\^", "u", "^")).isTrue();
        assertThat(matches("\\$", "u", "$")).isTrue();
        assertThat(matches("\\\\", "u", "\\")).isTrue();
        assertThat(matches("\\.", "u", ".")).isTrue();
        assertThat(matches("\\*", "u", "*")).isTrue();
        assertThat(matches("\\+", "u", "+")).isTrue();
        assertThat(matches("\\?", "u", "?")).isTrue();
        assertThat(matches("\\|", "u", "|")).isTrue();
        assertThat(matches("\\/", "u", "/")).isTrue();
        // Brackets: need to be inside a pattern context where they're not special
        assertThat(matches("\\(\\)", "u", "()")).isTrue();
        assertThat(matches("\\[\\]", "u", "[]")).isTrue();
        assertThat(matches("\\{\\}", "u", "{}")).isTrue();
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
        assertThat(matches("\\0", "", "\u0000")).isTrue();
        // \00 = octal 00 = U+0000
        assertThat(matches("\\00", "", "\u0000")).isTrue();
        // \07 = octal 07 = U+0007
        assertThat(matches("\\07", "", "\u0007")).isTrue();
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
        RegExpCompiler compiler = new RegExpCompiler();
        assertThatThrownBy(() -> compiler.compile("\\00", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
        assertThatThrownBy(() -> compiler.compile("\\01", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
    }

    // --- End-to-end tests via JS engine ---

    @Test
    public void testNullEscapeUnicodeMode() {
        // \0 in unicode mode = null character (U+0000)
        assertThat(matches("\\0", "u", "\u0000")).isTrue();
        assertThat(matches("\\0", "u", "0")).isFalse();
    }

    @Test
    public void testUnicodeEscapeBracedUnicodeMode() {
        assertThat(matches("\\u{41}", "u", "A")).isTrue();
        assertThat(matches("\\u{0041}", "u", "A")).isTrue();
        assertThat(matches("\\u{1F600}", "u", "\uD83D\uDE00")).isTrue(); // emoji
    }

    @Test
    public void testUnicodeEscapeInvalidNonUnicodeMode() {
        // Invalid backslash-u is identity escape in non-unicode mode
        assertThat(matches("\\uGGGG", "", "uGGGG")).isTrue();
    }

    @Test
    public void testUnicodeEscapeInvalidUnicodeModeFails() {
        RegExpCompiler compiler = new RegExpCompiler();
        assertThatThrownBy(() -> compiler.compile("\\uGGGG", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
    }

    @Test
    public void testUnicodeEscapeValid() {
        assertThat(matches("\\u0041", "", "A")).isTrue();
        assertThat(matches("\\u0061", "", "a")).isTrue();
        assertThat(matches("\\u03B1", "", "\u03B1")).isTrue(); // alpha
    }
}
