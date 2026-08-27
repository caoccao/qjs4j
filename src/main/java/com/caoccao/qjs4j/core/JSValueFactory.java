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

/**
 * Allocates the realm's built-in objects with their prototypes already attached.
 * <p>
 * Every {@code createJSXxx} here is reached through the {@link JSContext} method of the same name,
 * which delegates. Those wrappers are the stable surface — roughly two thousand call sites in
 * builtins, the VM and the tests use them — so they are permanent delegations rather than
 * deprecations, and this class stays package-private behind them.
 * <p>
 * The realm state it needs is read back through {@code context}: the cached {@code Object},
 * {@code Date}, {@code Promise} and {@code RegExp} prototypes for the hot paths, and
 * {@code transferPrototype} for everything else.
 */
final class JSValueFactory {
    private final JSContext context;

    JSValueFactory(JSContext context) {
        this.context = context;
    }

    JSAggregateError createJSAggregateError(String message) {
        JSAggregateError jsError = new JSAggregateError(context, message);
        context.transferPrototype(jsError, JSAggregateError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    /**
     * Create a new JSArray with proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @return A new JSArray instance with prototype set
     */
    JSArray createJSArray() {
        return createJSArray(0);
    }

    /**
     * Create a new JSArray with specified length and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param length Initial length of the array; must be in {@code [0, 2^32 - 1]}
     * @return A new JSArray instance with prototype set
     * @throws com.caoccao.qjs4j.exceptions.JSRangeErrorException when {@code length} is not a valid
     *                                                            ECMAScript array length
     */
    JSArray createJSArray(long length) {
        // A JavaScript array length is a uint32, so narrowing it to int for the capacity hint can
        // produce a negative value (4294967295L narrows to -1) and a NegativeArraySizeException.
        // Clamp instead: the hint is only a dense-storage sizing suggestion. The length itself is
        // validated by the JSArray constructor, which rejects anything outside [0, 2^32 - 1].
        return createJSArray(length, (int) Math.min(Math.max(length, 0), JSArray.MAX_DENSE_SIZE));
    }

    /**
     * Create a new JSArray with specified length, capacity, and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param length   Initial length of the array; must be in {@code [0, 2^32 - 1]}
     * @param capacity Initial capacity of the array
     * @return A new JSArray instance with prototype set
     * @throws com.caoccao.qjs4j.exceptions.JSRangeErrorException when {@code length} is not a valid
     *                                                            ECMAScript array length
     */
    JSArray createJSArray(long length, int capacity) {
        JSArray jsArray = new JSArray(context, length, capacity);
        context.transferPrototype(jsArray, JSArray.NAME);
        return jsArray;
    }

    /**
     * Create a new JSArray with specified values and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param values Initial values of the array
     * @return A new JSArray instance with prototype set
     */
    JSArray createJSArray(JSValue... values) {
        JSArray jsArray = new JSArray(context, values);
        context.transferPrototype(jsArray, JSArray.NAME);
        return jsArray;
    }

    /**
     * Create a new JSArray taking ownership of a freshly allocated values array.
     * Internal fast path to avoid an extra defensive copy in hot built-in paths.
     */
    JSArray createJSArray(JSValue[] values, boolean takeOwnership) {
        JSArray jsArray = new JSArray(context, values, takeOwnership);
        context.transferPrototype(jsArray, JSArray.NAME);
        return jsArray;
    }

    /**
     * Create a new JSArrayBuffer with proper prototype chain.
     *
     * @param byteLength The length in bytes
     * @return A new JSArrayBuffer instance with prototype set
     */
    JSArrayBuffer createJSArrayBuffer(int byteLength) {
        JSArrayBuffer jsArrayBuffer = new JSArrayBuffer(context, byteLength);
        context.transferPrototype(jsArrayBuffer, JSArrayBuffer.NAME);
        return jsArrayBuffer;
    }

    /**
     * Create a new resizable JSArrayBuffer with proper prototype chain.
     *
     * @param byteLength    The initial length in bytes
     * @param maxByteLength The maximum length in bytes, or -1 for non-resizable
     * @return A new JSArrayBuffer instance with prototype set
     */
    JSArrayBuffer createJSArrayBuffer(int byteLength, int maxByteLength) {
        JSArrayBuffer jsArrayBuffer = new JSArrayBuffer(context, byteLength, maxByteLength);
        context.transferPrototype(jsArrayBuffer, JSArrayBuffer.NAME);
        return jsArrayBuffer;
    }

    /**
     * ES2024 7.3.34 ArraySpeciesCreate(originalArray, length).
     * Following QuickJS JS_ArraySpeciesCreate.
     *
     * @return the new array-like object, or null if an exception was set on context
     */
    JSValue createJSArraySpecies(JSObject originalArray, long length) {
        // Step 3: If IsArray(originalArray) is false, return ArrayCreate(length)
        int isArr = JSTypeChecking.isArray(context, originalArray);
        if (isArr < 0) {
            return null;
        }
        if (isArr == 0) {
            // ArrayCreate(length): throw RangeError if length > 2^32 - 1
            if (length > 0xFFFFFFFFL) {
                return context.throwRangeError("Invalid array length");
            }
            return createJSArray(length, 0);
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
            // ArrayCreate(length): throw RangeError if length > 2^32 - 1
            if (length > 0xFFFFFFFFL) {
                return context.throwRangeError("Invalid array length");
            }
            return createJSArray(length, 0);
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
     * Create a new JSBigInt64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSBigInt64Array instance with prototype set
     */
    JSBigInt64Array createJSBigInt64Array(int length) {
        return initializeTypedArray(new JSBigInt64Array(context, length), JSBigInt64Array.NAME);
    }

    JSBigInt64Array createJSBigInt64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSBigInt64Array(context, buffer, byteOffset, length), JSBigInt64Array.NAME);
    }

    JSBigIntObject createJSBigIntObject(JSBigInt value) {
        JSBigIntObject wrapper = new JSBigIntObject(context, value);
        context.transferPrototype(wrapper, JSBigIntObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSBigUint64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSBigUint64Array instance with prototype set
     */
    JSBigUint64Array createJSBigUint64Array(int length) {
        return initializeTypedArray(new JSBigUint64Array(context, length), JSBigUint64Array.NAME);
    }

    JSBigUint64Array createJSBigUint64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSBigUint64Array(context, buffer, byteOffset, length), JSBigUint64Array.NAME);
    }

    JSBooleanObject createJSBooleanObject(JSBoolean value) {
        JSBooleanObject wrapper = new JSBooleanObject(context, value);
        context.transferPrototype(wrapper, JSBooleanObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSDataView with proper prototype chain.
     *
     * @param buffer     The ArrayBuffer to view
     * @param byteOffset The offset in bytes
     * @param byteLength The length in bytes
     * @return A new JSDataView instance with prototype set
     */
    JSDataView createJSDataView(IJSArrayBuffer buffer, int byteOffset, int byteLength) {
        JSDataView jsDataView = new JSDataView(context, buffer, byteOffset, byteLength);
        context.transferPrototype(jsDataView, JSDataView.NAME);
        return jsDataView;
    }

    /**
     * Create a new JSDate with proper prototype chain.
     *
     * @param timeValue The time value in milliseconds
     * @return A new JSDate instance with prototype set
     */
    JSDate createJSDate(double timeValue) {
        JSDate jsDate = new JSDate(context, timeValue);
        if (context.getCachedDatePrototype() != null) {
            jsDate.setPrototype(context.getCachedDatePrototype());
        } else {
            context.transferPrototype(jsDate, JSDate.NAME);
        }
        return jsDate;
    }

    JSDisposableStack createJSDisposableStack() {
        JSDisposableStack stack = new JSDisposableStack(context);
        context.transferPrototype(stack, JSDisposableStack.NAME);
        return stack;
    }

    JSError createJSError(String message) {
        JSError jsError = new JSError(context, message);
        context.transferPrototype(jsError, JSError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    JSEvalError createJSEvalError(String message) {
        JSEvalError jsError = new JSEvalError(context, message);
        context.transferPrototype(jsError, JSEvalError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    /**
     * Create a new JSFloat16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat16Array instance with prototype set
     */
    JSFloat16Array createJSFloat16Array(int length) {
        return initializeTypedArray(new JSFloat16Array(context, length), JSFloat16Array.NAME);
    }

    JSFloat16Array createJSFloat16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat16Array(context, buffer, byteOffset, length), JSFloat16Array.NAME);
    }

    /**
     * Create a new JSFloat32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat32Array instance with prototype set
     */
    JSFloat32Array createJSFloat32Array(int length) {
        return initializeTypedArray(new JSFloat32Array(context, length), JSFloat32Array.NAME);
    }

    JSFloat32Array createJSFloat32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat32Array(context, buffer, byteOffset, length), JSFloat32Array.NAME);
    }

    /**
     * Create a new JSFloat64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat64Array instance with prototype set
     */
    JSFloat64Array createJSFloat64Array(int length) {
        return initializeTypedArray(new JSFloat64Array(context, length), JSFloat64Array.NAME);
    }

    JSFloat64Array createJSFloat64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat64Array(context, buffer, byteOffset, length), JSFloat64Array.NAME);
    }

    /**
     * Create a new JSInt16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt16Array instance with prototype set
     */
    JSInt16Array createJSInt16Array(int length) {
        return initializeTypedArray(new JSInt16Array(context, length), JSInt16Array.NAME);
    }

    JSInt16Array createJSInt16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt16Array(context, buffer, byteOffset, length), JSInt16Array.NAME);
    }

    /**
     * Create a new JSInt32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt32Array instance with prototype set
     */
    JSInt32Array createJSInt32Array(int length) {
        return initializeTypedArray(new JSInt32Array(context, length), JSInt32Array.NAME);
    }

    JSInt32Array createJSInt32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt32Array(context, buffer, byteOffset, length), JSInt32Array.NAME);
    }

    /**
     * Create a new JSInt8Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt8Array instance with prototype set
     */
    JSInt8Array createJSInt8Array(int length) {
        return initializeTypedArray(new JSInt8Array(context, length), JSInt8Array.NAME);
    }

    JSInt8Array createJSInt8Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt8Array(context, buffer, byteOffset, length), JSInt8Array.NAME);
    }

    /**
     * Create a new JSMap with proper prototype chain.
     *
     * @return A new JSMap instance with prototype set
     */
    JSMap createJSMap() {
        JSMap jsMap = new JSMap(context);
        context.transferPrototype(jsMap, JSMap.NAME);
        return jsMap;
    }

    JSNumberObject createJSNumberObject(JSNumber value) {
        JSNumberObject wrapper = new JSNumberObject(context, value);
        context.transferPrototype(wrapper, JSNumberObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSObject with proper prototype chain.
     * Sets the object's prototype to Object.prototype from the global object.
     *
     * @return A new JSObject instance with prototype set
     */
    JSObject createJSObject() {
        JSObject jsObject = new JSObject(context);
        if (context.getObjectPrototype() != null) {
            jsObject.setPrototype(context.getObjectPrototype());
        } else {
            context.transferPrototype(jsObject, JSObject.NAME);
        }
        return jsObject;
    }

    /**
     * Create a new JSPromise with proper prototype chain.
     *
     * @return A new JSPromise instance with prototype set
     */
    JSPromise createJSPromise() {
        JSPromise jsPromise = new JSPromise(context);
        if (context.getCachedPromisePrototype() != null) {
            jsPromise.setPrototype(context.getCachedPromisePrototype());
        } else {
            context.transferPrototype(jsPromise, JSPromise.NAME);
        }
        return jsPromise;
    }

    JSRangeError createJSRangeError(String message) {
        JSRangeError jsError = new JSRangeError(context, message);
        context.transferPrototype(jsError, JSRangeError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    JSReferenceError createJSReferenceError(String message) {
        JSReferenceError jsError = new JSReferenceError(context, message);
        context.transferPrototype(jsError, JSReferenceError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    /**
     * Create a new JSRegExp with proper prototype chain.
     *
     * @param pattern The regular expression pattern
     * @param flags   The regular expression flags
     * @return A new JSRegExp instance with prototype set
     */
    JSRegExp createJSRegExp(String pattern, String flags) {
        JSRegExp jsRegExp = new JSRegExp(context, pattern, flags);
        if (context.getCachedRegExpPrototype() != null) {
            jsRegExp.setPrototype(context.getCachedRegExpPrototype());
        } else {
            context.transferPrototype(jsRegExp, JSRegExp.NAME);
        }
        return jsRegExp;
    }

    /**
     * Create a new JSSet with proper prototype chain.
     *
     * @return A new JSSet instance with prototype set
     */
    JSSet createJSSet() {
        JSSet jsSet = new JSSet(context);
        context.transferPrototype(jsSet, JSSet.NAME);
        return jsSet;
    }

    JSStringObject createJSStringObject() {
        JSStringObject wrapper = new JSStringObject(context);
        context.transferPrototype(wrapper, JSStringObject.NAME);
        return wrapper;
    }

    JSStringObject createJSStringObject(JSString value) {
        JSStringObject wrapper = new JSStringObject(context, value);
        context.transferPrototype(wrapper, JSStringObject.NAME);
        return wrapper;
    }

    JSSuppressedError createJSSuppressedError(String message) {
        JSSuppressedError jsError = new JSSuppressedError(context, message);
        context.transferPrototype(jsError, JSSuppressedError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    JSSymbolObject createJSSymbolObject(JSSymbol value) {
        JSSymbolObject wrapper = new JSSymbolObject(context, value);
        context.transferPrototype(wrapper, JSSymbolObject.NAME);
        return wrapper;
    }

    JSSyntaxError createJSSyntaxError(String message) {
        JSSyntaxError jsError = new JSSyntaxError(context, message);
        context.transferPrototype(jsError, JSSyntaxError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    JSTypeError createJSTypeError(String message) {
        JSTypeError jsError = new JSTypeError(context, message);
        context.transferPrototype(jsError, JSTypeError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    JSURIError createJSURIError(String message) {
        JSURIError jsError = new JSURIError(context, message);
        context.transferPrototype(jsError, JSURIError.NAME);
        context.captureStackTrace(jsError);
        return jsError;
    }

    /**
     * Create a new JSUint16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint16Array instance with prototype set
     */
    JSUint16Array createJSUint16Array(int length) {
        return initializeTypedArray(new JSUint16Array(context, length), JSUint16Array.NAME);
    }

    JSUint16Array createJSUint16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint16Array(context, buffer, byteOffset, length), JSUint16Array.NAME);
    }

    /**
     * Create a new JSUint32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint32Array instance with prototype set
     */
    JSUint32Array createJSUint32Array(int length) {
        return initializeTypedArray(new JSUint32Array(context, length), JSUint32Array.NAME);
    }

    JSUint32Array createJSUint32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint32Array(context, buffer, byteOffset, length), JSUint32Array.NAME);
    }

    /**
     * Create a new JSUint8Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint8Array instance with prototype set
     */
    JSUint8Array createJSUint8Array(int length) {
        return initializeTypedArray(new JSUint8Array(context, length), JSUint8Array.NAME);
    }

    JSUint8Array createJSUint8Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint8Array(context, buffer, byteOffset, length), JSUint8Array.NAME);
    }

    /**
     * Create a new JSUint8ClampedArray with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint8ClampedArray instance with prototype set
     */
    JSUint8ClampedArray createJSUint8ClampedArray(int length) {
        return initializeTypedArray(new JSUint8ClampedArray(context, length), JSUint8ClampedArray.NAME);
    }

    JSUint8ClampedArray createJSUint8ClampedArray(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint8ClampedArray(context, buffer, byteOffset, length), JSUint8ClampedArray.NAME);
    }

    /**
     * Create a new JSWeakMap with proper prototype chain.
     *
     * @return A new JSWeakMap instance with prototype set
     */
    JSWeakMap createJSWeakMap() {
        JSWeakMap jsWeakMap = new JSWeakMap(context);
        context.transferPrototype(jsWeakMap, JSWeakMap.NAME);
        return jsWeakMap;
    }

    /**
     * Create a new JSWeakSet with proper prototype chain.
     *
     * @return A new JSWeakSet instance with prototype set
     */
    JSWeakSet createJSWeakSet() {
        JSWeakSet jsWeakSet = new JSWeakSet(context);
        context.transferPrototype(jsWeakSet, JSWeakSet.NAME);
        return jsWeakSet;
    }

    private <T extends JSTypedArray> T initializeTypedArray(T typedArray, String constructorName) {
        context.transferPrototype(typedArray, constructorName);
        var buffer = typedArray.getBuffer();
        if (buffer instanceof JSObject jsObject && jsObject.getPrototype() == null) {
            context.transferPrototype(jsObject, buffer.isShared() ? JSSharedArrayBuffer.NAME : JSArrayBuffer.NAME);
        }
        return typedArray;
    }
}
