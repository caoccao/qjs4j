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
import com.caoccao.qjs4j.core.JSValue;

/**
 * VM exception for runtime errors.
 */
public class JSVirtualMachineException extends RuntimeException {
    private final JSError jsError;
    private final JSValue jsValue;
    private final boolean uncatchable;

    public JSVirtualMachineException(String message) {
        super(message);
        this.jsError = null;
        this.jsValue = null;
        this.uncatchable = false;
    }

    public JSVirtualMachineException(JSError jsError) {
        super(jsError.getMessage().value());
        this.jsError = jsError;
        this.jsValue = jsError;
        this.uncatchable = false;
    }

    public JSVirtualMachineException(String message, JSError jsError) {
        super(message);
        this.jsError = jsError;
        this.jsValue = jsError;
        this.uncatchable = false;
    }

    public JSVirtualMachineException(String message, JSValue jsValue) {
        super(message);
        this.jsError = null;
        this.jsValue = jsValue;
        this.uncatchable = false;
    }

    public JSVirtualMachineException(String message, Throwable cause) {
        super(message, cause);
        this.jsError = null;
        this.jsValue = null;
        this.uncatchable = false;
    }

    private JSVirtualMachineException(String message, boolean uncatchable) {
        super(message);
        this.jsError = null;
        this.jsValue = null;
        this.uncatchable = uncatchable;
    }

    /**
     * Create an exception that JavaScript {@code try}/{@code catch} must never intercept.
     * <p>
     * Used for host-initiated termination (execution deadline, host interrupt request), where a
     * script must not be able to keep itself alive by swallowing the signal, and for engine defects
     * such as an invalid opcode, which must reach the embedder rather than be masked as a guest
     * error.
     *
     * @param message the message describing why execution was terminated
     * @return an uncatchable VM exception
     */
    public static JSVirtualMachineException uncatchable(String message) {
        return new JSVirtualMachineException(message, true);
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

    /**
     * Whether this exception bypasses JavaScript exception handling entirely.
     *
     * @return true when script {@code catch} blocks must not observe this exception
     */
    public boolean isUncatchable() {
        return uncatchable;
    }
}
