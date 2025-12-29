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
 * SharedArrayBuffer constructor implementation.
 * Based on ES2017 SharedArrayBuffer specification.
 */
public final class SharedArrayBufferConstructor {

    /**
     * SharedArrayBuffer constructor function.
     * Cannot be called without 'new'.
     *
     * @param context The execution context
     * @param thisArg The this value
     * @param args    Constructor arguments [byteLength]
     * @return TypeError (SharedArrayBuffer must be called with 'new')
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        // This should be called via VM's handleCallConstructor
        // If called directly, it's an error
        return context.throwTypeError("SharedArrayBuffer constructor must be called with 'new'");
    }

    /**
     * Create a SharedArrayBuffer instance.
     * Called by VM when 'new SharedArrayBuffer(length)' is executed.
     *
     * @param context The execution context
     * @param length  The byte length
     * @return A new SharedArrayBuffer instance or error
     */
    public static JSValue createSharedArrayBuffer(JSContext context, JSValue length) {
        // Convert length to integer
        int byteLength = 0;
        if (length instanceof JSNumber num) {
            byteLength = (int) num.value();
        } else if (length instanceof JSUndefined) {
            byteLength = 0;
        } else {
            return context.throwTypeError("SharedArrayBuffer length must be a number");
        }

        if (byteLength < 0) {
            return context.throwRangeError("SharedArrayBuffer byteLength must be non-negative");
        }

        // Create and return SharedArrayBuffer
        return new JSSharedArrayBuffer(byteLength);
    }
}
