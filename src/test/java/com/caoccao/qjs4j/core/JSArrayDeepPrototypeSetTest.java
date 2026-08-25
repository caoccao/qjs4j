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

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An indexed array write must obey ordinary {@code [[Set]]} at any prototype depth.
 * <p>
 * The dense fast path first asks whether anything on the prototype chain could observe the write.
 * That question used to be answered by a walk bounded at 1,000 links which returned "no
 * interference" when it ran out of depth — the unsafe direction. A setter 1,001 links up was
 * therefore never called and an own element was created instead, so {@code array[0] = 1} diverged
 * from the specification purely as a function of how deep the chain was.
 * <p>
 * Compared against {@code ../quickjs/qjs}, which calls the setter and creates no own property at
 * every depth.
 */
public class JSArrayDeepPrototypeSetTest extends BaseTest {

    private String assignThroughChain(int depth) {
        // An IIFE, so repeated calls in one test do not redeclare bindings in the global scope.
        return evalToString(
                """
                        (function () {
                          let setterCalls = 0;
                          let terminal = {};
                          Object.defineProperty(terminal, '0', { set(v) { setterCalls++ } });

                          let head = terminal;
                          for (let i = 0; i < %d; i++) head = Object.create(head);

                          let array = [];
                          Object.setPrototypeOf(array, head);
                          array[0] = 1;

                          return setterCalls + ',' + Object.prototype.hasOwnProperty.call(array, '0');
                        })()"""
                        .formatted(depth));
    }

    private String evalToString(String code) {
        return JSTypeConversions.toString(context, context.eval(code)).value();
    }

    @Test
    @Timeout(60)
    public void testInheritedSetterRunsAtShallowDepth() {
        assertThat(assignThroughChain(0)).isEqualTo("1,false");
    }

    @Test
    @Timeout(60)
    public void testInheritedSetterRunsJustBelowTheCutoff() {
        assertThat(assignThroughChain(998)).isEqualTo("1,false");
    }

    @Test
    @Timeout(60)
    public void testInheritedSetterRunsJustBeyondTheCutoff() {
        // 1,001 links is the depth at which the bounded walk answered "no interference".
        assertThat(assignThroughChain(1001)).isEqualTo("1,false");
    }

    @Test
    @Timeout(60)
    public void testInheritedSetterRunsFarBeyondTheCutoff() {
        assertThat(assignThroughChain(3000)).isEqualTo("1,false");
    }

    @Test
    @Timeout(60)
    public void testDeepNonWritableInheritedPropertyBlocksTheWrite() {
        // The same false negative could bypass a distant non-writable property.
        assertThat(evalToString(
                """
                        'use strict';
                        let terminal = {};
                        Object.defineProperty(terminal, '0', { value: 7, writable: false });
                        let head = terminal;
                        for (let i = 0; i < 1001; i++) head = Object.create(head);
                        let array = [];
                        Object.setPrototypeOf(array, head);
                        try { array[0] = 1; 'ASSIGNED' } catch (e) { e.name }"""))
                .isEqualTo("TypeError");
    }

    @Test
    @Timeout(60)
    public void testTypedArrayInTheChainForcesTheSlowPath() {
        // A TypedArray has exotic [[Set]] for canonical numeric indices, so the fast path must
        // defer to it rather than writing an own element.
        assertThat(evalToString(
                """
                        (function () {
                          const array = [];
                          Object.setPrototypeOf(array, new Int8Array(4));
                          array[0] = 1;
                          return array[0] + ',' + Object.prototype.hasOwnProperty.call(array, '0');
                        })()"""))
                .isEqualTo("1,true");
    }

    @Test
    @Timeout(60)
    public void testProxyInTheChainForcesTheSlowPath() {
        assertThat(evalToString(
                """
                        (function () {
                          let trapped = 'no';
                          const proxy = new Proxy({}, { set(t, k, v) { trapped = k; return true } });
                          const array = [];
                          Object.setPrototypeOf(array, proxy);
                          array[0] = 1;
                          return trapped;
                        })()"""))
                .isEqualTo("0");
    }

    @Test
    @Timeout(60)
    public void testOrdinaryArrayWriteStillTakesTheFastPath() {
        // The complement: a plain array with the ordinary prototype chain must still store
        // elements densely and keep length in step.
        assertThat(evalToString(
                """
                        let array = [];
                        for (let i = 0; i < 100; i++) array[i] = i * 2;
                        array.length + ',' + array[99] + ',' + Object.prototype.hasOwnProperty.call(array, '50')"""))
                .isEqualTo("100,198,true");
    }
}
