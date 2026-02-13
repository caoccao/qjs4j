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

import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

import java.nio.ByteBuffer;

/**
 * Base class for JavaScript TypedArray objects.
 * Based on ES2020 TypedArray specification.
 * <p>
 * TypedArrays provide an array-like view of an underlying ArrayBuffer.
 */
public abstract class JSTypedArray extends JSObject {
    protected final JSArrayBufferable buffer;
    protected final int byteLength;
    protected final int byteOffset;
    protected final int bytesPerElement;
    protected final int length;

    /**
     * Create a TypedArray with a new buffer of the given length.
     */
    protected JSTypedArray(int length, int bytesPerElement) {
        super();
        if (length < 0) {
            throw new IllegalArgumentException("TypedArray length must be non-negative");
        }
        this.bytesPerElement = bytesPerElement;
        this.length = length;
        this.byteLength = length * bytesPerElement;
        this.byteOffset = 0;
        this.buffer = new JSArrayBuffer(this.byteLength);
    }

    /**
     * Create a TypedArray view on an existing ArrayBuffer or SharedArrayBuffer.
     */
    protected JSTypedArray(JSArrayBufferable buffer, int byteOffset, int length, int bytesPerElement) {
        super();
        if (buffer == null || buffer.isDetached()) {
            throw new IllegalArgumentException("Cannot create TypedArray on detached buffer");
        }

        this.bytesPerElement = bytesPerElement;

        // Validate and normalize byteOffset
        if (byteOffset < 0 || byteOffset % bytesPerElement != 0) {
            throw new JSRangeErrorException("TypedArray byteOffset must be aligned");
        }
        if (byteOffset > buffer.getByteLength()) {
            throw new JSRangeErrorException("TypedArray byteOffset out of range");
        }
        this.byteOffset = byteOffset;

        // Calculate length if not specified
        if (length < 0) {
            int remainingBytes = buffer.getByteLength() - byteOffset;
            if (remainingBytes % bytesPerElement != 0) {
                throw new JSRangeErrorException("Buffer byte length must be a multiple of element size");
            }
            this.length = remainingBytes / bytesPerElement;
            this.byteLength = remainingBytes;
        } else {
            this.length = length;
            this.byteLength = length * bytesPerElement;
            if (byteOffset + this.byteLength > buffer.getByteLength()) {
                throw new JSRangeErrorException("TypedArray extends beyond buffer");
            }
        }

        this.buffer = buffer;
    }

    /**
     * Check if an index is valid.
     */
    protected void checkIndex(int index) {
        if (buffer.isDetached()) {
            throw new IllegalStateException("TypedArray buffer is detached");
        }
        if (index < 0 || index >= length) {
            throw new JSRangeErrorException("TypedArray index out of range: " + index);
        }
    }

    protected String formatElement(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == 0) {
            return "0";
        }
        long asLong = (long) value;
        if (value == asLong) {
            return Long.toString(asLong);
        }
        return Double.toString(value);
    }

    /**
     * Get the underlying ArrayBuffer or SharedArrayBuffer.
     */
    public JSArrayBufferable getBuffer() {
        return buffer;
    }

    /**
     * Get the ByteBuffer for direct access.
     */
    protected ByteBuffer getByteBuffer() {
        if (buffer.isDetached()) {
            throw new IllegalStateException("TypedArray buffer is detached");
        }
        ByteBuffer buf = buffer.getBuffer().duplicate();
        buf.position(byteOffset);
        buf.limit(byteOffset + byteLength);
        return buf.slice();
    }

    /**
     * Get the byte length of this view.
     */
    public int getByteLength() {
        return byteLength;
    }

    /**
     * Get the byte offset within the buffer.
     */
    public int getByteOffset() {
        return byteOffset;
    }

    /**
     * Get the number of bytes per element.
     */
    public int getBytesPerElement() {
        return bytesPerElement;
    }

    /**
     * Get an element as a number.
     */
    public abstract double getElement(int index);

    /**
     * Get the number of elements.
     */
    public int getLength() {
        return length;
    }

    /**
     * TypedArray.prototype.set(array, offset)
     * Copy values from array into this TypedArray.
     */
    public void setArray(JSContext context, JSValue source, int offset) {
        if (offset < 0 || offset > length) {
            throw new JSRangeErrorException("TypedArray offset out of range");
        }

        if (source instanceof JSArray srcArray) {
            int srcLength = (int) srcArray.getLength();
            if (offset + srcLength > length) {
                throw new JSRangeErrorException("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                JSValue value = srcArray.get(i);
                double numVal = JSTypeConversions.toNumber(context, value).value();
                setElement(offset + i, numVal);
            }
        } else if (source instanceof JSTypedArray srcTyped) {
            int srcLength = srcTyped.getLength();
            if (offset + srcLength > length) {
                throw new JSRangeErrorException("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                setElement(offset + i, srcTyped.getElement(i));
            }
        } else if (source instanceof JSIterator jsIterator) {
            JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
            setArray(context, jsArray, offset);
        } else if (source instanceof JSObject srcObject) {
            int srcLength = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, srcObject.get("length")));
            if (offset + srcLength > length) {
                throw new JSRangeErrorException("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                JSValue value = srcObject.get(i);
                if (value.isUndefined()) {
                    value = srcObject.get(String.valueOf(i));
                }
                setElement(offset + i, JSTypeConversions.toNumber(context, value).value());
            }
        }
    }

    /**
     * Set an element from a number.
     */
    public abstract void setElement(int index, double value);

    /**
     * TypedArray.prototype.subarray(begin, end)
     * Returns a new TypedArray view on the same buffer.
     */
    public abstract JSTypedArray subarray(int begin, int end);

    @Override
    public String toString() {
        if (length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(formatElement(getElement(i)));
        }
        return sb.toString();
    }
}
