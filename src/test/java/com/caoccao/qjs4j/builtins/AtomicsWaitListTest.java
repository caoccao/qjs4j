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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two defects in the wait machinery.
 * <p>
 * Wait lists were keyed by {@code System.identityHashCode(bytes) + ":" + offset}. Identity hash
 * codes are not unique, so two live and unrelated {@code SharedArrayBuffer}s could share a wait
 * list and a notify on one would wake — and count — waiters on the other. And an in-flight
 * {@code Atomics.waitAsync} with no timeout held a daemon thread, a promise and that promise's
 * context for the life of the process; closing its runtime changed nothing.
 */
public class AtomicsWaitListTest extends BaseTest {
    private static JSTypedArray sharedInt32Array(JSContext context, int elementCount) {
        JSValue array = context.eval(
                "new Int32Array(new SharedArrayBuffer(" + (elementCount * 4) + "))",
                "atomics.js", false);
        return (JSTypedArray) array;
    }

    @Test
    @Timeout(60)
    public void testCloseCancelsOnlyTheClosingRuntimesWaits() throws InterruptedException {
        AtomicsObject sharedAtomics = new AtomicsObject();
        JSRuntime first = new JSRuntime(new JSRuntimeOptions().setAtomicsObject(sharedAtomics));
        JSRuntime second = new JSRuntime(new JSRuntimeOptions().setAtomicsObject(sharedAtomics));
        try {
            JSContext firstContext = first.createContext();
            JSContext secondContext = second.createContext();
            firstContext.eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                    + "globalThis.r = Atomics.waitAsync(i, 0, 0);", "atomics.js", false);
            secondContext.eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                    + "globalThis.r = Atomics.waitAsync(i, 0, 0);", "atomics.js", false);
            assertThat(sharedAtomics.getPendingAsyncWaitCount(first)).isEqualTo(1);
            assertThat(sharedAtomics.getPendingAsyncWaitCount(second)).isEqualTo(1);

            first.close();
            // Cancellation hands the waiting thread back; give it a moment to unwind.
            for (int attempt = 0; attempt < 100 && sharedAtomics.getPendingAsyncWaitCount(first) > 0; attempt++) {
                Thread.sleep(10);
            }
            assertThat(sharedAtomics.getPendingAsyncWaitCount(first))
                    .as("an infinite waitAsync must not survive its own runtime")
                    .isZero();
            assertThat(sharedAtomics.getPendingAsyncWaitCount(second))
                    .as("another agent's wait in the same cluster is untouched")
                    .isEqualTo(1);
        } finally {
            second.close();
        }
    }

    @Test
    @Timeout(60)
    public void testNotifyGoesToTheSelectedWaiterNotWhoeverArrivesNext() throws InterruptedException {
        // Notifications used to be a shared count, so a waiter that arrived after notify() had
        // chosen its targets could take the lock first and spend the token — leaving the agent the
        // notification was meant for still blocked.
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSTypedArray array = sharedInt32Array(context, 2);
            AtomicsObject atomics = runtime.getOptions().getAtomicsObject();

            CountDownLatch firstWaiterStarted = new CountDownLatch(1);
            String[] firstResult = {null};
            Thread firstWaiter = new Thread(() -> {
                JSContext waiterContext = runtime.createContext();
                firstWaiterStarted.countDown();
                firstResult[0] = atomics.wait(waiterContext, JSUndefined.INSTANCE, new JSValue[]{
                        array, JSNumber.of(0), JSNumber.of(0), JSNumber.of(30000)}).toString();
            }, "first-waiter");
            firstWaiter.start();
            assertThat(firstWaiterStarted.await(10, TimeUnit.SECONDS)).isTrue();
            // Let the waiter actually reach the queue.
            Thread.sleep(200);

            assertThat(atomics.notify(context, JSUndefined.INSTANCE, new JSValue[]{
                    array, JSNumber.of(0), JSNumber.of(1)}).toString()).isEqualTo("1");

            firstWaiter.join(20000);
            assertThat(firstResult[0])
                    .as("the notified waiter must be the one that was waiting")
                    .isEqualTo("ok");
        }
    }

    @Test
    @Timeout(60)
    public void testNotifyOnADifferentIndexOfTheSameBufferDoesNotWake() throws InterruptedException {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSTypedArray array = sharedInt32Array(context, 4);
            AtomicsObject atomics = runtime.getOptions().getAtomicsObject();

            CountDownLatch started = new CountDownLatch(1);
            String[] result = {null};
            Thread waiter = new Thread(() -> {
                JSContext waiterContext = runtime.createContext();
                started.countDown();
                result[0] = atomics.wait(waiterContext, JSUndefined.INSTANCE, new JSValue[]{
                        array, JSNumber.of(0), JSNumber.of(0), JSNumber.of(700)}).toString();
            }, "index-waiter");
            waiter.start();
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);

            assertThat(atomics.notify(context, JSUndefined.INSTANCE, new JSValue[]{
                    array, JSNumber.of(2), JSNumber.of(1)}).toString()).isEqualTo("0");
            waiter.join(20000);
            assertThat(result[0]).isEqualTo("timed-out");
        }
    }

    @Test
    @Timeout(60)
    public void testNotifyOnOneBufferDoesNotWakeAnother() throws InterruptedException {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSTypedArray waited = sharedInt32Array(context, 2);
            JSTypedArray other = sharedInt32Array(context, 2);
            AtomicsObject atomics = runtime.getOptions().getAtomicsObject();

            CountDownLatch started = new CountDownLatch(1);
            String[] result = {null};
            Thread waiter = new Thread(() -> {
                JSContext waiterContext = runtime.createContext();
                started.countDown();
                result[0] = atomics.wait(waiterContext, JSUndefined.INSTANCE, new JSValue[]{
                        waited, JSNumber.of(0), JSNumber.of(0), JSNumber.of(700)}).toString();
            }, "cross-buffer-waiter");
            waiter.start();
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);

            assertThat(atomics.notify(context, JSUndefined.INSTANCE, new JSValue[]{
                    other, JSNumber.of(0), JSNumber.of(1)}).toString())
                    .as("a notify on a different buffer must find nobody")
                    .isEqualTo("0");

            waiter.join(20000);
            assertThat(result[0]).isEqualTo("timed-out");
        }
    }

    @Test
    @Timeout(60)
    public void testNotifyWithNoWaitersReportsZero() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSTypedArray array = sharedInt32Array(context, 2);
            AtomicsObject atomics = runtime.getOptions().getAtomicsObject();
            assertThat(atomics.notify(context, JSUndefined.INSTANCE, new JSValue[]{
                    array, JSNumber.of(0), JSNumber.of(1)}).toString()).isEqualTo("0");
        }
    }

    @Test
    @Timeout(60)
    public void testWaitAsyncSettlesOnNotify() throws InterruptedException {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            context.eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                    + "globalThis.r = Atomics.waitAsync(i, 0, 0);"
                    + "globalThis.settled = 'pending';"
                    + "r.value.then(v => { globalThis.settled = v; });", "atomics.js", false);
            assertThat(context.eval("r.async").toString()).isEqualTo("true");

            context.eval("Atomics.notify(i, 0);", "atomics.js", false);
            for (int attempt = 0; attempt < 200; attempt++) {
                context.processMicrotasks();
                if (!"pending".equals(context.eval("String(settled)").toString())) {
                    break;
                }
                Thread.sleep(10);
            }
            assertThat(context.eval("String(settled)").toString()).isEqualTo("ok");
        }
    }
}
