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

import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

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
    public JSBigUint64Array(JSContext context, int length) {
        super(context, length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a BigUint64Array view on an existing buffer.
     */
    public JSBigUint64Array(JSContext context, IJSArrayBuffer buffer, int byteOffset, int length) {
        super(context, buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return createFromArguments(context, BYTES_PER_ELEMENT,
                context::createJSBigUint64Array, context::createJSBigUint64Array, args);
    }

    @Override
    protected JSTypedArray createView(int byteOffset, int length) {
        return new JSBigUint64Array(context, buffer, byteOffset, length);
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
    public String getObjectTag() {
        return "[object " + NAME + "]";
    }

    @Override
    public String getTypedArrayName() {
        return NAME;
    }

    @Override
    protected void integerIndexedElementSet(int index, JSValue value) {
        long longVal;
        try {
            longVal = JSTypeConversions.toBigInt64(context, value);
        } catch (JSTypeErrorException e) {
            context.throwTypeError(e.getMessage());
            return;
        } catch (JSSyntaxErrorException e) {
            context.throwSyntaxError(e.getMessage(), e.getSourceLocation());
            return;
        }
        if (!buffer.isDetached() && index >= 0 && index < getLength()) {
            ByteBuffer buf = getByteBuffer();
            buf.putLong(index * BYTES_PER_ELEMENT, longVal);
        }
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
        // Convert double to unsigned long
        long longValue = (long) value;
        buf.putLong(index * BYTES_PER_ELEMENT, longValue);
    }

    @Override
    protected void setJSElement(int index, JSValue value) {
        long longVal = JSTypeConversions.toBigInt64(context, value);
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        // Write raw 64-bit modulo value directly to avoid precision loss via double.
        buf.putLong(index * BYTES_PER_ELEMENT, longVal);
    }
}
