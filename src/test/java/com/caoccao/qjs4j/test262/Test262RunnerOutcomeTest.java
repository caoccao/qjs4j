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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The runner used to report success whatever happened: {@code main} treated only a thrown Java
 * exception as failure, so a missing test root, a filter that matched nothing, and a run in which
 * every test failed all produced {@code BUILD SUCCESSFUL}.
 */
public class Test262RunnerOutcomeTest {
    /**
     * A configuration that declares the synthetic file's only feature unsupported.
     *
     * @return the configuration
     */
    private static Test262Config configSkippingEverything() {
        Test262Config config = Test262Config.loadDefault();
        config.addUnsupportedFeatures("no-such-feature-at-all");
        return config;
    }

    /**
     * How many worker threads of one runner are still alive.
     * <p>
     * Matched on that runner's own prefix rather than the shared one: these cases deliberately
     * leave workers running, and they run in the same JVM as each other.
     *
     * @param runner the runner whose workers to count
     * @return the count
     */
    private static int liveWorkerThreadCount(Test262Runner runner) {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith(runner.workerThreadNamePrefix())) {
                count++;
            }
        }
        return count;
    }

    /**
     * A test file that takes long enough for an interruption to land mid-run.
     *
     * @return the source
     */
    private static String slowTestSource() {
        return "/*---\nflags: [raw]\n---*/\n"
                + "var total = 0;\n"
                + "for (var i = 0; i < 200000; i++) { total += i; }\n";
    }

    /**
     * A test file slow enough that a worker running it is still running a moment later.
     *
     * @return the source
     */
    private static String verySlowTestSource() {
        return "/*---\nflags: [raw]\n---*/\n"
                + "var total = 0;\n"
                + "for (var i = 0; i < 20000000; i++) { total += i; }\n";
    }

    private static Path writeFakeTest262Root(Path root, String testFileName, String testSource) throws IOException {
        Path testDirectory = root.resolve("test");
        Files.createDirectories(testDirectory);
        Files.writeString(testDirectory.resolve(testFileName), testSource);
        // Minimal stand-ins for the two harness files every non-raw test loads, so these cases
        // exercise the runner without needing a Test262 checkout.
        Path harnessDirectory = root.resolve("harness");
        Files.createDirectories(harnessDirectory);
        Files.writeString(harnessDirectory.resolve("assert.js"), "function assert(c, m) { if (!c) throw new Error(m); }\n");
        Files.writeString(harnessDirectory.resolve("sta.js"),
                "function Test262Error(message) { this.message = message || ''; }\n"
                        + "function $DONOTEVALUATE() { throw 'Test262: This statement should not be evaluated.'; }\n");
        return root;
    }

    @Test
    void testAFrozenReporterCountsLateResultsWithoutApplyingThem() {
        Test262Reporter reporter = new Test262Reporter();
        Test262TestCase testCase = new Test262TestCase(Paths.get("late.js"));
        reporter.recordResult(TestResult.pass(testCase));
        assertThat(reporter.getPassed()).isEqualTo(1);
        assertThat(reporter.isFrozen()).isFalse();

        reporter.freeze();
        reporter.recordResult(TestResult.pass(testCase));
        reporter.recordResult(TestResult.fail(testCase, "late"));
        reporter.recordSkipped(testCase, "late");

        assertThat(reporter.getPassed()).isEqualTo(1);
        assertThat(reporter.getFailed()).isZero();
        assertThat(reporter.getSkipped()).isZero();
        assertThat(reporter.getLateWrites()).isEqualTo(3);
        assertThat(reporter.isFrozen()).isTrue();
    }

    @Test
    void testAbandonedWorkersAreReportedRatherThanAssumedToHaveStopped() throws Exception {
        // Interruption is cooperative, so the wait for the workers can expire with some of them
        // still running. run() used to print a summary and return a final-looking outcome anyway,
        // while those workers went on executing files and mutating the reporter behind it.
        Path root = Files.createTempDirectory("qjs4j-test262-abandon");
        writeFakeTest262Root(root, "slow-000.js", verySlowTestSource());
        for (int index = 1; index < 200; index++) {
            Files.writeString(root.resolve("test").resolve(String.format("slow-%03d.js", index)),
                    verySlowTestSource());
        }
        Test262Runner runner = new Test262Runner(root, Test262Config.loadDefault(), null, 2)
                .setWorkerTerminationTimeoutMilliseconds(1);
        Test262Runner.RunOutcome[] outcome = {null};
        Thread runnerThread = new Thread(() -> {
            try {
                outcome[0] = runner.run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "test262-runner");
        runnerThread.start();
        Thread.sleep(500);
        runnerThread.interrupt();
        runnerThread.join(120_000);

        assertThat(runnerThread.isAlive()).isFalse();
        assertThat(outcome[0]).isNotNull();
        assertThat(outcome[0].abandonedWorkers())
                .as("workers that had not stopped are counted, not assumed away")
                .isPositive();
        assertThat(outcome[0].isSuccessful()).isFalse();
        assertThat(outcome[0].exitCode()).isEqualTo(1);
        assertThat(outcome[0].diagnostic()).contains("abandoned");

        int executedAtReturn = outcome[0].executed();
        assertThat(runner.reporter().isFrozen())
                .as("the reporter stops accepting results when the run returns")
                .isTrue();
        // Give whatever was still running every chance to write, then prove it did not land.
        for (int attempt = 0; attempt < 100 && liveWorkerThreadCount(runner) > 0; attempt++) {
            Thread.sleep(100);
        }
        assertThat(runner.reporter().getTotalExecuted())
                .as("nothing a leaked worker does can change the counts already returned")
                .isEqualTo(executedAtReturn);
    }

    @Test
    void testAbandonedWorkersMakeAnOtherwisePerfectRunUnsuccessful() {
        Test262Runner.RunOutcome outcome =
                new Test262Runner.RunOutcome(0, 0, 10, 0, false, 2, null, false);
        assertThat(outcome.isSuccessful()).isFalse();
        assertThat(outcome.exitCode()).isEqualTo(1);
        assertThat(outcome.diagnostic()).contains("2 test task(s) did not stop");
    }

    @Test
    void testAllSkippedSelectionFailsUnlessOptedIn() throws IOException {
        // Discovery finds a file, its metadata says the engine cannot run it, and the run executes
        // nothing at all. A green exit here is the same false green as a filter that matched
        // nothing — and it is what asking for one concrete unsupported test produced.
        Path root = writeFakeTest262Root(
                Files.createTempDirectory("qjs4j-test262-skipped"),
                "skipped.js",
                "/*---\nfeatures: [no-such-feature-at-all]\n---*/\nvar x = 1;\n");
        Test262Runner.RunOutcome failing = new Test262Runner(root, configSkippingEverything()).run();
        // Two, not one: an ordinary file has a sloppy and a strict interpretation, and both were
        // skipped. Counting the file instead meant the summary added files to interpretations.
        assertThat(failing.skipped()).isEqualTo(2);
        assertThat(failing.executed()).isZero();
        assertThat(failing.discoveryError()).isNull();
        assertThat(failing.diagnostic()).contains("were skipped");
        assertThat(failing.isSuccessful()).isFalse();
        assertThat(failing.exitCode()).isEqualTo(1);

        Test262Runner.RunOutcome allowed = new Test262Runner(root, configSkippingEverything())
                .setAllowEmptySelection(true)
                .run();
        assertThat(allowed.isSuccessful()).isTrue();
        assertThat(allowed.exitCode()).isZero();
    }

    @Test
    void testEmptySelectionFailsUnlessOptedIn() throws IOException {
        Path root = writeFakeTest262Root(
                Files.createTempDirectory("qjs4j-test262-empty"), "pass.js", "var x = 1;\n");
        Test262Runner.RunOutcome failing = new Test262Runner(root, Test262Config.loadDefault(), "no-such-file")
                .run();
        assertThat(failing.discoveryError()).contains("No test file matched");
        assertThat(failing.exitCode()).isEqualTo(1);

        Test262Runner.RunOutcome allowed = new Test262Runner(root, Test262Config.loadDefault(), "no-such-file")
                .setAllowEmptySelection(true)
                .run();
        assertThat(allowed.isSuccessful()).isTrue();
        assertThat(allowed.exitCode()).isZero();
    }

    @Test
    void testFailingTestProducesNonZeroExit() throws IOException {
        Path root = writeFakeTest262Root(
                Files.createTempDirectory("qjs4j-test262-fail"),
                "fail.js",
                "/*---\nflags: [raw]\n---*/\nthrow new Error('boom');\n");
        Test262Runner.RunOutcome outcome = new Test262Runner(root, Test262Config.loadDefault()).run();
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.isSuccessful()).isFalse();
        assertThat(outcome.exitCode()).isEqualTo(1);
    }

    @Test
    void testGivingUpOnAnUninterruptibleWorkerReportsIt() throws Exception {
        // A task that ignores interruption entirely, which is what shutdownNow() cannot do
        // anything about: a native call, a monitor wait, or an engine loop that misses its flag.
        Path root = Files.createTempDirectory("qjs4j-test262-uninterruptible");
        writeFakeTest262Root(root, "noop.js", "/*---\nflags: [raw]\n---*/\n");
        Test262Runner runner = new Test262Runner(root, Test262Config.loadDefault())
                .setWorkerTerminationTimeoutMilliseconds(50);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread worker = new Thread(runnable, Test262Runner.WORKER_THREAD_NAME_PREFIX + "uninterruptible");
                    worker.setDaemon(true);
                    return worker;
                });
        try {
            pool.submit(() -> {
                started.countDown();
                while (true) {
                    try {
                        if (release.await(1, TimeUnit.SECONDS)) {
                            return;
                        }
                    } catch (InterruptedException ignored) {
                        // Deliberately swallowed: this task cannot be stopped by interruption.
                    }
                }
            });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            Test262Runner.WorkerShutdown shutdown = runner.awaitWorkerTermination(pool);
            assertThat(shutdown.clean()).isFalse();
            assertThat(shutdown.abandonedWorkers()).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void testInterruptedRunLeavesNoWorkerRunning() throws IOException, InterruptedException {
        // Interruption used to restore the flag and break, which made the awaitTermination that
        // followed throw at once: run() returned a final-looking outcome while non-daemon workers
        // kept executing files and mutating the reporter behind it.
        Path root = Files.createTempDirectory("qjs4j-test262-interrupt");
        writeFakeTest262Root(root, "slow-000.js", slowTestSource());
        for (int index = 1; index < 400; index++) {
            Files.writeString(root.resolve("test").resolve(String.format("slow-%03d.js", index)), slowTestSource());
        }
        Test262Runner runner = new Test262Runner(root, Test262Config.loadDefault(), null, 2);
        Test262Runner.RunOutcome[] outcome = {null};
        boolean[] interruptFlagPreserved = {false};
        Thread runnerThread = new Thread(() -> {
            try {
                outcome[0] = runner.run();
                interruptFlagPreserved[0] = Thread.currentThread().isInterrupted();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "test262-runner");
        runnerThread.start();
        Thread.sleep(500);
        runnerThread.interrupt();
        runnerThread.join(120_000);

        assertThat(runnerThread.isAlive()).isFalse();
        assertThat(outcome[0]).isNotNull();
        assertThat(outcome[0].interrupted()).isTrue();
        assertThat(outcome[0].isSuccessful()).isFalse();
        assertThat(outcome[0].exitCode()).isEqualTo(1);
        assertThat(interruptFlagPreserved[0])
                .as("the caller's interrupt status survives the cleanup")
                .isTrue();
        assertThat(outcome[0].abandonedWorkers())
                .as("the pool stopped within the timeout, so nothing was abandoned")
                .isZero();
        assertThat(liveWorkerThreadCount(runner))
                .as("no worker of this run may still be running once run() has returned")
                .isZero();
    }

    @Test
    void testInterruptionProducesNonZeroExit() {
        Test262Runner.RunOutcome outcome =
                new Test262Runner.RunOutcome(0, 0, 10, 0, true, 0, null, false);
        assertThat(outcome.isSuccessful()).isFalse();
        assertThat(outcome.exitCode()).isEqualTo(1);
    }

    @Test
    void testMissingTestRootProducesNonZeroExit() throws IOException {
        Test262Runner runner = new Test262Runner(
                Paths.get("/definitely/missing/test262"), Test262Config.loadDefault());
        Test262Runner.RunOutcome outcome = runner.run();
        assertThat(outcome.discoveryError()).contains("test directory not found");
        assertThat(outcome.isSuccessful()).isFalse();
        assertThat(outcome.exitCode()).isEqualTo(1);
    }

    @Test
    void testPassingTestProducesZeroExit() throws IOException {
        Path root = writeFakeTest262Root(
                Files.createTempDirectory("qjs4j-test262-pass"),
                "pass.js",
                "/*---\nflags: [raw]\n---*/\nvar x = 1;\n");
        Test262Runner.RunOutcome outcome = new Test262Runner(root, Test262Config.loadDefault()).run();
        assertThat(outcome.passed()).isEqualTo(1);
        assertThat(outcome.failed()).isZero();
        assertThat(outcome.isSuccessful()).isTrue();
        assertThat(outcome.exitCode()).isZero();
    }

    @Test
    void testSkipsAreCountedByInterpretationLikeExecutions() throws IOException {
        // An ordinary file has two interpretations and a noStrict or module file has one. Filtering
        // by file and recording a single skip made the summary add files to interpretations, so
        // neither the file count nor the interpretation count could be reconciled with it.
        Path root = Files.createTempDirectory("qjs4j-test262-skip-units");
        writeFakeTest262Root(root, "ordinary.js",
                "/*---\nfeatures: [no-such-feature-at-all]\n---*/\nvar x = 1;\n");
        Files.writeString(root.resolve("test").resolve("only-strict.js"),
                "/*---\nfeatures: [no-such-feature-at-all]\nflags: [onlyStrict]\n---*/\nvar x = 1;\n");
        Files.writeString(root.resolve("test").resolve("no-strict.js"),
                "/*---\nfeatures: [no-such-feature-at-all]\nflags: [noStrict]\n---*/\nvar x = 1;\n");
        Files.writeString(root.resolve("test").resolve("raw.js"),
                "/*---\nfeatures: [no-such-feature-at-all]\nflags: [raw]\n---*/\nvar x = 1;\n");
        Files.writeString(root.resolve("test").resolve("module.js"),
                "/*---\nfeatures: [no-such-feature-at-all]\nflags: [module]\n---*/\nvar x = 1;\n");

        Test262Runner.RunOutcome outcome = new Test262Runner(root, configSkippingEverything())
                .setAllowEmptySelection(true)
                .run();
        assertThat(outcome.executed()).isZero();
        // 2 for the ordinary file, 1 each for onlyStrict, noStrict, raw and module.
        assertThat(outcome.skipped())
                .as("one skip per interpretation the run would otherwise have executed")
                .isEqualTo(6);
    }

    @Test
    void testStrictVariantIsExecutedForOrdinaryFiles() throws IOException {
        // No flags: two interpretations. The source is only an error under a strict prologue, so
        // one variant passes and one fails — proving both ran.
        Path root = writeFakeTest262Root(
                Files.createTempDirectory("qjs4j-test262-strict"), "strict.js", "var public = 1;\n");
        Test262Runner.RunOutcome outcome = new Test262Runner(root, Test262Config.loadDefault()).run();
        assertThat(outcome.passed() + outcome.failed()).isEqualTo(2);
        assertThat(outcome.failed()).isEqualTo(1);
    }

    @Test
    void testTimeoutProducesNonZeroExit() {
        Test262Runner.RunOutcome outcome =
                new Test262Runner.RunOutcome(0, 1, 10, 0, false, 0, null, false);
        assertThat(outcome.isSuccessful()).isFalse();
        assertThat(outcome.exitCode()).isEqualTo(1);
    }

    @Test
    void testUnknownArgumentsAreRejectedRatherThanSilentlySelectingTheFullSuite() throws IOException {
        // Every unrecognised argument used to overwrite the mode and then fall through to the full
        // default selection. A typo, a stray positional, or a focus argument appended after
        // --quick therefore ran a much larger suite than the one asked for, with no diagnostic —
        // and a mistyped --long-running ran no long-running test while still reporting success.
        Path root = writeFakeTest262Root(
                Files.createTempDirectory("qjs4j-test262-args"),
                "ok.js",
                "/*---\nflags: [raw]\n---*/\nvar x = 1;\n");
        String rootPath = root.toString();

        assertThatThrownBy(() -> Test262Runner.runMain(new String[]{rootPath, "--quik"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown argument '--quik'")
                .hasMessageContaining("Usage:");
        assertThatThrownBy(() -> Test262Runner.runMain(new String[]{rootPath, "--quick", "language"}))
                .as("a positional appended after a mode is not a mode")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown argument 'language'");
        assertThatThrownBy(() -> Test262Runner.runMain(new String[]{rootPath, "--quick", "--language"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> Test262Runner.runMain(new String[]{rootPath, "--single"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing value for --single");
    }

    @Test
    void testValidArgumentOrderingsAreAccepted() throws IOException {
        Path root = writeFakeTest262Root(
                Files.createTempDirectory("qjs4j-test262-args-ok"),
                "ok.js",
                "/*---\nflags: [raw]\n---*/\nvar x = 1;\n");
        String rootPath = root.toString();
        // Exit status 0 means the selection ran and passed; the point is that none of these throw.
        assertThat(Test262Runner.runMain(new String[]{rootPath})).isZero();
        assertThat(Test262Runner.runMain(new String[]{rootPath, "--threads", "1"})).isZero();
        assertThat(Test262Runner.runMain(new String[]{rootPath, "--threads", "1", "--single", "ok.js"})).isZero();
    }
}
