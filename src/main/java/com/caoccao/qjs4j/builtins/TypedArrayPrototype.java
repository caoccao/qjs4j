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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class TypedArrayPrototype {
    private TypedArrayPrototype() {
    }

    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.at");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.at on a typed array backed by a detached or out-of-bounds buffer");
        }
        // Step 3: Capture length before coercion
        int length = typedArray.getLength();
        // Step 4: Coerce index (may resize buffer)
        double relativeIndex = args.length > 0 ? JSTypeConversions.toInteger(context, args[0]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // Steps 5-8: Use original length for bounds computation
        int index;
        if (relativeIndex >= 0) {
            index = (int) relativeIndex;
        } else {
            index = (int) (length + relativeIndex);
        }
        // Step 9: Check against original length
        if (index < 0 || index >= length) {
            return JSUndefined.INSTANCE;
        }
        // Step 10: Get element (safe for detached/resized buffers)
        return safeGetElement(typedArray, index);
    }

    public static JSValue copyWithin(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.copyWithin");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.copyWithin on a typed array backed by a detached or out-of-bounds buffer");
        }
        // Step 3: Capture length before coercion
        int length = typedArray.getLength();

        // Steps 4-7: Coerce target (may resize buffer)
        double relativeTarget = args.length > 0 ? JSTypeConversions.toInteger(context, args[0]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        int target = (int) (relativeTarget < 0 ? Math.max(length + relativeTarget, 0) : Math.min(relativeTarget, length));

        // Steps 8-10: Coerce start (may resize buffer)
        double relativeStart = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        int start = (int) (relativeStart < 0 ? Math.max(length + relativeStart, 0) : Math.min(relativeStart, length));

        // Steps 11-13: Coerce end (may resize buffer)
        int end;
        if (args.length > 2 && !args[2].isUndefined()) {
            double relativeEnd = JSTypeConversions.toInteger(context, args[2]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            end = (int) (relativeEnd < 0 ? Math.max(length + relativeEnd, 0) : Math.min(relativeEnd, length));
        } else {
            end = length;
        }

        // Step 14: Compute count from original length values
        int count = Math.min(end - start, length - target);
        if (count <= 0) {
            return typedArray;
        }

        // Step 15: Check for OOB after all argument coercion
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.copyWithin on a typed array backed by a detached or out-of-bounds buffer");
        }

        // Steps 15d-i: Re-read length, clamp to/from/count
        int currentLength = typedArray.getLength();
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
        ByteBuffer buffer = arrayBuffer.getBuffer().duplicate();
        buffer.order(arrayBuffer.getBuffer().order());
        int baseByteOffset = typedArray.getByteOffset();
        int fromByteOffset = baseByteOffset + start * elementSize;
        int toByteOffset = baseByteOffset + target * elementSize;
        int byteCount = count * elementSize;

        // Handle overlapping regions by copying through temp array
        byte[] temp = new byte[byteCount];
        buffer.position(fromByteOffset);
        buffer.get(temp, 0, byteCount);
        buffer.position(toByteOffset);
        buffer.put(temp, 0, byteCount);

        return typedArray;
    }

    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.entries");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.entries on a typed array backed by a detached or out-of-bounds buffer");
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
            pair.push(typedArray.getJSElement(index[0]));
            index[0]++;
            return JSIterator.IteratorResult.of(context, pair);
        }, "Array Iterator");
    }

    public static JSValue every(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.every");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.every on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = typedArray.getLength();
        for (int k = 0; k < length; k++) {
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue testResult = callbackFn.call(context, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(testResult) == JSBoolean.FALSE) {
                return JSBoolean.FALSE;
            }
        }
        return JSBoolean.TRUE;
    }

    public static JSValue fill(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.fill");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.fill on a typed array backed by a detached or out-of-bounds buffer");
        }
        JSValue rawValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Step 3: Capture length BEFORE value conversion (per spec order)
        int length = typedArray.getLength();

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

        // Steps 6-9: Compute startIndex from original length
        int start = 0;
        if (args.length > 1) {
            double relativeStart = JSTypeConversions.toInteger(context, args[1]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (relativeStart < 0) {
                start = Math.max((int) (length + relativeStart), 0);
            } else {
                start = Math.min((int) relativeStart, length);
            }
        }

        // Steps 10-13: Compute endIndex from original length
        int end = length;
        if (args.length > 2 && !args[2].isUndefined()) {
            double relativeEnd = JSTypeConversions.toInteger(context, args[2]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (relativeEnd < 0) {
                end = Math.max((int) (length + relativeEnd), 0);
            } else {
                end = Math.min((int) relativeEnd, length);
            }
        }

        // Step 14: Check if buffer is now detached (after value/start/end coercion)
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.fill on a typed array backed by a detached or out-of-bounds buffer");
        }

        // Step 15-17: Re-read length, clamp endIndex and startIndex
        int currentLength = typedArray.getLength();
        end = Math.min(end, currentLength);
        start = Math.min(start, end);

        for (int i = start; i < end; i++) {
            typedArray.set(context, PropertyKey.fromIndex(i), convertedValue);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return typedArray;
    }

    public static JSValue filter(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.filter");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.filter on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = typedArray.getLength();

        // Collect kept elements
        List<JSValue> kept = new ArrayList<>();
        for (int k = 0; k < length; k++) {
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue selected = callbackFn.call(context, callbackThisArg,
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
        for (int i = 0; i < kept.size(); i++) {
            resultArray.set(context, PropertyKey.fromIndex(i), kept.get(i));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return resultArray;
    }

    public static JSValue find(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.find");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.find on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = typedArray.getLength();
        for (int k = 0; k < length; k++) {
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue testResult = callbackFn.call(context, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(testResult) == JSBoolean.TRUE) {
                return kValue;
            }
        }
        return JSUndefined.INSTANCE;
    }

    public static JSValue findIndex(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.findIndex");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.findIndex on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = typedArray.getLength();
        for (int k = 0; k < length; k++) {
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue testResult = callbackFn.call(context, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(testResult) == JSBoolean.TRUE) {
                return JSNumber.of(k);
            }
        }
        return JSNumber.of(-1);
    }

    public static JSValue findLast(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.findLast");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.findLast on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = typedArray.getLength();
        for (int k = length - 1; k >= 0; k--) {
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue testResult = callbackFn.call(context, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(testResult) == JSBoolean.TRUE) {
                return kValue;
            }
        }
        return JSUndefined.INSTANCE;
    }

    public static JSValue findLastIndex(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.findLastIndex");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.findLastIndex on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = typedArray.getLength();
        for (int k = length - 1; k >= 0; k--) {
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue testResult = callbackFn.call(context, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(testResult) == JSBoolean.TRUE) {
                return JSNumber.of(k);
            }
        }
        return JSNumber.of(-1);
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

    private static boolean isBigIntTypedArray(JSTypedArray typedArray) {
        return typedArray instanceof JSBigInt64Array || typedArray instanceof JSBigUint64Array;
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
            JSValue value = typedArray.getJSElement(i);
            sb.append(JSTypeConversions.toString(context, value).value());
        }
        return new JSString(sb.toString());
    }

    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.keys");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.keys on a typed array backed by a detached or out-of-bounds buffer");
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

    /**
     * Safe element access that returns undefined for detached/out-of-bounds typed arrays.
     * Per ES spec, IntegerIndexedElementGet returns undefined when the buffer is detached
     * or the index is out of bounds.
     */
    private static JSValue safeGetElement(JSTypedArray typedArray, int index) {
        if (typedArray.getBuffer().isDetached() || typedArray.isOutOfBounds() || index < 0 || index >= typedArray.getLength()) {
            return JSUndefined.INSTANCE;
        }
        return typedArray.getJSElement(index);
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

    /**
     * TypedArraySpeciesCreate per ES2024 23.2.4.1.
     * Creates a new TypedArray using the species constructor pattern.
     * Includes TypedArrayCreate length validation (step 3a).
     */
    private static JSValue typedArraySpeciesCreate(JSContext context, JSTypedArray exemplar, JSValue[] args) {
        // Get the default constructor for this typed array type
        JSValue defaultConstructor = context.getGlobalObject().get(PropertyKey.fromString(exemplar.getTypedArrayName()));

        // SpeciesConstructor(exemplar, defaultConstructor)
        JSValue constructorValue = exemplar.get(context, PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        JSValue chosenConstructor;
        if (constructorValue instanceof JSUndefined) {
            chosenConstructor = defaultConstructor;
        } else if (!(constructorValue instanceof JSObject constructorObj)) {
            return context.throwTypeError("constructor is not an object");
        } else {
            JSValue species = constructorObj.get(context, PropertyKey.SYMBOL_SPECIES);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (species instanceof JSUndefined || species instanceof JSNull) {
                chosenConstructor = defaultConstructor;
            } else if (species instanceof JSFunction) {
                chosenConstructor = species;
            } else {
                return context.throwTypeError("Species is not a constructor");
            }
        }

        // TypedArrayCreate(constructor, argumentList)
        JSValue result = JSReflectObject.constructSimple(context, chosenConstructor, args);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Validate the result is a typed array
        if (!(result instanceof JSTypedArray resultArray)) {
            return context.throwTypeError("TypedArray species constructor did not return a TypedArray");
        }

        // TypedArrayCreate step 3: If argumentList is a single Number,
        // check that newTypedArray's length >= that number
        if (args.length == 1 && args[0] instanceof JSNumber) {
            int requestedLength = (int) ((JSNumber) args[0]).value();
            if (resultArray.getLength() < requestedLength) {
                return context.throwTypeError("TypedArray species constructor returned an array that is too small");
            }
        }

        return result;
    }

    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.values");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.values on a typed array backed by a detached or out-of-bounds buffer");
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
            return JSIterator.IteratorResult.of(context, typedArray.getJSElement(index[0]++));
        }, "Array Iterator");
    }
}
