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
    public static final String NAME = "Uint8ClampedArray";

    /**
     * Create a Uint8ClampedArray with a new buffer.
     */
    public JSUint8ClampedArray(JSContext context, int length) {
        super(context, length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a Uint8ClampedArray view on an existing buffer.
     */
    public JSUint8ClampedArray(JSContext context, IJSArrayBuffer buffer, int byteOffset, int length) {
        super(context, buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return createFromArguments(context, BYTES_PER_ELEMENT,
                context::createJSUint8ClampedArray, context::createJSUint8ClampedArray, args);
    }

    @Override
    protected JSTypedArray createView(int byteOffset, int length) {
        return new JSUint8ClampedArray(context, buffer, byteOffset, length);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        return buf.get(index) & 0xFF; // Convert to unsigned
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
        int clampedValue = JSTypeConversions.toUint8Clamp(value);
        buf.put(index, (byte) clampedValue);
    }
}
