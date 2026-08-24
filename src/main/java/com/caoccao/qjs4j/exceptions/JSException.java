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
     */
    private static String formatErrorMessage(JSValue error) {
        if (error instanceof JSObject errorObj) {
            JSValue nameValue = errorObj.get(PropertyKey.NAME);
            JSValue messageValue = errorObj.get(PropertyKey.MESSAGE);

            String name = nameValue instanceof JSString ? ((JSString) nameValue).value() : "Error";
            String message = messageValue instanceof JSString ? ((JSString) messageValue).value() : "";

            if (message.isEmpty()) {
                return name;
            }
            return name + ": " + message;
        }

        return error.toString();
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
