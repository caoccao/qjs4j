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

import com.caoccao.qjs4j.types.JSModule;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JavaScript execution context.
 * Contains the global object, module cache, and stack trace.
 */
public final class JSContext {
    private final JSRuntime runtime;
    private final JSObject globalObject;
    private final Map<String, JSModule> moduleCache;

    public JSContext(JSRuntime runtime) {
        this.runtime = runtime;
        this.globalObject = new JSObject();
        this.moduleCache = new HashMap<>();
    }

    public JSValue eval(String code) {
        return null;
    }

    public JSModule loadModule(String specifier) {
        return null;
    }

    public void throwError(String message) {
    }

    public JSRuntime getRuntime() {
        return runtime;
    }

    public JSObject getGlobalObject() {
        return globalObject;
    }
}
