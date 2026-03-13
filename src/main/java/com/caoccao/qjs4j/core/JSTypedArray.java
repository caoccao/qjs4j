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
public sealed abstract class JSTypedArray extends JSObject permits
        JSUint8Array, JSUint8ClampedArray, JSUint16Array, JSUint32Array,
        JSInt8Array, JSInt16Array, JSInt32Array,
        JSFloat16Array, JSFloat32Array, JSFloat64Array,
        JSBigInt64Array, JSBigUint64Array {
    public static final String NAME = "TypedArray";
    private static final int CANONICAL_NUMERIC_INDEX_INVALID = -1;
    private static final int CANONICAL_NUMERIC_INDEX_NOT_CANONICAL = Integer.MIN_VALUE;
    protected final IJSArrayBuffer buffer;
    protected final int byteLength;
    protected final int byteOffset;
    protected final int bytesPerElement;
    protected final int length;
    protected final boolean trackRab;

    /**
     * Create a TypedArray with a new buffer of the given length.
     */
    protected JSTypedArray(JSContext context, int length, int bytesPerElement) {
        super(context);
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
        this.buffer = new JSArrayBuffer(context, this.byteLength);
        this.trackRab = false;
    }

    /**
     * Create a TypedArray view on an existing ArrayBuffer or SharedArrayBuffer.
     */
    protected JSTypedArray(JSContext context, IJSArrayBuffer buffer, int byteOffset, int length, int bytesPerElement) {
        super(context);
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
            boolean resizable = (buffer instanceof JSArrayBuffer jsArrayBuffer && jsArrayBuffer.isResizable())
                    || (buffer instanceof JSSharedArrayBuffer jsSharedArrayBuffer && jsSharedArrayBuffer.isGrowable());
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

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isValidDoubleString(String str) {
        if ("NaN".equals(str) || "Infinity".equals(str) || "-Infinity".equals(str)) {
            return true;
        }

        int index = 0;
        int length = str.length();
        if (str.charAt(index) == '-') {
            index++;
            if (index >= length) {
                return false;
            }
        }

        boolean hasIntegerDigits = false;
        while (index < length && isAsciiDigit(str.charAt(index))) {
            hasIntegerDigits = true;
            index++;
        }

        boolean hasFractionDigits = false;
        if (index < length && str.charAt(index) == '.') {
            index++;
            while (index < length && isAsciiDigit(str.charAt(index))) {
                hasFractionDigits = true;
                index++;
            }
        }

        if (!hasIntegerDigits && !hasFractionDigits) {
            return false;
        }

        if (index < length && (str.charAt(index) == 'e' || str.charAt(index) == 'E')) {
            index++;
            if (index < length && (str.charAt(index) == '+' || str.charAt(index) == '-')) {
                index++;
            }
            int exponentStart = index;
            while (index < length && isAsciiDigit(str.charAt(index))) {
                index++;
            }
            if (exponentStart == index) {
                return false;
            }
        }

        return index == length;
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

        JSValue iteratorMethod = sourceObject.get(PropertyKey.SYMBOL_ITERATOR);
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

    /**
     * ES2024 InitializeTypedArrayFromArrayBuffer steps 2-3:
     * Resolve byteOffset via ToIndex, then check alignment before ToIndex(length).
     * Returns the validated byteOffset, or -1 if a pending exception was set.
     */
    protected static int resolveAndValidateByteOffset(JSContext context, JSValue value, int bytesPerElement) {
        int byteOffset = toTypedArrayByteOffset(context, value);
        if (context.hasPendingException()) {
            return -1;
        }
        if (bytesPerElement > 1 && byteOffset % bytesPerElement != 0) {
            context.throwRangeError("start offset of TypedArray should be a multiple of " + bytesPerElement);
            return -1;
        }
        return byteOffset;
    }

    /**
     * Resolve a key for integer-indexed exotic semantics.
     *
     * @return non-negative integer index when canonical and valid;
     * {@link #CANONICAL_NUMERIC_INDEX_INVALID} when canonical numeric but invalid integer index;
     * {@link #CANONICAL_NUMERIC_INDEX_NOT_CANONICAL} otherwise.
     */
    private static int resolveCanonicalNumericIndex(PropertyKey key) {
        int typedArrayIndex = toTypedArrayIndex(key);
        if (typedArrayIndex >= 0) {
            return typedArrayIndex;
        }
        if (key.isSymbol()) {
            return CANONICAL_NUMERIC_INDEX_NOT_CANONICAL;
        }
        String keyString = key.toPropertyString();
        if (keyString.isEmpty()) {
            return CANONICAL_NUMERIC_INDEX_NOT_CANONICAL;
        }
        if ("-0".equals(keyString)) {
            return CANONICAL_NUMERIC_INDEX_INVALID;
        }
        char firstCharacter = keyString.charAt(0);
        if (!((firstCharacter >= '0' && firstCharacter <= '9')
                || firstCharacter == '-'
                || firstCharacter == 'I'
                || firstCharacter == 'N')) {
            return CANONICAL_NUMERIC_INDEX_NOT_CANONICAL;
        }
        if (!isValidDoubleString(keyString)) {
            return CANONICAL_NUMERIC_INDEX_NOT_CANONICAL;
        }
        double numericIndex = Double.parseDouble(keyString);
        if (!numberToString(numericIndex).equals(keyString)) {
            return CANONICAL_NUMERIC_INDEX_NOT_CANONICAL;
        }
        if (!Double.isFinite(numericIndex) || numericIndex != Math.floor(numericIndex) || numericIndex < 0) {
            return CANONICAL_NUMERIC_INDEX_INVALID;
        }
        int integerIndex = (int) numericIndex;
        if (integerIndex != numericIndex) {
            return CANONICAL_NUMERIC_INDEX_INVALID;
        }
        return integerIndex;
    }

    protected static int toArrayLikeLength(JSContext context, JSValue value) {
        long length = JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, value));
        if (length > Integer.MAX_VALUE) {
            throw new JSRangeErrorException("invalid array length");
        }
        return (int) length;
    }

    protected static int toTypedArrayBufferLength(JSContext context, JSValue value, int bytesPerElement) {
        return toTypedArrayLength(JSTypeConversions.toIndex(context, value), bytesPerElement);
    }

    protected static int toTypedArrayByteOffset(JSContext context, JSValue value) {
        long offset = JSTypeConversions.toIndex(context, value);
        if (offset > Integer.MAX_VALUE) {
            throw new JSRangeErrorException("invalid offset");
        }
        return (int) offset;
    }

    protected static int toTypedArrayIndex(JSContext context, JSValue value, int bytesPerElement) {
        return toTypedArrayLength(JSTypeConversions.toIndex(context, value), bytesPerElement);
    }

    /**
     * Try to interpret a PropertyKey as a typed array integer index.
     * Returns the index if it's a valid canonical numeric index string, or -1 otherwise.
     */
    private static int toTypedArrayIndex(PropertyKey key) {
        int fastIndex = key.toIndex();
        if (fastIndex >= 0) {
            return fastIndex;
        }
        if (!key.isString()) {
            return -1;
        }
        String keyString = key.asString();
        if (keyString == null || keyString.isEmpty()) {
            return -1;
        }
        // Fast path: single digit
        char firstCharacter = keyString.charAt(0);
        if (keyString.length() == 1 && firstCharacter >= '0' && firstCharacter <= '9') {
            return firstCharacter - '0';
        }
        // Must start with a digit (not '-' or '+')
        if (firstCharacter < '0' || firstCharacter > '9') {
            return -1;
        }
        // No leading zeros (except "0" itself, handled above)
        if (firstCharacter == '0') {
            return -1;
        }
        long parsedValue = firstCharacter - '0';
        for (int characterIndex = 1; characterIndex < keyString.length(); characterIndex++) {
            char character = keyString.charAt(characterIndex);
            if (character < '0' || character > '9') {
                return -1;
            }
            int digit = character - '0';
            if (parsedValue > (Integer.MAX_VALUE - digit) / 10L) {
                return -1;
            }
            parsedValue = parsedValue * 10 + digit;
        }
        return (int) parsedValue;
    }

    protected static int toTypedArrayLength(JSContext context, JSValue value, int bytesPerElement) {
        return toTypedArrayLength(
                JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, value)),
                bytesPerElement);
    }

    protected static int toTypedArrayLength(long length, int bytesPerElement) {
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
     * For non-numeric keys: delegates to ordinary defineProperty.
     */
    @Override
    public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        int canonicalNumericIndex = resolveCanonicalNumericIndex(key);
        if (canonicalNumericIndex == CANONICAL_NUMERIC_INDEX_NOT_CANONICAL) {
            return super.defineProperty(key, descriptor);
        }
        if (canonicalNumericIndex < 0) {
            return false;
        }
        int index = canonicalNumericIndex;
        // Check bounds and detachment
        if (buffer.isDetached() || index >= getLength()) {
            return false;
        }
        // IntegerIndexedObjectDefineOwnProperty per ES2024 10.4.5.3
        // Accessor descriptors are not allowed
        if (descriptor.isAccessorDescriptor()) {
            return false;
        }
        // Step 2a: If Desc has [[Configurable]] and it's false, return false
        // (TypedArray elements have configurable: true per ES2024)
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
            integerIndexedElementSet(index, descriptor.getValue());
        }
        return true;
    }

    /**
     * Integer-Indexed exotic object [[Delete]].
     * Returns false for valid in-bounds indices (elements are non-configurable).
     * Following QuickJS delete_property() for typed arrays.
     */
    @Override
    public boolean delete(PropertyKey key) {
        int canonicalNumericIndex = resolveCanonicalNumericIndex(key);
        if (canonicalNumericIndex == CANONICAL_NUMERIC_INDEX_NOT_CANONICAL) {
            return super.delete(key);
        }
        if (canonicalNumericIndex < 0) {
            return true;
        }
        int index = canonicalNumericIndex;
        if (index >= 0) {
            // In-bounds element: not deletable
            if (index < getLength() && !buffer.isDetached()) {
                if (context.isStrictMode()) {
                    context.throwTypeError("Cannot delete property '" + index + "'");
                }
                return false;
            }
            // Out-of-bounds or detached: canonical numeric index returns true
            return true;
        }
        return true;
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
        ByteBuffer backingBuffer = buffer.getBuffer();
        ByteBuffer buf = backingBuffer.duplicate();
        buf.position(byteOffset);
        buf.limit(byteOffset + getByteLength());
        ByteBuffer slice = buf.slice();
        slice.order(backingBuffer.order());
        return slice;
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
            // Round down to element boundary for partial elements
            int remainingBytes = currentByteLength - byteOffset;
            return (remainingBytes / bytesPerElement) * bytesPerElement;
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

    public abstract String getObjectTag();

    /**
     * Integer-Indexed exotic object [[GetOwnProperty]].
     * Following QuickJS JS_GetOwnPropertyInternal for fast arrays (lines 8452-8467).
     * For valid integer indices: returns {value, writable: true, enumerable: true, configurable: true}.
     * For canonical numeric indices that are not valid integer indices: returns null.
     * For non-numeric keys: delegates to ordinary getOwnPropertyDescriptor.
     */
    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        int canonicalNumericIndex = resolveCanonicalNumericIndex(key);
        if (canonicalNumericIndex == CANONICAL_NUMERIC_INDEX_NOT_CANONICAL) {
            return super.getOwnPropertyDescriptor(key);
        }
        if (canonicalNumericIndex < 0) {
            return null;
        }
        int index = canonicalNumericIndex;
        if (buffer.isDetached() || index >= getLength()) {
            return null;
        }
        return PropertyDescriptor.dataDescriptor(getJSElement(index), PropertyDescriptor.DataState.All);
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

    /**
     * Get the TypedArrayName (e.g., "Int8Array", "Float64Array").
     * Per ES spec, this is returned by the @@toStringTag getter.
     */
    public String getTypedArrayName() {
        return NAME;
    }

    /**
     * Integer-Indexed exotic object [[Get]].
     * For canonical numeric index strings, return the element or undefined
     * without walking the prototype chain.
     */
    @Override
    protected JSValue getWithReceiver(PropertyKey key, JSValue receiver, int depth) {
        int canonicalNumericIndex = resolveCanonicalNumericIndex(key);
        if (canonicalNumericIndex == CANONICAL_NUMERIC_INDEX_NOT_CANONICAL) {
            return super.getWithReceiver(key, receiver, depth);
        }
        if (canonicalNumericIndex < 0) {
            return JSUndefined.INSTANCE;
        }
        int index = canonicalNumericIndex;
        if (buffer.isDetached() || isOutOfBounds() || index >= getLength()) {
            return JSUndefined.INSTANCE;
        }
        return getJSElement(index);
    }

    @Override
    public boolean has(PropertyKey key) {
        int canonicalNumericIndex = resolveCanonicalNumericIndex(key);
        if (canonicalNumericIndex == CANONICAL_NUMERIC_INDEX_NOT_CANONICAL) {
            return super.has(key);
        }
        if (canonicalNumericIndex < 0) {
            return false;
        }
        return !buffer.isDetached() && canonicalNumericIndex < getLength();
    }

    /**
     * IntegerIndexedElementSet per ES spec.
     * Converts value to the appropriate type (ToNumber or ToBigInt), then writes
     * to the buffer only if it is still valid (not detached and index in bounds).
     * This handles the case where ToNumber/ToBigInt detaches the buffer as a side effect.
     * BigInt typed arrays override to use ToBigInt instead of ToNumber.
     */
    protected void integerIndexedElementSet(int index, JSValue value) {
        double numValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return;
        }
        if (!buffer.isDetached() && index >= 0 && index < getLength()) {
            setElement(index, numValue);
        }
    }

    public abstract boolean isAtomicsReadableAndWriteable();

    /**
     * Integer-Indexed exotic object [[OwnPropertyKeys]].
     * Following QuickJS JS_GetOwnPropertyNamesInternal for fast arrays (lines 8334-8354).
     * Returns integer indices first (in ascending order), then string keys, then symbol keys.
     */

    public abstract boolean isAtomicsWriteable();

    /**
     * Returns true if this TypedArray is length-tracking (auto-length)
     * on a resizable ArrayBuffer.
     */
    public boolean isLengthTracking() {
        return trackRab;
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
    public boolean preventExtensionsWithResult() {
        if (buffer instanceof JSArrayBuffer arrayBuffer && arrayBuffer.isResizable()) {
            return false;
        }
        if (buffer instanceof JSSharedArrayBuffer sharedArrayBuffer && sharedArrayBuffer.isGrowable() && trackRab) {
            return false;
        }
        return super.preventExtensionsWithResult();
    }

    @Override
    public void set(PropertyKey key, JSValue value) {
        int canonicalNumericIndex = resolveCanonicalNumericIndex(key);
        if (canonicalNumericIndex == CANONICAL_NUMERIC_INDEX_NOT_CANONICAL) {
            super.set(key, value);
            return;
        }
        // TypedArray [[Set]] with SameValue(O, Receiver) = true
        // Always call integerIndexedElementSet which performs value conversion
        // (ToNumber/ToBigInt) BEFORE checking detached/bounds per spec.
        if (canonicalNumericIndex < 0) {
            // Invalid numeric index strings must still run conversion side effects.
            integerIndexedElementSet(CANONICAL_NUMERIC_INDEX_INVALID, value);
            return;
        }
        integerIndexedElementSet(canonicalNumericIndex, value);
    }

    /**
     * TypedArray.prototype.set(array, offset)
     * Copy values from array into this TypedArray.
     */
    public void setArray(JSValue source, int offset) {
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
                JSValue value = srcArray.get(PropertyKey.fromString(Integer.toString(i)));
                if (context.hasPendingException()) {
                    return;
                }
                setJSElement(offset + i, value);
                if (context.hasPendingException()) {
                    return;
                }
            }
        } else if (source instanceof JSTypedArray srcTyped) {
            int srcLength = srcTyped.getLength();
            if (offset + srcLength > currentLength) {
                throw new JSRangeErrorException("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                setJSElement(offset + i, srcTyped.getJSElement(i));
                if (context.hasPendingException()) {
                    return;
                }
            }
        } else if (source instanceof JSIterator jsIterator) {
            JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
            if (context.hasPendingException()) {
                return;
            }
            setArray(jsArray, offset);
        } else if (source instanceof JSObject srcObject) {
            JSValue lengthValue = srcObject.get(PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                return;
            }
            int srcLength = toArrayLikeLength(context, lengthValue);
            if (context.hasPendingException()) {
                return;
            }
            if (offset + srcLength > currentLength) {
                throw new JSRangeErrorException("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                JSValue value = srcObject.get(PropertyKey.fromString(Integer.toString(i)));
                if (context.hasPendingException()) {
                    return;
                }
                setJSElement(offset + i, value);
                if (context.hasPendingException()) {
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
    protected void setJSElement(int index, JSValue value) {
        setElement(index, JSTypeConversions.toNumber(context, value).value());
    }

    /**
     * Override 3-arg setWithResult to route through the JSValue receiver version,
     * ensuring TypedArray's canonical numeric index handling is used when called
     * by generic Array.prototype methods (e.g., copyWithin, fill).
     */
    @Override
    public boolean setWithResult(PropertyKey key, JSValue value) {
        return setWithResult(key, value, (JSValue) this);
    }

    /**
     * TypedArray [[Set]](P, V, Receiver) - ES spec 10.4.5.5
     * Handles the case where Receiver may be any ECMAScript value (not just an object).
     * Following QuickJS JS_SetPropertyInternal typed_array_oob handling.
     */
    @Override
    public boolean setWithResult(PropertyKey key, JSValue value, JSValue receiver) {
        int canonicalNumericIndex = resolveCanonicalNumericIndex(key);
        if (canonicalNumericIndex == CANONICAL_NUMERIC_INDEX_NOT_CANONICAL) {
            return super.setWithResult(key, value, receiver);
        }
        // Step b.i: If SameValue(O, Receiver) is true
        if (receiver == this) {
            // Perform TypedArraySetElement(O, numericIndex, V)
            set(key, value);
            return !context.hasPendingException();
        }
        // Step b.ii: If IsValidIntegerIndex(O, numericIndex) is false, return true
        if (canonicalNumericIndex < 0 || buffer.isDetached() || canonicalNumericIndex >= getLength()) {
            return true;
        }
        // Valid integer index with different receiver.
        // Per ES2024 10.4.5.5 step 3: Return ? OrdinarySet(O, P, V, Receiver).
        // The TypedArray element acts as an own writable data property, so OrdinarySet
        // skips prototype chain walk and goes directly to setting on the receiver.
        if (receiver instanceof JSObject receiverObj) {
            PropertyDescriptor existingDescriptor = receiverObj.getOwnPropertyDescriptor(key);
            if (context.hasPendingException()) {
                return false;
            }
            if (existingDescriptor != null) {
                if (existingDescriptor.isAccessorDescriptor()) {
                    return false;
                }
                if (!existingDescriptor.isWritable()) {
                    return false;
                }
                PropertyDescriptor valueDescriptor = new PropertyDescriptor();
                valueDescriptor.setValue(value);
                return receiverObj.defineProperty(key, valueDescriptor);
            }
            if (!receiverObj.isExtensible()) {
                return false;
            }
            return receiverObj.defineProperty(key, value, PropertyDescriptor.DataState.All);
        }
        return false;
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
