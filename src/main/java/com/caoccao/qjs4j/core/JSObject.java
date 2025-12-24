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

import java.util.Map;

/**
 * Represents a JavaScript object.
 * Uses shape-based property storage for efficiency.
 */
public class JSObject implements JSValue {
    private JSShape shape;
    private JSValue[] propertyValues;
    private Map<Integer, JSValue> sparseProperties;
    private JSObject prototype;

    public JSObject() {
    }

    public JSObject(JSObject prototype) {
        this.prototype = prototype;
    }

    // Property operations
    public JSValue get(String propertyName) {
        return null;
    }

    public JSValue get(int propertyKey) {
        return null;
    }

    public void set(String propertyName, JSValue value) {
    }

    public void set(int propertyKey, JSValue value) {
    }

    public boolean has(String propertyName) {
        return false;
    }

    public boolean delete(String propertyName) {
        return false;
    }

    public JSValue[] ownPropertyKeys() {
        return new JSValue[0];
    }

    // Prototype chain
    public JSObject getPrototype() {
        return prototype;
    }

    public void setPrototype(JSObject prototype) {
        this.prototype = prototype;
    }

    @Override
    public JSValueType type() {
        return JSValueType.OBJECT;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }
}
