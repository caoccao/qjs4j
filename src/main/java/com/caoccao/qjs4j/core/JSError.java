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

import java.util.Objects;

/**
 * Represents a JavaScript Error object.
 * Base class for all JavaScript error types.
 */
public sealed class JSError extends JSObject permits
        JSAggregateError, JSRangeError, JSReferenceError, JSSyntaxError, JSTypeError, JSEvalError, JSURIError, JSSuppressedError {
    public static final String NAME = "Error";
    protected final JSContext context;

    /**
     * Create an Error with a message.
     */
    public JSError(JSContext context, String message) {
        super();
        this.context = context;
        if (message != null && !message.isEmpty()) {
            defineProperty(PropertyKey.MESSAGE,
                    PropertyDescriptor.dataDescriptor(new JSString(message), PropertyDescriptor.DataState.ConfigurableWritable));
        }
    }

    public static JSValue create(JSContext context, JSValue... args) {
        String message = "";
        if (args.length > 0 && !args[0].isUndefined()) {
            message = JSTypeConversions.toString(context, args[0]).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        JSError jsError = context.createJSError(message);
        if (args.length > 1) {
            if (!installErrorCause(context, jsError, args[1])) {
                return JSUndefined.INSTANCE;
            }
        }
        return jsError;
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        // Error.prototype is a plain object (not an Error instance) per ES spec / QuickJS
        JSObject errorPrototype = new JSObject();
        // Error.prototype.[[Prototype]] = Object.prototype (ES2024 20.5.3)
        context.transferPrototype(errorPrototype, JSObject.NAME);

        // All prototype properties: writable, non-enumerable, configurable
        errorPrototype.defineProperty(PropertyKey.fromString("name"), new JSString(NAME), PropertyDescriptor.DataState.ConfigurableWritable);
        errorPrototype.defineProperty(PropertyKey.fromString("message"), new JSString(""), PropertyDescriptor.DataState.ConfigurableWritable);
        errorPrototype.defineProperty(PropertyKey.fromString("toString"),
                new JSNativeFunction("toString", 0, JSError::errorToString), PropertyDescriptor.DataState.ConfigurableWritable);

        // Standard Error(message, options) — length = 1
        JSNativeFunction errorConstructor = new JSNativeFunction(
                NAME,
                1,
                (childContext, thisObj, childArgs) -> create(childContext, childArgs),
                true);
        errorConstructor.defineProperty(PropertyKey.fromString("prototype"), errorPrototype, PropertyDescriptor.DataState.None);

        // Error.isError static method (ES2024)
        errorConstructor.defineProperty(PropertyKey.fromString("isError"),
                new JSNativeFunction("isError", 1, JSError::isError), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor property on prototype (writable, non-enumerable, configurable)
        errorPrototype.defineProperty(PropertyKey.fromString("constructor"), errorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        return errorConstructor;
    }

    /**
     * Error.prototype.toString() — ES2024 20.5.3.4
     * Following QuickJS js_error_toString.
     */
    public static JSValue errorToString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject error)) {
            return context.throwTypeError("Error.prototype.toString requires that 'this' be an Object");
        }

        JSValue nameValue = error.get(context, PropertyKey.NAME);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String name;
        if (nameValue instanceof JSUndefined) {
            name = NAME;
        } else {
            name = JSTypeConversions.toString(context, nameValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue messageValue = error.get(context, PropertyKey.MESSAGE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String message;
        if (messageValue instanceof JSUndefined) {
            message = "";
        } else {
            message = JSTypeConversions.toString(context, messageValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (name.isEmpty()) {
            return new JSString(message);
        }
        if (message.isEmpty()) {
            return new JSString(name);
        }
        return new JSString(name + ": " + message);
    }

    /**
     * InstallErrorCause ( O, options )
     * ES2022: If options is an object with a "cause" property, install it as a
     * non-enumerable, writable, configurable own property on the error object.
     * Returns true on normal completion, false on abrupt completion (exception pending in context).
     */
    public static boolean installErrorCause(JSContext context, JSObject obj, JSValue options) {
        if (options instanceof JSObject optionsObj) {
            if (optionsObj.has("cause")) {
                JSValue cause = optionsObj.get(context, PropertyKey.CAUSE);
                if (context.hasPendingException()) {
                    return false;
                }
                obj.defineProperty(PropertyKey.CAUSE,
                        PropertyDescriptor.dataDescriptor(cause, PropertyDescriptor.DataState.ConfigurableWritable));
            }
        }
        return true;
    }

    /**
     * Error.isError(arg) — ES2024.
     * Returns true if arg is an error object (has [[ErrorData]] internal slot).
     * Following QuickJS js_error_isError which checks class_id == JS_CLASS_ERROR.
     */
    public static JSValue isError(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(arg instanceof JSError);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JSError jsError = (JSError) o;
        return Objects.equals(getName(), jsError.getName()) &&
                Objects.equals(getMessage(), jsError.getMessage());
    }

    public String getErrorName() {
        return NAME;
    }

    /**
     * Get the error message.
     */
    public JSString getMessage() {
        JSValue msgValue = get(PropertyKey.MESSAGE);
        if (msgValue.isUndefined()) {
            return new JSString("");
        }
        return JSTypeConversions.toString(context, msgValue);
    }

    /**
     * Get the error name.
     */
    public JSString getName() {
        JSValue nameValue = get(PropertyKey.NAME);
        if (nameValue.isUndefined()) {
            return new JSString(getErrorName());
        }
        return JSTypeConversions.toString(context, nameValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getMessage());
    }

    @Override
    public String toString() {
        String name = getName().value();
        String message = getMessage().value();
        if (message == null || message.isEmpty()) {
            return name;
        }
        return name + ": " + message;
    }
}
