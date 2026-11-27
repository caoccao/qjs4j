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

import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

/** Shared raw-word writes for BigInt64Array and BigUint64Array. */
abstract sealed class JSBigIntTypedArray extends JSTypedArray permits JSBigInt64Array, JSBigUint64Array {
    JSBigIntTypedArray(JSContext context, IJSArrayBuffer buffer, long byteOffset, long length) {
        super(context, buffer, byteOffset, length, Long.BYTES);
    }

    JSBigIntTypedArray(JSContext context, long length) {
        super(context, length, Long.BYTES);
    }

    @Override
    protected void integerIndexedElementSet(long index, JSValue value) {
        long longVal;
        try {
            longVal = JSTypeConversions.toBigInt64(context, value);
        } catch (JSTypeErrorException e) {
            context.throwTypeError(e.getMessage());
            return;
        } catch (JSSyntaxErrorException e) {
            context.throwSyntaxError(e.getMessage(), e.getSourceLocation());
            return;
        }
        if (!buffer.isDetached() && index >= 0 && index < getLength()) {
            JSByteBuffer buf = getByteBuffer();
            buf.putLong(index * Long.BYTES, longVal);
        }
    }

    @Override
    protected void setJSElement(long index, JSValue value) {
        long longVal = JSTypeConversions.toBigInt64(context, value);
        checkIndex(index);
        JSByteBuffer buf = getByteBuffer();
        // Write raw signed 64-bit value directly to avoid precision loss via double.
        buf.putLong(index * Long.BYTES, longVal);
    }
}
