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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A module specifier and an arbitrary export name are string literals, and a string literal means
 * its {@code StringValue} rather than the characters that spell it.
 * <p>
 * The token-driven link pass already agreed with the lexer about this, because it reads token
 * values. The evaluation path pulled the same literals back out of raw source with {@code substring}
 * and kept the backslashes, so the two stages disagreed: {@code import { x } from './dep.mjs'}
 * linked against {@code ./dep.mjs} and then failed to load a module called
 * {@code ./dep.mjs}, and {@code import { "a-b" as y }} linked against the export
 * {@code a-b} and then asked the namespace for a property spelled with a backslash — after the
 * dependency had run.
 * <p>
 * These need a graph on disk, which {@code BaseJavetTest} cannot express: it evaluates one source
 * under one fixed name with no module resolver. The single-module halves are checked against V8 in
 * {@link JSModuleIdentifierSpellingTest}; the expected values here were taken from Node 24.16.0
 * running the same files, and are noted per case where they are not simply "it works".
 */
public class JSModuleNameResolutionTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    /**
     * Evaluate an entry module and return what it left in {@code globalThis.result}.
     *
     * @param entryName   the entry module's file name
     * @param entrySource the entry module's source
     * @return the string form of the result
     */
    private String evaluateEntry(String entryName, String entrySource) throws IOException {
        Path entry = writeModule(entryName, entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(entrySource, entry.toString(), true);
            return context.eval("String(globalThis.result)", "probe.js", false).toString();
        }
    }

    @Test
    public void testAHexEscapeInASpecifier() throws IOException {
        // Node: 5.
        writeModule("dep2.mjs", "export const z = 5;\n");
        writeStandardDependency();
        assertThat(evaluateEntry("main-hex.mjs",
                "import { z } from \"./de\\x702.mjs\";\nglobalThis.result = z;\n"))
                .isEqualTo("5");
    }

    @Test
    public void testALineContinuationInASpecifier() throws IOException {
        // A LineContinuation contributes nothing to the StringValue, so this names ./dep.mjs.
        // Node: 42.
        writeStandardDependency();
        assertThat(evaluateEntry("main-continuation.mjs",
                "import { x } from \"./de\\\np.mjs\";\nglobalThis.result = x;\n"))
                .isEqualTo("42");
    }

    @Test
    public void testAPlainImportedNameAgainstAnEscapedExportName() throws IOException {
        // The review's second reproduction, the other way round. Node: 2.
        writeModule("dep-escaped-export.mjs",
                "const y = 2;\nexport { y as \"a\\u002db\" };\n");
        assertThat(evaluateEntry("main-plain-import.mjs",
                "import { \"a-b\" as v } from './dep-escaped-export.mjs';\nglobalThis.result = v;\n"))
                .isEqualTo("2");
    }

    @Test
    public void testAUnicodeFunctionAndClassImportedAcrossModules() throws IOException {
        // The review's Unicode reproductions. Node: 7:function.
        writeStandardDependency();
        assertThat(evaluateEntry("main-unicode-import.mjs",
                "import { café, Café } from './dep.mjs';\n"
                        + "globalThis.result = café() + ':' + typeof Café;\n"))
                .isEqualTo("7:function");
    }

    @Test
    public void testAUnicodeNameIsReExported() throws IOException {
        // Node: 7.
        writeStandardDependency();
        writeModule("reexport.mjs", "export { café as coffee } from './dep.mjs';\n");
        assertThat(evaluateEntry("main-reexport.mjs",
                "import { coffee } from './reexport.mjs';\nglobalThis.result = coffee();\n"))
                .isEqualTo("7");
    }

    @Test
    public void testAUnicodeNamespaceAlias() throws IOException {
        // The review's reproduction: this used to run the dependency and then fail with
        // "módulo is not defined". Node: 42.
        writeStandardDependency();
        assertThat(evaluateEntry("main-unicode-namespace.mjs",
                "import * as módulo from './dep.mjs';\nglobalThis.result = módulo.x;\n"))
                .isEqualTo("42");
    }

    @Test
    public void testAnEscapedDelimiterInASpecifier() throws IOException {
        // The closing quote is the third one, not the second: an escape-blind scan for the next
        // quote character truncates the specifier. Node: 9.
        writeModule("a'b.mjs", "export const q = 9;\n");
        assertThat(evaluateEntry("main-escaped-quote.mjs",
                "import { q } from './a\\'b.mjs';\nglobalThis.result = q;\n"))
                .isEqualTo("9");
    }

    @Test
    public void testAnEscapedImportedNameAgainstAPlainExportName() throws IOException {
        // The review's third reproduction, which used to fail only after the dependency had run.
        // Node: 1.
        writeStandardDependency();
        assertThat(evaluateEntry("main-escaped-import.mjs",
                "import { \"a\\u002db\" as v } from './dep.mjs';\nglobalThis.result = v;\n"))
                .isEqualTo("1");
    }

    @Test
    public void testAnEscapedLocalImportName() throws IOException {
        // The local binding is `y`, so `y` is the name the module body can use. Node: 42.
        writeStandardDependency();
        assertThat(evaluateEntry("main-escaped-local.mjs",
                "import { x as \\u0079 } from './dep.mjs';\nglobalThis.result = y;\n"))
                .isEqualTo("42");
    }

    @Test
    public void testAnEscapedNamespaceAlias() throws IOException {
        writeStandardDependency();
        assertThat(evaluateEntry("main-escaped-namespace.mjs",
                "import * as m\\u00f3dulo from './dep.mjs';\nglobalThis.result = módulo.x;\n"))
                .isEqualTo("42");
    }

    @Test
    public void testAnEscapedSpecifier() throws IOException {
        // The review's first reproduction. Node: 42.
        writeStandardDependency();
        assertThat(evaluateEntry("main-escaped-specifier.mjs",
                "import { x } from \"./d\\u0065p.mjs\";\nglobalThis.result = x;\n"))
                .isEqualTo("42");
    }

    private Path writeModule(String name, String source) throws IOException {
        Path path = moduleDirectory.resolve(name);
        Files.writeString(path, source);
        return path;
    }

    private void writeStandardDependency() throws IOException {
        writeModule("dep.mjs",
                "export const x = 42;\n"
                        + "export function café() { return 7; }\n"
                        + "export class Café {}\n"
                        + "const y = 1;\n"
                        + "export { y as \"a-b\" };\n");
    }
}
