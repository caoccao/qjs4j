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

public final class TypedArrayPrototype {
    private TypedArrayPrototype() {
    }

    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.at");
        if (typedArray == null) {
            return context.getPendingException();
        }
        int index = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        if (index < 0) {
            index += typedArray.getLength();
        }
        if (index < 0 || index >= typedArray.getLength()) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(typedArray.getElement(index));
    }

    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.entries");
        if (typedArray == null) {
            return context.getPendingException();
        }
        final int[] index = {0};
        return new JSIterator(context, () -> {
            if (typedArray.isOutOfBounds()) {
                context.throwTypeError("Cannot perform Array Iterator.prototype.next on a typed array backed by a detached or out-of-bounds buffer");
                return JSIterator.IteratorResult.done(context);
            }
            if (index[0] >= typedArray.getLength()) {
                return JSIterator.IteratorResult.done(context);
            }
            JSArray pair = context.createJSArray();
            pair.push(JSNumber.of(index[0]));
            pair.push(JSNumber.of(typedArray.getElement(index[0])));
            index[0]++;
            return JSIterator.IteratorResult.of(context, pair);
        }, "Array Iterator");
    }

    public static JSValue getBuffer(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "get TypedArray.prototype.buffer");
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSArrayBufferable buffer = typedArray.getBuffer();
        if (buffer instanceof JSValue value) {
            return value;
        }
        return context.throwTypeError("TypedArray buffer is not a JSValue");
    }

    public static JSValue getByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "get TypedArray.prototype.byteLength");
        if (typedArray == null) {
            return context.getPendingException();
        }
        return JSNumber.of(typedArray.getByteLength());
    }

    public static JSValue getByteOffset(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "get TypedArray.prototype.byteOffset");
        if (typedArray == null) {
            return context.getPendingException();
        }
        return JSNumber.of(typedArray.getByteOffset());
    }

    public static JSValue getLength(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "get TypedArray.prototype.length");
        if (typedArray == null) {
            return context.getPendingException();
        }
        return JSNumber.of(typedArray.getLength());
    }

    public static JSValue getToStringTag(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSTypedArray typedArray)) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(getTypedArrayName(typedArray));
    }

    private static String getTypedArrayName(JSTypedArray typedArray) {
        if (typedArray instanceof JSInt8Array) return JSInt8Array.NAME;
        if (typedArray instanceof JSUint8Array) return JSUint8Array.NAME;
        if (typedArray instanceof JSUint8ClampedArray) return JSUint8ClampedArray.NAME;
        if (typedArray instanceof JSInt16Array) return JSInt16Array.NAME;
        if (typedArray instanceof JSUint16Array) return JSUint16Array.NAME;
        if (typedArray instanceof JSInt32Array) return JSInt32Array.NAME;
        if (typedArray instanceof JSUint32Array) return JSUint32Array.NAME;
        if (typedArray instanceof JSFloat16Array) return JSFloat16Array.NAME;
        if (typedArray instanceof JSFloat32Array) return JSFloat32Array.NAME;
        if (typedArray instanceof JSFloat64Array) return JSFloat64Array.NAME;
        if (typedArray instanceof JSBigInt64Array) return JSBigInt64Array.NAME;
        if (typedArray instanceof JSBigUint64Array) return JSBigUint64Array.NAME;
        return "TypedArray";
    }

    public static JSValue join(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.join");
        if (typedArray == null) {
            return context.getPendingException();
        }
        String separator = args.length > 0 && !args[0].isUndefined()
                ? JSTypeConversions.toString(context, args[0]).value()
                : ",";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < typedArray.getLength(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            JSValue value = JSNumber.of(typedArray.getElement(i));
            sb.append(JSTypeConversions.toString(context, value).value());
        }
        return new JSString(sb.toString());
    }

    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.keys");
        if (typedArray == null) {
            return context.getPendingException();
        }
        final int[] index = {0};
        return new JSIterator(context, () -> {
            if (typedArray.isOutOfBounds()) {
                context.throwTypeError("Cannot perform Array Iterator.prototype.next on a typed array backed by a detached or out-of-bounds buffer");
                return JSIterator.IteratorResult.done(context);
            }
            if (index[0] >= typedArray.getLength()) {
                return JSIterator.IteratorResult.done(context);
            }
            return JSIterator.IteratorResult.of(context, JSNumber.of(index[0]++));
        }, "Array Iterator");
    }

    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.set");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (args.length == 0) {
            return context.throwTypeError("TypedArray.prototype.set requires a source array");
        }
        int offset = args.length > 1 ? (int) JSTypeConversions.toInteger(context, args[1]) : 0;
        try {
            typedArray.setArray(context, args[0], offset);
        } catch (JSRangeErrorException e) {
            return context.throwRangeError(e.getMessage());
        } catch (IllegalArgumentException e) {
            return context.throwTypeError(e.getMessage());
        }
        return JSUndefined.INSTANCE;
    }

    public static JSValue subarray(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.subarray");
        if (typedArray == null) {
            return context.getPendingException();
        }
        int begin = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        int end = args.length > 1 && !args[1].isUndefined()
                ? JSTypeConversions.toInt32(context, args[1])
                : typedArray.getLength();
        JSTypedArray result = typedArray.subarray(begin, end);
        result.setPrototype(typedArray.getPrototype());
        return result;
    }

    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        } else if (thisArg instanceof JSTypedArray jsTypedArray) {
            return new JSString(jsTypedArray.toString());
        }
        return JSTypeConversions.toString(context, thisArg);
    }

    private static JSTypedArray toTypedArray(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTypedArray typedArray)) {
            context.throwTypeError(methodName + " called on non-TypedArray");
            return null;
        }
        return typedArray;
    }

    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.values");
        if (typedArray == null) {
            return context.getPendingException();
        }
        final int[] index = {0};
        return new JSIterator(context, () -> {
            if (typedArray.isOutOfBounds()) {
                context.throwTypeError("Cannot perform Array Iterator.prototype.next on a typed array backed by a detached or out-of-bounds buffer");
                return JSIterator.IteratorResult.done(context);
            }
            if (index[0] >= typedArray.getLength()) {
                return JSIterator.IteratorResult.done(context);
            }
            return JSIterator.IteratorResult.of(context, JSNumber.of(typedArray.getElement(index[0]++)));
        }, "Array Iterator");
    }
}
