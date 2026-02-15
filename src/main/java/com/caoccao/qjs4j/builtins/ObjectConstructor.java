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

import java.util.List;

/**
 * Implementation of Object constructor and static methods.
 * Based on ES2020 Object specification.
 */
public final class ObjectConstructor {

    /**
     * Object.assign(target, ...sources)
     * ES2020 19.1.2.1
     * Copies all enumerable own properties from one or more source objects to a target object.
     */
    public static JSValue assign(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        JSValue targetValue = args[0];
        if (!(targetValue instanceof JSObject target)) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Copy properties from each source object
        for (int i = 1; i < args.length; i++) {
            JSValue source = args[i];
            if (source.isNullOrUndefined()) {
                continue;
            }

            if (source instanceof JSObject sourceObj) {
                List<PropertyKey> keys = sourceObj.getOwnPropertyKeys();
                for (PropertyKey key : keys) {
                    if (key.isString()) {
                        JSValue value = sourceObj.get(key);
                        target.set(key, value);
                    }
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
            JSNumberObject jsNumberObject = new JSNumberObject(num);
            context.transferPrototype(jsNumberObject, JSNumber.NAME);
            return jsNumberObject;
        }

        if (value instanceof JSString str) {
            JSStringObject jsStringObject = new JSStringObject(str);
            context.transferPrototype(jsStringObject, JSString.NAME);
            return jsStringObject;
        }

        if (value instanceof JSBoolean bool) {
            JSBooleanObject jsBooleanObject = new JSBooleanObject(bool);
            context.transferPrototype(jsBooleanObject, JSBoolean.NAME);
            return jsBooleanObject;
        }

        if (value instanceof JSSymbol jsSymbol) {
            JSSymbolObject jsSymbolObject = new JSSymbolObject(jsSymbol);
            context.transferPrototype(jsSymbolObject, JSSymbol.NAME);
            return jsSymbolObject;
        }

        if (value instanceof JSBigInt bigInt) {
            JSBigIntObject jsBigIntObject = new JSBigIntObject(bigInt);
            context.transferPrototype(jsBigIntObject, JSBigInt.NAME);
            return jsBigIntObject;
        }

        // For any other value, return new empty object
        return context.createJSObject();
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
        JSObject newObj = new JSObject();
        newObj.setPrototype(proto);
        // null prototype is allowed - object stays with null prototype

        // Handle propertiesObject parameter (args[1]) if present
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            if (!(args[1] instanceof JSObject propsObj)) {
                return context.throwTypeError("Properties must be an object");
            }

            // Get all own property keys from properties object
            List<PropertyKey> propKeys = propsObj.getOwnPropertyKeys();

            for (PropertyKey key : propKeys) {
                // Get the descriptor for this property
                JSValue descValue = propsObj.get(key);
                if (!(descValue instanceof JSObject descObj)) {
                    return context.throwTypeError("Property descriptor must be an object");
                }

                // Build property descriptor
                PropertyDescriptor descriptor = new PropertyDescriptor();

                // Check for value
                JSValue value = descObj.get("value");
                if (!(value instanceof JSUndefined)) {
                    descriptor.setValue(value);
                }

                // Check for writable
                JSValue writable = descObj.get("writable");
                if (!(writable instanceof JSUndefined)) {
                    descriptor.setWritable(JSTypeConversions.toBoolean(writable) == JSBoolean.TRUE);
                }

                // Check for enumerable
                JSValue enumerable = descObj.get("enumerable");
                if (!(enumerable instanceof JSUndefined)) {
                    descriptor.setEnumerable(JSTypeConversions.toBoolean(enumerable) == JSBoolean.TRUE);
                }

                // Check for configurable
                JSValue configurable = descObj.get("configurable");
                if (!(configurable instanceof JSUndefined)) {
                    descriptor.setConfigurable(JSTypeConversions.toBoolean(configurable) == JSBoolean.TRUE);
                }

                // Check for getter
                JSValue getter = descObj.get("get");
                if (!(getter instanceof JSUndefined)) {
                    if (!(getter instanceof JSFunction)) {
                        return context.throwTypeError("Getter must be a function");
                    }
                    descriptor.setGetter((JSFunction) getter);
                }

                // Check for setter
                JSValue setter = descObj.get("set");
                if (!(setter instanceof JSUndefined)) {
                    if (!(setter instanceof JSFunction)) {
                        return context.throwTypeError("Setter must be a function");
                    }
                    descriptor.setSetter((JSFunction) setter);
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

        if (!(args[1] instanceof JSObject props)) {
            return context.throwTypeError("Properties argument must be an object");
        }

        // Get all enumerable properties from the props object
        List<PropertyKey> propertyKeys = props.getOwnPropertyKeys();

        for (PropertyKey key : propertyKeys) {
            // Only process enumerable properties
            PropertyDescriptor keyDesc = props.getOwnPropertyDescriptor(key);
            if (keyDesc != null && keyDesc.isEnumerable()) {
                JSValue descValue = props.get(key);

                if (!(descValue instanceof JSObject descObj)) {
                    return context.throwTypeError("Property descriptor must be an object");
                }

                // Parse the descriptor object
                PropertyDescriptor desc = new PropertyDescriptor();

                // Check for value
                JSValue value = descObj.get("value");
                if (value != null && !(value instanceof JSUndefined)) {
                    desc.setValue(value);
                }

                // Check for writable
                JSValue writable = descObj.get("writable");
                if (writable != null && !(writable instanceof JSUndefined)) {
                    desc.setWritable(JSTypeChecking.isTruthy(writable));
                }

                // Check for enumerable
                JSValue enumerable = descObj.get("enumerable");
                if (enumerable != null && !(enumerable instanceof JSUndefined)) {
                    desc.setEnumerable(JSTypeChecking.isTruthy(enumerable));
                }

                // Check for configurable
                JSValue configurable = descObj.get("configurable");
                if (configurable != null && !(configurable instanceof JSUndefined)) {
                    desc.setConfigurable(JSTypeChecking.isTruthy(configurable));
                }

                // Check for getter
                JSValue getter = descObj.get("get");
                if (getter != null && !(getter instanceof JSUndefined)) {
                    if (!(getter instanceof JSFunction)) {
                        return context.throwTypeError("Getter must be a function");
                    }
                    desc.setGetter((JSFunction) getter);
                }

                // Check for setter
                JSValue setter = descObj.get("set");
                if (setter != null && !(setter instanceof JSUndefined)) {
                    if (!(setter instanceof JSFunction)) {
                        return context.throwTypeError("Setter must be a function");
                    }
                    desc.setSetter((JSFunction) setter);
                }

                obj.defineProperty(key, desc);
            }
        }

        return obj;
    }

    /**
     * Object.entries(obj)
     * ES2020 19.1.2.5
     * Returns an array of a given object's own enumerable property [key, value] pairs.
     */
    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.entries called on non-object");
        }
        JSValue arg = args[0];
        // Null and undefined throw TypeError
        if (arg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSArray result = context.createJSArray();
        if (arg instanceof JSObject jsObject) {
            List<PropertyKey> propertyKeys = jsObject.getOwnPropertyKeys();
            for (PropertyKey key : propertyKeys) {
                if (key.isString()) {
                    JSArray entry = context.createJSArray();
                    entry.push(new JSString(key.asString()));
                    entry.push(jsObject.get(key));
                    result.push(entry);
                }
            }
        }
        return result;
    }

    /**
     * Object.freeze(obj)
     * ES2020 19.1.2.6
     * Freezes an object.
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

        // Step 1: Prevent extensions
        obj.preventExtensions();

        // Step 2: Get all own property keys (string, symbol, and array indices)
        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();

        // Step 3: For each property, set configurable=false and (for data properties) writable=false
        for (PropertyKey key : propertyKeys) {
            PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);
            if (desc == null) {
                continue; // Skip if property doesn't exist (shouldn't happen)
            }

            // Modify the existing descriptor:
            // - Always set configurable to false
            // - For data properties, also set writable to false
            desc.setConfigurable(false);

            if (desc.isDataDescriptor()) {
                // Only modify writable if it's a data property
                desc.setWritable(false);
            }

            // Update the property with the modified descriptor
            obj.defineProperty(key, desc);
        }

        // Step 4: Mark the object as frozen (sets frozen, sealed, and extensible flags)
        obj.freeze();

        return arg;
    }

    /**
     * Object.fromEntries(iterable)
     * ES2019 19.1.2.5
     * Creates an object from an iterable of key-value pairs.
     */
    public static JSValue fromEntries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.fromEntries requires an iterable argument");
        }

        JSValue iterable = args[0];

        // Special case: if it's a JSArray, iterate directly for efficiency
        if (iterable instanceof JSArray arr) {
            JSObject result = context.createJSObject();

            for (int i = 0; i < arr.getLength(); i++) {
                JSValue entry = arr.get(i);

                // Each entry should be an array-like object with [key, value]
                if (!(entry instanceof JSObject entryObj)) {
                    return context.throwTypeError("Iterator value must be an object");
                }

                // Get key (element 0) and value (element 1)
                JSValue keyValue;
                JSValue entryValue;

                // If it's an array, use integer index access
                if (entryObj instanceof JSArray entryArr) {
                    keyValue = entryArr.get(0);
                    entryValue = entryArr.get(1);
                } else {
                    // Otherwise use property key access
                    keyValue = entryObj.get("0");
                    entryValue = entryObj.get("1");
                }

                // Convert key to string
                JSString keyString = JSTypeConversions.toString(context, keyValue);
                PropertyKey key = PropertyKey.fromString(keyString.value());

                // Set property
                result.set(key, entryValue);
            }

            return result;
        }

        // General case: use iterator protocol
        // Get the iterator method
        JSValue iteratorMethod = null;
        if (iterable instanceof JSObject obj) {
            PropertyKey iteratorKey = PropertyKey.fromSymbol(JSSymbol.ITERATOR);
            iteratorMethod = obj.get(iteratorKey);
        }

        if (!(iteratorMethod instanceof JSFunction iterFunc)) {
            return context.throwTypeError("Object.fromEntries requires an iterable");
        }

        // Call the iterator method to get the iterator
        JSValue iteratorValue = iterFunc.call(context, iterable, new JSValue[0]);
        if (!(iteratorValue instanceof JSIterator iterator)) {
            return context.throwTypeError("Iterator method must return an iterator");
        }

        // Create result object
        JSObject result = context.createJSObject();

        // Iterate over entries
        while (true) {
            JSObject iterResult = iterator.next();
            JSValue doneValue = iterResult.get("done");
            boolean done = doneValue instanceof JSBoolean && ((JSBoolean) doneValue).value();

            if (done) {
                break;
            }

            JSValue value = iterResult.get("value");

            // Each entry must be an object with at least 2 elements
            if (!(value instanceof JSObject entryObj)) {
                return context.throwTypeError("Iterator value must be an object");
            }

            // Get key (element 0) and value (element 1)
            JSValue keyValue;
            JSValue entryValue;

            // If it's an array, use integer index access
            if (entryObj instanceof JSArray entryArr) {
                keyValue = entryArr.get(0);
                entryValue = entryArr.get(1);
            } else {
                // Otherwise use property key access
                keyValue = entryObj.get("0");
                entryValue = entryObj.get("1");
            }

            // Convert key to string
            JSString keyString = JSTypeConversions.toString(context, keyValue);
            PropertyKey key = PropertyKey.fromString(keyString.value());

            // Set property
            result.set(key, entryValue);
        }

        return result;
    }

    /**
     * Object.getOwnPropertyDescriptor(obj, prop)
     * ES2015 19.1.2.6
     * Returns a property descriptor for an own property of an object.
     */
    public static JSValue getOwnPropertyDescriptor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return JSUndefined.INSTANCE;
        }

        JSValue objArg = args[0];
        if (!(objArg instanceof JSObject obj)) {
            return context.throwTypeError("Object.getOwnPropertyDescriptor called on non-object");
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);

        if (desc == null) {
            return JSUndefined.INSTANCE;
        }

        // Convert PropertyDescriptor to a descriptor object
        JSObject descObj = context.createJSObject();

        if (desc.isDataDescriptor()) {
            descObj.set("value", desc.getValue() != null ? desc.getValue() : JSUndefined.INSTANCE);
            descObj.set("writable", JSBoolean.valueOf(desc.isWritable()));
        } else if (desc.isAccessorDescriptor()) {
            descObj.set("get", desc.getGetter() != null ? desc.getGetter() : JSUndefined.INSTANCE);
            descObj.set("set", desc.getSetter() != null ? desc.getSetter() : JSUndefined.INSTANCE);
        }

        descObj.set("enumerable", JSBoolean.valueOf(desc.isEnumerable()));
        descObj.set("configurable", JSBoolean.valueOf(desc.isConfigurable()));

        return descObj;
    }

    /**
     * Object.getOwnPropertyDescriptors(obj)
     * ES2017 19.1.2.7
     * Returns all own property descriptors of an object.
     */
    public static JSValue getOwnPropertyDescriptors(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.getOwnPropertyDescriptors called on non-object");
        }

        JSValue objArg = args[0];
        if (!(objArg instanceof JSObject obj)) {
            return context.throwTypeError("Object.getOwnPropertyDescriptors called on non-object");
        }

        JSObject result = context.createJSObject();
        List<PropertyKey> keys = obj.getOwnPropertyKeys();

        for (PropertyKey key : keys) {
            PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);
            if (desc != null) {
                JSObject descObj = context.createJSObject();

                if (desc.isDataDescriptor()) {
                    descObj.set("value", desc.getValue() != null ? desc.getValue() : JSUndefined.INSTANCE);
                    descObj.set("writable", JSBoolean.valueOf(desc.isWritable()));
                } else if (desc.isAccessorDescriptor()) {
                    descObj.set("get", desc.getGetter() != null ? desc.getGetter() : JSUndefined.INSTANCE);
                    descObj.set("set", desc.getSetter() != null ? desc.getSetter() : JSUndefined.INSTANCE);
                }

                descObj.set("enumerable", JSBoolean.valueOf(desc.isEnumerable()));
                descObj.set("configurable", JSBoolean.valueOf(desc.isConfigurable()));

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
        if (args.length > 0) {
            JSValue objArg = args[0];
            if (objArg instanceof JSObject jsObject) {
                JSArray result = context.createJSArray();
                List<PropertyKey> keys = jsObject.getOwnPropertyKeys();
                for (PropertyKey key : keys) {
                    // Only include string keys (not symbols)
                    if (!key.isSymbol()) {
                        result.push(new JSString(key.toPropertyString()));
                    }
                }
                return result;
            } else {
                return context.throwTypeError("Cannot convert undefined or null to object");
            }
        }
        return context.throwTypeError("Cannot convert undefined or null to object");
    }

    /**
     * Object.getOwnPropertySymbols(obj)
     * ES2015 19.1.2.9
     * Returns an array of all own symbol properties.
     */
    public static JSValue getOwnPropertySymbols(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.createJSArray();
        }

        JSValue objArg = args[0];
        if (!(objArg instanceof JSObject obj)) {
            return context.createJSArray();
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
            return context.throwTypeError("Object.getPrototypeOf called on non-object");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return context.throwTypeError("Object.getPrototypeOf called on non-object");
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
        if (!(items instanceof JSArray arr)) {
            return context.throwTypeError("First argument must be an array");
        }

        if (!(args[1] instanceof JSFunction callback)) {
            return context.throwTypeError("Second argument must be a function");
        }

        JSObject result = context.createJSObject();

        long length = arr.getLength();
        for (long i = 0; i < length; i++) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, new JSNumber(i)};
            JSValue keyValue = callback.call(context, JSUndefined.INSTANCE, callbackArgs);

            // Convert key to string
            JSString keyString = JSTypeConversions.toString(context, keyValue);
            String key = keyString.value();

            // Get or create array for this key
            JSValue existingGroup = result.get(key);
            JSArray group;
            if (existingGroup instanceof JSArray) {
                group = (JSArray) existingGroup;
            } else {
                group = context.createJSArray();
                result.set(key, group);
            }

            // Add element to group
            group.push(element);
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
            return context.throwTypeError("Object.hasOwn requires at least 1 argument");
        }

        JSValue objValue = args[0];
        if (!(objValue instanceof JSObject obj)) {
            return context.throwTypeError("Object.hasOwn called on non-object");
        }

        if (args.length < 2) {
            return JSBoolean.FALSE;
        }

        JSString propName = JSTypeConversions.toString(context, args[1]);
        PropertyKey key = PropertyKey.fromString(propName.value());

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

        return JSBoolean.valueOf(obj.isFrozen());
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

        return JSBoolean.valueOf(obj.isSealed());
    }

    /**
     * Object.keys(obj)
     * ES2020 19.1.2.16
     * Returns an array of a given object's own enumerable property names.
     */
    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.keys called on non-object");
        }

        JSValue arg = args[0];

        // Null and undefined throw TypeError
        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            // Other primitive values are coerced to objects (return empty array)
            return context.createJSArray();
        }

        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
        JSArray result = context.createJSArray();

        for (PropertyKey key : propertyKeys) {
            if (key.isString()) {
                // Check if property is enumerable
                PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);
                if (desc != null && desc.isEnumerable()) {
                    result.push(new JSString(key.asString()));
                }
            }
        }

        return result;
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

        // Step 1: Prevent extensions
        obj.preventExtensions();

        // Step 2: Get all own property keys (string, symbol, and array indices)
        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();

        // Step 3: For each property, set configurable=false (but keep writable unchanged)
        for (PropertyKey key : propertyKeys) {
            PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);
            if (desc == null) {
                continue; // Skip if property doesn't exist (shouldn't happen)
            }

            // Modify the existing descriptor:
            // - Set configurable to false
            // - Do NOT modify writable (this is the difference from freeze)
            desc.setConfigurable(false);

            // Update the property with the modified descriptor
            obj.defineProperty(key, desc);
        }

        // Step 4: Mark the object as sealed (sets sealed and extensible flags)
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

        JSValue objValue = args[0];
        if (!(objValue instanceof JSObject obj)) {
            return context.throwTypeError("Object.setPrototypeOf called on non-object");
        }

        JSValue protoValue = args[1];
        JSObject proto;
        if (protoValue instanceof JSObject protoObj) {
            proto = protoObj;
        } else if (protoValue instanceof JSNull) {
            proto = null;
        } else {
            return context.throwTypeError("Object prototype may only be an Object or null");
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
     * Object.values(obj)
     * ES2020 19.1.2.21
     * Returns an array of a given object's own enumerable property values.
     */
    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Object.values called on non-object");
        }

        JSValue arg = args[0];

        // Null and undefined throw TypeError
        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return context.createJSArray();
        }

        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
        JSArray result = context.createJSArray();

        for (PropertyKey key : propertyKeys) {
            if (key.isString()) {
                JSValue value = obj.get(key);
                result.push(value);
            }
        }

        return result;
    }
}
