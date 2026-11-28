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
import com.caoccao.qjs4j.core.buffer.JSInt16Array;
import com.caoccao.qjs4j.core.buffer.JSTypedArray;
import com.caoccao.qjs4j.core.buffer.JSUint16Array;

import java.util.*;

/** Stable sorting that accepts arbitrary JavaScript comparators and stops on abrupt completion. */
final class SortOperations {
    private SortOperations() {
    }

    static int compareArrayElements(JSContext context, JSValue comparator, JSValue left, JSValue right) {
        if (left instanceof JSUndefined) {
            return right instanceof JSUndefined ? 0 : 1;
        }
        if (right instanceof JSUndefined) {
            return -1;
        }
        if (!(comparator instanceof JSUndefined)) {
            return compareWithCallback(context, comparator, left, right);
        }
        String leftString = JSTypeConversions.toString(context, left).value();
        if (context.hasPendingException()) {
            return 0;
        }
        String rightString = JSTypeConversions.toString(context, right).value();
        return leftString.compareTo(rightString);
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

    static int compareWithCallback(JSContext context, JSValue comparator, JSValue left, JSValue right) {
        JSValue result = JSCall.call(context, comparator, JSUndefined.INSTANCE, left, right);
        if (context.hasPendingException()) {
            return 0;
        }
        double number = JSTypeConversions.toNumber(context, result).value();
        return Double.isNaN(number) || number == 0 ? 0 : number < 0 ? -1 : 1;
    }

    private static boolean countingSort16BitIntegerArray(JSContext context, JSTypedArray typedArray,
            JSTypedArray target, long length) {
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
            context.checkExecutionInterrupt();
            counts[(int) typedArray.getElement(i) + valueOffset]++;
        }
        long outputIndex = 0;
        for (int countIndex = 0; countIndex < counts.length; countIndex++) {
            context.checkExecutionInterrupt();
            long count = counts[countIndex];
            double value = countIndex - valueOffset;
            for (long i = 0; i < count; i++) {
                context.checkExecutionInterrupt();
                target.setElement(outputIndex++, value);
            }
        }
        return true;
    }

    private static int requireWorkspaceLength(JSContext context, long length) {
        if (length > Integer.MAX_VALUE - 8L) {
            throw new com.caoccao.qjs4j.exceptions.JSException(context.throwRangeError("Invalid array length"));
        }
        return (int) length;
    }

    static JSValue sortArray(JSContext context, JSValue thisArg, JSValue[] args, boolean copy) {
        // ES2024 23.1.3.30 step 1: validate comparefn before anything else
        JSValue compareFunction = JSValue.argumentOrUndefined(args, 0);
        if (!(compareFunction instanceof JSUndefined) && !JSTypeChecking.isCallable(compareFunction)) {
            return context.throwTypeError("The comparison function must be either a function or undefined: "
                    + (compareFunction instanceof JSObject ? "#<Object>" : compareFunction.toString()));
        }

        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, copy ? "toSorted" : "sort");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        if (copy && length > 0xFFFF_FFFFL) {
            return context.throwRangeError("Invalid array length");
        }
        // sort skips holes. toSorted reads through them, materializing undefined.
        List<JSValue> sortableElements = new ArrayList<>();
        int undefinedCount = 0;
        for (long i = 0; i < length; i++) {
            context.checkExecutionInterrupt();
            JSValue value = copy
                    ? ArrayPrototype.getElement(context, obj, i)
                    : ArrayLikeElements.getPresent(context, obj, i);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (value == null) {
                continue;
            }
            if (!copy && value instanceof JSUndefined) {
                undefinedCount++;
                continue;
            }
            sortableElements.add(value);
        }

        // Phase 2: Sort only the defined, present elements.
        JSValue[] sorted = sortableElements.toArray(JSValue[]::new);
        if (!stableSort(context, sorted, (a, b) -> compareArrayElements(context, compareFunction, a, b))) {
            return context.getPendingException();
        }
        if (copy) {
            if (sorted.length <= JSArray.MAX_DENSE_SIZE) {
                return context.createJSArray(sorted, true);
            }
            JSArray result = context.createJSArray(0, sorted.length);
            for (JSValue value : sorted) {
                context.checkExecutionInterrupt();
                result.push(value);
            }
            return result;
        }

        // Phase 3: Write back sorted elements, then undefined values, then delete holes.
        long writeIndex = 0;

        // Write sorted elements
        for (JSValue value : sorted) {
            context.checkExecutionInterrupt();
            PropertyKey key = PropertyKey.fromIndexString(writeIndex);
            if (!ArrayCopy.setOrThrow(context, obj, key, value)) {
                return context.getPendingException();
            }
            writeIndex++;
        }

        // Write undefined values after sorted elements
        for (int i = 0; i < undefinedCount; i++) {
            context.checkExecutionInterrupt();
            PropertyKey key = PropertyKey.fromIndexString(writeIndex);
            if (!ArrayCopy.setOrThrow(context, obj, key, JSUndefined.INSTANCE)) {
                return context.getPendingException();
            }
            writeIndex++;
        }

        // Delete remaining holes
        for (long i = writeIndex; i < length; i++) {
            context.checkExecutionInterrupt();
            PropertyKey key = PropertyKey.fromIndexString(i);
            if (!ArrayCopy.deleteOrThrow(context, obj, key)) {
                return context.getPendingException();
            }
        }

        return obj;
    }

    private static JSValue sortElements(JSContext context, JSTypedArray typedArray, JSTypedArray target, long length,
            JSValue compareArg) {
        // Collect elements into array
        JSValue[] elements = new JSValue[requireWorkspaceLength(context, length)];
        for (long i = 0; i < length; i++) {
            context.checkExecutionInterrupt();
            elements[(int) i] = typedArray.getJSElement(i);
        }

        // Sort
        if (!stableSort(context, elements,
                (a, b) -> compareArg instanceof JSUndefined
                        ? compareTypedArrayElements(a, b)
                        : compareWithCallback(context, compareArg, a, b))) {
            return context.getPendingException();
        }

        // Write back
        for (long i = 0; i < length; i++) {
            context.checkExecutionInterrupt();
            target.set(PropertyKey.fromIndex(i), elements[(int) i]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return target;
    }

    static JSValue sortTypedArray(JSContext context, JSTypedArray typedArray, JSTypedArray target, long length,
            JSValue compareArg) {
        // The default numeric ordering is exactly Java's primitive double ordering: NaN values
        // sort last and -0 sorts before +0. Keep BigInt and user callbacks on the JSValue path,
        // where their comparison semantics cannot be represented by a primitive double array.
        if (compareArg instanceof JSUndefined && !TypedArrayPrototype.isBigIntTypedArray(typedArray)) {
            if (countingSort16BitIntegerArray(context, typedArray, target, length)) {
                return target;
            }
            double[] elements = new double[requireWorkspaceLength(context, length)];
            for (long i = 0; i < length; i++) {
                context.checkExecutionInterrupt();
                elements[(int) i] = typedArray.getElement(i);
            }
            Arrays.sort(elements);
            for (long i = 0; i < length; i++) {
                context.checkExecutionInterrupt();
                target.setElement(i, elements[(int) i]);
            }
            return target;
        }

        return sortElements(context, typedArray, target, length, compareArg);
    }

    /** Bottom-up merge sort: bounded progress even when the comparison relation is inconsistent. */
    static boolean stableSort(JSContext context, JSValue[] values, Comparator<JSValue> comparator) {
        int length = values.length;
        if (length < 2) {
            return true;
        }
        JSValue[] source = values;
        JSValue[] destination = new JSValue[length];
        for (long width = 1; width < length; width *= 2) {
            for (long start = 0; start < length; start += width * 2) {
                int left = (int) start;
                int middle = (int) Math.min(start + width, length);
                int right = middle;
                int end = (int) Math.min(start + width * 2, length);
                for (int output = left; output < end; output++) {
                    context.checkExecutionInterrupt();
                    boolean takeLeft = right == end;
                    if (!takeLeft && left < middle) {
                        takeLeft = comparator.compare(source[left], source[right]) <= 0;
                        if (context.hasPendingException()) {
                            return false;
                        }
                    }
                    destination[output] = takeLeft ? source[left++] : source[right++];
                }
            }
            JSValue[] temporary = source;
            source = destination;
            destination = temporary;
        }
        if (source != values) {
            System.arraycopy(source, 0, values, 0, length);
        }
        return true;
    }
}
