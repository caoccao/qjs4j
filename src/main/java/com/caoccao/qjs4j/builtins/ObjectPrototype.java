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
     * Object.prototype.__defineGetter__(prop, func)
     * Legacy method for defining getter
     */
    public static JSValue __defineGetter__(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("__defineGetter__ requires 2 arguments");
        }

        if (!(thisArg instanceof JSObject obj)) {
            return context.throwTypeError("__defineGetter__ called on null or undefined");
        }

        JSValue prop = args[0];
        JSValue getter = args[1];

        if (!(getter instanceof JSFunction)) {
            return context.throwTypeError("Getter must be a function");
        }

        PropertyKey key = PropertyKey.fromValue(context, prop);
        PropertyDescriptor desc = PropertyDescriptor.accessorDescriptor(
                (JSFunction) getter,
                null,
                true,
                true
        );

        obj.defineProperty(key, desc);
        return JSUndefined.INSTANCE;
    }

    /**
     * Object.prototype.__defineSetter__(prop, func)
     * Legacy method for defining setter
     */
    public static JSValue __defineSetter__(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("__defineSetter__ requires 2 arguments");
        }

        if (!(thisArg instanceof JSObject obj)) {
            return context.throwTypeError("__defineSetter__ called on null or undefined");
        }

        JSValue prop = args[0];
        JSValue setter = args[1];

        if (!(setter instanceof JSFunction)) {
            return context.throwTypeError("Setter must be a function");
        }

        PropertyKey key = PropertyKey.fromValue(context, prop);
        PropertyDescriptor desc = PropertyDescriptor.accessorDescriptor(
                null,
                (JSFunction) setter,
                true,
                true
        );

        obj.defineProperty(key, desc);
        return JSUndefined.INSTANCE;
    }

    /**
     * Object.prototype.__lookupGetter__(prop)
     * Legacy method for looking up getter
     */
    public static JSValue __lookupGetter__(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 1) {
            return JSUndefined.INSTANCE;
        }

        if (!(thisArg instanceof JSObject obj)) {
            return context.throwTypeError("__lookupGetter__ called on null or undefined");
        }

        JSValue prop = args[0];
        PropertyKey key = PropertyKey.fromValue(context, prop);

        // Walk up the prototype chain
        JSObject current = obj;
        while (current != null) {
            PropertyDescriptor desc = current.getOwnPropertyDescriptor(key);
            if (desc != null && desc.getGetter() != null) {
                return desc.getGetter();
            }
            current = current.getPrototype();
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Object.prototype.__lookupSetter__(prop)
     * Legacy method for looking up setter
     */
    public static JSValue __lookupSetter__(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 1) {
            return JSUndefined.INSTANCE;
        }

        if (!(thisArg instanceof JSObject obj)) {
            return context.throwTypeError("__lookupSetter__ called on null or undefined");
        }

        JSValue prop = args[0];
        PropertyKey key = PropertyKey.fromValue(context, prop);

        // Walk up the prototype chain
        JSObject current = obj;
        while (current != null) {
            PropertyDescriptor desc = current.getOwnPropertyDescriptor(key);
            if (desc != null && desc.getSetter() != null) {
                return desc.getSetter();
            }
            current = current.getPrototype();
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Object.prototype.__proto__ getter
     */
    public static JSValue __proto__Getter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject obj)) {
            return JSNull.INSTANCE;
        }

        JSObject proto = obj.getPrototype();
        return proto != null ? proto : JSNull.INSTANCE;
    }

    /**
     * Object.prototype.__proto__ setter
     */
    public static JSValue __proto__Setter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 1) {
            return JSUndefined.INSTANCE;
        }

        JSValue proto = args[0];

        if (!(thisArg instanceof JSObject obj)) {
            return JSUndefined.INSTANCE;
        }

        // Following QuickJS: __proto__ setter uses throw_flag=TRUE
        JSObject protoObj;
        if (proto instanceof JSNull) {
            protoObj = null;
        } else if (proto instanceof JSObject p) {
            protoObj = p;
        } else {
            // Non-object, non-null values are silently ignored
            return JSUndefined.INSTANCE;
        }

        // Proxies have their own setPrototype logic with trap handling
        if (obj instanceof JSProxy) {
            obj.setPrototype(protoObj);
            return JSUndefined.INSTANCE;
        }

        JSObject.SetPrototypeResult result = obj.setPrototypeChecked(protoObj);
        if (result != JSObject.SetPrototypeResult.SUCCESS) {
            String msg = result == JSObject.SetPrototypeResult.NOT_EXTENSIBLE
                    ? "object is not extensible"
                    : "circular prototype chain";
            return context.throwTypeError(msg);
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Object.assign(target, ...sources)
     */
    public static JSValue assign(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        JSValue targetArg = args[0];
        if (targetArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        if (!(targetArg instanceof JSObject target)) {
            return targetArg;
        }

        for (int i = 1; i < args.length; i++) {
            JSValue source = args[i];

            if (source.isNullOrUndefined()) {
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
    public static JSValue create(JSContext context, JSValue thisArg, JSValue[] args) {
        // Get the prototype argument
        JSValue protoArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Prototype must be null or an object
        JSObject proto = null;
        if (protoArg instanceof JSObject obj) {
            proto = obj;
        } else if (!(protoArg.isNull())) {
            return context.throwTypeError("Object prototype may only be an Object or null");
        }

        // Create new object with the specified prototype
        JSObject newObj = new JSObject(proto);

        // Handle propertiesObject parameter (args[1]) if present
        if (args.length > 1 && !(args[1].isUndefined())) {
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
                JSValue value = descObj.get(PropertyKey.VALUE);
                if (!(value instanceof JSUndefined)) {
                    descriptor.setValue(value);
                }

                // Check for writable
                JSValue writable = descObj.get(PropertyKey.WRITABLE);
                if (!(writable instanceof JSUndefined)) {
                    descriptor.setWritable(JSTypeConversions.toBoolean(writable) == JSBoolean.TRUE);
                }

                // Check for enumerable
                JSValue enumerable = descObj.get(PropertyKey.ENUMERABLE);
                if (!(enumerable instanceof JSUndefined)) {
                    descriptor.setEnumerable(JSTypeConversions.toBoolean(enumerable) == JSBoolean.TRUE);
                }

                // Check for configurable
                JSValue configurable = descObj.get(PropertyKey.CONFIGURABLE);
                if (!(configurable instanceof JSUndefined)) {
                    descriptor.setConfigurable(JSTypeConversions.toBoolean(configurable) == JSBoolean.TRUE);
                }

                // Check for getter
                JSValue getter = descObj.get(PropertyKey.GET);
                if (!(getter instanceof JSUndefined)) {
                    if (!(getter instanceof JSFunction)) {
                        return context.throwTypeError("Getter must be a function");
                    }
                    descriptor.setGetter((JSFunction) getter);
                }

                // Check for setter
                JSValue setter = descObj.get(PropertyKey.SET);
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
     * Object.defineProperty(obj, prop, descriptor)
     */
    public static JSValue defineProperty(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Object.defineProperty requires 3 arguments");
        }

        if (!(args[0] instanceof JSObject obj)) {
            return context.throwTypeError("Object.defineProperty called on non-object");
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);

        // Parse the descriptor object
        if (!(args[2] instanceof JSObject descObj)) {
            return context.throwTypeError("Property descriptor must be an object");
        }

        PropertyDescriptor desc = new PropertyDescriptor();

        // Following QuickJS js_obj_to_desc: use HasProperty to check field existence,
        // not whether the value is undefined. {value: undefined} is a valid descriptor.

        // Check for enumerable
        if (descObj.has(PropertyKey.ENUMERABLE)) {
            JSValue enumerable = descObj.get(PropertyKey.ENUMERABLE, context);
            desc.setEnumerable(JSTypeChecking.isTruthy(enumerable));
        }

        // Check for configurable
        if (descObj.has(PropertyKey.CONFIGURABLE)) {
            JSValue configurable = descObj.get(PropertyKey.CONFIGURABLE, context);
            desc.setConfigurable(JSTypeChecking.isTruthy(configurable));
        }

        // Check for value
        if (descObj.has(PropertyKey.VALUE)) {
            JSValue value = descObj.get(PropertyKey.VALUE, context);
            desc.setValue(value);
        }

        // Check for writable
        if (descObj.has(PropertyKey.WRITABLE)) {
            JSValue writable = descObj.get(PropertyKey.WRITABLE, context);
            desc.setWritable(JSTypeChecking.isTruthy(writable));
        }

        // Check for getter
        if (descObj.has(PropertyKey.GET)) {
            JSValue getter = descObj.get(PropertyKey.GET, context);
            if (!(getter instanceof JSUndefined) && !(getter instanceof JSFunction)) {
                return context.throwTypeError("Getter must be a function");
            }
            desc.setGetter(getter instanceof JSFunction ? (JSFunction) getter : null);
        }

        // Check for setter
        if (descObj.has(PropertyKey.SET)) {
            JSValue setter = descObj.get(PropertyKey.SET, context);
            if (!(setter instanceof JSUndefined) && !(setter instanceof JSFunction)) {
                return context.throwTypeError("Setter must be a function");
            }
            desc.setSetter(setter instanceof JSFunction ? (JSFunction) setter : null);
        }

        if (!obj.defineOwnProperty(key, desc, context)) {
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            return context.throwTypeError("Cannot define property " + key.toPropertyString() + ", object is not extensible");
        }
        return obj;
    }

    /**
     * Object.entries(obj)
     */
    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return context.createJSArray();
        }

        PropertyKey[] enumerableKeys = obj.enumerableKeys();
        JSValue[] entries = new JSValue[enumerableKeys.length];

        for (int i = 0; i < enumerableKeys.length; i++) {
            JSArray entry = context.createJSArray(2);
            entry.set(0, new JSString(enumerableKeys[i].toPropertyString()));
            entry.set(1, obj.get(enumerableKeys[i]));
            entries[i] = entry;
        }

        return context.createJSArray(entries);
    }

    /**
     * Object.freeze(obj)
     * ES2020 19.1.2.6
     * Freezes an object, preventing new properties and making existing properties non-configurable.
     */
    public static JSValue freeze(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public static JSValue hasOwnProperty(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject obj)) {
            return JSBoolean.FALSE;
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        PropertyKey key = PropertyKey.fromValue(context, args[0]);
        return JSBoolean.valueOf(obj.hasOwnProperty(key));
    }

    /**
     * Object.prototype.isPrototypeOf(V)
     */
    public static JSValue isPrototypeOf(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue v = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (!(v instanceof JSObject obj)) {
            return JSBoolean.FALSE;
        }

        if (!(thisArg instanceof JSObject thisObj)) {
            return context.throwTypeError("Object.prototype.isPrototypeOf called on null or undefined");
        }

        // Walk up the prototype chain
        JSObject proto = obj.getPrototype();
        while (proto != null) {
            if (proto == thisObj) {
                return JSBoolean.TRUE;
            }
            proto = proto.getPrototype();
        }

        return JSBoolean.FALSE;
    }

    /**
     * Object.keys(obj)
     */
    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return context.createJSArray();
        }

        PropertyKey[] enumerableKeys = obj.enumerableKeys();
        JSValue[] keyStrings = new JSValue[enumerableKeys.length];

        for (int i = 0; i < enumerableKeys.length; i++) {
            keyStrings[i] = new JSString(enumerableKeys[i].toPropertyString());
        }

        return context.createJSArray(keyStrings);
    }

    /**
     * Object.prototype.propertyIsEnumerable(V)
     */
    public static JSValue propertyIsEnumerable(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue prop = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (!(thisArg instanceof JSObject obj)) {
            return context.throwTypeError("Object.prototype.propertyIsEnumerable called on null or undefined");
        }

        PropertyKey key = PropertyKey.fromValue(context, prop);
        PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);

        if (desc == null) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(desc.isEnumerable());
    }

    /**
     * Object.prototype.toLocaleString()
     */
    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        // Default implementation: call toString
        if (!(thisArg instanceof JSObject obj)) {
            return context.throwTypeError("Object.prototype.toLocaleString called on null or undefined");
        }

        JSValue toStringMethod = obj.get(PropertyKey.TO_STRING);
        if (toStringMethod instanceof JSFunction func) {
            return func.call(context, thisArg, new JSValue[]{});
        }

        return new JSString("[object Object]");
    }

    /**
     * Object.prototype.toString()
     * ES2020 19.1.3.6
     * Returns a string representing the object with Symbol.toStringTag support.
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
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
            PropertyKey toStringTagKey = PropertyKey.SYMBOL_TO_STRING_TAG;
            JSValue tag = obj.get(toStringTagKey, context);

            if (tag instanceof JSString tagStr) {
                return new JSString("[object " + tagStr.value() + "]");
            }

            // Determine built-in type
            if (thisArg instanceof JSArguments) {
                return new JSString("[object Arguments]");
            }
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
            if (thisArg instanceof JSFloat16Array) {
                return new JSString("[object Float16Array]");
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
            if (thisArg instanceof JSBooleanObject) {
                return new JSString("[object Boolean]");
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
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    /**
     * Object.values(obj)
     */
    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return context.createJSArray();
        }

        PropertyKey[] enumerableKeys = obj.enumerableKeys();
        JSValue[] values = new JSValue[enumerableKeys.length];

        for (int i = 0; i < enumerableKeys.length; i++) {
            values[i] = obj.get(enumerableKeys[i]);
        }

        return context.createJSArray(values);
    }
}
