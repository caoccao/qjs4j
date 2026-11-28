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
import java.util.List;

/**
 * Represents a JavaScript Proxy object. Proxies wrap a target object and intercept operations on it via handler traps.
 * Based on ES2020 Proxy specification (simplified).
 */
public final class JSProxy extends JSObject {
    public static final String NAME = "Proxy";
    private final JSObject handler;
    private boolean revoked = false;
    private final JSValue target;

    /**
     * Create a new Proxy.
     *
     * @param context
     *            The execution context
     * @param target
     *            The target object or function to wrap
     * @param handler
     *            The handler object with trap functions
     */
    public JSProxy(JSContext context, JSValue target, JSObject handler) {
        super(context);
        this.target = target;
        this.handler = handler;
    }

    /**
     * Apply trap for function proxies. ES2020 9.5.13 [[Call]]
     */
    public JSValue apply(JSContext context, JSValue thisArg, JSValue[] args) {
        JSContext executionContext = resolveExecutionContext(context);
        if (rejectRevoked(executionContext, "apply")) {
            return null;
        }

        if (!JSTypeChecking.isFunction(target)) {
            return executionContext.rejectTypeError("not a function");
        }

        JSValue trapFunc = getCallableTrap("apply", executionContext);
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
            return executionContext.rejectTypeError("target is not callable");
        }

        // Create arguments array
        JSArray argArray = executionContext.createJSArray(args);

        // Call trap: handler.apply(target, thisArg, argumentsList)
        JSValue[] trapArgs = new JSValue[]{target, thisArg, argArray};
        return callCallableTrap(executionContext, executionContext, trapFunc, trapArgs);
    }

    private JSValue callCallableTrap(JSContext caller, JSContext trapContext, JSValue trap, JSValue[] args) {
        if (trap instanceof JSProxy proxy) {
            // Keep the native trap's interrupt and cross-realm exception boundary without allocating a JS function.
            return JSNativeFunction.callInRealm(caller, trapContext, proxy::apply, handler, args);
        }
        return ((JSFunction) trap).call(caller, handler, args);
    }

    private boolean checkExtensibility(boolean prevent) {
        JSContext executionContext = resolveExecutionContext(null);
        String operation = prevent ? "preventExtensions" : "isExtensible";
        if (rejectRevoked(executionContext, operation)) {
            return false;
        }
        JSObject object = (JSObject) target;
        JSValue trap = getTrapMethod(operation, executionContext);
        if (executionContext.hasPendingException()) {
            return false;
        }
        if (trap instanceof JSUndefined) {
            if (prevent) {
                object.preventExtensions();
                return true;
            }
            return object.isExtensible();
        }
        if (!(trap instanceof JSFunction function)) {
            executionContext.throwTypeError(operation + " trap must be a function");
            return false;
        }
        JSValue result = function.call(executionContext, handler, new JSValue[]{target});
        boolean reported = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (prevent) {
            if (reported && object.isExtensible()) {
                executionContext.throwTypeError(
                        "'preventExtensions' on proxy: trap returned truish but the proxy target is extensible");
                return false;
            }
        } else if (reported != object.isExtensible()) {
            executionContext.throwTypeError(
                    "'isExtensible' on proxy: trap result does not reflect extensibility of proxy target (which is 'true')");
            return false;
        }
        return reported;
    }

    /**
     * Construct trap for constructor proxies. ES2020 9.5.14 [[Construct]]
     */
    public JSValue construct(JSContext context, JSValue[] args, JSValue newTarget) {
        JSContext executionContext = resolveExecutionContext(context);
        if (rejectRevoked(executionContext, "construct")) {
            return null;
        }

        if (!JSTypeChecking.isConstructor(target)) {
            return executionContext.rejectTypeError("target is not a constructor");
        }

        JSValue trapFunc = getCallableTrap("construct", executionContext);
        if (executionContext.hasPendingException()) {
            return null;
        }
        if (trapFunc == null) {
            // ES2024 10.5.13 [[Construct]] step 7:
            // If trap is undefined, forward using full Construct(target, args, newTarget)
            // semantics (including bound functions, proxies, and custom newTarget).
            return JSReflectObject.constructWithNewTarget(executionContext, target, args, newTarget);
        }

        // Create arguments array
        JSArray argArray = executionContext.createJSArray(args);

        // Call trap: handler.construct(target, argumentsList, newTarget)
        JSValue[] trapArgs = new JSValue[]{target, argArray, newTarget};
        JSValue result = callCallableTrap(executionContext, executionContext, trapFunc, trapArgs);

        // Result must be an object
        if (!(result instanceof JSObject)) {
            return executionContext.rejectTypeError("construct trap must return an object");
        }

        return result;
    }

    /**
     * Helper to create an arguments array from JSValue[].
     */
    /**
     * Private field operations must bypass proxy traps and operate on the proxy object itself.
     */
    public void definePrivatePropertyDirect(PropertyKey key, PropertyDescriptor descriptor) {
        super.defineProperty(key, descriptor);
    }

    /**
     * Override defineProperty to route through proxy trap mechanism. ES2020 9.5.6 [[DefineOwnProperty]]
     */
    @Override
    public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        return definePropertyWithResult(resolveExecutionContext(null), key, descriptor);
    }

    public boolean definePropertyWithResult(JSContext context, PropertyKey key, PropertyDescriptor descriptor) {
        JSContext executionContext = context != null ? context : this.context;
        if (rejectRevoked(executionContext, "defineProperty")) {
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
        JSObject descObj = descriptor.toPartialDescriptorObject(executionContext);

        // Call trap: handler.defineProperty(target, property, descriptor)
        JSValue[] args = new JSValue[]{target, toKeyValue(key), descObj};
        JSValue result = trapFunc.call(executionContext, handler, args);
        if (executionContext.hasPendingException()) {
            return false;
        }

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (!boolResult) {
            return false;
        }

        PropertyDescriptor current = targetObj.getOwnPropertyDescriptor(key);
        return ProxyDescriptorInvariants.validateDefinition(executionContext, key, descriptor, current,
                targetObj.isExtensible());
    }

    public boolean definePropertyWithResult(PropertyKey key, PropertyDescriptor descriptor) {
        return definePropertyWithResult(resolveExecutionContext(null), key, descriptor);
    }

    /**
     * Override delete to intercept property deletion.
     */
    @Override
    public boolean delete(PropertyKey key, boolean throwOnFailure) {
        JSContext executionContext = resolveExecutionContext(null);
        boolean strictMode = throwOnFailure;
        if (rejectRevoked(executionContext, "delete")) {
            return false;
        }

        JSObject targetObj = (JSObject) target;
        JSValue deleteTrapFunc = getCallableTrap("deleteProperty", executionContext);
        if (executionContext.hasPendingException()) {
            return false;
        }
        if (deleteTrapFunc != null) {
            JSValue[] args = new JSValue[]{target, toKeyValue(key)};
            JSValue result = callCallableTrap(executionContext, executionContext, deleteTrapFunc, args);
            boolean success = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;

            if (success) {
                return ProxyDescriptorInvariants.canHideProperty(executionContext, targetObj, key, true);
            }
            if (strictMode) {
                executionContext.throwTypeError("'deleteProperty' on proxy: trap returned falsish for property '"
                        + key.toPropertyString() + "'");
                return false;
            }
            return success;
        }

        // No trap, forward to target.
        return targetObj.delete(key, throwOnFailure);
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

    /**
     * Override get to intercept property access by string.
     */
    @Override
    public JSValue get(String propertyName) {
        return get(PropertyKey.fromString(propertyName));
    }

    private JSValue getCallableTrap(String trapName, JSContext executionContext) {
        JSValue trap = getTrapMethod(trapName, executionContext);
        if (JSTypeChecking.isUndefinedOrMissing(trap)) {
            return null;
        }
        if (JSTypeChecking.isCallable(trap)) {
            return trap;
        }
        String trapValue = JSTypeConversions.toString(executionContext, trap).value();
        return executionContext.rejectTypeError(
                "'" + trapValue + "' returned for property '" + trapName + "' of object '#<Object>' is not a function");
    }

    public JSObject getHandler() {
        return handler;
    }

    /**
     * Internal get implementation that accepts a receiver parameter for prototype chain support.
     */
    private JSValue getInternal(PropertyKey key, JSContext context, JSValue receiver) {
        JSContext executionContext = resolveExecutionContext(context);
        if (rejectRevoked(executionContext, "get")) {
            return null;
        }

        // Check if handler has 'get' trap
        JSValue getTrap = handler.get(PropertyKey.GET);
        if (getTrap != null && !(getTrap instanceof JSUndefined) && !(getTrap instanceof JSNull)) {
            if (JSTypeChecking.isCallable(getTrap)) {
                JSValue[] args = new JSValue[]{target, toKeyValue(key), receiver};
                JSValue trapResult = callTrapValue(executionContext, getTrap, handler, args);
                if (executionContext.hasPendingException()) {
                    return null;
                }

                // Check invariant: non-configurable accessor without getter must return undefined
                // Since JSFunction extends JSObject, target is always a JSObject
                if (target instanceof JSObject targetObj) {
                    PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
                    if (targetDesc != null && !targetDesc.isConfigurable() && targetDesc.isAccessorDescriptor()
                            && targetDesc.getGetter() == null) {
                        // Non-configurable accessor without getter
                        if (!(trapResult instanceof JSUndefined)) {
                            String keyStr = key.isString() ? key.asString() : String.valueOf(key.getValue());
                            return executionContext.rejectTypeError("'get' on proxy: property '" + keyStr
                                    + "' is a non-configurable accessor property on the proxy target and does not have a getter function, but the trap did not return 'undefined' (got '"
                                    + JSTypeConversions.toString(executionContext, trapResult).value() + "')");
                        }
                    }

                    // Check invariant: non-writable, non-configurable data property must return same value
                    if (targetDesc != null && targetDesc.isDataDescriptor() && !targetDesc.isConfigurable()
                            && !targetDesc.isWritable()) {
                        // Non-writable, non-configurable data property
                        if (!JSTypeConversions.strictEquals(trapResult, targetDesc.getValue())) {
                            String keyStr = key.isString() ? key.asString() : String.valueOf(key.getValue());
                            return executionContext.rejectTypeError("'get' on proxy: property '" + keyStr
                                    + "' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value (expected '"
                                    + JSTypeConversions.toString(executionContext, targetDesc.getValue()).value()
                                    + "' but got '" + JSTypeConversions.toString(executionContext, trapResult).value()
                                    + "')");
                        }
                    }
                }
                return trapResult;
            } else {
                return executionContext.rejectTypeError("'" + JSTypeConversions.toString(executionContext, getTrap)
                        + "' returned for property 'get' of object '#<Object>' is not a function");
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
        return super.getOwnPropertyDescriptorRaw(key);
    }

    /**
     * Override getOwnPropertyDescriptor to intercept Object.getOwnPropertyDescriptor(). ES2020 9.5.5 [[GetOwnProperty]]
     */
    @Override
    protected PropertyDescriptor getOwnPropertyDescriptorRaw(PropertyKey key) {
        JSContext executionContext = resolveExecutionContext(null);
        if (rejectRevoked(executionContext, "getOwnPropertyDescriptor")) {
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
            return executionContext.rejectTypeError("getOwnPropertyDescriptor trap must be a function");
        }

        // Call trap: handler.getOwnPropertyDescriptor(target, property)
        JSValue[] args = new JSValue[]{target, toKeyValue(key)};
        JSValue trapResult = trapFunc.call(executionContext, handler, args);

        // Result must be undefined or object
        if (!(trapResult instanceof JSUndefined) && !(trapResult instanceof JSObject)) {
            return executionContext.rejectTypeError("getOwnPropertyDescriptor trap must return an object or undefined");
        }

        PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
        boolean targetExtensible = targetObj.isExtensible();

        if (trapResult instanceof JSUndefined) {
            // Invariant: cannot return undefined for non-configurable property
            if (targetDesc != null && !targetDesc.isConfigurable()) {
                return executionContext
                        .rejectTypeError("'getOwnPropertyDescriptor' on proxy: trap returned undefined for property '"
                                + key.toPropertyString() + "' which is non-configurable in the proxy target");
            }
            // Invariant: cannot return undefined if target is non-extensible and has property
            if (!targetExtensible && targetDesc != null) {
                return executionContext.rejectTypeError("proxy: inconsistent getOwnPropertyDescriptor");
            }
            return null;
        }

        // Convert result to PropertyDescriptor
        PropertyDescriptor resultDesc = toPropertyDescriptor((JSObject) trapResult);
        if (executionContext.hasPendingException()) {
            return null;
        }

        if (!ProxyDescriptorInvariants.canReport(resultDesc, targetDesc, targetExtensible)) {
            return executionContext.rejectTypeError("proxy: inconsistent getOwnPropertyDescriptor");
        }
        return resultDesc;
    }

    /**
     * Override getOwnPropertyKeys to intercept Object.keys(), etc. This follows QuickJS js_proxy_getOwnPropertyNames
     * implementation.
     * <p>
     * A failing trap reports the error through the context's pending exception and returns an empty list. Returning
     * {@code null} instead would make every one of the ~35 callers of {@link JSObject#getOwnPropertyKeys()} throw a
     * {@link NullPointerException}, which the VM then reports as an opaque internal error rather than as the trap's own
     * {@code TypeError}.
     */
    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        JSContext executionContext = resolveExecutionContext(null);
        if (rejectRevoked(executionContext, "getOwnPropertyKeys")) {
            return new ArrayList<>();
        }

        JSValue ownKeysTrapFunc = getCallableTrap("ownKeys", executionContext);
        if (executionContext.hasPendingException()) {
            return new ArrayList<>();
        }
        if (ownKeysTrapFunc != null) {
            JSValue result = callCallableTrap(executionContext, executionContext, ownKeysTrapFunc,
                    new JSValue[]{target});

            return ProxyOwnKeys.read(executionContext, (JSObject) target, result);
        }

        // No trap, forward to target
        return ((JSObject) target).getOwnPropertyKeys();
    }

    /**
     * Override getPrototype to intercept Object.getPrototypeOf(). ES2020 9.5.1 [[GetPrototypeOf]]
     */
    @Override
    public JSObject getPrototype() {
        JSContext executionContext = resolveExecutionContext(null);
        if (rejectRevoked(executionContext, "getPrototype")) {
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
            return executionContext.rejectTypeError("getPrototypeOf trap must be a function");
        }

        // Call trap: handler.getPrototypeOf(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = callTrapValue(executionContext, trap, handler, args);

        // Validate result is null or object
        if (!(result instanceof JSNull) && !(result instanceof JSObject)) {
            return executionContext.rejectTypeError("proxy getPrototypeOf handler must return an object or null");
        }
        JSObject handlerPrototype = result instanceof JSNull ? null : (JSObject) result;

        // Check invariants if target is not extensible
        if (!targetObj.isExtensible()) {
            JSObject targetProto = targetObj.getPrototype();
            if (!sameValue(targetProto, handlerPrototype)) {
                return executionContext.rejectTypeError(
                        "'getPrototypeOf' on proxy: proxy target is non-extensible but the trap did not return its actual prototype");
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

    /**
     * Helper to get a trap method from the handler. Returns null if handler is null/undefined. Following QuickJS
     * get_proxy_method().
     */

    private JSValue getTrapMethod(String trapName, JSContext executionContext) {
        if (rejectRevoked(executionContext, "getTrapMethod")) {
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
    protected boolean has(PropertyKey key, int depth) {
        JSContext executionContext = resolveExecutionContext(null);
        if (rejectRevoked(executionContext, "has")) {
            return false;
        }

        JSObject targetObj = (JSObject) target;
        JSValue hasTrapFunc = getCallableTrap("has", executionContext);
        if (hasTrapFunc != null) {
            JSValue[] args = new JSValue[]{target, toKeyValue(key)};
            JSValue result = callCallableTrap(executionContext, executionContext, hasTrapFunc, args);
            boolean trapResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;

            if (!trapResult) {
                ProxyDescriptorInvariants.canHideProperty(executionContext, targetObj, key, false);
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
        return super.getOwnPropertyDescriptorRaw(key) != null;
    }

    @Override
    public boolean hasOwnProperty(PropertyKey key) {
        return getOwnPropertyDescriptor(key) != null;
    }

    /**
     * A proxy's traps decide the whole lookup, so a proxy in someone else's prototype chain is always dispatched to
     * rather than walked through.
     *
     * @param key
     *            the property being looked up
     * @return always true
     */
    @Override
    protected boolean interceptsPropertyLookup(PropertyKey key) {
        return true;
    }

    /**
     * Override isExtensible to intercept Object.isExtensible(). ES2020 9.5.3 [[IsExtensible]]
     */
    @Override
    public boolean isExtensible() {
        return checkExtensibility(false);
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
     * Override preventExtensions to intercept Object.preventExtensions(). ES2020 9.5.4 [[PreventExtensions]]
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
        }
    }

    public boolean preventExtensionsWithResult() {
        return checkExtensibility(true);
    }

    /**
     * Context-aware assignment entry point used by VM/Reflect paths that must preserve current execution realm for
     * TypeError construction.
     */
    public void proxySet(JSContext executionContext, PropertyKey key, JSValue value) {
        proxySetInternal(executionContext, key, value, this, true);
    }

    public void proxySet(JSContext executionContext, PropertyKey key, JSValue value, JSObject receiver) {
        proxySetInternal(executionContext, key, value, receiver, true);
    }

    private boolean proxySetInternal(JSContext executionContext, PropertyKey key, JSValue value, JSValue receiver,
            boolean throwOnFailure) {
        JSContext effectiveContext = resolveExecutionContext(executionContext);
        if (rejectRevoked(effectiveContext, "set")) {
            return false;
        }

        JSObject targetObj = (JSObject) target;
        JSContext trapContext = resolveExecutionContext(null);
        JSValue setTrapFunc = getCallableTrap("set", trapContext);
        if (effectiveContext.hasPendingException()) {
            return false;
        }
        if (setTrapFunc != null) {
            JSValue[] args = new JSValue[]{target, toKeyValue(key), value, receiver};
            JSValue result = callCallableTrap(effectiveContext, trapContext, setTrapFunc, args);
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
                        effectiveContext.throwTypeError("'set' on proxy: trap returned truish for property '"
                                + key.toPropertyString()
                                + "' which exists in the proxy target as a non-configurable and non-writable data property with a different value");
                        return false;
                    }
                } else if (targetDesc.isAccessorDescriptor() && !targetDesc.isConfigurable()
                        && targetDesc.getSetter() == null) {
                    effectiveContext.throwTypeError("'set' on proxy: trap returned truish for property '"
                            + key.toPropertyString()
                            + "' which exists in the proxy target as a non-configurable and non-writable accessor property without a setter");
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

    private boolean rejectRevoked(JSContext executionContext, String operation) {
        if (revoked) {
            executionContext.throwTypeError("Cannot perform '" + operation + "' on a proxy that has been revoked");
        }
        return revoked;
    }

    private JSContext resolveExecutionContext(JSContext explicitContext) {
        if (explicitContext != null) {
            return explicitContext;
        }
        JSContext current = context.getRuntime().getCurrentExecutingContext();
        return current != null ? current : context;
    }

    /**
     * Revoke this proxy. After revocation, all proxy operations will throw TypeError. ES2020 26.2.2.1.1
     */
    public void revoke() {
        this.revoked = true;
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
     * Override set to intercept property assignment by string.
     */
    @Override
    public void set(String propertyName, JSValue value) {
        set(PropertyKey.fromString(propertyName), value);
    }

    /**
     * Private field operations must bypass proxy traps and operate on the proxy object itself.
     */
    public void setPrivatePropertyDirect(PropertyKey key, JSValue value) {
        super.setPrivatePropertyDirect(key, value);
    }

    /**
     * Override setPrototype to intercept Object.setPrototypeOf(). ES2020 9.5.2 [[SetPrototypeOf]]
     */
    @Override
    public void setPrototype(JSObject proto) {
        JSContext executionContext = resolveExecutionContext(null);
        boolean success = setPrototypeWithResult(proto);
        if (executionContext.hasPendingException()) {
            return;
        }
        if (!success) {
            executionContext
                    .throwTypeError("'setPrototypeOf' on proxy: trap returned falsish for property 'undefined'");
        }
    }

    public boolean setPrototypeWithResult(JSObject proto) {
        JSContext executionContext = resolveExecutionContext(null);
        if (rejectRevoked(executionContext, "setPrototype")) {
            return false;
        }

        JSObject targetObj = (JSObject) target;

        JSValue trapFunc = getCallableTrap("setPrototypeOf", executionContext);
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
        JSValue result = callCallableTrap(executionContext, executionContext, trapFunc, args);

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
        return PropertyDescriptor.fromDescriptorObject(resolveExecutionContext(null), obj, false);
    }

    @Override
    public String toString() {
        return "[object Proxy]";
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
        return context.rejectTypeError("trap is not a function");
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
}
