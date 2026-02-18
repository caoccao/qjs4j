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
 * Represents a JavaScript BigUint64Array.
 * 64-bit unsigned integer array.
 */
public final class JSBigUint64Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 8;
    public static final String NAME = "BigUint64Array";

    /**
     * Create a BigUint64Array with a new buffer.
     */
    public JSBigUint64Array(int length) {
        super(length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a BigUint64Array view on an existing buffer.
     */
    public JSBigUint64Array(JSArrayBufferable buffer, int byteOffset, int length) {
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
                return context.createJSBigUint64Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / BYTES_PER_ELEMENT);
            } else if (firstArg instanceof JSTypedArray jsTypedArray) {
                length = jsTypedArray.getLength();
                JSTypedArray newTypedArray = context.createJSBigUint64Array(length);
                newTypedArray.setArray(context, jsTypedArray, 0);
                return newTypedArray;
            } else if (firstArg instanceof JSArray jsArray) {
                length = (int) jsArray.getLength();
                JSTypedArray jsTypedArray = context.createJSBigUint64Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSIterator jsIterator) {
                JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
                length = (int) jsArray.getLength();
                JSTypedArray jsTypedArray = context.createJSBigUint64Array(length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSObject jsObject) {
                JSValue lengthValue = jsObject.get(PropertyKey.LENGTH, context);
                length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, lengthValue));
                JSTypedArray jsTypedArray = context.createJSBigUint64Array(length);
                jsTypedArray.setArray(context, jsObject, 0);
                return jsTypedArray;
            } else {
                length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, firstArg));
            }
        }
        return context.createJSBigUint64Array(length);
    }

    @Override
    public double getElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        long value = buf.getLong(index * BYTES_PER_ELEMENT);
        // Convert unsigned long to double (may lose precision for very large values)
        return Long.compareUnsigned(value, 0) < 0 ?
                (double) (value & Long.MAX_VALUE) + Math.pow(2, 63) :
                (double) value;
    }

    @Override
    public JSValue getJSElement(int index) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        long value = buf.getLong(index * BYTES_PER_ELEMENT);
        // Convert unsigned long to unsigned BigInteger
        BigInteger unsigned = value >= 0
                ? BigInteger.valueOf(value)
                : BigInteger.valueOf(value).add(BigInteger.ONE.shiftLeft(64));
        return new JSBigInt(unsigned);
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        // Convert double to unsigned long
        long longValue = (long) value;
        buf.putLong(index * BYTES_PER_ELEMENT, longValue);
    }

    @Override
    protected void setJSElement(int index, JSValue value, JSContext context) {
        long longVal = JSTypeConversions.toBigInt64(context, value);
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        // Write raw 64-bit modulo value directly to avoid precision loss via double.
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

        return new JSBigUint64Array(buffer, newByteOffset, newLength);
    }
}
