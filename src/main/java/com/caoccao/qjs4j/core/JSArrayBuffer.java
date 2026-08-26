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
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Represents a JavaScript ArrayBuffer object.
 * Based on ES2020 ArrayBuffer specification.
 * <p>
 * An ArrayBuffer is a raw binary data buffer of a fixed length.
 * It cannot be read or written directly - use TypedArrays or DataView.
 */
public final class JSArrayBuffer extends JSObject implements IJSArrayBuffer {
    public static final String NAME = "ArrayBuffer";
    /**
     * The largest data block the JVM can hold in one {@code byte[]}.
     * <p>
     * HotSpot refuses array lengths within a few words of {@link Integer#MAX_VALUE}, so a request
     * that survives the specification's own {@code INT32_MAX} check can still be unallocatable.
     * Rejecting it here makes that a {@code RangeError} the script can catch instead of an
     * {@code OutOfMemoryError} escaping as an internal engine failure.
     */
    static final int MAX_DATA_BLOCK_BYTE_LENGTH = Integer.MAX_VALUE - 8;
    private final int maxByteLength;
    private final boolean resizable;
    /**
     * Non-final: a resizable buffer allocates what it currently needs and reallocates on growth,
     * rather than reserving {@code maxByteLength} up front. Nothing in the engine holds the
     * {@code ByteBuffer} across a {@link #resize(int)} — typed arrays and {@code DataView} re-read
     * {@link #getBuffer()} per access — so replacing it is safe.
     */
    private ByteBuffer buffer;
    private boolean detached;
    private boolean immutable;
    private JSMemoryAccounting.Reservation reservation;

    /**
     * Create an ArrayBuffer with the specified byte length.
     *
     * @param byteLength The length in bytes
     */
    public JSArrayBuffer(JSContext context, int byteLength) {
        this(context, byteLength, -1);
    }

    /**
     * Create an ArrayBuffer with the specified byte length and max byte length.
     * <p>
     * A resizable buffer allocates its <em>current</em> length, not its maximum. Allocating
     * {@code maxByteLength} eagerly meant {@code new ArrayBuffer(1, {maxByteLength: 1 << 25})} cost
     * 32 MiB the moment it was constructed, whether or not the script ever grew it.
     *
     * @param byteLength    The initial length in bytes
     * @param maxByteLength The maximum length in bytes, or -1 for non-resizable
     */
    public JSArrayBuffer(JSContext context, int byteLength, int maxByteLength) {
        super(context);
        if (byteLength < 0) {
            throw new JSRangeErrorException("ArrayBuffer byteLength must be non-negative");
        }
        if (maxByteLength != -1 && maxByteLength < byteLength) {
            throw new JSRangeErrorException("ArrayBuffer maxByteLength must be >= byteLength");
        }
        this.resizable = (maxByteLength != -1);
        this.maxByteLength = (maxByteLength != -1) ? maxByteLength : byteLength;
        int capacity = paddedCapacity(byteLength);
        this.buffer = allocateAccountedBlock(context, capacity);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN); // JavaScript uses little-endian
        this.buffer.limit(byteLength);
        this.detached = false;
    }

    /**
     * Create an ArrayBuffer from an existing byte array.
     *
     * @param bytes The byte array to wrap
     */
    public JSArrayBuffer(JSContext context, byte[] bytes) {
        super(context);
        // Pad to multiple of 4 so VarHandle int-width CAS works for short-typed atomics
        int allocSize = paddedCapacity(bytes.length);
        if (allocSize > bytes.length) {
            this.buffer = accountBlock(context, allocSize, () -> {
                byte[] padded = new byte[allocSize];
                System.arraycopy(bytes, 0, padded, 0, bytes.length);
                return ByteBuffer.wrap(padded);
            });
            this.buffer.limit(bytes.length);
        } else {
            this.buffer = accountBlock(context, allocSize, () -> ByteBuffer.wrap(bytes));
        }
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.detached = false;
        this.resizable = false;
        this.maxByteLength = bytes.length;
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
            return new JSArrayBuffer(context, byteLength, (int) maxByteLengthLong);
        } else {
            return new JSArrayBuffer(context, byteLength);
        }
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
            resolvedPrototype = context.getPrototypeFromConstructor(newTargetObject, NAME);
            if (context.hasPendingException()) {
                throw new JSException(context.getPendingException());
            }
        }

        // Step 3: Allocation limit check + buffer creation (CreateByteDataBlock)
        JSArrayBuffer buf = allocateBuffer(context, validated[0], validated[1]);

        // Set prototype
        if (resolvedPrototype != null) {
            buf.setPrototype(resolvedPrototype);
        } else if (constructor != null) {
            JSObject constructorPrototype = context.getPrototypeFromConstructor(constructor, NAME);
            if (context.hasPendingException()) {
                throw new JSException(context.getPendingException());
            }
            if (constructorPrototype != null) {
                buf.setPrototype(constructorPrototype);
            }
        }
        return buf;
    }

    /**
     * The allocation size for a logical byte length, padded to a multiple of four.
     * <p>
     * The padding lets {@code Atomics} operate on 16-bit views through the enclosing aligned
     * 32-bit word. Computed in {@code long}: {@code (byteLength + 3) & ~3} on an {@code int} wraps
     * to {@link Integer#MIN_VALUE} at {@link Integer#MAX_VALUE}, which reached
     * {@code ByteBuffer.allocate} as {@code IllegalArgumentException: capacity < 0} — an internal
     * failure no {@code try}/{@code catch} could see, at a length the allocation check explicitly
     * permits.
     *
     * @param byteLength the logical length
     * @return the padded capacity
     * @throws JSRangeErrorException when the padded size cannot be allocated
     */
    static int paddedCapacity(int byteLength) {
        long padded = ((long) byteLength + 3L) & ~3L;
        if (padded > MAX_DATA_BLOCK_BYTE_LENGTH) {
            throw new JSRangeErrorException("Invalid array buffer length");
        }
        return (int) padded;
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
            JSValue maxByteLengthValue = options.get(PropertyKey.fromString("maxByteLength"));
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
     * Reserve capacity, produce the block, and only then bind the reservation to this buffer.
     * <p>
     * The reservation has to be taken before the allocation — refusing after the memory is already
     * committed would defeat the point — so the failing path has to give it back. It used to reserve
     * and register unconditionally: a JVM allocation failure left the bytes charged until the
     * half-constructed buffer was collected, and until then a runtime's ceiling was inflated by an
     * allocation that never happened.
     *
     * @param context   the owning context
     * @param capacity  the number of bytes being charged
     * @param allocator produces the block
     * @return the allocated block
     * @throws JSRangeErrorException when the runtime's limit would be exceeded
     */
    private ByteBuffer accountBlock(JSContext context, int capacity, Supplier<ByteBuffer> allocator) {
        JSMemoryAccounting accounting = context.getRuntime().getMemoryAccounting();
        if (!accounting.reserve(capacity)) {
            throw new JSRangeErrorException(
                    "Array buffer allocation failed: the runtime memory limit of "
                            + accounting.getLimit() + " bytes would be exceeded");
        }
        ByteBuffer allocated;
        try {
            allocated = allocator.get();
        } catch (RuntimeException | Error allocationFailure) {
            accounting.release(capacity);
            throw allocationFailure;
        }
        this.reservation = accounting.registerReservation(this, capacity);
        return allocated;
    }

    /**
     * Charge a data block against the runtime's memory accounting and allocate it.
     *
     * @param context  the owning context
     * @param capacity the number of bytes to allocate
     * @return the allocated block
     * @throws JSRangeErrorException when the runtime's limit would be exceeded
     */
    private ByteBuffer allocateAccountedBlock(JSContext context, int capacity) {
        return accountBlock(context, capacity, () -> ByteBuffer.allocate(capacity));
    }

    /**
     * Detach this ArrayBuffer, making it unusable.
     * ES2020 24.1.1.3
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
     * Resize the ArrayBuffer to the specified size.
     * ES2024 25.1.5.3
     * <p>
     * Detached and non-resizable are receiver-state conditions, so they are {@code TypeError}s
     * per ES2024 25.1.6.7 steps 3-4; only an out-of-range requested length is a {@code RangeError}
     * (step 6). The JavaScript built-in prechecks both, which is why the wrong type on this direct
     * Java API was invisible from script.
     *
     * @param newByteLength The new byte length
     * @throws JSTypeErrorException  if the buffer is detached or not resizable
     * @throws JSRangeErrorException if newByteLength is negative or exceeds maxByteLength
     */
    public void resize(int newByteLength) {
        if (detached) {
            throw new JSTypeErrorException("Cannot resize a detached ArrayBuffer");
        }
        if (!resizable) {
            throw new JSTypeErrorException("Cannot resize a non-resizable ArrayBuffer");
        }
        if (newByteLength < 0 || newByteLength > maxByteLength) {
            throw new JSRangeErrorException("New byte length must be between 0 and " + maxByteLength);
        }

        int oldByteLength = buffer.limit();
        if (newByteLength > buffer.capacity()) {
            // Growing past what was allocated. The reservation grows with the allocation, so a
            // buffer declared with a large maxByteLength is charged as it actually grows rather
            // than all at once — and can still be refused if the runtime limit is reached.
            int newCapacity = paddedCapacity(newByteLength);
            int additionalBytes = newCapacity - buffer.capacity();
            if (reservation != null && !reservation.grow(additionalBytes)) {
                JSMemoryAccounting accounting = context.getRuntime().getMemoryAccounting();
                throw new JSRangeErrorException(
                        "Array buffer resize failed: the runtime memory limit of "
                                + accounting.getLimit() + " bytes would be exceeded");
            }
            byte[] grown;
            try {
                grown = Arrays.copyOf(buffer.array(), newCapacity);
            } catch (RuntimeException | Error allocationFailure) {
                // The reservation was grown before the copy, so a failed copy has to give the
                // bytes back. Leaving them charged kept the old buffer alive with a reservation
                // sized for a block that was never allocated — phantom bytes for the runtime's
                // whole life, since nothing later releases them.
                if (reservation != null) {
                    reservation.shrink(additionalBytes);
                }
                throw allocationFailure;
            }
            // Bytes between the old length and the old capacity can hold data from before an
            // earlier shrink; ES2024 requires newly accessible bytes to read as zero.
            Arrays.fill(grown, oldByteLength, newByteLength, (byte) 0);
            ByteBuffer replacement = ByteBuffer.wrap(grown);
            replacement.order(ByteOrder.LITTLE_ENDIAN);
            replacement.limit(newByteLength);
            this.buffer = replacement;
            return;
        }
        buffer.limit(newByteLength);
        if (newByteLength > oldByteLength) {
            // Zero newly accessible bytes per ES2024 spec
            Arrays.fill(buffer.array(), oldByteLength, newByteLength, (byte) 0);
        }
    }

    /**
     * Mark this ArrayBuffer as immutable.
     */
    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    /**
     * ArrayBuffer.prototype.slice(begin, end)
     * ES2020 24.1.4.3
     * Returns a new ArrayBuffer with a copy of the bytes from begin to end.
     *
     * @param context the owning context
     * @param begin   Start offset (inclusive)
     * @param end     End offset (exclusive)
     * @return A new ArrayBuffer
     * @throws JSTypeErrorException if the buffer is detached
     */
    public JSArrayBuffer slice(JSContext context, int begin, int end) {
        if (detached) {
            throw new JSTypeErrorException("Cannot slice a detached ArrayBuffer");
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

        // Create new buffer with proper prototype and copy bytes
        JSArrayBuffer newBuffer = context.createJSArrayBuffer(newLength);
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
     * @throws JSTypeErrorException  if the buffer is already detached
     * @throws JSRangeErrorException if newByteLength is negative
     */
    public JSArrayBuffer transfer(JSContext context, int newByteLength) {
        if (detached) {
            throw new JSTypeErrorException("Cannot transfer a detached ArrayBuffer");
        }

        int currentLength = getByteLength();
        int targetLength = (newByteLength == -1) ? currentLength : newByteLength;

        // An invalid length is a range condition, not a receiver-state one.
        if (targetLength < 0) {
            throw new JSRangeErrorException("New byte length must be non-negative");
        }

        // Create new buffer with proper prototype, preserving resizability
        JSArrayBuffer newBuffer = context.createJSArrayBuffer(targetLength, resizable ? maxByteLength : -1);

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
     * @throws JSTypeErrorException  if the buffer is already detached
     * @throws JSRangeErrorException if newByteLength is negative
     */
    public JSArrayBuffer transferToFixedLength(JSContext context, int newByteLength) {
        if (detached) {
            throw new JSTypeErrorException("Cannot transfer a detached ArrayBuffer");
        }

        int currentLength = getByteLength();
        int targetLength = (newByteLength == -1) ? currentLength : newByteLength;

        // An invalid length is a range condition, not a receiver-state one.
        if (targetLength < 0) {
            throw new JSRangeErrorException("New byte length must be non-negative");
        }

        // Create new fixed-length buffer with proper prototype
        JSArrayBuffer newBuffer = context.createJSArrayBuffer(targetLength);

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
     * Transfer the contents to a new immutable ArrayBuffer and detach this buffer.
     * ES2025 ArrayBuffer.prototype.transferToImmutable
     *
     * @param context the owning context
     * @return A new immutable ArrayBuffer with the transferred contents
     * @throws JSTypeErrorException if the buffer is already detached
     */
    public JSArrayBuffer transferToImmutable(JSContext context) {
        if (detached) {
            throw new JSTypeErrorException("Cannot transfer a detached ArrayBuffer");
        }

        int currentLength = getByteLength();

        // Create new fixed-length buffer with proper prototype
        JSArrayBuffer newBuffer = context.createJSArrayBuffer(currentLength);

        // Copy data
        if (currentLength > 0) {
            byte[] bytes = new byte[currentLength];
            int oldPosition = buffer.position();
            buffer.position(0);
            buffer.get(bytes, 0, currentLength);
            buffer.position(oldPosition);
            newBuffer.getBuffer().put(bytes);
            newBuffer.getBuffer().position(0);
        }

        // Mark as immutable
        newBuffer.immutable = true;

        // Detach this buffer
        detach();

        return newBuffer;
    }
}
