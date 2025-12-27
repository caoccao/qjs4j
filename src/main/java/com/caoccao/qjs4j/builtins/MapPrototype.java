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

import java.util.Map;

/**
 * Implementation of Map.prototype methods.
 * Based on ES2020 Map specification.
 */
public final class MapPrototype {

    /**
     * get Map.prototype.size
     * ES2020 23.1.3.10
     * Returns the number of entries in the Map.
     */
    public static JSValue getSize(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "get Map.prototype.size called on non-Map");
        }

        return new JSNumber(map.size());
    }

    /**
     * Map.prototype.set(key, value)
     * ES2020 23.1.3.9
     * Sets the value for the key in the Map object. Returns the Map object.
     */
    public static JSValue set(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.set called on non-Map");
        }

        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue value = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        map.mapSet(key, value);
        return map; // Return the Map object for chaining
    }

    /**
     * Map.prototype.get(key)
     * ES2020 23.1.3.5
     * Returns the value associated with the key, or undefined if none exists.
     */
    public static JSValue get(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.get called on non-Map");
        }

        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return map.mapGet(key);
    }

    /**
     * Map.prototype.has(key)
     * ES2020 23.1.3.6
     * Returns a boolean indicating whether an element with the specified key exists.
     */
    public static JSValue has(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.has called on non-Map");
        }

        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(map.mapHas(key));
    }

    /**
     * Map.prototype.delete(key)
     * ES2020 23.1.3.3
     * Removes the element with the specified key. Returns true if an element existed and was removed.
     */
    public static JSValue delete(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.delete called on non-Map");
        }

        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(map.mapDelete(key));
    }

    /**
     * Map.prototype.clear()
     * ES2020 23.1.3.1
     * Removes all elements from the Map.
     */
    public static JSValue clear(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.clear called on non-Map");
        }

        map.mapClear();
        return JSUndefined.INSTANCE;
    }

    /**
     * Map.prototype.forEach(callbackFn, thisArg)
     * ES2020 23.1.3.4
     * Executes a provided function once per each key/value pair in the Map, in insertion order.
     */
    public static JSValue forEach(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.forEach called on non-Map");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return ctx.throwError("TypeError", "Map.prototype.forEach requires a function");
        }

        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        // Iterate over entries in insertion order
        for (Map.Entry<JSMap.KeyWrapper, JSValue> entry : map.entries()) {
            JSValue key = entry.getKey().value();
            JSValue value = entry.getValue();

            // Call callback with (value, key, map)
            JSValue[] callbackArgs = new JSValue[]{value, key, map};
            callback.call(ctx, callbackThisArg, callbackArgs);
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Map.prototype.entries()
     * ES2020 23.1.3.4
     * Returns an iterator over [key, value] pairs.
     * Simplified implementation - returns an array for now.
     */
    public static JSValue entries(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.entries called on non-Map");
        }

        JSArray result = new JSArray();
        for (Map.Entry<JSMap.KeyWrapper, JSValue> entry : map.entries()) {
            JSArray pair = new JSArray();
            pair.push(entry.getKey().value());
            pair.push(entry.getValue());
            result.push(pair);
        }

        return result;
    }

    /**
     * Map.prototype.keys()
     * ES2020 23.1.3.7
     * Returns an iterator over keys.
     * Simplified implementation - returns an array for now.
     */
    public static JSValue keys(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.keys called on non-Map");
        }

        JSArray result = new JSArray();
        for (Map.Entry<JSMap.KeyWrapper, JSValue> entry : map.entries()) {
            result.push(entry.getKey().value());
        }

        return result;
    }

    /**
     * Map.prototype.values()
     * ES2020 23.1.3.11
     * Returns an iterator over values.
     * Simplified implementation - returns an array for now.
     */
    public static JSValue values(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.values called on non-Map");
        }

        JSArray result = new JSArray();
        for (Map.Entry<JSMap.KeyWrapper, JSValue> entry : map.entries()) {
            result.push(entry.getValue());
        }

        return result;
    }
}
