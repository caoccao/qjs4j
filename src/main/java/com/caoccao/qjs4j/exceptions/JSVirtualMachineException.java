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

import com.caoccao.qjs4j.core.JSError;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.PropertyKey;

/**
 * VM exception for runtime errors.
 * <p>
 * This type carries guest errors: the engine catches it at many boundaries and turns it into a
 * JavaScript value — a thrown error, a rejected promise. Host-initiated termination is deliberately
 * <em>not</em> modelled here; see {@link JSTerminationException}.
 */
public class JSVirtualMachineException extends RuntimeException {
    private final JSError jsError;
    private final JSValue jsValue;

    public JSVirtualMachineException(String message) {
        super(message);
        this.jsError = null;
        this.jsValue = null;
    }

    /**
     * Wrap a thrown {@link JSError}.
     * <p>
     * The headline is read from physical storage. {@code JSError.getMessage()} is an ordinary
     * {@code get(PropertyKey.MESSAGE)}, so building this exception from it ran a guest accessor
     * while an error was already in flight — the same defect the diagnostic path in
     * {@link JSException} was hardened against, on the other of the two routes an uncaught error
     * takes out of the interpreter.
     *
     * @param jsError the thrown error
     */
    public JSVirtualMachineException(JSError jsError) {
        super(diagnosticMessage(jsError));
        this.jsError = jsError;
        this.jsValue = jsError;
    }

    public JSVirtualMachineException(String message, JSError jsError) {
        super(message);
        this.jsError = jsError;
        this.jsValue = jsError;
    }

    public JSVirtualMachineException(String message, JSValue jsValue) {
        super(message);
        this.jsError = null;
        this.jsValue = jsValue;
    }

    public JSVirtualMachineException(String message, Throwable cause) {
        super(message, cause);
        this.jsError = null;
        this.jsValue = null;
    }

    /**
     * Read an error's {@code message} without invoking an accessor or a Proxy trap.
     *
     * @param jsError the error
     * @return the message, or the empty string when it is absent or is not a string
     */
    private static String diagnosticMessage(JSError jsError) {
        return jsError.findDataPropertyForDiagnostics(PropertyKey.MESSAGE) instanceof JSString message
                ? message.value()
                : "";
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public JSError getJsError() {
        return jsError;
    }

    public JSValue getJsValue() {
        return jsValue;
    }
}
