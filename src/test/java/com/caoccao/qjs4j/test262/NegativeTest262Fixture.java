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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Runs synthetic negative cases with the standard harness bindings in a temporary suite. */
abstract class NegativeTest262Fixture {
    Test262Executor executor;
    @TempDir
    Path test262Root;
    Path testDirectory;

    Test262TestCase negativeCase(String code, String phase, String type, Test262TestCase.Variant variant,
            Set<String> flags) {
        Test262TestCase testCase = new Test262TestCase(testDirectory.resolve("synthetic.js"));
        testCase.setCode(code);
        testCase.setVariant(variant);
        testCase.setNegative(new Test262TestCase.NegativeInfo(phase, type));
        testCase.setFlags(flags);
        return testCase;
    }

    @BeforeEach
    public void setUp() throws IOException {
        Path harnessDirectory = test262Root.resolve("harness");
        Files.createDirectories(harnessDirectory);
        Files.writeString(harnessDirectory.resolve("assert.js"),
                "function assert(c, m) { if (!c) throw new Error(m); }\n");
        Files.writeString(harnessDirectory.resolve("sta.js"),
                "function Test262Error(message) { this.message = message || ''; }\n"
                        + "Test262Error.prototype.toString = function () {\n"
                        + "  return 'Test262Error: ' + this.message;\n" + "};\n" + "function $DONOTEVALUATE() {\n"
                        + "  throw 'Test262: This statement should not be evaluated.';\n" + "}\n");
        testDirectory = test262Root.resolve("test");
        Files.createDirectories(testDirectory);
        executor = new Test262Executor(new HarnessLoader(test262Root), 500, 5000);
    }
}
