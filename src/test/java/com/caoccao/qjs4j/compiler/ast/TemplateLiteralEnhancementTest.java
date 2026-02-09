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

package com.caoccao.qjs4j.compiler.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.compiler.Lexer;
import com.caoccao.qjs4j.compiler.Parser;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TemplateLiteralEnhancementTest extends BaseJavetTest {
    @Test
    public void testParseTaggedTemplateInvalidEscape() {
        Program program = new Parser(new Lexer("tag`bad\\u{110000}`;")).parse();
        TaggedTemplateExpression taggedTemplateExpression = (TaggedTemplateExpression) ((ExpressionStatement) program.body().get(0)).expression();
        TemplateLiteral templateLiteral = taggedTemplateExpression.quasi();
        assertThat(templateLiteral.rawQuasis()).containsExactly("bad\\u{110000}");
        assertThat(templateLiteral.quasis()).containsExactly((String) null);
    }

    @Test
    public void testParseTaggedTemplateLiteral() {
        Program program = new Parser(new Lexer("tag`a${x}b`;")).parse();
        TaggedTemplateExpression taggedTemplateExpression = (TaggedTemplateExpression) ((ExpressionStatement) program.body().get(0)).expression();
        TemplateLiteral templateLiteral = taggedTemplateExpression.quasi();
        assertThat(templateLiteral.rawQuasis()).containsExactly("a", "b");
        assertThat(templateLiteral.quasis()).containsExactly("a", "b");
        assertThat(templateLiteral.expressions()).hasSize(1);
        assertThat(templateLiteral.expressions().get(0)).isInstanceOf(Identifier.class);
        assertThat(((Identifier) templateLiteral.expressions().get(0)).name()).isEqualTo("x");
    }

    @Test
    public void testParseTaggedTemplateLiteralEscapes() {
        Program program = new Parser(new Lexer("tag`line\\n${x}\\u{41}`;")).parse();
        TaggedTemplateExpression taggedTemplateExpression = (TaggedTemplateExpression) ((ExpressionStatement) program.body().get(0)).expression();
        TemplateLiteral templateLiteral = taggedTemplateExpression.quasi();
        assertThat(templateLiteral.rawQuasis()).containsExactly("line\\n", "\\u{41}");
        assertThat(templateLiteral.quasis()).containsExactly("line\n", "A");
    }

    @Test
    public void testTemplateExpressionScannerEdgeCases() {
        assertStringWithJavet(
                "`${\"}\"}`",
                "`${1 /* } */ + 1}`",
                "`${/\\}/.test('}')}`",
                "`${/[}]/.test('}')}`",
                "`${6 / 2}`",
                "`${`a${1}`}`");
    }

    @Test
    public void testTaggedTemplateMethodReceiver() {
        assertStringWithJavet(
                """
                        const obj = {
                            prefix: "x",
                            tag(parts) { return this.prefix + parts[0]; }
                        };
                        obj.tag`y`;""");
    }

    @Test
    public void testTaggedTemplateUndefinedCookedSegmentForInvalidEscape() {
        assertStringWithJavet("((parts) => typeof parts[0])`\\u{110000}`");
        assertStringWithJavet("((parts) => parts.raw[0])`\\u{110000}`");
    }

    @Test
    public void testUntaggedTemplateInvalidEscapeThrows() {
        assertThatThrownBy(() -> new Parser(new Lexer("`bad\\u{110000}`;")).parse())
                .isInstanceOf(JSSyntaxErrorException.class);
    }

    @Test
    public void testUntaggedTemplateUnicodeEscapes() {
        assertStringWithJavet("`\\u{41}\\x42`");
    }

    @Test
    public void testStringRawUsesRawTemplateParts() {
        assertStringWithJavet(
                "String.raw`line\\nend`",
                "String.raw`a\\n${1}b`",
                "String.raw`\\u{41}`");
    }
}
