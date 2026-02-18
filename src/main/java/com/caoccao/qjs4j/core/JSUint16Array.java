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
 * Represents a JavaScript Uint16Array.
 * 16-bit unsigned integer array.
 */
public final class JSUint16Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 2;
    public static final String NAME = "Uint16Array";

    /**
     * Create a Uint16Array with a new buffer.
     */
    public JSUint16Array(int length) {
        super(length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a Uint16Array view on an existing buffer.
     */
    public JSUint16Array(JSArrayBufferable buffer, int byteOffset, int length) {
        super(buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        int length = 0;
        if (args.length >= 1) {
            JSValue firstArg = normalizeConstructorSource(context, args[0]);
            if (context.hasPendingException()) {
                return null;
            }
            if (firstArg instanceof JSNumber lengthNum) {
                length = toTypedArrayIndex(context, lengthNum, BYTES_PER_ELEMENT);
            } else if (firstArg instanceof JSArrayBufferable jsArrayBufferable) {
                int byteOffset = 0;
                if (args.length >= 2) {
                    byteOffset = toTypedArrayByteOffset(context, args[1]);
                }
                if (args.length >= 3 && !(args[2] instanceof JSUndefined)) {
                    length = toTypedArrayBufferLength(context, args[2], BYTES_PER_ELEMENT);
                    return context.createJSUint16Array(jsArrayBufferable, byteOffset, length);
                }
                length = toTypedArrayBufferDefaultLength(jsArrayBufferable, byteOffset, BYTES_PER_ELEMENT);
                return context.createJSUint16Array(jsArrayBufferable, byteOffset, length);
            } else if (firstArg instanceof JSTypedArray jsTypedArray) {
                length = jsTypedArray.getLength();
                JSTypedArray newTypedArray = context.createJSUint16Array(length);
                newTypedArray.setArray(context, jsTypedArray, 0);
                return newTypedArray;
            } else if (firstArg instanceof JSArray jsArray) {
                length = toTypedArrayLength(jsArray.getLength(), BYTES_PER_ELEMENT);
                JSTypedArray jsTypedArray = context.createJSUint16Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSIterator jsIterator) {
                JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
                length = toTypedArrayLength(jsArray.getLength(), BYTES_PER_ELEMENT);
                JSTypedArray jsTypedArray = context.createJSUint16Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSObject jsObject) {
                JSValue lengthValue = jsObject.get(PropertyKey.LENGTH, context);
                length = toTypedArrayLength(context, lengthValue, BYTES_PER_ELEMENT);
                JSTypedArray jsTypedArray = context.createJSUint16Array(length);
                jsTypedArray.setArray(context, jsObject, 0);
                return jsTypedArray;
            } else {
                length = toTypedArrayLength(context, firstArg, BYTES_PER_ELEMENT);
            }
        }
        return context.createJSUint16Array(length);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        return buf.getShort(index * BYTES_PER_ELEMENT) & 0xFFFF; // Convert to unsigned
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        buf.putShort(index * BYTES_PER_ELEMENT, (short) JSTypeConversions.toInt32(value));
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

        return new JSUint16Array(buffer, newByteOffset, newLength);
    }
}
