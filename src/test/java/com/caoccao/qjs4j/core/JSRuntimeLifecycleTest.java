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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * {@code JSRuntime.close()} had no closed state, so it was advisory: an embedder could create a
 * context afterwards and evaluate in it, enqueue jobs and drain them, and the global symbol
 * registries kept whatever they held. Closing is now terminal, and every operation is pinned before
 * close, after close, and after a second close.
 * <p>
 * Terminal also has to mean terminal for the two operations the class documents as safe from
 * another thread. Both tested {@code closed} and then mutated as separate steps while {@code close}
 * cleared independently, so a producer could pass the check, be suspended, and deposit its job or
 * its symbol into a runtime that had already finished letting go of them.
 */
public class JSRuntimeLifecycleTest extends BaseTest {
    /**
     * How many times each racing case replays the interleaving.
     */
    private static final int RACE_ROUNDS = 200;

    @Test
    public void testCloseIsIdempotent() {
        JSRuntime runtime = new JSRuntime();
        runtime.createContext();
        runtime.close();
        assertThatCode(runtime::close).doesNotThrowAnyException();
        assertThat(runtime.isClosed()).isTrue();
    }

    @Test
    @Timeout(120)
    public void testCloseNeverLetsAConcurrentEnqueueLandAfterTheClear() throws InterruptedException {
        // Enqueueing from another thread is documented as supported, so a close racing it must
        // still be a terminal boundary: the job is either accepted before the queue is cleared or
        // refused, never deposited into a closed runtime.
        int acceptedAcrossRounds = 0;
        int refusedAcrossRounds = 0;
        for (int round = 0; round < RACE_ROUNDS; round++) {
            JSRuntime runtime = new JSRuntime();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch running = new CountDownLatch(4);
            AtomicInteger accepted = new AtomicInteger();
            AtomicInteger refused = new AtomicInteger();
            List<Thread> producers = new ArrayList<>();
            for (int producerIndex = 0; producerIndex < 4; producerIndex++) {
                Thread producer = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    running.countDown();
                    // Keep going until the runtime is observed closed, so the producers really are
                    // mid-flight across the instant close clears the queue.
                    while (refused.get() < 4) {
                        try {
                            runtime.enqueueJob(() -> {
                            });
                            accepted.incrementAndGet();
                        } catch (IllegalStateException rejected) {
                            refused.incrementAndGet();
                        }
                    }
                }, "job-producer");
                producer.setDaemon(true);
                producers.add(producer);
                producer.start();
            }
            start.countDown();
            assertThat(running.await(30, TimeUnit.SECONDS)).isTrue();
            runtime.close();
            for (Thread producer : producers) {
                producer.join(30_000);
                assertThat(producer.isAlive()).isFalse();
            }
            assertThat(runtime.hasPendingJobs())
                    .as("round " + round + ": nothing may be deposited after close cleared the queue")
                    .isFalse();
            acceptedAcrossRounds += accepted.get();
            refusedAcrossRounds += refused.get();
        }
        // Both outcomes have to occur, or the rounds never straddled the close at all.
        assertThat(acceptedAcrossRounds).as("jobs accepted before close").isPositive();
        assertThat(refusedAcrossRounds).as("jobs refused after close").isPositive();
    }

    @Test
    @Timeout(120)
    public void testCloseNeverLetsAConcurrentGlobalSymbolSurviveTheClear() throws InterruptedException {
        int createdAcrossRounds = 0;
        for (int round = 0; round < RACE_ROUNDS; round++) {
            JSRuntime runtime = new JSRuntime();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch running = new CountDownLatch(4);
            List<JSSymbol> created = java.util.Collections.synchronizedList(new ArrayList<>());
            AtomicInteger refused = new AtomicInteger();
            List<Thread> producers = new ArrayList<>();
            for (int producerIndex = 0; producerIndex < 4; producerIndex++) {
                final int base = producerIndex * 100_000;
                Thread producer = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    running.countDown();
                    int attempt = 0;
                    while (refused.get() < 4) {
                        try {
                            created.add(runtime.getOrCreateGlobalSymbol("key-" + (base + attempt++)));
                        } catch (IllegalStateException rejected) {
                            // Refused because the runtime closed first, which is the other
                            // acceptable outcome.
                            refused.incrementAndGet();
                        }
                    }
                }, "symbol-producer");
                producer.setDaemon(true);
                producers.add(producer);
                producer.start();
            }
            start.countDown();
            assertThat(running.await(30, TimeUnit.SECONDS)).isTrue();
            runtime.close();
            for (Thread producer : producers) {
                producer.join(30_000);
                assertThat(producer.isAlive()).isFalse();
            }
            for (JSSymbol symbol : created) {
                assertThat(runtime.getGlobalSymbolKey(symbol))
                        .as("round " + round + ": no symbol may remain registered after close")
                        .isNull();
            }
            createdAcrossRounds += created.size();
        }
        assertThat(createdAcrossRounds).as("symbols registered before close").isPositive();
    }

    @Test
    public void testClosePropagatesToContexts() {
        JSRuntime runtime = new JSRuntime();
        JSContext context = runtime.createContext();
        assertThat(context.eval("1 + 1").toString()).isEqualTo("2");
        runtime.close();
        assertThatThrownBy(() -> context.eval("1 + 1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testCloseReleasesRuntimeOwnedRegistries() {
        JSRuntime runtime = new JSRuntime();
        JSContext context = runtime.createContext();
        JSSymbol symbol = runtime.getOrCreateGlobalSymbol("shared");
        assertThat(runtime.getGlobalSymbolKey(symbol)).isEqualTo("shared");
        runtime.getAtoms().intern("aDistinctiveInternedName");
        assertThat(runtime.getAtoms().getAtom("aDistinctiveInternedName")).isNotEqualTo(-1);
        assertThat(context).isNotNull();

        runtime.close();
        assertThat(runtime.getGlobalSymbolKey(symbol)).isNull();
        assertThat(runtime.getContexts()).isEmpty();
        assertThat(runtime.getCurrentExecutingContext()).isNull();
        // clear() restores the well-known atoms, so what must be gone is what a script interned.
        assertThat(runtime.getAtoms().getAtom("aDistinctiveInternedName")).isEqualTo(-1);
    }

    @Test
    @Timeout(120)
    public void testCloseWaitsForAContextItIsAboutToOwn() throws InterruptedException {
        // createContext admits under the same lock, so a context created while close is running is
        // either refused or closed along with the rest — never left open and unreachable.
        for (int round = 0; round < 20; round++) {
            JSRuntime runtime = new JSRuntime();
            CountDownLatch start = new CountDownLatch(1);
            List<JSContext> createdContexts = java.util.Collections.synchronizedList(new ArrayList<>());
            Thread producer = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (true) {
                    try {
                        createdContexts.add(runtime.createContext());
                    } catch (IllegalStateException rejected) {
                        // Refused after close, which is the other acceptable outcome.
                        return;
                    }
                }
            }, "context-producer");
            producer.setDaemon(true);
            producer.start();
            start.countDown();
            runtime.close();
            producer.join(30_000);
            assertThat(producer.isAlive()).isFalse();
            for (JSContext createdContext : createdContexts) {
                assertThat(createdContext.isClosed())
                        .as("round " + round + ": a context admitted before the clear is closed with the runtime")
                        .isTrue();
            }
        }
    }

    @Test
    public void testCreateContextIsRejectedAfterClose() {
        JSRuntime runtime = new JSRuntime();
        runtime.close();
        // The review's reproducer: this used to return a working context that evaluated fine.
        assertThatThrownBy(runtime::createContext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    public void testEnqueueJobIsRejectedAfterClose() {
        JSRuntime runtime = new JSRuntime();
        runtime.createContext();
        runtime.close();
        assertThatThrownBy(() -> runtime.enqueueJob(() -> {
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(runtime::runJobs).isInstanceOf(IllegalStateException.class);
        assertThat(runtime.hasPendingJobs()).isFalse();
    }

    @Test
    public void testGlobalSymbolRegistryIsRejectedAfterClose() {
        JSRuntime runtime = new JSRuntime();
        runtime.close();
        assertThatThrownBy(() -> runtime.getOrCreateGlobalSymbol("late"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testOperationsWorkBeforeClose() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            assertThat(runtime.isClosed()).isFalse();
            int[] ran = {0};
            runtime.enqueueJob(() -> ran[0]++);
            assertThat(runtime.hasPendingJobs()).isTrue();
            assertThat(runtime.runJobs()).isEqualTo(1);
            assertThat(ran[0]).isEqualTo(1);
            assertThat(runtime.getOrCreateGlobalSymbol("early")).isNotNull();
            assertThat(context.eval("1 + 1").toString()).isEqualTo("2");
        }
    }

    @Test
    public void testPendingJobsAreDiscardedOnClose() {
        JSRuntime runtime = new JSRuntime();
        runtime.createContext();
        int[] ran = {0};
        runtime.enqueueJob(() -> ran[0]++);
        runtime.close();
        assertThat(runtime.hasPendingJobs()).isFalse();
        assertThat(ran[0]).isZero();
    }
}
