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
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Module source does not reach the compiler as the caller wrote it: top-level declarations are
 * moved onto lines of their own, and a module with exports is then rewritten into generated code
 * that wraps it. Neither rewrite preserves the caller's coordinate system, so an error discovered
 * after them carries a position that belongs to text the caller never saw — a duplicate
 * {@code __proto__} at offset 44 of a 60-character module was reported at line 3, column 34,
 * offset 119, which names no character of the input at all.
 * <p>
 * Parsing the untouched source first was not enough: it answers the automatic-semicolon-insertion
 * question but leaves every early error the bytecode compiler raises to be found later, after the
 * rewrites. The source is therefore compiled as written, and these cases hold every such error to
 * the caller's own coordinates.
 * <p>
 * These assertions are about {@link JSException#getSourceLocation()}, which is an embedder-facing
 * Java API with no JavaScript counterpart, so they cannot be made against V8. The messages
 * themselves are compared with V8 in {@link JSModuleAutomaticSemicolonInsertionTest} and the other
 * Javet suites.
 */
public class JSModuleSourceLocationTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    /**
     * Assert that a module's compile error is reported where the caller can find it.
     *
     * @param source         the module source
     * @param filename       the name to evaluate it under
     * @param expectedLine   the 1-based line the error is on
     * @param expectedColumn the 1-based column the error is on
     */
    private void assertModuleErrorIsInTheCallersSource(
            String source, String filename, int expectedLine, int expectedColumn) {
        SourceLocation location = moduleErrorLocation(source, filename);
        assertThat(location).as("the error carries a location").isNotNull();
        assertThat(location.line()).as("line of: " + source).isEqualTo(expectedLine);
        assertThat(location.column()).as("column of: " + source).isEqualTo(expectedColumn);
        assertThat(location.offset())
                .as("start offset of: " + source)
                .isBetween(0, source.length());
        assertThat(location.endOffset())
                .as("end offset of: " + source)
                .isBetween(location.offset(), source.length());
        // The whole point: the position is the one the source has on its own, not one that a
        // rewritten copy of it happens to produce.
        assertThat(location)
                .as("the reported location matches compiling the untouched source")
                .isEqualTo(untouchedCompilationLocation(source));
    }

    /**
     * Evaluate a module and return the location of the error it raises.
     *
     * @param source   the module source, exactly as an embedder would pass it
     * @param filename the name to evaluate it under
     * @return the reported location
     */
    private SourceLocation moduleErrorLocation(String source, String filename) {
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            try {
                context.eval(source, filename, true);
            } catch (JSException jsException) {
                return jsException.getSourceLocation();
            }
            return fail("Expected " + filename + " to fail to compile");
        }
    }

    @Test
    public void testAMultiLineModuleKeepsItsOwnLineNumbers() {
        // Several declarations on their own lines already, so nothing is split; the point here is
        // that the generated module code the export syntax produces does not shift anything either.
        String source = "export const a = 1;\n"
                + "const b = 2;\n"
                + "const o = { __proto__: null, __proto__: {} };\n";
        assertModuleErrorIsInTheCallersSource(source, "multi-line.js", 3, 30);
    }

    @Test
    public void testAValidModuleStillEvaluates() {
        // Compiling the source before rewriting it must not reject anything that was fine, and the
        // second compilation must not have side effects the first one would trip over.
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval("export const v = 'ok'; globalThis.result = v;", "valid.js", true);
            assertThat(context.eval("globalThis.result", "check.js", false).toString()).isEqualTo("ok");
        }
    }

    @Test
    public void testAnErrorInADependencyIsReportedInThatDependencysSource() throws IOException {
        // A dependency becomes generated module code too, and its errors used to be reported
        // against that: a duplicate __proto__ at offset 35 of a 52-character file came back at
        // offset 228.
        String dependencySource = "export const o = { __proto__: null, __proto__: {} };\n";
        Path dependency = moduleDirectory.resolve("bad_dep.mjs");
        Files.writeString(dependency, dependencySource);
        Path entry = moduleDirectory.resolve("main.mjs");
        String entrySource = "import { o } from './bad_dep.mjs';\nglobalThis.result = o;\n";
        Files.writeString(entry, entrySource);

        SourceLocation location = moduleErrorLocation(entrySource, entry.toString());
        assertThat(location).isNotNull();
        assertThat(location.line()).isEqualTo(1);
        assertThat(location.column()).isEqualTo(37);
        assertThat(location.offset())
                .as("the offset names a character of the dependency")
                .isBetween(0, dependencySource.length());
        assertThat(location).isEqualTo(untouchedCompilationLocation(dependencySource));
    }

    @Test
    public void testDuplicateDeclarationIsReportedInTheCallersSource() {
        assertModuleErrorIsInTheCallersSource(
                "export {}; let dup = 1; let dup = 2;", "duplicate.js", 1, 37);
    }

    @Test
    public void testDuplicateProtoAfterAOneLineExportIsReportedInTheCallersSource() {
        // The review's reproducer. Sixty characters in, the offset used to be 119.
        assertModuleErrorIsInTheCallersSource(
                "export {}; const value = { __proto__: null, __proto__: {} };", "one-line.js", 1, 45);
    }

    @Test
    public void testDuplicateProtoAfterAOneLineImportIsReportedInTheCallersSource() {
        assertModuleErrorIsInTheCallersSource(
                "import { v } from './dep.mjs'; const o = { __proto__: 1, __proto__: 2 };",
                "import.js", 1, 58);
    }

    @Test
    public void testDuplicateProtoBeforeAOneLineExportIsReportedInTheCallersSource() {
        assertModuleErrorIsInTheCallersSource(
                "const value = { __proto__: null, __proto__: {} }; export {};", "before.js", 1, 34);
    }

    @Test
    public void testIllegalBreakIsReportedInTheCallersSource() {
        assertModuleErrorIsInTheCallersSource(
                "export {}; function f() { break; }", "break.js", 1, 27);
    }

    @Test
    public void testInvalidRegExpLiteralIsReportedInTheCallersSource() {
        assertModuleErrorIsInTheCallersSource(
                "export {}; const r = /(/;", "regexp.js", 1, 22);
    }

    @Test
    public void testUndefinedBreakTargetIsReportedInTheCallersSource() {
        assertModuleErrorIsInTheCallersSource(
                "export {}; outer: { } break inner;", "label.js", 1, 23);
    }

    @Test
    public void testUnresolvableExportIsReportedInTheCallersSource() {
        assertModuleErrorIsInTheCallersSource("export { missing };", "export.js", 1, 20);
    }

    /**
     * The location the compiler reports for the same source with nothing done to it.
     *
     * @param source the module source
     * @return the reported location
     */
    private SourceLocation untouchedCompilationLocation(String source) {
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            try {
                new Compiler(source, "untouched.js").setContext(context).compile(true);
            } catch (JSException jsException) {
                return jsException.getSourceLocation();
            } catch (JSErrorException errorException) {
                return errorException.getSourceLocation();
            } catch (JSCompilerException compilerException) {
                return compilerException.getSourceLocation();
            }
            return fail("Expected the untouched source to fail to compile");
        }
    }
}
