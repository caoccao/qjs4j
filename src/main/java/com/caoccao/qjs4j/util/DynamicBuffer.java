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

package com.caoccao.qjs4j.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Auto-growing byte buffer similar to QuickJS's DynBuf.
 * Based on cutils.c implementation.
 */
public final class DynamicBuffer {
    private byte[] buffer;
    private boolean error;
    private int size;

    /**
     * Create a new dynamic buffer with default initial capacity (64 bytes).
     */
    public DynamicBuffer() {
        this(64);
    }

    /**
     * Create a new dynamic buffer with specified initial capacity.
     */
    public DynamicBuffer(int initialCapacity) {
        this.buffer = new byte[Math.max(initialCapacity, 16)];
        this.size = 0;
        this.error = false;
    }

    /**
     * Append a single byte to the buffer.
     */
    public void append(byte b) {
        ensureCapacity(size + 1);
        if (!error) {
            buffer[size++] = b;
        }
    }

    /**
     * Append a byte array to the buffer.
     */
    public void append(byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            append(bytes, 0, bytes.length);
        }
    }

    /**
     * Append part of a byte array to the buffer.
     */
    public void append(byte[] bytes, int offset, int length) {
        if (bytes == null || length == 0) {
            return;
        }

        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new IndexOutOfBoundsException("Invalid offset or length");
        }

        ensureCapacity(size + length);
        if (!error) {
            System.arraycopy(bytes, offset, buffer, size, length);
            size += length;
        }
    }

    /**
     * Append a string encoded as UTF-8.
     */
    public void appendString(String str) {
        if (str != null && !str.isEmpty()) {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            append(bytes);
        }
    }

    /**
     * Append an unsigned 16-bit value (little-endian).
     */
    public void appendU16(int value) {
        ensureCapacity(size + 2);
        if (!error) {
            buffer[size++] = (byte) value;
            buffer[size++] = (byte) (value >> 8);
        }
    }

    /**
     * Append an unsigned 32-bit value (little-endian).
     */
    public void appendU32(long value) {
        ensureCapacity(size + 4);
        if (!error) {
            buffer[size++] = (byte) value;
            buffer[size++] = (byte) (value >> 8);
            buffer[size++] = (byte) (value >> 16);
            buffer[size++] = (byte) (value >> 24);
        }
    }

    /**
     * Append an unsigned 64-bit value (little-endian).
     */
    public void appendU64(long value) {
        ensureCapacity(size + 8);
        if (!error) {
            buffer[size++] = (byte) value;
            buffer[size++] = (byte) (value >> 8);
            buffer[size++] = (byte) (value >> 16);
            buffer[size++] = (byte) (value >> 24);
            buffer[size++] = (byte) (value >> 32);
            buffer[size++] = (byte) (value >> 40);
            buffer[size++] = (byte) (value >> 48);
            buffer[size++] = (byte) (value >> 56);
        }
    }

    /**
     * Append an unsigned 8-bit value.
     */
    public void appendU8(int value) {
        append((byte) value);
    }

    /**
     * Get the current capacity of the buffer.
     */
    public int capacity() {
        return buffer.length;
    }

    /**
     * Clear the buffer.
     */
    public void clear() {
        size = 0;
        error = false;
    }

    /**
     * Ensure the buffer has enough capacity for the required size.
     * Grows the buffer by doubling if necessary.
     */
    private void ensureCapacity(int required) {
        if (required <= buffer.length) {
            return;
        }

        try {
            // Calculate new capacity (at least double, or required size)
            int newCapacity = Math.max(buffer.length * 2, required);

            // Limit maximum capacity to avoid OutOfMemoryError
            if (newCapacity < 0 || newCapacity > Integer.MAX_VALUE - 8) {
                newCapacity = required;
            }

            buffer = Arrays.copyOf(buffer, newCapacity);
        } catch (OutOfMemoryError e) {
            error = true;
        }
    }

    /**
     * Get the internal buffer (for advanced use only).
     * Note: The returned array may be larger than size().
     */
    byte[] getInternalBuffer() {
        return buffer;
    }

    /**
     * Get a range of bytes from the buffer.
     */
    public byte[] getRange(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > size) {
            throw new IndexOutOfBoundsException("Invalid range");
        }
        return Arrays.copyOfRange(buffer, offset, offset + length);
    }

    /**
     * Check if an error occurred during buffer operations.
     */
    public boolean hasError() {
        return error;
    }

    /**
     * Insert bytes at the specified position.
     */
    public void insert(int position, int length) {
        if (position < 0 || position > size) {
            throw new IndexOutOfBoundsException("Invalid position");
        }
        ensureCapacity(size + length);
        if (!error) {
            System.arraycopy(buffer, position, buffer, position + length, size - position);
            size += length;
        }
    }

    /**
     * Reset the buffer and optionally resize.
     */
    public void reset(int newCapacity) {
        if (newCapacity > 0) {
            buffer = new byte[newCapacity];
        }
        size = 0;
        error = false;
    }

    /**
     * Set a 32-bit value at the specified position (little-endian).
     */
    public void setU32(int position, int value) {
        if (position < 0 || position + 4 > size) {
            throw new IndexOutOfBoundsException("Invalid position");
        }
        buffer[position] = (byte) (value & 0xFF);
        buffer[position + 1] = (byte) ((value >> 8) & 0xFF);
        buffer[position + 2] = (byte) ((value >> 16) & 0xFF);
        buffer[position + 3] = (byte) ((value >> 24) & 0xFF);
    }

    /**
     * Set a single byte at the specified position.
     */
    public void setU8(int position, int value) {
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("Invalid position");
        }
        buffer[position] = (byte) value;
    }

    /**
     * Get the current size of the buffer.
     */
    public int size() {
        return size;
    }

    /**
     * Get a copy of the buffer contents.
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, size);
    }

    /**
     * Get a ByteBuffer view of the contents (read-only).
     */
    public ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(buffer, 0, size).asReadOnlyBuffer();
    }

    @Override
    public String toString() {
        return "DynamicBuffer{size=" + size + ", capacity=" + buffer.length + ", error=" + error + "}";
    }

    /**
     * Truncate the buffer to the specified size.
     */
    public void truncate(int newSize) {
        if (newSize < 0 || newSize > size) {
            throw new IllegalArgumentException("Invalid size");
        }
        size = newSize;
    }
}
