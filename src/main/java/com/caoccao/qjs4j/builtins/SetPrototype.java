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
 * Implementation of Set.prototype methods.
 * Based on ES2020 Set specification.
 */
public final class SetPrototype {

    /**
     * Set.prototype.add(value)
     * ES2020 23.2.3.1
     * Adds the value to the Set. Returns the Set object.
     */
    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.add called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        set.setAdd(value);
        return set; // Return the Set object for chaining
    }

    /**
     * Set.prototype.clear()
     * ES2020 23.2.3.2
     * Removes all values from the Set.
     */
    public static JSValue clear(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.clear called on non-Set");
        }

        set.setClear();
        return JSUndefined.INSTANCE;
    }

    /**
     * Set.prototype.delete(value)
     * ES2020 23.2.3.4
     * Removes the value from the Set. Returns true if the value existed and was removed.
     */
    public static JSValue delete(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.delete called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(set.setDelete(value));
    }

    /**
     * Set.prototype.entries()
     * ES2020 23.2.3.5
     * Returns an iterator over [value, value] pairs.
     */
    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.entries called on non-Set");
        }

        return JSIterator.setEntriesIterator(set);
    }

    /**
     * Set.prototype.forEach(callbackFn, thisArg)
     * ES2020 23.2.3.6
     * Executes a provided function once per each value in the Set, in insertion order.
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.forEach called on non-Set");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Set.prototype.forEach requires a function");
        }

        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        // Iterate over values in insertion order
        for (JSMap.KeyWrapper wrapper : set.values()) {
            JSValue value = wrapper.value();

            // Call callback with (value, value, set)
            // Note: In Set, both arguments are the value (for consistency with Map)
            JSValue[] callbackArgs = new JSValue[]{value, value, set};
            callback.call(context, callbackThisArg, callbackArgs);
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * get Set.prototype.size
     * ES2020 23.2.3.9
     * Returns the number of values in the Set.
     */
    public static JSValue getSize(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("get Set.prototype.size called on non-Set");
        }

        return new JSNumber(set.size());
    }

    /**
     * Set.prototype.has(value)
     * ES2020 23.2.3.7
     * Returns a boolean indicating whether a value exists in the Set.
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.has called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(set.setHas(value));
    }

    /**
     * Set.prototype.keys()
     * ES2020 23.2.3.8
     * Returns an iterator over values (same as values()).
     */
    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        // In Set, keys() is the same as values()
        return values(context, thisArg, args);
    }

    /**
     * Set.prototype.values()
     * ES2020 23.2.3.10
     * Returns an iterator over values.
     */
    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.values called on non-Set");
        }

        return JSIterator.setValuesIterator(set);
    }
}
