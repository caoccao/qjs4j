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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static JSValue callCallable(JSContext context, JSValue callable, JSValue thisArg, JSValue[] args) {
        if (callable instanceof JSProxy proxy) {
            return proxy.apply(context, thisArg, args);
        }
        if (callable instanceof JSFunction function) {
            return function.call(context, thisArg, args);
        }
        return context.throwTypeError("Value is not callable");
    }

    /**
     * CompareTypedArrayElements per ES2024 23.2.4.4.
     * Default comparison for typed array sort when no compareFn is provided.
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
            typedArray.set(PropertyKey.fromIndex(i), convertedValue);
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
            resultArray.set(PropertyKey.fromIndex(i), kept.get(i));
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

    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.forEach");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.forEach on a typed array backed by a detached or out-of-bounds buffer");
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
            callbackFn.call(context, callbackThisArg,
                    new JSValue[]{kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return JSUndefined.INSTANCE;
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
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.includes");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.includes on a typed array backed by a detached or out-of-bounds buffer");
        }
        int length = typedArray.getLength();
        if (length == 0) {
            return JSBoolean.FALSE;
        }
        JSValue searchElement = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double fromIndexD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        int fromIndex;
        if (fromIndexD >= length) {
            return JSBoolean.FALSE;
        } else if (fromIndexD < 0) {
            fromIndex = Math.max(0, (int) (length + fromIndexD));
        } else {
            fromIndex = (int) fromIndexD;
        }
        // After coercion of fromIndex, buffer may have been detached
        for (int k = fromIndex; k < length; k++) {
            JSValue element = safeGetElement(typedArray, k);
            // SameValueZero: NaN === NaN, +0 === -0
            if (sameValueZero(element, searchElement)) {
                return JSBoolean.TRUE;
            }
        }
        return JSBoolean.FALSE;
    }

    public static JSValue indexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.indexOf");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.indexOf on a typed array backed by a detached or out-of-bounds buffer");
        }
        int length = typedArray.getLength();
        if (length == 0) {
            return JSNumber.of(-1);
        }
        JSValue searchElement = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double fromIndexD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // After coercion of fromIndex, check if TA went OOB (fixed-length shrink)
        if (typedArray.isOutOfBounds()) {
            return JSNumber.of(-1);
        }
        // Use ORIGINAL length for index computation and loop bounds (ES2024 taRecord caching)
        if (fromIndexD >= length) {
            return JSNumber.of(-1);
        }
        int fromIndex;
        if (fromIndexD < 0) {
            fromIndex = Math.max(0, (int) (length + fromIndexD));
        } else {
            fromIndex = (int) fromIndexD;
        }
        for (int k = fromIndex; k < length; k++) {
            JSValue element = safeGetElement(typedArray, k);
            // indexOf uses strict equality (===)
            if (JSTypeConversions.strictEquals(element, searchElement)) {
                return JSNumber.of(k);
            }
        }
        return JSNumber.of(-1);
    }

    private static boolean isBigIntTypedArray(JSTypedArray typedArray) {
        return typedArray instanceof JSBigInt64Array || typedArray instanceof JSBigUint64Array;
    }

    public static JSValue join(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.join");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.join on a typed array backed by a detached or out-of-bounds buffer");
        }
        // Step 3: Capture length BEFORE separator coercion
        int length = typedArray.getLength();

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
        for (int i = 0; i < length; i++) {
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

    public static JSValue lastIndexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.lastIndexOf");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.lastIndexOf on a typed array backed by a detached or out-of-bounds buffer");
        }
        int length = typedArray.getLength();
        if (length == 0) {
            return JSNumber.of(-1);
        }
        JSValue searchElement = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double fromIndexD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : length - 1;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // After coercion of fromIndex, check if TA went OOB (fixed-length shrink)
        if (typedArray.isOutOfBounds()) {
            return JSNumber.of(-1);
        }
        // Use ORIGINAL length for index computation and loop bounds (ES2024 taRecord caching)
        int fromIndex;
        if (fromIndexD >= 0) {
            fromIndex = (int) Math.min(fromIndexD, length - 1);
        } else {
            fromIndex = (int) (length + fromIndexD);
        }
        for (int k = fromIndex; k >= 0; k--) {
            JSValue element = safeGetElement(typedArray, k);
            if (JSTypeConversions.strictEquals(element, searchElement)) {
                return JSNumber.of(k);
            }
        }
        return JSNumber.of(-1);
    }

    public static JSValue map(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.map");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.map on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        int length = typedArray.getLength();

        // TypedArraySpeciesCreate with same length
        JSValue result = typedArraySpeciesCreate(context, typedArray, new JSValue[]{JSNumber.of(length)});
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSTypedArray resultArray = (JSTypedArray) result;

        for (int k = 0; k < length; k++) {
            JSValue kValue = safeGetElement(typedArray, k);
            JSValue mappedValue = callbackFn.call(context, callbackThisArg,
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
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.reduce");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.reduce on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        int length = typedArray.getLength();
        int k = 0;
        JSValue accumulator;
        if (args.length > 1) {
            accumulator = args[1];
        } else {
            if (length == 0) {
                return context.throwTypeError("Reduce of empty array with no initial value");
            }
            accumulator = safeGetElement(typedArray, 0);
            k = 1;
        }
        for (; k < length; k++) {
            JSValue kValue = safeGetElement(typedArray, k);
            accumulator = callbackFn.call(context, JSUndefined.INSTANCE,
                    new JSValue[]{accumulator, kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return accumulator;
    }

    public static JSValue reduceRight(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.reduceRight");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.reduceRight on a typed array backed by a detached or out-of-bounds buffer");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callbackFn)) {
            return context.throwTypeError(args.length == 0 || args[0].isUndefined()
                    ? "undefined is not a function"
                    : JSTypeConversions.toString(context, args[0]).value() + " is not a function");
        }
        int length = typedArray.getLength();
        int k = length - 1;
        JSValue accumulator;
        if (args.length > 1) {
            accumulator = args[1];
        } else {
            if (length == 0) {
                return context.throwTypeError("Reduce of empty array with no initial value");
            }
            accumulator = safeGetElement(typedArray, k);
            k--;
        }
        for (; k >= 0; k--) {
            JSValue kValue = safeGetElement(typedArray, k);
            accumulator = callbackFn.call(context, JSUndefined.INSTANCE,
                    new JSValue[]{accumulator, kValue, JSNumber.of(k), typedArray});
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return accumulator;
    }

    public static JSValue reverse(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.reverse");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.reverse on a typed array backed by a detached or out-of-bounds buffer");
        }
        int length = typedArray.getLength();
        int middle = length / 2;
        for (int lower = 0; lower < middle; lower++) {
            int upper = length - 1 - lower;
            JSValue lowerValue = typedArray.getJSElement(lower);
            JSValue upperValue = typedArray.getJSElement(upper);
            typedArray.set(PropertyKey.fromIndex(lower), upperValue);
            typedArray.set(PropertyKey.fromIndex(upper), lowerValue);
        }
        return typedArray;
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

    /**
     * SameValueZero comparison per ES spec.
     * Like === except NaN equals NaN, and +0 equals -0.
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

    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray target = toTypedArray(context, thisArg, "TypedArray.prototype.set");
        if (target == null) {
            return context.getPendingException();
        }
        JSValue source = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

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
        int targetOffset = (int) targetOffsetD;

        // Step 5: Check if target is detached/OOB (after offset coercion)
        if (target.isOutOfBounds()) {
            return context.throwTypeError("TypedArray is detached or out of bounds");
        }
        int targetLength = target.getLength();

        // Step 7: ToObject(source)
        JSObject src = JSTypeConversions.toObject(context, source);
        if (src == null || context.hasPendingException()) {
            return context.hasPendingException() ? context.getPendingException()
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
        if (srcLength + targetOffset > targetLength) {
            return context.throwRangeError("Source is too large");
        }

        boolean isBigInt = isBigIntTypedArray(target);

        // Step 11: Loop - convert values and write
        for (long k = 0; k < srcLength; k++) {
            PropertyKey pk = PropertyKey.fromString(Long.toString(k));
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
            int targetIndex = (int) (targetOffset + k);
            if (!target.getBuffer().isDetached() && !target.isOutOfBounds()
                    && targetIndex < target.getLength()) {
                target.set(PropertyKey.fromIndex(targetIndex), convertedValue);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        }
        return JSUndefined.INSTANCE;
    }

    private static JSValue setFromTypedArray(JSContext context, JSTypedArray target, JSTypedArray source, JSValue[] args) {
        // Step 3: ToIntegerOrInfinity(offset) - may detach buffer
        double targetOffsetD = args.length > 1 ? JSTypeConversions.toInteger(context, args[1]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (targetOffsetD < 0) {
            return context.throwRangeError("offset is out of bounds");
        }
        int targetOffset = (int) targetOffsetD;

        // Step 5: Check if target is OOB (after offset coercion)
        if (target.isOutOfBounds()) {
            return context.throwTypeError("TypedArray is detached or out of bounds");
        }
        int targetLength = target.getLength();

        // Step 9: Check source buffer detached
        if (source.getBuffer().isDetached()) {
            return context.throwTypeError("Source TypedArray buffer is detached");
        }
        // Step 11: Check source OOB
        if (source.isOutOfBounds()) {
            return context.throwTypeError("Source TypedArray is out of bounds");
        }
        int srcLength = source.getLength();

        // Step 13: Range check
        if ((long) srcLength + targetOffset > targetLength) {
            return context.throwRangeError("Source is too large");
        }

        // Step 14: Check content type compatibility
        boolean targetIsBigInt = isBigIntTypedArray(target);
        boolean sourceIsBigInt = isBigIntTypedArray(source);
        if (targetIsBigInt != sourceIsBigInt) {
            return context.throwTypeError("Cannot mix BigInt and non-BigInt typed arrays");
        }

        // Step 15: If same buffer, clone source values first to avoid overlap
        boolean sameBuffer = target.getBuffer() == source.getBuffer();
        if (sameBuffer) {
            // Clone source values into temporary array
            JSValue[] tempValues = new JSValue[srcLength];
            for (int i = 0; i < srcLength; i++) {
                tempValues[i] = source.getJSElement(i);
            }
            for (int i = 0; i < srcLength; i++) {
                target.set(PropertyKey.fromIndex(targetOffset + i), tempValues[i]);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        } else {
            // Different buffers - direct copy
            for (int i = 0; i < srcLength; i++) {
                JSValue value = source.getJSElement(i);
                target.set(PropertyKey.fromIndex(targetOffset + i), value);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
        }
        return JSUndefined.INSTANCE;
    }

    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.slice");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.slice on a typed array backed by a detached or out-of-bounds buffer");
        }
        int length = typedArray.getLength();

        double relativeStart = args.length > 0 ? JSTypeConversions.toInteger(context, args[0]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        int start;
        if (relativeStart < 0) {
            start = Math.max(0, (int) (length + relativeStart));
        } else {
            start = (int) Math.min(relativeStart, length);
        }

        int end;
        if (args.length > 1 && !args[1].isUndefined()) {
            double relativeEnd = JSTypeConversions.toInteger(context, args[1]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (relativeEnd < 0) {
                end = Math.max(0, (int) (length + relativeEnd));
            } else {
                end = (int) Math.min(relativeEnd, length);
            }
        } else {
            end = length;
        }

        int count = Math.max(0, end - start);

        // TypedArraySpeciesCreate
        JSValue result = typedArraySpeciesCreate(context, typedArray, new JSValue[]{JSNumber.of(count)});
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSTypedArray resultArray = (JSTypedArray) result;

        if (count > 0) {
            if (typedArray.isOutOfBounds()) {
                return context.throwTypeError("Cannot perform TypedArray.prototype.slice on a typed array backed by a detached or out-of-bounds buffer");
            }
            // Re-read length after species constructor may have resized (step 14c)
            int currentLength = typedArray.getLength();
            int newEnd = Math.min(end, currentLength);
            int srcCount = Math.max(0, newEnd - start);

            if (srcCount > 0 && !typedArray.getBuffer().isDetached() && !resultArray.getBuffer().isDetached()) {
                int elementSize = typedArray.getBytesPerElement();
                IJSArrayBuffer srcBuffer = typedArray.getBuffer();
                IJSArrayBuffer dstBuffer = resultArray.getBuffer();

                if (typedArray.getClass() == resultArray.getClass()) {
                    // Same type: byte-by-byte copy per spec step 14g
                    int srcByteOffset = typedArray.getByteOffset() + start * elementSize;
                    int dstByteOffset = resultArray.getByteOffset();
                    int byteCount = srcCount * elementSize;

                    if (srcBuffer == dstBuffer) {
                        // Same buffer: forward byte-by-byte copy per spec (step 14g.ix)
                        // This intentionally allows overlapping writes to produce spec-defined results
                        ByteBuffer buf = srcBuffer.getBuffer().duplicate();
                        buf.order(srcBuffer.getBuffer().order());
                        for (int i = 0; i < byteCount; i++) {
                            byte b = buf.get(srcByteOffset + i);
                            buf.put(dstByteOffset + i, b);
                        }
                    } else {
                        // Different buffers: bulk copy
                        ByteBuffer src = srcBuffer.getBuffer().duplicate();
                        src.order(srcBuffer.getBuffer().order());
                        ByteBuffer dst = dstBuffer.getBuffer().duplicate();
                        dst.order(dstBuffer.getBuffer().order());
                        byte[] temp = new byte[byteCount];
                        src.position(srcByteOffset);
                        src.get(temp, 0, byteCount);
                        dst.position(dstByteOffset);
                        dst.put(temp, 0, byteCount);
                    }
                } else {
                    // Different types: per-element copy (step 14h), limited by srcCount
                    for (int k = 0; k < srcCount; k++) {
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
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.some");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.some on a typed array backed by a detached or out-of-bounds buffer");
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
                return JSBoolean.TRUE;
            }
        }
        return JSBoolean.FALSE;
    }

    public static JSValue sort(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.sort");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.sort on a typed array backed by a detached or out-of-bounds buffer");
        }
        JSValue compareArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(compareArg instanceof JSUndefined)) {
            if (!JSTypeChecking.isCallable(compareArg)) {
                return context.throwTypeError("compareFn is not a function");
            }
        }
        int length = typedArray.getLength();
        if (length <= 1) {
            return typedArray;
        }

        // Collect elements into array
        JSValue[] elements = new JSValue[length];
        for (int i = 0; i < length; i++) {
            elements[i] = typedArray.getJSElement(i);
        }

        // Sort
        final JSValue finalCompareArg = compareArg;
        final boolean[] hasError = {false};
        try {
            Arrays.sort(elements, (a, b) -> {
                if (hasError[0]) {
                    return 0;
                }
                if (!(finalCompareArg instanceof JSUndefined)) {
                    JSValue result = callCallable(context, finalCompareArg,
                            JSUndefined.INSTANCE, new JSValue[]{a, b});
                    if (context.hasPendingException()) {
                        hasError[0] = true;
                        return 0;
                    }
                    double d = JSTypeConversions.toNumber(context, result).value();
                    if (Double.isNaN(d)) {
                        return 0;
                    }
                    return (int) Math.signum(d);
                }
                return compareTypedArrayElements(a, b);
            });
        } catch (IllegalArgumentException e) {
            // TimSort may throw if comparison is inconsistent, just ignore
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Write back
        for (int i = 0; i < length; i++) {
            typedArray.set(PropertyKey.fromIndex(i), elements[i]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return typedArray;
    }

    public static JSValue subarray(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.subarray");
        if (typedArray == null) {
            return context.getPendingException();
        }

        // Step 5-7: Get srcLength (0 if OOB)
        int srcLength = typedArray.getLength();

        // Step 8-10: Compute startIndex using toInteger (handles Infinity)
        double relativeStart = args.length > 0 ? JSTypeConversions.toInteger(context, args[0]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        int startIndex;
        if (relativeStart < 0) {
            startIndex = Math.max(0, (int) (srcLength + relativeStart));
        } else {
            startIndex = (int) Math.min(relativeStart, srcLength);
        }

        // Determine if end is undefined (for length-tracking detection)
        boolean endIsUndefined = args.length <= 1 || args[1].isUndefined();

        // Steps 11-12: Compute endIndex
        int endIndex;
        if (!endIsUndefined) {
            double relativeEnd = JSTypeConversions.toInteger(context, args[1]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (relativeEnd < 0) {
                endIndex = Math.max(0, (int) (srcLength + relativeEnd));
            } else {
                endIndex = (int) Math.min(relativeEnd, srcLength);
            }
        } else {
            endIndex = srcLength;
        }

        // Step 13-14: Compute beginByteOffset using stored byteOffset (preserved even when OOB)
        int elementSize = typedArray.getBytesPerElement();
        int srcByteOffset = typedArray.getByteOffset();
        int beginByteOffset = srcByteOffset + startIndex * elementSize;

        // SpeciesConstructor(O, defaultConstructor)
        IJSArrayBuffer buffer = typedArray.getBuffer();
        JSValue defaultConstructor = context.getGlobalObject().get(PropertyKey.fromString(typedArray.getTypedArrayName()));
        JSValue constructorValue = typedArray.get(PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSValue chosenConstructor;
        if (constructorValue instanceof JSUndefined) {
            chosenConstructor = defaultConstructor;
        } else if (!(constructorValue instanceof JSObject constructorObj)) {
            return context.throwTypeError("constructor is not an object");
        } else {
            JSValue species = constructorObj.get(PropertyKey.SYMBOL_SPECIES);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (species instanceof JSUndefined || species instanceof JSNull) {
                chosenConstructor = defaultConstructor;
            } else {
                chosenConstructor = species;
            }
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
            int newLength = Math.max(0, endIndex - startIndex);
            constructArgs = new JSValue[]{bufferArg, JSNumber.of(beginByteOffset), JSNumber.of(newLength)};
        }

        // Step 17: TypedArraySpeciesCreate
        JSValue result = JSReflectObject.constructSimple(context, chosenConstructor, constructArgs);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Validate result is a TypedArray (TypedArrayCreate step 2: ValidateTypedArray)
        if (!(result instanceof JSTypedArray)) {
            return context.throwTypeError("TypedArray species constructor did not return a TypedArray");
        }

        return result;
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.toLocaleString");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.toLocaleString on a typed array backed by a detached or out-of-bounds buffer");
        }
        int length = typedArray.getLength();

        String separator = ",";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
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
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.toReversed");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.toReversed on a typed array backed by a detached or out-of-bounds buffer");
        }
        int length = typedArray.getLength();

        // Create new typed array of same type (not species - ignores species per spec)
        JSValue constructor = context.getGlobalObject().get(PropertyKey.fromString(typedArray.getTypedArrayName()));
        JSValue result = JSReflectObject.constructSimple(context, constructor,
                new JSValue[]{JSNumber.of(length)});
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(result instanceof JSTypedArray resultArray)) {
            return context.throwTypeError("TypedArray constructor did not return a TypedArray");
        }

        for (int k = 0; k < length; k++) {
            JSValue fromValue = typedArray.getJSElement(length - 1 - k);
            resultArray.set(PropertyKey.fromIndex(k), fromValue);
        }
        return resultArray;
    }

    public static JSValue toSorted(JSContext context, JSValue thisArg, JSValue[] args) {
        // Step 1: Validate compareFn first (before this-value validation per spec)
        JSValue compareArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(compareArg instanceof JSUndefined)) {
            if (!JSTypeChecking.isCallable(compareArg)) {
                return context.throwTypeError("compareFn is not a function");
            }
        }

        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.toSorted");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.toSorted on a typed array backed by a detached or out-of-bounds buffer");
        }
        int length = typedArray.getLength();

        // Create new typed array of same type (not species - ignores species per spec)
        JSValue constructor = context.getGlobalObject().get(PropertyKey.fromString(typedArray.getTypedArrayName()));
        JSValue result = JSReflectObject.constructSimple(context, constructor,
                new JSValue[]{JSNumber.of(length)});
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(result instanceof JSTypedArray resultArray)) {
            return context.throwTypeError("TypedArray constructor did not return a TypedArray");
        }

        // Collect elements
        JSValue[] elements = new JSValue[length];
        for (int i = 0; i < length; i++) {
            elements[i] = typedArray.getJSElement(i);
        }

        // Sort
        final JSValue finalCompareArg = compareArg;
        final boolean[] hasError = {false};
        try {
            Arrays.sort(elements, (a, b) -> {
                if (hasError[0]) {
                    return 0;
                }
                if (!(finalCompareArg instanceof JSUndefined)) {
                    JSValue cmpResult = callCallable(context, finalCompareArg,
                            JSUndefined.INSTANCE, new JSValue[]{a, b});
                    if (context.hasPendingException()) {
                        hasError[0] = true;
                        return 0;
                    }
                    double d = JSTypeConversions.toNumber(context, cmpResult).value();
                    if (Double.isNaN(d)) {
                        return 0;
                    }
                    return (int) Math.signum(d);
                }
                return compareTypedArrayElements(a, b);
            });
        } catch (IllegalArgumentException e) {
            // TimSort may throw if comparison is inconsistent
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Write into result
        for (int i = 0; i < elements.length; i++) {
            resultArray.set(PropertyKey.fromIndex(i), elements[i]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return resultArray;
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
     * TypedArraySpeciesCreate per ES2024 23.2.4.1.
     * Creates a new TypedArray using the species constructor pattern.
     * Includes TypedArrayCreate length validation (step 3a).
     */
    private static JSValue typedArraySpeciesCreate(JSContext context, JSTypedArray exemplar, JSValue[] args) {
        // Get the default constructor for this typed array type
        JSValue defaultConstructor = context.getGlobalObject().get(PropertyKey.fromString(exemplar.getTypedArrayName()));

        // SpeciesConstructor(exemplar, defaultConstructor)
        JSValue constructorValue = exemplar.get(PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        JSValue chosenConstructor;
        if (constructorValue instanceof JSUndefined) {
            chosenConstructor = defaultConstructor;
        } else if (!(constructorValue instanceof JSObject constructorObj)) {
            return context.throwTypeError("constructor is not an object");
        } else {
            JSValue species = constructorObj.get(PropertyKey.SYMBOL_SPECIES);
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

    public static JSValue withMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTypedArray typedArray = toTypedArray(context, thisArg, "TypedArray.prototype.with");
        if (typedArray == null) {
            return context.getPendingException();
        }
        if (typedArray.isOutOfBounds()) {
            return context.throwTypeError("Cannot perform TypedArray.prototype.with on a typed array backed by a detached or out-of-bounds buffer");
        }
        // Step 3: Snapshot length
        int length = typedArray.getLength();

        // Step 4: ToIntegerOrInfinity(index)
        double relativeIndex = args.length > 0 ? JSTypeConversions.toInteger(context, args[0]) : 0;
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // Steps 5-6: Compute actualIndex using ORIGINAL length
        int actualIndex;
        if (relativeIndex >= 0) {
            actualIndex = (int) relativeIndex;
        } else {
            actualIndex = (int) (length + relativeIndex);
        }

        // Steps 7-8: Convert value (may resize/detach buffer)
        JSValue value = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
        JSValue constructor = context.getGlobalObject().get(PropertyKey.fromString(typedArray.getTypedArrayName()));
        JSValue result = JSReflectObject.constructSimple(context, constructor,
                new JSValue[]{JSNumber.of(length)});
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(result instanceof JSTypedArray resultArray)) {
            return context.throwTypeError("TypedArray constructor did not return a TypedArray");
        }

        // Steps 11-12: Copy elements using original length
        for (int k = 0; k < length; k++) {
            if (k == actualIndex) {
                resultArray.set(PropertyKey.fromIndex(k), convertedValue);
            } else {
                JSValue fromValue = safeGetElement(typedArray, k);
                resultArray.set(PropertyKey.fromIndex(k), fromValue);
            }
        }
        return resultArray;
    }
}
