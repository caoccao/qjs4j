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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Represents a JavaScript Uint32Array. 32-bit unsigned integer array.
 */
public final class JSUint32Array extends JSTypedArray {
    public static final int BYTES_PER_ELEMENT = 4;
    public static final String NAME = "Uint32Array";

    /**
     * Create a Uint32Array view on an existing buffer.
     */
    public JSUint32Array(JSContext context, IJSArrayBuffer buffer, long byteOffset, long length) {
        super(context, buffer, byteOffset, length, TypedArrayKind.UINT32);
    }

    /**
     * Create a Uint32Array with a new buffer.
     */
    public JSUint32Array(JSContext context, long length) {
        super(context, length, TypedArrayKind.UINT32);
    }

    @Override
    double readElement(JSByteBuffer buf, long index) {
        return buf.getInt(index * BYTES_PER_ELEMENT) & 0xFFFFFFFFL; // Convert to unsigned
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return TypedArrayKind.UINT32.create(context, args);
    }
}
