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
import org.junit.jupiter.api.Test;

/**
 * Only a call in tail position becomes a tail call.
 * <p>
 * A strict-mode {@code return f()} in a try block was compiled as a tail call, which discarded the frame and its catch
 * handler, so {@code try { return f() } catch (e) {}} let the exception escape. A call in a catch block of a try
 * statement without finally is in tail position and still runs as one; a call where a finally block must run is not.
 */
public class TailCallPositionTest extends BaseJavetTest {
    @Test
    public void testCallsInTryStatements() {
        assertStringWithJavet("""
                'use strict';
                function g() { throw new Error('x'); }
                function f() { try { return g(); } catch (e) { return 'caught bytecode'; } }
                function h() { try { return JSON.parse('{'); } catch (e) { return 'caught native'; } }
                const arrow = () => { try { return g(); } catch (e) { return 'caught arrow'; } };
                function nested() { try { if (true) { return g(); } } catch (e) { return 'caught nested'; } }
                function inCatch() { try { throw 1; } catch (e) { return g(); } }
                function outer() { try { return inCatch(); } catch (e) { return 'caught from catch block'; } }
                function conditional(flag) { try { return flag ? g() : 0; } catch (e) { return 'caught conditional'; } }
                function inLoop() { for (const x of [1]) { return g(); } }
                function loopCaller() { try { return inLoop(); } catch (e) { return 'caught loop'; } }
                const log = [];
                function step() { log.push('call'); return 1; }
                function withFinally() { try { return step(); } finally { log.push('finally'); } }
                withFinally();
                [f(), h(), arrow(), nested(), outer(), conditional(true), loopCaller(), log.join()].join('\\n')""");
    }

    @Test
    public void testDeepRecursionAfterTryStatementStillUsesTailCalls() {
        // 100,000 frames would overflow without the tail call; V8 has no proper tail calls, so it is not compared.
        assertEvalJavaValue("""
                'use strict';
                function count(n) {
                  try {
                    if (n === 0) throw new Error('done');
                  } catch (e) {
                    return 'bottom';
                  }
                  return count(n - 1);
                }
                count(100000)""", "bottom");
    }
}
