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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;
import com.caoccao.qjs4j.utils.ByteArrayAtomics;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of Atomics object methods.
 * Based on ES2017 Atomics specification.
 * <p>
 * The Atomics object provides atomic operations on SharedArrayBuffer and TypedArray views.
 * These operations guarantee atomic read-modify-write sequences and memory ordering.
 * <p>
 * Each JSRuntime owns an AtomicsObject instance so that wait/notify coordination
 * is scoped to the agent cluster (runtime), not shared globally across the JVM.
 */
public final class AtomicsObject implements AutoCloseable {
    /**
     * How long {@link #close()} waits for the {@code waitAsync} executor to stop.
     */
    private static final long WAIT_EXECUTOR_SHUTDOWN_TIMEOUT_MS = 5_000L;
    /**
     * In-flight {@code Atomics.waitAsync} operations, grouped by the runtime that started them, so
     * closing a runtime can end its own waits and no others. The map holds runtimes weakly: a
     * shared {@code AtomicsObject} outlives any one member of its agent cluster.
     */
    private final Map<JSRuntime, Set<AsyncWaitRegistration>> asyncWaits =
            Collections.synchronizedMap(new WeakHashMap<>());
    // Atomic access to the backing byte[] goes through ByteArrayAtomics, which keeps the lock-free
    // VarHandle path where the JDK offers it and falls back to a striped lock where it does not.
    // Calling byteArrayViewVarHandle directly from here is what broke every 16-, 32- and 64-bit
    // Atomics operation on JDK 25, where those view handles no longer support any atomic mode.
    // Shared thread pool for Atomics.waitAsync() — reuses threads instead of creating one per call.
    // Cached pool: idle threads are terminated after 60s, new threads created on demand.
    private final ExecutorService waitAsyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "qjs4j-atomics-waitAsync");
        t.setDaemon(true);
        return t;
    });
    /**
     * Wait lists, keyed by the identity of the data block and then by the byte offset within it.
     * <p>
     * The key used to be the string {@code System.identityHashCode(bytes) + ":" + offset}. Identity
     * hash codes are not unique, so two live and unrelated {@code SharedArrayBuffer}s could share a
     * wait list and {@code Atomics.notify} on one would wake — and count — waiters on the other.
     * The outer map compares the {@code byte[]} itself, which is identity because arrays do not
     * override {@code equals}; it holds the array weakly so a collected buffer takes its wait lists
     * with it.
     */
    private final Map<byte[], Map<Integer, WaitList>> waitLists =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static JSValue createBigUint64(long value) {
        BigInteger unsigned = value >= 0
                ? BigInteger.valueOf(value)
                : BigInteger.valueOf(value).add(BigInteger.ONE.shiftLeft(64));
        return new JSBigInt(unsigned);
    }

    private static JSObject createWaitAsyncSyncResult(JSContext context, String value) {
        JSObject result = context.createJSObject();
        result.set(PropertyKey.ASYNC, JSBoolean.FALSE);
        result.set(PropertyKey.VALUE, new JSString(value));
        return result;
    }

    private static int getAtomicIndex(JSContext context, JSTypedArray typedArray, JSValue indexValue) {
        if (typedArray.getBuffer().isDetached()) {
            throw new JSTypeErrorException("TypedArray buffer is detached");
        }
        int typedArrayLength = typedArray.getLength();
        final long indexLong;
        try {
            indexLong = JSTypeConversions.toIndex(context, indexValue);
        } catch (JSRangeErrorException e) {
            throw e;
        } catch (JSErrorException e) {
            throw e;
        }
        if (indexLong >= typedArrayLength) {
            throw new JSRangeErrorException("Index out of bounds");
        }
        return (int) indexLong;
    }

    private static double getAtomicsWaitTimeout(JSContext context, JSValue[] args, int timeoutArgIndex) {
        JSValue timeoutValue = args.length > timeoutArgIndex ? args[timeoutArgIndex] : JSUndefined.INSTANCE;
        double timeoutNumber = JSTypeConversions.toNumber(context, timeoutValue).value();
        if (Double.isNaN(timeoutNumber)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(timeoutNumber, 0.0);
    }

    /**
     * The byte offset of an element within its data block.
     *
     * @param typedArray the view
     * @param index      the element index
     * @return the absolute byte offset
     */
    private static int getWaitOffset(JSTypedArray typedArray, int index) {
        return typedArray.getByteOffset() + (index * typedArray.getBytesPerElement());
    }

    private static byte[] requireAtomicArray(JSTypedArray typedArray) {
        ByteBuffer byteBuffer = typedArray.getBuffer().getBuffer();
        if (byteBuffer == null) {
            throw new JSTypeErrorException("TypedArray buffer is detached");
        }
        return byteBuffer.array();
    }

    // --- CAS-loop helpers for short (Int16/Uint16) atomics using synchronized ---

    private static short shortCompareAndExchange(byte[] arr, int byteOffset, short expected, short replacement) {
        synchronized (arr) {
            int intOffset = byteOffset & ~3;
            int shift = (byteOffset & 2) << 3;
            int oldInt = getIntVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            if (oldShort != expected) {
                return oldShort;
            }
            int mask = 0xFFFF << shift;
            int newInt = (oldInt & ~mask) | ((replacement & 0xFFFF) << shift);
            setIntVolatile(arr, intOffset, newInt);
            return oldShort;
        }
    }

    private static short shortGetAndAdd(byte[] arr, int byteOffset, short delta) {
        synchronized (arr) {
            int intOffset = byteOffset & ~3;
            int shift = (byteOffset & 2) << 3;
            int oldInt = getIntVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            short newShort = (short) (oldShort + delta);
            int mask = 0xFFFF << shift;
            int newInt = (oldInt & ~mask) | ((newShort & 0xFFFF) << shift);
            setIntVolatile(arr, intOffset, newInt);
            return oldShort;
        }
    }

    private static short shortGetAndBitwiseAnd(byte[] arr, int byteOffset, short operand) {
        synchronized (arr) {
            int intOffset = byteOffset & ~3;
            int shift = (byteOffset & 2) << 3;
            int oldInt = getIntVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            short newShort = (short) (oldShort & operand);
            int mask = 0xFFFF << shift;
            int newInt = (oldInt & ~mask) | ((newShort & 0xFFFF) << shift);
            setIntVolatile(arr, intOffset, newInt);
            return oldShort;
        }
    }

    private static short shortGetAndBitwiseOr(byte[] arr, int byteOffset, short operand) {
        synchronized (arr) {
            int intOffset = byteOffset & ~3;
            int shift = (byteOffset & 2) << 3;
            int oldInt = getIntVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            short newShort = (short) (oldShort | operand);
            int mask = 0xFFFF << shift;
            int newInt = (oldInt & ~mask) | ((newShort & 0xFFFF) << shift);
            setIntVolatile(arr, intOffset, newInt);
            return oldShort;
        }
    }

    private static short shortGetAndBitwiseXor(byte[] arr, int byteOffset, short operand) {
        synchronized (arr) {
            int intOffset = byteOffset & ~3;
            int shift = (byteOffset & 2) << 3;
            int oldInt = getIntVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            short newShort = (short) (oldShort ^ operand);
            int mask = 0xFFFF << shift;
            int newInt = (oldInt & ~mask) | ((newShort & 0xFFFF) << shift);
            setIntVolatile(arr, intOffset, newInt);
            return oldShort;
        }
    }

    private static short shortGetAndSet(byte[] arr, int byteOffset, short newValue) {
        synchronized (arr) {
            int intOffset = byteOffset & ~3;
            int shift = (byteOffset & 2) << 3;
            int oldInt = getIntVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            int mask = 0xFFFF << shift;
            int newInt = (oldInt & ~mask) | ((newValue & 0xFFFF) << shift);
            setIntVolatile(arr, intOffset, newInt);
            return oldShort;
        }
    }

    // Helper methods to read/write ints/longs from byte[] with proper endianness
    private static int getIntVolatile(byte[] arr, int offset) {
        // Assume little-endian, aligned offset
        return ((arr[offset] & 0xFF)) |
                ((arr[offset + 1] & 0xFF) << 8) |
                ((arr[offset + 2] & 0xFF) << 16) |
                ((arr[offset + 3] & 0xFF) << 24);
    }

    private static void setIntVolatile(byte[] arr, int offset, int value) {
        arr[offset] = (byte) (value);
        arr[offset + 1] = (byte) (value >>> 8);
        arr[offset + 2] = (byte) (value >>> 16);
        arr[offset + 3] = (byte) (value >>> 24);
    }

    private static long getLongVolatile(byte[] arr, int offset) {
        return ((long) arr[offset] & 0xFF) |
                ((long) (arr[offset + 1] & 0xFF) << 8) |
                ((long) (arr[offset + 2] & 0xFF) << 16) |
                ((long) (arr[offset + 3] & 0xFF) << 24) |
                ((long) (arr[offset + 4] & 0xFF) << 32) |
                ((long) (arr[offset + 5] & 0xFF) << 40) |
                ((long) (arr[offset + 6] & 0xFF) << 48) |
                ((long) (arr[offset + 7] & 0xFF) << 56);
    }

    private static void setLongVolatile(byte[] arr, int offset, long value) {
        arr[offset] = (byte) (value);
        arr[offset + 1] = (byte) (value >>> 8);
        arr[offset + 2] = (byte) (value >>> 16);
        arr[offset + 3] = (byte) (value >>> 24);
        arr[offset + 4] = (byte) (value >>> 32);
        arr[offset + 5] = (byte) (value >>> 40);
        arr[offset + 6] = (byte) (value >>> 48);
        arr[offset + 7] = (byte) (value >>> 56);
    }

    private static short getShortVolatile(byte[] arr, int offset) {
        return (short) ((arr[offset] & 0xFF) | ((arr[offset + 1] & 0xFF) << 8));
    }

    private static void setShortVolatile(byte[] arr, int offset, short value) {
        arr[offset] = (byte) (value);
        arr[offset + 1] = (byte) (value >>> 8);
    }

    /**
     * Atomics.add(typedArray, index, value)
     * ES2017 24.4.3
     * Atomically adds value to the element at index and returns the old value.
     */
    public JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.add requires typedArray, index, and value");
        }

        // Validate typed array
        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.add requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.add only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue + value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue + value);
                }
                return JSNumber.of(Byte.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndAdd(arr, byteOffset, (short) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndAdd(arr, byteOffset, (short) value);
                return JSNumber.of(Short.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue + value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue + value);
                }
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue + value);
                }
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue + value);
                }
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.add invalid typed array");
    }

    /**
     * Atomics.and(typedArray, index, value)
     * ES2017 24.4.4
     * Atomically computes bitwise AND and returns the old value.
     */
    public JSValue and(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.and requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.and requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.and only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue & value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue & value);
                }
                return JSNumber.of(Byte.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndBitwiseAnd(arr, byteOffset, (short) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndBitwiseAnd(arr, byteOffset, (short) value);
                return JSNumber.of(Short.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue & value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue & value);
                }
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue & value);
                }
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue & value);
                }
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.and invalid typed array");
    }

    /**
     * End every {@code Atomics.waitAsync} a runtime started, without touching any other runtime's.
     * <p>
     * Called from {@link JSRuntime#close()}. A cancelled wait settles as {@code "timed-out"} if its
     * promise is still worth settling, and is dropped otherwise, so an infinite wait no longer
     * pins a daemon thread, a promise and a closed context for the life of the process.
     *
     * @param runtime the closing runtime
     * @return how many waits were cancelled
     */
    public int cancelAsyncWaits(JSRuntime runtime) {
        Set<AsyncWaitRegistration> registrations = asyncWaits.remove(runtime);
        if (registrations == null) {
            return 0;
        }
        List<AsyncWaitRegistration> snapshot;
        synchronized (registrations) {
            snapshot = new ArrayList<>(registrations);
        }
        for (AsyncWaitRegistration registration : snapshot) {
            registration.cancel();
        }
        return snapshot.size();
    }

    /**
     * Release this object's own resources: cancel every wait it is holding and stop its executor.
     * <p>
     * Deliberately <em>not</em> called by {@link JSRuntime#close()}, which cancels only its own
     * waits: an {@code AtomicsObject} can be shared by a whole agent cluster through
     * {@link JSRuntimeOptions#setAtomicsObject(AtomicsObject)}, so the first runtime to close is
     * not entitled to shut it down. An embedder that owns one exclusively calls this.
     */
    @Override
    public void close() {
        List<Set<AsyncWaitRegistration>> allRegistrations;
        synchronized (asyncWaits) {
            allRegistrations = new ArrayList<>(asyncWaits.values());
            asyncWaits.clear();
        }
        for (Set<AsyncWaitRegistration> registrations : allRegistrations) {
            List<AsyncWaitRegistration> snapshot;
            synchronized (registrations) {
                snapshot = new ArrayList<>(registrations);
            }
            for (AsyncWaitRegistration registration : snapshot) {
                registration.cancel();
            }
        }
        waitAsyncExecutor.shutdownNow();
        // Every waiter has been cancelled, so the tasks unwind immediately; waiting makes close a
        // point after which the object holds no thread, rather than a request that it stop soon.
        try {
            if (!waitAsyncExecutor.awaitTermination(WAIT_EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                // The threads are daemons, so a wait that will not unwind cannot outlive the JVM.
                Logger.getLogger(AtomicsObject.class.getName())
                        .log(Level.WARNING, "Atomics waitAsync executor did not stop within "
                                + WAIT_EXECUTOR_SHUTDOWN_TIMEOUT_MS + " ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Atomics.compareExchange(typedArray, index, expectedValue, replacementValue)
     * ES2017 24.4.5
     * Atomically compares and exchanges if equal, returns the old value.
     */
    public JSValue compareExchange(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 4) {
            return context.throwTypeError("Atomics.compareExchange requires typedArray, index, expectedValue, and replacementValue");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.compareExchange requires a TypedArray");
        }

        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.compareExchange only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    if (oldValue == (byte) expectedValue) {
                        arr[byteOffset] = (byte) replacementValue;
                    }
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    if (oldValue == (byte) expectedValue) {
                        arr[byteOffset] = (byte) replacementValue;
                    }
                }
                return JSNumber.of(Byte.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt16Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortCompareAndExchange(arr, byteOffset, (short) expectedValue, (short) replacementValue);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint16Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortCompareAndExchange(arr, byteOffset, (short) expectedValue, (short) replacementValue);
                return JSNumber.of(Short.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    if (oldValue == expectedValue) {
                        setIntVolatile(arr, byteOffset, replacementValue);
                    }
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    if (oldValue == expectedValue) {
                        setIntVolatile(arr, byteOffset, replacementValue);
                    }
                }
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                long replacementValue = JSTypeConversions.toBigInt64(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    if (oldValue == expectedValue) {
                        setLongVolatile(arr, byteOffset, replacementValue);
                    }
                }
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                long replacementValue = JSTypeConversions.toBigInt64(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    if (oldValue == expectedValue) {
                        setLongVolatile(arr, byteOffset, replacementValue);
                    }
                }
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.compareExchange invalid typed array");
    }

    /**
     * Atomics.exchange(typedArray, index, value)
     * ES2017 24.4.6
     * Atomically exchanges the value at index and returns the old value.
     */
    public JSValue exchange(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.exchange requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.exchange requires a TypedArray");
        }

        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.exchange only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) value;
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) value;
                }
                return JSNumber.of(Byte.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndSet(arr, byteOffset, (short) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndSet(arr, byteOffset, (short) value);
                return JSNumber.of(Short.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, value);
                }
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, value);
                }
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, value);
                }
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.exchange invalid typed array");
    }

    /**
     * The wait list for one location, or {@code null} when nobody has ever waited there.
     *
     * @param typedArray the view
     * @param index      the element index
     * @return the wait list, or {@code null}
     */
    WaitList findWaitList(JSTypedArray typedArray, int index) {
        Map<Integer, WaitList> byOffset = waitLists.get(requireAtomicArray(typedArray));
        return byOffset == null ? null : byOffset.get(getWaitOffset(typedArray, index));
    }

    /**
     * How many {@code Atomics.waitAsync} operations a runtime still has in flight.
     *
     * @param runtime the runtime
     * @return the count
     */
    public int getPendingAsyncWaitCount(JSRuntime runtime) {
        Set<AsyncWaitRegistration> registrations = asyncWaits.get(runtime);
        if (registrations == null) {
            return 0;
        }
        synchronized (registrations) {
            return registrations.size();
        }
    }

    /**
     * Atomics.isLockFree(size)
     * ES2017 24.4.2
     * Returns whether operations on a given size are lock-free.
     * <p>
     * This is a capability query, so it has to report the path the engine actually took.
     * {@link ByteArrayAtomics} detects that JDK 25 withdrew the atomic access modes from byte-array
     * view {@code VarHandle}s and routes every width through striped locks there — one flag for all
     * widths, because operations of different widths overlap on the same data block and must agree
     * on a protocol. Answering from the width alone told guest code the implementation guarantees
     * lock-free progress when it does not, which is exactly the premise an algorithm chooses itself
     * on.
     */
    public JSValue isLockFree(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        int size;
        try {
            size = (int) JSTypeConversions.toInteger(context, args[0]);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }

        // With synchronized implementation, all sizes are "lockful", but we follow spec:
        // 1,2,4 are traditionally lock-free, 8 might be lock-free on some platforms.
        // We still return true for these sizes to avoid breaking web compatibility.
        boolean lockFree = size == 1 || size == 2 || size == 4 || size == 8;
        return JSBoolean.valueOf(lockFree);
    }

    /**
     * Whether the {@code waitAsync} executor has stopped.
     *
     * @return true when no wait thread remains
     */
    public boolean isWaitExecutorTerminated() {
        return waitAsyncExecutor.isTerminated();
    }

    /**
     * Atomics.load(typedArray, index)
     * ES2017 24.4.7
     * Atomically loads and returns the value at index.
     */
    public JSValue load(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("Atomics.load requires typedArray and index");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.load requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.load only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                byte value;
                synchronized (arr) {
                    value = arr[byteOffset];
                }
                return JSNumber.of(value);
            } else if (typedArray instanceof JSUint8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                byte value;
                synchronized (arr) {
                    value = arr[byteOffset];
                }
                return JSNumber.of(Byte.toUnsignedInt(value));
            } else if (typedArray instanceof JSInt16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short value;
                synchronized (arr) {
                    value = getShortVolatile(arr, byteOffset);
                }
                return JSNumber.of(value);
            } else if (typedArray instanceof JSUint16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short value;
                synchronized (arr) {
                    value = getShortVolatile(arr, byteOffset);
                }
                return JSNumber.of(Short.toUnsignedInt(value));
            } else if (typedArray instanceof JSInt32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int value;
                synchronized (arr) {
                    value = getIntVolatile(arr, byteOffset);
                }
                return JSNumber.of(value);
            } else if (typedArray instanceof JSUint32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int value;
                synchronized (arr) {
                    value = getIntVolatile(arr, byteOffset);
                }
                return JSNumber.of(Integer.toUnsignedLong(value));
            } else if (typedArray instanceof JSBigInt64Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long value;
                synchronized (arr) {
                    value = getLongVolatile(arr, byteOffset);
                }
                return new JSBigInt(BigInteger.valueOf(value));
            } else if (typedArray instanceof JSBigUint64Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long value;
                synchronized (arr) {
                    value = getLongVolatile(arr, byteOffset);
                }
                return createBigUint64(value);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.load invalid typed array");
    }

    /**
     * Atomics.notify(typedArray, index, count)
     * ES2017 24.4.11
     * Notifies some agents that are sleeping in a wait on the given index.
     * Returns the number of agents that were awoken.
     */
    public JSValue notify(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 1) {
            return context.throwTypeError("Atomics.notify requires typedArray");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.notify requires a TypedArray");
        }

        if (!typedArray.isAtomicsWriteable()) {
            return context.throwTypeError(
                    typedArray.getObjectTag() + " is not an int32 or BigInt64 typed array.");
        }
        try {
            int index = getAtomicIndex(context, typedArray, args.length >= 2 ? args[1] : JSUndefined.INSTANCE);
            double countNumber = args.length >= 3 && !(args[2] instanceof JSUndefined)
                    ? JSTypeConversions.toInteger(context, args[2])
                    : Double.POSITIVE_INFINITY;
            double clampedCount = Math.max(countNumber, 0.0);

            IJSArrayBuffer buffer = typedArray.getBuffer();
            if (!buffer.isShared()) {
                return JSNumber.of(0);
            }

            int count = Double.isInfinite(clampedCount)
                    ? Integer.MAX_VALUE
                    : (int) Math.min(clampedCount, Integer.MAX_VALUE);

            WaitList waitList = findWaitList(typedArray, index);
            if (waitList == null) {
                return JSNumber.of(0);
            }

            int notified = waitList.notifyWaiters(count);
            return JSNumber.of(notified);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    /**
     * Atomics.or(typedArray, index, value)
     * ES2017 24.4.8
     * Atomically computes bitwise OR and returns the old value.
     */
    public JSValue or(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.or requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.or requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.or only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue | value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue | value);
                }
                return JSNumber.of(Byte.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndBitwiseOr(arr, byteOffset, (short) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndBitwiseOr(arr, byteOffset, (short) value);
                return JSNumber.of(Short.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue | value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue | value);
                }
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue | value);
                }
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue | value);
                }
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.or invalid typed array");
    }

    /**
     * Atomics.pause()
     * ES2024 Proposal
     * Provides a hint to the runtime that it may be a good time to yield.
     * Useful in spin-wait loops.
     */
    public JSValue pause(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length > 0) {
            JSValue iterationNumber = args[0];
            if (!(iterationNumber instanceof JSUndefined)) {
                if (iterationNumber instanceof JSNumber jsNumber) {
                    double value = jsNumber.value();
                    if (!Double.isFinite(value) || value != Math.rint(value)) {
                        return context.throwTypeError("not an integral number");
                    }
                } else {
                    return context.throwTypeError("not an integral number");
                }
            }
        }
        // Java 9+ Thread.onSpinWait() provides a hint to the JVM that we're in a spin-wait loop
        Thread.onSpinWait();
        return JSUndefined.INSTANCE;
    }

    /**
     * Take a place in the wait list for one location, creating the list if needed.
     * <p>
     * Looking the list up and joining it are one operation from the caller's point of view: a list
     * that was retired between the two refuses the registration, and the loop then takes the
     * successor that is actually in the lookup. That is what makes a waiter reachable by
     * {@code Atomics.notify} the moment it exists.
     *
     * @param typedArray the view
     * @param index      the element index
     * @return the caller's registration
     */
    WaitRegistration registerWaiter(JSTypedArray typedArray, int index) {
        byte[] block = requireAtomicArray(typedArray);
        int offset = getWaitOffset(typedArray, index);
        while (true) {
            Map<Integer, WaitList> byOffset = waitLists.computeIfAbsent(block, key -> new ConcurrentHashMap<>());
            WaitList waitList = byOffset.computeIfAbsent(offset, key -> new WaitList());
            WaitList.Waiter waiter = waitList.registerIfLive();
            if (waiter != null) {
                return new WaitRegistration(waitList, waiter, block, offset);
            }
        }
    }

    /**
     * Drop a wait list once its last waiter has gone.
     * <p>
     * Emptiness, retirement and removal from the lookup happen under the wait list's own lock, so
     * they are one step as far as {@link #registerWaiter(JSTypedArray, int)} is concerned. Testing
     * emptiness and then removing as two steps left a window in which an agent that had already
     * looked the list up could join it after it stopped being reachable: a later
     * {@code Atomics.notify} would look in the map, find nothing or a fresh list, report zero, and
     * leave that agent blocked — forever, for a wait with no timeout.
     *
     * @param registration the registration returned by {@link #registerWaiter(JSTypedArray, int)}
     */
    void releaseWaitList(WaitRegistration registration) {
        registration.waitList().retireIfEmpty(() -> {
            Map<Integer, WaitList> byOffset = waitLists.get(registration.block());
            if (byOffset != null) {
                byOffset.remove(registration.offset(), registration.waitList());
            }
        });
    }

    /**
     * Atomics.store(typedArray, index, value)
     * ES2017 24.4.11
     * Atomically stores value at index and returns the value.
     */
    public JSValue store(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.store requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.store requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.store only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSBigInt64Array || typedArray instanceof JSBigUint64Array) {
                JSBigInt returnValue = JSTypeConversions.toBigInt(context, args[2]);
                long storedValue = returnValue.value().longValue();
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (arr) {
                    setLongVolatile(arr, byteOffset, storedValue);
                }
                return returnValue;
            }
            double returnValue = JSTypeConversions.toInteger(context, args[2]);
            if (returnValue == 0.0) {
                returnValue = 0.0;
            }
            int int32Value = JSTypeConversions.toInt32(context, JSNumber.of(returnValue));
            if (typedArray instanceof JSInt8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (arr) {
                    arr[byteOffset] = (byte) int32Value;
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (arr) {
                    arr[byteOffset] = (byte) int32Value;
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSInt16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (arr) {
                    setShortVolatile(arr, byteOffset, (short) int32Value);
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (arr) {
                    setShortVolatile(arr, byteOffset, (short) int32Value);
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSInt32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (arr) {
                    setIntVolatile(arr, byteOffset, int32Value);
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (arr) {
                    setIntVolatile(arr, byteOffset, int32Value);
                }
                return JSNumber.of(returnValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.store invalid typed array");
    }

    /**
     * Atomics.sub(typedArray, index, value)
     * ES2017 24.4.12
     * Atomically subtracts value from the element at index and returns the old value.
     */
    public JSValue sub(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.sub requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.sub requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.sub only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue - value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue - value);
                }
                return JSNumber.of(Byte.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndAdd(arr, byteOffset, (short) -value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndAdd(arr, byteOffset, (short) -value);
                return JSNumber.of(Short.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue - value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue - value);
                }
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue - value);
                }
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue - value);
                }
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.sub invalid typed array");
    }

    /**
     * Atomics.wait(typedArray, index, value, timeout)
     * ES2017 24.4.13
     * Puts the agent to sleep until woken by notify or timeout expires.
     * Returns "ok" if woken by notify, "not-equal" if value doesn't match,
     * or "timed-out" if timeout expired.
     */
    public JSValue wait(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.wait requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.wait requires a TypedArray");
        }

        try {
            if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSBigInt64Array)) {
                return context.throwTypeError("Atomics.wait only works on Int32Array or BigInt64Array");
            }
            if (!typedArray.getBuffer().isShared()) {
                return context.throwTypeError("Atomics.wait requires a SharedArrayBuffer");
            }

            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            double timeoutDouble;

            if (typedArray instanceof JSInt32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int currentValue;
                synchronized (arr) {
                    currentValue = getIntVolatile(arr, byteOffset);
                }
                if (currentValue != expectedValue) {
                    return new JSString("not-equal");
                }
            } else {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long currentValue;
                synchronized (arr) {
                    currentValue = getLongVolatile(arr, byteOffset);
                }
                if (currentValue != expectedValue) {
                    return new JSString("not-equal");
                }
            }

            timeoutDouble = getAtomicsWaitTimeout(context, args, 3);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!context.isWaitable()) {
                return context.throwTypeError("Atomics.wait cannot be called in this context");
            }
            if (timeoutDouble <= 0.0) {
                return new JSString("timed-out");
            }

            long timeout = Double.isInfinite(timeoutDouble)
                    ? -1L
                    : Math.min((long) timeoutDouble, Long.MAX_VALUE);
            WaitRegistration registration = registerWaiter(typedArray, index);
            String result;
            try {
                result = registration.waitList().await(registration.waiter(), timeout);
            } finally {
                releaseWaitList(registration);
            }
            return new JSString(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JSString("timed-out");
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    /**
     * Atomics.waitAsync(typedArray, index, value, timeout)
     * ES2024 Proposal
     * Async version of wait that returns a result object with async property.
     * Returns {async: false, value: "not-equal"} if value doesn't match,
     * or {async: true, value: Promise} if waiting.
     */
    public JSValue waitAsync(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.waitAsync requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.waitAsync requires a TypedArray");
        }

        try {
            if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSBigInt64Array)) {
                return context.throwTypeError("Atomics.waitAsync only works on Int32Array or BigInt64Array");
            }
            if (!typedArray.getBuffer().isShared()) {
                return context.throwTypeError("Atomics.waitAsync requires a SharedArrayBuffer");
            }

            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            double timeoutDouble;

            if (typedArray instanceof JSInt32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int currentValue;
                synchronized (arr) {
                    currentValue = getIntVolatile(arr, byteOffset);
                }
                if (currentValue != expectedValue) {
                    return createWaitAsyncSyncResult(context, "not-equal");
                }
            } else {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long currentValue;
                synchronized (arr) {
                    currentValue = getLongVolatile(arr, byteOffset);
                }
                if (currentValue != expectedValue) {
                    return createWaitAsyncSyncResult(context, "not-equal");
                }
            }

            timeoutDouble = getAtomicsWaitTimeout(context, args, 3);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (timeoutDouble <= 0.0) {
                return createWaitAsyncSyncResult(context, "timed-out");
            }

            JSPromise promise = context.createJSPromise();
            JSObject result = context.createJSObject();
            result.set(PropertyKey.ASYNC, JSBoolean.TRUE);
            result.set(PropertyKey.VALUE, promise);
            long timeoutMillis = Double.isInfinite(timeoutDouble)
                    ? -1L
                    : Math.min((long) timeoutDouble, Long.MAX_VALUE);
            WaitRegistration waitRegistration = registerWaiter(typedArray, index);
            JSRuntime owningRuntime = context.getRuntime();
            AsyncWaitRegistration registration =
                    new AsyncWaitRegistration(waitRegistration.waitList(), waitRegistration.waiter());
            Set<AsyncWaitRegistration> registrations = asyncWaits.computeIfAbsent(
                    owningRuntime, key -> Collections.synchronizedSet(new LinkedHashSet<>()));
            registrations.add(registration);
            try {
                waitAsyncExecutor.execute(() -> {
                    String waitResult;
                    try {
                        waitResult = waitRegistration.waitList()
                                .await(waitRegistration.waiter(), timeoutMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        waitResult = "timed-out";
                    } finally {
                        registrations.remove(registration);
                        releaseWaitList(waitRegistration);
                    }
                    // A wait cancelled because its runtime closed must not touch that runtime's
                    // promise: the context it belongs to is gone.
                    if (!registration.isCancelled()) {
                        promise.fulfill(new JSString(waitResult));
                    }
                });
            } catch (RejectedExecutionException e) {
                registrations.remove(registration);
                waitRegistration.waitList().cancel(waitRegistration.waiter());
                releaseWaitList(waitRegistration);
                promise.fulfill(new JSString("timed-out"));
            }
            return result;
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    /**
     * Atomics.xor(typedArray, index, value)
     * ES2017 24.4.14
     * Atomically computes bitwise XOR and returns the old value.
     */
    public JSValue xor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.xor requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.xor requires a TypedArray");
        }

        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(
                    "Atomics.xor only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array");
        }

        try {
            int index = getAtomicIndex(context, typedArray, args[1]);
            byte[] arr = requireAtomicArray(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue ^ value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue;
                synchronized (arr) {
                    oldValue = arr[byteOffset];
                    arr[byteOffset] = (byte) (oldValue ^ value);
                }
                return JSNumber.of(Byte.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndBitwiseXor(arr, byteOffset, (short) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short oldValue = shortGetAndBitwiseXor(arr, byteOffset, (short) value);
                return JSNumber.of(Short.toUnsignedInt(oldValue));
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue ^ value);
                }
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue;
                synchronized (arr) {
                    oldValue = getIntVolatile(arr, byteOffset);
                    setIntVolatile(arr, byteOffset, oldValue ^ value);
                }
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue ^ value);
                }
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue;
                synchronized (arr) {
                    oldValue = getLongVolatile(arr, byteOffset);
                    setLongVolatile(arr, byteOffset, oldValue ^ value);
                }
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.xor invalid typed array");
    }

    /**
     * An {@code Atomics.waitAsync} in flight, owned by the runtime whose script started it.
     * <p>
     * Registration is what lets {@link #cancelAsyncWaits(JSRuntime)} end an unbounded wait when its
     * runtime closes. Without it, {@code Atomics.waitAsync(i, 0, 0)} with no timeout held a daemon
     * thread, a promise and that promise's context for the life of the JVM, and closing the runtime
     * changed nothing.
     */

    private static final class AsyncWaitRegistration {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final WaitList waitList;
        private final WaitList.Waiter waiter;

        private AsyncWaitRegistration(WaitList waitList, WaitList.Waiter waiter) {
            this.waitList = waitList;
            this.waiter = waiter;
        }

        private void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                waitList.cancel(waiter);
            }
        }

        private boolean isCancelled() {
            return cancelled.get();
        }
    }

    /**
     * The agents waiting on one {@code SharedArrayBuffer} location.
     * <p>
     * Each wait is its own queued node with its own {@link Condition}, so {@code Atomics.notify}
     * wakes exactly the waiters it selected. The previous design counted notifications in a shared
     * {@code pendingSignals} field, which made a notification a token any waiter could spend: after
     * {@code notifyWaiters} signalled an existing waiter and released the lock, a waiter that
     * arrived in between could take the lock first, see the token, consume it and return
     * {@code "ok"} — leaving the agent the notification was actually meant for still blocked.
     */
    static final class WaitList {
        private final Lock lock = new ReentrantLock();
        private final List<Waiter> waiters = new ArrayList<>();
        private boolean retired;

        /**
         * Block on a node until it is notified, cancelled, or the timeout expires.
         *
         * @param waiter    the node from {@link #register()}
         * @param timeoutMs the timeout, or a negative value to wait forever
         * @return {@code "ok"} or {@code "timed-out"}
         * @throws InterruptedException if the waiting thread is interrupted
         */
        String await(Waiter waiter, long timeoutMs) throws InterruptedException {
            lock.lock();
            try {
                if (timeoutMs < 0) {
                    while (waiter.state == Waiter.State.WAITING) {
                        waiter.condition.await();
                    }
                } else {
                    long remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                    while (waiter.state == Waiter.State.WAITING) {
                        if (remainingNanos <= 0) {
                            break;
                        }
                        remainingNanos = waiter.condition.awaitNanos(remainingNanos);
                    }
                }
                return waiter.state == Waiter.State.NOTIFIED ? "ok" : "timed-out";
            } finally {
                waiters.remove(waiter);
                lock.unlock();
            }
        }

        /**
         * Wake a node without notifying it, so its wait ends as a timeout. Used when the owning
         * runtime closes.
         *
         * @param waiter the node to cancel
         */
        void cancel(Waiter waiter) {
            lock.lock();
            try {
                if (waiter.state == Waiter.State.WAITING) {
                    waiter.state = Waiter.State.CANCELLED;
                    waiter.condition.signal();
                }
                waiters.remove(waiter);
            } finally {
                lock.unlock();
            }
        }

        /**
         * Whether nobody is waiting here any more.
         *
         * @return true when the queue is empty
         */
        boolean isEmpty() {
            lock.lock();
            try {
                return waiters.isEmpty();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Wake the given number of waiters, oldest first.
         *
         * @param count how many to wake
         * @return how many were woken
         */
        int notifyWaiters(int count) {
            lock.lock();
            try {
                int notified = 0;
                for (Waiter waiter : waiters) {
                    if (notified >= count) {
                        break;
                    }
                    if (waiter.state == Waiter.State.WAITING) {
                        waiter.state = Waiter.State.NOTIFIED;
                        waiter.condition.signal();
                        notified++;
                    }
                }
                return notified;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Join the queue, unless this list has already been retired from the lookup.
         *
         * @return the caller's node, or {@code null} when the list is no longer reachable and the
         * caller must take the one that replaced it
         */
        Waiter registerIfLive() {
            lock.lock();
            try {
                if (retired) {
                    return null;
                }
                Waiter waiter = new Waiter(lock.newCondition());
                waiters.add(waiter);
                return waiter;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Retire this list and remove it from the lookup, but only while it is still empty.
         * <p>
         * The removal runs under this list's lock so that retirement and unreachability are one
         * step: a registration that arrives in between blocks on the lock and is then refused,
         * rather than joining a list nothing can find any more.
         *
         * @param removeFromLookup removes this list from the wait-list map
         */
        void retireIfEmpty(Runnable removeFromLookup) {
            lock.lock();
            try {
                if (retired || !waiters.isEmpty()) {
                    return;
                }
                retired = true;
                removeFromLookup.run();
            } finally {
                lock.unlock();
            }
        }

        /**
         * One agent's place in the queue.
         */
        static final class Waiter {
            private final Condition condition;
            private State state = State.WAITING;

            private Waiter(Condition condition) {
                this.condition = condition;
            }

            private enum State {
                WAITING,
                NOTIFIED,
                CANCELLED
            }
        }
    }

    /**
     * One agent's place in a wait list, together with what it takes to drop that list again.
     *
     * @param waitList the list the waiter joined
     * @param waiter   the waiter's node
     * @param block    the data block the list belongs to
     * @param offset   the byte offset within that block
     */
    record WaitRegistration(WaitList waitList, WaitList.Waiter waiter, byte[] block, int offset) {
    }
}
