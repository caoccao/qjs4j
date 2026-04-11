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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
public final class AtomicsObject {
    // VarHandles for lock-free per-element atomic operations on byte[] backing arrays.
    private static final VarHandle BYTE_VH = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final VarHandle INT_VH = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG_VH = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle SHORT_VH = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    // Shared thread pool for Atomics.waitAsync() — reuses threads instead of creating one per call.
    // Cached pool: idle threads are terminated after 60s, new threads created on demand.
    private final ExecutorService waitAsyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "qjs4j-atomics-waitAsync");
        t.setDaemon(true);
        return t;
    });
    // Wait lists indexed by SharedArrayBuffer + index, scoped per runtime (agent cluster)
    private final Map<String, WaitList> waitLists = new ConcurrentHashMap<>();

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

    private static String getWaitKey(JSTypedArray typedArray, int index) {
        byte[] sharedBytes = requireAtomicArray(typedArray);
        int absoluteByteOffset = typedArray.getByteOffset() + (index * typedArray.getBytesPerElement());
        return System.identityHashCode(sharedBytes) + ":" + absoluteByteOffset;
    }

    private static byte[] requireAtomicArray(JSTypedArray typedArray) {
        ByteBuffer byteBuffer = typedArray.getBuffer().getBuffer();
        if (byteBuffer == null) {
            throw new JSTypeErrorException("TypedArray buffer is detached");
        }
        return byteBuffer.array();
    }

    // --- CAS-loop helpers for short (Int16/Uint16) atomics ---
    // byteArrayViewVarHandle(short[].class) only supports getVolatile/setVolatile.
    // RMW operations use a CAS loop on the enclosing aligned int word via INT_VH.
    // Backing arrays are padded to a multiple of 4 bytes to ensure the enclosing int
    // is always in bounds.

    private static short shortCompareAndExchange(byte[] arr, int byteOffset, short expected, short replacement) {
        int intOffset = byteOffset & ~3;
        int shift = (byteOffset & 2) << 3;
        int mask = 0xFFFF << shift;
        while (true) {
            int oldInt = (int) INT_VH.getVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            if (oldShort != expected) {
                return oldShort;
            }
            int newInt = (oldInt & ~mask) | ((replacement & 0xFFFF) << shift);
            if ((int) INT_VH.compareAndExchange(arr, intOffset, oldInt, newInt) == oldInt) {
                return oldShort;
            }
        }
    }

    private static short shortGetAndAdd(byte[] arr, int byteOffset, short delta) {
        int intOffset = byteOffset & ~3;
        int shift = (byteOffset & 2) << 3;
        int mask = 0xFFFF << shift;
        while (true) {
            int oldInt = (int) INT_VH.getVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            short newShort = (short) (oldShort + delta);
            int newInt = (oldInt & ~mask) | ((newShort & 0xFFFF) << shift);
            if ((int) INT_VH.compareAndExchange(arr, intOffset, oldInt, newInt) == oldInt) {
                return oldShort;
            }
        }
    }

    private static short shortGetAndBitwiseAnd(byte[] arr, int byteOffset, short operand) {
        int intOffset = byteOffset & ~3;
        int shift = (byteOffset & 2) << 3;
        int mask = 0xFFFF << shift;
        while (true) {
            int oldInt = (int) INT_VH.getVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            short newShort = (short) (oldShort & operand);
            int newInt = (oldInt & ~mask) | ((newShort & 0xFFFF) << shift);
            if ((int) INT_VH.compareAndExchange(arr, intOffset, oldInt, newInt) == oldInt) {
                return oldShort;
            }
        }
    }

    private static short shortGetAndBitwiseOr(byte[] arr, int byteOffset, short operand) {
        int intOffset = byteOffset & ~3;
        int shift = (byteOffset & 2) << 3;
        int mask = 0xFFFF << shift;
        while (true) {
            int oldInt = (int) INT_VH.getVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            short newShort = (short) (oldShort | operand);
            int newInt = (oldInt & ~mask) | ((newShort & 0xFFFF) << shift);
            if ((int) INT_VH.compareAndExchange(arr, intOffset, oldInt, newInt) == oldInt) {
                return oldShort;
            }
        }
    }

    private static short shortGetAndBitwiseXor(byte[] arr, int byteOffset, short operand) {
        int intOffset = byteOffset & ~3;
        int shift = (byteOffset & 2) << 3;
        int mask = 0xFFFF << shift;
        while (true) {
            int oldInt = (int) INT_VH.getVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            short newShort = (short) (oldShort ^ operand);
            int newInt = (oldInt & ~mask) | ((newShort & 0xFFFF) << shift);
            if ((int) INT_VH.compareAndExchange(arr, intOffset, oldInt, newInt) == oldInt) {
                return oldShort;
            }
        }
    }

    private static short shortGetAndSet(byte[] arr, int byteOffset, short newValue) {
        int intOffset = byteOffset & ~3;
        int shift = (byteOffset & 2) << 3;
        int mask = 0xFFFF << shift;
        while (true) {
            int oldInt = (int) INT_VH.getVolatile(arr, intOffset);
            short oldShort = (short) ((oldInt >>> shift) & 0xFFFF);
            int newInt = (oldInt & ~mask) | ((newValue & 0xFFFF) << shift);
            if ((int) INT_VH.compareAndExchange(arr, intOffset, oldInt, newInt) == oldInt) {
                return oldShort;
            }
        }
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
                byte oldValue = (byte) BYTE_VH.getAndAdd(arr, byteOffset, (byte) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue = (byte) BYTE_VH.getAndAdd(arr, byteOffset, (byte) value);
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
                int oldValue = (int) INT_VH.getAndAdd(arr, byteOffset, value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue = (int) INT_VH.getAndAdd(arr, byteOffset, value);
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndAdd(arr, byteOffset, value);
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndAdd(arr, byteOffset, value);
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
                byte oldValue = (byte) BYTE_VH.getAndBitwiseAnd(arr, byteOffset, (byte) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue = (byte) BYTE_VH.getAndBitwiseAnd(arr, byteOffset, (byte) value);
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
                int oldValue = (int) INT_VH.getAndBitwiseAnd(arr, byteOffset, value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue = (int) INT_VH.getAndBitwiseAnd(arr, byteOffset, value);
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndBitwiseAnd(arr, byteOffset, value);
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndBitwiseAnd(arr, byteOffset, value);
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.and invalid typed array");
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
                byte oldValue = (byte) BYTE_VH.compareAndExchange(arr, byteOffset, (byte) expectedValue, (byte) replacementValue);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue = (byte) BYTE_VH.compareAndExchange(arr, byteOffset, (byte) expectedValue, (byte) replacementValue);
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
                int oldValue = (int) INT_VH.compareAndExchange(arr, byteOffset, expectedValue, replacementValue);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue = (int) INT_VH.compareAndExchange(arr, byteOffset, expectedValue, replacementValue);
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                long replacementValue = JSTypeConversions.toBigInt64(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.compareAndExchange(arr, byteOffset, expectedValue, replacementValue);
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                long replacementValue = JSTypeConversions.toBigInt64(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.compareAndExchange(arr, byteOffset, expectedValue, replacementValue);
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
                byte oldValue = (byte) BYTE_VH.getAndSet(arr, byteOffset, (byte) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue = (byte) BYTE_VH.getAndSet(arr, byteOffset, (byte) value);
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
                int oldValue = (int) INT_VH.getAndSet(arr, byteOffset, value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue = (int) INT_VH.getAndSet(arr, byteOffset, value);
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndSet(arr, byteOffset, value);
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndSet(arr, byteOffset, value);
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.exchange invalid typed array");
    }

    /**
     * Atomics.isLockFree(size)
     * ES2017 24.4.2
     * Returns whether operations on a given size are lock-free.
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

        // In Java, operations on 1, 2, 4 bytes are typically lock-free on modern hardware
        // 8 bytes (long) is also lock-free with AtomicLong
        boolean lockFree = size == 1 || size == 2 || size == 4 || size == 8;
        return JSBoolean.valueOf(lockFree);
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
                return JSNumber.of((byte) BYTE_VH.getVolatile(arr, byteOffset));
            } else if (typedArray instanceof JSUint8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                return JSNumber.of(Byte.toUnsignedInt((byte) BYTE_VH.getVolatile(arr, byteOffset)));
            } else if (typedArray instanceof JSInt16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                return JSNumber.of((short) SHORT_VH.getVolatile(arr, byteOffset));
            } else if (typedArray instanceof JSUint16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                return JSNumber.of(Short.toUnsignedInt((short) SHORT_VH.getVolatile(arr, byteOffset)));
            } else if (typedArray instanceof JSInt32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                return JSNumber.of((int) INT_VH.getVolatile(arr, byteOffset));
            } else if (typedArray instanceof JSUint32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                return JSNumber.of(Integer.toUnsignedLong((int) INT_VH.getVolatile(arr, byteOffset)));
            } else if (typedArray instanceof JSBigInt64Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                return new JSBigInt(BigInteger.valueOf((long) LONG_VH.getVolatile(arr, byteOffset)));
            } else if (typedArray instanceof JSBigUint64Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                return createBigUint64((long) LONG_VH.getVolatile(arr, byteOffset));
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

            String waitKey = getWaitKey(typedArray, index);
            WaitList waitList = waitLists.get(waitKey);
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
                byte oldValue = (byte) BYTE_VH.getAndBitwiseOr(arr, byteOffset, (byte) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue = (byte) BYTE_VH.getAndBitwiseOr(arr, byteOffset, (byte) value);
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
                int oldValue = (int) INT_VH.getAndBitwiseOr(arr, byteOffset, value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue = (int) INT_VH.getAndBitwiseOr(arr, byteOffset, value);
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndBitwiseOr(arr, byteOffset, value);
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndBitwiseOr(arr, byteOffset, value);
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
                LONG_VH.setVolatile(arr, byteOffset, storedValue);
                return returnValue;
            }
            double returnValue = JSTypeConversions.toInteger(context, args[2]);
            if (returnValue == 0.0) {
                returnValue = 0.0;
            }
            int int32Value = JSTypeConversions.toInt32(context, JSNumber.of(returnValue));
            if (typedArray instanceof JSInt8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                byte storedValue = (byte) int32Value;
                BYTE_VH.setVolatile(arr, byteOffset, storedValue);
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                byte storedValue = (byte) int32Value;
                BYTE_VH.setVolatile(arr, byteOffset, storedValue);
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSInt16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short storedValue = (short) int32Value;
                SHORT_VH.setVolatile(arr, byteOffset, storedValue);
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short storedValue = (short) int32Value;
                SHORT_VH.setVolatile(arr, byteOffset, storedValue);
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSInt32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                INT_VH.setVolatile(arr, byteOffset, int32Value);
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                INT_VH.setVolatile(arr, byteOffset, int32Value);
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
                byte oldValue = (byte) BYTE_VH.getAndAdd(arr, byteOffset, (byte) -value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue = (byte) BYTE_VH.getAndAdd(arr, byteOffset, (byte) -value);
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
                int oldValue = (int) INT_VH.getAndAdd(arr, byteOffset, -value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue = (int) INT_VH.getAndAdd(arr, byteOffset, -value);
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndAdd(arr, byteOffset, -value);
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndAdd(arr, byteOffset, -value);
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
                int currentValue = (int) INT_VH.getVolatile(arr, byteOffset);
                if (currentValue != expectedValue) {
                    return new JSString("not-equal");
                }
            } else {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long currentValue = (long) LONG_VH.getVolatile(arr, byteOffset);
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

            String waitKey = getWaitKey(typedArray, index);
            WaitList waitList = waitLists.computeIfAbsent(waitKey, k -> new WaitList());
            long timeout = Double.isInfinite(timeoutDouble)
                    ? -1L
                    : Math.min((long) timeoutDouble, Long.MAX_VALUE);
            String result = waitList.await(timeout);
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
                int currentValue = (int) INT_VH.getVolatile(arr, byteOffset);
                if (currentValue != expectedValue) {
                    return createWaitAsyncSyncResult(context, "not-equal");
                }
            } else {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long currentValue = (long) LONG_VH.getVolatile(arr, byteOffset);
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

            String waitKey = getWaitKey(typedArray, index);
            WaitList waitList = waitLists.computeIfAbsent(waitKey, k -> new WaitList());
            JSPromise promise = context.createJSPromise();
            JSObject result = context.createJSObject();
            result.set(PropertyKey.ASYNC, JSBoolean.TRUE);
            result.set(PropertyKey.VALUE, promise);
            long timeoutMillis = Double.isInfinite(timeoutDouble)
                    ? -1L
                    : Math.min((long) timeoutDouble, Long.MAX_VALUE);
            waitList.registerWaiter();
            try {
                waitAsyncExecutor.execute(() -> {
                    try {
                        String waitResult = waitList.awaitRegisteredWaiter(timeoutMillis);
                        promise.fulfill(new JSString(waitResult));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        promise.fulfill(new JSString("timed-out"));
                    }
                });
            } catch (RejectedExecutionException e) {
                waitList.cancelRegisteredWaiter();
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
                byte oldValue = (byte) BYTE_VH.getAndBitwiseXor(arr, byteOffset, (byte) value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                byte oldValue = (byte) BYTE_VH.getAndBitwiseXor(arr, byteOffset, (byte) value);
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
                int oldValue = (int) INT_VH.getAndBitwiseXor(arr, byteOffset, value);
                return JSNumber.of(oldValue);
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int oldValue = (int) INT_VH.getAndBitwiseXor(arr, byteOffset, value);
                return JSNumber.of(Integer.toUnsignedLong(oldValue));
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndBitwiseXor(arr, byteOffset, value);
                return new JSBigInt(BigInteger.valueOf(oldValue));
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long oldValue = (long) LONG_VH.getAndBitwiseXor(arr, byteOffset, value);
                return createBigUint64(oldValue);
            }
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
        return context.throwTypeError("Atomics.xor invalid typed array");
    }

    /**
     * WaitList manages threads waiting on specific SharedArrayBuffer locations.
     * This is used by Atomics.wait() and Atomics.notify().
     */
    private static class WaitList {
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private int pendingSignals = 0;
        private int waitingCount = 0;

        public String await(long timeoutMs) throws InterruptedException {
            return await(timeoutMs, null);
        }

        public String await(long timeoutMs, CountDownLatch registrationLatch) throws InterruptedException {
            lock.lock();
            try {
                waitingCount++;
                if (registrationLatch != null) {
                    registrationLatch.countDown();
                }
                return awaitRegisteredWaiterInternal(timeoutMs);
            } finally {
                lock.unlock();
            }
        }

        public String awaitRegisteredWaiter(long timeoutMs) throws InterruptedException {
            lock.lock();
            try {
                return awaitRegisteredWaiterInternal(timeoutMs);
            } finally {
                lock.unlock();
            }
        }

        private String awaitRegisteredWaiterInternal(long timeoutMs) throws InterruptedException {
            try {
                if (timeoutMs < 0) {
                    while (pendingSignals == 0) {
                        condition.await();
                    }
                    pendingSignals--;
                    return "ok";
                }

                long remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                while (pendingSignals == 0) {
                    if (remainingNanos <= 0) {
                        return "timed-out";
                    }
                    remainingNanos = condition.awaitNanos(remainingNanos);
                }
                pendingSignals--;
                return "ok";
            } finally {
                waitingCount--;
                if (pendingSignals > waitingCount) {
                    pendingSignals = waitingCount;
                }
            }
        }

        public void cancelRegisteredWaiter() {
            lock.lock();
            try {
                if (waitingCount > 0) {
                    waitingCount--;
                    if (pendingSignals > waitingCount) {
                        pendingSignals = waitingCount;
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public int notifyWaiters(int count) {
            lock.lock();
            try {
                int availableToSignal = Math.max(waitingCount - pendingSignals, 0);
                int toNotify = Math.min(Math.max(count, 0), availableToSignal);
                pendingSignals += toNotify;
                for (int i = 0; i < toNotify; i++) {
                    condition.signal();
                }
                return toNotify;
            } finally {
                lock.unlock();
            }
        }

        public void registerWaiter() {
            lock.lock();
            try {
                waitingCount++;
            } finally {
                lock.unlock();
            }
        }
    }
}
