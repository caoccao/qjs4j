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
    public JSInt32Array(int length) {
        super(length, BYTES_PER_ELEMENT);
    }

    /**
     * Create an Int32Array view on an existing buffer.
     */
    public JSInt32Array(JSArrayBufferable buffer, int byteOffset, int length) {
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
                    return context.createJSInt32Array(jsArrayBufferable, byteOffset, length);
                }
                return context.createJSInt32Array(jsArrayBufferable, byteOffset, -1);
            } else if (firstArg instanceof JSTypedArray jsTypedArray) {
                if (jsTypedArray.isOutOfBounds()) {
                    context.throwTypeError("source TypedArray is out of bounds");
                    return null;
                }
                length = jsTypedArray.getLength();
                JSTypedArray newTypedArray = context.createJSInt32Array(length);
                newTypedArray.setArray(context, jsTypedArray, 0);
                return newTypedArray;
            } else if (firstArg instanceof JSArray jsArray) {
                length = toTypedArrayLength(jsArray.getLength(), BYTES_PER_ELEMENT);
                JSTypedArray jsTypedArray = context.createJSInt32Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSIterator jsIterator) {
                JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
                length = toTypedArrayLength(jsArray.getLength(), BYTES_PER_ELEMENT);
                JSTypedArray jsTypedArray = context.createJSInt32Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSObject jsObject) {
                JSValue lengthValue = jsObject.get(context, PropertyKey.LENGTH);
                length = toTypedArrayLength(context, lengthValue, BYTES_PER_ELEMENT);
                JSTypedArray jsTypedArray = context.createJSInt32Array(length);
                jsTypedArray.setArray(context, jsObject, 0);
                return jsTypedArray;
            } else {
                length = toTypedArrayLength(context, firstArg, BYTES_PER_ELEMENT);
            }
        }
        return context.createJSInt32Array(length);
    }


    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        return buf.getInt(index * BYTES_PER_ELEMENT);
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        buf.putInt(index * BYTES_PER_ELEMENT, JSTypeConversions.toInt32(value));
    }

    @Override
    public JSTypedArray subarray(int begin, int end) {
        // Normalize indices
        int currentLength = getLength();
        if (begin < 0) begin = Math.max(currentLength + begin, 0);
        else begin = Math.min(begin, currentLength);

        if (end < 0) end = Math.max(currentLength + end, 0);
        else end = Math.min(end, currentLength);

        int newLength = Math.max(end - begin, 0);
        int newByteOffset = byteOffset + begin * BYTES_PER_ELEMENT;

        return new JSInt32Array(buffer, newByteOffset, newLength);
    }
}
