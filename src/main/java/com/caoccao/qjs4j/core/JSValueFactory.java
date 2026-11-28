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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.core.buffer.*;
import com.caoccao.qjs4j.core.date.JSDate;
import com.caoccao.qjs4j.core.error.*;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.core.regexp.JSRegExp;

/**
 * Allocates the realm's built-in objects with their prototypes already attached.
 * <p>
 * The public factories on {@link JSContext} use the shared initializers here for prototype installation and stack-trace
 * capture. Allocations that depend on cached intrinsics or run JavaScript use the realm directly.
 * <p>
 * The realm state it needs is read back through {@code context}: the cached {@code Object}, {@code Date},
 * {@code Promise} and {@code RegExp} prototypes for the hot paths, and {@code transferPrototype} for everything else.
 */
final class JSValueFactory {
    private final JSContext context;

    JSValueFactory(JSContext context) {
        this.context = context;
    }

    private JSValue createDefaultSpeciesArray(long length) {
        if (length > 0xFFFFFFFFL) {
            return context.throwRangeError("Invalid array length");
        }
        return context.createJSArray(length, 0);
    }

    JSArray createJSArray() {
        return createJSArray(0);
    }

    JSArray createJSArray(long length) {
        // A JavaScript array length is a uint32, so narrowing it to int for the capacity hint can
        // produce a negative value (4294967295L narrows to -1) and a NegativeArraySizeException.
        // Clamp instead: the hint is only a dense-storage sizing suggestion. The length itself is
        // validated by the JSArray constructor, which rejects anything outside [0, 2^32 - 1].
        return context.createJSArray(length, (int) Math.min(Math.max(length, 0), JSArray.MAX_DENSE_SIZE));
    }

    JSValue createJSArraySpecies(JSObject originalArray, long length) {
        // Step 3: If IsArray(originalArray) is false, return ArrayCreate(length)
        int isArr = JSTypeChecking.isArray(context, originalArray);
        if (isArr < 0) {
            return null;
        }
        if (isArr == 0) {
            return createDefaultSpeciesArray(length);
        }

        // Step 4: Let C be ? Get(originalArray, "constructor")
        JSValue ctor = originalArray.get(PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return null;
        }

        // Step 5: If IsConstructor(C) is true, cross-realm check.
        // ES2024 10.4.2.3 ArraySpeciesCreate step 6.a-c:
        // if constructor realm differs and C is that realm's intrinsic %Array%,
        // treat C as undefined.
        if (JSTypeChecking.isConstructor(ctor) && ctor instanceof JSObject ctorObject) {
            JSContext constructorRealm = context.getFunctionRealm(ctorObject);
            if (context.hasPendingException()) {
                return null;
            }
            if (constructorRealm != context) {
                JSValue realmArrayConstructor = constructorRealm.getGlobalObject().get(JSArray.NAME);
                if (ctor == realmArrayConstructor) {
                    ctor = JSUndefined.INSTANCE;
                }
            }
        }

        // Step 6: If Type(C) is Object, get Symbol.species
        if (ctor instanceof JSObject ctorObj) {
            ctor = ctorObj.get(PropertyKey.SYMBOL_SPECIES);
            if (context.hasPendingException()) {
                return null;
            }
            if (ctor instanceof JSNull) {
                ctor = JSUndefined.INSTANCE;
            }
        }

        // Step 7: If C is undefined, return ArrayCreate(length)
        if (ctor instanceof JSUndefined) {
            return createDefaultSpeciesArray(length);
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

    JSDate createJSDate(double timeValue) {
        JSDate jsDate = new JSDate(context, timeValue);
        return initializePrototype(jsDate, context.getCachedDatePrototype(), JSDate.NAME);
    }

    JSObject createJSObject() {
        JSObject jsObject = new JSObject(context);
        return initializePrototype(jsObject, context.getObjectPrototype(), JSObject.NAME);
    }

    JSPromise createJSPromise() {
        JSPromise jsPromise = new JSPromise(context);
        return initializePrototype(jsPromise, context.getCachedPromisePrototype(), JSPromise.NAME);
    }

    JSRegExp createJSRegExp(String pattern, String flags) {
        JSRegExp jsRegExp = new JSRegExp(context, pattern, flags);
        return initializePrototype(jsRegExp, context.getCachedRegExpPrototype(), JSRegExp.NAME);
    }

    <T extends JSError> T initializeError(T error) {
        context.transferPrototype(error, error.getErrorName());
        context.captureStackTrace(error);
        return error;
    }

    private <T extends JSObject> T initializePrototype(T value, JSObject cached, String constructorName) {
        if (cached != null) {
            value.setPrototype(cached);
            return value;
        }
        return initializePrototype(value, constructorName);
    }

    <T extends JSObject> T initializePrototype(T value, String constructorName) {
        context.transferPrototype(value, constructorName);
        return value;
    }

    <T extends JSTypedArray> T initializeTypedArray(T typedArray) {
        context.transferPrototype(typedArray, typedArray.getTypedArrayName());
        var buffer = typedArray.getBuffer();
        if (buffer instanceof JSObject jsObject && jsObject.getPrototype() == null) {
            context.transferPrototype(jsObject, buffer.isShared() ? JSSharedArrayBuffer.NAME : JSArrayBuffer.NAME);
        }
        return typedArray;
    }
}
