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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runner used to report success whatever happened: {@code main} treated only a thrown Java
 * exception as failure, so a missing test root, a filter that matched nothing, and a run in which
 * every test failed all produced {@code BUILD SUCCESSFUL}.
 */
public class Test262RunnerOutcomeTest {
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
    void testInterruptionProducesNonZeroExit() {
        Test262Runner.RunOutcome outcome =
                new Test262Runner.RunOutcome(0, 0, 10, 0, true, null);
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
                new Test262Runner.RunOutcome(0, 1, 10, 0, false, null);
        assertThat(outcome.isSuccessful()).isFalse();
        assertThat(outcome.exitCode()).isEqualTo(1);
    }
}
