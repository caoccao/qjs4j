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

import com.caoccao.qjs4j.builtins.promise.PromiseConstructor;
import com.caoccao.qjs4j.core.*;

/** Constructing and populating arrays from iterables and array-like objects. */
final class ArrayFrom {
    private ArrayFrom() {
    }

    static JSObject construct(JSContext context, JSValue constructor, JSValue[] arguments, long defaultLength,
            String method) {
        if (!JSTypeChecking.isConstructor(constructor)) {
            return context.createJSArray(defaultLength);
        }
        JSValue target = JSReflectObject.constructSimple(context, constructor, arguments);
        if (context.hasPendingException()) {
            return null;
        }
        if (target instanceof JSObject object) {
            return object;
        }
        return context.rejectTypeError(method + " constructor must return an object");
    }

    static boolean copyMapped(JSContext context, JSObject source, JSObject target, long length, JSFunction mapper,
            JSValue thisArg, boolean typedTarget) {
        for (long i = 0; i < length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            PropertyKey key = PropertyKey.fromIndexString(i);
            JSValue value = source.get(key);
            if (context.hasPendingException()) {
                return false;
            }
            value = map(context, mapper, thisArg, value, i);
            if (context.hasPendingException()) {
                return false;
            }
            if (typedTarget) {
                target.set(key, value);
                if (context.hasPendingException()) {
                    return false;
                }
            } else if (!define(context, target, i, value)) {
                return false;
            }
        }
        return true;
    }

    static boolean define(JSContext context, JSObject target, long index, JSValue value) {
        if (!target.defineProperty(PropertyKey.fromIndexString(index), value, PropertyDescriptor.DataState.All)
                && !context.hasPendingException()) {
            context.throwTypeError("Cannot define property " + index + " on result object");
        }
        return !context.hasPendingException();
    }

    private static JSValue finish(JSContext context, JSObject target, long length) {
        if (!target.setWithResult(PropertyKey.LENGTH, JSNumber.of(length)) && !context.hasPendingException()) {
            return context.throwTypeError("Cannot set property length of result object");
        }
        return context.hasPendingException() ? context.getPendingException() : target;
    }

    static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue items = JSValue.argumentOrUndefined(args, 0);
        JSFunction mapFn = null;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            if (!(args[1] instanceof JSFunction)) {
                return context.throwTypeError("Array.from: when provided, the second argument must be a function");
            }
            mapFn = (JSFunction) args[1];
        }
        JSValue mapThisArg = JSValue.argumentOrUndefined(args, 2);
        JSObject itemsObject = ArrayPrototype.toObjectChecked(context, items);
        if (itemsObject == null || context.hasPendingException()) {
            return context.getPendingException();
        }
        JSFunction iteratorFunction = iteratorMethod(context, itemsObject, PropertyKey.SYMBOL_ITERATOR, items,
                "value is not iterable");
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (iteratorFunction != null) {
            JSObject targetArray = construct(context, thisArg, JSValue.NO_ARGS, 0, "Array.from");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            JSValue iteratorValue = iteratorFunction.call(context, items, JSValue.NO_ARGS);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(iteratorValue instanceof JSObject iteratorObject)) {
                return context.throwTypeError("iterator must be an object");
            }
            JSValue nextMethodValue = iteratorObject.get(PropertyKey.NEXT);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(nextMethodValue instanceof JSFunction nextFunction)) {
                return context.throwTypeError("iterator next is not a function");
            }

            long k = 0;
            while (true) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                JSValue nextResultValue = nextFunction.call(context, iteratorObject, JSValue.NO_ARGS);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (!(nextResultValue instanceof JSObject nextResultObject)) {
                    return context.throwTypeError("iterator result is not an object");
                }
                JSValue doneValue = nextResultObject.get(PropertyKey.DONE);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (JSTypeConversions.toBoolean(doneValue).isBooleanTrue()) {
                    break;
                }
                JSValue mappedValue = nextResultObject.get(PropertyKey.VALUE);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                try {
                    mappedValue = map(context, mapFn, mapThisArg, mappedValue, k);
                    if (context.hasPendingException() || !define(context, targetArray, k, mappedValue)) {
                        return iteratorCloseAndRestoreException(context, iteratorObject, context.getPendingException());
                    }
                } catch (Exception e) {
                    return iteratorCloseAndRestoreException(context, iteratorObject, context.takeRejectionReason(e));
                }
                k++;
            }
            return finish(context, targetArray, k);
        }

        long length = ArrayPrototype.lengthOfArrayLike(context, itemsObject);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSObject target = construct(context, thisArg, new JSValue[]{JSNumber.of(length)}, 0, "Array.from");
        if (target == null || !copyMapped(context, itemsObject, target, length, mapFn, mapThisArg, false)) {
            return context.getPendingException();
        }
        return finish(context, target, length);
    }

    private static JSValue iteratorCloseAndRestoreException(JSContext context, JSObject iteratorObject,
            JSValue originalException) {
        context.clearPendingException();
        JSValue returnMethod = iteratorObject.get(PropertyKey.RETURN);
        if (!context.hasPendingException() && JSTypeChecking.isCallable(returnMethod)) {
            PromiseConstructor.callCallable(context, returnMethod, iteratorObject, JSValue.NO_ARGS);
        }
        context.clearPendingException();
        context.setPendingException(originalException);
        return originalException;
    }

    static JSFunction iteratorMethod(JSContext context, JSObject object, PropertyKey key, JSValue receiver,
            String error) {
        JSValue method = object.get(key, receiver);
        if (context.hasPendingException() || method == null || method.isNullOrUndefined()) {
            return null;
        }
        if (method instanceof JSFunction function) {
            return function;
        }
        return context.rejectTypeError(error);
    }

    static JSValue map(JSContext context, JSFunction mapper, JSValue thisArg, JSValue value, long index) {
        return mapper == null ? value : mapper.call(context, thisArg, new JSValue[]{value, JSNumber.of(index)});
    }

    static JSValue of(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject target = construct(context, thisArg, new JSValue[]{JSNumber.of(args.length)}, 0, "Array.of");
        if (target == null) {
            return context.getPendingException();
        }
        for (int i = 0; i < args.length; i++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            if (!define(context, target, i, args[i])) {
                return context.getPendingException();
            }
        }
        return finish(context, target, args.length);
    }
}
