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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of Atomics object static methods.
 * Based on ES2017 Atomics specification.
 * <p>
 * The Atomics object provides atomic operations on SharedArrayBuffer and TypedArray views.
 * These operations guarantee atomic read-modify-write sequences and memory ordering.
 */
public final class AtomicsObject {

    // Global wait lists indexed by SharedArrayBuffer + index
    private static final Map<String, WaitList> waitLists = new ConcurrentHashMap<>();

    private static JSValue createBigUint64(long value) {
        BigInteger unsigned = value >= 0
                ? BigInteger.valueOf(value)
                : BigInteger.valueOf(value).add(BigInteger.ONE.shiftLeft(64));
        return new JSBigInt(unsigned);
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

    private static JSValue rethrowAsJSValue(JSContext context, JSErrorException errorException) {
        return switch (errorException.getErrorType()) {
            case RangeError -> context.throwRangeError(errorException.getMessage());
            case TypeError -> context.throwTypeError(errorException.getMessage());
            case SyntaxError -> context.throwSyntaxError(errorException.getMessage());
            default -> context.throwError(errorException.getMessage());
        };
    }

    private static ByteBuffer requireAtomicBuffer(JSTypedArray typedArray) {
        ByteBuffer byteBuffer = typedArray.getBuffer().getBuffer();
        if (byteBuffer == null) {
            throw new JSTypeErrorException("TypedArray buffer is detached");
        }
        return byteBuffer;
    }

    /**
     * Atomics.add(typedArray, index, value)
     * ES2017 24.4.3
     * Atomically adds value to the element at index and returns the old value.
     */
    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    byte oldValue = buffer.get(byteOffset);
                    buffer.put(byteOffset, (byte) (oldValue + value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    int oldValue = Byte.toUnsignedInt(buffer.get(byteOffset));
                    buffer.put(byteOffset, (byte) (oldValue + value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    short oldValue = buffer.getShort(byteOffset);
                    buffer.putShort(byteOffset, (short) (oldValue + value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    int oldValue = Short.toUnsignedInt(buffer.getShort(byteOffset));
                    buffer.putShort(byteOffset, (short) (oldValue + value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue + value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue + value);
                    return JSNumber.of(Integer.toUnsignedLong(oldValue));
                }
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue + value);
                    return new JSBigInt(BigInteger.valueOf(oldValue));
                }
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue + value);
                    return createBigUint64(oldValue);
                }
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
        return context.throwTypeError("Atomics.add invalid typed array");
    }

    /**
     * Atomics.and(typedArray, index, value)
     * ES2017 24.4.4
     * Atomically computes bitwise AND and returns the old value.
     */
    public static JSValue and(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    byte oldValue = buffer.get(byteOffset);
                    buffer.put(byteOffset, (byte) (oldValue & value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    int oldValue = Byte.toUnsignedInt(buffer.get(byteOffset));
                    buffer.put(byteOffset, (byte) (oldValue & value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    short oldValue = buffer.getShort(byteOffset);
                    buffer.putShort(byteOffset, (short) (oldValue & value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    int oldValue = Short.toUnsignedInt(buffer.getShort(byteOffset));
                    buffer.putShort(byteOffset, (short) (oldValue & value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue & value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue & value);
                    return JSNumber.of(Integer.toUnsignedLong(oldValue));
                }
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue & value);
                    return new JSBigInt(BigInteger.valueOf(oldValue));
                }
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue & value);
                    return createBigUint64(oldValue);
                }
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
        return context.throwTypeError("Atomics.and invalid typed array");
    }

    /**
     * Atomics.compareExchange(typedArray, index, expectedValue, replacementValue)
     * ES2017 24.4.5
     * Atomically compares and exchanges if equal, returns the old value.
     */
    public static JSValue compareExchange(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    byte oldValue = buffer.get(byteOffset);
                    if (oldValue == (byte) expectedValue) {
                        buffer.put(byteOffset, (byte) replacementValue);
                    }
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint8Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    int oldValue = Byte.toUnsignedInt(buffer.get(byteOffset));
                    if (oldValue == Byte.toUnsignedInt((byte) expectedValue)) {
                        buffer.put(byteOffset, (byte) replacementValue);
                    }
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt16Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    short oldValue = buffer.getShort(byteOffset);
                    if (oldValue == (short) expectedValue) {
                        buffer.putShort(byteOffset, (short) replacementValue);
                    }
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint16Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    int oldValue = Short.toUnsignedInt(buffer.getShort(byteOffset));
                    if (oldValue == Short.toUnsignedInt((short) expectedValue)) {
                        buffer.putShort(byteOffset, (short) replacementValue);
                    }
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    if (oldValue == expectedValue) {
                        buffer.putInt(byteOffset, replacementValue);
                    }
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int replacementValue = JSTypeConversions.toInt32(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    if (oldValue == expectedValue) {
                        buffer.putInt(byteOffset, replacementValue);
                    }
                    return JSNumber.of(Integer.toUnsignedLong(oldValue));
                }
            } else if (typedArray instanceof JSBigInt64Array) {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                long replacementValue = JSTypeConversions.toBigInt64(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    if (oldValue == expectedValue) {
                        buffer.putLong(byteOffset, replacementValue);
                    }
                    return new JSBigInt(BigInteger.valueOf(oldValue));
                }
            } else if (typedArray instanceof JSBigUint64Array) {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                long replacementValue = JSTypeConversions.toBigInt64(context, args[3]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    if (oldValue == expectedValue) {
                        buffer.putLong(byteOffset, replacementValue);
                    }
                    return createBigUint64(oldValue);
                }
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
        return context.throwTypeError("Atomics.compareExchange invalid typed array");
    }

    /**
     * Atomics.exchange(typedArray, index, value)
     * ES2017 24.4.6
     * Atomically exchanges the value at index and returns the old value.
     */
    public static JSValue exchange(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    byte oldValue = buffer.get(byteOffset);
                    buffer.put(byteOffset, (byte) value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    int oldValue = Byte.toUnsignedInt(buffer.get(byteOffset));
                    buffer.put(byteOffset, (byte) value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    short oldValue = buffer.getShort(byteOffset);
                    buffer.putShort(byteOffset, (short) value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    int oldValue = Short.toUnsignedInt(buffer.getShort(byteOffset));
                    buffer.putShort(byteOffset, (short) value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, value);
                    return JSNumber.of(Integer.toUnsignedLong(oldValue));
                }
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, value);
                    return new JSBigInt(BigInteger.valueOf(oldValue));
                }
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, value);
                    return createBigUint64(oldValue);
                }
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
        return context.throwTypeError("Atomics.exchange invalid typed array");
    }

    private static String getWaitKey(IJSArrayBuffer buffer, int index) {
        return System.identityHashCode(buffer) + ":" + index;
    }

    /**
     * Atomics.isLockFree(size)
     * ES2017 24.4.2
     * Returns whether operations on a given size are lock-free.
     */
    public static JSValue isLockFree(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        int size;
        try {
            size = (int) JSTypeConversions.toInteger(context, args[0]);
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
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
    public static JSValue load(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    return JSNumber.of(buffer.get(byteOffset));
                }
            } else if (typedArray instanceof JSUint8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    return JSNumber.of(Byte.toUnsignedInt(buffer.get(byteOffset)));
                }
            } else if (typedArray instanceof JSInt16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    return JSNumber.of(buffer.getShort(byteOffset));
                }
            } else if (typedArray instanceof JSUint16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    return JSNumber.of(Short.toUnsignedInt(buffer.getShort(byteOffset)));
                }
            } else if (typedArray instanceof JSInt32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    return JSNumber.of(buffer.getInt(byteOffset));
                }
            } else if (typedArray instanceof JSUint32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    return JSNumber.of(Integer.toUnsignedLong(buffer.getInt(byteOffset)));
                }
            } else if (typedArray instanceof JSBigInt64Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    return new JSBigInt(BigInteger.valueOf(buffer.getLong(byteOffset)));
                }
            } else if (typedArray instanceof JSBigUint64Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    return createBigUint64(buffer.getLong(byteOffset));
                }
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
        return context.throwTypeError("Atomics.load invalid typed array");
    }

    /**
     * Atomics.notify(typedArray, index, count)
     * ES2017 24.4.11
     * Notifies some agents that are sleeping in a wait on the given index.
     * Returns the number of agents that were awoken.
     */
    public static JSValue notify(JSContext context, JSValue thisArg, JSValue[] args) {
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

            String waitKey = getWaitKey(buffer, index);
            WaitList waitList = waitLists.get(waitKey);
            if (waitList == null) {
                return JSNumber.of(0);
            }

            int notified = waitList.notifyWaiters(count);
            return JSNumber.of(notified);
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
    }

    /**
     * Atomics.or(typedArray, index, value)
     * ES2017 24.4.8
     * Atomically computes bitwise OR and returns the old value.
     */
    public static JSValue or(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    byte oldValue = buffer.get(byteOffset);
                    buffer.put(byteOffset, (byte) (oldValue | value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    int oldValue = Byte.toUnsignedInt(buffer.get(byteOffset));
                    buffer.put(byteOffset, (byte) (oldValue | value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    short oldValue = buffer.getShort(byteOffset);
                    buffer.putShort(byteOffset, (short) (oldValue | value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    int oldValue = Short.toUnsignedInt(buffer.getShort(byteOffset));
                    buffer.putShort(byteOffset, (short) (oldValue | value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue | value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue | value);
                    return JSNumber.of(Integer.toUnsignedLong(oldValue));
                }
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue | value);
                    return new JSBigInt(BigInteger.valueOf(oldValue));
                }
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue | value);
                    return createBigUint64(oldValue);
                }
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
        return context.throwTypeError("Atomics.or invalid typed array");
    }

    /**
     * Atomics.pause()
     * ES2024 Proposal
     * Provides a hint to the runtime that it may be a good time to yield.
     * Useful in spin-wait loops.
     */
    public static JSValue pause(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public static JSValue store(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSBigInt64Array || typedArray instanceof JSBigUint64Array) {
                JSBigInt returnValue = JSTypeConversions.toBigInt(context, args[2]);
                long storedValue = returnValue.value().longValue();
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    buffer.putLong(byteOffset, storedValue);
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
                byte storedValue = (byte) int32Value;
                synchronized (buffer) {
                    buffer.put(byteOffset, storedValue);
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint8Array) {
                int byteOffset = typedArray.getByteOffset() + index;
                byte storedValue = (byte) int32Value;
                synchronized (buffer) {
                    buffer.put(byteOffset, storedValue);
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSInt16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short storedValue = (short) int32Value;
                synchronized (buffer) {
                    buffer.putShort(byteOffset, storedValue);
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint16Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                short storedValue = (short) int32Value;
                synchronized (buffer) {
                    buffer.putShort(byteOffset, storedValue);
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSInt32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    buffer.putInt(byteOffset, int32Value);
                }
                return JSNumber.of(returnValue);
            } else if (typedArray instanceof JSUint32Array) {
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    buffer.putInt(byteOffset, int32Value);
                }
                return JSNumber.of(returnValue);
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
        return context.throwTypeError("Atomics.store invalid typed array");
    }

    /**
     * Atomics.sub(typedArray, index, value)
     * ES2017 24.4.12
     * Atomically subtracts value from the element at index and returns the old value.
     */
    public static JSValue sub(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    byte oldValue = buffer.get(byteOffset);
                    buffer.put(byteOffset, (byte) (oldValue - value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    int oldValue = Byte.toUnsignedInt(buffer.get(byteOffset));
                    buffer.put(byteOffset, (byte) (oldValue - value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    short oldValue = buffer.getShort(byteOffset);
                    buffer.putShort(byteOffset, (short) (oldValue - value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    int oldValue = Short.toUnsignedInt(buffer.getShort(byteOffset));
                    buffer.putShort(byteOffset, (short) (oldValue - value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue - value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue - value);
                    return JSNumber.of(Integer.toUnsignedLong(oldValue));
                }
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue - value);
                    return new JSBigInt(BigInteger.valueOf(oldValue));
                }
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue - value);
                    return createBigUint64(oldValue);
                }
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
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
    public static JSValue wait(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer byteBuffer = requireAtomicBuffer(typedArray);
            double timeoutDouble;

            if (typedArray instanceof JSInt32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int currentValue;
                synchronized (byteBuffer) {
                    currentValue = byteBuffer.getInt(byteOffset);
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
                synchronized (byteBuffer) {
                    currentValue = byteBuffer.getLong(byteOffset);
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

            IJSArrayBuffer buffer = typedArray.getBuffer();
            String waitKey = getWaitKey(buffer, index);
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
            return rethrowAsJSValue(context, e);
        }
    }

    /**
     * Atomics.waitAsync(typedArray, index, value, timeout)
     * ES2024 Proposal
     * Async version of wait that returns a result object with async property.
     * Returns {async: false, value: "not-equal"} if value doesn't match,
     * or {async: true, value: Promise} if waiting.
     */
    public static JSValue waitAsync(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer byteBuffer = requireAtomicBuffer(typedArray);
            double timeoutDouble;

            if (typedArray instanceof JSInt32Array) {
                int expectedValue = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                int currentValue;
                synchronized (byteBuffer) {
                    currentValue = byteBuffer.getInt(byteOffset);
                }
                if (currentValue != expectedValue) {
                    return createWaitAsyncSyncResult(context, "not-equal");
                }
            } else {
                long expectedValue = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                long currentValue;
                synchronized (byteBuffer) {
                    currentValue = byteBuffer.getLong(byteOffset);
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

            IJSArrayBuffer buffer = typedArray.getBuffer();
            String waitKey = getWaitKey(buffer, index);
            WaitList waitList = waitLists.computeIfAbsent(waitKey, k -> new WaitList());
            JSPromise promise = context.createJSPromise();
            JSObject result = context.createJSObject();
            result.set(PropertyKey.ASYNC, JSBoolean.TRUE);
            result.set(PropertyKey.VALUE, promise);
            JSContext promiseContext = context;
            JSRuntime promiseRuntime = context.getRuntime();
            long timeoutMillis = Double.isInfinite(timeoutDouble)
                    ? -1L
                    : Math.min((long) timeoutDouble, Long.MAX_VALUE);
            CountDownLatch waiterRegisteredLatch = new CountDownLatch(1);
            Thread waitThread = new Thread(() -> {
                try {
                    String waitResult = waitList.await(timeoutMillis, waiterRegisteredLatch);
                    promise.fulfill(new JSString(waitResult));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    waiterRegisteredLatch.countDown();
                    promise.reject(new JSString("timed-out"));
                } finally {
                    synchronized (promiseRuntime) {
                        promiseRuntime.runJobs();
                        promiseContext.processMicrotasks();
                    }
                }
            }, "qjs4j-atomics-waitAsync");
            waitThread.setDaemon(true);
            waitThread.start();
            try {
                waiterRegisteredLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                promise.reject(new JSString("timed-out"));
            }
            return result;
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
        }
    }

    private static JSObject createWaitAsyncSyncResult(JSContext context, String value) {
        JSObject result = context.createJSObject();
        result.set(PropertyKey.ASYNC, JSBoolean.FALSE);
        result.set(PropertyKey.VALUE, new JSString(value));
        return result;
    }

    /**
     * Atomics.xor(typedArray, index, value)
     * ES2017 24.4.14
     * Atomically computes bitwise XOR and returns the old value.
     */
    public static JSValue xor(JSContext context, JSValue thisArg, JSValue[] args) {
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
            ByteBuffer buffer = requireAtomicBuffer(typedArray);
            if (typedArray instanceof JSInt8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    byte oldValue = buffer.get(byteOffset);
                    buffer.put(byteOffset, (byte) (oldValue ^ value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint8Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + index;
                synchronized (buffer) {
                    int oldValue = Byte.toUnsignedInt(buffer.get(byteOffset));
                    buffer.put(byteOffset, (byte) (oldValue ^ value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    short oldValue = buffer.getShort(byteOffset);
                    buffer.putShort(byteOffset, (short) (oldValue ^ value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint16Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Short.BYTES);
                synchronized (buffer) {
                    int oldValue = Short.toUnsignedInt(buffer.getShort(byteOffset));
                    buffer.putShort(byteOffset, (short) (oldValue ^ value));
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSInt32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue ^ value);
                    return JSNumber.of(oldValue);
                }
            } else if (typedArray instanceof JSUint32Array) {
                int value = JSTypeConversions.toInt32(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Integer.BYTES);
                synchronized (buffer) {
                    int oldValue = buffer.getInt(byteOffset);
                    buffer.putInt(byteOffset, oldValue ^ value);
                    return JSNumber.of(Integer.toUnsignedLong(oldValue));
                }
            } else if (typedArray instanceof JSBigInt64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue ^ value);
                    return new JSBigInt(BigInteger.valueOf(oldValue));
                }
            } else if (typedArray instanceof JSBigUint64Array) {
                long value = JSTypeConversions.toBigInt64(context, args[2]);
                int byteOffset = typedArray.getByteOffset() + (index * Long.BYTES);
                synchronized (buffer) {
                    long oldValue = buffer.getLong(byteOffset);
                    buffer.putLong(byteOffset, oldValue ^ value);
                    return createBigUint64(oldValue);
                }
            }
        } catch (JSErrorException e) {
            return rethrowAsJSValue(context, e);
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
                if (timeoutMs < 0) {
                    while (pendingSignals == 0) {
                        condition.await();
                    }
                    pendingSignals--;
                    waitingCount--;
                    return "ok";
                }

                long remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                while (pendingSignals == 0) {
                    if (remainingNanos <= 0) {
                        waitingCount--;
                        return "timed-out";
                    }
                    remainingNanos = condition.awaitNanos(remainingNanos);
                }
                pendingSignals--;
                waitingCount--;
                return "ok";
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
    }
}
