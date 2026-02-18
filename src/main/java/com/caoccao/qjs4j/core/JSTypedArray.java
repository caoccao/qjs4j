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
            throw new JSRangeErrorException("invalid length");
        }
        if (length > Integer.MAX_VALUE / bytesPerElement) {
            throw new JSRangeErrorException("invalid array buffer length");
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
            if (length > Integer.MAX_VALUE / bytesPerElement) {
                throw new JSRangeErrorException("invalid length");
            }
            this.byteLength = length * bytesPerElement;
            if ((long) byteOffset + this.byteLength > buffer.getByteLength()) {
                throw new JSRangeErrorException("TypedArray extends beyond buffer");
            }
        }

        this.buffer = buffer;
    }

    /**
     * TypedArray constructor source normalization for object arguments.
     *
     * <p>If @@iterator exists and is callable, constructors must consume the iterable.
     * If @@iterator exists but is not callable, throw TypeError. If absent/nullish,
     * keep array-like semantics.</p>
     */
    protected static JSValue normalizeConstructorSource(JSContext context, JSValue source) {
        if (!(source instanceof JSObject sourceObject)
                || source instanceof JSArray
                || source instanceof JSIterator
                || source instanceof JSTypedArray
                || source instanceof JSArrayBufferable) {
            return source;
        }

        JSValue iteratorMethod = sourceObject.get(PropertyKey.SYMBOL_ITERATOR, context);
        if (context.hasPendingException()) {
            return source;
        }
        if (iteratorMethod instanceof JSUndefined || iteratorMethod instanceof JSNull) {
            return source;
        }
        if (!(iteratorMethod instanceof JSFunction)) {
            context.throwTypeError("Symbol.iterator is not a function");
            return source;
        }

        JSArray iterableValues = JSIteratorHelper.iterableToList(context, source);
        if (context.hasPendingException() || iterableValues == null) {
            return source;
        }
        return iterableValues;
    }

    protected static int toArrayLikeLength(JSContext context, JSValue value) {
        long length = JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, value));
        if (length > Integer.MAX_VALUE) {
            throw new JSRangeErrorException("invalid array length");
        }
        return (int) length;
    }

    protected static int toTypedArrayIndex(JSContext context, JSValue value, int bytesPerElement) {
        return toTypedArrayLengthChecked(JSTypeConversions.toIndex(context, value), bytesPerElement);
    }

    protected static int toTypedArrayLength(JSContext context, JSValue value, int bytesPerElement) {
        return toTypedArrayLengthChecked(
                JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, value)),
                bytesPerElement);
    }

    protected static int toTypedArrayLength(long length, int bytesPerElement) {
        return toTypedArrayLengthChecked(length, bytesPerElement);
    }

    private static int toTypedArrayLengthChecked(long length, int bytesPerElement) {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new JSRangeErrorException("invalid length");
        }
        if (length > Integer.MAX_VALUE / (long) bytesPerElement) {
            throw new JSRangeErrorException("invalid array buffer length");
        }
        return (int) length;
    }

    /**
     * Try to interpret a PropertyKey as a typed array integer index.
     * Returns the index if it's a valid canonical numeric index string, or -1 otherwise.
     */
    private static int toTypedArrayIndex(PropertyKey key) {
        if (key.isSymbol()) {
            return -1;
        }
        String str = key.toPropertyString();
        if (str.isEmpty()) {
            return -1;
        }
        // Fast path: single digit
        char c = str.charAt(0);
        if (str.length() == 1 && c >= '0' && c <= '9') {
            return c - '0';
        }
        // Must start with a digit (not '-' or '+')
        if (c < '0' || c > '9') {
            return -1;
        }
        // No leading zeros (except "0" itself, handled above)
        if (c == '0') {
            return -1;
        }
        try {
            long val = Long.parseLong(str);
            if (val >= 0 && val <= Integer.MAX_VALUE) {
                return (int) val;
            }
        } catch (NumberFormatException e) {
            // not a valid index
        }
        return -1;
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

    @Override
    protected JSValue get(PropertyKey key, JSContext context, JSObject receiver) {
        int index = toTypedArrayIndex(key);
        if (index >= 0) {
            if (index < length && !buffer.isDetached()) {
                return getJSElement(index);
            }
            return JSUndefined.INSTANCE;
        }
        return super.get(key, context, receiver);
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
     * Get an element as the appropriate JSValue type.
     * Regular typed arrays return JSNumber, BigInt typed arrays override to return JSBigInt.
     */
    public JSValue getJSElement(int index) {
        return JSNumber.of(getElement(index));
    }

    /**
     * Get the number of elements.
     */
    public int getLength() {
        return length;
    }

    @Override
    public boolean has(PropertyKey key) {
        int index = toTypedArrayIndex(key);
        if (index >= 0) {
            return index < length && !buffer.isDetached();
        }
        return super.has(key);
    }

    @Override
    public void set(PropertyKey key, JSValue value, JSContext context) {
        int index = toTypedArrayIndex(key);
        if (index >= 0) {
            if (index < length && !buffer.isDetached()) {
                setJSElement(index, value, context);
            }
            return;
        }
        super.set(key, value, context);
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
            int srcLength = toArrayLikeLength(context, JSNumber.of(srcArray.getLength()));
            if (offset + srcLength > length) {
                throw new JSRangeErrorException("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                JSValue value = srcArray.get(PropertyKey.fromIndex(i), context);
                if (context != null && context.hasPendingException()) {
                    return;
                }
                setJSElement(offset + i, value, context);
                if (context != null && context.hasPendingException()) {
                    return;
                }
            }
        } else if (source instanceof JSTypedArray srcTyped) {
            int srcLength = srcTyped.getLength();
            if (offset + srcLength > length) {
                throw new JSRangeErrorException("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                setJSElement(offset + i, srcTyped.getJSElement(i), context);
                if (context != null && context.hasPendingException()) {
                    return;
                }
            }
        } else if (source instanceof JSIterator jsIterator) {
            JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
            if (context != null && context.hasPendingException()) {
                return;
            }
            setArray(context, jsArray, offset);
        } else if (source instanceof JSObject srcObject) {
            JSValue lengthValue = srcObject.get(PropertyKey.LENGTH, context);
            if (context != null && context.hasPendingException()) {
                return;
            }
            int srcLength = toArrayLikeLength(context, lengthValue);
            if (context != null && context.hasPendingException()) {
                return;
            }
            if (offset + srcLength > length) {
                throw new JSRangeErrorException("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                JSValue value = srcObject.get(PropertyKey.fromIndex(i), context);
                if (context != null && context.hasPendingException()) {
                    return;
                }
                setJSElement(offset + i, value, context);
                if (context != null && context.hasPendingException()) {
                    return;
                }
            }
        }
    }

    /**
     * Set an element from a number.
     */
    public abstract void setElement(int index, double value);

    /**
     * Set an element from a JSValue, performing the appropriate type conversion.
     * Regular typed arrays convert to Number, BigInt typed arrays override to convert to BigInt.
     */
    protected void setJSElement(int index, JSValue value, JSContext context) {
        setElement(index, JSTypeConversions.toNumber(context, value).value());
    }

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
