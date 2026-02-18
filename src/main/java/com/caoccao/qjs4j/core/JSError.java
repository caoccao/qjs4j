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
     * Create an Error with the default name 'Error'.
     */
    public JSError(JSContext context) {
        this(context, "");
    }

    /**
     * Create an Error with a message.
     */
    public JSError(JSContext context, String message) {
        this(context, NAME, message);
    }

    /**
     * Create an Error with a specific name and message.
     */
    public JSError(JSContext context, String name, String message) {
        super();
        this.context = context;
        set(PropertyKey.NAME, new JSString(name));
        set(PropertyKey.MESSAGE, new JSString(message));
    }

    public static JSObject create(JSContext context, JSValue... args) {
        String message = "";
        if (args.length > 0 && !args[0].isUndefined()) {
            message = JSTypeConversions.toString(context, args[0]).value();
        }
        return context.createJSError(message);
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        // Create Error prototype using the proper error class
        JSError errorPrototype = new JSError(context);
        // Error.prototype.[[Prototype]] = Object.prototype (ES2024 20.5.3)
        context.transferPrototype(errorPrototype, JSObject.NAME);

        errorPrototype.set(PropertyKey.TO_STRING, new JSNativeFunction("toString", 0, JSError::errorToString));

        // Standard Error(message)
        int length = 1;

        // Create Error constructor as a function (following QuickJS pattern)
        // QuickJS uses JS_NewCConstructor for error constructors
        JSNativeFunction errorConstructor = new JSNativeFunction(
                NAME,
                length,
                (childContext, thisObj, childArgs) -> {
                    // The VM has already created thisObj with the correct prototype
                    // We just need to initialize the error properties on thisObj
                    if (!(thisObj instanceof JSObject obj)) {
                        return JSUndefined.INSTANCE;
                    }

                    // Set name property
                    obj.set(PropertyKey.NAME, new JSString(NAME));

                    // Standard error: new Error(message, options)
                    // Step 3: If message is not undefined, CreateMethodProperty(O, "message", ToString(message))
                    if (childArgs.length > 0 && !(childArgs[0] instanceof JSUndefined)) {
                        String message = JSTypeConversions.toString(childContext, childArgs[0]).value();
                        obj.defineProperty(PropertyKey.MESSAGE,
                                PropertyDescriptor.dataDescriptor(new JSString(message), true, false, true));
                    }

                    // InstallErrorCause(O, options)
                    if (childArgs.length > 1) {
                        installErrorCause(obj, childArgs[1]);
                    }

                    // Return undefined to use the thisObj created by the VM
                    return JSUndefined.INSTANCE;
                },
                true);
        errorConstructor.set(PropertyKey.PROTOTYPE, errorPrototype);

        // Set constructor property on prototype
        errorPrototype.set(PropertyKey.CONSTRUCTOR, errorConstructor);

        return errorConstructor;
    }

    public static JSValue errorToString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject error)) {
            return new JSString("[object Object]");
        }

        JSValue nameValue = error.get(PropertyKey.NAME);
        JSValue messageValue = error.get(PropertyKey.MESSAGE);

        String name = nameValue instanceof JSString ? ((JSString) nameValue).value() : "Error";
        String message = messageValue instanceof JSString ? ((JSString) messageValue).value() : "";

        if (message.isEmpty()) {
            return new JSString(name);
        }

        return new JSString(name + ": " + message);
    }

    /**
     * InstallErrorCause ( O, options )
     * ES2022: If options is an object with a "cause" property, install it as a
     * non-enumerable, writable, configurable own property on the error object.
     */
    public static void installErrorCause(JSObject obj, JSValue options) {
        if (options instanceof JSObject optionsObj) {
            if (optionsObj.has("cause")) {
                JSValue cause = optionsObj.get("cause");
                obj.defineProperty(PropertyKey.CAUSE,
                        PropertyDescriptor.dataDescriptor(cause, true, false, true));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        JSError jsError = (JSError) o;
        return Objects.equals(getName(), jsError.getName()) &&
                Objects.equals(getMessage(), jsError.getMessage());
    }

    /**
     * Get the error message.
     */
    public JSString getMessage() {
        return JSTypeConversions.toString(context, get(PropertyKey.MESSAGE));
    }

    /**
     * Get the error name.
     */
    public JSString getName() {
        return JSTypeConversions.toString(context, get(PropertyKey.NAME));
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
