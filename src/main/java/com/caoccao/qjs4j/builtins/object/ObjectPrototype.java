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

package com.caoccao.qjs4j.builtins.object;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.date.JSDate;
import com.caoccao.qjs4j.core.error.JSError;
import com.caoccao.qjs4j.core.regexp.JSRegExp;

import java.util.List;

/**
 * Object prototype methods and constructors. Implements ECMAScript Object built-in methods.
 *
 * @see <a href="https://tc39.es/ecma262/#sec-object-objects">ECMAScript Object Objects</a>
 */
public final class ObjectPrototype {
    /**
     * Object.prototype.__defineGetter__(prop, func) Legacy method for defining getter
     */
    public static JSValue __defineGetter__(JSContext context, JSValue thisArg, JSValue[] args) {
        return defineAccessor(context, thisArg, args, true);
    }

    /**
     * Object.prototype.__defineSetter__(prop, func) Legacy method for defining setter
     */
    public static JSValue __defineSetter__(JSContext context, JSValue thisArg, JSValue[] args) {
        return defineAccessor(context, thisArg, args, false);
    }

    /**
     * Object.prototype.__lookupGetter__(prop) Legacy method for looking up getter
     */
    public static JSValue __lookupGetter__(JSContext context, JSValue thisArg, JSValue[] args) {
        return lookupAccessor(context, thisArg, args, true);
    }

    /**
     * Object.prototype.__lookupSetter__(prop) Legacy method for looking up setter
     */
    public static JSValue __lookupSetter__(JSContext context, JSValue thisArg, JSValue[] args) {
        return lookupAccessor(context, thisArg, args, false);
    }

    /**
     * Object.prototype.__proto__ getter
     */
    public static JSValue __proto__Getter(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES B.2.2.1.1: Step 1: Let O be ? ToObject(this value)
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject obj = ObjectConstructor.objectArgument(context, thisArg);
        if (obj == null) {
            return JSUndefined.INSTANCE;
        }

        return ObjectConstructor.prototypeOf(obj);
    }

    /**
     * Object.prototype.__proto__ setter
     */
    public static JSValue __proto__Setter(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES B.2.2.1.2: Step 1: Let O be ? RequireObjectCoercible(this value)
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        if (args.length < 1) {
            return JSUndefined.INSTANCE;
        }

        JSValue proto = args[0];

        // Step 3: If Type(O) is not Object, return undefined (primitives)
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
     * Object.create(proto[, propertiesObject]) Creates a new object with the specified prototype.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-object.create">ECMAScript Object.create</a>
     */
    public static JSValue create(JSContext context, JSValue thisArg, JSValue[] args) {
        // Get the prototype argument
        JSValue protoArg = JSValue.argumentOrUndefined(args, 0);

        // Prototype must be null or an object
        JSObject proto = null;
        if (protoArg instanceof JSObject obj) {
            proto = obj;
        } else if (!(protoArg.isNull())) {
            return context.throwTypeError("Object prototype may only be an Object or null");
        }

        // Create new object with the specified prototype
        JSObject newObj = new JSObject(context, proto);

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

    private static JSValue defineAccessor(JSContext context, JSValue thisArg, JSValue[] args, boolean getter) {
        String method = getter ? "__defineGetter__" : "__defineSetter__";
        if (args.length < 2) {
            return context.throwTypeError(method + " requires 2 arguments");
        }

        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError(method + " called on null or undefined");
        }
        JSObject obj = ObjectConstructor.objectArgument(context, thisArg);
        if (obj == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue accessor = args[1];

        if (!(accessor instanceof JSFunction function)) {
            return context.throwTypeError(getter ? "Getter must be a function" : "Setter must be a function");
        }

        JSValue prop = args[0];
        PropertyKey key = PropertyKey.fromValue(context, prop);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        PropertyDescriptor desc = new PropertyDescriptor();
        if (getter) {
            desc.setGetter(function);
        } else {
            desc.setSetter(function);
        }
        desc.setEnumerable(true);
        desc.setConfigurable(true);

        if (!obj.defineProperty(key, desc)) {
            if (!context.hasPendingException()) {
                context.throwTypeError("Cannot define property: " + key.toPropertyString());
            }
            return JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
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

        PropertyDescriptor desc = PropertyDescriptor.readDefinition(context, descObj, false);
        if (desc == null) {
            return context.getPendingException();
        }

        if (!obj.defineProperty(key, desc)) {
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            // A blocked array length truncation failed on a delete, not on extensibility. V8 names
            // the element that stopped it; saying "object is not extensible" here reported a reason
            // that was not merely differently worded but untrue — the array is extensible.
            if (obj instanceof JSArray array) {
                JSValue blockedTruncationError = array.throwBlockedLengthTruncation();
                if (blockedTruncationError != null) {
                    return blockedTruncationError;
                }
            }
            return obj.throwDefinePropertyFailure(context, key);
        }
        return obj;
    }

    /**
     * Object.entries(obj)
     */
    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        return enumerableProperties(context, args, Enumeration.ENTRIES);
    }

    private static JSValue enumerableProperties(JSContext context, JSValue[] args, Enumeration kind) {
        JSValue arg = JSValue.argumentOrUndefined(args, 0);

        if (arg instanceof JSNull || arg instanceof JSUndefined) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        if (!(arg instanceof JSObject obj)) {
            return context.createJSArray();
        }

        PropertyKey[] keys = obj.enumerableKeys();
        JSValue[] elements = new JSValue[keys.length];
        for (int i = 0; i < keys.length; i++) {
            PropertyKey key = keys[i];
            switch (kind) {
                case KEYS -> elements[i] = new JSString(key.toPropertyString());
                case VALUES -> elements[i] = obj.get(key);
                case ENTRIES -> {
                    JSArray entry = context.createJSArray(2);
                    entry.set(0, new JSString(key.toPropertyString()));
                    entry.set(1, obj.get(key));
                    elements[i] = entry;
                }
            }
        }
        return context.createJSArray(elements, true);
    }

    /**
     * Object.freeze(obj) ES2020 19.1.2.6 Freezes an object, preventing new properties and making existing properties
     * non-configurable.
     */
    public static JSValue freeze(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = JSValue.argumentOrUndefined(args, 0);

        if (!(arg instanceof JSObject obj)) {
            return arg;
        }

        obj.freeze();
        return arg;
    }

    private static JSValue getValueWithReceiver(JSContext context, JSObject object, PropertyKey key, JSValue receiver) {
        JSObject currentObject = object;
        while (currentObject != null) {
            PropertyDescriptor descriptor = currentObject.getOwnPropertyDescriptor(key);
            if (descriptor != null) {
                if (descriptor.isAccessorDescriptor()) {
                    JSFunction getter = descriptor.getGetter();
                    if (getter == null) {
                        return JSUndefined.INSTANCE;
                    }
                    return getter.call(context, receiver, JSValue.NO_ARGS);
                }
                JSValue value = descriptor.getValue();
                return value != null ? value : JSUndefined.INSTANCE;
            }
            currentObject = currentObject.getPrototype();
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Object.prototype.hasOwnProperty(prop)
     */
    public static JSValue hasOwnProperty(JSContext context, JSValue thisArg, JSValue[] args) {
        return testOwnProperty(context, thisArg, args, false);
    }

    /**
     * Object.prototype.isPrototypeOf(V)
     */
    public static JSValue isPrototypeOf(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue v = JSValue.argumentOrUndefined(args, 0);

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
        return enumerableProperties(context, args, Enumeration.KEYS);
    }

    private static JSValue lookupAccessor(JSContext context, JSValue thisArg, JSValue[] args, boolean getter) {
        String method = getter ? "__lookupGetter__" : "__lookupSetter__";
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError(method + " called on null or undefined");
        }
        if (args.length < 1) {
            return JSUndefined.INSTANCE;
        }
        JSObject obj = ObjectConstructor.objectArgument(context, thisArg);
        if (obj == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue prop = args[0];
        PropertyKey key = PropertyKey.fromValue(context, prop);

        // Walk up the prototype chain per spec B.2.2.4
        JSObject current = obj;
        while (current != null) {
            PropertyDescriptor desc = current.getOwnPropertyDescriptor(key);
            if (desc != null) {
                if (desc.isAccessorDescriptor()) {
                    JSFunction accessor = getter ? desc.getGetter() : desc.getSetter();
                    return accessor != null ? accessor : JSUndefined.INSTANCE;
                }
                return JSUndefined.INSTANCE;
            }
            current = current.getPrototype();
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Object.prototype.propertyIsEnumerable(V)
     */
    public static JSValue propertyIsEnumerable(JSContext context, JSValue thisArg, JSValue[] args) {
        return testOwnProperty(context, thisArg, args, true);
    }

    private static JSValue testOwnProperty(JSContext context, JSValue thisArg, JSValue[] args, boolean enumerableOnly) {
        // ES2024 20.1.3.2:
        // Step 1: Let P be ? ToPropertyKey(V) - BEFORE ToObject
        JSValue prop = JSValue.argumentOrUndefined(args, 0);
        PropertyKey key = PropertyKey.fromValue(context, prop);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Step 2: Let O be ? ToObject(this value)
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSObject obj = ObjectConstructor.objectArgument(context, thisArg);
        if (obj == null) {
            return JSUndefined.INSTANCE;
        }

        PropertyDescriptor descriptor = obj.getOwnPropertyDescriptor(key);
        if (!enumerableOnly && context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSBoolean.valueOf(descriptor != null && (!enumerableOnly || descriptor.isEnumerable()));
    }

    /**
     * Object.prototype.toLocaleString()
     */
    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        // QuickJS semantics: Invoke(this, "toString")
        if (thisArg instanceof JSNull || thisArg instanceof JSUndefined) {
            return context.throwTypeError("Object.prototype.toLocaleString called on null or undefined");
        }
        JSObject obj = JSTypeConversions.toObject(context, thisArg);
        if (obj == null || context.hasPendingException()) {
            return context.getPendingException();
        }
        JSValue toStringMethod;
        if (thisArg.isObject()) {
            toStringMethod = obj.get(PropertyKey.TO_STRING);
        } else {
            toStringMethod = getValueWithReceiver(context, obj, PropertyKey.TO_STRING, thisArg);
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (toStringMethod instanceof JSFunction func) {
            return func.call(context, thisArg, JSValue.NO_ARGS);
        }
        return context.throwTypeError("toString is not a function");
    }

    /**
     * Object.prototype.toString() ES2020 19.1.3.6 Returns a string representing the object with Symbol.toStringTag
     * support.
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES2024 20.1.3.6 Object.prototype.toString()
        // Step 1: If this is undefined, return "[object Undefined]"
        if (thisArg instanceof JSUndefined) {
            return new JSString("[object Undefined]");
        }
        // Step 2: If this is null, return "[object Null]"
        if (thisArg instanceof JSNull) {
            return new JSString("[object Null]");
        }

        // Step 3: Let O be ToObject(this)
        JSObject obj = ObjectConstructor.objectArgument(context, thisArg);
        if (obj == null) {
            return new JSString("[object Object]");
        }

        // Steps 4-14: Determine builtinTag based on internal slots per spec
        String builtinTag;
        // Step 4: Let isArray be ? IsArray(O)
        int isArrayResult = JSTypeChecking.isArray(context, obj);
        if (isArrayResult < 0) {
            return context.getPendingException();
        }
        if (isArrayResult > 0) {
            builtinTag = JSArray.NAME;
        } else if (obj instanceof JSArguments) {
            // Step 6: has [[ParameterMap]]
            builtinTag = "Arguments";
        } else if (JSTypeChecking.isFunction(obj)) {
            // Step 7: is callable
            builtinTag = JSFunction.NAME;
        } else if (obj instanceof JSError) {
            // Step 8: has [[ErrorData]] - always "Error" per spec
            builtinTag = JSError.NAME;
        } else if (obj instanceof JSBooleanObject) {
            // Step 9: has [[BooleanData]]
            builtinTag = JSBooleanObject.NAME;
        } else if (obj instanceof JSNumberObject) {
            // Step 10: has [[NumberData]]
            builtinTag = JSNumberObject.NAME;
        } else if (obj instanceof JSStringObject) {
            // Step 11: has [[StringData]]
            builtinTag = JSStringObject.NAME;
        } else if (obj instanceof JSDate) {
            // Step 12: has [[DateValue]]
            builtinTag = JSDate.NAME;
        } else if (obj instanceof JSRegExp) {
            // Step 13: has [[RegExpMatcher]]
            builtinTag = JSRegExp.NAME;
        } else {
            // Step 14: builtinTag is "Object"
            builtinTag = JSObject.NAME;
        }

        // Step 15: Let tag be ? Get(O, @@toStringTag)
        JSValue tag = obj.get(PropertyKey.SYMBOL_TO_STRING_TAG);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 16: If tag is not a String, set tag to builtinTag
        String tagStr;
        if (tag instanceof JSString jsTagStr) {
            tagStr = jsTagStr.value();
        } else {
            tagStr = builtinTag;
        }

        // Step 17: Return "[object " + tag + "]"
        return new JSString("[object " + tagStr + "]");
    }

    /**
     * Object.prototype.valueOf()
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES2024 20.1.3.7: Return ? ToObject(this value)
        if (thisArg.isNullOrUndefined()) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        if (thisArg instanceof JSObject) {
            return thisArg;
        }
        JSObject result = JSTypeConversions.toObject(context, thisArg);
        if (result == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return result;
    }

    /**
     * Object.values(obj)
     */
    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        return enumerableProperties(context, args, Enumeration.VALUES);
    }

    private enum Enumeration {
        ENTRIES,
        KEYS,
        VALUES
    }
}
