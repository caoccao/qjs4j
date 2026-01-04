/*
 * Copyright (c) 2024. caoccao.com Sam Cao
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks and reports test262 execution results.
 */
public class Test262Reporter {
    private final AtomicInteger passed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    private final AtomicInteger timeout = new AtomicInteger(0);

    private final List<TestResult> failures = Collections.synchronizedList(new ArrayList<>());
    private final List<TestResult> timeouts = Collections.synchronizedList(new ArrayList<>());

    public void recordResult(TestResult result) {
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

    public int getTotalExecuted() {
        return passed.get() + failed.get() + timeout.get();
    }

    public int getTotalTests() {
        return getTotalExecuted() + skipped.get();
    }

    public int getPassed() {
        return passed.get();
    }

    public int getFailed() {
        return failed.get();
    }

    public int getSkipped() {
        return skipped.get();
    }

    public int getTimeout() {
        return timeout.get();
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

        System.out.println("\n" + "=".repeat(70));
        System.out.println("Test262 Results Summary");
        System.out.println("=".repeat(70));
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
        System.out.println("=".repeat(70));

        if (!failures.isEmpty()) {
            System.out.println("\nFailed Tests (showing first 50):");
            int count = 0;
            for (TestResult failure : failures) {
                if (count++ >= 50) {
                    System.out.printf("  ... and %d more failures%n", failures.size() - 50);
                    break;
                }
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
    }

    public void reset() {
        passed.set(0);
        failed.set(0);
        skipped.set(0);
        timeout.set(0);
        failures.clear();
        timeouts.clear();
    }
}
