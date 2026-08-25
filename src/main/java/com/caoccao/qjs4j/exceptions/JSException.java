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

package com.caoccao.qjs4j.exceptions;

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.core.*;

/**
 * Exception thrown when JavaScript code throws an error.
 * Wraps the JavaScript error value and its source location for access from Java.
 */
public class JSException extends RuntimeException {
    private final JSValue errorValue;
    private final SourceLocation sourceLocation;

    public JSException(String name, String message) {
        this(name, message, null);
    }

    public JSException(String name, String message, Throwable cause) {
        super(name + ": " + message, cause);
        errorValue = new JSString(name + ": " + message);
        sourceLocation = null;
    }

    public JSException(JSValue errorValue) {
        this(errorValue, null);
    }

    public JSException(JSValue errorValue, Throwable cause) {
        super(formatErrorMessage(errorValue), cause);
        this.errorValue = errorValue;
        sourceLocation = errorValue instanceof JSError jsError ? jsError.getSourceLocation() : null;
    }

    /**
     * Format error message from JavaScript error object.
     * <p>
     * Own data properties only. Reading {@code name} and {@code message} through
     * {@link JSObject#get(PropertyKey)} invokes accessors, so constructing this exception ran
     * arbitrary user code at throw time — while an error was already in flight — and that code
     * could raise a second exception that displaced the one being reported.
     *
     * @param error the thrown value
     * @return a description built without running any script
     */
    private static String formatErrorMessage(JSValue error) {
        if (error instanceof JSError jsError) {
            // Fast path. A JSError already holds its name and message, so there is no reason to go
            // through descriptor lookups — and this runs on every thrown engine error.
            String name = jsError.getErrorName();
            JSString message = jsError.getMessage();
            String messageText = message == null ? "" : message.value();
            return messageText.isEmpty() ? name : name + ": " + messageText;
        }
        if (error instanceof JSObject errorObj) {
            String name = ownDataPropertyAsString(errorObj, PropertyKey.NAME, "Error");
            String message = ownDataPropertyAsString(errorObj, PropertyKey.MESSAGE, "");

            if (message.isEmpty()) {
                return name;
            }
            return name + ": " + message;
        }

        return error.toString();
    }

    /**
     * Read an own data property as a string, without invoking any accessor.
     *
     * @param errorObj     the object to read from
     * @param key          the property key
     * @param defaultValue the value to use when the property is absent, is an accessor, or is not
     *                     a string
     * @return the property value or the default
     */
    private static String ownDataPropertyAsString(JSObject errorObj, PropertyKey key, String defaultValue) {
        PropertyDescriptor descriptor = errorObj.getOwnPropertyDescriptor(key);
        if (descriptor != null && descriptor.isDataDescriptor()
                && descriptor.getValue() instanceof JSString stringValue) {
            return stringValue.value();
        }
        // Error objects keep name on their prototype, so fall back to the prototype chain, still
        // reading own data properties only.
        JSObject prototype = errorObj.getPrototype();
        for (int depth = 0; prototype != null && depth < 100; depth++) {
            PropertyDescriptor inherited = prototype.getOwnPropertyDescriptor(key);
            if (inherited != null) {
                return inherited.isDataDescriptor() && inherited.getValue() instanceof JSString inheritedValue
                        ? inheritedValue.value()
                        : defaultValue;
            }
            prototype = prototype.getPrototype();
        }
        return defaultValue;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Get the JavaScript error value.
     */
    public JSValue getErrorValue() {
        return errorValue;
    }

    /**
     * Get the source location associated with this exception.
     *
     * @return the source location, or {@code null} when it is unavailable
     */
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
