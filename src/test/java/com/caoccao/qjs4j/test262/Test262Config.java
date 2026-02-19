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
    private final Set<Pattern> excludePatterns;
    private final Set<Pattern> includePatterns;
    private final Set<String> unsupportedFeatures;
    private long asyncTimeoutMs;
    private int maxTests;

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
        return config;
    }

    public static Test262Config forLongRunningTest() {
        Test262Config config = loadDefault();
        config.includePatterns.clear();
        config.addIncludePatterns(
                Pattern.compile(".*/test/annexB/built-ins/RegExp/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/decodeURI.*/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/encodeURI.*/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/RegExp/.*\\.js$"));
        return config;
    }

    public static Test262Config forQuickTest() {
        Test262Config config = loadDefault();
        // Run a subset of tests for quick validation
        config.addExcludePatterns(
                Pattern.compile(".*/test/annexB/built-ins/RegExp/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/decodeURI.*/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/encodeURI.*/.*\\.js$"),
                Pattern.compile(".*/test/built-ins/RegExp/.*\\.js$"));
        config.maxTests = 6100;
        return config;
    }

    public static Test262Config loadDefault() {
        Test262Config config = new Test262Config();

        // Define unsupported features
        config.addUnsupportedFeatures("Intl.Segmenter",
                "Intl.DisplayNames",
                "top-level-await",
                "import.meta",
                "hashbang",
                "Temporal",
                "source-phase-imports",
                "cross-realm");

        // Default: run all tests
        config.addIncludePatterns(Pattern.compile(".*\\.js$"));

        // Exclude fixture files
        config.addExcludePatterns(Pattern.compile(".*_FIXTURE\\.js$"));

        // 5 second timeout for async tests
        config.asyncTimeoutMs = 5000;

        // No limit on number of tests
        config.maxTests = Integer.MAX_VALUE;

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
