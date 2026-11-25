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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A module specifier and an arbitrary export name are string literals, and a string literal means its
 * {@code StringValue} rather than the characters that spell it.
 * <p>
 * The token-driven link pass already agreed with the lexer about this, because it reads token values. The evaluation
 * path pulled the same literals back out of raw source with {@code substring} and kept the backslashes, so the two
 * stages disagreed: {@code import { x } from './dep.mjs'} linked against {@code ./dep.mjs} and then failed to load a
 * module called {@code ./dep.mjs}, and {@code import { "a-b" as y }} linked against the export {@code a-b} and then
 * asked the namespace for a property spelled with a backslash — after the dependency had run.
 * <p>
 * These need a graph on disk, which {@code BaseJavetTest} cannot express: it evaluates one source under one fixed name
 * with no module resolver. The single-module halves are checked against V8 in {@link JSModuleIdentifierSpellingTest};
 * the expected values here were taken from Node 24.16.0 running the same files, and are noted per case where they are
 * not simply "it works".
 */
public class JSModuleNameResolutionTest extends ModuleFileTest {

    @Test
    public void testAHexEscapeInASpecifier() throws IOException {
        // Node: 5.
        writeModule("dep2.mjs", "export const z = 5;\n");
        writeStandardDependency();
        assertThat(
                evaluateModuleResult("main-hex.mjs", "import { z } from \"./de\\x702.mjs\";\nglobalThis.result = z;\n"))
                .isEqualTo("5");
    }

    @Test
    public void testALineContinuationInASpecifier() throws IOException {
        // A LineContinuation contributes nothing to the StringValue, so this names ./dep.mjs.
        // Node: 42.
        writeStandardDependency();
        assertThat(evaluateModuleResult("main-continuation.mjs",
                "import { x } from \"./de\\\np.mjs\";\nglobalThis.result = x;\n")).isEqualTo("42");
    }

    @Test
    public void testAnEscapedDelimiterInASpecifier() throws IOException {
        // The closing quote is the third one, not the second: an escape-blind scan for the next
        // quote character truncates the specifier. Node: 9.
        writeModule("a'b.mjs", "export const q = 9;\n");
        assertThat(evaluateModuleResult("main-escaped-quote.mjs",
                "import { q } from './a\\'b.mjs';\nglobalThis.result = q;\n")).isEqualTo("9");
    }

    @Test
    public void testAnEscapedImportedNameAgainstAPlainExportName() throws IOException {
        // The review's third reproduction, which used to fail only after the dependency had run.
        // Node: 1.
        writeStandardDependency();
        assertThat(evaluateModuleResult("main-escaped-import.mjs",
                "import { \"a\\u002db\" as v } from './dep.mjs';\nglobalThis.result = v;\n")).isEqualTo("1");
    }

    @Test
    public void testAnEscapedLocalImportName() throws IOException {
        // The local binding is `y`, so `y` is the name the module body can use. Node: 42.
        writeStandardDependency();
        assertThat(evaluateModuleResult("main-escaped-local.mjs",
                "import { x as \\u0079 } from './dep.mjs';\nglobalThis.result = y;\n")).isEqualTo("42");
    }

    @Test
    public void testAnEscapedNamespaceAlias() throws IOException {
        writeStandardDependency();
        assertThat(evaluateModuleResult("main-escaped-namespace.mjs",
                "import * as m\\u00f3dulo from './dep.mjs';\nglobalThis.result = módulo.x;\n")).isEqualTo("42");
    }

    @Test
    public void testAnEscapedSpecifier() throws IOException {
        // The review's first reproduction. Node: 42.
        writeStandardDependency();
        assertThat(evaluateModuleResult("main-escaped-specifier.mjs",
                "import { x } from \"./d\\u0065p.mjs\";\nglobalThis.result = x;\n")).isEqualTo("42");
    }

    @Test
    public void testAPlainImportedNameAgainstAnEscapedExportName() throws IOException {
        // The review's second reproduction, the other way round. Node: 2.
        writeModule("dep-escaped-export.mjs", "const y = 2;\nexport { y as \"a\\u002db\" };\n");
        assertThat(evaluateModuleResult("main-plain-import.mjs",
                "import { \"a-b\" as v } from './dep-escaped-export.mjs';\nglobalThis.result = v;\n")).isEqualTo("2");
    }

    @Test
    public void testAUnicodeFunctionAndClassImportedAcrossModules() throws IOException {
        // The review's Unicode reproductions. Node: 7:function.
        writeStandardDependency();
        assertThat(evaluateModuleResult("main-unicode-import.mjs",
                "import { café, Café } from './dep.mjs';\n" + "globalThis.result = café() + ':' + typeof Café;\n"))
                .isEqualTo("7:function");
    }

    @Test
    public void testAUnicodeNameIsReExported() throws IOException {
        // Node: 7.
        writeStandardDependency();
        writeModule("reexport.mjs", "export { café as coffee } from './dep.mjs';\n");
        assertThat(evaluateModuleResult("main-reexport.mjs",
                "import { coffee } from './reexport.mjs';\nglobalThis.result = coffee();\n")).isEqualTo("7");
    }

    @Test
    public void testAUnicodeNamespaceAlias() throws IOException {
        // The review's reproduction: this used to run the dependency and then fail with
        // "módulo is not defined". Node: 42.
        writeStandardDependency();
        assertThat(evaluateModuleResult("main-unicode-namespace.mjs",
                "import * as módulo from './dep.mjs';\nglobalThis.result = módulo.x;\n")).isEqualTo("42");
    }

    private void writeStandardDependency() throws IOException {
        writeModule("dep.mjs", "export const x = 42;\n" + "export function café() { return 7; }\n"
                + "export class Café {}\n" + "const y = 1;\n" + "export { y as \"a-b\" };\n");
    }
}
