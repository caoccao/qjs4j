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
 * Represents a JavaScript RangeError object.
 */
public final class JSRangeError extends JSError {

    public static final String NAME = "RangeError";

    /**
     * Create a RangeError with a message.
     */
    public JSRangeError(JSContext context, String message) {
        super(context, message);
    }

    public static JSValue create(JSContext context, JSValue... args) {
        String message = "";
        if (args.length > 0 && !args[0].isUndefined()) {
            message = JSTypeConversions.toString(context, args[0]).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        JSRangeError jsRangeError = context.createJSRangeError(message);
        if (args.length > 1) {
            if (!JSError.installErrorCause(context, jsRangeError, args[1])) {
                return JSUndefined.INSTANCE;
            }
        }
        return jsRangeError;
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        JSObject errorPrototype = new JSObject(context);
        context.transferPrototype(errorPrototype, JSError.NAME);

        errorPrototype.defineProperty(PropertyKey.fromString("name"), new JSString(NAME), PropertyDescriptor.DataState.ConfigurableWritable);
        errorPrototype.defineProperty(PropertyKey.fromString("message"), new JSString(""), PropertyDescriptor.DataState.ConfigurableWritable);

        JSNativeFunction errorConstructor = new JSNativeFunction(context, NAME,
                1,
                (childContext, thisObj, childArgs) -> create(childContext, childArgs),
                true);
        errorConstructor.defineProperty(PropertyKey.fromString("prototype"), errorPrototype, PropertyDescriptor.DataState.None);
        errorPrototype.defineProperty(PropertyKey.fromString("constructor"), errorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        return errorConstructor;
    }

    @Override
    public String getErrorName() {
        return NAME;
    }
}
