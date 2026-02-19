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
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

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
    protected final boolean trackRab;

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
        this.trackRab = false;
    }

    /**
     * Create a TypedArray view on an existing ArrayBuffer or SharedArrayBuffer.
     */
    protected JSTypedArray(JSArrayBufferable buffer, int byteOffset, int length, int bytesPerElement) {
        super();
        if (buffer == null) {
            throw new IllegalArgumentException("Cannot create TypedArray on null buffer");
        }
        if (buffer.isDetached()) {
            throw new JSTypeErrorException("ArrayBuffer is detached");
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
            // Track resizable array buffer length when no explicit length given
            boolean resizable = buffer instanceof JSArrayBuffer ab && ab.isResizable();
            this.trackRab = resizable;
            int remainingBytes = buffer.getByteLength() - byteOffset;
            if (!resizable && remainingBytes % bytesPerElement != 0) {
                throw new JSRangeErrorException("Buffer byte length must be a multiple of element size");
            }
            this.length = remainingBytes / bytesPerElement;
            this.byteLength = remainingBytes;
        } else {
            this.trackRab = false;
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

    protected static int toTypedArrayBufferDefaultLength(JSArrayBufferable buffer, int byteOffset, int bytesPerElement) {
        // QuickJS / spec order: detached buffer check must happen before range/alignment checks.
        // This ensures abrupt detachment during byteOffset conversion throws TypeError, not RangeError.
        validateTypedArrayBufferNotDetached(buffer);
        int remainingBytes = buffer.getByteLength() - byteOffset;
        if (remainingBytes < 0 || remainingBytes % bytesPerElement != 0) {
            throw new JSRangeErrorException("invalid length");
        }
        return remainingBytes / bytesPerElement;
    }

    protected static int toTypedArrayBufferLength(JSContext context, JSValue value, int bytesPerElement) {
        return toTypedArrayLengthChecked(JSTypeConversions.toIndex(context, value), bytesPerElement);
    }

    protected static int toTypedArrayByteOffset(JSContext context, JSValue value) {
        long offset = JSTypeConversions.toIndex(context, value);
        if (offset > Integer.MAX_VALUE) {
            throw new JSRangeErrorException("invalid offset");
        }
        return (int) offset;
    }

    protected static int toTypedArrayIndex(JSContext context, JSValue value, int bytesPerElement) {
        return toTypedArrayLengthChecked(JSTypeConversions.toIndex(context, value), bytesPerElement);
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

    protected static void validateTypedArrayBufferNotDetached(JSArrayBufferable buffer) {
        if (buffer.isDetached()) {
            throw new JSTypeErrorException("ArrayBuffer is detached");
        }
    }

    /**
     * Check if an index is valid.
     */
    protected void checkIndex(int index) {
        if (buffer.isDetached()) {
            throw new IllegalStateException("TypedArray buffer is detached");
        }
        if (index < 0 || index >= getLength()) {
            throw new JSRangeErrorException("TypedArray index out of range: " + index);
        }
    }

    /**
     * Integer-Indexed exotic object [[Delete]].
     * Returns false for valid in-bounds indices (elements are non-configurable).
     * Following QuickJS delete_property() for typed arrays.
     */
    @Override
    public boolean delete(PropertyKey key, JSContext context) {
        int index = toTypedArrayIndex(key);
        if (index >= 0) {
            // In-bounds element: not deletable
            if (index < getLength() && !buffer.isDetached()) {
                if (context != null && context.isStrictMode()) {
                    context.throwTypeError("Cannot delete property '" + index + "'");
                }
                return false;
            }
            // Out-of-bounds or detached: canonical numeric index returns true
            return true;
        }
        return super.delete(key, context);
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
            if (index < getLength() && !buffer.isDetached()) {
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
        buf.limit(byteOffset + getByteLength());
        return buf.slice();
    }

    /**
     * Get the byte length of this view.
     * For length-tracking typed arrays on resizable buffers, returns the current effective byte length.
     */
    public int getByteLength() {
        if (trackRab) {
            if (buffer.isDetached()) {
                return 0;
            }
            int currentByteLength = buffer.getByteLength();
            if (byteOffset > currentByteLength) {
                return 0;
            }
            return currentByteLength - byteOffset;
        }
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
     * For length-tracking typed arrays on resizable buffers, returns the current effective length.
     */
    public int getLength() {
        if (trackRab) {
            if (buffer.isDetached()) {
                return 0;
            }
            int currentByteLength = buffer.getByteLength();
            if (byteOffset > currentByteLength) {
                return 0;
            }
            return (currentByteLength - byteOffset) / bytesPerElement;
        }
        return length;
    }

    @Override
    public boolean has(PropertyKey key) {
        int index = toTypedArrayIndex(key);
        if (index >= 0) {
            return index < getLength() && !buffer.isDetached();
        }
        return super.has(key);
    }

    /**
     * Check if this TypedArray is out of bounds due to buffer resize or detach.
     * Following QuickJS typed_array_is_oob semantics.
     */
    public boolean isOutOfBounds() {
        if (buffer.isDetached()) {
            return true;
        }
        int currentByteLength = buffer.getByteLength();
        if (byteOffset > currentByteLength) {
            return true;
        }
        if (trackRab) {
            return false;
        }
        // Fixed length: check if the view exceeds the current buffer size
        return (long) byteOffset + byteLength > currentByteLength;
    }

    @Override
    public void set(PropertyKey key, JSValue value, JSContext context) {
        int index = toTypedArrayIndex(key);
        if (index >= 0) {
            if (index < getLength() && !buffer.isDetached()) {
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
        int currentLength = getLength();
        if (offset < 0 || offset > currentLength) {
            throw new JSRangeErrorException("TypedArray offset out of range");
        }

        if (source instanceof JSArray srcArray) {
            int srcLength = toArrayLikeLength(context, JSNumber.of(srcArray.getLength()));
            if (offset + srcLength > currentLength) {
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
            if (offset + srcLength > currentLength) {
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
            if (offset + srcLength > currentLength) {
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
        int currentLength = getLength();
        if (currentLength == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentLength; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(formatElement(getElement(i)));
        }
        return sb.toString();
    }
}
