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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Tracks and reports test262 execution results.
 * <p>
 * <strong>Freezing.</strong> Java interruption is cooperative, so a runner that gives up waiting
 * for its workers cannot promise they have stopped — only that it will stop listening to them.
 * {@link #freeze()} is that boundary: the counts the runner reports afterwards are a snapshot no
 * late worker can change, and the writes those workers attempt are counted rather than applied, so
 * a leaked worker shows up as a number instead of as a total that disagrees with the one already
 * printed.
 */
public class Test262Reporter {
    private static final int TOP_SLOW_TEST_COUNT = 5;

    /**
     * Separates admitting a result from closing the reporter.
     * <p>
     * Recording holds the read lock and freezing holds the write lock, which is what makes
     * admission a transaction rather than two steps. A flag on its own gave each of them their own
     * visibility and nothing more: a worker could read "not frozen", be descheduled, and apply its
     * result after {@code freeze()} had returned and the outcome had been snapshotted — the exact
     * integrity problem freezing exists to prevent. Recording stays concurrent with recording,
     * because the counters and queues underneath are already safe against each other.
     */
    private final ReadWriteLock admissionLock = new ReentrantReadWriteLock();
    private final ConcurrentLinkedQueue<TestResult> allResults = new ConcurrentLinkedQueue<>();
    private final AtomicInteger failed = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<TestResult> failures = new ConcurrentLinkedQueue<>();
    private final AtomicInteger lateWrites = new AtomicInteger(0);
    private final AtomicInteger passed = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    private final AtomicInteger timeout = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<TestResult> timeouts = new ConcurrentLinkedQueue<>();
    /**
     * Guarded by {@link #admissionLock}.
     */
    private boolean frozen;

    /**
     * Stop accepting results, permanently.
     * <p>
     * Called once the runner has stopped waiting for its workers, whether they all finished or some
     * were abandoned. Taking the write lock means every result already being admitted has finished
     * being applied, and none can start afterwards — so from the moment this returns, the counts
     * cannot move and the summary that is printed and the outcome that is returned describe the
     * same run.
     */
    public void freeze() {
        admissionLock.writeLock().lock();
        try {
            frozen = true;
        } finally {
            admissionLock.writeLock().unlock();
        }
    }

    public int getFailed() {
        return failed.get();
    }

    /**
     * How many results arrived after {@link #freeze()} and were therefore discarded.
     * <p>
     * Non-zero means a worker outlived the run. It is diagnostic, not a count of tests: those
     * results were never part of any total.
     *
     * @return the number of discarded results
     */
    public int getLateWrites() {
        return lateWrites.get();
    }

    public int getPassed() {
        return passed.get();
    }

    public int getSkipped() {
        return skipped.get();
    }

    public int getTimeout() {
        return timeout.get();
    }

    public int getTotalExecuted() {
        return passed.get() + failed.get() + timeout.get();
    }

    /**
     * Every interpretation the run accounted for, executed or skipped.
     * <p>
     * One unit throughout: an interpretation, not a file. The runner used to filter by file and
     * record one skip for it while an executed file contributed two results, so this sum added
     * unlike things and could be reconciled with neither the file count nor the interpretation
     * count.
     *
     * @return the number of interpretations executed plus the number skipped
     */
    public int getTotalTests() {
        return getTotalExecuted() + skipped.get();
    }

    /**
     * Whether this reporter has stopped accepting results.
     *
     * @return true once frozen
     */
    public boolean isFrozen() {
        admissionLock.readLock().lock();
        try {
            return frozen;
        } finally {
            admissionLock.readLock().unlock();
        }
    }

    /**
     * Called while a result is being admitted, after the frozen check and before it is applied.
     * <p>
     * A no-op seam so a test can hold a write open at exactly the point the race lived, and observe
     * that a concurrent {@code freeze()} cannot complete around it.
     */
    protected void onAdmitting() {
    }

    public void printProgress() {
        int total = getTotalExecuted();
        int pass = passed.get();
        int fail = failed.get();
        int time = timeout.get();

        System.out.printf("Progress: %d tests executed (%d passed, %d failed, %d timeout)%n",
                total, pass, fail, time);
    }

    public void printSummary() {
        int total = getTotalTests();
        int executed = getTotalExecuted();

        if (!failures.isEmpty()) {
            List<TestResult> sortedFailures = new ArrayList<>(failures.size());
            sortedFailures.addAll(failures);
            sortedFailures.sort(Comparator.comparingInt(r -> r.getTestCase().getIndex()));
            System.out.println("\nFailed Tests:");
            for (TestResult failure : sortedFailures) {
                System.out.printf("  ❌ %s%n", failure.getTestCase());
                if (failure.getMessage() != null) {
                    System.out.printf("     %s%n", failure.getMessage());
                }
            }
        }

        if (!timeouts.isEmpty()) {
            System.out.println("\nTimeout Tests:");
            for (TestResult timeout : timeouts) {
                System.out.printf("  ⏱️  %s%n", timeout.getTestCase());
            }
        }

        System.out.println("\n" + "=".repeat(40));
        System.out.println("Test262 Results Summary");
        System.out.println("=".repeat(40));
        System.out.printf("Total tests:   %d%n", total);
        System.out.printf("Executed:      %d%n", executed);

        if (executed > 0) {
            System.out.printf("Passed:        %d (%.1f%%)%n",
                    passed.get(), 100.0 * passed.get() / executed);
            System.out.printf("Failed:        %d (%.1f%%)%n",
                    failed.get(), 100.0 * failed.get() / executed);
            System.out.printf("Timeout:       %d (%.1f%%)%n",
                    timeout.get(), 100.0 * timeout.get() / executed);
        }

        System.out.printf("Skipped:       %d%n", skipped.get());
        System.out.println();

        if (!allResults.isEmpty()) {
            List<TestResult> sortedByTime = new ArrayList<>(allResults.size());
            sortedByTime.addAll(allResults);
            sortedByTime.sort((a, b) -> Long.compare(
                    b.getTestCase().getTimeElapsed(),
                    a.getTestCase().getTimeElapsed()));

            int topCount = Math.min(TOP_SLOW_TEST_COUNT, sortedByTime.size());
            if (topCount > 0) {
                System.out.println("Top " + topCount + " Slowest Tests:");
                for (int i = 0; i < topCount; i++) {
                    Test262TestCase testCase = sortedByTime.get(i).getTestCase();
                    System.out.printf("  %d. %s (%d ms)%n", i + 1, testCase, testCase.getTimeElapsed());
                }
            }
        }

        System.out.println("=".repeat(40));
    }

    public void recordResult(TestResult result) {
        admissionLock.readLock().lock();
        try {
            if (frozen) {
                lateWrites.incrementAndGet();
                return;
            }
            onAdmitting();
            allResults.add(result);
            switch (result.getStatus()) {
                case PASS:
                    passed.incrementAndGet();
                    break;
                case FAIL:
                    failed.incrementAndGet();
                    failures.add(result);
                    break;
                case SKIP:
                    skipped.incrementAndGet();
                    break;
                case TIMEOUT:
                    timeout.incrementAndGet();
                    timeouts.add(result);
                    break;
            }
        } finally {
            admissionLock.readLock().unlock();
        }
    }

    public void recordSkipped(Test262TestCase test, String reason) {
        admissionLock.readLock().lock();
        try {
            if (frozen) {
                lateWrites.incrementAndGet();
                return;
            }
            onAdmitting();
            skipped.incrementAndGet();
        } finally {
            admissionLock.readLock().unlock();
        }
    }

    public void reset() {
        admissionLock.writeLock().lock();
        try {
            passed.set(0);
            failed.set(0);
            skipped.set(0);
            timeout.set(0);
            lateWrites.set(0);
            frozen = false;
            allResults.clear();
            failures.clear();
            timeouts.clear();
        } finally {
            admissionLock.writeLock().unlock();
        }
    }
}
