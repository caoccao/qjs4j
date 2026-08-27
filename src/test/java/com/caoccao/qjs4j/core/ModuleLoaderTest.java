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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct tests for {@link ModuleLoader}: the realm's module cache, how a specifier becomes a file,
 * the three payload kinds it reads, and the ordering rules that decide when a module body may run.
 * <p>
 * All of it used to be reachable only by evaluating one module that imports another, so the parts
 * that are hardest to arrange from source — a cache entry evicted after a failed load, a JSON
 * payload without its type attribute, GatherAvailableAncestors over a graph that is half arrived —
 * were also the least covered. Those are exactly the transitions that break quietly: a record left
 * behind after a failure makes the next import of the same specifier answer with a half-built
 * module rather than trying again.
 * <p>
 * The cases below drive the loader through its own package-private surface and read the answers off
 * module records, so a failure names the transition rather than the module that tripped over it.
 */
public class ModuleLoaderTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    private ModuleLoader loader() {
        return context.moduleLoader();
    }

    private JSDynamicImportModule moduleRecord(String resolvedSpecifier) {
        return new JSDynamicImportModule(resolvedSpecifier, loader().createModuleNamespaceObject());
    }

    private String namespaceText(JSDynamicImportModule moduleRecord, String exportName) {
        return moduleRecord.namespace().get(PropertyKey.fromString(exportName)).toString();
    }

    @Test
    public void testAMissingModuleIsATypeErrorThatNamesTheSpecifier() {
        assertThatThrownBy(() -> loader().resolveDynamicImportSpecifier(
                "./nope.mjs", moduleDirectory.resolve("main.mjs").toString(), "./nope.mjs"))
                .isInstanceOf(JSException.class);
        assertThat(context.getPendingException().toString()).contains("Cannot find module './nope.mjs'");
        context.clearPendingException();
        assertThat(loader().moduleCacheSize()).as("a specifier that resolves to nothing caches nothing").isZero();
    }

    @Test
    public void testAModuleThatCouldNotBeReadIsNotLeftHalfBuiltInTheCache() throws IOException {
        // A specifier can resolve to something that exists and still not be readable as a module —
        // a directory is the ordinary case. The record registered before the read has to go, or the
        // next import of the same specifier finds it, sees LOADING, and answers with a namespace
        // that was never populated instead of failing again.
        Path directory = moduleDirectory.resolve("subdirectory");
        Files.createDirectory(directory);
        String resolvedSpecifier = directory.toString();

        assertThatThrownBy(() -> loader().loadJSDynamicImportModule(resolvedSpecifier, new HashSet<>(), null))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("Cannot find module");
        context.clearPendingException();
        assertThat(loader().cachedModule(resolvedSpecifier))
                .as("a load that never read a byte must not leave a record behind")
                .isNull();

        assertThatThrownBy(() -> loader().loadJSDynamicImportModule(resolvedSpecifier, new HashSet<>(), null))
                .as("so the second import fails the same way rather than succeeding emptily")
                .isInstanceOf(JSException.class);
        context.clearPendingException();
    }

    @Test
    public void testAsyncEvaluationOrderIsHandedOutInSequence() {
        // ES2024 16.2.1.5.2 runs the modules that become ready in the order they were deferred, so
        // the stamps have to be distinct and increasing.
        int first = loader().nextAsyncEvaluationOrder();
        int second = loader().nextAsyncEvaluationOrder();
        int third = loader().nextAsyncEvaluationOrder();
        assertThat(second).isEqualTo(first + 1);
        assertThat(third).isEqualTo(second + 1);
    }

    @Test
    public void testBytesImportProducesAnImmutableUint8Array() throws IOException {
        String resolvedSpecifier = write("payload.bin", "AB");
        JSDynamicImportModule moduleRecord = loader().loadJSDynamicImportModule(
                resolvedSpecifier, new HashSet<>(), Map.of("type", "bytes"));

        assertThat(moduleRecord.status()).isEqualTo(JSDynamicImportModule.Status.EVALUATED);
        JSValue defaultExport = moduleRecord.namespace().get(PropertyKey.fromString("default"));
        assertThat(defaultExport).isInstanceOfSatisfying(JSUint8Array.class, uint8Array -> {
            assertThat(uint8Array.getLength()).isEqualTo(2);
            assertThat(uint8Array.getBuffer().isImmutable())
                    .as("a bytes payload is read-only, so a module cannot rewrite what it imported")
                    .isTrue();
        });
        assertThat(moduleRecord.rawSource()).as("bytes are not source").isEmpty();
    }

    @Test
    public void testCacheHoldsRecordsUntilTheyAreEvictedOrTheRealmIsCleared() {
        JSDynamicImportModule first =
                new JSDynamicImportModule("/modules/a.mjs", loader().createModuleNamespaceObject());
        JSDynamicImportModule second =
                new JSDynamicImportModule("/modules/b.mjs", loader().createModuleNamespaceObject());
        assertThat(loader().moduleCacheSize()).isZero();
        assertThat(loader().cachedModule("/modules/a.mjs")).isNull();

        loader().cacheModule("/modules/a.mjs", first);
        loader().cacheModule("/modules/b.mjs", second);
        assertThat(loader().moduleCacheSize()).isEqualTo(2);
        assertThat(loader().cachedModule("/modules/a.mjs")).isSameAs(first);

        loader().removeCachedModule("/modules/a.mjs");
        assertThat(loader().cachedModule("/modules/a.mjs")).isNull();
        assertThat(loader().moduleCacheSize()).isEqualTo(1);

        loader().clearModuleCache();
        assertThat(loader().moduleCacheSize()).isZero();
    }

    @Test
    public void testCacheKeyQualifiesOnlyTheImportsWhoseTypeChangesThePayload() {
        // The same file imported as text and as bytes is two different modules, so they cannot
        // share a cache entry. Every other attribute leaves the identity alone.
        String specifier = "/modules/dep.mjs";
        assertThat(loader().getDynamicImportCacheKey(specifier, null)).isEqualTo(specifier);
        assertThat(loader().getDynamicImportCacheKey(specifier, Map.of())).isEqualTo(specifier);
        assertThat(loader().getDynamicImportCacheKey(specifier, Map.of("type", "json"))).isEqualTo(specifier);
        assertThat(loader().getDynamicImportCacheKey(specifier, Map.of("type", "text")))
                .isNotEqualTo(specifier)
                .isNotEqualTo(loader().getDynamicImportCacheKey(specifier, Map.of("type", "bytes")));
    }

    @Test
    public void testClearingTheCacheAlsoDropsImportMetaObjects() {
        // import.meta is per module, so a cache that keeps it across a clear would hand a reloaded
        // module the object its previous incarnation had written to.
        JSObject before = loader().createImportMetaObject("/modules/a.mjs");
        before.set(PropertyKey.fromString("marker"), new JSString("stale"));
        loader().clearModuleCache();
        JSObject after = loader().createImportMetaObject("/modules/a.mjs");
        assertThat(after).isNotSameAs(before);
        assertThat(after.has(PropertyKey.fromString("marker"))).isFalse();
    }

    @Test
    public void testGatherAvailableAncestorsTakesOnlyTheDependentsThatHaveNoneLeftToWaitFor() {
        // ES2024 16.2.1.5.2.4. One dependency of a two-dependency module has arrived, so the
        // dependent is not ready; the one-dependency module is.
        JSDynamicImportModule finished = moduleRecord("/modules/finished.mjs");
        JSDynamicImportModule ready = moduleRecord("/modules/ready.mjs");
        ready.setPendingAsyncDependencyCount(1);
        JSDynamicImportModule stillWaiting = moduleRecord("/modules/waiting.mjs");
        stillWaiting.setPendingAsyncDependencyCount(2);
        finished.pendingDependents().add(ready);
        finished.pendingDependents().add(stillWaiting);

        List<JSDynamicImportModule> execList = new ArrayList<>();
        loader().gatherAvailableAncestors(finished, execList);

        assertThat(execList).containsExactly(ready);
        assertThat(stillWaiting.pendingAsyncDependencyCount())
                .as("the dependency that arrived is counted off even when the module is not ready")
                .isEqualTo(1);
        assertThat(finished.pendingDependents()).as("each dependent is gathered once").isEmpty();
    }

    @Test
    public void testGatherAvailableAncestorsWalksOnPastAModuleThatWillRunSynchronously() {
        // A ready module without top-level await runs immediately, so whatever was waiting on it
        // becomes available in the same pass. One with top-level await does not, and its own
        // dependents wait for its promise instead.
        JSDynamicImportModule finished = moduleRecord("/modules/finished.mjs");
        JSDynamicImportModule middle = moduleRecord("/modules/middle.mjs");
        middle.setPendingAsyncDependencyCount(1);
        JSDynamicImportModule top = moduleRecord("/modules/top.mjs");
        top.setPendingAsyncDependencyCount(1);
        finished.pendingDependents().add(middle);
        middle.pendingDependents().add(top);

        List<JSDynamicImportModule> execList = new ArrayList<>();
        loader().gatherAvailableAncestors(finished, execList);
        assertThat(execList).containsExactly(middle, top);

        JSDynamicImportModule awaitingFinished = moduleRecord("/modules/awaiting-finished.mjs");
        JSDynamicImportModule awaiting = moduleRecord("/modules/awaiting.mjs");
        awaiting.setHasTLA(true);
        awaiting.setPendingAsyncDependencyCount(1);
        JSDynamicImportModule aboveAwaiting = moduleRecord("/modules/above-awaiting.mjs");
        aboveAwaiting.setPendingAsyncDependencyCount(1);
        awaitingFinished.pendingDependents().add(awaiting);
        awaiting.pendingDependents().add(aboveAwaiting);

        List<JSDynamicImportModule> awaitingExecList = new ArrayList<>();
        loader().gatherAvailableAncestors(awaitingFinished, awaitingExecList);
        assertThat(awaitingExecList).containsExactly(awaiting);
    }

    @Test
    public void testImportMetaIsOneObjectPerModuleAndCarriesItsUrl() {
        JSObject first = loader().createImportMetaObject("/modules/a.mjs");
        assertThat(loader().createImportMetaObject("/modules/a.mjs"))
                .as("import.meta is the same object every time a module asks for it")
                .isSameAs(first);
        assertThat(first.get(PropertyKey.fromString("url"))).hasToString("/modules/a.mjs");
        assertThat(first.getPrototype()).as("import.meta has a null prototype").isNull();
        assertThat(loader().createImportMetaObject("/modules/b.mjs")).isNotSameAs(first);

        // A synthetic name is not a URL, and neither is no name at all.
        assertThat(loader().createImportMetaObject("<eval>").has(PropertyKey.fromString("url"))).isFalse();
        assertThat(loader().createImportMetaObject("").has(PropertyKey.fromString("url"))).isFalse();
        assertThat(loader().createImportMetaObject(null))
                .as("a module with no name still gets exactly one import.meta")
                .isSameAs(loader().createImportMetaObject(""));
    }

    @Test
    public void testImportingAnUnreadableSpecifierRejectsEveryTime() throws IOException {
        // The same defect as the case above, seen from the language: the second `import()` used to
        // resolve with an empty namespace because the failed first one left its record cached.
        Files.createDirectory(moduleDirectory.resolve("subdirectory"));
        Path entry = moduleDirectory.resolve("entry.mjs");
        String entrySource = """
                globalThis.outcomes = [];
                function attempt(label) {
                    return import('./subdirectory').then(
                        () => globalThis.outcomes.push(label + ':resolved'),
                        (e) => globalThis.outcomes.push(label + ':' + e.name));
                }
                attempt('first').then(() => attempt('second'));
                """;
        Files.writeString(entry, entrySource, StandardCharsets.UTF_8);

        context.eval(entrySource, entry.toString(), true);
        context.processMicrotasks();
        assertThat(context.eval("globalThis.outcomes.join(',')", "probe.js", false))
                .hasToString("first:TypeError,second:TypeError");
    }

    @Test
    public void testJsonImportParsesThroughTheRealmsJson() throws IOException {
        String resolvedSpecifier = write("data.json", "{\"answer\": 42}");
        JSDynamicImportModule moduleRecord = loader().loadJSDynamicImportModule(
                resolvedSpecifier, new HashSet<>(), Map.of("type", "json"));

        assertThat(moduleRecord.status()).isEqualTo(JSDynamicImportModule.Status.EVALUATED);
        assertThat(moduleRecord.explicitExportNames()).containsExactly("default");
        JSValue defaultExport = moduleRecord.namespace().get(PropertyKey.fromString("default"));
        assertThat(defaultExport).isInstanceOfSatisfying(JSObject.class, jsObject ->
                assertThat(jsObject.get(PropertyKey.fromString("answer"))).hasToString("42"));
    }

    @Test
    public void testJsonImportWithoutItsTypeAttributeKeepsItsErrorForTheNextImport() throws IOException {
        // A JSON module without `with { type: 'json' }` is a TypeError. Unlike a module that could
        // not be read at all, this one is kept: it failed evaluation, and ES2024 16.2.1.5 says a
        // module that failed evaluation rethrows the same error object every time it is imported.
        String resolvedSpecifier = write("data.json", "{\"answer\": 42}");
        assertThatThrownBy(() -> loader().loadJSDynamicImportModule(resolvedSpecifier, new HashSet<>(), null))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("Import attribute type must be 'json'");
        context.clearPendingException();

        JSDynamicImportModule cachedRecord = loader().cachedModule(resolvedSpecifier);
        assertThat(cachedRecord).isNotNull();
        assertThat(cachedRecord.status()).isEqualTo(JSDynamicImportModule.Status.EVALUATED_ERROR);
        assertThatThrownBy(() -> loader().loadJSDynamicImportModule(resolvedSpecifier, new HashSet<>(), null))
                .isInstanceOf(JSException.class)
                .extracting(thrown -> ((JSException) thrown).getErrorValue())
                .as("the same error object, not a new one")
                .isSameAs(cachedRecord.evaluationError());
        context.clearPendingException();
    }

    @Test
    public void testMalformedJsonFailsAsASyntaxErrorRatherThanAJavaException() {
        assertThatThrownBy(() -> loader().parseJsonModuleSource("{ not json }"))
                .isInstanceOf(JSException.class);
        context.clearPendingException();
        assertThat(loader().parseJsonModuleSource("[1, 2]")).isInstanceOf(JSArray.class);
    }

    @Test
    public void testNormalizeModuleSpecifierFoldsRedundantSegments() {
        assertThat(loader().normalizeModuleSpecifier("modules/./a.mjs"))
                .isEqualTo(Path.of("modules", "a.mjs").toString());
        assertThat(loader().normalizeModuleSpecifier("modules/sub/../a.mjs"))
                .isEqualTo(Path.of("modules", "a.mjs").toString());
        assertThat(loader().normalizeModuleSpecifier("")).isEmpty();
        assertThat(loader().normalizeModuleSpecifier(null)).isEmpty();
    }

    @Test
    public void testReadyForSyncExecutionRefusesAGraphThatHasToAwait() throws IOException {
        // ReadyForSyncExecution from the import-defer proposal. A module that awaits at top level
        // cannot be evaluated synchronously, and neither can anything that imports one.
        String awaiting = write("awaiting.mjs", "await Promise.resolve();\nexport const value = 1;\n");
        String importer = write("importer.mjs", "import { value } from './awaiting.mjs';\n");
        String plain = write("plain.mjs", "export const value = 1;\n");

        assertThat(loader().readyForSyncExecution(plain, new HashSet<>())).isTrue();
        assertThat(loader().readyForSyncExecution(awaiting, new HashSet<>())).isFalse();
        assertThat(loader().readyForSyncExecution(importer, new HashSet<>())).isFalse();
        // A file that is not there cannot be shown to need awaiting, and the error belongs to
        // whoever tries to load it rather than to this predicate.
        assertThat(loader().readyForSyncExecution(
                moduleDirectory.resolve("absent.mjs").toString(), new HashSet<>())).isTrue();
    }

    @Test
    public void testReadyForSyncExecutionTerminatesOnACycleAndBelievesAnEvaluatedRecord() throws IOException {
        String first = write("cycle-a.mjs", "import './cycle-b.mjs';\n");
        write("cycle-b.mjs", "import './cycle-a.mjs';\n");
        assertThat(loader().readyForSyncExecution(first, new HashSet<>()))
                .as("a cycle is walked once, not forever")
                .isTrue();

        // A record that has already finished is ready whatever its source says, and one that is
        // still evaluating is not.
        String awaiting = write("evaluated-awaiting.mjs", "await Promise.resolve();\n");
        JSDynamicImportModule evaluated = moduleRecord(awaiting);
        evaluated.setStatus(JSDynamicImportModule.Status.EVALUATED);
        loader().cacheModule(awaiting, evaluated);
        assertThat(loader().readyForSyncExecution(awaiting, new HashSet<>())).isTrue();

        evaluated.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
        assertThat(loader().readyForSyncExecution(awaiting, new HashSet<>())).isFalse();
    }

    @Test
    public void testSpecifierResolutionIsRelativeToTheReferrer() throws IOException {
        String dependency = write("dep.mjs", "export const value = 1;\n");
        String referrer = write("main.mjs", "import './dep.mjs';\n");

        assertThat(loader().resolveDynamicImportSpecifier("./dep.mjs", referrer, "./dep.mjs"))
                .isEqualTo(dependency);
        assertThat(loader().resolveDynamicImportSpecifier(dependency, null, dependency))
                .as("an absolute specifier needs no referrer")
                .isEqualTo(dependency);
        // A synthetic referrer name is not a directory, so it contributes nothing.
        assertThatThrownBy(() -> loader().resolveDynamicImportSpecifier("./dep.mjs", "<eval>", "./dep.mjs"))
                .isInstanceOf(JSException.class);
        context.clearPendingException();
    }

    @Test
    public void testTextImportIsDataAndNotSource() throws IOException {
        // The payload used to go through the module normaliser, so a text file whose bytes happened
        // to look like a declaration was tokenised and compiled as JavaScript.
        String content = "export {}; this is arbitrary text";
        String resolvedSpecifier = write("payload.txt", content);
        JSDynamicImportModule moduleRecord = loader().loadJSDynamicImportModule(
                resolvedSpecifier, new HashSet<>(), Map.of("type", "text"));

        assertThat(moduleRecord.status()).isEqualTo(JSDynamicImportModule.Status.EVALUATED);
        assertThat(namespaceText(moduleRecord, "default")).isEqualTo(content);
        assertThat(moduleRecord.exportOrigins()).containsEntry("default", resolvedSpecifier);
        // And the record is cached under the type-qualified key, so importing the same file as
        // source afterwards is a different module.
        assertThat(loader().cachedModule(loader().getDynamicImportCacheKey(
                resolvedSpecifier, Map.of("type", "text")))).isSameAs(moduleRecord);
    }

    @Test
    public void testTransformedSourceIsRecognisedByTheNameItIsEvaluatedUnder() throws IOException {
        // A module with exports is evaluated by handing its rewritten source back to eval under the
        // same file name. Anything that reads that text as a module sees one that exports nothing,
        // so the eval pipeline has to be able to tell the two apart.
        String resolvedSpecifier = write("exporting.mjs", "export const value = 1;\n");
        JSDynamicImportModule moduleRecord = moduleRecord(resolvedSpecifier);
        moduleRecord.setRawSource("export const value = 1;\n");
        moduleRecord.setTransformedSource("const value = 1;\n");
        loader().cacheModule(resolvedSpecifier, moduleRecord);

        assertThat(loader().isTransformedModuleSource("const value = 1;\n", resolvedSpecifier)).isTrue();
        assertThat(loader().isTransformedModuleSource("export const value = 1;\n", resolvedSpecifier))
                .as("the author's own text is not the generated text")
                .isFalse();
        // A synthetic file name never names a module, and neither does one nothing has cached.
        assertThat(loader().isTransformedModuleSource("const value = 1;\n", "<eval>")).isFalse();
        assertThat(loader().isTransformedModuleSource("const value = 1;\n", null)).isFalse();
        assertThat(loader().isTransformedModuleSource("const value = 1;\n", "unresolvable.mjs")).isFalse();
        assertThat(context.hasPendingException())
                .as("an unresolvable name is answered, not thrown, and leaves no exception behind")
                .isFalse();
    }

    private String write(String fileName, String content) throws IOException {
        Path path = moduleDirectory.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path.toString();
    }
}
