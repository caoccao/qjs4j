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

package com.caoccao.qjs4j.types;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JavaScript module.
 */
public final class JSModule implements JSValue {
    private final String url;
    private final Map<String, JSValue> exports;
    private final Map<String, ImportBinding> imports;
    private boolean evaluated;

    public JSModule(String url) {
        this.url = url;
        this.exports = new HashMap<>();
        this.imports = new HashMap<>();
        this.evaluated = false;
    }

    public void link(ModuleResolver resolver) {
    }

    public JSValue evaluate(JSContext ctx) {
        return null;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, JSValue> getExports() {
        return exports;
    }

    @Override
    public com.caoccao.qjs4j.core.JSValueType type() {
        return com.caoccao.qjs4j.core.JSValueType.OBJECT;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }

    public interface ModuleResolver {
        JSModule resolve(String specifier, JSModule referrer);
    }

    public record ImportBinding(String specifier, String imported, String local) {
    }
}
