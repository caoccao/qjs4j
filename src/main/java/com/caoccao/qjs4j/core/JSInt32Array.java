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
 * Represents a JavaScript Int32Array.
 * 32-bit signed integer array.
 */
public final class JSInt32Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 4;
    public static final String NAME = "Int32Array";

    /**
     * Create an Int32Array with a new buffer.
     */
    public JSInt32Array(JSContext context, int length) {
        super(context, length, BYTES_PER_ELEMENT);
    }

    /**
     * Create an Int32Array view on an existing buffer.
     */
    public JSInt32Array(JSContext context, IJSArrayBuffer buffer, int byteOffset, int length) {
        super(context, buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return createFromArguments(context, BYTES_PER_ELEMENT,
                context::createJSInt32Array, context::createJSInt32Array, args);
    }

    @Override
    protected JSTypedArray createView(int byteOffset, int length) {
        return new JSInt32Array(context, buffer, byteOffset, length);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        return buf.getInt(index * BYTES_PER_ELEMENT);
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
        return true;
    }

    @Override
    public boolean isAtomicsWriteable() {
        return true;
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        buf.putInt(index * BYTES_PER_ELEMENT, JSTypeConversions.toInt32(value));
    }
}
