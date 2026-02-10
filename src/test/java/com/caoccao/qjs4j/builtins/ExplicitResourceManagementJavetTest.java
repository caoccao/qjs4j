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

/**
 * Cross-engine tests for Explicit Resource Management runtime behavior.
 */
public class ExplicitResourceManagementJavetTest extends BaseJavetTest {
    @Test
    public void testAsyncDisposableStackLifoAndDisposed() {
        assertStringWithJavet("""
                (async () => {
                    globalThis.__order = "";
                    const stack = new AsyncDisposableStack();
                    stack.defer(() => {
                        globalThis.__order += "d";
                    });
                    stack.defer(() => {
                        globalThis.__order += "a";
                    });
                    await stack.disposeAsync();
                    return String(
                        globalThis.__order === "da"
                        && stack.disposed
                    );
                })();""");
    }

    @Test
    public void testDisposableStackLifoAndDisposed() {
        assertStringWithJavet("""
                globalThis.__log = [];
                globalThis.__stack = new DisposableStack();
                __stack.defer(() => {
                    __log.push("d");
                });
                __stack.defer(() => {
                    __log.push("a");
                });
                __stack.dispose();
                String(
                    JSON.stringify(__log) === "[\\"a\\",\\"d\\"]"
                    && __stack.disposed
                );""");
    }

    @Test
    public void testDisposableStackMove() {
        assertStringWithJavet("""
                (() => {
                    globalThis.__counter = 0;
                    const stack1 = new DisposableStack();
                    stack1.defer(() => {
                        globalThis.__counter += 1;
                    });
                    const stack2 = stack1.move();
                    let movedThrows = false;
                    try {
                        stack1.defer(() => {});
                    } catch (e) {
                        movedThrows = e instanceof TypeError;
                    }
                    stack2.dispose();
                    return String(
                        stack1.disposed
                        && stack2.disposed
                        && movedThrows
                        && globalThis.__counter === 1
                    );
                })();""");
    }

    @Test
    public void testDisposableStackTopLevelCallback() {
        assertStringWithJavet("""
                globalThis.__log = [];
                globalThis.__stack = new DisposableStack();
                __stack.defer(() => {
                    __log.push("x");
                });
                __stack.dispose();
                String(JSON.stringify(__log) === "[\\"x\\"]");""");
    }

    @Test
    public void testDisposableStackUsePreservesThis() {
        assertStringWithJavet("""
                globalThis.__log = [];
                globalThis.__stack = new DisposableStack();
                __stack.use({
                    value: "ok",
                    [Symbol.dispose]() {
                        __log.push(this.value);
                    },
                });
                __stack.dispose();
                String(JSON.stringify(__log) === "[\\"ok\\"]");""");
    }

    @Test
    public void testSuppressedErrorShape() {
        assertStringWithJavet("""
                (() => {
                    const stack = new DisposableStack();
                    stack.defer(() => {
                        throw new Error("first");
                    });
                    stack.defer(() => {
                        throw new Error("second");
                    });
                    try {
                        stack.dispose();
                        return "false";
                    } catch (e) {
                        return String(
                            e.name === "SuppressedError"
                            && typeof e.error === "object"
                            && typeof e.suppressed === "object"
                        );
                    }
                })();""");
    }

    @Test
    public void testTypeErrorPropagationFromAsyncDisposer() {
        assertStringWithJavet("""
                (async () => {
                    const stack = new AsyncDisposableStack();
                    stack.defer(() => {
                        globalThis.__missing();
                    });
                    try {
                        await stack.disposeAsync();
                        return "false";
                    } catch (e) {
                        return String(e instanceof TypeError);
                    }
                })();""");
    }

    @Test
    public void testTypeErrorPropagationFromDisposer() {
        assertStringWithJavet("""
                (() => {
                    const stack = new DisposableStack();
                    stack.defer(() => {
                        globalThis.__missing();
                    });
                    try {
                        stack.dispose();
                        return "false";
                    } catch (e) {
                        return String(e instanceof TypeError);
                    }
                })();""");
    }

    @Test
    public void testAwaitUsingDeclarationDisposesOnReturn() {
        assertStringWithJavet("""
                (async () => {
                    const log = [];
                    async function f() {
                        await using r = {
                            [Symbol.asyncDispose]() {
                                log.push("a");
                            }
                        };
                        log.push("b");
                        return 2;
                    }
                    const value = await f();
                    return JSON.stringify(log) + "|" + String(value);
                })();""");
    }

    @Test
    public void testAwaitUsingDeclarationFallsBackToSymbolDispose() {
        assertStringWithJavet("""
                (async () => {
                    const log = [];
                    {
                        await using r = {
                            [Symbol.dispose]() {
                                log.push("s");
                            }
                        };
                        log.push("b");
                    }
                    return JSON.stringify(log);
                })();""");
    }

    @Test
    public void testUsingDeclarationDisposesOnBlockExitAndReturn() {
        assertStringWithJavet(
                """
                        (() => {
                            const log = [];
                            {
                                using r = {
                                    [Symbol.dispose]() {
                                        log.push("d");
                                    }
                                };
                                log.push("b");
                            }
                            return JSON.stringify(log);
                        })();""",
                """
                        (() => {
                            const log = [];
                            function f() {
                                using r = {
                                    [Symbol.dispose]() {
                                        log.push("d");
                                    }
                                };
                                log.push("b");
                                return 7;
                            }
                            const value = f();
                            return JSON.stringify(log) + "|" + String(value);
                        })();""");
    }

    @Test
    public void testWellKnownSymbols() {
        assertStringWithJavet("""
                String(
                    typeof Symbol.dispose === "symbol"
                    && typeof Symbol.asyncDispose === "symbol"
                    && Symbol.dispose !== Symbol.asyncDispose
                )""");
    }
}
