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
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSRuntimeOptions;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A running match can be stopped by the host, and a pattern may have as many capture groups as V8 allows.
 * <p>
 * The matcher never polled the interrupt flag or the execution deadline, so with the backtracking limit raised or
 * removed nothing could stop a catastrophic match; and capture indices were one byte wide, which capped a pattern at
 * 254 groups where V8 allows 32,767.
 */
public class RegExpInterruptAndCaptureLimitTest extends BaseJavetTest {

    @Test
    public void testCaptureGroupCountMatchesV8() {
        assertStringWithJavet("""
                var p = '';
                for (var i = 0; i < 300; i++) p += '(x)';
                var m = new RegExp(p + '\\\\300').exec('x'.repeat(301));
                m.length + ':' + m[300] + ':' + m.index""", """
                var m = new RegExp('('.repeat(5000) + 'a' + ')'.repeat(5000)).exec('xa');
                m.length + ':' + m[5000]""", """
                var names = [];
                for (var i = 0; i < 400; i++) names.push('(?<g' + i + '>.)');
                var m = new RegExp(names.join('')).exec('y'.repeat(400));
                Object.keys(m.groups).length + ':' + m.groups.g399""", """
                'abcabc'.replace(new RegExp('(a)'.repeat(1) + '(b)(c)' + '()'.repeat(297) + '\\\\1'), '[$1$2$3]')""",
                "/(a)(?:(b)|c)+\\2\\1/.exec('acbba').join()", "/(?<=(\\d+)(\\d+))$/.exec('1053').join()");
        assertBooleanWithJavet("""
                (function () {
                  try { new RegExp('()'.repeat(32768)); return false } catch (e) { return e instanceof SyntaxError }
                })()""");
    }

    @Test
    @Timeout(60)
    public void testDeadlineStopsAMatch() {
        try (JSRuntime runtime = createUnboundedRuntime(); JSContext deadlineContext = runtime.createContext()) {
            deadlineContext.getVirtualMachine().setExecutionDeadline(System.currentTimeMillis() + 200);
            assertThatThrownBy(
                    () -> deadlineContext.eval("try { /(a+)+$/.test('a'.repeat(40) + 'b') } catch (e) { 'SWALLOWED' }"))
                    .isInstanceOf(JSTerminationException.class).hasMessage("execution timeout");
        }
    }

    @Test
    @Timeout(60)
    public void testHostInterruptStopsAMatch() throws InterruptedException {
        try (JSRuntime runtime = createUnboundedRuntime(); JSContext interruptedContext = runtime.createContext()) {
            // Through split, which drives the matcher itself rather than through exec.
            assertHostInterrupt(interruptedContext, "'a'.repeat(40).split(/(a+)+$b/)");
        }
    }

    @Test
    public void testTheProbeIsPolledPeriodically() {
        RegExpBytecode bytecode = new RegExpCompiler(context.getUnicodePropertyResolver()).compile("(a+)+$", "");
        AtomicInteger polls = new AtomicInteger();
        RegExpEngine engine = new RegExpEngine(bytecode, 0);
        assertThat(engine.exec("a".repeat(18) + "b", 0, polls::incrementAndGet)).isNull();
        assertThat(polls.get()).isGreaterThan(0);
        // Lookarounds run on a context of their own, which polls too.
        RegExpEngine lookahead = new RegExpEngine(
                new RegExpCompiler(context.getUnicodePropertyResolver()).compile("(?=(a+)+$)", ""), 0);
        polls.set(0);
        assertThat(lookahead.exec("a".repeat(18) + "b", 0, polls::incrementAndGet)).isNull();
        assertThat(polls.get()).isGreaterThan(0);
        // Without a probe the match runs to the end.
        assertThat(engine.exec("a".repeat(10) + "b", 0)).isNull();
    }

    private static JSRuntime createUnboundedRuntime() {
        return new JSRuntime(new JSRuntimeOptions().setRegExpBacktrackLimit(0));
    }
}
