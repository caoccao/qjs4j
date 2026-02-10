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

public class UsingDeclarationTest extends BaseJavetTest {
    @Test
    public void testAwaitUsingAsyncDisposeOnReturn() {
        assertStringWithJavet("""
                (async () => {
                    const log = [];
                    async function f() {
                        await using r = {
                            [Symbol.asyncDispose]() { log.push("a"); }
                        };
                        log.push("b");
                        return 1;
                    }
                    const value = await f();
                    return JSON.stringify(log) + "|" + String(value);
                })();""");
    }

    @Test
    public void testAwaitUsingFallsBackToSymbolDispose() {
        assertStringWithJavet("""
                (async () => {
                    const log = [];
                    {
                        await using r = {
                            [Symbol.dispose]() { log.push("s"); }
                        };
                        log.push("b");
                    }
                    return JSON.stringify(log);
                })();""");
    }

    @Test
    public void testUsingDisposesOnBlockExit() {
        assertStringWithJavet("""
                const log = [];
                {
                    using r = { [Symbol.dispose]() { log.push("d"); } };
                    log.push("b");
                }
                JSON.stringify(log);""");
    }

    @Test
    public void testUsingDisposesOnBreakAndContinue() {
        assertStringWithJavet("""
                const log = [];
                for (let i = 0; i < 3; i++) {
                    using r = { [Symbol.dispose]() { log.push("d" + i); } };
                    if (i === 1) {
                        continue;
                    }
                    if (i === 2) {
                        break;
                    }
                    log.push("b" + i);
                }
                JSON.stringify(log);""");
    }

    @Test
    public void testUsingDisposesOnReturn() {
        assertStringWithJavet("""
                const log = [];
                function f() {
                    using r = { [Symbol.dispose]() { log.push("d"); } };
                    log.push("b");
                    return 7;
                }
                const value = f();
                JSON.stringify(log) + "|" + String(value);""");
    }

    @Test
    public void testUsingDisposesOnThrow() {
        assertStringWithJavet("""
                const log = [];
                try {
                    using r = { [Symbol.dispose]() { log.push("d"); } };
                    throw new Error("x");
                } catch (e) {
                    log.push("c");
                }
                JSON.stringify(log);""");
    }
}
