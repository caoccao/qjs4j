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

/**
 * Represents a JavaScript Uint8Array. 8-bit unsigned integer array.
 */
public final class JSUint8Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 1;
    public static final String NAME = "Uint8Array";

    /**
     * Create a Uint8Array view on an existing buffer.
     */
    public JSUint8Array(JSContext context, IJSArrayBuffer buffer, long byteOffset, long length) {
        super(context, buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a Uint8Array with a new buffer.
     */
    public JSUint8Array(JSContext context, long length) {
        super(context, length, BYTES_PER_ELEMENT);
    }

    @Override
    TypedArrayKind elementKind() {
        return TypedArrayKind.UINT8;
    }

    @Override
    public double getElement(long index) {
        checkIndex(index);
        JSByteBuffer buf = getByteBuffer();
        return buf.get(index) & 0xFF; // Convert to unsigned
    }

    @Override
    public void setElement(long index, double value) {
        checkIndex(index);
        JSByteBuffer buf = getByteBuffer();
        buf.put(index, (byte) JSTypeConversions.toInt32(value));
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return TypedArrayKind.UINT8.create(context, args);
    }
}
