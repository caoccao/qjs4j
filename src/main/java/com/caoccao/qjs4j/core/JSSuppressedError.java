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
        set("error", error);
        set("suppressed", suppressed);
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
        JSObject jsObject = new JSSuppressedError(context, error, suppressed, message);
        context.getGlobalObject().get(NAME).asObject().ifPresent(jsObject::transferPrototypeFrom);
        return jsObject;
    }

    /**
     * Get the main error.
     */
    public JSValue getError() {
        return get("error");
    }

    /**
     * Get the suppressed error.
     */
    public JSValue getSuppressed() {
        return get("suppressed");
    }
}
