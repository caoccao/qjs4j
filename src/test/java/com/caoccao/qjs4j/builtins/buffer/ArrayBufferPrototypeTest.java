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
            JSArrayBuffer target = transfer.call(context, source, args).asArrayBuffer().orElseThrow();
            assertThat(target.getByteLength()).isEqualTo(length);
            assertThat(target.isDetached()).isFalse();
            assertThat(target.isResizable()).isFalse();
            assertThat(source.isDetached()).isTrue();
        }

        JSArrayBuffer source = new JSArrayBuffer(context, 16, 64);
        JSArrayBuffer target = transfer.call(context, source, numberArgs(24)).asArrayBuffer().orElseThrow();
        assertThat(target.getByteLength()).isEqualTo(24);
        assertThat(target.getMaxByteLength()).isEqualTo(fixed ? 24 : 64);
        assertThat(target.isResizable()).isEqualTo(!fixed);
        assertThat(source.isDetached()).isTrue();
        assertPendingTypeError(context, transfer.call(context, source, JSValue.NO_ARGS));
        assertPendingTypeError(context, transfer.call(context, new JSString("not a buffer"), JSValue.NO_ARGS));
    }

    @Test
    public void testGetByteLength() {
        // Normal case: various buffer sizes
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSValue result = ArrayBufferPrototype.getByteLength(context, buffer, JSValue.NO_ARGS);
        assertNumberValue(result, 16.0);

        buffer = new JSArrayBuffer(context, 0);
        result = ArrayBufferPrototype.getByteLength(context, buffer, JSValue.NO_ARGS);
        assertNumberValue(result, 0.0);

        buffer = new JSArrayBuffer(context, 1024);
        result = ArrayBufferPrototype.getByteLength(context, buffer, JSValue.NO_ARGS);
        assertNumberValue(result, 1024.0);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.getByteLength(context, nonBuffer, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetDetached() {
        // Normal case: non-detached buffer
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSValue result = ArrayBufferPrototype.getDetached(context, buffer, JSValue.NO_ARGS);
        assertThat(result.isBooleanFalse()).isTrue();

        // Normal case: detached buffer
        buffer.detach();
        result = ArrayBufferPrototype.getDetached(context, buffer, JSValue.NO_ARGS);
        assertThat(result.isBooleanTrue()).isTrue();

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.getDetached(context, nonBuffer, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetMaxByteLength() {
        // Normal case: non-resizable buffer
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSValue result = ArrayBufferPrototype.getMaxByteLength(context, buffer, JSValue.NO_ARGS);
        assertNumberValue(result, 16.0);

        // Normal case: resizable buffer
        JSArrayBuffer resizableBuffer = new JSArrayBuffer(context, 16, 64);
        result = ArrayBufferPrototype.getMaxByteLength(context, resizableBuffer, JSValue.NO_ARGS);
        assertNumberValue(result, 64.0);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.getMaxByteLength(context, nonBuffer, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetResizable() {
        // Normal case: non-resizable buffer
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSValue result = ArrayBufferPrototype.getResizable(context, buffer, JSValue.NO_ARGS);
        assertThat(result.isBooleanFalse()).isTrue();

        // Normal case: resizable buffer
        JSArrayBuffer resizableBuffer = new JSArrayBuffer(context, 16, 64);
        result = ArrayBufferPrototype.getResizable(context, resizableBuffer, JSValue.NO_ARGS);
        assertThat(result.isBooleanTrue()).isTrue();

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.getResizable(context, nonBuffer, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetToStringTag() {
        // Normal case: any this value should return "ArrayBuffer"
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSValue result = ArrayBufferPrototype.getToStringTag(context, buffer, JSValue.NO_ARGS);
        assertStringValue(result, "ArrayBuffer");

        // It's a getter that returns a constant, so it works with any thisArg
        result = ArrayBufferPrototype.getToStringTag(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);
        assertStringValue(result, "ArrayBuffer");
    }

    @Test
    public void testResize() {
        // Normal case: resize to larger size
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16, 64);
        JSValue result = ArrayBufferPrototype.resize(context, buffer, numberArgs(32));
        assertThat(result.isUndefined()).isTrue();
        assertThat(buffer.getByteLength()).isEqualTo(32);

        // Normal case: resize to smaller size
        result = ArrayBufferPrototype.resize(context, buffer, numberArgs(8));
        assertThat(result.isUndefined()).isTrue();
        assertThat(buffer.getByteLength()).isEqualTo(8);

        // Edge case: resize non-resizable buffer (TypeError per ES2024 spec)
        JSArrayBuffer fixedBuffer = new JSArrayBuffer(context, 16);
        result = ArrayBufferPrototype.resize(context, fixedBuffer, numberArgs(32));
        assertPendingTypeError(context, result);

        // Edge case: resize detached buffer (TypeError per ES2024 spec)
        buffer.detach();
        result = ArrayBufferPrototype.resize(context, buffer, numberArgs(16));
        assertPendingTypeError(context, result);

        // Edge case: resize beyond maxByteLength
        JSArrayBuffer buffer2 = new JSArrayBuffer(context, 16, 64);
        result = ArrayBufferPrototype.resize(context, buffer2, numberArgs(128));
        assertPendingRangeError(context, result);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.resize(context, nonBuffer, numberArgs(16));
        assertPendingTypeError(context, result);

        // Edge case: no arguments (ToIndex(undefined) = 0, resizes to 0 per ES2024 spec)
        JSArrayBuffer buffer3 = new JSArrayBuffer(context, 16, 64);
        result = ArrayBufferPrototype.resize(context, buffer3, JSValue.NO_ARGS);
        assertThat(result.isUndefined()).isTrue();
        assertThat(buffer3.getByteLength()).isEqualTo(0);
    }

    @Test
    public void testSlice() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);

        // Normal case: slice entire buffer
        JSValue result = ArrayBufferPrototype.slice(context, buffer, JSValue.NO_ARGS);
        JSArrayBuffer sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(16);

        // Normal case: slice with begin only
        result = ArrayBufferPrototype.slice(context, buffer, numberArgs(4));
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(12);

        // Normal case: slice with begin and end
        result = ArrayBufferPrototype.slice(context, buffer, numberArgs(4, 8));
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(4);

        // Normal case: negative begin
        result = ArrayBufferPrototype.slice(context, buffer, numberArgs(-4));
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(4);

        // Normal case: negative end
        result = ArrayBufferPrototype.slice(context, buffer, numberArgs(0, -4));
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(12);

        // Normal case: begin > end (empty slice)
        result = ArrayBufferPrototype.slice(context, buffer, numberArgs(8, 4));
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(0);

        // Normal case: begin out of bounds
        result = ArrayBufferPrototype.slice(context, buffer, numberArgs(20));
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(0);

        // Normal case: end out of bounds
        result = ArrayBufferPrototype.slice(context, buffer, numberArgs(0, 20));
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(16);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.slice(context, nonBuffer, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);

        // Edge case: slice on detached buffer
        buffer.detach();
        result = ArrayBufferPrototype.slice(context, buffer, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
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
