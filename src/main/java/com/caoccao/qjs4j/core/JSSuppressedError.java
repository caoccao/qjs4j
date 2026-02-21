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
 * Represents a JavaScript SuppressedError object.
 * SuppressedError is used when an error is suppressed in favor of another error,
 * typically in cleanup/dispose operations.
 */
public final class JSSuppressedError extends JSError {

    public static final String NAME = "SuppressedError";

    /**
     * Create a SuppressedError with default values.
     */
    public JSSuppressedError(JSContext context) {
        this(context, JSUndefined.INSTANCE, JSUndefined.INSTANCE, "");
    }

    /**
     * Create a SuppressedError with a message.
     */
    public JSSuppressedError(JSContext context, String message) {
        this(context, JSUndefined.INSTANCE, JSUndefined.INSTANCE, message);
    }

    /**
     * Create a SuppressedError with error, suppressed error, and message.
     *
     * @param context    The JavaScript context
     * @param error      The main (primary) error
     * @param suppressed The error that was suppressed during cleanup/dispose
     * @param message    Optional custom message
     */
    public JSSuppressedError(JSContext context, JSValue error, JSValue suppressed, String message) {
        super(context, NAME, message);
        set(PropertyKey.ERROR, error);
        set(PropertyKey.SUPPRESSED, suppressed);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // SuppressedError has special constructor: new SuppressedError(error, suppressed, message)
        JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue suppressed = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        String message = "";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            JSString messageStr = JSTypeConversions.toString(context, args[2]);
            message = messageStr.value();
        }
        return context.createJSSuppressedError(error, suppressed, message);
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        // Create Error prototype using the proper error class
        JSError errorPrototype = new JSSuppressedError(context);
        // SuppressedError.prototype.[[Prototype]] = Error.prototype (ES2024 20.5.5.1)
        context.transferPrototype(errorPrototype, JSError.NAME);

        errorPrototype.set(PropertyKey.TO_STRING, new JSNativeFunction("toString", 0, JSError::errorToString));

        // SuppressedError(error, suppressed, message)
        int length = 3;

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

                    // SuppressedError: new SuppressedError(error, suppressed, message, options)
                    JSValue error = childArgs.length > 0 ? childArgs[0] : JSUndefined.INSTANCE;
                    JSValue suppressed = childArgs.length > 1 ? childArgs[1] : JSUndefined.INSTANCE;
                    obj.set(PropertyKey.ERROR, error);
                    obj.set(PropertyKey.SUPPRESSED, suppressed);

                    // If message is not undefined, CreateMethodProperty(O, "message", ToString(message))
                    if (childArgs.length > 2 && !(childArgs[2] instanceof JSUndefined)) {
                        String message = JSTypeConversions.toString(childContext, childArgs[2]).value();
                        obj.defineProperty(PropertyKey.MESSAGE,
                                PropertyDescriptor.dataDescriptor(new JSString(message), true, false, true));
                    }

                    // InstallErrorCause(O, options)
                    if (childArgs.length > 3) {
                        JSError.installErrorCause(obj, childArgs[3]);
                    }

                    // Return undefined to use the thisObj created by the VM
                    return JSUndefined.INSTANCE;
                },
                true);
        errorConstructor.definePropertyReadonlyNonConfigurable("prototype", errorPrototype);

        // Set constructor property on prototype
        errorPrototype.set(PropertyKey.CONSTRUCTOR, errorConstructor);

        return errorConstructor;
    }

    /**
     * Get the main error.
     */
    public JSValue getError() {
        return get(PropertyKey.ERROR);
    }

    /**
     * Get the suppressed error.
     */
    public JSValue getSuppressed() {
        return get(PropertyKey.SUPPRESSED);
    }
}
