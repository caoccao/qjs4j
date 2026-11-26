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
 * Represents a JavaScript Int16Array. 16-bit signed integer array.
 */
public final class JSInt16Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 2;
    public static final String NAME = "Int16Array";

    /**
     * Create an Int16Array view on an existing buffer.
     */
    public JSInt16Array(JSContext context, IJSArrayBuffer buffer, long byteOffset, long length) {
        super(context, buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    /**
     * Create an Int16Array with a new buffer.
     */
    public JSInt16Array(JSContext context, long length) {
        super(context, length, BYTES_PER_ELEMENT);
    }

    @Override
    TypedArrayKind elementKind() {
        return TypedArrayKind.INT16;
    }

    @Override
    double readElement(JSByteBuffer buf, long index) {
        return buf.getShort(index * BYTES_PER_ELEMENT);
    }

    @Override
    public void setElement(long index, double value) {
        checkIndex(index);
        JSByteBuffer buf = getByteBuffer();
        buf.putShort(index * BYTES_PER_ELEMENT, (short) JSTypeConversions.toInt32(value));
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return TypedArrayKind.INT16.create(context, args);
    }
}
