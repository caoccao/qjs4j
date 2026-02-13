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
    private TypedArrayConstructor() {
    }

    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwTypeError("cannot be called");
    }

    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSConstructorType constructorType = getTypedArrayConstructorType(context, thisArg, "TypedArray.from");
        if (constructorType == null) {
            return context.getPendingException();
        }
        JSObject constructor = (JSObject) thisArg;
        JSValue items = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue mapFnValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue thisArgValue = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        if (!(mapFnValue instanceof JSFunction mapFn)) {
            if (!(mapFnValue instanceof JSUndefined)) {
                return context.throwTypeError("TypedArray.from mapFn must be a function");
            }
            JSObject result = constructorType.create(context, items);
            context.transferPrototype(result, constructor);
            return result;
        }

        JSArray sourceArray;
        if (items instanceof JSIterator jsIterator) {
            sourceArray = JSIteratorHelper.toArray(context, jsIterator);
        } else if (items instanceof JSArray jsArray) {
            sourceArray = jsArray;
        } else if (items instanceof JSObject jsObject) {
            int length = (int) JSTypeConversions.toLength(context, JSTypeConversions.toNumber(context, jsObject.get("length")));
            sourceArray = context.createJSArray(length, length);
            for (int i = 0; i < length; i++) {
                sourceArray.set(i, jsObject.get(i));
            }
        } else {
            return context.throwTypeError("TypedArray.from source must be iterable or array-like");
        }

        int length = (int) sourceArray.getLength();
        JSArray mappedArray = context.createJSArray(length, length);
        for (int i = 0; i < length; i++) {
            JSValue mappedValue = mapFn.call(context, thisArgValue, new JSValue[]{sourceArray.get(i), new JSNumber(i)});
            mappedArray.set(i, mappedValue);
        }
        JSObject result = constructorType.create(context, mappedArray);
        context.transferPrototype(result, constructor);
        return result;
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
}
