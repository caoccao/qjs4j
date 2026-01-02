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

import com.caoccao.qjs4j.utils.Float16;

import java.nio.ByteBuffer;

/**
 * Represents a JavaScript Float16Array.
 * 16-bit half-precision floating point array.
 */
public final class JSFloat16Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 2;

    /**
     * Create a Float16Array with a new buffer.
     */
    public JSFloat16Array(int length) {
        super(length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a Float16Array view on an existing buffer.
     */
    public JSFloat16Array(JSArrayBufferable buffer, int byteOffset, int length) {
        super(buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        short halfFloat = buf.getShort(byteOffset + index * BYTES_PER_ELEMENT);
        return Float16.toFloat(halfFloat);
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        short halfFloat = Float16.toHalf((float) value);
        buf.putShort(byteOffset + index * BYTES_PER_ELEMENT, halfFloat);
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

        return new JSFloat16Array(buffer, newByteOffset, newLength);
    }
}
