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

package com.caoccao.qjs4j.builtins.buffer;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.JSArrayBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ArrayBuffer prototype methods.
 */
public class ArrayBufferPrototypeTest extends BaseTest {

    private void assertTransfer(JSNativeCallback transfer, boolean fixed) {
        for (int length : new int[]{16, 32, 8}) {
            JSArrayBuffer source = new JSArrayBuffer(context, 16);
            JSValue[] args = length == 16 ? JSValue.NO_ARGS : numberArgs(length);
            JSArrayBuffer target = callBuiltin(transfer, source, args).asArrayBuffer().orElseThrow();
            assertThat(target.getByteLength()).isEqualTo(length);
            assertThat(target.isDetached()).isFalse();
            assertThat(target.isResizable()).isFalse();
            assertThat(source.isDetached()).isTrue();
        }

        JSArrayBuffer source = new JSArrayBuffer(context, 16, 64);
        JSArrayBuffer target = callBuiltin(transfer, source, 24).asArrayBuffer().orElseThrow();
        assertThat(target.getByteLength()).isEqualTo(24);
        assertThat(target.getMaxByteLength()).isEqualTo(fixed ? 24 : 64);
        assertThat(target.isResizable()).isEqualTo(!fixed);
        assertThat(source.isDetached()).isTrue();
        assertPendingTypeError(callBuiltin(transfer, source));
        assertPendingTypeError(callBuiltin(transfer, "not a buffer"));
    }

    @Test
    public void testGetByteLength() {
        // Normal case: various buffer sizes
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        var getByteLength = nativeMethod(ArrayBufferPrototype::getByteLength);
        assertNumberValue(getByteLength.call(buffer), 16.0);

        buffer = new JSArrayBuffer(context, 0);
        assertNumberValue(getByteLength.call(buffer), 0.0);

        buffer = new JSArrayBuffer(context, 1024);
        assertNumberValue(getByteLength.call(buffer), 1024.0);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        assertPendingTypeError(getByteLength.call(nonBuffer));
    }

    @Test
    public void testGetDetached() {
        // Normal case: non-detached buffer
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        var getDetached = builtin(ArrayBufferPrototype::getDetached, buffer);
        assertThat(getDetached.call()).isSameAs(JSBoolean.FALSE);

        // Normal case: detached buffer
        buffer.detach();
        assertThat(getDetached.call()).isSameAs(JSBoolean.TRUE);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        assertPendingTypeError(callBuiltin(ArrayBufferPrototype::getDetached, nonBuffer));
    }

    @Test
    public void testGetMaxByteLength() {
        // Normal case: non-resizable buffer
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        var getMaxByteLength = nativeMethod(ArrayBufferPrototype::getMaxByteLength);
        assertNumberValue(getMaxByteLength.call(buffer), 16.0);

        // Normal case: resizable buffer
        JSArrayBuffer resizableBuffer = new JSArrayBuffer(context, 16, 64);
        assertNumberValue(getMaxByteLength.call(resizableBuffer), 64.0);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        assertPendingTypeError(getMaxByteLength.call(nonBuffer));
    }

    @Test
    public void testGetResizable() {
        // Normal case: non-resizable buffer
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        var getResizable = nativeMethod(ArrayBufferPrototype::getResizable);
        assertThat(getResizable.call(buffer)).isSameAs(JSBoolean.FALSE);

        // Normal case: resizable buffer
        JSArrayBuffer resizableBuffer = new JSArrayBuffer(context, 16, 64);
        assertThat(getResizable.call(resizableBuffer)).isSameAs(JSBoolean.TRUE);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        assertPendingTypeError(getResizable.call(nonBuffer));
    }

    @Test
    public void testGetToStringTag() {
        // Normal case: any this value should return "ArrayBuffer"
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        var getToStringTag = nativeMethod(ArrayBufferPrototype::getToStringTag);
        assertStringValue(getToStringTag.call(buffer), "ArrayBuffer");

        // It's a getter that returns a constant, so it works with any thisArg
        assertStringValue(getToStringTag.call(JSUndefined.INSTANCE), "ArrayBuffer");
    }

    @Test
    public void testResize() {
        // Normal case: resize to larger size
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16, 64);
        var resizeMethod = builtin(ArrayBufferPrototype::resize, buffer);
        assertThat(resizeMethod.call(32).isUndefined()).isTrue();
        assertThat(buffer.getByteLength()).isEqualTo(32);

        // Normal case: resize to smaller size
        assertThat(resizeMethod.call(8).isUndefined()).isTrue();
        assertThat(buffer.getByteLength()).isEqualTo(8);

        // Edge case: resize non-resizable buffer (TypeError per ES2024 spec)
        JSArrayBuffer fixedBuffer = new JSArrayBuffer(context, 16);
        assertPendingTypeError(callBuiltin(ArrayBufferPrototype::resize, fixedBuffer, 32));

        // Edge case: resize detached buffer (TypeError per ES2024 spec)
        buffer.detach();
        assertPendingTypeError(resizeMethod.call(16));

        // Edge case: resize beyond maxByteLength
        JSArrayBuffer buffer2 = new JSArrayBuffer(context, 16, 64);
        assertPendingRangeError(callBuiltin(ArrayBufferPrototype::resize, buffer2, 128));

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        assertPendingTypeError(callBuiltin(ArrayBufferPrototype::resize, nonBuffer, 16));

        // Edge case: no arguments (ToIndex(undefined) = 0, resizes to 0 per ES2024 spec)
        JSArrayBuffer buffer3 = new JSArrayBuffer(context, 16, 64);
        assertThat(callBuiltin(ArrayBufferPrototype::resize, buffer3).isUndefined()).isTrue();
        assertThat(buffer3.getByteLength()).isEqualTo(0);
    }

    @Test
    public void testSlice() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);

        // Normal case: slice entire buffer
        var sliceMethod = builtin(ArrayBufferPrototype::slice, buffer);
        JSValue result = sliceMethod.call();
        assertThat(result.asArrayBuffer().orElseThrow().getByteLength()).isEqualTo(16);

        // Normal case: slice with begin only
        assertThat(sliceMethod.call(4).asArrayBuffer().orElseThrow().getByteLength()).isEqualTo(12);

        // Normal case: slice with begin and end
        assertThat(sliceMethod.call(4, 8).asArrayBuffer().orElseThrow().getByteLength()).isEqualTo(4);

        // Normal case: negative begin
        assertThat(sliceMethod.call(-4).asArrayBuffer().orElseThrow().getByteLength()).isEqualTo(4);

        // Normal case: negative end
        assertThat(sliceMethod.call(0, -4).asArrayBuffer().orElseThrow().getByteLength()).isEqualTo(12);

        // Normal case: begin > end (empty slice)
        assertThat(sliceMethod.call(8, 4).asArrayBuffer().orElseThrow().getByteLength()).isEqualTo(0);

        // Normal case: begin out of bounds
        assertThat(sliceMethod.call(20).asArrayBuffer().orElseThrow().getByteLength()).isEqualTo(0);

        // Normal case: end out of bounds
        result = sliceMethod.call(0, 20);
        assertThat(result.asArrayBuffer().orElseThrow().getByteLength()).isEqualTo(16);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = callBuiltin(ArrayBufferPrototype::slice, nonBuffer);
        assertPendingTypeError(result);

        // Edge case: slice on detached buffer
        buffer.detach();
        assertPendingTypeError(sliceMethod.call());
    }

    @Test
    public void testTransfer() {
        assertTransfer(ArrayBufferPrototype::transfer, false);
    }

    @Test
    public void testTransferToFixedLength() {
        assertTransfer(ArrayBufferPrototype::transferToFixedLength, true);
    }
}
