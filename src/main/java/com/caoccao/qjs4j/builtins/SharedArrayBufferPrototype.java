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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSSharedArrayBuffer;
import com.caoccao.qjs4j.core.JSValue;

/**
 * SharedArrayBuffer.prototype methods implementation.
 * Based on ES2017 SharedArrayBuffer specification.
 */
public final class SharedArrayBufferPrototype {

    /**
     * get SharedArrayBuffer.prototype.byteLength
     * ES2017 24.2.4.1
     * Returns the byte length of the SharedArrayBuffer.
     */
    public static JSValue getByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSharedArrayBuffer buffer)) {
            return context.throwTypeError("SharedArrayBuffer.prototype.byteLength called on non-SharedArrayBuffer");
        }

        return new JSNumber(buffer.getByteLength());
    }

    /**
     * SharedArrayBuffer.prototype.slice(begin, end)
     * ES2017 24.2.4.3
     * Returns a new SharedArrayBuffer with a copy of bytes from begin to end.
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSharedArrayBuffer buffer)) {
            return context.throwTypeError("SharedArrayBuffer.prototype.slice called on non-SharedArrayBuffer");
        }

        int byteLength = buffer.getByteLength();

        // Get begin parameter
        int begin = 0;
        if (args.length > 0 && args[0] instanceof JSNumber num) {
            begin = (int) num.value();
        }

        // Get end parameter (default to byteLength)
        int end = byteLength;
        if (args.length > 1 && args[1] instanceof JSNumber num) {
            end = (int) num.value();
        }

        // Perform the slice
        return buffer.slice(begin, end);
    }
}
