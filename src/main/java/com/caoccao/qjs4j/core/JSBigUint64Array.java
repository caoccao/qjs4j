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
 * Represents a JavaScript BigUint64Array.
 * 64-bit unsigned integer array.
 */
public final class JSBigUint64Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 8;

    /**
     * Create a BigUint64Array with a new buffer.
     */
    public JSBigUint64Array(int length) {
        super(length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a BigUint64Array view on an existing buffer.
     */
    public JSBigUint64Array(JSArrayBufferable buffer, int byteOffset, int length) {
        super(buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        long value = buf.getLong(index * BYTES_PER_ELEMENT);
        // Convert unsigned long to double (may lose precision for very large values)
        return Long.compareUnsigned(value, 0) < 0 ?
                (double) (value & Long.MAX_VALUE) + Math.pow(2, 63) :
                (double) value;
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        // Convert double to unsigned long
        long longValue = (long) value;
        buf.putLong(index * BYTES_PER_ELEMENT, longValue);
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

        return new JSBigUint64Array(buffer, newByteOffset, newLength);
    }
}
