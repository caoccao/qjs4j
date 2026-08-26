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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code negative.phase: resolution} says the error has to be raised while the module graph is
 * linked, before any module body runs. The executor compared the thrown constructor and nothing
 * else, so a module that linked cleanly and threw the same constructor from its body reported a
 * pass in the wrong phase — and the local checkout's 34 resolution-phase tests were therefore
 * counted without their phase claim ever being checked.
 * <p>
 * Test262 marks the boundary in the source itself: every parse- and resolution-phase test opens its
 * body with {@code $DONOTEVALUATE()}, a call that must be unreachable. Observing it is what lets
 * the executor tell the phases apart while loading, linking and evaluating are still one engine
 * operation.
 * <p>
 * These cases build their own Test262 root, so they need no checkout.
 */
public class Test262ResolutionPhaseTest {
    @TempDir
    Path test262Root;
    private Test262Executor executor;
    private Path testDirectory;

    private Test262TestCase moduleCase(String code, String phase, String type) {
        Test262TestCase testCase = new Test262TestCase(testDirectory.resolve("synthetic.js"));
        testCase.setFlags(Set.of("module"));
        testCase.setCode(code);
        testCase.setNegative(new Test262TestCase.NegativeInfo(phase, type));
        testCase.setVariant(Test262TestCase.Variant.MODULE);
        return testCase;
    }

    @BeforeEach
    public void setUp() throws IOException {
        Path harnessDirectory = test262Root.resolve("harness");
        Files.createDirectories(harnessDirectory);
        Files.writeString(harnessDirectory.resolve("assert.js"),
                "function assert(c, m) { if (!c) throw new Error(m); }\n");
        // The two definitions every non-raw test gets, copied from the real harness so the probe
        // wraps exactly what a real test calls.
        Files.writeString(harnessDirectory.resolve("sta.js"),
                "function Test262Error(message) { this.message = message || ''; }\n"
                        + "function $DONOTEVALUATE() {\n"
                        + "  throw 'Test262: This statement should not be evaluated.';\n"
                        + "}\n");
        testDirectory = test262Root.resolve("test");
        Files.createDirectories(testDirectory);
        executor = new Test262Executor(new HarnessLoader(test262Root), 500, 5000);
    }

    @Test
    void testResolutionPhaseAcceptsAnImportOfAMissingExport() throws IOException {
        // The shape of language/module-code/instn-named-err-not-found.js: the fixture exports
        // nothing, so linking fails and the body — which starts with $DONOTEVALUATE() — never runs.
        Files.writeString(testDirectory.resolve("empty_FIXTURE.js"), "export const other = 1;\n");
        TestResult result = executor.execute(moduleCase(
                "$DONOTEVALUATE();\n\nimport { missing } from './empty_FIXTURE.js';\n",
                "resolution",
                "SyntaxError"));
        assertThat(result.isPassed()).as(result.getMessage()).isTrue();
    }

    @Test
    void testResolutionPhaseRejectsAWrongConstructor() throws IOException {
        Files.writeString(testDirectory.resolve("empty_FIXTURE.js"), "export const other = 1;\n");
        TestResult result = executor.execute(moduleCase(
                "$DONOTEVALUATE();\n\nimport { missing } from './empty_FIXTURE.js';\n",
                "resolution",
                "TypeError"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("Expected TypeError");
    }

    @Test
    void testResolutionPhaseRejectsAnErrorThrownByTheBody() {
        // The false pass the review reproduced: nothing about this module fails to link, and the
        // SyntaxError it throws is a real SyntaxError — it is simply thrown in the wrong phase.
        TestResult result = executor.execute(moduleCase(
                "try { $DONOTEVALUATE(); } catch (e) {}\nthrow new SyntaxError('thrown by the body');\n",
                "resolution",
                "SyntaxError"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage())
                .contains("Expected a resolution-phase SyntaxError")
                .contains("the test body was evaluated");
    }

    @Test
    void testRuntimePhaseAcceptsTheSameErrorTheResolutionPhaseRejects() {
        // Identical source, identical thrown value, different metadata: the phase is the only thing
        // separating the two, which is what proves the executor is reading it.
        String code = "try { $DONOTEVALUATE(); } catch (e) {}\nthrow new SyntaxError('thrown by the body');\n";
        assertThat(executor.execute(moduleCase(code, "resolution", "SyntaxError")).isPassed()).isFalse();
        assertThat(executor.execute(moduleCase(code, "runtime", "SyntaxError")).isPassed()).isTrue();
    }
}
