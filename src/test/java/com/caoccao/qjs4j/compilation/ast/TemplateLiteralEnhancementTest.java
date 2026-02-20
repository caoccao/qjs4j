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

package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.compilation.Lexer;
import com.caoccao.qjs4j.compilation.parser.Parser;
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
    public void testStringRawUsesRawTemplateParts() {
        assertStringWithJavet(
                "String.raw`line\\nend`",
                "String.raw`a\\n${1}b`",
                "String.raw`\\u{41}`");
    }

    @Test
    public void testTaggedTemplateCallSiteCaching() {
        assertBooleanWithJavet(
                """
                        (() => {
                            delete globalThis.__templateFirst;
                            function tag(parts) {
                                if (globalThis.__templateFirst === undefined) {
                                    globalThis.__templateFirst = parts;
                                    return true;
                                }
                                return globalThis.__templateFirst === parts;
                            }
                            function invoke() {
                                return tag`x`;
                            }
                            const result = invoke() && invoke();
                            delete globalThis.__templateFirst;
                            return result;
                        })()""",
                """
                        (() => {
                            delete globalThis.__templateFirst;
                            delete globalThis.__templateSecond;
                            function tag(parts) {
                                if (globalThis.__templateFirst === undefined) {
                                    globalThis.__templateFirst = parts;
                                } else {
                                    globalThis.__templateSecond = parts;
                                }
                            }
                            tag`x`;
                            tag`x`;
                            const result = globalThis.__templateFirst !== globalThis.__templateSecond;
                            delete globalThis.__templateFirst;
                            delete globalThis.__templateSecond;
                            return result;
                        })()""");
    }

    @Test
    public void testTaggedTemplateDescriptorAndFrozenSemantics() {
        assertBooleanWithJavet(
                """
                        (() => {
                            function tag(parts) {
                                const rawDesc = Object.getOwnPropertyDescriptor(parts, "raw");
                                const partDesc = Object.getOwnPropertyDescriptor(parts, 0);
                                const rawPartDesc = Object.getOwnPropertyDescriptor(parts.raw, 0);
                                const lengthDesc = Object.getOwnPropertyDescriptor(parts, "length");
                                const rawLengthDesc = Object.getOwnPropertyDescriptor(parts.raw, "length");
                                return Object.isFrozen(parts)
                                    && Object.isFrozen(parts.raw)
                                    && !Object.isExtensible(parts)
                                    && !Object.isExtensible(parts.raw)
                                    && rawDesc.enumerable === false
                                    && rawDesc.writable === false
                                    && rawDesc.configurable === false
                                    && partDesc.enumerable === true
                                    && partDesc.writable === false
                                    && partDesc.configurable === false
                                    && rawPartDesc.enumerable === true
                                    && rawPartDesc.writable === false
                                    && rawPartDesc.configurable === false
                                    && lengthDesc.enumerable === false
                                    && lengthDesc.writable === false
                                    && lengthDesc.configurable === false
                                    && rawLengthDesc.enumerable === false
                                    && rawLengthDesc.writable === false
                                    && rawLengthDesc.configurable === false;
                            }
                            return tag`a${1}b`;
                        })()""");
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
    public void testTaggedTemplateObjectIsReadOnlyInStrictMode() {
        assertBooleanWithJavet(
                """
                        (() => {
                            "use strict";
                            function tag(parts) {
                                let threwPart = false;
                                let threwRaw = false;
                                try {
                                    parts[0] = "mutated";
                                } catch (e) {
                                    threwPart = e instanceof TypeError;
                                }
                                try {
                                    parts.raw = [];
                                } catch (e) {
                                    threwRaw = e instanceof TypeError;
                                }
                                return threwPart
                                    && threwRaw
                                    && parts[0] === "a"
                                    && parts.raw[0] === "a";
                            }
                            return tag`a`;
                        })()""");
    }

    @Test
    public void testTaggedTemplateRawUsesArrayPrototypeMethods() {
        assertBooleanWithJavet(
                """
                        (() => {
                            function tag(parts) {
                                return parts.map(segment => segment).join("|") === "a|b"
                                    && parts.raw.map(segment => segment).join("|") === "a|b";
                            }
                            return tag`a${1}b`;
                        })()""");
    }

    @Test
    public void testTaggedTemplateUndefinedCookedSegmentForInvalidEscape() {
        assertStringWithJavet("((parts) => typeof parts[0])`\\u{110000}`");
        assertStringWithJavet("((parts) => parts.raw[0])`\\u{110000}`");
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
    public void testUntaggedTemplateInvalidEscapeThrows() {
        assertThatThrownBy(() -> new Parser(new Lexer("`bad\\u{110000}`;")).parse())
                .isInstanceOf(JSSyntaxErrorException.class);
    }

    @Test
    public void testUntaggedTemplateUnicodeEscapes() {
        assertStringWithJavet("`\\u{41}\\x42`");
    }
}
