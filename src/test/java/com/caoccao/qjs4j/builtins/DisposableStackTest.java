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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSTypeConversions;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DisposableStackTest extends BaseTest {
    private String evalToString(String code) {
        JSValue value = context.eval(code);
        return JSTypeConversions.toString(context, value).value();
    }

    @Test
    public void testConstructorAndSymbols() {
        assertThat(evalToString("""
                String(
                    typeof DisposableStack === "function"
                    && typeof Symbol.dispose === "symbol"
                    && typeof Symbol.asyncDispose === "symbol"
                )""")).isEqualTo("true");
    }

    @Test
    public void testDisposeIsIdempotent() {
        assertThat(evalToString("""
                const stack = new DisposableStack();
                let count = 0;
                stack.defer(() => { count++; });
                stack.dispose();
                stack.dispose();
                String(count);""")).isEqualTo("1");
    }

    @Test
    public void testMoveAndDisposedFlag() {
        assertThat(evalToString("""
                const log = [];
                const stack1 = new DisposableStack();
                stack1.defer(() => { log.push(1); });
                const stack2 = stack1.move();
                const moved = stack1.disposed === true && stack2.disposed === false;
                let throwsOnOld = false;
                try {
                    stack1.defer(() => {});
                } catch (e) {
                    throwsOnOld = e instanceof TypeError;
                }
                stack2.dispose();
                String(moved) + "|" + String(throwsOnOld) + "|" + JSON.stringify(log);"""))
                .isEqualTo("true|true|[1]");
    }

    @Test
    public void testSuppressedErrorChaining() {
        assertThat(evalToString("""
                const stack = new DisposableStack();
                stack.defer(() => { throw new Error("first"); });
                stack.defer(() => { throw new Error("second"); });
                try {
                    stack.dispose();
                    "false";
                } catch (e) {
                    String(
                        e.name === "SuppressedError"
                        && e.error.message.includes("first")
                        && e.suppressed.message.includes("second")
                    );
                }""")).isEqualTo("true");
    }

    @Test
    public void testSymbolDisposeAlias() {
        assertThat(evalToString("""
                const log = [];
                const stack = new DisposableStack();
                stack.defer(() => { log.push("x"); });
                stack[Symbol.dispose]();
                JSON.stringify(log) + "|" + String(stack.disposed);"""))
                .isEqualTo("[\"x\"]|true");
    }

    @Test
    public void testUseAdoptDeferLifoOrder() {
        assertThat(evalToString("""
                const log = [];
                const stack = new DisposableStack();
                const a = { [Symbol.dispose]() { log.push("a"); } };
                const b = { [Symbol.dispose]() { log.push("b"); } };
                stack.use(a);
                stack.adopt("x", (value) => { log.push(value); });
                stack.defer(() => { log.push("d"); });
                stack.use(b);
                stack.dispose();
                JSON.stringify(log);""")).isEqualTo("[\"b\",\"d\",\"x\",\"a\"]");
    }

    @Test
    public void testUseValidation() {
        assertThat(evalToString("""
                const stack = new DisposableStack();
                const nullOk = stack.use(null) === null;
                const undefinedOk = stack.use(undefined) === undefined;
                let nonDisposableThrows = false;
                try {
                    stack.use(1);
                } catch (e) {
                    nonDisposableThrows = e instanceof TypeError;
                }
                String(nullOk && undefinedOk && nonDisposableThrows);""")).isEqualTo("true");
    }
}
