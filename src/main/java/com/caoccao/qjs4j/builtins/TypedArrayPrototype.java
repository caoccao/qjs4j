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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TypedArrayPrototype {
    private TypedArrayPrototype() {
    }

    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.at");
        if (typedArray == null) {
            return context.getPendingException();
        }
        // Step 3: Capture length before coercion
        long length = typedArray.getLength();
        // Step 4: Coerce index (may resize buffer)
        double relativeIndex = args.length > 0 ? JSTypeConversions.toInteger(context, args[0]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // Steps 5-8: Use original length for bounds computation
        long index;
        if (relativeIndex >= 0) {
            index = (long) relativeIndex;
        } else {
            index = (long) (length + relativeIndex);
        }
        // Step 9: Check against original length
        if (index < 0 || index >= length) {
            return JSUndefined.INSTANCE;
        }
        // Step 10: Get element (safe for detached/resized buffers)
        return safeGetElement(typedArray, index);
    }

    /**
     * CompareTypedArrayElements per ES2024 23.2.4.4. Default comparison for typed array sort when no compareFn is
     * provided.
     */
    private static int compareTypedArrayElements(JSValue x, JSValue y) {
        if (x instanceof JSBigInt xBig && y instanceof JSBigInt yBig) {
            return xBig.value().compareTo(yBig.value());
        }
        double dx = ((JSNumber) x).value();
        double dy = ((JSNumber) y).value();
        if (Double.isNaN(dx) && Double.isNaN(dy)) {
            return 0;
        }
        if (Double.isNaN(dx)) {
            return 1;
        }
        if (Double.isNaN(dy)) {
            return -1;
        }
        if (dx < dy) {
            return -1;
        }
        if (dx > dy) {
            return 1;
        }
        if (dx == 0 && dy == 0) {
            // +0 > -0 per spec
            boolean xNeg = (Double.doubleToRawLongBits(dx) & 0x8000000000000000L) != 0;
            boolean yNeg = (Double.doubleToRawLongBits(dy) & 0x8000000000000000L) != 0;
            if (xNeg && !yNeg) {
                return -1;
            }
            if (!xNeg && yNeg) {
                return 1;
            }
        }
        return 0;
    }

    private static JSValue constructTypedArray(JSContext context, JSValue constructor, JSValue[] args) {
        JSValue result = JSReflectObject.constructSimple(context, constructor, args);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(result instanceof JSTypedArray)) {
            return context.throwTypeError("TypedArray species constructor did not return a TypedArray");
        }
        return result;
    }

    public static JSValue copyWithin(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.copyWithin");
        if (typedArray == null) {
            return context.getPendingException();
        }
        // Step 3: Capture length before coercion
        long length = typedArray.getLength();

        long target = relativeIndex(context, args, 0, 0, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long start = relativeIndex(context, args, 1, 0, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long end = relativeIndex(context, args, 2, length, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 14: Compute count from original length values
        long count = Math.min(end - start, length - target);
        if (count <= 0) {
            return typedArray;
        }

        // Step 15: Check for OOB after all argument coercion
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError(
                    "Cannot perform TypedArray.prototype.copyWithin on a typed array backed by a detached or out-of-bounds buffer");
        }

        // Steps 15d-i: Re-read length, clamp to/from/count
        long currentLength = typedArray.getLength();
        target = Math.min(target, currentLength);
        start = Math.min(start, currentLength);
        count = Math.min(count, currentLength - target);
        count = Math.min(count, currentLength - start);

        if (count <= 0) {
            return typedArray;
        }

        // Use byte-level copy for bit precision (preserves NaN encodings)
        int elementSize = typedArray.getBytesPerElement();
        IJSArrayBuffer arrayBuffer = typedArray.getBuffer();
        if (arrayBuffer.isDetached()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.copyWithin on a detached ArrayBuffer");
        }
        JSByteBuffer buffer = arrayBuffer.getBuffer().duplicate();
        buffer.order(arrayBuffer.getBuffer().order());
        long baseByteOffset = typedArray.getByteOffset();
        long fromByteOffset = baseByteOffset + start * elementSize;
        long toByteOffset = baseByteOffset + target * elementSize;
        long byteCount = count * elementSize;

        buffer.copyTo(buffer, fromByteOffset, toByteOffset, byteCount);

        return typedArray;
    }

    private static boolean countingSort16BitIntegerArray(JSContext context, JSTypedArray typedArray, long length) {
        if (length < (1 << 16)) {
            return false;
        }
        int valueOffset;
        if (typedArray instanceof JSInt16Array) {
            valueOffset = 1 << 15;
        } else if (typedArray instanceof JSUint16Array) {
            valueOffset = 0;
        } else {
            return false;
        }

        long[] counts = new long[1 << 16];
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            counts[(int) typedArray.getElement(i) + valueOffset]++;
        }
        long outputIndex = 0;
        for (int countIndex = 0; countIndex < counts.length; countIndex++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            long count = counts[countIndex];
            double value = countIndex - valueOffset;
            for (long i = 0; i < count; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                typedArray.setElement(outputIndex++, value);
            }
        }
        return true;
    }

    private static JSValue createIterator(JSContext context, JSValue thisArg, String method, IteratorKind kind) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, method);
        if (typedArray == null) {
            return context.getPendingException();
        }
        final long[] index = {0};
        return new JSIterator(context, () -> {
            if (typedArray.isOutOfBounds()) {
                context.throwTypeError(
                        "Cannot perform Array Iterator.prototype.next on a typed array backed by a detached or out-of-bounds buffer");
                return JSIterator.IteratorResult.done(context);
            }
            if (index[0] >= typedArray.getLength()) {
                return JSIterator.IteratorResult.done(context);
            }
            long current = index[0]++;
            JSValue value = switch (kind) {
                case KEYS -> JSNumber.of(current);
                case VALUES -> typedArray.getJSElement(current);
                case ENTRIES -> context.createJSArray(JSNumber.of(current), typedArray.getJSElement(current));
            };
            return JSIterator.IteratorResult.of(context, value);
        }, "Array Iterator");
    }

    private static JSTypedArray createSameType(JSContext context, JSTypedArray typedArray, long length) {
        JSValue constructor = context.getGlobalObject().get(PropertyKey.fromString(typedArray.getTypedArrayName()));
        JSValue result = JSReflectObject.constructSimple(context, constructor, new JSValue[]{JSNumber.of(length)});
        if (context.hasPendingException()) {
            return null;
        }
        if (!(result instanceof JSTypedArray resultArray)) {
            context.throwTypeError("TypedArray constructor did not return a TypedArray");
            return null;
        }
        return resultArray;
    }

    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        return createIterator(context, thisArg, "TypedArray.prototype.entries", IteratorKind.ENTRIES);
    }

    public static JSValue every(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterate(context, thisArg, args, ArrayIteration.EVERY);
    }

    public static JSValue fill(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.fill");
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSValue rawValue = JSValue.argumentOrUndefined(args, 0);

        // Step 3: Capture length BEFORE value conversion (per spec order)
        long length = typedArray.getLength();

        // Step 4/5: Convert value ONCE before coercing start/end
        JSValue convertedValue;
        if (isBigIntTypedArray(typedArray)) {
            convertedValue = JSTypeConversions.toBigInt(context, rawValue);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        } else {
            convertedValue = JSTypeConversions.toNumber(context, rawValue);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }

        long start = relativeIndex(context, args, 1, 0, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long end = relativeIndex(context, args, 2, length, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 14: Check if buffer is now detached (after value/start/end coercion)
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError(
                    "Cannot perform TypedArray.prototype.fill on a typed array backed by a detached or out-of-bounds buffer");
        }

        // Step 15-17: Re-read length, clamp endIndex and startIndex
        long currentLength = typedArray.getLength();
        end = Math.min(end, currentLength);
        start = Math.min(start, end);

        if (convertedValue instanceof JSNumber number) {
            double value = number.value();
            for (long i = start; i < end; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                typedArray.setElement(i, value);
            }
        } else {
            for (long i = start; i < end; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                typedArray.set(PropertyKey.fromIndex(i), convertedValue);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        }
        return typedArray;
    }

    public static JSValue filter(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArrayForCallback(context, thisArg, args, "TypedArray.prototype.filter");
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSValue callbackFn = args[0];
        JSValue callbackThisArg = JSValue.argumentOrUndefined(args, 1);
        long length = typedArray.getLength();

        // Collect kept elements
        List<JSValue> kept = new ArrayList<>();
        for (long k = 0; k < length; k++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue selected = JSCall.call(context, callbackFn, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(selected) == JSBoolean.TRUE) {
                kept.add(kValue);
            }
        }

        // TypedArraySpeciesCreate (includes validation)
        JSValue result = typedArraySpeciesCreate(context, typedArray, new JSValue[]{JSNumber.of(kept.size())});
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSTypedArray resultArray = (JSTypedArray) result;
        for (long i = 0; i < kept.size(); i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            resultArray.set(PropertyKey.fromIndex(i), kept.get((int) i));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return resultArray;
    }

    public static JSValue find(JSContext context, JSValue thisArg, JSValue[] args) {
        return findViaPredicate(context, thisArg, args, "TypedArray.prototype.find", false, false);
    }

    public static JSValue findIndex(JSContext context, JSValue thisArg, JSValue[] args) {
        return findViaPredicate(context, thisArg, args, "TypedArray.prototype.findIndex", false, true);
    }

    public static JSValue findLast(JSContext context, JSValue thisArg, JSValue[] args) {
        return findViaPredicate(context, thisArg, args, "TypedArray.prototype.findLast", true, false);
    }

    public static JSValue findLastIndex(JSContext context, JSValue thisArg, JSValue[] args) {
        return findViaPredicate(context, thisArg, args, "TypedArray.prototype.findLastIndex", true, true);
    }

    private static JSValue findViaPredicate(JSContext context, JSValue thisArg, JSValue[] args, String methodName,
            boolean reverse, boolean returnIndex) {
        JSTypedArray typedArray = toTypedArrayForCallback(context, thisArg, args, methodName);
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSValue callbackFn = args[0];
        JSValue callbackThisArg = JSValue.argumentOrUndefined(args, 1);
        long length = typedArray.getLength();
        long step = reverse ? -1 : 1;
        long end = reverse ? -1 : length;
        for (long k = reverse ? length - 1 : 0; k != end; k += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue testResult = JSCall.call(context, callbackFn, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(testResult) == JSBoolean.TRUE) {
                return returnIndex ? JSNumber.of(k) : kValue;
            }
        }
        return returnIndex ? JSNumber.of(-1) : JSUndefined.INSTANCE;
    }

    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterate(context, thisArg, args, ArrayIteration.FOR_EACH);
    }

    public static JSValue getBuffer(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "get TypedArray.prototype.buffer");
        if (typedArray == null) {
            return context.getPendingException();
        }
        IJSArrayBuffer buffer = typedArray.getBuffer();
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
        // Per ES spec: return 0 for detached buffers and out-of-bounds views
        if (typedArray.isOutOfBounds()) {
            return JSNumber.of(0);
        }
        return JSNumber.of(typedArray.getByteLength());
    }

    public static JSValue getByteOffset(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "get TypedArray.prototype.byteOffset");
        if (typedArray == null) {
            return context.getPendingException();
        }
        // Per ES spec: return 0 for detached buffers and out-of-bounds views
        if (typedArray.isOutOfBounds()) {
            return JSNumber.of(0);
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
        if (thisArg instanceof JSTypedArray typedArray) {
            // Per ES spec: return the TypedArrayName (e.g., "Float64Array"), not [object Float64Array]
            return new JSString(typedArray.getTypedArrayName());
        }
        return JSUndefined.INSTANCE;
    }

    public static JSValue includes(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.includes");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();
        if (length == 0) {
            return JSBoolean.FALSE;
        }
        JSValue searchElement = JSValue.argumentOrUndefined(args, 0);
        double fromIndexD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long fromIndex;
        if (fromIndexD >= length) {
            return JSBoolean.FALSE;
        } else if (fromIndexD < 0) {
            fromIndex = Math.max(0, (long) (length + fromIndexD));
        } else {
            fromIndex = (long) fromIndexD;
        }
        // After coercion of fromIndex, buffer may have been detached
        for (long k = fromIndex; k < length; k++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = safeGetElement(typedArray, k);
            // SameValueZero: NaN === NaN, +0 === -0
            if (sameValueZero(element, searchElement)) {
                return JSBoolean.TRUE;
            }
        }
        return JSBoolean.FALSE;
    }

    public static JSValue indexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchIndex(context, thisArg, args, false);
    }

    private static boolean isBigIntTypedArray(JSTypedArray typedArray) {
        return typedArray instanceof JSBigInt64Array || typedArray instanceof JSBigUint64Array;
    }

    private static JSValue iterate(JSContext context, JSValue thisArg, JSValue[] args, ArrayIteration iteration) {
        JSTypedArray typedArray = toTypedArrayForCallback(context, thisArg, args, iteration.typedArrayMethod);
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSValue callbackFn = args[0];
        JSValue callbackThisArg = JSValue.argumentOrUndefined(args, 1);
        long length = typedArray.getLength();
        for (long k = 0; k < length; k++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue testResult = JSCall.call(context, callbackFn, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (iteration.stopsAt(testResult)) {
                return iteration.stopResult;
            }
        }
        return iteration.completion;
    }

    public static JSValue join(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.join");
        if (typedArray == null) {
            return context.getPendingException();
        }
        // Step 3: Capture length BEFORE separator coercion
        long length = typedArray.getLength();

        // Step 4: Convert separator ONCE (may cause side effects like resizing/detaching buffer)
        String separator;
        if (args.length > 0 && !args[0].isUndefined()) {
            separator = JSTypeConversions.toString(context, args[0]).value();
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        } else {
            separator = ",";
        }

        // Use original length for iteration; elements that are now OOB become empty string
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (i > 0) {
                sb.append(separator);
            }
            JSValue element = safeGetElement(typedArray, i);
            if (!(element instanceof JSUndefined)) {
                sb.append(JSTypeConversions.toString(context, element).value());
            }
        }
        return new JSString(sb.toString());
    }

    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        return createIterator(context, thisArg, "TypedArray.prototype.keys", IteratorKind.KEYS);
    }

    public static JSValue lastIndexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchIndex(context, thisArg, args, true);
    }

    public static JSValue map(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArrayForCallback(context, thisArg, args, "TypedArray.prototype.map");
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSValue callbackFn = args[0];
        JSValue callbackThisArg = JSValue.argumentOrUndefined(args, 1);
        long length = typedArray.getLength();

        // TypedArraySpeciesCreate with same length
        JSValue result = typedArraySpeciesCreate(context, typedArray, new JSValue[]{JSNumber.of(length)});
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSTypedArray resultArray = (JSTypedArray) result;

        for (long k = 0; k < length; k++) {

            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue mappedValue = JSCall.call(context, callbackFn, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            resultArray.set(PropertyKey.fromIndex(k), mappedValue);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return resultArray;
    }

    public static JSValue reduce(JSContext context, JSValue thisArg, JSValue[] args) {
        return reduce(context, thisArg, args, "TypedArray.prototype.reduce", false);
    }

    private static JSValue reduce(JSContext context, JSValue thisArg, JSValue[] args, String methodName,
            boolean reverse) {
        JSTypedArray typedArray = toTypedArrayForCallback(context, thisArg, args, methodName);
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSValue callbackFn = args[0];
        long length = typedArray.getLength();
        long step = reverse ? -1 : 1;
        long end = reverse ? -1 : length;
        long k = reverse ? length - 1 : 0;
        JSValue accumulator;
        if (args.length > 1) {
            accumulator = args[1];
        } else {
            if (length == 0) {
                return context.throwTypeError("Reduce of empty array with no initial value");
            }
            accumulator = safeGetElement(typedArray, k);
            k += step;
        }
        for (; k != end; k += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue kValue = safeGetElement(typedArray, k);
            accumulator = JSCall.call(context, callbackFn, JSUndefined.INSTANCE,
                    new JSValue[]{accumulator, kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return accumulator;
    }

    public static JSValue reduceRight(JSContext context, JSValue thisArg, JSValue[] args) {
        return reduce(context, thisArg, args, "TypedArray.prototype.reduceRight", true);
    }

    private static long relativeIndex(JSContext context, JSValue[] args, int index, long defaultValue, long length) {
        if (index >= args.length || args[index] instanceof JSUndefined) {
            return defaultValue;
        }
        double relative = JSTypeConversions.toInteger(context, args[index]);
        return (long) (relative < 0 ? Math.max(length + relative, 0) : Math.min(relative, length));
    }

    private static int requireWorkspaceLength(JSContext context, long length) {
        if (length > Integer.MAX_VALUE - 8L) {
            throw new com.caoccao.qjs4j.exceptions.JSException(context.throwRangeError("Invalid array length"));
        }
        return (int) length;
    }

    public static JSValue reverse(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.reverse");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();
        long middle = length / 2;
        for (long lower = 0; lower < middle; lower++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            long upper = length - 1 - lower;
            JSValue lowerValue = typedArray.getJSElement(lower);
            JSValue upperValue = typedArray.getJSElement(upper);
            typedArray.set(PropertyKey.fromIndex(lower), upperValue);
            typedArray.set(PropertyKey.fromIndex(upper), lowerValue);
        }
        return typedArray;
    }

    /**
     * Safe element access that returns undefined for detached/out-of-bounds typed arrays. Per ES spec,
     * IntegerIndexedElementGet returns undefined when the buffer is detached or the index is out of bounds.
     */
    private static JSValue safeGetElement(JSTypedArray typedArray, long index) {
        if (typedArray.getBuffer().isDetached() || typedArray.isOutOfBounds() || index < 0
                || index >= typedArray.getLength()) {
            return JSUndefined.INSTANCE;
        }
        return typedArray.getJSElement(index);
    }

    /**
     * SameValueZero comparison per ES spec. Like === except NaN equals NaN, and +0 equals -0.
     */
    private static boolean sameValueZero(JSValue x, JSValue y) {
        if (JSTypeConversions.strictEquals(x, y)) {
            return true;
        }
        // NaN === NaN under SameValueZero
        if (x instanceof JSNumber xn && y instanceof JSNumber yn) {
            return Double.isNaN(xn.value()) && Double.isNaN(yn.value());
        }
        return false;
    }

    private static JSValue searchIndex(JSContext context, JSValue thisArg, JSValue[] args, boolean fromEnd) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg,
                fromEnd ? "TypedArray.prototype.lastIndexOf" : "TypedArray.prototype.indexOf");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();
        if (length == 0) {
            return JSNumber.of(-1);
        }
        JSValue searchElement = JSValue.argumentOrUndefined(args, 0);
        double fromIndexD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : fromEnd ? length - 1 : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // After coercion of fromIndex, check if TA went OOB (fixed-length shrink)
        if (typedArray.isOutOfBounds()) {
            return JSNumber.of(-1);
        }
        // Use ORIGINAL length for index computation and loop bounds (ES2024 taRecord caching)
        long start = ArrayPrototype.searchStart(fromIndexD, length, fromEnd);
        long end = fromEnd ? -1 : length;
        int step = fromEnd ? -1 : 1;
        for (long k = start; k != end; k += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = safeGetElement(typedArray, k);
            // indexOf uses strict equality (===)
            if (JSTypeConversions.strictEquals(element, searchElement)) {
                return JSNumber.of(k);
            }
        }
        return JSNumber.of(-1);
    }

    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray target = toTypedArray(context, thisArg, "TypedArray.prototype.set");
        if (target == null) {
            return context.getPendingException();
        }
        JSValue source = JSValue.argumentOrUndefined(args, 0);

        if (source instanceof JSTypedArray srcTypedArray) {
            return setFromTypedArray(context, target, srcTypedArray, args);
        } else {
            return setFromArrayLike(context, target, source, args);
        }
    }

    private static JSValue setFromArrayLike(JSContext context, JSTypedArray target, JSValue source, JSValue[] args) {
        // Step 3: ToIntegerOrInfinity(offset) - may detach buffer
        double targetOffsetD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (targetOffsetD < 0) {
            return context.throwRangeError("offset is out of bounds");
        }
        long targetOffset = (long) targetOffsetD;

        // Step 5: Check if target is detached/OOB (after offset coercion)
        if (target.isOutOfBounds()) {
            return context.throwTypeError("TypedArray is detached or out of bounds");
        }
        long targetLength = target.getLength();

        // Step 7: ToObject(source)
        JSObject src = JSTypeConversions.toObject(context, source);
        if (src == null || context.hasPendingException()) {
            return context.hasPendingException()
                    ? context.getPendingException()
                    : context.throwTypeError("Cannot convert source to object");
        }

        // Step 8: Get source length
        JSValue srcLengthValue = src.get(PropertyKey.LENGTH);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long srcLength = JSTypeConversions.toLength(context, srcLengthValue);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 9: Range check
        if (targetOffset > targetLength || srcLength > targetLength - targetOffset) {
            return context.throwRangeError("Source is too large");
        }

        boolean isBigInt = isBigIntTypedArray(target);

        // Step 11: Loop - convert values and write
        for (long k = 0; k < srcLength; k++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey pk = PropertyKey.fromIndexString(k);
            JSValue value = src.get(pk);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            // Convert value (may cause side effects including detaching/resizing buffer)
            JSValue convertedValue;
            if (isBigInt) {
                convertedValue = JSTypeConversions.toBigInt(context, value);
            } else {
                convertedValue = JSTypeConversions.toNumber(context, value);
            }
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            // After value conversion, check if buffer is still valid before writing
            long targetIndex = (targetOffset + k);
            if (!target.getBuffer().isDetached() && !target.isOutOfBounds() && targetIndex < target.getLength()) {
                target.set(PropertyKey.fromIndex(targetIndex), convertedValue);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        }
        return JSUndefined.INSTANCE;
    }

    private static JSValue setFromTypedArray(JSContext context, JSTypedArray target, JSTypedArray source,
            JSValue[] args) {
        // Step 3: ToIntegerOrInfinity(offset) - may detach buffer
        double targetOffsetD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (targetOffsetD < 0) {
            return context.throwRangeError("offset is out of bounds");
        }
        long targetOffset = (long) targetOffsetD;

        // Step 5: Check if target is OOB (after offset coercion)
        if (target.isOutOfBounds()) {
            return context.throwTypeError("TypedArray is detached or out of bounds");
        }
        long targetLength = target.getLength();

        // Step 9: Check source buffer detached
        if (source.getBuffer().isDetached()) {
            return context.throwTypeError("Source TypedArray buffer is detached");
        }
        // Step 11: Check source OOB
        if (source.isOutOfBounds()) {
            return context.throwTypeError("Source TypedArray is out of bounds");
        }
        long srcLength = source.getLength();

        // Step 13: Range check
        if (targetOffset > targetLength || srcLength > targetLength - targetOffset) {
            return context.throwRangeError("Source is too large");
        }

        // Step 14: Check content type compatibility
        boolean targetIsBigInt = isBigIntTypedArray(target);
        boolean sourceIsBigInt = isBigIntTypedArray(source);
        if (targetIsBigInt != sourceIsBigInt) {
            return context.throwTypeError("Cannot mix BigInt and non-BigInt typed arrays");
        }

        if (target.getClass() == source.getClass()) {
            source.getBuffer().getBuffer().copyTo(target.getBuffer().getBuffer(), source.getByteOffset(),
                    target.getByteOffset() + targetOffset * target.getBytesPerElement(), source.getByteLength());
            return JSUndefined.INSTANCE;
        }
        if (target.getBuffer() == source.getBuffer()) {
            source = source.copyStorage();
        }
        for (long i = 0; i < srcLength; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            target.set(PropertyKey.fromIndex(targetOffset + i), source.getJSElement(i));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return JSUndefined.INSTANCE;
    }

    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.slice");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();

        long start = relativeIndex(context, args, 0, 0, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long end = relativeIndex(context, args, 1, length, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long count = Math.max(0, end - start);

        // TypedArraySpeciesCreate
        JSValue result = typedArraySpeciesCreate(context, typedArray, new JSValue[]{JSNumber.of(count)});
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSTypedArray resultArray = (JSTypedArray) result;

        if (count > 0) {
            if (typedArray.isOutOfBounds()) {
                return context.throwTypeError(
                        "Cannot perform TypedArray.prototype.slice on a typed array backed by a detached or out-of-bounds buffer");
            }
            // Re-read length after species constructor may have resized (step 14c)
            long currentLength = typedArray.getLength();
            long newEnd = Math.min(end, currentLength);
            long srcCount = Math.max(0, newEnd - start);

            if (srcCount > 0 && !typedArray.getBuffer().isDetached() && !resultArray.getBuffer().isDetached()) {
                int elementSize = typedArray.getBytesPerElement();
                IJSArrayBuffer srcBuffer = typedArray.getBuffer();
                IJSArrayBuffer dstBuffer = resultArray.getBuffer();

                if (typedArray.getClass() == resultArray.getClass()) {
                    // Same type: byte-by-byte copy per spec step 14g
                    long srcByteOffset = typedArray.getByteOffset() + start * elementSize;
                    long dstByteOffset = resultArray.getByteOffset();
                    long byteCount = srcCount * elementSize;

                    if (srcBuffer == dstBuffer) {
                        // Same buffer: forward byte-by-byte copy per spec (step 14g.ix)
                        // This intentionally allows overlapping writes to produce spec-defined results
                        JSByteBuffer buf = srcBuffer.getBuffer().duplicate();
                        buf.order(srcBuffer.getBuffer().order());
                        for (long i = 0; i < byteCount; i++) {
                            context.getVirtualMachine().checkExecutionInterruptForNative();
                            byte b = buf.get(srcByteOffset + i);
                            buf.put(dstByteOffset + i, b);
                        }
                    } else {
                        srcBuffer.getBuffer().copyTo(dstBuffer.getBuffer(), srcByteOffset, dstByteOffset, byteCount);
                    }
                } else {
                    // Different types: per-element copy (step 14h), limited by srcCount
                    for (long k = 0; k < srcCount; k++) {
                        context.getVirtualMachine().checkExecutionInterruptForNative();
                        JSValue kValue = safeGetElement(typedArray, start + k);
                        resultArray.set(PropertyKey.fromIndex(k), kValue);
                        if (context.hasPendingException()) {
                            return context.getPendingException();
                        }
                    }
                }
            }
            // Elements beyond srcCount in result array remain zero-filled (default)
        }
        return resultArray;
    }

    public static JSValue some(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterate(context, thisArg, args, ArrayIteration.SOME);
    }

    public static JSValue sort(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.sort");
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSValue compareArg = JSValue.argumentOrUndefined(args, 0);
        if (!(compareArg instanceof JSUndefined)) {
            if (!JSTypeChecking.isCallable(compareArg)) {
                return context.throwTypeError("compareFn is not a function");
            }
        }
        long length = typedArray.getLength();
        if (length <= 1) {
            return typedArray;
        }

        // The default numeric ordering is exactly Java's primitive double ordering: NaN values
        // sort last and -0 sorts before +0. Keep BigInt and user callbacks on the JSValue path,
        // where their comparison semantics cannot be represented by a primitive double array.
        if (compareArg instanceof JSUndefined && !isBigIntTypedArray(typedArray)) {
            if (countingSort16BitIntegerArray(context, typedArray, length)) {
                return typedArray;
            }
            double[] elements = new double[requireWorkspaceLength(context, length)];
            for (long i = 0; i < length; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                elements[(int) i] = typedArray.getElement(i);
            }
            Arrays.sort(elements);
            for (long i = 0; i < length; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                typedArray.setElement(i, elements[(int) i]);
            }
            return typedArray;
        }

        return sortElements(context, typedArray, typedArray, length, compareArg);
    }

    private static JSValue sortElements(JSContext context, JSTypedArray typedArray, JSTypedArray target, long length,
            JSValue compareArg) {
        // Collect elements into array
        JSValue[] elements = new JSValue[requireWorkspaceLength(context, length)];
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            elements[(int) i] = typedArray.getJSElement(i);
        }

        // Sort
        if (!SortOperations.stableSort(context, elements,
                (a, b) -> compareArg instanceof JSUndefined
                        ? compareTypedArrayElements(a, b)
                        : SortOperations.compareWithCallback(context, compareArg, a, b))) {
            return context.getPendingException();
        }

        // Write back
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            target.set(PropertyKey.fromIndex(i), elements[(int) i]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return target;
    }

    private static JSValue speciesConstructor(JSContext context, JSTypedArray exemplar, boolean requireFunction) {
        JSValue fallback = context.getGlobalObject().get(PropertyKey.fromString(exemplar.getTypedArrayName()));
        JSValue constructor = exemplar.get(PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (constructor instanceof JSUndefined) {
            return fallback;
        }
        if (!(constructor instanceof JSObject object)) {
            return context.throwTypeError("constructor is not an object");
        }
        JSValue species = object.get(PropertyKey.SYMBOL_SPECIES);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (species instanceof JSNull || species instanceof JSUndefined) {
            return fallback;
        }
        if (requireFunction && !(species instanceof JSFunction)) {
            return context.throwTypeError("Species is not a constructor");
        }
        return species;
    }

    public static JSValue subarray(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.subarray");
        if (typedArray == null) {
            return context.getPendingException();
        }

        // Step 5-7: Get srcLength (0 if OOB)
        long srcLength = typedArray.getLength();

        long startIndex = relativeIndex(context, args, 0, 0, srcLength);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long endIndex = relativeIndex(context, args, 1, srcLength, srcLength);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        boolean endIsUndefined = args.length <= 1 || args[1].isUndefined();

        // Step 13-14: Compute beginByteOffset using stored byteOffset (preserved even when OOB)
        int elementSize = typedArray.getBytesPerElement();
        long srcByteOffset = typedArray.getByteOffset();
        long beginByteOffset = srcByteOffset + startIndex * elementSize;

        // SpeciesConstructor(O, defaultConstructor)
        IJSArrayBuffer buffer = typedArray.getBuffer();
        JSValue chosenConstructor = speciesConstructor(context, typedArray, false);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 15-16: Build argument list
        JSValue bufferArg = (buffer instanceof JSValue bv) ? bv : typedArray;
        JSValue[] constructArgs;
        if (typedArray.isLengthTracking() && endIsUndefined) {
            // Step 15: Length-tracking + end undefined → (buffer, beginByteOffset)
            // Result will also be length-tracking
            constructArgs = new JSValue[]{bufferArg, JSNumber.of(beginByteOffset)};
        } else {
            // Step 16: Fixed length → (buffer, beginByteOffset, newLength)
            long newLength = Math.max(0, endIndex - startIndex);
            constructArgs = new JSValue[]{bufferArg, JSNumber.of(beginByteOffset), JSNumber.of(newLength)};
        }

        return constructTypedArray(context, chosenConstructor, constructArgs);
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.toLocaleString");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();

        String separator = ",";

        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (i > 0) {
                sb.append(separator);
            }
            // Use safeGetElement to handle OOB (buffer may have been resized during callback)
            JSValue element = safeGetElement(typedArray, i);
            if (!(element instanceof JSUndefined) && !(element instanceof JSNull)) {
                // Invoke element.toLocaleString()
                JSValue toLocaleStringFn = null;
                if (element instanceof JSObject elementObj) {
                    toLocaleStringFn = elementObj.get(PropertyKey.fromString("toLocaleString"));
                } else {
                    // Auto-box primitive to get toLocaleString from prototype
                    JSObject boxed = JSTypeConversions.toObject(context, element);
                    if (boxed != null) {
                        toLocaleStringFn = boxed.get(PropertyKey.fromString("toLocaleString"));
                    }
                }
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (toLocaleStringFn instanceof JSFunction fn) {
                    JSValue result = fn.call(context, element, args);
                    if (context.hasPendingException()) {
                        return context.getPendingException();
                    }
                    sb.append(JSTypeConversions.toString(context, result).value());
                } else {
                    sb.append(JSTypeConversions.toString(context, element).value());
                }
            }
        }
        return new JSString(sb.toString());
    }

    public static JSValue toReversed(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.toReversed");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();

        // Create new typed array of same type (not species - ignores species per spec)
        JSTypedArray resultArray = createSameType(context, typedArray, length);
        if (resultArray == null) {
            return context.getPendingException();
        }

        for (long k = 0; k < length; k++) {

            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue fromValue = typedArray.getJSElement(length - 1 - k);
            resultArray.set(PropertyKey.fromIndex(k), fromValue);
        }
        return resultArray;
    }

    public static JSValue toSorted(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Validate compareFn first (before this-value validation per spec)
        JSValue compareArg = JSValue.argumentOrUndefined(args, 0);
        if (!(compareArg instanceof JSUndefined)) {
            if (!JSTypeChecking.isCallable(compareArg)) {
                return context.throwTypeError("compareFn is not a function");
            }
        }

        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.toSorted");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();

        // Create new typed array of same type (not species - ignores species per spec)
        JSTypedArray resultArray = createSameType(context, typedArray, length);
        if (resultArray == null) {
            return context.getPendingException();
        }

        return sortElements(context, typedArray, resultArray, length, compareArg);
    }

    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        // TypedArray.prototype.toString delegates to Array.prototype.join
        // which uses the "join" method from the object
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        if (thisArg instanceof JSObject jsObject) {
            JSValue joinFn = jsObject.get(PropertyKey.fromString("join"));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (joinFn instanceof JSFunction fn) {
                JSValue result = fn.call(context, thisArg, JSValue.NO_ARGS);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                return result;
            }
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

    /**
     * Validate the receiver and buffer before checking the callback, as required by the typed-array methods. Callers
     * capture the iteration length after this validation and retain it across callback side effects.
     */
    private static JSTypedArray toTypedArrayForCallback(JSContext context, JSValue thisArg, JSValue[] args,
            String methodName) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, methodName);
        if (typedArray == null) {
            return null;
        }
        if (args.length == 0 || !JSTypeChecking.isCallable(args[0])) {
            context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
            return null;
        }
        return typedArray;
    }

    /**
     * TypedArraySpeciesCreate per ES2024 23.2.4.1. Creates a new TypedArray using the species constructor pattern.
     * Includes TypedArrayCreate length validation (step 3a).
     */
    private static JSValue typedArraySpeciesCreate(JSContext context, JSTypedArray exemplar, JSValue[] args) {
        JSValue constructor = speciesConstructor(context, exemplar, true);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSValue result = constructTypedArray(context, constructor, args);
        if (!(result instanceof JSTypedArray resultArray) || context.hasPendingException()) {
            return result;
        }

        // TypedArrayCreate step 3: If argumentList is a single Number,
        // check that newTypedArray's length >= that number
        if (args.length == 1 && args[0] instanceof JSNumber) {
            long requestedLength = (long) ((JSNumber) args[0]).value();
            if (resultArray.getLength() < requestedLength) {
                return context.throwTypeError("TypedArray species constructor returned an array that is too small");
            }
        }

        return result;
    }

    private static JSTypedArray validateTypedArray(JSContext context, JSValue thisArg, String methodName) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, methodName);
        if (typedArray == null) {
            return null;
        }
        if (typedArray.isOutOfBounds()) {
            context.throwTypeError(
                    "Cannot perform " + methodName + " on a typed array backed by a detached or out-of-bounds buffer");
            return null;
        }
        return typedArray;
    }

    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        return createIterator(context, thisArg, "TypedArray.prototype.values", IteratorKind.VALUES);
    }

    public static JSValue withMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = validateTypedArray(context, thisArg, "TypedArray.prototype.with");
        if (typedArray == null) {
            return context.getPendingException();
        }
        // Step 3: Snapshot length
        long length = typedArray.getLength();

        // Step 4: ToIntegerOrInfinity(index)
        double relativeIndex = args.length > 0 ? JSTypeConversions.toInteger(context, args[0]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // Steps 5-6: Compute actualIndex using ORIGINAL length
        long actualIndex;
        if (relativeIndex >= 0) {
            actualIndex = (long) relativeIndex;
        } else {
            actualIndex = (long) (length + relativeIndex);
        }

        // Steps 7-8: Convert value (may resize/detach buffer)
        JSValue value = JSValue.argumentOrUndefined(args, 1);
        JSValue convertedValue;
        if (isBigIntTypedArray(typedArray)) {
            convertedValue = JSTypeConversions.toBigInt(context, value);
        } else {
            convertedValue = JSTypeConversions.toNumber(context, value);
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 9: After value conversion, check if buffer was detached/OOB or index is out of range.
        // Following QuickJS: combined OOB + index check throws RangeError.
        if (typedArray.isOutOfBounds() || actualIndex < 0 || actualIndex >= typedArray.getLength()) {
            return context.throwRangeError("Invalid typed array index");
        }

        // Step 10: TypedArrayCreateSameType uses ORIGINAL length (not currentLength)
        JSTypedArray resultArray = createSameType(context, typedArray, length);
        if (resultArray == null) {
            return context.getPendingException();
        }

        // Steps 11-12: Copy elements using original length
        for (long k = 0; k < length; k++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (k == actualIndex) {
                resultArray.set(PropertyKey.fromIndex(k), convertedValue);
            } else {
                JSValue fromValue = safeGetElement(typedArray, k);
                resultArray.set(PropertyKey.fromIndex(k), fromValue);
            }
        }
        return resultArray;
    }

    private enum IteratorKind {
        ENTRIES, KEYS, VALUES
    }
}
