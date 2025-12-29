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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;

/**
 * Implementation of WeakMap.prototype methods.
 * Based on ES2020 WeakMap specification.
 */
public final class WeakMapPrototype {

    /**
     * WeakMap.prototype.delete(key)
     * ES2020 23.3.3.2
     * Removes the element with the specified key. Returns true if an element existed and was removed.
     */
    public static JSValue delete(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap weakMap)) {
            return context.throwTypeError("WeakMap.prototype.delete called on non-WeakMap");
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        JSValue key = args[0];

        // Key must be an object
        if (!(key instanceof JSObject keyObj)) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(weakMap.weakMapDelete(keyObj));
    }

    /**
     * WeakMap.prototype.get(key)
     * ES2020 23.3.3.3
     * Returns the value associated with the key, or undefined if none exists.
     */
    public static JSValue get(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap weakMap)) {
            return context.throwTypeError("WeakMap.prototype.get called on non-WeakMap");
        }

        if (args.length == 0) {
            return JSUndefined.INSTANCE;
        }

        JSValue key = args[0];

        // Key must be an object
        if (!(key instanceof JSObject keyObj)) {
            return JSUndefined.INSTANCE;
        }

        return weakMap.weakMapGet(keyObj);
    }

    /**
     * WeakMap.prototype.has(key)
     * ES2020 23.3.3.4
     * Returns a boolean indicating whether an element with the specified key exists.
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap weakMap)) {
            return context.throwTypeError("WeakMap.prototype.has called on non-WeakMap");
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        JSValue key = args[0];

        // Key must be an object
        if (!(key instanceof JSObject keyObj)) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(weakMap.weakMapHas(keyObj));
    }

    /**
     * WeakMap.prototype.set(key, value)
     * ES2020 23.3.3.5
     * Sets the value for the key in the WeakMap object. Returns the WeakMap object.
     * Key must be an object.
     */
    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap weakMap)) {
            return context.throwTypeError("WeakMap.prototype.set called on non-WeakMap");
        }

        if (args.length == 0) {
            return context.throwTypeError("WeakMap.prototype.set requires a key");
        }

        JSValue key = args[0];

        // Key must be an object
        if (!(key instanceof JSObject keyObj)) {
            return context.throwTypeError("Invalid value used as weak map key");
        }

        JSValue value = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        weakMap.weakMapSet(keyObj, value);
        return weakMap; // Return the WeakMap object for chaining
    }
}
