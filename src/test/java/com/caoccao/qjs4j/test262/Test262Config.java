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

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Configuration for test262 test execution.
 */
public class Test262Config {
    /**
     * Default ceiling on worker threads.
     * <p>
     * The full suite holds a runtime and a context per thread and includes tests that build strings
     * and backtrack stacks in the tens of megabytes, so running it as wide as the machine allows
     * puts the JVM under memory pressure rather than making it faster. Subsets that are short or
     * light override this with {@link #UNLIMITED_THREAD_COUNT}.
     */
    public static final int DEFAULT_MAX_THREAD_COUNT = 4;
    /**
     * Sentinel for "no ceiling": the runner picks a thread count from the CPU core count.
     */
    public static final int UNLIMITED_THREAD_COUNT = 0;
    private final Set<Pattern> excludePatterns;
    private final Set<Pattern> includePatterns;
    private final Set<String> unsupportedFeatures;
    private long asyncTimeoutMs;
    private int maxTests;
    private int maxThreadCount;

    private Test262Config() {
        excludePatterns = new HashSet<>();
        includePatterns = new HashSet<>();
        unsupportedFeatures = new HashSet<>();
    }

    public static Test262Config forLanguageTests() {
        Test262Config config = loadDefault();
        config.includePatterns.clear();
        config.addIncludePatterns(Pattern.compile(".*/test/language/.*\\.js$"));
        config.maxTests = 200;
        // Unlimited: this subset is short enough that the whole machine is the right amount of it.
        config.maxThreadCount = UNLIMITED_THREAD_COUNT;
        return config;
    }

    public static Test262Config forLongRunningTest() {
        Test262Config config = loadDefault();
        config.includePatterns.clear();
        config.addIncludePatterns(
                Pattern.compile(".*/test/annexB/built-ins/RegExp/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/decodeURI.*/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/encodeURI.*/.*\\.js$"),
                Pattern.compile(".*/test/staging/sm/Date/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/RegExp/.*\\.js$"));
        // config.maxTests = 600;
        return config;
    }

    public static Test262Config forQuickTest() {
        Test262Config config = loadDefault();
        // Run a subset of tests for quick validation
        config.addExcludePatterns(
                Pattern.compile(".*/test/annexB/built-ins/RegExp/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/decodeURI.*/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/encodeURI.*/.*\\.js$"),
                Pattern.compile(".*/test/staging/sm/Date/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/RegExp/.*\\.js$"));
        // config.maxTests = 400 * 100;
        // Unlimited: the quick subset is the one that gates CI, so it runs as wide as the machine
        // allows.
        config.maxThreadCount = UNLIMITED_THREAD_COUNT;
        return config;
    }

    public static Test262Config loadDefault() {
        Test262Config config = new Test262Config();

        // Define unsupported features
        config.addUnsupportedFeatures("source-phase-imports");

        // Default: run all tests
        config.addIncludePatterns(Pattern.compile(".*\\.js$"));

        // Exclude fixture files
        config.addExcludePatterns(Pattern.compile(".*_FIXTURE\\.js$"));

        // 5 second timeout for async tests
        config.asyncTimeoutMs = 5000;

        // No limit on number of tests
        config.maxTests = Integer.MAX_VALUE;

        config.maxThreadCount = DEFAULT_MAX_THREAD_COUNT;

        return config;
    }

    public void addExcludePatterns(Pattern... patterns) {
        Stream.of(patterns).forEach(excludePatterns::add);
    }

    public void addIncludePatterns(Pattern... patterns) {
        Stream.of(patterns).forEach(includePatterns::add);
    }

    public void addUnsupportedFeatures(String... features) {
        Stream.of(features).forEach(unsupportedFeatures::add);
    }

    public long getAsyncTimeoutMs() {
        return asyncTimeoutMs;
    }

    public int getMaxTests() {
        return maxTests;
    }

    /**
     * The ceiling on worker threads for this configuration.
     *
     * @return the maximum thread count, or {@link #UNLIMITED_THREAD_COUNT} for no ceiling
     */
    public int getMaxThreadCount() {
        return maxThreadCount;
    }

    public Set<String> getUnsupportedFeatures() {
        return unsupportedFeatures;
    }

    public boolean isFeatureUnsupported(String feature) {
        return unsupportedFeatures.contains(feature);
    }

    public boolean matchesIncludePattern(Path testPath) {
        String pathStr = testPath.toString().replace('\\', '/');

        // Check exclusions first
        for (Pattern exclude : excludePatterns) {
            if (exclude.matcher(pathStr).find()) {
                return false;
            }
        }

        // Check inclusions
        for (Pattern include : includePatterns) {
            if (include.matcher(pathStr).find()) {
                return true;
            }
        }

        return false;
    }

    public void setAsyncTimeoutMs(long asyncTimeoutMs) {
        this.asyncTimeoutMs = asyncTimeoutMs;
    }

    public void setMaxTests(int maxTests) {
        this.maxTests = maxTests;
    }

    /**
     * Set the ceiling on worker threads.
     *
     * @param maxThreadCount the maximum, or {@link #UNLIMITED_THREAD_COUNT} for no ceiling;
     *                       negative values are treated as no ceiling
     */
    public void setMaxThreadCount(int maxThreadCount) {
        this.maxThreadCount = Math.max(UNLIMITED_THREAD_COUNT, maxThreadCount);
    }

    public boolean shouldSkipTest(Test262TestCase test) {
        // Skip based on missing features
        for (String feature : test.getFeatures()) {
            if (isFeatureUnsupported(feature)) {
                return true;
            }
        }

        // Check patterns
        String pathStr = test.getPath().toString().replace('\\', '/');

        // Check exclusions first
        for (Pattern exclude : excludePatterns) {
            if (exclude.matcher(pathStr).find()) {
                return true;
            }
        }

        // Check inclusions
        boolean matchesInclude = false;
        for (Pattern include : includePatterns) {
            if (include.matcher(pathStr).find()) {
                matchesInclude = true;
                break;
            }
        }

        return !matchesInclude;
    }
}
