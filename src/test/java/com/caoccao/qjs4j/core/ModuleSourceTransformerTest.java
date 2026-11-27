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

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct tests for {@link ModuleSourceTransformer}, the textual half of module support.
 * <p>
 * Every one of these used to be reachable only through {@code eval}, which is a large part of why the same defects kept
 * coming back: a scanner that mis-reads an escape or a comment produces a module that fails somewhere else entirely,
 * and the failure names generated source rather than the text an author wrote. The transformer is almost all
 * string-in/string-out, so once it is a class of its own the seams that keep breaking can simply be asked what they
 * answer.
 * <p>
 * The cases below are the ones the review series kept hitting: escaped module specifiers, Unicode and escaped
 * identifiers, where an {@code export default} expression really ends, and what comment masking does and does not blank
 * out.
 */
public class ModuleSourceTransformerTest extends BaseTest {
    @Test
    public void testCollectingImportBindingsReadsNamesAsValues() {
        Set<String> bindingNames = new java.util.HashSet<>();
        Map<String, ModuleSourceTransformer.ImportBinding> importedBindings = new java.util.HashMap<>();
        transformer().collectImportBindings("import { x as \\u0079 } from './d\\u0065p.mjs';", bindingNames,
                importedBindings);
        // The local binding is called `y`, because that is the name module code writes to reach it.
        assertThat(bindingNames).containsExactly("y");
        assertThat(importedBindings).containsKey("y");
        assertThat(importedBindings.get("y").importedName()).isEqualTo("x");
        assertThat(importedBindings.get("y").sourceSpecifier()).isEqualTo("./dep.mjs");
        assertThat(importedBindings.get("y").deferredImport()).isFalse();
    }

    @Test
    public void testCollectingImportBindingsRecordsANamespaceBinding() {
        Set<String> bindingNames = new java.util.HashSet<>();
        Map<String, ModuleSourceTransformer.ImportBinding> importedBindings = new java.util.HashMap<>();
        transformer().collectImportBindings("import * as ns from './dep.mjs';", bindingNames, importedBindings);
        assertThat(bindingNames).containsExactly("ns");
        assertThat(importedBindings.get("ns").importedName())
                .isEqualTo(ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME);
    }

    @Test
    public void testDecodeIdentifierEscapesAppliesUnicodeEscapes() {
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u0078")).isEqualTo("x");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("caf\\u00e9")).isEqualTo("café");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{1D4AA}")).isEqualTo("\uD835\uDCAA");
        // Nothing to decode: the input is handed back as it stands.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("plain$_0")).isEqualTo("plain$_0");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("café")).isEqualTo("café");
    }

    @Test
    public void testDecodeIdentifierEscapesLeavesAnUnterminatedBracedEscapeAlone() {
        // An unterminated braced escape is malformed, so it gets the same answer as every other
        // malformed escape: its text stands. The branch used to append the escape and then rewind
        // the cursor to the backslash, so the loop walked the same characters twice and a
        // three-character unterminated escape decoded to five.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{")).isEqualTo("\\u{");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("a\\u{")).isEqualTo("a\\u{");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{12")).isEqualTo("\\u{12");
        // Only the malformed escape is left alone. Whatever follows it is still decoded, exactly as
        // it is after an escape with non-hexadecimal digits, so one bad escape does not silently
        // stop the scan.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{ \\u0041")).isEqualTo("\\u{ A");
    }

    @Test
    public void testDecodeIdentifierEscapesLeavesMalformedEscapesAlone() {
        // Not a decodable escape, so the text stands: the compiler rejects it with a real message.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\uZZZZ")).isEqualTo("\\uZZZZ");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("a\\u12")).isEqualTo("a\\u12");
    }

    @Test
    public void testDecodeIdentifierEscapesReadsABracedEscapeThatIsTerminated() {
        // The sibling of the case above: the brace does close, so the escape decodes. Both branches
        // read the same opening prefix, and only the closing brace tells them apart.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{41}")).isEqualTo("A");
        // A braced escape whose body is not hexadecimal is malformed too, and stands as written.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{ZZ}b")).isEqualTo("\\u{ZZ}b");
    }

    @Test
    public void testDecodeModuleStringLiteralValueAppliesEscapes() {
        // A specifier's meaning is its StringValue, not its spelling: './dep.mjs' either way.
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("./d\\u0065p.mjs")).isEqualTo("./dep.mjs");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("a\\u002db")).isEqualTo("a-b");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("\\x41")).isEqualTo("A");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("a\\nb")).isEqualTo("a\nb");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("a\\tb")).isEqualTo("a\tb");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("./a\\'b.mjs")).isEqualTo("./a'b.mjs");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("\\u{1F600}")).isEqualTo("\uD83D\uDE00");
    }

    @Test
    public void testDecodeModuleStringLiteralValueDropsLineContinuations() {
        // A LineContinuation contributes nothing to the value, and CRLF is one line terminator.
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("./de\\\np.mjs")).isEqualTo("./dep.mjs");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("./de\\\r\np.mjs")).isEqualTo("./dep.mjs");
    }

    @Test
    public void testDecodeModuleStringLiteralValueLeavesLegacyOctalAlone() {
        // A legacy octal escape is a SyntaxError in a module, and the source has already been
        // compiled once by the time this runs. Inventing a value for it would be worse.
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("\\01")).isEqualTo("\\01");
        // \0 not followed by a digit is NUL, which is a legal escape.
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("a\\0b")).isEqualTo("a\0b");
    }

    @Test
    public void testDefaultExportedDeclarationsHaveNoExpressionExtent() {
        // A default-exported function or class ends at its body, which the transformer finds by
        // matching braces. Only expressions need the parser.
        assertThat(transformer().scan("export default function f() {}\n").defaultExportExtents()).isEmpty();
        assertThat(transformer().scan("export default class C {}\n").defaultExportExtents()).isEmpty();
        assertThat(transformer().scan("export default async function g() {}\n").defaultExportExtents()).isEmpty();
    }

    @Test
    public void testDefaultExportExtentEndsAtTheGrammarNotAtABracketCount() {
        // `export default /\(/;` is balanced only if you know the parenthesis is inside a regular
        // expression literal. Counting delimiters swallowed the statement after it.
        String sourceCode = "export default /\\(/;\nglobalThis.after = 1;\n";
        Map<Integer, ModuleSourceScan.DefaultExportExtent> extents = transformer().scan(sourceCode)
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
        Map<Integer, ModuleSourceScan.DefaultExportExtent> extents = transformer().scan(sourceCode)
                .defaultExportExtents();
        assertThat(extents).containsKey(0);
        assertThat(extents.get(0).expression()).isEqualTo("0");
        assertThat(extents.get(0).trailingText()).isEqualTo("// trailing");
    }

    @Test
    public void testDefaultExportExtentSpansMoreThanItsOwnLine() {
        String sourceCode = "export default 1 +\n    2;\nglobalThis.after = 1;\n";
        Map<Integer, ModuleSourceScan.DefaultExportExtent> extents = transformer().scan(sourceCode)
                .defaultExportExtents();
        assertThat(extents).containsKey(0);
        assertThat(extents.get(0).expression()).isEqualTo("1 +\n    2");
        assertThat(extents.get(0).endLineIndex()).isOne();
    }

    @Test
    public void testEscapingAJavaScriptStringCoversEveryLineTerminator() {
        // A line terminator inside an interpolated value would end the generated literal and turn
        // the rest of the value into source text. U+2028 and U+2029 are line terminators too.
        assertThat(ModuleSourceText.escapeJavaScriptString("a\"b")).isEqualTo("a\\\"b");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\\b")).isEqualTo("a\\\\b");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\nb")).isEqualTo("a\\nb");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\u2028b")).isEqualTo("a\\u2028b");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\u2029b")).isEqualTo("a\\u2029b");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\u0001b")).isEqualTo("a\\u0001b");
    }

    @Test
    public void testGeneratedNamePrefixAvoidsANameTheSourceSpells() {
        // The prefix has to be one the module's own source does not use, or the transformer's
        // bookkeeping binding silently replaces the author's.
        assertThat(transformer().scan("const x = 1;").generatedModuleNamePrefix()).isEqualTo("__qjs4j");
        assertThat(transformer().scan("const __qjs4jDefaultExport$0 = 1;").generatedModuleNamePrefix())
                .isNotEqualTo("__qjs4j");
        // …including when the source spells it with an escape, which a substring search misses.
        assertThat(transformer().scan("const \\u005f\\u005fqjs4jModuleExports = 1;").generatedModuleNamePrefix())
                .isNotEqualTo("__qjs4j");
    }

    @Test
    public void testIdentifierNameValidationFollowsUnicodeNotAscii() {
        // `café` and `módulo` are ordinary identifiers; the transformer used to extract binding
        // names with [A-Za-z_$][A-Za-z0-9_$]* and lose them.
        assertThat(ModuleSourceText.isValidIdentifierName("café")).isTrue();
        assertThat(ModuleSourceText.isValidIdentifierName("módulo")).isTrue();
        assertThat(ModuleSourceText.isValidIdentifierName("$_0")).isTrue();
        assertThat(ModuleSourceText.isValidIdentifierName("0abc")).isFalse();
        assertThat(ModuleSourceText.isValidIdentifierName("a-b")).isFalse();
        assertThat(ModuleSourceText.isValidIdentifierName("")).isFalse();
        assertThat(ModuleSourceText.isValidIdentifierName(null)).isFalse();
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
    public void testNormalizeModuleDeclarationLinesGivesEachDeclarationItsOwnLine() {
        // A declaration sharing a line with other code was invisible to the line-oriented scan.
        String normalized = transformer().normalizeModuleDeclarationLines("const t = 1; export const v = 15;\n");
        assertThat(normalized).isEqualTo("const t = 1; \nexport const v = 15;\n");
    }

    @Test
    public void testNormalizeModuleDeclarationLinesLeavesSourceWithoutDeclarationsAlone() {
        String sourceCode = "const a = 1;\nconst b = 2;\n";
        assertThat(transformer().normalizeModuleDeclarationLines(sourceCode)).isEqualTo(sourceCode);
    }

    @Test
    public void testQuotedDelimitersInModuleDeclarations() {
        assertThat(transformer().stripQuotedSpecifier("'a\\'b' with { type: 'text' }")).isEqualTo("a'b");
        String body = "{ return 'https://example.org/}'; }";
        assertThat(ModuleSourceText.findMatchingClosingBrace(body, 0)).isEqualTo(body.length() - 1);
    }

    @Test
    public void testScanTopLevelModuleDeclarationsIgnoresDeclarationsInsideATemplate() {
        // Comment masking leaves template bodies verbatim, so this used to read as an export.
        ModuleSourceScan.ModuleDeclarationScan scan = transformer().scan("const t = `\nexport const fake = 1;\n`;\n")
                .scanTopLevelModuleDeclarations();
        assertThat(scan).isNotNull();
        assertThat(scan.hasExportDeclaration()).isFalse();
        assertThat(scan.hasImportDeclaration()).isFalse();
    }

    @Test
    public void testScanTopLevelModuleDeclarationsSeparatesImportsFromImportExpressions() {
        ModuleSourceScan.ModuleDeclarationScan declarationScan = transformer().scan("import { a } from './m.mjs';\n")
                .scanTopLevelModuleDeclarations();
        assertThat(declarationScan).isNotNull();
        assertThat(declarationScan.hasImportDeclaration()).isTrue();
        // `import(...)` and `import.meta` are expressions, not declarations.
        ModuleSourceScan.ModuleDeclarationScan expressionScan = transformer().scan("const p = import('./m.mjs');\n")
                .scanTopLevelModuleDeclarations();
        assertThat(expressionScan).isNotNull();
        assertThat(expressionScan.hasImportDeclaration()).isFalse();
        ModuleSourceScan.ModuleDeclarationScan metaScan = transformer().scan("const u = import.meta.url;\n")
                .scanTopLevelModuleDeclarations();
        assertThat(metaScan).isNotNull();
        assertThat(metaScan.hasImportDeclaration()).isFalse();
    }

    @Test
    public void testSplitOnTopLevelCommasIgnoresCommasInsideQuotes() {
        assertThat(ModuleSourceText.splitOnTopLevelCommas("a, b as c")).containsExactly("a", " b as c");
        assertThat(ModuleSourceText.splitOnTopLevelCommas("\"a,b\" as c, d")).containsExactly("\"a,b\" as c", " d");
    }

    @Test
    public void testStripQuotedSpecifierReadsTheLiteralAsAValue() {
        assertThat(transformer().stripQuotedSpecifier("'./dep.mjs'")).isEqualTo("./dep.mjs");
        assertThat(transformer().stripQuotedSpecifier("\"./d\\u0065p.mjs\"")).isEqualTo("./dep.mjs");
        // A backslash escapes the quote that follows it, so the first matching quote is not
        // necessarily the closing one.
        assertThat(transformer().stripQuotedSpecifier("'./a\\'b.mjs'")).isEqualTo("./a'b.mjs");
        // Everything after the closing quote belongs to the attributes clause, not the specifier.
        assertThat(transformer().stripQuotedSpecifier("'./d.json' with { type: 'json' }")).isEqualTo("./d.json");
    }

    @Test
    public void testStripQuotedSpecifierRejectsTextThatIsNotALiteral() {
        assertThatThrownBy(() -> transformer().stripQuotedSpecifier("./dep.mjs")).isInstanceOf(JSException.class);
        context.clearPendingException();
        assertThatThrownBy(() -> transformer().stripQuotedSpecifier("'unterminated")).isInstanceOf(JSException.class);
        context.clearPendingException();
    }

    @Test
    public void testSyntheticModuleImportClausesDistinguishDefaultAliasesFromOtherNames() {
        ModuleSourceTransformer transformer = transformer();
        assertThat(transformer.hasNonDefaultNamedBindings("value")).isFalse();
        assertThat(transformer.hasNonDefaultNamedBindings("value, {}")).isFalse();
        assertThat(transformer.hasNonDefaultNamedBindings("{default as payload,}")).isFalse();
        assertThat(transformer.hasNonDefaultNamedBindings("value, {default as payload, name as local}")).isTrue();
    }

    @Test
    public void testTokenizingModuleSourceDropsTheTerminatingEof() {
        assertThat(transformer().scan("const a = 1;").tokens()).isNotEmpty()
                .noneMatch(token -> token.type().name().equals("EOF"));
    }

    @Test
    public void testValidatingAGeneratedIdentifierRejectsWhatWouldSpliceSource() {
        // Names extracted by the scanner reach identifier position in generated source unescaped,
        // so anything that is not an identifier has to be refused rather than interpolated.
        assertThat(transformer().requireGeneratedIdentifier("café", "binding", "./m.mjs")).isEqualTo("café");
        assertThatThrownBy(
                () -> transformer().requireGeneratedIdentifier("x; globalThis.pwned = 1; let y", "binding", "./m.mjs"))
                .isInstanceOf(JSException.class).hasMessageContaining("not a valid identifier");
        context.clearPendingException();
    }

    /**
     * A transformer of this test's own, rather than the realm's.
     * <p>
     * The transformer holds no state beyond the context it reports syntax errors through, so one built here answers
     * exactly what the realm's does — and the context does not have to carry an accessor that only a test would ever
     * call. This test is in the transformer's own package, which is the whole reason its constructor can be reached.
     *
     * @return a transformer bound to this test's context
     */
    private ModuleSourceTransformer transformer() {
        return new ModuleSourceTransformer(context);
    }
}
