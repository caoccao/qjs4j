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
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

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
public final class JSSharedArrayBuffer extends JSObject implements IJSArrayBuffer {
    public static final String NAME = "SharedArrayBuffer";
    private final ByteBuffer buffer;
    /**
     * The current length, which every agent sharing this buffer reads and any of them may grow.
     * <p>
     * A plain {@code int} gave neither guarantee the class's own contract needs. Two agents could
     * read the same old length, request 50 and 100, and write 100 then 50 — both calls reporting
     * success while the buffer observably shrank — and an agent on another thread had no
     * happens-before edge that made a new length visible at all. The compare-and-set loop below
     * makes growth monotonic, and the volatile read makes it visible.
     */
    private final AtomicInteger byteLength;
    private final boolean growable;
    private final int maxByteLength;

    /**
     * Create a SharedArrayBuffer with the specified byte length.
     *
     * @param byteLength The length in bytes
     */
    public JSSharedArrayBuffer(JSContext context, int byteLength) {
        this(context, byteLength, byteLength, false);
    }

    /**
     * Create a growable SharedArrayBuffer with the specified current and max lengths.
     *
     * @param byteLength    The current length in bytes
     * @param maxByteLength The maximum length in bytes
     */
    public JSSharedArrayBuffer(JSContext context, int byteLength, int maxByteLength) {
        this(context, byteLength, maxByteLength, true);
    }

    /**
     * Allocate a shared data block.
     * <p>
     * A growable {@code SharedArrayBuffer} allocates {@code maxByteLength} up front, unlike
     * {@link JSArrayBuffer}, and deliberately so: the backing {@code byte[]} is handed to other
     * agents — {@code Atomics} reaches it through {@code getBuffer().array()} — so it cannot be
     * replaced on growth without those agents silently continuing to read a stale block. V8
     * reserves address space and commits lazily; the JVM has no equivalent. The size is charged to
     * the runtime's memory accounting instead, so declaring a large {@code maxByteLength} is
     * refused by the configured limit rather than by the heap running out.
     */
    private JSSharedArrayBuffer(JSContext context, int byteLength, int maxByteLength, boolean growable) {
        super(context);
        if (byteLength < 0) {
            throw new JSRangeErrorException("Invalid array buffer length");
        }
        if (maxByteLength < byteLength) {
            throw new JSRangeErrorException("Invalid array buffer max length");
        }
        // Use heap buffer so backing byte[] is accessible for VarHandle atomics
        // Pad to multiple of 4 so VarHandle int-width CAS works for short-typed atomics
        int capacity = JSArrayBuffer.paddedCapacity(maxByteLength);
        JSMemoryAccounting accounting = context.getRuntime().getMemoryAccounting();
        JSMemoryAccounting.Reservation pendingReservation = accounting.reserve(this, capacity);
        if (pendingReservation == null) {
            throw new JSRangeErrorException(
                    "Shared array buffer allocation failed: the runtime memory limit of "
                            + accounting.getLimit() + " bytes would be exceeded");
        }
        ByteBuffer allocated;
        try {
            allocated = ByteBuffer.allocate(capacity);
        } catch (RuntimeException | Error allocationFailure) {
            // Reserving before allocating is deliberate, so a failed allocation has to hand the
            // bytes back rather than leave the runtime's ceiling inflated by a block that does not
            // exist.
            pendingReservation.release();
            throw allocationFailure;
        }
        this.buffer = allocated;
        this.buffer.order(ByteOrder.LITTLE_ENDIAN); // JavaScript uses little-endian
        this.byteLength = new AtomicInteger(byteLength);
        this.maxByteLength = maxByteLength;
        this.growable = growable;
    }

    private static JSSharedArrayBuffer allocateBuffer(JSContext context, long length, long maxLength, boolean growable) {
        if (length > Integer.MAX_VALUE) {
            throw new JSException(context.throwRangeError("Invalid array buffer length"));
        }
        if (maxLength > Integer.MAX_VALUE) {
            throw new JSException(context.throwRangeError("Invalid array buffer max length"));
        }
        int intLength = (int) length;
        int intMaxLength = (int) maxLength;
        return growable
                ? new JSSharedArrayBuffer(context, intLength, intMaxLength)
                : new JSSharedArrayBuffer(context, intLength);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        try {
            long[] validated = validateArgs(context, args);
            JSSharedArrayBuffer buffer = allocateBuffer(context, validated[0], validated[1], validated[2] != 0);
            context.transferPrototype(buffer, NAME);
            return buffer;
        } catch (JSException e) {
            if (e.getErrorValue() instanceof JSObject errorObject) {
                return errorObject;
            }
            return context.throwRangeError("Invalid array buffer length");
        }
    }

    public static JSSharedArrayBuffer createForConstruct(JSContext context, JSFunction constructor,
                                                         JSValue newTarget, JSValue... args) {
        long[] validated = validateArgs(context, args);

        JSObject resolvedPrototype = null;
        if (newTarget instanceof JSObject newTargetObject) {
            resolvedPrototype = context.getPrototypeFromConstructor(newTargetObject, NAME);
            if (context.hasPendingException()) {
                throw new JSException(context.getPendingException());
            }
        }

        JSSharedArrayBuffer buffer = allocateBuffer(context, validated[0], validated[1], validated[2] != 0);
        if (resolvedPrototype != null) {
            buffer.setPrototype(resolvedPrototype);
        } else if (constructor != null) {
            JSObject constructorPrototype = context.getPrototypeFromConstructor(constructor, NAME);
            if (context.hasPendingException()) {
                throw new JSException(context.getPendingException());
            }
            if (constructorPrototype != null) {
                buffer.setPrototype(constructorPrototype);
            }
        }
        return buffer;
    }

    private static long[] validateArgs(JSContext context, JSValue[] args) {
        long length = 0;
        try {
            if (args.length > 0) {
                length = JSTypeConversions.toIndex(context, args[0]);
            }
        } catch (IllegalArgumentException | JSRangeErrorException e) {
            throw new JSException(context.throwRangeError("Invalid array buffer length"));
        }
        long maxLength = length;
        boolean growable = false;
        // GetArrayBufferMaxByteLengthOption: if Type(options) is not Object, return empty
        if (args.length > 1 && args[1] instanceof JSObject optionsObject) {
            boolean hadException = context.hasPendingException();
            JSValue maxByteLengthValue = optionsObject.get(PropertyKey.fromString("maxByteLength"));
            if (!hadException && context.hasPendingException()) {
                throw new JSException(context.getPendingException());
            }
            if (!(maxByteLengthValue instanceof JSUndefined)) {
                long maxLen;
                try {
                    maxLen = JSTypeConversions.toIndex(context, maxByteLengthValue);
                } catch (IllegalArgumentException | JSRangeErrorException e) {
                    throw new JSException(context.throwRangeError("Invalid array buffer max length"));
                }
                if (maxLen < length) {
                    throw new JSException(context.throwRangeError("Invalid array buffer max length"));
                }
                maxLength = maxLen;
                growable = true;
            }
        }
        return new long[]{length, maxLength, growable ? 1 : 0};
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
        return byteLength.get();
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
            throw new JSTypeErrorException("array buffer is not growable");
        }
        if (newByteLength > maxByteLength) {
            throw new JSRangeErrorException("invalid array buffer length");
        }
        // Compare against the length this attempt actually observed, and publish only a larger
        // one. A request below the current length is rejected however the agents interleave, and
        // a request that loses the race to a larger one is rejected rather than shrinking it back.
        while (true) {
            int currentByteLength = byteLength.get();
            if (newByteLength < currentByteLength) {
                throw new JSRangeErrorException("invalid array buffer length");
            }
            if (byteLength.compareAndSet(currentByteLength, newByteLength)) {
                return;
            }
        }
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

    @Override
    public boolean isImmutable() {
        return false;
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
        // One read of the shared length, so both ends are normalised against the same value.
        int currentByteLength = byteLength.get();

        // Normalize begin
        if (begin < 0) {
            begin = Math.max(currentByteLength + begin, 0);
        } else {
            begin = Math.min(begin, currentByteLength);
        }

        // Normalize end
        if (end < 0) {
            end = Math.max(currentByteLength + end, 0);
        } else {
            end = Math.min(end, currentByteLength);
        }

        // Calculate new length
        int newLength = Math.max(end - begin, 0);

        // Create new buffer and copy bytes
        JSSharedArrayBuffer newBuffer = new JSSharedArrayBuffer(context, newLength);
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
