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

package com.caoccao.qjs4j.utils;

import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Growth failure used to set an {@code error} flag and make every later append a silent no-op.
 * Nothing read the flag: {@code RegExpCompiler.compile()} appended a final {@code MATCH} that was
 * also ignored and returned the truncated bytes as a valid program, so a resource failure surfaced
 * later as a wrong match with nothing left to say what had happened.
 * <p>
 * The failure path is reachable here because a buffer can be given an explicit ceiling, which is
 * what makes it testable without exhausting the JVM.
 */
public class DynamicBufferGrowthTest {
    @Test
    public void testAppendPastTheCeilingThrows() {
        DynamicBuffer buffer = new DynamicBuffer(16, 32);
        buffer.append(new byte[32]);
        assertThat(buffer.size()).isEqualTo(32);
        assertThatThrownBy(() -> buffer.append((byte) 1))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("32");
    }

    @Test
    public void testBulkAppendPastTheCeilingThrowsAndLeavesTheBufferIntact() {
        DynamicBuffer buffer = new DynamicBuffer(16, 64);
        buffer.appendU32(0x01020304L);
        assertThatThrownBy(() -> buffer.append(new byte[128]))
                .isInstanceOf(JSRangeErrorException.class);
        // Nothing of the failed append landed, so the caller sees the buffer it had.
        assertThat(buffer.size()).isEqualTo(4);
        assertThat(buffer.toByteArray()).containsExactly(4, 3, 2, 1);
    }

    @Test
    public void testGrowthDoublesAndPreservesContents() {
        DynamicBuffer buffer = new DynamicBuffer(16);
        for (int index = 0; index < 1000; index++) {
            buffer.appendU8(index & 0xFF);
        }
        assertThat(buffer.size()).isEqualTo(1000);
        assertThat(buffer.capacity()).isGreaterThanOrEqualTo(1000);
        byte[] bytes = buffer.toByteArray();
        for (int index = 0; index < 1000; index++) {
            assertThat(bytes[index]).as("byte " + index).isEqualTo((byte) index);
        }
    }

    @Test
    public void testInsertOpensAGapAndKeepsTheTail() {
        DynamicBuffer buffer = new DynamicBuffer(64, 1024);
        buffer.append(new byte[]{1, 2, 3, 4});
        buffer.insert(2, 2);
        assertThat(buffer.size()).isEqualTo(6);
        byte[] bytes = buffer.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 1);
        assertThat(bytes[1]).isEqualTo((byte) 2);
        assertThat(bytes[4]).isEqualTo((byte) 3);
        assertThat(bytes[5]).isEqualTo((byte) 4);
        // A zero-length insert is a no-op rather than an error.
        buffer.insert(0, 0);
        assertThat(buffer.size()).isEqualTo(6);
    }

    @Test
    public void testInsertPastTheCeilingThrows() {
        DynamicBuffer buffer = new DynamicBuffer(16, 32);
        buffer.append(new byte[30]);
        assertThatThrownBy(() -> buffer.insert(0, 16)).isInstanceOf(JSRangeErrorException.class);
    }

    @Test
    public void testInsertRejectsALengthThatOverflowsTheSize() {
        DynamicBuffer buffer = new DynamicBuffer(64, 1024);
        buffer.append(new byte[]{1, 2, 3});
        // size + length overflows int and lands on a value an int comparison accepts.
        assertThatThrownBy(() -> buffer.insert(1, Integer.MAX_VALUE))
                .isInstanceOf(JSRangeErrorException.class);
        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    public void testInsertRejectsANegativeLengthInsteadOfDeletingData() {
        // insert(5, -1) validated the position but not the length, so it performed a legal
        // overlapping copy from index 5 to index 4 and set the size to 9: it deleted a byte and
        // reported success.
        DynamicBuffer buffer = new DynamicBuffer(64, 1024);
        buffer.append(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        assertThatThrownBy(() -> buffer.insert(5, -1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(buffer.size()).as("a rejected insert changes nothing").isEqualTo(10);
        assertThat(buffer.toByteArray()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    public void testRangeValidationSurvivesOverflowingOffsets() {
        DynamicBuffer buffer = new DynamicBuffer(64, 1024);
        buffer.append(new byte[]{1, 2, 3, 4});
        // offset + length wraps negative in int, which used to pass the bounds test.
        assertThatThrownBy(() -> buffer.getRange(Integer.MAX_VALUE, Integer.MAX_VALUE))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.append(new byte[]{1, 2}, Integer.MAX_VALUE, Integer.MAX_VALUE))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.setU32(Integer.MAX_VALUE, 1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(buffer.size()).isEqualTo(4);
    }

    @Test
    public void testResetHonoursTheCeiling() {
        DynamicBuffer buffer = new DynamicBuffer(16, 64);
        buffer.reset(1024);
        assertThat(buffer.capacity()).isEqualTo(64);
        assertThat(buffer.size()).isZero();
    }

    @Test
    public void testTheCeilingIsClampedToWhatTheJvmCanAllocate() {
        // A ceiling above the JVM's array limit would move the failure from a diagnosable
        // RangeError to an OutOfMemoryError, so it is clamped, and ordinary use is unaffected.
        DynamicBuffer buffer = new DynamicBuffer(16, Integer.MAX_VALUE);
        buffer.append(new byte[8]);
        assertThat(buffer.size()).isEqualTo(8);
        assertThat(new DynamicBuffer(16, -1).capacity()).isEqualTo(16);
    }

    @Test
    public void testU16AndU64AppendsPastTheCeilingThrow() {
        DynamicBuffer first = new DynamicBuffer(16, 16);
        first.append(new byte[16]);
        assertThatThrownBy(() -> first.appendU16(1)).isInstanceOf(JSRangeErrorException.class);

        DynamicBuffer second = new DynamicBuffer(16, 16);
        second.append(new byte[16]);
        assertThatThrownBy(() -> second.appendU64(1L)).isInstanceOf(JSRangeErrorException.class);

        DynamicBuffer third = new DynamicBuffer(16, 16);
        third.append(new byte[16]);
        assertThatThrownBy(() -> third.appendU32(1L)).isInstanceOf(JSRangeErrorException.class);
    }
}
