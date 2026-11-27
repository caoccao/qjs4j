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
