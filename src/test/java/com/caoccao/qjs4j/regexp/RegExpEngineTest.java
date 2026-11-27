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

import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class RegExpEngineTest extends RegExpTestBase {
    private RegExpEngine.MatchResult assertMatch(RegExpEngine engine, String input, int start, String expected) {
        RegExpEngine.MatchResult result = engine.exec(input, start);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo(expected);
        return result;
    }

    @Test
    public void testCaseInsensitiveMatching() {
        RegExpEngine engine = compilePattern("abc", "i");
        assertMatch(engine, "ABC", 0, "ABC");

        assertMatch(engine, "aBc", 0, "aBc");
    }

    @Test
    public void testDotAllMode() {
        RegExpEngine engine = compilePattern("a.b", "s");
        assertMatch(engine, "a\nb", 0, "a\nb");
    }

    @Test
    public void testDotWithoutDotAll() {
        RegExpEngine engine = compilePattern("a.b", "");
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertThat(result).isNull(); // Should not match because . doesn't match \n
    }

    @Test
    public void testEdgeCases() {
        // Empty pattern matches at every position
        RegExpEngine engine = compilePattern("", "");
        assertMatch(engine, "", 0, "");

        // Start index out of bounds
        RegExpEngine.MatchResult result = engine.exec("abc", -1);
        assertThat(result).isNull();
        result = engine.exec("abc", 4);
        assertThat(result).isNull();
    }

    @Test
    public void testEscapedCharacters() {
        RegExpEngine engine = compilePattern("a\\nb", "");
        assertMatch(engine, "a\nb", 0, "a\nb");

        engine = compilePattern("a\\tb", "");
        assertMatch(engine, "a\tb", 0, "a\tb");
    }

    @Test
    public void testExecNoMatch() {
        RegExpEngine engine = compilePattern("abc", "");
        RegExpEngine.MatchResult result = engine.exec("defgh", 0);
        assertThat(result).isNull();
    }

    @Test
    public void testExecSimpleMatch() {
        RegExpEngine engine = compilePattern("abc", "");
        RegExpEngine.MatchResult result = assertMatch(engine, "xxabcxx", 0, "abc");
        assertThat(result.startIndex()).isEqualTo(2);
        assertThat(result.endIndex()).isEqualTo(5);
    }

    @Test
    public void testExecWithGroups() {
        RegExpEngine engine = compilePattern("a(bc)d", "");
        RegExpEngine.MatchResult result = assertMatch(engine, "xabcd", 0, "abcd");
        assertThat(result.getCapture(1)).isEqualTo("bc");
    }

    @Test
    public void testFlagsCombination() {
        RegExpEngine engine = compilePattern("a.b", "is");
        assertMatch(engine, "A\nB", 0, "A\nB");
    }

    @Test
    public void testLineAnchors() {
        RegExpEngine engine = compilePattern("^abc", "");
        RegExpEngine.MatchResult result = engine.exec("abc", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();

        result = engine.exec("xabc", 0);
        assertThat(result).isNull(); // Should not match at position 0

        engine = compilePattern("abc$", "");
        result = engine.exec("abc", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();

        result = engine.exec("abcx", 0);
        assertThat(result).isNull(); // Should not match at end
    }

    @Test
    public void testLookbehindAssertionsBasic() {
        assertMatches("(?<=a)b", "", "ab");
        assertDoesNotMatch("(?<=a)b", "", "cb");
        assertMatches("(?<!a)b", "", "cb");
        assertDoesNotMatch("(?<!a)b", "", "ab");
        assertMatches("(?<!a)b", "", "b");
    }

    @Test
    public void testLookbehindAssertionsCaptures() {
        RegExpEngine engine = compilePattern("(?<=(a))b", "");
        RegExpEngine.MatchResult result = assertMatch(engine, "ab", 0, "b");
        assertThat(result.getCapture(1)).isEqualTo("a");

        assertMatches("(?<=(a))\\1", "", "aa");
    }

    @Test
    public void testLookbehindAssertionsSyntaxAndQuantifierRules() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertThatThrownBy(() -> compiler.compile("(?<=a", "")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("Unterminated group");
        assertThatThrownBy(() -> compiler.compile("(?<!a", "")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("Unterminated group");
        assertThatThrownBy(() -> compiler.compile("(?<=a)+b", ""))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class).hasMessageContaining("Nothing to repeat");

        // QuickJS allows quantified lookahead in non-unicode mode (Annex B).
        assertThatCode(() -> compiler.compile("(?=a)+b", "")).doesNotThrowAnyException();
    }

    @Test
    public void testLookbehindAssertionsVariableLength() {
        assertMatches("(?<=a*)b", "", "aaab");
        assertMatches("(?<=a{2,3})b", "", "aaab");
        assertDoesNotMatch("(?<=a{2,3})b", "", "ab");
        assertMatches("(?<=a)b+", "", "abbb");
    }

    @Test
    public void testMultilineMode() {
        RegExpEngine engine = compilePattern("^abc$", "m");
        assertMatch(engine, "abc\ndef", 0, "abc");

        RegExpEngine.MatchResult result = engine.exec("abc\ndef", 4);
        assertThat(result).isNull(); // Should not match "def" with "^abc$"

        // Test ^ matching at line start
        engine = compilePattern("^def", "m");
        assertMatch(engine, "abc\ndef", 4, "def");
    }

    @Test
    public void testMultipleCaptureGroups() {
        RegExpEngine engine = compilePattern("(a)(b)(c)", "");
        RegExpEngine.MatchResult result = assertMatch(engine, "abc", 0, "abc");
        assertThat(result.getCapture(1)).isEqualTo("a");
        assertThat(result.getCapture(2)).isEqualTo("b");
        assertThat(result.getCapture(3)).isEqualTo("c");
    }

    @Test
    public void testNamedCaptureGroupsBackReference() {
        assertBooleanWithJavet("""
                /(?<x>a)\\k<x>/.test('aa') && !/(?<x>a)\\k<x>/.test('ab')""");
    }

    @Test
    public void testNamedCaptureGroupsDuplicateNamesAcrossAlternatives() {
        assertObjectWithJavet(
                """
                        const dateRegex = /(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})|(?<month>\\d{2})\\/(?<day>\\d{2})\\/(?<year>\\d{4})/;
                        const match1 = '2025-03-15'.match(dateRegex);
                        const match2 = '03/15/2025'.match(dateRegex);
                        [match1.groups, match2.groups]""");
    }

    @Test
    public void testNamedCaptureGroupsDuplicateNameThrows() {
        assertErrorWithJavet("new RegExp('(?<x>a)(?<x>b)')");
    }

    @Test
    public void testNamedCaptureGroupsExecGroups() {
        assertBooleanWithJavet("""
                const m = /(?<year>\\d+)-(?<month>\\d+)/.exec('2024-01');
                m[0] === '2024-01'
                    && m[1] === '2024'
                    && m[2] === '01'
                    && m.groups.year === '2024'
                    && m.groups.month === '01'
                    && Object.getPrototypeOf(m.groups) === null""");
    }

    @Test
    public void testNamedCaptureGroupsForwardBackReference() {
        assertBooleanWithJavet("/\\k<x>(?<x>a)/.test('aa')");
    }

    @Test
    public void testNamedCaptureGroupsMatchAllGroups() {
        assertBooleanWithJavet("""
                const values = [];
                for (const m of 'a1 a2'.matchAll(/a(?<digit>\\d)/g)) {
                    values.push(m.groups.digit);
                }
                values.join(',') === '1,2'""");
    }

    @Test
    public void testNamedCaptureGroupsNotDefinedThrows() {
        assertThatThrownBy(() -> resetContext().eval("new RegExp('\\\\k<missing>(?<x>a)')"))
                .isInstanceOf(JSException.class).hasMessageContaining("Invalid named capture referenced");
    }

    @Test
    public void testNamedCaptureGroupsOptionalUndefined() {
        assertBooleanWithJavet("""
                const m = /(?<x>a)?b/.exec('b');
                m.groups.x === undefined""");
    }

    @Test
    public void testNamedCaptureGroupsReplaceAndReplaceAll() {
        assertBooleanWithJavet("""
                'ab'.replace(/(?<x>a)/, '$<x>-') === 'a-b'
                    && 'a1 a2'.replaceAll(/a(?<digit>\\d)/g, '[$<digit>]') === '[1] [2]'""");
    }

    @Test
    public void testNamedCaptureGroupsWithoutNamesHaveUndefinedGroups() {
        assertBooleanWithJavet("""
                const m = /(a)/.exec('a');
                m.groups === undefined""");
    }

    @Test
    public void testNestedCaptureGroups() {
        RegExpEngine engine = compilePattern("((a)b)", "");
        RegExpEngine.MatchResult result = assertMatch(engine, "ab", 0, "ab");
        assertThat(result.getCapture(1)).isEqualTo("ab");
        assertThat(result.getCapture(2)).isEqualTo("a");
    }

    @Test
    public void testOptionalGroupWithAlternationInStarLoop() {
        // Basic: alternation without star, without optional
        assertMatches("^(a|b)$", "", "a", "basic alt a");
        assertMatches("^(a|b)$", "", "b", "basic alt b");

        // Optional alternation (no star)
        assertMatches("^(a|b)?$", "", "", "opt alt empty");
        assertMatches("^(a|b)?$", "", "a", "opt alt a");

        // Star without alternation
        assertMatches("^(x)?$", "", "", "opt x empty");
        assertMatches("^(xa?)*$", "", "x", "star xa? x");
        assertMatches("^(xa?)*$", "", "xa", "star xa? xa");

        // Non-capturing optional alternation in star - simplest case
        assertMatches("^(?:a|b)*$", "", "a", "nc star alt a");
        assertMatches("^(?:a|b)+$", "", "a", "nc plus alt a");

        // Optional alternation - no star
        assertMatches("^x(?:a|b)?$", "", "x", "nc x opt alt");
        assertMatches("^x(?:a|b)?$", "", "xa", "nc xa opt alt");

        // Star of simple optional
        assertMatches("^(?:xa?)*$", "", "x", "nc star xa?");

        // Star of alternation without optional
        assertMatches("^(?:x(?:a|b))*$", "", "xa", "nc star x-alt xa");

        // Now the previously-failing combo: optional alternation in star
        assertMatches("^(?:x(?:a|b)?)*$", "", "x", "nc star x opt-alt x");
        assertMatches("^(?:x(?:a|b)?)*$", "", "xa", "nc star x opt-alt xa");

        // Capturing optional alternation in star
        assertMatches("^(x(a|b)?)*$", "", "x", "star x matches");
        assertMatches("^(x(a|b)?)*$", "", "xa", "star xa matches");
        assertMatches("^(x(a|b)?)*$", "", "xaxb", "star xaxb matches");

        // Original failing patterns from the bug report
        assertMatches("^([a-z]{2})(-t-([a-z]{2})(-(ab|cd))?)*$", "i", "ab-t-cd", "pattern with alternation in star");
        assertMatches("^([a-z]{2})(-t-([a-z]{2})(-(ab|cd))?)*$", "i", "ab-t-cd-ab",
                "pattern with alternation in star, matching alternation");
    }

    @Test
    public void testStartIndexInMiddle() {
        RegExpEngine engine = compilePattern("abc", "");
        RegExpEngine.MatchResult result = assertMatch(engine, "xxxabc", 3, "abc");
        assertThat(result.startIndex()).isEqualTo(3);
        assertThat(result.endIndex()).isEqualTo(6);
    }

    @Test
    public void testUnicodeCharacters() {
        RegExpEngine engine = compilePattern("😀🌟🚀", "");
        assertMatch(engine, "😀🌟🚀", 0, "😀🌟🚀");
        assertThat(engine.test("😀🌟🚀")).isTrue();
    }

    @Test
    public void testUnicodePropertyEscapesBasics() {
        assertMatches("\\p{L}", "u", "A");
        assertMatches("\\p{L}", "u", "π");
        assertDoesNotMatch("\\p{L}", "u", "1");
        assertMatches("\\P{L}", "u", "1");
        assertDoesNotMatch("\\P{L}", "u", "π");
        assertMatches("\\p{Any}", "u", "😀");
        assertMatches("\\p{Assigned}", "u", "A");
    }

    @Test
    public void testUnicodePropertyEscapesInCharacterClass() {
        assertMatches("^[\\p{L}\\p{Nd}]+$", "u", "Aπ9");
        assertMatches("^[\\P{L}]+$", "u", "123_");
        assertDoesNotMatch("^[\\P{L}]+$", "u", "123A");
    }

    @Test
    public void testUnicodePropertyEscapesPropertyForms() {
        assertMatches("\\p{gc=Lu}", "u", "A");
        assertDoesNotMatch("\\p{gc=Lu}", "u", "a");
        assertMatches("\\p{General_Category=Uppercase_Letter}", "u", "A");
        assertMatches("\\p{Script=Greek}", "u", "Ω");
        assertDoesNotMatch("\\p{Script=Greek}", "u", "A");
        assertMatches("\\p{sc=Grek}", "u", "β");
        assertMatches("\\p{Script_Extensions=Greek}", "u", "π");
        assertMatches("\\p{ASCII}", "u", "A");
        assertDoesNotMatch("\\p{ASCII}", "u", "π");
        assertMatches("\\p{White_Space}", "u", "\u00A0");
        assertMatches("\\p{ID_Start}", "u", "A");
        assertDoesNotMatch("\\p{ID_Start}", "u", "1");
        assertMatches("\\p{ID_Continue}", "u", "1");
    }

    @Test
    public void testUnicodePropertyEscapesRequireUnicodeMode() {
        assertMatches("\\p{L}", "", "p{L}");
        assertDoesNotMatch("\\p{L}", "", "A");
        assertMatches("[\\p{L}]", "", "{");
        assertMatches("\\P{L}", "", "P{L}");
    }

    @Test
    public void testUnicodePropertyEscapesSyntaxErrors() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());

        assertThatThrownBy(() -> compiler.compile("\\p", "u")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("expecting '{' after \\p");
        assertThatThrownBy(() -> compiler.compile("\\P", "u")).isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("expecting '{' after \\p");
        assertThatThrownBy(() -> compiler.compile("\\p{L", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class).hasMessageContaining("expecting '}'");
        assertThatThrownBy(() -> compiler.compile("\\p{Greek}", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("unknown unicode property name");
        assertThatThrownBy(() -> compiler.compile("\\p{sc=greek}", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("unknown unicode script");
        assertThatThrownBy(() -> compiler.compile("\\p{gc=lu}", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("unknown unicode general category");
        assertThatThrownBy(() -> compiler.compile("\\p{white_space}", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("unknown unicode property name");
        assertThatThrownBy(() -> compiler.compile("\\p{SC=Greek}", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("unknown unicode property name");
    }

    @Test
    public void testUnorderedCharacterClassRanges() {
        // Source order is deliberately unrelated to code point order. The compiler normalizes
        // non-case-folded ranges so the executor can safely use binary search.
        assertBooleanWithJavet("""
                const positive = /^[zb-dax]+$/;
                const negative = /^[^zb-dax]+$/;
                positive.test('zabcdx')
                    && !positive.test('e')
                    && negative.test('efwy')
                    && !negative.test('efay')""");
    }
}
