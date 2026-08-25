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

package com.caoccao.qjs4j.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Atomics} used to call {@code byteArrayViewVarHandle} directly. Those view handles offer no
 * atomic access modes at all on JDK 25, so `Atomics.add` failed there with an
 * {@code UnsupportedOperationException} the engine reported as an internal VM error.
 * <p>
 * Both paths are asserted on whichever JDK the suite runs on: the dispatching methods take the path
 * this JDK supports, and {@code ByteArrayAtomics.Fallback} is called directly so the locked path is
 * covered even where the lock-free one is available.
 */
public class ByteArrayAtomicsTest {
    private static byte[] block() {
        return new byte[32];
    }

    @Test
    public void testByteOperations() {
        byte[] array = block();
        assertThat(ByteArrayAtomics.getAndAddByte(array, 0, (byte) 5)).isZero();
        assertThat(ByteArrayAtomics.getVolatileByte(array, 0)).isEqualTo((byte) 5);
        assertThat(ByteArrayAtomics.getAndSetByte(array, 0, (byte) 9)).isEqualTo((byte) 5);
        assertThat(ByteArrayAtomics.compareAndExchangeByte(array, 0, (byte) 9, (byte) 1)).isEqualTo((byte) 9);
        assertThat(ByteArrayAtomics.compareAndExchangeByte(array, 0, (byte) 9, (byte) 7)).isEqualTo((byte) 1);
        assertThat(ByteArrayAtomics.getVolatileByte(array, 0)).isEqualTo((byte) 1);
        assertThat(ByteArrayAtomics.getAndBitwiseOrByte(array, 0, (byte) 6)).isEqualTo((byte) 1);
        assertThat(ByteArrayAtomics.getAndBitwiseAndByte(array, 0, (byte) 3)).isEqualTo((byte) 7);
        assertThat(ByteArrayAtomics.getAndBitwiseXorByte(array, 0, (byte) 1)).isEqualTo((byte) 3);
        ByteArrayAtomics.setVolatileByte(array, 0, (byte) 42);
        assertThat(ByteArrayAtomics.getVolatileByte(array, 0)).isEqualTo((byte) 42);
    }

    @Test
    public void testFallbackByteOperationsMatchTheLockFreeOnes() {
        byte[] array = block();
        assertThat(ByteArrayAtomics.Fallback.getAndAddByte(array, 0, (byte) 5)).isZero();
        assertThat(ByteArrayAtomics.Fallback.getVolatileByte(array, 0)).isEqualTo((byte) 5);
        assertThat(ByteArrayAtomics.Fallback.getAndSetByte(array, 0, (byte) 9)).isEqualTo((byte) 5);
        assertThat(ByteArrayAtomics.Fallback.compareAndExchangeByte(array, 0, (byte) 9, (byte) 1)).isEqualTo((byte) 9);
        assertThat(ByteArrayAtomics.Fallback.compareAndExchangeByte(array, 0, (byte) 9, (byte) 7)).isEqualTo((byte) 1);
        assertThat(ByteArrayAtomics.Fallback.getAndBitwiseOrByte(array, 0, (byte) 6)).isEqualTo((byte) 1);
        assertThat(ByteArrayAtomics.Fallback.getAndBitwiseAndByte(array, 0, (byte) 3)).isEqualTo((byte) 7);
        assertThat(ByteArrayAtomics.Fallback.getAndBitwiseXorByte(array, 0, (byte) 1)).isEqualTo((byte) 3);
        ByteArrayAtomics.Fallback.setVolatileByte(array, 0, (byte) 42);
        assertThat(ByteArrayAtomics.Fallback.getVolatileByte(array, 0)).isEqualTo((byte) 42);
    }

    @Test
    public void testFallbackIntOperationsMatchTheLockFreeOnes() {
        byte[] lockFree = block();
        byte[] locked = block();
        for (int step = 1; step <= 4; step++) {
            assertThat(ByteArrayAtomics.Fallback.getAndAddInt(locked, 4, step * 7))
                    .isEqualTo(ByteArrayAtomics.getAndAddInt(lockFree, 4, step * 7));
            assertThat(ByteArrayAtomics.Fallback.getAndBitwiseOrInt(locked, 4, step))
                    .isEqualTo(ByteArrayAtomics.getAndBitwiseOrInt(lockFree, 4, step));
            assertThat(ByteArrayAtomics.Fallback.getAndBitwiseXorInt(locked, 4, step * 3))
                    .isEqualTo(ByteArrayAtomics.getAndBitwiseXorInt(lockFree, 4, step * 3));
            assertThat(ByteArrayAtomics.Fallback.getAndBitwiseAndInt(locked, 4, ~step))
                    .isEqualTo(ByteArrayAtomics.getAndBitwiseAndInt(lockFree, 4, ~step));
        }
        assertThat(ByteArrayAtomics.Fallback.getVolatileInt(locked, 4))
                .isEqualTo(ByteArrayAtomics.getVolatileInt(lockFree, 4));
        assertThat(ByteArrayAtomics.Fallback.getAndSetInt(locked, 4, -1))
                .isEqualTo(ByteArrayAtomics.getAndSetInt(lockFree, 4, -1));
        assertThat(ByteArrayAtomics.Fallback.compareAndExchangeInt(locked, 4, -1, 12345))
                .isEqualTo(ByteArrayAtomics.compareAndExchangeInt(lockFree, 4, -1, 12345));
        ByteArrayAtomics.Fallback.setVolatileInt(locked, 4, 0x0BADF00D);
        ByteArrayAtomics.setVolatileInt(lockFree, 4, 0x0BADF00D);
        assertThat(locked).isEqualTo(lockFree);
    }

    @Test
    public void testFallbackLongOperationsMatchTheLockFreeOnes() {
        byte[] lockFree = block();
        byte[] locked = block();
        long[] operands = {1L, -1L, Long.MIN_VALUE, 0x0123456789ABCDEFL};
        for (long operand : operands) {
            assertThat(ByteArrayAtomics.Fallback.getAndAddLong(locked, 8, operand))
                    .isEqualTo(ByteArrayAtomics.getAndAddLong(lockFree, 8, operand));
            assertThat(ByteArrayAtomics.Fallback.getAndBitwiseOrLong(locked, 8, operand))
                    .isEqualTo(ByteArrayAtomics.getAndBitwiseOrLong(lockFree, 8, operand));
            assertThat(ByteArrayAtomics.Fallback.getAndBitwiseXorLong(locked, 8, operand))
                    .isEqualTo(ByteArrayAtomics.getAndBitwiseXorLong(lockFree, 8, operand));
            assertThat(ByteArrayAtomics.Fallback.getAndBitwiseAndLong(locked, 8, operand))
                    .isEqualTo(ByteArrayAtomics.getAndBitwiseAndLong(lockFree, 8, operand));
        }
        assertThat(ByteArrayAtomics.Fallback.getVolatileLong(locked, 8))
                .isEqualTo(ByteArrayAtomics.getVolatileLong(lockFree, 8));
        assertThat(ByteArrayAtomics.Fallback.getAndSetLong(locked, 8, 77L))
                .isEqualTo(ByteArrayAtomics.getAndSetLong(lockFree, 8, 77L));
        assertThat(ByteArrayAtomics.Fallback.compareAndExchangeLong(locked, 8, 77L, -5L))
                .isEqualTo(ByteArrayAtomics.compareAndExchangeLong(lockFree, 8, 77L, -5L));
        ByteArrayAtomics.Fallback.setVolatileLong(locked, 8, Long.MAX_VALUE);
        ByteArrayAtomics.setVolatileLong(lockFree, 8, Long.MAX_VALUE);
        assertThat(locked).isEqualTo(lockFree);
    }

    @Test
    public void testFallbackShortOperationsMatchTheLockFreeOnes() {
        byte[] lockFree = block();
        byte[] locked = block();
        for (short value : new short[]{0, 1, -1, Short.MIN_VALUE, Short.MAX_VALUE, 0x1234}) {
            ByteArrayAtomics.Fallback.setVolatileShort(locked, 2, value);
            ByteArrayAtomics.setVolatileShort(lockFree, 2, value);
            assertThat(ByteArrayAtomics.Fallback.getVolatileShort(locked, 2))
                    .isEqualTo(ByteArrayAtomics.getVolatileShort(lockFree, 2))
                    .isEqualTo(value);
            assertThat(locked).isEqualTo(lockFree);
        }
    }

    @Test
    public void testIntOperationsUseLittleEndianLayout() {
        byte[] array = block();
        ByteArrayAtomics.setVolatileInt(array, 0, 0x04030201);
        assertThat(array[0]).isEqualTo((byte) 1);
        assertThat(array[1]).isEqualTo((byte) 2);
        assertThat(array[2]).isEqualTo((byte) 3);
        assertThat(array[3]).isEqualTo((byte) 4);

        byte[] locked = block();
        ByteArrayAtomics.Fallback.setVolatileLong(locked, 0, 0x0807060504030201L);
        for (int index = 0; index < 8; index++) {
            assertThat(locked[index]).as("byte " + index).isEqualTo((byte) (index + 1));
        }
    }

    @Test
    public void testLockedIncrementsFromManyThreadsDoNotLoseUpdates() throws InterruptedException {
        // The whole point of the fallback: concurrent read-modify-write on one block must not lose
        // an update, whichever path is in force.
        byte[] array = block();
        int threadCount = 8;
        int incrementsPerThread = 5000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        try {
            for (int thread = 0; thread < threadCount; thread++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < incrementsPerThread; i++) {
                            ByteArrayAtomics.Fallback.getAndAddInt(array, 0, 1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(ByteArrayAtomics.Fallback.getVolatileInt(array, 0))
                .isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    public void testOneBlockAlwaysGetsOneLock() {
        // Correctness of the fallback depends on the stripe being a function of the array's
        // identity alone, so every width and every offset within a block agree.
        byte[] array = block();
        ByteArrayAtomics.Fallback.setVolatileInt(array, 0, 1);
        ByteArrayAtomics.Fallback.setVolatileInt(array, 4, 2);
        assertThat(ByteArrayAtomics.Fallback.getVolatileInt(array, 0)).isEqualTo(1);
        assertThat(ByteArrayAtomics.Fallback.getVolatileInt(array, 4)).isEqualTo(2);
        assertThat(ByteArrayAtomics.Fallback.getVolatileLong(array, 0)).isEqualTo(0x0000000200000001L);
    }

    @Test
    public void testPathSelectionMatchesTheRunningJdk() {
        boolean expected = java.lang.invoke.MethodHandles
                .byteArrayViewVarHandle(int[].class, java.nio.ByteOrder.LITTLE_ENDIAN)
                .isAccessModeSupported(java.lang.invoke.VarHandle.AccessMode.GET_AND_ADD);
        assertThat(ByteArrayAtomics.isLockFree()).isEqualTo(expected);
    }
}
