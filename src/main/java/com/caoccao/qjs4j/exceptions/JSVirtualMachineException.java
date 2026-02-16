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

    public JSVirtualMachineException(String message) {
        super(message);
        this.jsError = null;
        this.jsValue = null;
    }

    public JSVirtualMachineException(JSError jsError) {
        super(jsError.getMessage().value());
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

    public JSError getJsError() {
        return jsError;
    }

    public JSValue getJsValue() {
        return jsValue;
    }
}
