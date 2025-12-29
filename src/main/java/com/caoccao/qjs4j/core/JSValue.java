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

import java.util.Optional;

/**
 * Base sealed interface for all JavaScript values.
 * Implements the value representation using sealed interfaces for type safety.
 * Note: JSFunction extends JSObject, so it's not listed here separately.
 */
public sealed interface JSValue extends JSStackValue permits
        JSUndefined, JSNull, JSBoolean, JSNumber, JSString,
        JSObject, JSSymbol, JSBigInt {

    /**
     * Attempt to cast this value to JSArray.
     *
     * @return Optional containing the JSArray if this value is an array, empty otherwise
     */
    default Optional<JSArray> asArray() {
        return this instanceof JSArray v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSArrayBuffer.
     *
     * @return Optional containing the JSArrayBuffer if this value is an ArrayBuffer, empty otherwise
     */
    default Optional<JSArrayBuffer> asArrayBuffer() {
        return this instanceof JSArrayBuffer v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSAsyncGenerator.
     *
     * @return Optional containing the JSAsyncGenerator if this value is an async generator, empty otherwise
     */
    default Optional<JSAsyncGenerator> asAsyncGenerator() {
        return this instanceof JSAsyncGenerator v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSAsyncIterator.
     *
     * @return Optional containing the JSAsyncIterator if this value is an async iterator, empty otherwise
     */
    default Optional<JSAsyncIterator> asAsyncIterator() {
        return this instanceof JSAsyncIterator v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSBigInt.
     *
     * @return Optional containing the JSBigInt if this value is a BigInt, empty otherwise
     */
    default Optional<JSBigInt> asBigInt() {
        return this instanceof JSBigInt v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSBigIntObject.
     *
     * @return Optional containing the JSBigIntObject if this value is a BigInt object, empty otherwise
     */
    default Optional<JSBigIntObject> asBigIntObject() {
        return this instanceof JSBigIntObject v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSBoolean.
     *
     * @return Optional containing the JSBoolean if this value is a boolean, empty otherwise
     */
    default Optional<JSBoolean> asBoolean() {
        return this instanceof JSBoolean v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSBooleanObject.
     *
     * @return Optional containing the JSBooleanObject if this value is a Boolean object, empty otherwise
     */
    default Optional<JSBooleanObject> asBooleanObject() {
        return this instanceof JSBooleanObject v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSBoundFunction.
     *
     * @return Optional containing the JSBoundFunction if this value is a bound function, empty otherwise
     */
    default Optional<JSBoundFunction> asBoundFunction() {
        return this instanceof JSBoundFunction v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSBytecodeFunction.
     *
     * @return Optional containing the JSBytecodeFunction if this value is a bytecode function, empty otherwise
     */
    default Optional<JSBytecodeFunction> asBytecodeFunction() {
        return this instanceof JSBytecodeFunction v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSClass.
     *
     * @return Optional containing the JSClass if this value is a class, empty otherwise
     */
    default Optional<JSClass> asClass() {
        return this instanceof JSClass v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSFinalizationRegistry.
     *
     * @return Optional containing the JSFinalizationRegistry if this value is a FinalizationRegistry, empty otherwise
     */
    default Optional<JSFinalizationRegistry> asFinalizationRegistry() {
        return this instanceof JSFinalizationRegistry v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSFloat32Array.
     *
     * @return Optional containing the JSFloat32Array if this value is a Float32Array, empty otherwise
     */
    default Optional<JSFloat32Array> asFloat32Array() {
        return this instanceof JSFloat32Array v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSDataView.
     *
     * @return Optional containing the JSFloat64Array if this value is a Float64Array, empty otherwise
     */
    default Optional<JSFloat64Array> asFloat64Array() {
        return this instanceof JSFloat64Array v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSFunction.     *
     *
     * @return Optional containing the JSFunction if this value is a function, empty otherwise
     */
    default Optional<JSFunction> asFunction() {
        return this instanceof JSFunction v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSGenerator.
     *
     * @return Optional containing the JSGenerator if this value is a generator, empty otherwise
     */
    default Optional<JSGenerator> asGenerator() {
        return this instanceof JSGenerator v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSInt16Array.
     *
     * @return Optional containing the JSInt16Array if this value is an Int16Array, empty otherwise
     */
    default Optional<JSInt16Array> asInt16Array() {
        return this instanceof JSInt16Array v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSInt32Array.
     *
     * @return Optional containing the JSInt32Array if this value is an Int32Array, empty otherwise
     */
    default Optional<JSInt32Array> asInt32Array() {
        return this instanceof JSInt32Array v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSInt8Array.
     *
     * @return Optional containing the JSInt8Array if this value is an Int8Array, empty otherwise
     */
    default Optional<JSInt8Array> asInt8Array() {
        return this instanceof JSInt8Array v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSIterator.
     *
     * @return Optional containing the JSIterator if this value is an iterator, empty otherwise
     */
    default Optional<JSIterator> asIterator() {
        return this instanceof JSIterator v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSMap.
     *
     * @return Optional containing the JSMap if this value is a Map, empty otherwise
     */
    default Optional<JSMap> asMap() {
        return this instanceof JSMap v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSNativeFunction.
     *
     * @return Optional containing the JSNativeFunction if this value is a native function, empty otherwise
     */
    default Optional<JSNativeFunction> asNativeFunction() {
        return this instanceof JSNativeFunction v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSNull.
     *
     * @return Optional containing the JSNull if this value is null, empty otherwise
     */
    default Optional<JSNull> asNull() {
        return this instanceof JSNull v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSNumber.
     *
     * @return Optional containing the JSNumber if this value is a number, empty otherwise
     */
    default Optional<JSNumber> asNumber() {
        return this instanceof JSNumber v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSNumberObject.
     *
     * @return Optional containing the JSNumberObject if this value is a Number object, empty otherwise
     */
    default Optional<JSNumberObject> asNumberObject() {
        return this instanceof JSNumberObject v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSObject.
     *
     * @return Optional containing the JSObject if this value is an object, empty otherwise
     */
    default Optional<JSObject> asObject() {
        return this instanceof JSObject v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSPromise.
     *
     * @return Optional containing the JSPromise if this value is a Promise, empty otherwise
     */
    default Optional<JSPromise> asPromise() {
        return this instanceof JSPromise v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSProxy.
     *
     * @return Optional containing the JSProxy if this value is a Proxy, empty otherwise
     */
    default Optional<JSProxy> asProxy() {
        return this instanceof JSProxy v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSRegExp.
     *
     * @return Optional containing the JSRegExp if this value is a RegExp, empty otherwise
     */
    default Optional<JSRegExp> asRegExp() {
        return this instanceof JSRegExp v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSSet.
     *
     * @return Optional containing the JSSet if this value is a Set, empty otherwise
     */
    default Optional<JSSet> asSet() {
        return this instanceof JSSet v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSSharedArrayBuffer.
     *
     * @return Optional containing the JSSharedArrayBuffer if this value is a SharedArrayBuffer, empty otherwise
     */
    default Optional<JSSharedArrayBuffer> asSharedArrayBuffer() {
        return this instanceof JSSharedArrayBuffer v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSString.
     *
     * @return Optional containing the JSString if this value is a string, empty otherwise
     */
    default Optional<JSString> asString() {
        return this instanceof JSString v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSStringObject.
     *
     * @return Optional containing the JSStringObject if this value is a String object, empty otherwise
     */
    default Optional<JSStringObject> asStringObject() {
        return this instanceof JSStringObject v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSSymbol.
     *
     * @return Optional containing the JSSymbol if this value is a symbol, empty otherwise
     */
    default Optional<JSSymbol> asSymbol() {
        return this instanceof JSSymbol v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSSymbolObject.
     *
     * @return Optional containing the JSSymbolObject if this value is a Symbol object, empty otherwise
     */
    default Optional<JSSymbolObject> asSymbolObject() {
        return this instanceof JSSymbolObject v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSTypedArray.
     *
     * @return Optional containing the JSTypedArray if this value is a TypedArray, empty otherwise
     */
    default Optional<JSTypedArray> asTypedArray() {
        return this instanceof JSTypedArray v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSUint16Array.
     *
     * @return Optional containing the JSUint16Array if this value is a Uint16Array, empty otherwise
     */
    default Optional<JSUint16Array> asUint16Array() {
        return this instanceof JSUint16Array v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSUint32Array.
     *
     * @return Optional containing the JSUint32Array if this value is a Uint32Array, empty otherwise
     */
    default Optional<JSUint32Array> asUint32Array() {
        return this instanceof JSUint32Array v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSUint8Array.
     *
     * @return Optional containing the JSUint8Array if this value is a Uint8Array, empty otherwise
     */
    default Optional<JSUint8Array> asUint8Array() {
        return this instanceof JSUint8Array v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSUint8ClampedArray.
     *
     * @return Optional containing the JSUint8ClampedArray if this value is a Uint8ClampedArray, empty otherwise
     */
    default Optional<JSUint8ClampedArray> asUint8ClampedArray() {
        return this instanceof JSUint8ClampedArray v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSUndefined.
     *
     * @return Optional containing the JSUndefined if this value is undefined, empty otherwise
     */
    default Optional<JSUndefined> asUndefined() {
        return this instanceof JSUndefined v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSWeakMap.
     *
     * @return Optional containing the JSWeakMap if this value is a WeakMap, empty otherwise
     */
    default Optional<JSWeakMap> asWeakMap() {
        return this instanceof JSWeakMap v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSWeakRef.
     *
     * @return Optional containing the JSWeakRef if this value is a WeakRef, empty otherwise
     */
    default Optional<JSWeakRef> asWeakRef() {
        return this instanceof JSWeakRef v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSWeakSet.
     *
     * @return Optional containing the JSWeakSet if this value is a WeakSet, empty otherwise
     */
    default Optional<JSWeakSet> asWeakSet() {
        return this instanceof JSWeakSet v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Check if this value is an array.
     *
     * @return true if this value is an array, false otherwise
     */
    default boolean isArray() {
        return this instanceof JSArray;
    }

    /**
     * Check if this value is an ArrayBuffer.
     *
     * @return true if this value is an ArrayBuffer, false otherwise
     */
    default boolean isArrayBuffer() {
        return this instanceof JSArrayBuffer;
    }

    /**
     * Check if this value is an async generator.
     *
     * @return true if this value is an async generator, false otherwise
     */
    default boolean isAsyncGenerator() {
        return this instanceof JSAsyncGenerator;
    }

    /**
     * Check if this value is an async iterator.
     *
     * @return true if this value is an async iterator, false otherwise
     */
    default boolean isAsyncIterator() {
        return this instanceof JSAsyncIterator;
    }

    /**
     * Check if this value is a BigInt.
     *
     * @return true if this value is a BigInt, false otherwise
     */
    default boolean isBigInt() {
        return this instanceof JSBigInt;
    }

    default boolean isBigIntObject() {
        return this instanceof JSBigIntObject;
    }

    /**
     * Check if this value is a boolean.
     *
     * @return true if this value is a boolean, false otherwise
     */
    default boolean isBoolean() {
        return this instanceof JSBoolean;
    }

    /**
     * Check if this value is the boolean false.
     *
     * @return true if this value is the boolean false, false otherwise
     */
    default boolean isBooleanFalse() {
        return this == JSBoolean.FALSE;
    }

    /**
     * Check if this value is a Boolean object.
     *
     * @return true if this value is a Boolean object, false otherwise
     */
    default boolean isBooleanObject() {
        return this instanceof JSBooleanObject;
    }

    /**
     * Check if this value is the boolean true.
     *
     * @return true if this value is the boolean true, false otherwise
     */
    default boolean isBooleanTrue() {
        return this == JSBoolean.TRUE;
    }

    /**
     * Check if this value is a bound function.
     *
     * @return true if this value is a bound function, false otherwise
     */
    default boolean isBoundFunction() {
        return this instanceof JSBoundFunction;
    }

    /**
     * Check if this value is a bytecode function.
     *
     * @return true if this value is a bytecode function, false otherwise
     */
    default boolean isBytecodeFunction() {
        return this instanceof JSBytecodeFunction;
    }

    /**
     * Check if this value is a class.
     *
     * @return true if this value is a class, false otherwise
     */
    default boolean isClass() {
        return this instanceof JSClass;
    }

    /**
     * Check if this value is a DataView.
     *
     * @return true if this value is a DataView, false otherwise
     */
    default boolean isDataView() {
        return this instanceof JSDataView;
    }

    /**
     * Check if this value is a Date.
     *
     * @return true if this value is a Date, false otherwise
     */
    default boolean isDate() {
        return this instanceof JSDate;
    }

    /**
     * Check if this value is a FinalizationRegistry.
     *
     * @return true if this value is a FinalizationRegistry, false otherwise
     */
    default boolean isFinalizationRegistry() {
        return this instanceof JSFinalizationRegistry;
    }

    /**
     * Check if this value is a Float32Array.
     *
     * @return true if this value is a Float32Array, false otherwise
     */
    default boolean isFloat32Array() {
        return this instanceof JSFloat32Array;
    }

    /**
     * Check if this value is a Float64Array.
     *
     * @return true if this value is a Float64Array, false otherwise
     */
    default boolean isFloat64Array() {
        return this instanceof JSFloat64Array;
    }

    /**
     * Check if this value is a function.
     *
     * @return true if this value is a function, false otherwise
     */
    default boolean isFunction() {
        return this instanceof JSFunction;
    }

    /**
     * Check if this value is a function.
     * <p>
     * /**
     * Check if this value is a generator.
     *
     * @return true if this value is a generator, false otherwise
     */
    default boolean isGenerator() {
        return this instanceof JSGenerator;
    }

    /**
     * Check if this value is an Int16Array.
     *
     * @return true if this value is an Int16Array, false otherwise
     */
    default boolean isInt16Array() {
        return this instanceof JSInt16Array;
    }

    /**
     * Check if this value is an Int32Array.
     *
     * @return true if this value is an Int32Array, false otherwise
     */
    default boolean isInt32Array() {
        return this instanceof JSInt32Array;
    }

    /**
     * Check if this value is an Int8Array.
     *
     * @return true if this value is an Int8Array, false otherwise
     */
    default boolean isInt8Array() {
        return this instanceof JSInt8Array;
    }

    /**
     * Check if this value is an iterator.
     *
     * @return true if this value is an iterator, false otherwise
     */
    default boolean isIterator() {
        return this instanceof JSIterator;
    }

    /**
     * Check if this value is a Map.
     *
     * @return true if this value is a Map, false otherwise
     */
    default boolean isMap() {
        return this instanceof JSMap;
    }

    /**
     * Check if this value is a native function.
     *
     * @return true if this value is a native function, false otherwise
     */
    default boolean isNativeFunction() {
        return this instanceof JSNativeFunction;
    }

    /**
     * Check if this value is null.
     *
     * @return true if this value is null, false otherwise
     */
    default boolean isNull() {
        return this instanceof JSNull;
    }

    /**
     * Check if this value is null or undefined.
     *
     * @return true if this value is null or undefined, false otherwise
     */
    default boolean isNullOrUndefined() {
        return isNull() || isUndefined();
    }

    /**
     * Check if this value is a number.
     *
     * @return true if this value is a number, false otherwise
     */
    default boolean isNumber() {
        return this instanceof JSNumber;
    }

    /**
     * Check if this value is a Number object.
     *
     * @return true if this value is a Number object, false otherwise
     */
    default boolean isNumberObject() {
        return this instanceof JSNumberObject;
    }

    /**
     * Check if this value is an object.
     *
     * @return true if this value is an object, false otherwise
     */
    default boolean isObject() {
        return this instanceof JSObject;
    }

    /**
     * Check if this value is a Promise.
     *
     * @return true if this value is a Promise, false otherwise
     */
    default boolean isPromise() {
        return this instanceof JSPromise;
    }

    /**
     * Check if this value is a Proxy.
     *
     * @return true if this value is a Proxy, false otherwise
     */
    default boolean isProxy() {
        return this instanceof JSProxy;
    }

    /**
     * Check if this value is a RegExp.
     *
     * @return true if this value is a RegExp, false otherwise
     */
    default boolean isRegExp() {
        return this instanceof JSRegExp;
    }

    /**
     * Check if this value is a Set.
     *
     * @return true if this value is a Set, false otherwise
     */
    default boolean isSet() {
        return this instanceof JSSet;
    }

    /**
     * Check if this value is a SharedArrayBuffer.
     *
     * @return true if this value is a SharedArrayBuffer, false otherwise
     */
    default boolean isSharedArrayBuffer() {
        return this instanceof JSSharedArrayBuffer;
    }

    /**
     * Check if this value is a string.
     *
     * @return true if this value is a string, false otherwise
     */
    default boolean isString() {
        return this instanceof JSString;
    }

    /**
     * Check if this value is a String object.
     *
     * @return true if this value is a String object, false otherwise
     */
    default boolean isStringObject() {
        return this instanceof JSStringObject;
    }

    /**
     * Check if this value is a symbol.
     *
     * @return true if this value is a symbol, false otherwise
     */
    default boolean isSymbol() {
        return this instanceof JSSymbol;
    }

    /**
     * Check if this value is a Symbol object.
     *
     * @return true if this value is a Symbol object, false otherwise
     */
    default boolean isSymbolObject() {
        return this instanceof JSSymbolObject;
    }

    /**
     * Check if this value is a TypedArray.
     *
     * @return true if this value is a TypedArray, false otherwise
     */
    default boolean isTypedArray() {
        return this instanceof JSTypedArray;
    }

    /**
     * Check if this value is a Uint16Array.
     *
     * @return true if this value is a Uint16Array, false otherwise
     */
    default boolean isUint16Array() {
        return this instanceof JSUint16Array;
    }

    /**
     * Check if this value is a Uint32Array.
     *
     * @return true if this value is a Uint32Array, false otherwise
     */
    default boolean isUint32Array() {
        return this instanceof JSUint32Array;
    }

    /**
     * Check if this value is a Uint8Array.
     *
     * @return true if this value is a Uint8Array, false otherwise
     */
    default boolean isUint8Array() {
        return this instanceof JSUint8Array;
    }

    /**
     * Check if this value is a Uint8ClampedArray.
     *
     * @return true if this value is a Uint8ClampedArray, false otherwise
     */
    default boolean isUint8ClampedArray() {
        return this instanceof JSUint8ClampedArray;
    }

    /**
     * Check if this value is undefined.
     *
     * @return true if this value is undefined, false otherwise
     */
    default boolean isUndefined() {
        return this instanceof JSUndefined;
    }

    /**
     * Check if this value is a WeakMap.
     *
     * @return true if this value is a WeakMap, false otherwise
     */
    default boolean isWeakMap() {
        return this instanceof JSWeakMap;
    }

    /**
     * Check if this value is a WeakRef.
     *
     * @return true if this value is a WeakRef, false otherwise
     */
    default boolean isWeakRef() {
        return this instanceof JSWeakRef;
    }

    /**
     * Check if this value is a WeakSet.
     *
     * @return true if this value is a WeakSet, false otherwise
     */
    default boolean isWeakSet() {
        return this instanceof JSWeakSet;
    }

    /**
     * Convert to Java object representation.
     */
    Object toJavaObject();

    /**
     * Get the type of this value.
     */
    JSValueType type();
}
