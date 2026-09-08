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
 * Represents a JavaScript BigInt64Array.
 * 64-bit signed integer array.
 */
public final class JSBigInt64Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 8;
    public static final String NAME = "BigInt64Array";

    /**
     * Create a BigInt64Array with a new buffer.
     */
    public JSBigInt64Array(JSContext context, int length) {
        super(context, length, BYTES_PER_ELEMENT);
    }

    /**
     * Create a BigInt64Array view on an existing buffer.
     */
    public JSBigInt64Array(JSContext context, IJSArrayBuffer buffer, int byteOffset, int length) {
        super(context, buffer, byteOffset, length, BYTES_PER_ELEMENT);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return createFromArguments(context, BYTES_PER_ELEMENT,
                context::createJSBigInt64Array, context::createJSBigInt64Array, args);
    }

    @Override
    protected JSTypedArray createView(int byteOffset, int length) {
        return new JSBigInt64Array(context, buffer, byteOffset, length);
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
        return true;
    }

    @Override
    public void setElement(int index, double value) {
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        buf.putLong(index * BYTES_PER_ELEMENT, (long) value);
    }

    @Override
    protected void setJSElement(int index, JSValue value) {
        long longVal = JSTypeConversions.toBigInt64(context, value);
        checkIndex(index);
        ByteBuffer buf = getByteBuffer();
        // Write raw signed 64-bit value directly to avoid precision loss via double.
        buf.putLong(index * BYTES_PER_ELEMENT, longVal);
    }
}
