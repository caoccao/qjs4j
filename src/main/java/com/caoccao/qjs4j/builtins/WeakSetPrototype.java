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
 * Implementation of WeakSet.prototype methods.
 * Based on ES2020 WeakSet specification.
 */
public final class WeakSetPrototype {

    /**
     * WeakSet.prototype.add(value)
     * ES2020 23.4.3.1
     * Adds the value to the WeakSet object. Returns the WeakSet object.
     * Value must be an object.
     */
    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakSet weakSet)) {
            return context.throwTypeError("WeakSet.prototype.add called on non-WeakSet");
        }

        if (args.length == 0) {
            return context.throwTypeError("WeakSet.prototype.add requires a value");
        }

        JSValue value = args[0];

        // Value must be an object
        if (!(value instanceof JSObject valueObj)) {
            return context.throwTypeError("Invalid value used in weak set");
        }

        weakSet.weakSetAdd(valueObj);
        return weakSet; // Return the WeakSet object for chaining
    }

    /**
     * WeakSet.prototype.delete(value)
     * ES2020 23.4.3.2
     * Removes the value from the WeakSet. Returns true if the value existed and was removed.
     */
    public static JSValue delete(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakSet weakSet)) {
            return context.throwTypeError("WeakSet.prototype.delete called on non-WeakSet");
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        JSValue value = args[0];

        // Value must be an object
        if (!(value instanceof JSObject valueObj)) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(weakSet.weakSetDelete(valueObj));
    }

    /**
     * WeakSet.prototype.has(value)
     * ES2020 23.4.3.3
     * Returns a boolean indicating whether a value exists in the WeakSet.
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakSet weakSet)) {
            return context.throwTypeError("WeakSet.prototype.has called on non-WeakSet");
        }

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        JSValue value = args[0];

        // Value must be an object
        if (!(value instanceof JSObject valueObj)) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(weakSet.weakSetHas(valueObj));
    }
}
