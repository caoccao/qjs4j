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

import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Exception thrown when JavaScript code throws an error.
 * Wraps the JavaScript error value for access from Java.
 */
public class JSException extends RuntimeException {
    private final JSValue errorValue;

    public JSException(String name, String message) {
        this(name, message, null);
    }

    public JSException(String name, String message, Throwable cause) {
        super(name + ": " + message, cause);
        JSObject errorObject = new JSObject();
        errorObject.set("name", new JSString(name));
        errorObject.set("message", new JSString(message));
        errorValue = errorObject;
    }

    public JSException(JSValue errorValue) {
        this(errorValue, null);
    }

    public JSException(JSValue errorValue, Throwable cause) {
        super(formatErrorMessage(errorValue), cause);
        this.errorValue = errorValue;
    }

    /**
     * Format error message from JavaScript error object.
     */
    private static String formatErrorMessage(JSValue error) {
        if (error instanceof JSObject errorObj) {
            JSValue nameValue = errorObj.get("name");
            JSValue messageValue = errorObj.get("message");

            String name = nameValue instanceof JSString ? ((JSString) nameValue).value() : "Error";
            String message = messageValue instanceof JSString ? ((JSString) messageValue).value() : "";

            if (message.isEmpty()) {
                return name;
            }
            return name + ": " + message;
        }

        return error.toString();
    }

    /**
     * Get the JavaScript error value.
     */
    public JSValue getErrorValue() {
        return errorValue;
    }
}
