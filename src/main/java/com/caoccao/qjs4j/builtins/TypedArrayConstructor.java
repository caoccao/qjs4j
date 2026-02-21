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

public final class TypedArrayConstructor {
    private static final JSValue[] NO_ARGS = new JSValue[0];

    private TypedArrayConstructor() {
    }

    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwTypeError("cannot be called");
    }

    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("TypedArray.from called on non-TypedArray constructor");
        }

        JSValue items = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue mapFnValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue thisArgValue = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        // Validate mapfn
        JSFunction mapFn = null;
        if (!(mapFnValue instanceof JSUndefined)) {
            if (!(mapFnValue instanceof JSFunction mapFnFunc)) {
                return context.throwTypeError("TypedArray.from mapFn must be a function");
            }
            mapFn = mapFnFunc;
        }

        JSArray iterableValues = null;
        JSObject arrayLike = null;
        long itemsLength;
        JSObject itemsObject = JSTypeConversions.toObject(context, items);
        if (itemsObject == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        JSValue iteratorMethod = itemsObject.get(context, PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(iteratorMethod instanceof JSUndefined) && !(iteratorMethod instanceof JSNull)) {
            if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
                return context.throwTypeError("value is not iterable");
            }
            JSValue iterator = iteratorFunction.call(context, items, NO_ARGS);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(iterator instanceof JSObject iteratorObject)) {
                return context.throwTypeError("Result of the Symbol.iterator method is not an object");
            }
            iterableValues = context.createJSArray();
            while (true) {
                JSObject iterResult = JSIteratorHelper.iteratorNext(iteratorObject, context);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (iterResult == null) {
                    break;
                }
                JSValue doneValue = iterResult.get(context, PropertyKey.DONE);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                    break;
                }
                JSValue value = iterResult.get(context, PropertyKey.VALUE);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                iterableValues.push(value);
            }
            itemsLength = iterableValues.getLength();
        } else {
            arrayLike = itemsObject;
            JSValue lengthValue = arrayLike.get(context, PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            itemsLength = JSTypeConversions.toLength(context, lengthValue);
        }

        JSTypedArray target = typedArrayCreate(context, thisArg, itemsLength, "TypedArray.from");
        if (target == null) {
            return context.getPendingException();
        }

        for (long k = 0; k < itemsLength; k++) {
            PropertyKey key = PropertyKey.fromString(Long.toString(k));
            JSValue kValue = iterableValues != null
                    ? iterableValues.get(context, key)
                    : arrayLike.get(context, key);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (mapFn != null) {
                kValue = mapFn.call(context, thisArgValue, new JSValue[]{kValue, JSNumber.of(k)});
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
            target.set(key, kValue, context);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }
        return target;
    }

    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    private static JSConstructorType getTypedArrayConstructorType(JSContext context, JSValue thisArg, String methodName) {
        if (thisArg instanceof JSObject jsObject) {
            JSConstructorType constructorType = jsObject.getConstructorType();
            if (constructorType != null && constructorType.name().startsWith("TYPED_ARRAY_")) {
                return constructorType;
            }
        }
        context.throwTypeError(methodName + " called on non-TypedArray constructor");
        return null;
    }

    public static JSValue of(JSContext context, JSValue thisArg, JSValue[] args) {
        JSConstructorType constructorType = getTypedArrayConstructorType(context, thisArg, "TypedArray.of");
        if (constructorType == null) {
            return context.getPendingException();
        }
        JSObject constructor = (JSObject) thisArg;
        JSArray sourceArray = context.createJSArray(args);
        JSObject result = constructorType.create(context, sourceArray);
        context.transferPrototype(result, constructor);
        return result;
    }

    private static JSTypedArray typedArrayCreate(JSContext context, JSValue constructor, long length, String methodName) {
        if (length > Integer.MAX_VALUE) {
            context.throwRangeError("invalid array length");
            return null;
        }
        JSValue target = JSReflectObject.constructSimple(context, constructor, new JSValue[]{JSNumber.of(length)});
        if (context.hasPendingException()) {
            return null;
        }
        if (!(target instanceof JSTypedArray typedArray)) {
            context.throwTypeError(methodName + " constructor must return a TypedArray");
            return null;
        }
        if (typedArray.getLength() < length) {
            context.throwTypeError("TypedArray length is too small");
            return null;
        }
        return typedArray;
    }
}
