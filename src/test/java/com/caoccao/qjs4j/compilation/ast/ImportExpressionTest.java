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
import org.junit.jupiter.api.Test;

class ImportExpressionTest extends BaseJavetTest {

    @Test
    void testDynamicImportReturnsPromise() {
        // import() should return a promise
        assertStringWithJavet("typeof import('nonexistent').then");
    }

    @Test
    void testDynamicImportReturnsPromiseObject() {
        // import() result should be an object (Promise)
        assertStringWithJavet("typeof import('nonexistent')");
    }

    @Test
    void testDynamicImportCatchReturnsPromise() {
        // import().catch() should also return a promise
        assertStringWithJavet("typeof import('nonexistent').catch(e => e)");
    }

    @Test
    void testDynamicImportRejectedPromise() {
        // import() of non-existent module should reject
        assertBooleanWithJavet(
                "let rejected = false;\n" +
                "import('nonexistent').catch(e => { rejected = true; });\n" +
                "rejected"
        );
    }

    @Test
    void testDynamicImportWithOptions() {
        // import() with options should still return a promise
        assertStringWithJavet("typeof import('nonexistent', {})");
    }

    @Test
    void testDynamicImportWithOptionsAndWith() {
        // import() with options.with should still return a promise
        assertStringWithJavet("typeof import('nonexistent', { with: { type: 'json' } })");
    }

    @Test
    void testDynamicImportSpecifierToString() {
        // import() should convert specifier to string
        assertBooleanWithJavet(
                "let caught = false;\n" +
                "import({toString() { return 'nonexistent'; }}).catch(e => { caught = true; });\n" +
                "caught"
        );
    }

    @Test
    void testDynamicImportInNonModuleCode() {
        // import() should work in non-module (script) code too
        assertStringWithJavet("typeof import('nonexistent').then");
    }

    @Test
    void testDynamicImportInAsyncFunction() {
        // import() should work inside async functions
        assertStringWithJavet(
                "let result = '';\n" +
                "async function f() { result = typeof import('x').then; }\n" +
                "f();\n" +
                "result"
        );
    }

    @Test
    void testDynamicImportWithTrailingComma() {
        // import() should support trailing comma
        assertStringWithJavet("typeof import('nonexistent',)");
    }

    @Test
    void testDynamicImportWithTwoArgsTrailingComma() {
        // import() should support trailing comma after options
        assertStringWithJavet("typeof import('nonexistent', {},)");
    }
}
