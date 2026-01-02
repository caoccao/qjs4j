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

import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.RangeError;

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
            throw new RangeError("TypedArray byteOffset must be aligned");
        }
        if (byteOffset > buffer.getByteLength()) {
            throw new RangeError("TypedArray byteOffset out of range");
        }
        this.byteOffset = byteOffset;

        // Calculate length if not specified
        if (length < 0) {
            int remainingBytes = buffer.getByteLength() - byteOffset;
            if (remainingBytes % bytesPerElement != 0) {
                throw new RangeError("Buffer byte length must be a multiple of element size");
            }
            this.length = remainingBytes / bytesPerElement;
            this.byteLength = remainingBytes;
        } else {
            this.length = length;
            this.byteLength = length * bytesPerElement;
            if (byteOffset + this.byteLength > buffer.getByteLength()) {
                throw new RangeError("TypedArray extends beyond buffer");
            }
        }

        this.buffer = buffer;
    }

    public static JSTypedArray createTypedArray(JSContext context, ConstructorType constructorType, JSValue... args) {
        int length = 0;
        if (args.length >= 1) {
            JSValue firstArg = args[0];
            if (firstArg instanceof JSNumber lengthNum) {
                length = (int) JSTypeConversions.toLength(context, lengthNum);
            } else if (firstArg instanceof JSArrayBufferable jsArrayBufferable) {
                length = -1;
                int byteOffset = 0;
                if (args.length >= 2) {
                    byteOffset = (int) JSTypeConversions.toInteger(context, args[1]);
                }
                if (args.length >= 3) {
                    length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, args[2]));
                }
                return createTypedArray(context, constructorType, jsArrayBufferable, byteOffset, length);
            } else if (firstArg instanceof JSTypedArray jsTypedArray) {
                length = jsTypedArray.getLength();
                JSTypedArray newTypedArray = createTypedArray(context, constructorType, length);
                newTypedArray.setArray(context, jsTypedArray, 0);
                return newTypedArray;
            } else if (firstArg instanceof JSArray jsArray) {
                length = (int) jsArray.getLength();
                JSTypedArray jsTypedArray = createTypedArray(context, constructorType, length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSIterator jsIterator) {
                JSArray jsArray = JSIteratorHelper.toArray(context, jsIterator);
                length = (int) jsArray.getLength();
                JSTypedArray jsTypedArray = createTypedArray(context, constructorType, length);
                jsTypedArray.setArray(context, jsArray, 0);
                return jsTypedArray;
            } else if (firstArg instanceof JSObject jsObject) {
                length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, jsObject.get("length")));
                JSTypedArray jsTypedArray = createTypedArray(context, constructorType, length);
                for (int i = 0; i < length; i++) {
                    jsTypedArray.setElement(i, JSTypeConversions.toNumber(context, jsObject.get(i)).value());
                }
                return jsTypedArray;
            } else {
                length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, firstArg));
            }
        }
        return createTypedArray(context, constructorType, length);
    }

    public static JSTypedArray createTypedArray(
            JSContext context,
            ConstructorType constructorType,
            JSArrayBufferable jsArrayBufferable,
            int byteOffset,
            int length) {
        JSTypedArray jsTypedArray;
        switch (constructorType) {
            case TYPED_ARRAY_BIGINT64 ->
                    jsTypedArray = new JSBigInt64Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSBigInt64Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_BIGUINT64 ->
                    jsTypedArray = new JSBigUint64Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSBigUint64Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_FLOAT16 ->
                    jsTypedArray = new JSFloat16Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSFloat16Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_FLOAT32 ->
                    jsTypedArray = new JSFloat32Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSFloat32Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_FLOAT64 ->
                    jsTypedArray = new JSFloat64Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSFloat64Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_INT16 ->
                    jsTypedArray = new JSInt16Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSInt16Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_INT32 ->
                    jsTypedArray = new JSInt32Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSInt32Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_INT8 ->
                    jsTypedArray = new JSInt8Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSInt8Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_UINT16 ->
                    jsTypedArray = new JSUint16Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSUint16Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_UINT32 ->
                    jsTypedArray = new JSUint32Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSUint32Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_UINT8 ->
                    jsTypedArray = new JSUint8Array(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSUint8Array.BYTES_PER_ELEMENT);
            case TYPED_ARRAY_UINT8_CLAMPED ->
                    jsTypedArray = new JSUint8ClampedArray(jsArrayBufferable, byteOffset, length >= 0 ? length : jsArrayBufferable.getByteLength() / JSUint8ClampedArray.BYTES_PER_ELEMENT);
            default -> throw new JSException(context.throwTypeError("Unsupported TypedArray type"));
        }
        return jsTypedArray;
    }

    public static JSTypedArray createTypedArray(JSContext context, ConstructorType constructorType, int length) {
        JSTypedArray jsTypedArray;
        switch (constructorType) {
            case TYPED_ARRAY_BIGINT64 -> jsTypedArray = new JSBigInt64Array(length);
            case TYPED_ARRAY_BIGUINT64 -> jsTypedArray = new JSBigUint64Array(length);
            case TYPED_ARRAY_FLOAT16 -> jsTypedArray = new JSFloat16Array(length);
            case TYPED_ARRAY_FLOAT32 -> jsTypedArray = new JSFloat32Array(length);
            case TYPED_ARRAY_FLOAT64 -> jsTypedArray = new JSFloat64Array(length);
            case TYPED_ARRAY_INT16 -> jsTypedArray = new JSInt16Array(length);
            case TYPED_ARRAY_INT32 -> jsTypedArray = new JSInt32Array(length);
            case TYPED_ARRAY_INT8 -> jsTypedArray = new JSInt8Array(length);
            case TYPED_ARRAY_UINT16 -> jsTypedArray = new JSUint16Array(length);
            case TYPED_ARRAY_UINT32 -> jsTypedArray = new JSUint32Array(length);
            case TYPED_ARRAY_UINT8 -> jsTypedArray = new JSUint8Array(length);
            case TYPED_ARRAY_UINT8_CLAMPED -> jsTypedArray = new JSUint8ClampedArray(length);
            default -> throw new JSException(context.throwTypeError("Unsupported TypedArray type"));
        }
        return jsTypedArray;
    }

    /**
     * Check if an index is valid.
     */
    protected void checkIndex(int index) {
        if (buffer.isDetached()) {
            throw new IllegalStateException("TypedArray buffer is detached");
        }
        if (index < 0 || index >= length) {
            throw new RangeError("TypedArray index out of range: " + index);
        }
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
        ByteBuffer buf = buffer.getBuffer();
        buf.position(byteOffset);
        buf.limit(byteOffset + byteLength);
        return buf;
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
        if (offset < 0 || offset >= length) {
            throw new RangeError("TypedArray offset out of range");
        }

        if (source instanceof JSArray srcArray) {
            int srcLength = (int) srcArray.getLength();
            if (offset + srcLength > length) {
                throw new RangeError("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                JSValue value = srcArray.get(i);
                double numVal = JSTypeConversions.toNumber(context, value).value();
                setElement(offset + i, numVal);
            }
        } else if (source instanceof JSTypedArray srcTyped) {
            int srcLength = srcTyped.getLength();
            if (offset + srcLength > length) {
                throw new RangeError("Source array too large");
            }
            for (int i = 0; i < srcLength; i++) {
                setElement(offset + i, srcTyped.getElement(i));
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
        ByteBuffer byteBuffer = getByteBuffer();
        return byteBuffer.toString();
    }
}
