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

import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Auto-growing byte buffer similar to QuickJS's DynBuf.
 * Based on cutils.c implementation.
 */
public final class DynamicBuffer {
    /**
     * The largest array the JVM will allocate, less a margin HotSpot reserves.
     */
    public static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;
    private final int maxCapacity;
    private byte[] buffer;
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
        this(initialCapacity, MAX_CAPACITY);
    }

    /**
     * Create a buffer with an explicit ceiling.
     * <p>
     * The ceiling exists so the growth-failure path can be exercised without exhausting the test
     * JVM, which is otherwise the only way to reach it.
     *
     * @param initialCapacity the starting capacity
     * @param maxCapacity     the largest capacity this buffer may reach
     */
    public DynamicBuffer(int initialCapacity, int maxCapacity) {
        this.maxCapacity = Math.min(Math.max(maxCapacity, 16), MAX_CAPACITY);
        this.buffer = new byte[Math.min(Math.max(initialCapacity, 16), this.maxCapacity)];
        this.size = 0;
    }

    /**
     * Append a single byte to the buffer.
     */
    public void append(byte b) {
        ensureCapacity(size + 1);
        buffer[size++] = b;
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
        System.arraycopy(bytes, offset, buffer, size, length);
        size += length;
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
     * <p>
     * A value outside {@code 0..65535} is a caller bug, not data: the old behaviour of writing the
     * low two bytes of whatever arrived is what turned a 65,538-byte RegExp payload into a
     * declared length of 2, with no diagnostic anywhere.
     *
     * @param value the value to append
     * @throws IllegalArgumentException when the value does not fit in 16 bits
     */
    public void appendU16(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value does not fit in an unsigned 16-bit field: " + value);
        }
        ensureCapacity(size + 2);
        buffer[size++] = (byte) value;
        buffer[size++] = (byte) (value >> 8);
    }

    /**
     * Append an unsigned 32-bit value (little-endian).
     */
    public void appendU32(long value) {
        ensureCapacity(size + 4);
        buffer[size++] = (byte) value;
        buffer[size++] = (byte) (value >> 8);
        buffer[size++] = (byte) (value >> 16);
        buffer[size++] = (byte) (value >> 24);
    }

    /**
     * Append an unsigned 64-bit value (little-endian).
     */
    public void appendU64(long value) {
        ensureCapacity(size + 8);
        buffer[size++] = (byte) value;
        buffer[size++] = (byte) (value >> 8);
        buffer[size++] = (byte) (value >> 16);
        buffer[size++] = (byte) (value >> 24);
        buffer[size++] = (byte) (value >> 32);
        buffer[size++] = (byte) (value >> 40);
        buffer[size++] = (byte) (value >> 48);
        buffer[size++] = (byte) (value >> 56);
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
    }

    /**
     * Ensure the buffer has enough capacity for the required size, growing by doubling.
     * <p>
     * Growth failure <strong>throws</strong>. It used to set an {@code error} flag and make every
     * later append a silent no-op, and {@code RegExpCompiler.compile()} never read the flag: under
     * memory pressure it appended a final {@code MATCH} that was also ignored and returned the
     * truncated bytes as a valid program, so the symptom surfaced later as a wrong match or an
     * opcode error with nothing left to say the allocation had failed.
     * <p>
     * An {@code OutOfMemoryError} is not caught either. Converting one into a flag is what turned a
     * fatal, diagnosable condition into corrupt output.
     *
     * @param required the capacity needed
     * @throws JSRangeErrorException when the required capacity exceeds this buffer's ceiling
     */
    private void ensureCapacity(int required) {
        if (required >= 0 && required <= buffer.length) {
            return;
        }
        // Checked in long: `size + length` can overflow to a negative required size, which would
        // silently satisfy the test above.
        long requiredCapacity = required & 0xFFFFFFFFL;
        if (required < 0 || requiredCapacity > maxCapacity) {
            throw new JSRangeErrorException(
                    "Buffer cannot grow beyond " + maxCapacity + " bytes");
        }
        long doubled = (long) buffer.length * 2L;
        int newCapacity = (int) Math.min(Math.max(doubled, requiredCapacity), maxCapacity);
        buffer = Arrays.copyOf(buffer, newCapacity);
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
     * Insert bytes at the specified position.
     */
    public void insert(int position, int length) {
        if (position < 0 || position > size) {
            throw new IndexOutOfBoundsException("Invalid position");
        }
        ensureCapacity(size + length);
        System.arraycopy(buffer, position, buffer, position + length, size - position);
        size += length;
    }

    /**
     * Reset the buffer and optionally resize.
     */
    public void reset(int newCapacity) {
        if (newCapacity > 0) {
            buffer = new byte[Math.min(newCapacity, maxCapacity)];
        }
        size = 0;
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
        return "DynamicBuffer{size=" + size + ", capacity=" + buffer.length + "}";
    }

    /**
     * Truncate the buffer to the specified size.
     */
    public void truncate(int newSize) {
        if (newSize < 0 || newSize > size) {
            throw new JSRangeErrorException("Invalid size");
        }
        size = newSize;
    }
}
