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
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

/**
 * Represents a native (Java-implemented) JavaScript function.
 */
public final class JSNativeFunction extends JSFunction {
    public static final String NAME = JSFunction.NAME;
    private final JSNativeCallback callback;
    private final boolean isConstructor;
    private final int length;
    private final String name;
    private final boolean requiresNew;

    public JSNativeFunction(JSContext context, String name, int length, JSNativeCallback callback) {
        this(context, name, length, callback, false, false);
    }

    public JSNativeFunction(JSContext context, String name, int length, JSNativeCallback callback,
            boolean isConstructor) {
        this(context, name, length, callback, isConstructor, false);
    }

    public JSNativeFunction(JSContext context, String name, int length, JSNativeCallback callback,
            boolean isConstructor, boolean requiresNew) {
        super(context); // Initialize as JSObject
        this.name = name;
        this.length = length;
        this.callback = callback;
        this.isConstructor = isConstructor;
        this.requiresNew = requiresNew;

        initializeNameAndLength(name == null ? "" : name, JSNumber.of(length));

        // Native functions have a prototype property only if they are constructors
        if (isConstructor) {
            initializeConstructorPrototype(PropertyDescriptor.DataState.ConfigurableWritable);
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
        context.getVirtualMachine().checkExecutionInterrupt();
        JSContext callbackContext = getRealmContext();
        try {
            JSValue result = callback.call(callbackContext, thisArg, args);
            if (callbackContext != context && callbackContext.hasPendingException()) {
                context.setPendingException(callbackContext.getPendingException());
                callbackContext.clearPendingException();
            }
            return result;
        } catch (JSException | JSVirtualMachineException exception) {
            if (callbackContext != context && callbackContext.hasPendingException()) {
                context.setPendingException(callbackContext.getPendingException());
                callbackContext.clearPendingException();
            }
            throw exception;
        }
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

}
