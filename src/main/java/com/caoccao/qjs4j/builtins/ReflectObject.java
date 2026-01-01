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

/**
 * Implementation of Reflect object static methods.
 * Based on ES2020 Reflect specification.
 * The Reflect object provides methods for interceptable JavaScript operations.
 */
public final class ReflectObject {

    /**
     * Reflect.apply(target, thisArg, argumentsList)
     * ES2020 26.1.1
     * Calls a target function with specified this value and arguments.
     */
    public static JSValue apply(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSFunction target)) {
            return context.throwTypeError("Reflect.apply called on non-function");
        }

        JSValue funcThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSValue[] funcArgs = new JSValue[0];
        if (args.length > 2 && args[2] instanceof JSArray argsArray) {
            int length = (int) argsArray.getLength();
            funcArgs = new JSValue[length];
            for (int i = 0; i < length; i++) {
                funcArgs[i] = argsArray.get(i);
            }
        }

        return target.call(context, funcThisArg, funcArgs);
    }

    /**
     * Reflect.construct(target, argumentsList, newTarget)
     * ES2020 26.1.2
     * Acts like the 'new' operator, but as a function.
     * Simplified implementation.
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSFunction target)) {
            return context.throwTypeError("Reflect.construct called on non-function");
        }

        JSValue[] constructorArgs = new JSValue[0];
        if (args.length > 1 && args[1] instanceof JSArray argsArray) {
            int length = (int) argsArray.getLength();
            constructorArgs = new JSValue[length];
            for (int i = 0; i < length; i++) {
                constructorArgs[i] = argsArray.get(i);
            }
        }

        // Simplified: Create a new object and call the constructor with it
        JSObject newObj = new JSObject();
        target.call(context, newObj, constructorArgs);
        return newObj;
    }

    /**
     * Reflect.deleteProperty(target, propertyKey)
     * ES2020 26.1.4
     * Deletes a property from an object (like the 'delete' operator).
     */
    public static JSValue deleteProperty(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.deleteProperty called on non-object");
        }

        if (args.length < 2) {
            return JSBoolean.TRUE;
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        return JSBoolean.valueOf(target.delete(key));
    }

    /**
     * Reflect.get(target, propertyKey, receiver)
     * ES2020 26.1.6
     * Gets the value of a property on an object.
     */
    public static JSValue get(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.get called on non-object");
        }

        if (args.length < 2) {
            return JSUndefined.INSTANCE;
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        return target.get(key);
    }

    /**
     * Reflect.getPrototypeOf(target)
     * ES2020 26.1.8
     * Gets the prototype of an object.
     */
    public static JSValue getPrototypeOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.getPrototypeOf called on non-object");
        }

        JSObject prototype = target.getPrototype();
        return prototype != null ? prototype : JSNull.INSTANCE;
    }

    /**
     * Reflect.has(target, propertyKey)
     * ES2020 26.1.9
     * Checks if an object has a property (like the 'in' operator).
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.has called on non-object");
        }

        if (args.length < 2) {
            return JSBoolean.FALSE;
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        return JSBoolean.valueOf(target.has(key));
    }

    /**
     * Reflect.isExtensible(target)
     * ES2020 26.1.10
     * Checks if an object is extensible.
     */
    public static JSValue isExtensible(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.isExtensible called on non-object");
        }

        return JSBoolean.valueOf(target.isExtensible());
    }

    /**
     * Reflect.ownKeys(target)
     * ES2020 26.1.11
     * Returns an array of the target object's own property keys.
     */
    public static JSValue ownKeys(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.ownKeys called on non-object");
        }

        JSArray result = context.createJSArray();
        for (PropertyKey key : target.ownPropertyKeys()) {
            if (key.isString()) {
                result.push(new JSString(key.asString()));
            } else if (key.isSymbol()) {
                result.push(key.asSymbol());
            }
        }
        return result;
    }

    /**
     * Reflect.preventExtensions(target)
     * ES2020 26.1.12
     * Prevents new properties from being added to an object.
     */
    public static JSValue preventExtensions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.preventExtensions called on non-object");
        }

        target.preventExtensions();
        return JSBoolean.TRUE;
    }

    /**
     * Reflect.set(target, propertyKey, value, receiver)
     * ES2020 26.1.13
     * Sets the value of a property on an object.
     */
    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.set called on non-object");
        }

        if (args.length < 2) {
            return JSBoolean.FALSE;
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        JSValue value = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        target.set(key, value);
        return JSBoolean.TRUE;
    }

    /**
     * Reflect.setPrototypeOf(target, prototype)
     * ES2020 26.1.14
     * Sets the prototype of an object.
     */
    public static JSValue setPrototypeOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.setPrototypeOf called on non-object");
        }

        if (args.length < 2) {
            return JSBoolean.FALSE;
        }

        JSValue prototypeArg = args[1];
        if (prototypeArg instanceof JSNull) {
            target.setPrototype(null);
            return JSBoolean.TRUE;
        } else if (prototypeArg instanceof JSObject prototype) {
            target.setPrototype(prototype);
            return JSBoolean.TRUE;
        } else {
            return context.throwTypeError("Object prototype may only be an Object or null");
        }
    }
}
