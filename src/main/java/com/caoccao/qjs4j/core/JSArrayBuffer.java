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
    private final ByteBuffer buffer;
    private boolean detached;

    /**
     * Create an ArrayBuffer with the specified byte length.
     *
     * @param byteLength The length in bytes
     */
    public JSArrayBuffer(int byteLength) {
        super();
        if (byteLength < 0) {
            throw new IllegalArgumentException("ArrayBuffer byteLength must be non-negative");
        }
        this.buffer = ByteBuffer.allocate(byteLength);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN); // JavaScript uses little-endian
        this.detached = false;
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
        return buffer.capacity();
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
     * Detach this ArrayBuffer, making it unusable.
     * ES2020 24.1.1.3
     */
    public void detach() {
        this.detached = true;
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
     * Check if this buffer is a SharedArrayBuffer.
     *
     * @return false for ArrayBuffer, true for SharedArrayBuffer
     */
    public boolean isShared() {
        return false;
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
            buffer.position(begin);
            buffer.get(bytes, 0, newLength);
            newBuffer.getBuffer().put(bytes);
            buffer.position(0); // Reset position
        }

        return newBuffer;
    }

    @Override
    public String toString() {
        return "[object ArrayBuffer]";
    }
}
