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
 * The ES module loader rewrites module source textually before evaluating it (see
 * {@code JSContext.parseDynamicImportModuleSource}). These tests pin the module source shapes the
 * transformer actually supports, so the eventual move to a compiler-based module pipeline has a
 * behavioural safety net, and cover the hardening that keeps a mis-scanned name from being spliced
 * into generated source.
 * <p>
 * The {@code testKnownLimitation*} tests document behaviour that is wrong per the ECMAScript
 * specification but not fixable at this layer, because the transformer classifies lines by
 * string-matching rather than by parsing. They exist so the compiler-based module pipeline that
 * replaces this code has an explicit checklist of what it must start getting right.
 */
public class JSModuleSourceTransformTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    private JSValue evalModule(String dependencySource, String mainSource) throws IOException {
        Files.writeString(moduleDirectory.resolve("dep.mjs"), dependencySource);
        Path mainPath = moduleDirectory.resolve("main.mjs");
        Files.writeString(mainPath, mainSource);
        JSValue result = context.eval(mainSource, mainPath.toString(), true);
        context.processMicrotasks();
        return result;
    }

    private String evalModuleToString(String dependencySource, String mainSource) throws IOException {
        evalModule(dependencySource, mainSource);
        return JSTypeConversions.toString(context, context.eval("String(globalThis.__out)")).value();
    }

    @Test
    public void testExportClassIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "export class C { m() { return 6 } }",
                """
                        import { C } from './dep.mjs';
                        globalThis.__out = new C().m();"""))
                .isEqualTo("6");
    }

    @Test
    public void testExportConstIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "export const v = 1;",
                """
                        import { v } from './dep.mjs';
                        globalThis.__out = v;"""))
                .isEqualTo("1");
    }

    @Test
    public void testExportDefaultAnonymousClassIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "export default class { m() { return 9 } };",
                """
                        import d from './dep.mjs';
                        globalThis.__out = new d().m() + ':' + d.name;"""))
                .isEqualTo("9:default");
    }

    @Test
    public void testExportDefaultExpressionIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "export default 7;",
                """
                        import d from './dep.mjs';
                        globalThis.__out = d;"""))
                .isEqualTo("7");
    }

    @Test
    public void testExportDefaultNamedClassKeepsItsName() throws IOException {
        assertThat(evalModuleToString(
                "export default class K { m() { return 10 } };",
                """
                        import d from './dep.mjs';
                        globalThis.__out = new d().m() + ':' + d.name;"""))
                .isEqualTo("10:K");
    }

    @Test
    public void testExportDestructuringIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "export const { p, q } = { p: 1, q: 2 };",
                """
                        import { p, q } from './dep.mjs';
                        globalThis.__out = p + q;"""))
                .isEqualTo("3");
    }

    @Test
    public void testExportFunctionIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "export function f() { return 3 }",
                """
                        import { f } from './dep.mjs';
                        globalThis.__out = f();"""))
                .isEqualTo("3");
    }

    @Test
    public void testExportGeneratorIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "export function* g() { yield 4 }",
                """
                        import { g } from './dep.mjs';
                        globalThis.__out = [...g()][0];"""))
                .isEqualTo("4");
    }

    @Test
    public void testExportIsIgnoredInsideCommentsAndStrings() throws IOException {
        assertThat(evalModuleToString(
                """
                        /* export const fake = 1 */
                        const inString = "export const alsoFake = 1";
                        const inTemplate = `export const stillFake = 1`;
                        export const v = 16;""",
                """
                        import { v } from './dep.mjs';
                        globalThis.__out = v + ':' + typeof globalThis.fake;"""))
                .isEqualTo("16:undefined");
    }

    @Test
    public void testExportLetWithMultipleDeclaratorsIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "export let a = 1, b = 2;",
                """
                        import { a, b } from './dep.mjs';
                        globalThis.__out = a + b;"""))
                .isEqualTo("3");
    }

    @Test
    public void testExportListIsImportable() throws IOException {
        assertThat(evalModuleToString(
                """
                        const z = 11;
                        export { z };""",
                """
                        import { z } from './dep.mjs';
                        globalThis.__out = z;"""))
                .isEqualTo("11");
    }

    @Test
    public void testExportListWithRenameIsImportable() throws IOException {
        assertThat(evalModuleToString(
                """
                        const z = 12;
                        export { z as w };""",
                """
                        import { w } from './dep.mjs';
                        globalThis.__out = w;"""))
                .isEqualTo("12");
    }

    @Test
    public void testIndentedExportIsImportable() throws IOException {
        assertThat(evalModuleToString(
                "    export const v = 14;",
                """
                        import { v } from './dep.mjs';
                        globalThis.__out = v;"""))
                .isEqualTo("14");
    }

    @Test
    public void testKnownLimitationExportMustStartItsLine() {
        // Per spec this exports `v`. The scanner only recognises `export` as the first token on a
        // line, so the binding is absent from the module namespace.
        assertThatThrownBy(() -> evalModule(
                "const t = 1; export const v = 15;",
                """
                        import { v } from './dep.mjs';
                        globalThis.__out = v;"""))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("does not provide an export named 'v'");
    }

    @Test
    public void testKnownLimitationRegexpLiteralDesynchronisesTheScanner() {
        // `maskModuleComments` masks strings, templates and comments, but not regexp literals, so
        // the quote inside /"/ desynchronises its quote state for the rest of the line and the
        // `export` that follows is never seen.
        assertThatThrownBy(() -> evalModule(
                """
                        const r = /"/; export const v = 20;""",
                """
                        import { v } from './dep.mjs';
                        globalThis.__out = v;"""))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("does not provide an export named 'v'");
    }

    @Test
    public void testKnownLimitationStaticImportMustBeAloneOnItsLine() {
        // Per spec the import is hoisted and `v` is in scope. The scanner only recognises an
        // import declaration that occupies its whole line.
        assertThatThrownBy(() -> evalModule(
                "export const v = 22;",
                "import { v } from './dep.mjs'; globalThis.__out = v;"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("v is not defined");
    }

    @Test
    public void testNamespaceImportExposesExportedNames() throws IOException {
        assertThat(evalModuleToString(
                """
                        export const a = 1;
                        export function b() {}""",
                """
                        import * as ns from './dep.mjs';
                        globalThis.__out = Object.keys(ns).sort().join(',');"""))
                .isEqualTo("a,b");
    }

    @Test
    public void testStringExportNamesAreEscapedIntoGeneratedSource() throws IOException {
        // The exported name reaches string position in generated source, so it must be escaped
        // rather than concatenated raw.
        assertThat(evalModuleToString(
                """
                        const x = 1;
                        export { x as "we-ird", x as "we ird" };""",
                """
                        import * as ns from './dep.mjs';
                        globalThis.__out = Object.keys(ns).sort().join('|');"""))
                .isEqualTo("we ird|we-ird");
    }

    @Test
    public void testUnscannableExportNameFailsWithSyntaxError() {
        // An export name the line scanner cannot handle must produce a diagnosable SyntaxError.
        // It must never be spliced into the generated source as-is.
        assertThatThrownBy(() -> evalModule(
                """
                        const x = 1;
                        export { x as "we\\"ird" };""",
                """
                        import * as ns from './dep.mjs';
                        globalThis.__out = 1;"""))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("SyntaxError");
    }
}
