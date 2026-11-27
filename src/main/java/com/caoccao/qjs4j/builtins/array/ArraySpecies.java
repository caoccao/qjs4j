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

/** Array-producing operations that honor the receiver’s Symbol.species constructor. */
final class ArraySpecies {
    private ArraySpecies() {
    }

    private static boolean appendConcat(JSContext context, JSObject target, long index, JSValue value) {
        if (target instanceof JSArray array) {
            array.set(index, value);
            return !context.hasPendingException();
        }
        return define(context, target, index, value);
    }

    static JSValue concat(JSContext context, JSValue thisArg, JSValue[] args) {
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
                long elementLength = ArrayPrototype.lengthOfArrayLike(context, elementObj);
                if (elementLength < 0) {
                    return context.getPendingException();
                }

                if (resultIndex + elementLength > NumberPrototype.MAX_SAFE_INTEGER) { // MAX_SAFE_INTEGER
                    return context.throwTypeError("Array too long");
                }

                for (long k = 0; k < elementLength; k++, resultIndex++) {

                    context.getVirtualMachine().checkExecutionInterruptForNative();
                    JSValue value = ArrayLikeElements.getPresent(context, elementObj, k);
                    if (context.hasPendingException()
                            || value != null && !appendConcat(context, resultObj, resultIndex, value)) {
                        return context.getPendingException();
                    }
                }
            } else {
                // Step 4c: Non-spreadable - add as single element
                if (resultIndex >= NumberPrototype.MAX_SAFE_INTEGER) {
                    return context.throwTypeError("Array too long");
                }
                if (!appendConcat(context, resultObj, resultIndex, element)) {
                    return context.getPendingException();
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

    static JSObject createSpeciesObject(JSContext context, JSObject source, long length) {
        JSValue result = context.createJSArraySpecies(source, length);
        if (result == null || context.hasPendingException()) {
            return null;
        }
        if (result instanceof JSObject object) {
            return object;
        }
        return context.rejectTypeError("ArraySpeciesCreate did not return an object");
    }

    static boolean define(JSContext context, JSObject target, long index, JSValue value) {
        if (target.defineProperty(PropertyKey.fromIndexString(index), value, PropertyDescriptor.DataState.All)) {
            return true;
        }
        if (!context.hasPendingException()) {
            context.throwTypeError("Cannot define property " + index);
        }
        return false;
    }

    static JSValue flatten(JSContext context, JSValue thisArg, JSValue[] args, boolean map) {
        JSObject obj = ArrayPrototype.requireArrayLike(context, thisArg, map ? "flatMap" : "flat");
        long length = ArrayPrototype.checkedLength(context, obj);
        if (length < 0) {
            return context.getPendingException();
        }

        int depth = 1;
        JSValue mapper = null;
        JSValue callbackThis = JSUndefined.INSTANCE;
        if (map) {
            if (args.length == 0 || !JSTypeChecking.isCallable(args[0])) {
                return context.throwTypeError("Array.prototype.flatMap requires a callback function");
            }
            mapper = args[0];
            callbackThis = JSValue.argumentOrUndefined(args, 1);
        } else if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            double doubleValue = JSTypeConversions.toInteger(context, args[0]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            depth = doubleValue >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(doubleValue, 0);
        }

        // Step 4: Let A be ? ArraySpeciesCreate(O, 0).
        JSObject resultObj = createSpeciesObject(context, obj, 0);
        if (resultObj == null) {
            return context.hasPendingException() ? context.getPendingException() : JSUndefined.INSTANCE;
        }

        // Step 5: Perform ? FlattenIntoArray(A, O, sourceLen, 0, depthNum).
        internalFlattenIntoObject(context, obj, length, depth, resultObj, 0, mapper, callbackThis);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        return resultObj;
    }

    private static long internalFlattenIntoObject(JSContext context, JSObject source, long sourceLen, int depth,
            JSObject target, long targetIndex, JSValue mapper, JSValue mapperThis) {
        for (long i = 0; i < sourceLen; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue element = ArrayLikeElements.getPresent(context, source, i);
            if (context.hasPendingException()) {
                return targetIndex;
            }
            if (element == null) {
                continue;
            }
            if (mapper != null) {
                element = ArrayLikeElements.callElement(context, mapper, mapperThis, element, i, source);
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
                    long elementLen = ArrayPrototype.lengthOfArrayLike(context, elementObj);
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
            if (!define(context, target, targetIndex, element)) {
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

    static JSValue mapOrFilter(JSContext context, JSValue thisArg, JSValue[] args, boolean filter) {
        ArrayLikeElements elements = ArrayLikeElements.arrayCallback(context, thisArg, args, filter ? "filter" : "map");
        if (elements == null) {
            return context.getPendingException();
        }
        JSObject obj = elements.object;
        long length = elements.length;
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
            JSValue element = elements.getPresent(i);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (element == null) {
                continue;
            }
            JSValue mapped = ArrayLikeElements.callElement(context, callback, callbackThis, element, i, obj);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (filter) {
                if (JSTypeConversions.toBoolean(mapped) != JSBoolean.TRUE) {
                    continue;
                }

                mapped = element;
            }
            if (!define(context, resultObj, filter ? resultIndex++ : i, mapped)) {
                return context.getPendingException();
            }
        }
        return resultObj;
    }
}
