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

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * Represents a JavaScript BigInt64Array.
 * 64-bit signed integer array.
 */
public final class JSBigInt64Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 8;
    public static final String NAME = "BigInt64Array";

    /**
     * Create a BigInt64Array with a new buffer.
     */
    public JSBigInt64Array(int length) {
        super(length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a BigInt64Array view on an existing buffer.
     */
    public JSBigInt64Array(JSArrayBufferable buffer, int byteOffset, int length) {
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
                return context.createJSBigInt64Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / BYTES_PER_ELEMENT);
            } else if (firstArg instanceof JSTypedArray jsTypedArray) {
                length = jsTypedArray.getLength();
                JSTypedArray newTypedArray = context.createJSBigInt64Array(length);
                newTypedArray.setArray(context, jsTypedArray, 0);
                return newTypedArray;
            } else if (firstArg instanceof JSArray jsArray) {
                length = (int) jsArray.getLength();
                JSTypedArray jsTypedArray = context.createJSBigInt64Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSIterator jsIterator) {
                JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
                length = (int) jsArray.getLength();
                JSTypedArray jsTypedArray = context.createJSBigInt64Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSObject jsObject) {
                JSValue lengthValue = jsObject.get(PropertyKey.LENGTH, context);
                length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, lengthValue));
                JSTypedArray jsTypedArray = context.createJSBigInt64Array(length);
                jsTypedArray.setArray(context, jsObject, 0);
                return jsTypedArray;
            } else {
                length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, firstArg));
            }
        }
        return context.createJSBigInt64Array(length);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        return buf.getLong(index * BYTES_PER_ELEMENT);
    }

    @Override
    public JSValue getJSElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        return new JSBigInt(BigInteger.valueOf(buf.getLong(index * BYTES_PER_ELEMENT)));
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        buf.putLong(index * BYTES_PER_ELEMENT, (long) value);
    }

    @Override
    protected void setJSElement(int index, JSValue value, JSContext context) {
        long longVal = JSTypeConversions.toBigInt64(context, value);
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        // Write raw signed 64-bit value directly to avoid precision loss via double.
        buf.putLong(index * BYTES_PER_ELEMENT, longVal);
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

        return new JSBigInt64Array(buffer, newByteOffset, newLength);
    }
}
