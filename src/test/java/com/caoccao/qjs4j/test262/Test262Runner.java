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
import java.util.List;
import java.util.stream.Stream;

/**
 * Main runner for executing test262 conformance tests.
 */
public class Test262Runner {
    private final Test262Config config;
    private final Test262Executor executor;
    private final Test262Parser parser;
    private final Test262Reporter reporter;
    private final Path test262Root;

    public Test262Runner(Path test262Root, Test262Config config) {
        this.test262Root = test262Root;
        this.config = config;
        this.parser = new Test262Parser();
        HarnessLoader harnessLoader = new HarnessLoader(test262Root);
        this.executor = new Test262Executor(harnessLoader, config.getAsyncTimeoutMs());
        this.reporter = new Test262Reporter();
    }

    public static void main(String[] args) {
        try {
            Path test262Root = args.length > 0
                    ? Paths.get(args[0])
                    : Paths.get("../test262");

            // Determine config based on arguments
            Test262Config config;
            if (args.length > 1 && args[1].equals("--quick")) {
                config = Test262Config.forQuickTest();
            } else if (args.length > 1 && args[1].equals("--language")) {
                config = Test262Config.forLanguageTests();
            } else {
                config = Test262Config.loadDefault();
            }

            Test262Runner runner = new Test262Runner(test262Root, config);
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
                    .forEach(testFiles::add);
        }

        return testFiles;
    }

    public void run() throws IOException {
        System.out.println("Test262 Runner for qjs4j");
        System.out.println("Test262 root: " + test262Root.toAbsolutePath());
        System.out.println();

        Path testsDir = test262Root.resolve("test");

        if (!Files.exists(testsDir)) {
            System.err.println("Error: Test262 test directory not found at " + testsDir);
            System.err.println("Please ensure test262 is cloned at " + test262Root.toAbsolutePath());
            return;
        }

        List<Path> testFiles = discoverTests(testsDir);
        System.out.println("Discovered " + testFiles.size() + " test files");

        // Apply max tests limit
        if (testFiles.size() > config.getMaxTests()) {
            testFiles = testFiles.subList(0, config.getMaxTests());
            System.out.println("Limited to first " + config.getMaxTests() + " tests");
        }

        System.out.println("Starting test execution...\n");

        int testCount = 0;
        for (Path testFile : testFiles) {
            testCount++;

            try {
                Test262TestCase testCase = parser.parse(testFile);

                // Apply filters
                if (config.shouldSkipTest(testCase)) {
                    reporter.recordSkipped(testCase, "Feature not supported or excluded");
                    continue;
                }

                // Execute test
                TestResult result = executor.execute(testCase);
                reporter.recordResult(result);

                // Print progress every 100 tests
                if (testCount % 100 == 0) {
                    reporter.printProgress();
                }

            } catch (Exception e) {
                System.err.println("Error processing test " + testFile + ": " + e.getMessage());
            }
        }

        reporter.printSummary();
    }
}
