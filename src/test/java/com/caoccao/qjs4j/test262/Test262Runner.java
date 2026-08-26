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

import com.caoccao.qjs4j.test262.harness.HarnessLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Main runner for executing test262 conformance tests.
 */
public class Test262Runner {
    /**
     * The prefix every worker thread's name carries, so a leaked worker is identifiable.
     * <p>
     * Each runner appends a number of its own — see {@link #workerThreadNamePrefix()} — so a
     * thread that outlives its run can be traced back to the run that started it rather than to
     * "some Test262 run in this JVM".
     */
    static final String WORKER_THREAD_NAME_PREFIX = "test262-worker-";
    private static final long DEFAULT_WORKER_TERMINATION_TIMEOUT_MILLISECONDS = TimeUnit.MINUTES.toMillis(5);
    /**
     * The selections this runner accepts, and nothing else.
     * <p>
     * A map rather than a {@code switch} with a {@code default}, so that an argument the runner does
     * not recognise has nowhere to fall through to.
     */
    private static final Map<String, Supplier<Test262Config>> MODE_CONFIGURATIONS = Map.of(
            "--quick", Test262Config::forQuickTest,
            "--language", Test262Config::forLanguageTests,
            "--long-running", Test262Config::forLongRunningTest);
    private static final AtomicInteger RUNNER_NUMBER = new AtomicInteger();
    private final Test262Config config;
    private final Test262Executor executor;
    private final Test262Parser parser;
    private final Test262Reporter reporter;
    private final Integer requestedThreadCount;
    private final String singleTestPathFragment;
    private final Path test262Root;
    private final String workerThreadNamePrefix =
            WORKER_THREAD_NAME_PREFIX + RUNNER_NUMBER.incrementAndGet() + "-";
    private boolean allowEmptySelection;
    private long workerTerminationTimeoutMilliseconds = DEFAULT_WORKER_TERMINATION_TIMEOUT_MILLISECONDS;

    public Test262Runner(Path test262Root, Test262Config config) {
        this(test262Root, config, null, null);
    }

    public Test262Runner(Path test262Root, Test262Config config, String singleTestPathFragment) {
        this(test262Root, config, singleTestPathFragment, null);
    }

    public Test262Runner(
            Path test262Root,
            Test262Config config,
            String singleTestPathFragment,
            Integer requestedThreadCount) {
        this.test262Root = test262Root;
        this.config = config;
        this.singleTestPathFragment = singleTestPathFragment;
        this.requestedThreadCount = requestedThreadCount;
        this.parser = new Test262Parser();
        HarnessLoader harnessLoader = new HarnessLoader(test262Root);
        this.executor = new Test262Executor(harnessLoader, config.getAsyncTimeoutMs());
        this.reporter = new Test262Reporter();
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            exitCode = runMain(args);
        } catch (Exception e) {
            System.err.println("Error running test262: " + e.getMessage());
            e.printStackTrace();
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Parse the command line, run the suite and decide the process status.
     * <p>
     * Anything that means "the suite did not demonstrate conformance" is a nonzero status: a
     * failing test, a timeout, a missing test root, a filter that selected nothing, or an
     * interrupted run. Previously only a thrown Java exception was a failure, so a run in which
     * every test failed — or in which no test ran at all — still reported {@code BUILD SUCCESSFUL}.
     *
     * @param args the command line
     * @return the process exit status
     * @throws IOException if test discovery fails
     */
    static int runMain(String[] args) throws IOException {
        {
            Path test262Root = Paths.get("../test262");
            String mode = null;
            String singleTestPathFragment = null;
            Integer requestedThreadCount = null;

            int argIndex = 0;
            if (args.length > 0 && !args[0].startsWith("--")) {
                test262Root = Paths.get(args[0]);
                argIndex = 1;
            }
            while (argIndex < args.length) {
                String argument = args[argIndex];
                if ("--single".equals(argument)) {
                    if (argIndex + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --single");
                    }
                    singleTestPathFragment = args[argIndex + 1];
                    argIndex += 2;
                    continue;
                }
                if ("--threads".equals(argument)) {
                    if (argIndex + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --threads");
                    }
                    requestedThreadCount = Integer.parseInt(args[argIndex + 1]);
                    argIndex += 2;
                    continue;
                }
                // Anything left has to be a mode, and it has to be one this runner knows. Every
                // unrecognised argument used to overwrite `mode` and then fall through the switch
                // below to the full default selection, so `--quik`, a stray positional, or a focus
                // argument appended after `--quick` silently ran a much larger suite than the one
                // asked for — and a mistyped `--long-running` silently ran no long-running test
                // while still reporting success.
                if (!MODE_CONFIGURATIONS.containsKey(argument)) {
                    throw new IllegalArgumentException(
                            "Unknown argument '" + argument + "'.\n" + usage());
                }
                if (mode != null) {
                    throw new IllegalArgumentException(
                            "Modes are mutually exclusive, but both '" + mode + "' and '" + argument
                                    + "' were given.\n" + usage());
                }
                mode = argument;
                argIndex++;
            }

            Test262Config config = mode == null
                    ? Test262Config.loadDefault()
                    : MODE_CONFIGURATIONS.get(mode).get();

            Test262Runner runner = new Test262Runner(test262Root, config, singleTestPathFragment, requestedThreadCount);
            return runner.run().exitCode();
        }
    }

    /**
     * How to invoke the runner.
     *
     * @return the usage text
     */
    private static String usage() {
        List<String> modes = new ArrayList<>(MODE_CONFIGURATIONS.keySet());
        Collections.sort(modes);
        return "Usage: Test262Runner [<test262-root>] [" + String.join(" | ", modes) + "]"
                + " [--single <path-fragment>] [--threads <count>]\n"
                + "With no mode, the full default selection runs.";
    }

    /**
     * Wait for every worker to stop, ignoring further interruption so cleanup always completes.
     * <p>
     * Java interruption is cooperative, so this can only ever wait — a native call, a monitor wait
     * or a loop that never checks its interrupt flag survives {@code shutdownNow()}. Waiting
     * forever is not an option either, so the wait has a deadline and what happens past it is
     * reported rather than assumed: the count of workers still running becomes part of the run's
     * outcome, and the reporter is frozen so nothing they do afterwards can move a number the
     * caller has already been given.
     *
     * @param executorService the pool, already shut down
     * @return how the wait ended
     */
    WorkerShutdown awaitWorkerTermination(ThreadPoolExecutor executorService) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(workerTerminationTimeoutMilliseconds);
        long pollMilliseconds = Math.max(1L, Math.min(TimeUnit.MINUTES.toMillis(1), workerTerminationTimeoutMilliseconds));
        while (true) {
            try {
                if (executorService.awaitTermination(pollMilliseconds, TimeUnit.MILLISECONDS)) {
                    return new WorkerShutdown(!interrupted, 0);
                }
                if (System.nanoTime() - deadline >= 0) {
                    int abandonedWorkers = Math.max(1, executorService.getActiveCount());
                    System.err.println("Gave up waiting for " + abandonedWorkers
                            + " test task(s) to finish; abandoning them");
                    return new WorkerShutdown(false, abandonedWorkers);
                }
                System.out.println("Waiting for remaining test tasks to finish...");
            } catch (InterruptedException e) {
                // Absorbed here and re-raised by the caller once the pool has stopped.
                interrupted = true;
                executorService.shutdownNow();
            }
        }
    }

    private List<Path> discoverTests(Path testsDir) throws IOException {
        List<Path> testFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(testsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".js"))
                    .filter(p -> !p.toString().contains("_FIXTURE"))
                    .filter(p -> config.matchesIncludePattern(p))
                    .sorted(Comparator.comparing(Path::toString)) // Sort for consistent order
                    .forEach(testFiles::add);
        }
        return testFiles;
    }

    private List<Path> discoverTestsWithSingleFilter(Path testsDir) throws IOException {
        List<Path> testFiles = discoverTests(testsDir);
        String normalizedPathFragment = singleTestPathFragment.replace('\\', '/');
        testFiles.removeIf(testFile ->
                !testFile.toString().replace('\\', '/').contains(normalizedPathFragment));
        return testFiles;
    }

    /**
     * The reporter this run wrote into.
     * <p>
     * Package-private and for tests: what a run returns should not change afterwards, and proving
     * that means being able to look at the reporter once the run is over.
     *
     * @return the reporter
     */
    Test262Reporter reporter() {
        return reporter;
    }

    private Path resolveSingleTestPath(Path testsDir) {
        if (singleTestPathFragment == null || singleTestPathFragment.isBlank()) {
            return null;
        }
        String normalizedPath = singleTestPathFragment.replace('\\', '/');
        Path directPath = Paths.get(normalizedPath);
        if (!directPath.isAbsolute()) {
            Path resolvedFromTestRoot = test262Root.resolve(directPath).normalize();
            if (Files.isRegularFile(resolvedFromTestRoot)) {
                return resolvedFromTestRoot;
            }
            Path resolvedFromTestsDir = testsDir.resolve(directPath).normalize();
            if (Files.isRegularFile(resolvedFromTestsDir)) {
                return resolvedFromTestsDir;
            }
        } else if (Files.isRegularFile(directPath)) {
            return directPath.normalize();
        }
        return null;
    }

    /**
     * Discover, expand and execute the selected tests.
     *
     * @return a structured outcome; {@link RunOutcome#exitCode()} is what {@code main} exits with
     * @throws IOException if test discovery fails
     */
    public RunOutcome run() throws IOException {
        System.out.println("Test262 Runner for qjs4j");
        System.out.println("Test262 root: " + test262Root.toAbsolutePath().normalize());
        System.out.println();

        Path testsDir = test262Root.resolve("test");

        if (!Files.exists(testsDir)) {
            System.err.println("Error: Test262 test directory not found at " + testsDir);
            System.err.println("Please ensure test262 is cloned at " + test262Root.toAbsolutePath().normalize());
            return RunOutcome.discoveryFailed("Test262 test directory not found at " + testsDir);
        }

        List<Path> testFiles;
        if (singleTestPathFragment != null && !singleTestPathFragment.isBlank()) {
            String normalizedPathFragment = singleTestPathFragment.replace('\\', '/');
            Path singleTestPath = resolveSingleTestPath(testsDir);
            if (singleTestPath != null) {
                testFiles = List.of(singleTestPath);
            } else {
                testFiles = discoverTestsWithSingleFilter(testsDir);
            }
            System.out.println("Single-test filter: " + normalizedPathFragment);
        } else {
            testFiles = discoverTests(testsDir);
        }
        System.out.println("Discovered " + testFiles.size() + " test files");
        if (testFiles.isEmpty()) {
            System.out.println("No test file matched the current filter.");
            return allowEmptySelection
                    ? RunOutcome.of(reporter, false, 0, true)
                    : RunOutcome.discoveryFailed("No test file matched the current filter.");
        }

        // Apply max tests limit
        if (testFiles.size() > config.getMaxTests()) {
            testFiles = testFiles.subList(0, config.getMaxTests());
            System.out.println("Limited to first " + config.getMaxTests() + " tests");
        }

        int cpuCount = Runtime.getRuntime().availableProcessors();
        boolean isMacOs = System.getProperty("os.name", "").toLowerCase().contains("mac");
        int threadCount = Math.max(1, isMacOs ? cpuCount * 3 / 4 : cpuCount / 2);
        // The configuration's ceiling, applied before the explicit overrides below so either can
        // still ask for more. The full suite caps at Test262Config.DEFAULT_MAX_THREAD_COUNT because
        // each thread holds a runtime and a context and some tests build strings and backtrack
        // stacks in the tens of megabytes; the short subsets set UNLIMITED_THREAD_COUNT and run as
        // wide as the machine allows.
        int maxThreadCount = config.getMaxThreadCount();
        if (maxThreadCount != Test262Config.UNLIMITED_THREAD_COUNT) {
            threadCount = Math.min(threadCount, maxThreadCount);
        }
        String configuredThreadCount = System.getProperty("qjs4j.test262.threads", "").trim();
        if (!configuredThreadCount.isEmpty()) {
            threadCount = Math.max(1, Integer.parseInt(configuredThreadCount));
        }
        if (requestedThreadCount != null) {
            threadCount = Math.max(1, requestedThreadCount);
        }
        if (singleTestPathFragment != null && !singleTestPathFragment.isBlank()) {
            threadCount = 1;
        }
        threadCount = Math.max(1, threadCount);
        long prewarmElapsedMilliseconds = executor.prewarm();
        System.out.println("Prewarmed runtime/context in " + prewarmElapsedMilliseconds + " ms");
        System.out.println("Starting test execution with " + threadCount + " threads...\n");

        AtomicInteger workerNumber = new AtomicInteger();
        ThreadPoolExecutor executorService = new ThreadPoolExecutor(
                threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                runnable -> {
                    // Named so a leaked worker is identifiable, and daemon so one that will not
                    // stop cannot keep the JVM alive after run() has returned.
                    Thread worker = new Thread(runnable, workerThreadNamePrefix + workerNumber.incrementAndGet());
                    worker.setDaemon(true);
                    return worker;
                });
        AtomicInteger testCount = new AtomicInteger(0);
        // One work item per file. Parsing decides how many interpretations the file has, so the
        // expansion happens on the worker after parse() rather than here.
        List<Test262TestCase> testCases = new ArrayList<>(testFiles.size());
        for (int i = 0; i < testFiles.size(); i++) {
            Test262TestCase testCase = new Test262TestCase(testFiles.get(i));
            testCase.setIndex(i);
            testCases.add(testCase);
        }
        List<Future<?>> futures = new ArrayList<>(testCases.size());

        for (int i = 0; i < testCases.size(); i++) {
            final int fileIndex = i;
            Future<?> future = executorService.submit(() -> {
                Test262TestCase parsedFile = testCases.get(fileIndex);
                try {
                    parser.parse(parsedFile);

                    // Test262 defines how many interpretations a file has; an ordinary file has
                    // two. Running only the first one is why strict-mode-only regressions could
                    // be reported as passing — and skipping by the file rather than by the
                    // interpretation is why the summary added unlike units, counting two
                    // interpretations for a file that ran and one for a file that did not.
                    List<Test262TestCase> variants = parsedFile.expandVariants();

                    // Apply filters
                    if (config.shouldSkipTest(parsedFile)) {
                        for (Test262TestCase variant : variants) {
                            reporter.recordSkipped(variant, "Feature not supported or excluded");
                        }
                        return;
                    }

                    for (Test262TestCase variant : variants) {
                        // Between variants is the cheapest place to notice a cancellation, and
                        // taking it keeps the exceptional path — the one that ends in workers
                        // being abandoned — as short as it can be.
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        TestResult result = executor.execute(variant);
                        reporter.recordResult(result);

                        // Print progress every 100 executions
                        int count = testCount.incrementAndGet();
                        if (count % 100 == 0) {
                            reporter.printProgress();
                        }
                    }
                } catch (Throwable t) {
                    reporter.recordResult(TestResult.fail(parsedFile,
                            "Unexpected runner error: " + t.getClass().getSimpleName()
                                    + (t.getMessage() != null ? " - " + t.getMessage() : "")));
                }
            });
            futures.add(future);
        }

        // All tasks have been submitted at this point; disallow new submissions.
        executorService.shutdown();

        boolean interrupted = false;

        // Ensure any failure swallowed by submit() Future is surfaced and counted.
        for (int i = 0; i < testCases.size(); i++) {
            try {
                futures.get(i).get();
            } catch (InterruptedException e) {
                // The interrupt flag is deliberately not restored yet: awaitTermination below
                // would throw immediately, which is how an interrupted run used to return a
                // final-looking outcome while its workers carried on executing files and
                // mutating the reporter. It is restored once the pool has actually stopped.
                interrupted = true;
                break;
            } catch (ExecutionException e) {
                Test262TestCase testCase = testCases.get(i);
                Throwable cause = e.getCause() == null ? e : e.getCause();
                System.err.println("Error processing test " + testCase.getPath() + ": " + cause.getMessage());
                reporter.recordResult(TestResult.fail(testCase,
                        "Internal runner error: " + cause.getClass().getSimpleName()
                                + (cause.getMessage() != null ? " - " + cause.getMessage() : "")));
            } catch (CancellationException e) {
                // Only reachable once cleanup has cancelled the remaining work.
                interrupted = true;
                break;
            }
        }

        if (interrupted) {
            System.err.println("Test execution interrupted; cancelling remaining tests");
            for (Future<?> future : futures) {
                future.cancel(true);
            }
            executorService.shutdownNow();
        }
        WorkerShutdown shutdown = awaitWorkerTermination(executorService);
        interrupted |= !shutdown.clean();
        // Whatever the workers are doing now, the run is over: the counts stop moving here, so the
        // summary that is printed and the outcome that is returned are the same snapshot. A worker
        // that outlived the wait can no longer change either, and its attempts are counted.
        reporter.freeze();
        if (interrupted) {
            // Restored now that the reporter is closed, so the caller sees the interruption it
            // asked for and the summary below is a snapshot of a stopped run.
            Thread.currentThread().interrupt();
        }

        reporter.printSummary();
        if (shutdown.abandonedWorkers() > 0) {
            System.err.println(shutdown.abandonedWorkers() + " test task(s) were abandoned; the "
                    + "counts above exclude anything they do from now on");
        }
        RunOutcome outcome = RunOutcome.of(
                reporter, interrupted, shutdown.abandonedWorkers(), allowEmptySelection);
        if (!outcome.isSuccessful()) {
            System.err.println(outcome.diagnostic());
        }
        return outcome;
    }

    /**
     * Allow a selection that matches no test file to be a successful run.
     * <p>
     * Off by default: a filter that silently selects nothing is the same false green as a run whose
     * tests all failed. Tooling that deliberately runs an empty selection opts in.
     *
     * @param allowEmptySelection true to treat an empty selection as success
     * @return this
     */
    public Test262Runner setAllowEmptySelection(boolean allowEmptySelection) {
        this.allowEmptySelection = allowEmptySelection;
        return this;
    }

    /**
     * Set how long {@link #awaitWorkerTermination} waits before abandoning workers.
     * <p>
     * Package-private and for tests: the five-minute default cannot be reached in a unit test, and
     * the behaviour past the deadline is the part worth testing.
     *
     * @param workerTerminationTimeoutMilliseconds the timeout in milliseconds
     * @return this
     */
    Test262Runner setWorkerTerminationTimeoutMilliseconds(long workerTerminationTimeoutMilliseconds) {
        this.workerTerminationTimeoutMilliseconds = workerTerminationTimeoutMilliseconds;
        return this;
    }

    /**
     * The name prefix this runner's worker threads carry.
     *
     * @return the prefix, unique to this runner
     */
    String workerThreadNamePrefix() {
        return workerThreadNamePrefix;
    }

    /**
     * The outcome of a run, and the process status that follows from it.
     *
     * @param failed                the number of failing tests
     * @param timedOut              the number of timed-out tests
     * @param passed                the number of passing tests
     * @param skipped               the number of skipped tests
     * @param interrupted           whether the run was interrupted before it finished
     * @param abandonedWorkers      how many workers were still running when the runner stopped
     *                              waiting for them
     * @param discoveryError        the reason discovery produced nothing usable, or {@code null}
     * @param emptySelectionAllowed whether the caller opted into a run that executes nothing
     */
    public record RunOutcome(
            int failed,
            int timedOut,
            int passed,
            int skipped,
            boolean interrupted,
            int abandonedWorkers,
            String discoveryError,
            boolean emptySelectionAllowed) {

        static RunOutcome discoveryFailed(String reason) {
            return new RunOutcome(0, 0, 0, 0, false, 0, reason, false);
        }

        static RunOutcome of(
                Test262Reporter reporter,
                boolean interrupted,
                int abandonedWorkers,
                boolean emptySelectionAllowed) {
            return new RunOutcome(
                    reporter.getFailed(),
                    reporter.getTimeout(),
                    reporter.getPassed(),
                    reporter.getSkipped(),
                    interrupted,
                    abandonedWorkers,
                    null,
                    emptySelectionAllowed);
        }

        /**
         * Why this run did not demonstrate conformance.
         * <p>
         * Discovery finding no file and discovery finding files that were then all skipped are
         * different problems, and asking for a concrete test that declares only unsupported
         * features produces the second one: zero interpretations executed, and — before the
         * {@link #isSuccessful()} rule below — exit status zero.
         *
         * @return the diagnostic, or {@code null} when the run is successful
         */
        public String diagnostic() {
            if (discoveryError != null) {
                return discoveryError;
            }
            if (abandonedWorkers > 0) {
                return abandonedWorkers + " test task(s) did not stop and were abandoned; these "
                        + "counts describe only what finished before then.";
            }
            if (interrupted) {
                return "The run was interrupted before it finished.";
            }
            if (failed > 0 || timedOut > 0) {
                return failed + " test(s) failed and " + timedOut + " timed out.";
            }
            if (!emptySelectionAllowed && executed() == 0) {
                return skipped > 0
                        ? "All " + skipped + " discovered test file(s) were skipped; nothing was executed."
                        : "No test was executed.";
            }
            return null;
        }

        /**
         * How many interpretations actually ran.
         *
         * @return the number of executed interpretations
         */
        public int executed() {
            return passed + failed + timedOut;
        }

        /**
         * The process status this outcome implies.
         *
         * @return 0 only when every executed test passed and the run completed
         */
        public int exitCode() {
            return isSuccessful() ? 0 : 1;
        }

        /**
         * Whether the run demonstrated conformance over its selection.
         *
         * @return true when discovery succeeded, the run completed, something ran, and nothing
         * failed or timed out
         */
        public boolean isSuccessful() {
            return diagnostic() == null;
        }
    }

    /**
     * How a wait for the worker pool ended.
     *
     * @param clean            true when every worker stopped and nothing was interrupted
     * @param abandonedWorkers how many workers were still running when the wait gave up
     */
    record WorkerShutdown(boolean clean, int abandonedWorkers) {
    }
}
