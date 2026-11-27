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

/** A boxed primitive keeps its original value independently of the mutable host-facing primitive slot. */
abstract class JSPrimitiveObject<T extends JSValue> extends JSObject {
    protected final T value;

    JSPrimitiveObject(JSContext context, T value) {
        super(context);
        this.value = value;
        setPrimitiveValue(value);
    }

    @Override
    public Object toJavaObject() {
        return value.toJavaObject();
    }
}
