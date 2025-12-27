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
 * Object prototype methods and constructors.
 * Implements ECMAScript Object built-in methods.
 *
 * @see <a href="https://tc39.es/ecma262/#sec-object-objects">ECMAScript Object Objects</a>
 */
public final class ObjectPrototype {

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

        // Handle propertiesObject parameter (args[1]) if present
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            if (!(args[1] instanceof JSObject propsObj)) {
                return ctx.throwError("TypeError", "Properties must be an object");
            }

            // Get all own property keys from properties object
            List<PropertyKey> propKeys = propsObj.getOwnPropertyKeys();

            for (PropertyKey key : propKeys) {
                // Get the descriptor for this property
                JSValue descValue = propsObj.get(key);
                if (!(descValue instanceof JSObject descObj)) {
                    return ctx.throwError("TypeError", "Property descriptor must be an object");
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
                        return ctx.throwError("TypeError", "Getter must be a function");
                    }
                    descriptor.setGetter((JSFunction) getter);
                }

                // Check for setter
                JSValue setter = descObj.get("set");
                if (!(setter instanceof JSUndefined)) {
                    if (!(setter instanceof JSFunction)) {
                        return ctx.throwError("TypeError", "Setter must be a function");
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

        // Parse the descriptor object
        if (!(args[2] instanceof JSObject descObj)) {
            return ctx.throwError("TypeError", "Property descriptor must be an object");
        }

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
                return ctx.throwError("TypeError", "Getter must be a function");
            }
            desc.setGetter((JSFunction) getter);
        }

        // Check for setter
        JSValue setter = descObj.get("set");
        if (setter != null && !(setter instanceof JSUndefined)) {
            if (!(setter instanceof JSFunction)) {
                return ctx.throwError("TypeError", "Setter must be a function");
            }
            desc.setSetter((JSFunction) setter);
        }

        obj.defineProperty(key, desc);
        return obj;
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
     * Object.freeze(obj)
     * ES2020 19.1.2.6
     * Freezes an object, preventing new properties and making existing properties non-configurable.
     */
    public static JSValue freeze(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (!(arg instanceof JSObject obj)) {
            return arg;
        }

        obj.freeze();
        return arg;
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
     * Object.prototype.toString()
     * ES2020 19.1.3.6
     * Returns a string representing the object with Symbol.toStringTag support.
     */
    public static JSValue toString(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Handle primitives
        if (thisArg instanceof JSUndefined) {
            return new JSString("[object Undefined]");
        }
        if (thisArg instanceof JSNull) {
            return new JSString("[object Null]");
        }

        // Check for Function before Object (functions may not extend JSObject)
        if (thisArg instanceof JSFunction) {
            return new JSString("[object Function]");
        }

        // For objects, check Symbol.toStringTag first
        if (thisArg instanceof JSObject obj) {
            // Try to get Symbol.toStringTag
            PropertyKey toStringTagKey = PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG);
            JSValue tag = obj.get(toStringTagKey);

            if (tag instanceof JSString tagStr) {
                return new JSString("[object " + tagStr.value() + "]");
            }

            // Determine built-in type
            if (thisArg instanceof JSArray) {
                return new JSString("[object Array]");
            }
            if (thisArg instanceof JSPromise) {
                return new JSString("[object Promise]");
            }
            if (thisArg instanceof JSMap) {
                return new JSString("[object Map]");
            }
            if (thisArg instanceof JSSet) {
                return new JSString("[object Set]");
            }
            if (thisArg instanceof JSWeakMap) {
                return new JSString("[object WeakMap]");
            }
            if (thisArg instanceof JSWeakSet) {
                return new JSString("[object WeakSet]");
            }
            if (thisArg instanceof JSSharedArrayBuffer) {
                return new JSString("[object SharedArrayBuffer]");
            }
            if (thisArg instanceof JSArrayBuffer) {
                return new JSString("[object ArrayBuffer]");
            }
            if (thisArg instanceof JSDataView) {
                return new JSString("[object DataView]");
            }
            // TypedArray instances - check specific types
            if (thisArg instanceof JSInt8Array) {
                return new JSString("[object Int8Array]");
            }
            if (thisArg instanceof JSUint8Array) {
                return new JSString("[object Uint8Array]");
            }
            if (thisArg instanceof JSUint8ClampedArray) {
                return new JSString("[object Uint8ClampedArray]");
            }
            if (thisArg instanceof JSInt16Array) {
                return new JSString("[object Int16Array]");
            }
            if (thisArg instanceof JSUint16Array) {
                return new JSString("[object Uint16Array]");
            }
            if (thisArg instanceof JSInt32Array) {
                return new JSString("[object Int32Array]");
            }
            if (thisArg instanceof JSUint32Array) {
                return new JSString("[object Uint32Array]");
            }
            if (thisArg instanceof JSFloat32Array) {
                return new JSString("[object Float32Array]");
            }
            if (thisArg instanceof JSFloat64Array) {
                return new JSString("[object Float64Array]");
            }
            if (thisArg instanceof JSRegExp) {
                return new JSString("[object RegExp]");
            }
            if (thisArg instanceof JSDate) {
                return new JSString("[object Date]");
            }

            // Default for generic objects
            return new JSString("[object Object]");
        }

        // Primitives (when boxed)
        if (thisArg instanceof JSString) {
            return new JSString("[object String]");
        }
        if (thisArg instanceof JSNumber) {
            return new JSString("[object Number]");
        }
        if (thisArg instanceof JSBoolean) {
            return new JSString("[object Boolean]");
        }
        if (thisArg instanceof JSSymbol) {
            return new JSString("[object Symbol]");
        }
        if (thisArg instanceof JSBigInt) {
            return new JSString("[object BigInt]");
        }

        return new JSString("[object Object]");
    }

    /**
     * Object.prototype.valueOf()
     */
    public static JSValue valueOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return thisArg;
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
}
