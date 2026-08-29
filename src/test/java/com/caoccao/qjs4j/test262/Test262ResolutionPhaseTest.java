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
 * {@code negative.phase} says which stage of the module pipeline an error has to come from:
 * {@code resolution} means the graph failed to link and nothing was evaluated, {@code runtime}
 * means it linked and then something threw. Both directions have to be enforced or neither claim
 * means anything — the executor once checked neither, then only the first, so a genuine link
 * failure passed as {@code runtime} and a dependency whose body threw passed as {@code resolution}.
 * <p>
 * Two things now answer the question. Test262 marks the boundary in the source itself: every parse-
 * and resolution-phase test opens its body with {@code $DONOTEVALUATE()}, a call that must be
 * unreachable. And the engine counts module bodies — how many started, how many were pulled in to
 * satisfy an import, and whether any of them failed, by throwing or by rejecting a top-level-await
 * evaluation promise. That is what distinguishes a link failure that evaluated a dependency along
 * the way from an evaluation failure, while loading, linking and evaluating are still one engine
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
    void testResolutionPhaseRejectsADependencyBodyThatThrows() throws IOException {
        // The second false pass the review reproduced. The dependency's body runs and throws the
        // declared constructor before the root body is reached, so the root's $DONOTEVALUATE() is
        // never called and the marker alone sees nothing wrong. The error is an evaluation error
        // all the same.
        Files.writeString(testDirectory.resolve("throws_FIXTURE.js"),
                "throw new TypeError('dependency body ran');\n");
        TestResult result = executor.execute(moduleCase(
                "$DONOTEVALUATE();\n\nimport './throws_FIXTURE.js';\n",
                "resolution",
                "TypeError"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage())
                .contains("Expected a resolution-phase TypeError")
                .contains("a module body was evaluated");
    }

    @Test
    void testResolutionPhaseRejectsATopLevelAwaitDependencyThatRejects() throws IOException {
        // Same graph, dishonest metadata: an asynchronous module evaluation failure is still an
        // evaluation failure, so it cannot pass as a linking one.
        Files.writeString(testDirectory.resolve("rejects_FIXTURE.js"),
                "export const resolved = await 42;\n"
                        + "export default await Promise.reject(new TypeError('rejected'));\n");
        TestResult result = executor.execute(moduleCase(
                "$DONOTEVALUATE();\n\nimport { resolved } from './rejects_FIXTURE.js';\n",
                "resolution",
                "TypeError"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("a module body was evaluated");
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
    void testResolutionPhaseRejectsAnIntermediateDependencyBodyThatThrows() throws IOException {
        // Two levels down, so the failure is neither the root's nor its direct import's.
        Files.writeString(testDirectory.resolve("throws_FIXTURE.js"),
                "throw new TypeError('dependency body ran');\n");
        Files.writeString(testDirectory.resolve("middle_FIXTURE.js"),
                "import './throws_FIXTURE.js';\nexport const middle = 1;\n");
        TestResult result = executor.execute(moduleCase(
                "$DONOTEVALUATE();\n\nimport { middle } from './middle_FIXTURE.js';\n",
                "resolution",
                "TypeError"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("a module body was evaluated");
    }

    @Test
    void testRuntimePhaseAcceptsADependencyBodyThatThrows() throws IOException {
        // The same shape with honest metadata: a dependency that throws is an evaluation failure,
        // and the runtime phase must still accept it even though the root body never runs.
        Files.writeString(testDirectory.resolve("throws_FIXTURE.js"),
                "throw new TypeError('dependency body ran');\n");
        TestResult result = executor.execute(moduleCase(
                "import './throws_FIXTURE.js';\n",
                "runtime",
                "TypeError"));
        assertThat(result.isPassed()).as(result.getMessage()).isTrue();
    }

    @Test
    void testRuntimePhaseAcceptsATopLevelAwaitDependencyThatRejects() throws IOException {
        // The shape of language/module-code/top-level-await/module-import-rejection.js. The
        // dependency's body runs to completion and its evaluation promise then rejects, so nothing
        // threw on any stack and the root body never started — yet the graph was evaluated, and
        // the failure is a runtime one.
        Files.writeString(testDirectory.resolve("rejects_FIXTURE.js"),
                "export const resolved = await 42;\n"
                        + "export default await Promise.reject(new TypeError('rejected'));\n");
        TestResult result = executor.execute(moduleCase(
                "import { resolved } from './rejects_FIXTURE.js';\n"
                        + "throw new Error('this should be unreachable');\n",
                "runtime",
                "TypeError"));
        assertThat(result.isPassed()).as(result.getMessage()).isTrue();
    }

    @Test
    void testRuntimePhaseAcceptsTheSameErrorTheResolutionPhaseRejects() {
        // Identical source, identical thrown value, different metadata: the phase is the only thing
        // separating the two, which is what proves the executor is reading it.
        String code = "try { $DONOTEVALUATE(); } catch (e) {}\nthrow new SyntaxError('thrown by the body');\n";
        assertThat(executor.execute(moduleCase(code, "resolution", "SyntaxError")).isPassed()).isFalse();
        assertThat(executor.execute(moduleCase(code, "runtime", "SyntaxError")).isPassed()).isTrue();
    }

    @Test
    void testRuntimePhaseRejectsAnImportOfAMissingExport() throws IOException {
        // The first false pass the review reproduced. Linking fails, so nothing of the graph is
        // evaluated — the dependency is pulled in and runs to completion, but the module the
        // runner asked for never starts. Runtime-phase metadata is a lie here, and comparing the
        // thrown constructor alone cannot see it.
        Files.writeString(testDirectory.resolve("empty_FIXTURE.js"), "export const other = 1;\n");
        TestResult result = executor.execute(moduleCase(
                "$DONOTEVALUATE();\n\nimport { missing } from './empty_FIXTURE.js';\n",
                "runtime",
                "SyntaxError"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage())
                .contains("Expected a runtime-phase SyntaxError")
                .contains("no module body was evaluated");
    }

    @Test
    void testRuntimePhaseRejectsAnUnresolvableSpecifier() throws IOException {
        // Nothing at all is evaluated: the file does not exist, so the graph cannot even be built.
        TestResult result = executor.execute(moduleCase(
                "$DONOTEVALUATE();\n\nimport './absent_FIXTURE.js';\n",
                "runtime",
                "TypeError"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("no module body was evaluated");
    }

    @Test
    void testRuntimePhaseStillAcceptsAScriptThatThrows() {
        // The counters describe a module graph, so a script variant must not be held to them.
        Test262TestCase testCase = new Test262TestCase(testDirectory.resolve("synthetic.js"));
        testCase.setCode("throw new TypeError('thrown by the script');\n");
        testCase.setNegative(new Test262TestCase.NegativeInfo("runtime", "TypeError"));
        testCase.setVariant(Test262TestCase.Variant.NON_STRICT);
        assertThat(executor.execute(testCase).isPassed()).isTrue();
    }
}
