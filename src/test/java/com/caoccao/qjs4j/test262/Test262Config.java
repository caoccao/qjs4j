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

/**
 * Configuration for test262 test execution.
 */
public class Test262Config {
    private long asyncTimeoutMs;
    private Set<Pattern> excludePatterns;
    private Set<Pattern> includePatterns;
    private int maxTests;
    private Set<String> unsupportedFeatures;

    private Test262Config() {
    }

    public static Test262Config forLanguageTests() {
        Test262Config config = loadDefault();
        // Only run language tests
        config.includePatterns.clear();
        config.includePatterns.add(Pattern.compile(".*/test/language/.*\\.js$"));
        config.maxTests = 200;
        return config;
    }

    public static Test262Config forQuickTest() {
        Test262Config config = loadDefault();
        // Run a subset of tests for quick validation
        config.maxTests = 1200;
        return config;
    }

    public static Test262Config loadDefault() {
        Test262Config config = new Test262Config();

        // Define unsupported features
        config.unsupportedFeatures = new HashSet<>();
        config.unsupportedFeatures.add("Intl.Segmenter");
        config.unsupportedFeatures.add("Intl.DisplayNames");
        config.unsupportedFeatures.add("top-level-await");
        config.unsupportedFeatures.add("import.meta");
        config.unsupportedFeatures.add("hashbang");
        config.unsupportedFeatures.add("Temporal");
        config.unsupportedFeatures.add("source-phase-imports");

        // Default: run all tests
        config.includePatterns = new HashSet<>();
        config.includePatterns.add(Pattern.compile(".*\\.js$"));

        // Exclude fixture files
        config.excludePatterns = new HashSet<>();
        config.excludePatterns.add(Pattern.compile(".*_FIXTURE\\.js$"));

        // 5 second timeout for async tests
        config.asyncTimeoutMs = 5000;

        // No limit on number of tests
        config.maxTests = Integer.MAX_VALUE;

        return config;
    }

    public void addExcludePattern(String pattern) {
        excludePatterns.add(Pattern.compile(pattern));
    }

    public void addIncludePattern(String pattern) {
        includePatterns.add(Pattern.compile(pattern));
    }

    public void addUnsupportedFeature(String feature) {
        unsupportedFeatures.add(feature);
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
        String pathStr = testPath.toString();

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
        String pathStr = test.getPath().toString();

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
