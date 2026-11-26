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

/**
 * Represents a JavaScript BigUint64Array. 64-bit unsigned integer array.
 */
public final class JSBigUint64Array extends JSBigIntTypedArray {
    public static final int BYTES_PER_ELEMENT = 8;
    public static final String NAME = "BigUint64Array";

    /**
     * Create a BigUint64Array view on an existing buffer.
     */
    public JSBigUint64Array(JSContext context, IJSArrayBuffer buffer, long byteOffset, long length) {
        super(context, buffer, byteOffset, length);
    }

    /**
     * Create a BigUint64Array with a new buffer.
     */
    public JSBigUint64Array(JSContext context, long length) {
        super(context, length);
    }

    @Override
    TypedArrayKind elementKind() {
        return TypedArrayKind.BIGUINT64;
    }

    @Override
    double readElement(JSByteBuffer buf, long index) {
        long value = buf.getLong(index * BYTES_PER_ELEMENT);
        // Convert unsigned long to double (may lose precision for very large values)
        return Long.compareUnsigned(value, 0) < 0
                ? (double) (value & Long.MAX_VALUE) + Math.pow(2, 63)
                : (double) value;
    }

    @Override
    JSValue readJSElement(JSByteBuffer buf, long index) {
        return JSBigInt.fromUnsignedLong(buf.getLong(index * BYTES_PER_ELEMENT));
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return TypedArrayKind.BIGUINT64.create(context, args);
    }
}
