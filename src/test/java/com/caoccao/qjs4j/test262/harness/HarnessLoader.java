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

package com.caoccao.qjs4j.test262.harness;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.exceptions.JSException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches test262 harness files.
 */
public class HarnessLoader {
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Path harnessDir;

    public HarnessLoader(Path test262Root) {
        this.harnessDir = test262Root.resolve("harness");
    }

    /**
     * Get default harness files that should be loaded for most tests.
     *
     * @return Set of default harness file names
     */
    public static List<String> getDefaultIncludes() {
        return List.of("assert.js", "sta.js");
    }

    /**
     * Clear the harness file cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Load a harness file by name.
     *
     * @param filename The harness file name (e.g., "assert.js")
     * @return The harness file content
     */
    public String loadHarness(String filename) {
        return cache.computeIfAbsent(filename, fn -> {
            try {
                Path file = harnessDir.resolve(fn);
                if (!Files.exists(file)) {
                    throw new IllegalArgumentException("Harness file not found: " + file);
                }
                return Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load harness file: " + fn, e);
            }
        });
    }

    /**
     * Load harness files into a JavaScript context.
     *
     * @param context  The JavaScript context
     * @param includes The harness files to load
     * @throws JSException If a harness file cannot be evaluated
     */
    public void loadIntoContext(JSContext context, Collection<String> includes) throws JSException {
        for (String include : includes) {
            String code = loadHarness(include);
            context.eval(code, include, false);
        }
    }
}
