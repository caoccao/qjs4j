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

package com.caoccao.qjs4j.compilation.parser;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code AttributeKey} in an import {@code WithClause} is an {@code IdentifierName} or a
 * {@code StringLiteral}, so a reserved word is a valid key. Requiring an {@code IDENTIFIER} token
 * rejected sources such as {@code import x from './m.js' with {if: ''}} at parse time, which also
 * moved the failure of the surrounding module to the wrong phase.
 */
public class ImportAttributeKeyTest extends BaseTest {
    private void parseModule(String source) {
        new Compiler(source, "attributes.mjs").setContext(context).compile(true);
    }

    @Test
    public void testDuplicateAttributeKeyIsRejected() {
        assertThatThrownBy(() -> parseModule("import x from './m.js' with {type: 'json', type: 'json'};"))
                .isInstanceOf(JSSyntaxErrorException.class)
                .hasMessageContaining("Duplicate attribute key");
    }

    @Test
    public void testIdentifierAttributeKeyIsAccepted() {
        assertThatCode(() -> parseModule("import x from './m.js' with {type: 'json'};"))
                .doesNotThrowAnyException();
    }

    @Test
    public void testNonIdentifierNameAttributeKeyIsRejected() {
        assertThatThrownBy(() -> parseModule("import x from './m.js' with {1: 'json'};"))
                .isInstanceOf(JSSyntaxErrorException.class)
                .hasMessageContaining("identifier expected");
    }

    @Test
    public void testReservedWordAttributeKeyIsAccepted() {
        assertThatCode(() -> parseModule("import x from './m.js' with {if: ''};"))
                .doesNotThrowAnyException();
        assertThatCode(() -> parseModule("import './m.js' with {class: '', for: ''};"))
                .doesNotThrowAnyException();
        assertThatCode(() -> parseModule("export * from './m.js' with {typeof: ''};"))
                .doesNotThrowAnyException();
    }

    @Test
    public void testStringAttributeKeyIsAccepted() {
        assertThatCode(() -> parseModule("import x from './m.js' with {'a-b': 'json'};"))
                .doesNotThrowAnyException();
    }
}
