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
import java.util.Collections;
import java.util.List;

/**
 * Array prototype methods.
 * Implements ECMAScript Array.prototype methods.
 *
 * @see <a href="https://tc39.es/ecma262/#sec-properties-of-the-array-prototype-object">ECMAScript Array.prototype</a>
 */
public final class ArrayPrototype {

    /**
     * ES2024 7.3.34 ArraySpeciesCreate(originalArray, length).
     * Following QuickJS JS_ArraySpeciesCreate.
     *
     * @return the new array, or null if an exception was set on context
     */
    static JSValue arraySpeciesCreate(JSContext context, JSObject originalArray, long length) {
        // Step 3: If IsArray(originalArray) is false, return ArrayCreate(length)
        int isArr = JSTypeChecking.isArray(context, originalArray);
        if (isArr < 0) {
            return null;
        }
        if (isArr == 0) {
            return context.createJSArray((int) Math.min(length, Integer.MAX_VALUE));
        }

        // Step 4: Let C be ? Get(originalArray, "constructor")
        JSValue ctor = originalArray.get(PropertyKey.CONSTRUCTOR, context);
        if (context.hasPendingException()) {
            return null;
        }

        // Step 5: If IsConstructor(C) is true, cross-realm check
        if (JSTypeChecking.isConstructor(ctor)) {
            // Simplified: single-realm engine, skip cross-realm check
            // QuickJS sets C = undefined if constructor is from a different realm
        }

        // Step 6: If Type(C) is Object, get Symbol.species
        if (ctor instanceof JSObject ctorObj) {
            ctor = ctorObj.get(PropertyKey.SYMBOL_SPECIES, context);
            if (context.hasPendingException()) {
                return null;
            }
            if (ctor instanceof JSNull) {
                ctor = JSUndefined.INSTANCE;
            }
        }

        // Step 7: If C is undefined, return ArrayCreate(length)
        if (ctor instanceof JSUndefined) {
            return context.createJSArray((int) Math.min(length, Integer.MAX_VALUE));
        }

        // Step 8: If IsConstructor(C) is false, throw a TypeError exception
        if (!JSTypeChecking.isConstructor(ctor)) {
            return context.throwTypeError("Species constructor is not a constructor");
        }

        // Step 9: Return ? Construct(C, « length »)
        JSValue result = JSReflectObject.constructSimple(context, ctor, new JSValue[]{JSNumber.of(length)});
        if (context.hasPendingException()) {
            return null;
        }
        return result;
    }

    /**
     * Array.prototype.at(index)
     * ES2022 23.1.3.1
     * Returns the element at the specified index, supporting negative indices.
     * Generic: works on any object with a length property (per spec uses ToObject + LengthOfArrayLike).
     */
    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Let O be ? ToObject(this value).
        JSObject obj = JSTypeConversions.toObject(context, thisArg);
        if (obj == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 2: Let len be ? LengthOfArrayLike(O).
        long length;
        if (obj instanceof JSArray arr) {
            length = arr.getLength();
        } else {
            JSValue lenVal = obj.get(PropertyKey.LENGTH, context);
            if (context.hasPendingException()) return context.getPendingException();
            length = JSTypeConversions.toLength(context, lenVal);
            if (context.hasPendingException()) return context.getPendingException();
        }

        // Step 3: Let relativeIndex be ? ToIntegerOrInfinity(index).
        long index = (long) JSTypeConversions.toInteger(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return context.getPendingException();

        // Steps 4-5: Handle negative indices
        if (index < 0) {
            index = length + index;
        }

        // Step 6: Check bounds
        if (index < 0 || index >= length) {
            return JSUndefined.INSTANCE;
        }

        // Step 7: Return ? Get(O, ! ToString(k)).
        if (obj instanceof JSArray arr) {
            return arr.get((int) index);
        }
        return obj.get(PropertyKey.fromIndex((int) index), context);
    }

    /**
     * Array.prototype.concat(...items)
     * Merges arrays and/or values.
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
                return context.hasPendingException() ? context.getPendingException()
                        : context.throwTypeError("Array.prototype.concat called on null or undefined");
            }
        }

        // Step 2: Let A be ? ArraySpeciesCreate(O, 0)
        JSValue resultVal = arraySpeciesCreate(context, obj, 0);
        if (resultVal == null || context.hasPendingException()) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }
        if (!(resultVal instanceof JSObject resultObj)) {
            return context.throwTypeError("ArraySpeciesCreate did not return an object");
        }
        // Fast path flag: if result is a plain JSArray, use internal array ops
        JSArray resultArr = resultObj instanceof JSArray arr ? arr : null;
        long n = 0;

        // Steps 3-4: Process O (this) and each argument
        for (int i = -1; i < args.length; i++) {
            JSValue e = (i < 0) ? obj : args[i];

            // Step 4a: Let spreadable be ? IsConcatSpreadable(E)
            boolean spreadable = isConcatSpreadable(context, e);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            if (spreadable) {
                // Step 4b: Spreadable case - iterate through elements
                JSObject eObj = (JSObject) e;
                long len;
                if (eObj instanceof JSArray eArr) {
                    len = eArr.getLength();
                } else {
                    JSValue lenVal = eObj.get(PropertyKey.LENGTH, context);
                    if (context.hasPendingException()) return context.getPendingException();
                    len = JSTypeConversions.toLength(context, lenVal);
                    if (context.hasPendingException()) return context.getPendingException();
                }

                if (n + len > NumberPrototype.MAX_SAFE_INTEGER) { // MAX_SAFE_INTEGER
                    return context.throwTypeError("Array too long");
                }

                for (long k = 0; k < len; k++, n++) {
                    PropertyKey indexKey = PropertyKey.fromIndex((int) k);
                    // JS_TryGetPropertyInt64: only define property if it exists (preserve holes)
                    if (eObj.has(indexKey)) {
                        JSValue val = eObj.get(indexKey, context);
                        if (context.hasPendingException()) return context.getPendingException();
                        if (resultArr != null) {
                            resultArr.set(n, val);
                        } else if (!resultObj.createDataProperty(PropertyKey.fromIndex((int) n), val)) {
                            return context.throwTypeError("Cannot define property " + n);
                        }
                    }
                }
            } else {
                // Step 4c: Non-spreadable - add as single element
                if (n >= NumberPrototype.MAX_SAFE_INTEGER) {
                    return context.throwTypeError("Array too long");
                }
                if (resultArr != null) {
                    resultArr.set(n, e);
                } else if (!resultObj.createDataProperty(PropertyKey.fromIndex((int) n), e)) {
                    return context.throwTypeError("Cannot define property " + n);
                }
                n++;
            }
        }

        // Step 5: Set length
        if (resultArr != null) {
            resultArr.setLength(n);
        } else {
            resultObj.set(PropertyKey.LENGTH, JSNumber.of(n), context);
            if (context.hasPendingException()) return context.getPendingException();
        }
        return resultObj;
    }

    /**
     * Array.prototype.copyWithin(target, start[, end])
     * ES2015 23.1.3.3
     * Copies a sequence of array elements within the array.
     */
    public static JSValue copyWithin(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Let O be ? ToObject(this value).
        JSObject obj = JSTypeConversions.toObject(context, thisArg);
        if (obj == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 2: Let len be ? LengthOfArrayLike(O).
        long length;
        if (obj instanceof JSArray arr) {
            length = arr.getLength();
        } else {
            JSValue lenVal = obj.get(PropertyKey.LENGTH, context);
            if (context.hasPendingException()) return context.getPendingException();
            length = JSTypeConversions.toLength(context, lenVal);
            if (context.hasPendingException()) return context.getPendingException();
        }

        // Get target position (JS_ToInt64Clamp with min=0, max=len, neg_offset=len)
        long target = (long) JSTypeConversions.toInteger(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return context.getPendingException();
        if (target < 0) {
            target = Math.max(length + target, 0);
        } else {
            target = Math.min(target, length);
        }

        // Get start position
        long start = (long) JSTypeConversions.toInteger(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return context.getPendingException();
        if (start < 0) {
            start = Math.max(length + start, 0);
        } else {
            start = Math.min(start, length);
        }

        // Get end position
        long end;
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            end = (long) JSTypeConversions.toInteger(context, args[2]);
            if (context.hasPendingException()) return context.getPendingException();
            if (end < 0) {
                end = Math.max(length + end, 0);
            } else {
                end = Math.min(end, length);
            }
        } else {
            end = length;
        }

        // Calculate count
        long count = Math.min(end - start, length - target);
        if (count <= 0) {
            return obj;
        }

        // Copy direction depends on overlap (per spec step 12)
        int dir = (start < target && target < start + count) ? -1 : 1;
        for (long i = 0; i < count; i++) {
            final long from = dir < 0 ? (start + count - i - 1) : (start + i);
            final long to = dir < 0 ? (target + count - i - 1) : (target + i);
            final PropertyKey fromKey = PropertyKey.fromString(Long.toString(from));
            final PropertyKey toKey = PropertyKey.fromString(Long.toString(to));

            if (obj.has(fromKey)) {
                JSValue value = obj.get(fromKey, context);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                obj.set(toKey, value, context);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            } else if (!obj.delete(toKey, context)) {
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                return context.throwTypeError("Cannot delete property '" + to + "'");
            }
        }

        return obj;
    }

    /**
     * Array.prototype[Symbol.unscopables]
     * ES2015 23.1.3.32
     * Returns an object containing property names that are excluded from with statement binding.
     * These are methods added in ES2015 and later that should not be included in with statements.
     */
    public static JSObject createUnscopablesObject(JSContext context) {
        JSObject unscopables = context.createJSObject();
        unscopables.setPrototype(null);

        // ES2015 methods
        unscopables.set("copyWithin", JSBoolean.TRUE);
        unscopables.set("entries", JSBoolean.TRUE);
        unscopables.set("fill", JSBoolean.TRUE);
        unscopables.set("find", JSBoolean.TRUE);
        unscopables.set("findIndex", JSBoolean.TRUE);
        unscopables.set("flat", JSBoolean.TRUE);
        unscopables.set("flatMap", JSBoolean.TRUE);
        unscopables.set("includes", JSBoolean.TRUE);
        unscopables.set("keys", JSBoolean.TRUE);
        unscopables.set("values", JSBoolean.TRUE);

        // ES2022+ methods
        unscopables.set("at", JSBoolean.TRUE);
        unscopables.set("findLast", JSBoolean.TRUE);
        unscopables.set("findLastIndex", JSBoolean.TRUE);
        unscopables.set("toReversed", JSBoolean.TRUE);
        unscopables.set("toSorted", JSBoolean.TRUE);
        unscopables.set("toSpliced", JSBoolean.TRUE);

        return unscopables;
    }

    /**
     * Array.prototype.every(callbackFn[, thisArg])
     * Tests whether all elements pass the test.
     */
    public static JSValue every(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSNull || thisArg instanceof JSUndefined) {
            return context.throwTypeError("Array.prototype.every called on null or undefined");
        }
        JSObject obj = toObjectChecked(context, thisArg);
        if (obj == null) return context.getPendingException();

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        long length = lengthOfArrayLike(context, obj);
        if (context.hasPendingException()) return context.getPendingException();

        for (long i = 0; i < length; i++) {
            JSValue element = getElement(context, obj, i);
            if (context.hasPendingException()) return context.getPendingException();
            JSValue[] callbackArgs = {element, JSNumber.of(i), obj};
            JSValue result = callback.call(context, callbackThis, callbackArgs);
            if (context.hasPendingException()) return context.getPendingException();

            if (JSTypeConversions.toBoolean(result) == JSBoolean.FALSE) {
                return JSBoolean.FALSE;
            }
        }

        return JSBoolean.TRUE;
    }

    /**
     * Array.prototype.fill(value[, start[, end]])
     * ES2015 23.1.3.6
     * Fills all the elements of an array from a start index to an end index with a static value.
     */
    public static JSValue fill(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.fill called on non-array");
        }

        long length = arr.getLength();
        if (length == 0) {
            return arr;
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Get start position
        long start = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : 0;
        if (start < 0) {
            start = Math.max(length + start, 0);
        } else {
            start = Math.min(start, length);
        }

        // Get end position
        long end = args.length > 2 ? (long) JSTypeConversions.toInteger(context, args[2]) : length;
        if (end < 0) {
            end = Math.max(length + end, 0);
        } else {
            end = Math.min(end, length);
        }

        // Fill the array
        for (long i = start; i < end; i++) {
            arr.set((int) i, value);
        }

        return arr;
    }

    /**
     * Array.prototype.filter(callbackFn[, thisArg])
     * Creates a new array with elements that pass the test.
     */
    public static JSValue filter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.filter called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = (int) jsArray.getLength();
        JSArray result = context.createJSArray(0, length);

        for (long i = 0; i < length; i++) {
            JSValue element = jsArray.get(i);
            JSValue[] callbackArgs = {element, JSNumber.of(i), jsArray};
            JSValue keep = callback.call(context, callbackThis, callbackArgs);

            if (JSTypeConversions.toBoolean(keep) == JSBoolean.TRUE) {
                result.push(element);
            }
        }

        return result;
    }

    /**
     * Array.prototype.find(callbackFn[, thisArg])
     * Returns the first element that satisfies the test.
     */
    public static JSValue find(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.find called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        long length = arr.getLength();

        for (long i = 0; i < length; i++) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, JSNumber.of(i), arr};
            JSValue result = callback.call(context, callbackThis, callbackArgs);

            if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                return element;
            }
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Array.prototype.findIndex(callbackFn[, thisArg])
     * Returns the index of the first element that satisfies the test.
     */
    public static JSValue findIndex(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.findIndex called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        long length = arr.getLength();

        for (long i = 0; i < length; i++) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, JSNumber.of(i), arr};
            JSValue result = callback.call(context, callbackThis, callbackArgs);

            if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                return JSNumber.of(i);
            }
        }

        return JSNumber.of(-1);
    }

    /**
     * Array.prototype.findLast(callbackFn[, thisArg])
     * ES2023 23.1.3.13
     * Returns the last element that satisfies the test (iterates backwards).
     */
    public static JSValue findLast(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.findLast called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        long length = arr.getLength();

        // Iterate backwards
        for (long i = length - 1; i >= 0; i--) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, JSNumber.of(i), arr};
            JSValue result = callback.call(context, callbackThis, callbackArgs);

            if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                return element;
            }
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Array.prototype.findLastIndex(callbackFn[, thisArg])
     * ES2023 23.1.3.14
     * Returns the index of the last element that satisfies the test (iterates backwards).
     */
    public static JSValue findLastIndex(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.findLastIndex called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        long length = arr.getLength();

        // Iterate backwards
        for (long i = length - 1; i >= 0; i--) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, JSNumber.of(i), arr};
            JSValue result = callback.call(context, callbackThis, callbackArgs);

            if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                return JSNumber.of(i);
            }
        }

        return JSNumber.of(-1);
    }

    /**
     * Array.prototype.flat([depth])
     * Creates a new array with all sub-array elements concatenated.
     */
    public static JSValue flat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.flat called on non-array");
        }
        int depth = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 1;
        return internalFlattenArray(context, arr, depth);
    }

    /**
     * Array.prototype.flatMap(callback, thisArg)
     * ES2019 22.1.3.11
     * Maps each element using callback, then flattens the result by one level.
     */
    public static JSValue flatMap(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.flatMap called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Array.prototype.flatMap requires a callback function");
        }

        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        int length = (int) jsArray.getLength();
        JSArray result = context.createJSArray(0, length);

        for (int i = 0; i < length; i++) {
            JSValue element = jsArray.get(i);

            // Call the callback with (element, index, array)
            JSValue[] callbackArgs = new JSValue[]{
                    element,
                    JSNumber.of(i),
                    jsArray
            };
            JSValue mapped = callback.call(context, callbackThisArg, callbackArgs);

            // Flatten one level
            if (mapped instanceof JSArray mappedArray) {
                for (int j = 0; j < mappedArray.getLength(); j++) {
                    result.push(mappedArray.get(j));
                }
            } else {
                result.push(mapped);
            }
        }

        return result;
    }

    /**
     * Array.prototype.forEach(callbackFn[, thisArg])
     * Executes a function for each array element.
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.forEach called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        long length = arr.getLength();

        for (long i = 0; i < length; i++) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, JSNumber.of(i), arr};
            callback.call(context, callbackThis, callbackArgs);
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Get element at index from an array-like object.
     * Uses JSArray fast path for actual arrays.
     */
    static JSValue getElement(JSContext context, JSObject obj, long index) {
        if (obj instanceof JSArray arr && index <= Integer.MAX_VALUE) {
            return arr.get((int) index);
        }
        return obj.get(PropertyKey.fromString(Long.toString(index)), context);
    }

    /**
     * get Array.prototype.length
     * Returns the number of elements in the array.
     * This is a getter for the length property.
     */
    public static JSValue getLength(JSContext context, JSValue thisArg, JSValue[] args) {
        long length = 0;
        if (thisArg instanceof JSArray jsArray) {
            length = jsArray.getLength();
        }
        return JSNumber.of(length);
    }

    /**
     * Array.prototype.includes(searchElement[, fromIndex])
     * Determines whether an array includes a certain element.
     */
    public static JSValue includes(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.includes called on non-array");
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        JSValue searchElement = args[0];
        long length = arr.getLength();
        long fromIndex = args.length > 1 ? JSTypeConversions.toInt32(context, args[1]) : 0;

        if (fromIndex < 0) {
            fromIndex = Math.max(0, length + fromIndex);
        }

        for (long i = fromIndex; i < length; i++) {
            JSValue element = arr.get(i);
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
     * Array.prototype.indexOf(searchElement[, fromIndex])
     * Returns the first index at which a given element can be found.
     */
    public static JSValue indexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.indexOf called on non-array");
        }

        if (args.length == 0) {
            return JSNumber.of(-1);
        }

        JSValue searchElement = args[0];
        long length = arr.getLength();
        long fromIndex = args.length > 1 ? JSTypeConversions.toInt32(context, args[1]) : 0;

        if (fromIndex < 0) {
            fromIndex = Math.max(0, length + fromIndex);
        }

        for (long i = fromIndex; i < length; i++) {
            if (JSTypeConversions.strictEquals(arr.get(i), searchElement)) {
                return JSNumber.of(i);
            }
        }

        return JSNumber.of(-1);
    }

    private static JSArray internalFlattenArray(JSContext context, JSArray jsArray, int depth) {
        int length = (int) jsArray.getLength();
        JSArray result = context.createJSArray(0, length);
        for (int i = 0; i < length; i++) {
            JSValue element = jsArray.get(i);
            if (depth > 0 && element instanceof JSArray childJSArray) {
                JSArray flattened = internalFlattenArray(context, childJSArray, depth - 1);
                for (int j = 0; j < flattened.getLength(); j++) {
                    result.push(flattened.get(j));
                }
            } else {
                result.push(element);
            }
        }
        return result;
    }

    /**
     * IsConcatSpreadable per ES2024 23.1.3.1 step 4a.
     * Following QuickJS JS_isConcatSpreadable.
     */
    private static boolean isConcatSpreadable(JSContext context, JSValue val) {
        if (!(val instanceof JSObject obj)) {
            return false;
        }
        JSValue spreadable = obj.get(PropertyKey.SYMBOL_IS_CONCAT_SPREADABLE, context);
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

    /**
     * Array.prototype.join([separator])
     * Joins all elements of an array into a string.
     */
    public static JSValue join(JSContext context, JSValue thisArg, JSValue[] args) {
        StringBuilder result = new StringBuilder();
        if (thisArg instanceof JSArray jsArray) {
            String separator = args.length > 0 && !(args[0] instanceof JSUndefined) ?
                    JSTypeConversions.toString(context, args[0]).value() : ",";
            long length = jsArray.getLength();
            for (long i = 0; i < length; i++) {
                if (i > 0) {
                    result.append(separator);
                }
                JSValue element = jsArray.get(i);
                if (!(element instanceof JSNull) && !(element instanceof JSUndefined)) {
                    result.append(JSTypeConversions.toString(context, element).value());
                }
            }
        } else if (thisArg instanceof JSObject jsObject) {
            int length = (int) JSTypeConversions.toLength(context, jsObject.get("length"));
            if (length > 0) {
                String separator = args.length > 0 && !args[0].isUndefined() ?
                        JSTypeConversions.toString(context, args[0]).value() : ",";
                for (int i = 0; i < length; i++) {
                    if (i > 0) {
                        result.append(separator);
                    }
                    JSValue element = jsObject.get(i);
                    if (!element.isNullOrUndefined()) {
                        result.append(JSTypeConversions.toString(context, element).value());
                    } else {
                        element = jsObject.get(String.valueOf(i));
                        if (!element.isNullOrUndefined()) {
                            result.append(JSTypeConversions.toString(context, element).value());
                        }
                    }
                }
            }
        }
        return new JSString(result.toString());
    }

    /**
     * Array.prototype.lastIndexOf(searchElement[, fromIndex])
     * Returns the last index at which a given element can be found.
     */
    public static JSValue lastIndexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.lastIndexOf called on non-array");
        }

        if (args.length == 0) {
            return JSNumber.of(-1);
        }

        JSValue searchElement = args[0];
        long length = arr.getLength();
        long fromIndex = args.length > 1 ? JSTypeConversions.toInt32(context, args[1]) : length - 1;

        if (fromIndex < 0) {
            fromIndex = length + fromIndex;
        } else {
            fromIndex = Math.min(fromIndex, length - 1);
        }

        for (long i = fromIndex; i >= 0; i--) {
            if (JSTypeConversions.strictEquals(arr.get(i), searchElement)) {
                return JSNumber.of(i);
            }
        }

        return JSNumber.of(-1);
    }

    /**
     * LengthOfArrayLike(obj) — returns the length of an array-like object.
     * Uses JSArray.getLength() fast path for actual arrays.
     */
    static long lengthOfArrayLike(JSContext context, JSObject obj) {
        if (obj instanceof JSArray arr) {
            return arr.getLength();
        }
        JSValue lenVal = obj.get(PropertyKey.LENGTH, context);
        if (context.hasPendingException()) return -1;
        long len = JSTypeConversions.toLength(context, lenVal);
        if (context.hasPendingException()) return -1;
        return len;
    }

    /**
     * Array.prototype.map(callbackFn[, thisArg])
     * Creates a new array with the results of calling a function on every element.
     */
    public static JSValue map(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.map called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = (int) jsArray.getLength();
        JSArray result = context.createJSArray(0, length);

        for (long i = 0; i < length; i++) {
            JSValue element = jsArray.get(i);
            JSValue[] callbackArgs = {element, JSNumber.of(i), jsArray};
            JSValue mapped = callback.call(context, callbackThis, callbackArgs);
            result.push(mapped);
        }

        return result;
    }

    /**
     * Array.prototype.pop()
     * Removes and returns the last element of an array.
     */
    public static JSValue pop(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.pop called on non-array");
        }

        return arr.pop();
    }

    /**
     * Array.prototype.push(...items)
     * Appends elements to the end of an array.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-array.prototype.push">ECMAScript Array.prototype.push</a>
     */
    public static JSValue push(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.push called on non-array");
        }

        for (JSValue arg : args) {
            arr.push(arg, context);
        }

        return JSNumber.of(arr.getLength());
    }

    /**
     * Array.prototype.reduce(callbackFn[, initialValue])
     * Reduces array to a single value by calling a function on each element.
     */
    public static JSValue reduce(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.reduce called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        long length = arr.getLength();
        if (length == 0 && args.length < 2) {
            return context.throwTypeError("Reduce of empty array with no initial value");
        }

        long startIndex = 0;
        JSValue accumulator;

        if (args.length >= 2) {
            accumulator = args[1];
        } else {
            accumulator = arr.get(0);
            startIndex = 1;
        }

        for (long i = startIndex; i < length; i++) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {accumulator, element, JSNumber.of(i), arr};
            accumulator = callback.call(context, JSUndefined.INSTANCE, callbackArgs);
        }

        return accumulator;
    }

    /**
     * Array.prototype.reduceRight(callbackFn[, initialValue])
     * Reduces array from right to left.
     */
    public static JSValue reduceRight(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.reduceRight called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        long length = arr.getLength();
        if (length == 0 && args.length < 2) {
            return context.throwTypeError("Reduce of empty array with no initial value");
        }

        long startIndex = length - 1;
        JSValue accumulator;

        if (args.length >= 2) {
            accumulator = args[1];
        } else {
            accumulator = arr.get(length - 1);
            startIndex = length - 2;
        }

        for (long i = startIndex; i >= 0; i--) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {accumulator, element, JSNumber.of(i), arr};
            accumulator = callback.call(context, JSUndefined.INSTANCE, callbackArgs);
        }

        return accumulator;
    }

    /**
     * Array.prototype.reverse()
     * Reverses the elements of an array in place.
     */
    public static JSValue reverse(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.reverse called on non-array");
        }

        long length = arr.getLength();
        for (long i = 0; i < length / 2; i++) {
            JSValue temp = arr.get(i);
            arr.set(i, arr.get(length - 1 - i));
            arr.set(length - 1 - i, temp);
        }

        return arr;
    }

    /**
     * Array.prototype.shift()
     * Removes and returns the first element of an array.
     */
    public static JSValue shift(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.shift called on non-array");
        }

        return arr.shift();
    }

    /**
     * Array.prototype.slice([begin[, end]])
     * Returns a shallow copy of a portion of an array.
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.slice called on non-array");
        }

        int length = (int) jsArray.getLength();
        int begin = 0;
        int end = length;

        if (args.length > 0) {
            begin = JSTypeConversions.toInt32(context, args[0]);
            if (begin < 0) {
                begin = Math.max(length + begin, 0);
            } else {
                begin = Math.min(begin, length);
            }
        }

        if (args.length > 1) {
            end = JSTypeConversions.toInt32(context, args[1]);
            if (end < 0) {
                end = Math.max(length + end, 0);
            } else {
                end = Math.min(end, length);
            }
        }

        JSArray result = context.createJSArray(0, end - begin);
        for (long i = begin; i < end; i++) {
            result.push(jsArray.get(i));
        }

        return result;
    }

    /**
     * Array.prototype.some(callbackFn[, thisArg])
     * Tests whether at least one element passes the test.
     */
    public static JSValue some(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.some called on non-array");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Callback must be a function");
        }

        JSValue callbackThis = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        long length = arr.getLength();

        for (long i = 0; i < length; i++) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, JSNumber.of(i), arr};
            JSValue result = callback.call(context, callbackThis, callbackArgs);

            if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                return JSBoolean.TRUE;
            }
        }

        return JSBoolean.FALSE;
    }

    /**
     * Array.prototype.sort([compareFn])
     * Sorts the elements of an array in place.
     */
    public static JSValue sort(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.sort called on non-array");
        }

        JSFunction compareFn = args.length > 0 && args[0] instanceof JSFunction ?
                (JSFunction) args[0] : null;

        List<JSValue> elements = new ArrayList<>();
        long length = arr.getLength();
        for (long i = 0; i < length; i++) {
            elements.add(arr.get(i));
        }

        Collections.sort(elements, (a, b) -> {
            if (compareFn != null) {
                JSValue[] compareArgs = {a, b};
                JSValue result = compareFn.call(context, JSUndefined.INSTANCE, compareArgs);
                return JSTypeConversions.toInt32(context, result);
            } else {
                // Default: convert to strings and compare
                String aStr = JSTypeConversions.toString(context, a).value();
                String bStr = JSTypeConversions.toString(context, b).value();
                return aStr.compareTo(bStr);
            }
        });

        // Update array with sorted elements
        for (long i = 0; i < length; i++) {
            arr.set(i, elements.get((int) i));
        }

        return arr;
    }

    /**
     * Array.prototype.splice(start[, deleteCount[, ...items]])
     * Changes the contents of an array by removing or replacing elements.
     */
    public static JSValue splice(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.splice called on non-array");
        }

        int length = (int) jsArray.getLength();
        int start = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;

        if (start < 0) {
            start = Math.max(length + start, 0);
        } else {
            start = Math.min(start, length);
        }

        int deleteCount = length - start;
        if (args.length > 1) {
            deleteCount = Math.max(0, Math.min(JSTypeConversions.toInt32(context, args[1]), length - start));
        }

        // Collect deleted elements
        JSArray deleted = context.createJSArray(0, deleteCount);
        for (int i = 0; i < deleteCount; i++) {
            deleted.push(jsArray.get(start + i));
        }

        // Create new array with spliced result
        int insertCount = Math.max(0, args.length - 2);
        JSArray result = context.createJSArray(0, length - deleteCount + insertCount);

        // Copy elements before start
        for (long i = 0; i < start; i++) {
            result.push(jsArray.get(i));
        }

        // Insert new elements
        for (int i = 2; i < args.length; i++) {
            result.push(args[i]);
        }

        // Copy elements after deleted portion
        for (long i = start + deleteCount; i < length; i++) {
            result.push(jsArray.get(i));
        }

        // Replace original array contents
        jsArray.setLength(0);
        for (long i = 0; i < result.getLength(); i++) {
            jsArray.push(result.get(i));
        }

        return deleted;
    }

    /**
     * Array.prototype.toLocaleString()
     * ES2015 23.1.3.29
     * Returns a localized string representing the calling array and its elements.
     */
    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.toLocaleString called on non-array");
        }

        // For now, use the same implementation as toString
        // A full implementation would call toLocaleString on each element
        StringBuilder sb = new StringBuilder();
        long length = arr.getLength();

        for (long i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            JSValue element = arr.get(i);
            if (!(element instanceof JSUndefined || element instanceof JSNull)) {
                // In a full implementation, we would call toLocaleString on each element
                // For now, convert to string
                sb.append(JSTypeConversions.toString(context, element).value());
            }
        }

        return new JSString(sb.toString());
    }

    /**
     * ToObject + LengthOfArrayLike helper.
     * Converts thisArg to an object and gets its length.
     * Returns null if thisArg is null/undefined (sets pending exception).
     */
    static JSObject toObjectChecked(JSContext context, JSValue thisArg) {
        JSObject obj = JSTypeConversions.toObject(context, thisArg);
        if (obj == null) {
            context.throwTypeError("Cannot convert undefined or null to object");
        }
        return obj;
    }

    /**
     * Array.prototype.toReversed()
     * ES2023 23.1.3.31
     * Returns a new array with elements in reversed order (immutable version of reverse).
     */
    public static JSValue toReversed(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.toReversed called on non-array");
        }

        int length = (int) jsArray.getLength();
        JSArray result = context.createJSArray(0, length);

        // Copy elements in reverse order
        for (int i = length - 1; i >= 0; i--) {
            result.push(jsArray.get(i));
        }

        return result;
    }

    /**
     * Array.prototype.toSorted([compareFn])
     * ES2023 23.1.3.32
     * Returns a new sorted array (immutable version of sort).
     */
    public static JSValue toSorted(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.toSorted called on non-array");
        }

        JSFunction compareFn = args.length > 0 && args[0] instanceof JSFunction ?
                (JSFunction) args[0] : null;

        // Create a copy of the array elements
        List<JSValue> elements = new ArrayList<>();
        int length = (int) jsArray.getLength();
        for (int i = 0; i < length; i++) {
            elements.add(jsArray.get(i));
        }

        // Sort the copy
        elements.sort((a, b) -> {
            if (compareFn != null) {
                JSValue[] compareArgs = {a, b};
                JSValue result = compareFn.call(context, JSUndefined.INSTANCE, compareArgs);
                return JSTypeConversions.toInt32(context, result);
            } else {
                // Default: convert to strings and compare
                String aStr = JSTypeConversions.toString(context, a).value();
                String bStr = JSTypeConversions.toString(context, b).value();
                return aStr.compareTo(bStr);
            }
        });

        // Create new array with sorted elements
        JSArray result = context.createJSArray(0, elements.size());
        for (JSValue element : elements) {
            result.push(element);
        }

        return result;
    }

    /**
     * Array.prototype.toSpliced(start, deleteCount, ...items)
     * ES2023 23.1.3.33
     * Returns a new array with elements removed and/or added (immutable version of splice).
     */
    public static JSValue toSpliced(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.toSpliced called on non-array");
        }

        int length = (int) jsArray.getLength();
        int start = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;

        // Normalize start index
        if (start < 0) {
            start = Math.max(length + start, 0);
        } else {
            start = Math.min(start, length);
        }

        // Calculate delete count
        int deleteCount = length - start;
        if (args.length > 1) {
            deleteCount = Math.max(0, Math.min(JSTypeConversions.toInt32(context, args[1]), length - start));
        }

        // Create new array with spliced result
        JSArray result = context.createJSArray(0, length);

        // Copy elements before start
        for (long i = 0; i < start; i++) {
            result.push(jsArray.get(i));
        }

        // Insert new elements
        for (int i = 2; i < args.length; i++) {
            result.push(args[i]);
        }

        // Copy elements after deleted portion
        for (long i = start + deleteCount; i < length; i++) {
            result.push(jsArray.get(i));
        }

        return result;
    }

    /**
     * Array.prototype.toString()
     * Returns a string representing the array.
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        if (thisArg.isArray()) {
            return join(context, thisArg, new JSValue[0]);
        }
        if (thisArg instanceof JSObject jsObject) {
            JSValue joinValue = jsObject.get("join");
            if (joinValue instanceof JSFunction joinFn) {
                return joinFn.call(context, thisArg, new JSValue[0]);
            }
        }
        return ObjectPrototype.toString(context, thisArg, args);
    }

    /**
     * Array.prototype.unshift(...items)
     * Adds elements to the beginning of an array.
     */
    public static JSValue unshift(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray arr)) {
            return context.throwTypeError("Array.prototype.unshift called on non-array");
        }

        for (int i = args.length - 1; i >= 0; i--) {
            arr.unshift(args[i]);
        }

        return JSNumber.of(arr.getLength());
    }

    /**
     * Array.prototype.with(index, value)
     * ES2023 23.1.3.34
     * Returns a new array with the element at the given index replaced (immutable version of arr[index] = value).
     */
    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray jsArray)) {
            return context.throwTypeError("Array.prototype.with called on non-array");
        }

        if (args.length < 2) {
            return context.throwTypeError("Array.prototype.with requires 2 arguments");
        }

        int length = (int) jsArray.getLength();
        int index = JSTypeConversions.toInt32(context, args[0]);

        // Normalize negative index
        if (index < 0) {
            index = length + index;
        }

        // Check bounds
        if (index < 0 || index >= length) {
            return context.throwRangeError("Index out of bounds");
        }

        JSValue newValue = args[1];

        // Create a copy of the array
        JSArray result = context.createJSArray(0, length);
        for (long i = 0; i < length; i++) {
            if (i == index) {
                result.push(newValue);
            } else {
                result.push(jsArray.get(i));
            }
        }

        return result;
    }
}
