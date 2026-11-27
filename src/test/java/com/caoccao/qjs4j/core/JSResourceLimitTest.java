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
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The engine must not let a script consume unbounded time or memory.
 * <p>
 * Three limits are covered: a backtracking budget for the regular expression engine, a maximum string length, and a
 * host interrupt path for a running evaluation. Before these existed, {@code /(a+)+$/} against 40 characters hung the
 * calling thread, {@code 'a'.repeat(1e9)} raised {@code OutOfMemoryError} instead of {@code RangeError}, and
 * {@code while (true) {}} could only be stopped by killing the process.
 * <p>
 * Everything a limit does <em>not</em> change is asserted against V8, so the guards cannot quietly alter ordinary
 * behaviour, and so is every oversized input both engines reject. Three groups are deliberately not compared, each
 * because the divergence is the feature:
 * <ul>
 * <li>The ReDoS step budget. V8 runs {@code /(a+)+$/} against 64 characters to completion; qjs4j stops it. Bounding it
 * is the whole point.</li>
 * <li>The host interrupt and execution deadline, which are Java API with no JavaScript surface.</li>
 * </ul>
 */
public class JSResourceLimitTest extends BaseJavetTest {

    private String evalToString(String code) {
        return JSTypeConversions.toString(context, context.eval(code)).value();
    }

    @Test
    @Timeout(60)
    public void testBacktrackingLimitIsConfigurable() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setRegExpBacktrackLimit(0));
                JSContext unlimitedContext = runtime.createContext()) {
            // 0 disables the limit, restoring the previous unbounded behaviour. A 20-character
            // input is small enough to still finish quickly.
            JSValue result = unlimitedContext.eval("/(a+)+$/.test('a'.repeat(20) + '!')");
            assertThat(result).isEqualTo(JSBoolean.FALSE);
        }
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setRegExpBacktrackLimit(1000));
                JSContext strictContext = runtime.createContext()) {
            assertThatThrownBy(() -> strictContext.eval("/(a+)+$/.test('a'.repeat(20) + '!')"))
                    .isInstanceOf(JSException.class).hasMessageContaining("backtracking limit");
        }
    }

    @Test
    @Timeout(60)
    public void testCatastrophicBacktrackingInsideLookaheadIsBounded() {
        // A lookaround runs on a nested context. It must inherit the remaining budget rather than
        // start a fresh one, or a lookaround in a loop resets the budget on every iteration.
        assertThat(evalToString("""
                const subject = 'a'.repeat(64) + '!';
                try { /(?=(a+)+$)a/.test(subject); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }"""))
                .isEqualTo("CAUGHT RangeError");
    }

    @Test
    @Timeout(60)
    public void testCatastrophicBacktrackingIsBounded() {
        // Without a budget this grows exponentially: 0.20s / 0.28s / 0.58s / 1.65s for 16 / 20 /
        // 22 / 24 characters, and a 40-character input never returns.
        assertThat(evalToString("""
                const subject = 'a'.repeat(64) + '!';
                try { /(a+)+$/.test(subject); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }"""))
                .isEqualTo("CAUGHT RangeError");
    }

    @Test
    @Timeout(60)
    public void testConcatBeyondMaximumLengthRaisesRangeError() {
        assertThat(evalToString("""
                try {
                    let s = 'a'.repeat(1000);
                    for (let i = 0; i < 40; i++) { s = s.concat(s) }
                    'NO ERROR';
                } catch (e) { 'CAUGHT ' + e.name }""")).isEqualTo("CAUGHT RangeError");
    }

    @Test
    @Timeout(60)
    public void testExecutionDeadlineTerminatesAnInfiniteLoop() {
        try (JSRuntime runtime = new JSRuntime(); JSContext deadlineContext = runtime.createContext()) {
            deadlineContext.getVirtualMachine().setExecutionDeadline(System.currentTimeMillis() + 200);
            // The script wraps its own loop in try/catch. The deadline must not be interceptable.
            assertThatThrownBy(() -> deadlineContext.eval("try { while (true) {} } catch (e) { 'SWALLOWED' }"))
                    .isInstanceOf(JSTerminationException.class).hasMessage("execution timeout");
        }
    }

    @Test
    @Timeout(60)
    public void testHostInterruptTerminatesAnInfiniteLoop() throws InterruptedException {
        try (JSRuntime runtime = new JSRuntime(); JSContext interruptedContext = runtime.createContext()) {
            assertHostInterrupt(interruptedContext, "try { while (true) {} } catch (e) { 'SWALLOWED' }");
        }
    }

    @Test
    @Timeout(60)
    public void testInterruptCanBeClearedAndEvaluationResumes() {
        try (JSRuntime runtime = new JSRuntime(); JSContext resumableContext = runtime.createContext()) {
            String sum = "(function () { let total = 0; for (let i = 0; i < 200000; i++) { total += i } return total })()";
            runtime.requestInterrupt();
            assertThat(runtime.shouldInterrupt()).isTrue();
            assertThatThrownBy(() -> resumableContext.eval(sum)).isInstanceOf(JSTerminationException.class);
            runtime.clearInterrupt();
            assertThat(runtime.shouldInterrupt()).isFalse();
            assertThat(resumableContext.eval(sum)).isEqualTo(JSNumber.of(19999900000L));
        }
    }

    @Test
    @Timeout(60)
    public void testJoinBeyondMaximumLengthRaisesRangeError() {
        assertThat(evalToString("""
                try {
                    new Array(1000000).fill('a'.repeat(1000)).join('');
                    'NO ERROR';
                } catch (e) { 'CAUGHT ' + e.name }""")).isEqualTo("CAUGHT RangeError");
    }

    @Test
    @Timeout(60)
    public void testNoDeadlineLeavesOrdinaryCodeUnaffected() {
        assertThat(context.eval("let total = 0; for (let i = 0; i < 200000; i++) { total += i } total"))
                .isEqualTo(JSNumber.of(19999900000L));
    }

    @Test
    public void testOrdinaryRegularExpressionsAreUnaffected() {
        assertStringWithJavet("/(\\w+)\\s(\\w+)/.exec('John Smith')[2]",
                "'2026-08-25'.replace(/(\\d+)-(\\d+)-(\\d+)/, '$3/$2/$1')",
                "String('aaa'.repeat(2000).match(/a+/)[0].length)", "String('x'.repeat(100000).split(/(?=x)/).length)");
    }

    @Test
    public void testOrdinaryStringOperationsAreUnaffected() {
        assertStringWithJavet("'abc'.repeat(3)", "'x'.padStart(5, '-')", "'x'.padEnd(5, '-')", "'a'.concat('b', 'c')",
                "[1, 2, 3].join('-')", "'a' + 'b' + 1");
    }

    @Test
    @Timeout(60)
    public void testPadBeyondMaximumLengthRaisesRangeError() {
        assertStringWithJavet("try { 'a'.padStart(1e9); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }",
                "try { 'a'.padEnd(1e9); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    @Timeout(60)
    public void testRegExpReplaceBeyondMaximumLengthRaisesRangeError() {
        // 2^30 characters, past both engines' ceilings. The replacement loop used to grow its buffer until the JVM
        // failed an allocation, then caught the OutOfMemoryError and reported a catchable InternalError.
        assertStringWithJavet("""
                try {
                    'a'.repeat(2 ** 17).replace(/a/g, 'b'.repeat(2 ** 13));
                    'NO ERROR';
                } catch (e) { 'CAUGHT ' + e.name + ': ' + e.message }""");
        // Count the unmatched tail without materializing a half-gigabyte replacement.
        assertStringWithJavet("""
                try {
                    ('x' + 'a'.repeat(1000)).replace(/x/, () => 'b'.repeat(536870888 - 500));
                    'NO ERROR';
                } catch (e) { 'CAUGHT ' + e.name + ': ' + e.message }""");
    }

    @Test
    @Timeout(60)
    public void testRepeatAtTheExactLengthBoundary() {
        assertIntegerWithJavet("'ab'.repeat(268435444).length", "'a'.repeat(536870888).length",
                "'abc'.repeat(178956962).length");
        assertStringWithJavet("try { 'a'.repeat(536870889); 'NO ERROR' } catch (e) { e.name }",
                "try { 'ab'.repeat(268435445); 'NO ERROR' } catch (e) { e.name }");
    }

    @Test
    @Timeout(60)
    public void testRepeatBeyondMaximumLengthRaisesRangeError() {
        assertStringWithJavet("try { 'a'.repeat(1e9); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }",
                "try { 'ab'.repeat(2147483647); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    @Timeout(60)
    public void testRepeatCountBeyondLongRangeRaisesRangeError() {
        // The length guard used to multiply first: (long) 1e20 saturates at Long.MAX_VALUE, so
        // 2 * that wrapped to -2, the guard passed, and `new StringBuilder(-2)` threw
        // NegativeArraySizeException — a Java failure that escaped the script's own catch. The
        // multiplication is now a division, so nothing overflows before the check.
        assertStringWithJavet("try { 'xx'.repeat(1e20); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }",
                "try { 'xx'.repeat(9223372036854775807); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }",
                "try { 'abc'.repeat(1e30); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }",
                "try { 'x'.repeat(Number.MAX_VALUE); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    @Timeout(60)
    public void testRepeatEdgeCountsAreUnaffectedByTheOverflowGuard() {
        // The guard divides by s.length(), so the empty-string and zero-count shortcuts have to
        // come first — dividing by zero would turn a legal call into an engine failure.
        assertStringWithJavet("JSON.stringify(''.repeat(1e20))", "JSON.stringify(''.repeat(0))",
                "JSON.stringify('abc'.repeat(0))",
                "try { 'x'.repeat(-1); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }",
                "try { 'x'.repeat(Infinity); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }",
                "JSON.stringify('x'.repeat(NaN))", "JSON.stringify('x'.repeat())",
                "try { 'x'.repeat(-Infinity); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    @Timeout(60)
    public void testReplacementPatternsBeyondMaximumLengthRaiseRangeError() {
        // Each replacement pattern expands to the whole 2^20-character subject 1024 times, 2^30 characters in all. The
        // substitution used to be built without a length check, so only the JVM's heap stopped it.
        assertStringWithJavet("""
                const s = 'a'.repeat(2 ** 20);
                const attempt = f => {
                  try {
                    return 'length ' + f().length;
                  } catch (e) {
                    return e.name + ': ' + e.message;
                  }
                };
                const groupsOnce = () => {
                  const re = /x/;
                  re.exec = function () {
                    re.exec = () => null;
                    return { 0: 'a', length: 1, index: 0, groups: { n: s } };
                  };
                  return re;
                };
                [
                  attempt(() => s.replace(/a+/, '$&'.repeat(1024))),
                  attempt(() => s.replace(/$/, '$`'.repeat(1024))),
                  attempt(() => s.replace(/^/, "$'".repeat(1024))),
                  attempt(() => s.replace(/(?<n>a+)/, '$<n>'.repeat(1024))),
                  attempt(() => s.replace(/(a+)/, '$01'.repeat(1024))),
                  attempt(() => s.replace(/(a+)/, '$1'.repeat(1024))),
                  attempt(() => groupsOnce()[Symbol.replace](s, '$<n>'.repeat(1024))),
                  attempt(() => s.replace('a', "$'".repeat(1024))),
                  attempt(() => s.replace('a', '$`$&'.repeat(1024))),
                  attempt(() => 'x'.repeat(1024).replaceAll('x', () => s)),
                  attempt(() => s.replaceAll('a', "$'".repeat(1024))),
                  attempt(() => s.replace('a', '$&'.repeat(1024))),
                ].join('\\n')""");
        assertStringWithJavet("""
                try {
                    ('a' + 'b'.repeat(1000)).replace('a', () => 'c'.repeat(536870888 - 500));
                    'NO ERROR';
                } catch (e) { 'CAUGHT ' + e.name + ': ' + e.message }""");
        assertIntegerWithJavet("'x'.replace('x', () => 'c'.repeat(536870888)).length",
                "'xx'.replaceAll('x', () => 'c'.repeat(268435444)).length");
    }

    @Test
    @Timeout(60)
    public void testStringAdditionBeyondMaximumLengthRaisesRangeError() {
        assertThat(evalToString("""
                try {
                    let s = 'a';
                    for (let i = 0; i < 40; i++) { s = s + s }
                    'NO ERROR';
                } catch (e) { 'CAUGHT ' + e.name }""")).isEqualTo("CAUGHT RangeError");
    }
}
