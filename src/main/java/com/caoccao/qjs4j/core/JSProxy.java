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

import com.caoccao.qjs4j.exceptions.JSException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a JavaScript Proxy object.
 * Proxies wrap a target object and intercept operations on it via handler traps.
 * Based on ES2020 Proxy specification (simplified).
 */
public final class JSProxy extends JSObject {
    public static final String NAME = "Proxy";
    private final JSContext context;
    private final JSObject handler;
    private final JSValue target;
    private boolean revoked = false;

    /**
     * Create a new Proxy.
     *
     * @param target  The target object or function to wrap
     * @param handler The handler object with trap functions
     * @param context The execution context
     */
    public JSProxy(JSValue target, JSObject handler, JSContext context) {
        super();
        this.target = target;
        this.handler = handler;
        this.context = context;
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // Proxy requires exactly 2 arguments: target and handler
        if (args.length < 2) {
            return context.throwTypeError("Cannot create proxy with a non-object as target or handler");
        }
        // Target must be an object (since JSFunction extends JSObject, this covers both)
        JSValue target = args[0];
        if (!(target instanceof JSObject)) {
            return context.throwTypeError("Proxy target must be an object");
        }
        if (!(args[1] instanceof JSObject handler)) {
            return context.throwTypeError("Proxy handler must be an object");
        }
        // Create Proxy object
        // Note: We don't set the proxy's internal prototype from the target.
        // The proxy intercepts prototype operations via getPrototypeOf/setPrototypeOf traps.
        return new JSProxy(target, handler, context);
    }

    /**
     * Apply trap for function proxies.
     * ES2020 9.5.13 [[Call]]
     */
    public JSValue apply(JSContext context, JSValue thisArg, JSValue[] args) {
        if (revoked) {
            throw new JSException(this.context.throwTypeError("Cannot perform 'apply' on a proxy that has been revoked"));
        }

        if (!JSTypeChecking.isFunction(target)) {
            throw new JSException(this.context.throwTypeError("not a function"));
        }

        JSFunction trapFunc = getTrapFunction("apply");
        if (trapFunc == null) {
            if (target instanceof JSProxy targetProxy) {
                return targetProxy.apply(context, thisArg, args);
            }
            if (target instanceof JSFunction targetFunc) {
                return targetFunc.call(context, thisArg, args);
            }
            throw new JSException(this.context.throwTypeError("target is not callable"));
        }

        // Create arguments array
        JSArray argArray = createArgumentsArray(args);

        // Call trap: handler.apply(target, thisArg, argumentsList)
        JSValue[] trapArgs = new JSValue[]{target, thisArg, argArray};
        return trapFunc.call(context, handler, trapArgs);
    }

    /**
     * Construct trap for constructor proxies.
     * ES2020 9.5.14 [[Construct]]
     */
    public JSValue construct(JSContext context, JSValue[] args, JSValue newTarget) {
        if (revoked) {
            throw new JSException(this.context.throwTypeError("Cannot perform 'construct' on a proxy that has been revoked"));
        }

        if (!JSTypeChecking.isConstructor(target)) {
            throw new JSException(this.context.throwTypeError("target is not a constructor"));
        }

        JSFunction trapFunc = getTrapFunction("construct");
        if (trapFunc == null) {
            // Forward to target constructor
            if (target instanceof JSProxy targetProxy) {
                return targetProxy.construct(context, args, newTarget);
            }
            if (target instanceof JSFunction targetFunc) {
                JSObject instance = new JSObject();
                this.context.transferPrototype(instance, targetFunc);

                JSValue result = targetFunc.call(context, instance, args);
                if (result instanceof JSObject) {
                    return result;
                }
                return instance;
            }
            throw new JSException(this.context.throwTypeError("target is not a constructor"));
        }

        // Create arguments array
        JSArray argArray = createArgumentsArray(args);

        // Call trap: handler.construct(target, argumentsList, newTarget)
        JSValue[] trapArgs = new JSValue[]{target, argArray, newTarget};
        JSValue result = trapFunc.call(context, handler, trapArgs);

        // Result must be an object
        if (!(result instanceof JSObject)) {
            throw new JSException(this.context.throwTypeError(
                    "construct trap must return an object"));
        }

        return result;
    }

    /**
     * Helper to create an arguments array from JSValue[].
     */
    private JSArray createArgumentsArray(JSValue[] args) {
        JSArray array = context.createJSArray(0, args.length);
        for (int i = 0; i < args.length; i++) {
            array.set(i, args[i]);
        }
        return array;
    }

    /**
     * Override defineProperty to intercept Object.defineProperty().
     * ES2020 9.5.6 [[DefineOwnProperty]]
     */
    @Override
    public void defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'defineProperty' on a proxy that has been revoked"));
        }
        // Since JSFunction extends JSObject, target is always a JSObject
        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("defineProperty");
        if (trap instanceof JSUndefined) {
            targetObj.defineProperty(key, descriptor);
            return;
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwTypeError("defineProperty trap must be a function"));
        }

        // Convert descriptor to object
        JSObject descObj = fromPropertyDescriptor(descriptor);

        // Call trap: handler.defineProperty(target, property, descriptor)
        JSValue[] args = new JSValue[]{
                target,
                toKeyValue(key),
                descObj
        };
        JSValue result = trapFunc.call(context, handler, args);

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (!boolResult) {
            throw new JSException(context.throwTypeError("defineProperty returned false"));
        }

        // Validate invariants
        PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
        boolean targetExtensible = targetObj.isExtensible();

        boolean settingConfigFalse = descriptor.hasConfigurable() && !descriptor.isConfigurable();

        if (targetDesc == null) {
            // Property doesn't exist on target
            if (!targetExtensible || settingConfigFalse) {
                throw new JSException(context.throwTypeError(
                        "'defineProperty' on proxy: trap returned truish for adding property '" +
                                key.toPropertyString() +
                                "'  to the non-extensible proxy target"));
            }
        } else {
            // Property exists on target
            if (!isCompatiblePropertyDescriptor(targetExtensible, descriptor, targetDesc)) {
                throw new JSException(context.throwTypeError(
                        "'defineProperty' on proxy: trap returned truish for adding property '" +
                                key.toPropertyString() +
                                "'  that is incompatible with the existing property in the proxy target"));
            }
            if (targetDesc.isConfigurable() && settingConfigFalse) {
                throw new JSException(context.throwTypeError(
                        "proxy: inconsistent defineProperty"));
            }
        }
    }

    /**
     * Override delete to intercept property deletion.
     */
    @Override
    public boolean delete(PropertyKey key) {
        return delete(key, null);
    }

    @Override
    public boolean delete(PropertyKey key, JSContext ctx) {
        JSContext executionContext = ctx != null ? ctx : context;
        if (revoked) {
            throw new JSException(executionContext.throwTypeError("Cannot perform 'delete' on a proxy that has been revoked"));
        }

        JSObject targetObj = (JSObject) target;
        JSFunction deleteTrapFunc = getTrapFunction("deleteProperty");
        if (deleteTrapFunc != null) {
            JSValue[] args = new JSValue[]{target, toKeyValue(key)};
            JSValue result = deleteTrapFunc.call(executionContext, handler, args);
            boolean success = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;

            if (success) {
                PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
                if (targetDesc != null) {
                    if (!targetDesc.isConfigurable()) {
                        throw new JSException(executionContext.throwTypeError(
                                "'deleteProperty' on proxy: trap returned truish for property '" + key.toPropertyString() + "' which is non-configurable in the proxy target"));
                    }
                    if (!targetObj.isExtensible()) {
                        throw new JSException(executionContext.throwTypeError(
                                "'deleteProperty' on proxy: trap returned truish for property '" +
                                        key.toPropertyString() +
                                        "' but the proxy target is non-extensible"));
                    }
                }
            }

            if (!success && ctx != null && ctx.isStrictMode()) {
                throw new JSException(executionContext.throwTypeError(
                        "'deleteProperty' on proxy: trap returned falsish for property '" + key.toPropertyString() + "'"));
            }
            return success;
        }

        // No trap, forward to target (pass context for strict mode checking)
        return targetObj.delete(key, ctx);
    }

    /**
     * Helper to convert PropertyDescriptor to JSObject.
     */
    private JSObject fromPropertyDescriptor(PropertyDescriptor desc) {
        JSObject obj = new JSObject();

        if (desc.hasValue()) {
            obj.set(PropertyKey.fromString("value"), desc.getValue());
        }

        if (desc.hasWritable()) {
            obj.set(PropertyKey.fromString("writable"),
                    desc.isWritable() ? JSBoolean.TRUE : JSBoolean.FALSE);
        }

        if (desc.hasEnumerable()) {
            obj.set(PropertyKey.fromString("enumerable"),
                    desc.isEnumerable() ? JSBoolean.TRUE : JSBoolean.FALSE);
        }

        if (desc.hasConfigurable()) {
            obj.set(PropertyKey.fromString("configurable"),
                    desc.isConfigurable() ? JSBoolean.TRUE : JSBoolean.FALSE);
        }

        if (desc.hasGetter()) {
            obj.set(PropertyKey.fromString("get"), desc.getGetter());
        }

        if (desc.hasSetter()) {
            obj.set(PropertyKey.fromString("set"), desc.getSetter());
        }

        return obj;
    }

    /**
     * Override get to intercept property access by string.
     */
    @Override
    public JSValue get(String propertyName) {
        return get(PropertyKey.fromString(propertyName));
    }

    /**
     * Override get to intercept property access by index.
     */
    @Override
    public JSValue get(int index) {
        return get(PropertyKey.fromIndex(index));
    }

    /**
     * Override get to intercept property access.
     */
    @Override
    public JSValue get(PropertyKey key) {
        return get(key, context);
    }

    /**
     * Override get with context to intercept property access.
     */
    @Override
    public JSValue get(PropertyKey key, JSContext context) {
        // Use this proxy as the receiver
        return get(key, context, this);
    }

    /**
     * Override get method with receiver tracking for prototype chain support.
     */
    @Override
    protected JSValue get(PropertyKey key, JSContext context, JSObject receiver) {
        return getInternal(key, context, receiver);
    }

    public JSObject getHandler() {
        return handler;
    }

    /**
     * Internal get implementation that accepts a receiver parameter for prototype chain support.
     */
    private JSValue getInternal(PropertyKey key, JSContext context, JSObject receiver) {
        if (revoked) {
            throw new JSException(this.context.throwTypeError("Cannot perform 'get' on a proxy that has been revoked"));
        }

        // Check if handler has 'get' trap
        JSValue getTrap = handler.get(PropertyKey.fromString("get"));
        if (getTrap != null && !(getTrap instanceof JSUndefined) && !(getTrap instanceof JSNull)) {
            if (getTrap instanceof JSFunction getTrapFunc) {
                // Call the trap: handler.get(target, property, receiver)
                // Convert PropertyKey to JSValue for the trap
                JSValue keyValue;
                if (key.isString()) {
                    keyValue = new JSString(key.asString());
                } else if (key.isIndex()) {
                    // Convert index to string as JavaScript does
                    keyValue = new JSString(String.valueOf(key.getValue()));
                } else {
                    // Symbol
                    keyValue = key.asSymbol();
                }

                JSValue[] args = new JSValue[]{
                        target,
                        keyValue,
                        receiver  // Use the receiver parameter instead of 'this'
                };
                JSValue trapResult = getTrapFunc.call(this.context, handler, args);

                // Check invariant: non-configurable accessor without getter must return undefined
                // Since JSFunction extends JSObject, target is always a JSObject
                if (target instanceof JSObject targetObj) {
                    PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
                    if (targetDesc != null && !targetDesc.isConfigurable() &&
                            targetDesc.isAccessorDescriptor() && targetDesc.getGetter() == null) {
                        // Non-configurable accessor without getter
                        if (!(trapResult instanceof JSUndefined)) {
                            String keyStr = key.isString() ? key.asString() : String.valueOf(key.getValue());
                            throw new JSException(this.context.throwTypeError(
                                    "'get' on proxy: property '" +
                                            keyStr +
                                            "' is a non-configurable accessor property on the proxy target and does not have a getter function, but the trap did not return 'undefined' (got '" +
                                            JSTypeConversions.toString(this.context, trapResult).value() +
                                            "')"));
                        }
                    }

                    // Check invariant: non-writable, non-configurable data property must return same value
                    if (targetDesc != null && targetDesc.isDataDescriptor() &&
                            !targetDesc.isConfigurable() && !targetDesc.isWritable()) {
                        // Non-writable, non-configurable data property
                        if (!JSTypeConversions.strictEquals(trapResult, targetDesc.getValue())) {
                            String keyStr = key.isString() ? key.asString() : String.valueOf(key.getValue());
                            throw new JSException(this.context.throwTypeError(
                                    "'get' on proxy: property '" +
                                            keyStr +
                                            "' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value (expected '" +
                                            JSTypeConversions.toString(this.context, targetDesc.getValue()).value() +
                                            "' but got '" +
                                            JSTypeConversions.toString(this.context, trapResult).value() +
                                            "')"));
                        }
                    }
                }
                return trapResult;
            } else {
                throw new JSException(this.context.throwTypeError("'" + JSTypeConversions.toString(context, getTrap) + "' returned for property 'get' of object '#<Object>' is not a function"));
            }
        }
        // No trap, forward to target
        // Following QuickJS js_proxy_get: forward to target
        // Since JSFunction now extends JSObject, all targets are JSObjects
        if (target instanceof JSObject targetObj) {
            return targetObj.get(key, context != null ? context : this.context);
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Override getOwnPropertyDescriptor to intercept Object.getOwnPropertyDescriptor().
     * ES2020 9.5.5 [[GetOwnProperty]]
     */
    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'getOwnPropertyDescriptor' on a proxy that has been revoked"));
        }

        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("getOwnPropertyDescriptor");
        if (trap instanceof JSUndefined) {
            return targetObj.getOwnPropertyDescriptor(key);
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwTypeError(
                    "getOwnPropertyDescriptor trap must be a function"));
        }

        // Call trap: handler.getOwnPropertyDescriptor(target, property)
        JSValue[] args = new JSValue[]{
                target,
                toKeyValue(key)
        };
        JSValue trapResult = trapFunc.call(context, handler, args);

        // Result must be undefined or object
        if (!(trapResult instanceof JSUndefined) && !(trapResult instanceof JSObject)) {
            throw new JSException(context.throwTypeError(
                    "getOwnPropertyDescriptor trap must return an object or undefined"));
        }

        PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
        boolean targetExtensible = targetObj.isExtensible();

        if (trapResult instanceof JSUndefined) {
            // Invariant: cannot return undefined for non-configurable property
            if (targetDesc != null && !targetDesc.isConfigurable()) {
                throw new JSException(context.throwTypeError(
                        "'getOwnPropertyDescriptor' on proxy: trap returned undefined for property '" +
                                key.toPropertyString() +
                                "' which is non-configurable in the proxy target"));
            }
            // Invariant: cannot return undefined if target is non-extensible and has property
            if (!targetExtensible && targetDesc != null) {
                throw new JSException(context.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor"));
            }
            return null;
        }

        // Convert result to PropertyDescriptor
        PropertyDescriptor resultDesc = toPropertyDescriptor((JSObject) trapResult);

        // Validate against target descriptor
        if (targetDesc != null) {
            if (!isCompatiblePropertyDescriptor(targetExtensible, resultDesc, targetDesc)) {
                throw new JSException(context.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor"));
            }
            // Invariant: cannot return configurable for non-configurable property
            if (!targetDesc.isConfigurable() && resultDesc.isConfigurable()) {
                throw new JSException(context.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor"));
            }
        } else {
            // Invariant: cannot return non-configurable if property doesn't exist on non-extensible target
            if (!targetExtensible) {
                throw new JSException(context.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor"));
            }
        }

        return resultDesc;
    }

    /**
     * Override getOwnPropertyKeys to intercept Object.keys(), etc.
     * This follows QuickJS js_proxy_getOwnPropertyNames implementation.
     */
    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'getOwnPropertyKeys' on a proxy that has been revoked"));
        }

        JSFunction ownKeysTrapFunc = getTrapFunction("ownKeys");
        if (ownKeysTrapFunc != null) {
            JSValue result = ownKeysTrapFunc.call(context, handler, new JSValue[]{target});

            // Result must be array-like
            if (!(result instanceof JSObject resultObj)) {
                throw new JSException(context.throwTypeError(
                        "ownKeys trap result must be an object"));
            }

            // Get the length property
            JSValue lengthValue = resultObj.get("length");
            long lengthLong = JSTypeConversions.toLength(context, lengthValue);
            if (context.hasPendingException()) {
                throw new JSException(context.getPendingException());
            }
            if (lengthLong > Integer.MAX_VALUE) {
                throw new JSException(context.throwTypeError("ownKeys trap result is too large"));
            }
            int length = (int) lengthLong;
            List<PropertyKey> keys = new ArrayList<>(length);

            // Convert result to PropertyKey list
            for (int i = 0; i < length; i++) {
                JSValue keyValue = resultObj.get(i);
                if (keyValue instanceof JSString str) {
                    keys.add(PropertyKey.fromString(str.value()));
                } else if (keyValue instanceof JSSymbol sym) {
                    keys.add(PropertyKey.fromSymbol(sym));
                } else {
                    throw new JSException(context.throwTypeError(
                            "ownKeys trap result must contain only strings or symbols"));
                }
            }

            // Check for duplicate properties
            Set<PropertyKey> propertyKeySet = new HashSet<>();
            for (PropertyKey key : keys) {
                if (!propertyKeySet.add(key)) {
                    throw new JSException(context.throwTypeError(
                            "'ownKeys' on proxy: trap returned duplicate entries"));
                }
            }

            // Validate invariants
            JSObject targetObj = (JSObject) target;
            List<PropertyKey> targetKeys = targetObj.getOwnPropertyKeys();
            boolean targetExtensible = targetObj.isExtensible();

            // Check that all non-configurable own properties are included
            for (PropertyKey targetKey : targetKeys) {
                PropertyDescriptor desc = targetObj.getOwnPropertyDescriptor(targetKey);
                if (desc != null && !desc.isConfigurable()) {
                    if (!keys.contains(targetKey)) {
                        throw new JSException(context.throwTypeError(
                                "'ownKeys' on proxy: trap result did not include '" + targetKey.toPropertyString() + "'"));
                    }
                }
            }

            // If target is not extensible, result must not include extra keys
            if (!targetExtensible) {
                for (PropertyKey key : keys) {
                    if (!targetKeys.contains(key)) {
                        throw new JSException(context.throwTypeError(
                                "'ownKeys' on proxy: trap returned extra key '" + key.toPropertyString() + "'"));
                    }
                }
            }

            return keys;
        }

        // No trap, forward to target
        return ((JSObject) target).getOwnPropertyKeys();
    }

    /**
     * Override getPrototype to intercept Object.getPrototypeOf().
     * ES2020 9.5.1 [[GetPrototypeOf]]
     */
    @Override
    public JSObject getPrototype() {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'getPrototype' on a proxy that has been revoked"));
        }

        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("getPrototypeOf");
        if (trap instanceof JSUndefined) {
            return targetObj.getPrototype();
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwTypeError("getPrototypeOf trap must be a function"));
        }

        // Call trap: handler.getPrototypeOf(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = trapFunc.call(context, handler, args);

        // Validate result is null or object
        if (!(result instanceof JSNull) && !(result instanceof JSObject)) {
            throw new JSException(context.throwTypeError(
                    "proxy getPrototypeOf handler must return an object or null"));
        }

        // Check invariants if target is not extensible
        if (!targetObj.isExtensible()) {
            JSObject targetProto = targetObj.getPrototype();
            if (!sameValue(targetProto, result)) {
                throw new JSException(context.throwTypeError(
                        "'getPrototypeOf' on proxy: proxy target is non-extensible but the trap did not return its actual prototype"));
            }
        }

        return (result instanceof JSNull) ? null : (JSObject) result;
    }

    public JSValue getTarget() {
        return target;
    }

    private JSFunction getTrapFunction(String trapName) {
        JSValue trap = getTrapMethod(trapName);
        if (trap instanceof JSUndefined) {
            return null;
        }
        if (trap instanceof JSFunction trapFunc) {
            return trapFunc;
        }
        String trapValue = JSTypeConversions.toString(context, trap).value();
        throw new JSException(context.throwTypeError(
                "'" + trapValue + "' returned for property '" + trapName + "' of object '#<Object>' is not a function"));
    }

    /**
     * Helper to get a trap method from the handler.
     * Returns null if handler is null/undefined.
     * Following QuickJS get_proxy_method().
     */
    private JSValue getTrapMethod(String trapName) {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'getTrapMethod' on a proxy that has been revoked"));
        }

        JSValue method = handler.get(trapName);
        // Treat null as undefined
        if (method instanceof JSNull) {
            return JSUndefined.INSTANCE;
        }
        return method;
    }

    /**
     * Override has to intercept 'in' operator.
     */
    @Override
    public boolean has(PropertyKey key) {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'has' on a proxy that has been revoked"));
        }

        JSObject targetObj = (JSObject) target;
        JSFunction hasTrapFunc = getTrapFunction("has");
        if (hasTrapFunc != null) {
            JSValue[] args = new JSValue[]{target, toKeyValue(key)};
            JSValue result = hasTrapFunc.call(context, handler, args);
            boolean trapResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;

            if (!trapResult) {
                PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
                if (targetDesc != null && (!targetDesc.isConfigurable() || !targetObj.isExtensible())) {
                    if (!targetDesc.isConfigurable()) {
                        throw new JSException(context.throwTypeError(
                                "'has' on proxy: trap returned falsish for property '" +
                                        key.toPropertyString() +
                                        "' which exists in the proxy target as non-configurable"));
                    }
                    throw new JSException(context.throwTypeError(
                            "'has' on proxy: trap returned falsish for property '" +
                                    key.toPropertyString() +
                                    "' but the proxy target is not extensible"));
                }
            }

            return trapResult;
        }

        // No trap, forward to target
        return targetObj.has(key);
    }

    /**
     * Check if two descriptors are compatible.
     * Following ES2020 ValidateAndApplyPropertyDescriptor logic.
     */
    private boolean isCompatiblePropertyDescriptor(boolean extensible,
                                                   PropertyDescriptor desc,
                                                   PropertyDescriptor current) {
        // If current is undefined, check extensibility
        if (current == null) {
            return extensible;
        }

        // If desc is empty, it's compatible
        if (!desc.hasValue() && !desc.hasWritable() &&
                !desc.hasGetter() && !desc.hasSetter() &&
                !desc.hasEnumerable() && !desc.hasConfigurable()) {
            return true;
        }

        // If current is not configurable
        if (!current.isConfigurable()) {
            if (desc.hasConfigurable() && desc.isConfigurable()) {
                return false;
            }
            if (desc.hasEnumerable() && desc.isEnumerable() != current.isEnumerable()) {
                return false;
            }
        }

        // Check if converting between data and accessor
        boolean descIsAccessor = desc.hasGetter() || desc.hasSetter();
        boolean currentIsAccessor = current.hasGetter() || current.hasSetter();

        if (descIsAccessor != currentIsAccessor) {
            return current.isConfigurable();
        }

        // Both are data descriptors
        if (!descIsAccessor) {
            if (!current.isConfigurable() && !current.isWritable()) {
                if (desc.hasWritable() && desc.isWritable()) {
                    return false;
                }
                return !desc.hasValue() || sameValue(desc.getValue(), current.getValue());
            }
        } else {
            // Both are accessor descriptors
            if (!current.isConfigurable()) {
                if (desc.hasGetter() && !sameValue(desc.getGetter(), current.getGetter())) {
                    return false;
                }
                return !desc.hasSetter() || sameValue(desc.getSetter(), current.getSetter());
            }
        }

        return true;
    }

    /**
     * Override isExtensible to intercept Object.isExtensible().
     * ES2020 9.5.3 [[IsExtensible]]
     */
    @Override
    public boolean isExtensible() {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'isExtensible' on a proxy that has been revoked"));
        }

        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("isExtensible");
        if (trap instanceof JSUndefined) {
            return targetObj.isExtensible();
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwTypeError("isExtensible trap must be a function"));
        }

        // Call trap: handler.isExtensible(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = trapFunc.call(context, handler, args);

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        boolean targetExtensible = targetObj.isExtensible();

        // Invariant: result must match target's extensibility
        if (boolResult != targetExtensible) {
            throw new JSException(context.throwTypeError(
                    "'isExtensible' on proxy: trap result does not reflect extensibility of proxy target (which is 'true')"));
        }

        return boolResult;
    }

    /**
     * Check if this proxy has been revoked.
     *
     * @return true if revoked, false otherwise
     */
    public boolean isRevoked() {
        return revoked;
    }

    /**
     * Override ownPropertyKeys to use getOwnPropertyKeys.
     */
    @Override
    public PropertyKey[] ownPropertyKeys() {
        List<PropertyKey> keys = getOwnPropertyKeys();
        return keys.toArray(new PropertyKey[0]);
    }

    /**
     * Override preventExtensions to intercept Object.preventExtensions().
     * ES2020 9.5.4 [[PreventExtensions]]
     */
    @Override
    public void preventExtensions() {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'preventExtensions' on a proxy that has been revoked"));
        }

        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("preventExtensions");
        if (trap instanceof JSUndefined) {
            targetObj.preventExtensions();
            return;
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwTypeError("preventExtensions trap must be a function"));
        }

        // Call trap: handler.preventExtensions(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = trapFunc.call(context, handler, args);

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (boolResult) {
            // Invariant: if trap returns true, target must be non-extensible
            if (targetObj.isExtensible()) {
                throw new JSException(context.throwTypeError(
                        "'preventExtensions' on proxy: trap returned truish but the proxy target is extensible"));
            }
        } else {
            throw new JSException(context.throwTypeError("'preventExtensions' on proxy: trap returned falsish"));
        }
    }

    private boolean proxySetInternal(
            PropertyKey key,
            JSValue value,
            JSContext ctx,
            JSObject receiver,
            boolean throwOnFailure) {
        if (revoked) {
            throw new JSException(this.context.throwTypeError("Cannot perform 'set' on a proxy that has been revoked"));
        }

        JSContext executionContext = ctx != null ? ctx : this.context;
        JSObject targetObj = (JSObject) target;
        JSFunction setTrapFunc = getTrapFunction("set");
        if (setTrapFunc != null) {
            JSValue[] args = new JSValue[]{
                    target,
                    toKeyValue(key),
                    value,
                    receiver
            };
            JSValue result = setTrapFunc.call(executionContext, handler, args);
            boolean trapResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;

            if (!trapResult) {
                if (throwOnFailure && executionContext.isStrictMode()) {
                    throw new JSException(executionContext.throwTypeError(
                            "'set' on proxy: trap returned falsish for property '" + key.toPropertyString() + "'"));
                }
                return false;
            }

            PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
            if (targetDesc != null) {
                if (targetDesc.isDataDescriptor() && !targetDesc.isConfigurable() && !targetDesc.isWritable()) {
                    if (!sameValue(targetDesc.getValue(), value)) {
                        throw new JSException(executionContext.throwTypeError(
                                "'set' on proxy: trap returned truish for property '" +
                                        key.toPropertyString() +
                                        "' which exists in the proxy target as a non-configurable and non-writable data property with a different value"));
                    }
                } else if (targetDesc.isAccessorDescriptor() && !targetDesc.isConfigurable() && targetDesc.getSetter() == null) {
                    throw new JSException(executionContext.throwTypeError(
                            "'set' on proxy: trap returned truish for property '" +
                                    key.toPropertyString() +
                                    "' which exists in the proxy target as a non-configurable and non-writable accessor property without a setter"));
                }
            }
            return true;
        }

        boolean success = targetObj.setWithResult(key, value, executionContext, receiver);
        if (!success && throwOnFailure && executionContext.isStrictMode()) {
            throw new JSException(executionContext.throwTypeError(
                    "'set' on proxy: trap returned falsish for property '" + key.toPropertyString() + "'"));
        }
        return success;
    }

    /**
     * Revoke this proxy.
     * After revocation, all proxy operations will throw TypeError.
     * ES2020 26.2.2.1.1
     */
    public void revoke() {
        this.revoked = true;
    }

    /**
     * SameValue comparison (ES2020 7.2.10).
     */
    private boolean sameValue(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof JSValue valA && b instanceof JSValue valB) {
            // Simplified comparison - should use full SameValue algorithm
            if (valA instanceof JSNumber numA && valB instanceof JSNumber numB) {
                double da = numA.value();
                double db = numB.value();
                // Handle NaN case
                if (Double.isNaN(da) && Double.isNaN(db)) {
                    return true;
                }
                // Handle -0 vs +0
                if (da == 0 && db == 0) {
                    return Double.doubleToRawLongBits(da) == Double.doubleToRawLongBits(db);
                }
                return da == db;
            }
            if (valA instanceof JSString strA && valB instanceof JSString strB) {
                return strA.value().equals(strB.value());
            }
            if (valA instanceof JSBoolean boolA && valB instanceof JSBoolean boolB) {
                return boolA == boolB;
            }
            return valA == valB;
        }
        return a.equals(b);
    }

    /**
     * Override set to intercept property assignment by string.
     */
    @Override
    public void set(String propertyName, JSValue value) {
        set(PropertyKey.fromString(propertyName), value);
    }

    /**
     * Override set to intercept property assignment by index.
     */
    @Override
    public void set(int index, JSValue value) {
        set(PropertyKey.fromIndex(index), value);
    }

    /**
     * Override set to intercept property assignment.
     */
    @Override
    public void set(PropertyKey key, JSValue value) {
        set(key, value, context);
    }

    /**
     * Override set with context to intercept property assignment.
     */
    @Override
    public void set(PropertyKey key, JSValue value, JSContext context) {
        proxySetInternal(key, value, context, this, true);
    }

    /**
     * Override setPrototype to intercept Object.setPrototypeOf().
     * ES2020 9.5.2 [[SetPrototypeOf]]
     */
    @Override
    public void setPrototype(JSObject proto) {
        if (revoked) {
            throw new JSException(context.throwTypeError("Cannot perform 'setPrototype' on a proxy that has been revoked"));
        }

        JSObject targetObj = (JSObject) target;

        JSFunction trapFunc = getTrapFunction("setPrototypeOf");
        if (trapFunc == null) {
            // No trap - forward to target per ES2020 9.5.2 step 5
            targetObj.setPrototype(proto);
            return;
        }

        // Call trap: handler.setPrototypeOf(target, proto)
        JSValue[] args = new JSValue[]{target, proto == null ? JSNull.INSTANCE : proto};
        JSValue result = trapFunc.call(context, handler, args);

        // Convert to boolean
        boolean boolResult = JSTypeConversions.toBoolean(result).isBooleanTrue();
        if (boolResult) {
            // Check invariants if target is not extensible
            if (!targetObj.isExtensible()) {
                JSObject targetProto = targetObj.getPrototype();
                if (!sameValue(targetProto, proto)) {
                    throw new JSException(context.throwTypeError(
                            "'setPrototypeOf' on proxy: trap returned truish for setting a new prototype on the non-extensible proxy target"));
                }
            }
        } else {
            throw new JSException(context.throwTypeError("'setPrototypeOf' on proxy: trap returned falsish for property 'undefined'"));
        }
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value, JSContext context, JSObject receiver) {
        return proxySetInternal(key, value, context, receiver, false);
    }

    private JSValue toKeyValue(PropertyKey key) {
        if (key.isSymbol()) {
            return key.asSymbol();
        }
        return new JSString(key.toPropertyString());
    }

    /**
     * Helper to convert JSObject to PropertyDescriptor.
     */
    private PropertyDescriptor toPropertyDescriptor(JSObject obj) {
        PropertyDescriptor desc = new PropertyDescriptor();

        JSValue value = obj.get("value");
        if (!(value instanceof JSUndefined)) {
            desc.setValue(value);
        }

        JSValue writable = obj.get("writable");
        if (!(writable instanceof JSUndefined)) {
            desc.setWritable(JSTypeConversions.toBoolean(writable) == JSBoolean.TRUE);
        }

        JSValue enumerable = obj.get("enumerable");
        if (!(enumerable instanceof JSUndefined)) {
            desc.setEnumerable(JSTypeConversions.toBoolean(enumerable) == JSBoolean.TRUE);
        }

        JSValue configurable = obj.get("configurable");
        if (!(configurable instanceof JSUndefined)) {
            desc.setConfigurable(JSTypeConversions.toBoolean(configurable) == JSBoolean.TRUE);
        }

        JSValue get = obj.get("get");
        if (!(get instanceof JSUndefined)) {
            if (get instanceof JSFunction getFunc) {
                desc.setGetter(getFunc);
            }
        }

        JSValue set = obj.get("set");
        if (!(set instanceof JSUndefined)) {
            if (set instanceof JSFunction setFunc) {
                desc.setSetter(setFunc);
            }
        }

        return desc;
    }

    @Override
    public String toString() {
        return "[object Proxy]";
    }
}
