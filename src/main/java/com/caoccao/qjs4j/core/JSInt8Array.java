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
 * Represents a JavaScript Int8Array.
 * 8-bit signed integer array.
 */
public final class JSInt8Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 1;
    public static final String NAME = "Int8Array";

    /**
     * Create an Int8Array with a new buffer.
     */
    public JSInt8Array(JSContext context, int length) {
        super(context, length, BYTES_PER_ELEMENT);
    }

    /**
     * Create an Int8Array view on an existing buffer.
     */
    public JSInt8Array(JSContext context, IJSArrayBuffer buffer, int byteOffset, int length) {
        super(context, buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return createFromArguments(context, BYTES_PER_ELEMENT,
                context::createJSInt8Array, context::createJSInt8Array, args);
    }

    @Override
    protected JSTypedArray createView(int byteOffset, int length) {
        return new JSInt8Array(context, buffer, byteOffset, length);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        return buf.get(index);
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
        return false;
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        buf.put(index, (byte) JSTypeConversions.toInt32(value));
    }
}
