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

package com.caoccao.qjs4j.core.promise;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The promise reject callback sees the events V8's does, in the same order: HostPromiseRejectionTracker.
 * <p>
 * The callback used to fire only when an {@code await} found a rejected promise — that is, exactly when a handler
 * existed — and never for a rejection that nothing handled, so an embedder tracking unhandled rejections saw false
 * positives from every caught {@code await} and nothing for {@code Promise.reject(x)}. Each case runs in V8 through
 * Javet's own promise reject callback and in qjs4j, and the two event sequences are compared.
 */
public class JSPromiseRejectionTrackingTest extends BaseJavetTest {
    private void assertRejectEventsWithJavet(String... codeArray) throws JavetException {
        for (String code : codeArray) {
            List<String> v8Events = new ArrayList<>();
            v8Runtime.resetContext();
            v8Runtime.setPromiseRejectCallback((event, promise, value) -> v8Events.add(event.getName()));
            v8Runtime.getExecutor(code).executeVoid();
            v8Runtime.await();

            List<String> qjs4jEvents = new ArrayList<>();
            JSContext qjs4jContext = resetContext();
            qjs4jContext.setPromiseRejectCallback((event, promise, reason) -> qjs4jEvents.add(event.name()));
            qjs4jContext.eval(code);
            qjs4jContext.processMicrotasks();

            assertThat(qjs4jEvents).as(code).isEqualTo(v8Events);
        }
    }

    @Test
    public void testAsyncFunctionsAndGenerators() throws JavetException {
        assertRejectEventsWithJavet("(async () => { throw new Error('async throw') })()",
                "(async () => { await null; throw new Error('late') })()",
                "(async () => { try { await Promise.reject(3) } catch (e) {} })()",
                "(async () => { const p = Promise.reject(1); await null; try { await p } catch {} })()",
                "(async () => { return Promise.reject(20) })().catch(() => {})",
                "(async function* () { throw new Error('agen') })().next()",
                "(async () => { for await (const x of [Promise.reject(13)]) {} })().catch(() => {})",
                // return() on a finished async generator awaits the value it is handed.
                """
                        (async () => {
                          async function* g() {}
                          const it = g();
                          await it.next();
                          await it.return(Promise.reject(26)).catch(() => {});
                        })()""", """
                        (async () => {
                          async function* g() {}
                          const it = g();
                          await it.next();
                          const p = Promise.resolve(1);
                          Object.defineProperty(p, 'constructor', { get() { throw new Error('c') } });
                          await it.return(p).catch(() => {});
                        })()""", """
                        (async function* () { try { yield 1 } finally { await Promise.reject(24) } })()
                          .return()
                          .catch(() => {})""");
    }

    @Test
    public void testAYieldedRejectedPromiseIsHandledByTheYield() {
        // A rejected promise an async generator yields is consumed by the yield; it must not stay
        // reported as unhandled.
        java.util.Map<JSPromise, JSValue> unhandled = new java.util.LinkedHashMap<>();
        context.setPromiseRejectCallback((event, promise, reason) -> {
            if (event == PromiseRejectEvent.PromiseRejectWithNoHandler) {
                unhandled.put(promise, reason);
            } else {
                unhandled.remove(promise);
            }
        });
        context.eval("(async function* () { yield Promise.reject(12) })().next().catch(() => {})");
        context.processMicrotasks();
        assertThat(unhandled).isEmpty();
    }

    @Test
    public void testCombinatorsAndThenables() throws JavetException {
        assertRejectEventsWithJavet("Promise.all([Promise.reject(5)]).catch(() => {})",
                "Promise.all([Promise.reject(6)])", "Promise.allSettled([Promise.reject(7)])",
                "Promise.any([Promise.reject(8)]).catch(() => {})", "Promise.race([Promise.reject(9)]).catch(() => {})",
                "new Promise((_, rej) => rej(10)).finally(() => {})",
                "Promise.resolve({ then(_, rej) { rej(15) } }).catch(() => {})",
                "Promise.resolve({ then(_, rej) { rej(16) } })", "Promise.resolve().then(() => { throw 22 })",
                "Promise.resolve().then(() => { throw 23 }).catch(() => {})");
    }

    @Test
    public void testHandlersAttachedBeforeAndAfterTheRejection() throws JavetException {
        assertRejectEventsWithJavet("Promise.reject(new Error('a'))", "Promise.reject(1).catch(() => {})",
                "const p = Promise.reject(2); p.then(() => {}); p.catch(() => {})", "Promise.reject(4).then(v => v)",
                "Promise.reject(18).then(null, () => {})",
                "let reject; const p = new Promise((_, r) => { reject = r }); p.catch(() => {}); reject(1)",
                "let reject; const p = new Promise((_, r) => { reject = r }); reject(1); p.catch(() => {})",
                "let reject; new Promise((_, r) => { reject = r }); reject(1)");
    }

    @Test
    public void testIsHandledFollowsThePromiseLifecycle() {
        JSPromise promise = context.createJSPromise();
        assertThat(promise.isHandled()).isFalse();
        List<PromiseRejectEvent> events = new ArrayList<>();
        context.setPromiseRejectCallback((event, rejected, reason) -> events.add(event));
        promise.reject(new JSString("r"));
        assertThat(events).containsExactly(PromiseRejectEvent.PromiseRejectWithNoHandler);
        promise.markHandled();
        promise.markHandled();
        assertThat(promise.isHandled()).isTrue();
        assertThat(events).as("a second mark reports nothing").containsExactly(
                PromiseRejectEvent.PromiseRejectWithNoHandler, PromiseRejectEvent.PromiseHandlerAddedAfterReject);

        JSPromise fulfilled = context.createJSPromise();
        fulfilled.fulfill(JSNumber.of(1));
        fulfilled.markHandled();
        assertThat(fulfilled.isHandled()).isTrue();
        assertThat(events).as("a fulfilled promise reports nothing").hasSize(2);

        context.setPromiseRejectCallback(null);
        JSPromise silent = context.createJSPromise();
        silent.reject(new JSString("no callback"));
        silent.markHandled();
        assertThat(silent.isHandled()).isTrue();
    }

    @Test
    public void testReactionsAndSettlementFromJava() {
        List<String> seen = new ArrayList<>();
        JSNativeFunction record = new JSNativeFunction(context, "record", 1, (callContext, thisArg, args) -> {
            seen.add(args[0].toString());
            return JSUndefined.INSTANCE;
        });
        // A pending promise keeps only the reactions it is given.
        JSPromise pending = context.createJSPromise();
        pending.addReactions(new JSPromise.ReactionRecord(record, null, context), null);
        pending.addReactions(null, new JSPromise.ReactionRecord(record, null, context));
        pending.fulfill(JSNumber.of(1));
        // Settling again changes nothing.
        pending.fulfill(JSNumber.of(2));
        pending.reject(new JSString("late"));
        // A settled promise runs a matching reaction at once and ignores a missing one.
        pending.addReactions(null, null);
        pending.addReactions(new JSPromise.ReactionRecord(record, null, context), null);
        JSPromise rejected = context.createJSPromise();
        rejected.reject(new JSString("no"));
        rejected.reject(new JSString("again"));
        rejected.addReactions(new JSPromise.ReactionRecord(record, null, context), null);
        rejected.addReactions(null, new JSPromise.ReactionRecord(record, null, context));
        context.processMicrotasks();
        assertThat(seen).containsExactly("1", "1", "no");
        assertThat(pending.getResult().toString()).isEqualTo("1");
        assertThat(rejected.getResult().toString()).isEqualTo("no");
    }

    @Test
    public void testResolvingFunctionsCalledAfterResolution() throws JavetException {
        assertRejectEventsWithJavet("new Promise((res, rej) => { res(1); rej(2) })",
                "new Promise((res, rej) => { res(1); res(2) })",
                "new Promise((res, rej) => { rej(1); rej(2) }).catch(() => {})",
                "new Promise((res, rej) => { rej(1); res(2) }).catch(() => {})",
                "new Promise((res, rej) => { res(1); throw new Error('after') })",
                "const { promise, resolve, reject } = Promise.withResolvers(); resolve(1); reject(2)");
    }
}
