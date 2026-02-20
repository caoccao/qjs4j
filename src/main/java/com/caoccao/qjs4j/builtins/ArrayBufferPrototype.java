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
 * Implementation of ArrayBuffer.prototype methods.
 * Based on ES2020 ArrayBuffer specification.
 */
public final class ArrayBufferPrototype {

    /**
     * get ArrayBuffer.prototype.byteLength
     * ES2020 24.1.4.1
     * Returns the byte length of the buffer.
     */
    public static JSValue getByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("get ArrayBuffer.prototype.byteLength called on non-ArrayBuffer");
        }

        return JSNumber.of(buffer.getByteLength());
    }

    /**
     * get ArrayBuffer.prototype.detached
     * ES2024 25.1.5.1
     * Returns true if the ArrayBuffer has been detached.
     */
    public static JSValue getDetached(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("get ArrayBuffer.prototype.detached called on non-ArrayBuffer");
        }

        return JSBoolean.valueOf(buffer.isDetached());
    }

    /**
     * get ArrayBuffer.prototype.maxByteLength
     * ES2024 25.1.5.2
     * Returns the maximum byte length that the ArrayBuffer can be resized to.
     */
    public static JSValue getMaxByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("get ArrayBuffer.prototype.maxByteLength called on non-ArrayBuffer");
        }

        // ES2024: If IsDetachedBuffer(O) is true, return +0.
        if (buffer.isDetached()) {
            return JSNumber.of(0);
        }

        return JSNumber.of(buffer.getMaxByteLength());
    }

    /**
     * get ArrayBuffer.prototype.resizable
     * ES2024 25.1.5.3
     * Returns true if the ArrayBuffer can be resized.
     */
    public static JSValue getResizable(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("get ArrayBuffer.prototype.resizable called on non-ArrayBuffer");
        }

        return JSBoolean.valueOf(buffer.isResizable());
    }

    /**
     * get ArrayBuffer.prototype[@@toStringTag]
     * ES2020 24.1.4.4
     * Returns "ArrayBuffer".
     */
    public static JSValue getToStringTag(JSContext context, JSValue thisArg, JSValue[] args) {
        return new JSString(JSArrayBuffer.NAME);
    }

    /**
     * ArrayBuffer.prototype.resize(newByteLength)
     * ES2024 25.1.5.4
     * Resizes the ArrayBuffer to the specified size, in bytes.
     */
    public static JSValue resize(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("ArrayBuffer.prototype.resize called on non-ArrayBuffer");
        }

        // Step 2: RequireInternalSlot(O, [[ArrayBufferMaxByteLength]]) - check resizable
        if (!buffer.isResizable()) {
            return context.throwTypeError("Method ArrayBuffer.prototype.resize called on incompatible receiver #<ArrayBuffer>");
        }

        // Step 4: Let newByteLength be ? ToIndex(newLength) - may trigger valueOf which could detach
        long newByteLength;
        try {
            newByteLength = JSTypeConversions.toIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        } catch (JSRangeErrorException e) {
            return context.throwRangeError(e.getMessage());
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 5: If IsDetachedBuffer(O) is true, throw a TypeError
        if (buffer.isDetached()) {
            return context.throwTypeError("Cannot perform ArrayBuffer.prototype.resize on a detached ArrayBuffer");
        }

        // Step 6: If newByteLength > O.[[ArrayBufferMaxByteLength]], throw a RangeError
        if (newByteLength > buffer.getMaxByteLength()) {
            return context.throwRangeError("ArrayBuffer.prototype.resize: Invalid length parameter");
        }

        try {
            buffer.resize((int) newByteLength);
            return JSUndefined.INSTANCE;
        } catch (IllegalStateException | IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    /**
     * ArrayBuffer.prototype.slice(begin, end)
     * ES2020 24.1.4.3
     * Returns a new ArrayBuffer with a copy of bytes.
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("ArrayBuffer.prototype.slice called on non-ArrayBuffer");
        }

        int byteLength = buffer.getByteLength();

        // Get begin index
        int begin = 0;
        if (args.length > 0) {
            begin = JSTypeConversions.toInt32(context, args[0]);
        }

        // Get end index
        int end = byteLength;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            end = JSTypeConversions.toInt32(context, args[1]);
        }

        try {
            return buffer.slice(begin, end);
        } catch (IllegalStateException e) {
            return context.throwTypeError(e.getMessage());
        }
    }

    /**
     * ArrayBuffer.prototype.transfer([newByteLength])
     * ES2024 25.1.5.4
     * Creates a new ArrayBuffer with the same byte content as this buffer, then detaches this buffer.
     */
    public static JSValue transfer(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("ArrayBuffer.prototype.transfer called on non-ArrayBuffer");
        }

        int newByteLength = -1;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            newByteLength = JSTypeConversions.toInt32(context, args[0]);
        }

        try {
            return buffer.transfer(newByteLength);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return context.throwTypeError(e.getMessage());
        }
    }

    /**
     * ArrayBuffer.prototype.transferToFixedLength([newByteLength])
     * ES2024 25.1.5.5
     * Creates a new non-resizable ArrayBuffer with the same byte content as this buffer, then detaches this buffer.
     */
    public static JSValue transferToFixedLength(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("ArrayBuffer.prototype.transferToFixedLength called on non-ArrayBuffer");
        }

        int newByteLength = -1;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            newByteLength = JSTypeConversions.toInt32(context, args[0]);
        }

        try {
            return buffer.transferToFixedLength(newByteLength);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return context.throwTypeError(e.getMessage());
        }
    }
}
