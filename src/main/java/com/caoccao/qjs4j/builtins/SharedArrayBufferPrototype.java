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

import java.nio.ByteBuffer;

/**
 * SharedArrayBuffer.prototype methods implementation.
 * Based on ES2024 SharedArrayBuffer specification.
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
     * SharedArrayBuffer.prototype.slice(start, end)
     * ES2024 25.2.4.3
     * Returns a new SharedArrayBuffer with a copy of bytes from start to end.
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSharedArrayBuffer buffer)) {
            return context.throwTypeError("SharedArrayBuffer.prototype.slice called on non-SharedArrayBuffer");
        }

        int len = buffer.getByteLength();

        // Step 4: Let relativeStart be ? ToIntegerOrInfinity(start).
        int first;
        if (args.length > 0) {
            double relativeStart = JSTypeConversions.toInteger(context, args[0]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (relativeStart < 0) {
                first = (int) Math.max(len + relativeStart, 0);
            } else {
                first = (int) Math.min(relativeStart, len);
            }
        } else {
            first = 0;
        }

        // Step 7: If end is undefined, let relativeEnd be len; else let relativeEnd be ? ToIntegerOrInfinity(end).
        int fin;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            double relativeEnd = JSTypeConversions.toInteger(context, args[1]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (relativeEnd < 0) {
                fin = (int) Math.max(len + relativeEnd, 0);
            } else {
                fin = (int) Math.min(relativeEnd, len);
            }
        } else {
            fin = len;
        }

        int newLen = Math.max(fin - first, 0);

        // Step 11: Let ctor be ? SpeciesConstructor(O, %SharedArrayBuffer%).
        JSValue ctor = speciesConstructor(context, buffer);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 12: Let new be ? Construct(ctor, « newLen »).
        JSValue newObj;
        if (ctor instanceof JSUndefined) {
            // Use default SharedArrayBuffer constructor
            JSSharedArrayBuffer newBuffer = new JSSharedArrayBuffer(newLen);
            context.transferPrototype(newBuffer, JSSharedArrayBuffer.NAME);
            newObj = newBuffer;
        } else {
            newObj = JSReflectObject.construct(context, JSUndefined.INSTANCE,
                    new JSValue[]{ctor, new JSArray(JSNumber.of(newLen))});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }

        // Step 13: If new does not have an [[ArrayBufferData]] internal slot, throw a TypeError.
        if (!(newObj instanceof JSSharedArrayBuffer newBuffer)) {
            return context.throwTypeError("Species constructor did not return a SharedArrayBuffer");
        }

        // Step 14: If new is this, throw a TypeError.
        if (newBuffer == buffer) {
            return context.throwTypeError("cannot use identical SharedArrayBuffer");
        }

        // Step 15: If new.[[ArrayBufferByteLength]] < newLen, throw a TypeError.
        if (newBuffer.getByteLength() < newLen) {
            return context.throwTypeError("new SharedArrayBuffer is too small");
        }

        // Step 16: Copy bytes
        if (newLen > 0) {
            byte[] bytes = new byte[newLen];
            ByteBuffer src = buffer.getBuffer();
            synchronized (src) {
                ByteBuffer source = src.duplicate();
                source.position(first);
                source.limit(first + newLen);
                source.get(bytes);
            }
            ByteBuffer dst = newBuffer.getBuffer();
            synchronized (dst) {
                ByteBuffer target = dst.duplicate();
                target.position(0);
                target.put(bytes);
            }
        }

        return newBuffer;
    }

    /**
     * SpeciesConstructor(O, defaultConstructor) per ES2024 7.3.20.
     * Returns JSUndefined.INSTANCE to use the default constructor,
     * or the species constructor function.
     * Sets pending exception on error.
     */
    private static JSValue speciesConstructor(JSContext context, JSObject obj) {
        JSValue ctor = obj.get(context, PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (ctor instanceof JSUndefined) {
            return JSUndefined.INSTANCE;
        }
        if (!(ctor instanceof JSObject ctorObj)) {
            context.throwTypeError("constructor is not an object");
            return JSUndefined.INSTANCE;
        }
        JSValue species = ctorObj.get(context, PropertyKey.SYMBOL_SPECIES);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (species instanceof JSUndefined || species instanceof JSNull) {
            return JSUndefined.INSTANCE;
        }
        if (species instanceof JSFunction) {
            return species;
        }
        context.throwTypeError("Species is not a constructor");
        return JSUndefined.INSTANCE;
    }
}
