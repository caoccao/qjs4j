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

/**
 * Represents a JavaScript Proxy object.
 * Proxies wrap a target object and intercept operations on it via handler traps.
 * Based on ES2020 Proxy specification (simplified).
 */
public final class JSProxy extends JSObject {
    private final JSContext context;
    private final JSObject handler;
    private final boolean isFunc;
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
        this.isFunc = JSTypeChecking.isFunction(target);
    }

    /**
     * Check if the proxy is revoked and throw TypeError if so.
     */
    private void checkRevoked() {
        if (revoked) {
            throw new JSException(context.throwError("TypeError", "Cannot perform operation on a revoked proxy"));
        }
    }

    /**
     * Override delete to intercept property deletion.
     */
    @Override
    public boolean delete(PropertyKey key) {
        checkRevoked();

        // Check if handler has 'deleteProperty' trap
        JSValue deleteTrap = handler.get("deleteProperty");
        if (deleteTrap instanceof JSFunction deleteTrapFunc) {
            // Call the trap: handler.deleteProperty(target, property)
            JSValue[] args = new JSValue[]{
                    target,
                    key.isString() ? new JSString(key.asString()) : key.asSymbol()
            };
            JSValue result = deleteTrapFunc.call(context, handler, args);
            return JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        }

        // No trap, forward to target
        return getTargetAsObject().delete(key);
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
        checkRevoked();

        // Check if handler has 'get' trap
        JSValue getTrap = handler.get("get");
        if (getTrap instanceof JSFunction getTrapFunc) {
            // Call the trap: handler.get(target, property, receiver)
            JSValue[] args = new JSValue[]{
                    target,
                    key.isString() ? new JSString(key.asString()) : key.asSymbol(),
                    this
            };
            return getTrapFunc.call(context, handler, args);
        }

        // No trap, forward to target
        return getTargetAsObject().get(key);
    }

    public JSObject getHandler() {
        return handler;
    }

    public JSValue getTarget() {
        return target;
    }

    /**
     * Override has to intercept 'in' operator.
     */
    @Override
    public boolean has(PropertyKey key) {
        checkRevoked();

        // Check if handler has 'has' trap
        JSValue hasTrap = handler.get("has");
        if (hasTrap instanceof JSFunction hasTrapFunc) {
            // Call the trap: handler.has(target, property)
            JSValue[] args = new JSValue[]{
                    target,
                    key.isString() ? new JSString(key.asString()) : key.asSymbol()
            };
            JSValue result = hasTrapFunc.call(context, handler, args);
            return JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        }

        // No trap, forward to target
        return getTargetAsObject().has(key);
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
     * Override ownPropertyKeys to intercept Object.keys(), etc.
     */
    @Override
    public PropertyKey[] ownPropertyKeys() {
        checkRevoked();

        // Check if handler has 'ownKeys' trap
        JSValue ownKeysTrap = handler.get("ownKeys");
        if (ownKeysTrap instanceof JSFunction ownKeysTrapFunc) {
            // Call the trap: handler.ownKeys(target)
            JSValue[] args = new JSValue[]{target};
            JSValue result = ownKeysTrapFunc.call(context, handler, args);

            // Convert result to PropertyKey array
            if (result instanceof JSArray resultArray) {
                int length = (int) resultArray.getLength();
                PropertyKey[] keys = new PropertyKey[length];
                for (int i = 0; i < length; i++) {
                    JSValue keyValue = resultArray.get(i);
                    if (keyValue instanceof JSString str) {
                        keys[i] = PropertyKey.fromString(str.value());
                    } else if (keyValue instanceof JSSymbol sym) {
                        keys[i] = PropertyKey.fromSymbol(sym);
                    }
                }
                return keys;
            }
        }

        // No trap, forward to target
        return getTargetAsObject().ownPropertyKeys();
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
     * Helper to get a trap method from the handler.
     * Returns null if handler is null/undefined.
     * Following QuickJS get_proxy_method().
     */
    private JSValue getTrapMethod(String trapName) {
        checkRevoked();
        JSValue method = handler.get(trapName);
        // Treat null as undefined
        if (method instanceof JSNull) {
            return JSUndefined.INSTANCE;
        }
        return method;
    }

    /**
     * Helper to get target as JSObject.
     * In JavaScript, functions are objects, but in this implementation they're separate.
     * For object operations, we need the target to be a JSObject.
     */
    private JSObject getTargetAsObject() {
        if (target instanceof JSObject obj) {
            return obj;
        }
        throw new JSException(context.throwError("TypeError",
            "Proxy target must be an object for this operation"));
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
        checkRevoked();

        // Check if handler has 'set' trap
        JSValue setTrap = handler.get("set");
        if (setTrap instanceof JSFunction setTrapFunc) {
            // Call the trap: handler.set(target, property, value, receiver)
            JSValue[] args = new JSValue[]{
                    target,
                    key.isString() ? new JSString(key.asString()) : key.asSymbol(),
                    value,
                    this
            };
            setTrapFunc.call(context, handler, args);
            return;
        }

        // No trap, forward to target
        getTargetAsObject().set(key, value);
    }

    /**
     * Override getPrototype to intercept Object.getPrototypeOf().
     * ES2020 9.5.1 [[GetPrototypeOf]]
     */
    @Override
    public JSObject getPrototype() {
        checkRevoked();
        JSObject targetObj = getTargetAsObject();

        JSValue trap = getTrapMethod("getPrototypeOf");
        if (trap instanceof JSUndefined) {
            return targetObj.getPrototype();
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwError("TypeError", "getPrototypeOf trap must be a function"));
        }

        // Call trap: handler.getPrototypeOf(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = trapFunc.call(context, handler, args);

        // Validate result is null or object
        if (!(result instanceof JSNull) && !(result instanceof JSObject)) {
            throw new JSException(context.throwError("TypeError",
                "proxy getPrototypeOf handler must return an object or null"));
        }

        // Check invariants if target is not extensible
        if (!targetObj.isExtensible()) {
            JSObject targetProto = targetObj.getPrototype();
            if (!sameValue(targetProto, result)) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent getPrototypeOf"));
            }
        }

        return (result instanceof JSNull) ? null : (JSObject) result;
    }

    /**
     * Override setPrototype to intercept Object.setPrototypeOf().
     * ES2020 9.5.2 [[SetPrototypeOf]]
     */
    @Override
    public void setPrototype(JSObject proto) {
        checkRevoked();
        JSObject targetObj = getTargetAsObject();

        JSValue trap = getTrapMethod("setPrototypeOf");
        if (trap instanceof JSUndefined) {
            targetObj.setPrototype(proto);
            return;
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwError("TypeError", "setPrototypeOf trap must be a function"));
        }

        // Call trap: handler.setPrototypeOf(target, proto)
        JSValue[] args = new JSValue[]{target, proto == null ? JSNull.INSTANCE : proto};
        JSValue result = trapFunc.call(context, handler, args);

        // Convert to boolean
        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (boolResult) {
            // Check invariants if target is not extensible
            if (!targetObj.isExtensible()) {
                JSObject targetProto = targetObj.getPrototype();
                if (!sameValue(targetProto, proto)) {
                    throw new JSException(context.throwError("TypeError",
                        "proxy: inconsistent setPrototypeOf"));
                }
            }
        } else {
            throw new JSException(context.throwError("TypeError", "setPrototypeOf returned false"));
        }
    }

    /**
     * Override isExtensible to intercept Object.isExtensible().
     * ES2020 9.5.3 [[IsExtensible]]
     */
    @Override
    public boolean isExtensible() {
        checkRevoked();
        JSObject targetObj = getTargetAsObject();

        JSValue trap = getTrapMethod("isExtensible");
        if (trap instanceof JSUndefined) {
            return targetObj.isExtensible();
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwError("TypeError", "isExtensible trap must be a function"));
        }

        // Call trap: handler.isExtensible(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = trapFunc.call(context, handler, args);

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        boolean targetExtensible = targetObj.isExtensible();

        // Invariant: result must match target's extensibility
        if (boolResult != targetExtensible) {
            throw new JSException(context.throwError("TypeError",
                "proxy: inconsistent isExtensible"));
        }

        return boolResult;
    }

    /**
     * Override preventExtensions to intercept Object.preventExtensions().
     * ES2020 9.5.4 [[PreventExtensions]]
     */
    @Override
    public void preventExtensions() {
        checkRevoked();
        JSObject targetObj = getTargetAsObject();

        JSValue trap = getTrapMethod("preventExtensions");
        if (trap instanceof JSUndefined) {
            targetObj.preventExtensions();
            return;
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwError("TypeError", "preventExtensions trap must be a function"));
        }

        // Call trap: handler.preventExtensions(target)
        JSValue[] args = new JSValue[]{target};
        JSValue result = trapFunc.call(context, handler, args);

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (boolResult) {
            // Invariant: if trap returns true, target must be non-extensible
            if (targetObj.isExtensible()) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent preventExtensions"));
            }
        } else {
            throw new JSException(context.throwError("TypeError", "preventExtensions returned false"));
        }
    }

    /**
     * Override getOwnPropertyDescriptor to intercept Object.getOwnPropertyDescriptor().
     * ES2020 9.5.5 [[GetOwnProperty]]
     */
    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        checkRevoked();
        JSObject targetObj = getTargetAsObject();

        JSValue trap = getTrapMethod("getOwnPropertyDescriptor");
        if (trap instanceof JSUndefined) {
            return targetObj.getOwnPropertyDescriptor(key);
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwError("TypeError",
                "getOwnPropertyDescriptor trap must be a function"));
        }

        // Call trap: handler.getOwnPropertyDescriptor(target, property)
        JSValue[] args = new JSValue[]{
            target,
            key.isString() ? new JSString(key.asString()) : key.asSymbol()
        };
        JSValue trapResult = trapFunc.call(context, handler, args);

        // Result must be undefined or object
        if (!(trapResult instanceof JSUndefined) && !(trapResult instanceof JSObject)) {
            throw new JSException(context.throwError("TypeError",
                "getOwnPropertyDescriptor trap must return an object or undefined"));
        }

        PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
        boolean targetExtensible = targetObj.isExtensible();

        if (trapResult instanceof JSUndefined) {
            // Invariant: cannot return undefined for non-configurable property
            if (targetDesc != null && !targetDesc.isConfigurable()) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent getOwnPropertyDescriptor"));
            }
            // Invariant: cannot return undefined if target is non-extensible and has property
            if (!targetExtensible && targetDesc != null) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent getOwnPropertyDescriptor"));
            }
            return null;
        }

        // Convert result to PropertyDescriptor
        PropertyDescriptor resultDesc = toPropertyDescriptor((JSObject) trapResult);

        // Validate against target descriptor
        if (targetDesc != null) {
            if (!isCompatiblePropertyDescriptor(targetExtensible, resultDesc, targetDesc)) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent getOwnPropertyDescriptor"));
            }
            // Invariant: cannot return configurable for non-configurable property
            if (!targetDesc.isConfigurable() && resultDesc.isConfigurable()) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent getOwnPropertyDescriptor"));
            }
        } else {
            // Invariant: cannot return non-configurable if property doesn't exist on non-extensible target
            if (!targetExtensible) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent getOwnPropertyDescriptor"));
            }
        }

        return resultDesc;
    }

    /**
     * Override defineProperty to intercept Object.defineProperty().
     * ES2020 9.5.6 [[DefineOwnProperty]]
     */
    @Override
    public void defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        checkRevoked();
        JSObject targetObj = getTargetAsObject();

        JSValue trap = getTrapMethod("defineProperty");
        if (trap instanceof JSUndefined) {
            targetObj.defineProperty(key, descriptor);
            return;
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwError("TypeError", "defineProperty trap must be a function"));
        }

        // Convert descriptor to object
        JSObject descObj = fromPropertyDescriptor(descriptor);

        // Call trap: handler.defineProperty(target, property, descriptor)
        JSValue[] args = new JSValue[]{
            target,
            key.isString() ? new JSString(key.asString()) : key.asSymbol(),
            descObj
        };
        JSValue result = trapFunc.call(context, handler, args);

        boolean boolResult = JSTypeConversions.toBoolean(result) == JSBoolean.TRUE;
        if (!boolResult) {
            throw new JSException(context.throwError("TypeError", "defineProperty returned false"));
        }

        // Validate invariants
        PropertyDescriptor targetDesc = targetObj.getOwnPropertyDescriptor(key);
        boolean targetExtensible = targetObj.isExtensible();

        boolean settingConfigFalse = descriptor.hasConfigurable() && !descriptor.isConfigurable();

        if (targetDesc == null) {
            // Property doesn't exist on target
            if (!targetExtensible || settingConfigFalse) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent defineProperty"));
            }
        } else {
            // Property exists on target
            if (!isCompatiblePropertyDescriptor(targetExtensible, descriptor, targetDesc)) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent defineProperty"));
            }
            if (targetDesc.isConfigurable() && settingConfigFalse) {
                throw new JSException(context.throwError("TypeError",
                    "proxy: inconsistent defineProperty"));
            }
        }
    }

    /**
     * Apply trap for function proxies.
     * ES2020 9.5.13 [[Call]]
     */
    public JSValue apply(JSContext ctx, JSValue thisArg, JSValue[] args) {
        checkRevoked();

        if (!isFunc) {
            throw new JSException(context.throwError("TypeError", "not a function"));
        }

        JSValue trap = getTrapMethod("apply");
        if (trap instanceof JSUndefined) {
            if (target instanceof JSFunction targetFunc) {
                return targetFunc.call(ctx, thisArg, args);
            }
            throw new JSException(context.throwError("TypeError", "target is not callable"));
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwError("TypeError", "apply trap must be a function"));
        }

        // Create arguments array
        JSArray argArray = createArgumentsArray(args);

        // Call trap: handler.apply(target, thisArg, argumentsList)
        JSValue[] trapArgs = new JSValue[]{target, thisArg, argArray};
        return trapFunc.call(ctx, handler, trapArgs);
    }

    /**
     * Construct trap for constructor proxies.
     * ES2020 9.5.14 [[Construct]]
     */
    public JSValue construct(JSContext ctx, JSValue[] args, JSValue newTarget) {
        checkRevoked();

        if (!JSTypeChecking.isConstructor(target)) {
            throw new JSException(context.throwError("TypeError", "target is not a constructor"));
        }

        JSValue trap = getTrapMethod("construct");
        if (trap instanceof JSUndefined) {
            // Forward to target constructor
            if (target instanceof JSFunction targetFunc) {
                // Would need constructor invocation support
                throw new JSException(context.throwError("TypeError",
                    "construct forwarding not yet implemented"));
            }
            throw new JSException(context.throwError("TypeError", "target is not a constructor"));
        }

        if (!(trap instanceof JSFunction trapFunc)) {
            throw new JSException(context.throwError("TypeError", "construct trap must be a function"));
        }

        // Create arguments array
        JSArray argArray = createArgumentsArray(args);

        // Call trap: handler.construct(target, argumentsList, newTarget)
        JSValue[] trapArgs = new JSValue[]{target, argArray, newTarget};
        JSValue result = trapFunc.call(ctx, handler, trapArgs);

        // Result must be an object
        if (!(result instanceof JSObject)) {
            throw new JSException(context.throwError("TypeError",
                "construct trap must return an object"));
        }

        return result;
    }

    /**
     * Helper to create an arguments array from JSValue[].
     */
    private JSArray createArgumentsArray(JSValue[] args) {
        JSArray array = new JSArray();
        for (int i = 0; i < args.length; i++) {
            array.set(i, args[i]);
        }
        return array;
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
            if (!current.isConfigurable()) {
                return false;
            }
            return true;
        }

        // Both are data descriptors
        if (!descIsAccessor) {
            if (!current.isConfigurable() && !current.isWritable()) {
                if (desc.hasWritable() && desc.isWritable()) {
                    return false;
                }
                if (desc.hasValue() && !sameValue(desc.getValue(), current.getValue())) {
                    return false;
                }
            }
        } else {
            // Both are accessor descriptors
            if (!current.isConfigurable()) {
                if (desc.hasGetter() && !sameValue(desc.getGetter(), current.getGetter())) {
                    return false;
                }
                if (desc.hasSetter() && !sameValue(desc.getSetter(), current.getSetter())) {
                    return false;
                }
            }
        }

        return true;
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

    @Override
    public String toString() {
        return "[object Proxy]";
    }
}
