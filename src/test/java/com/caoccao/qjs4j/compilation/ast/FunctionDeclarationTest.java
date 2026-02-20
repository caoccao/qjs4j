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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSPromise;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for function declarations to debug async/await issues.
 */
public class FunctionDeclarationTest extends BaseTest {

    @Test
    void testAsyncFunctionDeclarationCall() {
        String code = """
                async function test() {
                    return 42;
                }
                test();
                """;

        assertThat(context.eval(code)).as("Async function should return a promise").isInstanceOf(JSPromise.class);
    }

    @Test
    void testAsyncFunctionDeclarationReference() {
        String code = """
                async function test() {
                    return 42;
                }
                test;
                """;

        assertThat(context.eval(code)).as("Should return the function").isInstanceOfSatisfying(JSBytecodeFunction.class, func -> {
            assertThat(func.getName()).isEqualTo("test");
            assertThat(func.isAsync()).as("Function should be marked as async").isTrue();
        });
    }

    @Test
    void testFunctionDeclarationCall() {
        String code = """
                function test() {
                    return 42;
                }
                test();
                """;

        assertThat(context.eval(code)).as("Should return 42").isInstanceOfSatisfying(JSNumber.class, jsNumber -> assertThat(jsNumber.value()).isEqualTo(42.0));
    }

    @Test
    void testSimpleFunctionDeclaration() {
        String code = """
                function test() {
                    return 42;
                }
                test;
                """;

        assertThat(context.eval(code)).as("Should return the function").isInstanceOfSatisfying(JSBytecodeFunction.class, func -> {
            assertThat(func.getName()).isEqualTo("test");
        });
    }
}
