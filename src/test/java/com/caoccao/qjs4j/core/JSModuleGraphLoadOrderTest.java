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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A module a graph names but cannot load fails the whole graph, before any of it runs.
 * <p>
 * The link pass used to decide only the requests it found easy. A specifier that did not resolve, a
 * file it could not read, and every declaration carrying a {@code type} attribute were skipped, on
 * the reasoning that evaluation would report them anyway. Evaluation does — but evaluation walks the
 * requests in source order and loads each module as it reaches it, so by the time it reached the
 * skipped one it had already run the bodies of every module named ahead of it. Two lines were enough
 * to see it: {@code import './good.mjs'; import './absent.mjs';} left {@code globalThis.ran} set and
 * reported one evaluated module body, for a graph ECMAScript says must never have begun evaluating.
 * <p>
 * Every case here puts a dependency with a visible side effect <em>first</em>, because that is the
 * whole point: the failure was never in doubt, only its timing. Each asserts the side effect did not
 * land, that the engine's own module-body count is zero, and that the error is positioned at the
 * declaration that asked for the module rather than nowhere.
 * <p>
 * Node 24.16.0 is the reference for the timing: for each of these entry modules it reports the same
 * failure with {@code globalThis.ran} still empty.
 */
public class JSModuleGraphLoadOrderTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    /**
     * Evaluate an entry module whose graph cannot be loaded, and assert that nothing ran.
     *
     * @param entryName           the entry module's file name
     * @param entrySource         the entry module's source
     * @param expectedMessagePart text the reported error must contain
     * @return the failure, for tests that go on to inspect its position
     */
    private JSException assertGraphFailsBeforeAnythingRuns(
            String entryName, String entrySource, String expectedMessagePart) throws IOException {
        writeModule("side-effect.mjs",
                "globalThis.dependencyBodyRan = true;\nexport const ok = 1;\n");
        Path entry = writeModule(entryName, entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            JSException failure = catchThrowableOfType(
                    JSException.class, () -> context.eval(entrySource, entry.toString(), true));
            assertThat(failure).as("the graph must fail").isNotNull();
            assertThat(failure).hasMessageContaining(expectedMessagePart);
            assertThat(context.eval("String(globalThis.dependencyBodyRan)", "probe.js", false).toString())
                    .as("the dependency named before the bad one must not have run")
                    .isEqualTo("undefined");
            assertThat(context.eval("String(globalThis.rootBodyRan)", "probe.js", false).toString())
                    .as("nor the entry module's own body")
                    .isEqualTo("undefined");
            assertThat(context.getModuleBodyEvaluationCount())
                    .as("the engine agrees that nothing was evaluated")
                    .isZero();
            return failure;
        }
    }

    @Test
    public void testABytesModuleAskedForANamedExportRunsNothing() throws IOException {
        writeModule("payload-bytes.bin", "");
        assertGraphFailsBeforeAnythingRuns("main-bytes-named.mjs",
                "import './side-effect.mjs';\n"
                        + "import { missing } from './payload-bytes.bin' with { type: 'bytes' };\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Bytes modules do not support named exports");
    }

    @Test
    public void testAJsonModuleAskedForANamedExportRunsNothing() throws IOException {
        writeModule("payload.json", "{\"a\": 1}\n");
        assertGraphFailsBeforeAnythingRuns("main-json-named.mjs",
                "import './side-effect.mjs';\n"
                        + "import { a } from './payload.json' with { type: 'json' };\n"
                        + "globalThis.rootBodyRan = true;\n",
                "JSON modules do not support named exports");
    }

    @Test
    public void testAJsonModuleImportedWithoutItsTypeAttributeRunsNothing() throws IOException {
        writeModule("payload-untyped.json", "{\"a\": 1}\n");
        assertGraphFailsBeforeAnythingRuns("main-json-untyped.mjs",
                "import './side-effect.mjs';\n"
                        + "import payload from './payload-untyped.json';\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Import attribute type must be 'json'");
    }

    @Test
    public void testAMissingDirectDependencyIsReportedAtItsDeclaration() throws IOException {
        String entrySource = "import './side-effect.mjs';\n"
                + "import './absent.mjs';\n";
        JSException failure = assertGraphFailsBeforeAnythingRuns(
                "main-missing-located.mjs", entrySource, "Cannot find module");
        SourceLocation location = failure.getSourceLocation();
        assertThat(location)
                .as("the declaration that named the module is where the failure belongs")
                .isNotNull();
        assertThat(location.line()).isEqualTo(2);
        assertThat(entrySource.substring(location.offset(), location.endOffset()))
                .isEqualTo("'./absent.mjs'");
        assertThat(failure.getSourceName())
                .as("and the offsets index the entry module the caller passed in")
                .endsWith("main-missing-located.mjs");
    }

    @Test
    public void testAMissingDirectDependencyRunsNothing() throws IOException {
        // The review's reproduction.
        assertGraphFailsBeforeAnythingRuns("main-missing-direct.mjs",
                "import './side-effect.mjs';\n"
                        + "import './absent.mjs';\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Cannot find module './absent.mjs'");
    }

    @Test
    public void testAMissingTransitiveDependencyNamesTheModuleThatAskedForIt() throws IOException {
        Path mid = writeModule("mid-named.mjs",
                "import './absent-named.mjs';\nexport const m = 1;\n");
        JSException failure = assertGraphFailsBeforeAnythingRuns("main-missing-named.mjs",
                "import './side-effect.mjs';\nimport { m } from './mid-named.mjs';\n",
                "Cannot find module './absent-named.mjs'");
        assertThat(failure.getSourceName())
                .as("the offsets index the dependency that made the request")
                .isEqualTo(mid.toString());
        assertThat(failure).hasMessageContaining("mid-named.mjs:1:8");
    }

    @Test
    public void testAMissingTransitiveDependencyRunsNothing() throws IOException {
        writeModule("mid-missing.mjs", "import './absent-transitive.mjs';\nexport const m = 1;\n");
        assertGraphFailsBeforeAnythingRuns("main-missing-transitive.mjs",
                "import './side-effect.mjs';\n"
                        + "import { m } from './mid-missing.mjs';\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Cannot find module './absent-transitive.mjs'");
    }

    @Test
    public void testAStarExportNamingAMissingModuleRunsNothing() throws IOException {
        assertGraphFailsBeforeAnythingRuns("main-star-export-missing.mjs",
                "import './side-effect.mjs';\n"
                        + "export * from './absent-star.mjs';\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Cannot find module './absent-star.mjs'");
    }

    @Test
    public void testATextModuleAskedForANamedExportRunsNothing() throws IOException {
        // The review's second reproduction: the payload is fine, the request for a name from it is
        // not, and the module named before it must still not have run.
        writeModule("payload.txt", "hello\n");
        assertGraphFailsBeforeAnythingRuns("main-text-named.mjs",
                "import './side-effect.mjs';\n"
                        + "import { missing } from './payload.txt' with { type: 'text' };\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Text modules do not support named exports");
    }

    @Test
    public void testATypedJavaScriptImportOfAMissingNameRunsNothing() throws IOException {
        // A `type` attribute the engine does not treat as synthetic leaves an ordinary JavaScript
        // module, and its names have to link like any other module's. The pass used to skip the
        // whole declaration on sight of any `type` at all.
        writeModule("dep-typed.mjs", "globalThis.dependencyBodyRan = true;\nexport const other = 1;\n");
        assertGraphFailsBeforeAnythingRuns("main-typed-js.mjs",
                "import './side-effect.mjs';\n"
                        + "import { missing } from './dep-typed.mjs' with { type: 'javascript' };\n"
                        + "globalThis.rootBodyRan = true;\n",
                "does not provide an export named 'missing'");
    }

    @Test
    public void testAValidTypedGraphStillEvaluates() throws IOException {
        // The complement, and the thing that would break if the pass simply rejected everything it
        // did not recognise: a graph whose typed imports are all correct must be left alone.
        writeModule("side-effect.mjs",
                "globalThis.dependencyBodyRan = true;\nexport const ok = 1;\n");
        writeModule("payload-ok.txt", "payload");
        writeModule("payload-ok.json", "{\"a\": 1}");
        Path entry = writeModule("main-typed-ok.mjs",
                "import './side-effect.mjs';\n"
                        + "import text from './payload-ok.txt' with { type: 'text' };\n"
                        + "import data from './payload-ok.json' with { type: 'json' };\n"
                        + "globalThis.result = text + ':' + data.a;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("payload:1");
            assertThat(context.eval("String(globalThis.dependencyBodyRan)", "probe.js", false).toString())
                    .as("a graph that links must evaluate exactly as before")
                    .isEqualTo("true");
        }
    }

    @Test
    public void testAnExportFromNamingAMissingModuleRunsNothing() throws IOException {
        // `export ... from` names a module exactly as an import does, and is loaded at the same
        // stage — so it fails at the same stage too.
        assertGraphFailsBeforeAnythingRuns("main-export-from-missing.mjs",
                "import './side-effect.mjs';\n"
                        + "export { x } from './absent-reexport.mjs';\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Cannot find module './absent-reexport.mjs'");
    }

    @Test
    public void testAnUnreadableDependencyRunsNothing() throws IOException {
        // A path that resolves — it exists — but cannot be read as a module.
        Files.createDirectory(moduleDirectory.resolve("unreadable.mjs"));
        assertGraphFailsBeforeAnythingRuns("main-unreadable.mjs",
                "import './side-effect.mjs';\n"
                        + "import './unreadable.mjs';\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Cannot find module");
    }

    @Test
    public void testAnUnreadableTextPayloadRunsNothing() throws IOException {
        // A directory resolves and is "readable" in the java.nio sense, but reading it as a payload
        // fails — which must happen at link time like every other unloadable module.
        Files.createDirectory(moduleDirectory.resolve("payload-dir.txt"));
        assertGraphFailsBeforeAnythingRuns("main-unreadable-text.mjs",
                "import './side-effect.mjs';\n"
                        + "import payload from './payload-dir.txt' with { type: 'text' };\n"
                        + "globalThis.rootBodyRan = true;\n",
                "Cannot find module");
    }

    private Path writeModule(String name, String source) throws IOException {
        Path path = moduleDirectory.resolve(name);
        Files.writeString(path, source);
        return path;
    }
}
