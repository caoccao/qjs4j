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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the ECMAScript arguments object.
 * The arguments object is an array-like object accessible inside functions
 * that contains the values of the arguments passed to that function.
 */
public class JSArgumentsTest {

    @Test
    public void testArgumentsAccess() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    function test(a, b) {
                        return arguments[0] + arguments[1] + arguments[2];
                    }
                    test(10, 20, 30);
                    """;
            JSValue result = context.eval(code);
            assertThat(result).isInstanceOf(JSNumber.class);
            assertThat(((JSNumber) result).value()).isEqualTo(60.0);
        }
    }

    @Test
    public void testArgumentsArrayLike() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    function test() {
                        let sum = 0;
                        for (let i = 0; i < arguments.length; i++) {
                            sum += arguments[i];
                        }
                        return sum;
                    }
                    test(1, 2, 3, 4, 5);
                    """;
            JSValue result = context.eval(code);
            assertThat(result).isInstanceOf(JSNumber.class);
            assertThat(((JSNumber) result).value()).isEqualTo(15.0);
        }
    }

    @Test
    public void testArgumentsFewerThanParameters() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    function test(a, b, c, d) {
                        return arguments.length;
                    }
                    test(1, 2);
                    """;
            JSValue result = context.eval(code);
            assertThat(result).isInstanceOf(JSNumber.class);
            assertThat(((JSNumber) result).value()).isEqualTo(2.0);
        }
    }

    @Test
    public void testArgumentsInMethodContext() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    const obj = {
                        method: function(a, b, c) {
                            return arguments.length;
                        }
                    };
                    obj.method(1, 2, 3, 4, 5);
                    """;
            JSValue result = context.eval(code);
            assertThat(result).isInstanceOf(JSNumber.class);
            assertThat(((JSNumber) result).value()).isEqualTo(5.0);
        }
    }

    @Test
    public void testArgumentsInNestedFunction() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    function outer(a, b) {
                        function inner(x, y) {
                            return arguments.length;
                        }
                        return inner(1, 2, 3);
                    }
                    outer(10, 20);
                    """;
            JSValue result = context.eval(code);
            assertThat(result).isInstanceOf(JSNumber.class);
            // inner function is called with 3 arguments
            assertThat(((JSNumber) result).value()).isEqualTo(3.0);
        }
    }

    @Test
    public void testArgumentsLength() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    function test(a, b) {
                        return arguments.length;
                    }
                    test(1, 2, 3, 4);
                    """;
            JSValue result = context.eval(code);
            assertThat(result).isInstanceOf(JSNumber.class);
            assertThat(((JSNumber) result).value()).isEqualTo(4.0);
        }
    }

    @Test
    public void testArgumentsMoreThanParameters() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    function test(a, b) {
                        return arguments.length;
                    }
                    test(1, 2, 3, 4, 5);
                    """;
            JSValue result = context.eval(code);
            assertThat(result).isInstanceOf(JSNumber.class);
            assertThat(((JSNumber) result).value()).isEqualTo(5.0);
        }
    }

    // TODO: Arguments modification needs proper indexed property handling
    // @Test
    // public void testArgumentsModification() {
    //     try (JSContext context = new JSContext(new JSRuntime())) {
    //         String code = """
    //                 function test(a, b) {
    //                     arguments[0] = 100;
    //                     return arguments[0];
    //                 }
    //                 test(1, 2);
    //                 """;
    //         JSValue result = context.eval(code);
    //         assertThat(result).isInstanceOf(JSNumber.class);
    //         assertThat(((JSNumber) result).value()).isEqualTo(100.0);
    //     }
    // }

    // TODO: Re-enable when REST opcode is fully implemented
    // @Test
    // public void testArgumentsWithRestParameters() {
    //     try (JSContext context = new JSContext(new JSRuntime())) {
    //         String code = """
    //                 function test(a, ...rest) {
    //                     // arguments includes all arguments, not just rest
    //                     return arguments.length;
    //                 }
    //                 test(1, 2, 3, 4);
    //                 """;
    //         JSValue result = context.eval(code);
    //         assertThat(result).isInstanceOf(JSNumber.class);
    //         assertThat(((JSNumber) result).value()).isEqualTo(4.0);
    //     }
    // }

    // TODO: Arrow functions need to capture outer scope's arguments
    // @Test
    // public void testArgumentsNotInArrowFunction() {
    //     try (JSContext context = new JSContext(new JSRuntime())) {
    //         // Arrow functions don't have their own arguments object
    //         // They inherit arguments from enclosing scope
    //         String code = """
    //                 function outer(a, b) {
    //                     const arrow = () => {
    //                         return arguments.length;
    //                     };
    //                     return arrow();
    //                 }
    //                 outer(1, 2, 3);
    //                 """;
    //         JSValue result = context.eval(code);
    //         assertThat(result).isInstanceOf(JSNumber.class);
    //         // Should return outer's arguments.length
    //         assertThat(((JSNumber) result).value()).isEqualTo(3.0);
    //     }
    // }

    @Test
    public void testArgumentsNoParameters() {
        try (JSContext context = new JSContext(new JSRuntime())) {
            String code = """
                    function test() {
                        return arguments.length;
                    }
                    test(1, 2, 3);
                    """;
            JSValue result = context.eval(code);
            assertThat(result).isInstanceOf(JSNumber.class);
            assertThat(((JSNumber) result).value()).isEqualTo(3.0);
        }
    }
}
