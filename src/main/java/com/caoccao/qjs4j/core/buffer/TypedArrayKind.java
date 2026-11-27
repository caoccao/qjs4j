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

package com.caoccao.qjs4j.core.buffer;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.iterator.JSIterator;
import com.caoccao.qjs4j.core.iterator.JSIteratorHelper;

import java.util.List;
import com.caoccao.qjs4j.utils.Float16;

/** Intrinsic metadata for the concrete typed arrays. */
public enum TypedArrayKind {
    /** Signed 64-bit BigInt elements. */
    BIGINT64(JSBigInt64Array.NAME, JSBigInt64Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_BIGINT64,
            JSContext::createJSBigInt64Array, JSContext::createJSBigInt64Array, JSBigInt64Array::new, true, true),
    /** Unsigned 64-bit BigInt elements. */
    BIGUINT64(JSBigUint64Array.NAME, JSBigUint64Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_BIGUINT64,
            JSContext::createJSBigUint64Array, JSContext::createJSBigUint64Array, JSBigUint64Array::new, true, false),
    /** Half-precision floating-point elements. */
    FLOAT16(JSFloat16Array.NAME, JSFloat16Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_FLOAT16,
            JSContext::createJSFloat16Array, JSContext::createJSFloat16Array, JSFloat16Array::new, false, false),
    /** Single-precision floating-point elements. */
    FLOAT32(JSFloat32Array.NAME, JSFloat32Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_FLOAT32,
            JSContext::createJSFloat32Array, JSContext::createJSFloat32Array, JSFloat32Array::new, false, false),
    /** Double-precision floating-point elements. */
    FLOAT64(JSFloat64Array.NAME, JSFloat64Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_FLOAT64,
            JSContext::createJSFloat64Array, JSContext::createJSFloat64Array, JSFloat64Array::new, false, false),
    /** Signed 16-bit integers. */
    INT16(JSInt16Array.NAME, JSInt16Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_INT16,
            JSContext::createJSInt16Array, JSContext::createJSInt16Array, JSInt16Array::new, true, false),
    /** Signed 32-bit integers. */
    INT32(JSInt32Array.NAME, JSInt32Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_INT32,
            JSContext::createJSInt32Array, JSContext::createJSInt32Array, JSInt32Array::new, true, true),
    /** Signed 8-bit integers. */
    INT8(JSInt8Array.NAME, JSInt8Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_INT8,
            JSContext::createJSInt8Array, JSContext::createJSInt8Array, JSInt8Array::new, true, false),
    /** Unsigned 16-bit integers. */
    UINT16(JSUint16Array.NAME, JSUint16Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_UINT16,
            JSContext::createJSUint16Array, JSContext::createJSUint16Array, JSUint16Array::new, true, false),
    /** Unsigned 32-bit integers. */
    UINT32(JSUint32Array.NAME, JSUint32Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_UINT32,
            JSContext::createJSUint32Array, JSContext::createJSUint32Array, JSUint32Array::new, true, false),
    /** Unsigned 8-bit integers. */
    UINT8(JSUint8Array.NAME, JSUint8Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_UINT8,
            JSContext::createJSUint8Array, JSContext::createJSUint8Array, JSUint8Array::new, true, false),
    /** Unsigned 8-bit elements with clamping on writes. */
    UINT8_CLAMPED(JSUint8ClampedArray.NAME, JSUint8ClampedArray.BYTES_PER_ELEMENT,
            JSConstructorType.TYPED_ARRAY_UINT8_CLAMPED, JSContext::createJSUint8ClampedArray,
            JSContext::createJSUint8ClampedArray, JSUint8ClampedArray::new, false, false);

    /** Global property insertion order is observable through Reflect.ownKeys(globalThis). */
    public static final List<TypedArrayKind> INSTALLATION_ORDER = List.of(INT8, UINT8, UINT8_CLAMPED, INT16, UINT16,
            INT32, UINT32, FLOAT16, FLOAT32, FLOAT64, BIGINT64, BIGUINT64);
    private final LengthFactory arrays;
    final boolean atomic;
    private final ViewFactory bufferViews;
    public final int bytesPerElement;
    public final JSNativeCallback callback;
    public final String name;
    final String objectTag;
    final ViewFactory rawViews;
    public final JSConstructorType type;
    final boolean waitable;

    TypedArrayKind(String name, int bytesPerElement, JSConstructorType type, LengthFactory arrays,
            ViewFactory bufferViews, ViewFactory rawViews, boolean atomic, boolean waitable) {
        this.name = name;
        this.objectTag = "[object " + name + "]";
        this.bytesPerElement = bytesPerElement;
        this.type = type;
        this.arrays = arrays;
        this.bufferViews = bufferViews;
        this.rawViews = rawViews;
        this.callback = (context, thisArg, args) -> create(context, args);
        this.atomic = atomic;
        this.waitable = waitable;
    }

    JSTypedArray allocate(JSContext context, long length) {
        return arrays.create(context, length);
    }

    JSTypedArray create(JSContext context, JSValue[] args) {
        if (args.length == 0) {
            return allocate(context, 0);
        }
        JSValue source = JSTypedArray.normalizeConstructorSource(context, args[0]);
        if (context.hasPendingException()) {
            return null;
        }
        if (source instanceof IJSArrayBuffer buffer) {
            long offset = args.length < 2
                    ? 0
                    : JSTypedArray.resolveAndValidateByteOffset(context, args[1], bytesPerElement);
            if (context.hasPendingException()) {
                return null;
            }
            long count = args.length < 3 || args[2] instanceof JSUndefined
                    ? -1
                    : JSTypedArray.toTypedArrayIndex(context, args[2], bytesPerElement);
            return context.hasPendingException() ? null : view(context, buffer, offset, count);
        }
        if (source instanceof JSNumber) {
            return allocate(context, JSTypedArray.toTypedArrayIndex(context, source, bytesPerElement));
        }
        if (source instanceof JSIterator iterator) {
            source = JSIteratorHelper.toArray(context, iterator);
            if (context.hasPendingException()) {
                return null;
            }
        }
        long count;
        if (source instanceof JSTypedArray array) {
            if (array.isOutOfBounds()) {
                return context.rejectTypeError("source TypedArray is out of bounds");
            }
            count = array.getLength();
        } else if (source instanceof JSArray array) {
            count = JSTypedArray.toTypedArrayLength(array.getLength(), bytesPerElement);
        } else if (source instanceof JSObject object) {
            count = JSTypedArray.toTypedArrayLength(context, object.get(PropertyKey.LENGTH), bytesPerElement);
        } else {
            return allocate(context, JSTypedArray.toTypedArrayLength(context, source, bytesPerElement));
        }
        JSTypedArray result = allocate(context, count);
        result.setArray(source, 0);
        return result;
    }

    JSTypedArray view(JSContext context, IJSArrayBuffer buffer, long byteOffset, long length) {
        return bufferViews.create(context, buffer, byteOffset, length);
    }

    /** Store the low bits for integer elements, or the rounded value for floating-point elements. */
    void writeNumber(JSByteBuffer buffer, long index, double value) {
        long offset = index * bytesPerElement;
        switch (this) {
            case INT8, UINT8 -> buffer.put(offset, (byte) JSTypeConversions.toInt32(value));
            case INT16, UINT16 -> buffer.putShort(offset, (short) JSTypeConversions.toInt32(value));
            case INT32, UINT32 -> buffer.putInt(offset, JSTypeConversions.toInt32(value));
            case UINT8_CLAMPED -> buffer.put(offset, (byte) JSTypeConversions.toUint8Clamp(value));
            case FLOAT16 -> buffer.putShort(offset, Float16.toHalf(value));
            case FLOAT32 -> buffer.putFloat(offset, (float) value);
            case FLOAT64 -> buffer.putDouble(offset, value);
            case BIGINT64, BIGUINT64 -> buffer.putLong(offset, (long) value);
        }
    }

    @FunctionalInterface
    private interface LengthFactory {
        JSTypedArray create(JSContext context, long length);
    }

    @FunctionalInterface
    interface ViewFactory {
        JSTypedArray create(JSContext context, IJSArrayBuffer buffer, long byteOffset, long length);
    }
}
