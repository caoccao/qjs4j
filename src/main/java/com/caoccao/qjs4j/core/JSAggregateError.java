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
     * Create an AggregateError with an empty errors array.
     */
    public JSAggregateError(JSContext context) {
        this(context, context.createJSArray(), "");
    }

    /**
     * Create an AggregateError with a message.
     */
    public JSAggregateError(JSContext context, String message) {
        this(context, context.createJSArray(), message);
    }

    /**
     * Create an AggregateError with errors and a message.
     */
    public JSAggregateError(JSContext context, JSArray errors, String message) {
        super(context, NAME, message);
        set("errors", errors);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        String message = "";
        if (args.length > 0 && !args[0].isUndefined()) {
            message = JSTypeConversions.toString(context, args[0]).value();
        }
        JSObject jsObject = new JSAggregateError(context, message);
        context.getGlobalObject().get(NAME).asObject().ifPresent(jsObject::transferPrototypeFrom);
        return jsObject;
    }

    /**
     * Get the errors array.
     */
    public JSArray getErrors() {
        return get("errors").asArray().orElseGet(JSArray::new);
    }
}
