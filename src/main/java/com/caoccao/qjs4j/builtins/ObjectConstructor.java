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
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of Object constructor and static methods.
 * Based on ES2020 Object specification.
 */
public final class ObjectConstructor {

    /**
     * Object.assign(target, ...sources)
     * ES2024 19.1.2.1
     * Copies all enumerable own properties from one or more source objects to a target object.
     */
    public static JSValue assign(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 1: Let to be ToObject(target)
        JSValue targetValue = args[0];
        if (targetValue.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject target = JSTypeConversions.toObject(context, targetValue);
        if (target == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 4: For each source
        for (int i = 1; i < args.length; i++) {
            JSValue source = args[i];
            if (source.isNullOrUndefined()) {
                continue;
            }

            // Step 4b: Let from be ToObject(nextSource)
            JSObject sourceObj = JSTypeConversions.toObject(context, source);
            if (sourceObj == null) {
                continue;
            }

            // Step 4b.ii: Let keys be from.[[OwnPropertyKeys]]()
            List<PropertyKey> keys = sourceObj.getOwnPropertyKeys();
            for (PropertyKey key : keys) {
                // Step 4c.i: Let desc be from.[[GetOwnProperty]](nextKey)
                PropertyDescriptor desc = sourceObj.getOwnPropertyDescriptor(key);
                if (desc == null) {
                    continue;
                }

                // Step 4c.iii: If desc is not undefined and desc.[[Enumerable]] is true
                if (!desc.isEnumerable()) {
                    continue;
                }

                // Step 4c.iii.1: Let propValue be Get(from, nextKey)
                JSValue propValue = sourceObj.get(key);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }

                // Step 4c.iii.3: Perform ? Set(to, nextKey, propValue, true)
                boolean setSuccess = target.setWithResult(key, propValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!setSuccess) {
                    return context.throwTypeError("Cannot assign to read only property '"
                            + key.toPropertyString() + "' of object '#<Object>'");
                }
            }
        }

        return target;
    }

    /**
     * Object(value)
     * ES2020 19.1.1.1
     * When called as a function, creates a wrapper object for the given value.
     * - For null/undefined, returns a new empty object
     * - For primitives (number, string, boolean, symbol, bigint), returns a wrapper object
     * - For objects, returns the object itself
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue newTarget = context.getConstructorNewTarget();
        if (newTarget != null) {
            JSValue objectConstructor = context.getGlobalObject().get(PropertyKey.fromString(JSObject.NAME));
            if (newTarget != objectConstructor) {
                JSObject newObject = new JSObject(context);
                if (newTarget instanceof JSObject newTargetObject) {
                    if (!context.transferPrototypeFromConstructor(newObject, newTargetObject)) {
                        if (context.hasPendingException()) {
                            return JSUndefined.INSTANCE;
                        }
                        context.transferPrototype(newObject, JSObject.NAME);
                    }
                } else {
                    context.transferPrototype(newObject, JSObject.NAME);
                }
                return newObject;
            }
        }

        // If no argument or undefined/null, return new empty object
        if (args.length == 0 || args[0].isNullOrUndefined()) {
            return context.createJSObject();
        }

        JSValue value = args[0];

        // If already an object, return it as-is
        if (value instanceof JSObject) {
            return value;
        }

        // Get global object for accessing constructor prototypes
        JSObject global = context.getGlobalObject();

        // Wrap primitive values in their respective object wrappers
        if (value instanceof JSNumber num) {
            JSNumberObject jsNumberObject = new JSNumberObject(context, num);
            context.transferPrototype(jsNumberObject, JSNumberObject.NAME);
            return jsNumberObject;
        }

        if (value instanceof JSString str) {
            JSStringObject jsStringObject = new JSStringObject(context, str);
            context.transferPrototype(jsStringObject, JSString.NAME);
            return jsStringObject;
        }

        if (value instanceof JSBoolean bool) {
            JSBooleanObject jsBooleanObject = new JSBooleanObject(context, bool);
            context.transferPrototype(jsBooleanObject, JSBoolean.NAME);
            return jsBooleanObject;
        }

        if (value instanceof JSSymbol jsSymbol) {
            JSSymbolObject jsSymbolObject = new JSSymbolObject(context, jsSymbol);
            context.transferPrototype(jsSymbolObject, JSSymbol.NAME);
            return jsSymbolObject;
        }

        if (value instanceof JSBigInt bigInt) {
            JSBigIntObject jsBigIntObject = new JSBigIntObject(context, bigInt);
            context.transferPrototype(jsBigIntObject, JSBigInt.NAME);
            return jsBigIntObject;
        }

        // For any other value, return new empty object
        return context.createJSObject();
    }

    /**
     * Close iterator and throw a TypeError.
     */
    private static void closeIteratorAndThrow(JSContext context, JSValue iterator, String message) {
        JSIteratorHelper.closeIterator(context, iterator);
        // Clear any exception from closeIterator, throw our TypeError
        context.clearPendingException();
        context.throwTypeError(message);
    }

    /**
     * Close iterator while preserving the current pending exception.
     * Per spec, the original error takes precedence over any error from return().
     */
    private static JSValue closeIteratorPreserveError(JSContext context, JSValue iterator) {
        JSValue savedError = context.getPendingException();
        context.clearPendingException();
        JSIteratorHelper.closeIterator(context, iterator);
        // Restore the original error
        if (context.hasPendingException()) {
            context.clearPendingException();
        }
        context.setPendingException(savedError);
        return JSUndefined.INSTANCE;
    }

    /**
     * Object.create(proto, propertiesObject)
     * ES2020 19.1.2.2
     * Creates a new object with the specified prototype object.
     */
    public static JSValue create(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object prototype may only be an Object or null: undefined");
        }
        JSValue firstArg = args[0];
        JSObject proto = null;
        if (!firstArg.isNull()) {
            if (firstArg instanceof JSObject jsObject) {
                proto = jsObject;
            } else {
                return context.throwTypeError("Object prototype may only be an Object or null: " + JSTypeConversions.toString(context, firstArg).value());
            }
        }
        // Create new object
        JSObject newObj = new JSObject(context);
        newObj.setPrototype(proto);
        // null prototype is allowed - object stays with null prototype

        // Handle propertiesObject parameter (args[1]) if present
        // ES2024 19.1.2.2 step 3: ObjectDefineProperties(obj, Properties)
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            JSObject propsObj = JSTypeConversions.toObject(context, args[1]);
            if (propsObj == null) {
                return context.throwTypeError("Cannot convert undefined or null to object");
            }

            // ObjectDefineProperties: get all own property keys
            List<PropertyKey> propKeys = propsObj.getOwnPropertyKeys();

            for (PropertyKey key : propKeys) {
                // Only process enumerable own properties (ES5.1 15.2.3.7 step 5a-b)
                PropertyDescriptor propDesc = propsObj.getOwnPropertyDescriptor(key);
                if (propDesc == null || !propDesc.isEnumerable()) {
                    continue;
                }

                // Get the descriptor value (triggers getters)
                JSValue descValue = propsObj.get(key);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!(descValue instanceof JSObject descObj)) {
                    return context.throwTypeError("Property descriptor must be an object");
                }

                // ToPropertyDescriptor(descObj) - use get(context,...) to trigger getters
                PropertyDescriptor descriptor = toPropertyDescriptor(context, descObj);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }

                // Define the property on the new object
                newObj.defineProperty(key, descriptor);
            }
        }

        return newObj;
    }

    /**
     * Object.defineProperties(obj, props)
     * ES5.1 15.2.3.7
     * Defines new or modifies existing properties directly on an object, returning the object.
     */
    public static JSValue defineProperties(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("Object.defineProperties requires 2 arguments");
        }

        if (!(args[0] instanceof JSObject obj)) {
            return context.throwTypeError("Object.defineProperties called on non-object");
        }

        // Step 2: Let props be ? ToObject(Properties)
        JSValue propsArg = args[1];
        if (propsArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject props;
        if (propsArg instanceof JSObject propsObj) {
            props = propsObj;
        } else {
            props = JSTypeConversions.toObject(context, propsArg);
            if (props == null) {
                return context.throwTypeError("Cannot convert to object");
            }
        }

        // Get all enumerable properties from the props object
        List<PropertyKey> propertyKeys = props.getOwnPropertyKeys();

        for (PropertyKey key : propertyKeys) {
            // Only process enumerable properties
            PropertyDescriptor keyDesc = props.getOwnPropertyDescriptor(key);
            if (keyDesc != null && keyDesc.isEnumerable()) {
                JSValue descValue = props.get(key);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }

                if (!(descValue instanceof JSObject descObj)) {
                    return context.throwTypeError("Property descriptor must be an object");
                }

                PropertyDescriptor desc = toPropertyDescriptor(context, descObj);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }

                // DefinePropertyOrThrow(O, P, desc)
                if (!obj.defineProperty(key, desc)) {
                    if (!context.hasPendingException()) {
                        context.throwTypeError("Cannot redefine property: " + key.toPropertyString());
                    }
                    return JSUndefined.INSTANCE;
                }
            }
        }

        return obj;
    }

    /**
     * Object.entries(obj)
     * ES2024 20.1.2.5 / EnumerableOwnProperties (7.3.25)
     * Returns an array of a given object's own enumerable string-keyed [key, value] pairs.
     */
    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSValue arg = args[0];
        if (arg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 1: Let obj be ? ToObject(O)
        JSObject obj;
        if (arg instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, arg);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        // Step 2: Let ownKeys be ? obj.[[OwnPropertyKeys]]()
        List<PropertyKey> ownKeys = obj.getOwnPropertyKeys();

        // Step 3: For each key, check enumerability and collect [key, value] pairs
        JSArray result = context.createJSArray();
        for (PropertyKey key : ownKeys) {
            // Only string keys (not symbols). Index keys are also "string" keys per spec.
            if (key.isSymbol()) {
                continue;
            }
            // Step 3a: Let desc be ? obj.[[GetOwnProperty]](key)
            PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            // Step 3b: If desc is not undefined and desc.[[Enumerable]] is true
            if (desc != null && desc.isEnumerable()) {
                JSValue value = obj.get(key);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                JSArray entry = context.createJSArray(2);
                entry.set(0, new JSString(key.toPropertyString()));
                entry.set(1, value);
                result.push(entry);
            }
        }
        return result;
    }

    /**
     * Object.freeze(obj)
     * ES2024 20.1.2.6 / SetIntegrityLevel (7.3.16) with level "frozen".
     * Following QuickJS js_object_seal() implementation with freeze_flag=1.
     */
    public static JSValue freeze(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.freeze called on non-object");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return arg; // Primitives are returned as-is
        }

        // TypedArrays backed by resizable buffers cannot be frozen because their
        // elements could change due to buffer resizing, which violates frozen semantics.
        if (obj instanceof JSTypedArray ta) {
            IJSArrayBuffer buf = ta.getBuffer();
            if (buf instanceof JSArrayBuffer ab && ab.isResizable()) {
                return context.throwTypeError("Cannot freeze a TypedArray backed by a resizable buffer");
            }
        }

        // Step 3: Set the [[Extensible]] internal slot of O to false
        obj.preventExtensions();

        // Step 5: Let keys be ? O.[[OwnPropertyKeys]]()
        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();

        // Step 7: For each element k of keys, freeze each property
        for (PropertyKey key : propertyKeys) {
            PropertyDescriptor currentDesc = obj.getOwnPropertyDescriptor(key);
            if (currentDesc == null) {
                continue;
            }

            // Per spec 7.3.16 step 7.b.ii: create a PARTIAL descriptor
            // For accessor properties: { [[Configurable]]: false }
            // For data properties: { [[Configurable]]: false, [[Writable]]: false }
            PropertyDescriptor freezeDesc = new PropertyDescriptor();
            freezeDesc.setConfigurable(false);
            if (currentDesc.isAccessorDescriptor()) {
                // Accessor: only set configurable
            } else {
                // Data or generic: also set writable
                freezeDesc.setWritable(false);
            }

            // DefinePropertyOrThrow(O, k, desc)
            if (!obj.defineProperty(key, freezeDesc)) {
                if (!context.hasPendingException()) {
                    context.throwTypeError("Cannot freeze property: " + key.toPropertyString());
                }
                return JSUndefined.INSTANCE;
            }
        }

        // Mark the object as frozen (sets frozen, sealed, and extensible flags)
        obj.freeze();

        return arg;
    }

    /**
     * Object.fromEntries(iterable)
     * ES2024 20.1.2.8 / AddEntriesFromIterable (7.4.38)
     * Creates an object from an iterable of key-value pairs.
     */
    public static JSValue fromEntries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.fromEntries requires an iterable argument");
        }

        JSValue iterable = args[0];
        if (iterable.isNullOrUndefined()) {
            return context.throwTypeError("Object.fromEntries requires an iterable argument");
        }

        // GetIterator(iterable, sync)
        JSValue iterator = JSIteratorHelper.getIterator(context, iterable);
        if (iterator == null) {
            if (!context.hasPendingException()) {
                return context.throwTypeError("object is not iterable");
            }
            return JSUndefined.INSTANCE;
        }

        JSObject result = context.createJSObject();

        // AddEntriesFromIterable step 4: Repeat
        while (true) {
            // Step 4a: IteratorStep - errors here propagate WITHOUT IteratorClose
            // IteratorNext step 3: if result is not an Object, throw TypeError (no IteratorClose)
            JSObject iterResult;
            try {
                iterResult = JSIteratorHelper.iteratorNext(iterator, context);
            } catch (JSVirtualMachineException e) {
                // IteratorStep errors propagate without IteratorClose
                throw e;
            }
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (iterResult == null) {
                // next() returned a non-object: IteratorNext step 3 throws TypeError
                // Per spec, do NOT close the iterator for IteratorStep errors
                return context.throwTypeError("Iterator result is not an object");
            }

            JSValue doneValue = iterResult.get(PropertyKey.DONE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                break;
            }

            // Step 4c: IteratorValue - errors propagate WITHOUT IteratorClose
            JSValue nextItem = iterResult.get(PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Steps 4d-4k: errors from here on CLOSE the iterator
            try {
                // Step 4d: If nextItem is not an Object, close iterator and throw TypeError
                if (!(nextItem instanceof JSObject entryObj)) {
                    closeIteratorAndThrow(context, iterator,
                            "Iterator value " + JSTypeConversions.toString(context, nextItem).value() + " is not an entry object");
                    return JSUndefined.INSTANCE;
                }

                // Step 4e: Get(nextItem, "0") — key. Errors close the iterator.
                JSValue keyValue = entryObj.get(PropertyKey.fromString("0"));
                if (context.hasPendingException()) {
                    return closeIteratorPreserveError(context, iterator);
                }

                // Step 4g: Get(nextItem, "1") — value. Errors close the iterator.
                JSValue entryValue = entryObj.get(PropertyKey.fromString("1"));
                if (context.hasPendingException()) {
                    return closeIteratorPreserveError(context, iterator);
                }

                // Step 4k: adder = CreateDataPropertyOnObject: ToPropertyKey + CreateDataProperty
                // ToPropertyKey supports symbols. Errors close the iterator.
                PropertyKey key = PropertyKey.fromValue(context, keyValue);
                if (context.hasPendingException()) {
                    return closeIteratorPreserveError(context, iterator);
                }

                // CreateDataProperty (defineProperty semantics, not Set)
                result.defineProperty(key, PropertyDescriptor.defaultData(entryValue));
            } catch (JSVirtualMachineException e) {
                // A bytecode function called during key/value processing threw.
                // Close the iterator, preserving the original error, then re-throw.
                JSValue savedError = context.getPendingException();
                context.clearPendingException();
                try {
                    JSIteratorHelper.closeIterator(context, iterator);
                } catch (JSVirtualMachineException ignored) {
                    // Ignore errors from closing
                }
                // Restore the original error
                if (context.hasPendingException()) {
                    context.clearPendingException();
                }
                if (savedError != null) {
                    context.setPendingException(savedError);
                }
                throw e;
            }
        }

        return result;
    }

    /**
     * Object.getOwnPropertyDescriptor(obj, prop)
     * ES2015 19.1.2.6
     * Returns a property descriptor for an own property of an object.
     */
    public static JSValue getOwnPropertyDescriptor(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES2015 19.1.2.6 step 1: Let obj be ? ToObject(O)
        JSValue objArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (objArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject obj;
        if (objArg instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, objArg);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue propertyArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        PropertyKey key = PropertyKey.fromValue(context, propertyArg);
        PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);

        if (desc == null) {
            return JSUndefined.INSTANCE;
        }

        // Convert PropertyDescriptor to a descriptor object
        JSObject descObj = context.createJSObject();

        if (desc.isDataDescriptor()) {
            descObj.set(PropertyKey.VALUE, desc.getValue() != null ? desc.getValue() : JSUndefined.INSTANCE);
            descObj.set(PropertyKey.WRITABLE, JSBoolean.valueOf(desc.isWritable()));
        } else if (desc.isAccessorDescriptor()) {
            descObj.set(PropertyKey.GET, desc.getGetter() != null ? desc.getGetter() : JSUndefined.INSTANCE);
            descObj.set(PropertyKey.SET, desc.getSetter() != null ? desc.getSetter() : JSUndefined.INSTANCE);
        }

        descObj.set(PropertyKey.ENUMERABLE, JSBoolean.valueOf(desc.isEnumerable()));
        descObj.set(PropertyKey.CONFIGURABLE, JSBoolean.valueOf(desc.isConfigurable()));

        return descObj;
    }

    /**
     * Object.getOwnPropertyDescriptors(obj)
     * ES2017 19.1.2.7
     * Returns all own property descriptors of an object.
     */
    public static JSValue getOwnPropertyDescriptors(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // ES2017 19.1.2.7 step 1: Let obj be ? ToObject(O)
        JSValue objArg = args[0];
        if (objArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject obj;
        if (objArg instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, objArg);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSObject result = context.createJSObject();
        List<PropertyKey> keys = obj.getOwnPropertyKeys();

        for (PropertyKey key : keys) {
            PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);
            if (desc != null) {
                JSObject descObj = context.createJSObject();

                if (desc.isDataDescriptor()) {
                    descObj.set(PropertyKey.VALUE, desc.getValue() != null ? desc.getValue() : JSUndefined.INSTANCE);
                    descObj.set(PropertyKey.WRITABLE, JSBoolean.valueOf(desc.isWritable()));
                } else if (desc.isAccessorDescriptor()) {
                    descObj.set(PropertyKey.GET, desc.getGetter() != null ? desc.getGetter() : JSUndefined.INSTANCE);
                    descObj.set(PropertyKey.SET, desc.getSetter() != null ? desc.getSetter() : JSUndefined.INSTANCE);
                }

                descObj.set(PropertyKey.ENUMERABLE, JSBoolean.valueOf(desc.isEnumerable()));
                descObj.set(PropertyKey.CONFIGURABLE, JSBoolean.valueOf(desc.isConfigurable()));

                result.set(key, descObj);
            }
        }

        return result;
    }

    /**
     * Object.getOwnPropertyNames(obj)
     * ES2015 19.1.2.8
     * Returns an array of all own property names (including non-enumerable).
     */
    public static JSValue getOwnPropertyNames(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // ES2015 19.1.2.8 step 1: Let obj be ? ToObject(O)
        JSValue objArg = args[0];
        if (objArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject obj;
        if (objArg instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, objArg);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSArray result = context.createJSArray();
        List<PropertyKey> keys = obj.getOwnPropertyKeys();
        for (PropertyKey key : keys) {
            // Only include string keys (not symbols)
            if (!key.isSymbol()) {
                result.push(new JSString(key.toPropertyString()));
            }
        }
        return result;
    }

    /**
     * Object.getOwnPropertySymbols(obj)
     * ES2015 19.1.2.9
     * Returns an array of all own symbol properties.
     */
    public static JSValue getOwnPropertySymbols(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // ES2015 19.1.2.9 step 1: Let obj be ? ToObject(O)
        JSValue objArg = args[0];
        if (objArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject obj;
        if (objArg instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, objArg);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        List<PropertyKey> keys = obj.getOwnPropertyKeys();
        JSArray result = context.createJSArray();

        for (PropertyKey key : keys) {
            // Only include symbol keys
            if (key.isSymbol()) {
                JSSymbol symbol = key.asSymbol();
                if (symbol != null) {
                    result.push(symbol);
                }
            }
        }

        return result;
    }

    /**
     * Object.getPrototypeOf(obj)
     * ES2020 19.1.2.9
     * Returns the prototype of the specified object.
     */
    public static JSValue getPrototypeOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // ES2015 19.1.2.9 step 1: Let obj be ? ToObject(O)
        JSValue arg = args[0];
        if (arg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject obj;
        if (arg instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, arg);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSObject prototype = obj.getPrototype();
        return prototype != null ? prototype : JSNull.INSTANCE;
    }

    /**
     * Object.groupBy(items, callbackFn)
     * ES2024 20.1.2.11
     * Groups array elements by a key returned from the callback function.
     */
    public static JSValue groupBy(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("Object.groupBy requires 2 arguments");
        }

        JSValue items = args[0];
        if (items.isNullOrUndefined()) {
            return context.throwTypeError("Cannot read properties of " +
                    (items instanceof JSNull ? "null" : "undefined") + " (reading 'Symbol(Symbol.iterator)')");
        }

        if (!(args[1] instanceof JSFunction callback)) {
            return context.throwTypeError("Second argument must be a function");
        }

        // Step 1: Get iterator from items (any iterable, not just arrays)
        JSValue iterator = JSIteratorHelper.getIterator(context, items);
        if (iterator == null) {
            if (!context.hasPendingException()) {
                return context.throwTypeError("object is not iterable");
            }
            return JSUndefined.INSTANCE;
        }

        // Result object has null prototype per spec
        JSObject result = new JSObject(context);
        result.setPrototype(null);

        long index = 0;
        while (true) {
            JSObject iterResult = JSIteratorHelper.iteratorNext(iterator, context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (iterResult == null) {
                return context.throwTypeError("Iterator result is not an object");
            }

            JSValue doneValue = iterResult.get(PropertyKey.DONE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                break;
            }

            JSValue element = iterResult.get(PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Call callback - errors close iterator
            JSValue keyValue;
            try {
                keyValue = callback.call(context, JSUndefined.INSTANCE, new JSValue[]{element, JSNumber.of(index)});
                if (context.hasPendingException()) {
                    closeIteratorPreserveError(context, iterator);
                    return JSUndefined.INSTANCE;
                }
            } catch (Exception e) {
                JSValue savedError = context.getPendingException();
                context.clearPendingException();
                try {
                    JSIteratorHelper.closeIterator(context, iterator);
                } catch (Exception ignored) {
                    // ignore close errors
                }
                if (context.hasPendingException()) {
                    context.clearPendingException();
                }
                if (savedError != null) {
                    context.setPendingException(savedError);
                }
                throw e;
            }

            // Convert key to property key
            PropertyKey key = PropertyKey.fromValue(context, keyValue);
            if (context.hasPendingException()) {
                closeIteratorPreserveError(context, iterator);
                return JSUndefined.INSTANCE;
            }

            // Get or create array for this key
            JSValue existingGroup = result.get(key);
            JSArray group;
            if (existingGroup instanceof JSArray existingArray) {
                group = existingArray;
            } else {
                group = context.createJSArray();
                result.set(key, group);
            }

            group.push(element);
            index++;
        }

        return result;
    }

    /**
     * Object.hasOwn(obj, prop)
     * ES2022 20.1.2.10
     * Static method to check if an object has a property as its own property.
     */
    public static JSValue hasOwn(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // ES2022 step 1: Let obj be ? ToObject(O)
        JSValue objValue = args[0];
        if (objValue.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject obj;
        if (objValue instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, objValue);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (args.length < 2) {
            return JSBoolean.FALSE;
        }

        // Step 2: Let P be ? ToPropertyKey(property) - supports symbols
        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(obj.hasOwnProperty(key));
    }

    /**
     * Object.hasOwnProperty(obj, prop)
     * This is actually Object.prototype.hasOwnProperty, but we implement it here.
     */
    public static JSValue hasOwnProperty(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject obj)) {
            return context.throwTypeError("hasOwnProperty called on non-object");
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        JSString propName = JSTypeConversions.toString(context, args[0]);
        PropertyKey key = PropertyKey.fromString(propName.value());

        return JSBoolean.valueOf(obj.hasOwnProperty(key));
    }

    /**
     * Object.is(value1, value2)
     * ES2015 19.1.2.10
     * Determines whether two values are the same value using SameValue algorithm.
     * Unlike ===, Object.is treats NaN as equal to NaN and +0 as different from -0.
     */
    public static JSValue is(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue x = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue y = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        // SameValue algorithm (ES2020 7.2.11)

        // 1. If Type(x) is different from Type(y), return false
        if (x.getClass() != y.getClass()) {
            return JSBoolean.FALSE;
        }

        // 2. If Type(x) is Number, then
        if (x instanceof JSNumber xNum && y instanceof JSNumber yNum) {
            double xVal = xNum.value();
            double yVal = yNum.value();

            // a. If x is NaN and y is NaN, return true
            if (Double.isNaN(xVal) && Double.isNaN(yVal)) {
                return JSBoolean.TRUE;
            }

            // b. If x is +0 and y is -0, return false
            if (xVal == 0.0 && yVal == 0.0) {
                // Check for +0 vs -0
                if (Double.doubleToRawLongBits(xVal) != Double.doubleToRawLongBits(yVal)) {
                    return JSBoolean.FALSE;
                }
            }

            // c. If x is -0 and y is +0, return false (covered above)
            // d. If x is the same Number value as y, return true
            // e. Return false
            return JSBoolean.valueOf(xVal == yVal);
        }

        // 3. Return SameValueNonNumber(x, y)
        // For other types, use reference equality
        return JSBoolean.valueOf(x == y);
    }

    /**
     * Object.isExtensible(obj)
     * ES5.1 15.2.3.13
     * Determines if an object is extensible (whether new properties can be added to it).
     */
    public static JSValue isExtensible(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.isExtensible called without arguments");
        }

        JSValue arg = args[0];

        // Non-objects are not extensible
        if (!(arg instanceof JSObject obj)) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(obj.isExtensible());
    }

    /**
     * Object.isFrozen(obj)
     * ES2020 19.1.2.12
     */
    public static JSValue isFrozen(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.isFrozen called without arguments");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return JSBoolean.TRUE; // Primitives are always frozen
        }

        return JSBoolean.valueOf(testIntegrityLevel(context, obj, true));
    }

    /**
     * Object.isSealed(obj)
     * ES2020 19.1.2.13
     */
    public static JSValue isSealed(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.isSealed called without arguments");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return JSBoolean.TRUE; // Primitives are always sealed
        }

        return JSBoolean.valueOf(testIntegrityLevel(context, obj, false));
    }

    /**
     * Object.keys(obj)
     * ES2020 19.1.2.16
     * Returns an array of a given object's own enumerable property names.
     */
    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        JSValue arg = args[0];

        // Null and undefined throw TypeError
        if (arg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Step 1: Let obj be ? ToObject(O)
        JSObject obj;
        if (arg instanceof JSObject jsObj) {
            obj = jsObj;
        } else {
            obj = JSTypeConversions.toObject(context, arg);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        // Step 2: EnumerableOwnProperties(obj, key) - single pass per spec
        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        List<JSValue> keyList = new ArrayList<>();
        for (PropertyKey key : propertyKeys) {
            // Include both string and index keys (not symbols)
            if (!key.isSymbol()) {
                PropertyDescriptor descriptor = obj.getOwnPropertyDescriptor(key);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (descriptor != null && descriptor.isEnumerable()) {
                    keyList.add(new JSString(key.toPropertyString()));
                }
            }
        }
        return context.createJSArray(keyList.toArray(JSValue.NO_ARGS), true);
    }

    /**
     * Object.preventExtensions(obj)
     * ES5.1 15.2.3.10
     * Prevents new properties from ever being added to an object.
     */
    public static JSValue preventExtensions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.preventExtensions called without arguments");
        }

        JSValue arg = args[0];

        // Non-objects are returned as-is
        if (!(arg instanceof JSObject obj)) {
            return arg;
        }

        obj.preventExtensions();
        return obj;
    }

    /**
     * Object.seal(obj)
     * ES2020 19.1.2.17
     * Seals an object.
     * Following QuickJS js_object_seal() implementation with freeze_flag=0.
     */
    public static JSValue seal(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.seal called on non-object");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return arg; // Primitives are returned as-is
        }

        // Step 2: Set the [[Extensible]] internal slot of O to false
        obj.preventExtensions();

        // Step 4: Let keys be ? O.[[OwnPropertyKeys]]()
        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();

        // Step 6: For each property, set configurable=false via DefinePropertyOrThrow
        for (PropertyKey key : propertyKeys) {
            PropertyDescriptor currentDesc = obj.getOwnPropertyDescriptor(key);
            if (currentDesc == null) {
                continue;
            }

            // Per spec 7.3.16 step 7.a: { [[Configurable]]: false } partial descriptor
            PropertyDescriptor sealDesc = new PropertyDescriptor();
            sealDesc.setConfigurable(false);

            if (!obj.defineProperty(key, sealDesc)) {
                if (!context.hasPendingException()) {
                    context.throwTypeError("Cannot seal property: " + key.toPropertyString());
                }
                return JSUndefined.INSTANCE;
            }
        }

        // Mark the object as sealed
        obj.seal();

        return arg;
    }

    /**
     * Object.setPrototypeOf(obj, prototype)
     * ES2020 19.1.2.18
     * Sets the prototype of a specified object.
     */
    public static JSValue setPrototypeOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("Object.setPrototypeOf requires 2 arguments");
        }

        // ES2024 step 1: RequireObjectCoercible(O) - throw for null/undefined
        JSValue objValue = args[0];
        if (objValue.isNullOrUndefined()) {
            return context.throwTypeError("Object.setPrototypeOf called on non-object");
        }

        JSValue protoValue = args[1];
        // Step 2: If proto is not Object and not null, throw TypeError
        if (!(protoValue instanceof JSObject) && !(protoValue instanceof JSNull)) {
            return context.throwTypeError("Object prototype may only be an Object or null");
        }

        // Step 3: If O is not Object, return O (primitives)
        if (!(objValue instanceof JSObject obj)) {
            return objValue;
        }

        JSObject proto;
        if (protoValue instanceof JSObject protoObj) {
            proto = protoObj;
        } else {
            proto = null;
        }

        // Proxies have their own setPrototype logic with trap handling
        if (obj instanceof JSProxy) {
            obj.setPrototype(proto);
            return obj;
        }

        // Following QuickJS: Object.setPrototypeOf uses throw_flag=TRUE
        JSObject.SetPrototypeResult result = obj.setPrototypeChecked(proto);
        if (result != JSObject.SetPrototypeResult.SUCCESS) {
            String msg = result == JSObject.SetPrototypeResult.NOT_EXTENSIBLE
                    ? "object is not extensible"
                    : "circular prototype chain";
            return context.throwTypeError(msg);
        }

        return obj;
    }

    /**
     * TestIntegrityLevel(O, level)
     * ES2024 7.3.17
     * Tests whether all own properties of an object are non-configurable
     * (sealed) and additionally non-writable (frozen).
     */
    private static boolean testIntegrityLevel(JSContext context, JSObject obj, boolean frozen) {
        // Step 1: If IsExtensible(O), return false
        if (obj.isExtensible()) {
            return false;
        }

        // Step 3: Let keys be ? O.[[OwnPropertyKeys]]()
        List<PropertyKey> keys = obj.getOwnPropertyKeys();

        // Step 4: For each element k of keys
        for (PropertyKey key : keys) {
            PropertyDescriptor currentDesc = obj.getOwnPropertyDescriptor(key);
            if (context.hasPendingException()) {
                return false;
            }
            if (currentDesc != null) {
                // Step 4.b.i: If currentDesc.[[Configurable]] is true, return false
                if (currentDesc.isConfigurable()) {
                    return false;
                }
                // Step 4.b.ii: If level is frozen and IsDataDescriptor(currentDesc)
                //   and currentDesc.[[Writable]] is true, return false
                if (frozen && currentDesc.isDataDescriptor() && currentDesc.isWritable()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * ToPropertyDescriptor(Obj)
     * ES2024 6.2.6.5 / QuickJS js_obj_to_desc
     * Converts a descriptor object to a PropertyDescriptor, using HasProperty + Get.
     */
    static PropertyDescriptor toPropertyDescriptor(JSContext context, JSObject descObj) {
        PropertyDescriptor desc = new PropertyDescriptor();
        boolean hasAccessor = false;
        boolean hasData = false;

        // Step 2: If HasProperty(Obj, "enumerable"), set [[Enumerable]] to ToBoolean(Get(Obj, "enumerable"))
        if (descObj.has(PropertyKey.ENUMERABLE)) {
            JSValue enumerable = descObj.get(PropertyKey.ENUMERABLE);
            if (context.hasPendingException()) {
                return null;
            }
            desc.setEnumerable(JSTypeChecking.isTruthy(enumerable));
        }

        // Step 3: If HasProperty(Obj, "configurable"), set [[Configurable]] to ToBoolean(Get(Obj, "configurable"))
        if (descObj.has(PropertyKey.CONFIGURABLE)) {
            JSValue configurable = descObj.get(PropertyKey.CONFIGURABLE);
            if (context.hasPendingException()) {
                return null;
            }
            desc.setConfigurable(JSTypeChecking.isTruthy(configurable));
        }

        // Step 4: If HasProperty(Obj, "value"), set [[Value]] to Get(Obj, "value")
        if (descObj.has(PropertyKey.VALUE)) {
            JSValue value = descObj.get(PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return null;
            }
            desc.setValue(value);
            hasData = true;
        }

        // Step 5: If HasProperty(Obj, "writable"), set [[Writable]] to ToBoolean(Get(Obj, "writable"))
        if (descObj.has(PropertyKey.WRITABLE)) {
            JSValue writable = descObj.get(PropertyKey.WRITABLE);
            if (context.hasPendingException()) {
                return null;
            }
            desc.setWritable(JSTypeChecking.isTruthy(writable));
            hasData = true;
        }

        // Step 6: If HasProperty(Obj, "get"), validate and set [[Get]]
        if (descObj.has(PropertyKey.GET)) {
            JSValue getter = descObj.get(PropertyKey.GET);
            if (context.hasPendingException()) {
                return null;
            }
            // Step 7b: If IsCallable(getter) is false and getter is not undefined, throw TypeError
            if (!(getter instanceof JSUndefined) && !JSTypeChecking.isCallable(getter)) {
                context.throwTypeError("Getter must be a function: " + JSTypeConversions.toString(context, getter).value());
                return null;
            }
            if (getter instanceof JSFunction getterFn) {
                desc.setGetter(getterFn);
            } else {
                // get: undefined — mark [[Get]] as present but undefined
                desc.setGetter(null);
            }
            hasAccessor = true;
        }

        // Step 7: If HasProperty(Obj, "set"), validate and set [[Set]]
        if (descObj.has(PropertyKey.SET)) {
            JSValue setter = descObj.get(PropertyKey.SET);
            if (context.hasPendingException()) {
                return null;
            }
            // Step 8b: If IsCallable(setter) is false and setter is not undefined, throw TypeError
            if (!(setter instanceof JSUndefined) && !JSTypeChecking.isCallable(setter)) {
                context.throwTypeError("Setter must be a function: " + JSTypeConversions.toString(context, setter).value());
                return null;
            }
            if (setter instanceof JSFunction setterFn) {
                desc.setSetter(setterFn);
            } else {
                // set: undefined — mark [[Set]] as present but undefined
                desc.setSetter(null);
            }
            hasAccessor = true;
        }

        // Step 8: If accessor and data properties are both present, throw TypeError
        if (hasAccessor && hasData) {
            context.throwTypeError("Invalid property descriptor. Cannot both specify accessors and a value or writable attribute, #<Object>");
            return null;
        }

        return desc;
    }

    /**
     * Object.values(obj)
     * ES2020 19.1.2.21
     * Returns an array of a given object's own enumerable property values.
     */
    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        JSValue arg = args[0];

        // Null and undefined throw TypeError
        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        JSObject obj;
        if (arg instanceof JSObject jsObject) {
            obj = jsObject;
        } else {
            obj = JSTypeConversions.toObject(context, arg);
            if (obj == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue[] fastPathValues = obj.enumerableStringPropertyValuesFastPath();
        if (fastPathValues != null) {
            if (obj.getClass() == JSObject.class) {
                return context.createJSArray(fastPathValues, true);
            }
            return context.createJSArray(fastPathValues);
        }

        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        List<JSValue> values = new ArrayList<>();
        for (PropertyKey key : propertyKeys) {
            if (!key.isSymbol()) {
                PropertyDescriptor descriptor = obj.getOwnPropertyDescriptor(key);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (descriptor != null && descriptor.isEnumerable()) {
                    values.add(obj.get(key));
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                }
            }
        }
        return context.createJSArray(values.toArray(JSValue.NO_ARGS), true);
    }
}
