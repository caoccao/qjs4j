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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Host-initiated termination must escape every boundary that turns a Java exception into a
 * JavaScript value.
 * <p>
 * Termination used to be a boolean flag on {@code JSVirtualMachineException}, a type the engine
 * catches at roughly 170 boundaries. Only a handful of them re-checked the flag, so a promise
 * executor, a {@code .then()} handler, an async function or an async generator silently demoted a
 * host abort to a rejected promise and returned normally to the embedder. Each test below evaluates
 * source ending in {@code 'SURVIVED'}: before the fix every one of them returned that string.
 */
public class JSTerminationSignalTest extends BaseTest {

    /**
     * Install {@code globalThis.abort()}, a host function that terminates execution.
     */
    private void installAbort() {
        JSNativeFunction abort = new JSNativeFunction(
                context,
                "abort",
                0,
                (ctx, thisArg, args) -> {
                    throw new JSTerminationException("host abort");
                });
        context.getGlobalObject().set(PropertyKey.fromString("abort"), abort);
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAnAsyncFunction() {
        installAbort();
        assertThatThrownBy(() -> context.eval("async function f() { abort() } f(); 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAnAsyncFunctionAwaitResumption() {
        installAbort();
        assertThatThrownBy(() -> context.eval(
                "async function f() { await Promise.resolve(1); abort() } f(); 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAnAsyncGenerator() {
        installAbort();
        assertThatThrownBy(() -> context.eval(
                "async function* g() { abort(); yield 1 } g().next(); 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAPromiseExecutor() {
        installAbort();
        assertThatThrownBy(() -> context.eval("new Promise(() => abort()); 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAPromiseReactionHandler() {
        installAbort();
        assertThatThrownBy(() -> context.eval("Promise.resolve(1).then(() => abort()); 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAPromiseRejectionHandler() {
        installAbort();
        assertThatThrownBy(() -> context.eval("Promise.reject(1).catch(() => abort()); 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAThenableResolution() {
        installAbort();
        assertThatThrownBy(() -> context.eval(
                "Promise.resolve({ then() { abort() } }); 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAForOfIterator() {
        installAbort();
        assertThatThrownBy(() -> context.eval(
                """
                        const iterable = { [Symbol.iterator]() { return { next() { abort() } } } };
                        try { for (const value of iterable) {} } catch (e) { 'SWALLOWED' }
                        'SURVIVED'"""))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAScriptCatchBlock() {
        installAbort();
        assertThatThrownBy(() -> context.eval("try { abort() } catch (e) { 'SWALLOWED' } 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAScriptFinallyBlock() {
        installAbort();
        assertThatThrownBy(() -> context.eval(
                "try { abort() } catch (e) { 'SWALLOWED' } finally { } 'SURVIVED'"))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesAUsingDisposal() {
        installAbort();
        assertThatThrownBy(() -> context.eval(
                """
                        function scope() {
                            using resource = { [Symbol.dispose]() { abort() } };
                        }
                        try { scope() } catch (e) { 'SWALLOWED' }
                        'SURVIVED'"""))
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
    }

    @Test
    @Timeout(60)
    public void testTerminationCrossesTheMicrotaskDrain() {
        installAbort();
        context.eval("globalThis.queued = false; Promise.resolve().then(() => { globalThis.queued = true })");
        context.enqueueMicrotask(() -> {
            throw new JSTerminationException("host abort");
        });
        assertThatThrownBy(context::processMicrotasks)
                .isInstanceOf(JSTerminationException.class)
                .hasMessage("host abort");
        // The drain aborted rather than recording the failure and continuing.
        assertThat(context.getMicrotaskFailures()).isEmpty();
    }

    @Test
    @Timeout(60)
    public void testTerminationIsNotRecordedAsAMicrotaskFailure() {
        installAbort();
        assertThatThrownBy(() -> context.eval("Promise.resolve(1).then(() => abort())"))
                .isInstanceOf(JSTerminationException.class);
        assertThat(context.getMicrotaskFailures()).isEmpty();
    }

    @Test
    @Timeout(60)
    public void testAnOrdinaryHostErrorStillBecomesAGuestError() {
        // The complement of every test above: a non-terminating host failure must remain
        // catchable, or making termination an Error would have made every host throw fatal.
        JSNativeFunction fail = new JSNativeFunction(
                context,
                "fail",
                0,
                (ctx, thisArg, args) -> ctx.throwTypeError("host failure"));
        context.getGlobalObject().set(PropertyKey.fromString("fail"), fail);
        assertThat(context.eval("try { fail() } catch (e) { e.name + ': ' + e.message }").toString())
                .isEqualTo("TypeError: host failure");
    }

    @Test
    @Timeout(60)
    public void testAnOrdinaryHostErrorStillRejectsAPromise() {
        JSNativeFunction fail = new JSNativeFunction(
                context,
                "fail",
                0,
                (ctx, thisArg, args) -> ctx.throwTypeError("host failure"));
        context.getGlobalObject().set(PropertyKey.fromString("fail"), fail);
        assertThat(context.eval(
                        """
                                globalThis.outcome = 'PENDING';
                                new Promise(() => fail()).catch((e) => { globalThis.outcome = e.name });
                                'STARTED'""")
                .toString())
                .isEqualTo("STARTED");
        context.processMicrotasks();
        assertThat(context.eval("outcome").toString()).isEqualTo("TypeError");
    }
}
