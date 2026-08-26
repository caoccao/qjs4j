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
import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.StringJoiner;

/**
 * A guest-supplied pattern must not choose how much memory the matcher claims — and the ceiling it
 * runs into has to be V8's, not one picked for tidiness.
 * <p>
 * Every limit comparison here runs the same source through V8 and through qjs4j, because V8's
 * behaviour is the specification for these limits in a way no reasoning about them is. Two earlier
 * attempts at this budget were argued from first principles and both were wrong: the first narrowed
 * the ceiling to a round number, the second derived it from the old entry count. Both broke
 * Test262's whole {@code property-escapes} corpus, which matches a greedy {@code +} against every
 * code point in Unicode.
 * <p>
 * The cap is now {@code RegExpStack::kMaximumStackSize} — {@code 64 * MB}, from V8's
 * {@code src/regexp/regexp-stack.h} — and the backtrack frame was cut from 84 bytes to 12 so that
 * budget buys an amount of backtracking comparable to V8's 4-byte slots.
 * <p>
 * One limit is deliberately absent here: the ReDoS step budget. V8 runs {@code /(a+)+$/} against 64
 * characters to completion and qjs4j stops it with a {@code RangeError}, so comparing the two would
 * assert away the feature. It is covered by {@code JSResourceLimitTest}, which does not go through
 * V8 for that group.
 */
public class RegExpResourceBoundTest extends BaseJavetTest {

    private String allStringsInUnicodeProperty(String propertyName) {
        UnicodePropertyResolver.SequencePropertyResult property =
                UnicodePropertyResolver.resolveSequenceProperty(propertyName);
        StringJoiner strings = new StringJoiner(",");
        int[] ranges = property.codePointRanges();
        for (int i = 0; i < ranges.length; i += 2) {
            for (int codePoint = ranges[i]; codePoint <= ranges[i + 1]; codePoint++) {
                strings.add(toJavaScriptString(codePoint));
            }
        }
        for (int[] sequence : property.sequences()) {
            strings.add(toJavaScriptString(sequence));
        }
        return """
                (function () {
                  const regExp = /^\\p{%2$s}$/v;
                  const subject = [%1$s].filter(string => regExp.test(string)).join('');
                  try { return String(/^\\p{%2$s}+$/v.test(subject)) } catch (e) { return e.name }
                })()""".formatted(strings, propertyName);
    }

    /**
     * Source that builds a subject containing every Unicode code point, then applies a pattern.
     *
     * @param pattern the regular expression literal to test with
     * @return source evaluating to {@code 'true'}, {@code 'false'} or the error name
     */
    private String everyCodePoint(String pattern) {
        return """
                (function () {
                  const parts = [];
                  for (let cp = 0; cp <= 0x10FFFF; cp++) parts.push(cp);
                  let subject = '';
                  for (let i = 0; i < parts.length; i += 10000) {
                    subject += String.fromCodePoint.apply(null, parts.slice(i, i + 10000));
                  }
                  try { return String(%s.test(subject)) } catch (e) { return e.name }
                })()""".formatted(pattern);
    }

    private String groups(int count) {
        return "()".repeat(count);
    }

    @Test
    @Timeout(60)
    public void testCaptureCountAtQuickJsLimitIsAccepted() {
        assertStringWithJavet(
                "(function () { try { new RegExp('%s'); return 'OK' } catch (e) { return e.name } })()"
                        .formatted(groups(254)));
    }

    @Test
    @Timeout(300)
    public void testGreedyMatchOverAHalfMillionCharacterSubject() {
        assertStringWithJavet(
                """
                        (function () {
                          const subject = 'x'.repeat(500000);
                          try { return String(/^([\\s\\S])+$/u.test(subject)) } catch (e) { return e.name }
                        })()""");
    }

    @Test
    @Timeout(300)
    public void testGreedyMatchOverEveryUnicodeCodePoint() {
        // ~1.11 million backtrack points. At the old 84-byte frame that was ~93 MiB — more than
        // V8's entire 64 MiB stack — so all 426 property-escape tests failed with
        // "exceeded the backtracking stack limit".
        assertStringWithJavet(everyCodePoint("/^[\\s\\S]+$/u"));
    }

    @Test
    @Timeout(300)
    public void testGreedyMatchOverEveryUnicodeCodePointWithACapture() {
        // A capture inside the loop dirties the saved state on every iteration, so each backtrack
        // point appends a state instead of sharing one. This is the expensive shape, and whether
        // it fits in 64 MiB is V8's answer to give, not mine.
        assertStringWithJavet(everyCodePoint("/^([\\s\\S])+$/u"));
    }

    @Test
    @Timeout(300)
    public void testNegatedPropertyEscapeOverEveryCodePoint() {
        // Verbatim the pattern from property-escapes/generated/White_Space.js, which was among the
        // 426 failures.
        assertStringWithJavet(everyCodePoint("/^\\P{White_Space}+$/u"));
    }

    @Test
    @Timeout(60)
    public void testNonCapturingGroupsDoNotCountTowardTheLimit() {
        assertStringWithJavet(
                "(function () { try { new RegExp('%s'); return 'OK' } catch (e) { return e.name } })()"
                        .formatted("(?:)".repeat(1000)));
    }

    @Test
    @Timeout(60)
    public void testOrdinaryCaptureHeavyPatternStillMatches() {
        assertStringWithJavet(
                """
                        (function () {
                          const pattern = new RegExp('%s' + '(x)');
                          const match = pattern.exec('x');
                          return match.length + ',' + match[match.length - 1];
                        })()""".formatted(groups(200)));
    }

    @Test
    @Timeout(300)
    public void testRgiEmojiPropertyMatchesAllStringsAsOneSubject() {
        // This is the same end-to-end shape as Test262's generated RGI_Emoji.js: concatenate every
        // string in the property, then match the complete subject with a greedy +. Each failed
        // branch pops a saved state before trying the next alternative, which exposed a leak of
        // popped states even though the live backtrack stack remained shallow.
        // This subject comes from qjs4j's Unicode 17 tables. The embedded V8 currently uses an
        // older Unicode version, so each engine first filters the list through its own RGI_Emoji
        // table. That isolates the resource-bound comparison from Unicode data-version skew.
        assertStringWithJavet(allStringsInUnicodeProperty("RGI_Emoji"));
    }

    private String toJavaScriptString(int... codePoints) {
        StringBuilder string = new StringBuilder("'");
        for (int codePoint : codePoints) {
            string.append("\\u{").append(Integer.toHexString(codePoint)).append('}');
        }
        return string.append('\'').toString();
    }
}
