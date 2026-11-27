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

import com.caoccao.qjs4j.builtins.number.NumberPrototype;
import com.caoccao.qjs4j.core.*;

/** Copying, shifting and writing indexed properties on ordinary array-like receivers. */
final class ArrayCopy {
    private ArrayCopy() {
    }

    private static boolean copyOrDeleteElement(JSContext context, JSObject object, PropertyKey from, PropertyKey to) {
        boolean present = object.has(from);
        if (context.hasPendingException()) {
            return false;
        }
        if (!present) {
            return deleteOrThrow(context, object, to);
        }
        JSValue value = object.get(from);
        return !context.hasPendingException() && setOrThrow(context, object, to, value);
    }

    private static JSObject copySpeciesRange(JSContext context, JSObject object, long start, long count) {
        JSObject result = ArraySpecies.createSpeciesObject(context, object, count);
        if (result == null) {
            return null;
        }
        for (long i = 0; i < count; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = ArrayLikeElements.getPresent(context, object, start + i);
            if (context.hasPendingException()) {
                return null;
            }
            if (element != null && !ArraySpecies.define(context, result, i, element)) {
                return null;
            }
        }
        result.set(PropertyKey.LENGTH, JSNumber.of(count));
        return context.hasPendingException() ? null : result;
    }

    private static JSValue copySpliced(JSContext context, JSObject obj, JSValue[] args, long length, long start,
            long deleteCount) {
        // Step 11: newLen = len + insertCount - actualDeleteCount
        long insertCount = Math.max(0, args.length - 2);
        long newLen = length - deleteCount + insertCount;

        // Step 12: If newLen > 2^53 - 1, throw a TypeError exception.
        if (newLen > NumberPrototype.MAX_SAFE_INTEGER) {
            return context.throwTypeError("Array length out of range");
        }

        // ArrayCreate step 1: If newLen > 2^32 - 1, throw a RangeError exception.
        if (newLen > 0xFFFF_FFFFL) {
            return context.throwRangeError("Invalid array length");
        }

        CopyResult result = new CopyResult(context, newLen);
        if (!result.copy(context, obj, 0, start, 1)) {
            return context.getPendingException();
        }
        for (int i = 2; i < args.length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            result.append(args[i]);
        }
        long suffix = start + deleteCount;
        return result.copy(context, obj, suffix, length - suffix, 1)
                ? result.finish(context)
                : context.getPendingException();
    }

    /**
     * Array.prototype.copyWithin(target, start[, end]) ES2015 23.1.3.3 Copies a sequence of array elements within the
     * array.
     */
    static JSValue copyWithin(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Let O be ? ToObject(this value).
        JSObject obj = JSTypeConversions.toObject(context, thisArg);
        if (obj == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 2: Let len be ? LengthOfArrayLike(O).
        long length = ArrayPrototype.lengthOfArrayLike(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        ArrayIndexRange.Copy copy = ArrayIndexRange.Copy.read(context, args, length);
        if (copy == null) {
            return context.getPendingException();
        }
        return moveElements(context, obj, copy.start(), copy.target(), copy.count(), copy.overlapsForward())
                ? obj
                : context.getPendingException();
    }

    /**
     * ES2024 DeletePropertyOrThrow(O, P) — throws TypeError if delete returns false.
     */
    static boolean deleteOrThrow(JSContext context, JSObject obj, PropertyKey key) {
        if (!obj.delete(key)) {
            if (!context.hasPendingException()) {
                context.throwTypeError("Cannot delete property '" + key.toPropertyString() + "'");
            }
            return false;
        }
        return !context.hasPendingException();
    }

    /**
     * Array.prototype.fill(value[, start[, end]]) ES2015 23.1.3.6 Fills all the elements of an array from a start index
     * to an end index with a static value.
     */
    static JSValue fill(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Let O be ? ToObject(this value).
        JSObject obj = ArrayPrototype.toObjectChecked(context, thisArg);
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        JSValue value = JSValue.argumentOrUndefined(args, 0);

        ArrayIndexRange range = ArrayIndexRange.read(context, args, 1, length);
        if (range == null) {
            return context.getPendingException();
        }

        // Step 8: Repeat, while k < final
        for (long i = range.start(); i < range.end(); i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            if (!setOrThrow(context, obj, key, value)) {
                return context.getPendingException();
            }
        }

        // Step 9: Return O.
        return obj;
    }

    static JSValue insertAtEnd(JSContext context, JSValue thisArg, JSValue[] args, boolean front) {
        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, front ? "unshift" : "push");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        // Per ES2024 step 4: If len + argCount > 2^53 - 1, throw a TypeError
        long newLen = length + args.length;
        if (newLen > NumberPrototype.MAX_SAFE_INTEGER) {
            return context.throwTypeError("Array too long");
        }

        if (front && args.length > 0 && !moveElements(context, obj, 0, args.length, length, true)) {
            return context.getPendingException();
        }
        if (!writeArguments(context, obj, front ? 0 : length, args, 0)
                || !setOrThrow(context, obj, PropertyKey.LENGTH, JSNumber.of(newLen))) {
            return context.getPendingException();
        }
        return JSNumber.of(newLen);
    }

    private static boolean moveElements(JSContext context, JSObject object, long from, long to, long count,
            boolean backwards) {
        long step = backwards ? -1 : 1;
        long end = backwards ? -1 : count;
        for (long i = backwards ? count - 1 : 0; i != end; i += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (!copyOrDeleteElement(context, object, PropertyKey.fromIndexString(from + i),
                    PropertyKey.fromIndexString(to + i))) {
                return false;
            }
        }
        return true;
    }

    private static String objectTag(JSObject obj) {
        if (obj instanceof JSArray) {
            return "[object Array]";
        }
        if (obj instanceof JSStringObject) {
            return "[object String]";
        }
        return "[object Object]";
    }

    static JSValue removeEndElement(JSContext context, JSValue thisArg, boolean first) {
        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, first ? "shift" : "pop");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        if (length == 0) {
            if (!setOrThrow(context, obj, PropertyKey.LENGTH, JSNumber.of(0))) {
                return context.getPendingException();
            }
            return JSUndefined.INSTANCE;
        }
        JSValue removed = obj.get(first ? PropertyKey.ZERO : PropertyKey.fromIndexString(length - 1));
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (first && !moveElements(context, obj, 1, 0, length - 1, false)) {
            return context.getPendingException();
        }
        if (!deleteOrThrow(context, obj, PropertyKey.fromIndexString(length - 1))) {
            return context.getPendingException();
        }
        if (!setOrThrow(context, obj, PropertyKey.LENGTH, JSNumber.of(length - 1))) {
            return context.getPendingException();
        }
        return removed;
    }

    /**
     * Array.prototype.reverse() Reverses the elements of an array in place.
     */
    static JSValue reverse(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, "reverse");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        for (long lower = 0, upper = length - 1; lower < upper; lower++, upper--) {

            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey lowerKey = PropertyKey.fromIndexString(lower);
            PropertyKey upperKey = PropertyKey.fromIndexString(upper);

            // Per ES2024: HasProperty then Get for lower, then HasProperty then Get for upper
            boolean lowerExists = obj.has(lowerKey);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            JSValue lowerVal = JSUndefined.INSTANCE;
            if (lowerExists) {
                lowerVal = obj.get(lowerKey);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }

            boolean upperExists = obj.has(upperKey);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            JSValue upperVal = JSUndefined.INSTANCE;
            if (upperExists) {
                upperVal = obj.get(upperKey);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }

            if (lowerExists && upperExists) {
                if (!setOrThrow(context, obj, lowerKey, upperVal)) {
                    return context.getPendingException();
                }
                if (!setOrThrow(context, obj, upperKey, lowerVal)) {
                    return context.getPendingException();
                }
            } else if (upperExists) {
                if (!setOrThrow(context, obj, lowerKey, upperVal)) {
                    return context.getPendingException();
                }
                if (!deleteOrThrow(context, obj, upperKey)) {
                    return context.getPendingException();
                }
            } else if (lowerExists) {
                if (!deleteOrThrow(context, obj, lowerKey)) {
                    return context.getPendingException();
                }
                if (!setOrThrow(context, obj, upperKey, lowerVal)) {
                    return context.getPendingException();
                }
            }
        }

        return obj;
    }

    /**
     * ES2024 Set(O, P, V, true) — always throws TypeError on failure regardless of strict mode. Generates V8-matching
     * error messages based on the failure reason.
     */
    static boolean setOrThrow(JSContext context, JSObject obj, PropertyKey key, JSValue value) {
        if (!obj.setWithResult(key, value)) {
            if (!context.hasPendingException()) {
                PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);
                if (desc != null && desc.isDataDescriptor() && !desc.isWritable()) {
                    context.throwTypeError("Cannot assign to read only property '" + key.toPropertyString()
                            + "' of object '" + objectTag(obj) + "'");
                } else if (!obj.isExtensible()) {
                    context.throwTypeError(
                            "Cannot add property " + key.toPropertyString() + ", object is not extensible");
                } else {
                    context.throwTypeError("Cannot set property '" + key.toPropertyString() + "'");
                }
            }
            return false;
        }
        return !context.hasPendingException();
    }

    /**
     * Array.prototype.slice([begin[, end]]) Returns a shallow copy of a portion of an array.
     */
    static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, "slice");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        ArrayIndexRange range = ArrayIndexRange.read(context, args, 0, length);
        if (range == null) {
            return context.getPendingException();
        }

        JSObject result = copySpeciesRange(context, obj, range.start(), range.count());
        return result != null
                ? result
                : context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
    }

    private static long spliceDeleteCount(JSContext context, JSValue[] args, long remaining) {
        if (args.length < 2) {
            return args.length == 0 ? 0 : remaining;
        }
        long count = ArrayIndexRange.toIntegerIndex(context, args[1]);
        return Math.max(0, Math.min(count, remaining));
    }

    static JSValue spliceElements(JSContext context, JSValue thisArg, JSValue[] args, boolean copy) {
        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, copy ? "toSpliced" : "splice");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        long start = ArrayIndexRange.relativeIndex(context, args, 0, length, 0);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long deleteCount = spliceDeleteCount(context, args, length - start);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        return copy
                ? copySpliced(context, obj, args, length, start, deleteCount)
                : spliceInPlace(context, obj, args, length, start, deleteCount);
    }

    private static JSValue spliceInPlace(JSContext context, JSObject obj, JSValue[] args, long length, long start,
            long deleteCount) {
        JSObject deletedObj = copySpeciesRange(context, obj, start, deleteCount);
        if (deletedObj == null) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }

        long insertCount = Math.max(0, args.length - 2);
        long newLen = length - deleteCount + insertCount;

        // Step 12: If len + insertCount - actualDeleteCount > 2^53 - 1, throw TypeError
        if (newLen > NumberPrototype.MAX_SAFE_INTEGER) {
            return context.throwTypeError("Array too long");
        }

        if (insertCount != deleteCount && !moveElements(context, obj, start + deleteCount, start + insertCount,
                length - start - deleteCount, insertCount > deleteCount)) {
            return context.getPendingException();
        }
        for (long i = newLen; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (!deleteOrThrow(context, obj, PropertyKey.fromIndexString(i))) {
                return context.getPendingException();
            }
        }

        if (!writeArguments(context, obj, start, args, 2)
                || !setOrThrow(context, obj, PropertyKey.LENGTH, JSNumber.of(newLen))) {
            return context.getPendingException();
        }
        return deletedObj;
    }

    /**
     * Array.prototype.toReversed() ES2023 23.1.3.31 Returns a new array with elements in reversed order (immutable
     * version of reverse).
     */
    static JSValue toReversed(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, "toReversed");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        // ArrayCreate step 1: If length > 2^32 - 1, throw a RangeError exception.
        if (length > 0xFFFF_FFFFL) {
            return context.throwRangeError("Invalid array length");
        }

        CopyResult result = new CopyResult(context, length);
        return result.copy(context, obj, length - 1, length, -1)
                ? result.finish(context)
                : context.getPendingException();
    }

    /**
     * Array.prototype.with(index, value) ES2023 23.1.3.34 Returns a new array with the element at the given index
     * replaced (immutable version of arr[index] = value).
     */
    static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, "with");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        // ArrayCreate step 1: If length > 2^32 - 1, throw a RangeError exception.
        if (length > 0xFFFF_FFFFL) {
            return context.throwRangeError("Invalid array length");
        }

        if (args.length < 2) {
            return context.throwTypeError("Array.prototype.with requires 2 arguments");
        }

        double indexD = JSTypeConversions.toInteger(context, args[0]);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Normalize negative index
        long index = (long) indexD;
        if (index < 0) {
            index = length + index;
        }

        // Check bounds
        if (index < 0 || index >= length) {
            return context.throwRangeError("Index out of bounds");
        }

        CopyResult result = new CopyResult(context, length);
        if (!result.copy(context, obj, 0, index, 1)) {
            return context.getPendingException();
        }
        context.getVirtualMachine().checkExecutionInterruptForNative();
        result.append(args[1]);
        return result.copy(context, obj, index + 1, length - index - 1, 1)
                ? result.finish(context)
                : context.getPendingException();
    }

    private static boolean writeArguments(JSContext context, JSObject object, long target, JSValue[] args, int first) {
        for (int i = first; i < args.length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (!setOrThrow(context, object, PropertyKey.fromIndexString(target++), args[i])) {
                return false;
            }
        }
        return true;
    }

    private static final class CopyResult {
        private final JSValue[] dense;
        private int next;
        private final JSArray sparse;

        CopyResult(JSContext context, long length) {
            dense = length <= JSArray.MAX_DENSE_SIZE ? new JSValue[(int) length] : null;
            sparse = dense == null ? context.createJSArray(0, (int) length) : null;
        }

        void append(JSValue value) {
            if (dense != null) {
                dense[next++] = value;
            } else {
                sparse.push(value);
            }
        }

        boolean copy(JSContext context, JSObject source, long start, long count, int step) {
            for (long i = 0; i < count; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                JSValue value = ArrayPrototype.getElement(context, source, start);
                if (context.hasPendingException()) {
                    return false;
                }
                append(value);
                start += step;
            }
            return true;
        }

        JSArray finish(JSContext context) {
            return dense == null ? sparse : context.createJSArray(dense, true);
        }
    }
}
