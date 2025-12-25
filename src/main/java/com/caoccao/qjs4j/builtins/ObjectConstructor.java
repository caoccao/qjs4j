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

import java.util.ArrayList;
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
        PropertyKey key = PropertyKey.fromString(propName.getValue());

        return JSBoolean.valueOf(obj.hasOwnProperty(key));
    }
}
