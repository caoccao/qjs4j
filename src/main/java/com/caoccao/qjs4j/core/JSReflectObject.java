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
import com.caoccao.qjs4j.exceptions.JSException;

/**
 * Implementation of Reflect object static methods. Based on ES2020 Reflect specification. The Reflect object provides
 * methods for interceptable JavaScript operations.
 */
public final class JSReflectObject {

    /**
     * Reflect.apply(target, thisArg, argumentsList) ES2020 26.1.1 Calls a target function with specified this value and
     * arguments.
     */
    public static JSValue apply(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !JSTypeChecking.isFunction(args[0])) {
            return context.throwTypeError("Reflect.apply called on non-function");
        }

        JSValue funcThisArg = JSValue.argumentOrUndefined(args, 1);
        JSValue arrayArg = JSValue.argumentOrUndefined(args, 2);
        JSValue[] funcArgs = JSCall.argumentsFromArrayLike(context, arrayArg);
        if (funcArgs == null) {
            return context.getPendingException();
        }

        if (args[0] instanceof JSProxy callableProxy) {
            return callableProxy.apply(context, funcThisArg, funcArgs);
        }
        return ((JSFunction) args[0]).call(context, funcThisArg, funcArgs);
    }

    private static void applyConstructorPrototype(JSContext context, JSObject object, JSFunction constructor,
            JSObject prototype) {
        if (prototype != null) {
            object.setPrototype(prototype);
        } else {
            context.transferPrototype(object, constructor);
        }
    }

    /**
     * Reflect.construct(target, argumentsList, newTarget) ES2020 26.1.2 Acts like the 'new' operator, but as a
     * function.
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !JSTypeChecking.isConstructor(args[0])) {
            return context.throwTypeError("Reflect.construct called on non-constructor");
        }

        JSValue arrayArg = JSValue.argumentOrUndefined(args, 1);
        JSValue[] constructorArgs = JSCall.argumentsFromArrayLike(context, arrayArg);
        if (constructorArgs == null) {
            return context.getPendingException();
        }

        JSValue newTarget = args.length > 2 ? args[2] : args[0];
        return constructWithNewTarget(context, args[0], constructorArgs, newTarget);
    }

    private static JSValue constructDynamicFunction(JSContext context, JSNativeFunction nativeFunction, JSValue[] args,
            JSValue newTarget) {
        JSContext realm = nativeFunction.getRealmContext() != null ? nativeFunction.getRealmContext() : context;
        JSValue result = JSCall.callConstructor(context, realm, nativeFunction, JSUndefined.INSTANCE, args, newTarget);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (result instanceof JSObject) {
            return result;
        }
        return context.throwTypeError(nativeFunction.getName() + " constructor must return an object");
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
            if (function instanceof JSNativeFunction nativeFunction && isDynamicFunctionConstructor(nativeFunction)) {
                return constructDynamicFunction(context, nativeFunction, args, newTarget);
            }

            return JSCall.constructOrdinary(context, function, args, newTarget);
        }

        // RegExp: ES2024 22.2.3.1 ordering:
        // Step 4-5: Extract raw pattern/flags (reads internal slots)
        // Step 6: RegExpAlloc(newTarget) → prototype lookup
        // Step 7-9: ToString(flags) → compile
        if (constructorType == JSConstructorType.REGEXP) {
            // Step 4-5: Extract raw args BEFORE prototype lookup
            JSValue[] rawArgs = JSRegExp.extractRawArgs(context, args);
            if (rawArgs == null || context.hasPendingException()) {
                return context.getPendingException();
            }

            // Step 6: Resolve prototype from newTarget
            JSObject resolvedPrototype = newTargetPrototype(context, function, newTarget);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            // Step 7-9: ToString and compile
            JSValue result = JSRegExp.createFromRawArgs(context, rawArgs[0], rawArgs[1]);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (result instanceof JSObject jsObject) {
                applyConstructorPrototype(context, jsObject, function, resolvedPrototype);
            }
            return result;
        }

        // ArrayBuffer/SharedArrayBuffer: ES spec requires OrdinaryCreateFromConstructor
        // (prototype access) AFTER argument validation but BEFORE CreateByteDataBlock
        // (allocation). Use createForConstruct which handles this ordering.
        if (constructorType == JSConstructorType.ARRAY_BUFFER) {
            try {
                return JSArrayBuffer.createForConstruct(context, function, newTarget, args);
            } catch (JSErrorException e) {
                return context.throwError(e);
            }
        }
        if (constructorType == JSConstructorType.SHARED_ARRAY_BUFFER) {
            try {
                return JSSharedArrayBuffer.createForConstruct(context, function, newTarget, args);
            } catch (JSErrorException e) {
                return context.throwError(e);
            } catch (JSException e) {
                if (e.getErrorValue() instanceof JSObject errorObject) {
                    return errorObject;
                }
                return context.throwRangeError("Invalid array buffer length");
            }
        }

        // For TypedArray constructors with buffer argument, ES2024 requires
        // AllocateTypedArray (prototype from newTarget) BEFORE argument processing.
        boolean isTypedArrayConstructor = constructorType.name().startsWith("TYPED_ARRAY_");
        JSObject resolvedPrototype = null;
        if (isTypedArrayConstructor && args.length > 0 && args[0] instanceof IJSArrayBuffer) {
            resolvedPrototype = newTargetPrototype(context, function, newTarget);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }

        // An error constructor reached through super() or Reflect.construct needs new.target: the stack it captures
        // leaves out the frames of the subclass constructors above it, as V8's does.
        boolean isErrorConstructor = constructorType.name().endsWith("ERROR");
        JSValue savedNativeConstructorNewTarget = context.getNativeConstructorNewTarget();
        if (isErrorConstructor) {
            context.setNativeConstructorNewTarget(newTarget);
        }
        JSValue result;
        try {
            result = constructorType.create(context, args);
        } catch (JSErrorException e) {
            return context.throwError(e);
        } finally {
            context.setNativeConstructorNewTarget(savedNativeConstructorNewTarget);
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        if (result instanceof JSObject jsObject && !jsObject.isProxy()) {
            if (resolvedPrototype == null) {
                resolvedPrototype = newTargetPrototype(context, function, newTarget);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
            applyConstructorPrototype(context, jsObject, function, resolvedPrototype);
            if (jsObject instanceof JSDataView dataView && !dataView.validateConstructorState(context)) {
                return context.getPendingException();
            }
        }
        return result;
    }

    /**
     * Construct a value using the given target as both constructor and newTarget. Equivalent to `new target(...args)`.
     */
    public static JSValue constructSimple(JSContext context, JSValue target, JSValue[] args) {
        return constructFunction(context, target, args, target);
    }

    static JSValue constructWithNewTarget(JSContext context, JSValue target, JSValue[] args, JSValue newTarget) {
        if (!JSTypeChecking.isConstructor(newTarget)) {
            return context.throwTypeError("Reflect.construct newTarget is not a constructor");
        }
        return constructFunction(context, target, args, newTarget);
    }

    /**
     * Reflect.defineProperty(target, propertyKey, attributes) ES2020 26.1.3
     */
    public static JSValue defineProperty(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.defineProperty called on non-object");
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        if (args.length < 3 || !(args[2] instanceof JSObject attributes)) {
            return context.throwTypeError("Property description must be an object");
        }

        PropertyDescriptor descriptor = toPropertyDescriptor(context, attributes);
        if (descriptor == null) {
            return context.getPendingException();
        }

        if (target instanceof JSProxy proxy) {
            return JSBoolean.valueOf(proxy.definePropertyWithResult(context, key, descriptor));
        }
        return JSBoolean.valueOf(target.defineProperty(key, descriptor));
    }

    /**
     * Reflect.deleteProperty(target, propertyKey) ES2020 26.1.4 Deletes a property from an object (like the 'delete'
     * operator).
     */
    public static JSValue deleteProperty(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.deleteProperty called on non-object");
        }

        if (args.length < 2) {
            return JSBoolean.TRUE;
        }

        PropertyKey key = PropertyKey.fromValue(context, args[1]);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        boolean wasStrictMode = context.isStrictMode();
        try {
            if (wasStrictMode) {
                context.exitStrictMode();
            }
            return JSBoolean.valueOf(target.delete(key));
        } finally {
            if (wasStrictMode) {
                context.enterStrictMode();
            }
        }
    }

    /**
     * Reflect.get(target, propertyKey, receiver) ES2020 26.1.6 Gets the value of a property on an object.
     */
    public static JSValue get(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.get called on non-object");
        }

        JSValue keyArg = JSValue.argumentOrUndefined(args, 1);
        PropertyKey key = PropertyKey.fromValue(context, keyArg);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSValue receiver = args.length > 2 ? args[2] : target;
        JSValue result = target.get(key, receiver);
        // Cross-realm: if the getter moved the exception to the receiver's context,
        // transfer it back to the caller's context so it can be properly thrown.
        if (!context.hasPendingException() && receiver instanceof JSObject receiverObj
                && receiverObj.getContext() != null && receiverObj.getContext() != context
                && receiverObj.getContext().hasPendingException()) {
            context.setPendingException(receiverObj.getContext().getPendingException());
            receiverObj.getContext().clearPendingException();
        }
        return result;
    }

    /**
     * Reflect.getOwnPropertyDescriptor(target, propertyKey) ES2020 26.1.7
     */
    public static JSValue getOwnPropertyDescriptor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.getOwnPropertyDescriptor called on non-object");
        }

        JSValue keyValue = JSValue.argumentOrUndefined(args, 1);
        PropertyKey key = PropertyKey.fromValue(context, keyValue);
        PropertyDescriptor descriptor = target.getOwnPropertyDescriptor(key);
        if (descriptor == null) {
            return JSUndefined.INSTANCE;
        }
        return descriptor.toDescriptorObject(context);
    }

    /**
     * Reflect.getPrototypeOf(target) ES2020 26.1.8 Gets the prototype of an object.
     */
    public static JSValue getPrototypeOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.getPrototypeOf called on non-object");
        }

        JSObject prototype = target.getPrototype();
        return prototype != null ? prototype : JSNull.INSTANCE;
    }

    /**
     * Reflect.has(target, propertyKey) ES2020 26.1.9 Checks if an object has a property (like the 'in' operator).
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.has called on non-object");
        }

        JSValue keyArg = JSValue.argumentOrUndefined(args, 1);
        PropertyKey key = PropertyKey.fromValue(context, keyArg);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        return JSBoolean.valueOf(target.has(key));
    }

    private static boolean isDynamicFunctionConstructor(JSNativeFunction function) {
        String functionName = function.getName();
        return JSFunction.NAME.equals(functionName) || "GeneratorFunction".equals(functionName)
                || "AsyncFunction".equals(functionName) || "AsyncGeneratorFunction".equals(functionName);
    }

    /**
     * Reflect.isExtensible(target) ES2020 26.1.10 Checks if an object is extensible.
     */
    public static JSValue isExtensible(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.isExtensible called on non-object");
        }

        return JSBoolean.valueOf(target.isExtensible());
    }

    private static JSObject newTargetPrototype(JSContext context, JSFunction function, JSValue newTarget) {
        return newTarget instanceof JSObject object
                ? context.getPrototypeFromConstructor(object, context.getIntrinsicDefaultPrototypeName(function))
                : null;
    }

    /**
     * Reflect.ownKeys(target) ES2020 26.1.11 Returns an array of the target object's own property keys.
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
     * Reflect.preventExtensions(target) ES2020 26.1.12 Prevents new properties from being added to an object.
     */
    public static JSValue preventExtensions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.preventExtensions called on non-object");
        }
        return JSBoolean.valueOf(target.preventExtensionsWithResult());
    }

    /**
     * Reflect.set(target, propertyKey, value, receiver) ES2020 26.1.13 Sets the value of a property on an object.
     */
    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.set called on non-object");
        }

        JSValue keyArg = JSValue.argumentOrUndefined(args, 1);
        PropertyKey key = PropertyKey.fromValue(context, keyArg);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSValue value = JSValue.argumentOrUndefined(args, 2);
        JSValue receiver = args.length > 3 ? args[3] : target;

        boolean success = target instanceof JSProxy proxy
                ? proxy.proxySetWithReceiver(key, value, receiver)
                : target.setWithResult(key, value, receiver);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        return JSBoolean.valueOf(success);
    }

    /**
     * Reflect.setPrototypeOf(target, prototype) ES2020 26.1.14 Sets the prototype of an object.
     */
    public static JSValue setPrototypeOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect.setPrototypeOf called on non-object");
        }

        JSValue prototypeArg = JSValue.argumentOrUndefined(args, 1);
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

        // Following QuickJS js_obj_to_desc: use HasProperty to check field existence.
        // {value: undefined} is a valid descriptor with an explicit value field.

        if (descriptorObject.has(PropertyKey.ENUMERABLE)) {
            JSValue enumerable = descriptorObject.get(PropertyKey.ENUMERABLE);
            descriptor.setEnumerable(JSTypeConversions.toBoolean(enumerable) == JSBoolean.TRUE);
        }

        if (descriptorObject.has(PropertyKey.CONFIGURABLE)) {
            JSValue configurable = descriptorObject.get(PropertyKey.CONFIGURABLE);
            descriptor.setConfigurable(JSTypeConversions.toBoolean(configurable) == JSBoolean.TRUE);
        }

        if (descriptorObject.has(PropertyKey.VALUE)) {
            JSValue value = descriptorObject.get(PropertyKey.VALUE);
            descriptor.setValue(value);
        }

        if (descriptorObject.has(PropertyKey.WRITABLE)) {
            JSValue writable = descriptorObject.get(PropertyKey.WRITABLE);
            descriptor.setWritable(JSTypeConversions.toBoolean(writable) == JSBoolean.TRUE);
        }

        if (descriptorObject.has(PropertyKey.GET)) {
            JSValue get = descriptorObject.get(PropertyKey.GET);
            if (!(get instanceof JSUndefined) && !(get instanceof JSFunction)) {
                context.throwTypeError("invalid getter");
                return null;
            }
            descriptor.setGetter(get instanceof JSFunction getter ? getter : null);
        }

        if (descriptorObject.has(PropertyKey.SET)) {
            JSValue set = descriptorObject.get(PropertyKey.SET);
            if (!(set instanceof JSUndefined) && !(set instanceof JSFunction)) {
                context.throwTypeError("invalid setter");
                return null;
            }
            descriptor.setSetter(set instanceof JSFunction setter ? setter : null);
        }

        if (descriptor.isAccessorDescriptor() && descriptor.isDataDescriptor()) {
            context.throwTypeError(
                    "Invalid property descriptor. Cannot both specify accessors and a value or writable attribute");
            return null;
        }

        return descriptor;
    }
}
