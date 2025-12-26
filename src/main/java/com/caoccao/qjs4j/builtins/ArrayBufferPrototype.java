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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;

/**
 * Implementation of ArrayBuffer.prototype methods.
 * Based on ES2020 ArrayBuffer specification.
 */
public final class ArrayBufferPrototype {

    /**
     * get ArrayBuffer.prototype.byteLength
     * ES2020 24.1.4.1
     * Returns the byte length of the buffer.
     */
    public static JSValue getByteLength(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return ctx.throwError("TypeError", "get ArrayBuffer.prototype.byteLength called on non-ArrayBuffer");
        }

        return new JSNumber(buffer.getByteLength());
    }

    /**
     * ArrayBuffer.prototype.slice(begin, end)
     * ES2020 24.1.4.3
     * Returns a new ArrayBuffer with a copy of bytes.
     */
    public static JSValue slice(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return ctx.throwError("TypeError", "ArrayBuffer.prototype.slice called on non-ArrayBuffer");
        }

        int byteLength = buffer.getByteLength();

        // Get begin index
        int begin = 0;
        if (args.length > 0) {
            begin = JSTypeConversions.toInt32(args[0]);
        }

        // Get end index
        int end = byteLength;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            end = JSTypeConversions.toInt32(args[1]);
        }

        try {
            return buffer.slice(begin, end);
        } catch (IllegalStateException e) {
            return ctx.throwError("TypeError", e.getMessage());
        }
    }
}
