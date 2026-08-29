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
 * Module source is put through a line-oriented transformer, and before it gets there every
 * top-level {@code import}/{@code export} declaration is moved onto lines of its own. Inserting a
 * line terminator is not a neutral edit: line terminators are what lets automatic semicolon
 * insertion terminate a statement, so splitting unconditionally handed the parser a semicolon the
 * author never wrote and the engine accepted modules that a conforming parser must reject.
 * <p>
 * The source is now parsed <em>as written</em> before any break is inserted, so the grammar — not a
 * token heuristic — decides whether the split is legal, and source missing a semicolon it needs is
 * rejected with the parser's own diagnostic rather than gaining one.
 */
public class JSModuleAutomaticSemicolonInsertionTest extends BaseJavetTest {
    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        moduleMode = true;
    }

    @Test
    public void testBlockBeforeAnExportIsSplitSafely() {
        assertStringWithJavet("{ globalThis.__probe = 1; } export const v = 2; String(v);");
    }

    @Test
    public void testClassDeclarationBeforeAnExportIsSplitSafely() {
        assertStringWithJavet("class K { m() { return 'k'; } } export const v = new K().m(); v;");
    }

    @Test
    public void testExplicitSemicolonBeforeAnExportIsSplitSafely() {
        // The shape the line-oriented scan was taught to see: the declaration is not first on its
        // line, but the statement in front of it is explicitly terminated.
        assertStringWithJavet("const t = 'first'; export const v = 'second'; t + ',' + v;");
    }

    @Test
    public void testExportClauseWithoutASemicolonBeforeMoreCodeIsRejected() {
        // The reproducer from the review. `export {}` needs a semicolon, and there is neither one
        // nor a line terminator in front of `let`.
        assertErrorWithJavet("export {} let x = 1;");
    }

    @Test
    public void testExportSeparatedByALineTerminatorStaysValid() {
        assertStringWithJavet("export {}\nlet x = 'kept';\nx;");
    }

    @Test
    public void testExportSeparatedByASemicolonStaysValid() {
        assertStringWithJavet("export {}; let x = 'kept'; x;");
    }

    @Test
    public void testFunctionDeclarationBeforeAnExportIsSplitSafely() {
        // A function body's closing brace terminates the declaration without a semicolon, so a
        // break in front of the export cannot change the parse.
        assertStringWithJavet("function f() { return 'called'; } export const v = f(); v;");
    }

    @Test
    public void testImportWithoutASemicolonBeforeMoreCodeIsRejected() {
        assertErrorWithJavet("import { v } from './dep.mjs' let x = 1;");
    }

    @Test
    public void testObjectLiteralWithoutASemicolonBeforeAnExportIsRejected() {
        // The closing brace here ends an expression statement, which does need a semicolon —
        // unlike the block and class-body braces above. Telling those apart is the grammar's job,
        // which is why the source is parsed rather than scanned for this.
        assertErrorWithJavet("let o = { a: 1 } export const v = 2;");
    }

    @Test
    public void testRejectionNamesTheOffendingTokenTheWayTheGrammarDoes() {
        // The rejection comes from the parser, so the token is named by category — reserved word,
        // strict-mode reserved word, identifier, number, string — rather than quoted verbatim.
        // Module code is strict, so `let`, `static` and `yield` are reserved here.
        assertErrorWithJavet(
                "export {} static",
                "export {} yield",
                "export {} enum",
                "export {} await",
                "export {} someIdentifier",
                "export {} 42",
                "export {} 'a string'",
                "export {} null",
                "export {} function f() {}",
                "export {} (x)",
                "export {} from");
    }

    @Test
    public void testSeveralExportDeclarationsOnOneLineStayValid() {
        assertStringWithJavet("export function a() { return 1; } export function b() { return 2; } String(a() + b());");
    }

    @Test
    public void testStatementWithoutASemicolonBeforeAnExportIsRejected() {
        assertErrorWithJavet("const t = 1 export const v = 2;");
    }
}
