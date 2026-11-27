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

package com.caoccao.qjs4j.builtins.buffer;

import com.caoccao.qjs4j.builtins.array.ArrayIndexRange;
import com.caoccao.qjs4j.builtins.number.NumberPrototype;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.IJSArrayBuffer;
import com.caoccao.qjs4j.core.buffer.JSArrayBuffer;
import com.caoccao.qjs4j.core.buffer.JSSharedArrayBuffer;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

/**
 * Implementation of ArrayBuffer.prototype methods. Based on ES2020 ArrayBuffer specification.
 */
public final class ArrayBufferPrototype {

    /**
     * get ArrayBuffer.prototype.byteLength ES2020 24.1.4.1 Returns the byte length of the buffer.
     */
    public static JSValue getByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("get ArrayBuffer.prototype.byteLength called on non-ArrayBuffer");
        }

        return JSNumber.of(buffer.getByteLength());
    }

    /**
     * get ArrayBuffer.prototype.detached ES2024 25.1.5.1 Returns true if the ArrayBuffer has been detached.
     */
    public static JSValue getDetached(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("get ArrayBuffer.prototype.detached called on non-ArrayBuffer");
        }

        return JSBoolean.valueOf(buffer.isDetached());
    }

    /**
     * get ArrayBuffer.prototype.immutable ES2025 immutable ArrayBuffer proposal. Returns true if the ArrayBuffer is
     * immutable.
     */
    public static JSValue getImmutable(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("get ArrayBuffer.prototype.immutable called on non-ArrayBuffer");
        }

        return JSBoolean.valueOf(buffer.isImmutable());
    }

    /**
     * get ArrayBuffer.prototype.maxByteLength ES2024 25.1.5.2 Returns the maximum byte length that the ArrayBuffer can
     * be resized to.
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
     * get ArrayBuffer.prototype.resizable ES2024 25.1.5.3 Returns true if the ArrayBuffer can be resized.
     */
    public static JSValue getResizable(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("get ArrayBuffer.prototype.resizable called on non-ArrayBuffer");
        }

        return JSBoolean.valueOf(buffer.isResizable());
    }

    /**
     * get ArrayBuffer.prototype[@@toStringTag] ES2020 24.1.4.4 Returns "ArrayBuffer".
     */
    public static JSValue getToStringTag(JSContext context, JSValue thisArg, JSValue[] args) {
        return new JSString(JSArrayBuffer.NAME);
    }

    /**
     * ArrayBuffer.prototype.resize(newByteLength) ES2024 25.1.5.4 Resizes the ArrayBuffer to the specified size, in
     * bytes.
     */
    public static JSValue resize(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("ArrayBuffer.prototype.resize called on non-ArrayBuffer");
        }

        // Step 2: RequireInternalSlot(O, [[ArrayBufferMaxByteLength]]) - check resizable
        if (!buffer.isResizable()) {
            return context.throwTypeError(
                    "Method ArrayBuffer.prototype.resize called on incompatible receiver #<ArrayBuffer>");
        }

        // Step 4: Let newByteLength be ? ToIndex(newLength) - may trigger valueOf which could detach
        long newByteLength;
        try {
            newByteLength = JSTypeConversions.toIndex(context, JSValue.argumentOrUndefined(args, 0));
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
            buffer.resize(newByteLength);
            return JSUndefined.INSTANCE;
        } catch (JSErrorException e) {
            // Preserve the error's own type. Collapsing every JSErrorException to one type turned
            // a RangeError into a TypeError (or the reverse) whenever a precheck above missed a
            // case, which is precisely when the type matters.
            return context.throwError(e);
        }
    }

    /**
     * ArrayBuffer.prototype.slice(begin, end) ES2020 24.1.4.3 Returns a new ArrayBuffer with a copy of bytes.
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("ArrayBuffer.prototype.slice called on non-ArrayBuffer");
        }

        return sliceBuffer(context, buffer, args);
    }

    static <T extends JSObject & IJSArrayBuffer> JSValue sliceBuffer(JSContext context, T source, JSValue[] args) {
        boolean shared = source.isShared();
        if (!shared && source.isDetached()) {
            return context.throwTypeError("Cannot slice a detached ArrayBuffer");
        }
        long length = source.getByteLength();
        long start = ArrayIndexRange.relativeIndex(context, args, 0, length, 0);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long end = ArrayIndexRange.relativeIndex(context, args, 1, length, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long newLength = Math.max(end - start, 0);
        JSValue constructor = speciesConstructor(context, source, shared);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSValue result;
        if (!(constructor instanceof JSUndefined)) {
            result = JSReflectObject.construct(context, JSUndefined.INSTANCE,
                    new JSValue[]{constructor, new JSArray(context, JSNumber.of(newLength))});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        } else if (shared) {
            JSSharedArrayBuffer buffer = new JSSharedArrayBuffer(context, newLength);
            context.transferPrototype(buffer, JSSharedArrayBuffer.NAME);
            result = buffer;
        } else {
            result = context.createJSArrayBuffer(newLength);
        }
        if (!(result instanceof IJSArrayBuffer target) || target.isShared() != shared) {
            return context.throwTypeError(shared
                    ? "Species constructor did not return a SharedArrayBuffer"
                    : "Species constructor did not return an ArrayBuffer");
        }
        String name = shared ? JSSharedArrayBuffer.NAME : JSArrayBuffer.NAME;
        if (target == source) {
            return context.throwTypeError("cannot use identical " + name);
        }
        if (!shared && target.isDetached()) {
            return context.throwTypeError("Species constructor returned a detached ArrayBuffer");
        }
        if (target.getByteLength() < newLength) {
            return context.throwTypeError("new " + name + " is too small");
        }
        if (!shared && source.isDetached()) {
            return context.throwTypeError("ArrayBuffer was detached during slice");
        }
        if (newLength > 0) {
            source.getBuffer().copyTo(target.getBuffer(), start, 0, newLength);
        }
        return result;
    }

    /**
     * SpeciesConstructor(O, defaultConstructor) per ES2020 7.3.20. Returns JSUndefined.INSTANCE to use the default
     * constructor, or the species constructor function. Sets pending exception on error.
     */
    private static JSValue speciesConstructor(JSContext context, JSObject obj, boolean shared) {
        JSValue ctor = obj.get(PropertyKey.CONSTRUCTOR);
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
        JSValue species = ctorObj.get(PropertyKey.SYMBOL_SPECIES);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (species instanceof JSUndefined || species instanceof JSNull) {
            return JSUndefined.INSTANCE;
        }
        if (shared ? species instanceof JSFunction : JSTypeChecking.isConstructor(species)) {
            return species;
        }
        context.throwTypeError("Species is not a constructor");
        return JSUndefined.INSTANCE;
    }

    /**
     * ArrayBuffer.prototype.transfer([newByteLength]) ES2024 25.1.5.4 Creates a new ArrayBuffer with the same byte
     * content as this buffer, then detaches this buffer.
     */
    public static JSValue transfer(JSContext context, JSValue thisArg, JSValue[] args) {
        return transfer(context, thisArg, args, false);
    }

    private static JSValue transfer(JSContext context, JSValue thisArg, JSValue[] args, boolean fixedLength) {
        String methodName = fixedLength ? "transferToFixedLength" : "transfer";
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("ArrayBuffer.prototype." + methodName + " called on non-ArrayBuffer");
        }

        // QuickJS ordering: ToIndex before detach check (valueOf could detach)
        long newByteLength;
        if (args.length < 1 || args[0] instanceof JSUndefined) {
            newByteLength = buffer.getByteLength();
        } else {
            try {
                newByteLength = JSTypeConversions.toIndex(context, args[0]);
            } catch (JSRangeErrorException e) {
                return context.throwRangeError(e.getMessage());
            }
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }

        if (buffer.isDetached()) {
            return context.throwTypeError(
                    "Cannot perform ArrayBuffer.prototype." + methodName + " on a detached ArrayBuffer");
        }

        // Step 6: If IsImmutableBuffer(arrayBuffer) is true, throw a TypeError
        if (buffer.isImmutable()) {
            return context.throwTypeError("Cannot transfer an immutable ArrayBuffer");
        }

        if (newByteLength > NumberPrototype.MAX_SAFE_INTEGER) {
            return context.throwRangeError("invalid array buffer length");
        }

        try {
            return fixedLength
                    ? buffer.transferToFixedLength(context, newByteLength)
                    : buffer.transfer(context, newByteLength);
        } catch (JSErrorException e) {
            // Preserve the error's own type. Collapsing every JSErrorException to one type turned
            // a RangeError into a TypeError (or the reverse) whenever a precheck above missed a
            // case, which is precisely when the type matters.
            return context.throwError(e);
        }
    }

    /**
     * ArrayBuffer.prototype.transferToFixedLength([newByteLength]) ES2024 25.1.5.5 Creates a new non-resizable
     * ArrayBuffer with the same byte content as this buffer, then detaches this buffer.
     */
    public static JSValue transferToFixedLength(JSContext context, JSValue thisArg, JSValue[] args) {
        return transfer(context, thisArg, args, true);
    }

    /**
     * ArrayBuffer.prototype.transferToImmutable() ES2025 - Creates a new immutable ArrayBuffer with the same byte
     * content, then detaches this buffer.
     */
    public static JSValue transferToImmutable(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArrayBuffer buffer)) {
            return context.throwTypeError("ArrayBuffer.prototype.transferToImmutable called on non-ArrayBuffer");
        }

        try {
            return buffer.transferToImmutable(context);
        } catch (JSErrorException e) {
            // Preserve the error's own type. Collapsing every JSErrorException to one type turned
            // a RangeError into a TypeError (or the reverse) whenever a precheck above missed a
            // case, which is precisely when the type matters.
            return context.throwError(e);
        }
    }
}
