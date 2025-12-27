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

import java.nio.ByteBuffer;

/**
 * Represents a JavaScript Uint8ClampedArray.
 * 8-bit unsigned integer array with clamping (used for canvas pixel data).
 * Values are clamped to [0, 255] range instead of wrapping.
 */
public final class JSUint8ClampedArray extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 1;

    /**
     * Create a Uint8ClampedArray with a new buffer.
     */
    public JSUint8ClampedArray(int length) {
        super(length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a Uint8ClampedArray view on an existing buffer.
     */
    public JSUint8ClampedArray(JSArrayBufferable buffer, int byteOffset, int length) {
        super(buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        return buf.get(index) & 0xFF; // Convert to unsigned
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();

        // Clamp value to [0, 255] range
        int clampedValue;
        if (Double.isNaN(value)) {
            clampedValue = 0;
        } else if (value < 0) {
            clampedValue = 0;
        } else if (value > 255) {
            clampedValue = 255;
        } else {
            // Round to nearest integer
            clampedValue = (int) Math.round(value);
        }

        buf.put(index, (byte) clampedValue);
    }

    @Override
    public JSTypedArray subarray(int begin, int end) {
        // Normalize indices
        if (begin < 0) begin = Math.max(length + begin, 0);
        else begin = Math.min(begin, length);

        if (end < 0) end = Math.max(length + end, 0);
        else end = Math.min(end, length);

        int newLength = Math.max(end - begin, 0);
        int newByteOffset = byteOffset + begin * BYTES_PER_ELEMENT;

        return new JSUint8ClampedArray(buffer, newByteOffset, newLength);
    }

    @Override
    public String toString() {
        return "[object Uint8ClampedArray]";
    }
}
