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

package com.caoccao.qjs4j.test262;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class Test262WorkerShutdownTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testShutdownWaitsForThreadExitAfterPoolTermination(boolean interrupt) throws InterruptedException {
        Test262Runner runner = new Test262Runner(Paths.get("unused"), Test262Config.loadDefault())
                .setWorkerTerminationTimeoutMilliseconds(TimeUnit.SECONDS.toMillis(10));
        DelayedWorkerExit worker = new DelayedWorkerExit();
        Test262Runner.WorkerShutdown[] shutdown = {null};
        Thread waiter = new Thread(() -> shutdown[0] = runner.awaitWorkerTermination(worker.pool, worker.threads),
                "test262-shutdown-waiter");
        waiter.setDaemon(true);
        try {
            worker.terminatePool();
            waiter.start();
            awaitThreadWait(waiter);
            if (interrupt) {
                waiter.interrupt();
                assertThat(worker.cancelled.await(10, TimeUnit.SECONDS)).isTrue();
                awaitThreadWait(waiter);
            }
            worker.releaseExit.countDown();
            waiter.join(TimeUnit.SECONDS.toMillis(10));

            assertThat(waiter.isAlive()).isFalse();
            assertThat(shutdown[0]).isNotNull();
            assertThat(shutdown[0].clean()).isEqualTo(!interrupt);
            assertThat(shutdown[0].abandonedWorkers()).isZero();
            assertThat(worker.threads).allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
        } finally {
            worker.stop();
            waiter.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    @Test
    void testTerminatedPoolWithLiveWorkerReachesShutdownDeadline() throws InterruptedException {
        Test262Runner runner = new Test262Runner(Paths.get("unused"), Test262Config.loadDefault())
                .setWorkerTerminationTimeoutMilliseconds(50);
        DelayedWorkerExit worker = new DelayedWorkerExit();
        try {
            worker.terminatePool();

            Test262Runner.WorkerShutdown shutdown = runner.awaitWorkerTermination(worker.pool, worker.threads);

            assertThat(shutdown.clean()).isFalse();
            assertThat(shutdown.abandonedWorkers()).isEqualTo(2);
        } finally {
            worker.stop();
        }
    }

    private static void awaitThreadWait(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.isAlive() && thread.getState() != Thread.State.TIMED_WAITING
                && System.nanoTime() - deadline < 0) {
            Thread.sleep(1);
        }
        assertThat(thread.getState()).as("shutdown must wait for the live worker even after the pool terminates")
                .isEqualTo(Thread.State.TIMED_WAITING);
    }

    /** Holds workers alive after their executor has signalled termination, making the exit race deterministic. */
    private static final class DelayedWorkerExit {
        private final CountDownLatch cancelled = new CountDownLatch(1);
        private final ThreadPoolExecutor pool;
        private final CountDownLatch reachedExit = new CountDownLatch(2);
        private final CountDownLatch releaseExit = new CountDownLatch(1);
        private final List<Thread> threads = new CopyOnWriteArrayList<>();

        private DelayedWorkerExit() {
            pool = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(() -> {
                    runnable.run();
                    reachedExit.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try {
                            releaseExit.await();
                            break;
                        } catch (InterruptedException ignored) {
                            interrupted = true;
                        }
                    }
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }, Test262Runner.WORKER_THREAD_NAME_PREFIX + "delayed-exit-" + threads.size());
                thread.setDaemon(true);
                threads.add(thread);
                return thread;
            }) {
                @Override
                public List<Runnable> shutdownNow() {
                    List<Runnable> pending = super.shutdownNow();
                    cancelled.countDown();
                    return pending;
                }
            };
        }

        void stop() throws InterruptedException {
            releaseExit.countDown();
            pool.shutdownNow();
            for (Thread thread : threads) {
                thread.join(TimeUnit.SECONDS.toMillis(10));
                assertThat(thread.isAlive()).isFalse();
            }
        }

        void terminatePool() throws InterruptedException {
            pool.prestartAllCoreThreads();
            pool.shutdown();
            assertThat(reachedExit.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(pool.isTerminated()).isTrue();
            assertThat(pool.getActiveCount()).isZero();
            assertThat(threads).hasSize(2).allSatisfy(thread -> assertThat(thread.isAlive()).isTrue());
        }
    }
}
