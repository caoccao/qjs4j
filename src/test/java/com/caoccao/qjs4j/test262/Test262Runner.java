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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Main runner for executing test262 conformance tests.
 */
public class Test262Runner {
    private final Test262Config config;
    private final Test262Executor executor;
    private final Test262Parser parser;
    private final Test262Reporter reporter;
    private final Integer requestedThreadCount;
    private final String singleTestPathFragment;
    private final Path test262Root;

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
        try {
            Path test262Root = Paths.get("../test262");
            String mode = "";
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
                mode = argument;
                argIndex++;
            }

            Test262Config config = switch (mode) {
                case "--quick" -> Test262Config.forQuickTest();
                case "--language" -> Test262Config.forLanguageTests();
                case "--long-running" -> Test262Config.forLongRunningTest();
                default -> Test262Config.loadDefault();
            };

            Test262Runner runner = new Test262Runner(test262Root, config, singleTestPathFragment, requestedThreadCount);
            runner.run();

        } catch (Exception e) {
            System.err.println("Error running test262: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
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

    public void run() throws IOException {
        System.out.println("Test262 Runner for qjs4j");
        System.out.println("Test262 root: " + test262Root.toAbsolutePath().normalize());
        System.out.println();

        Path testsDir = test262Root.resolve("test");

        if (!Files.exists(testsDir)) {
            System.err.println("Error: Test262 test directory not found at " + testsDir);
            System.err.println("Please ensure test262 is cloned at " + test262Root.toAbsolutePath().normalize());
            return;
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
            return;
        }

        // Apply max tests limit
        if (testFiles.size() > config.getMaxTests()) {
            testFiles = testFiles.subList(0, config.getMaxTests());
            System.out.println("Limited to first " + config.getMaxTests() + " tests");
        }

        int cpuCount = Runtime.getRuntime().availableProcessors();
        boolean isMacOs = System.getProperty("os.name", "").toLowerCase().contains("mac");
        int threadCount = Math.max(1, isMacOs ? cpuCount * 3 / 4 : cpuCount / 2);
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
        long prewarmElapsedMilliseconds = executor.prewarm();
        System.out.println("Prewarmed runtime/context in " + prewarmElapsedMilliseconds + " ms");
        System.out.println("Starting test execution with " + threadCount + " threads...\n");

        ThreadPoolExecutor executorService = new ThreadPoolExecutor(
                threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        AtomicInteger testCount = new AtomicInteger(0);
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
                Test262TestCase testCase = testCases.get(fileIndex);
                try {
                    parser.parse(testCase);

                    // Apply filters
                    if (config.shouldSkipTest(testCase)) {
                        reporter.recordSkipped(testCase, "Feature not supported or excluded");
                        return;
                    }

                    // Execute test
                    TestResult result = executor.execute(testCase);
                    reporter.recordResult(result);

                    // Print progress every 100 tests
                    int count = testCount.incrementAndGet();
                    if (count % 100 == 0) {
                        reporter.printProgress();
                    }
                } catch (Throwable t) {
                    reporter.recordResult(TestResult.fail(testCase,
                            "Unexpected runner error: " + t.getClass().getSimpleName()
                                    + (t.getMessage() != null ? " - " + t.getMessage() : "")));
                }
            });
            futures.add(future);
        }

        // All tasks have been submitted at this point; disallow new submissions.
        executorService.shutdown();

        // Ensure any failure swallowed by submit() Future is surfaced and counted.
        for (int i = 0; i < testCases.size(); i++) {
            try {
                futures.get(i).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                Test262TestCase testCase = testCases.get(i);
                Throwable cause = e.getCause() == null ? e : e.getCause();
                System.err.println("Error processing test " + testCase.getPath() + ": " + cause.getMessage());
                reporter.recordResult(TestResult.fail(testCase,
                        "Internal runner error: " + cause.getClass().getSimpleName()
                                + (cause.getMessage() != null ? " - " + cause.getMessage() : "")));
            }
        }

        try {
            while (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                System.out.println("Waiting for remaining test tasks to finish...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Test execution interrupted");
        }

        reporter.printSummary();
    }
}
