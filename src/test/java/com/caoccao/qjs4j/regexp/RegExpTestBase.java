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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Compiles patterns with the current realm's Unicode resolver. */
abstract class RegExpTestBase extends BaseJavetTest {
    protected void assertDoesNotMatch(String pattern, String flags, String input) {
        assertMatches(pattern, flags, input, false);
    }

    protected void assertInvalidPattern(RegExpCompiler compiler, String pattern, String flags) {
        assertThatThrownBy(() -> compiler.compile(pattern, flags)).as("/%s/%s", pattern, flags)
                .isInstanceOf(RegExpCompiler.RegExpSyntaxException.class);
    }

    protected void assertMatches(String pattern, String flags, String input) {
        assertMatches(pattern, flags, input, true);
    }

    private void assertMatches(String pattern, String flags, String input, boolean expected) {
        assertThat(patternMatches(pattern, flags, input)).as("/%s/%s on <%s>", pattern, flags, input)
                .isEqualTo(expected);
    }

    protected void assertMatches(String pattern, String flags, String input, String description) {
        assertThat(patternMatches(pattern, flags, input)).as(description).isTrue();
    }

    protected RegExpEngine compilePattern(String pattern, String flags) {
        RegExpCompiler compiler = new RegExpCompiler(context.getUnicodePropertyResolver());
        return new RegExpEngine(compiler.compile(pattern, flags));
    }

    protected boolean patternMatches(String pattern, String flags, String input) {
        RegExpEngine.MatchResult result = compilePattern(pattern, flags).exec(input, 0);
        return result != null && result.matched();
    }
}
