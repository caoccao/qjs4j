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
 * Represents a JavaScript ArrayBuffer object.
 * Based on ES2020 ArrayBuffer specification.
 * <p>
 * An ArrayBuffer is a raw binary data buffer of a fixed length.
 * It cannot be read or written directly - use TypedArrays or DataView.
 */
public final class JSArrayBuffer extends JSObject {
    private final int maxByteLength;
    private final boolean resizable;
    private ByteBuffer buffer;
    private boolean detached;

    /**
     * Create an ArrayBuffer with the specified byte length.
     *
     * @param byteLength The length in bytes
     */
    public JSArrayBuffer(int byteLength) {
        this(byteLength, -1);
    }

    /**
     * Create an ArrayBuffer with the specified byte length and max byte length.
     *
     * @param byteLength    The initial length in bytes
     * @param maxByteLength The maximum length in bytes, or -1 for non-resizable
     */
    public JSArrayBuffer(int byteLength, int maxByteLength) {
        super();
        if (byteLength < 0) {
            throw new IllegalArgumentException("ArrayBuffer byteLength must be non-negative");
        }
        if (maxByteLength != -1 && maxByteLength < byteLength) {
            throw new IllegalArgumentException("ArrayBuffer maxByteLength must be >= byteLength");
        }
        this.buffer = ByteBuffer.allocate(maxByteLength != -1 ? maxByteLength : byteLength);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN); // JavaScript uses little-endian
        this.buffer.limit(byteLength);
        this.detached = false;
        this.resizable = (maxByteLength != -1);
        this.maxByteLength = (maxByteLength != -1) ? maxByteLength : byteLength;
    }

    /**
     * Create an ArrayBuffer from an existing byte array.
     *
     * @param bytes The byte array to wrap
     */
    public JSArrayBuffer(byte[] bytes) {
        super();
        this.buffer = ByteBuffer.wrap(bytes);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.detached = false;
        this.resizable = false;
        this.maxByteLength = bytes.length;
    }

    /**
     * Detach this ArrayBuffer, making it unusable.
     * ES2020 24.1.1.3
     */
    public void detach() {
        this.detached = true;
    }

    /**
     * Get the underlying ByteBuffer.
     * This is for internal use by TypedArrays and DataView.
     *
     * @return The ByteBuffer, or null if detached
     */
    public ByteBuffer getBuffer() {
        if (detached) {
            return null;
        }
        return buffer;
    }

    /**
     * Get the byte length of this buffer.
     *
     * @return The byte length
     */
    public int getByteLength() {
        if (detached) {
            return 0;
        }
        return buffer.limit();
    }

    /**
     * Get the maximum byte length of this buffer.
     *
     * @return The maximum byte length
     */
    public int getMaxByteLength() {
        return maxByteLength;
    }

    /**
     * Check if this ArrayBuffer is detached.
     *
     * @return true if detached, false otherwise
     */
    public boolean isDetached() {
        return detached;
    }

    /**
     * Check if this ArrayBuffer is resizable.
     *
     * @return true if resizable, false otherwise
     */
    public boolean isResizable() {
        return resizable;
    }

    /**
     * Check if this buffer is a SharedArrayBuffer.
     *
     * @return false for ArrayBuffer, true for SharedArrayBuffer
     */
    public boolean isShared() {
        return false;
    }

    /**
     * Resize the ArrayBuffer to the specified size.
     * ES2024 25.1.5.3
     *
     * @param newByteLength The new byte length
     * @throws IllegalStateException    if the buffer is detached or not resizable
     * @throws IllegalArgumentException if newByteLength exceeds maxByteLength
     */
    public void resize(int newByteLength) {
        if (detached) {
            throw new IllegalStateException("Cannot resize a detached ArrayBuffer");
        }
        if (!resizable) {
            throw new IllegalStateException("Cannot resize a non-resizable ArrayBuffer");
        }
        if (newByteLength < 0 || newByteLength > maxByteLength) {
            throw new IllegalArgumentException("New byte length must be between 0 and " + maxByteLength);
        }

        buffer.limit(newByteLength);
    }

    /**
     * ArrayBuffer.prototype.slice(begin, end)
     * ES2020 24.1.4.3
     * Returns a new ArrayBuffer with a copy of the bytes from begin to end.
     *
     * @param begin Start offset (inclusive)
     * @param end   End offset (exclusive)
     * @return A new ArrayBuffer
     */
    public JSArrayBuffer slice(int begin, int end) {
        if (detached) {
            throw new IllegalStateException("Cannot slice a detached ArrayBuffer");
        }

        int byteLength = getByteLength();

        // Normalize begin
        if (begin < 0) {
            begin = Math.max(byteLength + begin, 0);
        } else {
            begin = Math.min(begin, byteLength);
        }

        // Normalize end
        if (end < 0) {
            end = Math.max(byteLength + end, 0);
        } else {
            end = Math.min(end, byteLength);
        }

        // Calculate new length
        int newLength = Math.max(end - begin, 0);

        // Create new buffer and copy bytes
        JSArrayBuffer newBuffer = new JSArrayBuffer(newLength);
        if (newLength > 0) {
            byte[] bytes = new byte[newLength];
            int oldPosition = buffer.position();
            buffer.position(begin);
            buffer.get(bytes, 0, newLength);
            buffer.position(oldPosition); // Reset position
            newBuffer.getBuffer().put(bytes);
            newBuffer.getBuffer().position(0);
        }

        return newBuffer;
    }

    @Override
    public String toString() {
        return "[object ArrayBuffer]";
    }

    /**
     * Transfer the contents to a new ArrayBuffer and detach this buffer.
     * ES2024 25.1.5.4
     *
     * @param newByteLength The byte length of the new buffer, or -1 to use current length
     * @return A new ArrayBuffer with the transferred contents
     * @throws IllegalStateException if the buffer is already detached
     */
    public JSArrayBuffer transfer(int newByteLength) {
        if (detached) {
            throw new IllegalStateException("Cannot transfer a detached ArrayBuffer");
        }

        int currentLength = getByteLength();
        int targetLength = (newByteLength == -1) ? currentLength : newByteLength;

        if (targetLength < 0) {
            throw new IllegalArgumentException("New byte length must be non-negative");
        }

        // Create new resizable buffer with same characteristics
        JSArrayBuffer newBuffer = new JSArrayBuffer(targetLength, resizable ? maxByteLength : -1);

        // Copy data up to the minimum of current and target length
        int copyLength = Math.min(currentLength, targetLength);
        if (copyLength > 0) {
            byte[] bytes = new byte[copyLength];
            int oldPosition = buffer.position();
            buffer.position(0);
            buffer.get(bytes, 0, copyLength);
            buffer.position(oldPosition);
            newBuffer.getBuffer().put(bytes);
            newBuffer.getBuffer().position(0);
        }

        // Detach this buffer
        detach();

        return newBuffer;
    }

    /**
     * Transfer the contents to a new fixed-length ArrayBuffer and detach this buffer.
     * ES2024 25.1.5.5
     *
     * @param newByteLength The byte length of the new buffer, or -1 to use current length
     * @return A new non-resizable ArrayBuffer with the transferred contents
     * @throws IllegalStateException if the buffer is already detached
     */
    public JSArrayBuffer transferToFixedLength(int newByteLength) {
        if (detached) {
            throw new IllegalStateException("Cannot transfer a detached ArrayBuffer");
        }

        int currentLength = getByteLength();
        int targetLength = (newByteLength == -1) ? currentLength : newByteLength;

        if (targetLength < 0) {
            throw new IllegalArgumentException("New byte length must be non-negative");
        }

        // Create new fixed-length buffer
        JSArrayBuffer newBuffer = new JSArrayBuffer(targetLength);

        // Copy data up to the minimum of current and target length
        int copyLength = Math.min(currentLength, targetLength);
        if (copyLength > 0) {
            byte[] bytes = new byte[copyLength];
            int oldPosition = buffer.position();
            buffer.position(0);
            buffer.get(bytes, 0, copyLength);
            buffer.position(oldPosition);
            newBuffer.getBuffer().put(bytes);
            newBuffer.getBuffer().position(0);
        }

        // Detach this buffer
        detach();

        return newBuffer;
    }
}
