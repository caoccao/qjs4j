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
import com.caoccao.qjs4j.utils.Float16;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents a JavaScript DataView object.
 * Based on ES2020 DataView specification.
 * <p>
 * DataView provides a low-level interface for reading and writing
 * multiple number types in an ArrayBuffer.
 */
public final class JSDataView extends JSObject {
    public static final String NAME = "DataView";
    private final IJSArrayBuffer buffer;
    private final int byteLength;
    private final int byteOffset;
    private final boolean lengthTracking;

    /**
     * Create a DataView for the entire buffer.
     */
    public JSDataView(IJSArrayBuffer buffer) {
        this(buffer, 0, buffer.getByteLength());
    }

    /**
     * Create a DataView for a portion of the buffer.
     */
    public JSDataView(IJSArrayBuffer buffer, int byteOffset, int byteLength) {
        this(buffer, byteOffset, byteLength, false);
    }

    /**
     * Create a DataView with optional length-tracking semantics on resizable/growable buffers.
     */
    public JSDataView(IJSArrayBuffer buffer, int byteOffset, int byteLength, boolean lengthTracking) {
        super();
        if (buffer == null || buffer.isDetached()) {
            throw new IllegalArgumentException("Cannot create DataView on detached buffer");
        }
        if (byteOffset < 0 || byteOffset > buffer.getByteLength()) {
            throw new JSRangeErrorException("DataView byteOffset out of range");
        }
        if (byteLength < 0) {
            throw new JSRangeErrorException("DataView byteLength out of range");
        }
        if (!lengthTracking && (long) byteOffset + byteLength > buffer.getByteLength()) {
            throw new JSRangeErrorException("DataView byteLength out of range");
        }

        this.buffer = buffer;
        this.byteOffset = byteOffset;
        this.byteLength = byteLength;
        this.lengthTracking = lengthTracking;
    }

    /**
     * DataView constructor implementation.
     * new DataView(buffer [, byteOffset [, byteLength]])
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
        int byteOffset = 0;
        if (args.length > 1) {
            Double convertedByteOffset = toIndex(context, args[1], "byteOffset");
            if (convertedByteOffset == null) {
                return getPendingExceptionAsObject(context);
            }
            byteOffset = convertedByteOffset.intValue();
        }

        if (buffer.isDetached()) {
            return context.throwTypeError("ArrayBuffer is detached");
        }
        if (byteOffset > buffer.getByteLength()) {
            return context.throwRangeError("byteOffset out of range");
        }

        // Get byteLength (optional, default to remaining buffer)
        int byteLength;
        boolean lengthTracking = false;
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            Double convertedByteLength = toIndex(context, args[2], "byteLength");
            if (convertedByteLength == null) {
                return getPendingExceptionAsObject(context);
            }
            byteLength = convertedByteLength.intValue();
            if ((long) byteOffset + byteLength > buffer.getByteLength()) {
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
        if (!lengthTracking && (long) byteOffset + byteLength > buffer.getByteLength()) {
            return context.throwRangeError("byteOffset + byteLength out of range");
        }

        JSDataView dataView = new JSDataView(buffer, byteOffset, byteLength, lengthTracking);
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
        if (index > Integer.MAX_VALUE) {
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

    private void checkOffset(int offset, int size) {
        if (buffer.isDetached()) {
            throw new IllegalStateException("ArrayBuffer is detached");
        }
        if (isOutOfBounds()) {
            throw new IllegalStateException("DataView is out of bounds");
        }
        int effectiveByteLength = getByteLength();
        if (offset < 0 || (long) offset + size > effectiveByteLength) {
            throw new JSRangeErrorException("DataView offset out of range");
        }
    }

    public JSBigInt getBigInt64(int byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        long value = buf.getLong(this.byteOffset + byteOffset);
        buf.order(originalOrder);
        return new JSBigInt(value);
    }

    public JSBigInt getBigUint64(int byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        long value = buf.getLong(this.byteOffset + byteOffset);
        buf.order(originalOrder);
        return new JSBigInt(toUnsignedBigInteger(value));
    }

    public JSObject getBuffer() {
        return (JSObject) buffer;
    }

    public int getByteLength() {
        if (lengthTracking) {
            if (buffer.isDetached()) {
                return 0;
            }
            int currentByteLength = buffer.getByteLength();
            if (byteOffset > currentByteLength) {
                return 0;
            }
            return currentByteLength - byteOffset;
        }
        return byteLength;
    }

    public int getByteOffset() {
        return byteOffset;
    }

    // Float32 operations
    public float getFloat16(int byteOffset, boolean littleEndian) {
        int halfFloat = getUint16(byteOffset, littleEndian);
        return Float16.toFloat((short) halfFloat);
    }

    public float getFloat32(int byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        float value = buf.getFloat(this.byteOffset + byteOffset);
        buf.order(originalOrder);
        return value;
    }

    // Float64 operations
    public double getFloat64(int byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        double value = buf.getDouble(this.byteOffset + byteOffset);
        buf.order(originalOrder);
        return value;
    }

    // Int16 operations
    public short getInt16(int byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 2);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        short value = buf.getShort(this.byteOffset + byteOffset);
        buf.order(originalOrder);
        return value;
    }

    // Int32 operations
    public int getInt32(int byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int value = buf.getInt(this.byteOffset + byteOffset);
        buf.order(originalOrder);
        return value;
    }

    // Int8 operations
    public byte getInt8(int byteOffset) {
        checkOffset(byteOffset, 1);
        return buffer.getBuffer().get(this.byteOffset + byteOffset);
    }

    // Uint16 operations
    public int getUint16(int byteOffset, boolean littleEndian) {
        return getInt16(byteOffset, littleEndian) & 0xFFFF;
    }

    // Uint32 operations
    public long getUint32(int byteOffset, boolean littleEndian) {
        return getInt32(byteOffset, littleEndian) & 0xFFFFFFFFL;
    }

    // Uint8 operations
    public int getUint8(int byteOffset) {
        return getInt8(byteOffset) & 0xFF;
    }

    public boolean isOutOfBounds() {
        if (buffer.isDetached()) {
            return true;
        }
        int currentByteLength = buffer.getByteLength();
        if (byteOffset > currentByteLength) {
            return true;
        }
        if (lengthTracking) {
            return false;
        }
        return (long) byteOffset + byteLength > currentByteLength;
    }

    public void setBigInt64(int byteOffset, JSBigInt value, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putLong(this.byteOffset + byteOffset, value.value().longValue());
        buf.order(originalOrder);
    }

    public void setBigUint64(int byteOffset, JSBigInt value, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putLong(this.byteOffset + byteOffset, value.value().longValue());
        buf.order(originalOrder);
    }

    public void setFloat16(int byteOffset, float value, boolean littleEndian) {
        setUint16(byteOffset, Float16.toHalf(value), littleEndian);
    }

    public void setFloat32(int byteOffset, float value, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putFloat(this.byteOffset + byteOffset, value);
        buf.order(originalOrder);
    }

    public void setFloat64(int byteOffset, double value, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putDouble(this.byteOffset + byteOffset, value);
        buf.order(originalOrder);
    }

    public void setInt16(int byteOffset, short value, boolean littleEndian) {
        checkOffset(byteOffset, 2);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putShort(this.byteOffset + byteOffset, value);
        buf.order(originalOrder);
    }

    public void setInt32(int byteOffset, int value, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putInt(this.byteOffset + byteOffset, value);
        buf.order(originalOrder);
    }

    public void setInt8(int byteOffset, byte value) {
        checkOffset(byteOffset, 1);
        buffer.getBuffer().put(this.byteOffset + byteOffset, value);
    }

    public void setUint16(int byteOffset, int value, boolean littleEndian) {
        setInt16(byteOffset, (short) value, littleEndian);
    }

    public void setUint32(int byteOffset, long value, boolean littleEndian) {
        setInt32(byteOffset, (int) value, littleEndian);
    }

    public void setUint8(int byteOffset, int value) {
        setInt8(byteOffset, (byte) value);
    }

    @Override
    public String toString() {
        return "[object DataView]";
    }

    /**
     * Revalidate DataView bounds after constructor prototype resolution.
     * QuickJS performs a second detached/range check because user code may run while reading newTarget.prototype.
     */
    public boolean validateConstructorState(JSContext context) {
        if (buffer.isDetached()) {
            context.throwTypeError("ArrayBuffer is detached");
            return false;
        }
        int currentByteLength = buffer.getByteLength();
        if (byteOffset > currentByteLength) {
            context.throwRangeError("byteOffset out of range");
            return false;
        }
        if (!lengthTracking && (long) byteOffset + byteLength > currentByteLength) {
            context.throwRangeError("byteOffset + byteLength out of range");
            return false;
        }
        return true;
    }
}
