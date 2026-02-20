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

import com.caoccao.qjs4j.exceptions.JSException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Represents a JavaScript ArrayBuffer object.
 * Based on ES2020 ArrayBuffer specification.
 * <p>
 * An ArrayBuffer is a raw binary data buffer of a fixed length.
 * It cannot be read or written directly - use TypedArrays or DataView.
 */
public final class JSArrayBuffer extends JSObject implements JSArrayBufferable {
    public static final String NAME = "ArrayBuffer";
    private final ByteBuffer buffer;
    private final int maxByteLength;
    private final boolean resizable;
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
     * ArrayBuffer constructor implementation.
     * new ArrayBuffer(byteLength)
     * new ArrayBuffer(byteLength, options)
     * <p>
     * Based on ES2020 24.1.1.1
     */
    public static JSArrayBuffer create(JSContext context, JSValue... args) {
        long[] validated = validateArgs(context, args);
        return allocateBuffer(context, validated[0], validated[1]);
    }

    /**
     * ArrayBuffer constructor with newTarget support for Reflect.construct.
     * Follows QuickJS js_array_buffer_constructor0/3 ordering:
     * 1. Argument validation (ToIndex, options, byteLength > maxByteLength)
     * 2. OrdinaryCreateFromConstructor (accesses newTarget.prototype)
     * 3. CreateByteDataBlock (allocation limit check + buffer creation)
     */
    public static JSArrayBuffer createForConstruct(JSContext context, JSFunction constructor,
                                                    JSValue newTarget, JSValue... args) {
        // Step 1: Argument validation (before prototype access)
        long[] validated = validateArgs(context, args);

        // Step 2: OrdinaryCreateFromConstructor - access newTarget.prototype
        JSObject resolvedPrototype = null;
        if (newTarget instanceof JSObject newTargetObject) {
            JSValue proto = newTargetObject.get(context, PropertyKey.PROTOTYPE);
            if (context.hasPendingException()) {
                throw new JSException(context.getPendingException());
            }
            if (proto instanceof JSObject protoObj) {
                resolvedPrototype = protoObj;
            }
        }

        // Step 3: Allocation limit check + buffer creation (CreateByteDataBlock)
        JSArrayBuffer buf = allocateBuffer(context, validated[0], validated[1]);

        // Set prototype
        if (resolvedPrototype != null) {
            buf.setPrototype(resolvedPrototype);
        } else if (constructor != null) {
            context.transferPrototype(buf, constructor);
        }
        return buf;
    }

    /**
     * Validate ArrayBuffer constructor arguments.
     * Returns [byteLength, maxByteLength] as longs (-1 for no maxByteLength).
     * Performs ToIndex and options parsing but NOT allocation limit checks.
     */
    private static long[] validateArgs(JSContext context, JSValue[] args) {
        // Get byteLength using ToIndex (preserves large values, throws RangeError for negative)
        JSValue byteLengthArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        long byteLengthLong = JSTypeConversions.toIndex(context, byteLengthArg);

        // Check for options (maxByteLength for resizable buffers)
        long maxByteLengthLong = -1;
        if (args.length >= 2 && args[1] instanceof JSObject options) {
            JSValue maxByteLengthValue = options.get(context, PropertyKey.fromString("maxByteLength"));
            if (context.hasPendingException()) {
                throw new JSException(context.getPendingException());
            }
            if (!(maxByteLengthValue instanceof JSUndefined)) {
                // QuickJS: JS_ToInt64Free then check bounds
                long maxLenLong = (long) JSTypeConversions.toInteger(context, maxByteLengthValue);
                if (byteLengthLong > maxLenLong || maxLenLong > 9007199254740991L) {
                    throw new JSException(context.throwRangeError("invalid array buffer max length"));
                }
                maxByteLengthLong = maxLenLong;
            }
        }
        return new long[]{byteLengthLong, maxByteLengthLong};
    }

    /**
     * Allocate the ArrayBuffer with validated lengths.
     * Performs allocation limit checks (QuickJS INT32_MAX limit).
     */
    private static JSArrayBuffer allocateBuffer(JSContext context, long byteLengthLong, long maxByteLengthLong) {
        // QuickJS: limited to INT32_MAX (2 GB)
        if (byteLengthLong > Integer.MAX_VALUE) {
            throw new JSException(context.throwRangeError("invalid array buffer length"));
        }
        int byteLength = (int) byteLengthLong;

        if (maxByteLengthLong >= 0) {
            if (maxByteLengthLong > Integer.MAX_VALUE) {
                throw new JSException(context.throwRangeError("invalid array buffer max length"));
            }
            return new JSArrayBuffer(byteLength, (int) maxByteLengthLong);
        } else {
            return new JSArrayBuffer(byteLength);
        }
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

        int oldByteLength = buffer.limit();
        buffer.limit(newByteLength);
        if (newByteLength > oldByteLength) {
            // Zero newly accessible bytes per ES2024 spec
            Arrays.fill(buffer.array(), oldByteLength, newByteLength, (byte) 0);
        }
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
