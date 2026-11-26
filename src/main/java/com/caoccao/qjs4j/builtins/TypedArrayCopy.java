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

/** Typed array copying and mutation, including resize and overlapping-buffer handling. */
final class TypedArrayCopy {
    private TypedArrayCopy() {
    }

    /**
     * Transfer elements after length and type validation. Slice observes forward writes on an alias; set must read the
     * original source.
     */
    private static boolean copyElements(JSContext context, JSTypedArray source, long start, JSTypedArray target,
            long targetIndex, long count, boolean snapshot) {
        if (source.getClass() == target.getClass()) {
            int width = source.getBytesPerElement();
            JSByteBuffer src = source.getBuffer().getBuffer();
            JSByteBuffer dst = target.getBuffer().getBuffer();
            long from = source.getByteOffset() + start * width;
            long to = target.getByteOffset() + targetIndex * width;
            long bytes = count * width;
            if (!snapshot && source.getBuffer() == target.getBuffer() && to > from && to - from < bytes) {
                for (long i = 0; i < bytes; i++) {
                    context.getVirtualMachine().checkExecutionInterruptForNative();
                    dst.put(to + i, src.get(from + i));
                }
            } else {
                src.copyTo(dst, from, to, bytes);
            }
            return true;
        }
        if (snapshot && source.getBuffer() == target.getBuffer()) {
            source = source.copyStorage();
        }
        for (long i = 0; i < count; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            target.set(PropertyKey.fromIndex(targetIndex + i), source.getJSElement(start + i));
            if (context.hasPendingException()) {
                return false;
            }
        }
        return true;
    }

    static JSValue copyWithin(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = TypedArrayPrototype.validateTypedArray(context, thisArg,
                "TypedArray.prototype.copyWithin");
        if (typedArray == null) {
            return context.getPendingException();
        }
        // Step 3: Capture length before coercion
        long length = typedArray.getLength();

        ArrayIndexRange.Copy copy = ArrayIndexRange.Copy.read(context, args, length);
        if (copy == null) {
            return context.getPendingException();
        }
        long count = copy.count();
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
        long target = Math.min(copy.target(), currentLength);
        long start = Math.min(copy.start(), currentLength);
        count = Math.min(count, currentLength - target);
        count = Math.min(count, currentLength - start);

        if (count <= 0) {
            return typedArray;
        }

        copyElements(context, typedArray, start, typedArray, target, count, true);
        return typedArray;
    }

    private static JSValue copyWithReplacement(JSContext context, JSTypedArray source, long length, boolean reversed,
            long replacedIndex, JSValue replacement) {
        JSTypedArray target = TypedArrayPrototype.createSameType(context, source, length);
        if (target == null) {
            return context.getPendingException();
        }
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue value = i == replacedIndex
                    ? replacement
                    : source.getElementOrUndefined(reversed ? length - 1 - i : i);
            target.set(PropertyKey.fromIndex(i), value);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return target;
    }

    static JSValue fill(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = TypedArrayPrototype.validateTypedArray(context, thisArg, "TypedArray.prototype.fill");
        if (typedArray == null) {
            return context.getPendingException();
        }
        JSValue rawValue = JSValue.argumentOrUndefined(args, 0);

        // Step 3: Capture length BEFORE value conversion (per spec order)
        long length = typedArray.getLength();

        // Step 4/5: Convert value ONCE before coercing start/end
        JSValue convertedValue = TypedArrayPrototype.convertElement(context,
                TypedArrayPrototype.isBigIntTypedArray(typedArray), rawValue);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        ArrayIndexRange range = ArrayIndexRange.read(context, args, 1, length);
        if (range == null) {
            return context.getPendingException();
        }
        long start = range.start();
        long end = range.end();

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

    static JSValue reverse(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = TypedArrayPrototype.validateTypedArray(context, thisArg,
                "TypedArray.prototype.reverse");
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

    static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray target = TypedArrayPrototype.toTypedArray(context, thisArg, "TypedArray.prototype.set");
        if (target == null) {
            return context.getPendingException();
        }
        JSValue source = JSValue.argumentOrUndefined(args, 0);

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

        if (source instanceof JSTypedArray srcTypedArray) {
            return setFromTypedArray(context, target, srcTypedArray, targetOffset, targetLength);
        } else {
            return setFromArrayLike(context, target, source, targetOffset, targetLength);
        }
    }

    private static JSValue setFromArrayLike(JSContext context, JSTypedArray target, JSValue source, long targetOffset,
            long targetLength) {
        // Step 7: ToObject(source)
        JSObject src = JSTypeConversions.toObject(context, source);
        if (src == null || context.hasPendingException()) {
            return context.hasPendingException()
                    ? context.getPendingException()
                    : context.throwTypeError("Cannot convert source to object");
        }

        // Step 8: Get source length
        long srcLength = ArrayPrototype.lengthOfArrayLike(context, src);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 9: Range check
        if (targetOffset > targetLength || srcLength > targetLength - targetOffset) {
            return context.throwRangeError("Source is too large");
        }

        boolean isBigInt = TypedArrayPrototype.isBigIntTypedArray(target);

        // Step 11: Loop - convert values and write
        for (long k = 0; k < srcLength; k++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey pk = PropertyKey.fromIndexString(k);
            JSValue value = src.get(pk);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            // Convert value (may cause side effects including detaching/resizing buffer)
            JSValue convertedValue = TypedArrayPrototype.convertElement(context, isBigInt, value);
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
            long targetOffset, long targetLength) {
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
        boolean targetIsBigInt = TypedArrayPrototype.isBigIntTypedArray(target);
        boolean sourceIsBigInt = TypedArrayPrototype.isBigIntTypedArray(source);
        if (targetIsBigInt != sourceIsBigInt) {
            return context.throwTypeError("Cannot mix BigInt and non-BigInt typed arrays");
        }

        return copyElements(context, source, 0, target, targetOffset, srcLength, true)
                ? JSUndefined.INSTANCE
                : context.getPendingException();
    }

    static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = TypedArrayPrototype.validateTypedArray(context, thisArg,
                "TypedArray.prototype.slice");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();

        ArrayIndexRange range = ArrayIndexRange.read(context, args, 0, length);
        if (range == null) {
            return context.getPendingException();
        }

        long count = range.count();

        // TypedArraySpeciesCreate
        JSTypedArray resultArray = TypedArrayPrototype.speciesOfLength(context, typedArray, count);
        if (resultArray == null) {
            return context.getPendingException();
        }

        if (count > 0) {
            if (typedArray.isOutOfBounds()) {
                return context.throwTypeError(
                        "Cannot perform TypedArray.prototype.slice on a typed array backed by a detached or out-of-bounds buffer");
            }
            // Re-read length after species constructor may have resized (step 14c)
            long currentLength = typedArray.getLength();
            long newEnd = Math.min(range.end(), currentLength);
            long srcCount = Math.max(0, newEnd - range.start());

            if (srcCount > 0 && !resultArray.getBuffer().isDetached()
                    && !copyElements(context, typedArray, range.start(), resultArray, 0, srcCount, false)) {
                return context.getPendingException();
            }
        }
        return resultArray;
    }

    static JSValue subarray(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = TypedArrayPrototype.toTypedArray(context, thisArg, "TypedArray.prototype.subarray");
        if (typedArray == null) {
            return context.getPendingException();
        }

        // Step 5-7: Get srcLength (0 if OOB)
        long srcLength = typedArray.getLength();

        ArrayIndexRange range = ArrayIndexRange.read(context, args, 0, srcLength);
        if (range == null) {
            return context.getPendingException();
        }
        boolean endIsUndefined = args.length <= 1 || args[1].isUndefined();

        // Step 13-14: Compute beginByteOffset using stored byteOffset (preserved even when OOB)
        int elementSize = typedArray.getBytesPerElement();
        long srcByteOffset = typedArray.getByteOffset();
        long beginByteOffset = srcByteOffset + range.start() * elementSize;

        // SpeciesConstructor(O, defaultConstructor)
        IJSArrayBuffer buffer = typedArray.getBuffer();
        JSValue chosenConstructor = TypedArrayPrototype.speciesConstructor(context, typedArray, false);
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
            long newLength = range.count();
            constructArgs = new JSValue[]{bufferArg, JSNumber.of(beginByteOffset), JSNumber.of(newLength)};
        }

        return TypedArrayPrototype.constructTypedArray(context, chosenConstructor, constructArgs);
    }

    static JSValue toReversed(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = TypedArrayPrototype.validateTypedArray(context, thisArg,
                "TypedArray.prototype.toReversed");
        if (typedArray == null) {
            return context.getPendingException();
        }
        long length = typedArray.getLength();

        // Create new typed array of same type (not species - ignores species per spec)
        return copyWithReplacement(context, typedArray, length, true, -1, JSUndefined.INSTANCE);
    }

    static JSValue withMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = TypedArrayPrototype.validateTypedArray(context, thisArg, "TypedArray.prototype.with");
        if (typedArray == null) {
            return context.getPendingException();
        }
        // Step 3: Snapshot length
        long length = typedArray.getLength();

        // Step 4: ToIntegerOrInfinity(index)
        long actualIndex = ArrayIndexRange.elementIndex(context, args, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Steps 7-8: Convert value (may resize/detach buffer)
        JSValue value = JSValue.argumentOrUndefined(args, 1);
        JSValue convertedValue = TypedArrayPrototype.convertElement(context,
                TypedArrayPrototype.isBigIntTypedArray(typedArray), value);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 9: After value conversion, check if buffer was detached/OOB or index is out of range.
        // Following QuickJS: combined OOB + index check throws RangeError.
        if (typedArray.isOutOfBounds() || actualIndex < 0 || actualIndex >= typedArray.getLength()) {
            return context.throwRangeError("Invalid typed array index");
        }

        // Step 10: TypedArrayCreateSameType uses ORIGINAL length (not currentLength)
        return copyWithReplacement(context, typedArray, length, false, actualIndex, convertedValue);
    }
}
