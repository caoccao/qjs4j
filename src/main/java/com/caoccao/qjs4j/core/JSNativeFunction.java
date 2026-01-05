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
    private final boolean isConstructor;
    private final int length;
    private final String name;

    public JSNativeFunction(String name, int length, NativeCallback callback) {
        this(name, length, callback, true);
    }

    public JSNativeFunction(String name, int length, NativeCallback callback, boolean isConstructor) {
        super(); // Initialize as JSObject
        this.name = name;
        this.length = length;
        this.callback = callback;
        this.isConstructor = isConstructor;

        // Set up function properties on the object
        // Functions are objects in JavaScript and have these standard properties
        // Use empty string for name property if name is null (e.g., Function.prototype)

        // Per ECMAScript spec, the "name" property has attributes:
        // { [[Writable]]: false, [[Enumerable]]: false, [[Configurable]]: true }
        this.defineProperty(
                PropertyKey.fromString("name"),
                PropertyDescriptor.dataDescriptor(
                        new JSString(this.name != null ? this.name : ""),
                        false, // writable
                        false, // enumerable
                        true   // configurable
                )
        );

        // Per ECMAScript spec, the "length" property has attributes:
        // { [[Writable]]: false, [[Enumerable]]: false, [[Configurable]]: true }
        this.defineProperty(
                PropertyKey.fromString("length"),
                PropertyDescriptor.dataDescriptor(
                        new JSNumber(this.length),
                        false, // writable
                        false, // enumerable
                        true   // configurable
                )
        );

        // Native functions have a prototype property only if they are constructors
        if (isConstructor) {
            JSObject funcPrototype = new JSObject();
            funcPrototype.set("constructor", this);
            this.set("prototype", funcPrototype);
        }
    }

    @Override
    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return callback.call(context, thisArg, args);
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    @Override
    public String toString() {
        // null name means no name at all (e.g., Function.prototype)
        // empty string name means use "anonymous" (e.g., unnamed functions)
        if (name == null) {
            return "function () { [native code] }";
        } else if (name.isEmpty()) {
            return "function anonymous() { [native code] }";
        } else {
            return "function " + name + "() { [native code] }";
        }
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }

    @FunctionalInterface
    public interface NativeCallback {
        JSValue call(JSContext context, JSValue thisArg, JSValue[] args);
    }
}
