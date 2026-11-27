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

package com.caoccao.qjs4j.builtins.buffer;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.*;
import com.caoccao.qjs4j.exceptions.*;
import com.caoccao.qjs4j.utils.ByteArrayAtomics;

import java.math.BigInteger;

/** Validates, coerces and accesses a single integer typed array element. */
enum AtomicOperation {
    ADD("add"),
    AND("and"),
    COMPARE_EXCHANGE("compareExchange", 4),
    EXCHANGE("exchange"),
    OR("or"),
    SUB("sub"),
    XOR("xor");

    private static final String INTEGER_ARRAY_REQUIREMENT = " only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array";
    private final int arity;
    private final String methodName;

    AtomicOperation(String name) {
        this(name, 3);
    }

    AtomicOperation(String name, int arity) {
        this.arity = arity;
        methodName = "Atomics." + name;
    }

    JSValue apply(JSContext context, JSValue[] args) {
        JSTypedArray typedArray = requireIntegerArray(context, args, arity, methodName);
        if (typedArray == null) {
            return context.getPendingException();
        }

        try {
            long index = getAtomicIndex(context, typedArray, args[1]);
            int width = typedArray.getBytesPerElement();
            long operand = toAtomicOperand(context, width, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            long replacement = 0;
            if (this == COMPARE_EXCHANGE) {
                replacement = toAtomicOperand(context, width, args[3]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
            // User code can detach or resize the buffer during either coercion.
            JSByteBuffer buffer = requireBuffer(typedArray, index);
            long address = address(typedArray, index);
            byte[] array = buffer.arrayAt(address);
            int offset = buffer.arrayOffsetAt(address);
            long previous = switch (width) {
                case 1 -> updateByte(array, offset, (byte) operand, (byte) replacement);
                case 2 -> updateShort(array, offset, (short) operand, (short) replacement);
                case 4 -> updateInt(array, offset, (int) operand, (int) replacement);
                case 8 -> updateLong(array, offset, operand, replacement);
                default -> throw new AssertionError(width);
            };
            return atomicResult(typedArray, previous);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    private byte updateByte(byte[] array, int offset, byte operand, byte replacement) {
        return switch (this) {
            case ADD -> ByteArrayAtomics.getAndAddByte(array, offset, operand);
            case SUB -> ByteArrayAtomics.getAndAddByte(array, offset, (byte) -operand);
            case AND -> ByteArrayAtomics.getAndBitwiseAndByte(array, offset, operand);
            case OR -> ByteArrayAtomics.getAndBitwiseOrByte(array, offset, operand);
            case XOR -> ByteArrayAtomics.getAndBitwiseXorByte(array, offset, operand);
            case EXCHANGE -> ByteArrayAtomics.getAndSetByte(array, offset, operand);
            case COMPARE_EXCHANGE -> ByteArrayAtomics.compareAndExchangeByte(array, offset, operand, replacement);
        };
    }

    private int updateInt(byte[] array, int offset, int operand, int replacement) {
        return switch (this) {
            case ADD -> ByteArrayAtomics.getAndAddInt(array, offset, operand);
            case SUB -> ByteArrayAtomics.getAndAddInt(array, offset, -operand);
            case AND -> ByteArrayAtomics.getAndBitwiseAndInt(array, offset, operand);
            case OR -> ByteArrayAtomics.getAndBitwiseOrInt(array, offset, operand);
            case XOR -> ByteArrayAtomics.getAndBitwiseXorInt(array, offset, operand);
            case EXCHANGE -> ByteArrayAtomics.getAndSetInt(array, offset, operand);
            case COMPARE_EXCHANGE -> ByteArrayAtomics.compareAndExchangeInt(array, offset, operand, replacement);
        };
    }

    private long updateLong(byte[] array, int offset, long operand, long replacement) {
        return switch (this) {
            case ADD -> ByteArrayAtomics.getAndAddLong(array, offset, operand);
            case SUB -> ByteArrayAtomics.getAndAddLong(array, offset, -operand);
            case AND -> ByteArrayAtomics.getAndBitwiseAndLong(array, offset, operand);
            case OR -> ByteArrayAtomics.getAndBitwiseOrLong(array, offset, operand);
            case XOR -> ByteArrayAtomics.getAndBitwiseXorLong(array, offset, operand);
            case EXCHANGE -> ByteArrayAtomics.getAndSetLong(array, offset, operand);
            case COMPARE_EXCHANGE -> ByteArrayAtomics.compareAndExchangeLong(array, offset, operand, replacement);
        };
    }

    private short updateShort(byte[] array, int offset, short operand, short replacement) {
        // Short views lack atomic read-modify-write operations. Storage pages are padded to
        // a multiple of four bytes so the CAS can preserve the neighboring halfword.
        int wordOffset = offset & ~3;
        int shift = (offset & 2) << 3;
        int mask = 0xFFFF << shift;
        while (true) {
            int word = ByteArrayAtomics.getVolatileInt(array, wordOffset);
            short previous = (short) (word >>> shift);
            if (this == COMPARE_EXCHANGE && previous != operand) {
                return previous;
            }
            int next = switch (this) {
                case ADD -> previous + operand;
                case SUB -> previous - operand;
                case AND -> previous & operand;
                case OR -> previous | operand;
                case XOR -> previous ^ operand;
                case EXCHANGE -> operand;
                case COMPARE_EXCHANGE -> replacement;
            };
            int updated = (word & ~mask) | ((next & 0xFFFF) << shift);
            if (ByteArrayAtomics.compareAndExchangeInt(array, wordOffset, word, updated) == word) {
                return previous;
            }
        }
    }

    private static long address(JSTypedArray typedArray, long index) {
        return typedArray.getByteOffset() + index * typedArray.getBytesPerElement();
    }

    private static JSValue atomicResult(JSTypedArray typedArray, long value) {
        if (typedArray instanceof JSBigUint64Array) {
            return JSBigInt.fromUnsignedLong(value);
        }
        if (typedArray instanceof JSBigInt64Array) {
            return new JSBigInt(BigInteger.valueOf(value));
        }
        if (typedArray instanceof JSUint32Array) {
            return JSNumber.of(Integer.toUnsignedLong((int) value));
        }
        if (typedArray instanceof JSUint16Array) {
            return JSNumber.of(Short.toUnsignedInt((short) value));
        }
        if (typedArray instanceof JSUint8Array) {
            return JSNumber.of(Byte.toUnsignedInt((byte) value));
        }
        return JSNumber.of((int) value);
    }

    static int byteOffset(JSTypedArray typedArray, long index) {
        return typedArray.getBuffer().getBuffer().arrayOffsetAt(address(typedArray, index));
    }

    static long getAtomicIndex(JSContext context, JSTypedArray typedArray, JSValue indexValue) {
        if (typedArray.isOutOfBounds()) {
            throw new JSTypeErrorException("TypedArray buffer is detached or out of bounds");
        }
        long typedArrayLength = typedArray.getLength();
        if (indexValue instanceof JSNumber number && number.value() >= 0 && number.value() < typedArrayLength
                && !context.hasPendingException()) {
            return (long) number.value();
        }
        long indexLong = JSTypeConversions.toIndex(context, indexValue);
        if (context.hasPendingException()) {
            throw new com.caoccao.qjs4j.exceptions.JSException(context.getPendingException());
        }
        if (indexLong >= typedArrayLength) {
            throw new JSRangeErrorException("Index out of bounds");
        }
        return indexLong;
    }

    static JSValue load(JSContext context, JSValue[] args) {
        JSTypedArray typedArray = requireIntegerArray(context, args, 2, "Atomics.load");
        if (typedArray == null) {
            return context.getPendingException();
        }

        try {
            long index = getAtomicIndex(context, typedArray, args[1]);
            JSByteBuffer buffer = requireBuffer(typedArray, index);
            long address = address(typedArray, index);
            byte[] array = buffer.arrayAt(address);
            int offset = buffer.arrayOffsetAt(address);
            long value = switch (typedArray.getBytesPerElement()) {
                case 1 -> ByteArrayAtomics.getVolatileByte(array, offset);
                case 2 -> ByteArrayAtomics.getVolatileShort(array, offset);
                case 4 -> ByteArrayAtomics.getVolatileInt(array, offset);
                case 8 -> ByteArrayAtomics.getVolatileLong(array, offset);
                default -> throw new AssertionError(typedArray.getBytesPerElement());
            };
            return atomicResult(typedArray, value);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    static byte[] requireAtomicArray(JSTypedArray typedArray, long index) {
        return requireBuffer(typedArray, index).arrayAt(address(typedArray, index));
    }

    private static JSByteBuffer requireBuffer(JSTypedArray typedArray, long index) {
        // Conversion of the index or value can resize or detach an ordinary ArrayBuffer.
        // An out-of-bounds or detached view has length zero.
        if (index >= typedArray.getLength()) {
            throw new JSTypeErrorException("TypedArray buffer is detached or out of bounds");
        }
        JSByteBuffer byteBuffer = typedArray.getBuffer().getBuffer();
        if (byteBuffer == null) {
            throw new JSTypeErrorException("TypedArray buffer is detached");
        }
        return byteBuffer;
    }

    private static JSTypedArray requireIntegerArray(JSContext context, JSValue[] args, int arity, String methodName) {
        if (args.length < arity) {
            String required = switch (arity) {
                case 2 -> " requires typedArray and index";
                case 4 -> " requires typedArray, index, expectedValue, and replacementValue";
                default -> " requires typedArray, index, and value";
            };
            context.throwTypeError(methodName + required);
        } else if (!(args[0] instanceof JSTypedArray typedArray)) {
            context.throwTypeError(methodName + " requires a TypedArray");
        } else if (!typedArray.isAtomicsReadableAndWriteable()) {
            context.throwTypeError(methodName + INTEGER_ARRAY_REQUIREMENT);
        } else {
            return typedArray;
        }
        return null;
    }

    static JSValue store(JSContext context, JSValue[] args) {
        JSTypedArray typedArray = requireIntegerArray(context, args, 3, "Atomics.store");
        if (typedArray == null) {
            return context.getPendingException();
        }

        try {
            long index = getAtomicIndex(context, typedArray, args[1]);
            if (typedArray instanceof JSBigInt64Array || typedArray instanceof JSBigUint64Array) {
                JSBigInt returnValue = JSTypeConversions.toBigInt(context, args[2]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                long storedValue = returnValue.value().longValue();
                JSByteBuffer buffer = requireBuffer(typedArray, index);
                long address = address(typedArray, index);
                byte[] arr = buffer.arrayAt(address);
                int byteOffset = buffer.arrayOffsetAt(address);
                ByteArrayAtomics.setVolatileLong(arr, byteOffset, storedValue);
                return returnValue;
            }
            double returnValue = JSTypeConversions.toInteger(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (returnValue == 0.0) {
                returnValue = 0.0;
            }
            int int32Value = JSTypeConversions.toInt32(context, JSNumber.of(returnValue));
            JSByteBuffer buffer = requireBuffer(typedArray, index);
            long address = address(typedArray, index);
            byte[] array = buffer.arrayAt(address);
            int offset = buffer.arrayOffsetAt(address);
            switch (typedArray.getBytesPerElement()) {
                case 1 -> ByteArrayAtomics.setVolatileByte(array, offset, (byte) int32Value);
                case 2 -> ByteArrayAtomics.setVolatileShort(array, offset, (short) int32Value);
                case 4 -> ByteArrayAtomics.setVolatileInt(array, offset, int32Value);
                default -> throw new AssertionError(typedArray.getBytesPerElement());
            }
            return JSNumber.of(returnValue);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    static long toAtomicOperand(JSContext context, int width, JSValue value) {
        // The only 8-byte integer typed arrays are BigInt64Array and BigUint64Array.
        return width == Long.BYTES
                ? JSTypeConversions.toBigInt64(context, value)
                : JSTypeConversions.toInt32(context, value);
    }
}
