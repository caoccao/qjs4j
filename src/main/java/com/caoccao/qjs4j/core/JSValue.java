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
 * Base sealed interface for all JavaScript values.
 * Implements the value representation using sealed interfaces for type safety.
 */
public sealed interface JSValue permits
        JSUndefined, JSNull, JSBoolean, JSNumber, JSString,
        JSObject, JSSymbol, JSBigInt, JSFunction {

    /**
     * Get the type of this value.
     */
    JSValueType type();

    /**
     * Convert to Java object representation.
     */
    Object toJavaObject();
}
