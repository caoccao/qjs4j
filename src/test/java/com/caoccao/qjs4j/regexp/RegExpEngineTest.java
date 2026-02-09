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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RegExpEngineTest extends BaseJavetTest {
    @Test
    public void testCaseInsensitiveMatching() {
        RegExpCompiler compiler = new RegExpCompiler();
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
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("a.b", "s");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("a\nb");
    }

    @Test
    public void testDotWithoutDotAll() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("a.b", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertThat(result).isNull(); // Should not match because . doesn't match \n
    }

    @Test
    public void testEdgeCases() {
        RegExpCompiler compiler = new RegExpCompiler();
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
        RegExpCompiler compiler = new RegExpCompiler();
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
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("defgh", 0);
        assertThat(result).isNull();
    }

    @Test
    public void testExecSimpleMatch() {
        RegExpCompiler compiler = new RegExpCompiler();
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
        RegExpCompiler compiler = new RegExpCompiler();
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
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("a.b", "is");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("A\nB", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("A\nB");
    }

    @Test
    public void testLineAnchors() {
        RegExpCompiler compiler = new RegExpCompiler();
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
    public void testMultilineMode() {
        RegExpCompiler compiler = new RegExpCompiler();
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
        RegExpCompiler compiler = new RegExpCompiler();
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
        assertThatThrownBy(() -> resetContext().eval("new RegExp('(?<x>a)(?<x>b)')"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("duplicate group name");
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
        RegExpCompiler compiler = new RegExpCompiler();
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
    public void testStartIndexInMiddle() {
        RegExpCompiler compiler = new RegExpCompiler();
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
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("😀🌟🚀", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("😀🌟🚀", 0);
        assertThat(result).isNotNull();
        assertThat(result.matched()).isTrue();
        assertThat(result.getMatch()).isEqualTo("😀🌟🚀");
        assertThat(engine.test("😀🌟🚀")).isTrue();
    }
}
