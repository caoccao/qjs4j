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
     * Object.keys(obj)
     * ES2020 19.1.2.16
     * Returns an array of a given object's own enumerable property names.
     */
    public static JSValue keys(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.keys called on non-object");
        }

        JSValue arg = args[0];

        // Null and undefined throw TypeError
        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            // Other primitive values are coerced to objects (return empty array)
            return new JSArray();
        }

        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
        JSArray result = new JSArray();

        for (PropertyKey key : propertyKeys) {
            if (key.isString()) {
                result.push(new JSString(key.asString()));
            }
        }

        return result;
    }

    /**
     * Object.values(obj)
     * ES2020 19.1.2.21
     * Returns an array of a given object's own enumerable property values.
     */
    public static JSValue values(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.values called on non-object");
        }

        JSValue arg = args[0];

        // Null and undefined throw TypeError
        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return new JSArray();
        }

        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
        JSArray result = new JSArray();

        for (PropertyKey key : propertyKeys) {
            if (key.isString()) {
                JSValue value = obj.get(key);
                result.push(value);
            }
        }

        return result;
    }

    /**
     * Object.entries(obj)
     * ES2020 19.1.2.5
     * Returns an array of a given object's own enumerable property [key, value] pairs.
     */
    public static JSValue entries(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.entries called on non-object");
        }

        JSValue arg = args[0];

        // Null and undefined throw TypeError
        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return new JSArray();
        }

        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
        JSArray result = new JSArray();

        for (PropertyKey key : propertyKeys) {
            if (key.isString()) {
                JSArray entry = new JSArray();
                entry.push(new JSString(key.asString()));
                entry.push(obj.get(key));
                result.push(entry);
            }
        }

        return result;
    }

    /**
     * Object.assign(target, ...sources)
     * ES2020 19.1.2.1
     * Copies all enumerable own properties from one or more source objects to a target object.
     */
    public static JSValue assign(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        JSValue targetValue = args[0];
        if (!(targetValue instanceof JSObject target)) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        // Copy properties from each source object
        for (int i = 1; i < args.length; i++) {
            JSValue source = args[i];
            if (source instanceof JSNull || source instanceof JSUndefined) {
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
     * Object.create(proto, propertiesObject)
     * ES2020 19.1.2.2
     * Creates a new object with the specified prototype object.
     */
    public static JSValue create(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object prototype may only be an Object or null");
        }

        JSValue proto = args[0];

        // Create new object
        JSObject newObj = new JSObject();

        // Set prototype
        if (proto instanceof JSObject protoObj) {
            newObj.setPrototype(protoObj);
        } else if (!(proto instanceof JSNull)) {
            return ctx.throwError("TypeError", "Object prototype may only be an Object or null");
        }
        // null prototype is allowed - object stays with null prototype

        // TODO: Handle propertiesObject parameter (args[1]) if present
        // For now, simplified implementation

        return newObj;
    }

    /**
     * Object.getPrototypeOf(obj)
     * ES2020 19.1.2.9
     * Returns the prototype of the specified object.
     */
    public static JSValue getPrototypeOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.getPrototypeOf called on non-object");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return ctx.throwError("TypeError", "Object.getPrototypeOf called on non-object");
        }

        JSObject prototype = obj.getPrototype();
        return prototype != null ? prototype : JSNull.INSTANCE;
    }

    /**
     * Object.setPrototypeOf(obj, prototype)
     * ES2020 19.1.2.18
     * Sets the prototype of a specified object.
     */
    public static JSValue setPrototypeOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return ctx.throwError("TypeError", "Object.setPrototypeOf requires 2 arguments");
        }

        JSValue objValue = args[0];
        if (!(objValue instanceof JSObject obj)) {
            return ctx.throwError("TypeError", "Object.setPrototypeOf called on non-object");
        }

        JSValue protoValue = args[1];
        if (protoValue instanceof JSObject proto) {
            obj.setPrototype(proto);
        } else if (protoValue instanceof JSNull) {
            obj.setPrototype(null);
        } else {
            return ctx.throwError("TypeError", "Object prototype may only be an Object or null");
        }

        return obj;
    }

    /**
     * Object.freeze(obj)
     * ES2020 19.1.2.6
     * Freezes an object (simplified implementation).
     */
    public static JSValue freeze(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.freeze called on non-object");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return arg; // Primitives are returned as-is
        }

        obj.freeze();
        return arg;
    }

    /**
     * Object.seal(obj)
     * ES2020 19.1.2.17
     * Seals an object (simplified implementation).
     */
    public static JSValue seal(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.seal called on non-object");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return arg; // Primitives are returned as-is
        }

        obj.seal();
        return arg;
    }

    /**
     * Object.isFrozen(obj)
     * ES2020 19.1.2.12
     */
    public static JSValue isFrozen(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.isFrozen called without arguments");
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
    public static JSValue isSealed(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.isSealed called without arguments");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSObject obj)) {
            return JSBoolean.TRUE; // Primitives are always sealed
        }

        return JSBoolean.valueOf(obj.isSealed());
    }

    /**
     * Object.hasOwnProperty(obj, prop)
     * This is actually Object.prototype.hasOwnProperty, but we implement it here.
     */
    public static JSValue hasOwnProperty(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject obj)) {
            return ctx.throwError("TypeError", "hasOwnProperty called on non-object");
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        JSString propName = JSTypeConversions.toString(args[0]);
        PropertyKey key = PropertyKey.fromString(propName.value());

        return JSBoolean.valueOf(obj.hasOwnProperty(key));
    }

    /**
     * Object.hasOwn(obj, prop)
     * ES2022 20.1.2.10
     * Static method to check if an object has a property as its own property.
     */
    public static JSValue hasOwn(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.hasOwn requires at least 1 argument");
        }

        JSValue objValue = args[0];
        if (!(objValue instanceof JSObject obj)) {
            return ctx.throwError("TypeError", "Object.hasOwn called on non-object");
        }

        if (args.length < 2) {
            return JSBoolean.FALSE;
        }

        JSString propName = JSTypeConversions.toString(args[1]);
        PropertyKey key = PropertyKey.fromString(propName.value());

        return JSBoolean.valueOf(obj.hasOwnProperty(key));
    }

    /**
     * Object.fromEntries(iterable)
     * ES2019 19.1.2.5
     * Creates an object from an iterable of key-value pairs.
     */
    public static JSValue fromEntries(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Object.fromEntries requires an iterable argument");
        }

        JSValue iterable = args[0];

        // Special case: if it's a JSArray, iterate directly for efficiency
        if (iterable instanceof JSArray arr) {
            JSObject result = new JSObject();

            for (int i = 0; i < arr.getLength(); i++) {
                JSValue entry = arr.get(i);

                // Each entry should be an array-like object with [key, value]
                if (!(entry instanceof JSObject entryObj)) {
                    return ctx.throwError("TypeError", "Iterator value must be an object");
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
                JSString keyString = JSTypeConversions.toString(keyValue);
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
            return ctx.throwError("TypeError", "Object.fromEntries requires an iterable");
        }

        // Call the iterator method to get the iterator
        JSValue iteratorValue = iterFunc.call(ctx, iterable, new JSValue[0]);
        if (!(iteratorValue instanceof JSIterator iterator)) {
            return ctx.throwError("TypeError", "Iterator method must return an iterator");
        }

        // Create result object
        JSObject result = new JSObject();

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
                return ctx.throwError("TypeError", "Iterator value must be an object");
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
            JSString keyString = JSTypeConversions.toString(keyValue);
            PropertyKey key = PropertyKey.fromString(keyString.value());

            // Set property
            result.set(key, entryValue);
        }

        return result;
    }

    /**
     * Object.groupBy(items, callbackFn)
     * ES2024 20.1.2.11
     * Groups array elements by a key returned from the callback function.
     */
    public static JSValue groupBy(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return ctx.throwError("TypeError", "Object.groupBy requires 2 arguments");
        }

        JSValue items = args[0];
        if (!(items instanceof JSArray arr)) {
            return ctx.throwError("TypeError", "First argument must be an array");
        }

        if (!(args[1] instanceof JSFunction callback)) {
            return ctx.throwError("TypeError", "Second argument must be a function");
        }

        JSObject result = new JSObject();

        long length = arr.getLength();
        for (long i = 0; i < length; i++) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, new JSNumber(i)};
            JSValue keyValue = callback.call(ctx, JSUndefined.INSTANCE, callbackArgs);

            // Convert key to string
            JSString keyString = JSTypeConversions.toString(keyValue);
            String key = keyString.value();

            // Get or create array for this key
            JSValue existingGroup = result.get(key);
            JSArray group;
            if (existingGroup instanceof JSArray) {
                group = (JSArray) existingGroup;
            } else {
                group = new JSArray();
                result.set(key, group);
            }

            // Add element to group
            group.push(element);
        }

        return result;
    }
}
