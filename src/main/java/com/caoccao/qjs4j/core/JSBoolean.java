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

/**
 * Represents a JavaScript boolean value.
 */
public record JSBoolean(boolean value) implements JSValue {
    public static final JSBoolean FALSE = new JSBoolean(false);
    public static final JSBoolean TRUE = new JSBoolean(true);

    public static JSBoolean valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public Object toJavaObject() {
        return value;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }

    @Override
    public JSValueType type() {
        return JSValueType.BOOLEAN;
    }
}
