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
 * Represents a JavaScript AggregateError object.
 * AggregateError represents an error when multiple errors need to be wrapped in a single error.
 */
public final class JSAggregateError extends JSError {

    public static final String NAME = "AggregateError";

    /**
     * Create an AggregateError with a message.
     */
    public JSAggregateError(JSContext context, String message) {
        super(context, message);
    }

    public static JSValue create(JSContext context, JSValue... args) {
        String message = "";

        // Step 3: If message is not undefined, CreateMethodProperty(O, "message", ToString(message))
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            message = JSTypeConversions.toString(context, args[1]).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSAggregateError jsAggregateError = context.createJSAggregateError(message);

        // Step 4: InstallErrorCause(O, options)
        if (args.length > 2) {
            if (!JSError.installErrorCause(context, jsAggregateError, args[2])) {
                return JSUndefined.INSTANCE;
            }
        }

        // Step 5: Let errorsList be ? IterableToList(errors).
        JSValue errorsArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSArray errorsList = JSIteratorHelper.iterableToList(context, errorsArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        jsAggregateError.defineProperty(PropertyKey.ERRORS,
                PropertyDescriptor.dataDescriptor(errorsList, PropertyDescriptor.DataState.ConfigurableWritable));

        return jsAggregateError;
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        // Create AggregateError.prototype (not an error instance per spec)
        JSObject errorPrototype = new JSObject(context);
        context.transferPrototype(errorPrototype, JSError.NAME);
        errorPrototype.defineProperty(PropertyKey.fromString("name"),
                PropertyDescriptor.dataDescriptor(new JSString(NAME), PropertyDescriptor.DataState.ConfigurableWritable));
        errorPrototype.defineProperty(PropertyKey.MESSAGE,
                PropertyDescriptor.dataDescriptor(new JSString(""), PropertyDescriptor.DataState.ConfigurableWritable));

        // AggregateError(errors, message)
        int length = 2;

        JSNativeFunction errorConstructor = new JSNativeFunction(context, NAME,
                length,
                (childContext, thisObj, childArgs) -> create(childContext, childArgs),
                true);
        errorConstructor.defineProperty(PropertyKey.fromString("prototype"), errorPrototype, PropertyDescriptor.DataState.None);

        // AggregateError.[[Prototype]] = Error (the constructor inherits from Error)
        JSValue errorCtor = context.getGlobalObject().get(JSError.NAME);
        if (errorCtor instanceof JSObject errorCtorObj) {
            errorConstructor.setPrototype(errorCtorObj);
        }

        // Set constructor property on prototype (non-enumerable per spec)
        errorPrototype.defineProperty(PropertyKey.CONSTRUCTOR,
                PropertyDescriptor.dataDescriptor(errorConstructor, PropertyDescriptor.DataState.ConfigurableWritable));

        return errorConstructor;
    }

    @Override
    public String getErrorName() {
        return NAME;
    }

    /**
     * Get the errors array.
     */
    public JSArray getErrors() {
        return get("errors").asArray().orElseGet(() -> new JSArray(context));
    }
}
