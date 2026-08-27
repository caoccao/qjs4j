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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where an {@code export default} expression ends is a grammar question.
 * <p>
 * The line-oriented module transformer answered it by counting brackets, braces and parentheses and
 * taking lines while they were unbalanced. Balanced delimiters do not mark the end of an
 * AssignmentExpression — {@code export default 1 +} is balanced and incomplete — so a valid module
 * became {@code let X = (0, 1 +);} and failed to parse at a position in text the author never
 * wrote. The count also had no notion of a regular expression literal, so {@code /\(/} opened a
 * depth that swallowed the statement after it, and the lexer read the {@code /} after
 * {@code default} as division, so such a module did not tokenise at all.
 * <p>
 * The parser decides it now, and V8 is the arbiter of what it should have decided.
 */
public class JSModuleDefaultExportExtentTest extends BaseJavetTest {
    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        moduleMode = true;
    }

    @Test
    public void testADefaultExportEndedByAutomaticSemicolonInsertion() {
        assertStringWithJavet("export default 1 +\n  2\nString('ok');");
    }

    @Test
    public void testADefaultExportOfAConditionalSpanningLines() {
        assertStringWithJavet("export default true\n  ? 'yes'\n  : 'no';\nString('ok');");
    }

    @Test
    public void testADefaultExportOfAMemberAndCallChainSpanningLines() {
        assertStringWithJavet("export default [1, 2]\n  .map(x => x * 2)\n  .join('-');\nString('ok');");
    }

    @Test
    public void testADefaultExportOfARegularExpressionContainingAClassDelimiter() {
        assertStringWithJavet("export default /[/]/;\nString('ok');");
    }

    @Test
    public void testADefaultExportOfARegularExpressionContainingAQuote() {
        assertStringWithJavet("export default /\"/;\nString('ok');");
    }

    @Test
    public void testADefaultExportOfARegularExpressionContainingAnEscapedDelimiter() {
        // The review's reproduction.
        assertStringWithJavet("export default /\\(/;\nString('ok');");
    }

    @Test
    public void testADefaultExportOfATaggedTemplateSpanningLines() {
        assertStringWithJavet(
                "const tag = (strings) => strings.raw[0];\n"
                        + "export default tag`first\nsecond`;\n"
                        + "String('ok');");
    }

    @Test
    public void testADefaultExportWithACommentAfterAnOperator() {
        assertStringWithJavet("export default 1 + // the second term is on the next line\n  2;\nString('ok');");
    }

    @Test
    public void testADefaultExportWithAnOperatorContinuation() {
        // The review's reproduction.
        assertStringWithJavet("export default 1 +\n  2;\nString('ok');");
    }

    @Test
    public void testCodeAfterADefaultExportOnTheSameLineStillRuns() {
        assertStringWithJavet("export default 1 + 2; globalThis.__probe = 'ran'; String(globalThis.__probe);");
    }

    @Test
    public void testDefaultAsAPropertyNameIsStillDivision() {
        // Teaching the lexer that `/` after `default` opens a regular expression must not break the
        // one other place the keyword can precede a `/`: as a property name, where it is division.
        // Script rather than module source, because a module with no declarations has no completion
        // value to compare.
        moduleMode = false;
        assertStringWithJavet(
                "String(({ default: 8 }).default / 2);",
                "var o = { default: 8 }; String(o?.default / 4);");
    }
}
