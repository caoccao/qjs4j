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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezing has to be a boundary, not a suggestion.
 * <p>
 * Reading a flag and then applying a result are two steps, and an {@code AtomicBoolean} makes each
 * of them visible without making the pair atomic. A worker could read "not frozen", be descheduled
 * while the runner froze the reporter, printed its summary and snapshotted the outcome, and then
 * apply its result into the totals the caller had already been handed — the exact integrity problem
 * freezing exists to prevent, and one a single-threaded test cannot reach.
 * <p>
 * The interleavings are established with latches and thread states rather than with sleeps. A sleep
 * is not evidence of ordering: on a loaded machine the thread whose blocking the test exists to
 * observe can simply fail to be scheduled inside the window, and the assertion then passes for a
 * reason that has nothing to do with the lock — including when the lock has been removed.
 */
public class Test262ReporterFreezeTest {
    /**
     * Block until a thread has parked, which for a thread whose only blocking call is a lock
     * acquisition is proof that it reached that acquisition and could not complete it.
     *
     * @param thread the thread to watch
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

    @Test
    @Timeout(60)
    void testAWriteAtTheAdmissionBoundaryIsWhollyInsideTheSnapshot() throws InterruptedException {
        // The write is held open exactly where the race lived. freeze() must not be able to
        // complete around it, so the result is wholly included rather than partially applied after
        // the caller has its numbers.
        PausableReporter reporter = new PausableReporter();
        Thread writer = new Thread(() -> reporter.recordResult(passingResult()), "reporter-writer");
        writer.start();
        assertThat(reporter.admitted.await(30, TimeUnit.SECONDS))
                .as("the writer is inside an admission")
                .isTrue();

        AtomicBoolean frozeEarly = new AtomicBoolean(true);
        CountDownLatch freezerAtGate = new CountDownLatch(1);
        Thread freezer = new Thread(() -> {
            freezerAtGate.countDown();
            reporter.freeze();
            frozeEarly.set(false);
        }, "reporter-freezer");
        freezer.start();

        // Not "wait a while and hope": wait until the freezer has reached freeze() and parked in
        // it. Its only blocking call is the write lock, so a parked freezer is a blocked freeze().
        assertThat(freezerAtGate.await(30, TimeUnit.SECONDS)).isTrue();
        awaitParked(freezer);
        assertThat(freezer.getState())
                .as("freeze() is blocked, not merely unscheduled")
                .isNotEqualTo(Thread.State.TERMINATED);
        assertThat(frozeEarly.get())
                .as("freeze() cannot complete while a result is being admitted")
                .isTrue();

        reporter.release.countDown();
        writer.join(30_000);
        freezer.join(30_000);

        assertThat(reporter.isFrozen()).isTrue();
        assertThat(reporter.getPassed())
                .as("the in-flight write landed before the freeze, not after it")
                .isEqualTo(1);
        assertThat(reporter.getLateWrites()).isZero();
        assertThat(reporter.getTotalExecuted()).isEqualTo(1);
    }

    @Test
    @Timeout(120)
    void testNoResultIsEverPartiallyAppliedUnderContention() throws InterruptedException {
        // Many writers against one freeze. Every attempt must end up either wholly counted or
        // wholly refused, and the totals must not move once freeze() has returned.
        Test262Reporter reporter = new Test262Reporter();
        int writerCount = 8;
        int resultsPerWriter = 2_000;
        CountDownLatch start = new CountDownLatch(1);
        // Every writer signals that it has been through an admission, so the freeze below is known
        // to land in the middle of live contention rather than before it or after it.
        CountDownLatch everyWriterAdmitted = new CountDownLatch(writerCount);
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
                    if (index == 0) {
                        everyWriterAdmitted.countDown();
                    }
                }
            }, "reporter-writer-" + writerIndex);
            writers.add(writer);
            writer.start();
        }
        start.countDown();
        assertThat(everyWriterAdmitted.await(60, TimeUnit.SECONDS))
                .as("every writer is contending before the freeze")
                .isTrue();
        reporter.freeze();

        int executedAtFreeze = reporter.getTotalExecuted();
        for (Thread writer : writers) {
            writer.join(60_000);
        }

        assertThat(reporter.getTotalExecuted())
                .as("nothing lands after freeze() returns")
                .isEqualTo(executedAtFreeze);
        assertThat(reporter.getPassed())
                .as("the freeze happened after real writes, not before any of them")
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
     * A reporter that can be held open at the moment a result is being admitted.
     */
    private static final class PausableReporter extends Test262Reporter {
        private final CountDownLatch admitted = new CountDownLatch(1);
        private final AtomicBoolean pauseNext = new AtomicBoolean(true);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        protected void onAdmitting() {
            if (!pauseNext.compareAndSet(true, false)) {
                return;
            }
            admitted.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
