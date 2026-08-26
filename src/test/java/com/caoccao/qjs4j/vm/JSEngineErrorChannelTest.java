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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Every error the engine can raise must reach the script's own {@code catch} block.
 * <p>
 * The VM routes JavaScript exceptions through the {@code pendingException} field, but the engine
 * also raises errors by throwing Java exceptions ({@code JSException},
 * {@code JSVirtualMachineException}, {@code JSRangeErrorException}, ...) out of an opcode handler.
 * Those used to unwind the Java stack straight out of {@code VirtualMachine.execute()} and were
 * never offered to the script's catch handlers, so an ordinary temporal-dead-zone
 * {@code ReferenceError} killed the whole evaluation.
 * <p>
 * Each snippet below evaluates to {@code "CAUGHT <name>"}. If an error escapes the script's
 * {@code try}, the qjs4j side throws instead of producing a string and the assertion fails.
 */
public class JSEngineErrorChannelTest extends BaseJavetTest {

    @Test
    public void testArrayBufferLengthRangeErrorIsCatchable() {
        assertStringWithJavet(
                "try { new ArrayBuffer(9007199254740991) } catch (e) { 'CAUGHT ' + e.name }",
                "try { new ArrayBuffer(-1) } catch (e) { 'CAUGHT ' + e.name }",
                "try { new ArrayBuffer(8, { maxByteLength: 9007199254740991 }) } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    public void testDataViewRangeErrorIsCatchable() {
        assertStringWithJavet(
                """
                        const buffer = new ArrayBuffer(8);
                        try { new DataView(buffer, 0, 99) } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testErrorRaisedInsideNestedTryIsCatchableByInnerHandler() {
        assertStringWithJavet(
                """
                        let log = '';
                        try {
                            try { tz; let tz = 1 } catch (e) { log += 'inner:' + e.name + ' ' }
                            log += 'after';
                        } catch (e) {
                            log += 'outer:' + e.name;
                        }
                        log""");
    }

    @Test
    public void testErrorRaisedInsideTryIsCatchableAcrossFunctionBoundary() {
        assertStringWithJavet(
                """
                        function raise() { tz; let tz = 1 }
                        try { raise() } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testFinallyStillRunsWhenAnEngineErrorIsRaised() {
        assertStringWithJavet(
                """
                        let log = '';
                        try {
                            try { tz; let tz = 1 } finally { log += 'finally ' }
                        } catch (e) {
                            log += 'CAUGHT ' + e.name;
                        }
                        log""");
    }

    @Test
    public void testPrivateFieldTypeErrorIsCatchable() {
        assertStringWithJavet(
                """
                        class A { #x = 1; static read(o) { return o.#x } }
                        try { A.read({}) } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testRevokedProxyApplyTypeErrorIsCatchable() {
        assertStringWithJavet(
                """
                        const r = Proxy.revocable(function () {}, {});
                        r.revoke();
                        try { r.proxy() } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testRevokedProxyConstructTypeErrorIsCatchable() {
        assertStringWithJavet(
                """
                        const r = Proxy.revocable(function () {}, {});
                        r.revoke();
                        try { new r.proxy() } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testRevokedProxyPropertyReadTypeErrorIsCatchable() {
        assertStringWithJavet(
                """
                        const r = Proxy.revocable({}, {});
                        r.revoke();
                        try { r.proxy.x } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testTemporalDeadZoneClassIsCatchable() {
        assertStringWithJavet(
                """
                        (function () {
                            try { new Later() } catch (e) { return 'CAUGHT ' + e.name }
                            class Later {}
                        })()""");
    }

    @Test
    public void testTemporalDeadZoneConstIsCatchable() {
        assertStringWithJavet(
                "try { tc; const tc = 1 } catch (e) { 'CAUGHT ' + e.name }",
                """
                        (function () {
                            try { tc; const tc = 1 } catch (e) { return 'CAUGHT ' + e.name }
                        })()""");
    }

    @Test
    public void testTemporalDeadZoneLetIsCatchable() {
        assertStringWithJavet(
                "try { tz; let tz = 1 } catch (e) { 'CAUGHT ' + e.name }",
                """
                        (function () {
                            try { tz; let tz = 1 } catch (e) { return 'CAUGHT ' + e.name }
                        })()""");
    }

    @Test
    public void testTypedArrayRangeErrorIsCatchable() {
        assertStringWithJavet(
                "try { new Int8Array(9007199254740991) } catch (e) { 'CAUGHT ' + e.name }",
                """
                        try {
                            new Int32Array(new ArrayBuffer(8)).set(new Array(100));
                        } catch (e) {
                            'CAUGHT ' + e.name;
                        }""");
    }
}
