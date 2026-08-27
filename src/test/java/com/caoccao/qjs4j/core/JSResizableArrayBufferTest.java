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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A resizable buffer used to allocate its {@code maxByteLength} at construction, so declaring a
 * growth ceiling cost that much heap immediately, and the four-byte padding was computed in
 * {@code int} — so a length the allocation check explicitly permits wrapped to
 * {@link Integer#MIN_VALUE} and escaped {@code try}/{@code catch} as an internal engine failure.
 */
public class JSResizableArrayBufferTest extends BaseJavetTest {
    @Test
    public void testGrowthPreservesContentsAndZeroesNewBytes() {
        assertStringWithJavet("""
                var b = new ArrayBuffer(4, { maxByteLength: 64 });
                var v = new Uint8Array(b);
                v[0] = 7;
                v[3] = 9;
                b.resize(32);
                var w = new Uint8Array(b);
                [w[0], w[3], w[4], w[31], b.byteLength].join(',');""");
    }

    @Test
    public void testLengthAtTheIntegerBoundaryIsACatchableRangeError() {
        // (byteLength + 3) & ~3 wrapped to Integer.MIN_VALUE here, and the failure surfaced as
        // IllegalArgumentException from ByteBuffer.allocate — an internal engine error no
        // try/catch could see. Not compared against V8: a data block is a Java byte[], so lengths
        // within a few words of Integer.MAX_VALUE are unallocatable on any JVM, while V8 on a
        // 64-bit host allocates them. What matters is that the refusal is a catchable RangeError.
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            for (String length : new String[]{"2147483647", "2147483646", "2147483645", "2147483644"}) {
                assertThat(context.eval(
                        "try { new ArrayBuffer(" + length + "); 'allocated'; } catch (e) { e.name; }",
                        "b.js", false).toString())
                        .as(length)
                        .isEqualTo("RangeError");
            }
        }
    }

    @Test
    public void testMaximumLengthAtTheIntegerBoundaryDoesNotAllocate() {
        // A huge growth ceiling with a small current length is legal and cheap: nothing is
        // allocated for the ceiling until the buffer actually grows.
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSValue result = context.eval(
                    "new ArrayBuffer(0, { maxByteLength: 2147483647 }).maxByteLength", "b.js", false);
            assertThat(result.toString()).isEqualTo("2147483647");
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isZero();
        }
    }

    @Test
    public void testResizableBufferAllocatesItsCurrentLengthNotItsMaximum() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSArrayBuffer buffer = (JSArrayBuffer) context.eval(
                    "new ArrayBuffer(1, { maxByteLength: 33554432 })", "b.js", false);
            assertThat(buffer.getByteLength()).isEqualTo(1);
            assertThat(buffer.getMaxByteLength()).isEqualTo(33554432);
            // Padded to a multiple of four for 16-bit atomics, and no further.
            assertThat(buffer.getBuffer().capacity()).isEqualTo(4);
        }
    }

    @Test
    public void testResizeBeyondMaximumIsStillARangeError() {
        assertStringWithJavet("""
                var b = new ArrayBuffer(4, { maxByteLength: 16 });
                try { b.resize(32); 'resized'; } catch (e) { e.name; }""");
    }

    @Test
    public void testShrinkThenGrowExposesZeroes() {
        assertIntegerWithJavet("""
                var b = new ArrayBuffer(16, { maxByteLength: 64 });
                new Uint8Array(b)[8] = 255;
                b.resize(4);
                b.resize(16);
                new Uint8Array(b)[8];""");
    }

    @Test
    public void testShrinkThenGrowPastCapacityExposesZeroes() {
        assertStringWithJavet("""
                var b = new ArrayBuffer(16, { maxByteLength: 128 });
                new Uint8Array(b)[8] = 255;
                b.resize(4);
                b.resize(64);
                [new Uint8Array(b)[8], b.byteLength].join(',');""");
    }
}
