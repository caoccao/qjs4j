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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Atomic 8-, 16-, 32- and 64-bit access to a {@code byte[]} data block, on every supported JDK.
 * <p>
 * <strong>Why this exists.</strong> {@code Atomics} was built directly on
 * {@link MethodHandles#byteArrayViewVarHandle}. On JDK 17 and 21 those view handles support the
 * full set of atomic access modes; <strong>on JDK 25 they support none of them</strong> —
 * {@code isAccessModeSupported} returns false for everything but plain get and set, and every
 * atomic call throws {@code UnsupportedOperationException}. A heap {@code byte[]} carries no
 * alignment guarantee the JVM is willing to stand behind any more, so the modes were withdrawn.
 * Since the engine reports the resulting Java exception as an internal VM error, {@code Atomics.add}
 * on a current JDK failed in a way guest code could not recover from.
 * <p>
 * <strong>How it is fixed.</strong> The lock-free path is used wherever the JDK still offers it,
 * decided once at class initialisation. Where it does not, operations run under a lock chosen from
 * the identity of the backing array, so every access to one data block — whatever its width, and
 * from whichever agent — serialises on the same monitor. Unrelated arrays may share a lock, which
 * costs contention and not correctness. The 8-bit path uses
 * {@link MethodHandles#arrayElementVarHandle}, which is not a view handle and is unaffected, but it
 * still routes through the lock when the fallback is active so that overlapping accesses of
 * different widths stay consistent with each other.
 */
public final class ByteArrayAtomics {
    private static final VarHandle BYTE_VH = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final VarHandle INT_VH =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    /**
     * Locks for the fallback path. A power of two so the stripe is a mask of the identity hash;
     * enough of them that unrelated buffers rarely collide, few enough to be free when unused.
     */
    private static final int LOCK_COUNT = 64;
    private static final Object[] LOCKS = createLocks();
    private static final VarHandle LONG_VH =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle SHORT_VH =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    /**
     * Whether the JDK's byte-array view handles still offer atomic access modes.
     */
    private static final boolean LOCK_FREE = supportsAtomicAccessModes();

    private ByteArrayAtomics() {
    }

    public static byte compareAndExchangeByte(byte[] array, int offset, byte expected, byte replacement) {
        if (LOCK_FREE) {
            return (byte) BYTE_VH.compareAndExchange(array, offset, expected, replacement);
        }
        return Fallback.compareAndExchangeByte(array, offset, expected, replacement);
    }

    public static int compareAndExchangeInt(byte[] array, int offset, int expected, int replacement) {
        if (LOCK_FREE) {
            return (int) INT_VH.compareAndExchange(array, offset, expected, replacement);
        }
        return Fallback.compareAndExchangeInt(array, offset, expected, replacement);
    }

    public static long compareAndExchangeLong(byte[] array, int offset, long expected, long replacement) {
        if (LOCK_FREE) {
            return (long) LONG_VH.compareAndExchange(array, offset, expected, replacement);
        }
        return Fallback.compareAndExchangeLong(array, offset, expected, replacement);
    }

    private static Object[] createLocks() {
        Object[] locks = new Object[LOCK_COUNT];
        for (int index = 0; index < LOCK_COUNT; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    // --- 8-bit ---

    public static byte getAndAddByte(byte[] array, int offset, byte delta) {
        if (LOCK_FREE) {
            return (byte) BYTE_VH.getAndAdd(array, offset, delta);
        }
        return Fallback.getAndAddByte(array, offset, delta);
    }

    public static int getAndAddInt(byte[] array, int offset, int delta) {
        if (LOCK_FREE) {
            return (int) INT_VH.getAndAdd(array, offset, delta);
        }
        return Fallback.getAndAddInt(array, offset, delta);
    }

    public static long getAndAddLong(byte[] array, int offset, long delta) {
        if (LOCK_FREE) {
            return (long) LONG_VH.getAndAdd(array, offset, delta);
        }
        return Fallback.getAndAddLong(array, offset, delta);
    }

    public static byte getAndBitwiseAndByte(byte[] array, int offset, byte operand) {
        if (LOCK_FREE) {
            return (byte) BYTE_VH.getAndBitwiseAnd(array, offset, operand);
        }
        return Fallback.getAndBitwiseAndByte(array, offset, operand);
    }

    public static int getAndBitwiseAndInt(byte[] array, int offset, int operand) {
        if (LOCK_FREE) {
            return (int) INT_VH.getAndBitwiseAnd(array, offset, operand);
        }
        return Fallback.getAndBitwiseAndInt(array, offset, operand);
    }

    public static long getAndBitwiseAndLong(byte[] array, int offset, long operand) {
        if (LOCK_FREE) {
            return (long) LONG_VH.getAndBitwiseAnd(array, offset, operand);
        }
        return Fallback.getAndBitwiseAndLong(array, offset, operand);
    }

    public static byte getAndBitwiseOrByte(byte[] array, int offset, byte operand) {
        if (LOCK_FREE) {
            return (byte) BYTE_VH.getAndBitwiseOr(array, offset, operand);
        }
        return Fallback.getAndBitwiseOrByte(array, offset, operand);
    }

    public static int getAndBitwiseOrInt(byte[] array, int offset, int operand) {
        if (LOCK_FREE) {
            return (int) INT_VH.getAndBitwiseOr(array, offset, operand);
        }
        return Fallback.getAndBitwiseOrInt(array, offset, operand);
    }

    // --- 16-bit ---

    public static long getAndBitwiseOrLong(byte[] array, int offset, long operand) {
        if (LOCK_FREE) {
            return (long) LONG_VH.getAndBitwiseOr(array, offset, operand);
        }
        return Fallback.getAndBitwiseOrLong(array, offset, operand);
    }

    public static byte getAndBitwiseXorByte(byte[] array, int offset, byte operand) {
        if (LOCK_FREE) {
            return (byte) BYTE_VH.getAndBitwiseXor(array, offset, operand);
        }
        return Fallback.getAndBitwiseXorByte(array, offset, operand);
    }

    // --- 32-bit ---

    public static int getAndBitwiseXorInt(byte[] array, int offset, int operand) {
        if (LOCK_FREE) {
            return (int) INT_VH.getAndBitwiseXor(array, offset, operand);
        }
        return Fallback.getAndBitwiseXorInt(array, offset, operand);
    }

    public static long getAndBitwiseXorLong(byte[] array, int offset, long operand) {
        if (LOCK_FREE) {
            return (long) LONG_VH.getAndBitwiseXor(array, offset, operand);
        }
        return Fallback.getAndBitwiseXorLong(array, offset, operand);
    }

    public static byte getAndSetByte(byte[] array, int offset, byte value) {
        if (LOCK_FREE) {
            return (byte) BYTE_VH.getAndSet(array, offset, value);
        }
        return Fallback.getAndSetByte(array, offset, value);
    }

    public static int getAndSetInt(byte[] array, int offset, int value) {
        if (LOCK_FREE) {
            return (int) INT_VH.getAndSet(array, offset, value);
        }
        return Fallback.getAndSetInt(array, offset, value);
    }

    public static long getAndSetLong(byte[] array, int offset, long value) {
        if (LOCK_FREE) {
            return (long) LONG_VH.getAndSet(array, offset, value);
        }
        return Fallback.getAndSetLong(array, offset, value);
    }

    public static byte getVolatileByte(byte[] array, int offset) {
        if (LOCK_FREE) {
            return (byte) BYTE_VH.getVolatile(array, offset);
        }
        return Fallback.getVolatileByte(array, offset);
    }

    public static int getVolatileInt(byte[] array, int offset) {
        if (LOCK_FREE) {
            return (int) INT_VH.getVolatile(array, offset);
        }
        return Fallback.getVolatileInt(array, offset);
    }

    public static long getVolatileLong(byte[] array, int offset) {
        if (LOCK_FREE) {
            return (long) LONG_VH.getVolatile(array, offset);
        }
        return Fallback.getVolatileLong(array, offset);
    }

    // --- 64-bit ---

    public static short getVolatileShort(byte[] array, int offset) {
        if (LOCK_FREE) {
            return (short) SHORT_VH.getVolatile(array, offset);
        }
        return Fallback.getVolatileShort(array, offset);
    }

    /**
     * Whether the lock-free path is in use on this JDK.
     *
     * @return true when byte-array view handles provide atomic access modes
     */
    public static boolean isLockFree() {
        return LOCK_FREE;
    }

    /**
     * The monitor guarding a data block on the fallback path.
     *
     * @param array the backing array
     * @return the lock for that array
     */
    private static Object lockFor(byte[] array) {
        return LOCKS[System.identityHashCode(array) & (LOCK_COUNT - 1)];
    }

    private static int readInt(byte[] array, int offset) {
        return (array[offset] & 0xFF)
                | ((array[offset + 1] & 0xFF) << 8)
                | ((array[offset + 2] & 0xFF) << 16)
                | ((array[offset + 3] & 0xFF) << 24);
    }

    private static long readLong(byte[] array, int offset) {
        return (readInt(array, offset) & 0xFFFFFFFFL)
                | ((long) readInt(array, offset + 4) << 32);
    }

    private static short readShort(byte[] array, int offset) {
        return (short) ((array[offset] & 0xFF) | ((array[offset + 1] & 0xFF) << 8));
    }

    public static void setVolatileByte(byte[] array, int offset, byte value) {
        if (LOCK_FREE) {
            BYTE_VH.setVolatile(array, offset, value);
            return;
        }
        Fallback.setVolatileByte(array, offset, value);
    }

    public static void setVolatileInt(byte[] array, int offset, int value) {
        if (LOCK_FREE) {
            INT_VH.setVolatile(array, offset, value);
            return;
        }
        Fallback.setVolatileInt(array, offset, value);
    }

    public static void setVolatileLong(byte[] array, int offset, long value) {
        if (LOCK_FREE) {
            LONG_VH.setVolatile(array, offset, value);
            return;
        }
        Fallback.setVolatileLong(array, offset, value);
    }

    // --- little-endian plain accessors used by the fallback ---

    public static void setVolatileShort(byte[] array, int offset, short value) {
        if (LOCK_FREE) {
            SHORT_VH.setVolatile(array, offset, value);
            return;
        }
        Fallback.setVolatileShort(array, offset, value);
    }

    private static boolean supportsAtomicAccessModes() {
        try {
            return INT_VH.isAccessModeSupported(VarHandle.AccessMode.GET_VOLATILE)
                    && INT_VH.isAccessModeSupported(VarHandle.AccessMode.SET_VOLATILE)
                    && INT_VH.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE)
                    && INT_VH.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD)
                    && INT_VH.isAccessModeSupported(VarHandle.AccessMode.GET_AND_SET)
                    && INT_VH.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_AND)
                    && INT_VH.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_OR)
                    && INT_VH.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_XOR)
                    && LONG_VH.isAccessModeSupported(VarHandle.AccessMode.GET_VOLATILE)
                    && LONG_VH.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD)
                    && SHORT_VH.isAccessModeSupported(VarHandle.AccessMode.GET_VOLATILE);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void writeInt(byte[] array, int offset, int value) {
        array[offset] = (byte) value;
        array[offset + 1] = (byte) (value >>> 8);
        array[offset + 2] = (byte) (value >>> 16);
        array[offset + 3] = (byte) (value >>> 24);
    }

    private static void writeLong(byte[] array, int offset, long value) {
        writeInt(array, offset, (int) value);
        writeInt(array, offset + 4, (int) (value >>> 32));
    }

    private static void writeShort(byte[] array, int offset, short value) {
        array[offset] = (byte) value;
        array[offset + 1] = (byte) (value >>> 8);
    }

    /**
     * The lock-based implementations, used when the JDK withdraws the atomic access modes.
     * <p>
     * Package-private and named separately from the dispatching methods so both paths can be
     * exercised on whichever JDK the suite happens to run on. Every method here must be called with
     * the array's monitor free; each takes it itself.
     */
    static final class Fallback {
        private Fallback() {
        }

        static byte compareAndExchangeByte(byte[] array, int offset, byte expected, byte replacement) {
            synchronized (lockFor(array)) {
                byte current = array[offset];
                if (current == expected) {
                    array[offset] = replacement;
                }
                return current;
            }
        }

        static int compareAndExchangeInt(byte[] array, int offset, int expected, int replacement) {
            synchronized (lockFor(array)) {
                int current = readInt(array, offset);
                if (current == expected) {
                    writeInt(array, offset, replacement);
                }
                return current;
            }
        }

        static long compareAndExchangeLong(byte[] array, int offset, long expected, long replacement) {
            synchronized (lockFor(array)) {
                long current = readLong(array, offset);
                if (current == expected) {
                    writeLong(array, offset, replacement);
                }
                return current;
            }
        }

        static byte getAndAddByte(byte[] array, int offset, byte delta) {
            synchronized (lockFor(array)) {
                byte current = array[offset];
                array[offset] = (byte) (current + delta);
                return current;
            }
        }

        static int getAndAddInt(byte[] array, int offset, int delta) {
            synchronized (lockFor(array)) {
                int current = readInt(array, offset);
                writeInt(array, offset, current + delta);
                return current;
            }
        }

        static long getAndAddLong(byte[] array, int offset, long delta) {
            synchronized (lockFor(array)) {
                long current = readLong(array, offset);
                writeLong(array, offset, current + delta);
                return current;
            }
        }

        static byte getAndBitwiseAndByte(byte[] array, int offset, byte operand) {
            synchronized (lockFor(array)) {
                byte current = array[offset];
                array[offset] = (byte) (current & operand);
                return current;
            }
        }

        static int getAndBitwiseAndInt(byte[] array, int offset, int operand) {
            synchronized (lockFor(array)) {
                int current = readInt(array, offset);
                writeInt(array, offset, current & operand);
                return current;
            }
        }

        static long getAndBitwiseAndLong(byte[] array, int offset, long operand) {
            synchronized (lockFor(array)) {
                long current = readLong(array, offset);
                writeLong(array, offset, current & operand);
                return current;
            }
        }

        static byte getAndBitwiseOrByte(byte[] array, int offset, byte operand) {
            synchronized (lockFor(array)) {
                byte current = array[offset];
                array[offset] = (byte) (current | operand);
                return current;
            }
        }

        static int getAndBitwiseOrInt(byte[] array, int offset, int operand) {
            synchronized (lockFor(array)) {
                int current = readInt(array, offset);
                writeInt(array, offset, current | operand);
                return current;
            }
        }

        static long getAndBitwiseOrLong(byte[] array, int offset, long operand) {
            synchronized (lockFor(array)) {
                long current = readLong(array, offset);
                writeLong(array, offset, current | operand);
                return current;
            }
        }

        static byte getAndBitwiseXorByte(byte[] array, int offset, byte operand) {
            synchronized (lockFor(array)) {
                byte current = array[offset];
                array[offset] = (byte) (current ^ operand);
                return current;
            }
        }

        static int getAndBitwiseXorInt(byte[] array, int offset, int operand) {
            synchronized (lockFor(array)) {
                int current = readInt(array, offset);
                writeInt(array, offset, current ^ operand);
                return current;
            }
        }

        static long getAndBitwiseXorLong(byte[] array, int offset, long operand) {
            synchronized (lockFor(array)) {
                long current = readLong(array, offset);
                writeLong(array, offset, current ^ operand);
                return current;
            }
        }

        static byte getAndSetByte(byte[] array, int offset, byte value) {
            synchronized (lockFor(array)) {
                byte current = array[offset];
                array[offset] = value;
                return current;
            }
        }

        static int getAndSetInt(byte[] array, int offset, int value) {
            synchronized (lockFor(array)) {
                int current = readInt(array, offset);
                writeInt(array, offset, value);
                return current;
            }
        }

        static long getAndSetLong(byte[] array, int offset, long value) {
            synchronized (lockFor(array)) {
                long current = readLong(array, offset);
                writeLong(array, offset, value);
                return current;
            }
        }

        static byte getVolatileByte(byte[] array, int offset) {
            synchronized (lockFor(array)) {
                return array[offset];
            }
        }

        static int getVolatileInt(byte[] array, int offset) {
            synchronized (lockFor(array)) {
                return readInt(array, offset);
            }
        }

        static long getVolatileLong(byte[] array, int offset) {
            synchronized (lockFor(array)) {
                return readLong(array, offset);
            }
        }

        static short getVolatileShort(byte[] array, int offset) {
            synchronized (lockFor(array)) {
                return readShort(array, offset);
            }
        }

        static void setVolatileByte(byte[] array, int offset, byte value) {
            synchronized (lockFor(array)) {
                array[offset] = value;
            }
        }

        static void setVolatileInt(byte[] array, int offset, int value) {
            synchronized (lockFor(array)) {
                writeInt(array, offset, value);
            }
        }

        static void setVolatileLong(byte[] array, int offset, long value) {
            synchronized (lockFor(array)) {
                writeLong(array, offset, value);
            }
        }

        static void setVolatileShort(byte[] array, int offset, short value) {
            synchronized (lockFor(array)) {
                writeShort(array, offset, value);
            }
        }
    }
}
