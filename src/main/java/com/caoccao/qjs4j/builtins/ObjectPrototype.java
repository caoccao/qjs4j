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
 * Object prototype methods and constructors.
 * Implements ECMAScript Object built-in methods.
 *
 * @see <a href="https://tc39.es/ecma262/#sec-object-objects">ECMAScript Object Objects</a>
 */
public final class ObjectPrototype {

    /**
     * Object.create(proto[, propertiesObject])
     * Creates a new object with the specified prototype.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-object.create">ECMAScript Object.create</a>
     */
    public static JSValue create(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Get the prototype argument
        JSValue protoArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Prototype must be null or an object
        JSObject proto = null;
        if (protoArg instanceof JSObject obj) {
            proto = obj;
        } else if (!(protoArg instanceof JSNull)) {
            return ctx.throwError("TypeError", "Object prototype may only be an Object or null");
        }

        // Create new object with the specified prototype
        JSObject newObj = new JSObject(proto);

        // If properties object is provided, define properties
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            // In full implementation, would call Object.defineProperties
            // For now, skip this
        }

        return newObj;
    }

    /**
     * Object.defineProperty(obj, prop, descriptor)
     */
    public static JSValue defineProperty(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return ctx.throwError("TypeError", "Object.defineProperty requires 3 arguments");
        }

        if (!(args[0] instanceof JSObject obj)) {
            return ctx.throwError("TypeError", "Object.defineProperty called on non-object");
        }

        PropertyKey key = PropertyKey.fromValue(args[1]);
        PropertyDescriptor desc = PropertyDescriptor.defaultData(args[2]);
        obj.defineProperty(key, desc);
        return obj;
    }

    /**
     * Object.keys(obj)
     */
    public static JSValue keys(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return new JSArray();
        }

        PropertyKey[] enumerableKeys = obj.enumerableKeys();
        JSValue[] keyStrings = new JSValue[enumerableKeys.length];

        for (int i = 0; i < enumerableKeys.length; i++) {
            keyStrings[i] = new JSString(enumerableKeys[i].toPropertyString());
        }

        return new JSArray(keyStrings);
    }

    /**
     * Object.values(obj)
     */
    public static JSValue values(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return new JSArray();
        }

        PropertyKey[] enumerableKeys = obj.enumerableKeys();
        JSValue[] values = new JSValue[enumerableKeys.length];

        for (int i = 0; i < enumerableKeys.length; i++) {
            values[i] = obj.get(enumerableKeys[i]);
        }

        return new JSArray(values);
    }

    /**
     * Object.entries(obj)
     */
    public static JSValue entries(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return new JSArray();
        }

        PropertyKey[] enumerableKeys = obj.enumerableKeys();
        JSValue[] entries = new JSValue[enumerableKeys.length];

        for (int i = 0; i < enumerableKeys.length; i++) {
            JSArray entry = new JSArray(2);
            entry.set(0, new JSString(enumerableKeys[i].toPropertyString()));
            entry.set(1, obj.get(enumerableKeys[i]));
            entries[i] = entry;
        }

        return new JSArray(entries);
    }

    /**
     * Object.assign(target, ...sources)
     */
    public static JSValue assign(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        JSValue targetArg = args[0];
        if (targetArg instanceof JSNull || targetArg instanceof JSUndefined) {
            return ctx.throwError("TypeError", "Cannot convert undefined or null to object");
        }

        if (!(targetArg instanceof JSObject target)) {
            return targetArg;
        }

        for (int i = 1; i < args.length; i++) {
            JSValue source = args[i];

            if (source instanceof JSNull || source instanceof JSUndefined) {
                continue;
            }

            if (source instanceof JSObject srcObj) {
                PropertyKey[] keys = srcObj.enumerableKeys();
                for (PropertyKey key : keys) {
                    target.set(key, srcObj.get(key));
                }
            }
        }

        return target;
    }

    /**
     * Object.freeze(obj)
     */
    public static JSValue freeze(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (!(arg instanceof JSObject obj)) {
            return arg;
        }

        // In full implementation, would freeze the object
        return obj;
    }

    /**
     * Object.prototype.toString()
     */
    public static JSValue toString(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSUndefined) {
            return new JSString("[object Undefined]");
        }
        if (thisArg instanceof JSNull) {
            return new JSString("[object Null]");
        }
        if (thisArg instanceof JSArray) {
            return new JSString("[object Array]");
        }
        if (thisArg instanceof JSFunction) {
            return new JSString("[object Function]");
        }
        if (thisArg instanceof JSObject) {
            return new JSString("[object Object]");
        }
        if (thisArg instanceof JSString) {
            return new JSString("[object String]");
        }
        if (thisArg instanceof JSNumber) {
            return new JSString("[object Number]");
        }
        if (thisArg instanceof JSBoolean) {
            return new JSString("[object Boolean]");
        }
        return new JSString("[object Unknown]");
    }

    /**
     * Object.prototype.valueOf()
     */
    public static JSValue valueOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    /**
     * Object.prototype.hasOwnProperty(prop)
     */
    public static JSValue hasOwnProperty(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject obj)) {
            return JSBoolean.FALSE;
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        PropertyKey key = PropertyKey.fromValue(args[0]);
        return JSBoolean.valueOf(obj.hasOwnProperty(key));
    }
}
