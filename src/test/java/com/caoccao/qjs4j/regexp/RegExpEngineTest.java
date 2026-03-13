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
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class RegExpEngineTest extends BaseJavetTest {
    private boolean matches(String pattern, String flags, String input) {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile(pattern, flags);
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec(input, 0);
        return result != null && result.matched();
    }

    @Test
    public void testCaseInsensitiveMatching() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("abc", "i");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("ABC", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("ABC");

        result = engine.exec("aBc", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("aBc");
    }

    @Test
    public void testDotAllMode() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("a.b", "s");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("a\nb");
    }

    @Test
    public void testDotWithoutDotAll() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("a.b", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertThat(result).isNull(); // Should not match because . doesn't match \n
    }

    @Test
    public void testEdgeCases() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        // Empty pattern matches at every position
        RegExpBytecode bytecode = compiler.compile("", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("");

        // Start index out of bounds
        result = engine.exec("abc", -1);
        assertThat(result).isNull();
        result = engine.exec("abc", 4);
        assertThat(result).isNull();
    }

    @Test
    public void testEscapedCharacters() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("a\\nb", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("a\nb");

        bytecode = compiler.compile("a\\tb", "");
        engine = new RegExpEngine(bytecode);
        result = engine.exec("a\tb", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("a\tb");
    }

    @Test
    public void testExecNoMatch() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("defgh", 0);
        assertThat(result).isNull();
    }

    @Test
    public void testExecSimpleMatch() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("xxabcxx", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("abc");
        assertThat(result.startIndex()).isEqualTo(2);
        assertThat(result.endIndex()).isEqualTo(5);
    }

    @Test
    public void testExecWithGroups() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("a(bc)d", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("xabcd", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("abcd");
        assertThat(result.getCapture(1)).isEqualTo("bc");
    }

    @Test
    public void testFlagsCombination() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("a.b", "is");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("A\nB", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("A\nB");
    }

    @Test
    public void testLineAnchors() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("^abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("abc", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();

        result = engine.exec("xabc", 0);
        assertThat(result).isNull(); // Should not match at position 0

        bytecode = compiler.compile("abc$", "");
        engine = new RegExpEngine(bytecode);
        result = engine.exec("abc", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();

        result = engine.exec("abcx", 0);
        assertThat(result).isNull(); // Should not match at end
    }

    @Test
    public void testLookbehindAssertionsBasic() {
        assertThat(matches("(?<=a)b", "", "ab")).isTrue();
        assertThat(matches("(?<=a)b", "", "cb")).isFalse();
        assertThat(matches("(?<!a)b", "", "cb")).isTrue();
        assertThat(matches("(?<!a)b", "", "ab")).isFalse();
        assertThat(matches("(?<!a)b", "", "b")).isTrue();
    }

    @Test
    public void testLookbehindAssertionsCaptures() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("(?<=(a))b", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("ab", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("b");
        assertThat(result.getCapture(1)).isEqualTo("a");

        assertThat(matches("(?<=(a))\\1", "", "aa")).isTrue();
    }

    @Test
    public void testLookbehindAssertionsSyntaxAndQuantifierRules() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        assertThatThrownBy(() -> compiler.compile("(?<=a", ""))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("Unclosed lookbehind");
        assertThatThrownBy(() -> compiler.compile("(?<!a", ""))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("Unclosed lookbehind");
        assertThatThrownBy(() -> compiler.compile("(?<=a)+b", ""))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("Nothing to repeat");

        // QuickJS allows quantified lookahead in non-unicode mode (Annex B).
        assertThatCode(() -> compiler.compile("(?=a)+b", ""))
                .doesNotThrowAnyException();
    }

    @Test
    public void testLookbehindAssertionsVariableLength() {
        assertThat(matches("(?<=a*)b", "", "aaab")).isTrue();
        assertThat(matches("(?<=a{2,3})b", "", "aaab")).isTrue();
        assertThat(matches("(?<=a{2,3})b", "", "ab")).isFalse();
        assertThat(matches("(?<=a)b+", "", "abbb")).isTrue();
    }

    @Test
    public void testMultilineMode() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("^abc$", "m");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("abc\ndef", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("abc");

        result = engine.exec("abc\ndef", 4);
        assertThat(result).isNull(); // Should not match "def" with "^abc$"

        // Test ^ matching at line start
        bytecode = compiler.compile("^def", "m");
        engine = new RegExpEngine(bytecode);
        result = engine.exec("abc\ndef", 4);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("def");
    }

    @Test
    public void testMultipleCaptureGroups() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("(a)(b)(c)", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("abc", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("abc");
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
    public void testNamedCaptureGroupsDuplicateNameThrows() {
        assertErrorWithJavet("new RegExp('(?<x>a)(?<x>b)')");
    }

    @Test
    public void testNamedCaptureGroupsDuplicateNamesAcrossAlternatives() {
        assertObjectWithJavet("""
                const dateRegex = /(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})|(?<month>\\d{2})\\/(?<day>\\d{2})\\/(?<year>\\d{4})/;
                const match1 = '2025-03-15'.match(dateRegex);
                const match2 = '03/15/2025'.match(dateRegex);
                [match1.groups, match2.groups]""");
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
                .isInstanceOf(JSException.class)
                .hasMessageContaining("group name not defined");
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
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("((a)b)", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("ab", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("ab");
        assertThat(result.getCapture(1)).isEqualTo("ab");
        assertThat(result.getCapture(2)).isEqualTo("a");
    }

    @Test
    public void testOptionalGroupWithAlternationInStarLoop() {
        // Basic: alternation without star, without optional
        assertThat(matches("^(a|b)$", "", "a")).as("basic alt a").isTrue();
        assertThat(matches("^(a|b)$", "", "b")).as("basic alt b").isTrue();

        // Optional alternation (no star)
        assertThat(matches("^(a|b)?$", "", "")).as("opt alt empty").isTrue();
        assertThat(matches("^(a|b)?$", "", "a")).as("opt alt a").isTrue();

        // Star without alternation
        assertThat(matches("^(x)?$", "", "")).as("opt x empty").isTrue();
        assertThat(matches("^(xa?)*$", "", "x")).as("star xa? x").isTrue();
        assertThat(matches("^(xa?)*$", "", "xa")).as("star xa? xa").isTrue();

        // Non-capturing optional alternation in star - simplest case
        assertThat(matches("^(?:a|b)*$", "", "a")).as("nc star alt a").isTrue();
        assertThat(matches("^(?:a|b)+$", "", "a")).as("nc plus alt a").isTrue();

        // Optional alternation - no star
        assertThat(matches("^x(?:a|b)?$", "", "x")).as("nc x opt alt").isTrue();
        assertThat(matches("^x(?:a|b)?$", "", "xa")).as("nc xa opt alt").isTrue();

        // Star of simple optional
        assertThat(matches("^(?:xa?)*$", "", "x")).as("nc star xa?").isTrue();

        // Star of alternation without optional
        assertThat(matches("^(?:x(?:a|b))*$", "", "xa")).as("nc star x-alt xa").isTrue();

        // Now the previously-failing combo: optional alternation in star
        assertThat(matches("^(?:x(?:a|b)?)*$", "", "x")).as("nc star x opt-alt x").isTrue();
        assertThat(matches("^(?:x(?:a|b)?)*$", "", "xa")).as("nc star x opt-alt xa").isTrue();

        // Capturing optional alternation in star
        assertThat(matches("^(x(a|b)?)*$", "", "x")).as("star x matches").isTrue();
        assertThat(matches("^(x(a|b)?)*$", "", "xa")).as("star xa matches").isTrue();
        assertThat(matches("^(x(a|b)?)*$", "", "xaxb")).as("star xaxb matches").isTrue();

        // Original failing patterns from the bug report
        assertThat(matches("^([a-z]{2})(-t-([a-z]{2})(-(ab|cd))?)*$", "i", "ab-t-cd"))
                .as("pattern with alternation in star").isTrue();
        assertThat(matches("^([a-z]{2})(-t-([a-z]{2})(-(ab|cd))?)*$", "i", "ab-t-cd-ab"))
                .as("pattern with alternation in star, matching alternation").isTrue();
    }

    @Test
    public void testStartIndexInMiddle() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("xxxabc", 3);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("abc");
        assertThat(result.startIndex()).isEqualTo(3);
        assertThat(result.endIndex()).isEqualTo(6);
    }

    @Test
    public void testUnicodeCharacters() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        RegExpBytecode bytecode = compiler.compile("😀🌟🚀", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("😀🌟🚀", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("😀🌟🚀");
        assertThat(engine.test("😀🌟🚀")).isTrue();
    }

    @Test
    public void testUnicodePropertyEscapesBasics() {
        assertThat(matches("\\p{L}", "u", "A")).isTrue();
        assertThat(matches("\\p{L}", "u", "π")).isTrue();
        assertThat(matches("\\p{L}", "u", "1")).isFalse();
        assertThat(matches("\\P{L}", "u", "1")).isTrue();
        assertThat(matches("\\P{L}", "u", "π")).isFalse();
        assertThat(matches("\\p{Any}", "u", "😀")).isTrue();
        assertThat(matches("\\p{Assigned}", "u", "A")).isTrue();
    }

    @Test
    public void testUnicodePropertyEscapesInCharacterClass() {
        assertThat(matches("^[\\p{L}\\p{Nd}]+$", "u", "Aπ9")).isTrue();
        assertThat(matches("^[\\P{L}]+$", "u", "123_")).isTrue();
        assertThat(matches("^[\\P{L}]+$", "u", "123A")).isFalse();
    }

    @Test
    public void testUnicodePropertyEscapesPropertyForms() {
        assertThat(matches("\\p{gc=Lu}", "u", "A")).isTrue();
        assertThat(matches("\\p{gc=Lu}", "u", "a")).isFalse();
        assertThat(matches("\\p{General_Category=Uppercase_Letter}", "u", "A")).isTrue();
        assertThat(matches("\\p{Script=Greek}", "u", "Ω")).isTrue();
        assertThat(matches("\\p{Script=Greek}", "u", "A")).isFalse();
        assertThat(matches("\\p{sc=Grek}", "u", "β")).isTrue();
        assertThat(matches("\\p{Script_Extensions=Greek}", "u", "π")).isTrue();
        assertThat(matches("\\p{ASCII}", "u", "A")).isTrue();
        assertThat(matches("\\p{ASCII}", "u", "π")).isFalse();
        assertThat(matches("\\p{White_Space}", "u", "\u00A0")).isTrue();
        assertThat(matches("\\p{ID_Start}", "u", "A")).isTrue();
        assertThat(matches("\\p{ID_Start}", "u", "1")).isFalse();
        assertThat(matches("\\p{ID_Continue}", "u", "1")).isTrue();
    }

    @Test
    public void testUnicodePropertyEscapesRequireUnicodeMode() {
        assertThat(matches("\\p{L}", "", "p{L}")).isTrue();
        assertThat(matches("\\p{L}", "", "A")).isFalse();
        assertThat(matches("[\\p{L}]", "", "{")).isTrue();
        assertThat(matches("\\P{L}", "", "P{L}")).isTrue();
    }

    @Test
    public void testUnicodePropertyEscapesSyntaxErrors() {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());

        assertThatThrownBy(() -> compiler.compile("\\p", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("expecting '{' after \\p");
        assertThatThrownBy(() -> compiler.compile("\\P", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("expecting '{' after \\p");
        assertThatThrownBy(() -> compiler.compile("\\p{L", "u"))
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class)
                .hasMessageContaining("expecting '}'");
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
}
