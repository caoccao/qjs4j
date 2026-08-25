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
 * Three limits are covered: a backtracking budget for the regular expression engine, a maximum
 * string length, and a host interrupt path for a running evaluation. Before these existed,
 * {@code /(a+)+$/} against 40 characters hung the calling thread, {@code 'a'.repeat(1e9)} raised
 * {@code OutOfMemoryError} instead of {@code RangeError}, and {@code while (true) {}} could only be
 * stopped by killing the process.
 * <p>
 * Everything a limit does <em>not</em> change is asserted against V8, so the guards cannot quietly
 * alter ordinary behaviour, and so is every oversized input both engines reject. Three groups are
 * deliberately not compared, each because the divergence is the feature:
 * <ul>
 * <li>The ReDoS step budget. V8 runs {@code /(a+)+$/} against 64 characters to completion; qjs4j
 * stops it. Bounding it is the whole point.</li>
 * <li>The exact string-length boundary. V8's {@code String::kMaxLength} is
 * {@code (1 << 29) - 24}; qjs4j's is {@code (1 << 27) - 1}, four times lower, because a Java
 * {@code String} is two bytes per character unconditionally — V8's ceiling would put a single
 * string over a gigabyte of heap. Both engines reject anything past their own ceiling, which is
 * what the shared assertions below check.</li>
 * <li>The host interrupt and execution deadline, which are Java API with no JavaScript surface.</li>
 * </ul>
 */
public class JSResourceLimitTest extends BaseJavetTest {

    private String evalToString(String code) {
        return JSTypeConversions.toString(context, context.eval(code)).value();
    }

    // -----------------------------------------------------------------------------------
    // C-6a: regular expression backtracking budget.
    // Not compared with V8: V8 completes these matches, and stopping them is the feature.
    // -----------------------------------------------------------------------------------

    @Test
    @Timeout(60)
    public void testBacktrackingLimitIsConfigurable() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setRegExpBacktrackLimit(0));
             JSContext unlimitedContext = runtime.createContext()) {
            // 0 disables the limit, restoring the previous unbounded behaviour. A 20-character
            // input is small enough to still finish quickly.
            JSValue result = unlimitedContext.eval(
                    "/(a+)+$/.test('a'.repeat(20) + '!')");
            assertThat(result).isEqualTo(JSBoolean.FALSE);
        }
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setRegExpBacktrackLimit(1000));
             JSContext strictContext = runtime.createContext()) {
            assertThatThrownBy(() -> strictContext.eval("/(a+)+$/.test('a'.repeat(20) + '!')"))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("backtracking limit");
        }
    }

    @Test
    @Timeout(60)
    public void testCatastrophicBacktrackingInsideLookaheadIsBounded() {
        // A lookaround runs on a nested context. It must inherit the remaining budget rather than
        // start a fresh one, or a lookaround in a loop resets the budget on every iteration.
        assertThat(evalToString(
                """
                        const subject = 'a'.repeat(64) + '!';
                        try { /(?=(a+)+$)a/.test(subject); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }"""))
                .isEqualTo("CAUGHT RangeError");
    }

    @Test
    @Timeout(60)
    public void testCatastrophicBacktrackingIsBounded() {
        // Without a budget this grows exponentially: 0.20s / 0.28s / 0.58s / 1.65s for 16 / 20 /
        // 22 / 24 characters, and a 40-character input never returns.
        assertThat(evalToString(
                """
                        const subject = 'a'.repeat(64) + '!';
                        try { /(a+)+$/.test(subject); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }"""))
                .isEqualTo("CAUGHT RangeError");
    }

    @Test
    @Timeout(60)
    public void testConcatBeyondMaximumLengthRaisesRangeError() {
        assertThat(evalToString(
                """
                        try {
                            let s = 'a'.repeat(1000);
                            for (let i = 0; i < 40; i++) { s = s.concat(s) }
                            'NO ERROR';
                        } catch (e) { 'CAUGHT ' + e.name }"""))
                .isEqualTo("CAUGHT RangeError");
    }

    // -----------------------------------------------------------------------------------
    // C-6b: maximum string length
    // -----------------------------------------------------------------------------------

    @Test
    @Timeout(60)
    public void testExecutionDeadlineTerminatesAnInfiniteLoop() {
        try (JSRuntime runtime = new JSRuntime(); JSContext deadlineContext = runtime.createContext()) {
            deadlineContext.getVirtualMachine().setExecutionDeadline(System.currentTimeMillis() + 200);
            // The script wraps its own loop in try/catch. The deadline must not be interceptable.
            assertThatThrownBy(() -> deadlineContext.eval("try { while (true) {} } catch (e) { 'SWALLOWED' }"))
                    .isInstanceOf(JSTerminationException.class)
                    .hasMessage("execution timeout");
        }
    }

    @Test
    @Timeout(60)
    public void testHostInterruptTerminatesAnInfiniteLoop() throws InterruptedException {
        try (JSRuntime runtime = new JSRuntime(); JSContext interruptedContext = runtime.createContext()) {
            Thread interrupter = new Thread(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                runtime.requestInterrupt();
            });
            interrupter.start();
            try {
                assertThatThrownBy(() -> interruptedContext.eval("try { while (true) {} } catch (e) { 'SWALLOWED' }"))
                        .isInstanceOf(JSTerminationException.class)
                        .hasMessage("execution interrupted");
            } finally {
                interrupter.join();
            }
        }
    }

    @Test
    @Timeout(60)
    public void testInterruptCanBeClearedAndEvaluationResumes() {
        try (JSRuntime runtime = new JSRuntime(); JSContext resumableContext = runtime.createContext()) {
            String sum = "(function () { let total = 0; for (let i = 0; i < 200000; i++) { total += i } return total })()";
            runtime.requestInterrupt();
            assertThat(runtime.shouldInterrupt()).isTrue();
            assertThatThrownBy(() -> resumableContext.eval(sum))
                    .isInstanceOf(JSTerminationException.class);
            runtime.clearInterrupt();
            assertThat(runtime.shouldInterrupt()).isFalse();
            assertThat(resumableContext.eval(sum)).isEqualTo(JSNumber.of(19999900000L));
        }
    }

    @Test
    @Timeout(60)
    public void testJoinBeyondMaximumLengthRaisesRangeError() {
        assertThat(evalToString(
                """
                        try {
                            new Array(1000000).fill('a'.repeat(1000)).join('');
                            'NO ERROR';
                        } catch (e) { 'CAUGHT ' + e.name }"""))
                .isEqualTo("CAUGHT RangeError");
    }

    @Test
    @Timeout(60)
    public void testNoDeadlineLeavesOrdinaryCodeUnaffected() {
        assertThat(context.eval("let total = 0; for (let i = 0; i < 200000; i++) { total += i } total"))
                .isEqualTo(JSNumber.of(19999900000L));
    }

    @Test
    public void testOrdinaryRegularExpressionsAreUnaffected() {
        assertStringWithJavet("/(\\w+)\\s(\\w+)/.exec('John Smith')[2]");
        assertStringWithJavet("'2026-08-25'.replace(/(\\d+)-(\\d+)-(\\d+)/, '$3/$2/$1')");
        assertStringWithJavet("String('aaa'.repeat(2000).match(/a+/)[0].length)");
        assertStringWithJavet("String('x'.repeat(100000).split(/(?=x)/).length)");
    }

    @Test
    public void testOrdinaryStringOperationsAreUnaffected() {
        assertStringWithJavet("'abc'.repeat(3)");
        assertStringWithJavet("'x'.padStart(5, '-')");
        assertStringWithJavet("'x'.padEnd(5, '-')");
        assertStringWithJavet("'a'.concat('b', 'c')");
        assertStringWithJavet("[1, 2, 3].join('-')");
        assertStringWithJavet("'a' + 'b' + 1");
    }

    @Test
    @Timeout(60)
    public void testPadBeyondMaximumLengthRaisesRangeError() {
        assertStringWithJavet("try { 'a'.padStart(1e9); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
        assertStringWithJavet("try { 'a'.padEnd(1e9); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    @Timeout(60)
    public void testRepeatAtTheExactLengthBoundary() {
        // The boundary the division has to get right: exactly MAX_LENGTH is allowed, one more is
        // not. MAX_LENGTH is 2^27 - 1, which is odd, so a two-character string cannot reach it.
        //
        // Not compared with V8: V8's String::kMaxLength is (1 << 29) - 24, four times higher, so V8
        // accepts both of these. A Java String is two bytes per character unconditionally, where
        // V8 uses one for Latin-1, so matching V8's ceiling would put a single string past a
        // gigabyte of heap. The tests above assert the behaviour both engines do share: anything
        // past either ceiling is a RangeError.
        assertThat(evalToString("'ab'.repeat(67108863).length")).isEqualTo("134217726");
        assertThat(evalToString("try { 'ab'.repeat(67108864); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }"))
                .isEqualTo("CAUGHT RangeError");
    }

    // -----------------------------------------------------------------------------------
    // C-6c: host interrupt and execution deadline.
    // Not compared with V8: Java API with no JavaScript surface.
    // -----------------------------------------------------------------------------------

    @Test
    @Timeout(60)
    public void testRepeatBeyondMaximumLengthRaisesRangeError() {
        assertStringWithJavet("try { 'a'.repeat(1e9); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
        assertStringWithJavet("try { 'ab'.repeat(2147483647); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    @Timeout(60)
    public void testRepeatCountBeyondLongRangeRaisesRangeError() {
        // The length guard used to multiply first: (long) 1e20 saturates at Long.MAX_VALUE, so
        // 2 * that wrapped to -2, the guard passed, and `new StringBuilder(-2)` threw
        // NegativeArraySizeException — a Java failure that escaped the script's own catch. The
        // multiplication is now a division, so nothing overflows before the check.
        assertStringWithJavet("try { 'xx'.repeat(1e20); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
        assertStringWithJavet(
                "try { 'xx'.repeat(9223372036854775807); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
        assertStringWithJavet("try { 'abc'.repeat(1e30); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
        assertStringWithJavet(
                "try { 'x'.repeat(Number.MAX_VALUE); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    @Timeout(60)
    public void testRepeatEdgeCountsAreUnaffectedByTheOverflowGuard() {
        // The guard divides by s.length(), so the empty-string and zero-count shortcuts have to
        // come first — dividing by zero would turn a legal call into an engine failure.
        assertStringWithJavet("JSON.stringify(''.repeat(1e20))");
        assertStringWithJavet("JSON.stringify(''.repeat(0))");
        assertStringWithJavet("JSON.stringify('abc'.repeat(0))");
        assertStringWithJavet("try { 'x'.repeat(-1); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
        assertStringWithJavet("try { 'x'.repeat(Infinity); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
        assertStringWithJavet("JSON.stringify('x'.repeat(NaN))");
        assertStringWithJavet("JSON.stringify('x'.repeat())");
        assertStringWithJavet("try { 'x'.repeat(-Infinity); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    @Timeout(60)
    public void testStringAdditionBeyondMaximumLengthRaisesRangeError() {
        assertThat(evalToString(
                """
                        try {
                            let s = 'a';
                            for (let i = 0; i < 40; i++) { s = s + s }
                            'NO ERROR';
                        } catch (e) { 'CAUGHT ' + e.name }"""))
                .isEqualTo("CAUGHT RangeError");
    }
}
