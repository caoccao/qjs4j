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

import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;
import com.caoccao.qjs4j.utils.Float16;

import java.math.BigInteger;
import java.nio.ByteOrder;

/**
 * Represents a JavaScript DataView object. Based on ES2020 DataView specification.
 * <p>
 * DataView provides a low-level interface for reading and writing multiple number types in an ArrayBuffer.
 */
public final class JSDataView extends JSObject {
    public static final String NAME = "DataView";
    private final IJSArrayBuffer buffer;
    private final long byteLength;
    private final long byteOffset;
    private final boolean lengthTracking;

    /**
     * Create a DataView for the entire buffer.
     */
    public JSDataView(JSContext context, IJSArrayBuffer buffer) {
        this(context, buffer, 0, buffer.getByteLength());
    }

    /**
     * Create a DataView for a portion of the buffer.
     */
    public JSDataView(JSContext context, IJSArrayBuffer buffer, long byteOffset, long byteLength) {
        this(context, buffer, byteOffset, byteLength, false);
    }

    /**
     * Create a DataView with optional length-tracking semantics on resizable/growable buffers.
     */
    public JSDataView(JSContext context, IJSArrayBuffer buffer, long byteOffset, long byteLength,
            boolean lengthTracking) {
        super(context);
        if (buffer == null || buffer.isDetached()) {
            throw new JSTypeErrorException("Cannot create DataView on detached buffer");
        }
        if (byteOffset < 0 || byteOffset > buffer.getByteLength()) {
            throw new JSRangeErrorException("DataView byteOffset out of range");
        }
        if (byteLength < 0) {
            throw new JSRangeErrorException("DataView byteLength out of range");
        }
        // Subtract rather than add: a Java caller can pass a length near Long.MAX_VALUE that ToIndex never produces.
        if (!lengthTracking && byteLength > buffer.getByteLength() - byteOffset) {
            throw new JSRangeErrorException("DataView byteLength out of range");
        }

        this.buffer = buffer;
        this.byteOffset = byteOffset;
        this.byteLength = byteLength;
        this.lengthTracking = lengthTracking;
    }

    private void checkOffset(long offset, int size) {
        if (buffer.isDetached()) {
            throw new JSTypeErrorException("ArrayBuffer is detached");
        }
        if (isOutOfBounds()) {
            throw new JSTypeErrorException("DataView is out of bounds");
        }
        long effectiveByteLength = getByteLength();
        if (offset < 0 || offset > effectiveByteLength - size) {
            throw new JSRangeErrorException("DataView offset out of range");
        }
    }

    public JSBigInt getBigInt64(long byteOffset, boolean littleEndian) {
        return new JSBigInt(getInt64(byteOffset, littleEndian));
    }

    public JSBigInt getBigUint64(long byteOffset, boolean littleEndian) {
        return new JSBigInt(toUnsignedBigInteger(getInt64(byteOffset, littleEndian)));
    }

    public JSObject getBuffer() {
        return (JSObject) buffer;
    }

    public long getByteLength() {
        if (lengthTracking) {
            if (buffer.isDetached()) {
                return 0;
            }
            long currentByteLength = buffer.getByteLength();
            if (byteOffset > currentByteLength) {
                return 0;
            }
            return currentByteLength - byteOffset;
        }
        return byteLength;
    }

    public long getByteOffset() {
        return byteOffset;
    }

    // Float32 operations
    public float getFloat16(long byteOffset, boolean littleEndian) {
        int halfFloat = getUint16(byteOffset, littleEndian);
        return Float16.toFloat((short) halfFloat);
    }

    public float getFloat32(long byteOffset, boolean littleEndian) {
        return Float.intBitsToFloat(getInt32(byteOffset, littleEndian));
    }

    // Float64 operations
    public double getFloat64(long byteOffset, boolean littleEndian) {
        return Double.longBitsToDouble(getInt64(byteOffset, littleEndian));
    }

    // Int16 operations
    public short getInt16(long byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 2);
        JSByteBuffer buf = buffer.getBuffer();
        short bits = buf.getShort(this.byteOffset + byteOffset);
        return littleEndian == (buf.order() == ByteOrder.LITTLE_ENDIAN) ? bits : Short.reverseBytes(bits);
    }

    // Int32 operations
    public int getInt32(long byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        JSByteBuffer buf = buffer.getBuffer();
        int bits = buf.getInt(this.byteOffset + byteOffset);
        return littleEndian == (buf.order() == ByteOrder.LITTLE_ENDIAN) ? bits : Integer.reverseBytes(bits);
    }

    private long getInt64(long byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        JSByteBuffer buf = buffer.getBuffer();
        long bits = buf.getLong(this.byteOffset + byteOffset);
        return littleEndian == (buf.order() == ByteOrder.LITTLE_ENDIAN) ? bits : Long.reverseBytes(bits);
    }

    // Int8 operations
    public byte getInt8(long byteOffset) {
        checkOffset(byteOffset, 1);
        return buffer.getBuffer().get(this.byteOffset + byteOffset);
    }

    // Uint16 operations
    public int getUint16(long byteOffset, boolean littleEndian) {
        return getInt16(byteOffset, littleEndian) & 0xFFFF;
    }

    // Uint32 operations
    public long getUint32(long byteOffset, boolean littleEndian) {
        return getInt32(byteOffset, littleEndian) & 0xFFFFFFFFL;
    }

    // Uint8 operations
    public int getUint8(long byteOffset) {
        return getInt8(byteOffset) & 0xFF;
    }

    public boolean isImmutable() {
        return buffer.isImmutable();
    }

    public boolean isOutOfBounds() {
        if (buffer.isDetached()) {
            return true;
        }
        long currentByteLength = buffer.getByteLength();
        if (byteOffset > currentByteLength) {
            return true;
        }
        if (lengthTracking) {
            return false;
        }
        return byteLength > currentByteLength - byteOffset;
    }

    public void setBigInt64(long byteOffset, JSBigInt value, boolean littleEndian) {
        setInt64(byteOffset, value.value().longValue(), littleEndian);
    }

    public void setBigUint64(long byteOffset, JSBigInt value, boolean littleEndian) {
        setBigInt64(byteOffset, value, littleEndian);
    }

    public void setFloat16(long byteOffset, double value, boolean littleEndian) {
        setUint16(byteOffset, Float16.toHalf(value), littleEndian);
    }

    public void setFloat32(long byteOffset, float value, boolean littleEndian) {
        setInt32(byteOffset, Float.floatToRawIntBits(value), littleEndian);
    }

    public void setFloat64(long byteOffset, double value, boolean littleEndian) {
        setInt64(byteOffset, Double.doubleToRawLongBits(value), littleEndian);
    }

    public void setInt16(long byteOffset, short value, boolean littleEndian) {
        checkOffset(byteOffset, 2);
        JSByteBuffer buf = buffer.getBuffer();
        buf.putShort(this.byteOffset + byteOffset,
                littleEndian == (buf.order() == ByteOrder.LITTLE_ENDIAN) ? value : Short.reverseBytes(value));
    }

    public void setInt32(long byteOffset, int value, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        JSByteBuffer buf = buffer.getBuffer();
        buf.putInt(this.byteOffset + byteOffset,
                littleEndian == (buf.order() == ByteOrder.LITTLE_ENDIAN) ? value : Integer.reverseBytes(value));
    }

    private void setInt64(long byteOffset, long value, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        JSByteBuffer buf = buffer.getBuffer();
        buf.putLong(this.byteOffset + byteOffset,
                littleEndian == (buf.order() == ByteOrder.LITTLE_ENDIAN) ? value : Long.reverseBytes(value));
    }

    public void setInt8(long byteOffset, byte value) {
        checkOffset(byteOffset, 1);
        buffer.getBuffer().put(this.byteOffset + byteOffset, value);
    }

    public void setUint16(long byteOffset, int value, boolean littleEndian) {
        setInt16(byteOffset, (short) value, littleEndian);
    }

    public void setUint32(long byteOffset, long value, boolean littleEndian) {
        setInt32(byteOffset, (int) value, littleEndian);
    }

    public void setUint8(long byteOffset, int value) {
        setInt8(byteOffset, (byte) value);
    }

    @Override
    public String toString() {
        return "[object DataView]";
    }

    /**
     * Revalidate DataView bounds after constructor prototype resolution. QuickJS performs a second detached/range check
     * because user code may run while reading newTarget.prototype.
     */
    public boolean validateConstructorState(JSContext context) {
        if (buffer.isDetached()) {
            context.throwTypeError("ArrayBuffer is detached");
            return false;
        }
        long currentByteLength = buffer.getByteLength();
        if (byteOffset > currentByteLength) {
            context.throwRangeError("byteOffset out of range");
            return false;
        }
        if (!lengthTracking && byteLength > currentByteLength - byteOffset) {
            context.throwRangeError("byteOffset + byteLength out of range");
            return false;
        }
        return true;
    }

    /**
     * DataView constructor implementation. new DataView(buffer [, byteOffset [, byteLength]])
     * <p>
     * Based on ES2020 24.3.2.1
     */
    public static JSObject create(JSContext context, JSValue... args) {
        if (args.length == 0) {
            return context.throwTypeError("DataView constructor requires at least 1 argument");
        }

        // Get buffer argument
        JSValue bufferArg = args[0];
        if (!(bufferArg instanceof IJSArrayBuffer buffer)) {
            return context.throwTypeError("First argument to DataView constructor must be an ArrayBuffer");
        }

        // Get byteOffset (optional, default 0)
        long byteOffset = 0;
        if (args.length > 1) {
            Double convertedByteOffset = toIndex(context, args[1], "byteOffset");
            if (convertedByteOffset == null) {
                return getPendingExceptionAsObject(context);
            }
            byteOffset = convertedByteOffset.longValue();
        }

        if (buffer.isDetached()) {
            return context.throwTypeError("ArrayBuffer is detached");
        }
        if (byteOffset > buffer.getByteLength()) {
            return context.throwRangeError("byteOffset out of range");
        }

        // Get byteLength (optional, default to remaining buffer)
        long byteLength;
        boolean lengthTracking = false;
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            Double convertedByteLength = toIndex(context, args[2], "byteLength");
            if (convertedByteLength == null) {
                return getPendingExceptionAsObject(context);
            }
            byteLength = convertedByteLength.longValue();
            if (buffer.isDetached()) {
                return context.throwTypeError("ArrayBuffer is detached");
            }
            if (byteLength > buffer.getByteLength() - byteOffset) {
                return context.throwRangeError("byteOffset + byteLength out of range");
            }
        } else {
            byteLength = buffer.getByteLength() - byteOffset;
            lengthTracking = isLengthTrackingCandidate(buffer);
        }

        if (buffer.isDetached()) {
            return context.throwTypeError("ArrayBuffer is detached");
        }
        if (byteOffset > buffer.getByteLength()) {
            return context.throwRangeError("byteOffset + byteLength out of range");
        }
        if (!lengthTracking && byteLength > buffer.getByteLength() - byteOffset) {
            return context.throwRangeError("byteOffset + byteLength out of range");
        }

        JSDataView dataView = new JSDataView(context, buffer, byteOffset, byteLength, lengthTracking);
        context.transferPrototype(dataView, NAME);
        return dataView;
    }

    private static JSObject getPendingExceptionAsObject(JSContext context) {
        JSValue pendingException = context.getPendingException();
        if (pendingException instanceof JSObject jsObject) {
            return jsObject;
        }
        return context.throwError("Unknown pending exception");
    }

    private static boolean isLengthTrackingCandidate(IJSArrayBuffer buffer) {
        if (buffer instanceof JSArrayBuffer jsArrayBuffer) {
            return jsArrayBuffer.isResizable();
        }
        if (buffer instanceof JSSharedArrayBuffer jsSharedArrayBuffer) {
            return jsSharedArrayBuffer.isGrowable();
        }
        return false;
    }

    private static Double toIndex(JSContext context, JSValue value, String name) {
        double index = JSTypeConversions.toInteger(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        if (Double.isNaN(index) || index < 0 || Double.isInfinite(index)) {
            context.throwRangeError("Invalid " + name);
            return null;
        }
        if (index > JSArrayBuffer.MAX_DATA_BLOCK_BYTE_LENGTH) {
            context.throwRangeError(name + " out of range");
            return null;
        }
        return index;
    }

    private static BigInteger toUnsignedBigInteger(long value) {
        if (value >= 0) {
            return BigInteger.valueOf(value);
        }
        return BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63);
    }
}
