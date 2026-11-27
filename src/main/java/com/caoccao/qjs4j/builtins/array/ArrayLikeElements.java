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

package com.caoccao.qjs4j.builtins.array;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.JSTypedArray;

/** Indexed reads shared by Array and TypedArray methods, using the length captured on entry. */
final class ArrayLikeElements {
    private final JSContext context;
    final long length;
    final JSObject object;
    private final JSTypedArray typedArray;

    private ArrayLikeElements(JSContext context, JSObject object, long length, JSTypedArray typedArray) {
        this.context = context;
        this.object = object;
        this.length = length;
        this.typedArray = typedArray;
    }

    JSValue at(JSValue[] args) {
        long index = ArrayIndexRange.elementIndex(context, args, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        return index < 0 || index >= length ? JSUndefined.INSTANCE : getProperty(index);
    }

    JSValue find(JSValue[] args, boolean reverse, boolean returnIndex) {
        JSValue callback = args[0];
        JSValue callbackThis = JSValue.argumentOrUndefined(args, 1);
        long step = reverse ? -1 : 1;
        long end = reverse ? -1 : length;
        for (long i = reverse ? length - 1 : 0; i != end; i += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = get(i);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            JSValue result = callElement(context, callback, callbackThis, element, i, object);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                return returnIndex ? JSNumber.of(i) : element;
            }
        }
        return returnIndex ? JSNumber.of(-1) : JSUndefined.INSTANCE;
    }

    JSValue get(long index) {
        return typedArray == null
                ? ArrayPrototype.getElement(context, object, index)
                : typedArray.getElementOrUndefined(index);
    }

    /** HasProperty followed by Get for ordinary receivers. Java null denotes a hole. */
    JSValue getPresent(long index) {
        if (typedArray != null) {
            return typedArray.getElementOrUndefined(index);
        }
        return getPresent(context, object, index);
    }

    JSValue getProperty(long index) {
        return typedArray == null
                ? object.get(PropertyKey.fromIndexString(index))
                : typedArray.getElementOrUndefined(index);
    }

    JSValue iterate(JSValue[] args, ArrayIteration iteration) {
        JSValue callbackThis = JSValue.argumentOrUndefined(args, 1);
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = getPresent(i);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (element == null) {
                continue;
            }
            JSValue result = callElement(context, args[0], callbackThis, element, i, object);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (iteration.stopsAt(result)) {
                return iteration.stopResult;
            }
        }
        return iteration.completion;
    }

    JSValue reduce(JSValue[] args, boolean reverse) {
        JSValue callback = args[0];
        JSValue accumulator = args.length >= 2 ? args[1] : null;
        long step = reverse ? -1 : 1;
        long end = reverse ? -1 : length;
        for (long i = reverse ? length - 1 : 0; i != end; i += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = getPresent(i);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (element == null) {
                continue;
            }
            if (accumulator == null) {
                accumulator = element;
            } else {
                accumulator = JSCall.call(context, callback, JSUndefined.INSTANCE,
                        new JSValue[]{accumulator, element, JSNumber.of(i), object});
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        }
        return accumulator == null
                ? context.throwTypeError("Reduce of empty array with no initial value")
                : accumulator;
    }

    JSValue search(JSValue[] args, boolean reverse, boolean includes) {
        JSValue missing = includes ? JSBoolean.FALSE : JSNumber.of(-1);
        if (length == 0) {
            return missing;
        }
        JSValue needle = JSValue.argumentOrUndefined(args, 0);
        double offset = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : reverse ? length - 1 : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!includes && typedArray != null && typedArray.isOutOfBounds()) {
            return missing;
        }
        long start = ArrayIndexRange.searchStart(offset, length, reverse);
        long step = reverse ? -1 : 1;
        long end = reverse ? -1 : length;
        for (long i = start; i != end; i += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = includes ? get(i) : getPresent(i);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (element != null && (includes
                    ? JSTypeConversions.sameValueZero(element, needle)
                    : JSTypeConversions.strictEquals(element, needle))) {
                return includes ? JSBoolean.TRUE : JSNumber.of(i);
            }
        }
        return missing;
    }

    static ArrayLikeElements arrayCallback(JSContext context, JSValue receiver, JSValue[] args, String method) {
        ArrayLikeElements elements = ordinary(context, receiver, method);
        if (elements != null && (args.length == 0 || !JSTypeChecking.isCallable(args[0]))) {
            context.throwTypeError("Callback must be a function");
            return null;
        }
        return elements;
    }

    static JSValue callElement(JSContext context, JSValue callback, JSValue thisArg, JSValue element, long index,
            JSObject receiver) {
        return JSCall.call(context, callback, thisArg, new JSValue[]{element, JSNumber.of(index), receiver});
    }

    static ArrayLikeElements capture(JSContext context, JSObject object) {
        long length = ArrayPrototype.checkedLength(context, object);
        return length < 0 ? null : new ArrayLikeElements(context, object, length, null);
    }

    static JSValue getPresent(JSContext context, JSObject object, long index) {
        if (object instanceof JSArray array && array.hasElement(index)) {
            return array.get(index);
        }
        PropertyKey key = PropertyKey.fromIndexString(index);
        return object.has(key) && !context.hasPendingException() ? object.get(key) : null;
    }

    static ArrayLikeElements ordinary(JSContext context, JSValue receiver, String method) {
        return capture(context, ArrayPrototype.requireArrayLike(context, receiver, method));
    }

    static ArrayLikeElements typed(JSContext context, JSValue receiver, String method) {
        JSTypedArray array = TypedArrayPrototype.validateTypedArray(context, receiver, method);
        return array == null ? null : new ArrayLikeElements(context, array, array.getLength(), array);
    }

    static ArrayLikeElements typedCallback(JSContext context, JSValue receiver, JSValue[] args, String method) {
        JSTypedArray array = TypedArrayPrototype.toTypedArrayForCallback(context, receiver, args, method);
        return array == null ? null : new ArrayLikeElements(context, array, array.getLength(), array);
    }
}
