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
 * Represents a native (Java-implemented) JavaScript function.
 */
public final class JSNativeFunction extends JSFunction {
    public static final String NAME = JSFunction.NAME;
    private final NativeCallback callback;
    private final boolean isConstructor;
    private final int length;
    private final String name;
    private final boolean requiresNew;

    public JSNativeFunction(JSContext context, String name, int length, NativeCallback callback) {
        this(context, name, length, callback, false, false);
    }

    public JSNativeFunction(JSContext context, String name, int length, NativeCallback callback, boolean isConstructor) {
        this(context, name, length, callback, isConstructor, false);
    }

    public JSNativeFunction(JSContext context, String name, int length, NativeCallback callback, boolean isConstructor, boolean requiresNew) {
        super(context); // Initialize as JSObject
        this.name = name;
        this.length = length;
        this.callback = callback;
        this.isConstructor = isConstructor;
        this.requiresNew = requiresNew;

        // Set up function properties on the object
        // Functions are objects in JavaScript and have these standard properties
        // Per ES spec, "length" comes before "name" in property order

        // Per ECMAScript spec, the "length" property has attributes:
        // { [[Writable]]: false, [[Enumerable]]: false, [[Configurable]]: true }
        this.defineProperty(
                PropertyKey.LENGTH,
                PropertyDescriptor.dataDescriptor(
                        JSNumber.of(this.length),
                        PropertyDescriptor.DataState.Configurable
                )
        );

        // Per ECMAScript spec, the "name" property has attributes:
        // { [[Writable]]: false, [[Enumerable]]: false, [[Configurable]]: true }
        // Use empty string for name property if name is null (e.g., Function.prototype)
        this.defineProperty(
                PropertyKey.NAME,
                PropertyDescriptor.dataDescriptor(
                        new JSString(this.name != null ? this.name : ""),
                        PropertyDescriptor.DataState.Configurable
                )
        );

        // Native functions have a prototype property only if they are constructors
        if (isConstructor) {
            JSObject funcPrototype = new JSObject(context);
            funcPrototype.set(PropertyKey.CONSTRUCTOR, this);
            // Default prototype is configurable so it can be deleted (Proxy) or overridden by explicit setup
            this.defineProperty(PropertyKey.PROTOTYPE,
                    PropertyDescriptor.dataDescriptor(funcPrototype, PropertyDescriptor.DataState.ConfigurableWritable));
        }
    }

    @Override
    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        if (requiresNew && context.getConstructorNewTarget() == null) {
            String constructorName = name != null ? name : "constructor";
            String errorMessage;
            if (JSPromise.NAME.equals(constructorName)) {
                errorMessage = "Promise constructor cannot be invoked without 'new'";
            } else {
                errorMessage = "Constructor " + constructorName + " requires 'new'";
            }
            return context.throwTypeError(errorMessage);
        }
        JSContext callbackContext = getHomeContext() != null ? getHomeContext() : context;
        JSValue result = callback.call(callbackContext, thisArg, args);
        if (callbackContext != context && callbackContext.hasPendingException()) {
            context.setPendingException(callbackContext.getPendingException());
            callbackContext.clearPendingException();
        }
        return result;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public boolean requiresNew() {
        return requiresNew;
    }

    @Override
    public String toString() {
        // QuickJS/V8 native function string form is single-line.
        String displayName = (name != null) ? name : "";
        return "function " + displayName + "() { [native code] }";
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }

    @FunctionalInterface
    public interface NativeCallback {
        JSValue call(JSContext context, JSValue thisArg, JSValue[] args);
    }
}
