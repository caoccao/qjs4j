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

package com.caoccao.qjs4j.cli;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSNativeFunction;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.PropertyKey;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The command-line host layer's paths a script cannot reach.
 */
public class CommandLineHostTest extends BaseTest {
    @Test
    public void testAnEngineFailureInACallbackIsNotAJavaScriptException() {
        CommandLineHost host = new CommandLineHost(context);
        host.install();
        context.getGlobalObject().set(PropertyKey.fromString("hostBug"),
                new JSNativeFunction(context, "hostBug", 0, (callContext, thisArg, args) -> {
                    throw new JSVirtualMachineException("engine failure");
                }));
        context.eval("setTimeout(hostBug, 1)");
        assertThatThrownBy(() -> host.runTimers(() -> true)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessage("engine failure");
    }

    @Test
    public void testAnInterruptedWaitStopsTheTimers() {
        CommandLineHost host = new CommandLineHost(context);
        host.install();
        context.eval("globalThis.ran = false; setTimeout(() => { ran = true }, 60000)");
        Thread.currentThread().interrupt();
        try {
            assertThat(host.runTimers(() -> true)).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is preserved").isTrue();
        } finally {
            Thread.interrupted();
        }
        assertEvalValue("String(ran)", new JSString("false"));
    }

    @Test
    public void testATerminationTheHostDidNotRaiseIsPassedOn() {
        // Only the termination an uncaught queueMicrotask callback raises becomes that callback's error.
        CommandLineHost host = new CommandLineHost(context);
        host.install();
        context.getRuntime().requestInterrupt();
        try {
            assertThatThrownBy(() -> host.eval("for (;;) {}", "interrupted.js", false))
                    .isInstanceOf(JSTerminationException.class).hasMessage("execution interrupted");
            context.enqueueMicrotask(() -> context.eval("for (;;) {}"));
            assertThatThrownBy(host::runJobs).isInstanceOf(JSTerminationException.class)
                    .hasMessage("execution interrupted");
        } finally {
            context.getRuntime().clearInterrupt();
        }
    }

    @Test
    public void testCancelledTimersAreRemovedImmediately() throws ReflectiveOperationException {
        CommandLineHost host = new CommandLineHost(context);
        host.install();
        context.eval("for(let i=0;i<1000;i++)clearTimeout(setTimeout(()=>{},2147483647,{payload:i}));");
        java.lang.reflect.Field queue = CommandLineHost.class.getDeclaredField("timerQueue");
        queue.setAccessible(true);
        assertThat((java.util.Collection<?>) queue.get(host)).isEmpty();
        assertThat(host.runTimers(() -> true)).isTrue();
        context.eval("let timer=setInterval(()=>clearInterval(timer),1);");
        assertThat(host.runTimers(() -> true)).isTrue();
        assertThat((java.util.Collection<?>) queue.get(host)).isEmpty();
    }

    @Test
    public void testTimersDueTogetherRunInCreationOrder() {
        CommandLineHost host = new CommandLineHost(context, () -> 0L);
        host.install();
        context.eval("""
                globalThis.order = [];
                for (const name of ['a', 'b', 'c']) setTimeout(() => order.push(name), 1);""");
        assertThat(host.runTimers(() -> true)).isTrue();
        assertEvalValue("order.join()", new JSString("a,b,c"));
    }
}
