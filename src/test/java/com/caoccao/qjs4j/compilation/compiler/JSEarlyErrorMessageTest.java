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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The early errors the bytecode compiler raises are script-observable text, so they are held to the
 * same standard as everything else here: the same message V8 produces, compared in full.
 * <p>
 * Four of them said something else — a {@code break} outside a loop, a {@code continue} outside a
 * loop, a {@code continue} whose label names a block rather than a loop (which was not even
 * distinguished from a label that does not exist), and an export of a name nothing defines.
 */
public class JSEarlyErrorMessageTest extends BaseJavetTest {
    @Test
    public void testContinueToALabelThatIsNotALoop() {
        assertErrorWithJavet(
                "foo: { continue foo; }",
                "outer: { while (false) { continue outer; } }");
    }

    @Test
    public void testIllegalBreak() {
        assertErrorWithJavet(
                "break;",
                "function f() { break; }",
                "if (true) { break; }",
                "switch (1) { case 1: (function () { break; })(); }");
    }

    @Test
    public void testIllegalContinue() {
        assertErrorWithJavet(
                "continue;",
                "function f() { continue; }",
                "while (false) { (function () { continue; })(); }");
    }

    @Test
    public void testUndefinedLabel() {
        assertErrorWithJavet(
                "outer: { } break inner;",
                "while (0) {} continue inner;",
                "break missing;");
    }

    /**
     * The module cases, which need module mode.
     */
    public static class ModuleMessages extends BaseJavetTest {
        @BeforeEach
        @Override
        public void setUp() throws Exception {
            super.setUp();
            moduleMode = true;
        }

        @Test
        public void testDuplicateProtoInAModule() {
            assertErrorWithJavet("export {}; const o = { __proto__: null, __proto__: {} };");
        }

        @Test
        public void testExportOfANameNothingDefines() {
            assertErrorWithJavet("export { missing };", "let a = 1; export { a, alsoMissing };");
        }
    }
}
