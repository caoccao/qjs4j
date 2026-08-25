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
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code maxMemoryUsage} and {@code maxStackSize} were public setters that nothing read: an
 * embedder could configure a 1 KiB memory ceiling and a 1 byte stack, and guest code would still
 * allocate a megabyte and recurse a thousand deep. These cases pin that both options are now
 * enforced, that reservations are released, and that the boundary is a catchable guest error.
 */
public class JSMemoryAccountingTest extends BaseTest {
    private static String evalToString(JSRuntime runtime, String code) {
        JSContext context = runtime.createContext();
        try {
            return context.eval(code, "limits.js", false).toString();
        } catch (JSException e) {
            return e.getMessage();
        }
    }

    @Test
    public void testAllocationBelowLimitSucceeds() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024))) {
            assertThat(evalToString(runtime, "new ArrayBuffer(32768).byteLength")).isEqualTo("32768");
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isEqualTo(32768);
        }
    }

    @Test
    public void testAllocationBeyondLimitRaisesCatchableRangeError() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(1024))) {
            // The review's reproducer: a 1 MiB buffer under a 1 KiB ceiling.
            assertThat(evalToString(runtime,
                    "try { new ArrayBuffer(1048576); 'allocated'; } catch (e) { e.name; }"))
                    .isEqualTo("RangeError");
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isZero();
        }
    }

    @Test
    public void testDefaultLimitIsTheDocumentedDefault() {
        try (JSRuntime runtime = new JSRuntime()) {
            assertThat(runtime.getMemoryAccounting().getLimit())
                    .isEqualTo(JSRuntimeOptions.DEFAULT_MAX_MEMORY_USAGE);
        }
    }

    @Test
    public void testDetachReleasesTheReservation() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024))) {
            JSContext context = runtime.createContext();
            JSArrayBuffer buffer = new JSArrayBuffer(context, 16384);
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isEqualTo(16384);
            buffer.detach();
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isZero();
            // Detaching twice must not release twice.
            buffer.detach();
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isZero();
        }
    }

    @Test
    public void testReleasedReservationCannotGrow() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(1024);
        JSMemoryAccounting.Reservation reservation = accounting.registerReservation(new Object(), 0);
        assertThat(reservation.grow(16)).isTrue();
        assertThat(reservation.bytes()).isEqualTo(16);
        reservation.release();
        assertThat(reservation.bytes()).isZero();
        assertThat(reservation.grow(16)).isFalse();
        assertThat(accounting.getReservedBytes()).isZero();
    }

    @Test
    public void testReservationIsPerRuntime() {
        try (JSRuntime first = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024));
             JSRuntime second = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024))) {
            evalToString(first, "globalThis.keep = new ArrayBuffer(32768);");
            assertThat(first.getMemoryAccounting().getReservedBytes()).isEqualTo(32768);
            assertThat(second.getMemoryAccounting().getReservedBytes()).isZero();
        }
    }

    @Test
    public void testReservationRejectsNegativeAmounts() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(1024);
        assertThatThrownBy(() -> accounting.reserve(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounting.release(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testResizeBeyondLimitRaisesRangeError() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(8192))) {
            assertThat(evalToString(runtime, """
                    var b = new ArrayBuffer(16, { maxByteLength: 1048576 });
                    try { b.resize(65536); 'resized'; } catch (e) { e.name; }"""))
                    .isEqualTo("RangeError");
        }
    }

    @Test
    public void testResizeChargesOnlyWhatItGrowsTo() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSArrayBuffer buffer = new JSArrayBuffer(context, 16, 1024 * 1024);
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isEqualTo(16);
            buffer.resize(4096);
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isEqualTo(4096);
        }
    }

    @Test
    public void testSharedArrayBufferMaximumIsCharged() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024))) {
            // A growable SharedArrayBuffer must keep one backing block for all agents, so its
            // maximum is charged up front — and refused when it does not fit.
            assertThat(evalToString(runtime,
                    "try { new SharedArrayBuffer(1, { maxByteLength: 1048576 }); 'allocated'; }"
                            + " catch (e) { e.name; }"))
                    .isEqualTo("RangeError");
            assertThat(evalToString(runtime,
                    "new SharedArrayBuffer(1, { maxByteLength: 4096 }).byteLength"))
                    .isEqualTo("1");
        }
    }

    @Test
    public void testStackSizeDerivesTheCallDepthLimit() {
        JSRuntimeOptions options = new JSRuntimeOptions()
                .setMaxStackSize(20 * JSRuntimeOptions.BYTES_PER_STACK_FRAME);
        assertThat(options.getMaxStackDepth()).isEqualTo(20);
        try (JSRuntime runtime = new JSRuntime(options)) {
            JSContext context = runtime.createContext();
            assertThat(context.getMaxStackDepth()).isEqualTo(20);
            assertThat(evalToString(runtime, """
                    function recurse(n) { return n === 0 ? 0 : recurse(n - 1); }
                    try { recurse(200); 'completed'; } catch (e) { e.name; }"""))
                    .isEqualTo("RangeError");
        }
    }

    @Test
    public void testStackSizeIsClampedToAtLeastOneFrame() {
        assertThat(new JSRuntimeOptions().setMaxStackSize(1).getMaxStackDepth()).isEqualTo(1);
        assertThat(new JSRuntimeOptions().setMaxStackSize(-5).getMaxStackDepth()).isEqualTo(1);
    }

    @Test
    public void testUnlimitedMemoryUsageDisablesTheCeiling() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(0))) {
            assertThat(runtime.getMemoryAccounting().getLimit()).isEqualTo(JSMemoryAccounting.UNLIMITED);
            assertThat(runtime.getMemoryAccounting().wouldExceedLimit(Long.MAX_VALUE)).isFalse();
            assertThat(evalToString(runtime, "new ArrayBuffer(1048576).byteLength")).isEqualTo("1048576");
        }
    }
}
