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
import com.caoccao.qjs4j.core.JSPromise;
import com.caoccao.qjs4j.core.JSTypeConversions;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportExpressionTest extends BaseJavetTest {
    private void assertRejectedPromiseReason(String code, String expectedReason) {
        JSValue jsValue = resetContext().eval(code, FILE_NAME, moduleMode);
        assertThat(jsValue).as(code).isInstanceOf(JSPromise.class);
        JSPromise promise = (JSPromise) jsValue;
        assertThat(awaitPromise(promise)).as(code).isTrue();
        assertThat(promise.getState()).as(code).isEqualTo(JSPromise.PromiseState.REJECTED);
        assertThat(JSTypeConversions.toString(context, promise.getResult()).toString()).as(code).isEqualTo(expectedReason);
    }

    @Test
    void testDynamicImportArgumentConversionRejectsPromise() {
        assertRejectedPromiseReason("""
                import({
                    toString() {
                        throw new TypeError('Invalid module specifier');
                    }
                });
                """, "TypeError: Invalid module specifier");
    }

    @Test
    void testDynamicImportInAsyncFunctionReturnsPromise() {
        assertStringWithJavet(
                """
                        async function load() {
                            return typeof import('missing-dynamic-import-module.js').then;
                        }
                        load();
                        """
        );
    }

    @Test
    void testDynamicImportInvalidOptionsTypeError() {
        assertRejectedPromiseReason(
                "import('missing-dynamic-import-module.js', 1);",
                "TypeError: options must be an object");
    }

    @Test
    void testDynamicImportInvalidWithTypeError() {
        assertRejectedPromiseReason(
                "import('missing-dynamic-import-module.js', { with: 1 });",
                "TypeError: options.with must be an object");
    }

    @Test
    void testDynamicImportMissingModuleError() {
        assertRejectedPromiseReason(
                "import('missing-dynamic-import-module.js');",
                "TypeError: Cannot find module 'missing-dynamic-import-module.js'");
    }

    @Test
    void testDynamicImportReturnsPromise() {
        assertStringWithJavet("typeof import('missing-dynamic-import-module.js').then");
    }

    @Test
    void testDynamicImportReturnsPromiseObject() {
        assertStringWithJavet("typeof import('missing-dynamic-import-module.js')");
    }

    @Test
    void testDynamicImportSpecifierToString() {
        assertRejectedPromiseReason("""
                import({
                    toString() {
                        return 'missing-dynamic-import-module.js';
                    }
                });
                """, "TypeError: Cannot find module 'missing-dynamic-import-module.js'");
    }

    @Test
    void testDynamicImportWithOptionsAccessorError() {
        assertRejectedPromiseReason("""
                import('missing-dynamic-import-module.js', {
                    get with() {
                        throw new TypeError('Invalid import options');
                    }
                });
                """, "TypeError: Invalid import options");
    }

    @Test
    void testDynamicImportWithTrailingComma() {
        assertStringWithJavet("typeof import('missing-dynamic-import-module.js',)");
    }

    @Test
    void testDynamicImportWithTwoArgsTrailingComma() {
        assertStringWithJavet("typeof import('missing-dynamic-import-module.js', {},)");
    }
}
