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
    private final JSObject handler;
    private final JSValue target;
    private boolean revoked = false;

    /**
     * Create a new Proxy.
     *
     * @param context The execution context
     * @param target  The target object or function to wrap
     * @param handler The handler object with trap functions
     */
    public JSProxy(JSContext context, JSValue target, JSObject handler) {
        super(context);
        this.target = target;
        this.handler = handler;
    }

    /**
     * Call a trap value that may be a JSFunction or a callable JSProxy.
     */
    private static JSValue callTrapValue(JSContext context, JSValue trap, JSValue thisArg, JSValue[] args) {
        if (trap instanceof JSFunction trapFunc) {
            return trapFunc.call(context, thisArg, args);
        }
        if (trap instanceof JSProxy trapProxy) {
            return trapProxy.apply(context, thisArg, args);
        }
        context.throwTypeError("trap is not a function");
        return null;
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
        return new JSProxy(context, target, handler);
    }

    /**
     * Apply trap for function proxies.
     * ES2020 9.5.13 [[Call]]
     */
    public JSValue apply(JSContext context, JSValue thisArg, JSValue[] args) {
        JSContext executionContext = resolveExecutionContext(context);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'apply' on a proxy that has been revoked");
            return null;
        }

        if (!JSTypeChecking.isFunction(target)) {
            executionContext.throwTypeError("not a function");
            return null;
        }

        JSFunction trapFunc = getTrapFunction("apply", executionContext);
        if (executionContext.hasPendingException()) {
            return null;
        }
        if (trapFunc == null) {
            if (target instanceof JSProxy targetProxy) {
                return targetProxy.apply(executionContext, thisArg, args);
            }
            if (target instanceof JSFunction targetFunc) {
                return targetFunc.call(executionContext, thisArg, args);
            }
            executionContext.throwTypeError("target is not callable");
            return null;
        }

        // Create arguments array
        JSArray argArray = createArgumentsArray(executionContext, args);

        // Call trap: handler.apply(target, thisArg, argumentsList)
        JSValue[] trapArgs = new JSValue[]{target, thisArg, argArray};
        return trapFunc.call(executionContext, handler, trapArgs);
    }

    /**
     * Construct trap for constructor proxies.
     * ES2020 9.5.14 [[Construct]]
     */
    public JSValue construct(JSContext context, JSValue[] args, JSValue newTarget) {
        JSContext executionContext = resolveExecutionContext(context);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'construct' on a proxy that has been revoked");
            return null;
        }

        if (!JSTypeChecking.isConstructor(target)) {
            executionContext.throwTypeError("target is not a constructor");
            return null;
        }

        JSFunction trapFunc = getTrapFunction("construct", executionContext);
        if (executionContext.hasPendingException()) {
            return null;
        }
        if (trapFunc == null) {
            // ES2024 10.5.13 [[Construct]] step 7:
            // If trap is undefined, forward using full Construct(target, args, newTarget)
            // semantics (including bound functions, proxies, and custom newTarget).
            JSArray argumentListArray = createArgumentsArray(executionContext, args);
            return JSReflectObject.construct(executionContext, JSUndefined.INSTANCE, new JSValue[]{target, argumentListArray, newTarget});
        }

        // Create arguments array
        JSArray argArray = createArgumentsArray(executionContext, args);

        // Call trap: handler.construct(target, argumentsList, newTarget)
        JSValue[] trapArgs = new JSValue[]{target, argArray, newTarget};
        JSValue result = trapFunc.call(executionContext, handler, trapArgs);

        // Result must be an object
        if (!(result instanceof JSObject)) {
            executionContext.throwTypeError(
                    "construct trap must return an object");
            return null;
        }

        return result;
    }

    /**
     * Helper to create an arguments array from JSValue[].
     */
    private JSArray createArgumentsArray(JSContext executionContext, JSValue[] args) {
        JSArray array = executionContext.createJSArray(0, args.length);
        for (int i = 0; i < args.length; i++) {
            array.set(i, args[i]);
        }
        return array;
    }

    /**
     * Private field operations must bypass proxy traps and operate on the proxy object itself.
     */
    public void definePrivatePropertyDirect(PropertyKey key, PropertyDescriptor descriptor) {
        super.defineProperty(key, descriptor);
    }

    /**
     * Override defineProperty to route through proxy trap mechanism.
     * ES2020 9.5.6 [[DefineOwnProperty]]
     */
    @Override
    public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        return definePropertyWithResult(resolveExecutionContext(null), key, descriptor);
    }

    public boolean definePropertyWithResult(PropertyKey key, PropertyDescriptor descriptor) {
        return definePropertyWithResult(resolveExecutionContext(null), key, descriptor);
    }

    public boolean definePropertyWithResult(JSContext context, PropertyKey key, PropertyDescriptor descriptor) {
        JSContext executionContext = context != null ? context : this.context;
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'defineProperty' on a proxy that has been revoked");
            return false;
        }
        // Since JSFunction extends JSObject, target is always a JSObject
        JSObject targetObj = (JSObject) target;

        JSValue trap = handler.get(PropertyKey.fromString("defineProperty"));
        if (executionContext.hasPendingException()) {
            return false;
        }
        if (trap instanceof JSNull) {
            trap = JSUndefined.INSTANCE;
        }
        if (trap instanceof JSUndefined) {
            return targetObj.defineProperty(key, descriptor);
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            executionContext.throwTypeError("defineProperty trap must be a function");
            return false;
        }

        // Convert descriptor to object
        JSObject descObj = fromPropertyDescriptor(executionContext, descriptor);

        // Call trap: handler.defineProperty(target, property, descriptor)
        JSValue[] args = new JSValue[]{
                target,
                toKeyValue(key),
                descObj
        };
        JSValue result = trapFunc.call(executionContext, handler, args);
        if (executionContext.hasPendingException()) {
            return false;
        }

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (!boolResult) {
            return false;
        }

        // Validate invariants
        PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
        boolean targetExtensible = targetObj.isExtensible();

        boolean settingConfigFalse = descriptor.hasConfigurable() && !descriptor.isConfigurable();

        if (targetDesc == null) {
            // Property doesn't exist on target
            if (!targetExtensible || settingConfigFalse) {
                executionContext.throwTypeError(
                        "'defineProperty' on proxy: trap returned truish for adding property '" +
                                key.toPropertyString() +
                                "'  to the non-extensible proxy target");
                return false;
            }
        } else {
            // Property exists on target
            if (!isCompatiblePropertyDescriptor(targetExtensible, descriptor, targetDesc)) {
                executionContext.throwTypeError(
                        "'defineProperty' on proxy: trap returned truish for adding property '" +
                                key.toPropertyString() +
                                "'  that is incompatible with the existing property in the proxy target");
                return false;
            }
            if (targetDesc.isConfigurable() && settingConfigFalse) {
                executionContext.throwTypeError(
                        "proxy: inconsistent defineProperty");
                return false;
            }
            if (targetDesc.isDataDescriptor()
                    && !targetDesc.isConfigurable()
                    && targetDesc.isWritable()
                    && descriptor.hasWritable()
                    && !descriptor.isWritable()) {
                executionContext.throwTypeError(
                        "proxy: inconsistent defineProperty");
                return false;
            }
        }
        return true;
    }

    /**
     * Override delete to intercept property deletion.
     */
    @Override
    public boolean delete(PropertyKey key) {
        JSContext executionContext = resolveExecutionContext(null);
        boolean strictMode = executionContext.isStrictMode();
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'delete' on a proxy that has been revoked");
            return false;
        }

        JSObject targetObj = (JSObject) target;
        JSFunction deleteTrapFunc = getTrapFunction("deleteProperty", executionContext);
        if (executionContext.hasPendingException()) {
            return false;
        }
        if (deleteTrapFunc != null) {
            JSValue[] args = new JSValue[]{target, toKeyValue(key)};
            JSValue result = deleteTrapFunc.call(executionContext, handler, args);
            boolean success = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;

            if (success) {
                PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
                if (targetDesc != null) {
                    if (!targetDesc.isConfigurable()) {
                        executionContext.throwTypeError(
                                "'deleteProperty' on proxy: trap returned truish for property '" + key.toPropertyString() + "' which is non-configurable in the proxy target");
                        return false;
                    }
                    if (!targetObj.isExtensible()) {
                        executionContext.throwTypeError(
                                "'deleteProperty' on proxy: trap returned truish for property '" +
                                        key.toPropertyString() +
                                        "' but the proxy target is non-extensible");
                        return false;
                    }
                }
            }

            if (!success && strictMode) {
                executionContext.throwTypeError(
                        "'deleteProperty' on proxy: trap returned falsish for property '" + key.toPropertyString() + "'");
                return false;
            }
            return success;
        }

        // No trap, forward to target with the target context strictness.
        return targetObj.delete(key);
    }

    @Override
    public PropertyKey[] enumerableKeys() {
        List<PropertyKey> ownKeys = getOwnPropertyKeys();
        List<PropertyKey> enumerableKeys = new ArrayList<>(ownKeys.size());
        for (PropertyKey key : ownKeys) {
            PropertyDescriptor descriptor = getOwnPropertyDescriptor(key);
            if (descriptor != null && descriptor.isEnumerable()) {
                enumerableKeys.add(key);
            }
        }
        return enumerableKeys.toArray(new PropertyKey[0]);
    }

    /**
     * Helper to convert PropertyDescriptor to JSObject.
     */
    private JSObject fromPropertyDescriptor(JSContext context, PropertyDescriptor desc) {
        JSObject obj = context.createJSObject();

        if (desc.hasValue()) {
            obj.set(PropertyKey.VALUE, desc.getValue());
        }

        if (desc.hasWritable()) {
            obj.set(PropertyKey.WRITABLE,
                    desc.isWritable() ? JSBoolean.TRUE : JSBoolean.FALSE);
        }

        if (desc.hasGetter()) {
            obj.set(PropertyKey.GET, desc.getGetter());
        }

        if (desc.hasSetter()) {
            obj.set(PropertyKey.SET, desc.getSetter());
        }

        if (desc.hasEnumerable()) {
            obj.set(PropertyKey.ENUMERABLE,
                    desc.isEnumerable() ? JSBoolean.TRUE : JSBoolean.FALSE);
        }

        if (desc.hasConfigurable()) {
            obj.set(PropertyKey.CONFIGURABLE,
                    desc.isConfigurable() ? JSBoolean.TRUE : JSBoolean.FALSE);
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
        // Use this proxy as the receiver
        return getInternal(key, resolveExecutionContext(null), this);
    }

    public JSObject getHandler() {
        return handler;
    }

    /**
     * Internal get implementation that accepts a receiver parameter for prototype chain support.
     */
    private JSValue getInternal(PropertyKey key, JSContext context, JSValue receiver) {
        JSContext executionContext = resolveExecutionContext(context);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'get' on a proxy that has been revoked");
            return null;
        }

        // Check if handler has 'get' trap
        JSValue getTrap = handler.get(PropertyKey.GET);
        if (getTrap != null && !(getTrap instanceof JSUndefined) && !(getTrap instanceof JSNull)) {
            if (JSTypeChecking.isCallable(getTrap)) {
                // Call the trap: handler.get(property, receiver)
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
                JSValue trapResult = callTrapValue(executionContext, getTrap, handler, args);
                if (executionContext.hasPendingException()) {
                    return null;
                }

                // Check invariant: non-configurable accessor without getter must return undefined
                // Since JSFunction extends JSObject, target is always a JSObject
                if (target instanceof JSObject targetObj) {
                    PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
                    if (targetDesc != null && !targetDesc.isConfigurable() &&
                            targetDesc.isAccessorDescriptor() && targetDesc.getGetter() == null) {
                        // Non-configurable accessor without getter
                        if (!(trapResult instanceof JSUndefined)) {
                            String keyStr = key.isString() ? key.asString() : String.valueOf(key.getValue());
                            executionContext.throwTypeError(
                                    "'get' on proxy: property '" +
                                            keyStr +
                                            "' is a non-configurable accessor property on the proxy target and does not have a getter function, but the trap did not return 'undefined' (got '" +
                                            JSTypeConversions.toString(executionContext, trapResult).value() +
                                            "')");
                            return null;
                        }
                    }

                    // Check invariant: non-writable, non-configurable data property must return same value
                    if (targetDesc != null && targetDesc.isDataDescriptor() &&
                            !targetDesc.isConfigurable() && !targetDesc.isWritable()) {
                        // Non-writable, non-configurable data property
                        if (!JSTypeConversions.strictEquals(trapResult, targetDesc.getValue())) {
                            String keyStr = key.isString() ? key.asString() : String.valueOf(key.getValue());
                            executionContext.throwTypeError(
                                    "'get' on proxy: property '" +
                                            keyStr +
                                            "' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value (expected '" +
                                            JSTypeConversions.toString(executionContext, targetDesc.getValue()).value() +
                                            "' but got '" +
                                            JSTypeConversions.toString(executionContext, trapResult).value() +
                                            "')");
                            return null;
                        }
                    }
                }
                return trapResult;
            } else {
                executionContext.throwTypeError("'" + JSTypeConversions.toString(executionContext, getTrap) + "' returned for property 'get' of object '#<Object>' is not a function");
                return null;
            }
        }
        // No trap, forward to target
        // Following QuickJS js_proxy_get: forward to target
        // Since JSFunction now extends JSObject, all targets are JSObjects
        if (target instanceof JSObject targetObj) {
            return targetObj.get(key, receiver);
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Private field operations must bypass proxy traps and operate on the proxy object itself.
     */
    public PropertyDescriptor getOwnPrivatePropertyDescriptorDirect(PropertyKey key) {
        return super.getOwnPropertyDescriptor(key);
    }

    /**
     * Override getOwnPropertyDescriptor to intercept Object.getOwnPropertyDescriptor().
     * ES2020 9.5.5 [[GetOwnProperty]]
     */
    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        JSContext executionContext = resolveExecutionContext(null);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'getOwnPropertyDescriptor' on a proxy that has been revoked");
            return null;
        }

        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("getOwnPropertyDescriptor", executionContext);
        if (trap == null) {
            return null;
        }
        if (trap instanceof JSUndefined) {
            return targetObj.getOwnPropertyDescriptor(key);
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            executionContext.throwTypeError(
                    "getOwnPropertyDescriptor trap must be a function");
            return null;
        }

        // Call trap: handler.getOwnPropertyDescriptor(target, property)
        JSValue[] args = new JSValue[]{
                target,
                toKeyValue(key)
        };
        JSValue trapResult = trapFunc.call(executionContext, handler, args);

        // Result must be undefined or object
        if (!(trapResult instanceof JSUndefined) && !(trapResult instanceof JSObject)) {
            executionContext.throwTypeError(
                    "getOwnPropertyDescriptor trap must return an object or undefined");
            return null;
        }

        PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
        boolean targetExtensible = targetObj.isExtensible();

        if (trapResult instanceof JSUndefined) {
            // Invariant: cannot return undefined for non-configurable property
            if (targetDesc != null && !targetDesc.isConfigurable()) {
                executionContext.throwTypeError(
                        "'getOwnPropertyDescriptor' on proxy: trap returned undefined for property '" +
                                key.toPropertyString() +
                                "' which is non-configurable in the proxy target");
                return null;
            }
            // Invariant: cannot return undefined if target is non-extensible and has property
            if (!targetExtensible && targetDesc != null) {
                executionContext.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor");
                return null;
            }
            return null;
        }

        // Convert result to PropertyDescriptor
        PropertyDescriptor resultDesc = toPropertyDescriptor((JSObject) trapResult);
        if (executionContext.hasPendingException()) {
            return null;
        }

        // Validate against target descriptor
        if (targetDesc != null) {
            if (!isCompatiblePropertyDescriptor(targetExtensible, resultDesc, targetDesc)) {
                executionContext.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor");
                return null;
            }
            // Invariant: cannot return configurable for non-configurable property
            if (!targetDesc.isConfigurable() && resultDesc.isConfigurable()) {
                executionContext.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor");
                return null;
            }
            if (resultDesc.hasConfigurable() && !resultDesc.isConfigurable() && targetDesc.isConfigurable()) {
                executionContext.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor");
                return null;
            }
            if (targetDesc.isDataDescriptor()
                    && !targetDesc.isConfigurable()
                    && targetDesc.isWritable()
                    && resultDesc.hasWritable()
                    && !resultDesc.isWritable()) {
                executionContext.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor");
                return null;
            }
        } else {
            // Invariant: cannot return non-configurable if property doesn't exist on non-extensible target
            if (!targetExtensible) {
                executionContext.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor");
                return null;
            }
            if (resultDesc.hasConfigurable() && !resultDesc.isConfigurable()) {
                executionContext.throwTypeError(
                        "proxy: inconsistent getOwnPropertyDescriptor");
                return null;
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
        JSContext executionContext = resolveExecutionContext(null);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'getOwnPropertyKeys' on a proxy that has been revoked");
            return null;
        }

        JSFunction ownKeysTrapFunc = getTrapFunction("ownKeys", executionContext);
        if (executionContext.hasPendingException()) {
            return null;
        }
        if (ownKeysTrapFunc != null) {
            JSValue result = ownKeysTrapFunc.call(executionContext, handler, new JSValue[]{target});

            // Result must be array-like
            if (!(result instanceof JSObject resultObj)) {
                executionContext.throwTypeError(
                        "ownKeys trap result must be an object");
                return null;
            }

            // Get the length property
            JSValue lengthValue = resultObj.get(PropertyKey.LENGTH);
            long lengthLong = JSTypeConversions.toLength(executionContext, lengthValue);
            if (executionContext.hasPendingException()) {
                return null;
            }
            if (lengthLong > Integer.MAX_VALUE) {
                executionContext.throwTypeError("ownKeys trap result is too large");
                return null;
            }
            int length = (int) lengthLong;
            List<PropertyKey> keys = new ArrayList<>(length);

            // Convert result to PropertyKey list - CreateListFromArrayLike
            for (int i = 0; i < length; i++) {
                JSValue keyValue = resultObj.get(PropertyKey.fromIndex(i));
                if (keyValue instanceof JSString str) {
                    keys.add(PropertyKey.fromString(str.value()));
                } else if (keyValue instanceof JSSymbol sym) {
                    keys.add(PropertyKey.fromSymbol(sym));
                } else {
                    executionContext.throwTypeError(
                            "ownKeys trap result must contain only strings or symbols");
                    return null;
                }
            }

            // Check for duplicate properties
            Set<PropertyKey> propertyKeySet = new HashSet<>();
            for (PropertyKey key : keys) {
                if (!propertyKeySet.add(key)) {
                    executionContext.throwTypeError(
                            "'ownKeys' on proxy: trap returned duplicate entries");
                    return null;
                }
            }

            // Validate invariants per ES2024 10.5.11
            JSObject targetObj = (JSObject) target;
            List<PropertyKey> targetKeys = targetObj.getOwnPropertyKeys();
            boolean targetExtensible = targetObj.isExtensible();

            // Step 16: Classify target keys as configurable or non-configurable
            List<PropertyKey> targetNonconfigurableKeys = new ArrayList<>();
            List<PropertyKey> targetConfigurableKeys = new ArrayList<>();
            for (PropertyKey targetKey : targetKeys) {
                PropertyDescriptor desc = targetObj.getOwnPropertyDescriptor(targetKey);
                if (desc != null && !desc.isConfigurable()) {
                    targetNonconfigurableKeys.add(targetKey);
                } else {
                    targetConfigurableKeys.add(targetKey);
                }
            }

            // Step 18: uncheckedResultKeys = copy of trapResult
            Set<PropertyKey> uncheckedResultKeys = new HashSet<>(keys);

            // Step 19: For each non-configurable key, must be in trap result
            for (PropertyKey nonconfigurableKey : targetNonconfigurableKeys) {
                if (!uncheckedResultKeys.remove(nonconfigurableKey)) {
                    executionContext.throwTypeError(
                            "'ownKeys' on proxy: trap result did not include '" + nonconfigurableKey.toPropertyString() + "'");
                    return null;
                }
            }

            // Step 20: If target is extensible, return trapResult
            if (!targetExtensible) {
                // Step 21: For each configurable key, must also be in trap result
                for (PropertyKey configurableKey : targetConfigurableKeys) {
                    if (!uncheckedResultKeys.remove(configurableKey)) {
                        executionContext.throwTypeError(
                                "'ownKeys' on proxy: trap result did not include '" + configurableKey.toPropertyString() + "'");
                        return null;
                    }
                }

                // Step 22: If uncheckedResultKeys is not empty, throw TypeError
                if (!uncheckedResultKeys.isEmpty()) {
                    PropertyKey extraKey = uncheckedResultKeys.iterator().next();
                    executionContext.throwTypeError(
                            "'ownKeys' on proxy: trap returned extra key '" + extraKey.toPropertyString() + "'");
                    return null;
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
        JSContext executionContext = resolveExecutionContext(null);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'getPrototype' on a proxy that has been revoked");
            return null;
        }

        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("getPrototypeOf", executionContext);
        if (trap == null) {
            return null;
        }
        if (trap instanceof JSUndefined) {
            return targetObj.getPrototype();
        }

        if (!JSTypeChecking.isCallable(trap)) {
            executionContext.throwTypeError("getPrototypeOf trap must be a function");
            return null;
        }

        // Call trap: handler.getPrototypeOf(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result;
        if (trap instanceof JSProxy proxyTrap) {
            result = proxyTrap.apply(executionContext, handler, args);
        } else {
            JSFunction trapFunction = (JSFunction) trap;
            result = trapFunction.call(executionContext, handler, args);
        }

        // Validate result is null or object
        if (!(result instanceof JSNull) && !(result instanceof JSObject)) {
            executionContext.throwTypeError(
                    "proxy getPrototypeOf handler must return an object or null");
            return null;
        }
        JSObject handlerPrototype = result instanceof JSNull ? null : (JSObject) result;

        // Check invariants if target is not extensible
        if (!targetObj.isExtensible()) {
            JSObject targetProto = targetObj.getPrototype();
            if (!sameValue(targetProto, handlerPrototype)) {
                executionContext.throwTypeError(
                        "'getPrototypeOf' on proxy: proxy target is non-extensible but the trap did not return its actual prototype");
                return null;
            }
        }

        return handlerPrototype;
    }

    public JSContext getProxyContext() {
        return context;
    }

    public JSValue getTarget() {
        return target;
    }

    private JSFunction getTrapFunction(String trapName) {
        return getTrapFunction(trapName, resolveExecutionContext(null));
    }

    private JSFunction getTrapFunction(String trapName, JSContext executionContext) {
        JSValue trap = getTrapMethod(trapName, executionContext);
        if (trap == null) {
            // getTrapMethod returned null due to error (e.g. revoked proxy)
            return null;
        }
        if (trap instanceof JSUndefined) {
            return null;
        }
        if (trap instanceof JSFunction trapFunc) {
            return trapFunc;
        }
        // A Proxy wrapping a callable target is also callable (ES2024 10.5.12/13)
        if (trap instanceof JSProxy trapProxy && JSTypeChecking.isCallable(trapProxy)) {
            return new JSNativeFunction(executionContext, trapName, 0, (callContext, thisArg, callArgs) ->
                    trapProxy.apply(callContext, thisArg, callArgs));
        }
        String trapValue = JSTypeConversions.toString(executionContext, trap).value();
        executionContext.throwTypeError(
                "'" + trapValue + "' returned for property '" + trapName + "' of object '#<Object>' is not a function");
        return null;
    }

    /**
     * Helper to get a trap method from the handler.
     * Returns null if handler is null/undefined.
     * Following QuickJS get_proxy_method().
     */
    private JSValue getTrapMethod(String trapName) {
        return getTrapMethod(trapName, resolveExecutionContext(null));
    }

    private JSValue getTrapMethod(String trapName, JSContext executionContext) {
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'getTrapMethod' on a proxy that has been revoked");
            return null;
        }

        JSValue method = handler.get(PropertyKey.fromString(trapName));
        // Treat null as undefined
        if (method instanceof JSNull) {
            return JSUndefined.INSTANCE;
        }
        return method;
    }

    /**
     * Override get method with receiver tracking for prototype chain support.
     */
    @Override
    protected JSValue getWithReceiver(PropertyKey key, JSValue receiver, int depth) {
        return getInternal(key, resolveExecutionContext(null), receiver);
    }

    /**
     * Override has to intercept 'in' operator.
     */
    @Override
    public boolean has(PropertyKey key) {
        JSContext executionContext = resolveExecutionContext(null);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'has' on a proxy that has been revoked");
            return false;
        }

        JSObject targetObj = (JSObject) target;
        JSFunction hasTrapFunc = getTrapFunction("has", executionContext);
        if (hasTrapFunc != null) {
            JSValue[] args = new JSValue[]{target, toKeyValue(key)};
            JSValue result = hasTrapFunc.call(executionContext, handler, args);
            boolean trapResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;

            if (!trapResult) {
                PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
                if (targetDesc != null && (!targetDesc.isConfigurable() || !targetObj.isExtensible())) {
                    if (!targetDesc.isConfigurable()) {
                        executionContext.throwTypeError(
                                "'has' on proxy: trap returned falsish for property '" +
                                        key.toPropertyString() +
                                        "' which exists in the proxy target as non-configurable");
                        return false;
                    }
                    executionContext.throwTypeError(
                            "'has' on proxy: trap returned falsish for property '" +
                                    key.toPropertyString() +
                                    "' but the proxy target is not extensible");
                    return false;
                }
            }

            return trapResult;
        }

        // No trap, forward to target
        return targetObj.has(key);
    }

    /**
     * Private field operations must bypass proxy traps and operate on the proxy object itself.
     */
    public boolean hasOwnPrivatePropertyDirect(PropertyKey key) {
        return super.getOwnPropertyDescriptor(key) != null;
    }

    @Override
    public boolean hasOwnProperty(PropertyKey key) {
        return getOwnPropertyDescriptor(key) != null;
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

        // Step 5: If desc is a generic descriptor (no [[Value]], [[Writable]], [[Get]], [[Set]]),
        // no further validation is required per ES2024 10.1.6.3.
        boolean descIsData = desc.hasValue() || desc.hasWritable();
        boolean descIsAccessor = desc.hasGetter() || desc.hasSetter();
        if (!descIsData && !descIsAccessor) {
            return true;
        }

        // Step 6: Check if converting between data and accessor
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
        JSContext executionContext = resolveExecutionContext(null);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'isExtensible' on a proxy that has been revoked");
            return false;
        }

        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("isExtensible", executionContext);
        if (executionContext.hasPendingException()) {
            return false;
        }
        if (trap instanceof JSUndefined) {
            return targetObj.isExtensible();
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            executionContext.throwTypeError("isExtensible trap must be a function");
            return false;
        }

        // Call trap: handler.isExtensible(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = trapFunc.call(executionContext, handler, args);

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        boolean targetExtensible = targetObj.isExtensible();

        // Invariant: result must match target's extensibility
        if (boolResult != targetExtensible) {
            executionContext.throwTypeError(
                    "'isExtensible' on proxy: trap result does not reflect extensibility of proxy target (which is 'true')");
            return false;
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
        JSContext executionContext = resolveExecutionContext(null);
        boolean success = preventExtensionsWithResult();
        if (executionContext.hasPendingException()) {
            return;
        }
        if (!success) {
            executionContext.throwTypeError("'preventExtensions' on proxy: trap returned falsish");
            return;
        }
    }

    public boolean preventExtensionsWithResult() {
        JSContext executionContext = resolveExecutionContext(null);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'preventExtensions' on a proxy that has been revoked");
            return false;
        }

        JSObject targetObj = (JSObject) target;

        JSValue trap = getTrapMethod("preventExtensions", executionContext);
        if (executionContext.hasPendingException()) {
            return false;
        }
        if (trap instanceof JSUndefined) {
            targetObj.preventExtensions();
            return true;
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            executionContext.throwTypeError("preventExtensions trap must be a function");
            return false;
        }

        // Call trap: handler.preventExtensions(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = trapFunc.call(executionContext, handler, args);

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (boolResult) {
            // Invariant: if trap returns true, target must be non-extensible
            if (targetObj.isExtensible()) {
                executionContext.throwTypeError(
                        "'preventExtensions' on proxy: trap returned truish but the proxy target is extensible");
                return false;
            }
        }
        return boolResult;
    }

    /**
     * Context-aware assignment entry point used by VM/Reflect paths that must
     * preserve current execution realm for TypeError construction.
     */
    public void proxySet(JSContext executionContext, PropertyKey key, JSValue value) {
        proxySetInternal(executionContext, key, value, this, true);
    }

    public void proxySet(JSContext executionContext, PropertyKey key, JSValue value, JSObject receiver) {
        proxySetInternal(executionContext, key, value, receiver, true);
    }

    private boolean proxySetInternal(
            JSContext executionContext,
            PropertyKey key,
            JSValue value,
            JSValue receiver,
            boolean throwOnFailure) {
        JSContext effectiveContext = resolveExecutionContext(executionContext);
        if (revoked) {
            effectiveContext.throwTypeError("Cannot perform 'set' on a proxy that has been revoked");
            return false;
        }

        JSObject targetObj = (JSObject) target;
        JSFunction setTrapFunc = getTrapFunction("set");
        if (effectiveContext.hasPendingException()) {
            return false;
        }
        if (setTrapFunc != null) {
            JSValue[] args = new JSValue[]{
                    target,
                    toKeyValue(key),
                    value,
                    receiver
            };
            JSValue result = setTrapFunc.call(effectiveContext, handler, args);
            boolean trapResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;

            if (!trapResult) {
                if (throwOnFailure && effectiveContext.isStrictMode()) {
                    effectiveContext.throwTypeError(
                            "'set' on proxy: trap returned falsish for property '" + key.toPropertyString() + "'");
                }
                return false;
            }

            PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
            if (targetDesc != null) {
                if (targetDesc.isDataDescriptor() && !targetDesc.isConfigurable() && !targetDesc.isWritable()) {
                    if (!sameValue(targetDesc.getValue(), value)) {
                        effectiveContext.throwTypeError(
                                "'set' on proxy: trap returned truish for property '" +
                                        key.toPropertyString() +
                                        "' which exists in the proxy target as a non-configurable and non-writable data property with a different value");
                        return false;
                    }
                } else if (targetDesc.isAccessorDescriptor() && !targetDesc.isConfigurable() && targetDesc.getSetter() == null) {
                    effectiveContext.throwTypeError(
                            "'set' on proxy: trap returned truish for property '" +
                                    key.toPropertyString() +
                                    "' which exists in the proxy target as a non-configurable and non-writable accessor property without a setter");
                    return false;
                }
            }
            return true;
        }

        boolean success = targetObj.setWithResult(key, value, receiver);
        if (!success && throwOnFailure && effectiveContext.isStrictMode()) {
            effectiveContext.throwTypeError(
                    "'set' on proxy: trap returned falsish for property '" + key.toPropertyString() + "'");
        }
        return success;
    }

    public boolean proxySetWithReceiver(PropertyKey key, JSValue value, JSValue receiver) {
        return proxySetInternal(resolveExecutionContext(null), key, value, receiver, false);
    }

    private JSContext resolveExecutionContext(JSContext explicitContext) {
        if (explicitContext != null) {
            return explicitContext;
        }
        JSContext current = context.getRuntime().getCurrentExecutingContext();
        return current != null ? current : context;
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
        proxySetInternal(resolveExecutionContext(null), key, value, this, true);
    }

    /**
     * Private field operations must bypass proxy traps and operate on the proxy object itself.
     */
    public void setPrivatePropertyDirect(PropertyKey key, JSValue value) {
        super.setPrivatePropertyDirect(key, value);
    }

    /**
     * Override setPrototype to intercept Object.setPrototypeOf().
     * ES2020 9.5.2 [[SetPrototypeOf]]
     */
    @Override
    public void setPrototype(JSObject proto) {
        JSContext executionContext = resolveExecutionContext(null);
        boolean success = setPrototypeWithResult(proto);
        if (executionContext.hasPendingException()) {
            return;
        }
        if (!success) {
            executionContext.throwTypeError("'setPrototypeOf' on proxy: trap returned falsish for property 'undefined'");
            return;
        }
    }

    public boolean setPrototypeWithResult(JSObject proto) {
        JSContext executionContext = resolveExecutionContext(null);
        if (revoked) {
            executionContext.throwTypeError("Cannot perform 'setPrototype' on a proxy that has been revoked");
            return false;
        }

        JSObject targetObj = (JSObject) target;

        JSFunction trapFunc = getTrapFunction("setPrototypeOf", executionContext);
        if (executionContext.hasPendingException()) {
            return false;
        }
        if (trapFunc == null) {
            // No trap - forward to target per ES2020 9.5.2 step 5
            if (targetObj instanceof JSProxy targetProxy) {
                return targetProxy.setPrototypeWithResult(proto);
            }
            JSObject.SetPrototypeResult result = targetObj.setPrototypeChecked(proto);
            return result == JSObject.SetPrototypeResult.SUCCESS;
        }

        // Call trap: handler.setPrototypeOf(target, proto)
        JSValue[] args = new JSValue[]{target, proto == null ? JSNull.INSTANCE : proto};
        JSValue result = trapFunc.call(executionContext, handler, args);

        // Convert to boolean
        boolean boolResult = JSTypeConversions.toBoolean(result).isBooleanTrue();
        if (boolResult) {
            // Check invariants if target is not extensible
            if (!targetObj.isExtensible()) {
                JSObject targetProto = targetObj.getPrototype();
                if (!sameValue(targetProto, proto)) {
                    executionContext.throwTypeError(
                            "'setPrototypeOf' on proxy: trap returned truish for setting a new prototype on the non-extensible proxy target");
                    return false;
                }
            }
        }
        return boolResult;
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value) {
        return proxySetInternal(resolveExecutionContext(null), key, value, this, false);
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value, JSObject receiver) {
        return proxySetInternal(resolveExecutionContext(null), key, value, receiver, false);
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value, JSValue receiver) {
        return proxySetInternal(resolveExecutionContext(null), key, value, receiver, false);
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
        JSContext executionContext = resolveExecutionContext(null);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        boolean hasAccessorFields = false;
        boolean hasDataFields = false;

        if (obj.has(PropertyKey.ENUMERABLE)) {
            JSValue enumerableValue = obj.get(PropertyKey.ENUMERABLE);
            if (executionContext.hasPendingException()) {
                return null;
            }
            descriptor.setEnumerable(JSTypeConversions.toBoolean(enumerableValue) == JSBoolean.TRUE);
        }

        if (obj.has(PropertyKey.CONFIGURABLE)) {
            JSValue configurableValue = obj.get(PropertyKey.CONFIGURABLE);
            if (executionContext.hasPendingException()) {
                return null;
            }
            descriptor.setConfigurable(JSTypeConversions.toBoolean(configurableValue) == JSBoolean.TRUE);
        }

        if (obj.has(PropertyKey.VALUE)) {
            JSValue value = obj.get(PropertyKey.VALUE);
            if (executionContext.hasPendingException()) {
                return null;
            }
            descriptor.setValue(value);
            hasDataFields = true;
        }

        if (obj.has(PropertyKey.WRITABLE)) {
            JSValue writableValue = obj.get(PropertyKey.WRITABLE);
            if (executionContext.hasPendingException()) {
                return null;
            }
            descriptor.setWritable(JSTypeConversions.toBoolean(writableValue) == JSBoolean.TRUE);
            hasDataFields = true;
        }

        if (obj.has(PropertyKey.GET)) {
            JSValue getterValue = obj.get(PropertyKey.GET);
            if (executionContext.hasPendingException()) {
                return null;
            }
            if (!(getterValue instanceof JSUndefined) && !JSTypeChecking.isCallable(getterValue)) {
                executionContext.throwTypeError("Getter must be a function");
                return null;
            }
            if (getterValue instanceof JSFunction getterFunction) {
                descriptor.setGetter(getterFunction);
            } else {
                descriptor.setGetter(null);
            }
            hasAccessorFields = true;
        }

        if (obj.has(PropertyKey.SET)) {
            JSValue setterValue = obj.get(PropertyKey.SET);
            if (executionContext.hasPendingException()) {
                return null;
            }
            if (!(setterValue instanceof JSUndefined) && !JSTypeChecking.isCallable(setterValue)) {
                executionContext.throwTypeError("Setter must be a function");
                return null;
            }
            if (setterValue instanceof JSFunction setterFunction) {
                descriptor.setSetter(setterFunction);
            } else {
                descriptor.setSetter(null);
            }
            hasAccessorFields = true;
        }

        if (hasAccessorFields && hasDataFields) {
            executionContext.throwTypeError(
                    "Invalid property descriptor. Cannot both specify accessors and a value or writable attribute, #<Object>");
            return null;
        }

        return descriptor;
    }

    @Override
    public String toString() {
        return "[object Proxy]";
    }
}
