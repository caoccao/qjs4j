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
     * get Set.prototype.size
     * ES2020 23.2.3.9
     * Returns the number of values in the Set.
     */
    public static JSValue getSize(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "get Set.prototype.size called on non-Set");
        }

        return new JSNumber(set.size());
    }

    /**
     * Set.prototype.add(value)
     * ES2020 23.2.3.1
     * Adds the value to the Set. Returns the Set object.
     */
    public static JSValue add(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.add called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        set.setAdd(value);
        return set; // Return the Set object for chaining
    }

    /**
     * Set.prototype.has(value)
     * ES2020 23.2.3.7
     * Returns a boolean indicating whether a value exists in the Set.
     */
    public static JSValue has(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.has called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(set.setHas(value));
    }

    /**
     * Set.prototype.delete(value)
     * ES2020 23.2.3.4
     * Removes the value from the Set. Returns true if the value existed and was removed.
     */
    public static JSValue delete(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.delete called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(set.setDelete(value));
    }

    /**
     * Set.prototype.clear()
     * ES2020 23.2.3.2
     * Removes all values from the Set.
     */
    public static JSValue clear(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.clear called on non-Set");
        }

        set.setClear();
        return JSUndefined.INSTANCE;
    }

    /**
     * Set.prototype.forEach(callbackFn, thisArg)
     * ES2020 23.2.3.6
     * Executes a provided function once per each value in the Set, in insertion order.
     */
    public static JSValue forEach(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.forEach called on non-Set");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return ctx.throwError("TypeError", "Set.prototype.forEach requires a function");
        }

        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        // Iterate over values in insertion order
        for (JSMap.KeyWrapper wrapper : set.values()) {
            JSValue value = wrapper.value();

            // Call callback with (value, value, set)
            // Note: In Set, both arguments are the value (for consistency with Map)
            JSValue[] callbackArgs = new JSValue[]{value, value, set};
            callback.call(ctx, callbackThisArg, callbackArgs);
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Set.prototype.entries()
     * ES2020 23.2.3.5
     * Returns an iterator over [value, value] pairs.
     * Simplified implementation - returns an array for now.
     */
    public static JSValue entries(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.entries called on non-Set");
        }

        JSArray result = new JSArray();
        for (JSMap.KeyWrapper wrapper : set.values()) {
            JSValue value = wrapper.value();
            JSArray pair = new JSArray();
            pair.push(value);
            pair.push(value); // In Set, both elements are the same value
            result.push(pair);
        }

        return result;
    }

    /**
     * Set.prototype.keys()
     * ES2020 23.2.3.8
     * Returns an iterator over values (same as values()).
     * Simplified implementation - returns an array for now.
     */
    public static JSValue keys(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // In Set, keys() is the same as values()
        return values(ctx, thisArg, args);
    }

    /**
     * Set.prototype.values()
     * ES2020 23.2.3.10
     * Returns an iterator over values.
     * Simplified implementation - returns an array for now.
     */
    public static JSValue values(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.values called on non-Set");
        }

        JSArray result = new JSArray();
        for (JSMap.KeyWrapper wrapper : set.values()) {
            result.push(wrapper.value());
        }

        return result;
    }
}
