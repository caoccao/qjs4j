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
import com.caoccao.qjs4j.core.buffer.IJSArrayBuffer;
import com.caoccao.qjs4j.core.buffer.JSBigInt64Array;
import com.caoccao.qjs4j.core.buffer.JSBigUint64Array;
import com.caoccao.qjs4j.core.buffer.JSTypedArray;
import com.caoccao.qjs4j.core.iterator.IteratorKind;
import com.caoccao.qjs4j.core.iterator.JSIterator;

import java.util.ArrayList;
import java.util.List;

public final class TypedArrayPrototype {
    private TypedArrayPrototype() {
    }

    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        ArrayLikeElements elements = ArrayLikeElements.typed(context, thisArg, "TypedArray.prototype.at");
        return elements == null ? context.getPendingException() : elements.at(args);
    }

    static JSValue constructTypedArray(JSContext context, JSValue constructor, JSValue[] args) {
        JSValue result = JSReflectObject.constructSimple(context, constructor, args);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(result instanceof JSTypedArray)) {
            return context.throwTypeError("TypedArray species constructor did not return a TypedArray");
        }
        return result;
    }

    static JSValue convertElement(JSContext context, boolean bigInt, JSValue value) {
        return bigInt ? JSTypeConversions.toBigInt(context, value) : JSTypeConversions.toNumber(context, value);
    }

    public static JSValue copyWithin(JSContext context, JSValue thisArg, JSValue[] args) {
        return TypedArrayCopy.copyWithin(context, thisArg, args);
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

    static JSTypedArray createSameType(JSContext context, JSTypedArray typedArray, long length) {
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
        return TypedArrayCopy.fill(context, thisArg, args);
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
            JSValue kValue = typedArray.getElementOrUndefined(k);
            JSValue selected = ArrayLikeElements.callElement(context, callbackFn, callbackThisArg, kValue, k,
                    typedArray);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(selected) == JSBoolean.TRUE) {
                kept.add(kValue);
            }
        }

        // TypedArraySpeciesCreate (includes validation)
        JSTypedArray resultArray = speciesOfLength(context, typedArray, kept.size());
        if (resultArray == null) {
            return context.getPendingException();
        }
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
        ArrayLikeElements elements = ArrayLikeElements.typedCallback(context, thisArg, args, methodName);
        return elements == null ? context.getPendingException() : elements.find(args, reverse, returnIndex);
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
        return searchIndex(context, thisArg, args, Search.INCLUDES);
    }

    public static JSValue indexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchIndex(context, thisArg, args, Search.FIRST);
    }

    static boolean isBigIntTypedArray(JSTypedArray typedArray) {
        return typedArray instanceof JSBigInt64Array || typedArray instanceof JSBigUint64Array;
    }

    private static JSValue iterate(JSContext context, JSValue thisArg, JSValue[] args, ArrayIteration iteration) {
        ArrayLikeElements elements = ArrayLikeElements.typedCallback(context, thisArg, args,
                iteration.typedArrayMethod);
        return elements == null ? context.getPendingException() : elements.iterate(args, iteration);
    }

    public static JSValue join(JSContext context, JSValue thisArg, JSValue[] args) {
        ArrayLikeElements elements = ArrayLikeElements.typed(context, thisArg, "TypedArray.prototype.join");
        return elements == null ? context.getPendingException() : ArrayJoin.join(context, elements, args, false);
    }

    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        return createIterator(context, thisArg, "TypedArray.prototype.keys", IteratorKind.KEYS);
    }

    public static JSValue lastIndexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchIndex(context, thisArg, args, Search.LAST);
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
        JSTypedArray resultArray = speciesOfLength(context, typedArray, length);
        if (resultArray == null) {
            return context.getPendingException();
        }

        for (long k = 0; k < length; k++) {

            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue kValue = typedArray.getElementOrUndefined(k);
            JSValue mappedValue = ArrayLikeElements.callElement(context, callbackFn, callbackThisArg, kValue, k,
                    typedArray);
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
        ArrayLikeElements elements = ArrayLikeElements.typedCallback(context, thisArg, args, methodName);
        return elements == null ? context.getPendingException() : elements.reduce(args, reverse);
    }

    public static JSValue reduceRight(JSContext context, JSValue thisArg, JSValue[] args) {
        return reduce(context, thisArg, args, "TypedArray.prototype.reduceRight", true);
    }

    public static JSValue reverse(JSContext context, JSValue thisArg, JSValue[] args) {
        return TypedArrayCopy.reverse(context, thisArg, args);
    }

    private static JSValue searchIndex(JSContext context, JSValue thisArg, JSValue[] args, Search search) {
        ArrayLikeElements elements = ArrayLikeElements.typed(context, thisArg, search.method);
        return elements == null
                ? context.getPendingException()
                : elements.search(args, search == Search.LAST, search == Search.INCLUDES);
    }

    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        return TypedArrayCopy.set(context, thisArg, args);
    }

    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        return TypedArrayCopy.slice(context, thisArg, args);
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

        return SortOperations.sortTypedArray(context, typedArray, typedArray, length, compareArg);
    }

    static JSValue speciesConstructor(JSContext context, JSTypedArray exemplar, boolean requireFunction) {
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

    static JSTypedArray speciesOfLength(JSContext context, JSTypedArray array, long length) {
        JSValue result = typedArraySpeciesCreate(context, array, new JSValue[]{JSNumber.of(length)});
        return context.hasPendingException() ? null : (JSTypedArray) result;
    }

    public static JSValue subarray(JSContext context, JSValue thisArg, JSValue[] args) {
        return TypedArrayCopy.subarray(context, thisArg, args);
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        ArrayLikeElements elements = ArrayLikeElements.typed(context, thisArg, "TypedArray.prototype.toLocaleString");
        return elements == null ? context.getPendingException() : ArrayJoin.join(context, elements, args, true);
    }

    public static JSValue toReversed(JSContext context, JSValue thisArg, JSValue[] args) {
        return TypedArrayCopy.toReversed(context, thisArg, args);
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

        return SortOperations.sortTypedArray(context, typedArray, resultArray, length, compareArg);
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

    static JSTypedArray toTypedArray(JSContext context, JSValue thisArg, String methodName) {
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
    static JSTypedArray toTypedArrayForCallback(JSContext context, JSValue thisArg, JSValue[] args, String methodName) {
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
    static JSValue typedArraySpeciesCreate(JSContext context, JSTypedArray exemplar, JSValue[] args) {
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

    static JSTypedArray validateTypedArray(JSContext context, JSValue thisArg, String methodName) {
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
        return TypedArrayCopy.withMethod(context, thisArg, args);
    }

    private enum Search {
        FIRST("TypedArray.prototype.indexOf"),
        INCLUDES("TypedArray.prototype.includes"),
        LAST("TypedArray.prototype.lastIndexOf");
        final String method;
        Search(String method) {
            this.method = method;
        }
    }
}
