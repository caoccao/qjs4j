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
public final class JSNativeFunction extends JSFunction {
    private final NativeCallback callback;
    private final int length;
    private final String name;

    public JSNativeFunction(String name, int length, NativeCallback callback) {
        super(); // Initialize as JSObject
        this.name = name;
        this.length = length;
        this.callback = callback;

        // Set up function properties on the object
        // Functions are objects in JavaScript and have these standard properties
        this.set("name", new JSString(this.name));
        this.set("length", new JSNumber(this.length));

        // Native functions also have a prototype property
        JSObject funcPrototype = new JSObject();
        funcPrototype.set("constructor", this);
        this.set("prototype", funcPrototype);
    }

    @Override
    public JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return callback.call(ctx, thisArg, args);
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        String functionName = name != null && !name.isEmpty() ? name : "anonymous";
        return "function " + functionName + "() { [native code] }";
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }

    @FunctionalInterface
    public interface NativeCallback {
        JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args);
    }
}
