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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code RegExp.input}, {@code RegExp.lastMatch}, {@code RegExp.lastParen},
 * {@code RegExp.leftContext}, {@code RegExp.rightContext} and {@code RegExp.$1}–{@code $9}: the
 * legacy statics a successful match leaves behind on the constructor.
 * <p>
 * Every one of them was uncovered — the accessors existed, the state holder behind them existed, and
 * nothing in the suite had ever asked what any of them answered. The observable half is checked
 * against V8, because these are web-reality semantics rather than anything the specification pins
 * down, and V8 is the reality. The rest of the class is defensive arithmetic that a match cannot
 * reach — a capture list shorter than nine, offsets the engine did not supply — and those go
 * through the holder directly.
 */
public class RegExpLegacyStaticsTest extends BaseJavetTest {
    @Test
    public void testAFailedMatchLeavesThePreviousOneStanding() {
        // The statics record the last *successful* match, so a search that finds nothing must not
        // clear them.
        assertStringWithJavet("""
                /b/.exec('abc');
                /zzz/.exec('abc');
                [RegExp.lastMatch, RegExp.leftContext, RegExp.rightContext].join('|')""");
    }

    @Test
    public void testAMatchWithNoGroupsLeavesLastParenEmpty() {
        assertStringWithJavet("""
                /b/.exec('abc');
                [RegExp.lastMatch, RegExp.lastParen, RegExp.leftContext, RegExp.rightContext].join('|')""");
    }

    @Test
    public void testAnEmptyMatchStillDividesTheSubject() {
        assertStringWithJavet("""
                /(?:)/.exec('abc');
                ['[' + RegExp.lastMatch + ']', RegExp.leftContext, RegExp.rightContext].join('|')""");
    }

    @Test
    public void testAnOutOfRangeCaptureIndexAnswersEmptyRatherThanThrowing() {
        // The accessor is reached from property lookup on the constructor, where the index has
        // already been parsed out of a name — so it answers rather than throws.
        RegExpLegacyStatics statics = new RegExpLegacyStatics();
        statics.update("abc", new String[]{"abc", "a"}, null, 0);
        assertThat(statics.getCapture(1)).isEqualTo("a");
        assertThat(statics.getCapture(0)).isEmpty();
        assertThat(statics.getCapture(-1)).isEmpty();
        assertThat(statics.getCapture(10)).isEmpty();
        assertThat(statics.getCapture(Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    public void testAnUpdateWithNothingToRecordLeavesEverythingEmpty() {
        // The matcher always supplies a match; a caller that supplies neither text nor offsets is
        // the guard against it one day not doing so.
        RegExpLegacyStatics statics = new RegExpLegacyStatics();
        statics.update("abc", new String[]{"abc", "a"}, new int[][]{{0, 3}}, 0);
        statics.update(null, null, null, 0);
        assertThat(statics.getInput()).isEmpty();
        assertThat(statics.getLastMatch()).isEmpty();
        assertThat(statics.getLastParen()).isEmpty();
        assertThat(statics.getLeftContext()).isEmpty();
        assertThat(statics.getRightContext()).isEmpty();
        assertThat(statics.getCapture(1)).isEmpty();
    }

    @Test
    public void testAnUpdateWithoutOffsetsFindsTheMatchInTheSubject() {
        // Offsets are how the contexts are decided. Without them the match text is searched for,
        // starting where the caller says the search began — which is what keeps `leftContext` from
        // pointing at an earlier occurrence of the same text.
        RegExpLegacyStatics statics = new RegExpLegacyStatics();
        statics.update("abcabc", new String[]{"bc"}, null, 3);
        assertThat(statics.getLastMatch()).isEqualTo("bc");
        assertThat(statics.getLeftContext()).isEqualTo("abca");
        assertThat(statics.getRightContext()).isEmpty();

        // A start index past the last occurrence falls back to the first one rather than losing it.
        statics.update("abcabc", new String[]{"bc"}, null, 99);
        assertThat(statics.getLeftContext()).isEqualTo("a");
        assertThat(statics.getRightContext()).isEqualTo("abc");

        // Match text that is not in the subject at all leaves the contexts at the start.
        statics.update("abc", new String[]{"zz"}, null, 0);
        assertThat(statics.getLastMatch()).isEqualTo("zz");
        assertThat(statics.getLeftContext()).isEmpty();
        assertThat(statics.getRightContext()).isEqualTo("abc");
    }

    @Test
    public void testCapturesBeyondTheNinthAreNotAddressableButStillCount() {
        // Only $1 to $9 exist, so a tenth group is unreachable by name — but it is still the last
        // parenthesized match, and that is what `lastParen` answers.
        assertStringWithJavet("""
                /(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)/.exec('abcdefghij');
                [RegExp.$9, RegExp.lastParen].join('|')""");
    }

    @Test
    public void testEveryCaptureIsAddressableAndUnmatchedOnesAreEmpty() {
        assertStringWithJavet("""
                /(a)(b)(c)/.exec('abc');
                [RegExp.$1, RegExp.$2, RegExp.$3, RegExp.$4].join('|')""");
        // An alternative that did not participate leaves its capture empty rather than undefined.
        assertStringWithJavet("""
                /(a)|(z)/.exec('a');
                [RegExp.$1, RegExp.$2, RegExp.lastParen].join('|')""");
    }

    @Test
    public void testLastParenIsTheLastGroupByPositionAndNotTheLastOneThatMatched() {
        // `/(a)|(z)/.exec('a')` answered "a", because the search walked backwards for the last
        // capture that was not null. The last group is the second one, and it matched nothing.
        assertStringWithJavet("""
                /(a)|(z)/.exec('a');
                '[' + RegExp.lastParen + ']'""");
        assertStringWithJavet("""
                /(a)(b)?/.exec('a');
                '[' + RegExp.lastParen + ']'""");
        assertStringWithJavet("""
                /(a)(b)/.exec('ab');
                RegExp.lastParen""");

        RegExpLegacyStatics statics = new RegExpLegacyStatics();
        statics.update("ab", new String[]{"ab", "a", "b", null}, new int[][]{{0, 2}}, 0);
        assertThat(statics.getLastParen()).isEmpty();
        statics.update("ab", new String[]{"ab", "a", "b"}, new int[][]{{0, 2}}, 0);
        assertThat(statics.getLastParen()).isEqualTo("b");
        // No groups at all leaves it empty.
        statics.update("ab", new String[]{"ab"}, new int[][]{{0, 2}}, 0);
        assertThat(statics.getLastParen()).isEmpty();
    }

    @Test
    public void testOffsetsOutsideTheSubjectAreClampedToIt() {
        // The offsets come from the matcher, and a subject the caller then reported differently
        // would otherwise index outside it.
        RegExpLegacyStatics statics = new RegExpLegacyStatics();
        statics.update("abc", new String[]{""}, new int[][]{{-5, 99}}, 0);
        assertThat(statics.getLastMatch()).as("empty match text is taken from the offsets").isEqualTo("abc");
        assertThat(statics.getLeftContext()).isEmpty();
        assertThat(statics.getRightContext()).isEmpty();

        // An end before the start collapses to an empty match at the start.
        statics.update("abc", new String[]{""}, new int[][]{{2, 1}}, 0);
        assertThat(statics.getLastMatch()).isEmpty();
        assertThat(statics.getLeftContext()).isEqualTo("ab");
        assertThat(statics.getRightContext()).isEqualTo("c");
    }

    @Test
    public void testReleaseDropsTheSubjectAndEveryCapture() {
        // The subject can be arbitrarily large and a closed context must own nothing.
        RegExpLegacyStatics statics = new RegExpLegacyStatics();
        statics.update("abc", new String[]{"abc", "a"}, new int[][]{{0, 3}}, 0);
        statics.release();
        assertThat(statics.getInput()).isNull();
        assertThat(statics.getLastMatch()).isNull();
        assertThat(statics.getLastParen()).isNull();
        assertThat(statics.getLeftContext()).isNull();
        assertThat(statics.getRightContext()).isNull();
        assertThat(statics.getCapture(1)).as("captures are emptied, not nulled").isEmpty();
    }

    @Test
    public void testTheSubjectIsRecordedAndCanBeSetOnItsOwn() {
        assertStringWithJavet("""
                /b/.exec('abc');
                RegExp.input""");
        // Assigning it directly is the legacy setter, and a null-ish assignment is a string.
        RegExpLegacyStatics statics = new RegExpLegacyStatics();
        assertThat(statics.getInput()).as("a fresh realm has an empty subject, not a null one").isEmpty();
        statics.setInput("abc");
        assertThat(statics.getInput()).isEqualTo("abc");
        statics.setInput(null);
        assertThat(statics.getInput()).isEmpty();
    }
}
