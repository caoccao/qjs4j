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
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSPromise;
import com.caoccao.qjs4j.core.JSTypeConversions;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncDisposableStackTest extends BaseTest {
    private JSPromise evalPromise(String code) {
        JSValue value = context.eval(code);
        assertThat(value).isInstanceOf(JSPromise.class);
        return (JSPromise) value;
    }

    private String evalToString(String code) {
        JSValue value = context.eval(code);
        return JSTypeConversions.toString(context, value).value();
    }

    private void settle(JSPromise promise) {
        assertThat(awaitPromise(promise)).isTrue();
        context.processMicrotasks();
    }

    @Test
    public void testConstructorAndSymbols() {
        assertThat(evalToString("""
                String(
                    typeof AsyncDisposableStack === "function"
                    && typeof Symbol.dispose === "symbol"
                    && typeof Symbol.asyncDispose === "symbol"
                )""")).isEqualTo("true");
    }

    @Test
    public void testDisposeAsyncIsIdempotent() {
        context.eval("""
                let count = 0;
                const stack = new AsyncDisposableStack();
                stack.defer(() => { count++; });
                globalThis.__stack = stack;
                globalThis.__count = () => String(count);""");
        settle(evalPromise("__stack.disposeAsync();"));
        settle(evalPromise("__stack.disposeAsync();"));
        assertThat(evalToString("__count();")).isEqualTo("1");
    }

    @Test
    public void testMoveAndDisposedFlag() {
        context.eval("""
                const log = [];
                const stack1 = new AsyncDisposableStack();
                stack1.defer(() => { log.push(1); });
                const stack2 = stack1.move();
                globalThis.__log = log;
                globalThis.__stack1 = stack1;
                globalThis.__stack2 = stack2;""");
        settle(evalPromise("__stack2.disposeAsync();"));
        assertThat(evalToString("""
                (() => {
                    let throwsOnOld = false;
                    try {
                        __stack1.defer(() => {});
                    } catch (e) {
                        throwsOnOld = e instanceof TypeError;
                    }
                    return String(__stack1.disposed === true && __stack2.disposed === true)
                        + "|" + String(throwsOnOld)
                        + "|" + JSON.stringify(__log);
                })();""")).isEqualTo("true|true|[1]");
    }

    @Test
    public void testSuppressedErrorChaining() {
        context.eval("""
                const stack = new AsyncDisposableStack();
                stack.defer(() => { throw new Error("first"); });
                stack.defer(() => Promise.reject(new Error("second")));
                globalThis.__stack = stack;""");
        JSPromise promise = evalPromise("__stack.disposeAsync();");
        settle(promise);
        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.REJECTED);
        JSValue errorValue = promise.getResult();
        assertThat(errorValue).isInstanceOf(JSObject.class);
        JSObject errorObject = (JSObject) errorValue;
        assertThat(toJSString(errorObject.get("name"))).isEqualTo("SuppressedError");
        assertThat(toJSString(((JSObject) errorObject.get("error")).get("message"))).contains("first");
        assertThat(toJSString(((JSObject) errorObject.get("suppressed")).get("message"))).contains("second");
    }

    @Test
    public void testSymbolAsyncDisposeAlias() {
        context.eval("""
                const log = [];
                const stack = new AsyncDisposableStack();
                stack.defer(() => { log.push("x"); });
                globalThis.__log = log;
                globalThis.__stack = stack;""");
        settle(evalPromise("__stack[Symbol.asyncDispose]();"));
        assertThat(evalToString("JSON.stringify(__log) + '|' + String(__stack.disposed);"))
                .isEqualTo("[\"x\"]|true");
    }

    @Test
    public void testUseAdoptDeferLifoOrder() {
        context.eval("""
                const log = [];
                const stack = new AsyncDisposableStack();
                const a = { [Symbol.asyncDispose]() { log.push("a"); } };
                const b = { [Symbol.dispose]() { log.push("b"); } };
                stack.use(a);
                stack.adopt("x", (value) => { log.push(value); });
                stack.defer(() => { log.push("d"); });
                stack.use(b);
                globalThis.__log = log;
                globalThis.__stack = stack;""");
        settle(evalPromise("__stack.disposeAsync();"));
        assertThat(evalToString("JSON.stringify(__log);")).isEqualTo("[\"b\",\"d\",\"x\",\"a\"]");
    }

    @Test
    public void testUseValidation() {
        assertThat(evalToString("""
                const stack = new AsyncDisposableStack();
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

    private String toJSString(JSValue value) {
        return JSTypeConversions.toString(context, value).value();
    }
}
