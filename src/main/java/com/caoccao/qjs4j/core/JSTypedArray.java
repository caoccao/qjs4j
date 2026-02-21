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
import com.caoccao.qjs4j.utils.DtoaConverter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for JavaScript TypedArray objects.
 * Based on ES2020 TypedArray specification.
 * <p>
 * TypedArrays provide an array-like view of an underlying ArrayBuffer.
 */
public abstract class JSTypedArray extends JSObject {
    public static final String NAME = "TypedArray";
    protected final IJSArrayBuffer buffer;
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
    protected JSTypedArray(IJSArrayBuffer buffer, int byteOffset, int length, int bytesPerElement) {
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
     * CanonicalNumericIndexString check per ES spec.
     * Returns true if the key is a canonical numeric index string
     * (i.e., ToString(ToNumber(str)) === str, or str is "-0").
     * Following QuickJS JS_AtomIsNumericIndex.
     */
    private static boolean isCanonicalNumericIndex(PropertyKey key) {
        if (key.isSymbol()) {
            return false;
        }
        String str = key.toPropertyString();
        if (str.isEmpty()) {
            return false;
        }
        if ("-0".equals(str)) {
            return true;
        }
        char first = str.charAt(0);
        if (!((first >= '0' && first <= '9') || first == '-' || first == 'I' || first == 'N')) {
            return false;
        }
        try {
            double num = Double.parseDouble(str);
            return numberToString(num).equals(str);
        } catch (NumberFormatException e) {
            return false;
        }
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
                || source instanceof IJSArrayBuffer) {
            return source;
        }

        JSValue iteratorMethod = sourceObject.get(context, PropertyKey.SYMBOL_ITERATOR);
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

    /**
     * Format a number the same way as JavaScript's ToString(Number).
     * Used by isCanonicalNumericIndex to check round-trip identity.
     */
    private static String numberToString(double value) {
        return DtoaConverter.convert(value);
    }

    protected static int toArrayLikeLength(JSContext context, JSValue value) {
        long length = JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, value));
        if (length > Integer.MAX_VALUE) {
            throw new JSRangeErrorException("invalid array length");
        }
        return (int) length;
    }

    protected static int toTypedArrayBufferDefaultLength(IJSArrayBuffer buffer, int byteOffset, int bytesPerElement) {
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

    protected static void validateTypedArrayBufferNotDetached(IJSArrayBuffer buffer) {
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
     * Integer-Indexed exotic object [[DefineOwnProperty]].
     * Following QuickJS JS_DefineProperty for typed arrays (lines 10176-10221).
     * For canonical numeric index strings: validates the index and descriptor,
     * sets the value if provided, and returns true/false per the spec.
     * For non-numeric keys: delegates to ordinary defineOwnProperty.
     */
    @Override
    public boolean defineOwnProperty(PropertyKey key, PropertyDescriptor descriptor, JSContext context) {
        if (!isCanonicalNumericIndex(key)) {
            return super.defineOwnProperty(key, descriptor, context);
        }
        String str = key.toPropertyString();
        // -0 is never a valid integer index
        if ("-0".equals(str)) {
            return false;
        }
        double numericIndex = Double.parseDouble(str);
        // Must be a finite integer
        if (!Double.isFinite(numericIndex) || numericIndex != Math.floor(numericIndex)) {
            return false;
        }
        // Must be non-negative
        if (numericIndex < 0) {
            return false;
        }
        int index = (int) numericIndex;
        // Must fit in int and match the double
        if (index != numericIndex) {
            return false;
        }
        // Check bounds and detachment
        if (buffer.isDetached() || index >= getLength()) {
            return false;
        }
        // Typed array elements are always {configurable: true, enumerable: true, writable: true}
        // Accessor descriptors are not allowed
        if (descriptor.isAccessorDescriptor()) {
            return false;
        }
        if (descriptor.hasConfigurable() && !descriptor.isConfigurable()) {
            return false;
        }
        if (descriptor.hasEnumerable() && !descriptor.isEnumerable()) {
            return false;
        }
        if (descriptor.hasWritable() && !descriptor.isWritable()) {
            return false;
        }
        // IntegerIndexedElementSet: convert value then write if buffer still valid
        if (descriptor.hasValue()) {
            integerIndexedElementSet(index, descriptor.getValue(), context);
        }
        return true;
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

    @Override
    public PropertyKey[] enumerableKeys() {
        List<PropertyKey> result = new ArrayList<>();
        if (!buffer.isDetached() && !isOutOfBounds()) {
            int len = getLength();
            for (int i = 0; i < len; i++) {
                result.add(PropertyKey.fromString(Integer.toString(i)));
            }
        }
        Collections.addAll(result, super.enumerableKeys());
        return result.toArray(new PropertyKey[0]);
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
     * Integer-Indexed exotic object [[Get]].
     * For canonical numeric index strings, return the element or undefined
     * without walking the prototype chain.
     */
    @Override
    protected JSValue get(JSContext context, PropertyKey key, JSObject receiver) {
        if (isCanonicalNumericIndex(key)) {
            // For canonical numeric indices, never fall through to prototype chain.
            String str = key.toPropertyString();
            if ("-0".equals(str)) {
                return JSUndefined.INSTANCE;
            }
            double numericIndex = Double.parseDouble(str);
            if (!Double.isFinite(numericIndex) || numericIndex != Math.floor(numericIndex) || numericIndex < 0) {
                return JSUndefined.INSTANCE;
            }
            int index = (int) numericIndex;
            if (index != numericIndex || buffer.isDetached() || isOutOfBounds() || index >= getLength()) {
                return JSUndefined.INSTANCE;
            }
            return getJSElement(index);
        }
        return super.get(context, key, receiver);
    }

    /**
     * Get the underlying ArrayBuffer or SharedArrayBuffer.
     */
    public IJSArrayBuffer getBuffer() {
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
        // Fixed-length views backed by resizable buffers return 0 when out of bounds
        if (isOutOfBounds()) {
            return 0;
        }
        return length;
    }

    /**
     * Integer-Indexed exotic object [[GetOwnProperty]].
     * Following QuickJS JS_GetOwnPropertyInternal for fast arrays (lines 8452-8467).
     * For valid integer indices: returns {value, writable: true, enumerable: true, configurable: true}.
     * For canonical numeric indices that are not valid integer indices: returns null.
     * For non-numeric keys: delegates to ordinary getOwnPropertyDescriptor.
     */
    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        if (!isCanonicalNumericIndex(key)) {
            return super.getOwnPropertyDescriptor(key);
        }
        String str = key.toPropertyString();
        if ("-0".equals(str)) {
            return null;
        }
        double numericIndex = Double.parseDouble(str);
        if (!Double.isFinite(numericIndex) || numericIndex != Math.floor(numericIndex) || numericIndex < 0) {
            return null;
        }
        int index = (int) numericIndex;
        if (index != numericIndex) {
            return null;
        }
        if (buffer.isDetached() || index >= getLength()) {
            return null;
        }
        return PropertyDescriptor.dataDescriptor(getJSElement(index), true, true, true);
    }

    /**
     * Integer-Indexed exotic object [[OwnPropertyKeys]].
     * Following QuickJS JS_GetOwnPropertyNamesInternal for fast arrays (lines 8334-8354).
     * Returns integer indices first (in ascending order), then string keys, then symbol keys.
     */
    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        List<PropertyKey> result = new ArrayList<>();
        // Add integer indices for buffer elements (only if not detached and not OOB)
        if (!buffer.isDetached() && !isOutOfBounds()) {
            int len = getLength();
            for (int i = 0; i < len; i++) {
                result.add(PropertyKey.fromString(Integer.toString(i)));
            }
        }
        // Add non-index own properties from the shape (string keys, then symbols)
        result.addAll(super.getOwnPropertyKeys());
        return result;
    }

    @Override
    public boolean has(PropertyKey key) {
        if (isCanonicalNumericIndex(key)) {
            String str = key.toPropertyString();
            if ("-0".equals(str)) {
                return false;
            }
            double numericIndex = Double.parseDouble(str);
            if (!Double.isFinite(numericIndex) || numericIndex != Math.floor(numericIndex) || numericIndex < 0) {
                return false;
            }
            int index = (int) numericIndex;
            if (index != numericIndex) {
                return false;
            }
            return !buffer.isDetached() && index < getLength();
        }
        return super.has(key);
    }

    /**
     * Integer-Indexed exotic object [[OwnPropertyKeys]].
     * Following QuickJS JS_GetOwnPropertyNamesInternal for fast arrays (lines 8334-8354).
     * Returns integer indices first (in ascending order), then string keys, then symbol keys.
     */

    /**
     * IntegerIndexedElementSet per ES spec.
     * Converts value to the appropriate type (ToNumber or ToBigInt), then writes
     * to the buffer only if it is still valid (not detached and index in bounds).
     * This handles the case where ToNumber/ToBigInt detaches the buffer as a side effect.
     * BigInt typed arrays override to use ToBigInt instead of ToNumber.
     */
    protected void integerIndexedElementSet(int index, JSValue value, JSContext context) {
        double numValue = JSTypeConversions.toNumber(context, value).value();
        if (!buffer.isDetached() && index >= 0 && index < getLength()) {
            setElement(index, numValue);
        }
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
    public PropertyKey[] ownPropertyKeys() {
        return getOwnPropertyKeys().toArray(new PropertyKey[0]);
    }

    @Override
    public void set(PropertyKey key, JSValue value, JSContext context) {
        if (isCanonicalNumericIndex(key)) {
            // TypedArray [[Set]] with SameValue(O, Receiver) = true
            // Always call integerIndexedElementSet which performs value conversion
            // (ToNumber/ToBigInt) BEFORE checking detached/bounds per spec.
            String str = key.toPropertyString();
            if ("-0".equals(str)) {
                // -0 is never a valid integer index; use -1 to skip write after conversion
                integerIndexedElementSet(-1, value, context);
                return;
            }
            try {
                double numericIndex = Double.parseDouble(str);
                if (!Double.isFinite(numericIndex) || numericIndex != Math.floor(numericIndex) || numericIndex < 0) {
                    integerIndexedElementSet(-1, value, context);
                    return;
                }
                int index = (int) numericIndex;
                if (index != numericIndex) {
                    integerIndexedElementSet(-1, value, context);
                    return;
                }
                integerIndexedElementSet(index, value, context);
            } catch (NumberFormatException e) {
                integerIndexedElementSet(-1, value, context);
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
                JSValue value = srcArray.get(context, PropertyKey.fromIndex(i));
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
            JSValue lengthValue = srcObject.get(context, PropertyKey.LENGTH);
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
                JSValue value = srcObject.get(context, PropertyKey.fromIndex(i));
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
     * TypedArray [[Set]](P, V, Receiver) - ES spec 10.4.5.5
     * Handles the case where Receiver may be any ECMAScript value (not just an object).
     * Following QuickJS JS_SetPropertyInternal typed_array_oob handling.
     */
    @Override
    public boolean setWithResult(PropertyKey key, JSValue value, JSContext context, JSValue receiver) {
        if (isCanonicalNumericIndex(key)) {
            // Step b.i: If SameValue(O, Receiver) is true
            if (receiver == this) {
                // Perform TypedArraySetElement(O, numericIndex, V)
                set(key, value, context);
                return !context.hasPendingException();
            }
            // Step b.ii: If IsValidIntegerIndex(O, numericIndex) is false, return true
            String str = key.toPropertyString();
            if ("-0".equals(str)) {
                return true;
            }
            try {
                double numericIndex = Double.parseDouble(str);
                if (!Double.isFinite(numericIndex) || numericIndex != Math.floor(numericIndex) || numericIndex < 0) {
                    return true;
                }
                int index = (int) numericIndex;
                if (index != numericIndex || buffer.isDetached() || index >= getLength()) {
                    return true;
                }
            } catch (NumberFormatException e) {
                return true;
            }
            // Valid integer index with different receiver - fall through to OrdinarySet
        }
        return super.setWithResult(key, value, context, receiver);
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
