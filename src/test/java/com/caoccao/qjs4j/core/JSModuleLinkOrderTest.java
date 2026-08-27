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
import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

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
 * Which names a declaration asks for is read from the engine's own tokens rather than matched out
 * of the source text, so the shapes that used to slip past the check — a tab around {@code as}, a
 * clause spread over lines, a comment between the tokens, a string import name,
 * {@code import d, * as ns from '…'} — are held to the same ordering as the plainest one. So are
 * the two the pass used to decline outright: a name two {@code export *} routes provide with
 * different bindings, and a name that does not resolve across a cycle.
 */
public class JSModuleLinkOrderTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    /**
     * Evaluate an entry module that imports a name {@code ./dep.mjs} does not export, and assert
     * that nothing ran.
     *
     * @param entryName   the entry module's file name
     * @param entrySource the entry module's source
     */
    private void assertImportOfAMissingNameRunsNothing(String entryName, String entrySource)
            throws IOException {
        writeModule("dep.mjs", "globalThis.dependencyBodyRan = true;\nexport const other = 1;\n");
        assertLinkFailsBeforeAnythingRuns(entrySource, entryName, "does not provide an export named");
    }

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
    public void testACommentBetweenTheTokensOfAnImportSpecifierRunsNothing() throws IOException {
        assertImportOfAMissingNameRunsNothing("main-comment.mjs",
                "import { /* which */ missing /* one */ as here } from './dep.mjs';\n"
                        + "globalThis.rootBodyRan = true;\n");
    }

    @Test
    public void testACycleThatDoesNotResolveANameRunsNothing() throws IOException {
        // The other half of the cycle case. `cyc-b` asks `cyc-a` for a name it does not export, and
        // the pass has to answer that across the cycle rather than decline — declining is what let
        // the graph run first.
        writeModule("cyc-a.mjs",
                "import { b } from './cyc-b.mjs';\n"
                        + "globalThis.dependencyBodyRan = true;\nexport const a = 1;\n");
        writeModule("cyc-b.mjs",
                "import { missing } from './cyc-a.mjs';\n"
                        + "globalThis.dependencyBodyRan = true;\nexport const b = 1;\n");
        assertLinkFailsBeforeAnythingRuns(
                "import { a } from './cyc-a.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-cycle-missing.mjs",
                "does not provide an export named 'missing'");
    }

    @Test
    public void testACyclicGraphStillEvaluates() throws IOException {
        // A legal cycle must be left completely alone: the pass resolves across it rather than
        // declining to look, and resolving must not turn a working graph into a link error.
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
    public void testADefaultAndNamespaceImportOfAModuleWithNoDefaultRunsNothing() throws IOException {
        // `import d, * as ns from '…'` was read as the identifier text `d  * as ns`, which is not
        // an identifier, so the request for `default` was dropped and the dependency ran.
        assertImportOfAMissingNameRunsNothing("main-default-namespace.mjs",
                "import d, * as ns from './dep.mjs';\nglobalThis.rootBodyRan = true;\n");
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
    public void testADependencyThatDoesNotParseRunsNothing() throws IOException {
        // ECMAScript parses every module in the graph while loading it, so a dependency that is not
        // valid source fails the graph before any of it runs — including the dependencies listed
        // ahead of it, which the engine used to evaluate on the way past.
        writeModule("parses.mjs", "globalThis.dependencyBodyRan = true;\n");
        writeModule("broken.mjs", "break;\n");
        assertLinkFailsBeforeAnythingRuns(
                "import './parses.mjs';\nimport './broken.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-parse-order.mjs",
                "SyntaxError");
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
    public void testALinkFailureInADependencySaysWhichModuleAskedAndWhere() throws IOException {
        // A dependency's offsets are positions in a file the caller never passed to eval, so on
        // their own they would name a place in the wrong text — which is why they used to be
        // dropped and written into the message instead, leaving an embedder to parse prose for
        // something the engine already knew. Paired with the name of the source they index, they
        // are exactly as usable as a root failure's, and are reported the same structured way.
        writeModule("dep-transitive.mjs", "export const other = 1;\n");
        Path mid = writeModule("mid-transitive.mjs",
                "import { missing } from './dep-transitive.mjs';\nexport const m = 1;\n");
        String entrySource = "import { m } from './mid-transitive.mjs';\n";
        Path entry = writeModule("main-transitive.mjs", entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            JSException failure = catchThrowableOfType(
                    JSException.class, () -> context.eval(entrySource, entry.toString(), true));
            assertThat(failure).isNotNull();
            assertThat(failure.getMessage())
                    .contains("The requested module './dep-transitive.mjs' "
                            + "does not provide an export named 'missing'")
                    .contains("mid-transitive.mjs:1:10");
            assertThat(failure.getSourceName())
                    .as("the offsets index the dependency, and the diagnostic says so")
                    .isEqualTo(mid.toString());
            SourceLocation location = failure.getSourceLocation();
            assertThat(location).isNotNull();
            assertThat(location.line()).isEqualTo(1);
            assertThat(location.column()).isEqualTo(10);
            String dependencySource = Files.readString(mid);
            assertThat(dependencySource.substring(location.offset(), location.endOffset()))
                    .as("the offsets select the imported name in the dependency's own source")
                    .isEqualTo("missing");
        }
    }

    @Test
    public void testALinkFailureInTheEntryModuleCarriesItsPositionInTheCallersSource()
            throws IOException {
        // The pass has the declaration that made the request, so the error can say where it is.
        // It used to be built through the location-free overload, and a caller got a message with
        // no line, column or offset at all.
        writeModule("dep-located.mjs", "export const other = 1;\n");
        String entrySource = "const before = 1;\nimport { missing as here } from './dep-located.mjs';\n";
        Path entry = writeModule("main-located.mjs", entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            JSException failure = catchThrowableOfType(
                    JSException.class, () -> context.eval(entrySource, entry.toString(), true));
            assertThat(failure).isNotNull();
            assertThat(failure.getMessage())
                    .as("the message names the module that was asked, as Node's does")
                    .isEqualTo("SyntaxError: The requested module './dep-located.mjs' "
                            + "does not provide an export named 'missing'");
            SourceLocation location = failure.getSourceLocation();
            assertThat(location).isNotNull();
            assertThat(location.line()).isEqualTo(2);
            assertThat(location.column()).isEqualTo(10);
            assertThat(entrySource.substring(location.offset(), location.endOffset()))
                    .as("the span covers the name that was asked for, in the caller's own source")
                    .isEqualTo("missing");
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
    public void testAMissingExportBehindATopLevelAwaitDependencyRunsNothing() throws IOException {
        // A graph whose dependency uses top-level await is still linked before it is evaluated, and
        // the await must not be an excuse to start running it.
        writeModule("tla-dep.mjs",
                "globalThis.dependencyBodyRan = true;\n"
                        + "export const ready = await 1;\n");
        assertLinkFailsBeforeAnythingRuns(
                "import { missing } from './tla-dep.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-tla.mjs",
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
    public void testANameProvidedByTwoStarRoutesToOneBindingIsNotAmbiguous() throws IOException {
        // Two routes to the *same* binding are not a conflict, and the pass must not invent one:
        // both middles re-export the same namespace object of the same leaf.
        writeModule("shared-leaf.mjs", "export const reached = 'leaf';\n");
        writeModule("shared-one.mjs", "export * as shared from './shared-leaf.mjs';\n");
        writeModule("shared-two.mjs",
                "import * as shared from './shared-leaf.mjs';\nexport { shared };\n");
        writeModule("shared-middle.mjs",
                "export * from './shared-one.mjs';\nexport * from './shared-two.mjs';\n");
        Path entry = writeModule("main-shared.mjs",
                "import { shared } from './shared-middle.mjs';\nglobalThis.result = shared.reached;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("leaf");
        }
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
    public void testANameTwoStarExportsProvideDifferentlyRunsNothing() throws IOException {
        // The review's reproduction. Two `export *` targets provide `x` from different bindings, so
        // the import is ambiguous — and the whole graph must be rejected before any of it runs.
        writeModule("ambiguous-a.mjs",
                "globalThis.dependencyBodyRan = true;\nexport const x = 1;\n");
        writeModule("ambiguous-b.mjs",
                "globalThis.dependencyBodyRan = true;\nexport const x = 2;\n");
        writeModule("ambiguous-middle.mjs",
                "globalThis.dependencyBodyRan = true;\n"
                        + "export * from './ambiguous-a.mjs';\n"
                        + "export * from './ambiguous-b.mjs';\n");
        assertLinkFailsBeforeAnythingRuns(
                "import { x } from './ambiguous-middle.mjs';\nglobalThis.rootBodyRan = true;\n",
                "main-ambiguous.mjs",
                "contains conflicting star exports for the name 'x'");
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
    public void testANamespaceImportOfAModuleWithAConflictingStarExportStillWorks() throws IOException {
        // Building a namespace is not asking for a name: ECMAScript leaves an ambiguous name out of
        // the namespace rather than failing, so this graph is legal and must evaluate. Node agrees
        // — `Object.keys(ns)` is `["only"]`.
        writeModule("ns-a.mjs", "export const x = 1;\nexport const only = 'a';\n");
        writeModule("ns-b.mjs", "export const x = 2;\n");
        writeModule("ns-middle.mjs", "export * from './ns-a.mjs';\nexport * from './ns-b.mjs';\n");
        Path entry = writeModule("main-namespace.mjs",
                "import * as ns from './ns-middle.mjs';\nglobalThis.result = ns.only;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("a");
        }
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
    public void testARootLinkFailureNamesNoSeparateSource() throws IOException {
        // The complement: when the failing declaration is in the text the caller passed, the
        // offsets already index that text and there is no other source to name.
        writeModule("dep-root-named.mjs", "export const other = 1;\n");
        String entrySource = "import { missing } from './dep-root-named.mjs';\n";
        Path entry = writeModule("main-root-named.mjs", entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            JSException failure = catchThrowableOfType(
                    JSException.class, () -> context.eval(entrySource, entry.toString(), true));
            assertThat(failure).isNotNull();
            assertThat(failure.getMessage()).doesNotContain("imported by");
            assertThat(failure.getSourceName()).isEqualTo(entry.toString());
            assertThat(failure.getSourceLocation()).isNotNull();
        }
    }

    @Test
    public void testAStringImportNameOfAMissingExportRunsNothing() throws IOException {
        // ES2022 arbitrary module namespace names. The scan skipped anything that was not an
        // identifier, so this asked for nothing at all.
        assertImportOfAMissingNameRunsNothing("main-string-name.mjs",
                "import { 'a-b' as local } from './dep.mjs';\nglobalThis.rootBodyRan = true;\n");
    }

    @Test
    public void testATabAroundAsInAnImportSpecifierRunsNothing() throws IOException {
        // The scan matched the single literal string " as ". Any other ECMAScript whitespace — a
        // tab here — meant the name was never asked for, so a whitespace-only edit decided whether
        // the dependency ran.
        assertImportOfAMissingNameRunsNothing("main-tab.mjs",
                "import { missing\tas here } from './dep.mjs';\nglobalThis.rootBodyRan = true;\n");
    }

    @Test
    public void testATrailingCommaInAnImportClauseRunsNothing() throws IOException {
        assertImportOfAMissingNameRunsNothing("main-trailing-comma.mjs",
                "import { missing, } from './dep.mjs';\nglobalThis.rootBodyRan = true;\n");
    }

    @Test
    public void testAUnicodeEscapeInAnImportNameRunsNothing() throws IOException {
        // `\\u006dissing` is the name `missing`, and the decoded name is what the exporting module
        // is asked for.
        assertImportOfAMissingNameRunsNothing("main-escape.mjs",
                "import { \\u006dissing } from './dep.mjs';\nglobalThis.rootBodyRan = true;\n");
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
    public void testAnImportClauseSpreadOverLinesRunsNothing() throws IOException {
        assertImportOfAMissingNameRunsNothing("main-multiline.mjs",
                "import {\n  other,\n  missing\n    as\n    here,\n} from './dep.mjs';\n"
                        + "globalThis.rootBodyRan = true;\n");
    }

    @Test
    public void testAnImportWithAnAttributeThatChoosesNoTypeIsStillChecked() throws IOException {
        // Attributes were skipped wholesale because `with { type: 'text' }` names a synthetic
        // module. An attribute that does not choose a module type says nothing about the target,
        // and the target is still ordinary JavaScript.
        assertImportOfAMissingNameRunsNothing("main-attributes.mjs",
                "import { missing } from './dep.mjs' with { unknownAttribute: 'x' };\n"
                        + "globalThis.rootBodyRan = true;\n");
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
