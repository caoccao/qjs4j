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
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

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

        return JSNumber.of(buffer.getByteLength());
    }

    /**
     * get SharedArrayBuffer.prototype.growable
     * Returns whether this SharedArrayBuffer is growable.
     */
    public static JSValue getGrowable(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSharedArrayBuffer buffer)) {
            return context.throwTypeError("SharedArrayBuffer.prototype.growable called on non-SharedArrayBuffer");
        }

        return JSBoolean.valueOf(buffer.isGrowable());
    }

    /**
     * get SharedArrayBuffer.prototype.maxByteLength
     * Returns the maximum byte length.
     */
    public static JSValue getMaxByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSharedArrayBuffer buffer)) {
            return context.throwTypeError("SharedArrayBuffer.prototype.maxByteLength called on non-SharedArrayBuffer");
        }

        return JSNumber.of(buffer.getMaxByteLength());
    }

    /**
     * get SharedArrayBuffer.prototype[@@toStringTag]
     * ES2017 24.2.4.2
     * Returns "SharedArrayBuffer".
     */
    public static JSValue getToStringTag(JSContext context, JSValue thisArg, JSValue[] args) {
        return new JSString(JSSharedArrayBuffer.NAME);
    }

    /**
     * SharedArrayBuffer.prototype.grow(newByteLength)
     * Grows a growable SharedArrayBuffer to the specified new length.
     */
    public static JSValue grow(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSharedArrayBuffer buffer)) {
            return context.throwTypeError("SharedArrayBuffer.prototype.grow called on non-SharedArrayBuffer");
        }

        int newByteLength;
        try {
            JSValue lengthArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            long length = JSTypeConversions.toIndex(context, lengthArg);
            if (length > Integer.MAX_VALUE) {
                return context.throwRangeError("Invalid array buffer length");
            }
            newByteLength = (int) length;
        } catch (IllegalArgumentException | JSRangeErrorException e) {
            return context.throwRangeError("Invalid array buffer length");
        }

        try {
            buffer.grow(newByteLength);
            return JSUndefined.INSTANCE;
        } catch (IllegalStateException e) {
            return context.throwTypeError(e.getMessage());
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
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
        if (args.length > 0) {
            begin = JSTypeConversions.toInt32(context, args[0]);
        }

        // Get end parameter (default to byteLength)
        int end = byteLength;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            end = JSTypeConversions.toInt32(context, args[1]);
        }

        // Perform the slice
        return buffer.slice(begin, end);
    }
}
