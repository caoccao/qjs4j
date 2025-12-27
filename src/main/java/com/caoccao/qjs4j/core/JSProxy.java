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
    private final JSObject target;
    private final JSObject handler;
    private final JSContext context;

    /**
     * Create a new Proxy.
     *
     * @param target  The target object to wrap
     * @param handler The handler object with trap functions
     * @param context The execution context
     */
    public JSProxy(JSObject target, JSObject handler, JSContext context) {
        super();
        this.target = target;
        this.handler = handler;
        this.context = context;
    }

    public JSObject getTarget() {
        return target;
    }

    public JSObject getHandler() {
        return handler;
    }

    /**
     * Override get to intercept property access.
     */
    @Override
    public JSValue get(PropertyKey key) {
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
        return target.get(key);
    }

    /**
     * Override set to intercept property assignment.
     */
    @Override
    public void set(PropertyKey key, JSValue value) {
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
        target.set(key, value);
    }

    /**
     * Override has to intercept 'in' operator.
     */
    @Override
    public boolean has(PropertyKey key) {
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
        return target.has(key);
    }

    /**
     * Override delete to intercept property deletion.
     */
    @Override
    public boolean delete(PropertyKey key) {
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
        return target.delete(key);
    }

    /**
     * Override ownPropertyKeys to intercept Object.keys(), etc.
     */
    @Override
    public PropertyKey[] ownPropertyKeys() {
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
        return target.ownPropertyKeys();
    }

    @Override
    public String toString() {
        return "[object Proxy]";
    }
}
