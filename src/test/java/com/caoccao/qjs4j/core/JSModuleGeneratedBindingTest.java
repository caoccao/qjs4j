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
 * Which name the source uses is now asked of the identifiers the lexer produces, so an identifier
 * written with escapes — {@code \\u005f_qjs4jDefaultExport$0} is {@code __qjs4jDefaultExport$0} —
 * is seen for what it is. Where a default-export expression ends is asked of the parser, so
 * operators, conditionals, member chains, templates, comments and regular expressions all end it
 * where the grammar does rather than where a bracket count happens to balance.
 * <p>
 * These are containment: the generated bindings are still in the module's scope and still
 * observable — direct eval can build a name the source never spells, and no choice of prefix hides
 * a binding from that. Only real module environments remove them, which is why this suite asserts
 * what the author's own bindings do rather than that the generated ones are absent.
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
    public void testADefaultExportEndedByAutomaticSemicolonInsertionEvaluates() throws IOException {
        // No semicolon: where the expression ends is what ASI says, which is a grammar question.
        assertThat(evaluateModuleResult("default-asi.mjs",
                "export default 1 + 2\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testADefaultExportFollowedByCodeOnTheSameLineKeepsThatCode() throws IOException {
        // The line-oriented scan discarded whatever followed the declaration on its line.
        assertThat(evaluateModuleResult("default-trailing.mjs",
                "export default 1 + 2; globalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testADefaultExportOfAConditionalSpanningLinesEvaluates() throws IOException {
        assertThat(evaluateModuleResult("default-conditional.mjs",
                "export default true\n  ? 'yes'\n  : 'no';\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testADefaultExportOfAMemberChainSpanningLinesIsTheValueExported() throws IOException {
        Path dependency = moduleDirectory.resolve("default-member.mjs");
        Files.writeString(dependency, "export default [1, 2]\n  .map(x => x * 2)\n  .join('-');\n");
        Path entry = moduleDirectory.resolve("default-member-main.mjs");
        String entrySource = "import chained from './default-member.mjs';\nglobalThis.result = chained;\n";
        Files.writeString(entry, entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(entrySource, entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("2-4");
        }
    }

    @Test
    public void testADefaultExportOfARegularExpressionWithADelimiterEvaluates() throws IOException {
        // The review's reproduction. The delimiter count had no notion of a regular expression, so
        // the escaped `(` opened a depth the rest of the module was swallowed into — and the lexer
        // read the `/` after `default` as division, so the module did not even tokenise.
        assertThat(evaluateModuleResult("default-regexp.mjs",
                "export default /\\(/;\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testADefaultExportOfARegularExpressionWithAQuoteEvaluates() throws IOException {
        assertThat(evaluateModuleResult("default-regexp-quote.mjs",
                "export default /\"/;\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testADefaultExportOfATaggedTemplateSpanningLinesEvaluates() throws IOException {
        assertThat(evaluateModuleResult("default-tagged-template.mjs",
                "const tag = (strings) => strings.raw[0];\n"
                        + "export default tag`first\nsecond`;\n"
                        + "globalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testADefaultExportWithACommentAfterAnOperatorEvaluates() throws IOException {
        assertThat(evaluateModuleResult("default-comment.mjs",
                "export default 1 + // the second term is on the next line\n  2;\n"
                        + "globalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }

    @Test
    public void testADefaultExportWithAnOperatorContinuationIsTheValueExported() throws IOException {
        // The review's reproduction: balanced delimiters do not mark the end of an expression, so
        // `export default 1 +` was taken as the whole of it.
        Path dependency = moduleDirectory.resolve("default-operator.mjs");
        Files.writeString(dependency, "export default 40 +\n  2;\n");
        Path entry = moduleDirectory.resolve("default-operator-main.mjs");
        String entrySource = "import answer from './default-operator.mjs';\nglobalThis.result = answer;\n";
        Files.writeString(entry, entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(entrySource, entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .isEqualTo("42");
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
    public void testAModuleDeclaringTheGeneratedNameWithAnEscapedDollarKeepsIt() throws IOException {
        // The escape is in the middle of the name rather than at its start.
        assertThat(evaluateModuleResult("collide-escaped-dollar.mjs",
                "const __qjs4jDefaultExport\\u00240 = 5;\n"
                        + "export default 9;\n"
                        + "globalThis.result = __qjs4jDefaultExport\\u00240;\n"))
                .isEqualTo("5");
    }

    @Test
    public void testAModuleDeclaringTheGeneratedNamesWithUnicodeEscapesKeepsThem() throws IOException {
        // The review's reproduction. `\u005f` is `_`, so these declare exactly the names the
        // transformer generates — and a substring search over the source text does not see them,
        // so a valid module was rejected with a syntax error in generated source.
        assertThat(evaluateModuleResult("collide-escaped.mjs",
                "const \\u005f_qjs4jDefaultExport$0 = 7;\n"
                        + "const \\u005f_qjs4jModuleExports = 'mine';\n"
                        + "export default 9;\n"
                        + "export const v = 1;\n"
                        + "globalThis.result = [\\u005f_qjs4jDefaultExport$0, "
                        + "\\u005f_qjs4jModuleExports].join(',');\n"))
                .isEqualTo("7,mine");
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
    public void testAnInvalidDefaultExportIsReportedInsideTheCallersOwnSource() throws IOException {
        // The other half of the guarantee: when the module really is invalid, the position must be
        // one the caller can find. A generated-source offset — line 4, offset 263, of a 52-character
        // module — names no character of what was passed in.
        String entrySource = "export default (\n  1 +\n);\nglobalThis.result = 'ok';\n";
        Path entry = moduleDirectory.resolve("default-invalid.mjs");
        Files.writeString(entry, entrySource);
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            JSException failure = catchThrowableOfType(
                    JSException.class, () -> context.eval(entrySource, entry.toString(), true));
            assertThat(failure).isNotNull();
            assertThat(failure.getMessage()).startsWith("SyntaxError");
            SourceLocation location = failure.getSourceLocation();
            assertThat(location).isNotNull();
            assertThat(location.offset())
                    .as("the offset is a position in the source the caller passed")
                    .isBetween(0, entrySource.length());
            assertThat(location.line()).isBetween(1, 4);
        }
    }

    @Test
    public void testAnObjectDefaultExportSpanningLinesEvaluates() throws IOException {
        assertThat(evaluateModuleResult("multiline-object.mjs",
                "export default {\n  a: 1,\n  b: 2\n};\nglobalThis.result = 'ok';\n"))
                .isEqualTo("ok");
    }
}
