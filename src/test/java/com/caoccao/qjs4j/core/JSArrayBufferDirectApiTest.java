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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The direct {@code JSArrayBuffer} Java API must raise the same error kinds the specification
 * assigns, not whichever one happened to be reachable.
 * <p>
 * The public API reported a detached or non-resizable {@code resize()} as {@code RangeError} —
 * ES2024 25.1.6.7 makes both receiver-state conditions, so both are {@code TypeError}s — while
 * {@code transfer()} reported an invalid negative length as {@code TypeError}, which is a range
 * condition. The JavaScript built-ins precheck every case, so the wrong types were invisible from
 * script and only embedders calling the Java methods saw them. The Javadoc separately declared
 * {@code IllegalStateException} and {@code IllegalArgumentException}, neither of which is thrown.
 */
public class JSArrayBufferDirectApiTest extends BaseTest {

    private JSArrayBuffer fixedBuffer(int byteLength) {
        return context.createJSArrayBuffer(byteLength);
    }

    private JSArrayBuffer resizableBuffer(int byteLength, int maxByteLength) {
        return context.createJSArrayBuffer(byteLength, maxByteLength);
    }

    @Test
    public void testResizeOnDetachedBufferRaisesTypeError() {
        JSArrayBuffer buffer = resizableBuffer(8, 16);
        buffer.detach();
        assertThatThrownBy(() -> buffer.resize(4))
                .isInstanceOf(JSTypeErrorException.class)
                .hasMessageContaining("detached");
    }

    @Test
    public void testResizeOnNonResizableBufferRaisesTypeError() {
        JSArrayBuffer buffer = fixedBuffer(8);
        assertThatThrownBy(() -> buffer.resize(4))
                .isInstanceOf(JSTypeErrorException.class)
                .hasMessageContaining("non-resizable");
    }

    @Test
    public void testResizeWithOutOfRangeLengthRaisesRangeError() {
        JSArrayBuffer buffer = resizableBuffer(8, 16);
        assertThatThrownBy(() -> buffer.resize(-1))
                .isInstanceOf(JSRangeErrorException.class);
        assertThatThrownBy(() -> buffer.resize(17))
                .isInstanceOf(JSRangeErrorException.class);
    }

    @Test
    public void testResizeWithinRangeStillWorks() {
        JSArrayBuffer buffer = resizableBuffer(8, 16);
        assertThatCode(() -> buffer.resize(12)).doesNotThrowAnyException();
        assertThat(buffer.getByteLength()).isEqualTo(12);
    }

    @Test
    public void testTransferOnDetachedBufferRaisesTypeError() {
        JSArrayBuffer buffer = fixedBuffer(8);
        buffer.detach();
        assertThatThrownBy(() -> buffer.transfer(context, 4))
                .isInstanceOf(JSTypeErrorException.class)
                .hasMessageContaining("detached");
    }

    @Test
    public void testTransferWithNegativeLengthRaisesRangeError() {
        JSArrayBuffer buffer = fixedBuffer(8);
        assertThatThrownBy(() -> buffer.transfer(context, -2))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    public void testTransferToFixedLengthWithNegativeLengthRaisesRangeError() {
        JSArrayBuffer buffer = fixedBuffer(8);
        assertThatThrownBy(() -> buffer.transferToFixedLength(context, -2))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    public void testTransferToFixedLengthOnDetachedBufferRaisesTypeError() {
        JSArrayBuffer buffer = fixedBuffer(8);
        buffer.detach();
        assertThatThrownBy(() -> buffer.transferToFixedLength(context, 4))
                .isInstanceOf(JSTypeErrorException.class)
                .hasMessageContaining("detached");
    }

    @Test
    public void testSliceOnDetachedBufferRaisesTypeError() {
        JSArrayBuffer buffer = fixedBuffer(8);
        buffer.detach();
        assertThatThrownBy(() -> buffer.slice(context, 0, 4))
                .isInstanceOf(JSTypeErrorException.class)
                .hasMessageContaining("detached");
    }

    @Test
    public void testSliceCopiesTheRequestedRange() {
        JSArrayBuffer buffer = fixedBuffer(8);
        for (int index = 0; index < 8; index++) {
            buffer.getBuffer().put(index, (byte) (index + 1));
        }
        JSArrayBuffer sliced = buffer.slice(context, 2, 6);
        assertThat(sliced.getByteLength()).isEqualTo(4);
        assertThat(sliced.getBuffer().get(0)).isEqualTo((byte) 3);
        assertThat(sliced.getBuffer().get(3)).isEqualTo((byte) 6);
        assertThat(buffer.isDetached()).isFalse();
    }

    @Test
    public void testSliceWithAnEmptyRangeProducesAnEmptyBuffer() {
        JSArrayBuffer buffer = fixedBuffer(8);
        assertThat(buffer.slice(context, 4, 4).getByteLength()).isZero();
        assertThat(buffer.slice(context, 6, 2).getByteLength()).isZero();
    }

    @Test
    public void testSliceNormalizesNegativeOffsets() {
        JSArrayBuffer buffer = fixedBuffer(8);
        for (int index = 0; index < 8; index++) {
            buffer.getBuffer().put(index, (byte) (index + 1));
        }
        assertThat(buffer.slice(context, -3, -1).getByteLength()).isEqualTo(2);
        assertThat(buffer.slice(context, -3, -1).getBuffer().get(0)).isEqualTo((byte) 6);
        assertThat(buffer.slice(context, -100, 100).getByteLength()).isEqualTo(8);
        assertThat(buffer.slice(context, 100, 100).getByteLength()).isZero();
    }

    @Test
    public void testTransferWithTheDefaultLengthKeepsTheCurrentSize() {
        JSArrayBuffer buffer = fixedBuffer(8);
        assertThat(buffer.transfer(context, -1).getByteLength()).isEqualTo(8);

        JSArrayBuffer other = fixedBuffer(8);
        assertThat(other.transferToFixedLength(context, -1).getByteLength()).isEqualTo(8);
    }

    @Test
    public void testTransferToZeroLengthCopiesNothing() {
        JSArrayBuffer buffer = fixedBuffer(8);
        assertThat(buffer.transfer(context, 0).getByteLength()).isZero();

        JSArrayBuffer other = fixedBuffer(8);
        assertThat(other.transferToFixedLength(context, 0).getByteLength()).isZero();
    }

    @Test
    public void testTransferStillWorksForAValidLength() {
        JSArrayBuffer buffer = fixedBuffer(8);
        JSArrayBuffer transferred = buffer.transfer(context, 4);
        assertThat(transferred.getByteLength()).isEqualTo(4);
        assertThat(buffer.isDetached()).isTrue();
    }

    // -----------------------------------------------------------------------------------
    // Script-visible behaviour must be unchanged: the built-ins precheck every case above,
    // so correcting the direct API's types must not alter what JavaScript observes.
    // -----------------------------------------------------------------------------------

    @Test
    public void testScriptVisibleResizeErrorsAreUnchanged() {
        assertThat(evalToString(
                """
                        const b = new ArrayBuffer(8);
                        try { b.resize(4); 'NO ERROR' } catch (e) { e.name }"""))
                .isEqualTo("TypeError");
        assertThat(evalToString(
                """
                        const r = new ArrayBuffer(8, { maxByteLength: 16 });
                        try { r.resize(32); 'NO ERROR' } catch (e) { e.name }"""))
                .isEqualTo("RangeError");
    }

    @Test
    public void testScriptVisibleTransferErrorsAreUnchanged() {
        assertThat(evalToString(
                """
                        const b = new ArrayBuffer(8);
                        b.transfer();
                        try { b.transfer(); 'NO ERROR' } catch (e) { e.name }"""))
                .isEqualTo("TypeError");
    }

    private String evalToString(String code) {
        return JSTypeConversions.toString(context, context.eval(code)).value();
    }
}
