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
import org.junit.jupiter.api.Timeout;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezing has to be a boundary, not a suggestion.
 * <p>
 * Reading a flag and then applying a result are two steps, and an {@code AtomicBoolean} makes each of them visible
 * without making the pair atomic. A worker could read "not frozen", be descheduled while the runner froze the reporter,
 * printed its summary and snapshotted the outcome, and then apply its result into the totals the caller had already
 * been handed — the exact integrity problem freezing exists to prevent, and one a single-threaded test cannot reach.
 * <p>
 * The interleavings are established with latches and thread states rather than with sleeps. A sleep is not evidence of
 * ordering: on a loaded machine the thread whose blocking the test exists to observe can simply fail to be scheduled
 * inside the window, and the assertion then passes for a reason that has nothing to do with the lock — including when
 * the lock has been removed.
 */
public class Test262ReporterFreezeTest {
    @Test
    @Timeout(60)
    void testAWriteAtTheAdmissionBoundaryIsWhollyInsideTheSnapshot() throws InterruptedException {
        // The write is held open exactly where the race lived. freeze() must not be able to
        // complete around it, so the result is wholly included rather than partially applied after
        // the caller has its numbers.
        ContendedReporter reporter = new ContendedReporter();
        Thread writer = new Thread(() -> reporter.recordResult(passingResult()), "reporter-writer");
        writer.start();
        assertThat(reporter.awaitAdmissions(1, 30, TimeUnit.SECONDS)).as("the writer is inside an admission").isTrue();

        Thread freezer = startBlockedFreezer(reporter);

        reporter.releaseAdmissions();
        writer.join(30_000);
        freezer.join(30_000);

        assertThat(reporter.isFrozen()).isTrue();
        assertThat(reporter.getPassed()).as("the in-flight write landed before the freeze, not after it").isEqualTo(1);
        assertThat(reporter.getLateWrites()).isZero();
        assertThat(reporter.getTotalExecuted()).isEqualTo(1);
    }

    @Test
    @Timeout(120)
    void testNoResultIsEverPartiallyAppliedUnderContention() throws InterruptedException {
        // Many writers against one freeze. Every attempt must end up either wholly counted or
        // wholly refused, and the totals must not move once freeze() has returned.
        //
        // The overlap is established, not hoped for. An earlier version signalled after each
        // writer's first write had *completed*, which proves only that a write happened — not that
        // any writer was still inside an admission when the freeze began. A scheduler that let the
        // workers run their remaining loops to completion before returning to the test thread
        // produced a freeze after all contention was over, and the assertions passed anyway,
        // because zero late writes still satisfies "counted or refused". Here every writer is held
        // *inside* its first admission until all of them are, and freeze() is then observed to park
        // behind them.
        int writerCount = 8;
        int resultsPerWriter = 2_000;
        ContendedReporter reporter = new ContendedReporter();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger attempted = new AtomicInteger();
        List<Thread> writers = new ArrayList<>();
        for (int writerIndex = 0; writerIndex < writerCount; writerIndex++) {
            Thread writer = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int index = 0; index < resultsPerWriter; index++) {
                    reporter.recordResult(passingResult());
                    attempted.incrementAndGet();
                }
            }, "reporter-writer-" + writerIndex);
            writers.add(writer);
            writer.start();
        }
        start.countDown();
        assertThat(reporter.awaitAdmissions(writerCount, 60, TimeUnit.SECONDS))
                .as("every writer is inside an admission, not merely past one").isTrue();

        Thread freezer = startBlockedFreezer(reporter);

        reporter.releaseAdmissions();
        freezer.join(60_000);
        assertThat(reporter.isFrozen()).isTrue();
        int executedAtFreeze = reporter.getTotalExecuted();

        // Deterministic proof that the boundary refuses: this attempt is made after freeze() has
        // demonstrably returned, so it must be refused rather than counted.
        reporter.recordResult(passingResult());
        attempted.incrementAndGet();
        assertThat(reporter.getLateWrites()).as("a write after the boundary is refused").isPositive();

        for (Thread writer : writers) {
            writer.join(60_000);
        }

        assertThat(reporter.getTotalExecuted()).as("nothing lands after freeze() returns").isEqualTo(executedAtFreeze);
        assertThat(reporter.getPassed())
                .as("every held admission landed before the freeze, so the freeze was mid-flight")
                .isGreaterThanOrEqualTo(writerCount);
        assertThat(reporter.getPassed() + reporter.getLateWrites())
                .as("every attempt was either counted or refused, and none was both or neither")
                .isEqualTo(attempted.get());
    }

    @Test
    @Timeout(60)
    void testResetReopensTheReporter() {
        Test262Reporter reporter = new Test262Reporter();
        reporter.recordResult(passingResult());
        reporter.freeze();
        reporter.recordResult(passingResult());
        assertThat(reporter.getPassed()).isEqualTo(1);
        assertThat(reporter.getLateWrites()).isEqualTo(1);

        reporter.reset();
        assertThat(reporter.isFrozen()).isFalse();
        assertThat(reporter.getLateWrites()).isZero();
        reporter.recordResult(passingResult());
        assertThat(reporter.getPassed()).isEqualTo(1);
    }

    /**
     * Block until a thread has parked, which for a thread whose only blocking call is a lock acquisition is proof that
     * it reached that acquisition and could not complete it.
     *
     * @param thread
     *            the thread to watch
     */
    private static void awaitParked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            if (state == Thread.State.TERMINATED) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError(thread.getName() + " never parked; state is " + thread.getState());
    }

    private static TestResult passingResult() {
        return TestResult.pass(new Test262TestCase(Paths.get("concurrent.js")));
    }

    private static Thread startBlockedFreezer(Test262Reporter reporter) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        Thread freezer = new Thread(() -> {
            started.countDown();
            reporter.freeze();
        }, "reporter-freezer");
        freezer.start();
        assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
        // Its only blocking call is the write lock. Parking proves freeze reached that lock.
        awaitParked(freezer);
        assertThat(freezer.isAlive()).as("freeze is blocked behind the active writers").isTrue();
        return freezer;
    }

    /**
     * A reporter that holds each thread inside its <em>first</em> admission until released.
     * <p>
     * Holding the first admission of every writer, rather than any {@code n} admissions, is what makes "all eight are
     * contending" true rather than "one fast writer went round eight times".
     */
    private static final class ContendedReporter extends Test262Reporter {
        private final Set<Thread> heldThreads = ConcurrentHashMap.newKeySet();
        private final Semaphore inAdmission = new Semaphore(0);
        private final CountDownLatch release = new CountDownLatch(1);

        /**
         * Block until the given number of distinct threads are inside an admission.
         *
         * @param count
         *            how many admissions to wait for
         * @param timeout
         *            how long to wait
         * @param unit
         *            the timeout's unit
         * @return true when they all arrived
         */
        private boolean awaitAdmissions(int count, long timeout, TimeUnit unit) throws InterruptedException {
            return inAdmission.tryAcquire(count, timeout, unit);
        }

        @Override
        protected void onAdmitting() {
            if (!heldThreads.add(Thread.currentThread())) {
                return;
            }
            inAdmission.release();
            try {
                release.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private void releaseAdmissions() {
            release.countDown();
        }
    }
}
