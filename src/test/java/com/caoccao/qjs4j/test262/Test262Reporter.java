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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks and reports test262 execution results.
 */
public class Test262Reporter {
    private static final int TOP_SLOW_TEST_COUNT = 5;

    private final List<TestResult> allResults = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger failed = new AtomicInteger(0);
    private final List<TestResult> failures = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger passed = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    private final AtomicInteger timeout = new AtomicInteger(0);
    private final List<TestResult> timeouts = Collections.synchronizedList(new ArrayList<>());

    public int getFailed() {
        return failed.get();
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

    public int getTotalTests() {
        return getTotalExecuted() + skipped.get();
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
            List<TestResult> sortedFailures = new ArrayList<>(failures);
            sortedFailures.sort(Comparator.comparingInt(r -> r.getTestCase().getIndex()));
            System.out.println("\nFailed Tests:");
            for (TestResult failure : sortedFailures) {
                System.out.printf("  ❌ %s%n", failure.getTestCase().getPath());
                if (failure.getMessage() != null) {
                    System.out.printf("     %s%n", failure.getMessage());
                }
            }
        }

        if (!timeouts.isEmpty()) {
            System.out.println("\nTimeout Tests:");
            for (TestResult timeout : timeouts) {
                System.out.printf("  ⏱️  %s%n", timeout.getTestCase().getPath());
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
            List<TestResult> sortedByTime = new ArrayList<>(allResults);
            sortedByTime.sort((a, b) -> Long.compare(
                    b.getTestCase().getTimeElapsed(),
                    a.getTestCase().getTimeElapsed()));

            int topCount = Math.min(TOP_SLOW_TEST_COUNT, sortedByTime.size());
            if (topCount > 0) {
                System.out.println("Top " + topCount + " Slowest Tests:");
                for (int i = 0; i < topCount; i++) {
                    Test262TestCase testCase = sortedByTime.get(i).getTestCase();
                    System.out.printf("  %d. %s (%d ms)%n", i + 1, testCase.getPath(), testCase.getTimeElapsed());
                }
            }
        }

        System.out.println("=".repeat(40));
    }

    public void recordResult(TestResult result) {
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
    }

    public void recordSkipped(Test262TestCase test, String reason) {
        skipped.incrementAndGet();
    }

    public void reset() {
        passed.set(0);
        failed.set(0);
        skipped.set(0);
        timeout.set(0);
        allResults.clear();
        failures.clear();
        timeouts.clear();
    }
}
