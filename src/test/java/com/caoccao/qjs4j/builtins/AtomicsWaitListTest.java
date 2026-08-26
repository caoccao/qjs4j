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
    public void testAnInjectedClusterObjectOutlivesEveryMemberRuntime() {
        // The embedder owns an injected instance. No member may close it, however many of them
        // shut down, and it stays usable until its owner closes it.
        AtomicsObject clusterAtomics = new AtomicsObject();
        JSRuntimeOptions options = new JSRuntimeOptions().setAtomicsObject(clusterAtomics);
        assertThat(options.isAtomicsObjectInjected()).isTrue();
        JSRuntime first = new JSRuntime(options);
        JSRuntime second = new JSRuntime(options);
        assertThat(first.getAtomicsObject()).isSameAs(clusterAtomics);
        assertThat(second.getAtomicsObject()).isSameAs(clusterAtomics);
        first.close();
        assertThat(clusterAtomics.isWaitExecutorTerminated()).isFalse();
        second.close();
        assertThat(clusterAtomics.isWaitExecutorTerminated())
                .as("the last member still does not own it")
                .isFalse();
        clusterAtomics.close();
        assertThat(clusterAtomics.isWaitExecutorTerminated()).isTrue();
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
    public void testContextsCreatedEitherSideOfAnOptionsMutationShareOneAtomicsObject() {
        // Two contexts of one runtime have to coordinate with each other. Binding the global
        // Atomics functions through the live options object meant a mutation between the two
        // createContext() calls put them on different objects, and a wait in one was invisible to
        // a notify in the other.
        JSRuntimeOptions options = new JSRuntimeOptions();
        AtomicsObject replacement = new AtomicsObject();
        try (JSRuntime runtime = new JSRuntime(options)) {
            JSContext before = runtime.createContext();
            options.setAtomicsObject(replacement);
            JSContext after = runtime.createContext();

            JSTypedArray array = sharedInt32Array(before, 2);
            before.eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                    + "globalThis.r = Atomics.waitAsync(i, 0, 0);", "atomics.js", false);
            assertThat(runtime.getAtomicsObject().getPendingAsyncWaitCount(runtime)).isEqualTo(1);

            // The second context's Atomics has to be able to see the first context's waiters.
            AtomicsObject.WaitRegistration registration =
                    runtime.getAtomicsObject().registerWaiter(array, 0);
            try {
                assertThat(runtime.getAtomicsObject().findWaitList(array, 0))
                        .isSameAs(registration.waitList());
                after.getGlobalObject().set("sharedArray", array);
                assertThat(after.eval("Atomics.notify(sharedArray, 0, 1)", "atomics.js", false).toString())
                        .as("the second context's Atomics.notify reaches the first context's waiter")
                        .isEqualTo("1");
            } finally {
                registration.waitList().cancel(registration.waiter());
                runtime.getAtomicsObject().releaseWaitList(registration);
            }
        } finally {
            replacement.close();
        }
    }

    @Test
    @Timeout(60)
    public void testDefaultRuntimeClosesTheAtomicsObjectItCreated() {
        // Every default JSRuntimeOptions builds its own AtomicsObject, and that object owns a
        // cached thread pool. Closing the runtime cancelled its waits but left the executor
        // running, so `try (JSRuntime runtime = new JSRuntime())` did not release everything the
        // runtime caused to exist — and a worker survived for the pool's 60-second idle timeout.
        JSRuntime runtime = new JSRuntime();
        AtomicsObject atomics = runtime.getAtomicsObject();
        JSContext context = runtime.createContext();
        context.eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                + "globalThis.r = Atomics.waitAsync(i, 0, 0);", "atomics.js", false);
        assertThat(atomics.getPendingAsyncWaitCount(runtime)).isEqualTo(1);
        assertThat(atomics.isWaitExecutorTerminated()).isFalse();

        runtime.close();

        assertThat(atomics.isWaitExecutorTerminated())
                .as("a runtime that created its own Atomics object closes it")
                .isTrue();
        assertThat(atomics.getPendingAsyncWaitCount(runtime)).isZero();
    }

    @Test
    @Timeout(60)
    public void testInjectedAtomicsObjectOutlivesTheRuntimesThatShareIt() {
        // An injected object belongs to an agent cluster, so no single member may shut it down.
        try (AtomicsObject clusterAtomics = new AtomicsObject()) {
            JSRuntime first = new JSRuntime(new JSRuntimeOptions().setAtomicsObject(clusterAtomics));
            try (JSRuntime second = new JSRuntime(new JSRuntimeOptions().setAtomicsObject(clusterAtomics))) {
                first.createContext().eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                        + "globalThis.r = Atomics.waitAsync(i, 0, 0);", "atomics.js", false);
                second.createContext().eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                        + "globalThis.r = Atomics.waitAsync(i, 0, 0);", "atomics.js", false);

                first.close();

                assertThat(clusterAtomics.isWaitExecutorTerminated())
                        .as("a shared cluster object is not closed by one of its members")
                        .isFalse();
                assertThat(clusterAtomics.getPendingAsyncWaitCount(second)).isEqualTo(1);
            }
        }
    }

    @Test
    @Timeout(60)
    public void testMutatingOptionsAfterConstructionCannotReachAnExistingRuntime() {
        // The runtime read its AtomicsObject out of the mutable options object again at shutdown,
        // so replacing it afterwards closed the replacement — which the runtime had never used —
        // and leaked the one it had.
        JSRuntimeOptions options = new JSRuntimeOptions();
        JSRuntime runtime = new JSRuntime(options);
        AtomicsObject used = runtime.getAtomicsObject();
        AtomicsObject replacement = new AtomicsObject();
        try {
            options.setAtomicsObject(replacement);
            assertThat(runtime.getAtomicsObject()).isSameAs(used);
            runtime.close();
            assertThat(used.isWaitExecutorTerminated())
                    .as("the runtime closes the instance it actually used")
                    .isTrue();
            assertThat(replacement.isWaitExecutorTerminated())
                    .as("an instance the runtime never used is not its to close")
                    .isFalse();
        } finally {
            replacement.close();
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
            AtomicsObject atomics = runtime.getAtomicsObject();

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
            AtomicsObject atomics = runtime.getAtomicsObject();

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
            AtomicsObject atomics = runtime.getAtomicsObject();

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
            AtomicsObject atomics = runtime.getAtomicsObject();
            assertThat(atomics.notify(context, JSUndefined.INSTANCE, new JSValue[]{
                    array, JSNumber.of(0), JSNumber.of(1)}).toString()).isEqualTo("0");
        }
    }

    @Test
    @Timeout(60)
    public void testOneOptionsObjectCanBeReusedRepeatedly() {
        // Ownership used to be a one-shot claim, so the third, fourth and fiftieth runtime built
        // from one options object all ran on an executor none of them owned.
        JSRuntimeOptions options = new JSRuntimeOptions();
        AtomicsObject[] instances = new AtomicsObject[8];
        for (int index = 0; index < instances.length; index++) {
            try (JSRuntime runtime = new JSRuntime(options)) {
                instances[index] = runtime.getAtomicsObject();
                runtime.createContext().eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                        + "globalThis.r = Atomics.waitAsync(i, 0, 0);", "atomics.js", false);
            }
            assertThat(instances[index].isWaitExecutorTerminated())
                    .as("runtime " + index + " owns and closes its own instance")
                    .isTrue();
        }
        for (int index = 1; index < instances.length; index++) {
            assertThat(instances[index]).isNotSameAs(instances[index - 1]);
        }
    }

    @Test
    @Timeout(60)
    public void testOneOptionsObjectGivesEveryRuntimeItsOwnAtomicsObject() {
        // Options that inject nothing describe "one per runtime". They used to build a single
        // instance eagerly and hand it to whichever runtime claimed it first, which left every
        // later runtime using an object it did not own — so closing the first took Atomics.waitAsync
        // away from runtimes that were still running.
        JSRuntimeOptions options = new JSRuntimeOptions();
        assertThat(options.isAtomicsObjectInjected()).isFalse();
        assertThat(options.getAtomicsObject()).isNull();
        JSRuntime first = new JSRuntime(options);
        JSRuntime second = new JSRuntime(options);
        try {
            assertThat(first.getAtomicsObject()).isNotSameAs(second.getAtomicsObject());
            first.close();
            assertThat(first.getAtomicsObject().isWaitExecutorTerminated()).isTrue();
            assertThat(second.getAtomicsObject().isWaitExecutorTerminated())
                    .as("a runtime that is still open keeps its own executor")
                    .isFalse();
            // And it still works, which is the part an embedder would actually notice.
            second.createContext().eval("globalThis.i = new Int32Array(new SharedArrayBuffer(8));"
                    + "globalThis.r = Atomics.waitAsync(i, 0, 0);", "atomics.js", false);
            assertThat(second.getAtomicsObject().getPendingAsyncWaitCount(second)).isEqualTo(1);
        } finally {
            second.close();
        }
        assertThat(second.getAtomicsObject().isWaitExecutorTerminated()).isTrue();
    }

    @Test
    @Timeout(60)
    public void testReclaimedWaitListRefusesALateRegistration() {
        // The exact interleaving from the review, played out one step at a time: an agent obtains
        // the wait list, the last old waiter leaves and the list is reclaimed, and only then does
        // the agent try to join. Joining the reclaimed list would put it somewhere Atomics.notify
        // can no longer look, so it must be refused and the agent given the live list instead.
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSTypedArray array = sharedInt32Array(context, 2);
            AtomicsObject atomics = runtime.getAtomicsObject();

            AtomicsObject.WaitRegistration first = atomics.registerWaiter(array, 0);
            assertThat(atomics.findWaitList(array, 0)).isSameAs(first.waitList());

            // The last old waiter leaves, and the empty list is dropped from the lookup.
            first.waitList().cancel(first.waiter());
            atomics.releaseWaitList(first);
            assertThat(atomics.findWaitList(array, 0)).isNull();

            assertThat(first.waitList().registerIfLive())
                    .as("a list that is no longer reachable must not accept a waiter")
                    .isNull();

            AtomicsObject.WaitRegistration second = atomics.registerWaiter(array, 0);
            assertThat(second.waitList())
                    .as("the late registration lands on the list that is actually in the lookup")
                    .isNotSameAs(first.waitList());
            assertThat(atomics.findWaitList(array, 0)).isSameAs(second.waitList());
            assertThat(second.waitList().notifyWaiters(1))
                    .as("and Atomics.notify can therefore find it")
                    .isEqualTo(1);
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

    @Test
    @Timeout(60)
    public void testWaitListWithAWaiterIsNotReclaimed() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSTypedArray array = sharedInt32Array(context, 2);
            AtomicsObject atomics = runtime.getAtomicsObject();

            AtomicsObject.WaitRegistration first = atomics.registerWaiter(array, 0);
            AtomicsObject.WaitRegistration second = atomics.registerWaiter(array, 0);
            assertThat(second.waitList()).isSameAs(first.waitList());

            // One of two waiters leaves: the list still has an occupant, so it stays reachable.
            first.waitList().cancel(first.waiter());
            atomics.releaseWaitList(first);
            assertThat(atomics.findWaitList(array, 0)).isSameAs(first.waitList());
            assertThat(first.waitList().registerIfLive()).isNotNull();

            assertThat(second.waitList().notifyWaiters(2)).isEqualTo(2);
        }
    }

    @Test
    @Timeout(60)
    public void testWaitListsOfDifferentOffsetsAreIndependent() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSTypedArray array = sharedInt32Array(context, 4);
            AtomicsObject atomics = runtime.getAtomicsObject();

            AtomicsObject.WaitRegistration atZero = atomics.registerWaiter(array, 0);
            AtomicsObject.WaitRegistration atTwo = atomics.registerWaiter(array, 2);
            assertThat(atTwo.waitList()).isNotSameAs(atZero.waitList());

            atZero.waitList().cancel(atZero.waiter());
            atomics.releaseWaitList(atZero);
            assertThat(atomics.findWaitList(array, 0)).isNull();
            assertThat(atomics.findWaitList(array, 2))
                    .as("reclaiming one location must not disturb another")
                    .isSameAs(atTwo.waitList());
        }
    }
}
