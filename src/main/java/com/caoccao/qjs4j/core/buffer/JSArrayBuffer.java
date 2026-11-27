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

package com.caoccao.qjs4j.core.buffer;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

import java.nio.ByteOrder;
import java.util.function.Supplier;

/**
 * Represents a JavaScript ArrayBuffer object. Based on ES2020 ArrayBuffer specification.
 * <p>
 * An ArrayBuffer is a raw binary data buffer of a fixed length. It cannot be read or written directly - use TypedArrays
 * or DataView.
 */
public final class JSArrayBuffer extends JSObject implements IJSArrayBuffer {
    /** Matches the 64-bit sandboxed V8 buffer ceiling (32 GiB - 1), independent of the host budget. */
    static final long MAX_DATA_BLOCK_BYTE_LENGTH = (1L << 35) - 1;
    static final long MAX_RESIZABLE_DATA_BLOCK_BYTE_LENGTH;
    public static final String NAME = "ArrayBuffer";

    /**
     * V8 rounds resizable reservations to allocation pages before checking its sandbox ceiling. Windows uses its 64 KiB
     * allocation granularity, macOS arm64 uses 16 KiB, and the other standard Javet hosts use 4 KiB. This compatibility
     * quantum is independent of JSByteBuffer's lazy storage page size.
     */
    static final int V8_ALLOCATION_PAGE_SIZE;

    static {
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Windows")) {
            V8_ALLOCATION_PAGE_SIZE = 64 * 1024;
        } else if (osName.startsWith("Mac") && System.getProperty("os.arch").equals("aarch64")) {
            V8_ALLOCATION_PAGE_SIZE = 16 * 1024;
        } else {
            V8_ALLOCATION_PAGE_SIZE = 4 * 1024;
        }
        MAX_RESIZABLE_DATA_BLOCK_BYTE_LENGTH = MAX_DATA_BLOCK_BYTE_LENGTH
                - MAX_DATA_BLOCK_BYTE_LENGTH % V8_ALLOCATION_PAGE_SIZE;
    }
    /**
     * Non-final: a resizable buffer allocates what it currently needs and reallocates on growth, rather than reserving
     * {@code maxByteLength} up front. Nothing in the engine holds the {@code JSByteBuffer} across a
     * {@link #resize(long)} — typed arrays and {@code DataView} re-read {@link #getBuffer()} per access — so replacing
     * it is safe.
     */
    private JSByteBuffer buffer;
    private boolean detached;
    private boolean immutable;
    private final long maxByteLength;
    private JSMemoryAccounting.Reservation reservation;
    private final boolean resizable;

    /**
     * Create an ArrayBuffer from an existing byte array.
     *
     * @param bytes
     *            The byte array to wrap
     */
    public JSArrayBuffer(JSContext context, byte[] bytes) {
        super(context);
        // Pad to multiple of 4 so VarHandle int-width CAS works for short-typed atomics
        long allocSize = paddedCapacity(bytes.length);
        if (allocSize > bytes.length) {
            this.buffer = accountBlock(context, allocSize, () -> {
                JSByteBuffer padded = JSByteBuffer.allocate(allocSize);
                padded.put(bytes).position(0);
                return padded;
            });
            this.buffer.limit(bytes.length);
        } else {
            this.buffer = accountBlock(context, allocSize, () -> JSByteBuffer.wrap(bytes));
        }
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.detached = false;
        this.resizable = false;
        this.maxByteLength = bytes.length;
    }

    /**
     * Create an ArrayBuffer with the specified byte length.
     *
     * @param byteLength
     *            The length in bytes
     */
    public JSArrayBuffer(JSContext context, long byteLength) {
        this(context, byteLength, -1);
    }

    /**
     * Create an ArrayBuffer with the specified byte length and max byte length.
     * <p>
     * A resizable buffer allocates its <em>current</em> length, not its maximum. Allocating {@code maxByteLength}
     * eagerly meant {@code new ArrayBuffer(1, {maxByteLength: 1 << 25})} cost 32 MiB the moment it was constructed,
     * whether or not the script ever grew it.
     *
     * @param byteLength
     *            The initial length in bytes
     * @param maxByteLength
     *            The maximum length in bytes, or -1 for non-resizable
     */
    public JSArrayBuffer(JSContext context, long byteLength, long maxByteLength) {
        super(context);
        if (byteLength < 0) {
            throw new JSRangeErrorException("ArrayBuffer byteLength must be non-negative");
        }
        if (maxByteLength != -1 && maxByteLength < byteLength) {
            throw new JSRangeErrorException("ArrayBuffer maxByteLength must be >= byteLength");
        }
        if (maxByteLength > MAX_RESIZABLE_DATA_BLOCK_BYTE_LENGTH) {
            throw new JSRangeErrorException("Invalid array buffer max length");
        }
        this.resizable = (maxByteLength != -1);
        this.maxByteLength = (maxByteLength != -1) ? maxByteLength : byteLength;
        long capacity = paddedCapacity(byteLength);
        this.buffer = allocateAccountedBlock(context, capacity);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN); // JavaScript uses little-endian
        this.buffer.limit(byteLength);
        this.detached = false;
    }

    /**
     * Reserve capacity, produce the block, and only then bind the reservation to this buffer.
     * <p>
     * The reservation has to be taken before the allocation — refusing after the memory is already committed would
     * defeat the point — so the failing path has to give it back. It used to reserve and register unconditionally: a
     * JVM allocation failure left the bytes charged until the half-constructed buffer was collected, and until then a
     * runtime's ceiling was inflated by an allocation that never happened.
     *
     * <p>
     * Package-private so a test can hand it an allocator that fails on demand. Reaching that path through the JVM's own
     * allocation limit needs a request larger than the heap, which makes the test's premise a property of the build's
     * {@code -Xmx} rather than of the code under test.
     *
     * @param context
     *            the owning context
     * @param capacity
     *            the number of bytes being charged
     * @param allocator
     *            produces the block
     * @return the allocated block
     * @throws JSRangeErrorException
     *             when the runtime's limit would be exceeded
     */
    JSByteBuffer accountBlock(JSContext context, long capacity, Supplier<JSByteBuffer> allocator) {
        JSMemoryAccounting accounting = context.getRuntime().getMemoryAccounting();
        JSMemoryAccounting.Reservation pendingReservation = accounting.reserve(this, capacity);
        if (pendingReservation == null) {
            throw new JSRangeErrorException("Array buffer allocation failed: the runtime memory limit of "
                    + accounting.getLimit() + " bytes would be exceeded");
        }
        JSByteBuffer allocated;
        try {
            allocated = allocator.get();
        } catch (RuntimeException | Error allocationFailure) {
            pendingReservation.release();
            throw allocationFailure;
        }
        this.reservation = pendingReservation;
        return allocated;
    }

    /**
     * Charge a data block against the runtime's memory accounting and allocate it.
     *
     * @param context
     *            the owning context
     * @param capacity
     *            the number of bytes to allocate
     * @return the allocated block
     * @throws JSRangeErrorException
     *             when the runtime's limit would be exceeded
     */
    private JSByteBuffer allocateAccountedBlock(JSContext context, long capacity) {
        return accountBlock(context, capacity, () -> JSByteBuffer.allocate(capacity));
    }

    /**
     * Detach this ArrayBuffer, making it unusable. ES2020 24.1.1.3
     */
    public void detach() {
        this.detached = true;
        this.buffer = null;
        // Give the reservation back now rather than when the collector gets round to the buffer:
        // a detach is the point at which the data block is unreachable by definition.
        if (reservation != null) {
            reservation.release();
        }
    }

    /**
     * Get the underlying JSByteBuffer. This is for internal use by TypedArrays and DataView.
     *
     * @return The JSByteBuffer, or null if detached
     */
    public JSByteBuffer getBuffer() {
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
    public long getByteLength() {
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
    public long getMaxByteLength() {
        return maxByteLength;
    }

    /**
     * Grow this buffer's reservation, produce the larger block, and give the growth back if producing it fails.
     * <p>
     * The reservation grows before the copy, so a buffer declared with a large {@code maxByteLength} is charged as it
     * actually grows rather than all at once — and can still be refused when the runtime limit is reached. A failed
     * copy therefore has to hand the growth back: leaving it charged kept the old buffer alive with a reservation sized
     * for a block that was never allocated, phantom bytes for the runtime's whole life since nothing later releases
     * them.
     * <p>
     * Package-private for the same reason as {@link #accountBlock}: a test can hand it an allocator that fails on
     * demand instead of asking for more memory than the JVM has.
     *
     * @param additionalBytes
     *            how much bigger the block is becoming
     * @param allocator
     *            produces the larger block
     * @return the larger block
     * @throws JSRangeErrorException
     *             when the runtime's limit would be exceeded
     */
    <T> T growAccountedBlock(long additionalBytes, Supplier<T> allocator) {
        if (reservation != null && !reservation.grow(additionalBytes)) {
            JSMemoryAccounting accounting = context.getRuntime().getMemoryAccounting();
            throw new JSRangeErrorException("Array buffer resize failed: the runtime memory limit of "
                    + accounting.getLimit() + " bytes would be exceeded");
        }
        try {
            return allocator.get();
        } catch (RuntimeException | Error allocationFailure) {
            if (reservation != null) {
                reservation.shrink(additionalBytes);
            }
            throw allocationFailure;
        }
    }

    /**
     * Check if this ArrayBuffer is detached.
     *
     * @return true if detached, false otherwise
     */
    public boolean isDetached() {
        return detached;
    }

    @Override
    public boolean isImmutable() {
        return immutable;
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
     * Resize the ArrayBuffer to the specified size. ES2024 25.1.5.3
     * <p>
     * Detached and non-resizable are receiver-state conditions, so they are {@code TypeError}s per ES2024 25.1.6.7
     * steps 3-4; only an out-of-range requested length is a {@code RangeError} (step 6). The JavaScript built-in
     * prechecks both, which is why the wrong type on this direct Java API was invisible from script.
     *
     * @param newByteLength
     *            The new byte length
     * @throws JSTypeErrorException
     *             if the buffer is detached or not resizable
     * @throws JSRangeErrorException
     *             if newByteLength is negative or exceeds maxByteLength
     */
    public void resize(long newByteLength) {
        if (detached) {
            throw new JSTypeErrorException("Cannot resize a detached ArrayBuffer");
        }
        if (!resizable) {
            throw new JSTypeErrorException("Cannot resize a non-resizable ArrayBuffer");
        }
        if (newByteLength < 0 || newByteLength > maxByteLength) {
            throw new JSRangeErrorException("New byte length must be between 0 and " + maxByteLength);
        }

        long oldByteLength = buffer.limit();
        if (newByteLength > buffer.capacity()) {
            long newCapacity = paddedCapacity(newByteLength);
            JSByteBuffer replacement = growAccountedBlock(newCapacity - buffer.capacity(), () -> {
                JSByteBuffer grown = JSByteBuffer.allocate(newCapacity).order(ByteOrder.LITTLE_ENDIAN);
                buffer.copyTo(grown, 0, 0, oldByteLength);
                return grown;
            });
            buffer = replacement.limit(newByteLength);
        } else {
            if (newByteLength < oldByteLength) {
                buffer.clearRange(newByteLength, oldByteLength);
            }
            buffer.limit(newByteLength);
        }
    }

    /**
     * Mark this ArrayBuffer as immutable.
     */
    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    /**
     * ArrayBuffer.prototype.slice(begin, end) ES2020 24.1.4.3 Returns a new ArrayBuffer with a copy of the bytes from
     * begin to end.
     *
     * @param context
     *            the owning context
     * @param begin
     *            Start offset (inclusive)
     * @param end
     *            End offset (exclusive)
     * @return A new ArrayBuffer
     * @throws JSTypeErrorException
     *             if the buffer is detached
     */
    public JSArrayBuffer slice(JSContext context, long begin, long end) {
        if (detached) {
            throw new JSTypeErrorException("Cannot slice a detached ArrayBuffer");
        }

        long byteLength = getByteLength();

        begin = sliceIndex(begin, byteLength);
        end = sliceIndex(end, byteLength);
        long newLength = Math.max(end - begin, 0);

        // Create new buffer with proper prototype and copy bytes
        JSArrayBuffer newBuffer = context.createJSArrayBuffer(newLength);
        if (newLength > 0) {
            buffer.copyTo(newBuffer.getBuffer(), begin, 0, newLength);
        }

        return newBuffer;
    }

    @Override
    public String toString() {
        return "[object ArrayBuffer]";
    }

    /**
     * Transfer the contents to a new ArrayBuffer and detach this buffer. ES2024 25.1.5.4
     *
     * @param newByteLength
     *            The byte length of the new buffer, or -1 to use current length
     * @return A new ArrayBuffer with the transferred contents
     * @throws JSTypeErrorException
     *             if the buffer is already detached
     * @throws JSRangeErrorException
     *             if newByteLength is negative
     */
    public JSArrayBuffer transfer(JSContext context, long newByteLength) {
        return transferContents(context, newByteLength, true, false);
    }

    private JSArrayBuffer transferContents(JSContext context, long newByteLength, boolean preserveResizability,
            boolean immutableResult) {
        if (detached) {
            throw new JSTypeErrorException("Cannot transfer a detached ArrayBuffer");
        }

        long currentLength = getByteLength();
        long targetLength = (newByteLength == -1) ? currentLength : newByteLength;

        // An invalid length is a range condition, not a receiver-state one.
        if (targetLength < 0) {
            throw new JSRangeErrorException("New byte length must be non-negative");
        }

        // Create new buffer with proper prototype, preserving resizability
        JSArrayBuffer newBuffer = context.createJSArrayBuffer(targetLength,
                preserveResizability && resizable ? maxByteLength : -1);

        // Copy data up to the minimum of current and target length
        long copyLength = Math.min(currentLength, targetLength);
        if (copyLength > 0) {
            buffer.copyTo(newBuffer.getBuffer(), 0, 0, copyLength);
        }

        newBuffer.immutable = immutableResult;

        // Detach only after allocation and copying succeed.
        detach();

        return newBuffer;
    }

    /**
     * Transfer the contents to a new fixed-length ArrayBuffer and detach this buffer. ES2024 25.1.5.5
     *
     * @param newByteLength
     *            The byte length of the new buffer, or -1 to use current length
     * @return A new non-resizable ArrayBuffer with the transferred contents
     * @throws JSTypeErrorException
     *             if the buffer is already detached
     * @throws JSRangeErrorException
     *             if newByteLength is negative
     */
    public JSArrayBuffer transferToFixedLength(JSContext context, long newByteLength) {
        return transferContents(context, newByteLength, false, false);
    }

    /**
     * Transfer the contents to a new immutable ArrayBuffer and detach this buffer. ES2025
     * ArrayBuffer.prototype.transferToImmutable
     *
     * @param context
     *            the owning context
     * @return A new immutable ArrayBuffer with the transferred contents
     * @throws JSTypeErrorException
     *             if the buffer is already detached
     */
    public JSArrayBuffer transferToImmutable(JSContext context) {
        return transferContents(context, -1, false, true);
    }

    static JSObject bufferPrototype(JSContext context, JSValue target, String name) {
        if (!(target instanceof JSObject constructor)) {
            return null;
        }
        JSObject prototype = context.getPrototypeFromConstructor(constructor, name);
        if (context.hasPendingException()) {
            throw new JSException(context.getPendingException());
        }
        return prototype;
    }

    /**
     * ArrayBuffer constructor implementation. new ArrayBuffer(byteLength) new ArrayBuffer(byteLength, options)
     * <p>
     * Based on ES2020 24.1.1.1
     */
    public static JSArrayBuffer create(JSContext context, JSValue... args) {
        ArrayBufferLengths lengths = ArrayBufferLengths.forArrayBuffer(context, args);
        return lengths.allocate(context);
    }

    /**
     * ArrayBuffer constructor with newTarget support for Reflect.construct. Follows QuickJS
     * js_array_buffer_constructor0/3 ordering: 1. Argument validation (ToIndex, options, byteLength > maxByteLength) 2.
     * OrdinaryCreateFromConstructor (accesses newTarget.prototype) 3. CreateByteDataBlock (allocation limit check +
     * buffer creation)
     */
    public static JSArrayBuffer createForConstruct(JSContext context, JSFunction constructor, JSValue newTarget,
            JSValue... args) {
        ArrayBufferLengths lengths = ArrayBufferLengths.forArrayBuffer(context, args);
        JSObject prototype = bufferPrototype(context, newTarget, NAME);
        JSArrayBuffer buffer = lengths.allocate(context);
        setBufferPrototype(context, buffer, prototype, constructor, NAME);
        return buffer;
    }

    /** Pad to an aligned 32-bit atomic word without narrowing the logical length. */
    static long paddedCapacity(long byteLength) {
        if (byteLength < 0 || byteLength > MAX_DATA_BLOCK_BYTE_LENGTH) {
            throw new JSRangeErrorException("Invalid array buffer length");
        }
        return (byteLength + 3L) & ~3L;
    }

    static void setBufferPrototype(JSContext context, JSObject buffer, JSObject prototype, JSFunction constructor,
            String name) {
        if (prototype == null) {
            prototype = bufferPrototype(context, constructor, name);
        }
        if (prototype != null) {
            buffer.setPrototype(prototype);
        }
    }

    static long sliceIndex(long relative, long length) {
        return relative < 0 ? Math.max(length + relative, 0) : Math.min(relative, length);
    }

}
