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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ArrayBuffer prototype methods.
 */
public class ArrayBufferPrototypeTest extends BaseTest {

    @Test
    public void testGetByteLength() {
        // Normal case: various buffer sizes
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSValue result = ArrayBufferPrototype.getByteLength(context, buffer, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(16.0);

        buffer = new JSArrayBuffer(0);
        result = ArrayBufferPrototype.getByteLength(context, buffer, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        buffer = new JSArrayBuffer(1024);
        result = ArrayBufferPrototype.getByteLength(context, buffer, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1024.0);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.getByteLength(context, nonBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetDetached() {
        // Normal case: non-detached buffer
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSValue result = ArrayBufferPrototype.getDetached(context, buffer, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Normal case: detached buffer
        buffer.detach();
        result = ArrayBufferPrototype.getDetached(context, buffer, new JSValue[]{});
        assertThat(result.isBooleanTrue()).isTrue();

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.getDetached(context, nonBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetMaxByteLength() {
        // Normal case: non-resizable buffer
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSValue result = ArrayBufferPrototype.getMaxByteLength(context, buffer, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(16.0);

        // Normal case: resizable buffer
        JSArrayBuffer resizableBuffer = new JSArrayBuffer(16, 64);
        result = ArrayBufferPrototype.getMaxByteLength(context, resizableBuffer, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(64.0);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.getMaxByteLength(context, nonBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetResizable() {
        // Normal case: non-resizable buffer
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSValue result = ArrayBufferPrototype.getResizable(context, buffer, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Normal case: resizable buffer
        JSArrayBuffer resizableBuffer = new JSArrayBuffer(16, 64);
        result = ArrayBufferPrototype.getResizable(context, resizableBuffer, new JSValue[]{});
        assertThat(result.isBooleanTrue()).isTrue();

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.getResizable(context, nonBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetToStringTag() {
        // Normal case: any this value should return "ArrayBuffer"
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSValue result = ArrayBufferPrototype.getToStringTag(context, buffer, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("ArrayBuffer");

        // It's a getter that returns a constant, so it works with any thisArg
        result = ArrayBufferPrototype.getToStringTag(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("ArrayBuffer");
    }

    @Test
    public void testResize() {
        // Normal case: resize to larger size
        JSArrayBuffer buffer = new JSArrayBuffer(16, 64);
        JSValue result = ArrayBufferPrototype.resize(context, buffer, new JSValue[]{new JSNumber(32)});
        assertThat(result.isUndefined()).isTrue();
        assertThat(buffer.getByteLength()).isEqualTo(32);

        // Normal case: resize to smaller size
        result = ArrayBufferPrototype.resize(context, buffer, new JSValue[]{new JSNumber(8)});
        assertThat(result.isUndefined()).isTrue();
        assertThat(buffer.getByteLength()).isEqualTo(8);

        // Edge case: resize non-resizable buffer (TypeError per ES2024 spec)
        JSArrayBuffer fixedBuffer = new JSArrayBuffer(16);
        result = ArrayBufferPrototype.resize(context, fixedBuffer, new JSValue[]{new JSNumber(32)});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: resize detached buffer (TypeError per ES2024 spec)
        buffer.detach();
        result = ArrayBufferPrototype.resize(context, buffer, new JSValue[]{new JSNumber(16)});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: resize beyond maxByteLength
        JSArrayBuffer buffer2 = new JSArrayBuffer(16, 64);
        result = ArrayBufferPrototype.resize(context, buffer2, new JSValue[]{new JSNumber(128)});
        assertRangeError(result);
        assertPendingException(context);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.resize(context, nonBuffer, new JSValue[]{new JSNumber(16)});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: no arguments (ToIndex(undefined) = 0, resizes to 0 per ES2024 spec)
        JSArrayBuffer buffer3 = new JSArrayBuffer(16, 64);
        result = ArrayBufferPrototype.resize(context, buffer3, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();
        assertThat(buffer3.getByteLength()).isEqualTo(0);
    }

    @Test
    public void testSlice() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);

        // Normal case: slice entire buffer
        JSValue result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{});
        JSArrayBuffer sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(16);

        // Normal case: slice with begin only
        result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{new JSNumber(4)});
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(12);

        // Normal case: slice with begin and end
        result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{new JSNumber(4), new JSNumber(8)});
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(4);

        // Normal case: negative begin
        result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{new JSNumber(-4)});
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(4);

        // Normal case: negative end
        result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{new JSNumber(0), new JSNumber(-4)});
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(12);

        // Normal case: begin > end (empty slice)
        result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{new JSNumber(8), new JSNumber(4)});
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(0);

        // Normal case: begin out of bounds
        result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{new JSNumber(20)});
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(0);

        // Normal case: end out of bounds
        result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{new JSNumber(0), new JSNumber(20)});
        sliced = result.asArrayBuffer().orElseThrow();
        assertThat(sliced.getByteLength()).isEqualTo(16);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.slice(context, nonBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: slice on detached buffer
        buffer.detach();
        result = ArrayBufferPrototype.slice(context, buffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testTransfer() {
        // Normal case: transfer with same size
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSValue result = ArrayBufferPrototype.transfer(context, buffer, new JSValue[]{});
        JSArrayBuffer transferred = result.asArrayBuffer().orElseThrow();
        assertThat(transferred.getByteLength()).isEqualTo(16);
        assertThat(buffer.isDetached()).isTrue();
        assertThat(transferred.isDetached()).isFalse();

        // Normal case: transfer to larger size
        JSArrayBuffer buffer2 = new JSArrayBuffer(16);
        result = ArrayBufferPrototype.transfer(context, buffer2, new JSValue[]{new JSNumber(32)});
        transferred = result.asArrayBuffer().orElseThrow();
        assertThat(transferred.getByteLength()).isEqualTo(32);
        assertThat(buffer2.isDetached()).isTrue();

        // Normal case: transfer to smaller size
        JSArrayBuffer buffer3 = new JSArrayBuffer(16);
        result = ArrayBufferPrototype.transfer(context, buffer3, new JSValue[]{new JSNumber(8)});
        transferred = result.asArrayBuffer().orElseThrow();
        assertThat(transferred.getByteLength()).isEqualTo(8);
        assertThat(buffer3.isDetached()).isTrue();

        // Normal case: transfer resizable buffer maintains resizability
        JSArrayBuffer resizableBuffer = new JSArrayBuffer(16, 64);
        result = ArrayBufferPrototype.transfer(context, resizableBuffer, new JSValue[]{new JSNumber(24)});
        transferred = result.asArrayBuffer().orElseThrow();
        assertThat(transferred.getByteLength()).isEqualTo(24);
        assertThat(transferred.getMaxByteLength()).isEqualTo(64);
        assertThat(transferred.isResizable()).isTrue();

        // Edge case: transfer already detached buffer
        JSArrayBuffer detachedBuffer = new JSArrayBuffer(16);
        detachedBuffer.detach();
        result = ArrayBufferPrototype.transfer(context, detachedBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.transfer(context, nonBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testTransferToFixedLength() {
        // Normal case: transfer to fixed-length with same size
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSValue result = ArrayBufferPrototype.transferToFixedLength(context, buffer, new JSValue[]{});
        JSArrayBuffer transferred = result.asArrayBuffer().orElseThrow();
        assertThat(transferred.getByteLength()).isEqualTo(16);
        assertThat(transferred.isResizable()).isFalse();
        assertThat(buffer.isDetached()).isTrue();

        // Normal case: transfer to larger size
        JSArrayBuffer buffer2 = new JSArrayBuffer(16);
        result = ArrayBufferPrototype.transferToFixedLength(context, buffer2, new JSValue[]{new JSNumber(32)});
        transferred = result.asArrayBuffer().orElseThrow();
        assertThat(transferred.getByteLength()).isEqualTo(32);
        assertThat(transferred.isResizable()).isFalse();
        assertThat(buffer2.isDetached()).isTrue();

        // Normal case: transfer to smaller size
        JSArrayBuffer buffer3 = new JSArrayBuffer(16);
        result = ArrayBufferPrototype.transferToFixedLength(context, buffer3, new JSValue[]{new JSNumber(8)});
        transferred = result.asArrayBuffer().orElseThrow();
        assertThat(transferred.getByteLength()).isEqualTo(8);
        assertThat(transferred.isResizable()).isFalse();
        assertThat(buffer3.isDetached()).isTrue();

        // Normal case: transfer resizable buffer to fixed-length
        JSArrayBuffer resizableBuffer = new JSArrayBuffer(16, 64);
        result = ArrayBufferPrototype.transferToFixedLength(context, resizableBuffer, new JSValue[]{new JSNumber(24)});
        transferred = result.asArrayBuffer().orElseThrow();
        assertThat(transferred.getByteLength()).isEqualTo(24);
        assertThat(transferred.getMaxByteLength()).isEqualTo(24);
        assertThat(transferred.isResizable()).isFalse();

        // Edge case: transfer already detached buffer
        JSArrayBuffer detachedBuffer = new JSArrayBuffer(16);
        detachedBuffer.detach();
        result = ArrayBufferPrototype.transferToFixedLength(context, detachedBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: called on non-ArrayBuffer
        JSValue nonBuffer = new JSString("not a buffer");
        result = ArrayBufferPrototype.transferToFixedLength(context, nonBuffer, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }
}