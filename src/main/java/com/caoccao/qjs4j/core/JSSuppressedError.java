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
     * Create a SuppressedError with a message.
     */
    public JSSuppressedError(JSContext context, String message) {
        super(context, message);
    }

    public static JSValue create(JSContext context, JSValue... args) {
        // SuppressedError(error, suppressed, message)
        // Step 3: If message is not undefined, convert to string and create "message" property
        boolean hasMessage = args.length > 2 && !(args[2] instanceof JSUndefined);
        String messageStr = null;
        if (hasMessage) {
            messageStr = JSTypeConversions.toString(context, args[2]).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        // Create the error object without message (we'll add it manually in the right order)
        JSSuppressedError jsSuppressedError = context.createJSSuppressedError("");

        // Per spec: property creation order is message, error, suppressed
        // Step 3b: CreateNonEnumerableDataPropertyOrThrow(O, "message", messageString)
        if (hasMessage) {
            jsSuppressedError.defineProperty(PropertyKey.MESSAGE,
                    PropertyDescriptor.dataDescriptor(new JSString(messageStr), PropertyDescriptor.DataState.ConfigurableWritable));
        }

        // Step 4: CreateNonEnumerableDataPropertyOrThrow(O, "error", error)
        JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        jsSuppressedError.defineProperty(PropertyKey.ERROR,
                PropertyDescriptor.dataDescriptor(error, PropertyDescriptor.DataState.ConfigurableWritable));

        // Step 5: CreateNonEnumerableDataPropertyOrThrow(O, "suppressed", suppressed)
        JSValue suppressed = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        jsSuppressedError.defineProperty(PropertyKey.SUPPRESSED,
                PropertyDescriptor.dataDescriptor(suppressed, PropertyDescriptor.DataState.ConfigurableWritable));

        // InstallErrorCause(O, options)
        if (args.length > 3) {
            if (!JSError.installErrorCause(context, jsSuppressedError, args[3])) {
                return JSUndefined.INSTANCE;
            }
        }

        return jsSuppressedError;
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        // Prototype is a plain object per ES spec
        JSObject errorPrototype = new JSObject();
        context.transferPrototype(errorPrototype, JSError.NAME);

        // Properties: writable, non-enumerable, configurable
        errorPrototype.defineProperty(PropertyKey.fromString("name"), new JSString(NAME), PropertyDescriptor.DataState.ConfigurableWritable);
        errorPrototype.defineProperty(PropertyKey.fromString("message"), new JSString(""), PropertyDescriptor.DataState.ConfigurableWritable);

        // SuppressedError(error, suppressed, message)
        int length = 3;

        JSNativeFunction errorConstructor = new JSNativeFunction(
                NAME,
                length,
                (childContext, thisObj, childArgs) -> create(childContext, childArgs),
                true);
        errorConstructor.defineProperty(PropertyKey.fromString("prototype"), errorPrototype, PropertyDescriptor.DataState.None);

        // Set constructor property on prototype (writable, non-enumerable, configurable)
        errorPrototype.defineProperty(PropertyKey.fromString("constructor"), errorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        return errorConstructor;
    }

    /**
     * Get the main error.
     */
    public JSValue getError() {
        return get(PropertyKey.ERROR);
    }

    @Override
    public String getErrorName() {
        return NAME;
    }

    /**
     * Get the suppressed error.
     */
    public JSValue getSuppressed() {
        return get(PropertyKey.SUPPRESSED);
    }
}
