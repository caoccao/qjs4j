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
 * Represents a JavaScript SharedArrayBuffer object.
 * Based on ES2017 SharedArrayBuffer specification.
 * <p>
 * A SharedArrayBuffer is a raw binary data buffer that can be shared across
 * multiple workers/agents. Unlike ArrayBuffer, it cannot be detached and
 * uses direct memory allocation for efficient sharing.
 * <p>
 * Key characteristics:
 * - Fixed-length binary data buffer
 * - Can be shared across workers/threads
 * - Cannot be detached (no transferable semantics)
 * - Used with Atomics for thread-safe operations
 * - Direct ByteBuffer for efficient multi-threaded access
 */
public final class JSSharedArrayBuffer extends JSObject implements JSArrayBufferable {
    private final ByteBuffer buffer;
    private final int byteLength;

    /**
     * Create a SharedArrayBuffer with the specified byte length.
     *
     * @param byteLength The length in bytes
     */
    public JSSharedArrayBuffer(int byteLength) {
        super();
        if (byteLength < 0) {
            throw new IllegalArgumentException("SharedArrayBuffer byteLength must be non-negative");
        }
        // Use direct buffer for sharing across threads
        this.buffer = ByteBuffer.allocateDirect(byteLength);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN); // JavaScript uses little-endian
        this.byteLength = byteLength;
    }

    /**
     * Get the underlying ByteBuffer.
     * This is for internal use by TypedArrays, DataView, and Atomics.
     *
     * @return The direct ByteBuffer
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * Get the byte length of this buffer.
     * ES2017 24.2.4.1 get SharedArrayBuffer.prototype.byteLength
     *
     * @return The byte length
     */
    public int getByteLength() {
        return byteLength;
    }

    /**
     * Check if this buffer is a SharedArrayBuffer.
     * Used to distinguish from regular ArrayBuffer.
     *
     * @return Always true
     */
    public boolean isShared() {
        return true;
    }

    /**
     * Check if this SharedArrayBuffer is detached.
     * SharedArrayBuffers cannot be detached.
     *
     * @return Always false
     */
    public boolean isDetached() {
        return false;
    }

    /**
     * SharedArrayBuffer.prototype.slice(begin, end)
     * ES2017 24.2.4.3
     * Returns a new SharedArrayBuffer with a copy of the bytes from begin to end.
     *
     * @param begin Start offset (inclusive)
     * @param end   End offset (exclusive)
     * @return A new SharedArrayBuffer
     */
    public JSSharedArrayBuffer slice(int begin, int end) {
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
        JSSharedArrayBuffer newBuffer = new JSSharedArrayBuffer(newLength);
        if (newLength > 0) {
            byte[] bytes = new byte[newLength];
            synchronized (buffer) {
                buffer.position(begin);
                buffer.get(bytes, 0, newLength);
                buffer.position(0); // Reset position
            }
            synchronized (newBuffer.getBuffer()) {
                newBuffer.getBuffer().put(bytes);
                newBuffer.getBuffer().position(0);
            }
        }

        return newBuffer;
    }

    @Override
    public String toString() {
        return "[object SharedArrayBuffer]";
    }
}
