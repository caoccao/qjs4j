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

import com.caoccao.qjs4j.builtins.*;

import java.util.List;

/** Intrinsic metadata for the concrete typed arrays. */
enum TypedArrayKind {
    /** Signed 64-bit BigInt elements. */
    BIGINT64(JSBigInt64Array.NAME, JSBigInt64Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_BIGINT64,
            BigInt64ArrayConstructor::call, true, true),
    /** Unsigned 64-bit BigInt elements. */
    BIGUINT64(JSBigUint64Array.NAME, JSBigUint64Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_BIGUINT64,
            BigUint64ArrayConstructor::call, true, false),
    /** Half-precision floating-point elements. */
    FLOAT16(JSFloat16Array.NAME, JSFloat16Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_FLOAT16,
            Float16ArrayConstructor::call, false, false),
    /** Single-precision floating-point elements. */
    FLOAT32(JSFloat32Array.NAME, JSFloat32Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_FLOAT32,
            Float32ArrayConstructor::call, false, false),
    /** Double-precision floating-point elements. */
    FLOAT64(JSFloat64Array.NAME, JSFloat64Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_FLOAT64,
            Float64ArrayConstructor::call, false, false),
    /** Signed 16-bit integers. */
    INT16(JSInt16Array.NAME, JSInt16Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_INT16,
            Int16ArrayConstructor::call, true, false),
    /** Signed 32-bit integers. */
    INT32(JSInt32Array.NAME, JSInt32Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_INT32,
            Int32ArrayConstructor::call, true, true),
    /** Signed 8-bit integers. */
    INT8(JSInt8Array.NAME, JSInt8Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_INT8,
            Int8ArrayConstructor::call, true, false),
    /** Unsigned 16-bit integers. */
    UINT16(JSUint16Array.NAME, JSUint16Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_UINT16,
            Uint16ArrayConstructor::call, true, false),
    /** Unsigned 32-bit integers. */
    UINT32(JSUint32Array.NAME, JSUint32Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_UINT32,
            Uint32ArrayConstructor::call, true, false),
    /** Unsigned 8-bit integers. */
    UINT8(JSUint8Array.NAME, JSUint8Array.BYTES_PER_ELEMENT, JSConstructorType.TYPED_ARRAY_UINT8,
            Uint8ArrayConstructor::call, true, false),
    /** Unsigned 8-bit elements with clamping on writes. */
    UINT8_CLAMPED(JSUint8ClampedArray.NAME, JSUint8ClampedArray.BYTES_PER_ELEMENT,
            JSConstructorType.TYPED_ARRAY_UINT8_CLAMPED, Uint8ClampedArrayConstructor::call, false, false);

    /** Global property insertion order is observable through Reflect.ownKeys(globalThis). */
    static final List<TypedArrayKind> INSTALLATION_ORDER = List.of(INT8, UINT8, UINT8_CLAMPED, INT16, UINT16, INT32,
            UINT32, FLOAT16, FLOAT32, FLOAT64, BIGINT64, BIGUINT64);
    final boolean atomic;
    final int bytesPerElement;
    final JSNativeCallback callback;
    final String name;
    final String objectTag;
    final JSConstructorType type;
    final boolean waitable;

    TypedArrayKind(String name, int bytesPerElement, JSConstructorType type, JSNativeCallback callback, boolean atomic,
            boolean waitable) {
        this.name = name;
        this.objectTag = "[object " + name + "]";
        this.bytesPerElement = bytesPerElement;
        this.type = type;
        this.callback = callback;
        this.atomic = atomic;
        this.waitable = waitable;
    }
}
