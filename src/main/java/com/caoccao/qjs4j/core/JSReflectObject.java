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

import com.caoccao.qjs4j.exceptions.JSErrorException;

/**
 * Implementation of Reflect object static methods.
 * Based on ES2020 Reflect specification.
 * The Reflect object provides methods for interceptable JavaScript operations.
 */
public final class JSReflectObject {

    /**
     * Reflect.apply(target, thisArg, argumentsList)
     * ES2020 26.1.1
     * Calls a target function with specified this value and arguments.
     */
    public static JSValue apply(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !JSTypeChecking.isFunction(args[0])) {
            return context.throwTypeError("Reflect.apply called on non-function");
        }

        JSValue funcThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue arrayArg = args.length > 2 ? args[2] : JSUndefined.INSTANCE;
        JSValue[] funcArgs = buildArgumentList(context, arrayArg);
        if (funcArgs == null) {
            return context.getPendingException();
        }

        if (args[0] instanceof JSProxy callableProxy) {
            return callableProxy.apply(context, funcThisArg, funcArgs);
        }
        return ((JSFunction) args[0]).call(context, funcThisArg, funcArgs);
    }

    private static JSValue[] buildArgumentList(JSContext context, JSValue arrayArg) {
        if (!(arrayArg instanceof JSObject arrayLike)) {
            context.throwTypeError("CreateListFromArrayLike called on non-object");
            return null;
        }

        JSValue lengthValue = arrayLike.get(PropertyKey.LENGTH, context);
        if (context.hasPendingException()) {
            return null;
        }

        long length = JSTypeConversions.toLength(context, lengthValue);
        if (context.hasPendingException()) {
            return null;
        }
        if (length > Integer.MAX_VALUE) {
            context.throwRangeError("too many arguments in function call");
            return null;
        }

        JSValue[] callArgs = new JSValue[(int) length];
        for (int i = 0; i < callArgs.length; i++) {
            JSValue argumentValue = arrayLike.get(PropertyKey.fromString(String.valueOf(i)), context);
            if (context.hasPendingException()) {
                return null;
            }
            if (argumentValue instanceof JSUndefined) {
                argumentValue = arrayLike.get(PropertyKey.fromIndex(i), context);
                if (context.hasPendingException()) {
                    return null;
                }
            }
            callArgs[i] = argumentValue;
        }
        return callArgs;
    }

    /**
     * Reflect.construct(target, argumentsList, newTarget)
     * ES2020 26.1.2
     * Acts like the 'new' operator, but as a function.
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !JSTypeChecking.isConstructor(args[0])) {
            return context.throwTypeError("Reflect.construct called on non-constructor");
        }

        JSValue arrayArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue[] constructorArgs = buildArgumentList(context, arrayArg);
        if (constructorArgs == null) {
            return context.getPendingException();
        }

        JSValue newTarget = args.length > 2 ? args[2] : args[0];
        if (!JSTypeChecking.isConstructor(newTarget)) {
            return context.throwTypeError("Reflect.construct newTarget is not a constructor");
        }

        return constructFunction(context, args[0], constructorArgs, newTarget);
    }

    private static JSValue constructFunction(JSContext context, JSValue target, JSValue[] args, JSValue newTarget) {
        if (target instanceof JSProxy proxy) {
            return proxy.construct(context, args, newTarget);
        }
        if (!(target instanceof JSFunction function)) {
            return context.throwTypeError("Reflect.construct called on non-constructor");
        }

        if (function instanceof JSBoundFunction boundFunction) {
            JSFunction targetFunction = boundFunction.getTarget();
            if (!JSTypeChecking.isConstructor(targetFunction)) {
                return context.throwTypeError(boundFunction.getName() + " is not a constructor");
            }
            JSValue adjustedNewTarget = newTarget == target ? targetFunction : newTarget;
            return constructFunction(context, targetFunction, boundFunction.prependBoundArgs(args), adjustedNewTarget);
        }

        if (function instanceof JSClass jsClass) {
            return jsClass.construct(context, args);
        }

        JSConstructorType constructorType = function.getConstructorType();
        if (constructorType == null) {
            JSObject thisObject = new JSObject();
            if (newTarget instanceof JSObject newTargetObject) {
                if (!context.transferPrototype(thisObject, newTargetObject)) {
                    // GetPrototypeFromConstructor fallback: use target function's prototype
                    context.transferPrototype(thisObject, function);
                }
            } else {
                context.transferPrototype(thisObject, function);
            }

            JSValue result;
            if (function instanceof JSNativeFunction nativeFunc) {
                result = nativeFunc.call(context, thisObject, args);
            } else if (function instanceof JSBytecodeFunction bytecodeFunction) {
                result = context.getVirtualMachine().execute(bytecodeFunction, thisObject, args, newTarget);
            } else {
                result = function.call(context, thisObject, args);
            }

            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            return result instanceof JSObject ? result : thisObject;
        }

        // Per ES spec, GetPrototypeFromConstructor(newTarget) must happen BEFORE
        // creating the object. This ensures prototype getter side-effects (including
        // throws) occur at the right time. (QuickJS: js_create_from_ctor)
        JSObject resolvedPrototype = null;
        if (newTarget instanceof JSObject newTargetObject) {
            JSValue proto = newTargetObject.get(PropertyKey.PROTOTYPE, context);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (proto instanceof JSObject protoObj) {
                resolvedPrototype = protoObj;
            }
        }

        JSObject result;
        try {
            result = constructorType.create(context, args);
        } catch (JSErrorException e) {
            return context.throwError(e.getErrorType().name(), e.getMessage());
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (result != null && !result.isError() && !result.isProxy()) {
            if (resolvedPrototype != null) {
                result.setPrototype(resolvedPrototype);
            } else {
                context.transferPrototype(result, function);
            }
        }
        return result != null ? result : JSUndefined.INSTANCE;
    }

    /**
     * Construct a value using the given target as both constructor and newTarget.
     * Equivalent to `new target(...args)`.
     */
    public static JSValue constructSimple(JSContext context, JSValue target, JSValue[] args) {
        return constructFunction(context, target, args, target);
    }

    /**
     * Reflect.defineProperty(target, propertyKey, attributes)
     * ES2020 26.1.3
     */
    public static JSValue defineProperty(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.defineProperty called on non-object");
        }
        if (args.length < 3 || !(args[2] instanceof JSObject attributes)) {
            return context.throwTypeError("Property description must be an object");
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        PropertyDescriptor descriptor = toPropertyDescriptor(context, attributes);
        if (descriptor == null) {
            return context.getPendingException();
        }

        if (target instanceof JSProxy proxy) {
            return JSBoolean.valueOf(proxy.definePropertyWithResult(key, descriptor));
        }
        target.defineProperty(key, descriptor);
        return JSBoolean.TRUE;
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

    private static JSObject fromPropertyDescriptor(JSContext context, PropertyDescriptor descriptor) {
        JSObject descriptorObject = context.createJSObject();

        if (descriptor.isDataDescriptor()) {
            descriptorObject.set("value", descriptor.getValue() != null ? descriptor.getValue() : JSUndefined.INSTANCE);
            descriptorObject.set("writable", JSBoolean.valueOf(descriptor.isWritable()));
        } else if (descriptor.isAccessorDescriptor()) {
            descriptorObject.set("get", descriptor.getGetter() != null ? descriptor.getGetter() : JSUndefined.INSTANCE);
            descriptorObject.set("set", descriptor.getSetter() != null ? descriptor.getSetter() : JSUndefined.INSTANCE);
        }

        descriptorObject.set("enumerable", JSBoolean.valueOf(descriptor.isEnumerable()));
        descriptorObject.set("configurable", JSBoolean.valueOf(descriptor.isConfigurable()));
        return descriptorObject;
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
        JSObject receiver = args.length > 2 && args[2] instanceof JSObject r ? r : target;
        return target.getWithReceiver(key, context, receiver);
    }

    /**
     * Reflect.getOwnPropertyDescriptor(target, propertyKey)
     * ES2020 26.1.7
     */
    public static JSValue getOwnPropertyDescriptor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.getOwnPropertyDescriptor called on non-object");
        }

        JSValue keyValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        PropertyKey key = PropertyKey.fromValue(context, keyValue);
        PropertyDescriptor descriptor = target.getOwnPropertyDescriptor(key);
        if (descriptor == null) {
            return JSUndefined.INSTANCE;
        }
        return fromPropertyDescriptor(context, descriptor);
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
            if (key.isSymbol()) {
                result.push(key.asSymbol());
            } else {
                result.push(new JSString(key.toPropertyString()));
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

        if (target instanceof JSProxy proxy) {
            return JSBoolean.valueOf(proxy.preventExtensionsWithResult());
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
        JSObject receiver = target;
        if (args.length > 3) {
            if (args[3] instanceof JSObject r) {
                receiver = r;
            } else {
                return JSBoolean.FALSE;
            }
        }

        boolean success = target.setWithResult(key, value, context, receiver);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        return JSBoolean.valueOf(success);
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

        JSValue prototypeArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSObject proto;
        if (prototypeArg instanceof JSNull) {
            proto = null;
        } else if (prototypeArg instanceof JSObject p) {
            proto = p;
        } else {
            return context.throwTypeError("Object prototype may only be an Object or null");
        }

        if (target instanceof JSProxy proxy) {
            return JSBoolean.valueOf(proxy.setPrototypeWithResult(proto));
        }

        // Following QuickJS: Reflect.setPrototypeOf uses throw_flag=FALSE (returns boolean)
        JSObject.SetPrototypeResult result = target.setPrototypeChecked(proto);
        return JSBoolean.valueOf(result == JSObject.SetPrototypeResult.SUCCESS);
    }

    private static PropertyDescriptor toPropertyDescriptor(JSContext context, JSObject descriptorObject) {
        PropertyDescriptor descriptor = new PropertyDescriptor();

        JSValue value = descriptorObject.get("value");
        if (!(value instanceof JSUndefined)) {
            descriptor.setValue(value);
        }

        JSValue writable = descriptorObject.get("writable");
        if (!(writable instanceof JSUndefined)) {
            descriptor.setWritable(JSTypeConversions.toBoolean(writable) == JSBoolean.TRUE);
        }

        JSValue enumerable = descriptorObject.get("enumerable");
        if (!(enumerable instanceof JSUndefined)) {
            descriptor.setEnumerable(JSTypeConversions.toBoolean(enumerable) == JSBoolean.TRUE);
        }

        JSValue configurable = descriptorObject.get("configurable");
        if (!(configurable instanceof JSUndefined)) {
            descriptor.setConfigurable(JSTypeConversions.toBoolean(configurable) == JSBoolean.TRUE);
        }

        JSValue get = descriptorObject.get("get");
        if (!(get instanceof JSUndefined)) {
            if (!(get instanceof JSFunction) && !(get instanceof JSNull)) {
                context.throwTypeError("Getter must be a function");
                return null;
            }
            descriptor.setGetter(get instanceof JSFunction getter ? getter : null);
        }

        JSValue set = descriptorObject.get("set");
        if (!(set instanceof JSUndefined)) {
            if (!(set instanceof JSFunction) && !(set instanceof JSNull)) {
                context.throwTypeError("Setter must be a function");
                return null;
            }
            descriptor.setSetter(set instanceof JSFunction setter ? setter : null);
        }

        if (descriptor.isAccessorDescriptor() && descriptor.isDataDescriptor()) {
            context.throwTypeError("Invalid property descriptor. Cannot both specify accessors and a value or writable attribute");
            return null;
        }

        return descriptor;
    }
}
