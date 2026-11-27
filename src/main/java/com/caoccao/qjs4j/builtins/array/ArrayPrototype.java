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

import com.caoccao.qjs4j.builtins.object.ObjectPrototype;
import com.caoccao.qjs4j.builtins.promise.PromiseConstructor;
import com.caoccao.qjs4j.core.*;

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
        ArrayLikeElements elements = ArrayLikeElements.capture(context, toObjectChecked(context, thisArg));
        return elements == null ? context.getPendingException() : elements.at(args);
    }

    static long checkedLength(JSContext context, JSObject receiver) {
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
        return ArraySpecies.concat(context, thisArg, args);
    }

    /**
     * Array.prototype.copyWithin(target, start[, end]) ES2015 23.1.3.3 Copies a sequence of array elements within the
     * array.
     */
    public static JSValue copyWithin(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayCopy.copyWithin(context, thisArg, args);
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
        return ArrayCopy.fill(context, thisArg, args);
    }

    /**
     * Array.prototype.filter(callbackFn[, thisArg]) Creates a new array with elements that pass the test.
     */
    public static JSValue filter(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArraySpecies.mapOrFilter(context, thisArg, args, true);
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
        ArrayLikeElements elements = ArrayLikeElements.arrayCallback(context, thisArg, args, methodName);
        return elements == null ? context.getPendingException() : elements.find(args, fromEnd, returnIndex);
    }

    /**
     * Array.prototype.flat([depth]) Creates a new array with all sub-array elements concatenated.
     */
    public static JSValue flat(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArraySpecies.flatten(context, thisArg, args, false);
    }

    /**
     * Array.prototype.flatMap(callback, thisArg) ES2019 22.1.3.11 Maps each element using callback, then flattens the
     * result by one level.
     */
    public static JSValue flatMap(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArraySpecies.flatten(context, thisArg, args, true);
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
        if (obj instanceof JSArray arr) {
            return arr.get(index);
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
        ArrayLikeElements elements = ArrayLikeElements.ordinary(context, thisArg, "includes");
        return elements == null ? context.getPendingException() : elements.search(args, false, true);
    }

    /**
     * Array.prototype.indexOf(searchElement[, fromIndex]) Returns the first index at which a given element can be
     * found.
     */
    public static JSValue indexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchIndex(context, thisArg, args, false);
    }

    private static JSValue iterate(JSContext context, JSValue thisArg, JSValue[] args, ArrayIteration iteration) {
        ArrayLikeElements elements = ArrayLikeElements.arrayCallback(context, thisArg, args, iteration.methodName);
        return elements == null ? context.getPendingException() : elements.iterate(args, iteration);
    }

    /**
     * Array.prototype.join([separator]) Joins all elements of an array into a string.
     */
    public static JSValue join(JSContext context, JSValue thisArg, JSValue[] args) {
        ArrayLikeElements elements = ArrayLikeElements.ordinary(context, thisArg, "join");
        return elements == null ? context.getPendingException() : ArrayJoin.join(context, elements, args, false);
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
        return ArraySpecies.mapOrFilter(context, thisArg, args, false);
    }

    /**
     * Array.prototype.pop() Removes and returns the last element of an array.
     */
    public static JSValue pop(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayCopy.removeEndElement(context, thisArg, false);
    }

    /**
     * Array.prototype.push(...items) Appends elements to the end of an array.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-array.prototype.push">ECMAScript Array.prototype.push</a>
     */
    public static JSValue push(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayCopy.insertAtEnd(context, thisArg, args, false);
    }

    /**
     * Array.prototype.reduce(callbackFn[, initialValue]) Reduces array to a single value by calling a function on each
     * element.
     */
    public static JSValue reduce(JSContext context, JSValue thisArg, JSValue[] args) {
        return reduceElements(context, thisArg, args, false);
    }

    private static JSValue reduceElements(JSContext context, JSValue thisArg, JSValue[] args, boolean fromEnd) {
        ArrayLikeElements elements = ArrayLikeElements.arrayCallback(context, thisArg, args,
                fromEnd ? "reduceRight" : "reduce");
        return elements == null ? context.getPendingException() : elements.reduce(args, fromEnd);
    }

    /**
     * Array.prototype.reduceRight(callbackFn[, initialValue]) Reduces array from right to left.
     */
    public static JSValue reduceRight(JSContext context, JSValue thisArg, JSValue[] args) {
        return reduceElements(context, thisArg, args, true);
    }

    static JSObject requireArrayLike(JSContext context, JSValue receiver, String method) {
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
        return ArrayCopy.reverse(context, thisArg, args);
    }

    private static JSValue searchIndex(JSContext context, JSValue thisArg, JSValue[] args, boolean fromEnd) {
        ArrayLikeElements elements = ArrayLikeElements.ordinary(context, thisArg, fromEnd ? "lastIndexOf" : "indexOf");
        return elements == null ? context.getPendingException() : elements.search(args, fromEnd, false);
    }

    /**
     * Array.prototype.shift() Removes and returns the first element of an array.
     */
    public static JSValue shift(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayCopy.removeEndElement(context, thisArg, true);
    }

    /**
     * Array.prototype.slice([begin[, end]]) Returns a shallow copy of a portion of an array.
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayCopy.slice(context, thisArg, args);
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
        return SortOperations.sortArray(context, thisArg, args, false);
    }

    /**
     * Array.prototype.splice(start[, deleteCount[, ...items]]) Changes the contents of an array by removing or
     * replacing elements.
     */
    public static JSValue splice(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayCopy.spliceElements(context, thisArg, args, false);
    }

    /**
     * Array.prototype.toLocaleString() ES2015 23.1.3.29 Returns a localized string representing the calling array and
     * its elements.
     */
    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        ArrayLikeElements elements = ArrayLikeElements.ordinary(context, thisArg, "toLocaleString");
        return elements == null ? context.getPendingException() : ArrayJoin.join(context, elements, args, true);
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
        return ArrayCopy.toReversed(context, thisArg, args);
    }

    /**
     * Array.prototype.toSorted([compareFn]) ES2023 23.1.3.32 Returns a new sorted array (immutable version of sort).
     */
    public static JSValue toSorted(JSContext context, JSValue thisArg, JSValue[] args) {
        return SortOperations.sortArray(context, thisArg, args, true);
    }

    /**
     * Array.prototype.toSpliced(start, deleteCount, ...items) ES2023 23.1.3.33 Returns a new array with elements
     * removed and/or added (immutable version of splice).
     */
    public static JSValue toSpliced(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayCopy.spliceElements(context, thisArg, args, true);
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
        return ArrayCopy.insertAtEnd(context, thisArg, args, true);
    }

    /**
     * Array.prototype.with(index, value) ES2023 23.1.3.34 Returns a new array with the element at the given index
     * replaced (immutable version of arr[index] = value).
     */
    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayCopy.with(context, thisArg, args);
    }

}
