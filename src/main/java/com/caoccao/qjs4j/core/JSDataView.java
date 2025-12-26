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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents a JavaScript DataView object.
 * Based on ES2020 DataView specification.
 *
 * DataView provides a low-level interface for reading and writing
 * multiple number types in an ArrayBuffer.
 */
public final class JSDataView extends JSObject {
    private final JSArrayBuffer buffer;
    private final int byteOffset;
    private final int byteLength;

    /**
     * Create a DataView for the entire buffer.
     */
    public JSDataView(JSArrayBuffer buffer) {
        this(buffer, 0, buffer.getByteLength());
    }

    /**
     * Create a DataView for a portion of the buffer.
     */
    public JSDataView(JSArrayBuffer buffer, int byteOffset, int byteLength) {
        super();
        if (buffer == null || buffer.isDetached()) {
            throw new IllegalArgumentException("Cannot create DataView on detached buffer");
        }
        if (byteOffset < 0 || byteOffset > buffer.getByteLength()) {
            throw new RangeError("DataView byteOffset out of range");
        }
        if (byteLength < 0 || byteOffset + byteLength > buffer.getByteLength()) {
            throw new RangeError("DataView byteLength out of range");
        }

        this.buffer = buffer;
        this.byteOffset = byteOffset;
        this.byteLength = byteLength;
    }

    public JSArrayBuffer getBuffer() {
        return buffer;
    }

    public int getByteOffset() {
        return byteOffset;
    }

    public int getByteLength() {
        return byteLength;
    }

    // Int8 operations
    public byte getInt8(int byteOffset) {
        checkOffset(byteOffset, 1);
        return buffer.getBuffer().get(this.byteOffset + byteOffset);
    }

    public void setInt8(int byteOffset, byte value) {
        checkOffset(byteOffset, 1);
        buffer.getBuffer().put(this.byteOffset + byteOffset, value);
    }

    // Uint8 operations
    public int getUint8(int byteOffset) {
        return getInt8(byteOffset) & 0xFF;
    }

    public void setUint8(int byteOffset, int value) {
        setInt8(byteOffset, (byte) value);
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

    public void setInt16(int byteOffset, short value, boolean littleEndian) {
        checkOffset(byteOffset, 2);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putShort(this.byteOffset + byteOffset, value);
        buf.order(originalOrder);
    }

    // Uint16 operations
    public int getUint16(int byteOffset, boolean littleEndian) {
        return getInt16(byteOffset, littleEndian) & 0xFFFF;
    }

    public void setUint16(int byteOffset, int value, boolean littleEndian) {
        setInt16(byteOffset, (short) value, littleEndian);
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

    public void setInt32(int byteOffset, int value, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putInt(this.byteOffset + byteOffset, value);
        buf.order(originalOrder);
    }

    // Uint32 operations
    public long getUint32(int byteOffset, boolean littleEndian) {
        return getInt32(byteOffset, littleEndian) & 0xFFFFFFFFL;
    }

    public void setUint32(int byteOffset, long value, boolean littleEndian) {
        setInt32(byteOffset, (int) value, littleEndian);
    }

    // Float32 operations
    public float getFloat32(int byteOffset, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        float value = buf.getFloat(this.byteOffset + byteOffset);
        buf.order(originalOrder);
        return value;
    }

    public void setFloat32(int byteOffset, float value, boolean littleEndian) {
        checkOffset(byteOffset, 4);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putFloat(this.byteOffset + byteOffset, value);
        buf.order(originalOrder);
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

    public void setFloat64(int byteOffset, double value, boolean littleEndian) {
        checkOffset(byteOffset, 8);
        ByteBuffer buf = buffer.getBuffer();
        ByteOrder originalOrder = buf.order();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putDouble(this.byteOffset + byteOffset, value);
        buf.order(originalOrder);
    }

    private void checkOffset(int offset, int size) {
        if (buffer.isDetached()) {
            throw new IllegalStateException("DataView buffer is detached");
        }
        if (offset < 0 || offset + size > byteLength) {
            throw new RangeError("DataView offset out of range");
        }
    }

    @Override
    public String toString() {
        return "[object DataView]";
    }

    public static class RangeError extends RuntimeException {
        public RangeError(String message) {
            super(message);
        }
    }
}
