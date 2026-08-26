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
 * Module semantics are still implemented by rewriting module source into ordinary JavaScript, and
 * the rewrite declares bookkeeping bindings in the author's own scope. Three ways that went wrong
 * are pinned here.
 * <p>
 * The generated names were fixed strings, so a module that happened to declare
 * {@code __qjs4jDefaultExport$0} had its value silently replaced. The per-module export binding was
 * built from {@code Math.abs(specifier.hashCode())}, which is still negative for
 * {@code Integer.MIN_VALUE} — so a module failed to evaluate because of what its file was called.
 * And the expression of an {@code export default} was taken from one line, so a default that spans
 * lines produced a syntax error at a position in text the caller never wrote.
 * <p>
 * These are containment: the generated bindings are still in the module's scope and still
 * observable. Only real module environments remove them, which is why this suite asserts what the
 * author's own bindings do rather than that the generated ones are absent.
 */
public class JSModuleGeneratedBindingTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    /**
     * Evaluate a module from a file and read back what it left on the global.
     *
     * @param name   the module's file name
     * @param source the module's source
     * @return the string value of {@code globalThis.result}
     */
    private String evaluateModuleResult(String name, String source) throws IOException {
        Path path = moduleDirectory.resolve(name);
        Files.writeString(path, source);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(source, path.toString(), true);
            return context.eval("String(globalThis.result)", "probe.js", false).toString();
        }
    }

    @Test
    public void testAModuleDeclaringSeveralGeneratedNamesKeepsAllOfThem() throws IOException {
        assertThat(evaluateModuleResult("collide-many.mjs",
                "const __qjs4jDefaultExport$0 = 'a';\n"
                        + "const __qjs4jDefaultExport$1 = 'b';\n"
                        + "const __qjs4jModuleExports = 'c';\n"
                        + "export default 'generated';\n"
                        + "export const v = 1;\n"
                        + "globalThis.result = [__qjs4jDefaultExport$0, __qjs4jDefaultExport$1, "
                        + "__qjs4jModuleExports].join(',');\n"))
                .isEqualTo("a,b,c");
    }

    @Test
    public void testAModuleDeclaringTheGeneratedDefaultNameKeepsItsOwnValue() throws IOException {
        // The review's reproduction: Node leaves `result` at 7, and the transformer used to
        // overwrite it with the default export's 9.
        assertThat(evaluateModuleResult("collide-default.mjs",
                "const __qjs4jDefaultExport$0 = 7;\n"
                        + "export default 9;\n"
                        + "globalThis.result = __qjs4jDefaultExport$0;\n"))
                .isEqualTo("7");
    }

    @Test
    public void testAModuleDeclaringTheGeneratedExportsNameKeepsItsOwnValue() throws IOException {
        assertThat(evaluateModuleResult("collide-exports.mjs",
                "const __qjs4jModuleExports = 'mine';\n"
                        + "export const v = 1;\n"
                        + "globalThis.result = __qjs4jModuleExports;\n"))
                .isEqualTo("mine");
    }

    @Test
    public void testAModuleWhoseFileNameHashesToTheMostNegativeIntegerEvaluates() throws IOException {
        // "polygenelubricants".hashCode() is Integer.MIN_VALUE, and Math.abs of that is still
        // negative — so the generated identifier contained a minus sign and the module would not
        // parse.
        assertThat("polygenelubricants".hashCode()).isEqualTo(Integer.MIN_VALUE);
        assertThat(evaluateModuleResult("polygenelubricants",
                "export const v = 1;\nglobalThis.result = v;\n"))
                .isEqualTo("1");
    }

    @Test
    public void testAMultiLineDefaultExportIsStillTheValueThatIsExported() throws IOException {
        // Not just "it parses": the value that spans the lines is the one the module exports.
        Path dependency = moduleDirectory.resolve("multiline-value.mjs");
        Files.writeString(dependency, "export default (\n  40\n  + 2\n);\n");
        Path entry = moduleDirectory.resolve("multiline-value-main.mjs");
        String entrySource = "import answer from './multiline-value.mjs';\nglobalThis.result = answer;\n";
        Files.writeString(entry, entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(entrySource, entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("42");
        }
    }

    @Test
    public void testAParenthesisedDefaultExportSpanningLinesEvaluates() throws IOException {
        assertThat(evaluateModuleResult("multiline-paren.mjs",
                "export default (\n  1 + 2\n);\nglobalThis.result = 1;\n"))
                .isEqualTo("1");
    }

    @Test
    public void testASingleLineDefaultExportStillWorks() throws IOException {
        assertThat(evaluateModuleResult("single-line-default.mjs",
                "export default 1 + 2;\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testAnArrayDefaultExportSpanningLinesEvaluates() throws IOException {
        assertThat(evaluateModuleResult("multiline-array.mjs",
                "export default [\n  1,\n  2\n];\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testAnArrowDefaultExportSpanningLinesEvaluates() throws IOException {
        assertThat(evaluateModuleResult("multiline-arrow.mjs",
                "export default (\n  x\n) => x + 1;\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testAnObjectDefaultExportSpanningLinesEvaluates() throws IOException {
        assertThat(evaluateModuleResult("multiline-object.mjs",
                "export default {\n  a: 1,\n  b: 2\n};\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }
}
