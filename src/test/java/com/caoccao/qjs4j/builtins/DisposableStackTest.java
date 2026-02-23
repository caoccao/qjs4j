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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class DisposableStackTest extends BaseJavetTest {
    @Test
    public void testConstructorAndSymbols() {
        assertBooleanWithJavet("""
                typeof DisposableStack === "function"
                && typeof Symbol.dispose === "symbol"
                && typeof Symbol.asyncDispose === "symbol"
                """);
    }

    @Test
    public void testDeferOnDisposedStackThrows() {
        assertErrorWithJavet("""
                (() => {
                    const stack = new DisposableStack();
                    stack.dispose();
                    stack.defer(() => {});
                })()""");
    }

    @Test
    public void testDisposeIsIdempotent() {
        assertIntegerWithJavet("""
                (() => {
                    const stack = new DisposableStack();
                    let count = 0;
                    stack.defer(() => { count++; });
                    stack.dispose();
                    stack.dispose();
                    return count;
                })()""");
    }

    @Test
    public void testMoveTransfersResources() {
        assertStringWithJavet("""
                (() => {
                    const log = [];
                    const stack1 = new DisposableStack();
                    stack1.defer(() => { log.push(1); });
                    const stack2 = stack1.move();
                    stack2.dispose();
                    return stack1.disposed + "|" + stack2.disposed + "|" + JSON.stringify(log);
                })()""");
    }

    @Test
    public void testSuppressedErrorChaining() {
        assertStringWithJavet("""
                (() => {
                    const stack = new DisposableStack();
                    stack.defer(() => { throw new Error("first"); });
                    stack.defer(() => { throw new Error("second"); });
                    try {
                        stack.dispose();
                        return "false";
                    } catch (e) {
                        return String(
                            e.name === "SuppressedError"
                            && e.error.message.includes("first")
                            && e.suppressed.message.includes("second")
                        );
                    }
                })()""");
    }

    @Test
    public void testSymbolDisposeAlias() {
        assertStringWithJavet("""
                (() => {
                    const log = [];
                    const stack = new DisposableStack();
                    stack.defer(() => { log.push("x"); });
                    stack[Symbol.dispose]();
                    return JSON.stringify(log) + "|" + String(stack.disposed);
                })()""");
    }

    @Test
    public void testUseAdoptDeferLifoOrder() {
        assertStringWithJavet("""
                (() => {
                    const log = [];
                    const stack = new DisposableStack();
                    const a = { [Symbol.dispose]() { log.push("a"); } };
                    const b = { [Symbol.dispose]() { log.push("b"); } };
                    stack.use(a);
                    stack.adopt("x", (value) => { log.push(value); });
                    stack.defer(() => { log.push("d"); });
                    stack.use(b);
                    stack.dispose();
                    return JSON.stringify(log);
                })()""");
    }

    @Test
    public void testUseNonDisposableThrows() {
        assertErrorWithJavet("""
                (() => {
                    const stack = new DisposableStack();
                    stack.use(1);
                })()""");
    }

    @Test
    public void testUseNullAndUndefined() {
        assertBooleanWithJavet("""
                (() => {
                    const stack = new DisposableStack();
                    return stack.use(null) === null && stack.use(undefined) === undefined;
                })()""");
    }
}
