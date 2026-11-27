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

package com.caoccao.qjs4j.core.module;

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleSourceScanTest extends BaseTest {
    @Test
    public void testDefaultExportedDeclarationsHaveNoExpressionExtent() {
        // A default-exported function or class ends at its body, which the transformer finds by
        // matching braces. Only expressions need the parser.
        assertThat(new ModuleSourceScan(context, "export default function f() {}\n").defaultExportExtents()).isEmpty();
        assertThat(new ModuleSourceScan(context, "export default class C {}\n").defaultExportExtents()).isEmpty();
        assertThat(new ModuleSourceScan(context, "export default async function g() {}\n").defaultExportExtents())
                .isEmpty();
    }

    @Test
    public void testDefaultExportExtentEndsAtTheGrammarNotAtABracketCount() {
        // `export default /\(/;` is balanced only if you know the parenthesis is inside a regular
        // expression literal. Counting delimiters swallowed the statement after it.
        String sourceCode = "export default /\\(/;\nglobalThis.after = 1;\n";
        Map<Integer, ModuleSourceScan.DefaultExportExtent> extents = new ModuleSourceScan(context, sourceCode)
                .defaultExportExtents();
        assertThat(extents).containsKey(0);
        assertThat(extents.get(0).expression()).isEqualTo("/\\(/");
        assertThat(extents.get(0).endLineIndex()).isZero();
        assertThat(extents.get(0).trailingText()).isEmpty();
    }

    @Test
    public void testDefaultExportExtentExcludesATrailingComment() {
        // Masking turns a comment into whitespace, so the expression must not absorb it — the
        // generated `let X = (0, e);` would otherwise close its parenthesis inside a line comment.
        String sourceCode = "export default 0; // trailing\n";
        Map<Integer, ModuleSourceScan.DefaultExportExtent> extents = new ModuleSourceScan(context, sourceCode)
                .defaultExportExtents();
        assertThat(extents).containsKey(0);
        assertThat(extents.get(0).expression()).isEqualTo("0");
        assertThat(extents.get(0).trailingText()).isEqualTo("// trailing");
    }

    @Test
    public void testDefaultExportExtentSpansMoreThanItsOwnLine() {
        String sourceCode = "export default 1 +\n    2;\nglobalThis.after = 1;\n";
        Map<Integer, ModuleSourceScan.DefaultExportExtent> extents = new ModuleSourceScan(context, sourceCode)
                .defaultExportExtents();
        assertThat(extents).containsKey(0);
        assertThat(extents.get(0).expression()).isEqualTo("1 +\n    2");
        assertThat(extents.get(0).endLineIndex()).isOne();
    }

    @Test
    public void testGeneratedNamePrefixAvoidsANameTheSourceSpells() {
        // The prefix has to be one the module's own source does not use, or the transformer's
        // bookkeeping binding silently replaces the author's.
        assertThat(new ModuleSourceScan(context, "const x = 1;").generatedModuleNamePrefix()).isEqualTo("__qjs4j");
        assertThat(new ModuleSourceScan(context, "const __qjs4jDefaultExport$0 = 1;").generatedModuleNamePrefix())
                .isNotEqualTo("__qjs4j");
        // …including when the source spells it with an escape, which a substring search misses.
        assertThat(new ModuleSourceScan(context, "const \\u005f\\u005fqjs4jModuleExports = 1;")
                .generatedModuleNamePrefix()).isNotEqualTo("__qjs4j");
    }

    @Test
    public void testMaskModuleCommentsBlanksCommentsAndKeepsLineStructure() {
        String sourceCode = "const a = 1; // export const fake = 1;\n/* export */ const b = 2;\n";
        String masked = ModuleSourceScan.maskModuleComments(sourceCode);
        assertThat(masked).hasSameSizeAs(sourceCode);
        assertThat(masked).doesNotContain("fake");
        assertThat(masked).contains("const a = 1;");
        assertThat(masked).contains("const b = 2;");
        // Line terminators survive, so a line index into the masked text is a line index into the
        // source.
        assertThat(masked.chars().filter(ch -> ch == '\n').count())
                .isEqualTo(sourceCode.chars().filter(ch -> ch == '\n').count());
    }

    @Test
    public void testMaskModuleCommentsKeepsStringAndTemplateContents() {
        // Masking blanks comments only. String and template bodies are left verbatim, which is why
        // membership in a declaration line is decided by the lexer rather than by this scan.
        String sourceCode = "const s = '// not a comment';\nconst t = `export const fake = 1;`;\n";
        String masked = ModuleSourceScan.maskModuleComments(sourceCode);
        assertThat(masked).contains("'// not a comment'");
        assertThat(masked).contains("export const fake = 1;");
    }

    @Test
    public void testScanTopLevelModuleDeclarationsIgnoresDeclarationsInsideATemplate() {
        // Comment masking leaves template bodies verbatim, so this used to read as an export.
        ModuleSourceScan.ModuleDeclarationScan scan = new ModuleSourceScan(context,
                "const t = `\nexport const fake = 1;\n`;\n").scanTopLevelModuleDeclarations();
        assertThat(scan).isNotNull();
        assertThat(scan.hasExportDeclaration()).isFalse();
        assertThat(scan.hasImportDeclaration()).isFalse();
    }

    @Test
    public void testScanTopLevelModuleDeclarationsSeparatesImportsFromImportExpressions() {
        ModuleSourceScan.ModuleDeclarationScan declarationScan = new ModuleSourceScan(context,
                "import { a } from './m.mjs';\n").scanTopLevelModuleDeclarations();
        assertThat(declarationScan).isNotNull();
        assertThat(declarationScan.hasImportDeclaration()).isTrue();
        // `import(...)` and `import.meta` are expressions, not declarations.
        ModuleSourceScan.ModuleDeclarationScan expressionScan = new ModuleSourceScan(context,
                "const p = import('./m.mjs');\n").scanTopLevelModuleDeclarations();
        assertThat(expressionScan).isNotNull();
        assertThat(expressionScan.hasImportDeclaration()).isFalse();
        ModuleSourceScan.ModuleDeclarationScan metaScan = new ModuleSourceScan(context, "const u = import.meta.url;\n")
                .scanTopLevelModuleDeclarations();
        assertThat(metaScan).isNotNull();
        assertThat(metaScan.hasImportDeclaration()).isFalse();
    }

    @Test
    public void testTokenizingModuleSourceDropsTheTerminatingEof() {
        assertThat(new ModuleSourceScan(context, "const a = 1;").tokens()).isNotEmpty()
                .noneMatch(token -> token.type().name().equals("EOF"));
    }
}
