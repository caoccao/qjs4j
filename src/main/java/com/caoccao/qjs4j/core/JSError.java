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
        set("name", new JSString(name));
        set("message", new JSString(message));
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
        return JSTypeConversions.toString(context, get("message"));
    }

    /**
     * Get the error name.
     */
    public JSString getName() {
        return JSTypeConversions.toString(context, get("name"));
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
