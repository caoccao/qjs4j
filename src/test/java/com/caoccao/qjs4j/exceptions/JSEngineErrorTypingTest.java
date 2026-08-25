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

package com.caoccao.qjs4j.exceptions;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Errors raised inside the engine's value classes must carry a JavaScript error type.
 * <p>
 * {@code JSArrayBuffer}, {@code JSTypedArray}, {@code JSDataView}, the weak collections, the
 * number formatters and the RegExp bytecode used to raise
 * {@code IllegalArgumentException}/{@code IllegalStateException}. Those carry no error type, so
 * every caller had to convert them by hand; a path that forgot reported an opaque internal error
 * instead of the specified {@code TypeError} or {@code RangeError}. They now raise
 * {@code JSTypeErrorException}, {@code JSRangeErrorException} or {@code JSSyntaxErrorException},
 * which the interpreter turns into a catchable JavaScript error of the right type on its own.
 * <p>
 * Each case is checked against V8, so the error type is verified, not just its presence.
 */
public class JSEngineErrorTypingTest extends BaseJavetTest {

    @Test
    public void testArrayBufferResizeErrorsAreRangeErrors() {
        assertStringWithJavet(
                """
                        const buffer = new ArrayBuffer(8, { maxByteLength: 16 });
                        try { buffer.resize(-1) } catch (e) { 'CAUGHT ' + e.name }""",
                """
                        const buffer = new ArrayBuffer(8, { maxByteLength: 16 });
                        try { buffer.resize(99) } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testArrayBufferResizeStillWorks() {
        assertStringWithJavet(
                """
                        const buffer = new ArrayBuffer(8, { maxByteLength: 16 });
                        buffer.resize(12);
                        String(buffer.byteLength)""");
    }

    @Test
    public void testDetachedArrayBufferErrorsAreTypeErrors() {
        assertStringWithJavet(
                """
                        const buffer = new ArrayBuffer(8);
                        buffer.transfer();
                        try { buffer.slice(0) } catch (e) { 'CAUGHT ' + e.name }""",
                """
                        const buffer = new ArrayBuffer(8);
                        buffer.transfer();
                        try { buffer.transfer() } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testDetachedDataViewAccessIsATypeError() {
        assertStringWithJavet(
                """
                        const buffer = new ArrayBuffer(8);
                        const view = new DataView(buffer);
                        buffer.transfer();
                        try { view.getInt8(0) } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testNumberFormattingRangeErrors() {
        assertStringWithJavet(
                "try { (1.5).toFixed(101) } catch (e) { 'CAUGHT ' + e.name }",
                "try { (1.5).toPrecision(0) } catch (e) { 'CAUGHT ' + e.name }",
                "try { (1.5).toString(1) } catch (e) { 'CAUGHT ' + e.name }",
                "try { (1.5).toString(37) } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    public void testNumberFormattingStillWorks() {
        assertStringWithJavet(
                "(1.5).toFixed(2)",
                "(1.5).toPrecision(3)",
                "(255).toString(16)");
    }

    @Test
    public void testRegExpErrorsAreSyntaxErrors() {
        assertStringWithJavet(
                "try { new RegExp('a', 'qq') } catch (e) { 'CAUGHT ' + e.name }",
                "try { new RegExp('a', 'gg') } catch (e) { 'CAUGHT ' + e.name }",
                "try { new RegExp('[z-a]') } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    public void testSharedArrayBufferGrowErrors() {
        assertStringWithJavet(
                """
                        const buffer = new SharedArrayBuffer(8);
                        try { buffer.grow(16) } catch (e) { 'CAUGHT ' + e.name }""",
                """
                        const buffer = new SharedArrayBuffer(8, { maxByteLength: 16 });
                        try { buffer.grow(4) } catch (e) { 'CAUGHT ' + e.name }""",
                "try { new SharedArrayBuffer(8, { maxByteLength: 4 }) } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    public void testWeakCollectionKeyErrorsAreTypeErrors() {
        assertStringWithJavet(
                "try { new WeakMap().set(1, 2) } catch (e) { 'CAUGHT ' + e.name }",
                "try { new WeakSet().add(1) } catch (e) { 'CAUGHT ' + e.name }",
                "try { new WeakRef(1) } catch (e) { 'CAUGHT ' + e.name }");
    }

    @Test
    public void testWeakCollectionsStillAcceptObjects() {
        assertStringWithJavet(
                """
                        const key = {};
                        const map = new WeakMap();
                        map.set(key, 'value');
                        map.get(key)""");
    }
}
