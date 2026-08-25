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
        if (error instanceof JSObject errorObj) {
            // A JSError takes the same physical-storage path as any other thrown object; only its
            // default name differs, and getErrorName() is a per-class constant that runs nothing.
            // The old fast path called JSError.getMessage(), which is an ordinary
            // get(PropertyKey.MESSAGE) — so a guest could replace `message` with an accessor and
            // have it run while the engine was already unwinding an exception, which is exactly
            // what the rest of this method exists to prevent.
            String defaultName = error instanceof JSError jsError ? jsError.getErrorName() : "Error";
            String name = ownDataPropertyAsString(errorObj, PropertyKey.NAME, defaultName);
            String message = ownDataPropertyAsString(errorObj, PropertyKey.MESSAGE, "");

            if (message.isEmpty()) {
                return name;
            }
            return name + ": " + message;
        }

        return error.toString();
    }

    /**
     * Read a data property as a string, without invoking any accessor or Proxy trap.
     * <p>
     * {@code getOwnPropertyDescriptor} is virtual, and on a {@link com.caoccao.qjs4j.core.JSProxy}
     * the override <em>is</em> the {@code getOwnPropertyDescriptor} trap — so reading {@code name}
     * and {@code message} that way still re-entered guest code for a thrown Proxy, four times per
     * reported exception. {@code findDataPropertyForDiagnostics} is {@code final} and resolves to
     * physical storage, which is the only read in the engine that provably runs nothing.
     *
     * @param errorObj     the object to read from
     * @param key          the property key
     * @param defaultValue the value to use when the property is absent, is an accessor, is not a
     *                     string, or belongs to a Proxy
     * @return the property value or the default
     */
    private static String ownDataPropertyAsString(JSObject errorObj, PropertyKey key, String defaultValue) {
        return errorObj.findDataPropertyForDiagnostics(key) instanceof JSString stringValue
                ? stringValue.value()
                : defaultValue;
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
