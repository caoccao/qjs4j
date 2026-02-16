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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit integration for test262 tests.
 * This allows running test262 tests from IDEs and Gradle.
 * <p>
 * Note: This test is disabled by default as it runs a large suite of tests.
 * Enable it by removing the @Disabled annotation or run via command line:
 * ./gradlew test --tests "Test262Test"
 */
public class Test262Test {
    private static final Path TEST262_ROOT = Paths.get("../test262");

    private List<Path> discoverTests(Path testsDir, Test262Config config) throws IOException {
        List<Path> testFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(testsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".js"))
                    .filter(p -> !p.toString().contains("_FIXTURE"))
                    .filter(p -> config.matchesIncludePattern(p))
                    .limit(config.getMaxTests())
                    .forEach(testFiles::add);
        }

        return testFiles;
    }

    private String getTestName(Path testFile) {
        Path relativePath = TEST262_ROOT.relativize(testFile);
        return relativePath.toString();
    }

    @Disabled
    @TestFactory
    Collection<DynamicTest> test262Suite() throws IOException {
        if (!Files.exists(TEST262_ROOT)) {
            System.err.println("Warning: test262 not found at " + TEST262_ROOT.toAbsolutePath().normalize());
            System.err.println("Skipping test262 tests. To run them, clone test262:");
            System.err.println("  cd .. && git clone https://github.com/tc39/test262.git");
            return List.of();
        }

        Test262Config config = Test262Config.forQuickTest(); // Run limited tests for JUnit
        Test262Parser parser = new Test262Parser();
        HarnessLoader harnessLoader = new HarnessLoader(TEST262_ROOT);
        Test262Executor executor = new Test262Executor(harnessLoader, config.getAsyncTimeoutMs());

        Path testsDir = TEST262_ROOT.resolve("test");
        List<Path> testFiles = discoverTests(testsDir, config);

        return testFiles.stream()
                .map(testFile -> DynamicTest.dynamicTest(
                        getTestName(testFile),
                        () -> {
                            Test262TestCase testCase = parser.parse(testFile);

                            // Skip if necessary
                            if (config.shouldSkipTest(testCase)) {
                                return; // JUnit doesn't have explicit skip, just return
                            }

                            TestResult result = executor.execute(testCase);

                            if (result.isFailed()) {
                                fail(result.getMessage());
                            } else if (result.isTimeout()) {
                                fail("Test timeout after " + config.getAsyncTimeoutMs() + "ms");
                            }
                            // Pass and skip are both successful
                        }
                ))
                .collect(Collectors.toList());
    }

    @Test
    void testRunnerExists() {
        // Simple smoke test to ensure the runner can be instantiated
        Test262Config config = Test262Config.loadDefault();
        Test262Runner runner = new Test262Runner(TEST262_ROOT, config);
        // Success if no exception
    }
}
