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
 * Represents a JavaScript ReferenceError object.
 */
public final class JSReferenceError extends JSError {

    public static final String NAME = "ReferenceError";

    /**
     * Create a ReferenceError with an empty message.
     */
    public JSReferenceError(JSContext context) {
        this(context, "");
    }

    /**
     * Create a ReferenceError with a message.
     */
    public JSReferenceError(JSContext context, String message) {
        super(context, NAME, message);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        String message = "";
        if (args.length > 0 && !args[0].isUndefined()) {
            message = JSTypeConversions.toString(context, args[0]).value();
        }
        JSObject jsObject = new JSReferenceError(context, message);
        context.transferPrototype(jsObject, NAME);
        return jsObject;
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        // Create Error prototype using the proper error class
        JSError errorPrototype = new JSReferenceError(context);
        // ReferenceError.prototype.[[Prototype]] = Error.prototype (ES2024 20.5.5.1)
        context.transferPrototype(errorPrototype, JSError.NAME);

        errorPrototype.set("toString", new JSNativeFunction("toString", 0, JSError::errorToString));

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
                    obj.set("name", new JSString(NAME));

                    // Standard error: new Error(message, options)
                    if (childArgs.length > 0 && !(childArgs[0] instanceof JSUndefined)) {
                        String message = JSTypeConversions.toString(childContext, childArgs[0]).value();
                        obj.defineProperty(PropertyKey.MESSAGE,
                                PropertyDescriptor.dataDescriptor(new JSString(message), true, false, true));
                    }

                    // InstallErrorCause(O, options)
                    if (childArgs.length > 1) {
                        JSError.installErrorCause(obj, childArgs[1]);
                    }

                    // Return undefined to use the thisObj created by the VM
                    return JSUndefined.INSTANCE;
                },
                true);
        errorConstructor.set("prototype", errorPrototype);

        // Set constructor property on prototype
        errorPrototype.set("constructor", errorConstructor);

        return errorConstructor;
    }
}
