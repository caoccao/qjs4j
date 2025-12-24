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
 * Represents a native (Java-implemented) JavaScript function.
 */
public final class JSNativeFunction implements JSFunction {
    private final NativeCallback callback;
    private final String name;
    private final int length;

    @FunctionalInterface
    public interface NativeCallback {
        JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args);
    }

    public JSNativeFunction(String name, int length, NativeCallback callback) {
        this.name = name;
        this.length = length;
        this.callback = callback;
    }

    @Override
    public JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return callback.call(ctx, thisArg, args);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }
}
