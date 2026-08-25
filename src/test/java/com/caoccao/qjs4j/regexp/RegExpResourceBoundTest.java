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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSTypeConversions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A guest-supplied pattern must not choose how much memory the matcher claims.
 * <p>
 * The backtracking cap used to be expressed in entries, but an entry holds every capture twice plus
 * the registers, so the pattern chose the entry size: at 2<sup>20</sup> entries a one-group pattern
 * budgeted 21 ints each and a 100,000-group pattern 200,019 — the same nominal cap, three orders of
 * magnitude apart in bytes. The initial 64-entry allocation alone was ~51 MiB for that pattern,
 * claimed before matching began, and no later check could take it back. The cap is now a fixed
 * number of ints, and the capture count is bounded at compile time as QuickJS bounds it
 * ({@code CAPTURE_COUNT_MAX}), which also stops the single-byte capture indices in the compiled
 * bytecode from wrapping.
 * <p>
 * Compared against {@code ../quickjs/qjs}, which raises {@code SyntaxError: too many captures} for
 * the 255th group and accepts 254.
 */
public class RegExpResourceBoundTest extends BaseTest {

    private String evalToString(String code) {
        return JSTypeConversions.toString(context, context.eval(code)).value();
    }

    private String groups(int count) {
        return "()".repeat(count);
    }

    @Test
    @Timeout(60)
    public void testCaptureCountAtTheLimitIsAccepted() {
        // 254 explicit groups plus group 0 is exactly CAPTURE_COUNT_MAX.
        assertThat(evalToString(
                "(function () { try { new RegExp('%s'); return 'OK' } catch (e) { return e.name } })()"
                        .formatted(groups(254))))
                .isEqualTo("OK");
    }

    @Test
    @Timeout(60)
    public void testCaptureCountBeyondTheLimitIsASyntaxError() {
        assertThat(evalToString(
                "(function () { try { new RegExp('%s'); return 'OK' } catch (e) { return e.name + ': ' + e.message } })()"
                        .formatted(groups(255))))
                .startsWith("SyntaxError:")
                .contains("too many captures");
    }

    @Test
    @Timeout(60)
    public void testHugeCaptureCountIsRejectedRatherThanAllocated() {
        // 100,000 groups used to reserve ~51 MiB of int[] before the first character was matched.
        assertThat(evalToString(
                "(function () { try { new RegExp('%s'); return 'OK' } catch (e) { return e.name } })()"
                        .formatted(groups(100000))))
                .isEqualTo("SyntaxError");
    }

    @Test
    @Timeout(60)
    public void testCaptureLimitAppliesToLiteralsToo() {
        assertThat(evalToString(
                "(function () { try { eval('/' + '%s' + '/'); return 'OK' } catch (e) { return e.name } })()"
                        .formatted(groups(300))))
                .isEqualTo("SyntaxError");
    }

    @Test
    @Timeout(60)
    public void testCatastrophicBacktrackingWithManyCapturesRaisesRangeError() {
        // The pattern at the capture limit still has to hit the backtracking budget rather than
        // the heap.
        assertThat(evalToString(
                """
                        (function () {
                          const pattern = new RegExp('^' + '(a+)+'.repeat(1) + '(a+)+$');
                          try { pattern.test('a'.repeat(40) + 'b'); return 'NO ERROR' }
                          catch (e) { return e.name }
                        })()"""))
                .isEqualTo("RangeError");
    }

    @Test
    @Timeout(60)
    public void testOrdinaryCaptureHeavyPatternStillMatches() {
        // The complement: a legitimate pattern near the limit must still work.
        assertThat(evalToString(
                """
                        (function () {
                          const pattern = new RegExp('%s' + '(x)');
                          const match = pattern.exec('x');
                          return match.length + ',' + match[match.length - 1];
                        })()""".formatted(groups(200))))
                .isEqualTo("202,x");
    }

    @Test
    @Timeout(60)
    public void testNamedGroupsCountTowardTheLimit() {
        StringBuilder pattern = new StringBuilder();
        for (int index = 0; index < 255; index++) {
            pattern.append("(?<g").append(index).append(">)");
        }
        assertThat(evalToString(
                "(function () { try { new RegExp('%s'); return 'OK' } catch (e) { return e.name } })()"
                        .formatted(pattern)))
                .isEqualTo("SyntaxError");
    }

    @Test
    @Timeout(60)
    public void testNonCapturingGroupsDoNotCountTowardTheLimit() {
        assertThat(evalToString(
                "(function () { try { new RegExp('%s'); return 'OK' } catch (e) { return e.name } })()"
                        .formatted("(?:)".repeat(1000))))
                .isEqualTo("OK");
    }
}
