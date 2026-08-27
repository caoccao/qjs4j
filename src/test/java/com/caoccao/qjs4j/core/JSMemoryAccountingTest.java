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

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code maxMemoryUsage} and {@code maxStackSize} were public setters that nothing read: an
 * embedder could configure a 1 KiB memory ceiling and a 1 byte stack, and guest code would still
 * allocate a megabyte and recurse a thousand deep. These cases pin that both options are now
 * enforced, that reservations are released, and that the boundary is a catchable guest error.
 */
public class JSMemoryAccountingTest extends BaseTest {
    /**
     * Require that the JVM cannot satisfy the largest data block the engine will accept.
     * <p>
     * The largest block is capped near two gigabytes, so on a big enough heap the request succeeds
     * and there is no allocation failure to roll back. That is a property of the build's
     * {@code -Xmx}, not of the code, so it is stated as an assumption: a heap that grows past this
     * point makes these two tests skip and say why, instead of leaving them green while they stop
     * exercising anything.
     */
    private static void assumeTheJvmRefusesTheLargestBlock() {
        assumeTrue(Runtime.getRuntime().maxMemory() < (long) unallocatableByteLength(),
                "the JVM heap is large enough to allocate the biggest block the engine allows, so "
                        + "there is no allocation failure to observe");
    }

    /**
     * Ask the collector for a while and report whether the reference cleared.
     *
     * @param reference the reference to watch
     * @return true when it cleared
     */
    private static boolean awaitCleared(WeakReference<?> reference) {
        for (int attempt = 0; attempt < 50 && reference.get() != null; attempt++) {
            System.gc();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return reference.get() == null;
    }

    private static String evalToString(JSRuntime runtime, String code) {
        JSContext context = runtime.createContext();
        try {
            return context.eval(code, "limits.js", false).toString();
        } catch (JSException e) {
            return e.getMessage();
        }
    }

    /**
     * A data-block length the JVM will refuse outright.
     * <p>
     * The test task pins {@code -Xmx1g}, so a request of the largest array HotSpot supports fails
     * immediately with {@code OutOfMemoryError} — without heap pressure, because the size exceeds
     * the maximum heap before anything is committed.
     *
     * @return the length in bytes
     */
    private static int unallocatableByteLength() {
        // Rounded down to a multiple of four so it survives the four-byte padding the engine adds
        // for Atomics alignment; otherwise the length check refuses it before any allocation.
        return JSArrayBuffer.MAX_DATA_BLOCK_BYTE_LENGTH & ~3;
    }

    @Test
    public void testALimitedCeilingIsStillTheCeilingNearTheRangeOfALong() {
        // The two guards are independent: the configured limit still applies when it is far below
        // the range of a long.
        JSMemoryAccounting accounting = new JSMemoryAccounting(1024);
        Object owner = new Object();
        assertThat(accounting.reserve(owner, Long.MAX_VALUE)).isNull();
        assertThat(accounting.getReservedBytes()).isZero();
        assertThat(accounting.reserve(owner, 1024)).isNotNull();
        assertThat(accounting.getReservedBytes()).isEqualTo(1024);
        assertThat(owner).isNotNull();
    }

    @Test
    public void testAResizeThatFailsToAllocateGivesTheGrowthBackWhateverTheHeapIs() {
        // The growth is charged before the copy, so a copy that fails has to hand it back. Driven
        // by an allocator that fails on demand, so the test does not depend on the build's -Xmx.
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024))) {
            JSContext context = runtime.createContext();
            JSArrayBuffer buffer = new JSArrayBuffer(context, 1024, 32768);
            long chargedBefore = runtime.getMemoryAccounting().getReservedBytes();

            assertThatThrownBy(() -> buffer.growAccountedBlock(4096, () -> {
                throw new OutOfMemoryError("injected");
            })).isInstanceOf(OutOfMemoryError.class);

            assertThat(runtime.getMemoryAccounting().getReservedBytes())
                    .as("growth that failed to allocate must not stay charged")
                    .isEqualTo(chargedBefore);
            // The buffer is untouched and still resizes normally afterwards.
            buffer.resize(2048);
            assertThat(buffer.getByteLength()).isEqualTo(2048);
            buffer.detach();
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isZero();
        }
    }

    @Test
    public void testAllocationBelowLimitSucceeds() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024))) {
            // Kept reachable on purpose: reading the total reclaims collected blocks, so a buffer
            // nothing refers to may legitimately have stopped counting by then.
            assertThat(evalToString(runtime, "globalThis.keep = new ArrayBuffer(32768); keep.byteLength"))
                    .isEqualTo("32768");
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
    public void testAnAllocationThatFailsGivesItsReservationBackWhateverTheHeapIs() {
        // The same rollback as testFailedAllocationGivesItsReservationBack, driven by an allocator
        // that fails on demand rather than by asking for more memory than the JVM has. Whether a
        // request is bigger than the heap is a property of the build's -Xmx; whether the charge is
        // handed back when an allocation fails is a property of this code, and that is what is
        // being tested.
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024))) {
            JSContext context = runtime.createContext();
            JSArrayBuffer buffer = new JSArrayBuffer(context, 1024);
            long chargedBefore = runtime.getMemoryAccounting().getReservedBytes();
            assertThat(chargedBefore).isEqualTo(1024);

            assertThatThrownBy(() -> buffer.accountBlock(context, 4096, () -> {
                throw new OutOfMemoryError("injected");
            })).isInstanceOf(OutOfMemoryError.class);

            assertThat(runtime.getMemoryAccounting().getReservedBytes())
                    .as("a block that never allocated must not stay charged")
                    .isEqualTo(chargedBefore);
            // The successful path still binds its reservation, so the two are not confusable.
            ByteBuffer allocated = buffer.accountBlock(context, 2048, () -> ByteBuffer.allocate(2048));
            assertThat(allocated.capacity()).isEqualTo(2048);
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isEqualTo(1024 + 2048);
        }
    }

    @Test
    public void testCollectedBufferStopsCountingAgainstTheLimit() {
        // Reclamation used to be a process-wide Cleaner thread reaching into per-runtime state.
        // The reservation is now the weak reference to its own buffer, and the bytes come back on
        // the thread that asks — either when the total is read or, more usefully, when the next
        // allocation consults the limit.
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(64 * 1024))) {
            JSContext context = runtime.createContext();
            JSArrayBuffer buffer = new JSArrayBuffer(context, 48 * 1024);
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isEqualTo(48 * 1024);
            // A second buffer of this size does not fit alongside the first.
            assertThat(evalToString(runtime, "try { new ArrayBuffer(49152); 'allocated'; } catch (e) { e.name; }"))
                    .isEqualTo("RangeError");

            WeakReference<JSArrayBuffer> bufferReference = new WeakReference<>(buffer);
            buffer = null;
            assertThat(awaitCleared(bufferReference)).isTrue();

            assertThat(runtime.getMemoryAccounting().getReservedBytes())
                    .as("a collected data block must stop counting against the limit")
                    .isZero();
            // And the allocation that did not fit a moment ago now does.
            assertThat(evalToString(runtime, "new ArrayBuffer(49152).byteLength")).isEqualTo("49152");
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
    public void testFailedAllocationGivesItsReservationBack() {
        // Accounting is committed before the JVM allocation — refusing afterwards would be
        // pointless — so a JVM allocation failure has to roll the charge back. It used to leave the
        // bytes charged until the half-constructed buffer was collected, which inflated the
        // runtime's ceiling and made later, much smaller allocations fail for space nothing used.
        assumeTheJvmRefusesTheLargestBlock();
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(0))) {
            JSContext context = runtime.createContext();
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isZero();
            assertThatThrownBy(() -> new JSArrayBuffer(context, unallocatableByteLength()))
                    .isInstanceOf(OutOfMemoryError.class);
            assertThat(runtime.getMemoryAccounting().getReservedBytes())
                    .as("a block the JVM refused must not stay charged")
                    .isZero();
            // And the runtime is still usable for an allocation that does fit.
            assertThat(evalToString(runtime, "new ArrayBuffer(1024).byteLength")).isEqualTo("1024");
        }
    }

    @Test
    public void testFailedResizeGivesTheGrowthBackAndLeavesTheBufferAlone() {
        assumeTheJvmRefusesTheLargestBlock();
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(0))) {
            JSContext context = runtime.createContext();
            JSArrayBuffer buffer = new JSArrayBuffer(context, 16, unallocatableByteLength());
            buffer.getBuffer().put(0, (byte) 7);
            long reservedBeforeResize = runtime.getMemoryAccounting().getReservedBytes();

            assertThatThrownBy(() -> buffer.resize(unallocatableByteLength()))
                    .isInstanceOf(OutOfMemoryError.class);

            assertThat(runtime.getMemoryAccounting().getReservedBytes())
                    .as("growth that failed to allocate must not stay charged")
                    .isEqualTo(reservedBeforeResize);
            assertThat(buffer.getByteLength()).as("the original buffer is untouched").isEqualTo(16);
            assertThat(buffer.getBuffer().get(0)).isEqualTo((byte) 7);
            // The reservation still tracks the block that does exist, so releasing it balances.
            buffer.detach();
            assertThat(runtime.getMemoryAccounting().getReservedBytes()).isZero();
        }
    }

    @Test
    public void testGrowingPastTheRangeOfALongIsRefused() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(JSMemoryAccounting.UNLIMITED);
        Object owner = new Object();
        JSMemoryAccounting.Reservation reservation = accounting.reserve(owner, Long.MAX_VALUE - 8);
        assertThat(reservation).isNotNull();
        assertThat(reservation.grow(8)).isTrue();
        assertThat(reservation.grow(1))
                .as("growth is charged through the same checked counter")
                .isFalse();
        assertThat(accounting.getReservedBytes()).isEqualTo(Long.MAX_VALUE);
        assertThat(reservation.bytes()).isEqualTo(Long.MAX_VALUE);
        assertThat(owner).as("the owner stays reachable so nothing reclaims it mid-test").isNotNull();
    }

    @Test
    public void testRefusedReservationChargesNothing() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(64);
        assertThat(accounting.reserve(new Object(), 65)).isNull();
        assertThat(accounting.getReservedBytes()).isZero();
        assertThat(accounting.wouldExceedLimit(64)).isFalse();
    }

    @Test
    public void testReleasedReservationCannotGrow() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(1024);
        Object owner = new Object();
        JSMemoryAccounting.Reservation reservation = accounting.reserve(owner, 0);
        assertThat(reservation.grow(16)).isTrue();
        assertThat(reservation.bytes()).isEqualTo(16);
        reservation.release();
        assertThat(reservation.bytes()).isZero();
        assertThat(reservation.grow(16)).isFalse();
        assertThat(accounting.getReservedBytes()).isZero();
        assertThat(owner).as("the owner stays reachable so nothing reclaims it mid-test").isNotNull();
    }

    @Test
    public void testReservationCanBeShrunkBackAndRejectsNegativeAmounts() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(1024);
        Object owner = new Object();
        JSMemoryAccounting.Reservation reservation = accounting.reserve(owner, 0);
        assertThat(reservation.grow(256)).isTrue();
        reservation.shrink(100);
        assertThat(reservation.bytes()).isEqualTo(156);
        assertThat(accounting.getReservedBytes()).isEqualTo(156);
        // Shrinking past what the reservation holds gives back only what it holds.
        reservation.shrink(1000);
        assertThat(reservation.bytes()).isZero();
        assertThat(accounting.getReservedBytes()).isZero();
        assertThatThrownBy(() -> reservation.shrink(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservation.grow(-1)).isInstanceOf(IllegalArgumentException.class);
        reservation.release();
        // A released reservation ignores further shrinking rather than double-releasing.
        reservation.shrink(64);
        assertThat(accounting.getReservedBytes()).isZero();
        assertThat(owner).as("the owner stays reachable so nothing reclaims it mid-test").isNotNull();
    }

    @Test
    public void testReservationIsChargedAndBoundInOneStep() {
        // The charge and the handle used to be two public calls, so a handle could be registered
        // for bytes nothing had paid for. Releasing it then drove the total below zero, and since
        // the limit check reads `limit - reserved`, a 64-byte ceiling handed out 154 bytes.
        JSMemoryAccounting accounting = new JSMemoryAccounting(64);
        Object charged = new Object();
        Object uncharged = new Object();
        assertThat(accounting.reserve(charged, 10)).isNotNull();
        JSMemoryAccounting.Reservation second = accounting.reserve(uncharged, 40);
        assertThat(second).isNotNull();
        second.release();
        assertThat(accounting.getReservedBytes())
                .as("releasing gives back exactly what was charged")
                .isEqualTo(10);
        // Releasing the same handle again is a no-op rather than a second refund.
        second.release();
        assertThat(accounting.getReservedBytes()).isEqualTo(10);
        assertThat(accounting.reserve(new Object(), 55))
                .as("only 54 bytes are left under the ceiling")
                .isNull();
        assertThat(accounting.reserve(new Object(), 54)).isNotNull();
        assertThat(accounting.getReservedBytes()).isEqualTo(64);
        assertThat(accounting.reserve(new Object(), 1)).isNull();
        assertThat(charged).as("owners stay reachable so nothing reclaims them mid-test").isNotNull();
        assertThat(uncharged).isNotNull();
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
    public void testReservationRejectsNegativeAmountsAndAMissingOwner() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(1024);
        assertThatThrownBy(() -> accounting.reserve(new Object(), -1))
                .isInstanceOf(IllegalArgumentException.class);
        // A reservation with no owner would never be reclaimed, so its bytes would be charged for
        // the runtime's whole life.
        assertThatThrownBy(() -> accounting.reserve(null, 16))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accounting.getReservedBytes()).isZero();
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
    public void testTheTotalNeverGoesBelowZeroUnderConcurrentReserveAndRelease() throws Exception {
        // The counter is the one thing on this class other threads touch, so the floor has to hold
        // when releases and reservations interleave.
        JSMemoryAccounting accounting = new JSMemoryAccounting(JSMemoryAccounting.UNLIMITED);
        int threadCount = 8;
        int roundsPerThread = 500;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicLong lowestObserved = new java.util.concurrent.atomic.AtomicLong();
        List<Thread> threads = new ArrayList<>();
        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int round = 0; round < roundsPerThread; round++) {
                    Object owner = new Object();
                    JSMemoryAccounting.Reservation reservation = accounting.reserve(owner, 128);
                    lowestObserved.accumulateAndGet(accounting.getReservedBytes(), Math::min);
                    reservation.release();
                    reservation.release();
                    lowestObserved.accumulateAndGet(accounting.getReservedBytes(), Math::min);
                    assertThat(owner).isNotNull();
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(lowestObserved.get()).isNotNegative();
        assertThat(accounting.getReservedBytes()).isZero();
    }

    @Test
    public void testUnlimitedMemoryUsageDisablesTheCeiling() {
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setMaxMemoryUsage(0))) {
            assertThat(runtime.getMemoryAccounting().getLimit()).isEqualTo(JSMemoryAccounting.UNLIMITED);
            assertThat(runtime.getMemoryAccounting().wouldExceedLimit(Long.MAX_VALUE)).isFalse();
            assertThat(evalToString(runtime, "new ArrayBuffer(1048576).byteLength")).isEqualTo("1048576");
        }
    }

    @Test
    public void testUnlimitedModeStillRefusesAReservationThatWouldOverflow() {
        // "No limit" is not "no arithmetic". Unlimited mode added without checking, so a total near
        // Long.MAX_VALUE wrapped to a negative byte count — a number the contract says cannot
        // happen, and one the release path then worked from.
        JSMemoryAccounting accounting = new JSMemoryAccounting(JSMemoryAccounting.UNLIMITED);
        Object first = new Object();
        Object second = new Object();
        assertThat(accounting.reserve(first, Long.MAX_VALUE)).isNotNull();
        assertThat(accounting.getReservedBytes()).isEqualTo(Long.MAX_VALUE);

        assertThat(accounting.reserve(second, 1))
                .as("one more byte does not fit in a long")
                .isNull();
        assertThat(accounting.getReservedBytes())
                .as("the refused reservation charged nothing")
                .isEqualTo(Long.MAX_VALUE);
        assertThat(accounting.reserve(second, 0))
                .as("nothing is still nothing")
                .isNotNull();
        assertThat(accounting.getReservedBytes()).isEqualTo(Long.MAX_VALUE);
        assertThat(first).as("owners stay reachable so nothing reclaims them mid-test").isNotNull();
        assertThat(second).isNotNull();
    }

    @Test
    public void testWouldExceedLimitAgreesWithReserveAtAConfiguredCeiling() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(64);
        Object owner = new Object();
        assertThat(accounting.wouldExceedLimit(64)).isFalse();
        assertThat(accounting.wouldExceedLimit(65)).isTrue();
        assertThat(accounting.reserve(owner, 64)).isNotNull();
        assertThat(accounting.wouldExceedLimit(0)).isFalse();
        assertThat(accounting.wouldExceedLimit(1)).isTrue();
        assertThat(accounting.reserve(new Object(), 1)).isNull();
        assertThat(owner).as("the owner stays reachable so nothing reclaims it mid-test").isNotNull();
    }

    @Test
    public void testWouldExceedLimitAgreesWithReserveInUnlimitedMode() {
        // The review's reproduction. The preflight answered `false` unconditionally in unlimited
        // mode while the charge behind it refuses anything that would overflow a long, so a caller
        // that asked first was told capacity existed that the very next call refused.
        JSMemoryAccounting accounting = new JSMemoryAccounting(JSMemoryAccounting.UNLIMITED);
        Object first = new Object();
        assertThat(accounting.wouldExceedLimit(Long.MAX_VALUE)).isFalse();
        assertThat(accounting.reserve(first, Long.MAX_VALUE)).isNotNull();

        assertThat(accounting.wouldExceedLimit(0))
                .as("nothing always fits")
                .isFalse();
        assertThat(accounting.wouldExceedLimit(1))
                .as("and the preflight says so before reserve() proves it")
                .isTrue();
        assertThat(accounting.reserve(new Object(), 1)).isNull();
        assertThat(first).as("the owner stays reachable so nothing reclaims it mid-test").isNotNull();
    }

    @Test
    public void testWouldExceedLimitReclaimsBeforeAnswering() {
        // The preflight predicts the reservation that follows it, so it has to see the same total
        // that reservation would see — including the bytes the collector has already taken back.
        JSMemoryAccounting accounting = new JSMemoryAccounting(64);
        JSMemoryAccounting.Reservation reservation = accounting.reserve(new Object(), 64);
        assertThat(reservation).isNotNull();
        reservation.release();
        assertThat(accounting.wouldExceedLimit(64)).isFalse();
        assertThat(accounting.reserve(new Object(), 64)).isNotNull();
    }

    @Test
    public void testWouldExceedLimitRejectsANegativeSizeExactlyAsReserveDoes() {
        JSMemoryAccounting accounting = new JSMemoryAccounting(64);
        assertThatThrownBy(() -> accounting.wouldExceedLimit(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot reserve a negative number of bytes: -1");
        assertThatThrownBy(() -> accounting.reserve(new Object(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot reserve a negative number of bytes: -1");
    }
}
