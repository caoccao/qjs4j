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
    public static final String NAME = "Float16Array";

    /**
     * Create a Float16Array with a new buffer.
     */
    public JSFloat16Array(JSContext context, int length) {
        super(context, length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a Float16Array view on an existing buffer.
     */
    public JSFloat16Array(JSContext context, IJSArrayBuffer buffer, int byteOffset, int length) {
        super(context, buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return createFromArguments(context, BYTES_PER_ELEMENT,
                context::createJSFloat16Array, context::createJSFloat16Array, args);
    }

    @Override
    protected JSTypedArray createView(int byteOffset, int length) {
        return new JSFloat16Array(context, buffer, byteOffset, length);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        short halfFloat = buf.getShort(index * BYTES_PER_ELEMENT);
        return Float16.toFloat(halfFloat);
    }

    @Override
    public String getObjectTag() {
        return "[object " + NAME + "]";
    }

    @Override
    public String getTypedArrayName() {
        return NAME;
    }

    @Override
    public boolean isAtomicsReadableAndWriteable() {
        return false;
    }

    @Override
    public boolean isAtomicsWriteable() {
        return false;
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        short halfFloat = Float16.toHalf(value);
        buf.putShort(index * BYTES_PER_ELEMENT, halfFloat);
    }
}
