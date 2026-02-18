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
 * Represents a JavaScript Uint8Array.
 * 8-bit unsigned integer array.
 */
public final class JSUint8Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 1;
    public static final String NAME = "Uint8Array";

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

    public static JSObject create(JSContext context, JSValue... args) {
        int length = 0;
        if (args.length >= 1) {
            JSValue firstArg = args[0];
            if (firstArg instanceof JSNumber lengthNum) {
                length = (int) JSTypeConversions.toIndex(context, lengthNum);
            } else if (firstArg instanceof JSArrayBufferable jsArrayBufferable) {
                length = -1;
                int byteOffset = 0;
                if (args.length >= 2) {
                    byteOffset = (int) JSTypeConversions.toInteger(context, args[1]);
                }
                if (args.length >= 3) {
                    length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, args[2]));
                }
                return new JSUint8Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / BYTES_PER_ELEMENT);
            } else if (firstArg instanceof JSTypedArray jsTypedArray) {
                length = jsTypedArray.getLength();
                JSTypedArray newTypedArray = new JSUint8Array(length);
                newTypedArray.setArray(context, jsTypedArray, 0);
                return newTypedArray;
            } else if (firstArg instanceof JSArray jsArray) {
                length = (int) jsArray.getLength();
                JSTypedArray jsTypedArray = new JSUint8Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSIterator jsIterator) {
                JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
                length = (int) jsArray.getLength();
                JSTypedArray jsTypedArray = new JSUint8Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSObject jsObject) {
                length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, jsObject.get("length")));
                JSTypedArray jsTypedArray = new JSUint8Array(length);
                for (int i = 0; i < length; i++) {
                    jsTypedArray.setElement(i, JSTypeConversions.toNumber(context, jsObject.get(i)).value());
                }
                return jsTypedArray;
            } else {
                length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, firstArg));
            }
        }
        JSObject jsObject = new JSUint8Array(length);
        context.transferPrototype(jsObject, NAME);
        return jsObject;
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
}
