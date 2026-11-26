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

import java.math.BigInteger;

/**
 * Represents a JavaScript BigInt64Array. 64-bit signed integer array.
 */
public final class JSBigInt64Array extends JSBigIntTypedArray {
    public static final int BYTES_PER_ELEMENT = 8;
    public static final String NAME = "BigInt64Array";

    /**
     * Create a BigInt64Array view on an existing buffer.
     */
    public JSBigInt64Array(JSContext context, IJSArrayBuffer buffer, long byteOffset, long length) {
        super(context, buffer, byteOffset, length);
    }

    /**
     * Create a BigInt64Array with a new buffer.
     */
    public JSBigInt64Array(JSContext context, long length) {
        super(context, length);
    }

    @Override
    TypedArrayKind elementKind() {
        return TypedArrayKind.BIGINT64;
    }

    @Override
    public double getElement(long index) {
        checkIndex(index);
        JSByteBuffer buf = getByteBuffer();
        return buf.getLong(index * BYTES_PER_ELEMENT);
    }

    @Override
    public JSValue getJSElement(long index) {
        checkIndex(index);
        JSByteBuffer buf = getByteBuffer();
        return new JSBigInt(BigInteger.valueOf(buf.getLong(index * BYTES_PER_ELEMENT)));
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return TypedArrayKind.BIGINT64.create(context, args);
    }
}
