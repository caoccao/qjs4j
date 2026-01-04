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
 * Internal value wrapper for VM-specific objects that shouldn't be exposed to JavaScript.
 * Used for things like for-in enumerators that need to be on the value stack but
 * aren't accessible from user code.
 */
public final class JSInternalValue implements JSStackValue {
    private final Object value;

    public JSInternalValue(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "[Internal: " + value.getClass().getSimpleName() + "]";
    }
}
