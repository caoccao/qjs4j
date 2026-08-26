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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ECMAScript links a whole module graph before evaluating any of it. This engine loaded a
 * dependency and evaluated it in the same step, so an import naming an export nothing provides was
 * discovered <em>after</em> the module it imports from had already run: side effects landed for a
 * graph that must never have begun evaluating, and the counters a host reads reported the sequence
 * as if nothing had been evaluated.
 * <p>
 * The cases here assert the ordering the way the defect is observed — a host-visible counter the
 * dependency increments, which must still read {@code undefined} — and the engine's own module-body
 * count, which must be exactly zero.
 * <p>
 * Loading, linking and evaluating are still one operation for what the link pass cannot decide:
 * ambiguity between two {@code export *} routes, namespace construction, and resolution across a
 * cycle. Those are recorded in the fix report, not pinned here, because the engine does not yet do
 * them in the right order.
 */
public class JSModuleLinkOrderTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    /**
     * Evaluate an entry module and assert that the graph failed to link without running anything.
     *
     * @param entrySource         the entry module's source
     * @param entryName           the entry module's file name
     * @param expectedMessagePart text the reported error must contain
     */
    private void assertLinkFailsBeforeAnythingRuns(
            String entrySource, String entryName, String expectedMessagePart) throws IOException {
        Path entry = writeModule(entryName, entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            assertThatThrownBy(() -> context.eval(entrySource, entry.toString(), true))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining(expectedMessagePart);
            assertThat(context.eval("String(globalThis.dependencyBodyRan)", "probe.js", false).toString())
                    .as("no dependency body may run for a graph that fails to link")
                    .isEqualTo("undefined");
            assertThat(context.eval("String(globalThis.rootBodyRan)", "probe.js", false).toString())
                    .as("nor the entry module's own body")
                    .isEqualTo("undefined");
            assertThat(context.getModuleBodyEvaluationCount())
                    .as("the engine agrees that nothing was evaluated")
                    .isZero();
        }
    }

    @Test
    public void testACyclicGraphStillEvaluates() throws IOException {
        // Resolution across a cycle is what the link pass declines to decide. It must therefore
        // leave a legal cycle completely alone.
        writeModule("cycle-b.mjs",
                "import { a } from './cycle-a.mjs';\nexport const b = 'b';\nglobalThis.sawA = typeof a;\n");
        Path entry = writeModule("cycle-a.mjs",
                "import { b } from './cycle-b.mjs';\nexport const a = 'a';\nglobalThis.result = b;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("b");
        }
    }

    @Test
    public void testADefaultImportOfAModuleWithNoDefaultRunsNothing() throws IOException {
        writeModule("dep-no-default.mjs", "globalThis.dependencyBodyRan = true;\nexport const other = 1;\n");
        assertLinkFailsBeforeAnythingRuns(
                "import theDefault from './dep-no-default.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-default.mjs",
                "does not provide an export named 'default'");
    }

    @Test
    public void testADependencyThatThrowsIsStillARuntimeFailure() throws IOException {
        // Linking succeeds here, so the graph is evaluated and the dependency's own error is what
        // comes out. The link pass must not turn this into a link error.
        writeModule("throwing.mjs",
                "globalThis.dependencyBodyRan = true;\nthrow new TypeError('dependency body ran');\n");
        Path entry = writeModule("main-throwing.mjs",
                "import './throwing.mjs';\nglobalThis.rootBodyRan = true;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            assertThatThrownBy(() -> context.eval(Files.readString(entry), entry.toString(), true))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("dependency body ran");
            assertThat(context.eval("String(globalThis.dependencyBodyRan)", "probe.js", false).toString())
                    .as("the dependency did run — that is what a runtime failure means")
                    .isEqualTo("true");
            assertThat(context.getModuleBodyEvaluationCount()).isPositive();
        }
    }

    @Test
    public void testAMissingExportBehindAStarReExportRunsNothing() throws IOException {
        writeModule("star-leaf.mjs", "globalThis.dependencyBodyRan = true;\nexport const other = 1;\n");
        writeModule("star-middle.mjs",
                "globalThis.dependencyBodyRan = true;\nexport * from './star-leaf.mjs';\n");
        assertLinkFailsBeforeAnythingRuns(
                "import { missing } from './star-middle.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-star.mjs",
                "does not provide an export named 'missing'");
    }

    @Test
    public void testAMissingExportTwoLevelsDownRunsNothing() throws IOException {
        // The failure is neither the entry's nor its direct import's, so nothing in the graph may
        // have started — including the level that links cleanly.
        writeModule("leaf.mjs", "globalThis.dependencyBodyRan = true;\nexport const other = 1;\n");
        writeModule("middle.mjs",
                "globalThis.dependencyBodyRan = true;\n"
                        + "import { missing } from './leaf.mjs';\n"
                        + "export const middle = 1;\n");
        assertLinkFailsBeforeAnythingRuns(
                "import { middle } from './middle.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-deep.mjs",
                "does not provide an export named 'missing'");
    }

    @Test
    public void testANameReachedThroughAStarReExportLinksAndEvaluates() throws IOException {
        // The acceptance side: a name that does resolve through `export *` must not be rejected.
        writeModule("ok-leaf.mjs", "export const reached = 'leaf';\n");
        writeModule("ok-middle.mjs", "export * from './ok-leaf.mjs';\n");
        Path entry = writeModule("main-ok-star.mjs",
                "import { reached } from './ok-middle.mjs';\nglobalThis.result = reached;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("leaf");
        }
    }

    @Test
    public void testANamedImportOfAMissingExportRunsNothing() throws IOException {
        // The review's reproduction.
        writeModule("dep.mjs", "globalThis.dependencyBodyRan = true;\nexport const other = 1;\n");
        assertLinkFailsBeforeAnythingRuns(
                "import { missing } from './dep.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main.mjs",
                "does not provide an export named 'missing'");
    }

    @Test
    public void testARenamedImportOfAMissingExportRunsNothing() throws IOException {
        writeModule("dep-renamed.mjs", "globalThis.dependencyBodyRan = true;\nexport const other = 1;\n");
        assertLinkFailsBeforeAnythingRuns(
                "import { missing as here } from './dep-renamed.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-renamed.mjs",
                "does not provide an export named 'missing'");
    }

    @Test
    public void testAValidGraphStillEvaluatesEveryModule() throws IOException {
        // The link pass parses the graph before evaluating it. Parsing must not be mistaken for
        // evaluating, and must not stop anything from being evaluated afterwards.
        writeModule("count-leaf.mjs",
                "globalThis.order = (globalThis.order || []).concat('leaf');\nexport const leaf = 1;\n");
        writeModule("count-middle.mjs",
                "import { leaf } from './count-leaf.mjs';\n"
                        + "globalThis.order = (globalThis.order || []).concat('middle');\n"
                        + "export const middle = leaf + 1;\n");
        Path entry = writeModule("main-count.mjs",
                "import { middle } from './count-middle.mjs';\n"
                        + "globalThis.order = (globalThis.order || []).concat('entry');\n"
                        + "globalThis.result = middle;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("2");
            assertThat(context.eval("globalThis.order.join(',')", "probe.js", false).toString())
                    .as("dependencies still evaluate before their importers, once")
                    .isEqualTo("leaf,middle,entry");
        }
    }

    @Test
    public void testAnIndirectExportOfAMissingNameRunsNothing() throws IOException {
        // `export { x } from './dep.mjs'` asks for a name exactly as an import does.
        writeModule("dep-indirect.mjs", "globalThis.dependencyBodyRan = true;\nexport const other = 1;\n");
        assertLinkFailsBeforeAnythingRuns(
                "export { missing } from './dep-indirect.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-indirect.mjs",
                "does not provide an export named 'missing'");
    }

    @Test
    public void testAnUnresolvableSpecifierIsStillReportedByEvaluation() throws IOException {
        // The link pass declines to judge a module it cannot read, so the error and its type stay
        // exactly what evaluation produces.
        Path entry = writeModule("main-absent.mjs",
                "import { anything } from './not-here.mjs';\nglobalThis.rootBodyRan = true;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            assertThatThrownBy(() -> context.eval(Files.readString(entry), entry.toString(), true))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("Cannot find module");
        }
    }

    private Path writeModule(String name, String source) throws IOException {
        Path path = moduleDirectory.resolve(name);
        Files.writeString(path, source);
        return path;
    }
}
