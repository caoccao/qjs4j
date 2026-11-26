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
import java.util.List;
import java.util.Set;

/**
 * Array prototype methods. Implements ECMAScript Array.prototype methods.
 *
 * @see <a href="https://tc39.es/ecma262/#sec-properties-of-the-array-prototype-object">ECMAScript Array.prototype</a>
 */
public final class ArrayPrototype {

    /**
     * Array.prototype.at(index) ES2022 23.1.3.1 Returns the element at the specified index, supporting negative
     * indices. Generic: works on any object with a length property (per spec uses ToObject + LengthOfArrayLike).
     */
    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Let O be ? ToObject(this value).
        JSObject obj = JSTypeConversions.toObject(context, thisArg);
        if (obj == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 2: Let len be ? LengthOfArrayLike(O).
        long length = lengthOfArrayLike(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        // Step 3: Let relativeIndex be ? ToIntegerOrInfinity(index).
        long index = toIntegerIndex(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Steps 4-5: Handle negative indices
        if (index < 0) {
            index = length + index;
        }

        // Step 6: Check bounds
        if (index < 0 || index >= length) {
            return JSUndefined.INSTANCE;
        }

        // Step 7: Return ? Get(O, ! ToString(k)).
        return obj.get(PropertyKey.fromIndexString(index));
    }

    private static long callbackIterationLength(JSContext context, JSObject obj, JSValue[] args) {
        if (obj == null) {
            return -1;
        }
        // Even an empty array must read length before validating the callback.
        long length = lengthOfArrayLike(context, obj);
        if (context.hasPendingException()) {
            return -1;
        }
        if (args.length == 0 || !JSTypeChecking.isCallable(args[0])) {
            context.throwTypeError("Callback must be a function");
            return -1;
        }
        return length;
    }

    private static long checkedLength(JSContext context, JSObject receiver) {
        if (receiver == null) {
            return -1;
        }
        long length = lengthOfArrayLike(context, receiver);
        return context.hasPendingException() ? -1 : length;
    }

    /**
     * Array.prototype.concat(...items) Merges arrays and/or values.
     */
    public static JSValue concat(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES2024 23.1.3.1 Array.prototype.concat
        // Step 1: Let O be ? ToObject(this value)
        JSObject obj;
        if (thisArg instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, thisArg);
            if (obj == null || context.hasPendingException()) {
                return context.hasPendingException()
                        ? context.getPendingException()
                        : context.throwTypeError("Array.prototype.concat called on null or undefined");
            }
        }

        // Step 2: Let A be ? ArraySpeciesCreate(O, 0)
        JSObject resultObj = createSpeciesObject(context, obj, 0);
        if (resultObj == null) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }
        // Fast path flag: if result is a plain JSArray, use internal array ops
        JSArray resultArr = resultObj instanceof JSArray arr ? arr : null;
        long resultIndex = 0;

        // Steps 3-4: Process O (this) and each argument
        for (int i = -1; i < args.length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = (i < 0) ? obj : args[i];

            // Step 4a: Let spreadable be ? IsConcatSpreadable(E)
            boolean spreadable = isConcatSpreadable(context, element);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            if (spreadable) {
                // Step 4b: Spreadable case - iterate through elements
                JSObject elementObj = (JSObject) element;
                long elementLength = lengthOfArrayLike(context, elementObj);
                if (elementLength < 0) {
                    return context.getPendingException();
                }

                if (resultIndex + elementLength > NumberPrototype.MAX_SAFE_INTEGER) { // MAX_SAFE_INTEGER
                    return context.throwTypeError("Array too long");
                }

                for (long k = 0; k < elementLength; k++, resultIndex++) {

                    context.getVirtualMachine().checkExecutionInterruptForNative();
                    PropertyKey indexKey = PropertyKey.fromIndexString(k);
                    // JS_TryGetPropertyInt64: only define property if it exists (preserve holes)
                    boolean present = elementObj.has(indexKey);
                    if (context.hasPendingException()) {
                        return context.getPendingException();
                    }
                    if (present) {
                        JSValue val = elementObj.get(indexKey);
                        if (context.hasPendingException()) {
                            return context.getPendingException();
                        }
                        if (resultArr != null) {
                            resultArr.set(resultIndex, val);
                        } else if (!resultObj.defineProperty(PropertyKey.fromIndexString(resultIndex), val,
                                PropertyDescriptor.DataState.All)) {
                            return context.throwTypeError("Cannot define property " + resultIndex);
                        }
                    }
                }
            } else {
                // Step 4c: Non-spreadable - add as single element
                if (resultIndex >= NumberPrototype.MAX_SAFE_INTEGER) {
                    return context.throwTypeError("Array too long");
                }
                if (resultArr != null) {
                    resultArr.set(resultIndex, element);
                } else if (!resultObj.defineProperty(PropertyKey.fromIndexString(resultIndex), element,
                        PropertyDescriptor.DataState.All)) {
                    return context.throwTypeError("Cannot define property " + resultIndex);
                }
                resultIndex++;
            }
        }

        // Step 5: Set length
        if (resultArr != null) {
            resultArr.setLength(resultIndex);
        } else {
            resultObj.set(PropertyKey.LENGTH, JSNumber.of(resultIndex));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return resultObj;
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

        // Small results fit entirely in JSArray's dense storage. Build that storage directly so
        // every copied value is still an own data property, including undefined values copied
        // from source holes, without repeatedly growing the array or walking its prototype.
        if (newLen <= JSArray.MAX_DENSE_SIZE) {
            JSValue[] elements = new JSValue[(int) newLen];
            int resultIndex = 0;
            for (long i = 0; i < start; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                elements[resultIndex++] = getElement(context, obj, i);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
            for (int i = 2; i < args.length; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                elements[resultIndex++] = args[i];
            }
            for (long i = start + deleteCount; i < length; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                elements[resultIndex++] = getElement(context, obj, i);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
            return context.createJSArray(elements, true);
        }

        // Create a sparse-capable result for lengths beyond dense storage.
        JSArray result = context.createJSArray(0, (int) newLen);

        // Copy elements before start
        for (long i = 0; i < start; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            result.push(getElement(context, obj, i));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }

        // Insert new elements
        for (int i = 2; i < args.length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            result.push(args[i]);
        }

        // Copy elements after deleted portion
        for (long i = start + deleteCount; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            result.push(getElement(context, obj, i));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }

        return result;
    }

    /**
     * Array.prototype.copyWithin(target, start[, end]) ES2015 23.1.3.3 Copies a sequence of array elements within the
     * array.
     */
    public static JSValue copyWithin(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Let O be ? ToObject(this value).
        JSObject obj = JSTypeConversions.toObject(context, thisArg);
        if (obj == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 2: Let len be ? LengthOfArrayLike(O).
        long length = lengthOfArrayLike(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        long target = relativeIndex(context, args, 0, length, 0);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long start = relativeIndex(context, args, 1, length, 0);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long end = relativeIndex(context, args, 2, length, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Calculate count
        long count = Math.min(end - start, length - target);
        if (count <= 0) {
            return obj;
        }

        // Copy direction depends on overlap (per spec step 12)
        int dir = (start < target && target < start + count) ? -1 : 1;
        for (long i = 0; i < count; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            final long from = dir < 0 ? (start + count - i - 1) : (start + i);
            final long to = dir < 0 ? (target + count - i - 1) : (target + i);
            final PropertyKey fromKey = PropertyKey.fromIndexString(from);
            final PropertyKey toKey = PropertyKey.fromIndexString(to);

            if (!copyOrDeleteElement(context, obj, fromKey, toKey)) {
                return context.getPendingException();
            }
        }

        return obj;
    }

    private static JSObject createSpeciesObject(JSContext context, JSObject source, long length) {
        JSValue result = context.createJSArraySpecies(source, length);
        if (result == null || context.hasPendingException()) {
            return null;
        }
        if (result instanceof JSObject object) {
            return object;
        }
        context.throwTypeError("ArraySpeciesCreate did not return an object");
        return null;
    }

    /**
     * Array.prototype[Symbol.unscopables] ES2015 23.1.3.32 Returns an object containing property names that are
     * excluded from with statement binding. These are methods added in ES2015 and later that should not be included in
     * with statements.
     */
    public static JSObject createUnscopablesObject(JSContext context) {
        JSObject unscopables = context.createJSObject();
        unscopables.setPrototype(null);

        // ES2022+ methods
        unscopables.set("at", JSBoolean.TRUE);

        // ES2015 methods
        unscopables.set("copyWithin", JSBoolean.TRUE);
        unscopables.set("entries", JSBoolean.TRUE);
        unscopables.set("fill", JSBoolean.TRUE);
        unscopables.set("find", JSBoolean.TRUE);
        unscopables.set("findIndex", JSBoolean.TRUE);
        unscopables.set("findLast", JSBoolean.TRUE);
        unscopables.set("findLastIndex", JSBoolean.TRUE);
        unscopables.set("flat", JSBoolean.TRUE);
        unscopables.set("flatMap", JSBoolean.TRUE);
        unscopables.set("includes", JSBoolean.TRUE);
        unscopables.set("keys", JSBoolean.TRUE);
        unscopables.set("toReversed", JSBoolean.TRUE);
        unscopables.set("toSorted", JSBoolean.TRUE);
        unscopables.set("toSpliced", JSBoolean.TRUE);
        unscopables.set("values", JSBoolean.TRUE);

        return unscopables;
    }

    /**
     * ES2024 DeletePropertyOrThrow(O, P) — throws TypeError if delete returns false.
     */
    private static boolean deleteOrThrow(JSContext context, JSObject obj, PropertyKey key) {
        if (!obj.delete(key)) {
            if (!context.hasPendingException()) {
                context.throwTypeError("Cannot delete property '" + key.toPropertyString() + "'");
            }
            return false;
        }
        return !context.hasPendingException();
    }

    /**
     * Array.prototype.every(callbackFn[, thisArg]) Tests whether all elements pass the test.
     */
    public static JSValue every(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterate(context, thisArg, args, ArrayIteration.EVERY);
    }

    /**
     * Array.prototype.fill(value[, start[, end]]) ES2015 23.1.3.6 Fills all the elements of an array from a start index
     * to an end index with a static value.
     */
    public static JSValue fill(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Let O be ? ToObject(this value).
        JSObject obj = toObjectChecked(context, thisArg);
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        JSValue value = JSValue.argumentOrUndefined(args, 0);

        long start = relativeIndex(context, args, 1, length, 0);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long end = relativeIndex(context, args, 2, length, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 8: Repeat, while k < final
        for (long i = start; i < end; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            if (!setOrThrow(context, obj, key, value)) {
                return context.getPendingException();
            }
        }

        // Step 9: Return O.
        return obj;
    }

    /**
     * Array.prototype.filter(callbackFn[, thisArg]) Creates a new array with elements that pass the test.
     */
    public static JSValue filter(JSContext context, JSValue thisArg, JSValue[] args) {
        return mapOrFilter(context, thisArg, args, true);
    }

    /**
     * Array.prototype.find(callbackFn[, thisArg]) Returns the first element that satisfies the test.
     */
    public static JSValue find(JSContext context, JSValue thisArg, JSValue[] args) {
        return findMatchingElement(context, thisArg, args, "find", false, false);
    }

    /**
     * Array.prototype.findIndex(callbackFn[, thisArg]) Returns the index of the first element that satisfies the test.
     */
    public static JSValue findIndex(JSContext context, JSValue thisArg, JSValue[] args) {
        return findMatchingElement(context, thisArg, args, "findIndex", false, true);
    }

    /**
     * Array.prototype.findLast(callbackFn[, thisArg]) ES2023 23.1.3.13 Returns the last element that satisfies the test
     * (iterates backwards).
     */
    public static JSValue findLast(JSContext context, JSValue thisArg, JSValue[] args) {
        return findMatchingElement(context, thisArg, args, "findLast", true, false);
    }

    /**
     * Array.prototype.findLastIndex(callbackFn[, thisArg]) ES2023 23.1.3.14 Returns the index of the last element that
     * satisfies the test (iterates backwards).
     */
    public static JSValue findLastIndex(JSContext context, JSValue thisArg, JSValue[] args) {
        return findMatchingElement(context, thisArg, args, "findLastIndex", true, true);
    }

    private static JSValue findMatchingElement(JSContext context, JSValue thisArg, JSValue[] args, String methodName,
            boolean fromEnd, boolean returnIndex) {
        JSObject obj = requireArrayLike(context, thisArg, methodName);
        long length = callbackIterationLength(context, obj, args);
        if (length < 0) {
            return context.getPendingException();
        }
        JSValue callback = args[0];
        JSValue callbackThis = JSValue.argumentOrUndefined(args, 1);
        long end = fromEnd ? -1 : length;
        long step = fromEnd ? -1 : 1;
        for (long i = fromEnd ? length - 1 : 0; i != end; i += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            // Find visits holes with Get, without the HasProperty used by every/some/reduce.
            JSValue element = obj.get(PropertyKey.fromIndexString(i));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            JSValue result = JSCall.call(context, callback, callbackThis, new JSValue[]{element, JSNumber.of(i), obj});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                return returnIndex ? JSNumber.of(i) : element;
            }
        }
        return returnIndex ? JSNumber.of(-1) : JSUndefined.INSTANCE;
    }

    /**
     * Array.prototype.flat([depth]) Creates a new array with all sub-array elements concatenated.
     */
    public static JSValue flat(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "flat");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        int depth;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            double doubleValue = JSTypeConversions.toInteger(context, args[0]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            depth = doubleValue >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(doubleValue, 0);
        } else {
            depth = 1;
        }

        // Step 4: Let A be ? ArraySpeciesCreate(O, 0).
        JSObject resultObj = createSpeciesObject(context, obj, 0);
        if (resultObj == null) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }

        // Step 5: Perform ? FlattenIntoArray(A, O, sourceLen, 0, depthNum).
        internalFlattenIntoObject(context, obj, length, depth, resultObj, 0, null, JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        return resultObj;
    }

    /**
     * Array.prototype.flatMap(callback, thisArg) ES2019 22.1.3.11 Maps each element using callback, then flattens the
     * result by one level.
     */
    public static JSValue flatMap(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "flatMap");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        if (args.length == 0 || !JSTypeChecking.isCallable(args[0])) {
            return context.throwTypeError("Array.prototype.flatMap requires a callback function");
        }
        JSValue callback = args[0];

        JSValue callbackThisArg = JSValue.argumentOrUndefined(args, 1);

        // Step 5: Let A be ? ArraySpeciesCreate(O, 0).
        JSObject resultObj = createSpeciesObject(context, obj, 0);
        if (resultObj == null) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }

        // Step 6: Perform ? FlattenIntoArray(A, O, sourceLen, 0, 1, mapperFunction, thisArg).
        internalFlattenIntoObject(context, obj, length, 1, resultObj, 0, callback, callbackThisArg);
        return context.hasPendingException() ? context.getPendingException() : resultObj;
    }

    /**
     * Array.prototype.forEach(callbackFn[, thisArg]) Executes a function for each array element.
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterate(context, thisArg, args, ArrayIteration.FOR_EACH);
    }

    /**
     * Get element at index from an array-like object. Uses JSArray fast path for actual arrays.
     */
    static JSValue getElement(JSContext context, JSObject obj, long index) {
        if (obj instanceof JSArray arr && index <= Integer.MAX_VALUE) {
            return arr.get((int) index);
        }
        return obj.get(PropertyKey.fromIndexString(index));
    }

    /**
     * get Array.prototype.length Returns the number of elements in the array. This is a getter for the length property.
     */
    public static JSValue getLength(JSContext context, JSValue thisArg, JSValue[] args) {
        long length = 0;
        if (thisArg instanceof JSArray jsArray) {
            length = jsArray.getLength();
        }
        return JSNumber.of(length);
    }

    /**
     * Array.prototype.includes(searchElement[, fromIndex]) Determines whether an array includes a certain element.
     */
    public static JSValue includes(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "includes");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        // Step 4: If len is 0, return false (before ToIntegerOrInfinity).
        if (length == 0) {
            return JSBoolean.FALSE;
        }

        JSValue searchElement = JSValue.argumentOrUndefined(args, 0);
        double fromIndexD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        if (fromIndexD >= length) {
            return JSBoolean.FALSE;
        }
        long fromIndex;
        if (fromIndexD < 0) {
            fromIndex = Math.max(0, length + (long) fromIndexD);
        } else {
            fromIndex = (long) fromIndexD;
        }

        if (obj instanceof JSArray jsArray && fromIndex <= Integer.MAX_VALUE && length <= Integer.MAX_VALUE) {
            int startIndex = (int) fromIndex;
            int endIndex = (int) length;
            for (int index = startIndex; index < endIndex; index++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                JSValue element = jsArray.get(index);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (JSTypeConversions.strictEquals(element, searchElement)) {
                    return JSBoolean.TRUE;
                }
                if (element instanceof JSNumber elementNumber && searchElement instanceof JSNumber searchNumber) {
                    if (Double.isNaN(elementNumber.value()) && Double.isNaN(searchNumber.value())) {
                        return JSBoolean.TRUE;
                    }
                }
            }
            return JSBoolean.FALSE;
        }

        for (long i = fromIndex; i < length; i++) {

            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            JSValue element = obj.get(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            // includes uses SameValueZero (NaN equals NaN)
            if (JSTypeConversions.strictEquals(element, searchElement)) {
                return JSBoolean.TRUE;
            }
            // Special case for NaN
            if (element instanceof JSNumber en && searchElement instanceof JSNumber sn) {
                if (Double.isNaN(en.value()) && Double.isNaN(sn.value())) {
                    return JSBoolean.TRUE;
                }
            }
        }

        return JSBoolean.FALSE;
    }

    /**
     * Array.prototype.indexOf(searchElement[, fromIndex]) Returns the first index at which a given element can be
     * found.
     */
    public static JSValue indexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchIndex(context, thisArg, args, false);
    }

    private static long internalFlattenIntoObject(JSContext context, JSObject source, long sourceLen, int depth,
            JSObject target, long targetIndex, JSValue mapper, JSValue mapperThis) {
        for (long i = 0; i < sourceLen; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            if (!source.has(key)) {
                if (context.hasPendingException()) {
                    return targetIndex;
                }
                continue;
            }
            JSValue element = source.get(key);
            if (context.hasPendingException()) {
                return targetIndex;
            }
            if (mapper != null) {
                element = JSCall.call(context, mapper, mapperThis, new JSValue[]{element, JSNumber.of(i), source});
                if (context.hasPendingException()) {
                    return targetIndex;
                }
            }
            if (depth > 0 && element instanceof JSObject elementObj) {
                int isArr = JSTypeChecking.isArray(context, elementObj);
                if (isArr < 0) {
                    return targetIndex;
                }
                if (isArr > 0) {
                    long elementLen = lengthOfArrayLike(context, elementObj);
                    if (context.hasPendingException()) {
                        return targetIndex;
                    }
                    targetIndex = internalFlattenIntoObject(context, elementObj, elementLen, depth - 1, target,
                            targetIndex, null, JSUndefined.INSTANCE);
                    if (context.hasPendingException()) {
                        return targetIndex;
                    }
                    continue;
                }
            }
            if (!target.defineProperty(PropertyKey.fromIndexString(targetIndex), element,
                    PropertyDescriptor.DataState.All)) {
                context.throwTypeError("Cannot define property " + targetIndex);
                return targetIndex;
            }
            targetIndex++;
        }
        return targetIndex;
    }

    /**
     * IsConcatSpreadable per ES2024 23.1.3.1 step 4a. Following QuickJS JS_isConcatSpreadable.
     */
    private static boolean isConcatSpreadable(JSContext context, JSValue val) {
        if (!(val instanceof JSObject obj)) {
            return false;
        }
        JSValue spreadable = obj.get(PropertyKey.SYMBOL_IS_CONCAT_SPREADABLE);
        if (context.hasPendingException()) {
            return false;
        }
        if (!(spreadable instanceof JSUndefined)) {
            return JSTypeConversions.toBoolean(spreadable) == JSBoolean.TRUE;
        }
        int res = JSTypeChecking.isArray(context, obj);
        // res < 0 means exception (revoked proxy) — caller checks hasPendingException
        return res > 0;
    }

    private static JSValue iterate(JSContext context, JSValue thisArg, JSValue[] args, ArrayIteration iteration) {
        JSObject obj = requireArrayLike(context, thisArg, iteration.methodName);
        long length = callbackIterationLength(context, obj, args);
        if (length < 0) {
            return context.getPendingException();
        }
        JSValue callback = args[0];
        JSValue callbackThis = JSValue.argumentOrUndefined(args, 1);
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            boolean present = obj.has(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!present) {
                continue;
            }
            JSValue element = obj.get(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            JSValue result = JSCall.call(context, callback, callbackThis, new JSValue[]{element, JSNumber.of(i), obj});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (iteration.stopsAt(result)) {
                return iteration.stopResult;
            }
        }
        return iteration.completion;
    }

    /**
     * Array.prototype.join([separator]) Joins all elements of an array into a string.
     */
    public static JSValue join(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSNull || thisArg instanceof JSUndefined) {
            return context.throwTypeError("Array.prototype.join called on null or undefined");
        }
        // Step 1: Let O be ? ToObject(this value), which cannot fail past the check above. A primitive receiver used to
        // join to "".
        JSObject jsObject = JSTypeConversions.toObject(context, thisArg);
        // Following QuickJS js_array_join: read length first, then coerce separator
        long length = jsObject instanceof JSArray jsArray
                ? jsArray.getLength()
                : JSTypeConversions.toLength(context, jsObject.get(PropertyKey.LENGTH));
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSString separator = args.length > 0 && !(args[0] instanceof JSUndefined)
                ? JSTypeConversions.toString(context, args[0])
                : new JSString(",");
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // An array already being joined further up — a cycle — renders as "", as in V8. The recursion used to
        // continue until the stack overflowed. As in V8's CycleProtectedArrayJoin, the array is registered only once
        // its length and separator are read, so a separator whose toString joins the same array still joins it.
        Set<JSObject> joinStack = context.getArrayJoinStack();
        if (!joinStack.add(jsObject)) {
            return new JSString("");
        }
        try {
            StringResultBuilder result = new StringResultBuilder(context);
            for (long i = 0; i < length; i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                if (i > 0) {
                    result.append(separator);
                }
                JSValue element = jsObject instanceof JSArray jsArray
                        ? jsArray.get(i)
                        : jsObject.get(PropertyKey.fromIndexString(i));
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (!(element instanceof JSNull) && !(element instanceof JSUndefined)) {
                    JSString elementString = JSTypeConversions.toString(context, element);
                    if (context.hasPendingException()) {
                        return context.getPendingException();
                    }
                    result.append(elementString);
                }
                if (result.length() > JSString.MAX_LENGTH) {
                    return context.throwRangeError("Invalid string length");
                }
            }
            return result.build();
        } finally {
            joinStack.remove(jsObject);
        }
    }

    /**
     * Array.prototype.lastIndexOf(searchElement[, fromIndex]) Returns the last index at which a given element can be
     * found.
     */
    public static JSValue lastIndexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchIndex(context, thisArg, args, true);
    }

    /**
     * LengthOfArrayLike(obj) — returns the length of an array-like object. Uses JSArray.getLength() fast path for
     * actual arrays.
     */
    static long lengthOfArrayLike(JSContext context, JSObject obj) {
        if (obj instanceof JSArray arr) {
            return arr.getLength();
        }
        JSValue lenVal = obj.get(PropertyKey.LENGTH);
        if (context.hasPendingException()) {
            return -1;
        }
        long arrayLikeLength = JSTypeConversions.toLength(context, lenVal);
        if (context.hasPendingException()) {
            return -1;
        }
        return arrayLikeLength;
    }

    /**
     * Array.prototype.map(callbackFn[, thisArg]) Creates a new array with the results of calling a function on every
     * element.
     */
    public static JSValue map(JSContext context, JSValue thisArg, JSValue[] args) {
        return mapOrFilter(context, thisArg, args, false);
    }

    private static JSValue mapOrFilter(JSContext context, JSValue thisArg, JSValue[] args, boolean filter) {
        JSObject obj = requireArrayLike(context, thisArg, filter ? "filter" : "map");
        long length = callbackIterationLength(context, obj, args);
        if (length < 0) {
            return context.getPendingException();
        }
        JSValue callback = args[0];

        JSValue callbackThis = JSValue.argumentOrUndefined(args, 1);

        // Step 5: Let A be ? ArraySpeciesCreate(O, len).
        JSObject resultObj = createSpeciesObject(context, obj, filter ? 0 : length);
        if (resultObj == null) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }

        long resultIndex = 0;
        for (long i = 0; i < length; i++) {

            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            if (!obj.has(key)) {
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                continue;
            }
            JSValue element = obj.get(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            JSValue[] callbackArgs = {element, JSNumber.of(i), obj};
            JSValue mapped = JSCall.call(context, callback, callbackThis, callbackArgs);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (filter) {
                if (JSTypeConversions.toBoolean(mapped) != JSBoolean.TRUE) {
                    continue;
                }
                key = PropertyKey.fromIndexString(resultIndex++);
                mapped = element;
            }
            if (!resultObj.defineProperty(key, mapped, PropertyDescriptor.DataState.All)) {
                return context.throwTypeError("Cannot define property " + key.toPropertyString());
            }
        }
        return resultObj;
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

    /**
     * Array.prototype.pop() Removes and returns the last element of an array.
     */
    public static JSValue pop(JSContext context, JSValue thisArg, JSValue[] args) {
        return removeEndElement(context, thisArg, false);
    }

    /**
     * Array.prototype.push(...items) Appends elements to the end of an array.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-array.prototype.push">ECMAScript Array.prototype.push</a>
     */
    public static JSValue push(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "push");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        // Per ES2024 step 4: If len + argCount > 2^53 - 1, throw a TypeError
        long newLen = length + args.length;
        if (newLen > NumberPrototype.MAX_SAFE_INTEGER) {
            return context.throwTypeError("Array too long");
        }

        for (int i = 0; i < args.length; i++) {

            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(length);
            if (!setOrThrow(context, obj, key, args[i])) {
                return context.getPendingException();
            }
            length++;
        }

        if (!setOrThrow(context, obj, PropertyKey.LENGTH, JSNumber.of(length))) {
            return context.getPendingException();
        }
        return JSNumber.of(length);
    }

    /**
     * Array.prototype.reduce(callbackFn[, initialValue]) Reduces array to a single value by calling a function on each
     * element.
     */
    public static JSValue reduce(JSContext context, JSValue thisArg, JSValue[] args) {
        return reduceElements(context, thisArg, args, false);
    }

    private static JSValue reduceElements(JSContext context, JSValue thisArg, JSValue[] args, boolean fromEnd) {
        String methodName = fromEnd ? "reduceRight" : "reduce";
        JSObject obj = requireArrayLike(context, thisArg, methodName);
        long length = callbackIterationLength(context, obj, args);
        if (length < 0) {
            return context.getPendingException();
        }
        JSValue callback = args[0];
        // An explicit undefined initial value is different from omitting the argument.
        boolean hasAccumulator = args.length >= 2;
        JSValue accumulator = hasAccumulator ? args[1] : JSUndefined.INSTANCE;
        long end = fromEnd ? -1 : length;
        long step = fromEnd ? -1 : 1;
        for (long i = fromEnd ? length - 1 : 0; i != end; i += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            boolean present = obj.has(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!present) {
                continue;
            }
            JSValue element = obj.get(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!hasAccumulator) {
                accumulator = element;
                hasAccumulator = true;
            } else {
                accumulator = JSCall.call(context, callback, JSUndefined.INSTANCE,
                        new JSValue[]{accumulator, element, JSNumber.of(i), obj});
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        }
        if (!hasAccumulator) {
            return context.throwTypeError("Reduce of empty array with no initial value");
        }
        return accumulator;
    }

    /**
     * Array.prototype.reduceRight(callbackFn[, initialValue]) Reduces array from right to left.
     */
    public static JSValue reduceRight(JSContext context, JSValue thisArg, JSValue[] args) {
        return reduceElements(context, thisArg, args, true);
    }

    static long relativeIndex(JSContext context, JSValue[] args, int index, long length, long defaultValue) {
        if (args.length <= index || args[index] instanceof JSUndefined) {
            return defaultValue;
        }
        long value = toIntegerIndex(context, args[index]);
        return value < 0 ? Math.max(length + value, 0) : Math.min(value, length);
    }

    private static JSValue removeEndElement(JSContext context, JSValue thisArg, boolean first) {
        JSObject obj = requireArrayLike(context, thisArg, first ? "shift" : "pop");
        long length = checkedLength(context, obj);
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
        if (first) {
            for (long k = 1; k < length; k++) {

                context.getVirtualMachine().checkExecutionInterruptForNative();
                PropertyKey from = PropertyKey.fromIndexString(k);
                PropertyKey to = PropertyKey.fromIndexString(k - 1);
                if (!copyOrDeleteElement(context, obj, from, to)) {
                    return context.getPendingException();
                }
            }
        }
        if (!deleteOrThrow(context, obj, PropertyKey.fromIndexString(length - 1))) {
            return context.getPendingException();
        }
        if (!setOrThrow(context, obj, PropertyKey.LENGTH, JSNumber.of(length - 1))) {
            return context.getPendingException();
        }
        return removed;
    }

    private static JSObject requireArrayLike(JSContext context, JSValue receiver, String method) {
        if (receiver instanceof JSNull || receiver instanceof JSUndefined) {
            context.throwTypeError("Array.prototype." + method + " called on null or undefined");
            return null;
        }
        return JSTypeConversions.toObject(context, receiver);
    }

    /**
     * Array.prototype.reverse() Reverses the elements of an array in place.
     */
    public static JSValue reverse(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "reverse");
        long length = checkedLength(context, obj);
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

    private static JSValue searchIndex(JSContext context, JSValue thisArg, JSValue[] args, boolean fromEnd) {
        JSObject obj = requireArrayLike(context, thisArg, fromEnd ? "lastIndexOf" : "indexOf");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        if (length == 0) {
            return JSNumber.of(-1);
        }

        JSValue searchElement = JSValue.argumentOrUndefined(args, 0);
        double fromIndexD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : fromEnd ? length - 1 : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long start = searchStart(fromIndexD, length, fromEnd);
        long end = fromEnd ? -1 : length;
        int step = fromEnd ? -1 : 1;
        for (long i = start; i != end; i += step) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            if (!obj.has(key)) {
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                continue;
            }
            JSValue element = obj.get(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.strictEquals(element, searchElement)) {
                return JSNumber.of(i);
            }
        }

        return JSNumber.of(-1);
    }

    static long searchStart(double fromIndex, long length, boolean fromEnd) {
        long index = (long) fromIndex;
        if (index < 0) {
            index += length;
        }
        return fromEnd ? Math.max(-1L, Math.min(index, length - 1)) : Math.max(0L, Math.min(index, length));
    }

    /**
     * ES2024 Set(O, P, V, true) — always throws TypeError on failure regardless of strict mode. Generates V8-matching
     * error messages based on the failure reason.
     */
    private static boolean setOrThrow(JSContext context, JSObject obj, PropertyKey key, JSValue value) {
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
     * Array.prototype.shift() Removes and returns the first element of an array.
     */
    public static JSValue shift(JSContext context, JSValue thisArg, JSValue[] args) {
        return removeEndElement(context, thisArg, true);
    }

    /**
     * Array.prototype.slice([begin[, end]]) Returns a shallow copy of a portion of an array.
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "slice");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        long begin = relativeIndex(context, args, 0, length, 0);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        long end = relativeIndex(context, args, 1, length, length);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long count = Math.max(end - begin, 0);

        // Step 7: Let A be ? ArraySpeciesCreate(O, count).
        JSObject resultObj = createSpeciesObject(context, obj, count);
        if (resultObj == null) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }

        long resultIndex = 0;
        for (long i = begin; i < end; i++, resultIndex++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            boolean kPresent = obj.has(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (kPresent) {
                JSValue element = obj.get(key);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                // Step 8.b.iii: Perform ? CreateDataPropertyOrThrow(A, ! ToString(resultIndex), kValue).
                if (!resultObj.defineProperty(PropertyKey.fromIndexString(resultIndex), element,
                        PropertyDescriptor.DataState.All)) {
                    return context.throwTypeError("Cannot define property " + resultIndex);
                }
            }
        }

        // Step 9: Perform ? Set(A, "length", resultIndex, true).
        resultObj.set(PropertyKey.LENGTH, JSNumber.of(resultIndex));
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        return resultObj;
    }

    /**
     * Array.prototype.some(callbackFn[, thisArg]) Tests whether at least one element passes the test.
     */
    public static JSValue some(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterate(context, thisArg, args, ArrayIteration.SOME);
    }

    /**
     * Array.prototype.sort([compareFn]) Sorts the elements of an array in place.
     */
    public static JSValue sort(JSContext context, JSValue thisArg, JSValue[] args) {
        return sortArray(context, thisArg, args, false);
    }

    private static JSValue sortArray(JSContext context, JSValue thisArg, JSValue[] args, boolean copy) {
        // ES2024 23.1.3.30 step 1: validate comparefn before anything else
        JSValue compareFunction = JSValue.argumentOrUndefined(args, 0);
        if (!(compareFunction instanceof JSUndefined) && !JSTypeChecking.isCallable(compareFunction)) {
            return context.throwTypeError("The comparison function must be either a function or undefined: "
                    + (compareFunction instanceof JSObject ? "#<Object>" : compareFunction.toString()));
        }

        JSObject obj = requireArrayLike(context, thisArg, copy ? "toSorted" : "sort");
        long length = checkedLength(context, obj);
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
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            if (!copy) {
                boolean present = obj.has(key);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (!present) {
                    continue;
                }
            }
            JSValue value = obj.get(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!copy && value instanceof JSUndefined) {
                undefinedCount++;
                continue;
            }
            sortableElements.add(value);
        }

        // Phase 2: Sort only the defined, present elements.
        JSValue[] sorted = sortableElements.toArray(JSValue[]::new);
        if (!SortOperations.stableSort(context, sorted,
                (a, b) -> SortOperations.compareArrayElements(context, compareFunction, a, b))) {
            return context.getPendingException();
        }
        if (copy) {
            JSArray result = context.createJSArray(0, sorted.length);
            for (JSValue value : sorted) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                result.push(value);
            }
            return result;
        }

        // Phase 3: Write back sorted elements, then undefined values, then delete holes.
        long writeIndex = 0;

        // Write sorted elements
        for (JSValue value : sorted) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(writeIndex);
            if (!setOrThrow(context, obj, key, value)) {
                return context.getPendingException();
            }
            writeIndex++;
        }

        // Write undefined values after sorted elements
        for (int i = 0; i < undefinedCount; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(writeIndex);
            if (!setOrThrow(context, obj, key, JSUndefined.INSTANCE)) {
                return context.getPendingException();
            }
            writeIndex++;
        }

        // Delete remaining holes
        for (long i = writeIndex; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            if (!deleteOrThrow(context, obj, key)) {
                return context.getPendingException();
            }
        }

        return obj;
    }

    /**
     * Array.prototype.splice(start[, deleteCount[, ...items]]) Changes the contents of an array by removing or
     * replacing elements.
     */
    public static JSValue splice(JSContext context, JSValue thisArg, JSValue[] args) {
        return spliceElements(context, thisArg, args, false);
    }

    private static long spliceDeleteCount(JSContext context, JSValue[] args, long remaining) {
        if (args.length < 2) {
            return args.length == 0 ? 0 : remaining;
        }
        long count = toIntegerIndex(context, args[1]);
        return Math.max(0, Math.min(count, remaining));
    }

    private static JSValue spliceElements(JSContext context, JSValue thisArg, JSValue[] args, boolean copy) {
        JSObject obj = requireArrayLike(context, thisArg, copy ? "toSpliced" : "splice");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        long start = relativeIndex(context, args, 0, length, 0);
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
        // Step 12: Let A be ? ArraySpeciesCreate(O, actualDeleteCount).
        JSObject deletedObj = createSpeciesObject(context, obj, deleteCount);
        if (deletedObj == null) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }

        // Step 10: Collect deleted elements
        for (long i = 0; i < deleteCount; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(start + i);
            boolean kPresent = obj.has(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (kPresent) {
                JSValue val = obj.get(key);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                // Step 10.c.ii: Perform ? CreateDataPropertyOrThrow(A, ! ToString(k), fromValue).
                if (!deletedObj.defineProperty(PropertyKey.fromIndexString(i), val, PropertyDescriptor.DataState.All)) {
                    return context.throwTypeError("Cannot define property " + i);
                }
            }
        }
        // Step 11: Perform ? Set(A, "length", actualDeleteCount, true).
        deletedObj.set(PropertyKey.LENGTH, JSNumber.of(deleteCount));
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long insertCount = Math.max(0, args.length - 2);
        long newLen = length - deleteCount + insertCount;

        // Step 12: If len + insertCount - actualDeleteCount > 2^53 - 1, throw TypeError
        if (newLen > NumberPrototype.MAX_SAFE_INTEGER) {
            return context.throwTypeError("Array too long");
        }

        if (insertCount < deleteCount) {
            // Shift elements left
            for (long k = start; k < length - deleteCount; k++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                PropertyKey from = PropertyKey.fromIndexString(k + deleteCount);
                PropertyKey to = PropertyKey.fromIndexString(k + insertCount);
                if (!copyOrDeleteElement(context, obj, from, to)) {
                    return context.getPendingException();
                }
            }
            // Delete trailing elements
            for (long k = newLen; k < length; k++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                if (!deleteOrThrow(context, obj, PropertyKey.fromIndexString(k))) {
                    return context.getPendingException();
                }
            }
        } else if (insertCount > deleteCount) {
            // Shift elements right
            for (long k = length - deleteCount - 1; k >= start; k--) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                PropertyKey from = PropertyKey.fromIndexString(k + deleteCount);
                PropertyKey to = PropertyKey.fromIndexString(k + insertCount);
                if (!copyOrDeleteElement(context, obj, from, to)) {
                    return context.getPendingException();
                }
            }
        }

        // Insert new elements
        for (int i = 0; i < insertCount; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(start + i);
            if (!setOrThrow(context, obj, key, args[i + 2])) {
                return context.getPendingException();
            }
        }

        if (!setOrThrow(context, obj, PropertyKey.LENGTH, JSNumber.of(newLen))) {
            return context.getPendingException();
        }
        return deletedObj;
    }

    private static long toIntegerIndex(JSContext context, JSValue value) {
        // Java's narrowing conversion truncates towards zero, maps NaN to zero and saturates
        // infinities at the long bounds, which the caller then clamps to the array length.
        double number = value instanceof JSNumber index
                ? index.value()
                : JSTypeConversions.toNumber(context, value).value();
        return (long) number;
    }

    /**
     * Array.prototype.toLocaleString() ES2015 23.1.3.29 Returns a localized string representing the calling array and
     * its elements.
     */
    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "toLocaleString");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }
        // A cycle renders as "", as in join, and the array is registered once its length is read, as in join.
        Set<JSObject> joinStack = context.getArrayJoinStack();
        if (!joinStack.add(obj)) {
            return new JSString("");
        }
        try {
            return toLocaleStringOfElements(context, obj, length, args);
        } finally {
            joinStack.remove(obj);
        }
    }

    private static JSValue toLocaleStringOfElements(JSContext context, JSObject obj, long length, JSValue[] args) {
        StringBuilder sb = new StringBuilder();

        for (long i = 0; i < length; i++) {

            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (i > 0) {
                sb.append(",");
            }
            PropertyKey key = PropertyKey.fromIndexString(i);
            JSValue element = obj.get(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(element instanceof JSUndefined || element instanceof JSNull)) {
                JSObject elementObject = JSTypeConversions.toObject(context, element);
                if (elementObject == null || context.hasPendingException()) {
                    return context.getPendingException();
                }
                JSValue toLocaleStringValue = elementObject.get(PropertyKey.fromString("toLocaleString"), element);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (!JSTypeChecking.isCallable(toLocaleStringValue)) {
                    return context.throwTypeError("toLocaleString is not a function");
                }
                // Per ECMA-402, invoke toLocaleString with exactly 2 args: locales and options
                JSValue localesArg = JSValue.argumentOrUndefined(args, 0);
                JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
                JSValue localeStringValue = PromiseConstructor.callCallable(context, toLocaleStringValue, element,
                        new JSValue[]{localesArg, optionsArg});
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                sb.append(JSTypeConversions.toString(context, localeStringValue).value());
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        }

        return new JSString(sb.toString());
    }

    /**
     * ToObject + LengthOfArrayLike helper. Converts thisArg to an object and gets its length. Returns null if thisArg
     * is null/undefined (sets pending exception).
     */
    static JSObject toObjectChecked(JSContext context, JSValue thisArg) {
        JSObject obj = JSTypeConversions.toObject(context, thisArg);
        if (obj == null) {
            context.throwTypeError("Cannot convert undefined or null to object");
        }
        return obj;
    }

    /**
     * Array.prototype.toReversed() ES2023 23.1.3.31 Returns a new array with elements in reversed order (immutable
     * version of reverse).
     */
    public static JSValue toReversed(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "toReversed");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        // ArrayCreate step 1: If length > 2^32 - 1, throw a RangeError exception.
        if (length > 0xFFFF_FFFFL) {
            return context.throwRangeError("Invalid array length");
        }

        JSArray result = context.createJSArray(0, (int) length);

        // Copy elements in reverse order
        for (long i = length - 1; i >= 0; i--) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            JSValue element = obj.get(key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            result.push(element);
        }

        return result;
    }

    /**
     * Array.prototype.toSorted([compareFn]) ES2023 23.1.3.32 Returns a new sorted array (immutable version of sort).
     */
    public static JSValue toSorted(JSContext context, JSValue thisArg, JSValue[] args) {
        return sortArray(context, thisArg, args, true);
    }

    /**
     * Array.prototype.toSpliced(start, deleteCount, ...items) ES2023 23.1.3.33 Returns a new array with elements
     * removed and/or added (immutable version of splice).
     */
    public static JSValue toSpliced(JSContext context, JSValue thisArg, JSValue[] args) {
        return spliceElements(context, thisArg, args, true);
    }

    /**
     * Array.prototype.toString() Returns a string representing the array.
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject object = JSTypeConversions.toObject(context, thisArg);
        if (object == null) {
            return context.getPendingException();
        }
        JSValue joinValue = object.get(PropertyKey.fromString("join"), thisArg);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (JSTypeChecking.isCallable(joinValue)) {
            return PromiseConstructor.callCallable(context, joinValue, thisArg, JSValue.NO_ARGS);
        }
        // Step 3: a non-callable join, undefined included, falls back to Object.prototype.toString. An array with
        // join set to undefined used to be joined anyway.
        return ObjectPrototype.toString(context, thisArg, args);
    }

    /**
     * Array.prototype.unshift(...items) Adds elements to the beginning of an array.
     */
    public static JSValue unshift(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "unshift");
        long length = checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        int argCount = args.length;

        // Step 4: If len + argCount > 2^53 - 1, throw TypeError
        if (length + argCount > NumberPrototype.MAX_SAFE_INTEGER) {
            return context.throwTypeError("Array too long");
        }

        if (argCount > 0) {
            // Shift existing elements right
            for (long k = length - 1; k >= 0; k--) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                PropertyKey from = PropertyKey.fromIndexString(k);
                PropertyKey to = PropertyKey.fromIndexString(k + argCount);
                if (!copyOrDeleteElement(context, obj, from, to)) {
                    return context.getPendingException();
                }
            }
        }
        // Insert new elements at the beginning
        for (int j = 0; j < argCount; j++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (!setOrThrow(context, obj, PropertyKey.fromIndexString(j), args[j])) {
                return context.getPendingException();
            }
        }

        long newLen = length + argCount;
        if (!setOrThrow(context, obj, PropertyKey.LENGTH, JSNumber.of(newLen))) {
            return context.getPendingException();
        }
        return JSNumber.of(newLen);
    }

    /**
     * Array.prototype.with(index, value) ES2023 23.1.3.34 Returns a new array with the element at the given index
     * replaced (immutable version of arr[index] = value).
     */
    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject obj = requireArrayLike(context, thisArg, "with");
        long length = checkedLength(context, obj);
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

        JSValue newValue = args[1];

        // Create a copy of the array
        JSArray result = context.createJSArray(0, (int) length);
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (i == index) {
                result.push(newValue);
            } else {
                PropertyKey key = PropertyKey.fromIndexString(i);
                result.push(obj.get(key));
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        }

        return result;
    }
}
