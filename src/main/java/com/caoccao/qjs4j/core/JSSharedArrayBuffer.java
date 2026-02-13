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
    public static final String NAME = "SharedArrayBuffer";
    private final ByteBuffer buffer;
    private final boolean growable;
    private final int maxByteLength;
    private int byteLength;

    /**
     * Create a SharedArrayBuffer with the specified byte length.
     *
     * @param byteLength The length in bytes
     */
    public JSSharedArrayBuffer(int byteLength) {
        this(byteLength, byteLength, false);
    }

    /**
     * Create a growable SharedArrayBuffer with the specified current and max lengths.
     *
     * @param byteLength    The current length in bytes
     * @param maxByteLength The maximum length in bytes
     */
    public JSSharedArrayBuffer(int byteLength, int maxByteLength) {
        this(byteLength, maxByteLength, true);
    }

    private JSSharedArrayBuffer(int byteLength, int maxByteLength, boolean growable) {
        super();
        if (byteLength < 0) {
            throw new IllegalArgumentException("Invalid array buffer length");
        }
        if (maxByteLength < byteLength) {
            throw new IllegalArgumentException("Invalid array buffer max length");
        }
        // Use direct buffer for sharing across threads
        this.buffer = ByteBuffer.allocateDirect(maxByteLength);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN); // JavaScript uses little-endian
        this.byteLength = byteLength;
        this.maxByteLength = maxByteLength;
        this.growable = growable;
    }

    public static JSObject create(JSContext context, JSValue... args) {
        long length = 0;
        try {
            if (args.length > 0) {
                length = JSTypeConversions.toIndex(context, args[0]);
            }
        } catch (IllegalArgumentException e) {
            return context.throwRangeError("Invalid array buffer length");
        }
        if (length > Integer.MAX_VALUE) {
            return context.throwRangeError("Invalid array buffer length");
        }

        int intLength = (int) length;
        int maxLength = intLength;
        boolean growable = false;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            if (args[1] instanceof JSNull) {
                return context.throwTypeError("Cannot convert undefined or null to object");
            }
            if (args[1] instanceof JSObject optionsObject) {
                JSValue maxByteLengthValue = optionsObject.get("maxByteLength");
                if (!(maxByteLengthValue instanceof JSUndefined)) {
                    long maxLen;
                    try {
                        maxLen = JSTypeConversions.toIndex(context, maxByteLengthValue);
                    } catch (IllegalArgumentException e) {
                        return context.throwRangeError("Invalid array buffer max length");
                    }
                    if (maxLen > Integer.MAX_VALUE || maxLen < length) {
                        return context.throwRangeError("Invalid array buffer max length");
                    }
                    maxLength = (int) maxLen;
                    growable = true;
                }
            }
        }

        JSObject jsObject = growable
                ? new JSSharedArrayBuffer(intLength, maxLength)
                : new JSSharedArrayBuffer(intLength);
        context.transferPrototype(jsObject, NAME);
        return jsObject;
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
     * Get the maximum byte length of this buffer.
     *
     * @return The maximum byte length
     */
    public int getMaxByteLength() {
        return maxByteLength;
    }

    /**
     * Grow the SharedArrayBuffer to the specified byte length.
     * SharedArrayBuffers can only grow, never shrink.
     *
     * @param newByteLength The new byte length
     */
    public void grow(int newByteLength) {
        if (!growable) {
            throw new IllegalStateException("array buffer is not growable");
        }
        if (newByteLength < byteLength || newByteLength > maxByteLength) {
            throw new IllegalArgumentException("invalid array buffer length");
        }
        this.byteLength = newByteLength;
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
     * Check if this SharedArrayBuffer is growable.
     *
     * @return true if growable, false otherwise
     */
    public boolean isGrowable() {
        return growable;
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
                ByteBuffer source = buffer.duplicate();
                source.position(begin);
                source.limit(begin + newLength);
                source.get(bytes);
            }
            synchronized (newBuffer.getBuffer()) {
                ByteBuffer target = newBuffer.getBuffer().duplicate();
                target.position(0);
                target.put(bytes);
            }
        }

        return newBuffer;
    }

    @Override
    public String toString() {
        return "[object SharedArrayBuffer]";
    }
}
