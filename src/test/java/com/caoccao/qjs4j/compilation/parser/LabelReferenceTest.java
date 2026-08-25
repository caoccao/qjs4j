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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * {@code break} and {@code continue} must accept every label name a label definition accepts.
 * <p>
 * The definition side already allowed the contextual keywords ({@code of}, {@code as},
 * {@code from}, {@code async}, {@code let}, {@code yield}, {@code await}), but the reference side
 * matched only {@code TokenType.IDENTIFIER}, so {@code of: { break of; }} was rejected with
 * {@code SyntaxError: Unexpected token 'of'} even though {@code of: { }} parsed fine.
 */
public class LabelReferenceTest extends BaseJavetTest {

    @Test
    public void testBreakAcceptsContextualKeywordLabels() {
        assertStringWithJavet(
                "of: { break of; } 'ok'",
                "as: { break as; } 'ok'",
                "from: { break from; } 'ok'",
                "async: { break async; } 'ok'",
                "get: { break get; } 'ok'",
                "set: { break set; } 'ok'");
    }

    @Test
    public void testBreakAcceptsPlainIdentifierLabels() {
        assertStringWithJavet("outer: { break outer; } 'ok'");
    }

    @Test
    public void testContinueAcceptsContextualKeywordLabels() {
        assertStringWithJavet(
                """
                        let count = 0;
                        of: for (let i = 0; i < 3; i++) { count++; continue of; }
                        String(count)""",
                """
                        let count = 0;
                        from: for (let i = 0; i < 3; i++) { count++; continue from; }
                        String(count)""");
    }

    @Test
    public void testNestedLabelsWithContextualKeywords() {
        assertStringWithJavet(
                """
                        let log = '';
                        of: for (let i = 0; i < 3; i++) {
                            as: for (let j = 0; j < 3; j++) {
                                if (j === 1) continue of;
                                if (i === 2) break of;
                                log += i + '' + j + ' ';
                            }
                        }
                        log.trim()""");
    }

    @Test
    public void testLetLabelInSloppyMode() {
        // `let` is a valid label in sloppy mode only; V8 decides what strict mode does and the
        // strict variant is generated automatically by the harness.
        assertStringWithJavet("lbl: { break lbl; } 'ok'");
    }

    @Test
    public void testUndefinedLabelIsStillASyntaxError() {
        assertErrorWithJavet(
                "of: { break as; }",
                "outer: { break missing; }");
    }

    @Test
    public void testNewlineBeforeLabelStillTriggersAutomaticSemicolonInsertion() {
        // ASI applies before the label, so this is an unlabelled `continue` — legal in a loop.
        assertStringWithJavet(
                """
                        let count = 0;
                        of: for (let i = 0; i < 3; i++) {
                            count++;
                            continue
                            of;
                        }
                        String(count)""");
    }
}
