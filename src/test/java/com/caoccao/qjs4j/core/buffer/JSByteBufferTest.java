package com.caoccao.qjs4j.core.buffer;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.assertj.core.api.Assertions.*;

class JSByteBufferTest {
    @Test
    void alignedSharedPrimitivesDoNotTearAgainstAtomics() throws Exception {
        long index = 1L << 31;
        JSByteBuffer buffer = JSByteBuffer.allocate(index + 16).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(buffer.getLong(index)).isZero();
        long first = 0x55555555AAAAAAAAL, second = 0xAAAAAAAA55555555L;
        buffer.putLong(index, first);
        byte[] page = buffer.arrayAt(index);
        int offset = buffer.arrayOffsetAt(index);
        var started = new java.util.concurrent.CountDownLatch(1);
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread writer = new Thread(() -> {
            started.countDown();
            while (running.get()) {
                com.caoccao.qjs4j.utils.ByteArrayAtomics.setVolatileLong(page, offset, first);
                buffer.putLong(index, second);
            }
        });
        writer.start();
        try {
            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 100000; i++) {
                assertThat(buffer.getLong(index)).isIn(first, second);
                assertThat(com.caoccao.qjs4j.utils.ByteArrayAtomics.getVolatileLong(page, offset)).isIn(first, second);
            }
        } finally {
            running.set(false);
            writer.join(5000);
        }
        assertThat(writer.isAlive()).isFalse();
    }

    @Test
    void bulkAccessAndPositionLimits() {
        byte[] bytes = {1, 2, 3, 4, 5, 6};
        JSByteBuffer wrapped = JSByteBuffer.wrap(bytes);
        assertThat(wrapped.array()).isSameAs(bytes);
        JSByteBuffer sliced = wrapped.position(2).slice();
        assertThat(sliced.arrayOffsetAt(1)).isEqualTo(3);
        sliced.put(new byte[]{9, 8});
        assertThat(bytes).containsExactly(1, 2, 9, 8, 5, 6);
        wrapped.position(0).limit(4);
        JSByteBuffer duplicate = wrapped.duplicate();
        assertThat(duplicate.position()).isZero();
        assertThat(duplicate.limit()).isEqualTo(4);
        JSByteBuffer destination = JSByteBuffer.allocate(8).position(2);
        destination.put(duplicate);
        assertThat(duplicate.remaining()).isZero();
        assertThat(destination.position()).isEqualTo(6);
        destination.position(0);
        byte[] copy = new byte[10];
        destination.get(copy, 1, 8);
        assertThat(copy).containsExactly(0, 0, 0, 1, 2, 9, 8, 0, 0, 0);
        long boundary = JSByteBuffer.PAGE_SIZE;
        JSByteBuffer sparse = JSByteBuffer.allocate(boundary * 4);
        sparse.position(boundary - 2).put(bytes, 1, 4);
        assertThat(sparse.position()).isEqualTo(boundary + 2);
        byte[] across = new byte[8];
        sparse.position(boundary - 4).get(across);
        assertThat(across).containsExactly(0, 0, 2, 9, 8, 5, 0, 0);
        sparse.position(boundary * 2 - 2).get(across);
        assertThat(across).containsOnly((byte) 0);
        assertThat(sparse.get(boundary * 3)).isZero();
        assertThatThrownBy(sparse::array).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copiesPreserveZerosAndHandleOverlapsInEitherDirection() {
        long large = (1L << 31) + 64;
        for (long sourceSize : new long[]{64, large}) {
            for (long targetSize : new long[]{64, large}) {
                JSByteBuffer source = JSByteBuffer.allocate(sourceSize);
                JSByteBuffer target = JSByteBuffer.allocate(targetSize);
                long from = sourceSize - 32, to = targetSize - 32;
                source.put(from, (byte) 11).put(from + 31, (byte) 99);
                target.put(to + 16, (byte) 12);
                source.copyTo(target, from, to, 32);
                assertThat(target.get(to)).isEqualTo((byte) 11);
                assertThat(target.get(to + 16)).isZero();
                assertThat(target.get(to + 31)).isEqualTo((byte) 99);
                target.copyTo(target, to, to + 1, 31);
                assertThat(target.get(to + 1)).isEqualTo((byte) 11);
                assertThat(target.get(to + 31)).isZero();
                target.copyTo(target, to + 1, to, 31);
                assertThat(target.get(to)).isEqualTo((byte) 11);
                target.copyTo(target, targetSize, targetSize, 0);
            }
        }
        JSByteBuffer source = JSByteBuffer.allocate(large);
        JSByteBuffer target = JSByteBuffer.allocate(large);
        target.put(8, (byte) 1).put(large - 1, (byte) 2);
        source.put(0, (byte) 3).put(large - 1, (byte) 4);
        source.copyTo(target, 0, 0, large);
        assertThat(target.get(8)).isZero();
        target.clearRange(large - 32, large);
        assertThat(target.get(0)).isEqualTo((byte) 3);
        assertThat(target.get(large - 1)).isZero();
        target.clearRange(0, 1);
        assertThat(target.get(0)).isZero();
    }

    @Test
    void copiesWriteEachTargetByteOnceInBothPageWalks() {
        long page = JSByteBuffer.PAGE_SIZE;
        JSByteBuffer source = JSByteBuffer.allocate(page * 4);
        source.put(0, (byte) 1).put(page - 1, (byte) 2).put(page * 2, (byte) 3).put(page * 3 - 1, (byte) 4);
        JSByteBuffer target = JSByteBuffer.allocate(page * 4);
        for (long number = 0; number < 4; number++) {
            target.put(number * page + 5, (byte) 9);
        }
        // Two source pages and a two-page range: each page is looked up directly.
        source.copyTo(target, page - 8, page - 8, 16);
        assertThat(target.get(page - 1)).isEqualTo((byte) 2);
        assertThat(target.get(page - 8)).isZero();
        assertThat(target.get(page + 5)).isZero();
        assertThat(target.get(page * 2 + 5)).isEqualTo((byte) 9);
        // A four-page range over two source pages: the table is scanned and the holes between hits zeroed.
        source.copyTo(target, 0, 0, page * 4);
        assertThat(target.get(0)).isEqualTo((byte) 1);
        assertThat(target.get(page - 1)).isEqualTo((byte) 2);
        assertThat(target.get(page + 5)).isZero();
        assertThat(target.get(page * 2)).isEqualTo((byte) 3);
        assertThat(target.get(page * 2 + 5)).isZero();
        assertThat(target.get(page * 3 - 1)).isEqualTo((byte) 4);
        assertThat(target.get(page * 3 + 5)).isZero();
        JSByteBuffer small = JSByteBuffer.allocate(64).put(10, (byte) 9).put(40, (byte) 9);
        source.copyTo(small, page - 32, 0, 64);
        assertThat(small.get(31)).isEqualTo((byte) 2);
        assertThat(small.get(10)).isZero();
        assertThat(small.get(40)).isZero();
        JSByteBuffer sparse = JSByteBuffer.allocate(page * 4).put(page * 2 + 1, (byte) 7);
        sparse.clearRange(0, page * 4);
        assertThat(sparse.get(page * 2 + 1)).isZero();
        target.clearRange(page - 2, page + 2);
        assertThat(target.get(page - 1)).isZero();
        assertThat(target.get(page - 3)).isZero();
        assertThat(target.get(page * 2)).isEqualTo((byte) 3);
    }

    @Test
    void invalidRangesFailBeforeMutatingStorageOrPosition() {
        JSByteBuffer buffer = JSByteBuffer.allocate(8).put(0, (byte) 7).position(4).limit(6);
        assertThatThrownBy(() -> buffer.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.get(6)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.getLong(0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.put(new byte[3])).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.put(new byte[1], -1, 1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.get(new byte[1], 1, 1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.limit(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.limit(9)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.position(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.position(7)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.clearRange(5, 4)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.copyTo(buffer, 0, 0, -1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.put(buffer)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JSByteBuffer.allocate(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JSByteBuffer.allocate(JSArrayBuffer.MAX_DATA_BLOCK_BYTE_LENGTH + 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(buffer.position()).isEqualTo(4);
        assertThat(buffer.get(0)).isEqualTo((byte) 7);
        assertThat(JSByteBuffer.allocate(0).remaining()).isZero();
    }

    @Test
    void primitivesMatchNioInBothOrdersAcrossPagesAndViews() {
        for (long size : new long[]{64, (1L << 31) + 64}) {
            for (ByteOrder order : new ByteOrder[]{ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
                for (long offset : size == 64
                        ? new long[]{0, 2, 5, 6}
                        : new long[]{0, 2, 5, 6, JSByteBuffer.PAGE_SIZE - 3, (1L << 31) - 3}) {
                    JSByteBuffer buffer = JSByteBuffer.allocate(size).order(order);
                    ByteBuffer expected = ByteBuffer.allocate(32).order(order);
                    buffer.putShort(offset, (short) 0xFEDC).putInt(offset + 2, 0x89ABCDEF)
                            .putLong(offset + 6, 0xFEDCBA9876543210L).putFloat(offset + 14, -1.25f)
                            .putDouble(offset + 18, Math.PI);
                    expected.putShort(0, (short) 0xFEDC).putInt(2, 0x89ABCDEF).putLong(6, 0xFEDCBA9876543210L)
                            .putFloat(14, -1.25f).putDouble(18, Math.PI);
                    byte[] actual = new byte[32];
                    buffer.duplicate().position(offset).get(actual);
                    assertThat(actual).containsExactly(expected.array());
                    assertThat(buffer.getShort(offset)).isEqualTo(expected.getShort(0));
                    assertThat(buffer.getInt(offset + 2)).isEqualTo(expected.getInt(2));
                    assertThat(buffer.getLong(offset + 6)).isEqualTo(expected.getLong(6));
                    assertThat(buffer.getFloat(offset + 14)).isEqualTo(expected.getFloat(14));
                    assertThat(buffer.getDouble(offset + 18)).isEqualTo(expected.getDouble(18));
                    JSByteBuffer slice = buffer.position(offset).limit(offset + 32).slice();
                    assertThat(slice.capacity()).isEqualTo(32);
                    assertThat(slice.order()).isSameAs(order);
                    assertThat(slice.getInt(2)).isEqualTo(expected.getInt(2));
                    slice.put(0, (byte) 42);
                    assertThat(buffer.get(offset)).isEqualTo((byte) 42);
                    assertThat(slice.arrayAt(0)).isSameAs(buffer.arrayAt(offset));
                    assertThat(slice.arrayOffsetAt(0)).isEqualTo(buffer.arrayOffsetAt(offset));
                    buffer.limit(offset);
                    assertThat(buffer.position()).isEqualTo(offset);
                }
            }
        }
    }

    @Test
    void sharedCopiesNeverExposeZerosTheSourceDoesNotHold() throws Exception {
        long size = JSByteBuffer.PAGE_SIZE * 2L + 64;
        JSByteBuffer source = JSByteBuffer.allocate(size);
        JSByteBuffer target = JSByteBuffer.allocate(size);
        byte[] ones = new byte[JSByteBuffer.PAGE_SIZE];
        java.util.Arrays.fill(ones, (byte) 1);
        for (long offset = 0; offset < size; offset += ones.length) {
            int count = (int) Math.min(ones.length, size - offset);
            source.position(offset).put(ones, 0, count);
            target.position(offset).put(ones, 0, count);
        }
        var started = new java.util.concurrent.CountDownLatch(1);
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread writer = new Thread(() -> {
            started.countDown();
            while (running.get()) {
                source.copyTo(target, 0, 0, size);
            }
        });
        writer.start();
        try {
            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            long[] probes = {0, JSByteBuffer.PAGE_SIZE - 1, JSByteBuffer.PAGE_SIZE, JSByteBuffer.PAGE_SIZE + 7,
                    size - 1};
            for (int i = 0; i < 200000; i++) {
                assertThat(target.get(probes[i % probes.length])).isEqualTo((byte) 1);
            }
        } finally {
            running.set(false);
            writer.join(5000);
        }
        assertThat(writer.isAlive()).isFalse();
    }
}
