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

import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a JavaScript SharedArrayBuffer object. Based on ES2017 SharedArrayBuffer specification.
 * <p>
 * A SharedArrayBuffer is a raw binary data buffer that can be shared across multiple workers/agents. Unlike
 * ArrayBuffer, it cannot be detached. Shared byte pages keep stable identities as the visible length grows.
 * <p>
 * Key characteristics: - Fixed-length or growable binary data buffer - Can be shared across workers/threads - Cannot be
 * detached (no transferable semantics) - Used with Atomics for thread-safe operations - Segmented JSByteBuffer for
 * long-indexed multi-threaded access
 */
public final class JSSharedArrayBuffer extends JSObject implements IJSArrayBuffer {
    public static final String NAME = "SharedArrayBuffer";
    private final JSByteBuffer buffer;
    /**
     * The current length, which every agent sharing this buffer reads and any of them may grow.
     * <p>
     * A plain {@code int} gave neither guarantee the class's own contract needs. Two agents could read the same old
     * length, request 50 and 100, and write 100 then 50 — both calls reporting success while the buffer observably
     * shrank — and an agent on another thread had no happens-before edge that made a new length visible at all. The
     * compare-and-set loop below makes growth monotonic, and the volatile read makes it visible.
     */
    private final AtomicLong byteLength;
    private final boolean growable;
    private final long maxByteLength;

    /**
     * Create a SharedArrayBuffer with the specified byte length.
     *
     * @param byteLength
     *            The length in bytes
     */
    public JSSharedArrayBuffer(JSContext context, long byteLength) {
        this(context, byteLength, byteLength, false);
    }

    /**
     * Create a growable SharedArrayBuffer with the specified current and max lengths.
     *
     * @param byteLength
     *            The current length in bytes
     * @param maxByteLength
     *            The maximum length in bytes
     */
    public JSSharedArrayBuffer(JSContext context, long byteLength, long maxByteLength) {
        this(context, byteLength, maxByteLength, true);
    }

    /**
     * Allocate a shared data block.
     * <p>
     * A growable buffer reserves its maximum logical capacity against the runtime budget. Large stores commit pages
     * lazily, and each page keeps its identity for Atomics and other agents as the visible length grows.
     */
    private JSSharedArrayBuffer(JSContext context, long byteLength, long maxByteLength, boolean growable) {
        super(context);
        if (byteLength < 0) {
            throw new JSRangeErrorException("Invalid array buffer length");
        }
        if (maxByteLength < byteLength) {
            throw new JSRangeErrorException("Invalid array buffer max length");
        }
        if (growable && maxByteLength > JSArrayBuffer.MAX_RESIZABLE_DATA_BLOCK_BYTE_LENGTH) {
            throw new JSRangeErrorException("Invalid array buffer max length");
        }
        // Use heap pages so their byte[] storage is accessible for VarHandle atomics
        // Pad to multiple of 4 so VarHandle int-width CAS works for short-typed atomics
        long capacity = JSArrayBuffer.paddedCapacity(maxByteLength);
        JSMemoryAccounting accounting = context.getRuntime().getMemoryAccounting();
        JSMemoryAccounting.Reservation pendingReservation = accounting.reserve(this, capacity);
        if (pendingReservation == null) {
            throw new JSRangeErrorException("Shared array buffer allocation failed: the runtime memory limit of "
                    + accounting.getLimit() + " bytes would be exceeded");
        }
        JSByteBuffer allocated;
        try {
            allocated = JSByteBuffer.allocate(capacity);
        } catch (RuntimeException | Error allocationFailure) {
            // Reserving before allocating is deliberate, so a failed allocation has to hand the
            // bytes back rather than leave the runtime's ceiling inflated by a block that does not
            // exist.
            pendingReservation.release();
            throw allocationFailure;
        }
        this.buffer = allocated;
        this.buffer.order(ByteOrder.LITTLE_ENDIAN); // JavaScript uses little-endian
        this.byteLength = new AtomicLong(byteLength);
        this.maxByteLength = maxByteLength;
        this.growable = growable;
    }

    /**
     * Get the underlying JSByteBuffer. This is for internal use by TypedArrays, DataView, and Atomics.
     *
     * @return The shared byte storage
     */
    public JSByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * Get the byte length of this buffer. ES2017 24.2.4.1 get SharedArrayBuffer.prototype.byteLength
     *
     * @return The byte length
     */
    public long getByteLength() {
        return byteLength.get();
    }

    /**
     * Get the maximum byte length of this buffer.
     *
     * @return The maximum byte length
     */
    public long getMaxByteLength() {
        return maxByteLength;
    }

    /**
     * Grow the SharedArrayBuffer to the specified byte length. SharedArrayBuffers can only grow, never shrink.
     *
     * @param newByteLength
     *            The new byte length
     */
    public void grow(long newByteLength) {
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
            long currentByteLength = byteLength.get();
            if (newByteLength < currentByteLength) {
                throw new JSRangeErrorException("invalid array buffer length");
            }
            if (byteLength.compareAndSet(currentByteLength, newByteLength)) {
                return;
            }
        }
    }

    /**
     * Check if this SharedArrayBuffer is detached. SharedArrayBuffers cannot be detached.
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
     * Check if this buffer is a SharedArrayBuffer. Used to distinguish from regular ArrayBuffer.
     *
     * @return Always true
     */
    public boolean isShared() {
        return true;
    }

    /**
     * SharedArrayBuffer.prototype.slice(begin, end) ES2017 24.2.4.3 Returns a new SharedArrayBuffer with a copy of the
     * bytes from begin to end.
     *
     * @param begin
     *            Start offset (inclusive)
     * @param end
     *            End offset (exclusive)
     * @return A new SharedArrayBuffer
     */
    public JSSharedArrayBuffer slice(long begin, long end) {
        // One read of the shared length, so both ends are normalised against the same value.
        long currentByteLength = byteLength.get();

        begin = JSArrayBuffer.sliceIndex(begin, currentByteLength);
        end = JSArrayBuffer.sliceIndex(end, currentByteLength);
        long newLength = Math.max(end - begin, 0);

        // Create new buffer and copy bytes
        JSSharedArrayBuffer newBuffer = new JSSharedArrayBuffer(context, newLength);
        if (newLength > 0) {
            buffer.copyTo(newBuffer.getBuffer(), begin, 0, newLength);
        }

        return newBuffer;
    }

    @Override
    public String toString() {
        return "[object SharedArrayBuffer]";
    }

    public static JSObject create(JSContext context, JSValue... args) {
        try {
            ArrayBufferLengths lengths = ArrayBufferLengths.forSharedArrayBuffer(context, args);
            JSSharedArrayBuffer buffer = lengths.allocateShared(context);
            context.transferPrototype(buffer, NAME);
            return buffer;
        } catch (JSException e) {
            if (e.getErrorValue() instanceof JSObject errorObject) {
                return errorObject;
            }
            return context.throwRangeError("Invalid array buffer length");
        }
    }

    public static JSSharedArrayBuffer createForConstruct(JSContext context, JSFunction constructor, JSValue newTarget,
            JSValue... args) {
        ArrayBufferLengths lengths = ArrayBufferLengths.forSharedArrayBuffer(context, args);
        JSObject prototype = JSArrayBuffer.bufferPrototype(context, newTarget, NAME);
        JSSharedArrayBuffer buffer = lengths.allocateShared(context);
        JSArrayBuffer.setBufferPrototype(context, buffer, prototype, constructor, NAME);
        return buffer;
    }

}
