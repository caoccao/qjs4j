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

import com.caoccao.qjs4j.utils.ByteArrayAtomics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A long-indexed byte-buffer view. Small data blocks use contiguous JVM storage; large blocks allocate zero-filled
 * pages on first write. Duplicate and sliced views share storage, including atomic pages. Logical capacity is charged
 * by the owning ArrayBuffer before this class is allocated.
 */
public final class JSByteBuffer {
    /** One MiB keeps page-table overhead low while bounding the cost of a sparse write. */
    public static final int PAGE_SIZE = 1 << 20;
    private final long base;
    private final long capacity;
    private final ByteBuffer contiguous;
    private long limit;
    private ByteOrder order = ByteOrder.BIG_ENDIAN;
    private long position;
    private final Store store;

    private JSByteBuffer(Store store, long base, long capacity) {
        this.store = store;
        this.base = base;
        this.capacity = capacity;
        this.limit = capacity;
        contiguous = store.bytes == null ? null : ByteBuffer.wrap(store.bytes, (int) base, (int) capacity).slice();
    }

    public byte[] array() {
        if (store.bytes == null) {
            throw new UnsupportedOperationException("Segmented storage has no single array");
        }
        return store.bytes;
    }

    /** The stable page containing index; intended for aligned VarHandle atomics. */
    public byte[] arrayAt(long index) {
        check(index, 1);
        if (store.bytes != null) {
            return store.bytes;
        }
        return store.pages.computeIfAbsent((base + index) / PAGE_SIZE, ignored -> new byte[PAGE_SIZE]);
    }

    /** Byte offset within arrayAt(index), including the base of a sliced view. */
    public int arrayOffsetAt(long index) {
        check(index, 1);
        return (int) (store.bytes != null ? base + index : (base + index) % PAGE_SIZE);
    }

    public long capacity() {
        return capacity;
    }

    private void check(long index, long size) {
        if (index < 0 || size < 0 || index > limit - size) {
            throw new IndexOutOfBoundsException();
        }
    }

    /** Zero a range without materializing unallocated pages. */
    public void clearRange(long start, long end) {
        check(start, end - start);
        if (store.bytes != null) {
            Arrays.fill(store.bytes, (int) (base + start), (int) (base + end), (byte) 0);
            return;
        }
        store.forEachPage(base + start, base + end, (from, to, page) -> {
            if (page != null) {
                int offset = (int) (from % PAGE_SIZE);
                Arrays.fill(page, offset, offset + (int) (to - from), (byte) 0);
            }
        });
    }

    /**
     * Copy a range, writing each target byte exactly once: a materialized source page is copied over the target and a
     * source hole zeroes its part of the target. A concurrent reader of a shared block therefore only ever sees bytes
     * the source held — never a zero from a clearing pass that a later write would overwrite. Overlaps within one block
     * go through a temporary sparse block, like memmove.
     */
    public void copyTo(JSByteBuffer target, long sourceIndex, long targetIndex, long count) {
        check(sourceIndex, count);
        target.check(targetIndex, count);
        if (count == 0) {
            return;
        }
        if (store.bytes != null && target.store.bytes != null) {
            System.arraycopy(store.bytes, (int) (base + sourceIndex), target.store.bytes,
                    (int) (target.base + targetIndex), (int) count);
            return;
        }
        if (store == target.store) {
            JSByteBuffer temporary = allocate(count);
            copyTo(temporary, sourceIndex, 0, count);
            temporary.copyTo(target, 0, targetIndex, count);
            return;
        }
        if (store.bytes != null) {
            target.write(targetIndex, store.bytes, (int) (base + sourceIndex), (int) count);
            return;
        }
        long start = base + sourceIndex;
        store.forEachPage(start, start + count, (from, to, page) -> {
            long destination = targetIndex + from - start;
            if (page == null) {
                target.clearRange(destination, destination + to - from);
            } else {
                target.write(destination, page, (int) (from % PAGE_SIZE), (int) (to - from));
            }
        });
    }

    public JSByteBuffer duplicate() {
        return new JSByteBuffer(store, base, capacity).limit(limit).position(position).order(order);
    }

    public JSByteBuffer get(byte[] target) {
        return get(target, 0, target.length);
    }

    public JSByteBuffer get(byte[] target, int offset, int count) {
        check(position, count);
        java.util.Objects.checkFromIndexSize(offset, count, target.length);
        long index = base + position;
        int remaining = count;
        while (remaining > 0) {
            byte[] source = store.bytes != null ? store.bytes : store.pages.get(index / PAGE_SIZE);
            int sourceOffset = (int) (store.bytes != null ? index : index % PAGE_SIZE);
            int chunk = Math.min(remaining, (store.bytes != null ? store.bytes.length : PAGE_SIZE) - sourceOffset);
            if (source == null) {
                Arrays.fill(target, offset, offset + chunk, (byte) 0);
            } else {
                System.arraycopy(source, sourceOffset, target, offset, chunk);
            }
            index += chunk;
            offset += chunk;
            remaining -= chunk;
        }
        position += count;
        return this;
    }

    public byte get(long index) {
        check(index, 1);
        if (contiguous != null) {
            return contiguous.get((int) index);
        }
        long absolute = base + index;
        byte[] page = store.pages.get(absolute / PAGE_SIZE);
        return page == null ? 0 : page[(int) (absolute % PAGE_SIZE)];
    }

    public double getDouble(long index) {
        check(index, 8);
        return contiguous == null ? Double.longBitsToDouble(readBits(index, 8)) : contiguous.getDouble((int) index);
    }

    public float getFloat(long index) {
        check(index, 4);
        return contiguous == null ? Float.intBitsToFloat((int) readBits(index, 4)) : contiguous.getFloat((int) index);
    }

    public int getInt(long index) {
        check(index, 4);
        return contiguous == null ? (int) readBits(index, 4) : contiguous.getInt((int) index);
    }

    public long getLong(long index) {
        check(index, 8);
        return contiguous == null ? readBits(index, 8) : contiguous.getLong((int) index);
    }

    public short getShort(long index) {
        check(index, 2);
        return contiguous == null ? (short) readBits(index, 2) : contiguous.getShort((int) index);
    }

    public long limit() {
        return limit;
    }

    public JSByteBuffer limit(long value) {
        if (value < 0 || value > capacity) {
            throw new IndexOutOfBoundsException();
        }
        limit = value;
        position = Math.min(position, value);
        return this;
    }

    public ByteOrder order() {
        return order;
    }

    public JSByteBuffer order(ByteOrder value) {
        order = value;
        if (contiguous != null) {
            contiguous.order(value);
        }
        return this;
    }

    public long position() {
        return position;
    }

    public JSByteBuffer position(long value) {
        if (value < 0 || value > limit) {
            throw new IndexOutOfBoundsException();
        }
        position = value;
        return this;
    }

    public JSByteBuffer put(byte[] source) {
        return put(source, 0, source.length);
    }

    public JSByteBuffer put(byte[] source, int offset, int count) {
        check(position, count);
        write(position, source, offset, count);
        position += count;
        return this;
    }

    public JSByteBuffer put(JSByteBuffer source) {
        if (source == this) {
            throw new IllegalArgumentException("Cannot copy a buffer into itself");
        }
        long count = source.remaining();
        source.copyTo(this, source.position, position, count);
        source.position += count;
        position += count;
        return this;
    }

    public JSByteBuffer put(long index, byte value) {
        check(index, 1);
        if (contiguous != null) {
            contiguous.put((int) index, value);
        } else {
            arrayAt(index)[arrayOffsetAt(index)] = value;
        }
        return this;
    }

    public JSByteBuffer putDouble(long index, double value) {
        check(index, 8);
        if (contiguous == null) {
            writeBits(index, Double.doubleToRawLongBits(value), 8);
        } else {
            contiguous.putDouble((int) index, value);
        }
        return this;
    }

    public JSByteBuffer putFloat(long index, float value) {
        check(index, 4);
        if (contiguous == null) {
            writeBits(index, Float.floatToRawIntBits(value), 4);
        } else {
            contiguous.putFloat((int) index, value);
        }
        return this;
    }

    public JSByteBuffer putInt(long index, int value) {
        check(index, 4);
        if (contiguous == null) {
            writeBits(index, value, 4);
        } else {
            contiguous.putInt((int) index, value);
        }
        return this;
    }

    public JSByteBuffer putLong(long index, long value) {
        check(index, 8);
        if (contiguous == null) {
            writeBits(index, value, 8);
        } else {
            contiguous.putLong((int) index, value);
        }
        return this;
    }

    public JSByteBuffer putShort(long index, short value) {
        check(index, 2);
        if (contiguous == null) {
            writeBits(index, value, 2);
        } else {
            contiguous.putShort((int) index, value);
        }
        return this;
    }

    private long readBits(long index, int size) {
        check(index, size);
        long absolute = base + index;
        if (absolute % size == 0) {
            // TypedArray elements are aligned and cannot cross a page. Read them as one primitive,
            // using the same synchronization as Atomics even on JDKs that require its lock fallback.
            byte[] page = store.pages.get(absolute / PAGE_SIZE);
            if (page == null) {
                return 0;
            }
            int offset = (int) (absolute % PAGE_SIZE);
            if (size == Short.BYTES) {
                short value = ByteArrayAtomics.getVolatileShort(page, offset);
                return Short.toUnsignedLong(order == ByteOrder.LITTLE_ENDIAN ? value : Short.reverseBytes(value));
            }
            if (size == Integer.BYTES) {
                int value = ByteArrayAtomics.getVolatileInt(page, offset);
                return Integer.toUnsignedLong(order == ByteOrder.LITTLE_ENDIAN ? value : Integer.reverseBytes(value));
            }
            long value = ByteArrayAtomics.getVolatileLong(page, offset);
            return order == ByteOrder.LITTLE_ENDIAN ? value : Long.reverseBytes(value);
        }
        // Unaligned DataView accesses may straddle pages.
        long value = 0;
        for (int i = 0; i < size; i++) {
            int shift = (order == ByteOrder.LITTLE_ENDIAN ? i : size - 1 - i) * 8;
            value |= (get(index + i) & 255L) << shift;
        }
        return value;
    }

    public long remaining() {
        return limit - position;
    }

    public JSByteBuffer slice() {
        return new JSByteBuffer(store, base + position, remaining()).order(order);
    }

    private void write(long index, byte[] source, int offset, int count) {
        java.util.Objects.checkFromIndexSize(offset, count, source.length);
        while (count > 0) {
            byte[] target = arrayAt(index);
            int destination = arrayOffsetAt(index);
            int chunk = Math.min(count, target.length - destination);
            System.arraycopy(source, offset, target, destination, chunk);
            index += chunk;
            offset += chunk;
            count -= chunk;
        }
    }

    private JSByteBuffer writeBits(long index, long value, int size) {
        check(index, size);
        if ((base + index) % size == 0) {
            byte[] page = arrayAt(index);
            int offset = arrayOffsetAt(index);
            if (size == Short.BYTES) {
                short bits = (short) value;
                ByteArrayAtomics.setVolatileShort(page, offset,
                        order == ByteOrder.LITTLE_ENDIAN ? bits : Short.reverseBytes(bits));
            } else if (size == Integer.BYTES) {
                int bits = (int) value;
                ByteArrayAtomics.setVolatileInt(page, offset,
                        order == ByteOrder.LITTLE_ENDIAN ? bits : Integer.reverseBytes(bits));
            } else {
                ByteArrayAtomics.setVolatileLong(page, offset,
                        order == ByteOrder.LITTLE_ENDIAN ? value : Long.reverseBytes(value));
            }
            return this;
        }
        for (int i = 0; i < size; i++) {
            int shift = (order == ByteOrder.LITTLE_ENDIAN ? i : size - 1 - i) * 8;
            put(index + i, (byte) (value >>> shift));
        }
        return this;
    }

    public static JSByteBuffer allocate(long capacity) {
        if (capacity < 0 || capacity > JSArrayBuffer.MAX_DATA_BLOCK_BYTE_LENGTH + 1) {
            throw new IllegalArgumentException("Invalid buffer capacity");
        }
        return new JSByteBuffer(new Store(capacity), 0, capacity);
    }

    public static JSByteBuffer wrap(byte[] bytes) {
        return new JSByteBuffer(new Store(bytes), 0, bytes.length);
    }
    /** Receives one page-bounded piece of a range; page is null for a hole. */
    @FunctionalInterface
    private interface PageVisitor {
        void visit(long from, long to, byte[] page);
    }

    private static final class Store {
        private final byte[] bytes;
        private final Map<Long, byte[]> pages;
        Store(byte[] bytes) {
            this.bytes = bytes;
            pages = null;
        }

        Store(long capacity) {
            bytes = capacity <= PAGE_SIZE ? new byte[(int) capacity] : null;
            pages = bytes == null ? new ConcurrentHashMap<>() : null;
        }

        /**
         * Visit the absolute range [first, last) in ascending order, one page-bounded piece at a time, with a null page
         * for an unallocated hole. A range spanning no more pages than the table holds is walked by direct lookup, so a
         * small copy inside a densely populated block costs its own pages rather than a scan of every page in the
         * block. A wider, mostly unallocated range scans the table instead of probing each page.
         */
        void forEachPage(long first, long last, PageVisitor visitor) {
            long firstPage = first / PAGE_SIZE;
            long lastPage = (last - 1) / PAGE_SIZE;
            if (lastPage - firstPage < pages.size()) {
                for (long number = firstPage; number <= lastPage; number++) {
                    long pageStart = number * PAGE_SIZE;
                    visitor.visit(Math.max(first, pageStart), Math.min(last, pageStart + PAGE_SIZE), pages.get(number));
                }
                return;
            }
            long[] numbers = pages.keySet().stream().mapToLong(Long::longValue)
                    .filter(number -> number >= firstPage && number <= lastPage).sorted().toArray();
            long cursor = first;
            for (long number : numbers) {
                long pageStart = number * PAGE_SIZE;
                long from = Math.max(first, pageStart);
                if (cursor < from) {
                    visitor.visit(cursor, from, null);
                }
                long to = Math.min(last, pageStart + PAGE_SIZE);
                visitor.visit(from, to, pages.get(number));
                cursor = to;
            }
            if (cursor < last) {
                visitor.visit(cursor, last, null);
            }
        }
    }
}
