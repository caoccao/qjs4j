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
import java.util.StringJoiner;

/**
 * Represents a JavaScript Uint8Array.
 * 8-bit unsigned integer array.
 */
public final class JSUint8Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 1;

    /**
     * Create a Uint8Array with a new buffer.
     */
    public JSUint8Array(int length) {
        super(length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a Uint8Array view on an existing buffer.
     */
    public JSUint8Array(JSArrayBufferable buffer, int byteOffset, int length) {
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
        int intValue = (int) value;
        buf.put(index, (byte) (intValue & 0xFF));
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

        return new JSUint8Array(buffer, newByteOffset, newLength);
    }

    @Override
    public String toString() {
        StringJoiner stringJoiner = new StringJoiner(",");
        for (byte b : getByteBuffer().array()) {
            stringJoiner.add(Integer.toString(b & 0xFF));
        }
        return stringJoiner.toString();
    }
}
