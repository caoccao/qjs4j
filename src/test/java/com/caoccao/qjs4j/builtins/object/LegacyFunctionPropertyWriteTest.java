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

package com.caoccao.qjs4j.builtins.object;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Writing a sloppy function's legacy {@code caller} or {@code arguments} property does nothing, as in V8.
 * <p>
 * The write reached {@code Function.prototype}'s {@code %ThrowTypeError%} setter and threw, where V8's setter ignores a
 * sloppy function. The accessor itself stays {@code %ThrowTypeError%}, which test262 checks by identity. The functions
 * are made with {@code new Function} so that they are sloppy in the strict-mode run as well.
 */
public class LegacyFunctionPropertyWriteTest extends BaseJavetTest {
    @Test
    public void testCustomSetterOnFunctionPrototypeStillRuns() {
        assertStringWithJavet("""
                const descriptor = Object.getOwnPropertyDescriptor(Function.prototype, 'caller');
                const log = [];
                Object.defineProperty(Function.prototype, 'caller', {
                  get() { return 'custom'; },
                  set(value) { log.push(value); },
                  configurable: true,
                });
                const sloppy = new Function('return 1');
                sloppy.caller = 1;
                Object.defineProperty(Function.prototype, 'caller', descriptor);
                log.join()""");
    }

    @Test
    public void testThrowTypeErrorElsewhereStillThrows() {
        // Only caller and arguments are ignored. V8 15 copies its own "set caller" function into such a descriptor,
        // which accepts a sloppy function under any key; qjs4j's is %ThrowTypeError%, which keeps throwing.
        assertEvalJavaValue("""
                const thrower = Object.getOwnPropertyDescriptor(Function.prototype, 'caller');
                Object.defineProperty(Function.prototype, 'other', thrower);
                const sloppy = new Function('return 1');
                let result;
                try { sloppy.other = 1; result = 'no error'; } catch (e) { result = e.name; }
                delete Function.prototype.other;
                result""", "TypeError");
    }

    @Test
    public void testWriteToSloppyFunctionIsIgnored() {
        assertStringWithJavet("""
                const sloppy = new Function('return 1');
                const attempt = action => {
                  try {
                    return String(action());
                  } catch (e) {
                    return e.name + ': ' + e.message;
                  }
                };
                [
                  attempt(() => { sloppy.caller = 5; return sloppy.caller; }),
                  attempt(() => { sloppy.arguments = 6; return sloppy.arguments; }),
                  attempt(() => Reflect.set(sloppy, 'caller', 7)),
                  attempt(() => Object.hasOwn(sloppy, 'caller')),
                  attempt(() => { (function () { 'use strict'; }).caller = 1; }),
                  attempt(() => { (() => {}).arguments = 1; }),
                  attempt(() => { sloppy.bind(null).caller = 1; }),
                  attempt(() => Reflect.set({ __proto__: sloppy }, 'caller', 1)),
                ].join('\\n')""");
    }
}
